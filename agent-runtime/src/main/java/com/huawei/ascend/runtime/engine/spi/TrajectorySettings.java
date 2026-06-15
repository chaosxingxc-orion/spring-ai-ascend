package com.huawei.ascend.runtime.engine.spi;

import java.util.regex.Pattern;

/**
 * Resolved per-invocation trajectory settings handed to a {@link TrajectorySource}.
 * The runtime computes these from global configuration plus any per-request override
 * before opening the trajectory, so the adapter base never reads configuration itself.
 * When enabled, every supported kind is emitted with masked and truncated payloads.
 */
public record TrajectorySettings(boolean enabled, Pattern maskKeyPattern, int truncateChars) {

    public static TrajectorySettings off() {
        return new TrajectorySettings(false, null, 0);
    }
}
