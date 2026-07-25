---
level: L2-LLD
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-009
status: active
dependency:
  - ../../L0-Top-Level-Design/boundaries.md
  - ../../L0-Top-Level-Design/constraints.md
  - ../../L1-High-Level-Design/agent-runtime/README.md
  - ../../L1-High-Level-Design/agent-runtime/logical.md
  - ../../L1-High-Level-Design/agent-runtime/process.md
  - ../../L1-High-Level-Design/agent-core/logical.md
  - ../../L1-High-Level-Design/agent-core/process.md
  - ../../L1-High-Level-Design/agent-client/overview.md
  - ../../L1-High-Level-Design/agent-client/scenarios.md
  - ../../../version-scope/FEAT-006-standard-agent-client-invocation.md
  - ../../../version-scope/FEAT-007-local-tool-registration-and-execution.md
  - ../../../version-scope/FEAT-009-runtime-response-client-side-tool-calling.md
  - ../../../version-scope/FEAT-010-task-level-dynamic-tool-visibility-and-handoff.md
---

# 【调用端侧工具响应】新增支持带有端侧工具的请求 — 设计文档

> 需求名称：【调用端侧工具响应】新增支持带有端侧工具的请求
> 需求说明：需要调用客户端侧工具的中断，通过智能体服务调用响应返回给客户端
> 关联需求：`FEAT-2026-010`（agent-core 端侧工具动态注册与调用）
> 目标实现：`agent-runtime-java`、`agent-solution/common/agent-runtime-ext-java`
> 最后更新：2026-07-24
> 关键范围：当前版本只支持 JSON-RPC A2A 的 `SendMessage`、`SendStreamingMessage` 与 `GetTask`；REST query 入口不在范围内。

---

## 1. 概述

### 1.1 特性定位

本特性使服务端 Agent 在一次 A2A Task 执行中临时看见客户端声明的本地工具，并在模型同一轮选择一个或多个客户端工具时，把完整调用集合转换为 `INPUT_REQUIRED` 投影。client 在本地独立执行这些工具，等每项都形成最终 outcome 后，以一次 continuation invocation 提交与 pending 集合一一对应的结果；SDK 在内部将 continuation 映射为原 Task 的 A2A 续接消息，runtime 校验后恢复原 Task。

本特性解决以下问题：客户端页面能力、终端插件和本地审批动作不能由服务端直接访问，也不应注册为平台全局工具。改造后，client 通过 A2A `params.metadata` 声明本次可用工具，runtime 维护 Task 状态，AgentCore 扩展 rail 只向本次模型调用注入工具描述；真实执行始终发生在 client。

适用场景包括读取当前页面、调用终端插件和触发本地 UI 动作。Action 工具所需的授权、确认或审批由 client 本地治理，只有最终工具 outcome 进入本特性；纯粹要求用户补充输入、选择、决策或材料的等待属于 FEAT-008。不适用于服务端本地工具、MCP 工具、Skill Hub 工具、远程 A2A Agent 委派或 REST query 调用。

**跨层实施范围：** FEAT-009 的主权仍是 runtime 的 Task 挂起、投影、continuation 校验和恢复，但本需求要形成可交付的端到端链路，因此第 4.1～4.4 节的 `ClientToolRail` 生命周期、`beforeModelCall`、`beforeToolCall`、`ToolInfo` 注入和 DeepAgent 内部 Agent 定位均属于本需求的实现范围。这些章节落地 FEAT-010 已定义的 Core 语义，不重新定义 FEAT-010；若缺少这些 solution/Core 配合，Runtime 无法独立完成 FEAT-009。

### 1.2 当前代码基线

本设计直接以三个代码仓的本地最新代码为事实基线，采用 JSON-RPC A2A、请求级 `ClientToolRail`、复用 `_interrupt`/TextPart、无新增 endpoint 与无公共 DTO 的最小设计。Handler 当前实际输出的单 interaction map / 多 `items[]` 是本需求需要适配的代码事实，不是 FEAT-009 要改造成统一 envelope 的目标；本需求不修改 Handler，也不改造 A2A Controller、RequestHandler 或远程 A2A coordinator。

本次按 2026-07-24 本地最新代码重新核对，基线分别为 `agent-core-java@a412e1c9`、`agent-runtime-java@731b91df`、`agent-solution@907ba8eb`。当前三个代码仓具备以下事实：

- `agent-runtime-java` 的 `A2aJsonRpcController` 已解析 `params.metadata`，`A2AProtocolAdapter` 已将其保存在 `ServeRequest.metadata`；带 `TextPart.metadata.toolCallId` 的入站文本已经归入受信任内部 key `runtime.remoteToolInputs`。
- `JiuwenCoreAgentHandler#buildInputs()` 已把 `runtime.remoteToolResults` 转成 `InteractiveInput.userInputs`。这两个内部 Map 的数据形态正好是 `toolCallId -> observationText`；为尽量沿用现有实现，本需求不新增平行 input key，由 solution 请求级适配在调用 Handler 前校验带 ID 的 `remoteToolInputs` 并转换为 `remoteToolResults`。单 pending 的无 ID 文本继续走 Core 现有字符串恢复。该复用只借用内部载体，不让 client-tool 进入 RemoteInvocationBatchCoordinator 或继承远程 Task 语义。
- `A2AEnabledServeOrchestrator` 当前在调用 Handler 前无条件尝试 `RemoteInvocationBatchCoordinator.resume()`；定向 TextPart 形成 `runtime.remoteToolInputs`、但不存在远端批次 shadow 时，coordinator 会返回 `REMOTE_BATCH_PARENT_MISMATCH`。因此多 pending 或显式带 `toolCallId` 的 client-tool continuation 目前到不了 solution 请求级适配。本需求需要在编排入口根据 `A2AAgentExecutor` 从 Task 恢复的 `_interrupt` kind 做最小分流：仅当单 map 或多 `items[]` 已确认全部为 `client_tool` 时跳过远端批次 resume，直接交给 Handler；其他中断形态继续执行既有 coordinator 探测，不修改 coordinator 的远端批次语义。
- `A2AAgentExecutor` 已能把 interrupt chunk 投影为 `INPUT_REQUIRED`，并只在原 Task 为 `INPUT_REQUIRED` 时从 status/history 恢复 `_interrupt` 到 `ServeRequest.metadata`；本需求沿用该行为，不在 Executor 中新增 client-tool 分支。
- 当前 A2A RequestHandler 会在 Executor 前拒绝终态 Task 的 continuation，终态 Task 不会进入 `A2AAgentExecutor`。非终态但非 `INPUT_REQUIRED` 的无 ID TextPart 仍是标准 A2A Message，不能仅凭文本识别成 client-tool 结果；agent-client 必须按 FEAT-006 的 invocation/taskRef 状态阻止错误 continuation。
- `agent-core-java` 的 ReActAgent 已支持 `beforeModelCall`、`beforeToolCall` rail、`ToolInterruptException`、同轮多个 interrupted ToolCall、`InteractiveInput.userInputs` 按 `toolCallId` 定向恢复和 `_skip_tool` 结果回灌。
- `JiuwenCoreAgentHandler#normalizeInterrupts()` 当前对单中断直接返回原 interaction map，对多中断生成 `items[]` envelope；既有 `singleInterruptKeepsLegacyMapShape` 测试固定了当前形态。本需求按现状适配，不修改 Handler。agent-client / solution 为便于后续校验而使用 pending 列表只是内部解析模型，不是 wire 归一要求，也不反向约束 Handler 改变输出。
- `A2AAgentExecutor` 的非流式入口通过 `A2AEnabledServeOrchestrator#queryWithProgress()` 保持同步 `agentHandler.query()` 语义，同时单独投影远端成员进度；流式入口仍走 `agentHandler.streamQuery()`。两条路径最终都保留 Handler 产生的单 map / 多 `items[]` 中断，不要求 client-tool 借用远程批次或把非流式执行改造成内部流。
- DeepAgent 内部持有 ReActAgent，`DeepAgent#getAgent()` 可定位实际执行模型和工具调用的内部 ReActAgent；启用 task-loop 时，DeepAgent 会把外层 `conversationId` 派生为 `conversationId + "_" + requestSeq` 的 request-level Session ID，内部 round 使用该派生 ID，而不是原始 A2A `conversationId`。`DeepAgentConfig.isTaskLoopEnabled` 当前默认 `false`，且非流式 `invoke` 在关闭 task-loop 时不执行内部 ReActAgent，因此本特性只承诺显式 `enableTaskLoop(true)` 的 DeepAgent。
- `agent-core-java` 的 `BaseAgent` 已公开 `registerRail()` / `unregisterRail()`；`JiuwenCoreAgentHandler#query()` 和 `streamQuery()` 都在当前调用栈内同步完成 Agent 执行，适合由扩展 Handler 在 `try/finally` 中管理请求级 rail 生命周期。
- `agent-solution` 的 `RemoteA2aToolInstaller` 和 `RemoteA2aInterruptRail` 已提供“识别普通 ReActAgent/DeepAgent、复用 `BaseInterruptRail` 中断恢复”的参考模式。
- `DeepAgent#runTaskLoop()` 尚未把 `result_type=interrupt` 作为无条件退出当前 invocation 的控制信号。默认 completion policy 且 interrupt 时没有已排队 follow-up 的情况下，现有 completion evaluator 会在该 round 后自然结束；显式 completion policy 未命中或存在已排队 follow-up 时则可能继续执行。本期以支持边界规避后两种场景，不修改 Core 生产代码，也不由 Runtime/Solution 提前截断后台执行。

### 1.3 核心设计原则

