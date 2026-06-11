package com.huawei.ascend.client;

import java.util.List;
import org.a2aproject.sdk.spec.StreamingEventKind;

/**
 * Aggregated outcome of one A2A send or stream.
 *
 * @param text   the user-visible answer text extracted from {@code events}
 *               via {@link A2aEvents#textFrom(List)} (accepted-ack messages
 *               excluded); empty when the agent produced no text
 * @param events every A2A event received, in arrival order — kept raw so
 *               callers can inspect task states, artifacts and metadata the
 *               text extraction does not surface
 * @param trace  trace correlation for the call
 */
public record A2aResponse(String text, List<StreamingEventKind> events, TraceCorrelation trace) {

    public A2aResponse {
        events = events == null ? List.of() : List.copyOf(events);
    }

    /**
     * True when this turn ended because the agent suspended the run waiting
     * on the caller (an A2A {@code input-required} / {@code auth-required}
     * state): {@link #text()} is the agent's prompt, and the caller is
     * expected to answer it in a follow-up send on the same session. False
     * for a run that actually finished.
     */
    public boolean awaitingInput() {
        return events.stream().anyMatch(A2aEvents::isAwaitingInput);
    }
}
