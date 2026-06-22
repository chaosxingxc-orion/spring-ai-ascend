---
artifact_type: delivery_projection
version: agent-bus-stage15-review-and-stage16-plan
status: stage-16-completed
source_commit: bff2371d
source_stage15_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage14-review-and-stage15-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_transport_candidates: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_l2: architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md
target_module: agent-bus
---

# agent-bus Stage 15 评审与 Stage 16 计划（断路器接入 worker：`ForwardingCircuitBreaker` 生产化补全）

## 0. 结论

提交 `bff2371d` 可以作为 Stage 15 的阶段性成果接受：真实投递绑定 PoC 落地 —— `A2aForwardingDeliveryPort implements ForwardingDeliveryPort`（A2A SDK 圈进 `transport.a2a` 子包）消费 agent-runtime `/a2a` JSON-RPC 端点 + `ForwardingEndpointResolver` 注入端口解开 routeHandle opaque（HD4 不破）+ MockWebServer 契约验证 5 场景（线上格式对称性）。**164 tests green**，ArchUnit 纯度 green，`§6.1` 第 4 项「真实投递绑定 deferred」解除、`§6.2` 不变。**接受**。

但 Stage 15 PoC 选了 **T1（同步等完成 = dispatcher-push over sync RPC）** 来证明对接可行性 —— 这恰好解除了 Stage 14 落地时**故意 defer 的** `ForwardingCircuitBreaker`：Stage 14 在端口 javadoc 写明「真实熔断需 per-route 失败率状态，且形态依赖 Stage 13 未裁决的 transport 模型 —— push（T1/T2）需 breaker 主动短路故障 route；consumer-pull（T3）天然自调速，显式 breaker 大体冗余。接入前会 bake in transport 假设」。Stage 15 选 T1（push）后，「断路器形态依赖 transport」的悬挂前提落地了。

Stage 16 = 用户在 Stage 15 收尾后选择的「agent-bus 生产化补全」方向：把 `ForwardingCircuitBreaker` 从 deferred no-op 正式接进 `ForwardingDispatcherWorker`。

**已落地**：端口加 `recordOutcome` 反馈（驱动状态机）+ 真实实现 `RouteCircuitBreaker`（CLOSED→OPEN→HALF_OPEN 三态机，纯 JDK）+ worker 7 参构造器 + `runOnce` 三处接入（顺序保证 `probeInFlight` 单探测不泄漏）。**179 tests green**（Stage 15 的 164 + 11 `RouteCircuitBreakerTest` + 4 worker 行为测试），ArchUnit 纯度 green（断路器纯 JDK，无需新豁免），`§6.2` 始终不得项不变。

**Stage 16 不裁决** Stage 13 的 push / pull / MQ 哲学张力（仍 H2/H3）—— 断路器**端口本身是 transport-agnostic 的纯 JDK 类型**（只消费 `ForwardingRouteHandle` / `ForwardingDeliveryResult`，不碰物理端点 HD4 守恒、不碰 broker）。即便最终 H2/H3 裁决选 T3（consumer-pull），接入的 breaker 也无害 —— T3 下 receiver 自调速，breaker 基本不触发。接入是安全的，不 bake in 任何 push / pull 假设，只是把 Stage 14 预留的 seam 填上。

## 1. Stage 15 评审回顾（commit `bff2371d`）

Stage 15 解除了从 Stage 8 起一直 deferred 的硬缺口：`ForwardingDeliveryPort.deliver` 的真实 transport 实现从未落地（唯一实现是 test-scope 的 `InMemoryForwardingDelivery` fake）。落地点（[`stage14-review-and-stage15-plan`](agent-bus-stage14-review-and-stage15-plan.md)）：

