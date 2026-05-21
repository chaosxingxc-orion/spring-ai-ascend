package com.huawei.ascend.service.platform;

import com.huawei.ascend.service.platform.auth.AuthProperties;
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
 * record under {@code com.huawei.ascend.service.platform..}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.huawei.ascend.service.platform")
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
