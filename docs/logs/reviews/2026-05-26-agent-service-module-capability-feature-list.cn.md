---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-service
affects_level: L1
affects_view: [scenarios, logical, process, development, physical]
status: proposed
language: zh-CN
relates_to:
  - docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md
  - docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.en.md
  - docs/logs/reviews/2026-05-26-agent-service-l1-oss-comparison-review.cn.md
  - docs/logs/reviews/2026-05-26-agent-service-l1-oss-comparison-source.cn.md
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
  - docs/adr/0139-fast-slow-path-narrowed-semantics.yaml
  - docs/adr/0140-agent-service-layer5-split.yaml
  - docs/adr/0141-internal-event-queue-design-only.yaml
  - docs/adr/0142-run-aggregate-single-owner.yaml
  - docs/adr/0143-review-log-demotion-l1-canonical-move.yaml
  - docs/adr/0144-agent-service-layer-package-matrix.yaml
  - docs/adr/0145-run-event-sealed-hierarchy.yaml
---

# Agent Service L1 — 模块功能详细描述与特性清单

> 日期：2026-05-26  
> 范围：`agent-service` 模块的服务职责、模块功能、业务场景覆盖与特性清单。  
> 定位：本文是 `docs/logs/reviews/` 下的架构评审意见，用于把历史 feature inventory、canonical 4+1 视图与 OSS 对比结论收敛成一份便于人和 AI 共同理解的 L1 模块能力清单。本文不替代 `docs/L1/agent-service/` 下的 canonical L1 视图；若有冲突，以 `docs/L1/agent-service/` 为准。

## 1. 结论先行

Agent Service 是一个 **Agent-native control service**，不是某个 Agent SDK、Workflow Engine、ChatClient、A2A starter 或 Runtime wrapper 的薄封装。它的核心职责是把外部请求安全地转为 tenant-bound Run / Task / Session 状态变化，并在服务端主权边界内治理异构 engine、工具调用、模型调用、暂停 / 恢复 / 取消、S2C callback、A2A peer collaboration 与长期运行任务。

最新 L1 架构下，特性清单应回答三个问题：

1. **系统能否按业务场景跑通**：S1 标准同步 intake、S2 长周期 ReAct/tool loop、S3 A2A peer collaboration、S4 S2C callback、S5 cancel during execution 都必须能从入口、状态、事件、控制、执行、翻译完整闭环。
2. **每项能力由哪个完整模块拥有**：清单按模块全称归类，不用层号或优先级替代模块名；互相配合的能力要正交，不能让两个模块同时拥有同一个事实源。
3. **能力水平是否达到业界平均线**：A2A Java、Spring AI A2A、AgentScope Runtime、Temporal、Conductor、LangGraph、LangChain4j、Spring AI、AutoGen、CrewAI、OpenAI Agents、Semantic Kernel 等只作为模式参照；本项目边界仍以 ADR、Rule、contract 与 canonical L1 视图为准。

因此，本文不引入额外“状态标签”或实现优先级。一个好的 L1 特性清单本身应降低后续设计熵：它只说明模块应具备哪些功能、这些功能覆盖哪些业务路径、输入输出是什么、与哪些模块协作、异常如何闭环，以及参考了哪些成熟 OSS 模式。

## 2. 分解原则

| 原则 | 含义 | 防止的问题 |
|---|---|---|
| 业务场景锚定 | 每项特性至少能回到 S1-S5 或跨场景不变量。 | 只按组件罗列，无法判断系统能否正常工作。 |
| 模块全称归属 | 使用 Access Layer / 对外接入层、Session & Task Manager / 会话与任务管理层等完整模块名。 | 用抽象层级标签代替真实模块，导致 ownership 不清。 |
| 事实源单一 | Run execution state、Task control state、Session context state、RunEvent audit/event stream 各有单一主权。 | StateStore 泛化、Run/Task 混同、Runtime 反向写状态。 |
| 控制流与数据流并重 | 既覆盖请求如何走到执行，也覆盖状态、事件、payload、callback、audit 如何流动。 | 只有 happy path，缺少异常、race、resume、dead-letter 视角。 |
| 模块协作正交 | 模块间通过明确输入 / 输出 / contract 协作，不把同一策略塞进多个模块。 | RuntimeMiddleware 与 ChatAdvisor 混淆，队列与状态机互相侵占。 |
| 业界基线校准 | 用 OSS 证明哪些能力是成熟 agent/workflow 服务的平均线。 | 只实现普通 SDK runner，缺少长周期、队列、interrupt、worker、human callback 等平台能力。 |

