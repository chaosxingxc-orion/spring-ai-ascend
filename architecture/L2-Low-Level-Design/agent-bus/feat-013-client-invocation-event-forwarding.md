---
level: L2
module: agent-bus
feature: FEAT-013
view: [logical, process, development, physical, scenarios]
status: draft
source_feature: ../../../version-scope/FEAT-013-client-invocation-event-forwarding.md
related_docs:
  - ../../../version-scope/FEAT-013-client-invocation-event-forwarding.md
  - ../../../version-scope/FEAT-001-standardized-agent-service-entrypoint.md
  - ../../L0-Top-Level-Design/boundaries.md
  - ../../L0-Top-Level-Design/glossary.md
  - ../../L1-High-Level-Design/agent-bus/README.md
  - ../../L1-High-Level-Design/agent-bus/logical.md
  - ../../L1-High-Level-Design/agent-bus/process.md
  - ../../L1-High-Level-Design/agent-bus/scenarios.md
  - ../../L1-High-Level-Design/agent-bus/features/README.md
  - ./forwarding-outbox-inbox.md
  - ./forwarding-persistence.md
---

# FEAT-013 L2 技术设计：客户端调用事件转发（gateway ↔ event-bus，RocketMQ pub/sub 两跳）

> 命名说明：本文架构语义（所有权、参与者、状态归属）使用 L0 逻辑名 `agent-runtime` / `agent-core`。当前真实 `agent-runtime` 实现为外仓 `agent-runtime-java`（仓内 `agent-runtime/` 目录为废弃代码，不作为事实源）；本 L2 涉及 agent-runtime 处均指外仓真实实现。

## 1. 概述

### 1.1 特性定位

FEAT-013 把 `version-scope/FEAT-013` 在外部行为层定义的"客户端调用事件转发"投影为可落地的 L2 技术设计。它约束 `agent-bus` 逻辑域中 **gateway 单元** 与 **event-bus 单元** 之间的客户端调用与响应事件转发语义，并落地本期已确定的 4 条运行态决策：

1. gateway **单独进程**部署；event-bus 与 registry-discovery-center **同进程**部署；三单元独立可替换。
2. gateway→event-bus 与 event-bus→agent-runtime **两跳均经 broker pub/sub**。
3. broker 选型 = **RocketMQ**。
4. event-bus→agent-runtime **不走 a2a push**（现有 `A2aForwardingDeliveryPort` 的 T1 HTTP 同步 push 路径在本特性范围内被 broker pub/sub 取代，T1 PoC 保留共存/灰度切换，见 §8）。

本 L2 解决的问题（before → after）：
- before：`version-scope/FEAT-013` 留空 broker 与部署（"不规定具体 broker、topic…"、"不承诺三单元同进程"）；代码侧 gateway 仅 SPI 无实现，event-bus→agent-runtime 为 T1 HTTP push，事件信封无 `eventType`，事件类型常量零落地。
- after：固定 RocketMQ pub/sub 两跳拓扑、三单元部署拆分、扩展后的 `AgentBusEventEnvelope`（= `ForwardingEnvelope` + eventType/source/target）、FEAT-013 事件类型枚举、调用响应状态机、gateway 生产实现落点、agent-runtime 侧 consumer/producer 落点。

### 1.2 核心设计原则

- **broker pub/sub 两跳，event-bus 为治理中继** — gateway→event-bus→agent-runtime 两跳均经 RocketMQ；event-bus 在两跳间做 inbox 去重 / 租户校验 / correlation / 审计，不是字节透传。理由：满足"两跳均经 broker"约束的同时保留 event-bus 治理责任（FEAT-013 §5.1.1 "event-bus 负责在生产方与消费方之间转发事件和维护投递治理语义"）。
- **A2A payload 兼容，bus 信封分离** — A2A JSON-RPC / Task / SSE 语义只作为 envelope 的 `payloadRef` 或 inline payload 出现；外层 `AgentBusEventEnvelope` 承载 tenant/trace/correlation/idempotency/deadline/route 治理字段。理由：FEAT-013 §3 "A2A JSON-RPC 只能作为 payload 或 payload 引用出现，不承担 bus 投递治理职责"。
- **bus 不拥有 Task** — `agent-bus` 不创建、不写入、不推进服务端 Task execution state；Task 生命周期仍归 `agent-runtime`。理由：FEAT-013 §5.2 + L0 boundaries。
- **三单元独立可替换** — gateway / event-bus / registry-discovery-center 作为可独立实现、独立部署、可由客户存量系统替换的运行时单元；本 L2 只约束它们之间的契约语义。理由：FEAT-013 §2 "三单元可替换部署 MUST"。
- **至少一次 + 幂等三层** — broker 模型 B ack-after-consume（至少一次）；bus 投递幂等（`tenantId+messageId`）、服务端创建幂等（`tenantId+idempotencyKey`，net-new in agent-runtime）、gateway 客户端重试幂等（复用 `idempotencyKey`）。理由：FEAT-013 §5.1.6。

