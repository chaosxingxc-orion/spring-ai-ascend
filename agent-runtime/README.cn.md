# agent-runtime

`agent-runtime` 是运行时代理托管 SDK，通过标准 A2A 接口暴露 Agent。它将执行引擎、运行生命周期、调度、会话/任务控制、内部事件队列和可启动的 Spring Boot 应用集成到一个模块中。

## 什么是 runtime

`agent-runtime` 提供：

- 可启动的 Spring Boot 运行时应用
- 运行时引擎适配器和执行流程
- 北向 A2A 接入层
- 会话、任务和输出的共享运行时管线

## 与 agent-service 的边界

`agent-runtime` 管理运行中的 Agent 进程及其 A2A 接入面。`agent-service` 是 `agent-runtime` 的下游服务化门面。

## 安装

当 reactor 外的模块需要依赖时，先安装到本地 Maven 仓库：

```bash
./mvnw -pl agent-runtime -am -DskipTests install
```

## 快速开始

最简集成示例在：

- `examples/agent-runtime-openjiuwen-simple` — 三步集成，无额外依赖，推荐入门

完整端到端示例：

- `examples/agent-runtime-a2a-llm-e2e`

## 启动入口

`agent-runtime` 以**库**形式发布：

```java
try (RunningRuntime runtime = RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(8080))) {
    // 正在 8080 端口提供 A2A 服务
}
```

启动示例应用：

```bash
export SAA_SAMPLE_LLM_API_KEY=sk-x00550472
./mvnw -f examples/agent-runtime-openjiuwen-simple/pom.xml spring-boot:run
```

## A2A 端点

### Agent 发现

- `GET /.well-known/agent-card.json`
- `GET /.well-known/agent.json`

### JSON-RPC 端点

- `POST /a2a`
- `POST /a2a/`

支持的 JSON-RPC 方法：

| 方法 | 说明 |
|---|---|
| `SendMessage` / `message/send` | 非流式消息 |
| `SendStreamingMessage` / `message/stream` | 流式消息 (SSE) |
| `GetTask` / `tasks/get` | 查询任务状态 |
| `CancelTask` / `tasks/cancel` | 取消任务 |
| `ListTasks` / `tasks/list` | 列出任务 |
| `SubscribeToTask` | 订阅任务事件 |

## Java 扩展点

| 类型 | 包 | 用途 |
|---|---|---|
| `AgentRuntimeHandler` | `engine.spi` | 核心 SPI，运行 Agent |
| `AgentCardProvider` | `engine.spi` | 可选的 A2A AgentCard 元数据 |
| `MemoryProvider` | `engine.spi` | 内存初始化/搜索/保存 SPI |
| `OpenJiuwenAgentRuntimeHandler` | `engine.openjiuwen` | openJiuwen Agent 基类 |
| `AgentScopeAgentRuntimeHandler` | `engine.agentscope` | AgentScope SDK Agent 基类 |
| `VersatileAgentRuntimeHandler` | `engine.versatile` | 通用 REST 代理 |

## 配置

```yaml
agent-runtime:
  access:
    a2a:
      default-tenant-id: sample-tenant
      default-agent-id: openjiuwen-simple-agent
      public-base-url: https://agents.example.com/runtime  # 反向代理场景

      agent-card:              # 可选，不配则自动生成
        name: my-agent
        description: 我的 Agent
        version: "1.0"

sample:
  openjiuwen:
    model-provider: openai
    api-key: ${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}
    api-base: ${SAA_SAMPLE_OPENJIUWEN_API_BASE:http://localhost:4000/v1}
    model-name: ${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}
    ssl-verify: ${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:true}
```

环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SAA_SAMPLE_LLM_API_KEY` | `sk-local-placeholder` | LLM API Key |
| `SAA_SAMPLE_OPENJIUWEN_API_BASE` | `http://localhost:4000/v1` | LLM API 地址 |
| `SAA_SAMPLE_LLM_MODEL` | `gpt-5.4-mini` | 模型名 |
| `SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER` | `openai` | 模型提供商 |
| `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY` | `true` | SSL 证书校验 |

## 开发文档

按特性分类的开发指导文档见：

- [`docs/guides/`](docs/guides/) — 每个特性独立成文，AI 可读

| 文档 | 阅读场景 |
|---|---|
| [handler-spi.md](docs/guides/handler-spi.md) | 实现自定义 AgentRuntimeHandler |
| [openjiuwen-adapter.md](docs/guides/openjiuwen-adapter.md) | 挂载 openJiuwen Agent |
| [agent-card-configuration.md](docs/guides/agent-card-configuration.md) | 配置 Agent 发现卡片 |
| [a2a-endpoints.md](docs/guides/a2a-endpoints.md) | A2A JSON-RPC 协议面 |
| [configuration-properties.md](docs/guides/configuration-properties.md) | 全部 application.yaml 配置参考 |

## 运行测试

```bash
export SAA_SAMPLE_LLM_API_KEY=sk-x00550472
./mvnw -f examples/agent-runtime-openjiuwen-simple/pom.xml test
```

## 注意事项

- 示例模块在 root Maven reactor 之外，需先 `mvn install -pl agent-runtime -DskipTests`
- 控制台客户端是最快捷的手动冒烟方式
- 本地默认密钥 `sk-x00550472` 仅限本地开发使用
- 示例默认值不可用于生产环境
