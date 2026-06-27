package com.huawei.ascend.examples.workmate.office;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class OfficeYaml {

    private OfficeYaml() {}

    static String stringField(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    static List<String> stringList(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return List.of();
    }

    static boolean booleanField(Map<?, ?> raw, String key, boolean defaultValue) {
        Object value = raw.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return defaultValue;
    }

    static Integer boxedInt(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static Long boxedLong(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static int intField(Map<?, ?> raw, String key, int fallback) {
        Object value = raw.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> applyProfile(Map<?, ?> raw) {
        String active = stringField(raw, "activeProfile");
        if (active == null || active.isBlank()) {
            return copyMap(raw);
        }
        Object profilesObj = raw.get("profiles");
        if (!(profilesObj instanceof Map<?, ?> profiles)) {
            return copyMap(raw);
        }
        Object profileObj = profiles.get(active);
        if (!(profileObj instanceof Map<?, ?> profile)) {
            return copyMap(raw);
        }
        Map<String, Object> merged = copyMap(raw);
        merged.remove("profiles");
        merged.remove("activeProfile");
        deepMerge(merged, profile);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Map<?, ?> raw) {
        Map<String, Object> copy = new java.util.HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static void deepMerge(Map<String, Object> base, Map<?, ?> overlay) {
        for (Map.Entry<?, ?> entry : overlay.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object overlayValue = entry.getValue();
            Object baseValue = base.get(key);
            if (overlayValue instanceof Map<?, ?> overlayMap
                    && baseValue instanceof Map<?, ?> baseMap) {
                Map<String, Object> mergedChild = copyMap(baseMap);
                deepMerge(mergedChild, overlayMap);
                base.put(key, mergedChild);
            } else {
                base.put(key, overlayValue);
            }
        }
    }
}
