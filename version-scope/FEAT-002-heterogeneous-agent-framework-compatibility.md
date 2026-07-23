---
scope: v0730
module: agent-runtime
feature_type: functional
feature_id: FEAT-002
status: active
updated: 2026-07-21
---

# 异构智能体框架兼容

## 1. 特性定位

FEAT-002 定义 `agent-runtime` 当前版本接入异构 Agent 框架的事实要求，并承载历史 agent-runtime core interface 提案中的核心 SPI 与状态边界事实。runtime 必须通过统一的 Adapter / Handler 抽象接入不同 Agent 实现，使上层标准 Agent 服务入口、Task 生命周期、SSE 输出、错误、取消、租户上下文、状态与轨迹语义不依赖具体底层框架。

本特性解决的问题是：OpenJiuwen、AgentScope、远端 REST Agent 服务以及自定义 Agent 实现有不同的 API、执行模型、流式协议、错误表面和扩展机制；`agent-runtime` 必须把这些差异约束在 adapter 内部，向 FEAT-001 定义的标准 Agent 服务入口暴露一致的执行语义。

对下游设计和实现而言，本特性是异构 Agent 接入层的事实来源。L2 设计、adapter 类、指南、示例和测试必须以本文定义的能力、边界和行为语义为准；实现中已经存在但本文未声明的能力，不能自动成为当前版本对外事实承诺。

本特性面向以下角色：

- Agent 开发者：通过框架专用基类或通用 `AgentHandler` SPI 把 Agent 挂载到 runtime。
- 平台集成方：用同一套 A2A 服务入口调用不同框架或远端服务包装出的 Agent。
- Adapter 开发者：为新框架实现执行、结果映射、错误映射、取消和可观测性适配。
- 测试与验收团队：按统一黑盒行为验证不同 adapter 是否产生一致的 Task/SSE/error 语义。

本特性定义 runtime 对异构 Agent 实现的接入与归一化要求，也定义 adapter 层必须依赖的核心 SPI、执行上下文、结果模型、state key 和单 Agent runtime 边界。标准 northbound A2A 服务入口由 `FEAT-001` 约束；智能体任务状态缓存由 `FEAT-003` 约束；远程 Agent 发现、工具安装和中断续接编排由 `FEAT-004` 约束；智能体中间件请求代理由 `FEAT-005` 约束。

在通用 Versatile REST 代理能力基础上，本特性同时支持独立部署的 Versatile 意图识别工作流接入 runtime。该类工作流仍使用同一套 `VersatileAgentHandler` 和标准 Agent 服务入口，但需要补充统一的结构化输入、结构化结果、显式用户交互中断及原工作流续接适配要求。

一层和二层意图识别工作流分别由独立 runtime 实例接入；同一个 Versatile adapter 实现通过不同实例的配置适配不同工作流，每个 runtime 实例仍只服务当前配置的一个 Agent。Adapter 负责请求转换、结果归一和原生用户交互信号转换，不承担工作流之间的业务编排、目标选择或跨 runtime 转发。

Versatile 意图识别工作流适配还面向以下角色：

- Versatile 意图工作流开发者：按照本特性约定提供工作流输入、正常结果和显式用户交互信号。
- Runtime 集成方：为独立工作流配置现有 Versatile adapter，并通过标准 Agent 服务入口发布能力。

