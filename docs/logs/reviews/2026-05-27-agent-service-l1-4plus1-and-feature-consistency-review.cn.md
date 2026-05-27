---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-service
affects_level: L1
affects_view: [scenarios, logical, process, development, physical]
status: proposed
language: zh-CN
relates_to:
  - architecture/workspace.dsl
  - architecture/docs/L1/agent-service.md
  - architecture/features/function-points.dsl
  - architecture/features/verification.dsl
  - docs/adr/0137-suspendsignal-canonical-interruptsignal-glossary.yaml
  - docs/adr/0138-agent-service-five-layer-l1-ratification.yaml
  - docs/adr/0139-fast-slow-path-narrowed-semantics.yaml
  - docs/adr/0140-engine-adapter-layer-split.yaml
  - docs/adr/0141-internal-event-queue-design-only.yaml
  - docs/adr/0142-run-aggregate-single-owner.yaml
  - docs/adr/0143-review-log-demotion-l1-canonical-move.yaml
  - docs/adr/0145-run-event-sealed-hierarchy.yaml
  - docs/adr/0146-suspend-reason-taxonomy-alignment-2026-05-22.yaml
  - docs/adr/0147-structurizr-workspace-authority.yaml
  - docs/adr/0149-structurizr-workspace-authority-w0-w5-shipped.yaml
  - docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.cn.md
  - docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.md
  - docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.cn.md
  - docs/L1/agent-service/README.md
  - docs/L1/agent-service/logical.md
  - docs/L1/agent-service/process.md
  - docs/L1/agent-service/physical.md
  - docs/L1/agent-service/development.md
  - docs/L1/agent-service/scenarios.md
  - docs/L1/agent-service/spi-appendix.md
  - docs/contracts/contract-catalog.md
  - docs/contracts/openapi-v1.yaml
  - docs/contracts/run-event.v1.yaml
  - agent-service/ARCHITECTURE.md
---

# Agent Service L1 4+1、功能点、能力边界与接口一致性复核（2026-05-27 严格复核与补遗版）

> 文档性质：架构评审记录，不替代 authoritative source，不将目标态或 `design_only` 能力写成已交付事实。
> 评审范围：2026-05-22 原始提议、2026-05-25/26 评审收敛、ADR-0137..0149、`docs/L1/agent-service/` 4+1、Structurizr 作者区、契约目录及功能点清单。
> 评审重点：原始设计是否被最新核心文档完整承接，Logical / Process 是否满足已接受 ADR，4+1 与 W5+ 作者区之间是否存在语义冲突；代码仅用于验证文档引用是否可落地，不作为本轮评价主线。
> 本次补遗：在原复核基础上，追加 review 文档自身证据新鲜度检查，避免“结论方向正确，但脚注、路径和现态旁证已经失效”。

---

## 0. 结论摘要

Agent Service 最新文档已承接大部分原始设计主线：五层/5a+5b 职责、Run 单一状态权威、Task/Run/Session/Memory 分离、Fast/Slow 收窄语义、A2A/S2C 场景以及三轨通道方向均已进入 ADR 或 4+1 文本。

但这组文档仍不能直接当作“无歧义 L1 基线”使用，因为同一语义在已接受 ADR、W5 作者区、canonical 4+1 与契约文件之间仍存在冲突，而且本 review 文档原稿自身也存在少量证据引用过期的问题。

本轮必须首先解决的 P0 文档冲突为：

1. **作者区权威状态和过渡优先级不闭合。** ADR-0149 已裁决 W0..W5 shipped，并确认 authoring root 转移已经发生；当前问题不是 transfer 本身无效，而是 `architecture/README.md` 与 `architecture/workspace.dsl` 仍保留 W1/advisory/future 文案，`docs/L1/agent-service/README.md` 又继续声明 per-view canonical 文件在冲突时获胜。也就是说，W5 到 W6 过渡窗口里的 metadata 与 precedence wording 尚未同步闭合。
2. **Logical View 的 Layer 3 表达同时违反 ADR-0141 的图和文两层要求。** ADR-0141 要求 Internal Event Queue 仅以 `design_only` 子节在主图之后出现、不得作为主组件图内同级块；`logical.md` 不仅在主 Mermaid 图中保留了 Layer 3，也在正文中继续解释“为何它出现在图里”。
3. **RunEvent 顺序语义互相矛盾。** ADR-0145 与 `logical.md`/`process.md` 将 suspend、resume、cancel 的事件描述为状态 CAS 成功之后发出，而 `docs/contracts/run-event.v1.yaml` 将 `SuspendRequestedEvent`、`ResumeRequestedEvent`、`CancelRequestedEvent` 定义为调用 CAS 之前发出。两者分别表达“请求意图事件”和“已提交迁移事件”，不可共用一个未区分的语义。

其次，Process/Scenarios/Physical 与作者区还存在接口范围、状态名、挂起原因、S2C tenant 载体、`RunRepository.updateIfNotTerminal(...)` 签名漂移，以及场景追踪不完整等 P1/P2 问题。本文因此采用“ADR 约束先行、过渡权威冲突显式列账、契约定义不被视图改写、功能点清单仅作覆盖检查、review 自身证据也必须跟随仓库现态刷新”的口径，不以实现状态掩盖核心文档自身的不一致。

---

## 1. 来源层级与复核口径

### 1.1 来源优先级

| 层级 | 来源 | 在本次评审中的用途 | 冲突处理口径 |
|---:|---|---|---|
| 1 | 已接受的 ADR，尤其 ADR-0137..0149 | 已裁决的设计约束、术语和迁移决策 | 后续作者区、视图和契约不得违反；新冲突需先通过 ADR 或勘误解决 |
| 2 | `architecture/workspace.dsl` 闭包及 `architecture/features/`、`architecture/docs/L1/` | ADR-0147 定义、ADR-0149 确认 W5 转移后的作者区 | 应承载后续编辑；当前与自身 README/W1 文案的冲突属于必须修复的迁移缺口 |
| 3 | `docs/L1/agent-service/{scenarios,logical,process,physical,development,spi-appendix}.md` | ADR-0143 确立的详细 canonical 4+1 物化视图 | 在 W5 过渡规则补齐前，作为 Agent Service 细节核对面；不得违反 ADR |
| 4 | `docs/contracts/*`、`docs/governance/architecture-status.yaml` | HTTP/schema/SPI 状态和基线契约 | 视图不得自行扩大接口、字段或事件语义；若需改变必须同步决策和契约 |
| 5 | `docs/L1/agent-service/features/` | `AS-L1-F01..F47` 的 `proposed` 完整性清单 | 用于找遗漏，不得反向覆盖 per-view 或把功能点文档直接当作 shipped |
| 6 | 模块文档、代码、测试与依赖 | 文档引用的落点旁证 | 仅用于验证路径或判断描述可实现性，不替代本轮核心文档裁决 |

### 1.2 状态词约定

| 状态 | 含义 |
|---|---|
| **ADR 已裁决** | 已接受的设计决定，后续 L1 视图与作者区必须遵循 |
| **作者区声明** | W5 闭包中的可编辑设计表达；仍须与 ADR、canonical 细节和契约同步 |
| **canonical 视图** | ADR-0143 下物化的 4+1 详细描述；是本轮审视 Logical/Process 的主要对象 |
| **契约已定义** | schema、API 或 SPI 语义已经写入契约文件，不等同运行时实现，但不可被视图随意改写 |
| **`proposed` 功能点** | 覆盖性功能点清单，用于发现 L1 缺口，不代表已裁决或已交付 |
| **`design_only`** | 设计边界存在，但当前不应描述为已运行或已交付 |
| **旁证** | 实现、测试路径或依赖证据；仅在文档声称现态/路径时用于定位漂移 |
| **核心文档冲突** | ADR、作者区、canonical 视图或契约对同一设计语义给出不相容定义 |

### 1.3 适用的设计红线

本次清单遵循 `CLAUDE.md` 与架构设计契约中与 Agent Service 直接相关的规则：

