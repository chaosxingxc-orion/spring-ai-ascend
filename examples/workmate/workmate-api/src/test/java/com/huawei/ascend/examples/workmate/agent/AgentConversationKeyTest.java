package com.huawei.ascend.examples.workmate.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentConversationKeyTest {

  @Test
  void singleAgentUsesStableSessionKey() {
    UUID sessionId = UUID.randomUUID();
    String taskId = UUID.randomUUID().toString();

    String key = AgentConversationKey.resolve(sessionId, taskId, null, false, 0);

    assertThat(key).isEqualTo(sessionId.toString());
  }

  @Test
  void singleAgentUsesGenerationKeyAfterEdit() {
    UUID sessionId = UUID.randomUUID();
    String taskId = UUID.randomUUID().toString();

    String key = AgentConversationKey.resolve(sessionId, taskId, null, false, 2);

    assertThat(key).isEqualTo(sessionId + ":g2");
  }

  @Test
  void teamMemberSubRunIsIsolatedPerTask() {
    UUID sessionId = UUID.randomUUID();
    String parentRunId = "parent-run";
    String subRunId = parentRunId + ":prd-writer";
    RunPersistenceContext memberContext =
        RunPersistenceContext.forMember(sessionId, subRunId, parentRunId, "prd-writer");

    String key = AgentConversationKey.resolve(sessionId, subRunId, memberContext, false, 3);

    assertThat(key).isEqualTo(sessionId + ":" + subRunId);
  }

  @Test
  void teamLeadSynthesisIsIsolatedPerParentRun() {
    UUID sessionId = UUID.randomUUID();
    String parentRunId = "parent-run";

    String key = AgentConversationKey.resolve(sessionId, parentRunId, null, true, 0);

    assertThat(key).isEqualTo(sessionId + ":" + parentRunId);
  }

  @Test
  void generatorVerifierSubRunsAreIsolated() {
    UUID sessionId = UUID.randomUUID();
    String parentRunId = "parent-run";
    String verifySubRunId = parentRunId + ":verifier:i2";
    RunPersistenceContext verifyContext =
        RunPersistenceContext.forMember(sessionId, verifySubRunId, parentRunId, "verifier");

    String key = AgentConversationKey.resolve(sessionId, verifySubRunId, verifyContext, false, 0);

    assertThat(key).isEqualTo(sessionId + ":" + verifySubRunId);
  }
}
