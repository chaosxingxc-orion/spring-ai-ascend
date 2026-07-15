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
---

# 【调用端侧工具响应】新增支持带有端侧工具的请求 — 设计文档

> 需求名称：【调用端侧工具响应】新增支持带有端侧工具的请求
> 需求说明：需要调用客户端侧工具的中断，通过智能体服务调用响应返回给客户端
> 关联需求：`FEAT-2026-010`（agent-core 端侧工具动态注册与调用）
> 目标实现：`agent-runtime-java`、`agent-solution/common/agent-runtime-ext-java`
> 最后更新：2026-07-15
> 关键范围：当前版本只支持 JSON-RPC A2A 的 `SendMessage`、`SendStreamingMessage` 与 `GetTask`；REST query 入口不在范围内。

---

## 1. 概述

### 1.1 特性定位

本特性使服务端 Agent 在一次 A2A Task 执行中临时看见客户端声明的本地工具，并在模型选择该工具时把调用转换为 `INPUT_REQUIRED`，等待 client 执行后主动提交结果，再恢复原 Task。

本特性解决以下问题：客户端页面能力、终端插件和本地审批动作不能由服务端直接访问，也不应注册为平台全局工具。改造后，client 通过 A2A `params.metadata` 声明本次可用工具，runtime 维护 Task 状态，AgentCore 扩展 rail 只向本次模型调用注入工具描述；真实执行始终发生在 client。

适用场景包括读取当前页面、调用终端插件、触发本地 UI 动作和请求用户确认。不适用于服务端本地工具、MCP 工具、Skill Hub 工具、远程 A2A Agent 委派或 REST query 调用。

### 1.2 当前代码基线

当前三个代码仓已经具备以下可复用事实：

- `agent-runtime-java` 的 `A2aJsonRpcController` 已解析 `params.metadata`，`A2AProtocolAdapter` 已将其保存在 `ServeRequest.metadata`。
- `A2AAgentExecutor` 已能把 interrupt chunk 投影为 `INPUT_REQUIRED`；`_interrupt` 在 Task status message 中的保存和 resume 恢复已在 `agent-runtime-java` 本地工作区实现，尚未提交或合入远端分支。
- `agent-core-java` 的 ReActAgent 已支持 `beforeModelCall`、`beforeToolCall` rail、`ToolInterruptException`、字符串 resume 自动关联和 `_skip_tool` 结果回灌。
- DeepAgent 内部持有 ReActAgent，`DeepAgent#getAgent()` 可定位实际执行模型和工具调用的内部 ReActAgent；启用 task-loop 时，DeepAgent 会把外层 `conversationId` 派生为 `conversationId + "_" + requestSeq` 的 request-level Session ID，内部 round 使用该派生 ID，而不是原始 A2A `conversationId`。
- `agent-core-java` 的 `BaseAgent` 已公开 `registerRail()` / `unregisterRail()`；`JiuwenCoreAgentHandler#query()` 和 `streamQuery()` 都在当前调用栈内同步完成 Agent 执行，适合由扩展 Handler 在 `try/finally` 中管理请求级 rail 生命周期。
- `agent-solution` 的 `RemoteA2aToolInstaller` 和 `RemoteA2aInterruptRail` 已提供“识别普通 ReActAgent/DeepAgent、复用 `BaseInterruptRail` 中断恢复”的参考模式。

### 1.3 核心设计原则

1. **A2A Task 是唯一服务端状态 owner** — 端侧工具等待通过 `INPUT_REQUIRED` 表达，不新增私有结果提交端点或第二套 Task 状态机。
2. **端侧工具是当前执行级上下文** — 工具定义只进入本次 `ModelCallInputs.tools`，不写入共享 `AbilityManager`、ResourceMgr、MCP 或 Skill Hub。
3. **solution 扩展承载 AgentCore 适配** — `JiuwenCoreAgentExtHandler` 从 `ServeRequest.metadata` 构造请求级 `ClientToolRail`，无须修改 `JiuwenCoreAgentHandler`、Core Session/input 或 `agent-core-java`。
4. **rail 按请求注册和注销** — 每次 `query/streamQuery` 创建一个携带不可变工具快照的 rail，执行前注册，`finally` 中注销；普通 ReActAgent 使用原始 `conversationId` 精确过滤，DeepAgent 首版同时识别原始 ID 和严格的数字派生后缀 ID。
5. **结果是普通工具 observation** — client 通过 resume Message 的 TextPart 返回结果文本，ReActAgent 将其关联到已保存的 ToolCall，由 Agent 决定继续、降级、完成或失败。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|---|---|---|---|
| A2A 能力视图接收 | 从 `params.metadata.clientTools` 接收本次工具目录 | `A2aJsonRpcController`, `ServeRequest.metadata` | ⚠️ 基础透传已存在，端侧工具校验待实现 |
| 请求级 rail 绑定 | 从 metadata 创建 rail，执行前注册、结束后注销 | `JiuwenCoreAgentExtHandler`, `ClientToolBinding` | ⬜ 计划中 |
| 动态工具可见性 | 只向本次 `ModelCallInputs.tools` 添加 ToolInfo | `ClientToolRail#beforeModelCall` | ⬜ 计划中 |
| 调用移交 | Session 过滤后复用基类，将端侧工具调用转换为 Core interrupt | `ClientToolRail#beforeToolCall`, `BaseInterruptRail` | ⬜ 计划中 |
| Task 等待投影 | interrupt 转换为 A2A `INPUT_REQUIRED` | `A2AAgentExecutor`, `_interrupt` | ⚠️ 通用能力已存在；元数据保真改动已在 runtime 本地工作区实现，尚未提交或合入 |
| 客户端结果回灌 | resume TextPart 由基类 rail 转成原 ToolCall observation | `BaseInterruptRail`, `ClientToolRail#resolveInterrupt` | ⬜ 计划中 |
| ReActAgent / DeepAgent 覆盖 | 每次请求将 rail 绑定到实际执行的 ReActAgent | `ClientToolInstaller` | ⚠️ ReActAgent 计划中；DeepAgent 首版使用派生 Session 规则适配，完整能力依赖第 11 章补强 |

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|---|---|---|
| JSON-RPC A2A 初始调用 | ⬜ | `SendMessage` 和 `SendStreamingMessage` 可通过 `params.metadata` 提供端侧工具能力视图。 |
| Task 级工具隔离 | ⬜ | 并发 Task 使用同一 Agent 实例时，只能看到本次 invocation 的工具。 |
| 模型工具注入 | ⬜ | 客户端工具以 `ToolInfo` 形式进入本次模型调用，不进入共享注册表。 |
| 端侧工具调用中断 | ⬜ | 模型调用端侧工具后，core 产生包含名称、参数、`toolCallId` 的移交意图。 |
| 非流式等待响应 | ⚠️ | 通用 interrupt 已支持；需补齐 client-tool 元数据和 resume 保真。 |
| SSE 等待事件 | ⚠️ | 通用 interrupt SSE 已支持；需验证 `_interrupt` 在队列关闭前已持久化并投递。 |
| GetTask 重新观察 | ⚠️ | TaskStore 已支持；需保证 status message metadata 保存完整 `_interrupt`。 |
| 成功结果回灌 | ⬜ | client 返回结果文本，rail 将其作为 ToolMessage 后继续 ReAct loop。 |
| 拒绝与错误回灌 | ⬜ | client 返回明确的拒绝或错误文本，由 Agent 按 observation 处理。 |
| ReActAgent 支持 | ⬜ | `ClientToolRail` 安装到 ReActAgent。 |
| DeepAgent 支持 | ⚠️ | 请求级 rail 安装到 `DeepAgent#getAgent()`，首版按 `conversationId` 或 `conversationId + "_" + 数字序号` 匹配内部 round；完整稳定关联及 task-loop interrupt 立即退出依赖第 11 章 Core/Runtime 后续补强。 |
| 同一 Task 多轮调用 | ⬜ | 每次 resume 后允许再次产生新的端侧工具请求。 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|---|---|---|
| REST `/query`、MVC/WebFlux query 入口 | 当前需求只接受 JSON-RPC A2A 入口 | 后续独立特性评审 |
| 新增客户端结果提交 endpoint | A2A `SendMessage`/`SendStreamingMessage` 已能恢复原 Task | 使用 `message.taskId` + `message.parts[].text` |
| 服务端访问 DOM、插件、文件、本地端口 | 客户端资源不在服务端信任和网络边界内 | client 本地执行后主动提交结果 |
| 全局 Tool Registry、MCP、Skill Hub 注册 | 动态端侧工具只属于当前 invocation | `beforeModelCall` 注入本次 `ModelCallInputs.tools` |
| 执行前向 `AbilityManager` 注册 ToolCard、执行后删除 | 共享 Agent 并发时会串工具，同名工具会覆盖或误删 | 只注册请求级 rail；工具只注入 `ModelCallInputs.tools` |
| Gateway / Event Bus Task owner | Task 权威状态属于 runtime | Gateway / Event Bus 只做受治理透传或投影 |
| runtime 判断业务结果真假 | runtime 只验证关联关系，不解释客户端业务事实 | Agent 消费 observation 后决定后续逻辑 |
| 平台级工具审计和授权策略 | 属于 middleware / bus / 业务治理扩展 | 本特性保留 metadata 和 trace 接缝，不新增策略系统 |

