package com.huawei.ascend.agentsdk.factory;

import com.huawei.ascend.agentsdk.adapter.deepagent.OpenJiuwenDeepAgentBuilder;
import com.huawei.ascend.agentsdk.adapter.react.OpenJiuwenReactAgentBuilder;
import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.huawei.ascend.agentsdk.spec.tool.HttpToolResolver;
import com.huawei.ascend.agentsdk.spec.tool.JavaFileToolResolver;
import com.huawei.ascend.agentsdk.spec.tool.ToolResolver;
import com.huawei.ascend.agentsdk.spec.yaml.AgentYamlLoader;
import com.huawei.ascend.agentsdk.support.UnsupportedFrameworkException;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AgentFactoryBuilder {
    private final List<ToolResolver> customToolResolvers = new ArrayList<>();

    public AgentFactoryBuilder toolResolver(ToolResolver resolver) {
        customToolResolvers.add(resolver);
        return this;
    }

    public ReActAgent toReactAgent(Path yamlPath) {
        return toReactAgent(new AgentYamlLoader().load(yamlPath));
    }

    public DeepAgent toDeepAgent(Path yamlPath) {
        return toDeepAgent(new AgentYamlLoader().load(yamlPath));
    }

    private ReActAgent toReactAgent(AgentSpec spec) {
        if (!"openjiuwen".equalsIgnoreCase(spec.frameworkType()) || !"react".equalsIgnoreCase(spec.agentType())) {
            throw unsupportedSpec(spec);
        }
        return new OpenJiuwenReactAgentBuilder(toolResolvers()).buildAgent(spec);
    }

    private DeepAgent toDeepAgent(AgentSpec spec) {
        if (!"openjiuwen".equalsIgnoreCase(spec.frameworkType()) || !"deepagent".equalsIgnoreCase(spec.agentType())) {
            throw unsupportedSpec(spec);
        }
        return new OpenJiuwenDeepAgentBuilder(toolResolvers()).buildAgent(spec);
    }

    private List<ToolResolver> toolResolvers() {
        List<ToolResolver> resolvers = new ArrayList<>(customToolResolvers);
        resolvers.add(new JavaFileToolResolver());
        resolvers.add(new HttpToolResolver());
        return List.copyOf(resolvers);
    }

    private UnsupportedFrameworkException unsupportedSpec(AgentSpec spec) {
        return new UnsupportedFrameworkException("Only OpenJiuwen react/deepagent YAML is supported by agent-sdk: "
                + spec.frameworkType() + "/" + spec.agentType());
    }

}
