package com.huawei.ascend.runtime.engine.otel;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.ErrorCategory;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.ErrorInfo;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Usage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises the trajectory→OpenTelemetry span mapping against an in-memory exporter. */
class OtelSpanSinkTest {

    private static final AttributeKey<String> TENANT = AttributeKey.stringKey("tenant.id");
    private static final AttributeKey<String> GEN_AI_OP = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> GEN_AI_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<Long> GEN_AI_IN = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<String> GEN_AI_TOOL = AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> TRACE_ID = AttributeKey.stringKey("trajectory.trace_id");
    private static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    private static final AttributeKey<Double> GEN_AI_INPUT_COST_USD = AttributeKey.doubleKey("gen_ai.usage.input_cost_usd");
    private static final AttributeKey<Double> GEN_AI_OUTPUT_COST_USD = AttributeKey.doubleKey("gen_ai.usage.output_cost_usd");
    private static final AttributeKey<String> GEN_AI_ERROR_TYPE = AttributeKey.stringKey("gen_ai.error.type");
    private static final AttributeKey<List<String>> GEN_AI_FINISH_REASONS = AttributeKey.stringArrayKey("gen_ai.response.finish_reasons");

    /** Basic helper — no finishReason or error. Keeps existing call sites unchanged. */
    private static TrajectoryEvent ev(long seq, Kind kind, long ts, Long dur, String spanId, String parent,
            String name, Usage usage) {
        return new TrajectoryEvent(seq, kind, ts, dur, "task1", spanId, parent, "t1", "ctx1", "task1",
                "obj", name, null, null, usage, null, null, null, null, null, "2");
    }

    /** Extended helper that also accepts finishReason and error. */
    private static TrajectoryEvent evFull(long seq, Kind kind, long ts, Long dur, String spanId, String parent,
            String name, Usage usage, String finishReason, ErrorInfo error) {
        return new TrajectoryEvent(seq, kind, ts, dur, "task1", spanId, parent, "t1", "ctx1", "task1",
                "obj", name, null, null, usage, null, null, error, null, finishReason, "2");
    }

    @Test
    void mapsTrajectoryToNestedSpansWithGenAiAndTenantAttributes() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Tracer tracer = provider.get("test");
        OtelSpanSink sink = new OtelSpanSink(tracer);

