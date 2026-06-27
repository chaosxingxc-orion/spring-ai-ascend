package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExpertYamlWriterTest {

    private static final Path OFFICE_ROOT = Path.of("../office").toAbsolutePath().normalize();

    @Test
    void roundTripsSingleAgentExpert() throws Exception {
        ExpertRegistry source = new ExpertRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), null);
        ExpertDefinition original = source.requireExpert("fund-analyst");
        ExpertRegistryEntry entry = source.findEntry("fund-analyst").orElseThrow();

        Path tempOffice = Files.createTempDirectory("expert-roundtrip-agent");
        Path expertDir = tempOffice.resolve("experts/fund-analyst");
        Files.createDirectories(expertDir);
        Files.writeString(expertDir.resolve("expert.yaml"), ExpertYamlWriter.render(original, entry.promptFile()));
        Files.writeString(expertDir.resolve(entry.promptFile()), original.systemPrompt());

        ExpertRegistry reloaded = new ExpertRegistry(new WorkmateOfficeProperties(tempOffice.toString()), null);
        ExpertDefinition roundTripped = reloaded.requireExpert("fund-analyst");

        assertThat(roundTripped.id()).isEqualTo(original.id());
        assertThat(roundTripped.name()).isEqualTo(original.name());
        assertThat(roundTripped.expertType()).isEqualTo(original.expertType());
        assertThat(roundTripped.category()).isEqualTo(original.category());
        assertThat(roundTripped.maxTurns()).isEqualTo(original.maxTurns());
        assertThat(roundTripped.displayName()).isEqualTo(original.displayName());
        assertThat(roundTripped.profession()).isEqualTo(original.profession());
        assertThat(roundTripped.skillCompatibility()).isEqualTo(original.skillCompatibility());
        assertThat(roundTripped.systemPrompt()).isEqualTo(original.systemPrompt());
    }

    @Test
    void roundTripsTeamExpert() throws Exception {
        ExpertRegistry source = new ExpertRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), null);
        ExpertDefinition original = source.requireExpert("gpt-researcher-team");
        ExpertRegistryEntry entry = source.findEntry("gpt-researcher-team").orElseThrow();

        Path tempOffice = Files.createTempDirectory("expert-roundtrip-team");
        Path teamDir = tempOffice.resolve("experts-market/gpt-researcher-team");
        Files.createDirectories(teamDir);
        Files.writeString(teamDir.resolve("expert.yaml"), ExpertYamlWriter.render(original, entry.promptFile()));
        Files.writeString(teamDir.resolve(entry.promptFile()), original.systemPrompt());

        ExpertRegistry reloaded = new ExpertRegistry(new WorkmateOfficeProperties(tempOffice.toString()), null);
        ExpertDefinition roundTripped = reloaded.requireExpert("gpt-researcher-team");

        assertThat(roundTripped.expertType()).isEqualTo("team");
        assertThat(roundTripped.coordinationPattern()).isEqualTo(original.coordinationPattern());
        assertThat(roundTripped.resolvedTeamRuntime()).isEqualTo(original.resolvedTeamRuntime());
        assertThat(roundTripped.members()).hasSize(original.members().size());
        assertThat(roundTripped.members()).extracting(TeamMemberDefinition::id)
                .containsExactlyElementsOf(original.members().stream().map(TeamMemberDefinition::id).toList());
        assertThat(roundTripped.lead().name()).isEqualTo(original.lead().name());
        assertThat(roundTripped.lead().title()).isEqualTo(original.lead().title());
        assertThat(roundTripped.maxTurns()).isEqualTo(original.maxTurns());
        assertThat(roundTripped.teamAgent().resolvedTeamMode()).isEqualTo(original.teamAgent().resolvedTeamMode());
    }

    @Test
    void draftOverridesBuiltinExpert(@TempDir Path dataDir) throws Exception {
        Path officeRoot = OFFICE_ROOT;
        OfficeImportPaths importPaths = new OfficeImportPaths(new WorkmateDataProperties(dataDir.toString()));

        Path draftDir = importPaths.expertDraftDir("fund-analyst");
        Files.createDirectories(draftDir);
        Files.writeString(draftDir.resolve("expert.yaml"), """
                id: fund-analyst
                name: Draft Fund Analyst
                description: Draft override
                expertType: agent
                promptFile: prompt.md
                category: finance
                tags:
                  - draft
                """);
        Files.writeString(draftDir.resolve("prompt.md"), "You are a draft fund analyst.");

        ExpertRegistry registry = new ExpertRegistry(
                new WorkmateOfficeProperties(officeRoot.toString()), importPaths);
        ExpertDefinition expert = registry.requireExpert("fund-analyst");

        assertThat(expert.name()).isEqualTo("Draft Fund Analyst");
        assertThat(expert.systemPrompt()).contains("draft fund analyst");
        assertThat(registry.findEntry("fund-analyst").orElseThrow().source()).isEqualTo(OfficeAssetSource.DRAFT);
    }
}
