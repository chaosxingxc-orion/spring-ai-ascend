---
level: L1
view: scenarios
status: draft
---

# Architecture Documentation Constraints

## 目的

定义 `docs/architecture/` 文档体系自身必须遵守的约束。这里沉淀过程检查中发现的问题和后续约定的标准模式，使架构文档质量可以持续约束，而不是靠一次性人工记忆。

## 适用读者

架构文档作者、模块负责人、AI agent、架构评审者。

## 维护规则

- 每次评审发现“文档结构类”问题，都要判断是否应新增或修订本文件的约束。
- 本文件只约束 `docs/architecture/` 的写作和组织方式；正式系统规则仍以 `CLAUDE.md`、`docs/governance/*`、`docs/adr/` 为准。
- 新约束必须包含：规则、禁止模式、检查方式、适用文档。

## 1. 模块口径约束

### DOC-C-001: 真实模块只能来自 root `pom.xml`

**规则：** 凡是标题或表格声称在定义“真实 reactor module”，主键必须来自 root `pom.xml`。但真实 reactor module 不自动等于 L0 架构模块；进入 Overview 的模块边界前，还必须通过 DOC-C-001.a 的准入判定。

当前真实模块口径：

```text
agent-client
agent-bus
agent-service
agent-execution-engine
agent-middleware
agent-evolve
spring-ai-ascend-dependencies
spring-ai-ascend-graphmemory-starter
```

**禁止模式：**

- 把 `Gateway`、`Workflow`、`Context Engine`、`Tool Gateway`、`Observability` 写成真实模块。
- 把 root `pom.xml` 清单无条件复制为 L0 架构模块清单。

**检查方式：**

- 人工检查所有“模块”表格的第一列。
- 后续可做脚本：扫描 `docs/architecture/**/*.md` 中 `| 模块 |`、`| 真实模块 |` 表格行，校验第一列是否在 root `pom.xml` 模块集合内。

**适用文档：** Overview、Module Cards、State Matrix、Harness Spec。

### DOC-C-001.a: Overview 模块边界必须先做准入判定

**规则：** 写 `Architecture Overview` 时，必须先把现有项分类为：

```text
Architecture Module
Runtime Component
Capability
Contract
State
Build Artifact
Packaging Artifact
Implementation Constraint
Open Issue
```

只有 `Architecture Module` 和必要的 `Runtime Component` 可以进入 Overview 的模块职责边界。

**禁止模式：**

- 把 BoM、dependency management、starter、test fixture、demo、adapter scaffold 写成 L0 架构模块。
- 因为某个东西存在于 root `pom.xml`，就自动把它写进 Overview 模块边界。
- 为了“保留现有内容”而把构建或发布工件提升成架构模块。

**检查方式：**

- Overview 的模块边界前必须有准入判定表或等价说明。
- 准入结果为 `Build Artifact`、`Packaging Artifact`、`Implementation Constraint` 的项，只能下沉到 build governance、implementation constraint、adapter appendix 或 Open Issue。

**适用文档：** Overview、Architecture Review Process、后续自动化检查。

### DOC-C-002: 能力聚合必须显式标记为 capability

**规则：** `Gateway`、`Workflow / Orchestrator`、`Context Engine`、`Tool Gateway`、`Observability`、`Runtime Governance` 等是能力聚合名。它们可以作为 capability、provided capability、consumed capability、scenario role，但不得作为真实模块主键。

**禁止模式：**

- `Module Name: Tool Gateway capability` 这类写法出现在 Module Responsibility Cards 的主卡片中。
- 在 Overview 中把能力聚合图命名为“模块图”而不说明它只是运行链路视图。

**检查方式：**

- Capability Map 可以用能力名做主键。
- Module Cards 必须用真实 module 做主键。
- Overview 如果展示 capability flow，标题必须写成“运行链路”“能力链路”或“控制流”，不能写成“真实模块列表”。

**适用文档：** Overview、Capability Map、Module Cards、Harness Spec。

### DOC-C-002.b: Gateway / Bus / Stream / Data Path 不得混写

**规则：** 文档中出现 Gateway、Bus、SSE、data reference 或 data path 时，必须遵守以下口径：

