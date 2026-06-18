package com.huawei.ascend.a2a.memory.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The experience-layer PII floor: emails, national ids, long digit runs, CN mobiles. */
class DefaultPiiRedactorTest {

    private final DefaultPiiRedactor redactor = new DefaultPiiRedactor();

    @Test
    void redactsEmail() {
        String out = redactor.redact("contact me at zhang.san@bank.com please");
        assertFalse(out.contains("zhang.san@bank.com"));
        assertTrue(out.contains(DefaultPiiRedactor.MARK));
    }

    @Test
    void redactsCnMobile() {
        assertEquals("call [REDACTED]", redactor.redact("call 13800138000"));
    }

    @Test
    void redactsLongDigitRunLikeACardOrAccount() {
        assertFalse(redactor.redact("card 6222021234567890123 expires soon").contains("6222021234567890123"));
    }

    @Test
    void redactsCnNationalId() {
        assertFalse(redactor.redact("id 11010119900307123X on file").contains("11010119900307123X"));
    }

    @Test
    void leavesOrdinaryTextUntouched() {
        String text = "client prefers short-term, low-risk wealth products";
        assertEquals(text, redactor.redact(text));
    }
}
