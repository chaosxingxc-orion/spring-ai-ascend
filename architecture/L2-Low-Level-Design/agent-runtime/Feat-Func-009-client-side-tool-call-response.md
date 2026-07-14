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

# 调用端侧工具响应 — 设计文档

> 需求名称：【FEAT-009】【调用端侧工具响应】新增支持带有端侧工具的请求，需要调用客户端侧工具的中断，通过智能体服务调用响应返回给客户端
> 关联需求：`FEAT-2026-010`（agent-core 端侧工具动态注册与调用）
> 目标实现：`agent-runtime-java`、`agent-solution/common/agent-runtime-ext-java`
> 最后更新：2026-07-14
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
- `A2AAgentExecutor` 已能把 interrupt chunk 投影为 `INPUT_REQUIRED`；当前工作分支补充了原始 `_interrupt` 在 Task status message 中的保存和 resume 恢复。
- `agent-core-java` 的 ReActAgent 已支持 `beforeInvoke`、`beforeModelCall`、`beforeToolCall` rail、`ToolInterruptException`、`InteractiveInput` 和 `_skip_tool` 结果回灌。
- DeepAgent 内部持有 ReActAgent，并会把传入 `AgentSessionApi` 的 env 复制到 request-level session 和内部 ReAct round session。
- `agent-solution` 的 `JiuwenCoreAgentExtHandler`、`RemoteA2aToolInstaller` 和 `RemoteA2aInterruptRail` 已提供“识别普通 ReActAgent/DeepAgent、幂等安装 rail、中断后恢复”的参考模式。

### 1.3 核心设计原则

1. **A2A Task 是唯一服务端状态 owner** — 端侧工具等待通过 `INPUT_REQUIRED` 表达，不新增私有结果提交端点或第二套 Task 状态机。
2. **端侧工具是 invocation 级上下文** — 工具定义只进入本次 Session 和模型调用，不写入共享 `AbilityManager`、ResourceMgr、MCP 或 Skill Hub。
3. **solution 扩展承载 AgentCore 适配** — `JiuwenCoreAgentExtHandler` 负责把 A2A metadata 放入本次 `AgentSessionApi.env`，无须修改 `JiuwenCoreAgentHandler` 或 `agent-core-java`。
4. **rail 安装一次、请求数据不驻留** — Agent 上只安装一个无状态 `ClientToolRail`；工具列表和结果从当前 callback context 读取。
5. **结果是 observation，不是 runtime 业务裁决** — `SUCCESS`、`REJECTED`、`ERROR` 均回灌 Agent，由 Agent 决定继续、降级、完成或失败。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|---|---|---|---|
| A2A 能力视图接收 | 从 `params.metadata.clientToolContext` 接收本次工具目录 | `A2aJsonRpcController`, `ServeRequest.metadata` | ⚠️ 基础透传已存在，端侧工具校验待实现 |
| 请求级上下文注入 | 将 metadata 放入本次 `AgentSessionApi.env` | `JiuwenCoreAgentExtHandler#runnerSession` | ⬜ 计划中 |
| 动态工具可见性 | 只向本次 `ModelCallInputs.tools` 添加 ToolInfo | `ClientToolRail#beforeModelCall` | ⬜ 计划中 |
| 调用移交 | 端侧工具调用转换为 Core interrupt | `ClientToolRail#beforeToolCall` | ⬜ 计划中 |
| Task 等待投影 | interrupt 转换为 A2A `INPUT_REQUIRED` | `A2AAgentExecutor`, `_interrupt` | ⚠️ 通用能力已存在，元数据保真改动待合入 |
| 客户端结果回灌 | `clientToolResults` 转为 `InteractiveInput` observation | `ClientToolRail#beforeInvoke` | ⬜ 计划中 |
| ReActAgent / DeepAgent 覆盖 | rail 安装到实际执行的 ReActAgent，Session env 向内传播 | `ClientToolInstaller` | ⬜ 计划中 |

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
| 成功结果回灌 | ⬜ | `SUCCESS` observation 转为 ToolMessage 后继续 ReAct loop。 |
| 拒绝与错误回灌 | ⬜ | `REJECTED`、`ERROR` 不伪造成成功，也不由 runtime 自动标记 Task failed。 |
| ReActAgent 支持 | ⬜ | `ClientToolRail` 安装到 ReActAgent。 |
| DeepAgent 支持 | ⬜ | rail 安装到 `DeepAgent#getAgent()`，metadata 经 Session env 传播到内部 round。 |
| 同一 Task 多轮调用 | ⬜ | 每次 resume 后允许再次产生新的端侧工具请求。 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|---|---|---|
| REST `/query`、MVC/WebFlux query 入口 | 当前需求只接受 JSON-RPC A2A 入口 | 后续独立特性评审 |
| 新增客户端结果提交 endpoint | A2A `SendMessage`/`SendStreamingMessage` 已能恢复原 Task | 使用 `message.taskId` + `params.metadata.clientToolResults` |
| 服务端访问 DOM、插件、文件、本地端口 | 客户端资源不在服务端信任和网络边界内 | client 本地执行后主动提交结果 |
| 全局 Tool Registry、MCP、Skill Hub 注册 | 动态端侧工具只属于当前 invocation | `beforeModelCall` 注入本次 `ModelCallInputs.tools` |
| 执行前向 `AbilityManager` 注册、执行后注销 | 共享 Agent 并发时会串工具，同名工具会覆盖或误删 | 无状态 rail + request context |
| Gateway / Event Bus Task owner | Task 权威状态属于 runtime | Gateway / Event Bus 只做受治理透传或投影 |
| runtime 判断业务结果真假 | runtime 只验证关联关系，不解释客户端业务事实 | Agent 消费 observation 后决定后续逻辑 |
| 平台级工具审计和授权策略 | 属于 middleware / bus / 业务治理扩展 | 本特性保留 metadata 和 trace 接缝，不新增策略系统 |