1. **A2A Task 是唯一服务端状态 owner** — 端侧工具等待通过 `INPUT_REQUIRED` 表达，不新增私有结果提交端点或第二套 Task 状态机。
2. **端侧工具是当前执行级上下文** — 工具定义只进入本次 `ModelCallInputs.tools`，不写入共享 `AbilityManager`、ResourceMgr、MCP 或 Skill Hub。
3. **solution 扩展承载 client-tool 专用适配** — `JiuwenCoreAgentExtHandler` 从 `ServeRequest.metadata` 构造请求级 `ClientToolRail`；通用多中断保真和按 `toolCallId` 恢复仍以前置 Core/Runtime 能力为准，不在 solution 中复制实现。
4. **rail 按请求注册和注销** — 每次 `query/streamQuery` 创建一个携带不可变工具快照的 rail，执行前注册，`finally` 中注销；普通 ReActAgent 使用原始 `conversationId` 精确过滤，DeepAgent 只匹配完整的 `conversationId + "_" + 纯数字 requestSeq` 派生 Session ID。
5. **结果按调用实例关联** — 多 pending 时每个结果 TextPart 必须携带非空 `metadata.toolCallId`；单 pending 时允许省略并由 Core 现有能力定向到唯一等待项。pending 恢复资格由原 `toolCallId -> toolName` 对确定，不能只凭历史工具名拦截新的同名调用。`toolId` 仍是 client 本地注册主键，不新增到 `clientTools` wire 投影。
6. **runtime 只保真透传，不执行或协调客户端工具** — runtime 保存和投影 Core/Handler 产生的完整 `client_tool` 中断内容，保持单 interaction map / 多 `items[]` 的当前 wire 形态；不创建 `_client_tool_batch`、不等待客户端本地执行线程，也不复用远端 `_remote_batch`。client 负责收齐本轮所有最终 outcome；solution 请求级适配把两种形态解析为 pending 列表，校验结果集合后一次交回 Core。
7. **业务 invocation 与传输 Task 分层** — 业务应用通过 FEAT-006 创建新的 continuation invocation；agent-client 内部解析旧 invocation 的 taskRef，并用原 A2A Task 发送续接 Message。业务应用不直接操作服务端 `taskId`。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|---|---|---|---|
| A2A 能力视图接收 | 从 `params.metadata.clientTools` 接收本次工具目录 | `A2aJsonRpcController`, `ServeRequest.metadata` | ⚠️ 基础透传已存在，端侧工具校验待实现 |
| 请求级 rail 绑定 | 从 metadata 创建 rail，执行前注册、结束后注销 | `JiuwenCoreAgentExtHandler`, `ClientToolRail.bind()` | ⬜ 计划中 |
| 动态工具可见性 | 只向本次 `ModelCallInputs.tools` 添加 ToolInfo | `ClientToolRail#beforeModelCall` | ⬜ 计划中 |
| 调用移交 | Session 过滤后复用基类，将端侧工具调用转换为 Core interrupt | `ClientToolRail#beforeToolCall`, `BaseInterruptRail` | ⬜ 计划中 |
| Task 等待投影 | 单 interaction map / 多 `items[]` 原样进入 A2A `INPUT_REQUIRED` | `JiuwenCoreAgentHandler`, `A2AAgentExecutor` | ✅ 通用投影已存在；本需求适配当前 Handler 输出 |
| continuation 编排分流 | 已恢复的全量 `client_tool` `_interrupt` 跳过 remote batch resume，定向结果到达 ExtHandler | `A2AEnabledServeOrchestrator` | ⬜ 本需求最小修改；其他形态和 coordinator 不改 |
| 客户端结果回灌 | resume TextPart 由基类 rail 转成原 ToolCall observation | `BaseInterruptRail`, `ClientToolRail#resolveInterrupt` | ⬜ 计划中 |
| ReActAgent / DeepAgent 覆盖 | 每次请求将 rail 绑定到实际执行的 ReActAgent | `ClientToolRail.bind()` | ⬜ 本需求实现；DeepAgent 仅验收显式启用 task-loop、默认 completion policy 且无已排队 follow-up 的场景 |

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|---|---|---|
| JSON-RPC A2A 初始调用 | ⬜ | `SendMessage` 和 `SendStreamingMessage` 可通过 `params.metadata` 提供端侧工具能力视图。 |
| Task 级工具隔离 | ⬜ | 并发 Task 使用同一 Agent 实例时，只能看到本次 invocation 的工具。 |
| 模型工具注入 | ⬜ | 客户端工具以 `ToolInfo` 形式进入本次模型调用，不进入共享注册表。 |
| 端侧工具调用中断 | ⬜ | 模型调用一个或多个端侧工具后，core 产生完整调用意图集合，每项包含名称、参数和 `toolCallId`。 |
| 非流式等待响应 | ⚠️ | 通用 interrupt 已支持；需补齐 client-tool 元数据和 resume 保真。 |
| SSE 等待事件 | ⚠️ | 通用 interrupt SSE 已支持；需验证 `_interrupt` 在队列关闭前已持久化并投递。 |
| GetTask 重新观察 | ⚠️ | TaskStore 已支持；需保证 status message metadata 保存完整 `_interrupt`。 |
| 多结果定向回灌 | ⬜ | 多 pending 时，client 在一次 continuation 中提交与全部 pending 一一对应、带 `TextPart.metadata.toolCallId` 的结果。 |
| 单结果回灌 | ⬜ | 只有一个 pending ToolCall 时允许普通结果文本，由 Core 现有字符串恢复能力定向到唯一等待项。 |
| 拒绝与错误回灌 | ⬜ | client 内部保留结构化 outcome，在 wire 上渲染为明确拒绝或错误 observation 文本，由 Agent 处理。 |
| ReActAgent 支持 | ⬜ | `ClientToolRail` 安装到 ReActAgent。 |
| DeepAgent 支持 | ⬜ | 仅支持显式 `enableTaskLoop(true)`、默认 completion policy 且 interrupt 时无已排队 follow-up；请求级 rail 安装到 `DeepAgent#getAgent()`，只按完整的 `conversationId + "_" + 数字序号` 匹配内部 round，不接受原始 ID。本期不修改 Core。 |
| 完整结果续接 | ⬜ | 多 pending 必须一次提交完整结果集合；缺少任一 pending 结果时整次 continuation 被拒绝，不恢复 Core。 |
| 同一 Task 多轮调用 | ⬜ | 当前调用集合全部解决并恢复后，后续模型轮允许再次产生新的端侧工具请求。 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|---|---|---|
| REST `/query`、MVC/WebFlux query 入口 | 当前需求只接受 JSON-RPC A2A 入口 | 后续独立特性评审 |
| 新增客户端结果提交 endpoint | A2A `SendMessage`/`SendStreamingMessage` 已能恢复原 Task | 使用 `message.taskId` + TextPart；多 pending 时同时携带 `metadata.toolCallId` |
| 服务端访问 DOM、插件、文件、本地端口 | 客户端资源不在服务端信任和网络边界内 | client 本地执行后主动提交结果 |
| 全局 Tool Registry、MCP、Skill Hub 注册 | 动态端侧工具只属于当前 invocation | `beforeModelCall` 注入本次 `ModelCallInputs.tools` |
| 执行前向 `AbilityManager` 注册 ToolCard、执行后删除 | 共享 Agent 并发时会串工具，同名工具会覆盖或误删 | 只注册请求级 rail；工具只注入 `ModelCallInputs.tools` |
| runtime 客户端工具批次协调器 | 客户端执行、审批、超时和重试由 agent-client 治理；Core 已保存 ToolCall 恢复状态 | runtime 只保真投影中断集合，并把结果按 `toolCallId` 交回 Core |
| 复用 `_remote_batch` | 该状态属于远端 Agent A2A 编排，拥有远端 Task 和调度语义 | client-tool 继续使用当前 Task `_interrupt` 与 Core checkpoint |
| Gateway / Event Bus Task owner | Task 权威状态属于 runtime | Gateway / Event Bus 只做受治理透传或投影 |
| runtime 判断业务结果真假 | runtime 只验证关联关系，不解释客户端业务事实 | Agent 消费 observation 后决定后续逻辑 |
| 平台级工具审计和授权策略 | 属于 middleware / bus / 业务治理扩展 | 本特性保留 metadata 和 trace 接缝，不新增策略系统 |
| 等待期间取消 | 本期不实现 FEAT-001 / FEAT-006 的标准 Task cancel 能力 | FEAT-009 将其降为 SHOULD；后续接入标准取消入口，已处于任一终态的 Task 本期仍禁止 client-tool resume |
| 损坏 JSON 或非 object 的工具参数 | 本期只承诺模型产生合法 JSON object 参数；为异常值新增无损 carrier 会扩大 Core/Runtime 契约 | 沿用 Core 标准工具错误；合法 object 内的 schema 不匹配仍由 client 形成 `invalid_tool_arguments` outcome |
| 同一 Task 内 client-tool 与远端 A2A 等待点交替或嵌套恢复 | 需要额外定义两类 pending metadata 的覆盖和恢复优先级，偏离本需求主链路 | 本期分别保证纯 client-tool 链路和既有纯远端 A2A 链路；组合链路后续独立设计 |

### 2.3 外部接口契约（Logical View）

#### 2.3.1 JSON-RPC 方法

| 方法 | 本特性用途 | 约束 |
|---|---|---|
| `SendMessage` | 初始调用、非流式端侧工具等待、SDK 内部提交 continuation 结果并恢复 Task | 工具定义放在 `params.metadata.clientTools`；SDK 内部 resume 使用原 `message.taskId`。多 pending 结果用 `TextPart.metadata.toolCallId` 定向，单 pending 可省略。 |
| `SendStreamingMessage` | 初始流式调用、通过 SSE 观察等待状态，也可作为 continuation 的传输入口 | 工具定义、结果关联和 Task 恢复约束与 `SendMessage` 相同。 |
| `GetTask` | client 断线或稍后恢复时重新查询 pending 端侧工具请求 | 只按 Task ID 查询，不创建新 Task |

#### 2.3.2 初始能力视图

```json
{
  "clientTools": [
    {
      "name": "readCurrentPage",
      "description": "读取当前页面内容",
      "inputSchema": {
        "type": "object",
        "properties": {
          "selector": {"type": "string"}
        },
        "additionalProperties": false
      }
    }
  ]
}
```

约束如下：

- `clientTools[].name` 必须非空，并在本次视图中唯一。
- `inputSchema` 使用 JSON Schema object 形态；空 schema 按空 object 处理。
- 每次请求携带当前完整工具集合；工具只对本次 Agent 执行有效。
- 本次请求未携带 `clientTools` 时，当前模型调用的端侧工具可见集合为空，不自动继承上一轮能力视图。
- `clientTools` 不增加 `toolId`。稳定 `toolId` 由 agent-client 保存在原 invocation 的 ToolView 快照中；收到工具请求后，client 只在该快照内以 `toolName` 解析本地 `toolId`，不得查询当前全局目录猜测历史调用。
- `toolName` 是模型可见名称和 wire 匹配键，必须在本次 ToolView 中唯一；`toolCallId` 是 Core 为每次实际调用生成的实例关联 ID。服务端按 `toolName` 选择工具、按 `toolCallId` 恢复具体调用，均不需要知道 client 本地 `toolId`。
- resume 请求即使不重新携带 `clientTools`，solution 也必须从原 Task 的 pending `_interrupt` 恢复全部待回灌 `toolCallId -> toolName`：单中断读取 `_interrupt` 自身，多中断读取 `_interrupt.items[]`。这不等价于把旧工具重新暴露给恢复后的模型调用。

#### 2.3.3 客户端结果

业务应用通过 agent-client 创建新的 continuation invocation，并关联旧 invocation 的 `input_required` 状态。SDK 内部解析 taskRef 后，使用原 `message.taskId` 恢复 Task。多 pending 时，每个最终结果使用至少一个非空 TextPart，并通过非空字符串 `metadata.toolCallId` 指定目标；同一结果拆为多个 Part 时，各 Part 使用相同 ID：

```json
{
  "message": {
    "role": "ROLE_USER",
    "messageId": "msg-2",
    "taskId": "task-123",
    "contextId": "ctx-1",
    "parts": [
      {
        "text": "页面正文……",
        "metadata": {"toolCallId": "call-123"}
      },
      {
        "text": "客户端拒绝执行该工具：用户未批准该操作。",
        "metadata": {"toolCallId": "call-456"}
      }
    ]
  }
}
```

client 内部的 `ToolExecutionRecord` 继续保存结构化 outcome、payload/payloadRef、幂等键和审计引用；当前 A2A wire 不新增 `clientToolResults`、结果状态枚举或 DataPart，而是把每个最终 outcome 渲染为明确且非空的 observation 文本。工具执行成功但没有业务返回内容时也必须生成明确文本，例如 `客户端工具执行成功，无返回内容。`，不能提交空串或仅空白字符。`TextPart.metadata.toolCallId` 只做调用实例关联，不承载客户端本地 `toolId`、审批过程或中间执行状态：

| 场景 | TextPart 示例 | Agent 语义 |
|---|---|---|
| 成功 | `页面正文……` | 正常工具 observation |
| 用户拒绝 | `客户端拒绝执行该工具：用户未批准该操作。` | Agent 决定说明、降级或结束 |
| 执行错误 | `客户端工具执行失败：本地插件不可用。` | Agent 决定换工具、重试或结束 |

多 pending 时，一条 Message 必须一次提交与当前 pending 集合完全一致的结果集合，每个结果携带非空 `toolCallId`；同一 ID 的多个 TextPart 按原顺序拼成一个 observation。只有一个 pending ToolCall 时允许省略 ID，并把普通 TextPart 作为唯一 observation。Solution 只校验恢复所需的 pending 类型和结果目标集合，不解析或判断 observation 的业务内容；缺项或未知 ID 时整次拒绝且不恢复 Core。非空结果文本由 FEAT-007 的 client 提交契约保证，空白文本和带/不带 ID 混用等偏门异常本期不增加专用处理。

#### 2.3.4 Task 等待投影

