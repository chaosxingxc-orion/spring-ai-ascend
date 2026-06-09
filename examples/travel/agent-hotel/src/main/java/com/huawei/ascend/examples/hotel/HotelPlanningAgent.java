/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel;

import com.huawei.ascend.examples.hotel.mock.MockHotelInventory;
import com.huawei.ascend.examples.hotel.prompt.SystemPromptBuilder;
import com.huawei.ascend.examples.hotel.tool.HotelDetailTool;
import com.huawei.ascend.examples.hotel.tool.HotelSearchTool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entry point for the hotel-planning sub-agent.
 *
 * <p>Construct once per host process — the underlying {@link MockHotelInventory},
 * {@link ReActAgent} and openJiuwen tool registrations are reused across calls. Each call
 * to {@link #chat(String)} runs one fresh ReAct loop with a unique conversation id and
 * releases its session resources before returning, so calls remain stateless.
 *
 * <p>Thread safety: {@link #chat(String)} is safe to call from multiple threads — openJiuwen
 * isolates per-call state inside the {@link Runner}. The inventory and ReAct agent
 * instances are effectively immutable after construction.
 *
 * <p>Lifecycle: callers may invoke {@link #close()} on shutdown to remove the registered
 * tools from the global {@link Runner} and stop the runner worker pool. Closing is
 * optional in single-process runs but recommended in tests.
 */
public final class HotelPlanningAgent implements AutoCloseable {

    private static final int MAX_ITERATIONS = 6;
    private static final String AGENT_ID_PREFIX = "hotel-planning-agent-";
    private static final AtomicLong INSTANCE_COUNTER = new AtomicLong();

    private final String agentId;
    private final ReActAgent agent;
    private final Tool searchTool;
    private final Tool detailTool;
    private final MockHotelInventory inventory;

    public HotelPlanningAgent(LlmConfig llm) {
        this(llm, new MockHotelInventory());
    }

    /** Visible for tests that want to inject a tailored inventory. */
    public HotelPlanningAgent(LlmConfig llm, MockHotelInventory inventory) {
        Objects.requireNonNull(llm, "llm");
        this.inventory = Objects.requireNonNull(inventory, "inventory");

        // Per-instance agent id so multiple hotel agents (or hotel+flight+train) in the same
        // process don't fight over the global Runner's tag-to-tool index.
        this.agentId = AGENT_ID_PREFIX + INSTANCE_COUNTER.incrementAndGet();

        AgentCard card = AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description("差旅多智能体系统中的酒店规划子智能体（ReAct + 内存 mock 数据）")
                .build();

        this.agent = new ReActAgent(card);

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of(
                        "role", "system",
                        "content", SystemPromptBuilder.build())))
                .maxIterations(MAX_ITERATIONS)
                .build()
                .configureModelClient(
                        llm.provider(),
                        llm.apiKey(),
                        llm.apiBase(),
                        llm.modelName(),
                        llm.sslVerify());
        agent.configure(config);

        this.searchTool = new HotelSearchTool(inventory);
        this.detailTool = new HotelDetailTool(inventory);
        registerTool(searchTool);
        registerTool(detailTool);
    }

    /**
     * Run one ReAct loop and return the markdown recommendation.
     *
     * <p>The conversation id is fresh on every call so there is no carryover. If the host
     * wants multi-turn behavior it should accumulate context on its side and feed it back
     * in the next {@code userMessage}.
     */
    public String chat(String userMessage) {
        Objects.requireNonNull(userMessage, "userMessage");
        String conversationId = agentId + "-" + UUID.randomUUID();
        try {
            Object raw = Runner.runAgent(
                    agent,
                    Map.of("query", userMessage, "conversation_id", conversationId),
                    null,
                    null);
            return extractOutput(raw);
        } finally {
            Runner.release(conversationId);
        }
    }

    /**
     * Unregister this agent's tools and stop the global Runner. Idempotent; safe to skip in
     * short-lived processes.
     */
    @Override
    public void close() {
        try {
            Runner.resourceMgr().removeTool(
                    searchTool.getCard().getId(), agentId, TagMatchStrategy.ALL, true);
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
        try {
            Runner.resourceMgr().removeTool(
                    detailTool.getCard().getId(), agentId, TagMatchStrategy.ALL, true);
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
    }

    /** Total hotels backing this agent — useful for ops checks and tests. */
    public int inventorySize() {
        return inventory.totalHotels();
    }

    private void registerTool(Tool tool) {
        // Defensive remove first — in case a previous instance with the same agentId
        // left state behind (only possible if the JVM was reused without close()).
        try {
            Runner.resourceMgr().removeTool(
                    tool.getCard().getId(), agentId, TagMatchStrategy.ALL, true);
        } catch (RuntimeException ignored) {
            // expected on first registration
        }
        Runner.resourceMgr().addTool(tool, agentId);
        agent.getAbilityManager().add(tool.getCard());
    }

    @SuppressWarnings("unchecked")
    private static String extractOutput(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object output = ((Map<String, Object>) map).get("output");
            if (output != null) {
                return String.valueOf(output);
            }
        }
        return raw == null ? "" : String.valueOf(raw);
    }
}
