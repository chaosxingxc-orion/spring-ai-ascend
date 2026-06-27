package com.huawei.ascend.examples.workmate.session;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class WorkspaceControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-workspace");
        WorkmateTestProperties.registerMcpEnabled(registry, false);
    }

    @Test
    void listsWorkspacePresets() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='default')]").exists())
                .andExpect(jsonPath("$[?(@.id=='office-demo')]").exists());
    }

    @Test
    void createsSessionWithSharedWorkspace() throws Exception {
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/sessions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title":"Shared task","workspacePath":"presets/office-demo"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workspaceKey").value("presets/office-demo"));
    }
}
