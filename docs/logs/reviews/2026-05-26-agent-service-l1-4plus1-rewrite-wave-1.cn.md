---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-service
affects_level: L1
affects_view: [scenarios, logical, process, development, physical]
status: proposed
language: zh-CN
relates_to:
  - docs/adr/0100-rc22-agent-service-l1-runtime-role-decomposition.yaml
  - docs/adr/0136-vocabulary-reconciliation-pr71-task-vs-run.yaml
  - docs/adr/0137-suspendsignal-canonical-interruptsignal-glossary.yaml
  - docs/adr/0138-agent-service-five-layer-l1-ratification.yaml
  - docs/adr/0139-fast-slow-path-narrowed-semantics.yaml
  - docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.md
  - docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.cn.md
---

# Agent Service L1 — 4+1 视图重写（Wave 1-6：评审稿 + 拒绝清单 + ADR 草案 + 4+1 视图 + Javadoc 词汇表）

> **降级声明（2026-05-26 rc55 按 [ADR-0143](../../adr/0143-review-log-demotion-l1-canonical-move.yaml) 追加）**：本文件是 rc53 Wave 1-6 设计过程的**历史撰写记录**。规范的 L1 4+1 源已按 ADR-0143 迁移到 `docs/L1/agent-service/{README,scenarios,logical,process,physical,development,spi-appendix}.md`。本文件与新规范视图文件冲突时，**新规范视图文件优先**。rc55 审计发现"将冻结的 review log 提升为'规范 L1 4+1 源'"违反 Rule G-1.a 的 L0/L1/L2 视图纪律（已在 `docs/governance/recurring-defect-families.yaml` 中注册为 `F-l1-canonical-source-in-interaction-log`）；ADR-0143 通过将规范源从 `docs/logs/reviews/` 移到 `docs/L1/` 来按结构闭环该家族。本文件按 `docs/governance/logs-folder-policy.md` 仍只读；该降级声明本身是允许追加的历史标记。

> **历史 artifact 冻结标记（2026-05-26 rc53-wave-8 闭环时追加）**：本文件是 rc53 wave 跨 Waves 1-6 撰写的规范 L1 4+1 源。Wave 8 闭环后（commit 见 `docs/logs/releases/2026-05-26-rc53-agent-service-l1-4plus1-rewrite-closure.en.md`），本文件按 `docs/governance/logs-folder-policy.md` **只读**。后续编辑作为新评审稿放到 `docs/logs/reviews/` 下。文件中数值是 point-in-time snapshot evidence；活动的 `agent-service/ARCHITECTURE.md` §0.5 携带前向兼容的权威指针。**rc55 更新（按上方降级声明）**：§0.5 的前向兼容权威指针现在指向 `docs/L1/agent-service/` 而非本评审文件。

> 日期：2026-05-26
> 范围：仅覆盖 `agent-service` 模块。
> Wave：6 个 wave 中的第 1 个（原 8-wave 计划在 ADR-0100 校准后压缩，见 §11）。
> 目标：以 Java 微服务 + Agentic 平台不变量为标尺，从严复审 PR #71 提出的 L1 设计；将其词汇与平台已确立的 4 层生命周期（ADR-0100）对齐；批准 5 层逻辑分解；落地 ADR 草案锁定后续 wave；分类并扫描复发缺陷家族。
> 约束：本文档是 `docs/logs/reviews/` 下的交互记录（按 `docs/governance/logs-folder-policy.md` 规约 — front-matter 可选但本文提供）。规范化 L1 文档 `agent-service/ARCHITECTURE.md` 由后续 wave 替换；在此之前仍以现行 L1 为活跃来源。

## 1. 背景

PR #71（[`docs/agent-service-l1-cn-20260525`](https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/71)）由人类专家评审后达成共识，给出 `agent-service` 的 5 层 L1 架构：

1. **对外接入层**（Gateway / A2A Service / MQ Adapter）
2. **会话与任务管理层**（SessionManager / TaskManager / StateStore）
3. **内部事件队列层**（EventProducer / QueueStorage / EventConsumer）
4. **Task-Centric 状态控制层**（生命周期 / 中断 / DualTrackRouter / Middleware API）
5. **引擎适配层**（Runtime SPI / Framework Adapter / ContextTranslator / Shadow Tool Interceptor）

本 wave（6 个 wave 之 Wave 1）做三件事：

- **吸收** PR #71 的术语进入 Rules + ADR（词汇校准，不重命名）。
- **严苛复审**：以 Java 微服务 + Agentic 平台 reviewer 身份，把 PR #71 每条声明分类为 ACCEPT / MODIFY / REJECT 并显式引用 Rule/ADR 依据。
- **重出 4+1 视图**：参考 [`docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.en.md`](2026-05-22-agent-service-l1-expansion-proposal.en.md) 的契约优先 / ADR 锚定 / SPI 直出 Java 签名设计语言，重置 L1 评审稿为严格 4+1（scenarios / logical / process / development / physical）。4+1 视图在 Wave 2-4 落地。

## 2. 关键纠错 — ADR-0100 已经拥有生命周期层级

初读 PR #71 时倾向于做 Run→Task / SuspendSignal→InterruptSignal 的整体重命名。**重读平台已落地 Java 代码与 ADR 后这个结论被推翻**。

[`ADR-0100`](../../adr/0100-rc22-agent-service-l1-runtime-role-decomposition.yaml)（rc22，2026-05-22 已接受）— 由同一组提议者撰写 — 已经确立：

### 2.1 4 层生命周期层级（Run ≤ Task ≤ Session ≤ Memory）

| 层 | Java 类型（已落地） | 语义 | 权威 |
|---|---|---|---|
| **Run** | `com.huawei.ascend.service.runtime.runs.Run`（record） | 瞬时计算快照（compute pointer + delta）；RunStatus DFA 状态机 | ADR-0021 + ADR-0100 + Rule R-C.2 |
| **Task** | `com.huawei.ascend.service.task.Task`（record） | 控制态（done-or-not, why-stopped）；A2A 协议状态包络 | ADR-0100 + `docs/contracts/a2a-envelope.v1.yaml` |
| **Session** | `com.huawei.ascend.service.session.Session`（record） | 数据上下文（讨论内容、变量） | ADR-0100 + ADR-0135 |
| **Memory** | `GraphMemoryRepository` SPI + `ConversationMemory`（M2_EPISODIC） | 知识态（who am I, rules） | ADR-0082 + ADR-0123 + ADR-0133 |

`Run` 与 `Task` 是**两个独立实体**。PR #71 中"Task 作为调度核心"映射到**平台已有的 Task 实体** — 不是 Run 的重命名。Run 实体仍然是规范化的执行态主干（Rule R-C.2.a 强制 `tenantId` + R-C.2.b 强制 `RunStateMachine.validate(from, to)` CAS 守卫）。

### 2.2 SuspendSignal 是声明的 Tier-A 差异化点（不重命名）

ADR-0100 **显式拒绝** InterruptSignal 形式的重命名。ADR-0100 原文：

> "§2.3 / §5.3 'abandon exception-based suspension, switch to explicit Yield event' — rejected because `SuspendSignal` (checked exception) is a Tier-A competitive differentiator: (a) the Java compiler enforces caller-side handling, (b) Rule R-G ArchUnit tests rest on the checked-exception shape, (c) rc8/rc9 cancellation paths use exception-flow semantics for cross-thread propagation."

`SuspendSignal`（`agent-execution-engine/src/main/java/com/huawei/ascend/engine/orchestration/spi/SuspendSignal.java`）在平台 29 处活跃调用（orchestrator、S2C、executor、测试）。重命名将放弃差异化并破坏 Rule R-G 的 ArchUnit 守卫。

### 2.3 Wave 计划影响

原 8-wave 计划假设 Waves 6+7 整体代码重命名。校准消除该假设：

- **Waves 1-5 不变**（评审 + 4+1 视图 + Rule / 契约更新）。
- **Wave 6 缩减**为 Run / Task / Session / SuspendSignal 类 Javadoc 增加词汇表段（无方法签名 / 字段 / 包 / DB schema 改动）。
- **Wave 7 删除**（无 Flyway 重命名迁移需要）。
- **Wave 8 不变**（把 4+1 定稿迁入 `agent-service/ARCHITECTURE.md`）。

## 3. 词汇校准表 — PR #71 ↔ 已落地平台

下表是 L1 重写的**规范术语表**。所有 L1/L2 文档、ADR、Rule 引用这些概念时使用已落地的平台名；PR #71 / 学术名作为设计语言散文中的同义词出现，但**绝不**作为 Java 标识符。

| PR #71 / 学术名 | 已落地平台名（规范） | 说明 |
|---|---|---|
| Task（"调度核心"语义） | `Task`（控制态 record，`service.task.Task`） | 已对齐；无重命名 |
| TaskID | `taskId`（String UUID，`Task.taskId`） | 已对齐 |
| TaskManager | `TaskCenter` 子包 + `TaskStateStore` SPI（ADR-0100） | Manager / Center 互用；SPI 名是 `TaskStateStore` |
| TaskEvent | Run 生命周期事件通过 `HookPoint` 枚举（`engine-hooks.v1.yaml`）触发 + slow-path 持久化时走 Outbox | 无独立 "TaskEvent" 类型；L1 事件模型基于 HookPoint |
| InterruptSignal | `SuspendSignal`（`engine.orchestration.spi.SuspendSignal`，checked 异常） | **仅作术语表同义词**；ADR-0137 记录映射 |
| InterruptType（INPUT_REQUIRED / TOOL_EXECUTION / COLLABORATION / SAFETY_CHECK） | `SuspendReason` 鉴别器 + `HookPoint.before_tool / after_tool` 链 + `Task.A2aState.INPUT_REQUIRED` | 4 种学术中断类型映射到 3 种平台机制（INPUT_REQUIRED 由 A2A 态承载、TOOL_EXECUTION 由 HookPoint、COLLABORATION/SAFETY_CHECK 由 SuspendReason） |
| Engine Adapter Layer | `EngineRegistry.resolve(envelope)` + per-`engine_type` 的 `ExecutorAdapter` | Rule R-M.a/.b；SPI 在 `agent-execution-engine.spi` |
| Runtime SPI | `ExecutorAdapter` SPI（`engine.spi.ExecutorAdapter`） | 一对一 |
| Framework Adapter | `ExecutorAdapter` 实现（`SequentialGraphExecutor`, `IterativeAgentLoopExecutor`，将来的 LangChain/LlamaIndex shell） | 一种 Framework = 一个 Adapter 实现 |
| Context Translator | `ContextProjector` SPI（`session.spi.ContextProjector`）+ `PromptTemplate`（ADR-0131）+ `StructuredOutputConverter<T>`（ADR-0130） | 3 SPI 组合 |
| Shadow Tool Interceptor | `ChatAdvisor`（ADR-0132）+ `RuntimeMiddleware` 监听 `HookPoint.before_tool / after_tool` | 工具拦截已由 2 SPI 组合提供 |
| Result Normalizer | `ExecutorAdapter` 出参契约（在 `engine-envelope.v1.yaml` 声明） | 无独立 normalizer SPI |
| Middleware Adapter | `RuntimeMiddleware`（ADR-0073）监听 `HookPoint`（`engine-hooks.v1.yaml`） | Rule R-M.c |
| Internal Event Queue | 三轨总线：`control` / `data` / `rhythm`（`docs/governance/bus-channels.yaml`，Rule R-E） | **PR #71 单队列 + 3 模式设计 REJECT**；见 §4.3 |
| EventProducer | 三轨总线生产侧（按通道） | 一个通道一个 producer intent |
| EventConsumer | 三轨总线消费侧（按通道） | 一个通道一个 consumer intent |
| QueueStorage 模式（in-mem / semi-persist / persist） | 由 `bus-channels.yaml` 每通道 `physical_channel:` 声明替换 | W0/W1 每通道 in-memory stub；W2 按 Rule R-E 升持久化 |
| SessionManager | `Session` record + `ContextProjector` SPI（ADR-0100） | Manager 是子包；SPI 名是 `ContextProjector` |
| StateStore | `RunRepository`（Run 持久化）+ `TaskStateStore`（Task 持久化）+ Session 存储（W2） | 三个独立 store，非单一 StateStore |
| DualTrackRouter | 本 Wave 1 的 ADR-0138 声明的新 SPI（W2 落地 design_only） | 映射到 `SlowTrackJudge`（per ADR-0112 已声明）；语义由 ADR-0139 窄化 |
| FastPath | 同进程反应式同步路径；保留 tenantId + RLS | ADR-0139（本 wave） |
| SlowPath | 持久化反应式 + SuspendSignal + ResumeDispatcher | ADR-0139（本 wave） |
| RemoteInterrupt（通过 A2A） | `S2cCallbackEnvelope`（`bus.spi.s2c`）走三轨 `control` 通道 | Rule R-M.d + ADR-0074；A2A 不得绕开 S2C |
| A2A Server / A2A Client | 仅 A2A 协议包络（`docs/contracts/a2a-envelope.v1.yaml`，design_only）— **无 `a2a-java` SDK 运行时依赖** | ADR-0100 §rejected-framing #1 |
| F-01..F-22 特性编号 | 平台无等价；作为 L2 backlog 接受，每行 `authority:` 列引用 ADR/Rule | 本 wave 保留为 L2 设计 backlog（见 §10 L2 边界契约） |
| Yield（PR #71 隐含） | `HookPoint.ON_YIELD` 协作调度提示（rc22 加入，per ADR-0100 §coexistence） | 与 SuspendSignal 共存；无状态机跃迁 |

