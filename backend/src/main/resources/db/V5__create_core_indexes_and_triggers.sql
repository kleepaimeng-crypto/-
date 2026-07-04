CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_app_user_updated_at
BEFORE UPDATE ON app_user
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_data_type_updated_at
BEFORE UPDATE ON data_type
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_file_job_updated_at
BEFORE UPDATE ON file_job
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_data_record_updated_at
BEFORE UPDATE ON data_record
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_tag_updated_at
BEFORE UPDATE ON tag
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_data_annotation_updated_at
BEFORE UPDATE ON data_annotation
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION prevent_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$;

CREATE TRIGGER trg_audit_log_append_only
BEFORE UPDATE OR DELETE ON audit_log
FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
