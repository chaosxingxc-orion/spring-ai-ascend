package com.huawei.ascend.runtime.engine;

/**
 * A single outcome the engine reports for a running handler, carried through the
 * engine's one outbound seam ({@link TaskControlClient}). Replaces the former
 * per-outcome event class hierarchy: {@link #kind} selects the outcome and only
 * the fields relevant to that kind are populated.
 *
 * <p>Use the static factories rather than the canonical constructor — they make
 * the kind/field pairing explicit:
 * <ul>
 *   <li>{@link #started} — lifecycle start (no payload)</li>
 *   <li>{@link #output} / {@link #completed} — carry an {@link EngineOutput}</li>
 *   <li>{@link #failed} — carries {@code errorCode} + {@code errorMessage}</li>
 *   <li>{@link #interrupted} — carries {@code interruptType} + {@code prompt}</li>
 *   <li>{@link #cancelled} — carries {@code reason}</li>
 * </ul>
 */
public record EngineEvent(
        EngineEventKind kind,
        EngineExecutionScope scope,
        EngineOutput output,
        String errorCode,
        String errorMessage,
        InterruptType interruptType,
        String prompt,
        String reason) {

    public static EngineEvent started(EngineExecutionScope scope) {
        return new EngineEvent(EngineEventKind.STARTED, scope, null, null, null, null, null, null);
    }

    public static EngineEvent output(EngineExecutionScope scope, EngineOutput output) {
        return new EngineEvent(EngineEventKind.OUTPUT, scope, output, null, null, null, null, null);
    }

    public static EngineEvent completed(EngineExecutionScope scope, EngineOutput output) {
        return new EngineEvent(EngineEventKind.COMPLETED, scope, output, null, null, null, null, null);
    }

    public static EngineEvent failed(EngineExecutionScope scope, String errorCode, String errorMessage) {
        return new EngineEvent(EngineEventKind.FAILED, scope, null, errorCode, errorMessage, null, null, null);
    }

    public static EngineEvent interrupted(EngineExecutionScope scope, InterruptType interruptType, String prompt) {
        return new EngineEvent(EngineEventKind.INTERRUPTED, scope, null, null, null, interruptType, prompt, null);
    }

    public static EngineEvent cancelled(EngineExecutionScope scope, String reason) {
        return new EngineEvent(EngineEventKind.CANCELLED, scope, null, null, null, null, null, reason);
    }
}
