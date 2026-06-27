package com.huawei.ascend.examples.workmate.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunStreamServiceTest {

    @Test
    void parseLastEventIdDefaultsToZero() {
        assertThat(RunStreamService.parseLastEventId(null)).isZero();
        assertThat(RunStreamService.parseLastEventId("")).isZero();
        assertThat(RunStreamService.parseLastEventId("abc")).isZero();
    }

    @Test
    void parseLastEventIdReadsNumericSeq() {
        assertThat(RunStreamService.parseLastEventId("12")).isEqualTo(12);
    }

    @Test
    void detectsTerminalEvents() {
        assertThat(RunStreamService.isTerminal("run.completed")).isTrue();
        assertThat(RunStreamService.isTerminal("run.failed")).isTrue();
        assertThat(RunStreamService.isTerminal("message.delta")).isFalse();
        assertThat(RunStreamService.isTerminal(
                        "run.completed",
                        "{\"surface\":\"team\",\"memberId\":\"writer\",\"parentRunId\":\"p1\"}"))
                .isFalse();
    }
}
