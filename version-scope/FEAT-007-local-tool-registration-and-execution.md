---
version: 0715
module: agent-client
feature_type: functional
feature_id: FEAT-007
status: active
---
# 客户端本地工具注册与调用特性文档

## 1. 特性定位

FEAT-007 定义 `agent-client` 当前版本承载客户端本地工具接入的事实：业务应用在集成 client SDK 的开发阶段通过标准 SPI 实现并注册本地工具，再在运行时显式声明 conversation 级或 invocation 级工具暴露策略与范围。服务端 Agent 只能基于 client 随真实 invocation 上报的 ToolView 请求这些已暴露工具；client 本地执行后通过受治理入口主动提交结果。

本特性解决的问题是：客户端侧本地工具需要进入智能体执行闭环，但不能被服务端当作可直接远程调用的函数、服务端工具项或可动态发现的客户端资源。客户端工具必须先由业务应用通过 SDK SPI 注册成本地工具目录，再由业务应用显式声明工具暴露策略，client 才能在发起 invocation 时计算并上报当前 ToolView。服务端只能基于该 ToolView 产生端侧工具请求；client 收到请求后只对已注册、已暴露且当前可用的工具执行，执行结果作为外部输入经 Gateway / IngressGateway 回到服务端，runtime 校验后决定是否推进 Task。

在总体架构中，本特性位于 application / agent-client 与 agent-runtime 端侧工具调用请求之间。它不替代 agent-middleware 的工具服务，也不让平台消息通道直接承载客户端本地资源访问。其事实边界是客户端 SDK 本地工具 SPI、工具目录、工具暴露策略、ToolView 上报、工具请求投影消费、本地执行、执行记录和结果提交。服务端工具请求契约、runtime Task 生命周期和 Task 状态推进不属于本特性的主权范围。

本特性面向以下角色：

- 业务应用开发者：通过 SDK SPI 实现和注册本地工具，并声明工具暴露策略。
- 客户端集成方：把本地上下文、只读观测和受控动作接入智能体调用链。
- agent-client SDK 开发者：实现本地工具 SPI、工具目录、暴露策略解析、ToolView 生成、工具请求投影消费、本地执行调度、执行记录和结果提交 facade。
- Gateway / IngressGateway 开发者：转发 ToolView、工具请求投影和结果提交，不直接访问客户端资源。
- agent-runtime 开发者：基于 ToolView 发起端侧工具请求，并在收到客户端结果后校验恢复点和推进 Task。
- 测试与验收团队：验证工具注册、显式暴露、权限、审批、幂等、超时和执行失败语义。

本特性只定义客户端本地工具的注册、暴露、请求识别、执行和结果提交闭环。服务端如何产生端侧工具调用意图由 FEAT-009 / FEAT-010 承接；标准 invocation 入口由 FEAT-006 承接；Gateway 路由和总线转发由 FEAT-011 / FEAT-012 / FEAT-013 承接；agent-middleware 工具执行和 runtime TaskStore 不属于本特性。

## 2. 当前版本工具要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 本地工具 SPI 注册 | MUST | SDK 必须允许业务应用在集成开发阶段通过 SPI 注册本地工具描述与 handler，包括稳定 toolId、名称、描述、类型、输入输出 schema、授权策略、审计策略、超时和幂等策略。 |
| 默认不暴露工具 | MUST | 未经业务应用显式声明，client 不得向服务端暴露任何本地工具；本地工具目录不等于服务端可见工具视图。 |
| 工具暴露策略 | MUST | SDK 必须支持业务应用声明 conversation 级和 invocation 级可暴露工具范围；invocation 级策略可以收窄或覆盖 conversation 级策略。 |
| ToolView 生成与上报 | MUST | client 必须在真实 invocation 请求中附上当前 ToolView；ToolView 由本地工具目录、conversation / invocation 暴露策略、租户、用户、上下文和工具可用性共同计算得到，避免服务端缓存客户端工具视图。 |
| Observation / Action 分类 | MUST | 本地工具必须区分 Observation 与 Action；Observation 偏只读观测，Action 产生副作用并必须支持更严格的授权、审批、幂等和审计要求。 |
| 工具请求投影消费 | MUST | SDK 必须能从 Gateway / runtime SSE、Task 投影或等价受治理消息中识别端侧工具请求投影，并校验其与当前 invocation、ToolView 和本地工具目录的匹配关系。 |
| 本地执行状态 | MUST | SDK 必须为每次工具请求投影维护本地执行状态和执行记录；该状态只用于客户端执行、UI 展示和本地审计。 |
| 本地工具执行 | MUST | Client 收到工具请求后，只能执行已注册、已暴露、当前可用且权限允许的本地工具，并返回最小必要结果、授权引用、审计引用或结构化拒绝。 |
| 本地授权与审批 | MUST | 无需授权的工具可由 client 自动执行；需要授权、确认或审批的 Action 工具必须进入 client / 业务应用本地治理流程，服务端不得远程驱动客户端内部审批步骤。 |
| 结果提交 | MUST | 工具执行结果必须通过 Gateway / IngressGateway 主动提交给 runtime；该提交是同一业务 invocation 下的 client 内部恢复请求，不是业务应用感知的新 invocation。 |
| 单次最终结果 | MUST | 一次工具请求投影在协议闭环上最多接受一个最终结果；重复提交必须幂等返回同一结果或明确冲突。 |
| 工具执行结果错误结构化 | MUST | 未声明工具、工具不可用、权限拒绝、上下文过期、参数非法、超时、重复提交和执行失败必须有结构化错误。 |
| 状态边界 | MUST | SDK 只保存本地工具目录、暴露策略、执行状态、审计引用和 invocation 到 taskRef 的必要映射，不拥有服务端 Task 权威状态，也不决定 runtime Task 是否恢复或终态。 |

