package com.huawei.ascend.bus.eventbus.runtime;

import com.huawei.ascend.bus.common.AgentBusBrokerProperties;
import com.huawei.ascend.bus.forwarding.runtime.relay.EventBusRelayWorker;
import com.huawei.ascend.bus.forwarding.runtime.relay.RelayTick;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scheduler-mechanism test for {@link RelayScheduler} — plain JUnit 5 (agent-bus has
 * no {@code @SpringBootTest}). Constructs a real {@link ThreadPoolTaskScheduler} +
 * {@link RelayScheduler} + counting-fake {@link RelayTick}, calls {@link RelayScheduler#start()},
 * and awaits the first fire. Does NOT boot RocketMQ/JDBC — the fake RelayTicks replace the
 * real workers. Mirrors the plain-JDK concurrency-test idiom of
 * {@code C3ForwardingMultiWorkerConcurrencyIntegrationTest}.
 */
class EventBusRelaySchedulerTest {

    private ThreadPoolTaskScheduler scheduler;

    private ThreadPoolTaskScheduler newScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("relay-test-");
        s.initialize(); // required when not Spring-managed
        return s;
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown(); // cancels the scheduled relay tasks
        }
    }

    /** Counting fake: records each call's (tenant, now, limit); can throw on the first N calls. */
    static final class CountingRelayTick implements RelayTick {
        final AtomicInteger calls = new AtomicInteger();
        final List<String> tenants = new ArrayList<>();
        final List<Long> nows = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();
        int throwOnFirst;
        CountDownLatch latch;

        @Override
        public EventBusRelayWorker.RelayTickResult runOnce(String tenantId, long nowMillisEpoch, int limit) {
            int n = calls.incrementAndGet();
            tenants.add(tenantId);
            nows.add(nowMillisEpoch);
            limits.add(limit);
            if (latch != null) {
                latch.countDown();
            }
            if (throwOnFirst > 0 && n <= throwOnFirst) {
                throw new RuntimeException("simulated tick failure #" + n);
            }
            return new EventBusRelayWorker.RelayTickResult(0, 0, 0, 0); // idle tick
        }
    }

    private static AgentBusBrokerProperties props(int relayTickLimit, long relayFixedDelayMs) {
        // null/0 for the unused fields — the compact constructor applies their defaults.
        return new AgentBusBrokerProperties(
                null, null, null, 0, null, null, 0, 0, 0, "default", relayTickLimit, relayFixedDelayMs);
    }

    @Test
    void scheduler_fires_runOnce_with_correct_args() throws InterruptedException {
        CountingRelayTick fwd = new CountingRelayTick();
        CountingRelayTick resp = new CountingRelayTick();
        // Separate latches: driveForward is scheduled before driveResponse, so fwd fires
        // first — awaiting fwd alone then checking resp immediately is a race. Await each
        // relay's first fire independently (each with its own 2s timeout).
        CountDownLatch fwdFirstFire = new CountDownLatch(1);
        CountDownLatch respFirstFire = new CountDownLatch(1);
        fwd.latch = fwdFirstFire;
        resp.latch = respFirstFire;
        scheduler = newScheduler();
        RelayScheduler rs = new RelayScheduler(fwd, resp, "default", 10, 50L, scheduler);

        rs.start();

        assertThat(fwdFirstFire.await(2, TimeUnit.SECONDS))
                .as("forward relay fired at least once").isTrue();
        assertThat(respFirstFire.await(2, TimeUnit.SECONDS))
                .as("response relay fired at least once").isTrue();
        assertThat(fwd.calls.get()).as("forward fired >= 1").isGreaterThanOrEqualTo(1);
        assertThat(resp.calls.get()).as("response fired >= 1").isGreaterThanOrEqualTo(1);
        assertThat(fwd.tenants.get(0)).isEqualTo("default");
        assertThat(fwd.nows.get(0)).as("clock.now() > 0").isGreaterThan(0L);
        assertThat(fwd.limits.get(0)).isEqualTo(10);
    }

    @Test
    void thrown_tick_does_not_kill_scheduler() throws InterruptedException {
        // scheduleWithFixedDelay cancels its future if the task THROWS — the per-tick try/catch
        // in driveForward swallows, so the schedule survives. Await the 2nd call as proof.
        CountingRelayTick fwd = new CountingRelayTick();
        fwd.throwOnFirst = 1; // first call throws
        CountDownLatch secondCall = new CountDownLatch(2);
        fwd.latch = secondCall;
        scheduler = newScheduler();
        RelayScheduler rs = new RelayScheduler(fwd, new CountingRelayTick(), "default", 10, 50L, scheduler);

        rs.start();

        assertThat(secondCall.await(3, TimeUnit.SECONDS))
                .as("scheduler kept firing after the first tick threw — the per-tick try/catch must "
                        + "swallow (else scheduleWithFixedDelay cancels the future)").isTrue();
        assertThat(fwd.calls.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void relay_props_default_when_non_positive() {
        AgentBusBrokerProperties defaulted = props(0, 0L);
        assertThat(defaulted.relayTickLimit()).as("relayTickLimit default 100").isEqualTo(100);
        assertThat(defaulted.relayFixedDelayMs()).as("relayFixedDelayMs default 1000").isEqualTo(1_000L);
    }
}
