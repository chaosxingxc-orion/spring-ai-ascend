---
level: L2-LLD
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-002
status: active
dependency:
  - ../../L1-High-Level-Design/agent-runtime/README.md
  - ../../L1-High-Level-Design/agent-runtime/development.md
  - ../../L1-High-Level-Design/agent-runtime/process.md
  - ../../../version-scope/FEAT-002-heterogeneous-agent-framework-compatibility.md
---

# 异构 Agent 框架兼容 — 设计文档

> 目标模块：`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/`
> 最后更新：2026-06-14

---

## 1. 概述

### 1.1 特性定位

agent-runtime 通过统一的 Adapter 抽象层接入不同类型的 Agent 实现（OpenJiuwen / AgentScope / Versatile），使上层 A2A 协议层无需感知底层 Agent 框架差异。

- **解决的问题**：不同 Agent 框架有不同的 API、执行模型和扩展机制。runtime 将它们统一为 `AgentRuntimeHandler` SPI，使得 A2A 协议层以相同方式调用任意 Agent。
- **适用场景**：需要在一个 runtime 实例中托管多种框架构建的 Agent，或需要为 Agent 框架提供统一的 A2A 协议暴露能力。如果只需要单一框架且不需要 A2A 协议，不需要此特性。

### 1.2 当前事实边界

本文只描述 Feat-Func-002 在当前 `agent-runtime` 模块中的已接受实现事实。面向调用方的黑盒行为、用户场景和外部示例已迁移到 `version-scope/FEAT-002-heterogeneous-agent-framework-compatibility.md`；模块级 API/SPI、逻辑对象归属和部署资源模型以 L1 设计及其附录为准。

### 1.3 设计原则

1. **SPI 优先** — 所有 Adapter 实现 `AgentRuntimeHandler`，runtime 核心只依赖 SPI，不依赖具体 Agent 框架
2. **模块隔离** — 每个 Adapter 在 `engine/<framework>/` 下自闭环，不穿越模块边界
3. **最小适配** — 新增 Agent 框架只需实现请求桥接、调用执行和 `StreamAdapter` 结果归一，不修改 runtime 核心
4. **状态归属清晰** — runtime 拥有 task/session/state key、续接语义和 Task 终态；框架内部 checkpoint/cache payload 由框架或智能体开发者自治
5. **扩展机制自治** — 框架 hook、rail、tool、skill、middleware、callback 不属于异构 adapter 的治理范围

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| Adapter 抽象层 | 定义统一的 Handler SPI 和公共类型 | `AgentRuntimeHandler`, `AgentExecutionResult`, `RuntimeIdentity` | ✅ |
| OpenJiuwen Adapter | 进程内调用 OpenJiuwen ReActAgent / Workflow / DeepAgent，并归一结果语义 | `OpenJiuwenAgentRuntimeHandler`, `OpenJiuwenWorkflowAgentRuntimeHandler`, `OpenJiuwenDeepAgentRuntimeHandler` | ✅ |
| AgentScope Adapter | 进程内/远程调用 AgentScope Agent | `AgentScopeAgent`, `AgentScopeRuntimeClient` | ✅ |
| Versatile Adapter | REST/SSE 代理远端非 A2A 服务 | `VersatileAgentRuntimeHandler`, `VersatileProperties` | ✅ |

---

