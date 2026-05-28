package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.engine.orchestration.spi.ExecutorDefinition;
import com.huawei.ascend.service.runtime.capability.Capability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end execution test (dev-posture realization of ADR-0070's dispatcher).
 *
 * <p>Proves that {@code POST /v1/runs} for a registered {@link Capability}
 * is driven to terminal {@code SUCCEEDED} by the real
 * {@link OrchestratingAsyncRunDispatcher} → in-memory orchestrator → graph
 * executor path. With the prior {@link NoOpAsyncRunDispatcher} default the Run
 * would remain {@code PENDING} forever, so reaching {@code SUCCEEDED} is the
 * discriminating assertion.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.posture=dev",
        "app.auth.issuer=https://issuer.test",
        "app.auth.audience=spring-ai-ascend",
        "app.auth.jwks-uri=https://issuer.test/.well-known/jwks.json"
})
class RunExecutionIT {

    private static final String CAPABILITY = "it-exec-demo";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("springaiascend")
            .withUsername("springaiascend")
            .withPassword("springaiascend");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        JwtDecoder fixtureJwtDecoder() {
            return JwtTestFixture.decoder();
        }

        /**
         * A single-node graph capability that completes immediately, exercising
         * the dispatcher → orchestrator → {@code SequentialGraphExecutor} path
         * without any external dependency.
         */
        @Bean
        Capability itExecDemoCapability() {
            return new Capability() {
                @Override
                public String capabilityName() {
                    return CAPABILITY;
                }

                @Override
                public ExecutorDefinition definition() {
                    ExecutorDefinition.NodeFunction node =
                            (ctx, payload) -> Map.of("executed", true, "tenant", ctx.tenantId());
                    return new ExecutorDefinition.GraphDefinition(
                            Map.of("run", node), Map.of(), "run");
                }
            };
        }
    }

    @LocalServerPort
    int port;

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void runReachesSucceededViaOrchestrator() throws Exception {
        UUID tenant = UUID.randomUUID();
        String bearer = JwtTestFixture.mintForTenant(tenant);

        HttpResponse<String> created = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Tenant-Id", tenant.toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"capabilityName\":\"" + CAPABILITY + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(202);
        String runId = JSON.readTree(created.body()).path("runId").asText();
        assertThat(runId).isNotBlank();

        String terminalStatus = pollUntilTerminal(runId, tenant, bearer);
        assertThat(terminalStatus)
                .as("dev-posture dispatcher must drive the Run to SUCCEEDED via the orchestrator; "
                        + "the NoOp default would leave it PENDING")
                .isEqualTo("SUCCEEDED");
    }

    private String pollUntilTerminal(String runId, UUID tenant, String bearer) throws Exception {
        for (int attempt = 0; attempt < 400; attempt++) {
            HttpResponse<String> got = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs/" + runId))
                            .header("Authorization", "Bearer " + bearer)
                            .header("X-Tenant-Id", tenant.toString())
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(got.statusCode()).isEqualTo(200);
            String status = JSON.readTree(got.body()).path("status").asText();
            if (!"PENDING".equals(status) && !"RUNNING".equals(status)) {
                return status;
            }
            Thread.sleep(25);
        }
        return "TIMEOUT";
    }
}
