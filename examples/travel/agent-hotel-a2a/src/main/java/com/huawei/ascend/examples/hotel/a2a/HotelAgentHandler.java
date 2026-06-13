/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import com.huawei.ascend.examples.hotel.HotelPlanningAgent;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plain SPI handler: the hotel agent answers one blocking chat call, so it
 * needs neither the openJiuwen adapter lifecycle nor any protocol types — it
 * consumes only the neutral execution context.
 */
final class HotelAgentHandler implements AgentRuntimeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelAgentHandler.class);
    private static final StreamAdapter ADAPTER = rawResults -> rawResults.map(AgentExecutionResult.class::cast);

    private final String agentId;
    private final HotelPlanningAgent agent;

    HotelAgentHandler(String agentId, HotelPlanningAgent agent) {
        this.agentId = agentId;
        this.agent = agent;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public Stream<?> execute(AgentExecutionContext context) {
        String query = context.lastUserText();
        LOGGER.info("hotel a2a execute tenantId={} sessionId={} taskId={} queryLength={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                query.length());
        try {
            String markdown = agent.chat(query);
            return Stream.of(AgentExecutionResult.completed(markdown));
        } catch (Exception e) {
            LOGGER.warn("hotel a2a execute failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    e.getClass().getSimpleName(),
                    errorMessage(e));
            throw new IllegalStateException(errorMessage(e), e);
        }
    }

    @Override
    public StreamAdapter resultAdapter() {
        return ADAPTER;
    }

    private static String errorMessage(Throwable error) {
        StringBuilder message = new StringBuilder();
        Throwable cursor = error;
        while (cursor != null) {
            String part = cursor.getMessage();
            if (part != null && !part.isBlank()) {
                if (!message.isEmpty()) {
                    message.append(": ");
                }
                message.append(part);
            }
            cursor = cursor.getCause();
        }
        return message.isEmpty() ? error.getClass().getName() : message.toString();
    }
}
