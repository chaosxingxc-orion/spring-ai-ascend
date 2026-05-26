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

Agent Service 是一个 **Agent-native control service**，不是 Agent SDK、Workflow Engine、ChatClient、A2A starter 或 Runtime wrapper 的薄封装。它的核心职责不是“把请求转给某个 Agent 执行”，而是把多协议入口、client 连接方式、Run / Task / Session 状态、长期任务恢复、三方 Agent 调度、client-hosted skills、sub-agent 委派、模型与工具配置、事件队列、RuntimeMiddleware 与 engine adapter 统一纳入服务端主权边界。

上一版清单只按 canonical S1-S5 粗粒度映射模块能力，遗漏了企业 Agent 服务中最常见的细分业务场景：SSE 与非 SSE 接入、断链恢复、上下文压缩、长任务续跑与切换、失败回滚重试、三方 Agent 原地恢复、client 提供技能、sub-agent 委派、模型 / 三方 Agent / client 配置归属。遗漏的根因是把 S1-S5 当作最终场景列表，而不是把它们当作 L1 canonical anchor 后继续展开可运行的业务闭环。

本文将 S1-S5 展开为 24 个 L1 场景簇，并据此重新生成模块特性清单。清单不引入实现状态、优先级或 L2 建议；它只说明每个完整模块必须拥有的能力、输入输出、协作边界、异常闭环和业界参照。若这些能力成立，系统应能覆盖普通同步调用、流式访问、非流式轮询、长任务、断链恢复、上下文治理、任务切换、失败重试、三方 Agent 恢复、client 技能调用、多 Agent 委派和关键配置治理。

## 2. 分解原则

| 原则 | 含义 | 防止的问题 |
| --- | --- | --- |
| canonical anchor + 场景簇展开 | S1-S5 是权威锚点；能力清单必须继续展开成企业 Agent 服务可验证的细分场景。 | 只覆盖 demo path，遗漏断链、恢复、retry、handoff、client skill、三方 Agent 等真实路径。 |
| 模块全称归属 | 使用 Access Layer / 对外接入层、Session & Task Manager / 会话与任务管理层等完整模块名。 | 用 L1/L2/Layer 代替真实模块，导致 ownership 不清。 |
| 事实源单一 | Run execution state、Task control state、Session context state、RunEvent stream、configuration authority 各有单一主权或明确只读引用。 | StateStore 泛化、Run/Task 混同、adapter 私写状态、配置散落在请求体或 engine 内。 |
| 接入方式显式建模 | SSE、非 SSE polling、webhook / push、future client SDK、Mode A / Mode B 部署都要有边界。 | 把“client 如何拿到结果、断线后如何恢复”留成隐含实现。 |
| 恢复优先于重调度 | 长任务、三方 Agent、sub-agent、S2C callback 中断后优先恢复原 Run / child Run / remote invocation，而不是盲目创建新 Agent。 | 企业服务中重复扣费、重复副作用、审计断裂和状态漂移。 |
| 数据流与控制流并重 | 同时覆盖 request、cursor、Run / Task / Session、RunEvent、checkpoint、remote invocation handle、model/tool profile、callback payload 的流转。 | 只有 happy path，没有 race、retry、dead-letter、context overflow、resume authorization。 |
| 业界基线校准 | 用 OSS 证明哪些能力是成熟 agent/workflow 服务的平均线。 | 只实现普通 SDK runner，缺少长期运行、queue、checkpoint、interrupt、worker、human callback、handoff 等平台能力。 |

## 3. 扩展业务场景簇

下表中的场景簇仍锚定 canonical `scenarios.md` 的 S1-S5，不新增 canonical authority；它们用于把 L1 模块能力拆到足够指导后续设计的粒度。

