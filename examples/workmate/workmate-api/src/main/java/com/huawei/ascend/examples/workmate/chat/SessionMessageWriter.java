package com.huawei.ascend.examples.workmate.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.agent.PlanPayload;
import com.huawei.ascend.examples.workmate.agent.PlanPayload.PlanStep;
import com.huawei.ascend.examples.workmate.question.QuestionPromptNormalizer;
import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Persists chat timeline rows ({@link SessionMessage}) during an agent run. */
@Component
class SessionMessageWriter {

    private final SessionMessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final SessionSequenceAllocator sequenceAllocator;
    private final SessionQuestionLookup questionLookup;

    SessionMessageWriter(
            SessionMessageRepository messageRepository,
            ObjectMapper objectMapper,
            SessionSequenceAllocator sequenceAllocator,
            SessionQuestionLookup questionLookup) {
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.sequenceAllocator = sequenceAllocator;
        this.questionLookup = questionLookup;
    }

    RunPersistenceContext beginSubRun(
            UUID sessionId, String runId, String parentRunId, String memberId, String memberName) {
        return RunPersistenceContext.forMember(sessionId, runId, parentRunId, memberId, memberName);
    }

    RunPersistenceContext beginRun(
            UUID sessionId,
            String runId,
            String userMessage,
            List<Map<String, Object>> mentionMaps,
            List<Map<String, Object>> attachmentMaps) {
        String userMessageId = UUID.randomUUID().toString();

        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("text", userMessage);
        if (mentionMaps != null && !mentionMaps.isEmpty()) {
            userPayload.put("mentions", mentionMaps);
        }
        if (attachmentMaps != null && !attachmentMaps.isEmpty()) {
            userPayload.put("attachments", attachmentMaps);
        }
        saveMessage(sessionId, runId, SessionMessageJson.chatItem("user", userMessageId, userPayload));
        return new RunPersistenceContext(sessionId, runId, null, null, null, null);
    }

    String appendAssistantDelta(RunPersistenceContext context, String text) {
        if (context == null || text == null || text.isEmpty()) {
            return context == null ? null : context.assistantMessageId();
        }
        if (RunPersistenceContext.isTeamSurface(context)) {
            return null;
        }
        context.appendAssistantText(text);
        if (context.assistantMessageId() == null) {
            String newMessageId = UUID.randomUUID().toString();
            context.beginAssistantTurn(newMessageId);
            saveMessage(
                    context.sessionId(),
                    context.runId(),
                    SessionMessageJson.chatItem("assistant", newMessageId, Map.of("text", "")));
        }
        context.appendCurrentTurnText(text);
        updateMessagePayload(
                context.sessionId(),
                context.assistantMessageId(),
                Map.of("text", context.currentTurnText()));
        return context.assistantMessageId();
    }

    String recordToolStart(
            RunPersistenceContext context, String toolName, Object args, String preferredToolCallId) {
        if (context == null) {
            return null;
        }
        String toolCallId = (preferredToolCallId != null && !preferredToolCallId.isBlank())
                ? preferredToolCallId
                : UUID.randomUUID().toString();
        context.pushRunningTool(toolName, toolCallId);
        if (RunPersistenceContext.isTeamSurface(context)) {
            return toolCallId;
        }
        context.closeAssistantTurn();
        if (WorkmateToolIds.isAskUserQuestion(toolName)) {
            return toolCallId;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", toolCallId);
        item.put("kind", "tool");
        item.put("toolName", toolName);
        item.put("toolCallId", toolCallId);
        item.put("status", "running");
        if (args != null) {
            item.put("args", args);
        }
        saveMessage(context.sessionId(), context.runId(), item);
        return toolCallId;
    }

    String recordToolEnd(RunPersistenceContext context, String toolName, Object result, boolean failed) {
        if (context == null) {
            return null;
        }
        String toolCallId = context.popRunningTool(toolName);
        if (toolCallId == null) {
            return null;
        }
        if (RunPersistenceContext.isTeamSurface(context)) {
            return toolCallId;
        }
        if (WorkmateToolIds.isAskUserQuestion(toolName)) {
            return toolCallId;
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("status", failed ? "error" : "done");
        if (result != null) {
            updates.put("result", result);
        }
        patchMessagePayload(context.sessionId(), toolCallId, updates);
        return toolCallId;
    }

    void recordDelegationPrompt(
            RunPersistenceContext context,
            String toolCallId,
            String memberId,
            String memberName,
            String message,
            String description) {
        if (context == null || RunPersistenceContext.isTeamSurface(context) || toolCallId == null || toolCallId.isBlank()) {
            return;
        }
        if (memberId == null || memberId.isBlank()) {
            return;
        }
        String cleanMessage = message == null ? "" : message.strip();
        String cleanDescription = description == null ? "" : description.strip();
        if (cleanMessage.isEmpty() && cleanDescription.isEmpty()) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "delegation-" + toolCallId);
        item.put("kind", "delegation");
        item.put("toolCallId", toolCallId);
        item.put("memberId", memberId);
        if (memberName != null && !memberName.isBlank()) {
            item.put("memberName", memberName);
        }
        if (!cleanMessage.isEmpty()) {
            item.put("message", cleanMessage);
        }
        if (!cleanDescription.isEmpty()) {
            item.put("description", cleanDescription);
        }
        saveMessage(context.sessionId(), context.runId(), item);
    }

    void recordPlan(RunPersistenceContext context, PlanPayload plan) {
        if (context == null || plan == null) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "plan-" + plan.planId());
        item.put("kind", "plan");
        item.put("planId", plan.planId());
        if (plan.title() != null && !plan.title().isBlank()) {
            item.put("title", plan.title());
        }
        item.put("steps", planStepsToMaps(plan.steps()));
        item.put("confirmed", false);

        SessionQuestionLookup.PlanRef existing = questionLookup.findPlanMessage(context.sessionId(), plan.planId());
        if (existing != null) {
            patchMessagePayload(context.sessionId(), existing.messageId(), item);
        } else {
            saveMessage(context.sessionId(), context.runId(), item);
        }
    }