### 2.3 外部接口契约（Logical View）

#### 2.3.1 JSON-RPC 方法

| 方法 | 本特性用途 | 约束 |
|---|---|---|
| `SendMessage` | 初始调用、非流式端侧工具等待、提交结果并恢复 Task | 能力和结果放在 `params.metadata`；resume 必须使用原 `message.taskId` |
| `SendStreamingMessage` | 初始流式调用、通过 SSE 观察等待状态、提交结果后继续流式执行 | metadata 契约与 `SendMessage` 相同 |
| `GetTask` | client 断线或稍后恢复时重新查询 pending 端侧工具请求 | 只按 Task ID 查询，不创建新 Task |

#### 2.3.2 初始能力视图

```json
{
  "clientToolContext": {
    "version": "1.0",
    "updateMode": "REPLACE",
    "scope": "CURRENT_TASK",
    "tools": [
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
}
```

约束如下：

- `version` 当前只接受 `1.0`。
- `updateMode` 当前只接受 `REPLACE`。
- `scope` 当前只接受 `CURRENT_TASK`。
- `tools[].name` 必须非空，并在本次视图中唯一。
- `inputSchema` 使用 JSON Schema object 形态；空 schema 按空 object 处理。
- 本次请求未携带 `clientToolContext` 时，当前可见端侧工具集合为空，不自动继承上一轮能力视图。

#### 2.3.3 客户端结果

```json
{
  "clientToolResults": {
    "version": "1.0",
    "items": [
      {
        "toolCallId": "call-123",
        "status": "SUCCESS",
        "result": {
          "content": "页面正文……"
        }
      }
    ]
  }
}
```

状态语义：

| 状态 | 必要字段 | observation 语义 |
|---|---|---|
| `SUCCESS` | `toolCallId`, `result` | 客户端工具成功执行，`result` 原样作为结构化 observation 的一部分 |
| `REJECTED` | `toolCallId`，可选 `error` | 用户或客户端明确拒绝执行 |
| `ERROR` | `toolCallId`, `error.code`, `error.message` | 权限不足、工具不可用或执行失败 |

`message.taskId` 是 Task 关联的唯一权威字段，结果 item 不重复携带 Task ID。

