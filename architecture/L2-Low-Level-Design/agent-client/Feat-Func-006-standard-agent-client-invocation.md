---
level: L2-LLD
module: agent-client
feature_type: functional
feature_id: FEAT-006
status: proposed
authority: non-authoritative
dependency:
  - ../../../version-scope/FEAT-006-standard-agent-client-invocation.md
  - ../../L1-High-Level-Design/agent-client/overview.md
  - ../../L1-High-Level-Design/agent-client/logical.md
  - ../../L1-High-Level-Design/agent-client/process.md
  - ../../L1-High-Level-Design/agent-client/development.md
  - ../agent-runtime/Feat-Func-009-调用端侧工具响应-新增支持带有端侧工具的请求.md
  - ../agent-bus/feat-011-l2_V3.0.md
  - ../../../agent-client/docs/proposals/agent-client-v1-design.md
  - ../../../agent-client/examples/cloud-client/README.md
---

# 标准化智能体服务调用 — 设计文档（FEAT-006 L2）

> 目标模块：`agent-client`（edge plane SDK）
> 事实来源（authoritative）：`version-scope/FEAT-006-standard-agent-client-invocation.md`（本 L2 是其实现级细化，术语与语义以 FEAT-006 为准）
> 参照实现：`agent-client/examples/cloud-client/`（可运行原型，JDK 17；原型符号名为早期形态，正文已按 FEAT-006 术语对齐，原型代码待机械跟随，见 §9）
> 最后更新：2026-07-21
> **🎯 本迭代交付范围（重要）：** 下游 runtime 本迭代只支持**最小非故障链路**。因此本 L2 的**本迭代交付面** = ①创建新会话调用 ②端侧工具结果续跑（内部，属 Feat-Func-007）③用户补充输入 `continueInput` ④普通多轮对话；调用模式**只接线 `STREAMING`**。**查询/取消/重订阅/UNKNOWN 恢复/断线补偿、`BLOCKING`/`ASYNC`** 是 FEAT-006 版本目标但**本迭代不交付**（见 §2.2），不进入本迭代交付的公共接口。
> **🔗 wire 基线：** client↔gateway 采用标准 A2A JSON-RPC 2.0 over HTTP + SSE，对齐 runtime `agent-runtime/Feat-Func-009`，并与 gateway 边界文档 `agent-bus/feat-011-l2_V3.0.md`（FEAT-011 客户端调用直连路由转发）冻结的 client↔gateway 契约一致（本迭代：`SendStreamingMessage` 创建走 SSE，`SendMessage` 续跑走同步单次 JSON 响应）。HTTP 承载/鉴权见 §3.5.0，标识映射见 §3.5。**唯一拓扑差异：client 连 gateway，由 gateway 受治理透传到 runtime**（见 §8）。
> **✅ 边界冻结（2026-07-22）：** 本文与 `feat-011-l2_V3.0.md` §4.9 / §5.9 / §6.9 的 client↔gateway 对齐结论已双方冻结（AC-1 选 A：创建允许缺省 `agentId`；AC-2～AC-8、AC-S3-*、AC-S4-* 同意）。下列正文已按该冻结结论落地。

---

## 1. 概述

### 1.1 特性定位

`agent-client` 作为 edge plane SDK，为业务应用提供**标准化的智能体服务调用入口与客户端侧状态管理**：业务用统一 facade 发起调用、消费输出、按需续跑（工具结果/用户输入），只面对稳定的客户端调用语义（`conversationId`、`invocationRef`、调用模式、状态投影、等待输入、错误分类），**不感知** A2A JSON-RPC、Gateway 路由、runtime endpoint 或服务端 `taskId`。

- **解决的问题**：不同业务应用用同一套客户端调用语义接入平台；把"客户端本地进度"与"服务端权威 Task 生命周期"解耦，并屏蔽 A2A/SSE 报文细节。
- **适用场景**：Web/BFF、后端业务系统、桌面/服务端应用发起智能体调用并消费流式输出。**不适用**：服务端 runtime-to-runtime 调用（属 agent-runtime）。

### 1.2 当前事实边界

本文描述 FEAT-006 的**拟议 L2 设计**，并按下游本迭代能力**收敛到最小非故障链路**（见顶部交付范围与 §2）。生产实现尚未落地；最佳实践与测试见 `agent-client/docs/getting-started.md`；模块级决策/路线图见 `agent-client-v1-design.md`（已声明被 FEAT-006/007 取代）。wire 语义以 §3.5 为准，与 `Feat-Func-009` 一致。

### 1.3 设计原则

