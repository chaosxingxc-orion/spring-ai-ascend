---
level: L2-LLD
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-005
status: active
dependency:
  - ../../L1-High-Level-Design/agent-runtime/README.md
  - ../../L1-High-Level-Design/agent-runtime/development.md
  - ../../L1-High-Level-Design/agent-runtime/process.md
  - ../../../version-scope/FEAT-005-remote-agent-orchestration.md
---

# 远程 Agent 编排 — 设计文档

> 目标模块：`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/`（南向）
> 最后更新：2026-06-14
> **⚠️ 关键约束：没有 skills 的 Agent Card 不会被 LLM 作为 Tool 调用。** 如果远端 Agent Card 的 `skills` 字段为空或不存在，Card Cache 不会为其生成 `RemoteAgentToolSpec`，该 Agent 对 LLM 不可见。这意味着：
> - 如果你的 Agent 需要被其他 Agent 作为 Tool 调用，必须在 Agent Card 中声明至少一个 skill
> - 仅用于直接 A2A 调用的 Agent（不需要被其他 Agent 发现的）可以不声明 skills

---

## 1. 概述

### 1.1 特性定位

agent-runtime 作为 A2A 客户端接入和调用其他 A2A Agent，实现跨 Agent 协作。远程 Agent 通过 YAML 配置静态接入，runtime 自动拉取 Agent Card、缓存维护本地目录、生成工具描述、安装为本地 Agent 可调用的 Tool。当 LLM 调用远程 Tool 时，走中断-续接流水线：本地 Agent 挂起 → 远程调用 → 等待结果 → 回灌本地 Agent 继续推理。

- **解决的问题**：单个 Agent 能力有限，需要将专业任务委托给其他 Agent。A2A 协议提供了标准的跨 Agent 通信方式，无需 Agent 之间共享代码或状态。
- **适用场景**：多 Agent 协作（旅行助手调用天气/酒店/航班 Agent）、企业 Agent 生态（主 Agent 调用部门级子 Agent）。如果只需要单一 Agent 完成所有任务，不需要此特性。

### 1.2 当前事实边界

本文只描述 Feat-Func-005 在当前 `agent-runtime` 模块中的已接受实现事实。面向调用方的黑盒行为、用户场景和外部示例已迁移到 `version-scope/FEAT-005-remote-agent-orchestration.md`；模块级 API/SPI、逻辑对象归属和部署资源模型以 L1 设计及其附录为准。

### 1.3 设计原则

1. **配置驱动** — 远程端点通过 YAML 静态配置，非动态网络发现
2. **A2A 原生** — 南向通信完全使用 A2A JSON-RPC，与远程 Agent 的实现语言/框架无关
3. **中断-续接** — 远程 Agent 返回输入请求时，父 Task 自动挂起等待；输入到达后恢复执行
4. **故障隔离** — 远程 Agent 不可用时从目录标记不可用，不影响其他远程 Agent 和本地 Agent 的运行

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| 远程 Agent 配置接入 | YAML 配置 → 拉取 Card → 缓存目录 | `RemoteAgentCardCache`, `RemoteAgentProperties` | ✅ |
| 远程调用通道 | A2A JSON-RPC 出站调用 | `A2aRemoteAgentOutboundAdapter` | ✅ |
| 远程调用编排 | 完整调用生命周期 + 中断-续接 | `A2aRemoteInvocationOrchestrator`, `A2aParentTaskProjector` | ✅ |
| 工具注入 | 远端 Skill → 本地 Tool | `OpenJiuwenRemoteToolInstaller`, `OpenJiuwenRemoteAgentInterruptRail` | ✅ |

---

