---
level: L2-LLD
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-017
status: design_accepted
implementation: pending
dependency:
  - ../../L0-Top-Level-Design/boundaries.md
  - ../../L0-Top-Level-Design/constraints.md
  - ../../L0-Top-Level-Design/glossary.md
  - ../../L1-High-Level-Design/agent-runtime/README.md
  - ../../L1-High-Level-Design/agent-runtime/logical.md
  - ../../L1-High-Level-Design/agent-runtime/process.md
  - ../../L1-High-Level-Design/agent-bus/README.md
  - ../../L1-High-Level-Design/agent-bus/logical.md
  - ../../L1-High-Level-Design/agent-bus/process.md
  - ../../../version-scope/FEAT-011-client-invocation-route-forwarding.md
  - ../../../version-scope/FEAT-012-client-invocation-bus-forwarding.md
  - ../../../version-scope/FEAT-013-client-invocation-event-forwarding.md
  - ../../../version-scope/FEAT-014-a2a-call-event-forwarding.md
  - ../../../version-scope/FEAT-017-bus-event-subscription-consumption.md
---

# 订阅消费总线事件消息设计文档

> 特性编号：FEAT-017
>
> 目标模块：`agent-runtime`
>
> 最后更新：2026-07-20
>
> 当前状态：设计已接受，生产代码待落地

---

## 1. 概述

### 1.1 特性定位

FEAT-017 为 `agent-runtime` 增加嵌入式总线事件消费入口。目标 runtime 从 `agent-bus` 接收客户端调用事件和服务间 A2A 调用事件，把事件映射到现有 A2A `RequestHandler` / Task 控制面，并向总线发布接受、拒绝、失败、响应、等待输入、流准备和终态投影。

本设计不增加第二套 Agent 执行协议。HTTP `/a2a` 和总线事件是两个 northbound adapter，二者在 A2A SDK `RequestHandler` 之前汇合，共享同一 `TaskStore`、A2A Task 状态机、`A2AAgentExecutor`、`ServeOrchestrator` 和 `AgentHandler`：

```text
HTTP /a2a ──> A2aJsonRpcController ─┐
                                    ├─> RequestHandler ─> TaskStore / QueueManager
agent-bus ──> RuntimeBusEventAdapter ┘              └─> A2AAgentExecutor
                                                        └─> ServeOrchestrator
                                                              └─> AgentHandler
                  │
                  └─> RuntimeBusResponsePublisher ─> agent-bus
```

总线只承载控制事件、状态投影和数据引用。token-by-token chunk、SSE frame、大对象正文和 Task execution state 不进入总线；实时输出继续通过 A2A SSE 获取。

### 1.2 当前事实与目标设计

FEAT-017 的代码实施基线和目标代码仓均为 `agent-solution`，预期落点位于 `common/agent-runtime-ext-java`。`spring-ai-ascend` 在本特性中只保存 version-scope 与 L0/L1/L2 架构文档；其中存在的同名 runtime 或 bus 验证代码不属于本特性的当前生产实现事实，不作为类名、包结构或完成度判断依据。

`agent-solution/common/agent-runtime-ext-java` 是 `agent-runtime-java` 的扩展层。它当前通过 Maven 依赖使用 `com.openjiuwen:agent-service-spec`、`agent-service-app` 和 `agent-service-adapters-agentcore` 0.1.0，因此 A2A Task 控制面由上游 `agent-runtime-java` 提供，FEAT-017 在 `agent-solution` 中基于这些公开类型和 Spring 扩展接缝完成接入，不复制或修改上游 Task 状态机。

| 范围 | `agent-solution` 当前代码事实 | FEAT-017 目标设计 |
|---|---|---|
| 代码位置 | 已有 `common/agent-runtime-ext-java` 聚合工程，包含 AgentCore 增强与 Versatile adapter | 在该扩展工程下新增 `agent-service-bus/agent-service-bus-event-consumer` 模块；不把生产代码放入 `spring-ai-ascend` |
| 上游 runtime 依赖 | 通过 0.1.0 依赖消费 `agent-service-spec`、`agent-service-app`、`agent-service-adapters-agentcore` | 复用上游 A2A `RequestHandler`、`TaskStore` 和标准 Task 生命周期；如公开扩展面不足，先形成明确的上游接口诉求，不复制内部实现 |
| Agent 执行接入 | `JiuwenCoreAgentExtHandler` 继承上游 `JiuwenCoreAgentHandler`；另有 `VersatileAgentHandler` adapter | 总线入口进入同一 Serve/A2A 执行链，保持现有 handler 对事件来源无感知 |
| Spring 装配 | 已有 `AgentCoreExtAutoConfiguration`、`VersatileAutoConfiguration` 和 AutoConfiguration imports | 新增独立的 bus consumer auto-configuration，按配置和所需端口条件激活，不侵入现有 adapter 装配 |
| 总线消费 | `agent-solution` 当前没有 bus 请求事件订阅、信封校验或消费确认实现 | 增加嵌入式订阅端口、信封校验、事件分发和 `ACK_CONSUMED` / `ACK_REJECTED` / `RETRY` 裁决 |
| 响应投影 | 当前没有 `INVOCATION_*` / `A2A_CALL_*` 状态投影发布实现 | 增加 Task 投影协调、可靠交接日志和响应发布端口 |
| 幂等与恢复 | 当前没有 bus receipt、Task admission 或响应投影幂等存储 | 对接 agent-bus inbox 去重，并增加 Task admission 与响应投影两层 runtime 幂等记录 |
| broker 接线 | 当前没有 broker client，也没有 topic/offset/consumer group 配置 | runtime 扩展保持产品无关；物理 broker adapter 由独立 integration/host assembly 装配 |

因此，本文后续出现的 `RuntimeBus*`、`Bus*` 和 `ProjectingTaskStore` 均是 `agent-solution` 的目标设计类型，不是 `spring-ai-ascend` 中已有类，也不是当前已运行能力；其与上游 runtime 的具体方法签名必须以 `agent-runtime-java` 0.1.0 对外 API 和最终编译验证为准。

### 1.3 设计目标

