package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateTeamRuntimeProperties;
import com.huawei.ascend.examples.workmate.office.dto.StudioCoordinationWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioLeadWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioTeamMemberWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioTeamWriteRequest;
import com.huawei.ascend.examples.workmate.team.agent.TeamRuntimeRouter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudioTeamWriteServiceTest {

    private static final Path OFFICE_ROOT = Path.of("../office").toAbsolutePath().normalize();

    @TempDir
    Path dataDir;

    private StudioTeamWriteService teamWriteService;
    private ExpertRegistry expertRegistry;

    @BeforeEach
    void setUp() {
        OfficeImportPaths importPaths = new OfficeImportPaths(new WorkmateDataProperties(dataDir.toString()));
        expertRegistry = new ExpertRegistry(new WorkmateOfficeProperties(OFFICE_ROOT.toString()), importPaths);
        TeamRuntimeRouter runtimeRouter;
        {
            WorkmateTeamRuntimeProperties properties = new WorkmateTeamRuntimeProperties();
            properties.setDefaultRuntime("openjiuwen-team");
            runtimeRouter = new TeamRuntimeRouter(expertRegistry, properties);
        }
        teamWriteService = new StudioTeamWriteService(expertRegistry, new StudioDraftStore(importPaths), runtimeRouter);
    }

    @Test
    void getsExistingTeamView() {
        var view = teamWriteService.getTeam("gpt-researcher-team");
        assertThat(view.team().summary().id()).isEqualTo("gpt-researcher-team");
        assertThat(view.members()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(view.runtimePreview().coordinationPattern()).isEqualTo("orchestrator");
    }

    @Test
    void includesMemberPromptContentFromLinkedExperts() {
        var view = teamWriteService.getTeam("ncre-expert");
        assertThat(view.team().promptContent()).isNotBlank();
        assertThat(view.members()).isNotEmpty();
        assertThat(view.members().get(0).promptContent()).isNotBlank();
        assertThat(view.members().get(0).promptFile()).isEqualTo("prompt.md");
        assertThat(view.members().get(0).expertYaml()).contains("id: ncre-expert__ncre-level1");
    }

    @Test
    void createsTeamDraftWithMembers() {
        var view = teamWriteService.createTeam(sampleTeamRequest("studio-demo-team", "orchestrator", "sequential"));
        assertThat(view.team().source()).isEqualTo(OfficeAssetSource.DRAFT);
        assertThat(view.members()).hasSize(2);
        assertThat(expertRegistry.findExpert("studio-demo-team")).isPresent();
        assertThat(expertRegistry.findExpert("studio-demo-team__researcher")).isPresent();
    }

    @Test
    void updatesCoordinationPattern() {
        teamWriteService.createTeam(sampleTeamRequest("studio-pipeline-team", "orchestrator", "sequential"));
        var updated = teamWriteService.updateCoordination(
                "studio-pipeline-team",
                new StudioCoordinationWriteRequest("pipeline", null, null));
        assertThat(updated.team().summary().coordination().pattern()).isEqualTo("pipeline");
    }

    @Test
    void addsAndRemovesMember() {
        teamWriteService.createTeam(sampleTeamRequest("studio-member-team", "orchestrator", "sequential"));
        var withMember = teamWriteService.addMember(
                "studio-member-team",
                new StudioTeamMemberWriteRequest(
                        "reviewer",
                        "Reviewer",
                        "studio-member-team__reviewer",
                        "Reviewer",
                        3,
                        null,
                        Map.of("zh", "审稿人"),
                        "local",
                        "You review drafts."));
        assertThat(withMember.members()).hasSize(3);

        var reduced = teamWriteService.deleteMember("studio-member-team", "reviewer");
        assertThat(reduced.members()).hasSize(2);
    }

    @Test
    void rejectsTeamWithSingleMember() {
        assertThatThrownBy(() -> teamWriteService.createTeam(new StudioTeamWriteRequest(
                        "studio-bad-team",
                        "Bad Team",
                        "Desc",
                        "Lead prompt",
                        "",
                        "custom",
                        List.of("draft", "team"),
                        "sequential",
                        "openjiuwen-team",
                        new StudioCoordinationWriteRequest("orchestrator", null, null),
                        new StudioLeadWriteRequest("Lead", Map.of("zh", "负责人"), null),
                        null,
                        List.of(new StudioTeamMemberWriteRequest(
                                "only",
                                "Only",
                                "studio-bad-team__only",
                                "Role",
                                1,
                                null,
                                Map.of(),
                                "local",
                                "Prompt")),
                        null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 members");
    }

    private static StudioTeamWriteRequest sampleTeamRequest(String id, String pattern, String collaboration) {
        return new StudioTeamWriteRequest(
                id,
                "Studio Team " + id,
                "Team for studio test",
                "You are the team lead.",
                "",
                "custom",
                List.of("draft", "team"),
                collaboration,
                "openjiuwen-team",
                new StudioCoordinationWriteRequest(pattern, null, null),
                new StudioLeadWriteRequest("Lead", Map.of("zh", "负责人"), null),
                null,
                List.of(
                        new StudioTeamMemberWriteRequest(
                                "researcher",
                                "Researcher",
                                id + "__researcher",
                                "Researcher",
                                1,
                                null,
                                Map.of("zh", "研究员"),
                                "local",
                                "You research topics."),
                        new StudioTeamMemberWriteRequest(
                                "writer",
                                "Writer",
                                id + "__writer",
                                "Writer",
                                2,
                                null,
                                Map.of("zh", "写作者"),
                                "local",
                                "You write reports.")),
                50);
    }
}
