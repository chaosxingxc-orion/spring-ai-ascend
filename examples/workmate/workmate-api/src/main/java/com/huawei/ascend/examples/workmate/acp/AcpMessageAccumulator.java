package com.huawei.ascend.examples.workmate.acp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges consecutive {@code agent_message_chunk} updates before emitting {@code message.delta}
 * drafts (W38 Phase 2 — inbound message accumulator).
 */
public class AcpMessageAccumulator {

    private final AcpInboundConverter inboundConverter = new AcpInboundConverter();
    private final StringBuilder chunkBuffer = new StringBuilder();
    private Map<String, Object> chunkMeta = Map.of();

    public List<RunEventDraft> ingest(Map<String, Object> update) {
        if (update == null) {
            return List.of();
        }
        if ("agent_message_chunk".equals(stringValue(update.get("sessionUpdate")))) {
            Map<String, Object> content = asMap(update.get("content"));
            String text = stringValue(content.get("text"));
            if (text != null) {
                chunkBuffer.append(text);
            }
            Map<String, Object> meta = asMap(update.get("_meta"));
            if (!meta.isEmpty()) {
                chunkMeta = meta;
            }
            return List.of();
        }
        List<RunEventDraft> out = new ArrayList<>(flush());
        RunEventDraft draft = inboundConverter.convert(update);
        if (draft != null) {
            out.add(draft);
        }
        return out;
    }

    public List<RunEventDraft> flush() {
        if (chunkBuffer.isEmpty()) {
            return List.of();
        }
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("sessionUpdate", "agent_message_chunk");
        update.put("content", Map.of("text", chunkBuffer.toString()));
        if (!chunkMeta.isEmpty()) {
            update.put("_meta", chunkMeta);
        }
        chunkBuffer.setLength(0);
        chunkMeta = Map.of();
        RunEventDraft draft = inboundConverter.convert(update);
        return draft == null ? List.of() : List.of(draft);
    }

    public List<RunEventDraft> ingestAll(List<Map<String, Object>> updates) {
        List<RunEventDraft> out = new ArrayList<>();
        if (updates == null) {
            return out;
        }
        for (Map<String, Object> update : updates) {
            out.addAll(ingest(update));
        }
        out.addAll(flush());
        return out;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
