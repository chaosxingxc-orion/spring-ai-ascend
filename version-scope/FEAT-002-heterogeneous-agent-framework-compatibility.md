---
version: 0715
module: agent-runtime
feature_type: functional
feature_id: FEAT-002
status: active
merged_from:
  - agent-runtime-core-interface
---

# 异构智能体框架兼容特性文档

## 1. 特性定位

FEAT-002 定义 `agent-runtime` 当前版本接入异构 Agent 框架的事实要求，并承载历史 agent-runtime core interface 提案中的核心 SPI 与状态边界事实。runtime 必须通过统一的 Adapter / Handler 抽象接入不同 Agent 实现，使上层标准 Agent 服务入口、Task 生命周期、SSE 输出、错误、取消、租户上下文、状态与轨迹语义不依赖具体底层框架。

本特性解决的问题是：OpenJiuwen、AgentScope、远端 REST Agent 服务以及自定义 Agent 实现有不同的 API、执行模型、流式协议、错误表面和扩展机制；`agent-runtime` 必须把这些差异约束在 adapter 内部，向 FEAT-001 定义的标准 Agent 服务入口暴露一致的执行语义。

对下游设计和实现而言，本特性是异构 Agent 接入层的事实来源。L2 设计、adapter 类、指南、示例和测试必须以本文定义的能力、边界和行为语义为准；实现中已经存在但本文未声明的能力，不能自动成为当前版本对外事实承诺。

本特性面向以下角色：

- Agent 开发者：通过框架专用基类或通用 `AgentRuntimeHandler` SPI 把 Agent 挂载到 runtime。
- 平台集成方：用同一套 A2A 服务入口调用不同框架或远端服务包装出的 Agent。
- Adapter 开发者：为新框架实现执行、结果映射、错误映射、取消和可观测性适配。
- 测试与验收团队：按统一黑盒行为验证不同 adapter 是否产生一致的 Task/SSE/error 语义。

