# agent-runtime 开发指南

这里按使用场景组织 `agent-runtime` 的开发参考文档。每份文档可以单独阅读。

## 文档目录

| 文档 | 阅读场景 |
|---|---|
| [handler-spi.md](handler-spi.md) | 实现自定义 `AgentRuntimeHandler`，理解 runtime handler 生命周期 |
| [openjiuwen-adapter.md](openjiuwen-adapter.md) | 托管 OpenJiuwen ReActAgent |
| [openjiuwen-deepagent-adapter.md](openjiuwen-deepagent-adapter.md) | 托管 OpenJiuwen DeepAgent |
| [openjiuwen-workflow-adapter.md](openjiuwen-workflow-adapter.md) | 托管 OpenJiuwen Workflow Agent（DAG + 中断/恢复） |
| [agent-sdk-openjiuwen-yaml.md](agent-sdk-openjiuwen-yaml.md) | 用 `agent-sdk` 从 `ascend-agent/v1` YAML 构造 OpenJiuwen ReActAgent / DeepAgent |
| [agentscope-adapter.md](agentscope-adapter.md) | 托管 AgentScope Agent |
| [versatile-adapter.md](versatile-adapter.md) | 通过 REST 代理接入远端 Agent、编写 `SKILL.md` |
| [a2a-endpoints.md](a2a-endpoints.md) | A2A 协议调用、通信模式、Java SDK 客户端 |
| [agent-card-configuration.md](agent-card-configuration.md) | 配置 A2A Agent 发现卡片、skills 和 capabilities |
| [memory-services.md](memory-services.md) | `MemoryProvider` SPI 与 OpenJiuwen 记忆集成 |
| [state-persistence.md](state-persistence.md) | Agent 执行状态与 checkpoint 持久化 |
| [mcp-tools.md](mcp-tools.md) | MCP 工具发现、调用与 OpenJiuwen 安装 |
| [skillhub.md](skillhub.md) | SkillHub 渐进式技能加载与 OpenJiuwen 安装 |
| [remote-invocation.md](remote-invocation.md) | Agent 通过 A2A 协议调用其他 Agent 作为工具 |
| [trajectory-observability.md](trajectory-observability.md) | 执行轨迹记录与敏感信息掩码 |
| [operations-guide.md](operations-guide.md) | 生命周期管理、健康检查、日志诊断、嵌入式部署 |
| [configuration-properties.md](configuration-properties.md) | `application.yaml` 配置项与行为说明 |

## 按任务速查

| 我想... | 阅读 |
|---|---|
| 创建新的 Agent 适配器 | [handler-spi.md](handler-spi.md) 加对应框架文档 |
| 托管 OpenJiuwen ReActAgent | [openjiuwen-adapter.md](openjiuwen-adapter.md) |
| 托管 OpenJiuwen DeepAgent | [openjiuwen-deepagent-adapter.md](openjiuwen-deepagent-adapter.md) |
| 托管 OpenJiuwen Workflow | [openjiuwen-workflow-adapter.md](openjiuwen-workflow-adapter.md) |
| 从 YAML 构造 OpenJiuwen Agent | [agent-sdk-openjiuwen-yaml.md](agent-sdk-openjiuwen-yaml.md) |
| 托管 AgentScope Agent | [agentscope-adapter.md](agentscope-adapter.md) |
| 代理远端 REST Agent | [versatile-adapter.md](versatile-adapter.md) |
| 用 curl 调用 Agent | [a2a-endpoints.md](a2a-endpoints.md) |
| 用 Java SDK 调用 Agent | [a2a-endpoints.md](a2a-endpoints.md) 第 5 节 |
| Agent 调用其他 A2A Agent | [remote-invocation.md](remote-invocation.md) |
| 添加会话记忆 | [memory-services.md](memory-services.md) |
| 持久化 Agent 状态 | [state-persistence.md](state-persistence.md) |
| 接入 MCP Server 工具 | [mcp-tools.md](mcp-tools.md) |
| 接入 SkillHub 技能目录 | [skillhub.md](skillhub.md) |
| 理解轨迹事件 | [trajectory-observability.md](trajectory-observability.md) |
| 修改 AgentCard 名称、描述或 skills | [agent-card-configuration.md](agent-card-configuration.md) |
| 编写 `SKILL.md` 引导 LLM | [versatile-adapter.md](versatile-adapter.md) 的 `SKILL.md` 实践 |
| 查找配置项 | [configuration-properties.md](configuration-properties.md) |
| 配置健康检查和优雅停机 | [operations-guide.md](operations-guide.md) |

## 相关

- 快速开始示例：`examples/agent-runtime-openjiuwen-simple/`
- DeepAgent A2A 示例：`examples/agent-runtime-a2a-openjiuwen-deepagent-e2e/`
- Workflow A2A 示例：`examples/agent-runtime-a2a-openjiuwen-workflow/`
- Agent SDK 示例：`examples/agent-sdk-example/`
- L2 设计文档：`architecture/docs/L2/agent-runtime/`
- 发布特性清单：`architecture/docs/L1/agent-runtime/features/agent-runtime-release-features.cn.md`
