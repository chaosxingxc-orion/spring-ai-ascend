---
version: 0715
module: agent-runtime
feature_type: functional
feature_id: FEAT-004
status: deprecated
archived: 2026-07-09
---

> 废弃稿：本文为历史特性文档，已从当前 `version-scope` 移除；当前 Redis 任务状态缓存事实以 `version-scope/FEAT-003-agent-task-state-cache.md` 为准。

# 中间件解耦 Memory & State 特性文档

## 1. 特性定位

FEAT-004 定义 `agent-runtime` 当前版本中 Memory 与 State 相关中间件能力的事实要求：runtime 必须提供框架中立的窄 Memory SPI、稳定的 state key / memory scope 派生语义，以及面向具体 Agent 框架的状态恢复接入边界，使 Agent 执行过程可以在不绑定特定存储产品、不泄漏框架私有状态模型的前提下使用记忆检索、记忆写回和框架级 checkpoint。

本特性解决的问题是：不同 Agent 框架通常拥有各自的 memory、checkpoint、conversation id、cache 或 session 状态机制。如果这些机制直接暴露给 northbound A2A 服务入口或 adapter 公共契约，调用方、测试和平台集成都必须理解具体框架的私有状态模型。`agent-runtime` 需要把这些差异收敛到运行时上下文、窄 MemoryProvider SPI 和框架本地 adapter 之内，对外只承诺稳定的 Task、session、tenant、state key、memory scope 和错误降级语义。

对下游设计和实现而言，本特性是 Memory/State 中间件边界的事实来源。L2 设计、OpenJiuwen adapter、未来 memory 后端、测试、指南和示例必须以本文定义的能力、边界和行为语义为准；实现中已经存在但本文未声明的后端、工具、checkpoint 或框架能力，不能自动成为当前版本对外事实承诺。

本特性面向以下角色：

- Agent 开发者：通过 `MemoryProvider`、OpenJiuwen rail 或框架自带 checkpoint 能力为 Agent 添加记忆和状态恢复。
- Adapter 开发者：把 runtime 上下文中的 tenant、user、session、task、state key 和 memory scope 映射到具体框架的 memory/checkpoint 入口。
- 平台集成方：替换或接入 memory 后端、配置 OpenJiuwen checkpointer，并理解这些能力的运行时隔离边界。
- 测试与验收团队：按统一黑盒行为验证 memory 检索/保存、state key 派生、checkpoint 配置、降级和边界排除项。

