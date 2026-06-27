package com.huawei.ascend.examples.workmate.question.dto;

import com.huawei.ascend.examples.workmate.question.QuestionGate.PendingQuestion;
import java.util.List;
import java.util.UUID;

public record PendingQuestionResponse(
        UUID questionId,
        UUID sessionId,
        String question,
        List<String> options,
        boolean allowFreeText,
        boolean multiSelect,
        String toolName) {

    public static PendingQuestionResponse from(PendingQuestion pending) {
        return new PendingQuestionResponse(
                pending.id(),
                pending.sessionId(),
                pending.question(),
                pending.options(),
                pending.allowFreeText(),
                pending.multiSelect(),
                pending.toolName());
    }
}
