package com.openjiuwen.core.alpha.model;

/**
 * 错误处理策略——节点执行失败时的处理方式。
 *
 * <p>四种策略覆盖了从最严格到最宽松的错误处理：
 * <ul>
 *   <li>FAIL_FAST: 一个节点失败 → 整个任务立即终止</li>
 *   <li>RETRY: 失败节点重试 N 次后决定</li>
 *   <li>DEGRADE: 失败节点降级处理（跳过/用默认值）</li>
 *   <li>PARTIAL_REPLAN: 只重跑失败节点及其下游节点</li>
 * </ul>
 */
public sealed interface ErrorPolicy
    permits ErrorPolicy.FailFast,
            ErrorPolicy.Retry,
            ErrorPolicy.Degrade,
            ErrorPolicy.PartialReplan {

    /** 快速失败：一个节点失败，整个 TaskGraph 执行终止。 */
    record FailFast() implements ErrorPolicy {}

    /**
     * 重试：失败节点重试指定次数，指数退避。
     *
     * @param maxRetries  最大重试次数
     * @param backoffMs   重试间隔（毫秒），每次翻倍
     * @param onExhausted 重试耗尽后的行为
     */
    record Retry(int maxRetries, long backoffMs, ErrorPolicy onExhausted) implements ErrorPolicy {
        public Retry {
            if (maxRetries < 1) maxRetries = 1;
            if (backoffMs < 0) backoffMs = 1000;
            if (onExhausted == null) onExhausted = new FailFast();
        }

        public static Retry of(int maxRetries) {
            return new Retry(maxRetries, 1000L, new FailFast());
        }

        public static Retry of(int maxRetries, ErrorPolicy onExhausted) {
            return new Retry(maxRetries, 1000L, onExhausted);
        }
    }

    /** 降级：节点失败时用默认值替代或直接跳过。 */
    record Degrade(Object defaultValue) implements ErrorPolicy {
        public static Degrade skip() { return new Degrade(null); }
    }

    /** 局部重规划：重跑失败节点及其下游节点。 */
    record PartialReplan(int maxReplanRounds) implements ErrorPolicy {
        public PartialReplan {
            if (maxReplanRounds < 1) maxReplanRounds = 2;
        }
        public static PartialReplan of() { return new PartialReplan(2); }
    }
}
