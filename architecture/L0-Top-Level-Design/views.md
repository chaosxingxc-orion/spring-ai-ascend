---
level: L0-TLD
TAG:
  - 4+1
  - logical-view
  - development-view
  - process-view
  - physical-view
  - scenarios-view
  - runtime-path
  - deployment-variants
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - boundaries.md
  - constraints.md
  - glossary.md
---

# L0 架构4+1视图

## 目的

本文档通过 4+1 视图组织 L0 架构事实，用于说明企业级智能体平台在逻辑结构、开发实现、运行控制、物理部署和技术验证场景上的顶层形态。

本文档是架构事实视图体系，不是版本范围内的业务场景 backlog。场景视图仅包含用于验证架构形态的技术验证场景；版本范围内的业务活动场景、特性用例和交付任务归属到 `version-scope/`，并在依赖架构约束时引用本文档。

## 视图地图

| 视图 | 回答的问题 | 本文档给出的信息 |
|---|---|---|
| 逻辑视图 | 系统由哪些逻辑模块、逻辑概念和能力关系构成？ | 逻辑模块、核心概念、状态归属、能力归属和模块关联关系。 |
| 开发视图 | 逻辑模块如何映射到实现代码、实现项目和技术选型？ | 当前仓库目录、未来开源实现项目、主流技术选型和不应提升为 L0 模块的制品。 |
| 运行视图 | 顶层运行路径如何流转，各模块采用什么并发模型？ | 请求接入、Task 执行、工具/上下文、S2C、A2A、挂起/恢复和证据链路径。 |
| 物理视图 | 不同部署形态下，模块如何映射到部署单元和物理资源？ | 平台中心、弱部门/PaaS、本地能力、业务中心/联邦和混合个人形态。 |
| 场景视图 | 哪些技术场景稳定运行可以反馈其他四个视图成立？ | S1-S6 技术验证场景及其覆盖的逻辑、开发、运行和物理视图风险。 |

## 逻辑视图

L0 逻辑视图描述系统的稳定心智模型。它不等同于 Java 包、运行进程、部署单元或未来开源项目名称。

### 逻辑模块

| L0 逻辑模块 | 核心职责 | 主要拥有的逻辑对象 | 不拥有 |
|---|---|---|---|
| `agent-client` | 业务接入、SDK、本地能力端点、游标/回调和流消费。 | client invocation、本地能力引用、SSE 消费游标、C-Side 能力端点。 | 服务端 Task 生命周期、平台审计写入、智能体通用编排。 |
| `agent-runtime` | 服务端 Task 生命周期、运行时接入、异构框架适配 SPI、反腐装配和查询/流表面。 | Task、Task tree、Service Task API、Session shell、服务端适配入口、外部实时流表面。 | 业务事实、模型/记忆/技能全局 SPI、跨边界 A2A 私有通道。 |
| `agent-core` | 面向开发者的智能体开发 SDK，主流选型为 `openJiuwen`（`agent-core`）。 | workflow 图执行器、ReAct loop 执行器、Node、Tool、Hook、Planner 等开发组件。 | 服务端 Task 生命周期、租户治理、跨边界路由、平台审计最终写入。 |
| `agent-bus` | 全局交互治理、Platform Gateway 治理、S2C、A2A/联邦、路由、权限中介和节奏信号。 | Platform Gateway 能力、A2A 控制、S2C 回调、数据引用信封、事件/控制通道、rhythm signal。 | 单实例内部多智能体协调、Service Task API、Task sleep 状态、大载荷正文和 token 流。 |
| `agent-middleware` | 可插拔智能体中间件、运行时 Hook 和审计表面。 | ModelGateway、Memory、Retrieval、Knowledge、Skill、Sandbox、Prompt、Advisor、RuntimeMiddleware。 | Task 生命周期状态、跨边界 A2A 控制传输、客户业务状态。 |
| `agent-evolve` | 异步演进、评估、学习反馈和受治理导出。 | 运行证据导出、Eval-Loop、提示词/知识优化、未来 ML Pipeline 接入。 | 主请求同步路径、运行时生命周期变更、未治理业务数据抽取。 |

### 核心概念与归属

