package com.huawei.ascend.examples.workmate.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteRequest;
import com.huawei.ascend.examples.workmate.agent.dto.PromptRunResult;
import com.huawei.ascend.examples.workmate.cloud.CloudRunRouter;
import com.huawei.ascend.examples.workmate.chat.SessionMessage;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.mention.MentionContextService;
import com.huawei.ascend.examples.workmate.mention.dto.MentionItem;
import com.huawei.ascend.examples.workmate.prompt.dto.PromptRequest;
import com.huawei.ascend.examples.workmate.prompt.dto.UserAttachmentItem;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.session.SessionBusyException;
import com.huawei.ascend.examples.workmate.session.SessionRunLock;
import com.huawei.ascend.examples.workmate.session.SessionRunQueue;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.team.TeamOrchestrator;
import com.huawei.ascend.examples.workmate.team.agent.TeamAgentRunBridge;
import com.huawei.ascend.examples.workmate.team.agent.TeamRuntimeRouter;
import com.huawei.ascend.examples.workmate.tenant.TenantQuotaService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentRunService {

    private static final Logger LOG = LoggerFactory.getLogger(AgentRunService.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final SessionService sessionService;
    private final SseRunEventMapper eventMapper;
    private final WorkmateLlmProperties llm;
    private final SessionPersistenceService sessionPersistenceService;
    private final RunStreamService runStreamService;
    private final SessionRunLock sessionRunLock;
    private final SessionRunQueue sessionRunQueue;
    private final AgentRunExecutor agentRunExecutor;
    private final TeamOrchestrator teamOrchestrator;
    private final TeamRuntimeRouter teamRuntimeRouter;
    private final TeamAgentRunBridge teamAgentRunBridge;
    private final ExpertRegistry expertRegistry;
    private final ObjectMapper objectMapper;
    private final MentionContextService mentionContextService;
    private final TenantQuotaService tenantQuotaService;
    private final CloudRunRouter cloudRunRouter;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentRunService(
            SessionService sessionService,
            SseRunEventMapper eventMapper,
            WorkmateLlmProperties llm,
            SessionPersistenceService sessionPersistenceService,
            RunStreamService runStreamService,
            SessionRunLock sessionRunLock,
            SessionRunQueue sessionRunQueue,
            AgentRunExecutor agentRunExecutor,
            TeamOrchestrator teamOrchestrator,
            TeamRuntimeRouter teamRuntimeRouter,
            TeamAgentRunBridge teamAgentRunBridge,
            ExpertRegistry expertRegistry,
            ObjectMapper objectMapper,
            MentionContextService mentionContextService,
            TenantQuotaService tenantQuotaService,
            CloudRunRouter cloudRunRouter) {
        this.sessionService = sessionService;
        this.eventMapper = eventMapper;
        this.llm = llm;
        this.sessionPersistenceService = sessionPersistenceService;
        this.runStreamService = runStreamService;
        this.sessionRunLock = sessionRunLock;
        this.sessionRunQueue = sessionRunQueue;
        this.agentRunExecutor = agentRunExecutor;
        this.teamOrchestrator = teamOrchestrator;
        this.teamRuntimeRouter = teamRuntimeRouter;
        this.teamAgentRunBridge = teamAgentRunBridge;
        this.expertRegistry = expertRegistry;
        this.objectMapper = objectMapper;
        this.mentionContextService = mentionContextService;
        this.tenantQuotaService = tenantQuotaService;
        this.cloudRunRouter = cloudRunRouter;
    }

    public PromptRunResult runPrompt(UUID sessionId, PromptRequest request) {
        tenantQuotaService.assertWithinTokenQuota();
        WorkmateSession session = sessionService.requireSession(sessionId);
        if (isSessionBusy(session)) {
            int position = sessionRunQueue.enqueue(
                    sessionId, request.message(), request.mentions(), request.attachments());
            return new PromptRunResult.Queued(position, position);
        }
        SseEmitter emitter = startRun(sessionId, (activeSession, taskId, activeEmitter) ->
                runPromptAsync(
                        activeSession,
                        request.message(),
                        request.mentions(),
                        request.attachments(),
                        activeEmitter,
                        taskId));
        return new PromptRunResult.Started(emitter);
    }

    public PromptRunResult runPrompt(UUID sessionId, String message) {
        return runPrompt(sessionId, new PromptRequest(message, List.of()));
    }

    /** v1.0 — automation/cron: start run without holding SSE client. */
    public void runPromptFireAndForget(UUID sessionId, String message) {
        runPrompt(sessionId, message);
    }

    public SseEmitter editMessage(UUID sessionId, int seq, String message) {
        sessionPersistenceService.requireActiveUserMessage(sessionId, seq);
        return startRun(sessionId, (session, taskId, emitter) -> {
            sessionPersistenceService.truncateFrom(sessionId, seq, "edit", taskId);
            runPromptAsync(session, message, List.of(), List.of(), emitter, taskId);
        });
    }

    public SseEmitter retry(UUID sessionId) {
        SessionMessage lastUser = sessionPersistenceService.requireLastActiveUserMessage(sessionId);
        String text = SessionPersistenceService.readUserText(lastUser, objectMapper);
        int seq = lastUser.getSeq();
        return startRun(sessionId, (session, taskId, emitter) -> {
            sessionPersistenceService.truncateFrom(sessionId, seq, "retry", taskId);
            runPromptAsync(session, text, List.of(), List.of(), emitter, taskId);
        });
    }

    public int queueDepth(UUID sessionId) {
        return sessionRunQueue.depth(sessionId);
    }

    public int clearQueue(UUID sessionId) {
        return sessionRunQueue.clear(sessionId);
    }

    private interface RunStarter {
        void run(WorkmateSession session, String taskId, SseEmitter emitter);
    }

    private boolean isSessionBusy(WorkmateSession session) {
        return session.getStatus() == SessionStatus.RUNNING || sessionRunLock.isLocked(session.getId());
    }

    private SseEmitter startRun(UUID sessionId, RunStarter starter) {
        WorkmateSession session = sessionService.requireSession(sessionId);
        if (isSessionBusy(session)) {
            throw new SessionBusyException(sessionId);
        }
        String taskId = UUID.randomUUID().toString();
        if (!sessionRunLock.tryAcquire(sessionId, taskId)) {
            throw new SessionBusyException(sessionId);
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        executor.submit(() -> {
            try {
                starter.run(session, taskId, emitter);
            } finally {
                sessionRunLock.release(sessionId, taskId);
                drainQueue(sessionId);
            }
        });
        return emitter;
    }

    private void drainQueue(UUID sessionId) {
        sessionRunQueue.poll(sessionId).ifPresent(queued -> {
            String taskId = UUID.randomUUID().toString();
            if (!sessionRunLock.tryAcquire(sessionId, taskId)) {
                LOG.warn("Could not acquire run lock to drain queued prompt for session {}", sessionId);
                return;
            }
            executor.submit(() -> {
                try {
                    WorkmateSession session = sessionService.requireSession(sessionId);
                    runPromptAsync(session, queued.message(), queued.mentions(), queued.attachments(), null, taskId);
                } catch (Exception ex) {
                    LOG.warn("Queued prompt run failed for session {}", sessionId, ex);
                } finally {
                    sessionRunLock.release(sessionId, taskId);
                    drainQueue(sessionId);
                }
            });
        });
    }

    private void runPromptAsync(
            WorkmateSession session,
            String message,
            List<MentionItem> mentions,
            List<UserAttachmentItem> attachments,
            SseEmitter emitter,
            String taskId) {
        java.util.concurrent.ScheduledFuture<?> heartbeat =
                emitter != null ? runStreamService.startHeartbeat(emitter) : null;
        AtomicBoolean clientConnected = new AtomicBoolean(emitter != null);
        if (emitter != null) {
            emitter.onCompletion(() -> clientConnected.set(false));
            emitter.onTimeout(() -> clientConnected.set(false));
            emitter.onError(ex -> clientConnected.set(false));
        }

        if (!llm.isConfigured()) {
            trySend(emitter, eventMapper.runFailed(
                    "LLM not configured. Set WORKMATE_LLM_API_KEY (and optional WORKMATE_LLM_API_BASE / WORKMATE_LLM_MODEL)."));
            completeEmitter(emitter);
            cancelHeartbeat(heartbeat);
            return;
        }

        UUID sessionId = session.getId();
        RunPersistenceContext persistenceContext = sessionPersistenceService.beginRun(
                sessionId,
                taskId,
                message,
                mentionContextService.toPayload(mentions),
                toAttachmentPayload(attachments));
        if (mentions != null && !mentions.isEmpty()) {
            sessionPersistenceService.persistRunEvent(
                    persistenceContext,
                    "message.mentions",
                    Map.of("mentions", mentionContextService.toPayload(mentions)));
        }

        try {
            if (isTeamSession(session)) {
                String teamMessage = withMentionContext(session, message, mentions);
                if (teamRuntimeRouter.isOpenJiuwenTeam(session.getExpertId())) {
                    teamAgentRunBridge.run(
                            session, teamMessage, emitter, clientConnected, taskId, persistenceContext);
                } else {
                    teamOrchestrator.runTeam(
                            session, teamMessage, emitter, clientConnected, taskId, persistenceContext);
                }
            } else {
                boolean routed = cloudRunRouter.tryExecuteRemote(
                        session,
                        message,
                        taskId,
                        persistenceContext,
                        emitter,
                        clientConnected);
                if (!routed) {
                    agentRunExecutor.execute(new ExecuteRequest(
                            session,
                            message,
                            taskId,
                            session.getExpertId(),
                            persistenceContext,
                            emitter,
                            clientConnected,
                            true,
                            true,
                            true,
                            null,
                            mentions));
                }
            }
        } finally {
            cancelHeartbeat(heartbeat);
            if (clientConnected.get()) {
                completeEmitter(emitter);
            }
        }
    }

    private static void cancelHeartbeat(java.util.concurrent.ScheduledFuture<?> heartbeat) {
        if (heartbeat != null) {
            heartbeat.cancel(true);
        }
    }

    private static void completeEmitter(SseEmitter emitter) {
        if (emitter != null) {
            emitter.complete();
        }
    }

    private boolean isTeamSession(WorkmateSession session) {
        if (session.getExpertId() == null || session.getExpertId().isBlank()) {
            return false;
        }
        return expertRegistry.findExpert(session.getExpertId())
                .map(expert -> expert.isTeam())
                .orElse(false);
    }

    private String withMentionContext(WorkmateSession session, String message, List<MentionItem> mentions) {
        StringBuilder prefix = new StringBuilder();
        if (isTeamSession(session)
                && session.getStatus() == SessionStatus.COMPLETED
                && mentionContextService.hasMemberMentions(mentions)) {
            String followUp = mentionContextService.buildTeamFollowUpDelegationSection(
                    session.getExpertId(), mentions);
            if (!followUp.isBlank()) {
                prefix.append(followUp).append("\n\n");
            }
        }
        if (mentions == null || mentions.isEmpty()) {
            return prefix.isEmpty() ? message : prefix + "User request:\n" + message;
        }
        String section = mentionContextService.buildPromptSection(
                session.getId(), session.getExpertId(), mentions);
        if (section.isBlank()) {
            return prefix.isEmpty() ? message : prefix + "User request:\n" + message;
        }
        return prefix + section + "\n\nUser request:\n" + message;
    }

    private static List<Map<String, Object>> toAttachmentPayload(List<UserAttachmentItem> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .map(item -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("path", item.path());
                    if (item.name() != null && !item.name().isBlank()) {
                        payload.put("name", item.name());
                    }
                    if (item.mime() != null && !item.mime().isBlank()) {
                        payload.put("mime", item.mime());
                    }
                    return payload;
                })
                .toList();
    }

    static boolean trySend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        if (emitter == null) {
            return true;
        }
        try {
            emitter.send(event);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