### 1.3 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| 客户端调用事件发布 | gateway 把客户端调用封装为事件投递到 event-bus | `AgentBusEventEnvelope`(eventType=`CLIENT_INVOCATION_REQUESTED`) + `BrokerForwardingRelayPort` | ⬜ net-new（gateway 生产实现） |
| 响应事件转发 | agent-runtime 把接受/拒绝/失败/响应/流准备/终态回传 gateway | `INVOCATION_*` 事件 + `BrokerForwardingConsumerPort` | ⬜ net-new（agent-runtime producer） |
| 接受等待窗口 UNKNOWN | gateway 在窗口内无法确认服务端是否建 Task 时返回 UNKNOWN | 调用响应状态机 `UNKNOWN` 分支 | ⬜ net-new |
| 流式 SSE 桥接 | gateway 在客户端连接存在时桥接 agent-runtime A2A SSE | stream 引用 + `SseEmitter`（复用 agent-runtime） | ⬜ net-new（gateway 桥接） |
| 幂等与重试 | 三层幂等 + UNKNOWN 后同键重试 | outbox/inbox dedup + `idempotencyKey` | ✅ 复用 outbox/inbox（bus 投递）/ ⬜ net-new（服务端创建） |
| 调用响应状态机 | gateway 侧观测态 COMPLETED_RESPONSE/ACCEPTED_WITH_TASK/STREAM_READY/REJECTED/FAILED/UNKNOWN | 新枚举 `InvocationResponseStatus` | ⬜ net-new |

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 级别 | 状态 | 说明 |
|---|---|---|---|
| 客户端调用事件发布 | MUST | ⬜ | gateway 封装客户端调用为 `CLIENT_INVOCATION_REQUESTED` 经 RocketMQ 投递到 event-bus。复用 `BrokerForwardingRelayPort` SPI；RocketMQ 具体 adapter net-new（confined `transport.broker`）。 |
| 服务端响应事件转发 | MUST | ⬜ | agent-runtime 把 `INVOCATION_*` 经 RocketMQ 回传 event-bus→gateway。agent-runtime producer net-new。 |
| 三单元可替换部署 | MUST | ✅ | gateway 单独进程、event-bus+registry 同进程、三单元独立可替换——本期部署决策（见 §5）。 |
| 外层 bus 事件信封 | MUST | ✅/⬜ | 扩展 `ForwardingEnvelope` 为 `AgentBusEventEnvelope`（additive `eventType`/`sourceServiceId`/`targetServiceId`）；治理字段复用。 |
| A2A payload 兼容 | MUST | ✅ | A2A JSON-RPC/Task/SSE 作为 `payloadRef` 或 inline payload；不承担治理职责。 |
| 同步阻塞调用转发 | MUST | ⬜ | gateway 在等待窗口内返回最终响应 / 已接受 Task 引用 / 拒绝 / 失败 / UNKNOWN。 |
| 流式调用请求转发 | MUST | ⬜ | 流式请求事件同阻塞经 broker 投递；token chunk 不进 broker。 |
| A2A SSE 流桥接 | MUST | ⬜ | gateway 按 stream 引用与服务端建立 A2A SSE 并桥接客户端；复用 agent-runtime `SseEmitter`。 |
| INVOCATION_ACCEPTED 与 INVOCATION_STREAM_READY 分离 | MUST | ⬜ | 接受建 Task 与流可订阅分离表达。 |
| UNKNOWN 响应状态 | MUST | ⬜ | 接受窗口内无法确认建 Task 时返回 UNKNOWN，不误报成功/失败。 |
| 服务端创建幂等 | MUST | ⬜ | `tenantId+idempotencyKey` 幂等键，net-new in agent-runtime。 |
| bus 投递幂等 | MUST | ✅ | 复用 outbox `(tenantId,messageId)` / inbox `(tenantId,messageId,consumerServiceId)` dedup。 |
| gateway 客户端幂等 | MUST | ⬜ | gateway 重试同一调用复用同一 `idempotencyKey`。 |
| clientInvocationId 网关侧关联 | SHOULD | ⬜ | gateway 可暴露 `clientInvocationId`，不作 Task 生命周期状态、不替代 taskId。 |
| 标准 Task 查询/订阅 | MUST | ⬜ | GetTask/SubscribeToTask 基于服务端 `taskId`。依赖 FEAT-001 缺口（CancelTask/SubscribeToTask/ListTasks 路由，见 §8）。 |
| 大载荷引用 | MUST | ✅ | `payloadRef` data reference path；token 流/大正文不进 broker（复用 §6.2②）。 |
| 租户隔离 | MUST | ✅ | 复用三层（app WHERE / RLS / GUC `set_config`）+ broker header tenant check + envelope routeHandle.tenantScope。 |
| 物理投递机制透明 | MUST | ✅ | 客户端/服务端不依赖具体 broker/topic/endpoint；routeHandle opaque 经 `ForwardingEndpointResolver` 解析。 |
| registry 支撑但不接管 | MUST | ✅ | 复用 `AgentDiscoveryService.resolveRouteHandle`；不接管调用/响应事件或 Task。 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|---|---|---|
| token chunk 事件化 | 不把 token-by-token chunk / SSE frame / 大正文作 broker 消息体 | A2A SSE 承载实时流（§4.6） |
| bus 拥有 Task 状态 | `agent-bus` 不写 Task execution state | Task 归 agent-runtime |
| clientInvocationId 替代 taskId | 只是网关侧关联句柄 | GetTask/SubscribeToTask 基于 taskId |
| 隐式重建 Task | SubscribeToTask 找不到 Task 不自动重建 | UNKNOWN 后同 idempotencyKey 重试 |
| 私有流协议 | 不定义 A2A SSE 之外的私有流协议 | A2A SSE |
| 物理 endpoint 暴露 | 客户端不直连服务端物理 stream endpoint | gateway 内部解析 stream 引用并桥接 |
| 具体 broker 产品强绑 | 本 L2 选 RocketMQ 为本期接线，但治理语义不反向定义 broker 产品概念 | broker 概念圈 `transport.broker`（§6.2②精神） |
| 单体部署假设 | 不承诺三单元同制品/同部署单元 | 本期部署决策（§5） |
| 大载荷数据通道 | broker 不作大对象/敏感正文数据通道 | payloadRef 引用路径 |
| 非 A2A 查询扩展 | 不新增 ResolveInvocation 之类非 A2A 标准方法 | UNKNOWN 后同 idempotencyKey 重试 |

