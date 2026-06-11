package com.huawei.ascend.examples.a2a.gateway;

import com.huawei.ascend.service.spi.discovery.AgentDirectory;
import com.huawei.ascend.service.spi.discovery.RoutingContext;
import com.huawei.ascend.service.spi.registry.RuntimeAgentRegistration;
import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import com.huawei.ascend.service.spi.registry.RuntimeRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import com.huawei.ascend.service.starter.AgentServiceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeRegistryPingPongE2eTest {

    private static final String TENANT = "tenant-ping";
    private static final String AGENT = "ping-agent";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentServiceAutoConfiguration.class));

    @Test
    void runtimeRegistrationCanBeDiscoveredAndRoutedByServiceFacade() {
        contextRunner.run(context -> {
            RuntimeRegistry runtimeRegistry = context.getBean(RuntimeRegistry.class);
            AgentDirectory directory = context.getBean(AgentDirectory.class);

            runtimeRegistry.register(new RuntimeAgentRegistration(
                    RuntimeInstanceId.of("runtime-ping-1"),
                    TENANT,
                    AGENT,
                    agentCard(),
                    URI.create("http://runtime-ping-1.local/a2a"),
                    URI.create("http://runtime-ping-1.local/v1/health"),
                    "1.0.0",
                    Duration.ofSeconds(30),
                    Map.of("zone", "az-1")));

            AgentCard discoveredCard = directory.getAgentCard(AGENT, TENANT);
            var route = directory.resolveRoute(
                    AGENT,
                    TENANT,
                    new RoutingContext("session-ping", "corr-ping", Map.of("message", "ping")));

            assertThat(discoveredCard.name()).isEqualTo(AGENT);
            assertThat(route.runtimeInstanceId()).isEqualTo(RuntimeInstanceId.of("runtime-ping-1"));
            assertThat(route.a2aEndpoint()).isEqualTo(URI.create("http://runtime-ping-1.local/a2a"));
            assertThat(directory.listAgents(TENANT)).extracting("agentId").containsExactly(AGENT);
        });
    }

    private static AgentCard agentCard() {
        return AgentCard.builder()
                .name(AGENT)
                .description("Ping Pong agent exposed by one runtime instance")
                .url("/a2a")
                .version("1.0.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:8080"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .extendedAgentCard(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.JSONRPC.asString(),
                        "/a2a")))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
    }
}
