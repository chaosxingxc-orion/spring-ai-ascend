package com.huawei.ascend.examples.workmate.audit.chain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditChainStateRepository extends JpaRepository<AuditChainState, Short> {
    Optional<AuditChainState> findById(short id);
}
