package com.openjiuwen.core.kernel.model;

/**
 * 预算模型。
 * 控制 Agent 执行的资源消耗上限。
 * sealed interface 确保只有两种预算模式：固定预算和无限预算。
 */
public sealed interface Budget permits Budget.Fixed, Budget.Unlimited {

    /** 最大允许的 LLM 调用次数 */
    int maxLLMCalls();

    /** 最大允许的工具调用次数 */
    int maxToolCalls();

    /** Token 预算上限（input + output） */
    long maxTokens();

    /** 最大执行时长（毫秒），0 表示不限 */
    long timeoutMillis();

    /**
     * 固定预算：显式声明所有上限。
     * 适用于生产环境，硬性限制资源消耗。
     */
    record Fixed(
        int maxLLMCalls,
        int maxToolCalls,
        long maxTokens,
        long timeoutMillis
    ) implements Budget {

        public Fixed {
            if (maxLLMCalls <= 0) throw new IllegalArgumentException("maxLLMCalls 必须 > 0");
            if (maxToolCalls <= 0) throw new IllegalArgumentException("maxToolCalls 必须 > 0");
            if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens 必须 > 0");
        }

        /** 默认生产级预算：10次LLM调用 / 20次工具调用 / 100K tokens / 5分钟 */
        public static Fixed productionDefault() {
            return new Fixed(10, 20, 100_000L, 300_000L);
        }

        /** 开发调试预算：放宽限制 */
        public static Fixed developmentDefault() {
            return new Fixed(50, 100, 1_000_000L, 0L);
        }
    }

    /**
     * 无限预算：仅受超时限制。
     * 适用于受信任的内部 Agent，仍需设置超时防止无限挂起。
     */
    record Unlimited(long timeoutMillis) implements Budget {

        public Unlimited {
            if (timeoutMillis < 0) throw new IllegalArgumentException("timeoutMillis 不能为负");
        }

        @Override public int maxLLMCalls() { return Integer.MAX_VALUE; }
        @Override public int maxToolCalls() { return Integer.MAX_VALUE; }
        @Override public long maxTokens() { return Long.MAX_VALUE; }
    }
}
