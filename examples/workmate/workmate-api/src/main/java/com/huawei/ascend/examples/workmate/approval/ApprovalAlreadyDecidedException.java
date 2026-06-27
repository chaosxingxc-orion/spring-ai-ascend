package com.huawei.ascend.examples.workmate.approval;

import java.util.UUID;

public class ApprovalAlreadyDecidedException extends RuntimeException {

    public ApprovalAlreadyDecidedException(UUID id) {
        super("Approval already decided: " + id);
    }
}
