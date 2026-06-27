package com.huawei.ascend.examples.workmate.session;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkmateSessionRepository extends JpaRepository<WorkmateSession, UUID> {

    List<WorkmateSession> findAllByOrderByUpdatedAtDesc();

    long countByArchivedAtIsNull();

    List<WorkmateSession> findByArchivedAtIsNullAndPinnedFalseAndStatusNotOrderByUpdatedAtAsc(
            SessionStatus status, org.springframework.data.domain.Pageable pageable);

    List<WorkmateSession> findByArchivedAtIsNullAndPinnedFalseOrderByUpdatedAtAsc(
            org.springframework.data.domain.Pageable pageable);

    List<WorkmateSession> findByArchivedAtIsNotNull();

    long countByArchivedAtIsNotNull();
}
