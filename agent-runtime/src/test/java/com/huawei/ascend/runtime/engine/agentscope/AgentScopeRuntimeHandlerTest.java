package com.huawei.ascend.runtime.engine.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.EngineInput;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.Map;
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
            assertThat(invocation.messages()).extracting(Message::text).containsExactly("ping");
            return Stream.of(AgentScopeEvent.output("pong-"), AgentScopeEvent.completed("done"));
        });

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).output().getContent()).isEqualTo("pong-");
        assertThat(results.get(1).output().getContent()).isEqualTo("done");
    }

    @Test
    void harnessHandlerMapsHumanInputEventToRuntimeInterrupt() {
        AgentScopeHarnessRuntimeHandler handler = new AgentScopeHarnessRuntimeHandler(
                "harness-agent",
                invocation -> Stream.of(AgentScopeEvent.interrupted("need approval")));

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context("harness-agent"))).toList();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.INTERRUPTED);
        assertThat(results.get(0).prompt()).isEqualTo("need approval");
    }

    private static AgentExecutionContext context() {
        return context("agent-scope");
    }

    private static AgentExecutionContext context(String agentId) {
        EngineExecutionScope scope = new EngineExecutionScope("tenant", "user", "session", "task", agentId);
        EngineInput input = new EngineInput("USER_MESSAGE", List.of(Message.user("ping")), Map.of("k", "v"));
        return new AgentExecutionContext(scope, input);
    }
}
