---
level: L1
view: logical
module: agent-service
status: proposed
authority: "Review-source record derived from docs/L1/agent-service/oss-agent-service-comparison.md; canonical L1 authority remains docs/L1/agent-service/"
---

# Agent Service 与 Java 开源 Agent / Workflow 架构对比分析

> 本文基于用户授权读取的 `spring-ai-ascend` commit `a37a554d711eb13897216c86b51db25c9cdf86b2`，以及项目 wiki 中已沉淀的开源项目分析资料，对 Agent Service 的内部 5 个模块进行横向类比；第二轮补充了非 Java 开源 Agent / Workflow 项目的源码分析与 wiki 沉淀。
>
> 对比基线来自 `docs/L1/agent-service/logical.md`、`development.md`、`process.md`、`physical.md`、`scenarios.md`：
>
> 1. Layer 1 — Access Layer，对外接入层；
> 2. Layer 2 — Session & Task Manager，会话与任务管理层；
> 3. Layer 3 — Internal Event Queue，内部事件队列层；
> 4. Layer 4 — Task-Centric Control Layer，Task-Centric 状态控制层；
> 5. Layer 5 — Engine Adapter Layer，引擎适配层；其中 commit `a37a554d` 已按 ADR-0140 拆成 5a Engine Dispatch & Execution 与 5b Translation & Tool-Intercept。

---

## 1. 结论先行

### 1.1 是否存在与我们 Agent Service 完全类似的开源实现？

没有发现单一 Java 开源项目与当前 Agent Service 的 5 层职责完全等价。

更准确的判断是：**Agent Service 是多个成熟开源架构模式的组合型控制面**：

| Agent Service 能力 | 最接近的开源参照 |
|---|---|
| 对外协议接入、A2A Task / AgentCard / cancel / streaming | A2A Java SDK、Spring AI A2A、AgentScope Runtime Java |
| 会话与任务模型、Task final / interrupted 状态 | A2A Java SDK、Solon AI、AgentScope Runtime Java、Conductor |
| 内部事件、队列、worker、durable execution | Temporal Java SDK、Conductor、AgentScope Runtime Java |
| Task-centric 状态控制、interrupt / resume / checkpoint | LangGraph4j、Temporal Java SDK、Conductor、A2A Java SDK |
| 引擎适配、模型 / 工具 / MCP / Spring AI integration | Spring AI Alibaba、Spring AI、LangChain4j、AgentScope Runtime Java、Solon AI |

因此，我们的 5 层划分不是凭空设计，而是把开源生态中分散在 protocol runtime、agent framework、durable workflow、Spring AI tool stack 里的职责，收敛到一个企业级 Agent 控制面里。

### 1.2 当前 5 层划分是否合理？

总体合理，尤其是 commit `a37a554d` 里的三个修正点是必要的：

1. **Layer 2 单独拥有 Run aggregate**：与 A2A Java `TaskStore` / Temporal workflow history / Conductor task persistence 的实践一致，状态持久化必须有单一拥有者。
2. **Layer 3 明确标记为 design_only**：这是合理的。当前代码未落 `service.queue/`，但 L1 先声明三轨 bus 绑定，可避免把“队列职责”误塞进 Orchestrator 或 Runtime Adapter。
3. **Layer 5 拆成 5a / 5b**：与 Spring AI / LangChain4j / AgentScope Runtime 的经验一致。执行适配与 prompt/tool/model translation 的变化节奏不同，拆开更清晰。

### 1.3 主要可改进点

后续可以从开源实践提取 8 个改进方向：

1. **A2A Task 状态与内部 Task.A2aState 对齐**：引入 `isFinal()` / `isInterrupted()` 这类状态谓词，降低状态判断分散风险。
2. **补齐 Layer 3 的最小代码骨架**：参考 Conductor / Temporal，将 Producer / Consumer / Dispatcher 接口先落地，即使实现仍是 in-memory。
3. **明确 RunEvent 与队列 channel 的映射测试**：参考 Conductor task event / Temporal workflow history，事件类型与 channel routing 应可被单测验证。
4. **将 `RunRepository.updateIfNotTerminal` 扩展为更显式的 TransitionResult**：返回 winner / loser / illegal / same-terminal 等结构，支撑 cancel race 与审计。
5. **Engine Adapter 增加 capability discovery**：参考 LangChain4j `supportedCapabilities()`、AgentScope Runtime protocol metadata、A2A AgentCard。
6. **RuntimeMiddleware 与 ChatAdvisor 的组合顺序落成 contract**：当前 L1 已区分两者，但还需要调用时序、失败传播、短路语义的 L2 contract。
7. **Checkpoint SPI 保持极小**：参考 LangGraph4j `BaseCheckpointSaver`，不要把业务状态、workflow history、memory 全塞进一个大接口。
8. **Thin protocol adapter 原则写入 Access Layer L2**：参考 AgentScope Runtime，A2A / Responses / HTTP 只做协议转换，不拥有核心状态机。

---

## 2. 对比对象与来源

本次对比使用项目 wiki 已沉淀的开源分析材料，以及本地 reference checkout：

| 项目 | 本地路径 / commit | 本次用于类比的重点 |
|---|---|---|
| Spring AI Alibaba | `/home/xiaom/reference-repos/spring-ai-ascend/spring-ai-alibaba`, `d70ab10` | Java/Spring agent platform、graph core、agent framework、admin、MCP、sandbox、A2A starter |
| A2A Java SDK | `/home/xiaom/reference-repos/spring-ai-ascend/a2a-java`, `0b867d0` | A2A Task lifecycle、TaskManager、TaskStore、AgentExecutor、client-side task event handling |
| Spring AI A2A | `/home/xiaom/reference-repos/spring-ai-ascend/spring-ai-a2a`, `d21f786` | Spring Boot A2A server thin adapter、controller、agent card、auto-configuration |
| AgentScope Java + Runtime Java | `agentscope-java` `28ef28a` / `agentscope-runtime-java` `7b9032` | AgentApp、Runner、AgentHandler、protocol adapter、externalized session/state/memory/sandbox |
| Temporal Java SDK | `/home/xiaom/reference-repos/spring-ai-ascend/temporal-sdk-java`, `44bb603` | Durable workflow、Worker、WorkflowStateMachines、signal/activity/replay/checkpoint 语义 |
| Conductor | `/home/xiaom/reference-repos/spring-ai-ascend/conductor`, `abd97e9` | REST/gRPC workflow/task service、worker poll/update、queue/persistence、AI/MCP worker |
| Solon AI | `/home/xiaom/reference-repos/spring-ai-ascend/solon-ai`, `34aef2f` | AgentSession、MCP、A2A preview、ACP、skills、轻量 Java AI 框架 |
| LangGraph4j | wiki 已分析，commit `4206519` | StateGraph、CompiledGraph、checkpoint、interrupt/resume、hooks |
| LangChain4j | wiki 已分析，commit `6871eb8` | AiServices、tool execution、AgenticScope、agent workflow、A2A/MCP integration |
| Spring AI | wiki 已分析，commit `3a14bd5` | ChatClient、Advisor、ToolCallback、MCP starter、auto-configuration |
| LangGraph (Python) | `/home/xiaom/reference-repos/spring-ai-ascend/langgraph`, `d1e2ff0` | StateGraph、Pregel runtime、checkpoint、interrupt/resume、LangGraph SDK/server API |
| AutoGen (Python) | `/home/xiaom/reference-repos/spring-ai-ascend/autogen`, `027ecf0` | AgentRuntime、message envelopes、async queue、subscriptions、intervention/cancellation、Studio/ext |
| CrewAI (Python) | `/home/xiaom/reference-repos/spring-ai-ascend/crewai`, `bad64b1` | Crew/Agent/Task/Process、Flow event workflow、memory、event bus、checkpoint config |
| OpenAI Agents Python | `/home/xiaom/reference-repos/spring-ai-ascend/openai-agents-python`, `6d5b888` | Runner、Agent、RunState、handoffs、guardrails、sessions、tracing、sandbox/MCP |
| Semantic Kernel (Python) | `/home/xiaom/reference-repos/spring-ai-ascend/semantic-kernel`, `3e180c1` | Kernel、plugins/functions/services、filters、agents、process framework、memory/vector store |


### 2.1 开源精品简介：它们各自解决的不是同一个问题

为了避免把项目名当成架构结论，先把每个开源项目的定位、核心原理、以及是否存在 Agent Service 等效模块说明清楚。

