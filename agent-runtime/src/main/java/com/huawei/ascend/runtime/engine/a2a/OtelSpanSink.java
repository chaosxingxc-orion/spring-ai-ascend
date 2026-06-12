package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Exports the neutral trajectory as OpenTelemetry spans. Span-pair kinds open a span on
 * {@code _START} and close it on {@code _END}, nesting under the parent span resolved from
 * our {@code parentSpanId}; point kinds ({@code REASONING}/{@code PROGRESS}/{@code ERROR})
 * become span events on the enclosing span ({@code ERROR} also sets span status). Every span
 * carries the mandatory {@code tenant.id}; model/tool spans carry {@code gen_ai.*}. OTel mints
 * its own trace/span ids, so our ids ride as attributes for cross-correlation with the JSONL
 * track. One instance per invocation; the single-threaded drain means no locking is needed.
 */
final class OtelSpanSink implements TrajectorySink {

    private static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("tenant.id");
    private static final AttributeKey<String> GEN_AI_OPERATION = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> GEN_AI_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<Long> GEN_AI_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> GEN_AI_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    private static final AttributeKey<String> GEN_AI_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> TRAJECTORY_SPAN_ID = AttributeKey.stringKey("trajectory.span_id");
    private static final AttributeKey<String> TRAJECTORY_TRACE_ID = AttributeKey.stringKey("trajectory.trace_id");

    private final Tracer tracer;
    private final Map<String, Span> openSpans = new HashMap<>();

    OtelSpanSink(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void accept(TrajectoryEvent event) {
        switch (event.kind()) {
            case RUN_START, MODEL_CALL_START, TOOL_CALL_START -> openSpan(event);
            case RUN_END, MODEL_CALL_END, TOOL_CALL_END -> closeSpan(event);
            case ERROR -> addPointEvent(event, true);
            case REASONING, PROGRESS -> addPointEvent(event, false);
        }
    }

    private void openSpan(TrajectoryEvent event) {
        SpanBuilder builder = tracer.spanBuilder(spanName(event))
                .setStartTimestamp(event.tsEpochMillis(), TimeUnit.MILLISECONDS);
        Span parent = event.parentSpanId() != null ? openSpans.get(event.parentSpanId()) : null;
        if (parent != null) {
            builder.setParent(Context.current().with(parent));
        } else {
            builder.setNoParent();
        }
        Span span = builder.startSpan();
        applyAttributes(span, event);
        if (event.spanId() != null) {
            openSpans.put(event.spanId(), span);
        } else {
            span.end(event.tsEpochMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void closeSpan(TrajectoryEvent event) {
        Span span = event.spanId() != null ? openSpans.remove(event.spanId()) : null;
        if (span == null) {
            return; // unbalanced end — nothing open to close
        }
        applyUsage(span, event);
        span.end(event.tsEpochMillis(), TimeUnit.MILLISECONDS);
    }

    private void addPointEvent(TrajectoryEvent event, boolean isError) {
        Span parent = event.parentSpanId() != null ? openSpans.get(event.parentSpanId()) : null;
        if (parent == null) {
            return;
        }
        parent.addEvent(String.valueOf(event.kind()).toLowerCase(Locale.ROOT));
        if (isError) {
            parent.setStatus(StatusCode.ERROR, errorMessage(event));
        }
    }

    private void applyAttributes(Span span, TrajectoryEvent event) {
        if (event.tenantId() != null) {
            span.setAttribute(TENANT_ID, event.tenantId());
        }
        if (event.traceId() != null) {
            span.setAttribute(TRAJECTORY_TRACE_ID, event.traceId());
        }
        if (event.spanId() != null) {
            span.setAttribute(TRAJECTORY_SPAN_ID, event.spanId());
        }
        switch (event.kind()) {
            case MODEL_CALL_START -> span.setAttribute(GEN_AI_OPERATION, "chat");
            case TOOL_CALL_START -> {
                span.setAttribute(GEN_AI_OPERATION, "execute_tool");
                if (event.name() != null) {
                    span.setAttribute(GEN_AI_TOOL_NAME, event.name());
                }
            }
            default -> { }
        }
        applyUsage(span, event);
    }

    private void applyUsage(Span span, TrajectoryEvent event) {
        TrajectoryEvent.Usage usage = event.usage();
        if (usage == null) {
            return;
        }
        if (usage.model() != null) {
            span.setAttribute(GEN_AI_MODEL, usage.model());
        }
        if (usage.inputTokens() != null) {
            span.setAttribute(GEN_AI_INPUT_TOKENS, usage.inputTokens().longValue());
        }
        if (usage.outputTokens() != null) {
            span.setAttribute(GEN_AI_OUTPUT_TOKENS, usage.outputTokens().longValue());
        }
    }

    @Override
    public void onClose() {
        // Defensive: end any span left open by an unbalanced stream so nothing leaks.
        openSpans.values().forEach(Span::end);
        openSpans.clear();
    }

    private static String spanName(TrajectoryEvent event) {
        return switch (event.kind()) {
            case RUN_START -> "agent.run";
            case MODEL_CALL_START -> "gen_ai.chat";
            case TOOL_CALL_START -> event.name() != null ? "execute_tool " + event.name() : "execute_tool";
            default -> String.valueOf(event.kind());
        };
    }

    private static String errorMessage(TrajectoryEvent event) {
        return event.error() != null && event.error().message() != null ? event.error().message() : "error";
    }
}
