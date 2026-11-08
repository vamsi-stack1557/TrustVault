CREATE OR REPLACE FUNCTION reject_audit_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only; UPDATE and DELETE are forbidden';
END;
$$;

CREATE TRIGGER trg_audit_events_append_only
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW
EXECUTE FUNCTION reject_audit_event_mutation();

-- The runtime role is intentionally not the owner of the schema/tables.
-- It can mutate business data and append audit events, but cannot update/delete audit rows.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'trustvault_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE ON app_users TO trustvault_app';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE ON vault_documents TO trustvault_app';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON document_grants TO trustvault_app';
        EXECUTE 'GRANT SELECT, INSERT ON audit_events TO trustvault_app';
        EXECUTE 'GRANT SELECT, UPDATE ON audit_chain_state TO trustvault_app';
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO trustvault_app';
    END IF;
END;
$$;
