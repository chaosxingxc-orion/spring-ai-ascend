package com.openjiuwen.runtime.alpha.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.alpha.model.ExecutionPolicy;
import com.openjiuwen.core.alpha.model.PlanGoal;
import com.openjiuwen.core.alpha.model.PlanResult;
import com.openjiuwen.core.kernel.model.AgentEvent;
import com.openjiuwen.core.kernel.model.BudgetLimits;
import com.openjiuwen.core.kernel.model.Checkpoint;
import com.openjiuwen.core.kernel.model.CheckpointId;
import com.openjiuwen.core.kernel.model.NodeId;
import com.openjiuwen.core.kernel.model.TaskId;
import com.openjiuwen.core.kernel.model.ToolName;
import com.openjiuwen.core.kernel.model.ToolResult;
import com.openjiuwen.core.kernel.model.YieldReason;
import com.openjiuwen.runtime.core.engine.AgentKernel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * DefaultPlanner 自纠错承重——plan V2 轮6 PEV P 内核：LLM 规划失败 → 自纠错重试 → 降级。
 *
 * <p>承重 IFF：LLM 返 invalid JSON → generateWithLLM 首试失败 → selfCorrect 重试 maxCorrectionRounds 次
 * → 仍失败 → PlanResult.failure（isValid==false + planningAttempts==1+maxCorrectionRounds）。
 *
 * <p>mutation-RED：剥 selfCorrect（首试 invalid 直接返，不重试）→ planningAttempts==1（非 1+maxRounds）→ RED。
 *
 * <p>诚实边界（铁律①）：mock kernel 硬断控制流（invalid JSON 注入），真 LLM 数据通道（kernel.think 真返
 * TaskGraph JSON）软观察 defer 轮9。
 */
class DefaultPlannerTest {

    private static final int MAX_CORRECTION_ROUNDS = 2;

    @Test
    void invalidJsonRetriesThenDegradesToFailure() {
        // mock kernel 永返 invalid JSON → 首试 + 2 轮自纠错全失败 → 降级 failure
        InvalidJsonKernel kernel = new InvalidJsonKernel();
        DefaultPlanner planner = new DefaultPlanner(kernel, new PlanValidator(),
                new ObjectMapper(), MAX_CORRECTION_ROUNDS);

        PlanResult result = planner.plan(new TaskId("t1"), PlanGoal.of("test-goal"),
                ExecutionPolicy.productionDefault()).block();

        assertThat(result).isNotNull();
        assertThat(result.isValid())
                .as("invalid JSON 永不收敛 → 降级 failure（IFF：全失败 ⟹ isValid==false）")
                .isFalse();
        assertThat(result.planningAttempts())
                .as("首试 + %d 轮自纠错 = %d 次", MAX_CORRECTION_ROUNDS, 1 + MAX_CORRECTION_ROUNDS)
                .isEqualTo(1 + MAX_CORRECTION_ROUNDS);
        // mutation-RED：剥 selfCorrect 重试 → planningAttempts==1（首试直接返）→ 此断言 RED
    }

    @Test
    void validJsonReturnsSuccessWithoutCorrection() {
        // mock kernel 返合法 TaskGraph JSON → 首试成功 → 无自纠错（planningAttempts==1）
        ValidJsonKernel kernel = new ValidJsonKernel();
        DefaultPlanner planner = new DefaultPlanner(kernel, new PlanValidator(),
                new ObjectMapper(), MAX_CORRECTION_ROUNDS);

        PlanResult result = planner.plan(new TaskId("t1"), PlanGoal.of("test-goal"),
                ExecutionPolicy.productionDefault()).block();

        assertThat(result).isNotNull();
        assertThat(result.isValid()).isTrue();
        assertThat(result.planningAttempts()).isEqualTo(1);
    }

    /** mock kernel 永返非法 JSON（非 TaskGraph）——驱动自纠错重试全失败。 */
    static final class InvalidJsonKernel implements AgentKernel {
        @Override
        public Mono<String> think(String prompt, BudgetLimits budget) {
            return Mono.just("this is not valid json {{{");
        }

        @Override
        public Mono<Void> emit(AgentEvent event) {
            return Mono.empty();
        }

        @Override
        public List<com.openjiuwen.core.meta.AgentDefinition.ToolDefinition> getToolDefinitions() {
            return List.of();
        }

        @Override
        public Mono<ToolResult> invokeTool(ToolName toolName, Map<String, Object> arguments, BudgetLimits budget) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Map<NodeId, Object>> observe(TaskId taskId, Set<NodeId> nodeIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CheckpointId> saveCheckpoint(Checkpoint checkpoint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Checkpoint> restoreCheckpoint(TaskId taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CheckpointId> yield(TaskId taskId, YieldReason reason, String currentState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<AgentEvent> observeEvents(TaskId taskId) {
            throw new UnsupportedOperationException();
        }
    }

    /** mock kernel 返合法单节点 TaskGraph JSON——驱动首试成功。 */
    static final class ValidJsonKernel implements AgentKernel {
        @Override
        public Mono<String> think(String prompt, BudgetLimits budget) {
            return Mono.just("{\"goal\":\"test-goal\",\"nodes\":[{\"id\":\"A\",\"description\":\"do A\",\"type\":\"LLM_CALL\"}],\"edges\":[]}");
        }

        @Override
        public Mono<Void> emit(AgentEvent event) {
            return Mono.empty();
        }

        @Override
        public List<com.openjiuwen.core.meta.AgentDefinition.ToolDefinition> getToolDefinitions() {
            return List.of();
        }

        @Override
        public Mono<ToolResult> invokeTool(ToolName toolName, Map<String, Object> arguments, BudgetLimits budget) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Map<NodeId, Object>> observe(TaskId taskId, Set<NodeId> nodeIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CheckpointId> saveCheckpoint(Checkpoint checkpoint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Checkpoint> restoreCheckpoint(TaskId taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CheckpointId> yield(TaskId taskId, YieldReason reason, String currentState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<AgentEvent> observeEvents(TaskId taskId) {
            throw new UnsupportedOperationException();
        }
    }
}
