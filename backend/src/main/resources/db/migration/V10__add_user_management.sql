ALTER TABLE app_user DROP CONSTRAINT ck_app_user_role;
ALTER TABLE app_user DROP CONSTRAINT ck_app_user_status;

ALTER TABLE app_user
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN deleted_by uuid REFERENCES app_user(id) ON DELETE RESTRICT,
    ADD COLUMN delete_reason varchar(500);

UPDATE app_user
SET role_code = 'SUPER_ADMIN'
WHERE role_code = 'ADMIN';

UPDATE app_user
SET status = 'FROZEN'
WHERE status = 'DISABLED';

ALTER TABLE app_user ALTER COLUMN role_code SET DEFAULT 'USER';
ALTER TABLE app_user ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_role
        CHECK (role_code IN ('SUPER_ADMIN', 'ADMIN', 'USER')),
    ADD CONSTRAINT ck_app_user_status
        CHECK (status IN ('ACTIVE', 'PENDING', 'FROZEN', 'DELETED')),
    ADD CONSTRAINT ck_app_user_deleted
        CHECK (
            (
                status = 'DELETED'
                AND deleted_at IS NOT NULL
                AND deleted_by IS NOT NULL
                AND length(btrim(delete_reason)) BETWEEN 1 AND 500
            )
            OR
            (
                status <> 'DELETED'
                AND deleted_at IS NULL
                AND deleted_by IS NULL
                AND delete_reason IS NULL
            )
        );

DROP INDEX idx_app_user_status;

CREATE INDEX idx_app_user_status_created
    ON app_user (status, created_at DESC, id);

CREATE INDEX idx_app_user_role_created
    ON app_user (role_code, created_at DESC, id);
