package com.huawei.ascend.agentsdk.support;

import java.util.Map;

/**
 * Strict option coercion: a mistyped value must fail with the option name,
 * never silently fall back to a default (a quoted {@code "10"} from env
 * substitution would otherwise run with the built-in iteration count).
 */
public final class OptionValues {

    private OptionValues() { }

    public static int intOption(Map<String, Object> options, String key, int fallback) {
        Object value = options.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new ValidationException("Option '" + key + "' must be an integer, got: " + value);
        }
    }
}
