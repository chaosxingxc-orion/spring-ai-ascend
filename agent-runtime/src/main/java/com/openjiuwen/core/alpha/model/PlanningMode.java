package com.openjiuwen.core.alpha.model;

/**
 * 规划模式——控制 LLM 生成 TaskGraph 的自主程度。
 *
 * 与 AutonomyLevel 联动：
 * - GUIDED → MANUAL
 * - ASSISTED → SEMI_AUTO
 * - META/AUTONOMOUS → AUTO（Alpha 策略默认不用，除非显式指定）
 */
public enum PlanningMode {

    /**
     * 自动规划：LLM 完全自主生成 TaskGraph，无需人类审核。
     * 适用于高信任 Agent（META/AUTONOMOUS）。
     */
    AUTO,

    /**
     * 半自动规划：LLM 生成 TaskGraph 后，需要人类审核确认。
     * 人类可以修改、删除、添加节点后再执行。
     * 适用于 ASSISTED 模式。
     */
    SEMI_AUTO,

    /**
     * 手动规划：人类显式定义 TaskGraph，LLM 不参与规划。
     * 适用于 GUIDED 模式或确定性工作流。
     */
    MANUAL
}