### 2.3 接口契约（Logical View）

#### 2.3.1 事件信封 `AgentBusEventEnvelope`

L2 固化命名：FEAT-013/014 规范命名的 `AgentBusEventEnvelope` 在代码实现上即**扩展后的 `ForwardingEnvelope`**（`agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/spi/ForwardingEnvelope.java`），additive 增加三个字段，保留 `ForwardingEnvelope` 名以最小化对已落地转发底座（outbox/inbox/状态机/SqlCodec/217 tests）的破坏。文档中两名称等价。

```java
public record ForwardingEnvelope(
        ForwardingMessageId messageId,
        AgentBusEventType  eventType,          // NEW（FEAT-013/014 事件类型判别）
        String             tenantId,          // 复用：强制 = routeHandle.tenantScope
        String             traceId,           // 复用：W3C 32-char hex
        String             correlationId,     // 复用：跨跳关联
        String             idempotencyKey,    // 复用：客户端重试幂等键（服务端创建幂等依据）
        ForwardingRouteHandle routeHandle,    // 复用：opaque，经 ForwardingEndpointResolver 解析为 broker topic
        String             capability,        // 复用
        String             sourceServiceId,   // NEW（提升自 ForwardingOutboxRecord / BrokerMessageHeaders）
        String             targetServiceId,   // NEW（同上）
        long               deadlineMillisEpoch,// 复用
        PayloadPolicy      payloadPolicy,     // 复用：CONTROL_ONLY / DATA_BEARING
        String             payloadRef         // 复用：DATA_BEARING 必填、CONTROL_ONLY 可选
) { /* compact constructor 增 eventType/source/target 非空非 blank 校验；tenantId==routeHandle.tenantScope 不变 */ }
```

> `sourceServiceId`/`targetServiceId` 已存在于 `ForwardingOutboxRecord` 与 `BrokerMessageHeaders`/`BrokerInboundMessage`；提升到 envelope 使信封自包含源/目标路由引用（对齐 FEAT-013 §3 `AgentBusEventEnvelope` "承载…源、目标路由引用"）。outbox record 在 enqueue 时镜像这两个字段（envelope 为权威）。

#### 2.3.2 事件类型枚举 `AgentBusEventType`（FEAT-013 族）

```java
public enum AgentBusEventType {
    // client → server（经 gateway → event-bus → agent-runtime）
    CLIENT_INVOCATION_REQUESTED,           // 发起 Agent 调用 / Task 创建
    CLIENT_INVOCATION_CANCEL_REQUESTED,    // 取消已有 Task（→ A2A CancelTask）
    CLIENT_INVOCATION_QUERY_REQUESTED,     // 查询 Task / ListTasks（→ A2A GetTask / ListTasks）
    CLIENT_STREAM_SUBSCRIBE_REQUESTED,     // 订阅已有 Task 的 A2A SSE（基于 taskId）
    // server(agent-runtime) → gateway（经 agent-runtime → event-bus → gateway）
    INVOCATION_ACCEPTED,                   // 已接受并创建/复用 Task（携带 taskId）
    INVOCATION_REJECTED,                   // 明确拒绝、未建 Task
    INVOCATION_FAILED,                     // 确定失败（携带错误码 + 可重试语义）
    INVOCATION_RESPONSE,                    // 阻塞窗口内一次性 A2A 响应
    INVOCATION_STREAM_READY,               // Task 的 A2A SSE 流已可订阅（携带 stream 引用）
    INVOCATION_TERMINAL;                    // Task 终态（完成/失败/取消），不载 token 流
    // FEAT-014 的 A2A_CALL_* / A2A_STREAM_* 族见 feat-014-a2a-call-event-forwarding.md
}
```

#### 2.3.3 调用响应状态枚举 `InvocationResponseStatus`

gateway 侧观测态（与 outbox 传输态 `ForwardingStatus` 区分；后者是 bus 投递层 PENDING/DISPATCHING/ACKED/...，前者是调用语义层）。

| 状态 | 事实语义 |
|---|---|
| `COMPLETED_RESPONSE` | 阻塞等待窗口内已获最终 A2A 响应 |
| `ACCEPTED_WITH_TASK` | 服务端已建/复用 Task 并返回 taskId，但最终响应未完成/不在窗口内 |
| `STREAM_READY` | 服务端 Task 的 A2A SSE 流已可订阅，gateway 可桥接 |
| `REJECTED` | 服务端明确拒绝、未建 Task |
| `FAILED` | 调用事件或服务端处理确定失败 |
| `UNKNOWN` | gateway 未能在接受等待窗口内确认服务端是否建 Task；不等同成功/失败 |

#### 2.3.4 行为承诺（RFC 2119）

- **必须**：gateway 在接受等待窗口超时且无法确认建 Task 时返回 `UNKNOWN`，并提供可重试的 `idempotencyKey`/`clientInvocationId`。
- **必须**：gateway 已观察到 `INVOCATION_ACCEPTED` 且获 taskId 后，若最终响应超时，返回 `ACCEPTED_WITH_TASK` 而非 `UNKNOWN`。
- **必须**：`INVOCATION_ACCEPTED` 与 `INVOCATION_STREAM_READY` 分离表达，不得互替。
- **禁止**：broker 消息体承载 token chunk / SSE frame / payload body / Task execution state（body 仅 routing descriptor，对齐 `BrokerOutboundMessage` §6.2②）。
- **禁止**：跨 tenant fallback（envelope `tenantId == routeHandle.tenantScope` + broker poll header tenant check）。
- **允许**：小型 A2A JSON-RPC envelope 作为 inline payload 出现在 `payloadRef` 引用的数据中，但不承担治理字段职责。