1. 消费 `CLIENT_*_REQUESTED` 与 `A2A_*_REQUESTED` 两族请求事件。
2. 复用标准 A2A Task 控制面，保持 HTTP 与 bus 两种入口行为一致。
3. 在 Task 创建或复用后尽快确认消费，不用 broker delivery 等待 Agent 终态。
4. 分离 bus 消息去重、Task 创建幂等和响应投影幂等三种语义。
5. 强制 tenant、target、deadline、schema 和 payload 边界校验。
6. 用 `taskId + streamRef` 暴露流准备事实，实时流仍走 A2A SSE。
7. 使 runtime 领域代码不依赖 RocketMQ、Kafka、topic、offset、consumer group 或 agent-bus 表结构。

### 1.4 显式排除

| 排除项 | 边界归属 |
|---|---|
| Gateway 路由、`clientInvocationId` 投影和 UNKNOWN 等待窗口 | FEAT-011 / FEAT-012 |
| event-bus 转发、outbox/inbox、broker relay、retry、DLQ/replay | FEAT-013 / FEAT-014 与 `agent-bus` L2 |
| 调用方 runtime 消费 `A2A_CALL_*` 响应并回灌父 Task/tool call | FEAT-004/005 及后续出站编排设计 |
| Agent Card、能力发现、版本和健康选择 | registry-discovery-center |
| token 流转发或长期 token 重放 | A2A SSE 与调用方重连策略 |
| 独立 sidecar、外部 worker 或专用 consumer 服务 | 当前版本不采用 |
| broker 产品参数成为 runtime 公共 API | 禁止 |

---

## 2. 架构与职责

### 2.1 组件关系

```text
┌────────────────────────────── agent-runtime ──────────────────────────────┐
│                                                                           │
│  RuntimeBusSubscriptionPort                                               │
│        │ delivery                                                         │
│        ▼                                                                  │
│  RuntimeBusEventConsumer                                                  │
│    ├─ BusEnvelopeValidator                                                │
│    ├─ BusPayloadResolver                                                  │
│    ├─ BusTaskAdmissionStore                                               │
│    └─ BusA2aRequestBridge ───────────────┐                                 │
│                                          ▼                                 │
│                               A2A RequestHandler                           │
│                                  ├─ TaskStore                              │
│                                  ├─ QueueManager / MainEventBus            │
│                                  └─ A2AAgentExecutor                       │
│                                          └─ ServeOrchestrator              │
│                                                  └─ AgentHandler           │
│                                          │                                 │
│                                          ▼                                 │
│                              BusTaskProjectionCoordinator                  │
│                                          │                                 │
│                              BusResponseProjectionStore                    │
│                                          │                                 │
│                              RuntimeBusResponsePublisher                   │
└──────────────────────────────────────────┼─────────────────────────────────┘
                                           ▼
                                      agent-bus
```

### 2.2 职责分配

| 组件 | 职责 | 不负责 |
|---|---|---|
| `RuntimeBusSubscriptionPort` | 启停订阅；把目标事件交给 runtime consumer；接收 ACK/RETRY 裁决 | 暴露 topic、offset、consumer group 给领域层 |
| `RuntimeBusEventConsumer` | 编排校验、幂等 admission、A2A bridge、初始投影和消费结果 | 执行业务 Agent；实现 broker retry |
| `BusEnvelopeValidator` | 校验 schema、tenant、target、deadline、事件族和 payload 描述 | 解析 broker header |
| `BusPayloadResolver` | 解析小型 inline A2A payload，或经授权解析 `payloadRef` | 拥有引用数据；绕过授权读取正文 |
| `BusTaskAdmissionStore` | 以 `(tenantId,idempotencyKey)` 保证创建类 Task admission 唯一；保存 `taskId` 与请求摘要 | 代替 bus inbox 去重 |
| `BusA2aRequestBridge` | 将事件映射到 `RequestHandler` 的 send/get/cancel/subscribe 语义 | HTTP 回环；私有 Task 状态机 |
| `BusTaskProjectionCoordinator` | 观察 A2A 返回值和 Task 状态，形成响应事件族 | 通过 bus 发布 token chunk |
| `BusResponseProjectionStore` | 保存待发布/已发布投影及稳定 eventId，支持失败补发 | 成为 Task 真相源 |
| `RuntimeBusResponsePublisher` | 把 runtime-neutral 响应投影可靠交给 agent-bus adapter | 让 runtime 感知 broker 物理确认细节 |

Task 状态观察采用目标类型 `ProjectingTaskStore` 装饰 A2A SDK 公共 `TaskStore`：委托原读写后，把发生 revision 变化的 Task 快照交给 `BusTaskProjectionCoordinator`。它不得另起 consumer 与 A2A SDK 的 `MainEventBusProcessor` 竞争同一内部队列；启动 repair scan 只补偿持久化 TaskStore 中已经存在但缺少投影的 revision。

`TaskStore` 接口本身可由扩展层实现，但上游默认 store 由 `A2AAutoConfiguration` 通过 `@ConditionalOnMissingBean` 创建。实施前必须用编译/启动 PoC 确认包装方式：优先由扩展模块提供完整的 `TaskStore` bean 并组合 delegate；若无法在不复制上游默认构造逻辑的前提下完成包装，则向上游增加 `TaskStore` customizer/decorator 扩展点。禁止依赖运行期 bean 替换或形成两个并列 `TaskStore`。

### 2.3 所有权不变量

- `agent-runtime` 是本地 Task 的唯一 owner；bus、gateway 和调用方不得通过事件写 Task 状态。
- `agent-bus` 拥有跨边界投递、receipt、retry、DLQ 和 broker 适配；runtime 只返回消费裁决。
- `TaskStore` 是 Task 快照真相源；投影存储只保存“哪些事实需要发布/已发布”，不复制完整 Task。
- handler 只接收框架无关执行上下文，不识别调用来自 HTTP 还是 bus。
- `payloadRef` 所指正文由外部数据 owner 持有，runtime 只通过授权 resolver 消费。

---

## 3. 事件契约

### 3.1 Runtime 中立信封

```java
public record AgentBusEventEnvelope(
        String schemaVersion,
        String eventType,
        String messageId,
        String tenantId,
        String sourceServiceId,
        String targetServiceId,
        String routeHandle,
        String correlationId,
        String traceId,
        String idempotencyKey,
        Instant deadline,
        String payloadContentType,
        byte[] inlinePayload,
        String payloadRef,
        Map<String, String> metadata) {}
```

该 record 表达 runtime 所需的逻辑字段，不规定 agent-bus 的线上序列化类或 broker header。adapter 必须完成线上契约到该中立信封的映射。

字段约束：