| 项目 | 核心定位 | 功能原理摘要 | 是否有 Agent Service 等效定义 / 模块 | 如果没有，它如何解决同类问题 |
|---|---|---|---|---|
| **A2A Java SDK** | A2A 协议 Java SDK | 用 A2A `Task` / `Message` / `TaskState` 定义跨 Agent 的协议对象；server 侧通过 `AgentExecutor` 承接任务执行，`TaskManager` / `TaskStore` 维护 task snapshot 与事件；client 侧用 `ClientTaskManager` 消费 task event / streaming / push notification。 | **没有完整 Agent Service**。最接近的是 server-common 的 task server + `AgentExecutor` 组合。 | 把“服务控制面”切成协议 SDK：协议状态、task store、executor contract 由 SDK 提供；真正的运行时、队列、tenant、policy 交给宿主应用实现。 |
| **Spring AI A2A** | Spring Boot 下把 Spring AI Agent 暴露成 A2A Server 的轻量适配器 | Controller 接收 A2A task/message 请求，AutoConfiguration 注入 executor、agent card、properties；`DefaultAgentExecutor` / `ChatClientExecutorHandler` 把协议请求转成 Spring AI ChatClient 调用。 | **没有完整 Agent Service**。等效模块是“Access Layer + thin executor adapter”。 | 以 starter 方式解决接入问题：不自己管理复杂 Run/Task/Queue，只把 Spring AI agent 包成 A2A endpoint。 |
| **Spring AI Alibaba** | Java/Spring 生态综合 Agent 平台 | 以 graph core / agent framework 承载 ReactAgent、StateGraph、WorkflowAgentExecutor；admin/studio 提供 workflow 构建、调试、运行入口；MCP、sandbox、A2A starter 与 observation 模块提供平台化集成。 | **部分接近 Agent Service**，但不是同名单体服务。它以多个模块共同覆盖 agent runtime、workflow admin、tool/MCP/sandbox、A2A 接入。 | 通过“框架 + 平台模块 + starter”组合解决：graph/agent runtime 负责执行，admin/studio 负责管理，starter 负责协议接入。边界比我们的 5 层控制面更分散。 |
| **AgentScope Runtime Java** | Agent-as-a-service runtime wrapper | `AgentApp` 组装一个 `Runner` 与一个 `AgentHandler`；protocol handler 把 A2A / Responses API 请求转为 `AgentRequest`；Runner 统一补齐 `userId/sessionId`，调用 handler 的 streamQuery，并把框架事件转换为 runtime event；state/session/memory/sandbox 通过服务接口外部化。 | **最接近“Agent Service”形态**，但它是“单 agent runtime service”，不是多层企业控制面。 | 通过 per-runtime service + protocol endpoint + externalized state 解决跨 VM Agent 服务化；不提供透明分布式 subagent 对象，也不内置完整 task queue/control plane。 |
| **Temporal Java SDK** | Durable execution SDK | Workflow code 产生命令；Temporal service 维护 workflow history；worker polling workflow/activity task；signal/timer/activity retry 通过 history replay 恢复。 | **没有 Agent Service**。等效的是 durable workflow execution substrate。 | 不理解 Agent/LLM，但提供 long-running task 的事实源、worker、signal、retry、timer、replay。Agent Service 可借鉴其 durable control 原理，而不是照搬 workflow 编程模型。 |
| **Conductor** | Workflow orchestration service/platform | REST/gRPC 暴露 workflow/task API；workflow executor 根据 workflow definition 调度 task；worker poll task、update result；queue/persistence backend 管理 retry、timeout、callback、human/system task；AI 模块提供 LLM/MCP worker。 | **有服务型控制面，但不是 Agent Service**。等效的是 workflow/task orchestration server。 | 用 workflow + task + worker queue 解决长任务编排。Agent 语义不是核心，但 task API、worker contract、queue/persistence 对我们的 Layer 2/3/4 很有参考价值。 |
| **LangGraph4j** | Java stateful graph/agent workflow runtime | `StateGraph` 定义 node/edge/channel reducer；compile 成 `CompiledGraph`；运行时支持 checkpoint saver、state history、interrupt before/after、resume、node/edge hooks。 | **没有服务层 Agent Service**。等效的是 Engine Adapter 后面的 graph executor kernel。 | 把状态流、interrupt/resume、checkpoint 做成 runtime library；接入层、tenant、queue、服务化由外部系统承担。 |
| **LangChain4j** | Java-native LLM application library + agentic workflow | `AiServices` 用 interface/proxy 生成 LLM-backed service；tool executor 反射调用 `@Tool`，支持 memoryId/context 注入；agentic module 支持 sequence/parallel/conditional/loop/planner/supervisor/HITL；A2A/MCP 模块提供集成。 | **没有 Agent Service**。等效的是 5b tool/model translation 与部分 agentic runtime。 | 用 library abstraction 解决模型、tool、RAG、agent workflow 组合；服务控制、Run/Task、队列、tenant 需要宿主平台实现。 |
| **Spring AI** | Spring 官方 AI application framework | `ChatClient` 统一 prompt/tool/advisor/options/structured output/stream；`ChatModel` / provider starter 隐藏模型差异；Advisor chain 作为 request pipeline middleware；MCP starter 提供 client/server transport。 | **没有 Agent Service**。等效的是 5b Spring idiomatic model/tool invocation layer。 | 解决 Spring 应用如何调用模型、工具、RAG、MCP；不负责 agent task 生命周期和 durable orchestration。 |
| **Solon AI** | 轻量 Java AI 应用框架 | 提供 Agent/ReAct/Team、AgentSession、MCP、A2A、ACP、skills、RAG 等模块；session 有 memory/file/Redis 变体，协议与 agent 能力组合较轻。 | **没有完整 Agent Service**。等效的是轻量 agent/session/protocol toolkit。 | 以小型 framework 组合解决 Agent + session + protocol，不承担企业级 Run/Task/Queue/control plane。 |
| **LangGraph (Python)** | Stateful graph / agent workflow runtime + platform SDK | `StateGraph` 节点读共享 state 并返回 partial state；state key 可配置 reducer；compile 后进入 Pregel-style runtime，支持 checkpoint、interrupt/resume、streaming 与远程 graph/thread/run/state API。 | **没有通用 Agent Service**。最接近的是 LangGraph Server/SDK 暴露的 thread/run/state/checkpoint 服务形态。 | 用 graph runtime + hosted platform API 解决 stateful agent workflow；接入、tenant、Run/Task/Session 聚合、内部队列由宿主平台补。 |
| **AutoGen (Python)** | Actor/message-runtime style multi-agent framework | `AgentId` 标识 agent；`PublishMessageEnvelope` / `SendMessageEnvelope` / `ResponseMessageEnvelope` 包装消息；`SingleThreadedAgentRuntime` 用 asyncio queue、subscription manager、serialization registry、cancellation token、intervention handler 与 telemetry metadata 处理投递。 | **没有 Agent Service**。等效的是内部 agent message runtime。 | 用 agent runtime + message envelopes + queue/subscription 解决多 agent 通信；持久 Run/Task/Session 与 tenant 控制面由外部服务实现。 |
| **CrewAI (Python)** | Multi-agent task orchestration framework | `Crew` 聚合 agents、tasks、process、memory、knowledge、tools、event listener、checkpoint config、RPM control 与 task output storage；`Flow` 用 `@start/@listen/@router` 组织事件驱动 workflow，支持 persistence 与 human feedback。 | **没有完整 Agent Service**。等效的是 Crew/Flow 应用级 orchestration object。 | 用 Crew/Task/Flow 解决多 agent 协作和任务编排；service ingress、durable queue、tenant-first aggregate 不在 OSS core 边界内。 |
| **OpenAI Agents Python** | Compact agent SDK / runner | `Runner` / `AgentRunner` 编排 turn-based loop；`Agent` 拥有 instructions、tools、MCP servers、handoffs、model settings、output schema、guardrails、hooks；run loop 产生 final / handoff / interruption / run-again next step，并支持 sessions、tracing、stream events、sandbox memory。 | **没有 Agent Service**。等效的是 Layer 4/5 SDK runner。 | 用 library runner 解决 agent loop、tool approval/interruption、handoff、guardrail、session persistence；HTTP/API、durable task 与 tenant governance 由应用承担。 |
| **Semantic Kernel (Python)** | Plugin/function/service/filter composition kernel | `Kernel` 管理 plugins、AI services、service selector、function invocation filters、prompt rendering filters、auto function invocation filters、reliability 与 function execution；`KernelProcess` 定义 process steps、edges、factories 与 state metadata。 | **没有 Agent Service**。等效的是 Layer 5b invocation kernel + process abstraction。 | 用 embedded kernel 解决函数/插件/模型服务编排；不拥有 Agent task 生命周期、内部队列或服务级 Run aggregate。 |

