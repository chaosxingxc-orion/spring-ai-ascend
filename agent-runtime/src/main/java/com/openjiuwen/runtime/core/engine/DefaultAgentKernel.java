package com.openjiuwen.runtime.core.engine;

import com.openjiuwen.core.kernel.model.*;
import com.openjiuwen.core.meta.AgentDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AgentKernel 默认实现。
 *
 * 职责：
 * 1. 聚合 SafetyBoundary，在每个系统调用前后执行安全检查
 * 2. 管理 LLM 调用（通过 Spring AI ChatModel 注入）
 * 3. 管理工具调用（通过工具注册表注入）
 * 4. 管理检查点存储（内存 / Redis / JDBC）
 * 5. 管理事件流（通过 Reactor Sinks.Many 广播）
 *
 * 线程安全：所有状态都是 ConcurrentHashMap / CopyOnWriteArrayList，
 * 配合虚拟线程可安全支持万级并发。
 */
public class DefaultAgentKernel implements AgentKernel {

    /** LLM 调用函数。实际由 Spring AI ChatModel 实现。 */
    private final LLMProvider llmProvider;

    /** 工具注册表。toolName → 工具执行函数。 */
    private final Map<ToolName, ToolExecutor> toolExecutors;

    /** 工具签名注册表。toolName → 工具 schema（@Tool 反射构建，由 AgentBeanPostProcessor 动态登记）。
     *  <p>与 {@link #toolExecutors} 并行：executors 持执行 lambda，本表持签名 schema。供
     *  {@link #getToolDefinitions()} 暴露给 Planner 渲染工具签名（工具签名透传，防签名不透传）。
     *  同 {@link #toolExecutors} 持共享 Map 引用而非拷贝，运行期动态注册对 kernel 可见。 */
    private final Map<ToolName, AgentDefinition.ToolDefinition> toolDefinitions;

    /** 检查点存储 */
    private final CheckpointStore checkpointStore;

    /** 安全边界 */
    private final SafetyBoundary safetyBoundary;

    /** 事件广播：每个 taskId 一个 SinkHolder（multicast sink + 生产者串行锁）。
     *  <p>并发根因（见诊断栈 {@code InternalManySink.emitNext → emitChunk → lambda$think$4}）：
     *  扇出并行节点的虚拟线程在 {@code think().block()} 订阅期同步 emit BLOCK_START、并在 LLM 流 doOnNext 上
     *  密集 emit DELTA，多线程并发轰击<strong>同一个</strong> multicast 安全 sink。该 sink 的 {@code tryEmitNext}
     *  以 CAS 串行生产者，争用返回 {@code FAIL_NON_SERIALIZED}；{@code busyLooping} 重试 1s 仍抢不到 CAS 即抛
     *  {@code Sinks$EmissionException}(Reactor Spec Rule 1.3) → think().block() 抛 → 节点 FAILED（串行基线全绿、并行必现）。
     *  <p>治本：{@code emitLock} 把并发生产者串行化——同一时刻只一个线程进 {@code emitNext}，tryEmitNext 必成功，
     *  根因消除（CAS 不再争用、不再抛）。下游订阅者仍在持锁线程同步收 onNext，但"串行"即满足 Spec Rule 1.3。
     *  锁粒度按 taskId（争用仅在同任务并行节点间），不同任务互不阻塞。 */
    private final Map<TaskId, SinkHolder> eventSinks = new ConcurrentHashMap<>();

    /** 每 taskId 的广播 sink 配一把生产者串行锁（见 {@link #eventSinks} 根因注释）。 */
    private static final class SinkHolder {
        final Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        final ReentrantLock emitLock = new ReentrantLock();
    }

    /** 运行时结果存储：taskId → nodeId → result */
    private final Map<TaskId, Map<NodeId, Object>> taskResults = new ConcurrentHashMap<>();

