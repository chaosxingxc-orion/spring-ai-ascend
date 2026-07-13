---
level: L1
view: version-scope
module: platform
status: active
updated: 2026-07-13
authority: "current version facts across agent-runtime and agent-bus"
covers: [标准化Agent服务入口, 异构Agent框架兼容, 智能体任务状态缓存, 智能体中间件请求代理, 远程Agent编排, RESTful Client Facade, 客户端调用路由转发, 客户端调用总线转发, 客户端调用事件转发, A2A调用事件转发, Agent Card注册与发现, 运行时实例路由查询, 订阅消费总线事件消息, 轨迹可观测性]
---

# version-scope

`version-scope` 是当前版本的事实范围描述目录，用于说明本版本已经纳入范围、需要被设计、实现、测试和指南对齐的需求事实。它不是长期路线图，也不是模块详细设计本身；它回答的是：当前版本对外承诺哪些能力、这些能力的外部行为边界是什么、哪些文档是后续详细设计与实现校验的事实来源。

本目录当前同时承载 `agent-runtime` 与 `agent-bus` 相关特性文档。文档从需求侧出发，描述外部可观察行为、能力边界、接口入口、用户旅程和不承诺项；`architecture/L2-Low-Level-Design/` 中的详细设计应引用并满足这些事实要求。如果实现或设计先行产生了新能力，也必须先回到 `version-scope` 明确其是否纳入当前版本事实范围。

## 1. 文档目的

本目录承载当前版本的需求类事实文档，包括：

| 文档类型 | 说明 |
|---|---|
| 功能特性文档 | 以外部视角描述功能能力、接口入口、场景旅程、行为语义和边界。 |
| DFX 特性文档 | 描述可观测性、可靠性、安全性等横切能力的外部行为和验收范围。 |
| 版本范围入口 | 汇总当前目录内的事实文档，帮助读者按模块和 Feature ID 定位。 |

其中，特性文档是本目录的核心产物。它们侧重外部可观察行为，不展开类级设计、数据结构或具体实现策略。

## 2. 范围边界

本目录只描述当前版本已经纳入事实范围的能力：

- 只声明当前版本范围内的特性，不记录后续路线图。
- 只描述外部行为、能力边界、接口入口和用户可见流程。
- 不替代 L2 详细设计；内部模块拆分、类设计、数据结构、存储模型和实现策略由 `architecture/L2-Low-Level-Design/` 承载。
- 不替代开发者指南；安装、配置、样例和完整操作手册由各模块 `docs/`、`examples/` 或指南文档承载。
- 不把已存在但未在特性文档声明的实现能力自动提升为当前版本对外承诺。

## 3. 特性索引

