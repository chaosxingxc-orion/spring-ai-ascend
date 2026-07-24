---
level: L2-LLD
module: agent-client
feature_type: functional
feature_id: FEAT-007
status: proposed
authority: non-authoritative
dependency:
  - ../../../version-scope/FEAT-007-local-tool-registration-and-execution.md
  - ../../L1-High-Level-Design/agent-client/overview.md
  - ../../L1-High-Level-Design/agent-client/logical.md
  - ../../L1-High-Level-Design/agent-client/process.md
  - ../../L1-High-Level-Design/agent-client/development.md
  - ./Feat-Func-006-standard-agent-client-invocation.md
  - ../agent-runtime/Feat-Func-009-调用端侧工具响应-新增支持带有端侧工具的请求.md
  - ../agent-bus/feat-011-l2_V3.0.md
  - ../../../agent-client/docs/proposals/agent-client-v1-design.md
  - ../../../agent-client/examples/cloud-client/README.md
---

# 本地工具注册与远端驱动调用 — 设计文档（FEAT-007 L2）

> 目标模块：`agent-client`（edge plane SDK）
> 事实来源（authoritative）：`version-scope/FEAT-007-local-tool-registration-and-execution.md`（本 L2 是其实现级细化，术语与语义以 FEAT-007 为准）
> 参照实现：`agent-client/examples/cloud-client/`（可运行原型，JDK 17；原型符号名为早期形态，正文已按 FEAT-007 术语对齐，原型代码待机械跟随，见 §10）
> 最后更新：2026-07-23
> **⚠️ 状态：proposed / non-authoritative。** 本文是待评审的实现级设计，不是已接受实现事实。
> **🔗 wire 基线：** 端侧工具多轮语义**对齐 runtime `agent-runtime/Feat-Func-009`**：工具视图走 `params.metadata.clientTools`（`name`/`description`/`inputSchema`，本地 `toolId` 不上 wire）；调用意图走 Task `status.message.metadata._interrupt`，单调用为原 interaction map，多调用为 `items[]` envelope（每项 `_interrupt_kind=client_tool`），SDK 收到后统一解析为内部 `ToolCall` 集合；结果仍走普通 TextPart observation 文本，多 pending 时须在一次 continuation 中提交完整结果集合，每个 Part 通过 `metadata.toolCallId` 定向，单 pending 时允许省略。创建和 continuation 均可使用 `SendMessage` 或 `SendStreamingMessage`，由调用方选择阻塞或 SSE 承载；每次 HTTP 均带 `Authorization: Bearer`。SDK 内部保留结构化 `ToolExecutionRecord`/`Outcome`，仅在回传前渲染为 observation 文本，不上 wire。gateway 只受治理透传上述标准 metadata，不拥有等待状态或结果关联。

---

## 1. 概述

### 1.1 特性定位

为端侧提供**本地工具的标准化 SPI、注册管理与暴露治理**，使远端智能体能经**多轮请求**驱动调用客户端本地能力：业务在开发期通过 SPI 注册本地工具 → 运行时显式声明 conversation 级/invocation 级**暴露策略** → client 据此计算 **ToolView** 随真实 invocation 上报 → runtime 基于 ToolView 产生 `INPUT_REQUIRED` + `_interrupt` 意图（单 map / 多 `items[]`）→ SDK 归一为内部集合并经治理入口本地执行一个或多个工具 → 以新的 continuation invocation 关联旧 invocation；SDK 内部再使用原 taskRef 和带 `toolCallId` 的 TextPart 续跑原 Task，直至任务完成。

- **解决的问题**：智能体需要客户端本地能力，但**不能**把本地工具当作服务端可直接远程调用的函数或可动态发现的资源。工具必须先注册、再显式暴露、每次 invocation 计算 ToolView 上报，服务端只能请求 ToolView 中可见的工具。
- **适用场景**：需要观测本地环境或执行本地动作的智能体任务。**不适用**：纯服务端可完成、无需客户端参与的任务。

### 1.2 当前事实边界

本文描述 FEAT-007 的**拟议 L2 设计**。服务端如何产生端侧工具调用意图由 runtime `Feat-Func-009` 承接；标准 invocation 入口由 Feat-Func-006 承接。Observation/Action 分类是**客户端侧治理声明位**，不上 wire、也不是技术沙箱（企业可信开发者场景，见 `agent-client-v1-design.md` 附录 A.5）。

### 1.3 设计原则