| 规则 | 对本模块的直接约束 |
|---|---|
| G-1 / G-1.1 / G-2 / G-8 | 4+1、代码映射、作者区文本和跨来源状态必须一致 |
| R-C / R-C.2 | 规范约束需有代码或门禁；Run 生命周期写入受统一主干约束 |
| R-D | SPI 必须与目录、模块元数据、DFX 和验证对齐 |
| R-E | `control` / `data` / `rhythm` 三轨通道边界不能混写 |
| R-F | 长任务北向边界采用 cursor，而不是把同步结果误写成创建接口返回 |
| R-I / R-I.1 | Compute & Control 与 Edge/Bus 之间的部署与入口边界 |
| R-J | tenant 隔离及 cancel 重新鉴权；状态竞态由原子更新收口 |
| R-K / R-L / R-M | capacity、sandbox、engine/hook/S2C 边界 |

---

## 2. 昨夜至今日的相关刷新范围

| 提交 | 时间（+08:00） | 与 Agent Service L1 相关的变化 | 对本次复核的影响 |
|---|---|---|---|
| `a37a554` | 2026-05-26 20:01:45 | 物化 `docs/L1/agent-service` canonical 4+1、SPI appendix、`run-event.v1.yaml` | 五层结构与场景/逻辑/进程基线形成 |
| `85cb888` | 2026-05-27 00:35:25 | 审计修补、ADR-0146、4+1 与契约调整 | 收敛挂起术语，但最新场景文件仍残留旧名 |
| `9611096` 至 `825a8e2` | 2026-05-27 01:55 至 03:05 | Structurizr W0-W5 作者区迁移及 W6/W7 准入条件文档（ADR-0149） | 作者区方向已经改变；README/workspace 现态文本尚未随权威迁移同步 |
| `0c43b7b` | 2026-05-27 09:13:15 | 将 `AS-SC01..AS-SC24` 与 `AS-L1-F01..AS-L1-F47` 吸收进 L1 视图 | 形成细粒度覆盖清单；该目录自身声明为 `proposed` 而非新权威面 |

---

## 3. 原始设计要求到最新核心文档的继承矩阵

| 原始设计主线（2026-05-22） | 后续裁决/收敛 | 最新核心文档承载 | 严格复核判断 | 必要动作 |
|---|---|---|---|---|
| Agent Service 是控制编排中心，服务与执行引擎分责 | ADR-0138、ADR-0140、ADR-0142 将责任落为五层及 5a/5b、Run 单一写入主干 | `logical.md`、`development.md`、`spi-appendix.md` | 主线已继承 | 保持 Layer 2/4/5 边界一致 |
| Platform-Centric / Business-Centric 双部署模式 | 模式被接受为部署定位差异，不应改写语义 | `physical.md` Mode A/B | 物理视图已承载，Logical/Process 未显式声明语义不变 | 在两视图加入跨模式不变量 |
| Embedded co-process / Stateless service-level 双调用模型 | 调用路径只能改变物理链路，不能绕过 tenant、幂等和状态主干 | Physical 有拓扑叙事，Process 仅间接体现 | 承载不充分 | 写明直调/RPC 均保持 Task/Run/CAS 语义 |
| Workflow DAG 与 ReAct Loop 双执行形态 | 统一为 `RunMode.GRAPH` / `RunMode.AGENT_LOOP` | `logical.md`、S1/S2 与 Layer 5a 功能点文档 | 已基本继承 | 补一行 glossary 映射即可 |
| `Run <= Task <= Session <= Memory` 与 Task 控制状态/Run 执行状态分离 | ADR-0136、ADR-0142 维持实体分离与 Run owner | `logical.md` ER/DFA、`development.md` | 详细视图已体现；W5 `architecture/docs/L1/agent-service.md` 叙事过薄 | 在作者区 L1 narrative 补实体层次和所有权 |
| 显式中断/恢复原语 | ADR-0137 将 `InterruptSignal` 收敛为 `SuspendSignal`；ADR-0146 固定六个 `SuspendReason` 名称 | Logical/Process 与 Scenarios | 部分继承；S2 仍写 `AwaitTool` 而非 `AwaitToolResult` | 修正场景词汇 |
| 双向 A2A 协作与 S2C 客户端能力回调 | A2A 为契约边界；S2C 绑定挂起/恢复 | S3、S4、P4、P5、功能点文档 | 场景存在；S3 将 child-run 误写成 `forClientCallback`，S4/P5 将未纳入 OpenAPI 的 resume 路由写为 shipped | 分离 `AwaitChildRun` 与 `AwaitClientCallback`，校正接口状态 |
| Internal Event Queue 与通道隔离 | ADR-0138/0141 将 Layer 3 收敛为 `design_only` 三轨 binding，而非现存同级运行层 | Logical/Physical/Development | 设计方向继承，但 Logical 主图表达直接违反 ADR-0141 | 将 Layer 3 移出主图，保留单独 design-only 子节 |
| Fast-Path / Slow-Path 调度 | ADR-0139 明确 fast 仅省略 checkpoint/snapshot，不得绕过 metadata、RLS 或 cursor 边界 | Scenarios/Process/Logical | 原则已写入；Process 快路径创建返回 `200 RunResponse` 再次扩大北向语义 | 统一创建响应为 cursor 契约 |
| 第三方框架、Shadow Tool 与中间件拦截 | ADR-0140 将 `RuntimeMiddleware` 固定在 Layer 4，5b 负责翻译/tool shaping | Logical/Process/功能点文档 | 责任表达基本具备 | 将 hook bridge 与控制责任写成可核查红线 |
| 模型、工具、客户端能力、adapter profile 与观测治理 | 功能点扩展为 F19..F47 及配置所有权规则 | 功能点文档、Logical 局部章节 | 覆盖性清单较完整，但作者区 L1 narrative 未承载全部主线 | 在 workspace narrative 显式链接/摘要 |
| 成熟 OSS 复用：Bus、Middleware、持久化/调度 | 后续评审否定“特定 SDK/单队列即 canonical”：A2A 不绑定 `a2a-java` runtime；三轨通道是意图隔离，broker 是物理选择 | Physical、契约与历史评审 | 原始意图已被收窄，必须避免恢复旧提案中的具体绑定承诺 | 使用选型边界表，而非依赖即架构结论 |
| L2 边界合同支撑 L1 下钻 | G-1.1.c 要求复杂子系统有 Boundary Contract | `development.md` 当前列出 5 个 L2 zone | 覆盖 Run/Control/Queue/RLS，但没有直接覆盖扩展后的功能点范围所涉 Access/A2A/S2C、5a/5b、配置/第三方恢复 | 扩展或明确复用关系 |

---

## 4. 最新 L1 设计特性清单（文档口径）

