package com.huawei.ascend.examples.workmate.office;
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

/** Verifies {@code workmate.studio.enabled=false} blocks every /studio endpoint via the interceptor. */
class StudioDisabledControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-studio-off");
        WorkmateTestProperties.registerStudioEnabled(registry, false);
        WorkmateTestProperties.registerOfficeRoot(registry);
    }

    @Test
    void disabledStudioBlocksReads() throws Exception {
        mockMvc.perform(get("/api/v1/studio/experts")).andExpect(status().isForbidden());
    }

    @Test
    void disabledStudioBlocksWrites() throws Exception {
        mockMvc.perform(post("/api/v1/studio/experts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"x","name":"x","description":"x","expertType":"agent","promptContent":"x"}
                                """))
                .andExpect(status().isForbidden());
    }
}