1. **默认不暴露** — 未经业务显式声明 `ToolExposurePolicy`，client **不向服务端暴露任何本地工具**；本地工具目录 ≠ 服务端可见的 ToolView。
2. **ToolView 每次 invocation 计算并上报** — ToolView = 本地工具目录 ∩ 暴露策略（conversation 级 + invocation 级）∩ 租户/用户/上下文/可用性；即使已有 conversation 级策略，也必须随真实 invocation 附上当前 ToolView，避免服务端缓存工具视图。
3. **执行 ≠ 交付 ACK** — 先本地执行、把 `ToolExecutionRecord` 写 outbox，再回传；"服务端已接收"以回传成功响应为准，是幂等基石。
4. **同一 toolCallId 只执行一次（本地去重）** — `toolCallId` 由服务端在单 `_interrupt` map 或多 `_interrupt.items[]` 中给出、跨重投/重连稳定，是本地执行去重键。多 pending 提交结果时必须在 `TextPart.metadata.toolCallId` 原样回传；单 pending 时允许省略以兼容普通文本续接。
5. **治理骨架不可绕过** — ToolView 可见性/暴露校验 → schema 校验 → 策略 → 审批 → 去重 → 执行 → 结果 outbox 的**顺序与卡点存在**由 SDK 强制；卡点里"判断什么"由业务实现。
6. **结果以 observation 文本上 wire** — 对齐 `Feat-Func-009`：回传是普通 TextPart 文本。SDK 内部 `ToolExecutionRecord`（含 `Outcome`、payload/payloadRef、审计引用）在回传前渲染成明确文本（§3.4）；结构化字段是客户端对象，不做 wire 级对齐。
7. **不做沙箱** — 进程内护栏（有界执行器 + deadline + 异常边界）防止工具拖垮宿主；强隔离属宿主部署能力。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| 工具 SPI 与描述 | 声明本地能力、Observation/Action、schema | `LocalTool`, `LocalToolDescriptor` | ⬜ proposed |
| 注册管理 | register/replace、冲突策略、toolId 唯一性 | `LocalToolRegistry` | ⬜ proposed |
| 暴露策略 | conversation/invocation 级可暴露范围 | `ToolExposurePolicy` | ⬜ proposed |
| ToolView 生成上报 | 目录 ∩ 策略 ∩ 上下文 → `clientTools` | `ToolView` | ⬜ proposed |
| 治理骨架 | 可见性/策略/审批卡点 | `Governance`(`PolicyGuard`/`ApprovalProvider`) | ⬜ proposed |
| 多轮驱动 | INPUT_REQUIRED(`_interrupt` 单 map / 多 `items[]`) → SDK 内部归一 → 本地执行 → 定向 TextPart 回传续跑 | `AgentClient#executeAndResume`, `TransportProvider#resumeToolResults` | ⬜ proposed |
| 去重与幂等 | toolCallId 本地去重、执行记录 outbox、ACK | `ToolDispatcher`, `ClientStateStore` | ⬜ proposed |
| 执行记录与渲染 | `ToolExecutionRecord` → observation 文本 | `ToolExecutionRecord`(`Outcome`) | ⬜ proposed |

---

## 2. 特性规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| 工具 SPI 注册 | ⬜ | `LocalTool` + `LocalToolDescriptor`；register 冲突拒绝、replace 覆盖、toolId 唯一 |
| 默认不暴露 | ⬜ | 未声明暴露策略时 ToolView 为空，服务端不可见任何工具 |
| 暴露策略 | ⬜ | `ToolExposurePolicy` 支持 conversation 级与 invocation 级；invocation 级可收窄/覆盖 conversation 级 |
| ToolView 生成与上报 | ⬜ | 每次 invocation 计算 ToolView 并映射为 `clientTools[{name,description,inputSchema}]` 上报（对齐 009） |
| Observation/Action 分类 | ⬜ | `OBSERVATION`（只读）/`ACTION`（副作用，强治理）；不上 wire |
| schema 校验 | ⬜ | 原型用必填参数键；生产用 input JSON Schema（= `inputSchema`） |
| ToolView 可见性校验 | ⬜ | 请求未在 ToolView / 未暴露 / 越权 → 结构化拒绝，不动态注册/执行 |
| 策略/审批卡点 | ⬜ | `PolicyGuard`：ALLOW/DENY/REQUIRE_APPROVAL；`ApprovalProvider`：Action 人工确认 |
| toolCallId 本地去重 | ⬜ | outbox + in-flight 双重去重，恰好执行一次 |
| deadline/超时 | ⬜ | 协作式超时 → 渲染为超时文本 |
| 单次最终结果 | ⬜ | 一次工具请求投影最多一个最终结果；重复提交幂等返回同一结果或明确冲突 |
| SDK 自动编排多轮 | ⬜ | 业务不直调 handler，SDK 收 INPUT_REQUIRED 自动经治理入口执行并续跑 |
| 多 pending 执行 | ⬜ | 同轮多个端侧工具调用完整保留；SDK 可按本地治理并行或串行执行，但须收齐每项最终 outcome 后一次提交 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| 默认暴露本地工具 | 违反 FEAT-007 默认不暴露 | 必须显式声明 `ToolExposurePolicy` |
| 服务端缓存 ToolView | 客户端能力随上下文变化 | 每次 invocation 重新计算并上报 |
| 工具沙箱 / 任意代码执行 | 一个 Java 库做不了可靠 OS 沙箱 | 进程内护栏；强隔离跑独立进程/容器 |
| 服务端透明调用本地函数 | 违反 edge 治理与不可信 caller 原则 | 声明式 capability + client 主动多轮回传 |
| 客户端 webhook 回调入口 | 安全面/网络可达性风险 | 折叠为 Task 待执行意图 |
| 结构化结果 / 结果枚举 / DataPart 上 wire | `Feat-Func-009` V1 只接受 TextPart observation | 内部结构化，回传前渲染文本（差异清单见 §9） |
| 服务端驱动本地审批 | 审批是 client 本地治理 | 服务端只发工具请求投影，不拆分驱动内部步骤 |

