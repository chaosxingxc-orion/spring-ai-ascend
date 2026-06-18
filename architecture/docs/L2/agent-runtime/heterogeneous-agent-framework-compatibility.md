# 异构 Agent 框架兼容 — 设计文档

> 适用目录：`architecture/docs/L2/agent-runtime/`
> 目标模块：`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/`
> 最后更新：2026-06-14

---

## 1. 概述

### 1.1 特性定位

agent-runtime 通过统一的 Adapter 抽象层接入不同类型的 Agent 实现（OpenJiuwen / AgentScope / Versatile），使上层 A2A 协议层无需感知底层 Agent 框架差异。

- **解决的问题**：不同 Agent 框架有不同的 API、执行模型和扩展机制。runtime 将它们统一为 `AgentRuntimeHandler` SPI，使得 A2A 协议层以相同方式调用任意 Agent。
- **适用场景**：需要在一个 runtime 实例中托管多种框架构建的 Agent，或需要为 Agent 框架提供统一的 A2A 协议暴露能力。如果只需要单一框架且不需要 A2A 协议，不需要此特性。

### 1.2 核心设计原则

1. **SPI 优先** — 所有 Adapter 实现 `AgentRuntimeHandler`，runtime 核心只依赖 SPI，不依赖具体 Agent 框架
2. **模块隔离** — 每个 Adapter 在 `engine/<framework>/` 下自闭环，不穿越模块边界
3. **最小适配** — 新增 Agent 框架只需实现一个 Adapter + `StreamAdapter`，不修改 runtime 核心
4. **框架让步 runtime** — 当框架能力与 runtime 契约冲突时，adapter 负责弥合差异（如 OpenJiuwen 同步执行模型下 cancel 语义的适配）

### 1.3 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| Adapter 抽象层 | 定义统一的 Handler SPI 和公共类型 | `AgentRuntimeHandler`, `AgentExecutionResult`, `RuntimeIdentity` | ✅ |
| OpenJiuwen Adapter | 进程内调用 openJiuwen Agent，Rails 注入 | `OpenJiuwenAgentRuntimeHandler`, `AgentRail` | ✅ |
| AgentScope Adapter | 进程内/远程调用 AgentScope Agent | `AgentScopeAgent`, `AgentScopeRuntimeClient` | ✅ |
| Versatile Adapter | REST/SSE 代理远端非 A2A 服务 | `VersatileAgentRuntimeHandler`, `VersatileProperties` | ✅ |

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| 统一 SPI — Handler 执行 | ✅ | `AgentRuntimeHandler.execute(context)` 返回 `Stream<?>` |
| 统一 SPI — 结果适配 | ✅ | `StreamAdapter` 将框架原生结果转为 `AgentExecutionResult` |
| 统一 SPI — 取消 | ✅ | `cancel(taskId)` 默认 no-op，各 Adapter 按自身能力实现 |
| 统一公共类型 | ✅ | `RuntimeIdentity`（租户/用户/会话/任务/Agent ID）、`RuntimeMessage`（角色+文本+元数据） |
| OpenJiuwen — 进程内执行 | ✅ | 通过 `Runner.runAgent()` 同步调用，结果包装为 Stream |
| OpenJiuwen — Rails 注入 | ✅ | 轨迹追踪、远程工具中断、记忆注入三种 Rail |
| OpenJiuwen — Checkpoint | ✅ | InMemory / SQLite，通过 `CheckpointerFactory` 全局配置 |
| OpenJiuwen — 记忆集成 | ✅ | `MemoryRuntimeRail`（ReActAgent） + `ExternalMemoryRail`（harness 兼容） |
| OpenJiuwen — 远程工具安装 | ✅ | `OpenJiuwenRemoteToolInstaller` 自动发现并注册远端 A2A Agent 为本地 Tool |
| OpenJiuwen — Workflow | ⬜ | 仅支持 Core Agent（ReActAgent），不支持 Workflow |
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
| MCP 协议接入 | ⬜ | 当前仅支持 Java 进程内 + HTTP/SSE |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| DeepAgent 适配 | OpenJiuwen DeepAgent 类不继承 `BaseAgent`，无法返回自 `createOpenJiuwenAgent()` | 使用 ReActAgent |
| AgentScope Workflow | 仅支持 Core Agent | — |
| Python / Node.js sidecar | 非 Java 进程内调用不在当前 scope | 使用 Versatile Adapter 代理远端服务 |
| 多 Handler 路由 | runtime 当前只选取第一个 Handler（按 `@Order`），不支持按 agentId 路由多个 Handler | 每个 Agent 部署独立 runtime 实例 |

