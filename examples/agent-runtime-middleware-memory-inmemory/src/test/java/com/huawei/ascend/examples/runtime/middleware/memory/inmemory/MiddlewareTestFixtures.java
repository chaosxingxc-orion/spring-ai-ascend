package com.huawei.ascend.examples.runtime.middleware.memory.inmemory;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;

final class MiddlewareTestFixtures {
    private MiddlewareTestFixtures() {
    }

    static AgentExecutionContext context(String stateKey) {
        RuntimeIdentity identity =
                new RuntimeIdentity("tenant", "user", "session", "task", "openjiuwen-simple-agent");
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("green tea")))
                .build();
        return new AgentExecutionContext(identity, "USER_MESSAGE", List.of(message), Map.of(), stateKey, null);
    }
}