| 场景簇 ID | canonical anchor | 业务场景 | 正常闭环 | 异常闭环 |
| --- | --- | --- | --- | --- |
| AS-SC01 | S1 | 同步短请求 | client 调用 `POST /v1/runs`，Access Layer 绑定 tenant / idempotency / trace，Run 快速完成并返回结果。 | schema invalid、idempotency conflict、engine mismatch、cross-tenant collapse。 |
| AS-SC02 | S1 / S2 | 非 SSE 长任务轮询 | Access Layer 返回 Task Cursor；client 通过 query endpoint 轮询 Run / Task 状态。 | client 轮询中断不影响 Run；query 必须 re-auth；terminal 状态幂等可查。 |
| AS-SC03 | S1 / S2 | SSE / streaming 接入 | client 请求流式状态或 token / step event；Access Layer 只承载 stream boundary，Run 状态仍由 Session & Task Manager 维护。 | SSE 断链后 client 以 cursor / event offset / runId 恢复查询；不能因为连接断开取消 Run。 |
| AS-SC04 | S1 / S2 / physical | 直连边界 | client 可以直连 Agent Service 的 Access Layer；不能直连 engine adapter、RunRepository、middleware 或 agent-bus。Mode B 业务侧部署仍保持相同服务边界。 | direct-to-engine、direct-to-queue、missing tenant binding 必须被拒绝或不可达。 |
| AS-SC05 | S1 / S2 | 多协议入口收敛 | HTTP、future gRPC、future A2A、future MQ ingress 收敛为同一 Run / Task / Session 创建与控制语义。 | 协议字段差异不能产生不同状态机；unsupported protocol 返回边界错误。 |
| AS-SC06 | S1-S5 | 入口幂等与重复提交 | 同一 tenant + idempotency key + request hash 返回同一创建结果或可解释冲突。 | body drift、duplicate submit、late retry 不能创建重复 Run。 |
| AS-SC07 | S1 / S2 | 会话断链后上下文恢复 | client 使用 sessionId / runId / taskId 重新进入服务，Session & Task Manager 恢复 Session projection 与 Run / Task 可见状态。 | session 不存在、tenant mismatch、projection lag、stale cursor 必须有确定响应。 |
| AS-SC08 | S2 | 上下文超长后压缩 | Translation & Tool-Intercept 根据 Session projection 生成可控上下文窗口；Session & Task Manager 保留原始 context state 与压缩投影边界。 | compression loss、prompt overflow、memory mutation race、cross-tenant memory read 必须被阻断或显式失败。 |
| AS-SC09 | S2 | 长任务持续运行 | Task Cursor 先返回；Run 在 control / data / rhythm 的事件与 tick 下继续执行；client 可查询或订阅进度。 | timeout、heartbeat lost、queue lag、executor crash 进入 SuspendSignal / RunEvent / retry / dead-letter 闭环。 |
| AS-SC10 | S2 | 任务执行中途切换执行位置 | Run 从 Fast-Path 升级 Slow-Path，或在 Mode A / Mode B、实例、worker 间切换执行位置；checkpoint / parentNodeKey / RunEvent 提供恢复锚点。 | deployment locus 变化、snapshot 不兼容、resume payload 丢失不能绕过 Layer 2 CAS。 |
| AS-SC11 | S2 | 失败后回滚到前置状态再重试 | Run 使用 attemptId、parentNodeKey、checkpoint reference 与 RunEvent history 表达可重试边界；重试是同一 Run 的受控 attempt 或明确 child Run。 | 非幂等 tool side effect、terminal Run、checkpoint missing、retry budget exhausted 必须进入确定失败或人工介入状态。 |
| AS-SC12 | S2 / S5 | 取消与完成竞态 | cancel、complete、fail、expire 同时发生时，只能由 `RunRepository.updateIfNotTerminal(...)` 决定 winner。 | loser 重新读取 post-CAS 状态；same-terminal 幂等成功；different-terminal 返回 illegal transition。 |
| AS-SC13 | S4 | client-hosted skill 调用 | engine 需要 client 本地文件、UI 确认、浏览器能力或私有工具时，抛出 `SuspendSignal.forClientCallback(...)`，通过 S2C envelope 让 client 执行后 resume。 | client timeout、callbackId mismatch、response schema invalid、resume re-auth failure 不能让 engine 私自继续。 |
| AS-SC14 | S4 | client 技能授权与能力声明 | Access Layer 公开 / 接收 client capability；Task-Centric Control Layer 在调用前做 policy / quota / sandbox / audit；Translation & Tool-Intercept 只塑形 tool call。 | client 声称能力但不可用、能力越权、结果不可验证时进入 suspend failure 或 controlled retry。 |
| AS-SC15 | S3 / S4 | 三方 Agent 调用 | Task-Centric Control Layer 派生 child Run 或 outbound invocation；Access Layer / IngressGateway 处理 peer / third-party protocol；Engine Dispatch & Execution 只通过 adapter 执行。 | peer unreachable、remote auth failure、remote error envelope、child terminal failure 保留 parentRunId / traceId / tenantId。 |
| AS-SC16 | S3 / S4 | 三方 Agent 中断后原地恢复 | 对三方 Agent 调度时记录 remoteAgentId、remoteThreadId / remoteTaskId / callbackId、adapter profile 与 parentRunId；下次进入优先 resume 原 remote invocation。 | 找不到 remote handle、remote terminal、adapter version drift、remote state lost 时必须显式转为重试、失败或人工处理，不能静默新建 Agent。 |
| AS-SC17 | S3 | Agent 委派 sub-agent | parent Run 创建 child Run，child 继承 tenant / trace / policy envelope，并在 terminal 后把结果回流给 parent Run。 | child timeout、child cancel、child failed、parent cancelled 必须决定 cascade / detach / fail / resume。 |
| AS-SC18 | S3 | 多 Agent / peer collaboration 聚合 | 多个 child Run 或 peer Run 并行或串行返回，Task-Centric Control Layer 负责 join / aggregation / conflict classification。 | partial failure、late result、duplicate child completion、aggregation schema invalid 必须可审计。 |
| AS-SC19 | S1-S4 | 模型配置承载 | model provider、model id、temperature、streaming、structured output、cost / quota profile 由服务内可治理 profile 表达，执行时使用 resolved snapshot。 | 请求体覆盖治理配置、unsupported option、model profile drift、quota exceeded 必须受控。 |
| AS-SC20 | S3 / S4 | 三方 Agent 适配配置承载 | third-party agent adapter、endpoint、auth mode、capability、resume handle schema、timeout / retry policy 由 adapter / agent registry 边界承载。 | adapter 缺失、capability mismatch、resume schema drift、credential scope 错误不能进入执行。 |
| AS-SC21 | S1 / S4 | client 信息与能力配置承载 | client identity、client type、SSE support、callback transport、client-hosted skill list、permission posture 由 Access Layer 与 Agent / Skill registry 输入共同确定。 | client capability stale、callback transport unavailable、permission mismatch 必须在调用前失败。 |
| AS-SC22 | S2 / S4 | 工具 / sandbox / skill 配置承载 | tool schema、skill capacity、sandbox policy、tool allowlist、memory access policy 在 RuntimeMiddleware 与 Translation & Tool-Intercept 边界共同生效。 | tool escape、over-wide sandbox grant、capacity exhausted、policy bypass 进入 audit + controlled failure。 |
| AS-SC23 | S1-S5 | 可观测与审计 | 每个入口、状态转移、suspend/resume、child Run、S2C callback、third-party invocation、terminal transition 都产生 traceable evidence。 | anonymous event、missing tenantId、lost terminal event、payload over inline cap 必须被 gate / runtime contract 捕获。 |
| AS-SC24 | S1-S5 | 配置快照与运行时漂移 | Run 创建时记录必要配置快照或引用；resume / retry 时按原快照恢复，只有显式策略允许升级。 | 配置热更新导致同一 Run 前后行为不可解释、adapter profile 漂移、model 参数漂移必须可检测。 |

## 4. 场景簇到模块协作矩阵

