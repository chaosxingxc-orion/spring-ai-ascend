package com.huawei.ascend.engine.planner.spi;

import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlannerSpiCarrierImmutabilityTest {

    private static final StepBudget STEP_BUDGET = new StepBudget(Duration.ofSeconds(1), 1.0);
    private static final PlanningBudget PLANNING_BUDGET =
            new PlanningBudget(3, Duration.ofSeconds(10), 10.0);

    @Test
    void planStepCopiesInputs() {
        Map<String, Object> inputs = new HashMap<>(Map.of("query", "hello"));

        PlanStep step = new PlanStep(
                "step",
                "Search",
                "search",
                inputs,
                Optional.empty(),
                Optional.empty(),
                STEP_BUDGET);

        inputs.put("query", "mutated");

        assertThat(step.inputs()).containsEntry("query", "hello");
        assertThatThrownBy(() -> step.inputs().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void planCopiesListsMapsAndNestedDependencyLists() {
        PlanStep start = new PlanStep(
                "start",
                "Start",
                "prepare",
                Map.of("query", "hello"),
                Optional.empty(),
                Optional.empty(),
                STEP_BUDGET);
        PlanStep step = new PlanStep(
                "step",
                "Search",
                "search",
                Map.of("query", "hello"),
                Optional.empty(),
                Optional.empty(),
                STEP_BUDGET);
        List<PlanStep> steps = new ArrayList<>(List.of(start, step));
        List<String> prerequisites = new ArrayList<>(List.of("start"));
        Map<String, List<String>> dependencies = new HashMap<>(Map.of("step", prerequisites));
        List<BranchPoint> branches = new ArrayList<>(List.of(
                new BranchPoint("step", "ok", "done", "retry")));
        List<LoopAnnotation> loops = new ArrayList<>(List.of(
                new LoopAnnotation("step", "until ok", 3)));
        Map<String, Object> metadata = new HashMap<>(Map.of("planner", "dag"));

        Plan plan = new Plan("plan", steps, dependencies, branches, loops, metadata);

        steps.clear();
        prerequisites.add("mutated");
        dependencies.put("other", List.of("x"));
        branches.clear();
        loops.clear();
        metadata.put("planner", "mutated");

        assertThat(plan.steps()).containsExactly(start, step);
        assertThat(plan.dependencies()).containsEntry("step", List.of("start"));
        assertThat(plan.branches()).hasSize(1);
        assertThat(plan.loops()).hasSize(1);
        assertThat(plan.metadata()).containsEntry("planner", "dag");
        assertThatThrownBy(() -> plan.dependencies().get("step").add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void planningRequestAndResultsCopyMutableComponents() {
        Map<String, Object> context = new HashMap<>(Map.of("session", "s1"));
        List<String> skills = new ArrayList<>(List.of("search"));
        List<String> memories = new ArrayList<>(List.of("m1"));
        List<String> alternatives = new ArrayList<>(List.of("linear"));
        List<String> unmet = new ArrayList<>(List.of("budget"));

        PlanningRequest request = new PlanningRequest(
                "tenant",
                "goal",
                context,
                skills,
                memories,
                PLANNING_BUDGET,
                PlanningStrategy.DAG);
        PlanningRationale rationale = new PlanningRationale("trace", alternatives);
        PlanningResult.PlanningInfeasible infeasible =
                new PlanningResult.PlanningInfeasible("no_plan", unmet);

        context.put("session", "mutated");
        skills.add("shell");
        memories.add("m2");
        alternatives.add("react");
        unmet.add("memory");

        assertThat(request.context()).containsEntry("session", "s1");
        assertThat(request.availableSkillKeys()).containsExactly("search");
        assertThat(request.availableMemoryRefs()).containsExactly("m1");
        assertThat(rationale.consideredAlternatives()).containsExactly("linear");
        assertThat(infeasible.unmetConstraints()).containsExactly("budget");
        assertThatThrownBy(() -> request.availableSkillKeys().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void executorDefinitionsCopyMaps() {
        ExecutorDefinition.NodeFunction node = (ctx, payload) -> payload;
        Map<String, ExecutorDefinition.NodeFunction> nodes = new HashMap<>(Map.of("start", node));
        Map<String, String> edges = new HashMap<>(Map.of("start", "end"));
        Map<String, Object> initialContext = new HashMap<>(Map.of("trace", "t1"));

        ExecutorDefinition.GraphDefinition graph =
                new ExecutorDefinition.GraphDefinition(nodes, edges, "start");
        ExecutorDefinition.AgentLoopDefinition loop =
                new ExecutorDefinition.AgentLoopDefinition((ctx, payload, iteration) ->
                        ExecutorDefinition.ReasoningResult.done(payload), 3, initialContext);

        nodes.clear();
        edges.put("start", "mutated");
        initialContext.put("trace", "mutated");

        assertThat(graph.nodes()).containsEntry("start", node);
        assertThat(graph.edges()).containsEntry("start", "end");
        assertThat(loop.initialContext()).containsEntry("trace", "t1");
        assertThatThrownBy(() -> graph.edges().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void planRejectsDuplicateUnknownAndCyclicStepDependencies() {
        PlanStep start = new PlanStep(
                "start",
                "Start",
                "search",
                Map.of(),
                Optional.empty(),
                Optional.empty(),
                STEP_BUDGET);
        PlanStep finish = new PlanStep(
                "finish",
                "Finish",
                "summarise",
                Map.of(),
                Optional.empty(),
                Optional.empty(),
                STEP_BUDGET);

        assertThatThrownBy(() -> new Plan(
                "plan",
                List.of(start, start),
                Map.of(),
                List.of(),
                List.of(),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> new Plan(
                "plan",
                List.of(start),
                Map.of("finish", List.of("start")),
                List.of(),
                List.of(),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
        assertThatThrownBy(() -> new Plan(
                "plan",
                List.of(start, finish),
                Map.of("start", List.of("finish"), "finish", List.of("start")),
                List.of(),
                List.of(),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void budgetsRejectInvalidCostUnits() {
        assertThatThrownBy(() -> new StepBudget(Duration.ofSeconds(1), -1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxCostUnits");
        assertThatThrownBy(() -> new PlanningBudget(1, Duration.ofSeconds(1), Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxCostUnits");
    }
}
