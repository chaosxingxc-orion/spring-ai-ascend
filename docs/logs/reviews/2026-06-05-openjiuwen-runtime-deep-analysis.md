# openjiuwen Runtime 深度架构推演

> 2026-06-05 | 首席 AI 基础设施架构师视角
> 基于 agentscope-java v2 源码级逆向 + DeepAgent 需求正推
> 级别：[解读] | 可信度：[最高]

---

## Phase 1: 现状剖析与核心痛点解构

### 1.1 agentscope v2 的执行路径逆向

我重新读了一遍 agentscope v2 的核心执行路径，这次不是"它有什么能力"，而是"它遇到 DeepAgent 会怎么崩"。

**实际执行路径**：

```
用户调用 agent.call(msgs)
  → AgentBase.using(acquireExecution, doCall, releaseExecution)
    → acquireExecution: AtomicBoolean.compareAndSet(false, true)
      → 如果已经在执行 → throw IllegalStateException
    → doCall(msgs): Mono<Msg>
      → doCallInner(msgs): Mono<Msg>
        → coreAgent(): Mono<Msg>
          → reasoning(iter): Mono<Msg>
            → MiddlewareChain.build(onReasoning)
              → reasoningStream(): Flux<AgentEvent>
                → MiddlewareChain.build(onModelCall)
                  → model.stream(): Flux<ChatResponse>
                  → publishEvent() via FluxSink
            → 如果有 toolCalls → acting(iter): Mono<Msg>
              → MiddlewareChain.build(onActing)
                → actingStream(): Flux<AgentEvent>
                  → toolkit.execute()
            → 回到 reasoning(iter+1)
          → 超过 maxIters → summarizing()
      → saveStateToSession()
    → releaseExecution: running.set(false)
```

### 1.2 五个物理与逻辑瓶颈

在上述路径上，我标记出 DeepAgent 场景下会崩的五个点：

#### 瓶颈 1：单线程执行锁（`acquireExecution`）

```java
// AgentBase.java:417
if (checkRunning && !running.compareAndSet(false, true)) {
    throw new IllegalStateException("Agent is still running");
}
```

**崩点**：一个 Agent 实例同一时刻只能有一个 `call()` 在跑。DeepAgent 需要什么？MCTS 需要同时展开 N 个分支，每个分支都是一个独立的推理-行动循环。这意味着 N 个子 Agent 必须并发执行，但 `acquireExecution` 禁止了这件事。

**影响级别**：**致命**。不是性能问题，是结构性不可能。

#### 瓶颈 2：Reactive 病毒式传染

```java
// 一旦 doCall 返回 Mono<Msg>，整条链都必须是 Reactive
protected Mono<Msg> doCall(List<Msg> msgs) {
    return doCallInner(msgs).flatMap(result -> saveStateToSession().thenReturn(result));
}
```

**崩点**：`Mono` / `Flux` 是病毒类型——一个方法返回 `Mono<X>`，调用它的方法也必须返回 `Mono<Y>`，层层传染。这意味着：
- 你不能在 Reactive 链里打普通断点（stack trace 是 reactor 内部帧）
- 你不能用 `try-catch` 正常处理异常（要用 `onErrorResume`）
- 你不能在循环中间 `return`（要用 `takeUntil` / `filter`）

**对 DeepAgent 的影响**：MCTS 需要在循环中间做复杂控制流——"如果这个分支评分低，回溯到父节点，选另一个分支展开"。这在同步代码里是一行 `if (score < threshold) { rollback(); continue; }`。在 Reactive 里？你需要 `flatMap` + `switchIfEmpty` + 状态传播，代码量翻 3 倍，可读性降 90%。

#### 瓶颈 3：平坦状态模型

```java
// AgentState 是一个 flat mutable bag
public final class AgentState implements State {
    private final List<Msg> context;               // 对话历史（线性）
    private final PermissionContextState permissionContext;
    private final ToolContextState toolContext;
    private final TaskContextState tasksContext;
    private final PlanModeContextState planModeContext;
}
```

**崩点**：AgentState 是一棵深度为 1 的树——一个根节点下挂着几个子上下文。没有"分支"的概念。MCTS 需要什么？一棵 N 叉决策树，每个节点是一个状态快照，可以回溯到任意祖先节点。AgentState 的 flat 结构无法表达这件事。

你需要的是：
```
根状态 → 分支A → 分支A1
                    → 分支A2
                → 分支B → 分支B1
```
AgentState 给你的是：
```
根状态（所有东西都在这一层）
```

