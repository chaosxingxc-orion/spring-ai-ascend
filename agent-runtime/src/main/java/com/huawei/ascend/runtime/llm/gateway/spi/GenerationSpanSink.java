package com.huawei.ascend.runtime.llm.gateway.spi;

/**
 * Receiver of GENERATION records for LLM invocations. The gateway emits the
 * GENERATION attribute set of every call to this seam. Default binding is the
 * dropping {@link NoopGenerationSpanSink}; deployments providing an OpenTelemetry
 * bean get the OTel bridge ({@code gateway.otel.OtelGenerationSpanSink}), and a
 * deployment-registered sink bean replaces either.
 */
public interface GenerationSpanSink {

    void emit(GenerationSpan span);

    /**
     * The GENERATION attribute set of one LLM invocation: the six observability
     * attributes plus the mandatory tenant attribution. Per-tenant cost rides this
     * record (and the spend log) — never Prometheus labels, whose cardinality rules
     * forbid a tenant tag.
     *
     * @param genAiSystem        upstream provider label ({@code gen_ai.system})
     * @param genAiRequestModel  model alias the caller requested ({@code gen_ai.request.model})
     * @param inputTokens        prompt tokens ({@code gen_ai.usage.input_tokens})
     * @param outputTokens       completion tokens ({@code gen_ai.usage.output_tokens})
     * @param costUsd            call cost ({@code langfuse.cost_usd}); null when the
     *                           alias has no pricing — the attribute is omitted, not zeroed
     * @param latencyMs          upstream latency ({@code langfuse.latency_ms})
     * @param tenantId           tenant attribution ({@code tenant.id}); never null/blank —
     *                           a GENERATION record without its tenant cannot be
     *                           constructed, so every sink receives an attributable span
     */
    record GenerationSpan(
            String genAiSystem,
            String genAiRequestModel,
            long inputTokens,
            long outputTokens,
            Double costUsd,
            long latencyMs,
            String tenantId) {

        public GenerationSpan {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be null or blank");
            }
        }
    }
}