## 3. 业务场景到模块协作矩阵

| 场景 | 正常业务闭环 | 关键异常闭环 | 主协作模块 |
|---|---|---|---|
| S1 标准同步 intake | Access Layer / 对外接入层接收 `POST /v1/runs`，Session & Task Manager / 会话与任务管理层创建 Run / Task，Task-Centric Control Layer / 任务中心控制层调度，Engine Dispatch & Execution / 引擎调度与执行模块执行，Translation & Tool-Intercept / 翻译与工具拦截模块完成上下文、prompt、模型调用塑形。 | cross-tenant 请求收敛为边界错误；idempotency hit 返回缓存或冲突；engine envelope schema invalid 转为 `engine_mismatch` 失败。 | 对外接入层、会话与任务管理层、任务中心控制层、引擎调度与执行模块、翻译与工具拦截模块。 |
| S2 长周期 ReAct / tool loop | 对外接入层返回 Task Cursor；会话与任务管理层维护 Run / Task / Session；内部事件队列绑定 control / data / rhythm；任务中心控制层在 HookPoint 周期内驱动 middleware；引擎调度与执行模块执行 agent loop；翻译与工具拦截模块解释 tool/model 调用。 | tool timeout、middleware short-circuit / fail、resume deployment locus 变化、checkpoint 恢复失败都必须通过 SuspendSignal、RunRepository CAS、RunEvent 与 channel routing 表达。 | 六个模块全部参与，其中内部事件队列提供长期运行的事件与节奏边界。 |
| S3 A2A peer collaboration | 父 Run 在本实例暂停；对外接入层 / IngressGateway 负责 peer ingress / egress；任务中心控制层派生 child Run 并等待；peer terminal status 回流后父 Run 恢复。 | peer unreachable、peer error envelope、cross-tenant peer call、child terminal failure 都必须保留 parentRunId / traceId / tenantId 关联，并由父 Run 决定恢复、失败或重试。 | 对外接入层、会话与任务管理层、内部事件队列、任务中心控制层、引擎调度与执行模块。 |
| S4 S2C client callback | 引擎调度与执行模块抛出 `SuspendSignal.forClientCallback(...)`；任务中心控制层发布 S2cCallbackEnvelope；内部事件队列用 control channel 发请求、data channel 收响应；客户端 resume 后 executor 继续。 | client timeout、response schema invalid、capacity exhausted、resume re-auth 失败必须进入明确事件、状态和错误 envelope。 | 会话与任务管理层、内部事件队列、任务中心控制层、引擎调度与执行模块、对外接入层。 |
| S5 执行中取消 | 对外接入层处理 cancel 请求并 re-auth；会话与任务管理层通过 `RunRepository.updateIfNotTerminal(...)` 做原子 CAS；任务中心控制层按 winner / loser 分类；内部事件队列输出 audit event。 | same-terminal cancel 返回幂等成功；different-terminal cancel 返回 illegal transition；cancel-vs-complete race loser 重新读取 post-CAS 状态并返回确定响应。 | 对外接入层、会话与任务管理层、任务中心控制层、内部事件队列。 |

## 4. 数据流与控制流闭环

### 4.1 数据流

