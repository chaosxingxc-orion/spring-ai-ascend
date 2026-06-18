package com.huawei.ascend.a2a.memory.privacy;

import java.util.regex.Pattern;

/**
 * Best-effort PII scrubber for the experience layer. Redacts the high-risk
 * identifiers a bank conversation tends to leak: email addresses, long digit
 * runs (card / account / national-id numbers), and CN mobile numbers. Order
 * matters — the longest/most specific patterns run first so a national id is not
 * partially eaten by the mobile rule.
 *
 * <p>This is a floor, not a guarantee; the closed engine may apply NER-based
 * detection. Callers that need certainty must not put raw PII into experience.
 */
public final class DefaultPiiRedactor implements PiiRedactor {

    public static final String MARK = "[REDACTED]";

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    // 12+ digit runs: bank card / account / 18-char national id (digits part).
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{12,}");
    // CN national id: 17 digits + digit or X.
    private static final Pattern CN_ID = Pattern.compile("\\b\\d{17}[\\dXx]\\b");
    // CN mobile: 1 followed by 10 digits (standalone).
    private static final Pattern CN_MOBILE = Pattern.compile("\\b1\\d{10}\\b");

    @Override
    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = EMAIL.matcher(text).replaceAll(MARK);
        out = CN_ID.matcher(out).replaceAll(MARK);
        out = LONG_DIGITS.matcher(out).replaceAll(MARK);
        out = CN_MOBILE.matcher(out).replaceAll(MARK);
        return out;
    }
}
