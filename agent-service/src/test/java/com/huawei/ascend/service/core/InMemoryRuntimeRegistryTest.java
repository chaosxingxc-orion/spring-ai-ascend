package com.huawei.ascend.service.core;

import com.huawei.ascend.service.spi.RuntimeRegistryContractTest;
import com.huawei.ascend.service.spi.discovery.RoutingContext;
import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import com.huawei.ascend.service.spi.registry.RuntimeLeaseRenewal;
import com.huawei.ascend.service.spi.registry.RuntimeState;
import com.huawei.ascend.service.spi.registry.SlaSnapshot;
import com.huawei.ascend.service.testsupport.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRuntimeRegistryTest extends RuntimeRegistryContractTest<InMemoryRuntimeRegistry> {

    @Override
    protected InMemoryRuntimeRegistry createRegistry(Clock clock) {
        return new InMemoryRuntimeRegistry(clock);
    }

    @Test
    void healthStatsCountReadyAndUnreachableRuntimes() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRuntimeRegistry registry = createRegistry(clock);
        registry.register(registration("runtime-ready", "agent-weather", Duration.ofSeconds(30)));
        registry.register(registration("runtime-expiring", "agent-travel", Duration.ofSeconds(5)));

        clock.set(NOW.plusSeconds(6));

        InMemoryRuntimeRegistry.HealthStats stats = registry.healthStats();
        assertThat(stats.registeredRuntimeCount()).isEqualTo(2);
        assertThat(stats.readyRuntimeCount()).isEqualTo(1);
        assertThat(stats.unreachableRuntimeCount()).isEqualTo(1);
    }

    @Test
    void healthStatsCountSaturatedRuntimeAsNotReady() {
        InMemoryRuntimeRegistry registry = createRegistry(new MutableClock(NOW));
        registry.register(registration("runtime-1", "agent-weather", Duration.ofSeconds(30)));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-1"),
                RuntimeState.READY,
                Duration.ofSeconds(30),
                SlaSnapshot.empty(),
                capacity(1, 0, 1, 1.0, 100),
                Map.of("reason", "llm-saturated")));

        assertThat(registry.healthStats().readyRuntimeCount()).isZero();
    }

    @Test
    void sessionSticksToPinnedInstanceWhenBetterScoredInstanceJoins() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRuntimeRegistry registry = createRegistry(clock);
        registry.register(registration("runtime-pinned", "agent-weather", Duration.ofSeconds(60)));

        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-1")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-pinned"));

        clock.set(NOW.plusSeconds(1));
        registry.register(registration("runtime-better", "agent-weather", Duration.ofSeconds(60)));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-pinned"),
                RuntimeState.READY,
                Duration.ofSeconds(60),
                SlaSnapshot.empty(),
                capacity(8, 1, 10, 0.9, 200),
                Map.of()));

        assertThat(registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-better"));
        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-1")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-pinned"));
    }

    @Test
    void pinBreaksOnDeregisterAndSessionRepinsToSurvivor() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRuntimeRegistry registry = createRegistry(clock);
        registry.register(registration("runtime-pinned", "agent-weather", Duration.ofSeconds(60)));
        registry.register(registration("runtime-survivor", "agent-weather", Duration.ofSeconds(60)));
        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-1")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-pinned"));

        registry.deregister(RuntimeInstanceId.of("runtime-pinned"));

        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-1")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-survivor"));

        // A freshly registered instance wins new picks, so the session staying
        // on the survivor proves the re-pick re-pinned rather than re-scoring.
        clock.set(NOW.plusSeconds(1));
        registry.register(registration("runtime-newest", "agent-weather", Duration.ofSeconds(60)));
        assertThat(registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-newest"));
        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-1")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-survivor"));
    }

    @Test
    void pinBreaksOnLeaseExpiryAndSessionRepinsToSurvivor() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRuntimeRegistry registry = createRegistry(clock);
        registry.register(registration("runtime-short", "agent-weather", Duration.ofSeconds(5)));
        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-1")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-short"));

        registry.register(registration("runtime-long", "agent-weather", Duration.ofSeconds(60)));
        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-1")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-short"));

        clock.set(NOW.plusSeconds(6));

        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-1")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-long"));

        // The expired instance comes back with the freshest heartbeat and wins
        // new picks; the session staying on runtime-long proves it re-pinned.
        registry.register(registration("runtime-short", "agent-weather", Duration.ofSeconds(60)));
        assertThat(registry.resolveRoute("agent-weather", TENANT, RoutingContext.empty()).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-short"));
        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-1")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-long"));
    }

    @Test
    void blankSessionKeepsScoreBasedPick() {
        InMemoryRuntimeRegistry registry = createRegistry(new MutableClock(NOW));
        registry.register(registration("runtime-hot", "agent-weather", Duration.ofSeconds(60)));
        registry.register(registration("runtime-cool", "agent-weather", Duration.ofSeconds(60)));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-hot"),
                RuntimeState.READY,
                Duration.ofSeconds(60),
                SlaSnapshot.empty(),
                capacity(8, 1, 10, 0.9, 120),
                Map.of()));

        assertThat(registry.resolveRoute("agent-weather", TENANT, session("   ")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-cool"));

        // Flip the pressure: a pinned session would stay put, a blank one follows the score.
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-hot"),
                RuntimeState.READY,
                Duration.ofSeconds(60),
                SlaSnapshot.empty(),
                capacity(1, 0, 10, 0.1, 60),
                Map.of()));
        registry.renew(new RuntimeLeaseRenewal(
                RuntimeInstanceId.of("runtime-cool"),
                RuntimeState.READY,
                Duration.ofSeconds(60),
                SlaSnapshot.empty(),
                capacity(8, 1, 10, 0.9, 120),
                Map.of()));

        assertThat(registry.resolveRoute("agent-weather", TENANT, session("   ")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-hot"));
    }

    @Test
    void pinMapBoundEvictsOldestSessionPin() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRuntimeRegistry registry = createRegistry(clock);
        registry.register(registration("runtime-old", "agent-weather", Duration.ofSeconds(600)));
        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-evicted")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-old"));

        clock.set(NOW.plusSeconds(1));
        registry.register(registration("runtime-new", "agent-weather", Duration.ofSeconds(600)));
        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-evicted")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-old"));

        for (int i = 0; i < InMemoryRuntimeRegistry.MAX_SESSION_PINS; i++) {
            registry.resolveRoute("agent-weather", TENANT, session("flood-" + i));
        }

        // The oldest pin fell out of the bounded map, so the session re-picks
        // the now-preferred instance instead of its original one.
        assertThat(registry.resolveRoute("agent-weather", TENANT, session("session-evicted")).runtimeInstanceId())
                .isEqualTo(RuntimeInstanceId.of("runtime-new"));
    }

    private static RoutingContext session(String sessionId) {
        return new RoutingContext(sessionId, null, Map.of());
    }
}