### 2.3 接口契约（Logical View）

```java
/** 业务实现的本地能力执行 SPI（只见 SDK 自有类型，不见 A2A/HTTP）。 */
public interface LocalTool {
    LocalToolDescriptor descriptor();
    CompletionStage<ToolExecutionRecord> execute(ToolInvocation invocation, ToolExecutionContext context);
}

/** 暴露策略：业务显式声明可暴露范围；默认空视图。 */
public interface ToolExposurePolicy {
    /** 计算本次 invocation 对服务端可见的 ToolView（目录 ∩ 策略 ∩ 上下文）。 */
    ToolView toolViewFor(ExposureContext context);   // conversation 级 + invocation 级合并
}

/** 治理入口：SDK 内部统一经此执行并回传；同一 toolCallId 只执行一次。 */
public interface AgentClient {
    CompletionStage<InvocationSnapshot> executeAndResume(
            String relatedInvocationRef, List<InvocationEvent.ToolCall> toolCalls);
    LocalToolRegistry tools();
}

/** 执行记录为客户端对象；回传前由 SDK 渲染成 observation 文本（枚举/payloadRef 不上 wire）。 */
public record ToolExecutionRecord(
        String toolCallId, Outcome outcome, Object payload, String payloadRef,
        String idempotencyKey, String auditRef, String rejectionReason) {
    public enum Outcome { OK, ERROR, REJECTED, TIMEOUT }
}
```

#### 数据类型

| 类型 | 关键字段 | 含义 | 约束 |
|------|---------|------|------|
| `LocalToolDescriptor` | `toolId`, `version`, `name`, `description`, `sideEffect`, `inputSchema`(`requiredArgumentKeys`), `timeout`, 授权/审批/审计策略 | 工具公开描述 | `toolId` 是执行匹配主键；`name`/`description` 供模型/UI；`sideEffect` 决定治理强度（不上 wire） |
| `ToolExposurePolicy` | conversation 级/invocation 级可暴露/拒绝范围、过期、最大次数、Action 授权模式、脱敏 | 暴露策略 | 默认空视图；invocation 级可收窄/覆盖 conversation 级 |
| `ToolView` | 当前可见工具集（映射 `clientTools`） | 服务端可见能力投影 | 只含已暴露且当前可用工具；不含未授权/已撤销/全量目录 |
| `ToolInvocation` | `toolId`, `toolCallId`, `arguments`, `deadline` | 一次远端驱动调用（源自 `_interrupt`，`toolId` 由原 ToolView 快照解析） | `toolId` 是本地执行匹配主键；`toolCallId` 是本地幂等/去重键 |
| `ToolExecutionRecord` | `outcome`, `payload`/`payloadRef`, `idempotencyKey`, `auditRef` | 客户端执行记录/结果 | 回传前渲染为 observation 文本 |
| `ToolExecutionContext` | `tenantId`, `traceId`, `deadline` | 执行上下文 | tenant 以 gateway 注入为权威 |

#### 行为承诺

- **必须**：未声明暴露策略时 ToolView 为空；每次 invocation 重新计算并上报 ToolView。
- **必须**：只对**已注册、已暴露、当前可用、权限允许**的工具执行；未声明/未暴露/越权/未注册/过期/参数非法 → 结构化拒绝，不动态注册/执行。
- **必须**：同一 `toolCallId` 恰好执行一次；先写 outbox 再回传；回传成功才标记 acked。
- **必须**：SDK 将单 `_interrupt` map 或多 `_interrupt.items[]` 归一为内部集合后，每个 ToolCall 独立经过 ToolView、schema、策略、审批和去重管道；SDK 不要求 runtime 驱动本地批次或审批过程。
- **必须**：多 pending 时每个结果 TextPart 携带对应 `metadata.toolCallId`；单 pending 时允许省略。未知、重复或已解决 ID 不得被 SDK 当作新的本地执行请求。
- **必须**：多 pending 时 SDK 收齐每项最终 outcome 后，在一次 continuation 中提交与等待集合完整对应的结果；不得分批提交。
- **必须**：拒绝/错误/超时均渲染为**明确的 observation 文本**回传（对齐 009 §2.3.3）。
- **禁止**：用 TextPart 承载工具定义（工具只走 `metadata.clientTools`）；公共 API 出现 A2A/HTTP 类型；服务端拆分驱动本地审批步骤。

---

## 3. 核心实现（参照原型）

### 3.1 暴露策略与 ToolView 生成

```
注册（开发期）: registry.register(LocalTool)  →  本地工具目录（client 本地事实）
        │
声明（运行时）: 业务声明 conversation 级 / invocation 级 ToolExposurePolicy
        │
计算（每次 invocation）: ToolView = 目录 ∩ (conversation 策略 ⊕ invocation 策略) ∩ 租户/用户/上下文/可用性
        │  invocation 级收窄/覆盖 conversation 级；默认空视图
        ▼
上报: ToolView → params.metadata.clientTools（随本次 invocation，见 §3.5）
```

> 未声明策略 → ToolView 为空 → 不上报任何 `clientTools` → 服务端不可见任何本地工具。

