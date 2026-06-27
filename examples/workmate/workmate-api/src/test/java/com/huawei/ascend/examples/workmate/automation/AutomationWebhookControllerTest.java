package com.huawei.ascend.examples.workmate.automation;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class AutomationWebhookControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-webhook");
        WorkmateTestProperties.registerWebhookChannel(registry, "generic", true, "test-secret");
        WorkmateTestProperties.registerWebhookChannel(registry, "feishu", true, "test-secret");
    }

    @Test
    void webhookConfigListsChannels() throws Exception {
        mockMvc.perform(get("/api/v1/automation/webhooks/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels[?(@.id=='generic')].enabled").value(true))
                .andExpect(jsonPath("$.channels[?(@.id=='generic')].secretConfigured").value(true))
                .andExpect(jsonPath("$.channels[?(@.id=='feishu')].enabled").value(true));
    }

    @Test
    void feishuChallenge() throws Exception {
        mockMvc.perform(post("/api/v1/automation/webhooks/feishu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge\":\"abc123\",\"token\":\"test-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").value("abc123"));

        mockMvc.perform(get("/api/v1/automation/webhooks/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channel").value("feishu"))
                .andExpect(jsonPath("$[0].outcome").value("CHALLENGE"));
    }

    @Test
    void genericWebhookRequiresSecretWhenConfigured() throws Exception {
        mockMvc.perform(post("/api/v1/automation/webhooks/generic")
                        .header("X-WorkMate-Webhook-Secret", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello webhook\",\"title\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
    }
}
