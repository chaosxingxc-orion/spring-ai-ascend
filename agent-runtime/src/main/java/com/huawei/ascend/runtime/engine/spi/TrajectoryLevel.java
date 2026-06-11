package com.huawei.ascend.runtime.engine.spi;

import java.util.Locale;

/**
 * Detail level for trajectory emission. OFF disables it entirely; SUMMARY emits
 * the mandatory core (run/tool/error) with truncated+masked payloads; FULL adds
 * the optional tier (model calls, reasoning, full payloads, masked but untruncated).
 */
public enum TrajectoryLevel {

    OFF,
    SUMMARY,
    FULL;

    public static TrajectoryLevel from(String value, TrajectoryLevel fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
