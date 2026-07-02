package com.openjiuwen.core.alpha.verifier;

/**
 * 验证失败后的处理策略。
 *
 * 当 Verifier 判定结果不通过时，有以下选择：
 * - LOCAL_REPLAN: 只重做失败节点（及其下游依赖）
 * - GLOBAL_REPLAN: 重新规划整个 TaskGraph
 * - ACCEPT_PARTIAL: 接受部分结果，放弃失败节点
 *
 * 策略选择由 VerifyMode 和失败程度共同决定：
 * - STRICT + 多节点失败 → GLOBAL_REPLAN
 * - STRICT + 少量节点失败 → LOCAL_REPLAN
 * - LIGHT + 验证失败 → LOCAL_REPLAN
 * - 多次 LOCAL_REPLAN 仍然失败 → ACCEPT_PARTIAL
 */
public sealed interface ReplanStrategy
    permits ReplanStrategy.LocalReplan,
            ReplanStrategy.GlobalReplan,
            ReplanStrategy.AcceptPartial {

    /**
     * 局部重做：只重做失败节点及其下游节点。
     * @param maxRounds 最大局部重做轮次
     */
    record LocalReplan(int maxRounds) implements ReplanStrategy {
        public LocalReplan {
            if (maxRounds < 1) maxRounds = 2;
        }
        public static LocalReplan of() { return new LocalReplan(2); }
    }

    /**
     * 全局重规划：丢弃整个 TaskGraph，重新规划。
     * 代价最高，但可能解决根本性问题。
     */
    record GlobalReplan() implements ReplanStrategy {}

    /**
     * 接受部分结果：放弃失败节点，返回已完成的结果。
     * 最后的手段。
     */
    record AcceptPartial() implements ReplanStrategy {}
}