| 编号 | 核心特性 | 设计要求 | 文档覆盖与风险 | 关键文档 |
|---|---|---|---|---|
| C-01 | 北向异步 Run 生命周期 API | 创建后返回 cursor；支持查询与取消 | OpenAPI 仅固定 create/get-by-id/cancel；作者区的 list 与场景中的 resume 声明扩大了当前契约表面 | `openapi-v1.yaml`、`architecture/docs/L1/agent-service.md`、S4/P5 |
| C-02 | Tenant-first 入口隔离 | 请求携带 tenant，跨租户不泄露资源 | HTTP 契约是 JWT claim/header 核验；Ingress 与 S2C 的 tenant 载体不能被 Physical/作者区假定为同一现态字段 | `contract-catalog.md`、`physical.md`、workspace narrative |
| C-03 | 幂等创建边界 | tenant + idempotency key 绑定，请求漂移拒绝 | 设计红线稳定；扩展后的功能点范围与验证作者区应继续引用同一契约范围 | `openapi-v1.yaml`、features/verification |
| C-04 | Run 聚合根与 DFA | Run 由 Layer 2 所有，终态写入不能被竞争覆盖 | Logical/Process 主旨正确；视图接口示意不能与 SPI appendix/契约签名分叉 | ADR-0142、`logical.md`、`process.md`、`spi-appendix.md` |
| C-05 | Cancel 原子竞态收口 | cancel 重新鉴权；CAS 决定 cancel/完成竞态胜者 | OpenAPI 和 Process 采用 `CANCELLED`；作者区 function-point 使用 `CANCEL_REQUESTED`，构成状态机冲突 | `openapi-v1.yaml`、P3/P6、`function-points.dsl` |
| C-06 | Suspend / Resume 编排协议 | 执行器可挂起，恢复保留原 Run 语义 | 逻辑协议存在；S4/P5 将 resume HTTP 写为 W2-shipped 与 W1 OpenAPI 范围矛盾 | ADR-0137/0146、S4/P5、`openapi-v1.yaml` |
| C-07 | S2C 客户端回调 | 回调跨边界且 tenant 可追踪 | 回调边界已描述；Physical 将 `tenant_id` 写入 envelope，而契约目录明确该字段尚不存在 | `contract-catalog.md:90`、`physical.md:129` |
| C-08 | 三轨事件通道 | control/data/rhythm 隔离承载运行时消息 | Layer 3 被 ADR-0141 定义为 `design_only` binding，不存在可声称交付的 service queue 运行层 | `logical.md`、`physical.md` |
| C-09 | `RunEvent` 事件模型 | 场景状态迁移能够映射事件变体与演进导出策略 | `design_only` 本身不冲突；真正冲突是请求/提交事件的 CAS 前后顺序定义不一致 | ADR-0145、`run-event.v1.yaml`、Logical/Process |
| C-10 | Engine 与 Middleware 边界 | Layer 4 负责控制和 middleware，5a 负责执行适配 | ADR-0140 已收敛责任，Logical/Process 需要把 hook bridge 与责任归属继续保持为同一规则 | ADR-0140、`logical.md`、`process.md` |
| C-11 | 双部署模式不改变语义 | 同进程或远程链路不得改变 Task/Run 与租户/CAS 边界 | 物理视图已有部署叙事；逻辑/进程视图仍应把“不改变责任和语义”写为跨视图约束 | `physical.md`、本次建议 |
| C-12 | Spring AI 翻译与工具截获 | 模型、Prompt、structured output、tool/memory/retrieval 适配归于 5b | Layer 5b 功能点清单已承载该方向；作者区简述未完整体现 profile/config ownership 主线 | `logical.md`、features、workspace narrative |

---

## 5. 分层职责与 AS-L1-F01..F47 功能清单

> `docs/L1/agent-service/features/README.md` 的状态为 `proposed`，并明确这些功能点文档不替代 canonical 4+1。本节用于检查设计覆盖面，表内“当前状态”仅复述文档成熟度或指出矛盾，不构成交付认定。

### 5.1 五层总体职责

| 层级 | 主要责任 | 文档边界/模块映射 | 设计状态或风险 |
|---|---|---|---|
| Layer 1 Access | 北向协议、鉴权、tenant、trace、幂等、cursor 输出 | `development.md` 映射 `service.platform.*`；OpenAPI 固定 W1 表面 | 基线存在；A2A/callback/SSE 仍不得由功能点文档扩大契约 |
| Layer 2 Session & Task Manager | Run 聚合根、状态机、任务/会话状态、租户作用域持久化 | Logical ER/DFA；ADR-0142；Development L2 contract | Run owner 主线稳定；作者区 narrative 需补实体层次 |
| Layer 3 Internal Event Queue | 内部事件与三轨通道绑定 | ADR-0141、Physical、Development | `design_only`；Logical 主图违规 |
| Layer 4 Task-Centric Control | 编排、suspend/resume、middleware、retry/cancel 仲裁 | ADR-0140/0142、Logical/Process | 边界方向稳定；事件次序与异常规范未收敛 |
| Layer 5a Engine Dispatch & Execution | engine 选择、执行器适配、hook surface、流式执行 | Logical/Development/SPI appendix | 5a/5b 分裂已裁决；需补 Boundary Contract |
| Layer 5b Translation & Tool-Intercept | Spring AI 翻译、模型/工具/记忆/检索适配 | Logical/功能点文档/SPI appendix | 功能点覆盖较广；配置/profile 需进入作者区摘要 |

### 5.2 Layer 1：Access 功能点

| ID | 功能点 | 对外行为/边界 | 当前状态 |
|---|---|---|---|
| AS-L1-F01 | 协议入口收敛 | HTTP 请求进入 Run 主干；远程/A2A 入口不得绕过边界 | HTTP 部分已交付；扩展入口设计态 |
| AS-L1-F02 | SSE / 流式输出 | 以事件流暴露进度，不破坏 cursor 语义 | `design_only` / W2+ |
| AS-L1-F03 | 轮询查询 | 客户端按 cursor 查询 Run 状态 | 按 ID 查询已交付；列表查询漂移 |
| AS-L1-F04 | 直连边界治理 | 防止客户端直达内部执行/中间件层 | 架构约束，需持续门禁 |
| AS-L1-F05 | tenant/auth/trace/idempotency 绑定 | 身份、租户、链路与幂等在入口收口 | 基础实现已交付 |
| AS-L1-F06 | 客户端能力与 callback transport | 识别客户端侧可执行能力与回调边界 | 接口/设计存在，HTTP 闭环未交付 |
| AS-L1-F07 | cancel/resume/callback ingress | 控制输入必须重鉴权并进入 Run 控制主干 | cancel 已交付；resume/callback 北向未交付 |
| AS-L1-F08 | Agent/Peer 能力发布 | 对等 Agent 能力与协商入口 | `design_only` |

### 5.3 Layer 2：Session & Task Manager 功能点

| ID | 功能点 | 责任边界 | 当前状态 |
|---|---|---|---|
| AS-L1-F09 | Run 状态权威 | 单一 Run 聚合根及 DFA | 已有 Java 主干与测试 |
| AS-L1-F10 | Task 控制状态 | 长任务游标与任务过程状态 | 接口/设计分解存在，完整 durable 路径待核对 |
| AS-L1-F11 | Session 上下文 | 会话与投影上下文归属 | `ContextProjector` 接口及内存实现存在 |
| AS-L1-F12 | 压缩投影锚点 | 上下文 compaction 产生可恢复投影 | 设计/adapter 范畴 |
| AS-L1-F13 | attempt/checkpoint/rollback 引用 | 恢复与回滚不丢失运行锚点 | 内存编排有基础，完整产品语义待闭环 |
| AS-L1-F14 | 父子/远程关联 | parentRun/childRun/remote handle 可追踪 | SPI/参考编排存在；远程链路设计态 |
| AS-L1-F15 | 配置快照/引用权威 | resume/retry 使用可审计配置版本 | 设计要求，需实现与验证增强 |
| AS-L1-F16 | tenant-first persistence 与生命周期审计 | 核心实体 tenant scoped；状态变更可审计 | HTTP/幂等基础存在；全实体持久化/RLS 未完全交付 |

### 5.4 Layer 3：Internal Event Queue 功能点

> 本层全部功能点只能按 `design_only` 描述；不得写成已经存在 `service.queue` 运行时实现。

| ID | 功能点 | 设计意图 | 当前状态 |
|---|---|---|---|
| AS-L1-F17 | `RunEvent` 路由 | 把 Run 生命周期事件映射到通道与消费者 | `design_only` |
| AS-L1-F18 | Producer / Consumer / Lease / Ack / Retry / DLQ | 可靠处理及失败闭环 | `design_only` |
| AS-L1-F19 | 三轨物理绑定 | `control`、`data`、`rhythm` 隔离 | `design_only` binding |
| AS-L1-F20 | SSE / polling 投影 | 将内部进度转为客户端可见状态 | `design_only` |
| AS-L1-F21 | rhythm / resume sweep | 长任务定时与恢复扫描 | `design_only` |
| AS-L1-F22 | 远程/子任务完成事件 | 子任务完成后恢复父 Run | `design_only` 事件面 |
| AS-L1-F23 | 配置漂移/审计事件 | 追踪配置与执行差异 | `design_only` |

