---
version: 0719
module: agent-core
feature_type: functional
feature_id: FEAT-010
status: active
updated: 2026-07-19
authority:
  - README.md
  - FEAT-006-standard-agent-client-invocation.md
  - FEAT-007-local-tool-registration-and-execution.md
  - FEAT-009-client-side-tool-response.md
---

# 任务级动态工具可见性与调用移交 - 当前版本事实要求

## 1. 特性定位

FEAT-010 定义 `agent-core` 当前版本对客户端侧本地工具的任务级动态可见性与调用移交事实：当 runtime / host 在一次 Agent 执行中通过结构化任务调用上下文提供当前有效的客户端侧 `ToolView` 时，core 必须使 Agent 能在本次执行范围内感知这些客户端侧工具；当 Agent 选择其中的客户端侧工具时，core 只产出客户端工具调用意图，并将后续执行、响应投影、任务挂起和恢复交由 runtime / client 链路处理。

本特性解决的问题是：客户端侧本地工具是随用户、客户端、页面、插件、会话状态或任务调用而变化的能力，不能被视为 Agent 的长期服务侧工具，也不能注册成平台全局工具。core 需要在不拥有客户端、不拥有 runtime Task 生命周期的前提下，把当前任务可用的客户端侧工具作为 Agent 本次执行的可见工具面之一，并在被选中时形成可被 runtime 承接的调用移交契约。

本特性处于 `agent-core` 与 `agent-runtime` 的协作边界：

- FEAT-006 定义标准客户端 invocation、continuation invocation 以及客户端侧调用链路的入口语义。
- FEAT-007 定义客户端本地工具的注册、暴露策略、当前有效 `ToolView`、本地执行、授权审批和结构化 outcome。
- FEAT-009 定义 runtime 如何承接 core 产生的客户端工具调用意图，将当前 Task 挂起，通过响应投影给 client，并在 client 提交工具 outcome 后恢复执行链路。
- FEAT-010 只定义 core 如何接收任务级 `ToolView`、形成 Agent 可见工具面、产出客户端工具调用意图，以及在恢复后消费工具结果观察。

本特性不定义客户端工具真实执行，不定义 runtime Task 状态机，不定义 A2A / SSE / Gateway / Event Bus 投影协议，不新增用户可见 API，也不定义平台全局 Tool Registry、MCP、Skill Hub 或 agent-middleware 工具。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 结构化任务调用上下文接收 | MUST | core 必须能从 runtime / host 提供的当前 Agent 执行上下文中获得客户端侧 `ToolView` 或等价结构化工具视图。该上下文是程序态结构化数据或对象传递，不应被描述为自然语言上下文或 prompt 拼接。 |
| 客户端侧工具可见面装配 | MUST | core 必须基于当前有效 `ToolView`，使 Agent 在本次执行中可以感知客户端侧本地工具。`ToolView` 只表示客户端侧本地工具，不覆盖 Agent 原有服务侧工具。 |
| 服务侧工具保留 | MUST | Agent 内置服务侧工具、框架工具或服务端中间件工具继续按 core 既有工具机制处理，不因本特性被替换或降级。 |
| 任务级有效性 | MUST | 客户端侧 `ToolView` 只对当前任务 / 当前调用有效；core 不得将其缓存为 Agent 长期能力，不得沉淀为平台全局工具，也不得影响其他任务。 |
| 统一工具格式 | MUST | client、runtime、core 对客户端侧工具描述必须遵循统一工具定义。FEAT-010 不引入跨格式翻译失败语义，也不要求 core 作为工具协议转换器。 |
| 调用移交意图 | MUST | 当 Agent 选择客户端侧工具时，core 必须产出客户端工具调用意图，使 runtime / host 能识别目标工具、调用参数、客户端侧工具属性以及与当前执行 / 任务的关联。 |
| 不直接执行客户端工具 | MUST | core 不得访问客户端页面、插件、文件、本地端口、凭证或业务 UI，不得调用 agent-client 内部 API，也不得远程驱动客户端审批流程。 |
| 工具结果观察消费 | MUST | runtime 恢复执行链路后，core 必须能把客户端工具 outcome 作为工具结果观察继续交给 Agent loop / workflow 处理。 |
| 不新增用户可见 API | MUST | 当前版本不因本特性新增外部 HTTP endpoint、A2A method、客户端 API 或用户可见控制面。 |
| 全局工具注册 | OUT | 当前版本不把客户端侧动态工具注册为平台全局工具、Skill Hub、MCP、agent-middleware 工具或服务端工具目录。 |

