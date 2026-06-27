package com.huawei.ascend.examples.workmate.chat;

import com.huawei.ascend.examples.workmate.question.QuestionPromptNormalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Read-side projection: merge run_events into chat messages and dedupe question cards. */
@Component
class ChatTimelineProjector {

    private final SessionMessageRepository messageRepository;
    private final RunEventWriter eventWriter;
    private final SessionQuestionLookup questionLookup;

    ChatTimelineProjector(
            SessionMessageRepository messageRepository,
            RunEventWriter eventWriter,
            SessionQuestionLookup questionLookup) {
        this.messageRepository = messageRepository;
        this.eventWriter = eventWriter;
        this.questionLookup = questionLookup;
    }

    List<Map<String, Object>> listMessages(UUID sessionId) {
        List<Map<String, Object>> raw = messageRepository.findBySessionIdAndSupersededFalseOrderBySeqAsc(sessionId).stream()
                .map(questionLookup::toMessageView)
                .filter(payload -> !"team".equals(payload.get("surface")))
                .map(ChatTimelineProjector::repairQuestionStatus)
                .toList();
        return dedupeQuestionMessages(mergeQuestionsFromRunEvents(sessionId, raw));
    }

    private List<Map<String, Object>> mergeQuestionsFromRunEvents(
            UUID sessionId, List<Map<String, Object>> messages) {
        Set<String> knownMessageIds = new HashSet<>();
        for (Map<String, Object> message : messages) {
            if ("question".equals(message.get("kind"))) {
                knownMessageIds.add(String.valueOf(message.get("id")));
            }
        }
        int maxSeq = eventWriter.maxSeq(sessionId);
        List<Map<String, Object>> merged = new ArrayList<>(messages);
        for (RunEvent event : eventWriter.findBySessionIdAndEventNameOrderBySeqAsc(sessionId, "question.required")) {
            Map<String, Object> data = eventWriter.readEventPayload(event);
            String question = SessionMessageJson.readOptionalString(data.get("question"));
            if (question == null) {
                continue;
            }
            List<String> options = SessionMessageJson.readStringList(data.get("options"));
            String messageId = SessionMessageJson.readOptionalString(data.get("messageId"));
            if (messageId == null || messageId.isBlank()) {
                messageId = QuestionPromptNormalizer.messageId(question, options);
            }
            if (knownMessageIds.contains(messageId)) {
                continue;
            }
            if (questionLookup.findAnsweredQuestion(sessionId, question, options).isPresent()) {
                continue;
            }
            UUID questionId = parseUuid(SessionMessageJson.readOptionalString(data.get("questionId")));
            if (questionId == null) {
                questionId = UUID.randomUUID();
            }
            boolean allowFreeText = !Boolean.FALSE.equals(data.get("allowFreeText"));
            boolean multiSelect = Boolean.TRUE.equals(data.get("multiSelect"));
            String status = event.getSeq() < maxSeq - 2 ? "cancelled" : "pending";
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", messageId);
            item.put("kind", "question");
            item.put("questionId", questionId.toString());
            item.put("question", question);
            item.put("options", options);
            item.put("allowFreeText", allowFreeText);
            item.put("multiSelect", multiSelect);
            item.put("status", status);
            item.put("seq", event.getSeq());
            merged.add(item);
            knownMessageIds.add(messageId);
        }
        return merged;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static List<Map<String, Object>> dedupeQuestionMessages(List<Map<String, Object>> messages) {
        Map<String, Map<String, Object>> winnerByPrompt = new LinkedHashMap<>();
        for (Map<String, Object> message : messages) {
            if (!"question".equals(message.get("kind"))) {
                continue;
            }
            Object questionObj = message.get("question");
            if (!(questionObj instanceof String prompt)) {
                continue;
            }
            String key = QuestionPromptNormalizer.semanticKey(
                    prompt, SessionMessageJson.readStringList(message.get("options")));
            Map<String, Object> current = winnerByPrompt.get(key);
            if (current == null || questionMessageRank(message) > questionMessageRank(current)) {
                winnerByPrompt.put(key, message);
            }
        }
        if (winnerByPrompt.isEmpty()) {
            return messages;
        }
        Set<String> winnerIds = winnerByPrompt.values().stream()
                .map(item -> String.valueOf(item.get("id")))
                .collect(Collectors.toCollection(HashSet::new));
        List<Map<String, Object>> deduped = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            if ("question".equals(message.get("kind"))) {
                if (winnerIds.contains(String.valueOf(message.get("id")))) {
                    deduped.add(message);
                }
            } else {
                deduped.add(message);
            }
        }
        return deduped;
    }

    static Map<String, Object> repairQuestionStatus(Map<String, Object> message) {
        if (!"question".equals(message.get("kind"))) {
            return message;
        }
        if (!"cancelled".equals(String.valueOf(message.getOrDefault("status", "")))) {
            return message;
        }
        if (!SessionMessageJson.readStringList(message.get("selections")).isEmpty()
                || SessionMessageJson.readOptionalString(message.get("answerText")) != null) {
            message.put("status", "answered");
        }
        return message;
    }

    private static int questionMessageRank(Map<String, Object> message) {
        int seq = message.get("seq") instanceof Number number ? number.intValue() : 0;
        String status = String.valueOf(message.getOrDefault("status", ""));
        int statusWeight = switch (status) {
            case "pending" -> 2_000_000;
            case "answered", "skipped" -> 1_000_000;
            case "cancelled" -> -1_000_000;
            default -> 0;
        };
        return statusWeight + seq;
    }
}
