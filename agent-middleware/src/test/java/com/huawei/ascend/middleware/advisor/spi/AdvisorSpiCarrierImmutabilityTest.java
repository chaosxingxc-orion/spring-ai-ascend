package com.huawei.ascend.middleware.advisor.spi;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Carrier immutability + tenant scoping contract for the ChatAdvisor SPI
 * (ADR-0132).
 *
 * <p>{@link AdvisedRequest} and {@link AdvisedResponse} both:
 * <ul>
 *   <li>reject null and blank {@code tenantId} (Rule R-C.c);</li>
 *   <li>defensively copy {@code advisorContext} on construction so that
 *       mutating the input map after construction does not affect the
 *       record; and</li>
 *   <li>expose an unmodifiable {@code advisorContext} view that throws
 *       {@code UnsupportedOperationException} on mutation attempts.</li>
 * </ul>
 */
class AdvisorSpiCarrierImmutabilityTest {

    private static Map<String, Object> sampleRequestEnvelope() {
        return Map.of(
                "model", "model",
                "messages", List.of(Map.of("role", "user", "content", "hello")));
    }

    private static Map<String, Object> sampleResponseEnvelope() {
        return Map.of(
                "content", "answer",
                "finishReason", "stop",
                "provider", "openai");
    }

    @Test
    void advisedRequestRejectsNullFields() {
        assertThatThrownBy(() -> new AdvisedRequest(null, sampleRequestEnvelope(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdvisedRequest("tenant", null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdvisedRequest("tenant", sampleRequestEnvelope(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void advisedRequestRejectsBlankTenantId() {
        assertThatThrownBy(() -> new AdvisedRequest("", sampleRequestEnvelope(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new AdvisedRequest("   ", sampleRequestEnvelope(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void advisedRequestCopiesPayloadAndAdvisorContextAndIsUnmodifiable() {
        Map<String, Object> requestEnvelope = new HashMap<>(sampleRequestEnvelope());
        Map<String, Object> advisorContext = new HashMap<>(Map.of("redaction", "pii"));

        AdvisedRequest request = new AdvisedRequest("tenant", requestEnvelope, advisorContext);

        requestEnvelope.put("model", "mutated");
        advisorContext.put("redaction", "mutated");
        advisorContext.put("added", "after");

        assertThat(request.requestEnvelope()).containsEntry("model", "model");
        assertThat(request.advisorContext()).containsEntry("redaction", "pii");
        assertThat(request.advisorContext()).doesNotContainKey("added");
        assertThatThrownBy(() -> request.requestEnvelope().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.advisorContext().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void advisedResponseRejectsNullFields() {
        assertThatThrownBy(() -> new AdvisedResponse(null, sampleResponseEnvelope(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdvisedResponse("tenant", null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdvisedResponse("tenant", sampleResponseEnvelope(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void advisedResponseRejectsBlankTenantId() {
        assertThatThrownBy(() -> new AdvisedResponse("", sampleResponseEnvelope(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new AdvisedResponse("\t", sampleResponseEnvelope(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void advisedResponseCopiesPayloadAndAdvisorContextAndIsUnmodifiable() {
        Map<String, Object> responseEnvelope = new HashMap<>(sampleResponseEnvelope());
        Map<String, Object> advisorContext = new HashMap<>(Map.of("cost", 0.12));

        AdvisedResponse response = new AdvisedResponse("tenant", responseEnvelope, advisorContext);

        responseEnvelope.put("content", "mutated");
        advisorContext.put("cost", 9.99);
        advisorContext.put("added", "after");

        assertThat(response.responseEnvelope()).containsEntry("content", "answer");
        assertThat(response.advisorContext()).containsEntry("cost", 0.12);
        assertThat(response.advisorContext()).doesNotContainKey("added");
        assertThatThrownBy(() -> response.responseEnvelope().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> response.advisorContext().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void streamingAdvisorCanWrapStreamingChainWithSamePackageChunks() {
        AdvisedRequest request = new AdvisedRequest("tenant", sampleRequestEnvelope(), Map.of());
        AdvisedResponse response = new AdvisedResponse("tenant", sampleResponseEnvelope(), Map.of());
        StreamingAdvisorChain chain = ignored -> Stream.of(
                new AdvisedStreamChunk.ContentDelta("hel"),
                new AdvisedStreamChunk.ContentDelta("lo"),
                new AdvisedStreamChunk.Complete(response));
        StreamingChatAdvisor advisor = new StreamingChatAdvisor() {
            @Override
            public String advisorName() {
                return "test-streaming-advisor";
            }

            @Override
            public int order() {
                return 0;
            }

            @Override
            public Stream<AdvisedStreamChunk> aroundStream(AdvisedRequest advisedRequest, StreamingAdvisorChain next) {
                return next.proceed(advisedRequest);
            }
        };

        List<AdvisedStreamChunk> chunks = advisor.aroundStream(request, chain).toList();

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).isInstanceOf(AdvisedStreamChunk.ContentDelta.class);
        assertThat(chunks.get(2)).isInstanceOfSatisfying(AdvisedStreamChunk.Complete.class,
                complete -> assertThat(complete.finalResponse()).isSameAs(response));
    }
}
