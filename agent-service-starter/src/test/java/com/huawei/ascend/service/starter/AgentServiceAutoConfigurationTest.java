package com.huawei.ascend.service.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.service.core.InMemoryRuntimeRegistry;
import com.huawei.ascend.service.core.RuntimeA2aGateway;
import com.huawei.ascend.service.spi.discovery.AgentDirectory;
import com.huawei.ascend.service.spi.registry.RuntimeAgentRegistration;
import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import com.huawei.ascend.service.spi.registry.RuntimeRegistry;
import com.huawei.ascend.service.spi.routing.RouteGrantService;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
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

    @Test
    void publicBaseUrlMasksCardsServedByTheControllerOnly() {
        contextRunner.withPropertyValues("agent-service.public-base-url=https://edge.example.com")
                .run(context -> {
                    // Masking is a controller-local view, never a second directory bean.
                    assertThat(context).hasSingleBean(AgentDirectory.class);
                    registerWeatherRuntime(context);

                    AgentCard served = context.getBean(RuntimeRegistryController.class)
                            .getAgentCard("agent-weather", "tenant-a");
                    String gatewayRoute = "https://edge.example.com/v1/agents/agent-weather/a2a";
                    assertThat(served.url()).isEqualTo(gatewayRoute);
                    assertThat(served.supportedInterfaces())
                            .isNotEmpty()
                            .allSatisfy(agentInterface ->
                                    assertThat(agentInterface.url()).isEqualTo(gatewayRoute));

                    // The gateway's directory stays unmasked so it can reach the runtime.
                    assertThat(context.getBean(AgentDirectory.class)
                            .getAgentCard("agent-weather", "tenant-a").url())
                            .isEqualTo("http://runtime-1.internal:8443/a2a");
                });
    }

    @Test
    void unsetPublicBaseUrlServesCardsVerbatim() {
        contextRunner.run(context -> {
            registerWeatherRuntime(context);

            AgentCard served = context.getBean(RuntimeRegistryController.class)
                    .getAgentCard("agent-weather", "tenant-a");
            assertThat(served.url()).isEqualTo("http://runtime-1.internal:8443/a2a");
        });
    }

    private static void registerWeatherRuntime(ApplicationContext context) {
        AgentCard agentCard = AgentCard.builder()
                .name("agent-weather")
                .description("agent-weather A2A runtime")
                .url("http://runtime-1.internal:8443/a2a")
                .version("1.0.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://runtime-1.internal:8443"))
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), "http://runtime-1.internal:8443/a2a")))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
        context.getBean(RuntimeRegistry.class).register(new RuntimeAgentRegistration(
                RuntimeInstanceId.of("runtime-1"),
                "tenant-a",
                "agent-weather",
                agentCard,
                URI.create("http://runtime-1.internal:8443/a2a"),
                URI.create("http://runtime-1.internal:8443/health"),
                "1.0.0",
                Duration.ofSeconds(60),
                Map.of()));
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