`params.metadata` 是端侧工具控制信封，不是大对象数据通道。小型 JSON 结果允许内联；页面全文、文件、图片、二进制内容或其他大结果必须在 `result` 中返回受治理的对象引用和必要摘要，例如：

```json
{
  "toolCallId": "call-123",
  "status": "SUCCESS",
  "result": {
    "summary": "页面正文已采集",
    "reference": {
      "uri": "object://client-results/task-123/call-123",
      "mediaType": "text/html"
    }
  }
}
```

对象引用的签发、权限和读取通道不由本特性定义；调用方必须复用平台既有数据面治理能力。

#### 2.3.4 Task 等待投影

runtime 复用当前 status message metadata 的 `_interrupt`，不新增平行的 `clientToolRequests` Task 字段：

```json
{
  "_interrupt": {
    "type": "__interaction__",
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

client 通过阻塞响应、SSE status update 或 `GetTask` 观察同一结构。`_interrupt_kind=client_tool` 用于区别人工输入和远程 A2A delegate。

#### 2.3.5 行为承诺

- **必须**：端侧工具等待时 Task 保持 `INPUT_REQUIRED`，不得标记为 `COMPLETED`。
- **必须**：resume 只接受原 Task，并按 pending `toolCallId` 关联 observation。
- **必须**：动态工具只进入本次模型调用，不进入共享 `AbilityManager`。
- **必须**：客户端拒绝和执行错误以 observation 回灌 Agent。
- **禁止**：使用 TextPart 承载工具定义或结构化工具结果。
- **禁止**：runtime 或 core 直接访问客户端资源。
- **允许**：client 在 resume 请求中提供新的完整能力视图；该视图替换上一轮，不影响已经产生的 pending tool call。

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
        │   └── JiuwenCoreAgentExtHandler.java     # 安装 rail；metadata -> request Session env
        └── external/clienttool/
            ├── ClientToolInstaller.java           # 解析目标 ReActAgent并幂等安装 rail
            ├── ClientToolRail.java                # beforeInvoke/model/tool 三阶段逻辑
            └── ClientToolMetadataSupport.java     # 原始 Map 校验和读取，无公共 DTO
```

### 3.3 agent-core-java

本特性不修改 `agent-core-java`。直接复用：

```text
ReActAgent
├── AgentCallbackContext.extra
├── InvokeInputs.queryPayload
├── ModelCallInputs.tools
├── ToolInterruptionState
└── InteractiveInput

DeepAgent
└── inner ReActAgent + AgentSessionApi.env 传播
```

### 3.4 核心类静态关系

```text
JiuwenCoreAgentHandler
        ▲
        │ extends
JiuwenCoreAgentExtHandler ───── installs once ─────▶ ClientToolInstaller
        │                                                │
        │ overrides runnerSession()                      │ resolves
        ▼                                                ▼
AgentSessionApi.env                               ReActAgent / DeepAgent.getAgent()
        │                                                │
        └──────────── consumed by ───────────────▶ ClientToolRail
                                                       │
                        ┌──────────────────────────────┼──────────────────────────┐
                        ▼                              ▼                          ▼
                  beforeInvoke                  beforeModelCall             beforeToolCall
                  context/result                ToolInfo injection          interrupt/observation
```

---

## 4. 核心设计（Logical + Process View）

### 4.1 请求级上下文承载

#### 4.1.1 `runnerSession()` 扩展

`JiuwenCoreAgentExtHandler` 在调用 `super.query()` / `super.streamQuery()` 前继续执行已有的 rail 安装逻辑，并覆盖基类现有的 `runnerSession(ServeRequest)`：

```java
@Override
protected Object runnerSession(ServeRequest request) {
    Map<String, Object> envs = new LinkedHashMap<>(resolveOriginalAgentEnvs(getAgent()));
    envs.put(CLIENT_TOOL_REQUEST_METADATA,
        immutableCopy(request.getMetadata()));

    return new AgentSessionApi(
        resolveSessionId(request),
        envs,
        resolveAgentCard(getAgent()),
        List.of(StreamMode.OUTPUT));
}
```

实现约束：

