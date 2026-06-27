-- W22: Audit ledger hardening — append-only (WORM) guard for run_events on PostgreSQL.
-- Runtime app account should have INSERT/SELECT only; migrations use a separate DDL account.

CREATE OR REPLACE FUNCTION workmate_deny_run_events_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'run_events is append-only (WORM): UPDATE/DELETE denied';
END;
$$;

DROP TRIGGER IF EXISTS run_events_worm_guard ON run_events;

CREATE TRIGGER run_events_worm_guard
    BEFORE UPDATE OR DELETE ON run_events
    FOR EACH ROW
    EXECUTE FUNCTION workmate_deny_run_events_mutation();