    /** 4 参便捷构造——无工具签名 schema（{@link #getToolDefinitions()} 返回空）。
     *  <p>供测试 / 无 @Tool 场景；等价于 5 参构造传空 {@code toolDefinitions}（planner 对无签名的工具名
     *  回退渲染裸名，与工具签名透传修复前行为一致）。生产路径（@Tool 自省）走 5 参构造。 */
    public DefaultAgentKernel(
        LLMProvider llmProvider,
        Map<ToolName, ToolExecutor> toolExecutors,
        CheckpointStore checkpointStore,
        SafetyBoundary safetyBoundary
    ) {
        this(llmProvider, toolExecutors, new ConcurrentHashMap<>(), checkpointStore, safetyBoundary);
    }

    public DefaultAgentKernel(
        LLMProvider llmProvider,
        Map<ToolName, ToolExecutor> toolExecutors,
        Map<ToolName, AgentDefinition.ToolDefinition> toolDefinitions,
        CheckpointStore checkpointStore,
        SafetyBoundary safetyBoundary
    ) {
        this.llmProvider = Objects.requireNonNull(llmProvider);
        // 持有共享 Map 引用而非防御性拷贝：@Tool 由 AgentBeanPostProcessor 在 kernel 构造后
        // 动态注册到该 Spring 单例 Map（OpenjiuwenAutoConfiguration#toolExecutors +
        // AgentBeanPostProcessor#registerToolExecutor）。拷贝会切断关联，使运行期注册的工具
        // 对 kernel 不可见（invokeTool 在拷贝快照里查不到 → "工具未注册"），导致所有真实
        // @Tool 的 TOOL_CALL 执行失败。调用方应传入 ConcurrentHashMap 以支持并发注册与查找。
        this.toolExecutors = Objects.requireNonNull(toolExecutors);
        // 同理：toolDefinitions 亦持共享 Map 引用（OpenjiuwenAutoConfiguration#toolDefinitions +
        // AgentBeanPostProcessor 登记），供 getToolDefinitions 暴露签名供 Planner 渲染。
        this.toolDefinitions = Objects.requireNonNull(toolDefinitions);
        this.checkpointStore = Objects.requireNonNull(checkpointStore);
        this.safetyBoundary = Objects.requireNonNull(safetyBoundary);
    }

    /** think 的 idle-timeout：流式调用时若连续 IDLE_TIMEOUT_SECONDS 无 chunk 则判卡死，快速失败。
     *  健康慢（持续 decode）每来一个 chunk 重置计时，永不撞此阈值 —— LLM 慢响应适配。 */
    private static final long IDLE_TIMEOUT_SECONDS = 30L;