### 2.3 外部接口契约（Logical View）

#### 2.3.1 JSON-RPC 方法

| 方法 | 本特性用途 | 约束 |
|---|---|---|
| `SendMessage` | 初始调用、非流式端侧工具等待、提交结果并恢复 Task | 工具定义放在 `params.metadata.clientTools`；结果走正常 TextPart；resume 必须使用原 `message.taskId` |
| `SendStreamingMessage` | 初始流式调用、通过 SSE 观察等待状态、提交结果后继续流式执行 | 工具定义和结果契约与 `SendMessage` 相同 |
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
- resume 请求即使不重新携带 `clientTools`，runtime 也必须从原 Task 的 pending `_interrupt` 恢复待回灌工具名，使基类 rail 能命中已保存的 ToolCall；这不等价于把旧工具重新暴露给恢复后的模型调用。

#### 2.3.3 客户端结果

client 使用原 `message.taskId` 恢复 Task，并通过普通 TextPart 返回工具结果：

```json
{
  "message": {
    "role": "ROLE_USER",
    "messageId": "msg-2",
    "taskId": "task-123",
    "contextId": "ctx-1",
    "parts": [
      {"text": "页面正文……"}
    ]
  }
}
```

首版不定义 `clientToolResults`、结果状态枚举或 DataPart。不同执行结果由 client 生成明确的 observation 文本：

| 场景 | TextPart 示例 | Agent 语义 |
|---|---|---|
| 成功 | `页面正文……` | 正常工具 observation |
| 用户拒绝 | `客户端拒绝执行该工具：用户取消了操作。` | Agent 决定说明、降级或结束 |
| 执行错误 | `客户端工具执行失败：本地插件不可用。` | Agent 决定换工具、重试或结束 |

`message.taskId` 是 Task 关联的唯一权威字段。首版同一时刻只允许一个 pending 客户端工具调用，因此 client 不需要返回 `toolCallId`；runtime 和 Core 使用已保存的 pending ToolCall 自动建立关联。大结果不应无限内联到 TextPart，client 应返回受治理对象引用和必要摘要。

#### 2.3.4 Task 等待投影

runtime 复用当前 status message metadata 的 `_interrupt`，不新增平行的 `clientToolRequests` Task 字段：

```json
{
  "_interrupt": {
    "type": "__interaction__",
    "index": 0,
    "payload": {
      "id": "call-123",
      "value": {
        "message": "Client tool invocation required: readCurrentPage",
        "context": {
          "_interrupt_kind": "client_tool",
          "arguments": {
            "selector": "#main"
          }
        },
        "payloadSchema": {},
        "toolCallId": "call-123",
        "toolName": "readCurrentPage"
      }
    },
    "message": "Client tool invocation required: readCurrentPage",
    "toolCallId": "call-123",
    "toolName": "readCurrentPage",
    "context": {
      "_interrupt_kind": "client_tool",
      "arguments": {
        "selector": "#main"
      }
    }
  }
}
```

上述字段与 `JiuwenCoreAgentHandler#toInterruptData()` 当前输出一致：`index` 是 Core 流输出序号，`payload` 是原始 `InteractionOutput` 的保真投影；`message`、`context`、`toolCallId` 和 `toolName` 是从 `payload` 提升出的常用字段。client 应优先使用提升字段，不应依赖 `payload.value` 的内部 Java 对象序列化结构；`payload` 仅作为兼容、诊断和保真字段。

client 通过阻塞响应、SSE status update 或 `GetTask` 观察同一结构。`_interrupt_kind=client_tool` 用于区别人工输入和远程 A2A delegate。

#### 2.3.5 行为承诺

- **必须**：端侧工具等待时 Task 保持 `INPUT_REQUIRED`，不得标记为 `COMPLETED`。
- **必须**：resume 只接受原 Task；结果文本自动关联到该 Task 唯一的 pending 客户端 ToolCall。
- **必须**：动态工具只进入本次模型调用，不进入共享 `AbilityManager`；请求结束后临时 rail 必须注销。
- **必须**：客户端拒绝和执行错误使用明确的 observation 文本回灌 Agent。
- **禁止**：使用 TextPart 承载工具定义；工具定义只放在 `params.metadata.clientTools`。
- **禁止**：runtime 或 core 直接访问客户端资源。
- **允许**：client 在 resume 请求中重新提供当前完整 `clientTools`；未提供时，runtime 仍可恢复 pending ToolCall，但结果回灌后的下一轮模型调用不再看见上一轮客户端工具。

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
        └── external/clienttool/
            ├── ClientToolInstaller.java           # 解析目标 ReActAgent并创建请求级绑定
            ├── ClientToolBinding.java             # 持有 target + rail，close() 精确注销
            ├── ClientToolRail.java                # 模型注入、Session 过滤、复用基类中断恢复
            └── ClientToolMetadataSupport.java     # 原始 Map 校验和读取，无公共 DTO
```

### 3.3 agent-core-java

本特性不修改 `agent-core-java`。直接复用：

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
├── ToolInterruptionState
└── InteractiveInput

DeepAgent
└── getAgent() -> inner ReActAgent
```

