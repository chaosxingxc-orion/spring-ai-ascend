package com.huawei.ascend.examples.workmate.acp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Parses NDJSON lines of ACP {@code sessionUpdate} envelopes (W38 Phase 3 sidecar bridge). */
@Component
public class AcpNdjsonParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public AcpNdjsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> parse(String ndjson) {
        if (ndjson == null || ndjson.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> updates = new ArrayList<>();
        for (String line : ndjson.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> row = objectMapper.readValue(line, MAP_TYPE);
                if (!row.isEmpty()) {
                    updates.add(new LinkedHashMap<>(row));
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid ACP NDJSON line: " + line, ex);
            }
        }
        return updates;
    }
}