| 场景类别 | 覆盖场景簇 | Access Layer / 对外接入层 | Session & Task Manager / 会话与任务管理层 | Internal Event Queue / 内部事件队列 | Task-Centric Control Layer / 任务中心控制层 | Engine Dispatch & Execution / 引擎调度与执行模块 | Translation & Tool-Intercept / 翻译与工具拦截模块 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 接入与连接模式 | AS-SC01-AS-SC06 | 协议收敛、SSE / polling / query / cancel / resume、tenant / idempotency / trace 绑定、直连边界。 | 创建 Run / Task / Session，维护 cursor 可见状态与幂等记录。 | 为长任务和 stream event 提供 routeable event 边界。 | 判断 Fast / Slow、接入控制请求转执行意图。 | 执行被选中的 engine，不拥有入口连接。 | 生成可流式输出、structured result 与 tool/model payload。 |
| 会话与上下文治理 | AS-SC07-AS-SC08 | re-entry query / resume 入口、client identity re-auth。 | Session context state、Run-to-Session projection、context version、tenant-bound memory reference。 | 可发布 projection lag、resume sweep、context compaction event。 | 决定是否可 resume、是否需要 suspend / fail / retry。 | 消费恢复后的 RunContext 与 snapshot。 | context projection、prompt construction、compression boundary。 |
| 长任务与恢复 | AS-SC09-AS-SC12 | cursor、query、cancel、resume、SSE reconnect。 | Run CAS、attemptId、parentNodeKey、terminal classification、audit。 | control / data / rhythm、timeout、retry、dead-letter、heartbeat。 | checkpoint-aware continuation、execution locus switch、rollback retry、cancel race 分类。 | execute / resume / stream / checkpoint handoff。 | 解释 partial output、tool result、resume payload。 |
| client 能力与 S2C | AS-SC13-AS-SC14 | client capability、callback transport、resume ingress。 | Task input_required / whyStopped、Run suspended state、Session continuity。 | S2C request 走 control，response 走 data，timeout 走 rhythm。 | S2C policy、quota、authorization、resume validation。 | 抛出 client callback suspension 并在 resume 后继续。 | tool-call shaping、response schema interpretation。 |
| 三方 Agent 与 sub-agent | AS-SC15-AS-SC18 | peer / third-party ingress-egress、AgentCard / adapter-facing protocol。 | parentRunId、child Run、remote invocation handle、tenant / trace correlation。 | child completion、remote callback、retry / dead-letter event。 | spawn / join / aggregate / recover / cascade cancel。 | third-party adapter、sub-agent executor、resume same remote invocation。 | remote tool / model payload normalization。 |
| 配置与治理 | AS-SC19-AS-SC24 | client profile、protocol capability publication、safe direct-access boundary。 | resolved config snapshot reference、state-to-config correlation、audit rows。 | channel config、delivery policy、configuration drift events。 | routing policy、RuntimeMiddleware policy, skill capacity, sandbox decision。 | engine / adapter / third-party capability registry and lifecycle. | model profile、tool schema、prompt / output / invocation profile。 |

## 5. 关键配置归属矩阵

配置不能散落在请求体、adapter 私有字段或 prompt 模板里。L1 需要先明确“谁承载配置事实，谁只消费配置”。

| 配置类别 | 主权承载模块 | 只读 / 消费模块 | 必须包含的信息 | 异常闭环 |
| --- | --- | --- | --- | --- |
| client identity 与接入能力 | Access Layer / 对外接入层 | 会话与任务管理层、任务中心控制层 | clientId、tenant binding、auth posture、SSE / polling / callback transport、client-hosted skill advertisement。 | client capability stale、transport unavailable、tenant mismatch。 |
| Agent identity 与服务能力 | Access Layer / 对外接入层 + Engine Dispatch & Execution / 引擎调度与执行模块 | 任务中心控制层、翻译与工具拦截模块 | AgentCard / capability publication、engine_type、supported run modes、streaming / tool / callback / delegation support。 | capability advertisement 与 EngineRegistry strict matching 不一致。 |
| Run 创建时配置快照 | Session & Task Manager / 会话与任务管理层 | 全部执行相关模块 | resolved model profile、engine profile、adapter profile、tool profile、routing posture、client callback posture 的引用或快照。 | resume / retry 时配置漂移不可解释。 |
| 模型信息 | Translation & Tool-Intercept / 翻译与工具拦截模块 | 引擎调度与执行模块、任务中心控制层 | provider、model id、options、streaming support、structured output support、cost / quota tags。 | unsupported option、quota exceeded、provider drift、schema mismatch。 |
| 三方 Agent 适配信息 | Engine Dispatch & Execution / 引擎调度与执行模块 | Access Layer、任务中心控制层、会话与任务管理层 | adapter id、endpoint、auth mode、remoteAgentId、remoteThreadId / remoteTaskId schema、resume token schema、timeout / retry policy。 | 无法恢复同一 remote invocation、credential scope 错误、adapter version drift。 |
| client-hosted skill 信息 | Access Layer / 对外接入层 + Task-Centric Control Layer / 任务中心控制层 | 翻译与工具拦截模块、引擎调度与执行模块 | skill name、capability schema、permission posture、callback transport、result schema、timeout。 | client skill 不可用、越权、结果 schema invalid、callback timeout。 |
| tool / sandbox / skill capacity | Task-Centric Control Layer / 任务中心控制层 | 翻译与工具拦截模块、引擎调度与执行模块 | skill-capacity、sandbox policy、tool allowlist、quota、memory access policy、HookPoint policy。 | policy bypass、over-wide grant、capacity exhausted、audit missing。 |
| channel 与 delivery policy | Internal Event Queue / 内部事件队列 | 会话与任务管理层、任务中心控制层 | control / data / rhythm physical channel、durability tier、lease、ack、retry、dead-letter、inline payload cap。 | control starvation、poison message、payload too large、dead-letter invisible。 |

## 6. 按模块归类的特性清单

### 6.1 Access Layer / 对外接入层

