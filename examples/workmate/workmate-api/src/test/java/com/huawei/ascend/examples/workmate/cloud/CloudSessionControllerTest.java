package com.huawei.ascend.examples.workmate.cloud;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class CloudSessionControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-cloud");
        WorkmateTestProperties.registerCloudEnabled(registry, true);
    }

    @Test
    void createCloudSessionWithManifest() throws Exception {
        mockMvc.perform(post("/api/v1/cloud/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertId": "fund-analyst",
                                  "title": "云基金研究"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expertId").value("fund-analyst"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.runtimeBaseUrl").isNotEmpty())
                .andExpect(jsonPath("$.linkedSessionId").isNotEmpty())
                .andExpect(jsonPath("$.sandboxId").isNotEmpty());

        mockMvc.perform(get("/api/v1/cloud/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("云基金研究"));
    }

    @Test
    void healthAndByLinkedSession() throws Exception {
        String body = mockMvc.perform(post("/api/v1/cloud/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertId": "fund-analyst",
                                  "title": "健康检查"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = new ObjectMapper().readTree(body);
        String cloudId = node.get("id").asText();
        String linkedId = node.get("linkedSessionId").asText();

        mockMvc.perform(get("/api/v1/cloud/sessions/by-linked/" + linkedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedSessionId").value(linkedId));

        mockMvc.perform(get("/api/v1/cloud/sessions/" + cloudId + "/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cloudSessionId").value(cloudId))
                .andExpect(jsonPath("$.healthy").isBoolean());
    }
}
