package com.huawei.ascend.runtime.engine.alpha;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.runtime.core.engine.DefaultAgentKernel;
import reactor.core.publisher.Flux;

import java.util.Iterator;
import java.util.List;

/**
 * Bridges the openjiuwen 2.0 kernel's {@link DefaultAgentKernel.LLMProvider} seam onto the
 * agent-core-java {@link Model} facade — the LLM surface 1.0 already depends on. This keeps
 * the Alpha engine on a single, stable LLM channel and avoids pulling spring-ai's
 * {@code ChatModel} into agent-runtime entirely (the version conflict between 1.0's pinned
 * spring-ai and 2.0's is unresolved by design).
 *
 * <p>The bridge holds an injected {@link Model} (constructed by the host with its
 * {@code ModelClientConfig}/{@code ModelRequestConfig}); tests substitute a {@code Model}
 * subclass that overrides {@code invoke}/{@code stream}, so no real HTTP is ever issued.
 *
 * <p>{@code stream} pulls the native {@code Iterator} on a bounded-elastic scheduler because
 * the agent-core-java streaming iterator performs blocking SSE reads; off-loading keeps the
 * blocking off reactor's event-loop threads.
 */
public final class AgentCoreJavaLlmProvider implements DefaultAgentKernel.LLMProvider {

    private final Model model;

    public AgentCoreJavaLlmProvider(Model model) {
        this.model = model;
    }

    @Override
    public String call(String prompt) {
        try {
            AssistantMessage message = model.invoke(
                    List.of(new UserMessage(prompt)),
                    null, null, null, null, null, null, null, null, null);
            return message.getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Flux<String> stream(String prompt) {
        return Flux.<String>create(sink -> {
            try {
                Iterator<AssistantMessageChunk> chunks = model.stream(
                        List.of(new UserMessage(prompt)),
                        null, null, null, null, null, null, null, null, null);
                // Break the while-loop on cancellation so the boundedElastic thread
                // is released promptly instead of draining the remaining stream.
                while (chunks.hasNext() && !sink.isCancelled()) {
                    String text = chunks.next().getContentAsString();
                    if (text != null && !text.isEmpty()) {
                        sink.next(text);
                    }
                }
                if (!sink.isCancelled()) {
                    sink.complete();
                }
            } catch (Exception e) {
                if (!sink.isCancelled()) {
                    sink.error(e);
                }
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
}
