package com.huawei.ascend.runtime.engine.otel;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
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

    private static TrajectoryEvent ev(long seq, Kind kind, long ts, Long dur, String spanId, String parent,
            String name, Usage usage) {
        return new TrajectoryEvent(seq, kind, ts, dur, "task1", spanId, parent, "t1", "ctx1", "task1",
                "obj", name, null, null, usage, null, null, null, null, "2");
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
        sink.accept(ev(3, Kind.MODEL_CALL_END, 1100, 99L, "model", "run", null, new Usage(10, 20, 5.0, "gemma")));
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

    private static SpanData byName(List<SpanData> spans, String name) {
        return spans.stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }
}
