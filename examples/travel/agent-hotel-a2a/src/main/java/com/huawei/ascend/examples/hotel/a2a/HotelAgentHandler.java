/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import com.huawei.ascend.examples.hotel.HotelPlanningAgent;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenMessageAdapter;
import java.util.List;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HotelAgentHandler extends OpenJiuwenAgentRuntimeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelAgentHandler.class);

    private final HotelPlanningAgent agent;

    HotelAgentHandler(String agentId, HotelPlanningAgent agent) {
        super(agentId);
        this.agent = agent;
    }

    @Override
    public Stream<?> execute(AgentExecutionContext context) {
        String query = extractLastUserText(context);
        LOGGER.info("hotel a2a execute tenantId={} sessionId={} taskId={} queryLength={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                query.length());
        try {
            String markdown = agent.chat(query);
            return Stream.of(markdown);
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

    private static String extractLastUserText(AgentExecutionContext context) {
        List<Message> messages = context.getMessages();
        if (messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m != null && m.role() == Message.Role.ROLE_USER) {
                return OpenJiuwenMessageAdapter.messageText(m);
            }
        }
        return OpenJiuwenMessageAdapter.messageText(messages.get(messages.size() - 1));
    }
}
