package com.huawei.ascend.runtime.common;

/**
 * Framework-neutral, caller-visible run event — the element type of the reactive output
 * stream the engine produces. A concrete framework driver never exposes its native event type
 * past the {@code OutputConverter}; everything above the converter speaks {@code RunEvent}.
 *
 * <p>Minimal and text-first for v1 ({@code content} is plain text); richer/multimodal payloads
 * extend it later without changing the seam.
 */
public record RunEvent(int sequence, RunEventType kind, RunPhase phase, String content, String error) {

    public static RunEvent accepted() {
        return new RunEvent(0, RunEventType.ACCEPTED, RunPhase.PENDING, null, null);
    }

    public static RunEvent chunk(int sequence, String content) {
        return new RunEvent(sequence, RunEventType.CHUNK, RunPhase.RUNNING, content, null);
    }

    public static RunEvent completed(int sequence, String content) {
        return new RunEvent(sequence, RunEventType.COMPLETED, RunPhase.SUCCEEDED, content, null);
    }

    public static RunEvent failed(int sequence, String error) {
        return new RunEvent(sequence, RunEventType.FAILED, RunPhase.FAILED, null, error);
    }

    public boolean terminal() {
        return kind == RunEventType.COMPLETED || kind == RunEventType.FAILED;
    }
}
