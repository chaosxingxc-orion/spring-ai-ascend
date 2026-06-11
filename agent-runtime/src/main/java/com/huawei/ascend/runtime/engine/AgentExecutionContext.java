package com.huawei.ascend.runtime.engine;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.spi.TrajectoryRuntime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.a2aproject.sdk.spec.Message;
import org.springframework.util.Assert;

/**
 * Minimal execution context — the contract between the A2A SDK bridge and
 * framework adapters. Deliberately decoupled from A2A SDK's heavy
 * {@code RequestContext} (which carries Task, Message, ServerCallContext etc.).
 */
public final class AgentExecutionContext {

    public static final String AGENT_STATE_KEY_VARIABLE = "agentStateKey";
    public static final String STATE_KEY_VARIABLE = "stateKey";
    public static final String INPUT_TYPE_REMOTE_RESUME = "REMOTE_RESUME";
    public static final String REMOTE_TOOL_CALL_ID_VARIABLE = "runtime.remoteToolCallId";
    public static final String REMOTE_TOOL_RESULT_VARIABLE = "runtime.remoteToolResult";

    private final RuntimeIdentity scope;
    private final String inputType;
    private final List<Message> messages;
    private final Map<String, Object> variables;
    private final String agentStateKey;
    private volatile Map<String, Object> agentState;
    /** Per-invocation trajectory wiring; null until a TrajectorySource handler opens it. */
    private volatile TrajectoryRuntime trajectoryRuntime;

    public AgentExecutionContext(RuntimeIdentity scope, String inputType,
                                  List<Message> messages, Map<String, Object> variables) {
        this(scope, inputType, messages, variables, resolveAgentStateKey(scope, variables), null);
    }

    public AgentExecutionContext(RuntimeIdentity scope, String inputType, List<Message> messages,
                                  Map<String, Object> variables, String agentStateKey,
                                  Map<String, Object> agentState) {
        this.scope = scope;
        this.inputType = inputType != null ? inputType : "USER_MESSAGE";
        this.messages = messages != null ? List.copyOf(messages) : List.of();
        this.variables = variables != null ? Map.copyOf(variables) : Map.of();
        Assert.hasText(agentStateKey, "agentStateKey must not be blank");
        this.agentStateKey = agentStateKey;
        this.agentState = agentState == null ? null : Map.copyOf(agentState);
    }

    public RuntimeIdentity getScope() { return scope; }
    public String getInputType() { return inputType; }
    public List<Message> getMessages() { return messages; }
    public Map<String, Object> getVariables() { return variables; }
    public String getAgentStateKey() { return agentStateKey; }
    public Optional<Map<String, Object>> getAgentState() { return Optional.ofNullable(agentState); }

    /** Atomic replace — used by adapters for checkpoint state. */
    public Map<String, Object> replaceAgentState(Map<String, Object> values) {
        Map<String, Object> next = Map.copyOf(values);
        this.agentState = next;
        return next;
    }

    public TrajectoryRuntime getTrajectoryRuntime() { return trajectoryRuntime; }

    public void setTrajectoryRuntime(TrajectoryRuntime trajectoryRuntime) {
        this.trajectoryRuntime = trajectoryRuntime;
    }

    private static String resolveAgentStateKey(RuntimeIdentity scope, Map<String, Object> variables) {
        Object explicit = variables != null ? variables.get(AGENT_STATE_KEY_VARIABLE) : null;
        if (!(explicit instanceof String t) || t.isBlank())
            explicit = variables != null ? variables.get(STATE_KEY_VARIABLE) : null;
        if (explicit instanceof String t && !t.isBlank()) return t;
        if (scope == null) throw new IllegalArgumentException("agentStateKey must be provided");
        return scope.taskId();
    }
}
