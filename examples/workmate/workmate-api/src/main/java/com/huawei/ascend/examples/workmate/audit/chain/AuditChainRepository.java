package com.huawei.ascend.examples.workmate.audit.chain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditChainRepository extends JpaRepository<AuditChainEntry, Long> {

    Optional<AuditChainEntry> findTopByOrderBySeqGlobalDesc();

    List<AuditChainEntry> findBySeqGlobalGreaterThanOrderBySeqGlobalAsc(long afterSeq, Pageable pageable);

    List<AuditChainEntry> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderBySeqGlobalAsc(
            Instant from, Instant toExclusive);

    @Query("""
            SELECT e FROM AuditChainEntry e
            WHERE (:category IS NULL OR e.category = :category)
              AND (:decision IS NULL OR e.decision = :decision)
              AND e.createdAt >= :fromInclusive
              AND e.createdAt < :toExclusive
              AND e.seqGlobal > :afterSeq
            ORDER BY e.seqGlobal ASC
            """)
    List<AuditChainEntry> search(
            @Param("category") String category,
            @Param("decision") String decision,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            @Param("afterSeq") long afterSeq,
            Pageable pageable);

    @Query("""
            SELECT e FROM AuditChainEntry e
            WHERE (:category IS NULL OR e.category = :category)
              AND (:decision IS NULL OR e.decision = :decision)
              AND e.createdAt >= :fromInclusive
              AND e.createdAt < :toExclusive
              AND (LOWER(e.eventName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(e.runId) LIKE LOWER(CONCAT('%', :q, '%')))
              AND e.seqGlobal > :afterSeq
            ORDER BY e.seqGlobal ASC
            """)
    List<AuditChainEntry> searchWithQuery(
            @Param("category") String category,
            @Param("decision") String decision,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            @Param("q") String q,
            @Param("afterSeq") long afterSeq,
            Pageable pageable);

    default List<AuditChainEntry> findBySegmentDate(LocalDate date) {
        Instant start = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        return findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderBySeqGlobalAsc(start, end);
    }
}
