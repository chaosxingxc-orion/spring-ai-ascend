package com.openjiuwen.runtime.alpha.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.alpha.graph.TaskEdge;
import com.openjiuwen.core.alpha.graph.TaskGraph;
import com.openjiuwen.core.alpha.graph.TaskNode;
import com.openjiuwen.core.alpha.graph.TaskNodeType;
import com.openjiuwen.core.alpha.model.Constraint;
import com.openjiuwen.core.alpha.model.PlanGoal;
import com.openjiuwen.core.alpha.model.PlanResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PlanValidator 承重测试——plan V2 轮6 主承重：规划 IFF（剥校验→环图漏过→RED）。
 *
 * <p>承重核心：checkCycles Tarjan SCC 环检测。剥 checkCycles 调用 → 环图 PlanResult.success（漏过）→
 * cycleDetected 断言 RED（mutation-RED 证非恒真）。
 *
 * <p>对照承重：悬空边 / 自环 / 缺必需工具 / 占位符 / 超 MaxSteps 各校验独立 IFF。
 */
class PlanValidatorTest {

    private static TaskGraph graph(List<TaskNode> nodes, List<TaskEdge> edges) {
        return new TaskGraph("test-goal", nodes, edges);
    }

    private static TaskNode node(String id) {
        return TaskNode.of(id, "do " + id, TaskNodeType.LLM_CALL);
    }

    private static TaskEdge edge(String from, String to) {
        return TaskEdge.of(from, to);
    }

    private PlanResult validate(TaskGraph g) {
        return new PlanValidator().validate(g, PlanGoal.of("test-goal"), List.of());
    }

    // ==================== Tarjan SCC 环检测（主承重） ====================

    @Test
    void cycleDetectedReturnsFailure() {
        // A→B, B→A：SCC={A,B} size=2>1 → CYCLE_DETECTED ERROR
        TaskGraph cycle = graph(List.of(node("A"), node("B")),
                List.of(edge("A", "B"), edge("B", "A")));

        PlanResult result = validate(cycle);

        assertThat(result.isValid()).isFalse();
        assertThat(result.issues())
                .anyMatch(i -> "CYCLE_DETECTED".equals(i.code())
                        && i.severity() == PlanResult.IssueSeverity.ERROR);
    }

    @Test
    void selfLoopReturnsFailure() {
        TaskGraph selfLoop = graph(List.of(node("A")),
                List.of(edge("A", "A")));

        PlanResult result = validate(selfLoop);

        assertThat(result.isValid()).isFalse();
        assertThat(result.issues())
                .anyMatch(i -> "SELF_LOOP".equals(i.code()));
    }

    @Test
    void validDagReturnsSuccess() {
        // A→B→C 无环
        TaskGraph dag = graph(List.of(node("A"), node("B"), node("C")),
                List.of(edge("A", "B"), edge("B", "C")));

        PlanResult result = validate(dag);

        assertThat(result.isValid()).isTrue();
        assertThat(result.issues())
                .noneMatch(i -> i.severity() == PlanResult.IssueSeverity.ERROR);
    }

    // ==================== 其他校验 IFF ====================

    @Test
    void invalidEdgeReturnsError() {
        // 边引用不存在的节点 C
        TaskGraph dangling = graph(List.of(node("A")),
                List.of(edge("A", "C")));

        PlanResult result = validate(dangling);

        assertThat(result.isValid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> "INVALID_EDGE".equals(i.code()));
    }

    @Test
    void unresolvedPlaceholderInDescriptionReturnsError() {
        TaskNode bad = TaskNode.of("A", "do ${x.output} thing", TaskNodeType.LLM_CALL);
        TaskGraph g = graph(List.of(bad), List.of());

        PlanResult result = validate(g);

        assertThat(result.isValid()).isFalse();
        assertThat(result.issues())
                .anyMatch(i -> "UNRESOLVED_PLACEHOLDER_IN_DESCRIPTION".equals(i.code()));
    }

    @Test
    void maxStepsExceededReturnsError() {
        TaskGraph g = graph(List.of(node("A"), node("B"), node("C")), List.of(edge("A", "B")));
        Constraint maxSteps = new Constraint.MaxStepsConstraint(2);

        PlanResult result = new PlanValidator().validate(g, PlanGoal.of("test"), List.of(maxSteps));

        assertThat(result.isValid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> "CONSTRAINT_VIOLATION".equals(i.code()));
    }

    @Test
    void missingRequiredToolReturnsError() {
        TaskGraph g = graph(List.of(node("A")), List.of());
        Constraint required = new Constraint.RequiredToolConstraint(
                "c1", "need tool X", Set.of("toolX"));

        PlanResult result = new PlanValidator().validate(g, PlanGoal.of("test"), List.of(required));

        assertThat(result.isValid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> "MISSING_REQUIRED_TOOL".equals(i.code()));
    }

    @Test
    void wholeRefInputPlaceholderAllowed() {
        // inputs value 整值 ${x.output} 合法（引用语法），不报错
        TaskNode ok = TaskNode.of("A", "do A", TaskNodeType.LLM_CALL,
                Map.of("ref", "${x.output}"));
        TaskGraph g = graph(List.of(ok), List.of());

        PlanResult result = validate(g);

        assertThat(result.issues())
                .noneMatch(i -> "UNRESOLVED_PLACEHOLDER_IN_INPUT".equals(i.code()));
    }
}
