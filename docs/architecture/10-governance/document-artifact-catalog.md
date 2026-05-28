---
level: L1
view: governance
status: draft
---

# Document Artifact Catalog

## 目的

登记 `docs/architecture/` 的目录级职责，说明每个目录主要承载什么内容、服务什么 A2D 活动、下级文档如何展开和如何检查质量。

本文先管理目录，不直接管理所有文件。原因是不同目录的展开方式不同，尤其 `02-modules/<module>/` 下每个模块可能拥有自己的文件组织方法。文件级清单应由对应目录的 README 或模块级 catalog 承担。

## 适用读者

架构负责人、模块负责人、文档作者、评审者、AI agent、harness 生成器。

## 维护规则

- 每个 `docs/architecture/` 下的一级目录必须在本文登记。
- 重要的二级目录如果有独立职责，也必须在本文登记，例如 `02-modules/<module>/`、`05-contracts/human-readable/`、`06-scenarios/technical/`。
- 本文只定义目录级主职责；目录内的文件级管理由该目录 README、模块 README 或模块级 catalog 继续展开。
- 新增、删除、改名目录，或改变目录职责时，必须同步更新本文。
- 如果某个文件找不到清晰归属，先回到本文判断它应该属于哪个目录；如果没有合适目录，再判断是否需要新增目录。
- 如果某个模块需要特殊文件组织方式，必须在该模块 README 中说明，不要求强行套用其他模块的文件结构。

## 字段说明

| 字段 | 含义 |
|---|---|
| 目录 / 文件组 | 被管理的目录，或根目录下必须单独说明的一组文件。 |
| 管理粒度 | 本文管理到目录、文件组、模板目录还是具体文件。 |
| 主要内容 | 该目录应该承载的信息范围。 |
| 主要作用 | 该目录在 A2D 或评审中的用途。 |
| A2D 活动 | 主要由哪些 A2D 活动产出或维护。 |
| 下级展开规则 | 目录内部如何继续管理文件。 |
| 质量检查点 | 评审时需要重点检查的约束。 |

## 目录总览