    void recordSystemMessage(RunPersistenceContext context, String text, String tone) {
        if (context == null || text == null || text.isBlank()) {
            return;
        }
        String messageId = UUID.randomUUID().toString();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", messageId);
        item.put("kind", "system");
        item.put("text", text);
        if (tone != null) {
            item.put("tone", tone);
        }
        saveMessage(context.sessionId(), context.runId(), item);
    }

    void recordExpertSwitched(RunPersistenceContext context, Map<String, Object> payload) {
        if (context == null || payload == null) {
            return;
        }
        String messageId = UUID.randomUUID().toString();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", messageId);
        item.put("kind", "expert-switched");
        item.put("fromExpertId", payload.get("fromExpertId"));
        item.put("toExpertId", payload.get("toExpertId"));
        item.put("fromExpertName", payload.get("fromExpertName"));
        item.put("toExpertName", payload.get("toExpertName"));
        item.put("newGeneration", payload.get("newGeneration"));
        item.put("mode", payload.get("mode"));
        saveMessage(context.sessionId(), context.runId(), item);
    }

    void upsertQuestionRequired(
            RunPersistenceContext context,
            UUID questionId,
            String question,
            List<String> options,
            boolean allowFreeText,
            boolean multiSelect,
            String status) {
        if (context == null || RunPersistenceContext.isTeamSurface(context)) {
            return;
        }
        if ("pending".equals(status) && questionLookup.findAnsweredQuestion(context.sessionId(), question, options).isPresent()) {
            return;
        }
        String messageId = QuestionPromptNormalizer.messageId(question, options);
        var existing = messageRepository.findByIdAndSessionId(messageId, context.sessionId());
        if (existing.isPresent()) {
            Map<String, Object> payload = SessionMessageJson.readPayload(existing.get(), objectMapper);
            String existingStatus = String.valueOf(payload.getOrDefault("status", ""));
            if ("answered".equals(existingStatus) || "skipped".equals(existingStatus)) {
                return;
            }
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("questionId", questionId.toString());
            patch.put("question", question);
            patch.put("options", options);
            patch.put("allowFreeText", allowFreeText);
            patch.put("multiSelect", multiSelect);
            patch.put("status", status);
            patchMessagePayload(context.sessionId(), messageId, patch);
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", messageId);
        item.put("kind", "question");
        item.put("questionId", questionId.toString());
        item.put("question", question);
        item.put("options", options);
        item.put("allowFreeText", allowFreeText);
        item.put("multiSelect", multiSelect);
        item.put("status", status);
        saveMessage(context.sessionId(), context.runId(), item);
    }

    void recordQuestionAnswered(
            UUID sessionId,
            UUID questionId,
            String question,
            List<String> options,
            List<String> selections,
            String textPreview,
            boolean skipped) {
        String messageId = questionLookup.resolveQuestionMessageId(sessionId, questionId);
        if (messageId == null && question != null && !question.isBlank()) {
            messageId = QuestionPromptNormalizer.messageId(question, options);
        }
        if (messageId == null) {
            messageId = "question-" + questionId;
        }
        String status = skipped ? "skipped" : "answered";
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("status", status);
        if (selections != null && !selections.isEmpty()) {
            patch.put("selections", selections);
        }
        if (textPreview != null && !textPreview.isBlank()) {
            patch.put("answerText", textPreview);
        }
        var existing = messageRepository.findByIdAndSessionId(messageId, sessionId);
        if (existing.isEmpty()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", messageId);
            item.put("kind", "question");
            item.put("questionId", questionId.toString());
            item.put("question", question == null ? "" : question);
            item.put("options", options == null ? List.of() : options);
            item.put("allowFreeText", true);
            item.put("multiSelect", false);
            item.putAll(patch);
            saveMessage(sessionId, "hitl-answer", item);
            return;
        }
        patchMessagePayload(sessionId, messageId, patch);
    }

    void recordQuestionCancelled(
            UUID sessionId,
            UUID questionId,
            String question,
            List<String> options,
            boolean allowFreeText,
            boolean multiSelect,
            String runId) {
        String messageId = questionLookup.resolveQuestionMessageId(sessionId, questionId);
        if (messageId == null && question != null && !question.isBlank()) {
            messageId = QuestionPromptNormalizer.messageId(question, options);
        }
        if (messageId == null) {
            messageId = "question-" + questionId;
        }
        var existing = messageRepository.findByIdAndSessionId(messageId, sessionId);
        if (existing.isEmpty()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", messageId);
            item.put("kind", "question");
            item.put("questionId", questionId.toString());
            item.put("question", question == null ? "" : question);
            item.put("options", options == null ? List.of() : options);
            item.put("allowFreeText", allowFreeText);
            item.put("multiSelect", multiSelect);
            item.put("status", "cancelled");
            String effectiveRunId = runId == null || runId.isBlank() ? "hitl-timeout" : runId;
            saveMessage(sessionId, effectiveRunId, item);
            return;
        }
        Map<String, Object> payload = SessionMessageJson.readPayload(existing.get(), objectMapper);
        String existingStatus = String.valueOf(payload.getOrDefault("status", ""));
        if ("answered".equals(existingStatus) || "skipped".equals(existingStatus)) {
            return;
        }
        if (payload.get("selections") instanceof List<?> selections && !selections.isEmpty()) {
            return;
        }
        patchMessagePayload(sessionId, messageId, Map.of("status", "cancelled"));
    }

    void finalizeAssistant(RunPersistenceContext context) {
        if (context == null || context.assistantMessageId() == null) {
            return;
        }
        updateMessagePayload(
                context.sessionId(),
                context.assistantMessageId(),
                Map.of("text", context.currentTurnText()));
    }

    SessionPersistenceService.PlanUpdateResult updatePlan(
            UUID sessionId, String planId, String title, List<Map<String, Object>> steps) {
        SessionQuestionLookup.PlanRef existing = questionLookup.findPlanMessage(sessionId, planId);
        if (existing == null) {
            throw new IllegalArgumentException("Plan not found: " + planId);
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        if (title != null && !title.isBlank()) {
            patch.put("title", title);
        }
        if (steps != null && !steps.isEmpty()) {
            patch.put("steps", steps);
        }
        if (!patch.isEmpty()) {
            patchMessagePayload(sessionId, existing.messageId(), patch);
        }
        SessionMessage message = messageRepository.findByIdAndSessionId(existing.messageId(), sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Plan message missing: " + planId));
        Map<String, Object> payload = SessionMessageJson.readPayload(message, objectMapper);
        String runId = message.getRunId() != null && !message.getRunId().isBlank()
                ? message.getRunId()
                : "plan-edit";
        return new SessionPersistenceService.PlanUpdateResult(payload, runId, !patch.isEmpty());
    }

    void confirmPlan(UUID sessionId, String planId) {
        if (planId != null && !planId.isBlank()) {
            SessionQuestionLookup.PlanRef existing = questionLookup.findPlanMessage(sessionId, planId);
            if (existing != null) {
                patchMessagePayload(sessionId, existing.messageId(), Map.of("confirmed", true));
                return;
            }
        }
        for (SessionMessage message : messageRepository.findBySessionIdAndSupersededFalseOrderBySeqAsc(sessionId)) {
            Map<String, Object> payload = SessionMessageJson.readPayload(message, objectMapper);
            if ("plan".equals(payload.get("kind")) && !Boolean.TRUE.equals(payload.get("confirmed"))) {
                patchMessagePayload(sessionId, message.getId(), Map.of("confirmed", true));
                break;
            }
        }
    }

    int markSupersededFrom(UUID sessionId, int fromSeq) {
        return messageRepository.markSupersededFrom(sessionId, fromSeq);
    }

    private void saveMessage(UUID sessionId, String runId, Map<String, Object> item) {
        String messageId = (String) item.get("id");
        int seq = sequenceAllocator.nextSeq(sessionId);
        messageRepository.save(new SessionMessage(
                messageId,
                sessionId,
                runId,
                seq,
                SessionMessageJson.writeJson(objectMapper, item)));
    }

    private void updateMessagePayload(UUID sessionId, String messageId, Map<String, Object> updates) {
        SessionMessage message = messageRepository.findByIdAndSessionId(messageId, sessionId).orElse(null);
        if (message == null) {
            return;
        }
        Map<String, Object> payload = SessionMessageJson.readPayload(message, objectMapper);
        payload.putAll(updates);
        message.setPayloadJson(SessionMessageJson.writeJson(objectMapper, payload));
        messageRepository.save(message);
    }

    private void patchMessagePayload(UUID sessionId, String messageId, Map<String, Object> patch) {
        updateMessagePayload(sessionId, messageId, patch);
    }

    private static List<Map<String, Object>> planStepsToMaps(List<PlanStep> steps) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlanStep step : steps) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", step.id());
            map.put("title", step.title());
            if (step.status() != null) {
                map.put("status", step.status());
            }
            result.add(map);
        }
        return result;
    }
}
