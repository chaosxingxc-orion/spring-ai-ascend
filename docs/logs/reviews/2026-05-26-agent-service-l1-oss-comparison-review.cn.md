---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-service
affects_level: [L1, L2]
affects_view: [scenarios, logical, process, development, physical]
status: proposed
language: zh-CN
relates_to:
  - docs/L1/agent-service/README.md
  - docs/L1/agent-service/scenarios.md
  - docs/L1/agent-service/logical.md
  - docs/L1/agent-service/process.md
  - docs/L1/agent-service/physical.md
  - docs/L1/agent-service/development.md
  - docs/L1/agent-service/spi-appendix.md
  - docs/adr/0136-vocabulary-reconciliation-pr71-task-vs-run.yaml
  - docs/adr/0137-suspendsignal-canonical-interruptsignal-glossary.yaml
  - docs/adr/0138-agent-service-five-layer-l1-ratification.yaml
  - docs/adr/0140-agent-service-layer5-split.yaml
  - docs/adr/0141-internal-event-queue-design-only.yaml
  - docs/adr/0142-run-aggregate-single-owner.yaml
  - docs/adr/0143-review-log-demotion-l1-canonical-move.yaml
  - docs/adr/0144-agent-service-layer-package-matrix.yaml
  - docs/adr/0145-run-event-sealed-hierarchy.yaml
---

# Agent Service L1 — 开源 Agent / Workflow 架构对比评审意见

> 日期：2026-05-26  
> 范围：`agent-service` L1 canonical 4+1 视图与后续 L2 backlog。  
> 来源：`docs/L1/agent-service/oss-agent-service-comparison.md` 的开源对比分析。  
> 定位：本文是 `docs/logs/reviews/` 下的新增架构评审意见，不替代 `docs/L1/agent-service/` 下的 canonical L1 视图。若本文与 canonical L1 视图冲突，以 `docs/L1/agent-service/` 为准。

## 1. 结论先行

没有发现单一 Java 开源项目与当前 Agent Service 的 L1 5 层职责完全等价。更准确的判断是：Agent Service 是多个成熟开源架构模式的组合型 **Agent-native control service**，把协议接入、Run/Task/Session、内部事件、Task-centric runtime governance、engine adapter 与 Spring AI / LangGraph / AutoGen / CrewAI / OpenAI Agents / Semantic Kernel 式工具和运行时翻译能力，收敛到一个受 ADR 和 Rule 约束的服务边界内。

当前 L1 5 层划分总体合理，尤其是以下 rc55 修正必须保留：

1. **Layer 2 单独拥有 Run aggregate**：与 A2A TaskStore、Temporal workflow history、Conductor task persistence 的事实源实践一致。状态持久化必须有单一拥有者，Layer 4 只能通过 `RunRepository.updateIfNotTerminal(...)` 发起状态转移意图。
2. **Layer 3 标记为 `design_only`**：当前没有 `service.queue/` 代码 home，但先声明三轨 bus 绑定可避免队列职责被误塞进 Orchestrator 或 Runtime Adapter。
3. **Layer 5 拆成 5a / 5b**：执行适配与 prompt / tool / model translation 的变化节奏不同。ADR-0140 的拆分避免 ChatAdvisor、RuntimeMiddleware、ExecutorAdapter 三者混淆。

本文不建议把 Agent Service 退化为任何单一开源框架的封装。下一步应优先把 Layer 2 / Layer 3 / Layer 4 的状态转移、事件队列、Hook / Suspend 语义推进到最小可验证代码与 contract test，而不是先扩展更多 engine adapter。

## 2. 对比对象与判断坐标

本评审把开源项目放入 Agent Service 4+1 坐标系，而不是按项目名直接得出架构结论。

