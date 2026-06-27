package com.huawei.ascend.examples.workmate.market;
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

class MarketplaceControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-market");
        WorkmateTestProperties.registerOfficeRoot(registry);
    }

    @Test
    void listsBuiltinMarketplaceAndPlugins() throws Exception {
        mockMvc.perform(get("/api/v1/marketplaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='workmate-builtin')]").exists());

        mockMvc.perform(get("/api/v1/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].marketplaceId").value("workmate-builtin"));
    }

    @Test
    void installsAndUninstallsPlugin() throws Exception {
        mockMvc.perform(post("/api/v1/plugins/workmate-builtin/web-access/install"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installed").value(true));

        mockMvc.perform(post("/api/v1/plugins/workmate-builtin/web-access/uninstall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installed").value(false));
    }

    @Test
    void addsCustomMarketplace() throws Exception {
        mockMvc.perform(post("/api/v1/marketplaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"demo-market","name":"Demo Market","sourceType":"directory","sourceUri":"/tmp/demo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("demo-market"));
    }
}