### 3.4 核心类静态关系

```text
JiuwenCoreAgentHandler
        ▲
        │ extends
JiuwenCoreAgentExtHandler ───── installForRequest ─────▶ ClientToolInstaller
        │                                                     │
        │ try/finally                                         │ resolves target
        ▼                                                     ▼
super.query/streamQuery()                            ReActAgent / DeepAgent.getAgent()
        │                                                     │
        │                                             registerRail(ClientToolRail)
        │                                                     │
        └──── return/error/cancel ─────▶ ClientToolBinding.close()
                                                              │
                                                    unregisterRail(exact rail)

ClientToolRail extends BaseInterruptRail
├── beforeModelCall()：只向当前 Session 注入 ToolInfo
├── beforeToolCall()：Session 过滤后调用 super.beforeToolCall()
└── resolveInterrupt()：新增 client_tool 类型；首次 interrupt，resume reject
```

---

## 4. 核心设计（Logical + Process View）

### 4.1 请求级 rail 生命周期

#### 4.1.1 方案选择

| 方案 | 优点 | 问题 | 结论 |
|---|---|---|---|
| 修改 Core，为 `BaseInterruptRail` 增加动态匹配扩展点 | 可安装单例 rail，动态读取当前上下文 | 当前基类方法已经允许子类覆盖并调用 `super`，新增 Core API 没有必要 | 不采用 |
| Agent 上永久安装一个无状态 rail，通过 Session env 读取工具 | 不需要反复注册 callback | 必须覆盖 `runnerSession()`、改变 Session 构造并向 DeepAgent 传播请求数据 | 不采用 |
| 每次请求创建 rail，Session 过滤后调用 `super.beforeToolCall()` | Core 零修改；不改 input/Session；完整复用基类中断恢复 | 需要在 Handler 中严格管理注册和注销 | **采用** |

采用方案只扩展 solution。`beforeToolCall` 的存在是为了接入已有 rail 生命周期和增加 Session 隔离，不承载新的中断/恢复算法；真正的匹配、pending ToolCall 恢复、`_skip_tool` 和 ToolMessage 回灌仍由 `BaseInterruptRail` 完成。

其中 Session guard 是 `ClientToolRail` 为请求级并发隔离新增的适配逻辑，并非复用 `RemoteA2aInterruptRail`；复用范围仅包括 `RemoteA2aToolInstaller` 的目标 Agent 解析模式，以及 guard 命中后 `BaseInterruptRail` 的既有中断恢复流程。

#### 4.1.2 Handler 生命周期

`JiuwenCoreAgentExtHandler` 不覆盖 `runnerSession()`，不改 Core input，也不把工具定义写入 Session env。它只在调用父类前从 `ServeRequest.metadata` 创建请求级绑定：

```java
@Override
public void streamQuery(ServeRequest request, QueryStreamObserver observer) {
    ClientToolBinding binding = clientToolInstaller.installForRequest(getAgent(), request);
    try {
        super.streamQuery(request, observer);
    } finally {
        binding.close();
    }
}

@Override
public QueryResponse query(ServeRequest request) {
    ClientToolBinding binding = clientToolInstaller.installForRequest(getAgent(), request);
    try {
        return super.query(request);
    } finally {
        binding.close();
    }
}
```

该生命周期成立的代码前提如下：

- `BaseAgent` 已公开 `registerRail()` 和 `unregisterRail()`。
- `JiuwenCoreAgentHandler#query()` 同步返回最终结果或中断结果。
- `JiuwenCoreAgentHandler#streamQuery()` 在当前调用栈内同步消费 Iterator；DeepAgent 即使在 `deep-agent-stream-*` 后台线程生产数据，Handler 仍阻塞到 `postRun()` 写入 `END_FRAME`、发生取消或抛出异常后才返回。
- `ClientToolBinding#close()` 只注销当前 rail 实例；`getTools()` 为空，因此不会增删 `AbilityManager` 中的 ToolCard。

无 `clientTools` 且无 pending `client_tool` 中断时，`installForRequest()` 返回 no-op binding。

#### 4.1.3 DeepAgent task-loop 中断边界

DeepAgent 的异步生产本身不会使 `finally` 失效。正常中断退出链路为：

```text
runTaskLoop 返回
  -> DeepAgent stream thread finally
  -> effectiveSession.postRun()
  -> StreamEmitter 写入 END_FRAME
  -> JiuwenCoreAgentHandler 结束 Iterator 消费
  -> JiuwenCoreAgentExtHandler finally
  -> ClientToolBinding.close()
```

当前 Core 的实际缺口不在线程切换，而在 `DeepAgent#runTaskLoop()` 没有把 `roundResult.result_type=interrupt` 作为无条件退出当前 invocation 的控制信号：

- 使用 HarnessFactory 自动注入的无 completion promise 默认 `TaskCompletionRail`，且没有已排队 follow-up 时，interrupt round 之后会标记本次 loop 完成并退出，以上生命周期成立。
- 配置显式 completion promise，或 interrupt 时已经存在 follow-up 时，task-loop 可能继续下一 round，直到 completion promise、最大轮数或超时条件命中；client-tool interrupt 会延迟投影，且可能在 client 尚未提交结果时发生额外模型执行。
- observer 主动取消会使 Handler 停止消费并注销 rail，但当前 DeepAgent 后台执行没有与该取消建立完整的协同终止契约；因此“Handler 已清理”不等价于“Core 后台执行已停止”。

首版不在 Runtime 或 ExtHandler 中强行截断 Iterator。提前返回会导致 DeepAgent 后台线程仍在运行而请求级 rail 已注销，风险高于等待 Core 正常结束。通用修复应放在 Core，Runtime 在 Core 提供终止保证后再增加防御性停止消费和观测，详见第 11 章。

### 4.2 目标 Agent 与请求范围

`ClientToolInstaller` 复用 `RemoteA2aToolInstaller` 的目标解析模式，同时保留目标类型，供 Session guard 选择匹配规则：

```java
ClientToolTarget resolve(Object agent) {
    if (agent instanceof BaseAgent baseAgent) {
        return new ClientToolTarget(baseAgent, SessionMatchMode.EXACT);
    }
    if (agent instanceof DeepAgent deepAgent) {
        return new ClientToolTarget(deepAgent.getAgent(), SessionMatchMode.DEEP_AGENT_DERIVED);
    }
    throw new IllegalArgumentException("Unsupported agent type");
}
```

安装器从 request 构造两组不可变数据：

- `visibleTools`：当前 `metadata.clientTools` 的完整工具定义，只供 `beforeModelCall` 注入。
- `interceptToolNames`：`visibleTools` 名称，加上 runtime 从 pending `_interrupt` 恢复的客户端工具名，只供 `BaseInterruptRail` 匹配首次调用或 resume ToolCall。

rail 同时保存 `request.getConversationId()` 和 `SessionMatchMode`。每个钩子首先执行以下范围判断，不属于当前请求则立即返回：

```java
boolean belongsToCurrentRequest(AgentCallbackContext ctx) {
    String sessionId = ctx.getSession().getSessionId();
    if (sessionId.equals(conversationId)) {
        return true;
    }
    if (matchMode != SessionMatchMode.DEEP_AGENT_DERIVED) {
        return false;
    }
    Pattern derived = Pattern.compile(
        "^" + Pattern.quote(conversationId) + "_[0-9]+$");
    return derived.matcher(sessionId).matches();
}
```

