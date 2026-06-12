/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import com.huawei.ascend.examples.hotel.HotelPlanningAgent;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.Messages;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.List;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HotelAgentHandler implements AgentRuntimeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelAgentHandler.class);

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
        String query = extractLastUserText(context);
        LOGGER.info("hotel a2a execute tenantId={} sessionId={} taskId={} queryLength={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                query.length());
        String markdown = agent.chat(query);
        return Stream.of(markdown);
    }

    @Override
    public StreamAdapter resultAdapter() {
        return raw -> raw.map(r -> AgentExecutionResult.completed(String.valueOf(r)));
    }

    private static String extractLastUserText(AgentExecutionContext context) {
        List<Message> messages = context.getMessages();
        if (messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m != null && m.role() == Message.Role.ROLE_USER) {
                return Messages.text(m);
            }
        }
        return Messages.text(messages.get(messages.size() - 1));
    }
}
