package com.huawei.ascend.middleware.model.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tenant-scoped LLM request envelope.
 *
 * <p>Authority: ADR-0121, schema at
 * {@code docs/contracts/model-invocation.v1.yaml}.
 *
 * @param tenantId       owning tenant (Rule R-C.c); MUST be non-blank.
 * @param modelId        stable id of the target model
 *                       (e.g. {@code "openai/gpt-4o-mini"}); MUST
 *                       be non-blank.
 * @param messages       ordered conversation turns; MUST be non-empty.
 * @param tools          tool descriptors the model may call; never
 *                       null, may be empty. Items reference
 *                       {@code SkillRef.skillKey()}.
 * @param parameters     provider-orthogonal hints (temperature,
 *                       top_p, max_tokens, response_format, ...).
 * @param hookContext    opaque context propagated through
 *                       {@code HookDispatcher} (tenantId, traceId,
 *                       runId, ...). May be empty.
 */
public record ModelInvocation(
        String tenantId,
        String modelId,
        List<Message> messages,
        List<String> tools,
        Map<String, Object> parameters,
        Map<String, Object> hookContext) {

    public ModelInvocation {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(hookContext, "hookContext");
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        parameters = Map.copyOf(parameters);
        hookContext = Map.copyOf(hookContext);
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank (Rule R-C.c)");
        }
        if (modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must be non-blank");
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must be non-empty");
        }
    }
}
