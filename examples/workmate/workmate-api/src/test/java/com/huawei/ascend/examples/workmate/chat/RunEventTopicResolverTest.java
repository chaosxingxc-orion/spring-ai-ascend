package com.huawei.ascend.examples.workmate.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunEventTopicResolverTest {

    @Test
    void mapsEventPrefixesToLanes() {
        assertThat(RunEventTopicResolver.resolve("team.member.completed")).isEqualTo("team");
        assertThat(RunEventTopicResolver.resolve("team.bus.published")).isEqualTo("bus");
        assertThat(RunEventTopicResolver.resolve("tool.start")).isEqualTo("tool");
        assertThat(RunEventTopicResolver.resolve("approval.required")).isEqualTo("approval");
        assertThat(RunEventTopicResolver.resolve("run.completed")).isEqualTo("run");
    }
}
