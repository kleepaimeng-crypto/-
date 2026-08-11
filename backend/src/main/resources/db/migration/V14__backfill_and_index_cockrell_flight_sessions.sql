CREATE OR REPLACE PROCEDURE backfill_cockrell_flight_sessions(batch_size integer DEFAULT 5000)
LANGUAGE plpgsql
AS $$
DECLARE
    after_id bigint := 0;
    batch_last_id bigint;
    updated_in_batch bigint;
    updated_total bigint := 0;
    pending_total bigint;
BEGIN
    LOOP
        SELECT MAX(id)
        INTO batch_last_id
        FROM (
            SELECT id
            FROM ife_cockrell_behavior
            WHERE flight_session_id IS NULL
              AND id > after_id
            ORDER BY id
            LIMIT batch_size
        ) pending_batch;

        EXIT WHEN batch_last_id IS NULL;

        WITH pending_batch AS (
            SELECT b.id, b.record_id, b.flight_no, b.event_at
            FROM ife_cockrell_behavior b
            WHERE b.flight_session_id IS NULL
              AND b.id > after_id
              AND b.id <= batch_last_id
        ), scored_candidates AS (
            SELECT
                b.id AS behavior_id,
                fs.id AS session_id,
                CASE
                    WHEN b.event_at BETWEEN fs.started_at
                            AND COALESCE(fs.ended_at, fs.last_sample_at)
                    THEN 0
                    ELSE 1
                END AS match_priority,
                CASE
                    WHEN b.event_at < fs.started_at
                    THEN EXTRACT(EPOCH FROM (fs.started_at - b.event_at))
                    WHEN b.event_at > COALESCE(fs.ended_at, fs.last_sample_at)
                    THEN EXTRACT(EPOCH FROM (
                        b.event_at - COALESCE(fs.ended_at, fs.last_sample_at)
                    ))
                    ELSE 0
                END AS distance_seconds
            FROM pending_batch b
            JOIN data_record r ON r.id = b.record_id
            JOIN flight_session fs
              ON upper(fs.flight_no) = upper(b.flight_no)
             AND fs.aircraft_registration_no = r.aircraft_registration_no
             AND fs.source_host = COALESCE(r.source_host, '0.0.0.0'::inet)
             AND b.event_at >= fs.started_at - INTERVAL '5 minutes'
             AND b.event_at <= COALESCE(fs.ended_at, fs.last_sample_at) + INTERVAL '5 minutes'
        ), ranked_candidates AS (
            SELECT
                behavior_id,
                session_id,
                RANK() OVER (
                    PARTITION BY behavior_id
                    ORDER BY match_priority, distance_seconds
                ) AS score_rank
            FROM scored_candidates
        ), counted_candidates AS (
            SELECT
                behavior_id,
                session_id,
                score_rank,
                COUNT(*) FILTER (WHERE score_rank = 1)
                    OVER (PARTITION BY behavior_id) AS best_candidate_count
            FROM ranked_candidates
        ), unique_best AS (
            SELECT behavior_id, session_id
            FROM counted_candidates
            WHERE score_rank = 1
              AND best_candidate_count = 1
        )
        UPDATE ife_cockrell_behavior b
        SET flight_session_id = best.session_id
        FROM unique_best best
        WHERE b.id = best.behavior_id
          AND b.flight_session_id IS NULL;

        GET DIAGNOSTICS updated_in_batch = ROW_COUNT;
        updated_total := updated_total + updated_in_batch;
        after_id := batch_last_id;
        COMMIT;
    END LOOP;

    SELECT COUNT(*)
    INTO pending_total
    FROM ife_cockrell_behavior
    WHERE flight_session_id IS NULL;

    RAISE NOTICE 'Cockrell flight-session backfill updated %, left % unassigned',
        updated_total, pending_total;
END;
$$;

CALL backfill_cockrell_flight_sessions(5000);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cockrell_session_seat_latest
    ON ife_cockrell_behavior (
        flight_session_id,
        seat_no,
        event_at DESC,
        created_at DESC,
        id DESC
    )
    INCLUDE (record_id)
    WHERE flight_session_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cockrell_pending_session
    ON ife_cockrell_behavior (flight_no, event_at, record_id)
    WHERE flight_session_id IS NULL;

ALTER TABLE ife_cockrell_behavior
    VALIDATE CONSTRAINT fk_cockrell_flight_session;

DROP PROCEDURE backfill_cockrell_flight_sessions(integer);
