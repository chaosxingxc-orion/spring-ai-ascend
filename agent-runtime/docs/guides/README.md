# agent-runtime 开发指导文档

按特性分类的开发参考文档，每份文档独立成文，按需阅读。

## 使用方式

**AI Agent（Claude、Copilot 等）：** 在编写代码前，根据当前任务阅读对应文档。
每份文档定义了合约、模式和配置面。

**开发者：** 从快速开始示例（`examples/agent-runtime-openjiuwen-simple/`）
入手，需要了解某个特性的细节时再查阅对应文档。

## 文档目录

| 文档 | 阅读场景 |
|---|---|
| [adapter-overview.md](adapter-overview.md) | 了解 OpenJiuwen / AgentScope / Versatile 三种适配器及选型 |
| [handler-spi.md](handler-spi.md) | 实现自定义 AgentRuntimeHandler |
| [openjiuwen-adapter.md](openjiuwen-adapter.md) | 挂载 openJiuwen ReActAgent 或 DeepAgent |
| [versatile-adapter.md](versatile-adapter.md) | 通过 REST 代理接入远端 Agent 服务 |
| [remote-invocation.md](remote-invocation.md) | Agent 通过 A2A 协议调用其他 Agent 作为工具 |
| [communication-models.md](communication-models.md) | 同步/流式/异步三种通讯模式及响应类型 |
| [middleware-services.md](middleware-services.md) | MemoryProvider SPI 与 Agent State 持久化 |
| [agent-card-configuration.md](agent-card-configuration.md) | 配置 A2A Agent 发现卡片 |
| [a2a-endpoints.md](a2a-endpoints.md) | A2A JSON-RPC 协议面：方法、SSE、错误码 |
| [configuration-properties.md](configuration-properties.md) | 全部 application.yaml 配置项参考 |

## 按任务速查

| 我想... | 阅读 |
|---|---|
| 了解适配器全景 | [adapter-overview.md](adapter-overview.md) |
| 创建新的 Agent 适配器 | [adapter-overview.md](adapter-overview.md) → [handler-spi.md](handler-spi.md) → 对应框架文档 |
| 挂载 openJiuwen Agent | [openjiuwen-adapter.md](openjiuwen-adapter.md) |
| 代理远端 REST Agent | [versatile-adapter.md](versatile-adapter.md) |
| Agent 调用其他 A2A Agent | [remote-invocation.md](remote-invocation.md) |
| 选择同步/流式/异步模式 | [communication-models.md](communication-models.md) |
| 理解 A2A 流式事件 | [communication-models.md](communication-models.md) → [a2a-endpoints.md](a2a-endpoints.md) |
| 添加会话记忆 | [middleware-services.md](middleware-services.md) |
| 持久化 Agent 状态 | [middleware-services.md](middleware-services.md#agent-state-persistence-checkpointer) |
| 修改 AgentCard 名称/描述 | [agent-card-configuration.md](agent-card-configuration.md) |
| 用 curl 调用 Agent | [a2a-endpoints.md](a2a-endpoints.md) |
| 查找配置项 | [configuration-properties.md](configuration-properties.md) |
| SSE 断线重连 | [a2a-endpoints.md](a2a-endpoints.md#tasksresubscribe-subscribetotask-reconnect) |

## 相关

- 快速开始示例：`examples/agent-runtime-openjiuwen-simple/`
- 模块 README：`agent-runtime/README.md`
- 架构文档：`architecture/docs/L0/ARCHITECTURE.md`
