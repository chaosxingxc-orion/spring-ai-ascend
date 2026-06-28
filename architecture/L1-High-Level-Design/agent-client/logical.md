---
level: L1-HLD
TAG:
  - logical-view
  - domain-model
  - architecture-fact
  - module-boundary
  - edge-plane
status: draft
dependency:
  - README.md
  - overview.md
  - scenarios.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
  - ../../L0-Top-Level-Design/views.md
  - ../../../docs/contracts/ingress-envelope.v1.yaml
---

# agent-client L1 架构逻辑视图

## 1. 逻辑视图定位

`agent-client` 是 edge plane 的业务接入与客户端集成边界。逻辑视图描述该客户端边界内部的领域对象、状态归属、能力治理模型、职责分层和跨模块依赖方向。

本视图回答以下问题：

- 业务侧调用进入 `agent-client` 后，被抽象成哪些客户端领域对象。
- Client invocation、cursor、capability correlation、debug session 与服务端 Task 的状态归属如何区分。
- 本地能力如何按 Observation / Action 建模，并分别承载不同治理要求。
- 当前版本如何用 client 主动多轮请求替代客户端暴露式 webhook / S2C 异步回调。
- 开发态 client 如何表达配置草稿、调试步进和智能体定义产物。
- `agent-client` 与 `agent-bus`、`agent-runtime`、`agent-core`、`agent-middleware` 的依赖方向如何保持隔离。

## 2. 领域对象模型

### 2.1 Client 身份与调用上下文

`ClientInvocation` 表示一次客户端侧智能体调用引用。它是业务应用和服务端 Task 之间的本地句柄，不是服务端生命周期状态。

```text
ClientInvocation
├── invocationId        客户端本地调用标识
├── tenantId            租户标识
├── actorRef            业务侧操作者或主体引用
├── traceId             调用链标识
├── idempotencyKey      幂等键
├── serverTaskRef       服务端 Task 引用
├── cursorRef           服务流消费游标引用
├── capabilityRefs      本地能力声明引用集合
└── statusProjection    客户端侧进度投影
```

`ClientRequestContext` 是 client 发起跨平面请求时使用的请求上下文。它承载 ingress envelope 所需的身份、关联和约束信息。

```text
ClientRequestContext
├── requestId
├── tenantId
├── traceId
├── idempotencyKey
├── requestType
├── deadline
└── requestAttributes
```

`ClientInvocation` 的逻辑职责是让业务应用持有一个稳定的本地引用；`ClientRequestContext` 的逻辑职责是把每一轮 client -> server 请求表达为可治理的入口请求。二者都不能替代 `agent-runtime` 内部的 Task state。

### 2.2 Cursor / Progress / Capability Correlation 归属

`agent-client` 管理三类客户端侧状态，但它们都不是服务端 Task 生命周期状态。

| 状态对象 | 归属 | Client 职责 |
|---|---|---|
| Client invocation | `agent-client` | 保存本地调用句柄、服务端 Task 引用、业务 UI 进度和请求关联信息。 |
| Cursor | `agent-client` | 保存 SSE/stream 最近消费位置、重订阅锚点和客户端恢复点。 |
| Capability correlation | `agent-client` | 关联某个待观测/待动作意图与本地执行结果，用于下一轮恢复请求。 |
| Server Task lifecycle | `agent-runtime` | 服务端权威执行生命周期，client 只能查询、取消、继续或提交输入。 |
| Agent checkpoint | 具体 Agent 框架或外部状态能力 | client 不读取或持久化框架内部 checkpoint。 |

Cursor 是客户端消费进度，status projection 是客户端 UI/业务侧投影，capability correlation 是本地能力多轮请求的恢复上下文。它们可以映射或引用服务端 Task，但不能成为服务端状态写入路径。

### 2.3 本地能力模型：Observation 与 Action

`LocalCapability` 表示 C-Side 暴露给智能体感知或调用的本地能力声明。当前 L1 将本地能力按环境交互语义拆分为 Observation 和 Action。

```text
LocalCapability
├── capabilityId
├── capabilityType       OBSERVATION | ACTION
├── tenantScope
├── inputSchemaRef
├── outputSchemaRef
├── policyRef
├── credentialRef
└── evidencePolicy
```

Observation 是只读环境观测能力。

```text
ObservationCapability
├── context read
├── retrieval
├── file summary
├── UI state snapshot
├── business fact read
└── local memory read
```

Action 是会产生业务副作用的动作能力。

```text
ActionCapability
├── approval submit
├── ticket update
├── business write
├── message send
├── local command
└── external system operation
```

