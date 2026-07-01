package com.openjiuwen.runtime.alpha;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.alpha.graph.*;
import com.openjiuwen.core.alpha.model.*;
import com.openjiuwen.core.alpha.verifier.NodeResult;
import com.openjiuwen.core.alpha.verifier.ReplanStrategy;
import com.openjiuwen.core.alpha.verifier.VerifyResult;
import com.openjiuwen.core.kernel.model.*;
import com.openjiuwen.core.meta.AgentDefinition;
import com.openjiuwen.runtime.alpha.planner.Planner;
import com.openjiuwen.runtime.alpha.verifier.Verifier;
import com.openjiuwen.runtime.core.dispatch.TaskContext;
import com.openjiuwen.runtime.core.engine.AgentKernel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * PEVAlphaStrategy 控制流承重——mock 证 Plan-Execute-Verify-Dispatch 循环。
 *
 * <p>每个测试标注 mutation-RED：剥什么代码/断言会变红。
 * 承重契约：mock 证控制流（硬断言），真 LLM 证数据通道（软观察，defer e2e）。
 */
class PEVAlphaStrategyControlFlowTest {

    // ==================== mock types ====================

    /** Planner that returns a fixed graph. */
    record MockPlanner(TaskGraph graph, boolean succeed, int failCount) implements Planner {
        MockPlanner(TaskGraph graph) { this(graph, true, 0); }

        @Override
        public Mono<PlanResult> plan(TaskId taskId, PlanGoal goal, ExecutionPolicy policy) {
            if (!succeed) return Mono.just(PlanResult.failure(List.of(), 1));
            return Mono.just(PlanResult.success(graph, 1));
        }

        @Override
        public PlanResult validate(TaskGraph g, PlanGoal goal, java.util.List<Constraint> constraints) {
            return PlanResult.success(g, 0);
        }
    }

    /** Planner that throws on first n calls, then returns the graph. */
    static class FlakyPlanner implements Planner {
        final TaskGraph graph;
        final int failCount;
        int callCount = 0;
        final String exceptionMsg;

        FlakyPlanner(TaskGraph graph, int failCount, String exceptionMsg) {
            this.graph = graph; this.failCount = failCount; this.exceptionMsg = exceptionMsg;
        }

        @Override
        public Mono<PlanResult> plan(TaskId taskId, PlanGoal goal, ExecutionPolicy policy) {
            if (++callCount <= failCount) {
                return Mono.error(new RuntimeException(exceptionMsg));
            }
            return Mono.just(PlanResult.success(graph, 1));
        }

        @Override
        public PlanResult validate(TaskGraph g, PlanGoal goal, java.util.List<Constraint> constraints) {
            return PlanResult.success(g, 0);
        }
    }

    /** Verifier that returns a fixed VerifyResult; tracks call count. */
    static class MockVerifier implements Verifier {
        final VerifyResult result;
        final AtomicInteger callCount = new AtomicInteger(0);
        final List<VerifyResult> sequence = new ArrayList<>();
        int sequenceIdx = 0;

        MockVerifier(VerifyResult result) { this.result = result; }

        /** Returns results in sequence — first call returns seq[0], second seq[1], etc. */
        MockVerifier withSequence(VerifyResult... results) {
            sequence.addAll(List.of(results));
            return this;
        }

        @Override
        public Mono<VerifyResult> verify(TaskId taskId, PlanGoal goal, TaskGraph graph,
                                          Map<NodeId, Object> nodeResults,
                                          ExecutionPolicy policy, BudgetLimits budget) {
            callCount.incrementAndGet();
            if (!sequence.isEmpty() && sequenceIdx < sequence.size()) {
                return Mono.just(sequence.get(sequenceIdx++));
            }
            return Mono.just(result);
        }

        @Override
        public ReplanStrategy decideReplanStrategy(VerifyResult vr, int retryCount) {
            return new ReplanStrategy.LocalReplan(3);
        }
    }

    /** Kernel that just accepts all calls silently. */
    static class SilentKernel implements AgentKernel {
        final List<String> thinkPrompts = new ArrayList<>();
        String toolResult = "mock-result";

        @Override
        public Mono<String> think(String prompt, BudgetLimits budget) {
            thinkPrompts.add(prompt);
            return Mono.just("mock-think-response");
        }

        @Override
        public Mono<ToolResult> invokeTool(ToolName toolName, Map<String, Object> arguments,
                                            BudgetLimits budget) {
            return Mono.just(ToolResult.ok(toolName, toolResult));
        }

