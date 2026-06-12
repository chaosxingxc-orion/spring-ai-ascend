package com.huawei.ascend.runtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentExecutionContextTest {

    @Test
    void lastUserTextReturnsNewestUserTurn() {
        AgentExecutionContext context = context(List.of(
                RuntimeMessage.user("first question"),
                RuntimeMessage.agent("answer"),
                RuntimeMessage.user("follow-up")));

        assertThat(context.lastUserText()).isEqualTo("follow-up");
    }

    @Test
    void lastUserTextFallsBackToNewestMessageWhenNoUserTurnExists() {
        AgentExecutionContext context = context(List.of(
                RuntimeMessage.agent("only"),
                RuntimeMessage.agent("agent turns")));

        assertThat(context.lastUserText()).isEqualTo("agent turns");
    }

    @Test
    void lastUserTextIsEmptyWithoutMessages() {
        assertThat(context(List.of()).lastUserText()).isEmpty();
    }

    @Test
    void runtimeMessageNormalizesNullsAndIsImmutable() {
        RuntimeMessage message = new RuntimeMessage(null, null, null);

        assertThat(message.role()).isEqualTo(RuntimeMessage.Role.USER);
        assertThat(message.text()).isEmpty();
        assertThat(message.metadata()).isEmpty();
        RuntimeMessage withMetadata = new RuntimeMessage(
                RuntimeMessage.Role.AGENT, "hi", Map.of("wireRole", "assistant"));
        assertThat(withMetadata.metadata()).containsEntry("wireRole", "assistant");
    }

    private static AgentExecutionContext context(List<RuntimeMessage> messages) {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE", messages, Map.of());
    }
}
