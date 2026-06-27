package com.huawei.ascend.examples.workmate.team.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateTeamRuntimeProperties;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TeamRuntimeRouterTest {

    @Test
    void defaultsToOpenJiuwenForOrchestratorTeamsWithoutExplicitRuntime() {
        ExpertRegistry registry = bundledRegistry();
        WorkmateTeamRuntimeProperties properties = new WorkmateTeamRuntimeProperties();
        properties.setDefaultRuntime("openjiuwen-team");
        TeamRuntimeRouter router = new TeamRuntimeRouter(registry, properties);

        assertThat(router.resolveKind("gpt-researcher-team")).isEqualTo(TeamRuntimeKind.OPENJIUWEN_TEAM);
        assertThat(router.isOpenJiuwenTeam("gpt-researcher-team")).isTrue();
    }

    @Test
    void defaultsToWorkmateOrchestratorWhenDefaultRuntimeIsLegacy() {
        ExpertRegistry registry = bundledRegistry();
        WorkmateTeamRuntimeProperties properties = new WorkmateTeamRuntimeProperties();
        properties.setDefaultRuntime("workmate-orchestrator");
        TeamRuntimeRouter router = new TeamRuntimeRouter(registry, properties);

        ExpertDefinition team = sampleTeam("legacy-demo-team", null);
        assertThat(router.resolveKind(team)).isEqualTo(TeamRuntimeKind.WORKMATE_ORCHESTRATOR);
    }

    @Test
    void routesExpertYamlRuntimeWithoutAllowlist() {
        ExpertRegistry registry = bundledRegistry();
        WorkmateTeamRuntimeProperties properties = new WorkmateTeamRuntimeProperties();
        TeamRuntimeRouter router = new TeamRuntimeRouter(registry, properties);

        assertThat(router.isOpenJiuwenTeam("gpt-researcher-team")).isTrue();
    }

    @Test
    void routesAllowlistedOrchestratorTeam() {
        ExpertRegistry registry = bundledRegistry();
        WorkmateTeamRuntimeProperties properties = new WorkmateTeamRuntimeProperties();
        properties.getAllowlist().add("gpt-researcher-team");
        TeamRuntimeRouter router = new TeamRuntimeRouter(registry, properties);

        assertThat(router.isOpenJiuwenTeam("gpt-researcher-team")).isTrue();
    }

    @Test
    void keepsGeneratorVerifierOnLegacyOrchestrator() {
        WorkmateTeamRuntimeProperties properties = new WorkmateTeamRuntimeProperties();
        properties.setDefaultRuntime("openjiuwen-team");
        TeamRuntimeRouter router = new TeamRuntimeRouter(bundledRegistry(), properties);

        ExpertDefinition gvTeam = sampleTeamWithPattern("demo-gv-team", "generator-verifier", null);
        assertThat(router.resolveKind(gvTeam)).isEqualTo(TeamRuntimeKind.WORKMATE_ORCHESTRATOR);
    }

    @Test
    void honorsExpertYamlRuntimeField() {
        ExpertDefinition team = sampleTeam("demo-team", "openjiuwen-team");
        WorkmateTeamRuntimeProperties properties = new WorkmateTeamRuntimeProperties();
        TeamRuntimeRouter router = new TeamRuntimeRouter(bundledRegistry(), properties);

        assertThat(router.resolveKind(team)).isEqualTo(TeamRuntimeKind.OPENJIUWEN_TEAM);
    }

    private static ExpertRegistry bundledRegistry() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        return new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);
    }

    private static ExpertDefinition sampleTeam(String id, String runtime) {
        return sampleTeamWithPattern(id, com.huawei.ascend.examples.workmate.office.CoordinationSpec.ORCHESTRATOR, runtime);
    }

    private static ExpertDefinition sampleTeamWithPattern(String id, String pattern, String runtime) {
        return new ExpertDefinition(
                id,
                "Demo Team",
                "desc",
                "team",
                "lead prompt",
                "init",
                "cat",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(
                        new com.huawei.ascend.examples.workmate.office.TeamMemberDefinition(
                                "a", "A", "fund-analyst", "role", 1, null),
                        new com.huawei.ascend.examples.workmate.office.TeamMemberDefinition(
                                "b", "B", "prd-writer", "role", 2, null)),
                "sequential",
                null,
                new com.huawei.ascend.examples.workmate.office.CoordinationSpec(pattern, null),
                null,
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                null,
                java.util.List.of(),
                java.util.List.of(),
                runtime,
                null);
    }
}