## 3. 外部接口与入口要求

FEAT-010 不定义具体 Java SPI、DTO 类名、HTTP endpoint、A2A 字段名或内部编排对象。下游 L2 设计可以细化实现形态，但不得改变以下黑盒接口事实。

| 接口面 | 来源 / 去向 | FEAT-010 使用语义 |
|---|---|---|
| 结构化任务调用上下文 | runtime / host -> core | runtime / host 在启动或恢复 Agent 执行时，向 core 提供当前执行所需的结构化上下文；其中可以包含当前有效的客户端侧 `ToolView`。core 不应从 A2A wire 请求中自行解析客户端工具视图。 |
| 客户端侧 ToolView | FEAT-007 / FEAT-009 -> core | `ToolView` 表示 client 当前显式暴露给本次调用的本地工具视图，包含统一工具定义所需的标识、用途、输入约束和必要元数据。 |
| Agent 可见工具面 | core -> Agent loop / workflow | core 将当前 `ToolView` 中的客户端侧工具纳入本次执行的 Agent 可见工具面，同时保留 Agent 既有服务侧工具。 |
| 客户端工具调用意图 | core -> runtime / host | Agent 选择客户端侧工具时，core 产出调用意图；该意图至少应表达目标工具标识、调用参数、客户端侧工具属性，以及与当前 Agent 执行 / 任务的关联。 |
| 工具结果观察 | runtime / host -> core | runtime 在 FEAT-009 恢复链路中把 client 返回的工具 outcome 作为工具结果观察交回 core；core 据此继续 Agent loop / workflow。 |

客户端工具调用意图是 core 对 runtime / host 的黑盒输出契约，不等同于客户端执行结果，也不要求 core 了解 runtime 后续采用阻塞响应、流式响应、Task 查询或其他投影方式。投影、挂起、恢复和 Task 可观察状态由 FEAT-009 承接。

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户 / 系统动作 | 期望行为 |
|---|---|---|---|
| 带客户端 ToolView 启动 Agent 执行 | client 已按 FEAT-007 暴露本次调用可用的本地工具，runtime 已按 FEAT-009 将 ToolView 绑定到当前 Task 执行上下文 | runtime / host 启动 Agent 执行并向 core 传入结构化任务调用上下文 | core 只在本次执行中使 Agent 感知该 ToolView 内的客户端侧工具；服务侧工具保持原有语义。 |
| Agent 选择客户端侧工具 | Agent 推理或 workflow 节点决定使用当前 ToolView 中的客户端本地工具 | core 产出客户端工具调用意图并交给 runtime / host | core 不执行客户端工具；runtime 按 FEAT-009 挂起 Task 并把请求通过响应投影给 client。 |
| 客户端工具结果恢复执行 | client 已执行、拒绝或返回本地工具异常 outcome，runtime 已校验恢复关系 | runtime / host 将工具 outcome 作为观察交回 core | core 将该 observation 纳入后续 Agent loop / workflow，由 Agent 继续、完成、失败、改用其他工具或再次请求工具。 |
| 下一次调用能力变化 | client 在新的 invocation 或 continuation 中提供了新的当前有效 ToolView | runtime / host 以新的结构化任务调用上下文启动或恢复执行 | core 按新的 ToolView 形成本次执行的客户端侧工具可见面；旧 ToolView 不因 core 缓存而自动保留。 |
| 无客户端侧 ToolView | 本次执行上下文没有客户端侧 ToolView，或 ToolView 为空 | core 启动 Agent 执行 | Agent 不获得客户端侧本地工具可见性；服务侧工具不受影响。 |

## 5. 行为语义与边界

### 5.1 任务级可见性语义

- 客户端侧 `ToolView` 只对当前任务 / 当前调用有效。
- core 不把 `ToolView` 缓存为 Agent 长期能力，不跨任务、用户、租户或会话共享。
- 服务端不维护客户端长期工具视图；每次真实 invocation 的客户端能力事实以随调用进入链路的当前 `ToolView` 为准。
- core 可以在当前任务范围内将 `ToolView` 适配为 Agent 可感知的工具面，但不得把客户端侧工具提升为服务侧全局工具。

### 5.2 工具分层语义

- `ToolView` 的范围只是客户端侧本地工具。
- Agent 原有内置服务侧工具、workflow 节点工具、框架工具或服务端中间件工具不属于 `ToolView`，继续由 core 既有工具机制处理。
- 只有 Agent 选择客户端侧本地工具时，才触发 FEAT-010 的调用移交流程。
- 服务侧工具调用失败、服务端工具授权、服务端工具执行和服务端工具治理不属于本特性。

