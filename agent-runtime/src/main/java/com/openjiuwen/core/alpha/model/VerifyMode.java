package com.openjiuwen.core.alpha.model;

/**
 * 验证模式——控制 Verify 阶段的严格程度。
 */
public enum VerifyMode {

    /**
     * 严格验证：LLM 详细检查每个节点的输出是否满足 expectedOutput。
     * 适用于高精度场景（金融、医疗）。
     */
    STRICT,

    /**
     * 轻量验证：LLM 只检查最终输出是否满足目标。
     * 不检查中间节点，节省 Token。
     * 适用于低风险场景。
     */
    LIGHT,

    /**
     * 无验证：执行完成后直接返回，不做任何验证。
     * 适用于确定性工作流或已充分测试的场景。
     */
    NONE
}