## 2. 特性规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| 统一 SPI — Handler 执行 | ✅ | `AgentRuntimeHandler.execute(context)` 返回 `Stream<?>` |
| 统一 SPI — 结果适配 | ✅ | `StreamAdapter` 将框架原生结果转为 `AgentExecutionResult` |
| 统一 SPI — 取消 | ✅ | `cancel(taskId)` 默认 no-op，各 Adapter 按自身能力实现 |
| 统一公共类型 | ✅ | `RuntimeIdentity`（租户/用户/会话/任务/Agent ID）、`RuntimeMessage`（角色+文本+元数据） |
| OpenJiuwen — ReActAgent | ✅ | 进程内调用 OpenJiuwen ReActAgent，结果包装为 Stream 并归一为 `AgentExecutionResult` |
| OpenJiuwen — Workflow | ✅ | 独立 Workflow adapter，支持 DAG 执行、人机交互中断和同 state key 续接调用 |
| OpenJiuwen — DeepAgent | ✅ | 独立 DeepAgent adapter，归一输出、失败和中断语义 |
| AgentScope — 本地 Agent | ✅ | 包装 `AgentScopeAgent` @FunctionalInterface |
| AgentScope — Harness Agent | ✅ | 测试/评估场景下的受控运行 |
| AgentScope — 远程 SSE 客户端 | ✅ | 通过 HTTP SSE 连接远程 AgentScope Runtime |
| AgentScope — 错误码映射 | ✅ | AgentScope 错误码自动映射到标准 ErrorCategory |
| AgentScope — Checkpoint | ⬜ | 未适配 |
| AgentScope — 记忆集成 | ⬜ | 未适配 |
| Versatile — REST 代理 | ✅ | A2A JSON-RPC → Versatile REST 双向转换 |
| Versatile — URL 模板 | ✅ | `{conversation_id}` + 自定义占位符替换 |
| Versatile — Header 透传 | ✅ | 三级优先级（YAML < flat metadata < structured metadata），allowlist 控制 |
| Versatile — 结果提取 | ✅ | match keyword → deep-find key 规则引擎 |
| Versatile — 中断检测 | ✅ | HTTP 流关闭无 End → INTERRUPTED |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| MCP 工具服务适配 | MCP 是工具服务协议，不是智能体框架 adapter | 若框架本身能调用 MCP，由框架或智能体开发者自治 |
| AgentScope Workflow | 仅支持 Core Agent | — |
| Python / Node.js sidecar | 非 Java 进程内调用不在当前 scope | 使用 Versatile Adapter 代理远端服务 |
| 多 Handler 路由 | runtime 当前只承载单 Agent；如果注册多个 Handler 会记录 WARN，并按 `@Order` 选取第一个作为兼容降级，不支持按 agentId 路由多个 Handler | 每个 Agent 部署独立 runtime 实例 |

### 2.3 行为承诺

- **必须**：所有 Adapter 的 `execute()` 返回的 Stream 必须由 `resultAdapter()` 映射为 `AgentExecutionResult` 流
- **必须**：`agentId()` 返回值在整个生命周期中保持不变
- **禁止**：Adapter 实现不可以直接依赖 A2A SDK 类型——所有交互通过 `AgentExecutionContext` 和 `AgentExecutionResult`

---

## 3. 核心实现

### 3.1 Adapter 抽象层

#### 3.1.1 执行流程

```
A2aAgentExecutor
  │
  ├─ handler.execute(context) → Stream<?> raw
  ├─ handler.resultAdapter().adapt(raw) → Stream<AgentExecutionResult>
  │
  └─ 逐个消费 AgentExecutionResult：
       ├─ OUTPUT       → emitter.addArtifact(TextPart)
       ├─ COMPLETED    → emitter.complete()
       ├─ FAILED       → emitter.fail()
       └─ INTERRUPTED  → task → INPUT_REQUIRED
```

#### 3.1.2 AbstractAgentRuntimeHandler 轨迹包装

```
子类 doExecute(context, trajectory)
  │
  ▼ (wrap in AbstractAgentRuntimeHandler.execute)
trajectory.emit(RUN_START)
  │
  ├─ Stream<?> raw = doExecute(context, trajectory)
  ├─ raw.onClose(() -> trajectory.emit(RUN_END))
  │
  └─ return raw (with trajectory lifecycle)
```

如果 `doExecute` 抛出异常，基类自动发射 ERROR 事件和 RUN_END。

### 3.2 OpenJiuwen Adapter

#### 3.2.1 执行模型

`Runner.runAgent(agent, input, conversationId, null)` 是同步阻塞调用。结果完全计算后才包装为 Stream。