| 字段 | 创建 | 查询/取消/订阅 | 约束 |
|---|---:|---:|---|
| `schemaVersion` | 必填 | 必填 | 只接受受支持 major version |
| `messageId` | 必填 | 必填 | bus receipt 去重与审计标识 |
| `tenantId` | 必填 | 必填 | 所有查写的强制隔离维度 |
| `sourceServiceId` / `targetServiceId` | 必填 | 必填 | target 必须匹配本 runtime identity |
| `correlationId` / `traceId` | 必填 | 必填 | 响应投影原样关联；两者不要求相等 |
| `idempotencyKey` | 必填 | 建议必填 | 创建幂等键；控制请求用于审计/响应防重 |
| `deadline` | 必填 | 必填 | 接收时已过期不得创建或修改 Task |
| `inlinePayload` / `payloadRef` | 二选一 | 按事件类型 | 同时存在或同时缺失均拒绝，纯控制信号另有 schema 声明时除外 |
| `routeHandle` | 可携带 | 可携带 | 只用于目标核验/审计，不解析为公开物理 endpoint |

当前 `agent-bus` forwarding profile 对 `DATA_BEARING` envelope 强制使用 `payloadRef`，因此与仓库内默认 adapter 联调时，A2A request/response 也按引用传递；`inlinePayload` 只为 FEAT-013/014 允许的小型 payload 兼容面和其他合规 adapter 保留，不能用于绕过当前 forwarding 的 data-reference 约束。

### 3.2 入站事件矩阵

| 入站事件 | A2A 控制面映射 | 必要 payload | 是否创建 Task | 成功投影 |
|---|---|---|---:|---|
| `CLIENT_INVOCATION_REQUESTED` | `onMessageSend` / `onMessageSendStream` | `MessageSendParams` 或 A2A JSON-RPC request | 是/推进已有 Task | `INVOCATION_ACCEPTED`，可追加 RESPONSE/STREAM_READY |
| `CLIENT_INVOCATION_QUERY_REQUESTED` | `onGetTask` | `taskId` + query params | 否 | `INVOCATION_RESPONSE` |
| `CLIENT_INVOCATION_CANCEL_REQUESTED` | `onCancelTask` | `taskId` | 否 | `INVOCATION_RESPONSE`，随后 TERMINAL |
| `CLIENT_STREAM_SUBSCRIBE_REQUESTED` | `onSubscribeToTask` 的可订阅性检查 | `taskId` | 否 | `INVOCATION_STREAM_READY` |
| `A2A_CALL_REQUESTED` | `onMessageSend` / `onMessageSendStream` | `MessageSendParams` 或 A2A JSON-RPC request | 是/推进已有 Task | `A2A_CALL_ACCEPTED`，可追加 RESPONSE/STREAM_READY |
| `A2A_CALL_QUERY_REQUESTED` | `onGetTask` | 远端 `taskId` + query params | 否 | `A2A_CALL_RESPONSE` |
| `A2A_CALL_CANCEL_REQUESTED` | `onCancelTask` | 远端 `taskId` | 否 | `A2A_CALL_RESPONSE`，随后 TERMINAL |
| `A2A_STREAM_SUBSCRIBE_REQUESTED` | `onSubscribeToTask` 的可订阅性检查 | 远端 `taskId` | 否 | `A2A_STREAM_READY` |

创建事件中的 payload 决定阻塞或流式 send 语义；事件名称不额外复制 A2A method。查询、取消和订阅必须使用目标 runtime 的 `taskId`，不得使用 `clientInvocationId`、父 Task ID、tool call ID 或 remote invocation ID 替代。

`CLIENT_INVOCATION_REQUESTED` / `A2A_CALL_REQUESTED` 也可以携带已有 `taskId` 表达标准 A2A continuation。此时 admission 使用该 taskId 建立或核对幂等记录，不分配新 Task；taskId 不存在或 tenant 不匹配时确定失败，禁止把 continuation 降级成新建请求。

### 3.3 出站投影模型

| 语义 | 客户端事件族 | 服务间 A2A 事件族 |
|---|---|---|
| 接受 | `INVOCATION_ACCEPTED` | `A2A_CALL_ACCEPTED` |
| 未创建 Task 的拒绝 | `INVOCATION_REJECTED` | `A2A_CALL_REJECTED` |
| 确定失败 | `INVOCATION_FAILED` | `A2A_CALL_FAILED` |
| 一次性响应/Task 快照 | `INVOCATION_RESPONSE` | `A2A_CALL_RESPONSE` |
| 等待输入 | `INVOCATION_INPUT_REQUIRED` | `A2A_CALL_INPUT_REQUIRED` |
| 流准备 | `INVOCATION_STREAM_READY` | `A2A_STREAM_READY` |
| 结果性终态 | `INVOCATION_TERMINAL` | `A2A_CALL_TERMINAL` |

```java
public record RuntimeBusResponseEvent(
        String schemaVersion,
        String eventId,
        String eventType,
        String tenantId,
        String sourceServiceId,
        String targetServiceId,
        String causationMessageId,
        String correlationId,
        String traceId,
        String taskId,
        String streamRef,
        String payloadRef,
        Map<String, Object> inlinePayload,
        RuntimeBusError error,
        Instant occurredAt) {}
```

| 投影后缀 | 触发条件 | `taskId` | 关键字段 |
|---|---|---:|---|
| `ACCEPTED` | Task 已创建或按幂等键复用 | 必填 | `idempotencyResult=CREATED/REUSED` |
| `REJECTED` | admission 前发生确定性拒绝，未创建 Task | 禁止伪造 | 拒绝码、不可重试语义 |
| `FAILED` | payload/内部处理发生确定失败 | 有则携带 | 错误码、`retryable` |
| `RESPONSE` | 阻塞窗口内得到 A2A response，或控制请求返回 Task 快照 | 适用时必填 | 小响应内联，大响应使用 `payloadRef` |
| `INPUT_REQUIRED` | Task 进入 `INPUT_REQUIRED` | 必填 | 输入描述、恢复上下文引用 |
| `STREAM_READY` | 已有 Task 的 A2A SSE 可订阅 | 必填 | `streamRef` 必填 |
| `TERMINAL` | Task 进入 COMPLETED/FAILED/CANCELED/REJECTED 结果态 | 必填 | terminal state、错误/结果引用 |