| 特性 ID | 特性分类 | 覆盖场景簇 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F01 | 协议入口收敛 | AS-SC01、AS-SC02、AS-SC05 | 接收 HTTP / future gRPC / future A2A / future MQ ingress，把外部 create / query / cancel / resume / callback 请求收敛为 tenant-bound service request。 | 输入：protocol payload、headers、identity。输出：normalized request、error envelope。 | 会话与任务管理层、任务中心控制层。 | unsupported protocol、schema invalid、protocol-state mismatch。 | A2A Java Task API、Conductor REST/gRPC、LangGraph run/thread API。 |
| AS-L1-F02 | SSE / streaming 接入 | AS-SC03、AS-SC09 | 支持 SSE 或等价 streaming 边界，把 token / step / RunEvent projection 发送给 client，但连接不是 Run 生命周期事实源。 | 输入：runId、cursor、stream options。输出：stream event、event offset、disconnect signal。 | 内部事件队列、会话与任务管理层、翻译与工具拦截模块。 | SSE disconnect、slow consumer、event replay gap、stream backpressure。 | A2A streaming、OpenAI Agents stream events、LangGraph streaming。 |
| AS-L1-F03 | 非 SSE polling / query 接入 | AS-SC02、AS-SC07、AS-SC09 | 为不能保持流连接的 client 提供 cursor polling、Run / Task query 与 terminal result retrieval。 | 输入：taskId / runId / cursor、tenant。输出：status snapshot、terminal payload、retry-after hint。 | 会话与任务管理层、任务中心控制层。 | stale cursor、cross-tenant query、terminal result expired。 | Conductor task query、Temporal query、A2A task get。 |
| AS-L1-F04 | 直连边界治理 | AS-SC04、AS-SC21 | 允许 client 直连 Agent Service 的 Access Layer 或 Mode B 本地部署入口；拒绝或隔离对 engine adapter、RunRepository、middleware、agent-bus 的直连。 | 输入：client route、deployment mode、auth context。输出：accepted ingress 或 boundary error。 | 物理部署面、会话与任务管理层、引擎调度与执行模块。 | direct-to-engine、missing tenant binding、bypass idempotency。 | AgentScope Runtime endpoint、Spring AI A2A thin controller。 |
| AS-L1-F05 | tenant / auth / trace / idempotency 绑定 | AS-SC01-AS-SC06、AS-SC23 | 在任何状态写入前完成 JWT tenant claim cross-check、TenantContextFilter、IdempotencyHeaderFilter、TraceExtractFilter 与 request hash 绑定。 | 输入：JWT、headers、body hash。输出：tenant-bound request context、idempotency decision、trace id。 | 会话与任务管理层。 | cross-tenant、idempotency conflict、body drift、missing trace。 | Spring Security filter chain、Conductor idempotent task update。 |
| AS-L1-F06 | client capability 与 callback transport | AS-SC13、AS-SC14、AS-SC21 | 接收并公开 client 支持的 SSE、polling、webhook、local file、browser action、human approval、client-hosted skill 等能力。 | 输入：client metadata、capability advertisement。输出：client profile、callback route、capability response。 | 任务中心控制层、翻译与工具拦截模块。 | stale capability、callback unavailable、permission mismatch。 | A2A AgentCard / push notification、OpenAI Agents tool approval。 |
| AS-L1-F07 | cancel / resume / callback ingress | AS-SC12-AS-SC14 | 提供取消、恢复和 client callback 响应入口，并把所有控制请求转为 tenant-bound control service call。 | 输入：runId、callbackId、resume payload、cancel actor。输出：resume accepted / rejected、cancel result。 | 会话与任务管理层、任务中心控制层、内部事件队列。 | resume re-auth failure、callbackId mismatch、same-terminal cancel、illegal transition。 | Temporal signal、Conductor human task update、A2A input_required resume。 |
| AS-L1-F08 | Agent / peer capability publication | AS-SC15、AS-SC18、AS-SC20 | 公开 Agent / peer 可见能力、AgentCard、supported protocol 与 delegation 能力，但不让公开能力削弱内部 EngineRegistry strict matching。 | 输入：agent metadata、engine capability summary、adapter profile。输出：capability response、peer-facing metadata。 | 引擎调度与执行模块、翻译与工具拦截模块。 | stale capability、peer unsupported feature、capability mismatch。 | A2A AgentCard、AgentScope metadata、OpenAI Agents handoff metadata。 |

### 6.2 Session & Task Manager / 会话与任务管理层

| 特性 ID | 特性分类 | 覆盖场景簇 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F09 | Run execution-state 事实源 | AS-SC01-AS-SC12 | 拥有 Run aggregate、RunStatus DFA、RunStateMachine validation 与 `RunRepository.updateIfNotTerminal(...)` 原子 CAS。 | 输入：create request、transition intent。输出：Run record、transition result、audit material。 | 对外接入层、任务中心控制层、内部事件队列。 | illegal transition、cancel-vs-complete race、terminal no-op、engine_mismatch。 | Temporal workflow history、Conductor durable task state。 |
| AS-L1-F10 | Task control-state 事实源 | AS-SC02、AS-SC03、AS-SC07、AS-SC13 | 维护协议可见 Task control state，表达 submitted / working / input_required / completed / failed 与 whyStopped。 | 输入：Run projection、suspend reason、terminal result。输出：Task state、cursor status、whyStopped。 | 对外接入层、任务中心控制层。 | Task / Run drift、input_required 与 RunStatus 混同。 | A2A TaskState final / interrupted predicates。 |
| AS-L1-F11 | Session context-state 事实源 | AS-SC07、AS-SC08、AS-SC13 | 维护 conversation messages、variables、Session projection、Run-to-Session relation 与 ContextProjector 输入，支撑断链后上下文恢复。 | 输入：conversation update、runId、sessionId、memory reference。输出：Session snapshot、context projection source。 | 翻译与工具拦截模块、任务中心控制层。 | stale projection、concurrent memory mutation、cross-tenant session read。 | AgentScope externalized session/state、LangChain4j `@MemoryId` risk。 |
| AS-L1-F12 | context version 与压缩投影锚点 | AS-SC08、AS-SC24 | 保存原始会话事实与压缩 / summary / projection 的版本关系，让上下文超长处理可解释、可恢复、可审计。 | 输入：context window pressure、messages、compression result。输出：projection version、summary reference、audit link。 | 翻译与工具拦截模块、内部事件队列。 | compression loss、prompt overflow、summary stale、memory mutation race。 | OpenAI Agents sessions、LangGraph state snapshot、CrewAI memory scopes。 |
| AS-L1-F13 | attempt / checkpoint / rollback reference | AS-SC10、AS-SC11 | 以 attemptId、parentNodeKey、checkpoint reference、RunEvent correlation 表达任务切换、回滚到前置状态再重试的事实锚点。 | 输入：retry intent、checkpoint id、failure reason。输出：new attempt marker、rollback reference、audit event material。 | 任务中心控制层、引擎调度与执行模块、内部事件队列。 | checkpoint missing、non-idempotent side effect、retry budget exhausted。 | Temporal retry/history、LangGraph checkpoint、Conductor retry。 |
| AS-L1-F14 | parent / child / remote invocation correlation | AS-SC15-AS-SC18 | 保存 parentRunId、childRunId、remoteAgentId、remoteTaskId / remoteThreadId、callbackId、traceId 与 tenantId 的关联。 | 输入：spawn request、third-party invocation handle、child terminal result。输出：correlation record、join material。 | 任务中心控制层、引擎调度与执行模块、对外接入层。 | orphan child、remote handle lost、duplicate child completion、tenant mismatch。 | A2A task id、OpenAI Agents handoff、AutoGen AgentId / message id。 |
| AS-L1-F15 | 配置快照 / 引用事实源 | AS-SC19-AS-SC24 | 在 Run 创建时记录 resolved model / engine / adapter / client / tool / routing profile 的快照或稳定引用，供 resume / retry 使用。 | 输入：resolved configuration set。输出：config snapshot reference、drift audit material。 | 全部执行相关模块。 | config drift、adapter version mismatch、model option drift、credential scope error。 | Temporal deterministic config discipline、Conductor workflow input snapshot。 |
| AS-L1-F16 | tenant-first persistence 与 lifecycle audit | AS-SC01-AS-SC24 | 所有 Run / Task / Session / idempotency / lifecycle audit 记录携带 tenantId，并在 durable backend 中遵守 RLS；状态变化可投影为 lifecycle audit 与 RunEvent material。 | 输入：tenant-bound aggregate changes。输出：RLS-bound record、audit row、event source material。 | 内部事件队列、物理部署面、对外接入层。 | RLS bypass、anonymous event、tenant inference、audit loss。 | Multi-tenant workflow services、Temporal namespace isolation。 |

