CREATE TABLE app_user (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username varchar(64) NOT NULL,
    password_hash varchar(255) NOT NULL,
    email varchar(254),
    role_code varchar(32) NOT NULL DEFAULT 'ADMIN',
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_at timestamptz,
    version integer NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_app_user_role CHECK (role_code = 'ADMIN'),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_app_user_version CHECK (version > 0),
    CONSTRAINT ck_app_user_username CHECK (length(btrim(username)) BETWEEN 1 AND 64)
);

CREATE UNIQUE INDEX uk_app_user_username_ci ON app_user (lower(username));
CREATE UNIQUE INDEX uk_app_user_email_ci ON app_user (lower(email)) WHERE email IS NOT NULL;
CREATE INDEX idx_app_user_status ON app_user (status);

CREATE TABLE data_type (
    code varchar(64) PRIMARY KEY,
    name varchar(128) NOT NULL,
    message_type varchar(128) NOT NULL,
    udp_port integer,
    source_system_code varchar(64) NOT NULL,
    source_device_code varchar(64) NOT NULL,
    parser_code varchar(64),
    enabled boolean NOT NULL DEFAULT true,
    supports_csv_import boolean NOT NULL DEFAULT false,
    supports_csv_export boolean NOT NULL DEFAULT true,
    supports_pdf_export boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL DEFAULT 0,
    description varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_data_type_message_type UNIQUE (message_type),
    CONSTRAINT uk_data_type_udp_port UNIQUE (udp_port),
    CONSTRAINT ck_data_type_udp_port CHECK (udp_port IS NULL OR udp_port BETWEEN 1 AND 65535),
    CONSTRAINT ck_data_type_code CHECK (code ~ '^[A-Z0-9_]+$')
);

CREATE INDEX idx_data_type_enabled_sort ON data_type (enabled, sort_order);

CREATE TABLE file_job (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type varchar(16) NOT NULL,
    data_type_code varchar(64) NOT NULL REFERENCES data_type(code) ON DELETE RESTRICT,
    file_format varchar(16) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    original_file_name varchar(255),
    result_file_name varchar(255),
    storage_path varchar(1000),
    error_file_path varchar(1000),
    file_size bigint NOT NULL DEFAULT 0,
    filter_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
    total_rows integer NOT NULL DEFAULT 0,
    success_rows integer NOT NULL DEFAULT 0,
    failed_rows integer NOT NULL DEFAULT 0,
    error_message varchar(2000),
    idempotency_key varchar(128),
    requested_by uuid NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    started_at timestamptz,
    completed_at timestamptz,
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_file_job_type CHECK (job_type IN ('IMPORT', 'EXPORT')),
    CONSTRAINT ck_file_job_format CHECK (
        (job_type = 'IMPORT' AND file_format = 'CSV') OR
        (job_type = 'EXPORT' AND file_format IN ('CSV', 'PDF'))
    ),
    CONSTRAINT ck_file_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_file_job_file_size CHECK (file_size >= 0),
    CONSTRAINT ck_file_job_rows CHECK (
        total_rows >= 0 AND success_rows >= 0 AND failed_rows >= 0 AND
        success_rows + failed_rows <= total_rows
    ),
    CONSTRAINT ck_file_job_completed CHECK (
        (status IN ('PENDING', 'RUNNING') AND completed_at IS NULL) OR
        (status IN ('SUCCEEDED', 'PARTIAL', 'FAILED') AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_file_job_requester_created ON file_job (requested_by, created_at DESC);
CREATE INDEX idx_file_job_type_status_created ON file_job (job_type, status, created_at DESC);
CREATE INDEX idx_file_job_data_type_created ON file_job (data_type_code, created_at DESC);
CREATE UNIQUE INDEX uk_file_job_idempotency ON file_job (requested_by, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE data_record (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    data_type_code varchar(64) NOT NULL REFERENCES data_type(code) ON DELETE RESTRICT,
    ingest_method varchar(16) NOT NULL,
    file_job_id uuid REFERENCES file_job(id) ON DELETE RESTRICT,
    source_system_code varchar(64) NOT NULL,
    source_device_code varchar(64) NOT NULL,
    source_host inet,
    source_port integer,
    aircraft_registration_no varchar(32) NOT NULL,
    aircraft_model varchar(128),
    airline_code varchar(16),
    flight_no varchar(20),
    origin varchar(4),
    destination varchar(4),
    sent_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    payload_count integer NOT NULL DEFAULT 1,
    raw_payload jsonb,
    raw_text text,
    parse_status varchar(16) NOT NULL DEFAULT 'RECEIVED',
    parse_error text,
    version integer NOT NULL DEFAULT 1,
    is_deleted boolean NOT NULL DEFAULT false,
    deleted_at timestamptz,
    deleted_by uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    delete_reason varchar(500),
    created_by uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_data_record_ingest_method CHECK (ingest_method IN ('UDP', 'CSV')),
    CONSTRAINT ck_data_record_source_job CHECK (
        (ingest_method = 'UDP' AND file_job_id IS NULL) OR
        (ingest_method = 'CSV' AND file_job_id IS NOT NULL)
    ),
    CONSTRAINT ck_data_record_source_port CHECK (source_port IS NULL OR source_port BETWEEN 1 AND 65535),
    CONSTRAINT ck_data_record_payload_count CHECK (payload_count > 0),
    CONSTRAINT ck_data_record_raw CHECK (raw_payload IS NOT NULL OR raw_text IS NOT NULL),
    CONSTRAINT ck_data_record_parse_status CHECK (parse_status IN ('RECEIVED', 'PARSED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_data_record_version CHECK (version > 0),
    CONSTRAINT ck_data_record_origin CHECK (origin IS NULL OR origin ~ '^[A-Z0-9]{4}$'),
    CONSTRAINT ck_data_record_destination CHECK (destination IS NULL OR destination ~ '^[A-Z0-9]{4}$'),
    CONSTRAINT ck_data_record_deleted CHECK (
        (is_deleted = false AND deleted_at IS NULL AND deleted_by IS NULL) OR
        (is_deleted = true AND deleted_at IS NOT NULL)
    )
);

CREATE INDEX idx_data_record_received_active
    ON data_record (received_at DESC) WHERE is_deleted = false;
CREATE INDEX idx_data_record_type_received_active
    ON data_record (data_type_code, received_at DESC) WHERE is_deleted = false;
CREATE INDEX idx_data_record_flight_received_active
    ON data_record (flight_no, received_at DESC) WHERE is_deleted = false;
CREATE INDEX idx_data_record_device_received_active
    ON data_record (source_device_code, received_at DESC) WHERE is_deleted = false;
CREATE INDEX idx_data_record_airline_received_active
    ON data_record (airline_code, received_at DESC) WHERE is_deleted = false;
CREATE INDEX idx_data_record_model_received_active
    ON data_record (aircraft_model, received_at DESC) WHERE is_deleted = false;
CREATE INDEX idx_data_record_route_received_active
    ON data_record (origin, destination, received_at DESC) WHERE is_deleted = false;
CREATE INDEX idx_data_record_parse_received_active
    ON data_record (parse_status, received_at DESC) WHERE is_deleted = false;
CREATE INDEX idx_data_record_deleted_cleanup
    ON data_record (is_deleted, deleted_at);
CREATE INDEX idx_data_record_file_job
    ON data_record (file_job_id) WHERE file_job_id IS NOT NULL;
