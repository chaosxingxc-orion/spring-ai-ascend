package com.huawei.ascend.runtime.engine.adapters.openjiuwen;

import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentDriver;
import com.huawei.ascend.runtime.engine.spi.OutputConverter;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * openJiuwen ReAct {@link com.huawei.ascend.runtime.engine.spi.AgentDriver}. Wires an openJiuwen
 * {@code ReActAgent} behind the neutral driver SPI; every openJiuwen specific (ReActAgent,
 * Runner, model config, result-map shape, resource release) stays inside this adapter so the
 * runtime core is never customised for openJiuwen.
 *
 * <p>{@link #invoke(InvocationRequest)} returns openJiuwen's native result as an opaque object;
 * {@link #outputConverter()} (an {@link OpenJiuwenOutputConverter}) turns it into the neutral
 * {@code RunEvent} stream.
 */
public final class OpenJiuwenAgentDriver extends AbstractAgentDriver {

    private final String agentId;
    private final String systemPrompt;
    private final String modelProvider;
    private final String apiKey;
    private final String apiBase;
    private final String modelName;
    private final boolean sslVerify;
    private final OpenJiuwenOutputConverter outputConverter = new OpenJiuwenOutputConverter();

    public OpenJiuwenAgentDriver(
            String agentId,
            String systemPrompt,
            String modelProvider,
            String apiKey,
            String apiBase,
            String modelName,
            boolean sslVerify) {
        this.agentId = agentId;
        this.systemPrompt = systemPrompt;
        this.modelProvider = modelProvider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        this.sslVerify = sslVerify;
    }

    @Override
    public String name() {
        return agentId;
    }

    @Override
    public String description() {
        return "openJiuwen ReAct agent driven through the agent-runtime neutral SPI.";
    }

    @Override
    public String frameworkId() {
        return "openjiuwen";
    }

    @Override
    public Object invoke(InvocationRequest request) {
        String conversationId = request.sessionId() == null ? request.requestId() : request.sessionId();
        ReActAgent agent = buildAgent();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", request.input());
        input.put("conversation_id", conversationId);
        try {
            Object result = Runner.runAgent(agent, input, null, null);
            return Collections.singletonList(result);
        } finally {
            try {
                Runner.release(conversationId);
            } catch (Exception ignored) {
                // best-effort cleanup; release failure must not mask the result
            }
        }
    }

    @Override
    public OutputConverter outputConverter() {
        return outputConverter;
    }

    private ReActAgent buildAgent() {
        AgentCard card = AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description("openJiuwen ReAct agent driven through the agent-runtime neutral SPI.")
                .build();
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                .maxIterations(3)
                .build()
                .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
        ModelRequestConfig modelConfig = config.getModelConfigObj();
        modelConfig.setTemperature(0.0);
        modelConfig.setMaxTokens(64);
        agent.configure(config);
        return agent;
    }
}