客户端来源使用 `INVOCATION_*`，A2A 来源使用 `A2A_CALL_*`；`STREAM_READY` 对应名称分别为 `INVOCATION_STREAM_READY` 和 `A2A_STREAM_READY`。投影器必须从 admission 记录保存的 source family 恢复事件族，禁止按当前消费者猜测。

### 3.4 `streamRef` 设计

`streamRef` 是不可读、可过期、受 tenant 约束的逻辑引用：

```text
streamRef = base64url(version + keyId + opaqueId + expiry + signature)
```

- 不编码 host、port、topic、partition 或公开 endpoint。
- resolver 必须同时校验 `tenantId + taskId + expiry`。
- `streamRef` 只表示流可订阅，不表示执行完成或已经产生 token。
- gateway/调用方用 `taskId + streamRef` 解析内部订阅条件，再建立 A2A SSE。
- 引用失效时允许重新发送 `*_STREAM_SUBSCRIBE_REQUESTED` 获取新引用，不重建 Task。

---

## 4. 核心接口设计

### 4.1 订阅与消费端口

```java
public interface RuntimeBusSubscriptionPort {
    void start(RuntimeBusDeliveryHandler handler);
    void stop();
}

@FunctionalInterface
public interface RuntimeBusDeliveryHandler {
    CompletionStage<RuntimeBusConsumeResult> onDelivery(AgentBusEventEnvelope envelope);
}

public record RuntimeBusConsumeResult(
        Disposition disposition,
        String failureCode,
        boolean retryable) {
    public enum Disposition { ACK_CONSUMED, ACK_REJECTED, RETRY }
}
```

两种 `ACK_*` 都表示该消息不应因 Agent 长时间执行而继续占用 broker delivery；adapter 分别将其映射为 inbox `CONSUMED` 与 `REJECTED`：

- Task 已可靠进入控制面；或
- 请求已被确定性拒绝，拒绝/失败投影已可靠记录；或
- 重复消息已被安全抑制并可恢复等价投影。

`RETRY` 只用于进入上述稳定边界之前的瞬时故障，例如 admission store 不可用、payloadRef 暂时不可取、投影记录无法持久化。业务 Task 终态失败不是消息投递重试理由。

目标服务身份使用本特性新增的窄 SPI：

```java
public interface RuntimeBusTargetIdentity {
    String serviceId();
}
```

默认实现从必填配置 `openjiuwen.service.bus.consumer-service-id` 构造。上游 `AgentServiceIdentity#getAppName()` 只承载应用展示/生命周期身份，除非部署契约明确声明 appName 与 bus serviceId 相同，否则不得隐式用它替代 bus 目标身份；需要统一时由 host 显式提供 `RuntimeBusTargetIdentity` bean。

### 4.2 A2A 控制面桥

```java
public interface BusA2aRequestBridge {
    BusDispatchResult send(BusRequestContext context, MessageSendParams params, boolean streaming);
    BusDispatchResult getTask(BusRequestContext context, TaskQueryParams params);
    BusDispatchResult cancelTask(BusRequestContext context, CancelTaskParams params);
    BusDispatchResult subscribeTask(BusRequestContext context, TaskIdParams params);
}
```

默认实现 `RequestHandlerBusA2aBridge`：

1. 构造 `ServerCallContext`，写入 `tenantId`、`correlationId`、`traceId`、source 和 deadline。
2. 直接调用 A2A SDK `RequestHandler` 公共方法，不调用本机 `/a2a`，也不把 Task 协议操作降级成 `ServeOrchestrator` 的会话操作。
3. 把 A2A response / publisher 首个可观察状态规范化为 `BusDispatchResult`。
4. 不直接调用 `AgentHandler` 或 `ServeOrchestrator`，避免绕过 Task 创建、TaskStore、Task 查询和 Task 取消语义。
5. 对 subscribe 只完成 Task/stream 可订阅性校验和 streamRef 准备，不订阅、缓存或转发 publisher 中的 SSE frame；真正的流消费发生在调用方持有 SSE 连接时。

| Bridge 操作 | A2A SDK 1.0.0.Final `RequestHandler` 映射 | 当前 HTTP `/a2a` 暴露情况 |
|---|---|---|
| 非流式 send | `onMessageSend(MessageSendParams, ServerCallContext)` | 已暴露 |
| 流式 send | `onMessageSendStream(MessageSendParams, ServerCallContext)` | 已暴露 |
| get task | `onGetTask(TaskQueryParams, ServerCallContext)` | 已暴露 |
| cancel task | `onCancelTask(CancelTaskParams, ServerCallContext)` | SDK/bean 已具备，当前 controller 未分发 |
| subscribe task | `onSubscribeToTask(TaskIdParams, ServerCallContext)` | SDK/bean 已具备，当前 controller 未分发 |

因此本文的“一致性”是与 A2A SDK Task 语义和同一个 `RequestHandler` bean 一致，不表示当前 HTTP controller 已暴露全部五种方法。`ServeOrchestrator.query/streamQuery/cancelActive/resetConversation` 是协议中立的 Agent/会话编排面，不能替代 `GetTask`、`CancelTask` 和 `SubscribeToTask`。

### 4.3 Task admission 幂等端口

```java
public interface BusTaskAdmissionStore {
    Admission reserve(String tenantId, String idempotencyKey,
                      String requestDigest, String allocatedTaskId,
                      String sourceFamily, String correlationId);
    void markAdmitted(String tenantId, String idempotencyKey, String taskId);
    Optional<Admission> find(String tenantId, String idempotencyKey);
}
```

唯一键是 `(tenantId,idempotencyKey)`。`requestDigest` 防止同一 key 被不同请求复用；冲突返回 `IDEMPOTENCY_KEY_CONFLICT`，不得把旧 Task 当成本次成功。

新建 Task 时先生成稳定 `taskId` 并写入 reservation，再把该 ID 传给内部 A2A send。这样即使进程在 Task 创建后、`markAdmitted` 前退出，恢复过程仍可按 reservation 中的 `taskId` 查询 TaskStore，而不是创建第二个 Task。

查询、取消和订阅事件不使用 admission store 创建 Task，只以 `(tenantId,taskId)` 查目标 Task。它们的响应事件仍按 `messageId/eventId` 做投影防重。

### 4.4 响应投影存储与发布