## 2. 特性规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| YAML 配置远程端点 | ✅ | `agent-runtime.remote-agents[N].url` |
| Agent Card 自动拉取 | ✅ | 启动时拉取，自适应刷新 |
| 本地目录维护 | ✅ | sticky remoteAgentId，故障降级 |
| RemoteAgentToolSpec 生成 | ✅ | 从 Card skills 生成，开放 JSON schema；**无 skills 的 Agent Card 不会被注入为 Tool** |
| OpenJiuwen Tool 安装 | ✅ | Placeholder Tool + Interrupt Rail |
| 远程 A2A 调用 | ✅ | `SendStreamingMessage`，独立 streaming |
| 中断-续接 | ✅ | 远程 INPUT_REQUIRED → 父 Task 挂起 → 用户输入 → 续写 |
| Metadata 转发 | ✅ | 入站 metadata → 出站远程调用 |
| 结果回灌 | ✅ | 远程 COMPLETED → InteractiveInput → 本地 Agent resume |
| 父 Task 进度投射 | ✅ | 远程 progress → 父 Task artifact |
| 取消级联传播 | ✅ | 父 Task cancel → 远程 CancelTask |
| 超时检测 | ✅ | REMOTE_TIMEOUT + 孤儿 Task cancel |
| 嵌套远程调用 | ⬜ | resume 后再次请求远程 → 返回错误 NESTED_REMOTE_INVOCATION_UNSUPPORTED |
| Graph/Parallel 编排 | ⬜ | 仅支持单层远程调用 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| 动态服务发现 | 远程端点必须通过 YAML 配置声明，不自动扫描网络 | — |
| 远程 Agent 负载均衡 | 不属于 agent-runtime 职责 | 在反向代理层实现 |
| 远程调用的认证 | A2A 认证属于协议层，不属于编排层 | 通过 A2A SDK 认证扩展 |

### 2.3 行为承诺

- **必须**：Card Cache 按配置 URL 维护，不发现新 URL
- **必须**：远程调用超时后 best-effort cancel 孤儿 Task
- **必须**：Card 刷新失败时保留上一次成功的 Card
- **禁止**：嵌套远程调用（resume 后再次请求远程 → 返回 NESTED_REMOTE_INVOCATION_UNSUPPORTED）
- **允许**：多个远程端点独立配置 stream-timeout

---

## 3. 核心实现

### 3.1 远程 Agent 配置接入

```
应用配置: agent-runtime.remote-agents[0].url=http://remote:18081
  │
  ▼ 启动时
A2aClientAutoConfiguration (条件激活)
  ├─ RemoteAgentCardCache: GET /.well-known/agent-card.json
  │     ├─ 解析: name → remoteAgentId, skills[].description → tool description
  │     └─ 自适应刷新: 10s 快速重试 → 600s 保活 → 指数退避 + 10% 抖动
  │
  ├─ RemoteAgentToolSpec 生成:
  │     remoteAgentId = "remote-planner"
  │     toolName = "a2a_remote_remote_planner"
  │     description = "Remote Planner\nPlans trips\nCreate a step-by-step plan"
  │     inputSchema = {"type":"object","properties":{"message":{"type":"string"}}}
  │
  └─ 故障降级: 不可达 → 标记 pending，不影响本地启动
```

**URL 归一化**：`http://host` / `http://host/` / `http://host/.well-known/agent-card.json` 归一化到同一入口。

**Agent Card 消费字段**：仅消费 `name`/`description`/`skills[].description`/`supportedInterfaces[].url`/`url`。不消费 `skills[].id/name/tags`（一个远端 Card 最多生成一个 Tool）。

> **⚠️ 关键约束：没有 skills 的 Agent Card 不会被 LLM 作为 Tool 调用。** 如果远端 Agent Card 的 `skills` 字段为空或不存在，Card Cache 不会为其生成 `RemoteAgentToolSpec`，该 Agent 对 LLM 不可见。这意味着：
> - 如果你的 Agent 需要被其他 Agent 作为 Tool 调用，必须在 Agent Card 中声明至少一个 skill
> - 仅用于直接 A2A 调用的 Agent（不需要被其他 Agent 发现的）可以不声明 skills

### 3.2 远程调用管道

