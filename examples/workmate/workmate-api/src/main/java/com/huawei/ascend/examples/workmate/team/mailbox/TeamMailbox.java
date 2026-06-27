package com.huawei.ascend.examples.workmate.team.mailbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * In-memory, thread-safe mailbox for a single team run.
 *
 * <p>Each registered recipient (member id or leader id) owns an unread queue. A
 * point-to-point message is enqueued to one recipient; a broadcast fans out to every
 * registered recipient except the sender. This mirrors the reference workbench's
 * {@code inboxes/<member>.json} and openjiuwen {@code message_manager}, but lives in the
 * WorkMate application layer so it stays framework-agnostic across heterogeneous member
 * backends (local / a2a / external).</p>
 *
 * <p>Delivery here is storage + fan-out only. Waking the recipient runtime is the
 * responsibility of the coordinator / member workers (decoupled, mirrors the openjiuwen
 * "coordinator only wakes, agents act" design).</p>
 */
public final class TeamMailbox {

    private final String teamRunId;
    private final Set<String> recipients = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<MailboxMessage>> inboxes =
            new ConcurrentHashMap<>();
    private final List<MailboxListener> listeners = new ArrayList<>();

    public TeamMailbox(String teamRunId) {
        if (teamRunId == null || teamRunId.isBlank()) {
            throw new IllegalArgumentException("teamRunId is required");
        }
        this.teamRunId = teamRunId;
    }

    public String teamRunId() {
        return teamRunId;
    }

    /** Register a recipient (member id or leader id) so it can receive direct + broadcast messages. */
    public void registerRecipient(String recipientId) {
        if (recipientId != null && !recipientId.isBlank()) {
            recipients.add(recipientId);
            inboxes.computeIfAbsent(recipientId, key -> new ConcurrentLinkedQueue<>());
        }
    }

    public Set<String> recipients() {
        return Set.copyOf(recipients);
    }

    /**
     * Deliver a message: point-to-point enqueues to a single recipient, broadcast fans out to
     * all registered recipients except the sender.
     *
     * @return the list of recipient ids the message was actually enqueued to
     */
    public List<String> deliver(MailboxMessage message) {
        if (message == null) {
            return List.of();
        }
        List<String> delivered = new ArrayList<>();
        if (message.isBroadcast()) {
            for (String recipient : recipients) {
                if (!recipient.equals(message.from())) {
                    enqueue(recipient, message);
                    delivered.add(recipient);
                }
            }
        } else {
            // Auto-register unknown recipients so out-of-band @member targets still land.
            registerRecipient(message.to());
            enqueue(message.to(), message);
            delivered.add(message.to());
        }
        notifyListeners(message, delivered);
        return delivered;
    }

    private void enqueue(String recipient, MailboxMessage message) {
        inboxes.computeIfAbsent(recipient, key -> new ConcurrentLinkedQueue<>()).add(message);
    }

    /** Drain and remove all unread messages for a recipient (FIFO). */
    public List<MailboxMessage> drainUnread(String recipientId) {
        ConcurrentLinkedQueue<MailboxMessage> queue = inboxes.get(recipientId);
        if (queue == null) {
            return List.of();
        }
        List<MailboxMessage> drained = new ArrayList<>();
        MailboxMessage msg;
        while ((msg = queue.poll()) != null) {
            drained.add(msg);
        }
        return drained;
    }

    /** Number of unread messages waiting for a recipient. */
    public int unreadCount(String recipientId) {
        ConcurrentLinkedQueue<MailboxMessage> queue = inboxes.get(recipientId);
        return queue == null ? 0 : queue.size();
    }

    public boolean hasUnread(String recipientId) {
        return unreadCount(recipientId) > 0;
    }

    public void addListener(MailboxListener listener) {
        if (listener != null) {
            synchronized (listeners) {
                listeners.add(listener);
            }
        }
    }

    private void notifyListeners(MailboxMessage message, List<String> delivered) {
        List<MailboxListener> snapshot;
        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            snapshot = List.copyOf(listeners);
        }
        for (MailboxListener listener : snapshot) {
            listener.onDelivered(message, delivered);
        }
    }

    /** Callback fired after a message is delivered (used by the coordinator to wake recipients). */
    @FunctionalInterface
    public interface MailboxListener {
        void onDelivered(MailboxMessage message, List<String> recipients);
    }
}
