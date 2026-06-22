package com.huawei.ascend.runtime.engine.spi;

import java.util.Map;

/**
 * Optional tool dependency declared by a skill. It is descriptive metadata only:
 * the SkillHub SPI does not call tools itself.
 */
public record SkillToolDependency(String type, String name, Map<String, Object> metadata) {

    public SkillToolDependency {
        type = type == null || type.isBlank() ? "unknown" : type;
        name = name == null ? "" : name;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
