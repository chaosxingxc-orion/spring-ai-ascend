package com.huawei.ascend.examples.workmate.approval;

import com.huawei.ascend.examples.workmate.audit.AuditFailClosePolicy;
import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.approval.ApprovalGate.PendingApproval;
import com.huawei.ascend.examples.workmate.approval.dto.ApprovalDecisionRequest;
import com.huawei.ascend.examples.workmate.approval.dto.ApprovalResponse;
import com.huawei.ascend.examples.workmate.approval.dto.PendingApprovalResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ApprovalService {

    private final ConcurrentHashMap<UUID, PendingApproval> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Boolean>> alwaysAllowed = new ConcurrentHashMap<>();
    private final AuditLedgerService auditLedgerService;

    public ApprovalService(AuditLedgerService auditLedgerService) {
        this.auditLedgerService = auditLedgerService;
    }

    public PendingApproval register(
            UUID sessionId,
            String taskId,
            String toolName,
            ToolRiskPolicy.RiskAssessment risk,
            Map<String, Object> args) {
        UUID id = UUID.randomUUID();
        PendingApproval approval = new PendingApproval(id, sessionId, taskId, toolName, risk, Map.copyOf(args));
        pending.put(id, approval);
        return approval;
    }

    public ApprovalResponse decide(UUID approvalId, ApprovalDecisionRequest request) {
        PendingApproval approval = pending.get(approvalId);
        if (approval == null) {
            throw new ApprovalNotFoundException(approvalId);
        }
        if (approval.verdict().isPresent()) {
            throw new ApprovalAlreadyDecidedException(approvalId);
        }

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("approvalId", approvalId.toString());
        auditPayload.put("decision", request.decision());
        ApprovalDecisionScope scope = ApprovalDecisionScope.fromRequest(request);
        auditPayload.put("scope", scope.name());
        auditPayload.put("always", request.always());
        auditPayload.put("toolName", approval.toolName());
        auditPayload.put("sessionId", approval.sessionId().toString());
        String auditEvent = approvalEventName(approval.toolName());
        auditLedgerService.recordFailClose(
                approval.sessionId(),
                approval.taskId(),
                auditEvent,
                auditPayload);

        boolean approve = scope != ApprovalDecisionScope.DENY;
        ApprovalGate.Verdict verdict = approve ? ApprovalGate.Verdict.APPROVED : ApprovalGate.Verdict.DENIED;
        approval.complete(verdict);

        if (approve && scope == ApprovalDecisionScope.SESSION) {
            rememberAlways(approval.sessionId(), approval.toolName(), approval.risk().summary());
        }

        pending.remove(approvalId);
        return new ApprovalResponse(approvalId, request.decision(), approval.sessionId());
    }

    public List<PendingApprovalResponse> listPending(UUID sessionId) {
        return pending.values().stream()
                .filter(p -> p.sessionId().equals(sessionId))
                .filter(p -> p.verdict().isEmpty())
                .map(PendingApprovalResponse::from)
                .toList();
    }

    public void expire(UUID approvalId) {
        PendingApproval approval = pending.remove(approvalId);
        if (approval != null && approval.verdict().isEmpty()) {
            approval.complete(ApprovalGate.Verdict.TIMED_OUT);
        }
    }

    public boolean isAlwaysAllowed(UUID sessionId, String toolName, String summary) {
        ConcurrentHashMap<String, Boolean> sessionRules = alwaysAllowed.get(sessionId);
        if (sessionRules == null) {
            return false;
        }
        return Boolean.TRUE.equals(sessionRules.get(alwaysKey(toolName, summary)));
    }

    private void rememberAlways(UUID sessionId, String toolName, String summary) {
        alwaysAllowed.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>())
                .put(alwaysKey(toolName, summary), Boolean.TRUE);
    }

    private static String alwaysKey(String toolName, String summary) {
        return toolName + "::" + summary;
    }

    private static String approvalEventName(String toolName) {
        if (toolName != null && toolName.startsWith("mcp__")) {
            return AuditFailClosePolicy.MCP_APPROVAL_DECIDED;
        }
        return AuditFailClosePolicy.APPROVAL_DECIDED;
    }
}