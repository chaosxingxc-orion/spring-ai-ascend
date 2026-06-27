package com.huawei.ascend.examples.workmate.team.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamDelegationToolEmitterTest {

  private static final String PARENT_RUN_ID = "c692df91-979e-4d8e-8a4d-be9ff58e8fb3";

  @Test
  void sendMessageToolCallIdFitsSessionMessageColumn() {
    String id = TeamDelegationToolEmitter.sendMessageToolCallId(PARENT_RUN_ID, "topic-researcher", 1);

    assertThat(id).isEqualTo("ts-c692df91-topic-researcher-1");
    assertThat(id.length()).isLessThanOrEqualTo(TeamDelegationToolEmitter.MAX_TOOL_CALL_ID_LENGTH);
    assertThat(("delegation-" + id).length()).isLessThanOrEqualTo(64);
  }

  @Test
  void buildTeamToolCallIdFitsSessionMessageColumn() {
    String id = TeamDelegationToolEmitter.buildTeamToolCallId(PARENT_RUN_ID);

    assertThat(id).isEqualTo("tb-c692df91");
    assertThat(id.length()).isLessThanOrEqualTo(64);
  }

  @Test
  void sendMessageToolCallIdFallsBackForVeryLongMemberIds() {
    String longMemberId = "a".repeat(40);
    String id = TeamDelegationToolEmitter.sendMessageToolCallId(PARENT_RUN_ID, longMemberId, 99);

    assertThat(id.length()).isLessThanOrEqualTo(TeamDelegationToolEmitter.MAX_TOOL_CALL_ID_LENGTH);
    assertThat(("delegation-" + id).length()).isLessThanOrEqualTo(64);
  }
}
