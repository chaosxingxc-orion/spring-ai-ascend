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
> 最后更新：2026-07-25
>
> 当前状态：功能代码已落地并接入 agent-bus runtime role，等待跨进程功能联调；生产持久化与多实例 DFX 后续实施

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
                  └─> AgentBusResponsePublisher ─> agent-bus runtimeResponseProducer
```

总线只承载控制事件、状态投影和数据引用。token-by-token chunk、SSE frame、大对象正文和 Task execution state 不进入总线；实时输出继续通过 A2A SSE 获取。

### 1.2 当前事实与目标设计

FEAT-017 的代码实施基线和目标代码仓均为 `agent-solution`，预期落点位于 `common/agent-runtime-ext-java`。`spring-ai-ascend` 在本特性中只保存 version-scope 与 L0/L1/L2 架构文档；其中存在的同名 runtime 或 bus 验证代码不属于本特性的当前生产实现事实，不作为类名、包结构或完成度判断依据。

`agent-solution/common/agent-runtime-ext-java` 是 `agent-runtime-java` 的扩展层。它当前通过 Maven 依赖使用 `com.openjiuwen:agent-service-spec`、`agent-service-app` 和 `agent-service-adapters-agentcore` 0.1.0，因此 A2A Task 控制面由上游 `agent-runtime-java` 提供，FEAT-017 在 `agent-solution` 中基于这些公开类型和 Spring 扩展接缝完成接入，不复制或修改上游 Task 状态机。

| 范围 | `agent-solution` 当前代码事实 | FEAT-017 目标设计 |
|---|---|---|
| 代码位置 | 已有 `common/agent-runtime-ext-java` 聚合工程，包含 AgentCore 增强与 Versatile adapter | 在该扩展工程下新增 `agent-service-bus-consumer` 模块；不把生产代码放入 `spring-ai-ascend` |
| 上游 runtime 依赖 | runtime host 通过 0.1.0 依赖消费 `agent-service-spec`、`agent-service-app`、`agent-service-adapters-agentcore` | FEAT-017 模块只直接依赖公开 A2A SDK，复用 `RequestHandler`、`TaskStore` 和标准 Task 生命周期；通过配置类全名声明与 `A2AAutoConfiguration` 的装配顺序，不直接或传递依赖 `agent-service-spec`；如公开扩展面不足，先形成明确的上游接口诉求，不复制内部实现 |
| Agent 执行接入 | `JiuwenCoreAgentExtHandler` 继承上游 `JiuwenCoreAgentHandler`；另有 `VersatileAgentHandler` adapter | 总线入口进入同一 Serve/A2A 执行链，保持现有 handler 对事件来源无感知 |
| Spring 装配 | 已有 `AgentCoreExtAutoConfiguration`、`VersatileAutoConfiguration` 和 AutoConfiguration imports | 新增独立的 bus consumer auto-configuration，按配置和所需具体组件条件激活，不侵入现有 adapter 装配 |
| 总线消费 | agent-bus PR 124 已提供 runtime role；FEAT-017 已实现请求事件适配、信封校验和消费确认 | SDK 自动提供 `runtimeRequestConsumer`；`AgentBusBrokerDeliveryPort` 与 `BrokerDeliveryLoop` 完成事件接收和 `ACK_CONSUMED` / `ACK_REJECTED` / `RETRY` 裁决 |
| 响应投影 | agent-bus PR 124 已提供 `runtimeResponseProducer` direct produce；FEAT-017 已实现 `INVOCATION_*` / `A2A_CALL_*` 状态投影发布 | Task 投影协调和 `BusResponseProjectionStore` 负责投影幂等/重试，`AgentBusResponsePublisher` 通过 SDK producer 直发 `resp_in` |
| 幂等与恢复 | 已提供内存 admission/projection store，能够验证单进程幂等、投影去重和 relay；上游 TaskStore 已支持可选 Redis | 当前阶段以内存 Store 闭合功能；admission/projection 的 Redis 持久化、跨重启和多实例原子性作为后续 DFX |
| broker 接线 | agent-bus SDK 按 `agent-bus.role.runtime.enabled=true` 自动提供请求 consumer 和响应 producer | Demo 只提供 `AgentHandler` 和部署配置，不创建 RocketMQ、Topic resolver、consumer、producer 或 dispatcher |

因此，本文后续出现的 `RuntimeBus*`、`Bus*` 和 `ProjectingTaskStore` 均是 `agent-solution` 的目标设计类型，不是 `spring-ai-ascend` 中已有类，也不是当前已运行能力；其与上游 runtime 的具体方法签名必须以 `agent-runtime-java` 0.1.0 对外 API 和最终编译验证为准。

本阶段交付边界优先闭合创建、查询、取消、订阅、Task 状态观察和响应投影等功能链路。
`BusTaskAdmissionStore` 与 `BusResponseProjectionStore` 暂时使用内存实现，功能验证环境显式配置
`allow-ephemeral-state=true`。这两类状态只保证当前进程存活期间有效，不声明跨重启可靠性或
多实例一致性。上游 `RuntimeRedisClient` 目前缺少多 Key 条件更新、CAS/Lua 和带租约 claim，
不能仅用普通 `GET + SET` 满足 admission 状态转换与 projection 顺序的原子要求；Redis
持久化保留为后续 DFX，不阻塞当前功能验收。

上述两个 Store 接口是 FEAT-017 的内部实现边界，不是开发者 SPI。自动装配无条件创建 SDK
内置实现，不使用 `@ConditionalOnMissingBean` 允许业务应用替换；未来持久化实现也由 SDK
内部提供和切换。

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
| `payloadRef` 所指正文的存储服务、引用格式和生产读取适配器 | 现有需求没有定义解析协议；FEAT-017 当前只处理 inline payload，不把引用字符串当作 A2A JSON |

---

## 2. 架构与职责

### 2.1 组件关系

```text
┌────────────────────────────── agent-runtime ──────────────────────────────┐
│                                                                           │
│  AgentBusBrokerDeliveryPort（agent-bus delivery adapter）                 │
│        │ delivery                                                         │
│        ▼                                                                  │
│  RuntimeBusEventConsumer                                                  │
│    ├─ BusEnvelopeValidator                                                │
│    ├─ inline payload reader                                               │
│    ├─ BusTaskAdmissionStore                                               │
│    └─ RequestHandlerBusA2aBridge ────────┐                                 │
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
│                              AgentBusResponsePublisher                     │
└──────────────────────────────────────────┼─────────────────────────────────┘
                                           ▼
                                      agent-bus