### 2.3 接口契约

#### AgentRuntimeHandler SPI

```java
/** Agent 托管的核心 SPI。实现此接口即可通过 A2A JSON-RPC 端点访问 Agent。 */
public interface AgentRuntimeHandler {
    /** 稳定唯一标识，必须与 A2A 路由键一致。 */
    String agentId();

    /** 健康检查闸门。每次执行前和健康端点调用。 */
    boolean isHealthy();

    /**
     * 核心执行方法。返回框架原生结果的 Stream。
     * @implSpec 实现者必须保证 Stream 在终端状态（COMPLETED/FAILED/INTERRUPTED）后关闭。
     */
    Stream<?> execute(AgentExecutionContext context);

    /** 将 execute() 的 Stream<?> 映射为 AgentExecutionResult 流。 */
    StreamAdapter resultAdapter();

    /** 取消进行中的执行。OpenJiuwen 同步执行模型下 cancel 仅阻止结果消费，不中断 LLM 调用。 */
    default void cancel(String taskId) {}

    default void start() {}
    default void stop() {}
}
```

#### AgentExecutionResult

| 类型 | 关键字段 | 含义 | A2A 映射 |
|------|---------|------|---------|
| `OUTPUT` | `outputContent()` | 增量输出 | → A2A Artifact (TextPart) |
| `COMPLETED` | `outputContent()` | 执行完成 | → emitter.complete() |
| `FAILED` | `outputContent()` | 执行失败 | → emitter.fail() + 结构化错误 |
| `INTERRUPTED` | `interruptPayload()` | 中断等待输入 | → Task → INPUT_REQUIRED |

InterruptPayload 是 sealed type：`UserInputInterrupt`（展示 prompt 等待用户输入）或 `RemoteAgentInterrupt`（触发远端 Agent 调用）。

#### 公共类型

| 类型 | 字段 | 约束 |
|------|------|------|
| `RuntimeIdentity` | `tenantId`, `userId`, `sessionId`, `taskId`, `agentId` | `tenantId`/`userId`/`sessionId`/`agentId` 非空 |
| `RuntimeMessage` | `Role(USER\|AGENT)`, `text`, `metadata` | — |

#### 行为承诺

- **必须**：所有 Adapter 的 `execute()` 返回的 Stream 必须由 `resultAdapter()` 映射为 `AgentExecutionResult` 流
- **必须**：`agentId()` 返回值在整个生命周期中保持不变
- **禁止**：Adapter 实现不可以直接依赖 A2A SDK 类型——所有交互通过 `AgentExecutionContext` 和 `AgentExecutionResult`
- **允许**：Adapter 可以按需选择使用哪些中间件能力（Memory、Checkpoint），不强制全部使用

---

## 3. 模块结构

### 3.1 包结构

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
│   ├── OpenJiuwenMessageAdapter.java       # AgentExecutionContext → openJiuwen 输入
│   ├── OpenJiuwenStreamAdapter.java        # Runner 结果 → AgentExecutionResult
│   ├── OpenJiuwenTrajectoryRail.java       # 模型/工具回调 → 轨迹事件
│   ├── OpenJiuwenRemoteAgentInterruptRail.java  # 拦截远端 Tool → 中断
│   ├── MemoryRuntimeRail.java              # beforeInvoke/afterInvoke → 记忆注入
│   ├── OpenJiuwenRemoteToolInstaller.java  # 安装远端 Tool
│   └── OpenJiuwenCheckpointerConfigurer.java   # Checkpointer 全局配置
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

### 3.2 核心类静态关系

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

## 4. 核心设计

### 4.1 Adapter 抽象层

#### 4.1.1 执行流程

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

#### 4.1.2 AbstractAgentRuntimeHandler 轨迹包装

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

### 4.2 OpenJiuwen Adapter

#### 4.2.1 执行模型

`Runner.runAgent(agent, input, conversationId, null)` 是同步阻塞调用。结果完全计算后才包装为 Stream。

**关键约束**：`cancel(taskId)` 仅关闭 Stream 阻止结果消费，不中断进行中的 LLM 调用。对于需要真正中断能力的场景，使用 AgentScope 或 Versatile Adapter。

#### 4.2.2 Rails 注入流程

