package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Value-level redaction: Luhn credit-card / SSN / GPS; Map/List recursion; no false positives. */
class PatternRedactorTest {

    private final PatternRedactor redactor = new PatternRedactor();

    // --- Credit card (Luhn-valid) ---

    @Test
    void visaCreditCardNumberIsRedacted() {
        // A known Luhn-valid test number (Visa: 4111 1111 1111 1111)
        assertThat(redactor.redact("card: 4111 1111 1111 1111 thanks"))
                .isEqualTo("card: *** thanks");
    }

    @Test
    void masterCardWithDashesIsRedacted() {
        // Luhn-valid Mastercard test number
        assertThat(redactor.redact("pay with 5500-0000-0000-0004 now"))
                .isEqualTo("pay with *** now");
    }

    @Test
    void luhnInvalidLongNumberSurvives() {
        // 16 digits but fails Luhn check
        assertThat(redactor.redact("ref: 1234567890123456"))
                .isEqualTo("ref: 1234567890123456");
    }

    @Test
    void shortDigitRunSurvives() {
        // 12 digits — below the 13-digit minimum
        assertThat(redactor.redact("pin: 123456789012"))
                .isEqualTo("pin: 123456789012");
    }

    // --- SSN ---

    @Test
    void ssnIsRedacted() {
        assertThat(redactor.redact("ssn 123-45-6789 on file"))
                .isEqualTo("ssn *** on file");
    }

    @Test
    void ssnLookalikeWithExtraDigitSurvives() {
        assertThat(redactor.redact("ref 123-456-7890"))
                .isEqualTo("ref 123-456-7890");
    }

    // --- GPS ---

    @Test
    void gpsCoordinatePairIsRedacted() {
        assertThat(redactor.redact("loc 37.7749,-122.4194"))
                .isEqualTo("loc ***");
    }

    @Test
    void gpsWithSpaceIsRedacted() {
        assertThat(redactor.redact("at 51.5074, -0.1278 now"))
                .isEqualTo("at *** now");
    }

    @Test
    void shortDecimalSurvives() {
        // Only 2 decimal places — below the 3-decimal-place GPS threshold
        assertThat(redactor.redact("rate 3.14 is pi"))
                .isEqualTo("rate 3.14 is pi");
    }

    // --- Recursion into Map/List ---

    @Test
    void redactsNestedMapLeaf() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Alice");
        payload.put("cc", "4111 1111 1111 1111");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) redactor.redact(payload);

        assertThat(result.get("name")).isEqualTo("Alice");
        assertThat(result.get("cc")).isEqualTo("***");
    }

    @Test
    void redactsInsideList() {
        List<Object> list = List.of("safe text", "ssn 123-45-6789 here", 42);

        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) redactor.redact(list);

        assertThat(result.get(0)).isEqualTo("safe text");
        assertThat(result.get(1)).isEqualTo("ssn *** here");
        assertThat(result.get(2)).isEqualTo(42);
    }

    @Test
    void nonStringNonCollectionPassesThrough() {
        assertThat(redactor.redact(Integer.valueOf(42))).isEqualTo(42);
        assertThat(redactor.redact(null)).isNull();
    }

    // --- Redactor.NONE no-op ---

    @Test
    void nonePassesThroughUnchanged() {
        String sensitive = "4111 1111 1111 1111";
        assertThat(Redactor.NONE.redact(sensitive)).isEqualTo(sensitive);
    }
}
