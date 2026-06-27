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

class AutomationJobControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-automation");

    }

    @Test
    void createAndListAutomationJobs() throws Exception {
        mockMvc.perform(post("/api/v1/automation/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "每日研报",
                                  "promptText": "生成市场摘要",
                                  "cronExpression": "0 9 * * *"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("每日研报"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.nextRunAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/automation/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("每日研报"));
    }
}
