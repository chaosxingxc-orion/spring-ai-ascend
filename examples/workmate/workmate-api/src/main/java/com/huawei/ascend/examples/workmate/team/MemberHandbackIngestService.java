package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.team.agent.MemberHandbackToolEmitter;
import com.huawei.ascend.examples.workmate.team.mailbox.RemoteHandbackIngestResult;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool;
import com.huawei.ascend.examples.workmate.team.runtime.TeamRunRegistration;
import com.huawei.ascend.examples.workmate.team.runtime.TeamRuntimeManager;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

/**
 * Ingests proactive remote member handbacks into the team mailbox and persists member-scoped UI events.
 */
@Service
public class MemberHandbackIngestService {

    public record Request(String memberId, String content, String summary, String to) {}

    public record Result(
            String memberId,
            String resolvedRecipient,
            String toolCallId,
            int sequence,
            String summary) {}

    private final TeamRuntimeManager teamRuntimeManager;
    private final AgentRunExecutor agentRunExecutor;
    private final com.huawei.ascend.examples.workmate.chat.SessionPersistenceService sessionPersistenceService;

    public MemberHandbackIngestService(
            TeamRuntimeManager teamRuntimeManager,
            AgentRunExecutor agentRunExecutor,
            com.huawei.ascend.examples.workmate.chat.SessionPersistenceService sessionPersistenceService) {
        this.teamRuntimeManager = teamRuntimeManager;
        this.agentRunExecutor = agentRunExecutor;
        this.sessionPersistenceService = sessionPersistenceService;
    }

    public Optional<Result> ingest(UUID sessionId, Request request) {
        if (sessionId == null || request == null) {
            return Optional.empty();
        }
        MemberWorkerPool pool = teamRuntimeManager.findBySession(sessionId.toString());
        TeamRunRegistration registration = teamRuntimeManager.registrationForSession(sessionId.toString());
        if (pool == null || registration == null) {
            return Optional.empty();
        }
        String memberId = request.memberId() == null ? "" : request.memberId().strip();
        if (memberId.isBlank()) {
            throw new IllegalArgumentException("memberId is required");
        }
        String to = request.to() == null || request.to().isBlank() ? "team-lead" : request.to().strip();
        if (!isLeaderTarget(to)) {
            throw new IllegalArgumentException("remote handback ingest only supports to=team-lead (got: " + to + ")");
        }
        RemoteHandbackIngestResult ingested =
                pool.ingestRemoteHandback(memberId, request.content(), request.summary());
        TeamMemberDefinition member = resolveMember(registration, memberId);
        String toolCallId = MemberHandbackToolEmitter.ingestHandbackToolCallId(
                registration.parentRunId(), memberId, ingested.sequence());
        MemberHandbackToolEmitter.emitRemoteHandback(
                agentRunExecutor,
                sessionPersistenceService,
                null,
                new AtomicBoolean(false),
                sessionId,
                registration.parentRunId(),
                member,
                request.content(),
                ingested.outcome().summary(),
                toolCallId,
                "push");
        return Optional.of(new Result(
                memberId,
                ingested.outcome().resolvedRecipient(),
                toolCallId,
                ingested.sequence(),
                ingested.outcome().summary()));
    }

    private static boolean isLeaderTarget(String to) {
        String normalized = to.toLowerCase();
        return normalized.equals("team-lead")
                || normalized.equals("lead")
                || normalized.equals("leader")
                || normalized.equals("main")
                || normalized.equals("__lead__");
    }

    private static TeamMemberDefinition resolveMember(TeamRunRegistration registration, String memberId) {
        TeamMemberDefinition known = registration.findMember(memberId);
        if (known != null) {
            return known;
        }
        return new TeamMemberDefinition(memberId, memberId, memberId + "__remote", "member", 0, "🧑");
    }
}