这个表说明：开源项目大多只覆盖 Agent Service 的某个切面。真正接近“服务”的有 AgentScope Runtime、Conductor、Spring AI Alibaba，以及非 Java 侧的 LangGraph hosted API/SDK、AutoGen runtime/Studio、CrewAI Enterprise-facing kickoff 模式，但它们的解决方式分别是 **single-agent runtime service**、**workflow orchestration server**、**平台模块集合**，都不是我们这种将 protocol ingress、Run/Task/Session、internal queue、Task-centric control、engine adapter 明确收口到一个 Agent Service L1 边界内的架构。

---

## 3. 基线：我们 Agent Service 的 5 层内部结构

commit `a37a554d` 的 L1 canonical 4+1 设计中，Agent Service 逻辑视图是：

```text
External Client / A2A Peer / MQ
        |
        v
Layer 1  Access Layer
        - Gateway / A2A Service / MQ Adapter / IngressGateway
        - TenantContextFilter / JWT / Idempotency / TraceExtract
        |
        v
Layer 2  Session & Task Manager
        - Run aggregate: Run / RunStatus / RunStateMachine / RunRepository
        - Task aggregate: Task / TaskStateStore
        - Session aggregate: Session / ContextProjector
        - IdempotencyRecord
        |
        v
Layer 3  Internal Event Queue (design_only at rc55)
        - control / data / rhythm channel binding over agent-bus
        - Producer / Consumer split, future service.queue/
        |
        v
Layer 4  Task-Centric Control Layer
        - Orchestrator
        - SuspendSignal handling
        - RuntimeMiddleware chain
        - Fast / Slow Path routing (design_only)
        - RunRepository.updateIfNotTerminal typed reference
        |
        v
Layer 5a Engine Dispatch & Execution
        - EngineRegistry.resolve(envelope)
        - ExecutorAdapter
        - EngineHookSurface emits HookPoint into Layer 4

Layer 5b Translation & Tool-Intercept
        - ContextProjector
        - PromptTemplate
        - StructuredOutputConverter
        - ChatAdvisor / AdvisorChain
```

几个关键红线：

- Access Layer 不直接驱动 Runtime，不直接调用 Middleware。
- Run aggregate 只由 Layer 2 拥有；Layer 4 只能通过 `RunRepository.updateIfNotTerminal(...)` 做 CAS 状态变更。
- Layer 3 当前没有代码 home，必须显式标注 design_only。
- RuntimeMiddleware 只属于 Layer 4；ChatAdvisor 只属于 Layer 5b，两者组合但不是别名。
- Layer 5a 只做 engine dispatch / execution，不能越过 Layer 4 直接调用 Middleware。

---

## 4. Layer 1 — 对外接入层对比

### 4.1 我们的 Layer 1

Agent Service Access Layer 负责协议收敛与入口横切：

- HTTP/gRPC `openapi-v1.yaml`；
- A2A `a2a-envelope.v1.yaml`，当前 design_only；
- MQ/Ingress `ingress-envelope.v1.yaml`，当前 design_only；
- tenant / JWT / idempotency / trace filter；
- 将请求转成 Run / Task 创建请求后交给 Layer 2，不直接执行引擎。

### 4.2 开源类似实现

| 开源项目 | 类似实现 | 对比判断 |
|---|---|---|
| A2A Java SDK | `AgentExecutor`、server task API、Task / Message / TaskState spec、push notification config、client-side `ClientTaskManager` | 最接近 A2A 协议接入边界，但它本身不是完整 Agent Service 控制面 |
| Spring AI A2A | `TaskController`、`MessageController`、`AgentCardController`、`A2AServerAutoConfiguration`、`DefaultAgentExecutor` | 很适合作为 Layer 1 A2A starter / auto-config 参考 |
| AgentScope Runtime Java | `AgentApp` + `Runner` + A2A / Responses API protocol handlers | 体现 thin protocol adapter 原则：协议转换后统一进入 Runner |
| Spring AI Alibaba | A2A starter、Admin/Studio controller、MCP server controller | 更像完整平台入口，覆盖 admin + runtime + MCP，但边界较宽 |
| Conductor | `TaskResource`、`WorkflowResource`、gRPC service | 类似任务编排平台入口，适合借鉴 REST/gRPC task polling/update/cancel API |
| Spring AI | ChatClient / MCP WebMVC/WebFlux transports | 更像 library/starter 层，不是 Agent Service 入口控制面 |
| LangGraph (Python) | SDK / remote graph 的 thread/run/state/stream API | 有 hosted graph service 形态，适合参考 run/thread/state/stream 接口，但不是 A2A/tenant ingress |
| AutoGen (Python) | FastAPI streaming samples、gRPC worker runtime samples、AutoGen Studio | 入口多为示例或 studio，核心不定义统一 Agent Service ingress |
| CrewAI (Python) | CLI / kickoff / enterprise client pattern | 弱接入层，偏应用框架启动入口 |
| OpenAI Agents Python | Host application 调用 Runner API | 无内建服务入口；应用自定义 HTTP/API |
| Semantic Kernel (Python) | Embedded Kernel in host app | 无统一接入层；由 ASP.NET/FastAPI/应用服务承载 |

### 4.3 功能原理与差异

- **A2A Java SDK 的原理**：先定义协议级 Task/Message/State，再用 `AgentExecutor` 把协议任务交给宿主 agent。它关心“远端如何看见一个 agent task”，不关心企业内部 Run/Task/Session/tenant/queue 如何治理。差异是：我们的 Access Layer 只是入口，不能成为 TaskStore 或 executor owner。
- **Spring AI A2A 的原理**：用 Spring Boot auto-configuration 把 A2A controller、agent card、executor handler 自动装配起来。它适合作为 `service.platform.a2a/` 的 starter 参照。差异是：它默认把请求快速转给 ChatClient，不承担 Layer 2/3/4 的持久状态和控制语义。
- **AgentScope Runtime 的原理**：protocol handler 只做 A2A/Responses → `AgentRequest` 的转换，真正调用统一进入 `Runner.streamQuery(...)`。差异是：AgentScope Runtime 的 Runner 包一个 AgentHandler，而我们的 Agent Service 需要先经过 Layer 2/4 决定 Run/Task 状态、路由与治理，再进入 engine adapter。
- **Conductor 的原理**：REST/gRPC API 暴露 workflow/task 操作，worker 用 poll/update 协议拉取任务并回写结果。差异是：Conductor 接入层面向通用 workflow worker；我们的接入层还要处理 A2A、S2C、tenant/idempotency/trace，并维持 Agent 语义。

非 Java 侧也支持这个判断：LangGraph 的 hosted API 把 thread/run/state/stream 暴露成平台接口，但 graph runtime 本身仍在内部；AutoGen/CrewAI/OpenAI Agents/Semantic Kernel 大多把入口交给宿主应用，说明强 agent runtime 不必然等于服务入口。

因此，Layer 1 不是“controller 列表”，而是协议收敛 + identity/idempotency/trace 绑定 + thin handoff。开源方案的共同经验是：入口层越薄，核心状态机越不容易被协议污染。

### 4.4 可借鉴优点

1. **Thin adapter 原则**：AgentScope Runtime 的 A2A / Responses adapter 只做协议转换，核心状态交给 Runner / handler。我们 Layer 1 应保持同样原则：HTTP/A2A/MQ 不拥有 Run 状态机。
2. **AgentCard / capability 暴露**：A2A Java 与 Spring AI A2A 对 AgentCard 的处理可用于未来 `service.platform.a2a/`，把能力声明放在入口层，而不是散落到 engine adapter。
3. **Controller / AutoConfiguration 分离**：Spring AI A2A 小而清晰，适合我们未来 A2A starter 参考。
4. **Task API 明确化**：Conductor 的 task poll/update API 说明入口层除了 create/cancel，还需要 query / heartbeat / result update / batch poll 之类的操作边界。

### 4.4 对我们设计的判断

当前 Layer 1 合理。它比开源项目更强调 tenant / idempotency / trace 这些企业控制面横切能力。需要注意的是：A2A 当前仍是 design_only，后续落地时应避免把 `AgentExecutor`、TaskStore 或 Runner 状态直接放进 controller；controller 应只转译协议并调用 Layer 2/4 公开服务。

---

## 5. Layer 2 — 会话与任务管理层对比

### 5.1 我们的 Layer 2

Layer 2 是 commit `a37a554d` 强化后的核心：