### 5.5 Layer 4：Task-Centric Control 功能点

| ID | 功能点 | 责任边界 | 当前状态 |
|---|---|---|---|
| AS-L1-F24 | Orchestrator loop | 编排执行、状态流转与挂起恢复 | 内存参考实现存在 |
| AS-L1-F25 | Fast/Slow 路径选择 | 只选择执行策略，不绕过元数据持久化与 tenant | `DualTrackRouter` 为 `design_only` |
| AS-L1-F26 | Suspend / Resume | 通过挂起协议恢复同一 Run | 内部参考路径存在；北向 resume 路由未交付 |
| AS-L1-F27 | RuntimeMiddleware 治理 | hook、策略、审计与拒绝归属控制层 | 接口存在；执行模块持有方式需厘清 |
| AS-L1-F28 | rollback/retry | 显式 attempt 与 checkpoint 边界 | 部分参考能力，完整规范待闭合 |
| AS-L1-F29 | cancel/complete race | CAS 决定唯一胜者 | 核心 cancel 路径已实现 |
| AS-L1-F30 | client-hosted skill | 服务端挂起并调用客户端能力 | SPI/设计存在，北向流程未闭合 |
| AS-L1-F31 | sub-agent / third-party join | 外部协作者进入同一控制语义 | 设计/跨模块 SPI |
| AS-L1-F32 | 同一远程 invocation 恢复 | 不静默新建 Agent 导致语义漂移 | 设计要求 |

### 5.6 Layer 5a：Engine Dispatch & Execution 功能点

| ID | 功能点 | 责任边界 | 当前状态 |
|---|---|---|---|
| AS-L1-F33 | `EngineRegistry` 严格匹配 | engine/envelope/能力不匹配即拒绝 | 跨模块实现存在 |
| AS-L1-F34 | `ExecutorAdapter` 生命周期 | 统一执行入口及恢复接口 | 跨模块 SPI 存在 |
| AS-L1-F35 | 第三方适配器 | 将外部 Agent/engine 纳入 envelope | 设计/扩展态 |
| AS-L1-F36 | 子 Agent 边界 | child Run 与父 Run 关系明确 | 参考编排与设计并存 |
| AS-L1-F37 | `EngineHookSurface` | 执行事件暴露给控制层 middleware | SPI 已列入契约目录 |
| AS-L1-F38 | stream / partial result | 执行器输出中间结果 | 设计态，不等同 SSE 已交付 |
| AS-L1-F39 | adapter 能力/版本治理 | 防止能力或版本漂移 | 设计与门禁要求 |

### 5.7 Layer 5b：Translation & Tool-Intercept 功能点

| ID | 功能点 | 责任边界 | 当前状态 |
|---|---|---|---|
| AS-L1-F40 | context / prompt 构建 | 将运行上下文翻译为模型输入 | Spring AI 适配骨架存在 |
| AS-L1-F41 | compaction | 长上下文压缩与投影 | 设计/adapter 范畴 |
| AS-L1-F42 | model profile | 模型参数和 provider 配置治理 | 依赖存在，完整治理需验证 |
| AS-L1-F43 | structured output | 结构化模型输出转换 | adapter 接口/骨架存在 |
| AS-L1-F44 | ChatAdvisor / tool shaping | advisor 与工具调用形态控制 | 主要为设计与适配表面 |
| AS-L1-F45 | client skill payload | 客户端技能输入输出映射 | 设计态 |
| AS-L1-F46 | remote payload normalization | 对等/远程 payload 归一化 | 设计态 |
| AS-L1-F47 | tool/memory/retrieval profile | 工具、记忆、检索能力配置边界 | middleware SPI 与 Spring AI 依赖存在；链路不应过度声明 |

---

## 6. 作者区功能点与 canonical / 契约文档一致性复核

`architecture/features/function-points.dsl` 是 W5 作者区功能点表达，其 `saa.status=shipped` 不能与 ADR-0143 的详细视图或正式契约定义相冲突。下表优先比较文档面；测试路径仅在 `verification.dsl` 声称了可解析关系时作为旁证。对验证作者区的判断必须以 `verification.dsl` 当前登记项为准，不能沿用旧测试名或历史 review 中的别名。

| 功能点 | 作者区声明 | 实现/契约复核 | 本文采用口径 |
|---|---|---|---|
| `FP-CREATE-RUN` | `shipped` | OpenAPI 将 `POST /v1/runs` 固定为 `202 + TaskCursor` | 与契约对齐；Process 快路径不得另写 `200` |
| `FP-CANCEL-RUN` | `shipped`，描述转换至 `CANCEL_REQUESTED` | OpenAPI 与 Process DFA 定义终态为 `CANCELLED` | 核心文档冲突，必须改作者区 |
| `FP-GET-RUN-STATUS` | `shipped` | OpenAPI 包含 `GET /v1/runs/{runId}` | 文档面一致 |
| `FP-LIST-RUNS` | `shipped`，包含 `GET /v1/runs` | OpenAPI W1 note 与 contract catalog 仅列 create/get-by-id/cancel | 作者区扩大契约范围，必须降级或补决策/契约 |
| `FP-INGRESS-ENVELOPE` | `shipped` | 契约目录将 ingress 绑定列为 design-only / 后续入口 | “载体定义”与“入口运行”必须拆开表达 |
| `FP-S2C-CALLBACK` | `shipped` | contract catalog 声明 envelope 尚无 in-band `tenantId`；OpenAPI 无 resume | 不得推导完整 client callback API 已成立 |
| `FP-RUN-STATE-TRANSITION` | `shipped` | ADR-0142/Process 定义 CAS 主干；事件前后序仍冲突 | 状态主干可保留，事件语义需先收敛 |
| `FP-SUSPEND-RESUME` | `shipped` | Suspend 设计已有 ADR；S4/P5 的 HTTP resume 未进入 OpenAPI | 仅能说明内部协议/设计场景，不能扩充 HTTP 表面 |
| `FP-CHILD-RUN-SPAWN` | `shipped` | S3/Process 应使用 `AwaitChildRun`，当前 S3 边界合同误写 S2C variant | 场景语义待修正后再使用 |
| `FP-IDEMPOTENCY-CLAIM` | `shipped` | OpenAPI 与功能点文档口径一致 | 文档面无新增冲突 |
| `FP-TENANT-CROSS-CHECK` | `shipped` | HTTP 契约为 JWT `tenant_id` 对 `X-Tenant-Id`；Ingress 是另一设计面 | 作者区必须区分入口类型 |
| `FP-POSTURE-BOOT-GUARD` | `shipped` | 本轮未发现与 4+1/契约冲突 | 保留，并由验证作者区维持可解析关系 |
| `FP-GRAPH-MEMORY-STORE` | `shipped` | 原始设计要求 Memory 层；workspace L1 narrative 未清晰关联实体层次 | 补 narrative 关联 |
| `FP-ENGINE-DISPATCH` | `shipped` | 5a 责任与 ADR-0140 对齐 | 保持与 5b/Layer 4 边界说明 |
| `FP-HOOK-DISPATCH` | `shipped` | ADR-0140 将 RuntimeMiddleware 责任固定到 Layer 4 | 明确 hook surface 是桥接而不是所有权转移 |

---

## 7. 接口定义清单（文档定义面）

### 7.1 HTTP 契约表面与视图冲突

