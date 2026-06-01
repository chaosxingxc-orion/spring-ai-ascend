package com.huawei.ascend.service.access.temp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;

/**
 * Temporary L1-only Spring Boot entry point for validating A2A ingress while the
 * platform application still depends on platform persistence wiring.
 *
 * <p>This class scans only {@code com.huawei.ascend.service.access}. Delete it
 * when the normal platform startup path can load L1 and the real L4
 * {@link com.huawei.ascend.service.access.core.TaskHandler} bean is available.
 */
@SpringBootApplication(
        scanBasePackages = "com.huawei.ascend.service.access",
        exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                FlywayAutoConfiguration.class,
                PgVectorStoreAutoConfiguration.class,
                SecurityAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class,
                ManagementWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        })
@ConfigurationPropertiesScan("com.huawei.ascend.service.access")
public class TemporaryA2aApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemporaryA2aApplication.class, args);
    }
}


