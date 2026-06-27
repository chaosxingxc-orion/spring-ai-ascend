package com.huawei.ascend.examples.workmate.approval;

import com.huawei.ascend.examples.workmate.approval.dto.ApprovalDecisionRequest;
import com.huawei.ascend.examples.workmate.approval.dto.ApprovalResponse;
import com.huawei.ascend.examples.workmate.approval.dto.PendingApprovalResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/sessions/{sessionId}/pending-approvals")
    public List<PendingApprovalResponse> listPending(@PathVariable UUID sessionId) {
        return approvalService.listPending(sessionId);
    }

    @PostMapping("/approvals/{approvalId}")
    public ApprovalResponse decide(
            @PathVariable UUID approvalId,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        return approvalService.decide(approvalId, request);
    }
}
