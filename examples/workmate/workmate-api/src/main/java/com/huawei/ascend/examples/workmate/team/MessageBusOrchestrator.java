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
import com.huawei.ascend.examples.workmate.spi.topic.PublishingTopicBusSpi;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusMemberContext;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusMessage;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusScope;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusSpi;
import com.huawei.ascend.examples.workmate.spi.topic.WorkmateTopicBusProvider;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Message-bus 拓扑（ADR-013 W26）：topic subscribe + 异步投递；多轮 wave + 收敛（W26.1）。
 */
@Service
public class MessageBusOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(MessageBusOrchestrator.class);
    private static final String WAVE_TOPIC_PREFIX = "wave.";
    private static final String ORCHESTRATOR_ID = "orchestrator";
    private static final String NO_NEW_MARKER = "NO_NEW_FINDINGS";

    private final ExpertRegistry expertRegistry;
    private final SessionPersistenceService sessionPersistenceService;
    private final MemberRunRouter memberRunRouter;
    private final TeamBlackboardService teamBlackboardService;
    private final WorkmateTopicBusProvider topicBusProvider;
    private final TeamEventEmitter teamEventEmitter;
    private final TeamAnswerPublisher teamAnswerPublisher;
    private final TeamMemberPayloadFactory memberPayloadFactory;

    public MessageBusOrchestrator(
            ExpertRegistry expertRegistry,
            SessionPersistenceService sessionPersistenceService,
            MemberRunRouter memberRunRouter,
            TeamBlackboardService teamBlackboardService,
            WorkmateTopicBusProvider topicBusProvider,
            TeamEventEmitter teamEventEmitter,
            TeamAnswerPublisher teamAnswerPublisher,
            TeamMemberPayloadFactory memberPayloadFactory) {
        this.expertRegistry = expertRegistry;
        this.sessionPersistenceService = sessionPersistenceService;
        this.memberRunRouter = memberRunRouter;
        this.teamBlackboardService = teamBlackboardService;
        this.topicBusProvider = topicBusProvider;
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
            throw new IllegalStateException("Message-bus team " + team.id() + " requires at least 2 members");
        }

        Termination termination =
                team.coordination() != null ? team.coordination().termination() : null;
        int maxWaves = resolveMaxWaves(termination);
        int convergenceTarget = ConvergenceSpec.noNewFindingsThreshold(
                termination != null ? termination.convergence() : null);

        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.started",
                teamStartedPayload(team, parentTaskId, members, maxWaves, convergenceTarget));

        Path workspaceRoot = Path.of(session.getWorkspaceRoot());
        TeamBlackboardService.MemoryUpdate init =
                teamBlackboardService.initialize(workspaceRoot, parentTaskId, team.id(), message);
        teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, init);

        TeamTerminationGuard guard = new TeamTerminationGuard(termination);

        boolean anyMemberFailed = false;
        boolean timeBudgetExceeded = false;
        boolean converged = false;
        int convergenceStreak = 0;
        int wavesCompleted = 0;

        TopicBusSpi rawBus = topicBusProvider.open(new TopicBusScope(parentTaskId, team.id()));
        PublishingTopicBusSpi bus = new PublishingTopicBusSpi(
                rawBus,
                event -> emitBusPublished(
                        emitter,
                        clientConnected,
                        parentContext,
                        parentTaskId,
                        rawBus,
                        event.topic(),
                        event.authorId(),
                        event.authorName(),
                        TeamRunPayloads.truncate(event.body(), TeamRunPayloads.SUMMARY_MAX),
                        event.publishSource()));
        try {
            for (int wave = 1; wave <= maxWaves; wave++) {
                if (guard.expired()) {
                    timeBudgetExceeded = true;
                    break;
                }
                if (convergenceTarget > 0 && convergenceStreak >= convergenceTarget) {
                    converged = true;
                    break;
                }

                wavesCompleted = wave;
                teamEventEmitter.emit(
                        emitter,
                        clientConnected,
                        parentContext,
                        "team.iteration.started",
                        TeamRunPayloads.iterationPayload(team.id(), parentTaskId, wave, maxWaves));

                Map<String, CompletableFuture<MemberRunOutcome>> memberFutures = registerSubscribersForWave(
                        wave,
                        session,
                        emitter,
                        clientConnected,
                        parentTaskId,
                        parentContext,
                        workspaceRoot,
                        members,
                        bus);

                if (wave == 1) {
                    bus.publishWithSource("orchestrator", TopicBusSpi.INGRESS_TOPIC, "user", "用户", message);
                } else {
                    String waveTopic = waveTopic(wave);
                    String waveBody = "Wave " + wave + ": review the message bus and add only NEW findings. "
                            + "If nothing new, reply with exactly: " + NO_NEW_MARKER;
                    bus.publishWithSource(
                            "orchestrator", waveTopic, ORCHESTRATOR_ID, "编排器", waveBody);
                }

                WaveCollectResult waveResult =
                        collectWaveOutcomes(memberFutures, guard, members, workspaceRoot, parentTaskId, emitter, clientConnected, parentContext, bus);
                anyMemberFailed = anyMemberFailed || waveResult.anyMemberFailed();
                if (waveResult.timeBudgetExceeded()) {
                    timeBudgetExceeded = true;
                    break;
                }

                if (waveResult.hadNewMemberFindings()) {
                    convergenceStreak = 0;
                } else {
                    convergenceStreak++;
                }
            }

            if (!timeBudgetExceeded && !converged && convergenceTarget > 0 && convergenceStreak >= convergenceTarget) {
                converged = true;
            }

            if (!timeBudgetExceeded) {
                String finalText = bus.summaryMarkdown();
                if (!finalText.isBlank()) {
                    String suffix = converged
                            ? "\n\n---\n*消息总线已收敛（连续 " + convergenceStreak + " 轮无新发现）。*"
                            : "";
                    teamAnswerPublisher.publish(emitter, clientConnected, parentContext, finalText + suffix);
                }
            } else {
                String partial = bus.summaryMarkdown();
                if (!partial.isBlank()) {
                    teamAnswerPublisher.publish(
                            emitter,
                            clientConnected,
                            parentContext,
                            partial + "\n\n---\n*团队时间预算已用尽，以上为总线已发布条目。*");
                }
            }

            Map<String, Object> completed = new LinkedHashMap<>();
            completed.put("teamId", team.id());
            completed.put("parentRunId", parentTaskId);
            completed.put("memberCount", members.size());
            completed.put("anyMemberFailed", anyMemberFailed);
            completed.put("timeBudgetExceeded", timeBudgetExceeded);
            completed.put("pattern", CoordinationSpec.MESSAGE_BUS);
            completed.put("busEntryCount", bus.entries().size());
            completed.put("busMode", maxWaves > 1 ? "async-subscribe-multiwave" : "async-subscribe");
            completed.put("topicBusProvider", topicBusProvider.providerId());
            completed.put("converged", converged);
            completed.put("convergenceStreak", convergenceStreak);
            completed.put("iterationsCompleted", wavesCompleted);
            teamEventEmitter.emit(emitter, clientConnected, parentContext, "team.completed", completed);
        } finally {
            bus.close();
        }
    }

    private record WaveCollectResult(boolean anyMemberFailed, boolean timeBudgetExceeded, boolean hadNewMemberFindings) {}

    private WaveCollectResult collectWaveOutcomes(
            Map<String, CompletableFuture<MemberRunOutcome>> memberFutures,
            TeamTerminationGuard guard,
            List<TeamMemberDefinition> members,
            Path workspaceRoot,
            String parentTaskId,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            TopicBusSpi bus) {
        boolean anyMemberFailed = false;
        boolean timeBudgetExceeded = false;
        boolean hadNewMemberFindings = false;

        for (TeamMemberDefinition member : members) {
            if (guard.expired()) {
                timeBudgetExceeded = true;
                memberFutures.values().forEach(f -> f.cancel(true));
                break;
            }
            CompletableFuture<MemberRunOutcome> future = memberFutures.get(member.id());
            if (future == null) {
                continue;
            }
            try {
                Long remaining = guard.remainingMs();
                MemberRunOutcome outcome =
                        remaining != null ? future.get(remaining, TimeUnit.MILLISECONDS) : future.get();
                if (outcome.outcome().failed()) {
                    anyMemberFailed = true;
                    continue;
                }
                String body = outcome.outcome().assistantText() != null
                        ? outcome.outcome().assistantText().trim()
                        : "";
                if (body.isBlank() || isNoNewMarker(body)) {
                    continue;
                }
                if (busContainsDuplicate(workspaceRoot, parentTaskId, body)) {
                    continue;
                }
                hadNewMemberFindings = true;
                String topic = outcome.member().id();
                bus.publishWithSource(
                        "outcome",
                        topic,
                        outcome.member().id(),
                        outcome.member().name(),
                        body);
                TeamBlackboardService.MemoryUpdate memory = teamBlackboardService.append(
                        workspaceRoot, parentTaskId, outcome.member().name(), body);
                teamEventEmitter.emitMemory(emitter, clientConnected, parentContext, parentTaskId, memory);
            } catch (TimeoutException ex) {
                timeBudgetExceeded = true;
                future.cancel(true);
                memberFutures.values().forEach(f -> f.cancel(true));
                break;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                timeBudgetExceeded = true;
                break;
            } catch (ExecutionException ex) {
                anyMemberFailed = true;
                LOG.warn("Message-bus member {} failed: {}", member.id(), ex.getCause() != null
                        ? ex.getCause().getMessage()
                        : ex.getMessage());
            }
        }
        return new WaveCollectResult(anyMemberFailed, timeBudgetExceeded, hadNewMemberFindings);
    }

    private boolean busContainsDuplicate(Path workspaceRoot, String parentTaskId, String body) {
        return teamBlackboardService.containsContent(workspaceRoot, parentTaskId, body);
    }

    private static boolean isNoNewMarker(String body) {
        String normalized = body.trim();
        return normalized.equalsIgnoreCase(NO_NEW_MARKER)
                || normalized.startsWith(NO_NEW_MARKER);
    }

    private Map<String, CompletableFuture<MemberRunOutcome>> registerSubscribersForWave(
            int wave,
            WorkmateSession session,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            RunPersistenceContext parentContext,
            Path workspaceRoot,
            List<TeamMemberDefinition> members,
            TopicBusSpi bus) {
        Map<String, CompletableFuture<MemberRunOutcome>> futures = new LinkedHashMap<>();
        Set<String> triggerTopics = wave == 1 ? Set.of(TopicBusSpi.INGRESS_TOPIC) : Set.of(waveTopic(wave));
        List<String> topicList = new ArrayList<>(triggerTopics);

        for (TeamMemberDefinition member : members) {
            CompletableFuture<MemberRunOutcome> future = new CompletableFuture<>();
            futures.put(member.id(), future);
            bus.subscribe(member.id(), triggerTopics, triggerEntry -> {
                if (future.isDone()) {
                    return;
                }
                try {
                    MemberRunOutcome outcome = runMemberOnBus(
                            session,
                            emitter,
                            clientConnected,
                            parentTaskId,
                            parentContext,
                            workspaceRoot,
                            member,
                            bus,
                            triggerEntry,
                            wave);
                    future.complete(outcome);
                } catch (Exception ex) {
                    future.completeExceptionally(ex);
                }
            });
            teamEventEmitter.emit(
                    emitter,
                    clientConnected,
                    parentContext,
                    "team.bus.subscribed",
                    bus.subscribedPayload(parentTaskId, member.id(), topicList));
        }
        return futures;
    }

    private record MemberRunOutcome(TeamMemberDefinition member, ExecuteOutcome outcome) {}

    private MemberRunOutcome runMemberOnBus(
            WorkmateSession session,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            String parentTaskId,
            RunPersistenceContext parentContext,
            Path workspaceRoot,
            TeamMemberDefinition member,
            TopicBusSpi bus,
            TopicBusMessage triggerEntry,
            int wave) {
        String subRunId = parentTaskId + ":" + member.id() + ":w" + wave;
        teamEventEmitter.emit(
                emitter,
                clientConnected,
                parentContext,
                "team.member.started",
                memberPayloadFactory.started(parentTaskId, subRunId, member));

        RunPersistenceContext subContext =
                sessionPersistenceService.beginSubRun(session.getId(), subRunId, parentTaskId, member.id(), member.name());
        Set<String> topics = Set.of(TopicBusSpi.INGRESS_TOPIC, TopicBusSpi.ALL_TOPICS);
        String prompt = busMemberPrompt(member, bus.snapshotForTopics(topics), triggerEntry, wave);
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
                        false,
                        new TopicBusMemberContext(
                                bus, member.id(), member.name(), Set.of(triggerEntry.topic()))),
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

    private void emitBusPublished(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String parentTaskId,
            TopicBusSpi bus,
            String topic,
            String authorId,
            String authorName,
            String body,
            String publishSource) {
        Map<String, Object> payload =
                bus.publishedPayload(parentTaskId, topic, authorId, authorName, TeamRunPayloads.truncate(body, TeamRunPayloads.SUMMARY_MAX));
        if (publishSource != null && !publishSource.isBlank()) {
            payload.put("publishSource", publishSource);
        }
        teamEventEmitter.emit(emitter, clientConnected, parentContext, "team.bus.published", payload);
    }

    private static String waveTopic(int wave) {
        return WAVE_TOPIC_PREFIX + wave;
    }

    private static int resolveMaxWaves(Termination termination) {
        if (termination != null
                && termination.maxIterations() != null
                && termination.maxIterations() > 0) {
            return termination.maxIterations();
        }
        return 1;
    }

    private static String busMemberPrompt(
            TeamMemberDefinition member, String busSnapshot, TopicBusMessage trigger, int wave) {
        String triggerLine = trigger != null
                ? "Triggered by bus message on topic `" + trigger.topic() + "` from "
                        + (trigger.authorName() != null ? trigger.authorName() : trigger.authorId())
                : "Triggered by ingress";
        return """
                You are %s on a WorkMate message-bus team (no leader). You subscribed to topics and react asynchronously when messages arrive.

                Wave: %d

                %s

                Message bus snapshot (subscribed topics):
                %s

                Publish your contribution as concise markdown to topic `%s`, or call workmate_team_bus_publish during the run.
                Do not duplicate prior bus entries. If you have nothing new to add, reply with exactly: %s
                """
                .formatted(
                        member.name(),
                        wave,
                        triggerLine,
                        busSnapshot.isBlank() ? "(empty)" : busSnapshot.trim(),
                        member.id(),
                        NO_NEW_MARKER);
    }

    private Map<String, Object> teamStartedPayload(
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members,
            int maxWaves,
            int convergenceTarget) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teamId", team.id());
        payload.put("parentRunId", parentRunId);
        payload.put("memberCount", members.size());
        payload.put("collaboration", team.collaboration() != null ? team.collaboration() : "parallel");
        payload.put("pattern", CoordinationSpec.MESSAGE_BUS);
        payload.put("maxIterations", maxWaves);
        payload.put("convergenceTarget", convergenceTarget);
        payload.put("busMode", maxWaves > 1 ? "async-subscribe-multiwave" : "async-subscribe");
        payload.put("topicBusProvider", topicBusProvider.providerId());
        payload.put("members", TeamRunPayloads.memberRoster(members));
        return payload;
    }
}
