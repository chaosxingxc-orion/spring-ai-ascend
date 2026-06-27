package com.huawei.ascend.examples.workmate.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunEventTerminalTest {

    @Test
    void memberRunCompletedIsNotLeaderTerminal() {
        String payload =
                "{\"surface\":\"team\",\"memberId\":\"topic-researcher\",\"parentRunId\":\"parent-1\"}";
        assertThat(RunEventTerminal.isTerminal("run.completed", payload)).isFalse();
        assertThat(RunEventTerminal.isTerminal("run.error", payload)).isFalse();
    }

    @Test
    void leaderRunCompletedIsTerminal() {
        assertThat(RunEventTerminal.isTerminal("run.completed", "{}")).isTrue();
        assertThat(RunEventTerminal.isTerminal("team.completed", "{}")).isTrue();
    }
}
