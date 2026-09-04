ALTER TABLE data_record
    DROP CONSTRAINT ck_data_record_origin,
    DROP CONSTRAINT ck_data_record_destination,
    ALTER COLUMN origin TYPE varchar(64),
    ALTER COLUMN destination TYPE varchar(64);

ALTER TABLE qar_sample
    DROP CONSTRAINT ck_qar_origin,
    DROP CONSTRAINT ck_qar_destination,
    ALTER COLUMN origin TYPE varchar(64),
    ALTER COLUMN destination TYPE varchar(64);

ALTER TABLE flight_session
    DROP CONSTRAINT ck_flight_session_origin,
    DROP CONSTRAINT ck_flight_session_destination,
    ALTER COLUMN origin TYPE varchar(64),
    ALTER COLUMN destination TYPE varchar(64);

ALTER TABLE flight_history.flight_session_archive
    ALTER COLUMN origin TYPE varchar(64),
    ALTER COLUMN destination TYPE varchar(64);