| 目录 / 文件组 | 管理粒度 | 主要内容 | 主要作用 | A2D 活动 | 下级展开规则 | 质量检查点 |
|---|---|---|---|---|---|---|
| 根目录文件组：`README.md`、`task.md`、`constraint-and-design-inventory.md` | 文件组 | 文档集入口、原始任务输入、全局约束和冲突清单。 | 提供文档体系入口、raw input 暂存和跨目录问题索引。 | A0 需求进入 / A1 架构准入判定 / A10 版本归档 | 根目录只保留全局入口和全局索引；正式设计应迁移到对应目录。 | `task.md` 不得被当成 accepted 设计；全局 Conflict / Open Issue 必须有后续位置或 owner。 |
| `00-overview/` | 目录 | L0 系统概览、术语、系统原则和运行架构心智模型。 | 帮助读者先理解系统运行架构，而不是实现细节。 | A1 架构准入判定 / A4 模块责任承接 / A10 版本归档 | 目录内可以按 overview、glossary、principles 展开；新增文件必须仍服务 L0 心智模型。 | 不得把 BoM、starter、demo、fixture 或依赖管理写成 L0 架构模块。 |
| `01-capabilities/` | 目录 | 能力地图、能力 owner、能力与场景/模块/验证方式的映射。 | 检查核心场景是否有能力承接，并支撑模块并行开发。 | A3 能力拆解 | 先维护总能力地图；能力复杂到需要独立生命周期时，再增加 `<capability-id>.md`。 | 能力不得伪装成模块；每个关键能力必须有 owner 或 Open Issue。 |
| `02-modules/` | 目录 | 模块责任总览和各模块设计包入口。 | 管理模块边界、模块设计包和并行开发入口。 | A4 模块责任承接 / A6 模块详细设计 | 根下维护 `module-responsibility-cards.md`；每个模块使用 `02-modules/<module>/` 自己展开。 | 主键必须是真实模块；支撑框架、依赖、starter 不得作为 L0 模块。 |
| `02-modules/<module>/` | 模板目录 | 单个模块的 README、设计、状态、流程、开发视图、harness、open issues 或其他模块自定义文档。 | 让模块负责人把系统级职责转成可开发、可验证的模块设计包。 | A6 模块详细设计 / A7 Harness 设计与生成 / A8 实现任务拆解 | 每个模块必须至少有 README 说明本模块文件管理方法；是否使用 4+1、state model、process design 等文件由模块复杂度决定。 | 模块文件结构可以不同，但必须能追溯到场景、能力、状态、契约、harness 和验证矩阵。 |
| `02-modules/agent-service/` | 当前模块目录 | agent-service 当前模块设计包。 | 作为第一个已展开的模块样例，承接 service 对外入口、Task 生命周期、SSE、查询、回调等设计。 | A6 模块详细设计 / A7 Harness 设计与生成 / A8 实现任务拆解 | 文件级索引由该目录 README 维护；后续如内容继续增长，可增加模块级 catalog。 | 必须遵守 Task 是服务端状态、Run 仅为历史或 client 视角兼容的口径；不得越界承担 Bus 或 Gateway 职责。 |
| `03-state/` | 目录 | 状态 owner、writer、reader、forbidden writer 和状态边界。 | 作为状态一致性和跨模块写入边界的主索引。 | A5 状态与契约设计 | 当前以状态矩阵为主；状态模型细节可下沉到模块目录。 | 每个核心状态只能有一个 owner；禁止隐式多写。 |
| `04-adrs/` | 目录 | 交付视角 ADR 草案和待提升决策。 | 记录当前 A2D 过程中的设计决策草案，不替代正式 `docs/adr/`。 | A5 状态与契约设计 / A10 版本归档 | 每个草案独立成文；提升为正式决策时迁移或同步到权威 ADR 目录。 | 必须诚实标记 draft；不得把草案写成已 runtime enforced。 |
| `05-contracts/` | 目录 | 人类可读 ICD 和机器可读 contract draft。 | 管理跨模块交互契约，并支撑 mock、stub、contract test 和 harness。 | A5 状态与契约设计 / A7 Harness 设计与生成 | 下分 human-readable 与 machine-readable；同一契约应保持语义配对。 | 机器可读 YAML 必须是 harness-first draft，进入生产前必须同步正式 contract catalog。 |
| `05-contracts/human-readable/` | 二级目录 | 人类可读 ICD、交互语义、错误语义、调用方向和边界说明。 | 让人类先确认契约语义，再生成机器可读草案。 | A5 状态与契约设计 | 每个 ICD 可以对应一个或多个 machine-readable draft；复杂契约可拆分多个 ICD。 | 必须说明参与模块、控制路径、数据路径、状态影响和 open issues。 |
| `05-contracts/machine-readable/` | 二级目录 | YAML contract draft、mock/stub/test 生成输入。 | 为 harness-first 验证提供机器可读素材。 | A5 状态与契约设计 / A7 Harness 设计与生成 | 每个 YAML 必须能追溯到 human-readable ICD；字段语义以 ICD 为准。 | 必须保持 `status: draft`；不得声明 production enforcement。 |
| `06-scenarios/` | 目录 | BA-* 业务活动场景、场景索引和场景管理规则。 | 用真实业务活动检验模块划分、能力覆盖和契约完整性。 | A2 核心场景建模 | BA-* 是主线；technical 子场景只能作为机制验证。 | 核心场景必须是业务活动，不能只列技术流程。 |
| `06-scenarios/technical/` | 二级目录 | 技术机制子场景，例如创建 Task、执行 step、上下文装配、工具调用、暂停恢复、A2A federation。 | 支撑 BA 场景下的机制级验证和 harness 生成。 | A2 核心场景建模 / A7 Harness 设计与生成 | 每个 technical scenario 必须挂到至少一个 BA-*，或明确标记为 future / open issue。 | 不得替代 BA-* 成为核心场景；历史 Run 命名必须说明当前 Task 口径。 |
| `07-invariants/` | 目录 | 架构不变量、禁止路径和可检查约束。 | 支撑静态检查、评审和 harness 断言。 | A5 状态与契约设计 / A9 集成验证与架构评审 | 当前以总不变量清单为主；模块特有不变量可下沉到模块目录。 | 不变量必须可验证，并能追踪到场景、状态、契约或原则。 |
| `08-harness/` | 目录 | 系统级或跨模块 harness 规格。 | 将场景、状态、契约和不变量转成可执行验证思路。 | A7 Harness 设计与生成 | 按能力或跨模块机制组织；模块专属 harness 优先放到模块目录。 | 每个 harness spec 必须能追溯到场景、契约、状态或 verification row。 |
| `09-verification/` | 目录 | 验证矩阵、测试策略和验证证据索引。 | 作为设计项到测试、评审、CI 或人工验证的追踪入口。 | A9 集成验证与架构评审 | verification matrix 是主索引；test strategy 说明测试组织方式。 | 每个关键设计项必须有验证方式或未覆盖说明。 |
| `10-governance/` | 目录 | A2D 工作模型、目录 catalog、文档约束、评审流程、变更治理和版本归档。 | 管理文档体系本身，约束 AI 与人类协作方式。 | A9 集成验证与架构评审 / A10 版本归档 | 治理文件可以继续按工作模型、约束、流程、baseline 等子目录展开。 | 过程发现的新文档问题必须沉淀为约束或 catalog 更新。 |
| `10-governance/a2d-intake/` | 预留目录 | 结构化需求入口记录。 | 保存从 raw input 进入 A2D 的正式入口。 | A0 需求进入 | 当前可先用 `task.md` 过渡；稳定后按 `<id>.md` 建档。 | 每份 intake 必须说明用户、目标、约束、进入判断和澄清问题。 |
| `10-governance/admission-decisions/` | 预留目录 | 准入分类和处理结论。 | 保存 Architecture Module / Capability / Contract / State 等分类判断。 | A1 架构准入判定 | 当前可先在 inventory 中记录；稳定后按 `<id>.md` 建档。 | 每个被纳入 Overview 或模块边界的对象必须有准入依据。 |
| `10-governance/baselines/` | 预留目录 | 阶段性 architecture baseline note。 | 管理版本归档、superseded 关系和遗留问题结转。 | A10 版本归档 | 当前可先在 README 或 PR 中记录；稳定后按 `<version>.md` 建档。 | baseline 必须说明 active、draft、superseded 和 carried open issues。 |

