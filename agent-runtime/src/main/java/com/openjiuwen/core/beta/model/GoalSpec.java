package com.openjiuwen.core.beta.model;

import java.util.*;

/**
 * 目标规格——Beta 策略的输入。
 *
 * Beta 策略不使用 TaskGraph，而是给 LLM 一个目标，
 * 让它自主决定如何完成。GoalSpec 是目标的结构化描述。
 *
 * @param goal                    目标描述
 * @param successCriteria         成功标准列表（文本形式）
 * @param context                 上下文信息（业务数据摘要、历史操作等）
 * @param priority                优先级（1-5，1=最高）
 * @param maxReplanCount          最大重规划次数（默认 5，防止无限 replan）
 * @param replanHistory           重规划历史（只读，每次 replan 追加，用于差异分析检测"假 replan"）
 * @param explicitlyApprovedTools 显式批准的写操作工具名集合（不在集合内的写操作默认需审批）
 */
public record GoalSpec(
    String goal,
    List<String> successCriteria,
    Map<String, String> context,
    int priority,
    int maxReplanCount,
    List<ReplanRecord> replanHistory,
    Set<String> explicitlyApprovedTools
) {

    public GoalSpec {
        if (goal == null || goal.isBlank()) throw new IllegalArgumentException("目标不能为空");
        successCriteria = successCriteria == null ? List.of() : List.copyOf(successCriteria);
        context = context == null ? Map.of() : Map.copyOf(context);
        if (priority < 1 || priority > 5) priority = 3;
        if (maxReplanCount <= 0) maxReplanCount = 5;
        replanHistory = replanHistory == null ? List.of() : List.copyOf(replanHistory);
        explicitlyApprovedTools = explicitlyApprovedTools == null ? Set.of() : Set.copyOf(explicitlyApprovedTools);
    }

    /** 向后兼容的紧凑构造函数（无 explicitlyApprovedTools） */
    public GoalSpec(String goal, List<String> successCriteria, Map<String, String> context, int priority) {
        this(goal, successCriteria, context, priority, 5, List.of(), Set.of());
    }

    /** 创建简单目标 */
    public static GoalSpec of(String goal) {
        return new GoalSpec(goal, List.of(), Map.of(), 3);
    }

    /** 创建带成功标准的目标 */
    public static GoalSpec of(String goal, List<String> successCriteria) {
        return new GoalSpec(goal, successCriteria, Map.of(), 3);
    }

    /** 创建带成功标准、replan 上限和批准工具的目标 */
    public static GoalSpec of(String goal, List<String> successCriteria, int maxReplanCount) {
        return new GoalSpec(goal, successCriteria, Map.of(), 3, maxReplanCount, List.of(), Set.of());
    }

    /** 当前已 replan 次数 */
    public int replanCount() {
        return replanHistory.size();
    }

    /** 是否还可以 replan */
    public boolean canReplan() {
        return replanCount() < maxReplanCount;
    }

    /** 追加一条 replan 记录，返回新的 GoalSpec（不可变） */
    public GoalSpec withReplan(ReplanRecord record) {
        var newHistory = new ArrayList<>(replanHistory);
        newHistory.add(record);
        return new GoalSpec(goal, successCriteria, context, priority, maxReplanCount, newHistory, explicitlyApprovedTools);
    }

    /** 追加显式批准的工具，返回新的 GoalSpec */
    public GoalSpec withApprovedTool(String toolName) {
        var newTools = new HashSet<>(explicitlyApprovedTools);
        newTools.add(toolName);
        return new GoalSpec(goal, successCriteria, context, priority, maxReplanCount, replanHistory, Set.copyOf(newTools));
    }

    /**
     * 单条 replan 记录。
     */
    public record ReplanRecord(
        int stepIndex,
        String reason,
        String newApproach
    ) {}
}
