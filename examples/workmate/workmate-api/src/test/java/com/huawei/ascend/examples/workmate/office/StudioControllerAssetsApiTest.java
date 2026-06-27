package com.huawei.ascend.examples.workmate.office;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class StudioControllerAssetsApiTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = StudioControllerTestSupport.registerStudio(registry, "studio-assets");
    }

    @BeforeEach
    void resetDrafts() throws Exception {
        StudioControllerTestSupport.resetDrafts(mockMvc, paths.data());
    }

    @Test
    void rejectsTeamMemberExpertIdTraversal() throws Exception {
        mockMvc.perform(post("/api/v1/studio/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "traversal-team",
                                  "name": "Traversal Team",
                                  "description": "member expertId traversal attempt",
                                  "promptContent": "Lead prompt.",
                                  "members": [
                                    {"id": "m1", "name": "M1", "expertId": "../../../../tmp/evil", "promptContent": "p"},
                                    {"id": "m2", "name": "M2", "promptContent": "p"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsAndUpdatesSkillViaApi() throws Exception {
        mockMvc.perform(post("/api/v1/studio/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "studio-api-skill",
                                  "name": "API Skill",
                                  "description": "Skill via API",
                                  "category": "custom",
                                  "skillContent": "# API skill body"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value("studio-api-skill"))
                .andExpect(jsonPath("$.source").value("DRAFT"));

        mockMvc.perform(put("/api/v1/studio/skills/studio-api-skill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API Skill Updated",
                                  "description": "Updated skill",
                                  "category": "custom",
                                  "skillContent": "# Updated body"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.name").value("API Skill Updated"))
                .andExpect(jsonPath("$.skillContent").value("# Updated body\n"));
    }

    @Test
    void listsSkillDirectoryFiles() throws Exception {
        Path draftDir = paths.data().resolve("office-drafts/skills/studio-dir-skill");
        Files.createDirectories(draftDir.resolve("scripts"));
        Files.writeString(
                draftDir.resolve("skill.yaml"),
                """
                        id: studio-dir-skill
                        name: Dir Skill
                        description: Skill with scripts
                        category: custom
                        """);
        Files.writeString(draftDir.resolve("SKILL.md"), "# Dir skill");
        Files.writeString(draftDir.resolve("scripts/recalc.py"), "print('recalc')\n");

        mockMvc.perform(post("/api/v1/studio/reload")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/studio/skills/studio-dir-skill/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.path=='scripts/recalc.py')]").exists())
                .andExpect(jsonPath("$[?(@.path=='scripts/recalc.py')].textReadable").value(true));

        mockMvc.perform(get("/api/v1/studio/skills/studio-dir-skill/files/content")
                        .param("path", "scripts/recalc.py"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("scripts/recalc.py"))
                .andExpect(jsonPath("$.content").value("print('recalc')\n"))
                .andExpect(jsonPath("$.binary").value(false))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    void returnsBuiltinTeamView() throws Exception {
        mockMvc.perform(get("/api/v1/studio/teams/gpt-researcher-team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.summary.id").value("gpt-researcher-team"))
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.members.length()").value(6))
                .andExpect(jsonPath("$.runtimePreview.resolvedRuntime").isNotEmpty());
    }

    @Test
    void createsTeamDraftViaApi() throws Exception {
        mockMvc.perform(post("/api/v1/studio/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "studio-api-team",
                                  "name": "API Team",
                                  "description": "Team via studio API",
                                  "promptContent": "You are the team lead.",
                                  "teamRuntime": "openjiuwen-team",
                                  "coordination": { "pattern": "orchestrator" },
                                  "lead": { "name": "Lead", "title": { "zh": "主理人" } },
                                  "members": [
                                    {
                                      "id": "a",
                                      "name": "Member A",
                                      "expertId": "studio-api-team__a",
                                      "role": "A",
                                      "order": 1,
                                      "promptContent": "You are A."
                                    },
                                    {
                                      "id": "b",
                                      "name": "Member B",
                                      "expertId": "studio-api-team__b",
                                      "role": "B",
                                      "order": 2,
                                      "promptContent": "You are B."
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.summary.id").value("studio-api-team"))
                .andExpect(jsonPath("$.team.source").value("DRAFT"))
                .andExpect(jsonPath("$.members.length()").value(2));

        mockMvc.perform(get("/api/v1/studio/teams/studio-api-team"))
                .andExpect(jsonPath("$.team.source").value("DRAFT"));
    }

    @Test
    void updatesTeamCoordinationViaApi() throws Exception {
        mockMvc.perform(post("/api/v1/studio/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "studio-coord-team",
                                  "name": "Coord Team",
                                  "description": "Coord test",
                                  "promptContent": "Lead prompt",
                                  "coordination": { "pattern": "orchestrator" },
                                  "lead": { "name": "Lead", "title": { "zh": "主理人" } },
                                  "members": [
                                    { "id": "a", "name": "A", "expertId": "studio-coord-team__a", "order": 1 },
                                    { "id": "b", "name": "B", "expertId": "studio-coord-team__b", "order": 2 }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/studio/teams/studio-coord-team/coordination")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pattern": "pipeline",
                                  "acceptanceCriteria": "Done when all members respond"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.summary.coordination.pattern").value("pipeline"));
    }

    @Test
    void previewsTeamRuntimeViaApi() throws Exception {
        mockMvc.perform(get("/api/v1/studio/teams/gpt-researcher-team/runtime-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinationPattern").value("orchestrator"))
                .andExpect(jsonPath("$.hasLead").value(true));
    }

    @Test
    void welcomeYamlUpdateDiffRollback() throws Exception {
        mockMvc.perform(get("/api/v1/studio/welcome/source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("BUILTIN"))
                .andExpect(jsonPath("$.welcomeYaml").isNotEmpty());

        mockMvc.perform(put("/api/v1/studio/welcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "welcomeYaml": "activeProfile: workmate\\nprofiles:\\n  workmate:\\n    hero:\\n      headline: \\"Studio Draft Welcome\\"\\n"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DRAFT"));

        mockMvc.perform(get("/api/v1/studio/welcome/diff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDraft").value(true))
                .andExpect(jsonPath("$.changedFields[0]").value("welcomeYaml"));

        mockMvc.perform(post("/api/v1/studio/reload")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/studio/welcome/rollback"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/studio/welcome/source"))
                .andExpect(jsonPath("$.source").value("BUILTIN"));
    }

    @Test
    void playbookUpdateDiffRollback() throws Exception {
        mockMvc.perform(get("/api/v1/studio/playbooks/daily-report/source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("BUILTIN"))
                .andExpect(jsonPath("$.title").value("工作总结日报"));

        mockMvc.perform(put("/api/v1/studio/playbooks/daily-report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Studio Draft Report",
                                  "description": "Draft playbook",
                                  "accent": "#34C759",
                                  "initPrompt": "Draft init prompt for playbook studio test.",
                                  "placements": ["home-best-practice"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DRAFT"));

        mockMvc.perform(get("/api/v1/studio/playbooks/daily-report/diff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDraft").value(true))
                .andExpect(jsonPath("$.changedFields").isArray());

        mockMvc.perform(post("/api/v1/studio/reload")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/studio/playbooks/daily-report/rollback"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/studio/playbooks/daily-report/source"))
                .andExpect(jsonPath("$.source").value("BUILTIN"))
                .andExpect(jsonPath("$.title").value("工作总结日报"));
    }

    @Test
    void dogfoodTeamOrchestratorToPipeline() throws Exception {
        String createBody = """
                {
                  "id": "studio-dogfood-team",
                  "name": "Dogfood Team",
                  "description": "P2.7 smoke",
                  "promptContent": "Lead prompt",
                  "teamRuntime": "workmate-orchestrator",
                  "coordination": { "pattern": "orchestrator" },
                  "lead": { "name": "Lead", "title": { "zh": "主理人" } },
                  "members": [
                    { "id": "a", "name": "A", "expertId": "studio-dogfood-team__a", "order": 1, "promptContent": "A" },
                    { "id": "b", "name": "B", "expertId": "studio-dogfood-team__b", "order": 2, "promptContent": "B" },
                    { "id": "c", "name": "C", "expertId": "studio-dogfood-team__c", "order": 3, "promptContent": "C" }
                  ]
                }
                """;
        mockMvc.perform(post("/api/v1/studio/teams").contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.summary.coordination.pattern").value("orchestrator"));

        mockMvc.perform(post("/api/v1/studio/experts/studio-dogfood-team/dry-run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty());

        mockMvc.perform(put("/api/v1/studio/teams/studio-dogfood-team/coordination")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "pattern": "pipeline" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.summary.coordination.pattern").value("pipeline"));

        mockMvc.perform(get("/api/v1/studio/teams/studio-dogfood-team/runtime-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinationPattern").value("pipeline"));
    }

    private static byte[] buildAgentZip(String yaml, String prompt) throws Exception {
        return StudioControllerTestSupport.buildAgentZip(yaml, prompt);
    }

}
