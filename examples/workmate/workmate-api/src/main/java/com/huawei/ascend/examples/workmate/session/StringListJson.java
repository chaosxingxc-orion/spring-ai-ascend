package com.huawei.ascend.examples.workmate.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class StringListJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private StringListJson() {}

    static List<String> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = MAPPER.readValue(json, LIST_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return List.of();
            }
            return List.copyOf(new LinkedHashSet<>(parsed));
        } catch (Exception ex) {
            return List.of();
        }
    }

    static String write(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>(new LinkedHashSet<>(values));
        normalized.removeIf(id -> id == null || id.isBlank());
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize connector id list", ex);
        }
    }
}