```

### 2.2 职责分配

| 组件 | 职责 | 不负责 |
|---|---|---|
| `AgentBusBrokerDeliveryPort` | 使用 agent-bus `BrokerForwardingConsumerPort` 拉取目标事件；把 ACK/RETRY 裁决映射为 commit/reject | 暴露 topic、offset、consumer group 给领域层 |
| `RuntimeBusEventConsumer` | 编排校验、幂等 admission、A2A bridge、初始投影和消费结果 | 执行业务 Agent；实现 broker retry |
| `BusEnvelopeValidator` | 校验 schema、tenant、target、deadline、事件族和 payload 描述 | 解析 broker header |
| inline payload reader | 从已规范化信封中读取 inline payload；遇到只有 `payloadRef` 的事件时返回确定性不可解析结果 | 猜测引用协议；把引用字符串当作 A2A JSON |
| `BusTaskAdmissionStore` | 以 `(tenantId,idempotencyKey)` 保证创建类 Task admission 唯一；保存 `taskId` 与请求摘要 | 代替 bus inbox 去重 |
| `RequestHandlerBusA2aBridge` | 将事件映射到 `RequestHandler` 的 send/get/cancel/subscribe 语义 | HTTP 回环；私有 Task 状态机 |
| `BusTaskProjectionCoordinator` | 观察 A2A 返回值和 Task 状态，形成响应事件族 | 通过 bus 发布 token chunk |
| `BusResponseProjectionStore` | 保存待发布/已发布投影及稳定 eventId，支持失败补发 | 成为 Task 真相源 |
| `AgentBusResponsePublisher` | 把 runtime-neutral 响应投影转换成 `BrokerOutboundMessage`，通过 SDK 的 `runtimeResponseProducer` 直发 `resp_in` | 创建 RocketMQ producer、选择 Topic 或管理 broker 生命周期 |

Task 状态观察采用目标类型 `ProjectingTaskStore` 装饰 A2A SDK 公共 `TaskStore`：委托原读写后，把发生 revision 变化的 Task 快照交给 `BusTaskProjectionCoordinator`。它不得另起 consumer 与 A2A SDK 的 `MainEventBusProcessor` 竞争同一内部队列。当前 repair scan 只在本进程存活期间补偿仍能从 admission/projection 内存状态关联的 Task revision；跨重启扫描持久化 TaskStore 并恢复缺失投影，需要后续持久化 admission/projection Store 配合。

`BusTaskAdmissionStore` 和 `BusResponseProjectionStore` 虽以 Java 接口表达，但只用于 SDK
内部解耦 consumer、coordinator、relay 与存储实现。开发者不注册这两个接口的 Bean。

`TaskStore` 接口本身可由扩展层实现，但上游默认 store 由 `A2AAutoConfiguration` 通过 `@ConditionalOnMissingBean` 创建。实施前必须用编译/启动 PoC 确认包装方式：优先由扩展模块提供完整的 `TaskStore` bean 并组合 delegate；若无法在不复制上游默认构造逻辑的前提下完成包装，则向上游增加 `TaskStore` customizer/decorator 扩展点。禁止依赖运行期 bean 替换或形成两个并列 `TaskStore`。

### 2.3 所有权不变量

- `agent-runtime` 是本地 Task 的唯一 owner；bus、gateway 和调用方不得通过事件写 Task 状态。
- `agent-bus` 拥有跨边界投递、receipt、retry、DLQ 和 broker 适配；runtime 只返回消费裁决。
- `TaskStore` 是 Task 快照真相源；投影存储只保存“哪些事实需要发布/已发布”，不复制完整 Task。
- handler 只接收框架无关执行上下文，不识别调用来自 HTTP 还是 bus。
- `payloadRef` 所指正文由外部数据 owner 持有；解析协议未定义前 runtime 不尝试读取，也不把引用当成正文。

本版本需求没有冻结 payload 数据服务、引用 URI、凭证、摘要字段或存储产品，因此 FEAT-017 不公开 payload resolver SPI，也不内置生产读取后端。inline payload 由 consumer 直接读取；只有 `payloadRef` 的消息返回明确的不可解析结果。后续若 agent-bus 或需求设计冻结统一解析契约，再由 SDK内部接入，不要求普通 Agent 开发者实现额外接口。

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

当前 `agent-bus` forwarding profile 已把控制字段与数据字段分离：`DATA_BEARING` envelope 可以使用有界的 `inlinePayload`，也可以使用 `payloadRef`；前者用于小型 A2A JSON-RPC request/response，后者用于大载荷或外部正文。agent-bus 只原样透传数据字段，不解析引用。由于现有需求尚未定义 `payloadRef` 的解析协议，FEAT-017 当前只接受 `inlinePayload`；只有 `payloadRef` 的事件返回确定性 `PAYLOAD_EMPTY`，不进入 A2A bridge。

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

payload 使用 A2A JSON-RPC request 时，`method` 必须沿用 FEAT-001 当前标准入口的 PascalCase 名称：`SendMessage`、`SendStreamingMessage`、`GetTask`、`CancelTask` 或 `SubscribeToTask`。`message/send`、`message/stream`、`message/sendStream`、`tasks/get`、`tasks/cancel`、`tasks/resubscribe` 等小写名称不属于本特性协议面，consumer 必须拒绝。payload 直接传递 `MessageSendParams`、`TaskQueryParams`、`CancelTaskParams` 或 `TaskIdParams` 时可以不携带 `method`，由事件类型决定控制操作；创建事件在未携带 `method` 时由 envelope 的流式标记区分阻塞与流式调用。

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

## 4. 核心组件设计

### 4.1 订阅与消费组件

```java
public final class AgentBusBrokerDeliveryPort implements AutoCloseable {
    Optional<Delivery> poll(
            String consumerServiceId,
            String tenantId,
            long nowEpochMillis);
    void commit(Delivery delivery);
    void reject(Delivery delivery, RejectReason reason);