本特性只定义 `agent-runtime` 中 Memory 与 State 的中间件接入语义。标准 northbound A2A 服务入口由 `FEAT-001` 约束；异构框架 adapter 的执行归一、结果流和取消语义由 `FEAT-002` 约束；远程 Agent 发现、工具安装和中断续接编排由 `FEAT-005` 约束。框架内部 memory、tool、skill、hook、checkpoint payload 和 cache 策略仍由具体框架或 Agent 开发者自治。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 框架中立 MemoryProvider SPI | MUST | runtime 必须提供 `MemoryProvider` 作为窄 memory 接入点，覆盖 `init(context)`、`search(context, query, limit)` 和 `save(context, records)`；该 SPI 不得依赖 OpenJiuwen、AgentScope、A2A SDK 或具体存储产品类型。 |
| MemoryHit 相关性契约 | MUST | `MemoryProvider.search` 返回的 `MemoryHit` 必须以相关性顺序排列；`score` 是可选字段，调用方不得要求所有后端都提供可比较分值。 |
| MemoryRecord 标准化写回 | MUST | `MemoryProvider.save` 接受 message-like `MemoryRecord`，至少包含 role、content 和 metadata；转换层必须保留可诊断 metadata，但不得把框架私有对象直接写入 SPI 表面。 |
| execution context 作用域 | MUST | Memory 与 State 调用必须消费 `AgentExecutionContext` 中的 tenant、user、session、task、agent、metadata、agentStateKey 和 memoryScope 等事实字段，而不是重新定义一套与 runtime 冲突的身份字段。 |
| state key 稳定派生 | MUST | runtime 必须提供稳定的 `agentStateKey` / `stateKey` 作为 adapter 传递给框架 conversation/checkpoint 的标识来源；缺省派生不得覆盖 tenant、session 或 task 事实字段。 |
| memory scope 稳定派生 | MUST | runtime 必须提供 memory scope 作为记忆检索和写回的隔离依据；memory provider 必须按 context 中的 tenant/user/session/state 语义隔离数据访问。 |
| OpenJiuwen ReActAgent 记忆注入 | MUST | 当前版本必须支持 OpenJiuwen ReActAgent 在每轮调用前通过 runtime memory 检索相关记忆并注入 system prompt 或等价 prompt builder section，调用后把可保存的对话消息写回 memory provider。 |
| OpenJiuwen external memory 适配 | MUST | 当前版本必须提供 OpenJiuwen-local adapter，把 runtime `MemoryProvider` 适配到 OpenJiuwen 原生 external memory provider 语义；该适配不把 OpenJiuwen memory 类型提升为 runtime 公共 SPI。 |
| Memory message 转换 | MUST | 当前版本必须提供 OpenJiuwen message 与 runtime `MemoryRecord` 的角色和内容转换，覆盖 user、assistant、system、tool 等常见消息角色；未知角色不得导致运行时崩溃。 |
| Memory 失败降级 | MUST | memory init/search/save 失败不得绕过标准 Task/error 表面，也不得导致框架私有异常泄漏；可降级场景必须记录可诊断日志，并按本文行为语义继续或失败。 |
| OpenJiuwen checkpointer 配置 | MUST | 当前版本必须支持通过 `OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer)` 配置 OpenJiuwen 原生 checkpointer，并提供 `setInMemoryDefault()` 作为开发/测试默认路径。 |
| Checkpoint 框架本地边界 | MUST | runtime 不定义跨框架通用 Checkpoint SPI；OpenJiuwen checkpoint payload、加载、保存和恢复由 OpenJiuwen 原生机制负责，runtime 只提供稳定 conversation/state key 和配置入口。 |
| In-memory checkpoint | MUST | 当前版本必须支持 OpenJiuwen in-memory checkpointer 作为非持久化开发/测试路径；不得把它描述为生产持久化能力。 |
| 自定义 checkpointer | SHOULD | 应允许应用注入 OpenJiuwen 原生 `Checkpointer` 实现，例如 SQLite 或其他自定义后端；其持久化、多实例共享、清理和错误语义由该实现负责。 |
| 中途按需检索记忆 | OUT | 当前版本不承诺 Agent 在推理过程中主动按需调用 runtime memory 检索。 |
| 记忆工具 | OUT | 当前版本不承诺把 MemoryProvider 自动暴露为 Agent tool；如果 Agent 需要工具式记忆读写，应由 Agent 开发者或框架自行封装。 |
| AgentScope memory/checkpoint | OUT | 当前版本不承诺 AgentScope adapter 接入 runtime MemoryProvider 或 checkpoint。 |
| Redis/向量库预置后端 | OUT | 当前版本不承诺提供 Redis、向量数据库或其他生产 memory/checkpoint 后端；后端属于外部实现或具体框架配置。 |

## 3. 外部接口与入口要求

