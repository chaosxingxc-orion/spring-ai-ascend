package com.huawei.ascend.agentsdk.example;

import com.huawei.ascend.agentsdk.factory.AgentHandlerFactory;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.nio.file.Path;

public final class OpenJiuwenDeepAgentSdkExample {
    private static final String AGENT_ID = "sdk-openjiuwen-deepagent-example-agent";

    private OpenJiuwenDeepAgentSdkExample() {
    }

    public static void main(String[] args) throws Exception {
        Path yamlPath = OpenJiuwenExampleSupport.resolveYamlPath(args, "deepagent.yaml");
        boolean proofMode = Boolean.parseBoolean(System.getProperty("openjiuwen.example.proof", "false"));
        Path effectiveYaml = proofMode ? OpenJiuwenExampleSupport.proofYaml(yamlPath) : yamlPath;
        String userInput = OpenJiuwenExampleSupport.userInput(args);

        Object deepAgent = AgentHandlerFactory.toDeepAgent(effectiveYaml);
        AgentRuntimeHandler handler = AgentHandlerFactory.toHandler(AGENT_ID, deepAgent);
        System.out.println("deepAgent: " + deepAgent.getClass().getName());
        OpenJiuwenExampleSupport.printExecution(
                "Agent SDK YAML -> OpenJiuwen DeepAgent Handler Example",
                effectiveYaml,
                userInput,
                handler);
    }
}