Versatile 意图识别工作流的原生交互信号转换和续接请求适配归属本特性；Task 进入 `INPUT_REQUIRED`、同 Task 续接和多轮交互状态由 FEAT-008 约束；正常结果中的 `agent_id` 对应 FEAT-015 定义的 Agent Card 逻辑身份，目标发现与后续调用由相应特性负责。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 统一 Handler SPI | MUST | 所有本地框架 adapter 和远端代理 adapter 必须通过 `AgentHandler` 或其框架专用基类接入 runtime；A2A 层不得直接依赖具体框架 SDK。 |
| 统一结果流适配 | MUST | Adapter 的执行输出必须通过 `QueryChunk` 流承载，覆盖三类 `QueryChunk` 类型（`chunk` 增量输出、`interrupt` 中断等待输入、`error` 异常失败），并通过流控制信号表达终态（`onComplete()` = 隐式 COMPLETED，`onError()` = FAILED）。 |
| 框架中立执行上下文 | MUST | Adapter 必须以 `ServeRequest` 作为执行输入，消费其中的 `tenantId`、`userId`、`conversationId`（兼任 session）、`messages`、`stream`、`spaceId` 和 `metadata`（透传 Map）等字段。当前版本 `task`、`agent`、`stateKey`、`memoryScope` 等字段未在 `ServeRequest` 中承载，由后续特性补齐。 |
| 单 Agent runtime 执行模型 | MUST | 当前版本一个 runtime 实例只承诺服务一个 Agent。多 Handler 注册无检测告警、无 `@Order` 选择逻辑；当前实现通过 `@ConditionalOnMissingBean` 提供占位 Holder，实际注入通过 `ObjectProvider.getIfAvailable()` 获取单个 Handler。不得承诺按 `agentId` 在同一实例内路由多个 Handler。 |
| Adapter 健康与生命周期 | SHOULD | Adapter 应实现 `start()`/`stop()` 生命周期方法。当前 `AgentReadiness` 仅覆盖启动阶段一次性检查（`isProcessUp()` / `isAgentLoaded()`），`isAgentLoaded` 在 init 后永不变更，不反映运行中底层框架或远端依赖的持续健康状态。持续健康探测由后续版本补齐。 |
| 协作式取消 | MUST | Handler 无独立 `cancel()` 入口。取消通过 `QueryStreamObserver.isCancelled()` 轮询实现（协作式取消），adapter 在流式循环中定期检查该标志。取消至少要阻止 runtime 继续消费本次执行结果；是否能立即中断底层 LLM、HTTP 或框架执行，由 adapter 能力和轮询窗口决定，不能被夸大为强制中断。 |
| 框架中立错误表面 | MUST | Adapter 必须把框架原生异常、HTTP 错误、SSE 错误或未知结果映射为 `FAILED` 或 runtime 等价失败终态，使 A2A 层形成一致的 Task/error 表面；框架原生存在错误 code 时应尽量保留。只有物理 SPI 能承载相关字段时，才承诺统一的 category、retryable 等结构化分类。 |
| 框架中立轨迹接入 | OUT | 当前版本未实现轨迹接入（全仓 trajectory 零命中），由后续特性承接。 |
| OpenJiuwen Agent adapter | MUST | 当前版本通过 `JiuwenCoreAgentHandler` 托管可通过 `Runner` 执行的 OpenJiuwen Agent（含 ReActAgent、DeepAgent 及实现了 Agent 接口的 WorkflowAgent），使用 runtime 传入的 `conversationId` 作为框架会话标识来源，输出通过 `QueryChunk` 映射为 runtime 结果流。 |
| AgentScope ReActAgent adapter | MUST | 当前版本必须支持包装宿主已构建的本地 `ReActAgent`，把 `Mono<Msg>` / `Flux<AgentEvent>` 映射为 runtime query、stream、失败和暂停语义。 |
| AgentScope HarnessAgent adapter | MUST | 当前版本必须支持包装宿主已构建的本地 `HarnessAgent`；调用走 Harness 公开 API，状态读取和定向中断通过其公开 ReAct delegate 完成，对上保持与 ReAct 相同的 runtime 协议。 |
| AgentScope A2A 暂停恢复 | MUST | 当前版本必须支持 message stop、人工确认和单个 external pending tool 三类已验证暂停；tool_result interrupt 必须携带 external tool 的 `name/arguments` 供外部执行，但不得暴露内部 tool-call ID；runtime 只从原 `INPUT_REQUIRED` Task 回带可信 `_interrupt` marker，adapter 从 AgentScope 当前 state 构造原生 `ConfirmResult` / `ToolResultBlock`。 |
| AgentScope 远程 SSE client | OUT | 当前版本只接入同一 JVM 内由宿主构建的 AgentScope `ReActAgent` 和 `HarnessAgent`，不提供 AgentScope 专用 HTTP/SSE client。远端非 A2A 服务可使用 Versatile，远端 A2A Agent 由 FEAT-004 处理。 |
| Versatile REST 代理 | MUST | 当前版本必须支持把远端 REST/SSE Agent 服务代理为 runtime Agent，完成 A2A Message 到 REST request、REST/SSE response 到 `QueryChunk` 的双向转换。 |
| Versatile URL 模板 | MUST | Versatile adapter 必须支持 `{conversation_id}` 和部署配置中的 URL 变量替换，使同一 runtime state/session 能稳定映射到远端 conversation。 |
| Versatile header 与 metadata 映射 | MUST | Versatile adapter 必须支持配置 header、允许列表内 metadata header 透传、structured metadata 覆盖等映射规则，并避免未授权 metadata 任意透传。 |
| Versatile 结果提取 | SHOULD | Versatile adapter 通过 `resultNodeName` 配置指定目标节点名称，当 SSE 流中出现匹配 `node_name` 且 `node_type` 为 `"QA"` 的事件时，提取其 `text` 作为业务结果；terminal event（`node_type: "End"`）到达时形成 completed 结果。提取路径（先 `/custom_rsp_data/data`，再 `/data`）当前为硬编码。 |
| Versatile 中断检测 | MUST | 当远端 HTTP/SSE 流关闭但未观察到明确 End / terminal 事件时，adapter 必须映射为 `INTERRUPTED` 或等价 input-required 语义，而不是误报 completed。 |
| Python / Node.js sidecar 原生 adapter | OUT | 当前版本不承诺直接以 sidecar SDK 方式接入 Python / Node.js Agent。跨语言 Agent 应通过 Versatile REST 代理或独立远端 Agent 方式接入。 |
| 同实例多 Agent 路由 | OUT | 当前版本不承诺一个 runtime 实例内按 agent id 路由多个 Handler。多 Agent 部署应使用多个 runtime 实例或上层路由。 |
| AgentScope Memory / Checkpoint | OUT | 当前版本不承诺 AgentScope adapter 原生接入 runtime Memory 或 Checkpoint 中间件。 |
| Versatile 意图工作流适配 | MUST | 当前版本必须支持使用既有 `VersatileAgentHandler` 接入独立部署的 Versatile 意图识别工作流；一层和二层工作流复用同一 adapter 实现，并由不同 runtime 实例分别配置和发布。 |
| Versatile 意图工作流三字段输入 | MUST | Adapter 必须使用 `query`、`intents` 和 `messages` 调用当前工作流；`query` 为字符串，`intents` 为包含 `id`、`name` 字符串字段的 JSON 数组，`messages` 为包含 `role`、`content` 字符串字段的 JSON 数组。 |
| 当前主输入与消息映射 | MUST | Adapter 必须从 `ServeRequest` 的当前调用输入取得用户本轮原始输入并映射为 `query`，从 `messages` 映射会话消息数组。在正常两层识别旅程中，一层和二层使用同一份用户本轮原始输入；一层 `response_content` 作为补充的 `assistant` 消息进入二层消息序列，不替代二层 `query`。 |
| 意图候选配置传递 | MUST | `intents` 必须来自当前 runtime 实例的开发者或部署配置，至少包含一个候选项。Adapter 负责数组结构、`id`、`name` 的技术校验和透传，不从 Agent Card 或用户输入推导候选意图。 |
| Versatile 意图工作流三字段结果 | MUST | 工作流正常完成时，Adapter 必须提取并以机器可读形式保留 `response_content`、`intent_id` 和 `agent_id`；三个字段必须为字符串，`intent_id`、`agent_id` 必须为非空值。 |
| 唯一下一跳 Agent 身份 | MUST | 一次正常结果必须只包含一个非空 `agent_id`，并表达当前 tenant 下可与 FEAT-015 Agent Card 逻辑 `agentId` 对应的目标身份；多目标或无法确定唯一值时必须形成可诊断失败。 |
| 正常业务结果一致转换 | MUST | Versatile 意图识别工作流以完整三字段表达匹配成功、未匹配或需要澄清时，Adapter 必须按相同技术流程转换并映射为 `COMPLETED`；Adapter 不解释业务含义，也不执行候选目标选择。 |
| Versatile 显式用户交互适配 | MUST | 意图识别工作流显式请求用户输入并提供用户提示和原执行续接关联时，Adapter 必须映射为 `QueryChunk(TYPE_INTERRUPT)`；同一 Task 的后续用户输入重新进入原 adapter 后，Adapter 必须使用稳定会话标识和工作流公开的续接信息组装原工作流续接请求。 |
| Versatile 多轮交互适配 | MUST | 工作流续接后再次请求用户输入时，Adapter 必须继续映射为中断；工作流完成或失败时分别映射为标准完成或失败语义。Task 等待状态、同 Task 续接和交互轮次由 FEAT-008 约束。 |
| Versatile 意图工作流结构化失败 | MUST | 输入或配置缺失、数组结构非法、正常结果字段缺失或类型错误、目标不唯一、远端调用失败、结果解析失败以及续接失败必须形成可诊断的标准失败；异常断流不得误报为完成或虚构用户交互问题。 |