| Feature ID | 模块 | 状态 | 特性 | 当前版本范围简介 | 特性文档 | L2 详细设计对应关系 |
|---|---|---|---|---|---|---|
| DFX-001 | agent-runtime | active | 轨迹可观测性 | 记录 Agent 执行过程中的运行、模型调用、工具调用、错误和进度事件，提供框架中立的执行轨迹与敏感信息掩码。 | [DFX-001-trajectory-observability.md](./DFX-001-trajectory-observability.md) | `architecture/L2-Low-Level-Design/agent-runtime/Feat-DFX-001-trajectory-observability.md` |
| FEAT-001 | agent-runtime | active | 标准化 Agent 服务入口 | runtime 作为标准 Agent 服务端，对普通 client、其他 runtime、agent-bus forwarding 暴露同一 A2A Agent Card、JSON-RPC、SSE、Task、错误和租户上下文入口，并支持受信任 runtime-to-runtime webhook 异步完成回调。 | [FEAT-001-standardized-agent-service-entrypoint.md](./FEAT-001-standardized-agent-service-entrypoint.md) | `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-001-standardized-agent-service-entrypoint.md` |
| FEAT-002 | agent-runtime | active | 异构 Agent 框架兼容 | 通过统一 Adapter / Handler / SPI 抽象接入异构 Agent 框架；adapter 只桥接请求、调用和结果，不治理框架私有状态。 | [FEAT-002-heterogeneous-agent-framework-compatibility.md](./FEAT-002-heterogeneous-agent-framework-compatibility.md) | `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-002-heterogeneous-agent-framework-compatibility.md` |
| FEAT-003 | agent-runtime | active | 智能体任务状态缓存 | 新增标准化 Redis 缓存 SPI，运行时与开发框架复用 Redis 连接池，支持缓存 A2A Task 与 Agent checkpoint。 | [FEAT-003-agent-task-state-cache.md](./FEAT-003-agent-task-state-cache.md) | `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-003-agent-task-state-cache.md` |
| FEAT-004 | agent-runtime | active | 远程 Agent 编排 | runtime 作为 A2A 客户端接入远程 Agent，基于 Agent Card 生成本地工具，并支持远程调用、中断续接、进度投射和取消传播。 | [FEAT-004-remote-agent-orchestration.md](./FEAT-004-remote-agent-orchestration.md) | `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-004-remote-agent-orchestration.md` |
| FEAT-005 | agent-runtime | active | 智能体中间件请求代理 | runtime 在部署或启动阶段通过 Skill Hub SPI 代理访问 Skill Hub，使用 runtime 凭据下载 Agent 声明的 skill 包，并把注册材料移交给 agent-core 或框架适配入口。 | [FEAT-005-agent-middleware-request-proxy.md](./FEAT-005-agent-middleware-request-proxy.md) | 待补充 |
| FEAT-006 | agent-runtime | proposed | RESTful Client Facade | 面向普通业务 client 提供 REST 风格兼容入口，内部归一到 FEAT-001 的标准 Agent 服务入口语义，不作为 runtime-to-runtime、agent-bus 或事件总线协议。 | [FEAT-006-restful-client-facade.md](./FEAT-006-restful-client-facade.md) | 待补充 |
| FEAT-011 | agent-bus | active | 客户端调用路由转发 | agent-gateway 按目标 agentId / route 语义把客户端调用转发到目标 runtime，同时保持 runtime Task owner 和 A2A 表面不变。 | [FEAT-011-client-invocation-route-forwarding.md](./FEAT-011-client-invocation-route-forwarding.md) | 待补充 |
| FEAT-012 | agent-bus | active | 客户端调用总线转发 | agent-gateway 将客户端调用标准化为总线控制事件，通过 Event Bus 与 runtime consumer 协作，并把接受、响应、流准备、等待输入和终态投影交付给客户端。 | [FEAT-012-client-invocation-bus-forwarding.md](./FEAT-012-client-invocation-bus-forwarding.md) | 待补充 |
| FEAT-013 | agent-bus | draft | 客户端调用事件转发 | agent-bus 作为事件总线转发客户端调用事件与服务端响应事件，保持 A2A 调用/响应兼容，实时流内容继续走服务端 A2A SSE。 | [FEAT-013-client-invocation-event-forwarding.md](./FEAT-013-client-invocation-event-forwarding.md) | 待补充 |
| FEAT-014 | agent-bus | draft | A2A 调用事件转发 | event-bus 承载智能体服务之间 A2A 调用事件与响应事件转发，保持 Task owner、流式边界、幂等和路由治理语义清晰。 | [FEAT-014-a2a-call-event-forwarding.md](./FEAT-014-a2a-call-event-forwarding.md) | 待补充 |
| FEAT-015 / Feat-Func-015 | agent-bus/r-and-d-center | draft | Agent Card 注册与发现 | registry-discovery-center 承载 Agent Card 注册、发现、可见性、版本和能力目录事实，为 gateway、runtime 和平台集成提供发现基础。 | [FEAT-015-agent-card-registration-and-discovery.md](./FEAT-015-agent-card-registration-and-discovery.md) | `architecture/L2-Low-Level-Design/agent-bus/registry-discovery-runtime-design.cn.md` |
| FEAT-016 | agent-bus | draft | 运行时实例路由查询 | registry-discovery-center 支持已知目标的运行时实例路由查询，向 gateway 或 runtime 提供不暴露物理 endpoint 的路由引用和可用性投影。 | [FEAT-016-runtime-instance-route-query.md](./FEAT-016-runtime-instance-route-query.md) | 待补充 |
| FEAT-017 | agent-runtime | draft | 订阅消费总线事件消息 | runtime 内嵌订阅并消费客户端调用事件和服务间 A2A 请求事件，复用标准 A2A Task 控制面并发布接受、响应、等待输入、流准备和终态投影。 | [FEAT-017-bus-event-subscription-consumption.md](./FEAT-017-bus-event-subscription-consumption.md) | 待补充 |

## 4. 阅读顺序

1. 先阅读本入口，确认当前版本事实范围和文档关系。
2. 如果关注 runtime 对外服务入口，先读 `FEAT-001`，再读 `FEAT-002`、`FEAT-003`、`FEAT-004`、`FEAT-005`、`FEAT-006`、`FEAT-017` 和 `DFX-001`。
3. 如果关注 agent-bus 调用转发链路，按 `FEAT-011`、`FEAT-012`、`FEAT-013`、`FEAT-014`、`FEAT-017` 的顺序阅读。
4. 如果关注注册发现和路由，阅读 `FEAT-015` 与 `FEAT-016`，再回到调用转发特性确认 route handle、Agent Card 和 Task owner 边界。
5. 进入 `architecture/L1-High-Level-Design/` 和 `architecture/L2-Low-Level-Design/` 阅读对应架构和详细设计，确认内部设计如何满足这些事实要求。

## 5. 维护规则

- 新增当前版本特性时，必须在本入口登记 Feature ID、模块、状态、特性名称、简介、特性文档和 L2 详细设计对应关系。
- 特性文档是需求事实要求，不是实现复盘。新增或变更能力时，应先更新对应 `version-scope` 文档，再让 L2 设计、实现、测试和指南对齐该事实。
- 特性文档应保持黑盒视角，避免提前写入类名级实现细节；必要的 SPI、配置入口或事件名可以作为外部接口说明。
- 入口文档只保留简介和索引；详细能力清单、显式排除、场景旅程和行为语义应放入对应特性文档。
- 若文档文件名编号与 front matter 中的历史编号不完全一致，应在索引中同时保留两种编号，直到后续治理统一。
- 未纳入当前版本事实范围的能力不在本入口声明；如需记录后续规划，应放入独立 roadmap 或 backlog 文档。
