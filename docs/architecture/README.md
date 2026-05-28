---
level: L0
view: scenarios
status: draft
---

# 架构到交付文档集

## 目的

本目录把现有架构权威文档重组为一套面向后续 AI 辅助开发、模块并行开发、harness 生成、集成验证和架构评审的工作文档集。它不是替代根目录 `ARCHITECTURE.md`、`CLAUDE.md`、`docs/governance/architecture-status.yaml` 或 `docs/adr/`，而是把这些权威来源整理成更适合交付流转的 A2D-H 视图。

核心链路：

```text
Principle -> Capability -> Ownership -> Contract -> Scenario
-> Executable Spec -> Harness -> Implementation -> Verification -> Governance
```

## 适用读者

- 架构师：快速理解 L0/L1 边界、控制权归属和开放问题。
- 模块负责人：确认本模块职责、非职责、状态读写边界和上下游契约。
- AI agent / harness 生成器：从场景断言、ICD、机器可读 contract 和 harness spec 生成测试或实现任务。
- 审查者：按 Verification Matrix 检查设计项是否有可验证证据。

## 权威来源

| 主题 | 权威来源 |
|---|---|
| 治理原则和工程规则 | `CLAUDE.md` |
| 能力状态、基线、已交付和 deferred 边界 | `docs/governance/architecture-status.yaml` |
| 架构决策 | `docs/adr/` |
| 根架构 | `ARCHITECTURE.md` |
| agent-service L1 4+1 | `docs/L1/agent-service/` |
| 公共契约目录 | `docs/contracts/contract-catalog.md` |
| 模块身份和依赖方向 | 各模块 `module-metadata.yaml` |

## 文档地图

| 层级 | 文档 | 作用 |
|---|---|---|
| Inventory | [constraint-and-design-inventory.md](constraint-and-design-inventory.md) | 把已有内容分类为 Goal、Capability、ADR Candidate、Conflict、Open Issue 等 |
| L0 Overview | [00-overview/architecture-overview.md](00-overview/architecture-overview.md) | 给新读者建立系统心智模型 |
| L0 Principles | [00-overview/system-principles.md](00-overview/system-principles.md) | 把治理原则翻译为交付约束 |
| Glossary | [00-overview/glossary.md](00-overview/glossary.md) | 统一术语，避免 Task / Run / Agent / Skill 混用 |
| Capability | [01-capabilities/capability-map.md](01-capabilities/capability-map.md) | 从能力出发映射模块和验证方式 |
| Module | [02-modules/module-responsibility-cards.md](02-modules/module-responsibility-cards.md) | 核心模块责任卡 |
| Module Development Pack | [02-modules/agent-service/](02-modules/agent-service/) | `agent-service` 准入式迁移、L1 逻辑设计和并行开发切片 |
| State | [03-state/state-ownership-matrix.md](03-state/state-ownership-matrix.md) | 状态唯一 owner、writer、reader 和 forbidden writer |
| ADR Drafts | [04-adrs/](04-adrs/) | 交付视角的 ADR 草案，不替代 `docs/adr/` |
| ICD | [05-contracts/human-readable/](05-contracts/human-readable/) | 人类可读交互契约 |
| Contract YAML | [05-contracts/machine-readable/](05-contracts/machine-readable/) | 可生成 mock、stub、contract test 的草案 |
| Scenario | [06-scenarios/](06-scenarios/) | BA-* 业务活动级核心场景、technical sub-scenarios 和可测试断言 |
| Invariants | [07-invariants/architecture-invariants.md](07-invariants/architecture-invariants.md) | 可检查架构不变量 |
| Harness | [08-harness/](08-harness/) | 模块 harness 规格 |
| Verification | [09-verification/verification-matrix.md](09-verification/verification-matrix.md) | 设计项到验证方式的矩阵 |
| Governance | [10-governance/](10-governance/) | 变更分级、评审流程和文档质量约束 |
| A2D Working Model | [10-governance/a2d-working-model.md](10-governance/a2d-working-model.md) | 定义从需求进入到版本归档的活动流程、输入输出、责任人和归档位置 |
| Document Artifact Catalog | [10-governance/document-artifact-catalog.md](10-governance/document-artifact-catalog.md) | 登记目录级主要内容、主要作用、A2D 活动、下级展开规则和质量检查点 |
| Documentation Constraints | [10-governance/architecture-documentation-constraints.md](10-governance/architecture-documentation-constraints.md) | 约束架构文档自身的命名、分层、表格主键、状态标记和检查方式 |

## 维护规则

1. 新增或修改设计项时，必须同时更新相关 Capability、State、ICD、Scenario、Invariant、Harness、Verification。
2. 本目录可以保留 draft 和 Open Issue，但不得把未决内容写成已交付事实。
3. L0 文档只描述目标、原则、能力、边界和状态归属，不写具体数据库表、Redis key、MQ topic、TTL 或 SDK 方法签名。
4. 如果本目录与权威来源冲突，以权威来源为准，并在 [constraint-and-design-inventory.md](constraint-and-design-inventory.md) 记录 Conflict。
5. 机器可读 YAML 是 harness-first 草案，进入生产契约前必须迁移或同步到 `docs/contracts/` 并补齐 ADR、catalog 和 gate 绑定。
6. 文档自身必须遵守 [Architecture Documentation Constraints](10-governance/architecture-documentation-constraints.md)；过程检查发现的新模式问题要回填到该文件。
7. 核心场景必须是 BA-* 业务活动场景；S1-S6 这类机制场景只能作为 `06-scenarios/technical/` 下的技术子场景。
8. 需求进入、场景建模、能力拆解、模块设计、harness、实现任务和版本归档必须遵守 [A2D Working Model](10-governance/a2d-working-model.md)；目录只作为活动产物的归档位置。
9. 新增、删除、改名或改变职责的目录必须同步更新 [Document Artifact Catalog](10-governance/document-artifact-catalog.md)；目录内部文件由目录 README 或模块级 catalog 继续展开。