### 6.3 Internal Event Queue / 内部事件队列

> 当前 canonical L1 将 Internal Event Queue / 内部事件队列定位为设计边界，代码目录尚未落地。本文只描述模块应承担的能力边界，避免把它误写成已经存在的运行时代码。

| 特性 ID | 特性分类 | 覆盖场景簇 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F17 | RunEvent envelope 与 channel routing | AS-SC09-AS-SC18、AS-SC23 | 为 RunCreated、RunStateTransition、SuspendRequested、ResumeRequested、S2C、ChildRun、CancelRequested、TerminalTransition、third-party callback 等事件声明 envelope 与 routing。 | 输入：state / control boundary event。输出：routeable RunEvent envelope。 | 会话与任务管理层、任务中心控制层、agent-bus。 | missing tenant、wrong channel、payload over inline cap。 | AutoGen message envelope、Temporal event history、Conductor task event。 |
| AS-L1-F18 | Producer / Consumer / Lease / Ack / Retry / DeadLetter | AS-SC09-AS-SC12、AS-SC15-AS-SC18 | 将 event publication 与 consumption 拆分，定义 lease、ack、retry、dead-letter、heartbeat、dedup 与 poison-message 关系。 | 输入：RunEvent、delivery receipt、consumer outcome。输出：ack、retry decision、dead-letter record。 | 任务中心控制层、会话与任务管理层、agent-bus。 | duplicate delivery、consumer crash、lease expiry、poison message。 | Conductor worker poll/update/dead-letter、Temporal worker task queue。 |
| AS-L1-F19 | 三轨物理通道绑定 | AS-SC09-AS-SC18 | 把 cancel / resume / suspend / S2C request / control signal 绑定到 control，把 payload / transition / S2C response / child completion 绑定到 data，把 heartbeat / tick / timeout 绑定到 rhythm。 | 输入：event intent、payload size、timer signal。输出：control / data / rhythm channel operation。 | 任务中心控制层、物理部署面、agent-bus。 | control starvation、data congestion、timer loss、durability tier 混同。 | Temporal signal/timer separation、Conductor queue visibility。 |
| AS-L1-F20 | SSE / polling event projection | AS-SC02、AS-SC03、AS-SC09 | 为 Access Layer 提供可投影到 SSE stream 或 polling snapshot 的事件视图，不把 client connection 当成队列本身。 | 输入：RunEvent stream、cursor offset、query request。输出：stream projection、status projection。 | 对外接入层、会话与任务管理层。 | stream replay gap、slow consumer、polling stale snapshot。 | A2A task event stream、OpenAI Agents stream event。 |
| AS-L1-F21 | resume sweep 与长期节奏 | AS-SC07、AS-SC09、AS-SC16 | 通过 rhythm tick 表达 timeout、deadline、resume sweep、remote invocation check、heartbeat 与 queue lag 观测。 | 输入：tick、lease state、remote handle、deadline。输出：timeout signal、resume check event、metric。 | 任务中心控制层、会话与任务管理层、引擎调度与执行模块。 | missed sweep、remote recovery not attempted、heartbeat lost。 | Temporal timers、Conductor task timeout。 |
| AS-L1-F22 | remote / child completion routing | AS-SC15-AS-SC18 | 为三方 Agent、peer Run、sub-agent child Run 的完成、失败、取消、超时提供回流事件边界。 | 输入：remote callback、child terminal result、peer event。输出：ChildRunCompletedEvent、remote completion event、dead-letter。 | 任务中心控制层、引擎调度与执行模块、对外接入层。 | duplicate callback、late result、orphan child、parent cancelled。 | A2A task update、AutoGen response envelope、CrewAI task output。 |
| AS-L1-F23 | 配置漂移与审计事件 | AS-SC19-AS-SC24 | 对模型、adapter、client capability、tool policy、channel policy 的运行时漂移产生可审计事件。 | 输入：resolved config snapshot、current config、drift detector output。输出：drift event、audit projection。 | 会话与任务管理层、任务中心控制层、引擎调度与执行模块、翻译与工具拦截模块。 | silent config drift、adapter profile mismatch、unsupported hot update。 | Temporal deterministic replay guard、Conductor workflow versioning。 |

### 6.4 Task-Centric Control Layer / 任务中心控制层

