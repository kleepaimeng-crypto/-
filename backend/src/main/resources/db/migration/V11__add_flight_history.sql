CREATE SCHEMA flight_history;

CREATE TABLE flight_history.flight_session_archive (
    id uuid PRIMARY KEY,
    source_flight_session_id uuid NOT NULL,
    source_system_code varchar(64) NOT NULL,
    source_device_code varchar(64) NOT NULL,
    source_host inet NOT NULL,
    flight_no varchar(20) NOT NULL,
    origin varchar(4) NOT NULL,
    destination varchar(4) NOT NULL,
    aircraft_registration_no varchar(32) NOT NULL,
    aircraft_model varchar(128),
    airline_code varchar(16),
    started_at timestamptz NOT NULL,
    ended_at timestamptz NOT NULL,
    point_count integer NOT NULL DEFAULT 0,
    finish_reason varchar(32) NOT NULL,
    archived_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_history_session_source UNIQUE (source_flight_session_id),
    CONSTRAINT ck_history_session_time CHECK (ended_at >= started_at),
    CONSTRAINT ck_history_session_point_count CHECK (point_count >= 0),
    CONSTRAINT ck_history_session_finish_reason CHECK (
        finish_reason IN ('LANDED', 'TIMEOUT', 'NEW_FLIGHT', 'FRAME_RESET')
    )
);

CREATE INDEX idx_history_session_ended
    ON flight_history.flight_session_archive (ended_at DESC, id);
CREATE INDEX idx_history_session_route_ended
    ON flight_history.flight_session_archive (flight_no, origin, destination, ended_at DESC);

CREATE TABLE flight_history.qar_point_archive (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES flight_history.flight_session_archive(id) ON DELETE RESTRICT,
    source_qar_sample_id bigint NOT NULL,
    sample_at timestamptz NOT NULL,
    source_time_text varchar(16) NOT NULL,
    frame_count bigint NOT NULL,
    air_ground_status varchar(16) NOT NULL,
    latitude double precision,
    longitude double precision,
    altitude_ft numeric(10,2),
    ground_speed_kt numeric(10,3),
    computed_air_speed_kt numeric(10,3),
    track_angle_deg numeric(7,3),
    heading_deg numeric(7,3),
    pitch_deg numeric(7,3),
    roll_deg numeric(7,3),
    distance_to_go_nm numeric(12,3),
    destination_eta_text varchar(32),
    archived_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_history_qar_source UNIQUE (source_qar_sample_id)
);

CREATE INDEX idx_history_qar_session_sample
    ON flight_history.qar_point_archive (session_id, sample_at, frame_count, id);

CREATE TABLE flight_history.archive_job (
    id uuid PRIMARY KEY,
    source_flight_session_id uuid NOT NULL,
    finish_reason varchar(32) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    next_retry_at timestamptz,
    last_error varchar(1000),
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    completed_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_history_archive_job_source UNIQUE (source_flight_session_id),
    CONSTRAINT ck_history_archive_job_reason CHECK (
        finish_reason IN ('LANDED', 'TIMEOUT', 'NEW_FLIGHT', 'FRAME_RESET')
    ),
    CONSTRAINT ck_history_archive_job_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_history_archive_job_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_history_archive_job_schedule
    ON flight_history.archive_job (status, next_retry_at, created_at);