本特性定义 runtime 对异构 Agent 实现的接入与归一化要求，也定义 adapter 层必须依赖的核心 SPI、执行上下文、结果模型、state key 和单 Agent runtime 边界。标准 northbound A2A 服务入口由 `FEAT-001` 约束；智能体任务状态缓存由 `FEAT-003` 约束；远程 Agent 发现、工具安装和中断续接编排由 `FEAT-005` 约束。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 统一 Handler SPI | MUST | 所有本地框架 adapter 和远端代理 adapter 必须通过 `AgentRuntimeHandler` 或其框架专用基类接入 runtime；A2A 层不得直接依赖具体框架 SDK。 |
| 统一结果流适配 | MUST | Adapter 的原生输出必须通过 `StreamAdapter` 或等价映射转为 `AgentExecutionResult` 流，并覆盖 `OUTPUT`、`COMPLETED`、`FAILED`、`INTERRUPTED` 四类结果语义。 |
| 框架中立执行上下文 | MUST | Adapter 必须以 `AgentExecutionContext` 作为执行输入，消费其中的 tenant、user、session、task、agent、message、metadata、state key 等运行时上下文，不得重新定义与 runtime 冲突的身份字段。 |
| 单 Agent runtime 执行模型 | MUST | 当前版本一个 runtime 实例只承诺服务一个 Agent。若 Spring 中存在多个 Handler，runtime 可以按 `@Order` 选择第一个并记录告警，但不得承诺按 `agentId` 在同一实例内路由多个 Handler。 |
| Adapter 健康与生命周期 | SHOULD | Adapter 应实现健康检查与 start/stop 生命周期，以便 runtime readiness 和运维观测能反映底层框架或远端依赖状态。 |
| 协作式取消 | MUST | Adapter 必须提供 runtime 可调用的取消入口。取消至少要阻止 runtime 继续消费本次执行结果；是否能立即中断底层 LLM、HTTP 或框架执行，由 adapter 能力决定，不能被夸大为强制中断。 |
| 框架中立错误表面 | MUST | Adapter 必须把框架原生异常、HTTP 错误、SSE 错误或未知结果映射为结构化 `FAILED` 结果或 runtime 标准错误语义，使 A2A 层能形成一致 Task/error 表面。 |
| 框架中立轨迹接入 | SHOULD | Adapter 应把可观察到的 run、model、tool、progress、error 事件映射到 runtime 轨迹语义；框架不暴露的事件不得伪造为已观测事实。 |
| OpenJiuwen ReActAgent adapter | MUST | 当前版本必须支持进程内托管 OpenJiuwen `ReActAgent`，使用 runtime 传入的稳定 state key 作为框架会话标识来源，输出通过 OpenJiuwen stream adapter 映射为 runtime 结果流。 |
| OpenJiuwen Workflow adapter | MUST | 当前版本必须支持以独立 adapter 托管 OpenJiuwen `Workflow`，支持 DAG 执行、人机交互中断、按 runtime state key 续接调用和 Workflow 输出映射；框架内部 checkpoint/cache 仍由 OpenJiuwen 或智能体开发者自治。 |
| OpenJiuwen DeepAgent adapter | MUST | 当前版本必须支持以独立 adapter 托管 OpenJiuwen `DeepAgent`，并将其执行输出、失败和中断语义归一为 runtime 结果流；DeepAgent 的内部规划、工具、skill、memory 和 checkpoint 机制不进入 adapter 治理范围。 |
| AgentScope 本地 Agent | SHOULD | 当前版本应支持包装本地 `AgentScopeAgent`，把 AgentScope 原生事件流映射为 runtime 结果流。 |
| AgentScope Harness Agent | SHOULD | 当前版本应支持测试/评估场景下的 harness 模式，以受控事件流验证 AgentScope adapter 行为。 |
| AgentScope 远程 SSE client | SHOULD | 当前版本应支持通过 HTTP/SSE 调用远端 AgentScope runtime，并把远端事件映射为 runtime 结果流。 |
| Versatile REST 代理 | MUST | 当前版本必须支持把远端 REST/SSE Agent 服务代理为 runtime Agent，完成 A2A Message 到 REST request、REST/SSE response 到 `AgentExecutionResult` 的双向转换。 |
| Versatile URL 模板 | MUST | Versatile adapter 必须支持 `{conversation_id}` 和部署配置中的 URL 变量替换，使同一 runtime state/session 能稳定映射到远端 conversation。 |
| Versatile header 与 metadata 映射 | MUST | Versatile adapter 必须支持配置 header、允许列表内 metadata header 透传、structured metadata 覆盖等映射规则，并避免未授权 metadata 任意透传。 |
| Versatile 结果提取 | SHOULD | Versatile adapter 应支持按 match/get 规则从远端 SSE 或 JSON payload 中提取业务结果，并在 terminal event 到达时形成 completed 结果。 |
| Versatile 中断检测 | MUST | 当远端 HTTP/SSE 流关闭但未观察到明确 End / terminal 事件时，adapter 必须映射为 `INTERRUPTED` 或等价 input-required 语义，而不是误报 completed。 |
| Python / Node.js sidecar 原生 adapter | OUT | 当前版本不承诺直接以 sidecar SDK 方式接入 Python / Node.js Agent。跨语言 Agent 应通过 Versatile REST 代理或独立远端 Agent 方式接入。 |
| 同实例多 Agent 路由 | OUT | 当前版本不承诺一个 runtime 实例内按 agent id 路由多个 Handler。多 Agent 部署应使用多个 runtime 实例或上层路由。 |
| AgentScope Memory / Checkpoint | OUT | 当前版本不承诺 AgentScope adapter 原生接入 runtime Memory 或 Checkpoint 中间件。 |

## 3. 外部接口与入口要求

