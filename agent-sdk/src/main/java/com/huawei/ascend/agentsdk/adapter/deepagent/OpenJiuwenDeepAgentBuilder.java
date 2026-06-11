package com.huawei.ascend.agentsdk.adapter.deepagent;

import com.huawei.ascend.agentsdk.adapter.OpenJiuwenAgentSpecMapper;
import com.huawei.ascend.agentsdk.adapter.OpenJiuwenSkillMapper;
import com.huawei.ascend.agentsdk.adapter.OpenJiuwenToolMapper;
import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.huawei.ascend.agentsdk.spec.tool.ResolvedTool;
import com.huawei.ascend.agentsdk.spec.tool.ToolResolver;
import com.huawei.ascend.agentsdk.spec.tool.ToolSpec;
import com.huawei.ascend.agentsdk.spec.tool.UnsupportedToolRefException;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenJiuwenDeepAgentBuilder {
    private final List<ToolResolver> toolResolvers;
    private final OpenJiuwenToolMapper toolMapper = new OpenJiuwenToolMapper();
    private final OpenJiuwenSkillMapper skillMapper = new OpenJiuwenSkillMapper();
    private final OpenJiuwenAgentSpecMapper specMapper = new OpenJiuwenAgentSpecMapper();

    public OpenJiuwenDeepAgentBuilder(List<ToolResolver> toolResolvers) {
        this.toolResolvers = List.copyOf(toolResolvers);
    }

    public DeepAgent buildAgent(AgentSpec spec) {
        OpenJiuwenDeepAgentOptions options = OpenJiuwenDeepAgentOptions.from(spec.frameworkOptions());
        List<Tool> tools = spec.toolSpecs().stream()
                .map(this::resolve)
                .map(toolMapper::toTool)
                .toList();
        List<String> skillDirectories = skillMapper.toSkillRootDirectories(spec.skillSpecs());
        DeepAgentConfig config = DeepAgentConfig.builder()
                .systemPrompt(spec.promptSpec().system())
                .maxIterations(options.maxIterations())
                .tools(List.copyOf(toObjects(tools)))
                .skillDirectories(skillDirectories)
                .skillMode("all")
                .model(modelConfig(spec))
                .backend(backendConfig(spec))
                .build();
        Workspace workspace = Workspace.builder()
                .rootPath(".")
                .language("cn")
                .build();
        return HarnessFactory.createDeepAgent(specMapper.card(spec), config, workspace);
    }

    private ResolvedTool resolve(ToolSpec spec) {
        return toolResolvers.stream()
                .filter(resolver -> resolver.supports(spec.ref().scheme()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedToolRefException(
                        "Unsupported tool ref scheme: " + spec.ref().scheme()))
                .resolve(spec);
    }

    private static List<Object> toObjects(List<Tool> tools) {
        return tools.stream().map(tool -> (Object) tool).toList();
    }

    private static Map<String, Object> modelConfig(AgentSpec spec) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("model", spec.modelSpec().name());
        return model;
    }

    private static Map<String, Object> backendConfig(AgentSpec spec) {
        Map<String, Object> backend = new LinkedHashMap<>();
        backend.put("provider", spec.modelSpec().provider());
        backend.put("apiKey", spec.modelSpec().apiKey());
        backend.put("baseUrl", spec.modelSpec().baseUrl());
        backend.put("verifySsl", spec.modelSpec().sslVerify());
        backend.put("headers", spec.modelSpec().headers());
        return backend;
    }
}