禁止使用无边界的 `startsWith(conversationId)`。数字后缀规则与 DeepAgent 当前 `requestLevelSessionId` 构造保持一致，可排除普通字符串前缀和非数字后缀误命中；但它仍依赖 Core 内部命名，也无法完全排除“某个 conversationId 恰好等于另一个 conversationId 的派生 ID”这类构造性碰撞。首版将其作为 solution-only 最小适配，稳定的 origin Session 标识列入第 11 章 Core 补强。

### 4.3 `beforeModelCall`：本次模型工具注入

```text
ClientToolRail.visibleTools
  -> 确认 callback Session 属于当前请求
  -> 读取当前 ModelCallInputs.tools
  -> 检查名称冲突
  -> ToolCard.toolInfo() 或等价构造
  -> 追加到本次 ModelCallInputs.tools
```

工具定义保存在请求级 rail 的不可变字段中，不写入 Session、callback extra 或 Agent 全局状态。若客户端工具名与已有服务端工具、远程 A2A 工具或同一视图内其他工具重名，当前执行失败并返回可诊断错误；禁止覆盖或拦截已有工具。

### 4.4 `beforeToolCall`：范围过滤后复用基类

`ClientToolRail` 不重新实现中断、resume input 提取或 ToolMessage 回灌，只增加 Session 范围过滤：

```java
@Override
public void beforeToolCall(AgentCallbackContext ctx) {
    if (!belongsToCurrentRequest(ctx)) {
        return;
    }
    super.beforeToolCall(ctx);
}
```

`super.beforeToolCall(ctx)` 完整复用 `BaseInterruptRail` 的既有流程：

1. 判断当前工具名是否属于构造函数传入的 `interceptToolNames`。
2. 按 pending ToolCall ID 提取 `InteractiveInput` / resume 字符串。
3. 调用子类 `resolveInterrupt()`。
4. 首次调用抛出 `ToolInterruptException`；resume 时设置 `_skip_tool`、`toolResult` 和 `ToolMessage`。

`ClientToolRail` 只实现决策差异：

```java
@Override
protected InterruptDecision resolveInterrupt(
        AgentCallbackContext ctx, ToolCall toolCall, Object resumeInput) {
    if (resumeInput != null) {
        return reject(resumeInput);
    }
    return interrupt(InterruptRequest.builder()
        .message("Client tool invocation required: " + toolCall.getName())
        .context(Map.of(
            "_interrupt_kind", "client_tool",
            "arguments", parseArguments(toolCall.getArguments())))
        .build());
}
```

Core 现有中断转换会补充 `toolCallId` 和 `toolName`。`_interrupt_kind=client_tool` 只用于中断产生后的 runtime 路由和客户端识别，不承担 rail 工具匹配；工具匹配由 `BaseInterruptRail` 的 `toolNames` 完成。

### 4.5 A2A interrupt 投影和 Task 恢复

#### 4.5.1 首次中断

```text
ClientToolRail
  -> ToolInterruptException
  -> ReActAgent ToolInterruptionState
  -> InteractionOutput / OutputSchema
  -> JiuwenCoreAgentHandler normalizeChunk()
  -> QueryChunk(type=interrupt, data=_interrupt payload)
  -> A2AAgentExecutor
  -> status message metadata._interrupt = raw interrupt
  -> emitter.requiresInput(statusMessage)
  -> Task = INPUT_REQUIRED
```

若服务启用了 `A2AEnabledServeOrchestrator`，中断在进入 `A2AAgentExecutor` 前还经过一次按 kind 分流：

```text
JiuwenCoreAgentHandler QueryChunk(type=interrupt)
  -> A2AEnabledServeOrchestrator#handleInterrupt()
     ├─ _interrupt_kind = a2a_delegate
     │    -> handleA2ADelegate()，进入远程 A2A 委派闭环
     └─ 其他 kind（client_tool / ask_user）
          -> 原样 observer.onNext(interrupt)
          -> observer.onComplete()
          -> A2AAgentExecutor 接收 interrupt
          -> requiresInput(statusMessage)
```

因此 `client_tool` 不会进入远程委派路径，而是穿透编排层交给当前 A2A client。当前编排器对所有非 `a2a_delegate` 中断使用 `forwarding ask_user interrupt` 日志文本，该文本对 `client_tool` 不够精确，但不影响路由和 Task 状态；日志语义清理不属于本特性功能改造范围。

#### 4.5.2 resume

`A2AAgentExecutor` 只在现有 Task 状态为 `INPUT_REQUIRED` 时执行 resume 逻辑，并从 Task status message 或 history 恢复 `_interrupt`：

```text
SendMessage(message.taskId, message.parts[].text)
  -> RequestContext.getTask()
  -> state == INPUT_REQUIRED
  -> copyStoredInterrupt(task, ServeRequest.metadata)
  -> ClientToolInstaller 读取 pending client_tool 名称
  -> 创建请求级 ClientToolRail(interceptToolNames += pending toolName)
  -> ReActAgent 根据 ToolInterruptionState 将结果字符串关联到唯一 pending toolCallId
  -> ClientToolRail.beforeToolCall() 过滤 Session 后调用 super
  -> BaseInterruptRail: reject(resumeInput) -> _skip_tool + ToolMessage
```

已有 Task 但不是 `INPUT_REQUIRED` 时不得按 client-tool resume 处理。若 resume 未携带新的 `clientTools`，rail 仍用 pending toolName 完成结果回灌，但后续模型调用的端侧工具可见集合为空。终态 Task 的后续输入遵循 A2A SDK 的终态约束。

### 4.6 Task 状态机

| 当前状态 | 事件 | 下一状态 | 附加行为 |
|---|---|---|---|
| 无 Task | 初始 `SendMessage`/`SendStreamingMessage` | `SUBMITTED` | 创建 Task |
| `SUBMITTED` | Agent 开始执行 | `WORKING` | `emitter.startWork()` |
| `WORKING` | client-tool interrupt | `INPUT_REQUIRED` | status message 保存完整 `_interrupt` |
| `INPUT_REQUIRED` | 原 Task 收到结果 TextPart | `WORKING` | 字符串关联到唯一 pending ToolCall，observation 回灌 ReAct loop |
| `WORKING` | Agent 正常结束 | `COMPLETED` | 返回最终 artifact/response |
| `WORKING` | Agent 决定失败或执行异常 | `FAILED` | 记录可诊断错误 |
| `WORKING` | Agent 再次调用端侧工具 | `INPUT_REQUIRED` | 进入下一轮等待 |

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

`AgentCallbackManager` 按 rail 实例记录 callback，`ClientToolBinding.close()` 只移除自己的注册。当前 Core interruption state 本身以 Session 为恢复边界，因此同一 Session 的并行 Agent 执行不在本特性并发承诺内；runtime 应维持同一 A2A context 的顺序执行约束。不同 contextId 之间还应增加“原始 ID 与另一请求派生 ID 碰撞”的测试；在 Core 提供稳定 origin Session 标识前，该极端碰撞属于已知限制。

### 4.8 安全与可观测性