| 方法与路径 | 作用 | 输入约束 | 响应/状态语义 | 文档复核结论 |
|---|---|---|---|---|
| `GET /v1/health` | 服务健康探针 | operator probe，不要求 tenant/idempotency | 健康信息 | contract catalog 现态表面 |
| `GET /actuator/health` | Actuator 健康探针 | permit-list | Actuator response | contract catalog 现态表面 |
| `GET /actuator/prometheus` | 指标暴露 | permit-list | Prometheus metrics | contract catalog 现态表面 |
| `POST /v1/runs` | 创建 Run | `X-Tenant-Id`；POST 需要 `Idempotency-Key`；JWT tenant 交叉核验 | `202` + cursor，初始 `PENDING` | OpenAPI W1 定义且符合 R-F |
| `GET /v1/runs/{runId}` | 获取单个 Run 状态 | tenant scoped | `200` 或不可见资源错误 | OpenAPI W1 定义 |
| `POST /v1/runs/{runId}/cancel` | 取消非终态 Run | tenant 重鉴权；CAS 防竞态 | `200` for cancelled/already-cancelled；`404` cross-tenant/unknown；`409` illegal transition | OpenAPI W1 定义 |
| `GET /v1/runs` | tenant 下列表/分页 | 作者区声称存在 | 当前 OpenAPI 不含该路径 | 未纳入契约，作者区需纠正或另行决策 |
| `POST /v1/runs/{runId}/resume` | 客户端回调后恢复 | 4+1 局部写成 W2-shipped | 当前 OpenAPI 不含该路径 | 未纳入契约，视图需纠正或另行决策 |
| SSE / webhook | 异步进度推送 | 设计将结合 `RunEvent` | OpenAPI 明确为 W2+ scope | 目标态，不是 W1 接口表面 |

### 7.2 Agent Service 发布 SPI

依据 `spi-appendix.md` 与契约目录，Agent Service 当前列出的九项 SPI 如下；实现存在性不改变其在权威文档中的状态限定。

| SPI | 关键方法或责任 | 当前状态 | 注意事项 |
|---|---|---|---|
| `RunRepository` | `findById(UUID)`、`save(Run)`、tenant 查询族、`updateIfNotTerminal(UUID, UnaryOperator<Run>)` | 已有接口与内存实现 | CAS 真实签名为两参数；Run 状态写入必须经此路径 |
| `GraphMemoryRepository` | `addFact(String tenantId, ...)`、`query(String tenantId, ...)`、`search(String tenantId, ...)` | 接口存在 | tenant 为显式参数；生产实现需单独核验 |
| `ResilienceContract` | `resolve(String operationId)`、`resolve(String tenant, String skill)` | 接口存在 | 与 capacity/suspend 规则联动 |
| `SkillCapacityRegistry` | `tryAcquire(String tenant, String skill)`、`release(...)` | 接口存在 | 对应 R-K capacity 管理 |
| `StatelessEngine` | `StateDelta execute(AgentInvokeRequest request)` | `implemented_unverified` | 有 `InMemoryStatelessEngine` 与测试，但不可扩写为生产引擎闭环 |
| `ContextProjector` | `project(String sessionId, String tenantId, String projectionPolicy)` | `implemented_unverified` | 有内存实现；属于 session/translation 交界 |
| `TaskStateStore` | `save(...)`、`load(...)` | `implemented_unverified` | 有内存实现；durable state 未由本次确认 |
| `Agent` | identity、definition、`invoke(AgentInvocation)` | `design_only` | 不得描述为完整运行时 Agent 已交付 |
| `AgentRegistry` | `register`、`unregister`、`find`、`list` | `design_only` | 第三方/子 Agent 注册表仍在设计范围 |

### 7.3 支撑接口与跨模块消费面

| 边界 | 主要接口/类型 | 所属模块 | Agent Service 中的用途 | 复核结论 |
|---|---|---|---|---|
| HTTP 幂等 | `IdempotencyStore` | `agent-service` | 创建请求 claim / drift 检测 | JDBC 实现存在 |
| 异步派发 | `AsyncRunDispatcher` | `agent-service` | 创建后派发 Run | 接口存在，默认 `NoOpAsyncRunDispatcher` 不代表完整异步执行 |
| 编排 | `Orchestrator`、`RunContext`、`Checkpointer`、`SuspendSignal` | `agent-execution-engine` | 启动/恢复/挂起/检查点 | SPI 与内存集成路径存在 |
| Engine | `ExecutorAdapter`、`GraphExecutor`、`AgentLoopExecutor`、`EngineRegistry`、`EngineHookSurface` | `agent-execution-engine` | 执行适配与 hook surface | 跨模块能力存在 |
| S2C | `S2cCallbackTransport`、`S2cCallbackEnvelope`、`S2cCallbackResponse` | `agent-bus` | 客户端能力回调 | envelope tenant 字段问题需修正描述或实现 |
| Ingress | `IngressGateway`、`IngressEnvelope` | `agent-bus` | 跨 plane 入口 | 不得取代当前 HTTP header/JWT 交付事实 |
| Middleware | `RuntimeMiddleware`、`HookPoint`、`HookContext`、`HookOutcome` | `agent-middleware` | hook/策略/审计边界 | 与 Layer 4/5a 归属措辞需统一 |
| 模型与工具 | `ModelGateway`、`Skill`、`SkillRegistry`、memory/retrieval/vector/prompt/advisor SPI | `agent-middleware` | Layer 5b 适配消费面 | 大量能力仍应按契约状态谨慎描述 |

### 7.4 核心契约状态

| 契约/载体 | 当前状态 | 正确使用方式 |
|---|---|---|
| `openapi-v1.yaml` | W1 已交付 HTTP 契约 | 北向当前表面的直接真值来源 |
| `run-event.v1.yaml` | `design_only` | 可用于事件设计映射，不可声称 Java 运行时事件已发出 |
| `Run` / `RunStatus` | 结构载体已存在 | 以 Java 状态枚举和状态机为当前行为真值 |
| `S2cCallbackEnvelope` | Java record 已存在 | 当前八字段中没有 in-band `tenantId`，tenant 需按现有绑定说明 |
| `IngressEnvelope` | 跨模块载体/后续入口绑定 | 不应把设计入口直接等同已交付 HTTP path |

---

## 8. 分层开源软件与设计选型边界

### 8.1 从原始提议收敛后的选型边界

原始提议提出尽量复用成熟开源组件，但后续 ADR 与 4+1 已经收窄了部分具体技术承诺。下表是文档编写时不得越过的边界。

| 关注面 | 可写入 L1 的稳定原则 | 不应写成既定架构的内容 | 承载文档 |
|---|---|---|---|
| 内部 Bus / Queue | Agent Service 必须按意图绑定 `control` / `data` / `rhythm` 三轨；Layer 3 当前为 `design_only` binding | 把 Redis Lists、Reactor Sinks、Disruptor、NATS、RabbitMQ 或 Kafka 中任一项写成 Agent Service canonical 运行底座 | ADR-0141、`physical.md`、bus contract |
| 长运行恢复与持久化 | Fast-Path 也保留 metadata/tenant/RLS 边界；Slow-Path 可承载 durable orchestration 方向 | 将“内存/半持久单队列”恢复为正式语义，或因引入 Temporal 就宣称 resume 闭环已经完成 | ADR-0139、Process/Physical |
| A2A | A2A 是对等协作协议/envelope 边界，必须保持 tenant/control 规则 | 将原提议中的 Google `a2a-java` SDK 写为生产运行依赖或协议底座 | ADR-0100 约束、S3、功能点文档 |
| Middleware / Shadow Tool | `RuntimeMiddleware` 责任归 Layer 4；5b 负责 Spring AI 翻译和 tool-shaping | 将 ChatAdvisor 等同 RuntimeMiddleware，或允许外部 runtime 绕过控制层直达工具执行 | ADR-0140、Logical |
| Model / Memory / Retrieval | 以 profile 和 tenant-scoped SPI 管理 provider、memory、retrieval | 因存在某个 starter/vector store 依赖就宣称完整模型或记忆能力闭环 | Logical、功能点文档、contract catalog |
| 可观测性与配置治理 | 配置版本、事件导出、审计和低基数遥测需要在边界合同中表达 | 将尚未收敛的事件顺序或字段当成可监控事实向外传播 | Logical、Development、RunEvent contract |

### 8.2 依赖/候选组件与层内作用

