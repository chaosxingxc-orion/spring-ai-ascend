package com.openjiuwen.core.dispatch;

/**
 * 自主度四档——控制 Agent 的行为边界。
 *
 * 显式声明，无框架默认。开发者在定义 Agent 时必须明确指定自主度。
 * 这不是可选的——自主度决定了：
 * - Agent 能调用哪些工具
 * - Agent 是否能自主决定下一步
 * - 何时需要人类介入
 * - 元Agent的晋升路径
 *
 * 晋升路径：GUIDED → ASSISTED → META → AUTONOMOUS
 * 不可跳级，每一步都需要稳定运行一段时间。
 */
public enum AutonomyLevel {

    /**
     * 第一档：引导模式。
     * - Agent 只执行显式指令，不自主决策
     * - 每一步都需要人类确认
     * - 适用于：新上线的 Agent、高风险操作
     * - Alpha 策略强制：PlanningMode = MANUAL
     */
    GUIDED(
        "引导模式",
        false,  // 不允许自主工具选择
        false,  // 不允许自主规划
        true    // 每步都需要人类确认
    ),

    /**
     * 第二档：辅助模式。
     * - Agent 可以自主调用安全工具
     * - 规划阶段需要人类审核
     * - 执行阶段可以自主进行
     * - 适用于：经过验证的常规操作
     * - Alpha 策略：PlanningMode = SEMI_AUTO
     */
    ASSISTED(
        "辅助模式",
        true,   // 允许自主调用安全工具
        false,  // 规划需要人类审核
        false   // 执行阶段不需要每步确认
    ),

    /**
     * 第三档：元Agent模式。
     * - Agent 可以生成子 Agent 处理子任务
     * - 自主规划和执行
     * - 验证失败时可以自主重试
     * - 适用于：成熟的多步骤流程
     * - Alpha/Beta 策略均可使用
     */
    META(
        "元Agent模式",
        true,   // 允许自主调用所有工具
        true,   // 允许自主规划
        false   // 仅在关键节点需要确认
    ),

    /**
     * 第四档：完全自主模式。
     * - Agent 完全自主决策和执行
     * - 仅受 SafetyBoundary 和 Budget 约束
     * - 适用于：经过长期验证的高信任 Agent
     * - Beta 策略（LLM 自主编排）的典型模式
     */
    AUTONOMOUS(
        "完全自主模式",
        true,   // 允许自主调用所有工具
        true,   // 允许自主规划
        false   // 不需要人类确认（仅 SafetyBoundary 约束）
    );

    private final String displayName;
    private final boolean allowAutonomousToolUse;
    private final boolean allowAutonomousPlanning;
    private final boolean requireHumanConfirmation;

    AutonomyLevel(String displayName, boolean allowAutonomousToolUse,
                  boolean allowAutonomousPlanning, boolean requireHumanConfirmation) {
        this.displayName = displayName;
        this.allowAutonomousToolUse = allowAutonomousToolUse;
        this.allowAutonomousPlanning = allowAutonomousPlanning;
        this.requireHumanConfirmation = requireHumanConfirmation;
    }

    public String displayName() { return displayName; }

    /** 是否允许 Agent 自主选择和调用工具 */
    public boolean allowAutonomousToolUse() { return allowAutonomousToolUse; }

    /** 是否允许 Agent 自主制定和修改执行计划 */
    public boolean allowAutonomousPlanning() { return allowAutonomousPlanning; }

    /** 是否每步都需要人类确认 */
    public boolean requireHumanConfirmation() { return requireHumanConfirmation; }

    /**
     * 检查是否可以从当前级别晋升到目标级别。
     * 晋升必须逐级进行：GUIDED → ASSISTED → META → AUTONOMOUS
     */
    public boolean canPromoteTo(AutonomyLevel target) {
        return this.ordinal() + 1 == target.ordinal();
    }
}