#### 3.1.1 工具如何暴露给远端（端到端流程，回应架构师）

本地工具**不会**被单独推送或由服务端主动发现；它**搭载 Feat-Func-006 的标准调用请求**一次性上报，链路如下：

```
① 注册（开发期）  registry.register(LocalTool)                      —— 仅进 client 本地目录，服务端不可见
② 声明（运行时）  业务把 ToolExposurePolicy 作为 Feat-Func-006
                 InvocationRequest.exposure 传入 invoke(...)        —— 声明本次可暴露范围（默认空）
③ 计算（client）  ToolView = 目录 ∩ 暴露策略 ∩ 租户/用户/上下文/可用性
④ 上报（wire）    ToolView 映射为创建请求 params.metadata.clientTools，
                 随 SendMessage/SendStreamingMessage 发给 gateway → runtime —— 见 §3.5 ① 与 Feat-Func-006 §3.2
⑤ 请求（服务端）  runtime 据 clientTools 注入模型工具视图；命中则回 INPUT_REQUIRED + _interrupt
```

- **入口就是标准调用**：暴露不是独立接口，而是 `Feat-Func-006` 创建 invocation 时通过 `InvocationRequest.exposure` 参数携带（见 Feat-Func-006 §2.4 数据类型、§3.2 创建流程）。
- **每次都带**：即使已有 conversation 级策略，也在每次 invocation 重新计算并上报 ToolView，服务端不缓存客户端工具视图。
- **默认不暴露**：未传 `exposure` → ToolView 空 → 不产生 `clientTools` → 服务端不可见任何本地工具。

### 3.2 治理骨架（ToolDispatcher 管道）

```
executeAndResume(relatedInvocationRef, toolCalls)  ← toolCalls 由 _interrupt 单 map / 多 items[] 归一化而来
  │
  ▼ 每个 ToolCall 独立进入 ToolDispatcher.dispatch(invocation, ctx)  ← 单项治理顺序不可绕过
  ├─ 1. outbox 去重：store.findRecord(toolCallId) 命中 → 复用，绝不重执行
  ├─ 2. in-flight 去重：同一 toolCallId 并发只跑一次（putIfAbsent）
  ├─ 3. ToolView 可见性/暴露校验：不在本次 ToolView / 未暴露 / 越权 → REJECTED(tool_not_declared|permission_denied)
  ├─ 4. 解析工具：原 invocation ToolView 快照 resolve(toolName) → toolId；registry.resolve(toolId) → handler，缺失 → REJECTED(tool_not_found)
  ├─ 5. schema 校验：必填键缺失/参数非法 → REJECTED(invalid_tool_arguments)
  ├─ 6. PolicyGuard.check → ALLOW / DENY(→permission_denied) / REQUIRE_APPROVAL
  ├─ 7. REQUIRE_APPROVAL → ApprovalProvider.requestApproval → 未批 → REJECTED(rejected)
  ├─ 8. 执行：tool.execute(...).orTimeout(deadline) → 超时 TIMEOUT / 异常 ERROR
  └─ 9. ToolExecutionRecord 落 outbox（store.saveRecord），并渲染为 observation 文本
  │
  ▼ transport.resumeToolResults(ResumeCommand(taskRef, messageId, observationsByToolCallId))
  └─ 成功响应 = 对已接受结果的交付 ACK → store.markAcked(toolCallId)  ← 此后只重投不重跑
```

> `toolCallId` 同时用于本地去重和传输关联：多 pending 时进入对应 `TextPart.metadata.toolCallId`；单 pending 时允许省略，但 SDK 本地仍使用原 ID 去重。多个 ToolCall 可以并行执行，但每项都必须独立通过上述治理管道，并在全部形成最终 outcome 后一次提交。

### 3.3 Observation / Action 分类与差异化治理（客户端侧）

| 类别 | 语义 | 治理重点 | 原型示例 |
|------|------|---------|---------|
| `OBSERVATION` | 只读观测（本地上下文/检索/文件摘要/UI 快照/记忆读取） | 数据范围、脱敏、租户隔离、时效；策略允许可自动执行 | `customer.profile.read`, `device.camera.capture`（占位） |
| `ACTION` | 产生副作用（审批提交/工单更新/业务写入/消息发送/本地命令） | 授权、审批、幂等、补偿、审计、回滚 | `ticket.create`（策略要求审批） |

> SDK 无法从技术上强制回调实现符合声明，且 `sideEffect` **不上 wire**。分类价值在于**按声明施加差异化本地治理并留审计痕迹**。服务端不得通过多步工具请求远程驱动客户端内部审批流程。

### 3.4 执行记录 → observation 文本渲染

对齐 `Feat-Func-009` §2.3.3：SDK 把 `ToolExecutionRecord` 渲染成明确的 observation 文本再上 wire。

| 本地情形 | ToolExecutionRecord（客户端对象） | 回传 TextPart（observation 文本，示例） |
|---------|-----------|-------------|
| 成功 | `outcome=OK, payload/payloadRef` | 结果摘要 / JSON 文本，如 `{"ticketId":"TK-8801"}`（大结果用 payloadRef + 摘要） |
| 策略/审批拒绝 | `outcome=REJECTED, rejectionReason` | `客户端拒绝执行该工具：用户取消了操作。` |
| 执行抛错 | `outcome=ERROR` | `客户端工具执行失败：本地插件不可用。` |
| 超过 deadline | `outcome=TIMEOUT` | `客户端工具执行超时：未在限定时间内完成。` |

