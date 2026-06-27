package com.huawei.ascend.examples.workmate.session;

import com.huawei.ascend.examples.workmate.mention.dto.MentionItem;
import com.huawei.ascend.examples.workmate.prompt.dto.UserAttachmentItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Component;

/** F6 — per-session FIFO prompt queue while a run is active. */
@Component
public class SessionRunQueue {

    public static final int MAX_QUEUE_SIZE = 5;

    public record QueuedPrompt(
            String message,
            List<MentionItem> mentions,
            List<UserAttachmentItem> attachments,
            Instant enqueuedAt) {}

    private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<QueuedPrompt>> queues = new ConcurrentHashMap<>();

    public int enqueue(
            UUID sessionId,
            String message,
            List<MentionItem> mentions,
            List<UserAttachmentItem> attachments) {
        ConcurrentLinkedQueue<QueuedPrompt> queue =
                queues.computeIfAbsent(sessionId, ignored -> new ConcurrentLinkedQueue<>());
        if (queue.size() >= MAX_QUEUE_SIZE) {
            throw new SessionQueueFullException(sessionId, MAX_QUEUE_SIZE);
        }
        queue.add(new QueuedPrompt(
                message,
                mentions == null ? List.of() : List.copyOf(mentions),
                attachments == null ? List.of() : List.copyOf(attachments),
                Instant.now()));
        return queue.size();
    }

    public Optional<QueuedPrompt> poll(UUID sessionId) {
        ConcurrentLinkedQueue<QueuedPrompt> queue = queues.get(sessionId);
        if (queue == null) {
            return Optional.empty();
        }
        QueuedPrompt next = queue.poll();
        if (queue.isEmpty()) {
            queues.remove(sessionId, queue);
        }
        return Optional.ofNullable(next);
    }

    public int depth(UUID sessionId) {
        ConcurrentLinkedQueue<QueuedPrompt> queue = queues.get(sessionId);
        return queue == null ? 0 : queue.size();
    }

    /** W45 — discard all queued prompts for a session. */
    public int clear(UUID sessionId) {
        ConcurrentLinkedQueue<QueuedPrompt> queue = queues.get(sessionId);
        if (queue == null) {
            return 0;
        }
        int cleared = queue.size();
        queue.clear();
        queues.remove(sessionId, queue);
        return cleared;
    }
}