Observation 的治理重点是数据范围、脱敏、租户隔离、可见性和时效性。Action 的治理重点是授权、审批、幂等、补偿、审计和失败回滚。二者必须在逻辑模型上区分，不能把所有本地能力都简化为无差别 tool call。

### 2.4 本地能力意图与多轮结果

当前版本中，服务端不直接回调 client endpoint。本地能力交互由服务端 Task 状态、事件或服务流表达为待处理意图，再由 `agent-client` 主动提交结果。

```text
CapabilityIntent
├── taskRef
├── correlationId
├── capabilityId
├── capabilityType
├── inputPayload
├── prompt
├── deadline
└── policyHints

CapabilityResult
├── correlationId
├── outcome             OK | ERROR | REJECTED | TIMEOUT
├── resultPayload
├── authorizedRef
├── auditRef
└── error
```

`CapabilityIntent` 是 client 从 Task 状态、事件或 stream 中识别出的待处理意图；`CapabilityResult` 是 client 执行本地治理后，通过下一轮 client -> server 请求返回的结果。它们构成本地能力多轮交互的逻辑闭环。

### 2.5 开发态调试模型

`DebugSession` 表示开发态 client 与开发态 runtime 之间的一次调试会话。

```text
DebugSession
├── debugSessionId
├── tenantId
├── developerRef
├── runtimeRef
├── draftRef
├── breakpoints
├── stepCursor
├── traceId
└── evidenceRefs
```

`DebugConfigurationDraft` 表示开发态下发的智能体配置草稿。

```text
DebugConfigurationDraft
├── draftId
├── agentKind          WORKFLOW | AGENT_LOOP
├── baseVersion
├── configPatch
├── validationState
└── exportTarget       WORKFLOW_DSL | OPENCLAW_JSON
```

`DebugArtifact` 表示调试完成后的智能体定义产物。

```text
DebugArtifact
├── artifactId
├── artifactType       WORKFLOW_DSL | OPENCLAW_JSON
├── sourceDraftId
├── contentRef
├── evidenceRefs
└── reviewState
```

开发态对象只在开发态 session 和草稿范围内生效。导出产物是可审阅、可版本化的定义候选，不自动等同于生产发布。

## 3. 四个逻辑责任面

### 3.1 分层总览

```text
┌──────────────────────────────────────────────────────────────┐
│                         agent-client                         │
│                                                              │
│  sdk-facade                                                   │
│  业务接入：创建调用、查询、取消、继续、开发态命令             │
│        │                                                     │
│        ├───────────────┬────────────────┬──────────────────┐ │
│        ▼               ▼                ▼                  ▼ │
│  invocation-state  stream-and-turn-loop  capability-and-debug │
│  本地状态与引用     流消费与主动多轮      本地能力治理与开发态 │
│  invocation/cursor  SSE/intent/resume    Observation/Action   │
│  correlation/debug  retry/resubscribe    debug/export         │
│        ▲               │                ▲                  │ │
│        └───────────────┴────────────────┴──────────────────┘ │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

四个责任面描述 client 侧逻辑职责，而不是强制代码包结构，也不是严格的自上而下调用栈。`sdk-facade` 是业务入口；`invocation-state` 是共享的本地状态与引用管理面；`stream-and-turn-loop` 与 `capability-and-debug` 是并列协作面：前者从服务流或查询结果中识别待处理意图并组织下一轮请求，后者负责本地 Observation、Action 和开发态调试能力的治理与执行。它们共同维持一个约束：`agent-client` 是受治理的 edge caller，不是服务端 Task owner。

### 3.2 sdk-facade：业务接入表面

`sdk-facade` 是业务应用、终端应用、IDE 或开发工具使用的客户端入口。

该层的逻辑职责包括：

- 创建 client invocation。
- 发起 Task 查询、取消、继续执行和重订阅。
- 提交本地 Observation / Action 结果。
- 发起开发态配置下发、单步调试和产物导出命令。
- 构造 client -> server 的请求语义，并交给 ingress 边界。

`sdk-facade` 不直接调用 compute_control 内部模块，不直接写服务端 Task state，也不把某个具体 HTTP endpoint 或 A2A client 对象暴露为模块唯一模型。

### 3.3 invocation-state：客户端状态与引用管理

`invocation-state` 管理客户端侧的调用、游标、能力关联和调试会话引用。

该层的逻辑职责包括：

- 保存 client invocation 与 serverTaskRef 的映射。
- 保存 stream cursor 和客户端侧恢复点。
- 保存 capability correlation，用于把待处理意图和下一轮结果请求关联起来。
- 保存 debug session、配置草稿引用和导出产物引用。
- 将本地 UI/业务进度投影与服务端 Task lifecycle state 区分开。

该层保存的是客户端本地状态和引用，不拥有服务端 Task 生命周期，也不持久化 Agent checkpoint。

### 3.4 stream-and-turn-loop：服务流消费与主动多轮

`stream-and-turn-loop` 负责消费服务端实时输出，并把服务端等待本地协作的状态转化为 client 主动发起的下一轮请求。

该层的逻辑职责包括：

- 建立并消费 SSE 或等价服务流。
- 维护事件顺序、cursor 和业务 UI 进度。
- 从 Task 状态、事件或服务流中识别 CapabilityIntent。
- 在本地能力完成后构造下一轮恢复请求。
- 处理断线、重订阅、查询补偿和 retry/backoff。

当前版本不在该层暴露 webhook endpoint。服务端不能通过网络直接回调 client；client 通过主动查询、订阅和下一轮请求完成交互闭环。

### 3.5 capability-and-debug：本地能力治理与开发态

`capability-and-debug` 负责 C-Side capability 的声明、路由、治理和开发态调试产物处理。

该层的逻辑职责包括：

- 管理本地 Observation provider。
- 管理本地 Action executor。
- 执行本地权限、审批、幂等、副作用保护和审计策略。
- 生成 CapabilityResult、authorizedRef、auditRef 或错误。
- 管理开发态配置草稿、断点、单步执行上下文和调试证据。
- 导出 workflow DSL 或 `openclaw.json` 类 agent-loop 配置产物。

该层可以接入本地业务系统、审批 UI、文件系统、IDE 或开发工具，但必须保持 C-Side 权限和数据边界，不把本地凭据或敏感正文无治理地传给平台侧。

## 4. 状态模型

### 4.1 ClientInvocation 状态投影

Client invocation 的状态是客户端投影状态，用于 UI、业务流程和本地恢复。它不是服务端 Task 状态机。

```text
NEW ──▶ SUBMITTED ──▶ STREAMING ──▶ COMPLETED
          │              │
          │              ├──▶ WAITING_CAPABILITY ──▶ RESUMING ──▶ STREAMING
          │              │
          │              ├──▶ CANCEL_REQUESTED
          │              │
          │              └──▶ FAILED
          │
          └──▶ REJECTED
