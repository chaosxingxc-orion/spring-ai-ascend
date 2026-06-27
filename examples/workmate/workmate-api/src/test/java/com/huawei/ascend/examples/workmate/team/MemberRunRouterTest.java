package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteRequest;
import com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties;
import com.huawei.ascend.examples.workmate.runtime.ConfiguredMemberRuntimeLifecycle;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class MemberRunRouterTest {

    @Mock
    private AgentRunExecutor agentRunExecutor;

  private ConfiguredMemberRuntimeLifecycle lifecycle;
  private WorkmateMemberRuntimeProperties properties;
  private MemberRunRouter router;

  @BeforeEach
  void setUp() {
    properties = new WorkmateMemberRuntimeProperties();
    lifecycle = new ConfiguredMemberRuntimeLifecycle(properties);
    router = new MemberRunRouter(agentRunExecutor, lifecycle, properties);
  }

  @Test
  void fallsBackToInProcessWhenRemoteDisabled() {
    properties.setEnabled(false);
    WorkmateSession session = new WorkmateSession(
        UUID.randomUUID(),
        "t",
        "/tmp",
        com.huawei.ascend.examples.workmate.session.SessionStatus.CREATED,
        "team",
        PermissionMode.CRAFT);
    ExecuteRequest request =
        new ExecuteRequest(session, "hi", "sub", "prd-writer", null, new SseEmitter(), new AtomicBoolean(true), false, false, false);
    when(agentRunExecutor.execute(request)).thenReturn(new ExecuteOutcome("local", false, null));

    ExecuteOutcome outcome = router.executeMember(request, "prd-writer");
    assertThat(outcome.assistantText()).isEqualTo("local");
    assertThat(router.usesRemoteMember("prd-writer")).isFalse();
  }

  @Test
  void resolvesRemoteUrlWhenEnabled() {
    properties.setEnabled(true);
    properties.setMembers(Map.of("prd-writer", "http://localhost:8081"));
    assertThat(router.usesRemoteMember("prd-writer")).isTrue();
    assertThat(router.remoteBaseUrl("prd-writer").map(Object::toString).orElse(""))
        .isEqualTo("http://localhost:8081");
  }
}