```
Agent 创建
  │
  ├─ MemoryRuntimeRail.beforeInvoke()
  │     └─ memoryProvider.search() → 注入 system prompt
  ├─ OpenJiuwenRemoteAgentInterruptRail (注册为 BaseInterruptRail)
  │     └─ LLM 调用远端 Tool 时 → 抛出 InterruptRequest
  ├─ OpenJiuwenTrajectoryRail
  │     ├─ beforeModelCall → MODEL_CALL_START
  │     ├─ afterModelCall  → MODEL_CALL_END (含 tokens/latency/reasoning)
  │     ├─ beforeToolCall  → TOOL_CALL_START
  │     ├─ afterToolCall   → TOOL_CALL_END
  │     └─ onError         → ERROR
  │
  ▼
Runner.runAgent() — 同步执行
  │
  ├─ MemoryRuntimeRail.afterInvoke()
  │     └─ memoryProvider.save()
  │
  ▼
OpenJiuwenStreamAdapter: Runner 结果 → AgentExecutionResult
  ├─ result_type=answer     → COMPLETED
  ├─ result_type=interrupt  → RemoteInvocation 或 INTERRUPTED
  └─ 其他                    → FAILED
```

#### 4.2.3 Checkpoint 配置

```java
// 全局配置，所有 OpenJiuwen Agent 生效
OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
// 或
Checkpointer custom = new MySqliteCheckpointer();
OpenJiuwenCheckpointerConfigurer.setDefault(custom);
```

`conversation_id` = `AgentExecutionContext.agentStateKey`。OpenJiuwen 框架按此 key 自动 save/restore。

### 4.3 AgentScope Adapter

#### 4.3.1 三种模式的数据流

```
本地 Agent:    AgentScopeAgent.streamEvents(invocation) → Stream<AgentScopeEvent>
Harness Agent: AgentScopeHarnessAgent.streamEvents(invocation) → Stream<AgentScopeEvent>
远程客户端:    HTTP POST → SSE 帧 → SseEventDecoder → AgentScopeEvent 流
```

#### 4.3.2 错误码映射

`AgentScopeStreamAdapter` 将 AgentScope 的错误码自动映射到 runtime 的 `RuntimeErrorCode` 分类体系。映射规则：解析 AgentScope 原生事件中的错误字段，walk-the-cause-chain 匹配已知模式（如超时 → TIMEOUT、不可达 → UPSTREAM_UNAVAILABLE），未知错误归为 INTERNAL。映射后的错误码通过 `AgentExecutionResult.FAILED` 返回，A2A 层据此构造结构化错误载荷（code / message / retryable）。

#### 4.3.3 轨迹事件覆盖

`AbstractAgentScopeRuntimeHandler` 支持的事件类型：RUN_START/END、TOOL_CALL_START/END、ERROR、PROGRESS。比 OpenJiuwen 多 PROGRESS（AgentScope 原生产出增量事件），少 MODEL_CALL（AgentScope 不暴露模型调用回调）。

### 4.4 Versatile Adapter

#### 4.4.1 类协作

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

#### 4.4.2 SSE 事件映射

| SSE `event` | 条件 | `AgentExecutionResult` | Target |
|-------------|------|------------------------|--------|
| `message` | text/summary 非空 | `OUTPUT(text)` | USER |
| `message` | `node_type=End` | 标记 hasEnd | — |
| `workflow_finished` | — | `COMPLETED(cache)` | LLM |
| `end` | hasEnd=true | `COMPLETED(cache/extracted)` | LLM |
| `end` / `connection_closed` | hasEnd=false | `INTERRUPTED("")` | USER |
| `exception` | — | `FAILED(code, msg)` | BOTH |
| `workflow_started` / `node_started` / `node_finished` | — | 过滤 | — |

#### 4.4.3 Header 三级优先级

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 低 | `versatile.headers` (YAML) | 部署时预设 |
| 中 | A2A flat metadata（passthrough allowlist） | `metadata.{key}` 在白名单内透传 |
| 高 | `metadata.versatile.headers`（structured） | 用户显式指定，allowlist 控制 |

#### 4.4.4 自动工具注入

```
Versatile 子 Agent 启动 → AgentCard (含 skills) → A2A SDK 暴露
  │
主 Agent 配置: agent-runtime.remote-agents[0].url = <子 Agent URL>
  │
RemoteAgentCardCache 拉取子 Agent Card
  │
OpenJiuwenRemoteToolInstaller: Skill → OpenJiuwen Tool
  │
LLM 调用 Tool → OpenJiuwenRemoteAgentInterruptRail 拦截 → 中断-续接
```

---

## 5. 配置模型

### 5.1 完整配置示例

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
    checkpointer: in-memory

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

### 5.2 配置属性表

