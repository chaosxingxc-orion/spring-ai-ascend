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

- `agent-service` / agent runtime 保持 Task 生命周期、Task 状态、Task 层级关系、suspend/resume 的所有权。
- `agent-bus` 不直接拥有或写入 Task execution state。
- `agent-bus` 逻辑上拆分为两大块：
  - Gateway：负责外部到内部的转发、入口治理和调度。
  - 真 bus：负责 service 与 service 之间的相互调用、跨服务路由和跨服务治理。
- W2 workflow primitives 继续保持设计态，直到具体版本意图定义 mailbox、admission、backpressure、tick 语义。
- S2C envelope 需要增加 `tenantId`；该变更影响契约、代码、测试和上下游文档，必须在独立迁移切片中施工，并先通知冲突方。
- main 分支中的历史 L1 文档只作为结构参考，不作为当前分支事实源。

## 待通知事项

- S2C tenant 迁移的冲突方需要通知并确认，包括 `agent-bus` 契约/测试、`agent-service` / runtime、`agent-execution-engine` callback suspension 路径、`agent-client` / edge capability、既有 L1 文档和治理模板。

## 后续工作

- Stage 1 harness 计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-harness.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-harness.md)。
- Stage 1 评审与 Stage 2 计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-review-and-stage2-plan.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-review-and-stage2-plan.md)。
- Stage 2 评审与 Stage 3 计划：[`../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage2-review-and-stage3-plan.md`](../../../../docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage2-review-and-stage3-plan.md)。
- 把 S2C tenant 迁移通知记录转成独立 delivery projection。
- 为 ingress、federation、reflection 增加契约测试计划。
- 为本目录生成 graphify 输入和漂移检查 manifest。
