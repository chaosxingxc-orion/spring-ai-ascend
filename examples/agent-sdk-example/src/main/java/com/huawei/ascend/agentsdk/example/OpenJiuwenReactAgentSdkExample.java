package com.huawei.ascend.agentsdk.example;

import com.huawei.ascend.agentsdk.factory.AgentHandlerFactory;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.openjiuwen.core.singleagent.ReActAgent;
import java.nio.file.Path;

public final class OpenJiuwenReactAgentSdkExample {
    private static final String AGENT_ID = "sdk-openjiuwen-example-agent";

    private OpenJiuwenReactAgentSdkExample() {
    }

    public static void main(String[] args) throws Exception {
        Path yamlPath = OpenJiuwenExampleSupport.resolveYamlPath(args, "agent.yaml");
        boolean proofMode = Boolean.parseBoolean(System.getProperty("openjiuwen.example.proof", "false"));
        Path effectiveYaml = proofMode ? OpenJiuwenExampleSupport.proofYaml(yamlPath) : yamlPath;
        String userInput = OpenJiuwenExampleSupport.userInput(args);

        ReActAgent reactAgent = AgentHandlerFactory.toReactAgent(effectiveYaml);
        AgentRuntimeHandler handler = AgentHandlerFactory.toHandler(AGENT_ID, reactAgent);
        System.out.println("reactAgent: " + reactAgent.getClass().getName());
        OpenJiuwenExampleSupport.printExecution(
                "Agent SDK YAML -> OpenJiuwen ReAct Handler Example",
                effectiveYaml,
                userInput,
                handler);
    }
}
