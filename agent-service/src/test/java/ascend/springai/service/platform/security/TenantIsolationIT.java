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
 * Tenant isolation integration test.
 *
 * Verifies that data written under tenant A is not visible to queries
 * executing under tenant B's GUC context (app.tenant_id = B).
 *
 * Scaffold only -- W2 wires the real persistence layer and applies
 * rls-policy.sql. Remove @Disabled when W2 schema migration lands.
 *
 * References: docs/security/rls-policy.sql
 *             docs/cross-cutting/security-control-matrix.md C3
 */
@Disabled("L0 scaffold; W2 wires real persistence layer. Remove @Disabled when rls-policy.sql is applied.")
@Testcontainers
@SpringBootTest
class TenantIsolationIT {

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
    void tenantADataIsNotVisibleToTenantB() {
        // TODO W2: insert row under tenant A GUC, then query under tenant B GUC,
        // assert empty result set. Apply rls-policy.sql before asserting.
        throw new UnsupportedOperationException("Scaffold only -- W2 wires persistence. See docs/security/rls-policy.sql.");
    }
}
