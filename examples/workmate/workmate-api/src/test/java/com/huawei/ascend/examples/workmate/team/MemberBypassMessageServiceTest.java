package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.team.backend.MemberBackendRegistry;
import com.huawei.ascend.examples.workmate.team.mailbox.TeamMailboxAddressing;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool;
import com.huawei.ascend.examples.workmate.team.runtime.TeamRuntimeManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MemberBypassMessageServiceTest {

    @Test
    void routesFromUserSenderNotLeader() {
        UUID sessionId = UUID.randomUUID();
        TeamRuntimeManager manager = new TeamRuntimeManager(new MemberBackendRegistry(List.of()));
        MemberWorkerPool pool = manager.runtimeFor(
                "parent-run",
                "__lead__",
                sessionId.toString(),
                List.of(new TeamMemberDefinition("topic-researcher", "谭溯源", "topic-researcher", "research", 1, "🧑")));
        AgentRunExecutor executor = Mockito.mock(AgentRunExecutor.class);
        MemberBypassMessageService service = new MemberBypassMessageService(manager, executor, null);

        var result = service.send(
                sessionId, new MemberBypassMessageService.Request("@topic-researcher", "请补充来源链接", null));
        assertThat(result).isPresent();
        assertThat(result.get().delivered()).containsExactly("topic-researcher");
        var mail = pool.mailbox().drainUnread("topic-researcher");
        assertThat(mail).hasSize(1);
        assertThat(mail.get(0).from()).isEqualTo(TeamMailboxAddressing.USER_SENDER_ID);
    }

    @Test
    void returnsEmptyWhenNoActiveRun() {
        MemberBypassMessageService service =
                new MemberBypassMessageService(new TeamRuntimeManager(new MemberBackendRegistry(List.of())), null, null);
        assertThat(service.send(
                        UUID.randomUUID(), new MemberBypassMessageService.Request("@main", "hello", null)))
                .isEmpty();
    }

    @Test
    void requiresMessageBody() {
        UUID sessionId = UUID.randomUUID();
        TeamRuntimeManager manager = new TeamRuntimeManager(new MemberBackendRegistry(List.of()));
        manager.runtimeFor(
                "run-1",
                "__lead__",
                sessionId.toString(),
                List.of(new TeamMemberDefinition("m1", "M1", "m1-expert", "role", 1, "🧑")));
        MemberBypassMessageService service = new MemberBypassMessageService(manager, null, null);

        assertThatThrownBy(() -> service.send(sessionId, new MemberBypassMessageService.Request("@main", "  ", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