- **`A2aForwardingDeliveryPort`**：照搬 main 的 `A2aRemoteAgentOutboundAdapter`（per-endpoint `JSONRPCTransport` 缓存 + `sendMessageStreaming` 阻塞等终态），圈进 `transport.a2a` 子包；终态映射表（COMPLETED / INPUT_REQUIRED→`acked`；`isFinal && !COMPLETED` / `AUTH_REQUIRED`→`retry(RECEIVER_UNAVAILABLE)`；`await` 超时→`retry(DELIVERY_TIMEOUT)`；连接级错误→`retry(RECEIVER_UNAVAILABLE)`；resolver 空→`dlq(ROUTE_NOT_FOUND)`）。
- **`ForwardingEndpointResolver`**：注入端口解开 routeHandle opaque（HD4 不破），默认 `MapEndpointResolver`，生产由 Stage 3 registry 实现。
- **同步等完成语义** = Stage 13 的 T1（dispatcher-push over sync RPC，镜像 main 的 adapter）；**不裁决** T1 vs C3 异步哲学张力。
- **线上格式对称性**（核心 PoC 价值）：harness 用 A2A SDK 自身序列化器产出 SSE 帧，与 agent-runtime `A2aJsonRpcController` 逐字节一致 —— 把 PoC 从「测一个 fake」提升为「测真实协议代码」。
- **SDK 行为发现**：HTTP 4xx / 5xx 不被当错误（SDK 视非 2xx 为静默空 SSE 流 → `DELIVERY_TIMEOUT`），真 socket 断开才触发 `errorConsumer`。
- **`§6.2` 守恒**：`forwarding_core_does_not_import_a2a_outside_transport_adapter` ArchUnit + 文本扫描排除 `transport/a2a` 子树；`transport.a2a` 是线上格式解析器（读远程 Task 事件映射为 `ForwardingDeliveryResult`，**从不把 Task execution state 写进 record**）。

Stage 15 DoD：164 tests green（158 + 5），ArchUnit 纯度 green，`§6.2` 不变，PoC 证明对接可行。**接受**。

> Stage 15 PoC 选 T1（push）后，Stage 14 deferred 的「断路器形态依赖 transport」前提落地 —— 这正是 Stage 16 的对象。

## 2. Stage 16 范围与设计

### 2.1 为什么（生产化补全）

Stage 14 落地 retry policy 时**故意只留 `ForwardingCircuitBreaker` 端口 + `ALWAYS_CLOSED` no-op，不接入 worker** —— 理由写在端口 javadoc：真实熔断需 per-route 失败率状态，且形态依赖 transport 模型（push 需主动短路、consumer-pull 天然自调速）。接入前会 bake in transport 假设。

**Stage 15 解除了这个阻塞**：PoC 选 T1（push over sync RPC）—— push 模型 dispatcher 主动驱动投递，**需要 breaker 在故障 route 上主动短路**，否则连续 retryable 失败会轰炸下游 receiver。T1 的选择让悬挂前提落地。

**为什么不破 transport-agnostic 原则**：断路器端口本身是 transport-agnostic 的纯 JDK 类型（`allowsDelivery(routeHandle)` + `recordOutcome(routeHandle, result)`），只消费 `ForwardingRouteHandle` / `ForwardingDeliveryResult`，不碰物理端点（HD4 守恒）、不碰 broker。即便 H2/H3 最终裁决选 T3，接入的 breaker 也无害（receiver 自调速，breaker 基本不触发）。接入安全，不 bake in 任何 push / pull 假设。

### 2.2 端口扩展：`recordOutcome` 结果反馈

当前端口只有 `allowsDelivery(routeHandle)`（投递前查询）+ `ALWAYS_CLOSED` lambda no-op。加投递后反馈方法驱动状态机：

```java
public interface ForwardingCircuitBreaker {
    boolean allowsDelivery(ForwardingRouteHandle routeHandle);
    void recordOutcome(ForwardingRouteHandle routeHandle, ForwardingDeliveryResult result);
    ForwardingCircuitBreaker ALWAYS_CLOSED = new ForwardingCircuitBreaker() { /* 两方法匿名类 */ };
}
```