---

## 3. 模块结构（Development View）

### 3.1 包结构

```
com.huawei.ascend.bus.
├── forwarding.spi.         # 复用：ForwardingEnvelope(扩展) / ForwardingOutboxPort / ForwardingInboxPort /
│                            #      ForwardingDispatcher / ForwardingStatus / ForwardingFailureCode / ForwardingRouteHandle
├── forwarding.runtime.      # 复用：ForwardingStateMachine / ForwardingDispatcherWorker / ForwardingDispatchLoop /
│   └── transport.           #   复用 + net-new
│       ├── broker.          #   复用 SPI（BrokerForwardingRelayPort/ConsumerPort/...）+ net-new RocketMQ adapter（confined）
│       └── a2a.             #   既有 A2aForwardingDeliveryPort（T1 push，本特性不用于 event-bus→agent-runtime，保留共存）
├── gateway.                 # ⬜ net-new
│   ├── spi.                 #   IngressGateway / IngressEnvelope / IngressResponse（既有 SPI，gateway 实现消费之）
│   └── runtime.             #   gateway 生产实现：HTTP 入口 / envelope 封装 / broker produce / SSE 桥接 / 接受等待窗口
├── registry.runtime.        # 复用：MvpRegistryController / PgMvpDiscoveryServiceImpl / RouteHandleCodec（route handle 产生/解析）
└── (AgentBusApplication)    # gateway 进程与 event-bus+registry 进程分别打包启动（见 §5）
```

> agent-runtime 侧 net-new 落点（外仓 `agent-runtime-java/service/agent-service-app`）：`com.openjiuwen.service.app.controller.broker`（consumer 解码信封→`ServeRequest`→`ServeOrchestrator.query/streamQuery`；producer tap `AgentEmitter` 回调发 `INVOCATION_*`）+ `BrokerAutoConfiguration`。详见 feat-014 §3（FEAT-014 的 agent-runtime 落点同源，事件族不同）。

### 3.2 核心类静态关系

```
«interface»                  «concrete»                       «interface»
IngressGateway               GatewayRuntimeController          BrokerForwardingRelayPort
  ↑  (既有 SPI)                 (net-new, gateway 进程)              ↑  (既有 SPI)
  └─ implements ───────────────┘                                   │ implements
                                  │                                │
                                  │── builds ──> ForwardingEnvelope(扩展) ── enqueue ──> ForwardingOutboxPort
                                  │                                                                    │
                                  │── produce ──> BrokerForwardingRelayPort ─────────────────────────┘
                                  │                                          │
                                  │── consume resp <── BrokerForwardingConsumerPort (gateway 侧)
                                  │
                                  └── bridges SSE <── (stream 引用 → agent-runtime A2A SSE) ──> SseEmitter(to client)
```

---

## 4. 核心设计（Logical + Process View）

### 4.1 两跳 broker 时序（前向 + 响应）

```
Client        Gateway进程           RocketMQ            EventBus+Registry进程        RocketMQ           AgentRuntime进程
  │  HTTP/A2A  │                      │                       │                       │                      │
  │── POST /a2a (Send*) ──────────────>│                      │                       │                      │
  │           │ build ForwardingEnvelope(CLIENT_INVOCATION_REQUESTED, source=gateway,│target=agent-runtime)│
  │           │── enqueue ────────────────────────────────> ForwardingOutboxPort (gateway outbox, dedup tenantId+messageId)
  │           │── worker claimDue ──> BrokerForwardingRelayPort.produce ──> T_invocation_req ──> │             │
  │           │                      │                       │── poll(consumer=event-bus, tenant) ── <T_invocation_req │
  │           │                      │                       │── Inbox.receive dedup (tenantId,messageId,consumerServiceId)
  │           │                      │                       │── commit (model B ack) ──> T_invocation_req ack          │
  │           │                      │                       │── re-publish: ForwardingEnvelope(同 eventType) outbox ──> BrokerForwardingRelayPort.produce ──> T_invocation_deliver ──> │
  │           │                      │                       │                                                          │── poll(consumer=runtime-<serviceId>) ── <T_invocation_deliver
  │           │                      │                       │                                                          │── Inbox dedup
  │           │                      │                       │                                                          │── ServeOrchestrator.query/streamQuery → Task lifecycle
  │           │                      │                       │                                                          │
  │           │                      │                       │                                                          │── produce INVOCATION_ACCEPTED ──> T_invocation_resp_in ──> │
  │           │                      │                       │<── poll(consumer=event-bus) ─── T_invocation_resp_in ───────┘
  │           │                      │                       │── Inbox dedup + correlation match
  │           │                      │                       │── re-publish INVOCATION_ACCEPTED outbox ──> produce ──> T_invocation_resp_out ──> │
  │           │<── poll(consumer=gateway) ─── T_invocation_resp_out ─────────────────────────────────────────────────────│
  │           │── match correlationId/requestId → InvocationResponseStatus
  │<── A2A-compatible response (阻塞一次性响应 / 已接受 Task 引用 / UNKNOWN) ──────────────────────────────────────────│
```

