package com.huawei.ascend.examples.workmate.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentRunContextTest {

    @Test
    void singleAgentUsesStableSessionConversationKey() {
        UUID sessionId = UUID.randomUUID();
        String taskId = UUID.randomUUID().toString();

        String key = AgentConversationKey.resolve(sessionId, taskId, null, false, 0);

        assertThat(key).isEqualTo(sessionId.toString());
    }

    @Test
    void teamSubRunUsesIsolatedConversationKey() {
        UUID sessionId = UUID.randomUUID();
        String taskId = "parent-run:member-a";
        RunPersistenceContext memberContext =
                RunPersistenceContext.forMember(sessionId, taskId, "parent-run", "member-a");

        String key = AgentConversationKey.resolve(sessionId, taskId, memberContext, false, 0);

        assertThat(key).isEqualTo(sessionId + ":" + taskId);
    }

    @Test
    void executionContextStillScopesTaskId() {
        UUID sessionId = UUID.randomUUID();
        String taskId = UUID.randomUUID().toString();
        RuntimeIdentity scope = RuntimeIdentity.of(
                "workmate", "workmate-user", sessionId.toString(), WorkmateAgentHandler.AGENT_ID);
        RuntimeIdentity scoped = scope.withTaskId(taskId);

        AgentExecutionContext context = new AgentExecutionContext(
                scoped,
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("follow up")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, sessionId.toString()));

        assertThat(context.getAgentStateKey()).isEqualTo(sessionId.toString());
        assertThat(scoped.taskId()).isEqualTo(taskId);
    }
}
