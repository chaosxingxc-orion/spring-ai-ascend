package com.huawei.ascend.examples.workmate.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON helpers for {@link SessionMessage} payloads. */
final class SessionMessageJson {

    static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private SessionMessageJson() {}

    static Map<String, Object> readPayload(SessionMessage message, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(message.getPayloadJson(), MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid session message JSON id=" + message.getId(), ex);
        }
    }

    static String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON", ex);
        }
    }

    static Map<String, Object> chatItem(String kind, String id, Map<String, Object> fields) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("kind", kind);
        item.putAll(fields);
        return item;
    }

    static String readUserText(SessionMessage message, ObjectMapper objectMapper) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayloadJson(), MAP_TYPE);
            Object text = payload.get("text");
            return text == null ? "" : text.toString();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid user message JSON id=" + message.getId(), ex);
        }
    }

    static List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry == null) {
                continue;
            }
            String text = String.valueOf(entry).trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    static String readOptionalString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