```

| 状态 | 逻辑含义 |
|---|---|
| NEW | 本地调用已创建，尚未被服务端接收。 |
| SUBMITTED | client 已通过 ingress 提交请求，等待服务端接受或拒绝。 |
| STREAMING | client 正在消费服务端输出或 Task 事件。 |
| WAITING_CAPABILITY | client 观察到服务端等待本地 Observation 或 Action 结果。 |
| RESUMING | client 已完成本地能力处理，正在主动提交下一轮恢复请求。 |
| CANCEL_REQUESTED | client 已发起取消请求，等待服务端 Task 终态或确认。 |
| COMPLETED | 本地投影观察到服务端 Task 正常完成。 |
| FAILED | 本地投影观察到服务端失败，或客户端侧无法恢复。 |
| REJECTED | 入口请求被拒绝，未形成可继续的服务端执行。 |

该状态投影可以由服务端响应、服务流事件、本地能力执行和本地错误共同驱动，但任何服务端 Task lifecycle 判断仍以 `agent-runtime` 为权威。

### 4.2 Cursor 状态

Cursor 表示客户端对服务流的消费位置。

```text
CursorState
├── invocationId
├── serverTaskRef
├── streamRef
├── lastEventRef
├── lastObservedStatus
└── updatedAt
```

Cursor 的逻辑规则：

- cursor 只能表示 client 已消费到哪里，不能表示 Task 已执行到哪里。
- cursor 可以用于重订阅、查询补偿和 UI 恢复。
- cursor 丢失时，client 必须通过受治理查询恢复当前 Task 可见状态。
- cursor 不承诺跨服务端重启后的完整事件重放能力，除非后续物理视图明确持久化机制。

### 4.3 CapabilityIntent / CapabilityResult 状态

本地能力多轮交互围绕 capability correlation 推进。

```text
DETECTED ──▶ POLICY_CHECKING ──▶ EXECUTING ──▶ RESULT_READY ──▶ SUBMITTED
   │              │                 │               │
   │              └──▶ REJECTED      └──▶ TIMEOUT     └──▶ FAILED
   │
   └──▶ EXPIRED
