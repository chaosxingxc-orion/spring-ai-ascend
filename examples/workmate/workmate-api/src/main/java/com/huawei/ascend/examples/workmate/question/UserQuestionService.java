package com.huawei.ascend.examples.workmate.question;

import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.question.QuestionGate.AnswerResult;
import com.huawei.ascend.examples.workmate.question.QuestionGate.PendingQuestion;
import com.huawei.ascend.examples.workmate.question.dto.QuestionAnswerRequest;
import com.huawei.ascend.examples.workmate.question.dto.QuestionAnswerResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class UserQuestionService {

    private static final int TEXT_PREVIEW_MAX = 200;

    private final ConcurrentHashMap<UUID, PendingQuestion> pending = new ConcurrentHashMap<>();
    private final SessionPersistenceService sessionPersistenceService;

    public UserQuestionService(SessionPersistenceService sessionPersistenceService) {
        this.sessionPersistenceService = sessionPersistenceService;
    }

    /**
     * reference-aligned: if the user already answered this prompt in the session, reuse the answer
     * instead of emitting another question.required / persisting a duplicate card.
     */
    public Optional<QuestionGate.AnswerResult> findPriorAnswer(
            UUID sessionId, String question, List<String> options) {
        return sessionPersistenceService.findAnsweredQuestion(sessionId, question, options)
                .map(record -> new QuestionGate.AnswerResult(
                        List.copyOf(record.selections()), record.text(), false));
    }

    /**
     * Reuse a prior answer or apply implicit defaults so leader SOP re-runs do not re-prompt the user
     * after research mode / team setup is already locked in.
     */
    public Optional<QuestionGate.AnswerResult> resolveWithoutPrompting(
            UUID sessionId, String question, List<String> options) {
        Optional<QuestionGate.AnswerResult> prior = findPriorAnswer(sessionId, question, options);
        if (prior.isPresent()) {
            return prior;
        }
        List<String> normalizedOptions = normalizeOptions(options);
        String semanticKey = QuestionPromptNormalizer.semanticKey(question, normalizedOptions);
        if (!"hitl:research-params".equals(semanticKey)) {
            return Optional.empty();
        }
        if (!isResearchSetupLocked(sessionId)) {
            return Optional.empty();
        }
        return Optional.of(new QuestionGate.AnswerResult(
                implicitResearchParamSelections(normalizedOptions), null, false));
    }

    private boolean isResearchSetupLocked(UUID sessionId) {
        return sessionPersistenceService.hasAnsweredSemanticKey(sessionId, "hitl:research-mode")
                || sessionPersistenceService.hasRunEvent(sessionId, "team.build.completed");
    }

    private static List<String> implicitResearchParamSelections(List<String> options) {
        if (options.isEmpty()) {
            return List.of();
        }
        if (options.size() <= 4) {
            return List.copyOf(options);
        }
        List<String> picked = new ArrayList<>();
        pickFirstMatching(options, "报告语言：中文", picked);
        pickFirstMatching(options, "近1年", picked);
        pickFirstMatching(options, "APA", picked);
        pickFirstMatching(options, "Markdown", picked);
        if (picked.isEmpty()) {
            return List.copyOf(options);
        }
        return List.copyOf(picked);
    }

    private static void pickFirstMatching(List<String> options, String needle, List<String> picked) {
        for (String option : options) {
            if (option.contains(needle) && !picked.contains(option)) {
                picked.add(option);
                return;
            }
        }
    }

    public PendingQuestion register(
            UUID sessionId,
            String taskId,
            String toolName,
            String question,
            List<String> options,
            boolean allowFreeText,
            boolean multiSelect) {
        UUID id = UUID.randomUUID();
        List<String> normalizedOptions = normalizeOptions(options);
        PendingQuestion pendingQuestion = new PendingQuestion(
                id,
                sessionId,
                taskId,
                toolName,
                question == null ? "" : question.trim(),
                normalizedOptions,
                allowFreeText,
                multiSelect);
        pending.put(id, pendingQuestion);
        return pendingQuestion;
    }

    public QuestionAnswerResponse answer(UUID questionId, QuestionAnswerRequest request) {
        PendingQuestion pendingQuestion = requirePending(questionId);
        if (Boolean.TRUE.equals(request.skip())) {
            return completeSkip(pendingQuestion);
        }

        List<String> selections = request.selections() == null ? List.of() : request.selections();
        String text = request.text() == null ? null : request.text().trim();
        validateAnswer(pendingQuestion, selections, text);

        AnswerResult result = new AnswerResult(List.copyOf(selections), text, false);
        pendingQuestion.complete(result);
        pending.remove(questionId);
        sessionPersistenceService.recordQuestionAnswered(
                pendingQuestion.sessionId(),
                questionId,
                pendingQuestion.question(),
                pendingQuestion.options(),
                selections,
                previewText(text),
                false);
        return new QuestionAnswerResponse(
                questionId, pendingQuestion.sessionId(), List.copyOf(selections), text, false);
    }

    public QuestionAnswerResponse skip(UUID questionId) {
        PendingQuestion pendingQuestion = requirePending(questionId);
        return completeSkip(pendingQuestion);
    }

    public List<com.huawei.ascend.examples.workmate.question.dto.PendingQuestionResponse> listPending(UUID sessionId) {
        return pending.values().stream()
                .filter(item -> item.sessionId().equals(sessionId))
                .filter(item -> item.result().isEmpty())
                .map(com.huawei.ascend.examples.workmate.question.dto.PendingQuestionResponse::from)
                .toList();
    }

    public void expire(UUID questionId) {
        PendingQuestion pendingQuestion = pending.remove(questionId);
        if (pendingQuestion != null && pendingQuestion.result().isEmpty()) {
            pendingQuestion.complete(new AnswerResult(List.of(), null, false));
            sessionPersistenceService.recordQuestionCancelled(
                    pendingQuestion.sessionId(),
                    questionId,
                    pendingQuestion.question(),
                    pendingQuestion.options(),
                    pendingQuestion.allowFreeText(),
                    pendingQuestion.multiSelect(),
                    pendingQuestion.taskId());
        }
    }

    public Map<String, Object> requiredPayload(PendingQuestion pendingQuestion) {
        Map<String, Object> payload = basePayload(pendingQuestion);
        payload.put("status", "pending");
        return payload;
    }

    public Map<String, Object> cancelledPayload(PendingQuestion pendingQuestion) {
        Map<String, Object> payload = basePayload(pendingQuestion);
        payload.put("status", "cancelled");
        payload.put("reason", "timeout");
        return payload;
    }

    private static Map<String, Object> basePayload(PendingQuestion pendingQuestion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", pendingQuestion.id().toString());
        payload.put("sessionId", pendingQuestion.sessionId().toString());
        payload.put("question", pendingQuestion.question());
        payload.put("options", pendingQuestion.options());
        payload.put("allowFreeText", pendingQuestion.allowFreeText());
        payload.put("multiSelect", pendingQuestion.multiSelect());
        payload.put("toolName", pendingQuestion.toolName());
        payload.put(
                "messageId",
                QuestionPromptNormalizer.messageId(pendingQuestion.question(), pendingQuestion.options()));
        return payload;
    }

    private QuestionAnswerResponse completeSkip(PendingQuestion pendingQuestion) {
        AnswerResult result = new AnswerResult(List.of(), null, true);
        pendingQuestion.complete(result);
        pending.remove(pendingQuestion.id());
        sessionPersistenceService.recordQuestionAnswered(
                pendingQuestion.sessionId(),
                pendingQuestion.id(),
                pendingQuestion.question(),
                pendingQuestion.options(),
                List.of(),
                null,
                true);
        return new QuestionAnswerResponse(
                pendingQuestion.id(), pendingQuestion.sessionId(), List.of(), null, true);
    }

    private PendingQuestion requirePending(UUID questionId) {
        PendingQuestion pendingQuestion = pending.get(questionId);
        if (pendingQuestion == null) {
            throw new QuestionNotFoundException(questionId);
        }
        if (pendingQuestion.result().isPresent()) {
            throw new QuestionAlreadyAnsweredException(questionId);
        }
        return pendingQuestion;
    }

    private static void validateAnswer(PendingQuestion pendingQuestion, List<String> selections, String text) {
        if (!selections.isEmpty()) {
            for (String selection : selections) {
                if (!pendingQuestion.options().contains(selection)) {
                    throw new IllegalArgumentException("Invalid selection: " + selection);
                }
            }
            if (!pendingQuestion.multiSelect() && selections.size() > 1) {
                throw new IllegalArgumentException("Only one selection allowed");
            }
        }
        boolean hasSelection = !selections.isEmpty();
        boolean hasText = text != null && !text.isBlank();
        if (!hasSelection && !hasText) {
            throw new IllegalArgumentException("Answer must include a selection or text");
        }
        if (hasText && !pendingQuestion.allowFreeText()) {
            throw new IllegalArgumentException("Free text is not allowed for this question");
        }
        if (!hasSelection && pendingQuestion.options().isEmpty() && !pendingQuestion.allowFreeText()) {
            throw new IllegalArgumentException("Question requires a selection");
        }
    }

    private static List<String> normalizeOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String option : options) {
            if (option == null) {
                continue;
            }
            String trimmed = option.trim();
            if (!trimmed.isEmpty() && !normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }

    private static String previewText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.length() <= TEXT_PREVIEW_MAX ? text : text.substring(0, TEXT_PREVIEW_MAX) + "…";
    }
}
