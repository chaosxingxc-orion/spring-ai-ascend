package com.huawei.ascend.runtime.engine.a2a;

import org.a2aproject.sdk.spec.Message;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class A2aAgentExecutor implements AgentExecutor {

    /**
     * Call-context state key under which the access layer publishes the
     * transport-authenticated tenant. It outranks the client-self-declared
     * params.tenant — a wire client must not be able to choose its tenant.
     */
    public static final String TENANT_STATE_KEY = "tenantId";

    private static final Logger LOG = LoggerFactory.getLogger(A2aAgentExecutor.class);

    /** Version of the structured-error payload carried on the failure DataPart/metadata. */
    private static final String ERROR_SCHEMA_VERSION = "1";

    private final AgentRuntimeHandler handler;

    public A2aAgentExecutor(AgentRuntimeHandler handler) {
        this.handler = handler;
    }

    @Override
    public void execute(RequestContext ctx, AgentEmitter emitter) {
        String taskId = ctx.getTaskId();
        if (handler == null) {
            LOG.warn("[A2A] no handler registered taskId={}", taskId);
            emitter.reject(failureMessage(emitter, "NO_HANDLER",
                    "no agent handler registered for this task", false));
            LOG.info("[A2A] task state=REJECTED taskId={}", taskId);
            return;
        }
        long startedNanos = System.nanoTime();
        String sessionId = ctx.getContextId();
        String agentId = handler.agentId();
        LOG.info("[A2A] execute start taskId={} sessionId={} agentId={}", taskId, sessionId, agentId);

        // ── (received) → SUBMITTED → WORKING ──
        emitter.submit();
        LOG.info("[A2A] task state=SUBMITTED taskId={}", taskId);
        emitter.startWork();
        LOG.info("[A2A] task state=WORKING taskId={}", taskId);

        // Per-task local state (this bean is a shared singleton — never hoist to a field).
        AtomicBoolean firstArtifact = new AtomicBoolean(true);
        String artifactId = taskId + "-response";

        // Everything after startWork must fail the task on error — context
        // construction throws on wire-controllable input (blank/null ids), and
        // an escape here would strand the task in WORKING forever.
        try {
            String inputText = extractText(ctx);
            LOG.info("[A2A] input parsed taskId={} textChars={}", taskId, inputText.length());
            AgentExecutionContext context = toExecutionContext(ctx, inputText);

            try (Stream<?> raw = executeAgent(context);
                 Stream<AgentExecutionResult> results = handler.resultAdapter().adapt(raw)) {

                AtomicBoolean terminalRouted = new AtomicBoolean(false);
                results.forEach(result -> {
                    LOG.info("[A2A] result taskId={} type={} outputChars={}",
                            taskId, result.type(),
                            result.outputContent() != null
                                    ? result.outputContent().length() : 0);
                    if (route(result, emitter, taskId, artifactId, firstArtifact)) {
                        terminalRouted.set(true);
                    }
                });
                if (!terminalRouted.get()) {
                    // The handler stream drained without a terminal result (e.g. the
                    // upstream runtime replied with no events). DefaultRequestHandler
                    // never forces a terminal state, so finalize here or the task
                    // stays WORKING forever and polling clients hang.
                    LOG.warn("[A2A] result stream ended without terminal result taskId={} — completing", taskId);
                    emitter.complete();
                }
            }
            LOG.info("[A2A] execute finish taskId={} durationMs={}",
                    taskId, (System.nanoTime() - startedNanos) / 1_000_000L);

        } catch (Exception e) {
            RuntimeErrorCode code = RuntimeErrorCode.classify(e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            LOG.error("[A2A] execute failed taskId={} code={} errorClass={} message={}",
                    taskId, code, e.getClass().getSimpleName(), e.getMessage(), e);
            emitter.fail(failureMessage(emitter, code.name(), detail, code.retryable()));
            LOG.info("[A2A] task state=FAILED taskId={}", taskId);
        }
    }

    private Stream<?> executeAgent(AgentExecutionContext context) {
        return handler.execute(context);
    }

    @Override
    public void cancel(RequestContext ctx, AgentEmitter emitter) {
        String taskId = ctx.getTaskId();
        LOG.info("[A2A] cancel requested taskId={}", taskId);
        try {
            emitter.cancel();
            LOG.info("[A2A] task state=CANCELED taskId={}", taskId);
        } catch (Exception e) {
            LOG.error("[A2A] cancel failed taskId={} message={}", taskId, e.getMessage(), e);
        }
    }

    /** Routes one result to the emitter; returns true when it put the task in a terminal state. */
    private boolean route(AgentExecutionResult result, AgentEmitter emitter, String taskId,
                          String artifactId, AtomicBoolean firstArtifact) {
        switch (result.type()) {
            case OUTPUT -> {
                String text = outputText(result);
                LOG.info("[A2A] output stream taskId={} textChars={}", taskId, text.length());
                // First chunk opens the artifact (append=false); later chunks append to the same
                // artifactId so the stream forms one growing artifact rather than many fragments.
                boolean append = !firstArtifact.getAndSet(false);
                emitter.addArtifact(List.<Part<?>>of(new TextPart(text)),
                        artifactId, "agent-response", null, append, false);
                // state stays WORKING — more output may follow; the terminal status closes the stream
                return false;
            }
            case COMPLETED -> {
                String text = outputText(result);
                if (!text.isBlank()) {
                    LOG.info("[A2A] complete with final output taskId={} textChars={}", taskId, text.length());
                    emitter.complete(emitter.newAgentMessage(List.<Part<?>>of(new TextPart(text)), null));
                } else {
                    emitter.complete();
                }
                LOG.info("[A2A] task state=COMPLETED taskId={}", taskId);
                return true;
            }
            case FAILED -> {
                String code = result.errorCode() == null ? "RUNTIME_ERROR" : result.errorCode();
                String msg = result.errorMessage() == null ? code : result.errorMessage();
                LOG.warn("[A2A] task state=FAILED taskId={} code={} message={}", taskId, code, msg);
                // Adapter-supplied codes pass through unchanged; retryability is unknown → conservative false.
                emitter.fail(failureMessage(emitter, code, result.errorMessage(), false));
                return true;
            }
            case INTERRUPTED -> {
                String prompt = result.prompt() == null ? "" : result.prompt();
                LOG.info("[A2A] task state=INPUT_REQUIRED taskId={} prompt={}", taskId, prompt);
                if (!prompt.isBlank()) {
                    emitter.sendMessage(prompt);
                }
                emitter.requiresInput();
                return true;
            }
        }
        return false;
    }

    private static String outputText(AgentExecutionResult result) {
        return result.outputContent() != null ? result.outputContent() : "";
    }

    /**
     * Builds an agent message carrying the failure both as human-readable text (a {@link TextPart})
     * and as a machine-readable {@link DataPart} ({@code code}, {@code message}, {@code retryable},
     * {@code schema_version}) so an A2A client can render the reason AND branch on it
     * programmatically. The same structure is mirrored on the message metadata for clients that read
     * {@code status.message.metadata} rather than the message parts.
     */
    private static Message failureMessage(AgentEmitter emitter, String code, String detail, boolean retryable) {
        String message = (detail == null || detail.isBlank()) ? code : detail;
        String text = (detail == null || detail.isBlank()) ? code : code + ": " + detail;
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("kind", "error");
        error.put("code", code);
        error.put("message", message);
        error.put("retryable", retryable);
        error.put("schema_version", ERROR_SCHEMA_VERSION);
        List<Part<?>> parts = List.of(new TextPart(text), new DataPart(error));
        return emitter.newAgentMessage(parts, Map.of("a2a.error", error));
    }

    private AgentExecutionContext toExecutionContext(RequestContext ctx, String text) {
        List<Message> messages = List.of(Message.builder().role(Message.Role.ROLE_USER).parts(java.util.List.of(new TextPart(text))).build());
        String sessionId = ctx.getContextId() != null ? ctx.getContextId() : ctx.getTaskId();
        return new AgentExecutionContext(
                new RuntimeIdentity(
                        metadata(ctx, "tenantId", "default"),
                        metadata(ctx, "userId", "system"),
                        sessionId,
                        ctx.getTaskId(),
                        metadata(ctx, "agentId", handler.agentId())),
                "USER_MESSAGE", messages,
                // In A2A every message/send of a conversation opens a NEW task within
                // the same contextId, so the framework conversation key must follow the
                // session — keying it by taskId would start a fresh framework
                // conversation each turn and checkpointer restore would never fire.
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, sessionId));
    }

    private static String extractText(RequestContext ctx) {
        return Messages.text(ctx.getMessage());
    }

    private static String metadata(RequestContext ctx, String key, String fallback) {
        if (TENANT_STATE_KEY.equals(key)) {
            Object transportTenant = ctx.getCallContext() == null
                    ? null : ctx.getCallContext().getState().get(TENANT_STATE_KEY);
            if (hasText(transportTenant)) {
                return String.valueOf(transportTenant);
            }
            if (hasText(ctx.getTenant())) {
                return ctx.getTenant();
            }
        }
        Map<String, Object> md = ctx.getMetadata();
        Object value = md == null ? null : md.get(key);
        return hasText(value) ? String.valueOf(value) : fallback;
    }

    private static boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }
}