- client 提供的工具名称、description、schema、arguments 和结果文本全部按不可信输入处理。
- 工具 description 只作为模型工具说明，不得被解释为系统级配置、Spring 表达式、脚本或服务端执行指令。
- schema 只用于模型参数描述和结构校验，不触发动态类加载或服务端反射调用。
- observation 进入模型上下文前按现有消息安全策略进行长度控制、转义和敏感信息处理。
- 日志只记录 Task ID、toolCallId、工具名和脱敏错误摘要，不记录完整页面内容、插件返回内容或敏感 arguments。
- 首次中断、`INPUT_REQUIRED`、resume 校验和 observation 回灌应沿用现有 trajectory/trace 接缝；本特性不新增平行审计存储。

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
- rail 只在当前 `query/streamQuery` 调用期间注册；正常返回、中断、取消和异常路径都在 `finally` 中注销。对于 DeepAgent，interrupt 正常退出依赖 Core task-loop 结束并发送 `END_FRAME`；observer 取消当前只保证 Handler 侧注销，不保证后台 DeepAgent 已协同停止，后续补强见第 11 章。
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

预期结果：Task 不是 completed，而是 `INPUT_REQUIRED`；status message metadata 包含 `_interrupt.toolCallId`、`toolName` 和 `context.arguments`。

### 6.3 决定调用端侧工具时的完整响应

#### 6.3.1 非流式 `SendMessage` 响应

以下是 6.2 中非流式 `SendMessage` 在 Agent 决定调用 `readCurrentPage` 后返回给 client 的完整 JSON-RPC 格式。`status-msg-1` 和时间戳仅为示例值，实际分别由 A2A SDK 和服务端生成：

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
          "parts": [
            {
              "text": "Client tool invocation required: readCurrentPage"
            }
          ],
          "messageId": "status-msg-1",
          "metadata": {
            "_interrupt": {
              "type": "__interaction__",
              "index": 0,
              "payload": {
                "id": "call-123",
                "value": {
                  "message": "Client tool invocation required: readCurrentPage",
                  "context": {
                    "_interrupt_kind": "client_tool",
                    "arguments": {
                      "selector": "#main"
                    }
                  },
                  "payloadSchema": {},
                  "toolCallId": "call-123",
                  "toolName": "readCurrentPage"
                }
              },
              "message": "Client tool invocation required: readCurrentPage",
              "context": {
                "_interrupt_kind": "client_tool",
                "arguments": {
                  "selector": "#main"
                }
              },
              "toolCallId": "call-123",
              "toolName": "readCurrentPage"
            }
          }
        },
        "timestamp": "2026-07-15T10:00:00+08:00"
      },
      "artifacts": [],
      "history": [
        {
          "role": "ROLE_USER",
          "parts": [
            {
              "text": "读取当前页面并总结"
            }
          ],
          "messageId": "msg-1",
          "contextId": "ctx-1"
        }
      ]
    }
  }
}
```

client 按以下路径消费：

- 原 Task ID：`result.task.id`，后续提交结果时写入 `message.taskId`。
- 等待状态：`result.task.status.state`，值必须为 `TASK_STATE_INPUT_REQUIRED`。
- 端侧工具请求：`result.task.status.message.metadata._interrupt`。
- 工具名称和参数：分别读取 `_interrupt.toolName` 和 `_interrupt.context.arguments`。
- `_interrupt.payload` 是 Core 原始 interaction payload 的保真投影；client 以提升到 `_interrupt` 顶层的 `toolCallId`、`toolName` 和 `context` 为主，不需要解析内部 `payload.value` 才能执行工具。
- `toolCallId` 用于观察和诊断；按照首版单 pending 调用约束，client 提交结果时无需回传该字段。

#### 6.3.2 流式 `SendStreamingMessage` 响应

流式调用的 HTTP `Content-Type` 为 `text/event-stream`。Agent 决定调用端侧工具时，client 收到一个名为 `jsonrpc` 的 SSE event；当前 A2A SDK 1.0.0.Final 将 `TaskStatusUpdateEvent` 序列化到 `result.statusUpdate`。以下将 `data` 中的 JSON 按层级格式化展示；真实 SSE 传输可将同一 JSON 压缩为单行，字段语义不变：

```text
event: jsonrpc
data: {
  "jsonrpc": "2.0",
  "id": "req-1",
  "result": {
    "statusUpdate": {
      "taskId": "task-123",
      "status": {
        "state": "TASK_STATE_INPUT_REQUIRED",
        "message": {
          "role": "ROLE_AGENT",
          "parts": [
            {
              "text": "Client tool invocation required: readCurrentPage"
            }
          ],
          "messageId": "status-msg-1",
          "metadata": {
            "_interrupt": {
              "type": "__interaction__",
              "index": 0,
              "payload": {
                "id": "call-123",
                "value": {
                  "message": "Client tool invocation required: readCurrentPage",
                  "context": {
                    "_interrupt_kind": "client_tool",
                    "arguments": {
                      "selector": "#main"
                    }
                  },
                  "payloadSchema": {},
                  "toolCallId": "call-123",
                  "toolName": "readCurrentPage"
                }
              },
              "message": "Client tool invocation required: readCurrentPage",
              "context": {
                "_interrupt_kind": "client_tool",
                "arguments": {
                  "selector": "#main"
                }
              },
              "toolCallId": "call-123",
              "toolName": "readCurrentPage"
            }
          }
        },
        "timestamp": "2026-07-15T10:00:00+08:00"
      },
      "contextId": "ctx-1"
    }
  }
}
```

流式 client 按以下路径消费：

- 原 Task ID：`result.statusUpdate.taskId`。
- A2A context：`result.statusUpdate.contextId`。
- 等待状态：`result.statusUpdate.status.state`，值必须为 `TASK_STATE_INPUT_REQUIRED`。
- 端侧工具请求：`result.statusUpdate.status.message.metadata._interrupt`。
- 工具名称和参数仍分别读取 `_interrupt.toolName` 和 `_interrupt.context.arguments`。

runtime 投递该 `INPUT_REQUIRED` event 后关闭当前 SSE event queue，但不会再发送 `TASK_STATE_COMPLETED`。Task 继续保存在 TaskStore 中并保持 `INPUT_REQUIRED`；client 可直接执行工具，也可稍后通过 `GetTask` 读取同一 status message，再按 6.4 提交结果恢复原 Task。

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
        "parts": [{"text": "页面正文……"}]
      }
    }
  }'
```