**关键约束**：`cancel(taskId)` 仅关闭 Stream 阻止结果消费，不中断进行中的 LLM 调用。对于需要真正中断能力的场景，使用 AgentScope 或 Versatile Adapter。

#### 3.2.2 OpenJiuwen adapter 类型

| Adapter | 入口 | 适配职责 |
|---|---|---|
| ReActAgent | `OpenJiuwenAgentRuntimeHandler` | 调用 ReActAgent，归一输出、失败和中断语义。 |
| Workflow | `OpenJiuwenWorkflowAgentRuntimeHandler` | 调用 Workflow DAG，归一完成、失败和人机交互中断语义。 |
| DeepAgent | `OpenJiuwenDeepAgentRuntimeHandler` | 调用 DeepAgent，归一输出、失败和中断语义。 |

### 3.3 AgentScope Adapter

#### 3.3.1 三种模式的数据流

```
本地 Agent:    AgentScopeAgent.streamEvents(invocation) → Stream<AgentScopeEvent>
Harness Agent: AgentScopeHarnessAgent.streamEvents(invocation) → Stream<AgentScopeEvent>
远程客户端:    HTTP POST → SSE 帧 → SseEventDecoder → AgentScopeEvent 流
```

#### 3.3.2 错误码映射

`AgentScopeStreamAdapter` 将 AgentScope 的错误码自动映射到 runtime 的 `RuntimeErrorCode` 分类体系。映射规则：解析 AgentScope 原生事件中的错误字段，walk-the-cause-chain 匹配已知模式（如超时 → TIMEOUT、不可达 → UPSTREAM_UNAVAILABLE），未知错误归为 INTERNAL。映射后的错误码通过 `AgentExecutionResult.FAILED` 返回，A2A 层据此构造结构化错误载荷（code / message / retryable）。

#### 3.3.3 轨迹事件覆盖

`AbstractAgentScopeRuntimeHandler` 支持的事件类型：RUN_START/END、TOOL_CALL_START/END、ERROR、PROGRESS。比 OpenJiuwen 多 PROGRESS（AgentScope 原生产出增量事件），少 MODEL_CALL（AgentScope 不暴露模型调用回调）。

### 3.4 Versatile Adapter

#### 3.4.1 类协作

```
VersatileAgentRuntimeHandler.execute(context)
  │
  ├─ VersatileMessageAdapter.toRequest(context) → VersatileHttpRequest
  │     ├─ URL:   模板替换 {conversation_id} + query params
  │     ├─ Headers: 三级优先级（YAML < flat metadata < structured versatile.headers）
  │     └─ Body:   {"inputs":{...}} — text JSON 优先，variables 回退
  │
  ├─ VersatileClient.stream(request) → Stream<String> (lazy SSE lines)
  │
  └─ VersatileStreamAdapter.adapt(rawStream) → Stream<AgentExecutionResult>
        ├─ message/workflow_finished/exception/end → 标准映射
        ├─ 未知事件 → match/get 提取规则 → 缓存 → End 后 COMPLETED
        └─ connection_closed 无 End → INTERRUPTED
```

#### 3.4.2 SSE 事件映射

| SSE `event` | 条件 | `AgentExecutionResult` | Target |
|-------------|------|------------------------|--------|
| `message` | text/summary 非空 | `OUTPUT(text)` | USER |
| `message` | `node_type=End` | 标记 hasEnd | — |
| `workflow_finished` | — | `COMPLETED(cache)` | LLM |
| `end` | hasEnd=true | `COMPLETED(cache/extracted)` | LLM |
| `end` / `connection_closed` | hasEnd=false | `INTERRUPTED("")` | USER |
| `exception` | — | `FAILED(code, msg)` | BOTH |
| `workflow_started` / `node_started` / `node_finished` | — | 过滤 | — |