| 逻辑概念 | 语义 | 归属 |
|---|---|---|
| Task | V1 统一的服务端权威执行生命周期状态。 | `agent-runtime` 实例。 |
| Task tree | 父子执行、委派、汇合、失败传播和成本归因关系。 | 同实例由本地 `agent-runtime` 拥有；跨实例通过 `agent-bus` 联邦控制保留引用关系。 |
| Client invocation | 客户端调用引用或 SDK 本地句柄，可映射到服务端 Task。 | `agent-client` + `agent-runtime` 查询表面。 |
| Session / Context package | 对话、变量、上下文投影、记忆和检索组装的上下文连续性。 | `agent-runtime` 与 `agent-middleware` 协作，生命周期不得覆盖 Task。 |
| Agent definition | 绑定模型、技能、记忆、规划器、提示词和 advisor 的智能体定义。 | `agent-runtime` 持有服务侧注册/运行入口，组件来自 `agent-core` 或 `agent-middleware`。 |
| Execution component | workflow 节点、ReAct loop、Tool、Hook、Planner 等开发者可选组件。 | `agent-core`。 |
| Runtime governance | 租户、身份、安全态势、幂等、审计、容量和策略控制。 | 由 `agent-runtime`、`agent-bus`、`agent-middleware` 按边界协作。 |
| Evidence | trace、span、event、audit、metrics、cost 和可安全重放的 fixture。 | 遥测与审计纵向能力，写入路径由运行时治理控制。 |

### 关联关系

| 关系 | 说明 | 约束 |
|---|---|---|
| `agent-client` -> `agent-runtime` | 客户端提交意图、查询 Task、消费服务流或执行本地能力回调。 | 客户端不直接写服务端生命周期状态。 |
| `agent-runtime` -> `agent-core` | 服务端通过受治理的执行契约调用官方 SDK 组件或被适配的智能体实现。 | `agent-core` 返回执行结果、挂起请求、工具意图、上下文请求或终态结果，不直接抢占 Task owner。 |
| `agent-runtime` -> 异构框架 | `agent-runtime` 持有适配 SPI、反腐装配入口与运行时接入契约。 | 不要求外部开源框架预先满足平台通信契约，由适配实现吸收差异。 |
| `agent-runtime` -> `agent-middleware` | 模型、技能、记忆、检索、提示词、Advisor 和 Hook 通过中间件边界进入。 | 中间件不得绕过服务端 Task owner 写生命周期状态。 |
| `agent-runtime` -> `agent-bus` | 跨实例、跨部门、跨部署或跨信任边界时使用 A2A/联邦、S2C 和 rhythm signal。 | `agent-bus` 治理跨边界控制和引用信封，但不拥有每个服务实例内部的 Task sleep 状态。 |
| `agent-evolve` <- 运行证据 | 演进平面异步消费受治理导出的运行证据。 | 不同步阻塞主执行路径。 |

切面纵向能力由 `constraints.md` 定义，包括租户纵向能力、安全态势纵向能力、遥测纵向能力、审计与策略纵向能力、容量与背压纵向能力。

## 开发视图

开发视图说明 L0 逻辑模块如何落到当前仓库、未来开源实现项目和技术选型。实现可以拆分、合并或迁移，但不能反向改变 L0 逻辑边界。

### 模块到实现映射

| L0 逻辑模块 | 当前仓库事实 / 代码落点 | 主流实现或技术选型 | 说明 |
|---|---|---|---|
| `agent-client` | `agent-client/` | Java SDK、HTTP/SSE client、本地能力端点适配。 | 面向业务接入与集成开发者，不承载平台编排。 |
| `agent-runtime` | `agent-runtime/` | Spring Boot service、Service Task API、运行时状态机、异构框架适配 SPI。 | `openJiuwen` 的 `agent-runtime-java` 可作为未来社区实现项目名。 |
| `agent-core` | 当前可能仍以历史 engine 目录、SDK 代码或草案实现存在。 | `openJiuwen`（`agent-core`）、workflow graph executor、ReAct loop executor、Node/Tool/Hook/Planner 组件。 | `agent-core-java` 是未来社区实现项目名，不替代 L0 逻辑模块名。 |
| `agent-bus` | `agent-bus/` | Platform Gateway、A2A/联邦、S2C、事件/控制通道、权限中介、节奏信号。 | 不等同于单一 MQ 或 event bus。 |
| `agent-middleware` | `agent-middleware/` | Spring AI 适配、ModelGateway、Memory、Retrieval、Skill、Sandbox、Prompt、Advisor、RuntimeMiddleware。 | 能力以可插拔 SPI 或适配器形态出现。 |
| `agent-evolve` | `agent-evolve/` | 受治理导出、Eval-Loop、离线评分、提示词/知识优化、未来 ML Pipeline。 | 只异步消费证据，不进入主请求同步链路。 |

