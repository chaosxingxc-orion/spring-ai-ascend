package com.openjiuwen.core.alpha.model;

/**
 * 执行策略配置——Alpha 策略的核心参数。
 *
 * 控制 Plan-Execute-Verify 的行为：
 * - planningMode: 规划的自主程度
 * - verifyMode: 验证的严格程度
 * - maxRetries: 验证失败后的最大重试次数
 * - parallelism: 同层节点的最大并行数
 * - bestOfKReplan: 重规划开关——PlanOrAnswerError→GlobalReplan 时是否生成 K 个候选 plan 选优（默认关，opt-in）
 * - bestOfK: 候选数（hard-capped [1,3]；仅在 bestOfKReplan=true 时生效）
 */
public record ExecutionPolicy(
    PlanningMode planningMode,
    VerifyMode verifyMode,
    int maxRetries,
    int maxParallelism,
    boolean enableAdaptiveReplanning,
    boolean bestOfKReplan,
    int bestOfK
) {

    public ExecutionPolicy {
        // hard cap K∈[1,3]——对齐 ReplanStrategy.LocalReplan.of() 的 clamp 范式，防配置失真。
        // bestOfK<1 规整为 1（即便 bestOfKReplan=true 也至少 1 候选，退化为单次 plan，零回归）。
        if (bestOfK < 1) bestOfK = 1;
        if (bestOfK > 3) bestOfK = 3;
    }

    /**
     * 向后兼容 5 参数构造（bestOfKReplan=false, bestOfK=2）——既有调用点零改动继续编译；委托 canonical。
     */
    public ExecutionPolicy(PlanningMode planningMode, VerifyMode verifyMode, int maxRetries,
                           int maxParallelism, boolean enableAdaptiveReplanning) {
        this(planningMode, verifyMode, maxRetries, maxParallelism, enableAdaptiveReplanning, false, 2);
    }

    /** 默认生产配置 */
    public static ExecutionPolicy productionDefault() {
        return new ExecutionPolicy(
            PlanningMode.SEMI_AUTO,
            VerifyMode.STRICT,
            3,
            4,
            true,
            false,
            2
        );
    }

    /** 开发调试配置：宽松 */
    public static ExecutionPolicy developmentDefault() {
        return new ExecutionPolicy(
            PlanningMode.AUTO,
            VerifyMode.LIGHT,
            5,
            8,
            true,
            false,
            2
        );
    }
}