| 入口 | 类型 | 事实要求 |
|---|---|---|
| `AgentRuntimeHandler` | Java SPI | 必须作为所有 adapter 被 runtime 调用的统一执行入口，提供 `agentId`、`isHealthy`、`execute`、`resultAdapter`、`cancel` 和生命周期扩展点。 |
| `StreamAdapter` | Java SPI | 必须把框架原生 `Stream<?>` 映射为 `Stream<AgentExecutionResult>`；映射过程中不得返回 A2A SDK 私有类型作为 adapter 对外事实。 |
| `AgentExecutionContext` | Java runtime context | 必须承载 adapter 执行所需的身份、消息、metadata、input type、state key、memory scope 和 task 语义。 |
| `AgentExecutionResult` | Java result model | 必须作为 adapter 到 runtime 的标准结果表面，表达增量输出、完成、失败和中断等待输入。 |
| `AgentCardProvider` | Java optional provider | 可由 handler 或 adapter 提供 Agent Card 元数据，使执行职责与能力声明分离；其 northbound 暴露仍受 FEAT-001 约束。 |
| `agentStateKey` / `stateKey` | Runtime state boundary | 必须作为 adapter 传递给框架调用的稳定会话标识来源；它只建立 runtime task/session 与框架内部会话的关联，不授权 adapter 读写或治理框架 checkpointer/cache payload。缺省 fallback 可以使用 task 语义，但不得覆盖 tenant、session 或 task 事实字段。 |
| `MemoryProvider` | Java reserved SPI | 只作为 runtime 预留的窄 memory 接入点出现；当前版本的 Redis 任务状态缓存由 FEAT-003 约束，Memory/State 中间件历史草稿已归档。 |
| `OpenJiuwenAgentRuntimeHandler` | Java adapter base | 应作为 OpenJiuwen ReActAgent 接入入口，开发者通过实现 `createOpenJiuwenAgent(context)` 构建 Agent。 |
| `OpenJiuwenWorkflowAgentRuntimeHandler` | Java adapter base | 应作为 OpenJiuwen Workflow 接入入口，开发者通过实现 `createOpenJiuwenWorkflow(context)` 构建 Workflow DAG。 |
| `OpenJiuwenDeepAgentRuntimeHandler` | Java adapter base | 应作为 OpenJiuwen DeepAgent 接入入口，开发者通过实现框架所需构造逻辑提供 DeepAgent。 |
| `AgentScopeAgentRuntimeHandler` | Java adapter base | 应作为本地 AgentScope Agent 接入入口，消费 `AgentScopeAgent` 事件流。 |
| `AgentScopeHarnessRuntimeHandler` | Java adapter base | 应作为 AgentScope 测试/评估 harness 接入入口。 |
| `AgentScopeRuntimeClientHandler` | Java adapter base | 应作为远程 AgentScope SSE runtime 接入入口。 |
| `VersatileAgentRuntimeHandler` | Java adapter base | 必须作为远端 REST/SSE 服务代理入口，并可提供 Agent Card 信息供 A2A 发现。 |
| `versatile.*` | YAML configuration | 必须承载 Versatile URL、timeout、URL variables、query params、headers、passthrough headers、input metadata keys 和 result extractions。 |
| `GET /.well-known/agent-card.json` | HTTP endpoint | 不属于 adapter 私有入口；任何 adapter 挂载出的 Agent 都必须通过 FEAT-001 的 Agent Card 发现表面暴露能力。 |
| `POST /a2a` | HTTP endpoint | 不属于 adapter 私有入口；任何 adapter 挂载出的 Agent 都必须通过 FEAT-001 的标准 A2A JSON-RPC/SSE 表面被调用。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 挂载 OpenJiuwen ReActAgent | 应用已引入 OpenJiuwen adapter，开发者能构建 `ReActAgent` | 开发者继承 `OpenJiuwenAgentRuntimeHandler` 并注册为 Spring Bean | runtime 通过标准 A2A 入口调用该 Agent；OpenJiuwen 输出被映射为 Task/SSE/Artifact/terminal 状态。 |
| 挂载 OpenJiuwen Workflow Agent | 应用已引入 Workflow adapter，框架或开发者已配置其内部续接机制 | 开发者继承 `OpenJiuwenWorkflowAgentRuntimeHandler`，构建 Workflow DAG | Workflow 正常完成时返回 completed；遇到人工确认节点时返回 input-required；用户续接同一任务后，adapter 以同一 runtime state key 发起续接调用，内部恢复由 OpenJiuwen 或开发者配置负责。 |
| 挂载 OpenJiuwen DeepAgent | 应用已引入 DeepAgent adapter，开发者能构建 `DeepAgent` | 开发者继承 `OpenJiuwenDeepAgentRuntimeHandler` 并注册为 Spring Bean | runtime 通过标准 A2A 入口调用该 Agent；DeepAgent 输出、失败和中断被映射为标准 Task/SSE/error 语义。 |
| 挂载 AgentScope 本地 Agent | 应用已能产出 `AgentScopeEvent` 流 | 开发者注册 `AgentScopeAgentRuntimeHandler` | runtime 以统一 `AgentExecutionResult` 消费 AgentScope 事件，调用方仍观察标准 A2A Task/SSE 表面。 |
| 连接远程 AgentScope runtime | 远端 AgentScope runtime 可通过 HTTP/SSE 访问 | 开发者注册 `AgentScopeRuntimeClientHandler` 和连接配置 | adapter 发起远端调用并解码 SSE，远端错误或断流按标准失败/中断语义返回。 |
| 代理远端 REST Agent 服务 | 远端服务提供 REST endpoint 和 SSE/JSON 响应 | 开发者注册 `VersatileAgentRuntimeHandler` 并配置 `versatile.*` | 调用方仍发送标准 A2A 请求；adapter 组装 REST request，解析远端 response 并返回标准 Task/SSE 结果。 |
| 远端服务需要会话连续性 | runtime 输入带有 session/context/task 语义 | Versatile adapter 用 `{conversation_id}` 或配置字段构造远端 URL | 同一 runtime state/session 稳定映射到远端 conversation，避免跨会话串扰。 |
| Agent 等待用户输入 | OpenJiuwen Workflow、OpenJiuwen DeepAgent 或 Versatile 远端服务产生中断语义 | 调用方收到 `INPUT_REQUIRED` 后用同 task/context 续接 | adapter 必须使用同一 runtime state key、task/context 或远端 continuation 信息发起续接调用；内部恢复上下文由框架或远端服务自治，adapter 不直接读写缓存 payload。 |
| 调用方取消任务 | A2A client 调用标准 cancel | runtime 调用当前 handler 的 cancel | adapter 至少停止本次结果消费并让 Task 表面进入取消语义；底层是否立即停止由 adapter 能力决定。 |
| 多 Handler 被同时注册 | Spring 容器中存在多个 `AgentRuntimeHandler` Bean | runtime 启动并选择执行 Handler | runtime 可以按 `@Order` 选第一个并记录告警；不得对外承诺同实例多 Agent 路由。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 Adapter 归一语义

