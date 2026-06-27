package com.huawei.ascend.examples.workmate.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.question.AnsweredQuestionRecord;
import com.huawei.ascend.examples.workmate.question.QuestionPromptNormalizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SessionQuestionLookup {

    private final SessionMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    SessionQuestionLookup(SessionMessageRepository messageRepository, ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    Map<String, Object> toMessageView(SessionMessage message) {
        Map<String, Object> payload = SessionMessageJson.readPayload(message, objectMapper);
        payload.put("seq", message.getSeq());
        return payload;
    }

    Optional<AnsweredQuestionRecord> findAnsweredQuestion(
            UUID sessionId, String question, List<String> options) {
        String requestedKey = QuestionPromptNormalizer.semanticKey(question, options);
        if (requestedKey.isBlank()) {
            return Optional.empty();
        }
        for (SessionMessage message : messageRepository.findBySessionIdAndSupersededFalseOrderBySeqAsc(sessionId)) {
            Map<String, Object> payload = SessionMessageJson.readPayload(message, objectMapper);
            if (!"question".equals(payload.get("kind"))) {
                continue;
            }
            Object questionObj = payload.get("question");
            if (!(questionObj instanceof String prompt)) {
                continue;
            }
            List<String> storedOptions = SessionMessageJson.readStringList(payload.get("options"));
            if (!requestedKey.equals(QuestionPromptNormalizer.semanticKey(prompt, storedOptions))) {
                continue;
            }
            if (!"answered".equals(payload.get("status"))) {
                continue;
            }
            return Optional.of(new AnsweredQuestionRecord(
                    SessionMessageJson.readStringList(payload.get("selections")),
                    SessionMessageJson.readOptionalString(payload.get("answerText"))));
        }
        return Optional.empty();
    }

    boolean hasAnsweredSemanticKey(UUID sessionId, String semanticKey) {
        if (semanticKey == null || semanticKey.isBlank()) {
            return false;
        }
        for (SessionMessage message : messageRepository.findBySessionIdAndSupersededFalseOrderBySeqAsc(sessionId)) {
            Map<String, Object> payload = SessionMessageJson.readPayload(message, objectMapper);
            if (!"question".equals(payload.get("kind")) || !"answered".equals(payload.get("status"))) {
                continue;
            }
            Object questionObj = payload.get("question");
            if (!(questionObj instanceof String prompt)) {
                continue;
            }
            List<String> storedOptions = SessionMessageJson.readStringList(payload.get("options"));
            if (semanticKey.equals(QuestionPromptNormalizer.semanticKey(prompt, storedOptions))) {
                return true;
            }
        }
        return false;
    }

    String resolveQuestionMessageId(UUID sessionId, UUID questionId) {
        String target = questionId.toString();
        for (SessionMessage message : messageRepository.findBySessionIdAndSupersededFalseOrderBySeqAsc(sessionId)) {
            Map<String, Object> payload = SessionMessageJson.readPayload(message, objectMapper);
            if ("question".equals(payload.get("kind")) && target.equals(payload.get("questionId"))) {
                return message.getId();
            }
        }
        return null;
    }

    PlanRef findPlanMessage(UUID sessionId, String planId) {
        for (SessionMessage message : messageRepository.findBySessionIdAndSupersededFalseOrderBySeqAsc(sessionId)) {
            Map<String, Object> payload = SessionMessageJson.readPayload(message, objectMapper);
            if ("plan".equals(payload.get("kind")) && planId.equals(payload.get("planId"))) {
                return new PlanRef(message.getId(), message.getRunId());
            }
        }
        return null;
    }

    record PlanRef(String messageId, String runId) {}
}
