package com.openjiuwen.runtime.alpha.planner;

import com.openjiuwen.core.alpha.graph.*;
import com.openjiuwen.core.alpha.model.*;
import com.openjiuwen.core.kernel.model.NodeId;
import com.openjiuwen.runtime.alpha.util.ToolNames;

import java.util.*;

/**
 * Plan 校验器——独立于 LLM 的纯逻辑校验。
 *
 * 校验项：
 * 1. DAG 环检测（基于 Tarjan SCC 算法）
 * 2. 节点依赖合理性（TOOL_CALL 节点的工具是否在可用工具列表中？）
 * 3. 预算可行性预估（节点数 * 预估单节点消耗 vs 预算上限）
 * 4. 约束检查（MaxSteps / RequiredTool / OutputFormat）
 *
 * 设计原则：
 * - 纯函数，无副作用
 * - 不调 LLM
 * - 返回所有问题（不只报第一个）
 */
public class PlanValidator {

    private static final int ESTIMATED_TOKENS_PER_LLM_NODE = 2000;
    private static final int ESTIMATED_TOKENS_PER_TOOL_NODE = 500;

    /**
     * 完整校验。
     */
    public PlanResult validate(TaskGraph graph, PlanGoal goal, List<Constraint> constraints) {
        List<PlanResult.PlanIssue> issues = new ArrayList<>();

        // 1. DAG 环检测
        checkCycles(graph, issues);

        // 2. 边的端点存在性
        checkEdgeEndpoints(graph, issues);

        // 3. 工具可用性
        checkToolAvailability(graph, goal, issues);

        // 4. 预算可行性
        checkBudgetFeasibility(graph, goal, issues);

        // 5. 约束检查
        checkConstraints(graph, constraints, issues);

        // 6. 占位符卫生——description 与 inputs.value 不得含未解析/内嵌的 ${ （会被 LLM 原样 echo）
        checkPlaceholderHygiene(graph, issues);

        boolean hasErrors = issues.stream()
            .anyMatch(i -> i.severity() == PlanResult.IssueSeverity.ERROR);

        if (hasErrors) {
            return PlanResult.failure(issues, 1);
        } else if (issues.isEmpty()) {
            return PlanResult.success(graph, 1);
        } else {
            return PlanResult.withWarnings(graph, issues, 1);
        }
    }

    // ==================== 环检测（Tarjan SCC） ====================

    /**
     * Tarjan 强连通分量算法检测 DAG 中的环。
     *
     * 如果找到任何大小 > 1 的 SCC，说明存在环。
     * 环中的所有节点 ID 会记录到 issues 中。
     */
    private void checkCycles(TaskGraph graph, List<PlanResult.PlanIssue> issues) {
        Map<NodeId, Integer> index = new HashMap<>();
        Map<NodeId, Integer> lowlink = new HashMap<>();
        Map<NodeId, Boolean> onStack = new HashMap<>();
        Deque<NodeId> stack = new ArrayDeque<>();
        int[] counter = {0};

        // 构建邻接表
        Map<NodeId, List<NodeId>> adj = new HashMap<>();
        for (TaskNode node : graph.nodes()) {
            adj.putIfAbsent(node.id(), new ArrayList<>());
        }
        for (TaskEdge edge : graph.edges()) {
            adj.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
        }

        List<Set<NodeId>> sccs = new ArrayList<>();

        for (TaskNode node : graph.nodes()) {
            if (!index.containsKey(node.id())) {
                tarjanDFS(node.id(), adj, index, lowlink, onStack, stack, counter, sccs);
            }
        }

        // 大小 > 1 的 SCC 就是环
        for (Set<NodeId> scc : sccs) {
            if (scc.size() > 1) {
                issues.add(PlanResult.PlanIssue.error(
                    "CYCLE_DETECTED",
                    "检测到环路: " + scc,
                    scc.iterator().next().value()
                ));
            }
        }

        // 自环检查
        for (TaskEdge edge : graph.edges()) {
            if (edge.from().equals(edge.to())) {
                issues.add(PlanResult.PlanIssue.error(
                    "SELF_LOOP",
                    "节点 " + edge.from() + " 存在自环",
                    edge.from().value()
                ));
            }
        }
    }

