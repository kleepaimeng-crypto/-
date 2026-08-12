ALTER TABLE ife_633_behavior
    ADD COLUMN flight_session_id uuid;

ALTER TABLE ife_633_behavior
    ADD CONSTRAINT fk_ife_633_flight_session
        FOREIGN KEY (flight_session_id)
        REFERENCES flight_session(id)
        ON DELETE RESTRICT
        NOT VALID;

-- Existing 633 history deliberately remains NULL. Only newly associated rows
-- enter this partial index, avoiding a rewrite or index build over old history.
CREATE INDEX CONCURRENTLY idx_ife_633_session_seat_latest
    ON ife_633_behavior (
        flight_session_id,
        seat_no,
        event_at DESC,
        created_at DESC,
        id DESC
    )
    INCLUDE (record_id)
    WHERE flight_session_id IS NOT NULL;
