package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.agent.RunEventBroadcaster;
import com.huawei.ascend.examples.workmate.agent.SseRunEventMapper;
import com.huawei.ascend.examples.workmate.approval.ApprovalGate;
import com.huawei.ascend.examples.workmate.approval.ApprovalGateFactory;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.question.QuestionGate;
import com.huawei.ascend.examples.workmate.question.QuestionGateFactory;
import com.huawei.ascend.examples.workmate.question.UserQuestionService;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.team.TeamBlackboardService;
import com.huawei.ascend.examples.workmate.team.TeamTerminationGuard;
import com.huawei.ascend.examples.workmate.team.TeamUserInteractionContract;
import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import com.huawei.ascend.examples.workmate.team.backend.MemberDescriptor;
import com.huawei.ascend.examples.workmate.team.backend.MemberRunResult;
import com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessage;
import com.huawei.ascend.examples.workmate.team.mailbox.TeamMailbox;
import com.huawei.ascend.examples.workmate.team.runtime.MemberRuntimeState;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerListener;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool;
import com.huawei.ascend.examples.workmate.team.runtime.TeamRuntimeManager;
import com.huawei.ascend.examples.workmate.usage.SessionUsageService;
import com.huawei.ascend.examples.workmate.usage.SessionUsageTotals;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TeamAgentRunBridge {

    private static final Logger LOG = LoggerFactory.getLogger(TeamAgentRunBridge.class);

    /** Internal mailbox id for the team leader (distinct from any member id). */
    private static final String LEADER_ID = "__lead__";

    /** Hard cap on leader reawaken turns, a safety net against runaway dispatch/reply loops. */
    private static final int MAX_LEADER_TURNS = 64;

    /** Poll interval while waiting for member handbacks to the leader (the reference mailbox cadence). */
    private static final long LEADER_POLL_INTERVAL_MS = 2_000;

    private final ExpertRegistry expertRegistry;
    private final TeamAgentSpecBuilder specBuilder;
    private final WorkmateTeamToolRegistrar toolRegistrar;
    private final OpenJiuwenTeamToolRegistrar openJiuwenTeamToolRegistrar;
    private final WorkmateTeamMemberRuntimeBootstrap memberRuntimeBootstrap;
    private final TeamStreamChunkConsumer chunkConsumer;
    private final AgentRunExecutor agentRunExecutor;
    private final SessionPersistenceService sessionPersistenceService;
    private final TeamBlackboardService teamBlackboardService;
    private final ApprovalGateFactory approvalGateFactory;
    private final QuestionGateFactory questionGateFactory;
    private final UserQuestionService userQuestionService;
    private final TeamAgentEventForwarder teamEventForwarder;
    private final SseRunEventMapper eventMapper;
    private final SessionService sessionService;
    private final RunEventBroadcaster runEventBroadcaster;
    private final TeamRuntimeManager teamRuntimeManager;
    private final SessionUsageService sessionUsageService;

    public TeamAgentRunBridge(
            ExpertRegistry expertRegistry,
            TeamAgentSpecBuilder specBuilder,
            WorkmateTeamToolRegistrar toolRegistrar,
            OpenJiuwenTeamToolRegistrar openJiuwenTeamToolRegistrar,
            WorkmateTeamMemberRuntimeBootstrap memberRuntimeBootstrap,
            TeamStreamChunkConsumer chunkConsumer,
            AgentRunExecutor agentRunExecutor,
            SessionPersistenceService sessionPersistenceService,
            TeamBlackboardService teamBlackboardService,
            ApprovalGateFactory approvalGateFactory,
            QuestionGateFactory questionGateFactory,
            UserQuestionService userQuestionService,
            TeamAgentEventForwarder teamEventForwarder,
            SseRunEventMapper eventMapper,
            SessionService sessionService,
            RunEventBroadcaster runEventBroadcaster,
            TeamRuntimeManager teamRuntimeManager,
            SessionUsageService sessionUsageService) {
        this.expertRegistry = expertRegistry;
        this.specBuilder = specBuilder;
        this.toolRegistrar = toolRegistrar;
        this.openJiuwenTeamToolRegistrar = openJiuwenTeamToolRegistrar;
        this.memberRuntimeBootstrap = memberRuntimeBootstrap;
        this.chunkConsumer = chunkConsumer;
        this.agentRunExecutor = agentRunExecutor;
        this.sessionPersistenceService = sessionPersistenceService;
        this.teamBlackboardService = teamBlackboardService;
        this.approvalGateFactory = approvalGateFactory;
        this.questionGateFactory = questionGateFactory;
        this.userQuestionService = userQuestionService;
        this.teamEventForwarder = teamEventForwarder;
        this.eventMapper = eventMapper;
        this.sessionService = sessionService;
        this.runEventBroadcaster = runEventBroadcaster;
        this.teamRuntimeManager = teamRuntimeManager;
        this.sessionUsageService = sessionUsageService;
    }

    public void run(
            WorkmateSession session,
            String message,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            RunPersistenceContext parentContext) {
        ExpertDefinition team = expertRegistry.requireExpert(session.getExpertId());
        List<TeamMemberDefinition> members = team.members();
        UUID sessionId = session.getId();
        sessionService.updateStatus(sessionId, SessionStatus.RUNNING);
        runEventBroadcaster.registerRun(sessionId, parentTaskId);
        boolean runFailed = false;
        try {
        TeamAgentSpec spec = specBuilder.build(session, team, parentTaskId);

        ApprovalGate approvalGate = approvalGateFactory.createGate();
        approvalGate.setListener(pending -> emit(
                emitter,
                clientConnected,
                parentContext,
                "approval.required",
                eventMapper.approvalPayload(pending)));
        QuestionGate questionGate = questionGateFactory.createGate();
        questionGate.setListener(pending -> {
            sessionPersistenceService.recordQuestionRequired(
                    parentContext,
                    pending.id(),
                    pending.question(),
                    pending.options(),
                    pending.allowFreeText(),
                    pending.multiSelect());
            emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "question.required",
                    userQuestionService.requiredPayload(pending));
        });
        questionGate.setCancelledListener(pending -> emit(
                emitter,
                clientConnected,
                parentContext,
                "question.cancelled",
                userQuestionService.cancelledPayload(pending)));

        TeamToolRegistrationContext toolContext = new TeamToolRegistrationContext(
                session, team, parentTaskId, approvalGate, questionGate);
        TeamToolRegistration toolRegistration = toolRegistrar.registerForTeam(spec, toolContext);

        emit(emitter, clientConnected, parentContext, "team.started",
                TeamEventSseAdapter.teamStartedPayload(
                        team, parentTaskId, members, TeamRuntimeKind.OPENJIUWEN_TEAM.yamlValue()));

        Path workspaceRoot = Path.of(session.getWorkspaceRoot());
        TeamBlackboardService.MemoryUpdate init =
                teamBlackboardService.initialize(workspaceRoot, parentTaskId, team.id(), message);
        emitTeamMemory(emitter, clientConnected, parentContext, parentTaskId, init);

        TeamTerminationGuard terminationGuard =
                new TeamTerminationGuard(team.coordination() != null ? team.coordination().termination() : null);

        boolean anyMemberFailed = false;
        boolean timeBudgetExceeded = false;
        boolean synthesisFailed = false;
        TeamAgentEventForwarder.MessagerTap messagerTap = TeamAgentEventForwarder.MessagerTap.EMPTY;
        OpenJiuwenTeamToolRegistrar.RuntimeToolRegistration runtimeTools =
                OpenJiuwenTeamToolRegistrar.RuntimeToolRegistration.EMPTY;

        AtomicInteger sendMessageSeq = new AtomicInteger(0);
        Map<String, String> activeSendToolCalls = new ConcurrentHashMap<>();
        MemberEventEmitter memberEvents = new MemberEventEmitter(
                agentRunExecutor,
                sessionPersistenceService,
                emitter,
                clientConnected,
                parentContext,
                parentTaskId,
                sessionId,
                members,
                sendMessageSeq,
                activeSendToolCalls,
                sessionUsageService);

        // Synchronous fallback path (non-openjiuwen topologies): lifecycle emitted inline.
        MemberExecutionListener memberExecutionListener = (event, memberId, detail) -> {
            switch (event) {
                case STARTED -> memberEvents.started(memberId);
                case COMPLETED -> memberEvents.completed(memberId);
                case FAILED -> memberEvents.failed(memberId, detail);
                default -> {
                    // no-op
                }
            }
        };

        // Async path (openjiuwen-team): the member worker drives lifecycle + reawaken/paused.
        MemberWorkerPool memberPool =
                teamRuntimeManager.runtimeFor(parentTaskId, LEADER_ID, sessionId.toString(), members);
        Set<String> startedMembers = ConcurrentHashMap.newKeySet();
        Set<String> activeTurnMembers = ConcurrentHashMap.newKeySet();
        MemberWorkerListener memberWorkerListener = new MemberWorkerListener() {
            @Override
            public void onStarted(MemberDescriptor member, List<MailboxMessage> inbound) {
                activeTurnMembers.add(member.memberId());
                if (startedMembers.add(member.memberId())) {
                    MailboxMessage delegation = primaryInbound(inbound, member.memberId());
                    String body = delegation != null ? delegation.body() : null;
                    String description = delegation != null ? delegation.summary() : null;
                    memberEvents.started(member.memberId(), body, description);
                } else {
                    MailboxMessage delegation = primaryInbound(inbound, member.memberId());
                    String body = delegation != null ? delegation.body() : null;
                    String description = delegation != null ? delegation.summary() : null;
                    memberEvents.reawakened(member.memberId(), body, description);
                }
            }

            @Override
            public void onCompleted(MemberDescriptor member, List<MailboxMessage> inbound, MemberRunResult result) {
                memberEvents.synthesizeImplicitHandback(memberPool, member.memberId(), result);
                memberEvents.completed(
                        member.memberId(),
                        handbackSummary(memberPool, member.memberId(), result));
            }

            @Override
            public void onFailed(MemberDescriptor member, List<MailboxMessage> inbound, MemberRunResult result) {
                memberEvents.failed(member.memberId(), result != null ? result.errorMessage() : null);
            }

            @Override
            public void onStateChanged(MemberDescriptor member, MemberRuntimeState state) {
                if (state == MemberRuntimeState.PAUSED && activeTurnMembers.remove(member.memberId())) {
                    memberEvents.paused(member.memberId());
                }
            }
        };

        try {
            TeamAgent leader = spec.build();
            TeamAgentMemberExecutionContext memberExecutionContext = new TeamAgentMemberExecutionContext(
                    session,
                    emitter,
                    clientConnected,
                    parentContext,
                    parentTaskId,
                    members,
                    memberPool,
                    sessionPersistenceService,
                    memberWorkerListener);
            memberRuntimeBootstrap.rebindMemberRuntimes(
                    leader, spec, memberExecutionListener, memberExecutionContext);
            runtimeTools = openJiuwenTeamToolRegistrar.register(leader, parentTaskId);
            String teamSessionId = TeamAgentSessionBinding.teamSessionId(session.getId());
            messagerTap = teamEventForwarder.attachMessagerTap(
                    leader.getTeamBackend().getMessager(),
                    spec,
                    teamSessionId,
                    team,
                    parentTaskId,
                    members,
                    emitter,
                    clientConnected,
                    parentContext,
                    false);

            // Leader async reawaken loop: run one leader turn, drain member replies addressed to the
            // leader, then re-run the leader with those replies as the next input. Members execute
            // concurrently on their own workers and route their output back to the leader's inbox.
            // The loop terminates once the leader has produced a turn with no pending replies and no
            // member is still active (or the time budget / max-turns guard fires).
            TeamMailbox mailbox = memberPool.mailbox();
            String leaderInbox = memberPool.leaderId();
            Object turnInput = Map.of("query", message);
            int turn = 0;
            boolean ignoreStaleMemberReplies = false;
            while (true) {
                if (terminationGuard.expired()) {
                    timeBudgetExceeded = true;
                    break;
                }
                if (++turn > MAX_LEADER_TURNS) {
                    LOG.warn("Team {} session {} reached max leader turns ({}), terminating",
                            team.id(), session.getId(), MAX_LEADER_TURNS);
                    break;
                }
                AgentSessionApi agentSession = AgentSessionApi.create(teamSessionId, Map.of(), leader.getCard());
                try {
                    agentSession.preRun(turnInput);
                    Iterator<Object> chunks =
                            leader.stream(turnInput, agentSession, List.of(StreamMode.OUTPUT));
                    while (chunks.hasNext()) {
                        if (terminationGuard.expired()) {
                            timeBudgetExceeded = true;
                            break;
                        }
                        Object chunk = chunks.next();
                        boolean failed = chunkConsumer.consume(
                                chunk,
                                team,
                                parentTaskId,
                                members,
                                emitter,
                                clientConnected,
                                parentContext,
                                true);
                        anyMemberFailed |= failed;
                    }
                } finally {
                    agentSession.postRun();
                }
                if (timeBudgetExceeded) {
                    break;
                }
                if (!memberPool.hasActiveWork()) {
                    ignoreStaleMemberReplies = true;
                }
                List<MailboxMessage> replies =
                        waitForLeaderReplies(mailbox, leaderInbox, memberPool, terminationGuard);
                if (!replies.isEmpty()) {
                    if (ignoreStaleMemberReplies) {
                        LOG.debug(
                                "Team {} session {} ignoring {} stale member replies after all members idle",
                                team.id(),
                                session.getId(),
                                replies.size());
                        if (!memberPool.hasActiveWork()) {
                            break;
                        }
                        continue;
                    }
                    turnInput = Map.of("query", formatLeaderReplies(replies, sessionId));
                    continue;
                }
                if (terminationGuard.expired()) {
                    timeBudgetExceeded = true;
                }
                // Leader produced a turn, no replies pending, all members idle → terminate.
                break;
            }
        } catch (Exception ex) {
            LOG.warn("TeamAgent run failed for team {} session {}", team.id(), session.getId(), ex);
            anyMemberFailed = true;
            synthesisFailed = true;
            runFailed = true;
            agentRunExecutor.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "run.error",
                    Map.of("message", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
        } finally {
            messagerTap.close();
            openJiuwenTeamToolRegistrar.unregister(runtimeTools);
            toolRegistrar.unregister(toolRegistration, team, session.getId());
        }

        emit(emitter, clientConnected, parentContext, "team.completed",
                TeamEventSseAdapter.teamCompletedPayload(
                        team, parentTaskId, members.size(), anyMemberFailed, timeBudgetExceeded, synthesisFailed));
        emit(emitter, clientConnected, parentContext, "run.completed", Map.of());
        } finally {
            teamRuntimeManager.release(parentTaskId);
            runEventBroadcaster.unregisterRun(sessionId, parentTaskId);
            sessionService.updateStatus(sessionId, runFailed ? SessionStatus.STOPPED : SessionStatus.COMPLETED);
        }
    }

    private void emitTeamMemory(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String parentRunId,
            TeamBlackboardService.MemoryUpdate update) {
        emit(emitter, clientConnected, parentContext, "team.memory",
                teamBlackboardService.memoryPayload(parentRunId, update));
    }

    private void emit(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String eventName,
            Map<String, Object> payload) {
        agentRunExecutor.emit(emitter, clientConnected, parentContext, eventName, payload);
    }

    private static TeamMemberDefinition resolveMember(List<TeamMemberDefinition> members, String memberKey) {
        if (memberKey == null || memberKey.isBlank()) {
            return null;
        }
        return members.stream()
                .filter(member -> member.id().equals(memberKey) || member.name().equals(memberKey))
                .findFirst()
                .orElse(null);
    }

    /**
     * Pick the delegation message that seeded a member's run: prefer one addressed directly to the
     * member with a non-blank body, else the first inbound with a body. Returns {@code null} when no
     * usable body is present (caller degrades to the member's task name).
     */
    private static MailboxMessage primaryInbound(List<MailboxMessage> inbound, String memberId) {
        if (inbound == null || inbound.isEmpty()) {
            return null;
        }
        MailboxMessage fallback = null;
        for (MailboxMessage message : inbound) {
            if (message == null || message.body() == null || message.body().isBlank()) {
                continue;
            }
            if (fallback == null) {
                fallback = message;
            }
            if (memberId != null && memberId.equals(message.to())) {
                return message;
            }
        }
        return fallback;
    }

    /**
     * Wait for member handbacks addressed to the leader. Polls at {@link #LEADER_POLL_INTERVAL_MS}
     * while members are still active so the leader is not re-run until a reply arrives or all
     * members go idle (reference-aligned blocking wait).
     */
    private static List<MailboxMessage> waitForLeaderReplies(
            TeamMailbox mailbox,
            String leaderInbox,
            MemberWorkerPool pool,
            TeamTerminationGuard terminationGuard) {
        List<MailboxMessage> immediate = mailbox.drainUnread(leaderInbox);
        if (!immediate.isEmpty()) {
            return immediate;
        }
        while (!terminationGuard.expired()) {
            if (!pool.hasActiveWork() && !mailbox.hasUnread(leaderInbox)) {
                break;
            }
            try {
                Thread.sleep(LEADER_POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            List<MailboxMessage> mail = mailbox.drainUnread(leaderInbox);
            if (!mail.isEmpty()) {
                return mail;
            }
            if (!pool.hasActiveWork()) {
                break;
            }
        }
        return mailbox.drainUnread(leaderInbox);
    }

    /** Prefer explicit {@code send_message} summary; fall back to assistant / remote body preview. */
    private static String handbackSummary(MemberWorkerPool pool, String memberId, MemberRunResult result) {
        if (pool != null) {
            String explicit = pool.explicitHandbackSummary(memberId);
            if (!explicit.isBlank()) {
                return explicit;
            }
        }
        return result != null && result.assistantText() != null ? result.assistantText().strip() : "";
    }

    /** Combine member replies into a single leader input for the next reawaken turn. */
    private static String formatLeaderReplies(List<MailboxMessage> replies, UUID sessionId) {
        StringBuilder builder = new StringBuilder();
        builder.append("以下是团队成员的最新回传，请据此继续推进任务"
                + "（若已满足目标，请直接给出最终答复并结束本轮协调）：\n\n");
        boolean outlineHandback = false;
        for (MailboxMessage reply : replies) {
            builder.append(reply.body()).append("\n\n");
            if (!outlineHandback
                    && TeamUserInteractionContract.isResearchPlannerOutlineHandback(
                            reply.from(), reply.body())) {
                outlineHandback = true;
            }
        }
        if (outlineHandback) {
            builder.append(TeamUserInteractionContract.outlineConfirmationReminder(
                    WorkmateToolIds.askUserQuestion(sessionId)));
        }
        return builder.toString().strip();
    }

    /**
     * Centralizes member lifecycle SSE emission (team.member.* events + the {@code send_message}
     * tool card) so both the synchronous fallback listener and the async worker listener stay in
     * sync. Thread-safe for concurrent members: state is held in concurrent collections and the
     * underlying emit serializes per-run.
     */
    private static final class MemberEventEmitter {

        private final AgentRunExecutor agentRunExecutor;
        private final SessionPersistenceService sessionPersistenceService;
        private final SseEmitter emitter;
        private final AtomicBoolean clientConnected;
        private final RunPersistenceContext parentContext;
        private final String parentTaskId;
        private final UUID sessionId;
        private final List<TeamMemberDefinition> members;
        private final AtomicInteger sendMessageSeq;
        private final Map<String, String> activeSendToolCalls;
        private final SessionUsageService sessionUsageService;

        MemberEventEmitter(
                AgentRunExecutor agentRunExecutor,
                SessionPersistenceService sessionPersistenceService,
                SseEmitter emitter,
                AtomicBoolean clientConnected,
                RunPersistenceContext parentContext,
                String parentTaskId,
                UUID sessionId,
                List<TeamMemberDefinition> members,
                AtomicInteger sendMessageSeq,
                Map<String, String> activeSendToolCalls,
                SessionUsageService sessionUsageService) {
            this.agentRunExecutor = agentRunExecutor;
            this.sessionPersistenceService = sessionPersistenceService;
            this.emitter = emitter;
            this.clientConnected = clientConnected;
            this.parentContext = parentContext;
            this.parentTaskId = parentTaskId;
            this.sessionId = sessionId;
            this.members = members;
            this.sendMessageSeq = sendMessageSeq;
            this.activeSendToolCalls = activeSendToolCalls;
            this.sessionUsageService = sessionUsageService;
        }

        void started(String memberId) {
            started(memberId, null, null);
        }

        /**
         * First spawn of a member: emit lifecycle event and a delegation (send_message) card that
         * carries the delegated task body/description.
         */
        void started(String memberId, String delegationMessage, String delegationDescription) {
            TeamMemberDefinition memberDef = resolveMember(members, memberId);
            if (memberDef == null) {
                return;
            }
            String subRunId = TeamAgentSessionBinding.subRunId(parentTaskId, memberDef.id());
            emit("team.member.started", TeamEventSseAdapter.memberStartedPayload(parentTaskId, subRunId, memberDef));
            beginSendMessage(memberDef, delegationMessage, delegationDescription, false);
        }

        /**
         * Re-delivery to an already-spawned member. Emits lifecycle plus a new delegation card for
         * the latest mailbox body so the member surface shows the current task (the reference workbench: each
         * Agent spawn carries a fresh prompt; WorkMate maps re-task to a new send_message card).
         */
        void reawakened(String memberId) {
            reawakened(memberId, null, null);
        }

        void reawakened(String memberId, String delegationMessage, String delegationDescription) {
            TeamMemberDefinition memberDef = resolveMember(members, memberId);
            if (memberDef == null) {
                return;
            }
            String subRunId = TeamAgentSessionBinding.subRunId(parentTaskId, memberDef.id());
            emit(
                    "team.member.reawakened",
                    TeamEventSseAdapter.memberReawakenedPayload(parentTaskId, subRunId, memberDef));
            endSendMessage(memberDef, true, null, null);
            beginSendMessage(memberDef, delegationMessage, delegationDescription, true);
        }

        /**
         * Remote A2A members cannot invoke the local {@code send_message} tool; synthesize member-scoped
         * tool cards so the team surface shows the the reference workbench receive card.
         */
        void synthesizeImplicitHandback(MemberWorkerPool pool, String memberId, MemberRunResult result) {
            if (result == null || result.failed()) {
                return;
            }
            if (pool != null && pool.hasExplicitHandback(memberId)) {
                return;
            }
            if (!"a2a".equals(result.backendKind())) {
                return;
            }
            TeamMemberDefinition memberDef = resolveMember(members, memberId);
            if (memberDef == null) {
                return;
            }
            String content = result.assistantText();
            if (content.isBlank()) {
                return;
            }
            MemberHandbackToolEmitter.emitRemoteHandback(
                    agentRunExecutor,
                    sessionPersistenceService,
                    emitter,
                    clientConnected,
                    sessionId,
                    parentTaskId,
                    memberDef,
                    content,
                    handbackSummary(pool, memberId, result),
                    MemberHandbackToolEmitter.handbackToolCallId(parentTaskId, memberId),
                    result.backendKind());
        }

        void completed(String memberId) {
            completed(memberId, null);
        }

        void completed(String memberId, String summary) {
            TeamMemberDefinition memberDef = resolveMember(members, memberId);
            if (memberDef == null) {
                return;
            }
            String subRunId = TeamAgentSessionBinding.subRunId(parentTaskId, memberDef.id());
            String cleanSummary = summary == null ? "" : summary.strip();
            Map<String, Object> payload =
                    TeamEventSseAdapter.memberCompletedPayload(parentTaskId, subRunId, memberDef, cleanSummary);
            attachMemberUsageTotals(payload, subRunId);
            emit("team.member.completed", payload);
            endSendMessage(memberDef, true, null, cleanSummary);
        }

        private void attachMemberUsageTotals(Map<String, Object> payload, String subRunId) {
            if (sessionUsageService == null || sessionId == null || subRunId == null) {
                return;
            }
            SessionUsageTotals totals = sessionUsageService.totalsForRun(sessionId, subRunId);
            if (totals.promptTokens() > 0) {
                payload.put("promptTokens", totals.promptTokens());
            }
            if (totals.completionTokens() > 0) {
                payload.put("completionTokens", totals.completionTokens());
            }
        }

        void failed(String memberId, String detail) {
            TeamMemberDefinition memberDef = resolveMember(members, memberId);
            if (memberDef == null) {
                return;
            }
            String subRunId = TeamAgentSessionBinding.subRunId(parentTaskId, memberDef.id());
            emit(
                    "team.member.failed",
                    TeamEventSseAdapter.memberFailedPayload(parentTaskId, subRunId, memberDef, detail));
            endSendMessage(memberDef, false, detail, null);
        }

        void paused(String memberId) {
            TeamMemberDefinition memberDef = resolveMember(members, memberId);
            if (memberDef == null) {
                return;
            }
            String subRunId = TeamAgentSessionBinding.subRunId(parentTaskId, memberDef.id());
            emit("team.member.paused", TeamEventSseAdapter.memberPausedPayload(parentTaskId, subRunId, memberDef));
        }

        private void beginSendMessage(
                TeamMemberDefinition memberDef,
                String delegationMessage,
                String delegationDescription,
                boolean reawaken) {
            int seq = sendMessageSeq.incrementAndGet();
            String toolCallId = TeamDelegationToolEmitter.emitSendMessageStart(
                    agentRunExecutor,
                    emitter,
                    clientConnected,
                    parentContext,
                    parentTaskId,
                    memberDef,
                    seq,
                    delegationMessage,
                    delegationDescription,
                    reawaken);
            activeSendToolCalls.put(memberDef.id(), toolCallId);
        }

        private void endSendMessage(
                TeamMemberDefinition memberDef, boolean success, String detail, String summary) {
            String toolCallId = activeSendToolCalls.remove(memberDef.id());
            if (toolCallId != null) {
                TeamDelegationToolEmitter.emitSendMessageEnd(
                        agentRunExecutor,
                        emitter,
                        clientConnected,
                        parentContext,
                        toolCallId,
                        memberDef,
                        success,
                        detail,
                        summary);
            }
        }

        private void emit(String eventName, Map<String, Object> payload) {
            agentRunExecutor.emit(emitter, clientConnected, parentContext, eventName, payload);
        }
    }
}