## 4. 严格 Accept / Modify / Reject 分类

PR #71 每条声明 **ACCEPT**（原样纳入 Wave 2-4）/ **MODIFY**（按列出的修正条款纳入）/ **REJECT — P0**（绝不出现在重写稿中）。

### 4.1 对外接入层（PR #71 §3.1）

| PR #71 声明 | 判定 | 依据 |
|---|---|---|
| Gateway 协议转换（REST / gRPC / WebSocket） | **ACCEPT** | Rule R-F（游标流）+ `openapi-v1.yaml` 已声明 HTTP 契约面。 |
| Gateway 不直接驱动 Runtime 也不直接调 Middleware | **ACCEPT** | 等价 Rule R-M.a（`EngineRegistry.resolve(...)` 强制）+ Rule R-M.c（RuntimeMiddleware 统一治理）。 |
| A2A Server + A2A Client 双向 | **MODIFY** | 双向能力接受；"remote interrupt" 必须走 `S2cCallbackEnvelope` 经三轨 `control` 通道（Rule R-M.d + Rule R-E + ADR-0049）；A2A 不得直接终止远端 Run。无 `a2a-java` SDK 运行时依赖（ADR-0100 §rejected-framing #1）。 |
| MQ / Event Bus 异步入口（Kafka / RabbitMQ / RocketMQ / Pulsar） | **MODIFY** | 异步入口落到 `IngressEnvelope`（`docs/contracts/ingress-envelope.v1.yaml`）经 Rule R-I.1；broker 选型是 W2+ 部署问题，不在 L1。 |

### 4.2 会话与任务管理层（PR #71 §3.2）

| PR #71 声明 | 判定 | 依据 |
|---|---|---|
| Session ↔ Task 1:N（一 Session 多 Task） | **ACCEPT** | 与 ADR-0100 + ADR-0135（AgentSession = (tenantId, conversationId) 在 Run 序列上的投影；Task 是 Session 内有边界的执行）一致。 |
| TaskManager + TaskID + 标准化创建 | **ACCEPT** | 已通过 `Task` record + `TaskStateStore` SPI 落地（ADR-0100 §decision）。无重命名。 |
| Task A2A 状态（Submitted / Working / Input_Required / Completed / Failed） | **ACCEPT** | 已落地为 `Task.A2aState` 5 值枚举，per `docs/contracts/a2a-envelope.v1.yaml`。拒绝 PR #71 扩展为 9 个并列 Run 状态（"working / input-required / tool-required / processing / completed / canceled / failed"）— 这些应折叠为 5 个 A2A 状态 + `SuspendReason` 鉴别器 + Run 侧 RunStatus DFA。 |
| StateStore 持久化 Snapshot + Version | **MODIFY** | 接受抽象。约束：写操作必须走 `RunRepository.updateIfNotTerminal(...)` 原子 CAS（rc39 起为抽象方法，per ADR-0118）；version 字段即乐观锁字段。封堵 F-nonatomic-run-status-write 第 5 次复发。 |
| Session 跨节点恢复语义 | **MODIFY** | 接受为 L2 议题。L1 声明 Boundary Contract：恢复必须再校验 `(request.tenantId == Session.tenantId)`（Rule R-J.b）。 |
| §3.2 ER 模型字段（sessionId, taskId, metadata, version） | **REJECT — P0** | **`tenantId` 缺失**。违反 Rule R-C.2.a（Run record 强制 `tenantId` + `Objects.requireNonNull`）+ Rule R-J.a（含 tenant_id 表必须 RLS）+ 原则 P-J（存储引擎层租户隔离）。`tenantId` 必须作为 Session / Task / LifecycleState / StateStore 的一级字段 — 已在 `Task.java`（第 31 行）/`Session.java`（第 27 行）/`Run.java`（第 25 行）核实存在。 |

### 4.3 内部事件队列层（PR #71 §3.3）— 主要重设计

| PR #71 声明 | 判定 | 依据 |
|---|---|---|
| EventProducer / EventConsumer 解耦入口线程与执行线程 | **ACCEPT** | 反应式 + 背压模式，符合 Rule R-G。标准 Outbox / Inbox 模式。 |
| **单层队列 + 3 种存储模式（in-memory / 半持久 / 持久）** | **REJECT — P0** | **正面冲突 Rule R-E 三轨物理隔离**：`bus-channels.yaml` 声明 `control`（高优先级、out-of-band）/ `data`（in-band、重负载）/ `rhythm`（心跳 / liveness）为**物理隔离通道**，`physical_channel:` ID 各异。PR #71 把物理隔离（通道）与持久化等级（存储模式）混淆，这是正交两轴。必须重设计为：内部事件按 **intent** 路由（cancel/resume → control；payload → data；heartbeat/tick → rhythm），且**每通道独立**声明其持久化等级（W0/W1 in-memory stub，W2+ 各通道独立持久化后端，per Rule R-E）。L1 必须把 `bus-channels.yaml` 显式作为绑定 manifest。 |
| TaskEvent 字段（idempotency_key, type, payload） | **MODIFY** | 接受事件模型形状。约束：`idempotency_key` 必须由 Access Layer 在 `IngressEnvelope` 阶段生成并贯穿全链，对齐 ADR-0057（idempotency）。 |
| Consumer dedup + dead-letter | **ACCEPT** | 标准微服务范式；与 Outbox/Inbox + Rule R-E 通道级重试一致。 |

### 4.4 Task-Centric 状态控制层（PR #71 §3.4）— 安全硬门

| PR #71 声明 | 判定 | 依据 |
|---|---|---|
| Task 为调度核心（非 Runtime 调用栈） | **ACCEPT** | 与 Rule R-M（引擎契约）+ Rule R-H（无 Thread.sleep，声明式挂起）一致。 |
| Interrupt 类型：INPUT_REQUIRED / TOOL_EXECUTION / COLLABORATION / SAFETY_CHECK | **MODIFY** | 接受 4 类概念。按 §3 词汇表映射：INPUT_REQUIRED → `Task.A2aState.INPUT_REQUIRED`；TOOL_EXECUTION → `HookPoint.before_tool / after_tool`；COLLABORATION → `SuspendSignal.forClientCallback(...)` 经 `S2cCallbackEnvelope`；SAFETY_CHECK → `SuspendReason.SafetyCheck`（在 `engine-envelope.v1.yaml` 注册新值）。 |
| **§3.4.1 状态机未建模 cancel 重鉴权 + cancel 竞态** | **REJECT — P0** | 违反 Rule R-J.b（cancel 再校验 `(request.tenantId == Run.tenantId)`；跨租户 → 404 at W0；终态→终态 → 200；非法跃迁 → 409）。防御 F-nonatomic-run-status-write（4 次复发：rc35 / rc36 / rc38 / rc39）。状态机图必须显式标注：(a) Cancelled 入口守卫 `(tenantId == Run.tenantId)`；(b) 终态→终态 200；(c) 非法跃迁 409；(d) 所有写操作经 `RunRepository.updateIfNotTerminal(...)` 原子 CAS — 在图注中引用该抽象方法名。 |
| DualTrackRouter + Fast-Path / Slow-Path | **MODIFY** | 概念接受；语义由 ADR-0139（本 wave）**严格窄化**：Fast-Path = 同进程反应式同步 + 元数据持久化（不跳过 tenantId / RLS）；Slow-Path = 持久反应式 + SuspendSignal + ResumeDispatcher。两条路径都不得违反 Rule R-G（反应式）/ Rule R-H（无 sleep）/ Rule R-J.a（tenant_id 表 RLS）。PR #71 的 "no mandatory persistence" 措辞改写为 "no mandatory checkpoint/snapshot — metadata persistence remains mandatory under RLS"。 |
| Middleware 调用收口在 Task-Centric Control Layer | **ACCEPT** | 等价 Rule R-M.c（RuntimeMiddleware 经 HookPoint 统一治理）。 |
| ResumeDispatcher 触发 Resume（非 Runtime 自驱） | **ACCEPT** | 与 Rule R-H + `SuspendSignal.forClientCallback(...)` checked-exception 路径一致。 |

### 4.5 引擎适配层（PR #71 §3.5）

| PR #71 声明 | 判定 | 依据 |
|---|---|---|
| 5 子组件：Runtime SPI / Framework Adapter / Context Translator / Shadow Tool Interceptor / Result Normalizer | **ACCEPT** | 学术词汇丰富 L1 表达力。ADR-0136（本 wave）按 §3 词汇表将每个子组件映射到一个或多个已落地 SPI。 |
| Shadow Tool 拦截 → TOOL_EXECUTION InterruptSignal | **ACCEPT** | 等价 Rule R-M.c hook 链 + `SuspendSignal` checked 路径。 |
| Engine Adapter 不实现 Runtime 自身 | **ACCEPT** | 符合 Rule R-M.a/.b（所有 dispatch 经 `EngineRegistry.resolve(envelope)`）。 |
| §8.2 Non-Goals（不在 agent-service 内实现 Workflow / ReAct / Memory / Sandbox / MCP / API） | **ACCEPT** | 与原则 P-I（5 平面拓扑）+ Rule R-I（部署平面 manifest）对齐。 |

### 4.6 跨层条款

| PR #71 声明 | 判定 | 依据 |
|---|---|---|
| 4 种 Mermaid 图（flowchart / ER / sequence / stateDiagram-v2） | **ACCEPT** | GitHub 原生渲染；表达力足够。Waves 2-4 沿用。 |
| F-01..F-22 特性目录 | **MODIFY** | 接受作为 L2 backlog。每行必须补 `authority:` 列引用 ADR-NNNN 或 Rule X（Rule M-2.b 设计稿 ADR 锚定精神）。无 authority 锚定的特性 ID 不接受。 |
| 评审稿置于 `docs/logs/reviews/`（front-matter 可选） | **ACCEPT** | 符合 `docs/governance/logs-folder-policy.md`。本 Wave 1 沿用。 |
| 文件名 `xiaoming-*` 占位符 | **MODIFY** | 改为主题化 slug `agent-service-l1-4plus1-rewrite-wave-N`。 |
| 双语（zh-CN + en-US）输出 | **ACCEPT** | 沿用。英文先于中文（CLAUDE.md "translate to English before any model call"）；中文兄弟稿做结构平价。 |
| 验证未声明 WSL 调用 | **MODIFY** | PR body Verification 块必须显式声明 WSL/Linux 调用（Rule G-7 + 用户 standing feedback `feedback_linux_first_dev.md`）。 |

## 5. 红线（任何 wave 不可突破）

§4 中 4 条 P0 拒绝项不得在任何 wave 中放行。每 wave PR 关闭检查时核验：

