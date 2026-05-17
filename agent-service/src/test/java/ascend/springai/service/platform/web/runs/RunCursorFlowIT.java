package ascend.springai.service.platform.web.runs;

import ascend.springai.service.runtime.runs.Run;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule 36.b — Cursor Flow Integration Test (Phase 8 / ADR-0070).
 *
 * <p>Asserts that {@code POST /v1/runs} returns HTTP 202 within 200 ms with a
 * {@code TaskCursor} payload, even when the registered {@link AsyncRunDispatcher}
 * synchronously blocks for 30 seconds. {@link RunController} must dispatch via
 * {@code CompletableFuture.runAsync(...)} so the HTTP response is never gated on
 * the dispatcher's runtime.
 *
 * <p>This test is the operational counterpart of Gate Rule 46
 * ({@code cursor_flow_documented}, which checks the OpenAPI schema) and Gate
 * Rule 53 ({@code cursor_flow_integration_test_present}, which checks this test
 * exists).
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E72.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.posture=dev",
        "app.auth.issuer=https://issuer.test",
        "app.auth.audience=spring-ai-ascend",
        "app.auth.jwks-uri=https://issuer.test/.well-known/jwks.json"
})
class RunCursorFlowIT {

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
         * Synthetic 30-second-blocking dispatcher. The dispatcher itself blocks; the
         * controller is required to fire-and-forget so the POST response still
         * arrives within 200 ms.
         */
        @Bean
        @Primary
        AsyncRunDispatcher blockingDispatcher() {
            return (Run run) -> {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
        }
    }

    @LocalServerPort
    int port;

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void createReturns202WithCursorWithin200ms() throws Exception {
        UUID tenant = UUID.randomUUID();
        String bearer = JwtTestFixture.mintForTenant(tenant);
        long startNanos = System.nanoTime();
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Tenant-Id", tenant.toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .POST(HttpRequest.BodyPublishers.ofString("{\"capabilityName\":\"echo\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(elapsedMs)
                .as("POST /v1/runs must return within 200 ms even when the dispatcher blocks 30 s")
                .isLessThan(200L);

        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("runId").asText()).isNotBlank();
        assertThat(body.path("cursor_url").asText())
                .matches("https?://[^/]+/v1/runs/" + body.path("runId").asText());
    }
}