- 必须合并 Agent 原有 env，不能因加入 client-tool metadata 改变现有配置行为。
- metadata 使用防御性复制，rail 不修改 `ServeRequest.metadata`。
- 未携带 client-tool metadata 时仍可使用同一 Session 构造路径。
- `JiuwenCoreAgentExtHandler` 当前只接受 Agent 实例，不接受 agent-id string；本特性保持该限制。

#### 4.1.2 DeepAgent 传播

DeepAgent 已将输入 Session env 复制到 request-level session；流式内部 round 也会把 env 复制到 inner session。因此 client-tool metadata 不需要进入 DeepAgent 的全局字段，也不需要修改 `executeCoreLoopRound()`：

```text
ExtHandler AgentSessionApi.env
  -> DeepAgent request-level AgentSessionApi.env
  -> task-loop inner AgentSessionApi.env
  -> inner ReActAgent callback session
```

### 4.2 无状态 rail 安装

`ClientToolInstaller` 复用 `RemoteA2aToolInstaller` 的目标解析模式：

```java
BaseAgent resolve(Object agent) {
    if (agent instanceof BaseAgent baseAgent) {
        return baseAgent;
    }
    if (agent instanceof DeepAgent deepAgent) {
        return deepAgent.getAgent();
    }
    throw new IllegalArgumentException("Unsupported agent type");
}
```

安装器使用 `WeakHashMap<BaseAgent, Boolean>` 或等价弱引用集合保证每个实际 ReActAgent 只安装一个 rail。`ClientToolRail#getTools()` 必须为空，注册 rail 时不得触发 `AbilityManager.add(ToolCard)`。

### 4.3 `beforeInvoke`：上下文和结果准备

```text
ReActAgent 构造 InvokeInputs
  -> fire BEFORE_INVOKE
  -> ClientToolRail 从 AgentSessionApi.env 读取 metadata
  -> metadata 写入本次 ctx.extra.run_context
  -> 若 _interrupt_kind != client_tool：结束
  -> 若为 client_tool resume：
       读取 pending toolCallId
       从 clientToolResults.items 查找匹配项
       构造 InteractiveInput
       覆盖 InvokeInputs.queryPayload
  -> ReActAgent 加载 ToolInterruptionState 并恢复
```

伪代码：

```java
public void beforeInvoke(AgentCallbackContext ctx) {
    Map<String, Object> metadata = metadataFromSession(ctx.getSession());
    ctx.getExtra().put("run_context", metadata);

    if (!(ctx.getInputs() instanceof InvokeInputs inputs)
        || !isClientToolResume(metadata)) {
        return;
    }

    InteractiveInput resumeInput = new InteractiveInput();
    String pendingId = pendingToolCallId(metadata.get("_interrupt"));
    findResult(metadata, pendingId)
        .ifPresent(result -> resumeInput.update(pendingId, result));
    inputs.setQueryPayload(resumeInput);
}
```

即使结果缺失或 `toolCallId` 不匹配，也必须设置空 `InteractiveInput`，禁止回退到 TextPart 字符串。这样 ReActAgent 不会把“Client tool results submitted”等提示文本误当成工具结果；rail 会再次中断，Task 继续保持 `INPUT_REQUIRED`。

### 4.4 `beforeModelCall`：本次模型工具注入

```text
ctx.extra.run_context.clientToolContext.tools
  -> 校验 version/updateMode/scope/schema
  -> 读取当前 ModelCallInputs.tools
  -> 检查名称冲突
  -> ToolCard.toolInfo() 或等价构造
  -> 追加到本次 ModelCallInputs.tools
  -> 将实际注入名称保存到 ctx.extra._client_tool_names
```

`_client_tool_names` 属于当前 callback context，只用于后续 `beforeToolCall` 判断，不写入 Agent 全局状态。

若客户端工具名与已有服务端工具、远程 A2A 工具或同一视图内其他工具重名，当前 invocation 失败并返回可诊断错误；禁止覆盖或拦截已有工具。

### 4.5 `beforeToolCall`：调用移交和 observation 回灌

#### 4.5.1 首次调用

当工具名存在于本次 `_client_tool_names`，且当前 `toolCallId` 没有匹配 observation 时：

