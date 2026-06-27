package com.huawei.ascend.examples.workmate.question;

import com.huawei.ascend.examples.workmate.config.WorkmateApprovalProperties;
import org.springframework.stereotype.Component;

@Component
public class QuestionGateFactory {

    private final UserQuestionService questionService;
    private final WorkmateApprovalProperties properties;

    public QuestionGateFactory(UserQuestionService questionService, WorkmateApprovalProperties properties) {
        this.questionService = questionService;
        this.properties = properties;
    }

    public QuestionGate createGate() {
        return new QuestionGate(questionService, properties.timeoutSeconds());
    }
}
