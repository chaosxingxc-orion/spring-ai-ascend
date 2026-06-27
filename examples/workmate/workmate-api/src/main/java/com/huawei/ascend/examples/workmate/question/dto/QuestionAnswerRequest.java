package com.huawei.ascend.examples.workmate.question.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record QuestionAnswerRequest(
        List<@Size(max = 32) String> selections,
        @Size(max = 4000) String text,
        Boolean skip) {
}
