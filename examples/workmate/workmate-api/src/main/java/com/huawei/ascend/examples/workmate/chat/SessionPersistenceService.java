package com.huawei.ascend.examples.workmate.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.agent.PlanPayload;
import com.huawei.ascend.examples.workmate.question.AnsweredQuestionRecord;
import com.huawei.ascend.examples.workmate.chat.InvalidConversationEditException;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.session.WorkmateSessionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionPersistenceService {

    private final SessionMessageRepository messageRepository;
    private final WorkmateSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final RunEventWriter eventWriter;
    private final SessionMessageWriter messageWriter;
    private final ChatTimelineProjector timelineProjector;
    private final SessionQuestionLookup questionLookup;

    public SessionPersistenceService(
            SessionMessageRepository messageRepository,
            WorkmateSessionRepository sessionRepository,
            ObjectMapper objectMapper,
            RunEventWriter eventWriter,
            SessionMessageWriter messageWriter,
            ChatTimelineProjector timelineProjector,
            SessionQuestionLookup questionLookup) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
        this.eventWriter = eventWriter;
        this.messageWriter = messageWriter;
        this.timelineProjector = timelineProjector;
        this.questionLookup = questionLookup;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMessages(UUID sessionId) {
        return timelineProjector.listMessages(sessionId);
    }

    @Transactional(readOnly = true)
    public Optional<AnsweredQuestionRecord> findAnsweredQuestion(
            UUID sessionId, String question, List<String> options) {
        return questionLookup.findAnsweredQuestion(sessionId, question, options);
    }

    @Transactional(readOnly = true)
    public boolean hasAnsweredSemanticKey(UUID sessionId, String semanticKey) {
        return questionLookup.hasAnsweredSemanticKey(sessionId, semanticKey);
    }

    @Transactional(readOnly = true)
    public boolean hasRunEvent(UUID sessionId, String eventName) {
        return eventWriter.hasRunEvent(sessionId, eventName);
    }

    @Transactional(readOnly = true)
    public List<RecordedRunEvent> listEventsAfter(UUID sessionId, int afterSeq) {
        return eventWriter.listEventsAfter(sessionId, afterSeq);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEventLog(UUID sessionId) {
        return listEventLog(sessionId, -1);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEventLog(UUID sessionId, int afterSeq) {
        return eventWriter.listEventLog(sessionId, afterSeq);
    }

    @Transactional
    public TruncateResult truncateFrom(UUID sessionId, int fromSeq, String reason, String auditRunId) {
        if (fromSeq < 1) {
            throw new InvalidConversationEditException("fromSeq must be >= 1");
        }
        WorkmateSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        int newGeneration = session.getConversationGeneration() + 1;
        session.setConversationGeneration(newGeneration);
        sessionRepository.save(session);

        int marked = messageWriter.markSupersededFrom(sessionId, fromSeq);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromSeq", fromSeq);
        payload.put("reason", reason);
        payload.put("newGeneration", newGeneration);
        payload.put("markedCount", marked);
        eventWriter.persistRunEvent(RunPersistenceContext.forAudit(sessionId, auditRunId), "conversation.truncated", payload);

        return new TruncateResult(fromSeq, newGeneration, marked);
    }

    @Transactional(readOnly = true)
    public SessionMessage requireActiveUserMessage(UUID sessionId, int seq) {
        SessionMessage message = messageRepository.findBySessionIdAndSeqAndSupersededFalse(sessionId, seq)
                .orElseThrow(() -> new InvalidConversationEditException("Message not found at seq " + seq));
        Map<String, Object> payload = SessionMessageJson.readPayload(message, objectMapper);
        if (!"user".equals(payload.get("kind"))) {
            throw new InvalidConversationEditException("Only user messages can be edited (seq " + seq + ")");
        }
        return message;
    }

    @Transactional(readOnly = true)
    public SessionMessage requireLastActiveUserMessage(UUID sessionId) {
        List<SessionMessage> messages =
                messageRepository.findBySessionIdAndSupersededFalseOrderBySeqAsc(sessionId);
        for (int i = messages.size() - 1; i >= 0; i--) {
            SessionMessage message = messages.get(i);
            Map<String, Object> payload = SessionMessageJson.readPayload(message, objectMapper);
            if ("user".equals(payload.get("kind"))) {
                return message;
            }
        }
        throw new InvalidConversationEditException("No active user message to retry");
    }

    public static String readUserText(SessionMessage message, ObjectMapper objectMapper) {
        return SessionMessageJson.readUserText(message, objectMapper);
    }

    public record TruncateResult(int fromSeq, int newGeneration, int markedCount) {}

    @Transactional
    public RunPersistenceContext beginSubRun(
            UUID sessionId, String runId, String parentRunId, String memberId) {
        return beginSubRun(sessionId, runId, parentRunId, memberId, null);
    }

    @Transactional
    public RunPersistenceContext beginSubRun(
            UUID sessionId, String runId, String parentRunId, String memberId, String memberName) {
        return messageWriter.beginSubRun(sessionId, runId, parentRunId, memberId, memberName);
    }

    @Transactional
    public RunPersistenceContext beginRun(UUID sessionId, String runId, String userMessage) {
        return beginRun(sessionId, runId, userMessage, List.of(), List.of());
    }

    @Transactional
    public RunPersistenceContext beginRun(
            UUID sessionId, String runId, String userMessage, List<Map<String, Object>> mentionMaps) {
        return beginRun(sessionId, runId, userMessage, mentionMaps, List.of());
    }

    @Transactional
    public RunPersistenceContext beginRun(
            UUID sessionId,
            String runId,
            String userMessage,
            List<Map<String, Object>> mentionMaps,
            List<Map<String, Object>> attachmentMaps) {
        return messageWriter.beginRun(sessionId, runId, userMessage, mentionMaps, attachmentMaps);
    }

    @Transactional
    public RecordedRunEvent recordEvent(RunPersistenceContext context, String eventName, Map<String, Object> payload) {
        return persistRunEvent(context, eventName, payload);
    }

    @Transactional
    public RecordedRunEvent persistRunEvent(
            RunPersistenceContext context, String eventName, Map<String, Object> payload) {
        return eventWriter.persistRunEvent(context, eventName, payload);
    }

    @Transactional
    public String appendAssistantDelta(RunPersistenceContext context, String text) {
        return messageWriter.appendAssistantDelta(context, text);
    }

    @Transactional
    public String recordToolStart(RunPersistenceContext context, String toolName, Object args) {
        return recordToolStart(context, toolName, args, null);
    }

    @Transactional
    public String recordToolStart(
            RunPersistenceContext context, String toolName, Object args, String preferredToolCallId) {
        return messageWriter.recordToolStart(context, toolName, args, preferredToolCallId);
    }

    @Transactional
    public String recordToolEnd(RunPersistenceContext context, String toolName, Object result, boolean failed) {
        return messageWriter.recordToolEnd(context, toolName, result, failed);
    }

    @Transactional
    public void recordDelegationPrompt(
            RunPersistenceContext context, String toolCallId, String memberId, String memberName, String message, String description) {
        messageWriter.recordDelegationPrompt(context, toolCallId, memberId, memberName, message, description);
    }

    @Transactional
    public void recordPlan(RunPersistenceContext context, PlanPayload plan) {
        messageWriter.recordPlan(context, plan);
    }

    @Transactional
    public void recordSystemMessage(RunPersistenceContext context, String text, String tone) {
        messageWriter.recordSystemMessage(context, text, tone);
    }

    @Transactional
    public void recordExpertSwitched(RunPersistenceContext context, Map<String, Object> payload) {
        messageWriter.recordExpertSwitched(context, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordQuestionRequired(
            RunPersistenceContext context,
            UUID questionId,
            String question,
            List<String> options,
            boolean allowFreeText,
            boolean multiSelect) {
        upsertQuestionRequired(context, questionId, question, options, allowFreeText, multiSelect, "pending");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertQuestionRequired(
            RunPersistenceContext context,
            UUID questionId,
            String question,
            List<String> options,
            boolean allowFreeText,
            boolean multiSelect,
            String status) {
        messageWriter.upsertQuestionRequired(
                context, questionId, question, options, allowFreeText, multiSelect, status);
    }

    @Transactional
    public void recordQuestionAnswered(
            UUID sessionId,
            UUID questionId,
            List<String> selections,
            String textPreview,
            boolean skipped) {
        recordQuestionAnswered(sessionId, questionId, null, null, selections, textPreview, skipped);
    }

    @Transactional
    public void recordQuestionAnswered(
            UUID sessionId,
            UUID questionId,
            String question,
            List<String> options,
            List<String> selections,
            String textPreview,
            boolean skipped) {
        messageWriter.recordQuestionAnswered(
                sessionId, questionId, question, options, selections, textPreview, skipped);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordQuestionCancelled(
            UUID sessionId,
            UUID questionId,
            String question,
            List<String> options,
            boolean allowFreeText,
            boolean multiSelect,
            String runId) {
        messageWriter.recordQuestionCancelled(
                sessionId, questionId, question, options, allowFreeText, multiSelect, runId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordQuestionCancelled(UUID sessionId, UUID questionId) {
        recordQuestionCancelled(sessionId, questionId, null, List.of(), true, false, null);
    }

    @Transactional
    public void finalizeAssistant(RunPersistenceContext context) {
        messageWriter.finalizeAssistant(context);
    }

    @Transactional
    public PlanUpdateResult updatePlan(
            UUID sessionId, String planId, String title, List<Map<String, Object>> steps) {
        return messageWriter.updatePlan(sessionId, planId, title, steps);
    }

    public record PlanUpdateResult(Map<String, Object> plan, String runId, boolean changed) {}

    @Transactional
    public void confirmPlan(UUID sessionId, String planId) {
        messageWriter.confirmPlan(sessionId, planId);
    }
}