## 下级展开规则

### 系统级目录

`00-overview/`、`01-capabilities/`、`03-state/`、`04-adrs/`、`05-contracts/`、`06-scenarios/`、`07-invariants/`、`08-harness/`、`09-verification/`、`10-governance/` 是系统级目录。

系统级目录的展开规则：

- 目录职责由本文定义。
- 文件级索引优先放在该目录 README；没有 README 时，由目录中主文档承担入口职责。
- 新增文件必须说明它补充哪个 A2D 产物，不能只是因为“有内容可写”而新增。
- 当目录内文件数量或类型变多时，可以新增目录级 README 或局部 catalog。

### 模块级目录

`02-modules/<module>/` 是模块级目录。模块级目录可以拥有自己的文件管理方法。

模块级目录的最低要求：

- 必须有 README，说明本模块设计包的文件地图和阅读顺序。
- 必须说明哪些文件是当前 accepted，哪些是 draft，哪些只是迁移参考。
- 必须能追溯到系统级场景、能力、状态、契约、harness 和 verification matrix。
- 如果模块内部文件结构与 `agent-service` 不同，只要 README 说明清楚即可，不要求强制一致。

### 文件级 catalog 的触发条件

只有在以下情况下，才需要继续打开文件级 catalog：

- 某个目录下文件数量较多，README 已不足以说明职责边界。
- 某个模块存在多条并行开发线，需要明确每个文件的 owner、状态和验证关系。
- 评审中多次出现文件职责漂移、重复承载或归档位置不清。
- AI 需要基于目录内文件自动生成 harness、任务或评审报告，而 README 信息不足。

文件级 catalog 应放在对应目录内，例如：

```text
docs/architecture/02-modules/<module>/document-artifact-catalog.md
docs/architecture/06-scenarios/document-artifact-catalog.md
```

## 反模式

- 顶层 catalog 直接展开所有文件，导致模块无法使用自己的文件管理方法。
- 模块目录没有 README，却依赖全局 catalog 解释模块内部文件。
- 新增目录没有说明主职责，只是把相似文件堆在一起。
- 技术子场景、契约草案、harness spec 找不到所属 BA 场景或 A2D 活动。
- 目录职责改变后，只改文件内容，不更新本文。

