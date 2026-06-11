package com.huawei.ascend.agentsdk.spec.tool;

import com.huawei.ascend.agentsdk.support.ValidationException;

/**
 * Shared attribute/descriptor validation for the built-in resolvers. Lives in
 * its own type so the Java and MCP resolvers do not have to reach into the
 * HTTP resolver for neutral helpers.
 */
final class ToolRefAttributes {

    private ToolRefAttributes() {
    }

    static ToolDescriptor descriptor(ToolSpec spec) {
        if (spec.name() == null || spec.name().isBlank()) {
            throw new ValidationException("Tool name is required for ref scheme: " + spec.ref().scheme());
        }
        if (spec.description() == null || spec.description().isBlank()) {
            throw new ValidationException("Tool description is required: " + spec.name());
        }
        return new ToolDescriptor(spec.name(), spec.description(), spec.inputSchema(), spec.outputSchema());
    }

    static String required(java.util.Map<String, Object> attributes, String key) {
        String value = string(attributes.get(key));
        if (value == null || value.isBlank()) {
            throw new ValidationException("Missing required tool ref attribute: " + key);
        }
        return value;
    }

    static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
