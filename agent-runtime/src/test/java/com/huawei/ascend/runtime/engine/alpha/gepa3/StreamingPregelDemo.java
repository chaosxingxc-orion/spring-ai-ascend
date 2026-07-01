package com.huawei.ascend.runtime.engine.alpha.gepa3;

import com.openjiuwen.core.alpha.graph.TaskGraph;
import com.openjiuwen.core.alpha.graph.TaskNode;
import com.openjiuwen.core.alpha.graph.TaskNodeType;
import com.openjiuwen.core.kernel.model.Budget;
import com.openjiuwen.core.kernel.model.BudgetLimits;
import com.openjiuwen.core.kernel.model.NodeId;
import com.openjiuwen.core.kernel.model.TaskId;
import com.openjiuwen.core.kernel.model.ToolName;
import com.openjiuwen.core.kernel.model.ToolResult;
import com.openjiuwen.runtime.core.engine.AgentKernel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * StreamingPregelDemo — 零阻塞流式 Pregel 执行器原型 v1.1。
 *
 * <p>v1.1 修复（来自 P2/P5 联合审查）：
 * <ul>
 *   <li><b>B1 治本</b>: 每节点结果在 doOnNext 中存入 Accumulator（collectList 屏障之前）。
 *       层超时时 acc.hasResult() 正确反映部分结果。</li>
 *   <li><b>B2 治本</b>: 空层防御 — Math.max(1, concurrency) + 空列表早返回。</li>
 *   <li><b>B3 治本</b>: Flux.defer() 每订阅创建新 Accumulator，消除双订阅状态污染。</li>
 *   <li><b>B4 治本</b>: executeNode 入口检查 acc.isCancelled()。</li>
 *   <li><b>B5 治本</b>: 超时参数构造器注入 + executeSuperstep 改为 protected。</li>
 *   <li><b>B6 治本</b>: NodeResult.failed() value=null（不存错误字符串）；
 *       getResultValue() 检查 failed 标志。</li>
 *   <li><b>B7 治本</b>: kernel Mono.empty() 通过 switchIfEmpty 转为显式错误。</li>
 * </ul>
 */
public class StreamingPregelDemo {

    static final Duration DEFAULT_NODE_TIMEOUT = Duration.ofSeconds(120);
    static final Duration DEFAULT_LAYER_TIMEOUT = Duration.ofSeconds(360);
    static final int DEFAULT_TOKEN_ESTIMATE = 256;

    private final AgentKernel kernel;
    final Duration nodeTimeout;
    final Duration layerTimeout;
    final int defaultTokenEstimate;

    public StreamingPregelDemo(AgentKernel kernel) {
        this(kernel, DEFAULT_NODE_TIMEOUT, DEFAULT_LAYER_TIMEOUT, DEFAULT_TOKEN_ESTIMATE);
    }

    public StreamingPregelDemo(AgentKernel kernel, Duration nodeTimeout, Duration layerTimeout) {
        this(kernel, nodeTimeout, layerTimeout, DEFAULT_TOKEN_ESTIMATE);
    }

    StreamingPregelDemo(AgentKernel kernel, Duration nodeTimeout, Duration layerTimeout,
                        int defaultTokenEstimate) {
        this.kernel = kernel;
        this.nodeTimeout = nodeTimeout;
        this.layerTimeout = layerTimeout;
        this.defaultTokenEstimate = defaultTokenEstimate;
    }

    // ── 公共类型 ──

    /** v1.1: failed 结果的 value 字段为 null，防止下游数据腐败。 */
    public record NodeResult(NodeId nodeId, Object value, boolean failed, String error) {
        public static NodeResult ok(NodeId id, Object value) {
            return new NodeResult(id, value, false, null);
        }
        public static NodeResult failed(NodeId id, String error) {
            return new NodeResult(id, null, true, error);
        }
    }

    public record SuperstepResult(int layerIndex, List<NodeResult> nodeResults,
                                   Set<NodeId> failedNodes) {}

    // ── 公共入口 ──

    /** v1.1: Flux.defer() 每次订阅创建新 Accumulator。 */
    public Flux<SuperstepResult> execute(TaskId taskId, TaskGraph graph,
                                          BudgetLimits initialBudget) {
        return Flux.defer(() -> {
            List<List<TaskNode>> layers = graph.executionLayers();
            Accumulator acc = new Accumulator(initialBudget);
            return Flux.fromIterable(layers)
                    .concatMap(layer -> executeSuperstep(taskId, layer, acc,
                            layers.indexOf(layer)))
                    .doOnCancel(acc::markCancelled)
                    .doFinally(signal -> acc.dispose());
        });
    }

    // ── 核心方法 ──

    /** v1.1: protected 可见性 + 空层防御 + doOnNext 即时存储。 */
    protected Mono<SuperstepResult> executeSuperstep(
            TaskId taskId, List<TaskNode> layer, Accumulator acc, int layerIndex) {
        if (layer.isEmpty()) {
            return Mono.just(new SuperstepResult(layerIndex, List.of(), Set.of()));
        }
        int concurrency = Math.max(1,
                Math.min(layer.size(), Runtime.getRuntime().availableProcessors()));

        return Flux.fromIterable(layer)
                .flatMap(node -> executeNode(taskId, node, acc)
                        .timeout(nodeTimeout)
                        .onErrorResume(e -> {
                            acc.recordFailed(node.id());
                            return Mono.just(NodeResult.failed(node.id(),
                                    "节点超时或异常: " + e.getMessage()));
                        })
                        // B1 治本: 每节点结果在 barrier 之前即时存储
                        .doOnNext(nr -> {
                            if (!nr.failed()) acc.putResult(nr.nodeId(), nr);
                        }),
                        concurrency)
                .collectList()
                .timeout(layerTimeout)
                .onErrorResume(e ->
                        // B1 治本: acc.hasResult() 现正确反映已完成节点的部分结果
                        Flux.fromIterable(layer)
                                .flatMap(node -> {
                                    if (acc.hasResult(node.id()))
                                        return Mono.just(acc.getResult(node.id()));
                                    acc.recordFailed(node.id());
                                    return Mono.just(NodeResult.failed(node.id(),
                                            "层超时: " + e.getMessage()));
                                })
                                .collectList())
                .map(results -> new SuperstepResult(layerIndex,
                        List.copyOf(results), acc.snapshotFailed()));
    }

