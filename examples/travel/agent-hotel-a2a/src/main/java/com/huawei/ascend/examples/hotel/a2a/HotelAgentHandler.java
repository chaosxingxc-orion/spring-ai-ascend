/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import com.huawei.ascend.examples.hotel.HotelPlanningAgent;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.List;
import java.util.Objects;

/**
 * OpenJiuwen-rail-based handler: delegates RUN lifecycle, memory recall and
 * memory save to {@link OpenJiuwenAgentRuntimeHandler} + its built-in
 * {@code MemoryRuntimeRail}, which writes the recalled block into the same
 * prompt-builder section ({@code runtime_long_term_memory}, priority 50) that
 * {@link HotelPlanningAgent#newBaseAgent()} is built for.
 */
final class HotelAgentHandler extends OpenJiuwenAgentRuntimeHandler {

    private final HotelPlanningAgent agent;
    private final MemoryProvider memoryProvider;

    HotelAgentHandler(String agentId, HotelPlanningAgent agent, MemoryProvider memoryProvider) {
        super(agentId);
        this.agent = Objects.requireNonNull(agent, "agent");
        this.memoryProvider = Objects.requireNonNull(memoryProvider, "memoryProvider");
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        return agent.newBaseAgent();
    }

    @Override
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        return List.of(memoryRuntimeRail(context, memoryProvider));
    }
}