```
本地 Agent 执行中 → LLM 调用远程 Tool
  │
  ▼
OpenJiuwenRemoteAgentInterruptRail.beforeToolCall()
  ├─ 检测 toolName 匹配远程 Agent
  ├─ 创建 InterruptRequest context:
  │     runtime.remote.kind = REMOTE_AGENT_INVOCATION
  │     runtime.remote.agentId = "remote-planner"
  │     runtime.remote.toolName = "a2a_remote_remote_planner"
  │     runtime.remote.toolCallId = "tool-call-1"
  │     runtime.remote.arguments = {"message":"hello remote"}
  │
  └─ → AgentExecutionResult.interrupted(remoteInvocation)
  │
  ▼ A2A 层
A2aRemoteInvocationOrchestrator
  ├─ outbound: invokeRemoteAgent()
  │     └─ A2aRemoteAgentOutboundAdapter: POST /a2a SendStreamingMessage
  │           message.role = ROLE_USER
  │           message.parts[0].text = toolArgs.message
  │
  ├─ 远程返回 ArtifactUpdate → A2aParentTaskProjector 投射到父 Task
  ├─ 远程返回 COMPLETED → 提取 text → toolResult
  ├─ 远程返回 INPUT_REQUIRED → 父 Task 挂起，metadata 保存 route
  │
  └─ cancel: 级联 CancelTask 到远程
```

#### 远端结果映射

| 远端事件 | 条件 | 本地结果 |
|---------|------|---------|
| `ArtifactUpdate` / `Message` | text 非空 | progress → 父 Task artifact |
| `TaskStatusUpdate` | COMPLETED | toolResult = TextPart 文本 |
| `TaskStatusUpdate` | INPUT_REQUIRED | 父 Task → INPUT_REQUIRED + metadata |
| `TaskStatusUpdate` | 其他 final state | toolResult = error JSON |
| 超时 | 超过 stream-timeout | `{"error":"remote A2A stream timed out","code":"REMOTE_TIMEOUT"}` |

### 3.3 中断-续接流程

```
第一轮:
  用户 → 主 Agent → LLM 调用远程 Tool → INTERRUPTED
    → 远程 SendStreamingMessage → 远程返回 INPUT_REQUIRED
    → 父 Task: INPUT_REQUIRED
      metadata: runtime.waitingTarget = REMOTE_AGENT
                runtime.remoteTaskId = "remote-task-1"
                runtime.remoteContextId = "remote-ctx-1"

第二轮:
  用户输入 → 本地 /a2a（同 parent task）
    → A2aAgentExecutor 识别 runtime.waitingTarget = REMOTE_AGENT
    → 直接调远端（不经本地 LLM）:
        message.taskId = "remote-task-1"
        message.contextId = "remote-ctx-1"
        message.parts[0].text = 用户补充输入
    → 远程 COMPLETED → toolResult = "remote answer"
```

### 3.4 结果回灌

```
远程 COMPLETED → toolResult = "remote answer"
  │
  ▼
A2aParentTaskProjector 构造 AgentExecutionContext:
  inputType = REMOTE_RESUME
  variables = {
    runtime.remoteToolCallId: "tool-call-1",
    runtime.remoteToolResult: "remote answer"
  }
  │
  ▼
OpenJiuwenMessageAdapter → InteractiveInput
  interactiveInput.update("tool-call-1", "remote answer")
  │
  ▼
OpenJiuwen Runner (resume 模式):
  tool call → tool result pair 注入 LLM 上下文
  LLM 继续推理 → answer → parent task COMPLETED
```

**结束条件**：远端 completed 只代表远端 tool leg 结束。parent task 的最终结束由本地 OpenJiuwen resume 后的结果决定：
- `result_type=answer` → parent COMPLETED
- `result_type=interrupt` (REMOTE_AGENT_INVOCATION) → 嵌套调用 → FAILED (NESTED_REMOTE_INVOCATION_UNSUPPORTED)
- `result_type=interrupt` (其他) → parent INPUT_REQUIRED

---

## 4. 代码结构

### 4.1 包结构

```
engine/a2a/
├── RemoteAgentProperties.java              # YAML 配置属性
├── RemoteAgentCardCache.java               # 远程 Agent Card 缓存（volatile snapshot）
├── RemoteAgentInvocationService.java       # 远程调用服务层
├── A2aRemoteAgentOutboundAdapter.java      # A2A JSON-RPC 出站传输适配器
├── A2aRemoteInvocationOrchestrator.java    # 远程调用编排引擎
├── A2aParentTaskProjector.java             # 父 Task 进度投射
├── A2aClientAutoConfiguration.java         # 条件自动装配（按 remote-agents[0].url 激活）

engine/openjiuwen/
├── OpenJiuwenRemoteAgentInterruptRail.java # 拦截远程 Tool → 创建 InterruptRequest
└── OpenJiuwenRemoteToolInstaller.java      # 安装远程 Tool 到 OpenJiuwen Agent

engine/spi/
└── RemoteAgentToolSpec.java                # 协议中立的远程 Tool 描述
```