- Gateway 是微服务框架入口能力；当前只代理对外暴露的 `agent-service`。
- `agent-service` 拥有 HTTP 对外入口、Task lifecycle、Task query 和 SSE streaming endpoint。
- `agent-bus` 只承载 S2C、callback、跨边界 A2A / federation 控制指令和 data reference envelope。
- 大型 payload、多模态内容、文件和长文本结果走对象存储、客户指定存储或受控数据服务；控制指令只携带 URI / object reference / metadata。
- 逐 token stream 不默认走 Bus；A2A 流式回传必须有单独技术方案或开放问题。

**禁止模式：**

- 把 Bus 写成普通业务请求入口、微服务 Gateway、payload 通道或 token stream 通道。
- 把 Gateway 写成独立业务编排模块。
- 把 SSE stream、A2A 控制指令和对象存储 data path 混成一个通信机制。

**检查方式：**

- 搜索 `Gateway`、`Bus`、`SSE`、`stream`、`payload`、`data reference`、`对象存储`。
- 检查这些词所在段落是否明确 control path、data path 和 stream path 的边界。

**适用文档：** Overview、Glossary、Module Cards、BA-001、BA-003、technical S1/S5/S6、后续 A2A / Stream ICD。

## 2. 术语定义约束

### DOC-C-002.a: 架构文档中文优先

**规则：** `docs/architecture/` 下的人工可读文档以中文为主。允许保留行业通用英文术语、模块名、契约名、代码标识、YAML key、状态枚举和缩写，但不得整段沿用英文模板。

**禁止模式：**

- `Business Activity`、`Module Collaboration Flow`、`Missing Module Capability Check` 等英文模板标题直接出现在中文场景文档中。
- 表头使用 `User`、`Feature`、`Check`、`Result` 等泛化英文，而正文是中文。
- 非代码段中出现大段英文流程句子。

**检查方式：**

- 场景、Overview、Module Card、ICD 的章节标题优先使用中文。
- 保留英文时应属于：真实模块名、contract id、ADR id、状态枚举、YAML assertion key、通用术语如 Agent / Run / trace / tenant。
- Review checklist 检查是否存在英文模板残留。

**适用文档：** 所有 `docs/architecture/**/*.md`，机器可读 YAML 和代码标识除外。

### DOC-C-003: 未定义角色名不得进入核心控制流

**规则：** 核心控制流、数据流、状态流中出现的角色名必须满足至少一项：

1. 是真实 reactor module；
2. 是已在 Glossary 中定义的 capability / role；
3. 同行写明真实落点。

**禁止模式：**

- 直接写 `Workflow`，但没有说明它对应 `agent-execution-engine` 的 Orchestrator / orchestration SPI 以及 `agent-service` 的 Task 受控入口；历史 Run 只能作为 client invocation / 实现兼容名。
- 写 `Context Engine`，但没有说明它是 `agent-service` + `agent-middleware` 的能力聚合。

**检查方式：**

- 审查 Overview 的核心控制流和核心数据流。
- 所有非模块名角色必须能在 Glossary 找到。

**适用文档：** Overview、Glossary、Scenario Spec、ICD。

## 3. 层级边界约束

### DOC-C-004: L0 不写 L2 / L3 实现细节

**规则：** L0 文档只能描述目标、原则、能力、边界、状态归属、质量属性和演进方向。

**禁止模式：**

- 在 L0 写具体数据库表结构、Redis key、MQ topic、TTL、retry 次数、SDK 方法签名。
- 把某个 reference implementation 写成平台级架构原则。

**检查方式：**

- 人工审查 L0 文件。
- 发现实现细节时，下沉到 ICD、Scenario、Harness 或 L2/L3 设计。

**适用文档：** `00-overview/*`、Capability Map。

### DOC-C-005: design_only / draft / shipped 必须诚实标记

**规则：** 未 runtime enforced 的 SPI、contract、adapter、scenario 不得写成已交付事实。

**禁止模式：**

- “Tool Gateway 已执行完整 skill lifecycle”，但实际只是 SPI / contract draft。
- “TaskEvent / RunEvent 已 runtime enforced”，但 Java sealed type 仍未落地。

**检查方式：**