1. **入口数据**：外部请求携带 tenant、identity、idempotency key、trace、协议 payload 与 engine envelope，经 Access Layer / 对外接入层规范化。
2. **状态数据**：Session & Task Manager / 会话与任务管理层将入口数据拆成 Run execution state、Task control state、Session context state 与 IdempotencyRecord；所有 tenant-bound 持久化受 RLS 约束。
3. **事件数据**：RunCreated、RunStateTransition、SuspendRequested、ResumeRequested、S2C、ChildRun、CancelRequested、TerminalTransition 等 RunEvent 由状态和控制边界产生，并按 intent 映射到 control / data / rhythm。
4. **执行数据**：Task-Centric Control Layer / 任务中心控制层把 RunContext 与 EngineEnvelope 交给 Engine Dispatch & Execution / 引擎调度与执行模块；执行模块只返回 result、yield、SuspendSignal 或 HookPoint event，不拥有服务级状态。
5. **模型与工具数据**：Translation & Tool-Intercept / 翻译与工具拦截模块把 Session projection 转成 InjectedContext、PromptTemplate、model invocation、tool request 与 structured output。
6. **回流数据**：tool result、S2C response、child-run completion、terminal result 回流到任务中心控制层，再通过会话与任务管理层的 CAS / projection / audit 进入事实源。

### 4.2 控制流

1. 对外接入层只做协议转译、认证上下文绑定、idempotency 与错误 envelope，不直接驱动 runtime，不直接调用 middleware。
2. 会话与任务管理层是 Run / Task / Session 的事实源；Run 状态写入只能通过 `RunRepository.updateIfNotTerminal(...)` 或 create-only save 路径。
3. 内部事件队列当前是设计边界；其控制含义是把 cancel、resume、suspend、S2C、payload、heartbeat 等意图绑定到三轨物理通道，而不是提前选择某个单一 MQ。
4. 任务中心控制层决定启动、暂停、恢复、取消、middleware short-circuit、Fast / Slow Path 分流、S2C callback、child-run join，但状态落库仍回到会话与任务管理层。
5. 引擎调度与执行模块通过 `EngineRegistry.resolve(envelope)` 严格匹配 engine，不让 engine adapter 反向决定 Run / Task / Session 状态模型。
6. 翻译与工具拦截模块塑形模型与工具调用，但不把 ChatAdvisor 伪装成 RuntimeMiddleware。

## 5. 按模块归类的特性清单

### 5.1 Access Layer / 对外接入层

| 特性 ID | 特性分类 | 覆盖场景 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
|---|---|---|---|---|---|---|---|
| AS-L1-F01 | 协议入口与 cursor | S1、S2、S5 | 接收 HTTP / future gRPC / future A2A / future MQ ingress，把外部请求转成内部 Run / Task 创建或查询 / 取消 / 恢复请求；长周期任务立即返回 Task Cursor。 | 输入：请求 payload、tenant、identity、idempotency key、trace。输出：RunResponse、TaskCursor、error envelope。 | 会话与任务管理层、任务中心控制层。 | schema invalid、unsupported protocol、long-running connection misuse。 | A2A Java Task API、Conductor task query / update、LangGraph hosted run/thread API。 |
| AS-L1-F02 | tenant / auth / trace / idempotency 绑定 | S1-S5 | 在请求进入状态事实源之前完成 JWT tenant claim cross-check、TenantContextFilter、IdempotencyHeaderFilter、TraceExtractFilter 与 request body hash 绑定。 | 输入：headers、JWT、body hash。输出：tenant-bound request context、idempotency decision、trace id。 | 会话与任务管理层。 | cross-tenant、idempotency hit、idempotency conflict、idempotency body drift。 | Spring Security filter chain、A2A request context、Conductor idempotent task update。 |
| AS-L1-F03 | Agent / capability 公开边界 | S3、S4 | 对外公开 agent identity、协议能力、streaming / callback / tool support 与 peer collaboration 能力，但不让能力声明改变内部 strict engine matching。 | 输入：agent metadata、engine capability summary。输出：AgentCard / capability response。 | 引擎调度与执行模块、翻译与工具拦截模块。 | capability mismatch、unsupported peer feature、stale capability advertisement。 | A2A AgentCard、AgentScope metadata、OpenAI Agents handoff metadata。 |
| AS-L1-F04 | cancel / query / resume 入口 | S2、S4、S5 | 提供 Run 查询、取消、resume 的协议入口，并把所有控制请求转为 tenant-bound service call。 | 输入：runId、tenant、resume payload、cancel actor。输出：Run status、resume accepted / rejected、cancel result。 | 会话与任务管理层、任务中心控制层、内部事件队列。 | cross-tenant cancel、same-terminal idempotent cancel、different-terminal illegal transition、resume schema invalid。 | Temporal signal/query、Conductor workflow/task resource、A2A input_required resume。 |

