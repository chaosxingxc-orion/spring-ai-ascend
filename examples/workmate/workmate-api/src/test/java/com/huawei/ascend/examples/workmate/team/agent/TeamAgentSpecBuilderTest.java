package com.huawei.ascend.examples.workmate.team.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamAgentSpecBuilderTest {

    @Test
    void buildsHybridTeamSpecFromBundledOfficeExpert() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);
        WorkmateLlmProperties llm = new WorkmateLlmProperties(
                "OpenAI",
                "test-key",
                "https://example.invalid/v1",
                "gpt-test",
                true,
                10,
                "gpt-test",
                java.util.List.of());
        ModelCatalogService modelCatalog = new ModelCatalogService(llm);
        TeamAgentSpecBuilder builder = new TeamAgentSpecBuilder(registry, modelCatalog, llm);

        ExpertDefinition team = registry.requireExpert("gpt-researcher-team");
        UUID sessionId = UUID.randomUUID();
        WorkmateSession session = new WorkmateSession(
                sessionId, "test", "/tmp/workmate-team-spec-test", SessionStatus.CREATED, team.id());
        session.setModelId(llm.defaultModelId());

        TeamAgentSpec spec = builder.build(session, team, "parent-run-12345678");

        assertThat(spec.getTeamName()).isEqualTo("gpt-researcher-team-parent-r");
        assertThat(spec.getTeamMode()).isEqualTo("hybrid");
        assertThat(spec.getSpawnMode()).isEqualTo("inprocess");
        assertThat(spec.getPredefinedMembers()).extracting(TeamMemberSpec::getMemberName)
                .contains("topic-researcher", "report-writer");
        assertThat(spec.getAgents()).containsKeys("leader", "topic-researcher", "report-writer");
        assertThat(spec.getWorkspace()).isNotNull();
        assertThat(spec.getWorkspace().getRootPath()).isEqualTo("/tmp/workmate-team-spec-test");
        assertThat(spec.getMetadata()).containsEntry("workmateTeamId", "gpt-researcher-team");
    }

    @Test
    void buildsMarketplaceTeamMembersFromBundledOfficeExpert() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);
        WorkmateLlmProperties llm = new WorkmateLlmProperties(
                "OpenAI",
                "test-key",
                "https://example.invalid/v1",
                "gpt-test",
                true,
                10,
                "gpt-test",
                java.util.List.of());
        ModelCatalogService modelCatalog = new ModelCatalogService(llm);
        TeamAgentSpecBuilder builder = new TeamAgentSpecBuilder(registry, modelCatalog, llm);

        ExpertDefinition team = registry.requireExpert("gpt-researcher-team");
        assertThat(team.members()).extracting(m -> m.backend().yamlValue())
                .containsOnly("local");

        UUID sessionId = UUID.randomUUID();
        WorkmateSession session = new WorkmateSession(
                sessionId, "test", "/tmp/workmate-expert-ref-test", SessionStatus.CREATED, team.id());
        session.setModelId(llm.defaultModelId());

        TeamAgentSpec spec = builder.build(session, team, "parent-run-expertref");

        assertThat(spec.getAgents().get("topic-researcher").getConfig().getCard().getDescription())
                .contains("gpt-researcher-team__topic-researcher");
    }

    @Test
    void buildsGptResearcherTeamWithAppendixAndMemberTurnLimits() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);
        WorkmateLlmProperties llm = new WorkmateLlmProperties(
                "OpenAI",
                "test-key",
                "https://example.invalid/v1",
                "gpt-test",
                true,
                10,
                "gpt-test",
                java.util.List.of());
        ModelCatalogService modelCatalog = new ModelCatalogService(llm);
        TeamAgentSpecBuilder builder = new TeamAgentSpecBuilder(registry, modelCatalog, llm);

        ExpertDefinition team = registry.requireExpert("gpt-researcher-team");
        assertThat(team.resolvedTeamRuntime()).isEqualTo("openjiuwen-team");
        assertThat(team.teamAgent().resolvedTeammateMode()).isEqualTo("build_mode");
        assertThat(team.maxTurns()).isEqualTo(200);
        assertThat(team.members().get(0).id()).isEqualTo("topic-researcher");

        UUID sessionId = UUID.randomUUID();
        WorkmateSession session = new WorkmateSession(
                sessionId, "test", "/tmp/workmate-gpt-researcher-test", SessionStatus.CREATED, team.id());
        session.setModelId(llm.defaultModelId());

        TeamAgentSpec spec = builder.build(session, team, "parent-run-gpt");

        assertThat(spec.getTeamMode()).isEqualTo("hybrid");
        assertThat(spec.getTeammateMode()).isEqualTo("build_mode");
        assertThat(spec.getAgents().get("leader").getConfig().getMaxIterations()).isEqualTo(200);
        assertThat(spec.getAgents().get("topic-researcher").getConfig().getMaxIterations()).isEqualTo(80);
        assertThat(spec.getAgents().get("research-planner").getConfig().getMaxIterations()).isEqualTo(40);
        assertThat(spec.getAgents().get("draft-reviewer").getConfig().getMaxIterations()).isEqualTo(30);
    }
}