| 接口 | 类型 | 事实要求 |
|---|---|---|
| `MemoryProvider` | Java SPI | 必须作为 runtime 预留的 memory init/search/save 接入点；实现方可替换后端，但不得要求调用方了解后端产品类型。 |
| `MemoryProvider.init(context)` | Java SPI method | 可为一次 Agent execution 初始化 memory 资源；失败时 adapter 必须按降级语义处理并记录诊断信息。 |
| `MemoryProvider.search(context, query, limit)` | Java SPI method | 必须按 context scope 检索相关记忆并返回按相关性排序的 `MemoryHit` 列表；空结果是合法结果。 |
| `MemoryProvider.save(context, records)` | Java SPI method | 必须保存标准化 `MemoryRecord`；默认实现可以为空，调用方不得假定所有 memory provider 都会持久化写回。 |
| `MemoryProvider.MemoryHit` | Java record | 必须表达 `id`、`content`、可选 `score` 和 `metadata`；`content` 缺省为空字符串，`metadata` 缺省为空 Map。 |
| `MemoryProvider.MemoryRecord` | Java record | 必须表达 `id`、`role`、`content` 和 `metadata`；缺省 role 可归一为 `unknown`，content 和 metadata 必须有安全默认值。 |
| `AgentExecutionContext.agentStateKey` | Runtime context field | 必须作为 adapter 传给框架 conversation/checkpoint 的稳定状态标识来源；框架内部 payload 不得反向覆盖该字段。 |
| `AgentExecutionContext.memoryScope` | Runtime context field | 必须作为 memory 检索和保存的默认隔离边界来源；provider 可进一步使用 tenant/user/session 等字段细分 scope。 |
| `OpenJiuwenAgentRuntimeHandler.memoryRuntimeRail(...)` | Java helper | 必须允许 OpenJiuwen ReActAgent adapter 在调用前检索并注入 runtime memory、调用后保存对话消息。 |
| `OpenJiuwenExternalMemoryProviderAdapter` | Java adapter | 必须只作为 OpenJiuwen-local 适配层存在，把 OpenJiuwen external memory 调用委托给 runtime `MemoryProvider`。 |
| `OpenJiuwenMemoryMessageAdapter` | Java adapter | 必须完成 OpenJiuwen message 与 runtime memory record 之间的安全转换；转换失败不得污染 runtime 公共 SPI。 |
| `OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer)` | Java helper | 必须把应用提供的 OpenJiuwen `Checkpointer` 注册为框架默认 checkpointer；该方法应在服务流量进入前完成调用。 |
| `OpenJiuwenCheckpointerConfigurer.setInMemoryDefault()` | Java helper | 必须创建并注册 OpenJiuwen in-memory checkpointer，主要用于开发、测试或无持久化要求场景。 |
| A2A `tenantId` / `X-Tenant-Id` 派生上下文 | Inbound context source | 必须能进入 memory/state 作用域派生链路；runtime 不负责认证租户身份，认证和清洗由前置网关负责。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 为 OpenJiuwen ReActAgent 添加长期记忆 | 应用注册了 `MemoryProvider`，handler 使用 OpenJiuwen ReActAgent adapter | 开发者在 handler 中启用 memory runtime rail | 每次 Agent 调用前按 context 检索相关记忆并注入 prompt，调用后把标准化消息写回 provider；A2A 调用方仍只看到标准 Task/SSE/result 表面。 |
| 无 memory provider 的普通 Agent 调用 | 应用未注册 memory provider 或 handler 未启用 memory rail | client 通过标准 A2A 入口调用 Agent | Agent 正常执行；runtime 不应强制要求 memory 后端存在，也不应在 Agent Card 中夸大 memory 能力。 |
| memory 检索失败后降级 | `MemoryProvider.search` 抛出异常或后端不可用 | Agent 调用进入 memory rail | runtime 记录 tenant/session/task 相关诊断日志，跳过记忆注入并继续执行；除非 adapter 明确把该失败声明为不可恢复，否则不应让调用方看到框架私有异常。 |
| memory 保存失败后返回结果 | Agent 已正常完成，但 `MemoryProvider.save` 失败 | memory rail 尝试保存本轮对话消息 | Agent 结果仍按标准 Task/SSE/result 返回；保存失败被记录为可诊断事件，不得把成功执行篡改为 failed Task。 |
| 配置 OpenJiuwen in-memory checkpoint | 应用需要开发/测试状态恢复路径 | 应用启动时调用 `OpenJiuwenCheckpointerConfigurer.setInMemoryDefault()` | OpenJiuwen 使用 in-memory checkpointer；进程重启后状态不保留，文档和指南不得把该路径描述为生产持久化。 |
| 配置 OpenJiuwen 自定义 checkpoint | 应用提供 OpenJiuwen 原生 `Checkpointer` Bean | 应用启动时调用 `OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer)` | 后续 OpenJiuwen Agent 调用使用该 checkpointer 进行框架本地状态保存/恢复；runtime 仍只治理 state key 和调用边界。 |
| 同一 session 连续调用 | inbound context 携带相同 tenant/user/session 或同一 task continuation 语义 | client 多次调用同一 Agent | runtime 派生稳定 `agentStateKey` 和 memory scope，使 memory 检索、写回和框架 conversation/checkpoint 能保持同一会话边界。 |
| 多租户隔离调用 | 前置网关注入可信 tenant，provider 支持租户隔离 | 不同 tenant 使用相同 user/session 文本调用 Agent | memory provider 必须按 context 中的 tenant/scope 隔离检索和保存；不同 tenant 不得因为相同 query 或 session 文本互相泄漏记忆。 |
| AgentScope adapter 调用 | 应用使用 AgentScope adapter | client 调用 AgentScope Agent | 当前版本不承诺 AgentScope memory/checkpoint 接入；AgentScope adapter 不得伪装为已接入 runtime MemoryProvider。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 MemoryProvider 窄 SPI 语义