| 类型 | 代表项目 | 与 Agent Service 的关系 | 判断 |
|---|---|---|---|
| Protocol-first service | A2A Java SDK、Spring AI A2A | 提供 A2A Task / AgentCard / executor 接入能力 | 可作为 Layer 1 / Task protocol 参照，但不是完整控制面 |
| Runtime-wrapper service | AgentScope Runtime Java | 把单个 agent framework 包成 runtime service | 最接近服务形态，但缺少完整 Run/Task/Queue/governance |
| Workflow-orchestration service | Temporal Java SDK、Conductor | 提供 durable task、worker、history、retry、signal | 可作为 Layer 3 / Layer 4 long-running control 参照，但不是 Agent 语义核心 |
| Java agent platform / library | Spring AI Alibaba、LangGraph4j、LangChain4j、Spring AI、Solon AI | 覆盖 graph、tool、MCP、sandbox、ChatClient、session 等切面 | 可作为 Layer 4 / 5a / 5b 局部参照 |
| Non-Java agent runtime | LangGraph、AutoGen、CrewAI、OpenAI Agents Python、Semantic Kernel | 分别强化 graph state、actor queue、crew orchestration、runner、plugin kernel | 可提取设计模式，不应反向决定 Java L1 边界 |

结论：开源生态多数只覆盖 Agent Service 的某个视图或某一层。真正与我们接近的是组合模式，而不是单个代码库。

### 2.1 逐项目证据展开

| 项目 | 最接近的 Agent Service 层 | 不能直接照搬的原因 | 可沉淀的架构意见 |
|---|---|---|---|
| A2A Java SDK | Layer 1 + Layer 2 的协议 Task 边界 | 它只定义协议可见 Task / Message / TaskState / AgentExecutor，不拥有内部 Run execution state、Session projection 或三轨队列 | 借鉴 `TaskState` 的 final / interrupted 谓词和 AgentCard / push notification；但 A2A TaskStore 不替代 RunRepository |
| Spring AI A2A | Layer 1 thin adapter + Layer 5b ChatClient bridge | 它默认把请求快速桥接到 ChatClient，不治理 tenantId-first persistence、cancel race、RuntimeMiddleware 或 S2C callback | A2A starter 应保持 auto-configuration / controller / executor handler 分离，controller 不拥有状态 |
| Spring AI Alibaba | Layer 4 / 5a / 5b 的 Java 平台能力集合 | graph、agent、admin、MCP、sandbox、A2A 分散在多个模块，不是单一 Agent Service 控制面 | 可参考 Java/Spring 生态下 graph、MCP、sandbox、admin 的平台化方向，但仍要受本项目 5 层边界约束 |
| AgentScope Runtime Java | Layer 1 + 5a 的 runtime-wrapper service | 它面向单 agent runtime service，Runner 包住 AgentHandler；缺少完整 Run/Task/Queue/Task-centric governance | 借鉴 protocol handler → unified runner 的 thin handoff，以及 session/state/memory/sandbox externalization |
| Temporal Java SDK | Layer 3 + Layer 4 的 durable execution substrate | Temporal 不理解 Agent protocol、tool governance、A2A/S2C 或 Session context；workflow history 不能替代 Run/Task/Session 三聚合 | 借鉴 history/replay、signal、timer、worker task queue 对 long-running recoverability 的约束 |
| Conductor | Layer 1 / 2 / 3 / 4 的 workflow/task orchestration server | 它以 workflow task / worker 为核心，Agent/LLM 只是 worker 扩展，不承载 SuspendSignal / HookPoint / EngineRegistry 语义 | 借鉴 worker poll/update、retry/timeout、human task、dead-letter 与 task query API |
| LangGraph4j / LangGraph | Layer 4 + 5a 的 graph runtime | 它们是 graph execution kernel / hosted graph API，不定义企业级 ingress、tenant/RLS、Run aggregate single owner | 借鉴 interrupt/resume/checkpoint/state history；Checkpointer SPI 必须保持小而不吞并 Session/Memory |
| LangChain4j / Spring AI / Semantic Kernel | Layer 5b 的 model/tool/function invocation kernel | 它们治理模型调用边界，不知道 Run CAS、tenant、cancel、audit 或 Layer 4 middleware | 借鉴 Advisor / ToolExecutor / plugin filter / structured output，但 RuntimeMiddleware 仍必须留在 Layer 4 |
| AutoGen | Layer 3 actor/message runtime + Layer 4 intervention | asyncio queue 与 subscription 很强，但不是 durable service queue，也没有本项目 tenant-first aggregate | 借鉴 message envelope、topic/subscription、cancellation token、intervention/drop 字段，用于 RunEvent envelope 设计 |
| CrewAI / OpenAI Agents Python | Layer 4 / 5a 多 agent orchestration 与 runner | 它们是应用级 Crew/Flow 或 SDK runner，不定义服务级 Run/Task/Queue | 借鉴 handoff、guardrail、human feedback、next-step loop，但不能让 SDK runner 拥有服务状态 |