> 大结果不应无限内联 TextPart；应回传受治理对象引用（`payloadRef`）+ 必要摘要（对齐 009 §2.3.3）。

### 3.5 wire 契约（对齐 Feat-Func-009）

**① 工具视图声明（创建请求 `params.metadata.clientTools` = ToolView 投影）**

ToolView 中每个可见工具映射为 `clientTools` 项（wire 只取 `name`；`inputSchema` 直接用），同时在原 invocation 的本地快照中保留唯一 `name -> toolId` 映射。`toolId`、`version`、`sideEffect`、`timeout` 和输出 schema 是客户端本地字段，**不上 wire**。未暴露工具不出现。

```json
{
  "clientTools": [
    {
      "name": "customer.profile.read",
      "description": "读取当前客户档案",
      "inputSchema": {
        "type": "object",
        "properties": { "customerId": {"type": "string"} },
        "required": ["customerId"],
        "additionalProperties": false
      }
    }
  ]
}
```

**② 调用意图（Task `status.message.metadata._interrupt`）**

Runtime 保持当前兼容形态：单调用直接把 interaction 放在 `_interrupt`，多调用才使用 `_interrupt.items[]`。下例为多调用，并省略 SDK 不应解析但传输链路仍原样保留的 interaction `payload`：

```json
{
  "_interrupt": {
    "message": "interaction batch",
    "items": [
      {
        "type": "__interaction__",
        "index": 0,
        "toolName": "customer.profile.read",
        "toolCallId": "call-123",
        "context": { "_interrupt_kind": "client_tool", "arguments": { "customerId": "C-9" } },
        "message": "Client tool invocation required: customer.profile.read"
      },
      {
        "type": "__interaction__",
        "index": 1,
        "toolName": "customer.order.read",
        "toolCallId": "call-456",
        "context": { "_interrupt_kind": "client_tool", "arguments": { "customerId": "C-9" } },
        "message": "Client tool invocation required: customer.order.read"
      }
    ]
  }
}
```

SDK 解析时，若 `_interrupt.items` 是合法数组则按原顺序读取全部 item，否则把合法的 `_interrupt` interaction map 视为单元素集合；随后对每项独立归一化：`toolName`→原 invocation ToolView 快照中的本地 `toolId`、`toolCallId`→本地去重键、`context.arguments`→`ToolInvocation.arguments`。只处理 `_interrupt_kind=client_tool`，不得使用当前全局目录猜测历史 ToolView 中不存在的工具。

**③ 结果回传（continuation invocation；SDK 内部为原 taskId + TextPart；支持阻塞或 SSE）**

HTTP 承载与鉴权见 Feat-Func-006 §3.5.0：`POST /a2a`、`Authorization: Bearer …`、`Accept: application/json`、新 `messageId`（≠ 创建键）、`contextId`=原 `conversationId`；响应 body 即该 Task 的下一状态（下一 `_interrupt` 或终态）。对齐 feat-011 §5.9.3 / AC-S3-*。

```json
{
  "message": {
    "role": "ROLE_USER", "messageId": "msg-2", "taskId": "task-123", "contextId": "conv-1",
    "parts": [
      {
        "text": "{\"name\":\"张三\",\"level\":\"VIP\"}",
        "metadata": {"toolCallId": "call-123"}
      },
      {
        "text": "{\"orders\":[]}",
        "metadata": {"toolCallId": "call-456"}
      }
    ]
  }
}
```

- 结果只走 TextPart，无 wire 结果枚举、无 DataPart。多 pending 时每个 Part 必须回传对应 `metadata.toolCallId`；单 pending 时可省略。业务 facade 每次提交形成新的 continuation invocation 并关联旧 invocation；SDK 内部解析旧 invocation 的 taskRef，以同一 Task 的 A2A Message 完成传输恢复。业务应用不直接操作 `taskId`。
- 多 pending 的一条 Message 必须提交全部最终结果。SDK 可在本地并行或串行执行，但须等待每项得到最终 outcome 后一次续跑；缺少任一结果时不得发起 continuation。SDK 不维护服务端批次状态，只维护本地 ToolExecutionRecord/outbox。

---

## 4. 代码结构（参照原型包）

```
com.huawei.ascend.client
├── tool/spi/
│   ├── LocalTool.java              # 本地能力执行 SPI（+ of(...) 便捷工厂）
│   ├── LocalToolDescriptor.java    # 工具描述 + SideEffect(OBSERVATION/ACTION)（客户端侧）
│   ├── ToolExposurePolicy.java     # 暴露策略（conversation/invocation 级）
│   ├── ToolView.java               # 当前可见工具投影（→ clientTools）
│   ├── ToolInvocation.java         # 一次调用（toolId 执行匹配；toolCallId 去重/关联）
│   ├── ToolExecutionRecord.java    # 执行记录（Outcome/payloadRef/审计，客户端对象）
│   ├── ToolExecutionContext.java   # tenant/trace/deadline
│   └── LocalToolRegistry.java      # 注册表（register 冲突/replace 覆盖/toolId 唯一）
├── spi/
│   └── Governance.java             # PolicyGuard / ApprovalProvider + 决策类型
├── state/spi/
│   └── ClientStateStore.java       # 执行记录 outbox / ACK（可替换）
└── internal/
    ├── ToolDispatcher.java         # 治理骨架 + 可见性/去重管道 + 结果渲染（本特性核心）
    ├── DefaultToolRegistry.java    # 注册表实现
    └── InMemoryStateStore.java     # 内存 outbox（MVP）
```

