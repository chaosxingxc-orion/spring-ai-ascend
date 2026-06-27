package com.huawei.ascend.examples.workmate.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.config.WorkmateSessionProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SessionAutoArchiveServiceTest {

    @Mock
    private WorkmateSessionRepository repository;

    private SessionAutoArchiveService service;

    @BeforeEach
    void setUp() {
        service = new SessionAutoArchiveService(repository, new WorkmateSessionProperties(50, true, true));
    }

    @Test
    void slotsNeededReturnsZeroWhenBelowCap() {
        assertEquals(0, SessionAutoArchiveService.slotsNeeded(49, 50));
    }

    @Test
    void slotsNeededReturnsOneWhenAtCap() {
        assertEquals(1, SessionAutoArchiveService.slotsNeeded(50, 50));
    }

    @Test
    void archiveOldestMarksSessionsArchived() {
        WorkmateSession oldest = session("oldest");
        when(repository.findByArchivedAtIsNullAndPinnedFalseAndStatusNotOrderByUpdatedAtAsc(
                        eq(SessionStatus.RUNNING), any(Pageable.class)))
                .thenReturn(List.of(oldest));
        when(repository.save(oldest)).thenAnswer(invocation -> invocation.getArgument(0));

        List<WorkmateSession> archived = service.archiveOldest(1);

        assertEquals(1, archived.size());
        verify(repository).save(oldest);
        assertEquals(false, oldest.isPinned());
        org.junit.jupiter.api.Assertions.assertNotNull(oldest.getArchivedAt());
    }

    @Test
    void archiveOldestFailsWhenNotEnoughCandidates() {
        when(repository.findByArchivedAtIsNullAndPinnedFalseAndStatusNotOrderByUpdatedAtAsc(
                        eq(SessionStatus.RUNNING), any(Pageable.class)))
                .thenReturn(List.of());
        when(repository.countByArchivedAtIsNull()).thenReturn(57L);

        assertThrows(InsufficientArchivableSessionsException.class, () -> service.archiveOldest(8));
    }

    private static WorkmateSession session(String title) {
        return new WorkmateSession(UUID.randomUUID(), title, "/tmp", SessionStatus.CREATED);
    }
}
