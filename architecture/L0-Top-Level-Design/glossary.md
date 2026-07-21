---
level: L0-TLD
TAG:
  - glossary
  - vocabulary
  - logical-view
  - forbidden-conflations
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - boundaries.md
  - constraints.md
  - governance.md
---

# L0 架构术语表

## 目的

本文档记录当前 L0 架构中使用的全局术语，约束人和 AI 在架构文档、设计材料、代码注释、契约和评审讨论中对同一概念使用一致的名称。

术语保留中英双语：中文用于解释架构语义，英文用于对照代码、接口、元数据、测试和历史材料中的命名。若某个术语已由 active ADR、模块元数据、架构事实、代码事实或 active contract 定义，则以对应权威来源为准。

## 术语使用规则

- 英文术语可直接出现在代码、包名、契约、测试和架构图中；中文术语用于解释语义，不强制要求出现在代码中。
- 同一概念不得在 L0/L1/L2 中使用多个互相冲突的名称。
- 历史术语可以保留为兼容说明，但不得引入新的状态 owner、模块边界或运行时职责。
- 术语状态统一使用 `draft`、`proposal`、`active` 和 `archive`，描述该术语在当前架构体系中的存活状态，而不是记录历史阶段。

## 当前术语表

| 中文术语 | English Term | 含义 | Owner / Home | 不要混淆为 | 状态 |
|---|---|---|---|---|---|
| 架构事实系统 | Architecture fact system | 用于指导和约束架构的 L0/L1/L2 与 4+1 架构事实体系。 | `architecture/` | version scope backlog | active |
| 版本范围系统 | Version scope system | 一个版本的需求、业务场景、特性用例、功能点、交付切片和验收范围。 | `version-scope/` | architecture fact system | active |
| 运行视图 | Process view | 4+1 视图中的进程/运行视图，在本文档中用中文“运行视图”表达，用于描述顶层运行路径、并发模型和运行时协作。 | `views.md` | physical view、development view、service stream | active |
| openJiuwen 实现项目 | openJiuwen implementation project | 未来官方社区实现项目，可实现一个或多个 L0 逻辑模块；当前 `agent-runtime-java` 映射 `agent-runtime`，`agent-core-java` 映射 `agent-core`。 | openJiuwen community | L0 logical module name | proposal |
| 任务 | Task | V1 统一的服务端权威执行生命周期状态。它与 A2A task 语义对齐，可由 client-to-server 请求创建/绑定，也可由一个 `agent-runtime` 实例通过 A2A/federation 请求另一个 `agent-runtime` 实例创建/绑定。 | `agent-runtime` instance | Session、Memory、client invocation、engine-internal execution state | active |
| 运行 | Run | 历史或实现兼容术语，用于描述 execution/invocation 相关语义。它不是 V1 L0 标准服务端生命周期状态，不得引入第二个状态 owner。 | archived docs or implementation history | Task、Session、business order | archive |
| 客户端调用 | Client invocation | 客户端侧调用引用或 SDK 本地句柄，可映射到服务端 Task。 | `agent-client` + `agent-runtime` query surface | independent server lifecycle state | active |
| Task 状态存储 / 历史 RunRepository | TaskStateStore / Historical RunRepository | Task 生命周期状态的受控读写入口。若当前代码或历史文档仍使用 `RunRepository` 等名称，应理解为 Task owner path 的实现兼容名，而不是第二个 Run 状态 owner。 | `agent-runtime` Task lifecycle owner | generic DAO、arbitrary state writer、independent Run state model | archive |
| 会话 | Session | 对话、变量和上下文投影连续性的上下文状态。 | `agent-runtime` session boundary | Task lifecycle、Memory | proposal |
| 记忆 | Memory | 通过 memory SPI 或外部 memory adapter 暴露的知识或经验状态。 | `agent-middleware` and configured memory providers | Session temporary context | proposal |
| 检查点 | Checkpoint | 在挂起或长周期中断前保存的恢复/重建 payload。 | Checkpointer SPI / runtime owner | business state snapshot | proposal |
| 智能体 | Agent | 绑定模型、技能、记忆、规划器、提示词和 advisor 的注册实体，用于执行。 | `agent-runtime` agent SPI | Orchestrator | proposal |
| 编排器 | Orchestrator | 分派工作、处理挂起/恢复并产生执行/状态意图的运行时组件。 | `agent-core` for Task execution; `agent-runtime` for Task lifecycle coordination | lifecycle state owner | proposal |
| 执行组件内部状态 | Engine-internal execution state | Task 生命周期边界以下的细粒度执行状态，例如 workflow node execution state 或 ReAct loop state。 | `agent-core` | Task lifecycle state | active |
| 执行引擎调用边界 | Execution Engine SPI | `agent-runtime` 请求 `agent-core` 执行 Task 并返回执行结果、挂起请求、工具意图、上下文请求、子工作意图或终态结果的调用边界。 | `agent-core` | bus control、model gateway SPI、lifecycle state writer | active |
| 官方执行引擎 | Official execution engine | `agent-core` 边界后的 openJiuwen 官方执行引擎实现。 | `agent-core` | heterogeneous execution engine adapter | active |
| 异构执行引擎 | Heterogeneous execution engine | 被适配到 Execution Engine SPI 的非 openJiuwen 智能体框架实现，包括 workflow-style 和 agent-loop-style 框架。 | `agent-core` adapter domain | independent lifecycle owner or service core dependency | active |
| 执行引擎适配器 | Engine adapter | 将异构执行引擎翻译到 Execution Engine SPI 的反腐适配器。服务侧提供扩展和装配入口，框架相关翻译属于执行组件适配领域。 | `agent-core` adapter domain + `agent-runtime` extension assembly | service-owned framework implementation、bus control | active |
| 异构框架兼容 | Heterogeneous framework compatibility | 官方 openJiuwen 执行和异构框架执行都可通过 `agent-core` 边界参与，而不重写 Task 生命周期归属，也不强制改造已运行智能体实现。 | `agent-core` + `agent-runtime` extension assembly | closed single-framework runtime or lifecycle-owner rewrite | active |
| 运行时中间件 | RuntimeMiddleware | 横切 middleware hook listener 和 dispatch surface。 | `agent-middleware` | provider implementation | active |
| 模型网关 | ModelGateway | 平台模型调用边界。 | `agent-middleware` model SPI | direct Spring AI `ChatModel` use | proposal |
| 技能 | Skill | 受治理的工具/技能执行单元。 | `agent-middleware` skill SPI | ungoverned business function call | proposal |
| 工具网关 | Tool Gateway | 面向 skill 授权、容量、审计、幂等和工具调用治理的能力聚合。 | `agent-middleware` + `agent-runtime` integration | independent reactor module | proposal |
| 上下文引擎 | Context Engine | 面向 session、context projection、memory、retrieval、vector 和 context package assembly 的能力聚合。 | `agent-runtime` + `agent-middleware` | independent reactor module | proposal |
| 平台网关 | Platform Gateway | 平台级入口治理能力，涵盖认证预检查、租户路由、跨服务路由、流量治理、A2A/S2C ingress 和权限中介；可在 L1/L2 中作为 `agent-bus` 下的运行时单元实现。 | `agent-bus` L1/L2 candidate | Service Task API、service stream、business orchestration | proposal |
| 服务任务 API | Service Task API | 服务侧拥有的 create task、query task、stream task、cancel task 和相关 Task 生命周期 HTTP/API 表面。当前 `agent-runtime` L1 中，A2A JSON-RPC 是该 API 的 active 实现形态。 | `agent-runtime` | Platform Gateway、bus event channel、engine pull queue | active |
| 智能体总线 | Agent Bus | 面向 S2C、A2A/federation、路由、权限中介、节奏、数据引用信封和窄事件/控制传输单元的广义平台交互治理领域；不等同于单一 MQ 或 event bus。 | `agent-bus` | narrow event bus、service SSE stream、gateway ingress | active |
| 事件/控制通道 | Event/control channel | `agent-bus` 领域下的窄传输或信号通道，可由 MQ 或其他消息中间件承载；只传控制命令、引用、路由元数据和节奏信号，不承载大对象正文或 token-by-token 外部流。 | `agent-bus` L1/L2 runtime unit | broad Agent Bus domain、data path、service stream | active |
| 集成开发者 | Integrating developer | 直接集成 `agent-client`、定义智能体、连接业务工具并负责业务系统内应用发布结果的平台用户。 | business application team | end business user、platform-internal module owner | proposal |
| C 侧 | C-Side | 拥有业务目标、规则、事实、本地工具、本地上下文和授权引用的业务应用/客户端侧。 | business application side | platform runtime state | proposal |
| S 侧 | S-Side | 拥有执行轨迹、治理、可观测、审计、容量和平台中间件的平台运行时侧。 | platform runtime side | business facts owner | proposal |
| 能力放置 | Capability placement | 决定 tool、context、memory、retriever、approval UI、adapter 或 A2A action 在哪里执行，以及跨越什么数据边界。 | architecture + contracts | module placement only | proposal |
| 本地能力 | Local capability | 在业务/客户端侧执行的能力，例如 local tool、local context、local memory、local retriever 或 approval UI。 | `agent-client` endpoint | platform-hosted capability | proposal |
| S2C 回调 | S2C callback | 面向本地能力、审批或外部输入的 Server-to-Client callback 或 handoff。 | `agent-bus` S2C + `agent-client` endpoint | A2A federation | proposal |
| A2A 控制命令 | A2A control command | 面向子工作、联邦、完成、失败、超时或 join 的 Agent-to-Agent 控制指令。 | `agent-bus` for cross-instance or cross-boundary control; `agent-runtime` instance for same-instance relationship | large data payload or token stream | proposal |
| 联邦协作 | Federation | 跨实例、跨部门、跨部署或跨信任边界的 A2A 协作。 | `agent-bus` + local and remote `agent-runtime` relationship owners | same-instance child work | proposal |
| 任务树 | Task tree | 用于追踪委派、join、失败传播和成本归因的父子执行关系。同实例子工作由本地 `agent-runtime` 拥有；联邦子引用通过 bus/federation control 中介，并由参与服务实例拥有。 | `agent-runtime` instance + observability | single trace span、engine-internal state、bus-owned lifecycle | active |
| 节奏信号 | Rhythm signal | 用于跨实例或跨边界协调的 timing、wakeup、retry、timeout 或 schedule signal。它可由 `agent-bus` 治理或路由，但 Task 级 suspend/resume 状态仍由对应 `agent-runtime` 实例拥有。 | `agent-bus` governance + `agent-runtime` Task owner | bus-owned Task sleep state、engine pull loop | proposal |
| 数据引用路径 | Data reference path | 大载荷或敏感 payload 路径：控制消息携带 URI/object reference/metadata，数据由授权消费者获取；`agent-bus` 可治理引用信封和权限交接，数据正文不进入窄事件/控制通道。 | external storage owner + `agent-bus` envelope governance | event/control channel payload transport | proposal |
| 服务 SSE 流 | Service SSE stream | `agent-runtime` 实时外部输出表面。具体流技术低于 L0，但 L0 默认将外部实时内容流视为服务表面。 | `agent-runtime` | event/control channel token stream | proposal |
| 客户授权引用 | Customer auth reference | 来自客户拥有的身份或权限系统的授权引用，平台可用它访问客户数据源，但不拥有或重定义客户细粒度业务权限模型。 | C-Side authorization owner + service/middleware integration | platform-owned business permission model、copied customer credentials | proposal |
| 租户纵向能力 | Tenant Vertical | 横切租户身份传播和隔离关注点。 | platform runtime | per-module tenant reinvention | active |
| 遥测纵向能力 | Telemetry Vertical | 横切 trace/span/event/LLM call/cost 证据关注点。 | platform observability | provider-local logging | active |
| Trace 上下文 | TraceContext | runtime context 的运行时遥测载体伴随对象。 | bus/service runtime SPI per active placement | HTTP-only header | active |
| 审计记录 | Audit record | 重要运行时决策和副作用的 append-only 平台证据。 | platform audit writer | business record | proposal |
| 平台托管服务 | Platform-hosted service | 面向弱部门/PaaS 租户的平台托管运行时。 | platform operations | business-owned service | proposal |
| 业务中心部署 | Business-centric deployment | 业务侧可托管 `agent-client`、`agent-runtime` 和 `agent-core`，平台保留共享 bus、middleware 和 federation governance 的部署形态。 | deployment architecture | new module boundary | proposal |
| 运行运营洞察 | Runtime operations insight | 智能体能力运行中的运维和业务运营证据，包括请求量、成功率、延迟、成本、错误、容量、trace 和 audit 维度。 | observability capability | single execution debug log、customer-owned business state | proposal |
| 场景规格 | Scenario spec | 区分 BA-* 业务活动场景和技术子场景，并在提升为架构事实或版本范围前记录期望断言的场景描述。 | version scope system + architecture stress scenarios | flow diagram only、active architecture truth before promotion | proposal |
| 演进数据飞轮 | Evolution data flywheel | 在主请求路径之外，面向运行证据、评分、学习和优化的受治理导出与分析循环。 | `agent-evolve` candidate boundary | synchronous online execution dependency | proposal |
| 不变量 | Invariant | 可检查的架构规则。 | L0 constraints and verification | slogan | active |
| 验证脚手架 | Harness | 用于驱动开发和验证的 mocks、stubs、fixtures、contract tests、scenario assertions 和 failure injection。 | verification/scope system | production implementation | proposal |
| 草案状态 | `draft` | 当前仍在形成中的架构材料，尚未进入存活架构事实。 | draft docs | active architecture fact | active |
| 提案状态 | `proposal` | 当前仍有提升价值、但尚未成为存活架构事实的提案或候选方向。 | proposal docs | active architecture fact | active |
| 生效状态 | `active` | 当前存活的架构事实、术语、约束或治理规则。 | `architecture/` | runtime-enforced evidence by default | active |
| 归档状态 | `archive` | 不再约束当前架构、仅可作为历史来源或兼容说明的材料。 | archive docs | active architecture fact | active |

## 禁止混淆

- 不要把 Run 当作 V1 标准生命周期术语；服务端执行生命周期状态使用 Task。
- 不要把 `agent-runtime-java` 或 `agent-core-java` 等 openJiuwen 实现项目名当作 L0 逻辑模块名的替代。
- 不要把 client invocation 当作第二个服务端生命周期状态。
- 不要把 Context Engine 或 Tool Gateway 当作独立模块。
- 不要把 Platform Gateway、Service Task API、广义 Agent Bus、窄 event/control channel 和 service SSE 混成同一个通信通道。
- 不要让 `agent-core` 直接从 bus、broker 或 external queues 拉取 Task；Task dispatch 必须经由 `agent-runtime`。
- 不要把 A2A control messages 或窄 event/control channel 当作大载荷或 token-stream transport。
- 不要把 business state 当作 platform runtime state。
- 不要把 draft ICD/YAML 材料当作 active contract authority。
- 不要把 version scope scenarios 当作 architecture truth，除非它们已经被提升为 active 架构事实。