    record Delivery(AgentBusEventEnvelope envelope, byte[] payload) {}
    enum RejectReason {
        INVALID_ENVELOPE,
        INVALID_PAYLOAD,
        PROCESSING_FAILED
    }
}

public record BusConsumptionDecision(Type type, String reason) {
    public enum Type { ACK_CONSUMED, ACK_REJECTED, RETRY }
}
```

两种 `ACK_*` 都表示该消息不应因 Agent 长时间执行而继续占用 broker delivery，delivery loop 对二者执行 `commit`；`ACK_REJECTED` 是 runtime 的确定性业务裁决，不调用 broker `reject`。只有 `RETRY` 才映射为 `reject(PROCESSING_FAILED)`，由 agent-bus 决定后续重试或死信处理。当前单进程功能阶段的 ACK 边界是：

- Task 已进入控制面，且响应投影已写入当前进程的 projection store；或
- 请求已被确定性拒绝，拒绝/失败投影已写入当前进程的 projection store；或
- 重复消息已在当前 admission 状态中被抑制。

`RETRY` 只用于进入上述边界之前的瞬时处理故障，例如 admission 操作失败、A2A bridge
暂时不可用或投影无法写入当前 store。只有 `payloadRef` 而无 inline payload 在本阶段属于
确定性不可解析，不作为瞬时故障重试。业务 Task 终态失败也不是消息投递重试理由。

上述 ACK 边界用于验证功能语义，不代表已经具备跨进程崩溃的 exactly-once 保证。inbox、
持久化 admission/projection 与 broker commit 的一致时序属于后续 DFX。

FEAT-015/016 的 registry `serviceId` 接入 runtime 之前，目标服务身份临时直接读取
`spring.application.name`。FEAT-017 不依赖 `AgentServiceIdentity`，不新增身份 SPI，也不要求重复配置
`target-service-id`。部署时必须保证该临时值与 gateway 写入 `targetServiceId` 的值一致；registry
权威 `serviceId` 接入后应替换这一临时来源。

`consumerServiceId` 不单独配置，按 FEAT-014 的稳定消费组规则从目标逻辑服务身份派生：

```text
consumerServiceId = "runtime-" + spring.application.name
```

同一逻辑服务的多个 runtime 实例共享该消费组和 inbox 去重范围；禁止使用会随进程、Pod 或端口变化的 instanceId 派生消费组。

### 4.2 A2A 控制面桥

```java
public class RequestHandlerBusA2aBridge {
    BusDispatchResult handle(AgentBusEventEnvelope envelope, byte[] resolvedPayload) throws Exception;
    Optional<String> requestedTaskId(AgentBusEventEnvelope envelope, byte[] resolvedPayload);
    boolean supportsReservedTaskId();
    BusDispatchResult handle(AgentBusEventEnvelope envelope,
                             byte[] resolvedPayload,
                             String reservedTaskId) throws Exception;
}
```

`resolvedPayload` 是 consumer 直接取得的 inline payload，不是 broker 原始消息；bridge 不负责 broker I/O。统一的 `handle` 入口在同一处完成事件类型与 JSON-RPC `method` 的配对校验、参数反序列化和 `RequestHandler` 分发。`requestedTaskId` 只提取 continuation 已携带的 Task ID，供 admission 在调用 A2A 前建立或核对幂等记录。指定新 Task ID 是具体 bridge 的内部可选能力，通过 `supportsReservedTaskId()` 判断；默认实现返回 `false`，不要求开发者实现额外接口。

默认实现 `RequestHandlerBusA2aBridge`：

1. 构造 `ServerCallContext`，写入 `tenantId`、`correlationId`、`idempotencyKey`、`traceId`、source、target 和 deadline。
2. 直接调用 A2A SDK `RequestHandler` 公共方法，不调用本机 `/a2a`，也不把 Task 协议操作降级成 `ServeOrchestrator` 的会话操作。
3. 把 A2A response / publisher 首个可观察状态规范化为 `BusDispatchResult`。
4. 不直接调用 `AgentHandler` 或 `ServeOrchestrator`，避免绕过 Task 创建、TaskStore、Task 查询和 Task 取消语义。
5. 对 subscribe 只调用 `onSubscribeToTask` 完成 Task/stream 可订阅性校验并返回 stream-ready 结果，不订阅、缓存或转发 publisher 中的 SSE frame；后续投影阶段负责生成 streamRef，真正的流消费发生在调用方持有 SSE 连接时。

| Bridge 操作 | JSON-RPC `method` | A2A SDK 1.0.0.Final `RequestHandler` 映射 | 当前 HTTP `/a2a` 暴露情况 |
|---|---|---|---|
| 非流式 send | `SendMessage` | `onMessageSend(MessageSendParams, ServerCallContext)` | 已暴露 |
| 流式 send | `SendStreamingMessage` | `onMessageSendStream(MessageSendParams, ServerCallContext)` | 已暴露 |
| get task | `GetTask` | `onGetTask(TaskQueryParams, ServerCallContext)` | 已暴露 |
| cancel task | `CancelTask` | `onCancelTask(CancelTaskParams, ServerCallContext)` | SDK/bean 已具备，当前 controller 未分发 |
| subscribe task | `SubscribeToTask` | `onSubscribeToTask(TaskIdParams, ServerCallContext)` | SDK/bean 已具备，当前 controller 未分发 |

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

public final class AgentBusResponsePublisher {
    void publish(BusResponseProjection projection);
}
```

