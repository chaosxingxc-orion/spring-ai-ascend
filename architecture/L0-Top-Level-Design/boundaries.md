---
level: L0-TLD
TAG:
  - boundaries
  - logical-view
  - modules
  - state-ownership
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - views.md
  - constraints.md
  - glossary.md
---

# L0 架构模块边界

## 目的

本文档描述 L0 架构中定义的六个逻辑模块边界：`agent-client`、`agent-runtime`、`agent-core`、`agent-bus`、`agent-middleware` 和 `agent-evolve`。这些 L0 逻辑模块是后续 L1 高阶设计展开的边界。

本文档只回答 L0 层面的边界问题：每个逻辑模块负责什么业务逻辑，拥有或维护哪些数据对象与状态对象，以及在系统行为中承担什么边界职责。具体 SPI、API、包结构、部署单元、数据库表、消息主题、错误码和重试参数由 L1/L2 架构或实现设计承载。

## 六大逻辑模块总览

| L0 逻辑模块 | 边界定位 |
|---|---|
| `agent-client` | 业务接入与客户端集成边界，负责客户端调用、本地能力承载和服务流消费。 |
| `agent-runtime` | 服务端运行时边界，负责 Task 生命周期、服务侧状态归属和运行时接入。 |
| `agent-core` | 智能体开发与执行组件边界，负责 workflow / agent-loop 等可组合执行能力。 |
| `agent-bus` | 全局交互治理与总线边界，负责跨边界控制、路由、联邦协作和节奏信号治理。 |
| `agent-middleware` | 智能体基础中间件边界，负责模型、记忆、知识、工具、沙箱、提示词等可插拔能力。 |
| `agent-evolve` | 异步演进与数据反馈边界，负责评估、学习反馈、优化和受治理导出。 |

## 模块边界详述

### `agent-client`

**业务逻辑职责（负责什么、不负责什么）**

`agent-client` 面向业务应用、终端应用和集成开发者，承载客户端侧接入、调用封装、本地能力端点、审批交互和服务端实时输出消费。它是 C-Side 与平台运行时之间的业务接入边界。

`agent-client` 不负责通用智能体编排、服务端生命周期管理、平台级审计写入、跨实例 A2A 控制、模型/记忆/工具全局治理，也不依赖 `agent-runtime`、`agent-core` 或 `agent-middleware` 的内部实现细节。

**数据与状态对象归属（持有什么，不持有什么）**

`agent-client` 拥有客户端调用引用、本地调用句柄、SSE/stream 消费游标、本地能力引用、本地回调上下文和客户端侧持久化进度。它可以保存业务侧授权引用或本地能力执行结果，但不拥有服务端 Task 生命周期状态。

**行为边界概述（面向谁，暴露什么）**

`agent-client` 面向业务应用、终端应用、集成开发者和 `agent-runtime` 暴露客户端接入与本地能力边界。对业务侧，它暴露提交意图、持有调用引用、消费服务流和接收回调的能力；对平台侧，它暴露本地工具、本地检索、本地审批 UI 等 C-Side 能力端点，并只返回经过治理的执行结果、授权引用或回调结果。

### `agent-runtime`

**业务逻辑职责（负责什么、不负责什么）**

`agent-runtime` 是服务端智能体运行时边界，负责承接服务端请求、创建和维护 Task 生命周期、管理 Task 层级关系、维持运行时查询与实时输出表面，并协调执行组件、中间件、总线和客户端能力。

`agent-runtime` 不负责客户端业务事实、客户细粒度权限模型、模型/记忆/工具 provider 内部状态、跨边界控制总线物理通道、跨实例 A2A 私有通道，也不拥有 `agent-core` 的组件内部执行状态。

**数据与状态对象归属（持有什么，不持有什么）**

`agent-runtime` 拥有 Task、Task tree、Task lifecycle state、Task hierarchy、Session / Context shell、Agent definition 的服务侧注册与运行入口、服务流引用和运行时查询引用。当前 active `agent-runtime` 默认不拥有跨重启恢复 checkpoint 或持久化挂起/恢复游标；这些能力属于后续 draft 设计。它是服务端 Task 生命周期的权威 owner。

**行为边界概述（面向谁，暴露什么）**

`agent-runtime` 面向 `agent-client`、`agent-core`、`agent-middleware`、`agent-bus` 和运行时治理能力暴露服务端 Task 生命周期边界。对客户端和业务入口，它暴露 Task 创建、查询、取消、恢复和服务流表面；对执行组件，它暴露受治理的执行入口和状态意图接收边界；对中间件和总线，它暴露上下文组装、工具治理、跨边界协作和运行时证据交接所需的服务侧协调表面。

### `agent-core`

**业务逻辑职责（负责什么、不负责什么）**

`agent-core` 是面向开发者的智能体开发与执行组件边界，提供 workflow、agent-loop、planner、node、tool、hook 等可组合执行能力。它描述智能体如何执行，但不成为服务端生命周期 owner。

当执行组件需要模型、记忆、知识检索、工具代理、沙箱或安全护栏等中间件能力时，`agent-core` 可以持有调用意图生成、调用参数组装、恢复点记录和结果消费逻辑；中间件服务的具体执行、provider 适配、容量、幂等、审计和安全治理仍归属 `agent-middleware`，并由 `agent-runtime` 在服务侧执行契约中居中协调。

`agent-core` 不负责 HTTP 入口、租户治理、服务端 Task 准入、Task 状态写入、跨边界路由、平台审计最终写入、模型/工具 provider 内部治理，也不得直接从 bus、broker、外部队列或 Platform Gateway 表面拉取 Task。

**数据与状态对象归属（持有什么，不持有什么）**