## 3. 外部接口与入口要求

本节定义 `agent-client` 主权范围内的接口面和客户端侧对象，不定义 runtime / Gateway 的服务端工具请求契约主权。服务端工具请求只能作为 client 消费的受治理投影引用出现。

| 入口 / 对象 | 类型 | 事实要求 |
|---|---|---|
| `LocalToolDescriptor` | SDK SPI 对象 | 描述本地工具的稳定 toolId、name、description、Observation / Action 类型、输入输出 schema、授权策略、审批策略、审计策略、超时和幂等策略。toolId 是执行匹配主键，name / description 用于模型和 UI 说明。 |
| 本地工具注册 SPI | SDK SPI | 业务应用在集成开发阶段实现并注册本地工具 handler；SDK 必须校验声明完整性和 toolId 唯一性。 |
| `ToolExposurePolicy` | SDK facade / 配置对象 | 业务应用显式声明 conversation 级或 invocation 级可暴露工具范围、拒绝范围、过期时间、最大使用次数、Action 授权模式、数据最小化和脱敏策略。默认策略为空视图。 |
| `ToolView` | client 能力投影 | client 基于本地工具目录和暴露策略生成，随 FEAT-006 invocation 上报给 Gateway / runtime。ToolView 只表达当前可见客户端能力，不是服务端工具目录，也不得包含未授权工具。 |
| 工具请求投影消费 | SDK 消费语义 | SDK 必须能识别 Gateway / runtime 投影中的工具调用请求，校验 toolId、参数 schema、conversation / invocation 关联、权限、过期和 ToolView 可见性；本文不定义该请求的服务端契约主权。 |
| `ToolExecutionRecord` | client 本地状态对象 | 记录工具请求关联、本地执行状态、授权结果、执行结果摘要、拒绝原因、错误、幂等键和本地审计 / 证据引用。 |
| 工具结果提交 facade | SDK 内部恢复请求 | client 主动提交结果、拒绝或结构化错误，必须携带工具请求关联、执行 outcome、最小必要 payload 或 payloadRef、幂等键和本地审计 / 证据引用；runtime 是否恢复 Task 不属于本特性决定。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 开发期注册本地工具 | 业务应用集成 agent-client SDK | 开发者通过 SDK SPI 注册 Observation 或 Action 工具及 handler | SDK 校验工具描述、schema、toolId 唯一性、授权策略、审计策略和执行约束，形成 client 本地工具目录；默认不向服务端暴露。 |
| conversation 级声明工具暴露范围 | 业务应用准备开启或复用 conversation | 业务应用声明该 conversation 允许暴露的本地工具范围和策略 | client 保存 conversation 级 ToolExposurePolicy；该策略本身不等于已上报 ToolView，真实 ToolView 必须随后续 invocation 请求发送。 |
| invocation 级声明工具暴露范围 | 某次 invocation 需要收窄、覆盖或临时开放工具 | 业务应用在 FEAT-006 标准 invocation 中携带 invocation 级工具暴露策略 | client 基于 invocation 策略、conversation 策略和本地工具目录计算 ToolView，并随本次 invocation 上报。未声明的工具不进入服务端可见视图。 |
| 服务端请求本地工具 | runtime 基于 ToolView 判断需要端侧工具参与 | runtime / Gateway 通过服务流、Task 投影或等价消息投影工具请求 | client 识别工具请求投影，校验该工具已注册、已暴露、参数匹配、上下文有效且权限允许；未通过校验时返回结构化拒绝。 |
| 自动执行 Observation 工具 | 工具为只读 Observation，策略允许自动执行 | client 调用本地 Observation handler | client 执行数据范围、脱敏、租户隔离和可见性校验后返回最小必要观测结果或引用，并记录本地执行证据。 |
| 执行 Action 工具 | 工具会产生业务副作用 | client 根据策略触发本地授权、审批、幂等和审计流程 | 授权通过后执行 Action handler；拒绝、超时或失败必须作为结构化结果返回。服务端不得远程驱动客户端内部审批步骤。 |
| 工具结果提交闭环 | 本地工具执行完成、拒绝或失败 | client 通过内部恢复请求提交工具结果 | client 携带工具请求关联、outcome、payload / payloadRef、幂等键和审计引用，经 Gateway 提交 runtime；runtime 校验后决定是否恢复、继续等待、失败或取消 Task。 |
| 幻觉或越权工具调用 | 服务端请求未在 ToolView 中暴露的工具，或请求超出授权范围 | client 收到工具请求投影 | client 不执行、不动态注册，返回 tool_not_declared、permission_denied 或等价结构化拒绝信号。 |
| 工具目录更新 | 业务应用升级或撤销本地工具 SPI | 后续 invocation 使用新的工具目录和暴露策略计算 ToolView | 服务端只感知随 invocation 上报的 ToolView；历史 invocation 的工具请求按其关联上下文校验，不因本地目录变化自动获得新工具能力。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.0 工具注册语义

