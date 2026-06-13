package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link ValueRecognizingRedactor}: key-name redaction, Luhn-valid card
 * numbers, national-id patterns, GPS coordinate pairs, and their respective
 * false-positive guards.
 */
class ValueRecognizingRedactorTest {

    private static final Pattern KEY_PATTERN =
            Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN);

    private final ValueRecognizingRedactor redactor =
            new ValueRecognizingRedactor(KEY_PATTERN, 256);

    // -------------------------------------------------------------------------
    // Key-name redaction (same as TrajectoryMasking — must still work)
    // -------------------------------------------------------------------------

    @Test
    void keyNameRedactionStillWorks() {
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("api_key", "sk-plaintext-secret", "query", "weather in Paris"));
        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> out = (Map<?, ?>) result;
        assertThat(out.get("api_key")).isEqualTo("***");
        assertThat(out.get("query")).isEqualTo("weather in Paris");
    }

    @Test
    void keyNameRedactionIsRecursive() {
        Object result = redactor.redact("MODEL_CALL_END", "result",
                Map.of("wrapper", Map.of("password", "hunter2", "name", "alice")));
        Map<?, ?> outer = (Map<?, ?>) result;
        Map<?, ?> inner = (Map<?, ?>) outer.get("wrapper");
        assertThat(inner.get("password")).isEqualTo("***");
        assertThat(inner.get("name")).isEqualTo("alice");
    }

    // -------------------------------------------------------------------------
    // Credit-card Luhn-valid values under innocuous keys → redacted
    // -------------------------------------------------------------------------

    @Test
    void luhnValidCardNumberUnderInnocentKeyIsRedacted() {
        // Visa test card: 4111111111111111 (Luhn-valid, 16 digits)
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("payment", "4111111111111111", "amount", "100"));
        Map<?, ?> out = (Map<?, ?>) result;
        assertThat(out.get("payment")).isEqualTo("***");
        assertThat(out.get("amount")).isEqualTo("100");
    }

    @Test
    void luhnValidCardWithSpaceGroupingIsRedacted() {
        // 4111 1111 1111 1111 — space-grouped Visa test card
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("card", "4111 1111 1111 1111"));
        assertThat(((Map<?, ?>) result).get("card")).isEqualTo("***");
    }

    @Test
    void luhnValidCardWithHyphenGroupingIsRedacted() {
        // 4111-1111-1111-1111
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("card", "4111-1111-1111-1111"));
        assertThat(((Map<?, ?>) result).get("card")).isEqualTo("***");
    }

    // -------------------------------------------------------------------------
    // Non-Luhn-valid 16-digit number → NOT masked (false-positive guard)
    // -------------------------------------------------------------------------

    @Test
    void nonLuhnSixteenDigitNumberIsNotRedacted() {
        // 1234567890123456 — 16 digits but NOT Luhn-valid
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("order_id", "1234567890123456"));
        assertThat(((Map<?, ?>) result).get("order_id")).isEqualTo("1234567890123456");
    }

    // -------------------------------------------------------------------------
    // Chinese resident ID (18 chars) → redacted
    // -------------------------------------------------------------------------

    @Test
    void chineseResidentIdIsRedacted() {
        // 17 digits + X — structurally valid 18-char resident ID
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("id_card", "11010519491231002X"));
        assertThat(((Map<?, ?>) result).get("id_card")).isEqualTo("***");
    }

    @Test
    void chineseResidentIdAllDigitsIsRedacted() {
        // 18 digits
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("id_card", "110105194912310021"));
        assertThat(((Map<?, ?>) result).get("id_card")).isEqualTo("***");
    }

    // -------------------------------------------------------------------------
    // SSN-like pattern → redacted
    // -------------------------------------------------------------------------

    @Test
    void ssnPatternIsRedacted() {
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("ssn", "123-45-6789"));
        assertThat(((Map<?, ?>) result).get("ssn")).isEqualTo("***");
    }

    // -------------------------------------------------------------------------
    // GPS coordinate PAIR → redacted
    // -------------------------------------------------------------------------

    @Test
    void gpsCoordinatePairIsRedacted() {
        // lat,lng each with 4+ decimal places
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("location", "39.9042,116.4074"));
        assertThat(((Map<?, ?>) result).get("location")).isEqualTo("***");
    }

    @Test
    void gpsCoordinatePairWithSpaceAfterCommaIsRedacted() {
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("location", "39.9042, 116.4074"));
        assertThat(((Map<?, ?>) result).get("location")).isEqualTo("***");
    }

    @Test
    void negativeGpsPairIsRedacted() {
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("position", "-33.8688, 151.2093"));
        assertThat(((Map<?, ?>) result).get("position")).isEqualTo("***");
    }

    // -------------------------------------------------------------------------
    // Bare float → NOT masked (false-positive guard for GPS)
    // -------------------------------------------------------------------------

    @Test
    void bareFloatIsNotRedacted() {
        // "39.9" — a single float, could be a price or score; must NOT be masked
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("score", "39.9"));
        assertThat(((Map<?, ?>) result).get("score")).isEqualTo("39.9");
    }

    @Test
    void gpsWithTooFewDecimalPlacesIsNotRedacted() {
        // Only 3 decimal places on lat — below the 4-decimal threshold
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("approx", "39.904, 116.407"));
        assertThat(((Map<?, ?>) result).get("approx")).isEqualTo("39.904, 116.407");
    }

    // -------------------------------------------------------------------------
    // Normal string → unchanged (or truncated when over the limit)
    // -------------------------------------------------------------------------

    @Test
    void normalSentenceIsNotRedacted() {
        Object result = redactor.redact("TOOL_CALL_START", "args",
                Map.of("message", "Hello, world!"));
        assertThat(((Map<?, ?>) result).get("message")).isEqualTo("Hello, world!");
    }

    @Test
    void longStringIsTruncatedAtLimit() {
        String longText = "x".repeat(300);
        Object result = redactor.redact("TOOL_CALL_START", "args", longText);
        assertThat((String) result).startsWith("x".repeat(256)).contains("…(300)");
    }

    // -------------------------------------------------------------------------
    // List and scalar passthrough
    // -------------------------------------------------------------------------

    @Test
    void listsAreWalked() {
        Object result = redactor.redact("TOOL_CALL_START", "args",
                List.of("normal", Map.of("token", "val"), "4111111111111111"));
        assertThat(result).isInstanceOf(List.class);
        List<?> out = (List<?>) result;
        assertThat(out.get(0)).isEqualTo("normal");
        assertThat(((Map<?, ?>) out.get(1)).get("token")).isEqualTo("***");
        assertThat(out.get(2)).isEqualTo("***"); // Luhn-valid card in list leaf
    }

    @Test
    void scalarIntegerPassesThrough() {
        assertThat(redactor.redact("TOOL_CALL_START", "args", 42)).isEqualTo(42);
    }

    @Test
    void nullPassesThrough() {
        assertThat(redactor.redact("TOOL_CALL_START", "args", null)).isNull();
    }
}
