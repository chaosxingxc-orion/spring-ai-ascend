package com.huawei.ascend.examples.workmate.question.dto;

import java.util.List;
import java.util.UUID;

public record QuestionAnswerResponse(
        UUID questionId,
        UUID sessionId,
        List<String> selections,
        String text,
        boolean skipped) {
}
