package com.huawei.ascend.bus.forwarding.runtime.relay;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deterministic unit tests for {@link RelayDispatchLoop} — the pure relay-tick
 * loop. No Spring / threads / sleep: the {@link RelayTick} worker, the
 * {@link RelayDispatchLoop.TickSource}, and the {@link RelayDispatchLoop.RelayIdleStrategy}
 * are inline fakes. Mirrors the testability story of {@code ForwardingDispatchLoop}.
 */
class RelayDispatchLoopTest {

    /** Fake worker: records (tenant, now, limit) per tick + returns scripted results. */
    static final class RecordingRelayTick implements RelayTick {
        final List<String> tenants = new ArrayList<>();
        final List<Long> nows = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();
        final Deque<EventBusRelayWorker.RelayTickResult> scripted = new ArrayDeque<>();

        @Override
        public EventBusRelayWorker.RelayTickResult runOnce(String tenantId, long nowMillisEpoch, int limit) {
            tenants.add(tenantId);
            nows.add(nowMillisEpoch);
            limits.add(limit);
            return scripted.isEmpty() ? new EventBusRelayWorker.RelayTickResult(0, 0, 0, 0) : scripted.poll();
        }
    }

    /** Capturing idle strategy: records every idle tick. */
    static final class CapturingIdle implements RelayDispatchLoop.RelayIdleStrategy {
        final List<EventBusRelayWorker.RelayTickResult> idleTicks = new ArrayList<>();
        @Override
        public void onIdle(EventBusRelayWorker.RelayTickResult lastTick) { idleTicks.add(lastTick); }
    }

    /** Queue-based TickSource: yields the given instants in order, then empty (stop). */
    static RelayDispatchLoop.TickSource source(long... instants) {
        Deque<Long> q = new ArrayDeque<>();
        for (long t : instants) q.add(t);
        return () -> q.isEmpty() ? OptionalLong.empty() : OptionalLong.of(q.poll());
    }

    @Test
    void runs_one_tick_per_yielded_instant_and_aggregates() {
        RecordingRelayTick worker = new RecordingRelayTick();
        worker.scripted.add(new EventBusRelayWorker.RelayTickResult(2, 1, 0, 1)); // tick @100
        worker.scripted.add(new EventBusRelayWorker.RelayTickResult(3, 0, 1, 2)); // tick @200
        CapturingIdle idle = new CapturingIdle();
        RelayDispatchLoop loop = new RelayDispatchLoop(worker, source(100L, 200L), idle);

        EventBusRelayWorker.RelayTickResult agg = loop.run("t1", 5);

        assertThat(worker.tenants).containsExactly("t1", "t1");
        assertThat(worker.nows).containsExactly(100L, 200L);
        assertThat(worker.limits).containsExactly(5, 5);
        assertThat(agg.relayed()).isEqualTo(5);            // 2 + 3
        assertThat(agg.dedupSuppressed()).isEqualTo(1);    // 1 + 0
        assertThat(agg.governanceRejected()).isEqualTo(1); // 0 + 1
        assertThat(agg.skipped()).isEqualTo(3);           // 1 + 2
        assertThat(idle.idleTicks).isEmpty();              // neither tick was idle (all counts > 0)
    }

    @Test
    void fires_onIdle_only_for_all_zero_ticks() {
        RecordingRelayTick worker = new RecordingRelayTick();
        worker.scripted.add(new EventBusRelayWorker.RelayTickResult(1, 0, 0, 0)); // busy (relayed)
        worker.scripted.add(new EventBusRelayWorker.RelayTickResult(0, 0, 0, 0)); // idle
        worker.scripted.add(new EventBusRelayWorker.RelayTickResult(0, 0, 0, 1)); // busy (skipped > 0)
        CapturingIdle idle = new CapturingIdle();
        RelayDispatchLoop loop = new RelayDispatchLoop(worker, source(10L, 20L, 30L), idle);

        loop.run("t1", 5);

        assertThat(idle.idleTicks).hasSize(1); // only the all-zero tick (@20) was idle
    }

    @Test
    void stops_when_source_returns_empty() {
        RecordingRelayTick worker = new RecordingRelayTick();
        RelayDispatchLoop loop = new RelayDispatchLoop(worker, source(), new CapturingIdle());

        EventBusRelayWorker.RelayTickResult agg = loop.run("t1", 5);

        assertThat(worker.tenants).isEmpty(); // zero ticks run
        assertThat(agg.relayed()).isZero();
    }

    @Test
    void rejects_blank_tenant_and_non_positive_limit() {
        RelayDispatchLoop loop = new RelayDispatchLoop(new RecordingRelayTick(), source(1L), new CapturingIdle());
        assertThatThrownBy(() -> loop.run("", 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loop.run("t1", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loop.run("t1", -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
