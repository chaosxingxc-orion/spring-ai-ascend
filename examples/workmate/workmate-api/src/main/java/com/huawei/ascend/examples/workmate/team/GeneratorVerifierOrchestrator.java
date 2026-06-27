package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteRequest;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.team.agent.TeamEventSseAdapter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Generator-Verifier 拓扑（ADR-013）：无 leader，生成 ↔ 校验循环直至通过或达到 maxIterations。
 */
@Service
public class GeneratorVerifierOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratorVerifierOrchestrator.class);
    private static final int DEFAULT_MAX_ITERATIONS = 3;
    private static final Pattern VERIFIED_PATTERN =
            Pattern.compile("VERIFIED:\\s*(yes|no)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEEDBACK_PATTERN =
            Pattern.compile("FEEDBACK:\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int BLACKBOARD_PROMPT_MAX = 6000;

    private final ExpertRegistry expertRegistry;
    private final SessionPersistenceService sessionPersistenceService;
    private final AgentRunExecutor agentRunExecutor;
    private final TeamBlackboardService teamBlackboardService;
    private final TeamEventEmitter teamEventEmitter;
    private final TeamAnswerPublisher teamAnswerPublisher;

    public GeneratorVerifierOrchestrator(
            ExpertRegistry expertRegistry,
            SessionPersistenceService sessionPersistenceService,
            AgentRunExecutor agentRunExecutor,
            TeamBlackboardService teamBlackboardService,
            TeamEventEmitter teamEventEmitter,
            TeamAnswerPublisher teamAnswerPublisher) {
        this.expertRegistry = expertRegistry;
        this.sessionPersistenceService = sessionPersistenceService;
        this.agentRunExecutor = agentRunExecutor;
        this.teamBlackboardService = teamBlackboardService;
        this.teamEventEmitter = teamEventEmitter;
        this.teamAnswerPublisher = teamAnswerPublisher;
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
        TeamMemberDefinition generator = resolveGenerator(members);
        TeamMemberDefinition verifier = resolveVerifier(members, generator);
        int maxIterations = resolveMaxIterations(team);
        String acceptanceCriteria =
                team.coordination() != null ? team.coordination().acceptanceCriteria() : null;

        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.started",
                teamStartedPayload(team, parentTaskId, members, maxIterations));

        Path workspaceRoot = Path.of(session.getWorkspaceRoot());
        TeamBlackboardService.MemoryUpdate init =
                teamBlackboardService.initialize(workspaceRoot, parentTaskId, team.id(), message);
        teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, init);

        String draft = "";
        String feedback = "";
        boolean accepted = false;
        boolean anyMemberFailed = false;
        boolean timeBudgetExceeded = false;
        TeamTerminationGuard terminationGuard =
                new TeamTerminationGuard(team.coordination() != null ? team.coordination().termination() : null);

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            if (terminationGuard.expired()) {
                timeBudgetExceeded = true;
                break;
            }
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.iteration.started",
                    Map.of(
                            "teamId", team.id(),
                            "parentRunId", parentTaskId,
                            "iteration", iteration,
                            "maxIterations", maxIterations));

            String genSubRunId = parentTaskId + ":" + generator.id() + ":i" + iteration;
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.member.started",
                    TeamRunPayloads.memberPayload(parentTaskId, genSubRunId, generator));

            RunPersistenceContext genContext = sessionPersistenceService.beginSubRun(
                    session.getId(), genSubRunId, parentTaskId, generator.id(), generator.name());
            String genPrompt = generatorPrompt(
                    message,
                    draft,
                    feedback,
                    iteration,
                    acceptanceCriteria,
                    teamBlackboardService.readForPrompt(workspaceRoot, parentTaskId, BLACKBOARD_PROMPT_MAX));
            ExecuteOutcome genOutcome = agentRunExecutor.execute(new ExecuteRequest(
                    session,
                    genPrompt,
                    genSubRunId,
                    generator.expertId(),
                    genContext,
                    emitter,
                    clientConnected,
                    false,
                    false,
                    false));

            if (genOutcome.failed()) {
                anyMemberFailed = true;
                teamEventEmitter.emit(
                        emitter,
                        clientConnected,
                        parentContext,
                        "team.member.failed",
                        TeamRunPayloads.withError(
                                TeamRunPayloads.memberPayload(parentTaskId, genSubRunId, generator),
                                genOutcome.errorMessage()));
                break;
            }

            draft = GeneratorAcceptanceChecker.dedupeRepeatedBody(
                    genOutcome.assistantText() != null ? genOutcome.assistantText().trim() : "");
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.member.completed",
                    TeamRunPayloads.withSummary(
                            TeamRunPayloads.memberPayload(parentTaskId, genSubRunId, generator), draft));
            TeamBlackboardService.MemoryUpdate genMemory = teamBlackboardService.append(
                    workspaceRoot, parentTaskId, "Generator iteration " + iteration, draft);
            teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, genMemory);

            String verifySubRunId = parentTaskId + ":" + verifier.id() + ":i" + iteration;
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.verify.started",
                    verifyPayload(parentTaskId, verifySubRunId, verifier, iteration, maxIterations));

            GeneratorAcceptanceChecker.Result programmatic =
                    GeneratorAcceptanceChecker.check(message, draft, acceptanceCriteria);
            if (!programmatic.passed()) {
                feedback = programmatic.feedback();
                teamEventEmitter.emit(
                        emitter,
                        clientConnected,
                        parentContext,
                        "team.verify.rejected",
                        TeamRunPayloads.withProgrammaticFeedback(
                                verifyPayload(parentTaskId, verifySubRunId, verifier, iteration, maxIterations),
                                feedback));
                TeamBlackboardService.MemoryUpdate gateMemory = teamBlackboardService.append(
                        workspaceRoot, parentTaskId, "Programmatic gate iteration " + iteration, feedback);
                teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, gateMemory);
                continue;
            }

            RunPersistenceContext verifyContext = sessionPersistenceService.beginSubRun(
                    session.getId(), verifySubRunId, parentTaskId, verifier.id(), verifier.name());
            String verifyPrompt = verifierPrompt(
                    message,
                    draft,
                    acceptanceCriteria,
                    teamBlackboardService.readForPrompt(workspaceRoot, parentTaskId, BLACKBOARD_PROMPT_MAX));
            ExecuteOutcome verifyOutcome = agentRunExecutor.execute(new ExecuteRequest(
                    session,
                    verifyPrompt,
                    verifySubRunId,
                    verifier.expertId(),
                    verifyContext,
                    emitter,
                    clientConnected,
                    false,
                    false,
                    false));

            if (verifyOutcome.failed()) {
                anyMemberFailed = true;
                teamEventEmitter.emit(
                        emitter,
                        clientConnected,
                        parentContext,
                        "team.verify.rejected",
                        TeamRunPayloads.withFeedback(
                                verifyPayload(parentTaskId, verifySubRunId, verifier, iteration, maxIterations),
                                verifyOutcome.errorMessage() != null
                                        ? verifyOutcome.errorMessage()
                                        : "verifier run failed"));
                break;
            }

            VerifyResult result = parseVerifyResult(verifyOutcome.assistantText());
            if (result.accepted) {
                accepted = true;
                teamEventEmitter.emit(
                        emitter,
                        clientConnected,
                        parentContext,
                        "team.verify.accepted",
                        TeamRunPayloads.withSummary(
                                verifyPayload(parentTaskId, verifySubRunId, verifier, iteration, maxIterations),
                                verifyOutcome.assistantText()));
                break;
            }

            feedback = result.feedback != null && !result.feedback.isBlank()
                    ? result.feedback.trim()
                    : TeamRunPayloads.truncate(verifyOutcome.assistantText(), TeamRunPayloads.SUMMARY_MAX);
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.verify.rejected",
                    TeamRunPayloads.withFeedback(
                            verifyPayload(parentTaskId, verifySubRunId, verifier, iteration, maxIterations),
                            feedback));
            TeamBlackboardService.MemoryUpdate rejectMemory = teamBlackboardService.append(
                    workspaceRoot, parentTaskId, "Verifier iteration " + iteration, feedback);
            teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, rejectMemory);
        }

        if (!draft.isBlank() && accepted) {
            teamAnswerPublisher.publish(emitter, clientConnected, parentContext, draft);
        } else if (!draft.isBlank() && !accepted && !anyMemberFailed) {
            teamAnswerPublisher.publish(
                    emitter,
                    clientConnected,
                    parentContext,
                    draft + "\n\n---\n*未通过全部校验，以上为最后一版草稿。*");
        } else if (anyMemberFailed) {
            LOG.warn(
                    "Generator-verifier team produced no draft teamId={} sessionId={}",
                    team.id(),
                    session.getId());
        }

        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.completed",
                Map.of(
                        "teamId", team.id(),
                        "parentRunId", parentTaskId,
                        "memberCount", members.size(),
                        "anyMemberFailed", anyMemberFailed,
                        "accepted", accepted,
                        "timeBudgetExceeded", timeBudgetExceeded,
                        "pattern", CoordinationSpec.GENERATOR_VERIFIER));
    }

    private static TeamMemberDefinition resolveGenerator(List<TeamMemberDefinition> members) {
        return byParticipantRole(members, "generator").orElse(members.get(0));
    }

    private static TeamMemberDefinition resolveVerifier(
            List<TeamMemberDefinition> members, TeamMemberDefinition generator) {
        Optional<TeamMemberDefinition> explicit = byParticipantRole(members, "verifier");
        if (explicit.isPresent() && !explicit.get().id().equals(generator.id())) {
            return explicit.get();
        }
        return members.stream()
                .filter(m -> !m.id().equals(generator.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("generator-verifier team requires 2 distinct members"));
    }

    private static Optional<TeamMemberDefinition> byParticipantRole(
            List<TeamMemberDefinition> members, String role) {
        return members.stream()
                .filter(m -> m.participantRole() != null && role.equalsIgnoreCase(m.participantRole()))
                .findFirst();
    }

    private static int resolveMaxIterations(ExpertDefinition team) {
        if (team.coordination() != null
                && team.coordination().termination() != null
                && team.coordination().termination().maxIterations() != null
                && team.coordination().termination().maxIterations() > 0) {
            return team.coordination().termination().maxIterations();
        }
        return DEFAULT_MAX_ITERATIONS;
    }

    private static String generatorPrompt(
            String userMessage,
            String draft,
            String feedback,
            int iteration,
            String acceptanceCriteria,
            String blackboard) {
        String criteriaHint =
                iteration <= 1 && acceptanceCriteria != null && !acceptanceCriteria.isBlank()
                        ? "\n\nAcceptance criteria (your draft must satisfy these):\n" + acceptanceCriteria.trim()
                        : "";
        String blackboardBlock =
                "\n\nShared team blackboard:\n" + (blackboard.isBlank() ? "(empty)" : blackboard.trim());
        if (iteration <= 1 || draft.isBlank()) {
            return """
                    You are the generator on a WorkMate expert team (generator-verifier pattern).

                    Original user request:
                    %s%s%s

                    Output ONLY the draft body in markdown (no file writes, no tool calls).
                    Produce a complete draft addressing the request.
                    """
                    .formatted(userMessage, criteriaHint, blackboardBlock);
        }
        return """
                You are the generator on a WorkMate expert team (generator-verifier pattern).

                Original user request:
                %s%s

                Your previous draft was rejected by the verifier. Revise it using the feedback below.
                Match the requested Chinese character count precisely.

                Previous draft:
                %s

                Verifier feedback:
                %s

                Produce an improved draft in markdown.
                """
                .formatted(
                        userMessage,
                        blackboardBlock,
                        draft,
                        feedback.isBlank() ? "(no specific feedback)" : feedback);
    }

    private static String verifierPrompt(
            String userMessage, String draft, String acceptanceCriteria, String blackboard) {
        String criteriaBlock =
                acceptanceCriteria != null && !acceptanceCriteria.isBlank()
                        ? "\n\nAcceptance criteria (ALL must pass — default to VERIFIED: no if any fail):\n"
                                + acceptanceCriteria.trim()
                        : "";
        String blackboardBlock =
                "\n\nShared team blackboard:\n" + (blackboard.isBlank() ? "(empty)" : blackboard.trim());
        return """
                You are the verifier on a WorkMate expert team (generator-verifier pattern).

                Original user request:
                %s

                Draft to review:
                %s%s%s

                Check completeness, accuracy, and alignment with the user request and acceptance criteria.
                Be strict on the first iteration: reject if word count or required phrases are missing.

                Respond in this exact format:

                VERIFIED: yes
                or
                VERIFIED: no
                FEEDBACK: <specific, actionable improvements>

                Do not include other sections before VERIFIED.
                """
                .formatted(userMessage, draft, criteriaBlock, blackboardBlock);
    }

    private static VerifyResult parseVerifyResult(String raw) {
        if (raw == null || raw.isBlank()) {
            return new VerifyResult(false, "empty verifier response");
        }
        Matcher verified = VERIFIED_PATTERN.matcher(raw);
        if (!verified.find()) {
            return new VerifyResult(false, TeamRunPayloads.truncate(raw, TeamRunPayloads.SUMMARY_MAX));
        }
        boolean accepted = "yes".equalsIgnoreCase(verified.group(1));
        String feedback = null;
        Matcher fb = FEEDBACK_PATTERN.matcher(raw);
        if (fb.find()) {
            feedback = fb.group(1).trim();
        }
        return new VerifyResult(accepted, feedback);
    }

    private static Map<String, Object> teamStartedPayload(
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members,
            int maxIterations) {
        Map<String, Object> payload = TeamEventSseAdapter.teamStartedPayload(team, parentRunId, members);
        payload.put("maxIterations", maxIterations);
        return payload;
    }

    private static Map<String, Object> verifyPayload(
            String parentRunId,
            String subRunId,
            TeamMemberDefinition verifier,
            int iteration,
            int maxIterations) {
        Map<String, Object> payload = TeamRunPayloads.memberPayload(parentRunId, subRunId, verifier);
        payload.put("iteration", iteration);
        payload.put("maxIterations", maxIterations);
        return payload;
    }

    private record VerifyResult(boolean accepted, String feedback) {}
}