| 特性 ID | 特性分类 | 覆盖场景簇 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F24 | Orchestrator control loop | AS-SC01-AS-SC18 | 根据 Run / Task / Session、EngineEnvelope、routing predicate、middleware result、checkpoint 与 remote handle 决定启动、继续、暂停、恢复、失败、重试或终止。 | 输入：RunContext、EngineEnvelope、HookOutcome、SuspendSignal、executor result。输出：dispatch decision、transition intent、resume intent。 | 会话与任务管理层、引擎调度与执行模块、内部事件队列。 | engine mismatch、executor failure、resume context missing、unexpected suspend。 | LangGraph runner loop、OpenAI Agents runner、Temporal workflow execution loop。 |
| AS-L1-F25 | Fast / Slow Path 与连接模式解耦 | AS-SC02-AS-SC04、AS-SC09 | 基于 wall-clock、external input、S2C、A2A、deployment locus 判断执行路径；SSE / polling 是访问模式，不决定 Run 是否 Fast / Slow。 | 输入：run metadata、routing policy、client access mode。输出：FastPath / SlowPath decision、mid-execution upgrade。 | 对外接入层、会话与任务管理层、内部事件队列。 | long-running misclassification、Fast-Path overrun、connection-mode leakage。 | Temporal durable execution、LangGraph checkpoint。 |
| AS-L1-F26 | Suspend / resume 语义 | AS-SC07、AS-SC09、AS-SC13-AS-SC16 | 捕获 client callback、child-run、third-party wait、tool-await、backpressure 等 checked suspension，把 Run 转为 SUSPENDED，并在条件满足时恢复。 | 输入：SuspendSignal、resume payload、child terminal status、remote callback。输出：suspend event、resume event、transition intent。 | 会话与任务管理层、内部事件队列、引擎调度与执行模块、对外接入层。 | timeout、schema invalid、re-auth failure、checkpoint missing、remote handle lost。 | LangGraph interrupt/resume、Temporal signal、Conductor human task。 |
| AS-L1-F27 | RuntimeMiddleware governance | AS-SC13、AS-SC14、AS-SC19-AS-SC22 | 在 HookPoint.before_tool / after_tool / before_llm / before_resume / before_remote_call 等边界执行 policy、quota、memory governance、sandbox routing、observability 与 failure handling。 | 输入：HookPoint event、RunContext、tool/model/client/remote metadata。输出：Proceed / ShortCircuit / Fail、audit signal。 | 引擎调度与执行模块、翻译与工具拦截模块、agent-middleware。 | policy bypass、over-wide sandbox grant、quota exhausted、middleware fail。 | Spring AI Advisor composability、LangChain4j ToolExecutor filter、Semantic Kernel filter。 |
| AS-L1-F28 | rollback / retry / attempt 分类 | AS-SC10、AS-SC11 | 根据 failure kind、checkpoint boundary、side-effect classification、attemptId 与 retry budget 决定同 Run retry、child Run retry、terminal fail 或人工介入。 | 输入：failure reason、checkpoint ref、side-effect marker、attempt policy。输出：retry intent、rollback reference、terminal decision。 | 会话与任务管理层、内部事件队列、引擎调度与执行模块。 | non-idempotent retry、checkpoint missing、retry storm、attempt drift。 | Temporal activity retry、Conductor retry policy、LangGraph checkpoint resume。 |
| AS-L1-F29 | cancel / complete race 分类 | AS-SC12 | 对 cancel winner / loser、same-terminal、different-terminal、active-to-cancelled 做确定分类，并保证响应码、状态和 audit event 一致。 | 输入：cancel actor、pre-CAS Run、post-CAS Run。输出：200 / 409 / 404、transition intent、audit signal。 | 对外接入层、会话与任务管理层、内部事件队列。 | duplicate cancel、cross-tenant cancel、terminal conflict、lost audit。 | Workflow engine CAS / optimistic transition、Conductor task terminal update。 |
| AS-L1-F30 | client-hosted skill 调度 | AS-SC13、AS-SC14、AS-SC21 | 判断何时调用 client 本地文件、浏览器动作、用户确认或私有 skill；把调用表达为 S2C callback，而不是让 engine adapter 直接访问 client。 | 输入：client capability、tool request、policy decision。输出：S2C request、resume expectation、failure decision。 | 对外接入层、内部事件队列、翻译与工具拦截模块。 | client unavailable、permission mismatch、callback timeout、result invalid。 | OpenAI Agents tool approval、Conductor human task、A2A input_required。 |
| AS-L1-F31 | sub-agent / 三方 Agent join 控制 | AS-SC15-AS-SC18 | 负责 spawn、join、aggregation、cascade cancel、partial failure classification 与 parent Run resume。 | 输入：delegation request、child / remote status、aggregation policy。输出：child Run intent、join result、parent transition intent。 | 会话与任务管理层、内部事件队列、引擎调度与执行模块、对外接入层。 | orphan child、partial failure、late result、parent cancelled、remote failure。 | OpenAI Agents handoff、CrewAI multi-agent process、AutoGen routed agents。 |
| AS-L1-F32 | same-remote-invocation recovery | AS-SC16 | 三方 Agent 调用中断后，优先用 remoteAgentId、remoteThreadId / remoteTaskId、callbackId 与 adapter profile 恢复同一个 remote invocation。 | 输入：remote handle、resume policy、adapter profile、Run state。输出：resume remote call、controlled retry、terminal failure。 | 会话与任务管理层、引擎调度与执行模块、内部事件队列。 | remote handle missing、remote terminal、adapter version drift、credential expired。 | Temporal external workflow signal、A2A task resume、LangGraph thread resume。 |

### 6.5 Engine Dispatch & Execution / 引擎调度与执行模块

| 特性 ID | 特性分类 | 覆盖场景簇 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F33 | EngineRegistry strict matching | AS-SC01-AS-SC05、AS-SC19 | 每次执行都通过 `EngineRegistry.resolve(envelope)`，按 `engine_type` 与 capability 找到唯一 ExecutorAdapter；不能绕过 registry 或按 Java subtype 私自分派。 | 输入：EngineEnvelope、capability requirement。输出：ExecutorAdapter 或 EngineMatchingException。 | 任务中心控制层、翻译与工具拦截模块。 | engine_type mismatch、missing adapter、capability mismatch。 | Spring AI model registry、LangGraph compiled graph registry。 |
| AS-L1-F34 | ExecutorAdapter lifecycle | AS-SC09-AS-SC12 | 统一 graph executor、agent loop、future actor runtime、crew orchestration、kernel process 的 execute / resume / stream / suspend / checkpoint handoff 边界。 | 输入：RunContext、resume payload、engine config snapshot。输出：result、stream chunk、SuspendSignal、snapshot ref、failure。 | 任务中心控制层、翻译与工具拦截模块、agent-execution-engine。 | executor crash、unsupported resume、stream interruption、snapshot incompatible。 | LangGraph4j graph runtime、AgentScope Runtime Runner、OpenAI Agents runner。 |
| AS-L1-F35 | third-party Agent adapter | AS-SC15、AS-SC16、AS-SC20 | 通过 adapter profile 调用三方 Agent，记录 remote invocation handle，并支持 resume same remote invocation。 | 输入：remote agent profile、parent Run context、adapter credentials reference。输出：remoteTaskId / remoteThreadId、remote status、remote result。 | 任务中心控制层、会话与任务管理层、对外接入层。 | remote auth failure、remote unreachable、remote state lost、adapter schema drift。 | A2A peer agent、AgentScope Runtime、OpenAI Agents handoff。 |
| AS-L1-F36 | sub-agent executor boundary | AS-SC17、AS-SC18 | 将本地 sub-agent、peer agent、third-party agent 都表达为可治理 execution target，不让 sub-agent object 逃逸服务状态模型。 | 输入：child execution request、agent definition、policy envelope。输出：child execution result、SuspendSignal、failure。 | 任务中心控制层、会话与任务管理层。 | child loop runaway、policy mismatch、parent cancellation ignored。 | CrewAI agent/task process、AutoGen routed agents。 |
| AS-L1-F37 | EngineHookSurface | AS-SC13、AS-SC14、AS-SC22 | executor 到达 tool / model / resume / checkpoint / remote-call 边界时，只能向任务中心控制层发 HookPoint event，不能直接调用 RuntimeMiddleware。 | 输入：engine-internal hook boundary。输出：HookPoint event。 | 任务中心控制层、翻译与工具拦截模块。 | direct middleware call、missing HookPoint、hook result ignored。 | LangChain4j tool callback、Semantic Kernel function filter。 |
| AS-L1-F38 | stream / partial result handoff | AS-SC03、AS-SC09 | 将 token、step、tool progress、partial result 交给 Access Layer / Internal Event Queue 可投影，而不是让 engine 直接拥有 client stream。 | 输入：engine stream chunk、progress event。输出：stream projection material、RunEvent material。 | 内部事件队列、对外接入层、翻译与工具拦截模块。 | stream disconnect、partial output schema mismatch、slow consumer。 | OpenAI Agents stream events、LangGraph streaming。 |
| AS-L1-F39 | adapter capability 与版本治理 | AS-SC19、AS-SC20、AS-SC24 | 管理 engine / third-party adapter 的 supported run modes、streaming、tool、checkpoint、S2C、delegation、resume schema 与版本能力。 | 输入：adapter metadata、runtime probe、capability contract。输出：capability registry entry、version compatibility signal。 | 对外接入层、任务中心控制层、会话与任务管理层。 | stale adapter capability、resume schema drift、unsupported run mode。 | A2A AgentCard、AgentScope metadata、LangChain4j capabilities。 |