runtime 复用当前 status message metadata 的 `_interrupt`，不新增平行的 `clientToolRequests` Task 字段，也不创建 `_client_tool_batch`。wire 形态直接适配 `JiuwenCoreAgentHandler` 当前实现：单个中断使用原 interaction map，同轮多个中断使用 `items[]` envelope。

单调用示例：

```json
{
  "_interrupt": {
    "type": "__interaction__",
    "index": 0,
    "message": "Client tool invocation required: readCurrentPage",
    "toolCallId": "call-123",
    "toolName": "readCurrentPage",
    "context": {
      "_interrupt_kind": "client_tool",
      "arguments": {"selector": "#main"}
    }
  }
}
```

多调用示例：

```json
{
  "_interrupt": {
    "message": "interaction batch",
    "items": [
      {
        "type": "__interaction__",
        "index": 0,
        "message": "Client tool invocation required: readCurrentPage",
        "toolCallId": "call-123",
        "toolName": "readCurrentPage",
        "context": {
          "_interrupt_kind": "client_tool",
          "arguments": {"selector": "#main"}
        }
      },
      {
        "type": "__interaction__",
        "index": 1,
        "message": "Client tool invocation required: confirmLocalAction",
        "toolCallId": "call-456",
        "toolName": "confirmLocalAction",
        "context": {
          "_interrupt_kind": "client_tool",
          "arguments": {"action": "submit"}
        }
      }
    ]
  }
}
```

单 interaction 或 `items[]` 中每个 item 的 `index` 是 Core 流输出序号，`message`、`context`、`toolCallId` 和 `toolName` 是 client 执行所需的稳定投影。`context.arguments` 只承诺投影合法 JSON object；object 内缺少必填字段、类型不匹配等 schema 问题由 client 判定并形成 `invalid_tool_arguments` outcome。不可解析 JSON 或非 object 参数沿用 Core 标准工具错误，本期不为其新增无损 carrier。当前 Handler 还会在每个 interaction 中保留原始 `payload`；Runtime、TaskStore、SSE、阻塞响应和 `GetTask` 必须原样保存该字段以及 Handler 产生的两种 wire 形态，不额外重写 envelope。`payload` 只用于现有链路保真和诊断，client 不应解析 `payload.value` 的内部 Java 序列化结构；本节及第 6.3 节示例因此省略 `payload`，不表示传输链路会删除它。

agent-client 和 solution 在各自进程内按 Handler 当前两种 wire 形态构造 pending 列表：存在合法 `items[]` 时按原顺序取全部 item，否则把合法的顶层 interaction map 视为单元素列表。这只是内部列表化处理，不改变 Task 中保存的 wire 数据，也不要求 Handler 对单中断生成 `items[]`。单 interaction 必须为 `client_tool`；多 interaction envelope 的 item 必须全部为 `client_tool`。同轮混入 `ask_user` 或 `a2a_delegate` 时按不支持的混合中断失败，不能静默过滤。`_interrupt_kind=client_tool` 用于区别纯用户输入和远程 A2A delegate。

#### 2.3.5 行为承诺

- **必须**：端侧工具等待时 Task 保持 `INPUT_REQUIRED`，不得标记为 `COMPLETED`。
- **必须**：业务 continuation invocation 由 agent-client 内部映射到原 Task；runtime 不得因 continuation 创建新的服务端 Task。
- **必须**：本需求产生的每个 `client_tool` interaction 稳定包含非空 `toolName` 和 `toolCallId`。`ClientToolRail` 使用 `ToolCallInterruptRequest.fromToolCall(request, toolCall)` 生成字段，Runtime 按 Handler 当前单 map / 多 `items[]` 形态原样投影；字段保真由跨层测试保证，不增加生产态 outgoing validator。
- **必须**：多 pending 时一次 continuation 提交与当前等待集合完整对应的逻辑结果，每个 TextPart 携带非空字符串 `metadata.toolCallId`；同一 ID 的多个非空 Part 按序拼接。单 pending 时普通 TextPart 可省略 ID，由 Core 现有字符串恢复能力定向到唯一等待项。
- **必须**：从 Handler 现有两种 wire 形态解析出的结果集合不完整或含未知目标时，在恢复 Core 前拒绝整次 continuation；不得做部分回灌、文本广播或顺序猜测。
- **必须**：动态工具只进入本次模型调用，不进入共享 `AbilityManager`；请求结束后临时 rail 必须注销。
- **必须**：客户端拒绝和执行错误使用明确的 observation 文本回灌 Agent；runtime 不根据文本业务含义直接把 Task 置为 FAILED。
- **禁止**：使用 TextPart 承载工具定义；工具定义只放在 `params.metadata.clientTools`。
- **禁止**：runtime 或 core 直接访问客户端资源。
- **允许**：client 在 continuation 中重新提供当前完整 `clientTools`；未提供时仍可恢复 pending ToolCall，但全部结果回灌后的下一轮模型调用不再看见上一轮客户端工具。

---

## 3. 模块结构（Development View）

### 3.1 agent-runtime-java

```text
service/agent-service-app/
└── controller/a2a/
    ├── A2aJsonRpcController.java       # 已有：解析 params.metadata
    ├── A2AProtocolAdapter.java         # 已有：metadata -> ServeRequest
    └── A2AAgentExecutor.java           # 保存/恢复 _interrupt，推进 INPUT_REQUIRED
```

本特性不修改 `agent-service-spec` DTO，不给 `ServeRequest` 增加 client-tool 专用字段，也不修改 REST controller。

### 3.2 agent-solution runtime extension

```text
common/agent-runtime-ext-java/
└── agent-service-adapters/agent-service-adapters-agentcore-ext/
    └── src/main/java/com/openjiuwen/service/adapters/agentcore/ext/
        ├── agentfw/
        │   └── JiuwenCoreAgentExtHandler.java     # 请求前绑定 rail，finally 注销
        └── external/
            └── ClientToolRail.java                # 请求解析与绑定、模型注入、Session 过滤和中断恢复
```

Solution 只在既有 `external` 包直接新增 `ClientToolRail.java` 一个生产文件，并修改既有 `JiuwenCoreAgentExtHandler`；单文件无需再创建 `external.clienttool` 子包。`ClientToolRail` 除承载 Core callback 行为外，还以静态 `bind(agent, request)` 收口 metadata/pending 解析、目标定位、名称冲突检查和注册生命周期；`RequestContext`、`SessionMatchMode` 与实现 `AutoCloseable` 的 `Binding` 均为该文件内的私有或嵌套类型，不新增公共 DTO、Support 类或 Spring Bean。这样保留一个清晰的 client-tool 功能边界，避免把约束解析细节堆入通用 Handler。

### 3.3 agent-core-java

本特性不因客户端工具新增 Core API。多调用支持以前置代码基线为条件，直接复用：

```text
BaseAgent
├── registerRail(AgentRail)
└── unregisterRail(AgentRail)

BaseInterruptRail
├── beforeToolCall() 静态工具名匹配
├── resume input 提取
├── ToolInterruptException
└── _skip_tool + ToolMessage 回灌

ReActAgent
├── ModelCallInputs.tools
├── ToolInterruptionState：完整保存同轮多个 ToolCall
└── InteractiveInput.userInputs：按 toolCallId 定向恢复

DeepAgent
└── getAgent() -> inner ReActAgent
```

本需求不修改 Core 生产代码。ReActAgent 直接复用上述能力；DeepAgent 通过 `getAgent()` 定位其内部 ReActAgent，并按第 4.1.3、11.2 节限定支持配置。

### 3.4 核心类静态关系

```text
JiuwenCoreAgentHandler
        ▲
        │ extends
JiuwenCoreAgentExtHandler ── ClientToolRail.bind(agent, request) ──▶ parse + resolve target
        │                                                                  │
        │ try/finally                                                      ▼
        ▼                                                        ReActAgent / DeepAgent.getAgent()
super.query/streamQuery()                                                  │
        │                                                        registerRail(ClientToolRail)
        │                                                                  │
        └──── return/interrupt/error ──▶ ClientToolRail.Binding.close() ────┘
                                                                           │
                                                                 unregisterRail(exact rail)

ClientToolRail extends BaseInterruptRail
├── beforeModelCall()：只向当前 Session 注入 ToolInfo
├── beforeToolCall()：Session 过滤后调用 super.beforeToolCall()
└── resolveInterrupt()：每个 ToolCall 独立产生 client_tool interrupt；resume 按 toolCallId reject
```

---

## 4. 核心设计（Logical + Process View）

### 4.1 请求级 rail 生命周期

#### 4.1.1 方案选择

| 方案 | 优点 | 问题 | 结论 |
|---|---|---|---|
| 修改 Core，为 `BaseInterruptRail` 增加动态匹配扩展点 | 可安装单例 rail，动态读取当前上下文 | 当前基类方法已经允许子类覆盖并调用 `super`，新增 Core API 没有必要 | 不采用 |
| Agent 上永久安装一个无状态 rail，通过 Session env 读取工具 | 不需要反复注册 callback | 必须覆盖 `runnerSession()`、改变 Session 构造并向 DeepAgent 传播请求数据 | 不采用 |
| 每次请求创建 rail，按 Session、可见名称或 pending 调用身份过滤后调用 `super.beforeToolCall()` | Core 零修改；不改 input/Session；复用基类 resume input 提取和 ToolMessage 回灌 | 需要在 Handler 中严格管理注册和注销，并在子类中防止 pending-only 名称误拦截新调用 | **采用** |

请求级 rail 方案只扩展 solution；另在 Runtime 编排入口增加第 4.5.2 节的最小 resume 分流。`beforeToolCall` 增加请求 Session 与调用资格门控：当前视图中的可见工具允许按名称产生新调用，pending-only 工具只允许原 `toolCallId -> toolName` 精确命中；门控通过后，静态名称匹配、resume input 提取、`_skip_tool` 和 ToolMessage 回灌仍由 `BaseInterruptRail` 完成。

其中 Session guard 是 `ClientToolRail` 为请求级并发隔离新增的适配逻辑，并非复用 `RemoteA2aInterruptRail`；复用范围仅包括 `RemoteA2aToolInstaller` 的目标 Agent 解析模式，以及 guard 命中后 `BaseInterruptRail` 的既有中断恢复流程。

#### 4.1.2 Handler 生命周期

`JiuwenCoreAgentExtHandler` 不覆盖 `runnerSession()`，不改 Core input，也不把工具定义写入 Session env。它保留现有 `installBeforeRun()` 对 Remote A2A 工具和 SkillHub 的安装行为，在调用父类前完成 client-tool metadata 解析、必要的多结果定向转换并创建请求级绑定：

```java
@Override
public void streamQuery(ServeRequest request, QueryStreamObserver observer) {
    installBeforeRun(); // 既有 Remote A2A + SkillHub 行为
    try (var binding = ClientToolRail.bind(getAgent(), request)) {
        super.streamQuery(request, observer);
    }
}

@Override
public QueryResponse query(ServeRequest request) {
    installBeforeRun(); // 既有 Remote A2A + SkillHub 行为
    try (var binding = ClientToolRail.bind(getAgent(), request)) {
        return super.query(request);
    }
}
```

`bind(agent, request)` 内部先解析 `clientTools` 和 Executor 已恢复的 `_interrupt`，形成私有 `RequestContext`，再定位目标 Agent并注册 rail。多 pending 必须把 `runtime.remoteToolInputs` 的目标集合与 pending 集合核对后转换为 Handler 已支持的 `runtime.remoteToolResults`；单 pending 未携带 `toolCallId` 时保留普通文本输入，直接复用 ReActAgent 的单中断字符串恢复。该适配不改写 Handler 的 outgoing wire 形态。mixed kind、损坏的 `_interrupt` 等非正常输入本期不增加专用修复逻辑，按第 8 章支持边界处理。

该生命周期成立的代码前提如下：

