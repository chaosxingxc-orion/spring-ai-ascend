package com.huawei.ascend.runtime.engine;

import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Shared pull-based SSE wire decoder: turns a line stream into a stream of
 * {@link SseFrame}s (blank-line framing, {@code data:} accumulation with
 * single-leading-space stripping and newline joining, EOF flush, mid-stream
 * failures surfaced as a terminal failure frame instead of an exception so a
 * partially-consumed stream still yields its decoded prefix).
 *
 * <p>Dialect points are parameters, not subclasses: {@code captureEventName}
 * keeps the {@code event:} name on the frame (LangGraph-style named events),
 * and {@code emitDataLessNamedFrames} emits a named frame that carried no
 * {@code data:} line at all (LangGraph signals stream completion with a bare
 * {@code event: end} frame, which a WHATWG-strict parser would drop).
 * Sentinel filtering ({@code [DONE]} / {@code null} / blank data) and JSON
 * payload decoding stay with each caller — they are payload dialect, not wire
 * mechanics.
 */
public final class SseEventDecoder {

    private SseEventDecoder() {
    }

    /**
     * One decoded SSE frame. Exactly one of the shapes holds: a failure frame
     * ({@code failure != null}, always last), a data frame ({@code data != null},
     * the joined data lines), or a data-less named frame ({@code data == null},
     * only when emitDataLessNamedFrames is set). {@code name} is never null;
     * it is empty when no {@code event:} line was seen or names are not captured.
     */
    public record SseFrame(String name, String data, RuntimeException failure) {
        public static SseFrame event(String name, String data) {
            return new SseFrame(name == null ? "" : name, data, null);
        }

        public static SseFrame failure(RuntimeException failure) {
            return new SseFrame("", null, failure);
        }
    }

    public static Stream<SseFrame> frames(Stream<String> lines, boolean captureEventName,
            boolean emitDataLessNamedFrames) {
        Iterator<String> iterator = lines.iterator();
        Spliterator<SseFrame> spliterator =
                new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL) {
                    private final StringBuilder data = new StringBuilder();
                    private String eventName = "";
                    private boolean hasData;
                    private boolean terminated;

                    @Override
                    public boolean tryAdvance(Consumer<? super SseFrame> action) {
                        while (true) {
                            if (terminated) {
                                return false;
                            }
                            String line;
                            try {
                                if (!iterator.hasNext()) {
                                    terminated = true;
                                    SseFrame frame = flush();
                                    if (frame != null) {
                                        action.accept(frame);
                                        return true;
                                    }
                                    return false;
                                }
                                line = iterator.next();
                            } catch (RuntimeException ex) {
                                // A connection dropped mid-stream must surface as a frame the
                                // caller can map to its structured failure event, not as an
                                // exception that loses the already-decoded prefix.
                                terminated = true;
                                action.accept(SseFrame.failure(ex));
                                return true;
                            }
                            if (line.isBlank()) {
                                SseFrame frame = flush();
                                if (frame != null) {
                                    action.accept(frame);
                                    return true;
                                }
                            } else if (captureEventName && line.startsWith("event:")) {
                                eventName = line.substring("event:".length()).trim();
                            } else if (line.startsWith("data:")) {
                                appendData(line.substring("data:".length()));
                            }
                        }
                    }

                    private void appendData(String value) {
                        if (value.startsWith(" ")) {
                            value = value.substring(1);
                        }
                        if (hasData) {
                            data.append('\n');
                        }
                        data.append(value);
                        hasData = true;
                    }

                    private SseFrame flush() {
                        String name = eventName;
                        eventName = "";
                        if (!hasData) {
                            return emitDataLessNamedFrames && !name.isBlank() ? SseFrame.event(name, null) : null;
                        }
                        String eventData = data.toString();
                        data.setLength(0);
                        hasData = false;
                        return SseFrame.event(name, eventData);
                    }
                };
        return StreamSupport.stream(spliterator, false).onClose(lines::close);
    }

    /**
     * Unwraps the async-transport wrapper layers ({@link CompletionException},
     * {@link UncheckedIOException}) around an SSE failure so the surfaced message
     * names the root cause, not the plumbing.
     */
    public static String failureMessage(RuntimeException ex) {
        Throwable failure = ex;
        while ((failure instanceof CompletionException || failure instanceof UncheckedIOException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