- 客户端本地工具必须由业务应用在集成开发阶段通过 SDK SPI 显式实现和注册，不能由模型或服务端任意创建。
- 本地工具目录是 client 本地事实，不是服务端可见工具目录。
- SDK 必须保留工具版本、schema、策略和 handler 绑定信息，使后续 ToolView 和工具请求校验可追溯。
- 工具标识必须包含稳定 toolId；name 和 description 可用于模型理解和 UI 展示，但不得替代 toolId 作为执行匹配主键。

#### 5.1.1 工具暴露与 ToolView 语义

- client 默认不向服务端暴露任何本地工具。
- 业务应用必须显式声明 conversation 级或 invocation 级 ToolExposurePolicy 后，client 才能生成非空 ToolView。
- conversation 级策略表示该 conversation 下的默认暴露范围；invocation 级策略可以收窄、覆盖或临时开放本次 invocation 的暴露范围。
- 即使已经存在 conversation 级暴露策略，client 也必须在真实 invocation 请求中附上当前 ToolView，避免服务端缓存客户端工具视图。
- ToolView 是 client 对服务端可见本地能力的投影，只表达当前可见工具，不表达未授权工具、已撤销工具或 client 本地全量工具目录。

#### 5.1.2 Observation / Action 语义

- Observation 工具用于只读环境观测，例如本地上下文读取、检索、文件摘要、UI 状态快照、业务事实读取或本地记忆读取。
- Observation 的治理重点是数据范围、脱敏、租户隔离、可见性和时效性；策略允许时可以自动执行。
- Action 工具用于产生业务副作用，例如审批提交、工单更新、业务写入、消息发送、本地命令或外部系统操作。
- Action 的治理重点是授权、审批、幂等、补偿、审计和失败回滚；需要授权的 Action 必须经过 client / 业务应用本地治理。
- 服务端不能通过多步工具请求远程驱动客户端内部审批流程；审批过程是 client 本地治理，不是服务端执行协议的一部分。

#### 5.1.3 远端驱动语义

- 服务端只能通过受治理消息请求 ToolView 中可见的客户端工具，不能直接访问客户端本地资源。
- 工具请求投影必须可与某个 invocation、ToolView、toolId、参数和 correlation 对齐。
- Client 只能对已注册、已暴露且当前可用的工具执行调用；未声明、未暴露、过期、无权限或参数非法的工具必须返回结构化错误。
- 如果模型因上下文幻觉请求未在 ToolView 中的工具，client 必须拒绝并返回结构化拒绝信号，不得动态注册或执行。

#### 5.1.4 本地执行与结果提交语义

