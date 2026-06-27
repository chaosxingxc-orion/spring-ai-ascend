package com.huawei.ascend.examples.workmate.automation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationJobRepository extends JpaRepository<AutomationJob, UUID> {

    List<AutomationJob> findAllByOrderByCreatedAtDesc();

    @Query("""
            select j from AutomationJob j
            where j.enabled = true and j.nextRunAt is not null and j.nextRunAt <= :now
            """)
    List<AutomationJob> findDueJobs(@Param("now") Instant now);
}
