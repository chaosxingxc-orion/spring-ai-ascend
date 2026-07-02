package com.openjiuwen.core.alpha.model;

import com.openjiuwen.core.alpha.graph.TaskGraph;

import java.util.List;

/**
 * Plan 阶段的输出——包含 TaskGraph 和校验结果。
 *
 * 设计决策：Planner 输出不仅仅是 TaskGraph，还包含校验报告。
 * 校验在 Planner 内部完成，而不是交给外部——这样 Planner 可以自纠错。
 * 如果 DAG 有环或依赖不合理，Planner 尝试修复后再输出。
 */
public record PlanResult(
    TaskGraph graph,
    boolean isValid,
    List<PlanIssue> issues,
    int planningAttempts
) {

    public PlanResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /** 成功结果 */
    public static PlanResult success(TaskGraph graph, int attempts) {
        return new PlanResult(graph, true, List.of(), attempts);
    }

    /** 带问题但仍然可用 */
    public static PlanResult withWarnings(TaskGraph graph, List<PlanIssue> issues, int attempts) {
        return new PlanResult(graph, true, issues, attempts);
    }

    /** 规划失败 */
    public static PlanResult failure(List<PlanIssue> issues, int attempts) {
        return new PlanResult(null, false, issues, attempts);
    }

    /**
     * 规划问题——校验阶段发现的问题。
     */
    public enum IssueSeverity {
        ERROR,   // 阻断性问题，必须修复
        WARNING  // 警告，可以继续
    }

    public record PlanIssue(
        IssueSeverity severity,
        String code,
        String message,
        String nodeId   // 关联的节点 ID，可能为 null
    ) {
        public static PlanIssue error(String code, String message) {
            return new PlanIssue(IssueSeverity.ERROR, code, message, null);
        }

        public static PlanIssue error(String code, String message, String nodeId) {
            return new PlanIssue(IssueSeverity.ERROR, code, message, nodeId);
        }

        public static PlanIssue warning(String code, String message) {
            return new PlanIssue(IssueSeverity.WARNING, code, message, null);
        }
    }
}
