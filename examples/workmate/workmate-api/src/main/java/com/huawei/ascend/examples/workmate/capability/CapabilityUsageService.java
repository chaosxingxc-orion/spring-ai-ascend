package com.huawei.ascend.examples.workmate.capability;

import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class CapabilityUsageService {

    private final CapabilityUsageStore store;

    public CapabilityUsageService(CapabilityUsageStore store) {
        this.store = store;
    }

    public void recordUsage(String type, String id) {
        if (type == null || type.isBlank() || id == null || id.isBlank()) {
            return;
        }
        store.recordUsage(type.trim(), id.trim(), Instant.now());
    }

    public Instant lastUsed(String type, String id) {
        return store.getLastUsed(type, id);
    }
}
