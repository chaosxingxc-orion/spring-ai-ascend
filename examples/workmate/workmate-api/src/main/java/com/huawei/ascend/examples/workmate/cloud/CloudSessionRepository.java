package com.huawei.ascend.examples.workmate.cloud;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CloudSessionRepository extends JpaRepository<CloudSession, UUID> {

    List<CloudSession> findAllByOrderByCreatedAtDesc();

    Optional<CloudSession> findFirstByLinkedSessionIdAndStatusInOrderByUpdatedAtDesc(
            UUID linkedSessionId, Collection<CloudSessionStatus> statuses);

    Optional<CloudSession> findFirstByLinkedSessionIdAndStatusNotOrderByUpdatedAtDesc(
            UUID linkedSessionId, CloudSessionStatus status);
}
