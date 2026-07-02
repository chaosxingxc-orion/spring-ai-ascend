package com.openjiuwen.core.kernel.model;

/**
 * 任务图中的节点标识。
 * 在 TaskGraph 内唯一，用于描述 DAG 中节点间的依赖关系。
 *
 * 限制字符集为字母、数字、连字符、下划线、点号，
 * 防止在 XML prompt 中通过属性值注入。
 *
 * @param value 节点 ID，如 "A", "B", "step-1", "node.sub"
 */
public record NodeId(String value) {

    private static final String VALID_PATTERN = "[a-zA-Z0-9_.\\-]+";

    public NodeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("NodeId 不能为空");
        }
        if (!value.matches(VALID_PATTERN)) {
            throw new IllegalArgumentException(
                "NodeId 包含非法字符: '" + value + "'，只允许字母、数字、下划线、连字符、点号");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
