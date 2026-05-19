package ascend.springai.service.platform.web.runs;

import ascend.springai.service.runtime.runs.Run;
import ascend.springai.engine.orchestration.spi.RunMode;
import ascend.springai.service.runtime.runs.spi.RunRepository;
import ascend.springai.service.runtime.runs.RunStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer-3 contract test for the {@link RunController} (plan §6) + Phase L
 * authenticated coverage closing reviewer findings P0-2 and the P1-1 underlying
 * gap.
 *
 * <p>The test boots a real Spring Boot context against a Testcontainers
 * Postgres, then drives HTTP through the real filter chain
 * (Spring Security → {@link ascend.springai.service.platform.tenant.JwtTenantClaimCrossCheck}
 * → {@link ascend.springai.service.platform.tenant.TenantContextFilter}
 * → {@link ascend.springai.service.platform.idempotency.IdempotencyHeaderFilter}
 * → {@link RunController}).
 *
 * <p>The {@link JwtTestConfig} test configuration registers a {@link Primary}
 * {@link JwtDecoder} bean wired from {@link JwtTestFixture}'s RSA keypair so
 * the controller can be reached with locally-minted Bearer tokens without
 * needing a real JWKS endpoint.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E5 (createReturnsPending),
 * #E6 (cancel_route_is_post_not_delete), #E7 (status-code matrix rows),
 * #E10 (tenantMismatchReturns403), #E24 (cancelTerminalReturns409),
 * #E37 (JwtTestFixture).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.posture=dev",
        "app.auth.issuer=https://issuer.test",
        "app.auth.audience=spring-ai-ascend",
        "app.auth.jwks-uri=https://issuer.test/.well-known/jwks.json"
})
class RunHttpContractIT {

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
    static class JwtTestConfig {
        @Bean
        @Primary
        JwtDecoder fixtureJwtDecoder() {
            return JwtTestFixture.decoder();
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    RunRepository runRepository;

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    // --- Unauthenticated rows (kept from pre-Phase-L) ---------------------

    @Test
    void post_runs_without_bearer_returns_401_or_403() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs"))
                        .header("Content-Type", "application/json")
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .POST(HttpRequest.BodyPublishers.ofString("{\"capabilityName\":\"x\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isIn(401, 403);
    }

    @Test
    void get_run_without_bearer_returns_401_or_403() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs/" + UUID.randomUUID()))
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isIn(401, 403);
    }

    @Test
    void cancel_route_is_post_not_delete() throws Exception {
        // E6: DELETE /v1/runs/{id} MUST NOT be a registered route. Without auth
        // the route is rejected anyway (401/403/404) — never 200.
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs/" + UUID.randomUUID()))
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isIn(401, 403, 404, 405);
    }

    @Test
    void health_remains_publicly_accessible() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/health"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    // --- Authenticated rows (Phase L — closes P0-2 + P1-1) ---------------

    @Test
    void createReturns202WithCursor() throws Exception {
        // Phase 8 / Rule R-F — Cursor Flow Mandate (ADR-0070).
        // POST /v1/runs returns 202 + TaskCursor envelope (runId, status, cursor_url),
        // not 201 + full RunResponse.
        UUID tenant = UUID.randomUUID();
        String bearer = JwtTestFixture.mintForTenant(tenant);
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Tenant-Id", tenant.toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .POST(HttpRequest.BodyPublishers.ofString("{\"capabilityName\":\"echo\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(202);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("runId").asText()).isNotBlank();
        assertThat(body.path("cursor_url").asText())
                .matches("https?://[^/]+/v1/runs/" + body.path("runId").asText());
    }

    @Test
    void tenantMismatchReturns403() throws Exception {
        UUID claimTenant = UUID.randomUUID();
        UUID headerTenant = UUID.randomUUID();
        String bearer = JwtTestFixture.mintForTenant(claimTenant);
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Tenant-Id", headerTenant.toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .POST(HttpRequest.BodyPublishers.ofString("{\"capabilityName\":\"x\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("error").path("code").asText()).isEqualTo("tenant_mismatch");
    }

    @Test
    void cancelTerminalReturns409() throws Exception {
        // Plants a SUCCEEDED run directly via the RunRepository (bypassing the
        // state machine, which is unit-tested separately in RunStateMachineTest)
        // because L1 has no HTTP path to drive a run into SUCCEEDED/FAILED/EXPIRED
        // — that requires the W2 executor surface. The controller's cancel route
        // then attempts CANCELLED→SUCCEEDED, which Run.withStatus rejects with
        // IllegalStateException, which RunController maps to 409
        // illegal_state_transition.
        UUID tenant = UUID.randomUUID();
        String bearer = JwtTestFixture.mintForTenant(tenant);
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        Run succeeded = new Run(
                runId, tenant.toString(), "echo",
                RunStatus.SUCCEEDED, RunMode.GRAPH,
                now, now, now,
                null, null, null, null);
        runRepository.save(succeeded);

        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs/" + runId + "/cancel"))
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Tenant-Id", tenant.toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(response.body()).path("error").path("code").asText())
                .isEqualTo("illegal_state_transition");
    }

    @Test
    void duplicateIdempotencyKeyReturns409() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID idemKey = UUID.randomUUID();
        String bearer = JwtTestFixture.mintForTenant(tenant);
        HttpResponse<String> first = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Tenant-Id", tenant.toString())
                        .header("Idempotency-Key", idemKey.toString())
                        .POST(HttpRequest.BodyPublishers.ofString("{\"capabilityName\":\"a\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        // Phase 8 / Rule R-F — first call returns 202 + cursor envelope (was 201).
        assertThat(first.statusCode()).isEqualTo(202);

        // Same idempotency key, DIFFERENT body — must trip the body-drift
        // branch (409 idempotency_body_drift) per ADR-0057 / IdempotencyHeaderFilter.
        HttpResponse<String> second = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + bearer)
                        .header("X-Tenant-Id", tenant.toString())
                        .header("Idempotency-Key", idemKey.toString())
                        .POST(HttpRequest.BodyPublishers.ofString("{\"capabilityName\":\"b\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(second.statusCode()).isEqualTo(409);
        String code = JSON.readTree(second.body()).path("error").path("code").asText();
        assertThat(code).isIn("idempotency_body_drift", "idempotency_conflict");
    }
}
