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
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entry point for the hotel-planning sub-agent.
 *
 * <p>Construct once per host process — the underlying {@link MockHotelInventory} and
 * openJiuwen tool registrations are reused across calls. Each call to
 * {@link #chat(String)} runs one fresh ReAct loop with a unique conversation id and
 * releases its session resources before returning.
 *
 * <p>The ReAct agent is intentionally built per invocation by {@link #newBaseAgent()}.
 * Sharing one ReActAgent across calls would accumulate registered rails (memory,
 * trajectory) on every invocation, which the runtime-side OpenJiuwen handler installs
 * fresh each time.
 *
 * <p>Thread safety: {@link #chat(String)} is safe to call from multiple threads — each
 * call builds its own agent against the shared inventory and tool instances. The
 * tools and inventory are effectively immutable after construction.
 *
 * <p>Lifecycle: callers may invoke {@link #close()} on shutdown to remove the registered
 * tools from the global {@link Runner}. Closing is optional in single-process runs but
 * recommended in tests.
 */
public final class HotelPlanningAgent implements AutoCloseable {

    private static final int MAX_ITERATIONS = 6;
    private static final String AGENT_ID_PREFIX = "hotel-planning-agent-";
    private static final AtomicLong INSTANCE_COUNTER = new AtomicLong();

    private final String agentId;
    private final LlmConfig llm;
    private final Tool searchTool;
    private final Tool detailTool;
    private final MockHotelInventory inventory;

    public HotelPlanningAgent(LlmConfig llm) {
        this(llm, new MockHotelInventory());
    }

    /** Visible for tests that want to inject a tailored inventory. */
    public HotelPlanningAgent(LlmConfig llm, MockHotelInventory inventory) {
        this.llm = Objects.requireNonNull(llm, "llm");
        this.inventory = Objects.requireNonNull(inventory, "inventory");

        // Per-instance agent id so multiple hotel agents (or hotel+flight+train) in the same
        // process don't fight over the global Runner's tag-to-tool index.
        this.agentId = AGENT_ID_PREFIX + INSTANCE_COUNTER.incrementAndGet();

        this.searchTool = new HotelSearchTool(inventory);
        this.detailTool = new HotelDetailTool(inventory);
        registerTool(searchTool);
        registerTool(detailTool);
    }

    /** Stable agent id used as the tool tag in the global Runner registry. */
    public String agentId() {
        return agentId;
    }

    /**
     * Build a fresh, fully-configured openJiuwen ReAct agent that the runtime can
     * decorate with rails (memory / trajectory) for this invocation. The returned
     * agent already knows about {@code hotel_search} / {@code hotel_detail} through
     * the global Runner registry, so the caller only needs to drive
     * {@code Runner.runAgent}.
     */
    public BaseAgent newBaseAgent() {
        AgentCard card = AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description("差旅多智能体系统中的酒店规划子智能体（ReAct + 内存 mock 数据）")
                .build();
        ReActAgent agent = new ReActAgent(card);
        // Do NOT set ReActAgentConfig.promptTemplate(...): that overrides
        // ReActAgent's SystemPromptBuilder wholesale, which makes the runtime
        // memory rail's addPromptBuilderSection(...) silently no-op (the
        // assembled system message ignores the builder when a fixed template
        // is configured). Instead, register hotel rules as a high-priority
        // builder section so the memory rail's section (priority 50) can
        // co-exist on the same prompt assembly path.
        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(MAX_ITERATIONS)
                .build()
                .configureModelClient(
                        llm.provider(),
                        llm.apiKey(),
                        llm.apiBase(),
                        llm.modelName(),
                        llm.sslVerify());
        agent.configure(config);
        // Priority 20 places hotel rules just after the agent identity section
        // (priority 10) and before the runtime memory section (priority 50).
        agent.addPromptBuilderSection("hotel_business_rules", SystemPromptBuilder.build(), 20);
        agent.getAbilityManager().add(searchTool.getCard());
        agent.getAbilityManager().add(detailTool.getCard());
        return agent;
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
        BaseAgent agent = newBaseAgent();
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
     * Unregister this agent's tools from the global Runner. Idempotent; safe to skip in
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
