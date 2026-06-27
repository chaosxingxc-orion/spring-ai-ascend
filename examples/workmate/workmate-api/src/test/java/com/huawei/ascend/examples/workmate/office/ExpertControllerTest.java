package com.huawei.ascend.examples.workmate.office;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.assertj.core.api.Assertions.assertThat;
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

class ExpertControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-expert");
        WorkmateTestProperties.registerOfficeRoot(registry);
    }

    @Test
    void listsExpertsAndCreatesSessionWithExpert() throws Exception {
        mockMvc.perform(get("/api/v1/experts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='fund-analyst')]").exists());

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Fund task","expertId":"fund-analyst"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expertId").value("fund-analyst"));
    }
}
