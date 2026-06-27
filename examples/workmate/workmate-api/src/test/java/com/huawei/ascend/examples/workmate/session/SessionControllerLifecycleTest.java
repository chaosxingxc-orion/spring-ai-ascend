package com.huawei.ascend.examples.workmate.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.ascend.examples.workmate.agent.PlanPayload;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

class SessionControllerLifecycleTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "session-lifecycle");
    }

    @Test
    void listSessionSummariesOmitsUsageRollup() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Summary row"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/sessions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sessionId))
                .andExpect(jsonPath("$[0].title").value("Summary row"))
                .andExpect(jsonPath("$[0].workspaceKey").isNotEmpty())
                .andExpect(jsonPath("$[0].promptTokens").doesNotExist())
                .andExpect(jsonPath("$[0].workspaceRoot").doesNotExist())
                .andExpect(jsonPath("$[0].officeArtifactRoot").doesNotExist());
    }

    @Test
    void createSessionReturnsIdAndCreatesWorkspaceDirectory() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Demo task"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Demo task"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.permissionMode").value("CRAFT"))
                .andExpect(jsonPath("$.promptTokens").value(0))
                .andExpect(jsonPath("$.completionTokens").value(0))
                .andExpect(jsonPath("$.workspaceRoot").isNotEmpty())
                .andReturn();

        String sessionId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        Path workspace = paths.workspace().resolve(sessionId);
        assertThat(Files.isDirectory(workspace)).isTrue();

        mockMvc.perform(get("/api/v1/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Demo task"));

        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sessionId));
    }

    @Test
    void createSessionWithAskMode() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ask only","permissionMode":"ASK"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissionMode").value("ASK"));
    }

    @Test
    void createSessionWithEnabledConnectors() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Fund task","expertId":"fund-analyst","enabledConnectorIds":["qieman-mcp"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabledConnectorIds[0]").value("qieman"));
    }

    @Test
    void createSessionWithEnabledSkills() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Skill task","enabledSkillIds":["skill-authoring","open-lesson"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabledSkillIds[0]").value("skill-authoring"))
                .andExpect(jsonPath("$.enabledSkillIds[1]").value("open-lesson"));
    }

    @Test
    void confirmPlanSwitchesToCraft() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plan task","permissionMode":"PLAN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissionMode").value("PLAN"))
                .andExpect(jsonPath("$.permissionModeBeforePlan").value("CRAFT"))
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/plan/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionMode").value("CRAFT"));
    }

    @Test
    void getMissingSessionReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound());
    }

}