### 5.2 Session & Task Manager / 会话与任务管理层

| 特性 ID | 特性分类 | 覆盖场景 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
|---|---|---|---|---|---|---|---|
| AS-L1-F05 | Run execution-state 事实源 | S1-S5 | 拥有 Run aggregate、RunStatus DFA、RunStateMachine validation 与 `RunRepository.updateIfNotTerminal(...)` 原子 CAS。 | 输入：create request、状态转移意图。输出：Run record、transition result、audit material。 | 对外接入层、任务中心控制层、内部事件队列。 | illegal transition、cancel-vs-complete race、terminal no-op、engine_mismatch terminal failure。 | Temporal workflow history 的单事实源原则、Conductor durable task state。 |
| AS-L1-F06 | Task control-state 事实源 | S1-S5 | 维护协议可见 Task control state，表达 done-or-not、why-stopped、input_required、completed、failed 等对外控制语义。 | 输入：Run 状态投影、协议状态变化。输出：Task state、whyStopped、cursor status。 | 对外接入层、会话 / Run 子域、任务中心控制层。 | Task / Run 状态漂移、input_required 与 RunStatus 混同。 | A2A TaskState、A2A TaskStore final / interrupted 谓词。 |
| AS-L1-F07 | Session context-state 事实源 | S1-S4 | 维护 conversation messages、variables、Session projection 与 ContextProjector 输入，支撑多 Run 共享上下文。 | 输入：conversation update、Run-to-Session projection、memory reference。输出：Session snapshot、InjectedContext source。 | 翻译与工具拦截模块、任务中心控制层。 | concurrent memory mutation、stale context projection、cross-tenant session read。 | LangChain4j `@MemoryId` 风险提示、AgentScope externalized session/state。 |
| AS-L1-F08 | tenant-first persistence 与 lifecycle audit | S1-S5 | 所有 Run / Task / Session / idempotency / lifecycle audit 记录携带 tenantId，并在 durable backend 中遵守 RLS；状态变化可投影为 lifecycle audit 与 RunEvent。 | 输入：tenant-bound aggregate changes。输出：RLS-bound records、audit rows、event source material。 | 内部事件队列、物理部署面、对外接入层。 | RLS bypass、tenant inference、audit loss、event without tenant。 | Multi-tenant workflow services、Temporal namespace isolation、Conductor task visibility。 |

### 5.3 Internal Event Queue / 内部事件队列

> 当前 canonical L1 将 Internal Event Queue / 内部事件队列定位为设计边界，代码目录尚未落地。本文只描述模块应承担的功能边界，避免把它误写成已经存在的运行时代码。

