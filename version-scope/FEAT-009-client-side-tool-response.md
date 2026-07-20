---
version: 0719
module: agent-runtime
feature_type: functional
feature_id: FEAT-009
status: active
updated: 2026-07-19
authority:
  - README.md
  - FEAT-001-standardized-agent-service-entrypoint.md
  - FEAT-006-standard-agent-client-invocation.md
  - FEAT-007-local-tool-registration-and-execution.md
  - FEAT-008-user-interaction-interrupt-response.md
  - FEAT-010-task-level-dynamic-tool-visibility-and-handoff.md
---

# 运行时通过响应调用客户端本地工具 - 当前版本事实要求

## 1. 特性定位

FEAT-009 定义 `agent-runtime` 当前版本在服务端 Agent 执行过程中处理客户端本地工具调用的外部行为事实：当客户端发起的标准 invocation 携带了当前可见的本地工具视图，且 Agent 执行过程中产生客户端本地工具调用时，runtime 必须挂起当前服务端 Task，并通过本次智能体服务调用响应把客户端工具请求投影返回给 client；client 完成本地工具执行后，再按标准 continuation invocation 提交工具结果，runtime 校验恢复关系后继续原 Task。

本特性解决的问题是：客户端本地工具只能在客户端本地执行，runtime 不能直接访问客户端页面、插件、文件、本地端口或业务 UI；同时 Agent 执行不能在等待客户端工具时被伪装成 completed。runtime 必须把“需要客户端执行本地工具”表达为标准 Task 生命周期中的可恢复等待状态，并在客户端后续提交工具结果后恢复同一个服务端 Task。

本特性处于以下特性之间的 runtime 边界：

- FEAT-006 定义业务应用通过 `agent-client` 创建 invocation、观察状态、取消和以新的 continuation invocation 继续等待输入；业务应用不直接操作服务端 `taskId`。
- FEAT-007 定义客户端本地工具注册、ToolExposurePolicy、ToolView、本地执行、授权审批、结构化 outcome 和结果提交 facade。
- FEAT-008 定义通用用户交互式中断响应。纯粹的用户补充输入、决策或材料等待归 FEAT-008；客户端 Action 工具执行前的本地授权、确认、审批归 FEAT-007，其最终 outcome 可作为 FEAT-009 的客户端工具结果进入 runtime。
- FEAT-010 定义 agent-core 如何基于任务级 ToolView 形成 Agent 可见工具集合，以及 Agent 选择客户端工具后的调用移交语义。
- FEAT-001 定义 A2A Task、Message、SSE、查询、取消、错误和状态表面。

FEAT-009 自身只定义 runtime 对客户端本地工具请求的 Task 级挂起、响应投影、结果接收和 Task 恢复语义。它不定义客户端工具注册和真实执行，不定义 core 的动态工具可见性算法，不新增 runtime 专用客户端工具 endpoint，也不定义 Gateway、Event Bus 或 agent-bus 的投影协议。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| ToolView 承接与 Task 级绑定 | MUST | runtime 必须能从标准 client invocation 中接收当前 ToolView 或等价客户端能力视图，并把它绑定到当前服务端 Task 的执行上下文，使下游 Agent 执行链路能够在任务范围内感知这些客户端能力。具体工具可见性计算、提示注入、模型工具 schema 组装和调用移交由 FEAT-010 定义。 |
| 客户端工具调用挂起 | MUST | Agent 执行过程中产生客户端本地工具调用时，runtime 必须挂起当前服务端 Task，使其进入等待客户端工具结果的可恢复中断状态。 |
| 本次调用响应投影 | MUST | runtime 必须通过收到的智能体服务调用响应，把客户端工具请求投影返回给 client。流式与非流式调用都不得继续占用请求等待客户端工具实际执行完成。 |
| 非完成状态 | MUST | 等待客户端本地工具结果时，Task 不得被标记为 completed，也不得把工具请求投影伪装成 Agent 最终答案。 |
| continuation 结果接收 | MUST | client 完成本地工具执行、拒绝、超时或失败后，必须按 FEAT-006 的 continuation invocation 语义提交结果；runtime 必须把合法 continuation 映射回原挂起 Task。 |
| Task 恢复 | MUST | runtime 校验 continuation 与当前挂起 Task 的关联后，必须把客户端工具 outcome 作为恢复输入交回执行链路，由 Agent/core 决定继续、完成、失败、再次请求客户端工具或进入其他等待状态。 |
| 客户端异常 outcome 透传 | MUST | 工具未声明、未暴露、权限不足、参数非法、用户拒绝、工具不可用、执行失败或超时等客户端 outcome 应作为工具结果输入恢复执行链路；runtime 不应仅因 outcome 表示工具失败就直接把 Task 置为 FAILED。 |
| runtime 恢复校验 | MUST | runtime 必须校验 continuation 的租户、调用上下文、Task 状态、恢复点和幂等语义；跨租户、错 invocation、Task 已终态、等待点不存在、重复或不可恢复等 runtime 层非法请求必须被拒绝或映射为标准错误。 |
| 等待期间查询观察 | MUST | 如果 Task 在等待客户端本地工具结果期间被查询，查询结果必须显示为中断挂起或等待输入类状态，不得显示为 completed。 |
| 等待期间取消 | MUST | 等待客户端本地工具结果期间，Task 必须允许按 FEAT-001 / FEAT-006 取消；取消后迟到的客户端工具结果不得恢复该 Task。 |
| 客户端资源边界 | MUST | runtime 不直接访问客户端本地工具、DOM、插件、文件、本地端口、凭证或业务 UI，也不远程驱动客户端内部审批流程。 |
| Gateway / Event Bus 主权边界 | MUST | Gateway、IngressGateway、Event Bus 或 agent-bus 可以承载请求、响应或投影交付，但不拥有客户端工具请求、客户端工具结果或 Task 权威状态。 |
| 新 endpoint | OUT | 当前版本不新增 runtime 专用客户端工具 endpoint；调用创建、继续、查询和取消必须复用 FEAT-006 / FEAT-001 定义的标准入口语义。 |
| 纯用户交互中断 | OUT | 纯粹需要用户补充输入、选择、决策或材料的等待由 FEAT-008 承接，不作为 FEAT-009 的客户端本地工具调用事实。 |

