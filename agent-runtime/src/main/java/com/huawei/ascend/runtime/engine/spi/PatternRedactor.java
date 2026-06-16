package com.huawei.ascend.runtime.engine.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default value-level {@link Redactor}: scans string leaves for sensitive content and replaces
 * the matched span with {@link #REDACTED}. Recurses into {@code Map}/{@code List} so a secret
 * nested anywhere in a structured payload is caught. Detects, conservatively to limit false
 * positives: credit-card numbers (13–19 digits passing the Luhn check), US-SSN-shaped ids
 * ({@code ddd-dd-dddd}), and decimal-degree GPS coordinate pairs.
 */
public final class PatternRedactor implements Redactor {

    private static final Pattern CARD_CANDIDATE = Pattern.compile("\\b\\d(?:[ -]?\\d){12,18}\\b");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern GPS = Pattern.compile(
            "[-+]?\\d{1,3}\\.\\d{3,}\\s*,\\s*[-+]?\\d{1,3}\\.\\d{3,}");

    @Override
    public Object redact(Object value) {
        if (value instanceof String s) {
            return redactString(s);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(e.getKey(), redact(e.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(redact(item));
            }
            return out;
        }
        return value;
    }

    private static String redactString(String input) {
        String result = redactCards(input);
        result = SSN.matcher(result).replaceAll(REDACTED);
        result = GPS.matcher(result).replaceAll(REDACTED);
        return result;
    }

    /** Replace only digit runs that pass the Luhn check, so ordinary long numbers survive. */
    private static String redactCards(String input) {
        Matcher m = CARD_CANDIDATE.matcher(input);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String candidate = m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(luhnValid(candidate) ? REDACTED : candidate));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static boolean luhnValid(String candidate) {
        String digits = candidate.replaceAll("[ -]", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (alternate) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
