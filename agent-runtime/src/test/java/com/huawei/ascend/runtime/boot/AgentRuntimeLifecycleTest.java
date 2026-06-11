package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * The host-side lifecycle driver: handlers must be started before the runtime
 * reports ready and stopped after it stops accepting executions. A handler
 * start failure must fail runtime startup (never a serving-but-broken
 * runtime), rolling back the handlers it already started.
 */
class AgentRuntimeLifecycleTest {

    private final AgentRuntimeHandler first = mock(AgentRuntimeHandler.class);
    private final AgentRuntimeHandler second = mock(AgentRuntimeHandler.class);
    private final RuntimeReadiness readiness = new RuntimeReadiness();

    @Test
    void startStartsHandlersInRegistrationOrderAndMarksReady() {
        AgentRuntimeLifecycle lifecycle = new AgentRuntimeLifecycle(List.of(first, second), readiness);

        lifecycle.start();

        InOrder order = inOrder(first, second);
        order.verify(first).start();
        order.verify(second).start();
        assertThat(readiness.isReady()).isTrue();
        assertThat(lifecycle.isRunning()).isTrue();
    }

    @Test
    void startFailureRollsBackStartedHandlersAndPropagates() {
        when(second.agentId()).thenReturn("late-failer");
        doThrow(new IllegalStateException("backend down")).when(second).start();
        AgentRuntimeLifecycle lifecycle = new AgentRuntimeLifecycle(List.of(first, second), readiness);

        assertThatThrownBy(lifecycle::start).isInstanceOf(IllegalStateException.class);

        verify(first).stop();
        assertThat(readiness.isReady()).isFalse();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void stopClosesReadinessGateAndStopsHandlersInReverseOrder() {
        AgentRuntimeLifecycle lifecycle = new AgentRuntimeLifecycle(List.of(first, second), readiness);
        lifecycle.start();

        lifecycle.stop();

        InOrder order = inOrder(second, first);
        order.verify(second).stop();
        order.verify(first).stop();
        assertThat(readiness.isReady()).isFalse();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void stopAttemptsEveryHandlerEvenWhenOneFails() {
        when(second.agentId()).thenReturn("failing-stopper");
        doThrow(new IllegalStateException("already closed")).when(second).stop();
        AgentRuntimeLifecycle lifecycle = new AgentRuntimeLifecycle(List.of(first, second), readiness);
        lifecycle.start();

        lifecycle.stop();

        verify(first).stop();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void stopWithoutStartTouchesNoHandler() {
        AgentRuntimeLifecycle lifecycle = new AgentRuntimeLifecycle(List.of(first, second), readiness);

        lifecycle.stop();

        verify(first, never()).stop();
        verify(second, never()).stop();
    }

    /** Handlers must be ready before web traffic starts and stop after it drains. */
    @Test
    void phaseOrdersHandlerLifecycleAroundWebServerLifecycle() {
        AgentRuntimeLifecycle lifecycle = new AgentRuntimeLifecycle(List.of(first), readiness);

        assertThat(lifecycle.getPhase()).isZero();
        assertThat(lifecycle.isAutoStartup()).isTrue();
    }
}
