package ascend.springai.service.platform.posture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the APP_POSTURE -> app.posture bridge defined in application.yml:66.
 * The bridge: app.posture: ${APP_POSTURE:dev}
 * Sets APP_POSTURE=research as a Spring property (simulating the OS env var)
 * and asserts that app.posture resolves to research in both the raw Environment
 * and in posture-sensitive filter behavior.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "APP_POSTURE=research",
                // Required by PostureBootGuard (ADR-0058) in research/prod posture.
                // Stub values satisfy AuthProperties.hasJwksConfig() without actually
                // hitting a remote issuer — this test does not authenticate.
                "app.auth.issuer=https://issuer.test",
                "app.auth.jwks-uri=https://issuer.test/.well-known/jwks.json",
                "app.auth.audience=spring-ai-ascend"
        })
class PostureBindingIT {

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

    @Autowired
    private Environment env;

    @LocalServerPort
    private int port;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Test
    void appPosture_resolves_from_APP_POSTURE_via_yaml_placeholder() {
        // The application.yml bridge: app.posture: ${APP_POSTURE:dev}
        // With APP_POSTURE=research in test properties, app.posture must be research.
        assertThat(env.getProperty("app.posture")).isEqualTo("research");
    }

    @Test
    void researchPosture_securityChain_rejects_unallowlisted_path() throws Exception {
        // Behavioral proof that the request chain is engaged. With the L1 JWT
        // decoder wired (ADR-0056), oauth2ResourceServer responds 401 to a
        // request with no Bearer token. (W0 returned 403 from denyAll; L1's
        // authenticated() chain plus oauth2 advertises Bearer with 401
        // instead.) Either response signals the deny-by-default policy.
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/runs")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isIn(401, 403);
    }
}