> event-bus 在两跳间是**治理中继**：inbox 去重 + tenant 校验 + correlation 匹配 + 审计；非字节透传。响应路径对称（agent-runtime→event-bus→gateway）。

### 4.2 gateway 组件

| 组件 | 职责 | 复用/net-new |
|---|---|---|
| `GatewayRuntimeController` | A2A 兼容 HTTP 入口（`POST /a2a` 阻塞 + `SendStreamingMessage` SSE）；解析 `IngressEnvelope`（`requestId`/`tenantId`/`idempotencyKey`/`requestType`/`traceId`） | net-new（消费既有 `IngressGateway` SPI） |
| Envelope 封装 | `IngressEnvelope` → `ForwardingEnvelope`(eventType=按 requestType 映射 `CLIENT_INVOCATION_REQUESTED`/`_CANCEL_`/`_QUERY_`/`CLIENT_STREAM_SUBSCRIBE_REQUESTED`)；生成 `messageId`；`correlationId`=`requestId`；`sourceServiceId`=gateway；`targetServiceId` 经 registry route handle 解析 | net-new |
| 客户端幂等 | 客户端重试同一调用时复用同一 `idempotencyKey`（`IngressEnvelope.idempotencyKey`） | net-new |
| broker produce | `ForwardingOutboxPort.enqueue` → worker `claimDue` → `BrokerForwardingRelayPort.produce` → RocketMQ | ✅ 复用 substrate + ⬜ RocketMQ adapter |
| 接受等待窗口 | 窗口内消费 `INVOCATION_ACCEPTED`/`_REJECTED`/`_FAILED`/`_RESPONSE`；超时且无 accepted → `UNKNOWN`；已 accepted 后超时 → `ACCEPTED_WITH_TASK` | net-new |
| 响应状态归并 | 按 `correlationId` 匹配响应事件 → `InvocationResponseStatus` | net-new |
| SSE 桥接 | 收到 `INVOCATION_STREAM_READY`(stream 引用) 后与 agent-runtime 建 A2A SSE 通道，桥接给客户端 `SseEmitter`；客户端断开则停止桥接，不后台缓存 token | net-new（复用 agent-runtime SSE 表面） |

### 4.3 调用响应状态机

| 当前观测态 | 收到事件 | 下一观测态 | 附加行为 |
|---|---|---|---|
| `PENDING`（已 produce 请求） | `INVOCATION_RESPONSE` | `COMPLETED_RESPONSE` | 返回客户端一次性 A2A 响应 |
| `PENDING` | `INVOCATION_ACCEPTED` | `ACCEPTED_WITH_TASK` | 返回 taskId 引用 |
| `PENDING` | `INVOCATION_REJECTED` | `REJECTED` | 返回可编程拒绝 |
| `PENDING` | `INVOCATION_FAILED` | `FAILED` | 返回错误码 + 可重试语义 |
| `PENDING` | （接受窗口超时，无 accepted/rejected/failed） | `UNKNOWN` | 返回 UNKNOWN + idempotencyKey 供重试 |
| `ACCEPTED_WITH_TASK` | `INVOCATION_RESPONSE` | `COMPLETED_RESPONSE` | 返回最终响应 |
| `ACCEPTED_WITH_TASK` | `INVOCATION_STREAM_READY` | `STREAM_READY` | 建 SSE 桥接 |
| `ACCEPTED_WITH_TASK` | `INVOCATION_TERMINAL`(失败/取消) | `FAILED` | 收尾 |
| `STREAM_READY` | `INVOCATION_TERMINAL`(完成) | `COMPLETED_RESPONSE` | 关闭 SSE，收尾 |
| `UNKNOWN` | 客户端同 idempotencyKey 重试 | （服务端幂等返回同一 taskId 或按新投递创建/拒绝） | 不创建第二个逻辑调用 |

> 状态机是 gateway 侧**观测态**，与 outbox 传输态 `ForwardingStatus`（PENDING/DISPATCHING/ACKED/RETRY_SCHEDULED/DLQ/EXPIRED）正交：前者是调用语义，后者是 bus 投递层。

### 4.4 幂等三层

| 层次 | 幂等键 | owner | 复用/net-new |
|---|---|---|---|
| bus 投递 | `(tenantId, messageId)` outbox / `(tenantId, messageId, consumerServiceId)` inbox | agent-bus forwarding substrate | ✅ 复用 `JdbcForwardingOutbox`/`JdbcForwardingInbox`（`ON CONFLICT DO NOTHING` + `SKIP LOCKED` claim） |
| 服务端创建 | `(tenantId, idempotencyKey)` | agent-runtime | ⬜ net-new（agent-runtime 当前无此去重，Redis `a2a:task:<taskId>` 无 tenant 前缀） |
| gateway 客户端重试 | `idempotencyKey`（同调用复用） | gateway | ⬜ net-new（gateway 复用 `IngressEnvelope.idempotencyKey`） |

> `idempotencyKey` 在 envelope 上是**业务幂等键**（服务端创建去重依据）；`messageId` 是 bus 投递去重键。二者不得混同（对齐 forwarding-outbox-inbox §5 MI8-004 收口）。

### 4.5 租户隔离（复用 + broker 层）