稳定 `eventId` 使用以下逻辑键生成：

```text
hash(tenantId, causationMessageId, taskId-or-none, projectionKind, projectionRevision)
```

同一事实重放得到相同 eventId；`append` 必须幂等。Task 状态发生新 revision 时产生新的投影，重复观察同 revision 不重复产生可见副作用。

投影发布失败不回滚已创建 Task。请求 consumer 在投影已写入 `BusResponseProjectionStore` 后可 ACK；独立的 `BusResponseRelay` 重试发布。该存储是 runtime 的投影交接日志，不暴露或复制 agent-bus outbox/inbox 物理模型。当前内存实现只能在进程存活期间提供该交接能力。

后续生产 DFX 目标仍要求 `TaskStore`、`BusTaskAdmissionStore` 和 `BusResponseProjectionStore` 跨进程重启持久化：admission store 提供唯一约束/CAS，projection store 提供带租约 claim 与幂等 append。三者不要求共享物理数据库；稳定 taskId 与 repair 流程用于闭合跨存储崩溃窗口。实现 Redis Store 前必须先补齐服务端原子更新能力，并完成多实例和故障注入验证，不能把简单 `GET + SET` 实现作为生产可靠性证据。当前 agent-bus runtime role 按 PR 124 的范围采用 direct produce；`AgentBusResponsePublisher` 仅在 broker 返回 `ACCEPTED` 后把投影标记为已发布，失败由 `BusResponseRelay` 基于当前 Store 做进程内重试。

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
2. inline payload 由 consumer 直接读取，并验证其 method 与事件类型兼容。只有 `payloadRef` 的事件在解析协议尚未冻结时形成确定性的 `*_FAILED`/`*_REJECTED`，不得把引用字符串当作 A2A JSON。
3. 对创建类请求按 `(tenantId,idempotencyKey)` reserve；重复请求复用 reservation 的 `taskId`。
   对携带已有 taskId 的 continuation，reservation 绑定并核对该 taskId，不另行分配。
