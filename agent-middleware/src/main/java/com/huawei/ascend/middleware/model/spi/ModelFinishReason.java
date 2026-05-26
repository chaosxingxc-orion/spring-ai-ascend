package com.huawei.ascend.middleware.model.spi;

import java.util.Arrays;
import java.util.Objects;

/**
 * Closed model-stop vocabulary used by model invocation and tool-loop gates.
 *
 * <p>Authority: ADR-0121, ADR-0134.
 */
public enum ModelFinishReason {
    STOP("stop"),
    LENGTH("length"),
    TOOL_CALLS("tool_calls"),
    CONTENT_FILTER("content_filter"),
    OTHER("other");

    private final String wireValue;

    ModelFinishReason(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ModelFinishReason fromWireValue(String finishReason) {
        Objects.requireNonNull(finishReason, "finishReason");
        return Arrays.stream(values())
                .filter(reason -> reason.wireValue.equals(finishReason))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "finishReason must be one of stop, length, tool_calls, content_filter, other"));
    }
}