| 层 | 机制 | 复用 |
|---|---|---|
| 应用层 | 所有 SQL `WHERE tenant_id = :tenantId`（R-C.c），跨 tenant 返回空/显式失败 | ✅ |
| DB 层 (RLS) | outbox/inbox/registry 表 `ENABLE ROW LEVEL SECURITY` + policy `tenant_id = current_setting('app.tenant_id', true)`，fail-closed | ✅ |
| GUC 接线 | `JdbcForwardingOutbox.withTenant` / `JdbcAgentRegistryRepository.withTenant` 事务内 `set_config('app.tenant_id', :tenantId, true)` ≡ SET LOCAL | ✅ |
| broker 层 | `BrokerForwardingConsumerPort.poll(consumerServiceId, tenantId, …)` 不返回 header tenantId ≠ poll tenantId 的消息（L2 header-tenant-check §6.2⑤） | ✅（SPI 已落，RocketMQ adapter 需实现） |
| envelope 层 | `ForwardingEnvelope` compact constructor 强制 `tenantId == routeHandle.tenantScope()` else `tenant_mismatch` | ✅ |

### 4.6 A2A SSE 流桥接

- gateway 收到 `INVOCATION_STREAM_READY`（携带 `taskId` + stream 引用）后，**仅在客户端与 gateway 的连接仍存在时**，按 stream 引用与 agent-runtime 建立 A2A SSE 通道，桥接给客户端 `SseEmitter`。
- 实时 token chunk **不进 broker**；broker 只传 `INVOCATION_STREAM_READY` 控制信号 + stream 引用。
- 客户端断开 → gateway 停止桥接，不长期后台消费/缓存 token 流（FEAT-013 §5.1.5）。
- 流重连：客户端持有 `taskId` 后可经 `CLIENT_STREAM_SUBSCRIBE_REQUESTED` 重新订阅；`SubscribeToTask` 不得隐式建新 Task。

---

## 5. 配置模型（Physical View）

### 5.1 部署拓扑（本期决策）

```
┌─────────────────┐        RocketMQ         ┌──────────────────────────────┐        RocketMQ         ┌────────────────────┐
│ Gateway 进程     │ ─── T_invocation_req ──> │ EventBus + Registry 进程      │ ── T_invocation_deliver ──> │ AgentRuntime 进程   │
│ (agent-bus       │ <── T_invocation_resp_out│ (agent-bus forwarding         │ <── T_invocation_resp_in ── │ (外仓 agent-runtime │
│  gateway 单元 +   │                         │  substrate inbox+outbox      │                            │  -java，net-new     │
│  转发底座 outbox)│                         │  +worker+relay + registry)   │                            │  broker consumer/   │
└─────────────────┘                         └──────────────────────────────┘                            │  producer)         │
                                                                                                          └────────────────────┘
```

- **Gateway 进程**：agent-bus gateway 单元 + 转发底座（outbox + worker + `BrokerForwardingRelayPort` + RocketMQ adapter）。单独进程。
- **EventBus + Registry 进程**：转发底座（inbox 收 hop1 + outbox 发 hop2 + worker/relay）+ registry-discovery（route handle 产生/解析）。同进程，模块独立可替换。
- **AgentRuntime 进程**：外仓 `agent-runtime-java`；net-new broker consumer（`BrokerForwardingConsumerPort` 契约）+ producer + inbox。
- **RocketMQ**：独立 broker 基础设施，不在任一应用进程内。

> 三单元独立可替换：gateway / event-bus / registry 各为可独立构建/部署的运行时单元；客户可用存量 API 网关/消息平台/注册发现系统替换其中任一，前提是满足本文事件信封/租户/trace/幂等/路由引用/响应状态/A2A 兼容边界。

### 5.2 完整配置示例

```yaml
agent-bus:
  gateway:
    a2a:
      base-url: http://0.0.0.0:8080/a2a        # 对客户端的 A2A 兼容入口
    accept-window:                             # 接受等待窗口
      accept-timeout-ms: 5000                  # 无 accepted/rejected/failed → UNKNOWN
      response-timeout-ms: 30000               # 已 accepted 后等最终响应
    sse:
      bridge-idle-timeout-ms: 60000             # 客户端断开后停止桥接的宽限
  broker:
    rocketmq:
      nameserver-endpoints: 10.0.0.10:9876;10.0.0.11:9876
      namespace: ascend-prod                   # tenant 隔离作用域（topic 前缀/namespace 分区）
      producer-group: gateway-producer
      consumer-group: gateway-consumer          # 消费响应 topic
      topics:
        invocation-req: ascend_bus_invocation_req
        invocation-deliver: ascend_bus_invocation_deliver
        invocation-resp-in: ascend_bus_invocation_resp_in
        invocation-resp-out: ascend_bus_invocation_resp_out
  forwarding:                                   # 复用既有 forwarding 配置（见 forwarding-persistence §5）
    retry-policy:
      base-ms: 100
      cap-ms: 60000
      max-attempts: 5
```

### 5.3 配置属性表（节选 net-new / 关键项）

| 属性路径 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|
| `agent-bus.gateway.a2a.base-url` | String | — | 是 | 对客户端 A2A 入口 |
| `agent-bus.gateway.accept-window.accept-timeout-ms` | long | 5000 | 否 | 接受窗口超时→UNKNOWN |
| `agent-bus.gateway.accept-window.response-timeout-ms` | long | 30000 | 否 | 已 accepted 后等最终响应 |
| `agent-bus.broker.rocketmq.nameserver-endpoints` | String | — | 是 | RocketMQ nameserver（映射 `BrokerClientProperties.nameserverEndpoints`） |
| `agent-bus.broker.rocketmq.namespace` | String | — | 是 | tenant 隔离作用域（映射 `BrokerClientProperties.namespace`） |
| `agent-bus.broker.rocketmq.topices.invocation-req` | String | ascend_bus_invocation_req | 否 | gateway→event-bus 前向 |
| `agent-bus.broker.rocketmq.topics.invocation-deliver` | String | ascend_bus_invocation_deliver | 否 | event-bus→agent-runtime 前向 |
| `agent-bus.broker.rocketmq.topics.invocation-resp-in` | String | ascend_bus_invocation_resp_in | 否 | agent-runtime→event-bus 响应 |
| `agent-bus.broker.rocketmq.topics.invocation-resp-out` | String | ascend_bus_invocation_resp_out | 否 | event-bus→gateway 响应 |

