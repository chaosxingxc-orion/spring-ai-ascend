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

class SessionControllerMutationTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @Autowired
    private SessionPersistenceService sessionPersistenceService;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "session-mutation");
    }

    @Test
    void listMessagesReturnsPersistedChatItems() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Chat history"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        UUID sessionUuid = UUID.fromString(sessionId);
        // beginRun persists the user message; the assistant message is created lazily on first delta.
        var ctx = sessionPersistenceService.beginRun(
                sessionUuid, UUID.randomUUID().toString(), "hello from server");
        sessionPersistenceService.appendAssistantDelta(ctx, "hi from assistant");
        sessionPersistenceService.finalizeAssistant(ctx);

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("user"))
                .andExpect(jsonPath("$[0].text").value("hello from server"))
                .andExpect(jsonPath("$[1].kind").value("assistant"));
    }

    @Test
    void updateMetadataPinsAndArchivesSession() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Pin me"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pinned":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.archivedAt").isEmpty());

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archived":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(false))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archived":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isEmpty());
    }

    @Test
    void updateMetadataPermissionMode() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Mode switch"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissionMode").value("CRAFT"))
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionMode":"ASK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionMode").value("ASK"));

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionMode":"PLAN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionMode").value("PLAN"))
                .andExpect(jsonPath("$.permissionModeBeforePlan").value("ASK"));

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionMode":"CRAFT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("plan/confirm")));
    }

    @Test
    void updateMetadataPermissionModeRejectedWhenArchived() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Archived mode"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archived":true}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionMode":"ASK"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Archived")));
    }

    @Test
    void updateMetadataModelAndEffort() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Model task"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelId":"local-model","effort":"HIGH"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("local-model"))
                .andExpect(jsonPath("$.effort").value("HIGH"));
    }

    @Test
    void updateSessionConnectors() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"MCP session"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/connectors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabledConnectorIds":["qieman","dingtalk"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledConnectorIds[0]").value("qieman"))
                .andExpect(jsonPath("$.enabledConnectorIds[1]").value("dingtalk"));

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/connectors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabledConnectorIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledConnectorIds").isEmpty());
    }

    @Test
    void updateSessionSkills() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Skill session"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabledSkillIds":["skill-authoring","open-lesson"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledSkillIds[0]").value("skill-authoring"))
                .andExpect(jsonPath("$.enabledSkillIds[1]").value("open-lesson"));

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabledSkillIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledSkillIds").isEmpty());
    }

    @Test
    void updateMetadataEnabledConnectorIds() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"MCP session"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabledConnectorIds":["qieman","dingtalk"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledConnectorIds[0]").value("qieman"))
                .andExpect(jsonPath("$.enabledConnectorIds[1]").value("dingtalk"));

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabledConnectorIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledConnectorIds").isEmpty());
    }

    @Test
    void updateMetadataExpertId() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Summon switch"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expertId":"fund-analyst"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expertId").value("fund-analyst"));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kind == 'expert-switched')].toExpertId").value("fund-analyst"));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/run-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'expert.switched')].data.toExpertId").value("fund-analyst"));
    }

    @Test
    void expertTransitionInSession() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Handoff","expertId":"prd-writer"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/expert-transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expertId":"fund-analyst","mode":"SUMMON_IN_SESSION"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expertId").value("fund-analyst"));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/run-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'expert.switched')].data.newGeneration").value(1));
    }

    @Test
    void updatePlanPersistsPlanUpdateRunEvent() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plan edit","permissionMode":"PLAN"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        UUID sessionUuid = UUID.fromString(sessionId);
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context =
                sessionPersistenceService.beginRun(sessionUuid, runId, "plan me");
        sessionPersistenceService.recordPlan(
                context,
                new PlanPayload(
                        "plan-1",
                        "Demo",
                        List.of(new PlanPayload.PlanStep("step-1", "First", "pending"))));

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/plans/plan-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated","steps":[{"id":"step-1","title":"Revised","status":"pending"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/run-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'plan.update')].data.planId").value("plan-1"));
    }

    @Test
    void ingestAcpUpdatesPersistsRunEvents() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"ACP ingest"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/acp/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"sessionUpdate":"agent_message_chunk","content":{"text":"hel"}},
                                  {"sessionUpdate":"agent_message_chunk","content":{"text":"lo"}}
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("message.delta"))
                .andExpect(jsonPath("$[0].data.text.preview").value("hello"))
                .andExpect(jsonPath("$[0].seq").isNumber());

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/run-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("message.delta"))
                .andExpect(jsonPath("$[0].data.text.preview").value("hello"));
    }

    @Test
    void ingestAcpNdjsonPersistsRunEvents() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"ACP ndjson ingest"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/acp/ingest/ndjson")
                        .contentType("application/x-ndjson")
                        .content("""
                                {"sessionUpdate":"agent_message_chunk","content":{"text":"side"}}
                                {"sessionUpdate":"agent_message_chunk","content":{"text":"car"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("message.delta"))
                .andExpect(jsonPath("$[0].data.text.preview").value("sidecar"));
    }

    @Test
    void uploadAttachmentStoresImageInWorkspace() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        byte[] png = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, (byte) 0x1f, 0x15, (byte) 0xc4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0a, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte) 0x9c, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, (byte) 0xb4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, (byte) 0xae,
            0x42, 0x60, (byte) 0x82
        };

        mockMvc.perform(multipart("/api/v1/sessions/" + sessionId + "/attachments")
                        .file(new MockMultipartFile("file", "shot.png", "image/png", png)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(org.hamcrest.Matchers.startsWith("uploads/")))
                .andExpect(jsonPath("$.name").value("shot.png"))
                .andExpect(jsonPath("$.mime").value("image/png"));
    }
}
