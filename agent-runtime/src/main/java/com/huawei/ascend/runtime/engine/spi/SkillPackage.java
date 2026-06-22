package com.huawei.ascend.runtime.engine.spi;

import java.util.Arrays;
import java.util.Map;

/**
 * Optional packaged skill payload, usually a zip archive containing SKILL.md
 * and adjacent reference files.
 */
public record SkillPackage(String skillId, String mediaType, byte[] content, Map<String, Object> metadata) {

    public SkillPackage {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        mediaType = mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType;
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
