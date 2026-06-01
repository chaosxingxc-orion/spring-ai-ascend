package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.temp.L3QueuePlaceholders.Queue;

import java.util.Optional;

public interface EgressQueueRegistry {
    Queue getOrCreate(EgressBinding binding);

    Optional<Queue> find(String tenantId, String sessionId, String taskId);

    Optional<EgressBinding> findBinding(String tenantId, String sessionId, String taskId);

    void remove(String tenantId, String sessionId, String taskId);
}


