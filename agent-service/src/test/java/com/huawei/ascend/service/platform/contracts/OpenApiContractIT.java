package com.huawei.ascend.service.platform.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI contract snapshot integration test for the W0 public surface.
 *
 * <p>Loads the pinned contract from the test classpath
 * (src/test/resources/contracts/openapi-v1-pinned.yaml) and diffs it against the
 * live springdoc spec at /v3/api-docs. Every path and operation declared in the
 * pinned file must be present in the live spec. Additive changes (new paths or
 * operations in live that are absent from the pinned file) are allowed. Only
 * breaking removals block the build.</p>
 *
 * <p>Starts a full Spring Boot application with real Postgres via Testcontainers,
 * matching the pattern established in HealthEndpointIT.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiContractIT {

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

    @LocalServerPort
    private int port;

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchLiveSpec() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON_MAPPER.readValue(response.body(), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void liveSpecContainsAllPinnedOperations() throws Exception {
        InputStream pinned = getClass().getResourceAsStream("/contracts/openapi-v1-pinned.yaml");
        assertThat(pinned).as("pinned spec on classpath at /contracts/openapi-v1-pinned.yaml").isNotNull();
        Map<String, Object> pinnedSpec = YAML_MAPPER.readValue(pinned, Map.class);

        Map<String, Object> liveSpec = fetchLiveSpec();
        assertThat(liveSpec).as("live OpenAPI spec from /v3/api-docs").isNotNull();

        OpenApiSnapshotComparator.ComparisonResult result =
                OpenApiSnapshotComparator.compare(pinnedSpec, liveSpec);
        assertThat(result.violations())
                .as("Breaking changes detected: pinned operations missing from live spec")
                .isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void liveSpecResponseSchemasMatchPinnedRequiredFields() throws Exception {
        InputStream pinned = getClass().getResourceAsStream("/contracts/openapi-v1-pinned.yaml");
        assertThat(pinned).as("pinned spec on classpath").isNotNull();
        Map<String, Object> pinnedSpec = YAML_MAPPER.readValue(pinned, Map.class);

        Map<String, Object> liveSpec = fetchLiveSpec();

        OpenApiSnapshotComparator.ComparisonResult result =
                OpenApiSnapshotComparator.compareResponseSchemas(pinnedSpec, liveSpec);
        assertThat(result.violations())
                .as("Response schema drift detected: required fields or types changed")
                .isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void liveSpecInfoIsPresent() throws Exception {
        Map<String, Object> spec = fetchLiveSpec();
        assertThat(spec).containsKey("info");
        Map<String, Object> info = (Map<String, Object>) spec.get("info");
        assertThat(info).containsKey("title");
    }

    /**
     * Phase L (reviewer P0-3): fail when the live spec exposes a {@code /v1/**}
     * operation that is NOT documented in the pinned snapshot, unless explicitly
     * tagged {@code x-experimental: true}. Pins the run lifecycle endpoints
     * shipped at L1 (POST /v1/runs, GET /v1/runs/{runId}, POST /v1/runs/{runId}/cancel).
     *
     * <p>Enforcer row: docs/governance/enforcers.yaml#E36.
     */
    @Test
    @SuppressWarnings("unchecked")
    void noUndocumentedV1OperationsExposedByLive() throws Exception {
        InputStream pinned = getClass().getResourceAsStream("/contracts/openapi-v1-pinned.yaml");
        assertThat(pinned).as("pinned spec on classpath").isNotNull();
        Map<String, Object> pinnedSpec = YAML_MAPPER.readValue(pinned, Map.class);

        Map<String, Object> liveSpec = fetchLiveSpec();

        OpenApiSnapshotComparator.ComparisonResult result =
                OpenApiSnapshotComparator.compareNoUndocumentedLivePaths(pinnedSpec, liveSpec);
        assertThat(result.violations())
                .as("Live spec exposes /v1/** operations not pinned in docs/contracts/openapi-v1.yaml")
                .isEmpty();
    }
}