4. 经 `RequestHandlerBusA2aBridge` 进入标准 A2A Task 控制面。
5. 创建成功或复用后，先把 `*_ACCEPTED` 投影写入当前 Store，再返回 ACK；本阶段内存 Store 不保证重启恢复。
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
  -> 投影写入当前 Store
  -> ACK
```

- Task 不存在时发布确定性 `*_FAILED(TASK_NOT_FOUND,retryable=false)` 并 ACK。
- tenant 不匹配的响应与 Task 不存在使用同一外部错误表面，日志和审计保留内部原因，避免跨租户枚举。
- subscribe 不启动新 Task；终态 Task 是否仍允许读取历史 SSE 由现有 A2A 能力决定，不支持时返回 `STREAM_NOT_AVAILABLE`。

### 5.3 重复投递与崩溃恢复

当前阶段使用内存 admission/projection Store，只保证同一进程内的重复请求抑制、稳定 eventId
去重和 relay 重试。下表是后续生产 DFX 的恢复目标，不是本阶段已具备的跨重启能力：

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
| Task 已创建/复用且 ACCEPTED 已写入当前 Store | `ACK_CONSUMED` | 当前进程内后续执行与原 delivery 解耦 |
| 非创建控制请求已处理且响应投影已写入当前 Store | `ACK_CONSUMED` | 已形成当前进程可继续发布的结果 |
| 信封非法、过期、tenant/target 不匹配 | `ACK_REJECTED` | 确定性失败，重投无意义；字段可信且足以关联时记录失败投影 |
| 当前 admission 已判定重复 | `ACK_CONSUMED` | 同一进程内不重复执行副作用 |
| admission/projection store 瞬时不可用 | RETRY | 尚未进入稳定接收边界 |
| Agent 执行失败或进入 INPUT_REQUIRED | 不改变原 `ACK_CONSUMED` | 属于 Task 生命周期，不是投递失败 |

agent-bus inbox 去重、持久化投影恢复与 broker commit 的一致时序在后续 DFX 中冻结。

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
- `ADMITTED` 表示 Task 已存在或已被控制面接受，不表示 Agent 已完成。
- 只有 admission 前的确定拒绝进入 `REJECTED`；已有 Task 的执行失败体现在 Task/terminal projection 中。
- 当前进程内发现超时 `RESERVED` 时，repairer 先按 taskId 查询 TaskStore，再决定重驱或补齐；
  跨重启处理依赖后续持久化 Store。

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
3. `targetServiceId == spring.application.name`（registry `serviceId` 接入前的临时映射），route tenant scope 与 `tenantId` 一致。
4. deadline 未过期，并限制过远 deadline，防止无限资源占用。
5. payload inline/ref 互斥、content type 和最大 inline bytes。
6. 当前只接受 `inlinePayload`；只有 `payloadRef` 的事件返回确定性 `PAYLOAD_EMPTY`。在统一解析协议冻结前，不读取引用、不把引用字符串当作 A2A JSON。
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
| `PAYLOAD_EMPTY` | false | `*_FAILED` | 没有可交给 A2A bridge 的 inline payload；当前版本不解析 `payloadRef` |
| `PAYLOAD_INVALID` | false | `*_FAILED` | A2A payload 无法解析或 method 不匹配 |
| `IDEMPOTENCY_KEY_CONFLICT` | false | `*_REJECTED` | 同 key 不同 request digest |
| `RUNTIME_NOT_READY` | true | `*_FAILED` | runtime 暂不接收执行 |
| `TASK_NOT_FOUND` | false | `*_FAILED` | 查询/取消/订阅的 Task 不存在或不可见 |
| `STREAM_NOT_AVAILABLE` | false | `*_FAILED` | Task 当前无可订阅 SSE |
| `PROJECTION_HANDOFF_FAILED` | true | RETRY | 稳定投影无法记录，尚不能 ACK |
| `INTERNAL_ERROR` | 按分类 | `*_FAILED` | 未分类内部错误，不泄露堆栈 |

### 7.3 安全约束

- 所有 store 调用必须显式携带或可从不可变信封取得 `tenantId`，禁止默认 tenant 和跨 tenant fallback。
- 响应投影不得回显 routeHandle 内部内容、物理 endpoint、broker 信息或敏感 payloadRef 凭证。
- 日志不记录 inline payload 正文、token、完整 payloadRef、签名 streamRef 或认证头。
- `streamRef` 使用可轮换 key 签名，解析失败统一返回不可用，不泄露 Task 是否存在。
- source identity 的认证由 subscription adapter 提供；runtime validator 仍校验 envelope source 与已认证 transport identity 一致。
- inline payload 和 metadata 使用协议实现固定的硬大小上限；当前版本不读取 `payloadRef` 指向的外部正文。

---

## 8. 自动装配与代码结构

### 8.1 包结构

```text
agent-solution/common/agent-runtime-ext-java/
└── agent-service-bus-consumer/
    ├── src/main/java/com/openjiuwen/service/bus/consumer/
    │   ├── RuntimeBusEventConsumer.java
    │   ├── BusTaskProjectionCoordinator.java
    │   ├── a2a/
    │   │   ├── RequestHandlerBusA2aBridge.java
    │   │   ├── ProjectingTaskStore.java
    │   │   └── TaskStoreProjectionPostProcessor.java
    │   ├── port/
    │   │   ├── BusTaskAdmissionStore.java
    │   │   └── BusResponseProjectionStore.java
    │   ├── runtime/
    │   │   ├── AgentBusBrokerDeliveryPort.java
    │   │   ├── AgentBusResponsePublisher.java
    │   │   ├── BrokerDeliveryLoop.java
    │   │   └── BrokerConsumerLifecycle.java
    │   └── autoconfigure/
    │       ├── BusConsumerAutoConfiguration.java
    │       └── BusConsumerProperties.java
    └── src/test/java/com/openjiuwen/service/bus/consumer/testkit/
        └── InMemoryBrokerDeliveryPort.java
