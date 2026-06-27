package com.huawei.ascend.examples.workmate.tools;

import com.huawei.ascend.examples.workmate.approval.ApprovalGate;
import com.huawei.ascend.examples.workmate.question.QuestionGate;
import java.util.UUID;

public record ToolExecutionContext(
        UUID sessionId, String taskId, ApprovalGate approvalGate, QuestionGate questionGate) {

    public ToolExecutionContext(UUID sessionId, String taskId, ApprovalGate approvalGate) {
        this(sessionId, taskId, approvalGate, null);
    }
}