> 本表仅说明已有依赖或合理候选在分层中的用途，不据此推导 L1 功能已经交付，也不替代 ADR 对 broker/adapter 的选择。

| 层级 | 开源软件/依赖 | 目的和作用 | 本次可确认状态 |
|---|---|---|---|
| Layer 1 Access | Spring Boot Web | 提供 REST controller、过滤器、HTTP 编排入口 | 已用于 Run/health 路由 |
| Layer 1 Access | Spring Validation | 对请求对象进行输入约束校验 | 依赖已引入，服务入口可使用 |
| Layer 1 Access | Spring Security + OAuth2 Resource Server | bearer token 校验与 JWT tenant claim 提取 | 已用于 tenant cross-check 边界 |
| Layer 1 Access | Springdoc OpenAPI | 生成/维护 HTTP 契约可见面 | 依赖存在；静态契约仍以 `docs/contracts/openapi-v1.yaml` 核验 |
| Layer 1 Access | Micrometer + Prometheus Registry | 低基数指标和服务可观察性 | Actuator/Prometheus 表面已列入契约 |
| Layer 1 Access | Logstash Logback Encoder | 结构化 JSON 日志与审计 MDC | 依赖已引入 |
| Layer 2 State | Spring JDBC | 幂等与后续状态持久化的数据库访问基础 | 已用于 `JdbcIdempotencyStore` |
| Layer 2 State | PostgreSQL Driver | tenant scoped durable persistence 的数据库连接 | 幂等存储使用；全量 Run/Session durable 未据此自动交付 |
| Layer 2 State | Flyway + PostgreSQL extension | 迁移 schema、约束与后续 RLS 演进 | `idempotency_dedup` 迁移存在；RLS 范围按台账限定 |
| Layer 2 State | Caffeine + Spring Cache | 本地缓存与轻量加速 | 依赖已引入；不得代替持久化权威 |
| Layer 3 Queue | 未选定已交付 service queue broker | 设计要求绑定 `control/data/rhythm` 三轨通道 | 本层仍为 `design_only` |
| Layer 3 / 4 | Temporal Java SDK | 长时流程/定时恢复候选基础 | 依赖存在，不等同三轨或异步 Run 已运行 |
| Layer 4 Control | Resilience4j Spring Boot 3 | 容错、限流/拒绝及 resilience contract 支撑 | 依赖与接口存在；完整调度语义需按实现核验 |
| Layer 4 Control | Spring Boot configuration/YAML | posture、capacity 等策略加载 | 策略/门禁路径已有基础 |
| Layer 5a Engine | SnakeYAML 或配置解析基础 | Engine envelope/registry 配置解析 | 跨模块实现可见 |
| Layer 5b Translation | Spring AI OpenAI Starter | OpenAI provider 模型适配 | 依赖与 adapter 表面存在 |
| Layer 5b Translation | Spring AI Anthropic Starter | Anthropic provider 模型适配 | 依赖与 adapter 表面存在 |
| Layer 5b Translation | Spring AI PGVector Store | 向量检索/记忆后端适配基础 | 依赖存在，不等同完整 retrieval profile 已闭合 |
| 跨层扩展 | MCP Java SDK | 工具/能力协议集成基础 | 依赖存在；功能点不得仅凭依赖宣称交付 |
| 跨层内容处理 | Apache Tika | 文档/内容解析基础 | 依赖存在；不直接构成 L1 核心 Run 能力 |
| 验证 | JUnit / Spring Boot Test / Spring Security Test | 单测与服务集成验证 | 测试路径可见 |
| 验证 | Testcontainers PostgreSQL | 验证实际数据库上的幂等/迁移行为 | 作者区当前登记的是 `IdempotencyStoreIT`，且其路径需要与仓库现态重新对齐，不能继续沿用旧的 `IdempotencyStorePostgresIT` 名称 |
| 验证 | WireMock / Rest Assured | 外部交互及 HTTP 契约验证 | 依赖存在 |
| 验证 | ArchUnit | 层级与边界门禁 | 依赖存在，规则覆盖仍以实际测试为准 |

### 8.3 不应由依赖推导出的结论

| 不应推导的结论 | 原因 |
|---|---|
| 引入 Temporal 即已经交付 Layer 3 durable queue 或 resume 调度 | Layer 3 已被明确标为 `design_only`，且北向 resume 路由缺失 |
| 引入 Spring AI providers 即已经交付完整 Agent/模型运行平台 | 多数 Layer 5b 功能点仍是 adapter/设计边界 |
| 引入 PGVector 即已经交付完整 memory/retrieval 产品能力 | Repository/SPI 与后端闭环需分开验证 |
| 有 `S2cCallbackTransport` 即 HTTP client callback 已交付 | 当前 OpenAPI 没有 resume/callback route，且 envelope tenant 字段仍有差异 |

---

## 9. 4+1 整体严格复核与问题清单

### 9.1 4+1 整体判断

| 视图/闭包 | 已正确承接的主线 | 严格评审结论 |
|---|---|---|
| Scenarios | S1-S5 已覆盖标准执行、循环/挂起、A2A、S2C、取消竞态 | 能覆盖核心用例，但 `AwaitTool`、child-run variant、resume 接口状态三处破坏术语/接口一致性 |
| Logical | 五层职责、Layer 2 owner、Layer 4 middleware、5a/5b 拆分及 Run DFA 方向正确 | 主组件图直接违反 ADR-0141；事件发射规则与契约矛盾；双模式不变量尚隐含 |
| Process | 创建、取消竞态、A2A/S2C 过程均有可审查时序 | Fast-Path 返回语义、resume 状态及 RunEvent 顺序需要先统一；异常路径缺汇总规范 |
| Physical | Mode A/Mode B、三轨通道及 tenant/RLS 关注点具备 | 把 design-only 事件写成运行时动作，并声称不存在的 S2C tenant 字段 |
| Development/SPI | 包/层映射和五项 L2 Boundary Contract 已建立 | 扩展后的功能点范围已超出五项合同的直接覆盖范围；需补 Access/A2A/S2C、5a/5b 与配置恢复边界 |
| Workspace closure | 已有 L1 narrative、function points 与 verification 关系 | 权威迁移元数据本身过期，且作者区声明与 canonical/契约存在冲突，当前不能无条件反向覆盖 4+1 |

### 9.2 P0：必须先裁决的核心文档冲突

| 编号 | 冲突 | 文档证据 | 影响 | 修正要求 |
|---|---|---|---|---|
| P0-1 | W5 作者区转移已被 ADR-0149 裁决，但根 README/workspace 仍写为 W1 advisory/future；同时 `docs/L1` 仍称 per-view 冲突时获胜 | ADR-0149:38-47,118；`architecture/README.md:10-13,104-109`；`architecture/workspace.dsl:5-8,16`；`docs/L1/agent-service/README.md:30-31,51,63` | 后续编辑者无法判断 DSL 与 materialized 视图的裁决顺序，容易继续制造双权威；更准确地说，这是过渡期元数据与优先级文字未同步，不是 authority transfer 本身失效 | 首先修正 W5 元数据，并明确 W5-W6 期间 authored zone、materialized 4+1 和 contract 的生成/冲突规则 |
| P0-2 | Layer 3 在 Logical 主组件图中仍作为同级层显示，且正文继续为这种画法辩护 | ADR-0141:51-62,106-110；`logical.md:34,99-110` | 明确违反已接受 ADR，对读者错误表达现存运行层 | 同时修 Mermaid 主图和正文解释段；从主图移除 Layer 3，仅在主图之后以 `design_only` binding 子节/注释表达三轨方向 |
| P0-3 | Suspend/Resume/Cancel 事件究竟在 CAS 前还是 CAS 后发出没有统一定义 | ADR-0145:119-132；`logical.md:444-454`；`process.md:130-139`；`run-event.v1.yaml:115-131,204-206` | 审计事件、状态事件、失败/拒绝语义无法机械验证，Scenario->Process 追踪也不可信 | 明确区分 intent 与 committed transition，或统一发射顺序；同步 ADR、契约和全部时序图 |

