package com.huawei.ascend.examples.workmate.connector;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class ConnectorControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-connector");
        WorkmateTestProperties.registerOfficeRoot(registry);
        WorkmateTestProperties.registerMcpEnabled(registry, true);
        registry.add("WORKMATE_MCP_DOCS_FS_ENABLED", () -> "false");
        registry.add("WORKMATE_MCP_QIEMAN_ENABLED", () -> "false");
    }

    @Test
    void listsConfiguredConnectors() throws Exception {
        mockMvc.perform(get("/api/v1/connectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='qieman')]").exists())
                .andExpect(jsonPath("$[?(@.id=='qieman' && @.requiresAuth==true)]").exists())
                .andExpect(jsonPath("$[?(@.id=='github')]").exists())
                .andExpect(jsonPath("$[?(@.id=='github' && @.source=='marketplace')]").exists());
    }
}