### 2.2 首版摘要相对原文遗漏的关键信息

首版生成稿过度压缩了源分析中的逐层证据，主要遗漏了四类细节：

1. **逐项目“不等价原因”不够明确**：只说某项目可参考，容易被误读为可替代。本文补充每个 OSS 项目的边界缺口，明确哪些只覆盖 protocol、runtime、workflow、graph、tool 或 plugin kernel。
2. **Layer 3 的 worker contract 细节不足**：源文档强调 queue 不只是 channel，还包括 Producer / Consumer / lease / ack / retry / dead-letter / heartbeat。本文将其升级为 P0 backlog，并把 Rule R-E 的三轨隔离作为约束列。
3. **Layer 5 capability discovery 依据不足**：源文档提到 LangChain4j capability、A2A AgentCard、AgentScope metadata。本文补充它只能增强 `EngineRegistry.resolve(envelope)`，不能削弱 strict matching。
4. **Session / Memory 并发风险没有展开**：源文档借 LangChain4j `@MemoryId` 风险说明并发纪律不能留给调用者自律。本文将其列为 P1 L2 backlog，并要求与 tenantId-first / RLS 一致。

## 3. 严格采纳 / 改写 / 拒绝分类

### 3.1 采纳（ACCEPT）

| 评审意见 | 依据 | 采纳方式 |
|---|---|---|
| Access Layer 必须保持 thin protocol adapter | AgentScope Runtime、Spring AI A2A、A2A Java SDK | Layer 1 只做协议转换、tenant / idempotency / trace 绑定，不拥有 Run 状态机，不直接驱动 Runtime |
| Run / Task / Session 三聚合拆分正确 | A2A TaskState、Temporal execution、AgentScope session/state、LangChain4j memoryId 风险 | 继续维持 Task 管 control state、Run 管 execution state、Session 管 context state |
| Layer 3 独立出来是必要的 | Temporal worker/history、Conductor queue/worker、AutoGen envelope/queue | 即使代码 home 仍是 design_only，也必须保留 L1 层次和三轨 channel 绑定 |
| Layer 4 是平台差异化核心 | LangGraph4j interrupt/resume、Temporal signal、Conductor human task、OpenAI Agents interruption | SuspendSignal、RuntimeMiddleware、Fast/Slow、S2C callback、cancel race 继续在 Layer 4 收口 |
| Layer 5a / 5b 拆分合理 | Spring AI ChatClient/Advisor、LangChain4j ToolExecutor、AgentScope Runner、LangGraph4j CompiledGraph | 5a 负责 execution dispatch，5b 负责 prompt/tool/model translation；RuntimeMiddleware 不进入 5b |
| 4+1 视图迁移到 `docs/L1/agent-service/` 是正确治理动作 | ADR-0143 + Rule G-1 | `docs/logs/reviews/` 仅保留交互评审记录，canonical L1 以 `docs/L1/agent-service/` 为准 |

### 3.2 需改写后采纳（MODIFY）

