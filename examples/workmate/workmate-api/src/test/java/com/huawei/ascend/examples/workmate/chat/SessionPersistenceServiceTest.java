package com.huawei.ascend.examples.workmate.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.session.WorkmateSessionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SessionPersistenceServiceTest {

    // A fresh session per test method: run_event seq and message persistence are per-session, and the
    // service flushes outside the test's rollback, so a shared id would let seq/messages bleed across
    // methods. A unique id keeps each method's queries isolated.
    private UUID sessionId;

    @Autowired
    private SessionPersistenceService persistenceService;

    @Autowired
    private WorkmateSessionRepository sessionRepository;

    @Autowired
    private SessionMessageRepository messageRepository;

    @Autowired
    private RunEventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerH2(DynamicPropertyRegistry registry) {
        WorkmateTestProperties.registerH2(registry, "workmate-persist");
    }

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        ensureSession(sessionId);
    }

    @Test
    void truncateFromMarksSupersededAndRecordsAuditEvent() {
        String runId = UUID.randomUUID().toString();
        persistenceService.beginRun(sessionId, runId, "first");
        String runId2 = UUID.randomUUID().toString();
        persistenceService.beginRun(sessionId, runId2, "second");

        // beginRun persists one (user) message per run; the assistant message is created lazily.
        List<Map<String, Object>> before = persistenceService.listMessages(sessionId);
        assertThat(before).hasSize(2);
        int fromSeq = ((Number) before.get(1).get("seq")).intValue();

        SessionPersistenceService.TruncateResult result =
                persistenceService.truncateFrom(sessionId, fromSeq, "edit", "audit-run");

        assertThat(result.newGeneration()).isEqualTo(1);
        assertThat(result.markedCount()).isGreaterThan(0);
        assertThat(persistenceService.listMessages(sessionId)).hasSize(1);

        var events = eventRepository.findBySessionIdAndSeqGreaterThanOrderBySeqAsc(sessionId, -1);
        assertThat(events.stream().map(RunEvent::getEventName))
                .contains("conversation.truncated");
    }

    private void ensureSession(UUID id) {
        if (sessionRepository.existsById(id)) {
            return;
        }
        sessionRepository.save(new WorkmateSession(
                id, "test", "/tmp/" + id, SessionStatus.CREATED));
    }

    @Test
    void beginRunPersistsUserMessageAndDefersAssistant() {
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context =
                persistenceService.beginRun(sessionId, runId, "hello");

        // The assistant message is created lazily on the first delta, so beginRun only persists
        // the user message and does not pre-seed an assistant id.
        assertThat(context).isNotNull();
        assertThat(context.assistantMessageId()).isNull();

        List<Map<String, Object>> messages = persistenceService.listMessages(sessionId);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("kind")).isEqualTo("user");
        assertThat(messages.get(0).get("text")).isEqualTo("hello");
    }

    @Test
    void beginRunPersistsMentionsOnUserMessage() {
        ensureSession(sessionId);
        String runId = UUID.randomUUID().toString();
        List<Map<String, Object>> mentionMaps = List.of(Map.of(
                "type", "file",
                "id", "notes.md",
                "path", "notes.md",
                "label", "notes.md"));
        persistenceService.beginRun(sessionId, runId, "with context", mentionMaps);

        List<Map<String, Object>> messages = persistenceService.listMessages(sessionId);
        assertThat(messages.get(0).get("mentions")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mentions = (List<Map<String, Object>>) messages.get(0).get("mentions");
        assertThat(mentions).hasSize(1);
        assertThat(mentions.get(0).get("type")).isEqualTo("file");
        assertThat(mentions.get(0).get("id")).isEqualTo("notes.md");
    }

    @Test
    void beginRunPersistsAttachmentsOnUserMessage() {
        ensureSession(sessionId);
        String runId = UUID.randomUUID().toString();
        List<Map<String, Object>> attachmentMaps = List.of(Map.of(
                "path", "uploads/shot.png",
                "name", "shot.png",
                "mime", "image/png"));
        persistenceService.beginRun(sessionId, runId, "see image", List.of(), attachmentMaps);

        List<Map<String, Object>> messages = persistenceService.listMessages(sessionId);
        assertThat(messages.get(0).get("attachments")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attachments = (List<Map<String, Object>>) messages.get(0).get("attachments");
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).get("path")).isEqualTo("uploads/shot.png");
    }

    @Test
    void appendAssistantDeltaUpdatesAssistantText() {
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context =
                persistenceService.beginRun(sessionId, runId, "hello");

        persistenceService.appendAssistantDelta(context, "WorkMate");
        persistenceService.finalizeAssistant(context);

        List<Map<String, Object>> messages = persistenceService.listMessages(sessionId);
        Map<String, Object> assistant = messages.get(messages.size() - 1);
        assertThat(assistant.get("kind")).isEqualTo("assistant");
        assertThat(assistant.get("text")).isEqualTo("WorkMate");
    }

    @Test
    void toolLifecyclePersistsRunningAndDoneStates() {
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context =
                persistenceService.beginRun(sessionId, runId, "run tool");

        persistenceService.recordToolStart(context, "workmate_bash", Map.of("command", "ls"));
        persistenceService.recordToolEnd(context, "workmate_bash", Map.of("success", true), false);

        List<Map<String, Object>> messages = persistenceService.listMessages(sessionId);
        Map<String, Object> tool = messages.stream()
                .filter(item -> "tool".equals(item.get("kind")))
                .findFirst()
                .orElseThrow();
        assertThat(tool.get("toolName")).isEqualTo("workmate_bash");
        assertThat(tool.get("toolCallId")).isNotNull();
        assertThat(tool.get("status")).isEqualTo("done");
    }

    @Test
    void recordEventReturnsMonotonicSeq() {
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context =
                persistenceService.beginRun(sessionId, runId, "events");

        var first = persistenceService.recordEvent(context, "message.delta", Map.of("text", "a"));
        var second = persistenceService.recordEvent(context, "message.delta", Map.of("text", "b"));

        // Messages and run events share one monotonic seq space per session, so the absolute starting
        // value depends on prior writes (beginRun's user message). Assert monotonicity instead.
        assertThat(second.seq()).isEqualTo(first.seq() + 1);

        var replay = persistenceService.listEventsAfter(sessionId, first.seq());
        assertThat(replay).hasSize(1);
        assertThat(replay.get(0).eventName()).isEqualTo("message.delta");
    }

    @Test
    void confirmPlanMarksPlanConfirmed() {
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context =
                persistenceService.beginRun(sessionId, runId, "plan me");

        persistenceService.recordPlan(
                context,
                new com.huawei.ascend.examples.workmate.agent.PlanPayload(
                        "plan-1",
                        "Demo",
                        List.of(
                                new com.huawei.ascend.examples.workmate.agent.PlanPayload.PlanStep(
                                        "step-1", "First", "pending"),
                                new com.huawei.ascend.examples.workmate.agent.PlanPayload.PlanStep(
                                        "step-2", "Second", "pending"))));

        persistenceService.confirmPlan(sessionId, "plan-1");

        List<Map<String, Object>> messages = persistenceService.listMessages(sessionId);
        Map<String, Object> plan = messages.stream()
                .filter(item -> "plan".equals(item.get("kind")))
                .findFirst()
                .orElseThrow();
        assertThat(plan.get("confirmed")).isEqualTo(true);
    }

    @Test
    void updatePlanStepsBeforeConfirm() {
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context =
                persistenceService.beginRun(sessionId, runId, "plan me");

        persistenceService.recordPlan(
                context,
                new com.huawei.ascend.examples.workmate.agent.PlanPayload(
                        "plan-1",
                        "Demo",
                        List.of(
                                new com.huawei.ascend.examples.workmate.agent.PlanPayload.PlanStep(
                                        "step-1", "First", "pending"))));

        SessionPersistenceService.PlanUpdateResult updated = persistenceService.updatePlan(
                sessionId,
                "plan-1",
                "Updated title",
                List.of(Map.of("id", "step-1", "title", "Revised first", "status", "pending")));

        assertThat(updated.plan().get("title")).isEqualTo("Updated title");
        assertThat(updated.plan().get("steps")).isInstanceOf(List.class);
    }

    @Test
    void messageAndRunEventSeqAreGloballyMonotonic() {
        ensureSession(sessionId);
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context =
                persistenceService.beginRun(sessionId, runId, "team task");

        persistenceService.appendAssistantDelta(context, "before tool");
        var event = persistenceService.recordEvent(context, "message.delta", Map.of("delta", "streamed"));
        String toolCallId = persistenceService.recordToolStart(
                context, "team.build_team", Map.of("memberCount", 2), "team-build-" + runId);
        persistenceService.appendAssistantDelta(context, "after tool");

        List<Map<String, Object>> messages = persistenceService.listMessages(sessionId);
        int userSeq = ((Number) messages.get(0).get("seq")).intValue();
        int assistantBeforeSeq = messages.stream()
                .filter(item -> "assistant".equals(item.get("kind")))
                .mapToInt(item -> ((Number) item.get("seq")).intValue())
                .findFirst()
                .orElseThrow();
        int toolSeq = messages.stream()
                .filter(item -> "tool".equals(item.get("kind")))
                .mapToInt(item -> ((Number) item.get("seq")).intValue())
                .findFirst()
                .orElseThrow();
        int assistantAfterSeq = messages.stream()
                .filter(item -> "assistant".equals(item.get("kind")))
                .mapToInt(item -> ((Number) item.get("seq")).intValue())
                .max()
                .orElseThrow();

        assertThat(userSeq).isLessThan(assistantBeforeSeq);
        assertThat(assistantBeforeSeq).isLessThan(event.seq());
        assertThat(event.seq()).isLessThan(toolSeq);
        assertThat(toolSeq).isLessThan(assistantAfterSeq);
        assertThat(toolCallId).isEqualTo("team-build-" + runId);
    }

    @Test
    void memberSubRunToolsDoNotAppearInMainChat() {
        ensureSession(sessionId);
        String parentRunId = UUID.randomUUID().toString();
        RunPersistenceContext parent =
                persistenceService.beginRun(sessionId, parentRunId, "team task");
        persistenceService.recordToolStart(parent, "workmate_bash", Map.of("command", "ls parent"));
        persistenceService.recordToolEnd(parent, "workmate_bash", Map.of("success", true), false);

        String subRunId = UUID.randomUUID().toString();
        RunPersistenceContext member =
                persistenceService.beginSubRun(sessionId, subRunId, parentRunId, "writer");
        persistenceService.recordToolStart(member, "workmate_read", Map.of("path", "notes.md"));
        persistenceService.recordToolEnd(member, "workmate_read", Map.of("success", true), false);

        List<Map<String, Object>> messages = persistenceService.listMessages(sessionId);
        long toolCount = messages.stream().filter(item -> "tool".equals(item.get("kind"))).count();
        assertThat(toolCount).isEqualTo(1);
        Map<String, Object> tool = messages.stream()
                .filter(item -> "tool".equals(item.get("kind")))
                .findFirst()
                .orElseThrow();
        assertThat(tool.get("toolName")).isEqualTo("workmate_bash");
    }

    @Test
    void recordDelegationPromptPersistsFullTaskBody() {
        ensureSession(sessionId);
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context =
                persistenceService.beginRun(sessionId, runId, "team task");
        String fullTask = "## 任务\n\n" + "正文".repeat(400);
        persistenceService.recordDelegationPrompt(
                context, "team-send-1", "topic-researcher", "谭溯源", fullTask, "Phase 1");

        List<Map<String, Object>> messages = persistenceService.listMessages(sessionId);
        Map<String, Object> delegation = messages.stream()
                .filter(item -> "delegation".equals(item.get("kind")))
                .findFirst()
                .orElseThrow();
        assertThat(delegation.get("memberId")).isEqualTo("topic-researcher");
        assertThat(delegation.get("message")).isEqualTo(fullTask);
        assertThat(delegation.get("description")).isEqualTo("Phase 1");
    }

    @Test
    void listMessagesDedupesLegacyDuplicateQuestionPrompts() throws JsonProcessingException {
        ensureSession(sessionId);
        String runId = UUID.randomUUID().toString();
        String prompt = "请确认执行模式（完整/快速/单章）";
        saveLegacyQuestion(sessionId, runId, "question-legacy-1", UUID.randomUUID(), prompt, 37, "answered");
        saveLegacyQuestion(sessionId, runId, "question-legacy-2", UUID.randomUUID(), prompt, 451, "answered");

        List<Map<String, Object>> questions = persistenceService.listMessages(sessionId).stream()
                .filter(item -> "question".equals(item.get("kind")))
                .toList();
        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).get("seq")).isEqualTo(451);
    }

    @Test
    void listMessagesMergesQuestionsFromRunEvents() throws JsonProcessingException {
        UUID sid = UUID.randomUUID();
        ensureSession(sid);
        String runId = UUID.randomUUID().toString();
        String messageId = "question-7d993dd852b0646e";
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("questionId", UUID.randomUUID().toString());
        payload.put("sessionId", sid.toString());
        payload.put("question", "以下是大纲，请确认是否满意？");
        payload.put("options", List.of("确认，按此大纲执行"));
        payload.put("allowFreeText", true);
        payload.put("multiSelect", false);
        payload.put("messageId", messageId);
        eventRepository.save(new RunEvent(
                UUID.randomUUID(), sid, runId, 100, "question.required", objectMapper.writeValueAsString(payload)));
        eventRepository.save(new RunEvent(
                UUID.randomUUID(), sid, runId, 110, "message.delta", "{\"delta\":\"继续\"}"));

        List<Map<String, Object>> messages = persistenceService.listMessages(sid);
        Map<String, Object> outline = messages.stream()
                .filter(item -> messageId.equals(item.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(outline.get("status")).isEqualTo("cancelled");
        assertThat(outline.get("question")).isEqualTo("以下是大纲，请确认是否满意？");
        long persisted = messageRepository.findBySessionIdAndSupersededFalseOrderBySeqAsc(sid).stream()
                .filter(message -> messageId.equals(message.getId()))
                .count();
        assertThat(persisted).isZero();
    }

    @Test
    void recordQuestionCancelledDoesNotOverwriteAnswered() {
        UUID sid = UUID.randomUUID();
        ensureSession(sid);
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context = persistenceService.beginRun(sid, runId, "research");
        UUID questionId = UUID.randomUUID();
        String prompt = "在开始之前，请确认以下研究参数：";
        List<String> options = List.of("完整模式（默认，含审稿修订循环）");
        persistenceService.recordQuestionRequired(
                context, questionId, prompt, options, true, false);
        persistenceService.recordQuestionAnswered(
                sid, questionId, prompt, options, List.of("完整模式（默认，含审稿修订循环）"), null, false);
        persistenceService.recordQuestionCancelled(
                sid, questionId, prompt, options, true, false, "run-timeout");

        Map<String, Object> card = persistenceService.listMessages(sid).stream()
                .filter(item -> "question".equals(item.get("kind")))
                .findFirst()
                .orElseThrow();
        assertThat(card.get("status")).isEqualTo("answered");
        assertThat(card.get("selections")).isEqualTo(List.of("完整模式（默认，含审稿修订循环）"));
    }

    @Test
    void recordQuestionCancelledPersistsCancelledCard() {
        UUID sid = UUID.randomUUID();
        ensureSession(sid);
        UUID questionId = UUID.randomUUID();
        persistenceService.recordQuestionCancelled(
                sid,
                questionId,
                "以下是大纲，请确认是否满意？",
                List.of("确认，按此大纲执行"),
                true,
                false,
                "run-timeout");

        Map<String, Object> card = persistenceService.listMessages(sid).stream()
                .filter(item -> "question".equals(item.get("kind")))
                .findFirst()
                .orElseThrow();
        assertThat(card.get("status")).isEqualTo("cancelled");
        assertThat(card.get("questionId")).isEqualTo(questionId.toString());
    }

    @Test
    void recordQuestionRequiredReusesStableMessageId() {
        ensureSession(sessionId);
        String runId = UUID.randomUUID().toString();
        RunPersistenceContext context = persistenceService.beginRun(sessionId, runId, "research");
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        String prompt = "请确认研究参数";
        persistenceService.recordQuestionRequired(
                context, firstId, prompt, List.of("A"), true, true);
        persistenceService.recordQuestionAnswered(sessionId, firstId, List.of("A"), null, false);
        persistenceService.recordQuestionRequired(
                context, secondId, prompt, List.of("A"), true, true);

        long stored = messageRepository.findBySessionIdAndSupersededFalseOrderBySeqAsc(sessionId).stream()
                .filter(message -> {
                    Map<String, Object> payload = readPayload(message);
                    return "question".equals(payload.get("kind"))
                            && prompt.equals(payload.get("question"));
                })
                .count();
        assertThat(stored).isOne();
    }

    private void saveLegacyQuestion(
            UUID sessionId,
            String runId,
            String messageId,
            UUID questionId,
            String prompt,
            int seq,
            String status) throws JsonProcessingException {
        Map<String, Object> payload = Map.of(
                "id", messageId,
                "kind", "question",
                "questionId", questionId.toString(),
                "question", prompt,
                "options", List.of("完整模式"),
                "allowFreeText", false,
                "multiSelect", false,
                "status", status,
                "selections", List.of("完整模式"));
        messageRepository.save(new SessionMessage(
                messageId, sessionId, runId, seq, objectMapper.writeValueAsString(payload)));
    }

    private Map<String, Object> readPayload(SessionMessage message) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(message.getPayloadJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