1. **不存在无 tenantId 的数据模型。** `tenantId` 是 Run / Task / Session / StateStore 的一级字段。（Rule R-C.2.a + R-J.a + P-J）
2. **不存在不建模 cancel 重鉴权 + 原子 CAS 的状态机。** Cancel 再校验 (tenantId == Run.tenantId)；写操作经 `RunRepository.updateIfNotTerminal(...)`。（Rule R-J.b + F-nonatomic-run-status-write 防御）
3. **不存在单层 + 模式持久化的内部队列。** 队列按 Rule R-E 拆分为 `control` / `data` / `rhythm` 物理通道。
4. **不存在 Fast-Path 暗示跳过 tenantId / RLS / 反应式 / SuspendSignal 的措辞。**（Rule R-G + R-H + R-J.a）

任一 wave PR 触犯任一条 → `reject and re-spin`。

## 6. ADR 草案清单（本 Wave 1 产出）

本 wave 草案 4 份 ADR（Wave 1 提交 proposed，Wave 5 升 accepted）：

| ADR | 标题 | 触动 Rule |
|---|---|---|
| ADR-0136 | Vocabulary Reconciliation: PR 71 "Task" ≡ existing platform Task entity (not Run alias) | R-C.2, G-1.1, G-3（仅文档级） |
| ADR-0137 | SuspendSignal Canonical; InterruptSignal / InterruptReason are L1 Glossary Synonyms (per ADR-0100 §rejected-framings) | R-M.d, R-H（仅文档级） |
| ADR-0138 | Agent Service 5-Layer L1 Ratification (PR 71 layers ↔ ADR-0100 components + Run≤Task≤Session≤Memory) | G-1, G-1.1, R-D |
| ADR-0139 | Fast-Path / Slow-Path Narrowed Semantics (reactive-only, tenantId+RLS preserved, metadata persistence mandatory) | R-G, R-H, R-J.a, R-F |

草案随本评审落到 `docs/adr/0136-*.yaml` … `docs/adr/0139-*.yaml`（`status: proposed`）。Wave 5 在 Rule + contract-catalog 级联后升 `status: accepted`。

## 7. 新增 / 更新缺陷家族（G-B 输出）

按 Per-Wave 验收标准，本 wave 期间每个发现按 `docs/governance/recurring-defect-families.yaml`（16 个现有 family）分类。先查重再新增。

### 7.1 现有 family 扩展（追加本 wave 复发记录）

| Family | 本 wave 复发记录 | 表面 |
|---|---|---|
| **F-l1-architecture-grounding-gap** | PR #71 §3.2 ER 模型缺 tenantId；§3.4.1 状态图缺安全 / 原子性转换 — 均为 L1 设计 grounding gap | `docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.{en,zh}.md` |
| **F-cross-authority-agreement** | PR #71 §3.3 "Internal Event Queue" 与 `bus-channels.yaml` 不一致；§3.1.2 "remote interrupt" 与 `s2c-callback.v1.yaml` 不一致；§3.5 组件名与 contract-catalog SPI 行不一致 | PR #71 + `bus-channels.yaml` + `s2c-callback.v1.yaml` + `contract-catalog.md` |
| **F-terminal-verb-overclaim** | PR #71 §3.4.1 状态机箭头标签暗示已发布的状态跃迁，而实际对应 W2 deferred 行为（cross-agent collaboration suspension、safety-check rejection）— 使用 "transitions to" / "is cancelled" 现在时表述 W2-deferred 子句（Rule R-K.c / R-L.b / R-M.d.b/.d.c） | PR #71 §3.4.1 |

### 7.2 本 wave 注册的新 family

去重后注册 4 个新 family（其他 PR #71 候选 family 折叠为 7.1 现有扩展）：