| 特性 ID | 特性分类 | 覆盖场景 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
|---|---|---|---|---|---|---|---|
| AS-L1-F09 | RunEvent envelope 与 channel routing | S1-S5 | 为 RunCreated、RunStateTransition、SuspendRequested、ResumeRequested、S2C、ChildRun、CancelRequested、TerminalTransition 等事件声明 envelope 与 routing。 | 输入：状态 / 控制边界事件。输出：routeable RunEvent envelope。 | 会话与任务管理层、任务中心控制层、agent-bus。 | event missing tenant、wrong channel、payload over inline cap。 | AutoGen message envelope、Temporal event history、Conductor task event。 |
| AS-L1-F10 | Producer / Consumer / Lease / Ack / Retry / DeadLetter | S2-S5 | 将 event publication 与 consumption 拆分，定义 lease、ack、retry、dead-letter、heartbeat 与 dedup 关系。 | 输入：RunEvent、control signal、delivery receipt。输出：ack、retry decision、dead-letter record。 | 任务中心控制层、会话与任务管理层、agent-bus。 | at-least-once duplicate、consumer crash、poison message、lease expiry。 | Conductor worker poll/update/dead-letter、Temporal worker task queue。 |
| AS-L1-F11 | 三轨物理通道绑定 | S2-S5 | 把 cancel / resume / suspend / S2C request 绑定到 control，把 payload / transition / S2C response / child completion 绑定到 data，把 heartbeat / tick 绑定到 rhythm。 | 输入：event intent、payload size、timer signal。输出：control / data / rhythm channel operation。 | 任务中心控制层、物理部署面、agent-bus。 | control starvation、data congestion、timer loss、durability tier 混同。 | Temporal signal/timer separation、Conductor queue visibility、high-priority control channels。 |
| AS-L1-F12 | 长周期节奏与可观测性 | S2、S3、S4 | 为 timeout、deadline、resume sweep、heartbeat、queue lag、retry count、dead-letter count 提供模块边界。 | 输入：rhythm tick、lease state、queue metrics。输出：timeout signal、observability metric、retry/dead-letter signal。 | 任务中心控制层、会话与任务管理层、物理部署面。 | timeout 未触发、resume 漏扫、queue lag 不可见。 | Temporal timers、Conductor task timeout / retry metrics。 |

### 5.4 Task-Centric Control Layer / 任务中心控制层

| 特性 ID | 特性分类 | 覆盖场景 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
|---|---|---|---|---|---|---|---|
| AS-L1-F13 | Orchestrator control loop | S1-S4 | 根据 Run / Task / Session、EngineEnvelope、routing predicate 与 middleware result 决定启动、继续、暂停、恢复、失败或终止。 | 输入：RunContext、EngineEnvelope、HookOutcome、SuspendSignal、executor result。输出：dispatch decision、transition intent、resume intent。 | 会话与任务管理层、引擎调度与执行模块、内部事件队列。 | engine mismatch、executor failure、unexpected SuspendSignal、resume context missing。 | LangGraph runner loop、OpenAI Agents runner、Temporal workflow execution loop。 |
| AS-L1-F14 | RuntimeMiddleware governance | S2、S4 | 在 HookPoint.before_tool / after_tool / before_llm / before_resume 等边界执行 policy、quota、memory governance、sandbox routing、observability 与 failure handling。 | 输入：HookPoint event、RunContext、tool/model metadata。输出：Proceed / ShortCircuit / Fail、audit signal。 | 引擎调度与执行模块、翻译与工具拦截模块、agent-middleware。 | middleware fail、short-circuit without audit、sandbox grant over-wide、quota exhausted。 | Spring AI Advisor 的可组合思想、LangChain4j ToolExecutor filter、Semantic Kernel plugin filter。 |
| AS-L1-F15 | Suspend / resume 语义 | S2、S3、S4 | 捕获 child-run、S2C callback、tool-await 等 checked suspension，把 Run 转为 SUSPENDED，并在条件满足时恢复。 | 输入：SuspendSignal、resume payload、child terminal status、S2C response。输出：suspend event、resume event、transition intent。 | 会话与任务管理层、内部事件队列、引擎调度与执行模块、对外接入层。 | client timeout、schema invalid、peer failure、resume re-auth failure、checkpoint missing。 | LangGraph interrupt/resume、Temporal signal、Conductor human task、OpenAI Agents interruption。 |
| AS-L1-F16 | cancel race 分类 | S5 | 对 cancel winner / loser、same-terminal、different-terminal、active-to-cancelled 做确定分类，并保证响应码与 audit event 一致。 | 输入：cancel actor、pre-CAS Run、post-CAS Run。输出：200 / 409 / 404、CancelRequestedEvent、terminal transition if won。 | 对外接入层、会话与任务管理层、内部事件队列。 | cancel-vs-complete race、duplicate cancel、cross-tenant cancel、terminal conflict。 | Workflow engine CAS / optimistic transition、Conductor task terminal update。 |
| AS-L1-F17 | Fast / Slow Path 与长期运行治理 | S1、S2、S4 | 根据 wall-clock、external input、S2C、A2A、deployment locus 等判断执行路径；Fast-Path 只省略中间 checkpoint，不省略 tenant、RLS、metadata persistence。 | 输入：run metadata、routing policy、execution estimate。输出：FastPath / SlowPath decision、mid-execution upgrade。 | 会话与任务管理层、引擎调度与执行模块、内部事件队列。 | long-running misclassification、Fast-Path overrun、checkpoint requirement drift。 | Temporal durable execution、LangGraph checkpoint、OpenAI Agents long-running run step loop。 |