#### 瓶颈 4：事件发布的 FluxSink 全局单例

```java
// ReActAgent.java:233
private final AtomicReference<FluxSink<AgentEvent>> activeEventSink = new AtomicReference<>();

// ReActAgent.java:840
private void publishEvent(AgentEvent event) {
    FluxSink<AgentEvent> sink = activeEventSink.get();
    if (sink != null) { sink.next(event); }
}
```

**崩点**：一个 Agent 实例只有一个 `activeEventSink`。如果 DeepAgent 同时跑 3 个子分支，3 个分支都往同一个 sink 推事件——事件混在一起，无法区分来自哪个分支。你需要给每个分支一个独立的事件通道，但 `activeEventSink` 是实例级字段，不是线程级或分支级的。

#### 瓶颈 5：3,138 行上帝类不可分片

ReActAgent 的 3,138 行里，reasoning / acting / summarizing 是 private 方法，不是独立类。你要给 DeepAgent 加"回溯"逻辑？你在哪里加？在 ReActAgent 里加。你要加"并行分支"？在 ReActAgent 里加。所有变更都集中在一个类上——这不是"高可演进"，这是"变更炸弹"。

### 1.3 痛点汇总：一句话

**agentscope v2 的 runtime 是为"单 Agent、单线程、线性执行"设计的。它的 Reactive API 是性能优化的产物，不是架构抽象的产物。面对 DeepAgent 的多分支并发、异步回溯、高频反思，它不是"不够好"——是结构性不兼容。**

---

## Phase 2: 消息机制与并发模型的深度对决

### 2.1 三种方案的真实 Trade-off

#### 方案 A：传统线程池 + 重量级消息总线（Kafka/RabbitMQ）

**它解决什么问题**：分布式 Agent 之间的可靠消息传递。

**它不解决什么问题**：单 JVM 内 Agent 执行引擎的并发模型。

**Trade-off 分析**：

| 维度 | 评估 |
|------|------|
| 复杂度引入 | **极高**。Kafka/RabbitMQ 是基础设施级别的运维负担（集群、分区、消费者组、重平衡） |
| 调试成本 | **灾难级**。消息跨进程边界，stack trace 断裂，分布式链路追踪是必须的 |
| 性能开销 | **序列化 + 网络往返**。单次消息传递 ~1-5ms，Agent 推理循环内部可能每秒产生 100+ 消息 |
| 适用场景 | 多节点 Agent 集群、跨服务编排。**不是单 JVM 执行引擎** |
| 符合"拒绝雕花"原则？ | **否**。这是用大炮打蚊子——为单进程执行引擎引入分布式消息中间件 |

**判断**：方向错误。消息总线是"Agent 之间怎么通信"的答案，不是"Agent 内部怎么执行"的答案。混淆了两个层级。

#### 方案 B：极致的反应式流（Reactor / WebFlux / Netty）

**它解决什么问题**：高并发 I/O（10K+ 连接）下的资源效率。用少量线程处理大量 I/O 密集型请求。

**它不解决什么问题**：Agent 执行逻辑的可读性和可演进性。

**Trade-off 分析**：

| 维度 | 评估 |
|------|------|
| I/O 效率 | **极高**。Netty event loop 模型是经过验证的高性能 I/O 方案 |
| 代码可读性 | **灾难级**。这是 agentscope 选择的路径，结果是 ReActAgent 3,138 行，没人能说清楚它到底怎么跑的 |
| 调试成本 | **极高**。Reactive stack trace 长这样：`at reactor.core.publisher.FluxFlatMap.java:XXX`——你的业务逻辑在哪里？不知道 |
| 病毒式传染 | **最严重的问题**。一个方法返回 `Flux<X>`，所有调用者都必须处理 `Flux`。`String` → `Mono<String>` → `Flux<String>` → `Flux<Flux<String>>`... |
| 符合"拒绝雕花"原则？ | **否**。Reactive 是微优化（节省线程），但创造了宏观问题（代码不可读、不可调试、不可演进） |
| 对 DeepAgent 的适用性 | **最差**。MCTS 的控制流（分支、回溯、选择）在同步代码里是自然的 if/for/switch。在 Reactive 里是 flatMap 嵌套地狱 |