#### 3.4.3 Header 三级优先级

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 低 | `versatile.headers` (YAML) | 部署时预设 |
| 中 | A2A flat metadata（passthrough allowlist） | `metadata.{key}` 在白名单内透传 |
| 高 | `metadata.versatile.headers`（structured） | 用户显式指定，allowlist 控制 |

#### 3.4.4 远程工具与 skill 边界

远程 Agent 发现、工具安装、中断续接和 skill 到框架工具的转换属于 Feat-Func-004 远程 Agent 编排，不属于本特性的异构框架适配事实。Versatile adapter 在本特性内只承诺 REST/SSE 请求代理、响应解析和结果归一。

---

## 4. 代码结构

### 4.1 包结构

```
engine/
├── spi/                                    # 框架中立抽象层
│   ├── AgentRuntimeHandler.java            # 核心 SPI 接口
│   ├── AbstractAgentRuntimeHandler.java    # 基类，拥有轨迹生命周期
│   ├── AgentExecutionResult.java           # 统一执行结果（OUTPUT/COMPLETED/FAILED/INTERRUPTED）
│   ├── StreamAdapter.java                  # @FunctionalInterface：Stream<?> → Stream<AgentExecutionResult>
│   ├── MemoryProvider.java                 # 记忆服务 SPI
│   └── RemoteAgentToolSpec.java            # 协议中立的远端 Agent 工具描述
├── openjiuwen/                             # OpenJiuwen Adapter
│   ├── OpenJiuwenAgentRuntimeHandler.java  # 抽象基类，子类实现 createOpenJiuwenAgent()
│   ├── OpenJiuwenWorkflowAgentRuntimeHandler.java  # Workflow adapter 入口
│   ├── OpenJiuwenDeepAgentRuntimeHandler.java      # DeepAgent adapter 入口
│   ├── OpenJiuwenMessageAdapter.java       # AgentExecutionContext → openJiuwen 输入
│   ├── OpenJiuwenStreamAdapter.java        # Runner 结果 → AgentExecutionResult
│   └── 其他 OpenJiuwen hook/rail/tool/skill/checkpointer wiring 由框架或开发者自治，不作为本特性核心结构
├── agentscope/                             # AgentScope Adapter
│   ├── AbstractAgentScopeRuntimeHandler.java   # 基类
│   ├── AgentScopeAgent.java                # @FunctionalInterface
│   ├── AgentScopeAgentRuntimeHandler.java  # 本地 Agent
│   ├── AgentScopeHarnessRuntimeHandler.java    # Harness Agent
│   ├── AgentScopeRuntimeClientHandler.java     # 远程 SSE 客户端
│   ├── AgentScopeRuntimeClient.java        # HTTP SSE 客户端
│   ├── AgentScopeMessageAdapter.java       # AgentExecutionContext → AgentScopeInvocation
│   └── AgentScopeStreamAdapter.java        # 原始事件 → AgentExecutionResult
└── versatile/                              # Versatile Adapter
    ├── VersatileAgentRuntimeHandler.java   # Handler + AgentCardProvider
    ├── VersatileMessageAdapter.java        # A2A Request → REST Request
    ├── VersatileStreamAdapter.java         # SSE 行 → AgentExecutionResult
    ├── VersatileClient.java                # JDK HttpClient, SSE 流式读取
    ├── VersatileHttpRequest.java           # REST 请求值对象
    └── VersatileProperties.java            # @ConfigurationProperties
```

### 4.2 核心类静态关系