```java
public interface BusResponseProjectionStore {
    ProjectionAppendResult append(RuntimeBusResponseEvent event);
    List<RuntimeBusResponseEvent> claimPending(int limit, Instant now);
    void markPublished(String tenantId, String eventId, Instant publishedAt);
    void scheduleRetry(String tenantId, String eventId, Instant nextAttemptAt, String failureCode);
}

public interface RuntimeBusResponsePublisher {
    CompletionStage<PublishResult> publish(RuntimeBusResponseEvent event);
}
```

稳定 `eventId` 使用以下逻辑键生成：

```text
hash(tenantId, causationMessageId, taskId-or-none, projectionKind, projectionRevision)
```

同一事实重放得到相同 eventId；`append` 必须幂等。Task 状态发生新 revision 时产生新的投影，重复观察同 revision 不重复产生可见副作用。

投影发布失败不回滚已创建 Task。请求 consumer 在投影已写入 `BusResponseProjectionStore` 后可 ACK；独立的 `BusResponseRelay` 重试发布。该存储是 runtime 的投影交接日志，不暴露或复制 agent-bus outbox/inbox 物理模型。

生产可靠性 profile 下，`TaskStore`、`BusTaskAdmissionStore` 和 `BusResponseProjectionStore` 都必须跨进程重启持久化：admission store 提供唯一约束/CAS，projection store 提供 claim 与幂等 append。三者不要求共享物理数据库；稳定 taskId 与 repair 流程用于闭合跨存储崩溃窗口。in-memory 实现只允许 contract test 和显式本地开发 profile，不能作为 FEAT-017 可靠消费验收证据。若 publisher 自身能证明“返回成功即进入 agent-bus durable handoff”，projection store 可以由该 adapter 组合实现，但 runtime 侧端口语义不变。

---

## 5. 运行流程

### 5.1 创建类调用

```text
agent-bus       consumer       validator/admission      RequestHandler      projection
    │               │                   │                    │                 │
    │ REQUESTED     │                   │                    │                 │
    ├──────────────>│ validate/resolve  │                    │                 │
    │               ├──────────────────>│ reserve(taskId)    │                 │
    │               │                   ├───────────────────>│ send            │
    │               │                   │                    ├─ create/reuse Task
    │               │                   │<───────────────────┤ taskId/state    │
    │               │                   ├─────────────────────────────────────>│ append ACCEPTED
    │<── ACK ───────┤                   │                    │                 │
    │               │                   │                    │                 ├─ publish ACCEPTED
    │               │                   │                    │                 ├─ later INPUT_REQUIRED/
    │               │                   │                    │                 │  STREAM_READY/TERMINAL
```

处理步骤：

1. 校验信封、target、tenant、deadline 和 payload 描述。
2. 解析 inline payload 或 `payloadRef`，验证其 A2A method 与事件类型兼容。
3. 对创建类请求按 `(tenantId,idempotencyKey)` reserve；重复请求复用 reservation 的 `taskId`。
   对携带已有 taskId 的 continuation，reservation 绑定并核对该 taskId，不另行分配。
4. 经 `RequestHandlerBusA2aBridge` 进入标准 A2A Task 控制面。
5. 创建成功或复用后，先持久化 `*_ACCEPTED` 投影，再返回 ACK。
6. 阻塞窗口内完成则追加 `*_RESPONSE`；流可订阅则追加 `*_STREAM_READY`。
7. Task 后续进入 INPUT_REQUIRED 或终态时，由 projection coordinator 发布对应投影，不再占用原消息 delivery。

### 5.2 查询、取消与重新订阅

```text
REQUESTED(taskId)
  -> 校验 tenant/target/deadline
  -> 按 tenantId + taskId 进入 RequestHandler
       query      -> RESPONSE(Task snapshot)
       cancel     -> RESPONSE + later TERMINAL(CANCELED)
       subscribe  -> STREAM_READY(taskId, new-or-current streamRef)
  -> 投影可靠记录
  -> ACK
```

- Task 不存在时发布确定性 `*_FAILED(TASK_NOT_FOUND,retryable=false)` 并 ACK。
- tenant 不匹配的响应与 Task 不存在使用同一外部错误表面，日志和审计保留内部原因，避免跨租户枚举。
- subscribe 不启动新 Task；终态 Task 是否仍允许读取历史 SSE 由现有 A2A 能力决定，不支持时返回 `STREAM_NOT_AVAILABLE`。

### 5.3 重复投递与崩溃恢复

| 恢复点 | 重放行为 |
|---|---|
| admission 前崩溃 | bus 重投，重新校验并 reserve |
| reservation 后、Task 创建前崩溃 | 使用 reservation 的稳定 taskId 重新进入控制面 |
| Task 创建后、markAdmitted 前崩溃 | 按稳定 taskId 查询并补齐 admission，不创建第二个 Task |
| ACCEPTED 投影 append 后、发布前崩溃 | consumer 可 ACK；response relay 按稳定 eventId 补发 |
| 投影发布后、markPublished 前崩溃 | bus 侧按 eventId 幂等抑制，relay 重发不产生重复可见事实 |
| Task 终态后 projection observer 崩溃 | 启动 repair scan 比对 Task snapshot 与投影 revision，补发终态 |

### 5.4 ACK 边界

| 处理结果 | 消费裁决 | 原因 |
|---|---|---|
| Task 已创建/复用且 ACCEPTED 已记录 | `ACK_CONSUMED` | 后续执行与原 delivery 解耦 |
| 非创建控制请求已处理且响应投影已记录 | `ACK_CONSUMED` | 已形成稳定结果 |
| 信封非法、过期、tenant/target 不匹配 | `ACK_REJECTED` | 确定性失败，重投无意义；字段可信且足以关联时记录失败投影 |
| bus inbox 已判定重复，runtime 可恢复等价投影 | `ACK_CONSUMED` | 不重复副作用；adapter/inbox 仍可保留 `DUPLICATE_SUPPRESSED` 审计状态 |
| admission/payload resolver/projection store 瞬时不可用 | RETRY | 尚未进入稳定接收边界 |
| Agent 执行失败或进入 INPUT_REQUIRED | 不改变原 `ACK_CONSUMED` | 属于 Task 生命周期，不是投递失败 |

---

## 6. 状态与幂等设计

### 6.1 三层幂等键

| 层次 | 键 | 防止的问题 | owner |
|---|---|---|---|
| bus delivery | `(tenantId,messageId,consumerServiceId)` | 同一 delivery 被同一 consumer 重复消费 | agent-bus inbox |
| Task admission | `(tenantId,idempotencyKey)` | 重试/不同 messageId 创建多个逻辑 Task | agent-runtime |
| response projection | `(tenantId,eventId)` | 投影 relay 重试产生重复可见事件 | runtime publisher + agent-bus consumer |