| 评审意见 | 风险 | 改写后的落点 |
|---|---|---|
| `Task.A2aState` 增加 `isFinal()` / `isInterrupted()` / `isActive()` | 直接改枚举会触及实现与测试，不能在 review log 中声明已实现 | 作为 Layer 2 Task lifecycle L2 backlog，后续 impl-mode 通过测试驱动实现 |
| `TaskStateStore` 增加 CAS-style transition primitive | 若直接类比 RunRepository，可能忽略 Task 与 Run 的语义差异 | L2 contract 先定义 `transitionIf(...)` 或等价原语，再由实现波次落地 |
| Layer 3 增加 Publisher / Consumer / Lease / DeadLetter 最小骨架 | Layer 3 当前 design_only，不能在 L1 评审中暗示代码已存在 | 写入 Layer 3 L2 backlog，要求任何实现都保留 control/data/rhythm channel 与 durability tier 正交 |
| RunEvent 与 channel routing 增加测试 | `run-event.v1.yaml` 当前 design_only，Java sealed type 尚未落地 | 在 ADR-0145 后续实现波次中增加 contract/gate 测试，不在本文声明 runtime enforced |
| Engine capability discovery | 不能替代 Rule R-M.a/.b 的 strict matching | 作为 `EngineRegistry.resolve(envelope)` 的增强维度：先 strict type matching，再 capability-aware matching |
| RuntimeMiddleware 与 ChatAdvisor 顺序契约 | 两者不同层，不能合并为一个 interceptor | 新增 Layer 4/5b L2 contract：定义 HookPoint、AdvisorChain、short-circuit、exception propagation 的组合顺序 |
| Checkpointer SPI 极小化 | 容易把 session / memory / workflow history 塞进一个大接口 | L2 contract 明确 Checkpointer 只承载 compute snapshot，不承载 Session 或 Memory 主权 |
| Protocol adapter thinness rule | A2A / MQ / HTTP starter 可能在 controller 中持有状态 | Access Layer L2 contract 明确 controller 只转译协议并调用 Layer 2/4 服务 |

### 3.3 拒绝（REJECT）

| 候选方向 | 拒绝原因 |
|---|---|
| 把 Agent Service 设计改成某个 OSS 框架的直接封装 | 违反当前 L1 的服务控制面定位；开源项目多数只覆盖某一层或某个视图 |
| 用 A2A TaskStore 替代 Run / Task / Session 三聚合 | A2A Task 是协议 control state，不能承载内部 execution state、tenantId-first persistence、cancel race 与 Session projection |
| 用 Temporal / Conductor workflow history 替代 Agent Service Layer 2/3/4 | durable workflow 是可借鉴的 long-running substrate，不是 Agent-native Run/Task/SuspendSignal/HookPoint 模型 |
| 让 Layer 5 engine adapter 反向决定 Layer 2/4 状态模型 | 违反 Rule R-M.a/.b 和 ADR-0142；engine 只能被适配，不能拥有 Run aggregate |
| 把 ChatAdvisor 视为 RuntimeMiddleware 的别名 | ADR-0140 已明确 RuntimeMiddleware 属于 Layer 4，ChatAdvisor 属于 Layer 5b；两者组合但不等价 |
| 把 Layer 3 写成单一 MQ 或单一持久化模式 | 违反 Rule R-E；channel intent 与 durability tier 是正交维度 |

## 4. 分层评审意见

### 4.1 Layer 1 — Access Layer

开源对照显示，入口层越薄，核心状态机越不容易被协议污染。A2A Java SDK 与 Spring AI A2A 适合作为 A2A endpoint / AgentCard / executor bridge 的参考；AgentScope Runtime 的 protocol handler → Runner 形态可作为 thin adapter 参照。

**评审意见**：当前 Layer 1 合理。后续实现 A2A / MQ / HTTP 扩展时，controller 不应拥有 TaskStore、RunRepository 或 executor lifecycle；它只应完成协议转换、tenant / idempotency / trace 绑定，并调用 Layer 2/4 的服务入口。

### 4.2 Layer 2 — Session & Task Manager

A2A Java 的 TaskState 谓词、AgentScope 的 externalized session/state、Conductor 的 task persistence、Temporal 的 durable identity 都支持当前 Run / Task / Session 拆分。LangChain4j 对同一 `@MemoryId` 并发调用的风险提示进一步说明：Session / Memory 并发约束必须平台化。