## 3. 外部接口与入口要求

FEAT-009 不定义新的 HTTP endpoint、A2A method、Java SPI、wire 字段名或内部 DTO。下游设计可以细化具体字段结构和实现类型，但不得改变以下外部接口事实。

| 接口面 | 来源 / 去向 | FEAT-009 使用语义 |
|---|---|---|
| 创建 invocation | FEAT-006 / FEAT-007 -> runtime | client 发起标准 Agent 调用时可携带当前 ToolView。runtime 接收后把 ToolView 绑定到当前 Task 执行上下文。 |
| 客户端工具请求投影 | runtime -> client | Agent 执行中产生客户端本地工具调用时，runtime 通过本次调用响应返回等待客户端工具结果的投影。投影可以被 client 用来执行 FEAT-007 的本地校验、授权、审批和工具执行。 |
| 流式响应 | FEAT-001 / FEAT-006 | 流式调用中出现客户端工具请求时，runtime 必须通过当前响应流投影等待状态，并按标准中断/等待语义收束本次发送流。 |
| 非流式响应 | FEAT-001 / FEAT-006 | 非流式调用中出现客户端工具请求时，runtime 必须返回可恢复的等待状态投影，而不是无限等待客户端本地工具执行完成。 |
| continuation invocation | FEAT-006 / FEAT-007 -> runtime | client 提交客户端工具 outcome 时，应以新的 continuation invocation 关联旧 invocation 的等待状态。runtime 内部映射回原挂起 Task。 |
| Task 查询 | FEAT-001 / FEAT-006 | 等待客户端工具结果期间，查询只能观察到 Task 处于等待/中断状态及必要恢复投影摘要，不能观察到 completed。 |
| Task 取消 | FEAT-001 / FEAT-006 | 等待期间取消使当前等待点失效；后续迟到工具结果不得推进 Task。 |

