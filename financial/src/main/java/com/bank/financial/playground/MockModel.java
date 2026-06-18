package com.bank.financial.playground;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A keyless mock model for the playground — returns canned output instead of
 * calling a real LLM, so a developer can exercise the wiring (compliance →
 * tools → approval → trace) with no API key. Injected via {@code agent.setLlm}.
 *
 * <p>Two modes:
 * <ul>
 *   <li>plain — always returns a final answer (one-turn);</li>
 *   <li><b>scripted</b> — on the first model call it emits a single tool call to
 *       {@code scriptTool} (used by {@code --demo} to trigger a sensitive tool
 *       and show the human-approval pause/resume); once that tool has run it
 *       returns the final answer.</li>
 * </ul>
 */
public final class MockModel extends Model {

    private final String reply;
    private final String scriptTool;     // nullable
    private final String scriptArgsJson; // nullable

    public MockModel(String reply) {
        this(reply, null, null);
    }

    public MockModel(String reply, String scriptTool, String scriptArgsJson) {
        super(ModelClientConfig.builder()
                        .clientProvider("openai").apiKey("mock").apiBase("http://localhost")
                        .verifySsl(false).build(),
                ModelRequestConfig.builder().modelName("mock").build());
        this.reply = reply;
        this.scriptTool = scriptTool;
        this.scriptArgsJson = scriptArgsJson;
    }

    @Override
    public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
            String model, Integer maxTokens, String stop, BaseOutputParser outputParser,
            Float timeout, Map<String, Object> kwargs) {
        if (scriptTool != null && !toolAlreadyRun(messages)) {
            ToolCall call = ToolCall.builder()
                    .id("mock-call-1").type("function")
                    .name(scriptTool).arguments(scriptArgsJson == null ? "{}" : scriptArgsJson)
                    .index(0).build();
            return AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
        }
        return new AssistantMessage(reply);
    }

    @Override
    public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
            Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser,
            Float timeout, Map<String, Object> kwargs) {
        return List.of(AssistantMessageChunk.builder().content(reply).finishReason("stop").build())
                .iterator();
    }

    /** Heuristic: after a tool runs, the message list has grown past system+user. */
    private static boolean toolAlreadyRun(Object messages) {
        return messages instanceof List<?> list && list.size() > 2;
    }
}
