# agent-runtime 开发指导文档

按特性分类的开发参考文档，每份文档独立成文，按需阅读。

## 文档目录

| 文档 | 阅读场景 |
|------|---------|
| [handler-spi.md](handler-spi.md) | 实现自定义 AgentRuntimeHandler、选择适配器 |
| [openjiuwen-adapter.md](openjiuwen-adapter.md) | 挂载 openJiuwen ReActAgent |
| [agentscope-adapter.md](agentscope-adapter.md) | 挂载 AgentScope Agent |
| [versatile-adapter.md](versatile-adapter.md) | 通过 REST 代理接入远端 Agent、编写 SKILL.md |
| [a2a-endpoints.md](a2a-endpoints.md) | A2A 协议调用：通讯模式、Methods、Java SDK 客户端 |
| [agent-card-configuration.md](agent-card-configuration.md) | 配置 A2A Agent 发现卡片（skills/capabilities） |
| [memory-services.md](memory-services.md) | MemoryProvider SPI 与 OpenJiuwen 记忆集成 |
| [state-persistence.md](state-persistence.md) | Agent 执行状态 Checkpoint 持久化 |
| [remote-invocation.md](remote-invocation.md) | Agent 通过 A2A 协议调用其他 Agent 作为工具 |
| [trajectory-observability.md](trajectory-observability.md) | 执行轨迹记录与敏感信息掩码 |
| [operations-guide.md](operations-guide.md) | 生命周期管理、健康检查、日志诊断、嵌入式部署 |
| [configuration-properties.md](configuration-properties.md) | 全部 application.yaml 配置项与行为说明 |

## 按任务速查

| 我想... | 阅读 |
|--------|------|
| 创建新的 Agent 适配器 | [handler-spi.md](handler-spi.md) → 对应框架文档 |
| 挂载 openJiuwen Agent | [openjiuwen-adapter.md](openjiuwen-adapter.md) |
| 挂载 AgentScope Agent | [agentscope-adapter.md](agentscope-adapter.md) |
| 代理远端 REST Agent | [versatile-adapter.md](versatile-adapter.md) |
| 用 curl 调用 Agent | [a2a-endpoints.md](a2a-endpoints.md) |
| 用 Java SDK 调用 Agent | [a2a-endpoints.md](a2a-endpoints.md) §5 |
| Agent 调用其他 A2A Agent | [remote-invocation.md](remote-invocation.md) |
| 添加会话记忆 | [memory-services.md](memory-services.md) |
| 持久化 Agent 状态 | [state-persistence.md](state-persistence.md) |
| 理解轨迹事件 | [trajectory-observability.md](trajectory-observability.md) |
| 修改 AgentCard 名称/描述 | [agent-card-configuration.md](agent-card-configuration.md) |
| 编写 SKILL.md 引导 LLM | [versatile-adapter.md](versatile-adapter.md) §SKILL.md 最佳实践 |
| 查找配置项 | [configuration-properties.md](configuration-properties.md) |
| 配置健康检查/优雅停机 | [operations-guide.md](operations-guide.md) |

## 相关

- 快速开始示例：`examples/agent-runtime-openjiuwen-simple/`
- 设计文档：`architecture/docs/L2/agent-runtime/`
- 发布特性清单：`architecture/docs/L1/agent-runtime/features/agent-runtime-release-features.cn.md`
