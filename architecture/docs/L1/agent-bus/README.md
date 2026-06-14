---
level: L1
module: agent-bus
status: draft
source_review_packet: docs/architecture/l0/10-governance/review-packets/agent-bus-architecture-review-draft.md
covers_views: [logical, process, development, physical, scenarios]
---

# agent-bus L1 架构文档索引

## 当前状态

本目录是 `agent-bus` 的正式 L1 架构文档入口。它根据 H2 决策创建，当前状态为草案。本文档集以 H2 评审包为事实输入，并把当前分支代码、契约、模块元数据和 L0 边界作为校验来源。

当前评审包：

- `docs/architecture/l0/10-governance/review-packets/agent-bus-architecture-review-draft.md`

## 命名说明（L0 逻辑模块与当前实现）

本文档集引用模块时区分两类名称（L0 commit `544391d8` 收敛后的逻辑边界）：

| L0 逻辑模块 | 当前实现 / 兼容落点 |
|---|---|
| `agent-runtime` | `agent-service/` 目录、Maven artifact `agent-service` |
| `agent-core` | `agent-execution-engine/` 目录、Maven artifact `agent-execution-engine` |

- 架构语义（生命周期 owner、参与者、状态归属、跨模块关系）优先使用 L0 逻辑名 `agent-runtime` / `agent-core`。
- 当前代码路径、Maven artifact、`module-metadata.yaml`、forbidden dependencies 等代码事实仍保留旧名 `agent-service` / `agent-execution-engine`。
- 仓库当前**没有** `agent-runtime/` 或 `agent-core/` 目录；这是命名收敛，不是目录重命名。

## 视图文件

| 文件 | 内容 | 状态 |
|---|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 模块总览、边界、当前事实、风险 | 草案 |
| [`logical.md`](logical.md) | 逻辑视图：Gateway、真 bus、SPI 表面、状态所有权 | 草案 |
| [`process.md`](process.md) | 进程视图：ingress、S2C、engine port、federation/reflection 流程 | 草案 |
| [`physical.md`](physical.md) | 物理视图：部署平面、网络边界、tenant 边界 | 草案 |
| [`development.md`](development.md) | 开发视图：代码结构、依赖规则、生成物边界 | 草案 |
| [`scenarios.md`](scenarios.md) | 场景视图：关键业务/系统场景 | 草案 |
| [`spi-appendix.md`](spi-appendix.md) | SPI 附录：当前已接受的 SPI 清单和迁移状态 | 草案 |
| [`features/README.md`](features/README.md) | L1 feature catalog：能力清单和成熟度 | 草案 |

## 已接受的边界

- `agent-runtime` 保持 Task 生命周期、Task 状态、Task 层级关系、suspend/resume 的所有权。
- `agent-bus` 不直接拥有或写入 Task execution state。
- `agent-bus` 逻辑上拆分为两大块：
  - Gateway：负责外部到内部的转发、入口治理和调度。
  - 真 bus：负责 service 与 service 之间的相互调用、跨服务路由和跨服务治理。
- W2 workflow primitives 继续保持设计态，直到具体版本意图定义 mailbox、admission、backpressure、tick 语义。
- S2C envelope 已增加 `tenantId`（Stage 2，Rule R-C.c）；runtime-side construction binding / schema validation integration 仍待后续波次补齐。
- main 分支中的历史 L1 文档只作为结构参考，不作为当前分支事实源。

## 第二阶段后续同步事项

- 契约层 S2C `tenantId` 迁移已完成（Stage 2，已通知冲突方）；剩余事项为 runtime-side construction binding、schema validation integration 与 downstream 文档/模板同步，均在后续波次推进，不进入当前实现。

## Agent 注册发现契约（Stage 3 设计态）

Agent / Service / Capability 注册与发现的完整设计态契约见 [`ICD-Agent-Registry-Discovery`](../../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md)。Stage 3 边界（HD3-001..007）：

- `agent-bus` 只拥有 runtime route index，不拥有 agent 定义或 Task 状态。
- registry key 强制包含 `tenantId`，禁止跨 tenant fallback。
- discovery 返回 opaque route handle，不携带 Task execution state。
- Stage 3 只定义接口和 harness 断言，不实现 runtime registry（持久化选择 deferred）。

## 类 MQ 转发契约（Stage 4 设计态）

类 MQ 转发底座的完整设计态契约见 [`ICD-Agent-Bus-Forwarding`](../../../../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md)。Stage 4 边界（HD4）：

- 转发语义 broker-agnostic：不绑定具体 broker / MQ 产品，产品选择 deferred 到 Stage 5。
- forwarding envelope 通过 `routeHandle` 消费 Stage 3 discovery result，不直接暴露或绕过物理 endpoint。
- forwarding envelope 有载荷时只携带 `payloadRef`（条件必填，MI5-003 方案 B；纯控制消息可省略）、不携带 payload body / token stream / Task execution state；大载荷走 data reference path。
- runtime-to-runtime 消息不改变远端 Task lifecycle owner；`agent-bus` 不写 Task execution state。
- Stage 4 只定义转发语义和 harness 断言，不实现运行态转发底座、不新增 mailbox / queue / DLQ / replay 运行态存储。

## 阶段记录

- Stage 1 harness 计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-harness.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-harness.md)。
- Stage 1 评审与 Stage 2 计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-review-and-stage2-plan.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-review-and-stage2-plan.md)。
- Stage 2 评审与 Stage 3 计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage2-review-and-stage3-plan.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage2-review-and-stage3-plan.md)。
- Stage 1 follow-up 评审与 Stage 3 执行计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-followups-review-and-stage3-plan.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-followups-review-and-stage3-plan.md)。
- Stage 3 评审与后续收口计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage3-review-and-followup-plan.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage3-review-and-followup-plan.md)。
- Stage 3 收口评审与 Stage 4 计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage3-close-review-and-stage4-plan.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage3-close-review-and-stage4-plan.md)。
- Stage 4 评审与 Stage 5 计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage4-review-and-stage5-plan.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage4-review-and-stage5-plan.md)。
- Stage 5 评审与 Stage 6 计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage5-review-and-stage6-plan.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage5-review-and-stage6-plan.md)。
- Stage 6 评审与 Stage 7 大批次计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage6-review-and-stage7-plan.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage6-review-and-stage7-plan.md)。

## 后续工作

- 补齐 S2C tenant 迁移后的 runtime-side construction binding / schema validation / downstream 文档同步。
- 为 ingress、federation、reflection 增加契约测试计划。
- 为本目录生成 graphify 输入和漂移检查 manifest。
- Stage 5 运行态候选方案评审：见 [`../../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md`](../../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md)（候选评审，不绑定产品；Stage 4 设计态契约见上方「类 MQ 转发契约」章节）。
- Stage 6 运行态候选裁决：见 [`../../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md`](../../../../docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md)（H2/H3 裁决记录，draft 待裁决；裁决前不写生产代码，实现等待裁决；Stage 6 计划见上方「阶段记录」）。
- Stage 7 建议进入 C3 database outbox / inbox 的较大批次：裁决落位、L2 技术设计、schema 草案、代码骨架、状态机和 harness 同步推进；计划见上方「阶段记录」。
