package com.huawei.ascend.runtime.engine.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A {@link Redactor} that walks the payload like {@link TrajectoryMasking} (key-name match
 * then string truncation) AND additionally scans STRING leaves for semantically-sensitive
 * values, replacing matches with {@code "***"}.
 *
 * <h3>Built-in recognizers</h3>
 * All recognizers are conservative (low false-positive rate) by design.
 *
 * <ul>
 *   <li><b>Credit card</b> — 13–19 digits optionally separated by single spaces or hyphens,
 *       passing the Luhn checksum. A string that looks like a card number but fails Luhn is
 *       left unchanged. This guards against redacting plain numeric IDs or order numbers.
 *
 *   <li><b>Chinese resident ID</b> — exactly 18 characters: 17 digits followed by a digit or
 *       {@code X}/{@code x} ({@code \d{17}[\dXx]}). The fixed length and check-digit structure
 *       make false positives vanishingly rare.
 *       Also matches SSN-like {@code \d{3}-\d{2}-\d{4}} (US Social Security Number format).
 *
 *   <li><b>GPS coordinate pair</b> — a decimal latitude,longitude pair of the form
 *       {@code -?\d{1,3}\.\d{4,},\s*-?\d{1,3}\.\d{4,}}: BOTH latitude AND longitude must be
 *       present in a comma-separated pair, each with at least 4 decimal places. Bare floats
 *       like {@code "39.9"} are intentionally NOT matched — a bare float is too common in
 *       non-geographic contexts (prices, scores, percentages) to mask safely.
 * </ul>
 */
public final class ValueRecognizingRedactor implements Redactor {

    private static final String REDACTED = "***";

    /**
     * Luhn-valid credit-card: 13–19 digits, optionally grouped by spaces or hyphens.
     * Digits-only or grouped variants; Luhn check filters out non-card numeric strings.
     */
    private static final Pattern CARD_CANDIDATE = Pattern.compile(
            "\\b(?:\\d[ -]?){12,18}\\d\\b");

    /**
     * Chinese 18-digit resident ID: 17 digits + 1 digit or X.
     * Must match the WHOLE string (or be isolated by word boundaries) to avoid redacting
     * substrings of longer numbers.
     */
    private static final Pattern CHINESE_ID = Pattern.compile(
            "\\b\\d{17}[\\dXx]\\b");

    /**
     * US SSN-like pattern: NNN-NN-NNNN.
     */
    private static final Pattern SSN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    /**
     * GPS coordinate PAIR: lat,lng both with at least 4 decimal places.
     * A bare float is NOT matched — a pair is required (prevents false positives on
     * floating-point scores, prices, percentages, etc.).
     */
    private static final Pattern GPS_PAIR = Pattern.compile(
            "-?\\d{1,3}\\.\\d{4,},\\s*-?\\d{1,3}\\.\\d{4,}");

    private final Pattern keyPattern;
    private final int truncateChars;

    /**
     * Constructs a recognizing redactor with the same key-pattern and truncation bound
     * as the standard masker, so deployments can replace the default masking call with
     * this one without changing the built-in key-name behaviour.
     *
     * @param keyPattern    regex matched against map keys; matched keys are replaced with
     *                      {@code "***"} without inspecting the value
     * @param truncateChars maximum length for string leaves; 0 means no truncation
     */
    public ValueRecognizingRedactor(Pattern keyPattern, int truncateChars) {
        this.keyPattern = keyPattern;
        this.truncateChars = truncateChars;
    }

    @Override
    public Object redact(String eventKind, String fieldPath, Object value) {
        return walk(value);
    }

    private Object walk(Object value) {
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
                    out.put(key, walk(entry.getValue()));
                }
            }
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::walk).toList();
        }
        if (value instanceof CharSequence text) {
            String s = text.toString();
            // Value-based recognition before truncation so we don't truncate then fail
            // to match a card/id that straddles the truncation point.
            if (containsSensitiveValue(s)) {
                return REDACTED;
            }
            if (truncateChars > 0 && s.length() > truncateChars) {
                return s.substring(0, truncateChars) + "…(" + s.length() + ")";
            }
            return s;
        }
        return value;
    }

    /**
     * Returns {@code true} if the string contains a recognizable sensitive value.
     * Order: cheapest checks first (regex without Luhn), then Luhn on any candidate.
     */
    private static boolean containsSensitiveValue(String s) {
        if (CHINESE_ID.matcher(s).find()) {
            return true;
        }
        if (SSN.matcher(s).find()) {
            return true;
        }
        if (GPS_PAIR.matcher(s).find()) {
            return true;
        }
        // Credit card: structural match first, then Luhn to eliminate false positives.
        var cardMatcher = CARD_CANDIDATE.matcher(s);
        while (cardMatcher.find()) {
            String digits = cardMatcher.group().replaceAll("[ -]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && luhn(digits)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Luhn checksum: standard algorithm over the digit string.
     * Returns {@code true} when the number is Luhn-valid.
     */
    private static boolean luhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