- `BaseAgent` 已公开 `registerRail()` 和 `unregisterRail()`。
- `JiuwenCoreAgentHandler#query()` 同步返回最终结果或中断结果。
- `JiuwenCoreAgentHandler#streamQuery()` 在当前调用栈内同步消费 Iterator；DeepAgent 即使在 `deep-agent-stream-*` 后台线程生产数据，Handler 仍阻塞到 `postRun()` 写入 `END_FRAME`、迭代结束或抛出异常后才返回。
- `ClientToolRail.Binding#close()` 只注销当前 rail 实例；`ClientToolRail#getTools()` 为空，因此不会增删 `AbilityManager` 中的 ToolCard。
- `ClientToolRail.bind()` 以原始 Agent 对象作为生命周期锁：普通 `BaseAgent` 锁定自身；DeepAgent 锁定外层 `DeepAgent`，在同一临界区内调用 `ensureInitialized()`、取得内部 ReActAgent并注册 rail。`Binding#close()` 使用同一锁注销，避免同一共享 Agent 上请求并发开始/结束时 callback 列表变更交错；锁只保护注册表变更，不包围 Agent 执行。DeepAgent 的通用首次初始化并发治理属于 Core 生命周期，本需求不额外实现或验收。

无 `clientTools` 且无 pending `client_tool` 中断时，`bind()` 返回 no-op binding。

#### 4.1.3 DeepAgent task-loop 中断边界

本期 DeepAgent 只支持显式 `enableTaskLoop(true)`、默认 completion policy，且 interrupt 时没有已排队 follow-up。该配置下 `runTaskLoop()` 返回后，现有 `postRun()` / `END_FRAME` 链路会使 Handler 结束 Iterator 消费，随后 `finally` 注销请求级 rail。关闭 task-loop、显式 completion policy或已有 follow-up 的组合不在本期支持和验收范围；Solution 不探测这些配置，也不复制 Core 的 task-loop 控制逻辑。

### 4.2 目标 Agent 与请求范围

`ClientToolRail.bind()` 复用 `RemoteA2aToolInstaller` 的目标解析模式，同时保留目标类型和生命周期锁，供 Session guard 与并发注册选择规则。直接传入的 ReActAgent 属于 `BaseAgent`；DeepAgent 不继承 `BaseAgent`，而是组合并持有内部 ReActAgent。metadata 解析先形成同文件内的 `RequestContext`，初始化、冲突检查和注册在同一个生命周期锁内完成：

```java
public static Binding bind(Object agent, ServeRequest request) {
    RequestContext context = prepare(request);
    if (context.isEmpty()) {
        return Binding.noop();
    }
    if (agent instanceof DeepAgent deepAgent) {
        synchronized (deepAgent) {
            deepAgent.ensureInitialized();
            return installLocked(
                deepAgent.getAgent(), deepAgent,
                SessionMatchMode.DEEP_AGENT_DERIVED, context);
        }
    }
    if (agent instanceof BaseAgent baseAgent) {
        synchronized (baseAgent) {
            return installLocked(
                baseAgent, baseAgent, SessionMatchMode.EXACT, context);
        }
    }
    throw new IllegalArgumentException("Unsupported agent type");
}
```

`prepare()`、`installLocked()`、`RequestContext` 和 `Binding` 都收口在 `ClientToolRail.java`；除 Handler 需要使用的 `bind()` 和可关闭的 `Binding` 外，其余均不对外暴露。`installLocked()` 在已持有 lifecycle lock 的前提下完成名称冲突检查、rail 创建和 `registerRail()`，并把同一个 lock owner 交给 `Binding#close()`。

`ClientToolRail.bind()` 从内部 `RequestContext` 构造以下不可变数据：

- `visibleTools`：当前 `metadata.clientTools` 的完整工具定义，只供 `beforeModelCall` 注入。
- `visibleToolNames`：`visibleTools` 的名称集合，决定哪些新 ToolCall 可以被识别为本次客户端工具调用。
- `pendingCallsById`：从 pending `_interrupt` 单 map / 多 `items[]` 恢复的 `toolCallId -> toolName` 映射，只决定哪些既有 ToolCall 可以回灌结果。
- `baseInterceptToolNames`：`visibleToolNames` 与 `pendingCallsById.values()` 的并集，仅传给 `BaseInterruptRail` 的静态名称集合；它不是充分匹配条件，子类必须先完成 Session 与调用身份门控。

rail 同时保存 `request.getConversationId()` 和 `SessionMatchMode`。每个钩子首先执行以下范围判断，不属于当前请求则立即返回：

```java
boolean belongsToCurrentRequest(AgentCallbackContext ctx) {
    String sessionId = ctx.getSession().getSessionId();
    if (matchMode == SessionMatchMode.EXACT) {
        return sessionId.equals(conversationId);
    }
    Pattern derived = Pattern.compile(
        "^" + Pattern.quote(conversationId) + "_[0-9]+$");
    return derived.matcher(sessionId).matches();
}
```

禁止使用无边界的 `startsWith(conversationId)`。普通 ReActAgent 只接受精确原始 ID；DeepAgent 的 callback Session 按当前代码使用派生 ID，因此只接受完整数字后缀正则，不同时接受原始 ID。这样 `ctx` 对应的 rail 不会命中另一个请求 `ctx_1` 的 DeepAgent 派生 Session `ctx_1_<requestSeq>`。本设计只针对当前代码基线，不额外设计其他 Session 命名形态。

### 4.3 `beforeModelCall`：本次模型工具注入

```text
ClientToolRail.visibleTools
  -> 确认 callback Session 属于当前请求
  -> 读取当前 ModelCallInputs.tools
  -> 检查名称冲突
  -> 用 ToolInfo.builder() 映射 name / description / inputSchema -> parameters
  -> 复制当前列表、追加 ToolInfo，再通过 ModelCallInputs.setTools() 写回
```

`ClientToolRail` 构造时显式 `setPriority(70)`。当前 `AgentCallbackManager` 按优先级从高到低执行；已有 `ProgressiveToolRail` 为 90、`AgentModeRail` 为 85，且二者会过滤 `ModelCallInputs.tools`。client rail 必须在这些既有过滤完成后追加当前 ToolView，否则 DeepAgent 首次初始化时的 rail 注册先后会决定端侧工具是否被误删。优先级只固定当前代码中的回调顺序，不修改任何既有 rail。

工具定义保存在请求级 rail 的不可变字段中，不写入 Session、callback extra 或 Agent 全局状态，也不把 `ToolInfo` 反向包装成 `ToolCard`。`installBeforeRun()` 先完成既有 Remote A2A/SkillHub 安装，随后 `ClientToolRail.bind()` 仅针对本次新声明的 `visibleTools`，以已初始化目标 ReActAgent 的 `AbilityManager.listToolInfo()` 检查客户端名称是否与服务端、Remote A2A 或 SkillHub 工具冲突；`beforeModelCall` 再对当次 `ModelCallInputs.tools` 做防御性冲突检查。若同一视图内重名或任一层冲突，当前执行失败并返回可诊断错误；禁止覆盖或拦截已有工具。从历史 `_interrupt` 恢复出的 pending-only 名称只用于完成原 ToolCall 的结果回灌，不重新声明或暴露工具，也不因恢复时出现新的同名服务端工具而绕过既有 Session + `toolCallId` 恢复点。

### 4.4 `beforeToolCall`：范围过滤后复用基类

`ClientToolRail` 不重新实现 resume input 提取或 ToolMessage 回灌，但必须在调用基类前增加 Session 和调用身份双重过滤：

```java
@Override
public void beforeToolCall(AgentCallbackContext ctx) {
    if (!belongsToCurrentRequest(ctx)) {
        return;
    }
    ToolCallInputs inputs = requireToolCallInputs(ctx);
    ToolCall toolCall = inputs.getToolCall();
    String toolName = requireNonBlank(inputs.getToolName(), "toolName");
    String toolCallId = toolCall.getId();
    String pendingToolName = pendingCallsById.get(toolCallId);
    boolean isPendingCall = pendingToolName != null;
    boolean isNewVisibleCall = visibleToolNames.contains(toolName);
    if (!isPendingCall && !isNewVisibleCall) {
        return;
    }
    requireNonBlank(toolCallId, "toolCallId");
    if (pendingToolName != null && !pendingToolName.equals(toolName)) {
        throw new IllegalArgumentException("Pending client tool identity mismatch");
    }
    super.beforeToolCall(ctx);
}
```

`super.beforeToolCall(ctx)` 完整复用 `BaseInterruptRail` 的既有流程：

1. 判断当前工具名是否属于构造函数传入的 `baseInterceptToolNames`。
2. 按当前 ToolCall ID 从 `InteractiveInput.userInputs` 提取定向结果；单 pending 的普通 TextPart 由 ReActAgent 现有字符串恢复能力转换为同一 `InteractiveInput` 形态。
3. 调用子类 `resolveInterrupt()`。
4. 首次调用抛出 `ToolInterruptException`；resume 时设置 `_skip_tool`、`toolResult` 和 `ToolMessage`。

`baseInterceptToolNames` 只是调用基类所需的名称集合。若 continuation 没有重传 `clientTools`，恢复完成后模型又产生同名但不同 `toolCallId` 的新调用，`visibleToolNames` 不包含该名称，`pendingCallsById` 也不包含新 ID，因此必须直接返回，不得仅凭历史名称再次中断。若 continuation 明确重传了该工具，则名称仍在 `visibleToolNames` 中，新调用可以进入下一次 client-tool 中断。

服务端只把当前 `visibleToolNames` 中的调用或 `pendingCallsById` 精确命中的恢复调用识别为 client-tool；任意不在 ToolView、也不属于 pending 的未知名称无法可靠区分为客户端工具还是普通服务端工具幻觉，继续走 Core 既有 unknown-tool 行为，不得把所有未知工具兜底移交给 client。FEAT-007 的防御性拒绝仍然成立：若一个合法 client-tool 投影到达 client 后，历史 ToolView 快照、本地注册表、权限或上下文已经不匹配，client 返回 `tool_not_declared`、`tool_not_found`、`permission_denied` 或等价 outcome，再由本需求按普通 observation 恢复。

`ClientToolRail` 只实现决策差异：

```java
@Override
protected InterruptDecision resolveInterrupt(
        AgentCallbackContext ctx, ToolCall toolCall, Object resumeInput) {
    String pendingToolName = pendingCallsById.get(toolCall.getId());
    if (pendingToolName != null) {
        if (!pendingToolName.equals(toolCall.getName()) || resumeInput == null) {
            throw new IllegalArgumentException("Invalid pending client tool resume");
        }
        return reject(resumeInput);
    }
    InterruptRequest request = InterruptRequest.builder()
        .message("Client tool invocation required: " + toolCall.getName())
        .context(Map.of(
            "_interrupt_kind", "client_tool",
            "arguments", parseArguments(toolCall.getArguments())))
        .build();
    return interrupt(ToolCallInterruptRequest.fromToolCall(request, toolCall));
}
```

`ToolCallInterruptRequest.fromToolCall(request, toolCall)` 使用现有 Core API 把当前调用的 `toolCallId` 和 `toolName` 写入中断请求；`JiuwenCoreAgentHandler#toInterruptData()` 随后将二者提升到单 interaction 顶层或多 `items[]` 的每一项。`_interrupt_kind=client_tool` 只用于中断产生后的 runtime 路由和客户端识别，不承担 rail 工具匹配；工具匹配由 `BaseInterruptRail` 的 `toolNames` 完成。Rail 不维护批次计数、完成屏障或共享结果 Map，因此同一 rail 实例并行处理多个 ToolCall 时没有 client-tool 批次可变状态。

`parseArguments()` 对空参数返回空 object，对合法 JSON object 返回结构化 Map；参数是否满足本地工具 schema 由 agent-client 按 FEAT-007 判断并形成最终 outcome。不可解析 JSON 或合法但非 object 的值不进入 client-tool 投影，沿用 Core 标准工具错误；本期不扩展参数载体。