## 3. 外部接口与入口要求

| 入口 | 类型 | 事实要求 |
|---|---|---|
| `AgentHandler` | Java SPI | 必须作为所有 adapter 被 runtime 调用的统一执行入口，提供 `query()`（同步执行）、`streamQuery()`（异步流式执行）、`start()`/`stop()` 生命周期和 `clearSession(conversationId)` 会话清理。 |
| `ServeRequest` | Java runtime context | 承载 adapter 执行输入，字段包括 `tenantId`、`userId`、`conversationId`、`spaceId`、`messages`、`stream`、`metadata`。当前版本 `task`（A2A task id）、`agent`（agent id）、`stateKey`（独立于 conversationId）、`memoryScope` 等字段未承载，由后续特性补齐。 |
| `QueryChunk` | Java result model | 作为 adapter 到 runtime 的标准结果表面，包含三类：`TYPE_CHUNK`（增量输出，可多次出现）、`TYPE_INTERRUPT`（中断等待输入）、`TYPE_ERROR`（异常失败）。COMPLETED 由 `observer.onComplete()` 隐式表达，FAILED 由 `observer.onError()` 表达，二者不在 `QueryChunk` 数据模型中。 |
| `agentStateKey` / `stateKey` | Runtime state boundary | 当前版本 `conversationId` 直接作为 session key 使用，无独立 `stateKey` 派生逻辑。`JiuwenCoreAgentHandler.runnerSession()` 直接用 `conversationId` 构造 Runner session；跨租户时 `conversationId` 碰撞可能导致会话串扰。独立 `stateKey` 派生规则由后续特性补齐。 |
| `JiuwenCoreAgentHandler` | Java adapter base | OpenJiuwen Agent 通用 adapter，接受任意 `Object agent`，通过反射调用 `Runner.runAgentStreaming(agent, ...)` 执行，不区分 ReActAgent / DeepAgent / WorkflowAgent 类型。 |
| `VersatileAgentHandler` | Java adapter base | 必须作为远端 REST/SSE 服务代理入口，并可提供 Agent Card 信息供 A2A 发现。 |
| `openjiuwen.service.versatile.*` | YAML configuration | 承载 Versatile URL、timeout、URL variables、query params、headers、passthrough headers、input metadata keys 和 `resultNodeName`。 |
| `GET /.well-known/agent-card.json` | HTTP endpoint | 不属于 adapter 私有入口；任何 adapter 挂载出的 Agent 都必须通过 FEAT-001 的 Agent Card 发现表面暴露能力。 |
| `POST /a2a` | HTTP endpoint | 不属于 adapter 私有入口；任何 adapter 挂载出的 Agent 都必须通过 FEAT-001 的标准 A2A JSON-RPC/SSE 表面被调用。 |


## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 挂载 OpenJiuwen ReActAgent | 应用已引入 OpenJiuwen adapter，开发者能构建 `ReActAgent` | 开发者继承 `JiuwenCoreAgentHandler` 并注册为 Spring Bean | runtime 通过标准 A2A 入口调用该 Agent；OpenJiuwen 输出被映射为 Task/SSE/Artifact/terminal 状态。 |
| 代理远端 REST Agent 服务 | 远端服务提供 REST endpoint 和 SSE/JSON 响应 | 开发者注册 `VersatileAgentHandler` 并配置 `openjiuwen.service.versatile.*` | 调用方仍发送标准 A2A 请求；adapter 组装 REST request，解析远端 response 并返回标准 Task/SSE 结果。 |
| 远端服务需要会话连续性 | runtime 输入带有 session/context/task 语义 | Versatile adapter 用 `{conversation_id}` 或配置字段构造远端 URL | 同一 runtime state/session 稳定映射到远端 conversation，避免跨会话串扰。 |
| Agent 等待用户输入 | JiuwenCore Agent 或 Versatile 远端服务产生中断语义 | 调用方收到 `INPUT_REQUIRED` 后用同 task/context 续接 | adapter 必须使用同一 `conversationId`、task/context 或远端 continuation 信息发起续接调用；内部恢复上下文由框架或远端服务自治，adapter 不直接读写缓存 payload。 |
| 调用方取消任务 | A2A client 调用标准 cancel | runtime 通过 `QueryStreamObserver.isCancelled()` 轮询通知 handler | adapter 在流式循环中检测到取消后停止结果消费并让 Task 表面进入取消语义；底层是否立即停止由 adapter 轮询窗口和框架能力决定。 |
| 多 Handler 被同时注册 | Spring 容器中存在多个 `AgentHandler` Bean | runtime 启动并选择执行 Handler | 当前实现无多 Handler 检测、无 `@Order` 选择、无 WARN 日志；通过 `ObjectProvider.getIfAvailable()` 获取 Handler，多 Handler 场景行为未定义。不得对外承诺同实例多 Agent 路由。 |
| 调用一层意图识别工作流 | 一层 Versatile 工作流已独立部署，并由一个 runtime 实例通过 `VersatileAgentHandler` 接入 | 调用方通过标准 Agent 服务入口提交用户本轮原始输入 | Adapter 将当前主输入映射为 `query`，结合当前实例的 `intents` 和消息上下文调用一层工作流；工作流正常完成后返回机器可读的三字段结果。 |
| 调用二层意图识别工作流 | 二层 Versatile 工作流已独立部署，并由另一个 runtime 实例通过相同 adapter 实现接入 | 调用方将同一份用户本轮原始输入作为二层主输入，并在消息序列中携带一层 `response_content` 作为补充的 `assistant` 消息 | Adapter 将用户原始输入映射为二层 `query`，将当前实例配置映射为 `intents`，将已有消息序列映射为 `messages`；一层结果不替代二层 `query`，Adapter 不负责跨 runtime 转发。 |
| 分类错误后重新调用一层工作流 | 调用方已形成新的调用主输入并重新调用一层 Agent | runtime 将本次请求交给一层 Adapter | Adapter 按普通调用规则重新映射输入并调用当前配置的工作流；重新分类原因不改变 Adapter 行为。 |
| 意图工作流返回正常结果 | 当前一层或二层工作流按照接入契约返回完整三字段结果 | Adapter 提取工作流结果 | Adapter 以机器可读形式保留 `response_content`、`intent_id`、唯一 `agent_id` 并映射为 `COMPLETED`；匹配成功、未匹配和需要澄清使用相同技术流程。 |
| 意图工作流请求用户交互 | 当前工作流显式请求用户补充、确认或选择，并提供用户提示与原执行续接关联 | Adapter 识别工作流原生交互信号 | Adapter 映射为 `QueryChunk(TYPE_INTERRUPT)`；FEAT-008 将 Task 推进到 `INPUT_REQUIRED`，调用方通过 FEAT-001 标准入口使用同一 Task 续接。 |
| 意图工作流多轮用户交互 | 同一 Task 的合法续接输入已经重新进入原 Adapter，且原工作流恢复后再次请求用户输入 | Adapter 消费恢复后的工作流结果 | Adapter 再次映射为中断；每轮 Task 状态和交互关联由 FEAT-008 管理。 |
| 意图工作流续接失败 | 原工作流的续接信息缺失、失效或远端续接请求失败 | runtime 使用现有 Handler 入口重新调用原 Adapter | Adapter 返回可区分续接阶段的标准失败，不把续接失败映射为正常完成，也不创建新的工作流执行代替原执行。 |
| 意图工作流输入或结果不满足契约 | 必填输入、配置或正常结果字段缺失、类型错误，或者 `agent_id` 不是唯一值 | Adapter 组装请求或提取结果 | Adapter 在对应阶段返回可诊断失败，不输出可被误认为完整正常结果的部分数据。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 Adapter 归一语义

- Adapter 是框架差异的吸收层，不是新的 northbound 协议层。
- A2A controller、Task store、readiness gate、trajectory、state/memory scope 必须看到统一的 `AgentHandler` 执行表面。
- Adapter 不得把 OpenJiuwen、AgentScope、Versatile 或其他框架的内部状态机直接暴露为 A2A Task 状态机。
- Adapter 可以保留框架内部 session、conversation、workflow node、remote request id 等字段，但这些字段必须稳定映射到 runtime context 或 metadata，不能覆盖 runtime 的 task/session/tenant 事实字段。
- Adapter 不得实现或代理框架 checkpointer/cache 的读写策略；它只能把 runtime 拥有的 state key、task/context 和调用生命周期信号传给框架。
- 框架 hook、rail、tool、skill、middleware、callback 等扩展机制不属于本特性承诺的 adapter 能力；如果框架或 Agent 开发者在内部使用这些机制，adapter 只观察最终执行结果。

#### 5.1.2 执行与结果映射语义