`ALWAYS_CLOSED` 从单方法 lambda 改成实现两方法的匿名类（lambda 无法实现多方法接口）。**源码兼容、二进制不兼容** —— `ALWAYS_CLOSED` 只在 agent-bus 内部用（worker 构造器委托默认值 + 测试），无外部消费者，安全。

### 2.3 触发分类：只有 retryable 失败驱动 breaker（breaker 内部分类）

`recordOutcome` 接收完整 `ForwardingDeliveryResult`，**分类逻辑封装在 breaker 内**（worker 只调一次，不关心怎么分类）：

| `result.outcome()` | breaker 语义 | 理由 |
|---|---|---|
| `ACKED` | **成功**（重置计数 / HALF_OPEN 探测成功 → CLOSED） | 投递到达 + 远程接收 |
| `RETRY_SCHEDULED` | **失败**（计数++ / HALF_OPEN 探测失败 → OPEN） | retryable 码（timeout / receiver_unavailable / backpressure）= route 不健康信号 |
| `DLQ` | **忽略** | non-retryable DLQ 是配置错误非 route 过载；retryable 耗尽 DLQ 的失败信号已在之前的 RETRY_SCHEDULED 记过 |
| `EXPIRED` | **忽略** | 消息自身 deadline，与 route 健康无关 |

`ForwardingDeliveryResult.retry(code)` 已在 Stage 9 classification 拒绝非 retryable 码，故 RETRY_SCHEDULED outcome 必然 retryable —— breaker 无需再查 `failureCode.retryable()`，只看 outcome。

### 2.4 真实实现：`RouteCircuitBreaker`（三态机，纯 JDK）

照 `ForwardingRetryPolicy.ExponentialBackoff` 的「注入端口 + compact constructor 验证不变量的实现」范式，落 `forwarding/runtime/RouteCircuitBreaker.java`（与 `ForwardingRetryPolicy` / `DispatchLeasePolicy` / `EpochClock` 同包同层）。注入 `EpochClock`（与 worker 续约判断同源，MI11-001）。构造参数：`failureThreshold`（`>= 1`）、`cooldownMillis`（`> 0`）、`clock`（非空）。

状态机：`CLOSED --(failureThreshold 连续失败)--> OPEN --(cooldown 到)--> HALF_OPEN`；`HALF_OPEN --(探测成功)--> CLOSED`；`HALF_OPEN --(探测失败)--> OPEN`。每路由状态 `RouteState`（`ConcurrentHashMap<String, RouteState>`，key = `routeHandle.value()`，每个 `RouteState` 用 `synchronized(routeState)` 保护）：`State state` / `int consecutiveFailures` / `long openedAtMillisEpoch` / `boolean probeInFlight`（HALF_OPEN 单探测锁）。

### 2.5 worker 接入：接入点顺序 + `probeInFlight` 不变量

worker 7 参构造器（+`ForwardingCircuitBreaker`）；6 参（Stage 14 全参）改为委托 7 参传 `ALWAYS_CLOSED`；3/4/5 参链不变。`runOnce` 三处接入，**顺序是 `probeInFlight` 不泄漏的关键**：

1. 投递前 `allowsDelivery`（**在 lease 续约检查之后、deliver 之前**）：OPEN→短路，复用现有 skip 路径（`skipped++`，留 DISPATCHING 待租约过期回收，**不消耗 attemptCount**）。
2. 投递后 `recordOutcome`（**在 switch 之前**）：使 switch 内 `markAcked` / `scheduleRetry` / `moveToDlq` / `markExpired` 的 lease-guard 异常不影响 breaker 反馈。
3. deliver 抛异常 catch 块补 `recordOutcome(retry(RECEIVER_UNAVAILABLE))`：HALF_OPEN 探测抛异常（永不返回 result）也清掉 `probeInFlight`，不卡死。

