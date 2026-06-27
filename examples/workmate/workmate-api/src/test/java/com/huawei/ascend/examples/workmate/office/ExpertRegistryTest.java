package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExpertRegistryTest {

    @Test
    void loadsBundledOfficeExperts() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);

        assertThat(registry.listExperts()).extracting(ExpertDefinition::id)
                .contains("fund-analyst", "prd-writer", "content-reviewer", "gpt-researcher-team");
        assertThat(registry.requireExpert("gpt-researcher-team").members()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(registry.requireExpert("gpt-researcher-team").coordinationPattern()).isEqualTo("orchestrator");
        assertThat(registry.requireExpert("fund-analyst").systemPrompt())
                .contains("基金研究专家");
    }

    @Test
    void parsesA2DisplayNameProfessionAndMaxTurns() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);

        ExpertDefinition fund = registry.requireExpert("fund-analyst");
        assertThat(fund.displayName()).containsEntry("zh", "基金研究助手");
        assertThat(fund.displayName()).containsEntry("en", "Fund Analyst");
        assertThat(fund.profession()).containsEntry("zh", "基金研究分析师");
        assertThat(fund.maxTurns()).isEqualTo(30);
        assertThat(fund.resolvedDisplayName("en")).isEqualTo("Fund Analyst");
    }

    @Test
    void parsesA3TeamMemberPersonaAndLeadTitle() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);

        ExpertDefinition team = registry.requireExpert("gpt-researcher-team");
        TeamMemberDefinition writer = team.members().stream()
                .filter(m -> "research-planner".equals(m.id()))
                .findFirst()
                .orElseThrow();
        assertThat(writer.name()).isEqualTo("季要纲");
        assertThat(writer.profession()).containsEntry("zh", "研究编辑");

        assertThat(team.lead()).isNotNull();
        assertThat(team.lead().name()).isEqualTo("顾全之");
        assertThat(team.lead().title()).containsEntry("zh", "研究主编");
        assertThat(team.lead().resolvedTitle()).isEqualTo("研究主编");
    }

    @Test
    void parsesTeamRuntimeAndTeamAgentOverrides() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);

        ExpertDefinition legal = registry.requireExpert("gpt-researcher-team");
        assertThat(legal.resolvedTeamRuntime()).isEqualTo("openjiuwen-team");
        assertThat(legal.teamAgent()).isNotNull();
        assertThat(legal.teamAgent().resolvedTeamMode()).isEqualTo("hybrid");
        assertThat(legal.teamAgent().resolvedSpawnMode()).isEqualTo("inprocess");
    }

    @Test
    void allMigratableMarketplaceTeamsUseOpenJiuwenRuntime() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);

        var marketplaceTeams = registry.listExperts().stream()
                .filter(ExpertDefinition::isTeam)
                .filter(e -> e.tags().contains("marketplace"))
                .filter(e -> CoordinationSpec.ORCHESTRATOR.equals(e.coordinationPattern())
                        || CoordinationSpec.AGENT_TEAM.equals(e.coordinationPattern()))
                .toList();

        assertThat(marketplaceTeams).isNotEmpty();
        assertThat(marketplaceTeams).allMatch(e -> "openjiuwen-team".equals(e.resolvedTeamRuntime()));
        assertThat(marketplaceTeams).allMatch(e -> e.teamAgent() != null);
    }

    @Test
    void parsesGptResearcherTeamRuntimeAndMemberOrder() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);

        ExpertDefinition team = registry.requireExpert("gpt-researcher-team");
        assertThat(team.resolvedTeamRuntime()).isEqualTo("openjiuwen-team");
        assertThat(team.teamAgent()).isNotNull();
        assertThat(team.teamAgent().resolvedTeamMode()).isEqualTo("hybrid");
        assertThat(team.teamAgent().resolvedTeammateMode()).isEqualTo("build_mode");
        assertThat(team.maxTurns()).isEqualTo(200);
        assertThat(team.members()).extracting(TeamMemberDefinition::id)
                .startsWith("topic-researcher", "research-planner");
    }

    @Test
    void resolvesMemberMaxTurnsFromPromptFrontmatterWhenYamlOmitsThem() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);

        assertThat(registry.requireExpert("gpt-researcher-team__topic-researcher").maxTurns()).isEqualTo(80);
        assertThat(registry.requireExpert("gpt-researcher-team__research-planner").maxTurns()).isEqualTo(40);
        assertThat(registry.requireExpert("gpt-researcher-team__draft-reviewer").maxTurns()).isEqualTo(30);
        assertThat(registry.requireExpert("gpt-researcher-team__report-publisher").maxTurns()).isEqualTo(30);
    }

    @Test
    void parsePromptFrontmatterIntReadsYamlStyleHeader() {
        String prompt = """
                ---
                name: demo
                maxTurns: 42
                ---

                # Body
                """;
        assertThat(ExpertRegistry.parsePromptFrontmatterInt(prompt, "maxTurns")).isEqualTo(42);
        assertThat(ExpertRegistry.parsePromptFrontmatterInt(prompt, "missing")).isNull();
        assertThat(ExpertRegistry.parsePromptFrontmatterInt("# no frontmatter", "maxTurns")).isNull();
    }
}
