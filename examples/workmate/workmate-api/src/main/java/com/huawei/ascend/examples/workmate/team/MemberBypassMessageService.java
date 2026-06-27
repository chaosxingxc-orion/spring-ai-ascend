package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.team.agent.TeamEventSseAdapter;
import com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessage;
import com.huawei.ascend.examples.workmate.team.mailbox.TeamMailboxAddressing;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool;
import com.huawei.ascend.examples.workmate.team.runtime.TeamRunRegistration;
import com.huawei.ascend.examples.workmate.team.runtime.TeamRuntimeManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Phase 3 — user {@code @member} bypass: route into the team mailbox and persist {@code
 * team.member.message} run_events for SSE / refresh replay.
 */
@Service
public class MemberBypassMessageService {

    public record Request(String target, String message, String summary) {}

    public record Result(String target, List<String> delivered, boolean broadcast) {}

    private final TeamRuntimeManager teamRuntimeManager;
    private final AgentRunExecutor agentRunExecutor;
    private final SessionPersistenceService sessionPersistenceService;

    public MemberBypassMessageService(
            TeamRuntimeManager teamRuntimeManager,
            AgentRunExecutor agentRunExecutor,
            SessionPersistenceService sessionPersistenceService) {
        this.teamRuntimeManager = teamRuntimeManager;
        this.agentRunExecutor = agentRunExecutor;
        this.sessionPersistenceService = sessionPersistenceService;
    }

    public Optional<Result> send(UUID sessionId, Request request) {
        if (sessionId == null || request == null) {
            return Optional.empty();
        }
        String message = request.message() == null ? "" : request.message().strip();
        if (message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        MemberWorkerPool pool = teamRuntimeManager.findBySession(sessionId.toString());
        TeamRunRegistration registration = teamRuntimeManager.registrationForSession(sessionId.toString());
        if (pool == null || registration == null) {
            return Optional.empty();
        }
        String target = normalizeTarget(request.target());
        String summary = request.summary() == null ? "" : request.summary().strip();
        List<String> delivered = pool.route(
                TeamMailboxAddressing.USER_SENDER_ID,
                target,
                message,
                summary.isBlank() ? null : summary);
        boolean broadcast = delivered.size() > 1
                || MailboxMessage.BROADCAST.equals(pool.router().resolveRecipient(target));
        emitBypassEvent(sessionId, registration, target, message, summary, delivered, broadcast);
        return Optional.of(new Result(target, delivered, broadcast));
    }

    private void emitBypassEvent(
            UUID sessionId,
            TeamRunRegistration registration,
            String target,
            String message,
            String summary,
            List<String> delivered,
            boolean broadcast) {
        Map<String, Object> payload = TeamEventSseAdapter.memberMessagePayload(
                registration.parentRunId(),
                TeamMailboxAddressing.USER_SENDER_ID,
                "用户",
                target,
                delivered,
                message,
                summary,
                broadcast);
        RunPersistenceContext parentContext =
                RunPersistenceContext.forAudit(sessionId, registration.parentRunId());
        agentRunExecutor.emit(
                (SseEmitter) null,
                new AtomicBoolean(false),
                parentContext,
                "team.member.message",
                payload);
    }

    private static String normalizeTarget(String target) {
        if (target == null || target.isBlank()) {
            return "@main";
        }
        return target.strip();
    }
}