- Run aggregate 单一拥有者：`Run`、`RunStatus`、`RunStateMachine`、`RunRepository`；
- Task aggregate：`Task`、`TaskStateStore`；
- Session aggregate：`Session`、`ContextProjector`；
- `RunRepository.updateIfNotTerminal(...)` 是唯一状态转移 CAS 入口；
- Run / Task / Session 都必须 tenantId-first，未来受 RLS 保护。

### 5.2 开源类似实现

| 开源项目 | 类似实现 | 对比判断 |
|---|---|---|
| A2A Java SDK | `TaskState`、`TaskManager`、`TaskStore`、`InMemoryTaskStore`、`ClientTaskManager` | 最适合对比 Task 生命周期；`isFinal()` / `isInterrupted()` 值得直接借鉴 |
| AgentScope Runtime Java | `userId` / `sessionId` 元数据、`SessionHistoryService`、`MemoryService`、`StateService` | 适合类比分布式 Agent service 的 session/state 外部化 |
| Solon AI | `AgentSession`，in-memory / file / Redis session 实现；A2A preview `Task` / `TaskState` | 适合作轻量 session storage 变体参考 |
| Conductor | `Task`、`WorkflowTask`、TaskService、WorkflowService、persistence backend | 适合类比任务查询、worker update、retry/timeout 元数据 |
| Temporal Java SDK | workflow identity、workflow history、state machine、query/signal | 更偏 durable workflow，不直接提供 session/task 管理，但证明状态历史必须独立持久化 |
| LangChain4j | `AgenticScope`、chat memory、`@MemoryId` | 提醒我们并发 memory/session 必须由平台层控制，而不是 library 自行保证 |
| LangGraph (Python) | thread / checkpoint / state snapshot | 强状态 runtime，但 thread/checkpoint 不能替代 Run/Task/Session 三聚合 |
| OpenAI Agents Python | `Session` protocol、`SQLiteSession`、OpenAI conversation sessions、compaction-aware sessions | 适合 session persistence 参考，但没有 tenant-first Run aggregate |
| CrewAI (Python) | Crew memory、MemoryScope/Slice、checkpoint config、task output storage、FlowPersistence | 应用级 memory/checkpoint/task output，很适合 session projection 与 task output 参考 |
| Semantic Kernel (Python) | agent thread、process state metadata、semantic memory/vector store | 适合 vocabulary 参考，但不是 CAS-protected service aggregate |

### 5.3 功能原理与差异

- **A2A Java SDK 的原理**：`TaskState` 是协议可见状态，区分 submitted/working/input-required/auth-required/completed/canceled/failed/rejected，并通过 final/interrupted 谓词简化调用方判断。差异是：A2A Task 是对外 control state；我们的 Layer 2 还要同时管理内部 Run execution state 与 Session context state。
- **AgentScope Runtime 的原理**：请求必须携带或补齐 `userId/sessionId`，状态、会话历史、memory、sandbox 通过服务接口获取或保存。差异是：它以 user/session 维持 agent runtime 连续性；我们还需要 tenantId-first、RunId、TaskId、RLS、CAS transition。
- **Conductor 的原理**：Workflow/Task 是数据库中的调度事实，worker 通过 task token / status update 改变任务进度。差异是：Conductor Task 往往就是调度单元；我们把 Task 作为控制状态，把 Run 作为可多次出现的执行快照。
- **Temporal 的原理**：workflow identity + history 是事实源，代码通过 replay 恢复逻辑状态。差异是：Temporal 不显式建 Agent Session；我们的 Session 是 LLM/Agent 上下文载体，不能被 workflow history 替代。
- **LangChain4j 的原理**：`@MemoryId` 将多轮 memory 绑定到调用上下文，但框架提示同一 memoryId 并发调用有风险。差异是：我们的并发控制必须平台化，不能把 session/memory consistency 留给调用者自律。

非 Java 侧进一步说明：LangGraph 把 thread/checkpoint 作为 runtime identity，OpenAI Agents 把 Session 当 conversation persistence，CrewAI 把 memory/checkpoint/task output 作为 Crew/Flow 的应用状态，Semantic Kernel 把 memory/process state 作为 library abstraction。它们都没有同时给出 tenant-first Task/Run/Session 三聚合。

所以 Layer 2 的关键不是“有 Task 类 / Session 类”，而是定义三类状态的边界：Task 管控制状态，Run 管执行状态，Session 管上下文状态，并用统一持久化/并发原语维护它们之间的投影关系。

### 5.4 可借鉴优点

1. **TaskState 谓词化**：A2A Java 的 `TaskState` 有 `isFinal()` 与 `isInterrupted()`，比散落判断更安全。我们的 `Task.A2aState` 可增加类似谓词。
2. **TaskStore 可插拔**：A2A Java 与 Solon AI 都有 in-memory 与可替换 backend 的路径；我们 `TaskStateStore` 应尽早定义 DB / Redis / in-memory 的一致语义。
3. **Session/state 外部化**：AgentScope Runtime 不把分布式 session 绑定到 JVM 对象，而是通过 `userId/sessionId` 访问外部 state/history/memory，这与我们的 tenantId / sessionId / runId first 方向一致。
4. **Run 与 Task 分离是正确的**：A2A/Conductor 的 Task 更像 control-state；Temporal 的 workflow execution 更像 run/execution-state。我们将 Task 与 Run 区分，符合开源实践。
5. **并发约束要平台化**：LangChain4j 对同一 `@MemoryId` 并发调用有风险提示。我们把 Run 状态更新集中到 CAS，可以继续把 session projection / memory mutation 也纳入类似并发纪律。

### 5.4 对我们设计的判断

Layer 2 是当前 Agent Service 设计中最应保持稳定的层。commit `a37a554d` 把 Run aggregate 单一拥有权钉在 Layer 2，是正确修正。

建议后续增强：

- `TaskStateStore` 明确定义 `transitionIf(...)` 或 `updateIfNotTerminal(...)` 类似原语，避免 Task 层重演 Run 层的非原子状态写问题。
- `Task.A2aState` 增加 `isFinal()`、`isInterrupted()`、`isActive()`。
- Session 与 Run 的 N:M projection 需要与 Memory 并发更新规则一起设计，避免 LangChain4j `@MemoryId` 式并发隐患。

---

## 6. Layer 3 — 内部事件队列层对比

### 6.1 我们的 Layer 3

Layer 3 当前是 design_only：

- 没有 `service.queue/` 代码 home；
- 绑定到 `agent-bus` 三轨 channel：`control`、`data`、`rhythm`；
- 路由意图：cancel / resume / suspend 走 control，payload / S2C response / state-transition event 走 data，heartbeat / tick 走 rhythm；
- 与 durability tier 正交：channel choice 与 in-memory / semi-persistent / persistent 不是同一维度。

### 6.2 开源类似实现

| 开源项目 | 类似实现 | 对比判断 |
|---|---|---|
| Temporal Java SDK | Worker task polling、workflow task、activity task、history replay、signal/timer | 最强 durable execution 参考；事件历史是恢复的事实源 |
| Conductor | task queue、worker poll/update、workflow message queue、persistence/queue backend | 最接近 service-level task queue 与 worker API |
| AgentScope Runtime Java | Runner stream events、A2A task subscription cancellation、external state/session services | 有 runtime event stream，但不是完整内部队列层 |
| A2A Java SDK | task event / update event、push notification、TaskStore | 对协议事件有帮助，但不是内部 queue 架构 |
| LangGraph4j | checkpoint/state history、streaming execution event | 有执行事件与 checkpoint，但不负责跨服务 queue |
| Spring AI Alibaba | workflow debug / task process / A2A starter / scheduling task | 有平台事件迹象，但不是以独立内部事件队列为核心 |
| AutoGen (Python) | Publish/Send/Response envelopes、asyncio queue、TopicId、SubscriptionManager、InterventionHandler | 最强非 Java 内部 runtime queue/envelope 参考，但单线程 runtime 不适合直接生产照搬 |
| CrewAI (Python) | `crewai_event_bus`、Crew/Flow events、tracing listener | 强 observability/control event pattern，但不是 durable queue |
| LangGraph (Python) | stream events、checkpoint writes、state history | 强 runtime event/checkpoint 参考，不是跨服务 worker queue |
| OpenAI Agents Python | stream events、sandbox memory generation queue | 有 run stream 与局部 background queue，但不是通用 Layer 3 |

### 6.3 功能原理与差异