`clientInvocationId` 是 gateway 侧关联句柄，`correlationId` 是链路关联字段，二者都不是上述唯一键；`taskId` 是 Task owner 返回的标准查询/取消/订阅标识。

### 6.2 Admission 状态机

```text
                requestDigest conflict
          ┌────────────────────────────> CONFLICT
          │
ABSENT ─reserve─> RESERVED ─dispatch─> ADMITTED
                     │                    │
                     └── deterministic ─> REJECTED
                         pre-admission
```

- `RESERVED` 必须保存稳定 taskId、source family、correlation、request digest 和创建时间。
- `ADMITTED` 表示 Task 已存在或已被控制面可靠接受，不表示 Agent 已完成。
- 只有 admission 前的确定拒绝进入 `REJECTED`；已有 Task 的执行失败体现在 Task/terminal projection 中。
- 超时 `RESERVED` 不能直接删除；repairer 必须先按 taskId 查询 TaskStore，再决定重驱或补齐。

### 6.3 投影顺序

单个 `(tenantId,taskId,correlationId)` 的语义顺序为：

```text
ACCEPTED
  ├─> RESPONSE (可选)
  ├─> STREAM_READY (可选，可重新生成新 revision)
  ├─> INPUT_REQUIRED (可重复于不同 Task revision)
  └─> TERMINAL (至多一个结果性终态，且在该 correlation 下最后)
```

`REJECTED` 是未创建 Task 的结束事件，不再发布 ACCEPTED/TERMINAL。若请求已经创建或复用 Task，之后 Task 才进入 A2A `REJECTED` 状态，则仍先有 ACCEPTED，并以 `TERMINAL(state=REJECTED)` 收敛，不能倒退成“未创建”的 `*_REJECTED`。`FAILED` 若发生在 admission 前是结束事件；若 Task 已创建，必须携带 taskId，并最终由 TERMINAL 收敛 Task 结果。

---

## 7. 校验、错误与安全

### 7.1 校验顺序

1. schema version 与 event type allowlist。
2. 必填字段、长度、字符集和 metadata 大小。
3. `targetServiceId == RuntimeBusTargetIdentity.serviceId()`，route tenant scope 与 `tenantId` 一致。
4. deadline 未过期，并限制过远 deadline，防止无限资源占用。
5. payload inline/ref 互斥、content type 和最大 inline bytes。
6. payloadRef 授权范围绑定 tenant/source/target，解析结果做摘要校验。
7. A2A method 与外层 event type 相容。
8. Task 操作的 `(tenantId,taskId)` 所有权。

信封若缺少可信 tenant/source/correlation，或 transport identity 校验失败，runtime 不尝试构造可能泄露信息的响应投影，直接返回 `ACK_REJECTED` 并由 adapter 记录受限审计；字段完整且可信的确定性错误才发布 `*_REJECTED` / `*_FAILED`。

### 7.2 错误码

| 错误码 | retryable | 投影 | 说明 |
|---|---:|---|---|
| `UNSUPPORTED_SCHEMA_VERSION` | false | `*_REJECTED` | 不支持的 major schema |
| `UNSUPPORTED_EVENT_TYPE` | false | `*_REJECTED` | 非 runtime 入站事件 |
| `INVALID_ENVELOPE` | false | `*_REJECTED` | 必填、格式或大小不合法 |
| `TARGET_MISMATCH` | false | `*_REJECTED` | 事件不属于本 runtime |
| `TENANT_SCOPE_VIOLATION` | false | `*_REJECTED` | tenant/route/payload scope 不一致 |
| `DEADLINE_EXCEEDED` | false | `*_FAILED` | 接收时已过期 |
| `PAYLOAD_REF_UNAVAILABLE` | true | 无稳定投影前 RETRY | 数据引用暂不可取 |
| `PAYLOAD_INVALID` | false | `*_FAILED` | A2A payload 无法解析或 method 不匹配 |
| `IDEMPOTENCY_KEY_CONFLICT` | false | `*_REJECTED` | 同 key 不同 request digest |
| `RUNTIME_NOT_READY` | true | `*_FAILED` | runtime 暂不接收执行 |
| `TASK_NOT_FOUND` | false | `*_FAILED` | 查询/取消/订阅的 Task 不存在或不可见 |
| `STREAM_NOT_AVAILABLE` | false | `*_FAILED` | Task 当前无可订阅 SSE |
| `PROJECTION_HANDOFF_FAILED` | true | RETRY | 稳定投影无法记录，尚不能 ACK |
| `INTERNAL_ERROR` | 按分类 | `*_FAILED` | 未分类内部错误，不泄露堆栈 |

### 7.3 安全约束

- 所有 store 和 resolver 方法必须显式携带 `tenantId`，禁止默认 tenant 和跨 tenant fallback。
- 响应投影不得回显 routeHandle 内部内容、物理 endpoint、broker 信息或敏感 payloadRef 凭证。
- 日志不记录 inline payload 正文、token、完整 payloadRef、签名 streamRef 或认证头。
- `streamRef` 使用可轮换 key 签名，解析失败统一返回不可用，不泄露 Task 是否存在。
- source identity 的认证由 subscription adapter 提供；runtime validator 仍校验 envelope source 与已认证 transport identity 一致。
- inline payload 和 metadata 必须配置硬大小上限，payloadRef resolver 必须有超时、内容长度和 MIME allowlist。

---

## 8. 自动装配与代码结构

### 8.1 包结构

```text
agent-solution/common/agent-runtime-ext-java/
└── agent-service-bus/agent-service-bus-event-consumer/
    └── src/main/java/com/openjiuwen/service/bus/
        ├── AgentBusEventEnvelope.java
        ├── RuntimeBusResponseEvent.java
        ├── RuntimeBusEventConsumer.java
        ├── BusEnvelopeValidator.java
        ├── BusA2aRequestBridge.java
        ├── RequestHandlerBusA2aBridge.java
        ├── ProjectingTaskStore.java
        ├── BusTaskProjectionCoordinator.java
        ├── BusResponseRelay.java
        ├── BusProjectionRepairer.java
        ├── package-info.java
        ├── spi/
        │   ├── RuntimeBusSubscriptionPort.java
        │   ├── RuntimeBusResponsePublisher.java
        │   ├── BusPayloadResolver.java
        │   ├── BusTaskAdmissionStore.java
        │   ├── BusResponseProjectionStore.java
        │   ├── RuntimeBusTargetIdentity.java
        │   └── StreamReferenceService.java
        └── autoconfigure/
            ├── RuntimeBusAutoConfiguration.java
            └── RuntimeBusProperties.java
```