- 所有新 contract / scenario / harness 必须有 `status` 或正文说明。
- 与 `docs/contracts/contract-catalog.md` 和 `architecture-status.yaml` 对照。

**适用文档：** 全部。

## 4. 表格主键约束

### DOC-C-006: 表格主键必须匹配章节语义

**规则：** 表格第一列必须与章节标题一致。

| 章节类型 | 第一列应该是什么 |
|---|---|
| 模块职责边界 | 真实模块 |
| 能力地图 | Capability ID / Capability Name |
| 状态矩阵 | State ID / State Name |
| ICD | ICD ID / Participating Modules |
| Scenario | Scenario ID |
| Verification Matrix | Design Item ID |

**禁止模式：**

- 在“模块职责边界”中用能力名做主列。
- 在“状态归属”中用模块名做主列，却没有逐项列 state owner。

**检查方式：**

- Review checklist 必须检查章节标题和表格主键是否一致。

**适用文档：** 所有表格型文档。

## 5. 追踪关系约束

### DOC-C-007: 关键设计项必须进入 Verification Matrix

**规则：** ADR、ICD、Scenario、Invariant、Harness 的关键项必须进入 Verification Matrix。

**禁止模式：**

- Scenario 有 assertions，但 Verification Matrix 没有测试或评审行。
- 新增 ICD，却没有 contract test idea。

**检查方式：**

- 每个新增文件至少有一个 matrix row，或在文件中解释为什么暂不进入。

**适用文档：** ADR Drafts、ICD、Scenario、Invariants、Harness。

### DOC-C-008: Open Issue 和 Conflict 必须有归宿

**规则：** Open Issue 和 Conflict 不能只散落在正文里，必须至少进入一个集中位置：

- `constraint-and-design-inventory.md`
- 相关 ADR draft
- `architecture-review-process.md` finding

**禁止模式：**

- 在模块卡片中写“Open Issues: TBD”，但没有 owner、影响或后续位置。
- 发现冲突后通过改写正文让冲突消失。

**检查方式：**

- 搜索 `Open Issues`、`Conflict`、`Unverified`，确认有 owner 或下一步。

**适用文档：** 全部。

## 6. 标准文档模式

### DOC-C-009: 每份 Markdown 工件必须有四段开头

**规则：** 除机器可读 YAML 外，每份 Markdown 工件开头必须包含：

```text
frontmatter
H1
## 目的
## 适用读者
## 维护规则
```

**禁止模式：**

- 直接进入设计正文，没有说明用途和维护方式。
- 没有 frontmatter 的架构工件。

**检查方式：**

- 轻量脚本检查 `---` frontmatter、`# `、`## 目的`、`## 适用读者`、`## 维护规则`。

**适用文档：** 所有 `docs/architecture/**/*.md`，`task.md` 可作为原始任务说明例外。

### DOC-C-010: Machine-readable contract 必须保持 draft 诚实

**规则：** `05-contracts/machine-readable/*.yaml` 是 harness-first 草案，不是生产 contract。

**禁止模式：**

- 在 draft YAML 中声明 `runtime_enforced`，除非它已同步到 `docs/contracts/`、contract catalog、ADR 和 tests。
- YAML 字段名过度绑定未来 Java class，而没有 ICD 语义支持。

**检查方式：**

- draft YAML 必须有 `status: draft`。
- 进入生产前必须走 Change Governance 的 Level 2 / Level 3 流程。

**适用文档：** Machine-readable contracts。

### DOC-C-011: 核心场景必须是 Business Activity Scenario

**规则：** `Architecture Overview` 和场景索引中的核心场景必须描述一个能串起多个架构模块的业务活动。Technical sub-scenario 可以验证机制，但不能替代核心场景。

每个 BA-* 核心场景至少包含：

```text
Business Activity
Direct User Profile or Direct User Concerns
Participating Architecture Modules
Module Collaboration Flow
Feature Coverage
Development-time debug evidence when applicable
Runtime metrics and tracing requirements when applicable
Deployment / capability placement variants when C-Side / S-Side or local capability is involved
State Changes
Contracts Exercised
Technical Sub-scenarios
Assertions
Missing Module Capability Check
Module Boundary Fitness Check
Harness Generation Notes
```

