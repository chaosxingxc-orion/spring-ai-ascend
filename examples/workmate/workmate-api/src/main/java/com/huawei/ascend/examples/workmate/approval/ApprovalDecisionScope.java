package com.huawei.ascend.examples.workmate.approval;

import com.huawei.ascend.examples.workmate.approval.dto.ApprovalDecisionRequest;

/** Trust granularity for sandbox intercept (G25). */
public enum ApprovalDecisionScope {
    ONCE,
    SESSION,
    DENY;

    public static ApprovalDecisionScope fromRequest(ApprovalDecisionRequest request) {
        if ("deny".equalsIgnoreCase(request.decision())) {
            return DENY;
        }
        String scope = request.scope();
        if (scope != null && !scope.isBlank()) {
            return valueOf(scope.trim().toUpperCase());
        }
        // Backward compat: always=true → SESSION; approve without always → ONCE
        if (Boolean.TRUE.equals(request.always())) {
            return SESSION;
        }
        return ONCE;
    }
}