broker-specific adapter 不进入上述包，也不让 `agent-bus` 生产模块反向依赖 `agent-runtime`。默认产品接线应位于独立 integration/host assembly：该装配层可以同时依赖两侧 SPI，负责把 `agent-bus` transport 适配到 runtime 的窄端口；两侧领域模块继续保持生产依赖隔离。

### 8.2 自动装配条件

`RuntimeBusAutoConfiguration` 仅在以下条件同时满足时激活：

- `openjiuwen.service.bus.enabled=true`；
- 存在且仅存在一个 `RuntimeBusSubscriptionPort`；
- 存在 `RuntimeBusResponsePublisher`、`BusTaskAdmissionStore`、`BusResponseProjectionStore` 和 `BusPayloadResolver`；
- 现有 A2A SDK `RequestHandler`、`TaskStore` 已装配；
- `openjiuwen.service.bus.consumer-service-id` 非空，并已生成或由 host 提供唯一 `RuntimeBusTargetIdentity`。

缺少必需端口时应启动失败并列出缺失 bean，不能悄悄启用只能消费不能响应的半功能。`enabled=false` 时不要求任何 bus 依赖，保持现有纯 HTTP runtime 可用。

生产 profile 还必须通过 durability capability 检查，拒绝把默认 `InMemoryTaskStore` 或 in-memory admission/projection store 用于可靠订阅。只有显式本地开发配置可以放宽该检查，并在 readiness、日志和健康端点中标记 `ephemeral_bus_state=true`。

### 8.3 配置表面

```yaml
openjiuwen:
  service:
    bus:
      enabled: false
      allow-ephemeral-state: false
      consumer-service-id: ${SERVICE_INSTANCE_ID}
      accepted-schema-major: 1
      max-inline-payload-bytes: 65536
      max-metadata-bytes: 16384
      payload-resolve-timeout: 5s
      response-relay-batch-size: 100
      response-relay-max-in-flight: 16
      repair-interval: 30s
      stream-ref-ttl: 10m
```

配置不得出现 topic、offset、partition、consumer group 或 broker retry。物理 transport 的这些参数属于 adapter 自己的命名空间。

### 8.4 生命周期

1. Spring 完成 `RequestHandler`、store 和 publisher 装配。
2. runtime readiness 进入 ready 后启动 projection relay，再启动 subscription。
3. drain 时先停止接收新 delivery，等待已进入 admission 的短临界区完成。
4. 不等待所有 Agent Task 终态；Task 按现有 runtime 生命周期继续或由部署策略处理。
5. 最后停止 response relay，并保留 pending projection 供下次启动恢复。

---

## 9. 可观测性与运行约束

### 9.1 指标

| 指标 | 类型 | 标签约束 |
|---|---|---|
| `runtime_bus_deliveries_total` | counter | event family、result；不以 tenant/messageId 作标签 |
| `runtime_bus_delivery_latency_seconds` | histogram | event family |
| `runtime_bus_admission_total` | counter | created/reused/conflict/rejected |
| `runtime_bus_projection_pending` | gauge | projection family |
| `runtime_bus_projection_publish_total` | counter | event type、result |
| `runtime_bus_projection_lag_seconds` | histogram | event type |
| `runtime_bus_payload_resolve_seconds` | histogram | inline/ref、result |
| `runtime_bus_repair_total` | counter | admission/projection、result |

tenant、messageId、taskId、correlationId 只进入结构化日志或 trace attribute，不作为高基数 metric label。

### 9.2 Trace 与日志

- 消费时从 envelope 恢复 trace；无合法 trace 时创建新 trace 并记录 `trace_recreated=true`。
- span 至少包括 `bus.consume`、`payload.resolve`、`task.admit`、`a2a.bridge`、`projection.append` 和 `projection.publish`。
- 日志公共字段：tenant hash、messageId、correlationId、taskId、eventType、idempotency result、consume disposition、failureCode。
- `ACCEPTED`、`INPUT_REQUIRED`、`STREAM_READY`、`TERMINAL` 必须有审计事实，且能关联 causation message。

### 9.3 背压与并发

- subscription adapter 依据 consumer 返回的尚未完成 stage 限制 in-flight delivery；runtime 不创建无界线程或无界队列。
- 同一 `(tenantId,idempotencyKey)` 的 admission 必须串行化；不同 key 可并发。
- 同一 Task 的投影按 Task revision 有序 append；不同 Task 可并发发布。
- payload resolver、A2A bridge 和 projection publisher 使用独立 bulkhead，避免大载荷读取耗尽 Task 执行资源。
- 并发上限由 runtime-neutral 参数表达，broker prefetch/pull batch 由 adapter 自行映射。

---

## 10. 测试设计

### 10.1 单元与契约测试

| 测试组 | 必测断言 |
|---|---|
| envelope validator | schema、target、tenant、deadline、inline/ref 互斥、大小限制 |
| event mapper | 8 种请求事件映射到正确 `RequestHandler` 方法；错误 family 不混用 |
| admission store contract | 同 key 同摘要复用 taskId；同 key 不同摘要冲突；tenant 隔离；并发唯一 |
| response projection store contract | eventId 幂等；revision 有序；claim/retry/markPublished 可恢复 |
| stream reference | 不含 endpoint；tenant/task/expiry/signature 校验；key rotation |
| consume disposition | admission/投影交接后 ACK；稳定边界前瞬时失败 RETRY；不等待 Task 终态 |
| payload resolver | inline/ref、授权失败、超时、内容长度、摘要不匹配 |

所有 SPI 必须提供 in-memory contract fixture；in-memory 只作为测试替身，不作为生产可靠性证明。

### 10.2 集成测试