> topic 经 `ForwardingEndpointResolver` 由 routeHandle 映射（HD4 opaque，gateway/agent-runtime 不读 topic value）；上表命名约定为默认，可由 resolver 覆写。

---

## 6. 对外呈现 / 用户场景（Scenario View）

### 6.1 外部接口（客户端视角，A2A 兼容）

| 端点 | 方法 | 说明 |
|---|---|---|
| `POST /a2a` | HTTP POST | A2A JSON-RPC 入口：`SendMessage`（阻塞）/ `SendStreamingMessage`（SSE）/ `GetTask` |
| `GET /.well-known/agent-card.json` | HTTP GET | Agent 能力发现（gateway 代理或直连 registry） |

> gateway 对客户端暴露 A2A 兼容表面；客户端不感知 broker/event-bus/topic/服务端实例。CancelTask/SubscribeToTask/ListTasks 依赖 FEAT-001 在 agent-runtime 侧补齐路由（见 §8）。

### 6.2 用户场景（对齐 FEAT-013 §4）

#### 6.2.1 阻塞调用返回最终响应
```
Client                Gateway                         AgentRuntime
  │── POST /a2a SendMessage ─>│                          │
  │                     │── CLIENT_INVOCATION_REQUESTED ──broker──event-bus──broker──>│
  │                     │<── INVOCATION_RESPONSE ──broker──event-bus──broker──────────│
  │<── A2A 一次性响应 ───│
```

#### 6.2.2 阻塞退化为 Task 引用（窗口内未完成）
```
Client                 Gateway                      AgentRuntime
  │── POST /a2a ────────>│                             │
  │                     │── CLIENT_INVOCATION_REQUESTED ─broker─>│ (建 Task)
  │                     │<── INVOCATION_ACCEPTED(taskId) ─broker──│
  │                     │  (response 窗口超时)
  │<── 202 已接受 Task 引用(taskId) ─│
  │  (客户端后续 GetTask/SubscribeToTask)
```

#### 6.2.3 接受窗口 UNKNOWN + 同键重试
```
Client                 Gateway                      AgentRuntime
  │── POST /a2a(idempotencyKey=K) ─>│                │
  │                     │── CLIENT_INVOCATION_REQUESTED ─broker─>│ (broker/服务端无回响)
  │                     │  (accept 窗口超时, 无 accepted)
  │<── UNKNOWN + idempotencyKey=K ──│
  │── POST /a2a(idempotencyKey=K) ─>│ (同键重试)
  │                     │   (服务端若已建 Task → INVOCATION_ACCEPTED 同 taskId；否则按新投递创建/拒绝)
```

#### 6.2.4 流式调用建立
```
Client                 Gateway                         AgentRuntime
  │── POST /a2a SendStreamingMessage ─>│                    │
  │                     │── CLIENT_INVOCATION_REQUESTED ─broker─>│
  │                     │<── INVOCATION_ACCEPTED(taskId) ─broker──│
  │                     │<── INVOCATION_STREAM_READY(stream引用) ─broker──│
  │                     │── (按 stream 引用建 A2A SSE 通道) ───────────────>│
  │<── SSE token stream <── (桥接) <─────────────────────────────────────│
```

#### 6.2.5 流重连 / 取消 / 查询 / 拒绝 / 终态
- 流重连：`CLIENT_STREAM_SUBSCRIBE_REQUESTED(taskId)` → `INVOCATION_STREAM_READY` → 重建 SSE。
- 取消：`CLIENT_INVOCATION_CANCEL_REQUESTED(taskId)` → 服务端按 A2A CancelTask 处理 → `INVOCATION_TERMINAL`。
- 查询：`CLIENT_INVOCATION_QUERY_REQUESTED(taskId)` → `INVOCATION_RESPONSE`(Task 快照)。
- 拒绝：服务端 `INVOCATION_REJECTED` → gateway 返回可编程拒绝，不伪造 taskId。
- 终态：`INVOCATION_TERMINAL` → gateway 收尾/审计；实时流以 A2A SSE/Task 查询为准。

---

## 7. 错误处理（Process View）