1. **三层主权分离** — `conversationId` 业务应用主权、`invocationRef`/`invocationId` client 主权、`taskRef`(=A2A `taskId`) runtime 主权。业务应用**只用 `invocationRef`** 操作调用；`taskRef` 仅 client 内部映射与诊断，**不得**成为业务操作句柄。
2. **客户端只投影，不拥有** — 服务端 Task lifecycle 权威 owner 是 `agent-runtime`；SDK 只维护 invocation 本地投影与 `invocationRef→taskRef` 映射，不写服务端权威状态、不建第二套 TaskStore。
3. **公共 API 框架中立** — 公共签名只出现 JDK 类型与 SDK 自有值对象，A2A/HTTP/JSON 库类型隔离在 transport adapter 内。
4. **调用模式是端到端诉求，不静默降级** — 创建时声明模式；本迭代只支持 `STREAMING`，传入未支持模式返回明确错误，不伪造结果、不静默降级（见 §2.5）。
5. **交付面即能力面** — 本迭代不交付的能力**不出现在公共接口**（避免误导下游）；其版本目标定位与对 gateway/runtime 的诉求单列（§2.2、§8）。
6. **拓扑保留，语义不变** — client 连 gateway、gateway 受治理透传，不改写 A2A 语义（§8），wire 契约与"直连 runtime"等价。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 本迭代 |
|--------|------|---------|------|
| 调用创建与本地句柄 | `invoke`（conversationId + STREAMING）→ 本地句柄 | `AgentClient`, `InvocationRequest`, `InvocationCall` | ✅ 交付 |
| 事件归一化 | A2A status/artifact/message → 密封事件 | `InvocationEvent`（sealed） | ✅ 交付 |
| 状态投影与快照 | Task 状态本地投影（随 SSE） | `TaskState`, `InvocationSnapshot` | ✅ 交付 |
| 端侧工具结果续跑 | INPUT_REQUIRED(client_tool) → 内部恢复请求 | 见 Feat-Func-007 | ✅ 交付 |
| 用户补充输入续跑 | INPUT_REQUIRED(用户输入) → 新 invocation 关联旧 invocation | `AgentClient#continueInput`, `ContinueInputRequest` | ✅ 交付 |
| 普通多轮对话 | 复用 `conversationId` 发起新 invocation | `invoke` + 稳定 conversationId | ✅ 交付 |
| 传输抽象 | 领域操作 → A2A wire；OS/网络差异隔离 | `TransportProvider`（SPI） | ✅ 交付 |
| 查询 / 取消 / 重订阅 / 断线补偿 / UNKNOWN 恢复 | — | — | ⏸ 版本目标，本迭代不交付（§2.2） |
| `BLOCKING` / `ASYNC` 模式 | — | — | ⏸ 预留，本迭代不接线（§2.5） |

> 端侧**工具结果**续跑是"同一 invocation 下的内部恢复请求"（属 Feat-Func-007），**不是** `continueInput` 那种业务可见的新 invocation。二者区别见 §3.4。

---

## 2. 特性规格

### 2.1 本迭代交付能力

| 能力 | 状态 | 说明 |
|------|------|------|
| 标准调用 facade（创建） | ⬜ | `invoke`：创建新 invocation，回显 `conversationId`/`invocationRef`/幂等键/模式 |
| conversation 传递 | ⬜ | 业务传入或委托生成 `conversationId`，同 conversation 稳定传递；普通多轮=复用它再 `invoke` |
| 调用模式声明（STREAMING） | ⬜ | 创建声明 `mode`，本迭代只接线 `STREAMING`（见 §2.5） |
| invocation 回显 | ⬜ | 回显 `conversationId`/`invocationRef`/幂等键/模式/状态投影；不要求业务持有 `taskId` |
| 归一化事件流 | ⬜ | Accepted / StatusChanged / ContentDelta / InputRequired / Completed / Failed（随 SSE） |
| Task 状态投影 | ⬜ | `TaskState` 闭集 + `isTerminal()`；未知值映射 UNKNOWN |
| 端侧工具结果续跑 | ⬜ | 内部恢复请求，SDK 自动编排（见 Feat-Func-007） |
| 用户补充输入续跑 | ⬜ | `continueInput`：新 invocation 关联旧 invocation 的 input_required（见 §3.4） |
| 工具视图上报 | ⬜ | 创建时携带 `ToolExposurePolicy` → 计算 `ToolView` → `params.metadata.clientTools`（见 §3.2、Feat-Func-007） |
| 传输可替换 | ⬜ | `TransportProvider` SPI；默认 JDK HttpClient，测试用 in-process fake |

### 2.2 本迭代不交付（FEAT-006 版本目标，runtime 暂未支持）

> 以下能力在 FEAT-006（authoritative）中为版本目标（含 MUST 项），但下游 runtime 本迭代只跑最小非故障链路，故**本迭代不实现、不进入公共接口**；待 runtime/gateway 支持后再按 FEAT-006 补齐（对 gateway 的诉求见 §8 G-6）。

| 能力 | FEAT-006 定位 | 本迭代处置 | 依赖方 |
|------|--------------|-----------|--------|
| 取消 `cancel` | MUST | 不提供接口；架构师已确认 runtime 本迭代不支持 `CancelTask` | runtime + gateway |
| 快照查询 `getInvocation` | MUST | 不提供接口（happy path 由 SSE 事件流即可观察） | runtime + gateway（`GetTask`） |
| 重订阅 `resubscribe` | MUST | 不提供接口（本迭代不做断线补偿） | runtime + gateway（`tasks/resubscribe`） |
| 断线补偿 | 派生 | 不做（依赖查询/重订阅） | — |
| UNKNOWN 恢复 / 创建幂等 | MUST | 不做（非故障链路无 UNKNOWN；不新增 `ResolveInvocation`） | gateway（messageId 去重） |
| `BLOCKING` / `ASYNC` 模式 | MUST | `mode` 字段保留，仅 `STREAMING` 接线，其余返回明确 UNSUPPORTED（§2.5） | runtime + gateway |

### 2.3 显式排除（架构非目标，非"暂缓"）

