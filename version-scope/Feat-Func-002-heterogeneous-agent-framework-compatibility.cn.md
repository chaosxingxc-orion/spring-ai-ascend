---
scope: version
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-002
status: active
dependency:
  - agent-runtime-release-features.cn.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-002-heterogeneous-agent-framework-compatibility.md
---

# 异构 Agent 框架兼容 — 黑盒行为说明

## 1. 特性定位

agent-runtime 通过统一的 Adapter 抽象层接入不同类型的 Agent 实现（OpenJiuwen / AgentScope / Versatile），使上层 A2A 协议层无需感知底层 Agent 框架差异。

- **解决的问题**：不同 Agent 框架有不同的 API、执行模型和扩展机制。runtime 将它们统一为 `AgentRuntimeHandler` SPI，使得 A2A 协议层以相同方式调用任意 Agent。
- **适用场景**：需要在一个 runtime 实例中托管多种框架构建的 Agent，或需要为 Agent 框架提供统一的 A2A 协议暴露能力。如果只需要单一框架且不需要 A2A 协议，不需要此特性。

## 2. 对外能力边界

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

## 3. 外部行为与用户场景

### 3.1 外部接口

| API | 说明 |
|-----|------|
| `AgentRuntimeHandler SPI` | 开发者实现此接口接入 Agent 框架 |
| `GET /.well-known/agent-card.json` | A2A 客户端发现 Agent 能力 |
| `POST /a2a` | A2A JSON-RPC 入口，所有 Adapter 统一通过此端点访问 |

### 3.2 用户示例

#### 3.2.1 挂载 OpenJiuwen Agent（三步）

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

#### 3.2.2 通过 Versatile 代理远端 REST 服务

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

### 3.3 E2E 流程

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