- **Temporal 的原理**：workflow task / activity task 被 worker 拉取执行，所有关键决策进入 workflow history；恢复时不是读某个当前变量，而是 replay history。差异是：我们的 Layer 3 不一定采用 Temporal history model，但 RunEvent 必须能成为恢复、审计、演进反馈的事实流。
- **Conductor 的原理**：queue backend + worker polling + task update + retry/timeout 共同构成内部调度闭环。差异是：Conductor 以 workflow task 为核心；我们的事件还要按 Agent 意图拆成 control/data/rhythm 三轨，支持 cancel/resume/suspend、payload/S2C、heartbeat/tick。
- **A2A Java SDK 的原理**：task event / push notification 面向外部 client 可见进度。差异是：这不是内部队列；它只能证明“事件流需要协议化”，不能替代 Layer 3 的 lease/ack/retry/dead-letter。
- **LangGraph4j 的原理**：checkpoint/state history 记录 graph 执行状态，用于 resume。差异是：checkpoint 是执行快照，不等于服务级队列；Layer 3 仍需负责跨实例、跨 worker、跨 channel 的传递语义。

非 Java 侧的关键增量是 AutoGen：它把 message envelope、sender/recipient/topic、future、cancellation token、metadata、subscription、intervention/drop 都显式建模。我们未来的 RunEvent envelope 可借鉴这些字段，但需要补上 durable lease/ack/retry/dead-letter。

因此，Layer 3 不能只写成“未来用某个 MQ”。它要定义事件类型、路由 channel、消费确认、失败重试、死信、幂等 key 与 durable tier 的关系。开源框架的差异恰好说明：protocol event、checkpoint history、worker queue、actor message runtime 是四种不同东西。

### 6.4 可借鉴优点

1. **队列不是只有消息通道，还要有 worker contract**：Conductor 的 worker poll/update 模型说明 Layer 3 未来不应只定义 Producer，还应定义 Consumer lease / ack / retry / heartbeat。
2. **事件历史支撑恢复**：Temporal 的 workflow history/replay 表明，Slow-Path 恢复不能只靠当前状态，关键事件需要可追溯。
3. **事件类型要 sealed / schema-first**：我们的 ADR-0145 `RunEvent` sealed hierarchy 与 `run-event.v1.yaml` 是对的，类似 Temporal event history / Conductor task event 的可审计轨迹。
4. **channel 与 durability 分离是正确的**：Conductor / Temporal 都不会把“事件意图”和“存储介质”混为一谈。我们的 control/data/rhythm 与 durability tier 正交，设计合理。

### 6.4 对我们设计的判断

Layer 3 作为独立层是合理的，但因为当前仍是 design_only，风险是后续实现时被 Layer 2 或 Layer 4 吸收，导致边界倒退。

建议后续优先落一个最小骨架：

```java
interface RunEventPublisher {
    void publish(RunEvent event, QueueChannel channel);
}

interface RunEventConsumer {
    void subscribe(QueueChannel channel, RunEventHandler handler);
}

interface QueueLease {
    void ack();
    void retry(Duration backoff);
    void deadLetter(String reason);
}
```

即使实现是 in-memory，也能把 Producer / Consumer / lease / retry 语义先固化，防止 Orchestrator 直接操作 bus 细节。

---

## 7. Layer 4 — Task-Centric 状态控制层对比

### 7.1 我们的 Layer 4

Layer 4 是 Agent Service 的控制核心：

- Orchestrator；
- SuspendSignal catch + handling；
- child-run 与 S2C callback 变体；
- RuntimeMiddleware chain，HookPoint dispatch；
- DualTrackRouter / SlowTrackJudge，当前 design_only；
- 不直接写 Run，只通过 Layer 2 `RunRepository.updateIfNotTerminal(...)`。

### 7.2 开源类似实现

| 开源项目 | 类似实现 | 对比判断 |
|---|---|---|
| LangGraph4j | interrupt before/after、checkpoint saver、resume、state history、NodeHook / EdgeHook | 最接近 agent workflow interrupt/resume/control 语义 |
| Temporal Java SDK | signal、timer、activity retry、workflow state machine、worker lifecycle | 最强 long-running control layer 参考 |
| Conductor | workflow executor、system task worker、retry/timeout/subworkflow/human task | 最接近服务编排控制面 |
| A2A Java SDK | `TaskState.INPUT_REQUIRED` / `AUTH_REQUIRED` interrupted states、cancel/rejected/failed terminal states | 对外 task control state 的协议化表达 |
| Spring AI Alibaba | `StateGraph`、`ReactAgent`、`WorkflowAgentExecutor`、workflow debug/run/resume APIs | Java/Spring agent platform 内部控制流参考 |
| AgentScope Runtime Java | Runner 管理 request stream、task subscription cancellation、state/session service | 较薄的 runtime control，适合协议 runtime，不如 Temporal/Conductor 完整 |
| LangGraph (Python) | Pregel runtime、interrupt/resume、checkpoint、reducers、state snapshots | 最强非 Java stateful control runtime 参考 |
| OpenAI Agents Python | next-step loop：final / handoff / interruption / run-again，guardrails、hooks、tool approval | 最强 agent loop 与 handoff/interruption 参考 |
| CrewAI (Python) | Crew process、Flow pause/listen/router、human feedback、checkpoint config | 多 agent task orchestration 与 event workflow 参考 |
| AutoGen (Python) | intervention handlers、cancellation token、message runtime lifecycle | actor/message control 参考，但不是 Run CAS 控制层 |

### 7.3 功能原理与差异

- **LangGraph4j 的原理**：graph node 返回 partial state，由 channel reducer 合并；interrupt before/after 与 checkpoint saver 允许在节点边界暂停并恢复。差异是：LangGraph4j 控制的是 graph runtime 内部状态；我们的 Layer 4 控制的是服务级 Run/Task 生命周期，并要把状态写入 Layer 2。
- **Temporal 的原理**：workflow code 发出 activity/signal/timer 等命令，Temporal service 负责调度与 history，worker 通过 replay 保证长任务可恢复。差异是：Temporal 把控制逻辑放进 deterministic workflow；我们的 Orchestrator 需要兼容 GraphExecutor、AgentLoopExecutor、未来异构 engine，不宜绑定单一 workflow 编程模型。
- **Conductor 的原理**：workflow executor 根据定义推进 task，system task / human task / subworkflow / retry/timeout 都是任务状态机的一部分。差异是：Conductor 的状态控制是 workflow-platform 语义；我们的 Layer 4 还要治理 LLM tool call、RuntimeMiddleware、S2C callback、child-run、A2A delegation。
- **Spring AI Alibaba 的原理**：ReactAgent / StateGraph / WorkflowAgentExecutor 提供 agent/workflow 执行与调试能力。差异是：它更靠近 runtime/framework；我们的 Layer 4 是 runtime governance 层，不能把 framework executor 当成控制面本身。

非 Java 侧把 Layer 4 可拆得更清楚：LangGraph 负责 graph state transition，OpenAI Agents 负责 agent turn next-step，CrewAI 负责 Crew/Flow orchestration，AutoGen 负责 actor message dispatch/intervention。我们的 Layer 4 应吸收这些机制，但保留服务级 Run/Task 治理与 Layer 2 CAS 写入。

所以 Layer 4 的价值是“Task-centric runtime governance”：它决定何时启动、暂停、恢复、取消、短路、升级 Slow-Path、触发 middleware，而不是实现某一个具体 agent loop。

### 7.4 可借鉴优点

1. **Interrupt / resume 要成为一等结构**：LangGraph4j 和 Temporal 都把 interrupt/signal/resume 作为显式机制。我们的 `SuspendSignal` checked exception 是 Java 生态下很强的编译期约束，方向正确。
2. **Control 不应拥有 persistence，但要拥有 transition intent**：Temporal workflow logic 决定下一步，history service 记录事实；我们 Layer 4 决定意图，Layer 2 CAS 落状态，边界类似。
3. **Fast / Slow Path 要可升级**：LangGraph4j checkpoint/resume 与 Temporal durable workflow 提示：Fast-Path 中途发现需要等待时，应能结构化升级为 Slow-Path。当前 scenarios.md 已写这条，后续要落代码。
4. **Middleware hook 要在控制层收口**：Spring AI Advisor 与 LangChain4j tool executor 都可以做工具拦截，但它们靠近 model/tool library。我们把 RuntimeMiddleware 放 Layer 4，是为了避免 runtime 直连 middleware，符合企业控制面治理需求。
5. **Human / client callback 类似 human task**：Conductor human task、A2A input_required、Temporal signal 都能支持我们 S2C callback 的设计依据。

### 7.4 对我们设计的判断

