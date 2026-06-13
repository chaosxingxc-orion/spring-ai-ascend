package com.huawei.ascend.examples.runtime.middleware.memory.inmemory;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.Map;

final class MiddlewareTestFixtures {
    private MiddlewareTestFixtures() {
    }

    static AgentExecutionContext context(String stateKey) {
        RuntimeIdentity identity =
                new RuntimeIdentity("tenant", "user", "session", "task", "openjiuwen-simple-agent");
        return new AgentExecutionContext(identity, "USER_MESSAGE",
                java.util.List.of(RuntimeMessage.user("green tea")), Map.of(), stateKey, null);
    }
}