- Handler 通过 `query(ServeRequest)`（同步）或 `streamQuery(ServeRequest, QueryStreamObserver)`（异步）执行 Agent；即使底层框架同步阻塞，也必须通过 `QueryStreamObserver` 消费结果。
- 增量输出通过 `QueryChunk(TYPE_CHUNK, data)` 承载，可在流中多次出现（LLM 每生成 token/segment 即发一个 chunk）。
- 需要用户输入或连接关闭未终止时通过 `QueryChunk(TYPE_INTERRUPT, message)` 表达，之后 `onComplete()` 被抑制。
- 异常或不可恢复错误通过 `QueryChunk(TYPE_ERROR, error)` + `observer.onError()` 表达 FAILED 终态。
- 正常完成通过 `observer.onComplete()` 隐式表达 COMPLETED 终态（前置无 `TYPE_INTERRUPT` chunk）。
- FAILED 完全由异常驱动（Java 异常或 `TYPE_ERROR` chunk + `onError()`）；Agent 无法以数据方式主动声明自己失败，只能依赖异常传播路径。
- `COMPLETED` 不得用于表示”远端还在等待用户输入”或”连接意外关闭但没有 terminal event”的情况。
- 未知原生结果类型必须失败或被显式过滤，不得以空 completed 掩盖。

#### 5.1.3 OpenJiuwen Agent 通用语义

- `JiuwenCoreAgentHandler` 是统一的 OpenJiuwen Agent 通用 adapter，通过反射调用 `Runner.runAgentStreaming(agent, ...)` 执行 Agent，不区分 ReActAgent、DeepAgent 或 WorkflowAgent 类型。
- 通过 `conversationId` 维持会话连续性；`conversationId` 直接作为 `Runner` session key 使用，无独立 `stateKey` 派生逻辑。
- OpenJiuwen streaming runner 输出的 chunk 映射为 runtime `QueryChunk` 流。
- Agent 内部 hook、rail、tool、skill、memory 和 checkpoint 能力由 OpenJiuwen 或智能体开发者自治，adapter 不声明这些机制的安装、编排或缓存读写承诺。
- cancel 通过 `QueryStreamObserver.isCancelled()` 轮询实现（协作式取消），handler 无独立 `cancel()` 入口。底层 LLM 调用是否立即中断由框架决定。

#### 5.1.5 AgentScope 语义

- AgentScope 本地 `ReActAgent` 和 `HarnessAgent` 的结果与事件必须由同一 adapter 映射为 runtime 等价的输出、完成、失败和可恢复暂停语义；当前版本不包含 AgentScope 专用远程 HTTP/SSE 模式。
- AgentScope 暂停恢复只承诺已实现的三类：`message` 使用空消息列表续跑，`confirmation` 将精确 `APPROVE/REJECT` 转为 `ConfirmResult`，`tool_result` 在第一轮输出当前唯一 external pending tool 的 `name/arguments`，并在第二轮根据 AgentScope state 构造 `ToolResultBlock`。不承诺多 external pending、自然语言确认、客户端回传内部 tool ID 或非 A2A 恢复。
- 当前接入的 AgentScope Java 2.0 本地 API 以 `Throwable` 表达执行失败，没有稳定的 code-bearing error event 契约；adapter 必须映射为 `FAILED` 或 runtime 等价失败终态并保留异常因果链，但不承诺不存在的原生错误 code。
- 当前版本不适配 AgentScope PROGRESS 轨迹；不得从文本增量或普通事件伪造 PROGRESS/MODEL_CALL 轨迹。
- 当前版本不承诺 AgentScope 原生 Memory 或 Checkpoint 适配。

#### 5.1.4 Versatile REST 代理语义

- Versatile adapter 必须把 A2A text input 和 metadata 映射为远端 REST request body、URL、query 和 header。
- `{conversation_id}` 必须由 runtime state/session 语义派生，确保远端 conversation 与 runtime 会话边界一致。
- Header 透传必须受 allowlist 或 structured metadata 规则控制；不得默认透传所有调用方 metadata。
- 远端 SSE 事件映射：普通 SSE data → `QueryChunk(TYPE_CHUNK)` 增量输出；`event: "exception"` → `QueryChunk(TYPE_ERROR)` + `onError()` = FAILED；流关闭且 `isCompleted=true` 且有 result → 最后发 answer envelope + `onComplete()` = COMPLETED；流关闭但无 End/terminal event → `QueryChunk(TYPE_INTERRUPT)` = INPUT_REQUIRED。
- INPUT_REQUIRED 的 message 来自非 interrupt chunk 的 data 文本拼接或兜底硬编码 `"Remote agent requires input"`；当前无机制将远端具体中断原因结构化传递。
- result extraction 通过 `resultNodeName` 匹配 `node_name` 且 `node_type == "QA"` 的事件提取 `text`；只能影响 completed payload 的组装，不能改变 terminal 状态语义。
- Versatile adapter 和 A2A 扩展位于 `agent-solution` 项目（扩展/示例性质），不在 `agent-runtime-java` 核心模块中。仅引入 `agent-runtime-java` 依赖时 Versatile REST 代理能力不可用。

##### Versatile 意图识别工作流专项适配语义

上述通用 Versatile 语义继续适用于普通远端 REST/SSE Agent 服务。接入符合以下专项契约的意图识别工作流时，`VersatileAgentHandler` 还必须完成本节定义的结构化输入输出和显式用户交互适配。

**部署与执行语义**

- 一层和二层意图识别工作流必须分别独立部署，并由不同 runtime 实例通过同一个 Versatile adapter 实现接入；一个 runtime 实例只调用当前实例配置的一个工作流。
- 工作流层级差异通过部署配置表达，不形成两套公开 Adapter 类或执行函数。
- 每次 Handler 执行只调用当前实例配置的工作流。工作流结束或中断后，Adapter 返回标准结果；一层到二层、业务工作流到一层的调用关系由调用方负责。

**输入契约**

所有 Versatile 意图识别工作流使用相同的三字段输入契约：