```

broker-specific adapter 不进入上述包，也不让 `agent-bus` 生产模块反向依赖 `agent-runtime`。默认产品接线应位于独立 integration/host assembly：该装配层可以同时依赖两侧 SPI，负责把 `agent-bus` transport 适配到 runtime 的窄端口；两侧领域模块继续保持生产依赖隔离。

### 8.2 自动装配条件

`BusConsumerAutoConfiguration` 仅在 `openjiuwen.service.bus.consumer.enabled=true` 时激活。自动装配完成后还会检查以下运行条件：

- 存在且仅存在一个 `AgentBusBrokerDeliveryPort`；
- agent-bus runtime role 已提供名为 `runtimeRequestConsumer` 的 `BrokerForwardingConsumerPort` 和名为 `runtimeResponseProducer` 的 `BrokerForwardingProducerPort`；
- 存在 `AgentBusResponsePublisher`、`BusTaskAdmissionStore` 和 `BusResponseProjectionStore`；模块当前只处理 inline payload；
- 现有 A2A SDK `RequestHandler`、`TaskStore` 已装配；
- `openjiuwen.service.bus.consumer.consumer-tenant-id` 和 `spring.application.name` 非空；`consumerServiceId` 临时从 `spring.application.name` 派生，不重复配置。

缺少必需组件时应启动失败并列出缺失 bean，不能悄悄启用只能消费不能响应的半功能。如果实际收到只有 `payloadRef` 的事件，必须返回确定性 `PAYLOAD_EMPTY`，不能把引用字符串当作 A2A JSON。`enabled=false` 时不要求任何 bus 依赖，保持现有纯 HTTP runtime 可用。

生产可靠性 profile 仍应通过 durability capability 检查，拒绝把默认 `InMemoryTaskStore` 或 in-memory admission/projection store 当作跨重启可靠实现。本阶段功能联调使用显式 `allow-ephemeral-state=true`；该配置表示接受进程重启后 admission/projection 状态丢失，不能作为生产可靠性验收证据。

### 8.3 配置表面

```yaml
openjiuwen:
  service:
    bus:
      consumer:
        enabled: true
        consumer-tenant-id: ${TENANT_ID}

agent-bus:
  role:
    runtime:
      enabled: true
  reliability:
    enabled: false
```

`openjiuwen.service.bus.consumer.enabled` 默认为 `false`；只有显式设为 `true` 才激活 FEAT-017 自动装配。启用时必须具有非空 `consumer-tenant-id` 和 `spring.application.name`。消费组身份临时派生为 `runtime-${spring.application.name}`，不提供独立的 `consumer-service-id` 或 `target-service-id` 配置。FEAT-015/016 的 registry `serviceId` 接入后，订阅过滤、响应 source 和消费组派生必须统一切换到该权威值。

```yaml
spring:
  application:
    name: ${RUNTIME_SERVICE_ID}
```

需要发布 STREAM_READY 时再提供签名密钥：

```yaml
openjiuwen:
  service:
    bus:
      consumer:
        stream-ref-secret: ${STREAM_REF_SECRET}