1. `CLIENT_INVOCATION_REQUESTED` → 真实 `RequestHandler` → Task CREATED/WORKING → `INVOCATION_ACCEPTED`。
2. `A2A_CALL_REQUESTED` → 同一 handler → `A2A_CALL_ACCEPTED`，业务 handler 无 source 分支。
3. 阻塞完成 → RESPONSE + 唯一 TERMINAL。
4. 流式调用 → ACCEPTED + STREAM_READY，捕获所有 bus publication 并断言无 token/SSE frame。
5. Task 进入 INPUT_REQUIRED → 对应 family 的 INPUT_REQUIRED 投影。
6. query/cancel/subscribe 使用真实 taskId；不存在、跨 tenant 和错误本地 ID 不创建 Task。
7. 相同 messageId 重投、不同 messageId + 相同 idempotencyKey 重试均只创建一个 Task。
8. Task 创建后模拟 publisher 失败：原 delivery ACK，relay 恢复后补发相同 eventId。
9. 在 reservation、Task 创建、投影 append 和 publish 各崩溃点重启，验证恢复表。
10. runtime drain：停止新消费，不等待长 Task 终态，不丢 pending projection。

### 10.3 跨模块验收

与 `agent-bus` 使用真实 adapter 做以下 E2E：

```text
gateway/source runtime
  -> agent-bus outbox/relay/broker/receiver
  -> RuntimeBusSubscriptionPort
  -> agent-runtime RequestHandler/TaskStore
  -> RuntimeBusResponsePublisher
  -> agent-bus
  -> gateway/source runtime projection consumer
```

必须覆盖：客户端创建/查询/取消/订阅、A2A 创建/查询/取消/订阅、重复投递、接受后长任务、INPUT_REQUIRED、流重连、终态失败、tenant 隔离、payloadRef 错误、adapter 不可用、响应重投和 DLQ 审计。跨模块测试还必须用消息体扫描断言 token chunk、SSE frame、物理 endpoint 和 Task execution state 未进入总线。

---

## 11. 需求追踪

| FEAT-017 要求 | 设计落点 | 验证 |
|---|---|---|
| 嵌入式事件订阅消费 | §2、§4.1、§8 | auto-configuration / lifecycle IT |
| 客户端与 A2A 八种请求事件 | §3.2、§5 | mapper contract + E2E |
| 标准入口语义复用 | §1.1、§4.2 | A2A `RequestHandler` semantic parity test |
| 外层信封与 A2A payload 分离 | §3.1、§7.1 | validator / payload tests |
| accepted/rejected/failed/response | §3.3、§5 | projection integration tests |
| input-required/stream-ready/terminal | §3.3、§5、§6.3 | Task transition tests |
| 稳定 streamRef | §3.4 | stream reference contract |
| ACK 到接收边界 | §4.1、§5.4 | long-running Task test |
| bus 去重与 Task 幂等分离 | §4.3、§6.1 | duplicate/concurrency tests |
| 响应发布幂等与补发 | §4.4、§5.3 | crash/retry tests |
| tenant 隔离 | §3.1、§7 | cross-tenant negative tests |
| token 流不进总线 | §1.1、§3.4、§10 | captured-message scan |
| broker 透明 | §2、§4、§8 | ArchUnit dependency rule |
| 调用方响应回灌排除 | §1.4 | architecture boundary review |

---

## 12. 实施顺序与完成条件

### 12.1 建议实施切片

1. **上游扩展面编译/启动 PoC**：以 `agent-runtime-java` 0.1.0 和 A2A SDK 1.0.0.Final 为基线，确认扩展模块可注入同一个 `RequestHandler`、`TaskStore` 与 `ServerCallContext` 相关类型；验证 `ProjectingTaskStore` 的唯一 bean 包装和自动装配顺序；确认 `RuntimeBusTargetIdentity` 的 host 覆盖方式。公开扩展面不足时先形成具体上游接口诉求，PoC 未通过不得进入后续切片。
2. 中立事件/响应模型、validator、mapper 和 SPI contract fixture。
3. `RequestHandlerBusA2aBridge`，完成八种事件与 A2A `RequestHandler` 语义一致性测试，并区分当前 HTTP controller 已暴露与仅 SDK 已具备的方法。
4. admission store 与稳定 taskId 恢复，完成创建幂等和崩溃点测试。
5. projection store、relay 和 Task projection coordinator。
6. streamRef、INPUT_REQUIRED、终态 repair。
7. Spring auto-configuration、readiness/drain、指标和 ArchUnit 规则。
8. 与 `agent-bus` adapter 联调及跨模块 E2E。

### 12.2 设计完成条件

- 不新增 bus 专用 Task 状态机或 HTTP loopback。
- runtime 公共模型和配置不出现具体 broker 产品概念。
- 八种入站事件和七类响应投影均有可执行 contract test。
- 创建 admission、投影交接和 ACK 顺序经过故障注入验证。
- Task 创建、查询、取消、订阅全程 tenant scoped。
- 总线捕获测试证明无 token、SSE frame、大正文和物理 endpoint。
- README 的实现状态只有在生产代码与跨模块验收通过后才从“设计已接受、代码待落地”更新为 active。

---

## 13. 关联设计

- [agent-runtime L1 README](../../L1-High-Level-Design/agent-runtime/README.md)
- [agent-runtime 逻辑视图](../../L1-High-Level-Design/agent-runtime/logical.md)
- [agent-runtime 进程视图](../../L1-High-Level-Design/agent-runtime/process.md)
- [agent-bus L1 README](../../L1-High-Level-Design/agent-bus/README.md)
- [agent-bus 逻辑视图](../../L1-High-Level-Design/agent-bus/logical.md)
- [agent-bus 进程视图](../../L1-High-Level-Design/agent-bus/process.md)
- [agent-bus forwarding outbox/inbox](../agent-bus/forwarding-outbox-inbox.md)
- [agent-bus forwarding persistence](../agent-bus/forwarding-persistence.md)
- [Feat-Func-001 标准化智能体服务入口](Feat-Func-001-standardized-agent-service-entrypoint.md)
- [Feat-Func-004 远程 Agent 编排](Feat-Func-004-remote-agent-orchestration.md)
- [FEAT-011 客户端调用路由转发](../../../version-scope/FEAT-011-client-invocation-route-forwarding.md)
- [FEAT-012 客户端调用总线转发](../../../version-scope/FEAT-012-client-invocation-bus-forwarding.md)
- [FEAT-013 客户端调用事件转发](../../../version-scope/FEAT-013-client-invocation-event-forwarding.md)
- [FEAT-014 A2A 调用事件转发](../../../version-scope/FEAT-014-a2a-call-event-forwarding.md)
- [FEAT-017 订阅消费总线事件消息需求](../../../version-scope/FEAT-017-bus-event-subscription-consumption.md)
