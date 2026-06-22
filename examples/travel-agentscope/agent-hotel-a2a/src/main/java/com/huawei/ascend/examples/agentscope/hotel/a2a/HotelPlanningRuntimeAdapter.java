/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel.a2a;

import com.huawei.ascend.examples.agentscope.hotel.HotelPlanningAgent;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeEvent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeInvocation;

import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge between agent-runtime's {@link AgentScopeAgent} SAM and the pure-AgentScope
 * {@link HotelPlanningAgent} library. Lives in the wrapper layer so the agent body
 * has no compile-time dependency on agent-runtime — swapping the runtime out only
 * touches this adapter.
 */
final class HotelPlanningRuntimeAdapter implements AgentScopeAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelPlanningRuntimeAdapter.class);

    private final HotelPlanningAgent agent;

    HotelPlanningRuntimeAdapter(HotelPlanningAgent agent) {
        this.agent = Objects.requireNonNull(agent, "agent");
    }

    @Override
    public Stream<AgentScopeEvent> streamEvents(AgentScopeInvocation invocation) {
        try {
            LOGGER.info(
                    "hotel agentscope execute start tenantId={} sessionId={} taskId={} agentId={} baseUrl={} model={}",
                    invocation.tenantId(),
                    invocation.sessionId(),
                    invocation.taskId(),
                    invocation.agentId(),
                    agent.llmConfig().baseUrl(),
                    agent.llmConfig().modelName());
            List<Event> events = agent.stream(toAgentScopeMessages(invocation));
            return toRuntimeEvents(events);
        } catch (RuntimeException ex) {
            LOGGER.warn(
                    "hotel agentscope execute failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    invocation.tenantId(),
                    invocation.sessionId(),
                    invocation.taskId(),
                    ex.getClass().getSimpleName(),
                    rootMessage(ex));
            throw new IllegalStateException(rootMessage(ex), ex);
        }
    }

    /**
     * Convert runtime turns into AgentScope {@link Msg}s. AGENT-role turns become
     * ASSISTANT messages, everything else USER; an empty list yields a single
     * empty USER message because the underlying stream rejects empty input.
     */
    private List<Msg> toAgentScopeMessages(AgentScopeInvocation invocation) {
        List<Msg> messages = new ArrayList<>();
        for (RuntimeMessage rm : invocation.messages()) {
            messages.add(Msg.builder()
                    .name(agent.agentId())
                    .role(rm.role() == RuntimeMessage.Role.AGENT ? MsgRole.ASSISTANT : MsgRole.USER)
                    .textContent(rm.text() == null ? "" : rm.text())
                    .build());
        }
        if (messages.isEmpty()) {
            messages.add(Msg.builder().name(agent.agentId()).role(MsgRole.USER).textContent("").build());
        }
        return messages;
    }

    /**
     * Intermediate AGENT_RESULT events become OUTPUT (so the A2A trajectory sees
     * streaming progress); the last event becomes COMPLETED. An empty stream still
     * yields one empty COMPLETED so the A2A surface terminates cleanly.
     */
    private static Stream<AgentScopeEvent> toRuntimeEvents(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Stream.of(AgentScopeEvent.completed(""));
        }
        List<AgentScopeEvent> out = new ArrayList<>();
        StringBuilder progress = new StringBuilder();
        String lastText = "";
        for (Event event : events) {
            String text = event.getMessage() == null ? "" : event.getMessage().getTextContent();
            lastText = text;
            if (event.isLast()) {
                out.add(AgentScopeEvent.completed(progress.isEmpty() ? text : ""));
            } else if (!text.isBlank()) {
                progress.append(text);
                out.add(AgentScopeEvent.output(text));
            }
        }
        if (out.stream().noneMatch(e -> e.type() == AgentScopeEvent.Type.COMPLETED)) {
            out.add(AgentScopeEvent.completed(lastText));
        }
        return out.stream();
    }

    private static String rootMessage(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = error;
        while (cur != null) {
            String m = cur.getMessage();
            if (m != null && !m.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(": ");
                }
                sb.append(m);
            }
            cur = cur.getCause();
        }
        return sb.isEmpty() ? error.getClass().getName() : sb.toString();
    }
}