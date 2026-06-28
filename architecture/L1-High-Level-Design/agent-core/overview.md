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
---

# agent-core L1 架构概览

## 目的

本文档给出 `agent-core` 的 L1 高阶心智模型，概述模块目标、受众边界、问题领域和模块边界形态。

本文档以 L0 对 `agent-core` 的逻辑边界为权威口径，并把 `openJiuwen/agent-core-java@v0.1.12-jdk21` 作为未来主流 Java 实现基线进行能力映射。本文档不把 openJiuwen 整仓所有包等比例提升为 `agent-core` 职责，也不展开类级接口签名、配置项全集、测试矩阵或本地旧 `agent-core` 模块迁移方案。

## 模块目标

`agent-core` 是 L0 定义的智能体开发框架逻辑模块，负责 workflow / agent-loop / planner / node / tool / hook 等可组合执行组件，以及执行组件对中间件能力的调用意图生成、调用参数组装、恢复点记录和结果消费逻辑。`openJiuwen agent-core-java` 是该逻辑模块的主流 Java 实现基线，远端仓库 Maven 坐标为：

```xml
<groupId>com.openjiuwen</groupId>
<artifactId>agent-core-java</artifactId>
<version>0.1.12</version>
```

模块目标包括：

- 提供面向业务开发者的 Agent 开发组件：`ReActAgent`、`WorkflowAgent`、LLM Agent facade、workflow 图、组件、planner、node、tool intent 和 hook。
- 提供本地执行组件基础：Pregel 风格图执行、组件并发、异步 I/O、流式片段、组件内部状态、中断点表达和 checkpoint 接缝。
- 提供中间件调用编排逻辑：执行组件识别模型、记忆、知识检索、工具代理、沙箱和安全护栏需求，并通过 `agent-runtime` 的受治理执行契约调用 `agent-middleware` 服务。
- 提供 openJiuwen 仓内能力归属映射：对仓内 memory、retrieval、sysop、guardrail、agent_teams、agent_evolving、harness 等能力区分主用、适配、候选复用和不启用边界。
- 提供文档与示例：`documents/zh` 按开发指南和 API 文档组织，`examples` 提供 workflow、ReAct、groups、retrieval、store、skill 等示例。

## 受众边界

| 受众 | 主要需求 |
|---|---|
| 模块维护者 | 理解 openJiuwen Java 核心框架的能力域、代码组织、外部依赖和迁入本工程后的边界。 |
| Agent 应用开发者 | 使用 Java SDK 创建 ReActAgent、WorkflowAgent、workflow 组件、tool intent 和本地执行组件。 |
| 框架扩展开发者 | 扩展 workflow、agent-loop、planner、node、hook，以及对中间件能力的调用编排逻辑。 |
| Runtime 集成开发者 | 判断 agent-runtime 如何嵌入或适配 agent-core 的 Agent 执行、流式输出、中断恢复和状态语义。 |
| 架构评审者 | 判断该模块是否保持智能体开发框架定位，是否避免承担中间件服务执行、平台网关、Task owner、跨实例治理和演进平面职责。 |

## 问题领域

`agent-core` 解决的是 Java 侧大模型 Agent 应用的构建与本地执行问题。当前问题领域集中在以下方面：

1. **Agent 应用入口多样**
   ReAct、Workflow 和 LLM Agent facade 面向不同复杂度的应用形态，需要共享 workflow、agent-loop、组件、planner、hook 和本地执行辅助能力。

2. **工作流执行需要可恢复和可流式**
   Workflow 由组件、边、条件、循环和子工作流组成，执行过程中需要支持组件并发、流式传输、用户交互、中断恢复和状态保存。

3. **中间件能力需要通过受治理调用进入执行**
   模型、记忆、知识检索、工具代理、沙箱和安全护栏的具体执行逻辑归属 `agent-middleware`。`agent-core` 需要保留执行组件对这些能力的调用意图、参数组装、恢复点和结果消费逻辑，而不是直接拥有 provider 执行。

4. **Session / Context 需要区分本地辅助与服务侧 owner**
   openJiuwen 实现基线中存在本地 Session、Context 和 checkpoint 类型；它们在 SDK 模式下可作为本地执行辅助。进入平台路径时，服务侧 Session / Context shell、Task 级中断恢复入口和生命周期状态仍归属 `agent-runtime`。

