package com.huawei.ascend.agentsdk.adapter.deepagent;

import com.huawei.ascend.agentsdk.adapter.OpenJiuwenToolMapper;
import com.huawei.ascend.agentsdk.adapter.react.OpenJiuwenRuntimeProof;
import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.huawei.ascend.agentsdk.spec.tool.ResolvedTool;
import com.huawei.ascend.agentsdk.spec.tool.ToolResolver;
import com.huawei.ascend.agentsdk.spec.tool.ToolSpec;
import com.huawei.ascend.agentsdk.spec.tool.UnsupportedToolRefException;
import com.openjiuwen.core.foundation.tool.Tool;
import java.util.List;

public final class OpenJiuwenDeepAgentBuilder {
    private final List<ToolResolver> toolResolvers;
    private final OpenJiuwenToolMapper toolMapper = new OpenJiuwenToolMapper();

    public OpenJiuwenDeepAgentBuilder(List<ToolResolver> toolResolvers) {
        this.toolResolvers = List.copyOf(toolResolvers);
    }

    public OpenJiuwenDeepAgentHandlerAdapter build(AgentSpec spec) {
        return toHandler(spec.name(), buildAgent(spec));
    }

    public Object buildAgent(AgentSpec spec) {
        OpenJiuwenDeepAgentOptions options = OpenJiuwenDeepAgentOptions.from(spec.frameworkOptions());
        List<Tool> tools = spec.toolSpecs().stream()
                .map(this::resolve)
                .map(toolMapper::toTool)
                .toList();

        /*
         * Temporary CI unblock:
         * com.openjiuwen:agent-core-java:0.1.12 currently available to CI does not expose
         * com.openjiuwen.harness.deep_agent.DeepAgent and related DeepAgentConfig classes.
         * Restore the real DeepAgent construction here after OpenJiuwen publishes a jar
         * that contains those APIs.
         */
        return new DeepAgentPlaceholder(
                spec.name(),
                "sdk-proof".equalsIgnoreCase(options.executeMode()),
                new OpenJiuwenRuntimeProof(spec, tools));
    }

    public static boolean supports(Object agent) {
        return agent instanceof DeepAgentPlaceholder;
    }

    public static OpenJiuwenDeepAgentHandlerAdapter toHandler(String agentId, Object agent) {
        DeepAgentPlaceholder placeholder = (DeepAgentPlaceholder) agent;
        return new OpenJiuwenDeepAgentHandlerAdapter(
                agentId,
                placeholder.proofMode(),
                placeholder.proof());
    }

    private ResolvedTool resolve(ToolSpec spec) {
        return toolResolvers.stream()
                .filter(resolver -> resolver.supports(spec.ref().scheme()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedToolRefException(
                        "Unsupported tool ref scheme: " + spec.ref().scheme()))
                .resolve(spec);
    }

    private record DeepAgentPlaceholder(String agentId, boolean proofMode, OpenJiuwenRuntimeProof proof) {
    }
}
