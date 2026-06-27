package com.huawei.ascend.examples.workmate.agent;

import com.huawei.ascend.examples.workmate.approval.ApprovalGate;
import com.huawei.ascend.examples.workmate.approval.ApprovalGateFactory;
import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.artifact.dto.ArtifactResponse;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.mention.dto.MentionItem;
import com.huawei.ascend.examples.workmate.memory.MemoryService;
import com.huawei.ascend.examples.workmate.question.QuestionGate;
import com.huawei.ascend.examples.workmate.question.QuestionGateFactory;
import com.huawei.ascend.examples.workmate.question.UserQuestionService;
import com.huawei.ascend.examples.workmate.session.SessionNotFoundException;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.team.agent.TeamDelegationToolEmitter;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import com.huawei.ascend.runtime.engine.spi.TrajectorySource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AgentRunExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AgentRunExecutor.class);

    private final AgentRuntimeHandler agentHandler;
    private final SessionService sessionService;
    private final SseRunEventMapper eventMapper;
    private final WorkmateLlmProperties llm;
    private final ApprovalGateFactory approvalGateFactory;
    private final QuestionGateFactory questionGateFactory;
    private final UserQuestionService userQuestionService;
    private final ArtifactService artifactService;
    private final PlanExtractService planExtractService;
    private final SessionPersistenceService sessionPersistenceService;
    private final RunEventBroadcaster runEventBroadcaster;
    private final MemoryService memoryService;
    private final AgentExecutionVariablesBuilder variablesBuilder;
    private final AgentToolSetLifecycle toolSetLifecycle;
    private final RunEventEmitter runEventEmitter;
    private final AgentRunResultHandler resultHandler;

    public AgentRunExecutor(
            AgentRuntimeHandler agentHandler,
            SessionService sessionService,
            SseRunEventMapper eventMapper,
            WorkmateLlmProperties llm,
            ApprovalGateFactory approvalGateFactory,
            QuestionGateFactory questionGateFactory,
            UserQuestionService userQuestionService,
            ArtifactService artifactService,
            PlanExtractService planExtractService,
            SessionPersistenceService sessionPersistenceService,
            RunEventBroadcaster runEventBroadcaster,
            MemoryService memoryService,
            AgentExecutionVariablesBuilder variablesBuilder,
            AgentToolSetLifecycle toolSetLifecycle,
            RunEventEmitter runEventEmitter,
            AgentRunResultHandler resultHandler) {
        this.agentHandler = agentHandler;
        this.sessionService = sessionService;
        this.eventMapper = eventMapper;
        this.llm = llm;
        this.approvalGateFactory = approvalGateFactory;
        this.questionGateFactory = questionGateFactory;
        this.userQuestionService = userQuestionService;
        this.artifactService = artifactService;
        this.planExtractService = planExtractService;
        this.sessionPersistenceService = sessionPersistenceService;
        this.runEventBroadcaster = runEventBroadcaster;
        this.memoryService = memoryService;
        this.variablesBuilder = variablesBuilder;
        this.toolSetLifecycle = toolSetLifecycle;
        this.runEventEmitter = runEventEmitter;
        this.resultHandler = resultHandler;
    }

    public record ExecuteRequest(
            WorkmateSession session,
            String message,
            String taskId,
            String expertId,
            RunPersistenceContext persistenceContext,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            boolean streamAssistant,
            boolean emitTerminalEvents,
            boolean updateSessionStatus,
            com.huawei.ascend.examples.workmate.spi.topic.TopicBusMemberContext topicBusMemberContext,
            List<MentionItem> mentions) {

        public ExecuteRequest(
                WorkmateSession session,
                String message,
                String taskId,
                String expertId,
                RunPersistenceContext persistenceContext,
                SseEmitter emitter,
                AtomicBoolean clientConnected,
                boolean streamAssistant,
                boolean emitTerminalEvents,
                boolean updateSessionStatus) {
            this(
                    session,
                    message,
                    taskId,
                    expertId,
                    persistenceContext,
                    emitter,
                    clientConnected,
                    streamAssistant,
                    emitTerminalEvents,
                    updateSessionStatus,
                    null,
                    List.of());
        }

        public ExecuteRequest(
                WorkmateSession session,
                String message,
                String taskId,
                String expertId,
                RunPersistenceContext persistenceContext,
                SseEmitter emitter,
                AtomicBoolean clientConnected,
                boolean streamAssistant,
                boolean emitTerminalEvents,
                boolean updateSessionStatus,
                com.huawei.ascend.examples.workmate.spi.topic.TopicBusMemberContext topicBusMemberContext) {
            this(
                    session,
                    message,
                    taskId,
                    expertId,
                    persistenceContext,
                    emitter,
                    clientConnected,
                    streamAssistant,
                    emitTerminalEvents,
                    updateSessionStatus,
                    topicBusMemberContext,
                    List.of());
        }
    }

    public record ExecuteOutcome(String assistantText, boolean failed, String errorMessage) {}

    public ExecuteOutcome execute(ExecuteRequest request) {
        WorkmateSession session = request.session();
        UUID sessionId = session.getId();
        String taskId = request.taskId();
        RunPersistenceContext persistenceContext = request.persistenceContext();
        SseEmitter emitter = request.emitter();
        AtomicBoolean clientConnected = request.clientConnected();

        if (!llm.isConfigured()) {
            String error = "LLM not configured. Set WORKMATE_LLM_API_KEY (and optional WORKMATE_LLM_API_BASE / WORKMATE_LLM_MODEL).";
            if (request.emitTerminalEvents()) {
                emit(emitter, clientConnected, persistenceContext, "run.failed", Map.of("message", error));
            }
            return new ExecuteOutcome("", true, error);
        }

        runEventBroadcaster.registerRun(sessionId, taskId);

        RuntimeIdentity scope = RuntimeIdentity.of(
                "workmate",
                "workmate-user",
                sessionId.toString(),
                WorkmateAgentHandler.AGENT_ID);
        RuntimeIdentity scoped = scope.withTaskId(taskId);

        ApprovalGate approvalGate = session.getPermissionMode().requiresApprovalGate()
                ? approvalGateFactory.createGate()
                : null;
        if (approvalGate != null) {
            approvalGate.setListener(pending -> {
                Map<String, Object> payload = eventMapper.approvalPayload(pending);
                emit(emitter, clientConnected, persistenceContext, "approval.required", payload);
            });
        }

        boolean memberSubRun = persistenceContext != null && persistenceContext.memberId() != null;
        QuestionGate questionGate = null;
        if (!memberSubRun) {
            questionGate = questionGateFactory.createGate();
            questionGate.setListener(pending -> {
                sessionPersistenceService.recordQuestionRequired(
                        persistenceContext,
                        pending.id(),
                        pending.question(),
                        pending.options(),
                        pending.allowFreeText(),
                        pending.multiSelect());
                Map<String, Object> payload = userQuestionService.requiredPayload(pending);
                emit(emitter, clientConnected, persistenceContext, "question.required", payload);
            });
            questionGate.setCancelledListener(pending -> {
                Map<String, Object> payload = userQuestionService.cancelledPayload(pending);
                emit(emitter, clientConnected, persistenceContext, "question.cancelled", payload);
            });
        }

        Map<String, Object> variables = variablesBuilder.build(request, approvalGate, questionGate, memberSubRun);

        AgentExecutionContext context = new AgentExecutionContext(
                scoped,
                "USER_MESSAGE",
                List.of(RuntimeMessage.user(request.message())),
                variables);

        TrajectorySettings trajectorySettings = new TrajectorySettings(
                true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 512);
        AtomicBoolean terminalEventSent = new AtomicBoolean(false);
        StringBuilder outputCollector = new StringBuilder();
        TrajectorySink sink = event -> {
            resultHandler.emitUsageDeltaIfPresent(emitter, clientConnected, persistenceContext, sessionId, taskId, event);
            SseEmitter.SseEventBuilder mapped = eventMapper.trajectoryEvent(event);
            if (mapped != null) {
                if (event.kind() == com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind.RUN_END) {
                    terminalEventSent.set(true);
                }
                resultHandler.emitTrajectory(emitter, clientConnected, persistenceContext, event, request.streamAssistant());
            }
        };

        AgentToolSetLifecycle.RegisteredToolSets toolSets = null;
        Map<String, Instant> artifactsBefore = artifactService.snapshotRelativePaths(sessionId);
        boolean failed = false;
        String errorMessage = null;
        try {
            if (request.updateSessionStatus()) {
                sessionService.updateStatus(sessionId, SessionStatus.RUNNING);
            }
            if (agentHandler instanceof TrajectorySource source) {
                source.openTrajectory(context, trajectorySettings, sink);
            }
            try (Stream<?> raw = agentHandler.execute(context)) {
                agentHandler.resultAdapter().adapt(raw).forEach(result -> resultHandler.handleResult(
                        emitter,
                        clientConnected,
                        persistenceContext,
                        result,
                        terminalEventSent,
                        request.streamAssistant(),
                        request.emitTerminalEvents(),
                        outputCollector));
            }
            toolSets = toolSetLifecycle.readFrom(context);
            if (request.updateSessionStatus()) {
                sessionService.updateStatus(sessionId, SessionStatus.COMPLETED);
            }
            if (request.streamAssistant()) {
                emitPlanIfNeeded(emitter, clientConnected, persistenceContext, session, persistenceContext.assistantText());
                emitArtifactUpdates(emitter, clientConnected, persistenceContext, artifactsBefore, sessionId);
                sessionPersistenceService.finalizeAssistant(persistenceContext);
            }
        } catch (SessionNotFoundException ex) {
            failed = true;
            errorMessage = ex.getMessage();
            sessionPersistenceService.recordSystemMessage(persistenceContext, errorMessage, "error");
            if (request.emitTerminalEvents()) {
                emit(emitter, clientConnected, persistenceContext, "run.failed", Map.of("message", errorMessage));
                terminalEventSent.set(true);
            }
        } catch (RuntimeException ex) {
            LOG.warn("Agent run failed sessionId={} taskId={}", sessionId, taskId, ex);
            failed = true;
            errorMessage = ex.getMessage() != null ? ex.getMessage() : "run failed";
            sessionPersistenceService.recordSystemMessage(persistenceContext, errorMessage, "error");
            if (request.emitTerminalEvents()) {
                emit(emitter, clientConnected, persistenceContext, "run.failed", Map.of("message", errorMessage));
            }
            if (request.updateSessionStatus()) {
                sessionService.updateStatus(sessionId, SessionStatus.STOPPED);
            }
            terminalEventSent.set(true);
        } finally {
            if (request.emitTerminalEvents() && !terminalEventSent.get()) {
                emit(emitter, clientConnected, persistenceContext, "run.completed", Map.of());
            }
            if (request.streamAssistant()) {
                sessionPersistenceService.finalizeAssistant(persistenceContext);
            }
            if (toolSets != null) {
                toolSetLifecycle.unregisterAll(toolSets, memberSubRun);
            }
            runEventBroadcaster.unregisterRun(sessionId, taskId);
            if (!failed
                    && request.updateSessionStatus()
                    && request.streamAssistant()
                    && persistenceContext.parentRunId() == null) {
                memoryService.tryAutoCapture(sessionId);
            }
        }

        String text = request.streamAssistant()
                ? persistenceContext.assistantText()
                : outputCollector.toString();
        return new ExecuteOutcome(text, failed, errorMessage);
    }

    private void emitPlanIfNeeded(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext context,
            WorkmateSession session,
            String assistantText) {
        if (session.getPermissionMode() != PermissionMode.PLAN) {
            return;
        }
        planExtractService.extract(assistantText).ifPresent(plan -> {
            sessionPersistenceService.recordPlan(context, plan);
            emit(emitter, clientConnected, context, "plan.create", planPayload(plan));
        });
    }

    private Map<String, Object> planPayload(PlanPayload plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", plan.planId());
        if (plan.title() != null && !plan.title().isBlank()) {
            payload.put("title", plan.title());
        }
        List<Map<String, Object>> steps = plan.steps().stream()
                .map(step -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", step.id());
                    map.put("title", step.title());
                    map.put("status", step.status() != null ? step.status() : "pending");
                    return map;
                })
                .toList();
        payload.put("steps", steps);
        return payload;
    }

    private void emitArtifactUpdates(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext context,
            Map<String, Instant> before,
            UUID sessionId) {
        for (ArtifactResponse artifact : artifactService.diffArtifacts(before, sessionId)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("path", artifact.path());
            payload.put("name", artifact.name());
            payload.put("mime", artifact.mime());
            payload.put("size", artifact.size());
            payload.put("updatedAt", artifact.updatedAt().toString());
            if ((artifact.mime() != null && artifact.mime().contains("html"))
                    || (artifact.path() != null && artifact.path().endsWith(".html"))) {
                payload.put("openInPanel", true);
                payload.put("preferredTab", "browser");
            }
            emit(emitter, clientConnected, context, "artifact.added", payload);
        }
    }

    /** Persists leader delegation tools to session_messages, then emits matching run_events. */
    public void emitLeaderDelegationToolStart(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext context,
            String toolName,
            String toolCallId,
            Map<String, Object> args) {
        sessionPersistenceService.recordToolStart(context, toolName, args, toolCallId);
        if (toolName != null && toolName.toLowerCase().contains("send_message") && args != null) {
            String memberId = readDelegationMemberId(args);
            if (memberId != null) {
                sessionPersistenceService.recordDelegationPrompt(
                        context,
                        toolCallId,
                        memberId,
                        readDelegationString(args, "memberName", "member_name"),
                        readDelegationString(args, "message", "content", "body"),
                        readDelegationString(args, "description", "summary"));
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolName);
        payload.put("toolCallId", toolCallId);
        payload.put("args", args);
        emit(emitter, clientConnected, context, "tool.start", payload);
    }

    private static String readDelegationMemberId(Map<String, Object> args) {
        String direct = readDelegationString(args, "memberId", "member_id", "to", "recipient", "target");
        if (direct != null) {
            return direct.startsWith("@") ? direct.substring(1) : direct;
        }
        Object routing = args.get("routing");
        if (routing instanceof Map<?, ?> routingMap) {
            Object target = routingMap.get("target");
            if (target instanceof String text && !text.isBlank()) {
                return text.startsWith("@") ? text.substring(1) : text.strip();
            }
        }
        return null;
    }

    private static String readDelegationString(Map<String, Object> args, String... keys) {
        for (String key : keys) {
            Object value = args.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.strip();
            }
        }
        return null;
    }

    public void onTeamBuildCompleted(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext context,
            String parentRunId,
            String teamName,
            String displayName,
            int memberCount) {
        Object lock = emitter != null ? emitter : this;
        synchronized (lock) {
            if (context != null) {
                context.closeAssistantTurn();
            }
            TeamDelegationToolEmitter.emitBuildTeam(
                    this,
                    emitter,
                    clientConnected,
                    context,
                    parentRunId,
                    teamName,
                    displayName,
                    memberCount);
        }
    }

    public void emitLeaderMessageDelta(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext context,
            Map<String, Object> payload,
            String delta) {
        Object lock = emitter != null ? emitter : this;
        synchronized (lock) {
            String messageId = sessionPersistenceService.appendAssistantDelta(context, delta);
            if (messageId != null) {
                payload.put("messageId", messageId);
            }
            emit(emitter, clientConnected, context, "message.delta", payload);
        }
    }

    public void emitLeaderDelegationToolEnd(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext context,
            String toolName,
            String toolCallId,
            Map<String, Object> result) {
        boolean failed = !Boolean.TRUE.equals(result.get("success"));
        sessionPersistenceService.recordToolEnd(context, toolName, result, failed);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolName);
        payload.put("toolCallId", toolCallId);
        payload.put("status", failed ? "error" : "success");
        payload.put("result", result);
        emit(emitter, clientConnected, context, "tool.end", payload);
    }

    public void emit(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext context,
            String eventName,
            Map<String, Object> payload) {
        runEventEmitter.emit(emitter, clientConnected, context, eventName, payload);
    }
}
