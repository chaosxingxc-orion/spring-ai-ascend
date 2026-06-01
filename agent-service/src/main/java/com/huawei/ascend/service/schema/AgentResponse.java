package com.huawei.ascend.service.schema;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical run response, the standard object the access layer assembles from
 * the internal output stream before a protocol adapter renders it back to the
 * caller.
 *
 * <p>Aligned with the agentscope-runtime {@code AgentResponse} (session id +
 * output messages + status), carrying the {@link RunStatus} lifecycle view and
 * an optional {@link RunError} when the run did not succeed.
 *
 * @param sessionId conversation/session id; never blank.
 * @param taskId    the task/run id this response is for; never blank.
 * @param status    terminal-or-current run status; never {@code null}.
 * @param output    output messages produced by the run; never {@code null}.
 * @param error     failure detail when {@code status == FAILED}; else {@code null}.
 * @param metadata  response attributes (usage, timings); never {@code null}.
 */
public record AgentResponse(
        String sessionId,
        String taskId,
        RunStatus status,
        List<Message> output,
        RunError error,
        Map<String, Object> metadata) {

    public AgentResponse {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(status, "status");
        output = output == null ? List.of() : List.copyOf(output);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Builds a successful response carrying the given output messages. */
    public static AgentResponse completed(String sessionId, String taskId, List<Message> output) {
        return new AgentResponse(sessionId, taskId, RunStatus.COMPLETED, output, null, Map.of());
    }

    /** Builds a failed response carrying the given error. */
    public static AgentResponse failed(String sessionId, String taskId, RunError error) {
        return new AgentResponse(sessionId, taskId, RunStatus.FAILED, List.of(), error, Map.of());
    }

    /** Concatenated text of all output messages. */
    public String outputText() {
        StringBuilder sb = new StringBuilder();
        for (Message message : output) {
            sb.append(message.text());
        }
        return sb.toString();
    }

    /**
     * Structured failure detail, mirroring the agentscope-runtime {@code Error}
     * shape ({@code code} + {@code message}).
     */
    public record RunError(String code, String message) {
        public RunError {
            code = (code == null || code.isBlank()) ? "UNKNOWN" : code;
            message = message == null ? "" : message;
        }
    }
}