### 6.6 Translation & Tool-Intercept / 翻译与工具拦截模块

| 特性 ID | 特性分类 | 覆盖场景簇 | 功能描述 | 输入 / 输出 | 协作模块 | 异常覆盖 | 业界参照 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F40 | Context projection 与 prompt construction | AS-SC07、AS-SC08 | 把 Session context、variables、memory projection、compressed summary 转成 InjectedContext，再通过 PromptTemplate 形成模型输入。 | 输入：Session projection、context version、template variables。输出：InjectedContext、RenderedPrompt。 | 会话与任务管理层、引擎调度与执行模块。 | stale context、missing variable、prompt overflow、cross-tenant memory read。 | Spring AI PromptTemplate、OpenAI Agents sessions、AgentScope state externalization。 |
| AS-L1-F41 | context compression boundary | AS-SC08、AS-SC24 | 对超长上下文执行 summary / trimming / retrieval projection，并把压缩结果与原始 Session 事实解耦。 | 输入：messages、context limit、memory policy。输出：compressed projection、loss marker、projection version。 | 会话与任务管理层、任务中心控制层。 | compression loses required fact、summary stale、memory mutation race。 | LangGraph state snapshot、CrewAI memory scopes。 |
| AS-L1-F42 | model profile normalization | AS-SC19、AS-SC24 | 将 provider、model id、options、streaming、structured output、cost / quota tags 标准化为服务内可治理 invocation profile。 | 输入：model config、run config snapshot。输出：normalized model invocation profile。 | 任务中心控制层、引擎调度与执行模块。 | unsupported option、provider drift、quota mismatch、profile bypass。 | Spring AI ChatClient、Semantic Kernel AI service selector。 |
| AS-L1-F43 | structured output 与 result interpretation | AS-SC01、AS-SC09、AS-SC13 | 将模型或 engine 输出转换为 typed domain object、tool result、client callback request 或 Run terminal payload。 | 输入：model output、schema、tool result。输出：typed result、conversion error、terminal payload。 | 引擎调度与执行模块、任务中心控制层。 | schema invalid、partial output、tool result mismatch、callback payload invalid。 | Spring AI StructuredOutputConverter、LangChain4j structured output。 |
| AS-L1-F44 | ChatAdvisor / tool shaping | AS-SC13、AS-SC14、AS-SC22 | 在 model-call boundary 完成 request decoration、tool-call shaping、response interpretation；它可以与 RuntimeMiddleware 组合，但不替代 RuntimeMiddleware。 | 输入：ChatClient request、tool definition、model response。输出：shaped request、tool-call descriptor、interpreted response。 | 任务中心控制层、引擎调度与执行模块、agent-middleware。 | advisor short-circuit 与 runtime policy 冲突、tool-call escape、model-call exception。 | Spring AI ChatAdvisor、LangChain4j ToolExecutor。 |
| AS-L1-F45 | client-hosted skill payload shaping | AS-SC13、AS-SC14、AS-SC21 | 将模型 / agent 产生的 client skill 调用转成受 schema 约束的 S2C payload，并解释 client 返回结果。 | 输入：tool-call intent、client skill schema、client response。输出：S2C request payload、validated response、conversion error。 | 对外接入层、任务中心控制层、内部事件队列。 | client response invalid、permission mismatch、callback timeout。 | A2A input_required、OpenAI Agents tool approval。 |
| AS-L1-F46 | remote agent payload normalization | AS-SC15、AS-SC16、AS-SC20 | 将三方 Agent / peer Agent 的协议 payload、status、tool result、error envelope 规范化为本服务可理解的 remote invocation result。 | 输入：remote response、adapter schema、AgentCard metadata。输出：normalized remote result、remote error classification。 | 引擎调度与执行模块、任务中心控制层、会话与任务管理层。 | remote schema drift、error envelope unknown、resume token mismatch。 | A2A task event、AgentScope protocol handler。 |
| AS-L1-F47 | tool / memory / retrieval invocation profile | AS-SC08、AS-SC22 | 标准化 tool schema、memory read/write、retrieval、embedding、sandbox-sensitive invocation 形态，并把 policy-sensitive 部分交给 RuntimeMiddleware。 | 输入：tool schema、memory policy、retrieval request。输出：normalized tool invocation、memory projection request。 | 任务中心控制层、引擎调度与执行模块、agent-middleware。 | tool schema drift、memory over-read、sandbox bypass、retrieval tenant leak。 | LangChain4j tools/RAG、Spring AI tools、Semantic Kernel plugins。 |

## 7. 正交性检查

