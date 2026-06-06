package com.huawei.ascend.runtime.access.protocol.a2a;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

// Serialised against other Spring context-booting tests via a shared READ_WRITE
// lock: Spring Boot 4.0.5 ApplicationContext boot is not thread-safe and two
// concurrent boots in one reused Surefire fork can deadlock.
@ResourceLock(value = "spring-context-boot", mode = ResourceAccessMode.READ_WRITE)
class A2aAccessPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsA2aDefaultsFromAgentRuntimePrefix() {
        contextRunner
                .withPropertyValues(
                        "agent-runtime.access.a2a.default-tenant-id=tenant-from-runtime-prefix",
                        "agent-runtime.access.a2a.default-agent-id=agent-from-runtime-prefix")
                .run(context -> {
                    A2aAccessProperties properties = context.getBean(A2aAccessProperties.class);

                    assertThat(properties.getDefaultTenantId()).isEqualTo("tenant-from-runtime-prefix");
                    assertThat(properties.getDefaultAgentId()).isEqualTo("agent-from-runtime-prefix");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(A2aAccessProperties.class)
    static class TestConfiguration {
    }
}
