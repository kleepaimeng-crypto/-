CREATE TABLE flight_session (
    id uuid PRIMARY KEY,
    source_system_code varchar(64) NOT NULL,
    source_device_code varchar(64) NOT NULL,
    source_host inet NOT NULL,
    flight_no varchar(20) NOT NULL,
    origin varchar(4) NOT NULL,
    destination varchar(4) NOT NULL,
    aircraft_registration_no varchar(32) NOT NULL,
    aircraft_model varchar(128),
    airline_code varchar(16),
    status varchar(16) NOT NULL,
    started_at timestamptz NOT NULL,
    last_sample_at timestamptz NOT NULL,
    last_received_at timestamptz NOT NULL,
    ended_at timestamptz,
    last_frame_count bigint NOT NULL,
    latest_qar_sample_id bigint REFERENCES qar_sample(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_flight_session_status CHECK (status IN ('ACTIVE', 'FINISHED')),
    CONSTRAINT ck_flight_session_origin CHECK (origin ~ '^[A-Z0-9]{4}$'),
    CONSTRAINT ck_flight_session_destination CHECK (destination ~ '^[A-Z0-9]{4}$'),
    CONSTRAINT ck_flight_session_frame_count CHECK (last_frame_count >= 0),
    CONSTRAINT ck_flight_session_ended_at CHECK (ended_at IS NULL OR ended_at >= started_at)
);

ALTER TABLE qar_sample ADD COLUMN flight_session_id uuid;

CREATE TEMP TABLE qar_session_assignment ON COMMIT DROP AS
WITH ordered AS (
    SELECT
        q.id AS qar_sample_id,
        q.sample_at,
        q.frame_count,
        q.flight_no,
        q.origin,
        q.destination,
        r.received_at,
        r.source_system_code,
        r.source_device_code,
        COALESCE(r.source_host, '0.0.0.0'::inet) AS source_host,
        r.aircraft_registration_no,
        r.aircraft_model,
        r.airline_code,
        LAG(q.frame_count) OVER stream_order AS previous_frame_count,
        LAG(r.received_at) OVER stream_order AS previous_received_at
    FROM qar_sample q
    JOIN data_record r ON r.id = q.record_id
    WINDOW stream_order AS (
        PARTITION BY
            r.source_system_code,
            r.source_device_code,
            COALESCE(r.source_host, '0.0.0.0'::inet),
            q.flight_no,
            q.origin,
            q.destination
        ORDER BY q.sample_at, q.frame_count, q.id
    )
),
marked AS (
    SELECT
        *,
        CASE
            WHEN previous_frame_count IS NULL THEN 1
            WHEN frame_count <= 5 AND previous_frame_count > frame_count THEN 1
            WHEN received_at - previous_received_at > INTERVAL '5 minutes' THEN 1
            ELSE 0
        END AS starts_new_session
    FROM ordered
),
segmented AS (
    SELECT
        *,
        SUM(starts_new_session) OVER (
            PARTITION BY
                source_system_code,
                source_device_code,
                source_host,
                flight_no,
                origin,
                destination
            ORDER BY sample_at, frame_count, qar_sample_id
        ) AS segment_no
    FROM marked
)
SELECT * FROM segmented;

CREATE TEMP TABLE generated_flight_session ON COMMIT DROP AS
SELECT
    gen_random_uuid() AS id,
    source_system_code,
    source_device_code,
    source_host,
    flight_no,
    origin,
    destination,
    segment_no,
    (array_agg(aircraft_registration_no ORDER BY sample_at DESC, frame_count DESC, qar_sample_id DESC))[1]
        AS aircraft_registration_no,
    (array_agg(aircraft_model ORDER BY sample_at DESC, frame_count DESC, qar_sample_id DESC))[1]
        AS aircraft_model,
    (array_agg(airline_code ORDER BY sample_at DESC, frame_count DESC, qar_sample_id DESC))[1]
        AS airline_code,
    MIN(sample_at) AS started_at,
    MAX(sample_at) AS last_sample_at,
    MAX(received_at) AS last_received_at,
    (array_agg(frame_count ORDER BY sample_at DESC, frame_count DESC, qar_sample_id DESC))[1]
        AS last_frame_count,
    (array_agg(qar_sample_id ORDER BY sample_at DESC, frame_count DESC, qar_sample_id DESC))[1]
        AS latest_qar_sample_id
FROM qar_session_assignment
GROUP BY
    source_system_code,
    source_device_code,
    source_host,
    flight_no,
    origin,
    destination,
    segment_no;

INSERT INTO flight_session (
    id,
    source_system_code,
    source_device_code,
    source_host,
    flight_no,
    origin,
    destination,
    aircraft_registration_no,
    aircraft_model,
    airline_code,
    status,
    started_at,
    last_sample_at,
    last_received_at,
    ended_at,
    last_frame_count,
    latest_qar_sample_id
)
SELECT
    id,
    source_system_code,
    source_device_code,
    source_host,
    flight_no,
    origin,
    destination,
    aircraft_registration_no,
    aircraft_model,
    airline_code,
    'FINISHED',
    started_at,
    last_sample_at,
    last_received_at,
    last_sample_at,
    last_frame_count,
    latest_qar_sample_id
FROM generated_flight_session;

WITH latest_per_source AS (
    SELECT DISTINCT ON (source_system_code, source_device_code, source_host)
        id
    FROM generated_flight_session
    ORDER BY
        source_system_code,
        source_device_code,
        source_host,
        last_received_at DESC,
        last_sample_at DESC
)
UPDATE flight_session
SET status = 'ACTIVE',
    ended_at = NULL,
    updated_at = now()
WHERE id IN (SELECT id FROM latest_per_source);

UPDATE qar_sample q
SET flight_session_id = s.id
FROM qar_session_assignment a
JOIN generated_flight_session s
  ON s.source_system_code = a.source_system_code
 AND s.source_device_code = a.source_device_code
 AND s.source_host = a.source_host
 AND s.flight_no = a.flight_no
 AND s.origin = a.origin
 AND s.destination = a.destination
 AND s.segment_no = a.segment_no
WHERE q.id = a.qar_sample_id;

ALTER TABLE qar_sample
    ALTER COLUMN flight_session_id SET NOT NULL,
    ADD CONSTRAINT fk_qar_sample_flight_session
        FOREIGN KEY (flight_session_id) REFERENCES flight_session(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uk_flight_session_active_source
    ON flight_session (source_system_code, source_device_code, source_host)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_flight_session_status_received
    ON flight_session (status, last_received_at DESC);

CREATE INDEX idx_qar_flight_session_sample
    ON qar_sample (flight_session_id, sample_at, frame_count, id);

DROP TABLE flight_track_current_state;
