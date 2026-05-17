package ascend.springai.service.platform.security;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that every tenant-scoped table has an RLS policy applied.
 *
 * Scaffold only -- W2 creates the schema and applies rls-policy.sql.
 *
 * References: docs/security/rls-policy.sql
 *             docs/cross-cutting/security-control-matrix.md C4
 */
@Disabled("L0 scaffold; W2 schema required. Remove @Disabled when W2 migration lands.")
@Testcontainers
@SpringBootTest
class RlsPolicyCoverageIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("springAiAscend")
            .withUsername("springAiAscend")
            .withPassword("springAiAscend");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void allTenantScopedTablesHaveRlsPolicy() {
        // TODO W2: query pg_policies for each expected table and assert
        // a tenant_isolation_policy exists with using-clause targeting app.tenant_id GUC.
        throw new UnsupportedOperationException("Scaffold only -- W2 schema required. See docs/security/rls-policy.sql.");
    }
}
