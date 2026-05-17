package ascend.springai.service.platform;

import ascend.springai.service.platform.auth.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * spring-ai-ascend platform entry point.
 *
 * <p>L1: JWT validation via {@link AuthProperties} (ADR-0056); idempotency
 * dedup, posture boot guard, and run HTTP API land in subsequent L1 phases.
 *
 * <p>{@link ConfigurationPropertiesScan} picks up every {@code @ConfigurationProperties}
 * record under {@code ascend.springai.service.platform..}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("ascend.springai.service.platform")
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
