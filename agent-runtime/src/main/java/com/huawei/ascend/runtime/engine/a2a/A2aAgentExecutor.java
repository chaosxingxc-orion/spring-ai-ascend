package com.huawei.ascend.runtime.engine.a2a;

import org.a2aproject.sdk.spec.Message;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class A2aAgentExecutor implements AgentExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(A2aAgentExecutor.class);

    private final AgentRuntimeHandler handler;

    public A2aAgentExecutor(AgentRuntimeHandler handler) {
        this.handler = handler;
    }

    @Override
    public void execute(RequestContext ctx, AgentEmitter emitter) {
        String taskId = ctx.getTaskId();
        if (handler == null) {
            LOG.warn("[A2A] no handler registered taskId={}", taskId);
            emitter.fail();
            return;
        }
        long startedNanos = System.nanoTime();
        String sessionId = ctx.getContextId();
        String agentId = handler.agentId();
        LOG.info("[A2A] execute start taskId={} sessionId={} agentId={}", taskId, sessionId, agentId);

        // ── SUBMITTED → WORKING ──
        emitter.startWork();
        LOG.info("[A2A] task state=WORKING taskId={}", taskId);

        String inputText = extractText(ctx);
        LOG.info("[A2A] input parsed taskId={} textChars={}", taskId, inputText.length());
        AgentExecutionContext context = toExecutionContext(ctx);

        try (Stream<?> raw = executeAgent(context);
             Stream<AgentExecutionResult> results = handler.resultAdapter().adapt(raw)) {

            results.forEach(result -> {
                LOG.info("[A2A] result taskId={} type={} outputChars={}",
                        taskId, result.type(),
                        result.outputContent() != null
                                ? result.outputContent().length() : 0);
                route(result, emitter, taskId);
            });
            LOG.info("[A2A] execute finish taskId={} durationMs={}",
                    taskId, (System.nanoTime() - startedNanos) / 1_000_000L);

        } catch (Exception e) {
            LOG.error("[A2A] execute failed taskId={} errorClass={} message={}",
                    taskId, e.getClass().getSimpleName(), e.getMessage(), e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            emitter.fail(failureMessage(emitter, "RUNTIME_ERROR", detail));
            LOG.info("[A2A] task state=FAILED taskId={}", taskId);
        }
    }

    private Stream<?> executeAgent(AgentExecutionContext context) {
        return handler.execute(context);
    }

    @Override
    public void cancel(RequestContext ctx, AgentEmitter emitter) {
        LOG.info("[A2A] cancel requested taskId={}", ctx.getTaskId());
        emitter.cancel();
        LOG.info("[A2A] task state=CANCELED taskId={}", ctx.getTaskId());
    }

    private void route(AgentExecutionResult result, AgentEmitter emitter, String taskId) {
        switch (result.type()) {
            case OUTPUT -> {
                String text = outputText(result);
                LOG.info("[A2A] output stream taskId={} textChars={}", taskId, text.length());
                emitter.addArtifact(List.<Part<?>>of(new TextPart(text)));
                // state stays WORKING — more output may follow
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
            }
            case FAILED -> {
                String code = result.errorCode() == null ? "RUNTIME_ERROR" : result.errorCode();
                String msg = result.errorMessage() == null ? code : result.errorMessage();
                LOG.warn("[A2A] task state=FAILED taskId={} code={} message={}", taskId, code, msg);
                emitter.fail(failureMessage(emitter, code, result.errorMessage()));
            }
            case INTERRUPTED -> {
                String prompt = result.prompt() == null ? "" : result.prompt();
                LOG.info("[A2A] task state=INPUT_REQUIRED taskId={} prompt={}", taskId, prompt);
                if (!prompt.isBlank()) {
                    emitter.sendMessage(prompt);
                }
                emitter.requiresInput();
            }
        }
    }

    private static String outputText(AgentExecutionResult result) {
        return result.outputContent() != null ? result.outputContent() : "";
    }

    /**
     * Builds an agent message carrying the failure reason so the A2A client sees
     * why the task failed instead of a bare FAILED status with no detail.
     */
    private static Message failureMessage(AgentEmitter emitter, String code, String detail) {
        String text = (detail == null || detail.isBlank()) ? code : code + ": " + detail;
        return emitter.newAgentMessage(List.<Part<?>>of(new TextPart(text)), null);
    }

    private AgentExecutionContext toExecutionContext(RequestContext ctx) {
        String text = extractText(ctx);
        List<Message> messages = List.of(Message.builder().role(Message.Role.ROLE_USER).parts(java.util.List.of(new TextPart(text))).build());
        return new AgentExecutionContext(
                new RuntimeIdentity(
                        metadata(ctx, "tenantId", "default"),
                        metadata(ctx, "userId", "system"),
                        ctx.getContextId() != null ? ctx.getContextId() : ctx.getTaskId(),
                        ctx.getTaskId(),
                        metadata(ctx, "agentId", handler.agentId())),
                "USER_MESSAGE", messages, Map.of());
    }

    private static String extractText(RequestContext ctx) {
        if (ctx.getMessage() == null || ctx.getMessage().parts() == null) return "";
        return ctx.getMessage().parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static String metadata(RequestContext ctx, String key, String fallback) {
        if ("tenantId".equals(key) && hasText(ctx.getTenant())) {
            return ctx.getTenant();
        }
        Map<String, Object> md = ctx.getMetadata();
        Object value = md == null ? null : md.get(key);
        return hasText(value) ? String.valueOf(value) : fallback;
    }

    private static boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }
}
