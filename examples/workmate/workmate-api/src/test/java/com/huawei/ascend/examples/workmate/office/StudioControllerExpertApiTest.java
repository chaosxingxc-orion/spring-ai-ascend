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

class StudioControllerExpertApiTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = StudioControllerTestSupport.registerStudio(registry, "studio-expert");
    }

    @BeforeEach
    void resetDrafts() throws Exception {
        StudioControllerTestSupport.resetDrafts(mockMvc, paths.data());
    }

    @Test
    void createsNewExpertDraftViaApi() throws Exception {
        mockMvc.perform(post("/api/v1/studio/experts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "studio-api-agent",
                                  "name": "API Agent",
                                  "description": "Created via studio API",
                                  "expertType": "agent",
                                  "promptContent": "You are an API-created agent.",
                                  "category": "custom",
                                  "tags": ["draft", "api-test"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value("studio-api-agent"))
                .andExpect(jsonPath("$.source").value("DRAFT"))
                .andExpect(jsonPath("$.promptContent").value("You are an API-created agent.\n"));
    }

    @Test
    void rejectsPromptFilePathTraversal() throws Exception {
        mockMvc.perform(post("/api/v1/studio/experts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "traversal-agent",
                                  "name": "Traversal",
                                  "description": "path traversal attempt",
                                  "expertType": "agent",
                                  "promptContent": "x",
                                  "promptFile": "../../../../../../tmp/poison.md",
                                  "category": "custom"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesBuiltinExpertViaApi() throws Exception {
        mockMvc.perform(put("/api/v1/studio/experts/fund-analyst")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API Overridden Analyst",
                                  "description": "Updated via PUT",
                                  "expertType": "agent",
                                  "promptContent": "Overridden via studio PUT.",
                                  "category": "finance"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.name").value("API Overridden Analyst"))
                .andExpect(jsonPath("$.source").value("DRAFT"));

        mockMvc.perform(get("/api/v1/studio/experts/fund-analyst/source"))
                .andExpect(jsonPath("$.promptContent").value("Overridden via studio PUT.\n"));
    }

    @Test
    void deletesDraftAndRestoresBuiltinViaApi() throws Exception {
        mockMvc.perform(put("/api/v1/studio/experts/fund-analyst")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Temporary Draft",
                                  "description": "Will be deleted",
                                  "expertType": "agent",
                                  "promptContent": "Temporary prompt.",
                                  "category": "finance"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/studio/experts/fund-analyst")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/studio/experts/fund-analyst/source"))
                .andExpect(jsonPath("$.source").value("BUILTIN"))
                .andExpect(jsonPath("$.summary.name").value("基金研究助手"));
    }

    @Test
    void forksExpertViaApi() throws Exception {
        mockMvc.perform(post("/api/v1/studio/experts/fund-analyst/fork"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value("fund-analyst"))
                .andExpect(jsonPath("$.source").value("DRAFT"))
                .andExpect(jsonPath("$.promptContent").isNotEmpty());
    }

    @Test
    void validatesExpertRequest() throws Exception {
        mockMvc.perform(post("/api/v1/studio/experts/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "bad id",
                                  "name": "",
                                  "description": "Desc",
                                  "promptContent": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void validatesExistingExpertByIdInBody() throws Exception {
        mockMvc.perform(post("/api/v1/studio/experts/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "fund-analyst",
                                  "name": "Fund Analyst",
                                  "description": "Desc",
                                  "promptContent": "Prompt body"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void dryRunCreatesSession() throws Exception {
        mockMvc.perform(post("/api/v1/studio/experts/fund-analyst/dry-run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.expertId").value("fund-analyst"));
    }

    @Test
    void dryRunAcceptsMarketCompoundExpertId() throws Exception {
        mockMvc.perform(post("/api/v1/studio/experts/gpt-researcher-team__topic-researcher/dry-run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.expertId").value("gpt-researcher-team__topic-researcher"));
    }

    @Test
    void rejectsDuplicateExpertCreate() throws Exception {
        String body = """
                {
                  "id": "studio-dup-expert",
                  "name": "Dup",
                  "description": "Dup expert",
                  "expertType": "agent",
                  "promptContent": "Prompt"
                }
                """;
        mockMvc.perform(post("/api/v1/studio/experts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/studio/experts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importsAgentZipToDraftViaStudioApi() throws Exception {
        byte[] zipBytes = buildAgentZip(
                """
                        id: studio-zip-agent
                        name: Zip Agent
                        description: Imported via studio zip
                        expertType: agent
                        category: custom
                        """,
                "You are a zip-imported studio agent.");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                                "/api/v1/studio/experts/import/zip")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "agent.zip", "application/zip", zipBytes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value("studio-zip-agent"))
                .andExpect(jsonPath("$.source").value("DRAFT"));
    }

    @Test
    void exportOfficeDraftViaApi() throws Exception {
        mockMvc.perform(put("/api/v1/studio/experts/fund-analyst")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Export Analyst",
                                  "description": "Export test",
                                  "expertType": "agent",
                                  "promptContent": "Export prompt.",
                                  "category": "finance"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/studio/export/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expertCount").value(1));

        mockMvc.perform(get("/api/v1/studio/experts/fund-analyst/export"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", org.hamcrest.Matchers.containsString("application/zip")));
    }

    @Test
    void expertDiffAndRollbackViaApi() throws Exception {
        mockMvc.perform(put("/api/v1/studio/experts/fund-analyst")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Diff Test Analyst",
                                  "description": "Draft for diff",
                                  "expertType": "agent",
                                  "promptContent": "Draft prompt for diff test.",
                                  "category": "finance"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/studio/experts/fund-analyst/diff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDraft").value(true))
                .andExpect(jsonPath("$.hasBaseline").value(true))
                .andExpect(jsonPath("$.baselineSource").value("BUILTIN"))
                .andExpect(jsonPath("$.changedFields").isArray());

        mockMvc.perform(post("/api/v1/studio/experts/fund-analyst/rollback"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/studio/experts/fund-analyst/source"))
                .andExpect(jsonPath("$.source").value("BUILTIN"))
                .andExpect(jsonPath("$.summary.name").value("基金研究助手"));
    }

    @Test
    void publishExpertMarksMetaPublished() throws Exception {
        mockMvc.perform(put("/api/v1/studio/experts/fund-analyst")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Publish Test Analyst",
                                  "description": "P3.2 smoke",
                                  "expertType": "agent",
                                  "promptContent": "Publish draft prompt.",
                                  "category": "finance"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/studio/experts/fund-analyst/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.assetType").value("expert"))
                .andExpect(jsonPath("$.assetId").value("fund-analyst"));

        mockMvc.perform(get("/api/v1/studio/draft-meta/expert/fund-analyst"))
                .andExpect(jsonPath("$.status").value("published"));
    }

    @Test
    void dogfoodBuiltinExpertOverrideReloadDryRun() throws Exception {
        mockMvc.perform(put("/api/v1/studio/experts/fund-analyst")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Dogfood Analyst",
                                  "description": "P1.8 smoke",
                                  "expertType": "agent",
                                  "promptContent": "Dogfood draft prompt.",
                                  "category": "finance"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/studio/reload")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/studio/experts/fund-analyst/source"))
                .andExpect(jsonPath("$.promptContent").value("Dogfood draft prompt.\n"));

        mockMvc.perform(post("/api/v1/studio/experts/fund-analyst/dry-run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty());
    }

    /** P2.7 dogfood: 3-member orchestrator team → pipeline switch. */
    private static byte[] buildAgentZip(String yaml, String prompt) throws Exception {
        return StudioControllerTestSupport.buildAgentZip(yaml, prompt);
    }

}
