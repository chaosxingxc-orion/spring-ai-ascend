package ascend.springai.service.platform.probe;

/*
 * W0 U2 promotion probe for agent-platform critical-path deps.
 *
 * Imports the cited APIs from each pinned dep so a successful `mvn compile`
 * proves the API exists at the version pinned by the parent POM. Per
 * docs/cross-cutting/oss-bill-of-materials.md (W0 promotes these to U2).
 *
 * Spring Boot 4.0.5, Spring Security 6, Flyway 11.19.1, Resilience4j 2.4.0,
 * Caffeine 3.2.4, springdoc-openapi 3.0.3, Logstash 8.0 verified at U2
 * once this probe compiles.
 *
 * This class has no runtime caller; it exists for the compiler.
 */

// Spring Web (Boot starter)
import org.springframework.web.bind.annotation.RestController;
// Spring Security 6 (oauth2-resource-server)
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
// Spring Data JDBC + Postgres
import org.springframework.jdbc.core.JdbcTemplate;
// Flyway
import org.flywaydb.core.Flyway;
// Resilience4j (Spring Boot 3 starter)
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
// Caffeine
import com.github.benmanes.caffeine.cache.Caffeine;
// Spring Cloud Vault
import org.springframework.cloud.vault.config.VaultProperties;
// Hibernate Validator
import jakarta.validation.Validator;
// Springdoc OpenAPI
import org.springdoc.core.models.GroupedOpenApi;
// Micrometer + Prometheus
import io.micrometer.core.instrument.MeterRegistry;
// Logback JSON encoder
import net.logstash.logback.encoder.LogstashEncoder;

public final class PlatformOssApiProbe {

    private PlatformOssApiProbe() {}

    public static String probe() {
        // Each dep below cites at least one symbol; classloader-resolution
        // and method-signature checks happen at compile + class-load time.
        Class<?>[] cites = new Class<?>[]{
                RestController.class,
                JwtDecoder.class,
                SecurityFilterChain.class,
                JdbcTemplate.class,
                Flyway.class,
                CircuitBreaker.class,
                RateLimiter.class,
                Caffeine.class,
                VaultProperties.class,
                Validator.class,
                GroupedOpenApi.class,
                MeterRegistry.class,
                LogstashEncoder.class
        };
        StringBuilder sb = new StringBuilder("agent-platform U2 probe: ");
        for (Class<?> c : cites) {
            sb.append(c.getSimpleName()).append(' ');
        }
        return sb.toString();
    }
}