### 制品与技术选型规则

| 对象 | 开发视图处理方式 |
|---|---|
| BoM / dependencies | 属于构建治理或相关模块开发制品，不是 L0 逻辑模块。 |
| starter / auto-configuration | 属于被包装能力的集成制品，不是 L0 逻辑模块。 |
| adapter | 属于服务侧适配或 SDK/中间件适配实现，必须说明归属模块和契约边界。 |
| generated facts / module metadata | 描述代码事实和 reactor 身份，不决定 L0 模块准入。 |
| external OSS framework | 作为异构智能体框架或中间件提供方接入，由 `agent-runtime` 适配 SPI 或 `agent-middleware` SPI 吸收差异。 |

L1 架构位于 `architecture/L1-High-Level-Design/`。L2 技术设计由后续 `architecture/L2-Low-Level-Design/` 或具体模块内技术设计承载。

## 运行视图

运行视图描述顶层关键路径和并发模型。L0 不规定线程池大小、超时值、topic 名称或 API 签名，但规定控制面、数据面、流式运行面和长等待语义必须分离。

### 关键运行路径

| 路径 | 顶层流程 | 涉及模块 | 必须保持的约束 |
|---|---|---|---|
| Task 创建与接入 | client / HTTP caller -> Service Task API 当前实现形态（A2A JSON-RPC） -> `agent-runtime` 创建 Task。 | `agent-client`、`agent-runtime` | 入口传播 tenant 和 trace；Platform Gateway 准入、actor、idempotency 和 posture 治理属于待展开 draft 设计。 |
| 智能体执行 | `agent-runtime` Task owner -> 受治理执行契约 -> `agent-core` 官方组件或异构框架适配实现 -> 返回执行结果/意图。 | `agent-runtime`、`agent-core`、异构框架适配实现 | SDK/框架不直接拉取 Task，不直接写生命周期状态。 |
| 上下文构建 | Task owner 请求上下文 -> Session shell -> memory/retrieval/prompt/advisor 组装 context package。 | `agent-runtime`、`agent-middleware` | Context 不覆盖 Task 生命周期；记忆和知识状态通过中间件边界读写。 |
| 工具调用 | 执行组件产生 tool intent -> 服务侧治理 -> skill/tool/sandbox 执行 -> audit/evidence。 | `agent-core`、`agent-runtime`、`agent-middleware` | 不可逆副作用必须幂等或有重复保护；工具不得绕过治理直接外呼。 |
| 本地能力 / 审批 | 服务侧产生中断或等待输入状态 -> 客户端继续消息或回调结果 -> 受控结果返回。 | `agent-runtime`、`agent-client` | 当前 active `agent-runtime` 表达 `INPUT_REQUIRED` 等状态；S2C 总线治理、本地能力权限和审批 UI 属于 draft 设计。 |
| 跨实例 A2A / 联邦 | 当前 `agent-runtime` 提供远端 A2A Agent outbound 调用支撑；跨实例、跨部门、跨数据边界治理需要显式声明。 | `agent-runtime`、远端 `agent-runtime` | 远端 Task 生命周期由远端服务拥有；中央 `agent-bus` 联邦治理属于 draft 设计。 |
| 挂起 / 恢复 | 长等待或外部输入等待 -> `agent-runtime` 表达 Task 中断/等待状态 -> 客户端继续消息或远端回灌恢复。 | `agent-runtime`、`agent-client` | 当前 active 默认 InMemory，不保证跨重启恢复；checkpoint/cursor/next-wake 持久化和节奏信号属于 draft 设计。 |
| 实时输出 | `agent-runtime` 服务流表面 -> client SSE/stream 消费。 | `agent-runtime`、`agent-client` | token/content stream 不退化为窄事件/控制通道。 |
| 数据引用 | 控制消息携带引用信封 -> 授权消费者读取对象存储/客户系统/提供方。 | `agent-bus`、`agent-runtime`、外部数据路径 | 大载荷和敏感数据不进入窄事件/控制载荷。 |
| 证据链 | 各路径产生 trace、span、event、audit、metrics、cost、fixture。 | 全模块 + 纵向能力 | 证据可关联 tenant、Task、agent、tree、tool 和 cost。 |

