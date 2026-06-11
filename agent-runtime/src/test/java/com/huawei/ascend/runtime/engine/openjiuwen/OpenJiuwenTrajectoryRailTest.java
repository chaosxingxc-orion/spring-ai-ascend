package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.spi.QueueTrajectoryChannel;
import com.huawei.ascend.runtime.engine.spi.StampingTrajectoryEmitter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryDraft;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.huawei.ascend.runtime.engine.spi.TrajectoryLevel;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OpenJiuwenTrajectoryRailTest {

    private final List<TrajectoryDraft> drafts = new ArrayList<>();
    private final TrajectoryEmitter emitter = drafts::add;
    private final OpenJiuwenTrajectoryRail rail = new OpenJiuwenTrajectoryRail(emitter);

    @Test
    void beforeModelCallEmitsScalarCounts() {
        AgentCallbackContext context = AgentCallbackContext.builder()
                .inputs(ModelCallInputs.builder().messages(List.of("a", "b")).tools(List.of()).build())
                .build();
        rail.beforeModelCall(context);

        assertThat(drafts).hasSize(1);
        TrajectoryDraft draft = drafts.get(0);
        assertThat(draft.kind()).isEqualTo(Kind.MODEL_CALL_START);
        assertThat(draft.args()).isEqualTo(Map.of("messages", 2, "tools", 0));
    }

    @Test
    void afterModelCallWithoutAssistantMessageDegradesGracefully() {
        AgentCallbackContext context = AgentCallbackContext.builder()
                .inputs(ModelCallInputs.builder().messages(List.of("a")).response("plain").build())
                .build();
        rail.afterModelCall(context);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).kind()).isEqualTo(Kind.MODEL_CALL_END);
        assertThat(drafts.get(0).usage()).isNull();
    }

    @Test
    void toolCallStartAndEndCarryNameAndStructuredPayload() {
        rail.beforeToolCall(AgentCallbackContext.builder()
                .inputs(ToolCallInputs.builder().toolName("search").toolArgs(Map.of("q", "hi")).build())
                .build());
        rail.afterToolCall(AgentCallbackContext.builder()
                .inputs(ToolCallInputs.builder().toolName("search").toolResult("done").build())
                .build());

        assertThat(drafts).extracting(TrajectoryDraft::kind)
                .containsExactly(Kind.TOOL_CALL_START, Kind.TOOL_CALL_END);
        assertThat(drafts.get(0).name()).isEqualTo("search");
        // Structured args pass through as a Map so the runtime's key-based masking can redact secrets
        // inside them; pre-stringifying here would hide secret-named keys from the masker.
        assertThat(drafts.get(0).args()).isEqualTo(Map.of("q", "hi"));
        assertThat(drafts.get(1).result()).isEqualTo("done");
    }

    @Test
    void opaqueToolPayloadIsStringifiedForFrameworkAgnosticSerialization() {
        Object opaque = new Object() {
            @Override
            public String toString() {
                return "opaque-tool-object";
            }
        };
        rail.beforeToolCall(AgentCallbackContext.builder()
                .inputs(ToolCallInputs.builder().toolName("search").toolArgs(opaque).build())
                .build());

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).args()).isEqualTo("opaque-tool-object");
    }

    /** End-to-end guard for the masking bypass: a secret-named tool-arg key is redacted northbound. */
    @Test
    void toolArgsAreMaskedWhenStampedThroughTheEmitter() {
        QueueTrajectoryChannel channel = new QueueTrajectoryChannel();
        TrajectorySettings settings = new TrajectorySettings(
                TrajectoryLevel.SUMMARY, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        TrajectoryEmitter stamping = new StampingTrajectoryEmitter(
                channel, new RuntimeIdentity("t", "u", "s", "task", "a"), settings,
                EnumSet.of(Kind.TOOL_CALL_START, Kind.TOOL_CALL_END));
        OpenJiuwenTrajectoryRail maskingRail = new OpenJiuwenTrajectoryRail(stamping);

        maskingRail.beforeToolCall(AgentCallbackContext.builder()
                .inputs(ToolCallInputs.builder().toolName("search")
                        .toolArgs(Map.of("q", "hi", "apiKey", "secret")).build())
                .build());
        channel.close();

        List<TrajectoryEvent> events = channel.drain().toList();
        assertThat(events).hasSize(1);
        Map<?, ?> args = (Map<?, ?>) events.get(0).args();
        assertThat(args.get("apiKey")).isEqualTo("***");
        assertThat(args.get("q")).isEqualTo("hi");
    }

    @Test
    void modelExceptionEmitsRetryableError() {
        AgentCallbackContext context = AgentCallbackContext.builder()
                .exception(new RuntimeException("boom"))
                .retryAttempt(2)
                .build();
        rail.onModelException(context);

        assertThat(drafts).hasSize(1);
        TrajectoryDraft draft = drafts.get(0);
        assertThat(draft.kind()).isEqualTo(Kind.ERROR);
        assertThat(draft.error().code()).isEqualTo("OPENJIUWEN_MODEL_ERROR");
        assertThat(draft.error().message()).contains("boom");
        assertThat(draft.attempt()).isEqualTo(2);
        assertThat(draft.retryable()).isTrue();
    }

    @Test
    void toolExceptionCarriesToolNameAndCode() {
        AgentCallbackContext context = AgentCallbackContext.builder()
                .inputs(ToolCallInputs.builder().toolName("search").build())
                .exception(new IllegalStateException("nope"))
                .build();
        rail.onToolException(context);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).kind()).isEqualTo(Kind.ERROR);
        assertThat(drafts.get(0).name()).isEqualTo("search");
        assertThat(drafts.get(0).error().code()).isEqualTo("OPENJIUWEN_TOOL_ERROR");
    }
}
