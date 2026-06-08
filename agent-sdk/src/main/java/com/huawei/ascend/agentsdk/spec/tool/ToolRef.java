package com.huawei.ascend.agentsdk.spec.tool;

import java.util.Map;

public record ToolRef(String scheme, Map<String, Object> attributes) {
    public ToolRef {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ToolRef of(String scheme, Map<String, Object> attributes) {
        return new ToolRef(scheme, attributes);
    }
}

