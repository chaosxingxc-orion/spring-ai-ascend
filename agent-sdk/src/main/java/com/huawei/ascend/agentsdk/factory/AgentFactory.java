package com.huawei.ascend.agentsdk.factory;

import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.nio.file.Path;

public final class AgentFactory {
    private AgentFactory() {
    }

    public static ReActAgent toReactAgent(Path yamlPath) {
        return builder().toReactAgent(yamlPath);
    }

    public static DeepAgent toDeepAgent(Path yamlPath) {
        return builder().toDeepAgent(yamlPath);
    }

    public static AgentFactoryBuilder builder() {
        return new AgentFactoryBuilder();
    }
}