### 5.5 Engine Dispatch & Execution / 引擎调度与执行模块

| 特性 ID | 特性分类 | 覆盖场景 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
|---|---|---|---|---|---|---|---|
| AS-L1-F18 | EngineRegistry strict matching | S1-S4 | 每次执行都通过 `EngineRegistry.resolve(envelope)`，按 `engine_type` 找到唯一 ExecutorAdapter；不能绕过 registry 或按 Java subtype 私自分派。 | 输入：EngineEnvelope。输出：ExecutorAdapter 或 EngineMatchingException。 | 任务中心控制层。 | engine_type mismatch、missing adapter、capability mismatch。 | Spring AI model registry、LangGraph compiled graph registry、OpenAI Agents runner selection。 |
| AS-L1-F19 | ExecutorAdapter lifecycle | S1-S4 | 统一 graph executor、agent loop、未来 actor runtime、crew orchestration、kernel process 的 execute / resume / stream / suspend 边界。 | 输入：RunContext、resume payload、engine-specific config。输出：result、stream chunk、SuspendSignal、failure。 | 任务中心控制层、翻译与工具拦截模块、agent-execution-engine。 | executor crash、unsupported resume、stream interruption、external runtime unavailable。 | LangGraph4j graph runtime、AgentScope Runtime Runner、CrewAI Flow、AutoGen runtime。 |
| AS-L1-F20 | EngineHookSurface | S2、S4 | executor 需要触发 tool / model / resume / checkpoint 边界时，只能向任务中心控制层发 HookPoint event，不能直接调用 RuntimeMiddleware。 | 输入：engine-internal hook boundary。输出：HookPoint event。 | 任务中心控制层、翻译与工具拦截模块。 | direct middleware call、missing HookPoint、hook result ignored。 | LangChain4j tool invocation callback、Semantic Kernel function filter。 |
| AS-L1-F21 | compute snapshot / checkpoint handoff | S2、S3、S4 | 将执行快照作为 compute snapshot 交给控制层治理，不能吞并 Session、Memory 或 workflow history 主权。 | 输入：parentNodeKey、resumePayload、executor snapshot。输出：snapshot reference、resume-compatible payload。 | 任务中心控制层、会话与任务管理层。 | snapshot incompatible、memory/session ownership blur、resume payload loss。 | LangGraph checkpoint saver、Temporal history 作为边界参照。 |

### 5.6 Translation & Tool-Intercept / 翻译与工具拦截模块

