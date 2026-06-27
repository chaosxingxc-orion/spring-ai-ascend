package com.huawei.ascend.examples.workmate.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.mock;

import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.approval.ApprovalGate.PendingApproval;
import com.huawei.ascend.examples.workmate.approval.ApprovalService;
import com.huawei.ascend.examples.workmate.approval.ToolRiskPolicy;
import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseRunEventMapperTest {

    private final SseRunEventMapper mapper = new SseRunEventMapper(new ObjectMapper());

    @Test
    void approvalPayloadIncludesSessionIdAndScopedToolName() {
        UUID sessionId = UUID.randomUUID();
        String bashId = WorkmateToolIds.bash(sessionId);
        ToolRiskPolicy.RiskAssessment risk =
                ToolRiskPolicy.assess(bashId, Map.of("command", "rm x"));
        PendingApproval pending = new ApprovalService(mock(AuditLedgerService.class)).register(
                sessionId, "task-1", bashId, risk, Map.of("command", "rm x"));

        Map<String, Object> payload = mapper.approvalPayload(pending);

        assertThat(payload.get("sessionId")).isEqualTo(sessionId.toString());
        assertThat(payload.get("tool")).isEqualTo(bashId);
        assertThat(payload.get("risk")).isEqualTo("HIGH");
        assertThat(payload.get("args")).isEqualTo(Map.of("command", "rm x"));
    }

    @Test
    void runCompletedEventBuilderExists() {
        SseEmitter.SseEventBuilder event = mapper.runCompleted();
        assertThat(event).isNotNull();
    }

    @Test
    void usageDeltaEventBuilderExists() {
        SseEmitter.SseEventBuilder event = mapper.usageDelta(10, 20, 100, 200, "deepseek-chat");
        assertThat(event).isNotNull();
    }
}