```

| 状态 | 逻辑含义 |
|---|---|
| DETECTED | client 从 Task 状态、事件或服务流中识别出本地能力意图。 |
| POLICY_CHECKING | client 正在检查能力声明、权限、数据范围、审批和副作用约束。 |
| EXECUTING | 本地 Observation provider 或 Action executor 正在执行。 |
| RESULT_READY | 本地能力结果已生成，等待提交给服务端。 |
| SUBMITTED | client 已通过下一轮请求提交结果。 |
| REJECTED | 本地策略或用户审批拒绝执行。 |
| TIMEOUT | 本地能力未在 deadline 内完成。 |
| FAILED | 本地能力执行失败或结果不可提交。 |
| EXPIRED | 服务端等待意图或本地关联已过期。 |

Observation 可以在 `POLICY_CHECKING` 后直接返回只读结果或数据引用。Action 在进入 `EXECUTING` 前必须完成必要授权、审批、幂等和审计准备。

### 4.4 DebugSession 状态

开发态调试状态只适用于开发态 runtime 和草稿。

```text
OPEN ──▶ CONFIGURING ──▶ STEPPING ──▶ EXPORT_READY ──▶ CLOSED
  │            │             │              │
  │            └──▶ INVALID   └──▶ FAILED    └──▶ ABANDONED
  └──▶ CLOSED
```

| 状态 | 逻辑含义 |
|---|---|
| OPEN | debug session 已创建并绑定开发态 runtime。 |
| CONFIGURING | client 正在下发或修改智能体配置草稿。 |
| STEPPING | runtime 正在按步或分段执行调试任务。 |
| EXPORT_READY | 调试结果可以导出为定义产物。 |
| CLOSED | 调试会话正常结束。 |
| INVALID | 配置草稿校验失败。 |
| FAILED | 调试执行失败。 |
| ABANDONED | 开发者放弃该调试会话或草稿。 |

DebugSession 状态不能作用于生产智能体实例。导出的 `DebugArtifact` 仍需进入版本、审阅或发布流程。

## 5. 逻辑依赖方向

### 5.1 Edge ingress 隔离方向

`agent-client` 是 edge caller，跨平面请求必须通过 ingress 契约。

```text
Business application / IDE
    ↓ calls
agent-client sdk-facade
    ↓ constructs IngressEnvelope
agent-bus IngressGateway
    ↓ routes
agent-runtime Task surface
```

`agent-client` 可以理解 ingress envelope 语义，但不依赖 `agent-runtime`、`agent-core` 或 `agent-middleware` 的生产代码。服务端 Task 创建、取消、恢复和状态推进由 `agent-runtime` 拥有。

### 5.2 本地能力交互方向

当前版本本地能力交互采用 client 主动多轮模型。

```text
agent-runtime Task / stream
    ↓ exposes CapabilityIntent
agent-client stream-and-turn-loop
    ↓ routes
ObservationProvider / ActionExecutor
    ↓ returns CapabilityResult
agent-client sdk-facade
    ↓ submits next turn through ingress
agent-runtime Task surface
```

该方向明确排除服务端直接访问 client webhook endpoint。Observation / Action 的执行发生在 C-Side；服务端只接收经过治理的结果、引用、拒绝或错误。

### 5.3 客户端状态读写边界

客户端状态读写集中在 `invocation-state` 责任面。

```text
sdk-facade
    ↓ creates / queries
invocation-state
    ↓ reads / writes
ClientInvocation / Cursor / CapabilityCorrelation / DebugSession
```

这些状态支持客户端体验、重连、能力多轮和开发态调试，但不成为服务端权威状态。任何需要服务端 Task 判断的行为都必须通过受治理入口查询或推进。

### 5.4 开发态与生产态隔离方向

开发态 client 可以连接开发态 runtime，但不能把调试通道提升为生产控制面。

```text
Developer UI / IDE
    ↓ calls
agent-client DebugSession
    ↓ submits debug command through ingress
Development Runtime
    ↓ returns step output
agent-client DebugArtifactExporter
    ↓ emits
Workflow DSL / openclaw.json candidate
```

配置草稿、断点和 step cursor 只在 debug session 内有效。导出产物是定义候选，需要经过版本、审阅和发布治理后才能进入生产。

### 5.5 与 L0 模块边界的关系

`agent-client` 在 L0 边界中只拥有客户端侧接入、游标、本地能力和开发态客户端对象。

```text
agent-client
    ↓ consumes ingress contract
agent-bus
    ↓ governs cross-plane entry
agent-runtime
    ↓ owns Task lifecycle
agent-core / agent-middleware
    ↓ provide execution and middleware capabilities behind runtime boundaries
```

`agent-client` 不承载通用智能体编排，不拥有平台审计最终写入，不接管模型、记忆、工具或沙箱的全局治理，不拥有跨实例 A2A 控制。它可以表达本地能力和开发态调试意图，但这些意图必须通过受治理的运行时边界被服务端消费。