        sink.onOpen("ctx1", "task1");
        sink.accept(ev(0, Kind.RUN_START, 1000, null, "run", null, null, null));
        sink.accept(ev(1, Kind.MODEL_CALL_START, 1001, null, "model", "run", null, null));
        sink.accept(ev(2, Kind.REASONING, 1002, null, "r1", "model", null, null));
        sink.accept(ev(3, Kind.MODEL_CALL_END, 1100, 99L, "model", "run", null, new Usage(10, 20, 5.0, "gemma", null, null, null)));
        sink.accept(ev(4, Kind.TOOL_CALL_START, 1101, null, "tool", "run", "search", null));
        sink.accept(ev(5, Kind.TOOL_CALL_END, 1150, 49L, "tool", "run", "search", null));
        sink.accept(ev(6, Kind.RUN_END, 1200, 200L, "run", null, null, null));
        sink.onClose();

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).extracting(SpanData::getName)
                .containsExactlyInAnyOrder("agent.run", "gen_ai.chat", "execute_tool search");

        SpanData run = byName(spans, "agent.run");
        SpanData model = byName(spans, "gen_ai.chat");
        SpanData tool = byName(spans, "execute_tool search");

        // Every span carries the mandatory tenant attribute + the correlation trace id.
        assertThat(spans).allSatisfy(s -> {
            assertThat(s.getAttributes().get(TENANT)).isEqualTo("t1");
            assertThat(s.getAttributes().get(TRACE_ID)).isEqualTo("task1");
        });

        // Run is the root; model and tool nest under it.
        assertThat(run.getParentSpanContext().isValid()).isFalse();
        assertThat(model.getParentSpanContext().getSpanId()).isEqualTo(run.getSpanContext().getSpanId());
        assertThat(tool.getParentSpanContext().getSpanId()).isEqualTo(run.getSpanContext().getSpanId());

        // gen_ai.* semantics on the model + tool spans.
        assertThat(model.getAttributes().get(GEN_AI_OP)).isEqualTo("chat");
        assertThat(model.getAttributes().get(GEN_AI_MODEL)).isEqualTo("gemma");
        assertThat(model.getAttributes().get(GEN_AI_IN)).isEqualTo(10L);
        assertThat(tool.getAttributes().get(GEN_AI_OP)).isEqualTo("execute_tool");
        assertThat(tool.getAttributes().get(GEN_AI_TOOL)).isEqualTo("search");

        // The reasoning point event rides as a span event on the model span.
        assertThat(model.getEvents()).extracting(e -> e.getName()).contains("reasoning");
    }

    @Test
    void modelCallEnd_withProviderAndCosts_emitsGenAiSystemAndCostAttributes() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        Tracer tracer = tracerFor(exporter);
        OtelSpanSink sink = new OtelSpanSink(tracer);

        Usage usage = new Usage(5, 10, 1.0, "gpt-4o", "openai", 0.001, 0.002);
        sink.onOpen("ctx1", "task1");
        sink.accept(ev(0, Kind.MODEL_CALL_START, 1000, null, "m1", null, null, null));
        sink.accept(evFull(1, Kind.MODEL_CALL_END, 1100, 100L, "m1", null, null, usage, null, null));
        sink.onClose();

        SpanData model = byName(exporter.getFinishedSpanItems(), "gen_ai.chat");
        assertThat(model.getAttributes().get(GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(model.getAttributes().get(GEN_AI_INPUT_COST_USD)).isEqualTo(0.001);
        assertThat(model.getAttributes().get(GEN_AI_OUTPUT_COST_USD)).isEqualTo(0.002);
    }

    @Test
    void modelCallEnd_withFinishReason_emitsFinishReasonsArray() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        Tracer tracer = tracerFor(exporter);
        OtelSpanSink sink = new OtelSpanSink(tracer);

        Usage usage = new Usage(5, 10, 1.0, "gpt-4o", null, null, null);
        sink.onOpen("ctx1", "task1");
        sink.accept(ev(0, Kind.MODEL_CALL_START, 1000, null, "m1", null, null, null));
        sink.accept(evFull(1, Kind.MODEL_CALL_END, 1100, 100L, "m1", null, null, usage, "stop", null));
        sink.onClose();

        SpanData model = byName(exporter.getFinishedSpanItems(), "gen_ai.chat");
        assertThat(model.getAttributes().get(GEN_AI_FINISH_REASONS)).containsExactly("stop");
    }

    @Test
    void errorEvent_withNonUnknownCategory_emitsGenAiErrorTypeOnEnclosingSpan() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        Tracer tracer = tracerFor(exporter);
        OtelSpanSink sink = new OtelSpanSink(tracer);

        ErrorInfo error = new ErrorInfo("429", "rate limit exceeded", ErrorCategory.RATE_LIMITED);
        sink.onOpen("ctx1", "task1");
        // Open the run span so the ERROR event has a parent to attach to.
        sink.accept(ev(0, Kind.RUN_START, 1000, null, "run", null, null, null));
        // ERROR event points at run as its parent.
        sink.accept(evFull(1, Kind.ERROR, 1050, null, "err1", "run", null, null, null, error));
        sink.accept(ev(2, Kind.RUN_END, 1200, 200L, "run", null, null, null));
        sink.onClose();

        SpanData run = byName(exporter.getFinishedSpanItems(), "agent.run");
        assertThat(run.getAttributes().get(GEN_AI_ERROR_TYPE)).isEqualTo("rate_limited");
    }

    @Test
    void modelEvent_withNullProviderCostAndFinishReason_attributesAbsent() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        Tracer tracer = tracerFor(exporter);
        OtelSpanSink sink = new OtelSpanSink(tracer);

        // Usage has tokens but no provider/costs; no finishReason.
        Usage usage = new Usage(3, 7, 1.0, "llama3", null, null, null);
        sink.onOpen("ctx1", "task1");
        sink.accept(ev(0, Kind.MODEL_CALL_START, 1000, null, "m1", null, null, null));
        sink.accept(evFull(1, Kind.MODEL_CALL_END, 1100, 100L, "m1", null, null, usage, null, null));
        sink.onClose();

        SpanData model = byName(exporter.getFinishedSpanItems(), "gen_ai.chat");
        assertThat(model.getAttributes().get(GEN_AI_SYSTEM)).isNull();
        assertThat(model.getAttributes().get(GEN_AI_INPUT_COST_USD)).isNull();
        assertThat(model.getAttributes().get(GEN_AI_OUTPUT_COST_USD)).isNull();
        assertThat(model.getAttributes().get(GEN_AI_FINISH_REASONS)).isNull();
    }

    @Test
    void modelCallFirstToken_underOpenParent_addsFirstTokenEventOnParent() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        Tracer tracer = tracerFor(exporter);
        OtelSpanSink sink = new OtelSpanSink(tracer);

        sink.onOpen("ctx1", "task1");
        sink.accept(ev(0, Kind.RUN_START, 1000, null, "run", null, null, null));
        sink.accept(ev(1, Kind.MODEL_CALL_FIRST_TOKEN, 1050, null, "ft1", "run", null, null));
        sink.accept(ev(2, Kind.RUN_END, 1200, 200L, "run", null, null, null));
        sink.onClose();

        SpanData run = byName(exporter.getFinishedSpanItems(), "agent.run");
        assertThat(run.getEvents()).extracting(e -> e.getName()).contains("gen_ai.first_token");
    }

    @Test
    void modelCallFirstToken_noOpenParent_noThrow() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        Tracer tracer = tracerFor(exporter);
        OtelSpanSink sink = new OtelSpanSink(tracer);

        sink.onOpen("ctx1", "task1");
        // parentSpanId is null — no open parent span — must be a no-op, not throw.
        sink.accept(ev(0, Kind.MODEL_CALL_FIRST_TOKEN, 1000, null, "ft1", null, null, null));
        sink.onClose();

        // No spans were exported (no open/close), just verify no exception was thrown.
        assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }

    private static SdkTracerProvider buildProvider(InMemorySpanExporter exporter) {
        return SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
    }

    private static Tracer tracerFor(InMemorySpanExporter exporter) {
        return buildProvider(exporter).get("test");
    }

    private static SpanData byName(List<SpanData> spans, String name) {
        return spans.stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }
}
