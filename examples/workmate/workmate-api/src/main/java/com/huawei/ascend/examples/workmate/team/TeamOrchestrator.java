package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteRequest;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.team.agent.TeamEventSseAdapter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TeamOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(TeamOrchestrator.class);

    private final ExpertRegistry expertRegistry;
    private final SessionPersistenceService sessionPersistenceService;
    private final AgentRunExecutor agentRunExecutor;
    private final GeneratorVerifierOrchestrator generatorVerifierOrchestrator;
    private final MessageBusOrchestrator messageBusOrchestrator;
    private final SharedStateOrchestrator sharedStateOrchestrator;
    private final TeamBlackboardService teamBlackboardService;
    private final MemberRunRouter memberRunRouter;
    private final TeamEventEmitter teamEventEmitter;
    private final TeamAnswerPublisher teamAnswerPublisher;
    private final TeamMemberPayloadFactory memberPayloadFactory;

    public TeamOrchestrator(
            ExpertRegistry expertRegistry,
            SessionPersistenceService sessionPersistenceService,
            AgentRunExecutor agentRunExecutor,
            GeneratorVerifierOrchestrator generatorVerifierOrchestrator,
            MessageBusOrchestrator messageBusOrchestrator,
            SharedStateOrchestrator sharedStateOrchestrator,
            TeamBlackboardService teamBlackboardService,
            MemberRunRouter memberRunRouter,
            TeamEventEmitter teamEventEmitter,
            TeamAnswerPublisher teamAnswerPublisher,
            TeamMemberPayloadFactory memberPayloadFactory) {
        this.expertRegistry = expertRegistry;
        this.sessionPersistenceService = sessionPersistenceService;
        this.agentRunExecutor = agentRunExecutor;
        this.generatorVerifierOrchestrator = generatorVerifierOrchestrator;
        this.messageBusOrchestrator = messageBusOrchestrator;
        this.sharedStateOrchestrator = sharedStateOrchestrator;
        this.teamBlackboardService = teamBlackboardService;
        this.memberRunRouter = memberRunRouter;
        this.teamEventEmitter = teamEventEmitter;
        this.teamAnswerPublisher = teamAnswerPublisher;
        this.memberPayloadFactory = memberPayloadFactory;
    }

    public void runTeam(
            WorkmateSession session,
            String message,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            RunPersistenceContext parentContext) {
        ExpertDefinition team = expertRegistry.requireExpert(session.getExpertId());
        if (CoordinationSpec.GENERATOR_VERIFIER.equals(team.coordinationPattern())) {
            generatorVerifierOrchestrator.runTeam(
                    session, message, emitter, clientConnected, parentTaskId, parentContext);
            return;
        }
        if (CoordinationSpec.MESSAGE_BUS.equals(team.coordinationPattern())) {
            messageBusOrchestrator.runTeam(
                    session, message, emitter, clientConnected, parentTaskId, parentContext);
            return;
        }
        if (CoordinationSpec.SHARED_STATE.equals(team.coordinationPattern())) {
            sharedStateOrchestrator.runTeam(
                    session, message, emitter, clientConnected, parentTaskId, parentContext);
            return;
        }
        List<TeamMemberDefinition> members = team.members();
        if (members.size() < 2) {
            throw new IllegalStateException("Team expert " + team.id() + " requires at least 2 members");
        }

        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.started",
                TeamEventSseAdapter.teamStartedPayload(team, parentTaskId, members));

        Path workspaceRoot = Path.of(session.getWorkspaceRoot());
        TeamBlackboardService.MemoryUpdate init =
                teamBlackboardService.initialize(workspaceRoot, parentTaskId, team.id(), message);
        teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, init);

        TeamTerminationGuard terminationGuard =
                new TeamTerminationGuard(team.coordination() != null ? team.coordination().termination() : null);
        boolean anyMemberFailed = false;
        boolean timeBudgetExceeded = false;
        boolean synthesisFailed = false;

        if (TeamEventSseAdapter.isParallelFanOut(team)) {
            ParallelMemberResult parallel =
                    runMembersParallel(
                            session,
                            message,
                            emitter,
                            clientConnected,
                            parentTaskId,
                            parentContext,
                            workspaceRoot,
                            team,
                            members,
                            terminationGuard);
            anyMemberFailed = parallel.anyMemberFailed();
            timeBudgetExceeded = parallel.timeBudgetExceeded();
        } else {
            SequentialMemberResult sequential =
                    runMembersSequential(
                            session,
                            message,
                            emitter,
                            clientConnected,
                            parentTaskId,
                            parentContext,
                            workspaceRoot,
                            members,
                            terminationGuard);
            anyMemberFailed = sequential.anyMemberFailed();
            timeBudgetExceeded = sequential.timeBudgetExceeded();
        }

        if (terminationGuard.expired()) {
            timeBudgetExceeded = true;
        }

        if (!timeBudgetExceeded) {
            String synthesisPrompt = synthesisPrompt(message, teamBlackboardService.read(workspaceRoot, parentTaskId));
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.lead.synthesizing",
                    Map.of("teamId", team.id(), "parentRunId", parentTaskId));
            ExecuteOutcome synthesis = agentRunExecutor.execute(new ExecuteRequest(
                    session,
                    synthesisPrompt,
                    parentTaskId,
                    team.id(),
                    parentContext,
                    emitter,
                    clientConnected,
                    true,
                    true,
                    true));

            if (synthesis.failed()) {
                synthesisFailed = true;
                LOG.warn(
                        "Team synthesis failed teamId={} sessionId={} parentRunId={}",
                        team.id(),
                        session.getId(),
                        parentTaskId);
            }
        } else {
            LOG.warn(
                    "Team run skipped synthesis due to time budget teamId={} sessionId={} parentRunId={}",
                    team.id(),
                    session.getId(),
                    parentTaskId);
            String partial = teamBlackboardService.read(workspaceRoot, parentTaskId);
            if (!partial.isBlank()) {
                teamAnswerPublisher.publish(
                        emitter,
                        clientConnected,
                        parentContext,
                        partial + "\n\n---\n*团队时间预算已用尽，以上为黑板上的部分产出。*");
            }
        }

        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.completed",
                TeamEventSseAdapter.teamCompletedPayload(
                        team, parentTaskId, members.size(), anyMemberFailed, timeBudgetExceeded, synthesisFailed));
    }

    private record SequentialMemberResult(boolean anyMemberFailed, boolean timeBudgetExceeded) {}

    private record ParallelMemberResult(boolean anyMemberFailed, boolean timeBudgetExceeded) {}

    private record MemberRunOutcome(
            TeamMemberDefinition member, String subRunId, ExecuteOutcome outcome, boolean skipped) {}

    private SequentialMemberResult runMembersSequential(
            WorkmateSession session,
            String message,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            RunPersistenceContext parentContext,
            Path workspaceRoot,
            List<TeamMemberDefinition> members,
            TeamTerminationGuard terminationGuard) {
        boolean anyMemberFailed = false;
        boolean timeBudgetExceeded = false;
        for (TeamMemberDefinition member : members) {
            if (terminationGuard.expired()) {
                timeBudgetExceeded = true;
                break;
            }
            MemberRunOutcome run = executeMemberRun(
                    session,
                    message,
                    emitter,
                    clientConnected,
                    parentTaskId,
                    parentContext,
                    workspaceRoot,
                    member,
                    teamBlackboardService.read(workspaceRoot, parentTaskId));
            if (run.outcome().failed()) {
                anyMemberFailed = true;
            }
        }
        return new SequentialMemberResult(anyMemberFailed, timeBudgetExceeded);
    }

    private ParallelMemberResult runMembersParallel(
            WorkmateSession session,
            String message,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            RunPersistenceContext parentContext,
            Path workspaceRoot,
            ExpertDefinition team,
            List<TeamMemberDefinition> members,
            TeamTerminationGuard terminationGuard) {
        if (terminationGuard.expired()) {
            return new ParallelMemberResult(false, true);
        }
        String blackboardSnapshot = teamBlackboardService.read(workspaceRoot, parentTaskId);
        for (TeamMemberDefinition member : members) {
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.member.started",
                    memberPayloadFactory.started(parentTaskId, parentTaskId + ":" + member.id(), member));
        }
        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.parallel.started",
                Map.of(
                        "teamId", team.id(),
                        "parentRunId", parentTaskId,
                        "memberCount", members.size(),
                        "pattern", CoordinationSpec.AGENT_TEAM));

        boolean anyMemberFailed = false;
        boolean timeBudgetExceeded = false;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<MemberRunOutcome>> tasks = new ArrayList<>();
            for (TeamMemberDefinition member : members) {
                tasks.add(() -> runMemberInParallel(
                        session,
                        message,
                        emitter,
                        clientConnected,
                        parentTaskId,
                        workspaceRoot,
                        member,
                        blackboardSnapshot));
            }
            List<Future<MemberRunOutcome>> futures = new ArrayList<>();
            for (Callable<MemberRunOutcome> task : tasks) {
                futures.add(pool.submit(task));
            }
            for (Future<MemberRunOutcome> future : futures) {
                MemberRunOutcome run;
                try {
                    Long remaining = terminationGuard.remainingMs();
                    if (remaining != null) {
                        run = future.get(remaining, TimeUnit.MILLISECONDS);
                    } else {
                        run = future.get();
                    }
                } catch (TimeoutException ex) {
                    future.cancel(true);
                    timeBudgetExceeded = true;
                    futures.forEach(f -> f.cancel(true));
                    break;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    timeBudgetExceeded = true;
                    futures.forEach(f -> f.cancel(true));
                    break;
                } catch (ExecutionException ex) {
                    anyMemberFailed = true;
                    continue;
                }
                recordMemberOutcome(
                        emitter, clientConnected, parentContext, parentTaskId, workspaceRoot, run);
                if (run.outcome().failed()) {
                    anyMemberFailed = true;
                }
            }
        }
        return new ParallelMemberResult(anyMemberFailed, timeBudgetExceeded);
    }

    private MemberRunOutcome runMemberInParallel(
            WorkmateSession session,
            String message,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            Path workspaceRoot,
            TeamMemberDefinition member,
            String blackboardSnapshot) {
        String subRunId = parentTaskId + ":" + member.id();
        RunPersistenceContext subContext =
                sessionPersistenceService.beginSubRun(session.getId(), subRunId, parentTaskId, member.id(), member.name());
        String memberPrompt = parallelMemberDelegationPrompt(message, member, blackboardSnapshot);
        ExecuteOutcome outcome = memberRunRouter.executeMember(new ExecuteRequest(
                session,
                memberPrompt,
                subRunId,
                member.expertId(),
                subContext,
                emitter,
                clientConnected,
                false,
                false,
                false), member.expertId());
        return new MemberRunOutcome(member, subRunId, outcome, false);
    }

    private MemberRunOutcome executeMemberRun(
            WorkmateSession session,
            String message,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            RunPersistenceContext parentContext,
            Path workspaceRoot,
            TeamMemberDefinition member,
            String blackboardContext) {
        String subRunId = parentTaskId + ":" + member.id();
        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.member.started",
                memberPayloadFactory.started(parentTaskId, subRunId, member));

        RunPersistenceContext subContext =
                sessionPersistenceService.beginSubRun(session.getId(), subRunId, parentTaskId, member.id(), member.name());

        String memberPrompt = memberDelegationPrompt(message, member, blackboardContext);
        ExecuteOutcome outcome = memberRunRouter.executeMember(new ExecuteRequest(
                session,
                memberPrompt,
                subRunId,
                member.expertId(),
                subContext,
                emitter,
                clientConnected,
                false,
                false,
                false), member.expertId());

        MemberRunOutcome run = new MemberRunOutcome(member, subRunId, outcome, false);
        recordMemberOutcome(emitter, clientConnected, parentContext, parentTaskId, workspaceRoot, run);
        return run;
    }

    private void recordMemberOutcome(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String parentTaskId,
            Path workspaceRoot,
            MemberRunOutcome run) {
        Map<String, Object> payload = TeamRunPayloads.memberPayload(parentTaskId, run.subRunId(), run.member());
        if (run.outcome().failed()) {
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.member.failed",
                    TeamRunPayloads.withError(payload, run.outcome().errorMessage()));
            TeamBlackboardService.MemoryUpdate failMemory = teamBlackboardService.append(
                    workspaceRoot,
                    parentTaskId,
                    run.member().name() + " — failed",
                    run.outcome().errorMessage() != null ? run.outcome().errorMessage() : "unknown error");
            teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, failMemory);
        } else {
            String summary = run.outcome().assistantText() != null ? run.outcome().assistantText().trim() : "";
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.member.completed",
                    TeamRunPayloads.withSummary(payload, summary));
            TeamBlackboardService.MemoryUpdate memory =
                    teamBlackboardService.append(workspaceRoot, parentTaskId, run.member().name(), summary);
            teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, memory);
        }
    }

    private static String parallelMemberDelegationPrompt(
            String userMessage, TeamMemberDefinition member, String initBlackboard) {
        return """
                You are %s on a WorkMate expert team (parallel fan-out). Other members run concurrently.

                Original user request:
                %s

                Team brief at fan-out (peer outputs are not available yet):
                %s

                Provide your independent contribution as concise markdown. Do not wait for peers.
                """
                .formatted(
                        member.name(),
                        userMessage,
                        initBlackboard.isBlank() ? "(blackboard empty)" : initBlackboard.trim());
    }

    private static String memberDelegationPrompt(
            String userMessage, TeamMemberDefinition member, String priorContext) {
        return """
                You are %s on a WorkMate expert team. Focus only on your specialty.

                Original user request:
                %s

                Prior team context (shared blackboard):
                %s

                Provide your contribution as concise markdown. Do not repeat prior members' work.
                """
                .formatted(
                        member.name(),
                        userMessage,
                        priorContext.isBlank() ? "(blackboard empty)" : priorContext.trim());
    }

    private static String synthesisPrompt(String userMessage, String teamContext) {
        return """
                Original user request:
                %s

                Team member reports:
                %s

                %s
                """
                .formatted(userMessage, teamContext.trim(), TeamSynthesisContract.synthesisInstructions());
    }
}