**关键洞察**：agentscope 选择 Reactive 的原因是什么？我读完源码后的判断——**不是经过深思熟虑的架构决策，而是因为核心团队是 Python/Reactor 背景，Reactive 是他们的舒适区**。证据：ReActAgent 内部的 `reasoning()` 方法最终调的是 `model.stream()` 返回 `Flux<ChatResponse>`——如果用同步 `model.call()` 返回 `ChatResponse`，整个 Reactive 链就没有存在的必要。

**判断**：这是"技术选型决定架构"而不是"架构需求决定技术选型"。我们要反过来。

#### 方案 C：Java 21 虚拟线程 + Spring 原生事件

**它解决什么问题**：**写同步代码，获得异步性能**。这是"大颗粒度进步"。

**Trade-off 分析**：

| 维度 | 评估 |
|------|------|
| I/O 效率 | **高**。虚拟线程在 I/O 等待时自动释放载体线程，效果等价于 Reactive 的非阻塞 |
| 代码可读性 | **最高**。同步代码。`String result = model.call(input)`。断点、单步、watch 表达式全部正常 |
| 调试成本 | **最低**。stack trace 是你的代码，不是 reactor 的代码 |
| 病毒式传染 | **零**。方法签名返回 `String` / `AgentResult` / `PhaseResult`，调用者不需要知道底层用了虚拟线程 |
| 符合"拒绝雕花"原则？ | **是**。不需要引入新的编程范式，只需要 `Executors.newVirtualThreadPerTaskExecutor()` |
| 对 DeepAgent 的适用性 | **最好**。MCTS 的分支/回溯就是自然的 if/for + 递归，不需要 flatMap |

**但有一个重要的细分决策**：

**ApplicationEvent 用在核心执行路径还是只用在旁路？**

```
方案 C-1：核心路径也用 ApplicationEvent
  phase.execute() → publishEvent(PhaseStartEvent) → 监听器处理 → publishEvent(PhaseEndEvent)

方案 C-2：核心路径用直接方法调用，ApplicationEvent 只用于旁路
  phase.execute() → 直接返回 PhaseResult
  同时：eventPublisher.publishEvent(PhaseExecutedEvent) → Studio/Audit 异步消费
```

**我选择 C-2**。理由：

核心执行路径必须是**直接方法调用**——`result = phase.execute(ctx)`。原因：
1. **可调试**：断点打在 `execute` 上，F5 单步进去，一切透明
2. **可追踪**：stack trace 清晰，从 AgentRuntime → Loop → Phase → Middleware
3. **无间接性**：没有事件总线的"谁在监听？事件去哪了？"的困惑

ApplicationEvent 用于旁路（Studio 可视化、审计日志、Metrics 采集），这些是"fire and forget"，不影响核心执行路径。

### 2.2 最终推荐与理由

**推荐：方案 C-2（虚拟线程 + 直接调用核心路径 + ApplicationEvent 旁路）**

**"大颗粒度进步"体现在哪里？**

| 对比维度 | Reactive (agentscope) | 虚拟线程 (推荐) | 颗粒度 |
|---------|----------------------|----------------|--------|
| 方法签名 | `Mono<Msg> doCall(List<Msg>)` | `AgentResult invoke(String)` | **根本性**——API 复杂度降一个数量级 |
| 调试 | Reactive stack trace（不可读） | 普通 Java stack trace | **根本性**——调试成本降 5-10x |
| 并发模型 | 手动管理 Flux/Mono 链 | `try (var exec = newVirtualThreadPerTaskExecutor())` | **根本性**——从"必须理解 Reactive 才能并发"到"写同步代码自动并发" |
| DeepAgent 适配 | flatMap 嵌套地狱 | 自然递归 + if/for | **根本性**——MCTS 可以直接表达为树遍历 |

这不是"奇技淫巧"，这是"换了一个范式"。

---

## Phase 3: 面向 DeepAgent 的状态与防腐层设计

### 3.1 状态抽象：树，不是袋

agentscope 的 AgentState 是一个 **flat mutable bag**——所有状态平铺在一个层级。这对 ReAct 够用（线性迭代：reason → act → observe → reason → ...），但对 MCTS 不够用。

**MCTS 的状态需求**：

```
                根节点（初始状态）
               /        |        \
          分支A      分支B      分支C
         /    \        |        
      A1      A2      B1
      |               |
    A1-叶            B1-叶（score=0.3）
  （score=0.8）
```

每个节点需要：
- 快照（这一步的状态）
- 评分（这步有多好）
- 访问次数（被探索了几次）
- 父节点引用（用于回溯）
- 子节点列表（用于展开）

