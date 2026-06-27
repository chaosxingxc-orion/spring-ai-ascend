package com.huawei.ascend.examples.workmate.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolRiskPolicyTest {

    @Test
    void detectsRmAsHighRisk() {
        var risk = ToolRiskPolicy.assess(
                WorkmateToolIds.bash(UUID.randomUUID().toString()), Map.of("command", "rm -rf tmp"));
        assertThat(risk.requiresApproval()).isTrue();
        assertThat(risk.level()).isEqualTo("HIGH");
    }

    @Test
    void lsIsSafe() {
        var risk = ToolRiskPolicy.assess(
                WorkmateToolIds.bash(UUID.randomUUID().toString()), Map.of("command", "ls -la"));
        assertThat(risk.requiresApproval()).isFalse();
    }

    @Test
    void mcpSubmitCreditMemoRequiresApproval() {
        var risk = ToolRiskPolicy.assess(
                "mcp__oa__submit_credit_memo",
                Map.of(
                        "operation", "提交授信审批",
                        "companyName", "星河科技有限公司",
                        "creditAmount", "5000万"));
        assertThat(risk.requiresApproval()).isTrue();
        assertThat(risk.level()).isEqualTo("HIGH");
        assertThat(risk.summary()).contains("星河");
    }

    @Test
    void mcpReadToolIsSafe() {
        var risk = ToolRiskPolicy.assess("mcp__qieman__SearchFunds", Map.of("keyword", "蓝筹"));
        assertThat(risk.requiresApproval()).isFalse();
    }
}
