package com.huawei.ascend.examples.workmate.team.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberHandbackToolEmitterTest {

    @Test
    void handbackToolCallIdStaysWithinSessionMessageLimit() {
        String id = MemberHandbackToolEmitter.handbackToolCallId("4bb0c177-1ac6-45f1-8cf3-3643c459c1d4", "topic-researcher");
        assertThat(id).startsWith("mr-");
        assertThat(id.length()).isLessThanOrEqualTo(TeamDelegationToolEmitter.MAX_TOOL_CALL_ID_LENGTH);
    }

    @Test
    void handbackToolCallIdTruncatesLongMemberIds() {
        String longMemberId = "x".repeat(120);
        String id = MemberHandbackToolEmitter.handbackToolCallId("parent-run-id", longMemberId);
        assertThat(id.length()).isLessThanOrEqualTo(TeamDelegationToolEmitter.MAX_TOOL_CALL_ID_LENGTH);
    }

    @Test
    void ingestHandbackToolCallIdIncludesSequence() {
        String id = MemberHandbackToolEmitter.ingestHandbackToolCallId(
                "4bb0c177-1ac6-45f1-8cf3-3643c459c1d4", "fund-analyst", 2);
        assertThat(id).startsWith("mi-");
        assertThat(id).endsWith("-2");
        assertThat(id.length()).isLessThanOrEqualTo(TeamDelegationToolEmitter.MAX_TOOL_CALL_ID_LENGTH);
    }
}