- Adapter 是框架差异的吸收层，不是新的 northbound 协议层。
- A2A controller、Task store、readiness gate、trajectory、state/memory scope 必须看到统一的 `AgentRuntimeHandler` 执行表面。
- Adapter 不得把 OpenJiuwen、AgentScope、Versatile 或其他框架的内部状态机直接暴露为 A2A Task 状态机。
- Adapter 可以保留框架内部 session、conversation、workflow node、remote request id 等字段，但这些字段必须稳定映射到 runtime context 或 metadata，不能覆盖 runtime 的 task/session/tenant 事实字段。
- Adapter 不得实现或代理框架 checkpointer/cache 的读写策略；它只能把 runtime 拥有的 state key、task/context 和调用生命周期信号传给框架。
- 框架 hook、rail、tool、skill、middleware、callback 等扩展机制不属于本特性承诺的 adapter 能力；如果框架或 Agent 开发者在内部使用这些机制，adapter 只观察最终执行结果。

#### 5.1.2 执行与结果映射语义

- `execute(context)` 必须返回可被 runtime 消费的 stream；即使底层框架是同步阻塞调用，也必须包装为统一 stream 结果。
- 原生增量输出必须映射为 `OUTPUT`；最终成功必须映射为 `COMPLETED`；异常或不可恢复错误必须映射为 `FAILED`；需要用户输入、远端继续或连接关闭未终止时必须映射为 `INTERRUPTED`。
- `COMPLETED` 不得用于表示“远端还在等待用户输入”或“连接意外关闭但没有 terminal event”的情况。
- 未知原生结果类型必须失败或被显式过滤，不得以空 completed 掩盖。

#### 5.1.3 OpenJiuwen ReActAgent 语义