Layer 4 的划分合理，且是区别于普通 Agent SDK 的核心价值。普通框架往往把 agent loop、tool execution、memory 更新揉在一个 runtime 里；我们将其拆成 Task-Centric Control，可支撑 tenant、policy、audit、cancel race、S2C callback、A2A delegation。

主要改进点：

- `RuntimeMiddleware` 的 HookPoint 顺序、异常传播、short-circuit 语义应形成 L2 contract。
- `DualTrackRouter` 不应长期 design_only；至少要先落 policy schema + SPI 空实现。
- S2C callback、child-run、tool-await 三类 suspension 应统一在 `SuspendReason` / `RunEvent` / channel routing 中表达。

---

## 8. Layer 5 — 引擎适配层对比

### 8.1 我们的 Layer 5a / 5b

commit `a37a554d` 将原 Layer 5 拆为：

- **Layer 5a Engine Dispatch & Execution**：
  - `EngineRegistry.resolve(envelope)`；
  - `ExecutorAdapter`；
  - strict engine matching；
  - EngineHookSurface 只向 Layer 4 emit HookPoint。

- **Layer 5b Translation & Tool-Intercept**：
  - `ContextProjector`；
  - `PromptTemplate`；
  - `StructuredOutputConverter<T>`；
  - `ChatAdvisor` / `AdvisorChain`；
  - 不承载 RuntimeMiddleware。

### 8.2 开源类似实现

| 开源项目 | 类似实现 | 对比判断 |
|---|---|---|
| AgentScope Runtime Java | `AgentHandler`、`Runner`、protocol adapter、state/session/memory/sandbox service injection | 最接近 5a runtime adapter：把具体 agent framework 包成统一 Runner 调用 |
| Spring AI Alibaba | `ReactAgent`、`StateGraph`、`AgentToolNode`、`GraphAgentExecutor`、sandbox/MCP | 最接近 Java/Spring integrated agent runtime + tool adapter |
| LangGraph4j | `CompiledGraph`、checkpoint saver、hooks | 适合 GraphExecutor adapter 的内部模型参考 |
| Spring AI | `ChatClient`、Advisor、ToolCallback、StructuredOutputConverter、MCP starter | 最适合 5b translation/tool/model-call pipeline 参考 |
| LangChain4j | `AiServices`、DefaultToolExecutor、AgenticScope、A2A/MCP integration | 更厚的 Java-native tool / service proxy / agent workflow adapter 参考 |
| Solon AI | Agent/ReAct/Team、MCP/A2A/ACP、AgentSession | 可作为轻量、多协议、多 session adapter 参考 |
| LangGraph (Python) | `CompiledGraph` / Pregel runtime / checkpoint saver | 适合作为 GraphExecutor adapter，checkpoint SPI 要隔离 |
| OpenAI Agents Python | `Agent` tools/MCP/handoffs/guardrails/sandbox、Runner | 适合作为 AgentLoopExecutor adapter 与 tool approval/interruption 参考 |
| CrewAI (Python) | Crew/Agent/Task/Tool/Knowledge/Memory | 适合作为 MultiAgentCrewExecutor adapter，但 Crew 对象职责过宽需收口 |
| AutoGen (Python) | RoutedAgent、AgentRuntime、tools/extensions | 适合作为 ActorAgentExecutor adapter，message runtime 不应泄露到 Layer 2 |
| Semantic Kernel (Python) | Kernel plugins/functions/services/filters、KernelProcess | 最强 5b plugin/function/filter composition 参考 |

### 8.3 功能原理与差异

- **AgentScope Runtime 的原理**：`AgentHandler` 屏蔽具体 agent framework，`Runner` 统一 lifecycle、identity、message/event conversion、streamQuery。差异是：它的 adapter 是 runtime service 的中心；我们的 5a adapter 只是 Layer 4 之后的执行出口，不能拥有 Run 状态机。
- **Spring AI 的原理**：`ChatClient` 把 prompt、tool、advisor、structured output、streaming 合成一个模型调用 DSL；Advisor chain 是模型调用边界上的 middleware。差异是：Advisor 看见的是 model-call request/response，不等于 RuntimeMiddleware 看见的 Run/HookPoint。
- **LangChain4j 的原理**：`AiServices` 用 Java interface 生成 LLM-backed service，ToolExecutor 负责 `@Tool` 反射调用、参数转换、context 注入、异常处理。差异是：这适合 5b 的 tool/model translation，但它不知道 tenant/RLS/cancel/audit。
- **LangGraph4j 的原理**：`CompiledGraph` 是 graph execution kernel，checkpoint saver 是可替换存储接口。差异是：它适合作为 5a 的一种 engine，不应反向决定 Agent Service 的 Layer 2/3/4 状态模型。
- **Spring AI Alibaba 的原理**：graph/agent/sandbox/MCP/A2A 模块组合成一套 Spring agent platform。差异是：它的 runtime、tool、admin 模块边界更偏平台集合；我们的 5a/5b 需要明确拆分执行适配与 Spring AI tool/prompt translation。

非 Java 侧说明 Layer 5 会面对多种 engine 类型：graph engine、actor runtime、crew orchestration、turn-based runner、kernel function pipeline。它们都应该是 5a/5b 的被适配对象，而不是反向重塑 Agent Service 的核心状态模型。

因此，Layer 5 的设计重点是 adapter boundary：底层框架可以很强，但进入 Agent Service 后必须被能力声明、strict matching、HookPoint emission、ContextProjector/Advisor composition 这些边界约束住。

### 8.4 可借鉴优点

1. **Adapter contract 要小而稳定**：AgentScope Runtime `AgentHandler` / `Runner` 说明 engine adapter 最好只暴露 request→stream / lifecycle / health / metadata，不要泄露底层 agent object。
2. **能力发现很重要**：LangChain4j `supportedCapabilities()`、A2A AgentCard、AgentScope protocol metadata 都提示我们 `EngineRegistry.resolve(envelope)` 不应只按 enum 匹配，还要考虑 streaming/tool/json/sandbox/checkpoint 能力。
3. **5a 与 5b 拆分正确**：Spring AI 的 ChatClient/Advisor/ToolCallback 是 model invocation pipeline；LangGraph4j/AgentScope 是 execution runtime。两类变化节奏不同，混在一层会造成低内聚。
4. **工具调用不能只靠 library hook**：Spring AI Advisor 和 LangChain4j ToolExecutor 很方便，但它们不理解 Run / tenant / policy / cancel / audit。我们要让 5b 产生 tool-shaping，真正治理仍经 Layer 4 RuntimeMiddleware。
5. **checkpoint backend 不应绑死在 engine adapter**：LangGraph4j 的 checkpoint saver 抽象很小，适合我们保持 Checkpointer SPI 极简。

### 8.4 对我们设计的判断

ADR-0140 拆分 5a/5b 是正确且必要的。开源实践也证明：

- runtime execution adapter 与 model/prompt/tool translation 是两种架构职责；
- protocol adapter 与 engine adapter 也不能混；
- tool callback / advisor 不是控制层 middleware。

建议后续增加：

```text
EngineCapability:
- supportsStreaming
- supportsToolCalling
- supportsStructuredOutput
- supportsCheckpoint
- supportsSuspendSignal
- supportsA2ADelegation
- supportedRunModes
- maxConcurrency / maxStepLimit
```

这样 `EngineRegistry.resolve(envelope)` 可以从简单类型匹配升级为 capability-aware matching。

---

## 9. 是否存在“Agent Service”等效模块：逐项判断

