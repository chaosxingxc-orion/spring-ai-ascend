package com.openjiuwen.runtime.alpha;

import com.openjiuwen.core.alpha.event.AlphaEvent;
import com.openjiuwen.core.alpha.executor.SuperstepResult;
import com.openjiuwen.core.alpha.graph.*;
import com.openjiuwen.core.alpha.model.*;
import com.openjiuwen.core.alpha.verifier.NodeResult;
import com.openjiuwen.core.alpha.verifier.ReplanAction;
import com.openjiuwen.core.alpha.verifier.RootCause;
import com.openjiuwen.core.alpha.verifier.VerifyResult;
import com.openjiuwen.core.kernel.model.*;
import com.openjiuwen.core.meta.AgentDefinition;
import com.openjiuwen.runtime.alpha.executor.DefaultPregelExecutor;
import com.openjiuwen.runtime.alpha.executor.PregelExecutor;
import com.openjiuwen.runtime.alpha.planner.DefaultPlanner;
import com.openjiuwen.runtime.alpha.planner.Planner;
import com.openjiuwen.runtime.alpha.util.ToolNames;
import com.openjiuwen.runtime.alpha.verifier.DefaultVerifier;
import com.openjiuwen.runtime.alpha.verifier.Verifier;
import com.openjiuwen.runtime.beta.selfheal.RootCauseDiagnoser;
import com.openjiuwen.runtime.core.dispatch.ExecutionStrategy;
import com.openjiuwen.runtime.core.dispatch.TaskContext;
import com.openjiuwen.runtime.core.engine.AgentKernel;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PEV Alpha 策略——Plan-Execute-Verify 显式控制引擎（AAC 嫁接版）。
 *
 * <p>三阶段执行模型：
 * <ol>
 *   <li><b>Plan</b>：LLM 生成 TaskGraph → PlanValidator 校验 → 自纠错循环</li>
 *   <li><b>Execute</b>：PregelExecutor BSP 超步执行 → ErrorPolicy 处理失败</li>
 *   <li><b>Verify → Diagnose → Dispatch</b>：Verifier 验证 → RootCauseDiagnoser 诊断根因 →
 *       ReplanAction dispatch（AAC 核心 ~30 行 sealed switch）</li>
 * </ol>
 *
 * <p>AAC 适配（vs openjiuwen-java AlphaStrategy）：
 * <ul>
 *   <li>dispatch 使用 {@link ReplanAction}（携带执行数据：failedNodes + feedback）而非
 *       ReplanStrategy（verifier 推荐层，只携带策略参数）</li>
 *   <li>diagnoseRootCause 委托给 {@link RootCauseDiagnoser#diagnose}（共享纯函数）</li>
 *   <li>RootCause → ReplanAction 映射委托给 {@link RootCauseDiagnoser#toReplanAction}</li>
 *   <li>保留 terminalGuard、shouldDowngradeGlobalReplan、correctionHint 注入</li>
 * </ul>
 *
 * <p>移植自 openjiuwen-java 2.0 AlphaStrategy（720 行）。
 */
public class PEVAlphaStrategy implements ExecutionStrategy {

    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration LLM_TIMEOUT_PLAN_RETRY = Duration.ofSeconds(360);

    @Override
    public String name() {
        return "pev-alpha";
    }

    @Override
    public Flux<AgentEvent> execute(TaskContext context) {
        return Flux.<AgentEvent>create(sink -> {
            PregelExecutor executor = resolveExecutor(context);
            AtomicBoolean terminalGuard = new AtomicBoolean(false);
            try {
                TaskId taskId = context.taskId();
                AgentKernel kernel = context.kernel();
                AtomicReference<BudgetLimits> budgetRef = new AtomicReference<>(
                    context.currentBudgetLimits());
                ExecutionPolicy policy = resolveExecutionPolicy(context);

                Planner planner = resolvePlanner(context, kernel);
                Verifier verifier = resolveVerifier(context, kernel);

                PlanGoal goal = buildPlanGoal(context);

                // ==================== Plan ====================
                sink.next(AgentEvent.of(taskId, EventType.TASK_STARTED, "开始规划"));

                PlanResult planResult;
                try {
                    planResult = planner.plan(taskId, goal, policy)
                        .timeout(LLM_TIMEOUT).block();
                } catch (Exception planEx) {
                    if (isTimeout(planEx)) {
                        sink.next(toAlphaEvent(taskId, new AlphaEvent.PlanRevised(taskId, now(), null,
                            Set.of(), "规划超时（" + LLM_TIMEOUT.getSeconds()
                            + "s），以 " + LLM_TIMEOUT_PLAN_RETRY.getSeconds() + "s 超时重试")));
                        try {
                            planResult = planner.plan(taskId, goal, policy)
                                .timeout(LLM_TIMEOUT_PLAN_RETRY).block();
                        } catch (Exception retryEx) {
                            sink.next(AgentEvent.of(taskId, EventType.TASK_FAILED,
                                "规划超时重试失败：" + retryEx.getMessage()));
                            sink.complete();
                            closeQuietly(executor);
                            return;
                        }
                    } else {
                        sink.next(AgentEvent.of(taskId, EventType.TASK_FAILED,
                            "规划异常：" + planEx.getMessage()));
                        sink.complete();
                        closeQuietly(executor);
                        return;
                    }
                }

                if (planResult == null || !planResult.isValid() || planResult.graph() == null) {
                    String codes = planResult != null
                        ? planResult.issues().stream().map(PlanResult.PlanIssue::code).toList().toString()
                        : "[null-result]";
                    int attempts = planResult != null ? planResult.planningAttempts() : 0;
                    sink.next(AgentEvent.of(taskId, EventType.TASK_FAILED,
                        "规划失败 attempts=" + attempts + " codes=" + codes));
                    sink.complete();
                    closeQuietly(executor);
                    return;
                }

                TaskGraph graph = planResult.graph();
                sink.next(toAlphaEvent(taskId, new AlphaEvent.PlanGenerated(taskId, now(), graph)));
                saveCheckpoint(kernel, taskId, "PLANNING", 0, graph);

                if (!planResult.issues().isEmpty()) {
                    for (PlanResult.PlanIssue issue : planResult.issues()) {
                        sink.next(toAlphaEvent(taskId, new AlphaEvent.ConstraintViolated(
                            taskId, now(), new Constraint.OutputFormatConstraint(
                                "plan-warning", issue.message(), "warning"))));
                    }
                }

                // SEMI_AUTO: yield for tool-call nodes
                if (policy.planningMode() == PlanningMode.SEMI_AUTO) {
                    for (TaskNode node : graph.nodes()) {
                        if (node.type() == TaskNodeType.TOOL_CALL) {
                            sink.next(toAlphaEvent(taskId, new AlphaEvent.ApprovalRequired(
                                taskId, now(), ApprovalGate.pending(
                                    node.id().value(),
                                    new ToolName(ToolNames.bareToolName(node.description())),
                                    "SEMIAUTO: 工具调用需确认", Map.of()))));
                        }
                    }
                }

                // ==================== Execute ====================
                Map<NodeId, Object> completedResults = new ConcurrentHashMap<>();
                Set<NodeId> executorFailedNodes = ConcurrentHashMap.newKeySet();
                Set<NodeId> executorSkippedNodes = ConcurrentHashMap.newKeySet();
                AtomicBoolean verifyExceeded = new AtomicBoolean(false);
                AtomicBoolean verifyPassed = new AtomicBoolean(false);

                Disposable subscription = executor.execute(taskId, graph, policy, budgetRef.get())
                    .doOnNext(superstep -> {
                        int nodesThisStep = superstep.nodeResults().size();
                        if (nodesThisStep > 0) {
                            BudgetLimits current = budgetRef.get();
                            budgetRef.set(current.recordLLMCall(nodesThisStep * 128));
                        }
                        completedResults.putAll(superstep.nodeResults());
                        executorFailedNodes.addAll(superstep.failedNodes());
                        executorSkippedNodes.addAll(superstep.skippedNodes());

                        for (var entry : superstep.nodeResults().entrySet()) {
                            if (!superstep.failedNodes().contains(entry.getKey())) {
                                kernel.emit(toAlphaEvent(taskId, new AlphaEvent.NodeCompleted(
                                    taskId, now(), entry.getKey(), entry.getValue())))
                                    .subscribe(r -> {}, e -> {});
                            }
                        }
                        for (NodeId failedId : superstep.failedNodes()) {
                            Object raw = superstep.nodeResults().get(failedId);
                            String error = raw instanceof NodeResult.DeviceFailure df
                                ? df.error()
                                : (raw != null ? String.valueOf(raw) : "执行失败");
                            kernel.emit(toAlphaEvent(taskId, new AlphaEvent.NodeFailed(
                                taskId, now(), failedId, error)))
                                .subscribe(r -> {}, e -> {});
                        }
                        sink.next(toAlphaEvent(taskId, new AlphaEvent.LayerCompleted(
                            taskId, now(), superstep.superstepIndex(),
                            new ArrayList<>(superstep.nodeResults().keySet()))));
                    })
                    .collectList()
                    .flatMap(steps -> Mono.fromRunnable(() -> {
                        // ==================== Verify ====================
                        if (policy.verifyMode() != VerifyMode.NONE) {
                            executeVerifyLoop(taskId, goal, graph, completedResults,
                                policy, budgetRef, planner, verifier, kernel, executor, sink, 0,
                                verifyExceeded, verifyPassed,
                                executorFailedNodes, executorSkippedNodes);
                        }

                        if (!verifyExceeded.get()) {
                            String finalOutput = assembleOutput(completedResults);
                            sink.next(toAlphaEvent(taskId, new AlphaEvent.AlphaCompleted(
                                taskId, now(), finalOutput, graph, verifyPassed.get(), false)));
                            sink.next(AgentEvent.of(taskId, EventType.TASK_COMPLETED, finalOutput));
                        }
                        if (terminalGuard.compareAndSet(false, true)) {
                            sink.complete();
                            closeQuietly(executor);
                        }
                    }).subscribeOn(Schedulers.boundedElastic()))
                    .onErrorResume(error -> {
                        sink.next(AgentEvent.of(taskId, EventType.TASK_FAILED, error.getMessage()));
                        if (terminalGuard.compareAndSet(false, true)) {
                            sink.complete();
                            closeQuietly(executor);
                        }
                        return Mono.empty();
                    })
                    .subscribe();

                sink.onDispose(() -> {
                    subscription.dispose();
                    if (terminalGuard.compareAndSet(false, true)) {
                        closeQuietly(executor);
                    }
                });

            } catch (Exception e) {
                sink.next(AgentEvent.of(context.taskId(), EventType.TASK_FAILED, e.getMessage()));
                if (terminalGuard.compareAndSet(false, true)) {
                    sink.complete();
                    closeQuietly(executor);
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<AgentEvent> resume(TaskContext context, Checkpoint checkpoint) {
        return execute(context);
    }

    // ==================== AAC Verify-Diagnose-Dispatch Loop ====================

    /**
     * AAC 核心 ~50 行：Verify → Diagnose → Dispatch → Retry/Degrade 循环。
     *
     * <p>与 openjiuwen 源码的关键差异：dispatch 使用 {@link ReplanAction}（携带执行数据的调度输出）
     * 而非 ReplanStrategy（verifier 推荐）。RootCause → ReplanAction 映射由
     * {@link RootCauseDiagnoser#toReplanAction} 完成（共享纯函数）。
     */
    private void executeVerifyLoop(
            TaskId taskId, PlanGoal goal, TaskGraph graph,
            Map<NodeId, Object> completedResults,
            ExecutionPolicy policy, AtomicReference<BudgetLimits> budgetRef,
            Planner planner, Verifier verifier, AgentKernel kernel,
            PregelExecutor executor,
            reactor.core.publisher.FluxSink<AgentEvent> sink,
            int retryCount,
            AtomicBoolean verifyExceeded,
            AtomicBoolean verifyPassed,
            Set<NodeId> executorFailedNodes,
            Set<NodeId> executorSkippedNodes) {

        // --- Verify ---
        boolean verifyThrew = false;
        VerifyResult verifyResult;
        try {
            verifyResult = verifier.verify(taskId, goal, graph,
                completedResults, policy, budgetRef.get())
                .timeout(LLM_TIMEOUT).block();
            budgetRef.set(budgetRef.get().recordLLMCall(256));
        } catch (Exception e) {
            verifyThrew = true;
            verifyResult = VerifyResult.failed("验证超时或异常: " + e.getMessage(), Set.of());
        }
        if (verifyResult == null) {
            verifyThrew = true;
            verifyResult = VerifyResult.failed("验证器返回 null", Set.of());
        }

        if (!verifyThrew && verifyResult.passed()) {
            sink.next(toAlphaEvent(taskId, new AlphaEvent.VerifyPassed(
                taskId, now(), verifyResult.overallFeedback())));
            verifyPassed.set(true);
            return;
        }

        sink.next(toAlphaEvent(taskId, new AlphaEvent.VerifyFailed(
            taskId, now(), verifyResult.overallFeedback(), verifyResult.failedNodes())));

        // --- Diagnose (AAC: shared RootCauseDiagnoser) ---
        // Convert NodeId sets to String sets for RootCauseDiagnoser
        Set<String> failedToolNodes = new HashSet<>();
        executorFailedNodes.forEach(n -> failedToolNodes.add(n.value()));
        executorSkippedNodes.forEach(n -> failedToolNodes.add(n.value()));

        RootCause cause = RootCauseDiagnoser.diagnose(verifyThrew, failedToolNodes, verifyResult.failedNodes());
        sink.next(toAlphaEvent(taskId, new AlphaEvent.RootCauseDiagnosed(taskId, now(), cause)));

        // --- Dispatch (AAC: ReplanAction with execution data) ---
        ReplanAction replanAction = RootCauseDiagnoser.toReplanAction(cause, verifyResult.overallFeedback(), verifyResult.failedNodes());

        // Framework backstop: maxRetries exceeded + verifier still demands replan → TASK_FAILED
        if (retryCount >= policy.maxRetries()
                && !(replanAction instanceof ReplanAction.AcceptPartial)) {
            sink.next(AgentEvent.of(taskId, EventType.TASK_FAILED,
                "验证失败次数超过上限 " + policy.maxRetries()));
            verifyExceeded.set(true);
            return;
        }

        // Cost-asymmetry conservative downgrade: non-GroundTruth GlobalReplan → LocalReplan
        if (replanAction instanceof ReplanAction.GlobalReplan
                && !isGroundTruthVerifier(verifier)) {
            sink.next(AgentEvent.of(taskId, EventType.PLAN_REVISED,
                "GlobalReplan→LocalReplan 降级：当前 Verifier 非地面真值，降级为有界局部重做"));
            replanAction = new ReplanAction.LocalReplan(verifyResult.failedNodes(), verifyResult.overallFeedback());
        }

        // --- AAC sealed dispatch (compiler-enforced exhaustiveness) ---
        switch (replanAction) {
            case ReplanAction.LocalReplan(var failedIds, var feedback) -> {
                sink.next(toAlphaEvent(taskId, new AlphaEvent.PlanRevised(
                    taskId, now(), graph, failedIds, feedback)));

                List<TaskNode> failedNodes = graph.nodes().stream()
                    .filter(n -> failedIds.contains(n.id().value()))
                    .toList();

                if (!failedNodes.isEmpty()) {
                    try {
                        List<TaskNode> resolvedNodes = new ArrayList<>();
                        for (TaskNode fn : failedNodes) {
                            Map<String, String> resolvedInputs = new LinkedHashMap<>();
                            for (var entry : fn.inputs().entrySet()) {
                                String val = entry.getValue();
                                if (val.startsWith("${") && val.endsWith("}")) {
                                    String ref = val.substring(2, val.length() - 1);
                                    String[] parts = ref.split("\\.", 2);
                                    if (parts.length < 2 || parts[0].isBlank()) {
                                        resolvedInputs.put(entry.getKey(), val);
                                        continue;
                                    }
                                    Object upstream = completedResults.get(new NodeId(parts[0]));
                                    resolvedInputs.put(entry.getKey(),
                                        upstream != null ? String.valueOf(upstream) : val);
                                } else {
                                    resolvedInputs.put(entry.getKey(), val);
                                }
                            }
                            // DEFECT-B: inject correctionHint for LLM_CALL nodes
                            String correctionHint = (fn.type() == TaskNodeType.LLM_CALL)
                                    ? feedback : null;
                            resolvedNodes.add(new TaskNode(fn.id(), fn.description(),
                                fn.type(), resolvedInputs, fn.expectedOutput(), fn.status(),
                                correctionHint));
                        }

                        TaskGraph subGraph = new TaskGraph(
                            goal.description() + " (局部重做)", resolvedNodes, List.of());
                        Map<NodeId, Object> reExecResults = new ConcurrentHashMap<>();
                        executor.execute(taskId, subGraph, policy, budgetRef.get())
                            .doOnNext(step -> reExecResults.putAll(step.nodeResults()))
                            .blockLast(LLM_TIMEOUT);
                        completedResults.putAll(reExecResults);
                    } catch (Exception e) {
                        sink.next(AgentEvent.of(taskId, EventType.PLAN_REVISED,
                            "LocalReplan re-execution failed: " + e.getMessage()));
                    }
                }

                executeVerifyLoop(taskId, goal, graph, completedResults,
                    policy, budgetRef, planner, verifier, kernel, executor, sink,
                    retryCount + 1, verifyExceeded, verifyPassed,
                    executorFailedNodes, executorSkippedNodes);
            }

            case ReplanAction.GlobalReplan(var feedback) -> {
                sink.next(AgentEvent.of(taskId, EventType.PLAN_REVISED,
                    "全局重规划（验证失败后）"));

                try {
                    final int bk = Math.max(1, Math.min(3, policy.bestOfK()));
                    PlanResult newPlan = (policy.bestOfKReplan()
                            ? planner.planBestOfK(taskId, goal, policy, bk, verifyResult)
                                .timeout(LLM_TIMEOUT.multipliedBy(bk + 1))
                            : planner.plan(taskId, goal, policy)
                                .timeout(LLM_TIMEOUT)).block();

                    if (newPlan != null && newPlan.isValid() && newPlan.graph() != null) {
                        sink.next(toAlphaEvent(taskId, new AlphaEvent.PlanRevised(
                            taskId, now(), newPlan.graph(), verifyResult.failedNodes(),
                            feedback)));

                        TaskGraph newGraph = newPlan.graph();
                        Map<NodeId, Object> newResults = new ConcurrentHashMap<>();
                        executor.execute(taskId, newGraph, policy, budgetRef.get())
                            .doOnNext(step -> newResults.putAll(step.nodeResults()))
                            .blockLast(LLM_TIMEOUT);

                        executeVerifyLoop(taskId, goal, newGraph, newResults,
                            policy, budgetRef, planner, verifier, kernel, executor, sink,
                            retryCount + 1, verifyExceeded, verifyPassed,
                            executorFailedNodes, executorSkippedNodes);
                        return;
                    }
                } catch (Exception globalReplanEx) {
                    sink.next(AgentEvent.of(taskId, EventType.PLAN_REVISED,
                        "GlobalReplan failed: " + globalReplanEx.getMessage()
                        + " — falling back to old plan data"));
                }

                executeVerifyLoop(taskId, goal, graph, completedResults,
                    policy, budgetRef, planner, verifier, kernel, executor, sink,
                    retryCount + 1, verifyExceeded, verifyPassed,
                    executorFailedNodes, executorSkippedNodes);
            }

            case ReplanAction.AcceptPartial(var reason) -> {
                String finalOutput = assembleOutput(completedResults);
                sink.next(toAlphaEvent(taskId, new AlphaEvent.AlphaCompleted(
                    taskId, now(), finalOutput, graph, false, true)));
                sink.next(AgentEvent.of(taskId, EventType.TASK_COMPLETED, finalOutput));
                verifyExceeded.set(true);
            }
        }
    }

    // ==================== 组件解析 ====================

    private Planner resolvePlanner(TaskContext context, AgentKernel kernel) {
        Object custom = context.extraContext().get("planner");
        if (custom instanceof Planner p) return p;
        return new DefaultPlanner(kernel);
    }

    private PregelExecutor resolveExecutor(TaskContext context) {
        Object custom = context.extraContext().get("executor");
        if (custom instanceof PregelExecutor e) return e;
        return new DefaultPregelExecutor(context);
    }

    private Verifier resolveVerifier(TaskContext context, AgentKernel kernel) {
        Object custom = context.extraContext().get("verifier");
        if (custom instanceof Verifier v) return v;
        return new DefaultVerifier(kernel);
    }

    // ==================== 辅助方法 ====================

    private PlanGoal buildPlanGoal(TaskContext context) {
        String userInput = context.input().userInput();
        Map<String, Object> params = context.input().parameters();

        List<String> successCriteria = List.of();
        if (params.containsKey("successCriteria") && params.get("successCriteria") instanceof List<?> list) {
            successCriteria = list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }

        Set<String> availableTools = resolveAvailableTools(params, context.agentDefinition());
        Map<String, String> ctx = new HashMap<>();
        if (context.input().metadata() != null) {
            ctx.putAll(context.input().metadata());
        }

        return PlanGoal.of(userInput, successCriteria, availableTools, ctx);
    }

    static Set<String> resolveAvailableTools(Map<String, Object> params, AgentDefinition agentDef) {
        if (params != null && params.containsKey("availableTools")
                && params.get("availableTools") instanceof List<?> list) {
            return new HashSet<>(list.stream().filter(Objects::nonNull).map(Object::toString).toList());
        }
        if (agentDef != null && agentDef.tools() != null) {
            Set<String> tools = new HashSet<>();
            for (AgentDefinition.ToolDefinition t : agentDef.tools()) {
                tools.add(t.name());
            }
            return tools;
        }
        return Set.of();
    }

    private ExecutionPolicy resolveExecutionPolicy(TaskContext context) {
        Object policy = context.extraContext().get("executionPolicy");
        return policy instanceof ExecutionPolicy ep ? ep : ExecutionPolicy.productionDefault();
    }

    private void saveCheckpoint(AgentKernel kernel, TaskId taskId, String phase,
                                 int stepIndex, TaskGraph graph) {
        try {
            Checkpoint cp = Checkpoint.of(taskId, phase, stepIndex, serializeGraph(graph));
            kernel.saveCheckpoint(cp).block(LLM_TIMEOUT);
        } catch (Exception ignored) {
            // checkpoint save failure must not interrupt execution
        }
    }

    private String serializeGraph(TaskGraph graph) {
        return graph != null ? graph.goal() : "";
    }

    private String assembleOutput(Map<NodeId, Object> results) {
        StringBuilder sb = new StringBuilder();
        for (var entry : results.entrySet()) {
            Object value = entry.getValue();
            String display = value instanceof NodeResult.Success s
                ? String.valueOf(s.value())
                : String.valueOf(value);
            sb.append(entry.getKey().value()).append(": ").append(display).append("\n");
        }
        return sb.toString().trim();
    }

    private Instant now() { return Instant.now(); }

    private AgentEvent toAlphaEvent(TaskId taskId, AlphaEvent alpha) {
        return AgentEvent.of(taskId, mapAlphaEventType(alpha), alpha.toString());
    }

    private EventType mapAlphaEventType(AlphaEvent alpha) {
        return switch (alpha) {
            case AlphaEvent.PlanGenerated ignored -> EventType.PLAN_GENERATED;
            case AlphaEvent.PlanRevised ignored -> EventType.PLAN_REVISED;
            case AlphaEvent.NodeStarted ignored -> EventType.NODE_STARTED;
            case AlphaEvent.NodeCompleted ignored -> EventType.NODE_COMPLETED;
            case AlphaEvent.NodeFailed ignored -> EventType.NODE_FAILED;
            case AlphaEvent.LayerCompleted ignored -> EventType.LAYER_COMPLETED;
            case AlphaEvent.VerifyPassed ignored -> EventType.VERIFY_PASSED;
            case AlphaEvent.VerifyFailed ignored -> EventType.VERIFY_FAILED;
            case AlphaEvent.RootCauseDiagnosed ignored -> EventType.ROOT_CAUSE_DIAGNOSED;
            case AlphaEvent.ApprovalRequired ignored -> EventType.APPROVAL_REQUIRED;
            case AlphaEvent.ApprovalGranted ignored -> EventType.APPROVAL_GRANTED;
            case AlphaEvent.ApprovalDenied ignored -> EventType.APPROVAL_DENIED;
            case AlphaEvent.ConstraintViolated ignored -> EventType.CONSTRAINT_VIOLATED;
            case AlphaEvent.AlphaCompleted ignored -> EventType.TASK_COMPLETED;
        };
    }

    private void closeQuietly(PregelExecutor executor) {
        if (executor instanceof AutoCloseable ac) {
            try { ac.close(); } catch (Exception ignored) {}
        }
    }

    private boolean isTimeout(Exception ex) {
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("TimeoutException") || msg.contains("timeout"))) return true;
        Throwable cause = ex.getCause();
        if (cause != null) {
            String cmsg = cause.getMessage();
            if (cmsg != null && (cmsg.contains("TimeoutException") || cmsg.contains("timeout"))) return true;
        }
        return false;
    }

    private boolean isGroundTruthVerifier(Verifier verifier) {
        // Check by class name to avoid direct dependency on GroundTruthVerifier
        return verifier.getClass().getSimpleName().equals("GroundTruthVerifier");
    }
}