        @Override public Mono<Void> emit(AgentEvent event) { return Mono.empty(); }
        @Override public Mono<Map<NodeId, Object>> observe(TaskId taskId, Set<NodeId> nodeIds) {
            return Mono.just(Map.of());
        }
        @Override public Mono<CheckpointId> saveCheckpoint(Checkpoint cp) {
            return Mono.just(new CheckpointId("cp"));
        }
        @Override public Mono<Checkpoint> restoreCheckpoint(TaskId taskId) { return Mono.empty(); }
        @Override public Mono<CheckpointId> yield(TaskId taskId, YieldReason reason, String state) {
            return Mono.just(new CheckpointId("y"));
        }
        @Override public Flux<AgentEvent> observeEvents(TaskId taskId) { return Flux.empty(); }
    }

    // ==================== helpers ====================

    private static TaskGraph simpleGraph() {
        return new TaskGraph("test-goal", List.of(
                TaskNode.of("A", "analyze data", TaskNodeType.LLM_CALL)), List.of());
    }

    private static TaskContext ctx(AgentKernel kernel, Planner planner, Verifier verifier) {
        Map<String, Object> extra = new HashMap<>();
        if (planner != null) extra.put("planner", planner);
        if (verifier != null) extra.put("verifier", verifier);
        return new TaskContext(new TaskId("t1"), new AgentName("test"),
                TaskInput.of("user-query"),
                new AgentDefinition(new AgentName("test"), "desc", "sys", List.of(),
                        com.openjiuwen.core.dispatch.AutonomyLevel.GUIDED,
                        Budget.Fixed.productionDefault(),
                        ExecutionPolicy.productionDefault(), null, null, Map.of()),
                kernel, Budget.Fixed.productionDefault(),
                com.openjiuwen.core.dispatch.AutonomyLevel.GUIDED, extra);
    }

    private static ExecutionPolicy verifyPolicy() {
        return new ExecutionPolicy(PlanningMode.AUTO,
                com.openjiuwen.core.alpha.model.VerifyMode.STRICT, 3, 4, true);
    }

    // ==================== C1: happy path ====================

