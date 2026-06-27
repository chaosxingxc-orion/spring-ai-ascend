package com.huawei.ascend.examples.workmate.question;

import java.util.UUID;

public class QuestionNotFoundException extends RuntimeException {

    public QuestionNotFoundException(UUID questionId) {
        super("Question not found: " + questionId);
    }
}