5. **仓内高阶能力需要重新映射逻辑归属**
   Agent Teams、DeepAgent harness、Agent evolving、RL online/offline 和 dev tools 是 openJiuwen 仓内可评估资产，但不自动成为 `agent-core` 逻辑职责。它们需要按 L0 映射到 `agent-runtime`、`agent-middleware`、`agent-evolve` 或仅作为参考实现。

## 模块边界形态

| 边界项 | agent-core 负责 | agent-core 不负责 | 事实下沉位置 |
|---|---|---|---|
| Java SDK | 提供 Agent facade、Workflow、ReAct loop、planner、node、tool intent、hook 和组件编排 API。 | 不提供平台级服务网关、多租户控制面或服务侧 Agent definition owner。 | `api-appendix.md`, `development.md` |
| 本地执行组件 | 提供图执行、组件执行、Runner 辅助、回调、流式片段和组件内部中断点表达。 | 不拥有服务端 Task 生命周期、跨 JVM 调度、分布式锁和平台级 drain coordination。 | `logical.md`, `process.md`, `physical.md` |
| 中间件调用 | 保留驱动 `agent-runtime` 调用模型、记忆、检索、工具代理、沙箱和 guardrail 的调用逻辑。 | 不拥有这些服务的 provider 执行、容量、幂等、审计和治理逻辑。 | `logical.md`, `spi-appendix.md` |
| 状态与上下文 | 拥有 Task 边界以下的 workflow node、ReAct loop 和组件内部状态；SDK 本地 Session/checkpoint 可作为辅助。 | 不拥有服务侧 Session / Context shell、Task lifecycle state、Memory/Knowledge provider 状态。 | `logical.md`, `process.md` |
| 多智能体与演化 | 对 openJiuwen `agent_teams`、`agent_evolving`、`harness` 做候选资产评估和归属映射。 | 不把团队生命周期、跨边界协同、训练平台、评测服务和演进微服务纳入 core 主体。 | `scenarios.md`, `development.md` |
| 文档与示例 | 提供 `documents/zh` 与 `examples`。 | 不把教程文档视为稳定 API 契约本身。 | `development.md` |

## openJiuwen 仓内能力归属映射

| openJiuwen 仓内能力 | L0 逻辑归属 | agent-core L1 采用策略 |
|---|---|---|
| `core.workflow`、`core.graph`、`core.singleagent`、ReAct / Workflow / component / planner / hook 相关能力 | `agent-core` | 作为主流实现基线的主体能力采用。 |
| `foundation` 中模型、工具、提示词和消息 schema | `agent-core` 调用逻辑 + `agent-middleware` 服务执行 | 可复用 schema、intent 和适配代码；provider 执行和治理不归 core。 |
| `memory`、`retrieval`、store、context processor | `agent-middleware`，由 `agent-runtime` 协调调用 | 可作为中间件候选实现或适配资产，不默认作为 core owner。 |
| `sysop`、sandbox、guardrail、安全工具 | `agent-middleware` + `agent-runtime` 治理 | 可复用工具定义和局部实现，但平台模式下必须经过授权、容量、幂等、审计和安全边界。 |
| `agent_teams`、`multiagent` | `agent-runtime` 同节点协作；跨边界进入 `agent-bus` | 仅可复用同边界执行算法或上下文拆分辅助；团队生命周期、成员状态和 Task tree owner 不归 core。 |
| `agent_evolving`、online/offline RL、trainer、evaluator、trajectory | `agent-evolve` | 作为演进平面候选资产评估，目标形态应可拆为数据采集/存储、处理流水线、采样运行时、仿真、在线/离线优化微服务。 |
| `harness`、`deepagents`、`auto_harness`、`dev_tools` | 视能力分别映射到 `agent-core`、`agent-runtime`、`agent-middleware` 或工具链 | 可参考和选择性复用，不作为 core 主体默认启用。 |

## 成熟度说明

远端仓库 `TEST_COMPILE_STATUS.md` 标明：主代码编译成功，测试编译仍有 32 个测试文件存在错误。因此本 L1 将主代码能力视为可分析的当前实现事实，但测试成熟度和迁入后的工程适配仍需在 L2 或迁移计划中关闭。