| 项目 | 是否有类似 Agent Service 的服务定义 | 等效模块 / 替代方案 | 与我们最大的功能差异 |
|---|---|---|---|
| A2A Java SDK | 否 | A2A server-common + `TaskManager` + `TaskStore` + `AgentExecutor` | 只定义协议任务和 executor contract，不定义内部 Run/Session/Queue/Control 服务。 |
| Spring AI A2A | 否 | A2A Spring Boot starter / controller / executor handler | 只有接入和 ChatClient bridge，没有企业级状态控制面。 |
| Spring AI Alibaba | 部分类似 | admin/studio + graph/agent framework + A2A/MCP/sandbox starters | 能力更像平台套件，职责散在多个模块；没有按 Agent Service 5 层收口。 |
| AgentScope Runtime Java | 最接近 | `AgentApp` + `Runner` + `AgentHandler` + protocol adapters + external services | 是单 agent runtime service；不内置完整 Run/Task/Queue/Task-centric governance。 |
| Temporal Java SDK | 否 | Workflow service + worker + history + activity/signal | 解决 durable execution，不解决 Agent protocol / tool governance / session context。 |
| Conductor | 部分类似 | Workflow/Task service + queue + worker + persistence | 是 workflow orchestration server；Agent/LLM 是 worker 扩展，不是核心抽象。 |
| LangGraph4j | 否 | `CompiledGraph` runtime + checkpoint saver | 是 engine kernel，不是服务控制面。 |
| LangChain4j | 否 | `AiServices` + ToolExecutor + Agentic module | 是应用 library，不管理服务级 Run/Task/Queue。 |
| Spring AI | 否 | ChatClient / Advisor / ToolCallback / MCP starters | 是模型与工具调用层，不是 Agent task service。 |
| Solon AI | 否 | Agent/ReAct/Team + AgentSession + MCP/A2A/ACP modules | 是轻量框架组合，缺少企业控制面和 durable queue。 |
| LangGraph (Python) | 部分类似 | LangGraph Server/SDK 的 thread/run/state/checkpoint API + `CompiledGraph` runtime | 是 graph workflow runtime/platform，不是多协议 Agent control service。 |
| AutoGen (Python) | 否 | AgentRuntime + message envelope + queue/subscription + Studio | 是 actor/message runtime，不拥有 Run/Task/Session 持久控制面。 |
| CrewAI (Python) | 否 | Crew/Task/Process + Flow + memory/checkpoint/event bus | 是应用级 multi-agent orchestration object，不是 tenant-first service。 |
| OpenAI Agents Python | 否 | Runner/Agent/Session/Handoff/Guardrail/Tracing/Sandbox | 是 SDK runner，不定义 HTTP ingress、durable queue 或 TaskStore。 |
| Semantic Kernel (Python) | 否 | Kernel plugins/functions/services/filters + process framework | 是 embedded invocation kernel，不拥有 Agent task service 生命周期。 |

结论：开源中存在三类“近似 Agent Service”的替代路径：

1. **Protocol-first service**：A2A Java / Spring AI A2A。先把 agent 暴露成标准 task endpoint，但内部控制面由应用自己补。
2. **Runtime-wrapper service**：AgentScope Runtime。把一个 agent framework 包成服务 endpoint，并外部化 session/state/memory/sandbox。
3. **Workflow-orchestration service**：Conductor / Temporal。先解决 durable task、worker、history、retry，再让 Agent/LLM 作为 worker 或 activity 接入。

我们的 Agent Service 选择的是第四条路径：**Agent-native control service**。它吸收前三类的优点，但把 Agent 协议、Run/Task/Session、事件队列、Task-centric control、engine adapter 放在同一个 L1 服务边界内治理。

## 9. 横向总表：开源方案是否具备 Agent Service 5 模块类似实现

| 开源项目 | Layer 1 接入 | Layer 2 会话/任务 | Layer 3 事件队列 | Layer 4 Task-Centric 控制 | Layer 5 引擎适配 | 总体相似度 |
|---|---|---|---|---|---|---|
| A2A Java SDK | 强：A2A server/client/protocol | 强：TaskManager/TaskStore/TaskState | 中：task event/push notification | 中：input_required/auth_required/cancel 状态 | 中：AgentExecutor | 高，但偏协议 SDK |
| Spring AI A2A | 强：controller/autoconfig/AgentCard | 中：依赖 A2A task model | 弱 | 中：DefaultAgentExecutor | 中：ChatClient executor | 中高，适合 Layer 1/5 thin adapter |
| Spring AI Alibaba | 强：admin/studio/A2A/MCP 入口 | 中：agent/workflow/task 结构 | 中：workflow/debug/scheduling 迹象 | 强：StateGraph/ReactAgent/WorkflowAgentExecutor | 强：Spring/graph/tool/MCP/sandbox | 高，最像 Java agent platform |
| AgentScope Runtime Java | 强：A2A/Responses API | 强：session/state/memory externalization | 中：stream events/cancel subscription | 中：Runner control | 强：AgentHandler/Runner | 高，最像 agent-as-a-service runtime |
| Temporal Java SDK | 中：client/service stubs | 中：workflow identity/history | 强：workflow/activity task queues/history | 强：signal/timer/retry/state machine | 中：activity/workflow impl adapter | 高，但不是 Agent 框架 |
| Conductor | 强：REST/gRPC workflow/task API | 强：Task/Workflow service/persistence | 强：queue/worker/poll/update | 强：workflow executor/retry/timeout/human/subworkflow | 中：workers/system tasks/AI/MCP module | 高，但偏 workflow orchestration platform |
| LangGraph4j | 弱 | 中：thread/config/checkpoint identity | 中：stream/checkpoint history | 强：interrupt/resume/checkpoint/state reducer | 强：CompiledGraph | 中高，适合 workflow runtime 内核 |
| LangChain4j | 中：A2A/MCP modules | 中：memoryId/AgenticScope | 弱 | 中：agentic workflows/HITL | 强：AiServices/tool executor/provider | 中，适合 5b/tool/RAG |
| Spring AI | 中：MCP/WebMVC/WebFlux/starter | 弱中：ChatMemory/Advisor context | 弱 | 弱中：Advisor chain | 强：ChatClient/ToolCallback/model adapters | 中，适合 5b/Spring idiom |
| Solon AI | 中：MCP/A2A/ACP | 强：AgentSession variants | 弱中 | 中：ReAct/Team/flow | 中：Agent/MCP/skill adapters | 中，轻量参考 |
| LangGraph (Python) | 中：SDK/server thread/run/state/stream | 强：thread/checkpoint/state | 中：stream/checkpoint history | 强：interrupt/resume/reducer/Pregel | 强：CompiledGraph/checkpointer | 高，适合 graph runtime 与 checkpoint |
| AutoGen (Python) | 中：FastAPI/gRPC samples/Studio | 中：AgentId/message context | 强：envelope/queue/subscription | 强：intervention/cancellation/message runtime | 中：RoutedAgent/tools/ext | 高，适合 internal runtime queue 参考 |
| CrewAI (Python) | 弱中：CLI/kickoff/enterprise pattern | 中：Crew/Task/memory/checkpoint | 中：event bus/tracing events | 强：Crew process/Flow pause-router | 强：agents/tools/knowledge | 中高，适合 multi-agent orchestration |
| OpenAI Agents Python | 弱：host app controls ingress | 中：Session/SQLite/conversation/RunState | 弱中：stream events/sandbox memory queue | 强：handoff/interruption/guardrail/next-step loop | 强：tools/MCP/sandbox/model adapters | 高，适合 agent runner 参考 |
| Semantic Kernel (Python) | 弱：embedded kernel | 中：agent thread/process state/memory | 弱 | 中：process/filter pipeline | 强：plugins/functions/services/filters | 中高，适合 5b invocation kernel |

---

## 10. 对 Agent Service 模块划分的评价

### 10.1 合理之处

#### 10.1.1 以服务控制面为中心，而不是以 Agent SDK 为中心

Spring AI、LangChain4j、LangGraph4j 都更偏 library/runtime；Conductor、Temporal 更偏 workflow platform；A2A Java 更偏 protocol SDK。我们的 Agent Service 把这些维度组合成服务控制面，符合企业级 Agent 平台需要。

#### 10.1.2 Run / Task / Session 三者拆分正确

开源项目给出的证据：

- A2A Java 的 Task 是协议控制状态；
- Temporal 的 workflow execution 是 durable execution 状态；
- AgentScope Runtime 的 session/state/memory 通过 userId/sessionId 外部化；
- LangChain4j 暴露了 memoryId 并发风险。

所以我们区分：

```text
Task = control state: 做没做、为什么停、是否需要输入
Run = execution state: 本次计算快照、状态机、checkpoint、cancel race
Session = context state: 会话历史、变量、投影
```

这个拆分合理。

#### 10.1.3 Layer 3 独立出来是必要的

虽然当前 design_only，但 Temporal / Conductor 说明 long-running execution 一定需要事件、队列、worker、history、retry 这些结构。如果没有 Layer 3，未来这些职责会侵入 Layer 4 Orchestrator 或 Layer 2 Repository。

#### 10.1.4 Layer 4 是平台差异化核心

普通 Agent 框架往往直接从 agent loop 调 tool/memory/model。我们通过 Layer 4 把 RuntimeMiddleware、SuspendSignal、Fast/Slow、child-run、S2C callback 收口，才能统一做 tenant、policy、audit、quota、cancel、resume。

#### 10.1.5 5a/5b 拆分降低耦合

Spring AI / LangChain4j 这类 model invocation pipeline 与 AgentScope / LangGraph4j 这类 execution runtime 的接口不同。5a/5b 拆分避免了 ChatAdvisor 与 RuntimeMiddleware 混淆，也避免 engine adapter 承担 prompt/tool translation 的全部变化。