```

`stream-ref-secret` 未配置时不创建 `StreamReferenceService`，相应的 STREAM_READY 投影能力不启用。该密钥必须通过部署 Secret 或 host 密钥设施提供，不得写入源码和普通配置仓库。streamRef TTL 固定为 5 分钟。

schema major 和资源安全边界属于实现支持能力，不允许通过部署配置宣称额外协议能力。当前固定值为：

| 约束 | 固定值 |
|---|---:|
| accepted schema major | 1 |
| inline payload 上限 | 65,536 bytes |
| metadata 上限 | 16,384 bytes |
| deadline 最大前视时间 | 86,400 seconds |

普通部署不需要配置轮询、并发、重试和 repair 参数。确需性能调优时，可使用独立的可选 `tuning` 子配置：

```yaml
openjiuwen:
  service:
    bus:
      consumer:
        tuning:
          poll-interval: 1s
          payload-max-in-flight: 16
          bridge-max-in-flight: 16
          projection-max-in-flight: 16
          response-relay-max-attempts: 5
          response-relay-backoff: 100ms
          repair-interval: 5s
```

上述参数均有代码默认值，部署者不填写时采用示例所列默认值。repair 每轮扫描上限当前固定为 100，admission 锁分片数固定为 64，不属于配置表面。当前功能联调需要显式允许内存状态：

```yaml
openjiuwen:
  service:
    bus:
      consumer:
        allow-ephemeral-state: true
```

配置不得出现 topic、offset、partition、consumer group 或 broker retry。物理 transport 的这些参数属于 adapter 自己的命名空间。FEAT-017 当前没有统一的 `payload-resolve-timeout` 或外部 payload provider 配置；待需求或 agent-bus 冻结引用解析协议后再补充 SDK 内部实现。

### 8.4 生命周期

1. Spring 完成 `RequestHandler`、store 和 publisher 装配。
2. runtime readiness 进入 ready 后启动 projection relay，再启动 subscription。
3. drain 时先停止接收新 delivery，等待已进入 admission 的短临界区完成。
4. 不等待所有 Agent Task 终态；Task 按现有 runtime 生命周期继续或由部署策略处理。
5. 最后停止 response relay；当前内存实现只在本次进程 drain 期间尽量处理 pending
   projection，进程退出后不能恢复。后续持久化 Store 应保留 pending projection 供下次启动
   或其他实例继续处理。

---

## 9. 运行约束

### 9.1 可观测性边界

本版本暂不实现 FEAT-017 专用的 Micrometer 指标、Observation span 和 trace 恢复逻辑，不提供
`BusConsumerTelemetry`，可观测性不作为本次交付的完成条件。Envelope 和响应投影仍透传
`traceId`、`correlationId` 等关联字段，供后续由 runtime host 统一接入可观测平台。

本版本保留必要的业务日志和投影审计日志。`ACCEPTED`、`INPUT_REQUIRED`、`STREAM_READY`、
`TERMINAL` 投影应能通过 tenant hash、messageId、correlationId、taskId、eventType 和
causation message 进行问题定位。后续若实现专用指标和 Trace，应另行补充指标名称、低基数标签、
span 边界及跨 broker 的 trace 上下文格式。

### 9.2 背压与并发

- subscription adapter 依据 consumer 返回的尚未完成 stage 限制 in-flight delivery；runtime 不创建无界线程或无界队列。
- 同一 `(tenantId,idempotencyKey)` 的 admission 必须串行化；不同 key 可并发。
- 同一 Task 的投影按 Task revision 有序 append；不同 Task 可并发发布。
- A2A bridge 和 projection publisher 使用独立 bulkhead，避免其中一个阶段耗尽 Task 执行资源。
- 并发上限由 runtime-neutral 参数表达，broker prefetch/pull batch 由 adapter 自行映射。

---

## 10. 测试设计

### 10.1 单元与契约测试

| 测试组 | 必测断言 |
|---|---|
| envelope validator | schema、target、tenant、deadline、inline/ref 互斥、大小限制 |
| event mapper | 8 种请求事件映射到正确 `RequestHandler` 方法；错误 family 不混用 |
| admission store contract | 内存实现验证同 key 同摘要复用 taskId、同 key 不同摘要冲突、tenant 隔离和进程内并发唯一 |
| response projection store contract | 内存实现验证 eventId 幂等、revision 有序、pending/retry/markPublished；跨重启 claim 后续验证 |
| stream reference | 不含 endpoint；tenant/task/expiry/signature 校验；key rotation |
| consume disposition | admission/投影交接后 ACK；稳定边界前瞬时失败 RETRY；不等待 Task 终态 |
| payload 处理 | inline payload 原样进入 bridge；只有 `payloadRef` 时确定性失败，引用字符串不得被当作 A2A JSON |

两个 Store 保留 in-memory contract fixture，作为当前功能实现和测试替身；Redis 原子性、跨重启
恢复和多实例 claim 属于后续 DFX，不纳入本阶段功能完成条件。

### 10.2 集成测试

1. `CLIENT_INVOCATION_REQUESTED` → 真实 `RequestHandler` → Task CREATED/WORKING → `INVOCATION_ACCEPTED`。
2. `A2A_CALL_REQUESTED` → 同一 handler → `A2A_CALL_ACCEPTED`，业务 handler 无 source 分支。
3. 阻塞完成 → RESPONSE + 唯一 TERMINAL。
4. 流式调用 → ACCEPTED + STREAM_READY，捕获所有 bus publication 并断言无 token/SSE frame。
5. Task 进入 INPUT_REQUIRED → 对应 family 的 INPUT_REQUIRED 投影。
6. query/cancel/subscribe 使用真实 taskId；不存在、跨 tenant 和错误本地 ID 不创建 Task。
7. 相同 messageId 重投、不同 messageId + 相同 idempotencyKey 重试均只创建一个 Task。
8. Task 创建后模拟 publisher 短暂失败：原 delivery ACK，在同一进程内由 relay 补发相同
   eventId。
9. runtime drain：停止新消费，不等待长 Task 终态；在允许的 drain 时间内处理已进入 relay
   的投影。

以下故障注入归入后续 DFX：在 reservation、Task 创建、投影 append 和 publish 各崩溃点
重启，验证持久化恢复表及 pending projection 不丢失。

### 10.3 跨模块验收

与 `agent-bus` 使用真实 adapter 做以下 E2E：

```text
gateway/source runtime
  -> agent-bus outbox/relay/broker/receiver
  -> AgentBusBrokerDeliveryPort
  -> agent-runtime RequestHandler/TaskStore
  -> AgentBusResponsePublisher / runtimeResponseProducer
  -> agent-bus
  -> gateway/source runtime projection consumer
