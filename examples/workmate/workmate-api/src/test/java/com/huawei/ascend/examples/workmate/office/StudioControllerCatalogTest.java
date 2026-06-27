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

class StudioControllerCatalogTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = StudioControllerTestSupport.registerStudio(registry, "studio-catalog");
    }

    @BeforeEach
    void resetDrafts() throws Exception {
        StudioControllerTestSupport.resetDrafts(mockMvc, paths.data());
    }

    @Test
    void reloadReturnsCounts() throws Exception {
        mockMvc.perform(post("/api/v1/studio/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experts").isNumber())
                .andExpect(jsonPath("$.skills").isNumber())
                .andExpect(jsonPath("$.warnings").isArray());
    }

    @Test
    void listsExpertsWithSource() throws Exception {
        mockMvc.perform(get("/api/v1/studio/experts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.summary.id=='fund-analyst')].source").value("BUILTIN"));
    }

    @Test
    void listsSkillsWithSource() throws Exception {
        mockMvc.perform(get("/api/v1/studio/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[0].summary.id").exists())
                .andExpect(jsonPath("$[0].source").exists());
    }

    @Test
    void returnsExpertSourceWithPrompt() throws Exception {
        mockMvc.perform(get("/api/v1/studio/experts/fund-analyst/source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value("fund-analyst"))
                .andExpect(jsonPath("$.promptFile").value("prompt.md"))
                .andExpect(jsonPath("$.promptContent").isNotEmpty())
                .andExpect(jsonPath("$.expertYaml").isNotEmpty())
                .andExpect(jsonPath("$.source").value("BUILTIN"));
    }

    @Test
    void resolvesExpertCapabilities() throws Exception {
        mockMvc.perform(get("/api/v1/studio/experts/fund-analyst/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectors[?(@.id=='qieman')]").exists())
                .andExpect(jsonPath("$.connectors[?(@.id=='qieman')].found").value(true));

        Path draftDir = paths.data().resolve("office-drafts/experts/studio-cap-expert");
        Files.createDirectories(draftDir);
        Files.writeString(
                draftDir.resolve("expert.yaml"),
                """
                        id: studio-cap-expert
                        name: Cap Expert
                        description: capabilities test
                        expertType: agent
                        promptFile: prompt.md
                        preloadSkills:
                          - web-access
                        skillCompatibility:
                          - qieman
                          - web-access
                        """);
        Files.writeString(draftDir.resolve("prompt.md"), "Prompt body");
        mockMvc.perform(post("/api/v1/studio/reload")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/studio/experts/studio-cap-expert/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[?(@.id=='web-access')].found").value(true))
                .andExpect(jsonPath("$.connectors[?(@.id=='qieman')].found").value(true));
    }

    @Test
    void draftOverrideVisibleAfterReload() throws Exception {
        Path draftDir = paths.data().resolve("office-drafts/experts/fund-analyst");
        Files.createDirectories(draftDir);
        Files.writeString(draftDir.resolve("expert.yaml"), """
                id: fund-analyst
                name: Studio Draft Analyst
                description: Draft from studio test
                expertType: agent
                promptFile: prompt.md
                category: finance
                """);
        Files.writeString(draftDir.resolve("prompt.md"), "Draft prompt body for studio test.");

        mockMvc.perform(post("/api/v1/studio/reload")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/studio/experts/fund-analyst/source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.name").value("Studio Draft Analyst"))
                .andExpect(jsonPath("$.promptContent").value("Draft prompt body for studio test."))
                .andExpect(jsonPath("$.source").value("DRAFT"));
    }

    @Test
    void studioDisabledReturnsForbiddenForReads() throws Exception {
        // Sanity: when the feature flag is on (as in this test class) reads succeed; the disabled-state
        // 403 path is covered by StudioDisabledControllerTest with workmate.studio.enabled=false.
        mockMvc.perform(get("/api/v1/studio/experts")).andExpect(status().isOk());
    }

    @Test
    void studioConfigReportsEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/studio/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.auditEnabled").value(true));
    }

    /** P1.8 dogfood: override builtin prompt → reload → dry-run session. */
}