| 边界 | 正确切分 | 错误切分 |
| --- | --- | --- |
| SSE / polling vs Run lifecycle | Access Layer 管连接和投影；Run lifecycle 由 Session & Task Manager + Task-Centric Control Layer 管。 | SSE 断线即取消 Run，或 polling 直接读取 engine 内部状态。 |
| direct access vs bypass | client 可直连 Agent Service Access Layer；不能直连 engine、repository、middleware、bus。 | 为低延迟让 client 直接调用 ExecutorAdapter 或 agent-bus topic。 |
| Run vs Task vs Session | Run 管 execution state；Task 管 protocol/control state；Session 管 context state。 | 一个 StateStore 同时吞并 Run、Task、Session。 |
| checkpoint vs Session / Memory | checkpoint 是 compute snapshot；Session / Memory 是上下文和知识事实源。 | 用 checkpoint 替代 Session projection 或 memory mutation discipline。 |
| retry vs new Agent scheduling | retry / resume 优先复用同一 Run、attempt、remote handle 或 child Run 关系。 | 三方 Agent 中断后静默创建新的 remote Agent，导致重复副作用和审计断裂。 |
| client-hosted skill vs server tool | client skill 通过 S2C callback 和 policy 控制；server tool 通过 RuntimeMiddleware 与 sandbox 控制。 | engine adapter 直接访问 client 本地能力或把 client skill 当作普通 server-side tool。 |
| RuntimeMiddleware vs ChatAdvisor | RuntimeMiddleware 处理 Run-aware HookPoint；ChatAdvisor 处理 model-call boundary。 | 把两者都叫 tool interceptor 并放在同一模块。 |
| configuration source vs runtime consumption | 配置由明确模块承载并在 Run 创建时形成 resolved snapshot / reference；执行模块只消费。 | 每个 adapter、prompt、请求体各自解释配置。 |

## 8. 多角度反思

### 8.1 业务完整性

扩展后的 AS-SC01-AS-SC24 覆盖了短请求、长任务、SSE、非 SSE、断链恢复、上下文压缩、任务切换、失败重试、取消竞态、S2C、client skill、三方 Agent、sub-agent、多 Agent 聚合、配置治理与审计。它们不是孤立功能点，而是从入口到状态、事件、控制、执行、翻译再回到事实源的闭环。

### 8.2 异常完整性

异常不再只列 schema invalid 或 cancel race。清单显式覆盖 SSE 断链、polling stale cursor、context overflow、compression loss、checkpoint missing、resume re-auth failure、remote handle lost、third-party adapter drift、client skill unavailable、non-idempotent retry、orphan child、late remote result、configuration drift、payload over inline cap 和 anonymous event。

### 8.3 数据流完整性

关键数据必须能跨模块保留身份与因果：tenantId、traceId、runId、taskId、sessionId、parentRunId、attemptId、parentNodeKey、callbackId、remoteTaskId / remoteThreadId、config snapshot reference、context projection version、RunEvent id。任何缺失都会导致恢复、审计或去重失败。

### 8.4 控制流完整性

控制链保持单向主权：Access Layer 规范化入口；Session & Task Manager 创建和维护事实源；Task-Centric Control Layer 做运行时决策；Engine Dispatch & Execution 执行被治理的 adapter；Translation & Tool-Intercept 塑形模型、工具、client skill 与 remote payload；状态和事件回写仍回到 Session & Task Manager 与 Internal Event Queue。

### 8.5 业界能力基线

与 OSS 平均能力相比，本清单覆盖：A2A 的 Task / AgentCard / input_required / streaming，Temporal 的 signal / timer / retry / history，Conductor 的 worker poll / retry / timeout / dead-letter / human task，LangGraph 的 checkpoint / interrupt / resume / thread state，OpenAI Agents 的 handoff / interruption / tool approval / sessions，AutoGen 的 message envelope / agent id / routed agent，CrewAI 的 multi-agent orchestration / memory / event bus，Spring AI / LangChain4j / Semantic Kernel 的 model-tool-function invocation kernel。Agent Service 不复制这些项目，而是把成熟模式收敛进 Java 服务控制面。

## 9. 过时或应删除的旧特性表达

| 旧表达 | 最新判断 | 替代表达 |
| --- | --- | --- |
| “只覆盖 S1-S5 即可” | 拒绝 | S1-S5 是 canonical anchor；能力清单必须展开企业 Agent 服务场景簇。 |
| “SSE 是接口细节” | 拒绝 | SSE / polling / reconnect 是 Access Layer 与长期任务恢复的核心场景。 |
| “client 断链等于任务取消” | 拒绝 | client connection 与 Run lifecycle 解耦；断链后通过 cursor / query / resume 恢复。 |
| “上下文超长只是 prompt 问题” | 拒绝 | context compression 涉及 Session 事实源、projection version、memory governance 与 prompt shaping。 |
| “任务失败后直接重跑” | 拒绝 | retry 必须绑定 attemptId、checkpoint、side-effect classification 与 audit。 |
| “三方 Agent 中断后重新调度一个” | 拒绝 | 优先恢复同一 remote invocation；不能静默新建 Agent。 |
| “client skill 是普通 tool” | 拒绝 | client-hosted skill 必须走 S2C callback、client capability、permission 与 resume 语义。 |
| “配置由 adapter 自己解释” | 拒绝 | model / adapter / client / tool / routing 配置必须有模块主权和 Run-time snapshot / reference。 |
| “RuntimeMiddleware 与 ChatAdvisor 都是 tool interceptor” | 拒绝 | RuntimeMiddleware 属于任务中心控制层；ChatAdvisor 属于翻译与工具拦截模块。 |
| “A2A TaskStore 可替代 RunRepository” | 拒绝 | A2A TaskStore 只覆盖协议 control state，不能承载 Run execution state、tenant/RLS/cancel race。 |

## 10. 最终判断

Agent Service 的模块功能应以扩展业务场景簇为中心，而不是只按粗粒度 S1-S5 或组件名称罗列。本文给出的 AS-SC01-AS-SC24 与 AS-L1-F01-AS-L1-F47 共同构成一份 L1 模块能力地图：它说明 client 如何接入和恢复，Session 如何承载上下文和压缩投影，长任务如何续跑、切换、回滚和重试，三方 Agent 如何原地恢复，client 技能如何被服务端治理，sub-agent 如何委派和聚合，关键配置由哪个模块承载，以及所有正常路径、异常路径、数据流、控制流如何闭环。

这份清单仍是架构评审意见，不替代 canonical L1；但它比上一版更接近企业级 Java Agent Service 的真实能力边界。