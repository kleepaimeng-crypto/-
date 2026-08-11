-- Historical KKRE rows deliberately remain unassigned. The nullable, NOT VALID
-- foreign key created in V13 still enforces every new non-null session reference
-- without scanning or rewriting the existing Cockrell history.

-- Idempotent cleanup for an environment where the original V14 was interrupted.
DROP PROCEDURE IF EXISTS backfill_cockrell_flight_sessions(integer);

-- Only newly associated rows enter this partial index, so large legacy tables
-- whose flight_session_id remains NULL do not make deployment expensive.
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