| 排除项 | 原因 | 替代 |
|--------|------|------|
| 业务应用以 `taskId` 操作调用 | Task 主权属 runtime | 一律走 `invocationRef` |
| 服务端 Task 生命周期写入 | 权威 owner 是 agent-runtime | 只投影，经受治理入口发请求 |
| 客户端 webhook / S2C 回调入口 | 安全面与网络可达性风险 | 折叠为 Task 待输入意图 + client 主动多轮请求 |
| 第二套 run/job 状态机 | 避免与 A2A Task 事实冲突 | 统一投影到 `TaskState` |
| 独立 turn 概念 | FEAT-006 不引入 | 多轮=同 conversation 下多个 invocation |
| 一次 invoke 内并行多工具 | `Feat-Func-009` V1 单 pending | 单 pending 串行多轮（见 Feat-Func-007） |

### 2.4 接口契约（Logical View）

```java
/** 面向业务的稳定入口（本迭代交付面）。业务只用 invocationRef 操作；公共签名只用 JDK 类型与 SDK 自有值对象。 */
public interface AgentClient extends AutoCloseable {
    /** 创建调用：必须携带 conversationId 与调用模式（本迭代仅 STREAMING）。返回本地调用控制器。 */
    InvocationCall invoke(InvocationRequest request);
    /** 用户补充输入续跑：创建新 invocation 关联旧 invocation 的 input_required（业务可见）。 */
    InvocationCall continueInput(ContinueInputRequest request);
    LocalToolRegistry tools();   // 见 Feat-Func-007
    @Override void close();
    // 版本目标、本迭代不交付（见 §2.2）：
    //   getInvocation(invocationRef) / cancel(invocationRef, reason) / resubscribe(invocationRef)
    //   —— 待 runtime 支持 GetTask / CancelTask / tasks/resubscribe 后再补齐，不在本迭代接口暴露。
}

/** 一次本地调用控制器：以 invocationRef 为句柄，暴露 accepted/events/completion，不伪造服务端状态。 */
public interface InvocationCall extends AutoCloseable {
    String invocationRef();
    String conversationId();
    CompletionStage<Handle> accepted();          // Handle(invocationRef, conversationId, diagnosticTaskRef)
    Flow.Publisher<InvocationEvent> events();     // STREAMING 消费入口；可多订阅，尊重 demand
    CompletionStage<InvocationSnapshot> completion();
}

public enum InvocationMode { STREAMING, /* 预留，本迭代未接线： */ BLOCKING, ASYNC }
```

#### 数据类型

| 类型 | 关键字段 | 含义 | 约束 |
|------|---------|------|------|
| `InvocationRequest` | `agentId`(可选), `conversationId`, `mode`, `input`, `exposure`, `invocationId`, `idempotencyKey`, `trace`, `credentialContext` | 首轮调用请求 | `conversationId`/`mode` 非空；**`agentId` 可缺省**（缺省时 wire **不写** `params.metadata.agentId`，由 gateway 默认 Agent 选路；有则须非空、绝不空串，见 §3.5 ①，对齐 feat-011 AC-1/AC-2）；`mode` 非 STREAMING → UNSUPPORTED；`exposure` 可选（见下） |
| `InvocationRequest.exposure` | `ToolExposurePolicy` | 本次可暴露工具策略 | 缺省=空视图（不暴露任何本地工具）；client 据此算 ToolView 上报（Feat-Func-007） |
| `ContinueInputRequest` | `conversationId`, `relatedInvocationRef`(或 `inputRequiredRef`), `input`, `invocationId`, `idempotencyKey` | 用户补充输入续跑 | 关联不可续接/过期/多义 → 明确错误，不偷偷新建普通任务 |
| `InvocationEvent`（sealed） | `invocationRef()` + 6 个变体 | 归一化事件 | 变体闭集，transport 负责映射 |
| `TaskState`（enum） | `isTerminal()` | Task 状态本地投影 | 闭集 + UNKNOWN 兜底 |
| `InvocationSnapshot` | `invocationRef`, `state`, `terminal`, `pendingToolCall`, `diagnosticTaskRef` | invocation 只读投影 | `pendingToolCall` 仅 INPUT_REQUIRED 非空；`diagnosticTaskRef` 非操作性 |
| `Handle` | `invocationRef`, `conversationId`, `diagnosticTaskRef` | 接受回执 | `diagnosticTaskRef` 仅诊断，不作操作句柄 |

### 2.5 调用模式如何在同一入口实现（回应"一个接口如何支持三种模式/是否预留"）

`invoke()` 统一返回 `InvocationCall`，**三种模式复用同一句柄**，差异只在 transport 选用的 A2A 方法与调用方的消费方式：

| 模式 | transport 侧 | 调用方消费方式 | 本迭代 |
|------|-------------|--------------|--------|
| `STREAMING` | `SendStreamingMessage`（SSE） | 订阅 `events()` 增量消费，`completion()` 结算终态 | ✅ 交付 |
| `BLOCKING` | `SendMessage`（一次性响应） | 忽略 `events()`，直接 `await completion()` 取一次性结果 | ⏸ 预留（字段在、未接线） |
| `ASYNC` | `SendMessage`/`SendStreamingMessage` | `accepted()` 拿 `invocationRef` 即返回，后续靠查询/重订阅观察 | ⏸ 预留（且依赖 §2.2 的查询/重订阅） |