- `MemoryProvider` 是 memory 中间件接入点，不是完整 memory 产品契约。
- SPI 只表达每次执行的初始化、检索和写回；memory 抽取、压缩、embedding、索引、排序、TTL、冷热分层和治理策略由 provider 实现负责。
- `search` 的结果顺序即相关性契约；`score` 可用于诊断或展示，但调用方不得以 score 存在与否判断 provider 是否可用。
- `save` 的默认实现可以为空；handler 或 adapter 可以调用写回，但不得把“写回必然持久化成功”作为对外事实。
- provider 必须把 `AgentExecutionContext` 视为作用域事实来源，不得通过 message metadata 任意覆盖 tenant/user/session/task/state key。

#### 5.1.2 Memory 注入与写回语义

- OpenJiuwen ReActAgent memory rail 必须在模型调用前检索记忆，并把记忆作为系统上下文或等价 prompt section 注入，而不是伪装成新的用户输入。
- 注入的记忆必须带有可辨识的 runtime memory 语义，避免 Agent 或审计链路把历史记忆误判为当前用户消息。
- 调用后写回必须以标准化 message-like record 为单位；工具消息、系统消息和未知角色必须安全处理。
- 当前版本只承诺轮次开始前一次性检索，不承诺在 Agent 推理中途按需检索或主动更新 memory。

#### 5.1.3 State key 与 checkpoint 语义

- `agentStateKey` 是 runtime 向 adapter 和框架传递的稳定状态边界，不是框架内部 checkpoint payload。
- OpenJiuwen conversation/checkpoint 应使用 runtime state key 派生的稳定标识，以保持同一 runtime session/task continuation 的状态连续性。
- runtime 不读取、不解释、不迁移 OpenJiuwen checkpointer payload；payload 结构、持久化和恢复语义属于 OpenJiuwen 或应用注入的 checkpointer。
- 不同框架的 checkpoint 机制差异较大，当前版本不定义跨框架通用 Checkpoint SPI。

#### 5.1.4 作用域、租户与数据隔离语义

- tenant、user、session、task、agent、correlation、trace 等字段必须能进入 memory/state 派生链路。
- runtime 可以从 inbound A2A metadata、header 或默认配置派生这些字段，但不得承担租户认证职责。
- memory provider 必须按 context scope 隔离数据访问；测试中应覆盖不同 tenant/session 的记忆不串扰。
- message-level metadata 可作为业务或 provider 补充信息保存，但不得覆盖 runtime 身份字段。

#### 5.1.5 OpenJiuwen local adapter 语义

- `OpenJiuwenExternalMemoryProviderAdapter` 是 OpenJiuwen-local 适配层，不是 runtime 公共 memory 协议。
- OpenJiuwen 原生 memory tool、skill、rail、hook、checkpoint 和 callback 机制由 OpenJiuwen 或 Agent 开发者自治；runtime adapter 只承诺把必要调用桥接到 `MemoryProvider`。
- `OpenJiuwenCheckpointerConfigurer` 只能作为启动期配置入口使用；不应在每次请求中切换全局 checkpointer。

#### 5.1.6 错误、降级与可观测结果

