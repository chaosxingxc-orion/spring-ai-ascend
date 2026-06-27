package com.huawei.ascend.examples.workmate.capability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.support.JsonFileStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CapabilityUsageStore {

    private static final Logger LOG = LoggerFactory.getLogger(CapabilityUsageStore.class);
    private static final String FILE_NAME = "capability-usage.json";

    private final JsonFileStore<Map<String, String>> backing;

    public CapabilityUsageStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.backing = new JsonFileStore<>(
                dataProperties.resolvedPath().resolve(FILE_NAME),
                objectMapper,
                new TypeReference<Map<String, String>>() {},
                HashMap::new,
                LOG);
    }

    Instant getLastUsed(String type, String id) {
        return backing.read(map -> {
            String raw = map.get(key(type, id));
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return Instant.parse(raw);
        });
    }

    void recordUsage(String type, String id, Instant instant) {
        backing.write(map -> map.put(key(type, id), instant.toString()));
    }

    Map<String, Instant> snapshot() {
        return backing.read(map -> {
            Map<String, Instant> copy = new HashMap<>();
            map.forEach((usageKey, value) -> copy.put(usageKey, Instant.parse(value)));
            return Map.copyOf(copy);
        });
    }

    private static String key(String type, String id) {
        return type + ":" + id;
    }
}
