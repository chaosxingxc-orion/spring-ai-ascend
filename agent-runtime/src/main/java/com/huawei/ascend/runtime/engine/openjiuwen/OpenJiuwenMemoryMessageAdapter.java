package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Converts between openJiuwen's LLM message model and the runtime-neutral
 * memory record model.
 *
 * <p>The adapter only depends on stable message concepts that exist across the
 * current openJiuwen lines: role, content, optional name, and optional tool call
 * id. Higher-level memory extraction, compaction, vector indexing, and ranking
 * remain owned by the concrete {@link MemoryProvider}.
 */
final class OpenJiuwenMemoryMessageAdapter {

    static final String METADATA_SOURCE = "source";
    static final String METADATA_NAME = "name";
    static final String METADATA_TOOL_CALL_ID = "toolCallId";
    static final String SOURCE_OPENJIUWEN = "openjiuwen";

    List<MemoryProvider.MemoryRecord> toMemoryRecords(List<BaseMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .filter(Objects::nonNull)
                .map(this::toMemoryRecord)
                .toList();
    }

    MemoryProvider.MemoryRecord toMemoryRecord(BaseMessage message) {
        String role = normalizeRole(message.getRole());
        String content = message.getContentAsString();
        Map<String, Object> metadata = messageMetadata(message);
        return new MemoryProvider.MemoryRecord(null, role, content, metadata);
    }

    BaseMessage toOpenJiuwenMessage(MemoryProvider.MemoryRecord record) {
        String role = normalizeRole(record.role());
        String content = record.content();
        String name = stringMetadata(record.metadata(), METADATA_NAME);
        return switch (role) {
            case "assistant" -> new AssistantMessage(content);
            case "system" -> hasText(name) ? new SystemMessage(content, name) : new SystemMessage(content);
            case "tool" -> toolMessage(content, name, stringMetadata(record.metadata(), METADATA_TOOL_CALL_ID));
            case "user" -> hasText(name) ? new UserMessage(content, name) : new UserMessage(content);
            default -> new BaseMessage(role, content);
        };
    }

    private static Map<String, Object> messageMetadata(BaseMessage message) {
        String name = message.getName();
        if (message instanceof ToolMessage toolMessage && hasText(toolMessage.getToolCallId())) {
            if (hasText(name)) {
                return Map.of(
                        METADATA_SOURCE, SOURCE_OPENJIUWEN,
                        METADATA_NAME, name,
                        METADATA_TOOL_CALL_ID, toolMessage.getToolCallId());
            }
            return Map.of(
                    METADATA_SOURCE, SOURCE_OPENJIUWEN,
                    METADATA_TOOL_CALL_ID, toolMessage.getToolCallId());
        }
        if (hasText(name)) {
            return Map.of(METADATA_SOURCE, SOURCE_OPENJIUWEN, METADATA_NAME, name);
        }
        return Map.of(METADATA_SOURCE, SOURCE_OPENJIUWEN);
    }

    private static BaseMessage toolMessage(String content, String name, String toolCallId) {
        String stableToolCallId = hasText(toolCallId) ? toolCallId : "runtime-memory";
        if (hasText(name)) {
            return new ToolMessage(content, stableToolCallId, name);
        }
        return new ToolMessage(content, stableToolCallId);
    }

    private static String stringMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value instanceof String text ? text : null;
    }

    private static String normalizeRole(String role) {
        return hasText(role) ? role.trim().toLowerCase(Locale.ROOT) : "unknown";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