### 核心类静态关系

```
DefaultAgentClient
   │ executeAndResume（收到 _interrupt 归一化的 ToolCall）
   ▼
ToolDispatcher ── uses ──► DefaultToolRegistry ── resolves(toolName) ──► LocalTool（业务实现）
   │  ├── uses ──► ToolExposurePolicy / ToolView（可见性校验）
   │  ├── uses ──► Governance.PolicyGuard / ApprovalProvider（业务实现）
   │  └── uses ──► ClientStateStore（执行记录 outbox/ACK）
   ▼
TransportProvider.resumeToolResults（带 taskRef + 一个或多个定向 observation 文本续跑，见 Feat-Func-006 §3.5）
```

---

## 5. 运行流程

### 5.1 多轮驱动主流程（SDK 自动编排）

```
用户 → 业务应用            agent-client SDK              gateway (受治理透传)        runtime (A2A, Feat-Func-009)
  │  invoke + 暴露策略 ─────►│ 计算 ToolView                 │                          │
  │                         │ SendMessage/SendStreamingMessage                         │
  │                         │  + metadata.clientTools(ToolView) ► 透传 ───────────────►│
  │                         │◄──── Accepted(invocationRef) ─│◄─────────────────────────│
  │                         │◄─ INPUT_REQUIRED + map/items[]│◄──── map/items[] ────────│  (client_tool)
  │                         │  每个 item 独立走治理/审批/去重/执行/outbox/文本渲染
  │                         │ continuation invocation       │                          │
  │                         │ SDK: SendMessage(taskId, TextParts + toolCallId)         │
  │                         │  ── 定向 observation 文本 ───►│──────── 透传 ───────────►│
  │                         │◄──── WORKING ─────────────────│◄─────────────────────────│  完整结果集合通过校验后 markAcked
  │                         │◄──── COMPLETED ───────────────│◄─────────────────────────│
  │◄── completion() 结算 ────│                               │                          │
```

业务无需自己订阅并调用 handler；SDK 收到 `INPUT_REQUIRED` 后归一化全部 items，经治理入口执行，收齐每项最终 outcome 后只创建一次 continuation invocation。

> **承载形态（对齐 Feat-Func-009）**：创建与每次工具结果 continuation 均支持 `SendMessage` 或 `SendStreamingMessage`。阻塞调用通过单次 JSON 响应返回下一状态；流式调用通过 SSE 投影 `INPUT_REQUIRED(_interrupt)` 或后续状态，并在进入等待后关闭本次发送流。SDK 把两种承载统一归一化为同一 `InvocationEvent` 流，业务可按调用场景选择，工具字段和恢复语义不因承载方式变化。

### 5.2 重连重放幂等（原型已验证）

```
gateway/runtime 重复投递 INPUT_REQUIRED(_interrupt.toolCallId=call-0)   ← 单调用；模拟 SSE 断线重连 / GetTask 重观察
  → 第 1 次：执行工具（count=1），resume 推进
  → 第 2 次：dispatch 命中 outbox（按 toolCallId）→ 复用记录，不重执行；
             resume 复用同一 continuation 幂等语义；已解决 ID 不重复推进
结果：工具恰好执行一次（原型断言 [PASS]）
```

### 5.3 错误、状态与可观测结果（错误码闭集，对齐 FEAT-007 §5.1.5）

| 场景 | 错误码 | 行为 | 回传 observation 文本 |
|------|--------|------|---------|
| 工具未声明（不在 ToolView） | `tool_not_declared` | 不执行、不动态注册 | `客户端拒绝执行该工具：未声明。` |
| 工具未注册 | `tool_not_found` | 不执行 | `客户端拒绝执行该工具：工具未注册。` |
| 工具不可用/上下文过期 | `tool_not_available` / `stale_context` | 不执行 | `客户端拒绝执行该工具：当前不可用。` |
| 参数非法 | `invalid_tool_arguments` | 不执行 | `客户端拒绝执行该工具：参数不合法。` |
| 权限不足 | `permission_denied` | 不执行 | `客户端拒绝执行该工具：无权限。` |
| 用户拒绝 | `rejected` | 保留审计引用 | `客户端拒绝执行该工具：用户未批准。` |
| 执行超时 | `timeout` / `expired` | 放弃等待 | `客户端工具执行超时：……` |
| 执行异常 | （渲染为 ERROR 文本） | catch 成结构化失败 | `客户端工具执行失败：……` |
| 重复提交 | 幂等键冲突 | 复用同一记录或明确冲突 | 不重执行、不重推进 |
| 服务端幻觉调用 | `tool_not_declared` / `permission_denied` | 不执行、不动态注册 | 结构化拒绝 |

