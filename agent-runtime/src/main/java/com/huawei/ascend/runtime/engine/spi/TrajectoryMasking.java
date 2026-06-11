package com.huawei.ascend.runtime.engine.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Redacts sensitive map keys and (at SUMMARY) truncates long string leaves before a
 * trajectory payload leaves the process. Applied uniformly in the runtime so every
 * framework adapter gets identical redaction without implementing it themselves.
 */
public final class TrajectoryMasking {

    public static final String DEFAULT_KEY_PATTERN =
            "(?i)(key|token|secret|password|passwd|passphrase|pwd|authorization|api[-_]?key|credential)";

    private static final String REDACTED = "***";

    private TrajectoryMasking() {
    }

    public static Object mask(Object value, TrajectoryLevel level, Pattern keyPattern, int truncateChars) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (keyPattern != null && keyPattern.matcher(key).find()) {
                    out.put(key, REDACTED);
                } else {
                    out.put(key, mask(entry.getValue(), level, keyPattern, truncateChars));
                }
            }
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(v -> mask(v, level, keyPattern, truncateChars)).toList();
        }
        if (value instanceof CharSequence text) {
            String s = text.toString();
            if (level == TrajectoryLevel.SUMMARY && truncateChars > 0 && s.length() > truncateChars) {
                return s.substring(0, truncateChars) + "…(" + s.length() + ")";
            }
            return s;
        }
        return value;
    }
}
