package com.huawei.ascend.examples.workmate.session;

import com.huawei.ascend.examples.workmate.config.WorkmateSessionProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F6 — LRU auto-archive: when the active session cap is reached, archive the least-recently-updated
 * unpinned sessions. RUNNING sessions are protected by default.
 */
@Service
public class SessionAutoArchiveService {

    private final WorkmateSessionRepository repository;
    private final WorkmateSessionProperties sessionProperties;

    public SessionAutoArchiveService(
            WorkmateSessionRepository repository, WorkmateSessionProperties sessionProperties) {
        this.repository = repository;
        this.sessionProperties = sessionProperties;
    }

    /** Slots to free before creating one new active session. */
    public static int slotsNeeded(long activeCount, int maxActive) {
        if (activeCount < maxActive) {
            return 0;
        }
        return (int) (activeCount - maxActive + 1);
    }

    @Transactional(readOnly = true)
    public int countArchivableCandidates() {
        return findCandidates(Integer.MAX_VALUE).size();
    }

    @Transactional
    public List<WorkmateSession> archiveOldest(int count) {
        if (count <= 0) {
            return List.of();
        }
        List<WorkmateSession> candidates = findCandidates(count);
        if (candidates.size() < count) {
            throw new InsufficientArchivableSessionsException(
                    count, candidates.size(), (int) repository.countByArchivedAtIsNull(), sessionProperties.maxActive());
        }
        Instant now = Instant.now();
        List<WorkmateSession> archived = new ArrayList<>(candidates.size());
        for (WorkmateSession session : candidates) {
            session.setArchivedAt(now);
            session.setPinned(false);
            archived.add(repository.save(session));
        }
        return archived;
    }

    @Transactional
    public List<WorkmateSession> archiveToTargetActiveCount(int targetActiveCount) {
        long active = repository.countByArchivedAtIsNull();
        int needed = (int) Math.max(0, active - targetActiveCount);
        return archiveOldest(needed);
    }

    private List<WorkmateSession> findCandidates(int limit) {
        if (sessionProperties.protectRunning()) {
            return repository.findByArchivedAtIsNullAndPinnedFalseAndStatusNotOrderByUpdatedAtAsc(
                    SessionStatus.RUNNING, PageRequest.of(0, limit));
        }
        return repository.findByArchivedAtIsNullAndPinnedFalseOrderByUpdatedAtAsc(PageRequest.of(0, limit));
    }
}