**评审意见**：Layer 2 是当前设计中最应保持稳定的层。后续优先增强 Task lifecycle 的 transition primitive，并把 Session projection / Memory mutation 纳入明确并发纪律。

### 4.3 Layer 3 — Internal Event Queue

Temporal、Conductor、AutoGen 共同说明，内部队列不是“未来换一个 MQ”这么简单。Layer 3 至少要定义 event envelope、channel routing、lease、ack、retry、dead-letter、idempotency key 和 durable tier 的关系。

**评审意见**：Layer 3 保持独立层是正确的，但必须持续标注 `design_only`，直到 `service.queue/` 或等价 code home 落地。后续最小骨架可包括 `RunEventPublisher`、`RunEventConsumer`、`QueueLease` 与 dead-letter contract，但不能破坏 Rule R-E 的 control/data/rhythm 物理隔离。

### 4.4 Layer 4 — Task-Centric Control Layer

LangGraph4j / LangGraph 的 interrupt/resume、Temporal signal、Conductor human task、OpenAI Agents 的 interruption / next-step loop 都支持 SuspendSignal 与 runtime governance 成为一等结构。Layer 4 的价值不是实现某个 agent loop，而是决定何时启动、暂停、恢复、取消、短路、升级 Slow-Path、触发 middleware。

**评审意见**：Layer 4 是 Agent Service 区别于普通 Agent SDK 的核心价值。下一步应优先定义 RuntimeMiddleware ordering / short-circuit / exception propagation contract，并将 S2C callback、child-run、tool-await 三类 suspension 统一映射到 SuspendReason / RunEvent / channel routing。

### 4.5 Layer 5a / 5b — Engine Adapter Layer

Spring AI、LangChain4j、LangGraph4j、AgentScope Runtime、OpenAI Agents、Semantic Kernel 证明 engine 类型会不断变化：graph engine、actor runtime、crew orchestration、turn-based runner、kernel function pipeline 都可能成为被适配对象。

**评审意见**：ADR-0140 拆分 5a / 5b 是正确且必要的。后续 `EngineRegistry.resolve(envelope)` 可从单纯 `engine_type` strict matching 增强为 capability-aware matching，但 strict matching 仍是 Rule R-M.a/.b 的底线。

## 5. 4+1 视图评价

当前 4+1 设计合理：

| 视图 | 当前价值 | 开源对照 |
|---|---|---|
| Scenarios | S1-S5 覆盖 Fast/Slow、A2A、S2C、cancel race | 类似 Temporal / Conductor 用场景驱动失败路径 |
| Logical | 5 层 + Run/Task/Session ER + state machine + RunEvent | 对应 A2A TaskState、Temporal state machine、Conductor task model |
| Process | P1-P6 序列图，尤其 cancel winner/loser | 对应 workflow engine 对 race / retry / signal 的过程建模 |
| Physical | 五平面、RLS、三轨 bus、sandbox boundary | 对应 Conductor / Temporal 的部署与持久化分离，并加入 tenant/RLS |
| Development | package tree、Layer↔Package matrix、L2 boundary contracts | 比多数开源项目更强的治理视图，适合长期多人开发 |

建议后续追加一个 OSS pattern appendix 或从 README 链接本文，避免后续 L2 设计遗忘开源参照。但该 appendix 只能作为参考材料，不能替代 ADR、Rule、contract catalog 或 canonical L1 view。

## 6. 后续改进 backlog

下表不是立即实现清单，而是把 OSS 证据转成后续 L2 / impl-mode 的候选入口。每一项都必须在落地前补齐 contract、测试与门禁，不得仅凭“某 OSS 如此设计”改变当前 canonical L1。