客户端工具请求投影的具体字段由 FEAT-001、FEAT-006、FEAT-007、FEAT-010 和 L2 设计共同约束。黑盒事实层只要求该投影足以让 client 尝试按 FEAT-007 校验和执行：如果工具不存在、未暴露、越权、参数非法或模型幻觉导致无法执行，client 可以在 continuation invocation 中提交结构化异常 outcome。

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户 / 系统动作 | 期望行为 |
|---|---|---|---|
| 带 ToolView 发起调用 | 业务应用已通过 FEAT-007 注册并显式暴露本地工具 | client 按 FEAT-006 创建 invocation，并随调用提交当前 ToolView | runtime 接收 ToolView 并绑定到当前 Task 执行上下文；Agent/core 能在任务范围内感知这些客户端能力。 |
| 流式调用中请求客户端工具 | client 使用流式 invocation，Agent 执行中选择客户端本地工具 | runtime 观察到执行链路产生客户端本地工具调用 | runtime 挂起当前 Task，通过 SSE / 流式响应投影客户端工具请求并收束本次发送流；Task 保持等待客户端工具结果的非终态。 |
| 非流式调用中请求客户端工具 | client 使用 blocking invocation，Agent 执行中选择客户端本地工具 | runtime 观察到执行链路产生客户端本地工具调用 | runtime 不继续阻塞等待客户端执行工具，而是通过本次响应返回可恢复的等待状态和客户端工具请求投影。 |
| 客户端执行工具后恢复 Task | client 已收到客户端工具请求投影 | client 按 FEAT-007 执行本地工具、拒绝、返回错误或超时，并按 FEAT-006 创建 continuation invocation | runtime 校验 continuation 与挂起 Task 的关联，把工具 outcome 作为恢复输入交回执行链路；Agent/core 决定继续、完成、失败、再次请求工具或进入其他等待。 |
| 幻觉或越权工具调用 | Agent 请求了 ToolView 中不存在、未暴露或越权的客户端工具 | client 校验工具请求投影失败 | client 按 FEAT-007 提交 tool_not_declared、permission_denied、invalid_tool_arguments 或等价 outcome；runtime 将其作为客户端工具结果输入恢复执行链路。 |
| 等待期间查询 | Task 已挂起等待客户端工具结果 | 业务应用通过 invocation 查询，或平台按标准 Task 查询 | 查询投影显示 Task 处于等待客户端工具结果的中断状态，不显示 completed。 |
| 等待期间取消和迟到结果 | Task 等待客户端工具结果期间被取消 | client 后续提交已经完成但迟到的工具结果 | runtime 不恢复已取消或终态 Task；迟到结果被拒绝、忽略或映射为明确状态冲突。 |

## 5. 行为语义与边界

### 5.1 ToolView 与任务范围语义

- ToolView 来自 FEAT-007 的本地工具目录和暴露策略，并随 FEAT-006 invocation 进入平台链路。
- runtime 只把 ToolView 绑定到当前 Task 执行上下文，不把它注册为平台全局工具、服务端工具目录、Skill Hub、MCP 或 agent-middleware 工具。
- ToolView 的任务级可见性、Agent 工具面构造和调用移交由 FEAT-010 定义；FEAT-009 不定义 core 的内部工具选择算法。
- 服务端不应缓存客户端长期工具视图；每次真实 invocation 的客户端能力事实以随调用进入的 ToolView 为准。

### 5.2 客户端工具请求响应语义

- 客户端本地工具调用是当前 Task 的可恢复等待点，不是完成态，也不是 Agent 最终答案。
- runtime 必须通过本次调用响应把客户端工具请求返回给 client；流式和非流式都不得等待客户端本地工具实际执行完成后再继续本次响应。
- 客户端工具请求投影是让 client 尝试执行本地能力的请求，不保证工具一定存在、可用、授权或参数正确。
- 如果 Agent/core 因幻觉、误传或越权产生不可执行工具请求，client 应按 FEAT-007 返回结构化异常 outcome。

### 5.3 continuation 与恢复语义

- 客户端工具结果提交遵守 FEAT-006 的 continuation invocation 语义：业务应用 / client 使用新的 invocation 关联旧 invocation 的等待状态。
- runtime 在服务端语义上恢复原挂起 Task，不创建新的服务端 Task owner。
- continuation 必须由 runtime 校验租户、调用关联、Task 状态、恢复上下文和幂等语义。
- 合法 continuation 使 Task 离开等待客户端工具结果状态，并把工具 outcome 交回执行链路。
- 重复 continuation、错关联 continuation、终态 Task continuation 或恢复上下文缺失必须被拒绝或映射为标准错误，不得隐式创建新 Task。

### 5.4 客户端工具 outcome 语义

- `OK`、用户拒绝、权限不足、工具不可用、工具未声明、参数非法、执行失败、超时等 outcome 都是客户端工具结果输入。
- runtime 不根据 outcome 的业务含义直接决定 Task 成败；Agent/core 可以据此继续、换工具、再次请求客户端工具、失败或完成。
- runtime 可以基于协议非法、恢复点非法、Task 状态非法、跨租户、重复提交或不可恢复等 runtime 层事实拒绝请求或推进失败。
- 工具执行结果正文、错误正文、授权引用、审计引用、payloadRef 或最小必要结果的生成和提交由 FEAT-007 约束。