| Family ID | 标题 | 根因 | 表面 | 首条 prevention |
|---|---|---|---|---|
| **F-design-artifact-omits-tenant-spine** | 设计稿省略 `tenantId` 一级字段 | L1/L2 设计 surface 上的 Mermaid ER / 状态图块体省略 Run / Task / Session / StateStore 的 `tenantId` 一级字段，把租户范畴埋进 `metadata` 不透明字符串或完全省略。下游实现继承这个设计缺口，在编码时违反 Rule R-C.2.a + R-J.a。本 family 是实现侧 F-nonatomic-run-status-write 的 L1 设计孪生 — 根因都是"租户不变量被当作运行时关注，而非设计关注"。 | docs/logs/reviews/*.md, docs/L2/*.md, agent-*/ARCHITECTURE.md, docs/contracts/*.yaml | 本 Wave 1 ADR-0136 + ADR-0138 显式 "first-class tenantId" 红线 + Wave 2 重写稿 ER 块 |
| **F-design-doc-violates-three-track-bus** | 设计稿提出绕过 Rule R-E 三轨通道的队列 / 事件总线抽象 | 设计稿引入"内部事件队列"或"消息总线"抽象，自带持久化轴（in-mem / semi-persist / persist），不绑定到规范化的 `bus-channels.yaml` 三通道（control / data / rhythm）。把**物理隔离**（通道）与**持久化等级**（通道后端选型）当成一轴，使下游实现跳过三轨保证。 | docs/logs/reviews/*.md, docs/L2/*.md, docs/contracts/*.yaml | 本 Wave 1 ADR-0138 §3.3 绑定条款 + Wave 3 Physical View 三轨绑定图 |
| **F-design-doc-language-bypasses-invariant** | 设计稿措辞暗示绕开反应式 / RLS / 无 sleep 不变量 | 设计稿用随意语言（"no mandatory persistence"、"fast-path skips checkpoint"、"lightweight synchronous"、"memory-only path"）— 这些被实现者读到时，会被解读为允许绕开 Rule R-G（反应式 I/O）/ Rule R-H（无 Thread.sleep）/ Rule R-J.a（tenant_id 表 RLS）/ Rule R-C.2（RunRepository.updateIfNotTerminal CAS）。语言是上游因，代码缺陷是下游果。 | docs/logs/reviews/*.md, docs/L2/*.md, docs/adr/*.yaml | 本 Wave 1 ADR-0139（Fast-Path/Slow-Path 窄化）显式红线 + Wave 2 Logical View 窄化散文 |
| **F-placeholder-leaks-into-active-corpus** | 占位符泄漏到 active 文档语料 | 匿名占位符（`xiaoming`、`wanshoulu`、`foo`、`bar`、`TBD`、`TODO-template`）泄漏到 active 设计 surface — 文件 slug、散文、代码块作者标签 — 评审前没有清理。slug 尤其变成稳定 URL（PR 评审链接、归档链接），事后清理代价高。 | docs/logs/reviews/*.md, docs/L2/*.md, docs/contracts/*.yaml | 本 Wave 1 主题化 slug 重命名 + Wave 5 gate-rule 词袋 grep |

### 7.3 yaml + md 同步（Rule G-9.b/c 纪律）

本 Wave 1 发布 `docs/governance/recurring-defect-families.yaml` 内容差异：
- 4 个新 family 追加 `families:` 列表，各含 Rule G-9.a 要求的 9 字段。
- 3 个现有 family 的 `occurrences:` 数组追加 `rc53-wave-1-agent-service-l1-4plus1-rewrite`，`last_observed_rc:` 推进。
- `last_updated:` 推进到 `2026-05-26`。
- `schema_version:` 保持 `1`。
- `docs/governance/recurring-defect-families.md` 同步匹配的 family-id 标题 + `cleanup_status:` 平价（Rule G-9.c）。

## 8. 兄弟横扫清单（G-C 输出）

按 Per-Wave 验收标准 G-C，本 wave 触动的每个 family 必须在 active 语料上做横扫。横扫指纹见 plan 文件 `D:\.claude\plans\noble-stargazing-cookie.md` §G-C。**PR #71 本身在范围内** — 虽然分支未合并，本 wave 的工作就是评议该分支，故 PR #71 内容通过 `git show pr71-review:<path>` 虚拟在范围。结果：每个 family 报告 ≥1 hit 或 negative-confirmation 行（Rule G-E 非空守卫）。

### Sweep 方法 legend

| 记号 | 含义 |
|---|---|
| **active-local** | 文件在当前分支 checkout 上存在 |
| **virtual-via-pr71** | 文件仅在 `pr71-review` ref 上存在；本 wave 评议该 PR 因此在范围 |
| **historical-archive** | `docs/archive/` 下文件 — 按 logs-folder-policy 豁免 |
| **prohibition-doc** | Rule 卡 / 契约 / deferred 文档；关键字以 BAN 目标出现而非违规 |

### 8.1 F-design-artifact-omits-tenant-spine — 横扫

指纹：设计 surface 上 Mermaid `erDiagram`（或等价 `## *Data Model` 字段表）块缺 `tenantId` / `tenant_id` 字段。

Sweep 命令：`Grep "erDiagram" --glob "*.md"`（仓库全量）；与 `pr71-review` ref 交叉核对。

| Hit # | file:line | Sweep 方法 | 类型 | 本 wave 处置 |
|---|---|---|---|---|
| 1 | `pr71-review:docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.en.md` §3.2 ER 块 | virtual-via-pr71 | 原 finding | 本 Wave 1 ADR-0138 + Wave 2 ER 块修复 |
| 2 | `pr71-review:docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.md` §3.2 ER 块 | virtual-via-pr71 | 原 finding | 同上（Wave 2 cn 兄弟） |

Negative confirmation：`Grep "erDiagram" --glob "*.md"` 跨 active-local 分支 0 命中（本 wave 评审稿在 §8 指纹散文中提到该 token — 已核实）。模式在 active-local `.md` 0 命中。PR #71 是唯一携带 `erDiagram` 块的 artifact，仅在未合并的 `pr71-review` ref 上。

active-local Java 源中的兄弟：`Task.java`（第 31 行）+ `Session.java`（第 27 行）+ `Run.java`（第 25 行）均声明 `String tenantId` 并 `Objects.requireNonNull` — Rule R-C.2.a 合规。active 代码中无结构性 sibling hit。

### 8.2 F-design-doc-shipped-vocab-divergence（折叠到 F-cross-authority-agreement）— 横扫

指纹：PR #71 风格学术词汇（`DualTrackRouter`、`ShadowToolInterceptor`、`InterruptSignal`）出现在 active 设计 surface 而未做术语表映射到已落地 SPI 词汇。

Sweep 命令：`Grep "\\bDualTrackRouter\\b|\\bShadowToolInterceptor\\b|\\bInterruptSignal\\b" --glob "docs/logs/reviews/2026-05-2*.md"`。

Sweep 结果：**9 个 active-local 文件命中** + PR #71 虚拟引用。

| Hit # | file | Sweep 方法 | 类型 | 本 wave 处置 |
|---|---|---|---|---|
| 1 | `pr71-review:docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.{en,md}` | virtual-via-pr71 | 原 finding | 本 Wave 1 §3 词汇表修复 |
| 2 | `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.en.md` | active-local | 参考模板 — 学术词汇用于设计语言，但 ADR-0100 + SuspendSignal 引用正确 | 无操作；ADR-0137 为后续使用锚定术语映射 |
| 3 | `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.cn.md` | active-local | #2 中文兄弟 | 同上 |
| 4 | `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal-response.en.md` | active-local | #2 的回复；同词汇 | 同上 |
| 5 | `docs/logs/reviews/2026-05-22-agent-execution-engine-friction-resolution.{en,cn}.md` | active-local | 引擎模块兄弟评审；同义使用 InterruptSignal | 无操作；同术语锚 |
| 6 | `docs/logs/reviews/2026-05-23-rc34-adversarial-followup-findings.md` | active-local | rc34 评审；用学术词汇 | 同上 |
| 7 | `docs/logs/reviews/2026-05-24-l0-rc38-post-audit-architecture-review.en.md` | active-local | rc38 评审；用学术词汇 | 同上 |
| 8 | `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.{en,cn}.md`（本 wave） | active-local | 本 wave 主创术语表 | n/a |

Negative confirmation：`docs/L2/*.md` 目录尚不存在（0 文件）；模式在该子树不适用。

净结果：7 个 active-local 文件携带学术词汇，Wave 5 ADR-0137 接受后均被锚定。无需对个别文件做修正；ADR-0137 是结构性 fix。

### 8.3 F-state-machine-doc-omits-security-atomicity — 横扫

指纹：Mermaid `stateDiagram-v2` 块（含散文形式状态图）包含 `Canceled`/`Cancelled` 转换，同段无 `re-auth` / `tenantId` / `updateIfNotTerminal` / `CAS` 标注。

Sweep 命令：`Grep "stateDiagram-v2" --glob "*.md"`。

Sweep 结果：**2 个 active-local 文件命中**（本 wave 评审稿 — 该 token 出现在 §8 指纹散文中，并非真实 Mermaid 块）。

| Hit # | file | Sweep 方法 | 类型 | 本 wave 处置 |
|---|---|---|---|---|
| 1 | `pr71-review:docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.{en,md}` §3.4.1 `stateDiagram-v2` 块 — Canceled 转换**无**原子性 / 重鉴权标注 | virtual-via-pr71 | 原 finding | Wave 2 Logical View 状态机 + Wave 3 Process View cancel 时序图修复（两处都引用 `RunRepository.updateIfNotTerminal` + `(tenantId == Run.tenantId)` 守卫） |
| 2 | `agent-service/ARCHITECTURE.md`（RunStatus DFA 以散文形式落地，非 Mermaid；cancel-race-aware 叙事在 §2.B `runtime / runs`） | active-local | 已 grounded — 显式引用 `RunRepository.updateIfNotTerminal` + 原子 CAS 模式 | 无回退；Wave 2/3 Mermaid 块需保持与此散文一致 |

Negative confirmation：`Grep "stateDiagram-v2"` 跨 active-local 返回 2 文件 — 都是本 wave 评审稿（token 在 §8 指纹散文中出现而非真实 Mermaid 块）。2026-05-22 参考模板**不**使用 Mermaid 状态图 — negative grep 已核实。无静默跳过。

### 8.4 F-design-doc-violates-three-track-bus — 横扫

指纹：设计稿引用 `event queue` / `internal queue` / `message bus` / `task queue` 但同段未提 `control` + `data` + `rhythm` 通道，也未引用 `bus-channels.yaml`。

Sweep 命令：`Grep -i "internal event queue|internal queue|message bus|task queue" --glob "*.md"`。

Sweep 结果：**7 个 active-local 文件命中**，4 个是真实兄弟。

| Hit # | file | Sweep 方法 | 类型 | 本 wave 处置 |
|---|---|---|---|---|
| 1 | `pr71-review:docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.{en,md}` §3.3 — 单层 + 3 模式 | virtual-via-pr71 | 原 finding | §4.3 重写 + Wave 2 Logical + Wave 3 Physical 修复 |
| 2 | `docs/spring-ai-ascend-architecture-whitepaper-en.md` | active-local | **false-positive — 已正确绑定**：白皮书 §5.2 正是三轨概念的源头（`Three-Track Isolation of Physical Channels: Anti-Congestion System for High-Priority Control, Heavy Data, and Timing Heartbeats`） | 无操作；白皮书是规范权威 |
| 3 | `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.{en,cn}.md` | active-local | 引用 "internal high-throughput event/task queue" + "Reactor Sinks" + 半持久化后端 — 同段未绑三轨通道 | Out-of-scope 兄弟（§9 第 1 行）；Wave 5 加同段三轨绑定引用 OR 标历史 |
| 4 | `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal-response.en.md` | active-local | #3 的回复 — 同用法 | Out-of-scope 兄弟（§9 第 1 行） |
| 5 | `docs/archive/spring-ai-fin/systematic-architecture-remediation-plan-2026-05-09-cycle-9.en.md` | historical-archive | `docs/archive/` 下 — 按 logs-folder-policy 豁免 | 无操作 |

Negative confirmation：PR #71 是唯一携带单层 + 3 模式构造的 artifact（结构性缺陷）；其他命中以散文形式正确提及队列概念。1 个推后兄弟（2026-05-22 expansion proposal）在 §9 处理。

### 8.5 F-design-doc-language-bypasses-invariant — 横扫

指纹：风险措辞 grep（`no mandatory persistence`、`skip persistence`、`bypass.*reactive`、`synchronous.*long.*running`、`lightweight.*skip`、`memory-only path`、`no checkpoint`）。

Sweep 命令：`Grep "no mandatory persistence|memory-only path|skip persistence|skip checkpoint|bypass.*reactive|synchronous.*long.*running|lightweight.*skip" --glob "*.md"`。

Sweep 结果：**3 个 active-local 文件命中** + PR #71 虚拟。

| Hit # | file | Sweep 方法 | 类型 | 本 wave 处置 |
|---|---|---|---|---|
| 1 | `pr71-review:docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.{en,md}` §3.4.3 — "Avoid or minimize cross-thread dispatch, no mandatory persistence, local synchronous resume" | virtual-via-pr71 | 原 finding | ADR-0139 窄化 Fast-Path 语义 + §4.4 重写 |
| 2 | `docs/logs/reviews/2026-05-13-{wanshoulu}-wave-N-request.md` 第 243 行 — "W0 only supports **synchronous return; long-running** agents cannot provide real-time feedback" | active-local | **regex false-positive** — 散文描述 W0 限制（同步返回挡 long-running agent），并非设计指示绕开不变量 | 无操作；regex false-positive，语义无害 |
| 3 | `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.{en,cn}.md`（本 wave） | active-local | 本 wave 主创指纹散文 — token 在 §8.5 指纹描述中出现，非违规 | n/a |

另外，2026-05-22 参考模板的 "compact edge deployment" / "stateless and semi-persistence" 措辞是部分兄弟但未触发严格 regex；在 §9 第 2 行跟踪（推后跟进）以显式按 ADR-0139 加 invariant-preservation pin。

`Thread.sleep` specifically 的 negative confirmation：`Grep "Thread\\.sleep" --glob "*.md"` 返回 18 文件。**全部 18 个均为 prohibition-doc surface**（Rule 卡 R-G/R-H/R-K/R-M/R-F + 原则 P-H + governance contracts + CLAUDE-deferred + 3 历史评审回复 + v6-rationale + 本 wave 评审稿）— 关键字以 BAN 目标出现而非违规。模式在 active 散文中 0 违规命中；18 prohibition-doc 提及。

### 8.6 F-design-doc-orphan-from-authority — 横扫

指纹：`docs/logs/reviews/` 或 `docs/L2/` 下 `.md` 中 `ADR-[0-9]{4}` 引用次数为 0 且 `Rule [DGRM]-` 引用次数为 0。

| Hit # | file | Sweep 方法 | 类型 | 本 wave 处置 |
|---|---|---|---|---|
| 1 | `pr71-review:docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.{en,md}` — 525 行 0 ADR ref + 0 Rule ref | virtual-via-pr71 | 原 finding | §3 词汇表 + ADR 草案引用 ADR-0100 / ADR-0136..0139 / Rule R-C.2 / R-M / R-J / R-E / R-G / R-H 修复 |

Negative confirmation 跨 active-local `docs/logs/reviews/2026-05-2*.md`：所有评审文件至少引用 1 ADR 或 Rule（采样 2026-05-22 / 2026-05-23 / 2026-05-24 / 2026-05-26-l0-rc52 批次 — 每个都在规范位置携带多条 ADR 与 Rule 引用）。模式 `ADR-[0-9]{4}|Rule [DGRM]-` 在 active-local 评审文件 100% 命中 ≥1；PR #71 2 文件（virtual-via-pr71）是唯一孤儿。

### 8.7 F-placeholder-leaks-into-active-corpus — 横扫

指纹：active docs + Java source 上对词袋 `\\bxiaoming\\b`、`\\bwanshoulu\\b`、`\\bfoo\\b`、`\\bbar\\b`、`\\bTBD\\b`、`TODO:` 的 grep（排除 `docs/archive/` + `docs/CLAUDE-locked/` + Git 历史路径）。

Sweep 命令：`Grep "\\bxiaoming\\b"` 与 `Grep "\\bwanshoulu\\b"`。

| Hit # | file | Sweep 方法 | 类型 | 本 wave 处置 |
|---|---|---|---|---|
| 1 | `pr71-review:docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.{en,md}` — slug 中 `xiaoming` | virtual-via-pr71 | 原 finding（仅文件 slug；in-file 内容除 front-matter 作者标签外无 `xiaoming` 占位符） | 本 Wave 1 用主题化 slug `agent-service-l1-4plus1-rewrite-wave-1`。PR #71 合并后由 Wave 8 处置；历史快照标记保留原 slug 评审记录。 |
| 2 | `docs/logs/reviews/2026-05-13-{wanshoulu}-wave-N-request.md` — slug 中 `wanshoulu` 占位符（含花括号） | active-local | 已存在的占位符泄漏（active 语料） | Out-of-scope 跟进（§9 第 3 行）；Wave 5 改主题化 slug + 按 logs-folder-policy 加历史快照标记 |
| 3 | `docs/adr/0136-vocabulary-reconciliation-pr71-task-vs-run.yaml`（`context:` 块中引用 PR #71 的 `xiaoming` 文件名） | active-local | ADR 正文中的显式引用 | 无操作 — 引用不算占位符泄漏 |
| 4 | `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.{en,cn}.md`（本 wave — §8.7 指纹散文引用 PR #71 文件名） | active-local | 显式引用 | 无操作 |

Negative confirmation：`Grep "\\bTBD\\b|TODO:" docs/governance/ docs/contracts/ docs/adr/ agent-*/src/main/java/` 在 **active** governance / contract / ADR / Java-source surface 0 命中。`\bxiaoming\b` token 在 active-local 共 3 条路径（本 wave 评审稿 + ADR-0136 引用）；`\bwanshoulu\b` token 在 active-local 1 条路径（`2026-05-13` 评审文件 slug）— 真实泄漏。

## 9. Out-of-Scope 兄弟（G-D 输出）

按 Per-Wave 验收标准 G-D，与本 wave 主题不同的兄弟登记在 family `open_residual:` + `docs/CLAUDE-deferred.md` 跟进清单。**无静默忽略**。

| Family | 推后的兄弟 | 处理 wave |
|---|---|---|
| F-design-doc-violates-three-track-bus | `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.{en,cn}.md` "internal queue" 散文未含同段三轨绑定引用 | Wave 5（Rule + contract-catalog 级联）追加一段说明 OR 标历史 |
| F-design-doc-language-bypasses-invariant | `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.{en,cn}.md` "compact edge deployment" Fast-Path 措辞需显式 Rule R-G/R-J pin（per ADR-0139） | Wave 5 同段附加 |
| F-placeholder-leaks-into-active-corpus | `docs/logs/reviews/2026-05-13-{wanshoulu}-wave-N-request.md` 花括号占位符 slug | Wave 5 — 改为主题化 slug，按 logs-folder-policy 加历史快照标记 |

每条推后行加入本 wave families.yaml 内容差异中对应 family 的 `open_residual:`。

## 10. Wave 路线 — 6 个 Wave（校准后）

原 8-wave 计划在 ADR-0100 校准后压缩为 6 wave（见 §2.3）。

| Wave | 标题 | 主要交付 |
|---|---|---|
| 1（本 wave） | 评审稿 + 拒绝清单 + ADR 草案 + family 注册 | 本文件 + 4 份 ADR 草案 + families.yaml/md 内容差异 |
| 2 | 4+1 上半 — Scenarios + Logical | 评审稿追加 §Scenarios + §Logical View（5 层 Mermaid + tenantId-first ER + cancel-race-aware 状态机 + 术语表） |
| 3 | 4+1 下半 — Process + Physical | 追加 §Process View（5 时序图）+ §Physical View（5 平面映射 + RLS + 三轨绑定 + sandbox 隔离） |
| 4 | 4+1 收尾 — Development + SPI 附录 + L2 边界契约 | 追加 §Development View（包树，满足 Rule G-1.1.a）+ §SPI 附录（四向校验，满足 G-1.1.b）+ §L2 边界契约（满足 G-1.1.c） |
| 5 | Rule + contract-catalog + module-metadata + DFX 级联 | 更新 Rule 卡（R-C.2 / R-M / R-J / R-H / R-D / R-E / R-K / G-1.1 / G-3 词汇不变；仅按需更新引用）+ contract-catalog SPI 行 + module-metadata + dfx；ADR-0136..0139 升 accepted |
| 6 | Javadoc 词汇表注入 | 为 Run.java + Task.java + Session.java + SuspendSignal.java + SuspendReason.java Javadoc 追加 Vocabulary Glossary 段；无方法签名 / 字段 / 包 / DB schema 改动 |
| （7 删除） | — | — |
| 8 | ARCHITECTURE.md 迁入 + release note + 关闭 | 把 Wave 2-4 4+1 内容迁入 `agent-service/ARCHITECTURE.md`；为本 Wave 1 文件加历史快照标记；发布 release note + families.yaml 内容差异 + R-B 4 pillar metrics 刷新 |

## 11. Per-Wave 验收标准（提醒）

每个 wave PR 必须通过 plan 文件 `D:\.claude\plans\noble-stargazing-cookie.md` §"Per-Wave 验收标准" 中 6 个 G-* gate：

- **G-A** 直接修复（本 wave 点名 finding 关闭）
- **G-B** 持续分类（新发现登记到现有或新 family）
- **G-C** 持续兄弟横扫（每个触动 family 在 active 语料上扫描）
- **G-D** 持续修复（in-scope 兄弟修复，out-of-scope 兄弟推后到 open_residual + CLAUDE-deferred + 后续 wave 跟进清单）
- **G-E** 非空守卫（G-B + G-C 输出不得静默为空；需要时给 negative-confirmation 行）
- **G-F** 文档化（每 wave 评审稿末尾 5 个固定章节：Direct-Fix Log、新/扩展 Family、Sibling-Sweep Inventory、Out-of-Scope Deferred、Verification）

## 12. Verification

仅在 WSL/Linux（Rule G-7）。

```bash
# A. Gate 自检（read-only — 本 wave 仅文档）
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && bash gate/check_parallel.sh 2>&1 | tee /tmp/gate-wave-1.log'