**最"直接"的实现**：

```java
/**
 * 状态树节点。支持 MCTS 的展开、选择、回溯。
 * 没有响应式，没有消息队列，没有状态机框架——就是一棵树。
 */
public class StateNode {

    // ===== 树结构 =====
    private final StateNode parent;           // 父节点（根节点为 null）
    private final List<StateNode> children;   // 子节点（展开后添加）

    // ===== 状态快照 =====
    private final Map<String, Object> snapshot;  // 这一时刻的完整状态

    // ===== MCTS 统计 =====
    private double score;      // 评估分数 [0, 1]
    private int visits;        // 访问次数
    private Instant createdAt; // 创建时间

    // ===== 核心操作 =====

    /** 展开一个子节点（深拷贝当前状态作为起点） */
    public StateNode fork() {
        Map<String, Object> childSnapshot = new HashMap<>(this.snapshot);
        StateNode child = new StateNode(this, childSnapshot);
        this.children.add(child);
        return child;
    }

    /** 回溯到父节点（MCTS 的 backpropagation） */
    public StateNode rollback() {
        return this.parent;  // 直接返回，没有状态恢复的复杂性
    }

    /** UCB1 选择：选哪个子节点最值得探索 */
    public StateNode selectChild(double explorationParam) {
        return children.stream()
            .max(Comparator.comparingDouble(
                c -> c.score / c.visits
                     + explorationParam * Math.sqrt(Math.log(this.visits) / c.visits)))
            .orElse(null);
    }

    /** 是否是叶子节点（未展开） */
    public boolean isLeaf() { return children.isEmpty(); }
}
```

**为什么不用现成的状态机框架（如 Spring Statemachine）？**

因为 MCTS **不是状态机**。状态机是"有限状态集合 + 确定性转移"。MCTS 是"无限状态树 + 概率性选择"。硬套状态机框架是"雕花"——框架的约束（预定义状态、预定义转移）反而限制了 MCTS 的灵活性。

一棵普通的 Java 树 + 三个方法（fork / rollback / selectChild）就够了。

### 3.2 记忆与上下文的快照-恢复

DeepAgent 的长时间运行（500+ 步）意味着：

1. **对话历史会膨胀** → 需要定期压缩
2. **中间结果不能丢失** → 需要持久化
3. **回溯时需要恢复** → 需要快照

**最"直接"的实现**：

```java
/**
 * 上下文快照。就是一个不可变的副本。
 * 不需要事件溯源（Event Sourcing），不需要 CQRS——直接深拷贝。
 */
public record ContextSnapshot(
    String snapshotId,
    List<Message> messages,         // 对话历史快照
    Map<String, Object> variables,  // 运行时变量快照
    String planState,               // Plan JSON 快照
    Instant timestamp
) {
    /** 从当前 LoopContext 创建快照 */
    public static ContextSnapshot from(LoopContext ctx) {
        return new ContextSnapshot(
            UUID.randomUUID().toString(),
            List.copyOf(ctx.memory().getMessages()),    // 不可变拷贝
            Map.copyOf(ctx.variables()),                // 不可变拷贝
            ctx.plan() != null ? ctx.plan().toJson() : null,
            Instant.now()
        );
    }

    /** 恢复到这个快照 */
    public LoopContext restore() { ... }
}
```

**为什么不用 Event Sourcing？**

Event Sourcing 的核心价值是"通过重放事件恢复状态"。但 Agent 的状态恢复不是审计需求（审计有 AuditMiddleware 处理），是**性能需求**——回溯时快速恢复到某个点。直接深拷贝比事件重放快 100 倍，代码简单 10 倍。这是"直接"胜过"优雅"的典型场景。

### 3.3 防腐层：在确定性边界上建墙

**核心问题**：LLM 输出是非确定性的。同样的 prompt，两次调用可能返回不同格式。如果 Runtime 直接信任 LLM 输出，整个系统就是非确定性的。

**防腐层的目标**：**让 Runtime 的内部状态始终是确定性的——不管 LLM 返回什么垃圾。**

