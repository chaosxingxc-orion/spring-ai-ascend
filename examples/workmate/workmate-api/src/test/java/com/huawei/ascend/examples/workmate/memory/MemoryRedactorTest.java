package com.huawei.ascend.examples.workmate.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemoryRedactorTest {

    private final MemoryRedactor redactor = new MemoryRedactor();

    @Test
    void dropsSecretLikeLines() {
        assertThat(redactor.sanitizeLine("password is hunter2")).isBlank();
    }

    @Test
    void redactsEmailAndPhone() {
        String sanitized = redactor.sanitizeLine("Contact me at alice@example.com or 13800138000");
        assertThat(sanitized).contains("[REDACTED_EMAIL]").contains("[REDACTED_PHONE]");
    }
}