**预留策略**：`InvocationMode` 枚举与 `mode` 必填字段**保留**（对齐 FEAT-006、避免后续破坏性变更），但本迭代 transport adapter **只实现 STREAMING 分支**；传入 `BLOCKING`/`ASYNC` 时 `invoke()` 立即以明确的 `UNSUPPORTED_MODE` 错误结束（不静默降级为 STREAMING）。后续接线 `BLOCKING`/`ASYNC` 属 transport 层内部实现，不改公共 API 形状。

---

## 3. 核心实现（参照原型）

### 3.1 分层与传输隔离

```
业务应用
  │  只依赖 api 包（JDK 类型 + SDK 值对象；只见 invocationRef）
  ▼
AgentClient (api)  ──►  DefaultAgentClient (internal, core 编排 + invocationRef→taskRef 映射)
                              │
                              ▼  只依赖 TransportProvider SPI
                        TransportProvider (transport.spi)
                         ├── JDK HttpClient 实现：A2A JSON-RPC + SSE 连 gateway（生产，wire 见 §3.5）
                         └── InProcessFakeGateway（transport.fake，测试用，模拟 A2A 多轮）
```

wire 细节与 `invocationRef↔taskRef` 映射封装在 core/transport 层，公共 API 不感知。

### 3.2 调用创建与接受（含工具视图上报）

```
业务: client.invoke(request{conversationId, mode=STREAMING, input, exposure, invocationId, idempotencyKey})
  │  ① 工具视图：ToolView = exposure.toolViewFor(ctx)（见 Feat-Func-007 §3.1），映射为 clientTools
  │  ② 构造 CreateCommand(conversationId→contextId, invocationId/idempotencyKey→messageId, input, clientTools)
  │     agentId：仅当业务传入非空时写 params.metadata.agentId；缺省则整段省略（绝不空串），交 gateway 默认 Agent 选路
  ▼
DefaultAgentClient: transport.createAndStream(cmd) → Flow.Publisher<InvocationEvent>
  │  建立 invocationRef，内部绑定 taskRef（首帧 result.task.id）
  ▼
transport（A2A）: SendStreamingMessage（无 taskId 创建）POST 到 gateway
  │  首个事件 Accepted：结算 accepted() → Handle(invocationRef, conversationId, diagnosticTaskRef)
```

> **工具如何暴露给远端（回应架构师）**：本地工具的"注册"是开发期行为（Feat-Func-007），"暴露"则通过**本调用入口**完成——业务在 `InvocationRequest.exposure` 声明本次可暴露范围，client 计算出 `ToolView` 并放入创建请求的 `params.metadata.clientTools` 随本次调用上报（wire 见 §3.5 ①）。未声明 `exposure` → 空视图 → 不上报任何工具。ToolView 的计算细节见 Feat-Func-007 §3.1。

### 3.3 事件归一化

transport adapter 把 A2A 报文映射为密封事件（业务永不接触 A2A 类型），映射源与 `Feat-Func-009` 报文结构一致：

| A2A 报文（对齐 Feat-Func-009） | 归一化事件 |
|---------|-----------|
| 首个响应 / `result.task`（新建） | `Accepted(invocationRef)`（内部记录 taskRef、contextId） |
| `result.statusUpdate` / `result.task.status`（`TASK_STATE_*`） | `StatusChanged(invocationRef, TaskState, terminal)` |
| `Message` / `Artifact` 文本 part | `ContentDelta(invocationRef, text)` |
| `TASK_STATE_INPUT_REQUIRED` + `_interrupt`（`_interrupt_kind=client_tool`） | `InputRequired(invocationRef, ToolCall)`（端侧工具，见 Feat-Func-007） |
| `TASK_STATE_INPUT_REQUIRED`（无 `_interrupt`/非 client_tool） | `InputRequired(invocationRef, null)`（需用户补充输入 → 业务侧 `continueInput`） |
| `TASK_STATE_COMPLETED` | `Completed(invocationRef, summary)` |
| `TASK_STATE_FAILED` / JSON-RPC error | `Failed(invocationRef, errorCode, message)` |

> `TaskState` 枚举对齐 A2A（`TASK_STATE_SUBMITTED`/`WORKING`/`INPUT_REQUIRED`/`COMPLETED`/`FAILED`/`CANCELED`/`REJECTED`）；未识别值映射 UNKNOWN。

### 3.4 状态投影 + 两类"续跑"的区分

`TaskState` 是服务端状态在 client 侧的**只读投影**：

```
SUBMITTED → WORKING → (INPUT_REQUIRED ⇄ WORKING)* → COMPLETED / FAILED / CANCELED / REJECTED
```

**INPUT_REQUIRED 有两种成因，处理路径不同：**

| 成因 | 识别 | 处理 | 是否新 invocation |
|------|------|------|------------------|
| **端侧工具**（`_interrupt_kind=client_tool`） | 事件带 `ToolCall` | SDK 经治理入口执行并**内部恢复请求**续跑同一 Task（Feat-Func-007，业务不感知） | 否（内部恢复请求） |
| **用户补充输入** | INPUT_REQUIRED 无 client_tool 意图 | 业务侧感知，调用 `continueInput` 发**新 invocation** 关联旧 invocation | 是（业务可见新 invocation） |

#### 3.4.1 continueInput 实现（回应"看不到对应实现"）