**禁止模式：**

- 把 S1 Create Task / Invocation、S2 Execute Agent Step 这类机制清单直接当成 Overview 的核心场景。
- 只有流程步骤，没有说明直接第一用户、业务活动、参与模块、能力覆盖和模块边界适配性。
- 只描述平台内部控制流，没有描述应用开发者在开发态如何 debug、运行态如何运维和运营。
- 涉及本地工具、本地上下文、业务数据不出域或企业公共中间件时，没有说明 capability placement 和数据驻留边界。
- 新增 technical scenario 后没有挂到至少一个 BA-* 业务活动场景。

**检查方式：**

- Overview 的核心场景索引主表必须是 BA-*。
- `06-scenarios/README.md` 必须区分业务活动级核心场景和 `technical/` 子场景。
- 新增 BA-* 时必须进入 Verification Matrix；新增 technical scenario 时必须说明所属 BA-* 或标记为 future / open issue。

**适用文档：** Overview、Scenario Specs、Capability Map、Verification Matrix、Architecture Review Process。

### DOC-C-012: 架构目录必须登记到 Document Artifact Catalog

**规则：** `docs/architecture/` 下的一级目录和具有独立职责的重要二级目录必须登记到 [Document Artifact Catalog](document-artifact-catalog.md)，并说明主要内容、主要作用、对应 A2D 活动、下级展开规则和质量检查点。

文件级管理不在顶层 catalog 中强制展开。目录内部文件由目录 README、模块 README 或模块级 catalog 管理。`02-modules/<module>/` 这类模块目录可以拥有自己的文件组织方法，但必须在模块 README 中说明。

**禁止模式：**

- 新增目录后没有进入 Document Artifact Catalog。
- 目录职责发生变化，但 catalog 仍然描述旧职责。
- 顶层 catalog 直接展开所有模块文件，导致模块无法使用自己的文件管理方法。
- 模块目录没有 README，却依赖全局 catalog 解释模块内部文件。
- catalog 只写目录名，不说明下级展开规则和质量检查点。

**检查方式：**

- 扫描 `docs/architecture/` 一级目录和重要二级目录，与 Document Artifact Catalog 的目录行对齐。
- Review 时检查新增、删除、改名和职责变化是否同步更新 catalog。
- 如果某个文件找不到清晰归属，先检查它所在目录的 README 或模块 README；如果仍无法归属，再回到 A2D Working Model 判断是否应该新增目录或合并到已有产物。

**适用文档：** `docs/architecture/` 目录结构、各目录 README、模块 README 和模块级 catalog。

## 7. 当前已沉淀的问题

| Finding | 约束化结果 |
|---|---|
| “模块职责边界”表用能力名做主列。 | DOC-C-001, DOC-C-002, DOC-C-006 |
| Overview 前面运行链路列 6 个模块，职责边界列 8 个模块，口径未说明。 | DOC-C-001 |
| 核心控制流写 `Workflow`，但未定义真实落点。 | DOC-C-003 |
| Tool Gateway / Context Engine 被误读为真实模块。 | DOC-C-002 |
| S1-S6 技术机制场景被当成核心场景，无法检验业务活动级模块组合。 | DOC-C-011 |
| A2D 产物和归档位置对不上，读者需要自行推断目录职责和下级展开方式。 | DOC-C-012 |

## 8. 后续可自动化检查

优先级建议：

1. 检查 Markdown 标准开头：frontmatter、H1、目的、适用读者、维护规则。
2. 检查 Module Cards 的 `## MOD-*` 标题是否全部来自 root `pom.xml`。
3. 检查 machine-readable YAML 是否 `status: draft`。
4. 检查 Overview 的准入判定是否覆盖 root `pom.xml` 现有项，并确认 BoM / starter / fixture / demo 未进入 L0 模块边界。
5. 检查 Overview 核心场景索引是否以 BA-* 为主表，technical scenario 是否只作为子场景。
6. 检查 `Open Issues` / `Conflict` / `Unverified` 是否出现在集中清单或 Verification Matrix。
7. 检查 `docs/architecture/` 一级目录和重要二级目录是否全部登记在 Document Artifact Catalog。
