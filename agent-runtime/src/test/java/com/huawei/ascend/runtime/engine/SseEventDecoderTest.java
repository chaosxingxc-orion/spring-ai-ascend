package com.huawei.ascend.runtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.SseEventDecoder.SseFrame;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SseEventDecoderTest {

    @Test
    void namedFramesCarryEventNameAndDataLessNamedFrameIsEmittedWhenRequested() {
        List<SseFrame> frames = SseEventDecoder.frames(Stream.of(
                "event: metadata",
                "data: {\"run_id\":\"run-1\"}",
                "",
                "event: end",
                ""), true, true).toList();

        assertThat(frames).containsExactly(
                SseFrame.event("metadata", "{\"run_id\":\"run-1\"}"),
                SseFrame.event("end", null));
    }

    @Test
    void eventNameLinesAreIgnoredWhenNotCaptured() {
        List<SseFrame> frames = SseEventDecoder.frames(Stream.of(
                "event: end",
                "",
                "data: {\"status\":\"completed\"}"), false, false).toList();

        assertThat(frames).containsExactly(SseFrame.event("", "{\"status\":\"completed\"}"));
    }

    @Test
    void foldedDataLinesJoinWithNewline() {
        List<SseFrame> frames = SseEventDecoder.frames(Stream.of(
                "data: {\"a\":",
                "data: 1}",
                ""), false, false).toList();

        assertThat(frames).containsExactly(SseFrame.event("", "{\"a\":\n1}"));
    }

    @Test
    void unflushedFrameIsEmittedAtEndOfStream() {
        List<SseFrame> frames = SseEventDecoder.frames(
                Stream.of("data: {\"done\":true}"), false, false).toList();

        assertThat(frames).containsExactly(SseFrame.event("", "{\"done\":true}"));
    }

    @Test
    void dataWithoutLeadingSpaceIsAccepted() {
        List<SseFrame> frames = SseEventDecoder.frames(Stream.of("data:x", ""), false, false).toList();

        assertThat(frames).containsExactly(SseFrame.event("", "x"));
    }

    @Test
    void whitespaceOnlyEventNameIsTreatedAsUnnamed() {
        List<SseFrame> frames = SseEventDecoder.frames(Stream.of(
                "event:   ",
                "data: x",
                "",
                "event:   ",
                ""), true, true).toList();

        assertThat(frames).containsExactly(SseFrame.event("", "x"));
    }

    /** Sentinel payloads are dialect, not wire mechanics: they pass through untouched. */
    @Test
    void sentinelDataPassesThroughForCallerSideFiltering() {
        List<SseFrame> frames = SseEventDecoder.frames(Stream.of(
                "data: [DONE]",
                "",
                "data: null",
                ""), false, false).toList();

        assertThat(frames).containsExactly(
                SseFrame.event("", "[DONE]"),
                SseFrame.event("", "null"));
    }

    @Test
    void midStreamFailureSurfacesAsTerminalFailureFrame() {
        UncheckedIOException failure = new UncheckedIOException(new IOException("stream reset"));
        Stream<String> lines = Stream.concat(
                Stream.of("data: {\"a\":1}", ""),
                Stream.<String>generate(() -> {
                    throw failure;
                }).limit(1));

        List<SseFrame> frames = SseEventDecoder.frames(lines, false, false).toList();

        assertThat(frames).hasSize(2);
        assertThat(frames.get(0)).isEqualTo(SseFrame.event("", "{\"a\":1}"));
        assertThat(frames.get(1).failure()).isSameAs(failure);
    }

    @Test
    void eventNameResetsAtFrameBoundary() {
        List<SseFrame> frames = SseEventDecoder.frames(Stream.of(
                "event: values",
                "data: 1",
                "",
                "data: 2",
                ""), true, true).toList();

        assertThat(frames).containsExactly(
                SseFrame.event("values", "1"),
                SseFrame.event("", "2"));
    }

    @Test
    void failureMessageUnwrapsAsyncTransportWrappers() {
        assertThat(SseEventDecoder.failureMessage(new CompletionException(
                new UncheckedIOException(new IOException("connection refused")))))
                .isEqualTo("connection refused");
        assertThat(SseEventDecoder.failureMessage(new IllegalStateException((String) null)))
                .isEqualTo("IllegalStateException");
    }

    @Test
    void closingTheFrameStreamClosesTheLineStream() {
        AtomicBoolean closed = new AtomicBoolean();
        Stream<String> lines = Stream.of("data: x", "").onClose(() -> closed.set(true));

        try (Stream<SseFrame> frames = SseEventDecoder.frames(lines, false, false)) {
            frames.toList();
        }

        assertThat(closed).isTrue();
    }
}