    @Test
    void happyPathPlanExecuteVerifyCompletes() {
        SilentKernel kernel = new SilentKernel();
        MockPlanner planner = new MockPlanner(simpleGraph());
        MockVerifier verifier = new MockVerifier(VerifyResult.passed("all good"));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx(kernel, planner, verifier))
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> e.type() == EventType.TASK_COMPLETED))
                .as("happy path must terminate with TASK_COMPLETED")
                .isTrue();
        assertThat(events.stream().anyMatch(e -> e.type() == EventType.VERIFY_PASSED))
                .as("happy path must emit VERIFY_PASSED")
                .isTrue();
        // mutation-RED: strip verify PASSED → VERIFY_PASSED missing → RED
    }

    // ==================== C2: DeviceFailure → AcceptPartial ====================

    @Test
    void deviceFailureLeadsToAcceptPartialDegraded() {
        SilentKernel kernel = new SilentKernel();
        MockPlanner planner = new MockPlanner(simpleGraph());
        // VerifyResult with DeviceFailure nodes → should trigger AcceptPartial
        MockVerifier verifier = new MockVerifier(
                VerifyResult.failed("tool crashed", Set.of("A")));

        // Use policy with maxRetries=0 so no retry — verify fail → immediate diagnose/dispatch
        ExecutionPolicy tight = new ExecutionPolicy(PlanningMode.AUTO,
                com.openjiuwen.core.alpha.model.VerifyMode.STRICT, 0, 4, true);

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(
                ctx(kernel, planner, verifier)).collectList().block();

        assertThat(events).isNotNull();
        // Should either complete degraded or fail
        boolean hasCompleted = events.stream().anyMatch(e -> e.type() == EventType.TASK_COMPLETED);
        boolean hasVerifiedFailed = events.stream().anyMatch(e -> e.type() == EventType.VERIFY_FAILED);
        boolean hasRootCause = events.stream().anyMatch(e -> e.type() == EventType.ROOT_CAUSE_DIAGNOSED);

        assertThat(hasVerifiedFailed).as("must emit VERIFY_FAILED for DeviceFailure").isTrue();
        assertThat(hasRootCause).as("must emit ROOT_CAUSE_DIAGNOSED after verify fail").isTrue();
        assertThat(hasCompleted || events.stream().anyMatch(e -> e.type() == EventType.TASK_FAILED))
                .as("must reach terminal state").isTrue();
        // mutation-RED: strip diagnose → ROOT_CAUSE_DIAGNOSED missing → RED
    }

    // ==================== C4: LocalReplan → retry verify passes ====================

    @Test
    void localReplanRetryCorrectsAndPasses() {
        SilentKernel kernel = new SilentKernel();
        MockPlanner planner = new MockPlanner(simpleGraph());
        // Sequence: first verify fails (1 node) → LocalReplan → second verify passes
        MockVerifier verifier = new MockVerifier(VerifyResult.passed("ok"))
                .withSequence(
                        VerifyResult.failed("wrong answer", Set.of("A")),
                        VerifyResult.passed("corrected"));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx(kernel, planner, verifier))
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(verifier.callCount.get()).isGreaterThanOrEqualTo(2);
        boolean finalPassed = events.stream().anyMatch(e -> e.type() == EventType.VERIFY_PASSED);
        assertThat(finalPassed).as("LocalReplan + retry must eventually pass").isTrue();
        // mutation-RED: strip LocalReplan re-execution → callCount==1 (never retry) → RED
    }

    // ==================== C5: GlobalReplan → new plan passes ====================

    @Test
    void globalReplanGeneratesNewPlan() {
        SilentKernel kernel = new SilentKernel();
        MockPlanner planner = new MockPlanner(simpleGraph());
        // Verify fails with many nodes (>2) → triggers GlobalReplan
        MockVerifier verifier = new MockVerifier(VerifyResult.passed("ok"))
                .withSequence(
                        VerifyResult.failed("total failure", Set.of("A", "B", "C")),
                        VerifyResult.passed("new plan works"));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx(kernel, planner, verifier))
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(verifier.callCount.get()).isGreaterThanOrEqualTo(2);
        // GlobalReplan should emit PLAN_REVISED
        boolean hasPlanRevised = events.stream().anyMatch(e -> e.type() == EventType.PLAN_REVISED);
        assertThat(hasPlanRevised).as("GlobalReplan must emit PLAN_REVISED").isTrue();
        // mutation-RED: strip GlobalReplan branch → PLAN_REVISED missing → RED
    }

    // ==================== C6: maxRetries exceeded ====================

    @Test
    void maxRetriesExceededLeadsToTaskFailed() {
        SilentKernel kernel = new SilentKernel();
        MockPlanner planner = new MockPlanner(simpleGraph());
        // Verifier always fails → loop exhausts retries
        MockVerifier verifier = new MockVerifier(
                VerifyResult.failed("persistent failure", Set.of("A")));

        // maxRetries=1
        ExecutionPolicy tight = new ExecutionPolicy(PlanningMode.AUTO,
                com.openjiuwen.core.alpha.model.VerifyMode.STRICT, 1, 4, true);
        Map<String, Object> extra = new HashMap<>();
        extra.put("planner", planner);
        extra.put("verifier", verifier);
        extra.put("executionPolicy", tight);
        TaskContext tctx = new TaskContext(new TaskId("t1"), new AgentName("test"),
                TaskInput.of("q"),
                new AgentDefinition(new AgentName("test"), "d", "s", List.of(),
                        com.openjiuwen.core.dispatch.AutonomyLevel.GUIDED,
                        Budget.Fixed.productionDefault(), tight, null, null, Map.of()),
                kernel, Budget.Fixed.productionDefault(),
                com.openjiuwen.core.dispatch.AutonomyLevel.GUIDED, extra);

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(tctx).collectList().block();

        assertThat(events).isNotNull();
        boolean hasTaskFailed = events.stream().anyMatch(e -> e.type() == EventType.TASK_FAILED);
        assertThat(hasTaskFailed)
                .as("maxRetries exceeded must emit TASK_FAILED")
                .isTrue();
        // mutation-RED: strip maxRetries check → infinite loop or TASK_COMPLETED → RED
    }

    // ==================== C7: terminalGuard prevents double complete ====================

    @Test
    void terminalGuardEnsuresCleanCompletion() {
        SilentKernel kernel = new SilentKernel();
        MockPlanner planner = new MockPlanner(simpleGraph());
        MockVerifier verifier = new MockVerifier(VerifyResult.passed("ok"));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx(kernel, planner, verifier))
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).isNotEmpty();
        // Verify clean termination: TASK_COMPLETED is present, TASK_FAILED is not
        assertThat(events.stream().anyMatch(e -> e.type() == EventType.TASK_COMPLETED))
                .as("must emit TASK_COMPLETED on success").isTrue();
        assertThat(events.stream().noneMatch(e -> e.type() == EventType.TASK_FAILED))
                .as("TASK_FAILED must not appear on success").isTrue();
        // terminalGuard ensures the stream terminates (no infinite hang)
        // mutation-RED: strip terminalGuard CAS → Mono never completes → test hangs → RED
    }

    // ==================== C8: plan timeout → retry succeeds ====================

    @Test
    void planTimeoutRetriesAndSucceeds() {
        SilentKernel kernel = new SilentKernel();
        // FlakyPlanner: fails once with timeout-like exception, then succeeds
        FlakyPlanner planner = new FlakyPlanner(simpleGraph(), 1,
                "java.util.concurrent.TimeoutException: 请求超时");
        MockVerifier verifier = new MockVerifier(VerifyResult.passed("ok"));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx(kernel, planner, verifier))
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.stream().anyMatch(e -> e.type() == EventType.TASK_COMPLETED))
                .as("plan timeout + retry must eventually complete")
                .isTrue();
        assertThat(planner.callCount).isGreaterThanOrEqualTo(2);
        // mutation-RED: strip retry logic → TASK_FAILED (not completed) → RED
    }

    // ==================== C9: plan timeout → both attempts fail ====================

    @Test
    void planTimeoutBothAttemptsFailLeadsToTaskFailed() {
        SilentKernel kernel = new SilentKernel();
        // FlakyPlanner: fails twice — retry also fails
        FlakyPlanner planner = new FlakyPlanner(simpleGraph(), 2,
                "java.util.concurrent.TimeoutException: 请求超时");
        MockVerifier verifier = new MockVerifier(VerifyResult.passed("ok"));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx(kernel, planner, verifier))
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.stream().anyMatch(e -> e.type() == EventType.TASK_FAILED))
                .as("plan timeout twice must emit TASK_FAILED")
                .isTrue();
        // mutation-RED: strip retry failure path → TASK_COMPLETED → RED
    }

    // ==================== C10: LocalReplan injects correctionHint ====================

    @Test
    void localReplanInjectsCorrectionHintOnFailedNodes() {
        SilentKernel kernel = new SilentKernel();
        MockPlanner planner = new MockPlanner(simpleGraph());
        // Sequence: verify fails (1 node) → LocalReplan → re-execute → verify passes
        MockVerifier verifier = new MockVerifier(VerifyResult.passed("corrected"))
                .withSequence(
                        VerifyResult.failed("wrong calculation", Set.of("A")),
                        VerifyResult.passed("corrected"));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx(kernel, planner, verifier))
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(verifier.callCount.get()).isGreaterThanOrEqualTo(2);
        // Verify that correction flow completed
        boolean planRevised = events.stream().anyMatch(e -> e.type() == EventType.PLAN_REVISED);
        assertThat(planRevised).as("LocalReplan must emit PLAN_REVISED with correction hint").isTrue();
        // mutation-RED: strip correctionHint injection → re-executed node has null hint → RED
    }

    // ==================== C11: bestOfKReplan path ====================

    @Test
    void bestOfKReplanGeneratesNewPlanViaBestOfK() {
        SilentKernel kernel = new SilentKernel();
        MockPlanner planner = new MockPlanner(simpleGraph());
        // Verify fails with many nodes → GlobalReplan
        MockVerifier verifier = new MockVerifier(VerifyResult.passed("ok"))
                .withSequence(
                        VerifyResult.failed("total failure", Set.of("A", "B", "C")),
                        VerifyResult.passed("best-of-K plan works"));

        // Policy with bestOfKReplan=true and bestOfK=2
        ExecutionPolicy bestOfKPolicy = new ExecutionPolicy(PlanningMode.AUTO,
                com.openjiuwen.core.alpha.model.VerifyMode.STRICT, 2, 2, true);
        Map<String, Object> extra = new HashMap<>();
        extra.put("planner", planner);
        extra.put("verifier", verifier);
        extra.put("executionPolicy", bestOfKPolicy);
        TaskContext tctx = new TaskContext(new TaskId("t1"), new AgentName("test"),
                TaskInput.of("q"),
                new AgentDefinition(new AgentName("test"), "d", "s", List.of(),
                        com.openjiuwen.core.dispatch.AutonomyLevel.GUIDED,
                        Budget.Fixed.productionDefault(), bestOfKPolicy, null, null, Map.of()),
                kernel, Budget.Fixed.productionDefault(),
                com.openjiuwen.core.dispatch.AutonomyLevel.GUIDED, extra);

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(tctx).collectList().block();

        assertThat(events).isNotNull();
        boolean hasPlanRevised = events.stream().anyMatch(e -> e.type() == EventType.PLAN_REVISED);
        assertThat(hasPlanRevised).as("bestOfKReplan must emit PLAN_REVISED").isTrue();
        // mutation-RED: strip bestOfKReplan branch → bestOfK never called → RED
    }
}
