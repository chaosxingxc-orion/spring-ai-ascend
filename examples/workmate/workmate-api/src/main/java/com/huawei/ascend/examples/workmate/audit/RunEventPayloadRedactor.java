package com.huawei.ascend.examples.workmate.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateAuditProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Redacts secrets/PII and truncates large payloads before persisting run_events (W22 / B1).
 * Live SSE may still stream full payloads; only the audit ledger is redacted.
 */
@Component
public class RunEventPayloadRedactor {

    private static final Pattern SECRET_KEY =
            Pattern.compile("(password|token|secret|api[_-]?key|cookie|authorization|credential|private[_-]?key|gateway[_-]?token|bot[_-]?token)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(?:\\+?86)?1[3-9]\\d{9}(?![0-9])");
    private static final Set<String> LARGE_TEXT_FIELDS = Set.of(
            "text", "summary", "feedback", "message", "stdout", "stderr", "content", "body", "command", "result", "args");
    /** Routing metadata on tool args — must survive redaction for UI cards and dogfood validators. */
    private static final Set<String> STRUCTURAL_ARG_FIELDS = Set.of(
            "to", "recipient", "from", "from_member", "frommember", "sender", "memberid", "handbacksource");
    /**
     * For team delegation tool events the task prompt ("message"/"description") is the content the UI
     * pins as the member's opening turn, so it must survive intact instead of being clipped to a 200-char
     * preview. PII/secret redaction and the overall payload size cap still apply.
     */
    private static final Set<String> DELEGATION_KEEP_FULL_FIELDS = Set.of("message", "description", "content");

    private final ObjectMapper objectMapper;
    private final WorkmateAuditProperties properties;

    public RunEventPayloadRedactor(ObjectMapper objectMapper, WorkmateAuditProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Map<String, Object> redact(String eventName, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        boolean keepDelegationText = isDelegationToolPayload(payload);
        Map<String, Object> redacted = redactValue(payload, null, keepDelegationText);
        return enforceSizeLimit(redacted);
    }

    /** team.build_team / team.send_message tool events carry the delegated task prompt. */
    private boolean isDelegationToolPayload(Map<String, Object> payload) {
        Object toolName = payload.get("toolName");
        if (!(toolName instanceof String name)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("send_message") || lower.contains("build_team");
    }

    private Map<String, Object> redactValue(Map<String, Object> map, String parentKey, boolean keepDelegationText) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSecretKey(key)) {
                out.put(key, "[REDACTED]");
                continue;
            }
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedMap = new LinkedHashMap<>();
                nested.forEach((k, v) -> nestedMap.put(String.valueOf(k), v));
                out.put(key, redactValue(nestedMap, key, keepDelegationText));
            } else if (value instanceof List<?> list) {
                out.put(key, redactList(list, key, keepDelegationText));
            } else if (value instanceof String text) {
                if ("args".equals(key)) {
                    Map<String, Object> parsedArgs = parseJsonObjectMap(text);
                    if (parsedArgs != null) {
                        out.put(key, redactValue(parsedArgs, key, keepDelegationText));
                        continue;
                    }
                }
                out.put(key, redactString(key, text, parentKey, keepDelegationText));
            } else {
                out.put(key, value);
            }
        }
        return out;
    }

    private List<Object> redactList(List<?> list, String parentKey, boolean keepDelegationText) {
        List<Object> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> nested) {
                Map<String, Object> nestedMap = new LinkedHashMap<>();
                nested.forEach((k, v) -> nestedMap.put(String.valueOf(k), v));
                out.add(redactValue(nestedMap, parentKey, keepDelegationText));
            } else if (item instanceof String text) {
                out.add(redactString(parentKey, text, parentKey, keepDelegationText));
            } else {
                out.add(item);
            }
        }
        return out;
    }

    private Object redactString(String fieldKey, String text, String parentKey, boolean keepDelegationText) {
        String redactedPii = redactPii(text);
        if (shouldTruncateField(fieldKey, parentKey, keepDelegationText)) {
            return previewWithHash(redactedPii);
        }
        return redactPii(redactedPii);
    }

    private boolean shouldTruncateField(String fieldKey, String parentKey, boolean keepDelegationText) {
        if (fieldKey == null) {
            return false;
        }
        String lower = fieldKey.toLowerCase(Locale.ROOT);
        if (STRUCTURAL_ARG_FIELDS.contains(lower)) {
            return false;
        }
        if (keepDelegationText && DELEGATION_KEEP_FULL_FIELDS.contains(lower)) {
            return false;
        }
        if (LARGE_TEXT_FIELDS.contains(lower)) {
            return true;
        }
        return parentKey != null && LARGE_TEXT_FIELDS.contains(parentKey.toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> parseJsonObjectMap(String text) {
        if (text == null || text.isBlank() || text.charAt(0) != '{') {
            return null;
        }
        try {
            return objectMapper.readValue(text, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static final com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> MAP_TYPE =
            new com.fasterxml.jackson.core.type.TypeReference<>() {};

    private Map<String, Object> previewWithHash(String text) {
        if (text == null) {
            return Map.of("preview", "", "bytes", 0);
        }
        int max = properties.previewMaxChars();
        String preview = text.length() <= max ? text : text.substring(0, max) + "…";
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("preview", preview);
        meta.put("bytes", text.getBytes(StandardCharsets.UTF_8).length);
        if (text.length() > max) {
            meta.put("sha256", sha256Hex(text));
        }
        return meta;
    }

    private String redactPii(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String step = EMAIL.matcher(text).replaceAll("[REDACTED_EMAIL]");
        return PHONE.matcher(step).replaceAll("[REDACTED_PHONE]");
    }

    private boolean isSecretKey(String key) {
        return key != null && SECRET_KEY.matcher(key).find();
    }

    private Map<String, Object> enforceSizeLimit(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            int limit = properties.maxPayloadBytes();
            if (json.getBytes(StandardCharsets.UTF_8).length <= limit) {
                return payload;
            }
            Map<String, Object> truncated = new LinkedHashMap<>();
            truncated.put("metadata_truncated", true);
            truncated.put("metadata_original_bytes", json.getBytes(StandardCharsets.UTF_8).length);
            truncated.put("event_preview", previewWithHash(json));
            return truncated;
        } catch (JsonProcessingException ex) {
            return Map.of("metadata_truncated", true, "preview", "[unserializable payload]");
        }
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
