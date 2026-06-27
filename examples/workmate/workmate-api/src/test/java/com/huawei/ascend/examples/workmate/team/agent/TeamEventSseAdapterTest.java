package com.huawei.ascend.examples.workmate.team.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TeamEventSseAdapterTest {

    @Test
    void teamStartedPayloadMatchesOrchestratorShape() {
        ExpertRegistry registry = bundledRegistry();
        ExpertDefinition team = registry.requireExpert("gpt-researcher-team");
        List<TeamMemberDefinition> members = team.members();

        Map<String, Object> payload = TeamEventSseAdapter.teamStartedPayload(team, "task-1", members);

        assertThat(payload)
                .containsEntry("teamId", "gpt-researcher-team")
                .containsEntry("parentRunId", "task-1")
                .containsEntry("pattern", "orchestrator");
        assertThat(payload.get("members")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roster = (List<Map<String, Object>>) payload.get("members");
        assertThat(roster.get(0))
                .containsEntry("memberId", "topic-researcher")
                .containsEntry("backendType", "local");
    }

    @Test
    void teamStartedPayloadIncludesTeamRuntimeWhenProvided() {
        ExpertRegistry registry = bundledRegistry();
        ExpertDefinition team = registry.requireExpert("gpt-researcher-team");
        Map<String, Object> payload = TeamEventSseAdapter.teamStartedPayload(
                team, "task-1", team.members(), "openjiuwen-team");
        assertThat(payload).containsEntry("teamRuntime", "openjiuwen-team");
    }

    @Test
    void adaptsMemberSpawnedToTeamMemberStarted() {
        ExpertRegistry registry = bundledRegistry();
        ExpertDefinition team = registry.requireExpert("gpt-researcher-team");
        List<TeamMemberDefinition> members = team.members();

        var adapted = TeamEventSseAdapter.adaptTeamEvent(
                TeamEvent.MEMBER_SPAWNED,
                Map.of("member_name", "topic-researcher"),
                team,
                "task-1",
                members);

        assertThat(adapted).isPresent();
        assertThat(adapted.get().sseEventName()).isEqualTo("team.member.started");
        assertThat(adapted.get().payload())
                .containsEntry("memberId", "topic-researcher")
                .containsEntry("subRunId", "task-1:topic-researcher");
    }

    @Test
    void adaptsTeamCreatedToTeamBuildCompleted() {
        ExpertRegistry registry = bundledRegistry();
        ExpertDefinition team = registry.requireExpert("gpt-researcher-team");
        List<TeamMemberDefinition> members = team.members();

        List<TeamEventSseAdapter.AdaptedTeamEvent> adapted = TeamEventSseAdapter.adaptTeamEvents(
                TeamEvent.CREATED,
                Map.of("team_name", "research-ai-agent", "display_name", "research-ai-agent"),
                team,
                "task-1",
                members);

        assertThat(adapted).hasSize(1);
        assertThat(adapted.get(0).sseEventName()).isEqualTo("team.build.completed");
        assertThat(adapted.get(0).payload())
                .containsEntry("teamName", "research-ai-agent")
                .containsEntry("displayName", "research-ai-agent")
                .containsEntry("memberCount", 6)
                .containsEntry("status", "created");
    }

    @Test
    void memberMessagePayloadIncludesBypassMetadata() {
        Map<String, Object> payload = TeamEventSseAdapter.memberMessagePayload(
                "task-1",
                "__user__",
                "用户",
                "@topic-researcher",
                List.of("topic-researcher"),
                "请补充来源",
                "旁路",
                false);

        assertThat(payload)
                .containsEntry("surface", "team")
                .containsEntry("parentRunId", "task-1")
                .containsEntry("from", "__user__")
                .containsEntry("fromLabel", "用户")
                .containsEntry("to", "@topic-researcher")
                .containsEntry("kind", "user_bypass")
                .containsEntry("message", "请补充来源")
                .containsEntry("summary", "旁路");
        assertThat(payload.get("delivered")).isEqualTo(List.of("topic-researcher"));
    }

    private static ExpertRegistry bundledRegistry() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        return new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);
    }
}
