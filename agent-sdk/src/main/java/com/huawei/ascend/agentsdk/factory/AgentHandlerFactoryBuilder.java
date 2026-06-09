package com.huawei.ascend.agentsdk.factory;

import com.huawei.ascend.agentsdk.adapter.deepagent.OpenJiuwenDeepAgentBuilder;
import com.huawei.ascend.agentsdk.adapter.react.OpenJiuwenReactAgentBuilder;
import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.huawei.ascend.agentsdk.spec.tool.HttpToolResolver;
import com.huawei.ascend.agentsdk.spec.tool.JavaFileToolResolver;
import com.huawei.ascend.agentsdk.spec.tool.McpToolResolver;
import com.huawei.ascend.agentsdk.spec.tool.ToolResolver;
import com.huawei.ascend.agentsdk.spec.yaml.AgentYamlLoader;
import com.huawei.ascend.agentsdk.support.UnsupportedFrameworkException;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.openjiuwen.core.singleagent.ReActAgent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AgentHandlerFactoryBuilder {
    private final List<ToolResolver> customToolResolvers = new ArrayList<>();
    private Path cacheRoot;

    public AgentHandlerFactoryBuilder toolResolver(ToolResolver resolver) {
        customToolResolvers.add(resolver);
        return this;
    }

    public AgentHandlerFactoryBuilder cacheRoot(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
        return this;
    }

    public AgentRuntimeHandler fromYaml(Path yamlPath) {
        AgentSpec spec = new AgentYamlLoader().load(yamlPath);
        String agentType = normalize(spec.agentType());
        if ("react".equals(agentType)) {
            return toHandler(spec.name(), toReactAgent(spec));
        }
        if ("deepagent".equals(agentType)) {
            return toHandler(spec.name(), toDeepAgent(spec));
        }
        throw unsupportedSpec(spec);
    }

    public ReActAgent toReactAgent(Path yamlPath) {
        return toReactAgent(new AgentYamlLoader().load(yamlPath));
    }

    public Object toDeepAgent(Path yamlPath) {
        return toDeepAgent(new AgentYamlLoader().load(yamlPath));
    }

    public AgentRuntimeHandler toHandler(String name, Object agent) {
        if (agent instanceof ReActAgent reactAgent) {
            return OpenJiuwenReactAgentBuilder.toHandler(name, reactAgent);
        }
        if (OpenJiuwenDeepAgentBuilder.supports(agent)) {
            return OpenJiuwenDeepAgentBuilder.toHandler(name, agent);
        }
        throw new UnsupportedFrameworkException("Unsupported OpenJiuwen agent instance: "
                + (agent == null ? "null" : agent.getClass().getName()));
    }

    private ReActAgent toReactAgent(AgentSpec spec) {
        if (!"openjiuwen".equalsIgnoreCase(spec.frameworkType()) || !"react".equalsIgnoreCase(spec.agentType())) {
            throw unsupportedSpec(spec);
        }
        Path effectiveCacheRoot = spec.cacheRoot() != null ? spec.cacheRoot() : cacheRoot;
        if (effectiveCacheRoot != null) {
            // Reserved for localCache-enabled resource materialization.
        }
        return new OpenJiuwenReactAgentBuilder(toolResolvers()).buildAgent(spec);
    }

    private Object toDeepAgent(AgentSpec spec) {
        if (!"openjiuwen".equalsIgnoreCase(spec.frameworkType()) || !"deepagent".equalsIgnoreCase(spec.agentType())) {
            throw unsupportedSpec(spec);
        }
        Path effectiveCacheRoot = spec.cacheRoot() != null ? spec.cacheRoot() : cacheRoot;
        if (effectiveCacheRoot != null) {
            // Reserved for localCache-enabled resource materialization.
        }
        return new OpenJiuwenDeepAgentBuilder(toolResolvers()).buildAgent(spec);
    }

    private List<ToolResolver> toolResolvers() {
        List<ToolResolver> resolvers = new ArrayList<>(customToolResolvers);
        resolvers.add(new JavaFileToolResolver());
        resolvers.add(new HttpToolResolver());
        resolvers.add(new McpToolResolver());
        return List.copyOf(resolvers);
    }

    private UnsupportedFrameworkException unsupportedSpec(AgentSpec spec) {
        return new UnsupportedFrameworkException("Only OpenJiuwen react/deepagent YAML is supported by agent-sdk: "
                + spec.frameworkType() + "/" + spec.agentType());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}

