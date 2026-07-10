---
level: L1
view: version-scope
module: agent-runtime
status: active
updated: 2026-07-09
authority: "ADR-0159 (agent-runtime consolidation) + current version facts"
covers: [标准化Agent服务入口, 异构Agent框架兼容, 智能体任务状态缓存, 远程Agent编排, RESTful Client Facade, 客户端调用事件转发, 轨迹可观测性]
---

# agent-runtime version-scope

`version-scope` 是当前版本的事实范围描述目录，用于说明本版本已经纳入范围、需要被设计与实现对齐的需求事实。它不是长期路线图，也不是模块详细设计本身；它回答的是：当前版本对外承诺哪些能力、这些能力的外部行为边界是什么、哪些文档是后续详细设计与实现校验的事实来源。

本目录下的特性文档是从需求侧出发的指挥棒和驱动力，是特性设计、实现、测试与指南必须对齐的事实要求。这里的描述即事实；如果设计或实现已经先行产生了新能力，也必须先回到 `version-scope` 明确其是否纳入当前版本事实范围，再由 L2 设计和实现承接。

## 1. 文档目的

本目录承载 `agent-runtime` 当前版本的需求类文档，包括：

| 文档类型 | 说明 |
|---|---|
| 原始需求文档 | 记录需求来源、业务动机、版本目标和约束条件。 |
| 场景设计文档 | 记录面向用户或系统集成方的典型使用场景、交互链路和端到端流程。 |
| 特性用例文档 | 以外部视角描述特性的黑盒行为，包括能力边界、外部接口、用户示例和 E2E 流程。 |

其中，特性用例文档是本目录的核心产物。它们侧重外部可观察行为，不展开模块内部结构；`architecture/L2-Low-Level-Design/` 中各模块详细设计会引用这些特性用例文档，并与其一一对应。

## 2. 范围边界

本目录只描述当前版本已经纳入事实范围的能力：

- 只声明当前版本范围内的特性，不记录后续路线图。
- 只描述外部行为、能力边界、接口入口和用户可见流程。
- 不替代 L2 详细设计；内部模块拆分、类设计、数据结构和实现策略由 `architecture/L2-Low-Level-Design/` 承载。
- 不替代开发者指南；安装、配置和完整操作手册由 `agent-runtime/docs/guides/` 承载。

## 3. 特性索引

| Feature ID | 特性 | 当前版本范围简介 | 特性用例文档 | L2 详细设计对应关系 |
|---|---|---|---|---|
| FEAT-001 | 标准化 Agent 服务入口 | runtime 作为标准 Agent 服务端，对普通 client、其他 runtime、agent-bus forwarding 暴露同一 A2A Agent Card、JSON-RPC、SSE、Task、错误和租户上下文入口，并支持受信任 runtime-to-runtime webhook 异步完成回调。 | [FEAT-001-standardized-agent-service-entrypoint.md](./FEAT-001-standardized-agent-service-entrypoint.md) | `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-001-standardized-agent-service-entrypoint.md` |
| FEAT-002 | 异构 Agent 框架兼容 | 通过统一 Adapter / Handler / SPI 抽象接入 OpenJiuwen ReActAgent、Workflow、DeepAgent、AgentScope 和 Versatile REST 代理；adapter 只桥接请求、调用和结果，不治理框架 cache/checkpointer、hook、rail、tool、skill。 | [FEAT-002-heterogeneous-agent-framework-compatibility.md](./FEAT-002-heterogeneous-agent-framework-compatibility.md) | `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-002-heterogeneous-agent-framework-compatibility.md` |
| FEAT-003 | 智能体任务状态缓存 | 新增标准化 Redis 缓存 SPI，运行时与开发框架复用 Redis 连接池，支持缓存 A2A Task 与 checkpoints。 | [FEAT-003-agent-task-state-cache.md](./FEAT-003-agent-task-state-cache.md) | `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-003-agent-task-state-cache.md` |
| FEAT-005 | 远程 Agent 编排 | runtime 作为 A2A 客户端接入远程 Agent，基于 Agent Card 生成本地工具，并支持远程调用、中断续接、进度投射和取消传播。 | [FEAT-005-remote-agent-orchestration.md](./FEAT-005-remote-agent-orchestration.md) | `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-005-remote-agent-orchestration.md` |
| FEAT-006 | RESTful Client Facade | 面向普通业务 client 提供 REST 风格兼容入口，内部归一到 FEAT-001 的标准 Agent 服务入口语义，不作为 runtime-to-runtime、agent-bus 或事件总线协议。 | [FEAT-006-restful-client-facade.md](./FEAT-006-restful-client-facade.md) | 待补充 |
| FEAT-013 | 客户端调用事件转发 | agent-bus 作为事件总线转发客户端调用事件与服务端响应事件，保持 A2A 调用/响应兼容，实时流内容继续走服务端 A2A SSE。 | [FEAT-013-client-invocation-event-forwarding.md](./FEAT-013-client-invocation-event-forwarding.md) | 待补充 |
| DFX-001 | 轨迹可观测性 | 记录 Agent 执行过程中的运行、模型调用、工具调用、错误和进度事件，提供框架中立的执行轨迹与敏感信息掩码。 | [DFX-001-trajectory-observability.md](./DFX-001-trajectory-observability.md) | `architecture/L2-Low-Level-Design/agent-runtime/Feat-DFX-001-trajectory-observability.md` |

## 4. 阅读顺序

1. 先阅读本入口，确认当前版本事实范围和文档关系。
2. 按 Feature ID 阅读对应特性用例文档，确认外部行为、能力边界和场景流程。
3. 进入 `architecture/L2-Low-Level-Design/agent-runtime/` 阅读对应详细设计，确认内部设计如何满足特性用例。
4. 进入 `agent-runtime/docs/guides/` 和 `examples/` 查看开发指导与可运行样例。

## 5. 维护规则

- 新增当前版本特性时，必须在本入口登记 Feature ID、特性名称、简介、特性用例文档和 L2 详细设计对应关系。
- 特性文档是需求事实要求，不是实现复盘。新增或变更能力时，应先更新对应 `version-scope` 文档，再让 L2 设计、实现、测试和指南对齐该事实。
- 特性用例文档应保持黑盒视角，避免提前写入类名级实现细节；必要的 SPI 或配置入口可以作为外部接口说明。
- 入口文档只保留简介和索引；详细能力清单、显式排除、用户示例和 E2E 流程应放入对应特性用例文档。
- 未纳入当前版本事实范围的能力不在本入口声明；如需记录后续规划，应放入独立 roadmap 或 backlog 文档。
