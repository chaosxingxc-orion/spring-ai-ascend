package com.huawei.ascend.examples.workmate.team.runtime;

import com.huawei.ascend.examples.workmate.team.backend.MemberBackendRegistry;
import com.huawei.ascend.examples.workmate.team.backend.MemberDescriptor;
import com.huawei.ascend.examples.workmate.team.backend.MemberRunContext;
import com.huawei.ascend.examples.workmate.team.backend.MemberRunResult;
import com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessage;
import com.huawei.ascend.examples.workmate.team.mailbox.TeamMailbox;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A long-lived, concurrent runtime for a single team member.
 *
 * <p>Each worker owns one member and drives its run loop independently of the others, so multiple
 * members execute in parallel even when the underlying {@code MemberBackend} runs a turn
 * synchronously. This is the WorkMate application-layer equivalent of the openjiuwen
 * {@code asyncio.Task}-per-member model, realized with an {@link Executor} (virtual threads).</p>
 *
 * <p><b>Auto-reawaken:</b> a worker that has finished a turn ({@code PAUSED}) is woken again
 * whenever a new message lands in its mailbox, mirroring the reference workbench's auto-restart safety net.</p>
 *
 * <p><b>Single-flight:</b> at most one turn runs at a time per member; messages that arrive while
 * a turn is in flight are picked up by the same loop without a lost-wakeup race.</p>
 */
public final class MemberWorker {

    private static final Logger LOG = LoggerFactory.getLogger(MemberWorker.class);

    private final MemberDescriptor member;
    private final MemberWorkerPool pool;
    private final TeamMailbox mailbox;
    private final MemberBackendRegistry backendRegistry;
    private final MemberRunContextFactory contextFactory;
    private final Executor executor;
    private final MemberWorkerListener listener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean pending = new AtomicBoolean(false);
    private volatile MemberRuntimeState state = MemberRuntimeState.READY;
    private volatile boolean stopped = false;

    public MemberWorker(
            MemberDescriptor member,
            MemberWorkerPool pool,
            TeamMailbox mailbox,
            MemberBackendRegistry backendRegistry,
            MemberRunContextFactory contextFactory,
            Executor executor,
            MemberWorkerListener listener) {
        this.member = member;
        this.pool = pool;
        this.mailbox = mailbox;
        this.backendRegistry = backendRegistry;
        this.contextFactory = contextFactory;
        this.executor = executor;
        this.listener = listener == null ? MemberWorkerListener.NOOP : listener;
    }

    public MemberDescriptor member() {
        return member;
    }

    public MemberRuntimeState state() {
        return state;
    }

    /** Signal that new work may be available; (re)starts the run loop if idle. */
    public void signal() {
        if (stopped) {
            return;
        }
        pending.set(true);
        tryStart();
    }

    /** Stop the worker; an in-flight turn finishes but no new turns will start. */
    public void stop() {
        stopped = true;
        transition(MemberRuntimeState.STOPPED);
    }

    private void tryStart() {
        if (stopped) {
            return;
        }
        if (running.compareAndSet(false, true)) {
            executor.execute(this::runLoop);
        }
    }

    private void runLoop() {
        boolean processedTurn = false;
        try {
            while (!stopped && pending.getAndSet(false)) {
                List<MailboxMessage> batch = mailbox.drainUnread(member.memberId());
                if (batch.isEmpty()) {
                    if (mailbox.hasUnread(member.memberId())) {
                        pending.set(true);
                        continue;
                    }
                    break;
                }
                processedTurn = true;
                processBatch(batch);
            }
        } finally {
            running.set(false);
            if (!stopped) {
                if (pending.get() || mailbox.hasUnread(member.memberId())) {
                    pending.set(true);
                    tryStart();
                } else if (processedTurn) {
                    transition(MemberRuntimeState.PAUSED);
                }
            }
        }
    }

    private void processBatch(List<MailboxMessage> batch) {
        transition(MemberRuntimeState.BUSY);
        MemberRunResult result;
        try {
            if (pool != null) {
                pool.beginMemberTurn(member.memberId());
            }
            listener.onStarted(member, batch);
            String combined = batch.stream()
                    .map(MailboxMessage::body)
                    .collect(Collectors.joining("\n\n"));
            try {
                MemberRunContext context = contextFactory.create(member, combined, batch);
                result = backendRegistry.run(context);
            } catch (RuntimeException ex) {
                LOG.warn("Member worker {} failed", member.memberId(), ex);
                result = MemberRunResult.failed(
                        ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            }
        } catch (RuntimeException ex) {
            LOG.warn("Member worker {} failed during turn setup", member.memberId(), ex);
            result = MemberRunResult.failed(
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
        try {
            if (result.failed()) {
                listener.onFailed(member, batch, result);
            } else {
                listener.onCompleted(member, batch, result);
            }
        } finally {
            if (pool != null) {
                pool.endMemberTurn(member.memberId());
            }
        }
    }

    private void transition(MemberRuntimeState next) {
        if (state == next) {
            return;
        }
        state = next;
        listener.onStateChanged(member, next);
    }
}