预期结果：runtime 恢复原 Task；ReActAgent 将结果文本自动关联到唯一 pending ToolCall，rail 生成 ToolMessage 后继续执行，并进入 `COMPLETED`、`FAILED` 或下一次 `INPUT_REQUIRED`。

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
  │                         │                          │ beforeToolCall          │
  │                         │                          │<── tool call ──────────│
  │                         │                          │ session guard + super   │
  │                         │<── client_tool interrupt│                        │
  │<── INPUT_REQUIRED ─────│                          │                        │
  │                         │                          │ finally: unregister     │
  │                         │                          │                        │
  │ 本地执行工具             │                          │                        │
  │ SendMessage(taskId, TextPart result)             │                        │
  │────────────────────────>│ restore pending interrupt                        │
  │                         │─────────────────────────>│ register resume rail    │
  │                         │                          │───────────────────────>│
  │                         │                          │ beforeToolCall          │
  │                         │                          │<───────────────────────│
  │                         │                          │ session guard + super   │
  │                         │                          │ reject -> ToolMessage   │
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
| client-tool interrupt 缺 message | rail 构造异常 | 不产生不可观察的等待状态 | Task failed |
| Task 不是 INPUT_REQUIRED | client 携带旧 taskId 提交结果 | 不按 resume 恢复 `_interrupt` | 遵循 A2A SDK 当前 Task 约束 |
| resume 缺少结果 TextPart | client 只提交 taskId，没有有效文本 | 不构造有效 observation | 返回明确输入错误或保持 `INPUT_REQUIRED` |
| client 拒绝 | TextPart 明确说明用户拒绝 | 原文本作为 ToolMessage | Agent 决定降级、说明或失败 |
| client 执行失败 | TextPart 明确说明执行失败 | 原文本作为 ToolMessage | Agent 决定更换工具、重试或结束 |
| client 断线 | SSE/HTTP 连接断开 | Task owner 和 status 保留在 runtime | client 可通过 `GetTask` 重新观察 |
| rail 绑定失败 | 使用非 ExtHandler、不支持的 Agent 类型或注册异常 | 不启动带端侧工具的 Agent 执行 | 明确配置/装配错误，不静默执行服务端工具 |
| rail 注销异常 | 正常返回、中断、取消或异常后的清理失败 | 记录 target、Session 和 rail 标识，后续请求不得复用脏绑定 | 当前执行按主异常优先，清理异常进入告警 |

日志不得记录完整页面内容、插件返回内容或敏感 arguments；允许记录 Task ID、toolCallId、工具名和脱敏错误摘要。

---

## 8. 限制与待补

| 限制 | 影响范围 | 临时方案 |
|---|---|---|
| 只支持 JSON-RPC A2A | REST query 无法声明或提交端侧工具 | 使用 `/a2a` |
| 只支持 Agent 实例模式 | `JiuwenCoreAgentExtHandler` 不接受 agent-id string | 宿主显式提供 ReActAgent 或 DeepAgent 实例 |
| DeepAgent Session 关联依赖数字后缀规则 | 首版 solution 需要按 `conversationId_[0-9]+` 识别内部 round，依赖 Core 当前命名且存在构造性 ID 碰撞可能 | 使用严格全串匹配并覆盖碰撞测试；后续由 Core 提供稳定 origin Session 标识 |
| DeepAgent task-loop 不保证所有 completion policy 在 interrupt 后立即退出 | 显式 completion promise 或已排队 follow-up 可能使 Core 在 client 尚未恢复时继续 round，延迟 `INPUT_REQUIRED` | 首版按默认 task-loop 配置验收；通用修复在 Core 将 `result_type=interrupt` 设为当前 invocation 终止条件 |
| DeepAgent observer 取消与后台执行未形成完整协同终止 | Handler 已返回并注销 rail 时，后台线程可能仍短暂运行 | 首版不以强制取消作为正常中断路径；Core 提供可取消执行句柄后由 Runtime 补充关闭和观测 |
| 当前一次等待只保证一个端侧工具调用 | 同一模型响应并行产生多个 client tool call 时只保证单调用闭环 | 提示模型顺序调用；并行聚合另立增强项 |
| 同一 Session 不支持并行 Agent 执行 | Core interruption state 和恢复点以 Session 为边界，并行执行会竞争同一上下文 | runtime 对同一 A2A context 顺序推进；不同 Session 可并发 |
| 不提供服务端超时/取消策略 | client 长时间不提交时 Task 保持等待 | client 通过现有 Task/业务治理处理；后续独立设计 |
| 不提供业务授权策略 | runtime 只做结构和关联校验 | 由 client、Gateway 或后续治理 rail 承担 |
| 不适合在 TextPart 内联大对象或二进制内容 | 大结果会扩大消息和模型上下文 | 返回受治理对象引用和小型摘要文本 |
| 不保证跨重启完整恢复 | 受现有 TaskStore 和 Agent checkpoint 能力约束 | 启用现有 Redis-backed TaskStore/checkpointer；完整灾恢复另立特性 |

---

## 9. L0 / L1 架构一致性审视

### 9.1 L0

| L0 约束 | 本设计结论 | 是否冲突 |
|---|---|---|
| 客户端本地工具必须显式声明执行宿主和数据边界 | `clientTools` 只进入当前请求的模型工具列表，真实执行只在 client | 否 |
| runtime 是服务端 Task owner | `INPUT_REQUIRED`、GetTask 和 resume 均由 runtime 管理 | 否 |
| Core 不应因业务定制被修改 | 本设计不修改 `agent-core-java` | 否 |
| 长等待采用挂起而非占用 | 端侧工具等待表达为 Task 状态，SSE 可关闭后查询恢复 | 否 |
| 控制、数据、流机制分离 | 工具声明走 metadata，结果走正常 TextPart；大结果只传对象引用和摘要 | 否 |

### 9.2 L1 agent-runtime

L1 已定义 `ServeRequest.metadata`、interrupt chunk、`INPUT_REQUIRED`、`SendMessage` resume 和 `GetTask`。本设计是这些既有契约上的 L2 细化，不改变 handler SPI、Task owner、部署资源或 A2A northbound 边界。

### 9.3 L1 agent-core

L1 已定义 core 负责工具调用意图、组件内部恢复点和结果消费，runtime 负责 Task 级恢复入口。本设计复用 rail 和 `InteractiveInput`，不让 core 依赖 A2A 或 TaskStore，符合依赖方向。第 11 章建议的 origin Session、task-loop interrupt 终止和可取消 stream handle 都是通用 Core 执行契约补强，不引入 A2A 或 client-tool 业务依赖，因此不构成 L1 职责反转。

### 9.4 L1 agent-client

L1 已定义 client 不暴露 server-to-client webhook，而是从 Task 状态或服务流识别待处理意图，并主动发起下一轮请求。本设计使用 `INPUT_REQUIRED` + `SendMessage(taskId)`，与该方向一致。

### 9.5 审视结论

未发现需要修改 L0 或 L1 的职责冲突。本次只新增 Feat-Func-009 L2 文档和 agent-runtime L2 索引；不修改 L0/L1 文档。

---

## 10. 实施改造与验证

### 10.1 agent-runtime-java 改造

| 文件 | 修改 |
|---|---|
| `A2AAgentExecutor.java` | 仅把 `INPUT_REQUIRED` Task 识别为 resume；保存 status message metadata `_interrupt`；resume 时恢复 `_interrupt`；确保流式中断事件持久化后再关闭队列 |
| `A2AAgentExecutorTest.java` | 覆盖同步/流式 `_interrupt` 保存、status/history 恢复、非 INPUT_REQUIRED 不恢复 |

以下文件不修改：`A2aJsonRpcController`、`A2AProtocolAdapter`、`ServeRequest`、`QueryRequest`、REST controllers、`JiuwenCoreAgentHandler`。

### 10.2 agent-solution 改造