| 错误场景 | 触发 | bus 行为 | 对外结果 |
|---|---|---|---|
| 调用事件信封非法 | envelope 构造违反（tenant 空白/payloadRef 缺等） | gateway 拒绝发布 / event-bus 拒绝消费 | 客户端 400 可编程错误；不建 Task |
| tenant 不匹配 | `envelope.tenantId != routeHandle.tenantScope` 或 broker poll header tenant check 失败 | 拒绝（不入队 / inbox `REJECTED` / broker reject）→ `ForwardingFailureCode.TENANT_MISMATCH` | 400 + 审计；禁止跨 tenant fallback |
| route handle 不可解析 | `ForwardingEndpointResolver` 返回空 | `BrokerProduceOutcome.ROUTE_NOT_FOUND` → 非 retryable → DLQ | `route_not_found`；不暴露物理 endpoint 作恢复 |
| broker 不可达 | RocketMQ 瞬时不可达 | `BrokerProduceOutcome.UNAVAILABLE` → retryable → `ForwardingFailureCode.RECEIVER_UNAVAILABLE` / `DELIVERY_TIMEOUT` | retry policy 退避重投；窗口内未恢复→UNKNOWN |
| 服务端拒绝 | 鉴权/租户/能力/输入/策略 | `INVOCATION_REJECTED` | 可编程拒绝；不伪造 taskId |
| 服务端建 Task 后阻塞超时 | 已 `INVOCATION_ACCEPTED` 但最终响应超时 | 返回 `ACCEPTED_WITH_TASK`（不误报 UNKNOWN） | Task 引用 + 后续 GetTask/Subscribe |
| 接受阶段超时 | accept 窗口内无 accepted/rejected/failed | `UNKNOWN` | UNKNOWN + idempotencyKey 供重试 |
| 流准备超时 | 已有 taskId 但无 stream ready | 客户端可 `CLIENT_STREAM_SUBSCRIBE_REQUESTED(taskId)` 重连 | 无 taskId → UNKNOWN |
| 实时流中断 | SSE 断开 | 客户端经 taskId 重新订阅；bus 不重放 token chunk | `INVOCATION_TERMINAL` 收尾 |
| 服务端终态失败 | Task FAILED/CANCELED/REJECTED | `INVOCATION_TERMINAL`（映射 `ForwardingFailureCode.REMOTE_TASK_FAILED` non-retryable，不消耗 retry 预算） | A2A Task 失败语义；保留 correlation/trace |

---

## 8. 限制与待补

### 8.1 net-new 项（本期设计，未实现）

| 项 | 落点 | 备注 |
|---|---|---|
| gateway 生产实现 | `com.huawei.ascend.bus.gateway.runtime`（net-new 包） | HTTP 入口 + envelope 封装 + 接受窗口 + SSE 桥接 |
| `AgentBusEventType` 枚举 + `InvocationResponseStatus` | `forwarding.spi`（或新 `event.spi`） | FEAT-013 族；FEAT-014 族见 feat-014 |
| `ForwardingEnvelope` 扩展（eventType/source/target） | `forwarding.spi/ForwardingEnvelope.java` | additive；compact constructor 增校验；SqlCodec/record 镜像同步 |
| RocketMQ 具体 adapter | `forwarding.runtime.transport.broker`（confined） | 实现 `BrokerForwardingRelayPort`/`ConsumerPort`；ArchUnit 已授权（vacuously green） |
| agent-runtime 侧 broker consumer/producer | 外仓 `agent-runtime-java/service/agent-service-app`（`controller.broker` + `BrokerAutoConfiguration`） | 详见 feat-014 §3/§4（同源落点，事件族不同） |
| 服务端创建幂等 `tenantId+idempotencyKey` | agent-runtime（`RedisTaskStore` 需加 tenant 前缀 + 幂等索引） | 当前无；`X-Tenant-Id` 仅 header 透传 |
| FEAT-001 缺口：CancelTask/SubscribeToTask/ListTasks 路由 | agent-runtime `A2aJsonRpcController` | 当前仅路由 SendMessage/SendStreamingMessage/GetTask，其余 -32601 |

### 8.2 复用项（已落地，不改语义）

- 转发底座 outbox/inbox/worker/状态机/JDBC/RLS（`forwarding.spi`/`forwarding.runtime`/`persistence.jdbc`，217 tests green）。
- broker-agnostic SPI（`BrokerForwardingRelayPort`/`ConsumerPort`/`BrokerOutboundMessage`/`BrokerInboundMessage`/`BrokerMessageHeaders`/`BrokerProduceOutcome`/`BrokerClientProperties` + `InMemoryBroker` 契约测试）。
- registry-discovery（`MvpRegistryController`/`PgMvpDiscoveryServiceImpl`/`RouteHandleCodec`）。
- `ForwardingFailureCode`（8 码，含 `REMOTE_TASK_FAILED`）/ `ForwardingStatus` / `ForwardingRetryPolicy` / `RouteCircuitBreaker`。
- tenant 隔离三层（app/RLS/GUC）。

### 8.3 与 Stage 叙事的关系

- 本 L2 为 FEAT-013 本期决定接线方向，**非推翻** Stage 1→26 叙事。Stage 25 已裁决投递模型 T4 hybrid（outbox + broker，`adopted-t4`）；Stage 26 已落 broker-agnostic SPI 骨架 + 锁定 RocketMQ（`transport.broker` 子包，217 tests green，真实实例 PoC deferred 部署环境）。
- 本 L2 推进的 Stage 27+ 事项（FEAT-013 范围内）：relay adapter 接 worker / `AWAITING_ACK` 状态机 / 模型 B 反向 ack / receiver consumer / 生产 TickSource。这些在 FEAT-013 实施切片中落地，具体 Stage 编号由交付波次编排。
- **event-bus→agent-runtime 不走 a2a push**：现有 `A2aForwardingDeliveryPort`（T1 HTTP sync push）在本特性范围内被 broker pub/sub 取代；T1 PoC 保留共存/灰度切换（Stage 30 T1→T4 切换），但 FEAT-013/014 投产路径为 broker。

### 8.4 不承诺项（对齐 FEAT-013 §5.2）

不承诺 token chunk 事件化 / bus 拥有 Task 状态 / clientInvocationId 替代 taskId / 隐式重建 Task / 私有流协议 / 物理 endpoint 暴露 / 具体 broker 产品强绑 / 单体部署 / 大载荷数据通道 / 非 A2A 查询扩展（`ResolveInvocation`）。
