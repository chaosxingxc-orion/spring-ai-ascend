package ascend.springai.service.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W0 acceptance test: GET /v1/health returns 200, body has status + sha +
 * db_ping_ns + ts. Real Postgres via Testcontainers proves Flyway applies
 * V1__init.sql and JdbcTemplate reaches the DB.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIT {

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

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Test
    @SuppressWarnings("unchecked")
    void healthReturnsUpWithDbPingAndSha() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/health")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);

        Map<String, Object> body = MAPPER.readValue(response.body(), Map.class);
        assertThat(body.get("status")).isEqualTo("UP");
        assertThat(body.get("sha")).isNotNull();
        assertThat(((Number) body.get("db_ping_ns")).longValue()).isGreaterThan(0L);
        assertThat(body.get("ts")).isNotNull();
    }
}