### 9.3 P1/P2：视图、作者区与契约需同步的问题

| 优先级 | 问题 | 文档证据 | 修正方向 |
|---|---|---|---|
| P1 | 作者区将 `GET /v1/runs` 列表能力写入 L1 和 function points，但 HTTP 契约未包含该路由 | `architecture/docs/L1/agent-service.md:9`、`function-points.dsl:57,230`；`openapi-v1.yaml:289`、`contract-catalog.md:10` | 将其降为 proposed，或以 ADR + OpenAPI 先扩展接口范围 |
| P1 | 作者区取消状态使用 `CANCEL_REQUESTED`，与 canonical DFA/HTTP 契约 `CANCELLED` 矛盾 | `function-points.dsl:33`；`logical.md:228-239`；`openapi-v1.yaml:126-143` | 直接修正 function point；如需中间态另立决策 |
| P1 | S4/P5 将 `POST /v1/runs/{id}/resume` 写为 W2-shipped，而 W1 OpenAPI 明确未包含它 | `scenarios.md:127`、`process.md:198-221`；`openapi-v1.yaml:289-295` | 视图改为 target/design-only，或在接口决策完成后统一升级 |
| P1 | Process 的 Fast-Path 创建分支返回 `200 RunResponse`，违背 cursor 边界 | `process.md:53-71`；`openapi-v1.yaml:33-50,266-295`；ADR-0139 | 快慢路径只决定内部执行与 checkpoint，北向创建统一返回 `202 + TaskCursor` |
| P1 | Scenario S2 使用已废弃的 `AwaitTool` | ADR-0146:71-103；`scenarios.md:101,103` | 改为 canonical `AwaitToolResult` |
| P1 | Scenario S3 将父 Run 等待子 Run 写成 `SuspendSignal.forClientCallback(...)` | `scenarios.md:117`；`process.md:171`；ADR-0146 canonical `AwaitChildRun` | S3 使用 child-run variant；S4 才使用 client-callback variant |
| P1 | Physical 声称 `S2cCallbackEnvelope.tenant_id` 存在 | `physical.md:129`；`contract-catalog.md:90` | 改写为当前 out-of-band tenant binding，或另行变更载体契约 |
| P1 | `RunRepository.updateIfNotTerminal(...)` 在视图、旁证与 SPI 实际签名之间发生漂移 | `logical.md:67`；`process.md:15,55,130`；`agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/spi/RunRepository.java:44` | 统一以当前 SPI 两参数签名为准，并把 tenant guard 所在层次与 CAS 调用签名分开描述，避免把“tenant 校验职责”误写成“repository 方法签名” |
| P1 | W5 作者区 narrative 未表达原始设计中的实体层次、双模式、五层边界、配置/profile 与第三方恢复主线 | `architecture/docs/L1/agent-service.md` 对照 `logical.md`、功能点文档与 2026-05-22 设计 | 作者区补摘要和到详细视图的强链接，避免新 authoring root 语义缩水 |
| P1 | `verification.dsl` 的测试旁证存在现态路径失配，且本 review 原稿误用了旧测试名 | `architecture/features/verification.dsl:14-95` 当前登记的是 `RunControllerCreateIT`、`RunControllerCancelIT`、`IdempotencyStoreIT`、`TenantContextFilterIT`、`RunStateMachineTest`、`EngineRegistryTest`、`PostureBootGuardTest`；其中多条 `saa.sourceFile` 与仓库现态不匹配 | 这是作者区引用完整性问题，也是本 review 证据新鲜度问题；应先按 `verification.dsl` 当前登记项逐条校正，再重跑 architecture gate |
| P2 | Physical 使用现在时描述 design-only `RunEvent` 发射/消费 | `physical.md:21,80-92,129`；`run-event.v1.yaml:24` | 全部改为 target-state/design-only 限定 |
| P2 | L2 Boundary Contract 只有五个 zone，无法直接覆盖 F01..F47 扩展后的边界面 | `development.md:16,170-274`；`features/README.md` | 补 Access/A2A/S2C、Engine/Translation 及配置/remote recovery 合同，或声明它们由哪项既有合同覆盖 |

### 9.4 必须补入 4+1 的机械化追踪表

此前提出的 Scenarios -> Logical/Process 显式追踪表应采纳。表中 `RunEvent` 仍标为设计事件；在 P0-3 的顺序冲突裁决前，事件栏只列种类，不承诺 CAS 前后次序。

| 场景 | 经过的逻辑层 | 状态主干 | 设计事件（`RunEvent: design_only`） | 通道口径 |
|---|---|---|---|---|
| S1 标准创建/快速执行 | L1 -> L2 -> L4 -> L5a/5b | `PENDING -> RUNNING -> SUCCEEDED/FAILED` | Created / StateTransition / Terminal | 当前 HTTP + 内部直调；三轨为目标绑定 |
| S2 长任务与恢复 | L1 -> L2 -> L4 -> L5a/5b | `PENDING -> RUNNING -> SUSPENDED -> RUNNING -> terminal`；挂起原因=`AwaitToolResult` 等 | Suspend / Resume / StateTransition | cursor 为契约边界；data/rhythm 为目标绑定 |
| S3 子 Agent / 远程协作 | L1 -> L2 -> L4 -> L5a -> remote | parent `AwaitChildRun` + child terminal + parent resume | Child / Suspend / Resume | control/data 目标态 |
| S4 S2C 客户端回调 | L5a -> L4 -> client -> L4 -> L5a | server `AwaitClientCallback` suspend/resume | S2cCallback / Suspend / Resume | Transport 边界存在；HTTP resume 不在 W1 契约 |
| S5 取消与竞态 | L1 -> L2（由 L4/HTTP 触发） | active -> `CANCELLED`；terminal conflict -> 409 | Cancel / StateTransition / Terminal | 当前 HTTP cancel 已交付；事件目标态 |

### 9.5 必须写成跨视图明确约束的双模式原则

| 约束 | Logical View 应明确 | Process View 应明确 | Physical View 保持 |
|---|---|---|---|
| 双部署模式 | 责任边界不因组件同进程或远程部署变化：Run 仍由 Layer 2 所有，middleware 仍归控制边界治理 | 同进程直调或远程无状态调用只改变物理链路，不改变 Task/Run、tenant、CAS 与幂等语义 | Mode A / Mode B 的部署位置、网络与存储拓扑 |
| 双调用模型 | Fast/Slow 仅影响执行策略，不能绕过入口/持久化/安全边界 | 创建响应始终受 cursor 契约约束；内部执行是否快速完成不改变 HTTP 创建语义 | 通道和部署差异可随路径变化 |

### 9.6 Logical View 应集中的关键约束清单

| 约束编号 | 建议正文 |
|---|---|
| LV-R1 | Runtime 或 Engine 不得直接越过控制边界调用内部 Middleware 语义；hook bridge 的允许形态必须明确并可验证。 |
| LV-R2 | 所有 Run 状态写入必须经 `RunRepository.updateIfNotTerminal(UUID, UnaryOperator<Run>)` 或其明确升级后的唯一权威签名。 |
| LV-R3 | Layer 3 是 `design_only` 通道绑定，不是现有同级运行层；依 ADR-0141 不得画在主组件图内。 |
| LV-R4 | Fast-Path 不能绕过 tenant、幂等、metadata persistence 或存储隔离要求。 |
| LV-R5 | A2A / S2C / remote path 不能绕过统一 control boundary 和重新鉴权。 |
| LV-R6 | 核心实体与跨边界消息必须能够关联 tenant；不存在的载体字段不得写成当前事实。 |
| LV-R7 | `RunEvent` 在事件顺序被统一且 runtime contract 升级前只作为设计事件映射使用；不得同时声称 CAS 前与 CAS 后语义。 |
| LV-R8 | 同进程与远程调用不改变 Run/Task 所有权与状态语义。 |

