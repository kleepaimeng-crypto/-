CREATE TABLE flight_track_current_state (
    state_key varchar(16) PRIMARY KEY,
    record_id uuid NOT NULL REFERENCES data_record(id) ON DELETE CASCADE,
    sample_at timestamptz NOT NULL,
    source_time_text varchar(16) NOT NULL,
    flight_no varchar(20) NOT NULL,
    origin varchar(4) NOT NULL,
    destination varchar(4) NOT NULL,
    aircraft_registration_no varchar(32) NOT NULL,
    aircraft_model varchar(128),
    airline_code varchar(16),
    air_ground_status varchar(16) NOT NULL,
    altitude_ft numeric(10,2),
    computed_air_speed_kt numeric(10,3),
    ground_speed_kt numeric(10,3),
    latitude double precision NOT NULL,
    longitude double precision NOT NULL,
    track_angle_deg numeric(7,3),
    heading_deg numeric(7,3),
    pitch_deg numeric(7,3),
    roll_deg numeric(7,3),
    distance_to_go_nm numeric(12,3),
    destination_eta_text varchar(32),
    frame_count bigint NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_flight_track_current_state_key CHECK (state_key = 'CURRENT'),
    CONSTRAINT ck_flight_track_current_origin CHECK (origin ~ '^[A-Z0-9]{4}$'),
    CONSTRAINT ck_flight_track_current_destination CHECK (destination ~ '^[A-Z0-9]{4}$'),
    CONSTRAINT ck_flight_track_current_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_flight_track_current_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_flight_track_current_ground_speed CHECK (ground_speed_kt IS NULL OR ground_speed_kt >= 0),
    CONSTRAINT ck_flight_track_current_frame_count CHECK (frame_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_qar_sample_sample_at_desc
    ON qar_sample (sample_at DESC)
    WHERE latitude IS NOT NULL
      AND longitude IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_qar_sample_flight_sample_desc
    ON qar_sample (flight_no, sample_at DESC)
    WHERE latitude IS NOT NULL
      AND longitude IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_qar_sample_active_recent
    ON qar_sample (sample_at DESC, flight_no)
    WHERE latitude IS NOT NULL
      AND longitude IS NOT NULL
      AND ground_speed_kt > 100;
