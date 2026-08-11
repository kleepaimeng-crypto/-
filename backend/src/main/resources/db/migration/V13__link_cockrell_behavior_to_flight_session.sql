ALTER TABLE ife_cockrell_behavior
    ADD COLUMN flight_session_id uuid;

ALTER TABLE ife_cockrell_behavior
    ADD CONSTRAINT fk_cockrell_flight_session
        FOREIGN KEY (flight_session_id)
        REFERENCES flight_session(id)
        ON DELETE RESTRICT
        NOT VALID;