### 5.5 查询、取消与迟到结果语义

- 等待客户端工具结果期间，Task 查询必须可观察到等待/中断状态。
- 等待状态不是 completed，也不是普通 failed；除非 runtime 层发生明确失败。
- 等待期间可以取消 Task；取消后等待点失效。
- 取消、失败、完成或其他终态之后到达的客户端工具结果不得恢复 Task。

### 5.6 与用户交互中断的边界

- 如果 Agent 只是要求用户补充输入、做自然语言选择、提供材料或回答问题，应按 FEAT-008 的交互式中断处理。
- 如果 Agent 请求执行客户端本地工具，而该工具作为 Action 需要本地确认或审批，则确认和审批过程属于 FEAT-007 的客户端本地治理；最终 outcome 进入 FEAT-009 的工具结果恢复链路。
- FEAT-009 不定义审批 UI、表单协议、人工确认流程或 HITL 专用状态机。

### 5.7 错误与可观测语义

| 场景 | 事实要求 |
|---|---|
| ToolView 缺失或为空 | Agent/core 不应感知未暴露客户端工具；如仍产生客户端工具请求，client 可返回未声明或不可用 outcome。 |
| 客户端工具请求投影生成失败 | runtime 应返回标准错误或使 Task 进入可诊断失败表面，不得伪造完成。 |
| continuation 关联无效 | runtime 拒绝恢复，并返回明确错误或状态冲突。 |
| Task 已终态 | runtime 不恢复 Task；迟到结果被拒绝、忽略或映射为明确错误。 |
| 客户端返回工具异常 outcome | runtime 将其作为恢复输入交给执行链路，不直接伪造成 runtime 执行失败。 |
| 恢复执行失败 | runtime 按标准失败表面处理，并保留可诊断错误。 |
| 可观测链路 | runtime 应记录 ToolView 进入、客户端工具请求响应、Task 挂起、continuation 接收、校验、恢复、取消、迟到结果和失败等事实，并关联 tenant、conversation、invocation、Task、trace 和耗时。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把 FEAT-009 作为 runtime 处理客户端本地工具请求响应和恢复 Task 的事实来源，不得把 runtime 描述为客户端工具执行器。
- FEAT-009 不新增 runtime 专用客户端工具 endpoint；创建 invocation、continuation、查询和取消必须复用 FEAT-006 / FEAT-001 的入口语义。
- runtime 必须把 ToolView 作为当前 Task 执行上下文的一部分交给 Agent 执行链路；具体 task 级动态工具可见性和调用移交由 FEAT-010 承接。
- 下游实现不得把 ToolView 提升为平台全局工具目录，也不得把客户端本地工具注册为 agent-middleware、Skill Hub、MCP 或服务端工具。
- 工具结果 outcome 的业务含义应交给 Agent/core 处理；runtime 只处理协议、恢复点、Task 状态、幂等和安全边界。
- 测试必须覆盖带 ToolView 创建调用、流式工具请求响应、非流式工具请求响应、continuation 工具结果恢复、客户端拒绝/越权/未声明/参数非法 outcome、等待期间查询、等待期间取消、迟到结果、重复 continuation 和恢复上下文缺失。
- 开发指南必须明确区分 FEAT-007 的 client 本地执行、FEAT-008 的纯用户交互中断、FEAT-009 的 runtime 工具请求响应与恢复、FEAT-010 的 core 动态工具可见性。
- 任何对客户端直连执行、服务端驱动本地审批、专用工具 endpoint、平台全局客户端工具目录、独立工具状态机或独立 HITL 状态机的新增承诺，都必须先更新本特性或新增 version-scope 特性。

## 7. 关联文档

- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-006-standard-agent-client-invocation.md`
- `version-scope/FEAT-007-local-tool-registration-and-execution.md`
- `version-scope/FEAT-008-user-interaction-interrupt-response.md`
- `version-scope/FEAT-010-task-level-dynamic-tool-visibility-and-handoff.md`
- `architecture/L1-High-Level-Design/agent-runtime/README.md`
- `architecture/L1-High-Level-Design/agent-runtime/logical.md`
- `architecture/L1-High-Level-Design/agent-runtime/scenarios.md`
- `architecture/L1-High-Level-Design/agent-runtime/api-appendix.md`
- `architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md`