`continueInput` 是本迭代交付项，其实现与"工具结果续跑"共用同一条 resume wire，差异只在**由业务发起 + 携带用户文本 + 建立业务可见的新 invocationRef**：

```
业务观察到 InputRequired(需用户输入) → 收集用户补充内容
  │  client.continueInput(ContinueInputRequest{conversationId, relatedInvocationRef=旧 invocationRef, input=用户文本, invocationId=新, idempotencyKey=新})
  ▼
DefaultAgentClient:
  ├─ 解析 relatedInvocationRef → 旧 invocation 的 taskRef（必须处于 INPUT_REQUIRED，否则明确错误）
  ├─ 新建 newInvocationRef，映射到同一 taskRef（一个 Task 可被多个 invocationRef 引用）
  └─ transport.resume(ResumeCommand(taskRef, messageId=新, contextId=conversationId, text=用户文本))
  ▼
wire: SendMessage(taskId=旧 taskRef, contextId, messageId=新, parts=[{text: 用户文本}])   ← 见 §3.5 ④
  │  runtime 校验恢复点，把用户输入回灌 Task 并推进（WORKING → …）
  ▼
返回新的 InvocationCall（events/completion 复用同一 Task 的后续投影）
```

> 与端侧工具续跑（Feat-Func-007 §3.5 ③）在 wire 上形态相同（`SendMessage` + `taskId` + `TextPart`），区别是：工具续跑是 SDK 自动发起的内部恢复请求、不产生业务可见 invocation；`continueInput` 由业务发起、产生业务可见的新 `invocationRef` 并通过 `relatedInvocationRef` 关联旧 invocation。关联不可续接/过期/多义 → 返回明确错误，不静默新建普通任务。

### 3.5 wire 契约（client↔gateway，对齐 Feat-Func-009 与 feat-011）+ 标识映射

#### 3.5.0 HTTP 承载与鉴权（对齐 feat-011 §3.3 / §4.9，SDK transport 保证）

wire 之上的 HTTP 承载与治理契约由 transport 统一实现，业务侧无感知：

| 项 | 约定 | 对应边界 |
|----|------|---------|
| 入口 | 单一 A2A JSON-RPC facade（gateway `POST /a2a`）；不新开第二套私有 stream URL | feat-011 GW-1/AC-6 |
| method | 只用 `SendMessage` / `SendStreamingMessage`（**不**用 `message/send`、`message/stream` 别名） | feat-011 GW-1/AC-6 |
| 鉴权 | **每一次**出站 HTTP（创建、流式建连、工具续跑、continueInput）带 `Authorization: Bearer <token>`；凭据只走 HTTP 头，**不进** A2A body/`params`/`metadata` | feat-011 G1 / AC-3 |
| 租户 | **不自报权威租户**（不发 `X-Tenant-Id`）；SDK 本地 `tenantId`（若配置）仅用于本地日志，不上 wire；权威租户由 gateway 从凭据解析注入 | feat-011 G2 / AC-4(GW-4) |
| 请求头 | `Content-Type: application/json`；流式创建 `Accept: text/event-stream`；同步/续跑 `Accept: application/json` | feat-011 AC-6 |
| `agentId` | 有则写 `params.metadata.agentId`（非空）；无则整段省略，**绝不空串**（空串触发 gateway G3 `VALIDATION_AGENT_ID`） | feat-011 AC-1/AC-2/GW-6 |
| 幂等键 | 创建用 `params.message.messageId`（=`invocationId`/`idempotencyKey`）；未获 `taskId` 的重试复用同键同正文；无键时**不**自动再发创建 | feat-011 G4 / AC-4 |
| 治理错误 | 401/403/400/409 等按 HTTP 治理错误处理，**不**投影为"已接受 Task"成功面 | feat-011 G9 / AC-7 |

> **创建 vs 续跑的响应形态（本迭代冻结，对齐 feat-011 §5.9.3）**：创建用 `SendStreamingMessage`，响应为 **SSE**（`event: jsonrpc` + 完整 JSON-RPC `data`）；SSE 下行到 `INPUT_REQUIRED` 后**流可关闭**。工具结果续跑与 `continueInput` 用 `SendMessage`，响应为**单次 JSON（非 SSE 续传）**，其 body 即该 Task 的下一状态（下一 `INPUT_REQUIRED` 或终态）。若 runtime 后续支持续跑 SSE，另文冻结。

**标识映射（FEAT-006 业务标识 → A2A wire）**

| FEAT-006 标识 | 主权 | A2A wire 字段 | 说明 |
|--------------|------|--------------|------|
| `conversationId` | 业务应用 | `message.contextId` | 同 conversation 稳定传递；普通多轮复用它 |
| `invocationId` / `idempotencyKey` | agent-client | `message.messageId` | 每次调用/续跑新生成 |
| `invocationRef` | agent-client | 本地句柄（不上 wire） | 内部映射到 taskRef |
| `taskRef` | agent-runtime | `message.taskId` / `result.task.id` | 内部/诊断，不对业务暴露 |

**① 创建（`SendStreamingMessage`，无 taskId；含工具视图）**

HTTP 头见 §3.5.0（`Authorization: Bearer …`、`Accept: text/event-stream`）。`agentId` 缺省与显式两种形态：

缺省 `agentId`（AC-1 选 A：不写 `metadata.agentId`，由 gateway 默认 Agent 选路）：

