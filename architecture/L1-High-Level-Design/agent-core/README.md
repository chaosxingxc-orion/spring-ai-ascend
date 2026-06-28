---
level: L1-HLD
TAG:
  - entry
  - governance
  - reading-path
  - architecture-fact
status: active
dependency:
  - overview.md
  - scenarios.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
  - glossary.md
---

# agent-core L1 架构高阶设计

## 目的

本目录是 `agent-core` 模块的 L1 高阶设计入口，用于说明后续以 `openJiuwen/agent-core-java@v0.1.12-jdk21` 为主流实现基线迭代时，应遵循的模块定位、职责范围、4+1 视图、主要能力、公共 API/SPI、依赖方向和阅读路径。

本文档以上层 L0 对 `agent-core` 的逻辑边界为权威口径，并以远端仓库 `https://gitcode.com/openJiuwen/agent-core-java/tree/v0.1.12-jdk21` 作为主流实现基线事实来源；tag `v0.1.12-jdk21` 对应提交 `dbd58ef84b122130f631372e3bf6bb960933f5e4`。本地当前 `agent-core` 模块不会作为事实来源；该模块后续可被移除并由 openJiuwen 基线中符合 L0 边界的能力替代或吸收。仓内不符合 `agent-core` 逻辑归属但有复用价值的能力，应映射到 `agent-runtime`、`agent-middleware`、`agent-evolve`、`agent-bus` 或工具链候选资产，不强删、不强依赖、不默认启用。

L1 文档回答“`agent-core` 是什么、为什么这样分层、对外承诺什么、与哪些运行时和业务模块协作”。它不展开单个特性的类级实现、完整配置示例、模型供应商参数、测试修复清单和运维脚本；这些内容应进入 L2 详细设计、开发指南或版本范围文档。

## 文档地图

| 文件 | 作用 |
|---|---|
| `README.md` | 入口、目的范围、文档地图、阅读路径和文档规范。 |
| `overview.md` | 架构概览：模块目标、受众边界、问题领域和模块边界形态。 |
| `scenarios.md` | 场景视图：关键用户/系统场景，用于连接架构视图和功能验证。 |
| `logical.md` | 逻辑视图：领域模型、核心抽象、关键类型关系、状态边界、分层和依赖方向。 |
| `development.md` | 开发视图：Maven 形态、命名空间、包结构、依赖、资源、文档和测试边界。 |
| `process.md` | 进程视图：主要执行流程、同步/异步边界、中断恢复、流式输出和错误处理。 |
| `physical.md` | 物理视图：部署模式、进程边界、资源、外部服务和存储拓扑。 |
| `api-appendix.md` | API 附录：Agent、Workflow、Runner、组件、中间件调用和候选资产公开入口。 |
| `spi-appendix.md` | SPI 附录：模型/工具调用、存储候选、回调、检查点、guardrail、MCP 和扩展面。 |
| `glossary.md` | 模块术语表：定义 agent-core 内部容易混淆的术语和边界。 |

## 阅读路径

1. 阅读 `overview.md`，先建立模块定位、当前能力、公共契约和 L1 范围边界。
2. 阅读 4+1 视图文档：`scenarios.md` -> `logical.md` -> `development.md` -> `process.md` -> `physical.md`。
3. 阅读 `api-appendix.md`，确认面向业务开发者的 SDK 入口。
4. 阅读 `spi-appendix.md`，确认模型、工具、存储、回调、检查点和安全扩展面。
5. 阅读 `glossary.md`，确认模块内术语、状态和边界命名。
6. 当需要实现、测试或排查某个具体特性时，进入 L2 详细设计或 openJiuwen 原仓的 `documents/zh/2.开发指南/`、`examples/` 与 `src/test/`。

## 文档规范

`agent-core` 是面向 Java 生态的智能体开发框架和执行组件边界。它提供 Agent 创建、ReAct 推理、Workflow 编排、图执行、planner、node、tool intent、hook、组件内部状态和驱动中间件调用的逻辑。模型服务、记忆服务、知识检索服务、工具代理服务、沙箱服务和安全护栏服务的具体执行归 `agent-middleware`；服务侧 Task 生命周期、Session / Context shell 和 Agent definition 入口归 `agent-runtime`；演进训练和评估平面归 `agent-evolve`。

本 L1 范围包括模块目标、构建形态、部署位面、逻辑分层、核心抽象、状态归属、执行流程、同步/异步边界、流式片段、中断点表达、包结构、Maven 依赖、公开 API、扩展 SPI、关键场景、openJiuwen 仓内能力归属映射和测试成熟度。

L1 与 L2 的边界按“模块地图”和“特性落地”划分：

- L1 保留模块级事实：职责、边界、视图、能力总览、稳定入口和跨模块约束。
- L2 展开特性级事实：类协作、配置项全集、模型供应商适配、工作流组件实现、检索 pipeline、记忆策略、Agent evolving 算法和测试修复细节。

本 L1 可以保留影响模块边界判断、API/SPI 稳定性、部署形态、状态归属和运行路径的关键代码事实，例如 Maven 坐标、Java 版本、一级包、核心 Agent/Workflow/Runner 类型、主要状态名、流式与中断语义、外部依赖类别、能力归属映射和测试成熟度。完整 API 类型页、教程示例和测试错误修复进度由 openJiuwen 原仓文档与后续 L2 文档维护。
