package com.huawei.ascend.examples.workmate.usage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionUsageRepository extends JpaRepository<SessionUsageRecord, UUID> {

    List<SessionUsageRecord> findBySessionId(UUID sessionId);

    List<SessionUsageRecord> findBySessionIdAndRunId(UUID sessionId, String runId);

    @Query(
            "SELECT COALESCE(SUM(r.promptTokens + r.completionTokens), 0L) FROM SessionUsageRecord r WHERE r.createdAt >= :since")
    long sumTotalTokensSince(@Param("since") Instant since);
}
