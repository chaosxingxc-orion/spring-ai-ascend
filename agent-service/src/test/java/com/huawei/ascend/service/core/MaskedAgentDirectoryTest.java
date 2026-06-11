package com.huawei.ascend.service.core;

import com.huawei.ascend.service.spi.discovery.AgentCardSummary;
import com.huawei.ascend.service.spi.discovery.AgentDirectory;
import com.huawei.ascend.service.spi.discovery.RoutingContext;
import com.huawei.ascend.service.spi.discovery.RuntimeRoute;
import com.huawei.ascend.service.spi.registry.RuntimeAgentRegistration;
import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import com.huawei.ascend.service.testsupport.AgentCards;
import com.huawei.ascend.service.testsupport.MutableClock;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskedAgentDirectoryTest {

    private static final Instant NOW = Instant.parse("2026-06-04T10:00:00Z");
    private static final String TENANT = "tenant-a";
    private static final URI RUNTIME_ENDPOINT = URI.create("http://runtime-1.internal:8443/a2a");

    private final InMemoryRuntimeRegistry registry = new InMemoryRuntimeRegistry(new MutableClock(NOW));
    private final MaskedAgentDirectory masked =
            new MaskedAgentDirectory(registry, "https://edge.example.com/");

    @Test
    void maskedCardCarriesTheGatewayRouteForUrlAndEveryInterface() {
        AgentCard runtimeCard = AgentCard.builder(AgentCards.agentCard("agent-weather"))
                .url(RUNTIME_ENDPOINT.toString())
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), RUNTIME_ENDPOINT.toString()),
                        new AgentInterface("HTTP+JSON", "http://runtime-1.internal:8443/rest")))
                .build();
        register(runtimeCard);

        AgentCard card = masked.getAgentCard("agent-weather", TENANT);

        String gatewayRoute = "https://edge.example.com/v1/agents/agent-weather/a2a";
        assertThat(card.url()).isEqualTo(gatewayRoute);
        assertThat(card.supportedInterfaces())
                .hasSize(2)
                .allSatisfy(agentInterface -> assertThat(agentInterface.url()).isEqualTo(gatewayRoute));
        assertThat(card.supportedInterfaces())
                .extracting(AgentInterface::protocolBinding)
                .containsExactly(TransportProtocol.JSONRPC.asString(), "HTTP+JSON");
        // Masking touches only endpoint fields; identity metadata survives.
        assertThat(card.name()).isEqualTo("agent-weather");
        assertThat(card.version()).isEqualTo("1.0.0");
    }

    @Test
    void listAgentsAndResolveRouteKeepRealEndpoints() {
        register(AgentCards.agentCard("agent-weather"));

        List<AgentCardSummary> summaries = masked.listAgents(TENANT);
        assertThat(summaries).isEqualTo(registry.listAgents(TENANT));
        assertThat(summaries.getFirst().a2aEndpoint()).isEqualTo(RUNTIME_ENDPOINT);

        RuntimeRoute route = masked.resolveRoute("agent-weather", TENANT, RoutingContext.empty());
        assertThat(route.a2aEndpoint()).isEqualTo(RUNTIME_ENDPOINT);
    }

    @Test
    void blankPublicBaseUrlIsRejected() {
        assertThatThrownBy(() -> new MaskedAgentDirectory(registry, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void register(AgentCard agentCard) {
        registry.register(new RuntimeAgentRegistration(
                RuntimeInstanceId.of("runtime-1"),
                TENANT,
                "agent-weather",
                agentCard,
                RUNTIME_ENDPOINT,
                URI.create("http://runtime-1.internal:8443/health"),
                "1.0.0",
                Duration.ofSeconds(60),
                Map.of()));
    }
}