### 4.5 A2A interrupt 投影和 Task 恢复

#### 4.5.1 首次中断

```text
ClientToolRail（每个 ToolCall 独立）
  -> ToolInterruptException A/B/...
  -> ReActAgent ToolInterruptionState 保存完整同轮集合
  -> JiuwenCoreAgentHandler 保持现状：单 interaction 返回原 map，多 interaction 返回 items[]
  -> QueryChunk(type=interrupt, data=单 interaction map / 多 items[] envelope)
  -> JiuwenCoreAgentExtHandler 不改写单/多 wire 形态
  -> A2AEnabledServeOrchestrator 仅识别全部为 a2a_delegate 的集合
  -> client_tool 不进入 RemoteInvocationBatchCoordinator，原样转发
  -> A2AAgentExecutor
  -> status message metadata._interrupt = 完整 envelope
  -> emitter.requiresInput(statusMessage)
  -> Task = INPUT_REQUIRED
```

若服务启用了 `A2AEnabledServeOrchestrator`，中断在进入 `A2AAgentExecutor` 前还经过一次按 kind 分流：

```text
JiuwenCoreAgentHandler QueryChunk(type=interrupt)
  -> A2AEnabledServeOrchestrator#handleInterrupt() / #handleQueryInterrupt()
     ├─ _interrupt_kind = a2a_delegate
     │    -> handleA2ADelegate()，进入远程 A2A 委派闭环
     └─ 其他 kind（client_tool / ask_user）
          -> 原样 observer.onNext(interrupt)
          -> observer.onComplete()
          -> A2AAgentExecutor 接收 interrupt
          -> requiresInput(statusMessage)
```

因此，首次产生的单个 `client_tool` map 或多项全 `client_tool` envelope 都不会进入远程委派执行路径，而是穿透编排层交给当前 A2A client。本需求不创建客户端工具 coordinator，也不修改远端批次执行语义；仅对 continuation 进入 Handler 前的远端批次 resume 探测增加下一节所述分流。同轮混合 `client_tool` 与其他 interrupt kind 本期不支持，不为该偏门组合增加 Solution outgoing 包装层。

#### 4.5.2 resume

`A2AAgentExecutor` 只在现有 Task 状态为 `INPUT_REQUIRED` 时从 Task status message 或 history 恢复完整 `_interrupt`。它不解析 client-tool 业务语义；pending 解析、目标校验和结果转换由 `JiuwenCoreAgentExtHandler` 调用 Handler 前创建的 solution 请求级适配完成：

`A2AEnabledServeOrchestrator` 只为已恢复且可信的纯 client-tool 等待点增加旁路，流式、非流式入口共用同一判断：

```java
private static boolean isClientToolResume(ServeRequest request) {
    Object stored = request.getMetadata().get("_interrupt");
    if (!(stored instanceof Map<?, ?> map)) {
        return false;
    }
    Map<String, Object> interrupt = new LinkedHashMap<>();
    map.forEach((key, value) -> interrupt.put(String.valueOf(key), value));
    return isAllInterruptKind(interrupt, "client_tool");
}
```

`isAllInterruptKind()` 同时处理 Handler 当前的单 interaction map 和多 `items[]`，要求集合非空且 `_interrupt_kind` 全部等于目标 kind。`tryResumePending()` 与 `syncResumePending()` 仅在 `isClientToolResume()` 返回 `true` 时跳过 `batchCoordinator.resume()`，直接把原 `ServeRequest` 交给 Handler；返回 `false` 时完整执行现有 coordinator 探测。判断只读取 Executor 从原 Task 恢复的中断 kind，不根据 `runtime.remoteToolInputs` 猜测业务类型。除全量 `client_tool` 外的所有形态均保持原行为；mixed kind 本期不支持。

```text
agent-client continuation invocation
  -> SDK 内部 SendMessage(message.taskId, message.parts[])
  -> A2AProtocolAdapter 提取 TextPart 文本和可选 toolCallId
  -> RequestContext.getTask()
  -> state == INPUT_REQUIRED
  -> copyStoredInterrupt(task, ServeRequest.metadata)
  -> A2AEnabledServeOrchestrator 确认已恢复的 _interrupt 为全量 client_tool
  -> 跳过 RemoteInvocationBatchCoordinator.resume()，直接进入 Handler
  -> A2AEnabledServeOrchestrator 原样转发 client_tool resume
  -> ClientToolRail.bind() 将单 map / 多 items[] 解析为 pending 列表
  -> 校验结果集合与当前 client_tool pending items 完整且一一对应
  -> 多 pending：校验 runtime.remoteToolInputs 后转成 runtime.remoteToolResults
  -> 单 pending：无 toolCallId 时保留普通文本，由 ReActAgent 定向到唯一 pending ToolCall；带 ID 时按多目标路径校验并转换
  -> JiuwenCoreAgentHandler 沿用现有逻辑构造 InteractiveInput.userInputs
  -> ClientToolRail.bind() 读取全部 pending client_tool 的 toolCallId -> toolName
  -> 创建请求级 ClientToolRail(baseInterceptToolNames = visible names + pending names)
  -> ReActAgent 根据 ToolInterruptionState 将每个输入关联到对应 pending ToolCall
  -> ClientToolRail.beforeToolCall() 过滤 Session，并按 visible name 或 pending ID + name 放行后调用 super
  -> 每个结果：BaseInterruptRail reject(resumeInput) -> _skip_tool + ToolMessage
  -> 全部 pending 一次解决后进入下一轮 Agent 推理
```

已有 Task 但不是 `INPUT_REQUIRED` 时，`A2AAgentExecutor` 不恢复 `_interrupt`，solution 不得把请求识别为 client-tool resume。多 pending 的结果 ID 集合缺项、含未知或非当前等待 ID 时，由 solution 在恢复 Core 前拒绝。`runtime.remoteToolInputs` 和 `runtime.remoteToolResults` 在本链路中都只是受信任内部的 `Map<toolCallId, observationText>`，不包含 outcome、payload、审计或幂等字段，不是新的 wire 结果 carrier；Runtime 和 solution 都不解析其中的业务文本。

这两个 key 仍是受信任的内部 metadata，外部同名 metadata 会被 Adapter 清除。`A2AProtocolAdapter` 沿用现有 TextPart 提取逻辑：同一 `toolCallId` 的多个 TextPart 按原顺序拼接为一个逻辑 observation，不同 ID 分别写入 `remoteToolInputs`；solution 根据 Executor 已恢复的单 map / 多 `items[]` 完成 pending 集合校验并转换为 `remoteToolResults`。转换后移除 `remoteToolInputs`，且不写 `runtime.remoteBatchId`，因此不创建、恢复或修改 `_remote_batch`；单 pending 的无 ID 普通文本不做 Map 转换，由 Core 现有能力恢复。

本需求新增的 continuation 校验只包括恢复主链路必需内容：确认 Task 恢复出的 pending 是 client-tool；多 pending 或显式带 ID 的单 pending，结果 ID 集合必须与 pending 集合一致；单 pending 无 ID 时由 Core 定向到唯一调用。Gateway 鉴权与租户授权、invocation 到 taskRef 的映射、标准 A2A Task/context 所有权校验和既有 messageId/重试幂等语义均复用 FEAT-001/FEAT-006 入口，本需求不新增幂等表，也不改造通用 ingress。若 continuation 未携带新的 `clientTools`，`ClientToolRail` 仍按 pending `toolCallId + toolName` 完成原调用回灌，但不重新向模型暴露历史工具。

### 4.6 Task 状态机

| 当前状态 | 事件 | 下一状态 | 附加行为 |
|---|---|---|---|
| 无 Task | 初始 `SendMessage`/`SendStreamingMessage` | `SUBMITTED` | 创建 Task |
| `SUBMITTED` | Agent 开始执行 | `WORKING` | `emitter.startWork()` |
| `WORKING` | 一个或多个 client-tool interrupt | `INPUT_REQUIRED` | status message 原样保存单 map / 多 `items[]` 的完整 `_interrupt` |
| `INPUT_REQUIRED` | continuation 的 TextPart 定向结构非法 | 保持原状态或由标准入口拒绝 | Adapter/SDK 在 Agent 执行前返回协议错误；不恢复 Core，不承诺创建新的失败状态 |
| `INPUT_REQUIRED` | 已通过协议解析，但 pending 结果集合不完整或非法 | `FAILED` | solution 在恢复 Core 前拒绝；按当前异步 AgentExecutor 契约走标准失败投影，不承诺原等待态可重试 |
| `INPUT_REQUIRED` | continuation 一次提交全部合法结果 | `WORKING` | 每个结果关联到对应 pending ToolCall，Core 才进入下一轮执行 |
| `WORKING` | Agent 正常结束 | `COMPLETED` | 返回最终 artifact/response |
| `WORKING` | Agent 决定失败或执行异常 | `FAILED` | 记录可诊断错误 |
| `WORKING` | Agent 再次调用端侧工具 | `INPUT_REQUIRED` | 进入下一轮等待 |
| 任一终态 | 迟到 continuation | 保持原终态 | A2A SDK 在 Executor 前返回 `UnsupportedOperationError`，不执行 Handler/Core |

### 4.7 并发隔离

本特性禁止向共享 `AbilityManager` 临时注册 ToolCard：

```java
try {
    abilityManager.add(clientToolCard);
    super.streamQuery(request, observer);
} finally {
    abilityManager.remove(clientToolCard.getName());
}
```

共享 Agent 并发执行时，注册到注销之间的 ToolCard 对所有 Task 可见；同名工具还会发生覆盖和误删。请求级 rail 的注册/注销不会注册 ToolCard，并按目标类型对应的 Session 规则过滤 callback：

```text
共享 ReActAgent
  ├── ClientToolRail A(origin=A, mode=EXACT/DEEP_AGENT_DERIVED, immutable tools A)
  └── ClientToolRail B(origin=B, mode=EXACT/DEEP_AGENT_DERIVED, immutable tools B)

ReAct Session A 或 DeepAgent 派生 Session A_<数字> callback
  ├── rail A: 命中并处理
  └── rail B: origin/mode 不匹配，返回

ReAct Session B 或 DeepAgent 派生 Session B_<数字> callback
  ├── rail A: origin/mode 不匹配，返回
  └── rail B: 命中并处理
```

`AgentCallbackManager` 按 rail 实例记录 callback，`ClientToolRail.Binding#close()` 只移除自己的注册。同一目标 Agent 的 client rail 注册和注销使用第 4.1.2 节的生命周期锁串行化，callback 执行不持锁；不同请求仍可并发执行，并由 Session guard 隔离。同一请求内多个 ToolCall 可以并行进入同一个 `ClientToolRail`；rail 只读取不可变工具集合和 callback 自身的 ToolCall/resume input，不保存批次完成计数或共享结果，因此不得增加需要锁保护的 client-tool 批次状态。

当前 Core interruption state 本身以 Session 为恢复边界，因此同一 Session 的并行 Agent invocation 不在本特性并发承诺内；runtime 应维持同一 A2A context 的顺序执行约束。不同 contextId 之间应覆盖 `ctx` 与 `ctx_1` 等容易混淆的隔离测试：DeepAgent rail 只匹配各自完整派生 Session，不匹配任何原始 ID。

### 4.8 安全与可观测性

- client 提供的工具名称、description、schema、arguments 和结果文本全部按不可信输入处理。
- 工具 description 只作为模型工具说明，不得被解释为系统级配置、Spring 表达式、脚本或服务端执行指令。
- schema 只用于模型参数描述和结构校验，不触发动态类加载或服务端反射调用。
- observation 进入模型上下文前按现有消息安全策略进行长度控制、转义和敏感信息处理。
- 日志只记录 Task ID、toolCallId、工具名和脱敏错误摘要，不记录完整页面内容、插件返回内容或敏感 arguments。
- 首次中断、`INPUT_REQUIRED`、continuation 接收、目标校验、每个 toolCallId 的回灌和再次中断应沿用现有 trajectory/trace 接缝；记录 tenant、conversation、invocation、Task、toolCallId、trace 和耗时，不新增平行审计存储。

