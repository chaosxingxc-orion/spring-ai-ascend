package com.huawei.ascend.examples.workmate.memory;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

class MemoryControllerIntegrationTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @Autowired
    private SessionPersistenceService sessionPersistenceService;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-memory-api");
        WorkmateTestProperties.registerOfficeRoot(registry);
    }

    @Test
    void rememberCapturesHeuristicMemoryFromSessionMessages() throws Exception {
        mockMvc.perform(patch("/api/v1/memory/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"autoCapture":false}
                                """))
                .andExpect(status().isOk());

        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Memory test"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id");

        UUID id = UUID.fromString(sessionId);
        sessionPersistenceService.beginRun(id, UUID.randomUUID().toString(), "我是产品经理，偏好简洁中文回复");

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/remember"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("captured"))
                .andExpect(jsonPath("$.entries.length()").value(1));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasContent").value(true))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("产品经理")));
    }
}
