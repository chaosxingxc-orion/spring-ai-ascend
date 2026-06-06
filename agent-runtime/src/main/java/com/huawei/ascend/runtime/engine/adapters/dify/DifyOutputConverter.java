package com.huawei.ascend.runtime.engine.adapters.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.engine.spi.OutputConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * Converts Dify's SSE response body into the neutral {@code RunEvent} stream. Lives entirely inside
 * the Dify adapter so the runtime core stays framework-neutral.
 *
 * <p>Dify streams {@code data: {json}} lines; this maps the event types onto neutral events: an
 * ACCEPTED event first, each {@code message}/{@code agent_message} chunk to a CHUNK event (and is
 * accumulated), {@code message_end} (chat) or {@code workflow_finished} (workflow) to a COMPLETED
 * event carrying the full answer, and {@code error} to a FAILED event. Other control events
 * (node_started, ping, …) are ignored.
 */
public final class DifyOutputConverter implements OutputConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Flow.Publisher<RunEvent> convert(Object frameworkStream) {
        List<RunEvent> events = new ArrayList<>();
        events.add(RunEvent.accepted());
        StringBuilder answer = new StringBuilder();
        int sequence = 0;
        boolean terminal = false;
        String sse = frameworkStream == null ? "" : frameworkStream.toString();
        for (String rawLine : sse.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (!line.startsWith("data:")) {
                continue;
            }
            String json = line.substring("data:".length()).strip();
            if (json.isEmpty() || "[DONE]".equals(json)) {
                continue;
            }
            JsonNode node;
            try {
                node = MAPPER.readTree(json);
            } catch (Exception ex) {
                continue;
            }
            String event = node.path("event").asText("");
            switch (event) {
                case "message", "agent_message" -> {
                    String chunk = node.path("answer").asText("");
                    if (!chunk.isEmpty()) {
                        answer.append(chunk);
                        events.add(RunEvent.chunk(++sequence, chunk));
                    }
                }
                case "message_end" -> {
                    events.add(RunEvent.completed(++sequence, answer.toString()));
                    terminal = true;
                }
                case "workflow_finished" -> {
                    JsonNode outputs = node.path("data").path("outputs");
                    String out = outputs.has("answer")
                            ? outputs.path("answer").asText("")
                            : (answer.length() > 0 ? answer.toString() : outputs.toString());
                    events.add(RunEvent.completed(++sequence, out));
                    terminal = true;
                }
                case "error" -> {
                    events.add(RunEvent.failed(++sequence,
                            node.path("message").asText("dify error")));
                    terminal = true;
                }
                default -> {
                    // node_started / node_finished / ping / tts_message / ... carry no answer text.
                }
            }
        }
        if (!terminal) {
            events.add(RunEvent.completed(++sequence, answer.toString()));
        }
        return syncPublisher(events);
    }

    /** Minimal synchronous JDK Flow publisher: delivers all items on request, then completes. */
    private static Flow.Publisher<RunEvent> syncPublisher(List<RunEvent> items) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private int idx = 0;
            private boolean cancelled = false;

            @Override
            public void request(long n) {
                while (n-- > 0 && idx < items.size() && !cancelled) {
                    subscriber.onNext(items.get(idx++));
                }
                if (idx >= items.size() && !cancelled) {
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        });
    }
}