`agent-core` 拥有 workflow 图、节点定义、ReAct loop 内部步骤、planner 状态、组件内部执行状态、执行组件产生的结果或意图，以及开发者可组合的执行组件元数据。它拥有 Task 边界以下的细粒度执行状态，但不拥有 Task 生命周期状态。若实现基线中存在本地 Session、checkpoint 或上下文对象，它们只能作为 SDK 本地执行辅助；进入平台运行路径时，服务侧 Session / Context shell 和 Task 级中断恢复入口仍由 `agent-runtime` 拥有。

**行为边界概述（面向谁，暴露什么）**

`agent-core` 面向智能体开发者、`agent-runtime` 和异构智能体框架适配实现暴露智能体执行组件边界。对开发者，它暴露 workflow、agent-loop、planner、node、tool、hook 等可组合执行组件；对 `agent-runtime`，它暴露受治理执行入口和执行结果/意图返回边界；对异构框架，它暴露可被适配纳入的执行组件模型，而不暴露服务端生命周期写入面。

### `agent-bus`

**业务逻辑职责（负责什么、不负责什么）**

`agent-bus` 是全局交互治理与总线边界，负责跨实例、跨部门、跨部署、跨信任边界的控制协作方向。当前 active 架构只保留该逻辑边界，具体 L1/L2 总线治理、路由和传输机制尚未展开。它治理跨边界通信关系，而不是拥有每个服务实例内部的生命周期状态。

`agent-bus` 不负责单个 `agent-runtime` 实例内部的多智能体协调，不直接修改 Task hierarchy 或 Task lifecycle state，不承载 token-by-token 服务流、大对象正文、多模态正文或客户敏感数据正文，也不做微服务网关层面的业务编排。若多个智能体实体天然存在，无论共节点还是跨节点，其协同生命周期、成员状态和 Task tree 关系应由对应 `agent-runtime` 居中管理；跨实例或跨信任边界时再进入 `agent-bus` 治理。若任务过程中只是为了上下文隔离而派生无状态子执行，则应建模为 Task tree / 子 Task 协作，而不是独立多智能体协同 owner。

**数据与状态对象归属（持有什么，不持有什么）**

`agent-bus` 的目标边界包括跨边界路由引用、A2A / federation 控制命令、S2C 回调引用、数据引用信封、权限中介上下文、节奏信号、唤醒信号、超时信号和事件/控制通道元数据；这些具体能力在展开为 L1/L2 前不作为已实现事实。它不拥有 Task lifecycle state 或 Task sleep state。

**行为边界概述（面向谁，暴露什么）**

`agent-bus` 面向多个 `agent-runtime` 实例、`agent-client` 回调端点、平台治理能力和跨信任边界参与方暴露全局交互治理边界。它暴露 A2A / federation 控制、S2C 回调治理、跨边界路由、权限中介、数据引用信封和节奏信号等控制表面；它只暴露控制、引用、路由和节奏语义，不暴露服务端 Task 生命周期写入面或大载荷数据传输面。

### `agent-middleware`

**业务逻辑职责（负责什么、不负责什么）**

`agent-middleware` 是智能体基础中间件边界，承载模型、记忆、知识、检索、技能、工具、沙箱、提示词、advisor 和运行时 hook 等可插拔能力。它为执行过程提供能力，但不成为执行生命周期 owner。

`agent-middleware` 不拥有 Task 生命周期状态，不绕过 `agent-runtime` 写服务端状态，不承载跨边界 A2A 控制传输，不把 provider 直连遥测作为唯一可观测性来源，也不拥有客户业务状态。

**数据与状态对象归属（持有什么，不持有什么）**

`agent-middleware` 拥有或代理 Memory、Knowledge、Retrieval index、Model route、Skill / Tool definition、Sandbox execution context、Prompt asset、Advisor rule、RuntimeMiddleware evidence 等中间件能力对象。外部 provider 的数据状态仍归 provider 或客户系统所有。

**行为边界概述（面向谁，暴露什么）**

`agent-middleware` 面向 `agent-runtime`、`agent-core`、平台治理能力和外部 provider 暴露智能体中间件能力边界。它暴露模型调用、记忆、知识检索、技能/工具、沙箱、提示词、advisor 和运行时 hook 等可插拔能力，同时暴露策略、容量、审计和证据产出的治理表面；它不向外暴露 Task 生命周期写入面。

### `agent-evolve`

**业务逻辑职责（负责什么、不负责什么）**

`agent-evolve` 是异步演进与数据反馈平面，负责基于受治理运行证据进行评估、学习、优化和反馈。它用于提升系统长期质量，而不是参与主请求同步执行。该平面可进一步包含数据采集与存储、数据处理流水线、采样与自由化智能体运行时、业务环境仿真、在线优化和离线优化等能力；这些能力可以拆分为独立微服务或作业单元，不与 `agent-core` 的智能体开发框架定位重合。

`agent-evolve` 不同步阻塞主执行路径，不直接修改运行时生命周期状态，不绕过导出治理抽取客户业务数据，不在主请求链路中执行重型评分、训练、图谱构建或调优任务。

**数据与状态对象归属（持有什么，不持有什么）**

`agent-evolve` 拥有受治理导出任务、评估样本、评分结果、离线分析结果、提示词/知识优化建议、训练或评估数据集引用、演进任务状态和未来 ML Pipeline 适配引用。它消费的是经过治理的证据或导出数据。

**行为边界概述（面向谁，暴露什么）**

`agent-evolve` 面向平台治理者、评估/训练任务、知识与提示词优化流程以及未来 ML Pipeline 暴露异步演进边界。它暴露受治理证据消费、离线评估、评分分析、优化建议、数据集引用和演进任务状态等反馈表面；它的输出以建议、资产变更候选或离线结果形式返回治理流程，而不直接暴露主请求同步执行面。