### 并发模型

| 并发域 | 并发方式 | 资源释放规则 | 背压与隔离 |
|---|---|---|---|
| 接入控制 | 短事务处理请求准入、幂等校验、租户和安全态势绑定。 | 不承载长尾 token stream 或大载荷传输。 | 与服务流、数据路径和 bus 控制通道隔离。 |
| Task lifecycle | 每个 Task 由 `agent-runtime` 持有状态机和确定性 writer path。 | 当前长等待表达为 Task 状态、事件和继续消息。 | 租户/应用/agent/Task tree 维度容量治理属于 draft 设计。 |
| SDK / 框架执行 | workflow 节点、ReAct loop 和异构适配可并发执行。 | 执行组件返回意图或结果，不保留服务端生命周期锁。 | 由服务侧契约和中间件治理限制工具、模型和资源使用。 |
| 中间件能力 | 当前仅在 `agent-runtime` SPI 中提供 memory、trajectory、remote tool 等窄接缝。 | 外部调用与沙箱执行治理属于后续中间件 L1/L2 设计。 | Provider 级背压属于 draft 设计。 |
| Bus 控制 | `agent-bus` 作为逻辑模块保留跨边界治理方向。 | A2A、S2C、rhythm signal 和数据引用信封的窄控制通道属于 draft 设计。 | 不承载 token stream、大对象正文或服务内 Task sleep owner是目标约束，尚待 L1 展开。 |
| 服务流 | SSE 或等价服务实时输出表面。 | 客户端消费慢不得反向阻塞控制面。 | 与事件/控制通道分离。 |
| 演进平面 | 异步消费导出证据，离线分析、评分或优化。 | 不同步等待主请求。 | 通过导出契约和隐私治理限流。 |

## 物理视图

物理视图说明不同部署形态下，逻辑模块如何映射到部署单元、信任边界和物理资源。一个逻辑模块可以被拆成多个部署单元，多个逻辑模块也可以在早期阶段合并部署，但合并部署不改变逻辑边界。

### 物理平面

| 物理平面 | 典型部署单元 | 主要资源 | 承载模块 |
|---|---|---|---|
| 边缘 / 客户端平面 | SDK、业务应用、本地能力 agent、审批 UI。 | 终端、本地服务器、业务侧网络和本地凭据。 | `agent-client`，以及 C-Side local capability。 |
| 服务控制平面 | Task service、runtime service、adapter host、query/stream service。 | 当前 active `agent-runtime` 使用宿主 JVM、服务线程池和 InMemory 状态；checkpoint 存储属于 draft 设计。 | `agent-runtime`，可包含被适配的 `agent-core` 执行组件。 |
| SDK / 执行组件平面 | Java SDK library、agent-core runtime、workflow/ReAct executor、异构框架 adapter host。 | CPU/NPU/GPU 入口、执行线程、框架运行时资源。 | `agent-core`，以及由 `agent-runtime` 适配接入的外部框架。 |
| 总线与交互治理平面 | Platform Gateway、A2A/S2C gateway、event/control channel、permission/routing service。 | 网络、消息中间件、路由表、权限中介、调度器。 | `agent-bus`，当前尚未展开 L1 active 设计。 |
| 中间件能力平面 | model gateway、memory store、retrieval service、skill service、sandbox service、prompt/advisor service。 | 模型连接池、向量库、对象存储、沙箱容器、外部工具连接。 | `agent-middleware`。 |
| 数据路径平面 | 客户数据源、对象存储、第三方系统、模型/知识提供方。 | 数据库、对象存储、文件系统、专线或外部 API。 | 外部系统 + 数据引用路径治理。 |
| 演进平面 | export job、eval worker、offline scoring、ML pipeline adapter。 | 离线计算、训练/评估资源、证据仓。 | `agent-evolve`。 |

### 部署形态映射