```json
{
  "jsonrpc": "2.0", "id": "req-1", "method": "SendStreamingMessage",
  "params": {
    "message": {
      "role": "ROLE_USER", "messageId": "msg-1", "contextId": "conv-1",
      "parts": [{"text": "帮我看看当前页面并建单"}]
    },
    "metadata": { "clientTools": [ /* = ToolView 投影；未声明 exposure 则省略，见 Feat-Func-007 §3.5 */ ] }
  }
}
```

显式 `agentId`（写入 `params.metadata.agentId`，非空）：

```json
{
  "jsonrpc": "2.0", "id": "req-1", "method": "SendStreamingMessage",
  "params": {
    "message": {
      "role": "ROLE_USER", "messageId": "msg-1", "contextId": "conv-1",
      "parts": [{"text": "帮我看看当前页面并建单"}]
    },
    "metadata": { "agentId": "support-agent", "clientTools": [ /* 同上 */ ] }
  }
}
```

**② 等待端侧工具**：Task 进入 `TASK_STATE_INPUT_REQUIRED`，`status.message.metadata._interrupt` 携带 `toolName`/`toolCallId`/`context.arguments`/`_interrupt_kind=client_tool`（结构见 Feat-Func-007 §3.5 与 `Feat-Func-009` §2.3.4）。SSE 帧为 `event: jsonrpc` + 完整 JSON-RPC `data`。

**③ 端侧工具结果续跑（`SendMessage`，原 taskId + 普通 TextPart，SDK 自动，内部恢复请求）**

HTTP 头见 §3.5.0（`Authorization: Bearer …`、`Accept: application/json`）；响应为**单次 JSON**（非 SSE），body 即 Task 下一状态。

```json
{
  "jsonrpc": "2.0", "id": "req-2", "method": "SendMessage",
  "params": { "message": {
    "role": "ROLE_USER", "messageId": "msg-2", "taskId": "task-123", "contextId": "conv-1",
    "parts": [{"text": "页面正文……"}]
  } }
}
```

**④ 用户补充输入续跑（`continueInput`；wire 形态同 ③——`SendMessage`+原 taskId，同步单次 JSON 响应，由业务发起，携带用户文本）**

```json
{
  "jsonrpc": "2.0", "id": "req-3", "method": "SendMessage",
  "params": { "message": {
    "role": "ROLE_USER", "messageId": "msg-3", "taskId": "task-123", "contextId": "conv-1",
    "parts": [{"text": "补充：客户手机号 138xxxx"}]
  } }
}
```

- 续跑（③④）以 `message.taskId` 关联，**不回传 `toolCallId`**（V1 单 pending，见 Feat-Func-007）。
- **普通多轮对话**：复用同一 `contextId(conversationId)` 再发一条**无 taskId** 的创建（=①），得到新 Task；不是对旧 Task 的续跑。

---

## 4. 代码结构（参照原型包）

```
com.huawei.ascend.client
├── api/
│   ├── AgentClient.java            # 业务入口（本迭代交付面：invoke + continueInput + tools）
│   ├── AgentClients.java           # builder 工厂
│   ├── InvocationRequest.java      # 调用请求（conversationId/mode/exposure/invocationId/幂等键…）
│   ├── ContinueInputRequest.java   # 用户补充输入续跑（关联旧 invocation）
│   ├── InvocationMode.java         # STREAMING（交付）/ BLOCKING / ASYNC（预留）
│   ├── InvocationCall.java         # 本地句柄：invocationRef + accepted/events/completion
│   ├── InvocationEvent.java        # sealed 事件层次 + ToolCall
│   ├── TaskState.java              # Task 状态投影 enum（isTerminal）
│   └── InvocationSnapshot.java     # invocation 只读快照
├── transport/spi/
│   └── TransportProvider.java      # 传输抽象（createAndStream / resume；query/cancel/resubscribe 版本目标预留）
├── transport/fake/
│   └── InProcessFakeGateway.java   # 测试用：SubmissionPublisher 模拟 A2A 多轮
└── internal/
    └── DefaultAgentClient.java     # core 编排 + invocationRef→taskRef 映射
```

---

## 5. 运行流程

### 5.1 主流程（创建 SSE + 同步续跑循环，对齐 feat-011 §5.9.3）

本迭代冻结的多轮承载形态（与 gateway/runtime 一致）：

```
① 创建：SendStreamingMessage（无 taskId）→ SSE
     Accepted → StatusChanged(WORKING) → ContentDelta*
       ├─ 直接 COMPLETED：SSE 流内结算终态（无工具/无需补充输入）
       └─ INPUT_REQUIRED：SSE 首次出现待输入即可关闭该流（stream 可关）
            │  记录 taskRef（result.task.id）
            ▼
② 续跑：SendMessage（原 taskId + 新 messageId + TextPart）→ 同步单次 JSON 响应
     响应 body = 该 Task 下一状态：
       ├─ 又一个 INPUT_REQUIRED(client_tool) → SDK 自动执行工具后再发 ② （循环）
       ├─ INPUT_REQUIRED(用户输入)          → 业务侧 continueInput 再发 ②
       └─ COMPLETED / FAILED               → 结算 completion()
```

