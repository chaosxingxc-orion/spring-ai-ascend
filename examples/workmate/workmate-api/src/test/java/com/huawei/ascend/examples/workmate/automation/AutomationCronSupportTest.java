package com.huawei.ascend.examples.workmate.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AutomationCronSupportTest {

    @Test
    void normalizesFiveFieldCron() {
        String normalized = AutomationCronSupport.normalizeCron("0 9 * * *");
        assertThat(normalized).isEqualTo("0 0 9 * * *");
    }

    @Test
    void computesNextRunAfterNow() {
        Instant after = Instant.parse("2026-06-22T08:00:00Z");
        Instant next = AutomationCronSupport.computeNextRun("0 9 * * *", after);
        assertThat(next).isAfter(after);
    }
}