# B. Maven verify（本 wave 无 Java 改动；但为不变量跑一次）
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && ./mvnw -Pquality verify 2>&1 | tee /tmp/maven-wave-1.log'

# C. families.yaml 内容差异（Rule G-9.b）
git diff HEAD~1 -- docs/governance/recurring-defect-families.yaml

# D. ADR yaml schema
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && for f in docs/adr/013{6,7,8,9}-*.yaml; do python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" "$f"; done'

# E. 架构图重建并一致（本 wave 不动 Maven；纯读取检查）
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && python3 gate/build_architecture_graph.py --check --no-write'
```

PR body 检查清单（Rule G-7 + standing user feedback）：
- [ ] WSL 调用已声明（见上面命令）
- [ ] families.yaml 内容差异（Rule G-9.b）
- [ ] families.md 同步（Rule G-9.c）
- [ ] G-B family-id 清单：F-l1-architecture-grounding-gap（扩展）+ F-cross-authority-agreement（扩展）+ F-terminal-verb-overclaim（扩展）+ 4 新（F-design-artifact-omits-tenant-spine + F-design-doc-violates-three-track-bus + F-design-doc-language-bypasses-invariant + F-placeholder-leaks-into-active-corpus）
- [ ] G-C 横扫命中合计：见 §8（7 family × 计数）
- [ ] G-E 非空守卫行：§8 中 6 条显式 negative-confirmation 行

## 13. 小结

PR #71 通过人类专家共识捕捉到了合法的 5 层 L1 脚手架。技术内容存在 4 处 P0 结构性缺陷，映射到 7 个缺陷家族（3 现有扩展 + 4 新）。对 ADR-0100 重新校准后，整体重命名叙事被推翻 — Run、Task、Session、SuspendSignal 都是规范化的已落地类型，语义各异；L1 重写词汇通过术语表对齐它们，而非通过代码改名。6-wave 计划批准脚手架，修缺陷，按设计防止复发（4 新 family + 2 新 ADR + 2 窄化 ADR），并在 Wave 8 把成果迁入 `agent-service/ARCHITECTURE.md`。

---

# Wave 2 — Scenarios View + Logical View

> 由 Wave 2/6 追加（rc53-wave-2；分支 `rc53/agent-service-l1-4plus1-rewrite`）。
> 按 Rule G-1.a（Layered 4+1 Discipline）以下视图分别携带 `view: scenarios` 与 `view: logical`；文件顶部 front-matter 的 `view:` 列表已经涵盖两者。

## 14. Scenarios 视图

本节枚举 L1 设计必须支持的 **5 个规范化场景**。每个场景注明其经过的层、使用的契约 artifact、必须保持的故障模式边界条件。Scenarios 喂给 §15 Logical View（由哪些层实现）、Wave 3 §16 Process View（时序图）、Wave 3 §17 Physical View（部署拓扑 + RLS + 三轨绑定）。

### 14.1 S1 — 标准同步入口（Fast-Path 合格）

| 字段 | 值 |
|---|---|
| **Actor** | Web/App 客户端 → REST `POST /v1/runs`（或 gRPC 等价） |
| **经过的层** | Access Layer → Session & Task Manager → Task-Centric Control Layer → Engine Adapter Layer |
| **Run.mode** | `GRAPH`（确定性短链）或低估算步数的 `AGENT_LOOP` |
| **路径鉴别** | DualTrackRouter predicate（按 ADR-0112 的 `SlowTrackJudge`）— Fast-Path 合格当且仅当：(i) 预估 wall-clock ≤ 5 s，(ii) 无外部 input 等待、无 S2C callback、无 A2A collaboration，(iii) 无预期 resume 到不同部署 |
| **持久化形态** | Run + Task 元数据记录在 create + 终态跃迁时按 RLS 持久化；**无中间 compute checkpoint**（按 ADR-0139 窄化 Fast-Path） |
| **触动契约** | `openapi-v1.yaml`、`engine-envelope.v1.yaml`、`ingress-envelope.v1.yaml`（若用异步入口） |
| **边界契约** | wall-clock ≤ Fast-Path 上限；若执行中超出，executor 抛 `SuspendSignal`，Run 通过 SUSPENDED 状态切到 Slow-Path |
| **故障模式** | (a) 跨租户请求：W0 按 Rule R-J.b 返回 404 not_found；(b) Idempotency-Key 冲突：按 ADR-0057 返回 409 idempotency_conflict 或 409 idempotency_body_drift；(c) Engine envelope schema 违规：`EngineMatchingException` → Run FAILED with reason `engine_mismatch`（按 Rule R-M.b） |

### 14.2 S2 — 含工具调用的长程 ReAct（Slow-Path）

| 字段 | 值 |
|---|---|
| **Actor** | Web/App 客户端请求多工具 agent run |
| **经过的层** | Access Layer → Session & Task Manager → Internal Event Queue（data + rhythm）→ Task-Centric Control Layer ↔ Engine Adapter Layer（带 HookPoint.before_tool / after_tool 的循环） |
| **Run.mode** | `AGENT_LOOP` |
| **路径鉴别** | DualTrackRouter 选 Slow-Path（多步或可能工具调用） |
| **持久化形态** | Run + Task 在 RLS 下持久化；**Checkpointer 在每个工具调用边界为中间状态打快照**（W2 Checkpointer SPI，按 ADR-0021）；从任意 checkpoint Resume — 通过 `RunRepository.updateIfNotTerminal(...)` CAS 把 `RunStatus.SUSPENDED → RUNNING` |
| **触动契约** | `engine-envelope.v1.yaml`、`engine-hooks.v1.yaml`、`model-invocation.v1.yaml`（按 ADR-0134 的 tool_call_loop 段）、`memory-store.v1.yaml` |
| **边界契约** | 每个工具调用被 `HookPoint.before_tool` + `HookPoint.after_tool` 包络；工具执行由 `RuntimeMiddleware` 治理（Rule R-M.c）；技能容量按 `skill-capacity.yaml`（Rule R-K） |
| **故障模式** | (a) 工具执行超时：Run 保持 RUNNING 直到工具返回或中间件抛 SuspendSignal；(b) 在不同部署上 Resume：从 Checkpointer 恢复状态；tenantId 按 Rule R-J.b 再校验（W2 R-J.b.d Resume 重鉴权 deferred） |

### 14.3 S3 — A2A 对等协作

| 字段 | 值 |
|---|---|
| **Actor** | Agent A（本实例）调 Agent B（peer 实例）做子任务委派；Agent A 的 Run 挂起直到 Agent B 返回 |
| **经过的层** | Access Layer（A2A Client outbound + 对端 A2A Server inbound）→ Task-Centric Control Layer 挂起 parent Run → Engine Adapter Layer 经三轨 `control` 通道用 `IngressEnvelope` dispatch 到 peer |
| **Run.mode** | parent: `GRAPH` 或 `AGENT_LOOP`；peer 上 child Run：独立（peer 自选） |
| **触动契约** | `a2a-envelope.v1.yaml`（W1 design_only；无 `a2a-java` SDK 运行时依赖，按 ADR-0100 §rejected-framing #1）、`ingress-envelope.v1.yaml`、`engine-envelope.v1.yaml` |
| **边界契约** | parent Run 挂起用 `SuspendSignal.forClientCallback(...)` checked 变体（ADR-0074）；peer 的 Run 用各自 RunRepository；关联通过 Run record 的 parentRunId + traceId 字段 |
| **故障模式** | (a) Peer 不可达：SuspendReason.AwaitClientCallback 超时；Run 跃迁 FAILED with `peer_unreachable`；(b) Peer 返回错误包络：parent Run resume 决定恢复（按 ADR-0118 retry 或通过 `RunRepository.updateIfNotTerminal(...)` CAS 跃迁 FAILED）；(c) 跨租户 peer call：在 A2A Server 侧拒绝（按 Rule R-I.1 + IngressGateway 鉴权，W3+ SDK 落地后） |

### 14.4 S4 — S2C 客户端回调（服务端挂起，向客户端要能力）

| 字段 | 值 |
|---|---|
| **Actor** | 服务端 Run 需要客户端能力（如用户确认、浏览器 cookie、本地文件访问）；Run 用 `SuspendSignal.forClientCallback(...)` 挂起，客户端通过 `POST /v1/runs/{runId}/resume`（W2 已发布）携带能力响应解析 |
| **经过的层** | Engine Adapter Layer（executor 抛）→ Task-Centric Control Layer（orchestrator 捕获、持久化 checkpoint、把 Run 跃迁到 SUSPENDED）→ 三轨 `control` 通道把 `S2cCallbackEnvelope` 发到客户端 → `data` 通道携带响应载荷（16 KiB inline 上限，按 Rule R-E）→ Resume |
| **Run.mode** | 继承自 parent execution |
| **触动契约** | `s2c-callback.v1.yaml`（runtime_enforced，按 Rule R-M.d）、`engine-envelope.v1.yaml`、`engine-hooks.v1.yaml`（before_suspension + before_resume） |
| **边界契约** | `RunStatus.RUNNING → SUSPENDED` 跃迁经 `RunRepository.updateIfNotTerminal(...)` 原子 CAS；SuspendReason = `AwaitClientCallback`；resume 时 `SuspendSignal.forClientCallback(...)` unwind，executor 用注入的能力响应（`resumePayload`）继续 |
| **故障模式** | (a) 客户端超时：SuspendReason.AwaitClientCallback 失效；Run 通过 CAS 跃迁 FAILED；(b) 技能容量耗尽（Rule R-K + `skill-capacity.yaml` 的 `s2c.client.callback` 行）：caller 用 SuspendReason.RateLimited 挂起（W2 deferred 按 Rule R-M.d.b）；(c) 响应包络 schema 不合法：对 `s2c-callback.v1.yaml#response` 校验失败；Run 跃迁 FAILED with `s2c_response_invalid` |

### 14.5 S5 — 执行中 Cancel（Cancel 重鉴权 + Cancel 竞态）