| 形态 | 模块与部署单元映射 | 物理资源特征 | 架构含义 |
|---|---|---|---|
| 平台中心型 | `agent-runtime`、`agent-bus`、`agent-middleware`、`agent-evolve` 由平台部署；`agent-client` 在业务侧；`agent-core` 作为平台服务内组件或库运行。 | 平台托管 CPU/存储/模型连接/沙箱；业务侧只持有 SDK 和少量本地引用。 | 平台托管上下文、工具、模型治理、可观测性和运行控制。 |
| 弱部门 / PaaS 租户型 | 所有运行时部署单元由平台托管；业务只提交配置、授权引用和发布验收输入。 | 平台多租户隔离、共享容量池、统一审计与运维。 | 平台拥有运行时，不拥有业务事实和细粒度业务权限模型。 |
| 受保护本地能力型 | 敏感工具、本地上下文、本地记忆/检索或审批 UI 部署在 C-Side；平台部署服务控制、bus 和中间件公共能力。 | 本地资源保存敏感数据；平台通过 S2C/Yield 获取受控结果。 | 能力放置显式跨越 C-Side/S-Side 边界。 |
| 业务中心 / 联邦型 | 业务侧可部署 `agent-client`、`agent-runtime`、`agent-core` 和部分 `agent-middleware`；平台保留共享 `agent-bus`、公共中间件和演进治理。 | 本地低延迟执行，跨边界控制走平台 bus。 | 同实例协作本地闭合；跨实例、跨部门、跨信任边界仍使用 A2A/联邦契约。 |
| 混合企业个人型 | 个人本地工具、业务侧服务和平台公共服务共同参与同一 Task。 | 本地终端、业务服务、平台服务和外部数据路径同时参与。 | 能力放置可在一个 Task 内变化，但每次跨边界必须留下引用、授权和审计证据。 |

信任边界包括 HTTP 边缘到运行时边界、C-Side 到 S-Side 边界、父执行到子执行边界、Task 到技能权限边界、跨 workflow/实例/部署/信任边界交接、租户级存储与遥测重放边界。

## 场景视图

场景视图只描述技术验证场景。它的目的不是覆盖业务 backlog，而是选择少量技术场景来持续反证逻辑视图、开发视图、运行视图和物理视图是否成立。

### 技术验证场景

| 场景 | 稳定运行证明什么 | 覆盖视图 |
|---|---|---|
| S1 创建 Task | A2A Service Task API 能创建 runtime Task，并保持 Task owner 归属清晰；租户/操作者/幂等/posture 的完整准入治理属于 draft 设计。 | 逻辑视图、运行视图、物理视图。 |
| S2 执行智能体步骤 | `agent-runtime` 能通过中立执行 SPI 调用本地 handler 或适配实现，并保持 Task 生命周期权威。 | 逻辑视图、开发视图、运行视图。 |
| S3 构建上下文包 | 当前验证 Task/Session 与业务 Agent checkpoint 不混写；完整 Context Engine、retrieval、prompt、advisor 组装属于 draft 设计。 | 逻辑视图、运行视图。 |
| S4 带治理的工具调用 | 当前验证远端 Agent 工具规格与中立 SPI 边界；Tool/Skill/Sandbox 授权、容量、审计、幂等和副作用保护属于 draft 设计。 | 逻辑视图、开发视图、运行视图。 |
| S5 挂起 / 恢复 | 当前验证 `INPUT_REQUIRED`、继续消息和远端回灌等 Task 状态路径；跨重启 checkpoint、cursor、callback、timeout 或 rhythm signal 恢复属于 draft 设计。 | 运行视图、物理视图。 |
| S6 子 Task / 联邦协作 | 当前验证远端 A2A outbound 支撑不抢占远端生命周期；同实例 Task tree、跨实例 A2A/联邦和 agent-bus 治理属于 draft 设计。 | 逻辑视图、运行视图、物理视图。 |

### 场景提升规则

当前 S1-S6 是技术验证场景候选，不是业务活动场景。它们在完成 L0-GAP-003 的提升决策，并具备对应断言、fixture、contract test、架构审视或显式未验证状态之前，不能被称为已验证的运行时权威。

场景提升时必须记录：

- 该场景验证的 L0 逻辑模块和状态 owner。
- 涉及的实现模块、适配器或技术选型。
- 控制面、数据面、服务流和 bus 事件/控制通道是否分离。
- 涉及的部署形态和物理资源假设。
- 通过的自动化测试、人工审视或明确的未验证状态。

## 视图输出

当前分支不保留独立的机器可读 L0 workspace 权威。L0 视图模型以架构事实形式记录在本文档中。

`docs/architecture/l0/architecture-views/` 下的 PlantUML 渲染图和图片导出属于历史草案交付视图。它们可以作为视觉参考，但在重新提升回 `architecture/` 之前，应基于当前架构事实重新生成。