| 文件/类 | 修改 |
|---|---|
| `JiuwenCoreAgentExtHandler` | `query/streamQuery` 前创建请求级绑定，`finally` 调用 `close()`；不覆盖 `runnerSession()` |
| `ClientToolInstaller` | 解析 BaseAgent/DeepAgent 内部 ReActAgent，并保留 `EXACT` / `DEEP_AGENT_DERIVED` 匹配模式；从当前工具视图与 pending interrupt 创建并注册 rail |
| `ClientToolBinding` | 保存 target + rail；幂等 `close()` 调用 `unregisterRail(exactRail)`；提供 no-op binding |
| `ClientToolRail` | 继承 `BaseInterruptRail`；实现 `beforeModelCall`、按目标类型选择的 Session 过滤、`beforeToolCall` 和 `resolveInterrupt`；DeepAgent 只接受原始 ID 或严格数字派生后缀 |
| `ClientToolMetadataSupport` | 读取和校验 `clientTools`，读取 pending `_interrupt`，构造 visible tools 与 intercept names |
| `AgentCoreExtAutoConfiguration` | 如需 Spring 注入 installer，则注册默认 Bean；无 client metadata 时保持 no-op |

`RemoteA2aToolInstaller` 和 `RemoteA2aInterruptRail` 不修改，只增加共存测试。

### 10.3 单元测试

| 测试项 | 验证点 |
|---|---|
| metadata 校验 | `clientTools` 工具名、唯一性和 schema |
| 大结果边界 | TextPart 只承载结果文本或对象引用摘要，不内联二进制内容 |
| 请求级绑定 | 普通 ReActAgent 和 DeepAgent 内部 ReActAgent 执行前注册、完成后精确注销 |
| 异常清理 | 正常、interrupt、cancel 和 exception 四条 Handler 路径均调用 binding.close()；另验证 DeepAgent 默认 interrupt 路径产生 `END_FRAME` 后再注销 |
| rail 无全局工具 | `AbilityManager.listToolInfo()` 不出现动态 client tool |
| beforeModelCall | 只追加本次 ToolInfo，下一 invocation 不残留 |
| 名称冲突 | 不覆盖服务端工具或 Remote A2A 工具 |
| beforeToolCall 范围过滤 | ReActAgent 只精确匹配 conversationId；DeepAgent 匹配原始 ID 或 `Pattern.quote(conversationId) + "_[0-9]+"`；其他 Session 直接返回 |
| 首次调用 | 基类命中工具名后，`resolveInterrupt` 产生 client_tool interrupt，包含 ID、名称、arguments |
| resume 结果 | pending toolName 可重建拦截集合；基类把成功、拒绝和错误文本设置为 `_skip_tool` 和正确 ToolMessage |
| 并发隔离 | 同一 Agent 上不同 Session A/B 不互相看见或拦截工具，含同名不同 schema、普通前缀和原始 ID/派生 ID 构造性碰撞场景 |
| DeepAgent 覆盖 | rail 注册到 `DeepAgent#getAgent()`；探针断言内部 callback 使用派生 Session ID，严格数字后缀 guard 能命中，精确原始 ID 假设不会再进入实现 |

### 10.4 Runtime 集成测试

| 测试项 | 验证点 |
|---|---|
| 非流式 SendMessage | 初始工具调用返回 INPUT_REQUIRED 和完整 `_interrupt` |
| SendStreamingMessage | SSE 收到 INPUT_REQUIRED，流正常关闭且 Task 可查询 |
| GetTask | 断线后仍能读取 pending toolCallId/name/arguments |
| resume 成功 | 使用原 taskId 提交结果 TextPart，Agent 继续并完成 |
| resume 拒绝/错误 | 明确的拒绝/错误文本作为 observation 进入 Agent |
| 多轮调用 | 同一 Task 可经历多次 client-tool 中断与恢复 |
| ReAct/DeepAgent | ReActAgent 与默认 task-loop 配置的 DeepAgent 走同一 A2A 契约；自定义 completion promise 场景作为第 11 章 Core 补强回归测试 |
| Remote A2A 共存 | `a2a_delegate` rail 与 `client_tool` rail 不互相拦截 |
| REST 回归 | REST query 行为和请求模型不发生变化 |

### 10.5 验收标准

1. client 能通过 JSON-RPC A2A 声明本次端侧工具。
2. ReActAgent 和默认 task-loop 配置的 DeepAgent 都只能看到当前 invocation 的工具；DeepAgent 内部派生 Session 必须通过严格数字后缀 guard 命中。
3. 端侧工具调用使原 Task 进入可查询的 `INPUT_REQUIRED`，参数和 `toolCallId` 完整可见。
4. client 能用原 `message.taskId` 和结果 TextPart 提交成功、拒绝或错误说明并恢复 Agent，无需回传 toolCallId。
5. 两个并发 Task 共用同一 Agent 实例时工具目录不串扰。
6. 正常、interrupt、cancel 和 exception 后，Handler 请求级 rail 均已注销；DeepAgent 默认 interrupt 路径必须先结束 Iterator 再注销。运行期间和结束后 `AbilityManager` 均不包含动态端侧 ToolCard。
7. 不新增 REST 入口、私有结果 endpoint、Runtime 公共 DTO 或 Core 代码修改。

---

## 11. Core 与 Runtime 后续补强

本章记录为消除首版 solution 适配债务、完善 DeepAgent 通用中断语义而建议的后续框架改造，不并入本特性首版代码范围。两个问题的责任边界不同：派生 Session ID 本身不是 Core bug，但 Core 缺少稳定的父子 Session 关联契约；task-loop 收到 interrupt 后仍可能继续 round 则属于 Core 控制流缺口。Runtime 不是根因，但需要在 Core 明确终止契约后增加防御性处理和可观测性。

### 11.1 问题与责任边界

| 问题 | 当前事实 | 根因归属 | 首版处理 |
|---|---|---|---|
| DeepAgent callback Session 与 A2A conversationId 不同 | DeepAgent 使用 `conversationId + "_" + requestSeq` 创建 request-level Session，task-loop 和内部 ReAct round 均使用派生 ID | solution 原设计采用精确比较属于适配错误；Core 缺少稳定 origin Session 契约属于扩展能力不足，不把派生 ID 行为本身定义为 bug | solution 使用目标类型感知的严格数字后缀匹配 |
| DeepAgent stream 使用后台线程 | Handler 消费的是阻塞 Iterator，`postRun()` 最终通过 `END_FRAME` 结束消费 | 线程切换本身不是 Core、Runtime 或 solution bug | 保留 Handler `try/finally` 生命周期 |
| task-loop interrupt 后可能继续 round | `TaskLoopEventHandler` 已返回 `result_type=interrupt`，但 `DeepAgent#runTaskLoop()` 未无条件 break | Core 通用控制流缺口，影响 client-tool、ask_user、权限确认和 remote A2A 等所有 interrupt rail | 首版仅按默认 task-loop 配置验收；通用修复放在 Core |
| observer 取消与 DeepAgent 后台执行不同步 | Runtime Handler 可停止消费并返回，但没有通用句柄确保后台 thread 已停止 | Core 缺少可取消执行契约；Runtime 缺少契约落地后的关闭和监控 | 首版不把取消作为正常中断闭环；后续按 11.4 顺序补强 |

### 11.2 Core 补强一：稳定的 origin Session 标识

#### 11.2.1 原因