| 特性 ID | 特性分类 | 覆盖场景 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
|---|---|---|---|---|---|---|---|
| AS-L1-F22 | Context projection 与 prompt construction | S1-S4 | 把 Session context、variables、memory projection 转成 InjectedContext，再通过 PromptTemplate 形成模型输入。 | 输入：Session projection、template variables、agent definition。输出：InjectedContext、RenderedPrompt。 | 会话与任务管理层、引擎调度与执行模块。 | stale context、missing variable、cross-tenant memory read、prompt schema drift。 | Spring AI PromptTemplate、LangChain4j memory / prompt injection、AgentScope state externalization。 |
| AS-L1-F23 | structured output 与 result interpretation | S1、S2 | 将模型或 engine 输出转换为 typed domain object、tool result 或 Run terminal result。 | 输入：model output、schema、tool result。输出：typed result、conversion error、terminal payload。 | 引擎调度与执行模块、任务中心控制层。 | schema invalid、partial output、tool result mismatch。 | Spring AI StructuredOutputConverter、LangChain4j structured output、Semantic Kernel function result。 |
| AS-L1-F24 | ChatAdvisor / tool shaping | S2、S4 | 在 model-call boundary 完成 request decoration、tool-call shaping、response interpretation；它可以与 RuntimeMiddleware 组合，但不替代 RuntimeMiddleware。 | 输入：ChatClient request、tool definition、model response。输出：shaped request、interpreted response、tool-call descriptor。 | 任务中心控制层、引擎调度与执行模块、agent-middleware。 | advisor short-circuit 与 runtime policy 冲突、tool-call escape、model-call exception。 | Spring AI ChatAdvisor、LangChain4j ToolExecutor、Semantic Kernel plugin filter。 |
| AS-L1-F25 | 模型 / 工具 / invocation profile | S1-S4 | 吸收 Spring AI、LangChain4j、Semantic Kernel、OpenAI Agents 等 invocation kernel 的差异，形成服务内可治理的 profile。 | 输入：model config、tool schema、invocation options。输出：normalized invocation profile。 | 引擎调度与执行模块、任务中心控制层。 | unsupported model option、tool schema drift、profile bypasses governance。 | Spring AI ChatClient、LangChain4j AiServices、OpenAI Agents tool model、Semantic Kernel plugin model。 |

## 6. 正交性检查

| 边界 | 正确切分 | 错误切分 |
|---|---|---|
| Run vs Task vs Session | Run 管 execution state；Task 管 protocol/control state；Session 管 context state。 | 用一个 StateStore 同时吞并 Run、Task、Session。 |
| 任务中心控制层 vs 会话与任务管理层 | 任务中心控制层提出状态转移意图；会话与任务管理层执行 CAS 并持久化。 | Orchestrator 或 ExecutorAdapter 直接写 Run status。 |
| 内部事件队列 vs agent-bus | 内部事件队列定义 service 内事件意图与 routing；agent-bus 提供物理通道。 | 把 Layer 3 写成某个具体 MQ 或把三轨通道写成 durability mode。 |
| RuntimeMiddleware vs ChatAdvisor | RuntimeMiddleware 处理 Run-aware HookPoint；ChatAdvisor 处理 model-call boundary。 | 把两者都叫 tool interceptor 并放在同一模块。 |
| Engine adapter vs service state model | Engine adapter 被 EngineRegistry 严格匹配并执行；服务状态模型由 Agent Service 拥有。 | 让 LangGraph、OpenAI Agents、CrewAI 等 SDK runner 反向决定 Run / Task / Session。 |
| Checkpoint vs Session / Memory | checkpoint 是 compute snapshot；Session / Memory 是上下文和知识事实源。 | 用 checkpoint 替代 Session projection 或 Memory mutation discipline。 |

## 7. 多角度反思

### 7.1 业务完整性

S1-S5 覆盖了入口创建、同步完成、长周期执行、工具调用、peer collaboration、客户端能力回调、取消、恢复、终态分类。若 AS-L1-F01..F25 都成立，系统具备从请求进入到状态终结的闭环：外部入口可返回 cursor，状态事实源可防止 race，控制层可处理暂停 / 恢复，执行层可适配异构 engine，翻译层可治理模型与工具调用。

### 7.2 异常完整性