`DispatchTickResult` **不变** —— 短路复用现有 skip 路径，自洽不变量 `claimed == acked+retried+dlqd+expired+skipped` 自动保持，**无新计数器**，现有测试不破坏。

### 2.6 transport-agnostic 不破 + ArchUnit

breaker 端口只消费 `ForwardingRouteHandle` / `ForwardingDeliveryResult`（HD4 守恒），不碰 broker。`RouteCircuitBreaker` 纯 JDK（依赖 `ForwardingDeliveryResult` / `ForwardingRouteHandle` / `EpochClock` / `java.util.concurrent`），不沾 spring / jdbc / jackson / kafka / nats / netty / a2a 任何规则 —— **无需新增 ArchUnit 豁免**。worker 新增 `ForwardingCircuitBreaker` 字段同理不破。`§6.2` 文本扫描不触发（breaker 无 Task state / payload body / broker client）。

## 3. 关键不变量（`probeInFlight` 不泄漏）

`probeInFlight` 是 HALF_OPEN 单探测锁，泄漏会导致 route 永远卡在 HALF_OPEN 拒绝所有后续投递。三个 skip 路径 + 一个 lease-mutation 异常路径，任一都不泄漏：

- **renew 失败 skip**：在 `allowsDelivery` 之前 → 不碰 breaker ✓
- **breaker OPEN skip**：`allowsDelivery` 返回 false → 不设 `probeInFlight`（只有 OPEN→HALF_OPEN 转换和 HALF_OPEN 放行才设）✓
- **deliver 抛异常 skip**：`allowsDelivery` 已放行（可能设 `probeInFlight`）→ catch 块补 `recordOutcome(failure)` 清理 ✓
- **正常路径**：`allowsDelivery` → deliver → `recordOutcome`（switch 前）→ switch（即使 switch 内 lease mutation 抛异常，breaker 已收到反馈）✓

由 worker 行为测试 `thrown_half_open_probe_reopens_without_stranding` 覆盖（可切换 `throwNext` 的委托交付：探测抛异常后重新 OPEN，冷却后恢复探测 ACKED → 状态回 CLOSED，证明 `probeInFlight` 被清理）。

## 4. 切片 + MI 表（执行结果）

| MI | 切片 | 产出 | 状态 |
|---|---|---|---|
| MI16-001 | 0 端口扩展 + 治理 | `ForwardingCircuitBreaker` 加 `recordOutcome(routeHandle, result)`；`ALWAYS_CLOSED` 从 lambda 改两方法匿名类；端口 javadoc 从「deferred, not wired」更新为「Stage 16 wired」。decision §8 加 Stage 16 许可段（正向：breaker 接入 + 三态；反向：§6.2 不变；Stage 15 选 T1 解除阻塞） | ✓ |
| MI16-002 | 1 `RouteCircuitBreaker` 三态机 | `forwarding/runtime/RouteCircuitBreaker.java`（CLOSED/OPEN/HALF_OPEN + `failureThreshold` + `cooldownMillis` + 注入 `EpochClock` + per-route `ConcurrentHashMap` + `synchronized(RouteState)` + `probeInFlight` 单探测）。纯 JDK，compact constructor 验证 | ✓ |
| MI16-003 | 1 单元测试 | `RouteCircuitBreakerTest.java`（纯状态机，不接 worker）：连续失败达阈值→OPEN、冷却→HALF_OPEN、探测成功→CLOSED 清零、探测失败→OPEN 刷新冷却、ACKED 清零、DLQ/EXPIRED 忽略、per-route 状态隔离 | ✓ 11 tests |
| MI16-004 | 2 worker 接入 | `ForwardingDispatcherWorker` 7 参构造器（+`ForwardingCircuitBreaker`）；6 参委托 7 参传 `ALWAYS_CLOSED`；`runOnce` 三处接入（allowsDelivery 短路 + recordOutcome 反馈 + catch 块补 retry(RECEIVER_UNAVAILABLE)） | ✓ |
| MI16-005 | 2 worker 行为测试 | `AgentBusForwardingRuntimeContractTest` 加 Stage 16 节：连续 retryable 失败触发短路（后续 skip 不 deliver）、短路 skip 留 DISPATCHING 不消耗 attemptCount、冷却后半开放行一条探测、探测成功恢复 CLOSED、deliver 抛异常时 breaker 计入失败 | ✓ 4 tests |
| MI16-006 | 3 ArchUnit + 自洽 | 现有 ArchUnit 规则 green（无需新规则，断路器纯 JDK）；`DispatchTickResult` 自洽不变量保持；§6.2 文本扫描不触发 | ✓ |
| — | 4 文档同步 | decision §8（Stage 16 bullet + 更新 Stage 14/15 deferred 标注）+ ICD（边界标题 + Stage 16 边界条 + Open Issues 熔断行）+ yaml（`stage16_scope` + 顶部 description）+ L2 `forwarding-persistence §17` + 更新 §15.3 + L1 README/physical + 本双语文档 | ✓ |
| — | 5 构建验证 + 提交 | `mvn -f .../agent-bus/pom.xml test`；179 tests green；ArchUnit green；commit + push（experimental） | 见 slice 5 |