```java
InterruptRequest request = InterruptRequest.builder()
    .message("Client tool invocation required: " + toolName)
    .context(Map.of(
        "_interrupt_kind", "client_tool",
        "arguments", parseArguments(toolCall.getArguments())))
    .build();
throw new ToolInterruptException(request, toolCall);
```

Core 现有 `ToolCallInterruptRequest.fromToolCall(...)` 会补充 `toolCallId` 和 `toolName`。arguments 放在已有 `InterruptRequest.context`，无需修改 Core 类型。

#### 4.5.2 resume 回灌

当 `InteractiveInput` 包含当前 `toolCallId` 时：

- `SUCCESS`：把结构化 result 序列化为 ToolMessage content。
- `REJECTED`：生成明确的拒绝 observation。
- `ERROR`：生成包含 error code/message 的错误 observation。
- 设置 `ctx.extra._skip_tool=true`，防止 `AbilityManager` 查找不存在的端侧工具实例。
- 设置 `ToolCallInputs.toolResult` 和 `ToolCallInputs.toolMsg`，由现有 ReAct loop 继续执行。

### 4.6 A2A interrupt 投影和 Task 恢复

#### 4.6.1 首次中断

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

#### 4.6.2 resume

`A2AAgentExecutor` 只在现有 Task 状态为 `INPUT_REQUIRED` 时执行 resume 逻辑，并从 Task status message 或 history 恢复 `_interrupt`：

```text
SendMessage(message.taskId, params.metadata.clientToolResults)
  -> RequestContext.getTask()
  -> state == INPUT_REQUIRED
  -> copyStoredInterrupt(task, ServeRequest.metadata)
  -> JiuwenCoreAgentExtHandler.runnerSession()
  -> ClientToolRail.beforeInvoke()
  -> InteractiveInput
  -> ReActAgent resume
```

已有 Task 但不是 `INPUT_REQUIRED` 时不得按 client-tool resume 处理。终态 Task 的后续输入遵循 A2A SDK 的终态约束。

### 4.7 Task 状态机

| 当前状态 | 事件 | 下一状态 | 附加行为 |
|---|---|---|---|
| 无 Task | 初始 `SendMessage`/`SendStreamingMessage` | `SUBMITTED` | 创建 Task |
| `SUBMITTED` | Agent 开始执行 | `WORKING` | `emitter.startWork()` |
| `WORKING` | client-tool interrupt | `INPUT_REQUIRED` | status message 保存完整 `_interrupt` |
| `INPUT_REQUIRED` | 匹配结果提交 | `WORKING` | 恢复原 Task，observation 回灌 ReAct loop |
| `INPUT_REQUIRED` | 结果缺失或 `toolCallId` 不匹配 | `INPUT_REQUIRED` | 不消费错误输入，重新发出等待状态 |
| `WORKING` | Agent 正常结束 | `COMPLETED` | 返回最终 artifact/response |
| `WORKING` | Agent 决定失败或执行异常 | `FAILED` | 记录可诊断错误 |
| `WORKING` | Agent 再次调用端侧工具 | `INPUT_REQUIRED` | 进入下一轮等待 |

### 4.8 并发隔离

本特性禁止以下实现：

```java
try {
    abilityManager.add(clientToolCard);
    super.streamQuery(request, observer);
} finally {
    abilityManager.remove(clientToolCard.getName());
}
```

共享 Agent 并发执行时，注册到注销之间的 ToolCard 对所有 Task 可见；同名工具还会发生覆盖和误删。正确隔离如下：

```text
共享 ReActAgent
  └── ClientToolRail（安装一次，无请求字段）

Task A AgentCallbackContext.extra
  └── client tools A

Task B AgentCallbackContext.extra
  └── client tools B
```

### 4.9 安全与可观测性

- client 提供的工具名称、description、schema、arguments 和 result 全部按不可信输入处理。
- 工具 description 只作为模型工具说明，不得被解释为系统级配置、Spring 表达式、脚本或服务端执行指令。
- schema 只用于模型参数描述和结构校验，不触发动态类加载或服务端反射调用。
- observation 进入模型上下文前按现有消息安全策略进行长度控制、转义和敏感信息处理。
- 日志只记录 Task ID、toolCallId、工具名、结果状态和脱敏错误码，不记录完整页面内容、插件返回内容或敏感 arguments。
- 首次中断、`INPUT_REQUIRED`、resume 校验和 observation 回灌应沿用现有 trajectory/trace 接缝；本特性不新增平行审计存储。

