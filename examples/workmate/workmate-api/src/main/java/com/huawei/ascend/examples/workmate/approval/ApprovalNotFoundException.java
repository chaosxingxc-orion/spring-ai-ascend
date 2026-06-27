package com.huawei.ascend.examples.workmate.approval;

import java.util.UUID;

public class ApprovalNotFoundException extends RuntimeException {

    public ApprovalNotFoundException(UUID id) {
        super("Approval not found: " + id);
    }
}
