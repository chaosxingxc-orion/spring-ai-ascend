package com.huawei.ascend.examples.workmate.chat;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RunEventRepository extends JpaRepository<RunEvent, UUID> {

    List<RunEvent> findBySessionIdOrderBySeqAsc(UUID sessionId);

    List<RunEvent> findBySessionIdAndSeqGreaterThanOrderBySeqAsc(UUID sessionId, int afterSeq);

    List<RunEvent> findBySessionIdAndEventNameOrderBySeqAsc(UUID sessionId, String eventName);

    @Query("SELECT COALESCE(MAX(e.seq), 0) FROM RunEvent e WHERE e.sessionId = :sessionId")
    int findMaxSeq(@Param("sessionId") UUID sessionId);

    boolean existsBySessionIdAndEventName(UUID sessionId, String eventName);

    @Query("""
            SELECT e FROM RunEvent e
            WHERE NOT EXISTS (
                SELECT 1 FROM AuditChainEntry c WHERE c.runEventId = e.id
            )
            ORDER BY e.createdAt ASC, e.id ASC
            """)
    List<RunEvent> findUnprojected(Pageable pageable);
}