---

## 6. 配置与使用

### 6.1 注册、暴露与治理（参照原型；符号名以 FEAT-007 术语为准）

```java
AgentClient client = AgentClients.builder()
        .transport(transport)   // A2A over HTTP/SSE 连 gateway
        .policyGuard((descriptor, inv, ctx) -> completed(
                descriptor.sideEffect() == SideEffect.ACTION
                        ? PolicyDecision.requireApproval("确认执行：" + descriptor.toolId())
                        : PolicyDecision.allow()))
        .approvalProvider((descriptor, inv, prompt) -> completed(ApprovalDecision.approved("auto")))
        .build();

// 开发期注册
client.tools().register(LocalTool.of(
        LocalToolDescriptor.builder().toolId("ticket.create").version("1")
                .sideEffect(SideEffect.ACTION)
                .requiredArgumentKeys(Set.of("customerId", "summary")).build(),
        (inv, ctx) -> completed(ToolExecutionRecord.ok("call", Map.of("ticketId", "TK-8801")))));

// 运行时显式声明暴露策略（默认不暴露）
InvocationCall call = client.invoke(InvocationRequest.builder()
        .agentId("support-agent").conversationId("conv-1").mode(InvocationMode.STREAMING)
        .exposure(ToolExposurePolicy.allow("ticket.create", "customer.profile.read"))
        .input("...").build());
```

### 6.2 关键扩展点

| SPI | 默认 | 业务通常自定义什么 |
|-----|------|------------------|
| `LocalTool` | 无 | 具体本地能力（读/写/设备） |
| `ToolExposurePolicy` | 空视图（不暴露） | 每 conversation/invocation 的可暴露范围与脱敏 |
| `PolicyGuard` | 全放行 | 权限、数据范围、副作用约束判据 |
| `ApprovalProvider` | 自动批准 | 弹出确认 UI / 审批流 |
| `ClientStateStore` | 内存 | 需重启恢复时换文件/DB outbox |

---

## 7. 当前限制

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| 结果只文本 | 无法回传结构化结果/明确 outcome 枚举给服务端 | SDK 渲染明确文本；结构化属 V1.1 差异（§9） |
| 多 pending 依赖目标 metadata | 旧 transport 若丢弃 TextPart metadata 将无法精确续接 | 以前置 runtime 通用 Part metadata 保真能力为准；单 pending 保持普通文本兼容 |
| 原型符号名滞后 | 原型仍用 `ToolDescriptor`/`ToolResult`、无暴露策略 | 正文已按 FEAT-007 术语；原型机械跟随（§10） |
| 无沙箱 | 不可信代码不能安全隔离 | 进程内护栏；强隔离跑独立进程/容器 |
| 内存 outbox | 不承诺跨进程重启恢复 | 换持久化 `ClientStateStore` |

---

## 8. 对 gateway 的要求（转述给 gateway 负责同事）

> gateway 侧权威设计为 `agent-bus/feat-011-l2_V3.0.md`（FEAT-011），其中 §5（S3 端侧工具结果续跑）与 §5.9 已与 client 双方冻结（2026-07-22）。通用 gateway 要求（A2A 兼容入口、鉴权/租户注入、agentId 路由、**粘滞路由**、错误分层、SSE 逐帧透传）见 Feat-Func-006 §8 与 feat-011。以下是**端侧工具多轮**特有的透传要求（对应 feat-011 IN-6 / GW-S3-*）。

| 编号 | 要求 | 说明 / 理由 |
|------|------|------------|
| **GT-1 clientTools（ToolView）透传** | 创建请求 `params.metadata.clientTools` 必须**原样透传**给 runtime，不吞掉/改写/重排 | runtime 据此注入模型工具视图（009 §2.3.2）；缺失则端侧工具不可见 |
| **GT-2 `_interrupt` 透传** | runtime 的单 `_interrupt` map 或多 `_interrupt.items[]`（每项含 `toolName`/`toolCallId`/`context.arguments`/`_interrupt_kind`）必须原样透传给 client | client 据此归一化全部 `ToolCall`；不得改写单/多 envelope、截断、合并或删减 |
| **GT-3 TextPart 结果透传** | SDK 内部用原 `taskId` 的 `SendMessage` + TextPart 回传，gateway 必须同时保留正文和 `metadata.toolCallId` | 多 pending 依赖该字段精确关联；单 pending 无 ID 时仍按普通文本透传 |
| **GT-4 粘滞路由（工具续跑尤其依赖）** | 带 `taskId` 的结果回传必须路由到**持有该 Task pending ToolCall 的同一 runtime 实例** | 见 Feat-Func-006 §8 G-5；工具多轮对会话亲和性零容忍 |
| **GT-5 正文/事件配额声明** | `clientTools` 计入创建请求正文上限；结果 TextPart 计入事件/正文上限；超限策略需声明 | client 据此约束目录大小与结果内联（改用 payloadRef） |
| **GT-6 保持多 pending 结构** | 多 pending 时不得由 gateway 合并、拆分、重排 `_interrupt.items[]`，也不得把一个结果广播到多个 toolCallId | gateway 只透传，不拥有工具等待集合或结果路由 |

---