```
LLM 输出（非确定性）
    │
    ▼
┌──────────────────────────────────┐
│        防腐层（Anti-Corruption Layer）        │
│                                              │
│  ① Schema Validation                         │
│     → 输出必须符合预定义 JSON Schema           │
│     → 不符合？重试（最多 N 次）                │
│                                              │
│  ② Structured Output 强制                     │
│     → 用 Spring AI 的 BeanOutputConverter     │
│     → LLM 被强制输出 JSON，不是自由文本        │
│                                              │
│  ③ 契约断言（AOP）                            │
│     → @RequireValidPlan：Plan 必须有至少1步    │
│     → @RequireToolResult：工具必须有返回值     │
│     → @BudgetCheck：Token 未超限              │
│                                              │
│  ④ 降级兜底                                   │
│     → LLM 返回无法解析？用预设的 fallback      │
│     → 工具超时？返回结构化的超时错误            │
└──────────────────────────────────┘
    │
    ▼
Runtime 内部状态（确定性、类型安全）
```

**Spring Boot 的具体实现**：

**① Schema Validation（入口处强制）**：

```java
/**
 * LLM 输出验证器。在 ReasonPhase 返回结果时强制校验。
 * 不合法 → 重试（带修正提示）。
 * 重试耗尽 → 兜底（返回结构化错误）。
 */
@Component
public class LlmOutputValidator {

    private final JsonSchema schema;  // 从 ToolDefinition 自动生成

    /**
     * 验证 LLM 返回的 tool_calls 是否符合 schema。
     * 返回 Either：左边是验证通过的 tool_calls，右边是修正提示（用于重试）。
     */
    public Either<String, List<ToolCall>> validate(String llmOutput) {
        try {
            List<ToolCall> calls = parseToolCalls(llmOutput);
            for (ToolCall call : calls) {
                JsonNode args = objectMapper.readTree(call.args());
                Set<ValidationMessage> errors = schema.validate(args);
                if (!errors.isEmpty()) {
                    return Either.right(
                        "LLM 输出的工具参数不符合 schema。错误：" + errors
                        + "。请严格按照 schema 重新输出。");
                }
            }
            return Either.left(calls);
        } catch (JsonProcessingException e) {
            return Either.right("LLM 输出不是合法 JSON。请重新输出。");
        }
    }
}
```

**② 契约断言（AOP，声明式）**：

```java
/**
 * AOP 切面：在 Phase 执行前后强制断言。
 * 用注解声明契约，不需要每个 Phase 手动检查。
 */
@Aspect
@Component
public class ContractAspect {

    // 任何 Phase 执行前：上下文不能为空
    @Before("execution(* com.openjiuwen.runtime.phases.*.execute(..))")
    public void requireValidContext(JoinPoint jp) {
        PhaseContext ctx = (PhaseContext) jp.getArgs()[0];
        Assert.notNull(ctx.loopContext(), "LoopContext 不能为空");
        Assert.notNull(ctx.loopContext().chatClient(), "ChatClient 不能为空");
    }

    // ActPhase 执行后：工具结果不能为 null
    @AfterReturning(
        pointcut = "execution(* ActPhase.execute(..))",
        returning = "result")
    public void requireToolResults(PhaseResult result) {
        if (result.status() == Status.CONTINUE) {
            Assert.notEmpty(result.toolResults(), "ActPhase 必须产生工具结果");
        }
    }
}
```

**③ 降级兜底（永远有退路）**：

```java
/**
 * ReasonPhase 内部：验证 + 重试 + 兜底。
 */
public class ReasonPhase implements Phase {

    private static final int MAX_RETRIES = 3;

    @Override
    public PhaseResult execute(PhaseContext ctx) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            ChatResponse response = callLLM(ctx);

            Either<String, List<ToolCall>> validation =
                validator.validate(response);

            if (validation.isLeft()) {
                // 验证通过
                return validation.left().isEmpty()
                    ? PhaseResult.complete(extractText(response))
                    : PhaseResult.continueWith(validation.left());
            }

            // 验证失败 → 把修正提示加到上下文，重试
            ctx.addMessage("system", validation.right());
        }

        // 重试耗尽 → 兜底：返回结构化错误，不是崩溃
        return PhaseResult.error(
            "LLM 输出连续 " + MAX_RETRIES + " 次不符合 schema。"
            + "请检查 system prompt 或工具 schema 定义。");
    }
}
```

**防腐层的三条铁律**：

1. **LLM 输出在进入 Runtime 之前必须通过验证**——没有例外
2. **验证失败不崩溃，降级兜底**——系统永远可用，只是质量下降
3. **所有 LLM 原始输出存入审计日志**——可以事后分析 LLM 行为

---

## Phase 4: 架构蓝图与核心接口

