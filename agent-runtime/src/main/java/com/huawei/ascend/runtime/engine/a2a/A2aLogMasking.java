package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import java.util.regex.Pattern;

/**
 * Masks free text before it reaches an operator log line. The log collection chain is an
 * export channel exactly like the trajectory: error messages and framework payloads can
 * embed business data or credentials, so they go through the same
 * {@link TrajectoryMasking} primitive (and its truncation) instead of being printed raw.
 * See the package logging convention in {@code package-info}.
 */
final class A2aLogMasking {

    private static final Pattern KEY_PATTERN = Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN);
    private static final int TRUNCATE_CHARS = 256;

    private A2aLogMasking() {
    }

    static String mask(String text) {
        Object masked = TrajectoryMasking.mask(text, KEY_PATTERN, TRUNCATE_CHARS);
        return masked != null ? String.valueOf(masked) : null;
    }
}