    // ── 节点分发 ──

    private Mono<NodeResult> executeNode(TaskId taskId, TaskNode node, Accumulator acc) {
        if (acc.isCancelled())
            return Mono.just(NodeResult.failed(node.id(), "执行已取消"));
        return switch (node.type()) {
            case LLM_CALL -> executeLLMNode(taskId, node, acc);
            case TOOL_CALL -> executeToolNode(taskId, node, acc);
            case SUB_AGENT -> Mono.just(NodeResult.failed(node.id(),
                    "SUB_AGENT 不在原型范围"));
        };
    }

    private Mono<NodeResult> executeLLMNode(TaskId taskId, TaskNode node, Accumulator acc) {
        String prompt = buildLLMPrompt(node, acc);
        BudgetLimits current = acc.budgetRef.get();
        return kernel.think(prompt, current, taskId, node.id())
                .switchIfEmpty(Mono.error(
                        new RuntimeException("LLM 返回空响应: node=" + node.id().value())))
                .map(output -> {
                    acc.recordLLMCall(defaultTokenEstimate);
                    return NodeResult.ok(node.id(), output);
                });
    }

    private Mono<NodeResult> executeToolNode(TaskId taskId, TaskNode node, Accumulator acc) {
        Map<String, Object> resolvedArgs = resolveInputs(node.inputs(), acc);
        ToolName toolName = new ToolName(bareToolName(node.description()));
        return kernel.invokeTool(toolName, resolvedArgs, acc.budgetRef.get())
                .switchIfEmpty(Mono.error(
                        new RuntimeException("工具返回空: " + toolName.value())))
                .flatMap(result -> {
                    acc.recordToolCall();
                    if (result != null && result.success())
                        return Mono.just(NodeResult.ok(node.id(), result.result()));
                    String errMsg = "工具调用失败: "
                            + (result != null ? result.error() : "null result");
                    return Mono.error(new RuntimeException(errMsg));
                });
    }

    // ── 辅助方法 ──

    private String buildLLMPrompt(TaskNode node, Accumulator acc) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务: ").append(node.description()).append("\n");
        if (!node.inputs().isEmpty()) {
            sb.append("输入:\n");
            node.inputs().forEach((key, ref) -> {
                NodeId depId = resolveNodeRef(ref);
                Object val = acc.getResultValue(depId);
                sb.append("  ").append(key).append(" = ").append(val).append("\n");
            });
        }
        if (node.expectedOutput() != null && !node.expectedOutput().isBlank())
            sb.append("期望输出: ").append(node.expectedOutput()).append("\n");
        return sb.toString();
    }

    private Map<String, Object> resolveInputs(Map<String, String> inputs, Accumulator acc) {
        if (inputs == null || inputs.isEmpty()) return Map.of();
        Map<String, Object> resolved = new LinkedHashMap<>();
        inputs.forEach((key, ref) -> {
            NodeId depId = resolveNodeRef(ref);
            resolved.put(key, acc.getResultValue(depId));
        });
        return Collections.unmodifiableMap(resolved);
    }

    static NodeId resolveNodeRef(String ref) {
        if (ref == null) return new NodeId("null");
        String cleaned = ref.replace("${", "").replace("}", "");
        if (cleaned.contains("."))
            cleaned = cleaned.substring(0, cleaned.indexOf('.'));
        return new NodeId(cleaned);
    }

    static String bareToolName(String description) {
        if (description == null || description.isBlank()) return "unknown";
        String trimmed = description.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    // ── 跨层累加器 ──

    static class Accumulator {
        final Map<NodeId, NodeResult> results = new ConcurrentHashMap<>();
        final Set<NodeId> failedNodes = ConcurrentHashMap.newKeySet();
        final AtomicReference<BudgetLimits> budgetRef;
        final AtomicLong llmCalls = new AtomicLong(0);
        final AtomicLong toolCalls = new AtomicLong(0);
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        Accumulator(BudgetLimits initialBudget) {
            this.budgetRef = new AtomicReference<>(initialBudget);
        }

        void putResult(NodeId id, NodeResult result) { results.put(id, result); }
        boolean hasResult(NodeId id) { return results.containsKey(id); }
        NodeResult getResult(NodeId id) { return results.get(id); }

        /** v1.1: 检查 failed 标志 — 失败节点返回 null。 */
        Object getResultValue(NodeId id) {
            NodeResult nr = results.get(id);
            if (nr == null || nr.failed()) return null;
            return nr.value();
        }

        void recordFailed(NodeId id) { failedNodes.add(id); }
        Set<NodeId> snapshotFailed() { return Set.copyOf(failedNodes); }
        void recordLLMCall(int tokens) {
            llmCalls.incrementAndGet();
            budgetRef.updateAndGet(b -> b.recordLLMCall(tokens));
        }
        void recordToolCall() {
            toolCalls.incrementAndGet();
            budgetRef.updateAndGet(BudgetLimits::recordToolCall);
        }
        void markCancelled() { cancelled.set(true); }
        boolean isCancelled() { return cancelled.get(); }
        void dispose() { results.clear(); failedNodes.clear(); }
    }
}