| 编号 | 改进点 | 来源实践 | 建议落点 | 优先级 | 约束 |
|---|---|---|---|---|---|
| I-01 | `Task.A2aState` 增加状态谓词 | A2A Java `TaskState` | `service.task` | P0 | 不改变 RunStatus DFA |
| I-02 | `TaskStateStore` 增加 CAS-style transition primitive | A2A TaskStore + RunRepository | `service.task.spi` | P0 | 先 L2 contract，后实现 |
| I-03 | Layer 3 Publisher / Consumer / Lease / DeadLetter 骨架 | Conductor queue、Temporal task queue | future `service.queue/` | P0 | 保持 `design_only` 直到代码落地 |
| I-04 | RunEvent channel routing 单测 | Temporal event history、Conductor task events | `run-event.v1.yaml` + tests | P0 | 必须映射 control/data/rhythm |
| I-05 | Engine capability discovery | LangChain4j capabilities、A2A AgentCard、AgentScope metadata | `service.engine.spi` | P1 | 不削弱 strict matching |
| I-06 | RuntimeMiddleware ordering / short-circuit / exception contract | Spring AI Advisor、LangChain4j ToolExecutor | Layer 4 L2 design | P1 | RuntimeMiddleware 仍只属于 Layer 4 |
| I-07 | Checkpointer SPI 极小化 | LangGraph4j checkpoint saver | orchestration SPI | P1 | 不承载 Session / Memory 主权 |
| I-08 | Protocol adapter thinness rule | AgentScope Runtime Runner | Access Layer L2 | P1 | controller 不持有 Run 状态 |
| I-09 | Session / memory mutation 并发纪律 | LangChain4j `@MemoryId` 风险 | Session / Memory L2 | P1 | 与 tenantId-first 和 RLS 一致 |
| I-10 | Human/S2C callback 与 A2A input_required 对齐 | A2A Java、Conductor human task、Temporal signal | S2C / Task state | P2 | 通过 SuspendSignal canonical path 表达 |
| I-11 | Admin/debug process query API | Spring AI Alibaba workflow debug、Conductor WorkflowResource | future observability/admin | P2 | 不能绕过 Layer 2/4 |
| I-12 | sandbox/tool governance schema 与 physical enforceability 对齐 | Spring AI Alibaba sandbox、AgentScope SandboxService | Middleware / Sandbox | P2 | 必须符合 Rule R-L |

## 7. 推荐 L2 深挖顺序

1. **Layer 2 Task lifecycle L2**：先补 `Task.A2aState` 谓词和 `TaskStateStore` transition contract。
2. **Layer 3 Internal Event Queue L2**：再落 Producer / Consumer / Lease / retry / dead-letter / channel routing。
3. **Layer 4 RuntimeMiddleware + SuspendSignal L2**：定义 HookPoint 顺序、short-circuit、failure propagation、resume semantics。
4. **Layer 5 Engine Adapter L2**：定义 EngineCapability、adapter lifecycle、streaming、tool-call、checkpoint、structured-output 能力。
5. **Layer 1 A2A Access L2**：最后细化 Thin controller、AgentCard、Task Cursor、cancel/query/streaming endpoint。

## 8. 最终判断

Agent Service 的模块划分与 4+1 设计总体合理，而且比单个开源项目更完整。开源生态给出的主要启发不是“替换现有设计”，而是为每一层提供校验坐标：

- A2A Java 证明 Task 协议状态与 final/interrupted 谓词的重要性；
- Spring AI A2A / AgentScope Runtime 证明 protocol adapter 应保持薄；
- Temporal / Conductor 证明长周期任务必须有事件、队列、worker、history、retry、resume；
- LangGraph4j / LangGraph 证明 interrupt/resume/checkpoint 应是一等 runtime 能力；
- Spring AI / LangChain4j 证明 prompt/tool/model translation 与 runtime control 应分离；
- Spring AI Alibaba / Solon AI 证明 Java 生态下 agent、MCP、A2A、sandbox、admin 正在平台化收敛；
- AutoGen / CrewAI / OpenAI Agents / Semantic Kernel 证明 actor runtime、multi-agent orchestration、agent runner、plugin kernel 都是可适配对象，而不是 Agent Service 的替代边界。

因此，后续工作应以当前 canonical L1 为主线，吸收 OSS 模式补强 L2 contract 与实现门禁。优先级应放在状态转移、事件队列、Hook / Suspend 语义的可验证化，而不是扩大 engine adapter 数量。