- OpenJiuwen ReActAgent adapter 必须把 runtime text input 映射为 OpenJiuwen input，并以 stable state key / conversation id 维持会话连续性。
- OpenJiuwen streaming runner 或等价执行路径输出的 chunk 必须映射为 runtime 结果流。
- ReActAgent 内部 hook、rail、tool、skill、memory 和 checkpoint 能力由 OpenJiuwen 或智能体开发者自治，adapter 不声明这些机制的安装、编排或缓存读写承诺。

#### 5.1.4 OpenJiuwen Workflow 语义

- Workflow adapter 必须作为独立 adapter 入口存在，不能被描述为 ReActAgent adapter 内部隐式能力。
- Workflow DAG 正常结束时必须映射为 `COMPLETED`；Workflow 抛出或返回错误时必须映射为 `FAILED`。
- Workflow 人工确认或交互节点必须映射为 `INTERRUPTED` / input-required；同一 state key 下的后续用户输入必须由 adapter 传回 OpenJiuwen，内部 checkpoint/cache 恢复由 OpenJiuwen 或智能体开发者自治。
- Workflow 组件模型不等同于 ReActAgent 模型，二者必须保持独立 adapter 入口。

#### 5.1.5 OpenJiuwen DeepAgent 语义

- DeepAgent adapter 必须作为独立 adapter 入口存在，不能被描述为 ReActAgent 或 Workflow adapter 内部隐式能力。
- DeepAgent 的最终输出、失败和中断必须映射为 runtime 标准 `OUTPUT`、`COMPLETED`、`FAILED` 或 `INTERRUPTED` 语义。
- DeepAgent 内部规划、工具调用、skill 调度、MCP 工具服务调用、memory 和 checkpoint 机制由 OpenJiuwen 或智能体开发者自治；adapter 不对这些内部机制做显式承诺。

#### 5.1.6 AgentScope 语义

- AgentScope 本地、Harness 和远程 SSE 三种模式最终都必须产出 `AgentScopeEvent` 或等价事件流，并由 adapter 映射为 runtime 结果。
- AgentScope 原生错误码、异常链或远端错误必须映射到 runtime 标准错误分类；未知错误归为内部错误或等价失败。
- AgentScope 可产生 PROGRESS 类轨迹事件；不暴露模型调用回调时，不得伪造 MODEL_CALL 轨迹。
- 当前版本不承诺 AgentScope 原生 Memory 或 Checkpoint 适配。

#### 5.1.7 Versatile REST 代理语义

- Versatile adapter 必须把 A2A text input 和 metadata 映射为远端 REST request body、URL、query 和 header。
- `{conversation_id}` 必须由 runtime state/session 语义派生，确保远端 conversation 与 runtime 会话边界一致。
- Header 透传必须受 allowlist 或 structured metadata 规则控制；不得默认透传所有调用方 metadata。
- 远端 SSE 的 message、workflow_finished、end、exception、connection_closed 等事件必须映射为标准 `OUTPUT`、`COMPLETED`、`FAILED` 或 `INTERRUPTED`。
- result extraction 只能影响 completed payload 的组装，不能改变 terminal 状态语义。

#### 5.1.8 错误、取消与可观测性结果