| 字段 | 值 |
|---|---|
| **Actor** | 客户端调 `POST /v1/runs/{runId}/cancel`；Run 可能处于 RUNNING、SUSPENDED 或已终态 |
| **经过的层** | Access Layer（RunController.cancel）→ Session & Task Manager（RunRepository load + tenant 守卫）→ Task-Centric Control Layer（RunStateMachine.validate 守护非法跃迁） |
| **持久化形态** | cancel 跃迁是**经 `RunRepository.updateIfNotTerminal(this.tenantId, this.runId, RunStatus.CANCELLED)` 的原子 CAS**（按 ADR-0118 为抽象方法）；其实现必须是带 `WHERE status NOT IN (CANCELLED, SUCCEEDED, FAILED, EXPIRED)` 子句的单条 SQL UPDATE 语句（或等价原子原语） |
| **鉴权** | RunController.cancel 再校验 `(request.tenantId == Run.tenantId)`；跨租户 W0 按 Rule R-J.b 收敛到 **404 not_found**（W1 按 ADR-0108 widen 到 403 tenant_mismatch + WARN audit，deferred） |
| **结果** | (a) Active → terminal（RUNNING/SUSPENDED → CANCELLED）：成功，返回 200；(b) 同状态终态（CANCELLED → cancel）：幂等，返回 200；(c) 不同终态（SUCCEEDED/FAILED/EXPIRED → cancel）：返回 409 illegal_state_transition；(d) cancel-vs-complete 并发竞态：CAS 的 WHERE 子句允许一个 writer 胜出；败者 re-read 看到 CAS 后的 Run row，按之返回 200（败给同租户 cancel）或 409（败给 complete/fail/expire） |
| **故障模式** | 4 次复发的 cancel-vs-complete 竞态（`F-nonatomic-run-status-write` rc35/rc36/rc38/rc39）**在此入口结构性关闭** — 通过抽象 `updateIfNotTerminal` 方法；任何 caller 都无法绕过。Run 状态上的任何新写路径都引入第 5 次复发风险 — 由 `RunRepository.updateIfNotTerminal` 抽象方法纪律守护。 |

## 15. Logical 视图

5 层 L1 逻辑分解（按 ADR-0138）。每层映射到 `agent-service/src/main/java/com/huawei/ascend/service/...` 下子包（Wave 4 发布规范的 Development View 树）。

### 15.1 5 层组件图

> Mermaid 图见 .en 版本 §15.1（结构性 artefact；保持中英一致）

**Layer 职责**：

1. **Access Layer** — 入站协议收口（HTTP/gRPC 经 openapi-v1.yaml；A2A 经 a2a-envelope.v1.yaml；MQ 经 ingress-envelope.v1.yaml）。执行租户绑定（`TenantContextFilter` + JWT 交叉校验，按 ADR-0040）、idempotency claim（按 ADR-0057）、不直接驱动 Runtime（Rule R-M.a）、不直接调 Middleware（Rule R-M.c）。

2. **Session & Task Manager** — 拥有 Run / Task / Session record 生命周期。RLS 下持久化（Rule R-J.a），通过 `RunRepository.updateIfNotTerminal(...)` 原子 CAS（Rule R-C.2.b + ADR-0118）。Session ↔ Task 1:N（按 ADR-0100）。

3. **Internal Event Queue（三轨总线本地化绑定层）** — Wave 4 新增子包。Producer/Consumer 按 intent 路由到 `bus-channels.yaml` 中声明的 `control` / `data` / `rhythm` 三轨（Rule R-E）。"in-memory / 半持久 / 持久"持久化等级是正交轴；通道选型是**隔离**轴。ADR-0138 §3 红线 c 禁止合并两轴。

4. **Task-Centric Control Layer** — Orchestrator + 状态机 + DualTrackRouter + RuntimeMiddleware 链。Runtime 直接调 Middleware 的位置转换成 `HookPoint` 事件（按 Rule R-M.c + engine-hooks.v1.yaml）并经中间件链 dispatch。SuspendSignal 处理（child-run + S2C callback 两种变体）住此处。

5. **Engine Adapter Layer** — `EngineRegistry.resolve(envelope)`（按 Rule R-M.a）。Graph（`SequentialGraphExecutor`）+ AgentLoop（`IterativeAgentLoopExecutor`）的 ExecutorAdapter 实现；LangChain / LlamaIndex 异构 adapter shell 在 W1 design_only，实现 defer。ChatAdvisor（ADR-0132）+ ContextProjector + PromptTemplate + StructuredOutputConverter 组合实现 PR #71 "Shadow Tool Interceptor" + "Context Translator" 概念。

### 15.2 ER 模型 — Run / Task / Session / LifecycleState（tenantId-first）

> 红线：以下每个实体必须把 `tenantId` 作为一级字段。已核实：`Run.java:25`、`Task.java:31`、`Session.java:27`。

> Mermaid erDiagram 见 .en 版本 §15.2。

**租户隔离强制**：
- 上表每张表都带 `tenant_id` 列，创建该表的 Flyway 迁移启用 RLS policy（Rule R-J.a）。
- `IdempotencyRecord` / `RUN` / `TASK` / `SESSION` 表都带 RLS；legacy `idempotency_dedup` 表在 `gate/rls-baseline-grandfathered.txt` 中 grandfathered（W2 retrofit 待办，Rule R-J.a.b deferred）。

### 15.3 RunStatus 状态机（cancel-race-aware + CAS 标注）

> Mermaid stateDiagram-v2 见 .en 版本 §15.3。

**cancel-vs-complete 竞态消解**：两个 writer 并发（如 `RunController.cancel` 与 orchestrator 终态 SUCCEEDED 在同一 `runId` 上竞）时，原子 CAS 的 `WHERE status NOT IN (CANCELLED, SUCCEEDED, FAILED, EXPIRED)` 子句**仅允许一个胜出**。败者 re-read 看到 CAS 后的 Run row 并按之返回（胜方状态匹配请求转换则 200，否则 409）。状态机因此**结构性建模**竞态，而非 retry-and-pray。

### 15.4 Task.A2aState 状态机（A2A 协议包络）

> Mermaid stateDiagram-v2 见 .en 版本 §15.4。

Task.A2aState 是**控制态** DFA。它与 RunStatus（**执行态** DFA）解耦。一个 WORKING 中的 Task 可能有多个瞬时 Run 循环经过 PENDING/RUNNING/SUSPENDED/SUCCEEDED。

### 15.5 SuspendSignal 流（child-run + S2C-callback 两变体）

> Mermaid flowchart 见 .en 版本 §15.5。

两个 SuspendSignal 变体共享**同一 checked-exception 类型签名** — 这是 ADR-0100 §rejected-framings #2（Yield/SuspendSignal 共存）的设计：一个 Java 编译器守卫、一个 Rule R-G ArchUnit 守卫、一个 Rule R-H "no Thread.sleep" 强制作用域。

### 15.6 词汇术语表（PR #71 ↔ 已落地）

本文档顶部 §3 词汇校准表是规范术语表。Wave 6 将这些同义词传播到 `Run.java` / `Task.java` / `Session.java` / `SuspendSignal.java` / `SuspendReason.java` 的 Javadoc，让未来读其中一种拼法的维护者都到达规范类型。ADR-0136（规范实体校准）+ ADR-0137（SuspendSignal 术语表）是 ADR 级权威。

---

# Wave 2 闭环（G-A..G-F）

- **G-A 直接修复**：§14 Scenarios + §15 Logical View 追加到本评审稿（.en + .cn）；Wave 2 任务关闭。
- **G-B 分类**：Wave 2 撰写期间无新发现；无新 family 注册。（Negative confirmation: 0 new findings.）
- **G-C 兄弟横扫**：Wave 2 范围是 design-doc 内部（向同一评审文件追加视图）；§8 Wave 1 清单仍规范。在新追加的 §14/§15 散文上重跑 family 指纹返回 0 新兄弟 hit（Grep 已核实：无风险措辞、无孤立权威 ref、无 tenantId-less ER 块、无单层队列散文）。Negative confirmation：F-design-artifact-omits-tenant-spine 模式在 active-local 上 0 命中（除 ADR / Wave-1-§8 引用）。
- **G-D 持续修复**：无 in-scope 兄弟需吸收；3 个 Wave 1 deferred 兄弟保留为 Wave 5 跟进。
- **G-E 非空守卫**：§15.2 ER 块在 4 个实体上都有 tenantId（已核实）；§15.3 RunStatus 状态机在每个 cancel 箭头上都有 CAS + tenant-guard 标注（已核实）；§15.5 SuspendSignal 流使用规范 Java 类型名（已核实）。
- **G-F 文档化**：Wave 2 章节就地追加；本 Closure block 总结 wave。

---

# Wave 3 — Process View + Physical View

> 由 Wave 3/6 追加（rc53-wave-3）。Mermaid 时序与拓扑图见 `.en` 版本 §16 / §17 — 结构性 artefact 保持中英一致以便 Github 渲染。

## 16. Process 视图

5 个规范化时序图，每个映射到 §14 一个 Scenario：

- **P1 — 标准同步入口 → 状态机 → suspend → resume**（覆盖 S1 + S2）：见 .en §16.1。展示 X-Tenant-Id 绑定 → IdempotencyHeaderFilter claimOrFind（ADR-0057）→ RunRepository.save（PENDING + tenantId）→ DualTrackRouter judge → Fast-Path 同步直返 200 OR Slow-Path 返回 202 TaskCursor（Rule R-F）。Fast-Path 走 `updateIfNotTerminal` CAS（Rule R-C.2.b + ADR-0118）；Slow-Path 在 SuspendSignal 后挂起。
- **P2 — Fast-Path / Slow-Path 决策树**（覆盖 S1/S2 分支）：见 .en §16.2。强调 Fast-Path 不跳过 RLS 持久化（ADR-0139 红线）+ 反应式 + 无 sleep（Rule R-G / R-H）；Slow-Path 在每个 tool-call boundary 跑 Checkpointer.snapshot。
- **P3 — Cancel 重鉴权 + 原子 CAS**（覆盖 S5）：见 .en §16.3。Run 找不到 → 404；Run.tenantId ≠ reqTid → 收敛为 404（Rule R-J.b W0；W1 widen 到 403 deferred per ADR-0108）；CAS 胜 → 200 + audit；CAS 输 → 重读 → 同终态 200（幂等）或异终态 409。**没有 findById→withStatus→save 序列**，CAS WHERE 子句 + 重读就是原子性。
- **P4 — S2C 客户端回调**（覆盖 S4）：见 .en §16.4。Executor 抛 `SuspendSignal.forClientCallback(...)` → Checkpointer.snapshot → updateIfNotTerminal CAS 转 SUSPENDED + SuspendReason.AwaitClientCallback → Bus.publish 到 `control` 通道（Rule R-E + bus-channels.yaml）→ 客户端经 `data` 通道返回 ≤ 16 KiB inline response（Rule R-E §4 #13）→ 校验 s2c-callback.v1.yaml#response → resume 或 FAILED。
- **P5 — Idempotency 去重链**（覆盖 S1 入口加固）：见 .en §16.5。SHA-256 hash(method:path:body) → claimOrFind(tenantId, key, hash)；4 个出口：首请求 → 201；重放命中 → 200 cached；hash 不一致 → 409 body_drift；在飞同 key → 409 conflict（按 ADR-0057）。

P3 结构性防御 `F-nonatomic-run-status-write`（4 次复发：rc35/rc36/rc38/rc39）— CAS WHERE 子句 + 重读即原子性，没有分步写入。

## 17. Physical 视图

### 17.1 五平面部署映射（原则 P-I）

见 .en §17.1。模块按 `deployment_plane:` 字段划分：
- `edge` — agent-client SDK（W3+ design_only）+ Web/App/CLI 客户端
- `compute_control` — agent-service（本模块）+ agent-execution-engine
- `bus_state` — agent-bus（三轨通道；S2C/Ingress/A2A transport）+ agent-middleware（ModelGateway/Skill/Memory/Vector/Planner/Sandbox SPI）+ PostgreSQL（RLS-enabled tables，Rule R-J.a）
- `sandbox` — 不可信代码执行（按 sandbox-policies.yaml + Rule R-L）
- `evolution` — agent-evolve（Python ML 离线/在线 evolution）

**禁止的拓扑边**：edge → compute_control 直连（Rule R-I.1，仅 IngressGateway）；engine → middleware 直连（Rule R-M.a/.c）。

### 17.2 数据库 schema + RLS policy（Rule R-J.a）

