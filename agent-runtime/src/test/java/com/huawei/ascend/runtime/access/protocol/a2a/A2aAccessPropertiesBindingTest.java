package com.huawei.ascend.runtime.access.protocol.a2a;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class A2aAccessPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsA2aDefaultsFromAgentRuntimePrefix() {
        contextRunner
                .withPropertyValues(
                        "agent-runtime.access.a2a.default-tenant-id=tenant-from-runtime-prefix",
                        "agent-runtime.access.a2a.default-agent-id=agent-from-runtime-prefix",
                        "agent-runtime.access.a2a.public-base-url=https://agents.example.com/runtime-one")
                .run(context -> {
                    A2aAccessProperties properties = context.getBean(A2aAccessProperties.class);

                    assertThat(properties.getDefaultTenantId()).isEqualTo("tenant-from-runtime-prefix");
                    assertThat(properties.getDefaultAgentId()).isEqualTo("agent-from-runtime-prefix");
                    assertThat(properties.getPublicBaseUrl()).isEqualTo("https://agents.example.com/runtime-one");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(A2aAccessProperties.class)
    static class TestConfiguration {
    }
}
