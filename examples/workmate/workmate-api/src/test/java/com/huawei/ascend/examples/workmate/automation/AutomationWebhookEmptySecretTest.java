package com.huawei.ascend.examples.workmate.automation;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** A webhook channel enabled with an empty secret must refuse requests instead of running open. */
class AutomationWebhookEmptySecretTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-webhook-empty");
        WorkmateTestProperties.registerWebhookChannel(registry, "generic", true, "");
    }

    @Test
    void enabledChannelWithEmptySecretRefusesRequest() throws Exception {
        mockMvc.perform(post("/api/v1/automation/webhooks/generic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\",\"title\":\"t\"}"))
                .andExpect(status().is5xxServerError());
    }
}
