-- spring-ai-ascend Row-Level Security Policy
-- Applied during schema migration W2 when the persistence layer materializes.
-- Owner: persistence-starter | Maturity: L0 (design only; applied in W2)
-- References: ADR-05 (tenant isolation via GUC SET LOCAL)
--             docs/cross-cutting/statelessness-and-partition-policy.md
--             docs/cross-cutting/security-control-matrix.md C3-C5

-- GUC assertion: reject transactions that did not call SET LOCAL app.tenant_id.
-- Applied at transaction begin via session_replication_role trigger or advisory lock.

-- Enable RLS on each tenant-scoped table.
-- Replace <table> with actual table names when schema is created in W2.

-- Pattern:
-- ALTER TABLE <table> ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE <table> FORCE ROW LEVEL SECURITY;

-- CREATE POLICY tenant_isolation_policy ON <table>
--     USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- GUC empty assertion trigger (fires on every DML if app.tenant_id is not set).
CREATE OR REPLACE FUNCTION assert_tenant_guc_set()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF current_setting('app.tenant_id', true) IS NULL OR
       current_setting('app.tenant_id', true) = '' THEN
        RAISE EXCEPTION 'tenant_isolation_violation: app.tenant_id GUC is not set. '
            'Every DML must be preceded by SET LOCAL app.tenant_id = <uuid>.';
    END IF;
    RETURN NEW;
END;
$$;

-- Template: Attach trigger to each tenant-scoped table.
-- CREATE TRIGGER check_tenant_guc
--     BEFORE INSERT OR UPDATE OR DELETE ON <table>
--     FOR EACH ROW EXECUTE FUNCTION assert_tenant_guc_set();

-- Cross-tenant escape test: verify that a query under tenant A cannot see tenant B data.
-- Tested in TenantIsolationIT (see agent-platform/src/test/java/com/huawei/ascend/platform/security/).
