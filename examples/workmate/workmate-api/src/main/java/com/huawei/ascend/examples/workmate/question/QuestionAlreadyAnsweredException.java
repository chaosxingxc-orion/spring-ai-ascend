package com.huawei.ascend.examples.workmate.question;

import java.util.UUID;

public class QuestionAlreadyAnsweredException extends RuntimeException {

    public QuestionAlreadyAnsweredException(UUID questionId) {
        super("Question already answered: " + questionId);
    }
}
