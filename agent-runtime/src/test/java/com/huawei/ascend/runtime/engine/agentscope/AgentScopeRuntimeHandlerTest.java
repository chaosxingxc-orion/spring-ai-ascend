package com.huawei.ascend.runtime.engine.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentScopeRuntimeHandlerTest {

    @Test
    void agentHandlerStreamsAgentScopeEventsThroughRuntimeResultModel() {
        AgentScopeAgentRuntimeHandler handler = new AgentScopeAgentRuntimeHandler("agent-scope", invocation -> {
            assertThat(invocation.tenantId()).isEqualTo("tenant");
            assertThat(invocation.userId()).isEqualTo("user");
            assertThat(invocation.sessionId()).isEqualTo("session");
            assertThat(invocation.taskId()).isEqualTo("task");
            assertThat(invocation.agentId()).isEqualTo("agent-scope");
            assertThat(invocation.messages()).extracting(RuntimeMessage::text)
                    .containsExactly("ping");
            return Stream.of(AgentScopeEvent.output("pong-"), AgentScopeEvent.completed("done"));
        });

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEqualTo("pong-");
        assertThat(results.get(1).outputContent()).isEqualTo("done");
    }

    @Test
    void harnessHandlerMapsHumanInputEventToRuntimeInterrupt() {
        AgentScopeHarnessRuntimeHandler handler = new AgentScopeHarnessRuntimeHandler(
                "harness-agent",
                invocation -> Stream.of(AgentScopeEvent.interrupted("need approval")));

        List<AgentExecutionResult> results = handler.resultAdapter()
                .adapt(handler.execute(context("harness-agent")))
                .toList();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.INTERRUPTED);
        assertThat(results.get(0).prompt()).isEqualTo("need approval");
    }

    @Test
    void enabledEmitsRunLifecycleWithProgressAndErrorFromAgentScopeStream() {
        AgentScopeAgentRuntimeHandler handler = new AgentScopeAgentRuntimeHandler("agent-scope", invocation ->
                Stream.of(AgentScopeEvent.output("pong-"), AgentScopeEvent.failed("X", "boom"),
                        AgentScopeEvent.completed("done")));

        List<TrajectoryEvent> events = runWithTrajectory(handler);

        // The first streamed output delta also emits the time-to-first-token marker.
        assertThat(events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.MODEL_CALL_FIRST_TOKEN, Kind.PROGRESS, Kind.ERROR, Kind.RUN_END);
        assertThat(events).allSatisfy(e -> assertThat(e.tenantId()).isEqualTo("tenant"));
        // The run span is the root; the error point event hangs off it.
        TrajectoryEvent runStart = events.stream().filter(e -> e.kind() == Kind.RUN_START).findFirst().orElseThrow();
        TrajectoryEvent error = events.stream().filter(e -> e.kind() == Kind.ERROR).findFirst().orElseThrow();
        assertThat(runStart.parentSpanId()).isNull();
        assertThat(error.parentSpanId()).isEqualTo(runStart.spanId());
        assertThat(error.error().code()).isEqualTo("X");
    }

    @Test
    void firstOutputDeltaEmitsExactlyOneFirstTokenMarkerCarryingTtft() {
        AgentScopeAgentRuntimeHandler handler = new AgentScopeAgentRuntimeHandler("agent-scope", invocation ->
                Stream.of(AgentScopeEvent.output("a"), AgentScopeEvent.output("b"), AgentScopeEvent.completed("done")));

        List<TrajectoryEvent> events = runWithTrajectory(handler);

        assertThat(events).extracting(TrajectoryEvent::kind)
                .containsExactly(Kind.RUN_START, Kind.MODEL_CALL_FIRST_TOKEN, Kind.PROGRESS, Kind.PROGRESS,
                        Kind.RUN_END);
        assertThat(events).filteredOn(e -> e.kind() == Kind.MODEL_CALL_FIRST_TOKEN).hasSize(1);
        TrajectoryEvent runStart = events.stream().filter(e -> e.kind() == Kind.RUN_START).findFirst().orElseThrow();
        TrajectoryEvent firstToken = events.stream()
                .filter(e -> e.kind() == Kind.MODEL_CALL_FIRST_TOKEN).findFirst().orElseThrow();
        assertThat(firstToken.parentSpanId()).isEqualTo(runStart.spanId());
        assertThat(firstToken.durationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    private static List<TrajectoryEvent> runWithTrajectory(AgentScopeAgentRuntimeHandler handler) {
        AgentExecutionContext context = context();
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        List<TrajectoryEvent> events = new ArrayList<>();
        handler.openTrajectory(context, settings, (TrajectorySink) events::add);
        try (Stream<?> raw = handler.execute(context)) {
            raw.forEach(x -> { });
        }
        return events;
    }

    private static AgentExecutionContext context() {
        return context("agent-scope");
    }

    private static AgentExecutionContext context(String agentId) {
        RuntimeIdentity scope = new RuntimeIdentity("tenant", "user", "session", "task", agentId);
        return new AgentExecutionContext(scope, "USER_MESSAGE", List.of(RuntimeMessage.user("ping")), Map.of());
    }
}