### 5.3 统一工具定义语义

- client、runtime、core 之间的客户端侧工具描述应遵循统一工具格式，一体化定义工具标识、用途、输入约束和必要元数据。
- FEAT-010 不定义工具格式转换、不定义跨协议降级，也不定义“工具描述无法表达”的兼容分支；此类问题应在 client / runtime / core 的统一工具设计中避免。
- core 的职责是使用该统一工具定义形成本次 Agent 可见面，并在工具被选择时产出调用意图。

### 5.4 调用移交语义

- core 在 Agent 选择客户端侧工具时只产出客户端工具调用意图。
- 客户端工具调用意图至少应让 runtime / host 能识别目标工具、调用参数、客户端侧工具属性，以及与当前 Agent 执行 / 任务的关联。
- core 不决定 runtime 如何挂起 Task、如何响应 client、如何处理 SSE、如何查询或取消 Task。
- core 不直接调用 client，不访问客户端本地资源，也不持有客户端执行权限。

### 5.5 结果观察语义

- runtime / host 恢复执行时，客户端工具 outcome 作为工具结果观察进入 core。
- `OK`、用户拒绝、权限不足、工具不可用、参数非法、执行失败或超时等 outcome 的业务含义由 Agent 后续推理或 workflow 逻辑处理。
- core 不验证客户端是否真实执行了工具，不验证客户端授权审批是否充分，也不越权补做客户端治理。
- core 消费 observation 后，可以让 Agent 继续、完成、失败、选择其他工具或再次触发客户端侧工具调用。

### 5.6 非承诺边界

| 边界 | 当前版本不承诺 |
|---|---|
| 用户可见 API | 不新增 HTTP endpoint、A2A method、客户端 API 或用户可见控制面。 |
| 客户端真实执行 | core 不执行页面读取、插件动作、本地文件访问、人工确认或本地业务动作。 |
| runtime Task 语义 | Task owner、挂起、响应投影、continuation 恢复、查询和取消由 FEAT-009 / FEAT-001 / FEAT-006 承接。 |
| 客户端注册与治理 | 本地工具注册、暴露策略、授权审批、真实执行和 outcome 结构由 FEAT-007 承接。 |
| 全局工具体系 | 不创建平台全局 Tool Registry、Skill Hub、MCP 或 agent-middleware 工具。 |
| 协议投影 | 不定义 Gateway、IngressGateway、Event Bus、agent-bus、SSE 或 A2A wire 投影细节。 |
| 模型工具一般语义 | 模型选择工具、工具参数生成、工具调用异常和 observation 消费遵循 Agent / 模型工具使用的一般语义，本特性不另设兜底状态机。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把 FEAT-010 作为 `agent-core` 任务级客户端侧工具可见性与调用移交的事实来源，不得把 core 描述为客户端工具执行器、runtime Task owner 或客户端工具注册中心。
- 下游设计必须使用结构化任务调用上下文承载 `ToolView`，不得把客户端侧工具下发描述为自然语言 prompt 注入或要求 core 解析 A2A wire 请求。
- FEAT-010 与 FEAT-009 的边界必须保持清晰：core 负责动态工具可见性和调用意图，runtime 负责 Task 挂起、响应投影、continuation 校验和恢复。
- FEAT-010 与 FEAT-007 的边界必须保持清晰：client 负责本地工具注册、暴露、授权、执行和 outcome，core 只消费当前有效 `ToolView` 和后续 observation。
- 下游实现不得把客户端侧 `ToolView` 提升为平台全局工具目录，也不得把它与 Agent 服务侧工具、MCP、Skill Hub 或 agent-middleware 工具混为一体。
- 测试必须覆盖带 ToolView 的 Agent 执行、无 ToolView 的执行、服务侧工具不受影响、客户端侧工具选择后的调用意图、runtime 恢复后的 observation 消费，以及新 invocation 中 ToolView 变化后旧视图不自动保留。
- 任何对新增外部 API、客户端直连执行、runtime Task 状态、Gateway/Event Bus 投影、全局工具注册或客户端治理流程的新增承诺，都必须先更新对应 version-scope 文档或新增独立特性。

## 7. 关联文档

- `version-scope/FEAT-006-standard-agent-client-invocation.md`
- `version-scope/FEAT-007-local-tool-registration-and-execution.md`
- `version-scope/FEAT-009-client-side-tool-response.md`
- `architecture/L1-High-Level-Design/agent-core/logical.md`
- `architecture/L1-High-Level-Design/agent-core/spi-appendix.md`
