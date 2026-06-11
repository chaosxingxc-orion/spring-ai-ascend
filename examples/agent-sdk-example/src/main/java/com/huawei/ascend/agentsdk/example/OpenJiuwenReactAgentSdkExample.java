package com.huawei.ascend.agentsdk.example;

import com.huawei.ascend.agentsdk.factory.AgentFactory;
import com.openjiuwen.core.singleagent.ReActAgent;
import java.nio.file.Path;

public final class OpenJiuwenReactAgentSdkExample {
    private OpenJiuwenReactAgentSdkExample() {
    }

    public static void main(String[] args) throws Exception {
        Path yamlPath = OpenJiuwenExampleSupport.resolveYamlPath(args, "agent.yaml");
        String userInput = OpenJiuwenExampleSupport.userInput(args);

        ReActAgent reactAgent = AgentFactory.toReactAgent(yamlPath);
        OpenJiuwenExampleSupport.printReactExecution(
                "Agent SDK YAML -> OpenJiuwen ReActAgent Example",
                yamlPath,
                userInput,
                reactAgent);
    }
}
