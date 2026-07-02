package com.openjiuwen.core.meta;

import com.openjiuwen.core.alpha.model.ExecutionPolicy;
import com.openjiuwen.core.dispatch.AutonomyLevel;
import com.openjiuwen.core.kernel.model.AgentName;
import com.openjiuwen.core.kernel.model.Budget;

import java.util.List;
import java.util.Map;

/**
 * Agent 定义——注册到 AgentRegistry 的完整描述。
 *
 * 这是 Runtime 中"一个 Agent 是什么"的完整描述。
 * 由 @Agent 注解扫描生成，或通过 API 动态注册。
 *
 * 关键字段：
 * - name:        Agent 名称（全局唯一）
 * - systemPrompt: 系统提示（定义 Agent 的角色、能力、行为规范）
 * - tools:       可用工具列表
 * - autonomyLevel: 自主度（决定路由到 Alpha 还是 Beta 策略）
 * - budget:      默认预算
 * - basedOn:     基于（元Agent的继承关系）
 */
public record AgentDefinition(
    AgentName name,
    String description,
    String systemPrompt,
    List<ToolDefinition> tools,
    AutonomyLevel autonomyLevel,
    Budget budget,
    ExecutionPolicy executionPolicy,
    String model,
    String basedOn,
    Map<String, Object> metadata
) {

    public AgentDefinition {
        if (name == null) throw new IllegalArgumentException("Agent 名称不能为 null");
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "你是一个 AI 助手。";
        }
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (autonomyLevel == null) autonomyLevel = AutonomyLevel.GUIDED;
        if (budget == null) budget = Budget.Fixed.productionDefault();
    }

    /**
     * 工具定义。
     */
    public record ToolDefinition(
        String name,
        String description,
        List<ParameterDefinition> parameters
    ) {}

    /**
     * 工具参数定义。
     */
    public record ParameterDefinition(
        String name,
        String description,
        String type,
        boolean required
    ) {}
}
