---
level: L1-HLD
TAG:
  - overview
  - architecture-fact
  - module-boundary
status: active
dependency:
  - README.md
  - scenarios.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
  - ../../../version-scope/agent-runtime-release-features.cn.md
---

# agent-runtime L1 架构概览

## 目的

本文档给出 `agent-runtime` 模块的 L1 高阶心智模型，概述模块目标、受众边界、问题领域和模块边界形态。

本文档只描述 active 架构现状，不重复 L0 顶层设计中的架构原则和质量属性，也不展开类级清单、接口签名、配置项、测试矩阵、错误处理表或下一版本提案。

## 模块目标

`agent-runtime` 是可嵌入、可独立启动的 **task-owning runtime SDK**。它在 L0 边界下负责把外部 A2A 请求、Task 生命周期和异构 Agent 框架执行连接为一条可治理的运行时路径。

当前模块目标包括：

- 通过开源 A2A SDK 提供 JSON-RPC over HTTP 接入、Task 生命周期基础设施、事件总线和队列管理。
- 通过框架无关的 `AgentRuntimeHandler` SPI 驱动 openJiuwen、AgentScope 等异构 Agent 框架。
- 将 A2A SDK 的执行请求桥接为 runtime 内部的中立执行上下文，并将框架原生结果映射为 runtime 中立结果。
- 提供 Agent Card 发现能力，使一个 runtime 实例可以向外暴露可发现的 Agent 元数据。
- 提供纯 Java 嵌入入口和 Spring Boot 自动装配入口，支持业务应用以 SDK 方式集成 runtime。
- 为远端 A2A Agent 调用提供 runtime 侧目录与调用支撑能力，但不接管跨边界 A2A 总线治理。

## 受众边界

| 受众 | 主要需求 |
|---|---|
| 模块维护者 | 理解 `agent-runtime` 的模块身份、L0 边界、active 能力、依赖方向和 per-view 文档入口。 |
| Agent 框架适配开发者 | 理解框架如何通过 `AgentRuntimeHandler`、结果适配、memory、trajectory 和远端工具接缝接入 runtime。 |
| 业务集成开发者 | 理解如何把 runtime 作为 SDK 嵌入应用，以及当前对外暴露的 A2A 和 Agent Card 契约边界。 |
| 架构评审者 | 判断 runtime 是否仍保持框架中立、服务边界清晰、状态归属明确，并与 L0 顶层设计一致。 |
| AI agent / 文档维护者 | 以本文建立模块心智模型，再进入 4+1 视图和 L2 详细设计定位事实来源。 |

## 问题领域

`agent-runtime` 解决的是 Agent 执行运行时的接入、桥接和适配问题。当前问题领域集中在以下几个方面：

1. **A2A 协议接入与内部执行语义不同**
   A2A 请求以 JSON-RPC、Task、消息和事件流为外部语义；runtime 内部需要将其转换为框架无关的执行上下文和结果语义，避免 Agent 框架适配器直接依赖协议细节。

2. **异构 Agent 框架需要低改造接入**
   openJiuwen、AgentScope 等框架拥有各自的输入、输出、流式事件、中断和状态表达。runtime 需要提供稳定 SPI 和适配层，把框架差异吸收在模块内部。

3. **runtime Task/Session 与业务 Agent checkpoint 容易混写**
   当前 A2A SDK 管理的是 runtime 层面的 Task、Session、事件队列和状态推进；FEAT-003 可将 runtime Task 状态接入 Redis-backed TaskStore。业务 Agent 自身的 checkpoint 或记忆状态委托具体框架或外部能力，`agent-runtime` 只提供状态键、上下文和 Redis cache 桥接接缝。

4. **嵌入式 SDK 与 Spring Boot 装配需要共存**
   `agent-runtime` 既要支持纯 Java SDK 式启动，也要支持 Spring Boot 自动装配。Spring 依赖必须被限制在接入和 host 实现边界内，不能污染框架无关 SPI。

5. **运行时结果需要统一回传**
   同步应答、流式输出、失败、取消、中断和人工输入等待需要被折叠为统一的 runtime 结果语义，再由 A2A SDK 转换为外部响应。

## 模块边界形态

`agent-runtime` 是 L1 逻辑模块，也是当前 Java 实现中的可运行 SDK 模块。它不等同于平台 serviceization 外观，也不替代跨模块总线、业务状态或中间件能力。

| 边界项 | agent-runtime 负责 | agent-runtime 不负责 | 事实下沉位置 |
|---|---|---|---|
| A2A 接入 | 暴露 `/a2a` JSON-RPC 和 Agent Card 发现端点，并通过 A2A SDK 处理标准请求。 | 不定义新的外部协议族；多协议接入属于后续提案或 L2 设计。 | `api-appendix.md`, `logical.md`, `process.md` |
| Task 生命周期桥接 | 消费 A2A SDK 的 TaskStore、EventBus、QueueManager、RequestHandler，把 Task 执行推进到 Agent SPI；当前 A2A JSON-RPC 是 Service Task API 的实现形态。 | 不拥有平台级 Run record、幂等入口或 serviceization 状态外观。 | `logical.md`, `process.md`, `physical.md`, `api-appendix.md` |
| Agent 执行 SPI | 定义并消费框架无关的 `AgentRuntimeHandler`、执行结果、结果适配、memory、trajectory 和远端工具规格。 | 不把某个 Agent 框架设为平台唯一执行模型。 | `spi-appendix.md`, `development.md` |
| 框架适配 | 提供 openJiuwen、AgentScope 等当前适配实现和抽象基类。 | 不承诺所有未来框架适配已经 active。 | `development.md`, L2 详细设计 |
| 嵌入式启动 | 提供纯 Java runtime 入口、Spring Boot host 和自动装配。 | 不负责业务应用的部署编排、租户入口治理或平台网关能力。 | `development.md`, `physical.md` |
| 远端 Agent 调用支撑 | 在 `engine.a2a` 协议桥 / outbound invocation 边界内维护远端 Agent Card 目录和 outbound 调用支撑。 | 不接管跨实例、跨部门、跨数据边界的 A2A 总线治理；该边界归属 L0 中的 `agent-bus`。 | `logical.md`, `process.md` |
| 状态归属 | 管理 runtime Task/Session 语义和执行过程中的中立上下文；FEAT-003 可提供 Redis-backed Task 状态缓存。 | 不解释或接管业务 Agent checkpoint、业务 memory、外部系统状态；不把事件队列和执行线程池升级为分布式 runtime。 | `logical.md`, `physical.md` |

跨模块依赖方向保持为：`agent-runtime` 可对齐或映射 `agent-bus` 中立执行词汇，但当前不形成对 `agent-bus` 的编译依赖；`agent-runtime` 不依赖 `agent-service`；`agent-service` 可以集成 `agent-runtime` 作为运行时 SDK。
