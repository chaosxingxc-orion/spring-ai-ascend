package com.huawei.ascend.examples.workmate.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionMessageRepository extends JpaRepository<SessionMessage, String> {

    List<SessionMessage> findBySessionIdOrderBySeqAsc(UUID sessionId);

    List<SessionMessage> findBySessionIdAndSupersededFalseOrderBySeqAsc(UUID sessionId);

    Optional<SessionMessage> findBySessionIdAndSeqAndSupersededFalse(UUID sessionId, int seq);

    @Query("SELECT COALESCE(MAX(m.seq), 0) FROM SessionMessage m WHERE m.sessionId = :sessionId")
    int findMaxSeq(@Param("sessionId") UUID sessionId);

    Optional<SessionMessage> findByIdAndSessionId(String id, UUID sessionId);

    @Modifying
    @Query("""
            UPDATE SessionMessage m
            SET m.superseded = true
            WHERE m.sessionId = :sessionId AND m.seq >= :fromSeq AND m.superseded = false
            """)
    int markSupersededFrom(@Param("sessionId") UUID sessionId, @Param("fromSeq") int fromSeq);
}
