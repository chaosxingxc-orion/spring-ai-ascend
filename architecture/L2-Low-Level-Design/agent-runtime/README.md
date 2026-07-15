---
level: L2-LLD
module: agent-runtime
status: active
dependency:
  - ../../README.md
  - ../../L1-High-Level-Design/agent-runtime/README.md
---

# agent-runtime L2 详细设计

## 目的

本目录保存 `agent-runtime` 模块当前生效的 L2 详细设计事实。

L2 详细设计是在对应模块 L1 高阶设计指导下，按特性颗粒度展开实现级设计。它回答“某个已接受特性如何由当前代码实现、由哪些类协作承接、由哪些运行流程和配置使用方式驱动”，不重新定义 L0/L1 已经确定的系统边界、模块职责、逻辑对象归属、部署形态、资源模型或跨模块依赖方向。模块级 API/SPI 契约统一在 L1 API/SPI 附录中维护。

## 范围

本目录只描述与代码严格对应的当前事实，包括：

- 已实现或已接受的功能特性设计。
- 已实现或已接受的非功能特性设计，例如可观测性、可靠性、安全、性能、可运维性等 DFX 设计。
- 与特性内部实现直接相关的类协作、配置使用、错误语义、运行流程和当前限制。
- 由 L1 `agent-runtime` 高阶设计约束出的实现边界。

本目录不保存 draft / proposal / archive 材料。草稿、提案、评审记录、历史归档和未接受设计应放在 `docs/` 目录下；只有经当前架构事实接纳、并能与代码事实对应的内容，才进入 `architecture/L2-Low-Level-Design/agent-runtime/`。

## 命名规则

L2 特性文档按功能特性和非功能特性分组命名：

- 功能特性：`Feat-Func-[3 digits number]-[short name].md`
- 非功能特性：`Feat-DFX-[3 digits number]-[short name].md`

编号在各自分组内递增并保持稳定。`short name` 使用小写英文短语和连字符，表达特性边界，不表达实现状态。

## 阅读路径

1. 先阅读 `architecture/L1-High-Level-Design/agent-runtime/README.md`，确认 `agent-runtime` 的模块定位、4+1 视图入口和 L1/L2 边界。
2. 按本文档的特性清单定位目标 L2 文档。
3. 对实现、测试、配置或排障做事实判断时，以当前代码、模块元数据、测试和契约为准；若 L2 文字与代码事实冲突，应停止并修正文档或代码事实。
4. 涉及 draft / proposal / archive 的材料时，到 `docs/` 下查阅，不把它们当作当前架构事实。

## 功能特性清单

| 编号 | 文档 | 特性 | 当前事实边界 |
|---|---|---|---|
| Feat-Func-001 | [标准化 Agent 服务入口](Feat-Func-001-standardized-agent-service-entrypoint.md) | A2A northbound 接入、Agent Card、普通 client/其他 runtime/agent-bus forwarding inbound、阻塞/流式/异步 S2C 通讯、A2A 执行桥接。 | 标准 Agent 服务入口和 Task 表面映射。 |
| Feat-Func-002 | [异构 Agent 框架兼容](Feat-Func-002-heterogeneous-agent-framework-compatibility.md) | `AgentRuntimeHandler` 适配模型、核心 SPI 与状态边界、OpenJiuwen ReAct/Workflow/DeepAgent、AgentScope、Versatile adapter 接入。 | 框架中立执行 SPI、状态归属原则与具体 adapter 协作；adapter 不治理框架 cache/checkpointer、hook、rail、tool、skill。 |
| Feat-Func-003 | [智能体任务状态缓存](Feat-Func-003-agent-task-state-cache.md) | 标准化 Redis 缓存 SPI、原生 Redis 单机/集群策略、客户封装 Redis 适配、A2A Task 与 checkpoints 缓存复用。 | 任务状态缓存、Redis 连接池复用、客户适配扩展点、日志脱敏和内部验收边界。 |
| Feat-Func-004 | [远程 Agent 编排](Feat-Func-004-remote-agent-orchestration.md) | 远程 Agent Card 拉取、Tool 注入、中断-续接、远程调用结果回灌。 | runtime 作为 A2A client 编排其他 Agent。 |

## 非功能特性清单

| 编号 | 文档 | 特性 | 当前事实边界 |
|---|---|---|---|
| Feat-DFX-001 | [轨迹可观测性](Feat-DFX-001-trajectory-observability.md) | Trajectory 事件模型、stamping、masking、sink、A2A 北向投递与 OTel 导出边界。 | Agent 执行轨迹的框架中立记录与输出。 |

## 非架构事实材料

以下材料不是当前 L2 架构事实，保留在 `docs/` 下作为草稿、提案、归档或独立验证材料：

| 材料 | 位置 | 状态 | 说明 |
|---|---|---|---|
| 中间件解耦样例配置与端到端验证指南 | `docs/architecture/l2/agent-runtime/drafts/middleware-decoupling-examples.cn.md` | draft | 面向测试团队和客户开发团队的样例配置与独立验证说明。 |
