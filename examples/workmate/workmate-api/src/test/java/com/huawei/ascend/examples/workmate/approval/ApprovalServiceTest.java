package com.huawei.ascend.examples.workmate.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.huawei.ascend.examples.workmate.audit.AuditFailClosePolicy;
import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.approval.dto.ApprovalDecisionRequest;
import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApprovalServiceTest {

    private ApprovalService approvalService;
    private AuditLedgerService auditLedgerService;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        auditLedgerService = mock(AuditLedgerService.class);
        approvalService = new ApprovalService(auditLedgerService);
        sessionId = UUID.randomUUID();
    }

    @Test
    void listsAndDeniesPendingApproval() {
        String bashId = WorkmateToolIds.bash(sessionId);
        ToolRiskPolicy.RiskAssessment risk =
                ToolRiskPolicy.assess(bashId, Map.of("command", "rm hitl-test.txt"));
        ApprovalGate.PendingApproval pending = approvalService.register(
                sessionId, "task-1", bashId, risk, Map.of("command", "rm hitl-test.txt"));

        assertThat(approvalService.listPending(sessionId)).hasSize(1);
        assertThat(approvalService.listPending(sessionId).getFirst().approvalId()).isEqualTo(pending.id());

        approvalService.decide(pending.id(), new ApprovalDecisionRequest("deny", null, false));

        verify(auditLedgerService)
                .recordFailClose(eq(sessionId), eq("task-1"), eq(AuditFailClosePolicy.APPROVAL_DECIDED), any());
        assertThat(approvalService.listPending(sessionId)).isEmpty();
        assertThat(pending.verdict()).contains(ApprovalGate.Verdict.DENIED);
    }

    @Test
    void approvesAndRemovesFromPending() {
        String bashId = WorkmateToolIds.bash(sessionId);
        ToolRiskPolicy.RiskAssessment risk =
                ToolRiskPolicy.assess(bashId, Map.of("command", "rm foo.txt"));
        ApprovalGate.PendingApproval pending = approvalService.register(
                sessionId, "task-2", bashId, risk, Map.of("command", "rm foo.txt"));

        approvalService.decide(pending.id(), new ApprovalDecisionRequest("approve", "ONCE", false));

        assertThat(approvalService.listPending(sessionId)).isEmpty();
        assertThat(pending.verdict()).contains(ApprovalGate.Verdict.APPROVED);
    }

    @Test
    void alwaysAllowRememberedForSession() {
        String bashId = WorkmateToolIds.bash(sessionId);
        ToolRiskPolicy.RiskAssessment risk =
                ToolRiskPolicy.assess(bashId, Map.of("command", "rm always.txt"));
        ApprovalGate.PendingApproval pending = approvalService.register(
                sessionId, "task-3", bashId, risk, Map.of("command", "rm always.txt"));

        approvalService.decide(pending.id(), new ApprovalDecisionRequest("approve", null, true));

        assertThat(approvalService.isAlwaysAllowed(sessionId, bashId, risk.summary())).isTrue();
    }

    @Test
    void scopeSessionRememberedForSession() {
        String bashId = WorkmateToolIds.bash(sessionId);
        ToolRiskPolicy.RiskAssessment risk =
                ToolRiskPolicy.assess(bashId, Map.of("command", "rm session.txt"));
        ApprovalGate.PendingApproval pending = approvalService.register(
                sessionId, "task-4", bashId, risk, Map.of("command", "rm session.txt"));

        approvalService.decide(pending.id(), new ApprovalDecisionRequest("approve", "SESSION", false));

        assertThat(approvalService.isAlwaysAllowed(sessionId, bashId, risk.summary())).isTrue();
    }

    @Test
    void scopeOnceDoesNotRememberForSession() {
        String bashId = WorkmateToolIds.bash(sessionId);
        ToolRiskPolicy.RiskAssessment risk =
                ToolRiskPolicy.assess(bashId, Map.of("command", "rm once.txt"));
        ApprovalGate.PendingApproval pending = approvalService.register(
                sessionId, "task-5", bashId, risk, Map.of("command", "rm once.txt"));

        approvalService.decide(pending.id(), new ApprovalDecisionRequest("approve", "ONCE", false));

        assertThat(approvalService.isAlwaysAllowed(sessionId, bashId, risk.summary())).isFalse();
    }

    @Test
    void mcpApprovalUsesDedicatedAuditEvent() {
        ToolRiskPolicy.RiskAssessment risk = ToolRiskPolicy.assess(
                "mcp__oa__submit_credit_memo",
                Map.of("customerName", "XX企业", "creditAmount", "5000万"));
        ApprovalGate.PendingApproval pending = approvalService.register(
                sessionId,
                "task-mcp",
                "mcp__oa__submit_credit_memo",
                risk,
                Map.of("customerName", "XX企业"));

        approvalService.decide(pending.id(), new ApprovalDecisionRequest("deny", null, false));

        verify(auditLedgerService)
                .recordFailClose(
                        eq(sessionId),
                        eq("task-mcp"),
                        eq(AuditFailClosePolicy.MCP_APPROVAL_DECIDED),
                        any());
    }

    @Test
    void decideUnknownApprovalThrows() {
        assertThatThrownBy(() ->
                        approvalService.decide(UUID.randomUUID(), new ApprovalDecisionRequest("deny", null, false)))
                .isInstanceOf(ApprovalNotFoundException.class);
    }
}
