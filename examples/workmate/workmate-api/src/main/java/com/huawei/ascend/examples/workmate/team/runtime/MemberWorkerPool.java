package com.huawei.ascend.examples.workmate.team.runtime;

import com.huawei.ascend.examples.workmate.team.backend.MemberBackendRegistry;
import com.huawei.ascend.examples.workmate.team.backend.MemberDescriptor;
import com.huawei.ascend.examples.workmate.team.backend.MemberRunResult;
import com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessage;
import com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessageKind;
import com.huawei.ascend.examples.workmate.team.mailbox.MemberHandbackTurn;
import com.huawei.ascend.examples.workmate.team.mailbox.MemberMessageRouter;
import com.huawei.ascend.examples.workmate.team.mailbox.MemberSendMessageOutcome;
import com.huawei.ascend.examples.workmate.team.mailbox.RemoteHandbackIngestResult;
import com.huawei.ascend.examples.workmate.team.mailbox.TeamMailbox;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concurrent member runtime for a single team run: owns the {@link TeamMailbox}, the
 * {@link MemberMessageRouter}, and one {@link MemberWorker} per member.
 *
 * <p>A single mailbox listener wakes the relevant workers on every delivery, so members run in
 * parallel and idle members auto-reawaken when new mail arrives. The leader (the openjiuwen
 * TeamAgent) is not a worker here; messages addressed to the leader are surfaced to the wiring
 * layer via {@link #leaderId()} + mailbox draining.</p>
 */
public final class MemberWorkerPool {

    private static final Logger LOG = LoggerFactory.getLogger(MemberWorkerPool.class);

    private final String teamRunId;
    private final TeamMailbox mailbox;
    private final MemberMessageRouter router;
    private final MemberBackendRegistry backendRegistry;
    private final Executor executor;
    private final Map<String, MemberWorker> workers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<MemberRunResult>> awaiting = new ConcurrentHashMap<>();
    /** Per-member explicit {@code send_message} handback within the current worker turn. */
    private final Map<String, MemberHandbackTurn> explicitHandbacks = new ConcurrentHashMap<>();
    /** Monotonic ingest sequence for proactive remote handback UI cards (per member). */
    private final Map<String, AtomicInteger> remoteHandbackSequences = new ConcurrentHashMap<>();

    public MemberWorkerPool(
            String teamRunId,
            String leaderId,
            MemberBackendRegistry backendRegistry,
            Executor executor) {
        this.teamRunId = teamRunId;
        this.mailbox = new TeamMailbox(teamRunId);
        this.router = new MemberMessageRouter(leaderId);
        this.backendRegistry = backendRegistry;
        this.executor = executor;
        this.mailbox.registerRecipient(leaderId);
        this.mailbox.addListener(this::onDelivered);
    }

    public String teamRunId() {
        return teamRunId;
    }

    public String leaderId() {
        return router.leaderId();
    }

    public TeamMailbox mailbox() {
        return mailbox;
    }

    public MemberMessageRouter router() {
        return router;
    }

    /** Register a member and create its concurrent worker. */
    public MemberWorker addMember(
            MemberDescriptor member,
            MemberRunContextFactory contextFactory,
            MemberWorkerListener listener) {
        MemberWorkerListener wrapped = wrapForCorrelation(member, listener);
        MemberWorker worker =
                new MemberWorker(member, this, mailbox, backendRegistry, contextFactory, executor, wrapped);
        workers.put(member.memberId(), worker);
        mailbox.registerRecipient(member.memberId());
        return worker;
    }

    /**
     * Deliver a message to a member and block until its turn completes, returning the result.
     *
     * <p>Used by the synchronous leader path (openjiuwen {@code runMember} → {@code invoke}) so the
     * leader still receives the member's output inline, while the member actually executes on its
     * own worker thread through the backend registry. The future always completes because the
     * worker emits a result for both success and failure.</p>
     */
    public MemberRunResult dispatchAndAwait(String fromId, String memberId, String body, Duration timeout) {
        if (worker(memberId) == null) {
            return MemberRunResult.failed("No worker registered for member: " + memberId);
        }
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<MemberRunResult> future = new CompletableFuture<>();
        awaiting.put(correlationId, future);
        MailboxMessage message = MailboxMessage.create(
                teamRunId, fromId, memberId, MailboxMessageKind.MESSAGE, body, null, correlationId);
        mailbox.deliver(message);
        try {
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                return future.get();
            }
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return MemberRunResult.failed("member dispatch interrupted");
        } catch (TimeoutException ex) {
            return MemberRunResult.failed("member run timed out after " + timeout);
        } catch (Exception ex) {
            return MemberRunResult.failed(ex.getMessage() != null ? ex.getMessage() : "member dispatch failed");
        } finally {
            awaiting.remove(correlationId);
        }
    }

    private MemberWorkerListener wrapForCorrelation(MemberDescriptor member, MemberWorkerListener listener) {
        MemberWorkerListener delegate = listener == null ? MemberWorkerListener.NOOP : listener;
        return new MemberWorkerListener() {
            @Override
            public void onStarted(MemberDescriptor m, List<MailboxMessage> inbound) {
                delegate.onStarted(m, inbound);
            }

            @Override
            public void onCompleted(MemberDescriptor m, List<MailboxMessage> inbound, MemberRunResult result) {
                boolean correlated = completeCorrelations(inbound, result);
                if (!correlated) {
                    routeReplyToLeader(m, result, false);
                }
                delegate.onCompleted(m, inbound, result);
            }

            @Override
            public void onFailed(MemberDescriptor m, List<MailboxMessage> inbound, MemberRunResult result) {
                boolean correlated = completeCorrelations(inbound, result);
                if (!correlated) {
                    routeReplyToLeader(m, result, true);
                }
                delegate.onFailed(m, inbound, result);
            }

            @Override
            public void onStateChanged(MemberDescriptor m, MemberRuntimeState state) {
                delegate.onStateChanged(m, state);
            }
        };
    }

    /** Clears any prior explicit-handback marker at the start of a member worker turn. */
    public void beginMemberTurn(String memberId) {
        if (memberId != null && !memberId.isBlank()) {
            explicitHandbacks.remove(memberId);
        }
    }

    /** Clears turn-local explicit-handback state after a member worker turn completes. */
    public void endMemberTurn(String memberId) {
        if (memberId != null && !memberId.isBlank()) {
            explicitHandbacks.remove(memberId);
        }
    }

    /**
     * Record that the member already delivered structured output to the leader via {@code send_message}
     * during this turn (local backend only). Suppresses duplicate implicit assistant wrap.
     */
    public void markExplicitHandback(String memberId, String summary) {
        if (memberId == null || memberId.isBlank()) {
            return;
        }
        explicitHandbacks.put(memberId, new MemberHandbackTurn(summary));
    }

    public boolean hasExplicitHandback(String memberId) {
        return memberId != null && explicitHandbacks.containsKey(memberId);
    }

    public String explicitHandbackSummary(String memberId) {
        MemberHandbackTurn turn = memberId == null ? null : explicitHandbacks.get(memberId);
        return turn == null ? "" : turn.summary();
    }

    /**
     * Route a member's produced output back to the leader's inbox so the bridge can re-awaken the
     * leader with it (fire-and-continue). Only used when the inbound message was not correlated to
     * a synchronous {@link #dispatchAndAwait} waiter.
     */
    private void routeReplyToLeader(MemberDescriptor member, MemberRunResult result, boolean failed) {
        if (!failed && hasExplicitHandback(member.memberId())) {
            return;
        }
        String label = member.memberName() != null && !member.memberName().isBlank()
                ? member.memberName() : member.memberId();
        String body;
        String summary;
        if (failed) {
            String error = result != null && result.errorMessage() != null ? result.errorMessage() : "未知错误";
            body = "成员「" + label + "」执行失败：" + error;
            summary = "member failed: " + label;
        } else {
            String output = result != null ? result.assistantText() : "";
            if (result != null && "a2a".equals(result.backendKind())) {
                body = output;
                summary = "remote handback: " + label;
            } else {
                body = "来自成员「" + label + "」的产出：\n" + output;
                summary = "member output: " + label;
            }
        }
        MailboxMessage reply = MailboxMessage.create(
                teamRunId, member.memberId(), leaderId(), MailboxMessageKind.MESSAGE, body, summary, null);
        mailbox.deliver(reply);
    }

    private boolean completeCorrelations(List<MailboxMessage> inbound, MemberRunResult result) {
        boolean any = false;
        for (MailboxMessage message : inbound) {
            String correlationId = message.correlationId();
            if (correlationId != null) {
                CompletableFuture<MemberRunResult> future = awaiting.remove(correlationId);
                if (future != null) {
                    future.complete(result);
                    any = true;
                }
            }
        }
        return any;
    }

    public MemberWorker worker(String memberId) {
        return workers.get(memberId);
    }

    /**
     * Whether any member is currently doing work or has work queued: a worker is {@code BUSY}, or a
     * member still has unread mail waiting to be picked up. Used by the leader reawaken loop to
     * decide between waiting for replies and terminating the run.
     */
    public boolean hasActiveWork() {
        for (MemberWorker w : workers.values()) {
            if (w.state() == MemberRuntimeState.BUSY) {
                return true;
            }
            if (mailbox.hasUnread(w.member().memberId())) {
                return true;
            }
        }
        return false;
    }

    public List<String> route(String fromId, String toToken, String body) {
        return route(fromId, toToken, body, null);
    }

    /**
     * Member {@code send_message} tool adapter: validate inputs, route, return OpenJiuwen tool result map.
     */
    public Map<String, Object> invokeSendMessageTool(String fromId, Map<String, Object> inputs) {
        String to = readInputField(inputs, "to", "recipient", "target");
        String content = readInputField(inputs, "content", "message", "body");
        String summary = readInputField(inputs, "summary", "description");
        if (to.isBlank()) {
            return toolError("'to' / 'recipient' is required (use team-lead for the team lead)");
        }
        if (content.isBlank()) {
            return toolError("'content' is required");
        }
        return routeSendMessage(fromId, to, content, summary).toToolResult();
    }

    /**
     * Proactive remote handback ingest (A2A webhook / push): deliver to the team lead and mark explicit
     * handback so a later sync A2A completion does not duplicate the leader inbox.
     */
    public RemoteHandbackIngestResult ingestRemoteHandback(String memberId, String content, String summary) {
        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException("memberId is required");
        }
        String body = content == null ? "" : content.strip();
        if (body.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        int sequence = nextRemoteHandbackSequence(memberId);
        MemberSendMessageOutcome outcome = routeSendMessage(memberId, "team-lead", body, summary);
        return new RemoteHandbackIngestResult(outcome, sequence);
    }

    public int nextRemoteHandbackSequence(String memberId) {
        return remoteHandbackSequences
                .computeIfAbsent(memberId, ignored -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * Member {@code send_message} entry (local tool + future adapters): validate routing, deliver into
     * the mailbox, and mark explicit leader handback when {@code to} resolves to the team lead.
     */
    public MemberSendMessageOutcome routeSendMessage(
            String fromId, String toToken, String content, String summary) {
        String resolved = router.resolveRecipient(toToken);
        String body = content == null ? "" : content.strip();
        String cardSummary = summary == null ? "" : summary.strip();
        route(fromId, toToken, body, cardSummary.isBlank() ? null : cardSummary);
        if (leaderId().equals(resolved)) {
            markExplicitHandback(fromId, cardSummary);
        }
        return new MemberSendMessageOutcome(fromId, resolved, cardSummary);
    }

    private static Map<String, Object> toolError(String message) {
        return Map.of("success", false, "error", message);
    }

    private static String readInputField(Map<String, Object> inputs, String... keys) {
        if (inputs == null) {
            return "";
        }
        for (String key : keys) {
            Object value = inputs.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.strip();
            }
        }
        return "";
    }

    /**
     * Route a message (from a member, the leader, or a user {@code @member}) into the mailbox.
     * Delivery wakes the recipient worker(s) automatically.
     *
     * @param fromId    sender id
     * @param toToken   routing token ({@code @member} / {@code @all} / {@code @main} / member id / team-lead)
     * @param body      message content
     * @param summary   optional short summary for UI / leader inbox
     * @return resolved recipient ids the message was delivered to
     */
    public List<String> route(String fromId, String toToken, String body, String summary) {
        String recipient = router.resolveRecipient(toToken);
        MailboxMessage message = MailboxMessage.create(
                teamRunId, fromId, recipient, com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessageKind.MESSAGE,
                body, summary, null);
        return mailbox.deliver(message);
    }

    private void onDelivered(MailboxMessage message, List<String> recipients) {
        for (String recipient : recipients) {
            MemberWorker worker = workers.get(recipient);
            if (worker != null) {
                worker.signal();
            }
        }
    }

    /** Stop all workers (in-flight turns finish, no new turns start). */
    public void shutdown() {
        LOG.info("Shutting down member worker pool for team run {}", teamRunId);
        workers.values().forEach(MemberWorker::stop);
    }
}