| 字段 | 要求 | 输入来源 | 事实语义 |
|---|---|---|---|
| `query` | MUST | `ServeRequest` 当前调用消息中的主输入 | 当前工作流需要处理的主要内容。正常的一层和二层调用均使用用户本轮原始输入，Adapter 原样映射，不解释其业务来源。 |
| `intents` | MUST | 当前 runtime 实例的开发者或部署配置 | 当前工作流可识别的候选意图 JSON 数组；必须至少包含一个元素。 |
| `intents[].id` | MUST | `intents` 配置项 | 候选意图标识，必须为非空字符串。 |
| `intents[].name` | MUST | `intents` 配置项 | 候选意图名称，必须为非空字符串；Adapter 不解释其业务含义。 |
| `messages` | MUST | `ServeRequest.messages` | 当前工作流可使用的会话消息 JSON 数组；必须至少包含一条消息，Adapter 按消息顺序映射 `role`、`content`。 |
| `messages[].role` | MUST | `ServeRequest` 消息项 | 当前消息角色，必须为非空字符串；Adapter 不推断或改写角色。 |
| `messages[].content` | MUST | `ServeRequest` 消息项 | 当前消息内容，必须为非空字符串；Adapter 不生成、总结或改写内容。 |

输入处理遵守以下语义：

- 正常两层识别旅程中，一层和二层使用同一份用户本轮原始输入作为各自的 `query`。一层 `response_content` 作为补充的 `assistant` 消息加入二层调用的 `messages`，不替代二层 `query`。
- 重新分类旅程中，调用方可以把业务工作流形成的重分类上下文作为新一层调用的当前主输入；Adapter 仍按普通调用执行相同映射，不识别重新分类原因。
- Adapter 从当前执行输入映射已有消息，不调用模型生成字段，不推断消息角色，不拼接、总结或改写会话内容。
- Adapter 只校验 `query`、`intents`、`messages` 的必填性、数组结构、元素字段类型和配置完整性，不判断候选意图或消息内容在业务上是否正确。
- 任一必填输入无法取得、数组为空或数组元素缺少必填字符串字段时，Adapter 必须在发起远端调用前形成可诊断的输入错误。

**正常结果契约与转换语义**

Versatile 意图识别工作流每次正常完成必须返回以下三字段结果：

| 字段 | 类型 | 要求 | 事实语义 |
|---|---|---|---|
| `response_content` | `string` | MUST | 当前工作流返回给调用方后续处理的主要业务内容；字段必须存在，是否允许空字符串由工作流自身的结果契约决定。 |
| `intent_id` | `string` | MUST | 低码平台生成的下一跳工作流标识，用于业务关联、透传和审计；必须为非空字符串，不作为 Agent Card 查询条件。 |
| `agent_id` | `string` | MUST | 当前 tenant 下与 FEAT-015 逻辑 `agentId` 对应的唯一下一跳目标标识；必须为单个非空字符串。 |

结果处理遵守以下语义：

- Adapter 必须使用既有 Versatile 结果提取机制取得三个字段，并在既有 `QueryChunk` 结果表面中以机器可读形式完整保留，不得要求调用方从自然语言中推导目标。
- 本专项适配不新增结果类；三个字段在 `QueryChunk` 数据中的具体承载结构由 L2 设计明确。
- 一个 `intent_id` 可以在工作流内部对应多个候选 `agent_id`，但工作流必须完成本次选择并只返回一个 `agent_id`。Adapter 不执行第二次业务选择。
- Adapter 只校验 `agent_id` 的存在性、字符串类型和唯一性，不查询注册中心验证目标是否存在或可路由。
- 匹配成功、未匹配和需要澄清均属于正常业务结果。工作流通过预定义的 `intent_id`、唯一 `agent_id` 和 `response_content` 表达结果，Adapter 执行相同技术转换并映射为 `COMPLETED`。

**用户交互中断与续接语义**

- 意图识别工作流只有在显式返回用户交互信号，并提供足以表达用户提示和关联原执行的信息时，Adapter 才能将其映射为 `QueryChunk(TYPE_INTERRUPT)`。
- 通用 Versatile 的“无 terminal event 连接关闭”检测仍用于防止误报完成；仅有连接关闭而没有完整显式交互信息时，Adapter 不得虚构用户问题、输入要求或可恢复的业务中断。
- Task 进入 `INPUT_REQUIRED`、同 Task 续接、交互轮次和等待点推进由 FEAT-008 约束；客户端续接消息格式、访问校验和标准入口由 FEAT-001 约束。
- 同一 Task 的合法续接消息重新进入原 Handler 后，Adapter 必须从 `ServeRequest` 取得本轮用户输入，并结合稳定的 `conversationId` 和工作流公开的续接信息组装原工作流续接请求。
- 用户响应的业务有效性由恢复后的 Versatile 工作流判断。工作流可以完成、失败或再次请求用户交互，Adapter 必须重新映射为相应的标准结果。
- Adapter 只传递 runtime 拥有的稳定会话标识、工作流公开的续接信息和本轮用户输入，不定义或治理远端工作流的 checkpoint、执行快照和持久化策略。

**失败、取消与可观测语义**

- Adapter 不把远端技术失败转换为正常 `intent_id`，也不生成匹配、澄清或未匹配的业务兜底结果。
- 输入配置缺失、请求组装失败、正常结果字段缺失或类型错误、`agent_id` 不唯一、远端调用失败、解析失败和续接失败必须形成可诊断的失败表面。
- 取消继续遵守 `QueryStreamObserver.isCancelled()` 的协作式取消语义；Adapter 停止消费本次结果，并在远端协议支持时尽力通知工作流终止对应执行。
- 工作流调用、结果、中断、续接和失败必须关联 runtime 可提供的 tenant、conversation、request、trace 和 correlation 语义；日志和错误信息不得无控制地记录完整用户输入、`messages`、工作流响应或用户交互响应。

