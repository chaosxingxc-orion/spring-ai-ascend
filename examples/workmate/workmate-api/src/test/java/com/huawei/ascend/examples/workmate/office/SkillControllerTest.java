package com.huawei.ascend.examples.workmate.office;
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

class SkillControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-skill");
        WorkmateTestProperties.registerOfficeRoot(registry);
    }

    @Test
    void listsSkillsAndInstalls() throws Exception {
        mockMvc.perform(get("/api/v1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='excel-handler')]").exists())
                .andExpect(jsonPath("$[?(@.id=='skill-authoring' && @.installed==true)]").exists());

        mockMvc.perform(post("/api/v1/skills/excel-handler/install")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("excel-handler"))
                .andExpect(jsonPath("$.installed").value(true));
    }
}
