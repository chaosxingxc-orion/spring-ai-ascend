package com.huawei.ascend.examples.workmate.cloud;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Verifies {@code workmate.cloud.enabled=false} blocks every /cloud endpoint via the interceptor. */
class CloudDisabledControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-cloud-off");
        WorkmateTestProperties.registerCloudEnabled(registry, false);
    }

    @Test
    void disabledCloudBlocksReads() throws Exception {
        mockMvc.perform(get("/api/v1/cloud/sessions")).andExpect(status().isForbidden());
    }

    @Test
    void disabledCloudBlocksCreates() throws Exception {
        mockMvc.perform(post("/api/v1/cloud/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expertId":"fund-analyst","title":"blocked"}
                                """))
                .andExpect(status().isForbidden());
    }
}
