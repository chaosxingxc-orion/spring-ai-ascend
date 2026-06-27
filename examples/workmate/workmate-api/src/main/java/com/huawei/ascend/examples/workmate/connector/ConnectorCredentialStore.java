package com.huawei.ascend.examples.workmate.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.support.JsonFileStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConnectorCredentialStore {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorCredentialStore.class);
    private static final String FILE_NAME = "connector-credentials.json";

    private final JsonFileStore<Map<String, Map<String, String>>> backing;

    public ConnectorCredentialStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.backing = new JsonFileStore<>(
                dataProperties.resolvedPath().resolve(FILE_NAME),
                objectMapper,
                new TypeReference<Map<String, Map<String, String>>>() {},
                HashMap::new,
                LOG);
    }

    public Optional<Map<String, String>> find(String connectorId) {
        return backing.read(map -> {
            Map<String, String> stored = map.get(connectorId);
            if (stored == null || stored.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(Map.copyOf(stored));
        });
    }

    public void save(String connectorId, Map<String, String> values) {
        backing.write(map -> map.put(connectorId, new HashMap<>(values)));
    }

    public void clear(String connectorId) {
        backing.write(map -> map.remove(connectorId));
    }
}