## 9. 与 Feat-Func-009 的差异 / 客户端增强诉求（未来 V1.1，需 runtime+gateway 共同升级）

> 以下能力在本 V1 设计中**已按 009 对齐裁剪掉 wire 表达**，仅保留在 client 本地模型。如需上 wire，须与 runtime、gateway 共同评审升级，属 V1.1，不阻塞本次提交。

| 诉求 | 现状（V1 对齐 009） | V1.1 期望 | 依赖方 |
|------|---------|---------|--------|
| 结构化工具结果 | 只回传 TextPart 文本 | DataPart / `clientToolResults` 结构化结果 | runtime + gateway |
| 明确 outcome 枚举 | 渲染为文本（OK/REJECTED/ERROR/TIMEOUT 仅本地） | wire 级 `outcome` 与服务端对齐 | runtime |
| 副作用分类上 wire | `sideEffect` 仅客户端治理 | `clientTools[].sideEffect` 供服务端差异化 | runtime + gateway |
| 工具版本 | `clientTools` 无 version | `clientTools[].version` + 意图回带 | runtime |
| 创建幂等 / 取消 | 依赖 gateway（Feat-Func-006 §8 G-6） | wire 级去重 + `tasks/cancel` | gateway + runtime |

---

## 10. 与 version-scope FEAT-007 的一致性与落地映射

> 正文已按 FEAT-007 对齐（默认不暴露、ToolExposurePolicy、ToolView、可见性校验、术语、错误码闭集、ToolExecutionRecord）。剩余仅为**参照原型代码**的机械跟随项。

| 项 | FEAT-007 要求 | 本文状态 | 落地项（原型代码待改） |
|---|--------------|---------|----------------------|
| 默认不暴露 | 未声明策略不暴露 | ✅ 已对齐（§3.1） | 原型在注册与上报间插入 ToolExposurePolicy 闸门 |
| 暴露策略 | conversation/invocation 级 | ✅ 已对齐 | 原型新增 `ToolExposurePolicy` 与合并规则 |
| ToolView | 每次 invocation 计算上报 | ✅ 已对齐（§3.1/§3.5） | `clientTools` 由 ToolView 投影，不再整表上报 |
| 可见性/越权校验 | 未暴露/越权→结构化拒绝 | ✅ 已对齐（§3.2 步骤 3、§5.3） | 校验链增加 ToolView 可见性步骤 |
| 术语 | LocalToolDescriptor/ToolExposurePolicy/ToolView/ToolExecutionRecord | ✅ 已对齐 | 原型 `ToolDescriptor`/`ToolResult` 机械改名 |
| 结果对象 | outcome+payload/payloadRef+幂等键+审计 | ✅ 已对齐（客户端对象，wire=文本） | 原型 `ToolResult`→`ToolExecutionRecord` 补字段 |
| continuation 与内部恢复 | 业务 facade 创建新 continuation invocation；SDK 内部恢复原 Task | ✅ 已对齐（§3.5） | 保存 invocation-taskRef 映射，业务应用不操作 taskId |
| 错误码闭集 | FEAT-007 §5.1.5 | ✅ 已对齐（§5.3） | 原型 Outcome 归并 → 明确错误码渲染 |

### 10.1 与 feat-011（gateway 边界）一致性

> 以下是本设计要求 feat-011/gateway 同步保持的最小契约。若 gateway 文档仍冻结为“单 pending、不透传 toolCallId”，该两项已与当前 Feat-Func-009 脱离，需要由 gateway owner 最小更新；无需改变 gateway 状态所有权或新增接口。

| feat-011 冻结项 | 本文落地 |
|----------------|---------|
| AC-S3-1 `taskRef` 源 `result.task.id`，续跑唯一粘滞键 | §3.5 ②③、Feat-Func-006 §3.5 标识映射 |
| AC-S3-2/GW-S3-2 续跑写 `params.message.taskId` | §3.5 ③ |
| AC-S3-3/GW-S3-3 续跑用新 `messageId`、不走创建幂等 | §3.5 ③、§5.2 |
| AC-S3-4 识别 `_interrupt` 单 map / 多 `items[]` 中的 `client_tool`、SSE 关流后本地执行再续跑 | §3.2、§5.1（承载形态） |
| AC-S3-5/GW-S3-4 结果仍为 TextPart observation；多 pending 透传 `metadata.toolCallId`，单 pending 可省略 | §3.4、§3.5 ③、§1.3 原则 4/6 |
| GW-S3-5 创建带 `clientTools`、下行 `_interrupt` 不删不改 | §3.5 ①②、§8 GT-1/GT-2 |
| GW-S3-1/§5.9.3 续跑支持 `SendMessage` / `SendStreamingMessage`，字段与恢复语义一致 | §3.5 ③、§5.1 承载形态 |
| GW-S3-6/AC-S3-6 续跑每次带 `Authorization: Bearer` | §3.5 ③（引 Feat-Func-006 §3.5.0） |
| GW-S3-7 续跑不依赖 `agentId` 寻路（gateway 粘滞） | §3.5 ③、§8 GT-4 |
| GW-S3-9 owner 不可定位=明确失败、不静默新建 | §8 GT-4、Feat-Func-006 §5.3 |