OpenJiuwen 和 AgentScope 通过各自的 `@ConfigurationProperties` 类注入，无固定 runtime 前缀。Versatile 有固定前缀 `versatile.*`。

### 5.3 配置类

Adapter 通过 Spring `ObjectProvider<AgentRuntimeHandler>` 自动发现 Handler Bean。无需额外配置注册步骤。

---

## 6. 对外呈现 / 用户场景

### 6.1 外部接口

| API | 说明 |
|-----|------|
| `AgentRuntimeHandler SPI` | 开发者实现此接口接入 Agent 框架 |
| `GET /.well-known/agent-card.json` | A2A 客户端发现 Agent 能力 |
| `POST /a2a` | A2A JSON-RPC 入口，所有 Adapter 统一通过此端点访问 |

### 6.2 用户示例

#### 6.2.1 挂载 OpenJiuwen Agent（三步）

```java
// Step 1: 继承 OpenJiuwenAgentRuntimeHandler
public class MyHandler extends OpenJiuwenAgentRuntimeHandler {
    public MyHandler() { super("my-agent-id"); }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
        ReActAgent agent = new ReActAgent(AgentCard.builder().id("my-agent-id").build());
        ReActAgentConfig config = ReActAgentConfig.builder()
            .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
            .maxIterations(5)
            .build()
            .configureModelClient("openai", apiKey, apiBase, modelName, true);
        agent.configure(config);
        return agent;
    }
}

// Step 2: 注册为 Spring Bean
@Bean OpenJiuwenAgentRuntimeHandler myHandler() { return new MyHandler(); }

// Step 3: 启动
// 预期：runtime 自动生成 AgentCard，暴露 A2A 端点
```

#### 6.2.2 通过 Versatile 代理远端 REST 服务

```yaml
versatile:
  url: http://workflow-engine:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
  url-variables:
    project_id: my-project
    agent_id: my-agent
  result-extractions:
    - match: booking_success
      get: ticket
```

调用方通过标准 A2A `SendStreamingMessage` 发送请求，text 承载 `{"inputs":{...}}`，Versatile Adapter 自动转换为 REST POST 并解析 SSE 响应。

### 6.3 E2E 流程

#### OpenJiuwen Agent 从请求到响应

```
A2A Client                    Runtime                     OpenJiuwen Agent
  │                              │                              │
  │── SendStreamingMessage ─────>│                              │
  │                              │── AgentExecutionContext ────>│
  │                              │                              │── Rails: 记忆注入
  │                              │                              │── Runner.runAgent()
  │                              │                              │── 模型调用 × N
  │                              │                              │── 工具调用 × M
  │                              │<──── result Map ────────────│
  │                              │── StreamAdapter: Map → AgentExecutionResult
  │<── SSE: OUTPUT+COMPLETED ───│                              │
```

#### Versatile 子 Agent 两轮交互

```
用户                    主 Agent (LLM)               Versatile 子 Agent
  │                         │                              │
  │── "订酒店" ────────────>│                              │
  │                         │── LLM 生成 tool call ───────>│
  │                         │                              │── REST POST
  │                         │<── SSE: hotels_info + HTTP关闭│
  │                         │── INTERRUPTED               │
  │<── "请选择酒店" ───────│                              │
  │                         │                              │
  │── "希尔顿花园" ────────>│                              │
  │                         │── remote continuation ──────>│
  │                         │                              │── REST POST (同 conversation)
  │                         │<── SSE: booking_success+End ─│
  │                         │── extraction: ticket → COMPLETED
  │                         │── LLM 总结                   │
  │<── "预订成功..." ──────│                              │
```

---

## 7. 错误处理

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

---

## 8. 限制与待补

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| OpenJiuwen 同步执行，cancel 不中断 LLM 调用 | 需要真正取消能力的长时间 LLM 调用场景 | 使用 AgentScope 或 Versatile Adapter |
| OpenJiuwen 仅支持 Core Agent，不支持 Workflow | 需要多步 Workflow 的场景 | 使用 Versatile 代理 Workflow 引擎 |
| AgentScope 不支持 Checkpoint / Memory | 需要状态持久化或记忆的 AgentScope 场景 | 在 AgentScope 层自行实现 |
| 仅第一个 Handler（按 @Order）被选取 | 一个 runtime 实例只能服务一个 Agent | 每个 Agent 部署独立 runtime 实例 |
| DeepAgent 不支持 | DeepAgent 类不继承 BaseAgent | 使用 ReActAgent |
| MCP 协议未接入 | 无法通过 MCP 连接工具生态 | 通过 A2A 远程 Agent 间接调用 |