| 场景 | 事实要求 |
|---|---|
| handler 未就绪或不健康 | runtime 应拒绝执行或暴露不健康状态，调用方不应看到伪成功结果。 |
| adapter 创建底层 Agent 失败 | 必须映射为 failed Task/error，并记录可诊断错误。 |
| 框架同步调用异常 | 必须映射为 `FAILED`；不得让异常绕过标准 Task/error 表面。 |
| 远端 HTTP 超时 | 必须映射为可诊断、可分类的失败错误。 |
| 远端 HTTP 4xx/5xx | 必须保留状态码或等价错误 code，并映射为 `FAILED`。 |
| SSE 解析失败 | 应记录并按可恢复性选择跳过单帧或失败整个任务；不得输出破损事件给 A2A 调用方。 |
| 流关闭但无 terminal event | 必须映射为 `INTERRUPTED` 或失败，不能映射为 completed。 |
| cancel requested | 必须停止 runtime 对该执行流的继续消费，并尽力通知底层框架或远端请求。 |
| trajectory fields | Adapter 发出的轨迹必须带有 runtime stamped context/task/agent/correlation 语义；敏感字段遵守 DFX 掩码规则。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 同实例多 Agent 路由 | 不承诺在一个 runtime 实例内按 `agentId` 路由多个 Handler；多 Agent 应多实例部署或由上层网关路由。 |
| Adapter 私有 northbound endpoint | 不承诺为 OpenJiuwen、AgentScope 或 Versatile 暴露绕过 FEAT-001 的私有执行 endpoint。 |
| 状态缓存归属边界 | 异构框架 adapter 不依赖、不读写、不配置、不治理智能体框架的 checkpointer/cache payload；adapter 只传递 runtime 拥有的 state key、task/context 和调用生命周期信号。runtime 任务状态缓存、revision、fencing、TTL 与租户隔离由 FEAT-003 约束；框架内部执行快照由框架或智能体开发者自治。 |
| 框架扩展机制自治边界 | 异构框架 adapter 不承诺、不安装、不编排、不治理框架 hook、rail、tool、skill、middleware、callback 等扩展机制。这些机制应由智能体框架提供，或由智能体开发者在构建 Agent 时自定义；adapter 只负责请求桥接、调用执行和结果归一。 |
| 强制中断底层模型调用 | `cancel` 不承诺立即中断已经进入底层 LLM 或远端服务的阻塞调用。 |
| AgentScope Workflow | 不承诺 AgentScope Workflow 适配。 |
| AgentScope Memory / Checkpoint | 不承诺 AgentScope adapter 接入 runtime MemoryProvider 或 Checkpoint。 |
| Python / Node.js 原生 sidecar | 不承诺直接通过进程内 SDK 或 sidecar 协议接入非 Java Agent；应使用 Versatile 或远程 A2A Agent。 |
| MCP 作为 Agent adapter | MCP 是工具服务协议，不是本特性的异构智能体框架 adapter。若智能体框架自身具备调用模型或调用 MCP 服务的能力，该能力由框架或智能体开发者自治，agent-runtime 异构适配不做显式承诺。 |
| REST facade 替代 Versatile | FEAT-006 的 RESTful client facade 面向业务 client；Versatile adapter 面向代理远端 Agent 服务，二者不得互相替代事实边界。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本文作为异构 Agent adapter 层的事实来源，不能把旧实现限制或新增代码能力未经声明地写成事实承诺。
- A2A 层必须只依赖 `AgentRuntimeHandler` / `AgentExecutionResult` 等框架中立表面，不得导入 OpenJiuwen、AgentScope、Versatile 私有类型。
- 新增 adapter 必须提供执行入口、结果映射、错误映射、取消语义、健康检查策略、配置说明和至少一个可运行示例；不得把框架 cache/checkpointer 读写、hook/rail/tool/skill 编排写成本特性承诺。
- OpenJiuwen ReActAgent、OpenJiuwen Workflow 与 OpenJiuwen DeepAgent 必须在文档和实现中保持入口清晰：三者分别面向 LLM 自主循环、DAG 编排/人机交互中断和 DeepAgent 执行模型。
- Versatile adapter 的 URL、header、metadata、result extraction 和中断检测规则必须被测试覆盖，尤其要覆盖“无 End 连接关闭不得 completed”的边界。
- AgentScope adapter 的错误映射、远程 SSE 解码和 PROGRESS 轨迹必须被测试覆盖；Memory/Checkpoint 不得在未实现前写入 guide 作为承诺能力。
- 多 Handler 注册只能作为兼容降级路径处理；任何同实例多 Agent 路由设计必须先更新本特性或新增 version-scope 特性。
- 若未来要支持 Python/Node sidecar、AgentScope Workflow、强制取消、多 Agent 路由，或由 runtime 统一治理框架 hook/tool/skill/cache，必须先更新当前版本事实要求，再进入 L2 和实现。

## 7. 关联文档

- `architecture/L2-Low-Level-Design/agent-runtime/FEAT-002-heterogeneous-agent-framework-compatibility.md`
