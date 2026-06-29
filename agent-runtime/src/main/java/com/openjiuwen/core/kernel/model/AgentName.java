package com.openjiuwen.core.kernel.model;

/**
 * Agent 名称。
 * 在 {@link com.openjiuwen.runtime.core.dispatch.AgentRegistry} 中用作唯一键。
 * 遵循 Spring Bean 命名规范：小写字母开头，允许中划线。
 *
 * @param value Agent 名称，如 "order-service"
 */
public record AgentName(String value) {

    public AgentName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AgentName 不能为空");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
