package com.openjiuwen.runtime.alpha.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.alpha.graph.TaskGraph;
import com.openjiuwen.core.alpha.graph.TaskNode;
import com.openjiuwen.core.alpha.graph.TaskNodeType;
import com.openjiuwen.core.alpha.model.PlanGoal;
import com.openjiuwen.core.alpha.verifier.VerifyResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DefaultPlanner fitness 承重——plan V2 轮8 主承重：best-of-K fitness IFF（确定性选优）。
 *
 * <p>GEPA-lite fitness = 0.45*criteriaCoverage + 0.35*toolCallRatio + 0.20*failedHit（纯函数，零 LLM-judge）。
 * selectBest 选 fitness argmax。
 *
 * <p>承重 IFF：候选 A（criteriaCoverage 高）fitness > 候选 B（criteriaCoverage 低）。剥 0.45*cov（改 0）
 * → A 优势消失 → A<B → 断言 A>B RED（mutation-RED 证 criteriaCoverage 真承重）。
 *
 * <p>诚实边界：fitness 纯函数确定性（执行前评估，零 LLM/零 execute）；planBestOfK 真 LLM 采样（K 次
 * generateWithLLM）数据通道 defer 轮9。
 */
class DefaultPlannerFitnessTest {

    private static final VerifyResult NO_FAILED = VerifyResult.passed("");

    private static TaskGraph graph(TaskNode... nodes) {
        return new TaskGraph("test", List.of(nodes), List.of());
    }

    @Test
    void criteriaCoverageYieldsHigherFitness() {
        // successCriteria = "分析销售数据"
        PlanGoal goal = PlanGoal.of("test", List.of("分析销售数据"));
        // A: description 含 "分析销售数据" → criteriaCoverage 高；LLM_CALL → toolRatio 0
        TaskGraph candidateA = graph(TaskNode.of("A", "分析销售数据的报表", TaskNodeType.LLM_CALL));
        // B: description 不含 → criteriaCoverage 低；TOOL_CALL → toolRatio 1
        TaskGraph candidateB = graph(TaskNode.of("B", "do task", TaskNodeType.TOOL_CALL));

        double fitnessA = DefaultPlanner.fitness(candidateA, goal, NO_FAILED);
        double fitnessB = DefaultPlanner.fitness(candidateB, goal, NO_FAILED);

        // 0.45*cov 差异主导：A(criteriaCoverage>0) > B(criteriaCoverage=0)
        assertThat(fitnessA)
                .as("criteriaCoverage 高的候选 fitness 应更高（IFF：0.45*cov 真承重）")
                .isGreaterThan(fitnessB);
    }

    @Test
    void toolCallRatioContributes() {
        PlanGoal goal = PlanGoal.of("test", List.of());
        // 两候选 criteriaCoverage 都 0（successCriteria 空），区分在 toolRatio
        TaskGraph llmOnly = graph(TaskNode.of("A", "think", TaskNodeType.LLM_CALL));
        TaskGraph toolOnly = graph(TaskNode.of("B", "act", TaskNodeType.TOOL_CALL));

        double fitnessLlm = DefaultPlanner.fitness(llmOnly, goal, NO_FAILED);
        double fitnessTool = DefaultPlanner.fitness(toolOnly, goal, NO_FAILED);

        // 0.35*toolRatio：TOOL_CALL 候选 > LLM_CALL 候选
        assertThat(fitnessTool)
                .as("TOOL_CALL 比率贡献 fitness（0.35*toolRatio）")
                .isGreaterThan(fitnessLlm);
    }

    @Test
    void fitnessIsDeterministicAndBounded() {
        PlanGoal goal = PlanGoal.of("test", List.of("criteria"));
        TaskGraph g = graph(TaskNode.of("A", "criteria here", TaskNodeType.LLM_CALL));

        double f1 = DefaultPlanner.fitness(g, goal, NO_FAILED);
        double f2 = DefaultPlanner.fitness(g, goal, NO_FAILED);

        assertThat(f1).isEqualTo(f2); // 确定性
        assertThat(f1).isBetween(0.0, 1.0); // fitness 在 [0,1] 区间
        // mutation-RED：改 0.45→0（剥 criteriaCoverage 权重）→ criteriaCoverageYieldsHigherFitness 的
        // A 优势消失 → A<B → 断言 A>B RED（证 0.45*cov 真承重）
    }
}