- 客户端工具执行结果是外部输入，不是服务端事实，runtime 必须校验后才能决定是否推进 Task。
- 无需授权的本地工具执行可以由 client 在同一业务 invocation 下自行完成；工具结果提交是 client 内部恢复请求，不是业务应用感知的新 invocation。
- 权限不足、工具不可用、上下文过期、参数非法和执行失败都应作为结构化结果提交，而不是静默丢弃。
- 一次工具请求投影在协议闭环上最多接受一个最终结果；本地执行过程可以有本地中间状态、审批、进度和重试，但不得要求服务端直接驱动客户端内部步骤。
- 重复结果提交必须通过幂等键返回同一结果或明确冲突。
- runtime 是否恢复、继续等待、失败或取消 Task 不属于 client 决策范围。

#### 5.1.5 错误、状态与可观测结果

| 场景 | 事实要求 |
|---|---|
| 工具未声明 | SDK 返回 tool_not_declared 或等价错误。 |
| 工具未注册 | SDK 返回 tool_not_found 或等价错误。 |
| 工具不可用 | SDK 返回 tool_not_available、stale_context 或等价错误。 |
| 参数非法 | SDK 返回 invalid_tool_arguments。 |
| 权限不足 | SDK 返回 permission_denied。 |
| 用户拒绝 | SDK 返回 rejected，并保留必要审计引用。 |
| 执行超时 | SDK 返回 timeout 或 expired，并按本地策略停止或隔离执行。 |
| 重复提交 | SDK 通过幂等键返回同一结果或冲突。 |
| 任务已终态 | SDK 不再主动提交结果，或提交后由平台拒绝并映射为明确错误。 |
| 服务端幻觉调用 | SDK 返回 tool_not_declared 或 permission_denied，不执行、不动态注册。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 服务端 Task 权威状态 | SDK 不保存 TaskStore，只保存本地执行状态、ToolView 生成上下文和必要 taskRef 映射。 |
| 默认暴露本地工具 | 未显式声明 ToolExposurePolicy 时，client 不向服务端暴露任何本地工具。 |
| 自动上传本地敏感数据 | 本地数据默认最小化、脱敏或引用传递。 |
| 绕过 Gateway 提交结果 | 结果提交必须进入受治理入口。 |
| 服务端直接调用本地函数 | 服务端只能发起受治理工具请求投影，不能直接访问客户端本地函数、进程或网络端点。 |
| 服务端工具目录 | ToolView 不是服务端工具目录，未暴露工具对服务端不可见。 |
| 服务端驱动本地审批 | 本地授权、确认和审批由 client / 业务应用治理，服务端不得拆分驱动客户端内部步骤。 |
| 跨租户工具共享 | 客户端工具默认不跨租户、用户或 conversation 共享。 |
| 客户端推进 Task | SDK 不决定服务端 Task 是否恢复、完成、失败或取消。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本特性作为客户端本地工具接入事实来源，不得把客户端工具描述为服务端可直接调用的函数。
- `agent-client`、Gateway / IngressGateway、runtime 端侧工具响应和 agent-core handoff 设计必须共享本地工具 SPI、ToolExposurePolicy、ToolView、工具请求投影消费、结果提交和本地执行状态等核心语义。
- 测试必须覆盖工具 SPI 注册、默认空 ToolView、conversation 级暴露策略、invocation 级暴露策略、ToolView 随 invocation 上报、Observation 自动执行、Action 授权审批、权限不足、工具未声明、工具不可用、参数非法、重复提交、单次最终结果和 Task 终态冲突。
- 开发指南必须强调客户端工具结果是外部输入，需要 runtime 校验后才能回灌 Agent；client 不能越权替 runtime 推进 Task。
- 任何对默认暴露工具、跨租户共享工具、服务端直接调用本地函数、服务端驱动本地审批或客户端推进 Task 状态的新增承诺，都必须先更新本特性或新增 version-scope 特性。
- 本特性术语必须保持稳定：本地工具、LocalToolDescriptor、ToolExposurePolicy、ToolView、Observation、Action、工具请求投影、ToolExecutionRecord、结果提交、本地执行状态。

## 7. 关联文档

- `agent-sdk/Docs/Agent-client组件本地工具注册与远端驱动调用特性设计.md`
- `version-scope/FEAT-006-standard-agent-client-invocation.md`
- `JAVA local working/version-scope/FEAT-011-client-invocation-route-forwarding.md`
- `JAVA local working/version-scope/FEAT-012-client-invocation-bus-forwarding.md`
- `JAVA local working/version-scope/FEAT-013-client-invocation-event-forwarding.md`
- `Docs/FEAT_Design/FEAT-009-agent-runtime-client-side-tool-response.md`
- `Docs/FEAT_Design/FEAT-010-agent-core-dynamic-client-side-tool-registration-invocation.md`
