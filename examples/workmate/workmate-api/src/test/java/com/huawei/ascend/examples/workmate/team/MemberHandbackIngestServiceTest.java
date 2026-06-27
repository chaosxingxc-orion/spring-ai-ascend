package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.team.backend.MemberBackendRegistry;
import com.huawei.ascend.examples.workmate.team.runtime.TeamRuntimeManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberHandbackIngestServiceTest {

    @Test
    void returnsEmptyWhenNoActiveTeamRun() {
        MemberHandbackIngestService service =
                new MemberHandbackIngestService(new TeamRuntimeManager(new MemberBackendRegistry(List.of())), null, null);

        assertThat(service.ingest(
                        UUID.randomUUID(),
                        new MemberHandbackIngestService.Request("m1", "body", null, "team-lead")))
                .isEmpty();
    }

    @Test
    void rejectsNonLeaderTarget() {
        UUID sessionId = UUID.randomUUID();
        TeamRuntimeManager manager = new TeamRuntimeManager(new MemberBackendRegistry(List.of()));
        manager.runtimeFor(
                "parent-run",
                "__lead__",
                sessionId.toString(),
                List.of(new TeamMemberDefinition("m1", "M1", "m1-expert", "role", 1, "🧑")));
        MemberHandbackIngestService service = new MemberHandbackIngestService(manager, null, null);

        assertThatThrownBy(() -> service.ingest(
                        sessionId, new MemberHandbackIngestService.Request("m1", "body", null, "peer-id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("team-lead");
    }
}
