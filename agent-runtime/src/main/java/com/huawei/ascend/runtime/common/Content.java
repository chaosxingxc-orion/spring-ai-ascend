package com.huawei.ascend.runtime.common;

import java.util.Map;
import java.util.Objects;

/**
 * A single content part of a {@link Message}.
 *
 * <p>Modelled after the agentscope-runtime {@code Content} hierarchy but kept
 * as one lightweight record keyed by {@code type} ({@code text}, {@code image},
 * {@code audio}, {@code data}, {@code tool_result}, {@code artifact_ref}, ...)
 * rather than a class-per-modality tree. {@code value} holds the text string for
 * {@code text} parts, or a structured/reference object for richer modalities.
 *
 * @param type     content type discriminator; see {@link ContentType}.
 * @param value    the content value: a {@code String} for text, otherwise a
 *                 structured object or reference id.
 * @param metadata optional per-part attributes; never {@code null}.
 */
public record Content(
        String type,
        Object value,
        Map<String, Object> metadata) {

    public Content {
        type = (type == null || type.isBlank()) ? ContentType.TEXT : type;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Creates a plain text content part. */
    public static Content text(String text) {
        return new Content(ContentType.TEXT, text == null ? "" : text, Map.of());
    }

    /** Creates a structured data content part. */
    public static Content data(Object value) {
        return new Content(ContentType.DATA, value, Map.of());
    }

    /**
     * Returns the value as text when this is a text part, otherwise the
     * {@code toString()} of the value (empty string when {@code null}).
     */
    public String asText() {
        if (value == null) {
            return "";
        }
        return value instanceof String s ? s : value.toString();
    }

    /** True when this part carries plain text. */
    public boolean isText() {
        return Objects.equals(ContentType.TEXT, type);
    }
}
