/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel;

import com.huawei.ascend.examples.agentscope.hotel.mock.MockHotelInventory;
import com.huawei.ascend.examples.agentscope.hotel.prompt.SystemPromptBuilder;
import com.huawei.ascend.examples.agentscope.hotel.tool.HotelSkills;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeEvent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeInvocation;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AgentScope-flavored hotel-planning sub-agent. Mirrors
 * {@code com.huawei.ascend.examples.hotel.HotelPlanningAgent} (openJiuwen flavor) but
 * is built entirely on {@code io.agentscope:agentscope-core}: a {@link ReActAgent}
 * over {@link OpenAIChatModel}, with the two hotel skills registered through
 * {@link Toolkit#registerTool(Object)}.
 *
 * <p>Implements {@link AgentScopeAgent} so it can be wrapped by
 * {@code AgentScopeAgentRuntimeHandler} and exposed by agent-runtime over A2A. Each
 * invocation builds a fresh inner ReActAgent — sharing one across calls would let
 * conversation state leak across A2A sessions.
 *
 * <p>Long-term memory parity with the openJiuwen sibling is not yet implemented
 * because the AgentScope handler family has no memory rail; see runtime issue #316.
 */
public final class HotelPlanningAgent implements AgentScopeAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelPlanningAgent.class);
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

    public int inventorySize() {
        return inventory.totalHotels();
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
                    llm.baseUrl(),
                    llm.modelName());
            List<Event> events = buildReActAgent()
                    .stream(toAgentScopeMessages(invocation), streamOptions())
                    .collectList()
                    .block(MODEL_TIMEOUT);
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

    /**
     * Build the AgentScope message list: the invocation messages are forwarded as USER /
     * ASSISTANT turns. The system prompt is supplied via {@link ReActAgent.Builder#sysPrompt(String)}
     * — duplicating it here as a SYSTEM Msg would shadow the ReActAgent's own prompt assembly.
     * When no user-side messages are present the agent must still receive at least one Msg or
     * the underlying stream rejects the call.
     */
    private List<Msg> toAgentScopeMessages(AgentScopeInvocation invocation) {
        List<Msg> messages = new ArrayList<>();
        for (RuntimeMessage rm : invocation.messages()) {
            messages.add(Msg.builder()
                    .name(agentId)
                    .role(rm.role() == RuntimeMessage.Role.AGENT ? MsgRole.ASSISTANT : MsgRole.USER)
                    .textContent(rm.text() == null ? "" : rm.text())
                    .build());
        }
        if (messages.isEmpty()) {
            messages.add(Msg.builder().name(agentId).role(MsgRole.USER).textContent("").build());
        }
        return messages;
    }

    private StreamOptions streamOptions() {
        return StreamOptions.builder()
                .eventTypes(EventType.AGENT_RESULT)
                .incremental(false)
                .build();
    }

    /**
     * Translate the AgentScope event stream into runtime-flavored events. Intermediate
     * AGENT_RESULT events become OUTPUT (so the A2A trajectory sees streaming progress);
     * the last event becomes COMPLETED. If the stream is empty the runtime gets a single
     * empty COMPLETED, which the A2A surface still terminates cleanly.
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