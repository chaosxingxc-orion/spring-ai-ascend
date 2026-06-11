package com.huawei.ascend.service.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.service.core.InMemoryRuntimeRegistry;
import com.huawei.ascend.service.core.RuntimeA2aGateway;
import com.huawei.ascend.service.spi.discovery.AgentDirectory;
import com.huawei.ascend.service.spi.registry.RuntimeRegistry;
import com.huawei.ascend.service.spi.routing.RouteGrantService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AgentServiceAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentServiceAutoConfiguration.class));

    @Test
    void autoConfiguresTheServiceEdgeByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RuntimeRegistry.class);
            assertThat(context).hasSingleBean(AgentDirectory.class);
            assertThat(context).hasSingleBean(RouteGrantService.class);
            assertThat(context).hasSingleBean(RuntimeA2aGateway.class);
            assertThat(context).hasSingleBean(RuntimeRegistryController.class);
            assertThat(context).hasSingleBean(A2aGatewayController.class);
            assertThat(context).hasSingleBean(RouteGrantController.class);
            // Registry and directory are one in-memory store, so a runtime
            // registered through one surface is discoverable through the other.
            assertThat(context.getBean(RuntimeRegistry.class))
                    .isSameAs(context.getBean(AgentDirectory.class));
        });
    }

    @Test
    void disablingThePropertySwitchesTheWholeEdgeOff() {
        contextRunner.withPropertyValues("agent-service.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(RuntimeRegistry.class);
            assertThat(context).doesNotHaveBean(RuntimeRegistryController.class);
            assertThat(context).doesNotHaveBean(A2aGatewayController.class);
        });
    }

    @Test
    void deploymentProvidedRegistryWinsTheConditionalSeam() {
        contextRunner.withUserConfiguration(CustomRegistryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(RuntimeRegistry.class);
            assertThat(context.getBean(RuntimeRegistry.class))
                    .isSameAs(context.getBean(CustomRegistryConfiguration.class).registry);
        });
    }

    @Test
    void jwtFilterIsAbsentUntilEnabled() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void jwtFilterGuardsTheServiceRoutesWhenEnabled() {
        contextRunner.withPropertyValues(
                "agent-service.access.jwt.enabled=true",
                "agent-service.access.jwt.hmac-secret=service-test-secret-which-is-long-enough")
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
                    assertThat(registration.getFilter()).isInstanceOf(ServiceTenantAuthFilter.class);
                    assertThat(registration.getUrlPatterns())
                            .contains("/v1/runtime-registrations", "/v1/agents", "/v1/route-grants");
                });
    }

    @Test
    void jwtEnabledWithoutSecretFailsStartup() {
        contextRunner.withPropertyValues("agent-service.access.jwt.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRegistryConfiguration {

        final InMemoryRuntimeRegistry registry = new InMemoryRuntimeRegistry();

        @Bean
        InMemoryRuntimeRegistry customRegistryStore() {
            return registry;
        }
    }
}