### 9.7 Process View 应补入的异常路径总表

| 异常路径 | 检测者 | 当前/目标返回或状态 | 是否产生事件 | 通道/链路口径 |
|---|---|---|---|---|
| Idempotency hit，同请求 | `IdempotencyStore` / Access | 返回既有 cursor 或当前契约定义结果 | 事件待设计 | 当前 HTTP |
| Idempotency drift | `IdempotencyStore` / Access | conflict error | 审计事件目标态 | 当前 HTTP |
| Cross-tenant access | tenant filter / controller | 当前 cancel/resource 路径按契约隐藏或拒绝 | 审计要求需按现态/目标态区分 | 当前 HTTP |
| Illegal transition | Run state/CAS path | `409 illegal_state_transition` | `RunEvent` 目标态 | 当前 HTTP + 状态主干 |
| Callback timeout | S2C control path | Run fail/suspend 语义需契约固定 | 设计事件 | S2C/control 目标链路 |
| Late callback after terminal | S2C control path + RunRepository | 不得重开终态 Run | 设计审计事件 | control 目标链路 |
| Child run failure | Orchestrator | 父 Run 恢复/失败/补偿策略需明确 | 设计事件 | control/data 目标链路 |
| Middleware rejection / sandbox denial | Layer 4 middleware / sandbox boundary | controlled failure 或 suspend | 设计审计事件 | control 目标链路 |

---

### 9.8 Development / SPI 完整性补审

| 边界范围 | 当前 L2 Boundary Contract 覆盖 | 对功能点清单的缺口 | 建议 |
|---|---|---|---|
| Run / Session / RLS | 已有 run lifecycle、session decoupling、Postgres RLS 合同 | 可承接 F09..F16 的主体，但 configuration snapshot/audit 仍需说明 | 补配置引用和审计输出 |
| Control / routing / queue | 已有 orchestrator backpressure、DualTrackRouter、Internal Event Queue 合同 | 可承接 F17..F29 主体；RunEvent 次序未收敛 | 先裁决 P0-3，再细化 event/output |
| Access / A2A / S2C | 无独立 L2 合同 | F01..F08、F30..F32 的协议、重鉴权、回调恢复边界缺少单点规范 | 增加入口与协作 Boundary Contract |
| Engine / Translation / Tool | 无独立 L2 合同 | F33..F47 的 adapter 严格匹配、hook bridge、profile/config ownership 缺单点规范 | 增加 5a/5b Boundary Contract，引用 ADR-0140 |
| 第三方/远程恢复 | 无明确合同 | 原始提案的 heterogeneous agent 与同一 invocation 恢复可能被功能点清单覆盖但未被规范化 | 新增恢复与能力协商合同或明确归并位置 |

---

## 10. 不可与核心文档冲突的设计与契约基线

下表可作为后续修订作者区与 4+1 文本时的最低安全基线；“当前可写”来自已裁决 ADR、canonical 细节或契约，不以实现覆盖设计判断。

| 主题 | 文档基线中可以明确写出的事实 | 在同步完成前不能写成事实的内容 |
|---|---|---|
| 权威链 | ADR-0149 裁决 W5 作者区转移已完成；ADR-0143 的 detailed views 仍是待同步的细节载体 | 不说明生成/覆盖规则而任由作者区和 per-view 各自获胜 |
| HTTP Run API | `openapi-v1.yaml` 定义 create=`202 + cursor`、get-by-id、cancel 三项 W1 Run 生命周期路由 | list、resume、SSE、webhook 已属于相同契约范围 |
| Cancel 状态 | Logical/Process/OpenAPI 采用取消目标状态 `CANCELLED` | 未决策即将 `CANCEL_REQUESTED` 引入当前 DFA |
| Run 写入 | ADR-0142 要求 Layer 2 的 `RunRepository.updateIfNotTerminal(...)` 是状态迁移主干 | 由某一视图引入与 SPI appendix/契约不一致的新签名 |
| Tenant 边界 | HTTP、Ingress、S2C 各自必须说明 tenant 绑定机制 | 将 HTTP header/JWT、Ingress 字段和 S2C out-of-band 绑定混写为同一字段事实 |
| Layer 3 | ADR-0141 只允许 `design_only` 三轨 binding 子节 | 将其画为主图内现存同级运行层，或声明已选定 broker/runtime |
| RunEvent | ADR-0145 与 schema 定义十类设计事件；其发射顺序必须统一后才能引用 | 在同一 L1 中同时定义 CAS 前和 CAS 后的 suspend/resume/cancel 语义 |
| Fast/Slow | ADR-0139 仅收窄 checkpoint/snapshot 决策，不改变 metadata、tenant、RLS、cursor 红线 | 以 fast 分支改变北向创建响应或绕开安全边界 |
| OSS/适配 | Spring AI、Temporal、broker 等只能按各层用途/候选边界描述 | 从依赖存在反推完整能力或固定物理选型 |

---

## 11. 建议的修订顺序

| 顺序 | 修订对象 | 具体动作 | 完成标准 |
|---:|---|---|---|
| 1 | ADR-0149 后的权威入口说明：`architecture/README.md`、`architecture/workspace.dsl`、`docs/L1/agent-service/README.md` | 消除 W1/advisory 旧状态，声明 W5-W6 的 authored zone、物化视图和契约优先级/同步方式 | 后续编辑只有一条可执行裁决链 |
| 2 | ADR-0145 / `run-event.v1.yaml` / Logical / Process / Scenarios | 决定 intent event 与 committed transition event 是否分型，或统一 CAS 前后顺序 | 所有事件表、时序图和 schema 对同一事件给出同一语义 |
| 3 | `docs/L1/agent-service/logical.md` | 按 ADR-0141 将 Layer 3 移出主组件图；加入红线及双模式不变量 | Logical 不违反 ADR，且成为责任边界审查入口 |
| 4 | `process.md`、`scenarios.md`、`physical.md` | 修正 cursor/resume、`AwaitToolResult`、`AwaitChildRun`、S2C tenant 及 design-only 时态；加入追踪表和异常表 | 三个视图与 ADR/OpenAPI/contract catalog 一致 |
| 5 | `architecture/docs/L1/agent-service.md`、`function-points.dsl`、`verification.dsl` | 补全原始主线摘要，纠正 list/cancel/tenant/verification 引用，并统一 `updateIfNotTerminal(...)` 的职责描述与签名口径 | 作者区既不语义缩水，也不扩大契约现态；验证旁证与 SPI 签名均可解析 |
| 6 | `development.md` / `spi-appendix.md` / 功能点文档链接 | 为 Access/A2A/S2C、5a/5b、配置/远程恢复补 Boundary Contract 或复用声明 | 47 项功能点可机械追踪到 L2 规范 |

---

## 12. 本次盘点的最终判断

1. 原始设计的核心方向并未丢失：双部署/双调用、五层与 5a/5b、Run/Task/Session/Memory、A2A/S2C、Fast/Slow 收窄语义、Shadow Tool/配置治理均能在最新文档集合中找到承载面。
2. 当前集合仍存在不可忽略的核心文档冲突，最严重的是 W5 过渡期权威链文字未闭合、Logical 主图与正文共同违反 ADR-0141、RunEvent 的 CAS 前后顺序互斥。未先解决这三项，继续扩写功能点说明或接口说明会放大不一致。
3. Logical View 与 Process View 均不能直接判为合格：前者需先修图、修正文和补红线，后者需统一事件/接口语义并补异常规范；Scenarios 与 Physical 也需同步术语、tenant、design-only 时态与签名口径。
4. `AS-L1-F01..F47` 适合继续作为完整性检查表，但其 `proposed` 定位必须保持；真正的 L1 基线应由 ADR 裁决、单一作者区、可生成/可同步的 4+1 与一致的契约共同形成。
5. 本 review 文档自身也必须跟随仓库现态更新旁证；如果脚注、路径、测试名和 SPI 签名已经过期，即使主结论成立，也会降低这份评审稿的可信度。