---

## 5. 配置模型（Physical View）

### 5.1 配置结论

本特性不新增 YAML 或 Spring ConfigurationProperties。端侧工具是客户端随 invocation 提供的能力视图，不是服务启动配置。

| 配置项 | 结论 | 原因 |
|---|---|---|
| 全局 enable 开关 | 不新增 | 未携带 `clientToolContext` 时自然不启用 |
| 工具白名单 | 当前不新增 | 业务授权策略属于后续治理扩展；当前只做结构和名称冲突校验 |
| 工具超时 | 不新增 | 工具在 client 执行，当前 FEAT 不承诺服务端工具超时策略 |
| 客户端 endpoint | 不新增 | runtime 不反向访问 client |

### 5.2 部署与状态

- `ClientToolRail` 与 `JiuwenCoreAgentExtHandler` 位于同一宿主 JVM。
- request metadata 只存在于本次 `AgentSessionApi.env` 和 callback context。
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
        "clientToolContext": {
          "version": "1.0",
          "updateMode": "REPLACE",
          "scope": "CURRENT_TASK",
          "tools": [{
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
    }
  }'
```

预期结果：Task 不是 completed，而是 `INPUT_REQUIRED`；status message metadata 包含 `_interrupt.toolCallId`、`toolName` 和 `context.arguments`。

### 6.3 提交结果示例

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
        "parts": [{"text": "Client tool results submitted"}]
      },
      "metadata": {
        "clientToolResults": {
          "version": "1.0",
          "items": [{
            "toolCallId": "call-123",
            "status": "SUCCESS",
            "result": {"content": "页面正文……"}
          }]
        },
        "clientToolContext": {
          "version": "1.0",
          "updateMode": "REPLACE",
          "scope": "CURRENT_TASK",
          "tools": []
        }
      }
    }
  }'
```

预期结果：runtime 恢复原 Task，Agent 获得 `call-123` observation，继续执行并进入 `COMPLETED`、`FAILED` 或下一次 `INPUT_REQUIRED`。

### 6.4 E2E 流程

```text
Client                 A2A Runtime              ExtHandler/Rail          ReAct/DeepAgent
  │                         │                          │                        │
  │ SendMessage + tools     │                          │                        │
  │────────────────────────>│ ServeRequest.metadata    │                        │
  │                         │─────────────────────────>│ Session env             │
  │                         │                          │───────────────────────>│
  │                         │                          │ beforeModelCall: tools  │
  │                         │                          │<───────────────────────│
  │                         │                          │ beforeToolCall          │
  │                         │                          │<── tool call ──────────│
  │                         │<── interrupt ───────────│                        │
  │<── INPUT_REQUIRED ─────│                          │                        │
  │                         │                          │                        │
  │ 本地执行工具             │                          │                        │
  │ SendMessage(taskId, result)                      │                        │
  │────────────────────────>│ restore _interrupt      │                        │
  │                         │─────────────────────────>│ beforeInvoke             │
  │                         │                          │ InteractiveInput        │
  │                         │                          │───────────────────────>│
  │                         │                          │ observation             │
  │                         │                          │<───────────────────────│
  │<── final / next wait ──│                          │                        │
```

---

## 7. 错误处理（Process View）

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---|---|---|---|
| metadata 版本不支持 | `version != 1.0` | ExtHandler/Rail 拒绝本次执行，不注入工具 | Task failed，返回脱敏的版本错误 |
| updateMode/scope 不支持 | 非 `REPLACE`/`CURRENT_TASK` | 拒绝能力视图 | Task failed，返回明确参数错误 |
| 工具定义缺字段 | name 为空、schema 非法 | 不进入模型调用，记录校验错误 | Task failed |
| 工具名冲突 | 与已有 ToolInfo 或本次其他工具同名 | 禁止覆盖共享或服务端工具 | Task failed，返回冲突名称 |
| client-tool interrupt 缺 message | rail 构造异常 | 不产生不可观察的等待状态 | Task failed |
| Task 不是 INPUT_REQUIRED | client 携带旧 taskId 提交结果 | 不按 resume 恢复 `_interrupt` | 遵循 A2A SDK 当前 Task 约束 |
| `toolCallId` 不匹配 | 结果不属于当前 pending call | 构造空 `InteractiveInput`，不消费 TextPart | 重新返回 `INPUT_REQUIRED` |
| 结果状态未知 | 非 SUCCESS/REJECTED/ERROR | 视为无有效 observation | 重新返回 `INPUT_REQUIRED`，记录 WARN |
| client 拒绝 | `status=REJECTED` | 生成拒绝 ToolMessage | Agent 决定降级、说明或失败 |
| client 执行失败 | `status=ERROR` | 生成错误 ToolMessage | Agent 决定更换工具、重试或结束 |
| client 断线 | SSE/HTTP 连接断开 | Task owner 和 status 保留在 runtime | client 可通过 `GetTask` 重新观察 |
| rail 未安装 | 使用非 ExtHandler 或不支持的 Agent 类型 | 不向模型暴露端侧工具 | 明确配置/装配错误，不静默执行服务端工具 |

日志不得记录完整页面内容、插件返回内容或敏感 arguments；允许记录 Task ID、toolCallId、工具名、状态和脱敏错误码。

---

## 8. 限制与待补

| 限制 | 影响范围 | 临时方案 |
|---|---|---|
| 只支持 JSON-RPC A2A | REST query 无法声明或提交端侧工具 | 使用 `/a2a` |
| 只支持 Agent 实例模式 | `JiuwenCoreAgentExtHandler` 不接受 agent-id string | 宿主显式提供 ReActAgent 或 DeepAgent 实例 |
| 当前一次等待只保证一个端侧工具调用 | 同一模型响应并行产生多个 client tool call 时只保证单调用闭环 | 提示模型顺序调用；并行聚合另立增强项 |
| 能力视图只支持 REPLACE | 不支持 PATCH/MERGE | client 每轮发送完整当前能力视图 |
| 不提供服务端超时/取消策略 | client 长时间不提交时 Task 保持等待 | client 通过现有 Task/业务治理处理；后续独立设计 |
| 不提供业务授权策略 | runtime 只做结构和关联校验 | 由 client、Gateway 或后续治理 rail 承担 |
| 不支持在 metadata 内联大对象或二进制内容 | 大结果会挤占控制通道并扩大 Task 快照 | 返回受治理对象引用和小型摘要 |
| 不保证跨重启完整恢复 | 受现有 TaskStore 和 Agent checkpoint 能力约束 | 启用现有 Redis-backed TaskStore/checkpointer；完整灾恢复另立特性 |

---

## 9. L0 / L1 架构一致性审视

### 9.1 L0

| L0 约束 | 本设计结论 | 是否冲突 |
|---|---|---|
| 客户端本地工具必须显式声明执行宿主和数据边界 | `scope=CURRENT_TASK`，真实执行只在 client | 否 |
| runtime 是服务端 Task owner | `INPUT_REQUIRED`、GetTask 和 resume 均由 runtime 管理 | 否 |
| Core 不应因业务定制被修改 | 本设计不修改 `agent-core-java` | 否 |
| 长等待采用挂起而非占用 | 端侧工具等待表达为 Task 状态，SSE 可关闭后查询恢复 | 否 |
| 控制、数据、流机制分离 | 工具控制信封和小型结果走 A2A metadata；大结果只传对象引用，正文不进入 TextPart | 否 |

### 9.2 L1 agent-runtime

L1 已定义 `ServeRequest.metadata`、interrupt chunk、`INPUT_REQUIRED`、`SendMessage` resume 和 `GetTask`。本设计是这些既有契约上的 L2 细化，不改变 handler SPI、Task owner、部署资源或 A2A northbound 边界。

### 9.3 L1 agent-core

L1 已定义 core 负责工具调用意图、组件内部恢复点和结果消费，runtime 负责 Task 级恢复入口。本设计复用 rail 和 `InteractiveInput`，不让 core 依赖 A2A 或 TaskStore，符合依赖方向。

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
| `JiuwenCoreAgentExtHandler` | 安装 ClientToolRail；覆盖 `runnerSession()`；合并原 env 并注入 request metadata |
| `ClientToolInstaller` | 解析 BaseAgent/DeepAgent 内部 ReActAgent，弱引用幂等安装无状态 rail |
| `ClientToolRail` | 实现 beforeInvoke、beforeModelCall、beforeToolCall |
| `ClientToolMetadataSupport` | 读取和校验原始 metadata Map，构造 ToolInfo/InteractiveInput observation |
| `AgentCoreExtAutoConfiguration` | 如需 Spring 注入 installer，则注册默认 Bean；无 client metadata 时保持 no-op |

`RemoteA2aToolInstaller` 和 `RemoteA2aInterruptRail` 不修改，只增加共存测试。

### 10.3 单元测试

| 测试项 | 验证点 |
|---|---|
| metadata 校验 | version、REPLACE、CURRENT_TASK、工具名、schema、结果状态 |
| 大结果边界 | metadata 只接受小型结构化结果；大对象通过 reference 表达 |
| rail 幂等安装 | 普通 ReActAgent 和 DeepAgent 内部 ReActAgent 各只安装一次 |
| rail 无全局工具 | `AbilityManager.listToolInfo()` 不出现动态 client tool |
| beforeInvoke 初始请求 | Session metadata 进入当前 callback extra |
| beforeInvoke resume | 匹配结果生成 InteractiveInput；不匹配生成空 InteractiveInput |
| beforeModelCall | 只追加本次 ToolInfo，下一 invocation 不残留 |
| 名称冲突 | 不覆盖服务端工具或 Remote A2A 工具 |
| beforeToolCall 首次调用 | 产生 client_tool interrupt，包含 ID、名称、arguments |
| success/rejected/error | 均设置 `_skip_tool` 和正确 ToolMessage |
| 并发隔离 | 同一 Agent 上 Task A/B 不互相看见工具，含同名不同 schema 场景 |
| DeepAgent env 传播 | 非流式和流式 task-loop 内部 ReActAgent 均读取当前 metadata |

### 10.4 Runtime 集成测试

| 测试项 | 验证点 |
|---|---|
| 非流式 SendMessage | 初始工具调用返回 INPUT_REQUIRED 和完整 `_interrupt` |
| SendStreamingMessage | SSE 收到 INPUT_REQUIRED，流正常关闭且 Task 可查询 |
| GetTask | 断线后仍能读取 pending toolCallId/name/arguments |
| resume 成功 | 使用原 taskId 提交 SUCCESS，Agent 继续并完成 |
| resume 拒绝/错误 | observation 进入 Agent，不被 runtime 伪造成成功或直接失败 |
| 错误 call ID | 不推进错误结果，Task 再次 INPUT_REQUIRED |
| 多轮调用 | 同一 Task 可经历多次 client-tool 中断与恢复 |
| ReAct/DeepAgent | 两类 Agent 走同一 A2A 契约 |
| Remote A2A 共存 | `a2a_delegate` rail 与 `client_tool` rail 不互相拦截 |
| REST 回归 | REST query 行为和请求模型不发生变化 |

### 10.5 验收标准

1. client 能通过 JSON-RPC A2A 声明本次端侧工具。
2. ReActAgent 和 DeepAgent 的模型都只能看到当前 invocation 的工具。
3. 端侧工具调用使原 Task 进入可查询的 `INPUT_REQUIRED`，参数和 `toolCallId` 完整可见。
4. client 能用原 `message.taskId` 提交成功、拒绝或错误结果并恢复 Agent。
5. 两个并发 Task 共用同一 Agent 实例时工具目录不串扰。
6. 运行期间和结束后 `AbilityManager` 均不包含动态端侧 ToolCard。
7. 不新增 REST 入口、私有结果 endpoint、Runtime 公共 DTO 或 Core 代码修改。