| 表 | tenantId 列 | RLS | Flyway 迁移 | W2 retrofit |
|---|---|---|---|---|
| `runs` | `tenant_id NOT NULL` | 是 | `V1__runs_create.sql` 同迁移启用 RLS + per-tenant POLICY | 否 |
| `tasks` | `tenant_id NOT NULL` | 是 | W2（Task 持久化未落地；当前 `Task` record 仅在内存） | Task 持久化落地时同迁移加 RLS |
| `sessions` | `tenant_id NOT NULL` | 是 | W2 | Session 持久化落地时同迁移加 RLS |
| `lifecycle_state_audit` | `tenant_id NOT NULL`（FK 派生） | 是 | W1+ | 表创建时 RLS |
| `idempotency_dedup`（legacy） | `tenant_id NOT NULL` | grandfathered 否 | 早于 R-J.a 规则 | **是** — W2 retrofit per deferred R-J.a.b |
| `s2c_callback_audit` | `tenant_id NOT NULL` | 是 | 随 S2C runtime_enforced 落地（Rule R-M.d） | 否 |

应用级租户绑定：每请求由 `TenantContextFilter` 绑定（X-Tenant-Id + JWT claim 交叉校验，ADR-0040）；DB session 级 `SET LOCAL app.tenant_id` GUC 在 W2（与 R2DBC 同步）。

### 17.3 三轨总线物理绑定（Rule R-E）

| 通道 | 优先级 | physical_channel（W0/W1） | physical_channel（W2+） | 承载 intent |
|---|---|---|---|---|
| **control** | 最高 | `in_memory_priority_queue_w0` | NATS subject `agent.control.>`（或 RabbitMQ priority queue） | PAUSE / KILL / CANCEL / RESUME / DEADLINE_SHIFT / S2cCallbackEnvelope request |
| **data** | 普通 | `in_memory_unbounded_queue_w0` | NATS JetStream `agent.data.>`（或 Kafka） — 重载、持久 | Run input payload / S2cCallback response / Engine output |
| **rhythm** | 低 | `in_memory_tick_w0` | 轻量 tick service（Quartz / 定时任务） | Heartbeat / liveness pulse（1 Hz baseline） |

`bus-channels.yaml` 是规范 manifest；ADR-0138 §3 把 agent-service "Internal Event Queue" 层绑定到这三个通道 — **显式禁止**单队列 + 模式持久化轴。

### 17.4 Sandbox 隔离边界（Rule R-L）

见 .en §17.4。Engine Runtime → SandboxExecutor SPI →（policy subsumption 检查，R-L.b W2 deferred）→ 物理 sandbox（separate process / container / VM）。物理 sandbox 强制：(1) 网络 egress 限制，(2) 文件系统读写限制，(3) CPU/memory/wall-clock 上限。Runtime 不直接 access sandbox executor — 经 `RuntimeMiddleware` on `HookPoint.before_tool`。

---

# Wave 3 闭环（G-A..G-F）

- **G-A 直接修复**：§16 Process View + §17 Physical View 追加（.en + .cn）。Wave 3 任务关闭。
- **G-B 分类**：Wave 3 撰写期间 0 新发现；0 新 family。
- **G-C 兄弟横扫**：在新 §16/§17 散文上重跑 family 指纹返回 0 新兄弟 hit。Negative confirmation：F-design-doc-violates-three-track-bus 在 active-local 上 0 命中（除 Wave 1 §8 已 documented 站点 — §17.3 表是规范三轨绑定，引用 `bus-channels.yaml` 满足同段绑定要求）；F-design-doc-language-bypasses-invariant 0 命中（§16.2 Fast-Path 分支显式 "Run + Task metadata STILL persisted under RLS" — 不变量保留）。
- **G-D 持续修复**：Wave 1 deferred 兄弟保持不变。
- **G-E 非空守卫**：§16.3 P3 cancel 时序图有显式 re-auth + CAS + post-CAS 重读（与已发布 `RunRepository.updateIfNotTerminal`（ADR-0118）一致）；§17.2 RLS 表列每个 `tenant_id`-bearing 表的 policy 状态；§17.3 把 Internal Event Queue 绑定到 bus-channels.yaml。
- **G-F 文档化**：本 Closure block。

---

# Wave 4 — Development View + SPI 附录 + L2 边界契约

> 由 Wave 4/6 追加（rc53-wave-4）。满足 Rule G-1.1 三 sub-clause：.a（Development View 代码映射）、.b（SPI 接口附录 4-way parity）、.c（L2 边界契约）。

## 18. Development 视图

按 Rule G-1.1.a，Development View 必须含 Markdown fenced 文本块声明包级目录树；Logical View 中命名的每个主要组件必须映射到具体代码路径；树路径在 gate 时与实际 filesystem 交叉校验。包树见 `.en` §18 — 在 `agent-service/src/main/java/com/huawei/ascend/service/...` 下：

- `platform/{auth, engine, idempotency, observability, persistence, posture, probe, resilience, tenant, web}` — 平台横切（ADR-0078 起在 service.platform 下）；`web/runs/` 含 RunController（**Layer 1 Access** 入口）。
- `runtime/{runs, orchestration/inmemory, memory, resilience, s2c, idempotency, posture, probe, evolution}` — runtime kernel（按 ADR-0078 + ADR-0088 合并）；`orchestration/inmemory/` 含 SyncOrchestrator、SequentialGraphExecutor、IterativeAgentLoopExecutor、InMemoryCheckpointer — **Layer 4 Task-Centric Control** 核心。
- `engine/{adapter, spi}` — rc22 per ADR-0100 — **Layer 5 Engine Adapter**。
- `session/{Session.java, spi/}` — rc22 per ADR-0100 — **Layer 2 Session 半**；含 `ContextProjector` SPI。
- `task/{Task.java, spi/}` — rc22 per ADR-0100 — **Layer 2 Task 半**；含 `TaskStateStore` SPI。
- `agent/{spi}` — rc43 per ADR-0128 — Agent first-class entity SPI。
- `integration/springai` — Spring AI 参考 adapter shell（ADR-0125）。
- `queue/` — **Layer 3 Internal Event Queue** 绑定层 — 当前**未在文件系统上**（Wave 4+ scaffold）；按 ADR-0138 §3 绑定到 agent-bus 三轨通道。

**Layer ↔ 子包 映射** 见 .en §18 cross-walk 表；**ADR-0100 5 组件 ↔ 本 L1 5 层** 映射也见 .en §18。两种分解是同一架构的不同投影。

## 19. SPI 接口附录

按 Rule G-1.1.b，SPI 接口附录必须列出模块发布的每个 `public interface` FQN 并满足 **4-way parity**（module-metadata ↔ contract-catalog ↔ DFX ↔ Java 源文件）。

### 19.1 当前已落地 SPI（9 接口，7 spi_packages）

见 .en §19.1 完整表格。9 个 SPI：

1. `RunRepository`（runs.spi，W1 已发布；按 ADR-0118 抽象 `updateIfNotTerminal`）
2. `GraphMemoryRepository`（memory.spi，W1 in-memory 参考实现）
3. `ResilienceContract`（resilience.spi，W1 已发布）
4. `SkillCapacityRegistry`（resilience.spi，W1 已发布）
5. `StatelessEngine`（engine.spi，rc23/rc24 per ADR-0100）
6. `ContextProjector`（session.spi，rc23/rc24 per ADR-0100）
7. `TaskStateStore`（task.spi，rc23/rc24 per ADR-0100）
8. `Agent`（agent.spi，rc43 design_only per ADR-0128）
9. `AgentRegistry`（agent.spi，rc43 design_only per ADR-0128）

**4-way parity 校验**（gate 时机器化）：(1) module-metadata.yaml#spi_packages 7 包 ↔ (2) contract-catalog.md §2 ↔ (3) docs/dfx/agent-service.yaml#spi_packages ↔ (4) filesystem。Wave 5 任务负责把 contract-catalog + dfx 落齐。

### 19.2 本 Wave 1 声明的新 SPI（design_only）

ADR-0138 §3 命名 `service.queue/` 子包给 Layer 3 绑定层；ADR-0138 也命名 `DualTrackRouter` SPI（W4+ design_only）。本 wave **不**引入新 Java SPI 接口 — 仅在已落地 SPI 上批准 5 层 L1 视图。

### 19.3 从其他模块消费的 SPI（非本模块导出）

见 .en §19.3 完整表格 — agent-execution-engine 提供 `Orchestrator` / `Checkpointer` / `SuspendSignal` / `ExecutorAdapter` / `EngineHookSurface`；agent-bus 提供 `S2cCallbackTransport` / `IngressGateway`；agent-middleware 提供 `RuntimeMiddleware` / `HookPoint` 及 advisor / memory / model / retrieval / skill 等 SPI。

## 20. L2 边界契约

按 Rule G-1.1.c，任何由 L2（`docs/L2/*.md`）承担的子系统必须在 L1 声明 **inputs / outputs / DFX expectations** 边界契约。PR #71 F-01..F-22 是天然起点；每行映射到未来一份 L2 文档，且本节就是 L1 侧承诺。

当前 `docs/L2/` 0 文件（已核实）。下方 5 个 L2 zone 是 L1 侧承诺（详见 .en §20.1-§20.5 表格）：

- **L2-A Access 层**：HTTP Gateway / A2A Service / MQ Adapter — `openapi-v1.yaml` + `a2a-envelope.v1.yaml` + `ingress-envelope.v1.yaml` 为契约权威。
- **L2-B Session & Task 管理层**：SessionManager + ContextProjector + TaskCenter + TaskStateStore + RunRepository — ADR-0100 + ADR-0135 + Rule R-C.2 + ADR-0118 为契约权威；写操作经原子 CAS。
- **L2-C 内部事件队列**：Producer/Consumer 按 intent 绑定到 `control/data/rhythm` 三轨；Outbox/Inbox 模式 W2+ — `bus-channels.yaml` + Rule R-E + ADR-0057 为契约权威。
- **L2-D Task-Centric Control 层**：Orchestrator + DualTrackRouter + ResumeDispatcher + RuntimeMiddleware chain — Rule R-C.2 + Rule R-G + Rule R-J + ADR-0019/0073/0074/0118/0139 为契约权威。
- **L2-E Engine Adapter 层**：EngineRegistry + ExecutorAdapter + ContextProjector + PromptTemplate + StructuredOutputConverter + ChatAdvisor — Rule R-M + Rule R-G/R-H + ADR-0130/0131/0132/0133 + ADR-0100 为契约权威。

### 20.6 F-01..F-22 特性 → L2 映射

见 .en §20.6 完整 22 行映射表。每行带 `authority:` 列引用 ADR/Rule，满足 Rule M-2.b 设计稿 ADR 锚定精神。代表性示例：F-09（Task-centric state machine）→ L2-D，authority = Rule R-C.2 + ADR-0118；F-12（Fast-Path 路由）→ L2-D，authority = ADR-0139（窄化语义）；F-16（Shadow Tool Interceptor）→ L2-E，authority = ADR-0132 + Rule R-M.c。

---

# Wave 4 闭环（G-A..G-F）

- **G-A 直接修复**：§18 Development View + §19 SPI 附录 + §20 L2 边界契约追加（.en + .cn）。Rule G-1.1.a/.b/.c 满足。Wave 4 任务关闭。
- **G-B 分类**：横扫中 1 个新发现 — ADR-0138 §3 引用的 `service.queue/` 子包当前**未在 filesystem**。这是 **design-time forward declaration**，非缺陷；在 §18 layer-to-package 映射和 §19.2（未来 SPI 占位符）中透明跟踪。无 family 注册。
- **G-C 兄弟横扫**：在 §18（development view 代码映射）和 §19（SPI 附录 4-way parity）上重跑 F-l1-architecture-grounding-gap 指纹 — 均满足 Rule G-1.1.a/.b 结构形态（fenced 文本块 + filesystem 路径交叉引用）；0 新兄弟 hit。
- **G-D 持续修复**：Wave 1 deferred 兄弟保持不变。
- **G-E 非空守卫**：§18 树把 5 层映射到 filesystem 路径（与实际 `find agent-service/src/main/java/com/huawei/ascend/service/` 树交叉校验）；§19.1 列 9 SPI 接口与 sub-package + status + authority（与 `module-metadata.yaml` 第 13-20 行交叉校验）；§20 有 5 个 L2 zone × 3-4 行 + F-01..F-22 inventory 每行带 `authority:`。
- **G-F 文档化**：本 Closure block。

