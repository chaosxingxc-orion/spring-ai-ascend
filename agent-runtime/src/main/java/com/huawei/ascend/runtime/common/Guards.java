package com.huawei.ascend.runtime.common;

import java.util.Objects;

/**
 * Shared argument guards for runtime value objects.
 *
 * <p>Every record and service in the runtime applies the same check to its
 * mandatory string identifiers (tenantId, sessionId, taskId, agentId, ...):
 * reject {@code null} and reject blank. Defining that check once here — instead
 * of copying a private helper into each value object — keeps the validation
 * contract identical across the runtime and lets it change in a single place.
 */
public final class Guards {

    private Guards() {
    }

    /**
     * Returns {@code value} unchanged when it is a non-null, non-blank string.
     * {@code name} labels the field in the thrown message so the caller can tell
     * which argument was rejected.
     *
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