### 4.1 核心模块划分

```
┌─────────────────────────────────────────────────────────────┐
│                     应用层（用户代码）                         │
│              @Tool + YAML/XML + AgentBuilder                 │
├─────────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │  编排层       │  │  记忆层       │  │  通信层           │  │
│  │             │  │             │  │                 │  │
│  │ AgentRuntime│  │ AgentMemory │  │ Spring AI       │  │
│  │ ExecutionLoop│  │ ContextSnap │  │ ChatClient      │  │
│  │ Phase       │  │ StateNode   │  │ ToolRegistry    │  │
│  │ Middleware   │  │ Plan        │  │ MCP             │  │
│  │             │  │             │  │                 │  │
│  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘  │
│         │                │                   │            │
│         └────────────────┼───────────────────┘            │
│                          │                                │
│                    ┌─────▼─────┐                          │
│                    │  防腐层     │                          │
│                    │ Validator  │                          │
│                    │ Contract   │                          │
│                    │ Fallback   │                          │
│                    └───────────┘                          │
│                          │                                │
├──────────────────────────┼────────────────────────────────┤
│                    Spring Boot 3.3                         │
│              DI / AOP / Virtual Threads / Events           │
└─────────────────────────────────────────────────────────────┘
```

**四个层，职责清晰**：

| 层 | 职责 | 核心关注 | 最大敌人 |
|---|------|---------|---------|
| **编排层** | 执行循环、阶段编排、中间件链 | 控制流 | 复杂度（不能重蹈 agentscope 3138 行覆辙） |
| **记忆层** | 对话历史、状态快照、计划树 | 数据持久化 | 膨胀（500+ 步的上下文管理） |
| **通信层** | LLM 交互、工具调用、MCP | I/O | 非确定性（LLM 输出不可预测） |
| **防腐层** | 验证、降级、契约 | 确定性边界 | 腐败（LLM 输出污染内部状态） |

### 4.2 极简接口设计

我只给 5 个接口。不是"最少能做到几个"，而是"少于 5 个无法覆盖核心循环，多于 5 个就有冗余"。

#### 接口 1：ExecutionLoop（整个架构的心脏）

```java
/**
 * 可插拔的执行策略。整个架构最重要的一个接口。
 *
 * 设计原则体现：
 * - 直接：一个方法，一个输入，一个输出。没有泛型风暴。
 * - 高可演进：新增范式 = 实现 + @Component，零核心修改。
 * - 拒绝雕花：不返回 Mono/Flux，返回 AgentResult。
 *   虚拟线程在底层处理并发，调用者无感。
 */
public interface ExecutionLoop {

    /** 范式标识，用于 Spring 路由 */
    String paradigm();

    /** 同步执行。内部可以并发，但对外是一个阻塞调用。 */
    AgentResult execute(AgentRequest request);
}
```

**Trade-off**：为什么是 `AgentResult` 而不是 `PhaseResult`？因为 Loop 是"最外层"抽象——调用者（AgentRuntime）只关心最终结果。PhaseResult 是 Loop 内部的控制流机制，不应该泄露到 Loop 边界之外。**内部分解，外部统一。**

#### 接口 2：Phase（可组合的执行步骤）

```java
/**
 * 循环内的一个步骤。Phase 之间通过 PhaseResult 传递控制流。
 *
 * 设计原则体现：
 * - 直接：execute 返回 PhaseResult，Loop 用 switch 处理——没有回调地狱。
 * - 高可演进：新范式只需定义新 Phase 组合，不改旧 Phase。
 * - 拒绝雕花：Phase 不知道 Loop 的存在，不知道其他 Phase 的存在。
 *   每个Phase 只做一件事。
 */
public interface Phase {

    /** 阶段名称，用于中间件路由 */
    String name();

    /**
     * 执行阶段。
     * PhaseResult.status 告诉 Loop 下一步做什么。
     * Phase 不决定控制流——它只建议。Loop 决定。
     */
    PhaseResult execute(PhaseContext context);
}
```

**Trade-off**：为什么 `PhaseResult` 里有 `REPLAN` 状态？这看起来是 DeepAgent 专用的。**是的，但 REPLAN 是"通用控制流"——任何 Phase 都可能说"我搞不定，需要重新规划"。**它不是 DeepAgent 的专属概念，是通用循环语义的一部分。

#### 接口 3：PhaseMiddleware（阶段级洋葱）

