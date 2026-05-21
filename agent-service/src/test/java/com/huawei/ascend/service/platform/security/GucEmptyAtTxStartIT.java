package com.huawei.ascend.service.platform.security;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that app.tenant_id GUC is empty at transaction start (no bleed-through).
 *
 * Uses SET LOCAL so the GUC resets at transaction commit/rollback.
 * This test confirms that a second transaction under a different tenant cannot
 * inherit the GUC from a previous transaction on the same connection.
 *
 * Scaffold only -- W2 wires the connection pool with GUC lifecycle hooks.
 *
 * References: docs/security/rls-policy.sql
 *             docs/cross-cutting/security-control-matrix.md C5
 *             docs/cross-cutting/statelessness-and-partition-policy.md
 */
@Disabled("L0 scaffold; W2 connection-pool GUC hooks required. Remove @Disabled when W2 persistence lands.")
@Testcontainers
@SpringBootTest
class GucEmptyAtTxStartIT {

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
    void gucIsNullAtTransactionStart() {
        // TODO W2: execute two sequential transactions. In tx1, SET LOCAL app.tenant_id = uuid1.
        // In tx2 (same connection), assert current_setting('app.tenant_id', true) IS NULL.
        // This proves SET LOCAL reset occurs at tx end, preventing GUC bleed-through.
        throw new UnsupportedOperationException("Scaffold only -- W2 connection pool required.");
    }
}
