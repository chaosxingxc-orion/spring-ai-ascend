/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.trip;

import com.huawei.ascend.examples.agentscope.trip.prompt.SystemPromptBuilder;
import com.huawei.ascend.examples.agentscope.trip.tool.TripSkills;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * AgentScope-flavored trip planning agent. Mirrors the sibling
 * {@code com.huawei.ascend.examples.agentscope.hotel.HotelPlanningAgent}: a
 * {@link ReActAgent} over {@link OpenAIChatModel} with a single local tool
 * ({@code plan_hotel}) registered through {@link Toolkit#registerTool(Object)}.
 *
 * <p>Pure-AgentScope library — has no compile-time dependency on agent-runtime.
 * A runtime host wraps this agent through an adapter that implements the
 * AgentScope runtime SAM (see {@code agent-trip-a2a/TripPlanningRuntimeAdapter}).
 *
 * <p>The {@code plan_hotel} tool delegates through {@link TripSkills} to a
 * {@link com.huawei.ascend.examples.agentscope.trip.tool.HotelPlannerClient} the
 * host supplies — typically the A2A binding shipped by the
 * {@code agent-trip-a2a} wrapper. The AgentScope handler family has no
 * remote-agent auto-injection hook (OpenJiuwen has it via
 * {@code OpenJiuwenRemoteToolInstaller}), so the tool is wired here as a
 * regular local skill rather than injected by the runtime.
 *
 * <p>Each {@link #stream(List)} call builds a fresh inner ReActAgent so
 * conversation state does not leak across sessions. Long-term memory parity
 * with the OpenJiuwen trip agent is not implemented because the AgentScope
 * handler has no memory rail (see runtime issue #316).
 */
public final class TripPlanningAgent {

    private static final int DEFAULT_MAX_ITERS = 5;
    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(180);

    private final String agentId;
    private final LlmConfig llm;
    private final TripSkills skills;
    private final int maxIters;

    public TripPlanningAgent(String agentId, LlmConfig llm, TripSkills skills) {
        this(agentId, llm, skills, DEFAULT_MAX_ITERS);
    }

    public TripPlanningAgent(String agentId, LlmConfig llm, TripSkills skills, int maxIters) {
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.llm = Objects.requireNonNull(llm, "llm");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.maxIters = maxIters;
    }

    public String agentId() {
        return agentId;
    }

    public LlmConfig llmConfig() {
        return llm;
    }

    /**
     * Run one ReAct turn over the supplied conversation history and return the
     * collected AgentScope events. Pure AgentScope API: the runtime adapter in
     * the wrapper module translates these into runtime events.
     */
    public List<Event> stream(List<Msg> messages) {
        return buildReActAgent()
                .stream(messages, streamOptions())
                .collectList()
                .block(MODEL_TIMEOUT);
    }

    private ReActAgent buildReActAgent() {
        GenerateOptions options = GenerateOptions.builder()
                .stream(true)
                .temperature(0.1)
                .maxTokens(1500)
                .build();
        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(llm.apiKey())
                .baseUrl(llm.baseUrl())
                .endpointPath(llm.endpointPath())
                .modelName(llm.modelName())
                .stream(true)
                .formatter(new OpenAIChatFormatter())
                .generateOptions(options)
                .build();
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(skills);
        return ReActAgent.builder()
                .name(agentId)
                .description("差旅多智能体系统中的行程规划智能体（AgentScope ReAct + plan_hotel 工具）")
                .sysPrompt(SystemPromptBuilder.build(TripSkills.HOTEL_TOOL_NAME))
                .model(model)
                .toolkit(toolkit)
                .maxIters(maxIters)
                .generateOptions(options)
                .build();
    }

    private static StreamOptions streamOptions() {
        return StreamOptions.builder()
                .eventTypes(EventType.AGENT_RESULT)
                .incremental(false)
                .build();
    }
}