    private void tarjanDFS(NodeId v, Map<NodeId, List<NodeId>> adj,
                           Map<NodeId, Integer> index, Map<NodeId, Integer> lowlink,
                           Map<NodeId, Boolean> onStack, Deque<NodeId> stack,
                           int[] counter, List<Set<NodeId>> sccs) {
        index.put(v, counter[0]);
        lowlink.put(v, counter[0]);
        counter[0]++;
        stack.push(v);
        onStack.put(v, true);

        for (NodeId w : adj.getOrDefault(v, List.of())) {
            if (!index.containsKey(w)) {
                tarjanDFS(w, adj, index, lowlink, onStack, stack, counter, sccs);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.getOrDefault(w, false)) {
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        if (lowlink.get(v).equals(index.get(v))) {
            Set<NodeId> scc = new LinkedHashSet<>();
            NodeId w;
            do {
                w = stack.pop();
                onStack.put(w, false);
                scc.add(w);
            } while (!w.equals(v));
            sccs.add(scc);
        }
    }

    // ==================== 边端点检查 ====================

    private void checkEdgeEndpoints(TaskGraph graph, List<PlanResult.PlanIssue> issues) {
        Set<NodeId> nodeIds = new HashSet<>();
        for (TaskNode node : graph.nodes()) {
            nodeIds.add(node.id());
        }

        for (TaskEdge edge : graph.edges()) {
            if (!nodeIds.contains(edge.from())) {
                issues.add(PlanResult.PlanIssue.error(
                    "INVALID_EDGE",
                    "边的起点 " + edge.from() + " 不存在于节点列表中"
                ));
            }
            if (!nodeIds.contains(edge.to())) {
                issues.add(PlanResult.PlanIssue.error(
                    "INVALID_EDGE",
                    "边的终点 " + edge.to() + " 不存在于节点列表中"
                ));
            }
        }
    }

    // ==================== 工具可用性检查 ====================

    private void checkToolAvailability(TaskGraph graph, PlanGoal goal,
                                       List<PlanResult.PlanIssue> issues) {
        if (goal.availableTools().isEmpty()) {
            return; // 未声明可用工具列表，跳过检查
        }

        for (TaskNode node : graph.nodes()) {
            if (node.type() == TaskNodeType.TOOL_CALL) {
                // 整段 description 即工具名（简化约定）；排毒层（工具名归一化）：归一化签名噪声，
                // 使 LLM 把 "name(params)" 抄进 description 时仍能正确比对（执行期同法兜底）。
                String rawDesc = node.description();
                String toolName = ToolNames.bareToolName(rawDesc);
                // 可观测层（工具名归一化）：归一化剥离了内容 → LLM 违规把签名抄进 description，发专属 WARNING 留痕
                // （与 trim 区分：仅当真有 "- "/"(" 噪声才报，纯空白差不误报）。
                if (!toolName.equals(rawDesc.trim())) {
                    issues.add(new PlanResult.PlanIssue(PlanResult.IssueSeverity.WARNING,
                        "TOOL_DESC_NOISE",
                        "节点 " + node.id() + " 的 description 含参数签名噪声（\"" + rawDesc
                            + "\"），已归一化为工具名 \"" + toolName + "\"（TOOL_CALL 的 description 应只填工具名）",
                        node.id().value()
                    ));
                }
                if (!goal.availableTools().contains(toolName)) {
                    issues.add(new PlanResult.PlanIssue(PlanResult.IssueSeverity.WARNING,
                        "UNKNOWN_TOOL",
                        "节点 " + node.id() + " 引用了未注册的工具: " + toolName,
                        node.id().value()
                    ));
                }
            }
        }
    }

    // ==================== 预算可行性检查 ====================

    private void checkBudgetFeasibility(TaskGraph graph, PlanGoal goal,
                                        List<PlanResult.PlanIssue> issues) {
        if (goal.budgetHint() == null) {
            return;
        }

        PlanGoal.PlanBudgetHint hint = goal.budgetHint();
        int llmNodeCount = 0;
        int toolNodeCount = 0;

        for (TaskNode node : graph.nodes()) {
            switch (node.type()) {
                case LLM_CALL, SUB_AGENT -> llmNodeCount++;
                case TOOL_CALL -> toolNodeCount++;
            }
        }

        long estimatedTokens =
            (long) llmNodeCount * ESTIMATED_TOKENS_PER_LLM_NODE +
            (long) toolNodeCount * ESTIMATED_TOKENS_PER_TOOL_NODE;

        if (estimatedTokens > hint.estimatedTokens()) {
            issues.add(PlanResult.PlanIssue.warning(
                "BUDGET_RISK",
                String.format("预估 Token 消耗 %d 超过预算提示 %d，可能导致预算耗尽",
                    estimatedTokens, hint.estimatedTokens())
            ));
        }

        if (llmNodeCount > hint.estimatedLLMCalls()) {
            issues.add(PlanResult.PlanIssue.warning(
                "BUDGET_RISK",
                String.format("LLM 调用次数 %d 超过预估 %d",
                    llmNodeCount, hint.estimatedLLMCalls())
            ));
        }
    }

    // ==================== 约束检查 ====================

    private void checkConstraints(TaskGraph graph, List<Constraint> constraints,
                                  List<PlanResult.PlanIssue> issues) {
        for (Constraint constraint : constraints) {
            switch (constraint) {
                case Constraint.MaxStepsConstraint max -> {
                    if (graph.nodes().size() > max.maxSteps()) {
                        issues.add(PlanResult.PlanIssue.error(
                            "CONSTRAINT_VIOLATION",
                            String.format("节点数 %d 超过最大步骤限制 %d",
                                graph.nodes().size(), max.maxSteps())
                        ));
                    }
                }
                case Constraint.RequiredToolConstraint required -> {
                    Set<String> usedTools = new HashSet<>();
                    for (TaskNode node : graph.nodes()) {
                        if (node.type() == TaskNodeType.TOOL_CALL) {
                            // 工具名归一化排毒层一致性：与 checkToolAvailability 同口径归一化，
                            // 否则 LLM 把签名 "name(params)" 抄进 description 时 usedTools 含噪声串、与裸名 requiredTools
                            // 恒不命中 → 误报 MISSING_REQUIRED_TOOL ERROR（即便同次校验刚证明该工具可用）。
                            usedTools.add(ToolNames.bareToolName(node.description()));
                        }
                    }
                    for (String tool : required.requiredTools()) {
                        if (!usedTools.contains(tool)) {
                            issues.add(PlanResult.PlanIssue.error(
                                "MISSING_REQUIRED_TOOL",
                                "缺少必需工具: " + tool
                            ));
                        }
                    }
                }
                case Constraint.OutputFormatConstraint ignored -> {
                    // OutputFormat 在 Verify 阶段检查
                }
                case Constraint.ApprovalConstraint ignored -> {
                    // ApprovalConstraint 在执行阶段检查
                }
            }
        }
    }

    // ==================== 占位符卫生检查 ====================

    /**
     * 检查节点是否含未解析/内嵌的 ${} 占位符。
     *
     * 内嵌占位符（如 description 里的"维度3：${dim3}"）无法被 resolveTemplate/resolveInputs 解析，
     * 会被 LLM 原样 echo 进输出。description 完全禁止 ${}；
     * inputs value 允许整值引用 ${nodeId.output}，禁止内嵌。
     */
    private void checkPlaceholderHygiene(TaskGraph graph, List<PlanResult.PlanIssue> issues) {
        for (TaskNode node : graph.nodes()) {
            // description 不该有任何占位符引用（引用应走 inputs 传值）
            if (node.description().contains("${")) {
                issues.add(PlanResult.PlanIssue.error(
                    "UNRESOLVED_PLACEHOLDER_IN_DESCRIPTION",
                    "节点 " + node.id() + " 的 description 含内嵌占位符，引用上游输出应放进 inputs 的整值 value",
                    node.id().value()
                ));
            }
            // inputs value：整值 ${...} 合法（约定引用语法），内嵌禁止
            for (var entry : node.inputs().entrySet()) {
                String v = entry.getValue();
                boolean isWholeRef = v.startsWith("${") && v.endsWith("}");
                if (!isWholeRef && v.contains("${")) {
                    issues.add(PlanResult.PlanIssue.error(
                        "UNRESOLVED_PLACEHOLDER_IN_INPUT",
                        "节点 " + node.id() + " 的 input " + entry.getKey()
                            + " 含内嵌占位符，引用应作为整值 value（如 \"${x.output}\"）",
                        node.id().value()
                    ));
                }
            }
        }
    }
}