## 5. deferred（明示边界）

- **不裁决 T1 vs C3 异步的哲学张力**：Stage 16 接入 breaker 的正当性来自 Stage 15 PoC 选 T1（push 需主动短路），但**不裁决** Stage 13 的 push / pull / MQ 最终模型（仍 H2/H3）。breaker 端口 transport-agnostic，即便最终选 T3 也无害。
- **per-route 状态非持久化**：`RouteCircuitBreaker` 状态在内存（`ConcurrentHashMap`），进程重启丢失（回到 CLOSED）。生产化若需跨重启保持 OPEN 状态，需把 breaker 状态持久化 —— deferred。
- **breaker 参数调优 deferred**：`failureThreshold` / `cooldownMillis` 的生产默认值、是否 per-route 配置、HALF_OPEN 探测数（当前单探测）—— 均生产化时定。
- **多 worker 实例共享单例 breaker deferred**：单 `RouteCircuitBreaker` 实例跨多 worker 线程共享时 `synchronized(RouteState)` 保证安全；多 worker 实例各自持有独立 breaker 会导致 per-instance 状态分裂 —— 生产部署需共享单例 breaker，deferred。
- **沿用 Stage 15 的 deferred**：真实 agent-runtime 端到端拉起验证、`REMOTE_TASK_FAILED` non-retryable 码、registry 集成的 resolver 生产实现、连接池治理、真实 scheduler / polling、push / pull / MQ 最终裁决 —— 均 H2/H3 后。
- **§6.2 守恒验证**：grep 确认 `RouteCircuitBreaker` 无 Task state / payload body / concrete broker client（Kafka / RabbitMQ / NATS）；纯 JDK。

## 相关文档

- Stage 13 transport 投递模型候选评审：[`agent-bus-forwarding-runtime-transport-candidates`](../review-packets/agent-bus-forwarding-runtime-transport-candidates.md)（T1-T4 × 8 维度，T3 consumer-pull over DB 非裁决推荐）。
- C3 裁决：[`agent-bus-forwarding-runtime-decision`](../review-packets/agent-bus-forwarding-runtime-decision.md)（`adopted-c3`；§4 Stage 16 许可段、§8 Stage 16 条）。
- runtime 契约：[`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)（Stage 16 边界条 + Open Issues）。
- 持久化 L2：[`forwarding-persistence §17`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)（Stage 16 决策：断路器接入 worker）。
- Stage 15 计划：[`stage14-review-and-stage15-plan`](agent-bus-stage14-review-and-stage15-plan.md)（真实投递绑定 PoC）。
