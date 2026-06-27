package com.huawei.ascend.examples.workmate.tenant;
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

class TenantQuotaControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-quota");

    }

    @Test
    void returnsTenantQuota() throws Exception {
        mockMvc.perform(get("/api/v1/tenant/quota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("default"))
                .andExpect(jsonPath("$.metrics[0].key").value("activeSessions"))
                .andExpect(jsonPath("$.metrics[1].key").value("monthlyTokens"));
    }
}
