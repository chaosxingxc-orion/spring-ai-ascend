package com.huawei.ascend.runtime.engine.spi;

import java.util.regex.Pattern;

/**
 * Resolved per-invocation trajectory settings handed to a {@link TrajectorySource}.
 * The runtime computes these from global configuration plus any per-request override
 * before opening a channel, so the adapter base never reads configuration itself.
 */
public record TrajectorySettings(TrajectoryLevel level, Pattern maskKeyPattern, int truncateChars) {

    public static TrajectorySettings off() {
        return new TrajectorySettings(TrajectoryLevel.OFF, null, 0);
    }
}
