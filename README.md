# spring-ai-ascend

`spring-ai-ascend` 是一个面向企业自托管场景的 Java/Spring AI Agent 平台实验分支。当前代码已经从早期的 `agent-service` / `agent-execution-engine` 命名过渡到更贴近架构语义的 `agent-runtime` / `agent-core`：前者承载可嵌入启动的 A2A Agent runtime，后者承载异构执行引擎与规划 SPI。

这个仓库不是一个单纯的样例工程。它同时维护三类事实：

- 代码事实：Maven module、SPI、测试、配置属性和可运行入口。
- 架构事实：`architecture/` 下的 L0/L1/L2 设计、模块边界、4+1 视图和接口附录。
- 版本范围：`version-scope/` 下的版本特性、功能点和 DFX 范围。

## 当前实现重点

当前 `experimental` 分支的核心落点是 `agent-runtime`。该目录已从 `main` 分支同步，提供一个框架中立的 Agent 托管 runtime SDK：

- 通过 `AgentRuntimeHandler` 与 `StreamAdapter` SPI 托管不同 Agent 框架。
- 通过 A2A SDK 暴露 Agent Card 与 JSON-RPC/SSE 调用入口。
- 使用 A2A SDK 的可替换内存 TaskStore 和事件队列承载任务生命周期。
- 将会话状态持久化交给被托管框架自己的 checkpointer。
- 支持远程 A2A Agent 作为工具调用、Agent Card 缓存、超时控制和健康细节展示。
- 提供轨迹观测、日志关联 MDC、OpenTelemetry span sink 等运行可观测能力。

`agent-core` 是从 `agent-execution-engine` 重命名后的执行核心模块。它保留 `com.huawei.ascend.engine.*` 包结构，承载：

- `ExecutorAdapter`、`GraphExecutor`、`AgentLoopExecutor` 等引擎侧 SPI。
- `EngineRegistry`、`EngineEnvelope` 与 in-process engine port 实现。
- `Planner`、`Plan`、`PlanStep`、`PlanningRequest`、`PlanningResult` 等规划 SPI。
- 对 `agent-bus` 中立执行模型 SPI 的消费，例如 `RunContext`、`SuspendSignal`、`ExecutorDefinition`。

## 模块结构

当前 reactor 模块如下：

| 模块 | 角色 | 当前状态 |
|---|---|---|
| `spring-ai-ascend-dependencies` | BoM | 管理本仓库模块与第三方依赖版本 |
| `agent-runtime` | A2A Agent runtime | 可嵌入 runtime SDK，托管 Agent 并暴露 A2A 接口 |
| `agent-core` | 执行核心 | 异构执行引擎、EngineRegistry、EngineEnvelope、Planner SPI |
| `agent-bus` | 总线与跨平面契约 | ingress、S2C callback、中立 engine SPI、forwarding runtime |
| `agent-middleware` | 中间件 SPI | Hook、模型网关、Skill、Memory、Vector、Retriever、Embedding 等中间层契约 |
| `agent-client` | 边缘访问 SDK | 客户端侧骨架与边界约束测试 |
| `agent-evolve` | 演进平面 | 慢速评估、在线演进等实验性 SPI 骨架 |

## 快速构建

```bash
./mvnw -T 1C -q clean install
```

只验证执行核心：

```bash
./mvnw -pl agent-core -am test -q
```

只验证 A2A runtime：

```bash
./mvnw -pl agent-runtime -am test -q
```

`agent-runtime` 作为库交付，模块 README 中的推荐嵌入方式是通过 `RuntimeApp.create(handler).run(...)` 启动 runtime host。更多 A2A endpoint、Agent Card、远程 Agent、MCP 工具、OpenJiuwen/AgentScope 适配和运维配置，见 `agent-runtime/docs/guides/`。

## agent-runtime 能做什么

`agent-runtime` 适合用在需要把某个 Agent 实现托管成标准 A2A 服务的场景：

- 启动一个 Agent 托管进程。
- 发布 `/.well-known/agent-card.json`。
- 通过 `/a2a` 和 `/a2a/` 接收 JSON-RPC 请求。
- 支持 `message/send`、`message/stream`、`tasks/get`、`tasks/cancel` 等调用。
- 通过配置声明远程 A2A Agent，让本地 Agent 把远端 Agent 当作工具使用。
- 在日志中关联 `contextId`、`taskId`、`tenantId`、`agentId`。

它不负责长期 Run 账本、崩溃恢复、幂等调度或企业服务化门面。这些能力属于更高层服务化方向或后续版本范围，不应塞回 runtime 托管层。

## 版本特性范围

`version-scope/` 是版本承诺的入口。当前目录包含：

- `agent-runtime-release-features.md`
- `FEAT-001-standardized-agent-service-entrypoint.md`
- `FEAT-002-heterogeneous-agent-framework-compatibility.md`
- `FEAT-003-agent-runtime-core-interface.md`
- `FEAT-004-middleware-memory-and-state.md`
- `FEAT-005-remote-agent-orchestration.md`
- `DFX-001-trajectory-observability.md`

这些文档描述“本版本要交付什么、以什么能力验收”。当版本范围和架构设计发生冲突时，应先更新或澄清架构事实，再落实现代码。

## 架构文档路径

架构事实从 `architecture/` 开始阅读：

1. `architecture/README.md`：说明架构事实、代码事实、需求材料之间的边界。
2. `architecture/L0-Top-Level-Design/`：系统级模块切分、全局约束、治理原则和术语。
3. `architecture/L1-High-Level-Design/`：模块级高阶设计，按 4+1 视图和 API/SPI 附录组织。
4. `architecture/L2-Low-Level-Design/`：特性级详细设计，描述内部实现逻辑、流程和限制。

`docs/` 中包含评审、归档、竞争分析、治理日志和历史材料。除非被 `architecture/` 显式提升或引用，它们是上下文，不是当前架构事实。

## 开发约定

- 模块身份以各目录下的 `module-metadata.yaml` 为准。
- Maven artifact 与目录命名当前采用 `agent-runtime` / `agent-core`。
- `agent-bus` 的中立 SPI 不能反向依赖 runtime 或 core。
- `agent-client`、`agent-evolve` 保持边界骨架，不直接穿透调用 compute/control 模块。
- 新功能优先通过 SPI、配置和 Spring Bean 接入，避免改动平台内部实现作为扩展方式。

## 推荐入口

- Runtime 概览：`agent-runtime/README.md`
- Runtime 指南索引：`agent-runtime/docs/guides/README.md`
- A2A endpoint：`agent-runtime/docs/guides/a2a-endpoints.md`
- Handler SPI：`agent-runtime/docs/guides/handler-spi.md`
- 远程 Agent 调用：`agent-runtime/docs/guides/remote-invocation.md`
- 轨迹观测：`agent-runtime/docs/guides/trajectory-observability.md`
- 架构事实入口：`architecture/README.md`
- 版本范围入口：`version-scope/agent-runtime-release-features.md`
