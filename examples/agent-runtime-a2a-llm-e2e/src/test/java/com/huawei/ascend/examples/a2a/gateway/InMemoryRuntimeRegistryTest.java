package com.huawei.ascend.examples.a2a.gateway;

import com.huawei.ascend.examples.a2a.gateway.core.InMemoryRuntimeRegistry;
import com.huawei.ascend.examples.a2a.gateway.model.AgentRouteNotFoundException;
import com.huawei.ascend.examples.a2a.gateway.model.GatewayErrorCode;
import com.huawei.ascend.examples.a2a.gateway.model.RoutingContext;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeAgentRegistration;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeCapacitySnapshot;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeInstanceId;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeLeaseRenewal;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeState;
import com.huawei.ascend.examples.a2a.gateway.model.SlaSnapshot;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRuntimeRegistryTest {

    private static final Instant NOW = Instant.parse("2026-06-04T10:00:00Z");

    @Test
    void registerMakesAgentDiscoverableAndRoutable() {
        InMemoryRuntimeRegistry registry = registryAt(NOW);

        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));

        assertThat(registry.getAgentCard("agent-weather", "tenant-a").name()).isEqualTo("agent-weather");
        assertThat(registry.listAgents("tenant-a"))
                .extracting("agentId")
                .containsExactly("agent-weather");
        assertThat(registry.resolveRoute("agent-weather", "tenant-a", RoutingContext.empty()).a2aEndpoint())
                .isEqualTo(URI.create("http://runtime-1.example/a2a"));
    }

    @Test
    void expiredLeaseIsNotRoutable() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRuntimeRegistry registry = new InMemoryRuntimeRegistry(clock);
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(5)));

        clock.set(NOW.plusSeconds(6));

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", "tenant-a", RoutingContext.empty()))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.RUNTIME_UNREACHABLE));

        assertThat(registry.listAgents("tenant-a")).isEmpty();
    }

    @Test
    void coldAndCapacityStatesAreNotRoutable() {
        InMemoryRuntimeRegistry registry = registryAt(NOW);
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.COLD,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                Map.of()));

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", "tenant-a", RoutingContext.empty()))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.RUNTIME_COLD));

        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.AT_CAPACITY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                Map.of()));

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", "tenant-a", RoutingContext.empty()))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.RUNTIME_AT_CAPACITY));
    }

    @Test
    void routePrefersMostRecentlyRenewedReadyRuntime() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRuntimeRegistry registry = new InMemoryRuntimeRegistry(clock);
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));
        registry.register(registration("runtime-2", "agent-weather", Duration.ofSeconds(30)));

        clock.set(NOW.plusSeconds(1));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                Map.of()));

        assertThat(registry.resolveRoute("agent-weather", "tenant-a", RoutingContext.empty()).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-1"));
    }

    @Test
    void readyRuntimeWithFullLlmCapacityIsNotRoutable() {
        InMemoryRuntimeRegistry registry = registryAt(NOW);
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                capacity(1, 0, 1, 1.0, 100),
                Map.of("reason", "llm-saturated")));

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", "tenant-a", RoutingContext.empty()))
                .isInstanceOfSatisfying(AgentRouteNotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo(GatewayErrorCode.RUNTIME_AT_CAPACITY));
        assertThat(registry.listAgents("tenant-a").getFirst().state()).isEqualTo(RuntimeState.AT_CAPACITY);
        assertThat(registry.healthSnapshot(0).readyRuntimeCount()).isZero();
    }

    @Test
    void routePrefersLowerPressureReadyRuntime() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRuntimeRegistry registry = new InMemoryRuntimeRegistry(clock);
        registry.register(registration("runtime-hot", "agent-weather", Duration.ofSeconds(30)));
        registry.register(registration("runtime-cool", "agent-weather", Duration.ofSeconds(30)));

        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-cool"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                capacity(1, 0, 10, 0.1, 60),
                Map.of()));
        clock.set(NOW.plusSeconds(1));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-hot"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                capacity(8, 1, 10, 0.9, 120),
                Map.of()));

        assertThat(registry.resolveRoute("agent-weather", "tenant-a", RoutingContext.empty()).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-cool"));
    }

    @Test
    void deregisterRemovesRuntimeFromRouteView() {
        InMemoryRuntimeRegistry registry = registryAt(NOW);
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));

        assertThat(registry.deregister(RuntimeInstanceId.of("runtime-1")).removed()).isTrue();

        assertThatThrownBy(() -> registry.resolveRoute("agent-weather", "tenant-a", RoutingContext.empty()))
                .isInstanceOf(AgentRouteNotFoundException.class);
    }

    private static InMemoryRuntimeRegistry registryAt(Instant instant) {
        return new InMemoryRuntimeRegistry(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static RuntimeAgentRegistration registration(String runtimeId, String agentId, Duration ttl) {
        return new RuntimeAgentRegistration(
                RuntimeInstanceId.of(runtimeId),
                "tenant-a",
                agentId,
                agentCard(agentId),
                URI.create("http://" + runtimeId + ".example/a2a"),
                URI.create("http://" + runtimeId + ".example/health"),
                "1.0.0",
                ttl,
                Map.of("zone", "az-a"));
    }

    private static AgentCard agentCard(String agentId) {
        AgentCapabilities capabilities = AgentCapabilities.builder()
                .streaming(true)
                .pushNotifications(true)
                .extendedAgentCard(false)
                .build();
        return AgentCard.builder()
                .name(agentId)
                .description(agentId + " A2A runtime")
                .url("/a2a")
                .version("1.0.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:8080"))
                .capabilities(capabilities)
                .defaultInputModes(java.util.List.of("text"))
                .defaultOutputModes(java.util.List.of("text"))
                .skills(java.util.List.of())
                .supportedInterfaces(java.util.List.of(new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), "/a2a")))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
    }

    private static RuntimeCapacitySnapshot capacity(
            int llmInFlight,
            int llmQueueDepth,
            int llmMaxConcurrency,
            double estimatedLoad,
            long p95FirstTokenMs) {
        return new RuntimeCapacitySnapshot(
                0,
                0,
                0,
                llmInFlight,
                llmQueueDepth,
                llmMaxConcurrency,
                50,
                p95FirstTokenMs,
                p95FirstTokenMs * 2,
                0,
                0,
                estimatedLoad,
                NOW);
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        private void set(Instant instant) {
            this.instant.set(instant);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported in this test clock");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
