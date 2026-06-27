package com.huawei.ascend.examples.workmate.question;

import com.huawei.ascend.examples.workmate.question.dto.QuestionAnswerRequest;
import com.huawei.ascend.examples.workmate.question.dto.QuestionAnswerResponse;
import com.huawei.ascend.examples.workmate.question.dto.PendingQuestionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class QuestionController {

    private final UserQuestionService questionService;

    public QuestionController(UserQuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping("/sessions/{sessionId}/pending-questions")
    public List<PendingQuestionResponse> listPending(@PathVariable UUID sessionId) {
        return questionService.listPending(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/questions/{questionId}/answer")
    public QuestionAnswerResponse answer(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @Valid @RequestBody(required = false) QuestionAnswerRequest request) {
        QuestionAnswerRequest payload = request == null ? new QuestionAnswerRequest(null, null, false) : request;
        QuestionAnswerResponse response = payload.skip() != null && payload.skip()
                ? questionService.skip(questionId)
                : questionService.answer(questionId, payload);
        if (!response.sessionId().equals(sessionId)) {
            throw new QuestionNotFoundException(questionId);
        }
        return response;
    }
}