---

## 5. 配置模型（Physical View）

### 5.1 配置结论

本特性不新增 YAML 或 Spring ConfigurationProperties。端侧工具是客户端随 invocation 提供的能力视图，不是服务启动配置。

| 配置项 | 结论 | 原因 |
|---|---|---|
| 全局 enable 开关 | 不新增 | 未携带 `clientTools` 时自然不启用 |
| 工具白名单 | 当前不新增 | 业务授权策略属于后续治理扩展；当前只做结构和名称冲突校验 |
| 工具超时 | 不新增 | 工具在 client 执行，当前 FEAT 不承诺服务端工具超时策略 |
| 客户端 endpoint | 不新增 | runtime 不反向访问 client |

### 5.2 部署与状态

- `ClientToolRail` 与 `JiuwenCoreAgentExtHandler` 位于同一宿主 JVM。
- request metadata 由 Handler 解析为请求级 rail 的不可变字段，不写入 Core Session 或 Agent 全局状态。
- rail 只在当前 `query/streamQuery` 调用期间注册；正常返回、中断和异常路径都在 `finally` 中注销。对于本期支持的 DeepAgent 配置，默认 completion policy 在无已排队 follow-up 时结束 task-loop 并发送 `END_FRAME`，随后 Handler 才注销 rail。
- pending `_interrupt` 随 A2A Task status message 进入当前 TaskStore；启用 Redis-backed TaskStore 时随 Task 一起保存。
- Core 的 interruption state 仍由现有 Agent session/checkpointer 机制维护，本特性不新增持久化介质。

---

## 6. 对外呈现 / 用户场景（Scenario View）

### 6.1 外部接口

| 端点 | JSON-RPC method | 说明 |
|---|---|---|
| `POST /a2a` | `SendMessage` | 非流式初始调用或恢复原 Task |
| `POST /a2a` | `SendStreamingMessage` | 流式初始调用或恢复原 Task |
| `POST /a2a` | `GetTask` | 根据 Task ID 查询当前等待状态 |

### 6.2 初始调用示例

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-1",
    "method": "SendMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-1",
        "contextId": "ctx-1",
        "parts": [{"text": "读取当前页面并总结"}]
      },
      "metadata": {
        "clientTools": [{
          "name": "readCurrentPage",
          "description": "读取当前页面内容",
          "inputSchema": {
            "type": "object",
            "properties": {
              "selector": {"type": "string"}
            }
          }
        }]
      }
    }
  }'
```

预期结果：Task 不是 completed，而是 `INPUT_REQUIRED`；status message metadata 的 `_interrupt` 完整包含本轮全部客户端工具调用。单调用字段位于 `_interrupt` 顶层，多调用位于 `_interrupt.items[]`，每项都包含 `toolCallId`、`toolName` 和 `context.arguments`。

### 6.3 决定调用端侧工具时的响应投影

#### 6.3.1 非流式 `SendMessage` 响应

以下省略与本特性无关的 Task history、时间戳，以及 client 不应解析但链路仍原样保留的 interaction `payload`，展示多调用等待的稳定字段：

```json
{
  "jsonrpc": "2.0",
  "id": "req-1",
  "result": {
    "task": {
      "id": "task-123",
      "contextId": "ctx-1",
      "status": {
        "state": "TASK_STATE_INPUT_REQUIRED",
        "message": {
          "role": "ROLE_AGENT",
          "metadata": {
            "_interrupt": {
              "message": "interaction batch",
              "items": [
                {
                  "type": "__interaction__",
                  "index": 0,
                  "message": "Client tool invocation required: readCurrentPage",
                  "toolCallId": "call-123",
                  "toolName": "readCurrentPage",
                  "context": {
                    "_interrupt_kind": "client_tool",
                    "arguments": {"selector": "#main"}
                  }
                },
                {
                  "type": "__interaction__",
                  "index": 1,
                  "message": "Client tool invocation required: readSelection",
                  "toolCallId": "call-456",
                  "toolName": "readSelection",
                  "context": {
                    "_interrupt_kind": "client_tool",
                    "arguments": {}
                  }
                }
              ]
            }
          }
        }
      }
    }
  }
}
```

agent-client 内部保存 `result.task.id` 到 invocation-taskRef 映射；业务应用只看到旧 invocation 的等待状态和新的 continuation invocation 入口。SDK 分别解析单 map 和多 `items[]`，构造内部 `ToolCall` 集合，再按每项 `toolName`、`arguments` 和原 invocation ToolView 快照执行本地校验，不解析 Core 原始 payload。

#### 6.3.2 流式 `SendStreamingMessage` 响应

流式调用通过一个 `TASK_STATE_INPUT_REQUIRED` status update 投影与非流式相同的 `_interrupt` 形态，随后关闭当前发送流，不再发送 `TASK_STATE_COMPLETED`：

```text
event: jsonrpc
data: {
  "jsonrpc": "2.0",
  "id": "req-1",
  "result": {
    "statusUpdate": {
      "taskId": "task-123",
      "contextId": "ctx-1",
      "status": {
        "state": "TASK_STATE_INPUT_REQUIRED",
        "message": {
          "metadata": {
            "_interrupt": {
              "message": "interaction batch",
              "items": [
                {"type":"__interaction__","index":0,"message":"Client tool invocation required: readCurrentPage","toolCallId":"call-123","toolName":"readCurrentPage","context":{"_interrupt_kind":"client_tool","arguments":{"selector":"#main"}}},
                {"type":"__interaction__","index":1,"message":"Client tool invocation required: readSelection","toolCallId":"call-456","toolName":"readSelection","context":{"_interrupt_kind":"client_tool","arguments":{}}}
              ]
            }
          }
        }
      }
    }
  }
}
```

Task 继续保存在 TaskStore 中并保持 `INPUT_REQUIRED`；`GetTask` 必须返回同一完整 items 投影。client 可以立即执行，也可以稍后创建 continuation invocation。

### 6.4 提交结果示例

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-2",
    "method": "SendMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-2",
        "taskId": "task-123",
        "contextId": "ctx-1",
        "parts": [
          {
            "text": "页面正文……",
            "metadata": {"toolCallId": "call-123"}
          },
          {
            "text": "选中文本……",
            "metadata": {"toolCallId": "call-456"}
          }
        ]
      }
    }
  }'
```

预期结果：solution 请求级适配校验提交 ID 集合与当前等待集合完全相等，构造 `runtime.remoteToolResults={call-123:..., call-456:...}`；Handler 据此生成 `InteractiveInput.userInputs` 恢复原 Task，两个 rail 回调分别生成对应 ToolMessage。若只提交 `call-123`，solution 在调用 Handler/Core 前拒绝整次 continuation，不生成任何 ToolMessage；按当前异步 AgentExecutor 契约，Task 走标准失败投影，不承诺保留原等待态重试。

### 6.5 E2E 流程

```text
Client                 A2A Runtime              ExtHandler/Rail          ReAct/DeepAgent
  │                         │                          │                        │
  │ SendMessage + tools     │                          │                        │
  │────────────────────────>│ ServeRequest.metadata    │                        │
  │                         │─────────────────────────>│ register request rail   │
  │                         │                          │───────────────────────>│
  │                         │                          │ beforeModelCall: tools  │
  │                         │                          │<───────────────────────│
  │                         │                          │ beforeToolCall A/B      │
  │                         │                          │<── tool calls ─────────│
  │                         │                          │ session guard + super   │
  │                         │<── client_tool map/items[]                    │
  │<── INPUT_REQUIRED(all) ─│                          │                        │
  │                         │                          │ finally: unregister     │
  │                         │                          │                        │
  │ 本地执行一个或多个工具    │                          │                        │
  │ continuation invocation                           │                        │
  │ SDK: SendMessage(taskId, TextParts + toolCallId)  │                        │
  │────────────────────────>│ restore + validate complete pending set          │
  │                         │─────────────────────────>│ register resume rail    │
  │                         │                          │───────────────────────>│
  │                         │                          │ beforeToolCall          │
  │                         │                          │<───────────────────────│
  │                         │                          │ session guard + super   │
  │                         │                          │ per-id ToolMessages     │
  │                         │                          │───────────────────────>│
  │                         │                          │<───────────────────────│
  │<── final / next wait ──│                          │ finally: unregister     │
```

---

## 7. 错误处理（Process View）

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---|---|---|---|
| 工具定义缺字段 | name 为空、schema 非法 | 不进入模型调用，记录校验错误 | Task failed |
| 工具名冲突 | 与已有 ToolInfo 或本次其他工具同名 | 禁止覆盖共享或服务端工具 | Task failed，返回冲突名称 |
| Task 已终态 | SDK 使用旧 taskRef 提交迟到结果 | `DefaultRequestHandler` 在 Executor 前拒绝，不恢复 `_interrupt` | 复用现有 `UnsupportedOperationError`，原 Task 状态不变 |
| resume 缺少结果 TextPart | client 只提交 taskId，没有有效文本 | solution 不构造 observation，不恢复 Core | 当前 Task 走标准失败投影 |
| 多 pending 结果集合缺项 | 提交的 toolCallId 集合小于当前等待集合 | solution 不恢复 Core，不做部分回灌 | 当前 Task 走标准失败投影 |
| 多 pending 缺 toolCallId | 无法确定 TextPart 对应调用 | solution 不恢复 Core，不广播文本 | 当前 Task 走标准失败投影 |
| toolCallId 未知 | ID 不属于当前单 map / 多 `items[]` 解析出的 pending 集合 | solution 不恢复 Core | 当前 Task 走标准失败投影；错误消息不泄露其他 Task 信息 |
| client 拒绝 | TextPart 明确说明用户拒绝 | 原文本作为 ToolMessage | Agent 决定降级、说明或失败 |
| client 执行失败 | TextPart 明确说明执行失败 | 原文本作为 ToolMessage | Agent 决定更换工具、重试或结束 |
| client 断线 | SSE/HTTP 连接断开 | Task owner 和 status 保留在 runtime | client 可通过 `GetTask` 重新观察 |

当前 `A2AAgentExecutor#execute()` 会把 ExtHandler 抛出的常见参数/状态异常映射为现有 `emitter.fail()`。本需求只要求 solution 在调用 Handler/Core 前拒绝缺项、未知目标等不能正确关联的 client-tool 结果并沿用该失败投影；不改造 Controller、RequestHandler、Task 更新顺序或错误码。损坏的中断结构、mixed kind、带 ID/无 ID 混用等偏门异常沿用现有行为，本期不增加专用处理。

日志不得记录完整页面内容、插件返回内容或敏感 arguments；允许记录 Task ID、toolCallId、工具名和脱敏错误摘要。

---

## 8. 限制与待补

