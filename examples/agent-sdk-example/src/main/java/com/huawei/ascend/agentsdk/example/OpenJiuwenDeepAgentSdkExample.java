package com.huawei.ascend.agentsdk.example;

import com.huawei.ascend.agentsdk.factory.AgentFactory;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.nio.file.Path;

public final class OpenJiuwenDeepAgentSdkExample {
    private OpenJiuwenDeepAgentSdkExample() {
    }

    public static void main(String[] args) throws Exception {
        Path yamlPath = OpenJiuwenExampleSupport.resolveYamlPath(args, "deepagent.yaml");
        String userInput = OpenJiuwenExampleSupport.userInput(args);

        DeepAgent deepAgent = AgentFactory.toDeepAgent(yamlPath);
        OpenJiuwenExampleSupport.printDeepAgentExecution(
                "Agent SDK YAML -> OpenJiuwen DeepAgent Example",
                yamlPath,
                userInput,
                deepAgent);
    }
}