```

当前功能验收必须覆盖：客户端创建/查询/取消/订阅、A2A 创建/查询/取消/订阅、同一进程内
重复投递、接受后长任务、INPUT_REQUIRED、流重连、终态失败、tenant 隔离、只有 payloadRef
时的确定性失败、adapter 短暂不可用和同一进程内响应重投。跨模块测试还必须用消息体扫描
断言 token chunk、SSE frame、物理 endpoint 和 Task execution state 未进入总线。

跨重启响应补发、持久化去重和 DLQ 审计归入后续 DFX 验收。

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
| 响应发布幂等与补发 | §4.4、§5.3 | 当前同进程 retry tests；后续 crash recovery tests |
| tenant 隔离 | §3.1、§7 | cross-tenant negative tests |
| token 流不进总线 | §1.1、§3.4、§10 | captured-message scan |
| broker 透明 | §2、§4、§8 | ArchUnit dependency rule |
| 调用方响应回灌排除 | §1.4 | architecture boundary review |

---

## 12. 实施顺序与完成条件

### 12.1 建议实施切片

1. **上游扩展面编译/启动 PoC**：以 `agent-runtime-java` 0.1.0 和 A2A SDK 1.0.0.Final 为基线，确认扩展模块可注入同一个 `RequestHandler`、`TaskStore` 与 `ServerCallContext` 相关类型；验证 `ProjectingTaskStore` 的唯一 bean 包装和自动装配顺序。公开扩展面不足时先形成具体上游接口诉求，PoC 未通过不得进入后续切片。
2. 中立事件/响应模型、validator、mapper 和 SPI contract fixture。
3. `RequestHandlerBusA2aBridge`，完成八种事件与 A2A `RequestHandler` 语义一致性测试，并区分当前 HTTP controller 已暴露与仅 SDK 已具备的方法。
4. 使用内存 admission store 完成当前进程内的创建幂等；稳定 taskId 的协议恢复继续按未闭环项推进。
5. 使用内存 projection store 完成 relay 和 Task projection coordinator 功能链。
6. streamRef、INPUT_REQUIRED、终态 repair。
7. Spring auto-configuration、readiness/drain 和 ArchUnit 规则；专用可观测性能力留待后续实现。
8. 与 `agent-bus` adapter 联调及跨模块功能 E2E。
9. 后续 DFX：补齐 Redis 原子更新能力，实现 admission/projection 持久化、跨重启恢复、
   多实例 claim/租约、streamRef 多实例和故障注入。

### 12.2 设计完成条件

- 不新增 bus 专用 Task 状态机或 HTTP loopback。
- runtime 公共模型和配置不出现具体 broker 产品概念。
- 八种入站事件和七类响应投影均有可执行 contract test。
- 创建 admission、投影交接和 ACK 顺序完成单进程功能验证。
- Task 创建、查询、取消、订阅全程 tenant scoped。
- 总线捕获测试证明无 token、SSE frame、大正文和物理 endpoint。
- README 的实现状态只有在生产代码与跨模块验收通过后才从“设计已接受、代码待落地”更新为 active。

以下不作为当前功能完成的阻塞条件，统一进入后续 DFX：Redis admission/projection Store、
跨进程重启恢复、多实例原子 claim、streamRef 共享状态、专用指标/Trace 和容量压测。

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
