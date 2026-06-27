package com.huawei.ascend.examples.workmate.audit.chain;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditSegmentRepository extends JpaRepository<AuditSegment, LocalDate> {
    Optional<AuditSegment> findBySegmentDate(LocalDate segmentDate);
}
