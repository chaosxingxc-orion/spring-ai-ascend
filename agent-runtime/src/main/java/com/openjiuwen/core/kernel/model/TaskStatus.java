package com.openjiuwen.core.kernel.model;

/**
 * 任务状态——覆盖 Agent 执行的全生命周期。
 * sealed interface 确保状态转换的所有分支都被编译器检查。
 *
 * 状态转换图：
 * <pre>
 * Created → Planning → Executing → Verifying → Completed
 *                ↓          ↓           ↓
 *             Failed     Paused      Failed
 *                          ↓
 *                       Cancelled
 * </pre>
 */
public sealed interface TaskStatus
    permits TaskStatus.Created,
            TaskStatus.Planning,
            TaskStatus.Executing,
            TaskStatus.Verifying,
            TaskStatus.Paused,
            TaskStatus.Completed,
            TaskStatus.Failed,
            TaskStatus.Cancelled {

    /** 状态名称 */
    String name();

    /** 是否为终态（不会再变化） */
    boolean isTerminal();

    /**
     * 已创建，尚未开始执行。
     */
    record Created() implements TaskStatus {
        @Override public String name() { return "CREATED"; }
        @Override public boolean isTerminal() { return false; }
    }

    /**
     * 规划中——LLM 正在分析目标、生成 TaskGraph。
     */
    record Planning() implements TaskStatus {
        @Override public String name() { return "PLANNING"; }
        @Override public boolean isTerminal() { return false; }
    }

    /**
     * 执行中——按 TaskGraph 拓扑排序执行子任务。
     */
    record Executing() implements TaskStatus {
        @Override public String name() { return "EXECUTING"; }
        @Override public boolean isTerminal() { return false; }
    }

    /**
     * 验证中——LLM 正在检查执行结果是否满足目标。
     */
    record Verifying() implements TaskStatus {
        @Override public String name() { return "VERIFYING"; }
        @Override public boolean isTerminal() { return false; }
    }

    /**
     * 已暂停——等待外部输入（人工审批、补充信息等）。
     * 可以通过 resume() 恢复执行。
     */
    record Paused(YieldReason reason) implements TaskStatus {
        @Override public String name() { return "PAUSED"; }
        @Override public boolean isTerminal() { return false; }
    }

    /**
     * 已完成——执行成功，结果可用。
     */
    record Completed() implements TaskStatus {
        @Override public String name() { return "COMPLETED"; }
        @Override public boolean isTerminal() { return true; }
    }

    /**
     * 已失败——执行过程中出现不可恢复的错误。
     */
    record Failed(String errorMessage) implements TaskStatus {
        @Override public String name() { return "FAILED"; }
        @Override public boolean isTerminal() { return true; }
    }

    /**
     * 已取消——用户主动取消执行。
     */
    record Cancelled() implements TaskStatus {
        @Override public String name() { return "CANCELLED"; }
        @Override public boolean isTerminal() { return true; }
    }
}
