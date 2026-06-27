package com.huawei.ascend.examples.workmate.approval;

import com.huawei.ascend.examples.workmate.config.WorkmateApprovalProperties;
import org.springframework.stereotype.Component;

@Component
public class ApprovalGateFactory {

    private final ApprovalService approvalService;
    private final WorkmateApprovalProperties properties;

    public ApprovalGateFactory(ApprovalService approvalService, WorkmateApprovalProperties properties) {
        this.approvalService = approvalService;
        this.properties = properties;
    }

    public ApprovalGate createGate() {
        return new ApprovalGate(approvalService, properties.timeoutSeconds());
    }
}