### 10.2 当前不足

1. **Layer 3 还没有代码锚点**：文档已纠正 design_only，但工程上仍容易被误解为已实现。
2. **TaskStateStore 语义弱于 RunRepository**：Run 有 CAS 纪律，Task 也需要类似 transition primitive。
3. **EngineRegistry capability matching 未充分表达**：现在强调 strict matching，但未充分吸收 AgentCard / supportedCapabilities 这类能力发现实践。
4. **RuntimeMiddleware 与 ChatAdvisor 的组合仍偏概念层**：已区分机制，但缺少调用顺序、失败传播、short-circuit 的可执行 contract。
5. **Session projection / memory mutation 的并发模型仍需细化**：LangChain4j 的 memoryId 并发风险提示这里不能只靠 convention。

---

## 11. 4+1 视图设计评价

### 11.1 当前 4+1 是否合理？

合理。commit `a37a554d` 把 L1 4+1 从 review log 迁移到 `docs/L1/agent-service/`，并拆成 per-view 文件，是正确的架构治理动作。

| 视图 | 当前价值 | 与开源对照 |
|---|---|---|
| Scenarios | S1-S5 覆盖 Fast/Slow、A2A、S2C、Cancel race | 类似 Temporal/Conductor 用场景驱动状态与失败路径 |
| Logical | 5 层 + Run/Task/Session ER + state machine + RunEvent | 类似 A2A TaskState、Temporal state machine、Conductor task model |
| Process | P1-P6 序列图，尤其 cancel winner/loser | 类似 workflow engine 对 race / retry / signal 的过程建模 |
| Physical | 五平面、RLS、三轨 bus、sandbox boundary | 类似 Conductor/Temporal 的部署与持久化分离，但加入 tenant/RLS |
| Development | package tree、Layer↔Package matrix、L2 boundary contracts | 比多数开源项目更强的治理视图，适合长期多人开发 |

### 11.2 4+1 的优点

1. **防止“概念层”和“代码包结构”混淆**：ADR-0144 的 Layer↔Package matrix 很关键。
2. **能容纳 design_only 状态**：Layer 3、A2A、DualTrackRouter 都可以先在视图中占位，但必须标注未实现。
3. **支持 gate / enforcer 演进**：Development View 能把 logical component 映射到 package，后续可自动检查。
4. **适合对比开源项目**：开源项目往往只在某个视图强，例如 Temporal 强 Process/Physical，Spring AI 强 Development，A2A 强 Logical/Scenario。4+1 能把它们放在同一坐标系里。

### 11.3 4+1 的改进点

1. **增加 Open-source Pattern Appendix**：建议本文后续可以作为 `oss-comparison` 附录纳入 README cross-link，避免后续设计遗忘开源参照。
2. **在 Logical View 增加“开源参照”脚注表**：每层标注最接近开源模式，例如 Layer 2 ↔ A2A TaskStore / Layer 3 ↔ Conductor queue / Layer 4 ↔ Temporal signal。
3. **在 Development View 的 L2 Boundary Contracts 里补充 OSS-derived acceptance criteria**：例如 Internal Event Queue 的 L2 contract 可加入 Conductor-style ack/retry/dead-letter。
4. **Process View 可加入失败恢复矩阵**：参考 Temporal / Conductor，将 retry、timeout、resume、cancel loser 的 outcome 统一成表。

---

## 12. 可提取的具体改进清单

| 编号 | 改进点 | 来源实践 | 建议落点 | 优先级 |
|---|---|---|---|---|
| I-01 | `Task.A2aState` 增加 `isFinal()` / `isInterrupted()` | A2A Java `TaskState` | `service.task` | P0 |
| I-02 | `TaskStateStore` 增加 CAS-style transition primitive | A2A TaskStore + RunRepository 经验 | `service.task.spi` | P0 |
| I-03 | Layer 3 最小接口骨架：Publisher / Consumer / Lease / DeadLetter | Conductor worker queue、Temporal task queue | future `service.queue/` | P0 |
| I-04 | RunEvent channel routing 增加单测 | Temporal event history、Conductor task events | `run-event.v1.yaml` + tests | P0 |
| I-05 | Engine capability discovery | LangChain4j capabilities、A2A AgentCard、AgentScope metadata | `service.engine.spi` | P1 |
| I-06 | RuntimeMiddleware ordering / short-circuit / exception contract | Spring AI Advisor、LangChain4j tool executor | Layer 4 L2 design | P1 |
| I-07 | Checkpointer SPI 保持极小，不承载 memory/session | LangGraph4j `BaseCheckpointSaver` | orchestration SPI | P1 |
| I-08 | Protocol adapters thinness rule | AgentScope Runtime Runner | Access Layer L2 | P1 |
| I-09 | Session / memory mutation 并发纪律 | LangChain4j `@MemoryId` risk | Session / Memory L2 | P1 |
| I-10 | Human/S2C callback 与 A2A input_required 状态对齐 | A2A Java + Conductor human task + Temporal signal | S2C / Task state | P2 |
| I-11 | Admin/debug process query API | Spring AI Alibaba workflow debug、Conductor WorkflowResource | future observability/admin | P2 |
| I-12 | sandbox/tool governance schema 与 physical enforceability 对齐 | Spring AI Alibaba sandbox、AgentScope SandboxService | Middleware / Sandbox | P2 |

---

## 13. 推荐后的 L2 深挖顺序

后续如果要继续拆 L2，建议顺序如下：

1. **Layer 2 Task lifecycle L2**
   - 对比 A2A Java `TaskState` / `TaskManager` / `TaskStore`；
   - 目标：完善 `Task.A2aState` 与 `TaskStateStore` transition contract。

2. **Layer 3 Internal Event Queue L2**
   - 对比 Conductor task poll/update 与 Temporal worker/history；
   - 目标：落 Producer / Consumer / Lease / retry / dead-letter / channel routing。

3. **Layer 4 RuntimeMiddleware + SuspendSignal L2**
   - 对比 LangGraph4j interrupt/resume、Temporal signal、Conductor human task；
   - 目标：定义 HookPoint 顺序、short-circuit、failure propagation、resume semantics。

4. **Layer 5 Engine Adapter L2**
   - 对比 AgentScope Runtime `AgentHandler/Runner`、LangGraph4j `CompiledGraph`、Spring AI `ChatClient/Advisor`、LangChain4j `AiServices/ToolExecutor`；
   - 目标：定义 `EngineCapability`、adapter lifecycle、streaming、tool-call、checkpoint、structured-output 能力。

5. **Layer 1 A2A Access L2**
   - 对比 A2A Java SDK、Spring AI A2A、AgentScope Runtime A2A adapter；
   - 目标：Thin controller、AgentCard、Task Cursor、cancel/query/streaming endpoint。

---

## 14. 最终判断

Agent Service 的模块划分与 4+1 设计总体是合理的，而且比单个开源项目更完整：

- A2A Java 证明了 Task 协议状态与 interrupted/final 谓词的重要性；
- Spring AI A2A / AgentScope Runtime 证明了 protocol adapter 应该薄；
- Temporal / Conductor 证明了长周期任务必须有事件、队列、worker、history、retry、resume；
- LangGraph4j 证明了 interrupt/resume/checkpoint 应该是一等 runtime 能力；
- Spring AI / LangChain4j 证明了 prompt/tool/model translation 与 runtime control 应分离；
- Spring AI Alibaba / Solon AI 证明 Java 生态下 agent、MCP、A2A、sandbox、admin 平台化正在收敛，但各项目边界并不完全一致。
- LangGraph / AutoGen / CrewAI / OpenAI Agents Python / Semantic Kernel 证明非 Java 生态已经分别把 stateful graph、actor message runtime、multi-agent orchestration、agent runner、plugin/function kernel 做得很强，但它们仍是 runtime/framework/kernel，不是我们的 Agent-native control service。

因此，当前 Agent Service 不应退化为某个开源框架的直接封装。更合理的定位是：

> Agent Service = 多协议 Agent 接入控制面 + Run/Task/Session 状态管理 + 内部事件/恢复骨架 + Task-Centric runtime governance + 多引擎适配与 Spring AI / LangGraph / AutoGen / CrewAI / OpenAI Agents / Semantic Kernel 式工具与运行时翻译层。

下一步应优先把 Layer 2 / Layer 3 / Layer 4 的“状态转移、事件队列、Hook/Suspend 语义”从文档推进到最小可验证代码与 contract test；这比先扩展更多 engine adapter 更关键。