异常覆盖不依赖某个单点兜底，而是分布在模块边界：对外接入层处理 cross-tenant / idempotency / schema；会话与任务管理层处理 CAS / illegal transition；内部事件队列处理 retry / dead-letter / lease；任务中心控制层处理 middleware fail / SuspendSignal / timeout / race；引擎调度与执行模块处理 adapter mismatch / executor failure；翻译与工具拦截模块处理 structured output / tool-call / prompt drift。

### 7.3 数据流完整性

所有跨模块数据都应携带 tenantId、traceId、runId 或 taskId，且进入持久化或事件通道时不能丢失身份。RunEvent、S2cCallbackEnvelope、IngressEnvelope、ToolInvocationRequest 等都必须避免匿名事件、tenant inference 与 payload 无界膨胀。

### 7.4 控制流完整性

控制流只允许一个方向的主权链：入口规范化请求，会话与任务管理层创建事实源，任务中心控制层提出控制决策，引擎调度与执行模块执行，翻译与工具拦截模块塑形 invocation，结果再回到任务中心控制层与会话与任务管理层。任何“入口直接调 Runtime”“engine 直接写状态”“ChatAdvisor 直接执行 runtime policy”的捷径都会破坏架构。

### 7.5 业界能力基线

与 OSS 平均能力相比，本文清单至少覆盖：A2A 的 Task / AgentCard / input_required，Temporal 的 durable history / signal / timer，Conductor 的 worker poll / retry / dead-letter / human task，LangGraph 的 interrupt / resume / checkpoint，Spring AI / LangChain4j / Semantic Kernel 的 model-tool-function invocation kernel，AgentScope / AutoGen / CrewAI / OpenAI Agents 的 runtime / actor / handoff / runner 模式。Agent Service 不复制这些项目，而是把它们的成熟模式收敛进 Java 服务控制面。

## 8. 过时或应删除的旧特性表达

| 旧表达 | 最新判断 | 替代表达 |
|---|---|---|
| “Task as scheduling core = rename Run” | 拒绝 | Task 是 control state；Run 是 execution state；两者共存。 |
| “InterruptSignal” 作为 Java 机制名 | 拒绝 | 使用 `SuspendSignal`；Interrupt 仅为 glossary synonym。 |
| “Internal Event Queue = one queue + three storage modes” | 拒绝 | 三轨 physical channel + per-channel durability tier。 |
| “Fast-Path 不强制持久化” | 拒绝 | Fast-Path 不强制 checkpoint；Run / Task metadata 与 RLS persistence 仍必须存在。 |
| “RuntimeMiddleware 与 ChatAdvisor 都是 Shadow Tool Interceptor” | 拒绝 | RuntimeMiddleware 属于任务中心控制层；ChatAdvisor 属于翻译与工具拦截模块；二者组合但不等价。 |
| “Engine adapter 决定服务状态模型” | 拒绝 | Engine 只能被适配；Run / Task / Session 状态模型由 Agent Service 主权拥有。 |
| “A2A TaskStore 可替代内部 RunRepository” | 拒绝 | A2A TaskStore 只覆盖 protocol control state，不能承载 Run execution state、tenant/RLS/cancel race。 |
| “Workflow history 可替代 Run / Task / Session” | 拒绝 | Temporal / Conductor 可借鉴 durable execution，但不能替代 Agent-native 聚合边界。 |

## 9. 最终判断

Agent Service 的模块功能应以业务场景闭环为中心，以模块全称归属为组织方式，以 Run / Task / Session 三聚合、RunEvent、三轨通道、RuntimeMiddleware、EngineRegistry、ChatAdvisor / ContextProjector 等核心结构为稳定锚点。本文给出的 AS-L1-F01..F25 不是按优先级排列的一次性实现清单，也不是额外状态体系；它是一份 L1 模块能力地图，用来说明 Agent Service 在完成这些模块特性后如何支撑正常路径、异常路径、数据流、控制流与业界平均能力基线。