- **创建**走 SSE（`SendStreamingMessage`）；**每一次续跑**（工具结果或 `continueInput`）走**同步 `SendMessage`**，其响应即下一状态帧（非 SSE 续传）。
- SDK 把上述两种承载统一归一化为同一 `InvocationEvent` 流对上暴露；业务只订阅 `events()` / `completion()`，不感知"这一步是 SSE 还是同步响应"。
- 详细报文见 §3.5；工具续跑编排见 Feat-Func-007 §5。本特性负责"接受 → 事件归一化 → 状态投影 → 续跑（工具/用户输入）→ 终态结算"。

### 5.2 断线补偿（本迭代不交付）

> FEAT-006 的断线补偿依赖查询/重订阅（`GetTask`/`tasks/resubscribe`），而下游 runtime 本迭代不支持这些方法（§2.2）。因此**本迭代不做断线补偿**：SSE 断开即视为该次调用失败并对上暴露明确错误，由业务重新发起。待 runtime/gateway 支持后再按 FEAT-006 补齐（§8 G-6）。

### 5.3 错误与降级（本迭代范围内）

| 错误场景 | 触发 | 行为 | 对外结果 |
|---------|------|------|---------|
| 网络失败 | 断网/超时 | 回显幂等键与调用关联 | 可重试网络错误 |
| 路由失败 | route not found / 无权限 / 不可用 | 暴露平台错误 | route/permission/service 错误 |
| 服务端失败 | A2A/Task error | 展示 failed invocation 投影 | 不包装成网络失败 |
| 不支持的调用模式 | mode≠STREAMING | 立即结束，不静默降级 | `UNSUPPORTED_MODE` 明确错误 |
| SSE 中断 | 流断（本迭代无补偿） | 判定该次调用失败 | 明确错误，业务重发 |
| 关联不可续接 | continueInput 关联过期/多义/终态 | 返回明确错误 | 不偷偷新建普通任务 |
| 未知状态 | 服务端新增 TaskState | 映射 UNKNOWN，不崩溃 | 不阻塞流程 |

---

## 6. 配置与使用

### 6.1 构造与调用（参照原型；符号名以 FEAT-006 术语为准）

```java
AgentClient client = AgentClients.builder()
        .transport(transportProvider)   // 生产：A2A over HTTP/SSE 连 gateway；测试：InProcessFakeGateway
        .eventListener(event -> log(event))
        .build();

// 创建（STREAMING），可选声明本次工具暴露策略
InvocationCall call = client.invoke(InvocationRequest.builder()
        .agentId("support-agent")
        .conversationId("conv-1")
        .mode(InvocationMode.STREAMING)
        .exposure(ToolExposurePolicy.allow("customer.profile.read"))   // 见 Feat-Func-007
        .input("...")
        .build());
Handle handle = call.accepted().toCompletableFuture().get();          // handle.invocationRef()
// 观察到需用户补充输入时：
InvocationCall cont = client.continueInput(ContinueInputRequest.builder()
        .conversationId("conv-1")
        .relatedInvocationRef(handle.invocationRef())
        .input("补充：客户手机号 138xxxx")
        .build());
```

### 6.2 关键构造项

| 构造项 | 类型 | 默认 | 说明 |
|-------|------|------|------|
| `transport` | `TransportProvider` | 无（必填） | wire 绑定（gateway 端点/鉴权）；隔离 OS/网络差异 |
| `stateStore` | `ClientStateStore` | 内存实现 | 见 Feat-Func-007（结果 outbox / ACK） |
| `eventListener` | `Consumer<InvocationEvent>` | no-op | 观测/日志/指标 |

> 租户不由 SDK 自证；鉴权与租户注入在 gateway 完成（§8 G-3）。

---

## 7. 当前限制

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| 无生产实现 | 仅原型 + fake gateway；A2A HttpClient adapter 待实现 | 以原型验证 core 逻辑 |
| 仅 STREAMING | BLOCKING/ASYNC 预留未接线 | 传入即 UNSUPPORTED；后续 transport 层补 |
| 无查询/取消/重订阅/断线补偿 | 本迭代最小非故障链路 | 版本目标，见 §2.2、§8 G-6 |
| 原型符号名滞后 | 原型仍用 `taskId`/`clientInvocationId` 旧签名 | 正文已按 FEAT-006 术语；原型机械跟随（§9） |
| 内存态投影 | 不承诺跨进程重启恢复 | 换 `ClientStateStore` 持久化实现 |

---

## 8. 对 gateway 的要求（转述给 gateway 负责同事）

> **gateway 侧权威设计现为 `agent-bus/feat-011-l2_V3.0.md`（FEAT-011 客户端调用直连路由转发）**；下列要求已在该文档 §4.9/§5.9/§6.9 与 client 双方冻结（2026-07-22），本节保留为 client 视角的对接摘要与追溯，冲突以 feat-011 为准。
> **总原则：gateway 是受治理的 A2A 透传/直连代理，对 client 暴露的 A2A 语义必须与 `Feat-Func-009` 对 runtime 定义的完全一致，不得改写。** G-1~G-5、G-7、G-8 是本迭代交付所必需；G-6 是版本目标（本迭代不交付，先登记诉求）。

