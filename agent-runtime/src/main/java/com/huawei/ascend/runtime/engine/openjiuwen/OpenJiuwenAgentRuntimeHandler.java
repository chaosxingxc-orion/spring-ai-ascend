package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.service.AgentStateSnapshot;
import com.huawei.ascend.runtime.engine.spi.AbstractStatefulAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for openJiuwen {@link AgentRuntimeHandler} implementations. The concrete
 * handler owns how it builds and invokes its openJiuwen agent; this class
 * provides the runtime-facing id, input/result mapping helpers, and the bridge
 * between runtime Agent State and openJiuwen {@link AgentSessionApi} state.
 */
public abstract class OpenJiuwenAgentRuntimeHandler extends AbstractStatefulAgentRuntimeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenAgentRuntimeHandler.class);
    static final String STATE_SESSION_ID = "openjiuwen.sessionId";
    static final String STATE_VALUES = "openjiuwen.state";

    private final ThreadLocal<AgentSessionApi> currentSession = new ThreadLocal<>();
    private final OpenJiuwenMessageAdapter messageConverter;
    private final OpenJiuwenStreamAdapter resultMapper;

    protected OpenJiuwenAgentRuntimeHandler(String agentId) {
        this(agentId, new OpenJiuwenMessageAdapter());
    }

    protected OpenJiuwenAgentRuntimeHandler(String agentId, String description) {
        this(agentId, agentId, description, new OpenJiuwenMessageAdapter(), new OpenJiuwenStreamAdapter());
    }

    protected OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter) {
        this(agentId, agentId, "openJiuwen agent " + agentId, messageConverter, new OpenJiuwenStreamAdapter());
    }

    OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter,
            OpenJiuwenStreamAdapter resultMapper) {
        this(agentId, agentId, "openJiuwen agent " + agentId, messageConverter, resultMapper);
    }

    OpenJiuwenAgentRuntimeHandler(String agentId, String name, String description,
            OpenJiuwenMessageAdapter messageConverter, OpenJiuwenStreamAdapter resultMapper) {
        super(agentId, name, description);
        this.messageConverter = messageConverter;
        this.resultMapper = resultMapper;
    }

    @Override
    protected void beforeExecute(AgentExecutionContext context) {
        currentSession.remove();
    }

    @Override
    protected void afterExecute(AgentExecutionContext context) {
        AgentSessionApi session = currentSession.get();
        currentSession.remove();
        if (session == null) {
            return;
        }
        Map<String, Object> values = new LinkedHashMap<>(
                context.getAgentState().map(AgentStateSnapshot::values).orElseGet(Map::of));
        values.put(STATE_SESSION_ID, session.getSessionId());
        values.put(STATE_VALUES, new LinkedHashMap<>(session.dumpState()));
        context.replaceAgentState(values);
    }

    /**
     * Creates the openJiuwen session for the current execution from the runtime
     * Agent State snapshot. Subclasses should pass the returned session to
     * {@code Runner.runAgent(...)} or {@code Runner.runAgentStreaming(...)}.
     */
    protected AgentSessionApi openJiuwenSession(AgentExecutionContext context, BaseAgent agent) {
        AgentStateSnapshot snapshot = context.getAgentState().orElse(null);
        Map<String, Object> values = snapshot == null ? Map.of() : snapshot.values();
        String sessionId = stateString(values.get(STATE_SESSION_ID), context.getScope().taskId());
        AgentSessionApi session = AgentSessionApi.create(sessionId, Map.of(), agent == null ? null : agent.getCard());
        Map<String, Object> restoredState = openJiuwenGlobalState(values.get(STATE_VALUES));
        if (!restoredState.isEmpty()) {
            session.updateState(restoredState);
        }
        currentSession.set(session);
        LOGGER.info("openjiuwen state restore tenantId={} sessionId={} taskId={} agentId={} stateKeys={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                context.getScope().agentId(),
                restoredState.keySet());
        return session;
    }

    protected Object toOpenJiuwenInput(AgentExecutionContext context) {
        LOGGER.info("openjiuwen input convert tenantId={} sessionId={} taskId={} agentId={} inputType={} messages={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                context.getScope().agentId(),
                context.getInput().inputType(),
                context.getInput().messages().size());
        return messageConverter.toOpenJiuwenInput(context);
    }

    @Override
    public StreamAdapter resultAdapter() {
        return rawResults -> rawResults.map(this::mapRawResult);
    }

    @SuppressWarnings("unchecked")
    private com.huawei.ascend.runtime.engine.spi.AgentExecutionResult mapRawResult(Object rawResult) {
        LOGGER.info("openjiuwen raw result received type={}",
                rawResult == null ? "null" : rawResult.getClass().getName());
        if (rawResult instanceof Map<?, ?> map) {
            return resultMapper.map((Map<String, Object>) map);
        }
        return resultMapper.map(Map.of("result_type", "answer", "output", String.valueOf(rawResult)));
    }

    private static String stateString(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private static Map<String, Object> stateMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> state = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key instanceof String textKey) {
                state.put(textKey, item);
            }
        });
        return state;
    }

    private static Map<String, Object> openJiuwenGlobalState(Object value) {
        Map<String, Object> dump = stateMap(value);
        Object global = dump.get("global_state");
        if (global instanceof Map<?, ?>) {
            return stateMap(global);
        }
        return dump;
    }

    protected void safeRelease(AgentExecutionContext context) {
        safeRelease(context.getScope());
    }

    protected void safeRelease(EngineExecutionScope scope) {
        try {
            Runner.release(scope.taskId());
        } catch (Exception ignored) {
            // best-effort cleanup; release failures must not mask the result
        }
    }

    protected static String errorMessage(Throwable error) {
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