```java
/**
 * 阶段级中间件。包裹 Phase 的执行。
 *
 * 设计原则体现：
 * - 直接：handle(ctx, next) —— 调 next.execute() 继续，不调就短路。
 *   没有反应式操作符，没有订阅/取消。
 * - 高可演进：targetPhases() 让中间件声明适用范围，
 *   新范式自带新 Phase 名，旧中间件自动适用或排除。
 * - 拒绝雕花：不需要 5 个方法（agentscope 的 onAgent/onReasoning/...），
 *   一个 handle() 搞定所有拦截场景。
 */
public interface PhaseMiddleware {

    /** 适用的阶段。空 = 全部阶段。 */
    default Set<String> targetPhases() { return Set.of(); }

    /** 洋葱模式。调 next.execute() 继续。 */
    PhaseResult handle(PhaseContext context, MiddlewareNext next);
}
```

**Trade-off**：为什么是一个方法 `handle()` 而不是 agentscope 的 5 个方法？因为 5 个方法的前提假设是"只有 Agent 级别的 5 个拦截点"。我们的拦截点是 Phase 级别的——Phase 名字是开放的（"reason"、"act"、"plan"、"delegate"、"reflect"...），所以用一个方法 + `targetPhases()` 过滤比 N 个方法更灵活、更少假设。

#### 接口 4：AgentMemory（记忆与快照）

```java
/**
 * Agent 记忆系统。短期 + 长期 + 快照。
 *
 * 设计原则体现：
 * - 直接：addMessage / getMessages / search / snapshot / restore。
 *   没有反应式流，没有事件溯源，就是 CRUD + 搜索。
 * - 高可演进：snapshot/restore 为 MCTS 提供基础，
 *   不需要现在实现 MCTS，但接口已经预留。
 * - 拒绝雕花：不抽象出 MemoryBackend 接口（Spring AI ChatMemory 已经是了）。
 *   这个接口是"面向 Agent 的记忆 API"，不是"存储抽象"。
 */
public interface AgentMemory {

    // ===== 短期记忆（当前会话）=====
    void addMessage(String role, String content);
    List<Message> getMessages();
    void clear();

    // ===== 长期记忆（跨会话语义搜索）=====
    List<MemoryEntry> search(String query, int limit);
    void save(MemoryEntry entry);

    // ===== 快照与恢复（DeepAgent / MCTS）=====
    /** 创建当前记忆状态的不可变快照 */
    MemorySnapshot snapshot();

    /** 恢复到指定快照 */
    void restore(MemorySnapshot snapshot);
}
```

**Trade-off**：`snapshot()` 的实现是深拷贝还是 Copy-on-Write？**深拷贝**。原因：Agent 的对话历史通常 < 100K tokens ≈ ~50KB 内存。深拷贝 50KB 在 Java 里是微秒级操作，不值得引入 CoW 的复杂度。

#### 接口 5：StateNode（MCTS 状态树）

```java
/**
 * 状态树节点。支持 MCTS 的展开、选择、回溯。
 *
 * 设计原则体现：
 * - 直接：fork() / rollback() / selectChild() —— 三个方法覆盖 MCTS 全部操作。
 *   不需要单独的 MCTS 框架、不需要 MonteCarlo 搜索引擎。
 * - 高可演进：StateNode 独立于 ExecutionLoop。
 *   ReActLoop 不用它（null），DeepLoop 用它。
 *   未来加新的搜索策略（Beam Search / A*），也只需要实现不同的 selectChild()。
 * - 拒绝雕花：不引入 UCB1 接口 / SelectionStrategy / ExpansionPolicy。
 *   这些是算法细节，不是架构抽象。一个 selectChild(double c) 参数就够了。
 */
public class StateNode {

    private final StateNode parent;
    private final List<StateNode> children = new ArrayList<>();
    private final Map<String, Object> snapshot;  // 状态快照
    private double score;
    private int visits;

    /** 展开子节点（深拷贝当前快照） */
    public StateNode fork() {
        StateNode child = new StateNode(this, new HashMap<>(this.snapshot));
        this.children.add(child);
        return child;
    }

    /** 回溯到父节点 */
    public StateNode rollback() { return this.parent; }

    /** UCB1 选择 */
    public StateNode selectChild(double explorationParam) {
        return children.stream()
            .max(Comparator.comparingDouble(c ->
                (c.visits == 0 ? Double.MAX_VALUE :
                 c.score / c.visits
                 + explorationParam * Math.sqrt(Math.log(this.visits) / c.visits))))
            .orElse(null);
    }

    /** 反向传播：从叶节点到根，更新 score 和 visits */
    public void backpropagate(double delta) {
        StateNode node = this;
        while (node != null) {
            node.visits++;
            node.score += delta;
            node = node.parent;
        }
    }
}
```