| 编号 | 要求 | 本迭代 | 说明 / 理由 |
|------|------|--------|------------|
| **G-1 A2A 兼容入口** | 暴露标准 A2A JSON-RPC（本迭代至少 `SendStreamingMessage`/`SendMessage`），逐字段透传 | 必需 | 不得改动 `result.task`/`statusUpdate`/`TASK_STATE_*`/`_interrupt` 及 `contextId`/`messageId`/`taskId` |
| **G-2 Agent 发现与 Card url 改写** | 暴露发现入口，把 Card `url` 改写为 gateway 自身 | 必需 | 防止 client 直连 runtime 绕过治理 |
| **G-3 认证与租户注入** | 鉴权、从凭据解析租户注入下游、丢弃 client 自报租户 | 必需 | client 不自证租户 |
| **G-4 按 agentId 路由（可缺省）** | 有 `params.metadata.agentId` → 路由到目标 runtime；**缺省 → gateway 默认 Agent** | 必需 | 对齐 feat-011 IN-2/AC-1；client 缺省时不写该字段、绝不空串 |
| **G-5 粘滞路由（关键）** | 带 `taskId` 的续跑（工具/用户输入）必须路由到**持有该 Task 的同一 runtime 实例** | 必需 | 009 pending 存实例级 TaskStore；错误实例致 resume 失败/Task 挂起 |
| **G-7 错误分层** | 治理层错误（401/403/404/413/429/503）以 HTTP + 稳定 error body 暴露 | 必需 | client 两层都处理 |
| **G-8 SSE 逐帧透传** | 保持 runtime SSE 帧逐帧透传，不缓冲、不改写 `_interrupt` | 必需 | 保证事件归一化与续跑 |
| **G-6 查询/取消/重订阅/创建幂等** | 待 runtime 补齐 `GetTask`/`CancelTask`/`tasks/resubscribe` 与按 `messageId` 去重后，gateway 透传 | ⏸ 版本目标 | 本迭代不交付；先登记，避免后续接口反复 |

> 端侧工具多轮相关的 gateway 要求见 Feat-Func-007 §8。

---

## 9. 与 version-scope FEAT-006 的一致性与本迭代范围

> 正文术语/语义已按 FEAT-006 对齐；能力面按下游本迭代**收敛为最小非故障链路**。下表说明交付/不交付与落地项。

| 项 | FEAT-006 定位 | 本迭代 | 说明 / 落地项 |
|---|--------------|--------|--------------|
| invocationRef 操作面 | MUST | ✅ 已对齐 | 原型 `getTask(taskId)` 等旧签名机械替换；本迭代仅保留 `invoke`/`continueInput` |
| conversationId | MUST | ✅ 交付 | `InvocationRequest.conversationId`（→ contextId） |
| 调用模式 | MUST（三种） | ✅ 部分：STREAMING | `mode` 保留必填；BLOCKING/ASYNC 预留、UNSUPPORTED（§2.5） |
| 端侧工具结果续跑 | — | ✅ 交付 | 内部恢复请求（Feat-Func-007） |
| 用户补充输入续跑 | MUST（继续等待输入） | ✅ 交付 | `continueInput`（§3.4.1） |
| 普通多轮 | — | ✅ 交付 | 复用 conversationId 再 invoke |
| 工具视图上报 | — | ✅ 交付 | `exposure`→ToolView→`clientTools`（§3.2、Feat-Func-007） |
| 查询/取消/重订阅/UNKNOWN 恢复/断线补偿 | MUST | ⏸ 不交付 | runtime 本迭代未支持；诉求登记见 §2.2、§8 G-6 |
| 术语/标识映射 | — | ✅ 已对齐 | §3.5 给出 conversationId↔contextId、invocationId/幂等键↔messageId、taskRef↔taskId |

### 9.1 与 feat-011（gateway 边界）一致性

> 与 `agent-bus/feat-011-l2_V3.0.md` §4.9/§5.9/§6.9 双方冻结结论（2026-07-22）逐项落地情况：

| feat-011 冻结项 | 本文落地 |
|----------------|---------|
| AC-1（选 A）创建允许缺省 `agentId`、缺省不写 `metadata.agentId` | §2.4 数据类型（agentId 可选）、§3.2、§3.5.0、§3.5 ① 两例 |
| AC-2/GW-6 有则写 `params.metadata.agentId` 非空、绝不空串 | §3.5.0、§3.5 ① 显式例 |
| AC-3/G1 每次 HTTP 带 `Authorization: Bearer`、凭据不进 body | §3.5.0 鉴权行 |
| AC-4 `invocationId`/`idempotencyKey`→`message.messageId` | §3.5 标识映射、§3.5.0 幂等键行 |
| AC-5 `conversationId`→`message.contextId` | §3.5 标识映射 |
| AC-6 method `SendMessage`/`SendStreamingMessage`、头 Content-Type/Accept | §3.5.0 |
| AC-7/G9 治理错误按 HTTP 层处理不投影为成功 Task | §3.5.0、§5.3 |
| AC-S3-1 `taskRef` 源 `result.task.id`、经 Handle/Accepted 诊断可读 | §2.4 Handle、§3.5 标识映射 |
| AC-S3-* / AC-S4-2 续跑=`SendMessage`+原 `taskId`+新 `messageId`，同步单次 JSON | §3.5.0、§3.5 ③④、§5.1 |
| AC-S4-2 `continueInput` wire 带原 `taskId`、`relatedInvocationRef` 不上 wire | §3.4.1、§3.5 ④ |
| IN-9/IN-10 查询/取消/重订阅/UNKNOWN 730 不交付 | §2.2、§7、§8 G-6 |