| 场景 | 事实要求 |
|---|---|
| memory init failure | 应记录 WARN 或等价诊断事件；若不影响 Agent 执行，可继续无记忆执行。 |
| memory search failure | 应跳过记忆注入并继续执行，除非应用把 memory 声明为强依赖；不得泄漏后端私有异常给 A2A 调用方。 |
| memory search empty | 是合法结果，Agent 按无历史记忆执行。 |
| memory save failure | 不得把已成功的 Agent 执行篡改为 failed Task；必须记录可诊断日志或 trajectory 事件。 |
| memory record conversion failure | 应跳过不可转换记录或映射为安全默认值，不得输出破损 record 给 provider。 |
| checkpointer not configured | OpenJiuwen 使用其默认路径或应用配置路径；runtime 不应假定存在生产持久化。 |
| checkpointer load/save failure | 由 OpenJiuwen/checkpointer 实现处理；runtime 不得伪造已恢复状态，必要时应形成标准 failed/interrupted 语义或按框架降级继续。 |
| tenant/scope missing | 必须使用可解释默认值或拒绝执行；不得把不同未知租户无边界地混入同一生产 memory scope。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| Memory 产品能力 | 不承诺 embedding、向量数据库、知识抽取、摘要压缩、相似度算法、冷热分层、TTL、删除合规或管理控制台。 |
| 生产 memory 后端 | 不内置 Redis、向量库、关系数据库或对象存储 memory 后端；这些属于 `MemoryProvider` 实现层。 |
| 记忆工具化 | 不自动把 `MemoryProvider` 暴露为 Agent tool，也不承诺 Agent 可在推理中主动读写记忆。 |
| 中途记忆检索 | 不承诺在一次 Agent 执行流中按需多次检索 memory；当前主路径是轮次开始前检索注入。 |
| 跨框架 Checkpoint SPI | 不定义统一 CheckpointProvider；各框架状态恢复由对应框架或 adapter 本地处理。 |
| OpenJiuwen payload 治理 | 不读写、不解释、不迁移 OpenJiuwen checkpointer payload；runtime 只提供配置入口和 state key。 |
| AgentScope memory/checkpoint | 不承诺 AgentScope adapter 接入 runtime MemoryProvider、OpenJiuwen memory adapter 或 checkpoint。 |
| 多实例状态一致性 | 不承诺 in-memory checkpointer 支持多实例共享；自定义 checkpointer 的一致性和锁语义由实现方负责。 |
| memory 能力对外发现 | Agent Card 不应因为内部接入 memory provider 就默认声明新的 A2A capability；memory 是执行增强，不是 northbound 协议能力。 |
| 认证授权 | runtime 不认证 tenant/user/session 字段；前置网关或平台负责认证、清洗和注入可信上下文。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本文作为 Memory/State 中间件事实来源，不得把具体后端实现、框架私有 payload 或旧版示例写成当前版本公共承诺。
- `MemoryProvider` 必须保持框架中立和窄接口；新增字段或方法必须先证明无法通过 context、metadata 或 provider 实现层表达。
- Adapter 必须只通过 `AgentExecutionContext` 获取 memory/state 作用域，不得自行拼接一套与 runtime tenant/session/task 冲突的 identity。
- OpenJiuwen memory 注入必须标识为 runtime recalled memory，不得伪装成当前用户输入；相关测试应覆盖 prompt 注入、空结果、异常降级和保存失败。
- Checkpointer 配置必须是启动期或应用 wiring 行为；不得在请求级动态切换全局 OpenJiuwen `CheckpointerFactory`。
- 文档和 guide 中出现 in-memory checkpointer 时必须标明非生产持久化语义；SQLite 或其他自定义 checkpointer 只能按其自身实现能力声明持久化和多实例特性。
- AgentScope、Redis、向量库、记忆工具、中途检索、通用 checkpoint SPI 等能力若未来进入范围，必须先更新本文或新增 version-scope 特性，再进入 L2 和实现。
- 测试必须覆盖 memory scope/tenant 隔离、`MemoryHit.score` 可选、`MemoryRecord` 默认值、OpenJiuwen memory rail 注入/写回、memory 异常降级、checkpointer 配置入口和当前 OUT 项不被误声明。

## 7. 关联文档

- `docs/archive/architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-004-middleware-memory-and-state.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-002-heterogeneous-agent-framework-compatibility.md`
