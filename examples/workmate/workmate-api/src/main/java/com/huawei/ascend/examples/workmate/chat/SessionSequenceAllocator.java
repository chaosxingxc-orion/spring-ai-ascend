package com.huawei.ascend.examples.workmate.chat;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Per-session high-water mark shared by persisted messages and run-events so timeline seqs stay
 * globally monotonic.
 */
@Component
class SessionSequenceAllocator {

    private final SessionMessageRepository messageRepository;
    private final RunEventRepository eventRepository;

    SessionSequenceAllocator(SessionMessageRepository messageRepository, RunEventRepository eventRepository) {
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
    }

    int nextSeq(UUID sessionId) {
        return Math.max(messageRepository.findMaxSeq(sessionId), eventRepository.findMaxSeq(sessionId)) + 1;
    }
}