### 4.2 核心类静态关系

```
RemoteAgentCardCache
      │
      ├── 拉取 Agent Card ──→ RemoteAgentToolSpec
      │                              │
      │                              ▼
      │                   OpenJiuwenRemoteToolInstaller
      │                              │
      │                              ▼ install
      │                   OpenJiuwen Agent
      │
      ▼
A2aRemoteAgentOutboundAdapter  ←── RemoteAgentInvocationService
      │
      ▼ 调用
A2aRemoteInvocationOrchestrator
      │
      ├── outbound: invoke remote
      ├── inbound: A2aParentTaskProjector → parent task
      └── resume: re-enter local handler
```

---

## 5. 运行流程

### 5.1 主流程

主流程由第 3 章各子特性的内部实现流程描述；本章只补充跨流程的错误、取消和降级语义，避免重复外部用户场景。

### 5.2 分支流程

分支流程按第 3 章中的状态流转、数据流或 adapter 分支处理。涉及外部调用方式的黑盒场景不在 L2 展开。

### 5.3 错误、取消、降级处理

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---------|---------|------|---------|
| Card 初次解析失败 | URL 不可达或返回非 Card | URL 保持 pending，不注入 tool | 本地 Agent 正常启动（无该远程 tool） |
| Card 后续刷新失败 | 已 available 的远端响应失败 | 保留上次成功 Card | 远程 tool 继续可用 |
| 远程超时 | 超过 stream-timeout | REMOTE_TIMEOUT → child error | toolResult = `{"error":"REMOTE_TIMEOUT"}` |
| 远程返回 FAILED | 远端 Agent 执行失败 | error 投射到父 Task | 父 Task 继续（LLM 看到 error toolResult） |
| 父 Task 取消 | 用户 CancelTask | best-effort cancel 远程 Task | 远程 Task 可能仍在后台执行 |
| 远端 late event | terminal/timeout 后到达 | 丢弃，不投影 | 不影响父 Task |
| 嵌套远程调用 | resume 后 LLM 再次请求远程 | 返回 NESTED_REMOTE_INVOCATION_UNSUPPORTED | parent task FAILED |
| Card Cache 全空 | 所有 URL 不可达 | 不安装任何远程 tool | 本地 Agent 正常运行（无远程 tool） |

---

## 6. 配置使用

### 6.1 完整配置示例

```yaml
agent-runtime:
  remote-agents:
    - url: http://weather-agent:18081
      stream-timeout: 30s
      output:
        default-target: USER
        completion-target: LLM
    - url: http://hotel-agent:18082
      stream-timeout: 60s
```

### 6.2 配置属性表

| 属性路径 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `agent-runtime.remote-agents[N].url` | String | — | 远程 Agent base URL（必填以激活） |
| `agent-runtime.remote-agents[N].stream-timeout` | Duration | — | 流式调用超时 |
| `agent-runtime.remote-agents[N].output.default-target` | String | — | 默认输出目标（USER / LLM / BOTH） |
| `agent-runtime.remote-agents[N].output.completion-target` | String | — | 完成时输出目标 |

---

## 7. 当前限制

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| 仅单层远程调用 | 不支持 Agent A → Agent B → Agent C 的链式调用 | 每层独立配置 |
| 不支持嵌套调用 | resume 后不能再次请求远程 | LLM 应一次性输出所有需要的 tool call |
| 远程端点需静态配置 | 新增远程 Agent 需修改 YAML 并重启 | — |
| 仅 OpenJiuwen 支持远程 Tool | AgentScope 不能作为调用方发起远程调用 | 使用 OpenJiuwen 作为主 Agent |
| 无 Graph/Parallel 编排 | 不支持多个远程 Agent 并行调用 | 按 LLM 决定的顺序依次调用 |
