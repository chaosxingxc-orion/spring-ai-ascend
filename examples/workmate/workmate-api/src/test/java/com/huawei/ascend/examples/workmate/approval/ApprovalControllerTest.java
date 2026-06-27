package com.huawei.ascend.examples.workmate.approval;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class ApprovalControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @Autowired
    private ApprovalService approvalService;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-approval");

    }

    @Test
    void listsPendingAndDecidesViaRest() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String bashId = WorkmateToolIds.bash(sessionId);
        ToolRiskPolicy.RiskAssessment risk =
                ToolRiskPolicy.assess(bashId, Map.of("command", "rm probe.txt"));
        ApprovalGate.PendingApproval pending = approvalService.register(
                sessionId, "task-rest", bashId, risk, Map.of("command", "rm probe.txt"));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/pending-approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].approvalId").value(pending.id().toString()))
                .andExpect(jsonPath("$[0].toolName").value(bashId));

        mockMvc.perform(post("/api/v1/approvals/" + pending.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"deny\",\"always\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("deny"));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/pending-approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