```
«interface»               «abstract»                   «concrete»
AgentRuntimeHandler        AbstractAgentRuntimeHandler   OpenJiuwenAgentRuntimeHandler
      ↑                          ↑                           ↑
      └─── implements ───────────┘                           │
                   └────── extends ──────────────────────────┘
                   └────── extends ──────> AbstractAgentScopeRuntimeHandler
                   └────── implements ───> VersatileAgentRuntimeHandler
                                                 ↑
                                       also implements AgentCardProvider
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
| Handler 执行异常 | `doExecute()` 抛出 RuntimeException | 基类发射 ERROR 轨迹事件 + RUN_END | `FAILED` result → emitter.fail() |
| 创建 Agent 失败 | `createOpenJiuwenAgent()` 返回 null | NPE → 同执行异常处理 | FAILED |
| OpenJiuwen Runner 异常 | 模型 API 不可达 | `Runner.runAgent()` 抛出异常，adapter 捕获 | `FAILED("OPENJIUWEN_RUN_ERROR")` |
| Versatile HTTP 超时 | 超过 `versatile.timeout` | `HttpTimeoutException` → 标记超时 | `FAILED("VERSATILE_TIMEOUT")` |
| Versatile HTTP 4xx/5xx | 远端返回错误 | 读取 error body | `FAILED("VERSATILE_HTTP_{code}")` |
| Versatile SSE 解析失败 | 某行 JSON 不合法 | 跳过该行，WARN 日志 | 该行丢弃 |
| Versatile 流关闭无 End | HTTP 连接断开 | 注入 `connection_closed` → 无 End → INTERRUPTED | INPUT_REQUIRED |
| OpenJiuwen cancel | `CancelTask` 到达 | 关闭 Stream 阻止消费 | Task → CANCELED（不中断 LLM 调用） |

### 5.4 行为边界

| 边界 | 行为约束 |
|---|---|
| 状态缓存归属边界 | Adapter 只传递 runtime state key / task / context 和调用生命周期信号，不读写、不配置、不治理框架 checkpointer/cache payload。runtime 任务状态缓存由 Feat-Func-003 定义；框架内部快照由框架或智能体开发者自治。 |
| 框架扩展机制自治边界 | Adapter 不安装、不编排、不治理框架 hook、rail、tool、skill、middleware、callback。这些机制由智能体框架提供，或由智能体开发者在构建 Agent 时自定义。 |
| MCP 工具服务边界 | MCP 是工具服务协议，不是异构智能体框架。若框架本身能调用 MCP 服务，该能力由框架或智能体开发者自治。 |

---

## 6. 配置使用

### 6.1 完整配置示例

```yaml
# agent-runtime 全局配置
agent-runtime:
  access:
    a2a:
      default-tenant-id: default
      default-agent-id: my-agent

# OpenJiuwen 配置（sample 前缀为示例项目约定，生产自定义）
sample:
  openjiuwen:
    model-provider: openai
    api-key: ${LLM_API_KEY}
    api-base: https://api.openai.com/v1
    model-name: gpt-5.4-mini
    ssl-verify: true

# Versatile 配置
versatile:
  url: http://host:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
  url-variables:
    project_id: mock_project_id
    agent_id: fb723468-c8ca-424b-a95f-a3e74b37e090
  query-params:
    type: controller
  timeout: 30s
  headers:
    content-type: application/json
    stream: "true"
  passthrough-headers:
    - x-language
  result-extractions:
    - match: hotel_book_success
      get: ticket
```

### 6.2 配置属性表

OpenJiuwen 和 AgentScope 通过各自的 `@ConfigurationProperties` 类注入，无固定 runtime 前缀。Versatile 有固定前缀 `versatile.*`。

### 6.3 配置类

Adapter 通过 Spring `ObjectProvider<AgentRuntimeHandler>` 自动发现 Handler Bean。无需额外配置注册步骤。

---

## 7. 当前限制

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| OpenJiuwen 同步执行，cancel 不中断 LLM 调用 | 需要真正取消能力的长时间 LLM 调用场景 | 使用 AgentScope 或 Versatile Adapter |
| 多 Handler 注册仅兼容降级选第一个（按 @Order） | 一个 runtime 实例只能服务一个 Agent；多 Handler 会记录 WARN | 每个 Agent 部署独立 runtime 实例 |
| MCP 不作为 Agent adapter | MCP 是工具服务协议，不是异构智能体框架 | 若框架本身能调用 MCP 服务，由框架或智能体开发者自治 |
