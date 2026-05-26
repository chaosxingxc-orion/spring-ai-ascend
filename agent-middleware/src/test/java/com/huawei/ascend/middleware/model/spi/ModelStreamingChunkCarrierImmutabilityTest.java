package com.huawei.ascend.middleware.model.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelStreamingChunkCarrierImmutabilityTest {

    @Test
    void contentDeltaRejectsNullDeltaText() {
        assertThatThrownBy(() -> new ModelResponseChunk.ContentDelta(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("deltaText");
    }

    @Test
    void contentDeltaAcceptsEmptyDeltaText() {
        ModelResponseChunk.ContentDelta chunk = new ModelResponseChunk.ContentDelta("");
        assertThat(chunk.deltaText()).isEmpty();
    }

    @Test
    void toolCallDeltaRejectsNullFields() {
        assertThatThrownBy(() -> new ModelResponseChunk.ToolCallDelta(null, "search", "{}"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("callId");
        assertThatThrownBy(() -> new ModelResponseChunk.ToolCallDelta("call", null, "{}"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("skillKey");
        assertThatThrownBy(() -> new ModelResponseChunk.ToolCallDelta("call", "search", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("argumentsDelta");
    }

    @Test
    void toolCallDeltaPreservesAllFields() {
        ModelResponseChunk.ToolCallDelta chunk =
                new ModelResponseChunk.ToolCallDelta("call-1", "search", "{\"q\":");
        assertThat(chunk.callId()).isEqualTo("call-1");
        assertThat(chunk.skillKey()).isEqualTo("search");
        assertThat(chunk.argumentsDelta()).isEqualTo("{\"q\":");
    }

    @Test
    void completeRejectsNullFinalResponse() {
        assertThatThrownBy(() -> new ModelResponseChunk.Complete(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("finalResponse");
    }

    @Test
    void completeCarriesAssembledModelResponse() {
        ModelResponse response = new ModelResponse(
                "hello",
                List.of(),
                ModelFinishReason.STOP,
                null,
                Map.of("provider", "openai"));
        ModelResponseChunk.Complete chunk = new ModelResponseChunk.Complete(response);
        assertThat(chunk.finalResponse()).isSameAs(response);
        assertThat(chunk.finalResponse().content()).isEqualTo("hello");
    }

    @Test
    void chunkSealedInterfacePermitsExactlyThreeVariants() {
        Class<?>[] permitted = ModelResponseChunk.class.getPermittedSubclasses();
        assertThat(permitted)
                .containsExactlyInAnyOrder(
                        ModelResponseChunk.ContentDelta.class,
                        ModelResponseChunk.ToolCallDelta.class,
                        ModelResponseChunk.Complete.class);
    }

    @Test
    void modelGatewayDefaultStreamThrowsUnsupportedOperationWithDesignOnlyMessage() {
        ModelGateway gateway = invocation -> new ModelResponse(
                "ignored", List.of(), ModelFinishReason.STOP, null, Map.of());
        ModelInvocation invocation = new ModelInvocation(
                "tenant",
                "model",
                List.of(new Message.UserMessage("hi")),
                List.of(),
                Map.of(),
                Map.of());

        assertThatThrownBy(() -> gateway.stream(invocation))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("design-only");
    }

    @Test
    void modelGatewayDefaultStreamRejectsNullInvocation() {
        ModelGateway gateway = invocation -> new ModelResponse(
                "ignored", List.of(), ModelFinishReason.STOP, null, Map.of());

        assertThatThrownBy(() -> gateway.stream(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("invocation");
    }

    @Test
    void overrideOfStreamReplacesDefaultBehavior() {
        ModelResponse finalResponse = new ModelResponse(
                "hello",
                List.of(),
                ModelFinishReason.STOP,
                null,
                Map.of());
        ModelGateway streaming = new ModelGateway() {
            @Override
            public ModelResponse invoke(ModelInvocation invocation) {
                return finalResponse;
            }
            @Override
            public Stream<ModelResponseChunk> stream(ModelInvocation invocation) {
                return Stream.of(
                        new ModelResponseChunk.ContentDelta("hel"),
                        new ModelResponseChunk.ContentDelta("lo"),
                        new ModelResponseChunk.Complete(finalResponse));
            }
        };
        ModelInvocation invocation = new ModelInvocation(
                "tenant",
                "model",
                List.of(new Message.UserMessage("hi")),
                List.of(),
                Map.of(),
                Map.of());

        List<ModelResponseChunk> chunks = streaming.stream(invocation).toList();

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).isInstanceOf(ModelResponseChunk.ContentDelta.class);
        assertThat(chunks.get(2)).isInstanceOfSatisfying(ModelResponseChunk.Complete.class,
                last -> assertThat(last.finalResponse()).isSameAs(finalResponse));
    }
}