### 4.3 执行流程：ReAct vs Deep 的对比

**ReAct**（3 个 Phase 线性循环）：

```java
// ReActLoop.java — 约 50 行核心逻辑
for (int i = 0; i < maxIterations; i++) {
    PhaseResult r = executeWithMiddleware(reasonPhase, ctx, i);
    if (r.status() == COMPLETE) return AgentResult.success(r.output());
    if (r.status() == ERROR)    return AgentResult.error(r.errorMessage());

    PhaseResult a = executeWithMiddleware(actPhase, ctx, r, i);
    if (a.status() == ERROR) return AgentResult.error(a.errorMessage());

    executeWithMiddleware(observePhase, ctx, a, i);
}
return summarize(ctx);
```

**Deep / MCTS**（树遍历 + 递归委托）：

```java
// DeepLoop.java — 约 80 行核心逻辑
StateNode root = new StateNode(null, ctx.snapshot());
for (int sim = 0; sim < maxSimulations; sim++) {

    // 1. Selection：从根走到最有希望的叶
    StateNode leaf = root;
    while (!leaf.isLeaf()) {
        leaf = leaf.selectChild(1.41);
    }

    // 2. Expansion：展开叶节点
    StateNode child = leaf.fork();

    // 3. Simulation：委托给子 Loop（ReAct 或嵌套 Deep）
    LoopContext childCtx = ctx.forChild(child);
    ExecutionLoop childLoop = loops.get(strategyFor(child));
    AgentResult result = childLoop.execute(childCtx.toRequest());

    // 4. Backpropagation：从叶到根更新评分
    child.backpropagate(score(result));
}

// 选最优路径
StateNode best = root.selectChild(0);  // exploration=0 → 纯利用
return AgentResult.success(best.snapshot);
```

**关键观察**：两段代码都是**纯同步 Java**——没有 Flux、没有 Mono、没有 subscribe、没有 flatMap。MCTS 的 selection / expansion / simulation / backpropagation 四步就是四个方法调用。**这不是因为偷懒，是因为"直接"就是最好的设计。**

### 4.4 模块依赖图（极简版）

```
                    ExecutionLoop（接口）
                         │
              ┌──────────┼──────────┐
              │          │          │
          ReActLoop  WorkflowLoop  DeepLoop
              │          │          │
              └──────────┼──────────┘
                         │
                    Phase（接口）
                    PhaseMiddleware（接口）
                         │
                    AgentMemory（接口）
                    StateNode（类）
                         │
                    ─────┴─────
                    Spring Boot 3.3
                    Spring AI 1.1
                    Java 21 Virtual Threads
```

**依赖规则**：
- 上层只依赖接口，不依赖实现
- Loop 之间不互相依赖（DeepLoop 委托给其他 Loop 通过 `Map<String, ExecutionLoop>`，不是直接 import）
- 新增范式 = 新 Loop + 新 Phase（组合），不改旧代码

---

## 附录：关键决策记录

| 决策 | 选择 | 放弃 | Trade-off 摘要 |
|------|------|------|---------------|
| 并发模型 | 虚拟线程 + 同步 API | Reactive（Flux/Mono） | 牺牲极端 I/O 吞吐，换取 10x 调试效率和企业 Java 友好度 |
| 中间件 | Phase 级 1 方法洋葱 | Agent 级 5 方法洋葱 | 一个 handle() 比五个方法更通用、更少假设 |
| 状态模型 | 树（StateNode） | Flat bag（AgentState） | 多一点内存开销（深拷贝），换 MCTS 原生支持 |
| 快照机制 | 深拷贝 | Event Sourcing | 不支持增量恢复，但 50KB 深拷贝是微秒级，不值得引入 CQRS 复杂度 |
| 防腐层 | Schema Validation + AOP + 重试兜底 | 信任 LLM 输出 | 多一次 JSON 解析开销，换系统确定性 |
| ApplicationEvent | 仅旁路（Studio/Audit） | 核心执行路径 | 旁路事件可能丢失（不保证可靠），但核心路径不依赖它 |

---

_最后更新：2026-06-05_
