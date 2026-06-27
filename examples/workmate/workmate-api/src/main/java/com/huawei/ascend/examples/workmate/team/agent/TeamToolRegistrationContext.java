package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.approval.ApprovalGate;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.question.QuestionGate;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;

public record TeamToolRegistrationContext(
        WorkmateSession session,
        ExpertDefinition team,
        String parentRunId,
        ApprovalGate approvalGate,
        QuestionGate questionGate) {

    public static String sharedAgentTag(java.util.UUID sessionId) {
        return "workmate-team-" + sessionId;
    }
}
