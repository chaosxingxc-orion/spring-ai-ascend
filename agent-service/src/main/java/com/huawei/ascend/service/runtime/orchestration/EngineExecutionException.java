package com.huawei.ascend.service.runtime.orchestration;

/**
 * Raised by the orchestrator when an EnginePort FAILED terminal event arrives without an in-JVM
 * outcome handle to rethrow (e.g. reconstructed after crossing a mock wire). Carries the
 * error-class token from the event.
 */
public final class EngineExecutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String errorClass;

    public EngineExecutionException(String errorClass, String message) {
        super(errorClass + ": " + message);
        this.errorClass = errorClass;
    }

    public String errorClass() {
        return errorClass;
    }
}