| 限制 | 影响范围 | 临时方案 |
|---|---|---|
| 只支持 JSON-RPC A2A | REST query 无法声明或提交端侧工具 | 使用 `/a2a` |
| 只支持 Agent 实例模式 | `JiuwenCoreAgentExtHandler` 不接受 agent-id string | 宿主显式提供 ReActAgent 或 DeepAgent 实例 |
| DeepAgent 必须启用 task-loop | 当前 `isTaskLoopEnabled=false` 时，非流式 `invoke` 不执行内部 ReActAgent，无法与流式入口形成一致的工具调用闭环 | 宿主构造 DeepAgent 时显式 `.enableTaskLoop(true)`；关闭 task-loop 的 DeepAgent 不在本期验收范围 |
| DeepAgent Session 关联依赖数字后缀规则 | solution 需要按 `conversationId_[0-9]+` 识别内部 round | 本期只匹配当前代码的完整派生 ID，不支持其他命名形态 |
| DeepAgent task-loop 不保证所有 completion policy 在 interrupt 后立即退出 | 显式 completion policy 未命中或 interrupt 时存在已排队 follow-up，Core 可能继续 round | 本期只支持默认 completion policy 且 interrupt 时无已排队 follow-up；其他配置不验收 |
| 偏门异常组合 | mixed interrupt kind、损坏的 `_interrupt`、损坏 JSON 参数或带 ID/无 ID TextPart 混用不属于正常端侧工具闭环 | 沿用当前 Core/Runtime 错误行为；本期不增加额外适配层、修复状态机或专用错误码 |
| 同一 Session 不支持并行 Agent 执行 | Core interruption state 和恢复点以 Session 为边界，并行执行会竞争同一上下文 | runtime 对同一 A2A context 顺序推进；不同 Session 可并发 |
| 不提供客户端工具执行超时 | client 长时间不提交时 Task 保持等待 | 执行超时和审批超时由 agent-client 治理；本设计不增加服务端等待超时机制 |
| 不提供业务授权策略 | runtime 只做结构和关联校验 | 由 client、Gateway 或后续治理 rail 承担 |
| 不适合在 TextPart 内联大对象或二进制内容 | 大结果会扩大消息和模型上下文 | 由 client 返回受治理对象引用和小型摘要文本 |

---

## 9. L0 / L1 架构一致性审视

### 9.1 L0

| L0 约束 | 本设计结论 | 是否冲突 |
|---|---|---|
| 客户端本地工具必须显式声明执行宿主和数据边界 | `clientTools` 只进入当前请求的模型工具列表，真实执行只在 client | 否 |
| runtime 是服务端 Task owner | `INPUT_REQUIRED`、GetTask 和 resume 均由 runtime 管理 | 否 |
| Core 不应因业务定制被修改 | client-tool 专用逻辑留在 solution；本期不修改 Core 生产代码，以支持边界规避 DeepAgent 非默认 completion policy，并以测试固化既有多中断、定向恢复和身份字段契约 | 否 |
| 长等待采用挂起而非占用 | 端侧工具等待表达为 Task 状态，SSE 可关闭后查询恢复 | 否 |
| 控制、数据、流机制分离 | 工具声明走 metadata，结果走正常 TextPart；大结果只传对象引用和摘要 | 否 |

### 9.2 L1 agent-runtime

L1 已定义 `ServeRequest.metadata`、interrupt chunk、`INPUT_REQUIRED`、`SendMessage` resume 和 `GetTask`。本设计是这些既有契约上的 L2 细化，不改变 handler SPI、Task owner、部署资源或 A2A northbound 边界。

### 9.3 L1 agent-core

L1 已定义 core 负责工具调用意图、组件内部恢复点和结果消费，runtime 负责 Task 级恢复入口。本设计复用现有 rail、`ToolCallInterruptRequest`、多中断状态和 `InteractiveInput`，不让 core 依赖 A2A 或 TaskStore，符合依赖方向；本期不修改 Core 生产代码。

### 9.4 L1 agent-client

L1 已定义 client 不暴露 server-to-client webhook，而是从 Task 状态或服务流识别待处理意图，并主动发起下一轮请求。本设计在业务 facade 层创建新的 continuation invocation，并由 SDK 内部映射为 `INPUT_REQUIRED` 原 Task 的 `SendMessage(taskId)`，与该方向一致。

### 9.5 审视结论

未发现需要修改 L0 或 L1 的职责冲突。本次主改 Feat-Func-009 L2，并仅最小同步 FEAT-007 的 ToolView/结果 wire 边界和 FEAT-009 的取消级别、continuation 分层；不修改 L0/L1。

---

## 10. 实施改造与验证

### 10.1 agent-runtime-java 改造

| 文件 | 修改 |
|---|---|
| `A2aJsonRpcController` / `A2AProtocolAdapter` | **不修改生产逻辑**；保留现有 `runtime.remoteToolInputs` 提取、保留 key 清理、同一 `toolCallId` 多 TextPart 按序拼接及带 ID/无 ID TextPart 混用拒绝行为 |
| `JiuwenCoreAgentHandler` | **不修改**；保留单 interaction map / 多 `items[]` 输出，并沿用现有 `runtime.remoteToolResults -> InteractiveInput.userInputs` 输入构造 |
| `JiuwenCoreAgentHandlerTest` | **不修改既有形态断言**；`singleInterruptKeepsLegacyMapShape` 和多 interaction 用例继续作为 wire 基线证据 |
| `A2AEnabledServeOrchestrator` | **最小修改**；流式 `tryResumePending()` 和非流式 `syncResumePending()` 在调用 coordinator 前读取 Executor 已恢复的 `_interrupt`。仅当单 map / 多 `items[]` 已确认全部为 `client_tool` 时跳过远端批次并直接进入 Handler；其他形态继续现有 coordinator 探测。不解析 observation 文本 |
| `RemoteInvocationBatchCoordinator` | **不修改**；继续把带定向输入但没有远端 batch shadow 视为 `REMOTE_BATCH_PARENT_MISMATCH`，不加入 client-tool kind 分支，不创建或恢复 client-tool 批次 |
| `A2AAgentExecutor.java` | **不修改生产逻辑**；沿用 `INPUT_REQUIRED` Task 的 `_interrupt` 保存/恢复和流式持久化后关队列行为，不新增 client-tool 校验分支 |
| Runtime 测试 | 复用 Adapter 的定向 TextPart、Handler 的单 map / 多 `items[]`、Executor 的 `_interrupt` 保存/恢复测试作为基线证据；新增流式/非流式 client-tool 定向 continuation 绕过 remote resume 的测试，并证明全 `a2a_delegate`、缺失 `_interrupt` 的远端恢复及 `REMOTE_BATCH_PARENT_MISMATCH` 防护保持原状 |

除 `A2AEnabledServeOrchestrator` 的 resume 分流外，不修改 `ServeRequest`、`QueryRequest`、REST controllers、`A2aJsonRpcController`、`A2AProtocolAdapter`、`A2AAgentExecutor`、`JiuwenCoreAgentHandler` 或 `RemoteInvocationBatchCoordinator`，也不新增 Runtime 公共 DTO或内部 metadata key。不得复制远程批次 coordinator，也不增加 client-tool 专用 endpoint、Task 状态或请求边界异常映射。

### 10.2 agent-solution 改造

| 文件/类 | 修改 |
|---|---|
| `JiuwenCoreAgentExtHandler` | 保留 `installBeforeRun()` 的 Remote A2A/SkillHub 行为；`query/streamQuery` 前调用 `ClientToolRail.bind(agent, request)`，`finally` 通过 try-with-resources 关闭绑定；不持有 Support 实例、不包装输出、不改写单/多 wire 形态，不覆盖 `runnerSession()` |
| `ClientToolRail` | **唯一新增生产类**。静态 `bind()` 承载 metadata/pending 解析、带 ID 结果集合校验与 Map 转换、ReActAgent/DeepAgent 目标定位、名称冲突检查和请求级注册/注销；嵌套 `RequestContext` 与 `Binding` 不拆文件。rail 本身继承 `BaseInterruptRail` 并设置 priority 70，实现 `beforeModelCall`、Session 过滤和调用身份门控，再复用基类读取 resume input 和回灌 ToolMessage；不保存批次可变状态 |
| `AgentCoreExtAutoConfiguration` | **不修改**；`ClientToolRail` 无外部依赖，由 ExtHandler 静态调用，不新增 Bean |

`RemoteA2aToolInstaller` 和 `RemoteA2aInterruptRail` 不修改，只增加共存测试。

### 10.3 单元测试

| 测试项 | 验证点 |
|---|---|
| metadata 解析 | `clientTools` 映射正确；pending 单 map / 多 `items[]` 均能读取 `toolCallId/toolName` |
| 多结果校验 | 多 pending 的结果 ID 集合与等待集合一致，缺项或未知目标在恢复 Core 前整体拒绝；单 pending 可省略 ID |
| 请求级绑定 | 普通 ReActAgent 和 DeepAgent 内部 ReActAgent 执行前注册、完成后精确注销 |
| 异常清理 | 正常、interrupt 和 exception 三条 Handler 路径均调用 binding.close()；另验证本期支持的 DeepAgent 默认 completion policy 在无已排队 follow-up 时先产生 `END_FRAME` 再注销 |
| rail 无全局工具 | `AbilityManager.listToolInfo()` 不出现动态 client tool |
| beforeModelCall | 只追加本次 ToolInfo，下一 invocation 不残留 |
| 既有 rail 共存 | Remote A2A、SkillHub、AgentMode/ProgressiveTool 等既有 rail 启用时，priority 70 使 client tool 在当前工具过滤后注入，不丢失既有工具、不绕过名称冲突且不改变既有 rail 注册内容 |
| 名称冲突 | 本次新声明的 visible tool 不覆盖服务端工具或 Remote A2A 工具；pending-only 名称不重新暴露，并仍按原 Session + toolCallId 完成恢复 |
| beforeToolCall 范围过滤 | ReActAgent 只精确匹配 conversationId；DeepAgent 只匹配 `Pattern.quote(conversationId) + "_[0-9]+"`，不接受原始 ID；其他 Session 直接返回 |
| 首次调用 | 基类命中工具名后，`resolveInterrupt` 产生 client_tool interrupt，包含 ID、名称、arguments |
| schema 不匹配参数 | 合法 JSON object 即使缺少必填字段或字段类型不匹配，仍能形成 client-tool interrupt，并由 client 生成 `invalid_tool_arguments` outcome |
| 同轮多调用 | A/B/C 每个 ToolCall 独立产生中断，rail 不共享或覆盖临时状态，Handler 输出完整 `items[]` |
| 单/多中断适配 | Handler 保持单 interaction map / 多 interaction `items[]`；Runtime 原样投影，client 与 solution 内部解析为 pending 列表，顺序和每项字段不丢失 |
| resume 结果 | 全部 pending `toolCallId -> toolName` 可重建恢复资格；门控命中后，基类按 toolCallId 把成功、拒绝和错误文本设置到正确 ToolMessage |
| resume 无 `clientTools` | binding/rail 仍注册；`visibleTools` 为空且 `pendingCallsById` 包含全部等待调用；不重新暴露旧工具，但可完成原 pending 回灌 |
| resume 后同名新调用 | 未重传 `clientTools` 时，完成 pending 回灌后出现同名但新 `toolCallId` 的 ToolCall 不被 client rail 拦截；明确重传该工具时才允许产生新一轮 client-tool 中断 |
| 不完整 resume | A/B/C pending 时只提交 A，solution 在调用 Handler/Core 前拒绝，且不生成任何 ToolMessage |
| 并发隔离 | 同一 Agent 上不同 Session A/B 不互相看见或拦截工具，覆盖同名不同 schema、普通字符串前缀以及 `ctx`/`ctx_1`；DeepAgent 仅派生匹配，不得因另一个请求的原始 ID 误命中 |
| DeepAgent 覆盖 | 显式 `enableTaskLoop(true)`、默认 completion policy、interrupt 时无已排队 follow-up；rail 注册到 `DeepAgent#getAgent()`，探针断言内部 callback 使用派生 Session ID，严格数字后缀 guard 能命中；其他配置明确不在验收矩阵 |

### 10.4 Runtime 集成测试