外部 `conversationId` 表示 A2A 会话边界，DeepAgent 的派生 Session ID 表示一次 request-level 执行边界，两者职责不同。当前只有字符串命名关系，没有显式父子关系，导致上层 rail、trace、审计或权限策略只能解析 `_数字` 后缀。该耦合会受 Core 命名重构影响，也无法彻底消除构造性 ID 碰撞。

#### 11.2.2 建议方案

在 Core Session 契约中增加稳定的根/来源 Session 标识，建议采用显式 API，而不是继续约定字符串格式：

```java
public interface Session {
    String getSessionId();

    default String getOriginSessionId() {
        return getSessionId();
    }
}
```

DeepAgent 创建 request-level `effectiveSession` 和 inner streaming session 时，显式继承外层 `originSessionId`：

```text
A2A conversationId = ctx-1
  -> outer Session: sessionId=ctx-1, originSessionId=ctx-1
  -> DeepAgent effective Session: sessionId=ctx-1_17, originSessionId=ctx-1
  -> inner ReAct Session: sessionId=ctx-1_17, originSessionId=ctx-1
```

随后 `ClientToolRail`、其他请求级 rail 和可观测组件统一精确比较 `getOriginSessionId()`；solution 删除 `DEEP_AGENT_DERIVED` 后缀解析。若不希望扩展 `Session` 接口，可在 `AgentSessionApi` 增加等价只读属性和统一解析帮助类，但不建议用普通业务 env key 作为长期公共契约。

#### 11.2.3 Core 验证

- invoke、stream、task-loop、inner streaming 四条路径都继承相同 origin Session ID。
- request-level Session ID 仍保持每次执行唯一，不改变 task scheduler、event queue 和 coordinator 的隔离行为。
- 外层与 effectiveSession 之间的 interruption state 复制不受影响。
- 两个构造性碰撞 ID，例如 `ctx` 与 `ctx_1`，按 origin Session 精确隔离。

### 11.3 Core 补强二：interrupt 终止当前 task-loop invocation

#### 11.3.1 原因

`TASK_INTERACTION` 表示当前执行必须等待外部输入。它与“任务完成”不同，但同样必须结束当前服务调用，不能继续消费 completion promise 或 follow-up。当前 `DeepAgent#runTaskLoop()` 已把 interrupt round 保存到 `rounds` 和 coordinator，却没有把它作为控制流终止条件。

#### 11.3.2 建议修改

在保存当前 round、usage 和 lastResult 之后，在 completion promise 和 follow-up 处理之前增加通用判断：

```java
coordinator.incrementIteration();
coordinator.addTokenUsage(resolveTokenUsage(roundResult));
coordinator.setLastResult(roundResult);

if ("interrupt".equals(String.valueOf(roundResult.get("result_type")))) {
    break;
}
```

放在该位置可以同时保证：

- interrupt round 被保留为 `final_result`；
- `state`、usage 和 pending ToolCall 已写回；
- 不错误满足或清空 completion promise；
- interrupt 优先于已经排队的 follow-up，不允许在等待外部结果前继续模型执行；Core 需明确把 session-scoped follow-up 迁移到稳定 origin Session 队列、持久化到恢复状态或显式拒绝，不能静默丢弃，也不能在当前 invocation 继续消费；
- `stopTaskLoopRuntime()`、外层状态复制、`postRun()` 和 `END_FRAME` 仍走现有 `finally`。

不建议在 `TaskLoopEventHandler` 中把 interrupt 转换为 abort。`INPUT_REQUIRED` 是可恢复暂停，不是取消或失败；复用 abort 会污染 loop state 和外部状态语义。

#### 11.3.3 Core 验证

- 默认 `TaskCompletionRail`：interrupt 后只产生一个 round 并关闭流。
- 显式 completion promise：未命中 promise 时仍立即返回 interrupt，不再继续模型调用。
- 已排队 follow-up：interrupt 后不再发生额外模型调用；follow-up 按 Core 明确的迁移、持久化或拒绝策略处理，测试不得接受静默丢失。
- invoke 与 stream 均保留完整 interruption state；stream 必须收到 `END_FRAME`。
- client-tool、ask_user、权限 rail、remote A2A rail 使用同一通用测试矩阵。

### 11.4 Core / Runtime 补强三：取消与流终止协同

#### 11.4.1 Core 先提供可取消执行契约

DeepAgent 当前返回普通阻塞 `Iterator`，调用方无法确认停止消费是否同时停止后台执行。建议 Core 提供 `AutoCloseable`/cancel-capable stream handle，或让统一 stream iterator 在 `close()` 时触发：

```text
requestAbort()
  -> 停止当前 task-loop / scheduler task
  -> stopTaskLoopRuntime()
  -> 状态复制
  -> postRun()
  -> END_FRAME
```

取消必须幂等，并区分 client disconnect、显式 Task cancel 和正常 `INPUT_REQUIRED`，不得把 interrupt 当作 cancel。

#### 11.4.2 Runtime 在 Core 契约完成后补强

Runtime 后续建议修改如下：

1. `JiuwenCoreAgentHandler#streamQuery()` 在 observer 取消或线程中断时关闭可取消 stream handle，并等待 Core 完成有限时清理后再返回。
2. 收到 `QueryChunk.TYPE_INTERRUPT` 后将其视为当前 invocation 的终端业务事件；转发一次后不再接收普通 answer chunk。该逻辑必须建立在 Core 已保证 interrupt 结束执行的前提上，不能单独提前上线。
3. `A2AEnabledServeOrchestrator` 保持仅 `a2a_delegate` 进入远程委派；把非 delegate 日志从 `forwarding ask_user interrupt` 改为包含实际 kind 的通用描述。
4. 增加 `interrupt -> END_FRAME` 时延、interrupt 后额外 chunk 数量、取消清理超时和仍在运行的后台执行数等指标；出现 interrupt 后普通 chunk 或清理超时时告警。
5. `A2AAgentExecutor` 增加防御性测试，确保首个 interrupt 已持久化为 `INPUT_REQUIRED` 后忽略重复终止事件，不把随后异常的 completion 覆盖为 `COMPLETED`。

Runtime 不应自行解析 DeepAgent Session 后缀，也不应通过关闭 A2A event queue 假装 Core 已停止。Session lineage 和 task-loop 退出都属于 Core 内部执行语义，Runtime 只消费稳定契约并维护 A2A Task 状态。

### 11.5 推荐实施顺序

```text
1. Core：interrupt 无条件结束当前 DeepAgent task-loop invocation
2. Core：提供 origin Session ID，并让 effective/inner Session 继承
3. agent-solution：ClientToolRail 改为精确匹配 origin Session，删除后缀解析
4. Core：提供可取消 stream handle / 协同停止能力
5. Runtime：接入 cancel/close、interrupt 终端防御、日志与指标
6. 三仓集成测试：ReAct + DeepAgent，sync + stream，interrupt + resume + cancel
```

第 1 步是保证端侧工具中断正确性的核心修复，应优先于 Runtime 提前停止消费；第 2、3 步消除首版 Session 命名耦合；第 4、5 步解决取消和异常场景下的生命周期一致性。完成这些补强后，2.1 中 DeepAgent 支持状态可从“受限支持”调整为完整支持，并删除第 8 章对应临时限制。
