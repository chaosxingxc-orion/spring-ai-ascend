package com.huawei.ascend.runtime.engine.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.a2a.Messages;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
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
            assertThat(invocation.messages()).extracting(AgentScopeRuntimeHandlerTest::messageText)
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

    private static AgentExecutionContext context() {
        return context("agent-scope");
    }

    private static AgentExecutionContext context(String agentId) {
        RuntimeIdentity scope = new RuntimeIdentity("tenant", "user", "session", "task", agentId);
        return new AgentExecutionContext(scope, "USER_MESSAGE", List.of(message(Message.Role.ROLE_USER, "ping")), Map.of());
    }

    private static Message message(Message.Role role, String text) {
        return Message.builder()
                .role(role)
                .parts(new TextPart(text))
                .build();
    }

    private static String messageText(Message message) {
        return Messages.text(message);
    }
}