    /** emit 失败重试兜底：忙等至多 1s，覆盖 FAIL_OVERFLOW（订阅者短暂滞后）。
     *  并发争用（FAIL_NON_SERIALIZED）已由 {@link SinkHolder#emitLock} 串行化生产者根除——
     *  持锁期间至多一个线程 emit，tryEmitNext 不再返回 FAIL_NON_SERIALIZED。 */
    private static final Sinks.EmitFailureHandler EMIT_HANDLER =
        Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1));

    // ==================== 系统调用 1: think ====================

    @Override
    public Mono<String> think(String prompt, BudgetLimits budget) {
        // 2 参入口退化为不带身份的 4 参调用：taskId==null 时 4 参实现跳过流式事件发布，
        // 行为与历史 2 参实现完全一致（仅流式聚合 + idle-timeout + 安全检查）。
        return think(prompt, budget, null, null);
    }

    @Override
    public Mono<String> think(String prompt, BudgetLimits budget, TaskId taskId, NodeId nodeId) {
        return Mono.fromCallable(() -> {
                // 前置检查：预算
                List<Violation> budgetViolations = safetyBoundary.checkBudget(budget);
                if (!budgetViolations.isEmpty()) {
                    throw new SafetyViolationException("预算不足", budgetViolations);
                }
                return true; // 仅触发检查，返回值驱动后续 flatMap
            })
            .flatMap(ignored -> {
                // 流式三段式（对齐 AgentScope v2 *_BLOCK_START/_DELTA/_END）：
                // 块头带 nodeId，前端按块分桶，扇出并行节点的 chunk 不串。
                // taskId==null（2 参入口/测试）时跳过事件发布，零行为变化。
                boolean emit = taskId != null;
                if (emit) emitChunk(taskId, EventType.THINKING_BLOCK_START, "", nodeId);
                // think() 总时限（total-duration cap）与 idle-timeout 正交分工：
                //   idle-timeout(:138) 治"卡死"——连续 IDLE_TIMEOUT_SECONDS 无 chunk 即快速失败；
                //   但不治"慢滴"——chunk 以 <idle 间隔永远滴来 → idle 永不触发 + reduce 永不 complete → 调用挂死。
                // 在 reduce 后的 Mono<String> 上加 .timeout(totalCap)：Mono.timeout 是订阅→唯一发射的总时限
                // (不随 chunk 重置)，与内层 Flux.timeout(idle=每 chunk 重置)正交。三机制分工：
                //   健康快响应→两计时器都满足；全 hang(黑洞)→30s idle 触发；慢滴→reduce 永不发射→totalCap 截断。
                // totalCapMillis==0(Unlimited)时跳过，保留"无总时限"契约（不改既有语义）。
                // 诚实边界：本 cap 只治单次 think() 的 wall-clock 总时限，不覆盖——
                //   (1) totalCapMillis==0(Unlimited)路径：仍可慢滴挂死，由 Budget.Fixed.productionDefault()
                //       (timeoutMillis=300_000ms)默认配总时限兜底；显式 Unlimited 需调用方自担。
                //   (2) 累计预算追踪：BudgetLimits.elapsedMillis/isExceeded 仍是结构性死代码(withElapsed 零调用)，
                //       跨多次 think() 的累计预算超限不属本 cap，留独立治本路径。
                long totalCapMillis = budget.budget().timeoutMillis();
                Mono<String> reduced = llmProvider.stream(prompt)
                    .timeout(Duration.ofSeconds(IDLE_TIMEOUT_SECONDS))   // idle-timeout：连续 30s 无 chunk 判卡死
                    .doOnNext(chunk -> { if (emit) emitChunk(taskId, EventType.THINKING_DELTA, chunk, null); })
                    .reduce(new StringBuilder(), StringBuilder::append)
                    .map(StringBuilder::toString);
                if (totalCapMillis > 0) {
                    reduced = reduced.timeout(Duration.ofMillis(totalCapMillis));   // whole-call wall-clock cap
                }
                return reduced
                    .doOnSuccess(v -> { if (emit) emitChunk(taskId, EventType.THINKING_BLOCK_END, "", nodeId); })
                    .doOnError(e -> { if (emit) emitChunk(taskId, EventType.THINKING_BLOCK_END,
                        "流式推理出错（已脱敏）", nodeId); });
            })
            .flatMap(output -> {
                // 后置检查：输出安全性
                List<Violation> outputViolations = safetyBoundary.checkLLMOutput(output);
                boolean hasCritical = outputViolations.stream()
                    .anyMatch(v -> v.severity() == Violation.Severity.CRITICAL);
                if (hasCritical) {
                    return Mono.error(new SafetyViolationException("LLM 输出违规", outputViolations));
                }
                // 非 CRITICAL 放行（同原行为）
                return Mono.just(output);
            });
    }

    // ==================== 系统调用 2: invokeTool ====================

    @Override
    public Mono<ToolResult> invokeTool(ToolName toolName, Map<String, Object> arguments, BudgetLimits budget) {
        return Mono.fromCallable(() -> {
            // 前置检查：预算 + 工具权限
            List<Violation> violations = safetyBoundary.checkToolCall(toolName, arguments, budget);
            if (!violations.isEmpty()) {
                boolean hasCritical = violations.stream()
                    .anyMatch(v -> v.severity() == Violation.Severity.CRITICAL);
                if (hasCritical) {
                    throw new SafetyViolationException("工具调用被拒绝", violations);
                }
            }

            // 查找工具
            ToolExecutor executor = toolExecutors.get(toolName);
            if (executor == null) {
                return ToolResult.fail(toolName, "工具未注册: " + toolName);
            }

            // 执行工具
            try {
                Object result = executor.execute(arguments);
                return ToolResult.ok(toolName, result);
            } catch (Exception e) {
                return ToolResult.fail(toolName, "工具执行失败: " + e.getMessage());
            }
        });
    }

    // ==================== 工具签名自省（工具签名透传：暴露 @Tool schema 供 Planner 渲染签名） ====================

    @Override
    public List<AgentDefinition.ToolDefinition> getToolDefinitions() {
        return List.copyOf(toolDefinitions.values());
    }

    // ==================== 系统调用 3: observe ====================

    @Override
    public Mono<Map<NodeId, Object>> observe(TaskId taskId, Set<NodeId> nodeIds) {
        return Mono.fromCallable(() -> {
            Map<NodeId, Object> results = taskResults.getOrDefault(taskId, Map.of());
            if (nodeIds == null || nodeIds.isEmpty()) {
                return Collections.unmodifiableMap(results);
            }
            Map<NodeId, Object> filtered = new LinkedHashMap<>();
            for (NodeId nid : nodeIds) {
                if (results.containsKey(nid)) {
                    filtered.put(nid, results.get(nid));
                }
            }
            return Collections.unmodifiableMap(filtered);
        });
    }

    // ==================== 系统调用 4: saveCheckpoint ====================

    @Override
    public Mono<CheckpointId> saveCheckpoint(Checkpoint checkpoint) {
        return checkpointStore.save(checkpoint).thenReturn(checkpoint.checkpointId());
    }

    // ==================== 系统调用 5: restoreCheckpoint ====================

    @Override
    public Mono<Checkpoint> restoreCheckpoint(TaskId taskId) {
        return checkpointStore.loadLatest(taskId)
            .doOnNext(cp -> {
                // 恢复结果存储
                // 实际的反序列化逻辑由上层策略处理
            });
    }

    // ==================== 系统调用 6: yield ====================

    @Override
    public Mono<CheckpointId> yield(TaskId taskId, YieldReason reason, String currentState) {
        Checkpoint checkpoint = Checkpoint.of(taskId, "YIELDED", 0, currentState);
        return saveCheckpoint(checkpoint)
            .doOnNext(cpId -> emit(AgentEvent.of(taskId, EventType.TASK_PAUSED,
                reason.description(), Map.of("reason", reason.getClass().getSimpleName()))).subscribe());
    }

    // ==================== 系统调用 7: emit ====================

    @Override
    public Mono<Void> emit(AgentEvent event) {
        return Mono.fromRunnable(() -> {
            SinkHolder holder = eventSinks.computeIfAbsent(event.taskId(), k -> new SinkHolder());
            // 持锁串行化生产者：多线程并发 emitNext 会令 multicast sink 的 CAS 持续 FAIL_NON_SERIALIZED，
            // busyLoop 1s 抢不到即抛 Sinks$EmissionException（见 #eventSinks 根因注释）。锁保证至多一个线程
            // 进 emitNext，tryEmitNext 必成功。非流式 chunk 的事件发布都经此路径（NodeCompleted/Failed 等）。
            holder.emitLock.lock();
            try {
                holder.sink.emitNext(event, EMIT_HANDLER);
            } finally {
                holder.emitLock.unlock();
            }
        });
    }

    @Override
    public Flux<AgentEvent> observeEvents(TaskId taskId) {
        return eventSinks.computeIfAbsent(taskId, k -> new SinkHolder()).sink.asFlux();
    }

    /**
     * 流式 chunk 事件直发——不经 {@link #emit(AgentEvent)} 的 Mono.fromRunnable 包装，
     * 避免在 think 的 doOnNext/doOnSuccess 热路径上每个 chunk 分配一个 Mono 再 subscribe。
     *
     * <p>并发安全：扇出并行节点的虚拟线程会并发调用（A/B/C 同时 think），
     * 由 {@link SinkHolder#emitLock} 串行化生产者——同一时刻只一个线程 emitNext，multicast sink 的 CAS 不再
     * 争用（先前高并发下 CAS 持续 FAIL_NON_SERIALIZED、busyLoop 1s 抢不到即抛 Sinks$EmissionException，
     * 见 #eventSinks 根因注释）。对快产慢消仍由 busyLoop + onBackpressureBuffer 形成背压。
     *
     * <p><b>调用方契约（硬约束）</b>：sink.emitNext 会同步通知下游订阅者，发生在持锁线程上
     * （DELTA 经 doOnNext 跑在 reactor-netty I/O 线程）。故：① 下游订阅链必须<b>非阻塞</b>——当前唯一消费者
     * {@code AgentClient.invokeStream} 仅 takeUntil 谓词 + doFinally（无重 I/O），锁持有为微秒级；② 订阅者<b>不得</b>
     * 在 onNext 内对本 taskId 回调 emit/emitChunk（重入同一把锁→嵌套 onNext→重开 Reactor Spec Rule 1.3），当前链路无此回调。
     * 若未来下游需阻塞操作，应在该处加 publishOn 隔离，而非在此持锁等待。
     *
     * @param nodeId 非 null 时作为 metadata.nodeId 携带（仅 BLOCK_START/END；DELTA 传 null 保持裸 chunk）
     */
    private void emitChunk(TaskId taskId, EventType type, String data, NodeId nodeId) {
        SinkHolder holder = eventSinks.computeIfAbsent(taskId, k -> new SinkHolder());
        Map<String, String> meta = (nodeId != null) ? Map.of("nodeId", nodeId.value()) : Map.of();
        holder.emitLock.lock();
        try {
            holder.sink.emitNext(AgentEvent.of(taskId, type, data, meta), EMIT_HANDLER);
        } finally {
            holder.emitLock.unlock();
        }
    }

    // ==================== 内部辅助：注册执行结果 ====================

    /**
     * 记录节点的执行结果，供 observe() 查询。
     * 由策略层在节点执行完成后调用。
     */
    public void recordNodeResult(TaskId taskId, NodeId nodeId, Object result) {
        taskResults.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>()).put(nodeId, result);
    }

    // ==================== 内部接口 ====================

    /**
     * LLM 调用抽象。由 Spring AI ChatModel 实现。
     *
     * <p>call() 为非流式整包调用；stream() 为流式调用（每 chunk 一个 String）。
     * think() 默认走 stream() 以获得 idle-timeout 语义（健康慢不被误杀、卡死快速失败）。
     * 无流式能力的 provider（如测试 MockChatModel）只需实现 call()，stream() 退化为单元素流。
     */
    public interface LLMProvider {
        String call(String prompt);

        /** 流式调用。默认退化为单元素流（兼容无流式能力的 provider）。 */
        default Flux<String> stream(String prompt) {
            return Flux.just(call(prompt));
        }
    }

    /**
     * 工具执行抽象。@Tool 方法或 MCP 远程工具。
     */
    @FunctionalInterface
    public interface ToolExecutor {
        Object execute(Map<String, Object> arguments) throws Exception;
    }

    /**
     * 检查点存储抽象。
     */
    public interface CheckpointStore {
        Mono<Void> save(Checkpoint checkpoint);
        Mono<Checkpoint> loadLatest(TaskId taskId);
        Flux<Checkpoint> list(TaskId taskId);
    }

    /**
     * 安全违规异常——不用于系统 bug，只用于 Agent 行为越界。
     */
    public static class SafetyViolationException extends RuntimeException {
        private final List<Violation> violations;

        public SafetyViolationException(String message, List<Violation> violations) {
            super(message);
            this.violations = violations;
        }

        public List<Violation> violations() { return violations; }
    }
}