| 测试项 | 验证点 |
|---|---|
| 非流式 SendMessage | 单调用返回 `_interrupt` map；同轮多个工具调用返回完整 `_interrupt.items[]`；两者都进入 `INPUT_REQUIRED` |
| SendStreamingMessage | SSE 投影单 interaction map 或多 interaction `items[]`，随后流正常关闭且 Task 可查询 |
| GetTask | 断线后仍能读取全部 pending toolCallId/name/arguments |
| 多目标 resume | 一条 Message 的多个非空 TextPart 按非空 metadata.toolCallId 生成对应 ToolMessage |
| client-tool resume 分流 | 流式 `tryResumePending()` 与非流式 `syncResumePending()` 遇到 Task 恢复的单/多 client-tool `_interrupt` 时均不调用 remote resume，定向输入能到达 ExtHandler |
| remote resume 回归 | 只有全量 `client_tool` `_interrupt` 旁路 coordinator；缺失 `_interrupt` 和全 `a2a_delegate` 保持既有探测，其中没有可信 `_interrupt` 却提交定向输入仍返回 `REMOTE_BATCH_PARENT_MISMATCH` |
| 既有输入路径复用 | Runtime 根据已恢复的全量 `client_tool` `_interrupt` 跳过 remote resume 后，client-tool 结果由 solution 从 `runtime.remoteToolInputs` 校验转换为 `runtime.remoteToolResults`，再经 Handler 既有逻辑进入 `InteractiveInput.userInputs`；不产生 `_remote_batch` 或 `runtime.remoteBatchId` |
| 不完整集合 | 只提交 A、遗漏 B/C 时 solution 在 Core 前拒绝，Task 按当前异步执行契约进入标准失败投影 |
| 单目标恢复 | 只有一个 pending 时，不带 toolCallId 的普通 TextPart 经 Core 现有字符串恢复能力正常回灌；显式带正确 ID 时也可恢复 |
| 目标校验 | 多 pending 的结果目标集合必须与等待集合一致；非法组合整体拒绝，不做部分恢复或顺序猜测 |
| resume 拒绝/错误 | 明确的拒绝/错误文本作为 observation 进入 Agent |
| 多轮调用 | 同一 Task 可经历多次 client-tool 中断与恢复 |
| 标准入口回归 | 复用 FEAT-001/FEAT-006 已有 continuation 与终态拒绝语义，不新增 client-tool 幂等表或入口逻辑 |
| ReAct/DeepAgent | ReActAgent 与本期受支持配置的 DeepAgent 走同一 A2A 契约；DeepAgent 仅覆盖显式 `enableTaskLoop(true)`、默认 completion policy 且无已排队 follow-up |
| Remote A2A 共存 | `a2a_delegate` rail 与 `client_tool` rail 不互相拦截 |
| REST 回归 | REST query 行为和请求模型不发生变化 |

### 10.5 验收标准

1. client 能通过 JSON-RPC A2A 声明本次端侧工具。
2. ReActAgent 和本期受支持配置的 DeepAgent 都只能看到当前 invocation 的工具；DeepAgent 必须显式 `enableTaskLoop(true)`、使用默认 completion policy、在 interrupt 时无已排队 follow-up，且内部派生 Session 通过严格数字后缀 guard 命中。
3. 同轮一个或多个端侧工具调用使原 Task 进入可查询的 `INPUT_REQUIRED`；单调用 `_interrupt` map 和多调用 `_interrupt.items[]` 都完整保留每项参数、名称和 `toolCallId`。
4. 多 pending 时 client 在一次 continuation 中提交与等待集合完整对应、带非空 `TextPart.metadata.toolCallId` 的非空最终结果；单 pending 时允许省略 ID，但 observation 仍必须非空。
5. 带/不带 ID 混用由 Adapter 在执行前拒绝；不完整、空 observation 或含未知目标的结果集合由 solution 在恢复 Core 前整体拒绝。同一目标的多个非空 TextPart 按序拼接为一个 observation，只有完整合法的集合才能继续 Agent。协议层拒绝复用标准 A2A/JSON-RPC 错误，进入 AgentExecutor 后的语义拒绝按标准失败投影结束该次非法 continuation；两者都不做部分恢复。
6. 非等待状态、未知/已解决 ID 均不能错误恢复 Task；标准入口的重复 continuation 语义继续复用且不重复推进模型。
7. 不同 Session ID 的并发 Task 共用同一 Agent 实例时，工具目录和结果关联不串扰；DeepAgent 只接受派生 Session 全串匹配。
8. 正常、interrupt 和 exception 后，Handler 请求级 rail 均已注销；本期支持的 DeepAgent interrupt 路径必须先结束 Iterator 再注销。运行期间和结束后 `AbilityManager` 均不包含动态端侧 ToolCard。
9. 不新增 REST 入口、私有结果 endpoint、Runtime 公共 DTO、内部 metadata key、client-tool coordinator、幂等表或 `_client_tool_batch`；client-tool 只复用现有 `remoteToolInputs/remoteToolResults` 的 Map 载体，不借用远程 A2A 批次、远程 Task 或 `_remote_batch` 语义。
10. 本期不交付 Task cancel；但任何已处于终态的 Task 均不得被 client-tool continuation 恢复。

---

## 11. Core / Runtime 复用边界

本需求复用 Core 和 Runtime 的大部分既有能力。Core 已提供 ToolInfo 动态注入点、`ToolCallInterruptRequest.fromToolCall()`、同轮多调用中断保存和按 `toolCallId` 恢复，因此不修改 Core 生产代码；Runtime 已提供 metadata/TextPart 适配、Handler 单 map / 多 `items[]` 投影、`INPUT_REQUIRED` 保存与恢复，只需在 `A2AEnabledServeOrchestrator` 增加 Handler 前的最小 resume 分流。client-tool 专用解析、校验、Map 转换和请求级 rail 生命周期落在 agent-solution。

### 11.1 问题与责任边界

| 问题 | 当前事实 | 根因归属 | 本需求处理 |
|---|---|---|---|
| DeepAgent callback Session 与 A2A conversationId 不同 | DeepAgent 使用 `conversationId + "_" + requestSeq` 创建 request-level Session，task-loop 和内部 ReAct round 均使用派生 ID | solution 若按原始 ID 精确比较将无法命中 | DeepAgent 模式只使用严格数字后缀全串匹配，不同时接受原始 ID |
| DeepAgent stream 使用后台线程 | Handler 消费的是阻塞 Iterator，`postRun()` 最终通过 `END_FRAME` 结束消费 | 线程切换本身不是 Core、Runtime 或 solution bug | 保留 Handler `try/finally` 生命周期 |
| task-loop interrupt 后可能继续 round | 默认 completion policy 且无已排队 follow-up 时现有 loop 会在该 round 后结束；显式 completion policy 未命中或存在 follow-up 时可能继续 | Core 通用控制流边界，不应由 Runtime/Solution 模拟修复 | 本期仅支持前一种 DeepAgent 配置；其余场景不验收，不修改 Core |
| Handler 单/多中断 wire 形态不同 | 单 interaction 返回顶层 map，多 interaction 才返回 `items[]` | Runtime 当前输出契约 | Handler 保持现状；Solution 和 client 分别解析两种形态后建立内部 pending 列表 |
| client-tool 定向结果被远端 resume 前置拦截 | Adapter 已产出 `runtime.remoteToolInputs`，但 `A2AEnabledServeOrchestrator` 在 Handler 前先调用 coordinator；没有 remote batch shadow 时返回 `REMOTE_BATCH_PARENT_MISMATCH` | 多 pending 和显式带 ID 的单 pending 无法到达 solution | **本需求同批必改**；Runtime 只对 Task 恢复出的全量 `client_tool` `_interrupt` 跳过 remote resume 探测，不修改其他 kind 或 coordinator |
| client-tool 定向结果缺少转换 | Handler 只消费 `runtime.remoteToolResults`，client-tool 结果到达 ExtHandler 时仍是 `runtime.remoteToolInputs` | 缺少在 client-tool resume 点校验 pending 集合并完成两个既有 Map 之间的转换 | **本需求同批必改**；由 solution 请求级适配校验并转换，不新增 key、不改 Handler |

### 11.2 DeepAgent 支持边界

本期只支持显式 `enableTaskLoop(true)`、默认 completion policy 且 interrupt 时没有已排队 follow-up 的 DeepAgent。该配置下验证 invoke / stream 均保留完整 interruption state，stream 收到 `END_FRAME` 后才注销 rail。关闭 task-loop、显式 completion policy和已有 follow-up 的配置不支持、不验收，Solution 不增加反射探测或 task-loop 控制逻辑。

### 11.3 既有身份字段契约

#### 11.3.1 原因

continuation 未重新携带 `clientTools` 时，solution 依赖 Task 中保存的单 `_interrupt.toolName/toolCallId` 或多 `_interrupt.items[].toolName/toolCallId` 重建 `pendingCallsById`，并按该映射定向结果和限制恢复资格。字段由 Core 的 `ToolCallInterruptRequest.fromToolCall()` 从原始 ToolCall 填充，再由 Runtime adapter 提升到 interaction 顶层并随 Task 持久化。如果任一层截断多项集合、遗漏字段或只保留原始 `payload`，将产生已经进入 `INPUT_REQUIRED` 却无法完整恢复的 Task。

#### 11.3.2 建议契约

- Core：本需求的 `ClientToolRail` 使用既有 `ToolCallInterruptRequest.fromToolCall()` 生成非空 `toolCallId`、`toolName`，不新增 Core 类型或字段。
- Runtime Adapter：沿用现有 `JiuwenCoreAgentHandler#toInterruptData()` 对每个 `ToolCallInterruptRequest` 提升 `toolCallId` 和 `toolName`；单调用位于 `_interrupt` 顶层，多调用位于 `_interrupt.items[]`，不得要求上层解析 `payload.value`，也不得只保留最后一项。
- A2A Task：单 map / 多 `items[]` 必须原样保留每个 client-tool interaction 的 `toolCallId/toolName`；通过 Handler、Executor、TaskStore 的跨仓契约测试保证，不新增 Runtime 专用字段校验分支。
- Resume：`toolName` 与 `toolCallId` 共同重建 pending 调用身份；多 pending 时 client 必须在 TextPart metadata 回传非空 `toolCallId`，单 pending 可省略并由 Core 现有字符串恢复能力定向到唯一 item。历史 `toolName` 不能单独授权拦截新的同名 ToolCall。

#### 11.3.3 契约测试

```text
ToolCall
  -> ToolCallInterruptRequest.fromToolCall()
  -> InteractionOutput / OutputSchema
  -> JiuwenCoreAgentHandler.toInterruptData()
  -> Task status message metadata._interrupt（单 map / 多 items[]）
  -> copyStoredInterrupt()
  -> ClientToolRail.bind() pendingCallsById[toolCallId] = toolName
  -> TextPart.metadata.toolCallId
  -> InteractiveInput.userInputs[toolCallId]
```

优先复用 Core/Runtime 既有单元测试作为字段和 wire 基线，由 Solution/端到端测试补齐 client-tool 的跨层串联证据；只对真实契约缺口增加回归测试。测试断言单 interaction 和多 `items[]` 中所有 interaction 的 `toolCallId/toolName` 不丢失，并覆盖多目标定向和单目标省略 ID。

### 11.4 推荐实施顺序

```text
1. agent-solution：适配单 map / 多 items[]，复用 remoteToolInputs/remoteToolResults，完成 client-tool pending 集合校验和定向转换
2. agent-solution：完成请求级 ClientToolRail、ToolInfo 注入、调用移交和 ReAct/DeepAgent 定位
3. 复用 Core / Runtime 既有测试固定 toolCallId/toolName、单 map / 多 items[] 和 INPUT_REQUIRED 保存/恢复事实，仅对证据缺口补最小回归
4. 三仓集成测试：ReActAgent + 本期受支持配置的 DeepAgent，sync + stream，single/multi interrupt + 完整结果恢复/缺项拒绝
```

第 1、2、4 步是本需求新增实现与验收主体，均以 agent-solution 为主要落点；第 3 步以复用当前 Core/Runtime 能力和测试证据为先。Core 生产代码保持不变，Runtime 仅实施 `A2AEnabledServeOrchestrator` 的最小 resume 分流；DeepAgent 超出本期配置边界的通用 interrupt 退出另行演进。
