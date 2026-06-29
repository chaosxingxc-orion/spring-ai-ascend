package com.huawei.ascend.runtime.engine.alpha;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bearing layer for the LLM bridge: verifies {@link AgentCoreJavaLlmProvider} faithfully hands
 * the prompt to the agent-core-java {@link Model} and returns its output. A {@code Model}
 * subclass captures prompts and replays canned replies, so no real HTTP runs. The end-to-end
 * prompt token is an IFF marker — strip the bridge's pass-through and the captured prompt no
 * longer contains it (mutation-RED).
 */
class AgentCoreJavaLlmProviderTest {

    private static final String BEARING_TOKEN = "BRIDGE_BEARING_TOKEN_7Q";

    @Test
    void callReturnsModelOutput() {
        AgentCoreJavaLlmProvider provider = new AgentCoreJavaLlmProvider(new CapturingTestModel("pong"));
        assertThat(provider.call("ping")).isEqualTo("pong");
    }

    @Test
    void callPassesPromptThroughToModel() {
        CapturingTestModel model = new CapturingTestModel("reply");
        AgentCoreJavaLlmProvider provider = new AgentCoreJavaLlmProvider(model);
        provider.call(BEARING_TOKEN);
        assertThat(model.capturedPrompts)
                .as("bridge must hand the prompt to Model.invoke verbatim; strip the pass-through and this fails")
                .anyMatch(p -> p.contains(BEARING_TOKEN));
    }

    @Test
    void streamFlattensModelChunks() {
        AgentCoreJavaLlmProvider provider = new AgentCoreJavaLlmProvider(new CapturingTestModel("chunk-reply"));
        StepVerifier.create(provider.stream("prompt"))
                .assertNext(chunk -> assertThat(chunk).isEqualTo("chunk-reply"))
                .verifyComplete();
    }

    @Test
    void streamPropagatesModelError() {
        AgentCoreJavaLlmProvider provider = new AgentCoreJavaLlmProvider(new FailingTestModel());
        StepVerifier.create(provider.stream("prompt"))
                .expectError()
                .verify();
    }

    /** Model subclass that captures the prompt text and returns a canned reply. */
    static final class CapturingTestModel extends Model {
        final List<String> capturedPrompts = new CopyOnWriteArrayList<>();
        private final String reply;

        CapturingTestModel(String reply) {
            super(ModelClientConfig.builder()
                            .clientProvider("openai").apiKey("mock").apiBase("http://localhost")
                            .verifySsl(false).build(),
                    ModelRequestConfig.builder().modelName("mock").build());
            this.reply = reply;
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                       String model, Integer maxTokens, String stop, BaseOutputParser outputParser,
                                       Float timeout, Map<String, Object> kwargs) {
            capture(messages);
            return new AssistantMessage(reply);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                                                      Float topP, String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) {
            capture(messages);
            return List.of(AssistantMessageChunk.builder().content(reply).finishReason("stop").build())
                    .iterator();
        }

        private void capture(Object messages) {
            if (messages instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof BaseMessage m) {
                        capturedPrompts.add(m.getContentAsString());
                    }
                }
            }
        }
    }

    /** Model subclass whose stream throws — verifies the bridge propagates errors, not swallows them. */
    static final class FailingTestModel extends Model {
        FailingTestModel() {
            super(ModelClientConfig.builder()
                            .clientProvider("openai").apiKey("mock").apiBase("http://localhost")
                            .verifySsl(false).build(),
                    ModelRequestConfig.builder().modelName("mock").build());
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                       String model, Integer maxTokens, String stop, BaseOutputParser outputParser,
                                       Float timeout, Map<String, Object> kwargs) {
            throw new RuntimeException("model invoke failed");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                                                      Float topP, String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) {
            throw new RuntimeException("model stream failed");
        }
    }
}
