package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteRequest;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.CoordinationSpec.Termination;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

/**
 * Shared-state 拓扑（ADR-013 W27）：成员并行读写共享黑板；终止条件一等公民（迭代/收敛/时间预算）。
 */
@Service
public class SharedStateOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(SharedStateOrchestrator.class);
    private static final int DEFAULT_MAX_ITERATIONS = 4;

    private final ExpertRegistry expertRegistry;
    private final SessionPersistenceService sessionPersistenceService;
    private final MemberRunRouter memberRunRouter;
    private final TeamBlackboardService teamBlackboardService;
    private final TeamEventEmitter teamEventEmitter;
    private final TeamAnswerPublisher teamAnswerPublisher;
    private final TeamMemberPayloadFactory memberPayloadFactory;

    public SharedStateOrchestrator(
            ExpertRegistry expertRegistry,
            SessionPersistenceService sessionPersistenceService,
            MemberRunRouter memberRunRouter,
            TeamBlackboardService teamBlackboardService,
            TeamEventEmitter teamEventEmitter,
            TeamAnswerPublisher teamAnswerPublisher,
            TeamMemberPayloadFactory memberPayloadFactory) {
        this.expertRegistry = expertRegistry;
        this.sessionPersistenceService = sessionPersistenceService;
        this.memberRunRouter = memberRunRouter;
        this.teamBlackboardService = teamBlackboardService;
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
        List<TeamMemberDefinition> members = team.members();
        if (members.size() < 2) {
            throw new IllegalStateException("Shared-state team " + team.id() + " requires at least 2 members");
        }

        Termination termination =
                team.coordination() != null ? team.coordination().termination() : null;
        int maxIterations = resolveMaxIterations(termination);
        int convergenceTarget = ConvergenceSpec.noNewFindingsThreshold(
                termination != null ? termination.convergence() : null);
        TeamTerminationGuard timeGuard = new TeamTerminationGuard(termination);

        teamEventEmitter.emit(emitter, clientConnected, parentContext, "team.started", teamStartedPayload(team, parentTaskId, members, maxIterations, convergenceTarget));

        Path workspaceRoot = Path.of(session.getWorkspaceRoot());
        TeamBlackboardService.MemoryUpdate init =
                teamBlackboardService.initialize(workspaceRoot, parentTaskId, team.id(), message);
        teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, init);

        boolean anyMemberFailed = false;
        boolean timeBudgetExceeded = false;
        boolean converged = false;
        int iteration = 0;
        int convergenceStreak = 0;

        while (iteration < maxIterations && !timeGuard.expired()) {
            iteration++;
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.iteration.started",
                    TeamRunPayloads.iterationPayload(team.id(), parentTaskId, iteration, maxIterations));
            emitStateProgress(
                    emitter,
                    clientConnected,
                    parentContext,
                    parentTaskId,
                    iteration,
                    maxIterations,
                    convergenceStreak,
                    convergenceTarget,
                    init.version(),
                    false);

            RoundResult round = runRound(
                    session,
                    emitter,
                    clientConnected,
                    parentTaskId,
                    parentContext,
                    workspaceRoot,
                    members,
                    iteration,
                    timeGuard);
            anyMemberFailed = anyMemberFailed || round.anyMemberFailed();
            timeBudgetExceeded = round.timeBudgetExceeded();
            if (timeBudgetExceeded) {
                break;
            }

            long version = teamBlackboardService.currentVersion(workspaceRoot, parentTaskId);
            if (round.hadNewFindings()) {
                convergenceStreak = 0;
            } else {
                convergenceStreak++;
            }

            emitStateProgress(
                    emitter,
                    clientConnected,
                    parentContext,
                    parentTaskId,
                    iteration,
                    maxIterations,
                    convergenceStreak,
                    convergenceTarget,
                    version,
                    round.hadNewFindings());

            if (convergenceTarget > 0 && convergenceStreak >= convergenceTarget) {
                converged = true;
                LOG.info("Shared-state team {} converged after {} idle round(s)", team.id(), convergenceStreak);
                break;
            }
        }

        if (timeGuard.expired() && !converged) {
            timeBudgetExceeded = true;
        }

        Optional<String> deciderId =
                termination != null && termination.decider() != null && !termination.decider().isBlank()
                        ? Optional.of(termination.decider().trim())
                        : Optional.empty();
        if (deciderId.isPresent() && !timeBudgetExceeded) {
            TeamMemberDefinition decider =
                    members.stream().filter(m -> m.id().equals(deciderId.get())).findFirst().orElse(null);
            if (decider != null) {
                DeciderResult deciderResult = runDecider(
                        session, emitter, clientConnected, parentTaskId, parentContext, workspaceRoot, decider);
                anyMemberFailed = anyMemberFailed || deciderResult.failed();
            }
        }

        String finalText = teamBlackboardService.read(workspaceRoot, parentTaskId);
        if (!finalText.isBlank()) {
            if (timeBudgetExceeded) {
                teamAnswerPublisher.publish(
                        emitter,
                        clientConnected,
                        parentContext,
                        finalText + "\n\n---\n*团队时间预算已用尽，以上为黑板当前内容。*");
            } else if (converged) {
                teamAnswerPublisher.publish(
                        emitter,
                        clientConnected,
                        parentContext,
                        finalText + "\n\n---\n*协作已收敛（连续 " + convergenceStreak + " 轮无新发现）。*");
            } else {
                teamAnswerPublisher.publish(emitter, clientConnected, parentContext, finalText);
            }
        }

        long finalVersion = teamBlackboardService.currentVersion(workspaceRoot, parentTaskId);
        Map<String, Object> completed = new LinkedHashMap<>();
        completed.put("teamId", team.id());
        completed.put("parentRunId", parentTaskId);
        completed.put("memberCount", members.size());
        completed.put("anyMemberFailed", anyMemberFailed);
        completed.put("timeBudgetExceeded", timeBudgetExceeded);
        completed.put("pattern", CoordinationSpec.SHARED_STATE);
        completed.put("converged", converged);
        completed.put("convergenceStreak", convergenceStreak);
        completed.put("iterationsCompleted", iteration);
        completed.put("blackboardVersion", finalVersion);
        teamEventEmitter.emit(emitter, clientConnected, parentContext, "team.completed", completed);
    }

    private record RoundResult(boolean anyMemberFailed, boolean timeBudgetExceeded, boolean hadNewFindings) {}

    private record DeciderResult(boolean failed) {}

    private RoundResult runRound(
            WorkmateSession session,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            RunPersistenceContext parentContext,
            Path workspaceRoot,
            List<TeamMemberDefinition> members,
            int iteration,
            TeamTerminationGuard timeGuard) {
        boolean anyMemberFailed = false;
        boolean timeBudgetExceeded = false;
        boolean hadNewFindings = false;

        String blackboardSnapshot = teamBlackboardService.readForPrompt(workspaceRoot, parentTaskId, 12000);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<MemberRunOutcome>> tasks = new ArrayList<>();
            for (TeamMemberDefinition member : members) {
                tasks.add(() -> runMemberRound(
                        session,
                        emitter,
                        clientConnected,
                        parentTaskId,
                        parentContext,
                        member,
                        iteration,
                        blackboardSnapshot));
            }
            List<Future<MemberRunOutcome>> futures = new ArrayList<>();
            for (Callable<MemberRunOutcome> task : tasks) {
                futures.add(pool.submit(task));
            }
            for (Future<MemberRunOutcome> future : futures) {
                if (timeGuard.expired()) {
                    timeBudgetExceeded = true;
                    futures.forEach(f -> f.cancel(true));
                    break;
                }
                try {
                    Long remaining = timeGuard.remainingMs();
                    MemberRunOutcome outcome =
                            remaining != null ? future.get(remaining, TimeUnit.MILLISECONDS) : future.get();
                    if (outcome.outcome().failed()) {
                        anyMemberFailed = true;
                    }
                    String body = outcome.outcome().assistantText() != null
                            ? outcome.outcome().assistantText().trim()
                            : "";
                    if (!body.isBlank()
                            && !teamBlackboardService.containsContent(workspaceRoot, parentTaskId, body)) {
                        TeamBlackboardService.MemoryUpdate memory = teamBlackboardService.appendLocked(
                                workspaceRoot, parentTaskId, outcome.member().name(), body);
                        teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, memory);
                        hadNewFindings = true;
                    }
                } catch (TimeoutException ex) {
                    timeBudgetExceeded = true;
                    future.cancel(true);
                    futures.forEach(f -> f.cancel(true));
                    break;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    timeBudgetExceeded = true;
                    break;
                } catch (ExecutionException ex) {
                    anyMemberFailed = true;
                }
            }
        }
        return new RoundResult(anyMemberFailed, timeBudgetExceeded, hadNewFindings);
    }

    private DeciderResult runDecider(
            WorkmateSession session,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            RunPersistenceContext parentContext,
            Path workspaceRoot,
            TeamMemberDefinition decider) {
        String subRunId = parentTaskId + ":" + decider.id() + ":decider";
        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.member.started",
                memberPayloadFactory.started(parentTaskId, subRunId, decider));

        RunPersistenceContext subContext =
                sessionPersistenceService.beginSubRun(session.getId(), subRunId, parentTaskId, decider.id(), decider.name());
        String blackboard = teamBlackboardService.readForPrompt(workspaceRoot, parentTaskId, 12000);
        String prompt = deciderPrompt(decider, blackboard);
        ExecuteOutcome outcome = memberRunRouter.executeMember(
                new ExecuteRequest(
                        session,
                        prompt,
                        subRunId,
                        decider.expertId(),
                        subContext,
                        emitter,
                        clientConnected,
                        false,
                        false,
                        false),
                decider.expertId());

        if (outcome.failed()) {
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.member.failed",
                    TeamRunPayloads.withError(TeamRunPayloads.memberPayload(parentTaskId, subRunId, decider), outcome.errorMessage()));
            return new DeciderResult(true);
        }
        String summary = outcome.assistantText() != null ? outcome.assistantText().trim() : "";
        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.member.completed",
                TeamRunPayloads.withSummary(TeamRunPayloads.memberPayload(parentTaskId, subRunId, decider), summary));
        if (!summary.isBlank()
                && !teamBlackboardService.containsContent(workspaceRoot, parentTaskId, summary)) {
            TeamBlackboardService.MemoryUpdate memory =
                    teamBlackboardService.appendLocked(workspaceRoot, parentTaskId, decider.name() + " (裁决)", summary);
            teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, memory);
        }
        return new DeciderResult(false);
    }

    private record MemberRunOutcome(TeamMemberDefinition member, ExecuteOutcome outcome) {}

    private MemberRunOutcome runMemberRound(
            WorkmateSession session,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            RunPersistenceContext parentContext,
            TeamMemberDefinition member,
            int iteration,
            String blackboardSnapshot) {
        String subRunId = parentTaskId + ":" + member.id() + ":r" + iteration;
        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.member.started",
                memberPayloadFactory.started(parentTaskId, subRunId, member));

        RunPersistenceContext subContext =
                sessionPersistenceService.beginSubRun(session.getId(), subRunId, parentTaskId, member.id(), member.name());
        String prompt = peerMemberPrompt(member, blackboardSnapshot, iteration);
        ExecuteOutcome outcome = memberRunRouter.executeMember(
                new ExecuteRequest(
                        session,
                        prompt,
                        subRunId,
                        member.expertId(),
                        subContext,
                        emitter,
                        clientConnected,
                        false,
                        false,
                        false),
                member.expertId());

        if (outcome.failed()) {
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.member.failed",
                    TeamRunPayloads.withError(TeamRunPayloads.memberPayload(parentTaskId, subRunId, member), outcome.errorMessage()));
        } else {
            String summary = outcome.assistantText() != null ? outcome.assistantText().trim() : "";
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.member.completed",
                    TeamRunPayloads.withSummary(TeamRunPayloads.memberPayload(parentTaskId, subRunId, member), summary));
        }
        return new MemberRunOutcome(member, outcome);
    }

    private static String peerMemberPrompt(TeamMemberDefinition member, String blackboard, int iteration) {
        return """
                You are %s on a WorkMate shared-state team (no leader). Peers collaborate via a shared blackboard.

                Round: %d

                Shared blackboard (read-only snapshot):
                %s

                Add only NEW findings or refinements not already on the blackboard. If nothing new, reply with a single line: NO_NEW_FINDINGS
                """
                .formatted(
                        member.name(),
                        iteration,
                        blackboard.isBlank() ? "(empty)" : blackboard.trim());
    }

    private static String deciderPrompt(TeamMemberDefinition decider, String blackboard) {
        return """
                You are %s — designated decider on a WorkMate shared-state team.

                Shared blackboard:
                %s

                Produce a concise final synthesis for the user based on the blackboard. Do not repeat the entire board.
                """
                .formatted(decider.name(), blackboard.isBlank() ? "(empty)" : blackboard.trim());
    }

    private void emitStateProgress(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String parentRunId,
            int iteration,
            int maxIterations,
            int convergenceStreak,
            int convergenceTarget,
            long blackboardVersion,
            boolean roundHadNewFindings) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parentRunId", parentRunId);
        payload.put("iteration", iteration);
        payload.put("maxIterations", maxIterations);
        payload.put("convergenceStreak", convergenceStreak);
        payload.put("convergenceTarget", convergenceTarget);
        payload.put("blackboardVersion", blackboardVersion);
        payload.put("roundHadNewFindings", roundHadNewFindings);
        teamEventEmitter.emit(emitter, clientConnected, parentContext, "team.state.progress", payload);
    }

    private static int resolveMaxIterations(Termination termination) {
        if (termination != null
                && termination.maxIterations() != null
                && termination.maxIterations() > 0) {
            return termination.maxIterations();
        }
        return DEFAULT_MAX_ITERATIONS;
    }

    private Map<String, Object> teamStartedPayload(
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members,
            int maxIterations,
            int convergenceTarget) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teamId", team.id());
        payload.put("parentRunId", parentRunId);
        payload.put("memberCount", members.size());
        payload.put("collaboration", team.collaboration() != null ? team.collaboration() : "parallel");
        payload.put("pattern", CoordinationSpec.SHARED_STATE);
        payload.put("maxIterations", maxIterations);
        payload.put("convergenceTarget", convergenceTarget);
        payload.put("members", TeamRunPayloads.memberRoster(members));
        return payload;
    }
}
