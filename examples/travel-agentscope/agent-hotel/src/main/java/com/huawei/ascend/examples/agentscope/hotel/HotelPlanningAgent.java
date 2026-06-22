/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel;

import com.huawei.ascend.examples.agentscope.hotel.mock.MockHotelInventory;
import com.huawei.ascend.examples.agentscope.hotel.prompt.SystemPromptBuilder;
import com.huawei.ascend.examples.agentscope.hotel.tool.HotelSkills;

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
 * AgentScope-flavored hotel-planning sub-agent. Mirrors
 * {@code com.huawei.ascend.examples.hotel.HotelPlanningAgent} (openJiuwen flavor) but
 * is built entirely on {@code io.agentscope:agentscope-core}: a {@link ReActAgent}
 * over {@link OpenAIChatModel}, with the two hotel skills registered through
 * {@link Toolkit#registerTool(Object)}.
 *
 * <p>Pure-AgentScope library — has no compile-time dependency on agent-runtime. A
 * runtime host wraps this agent through an adapter that implements the AgentScope
 * runtime SAM (see {@code agent-hotel-a2a/HotelPlanningRuntimeAdapter}); other hosts
 * may invoke {@link #stream(List)} directly with AgentScope {@link Msg} turns.
 *
 * <p>Each {@link #stream(List)} call builds a fresh inner ReActAgent — sharing one
 * across calls would let conversation state leak across sessions.
 */
public final class HotelPlanningAgent {

    private static final int MAX_ITERS = 6;
    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(120);

    private final String agentId;
    private final LlmConfig llm;
    private final MockHotelInventory inventory;
    private final HotelSkills skills;

    public HotelPlanningAgent(String agentId, LlmConfig llm) {
        this(agentId, llm, new MockHotelInventory());
    }

    public HotelPlanningAgent(String agentId, LlmConfig llm, MockHotelInventory inventory) {
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.llm = Objects.requireNonNull(llm, "llm");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.skills = new HotelSkills(inventory);
    }

    public String agentId() {
        return agentId;
    }

    public LlmConfig llmConfig() {
        return llm;
    }

    public int inventorySize() {
        return inventory.totalHotels();
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
                .maxTokens(1200)
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
                .description("差旅多智能体系统中的酒店规划子智能体（AgentScope ReAct + 内存 mock 数据）")
                .sysPrompt(SystemPromptBuilder.build())
                .model(model)
                .toolkit(toolkit)
                .maxIters(MAX_ITERS)
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