#### 5.1.6 错误、取消与可观测性结果

| 场景 | 事实要求 |
|---|---|
| handler 未就绪 | init 阶段未完成时 `AgentHandlerHolder.requireHandler()` 抛出 `IllegalStateException` 拒绝执行（启动时序保护）。当前版本无运行时健康拒绝机制；若 handler 在 init 后变为不健康，无 `isHealthy()` 方法可供运行时查询。 |
| adapter 创建底层 Agent 失败 | 必须映射为 failed Task/error，并记录可诊断错误。 |
| 框架同步调用异常 | 必须映射为 `FAILED`；不得让异常绕过标准 Task/error 表面。 |
| 远端 HTTP 超时 | 当前 Versatile adapter 将 HTTP 错误统一包装为 `IllegalStateException("Versatile invocation failed")`，状态码在日志中可见但丢失于异常链。可分类的失败错误由后续版本补齐。 |
| 远端 HTTP 4xx/5xx | 当前同上，状态码未保留在异常链中供上游分类。 |
| SSE 解析失败 | 应记录并按可恢复性选择跳过单帧或失败整个任务；不得输出破损事件给 A2A 调用方。 |
| 流关闭但无 terminal event | 必须映射为 `INTERRUPTED` 或失败，不能映射为 completed。 |
| cancel requested | 必须停止 runtime 对该执行流的继续消费，并尽力通知底层框架或远端请求。 |
| trajectory fields | Adapter 发出的轨迹必须带有 runtime stamped context/task/agent/correlation 语义；敏感字段遵守 DFX 掩码规则。 |
| 意图工作流输入或配置非法 | 必须在远端调用前形成可诊断失败，并指出失败发生在配置读取或请求组装阶段。 |
| 意图工作流正常结果字段非法 | 字段缺失、类型错误或 `agent_id` 不唯一时必须失败，不得输出可被误认为完整结果的部分数据。 |
| 意图工作流交互信息不完整 | 工作流表示需要用户输入但未提供用户提示或原执行续接关联时，必须形成可诊断失败或通用技术中断，不得虚构业务问题。 |
| 意图工作流续接失败 | 续接信息缺失、失效或远端续接请求失败时必须保留可区分的续接失败阶段，不得以新执行替代原执行。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 同实例多 Agent 路由 | 不承诺在一个 runtime 实例内按 `agentId` 路由多个 Handler；多 Agent 应多实例部署或由上层网关路由。 |
| Adapter 私有 northbound endpoint | 不承诺为 OpenJiuwen、AgentScope 或 Versatile 暴露绕过 FEAT-001 的私有执行 endpoint。 |
| 状态缓存归属边界 | 异构框架 adapter 不直接访问、不配置、不治理智能体框架的 checkpointer/cache payload；adapter 只传递 runtime 拥有的 state key、task/context 和调用生命周期信号。框架公开 API 返回的 live-state projection 可被只读用于协议转换，但不得被 adapter 持久化为第二份恢复状态或作为直接治理底层 store 的入口。runtime 任务状态缓存、revision、fencing、TTL 与租户隔离由 FEAT-003 约束；框架内部执行快照由框架或智能体开发者自治。 |
| 框架扩展机制自治边界 | 异构框架 adapter 不承诺、不安装、不编排、不治理框架 hook、rail、tool、skill、middleware、callback 等扩展机制。这些机制应由智能体框架提供，或由智能体开发者在构建 Agent 时自定义；adapter 只负责请求桥接、调用执行和结果归一。 |
| 强制中断底层模型调用 | `cancel` 不承诺立即中断已经进入底层 LLM 或远端服务的阻塞调用。 |
| AgentScope Workflow | 不承诺 AgentScope Workflow 适配。 |
| AgentScope Memory / Checkpoint | 不承诺 AgentScope adapter 接入 runtime MemoryProvider 或 Checkpoint。 |
| AgentScope 远程 SSE / PROGRESS | 不承诺 AgentScope 专用远程 HTTP/SSE client，也不承诺 AgentScope PROGRESS 轨迹映射。 |
| Python / Node.js 原生 sidecar | 不承诺直接通过进程内 SDK 或 sidecar 协议接入非 Java Agent；应使用 Versatile 或远程 A2A Agent。 |
| MCP 作为 Agent adapter | MCP 是工具服务协议，不是本特性的异构智能体框架 adapter。若智能体框架自身具备调用模型或调用 MCP 服务的能力，该能力由框架或智能体开发者自治，agent-runtime 异构适配不做显式承诺。 |
| 客户端 facade 替代 Versatile | FEAT-006 的标准 agent-client facade 面向业务应用侧客户端调用；Versatile adapter 面向代理远端 Agent 服务，二者不得互相替代事实边界。 |
| 意图工作流部署与发布 | 一层、二层工作流的独立部署、runtime 实例配置、标准服务入口和 Agent Card 由部署与集成流程完成；同一 adapter 实现只适配当前实例配置的一个工作流。 |
| 意图工作流输入输出职责 | 调用方按照三字段输入契约提供请求，Versatile 意图识别工作流按照三字段正常结果和显式用户交互信号契约返回执行事实；未匹配、需要澄清、匹配成功及候选目标选择由工作流表达，Adapter 只校验、转换和归一工作流已经返回的事实。 |
| 意图工作流业务匹配与兜底 | Adapter 不负责解释意图、选择候选 `agent_id` 或生成业务失败兜底；相关判断由意图识别工作流或其调用方完成。 |
| 结构化结果后续消费 | Adapter 返回机器可读的 `response_content`、`intent_id` 和唯一 `agent_id`；Agent Card 查询、实例路由及下游 Agent 调用由对应注册发现、路由和远程调用特性负责。 |
| 意图工作流用户交互职责 | Adapter 负责原生交互信号转换和 Versatile 续接请求适配；Task 状态、同 Task 续接和交互轮次由 FEAT-008 约束，客户端消息格式和访问校验由 FEAT-001 约束，用户响应的业务有效性由产生交互信号的工作流判断。 |
| Versatile 意图工作流原生协议 | 原生交互事件、字段路径、恢复请求格式和具体配置项由 L2 设计在既有 `openjiuwen.service.versatile.*` 体系内明确，并满足本特性行为契约。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本文作为异构 Agent adapter 层的事实来源，不能把旧实现限制或新增代码能力未经声明地写成事实承诺。
- A2A 层必须只依赖 `AgentHandler` / `QueryChunk` 等框架中立表面，不得导入 OpenJiuwen、AgentScope、Versatile 私有类型。
- 新增 adapter 必须提供执行入口、结果映射、失败终态映射、取消语义、健康检查策略、配置说明和至少一个可运行示例；原生错误 code 存在时应验证其保留行为，统一错误分类只在物理 SPI 支持时验证；不得把框架 cache/checkpointer 读写、hook/rail/tool/skill 编排写成本特性承诺。
- OpenJiuwen Agent 通过 `JiuwenCoreAgentHandler` 统一托管；ReActAgent、DeepAgent 和实现了 Agent 接口的 WorkflowAgent 均可通过 `Runner` 执行，不区分专用 adapter 入口。
- Versatile adapter 的 URL、header、metadata、result extraction 和中断检测规则必须被测试覆盖，尤其要覆盖“无 End 连接关闭不得 completed”的边界。
- AgentScope adapter 必须覆盖本地 ReAct/Harness 的正常完成、失败终态、无业务终态断流、暂停恢复和取消边界；当前本地 API 没有稳定原生错误 code 时不要求 code 保留测试。远程 SSE、PROGRESS、Memory/Checkpoint 不得在未实现前写入 guide 作为当前承诺能力。
- 多 Handler 注册只能作为兼容降级路径处理；任何同实例多 Agent 路由设计必须先更新本特性或新增 version-scope 特性。
- 若未来要支持 Python/Node sidecar、AgentScope Workflow、AgentScope 远程 SSE/PROGRESS、强制取消、多 Agent 路由，或由 runtime 统一治理框架 hook/tool/skill/cache，必须先更新当前版本事实要求，再进入 L2 和实现。
- Versatile 意图识别工作流的 L2 设计必须复用 `VersatileAgentHandler`、`AgentHandler`、`ServeRequest`、`QueryChunk` 和既有 `openjiuwen.service.versatile.*` 配置体系，不得另建职责重复的公开 Handler、执行函数、结果类型或中断类型。
- L2 设计必须明确 `query`、`intents`、`messages` 的组装规则，以及 `messages` 从 `ServeRequest.messages` 提取 `role`、`content` 的映射规则；开发者指南必须说明消息权限、最大条数、长度、编码、截断和敏感信息处理。
- L2 设计必须明确三字段正常结果在既有 `QueryChunk` 数据中的机器可读承载方式、显式用户交互信号识别规则和原工作流续接请求映射；具体原生字段路径和配置项名称不得上升为新的公开 Runtime SPI。
- 意图工作流输入输出测试必须覆盖一层和二层使用同一份用户本轮原始输入、一层 `response_content` 作为补充 `assistant` 消息进入二层 `messages` 且不替代二层 `query`、重新分类调用、数组读取与序列化、空数组、非法元素、完整三字段结果、未匹配、需要澄清、字段缺失、类型错误和多目标 `agent_id`。
- 意图工作流中断续接测试必须覆盖显式交互信号到 `QueryChunk(TYPE_INTERRUPT)` 的转换、同 Task 用户输入到 Versatile 续接请求的映射、续接后再次中断、续接信息不可用、远端续接失败，以及无 terminal event 的异常断流不得伪造用户交互请求；Task 投影和交互轮次按照 FEAT-008 验证。
- 意图工作流失败与可观测测试必须覆盖远端 HTTP/SSE 错误、超时、输入组装失败、结果解析失败、协作式取消、敏感字段保护及调用、中断、续接的 trace/correlation 关联。

## 7. 关联文档

- `architecture/L2-Low-Level-Design/agent-runtime/FEAT-002-heterogeneous-agent-framework-compatibility.md`
- `../spring-ai-ascend/architecture/L0-Top-Level-Design/boundaries.md`
- `../spring-ai-ascend/architecture/L0-Top-Level-Design/glossary.md`
- `../spring-ai-ascend/architecture/L1-High-Level-Design/agent-runtime/README.md`
- `../spring-ai-ascend/architecture/L1-High-Level-Design/agent-runtime/logical.md`
- `../spring-ai-ascend/architecture/L1-High-Level-Design/agent-runtime/process.md`
- `../spring-ai-ascend/architecture/L1-High-Level-Design/agent-runtime/scenarios.md`
- `../spring-ai-ascend/architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md`
- `../spring-ai-ascend/version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `../spring-ai-ascend/version-scope/FEAT-008-user-interaction-interrupt-and-response.md`
- `../spring-ai-ascend/version-scope/Feat-015-agent-card-registration-and-discovery.md`
- `../spring-ai-ascend/version-scope/DFX-001-trajectory-observability.md`
