package com.openjiuwen.core.kernel.model;

/**
 * 工具方法名称。
 * 对应 @Tool 注解方法的名称或 MCP 工具名。
 *
 * @param value 工具名称，如 "checkOrder"
 */
public record ToolName(String value) {

    public ToolName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ToolName 不能为空");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