---

# Wave 5 — Rule + 契约目录级联

> Wave 5/6（rc53-wave-5）。按 §6 ADR 草案清单 + Wave 1 deferred 兄弟清单（§9），本 wave 把 4 份 ADR 升级为 accepted 并关闭 3 个 deferred 兄弟。

## 21. ADR 升级（proposed → accepted）

| ADR | 标题 | Wave 5 状态 |
|---|---|---|
| ADR-0136 | Vocabulary Reconciliation: PR 71 "Task" ≡ existing platform Task entity (not Run alias) | **accepted** |
| ADR-0137 | SuspendSignal Canonical; InterruptSignal / InterruptReason are L1 Glossary Synonyms | **accepted** |
| ADR-0138 | Agent Service 5-Layer L1 Ratification | **accepted** |
| ADR-0139 | Fast-Path / Slow-Path Narrowed Semantics | **accepted** |

## 22. Rule + 契约目录核验

- **Rule 卡**（R-C.2 / R-M / R-J / R-H / R-D / R-E / R-K / G-1.1 / G-3）：无 kernel 文本改动 — L1 重写使用已落地词汇；ADR-0136..0139 是文档级校准，非 Rule kernel 突变。
- **CLAUDE.md kernel**：无改动（词汇不变）。
- **`docs/contracts/contract-catalog.md` §2**：已核验包含 9 个 agent-service SPI 行（RunRepository / GraphMemoryRepository / ResilienceContract / SkillCapacityRegistry / StatelessEngine / ContextProjector / TaskStateStore / Agent / AgentRegistry）；4-way parity 完整。
- **`agent-service/module-metadata.yaml#spi_packages`**：7 个 package 声明（第 13-20 行），无改动。
- **`docs/dfx/agent-service.yaml#spi_packages`**：原有 parity 保留。

Wave 5 对 Rule 卡 / CLAUDE.md / contract-catalog / module-metadata / DFX 文件无改动 — L1 校准是 doc-only。

## 23. Wave 1 deferred 兄弟关闭

§9 中 3 个 deferred 兄弟关闭：

| Family | 推后的兄弟 | Wave 5 处置 |
|---|---|---|
| F-design-doc-violates-three-track-bus | `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.{en,cn}.md` "internal queue" 散文 | 在 §1.3.4 段落（.en + .cn）就地追加 **（2026-05-26 rc53-wave-5 按 ADR-0138 §3 红线 c 更新）** 说明；把 internal-queue 概念绑定到 `bus-channels.yaml` 三轨 manifest；交叉引用 Wave 1 §15.1 / §17.3 |
| F-design-doc-language-bypasses-invariant | 同文件 "compact edge deployment" / "stateless and semi-persistence" Fast-Path 散文 | 在 §1.3.4 Stateless/Semi-persistence 段（.en + .cn）就地追加 **（2026-05-26 rc53-wave-5 按 ADR-0139 窄化 Fast-Path 语义更新）** 说明；pin Rule R-G + R-H + R-J.a 不变量；交叉引用 Wave 1 §4.4 + §16.2 |
| F-placeholder-leaks-into-active-corpus | `docs/logs/reviews/2026-05-13-{wanshoulu}-wave-N-request.md` slug | 在文件顶部追加 **slug 占位符说明**，解释 `{wanshoulu}` 是代号占位符，按 logs-folder-policy 为稳定 URL 保留；未来评审稿 slug 使用主题化命名 |

## 24. Wave 5 闭环（G-A..G-F）

- **G-A 直接修复**：4 份 ADR 升级；3 个 deferred 兄弟经就地标注关闭。
- **G-B 分类**：Wave 5 期间 0 新发现。
- **G-C 兄弟横扫**：在 3 个已关闭兄弟的指纹上重跑 — `Grep` 确认每个标注段落现在包含绑定引用（三轨）/ 不变量保留 pin（Fast-Path）/ 占位符说明（wanshoulu slug）；0 新兄弟 hit。
- **G-D 持续修复**：全部 3 个 Wave 1 deferred 兄弟在本 wave 关闭。
- **G-E 非空守卫**：ADR YAML schema 校验（Wave 1 已核验）；contract-catalog §2 行校验（Wave 4 §19 已核验 — agent-service 有 9 SPI 行）；module-metadata.yaml spi_packages 数 = 7（Wave 4 已核验）；dfx_packages parity（Wave 4 已核验）。
- **G-F 文档化**：本 Closure block。

---

# Wave 6 — Javadoc 词汇表注入

> Wave 6/6（rc53-wave-6）。按 ADR-0136 + ADR-0137 §decision #2，本 wave 把"Vocabulary Glossary"段注入 5 个携带规范词汇的类型 Javadoc，让未来读学术拼法（Task/Run、InterruptSignal/SuspendSignal）或平台拼法的维护者都到达规范 Java 类型。**无方法签名 / 字段 / 包 / DB schema 改动。**

## 25. Javadoc 词汇表补丁

| Java 类型 | 文件 | 词汇表内容（摘要） |
|---|---|---|
| `Run`（record） | `agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/Run.java` | "Run 是瞬时计算快照层；不是 Task 的同义词。PR-71 'Task as scheduling core' 映射到 {@link Task}，不是 Run 的重命名。"（按 ADR-0136 + ADR-0100） |
| `Task`（record） | `agent-service/src/main/java/com/huawei/ascend/service/task/Task.java` | "PR-71 'Task as scheduling core' 指此类（不是 Run 重命名）。Task 是控制态；Run 是计算快照 — 按 ADR-0100 是不同实体。一个 Task 可能有多个瞬时 Run。" |
| `Session`（record） | `agent-service/src/main/java/com/huawei/ascend/service/session/Session.java` | "PR-71 'SessionManager' 指此实体 + {@link ContextProjector}。AgentSession 按 ADR-0135 NOT 一个独立 SPI — 它是 (tenantId, conversationId) 投影。" |
| `SuspendSignal`（checked 异常） | `agent-execution-engine/src/main/java/com/huawei/ascend/engine/orchestration/spi/SuspendSignal.java` | "PR-71 'InterruptSignal' / 'interrupt primitive' 指此类。ADR-0100 §rejected-framings #2 显式保留 checked-exception 形态作为 Tier-A 差异化点。" 同义词：InterruptSignal ≡ SuspendSignal；InterruptReason ≡ SuspendReason；Yield ≡ HookPoint.ON_YIELD。 |
| `SuspendReason`（sealed interface） | `agent-service/src/main/java/com/huawei/ascend/service/runtime/resilience/spi/SuspendReason.java` | "PR-71 'InterruptReason' / 'InterruptType' 指此 sealed interface。4 值 PR-71 InterruptType 枚举分解到 3 个已有机制（Task.A2aState.INPUT_REQUIRED、HookPoint.before/after_tool、SuspendReason.AwaitClientCallback）+ deferred SafetyCheck permit。" 无 InterruptType 枚举引入。 |

## 26. Wave 6 闭环（G-A..G-F）

- **G-A 直接修复**：5 个 Java 类型加 Vocabulary Glossary Javadoc 段。无方法签名 / 字段 / 包 / DB schema 改动。
- **G-B 分类**：0 新发现；0 family 注册。
- **G-C 兄弟横扫**：在修改的 Java 源上重跑 4 个 active family — Javadoc 改动未引入新违规。Negative confirmation：`Grep "Vocabulary Glossary"` 跨 `agent-service/src/main/java/` + `agent-execution-engine/src/main/java/` 返回 5 命中（每个修改文件一处）— count 与预期相符。
- **G-D 持续修复**：无新 in-scope 兄弟；out-of-scope 兄弟保持不变。
- **G-E 非空守卫**：5 个 Java 文件修改（git diff 已核实 — Run.java + Task.java + Session.java + SuspendSignal.java + SuspendReason.java）；compile-time 影响：零（Javadoc-only edit）。
- **G-F 文档化**：本 Closure block。

---

# Wave 8 — ARCHITECTURE.md 指针 + Release Note + 关闭

> 最终 wave（rc53-wave-8）。原 8-wave 计划的 Wave 7（Run→Task / SuspendSignal→InterruptSignal Java 重命名 + Flyway schema 迁移）在 Wave 1 ADR-0100 校准后删除；Wave 8 因此是 Wave 6 的直接后续，关闭 rc53 wave family。

## 27. 迁入策略

`agent-service` 的规范化 L1 4+1 源是本文档（Waves 1-6）。Wave 8 把活动的 `agent-service/ARCHITECTURE.md` 接到本规范源 — 而**不是**覆写 800+ 行已落地散文：

- 在 `agent-service/ARCHITECTURE.md` 顶部增加 `## 0.5 Canonical L1 4+1 View Source (rc53 ratification)` 章节，链接本文档作为 4+1 视图权威。
- 现有 §1+ 散文保留为 shipped-state grounding；如与 rc53 4+1 视图冲突（tenantId-first ER、cancel-race-aware 状态机、Internal Event Queue 层的三轨绑定），**以 rc53 4+1 文档为准**。
- `Last refreshed:` 头行 + `authority:` front-matter 行推进，记录 rc53 ratification。

## 28. 历史快照标记

本文档现按 `docs/governance/logs-folder-policy.md` 只读（Wave 8 闭环后）。文件顶部标记（Wave 8 加入）声明 freeze 并指引未来编辑到新评审稿。文件中冻结的数值是 rc53 闭环时的 snapshot evidence；活动的 `agent-service/ARCHITECTURE.md` §0.5 指针承前向权威。

## 29. families.yaml + families.md 同步（Rule G-9.b/c）

- `docs/governance/recurring-defect-families.yaml#last_updated:` 推进到引用 6 个 wave commits + deliverables 的 Wave-8-closure 说明。recurring_defect_families 数保持 20（Waves 5-8 无新 family 发现）。
- `docs/governance/recurring-defect-families.md` §3 META-Lessons 表可选追加 rc53 行（TBD — 微小整理，本 wave commit 可加可不加）。

## 30. Release Note

发布为 `docs/logs/releases/2026-05-26-rc53-agent-service-l1-4plus1-rewrite-closure.en.md`。总结 6 waves 交付物、列接受的 ADR（0136-0139）、声明 4 红线、列词汇校准表、给 WSL/Linux 验证命令。

## 31. Wave 8 闭环（G-A..G-F）

- **G-A 直接修复**：§0.5 指针加到活动 `agent-service/ARCHITECTURE.md`；历史快照标记加到本评审稿顶部；release note 发布；families.yaml last_updated 内容差异存在（Rule G-9.b）。
- **G-B 分类**：Wave 8 期间 0 新发现。
- **G-C 兄弟横扫**：在含新 release note + 更新 ARCHITECTURE.md 的 active 语料上重跑 7 个触动 family — 0 新兄弟 hit。Negative confirmation：新 ARCHITECTURE.md §0.5 含显式 "rc53 4+1 document is authoritative" 优先级语言；无 Fast-Path bypass 措辞、无单层队列语言、无 tenantId-less ER、无权威孤儿。
- **G-D 持续修复**：无新 in-scope 兄弟；3 个 Wave 1 deferred 兄弟保持关闭（Wave 5）。
- **G-E 非空守卫**：4 个 wave-8 文件编辑通过 `git status` 核实（ARCHITECTURE.md + 本文件 + cn 兄弟 + families.yaml）；1 个新文件（release note）。
- **G-F 文档化**：本 Closure block + standalone release note 是 wave 最终文档。

---

# rc53 最终闭环总结

8 waves 计划 → 6 waves 执行（Wave 7 在 Wave 1 ADR-0100 校准后删除）。每个 wave G-A..G-F 6 个 gate 全部通过。4 个新 ADR 接受（ADR-0136..0139）。4 个新缺陷家族注册，3 个现有扩展（recurring_defect_families 16 → 20）。agent-service 的 L1 4+1 视图现在以本评审稿规范发布，活动的 ARCHITECTURE.md §0.5 指针承前向权威。PR #71 已处置：词汇被吸收为术语表同义词、结构性缺陷在设计层修复、脚手架被批准、无需 Java 重命名。
