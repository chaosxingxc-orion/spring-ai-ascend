---
scope: version-draft
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-008
status: draft
updated: 2026-07-17
authority:
  - ../architecture/L0-Top-Level-Design
  - ../architecture/L1-High-Level-Design/agent-runtime
  - ./FEAT-001-standardized-agent-service-entrypoint.md
  - ./FEAT-002-heterogeneous-agent-framework-compatibility.md
  - ./FEAT-005-remote-agent-orchestration.md
  - ./DFX-001-trajectory-observability.md
drives:
  - ../architecture/L1-High-Level-Design/agent-runtime/logical.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-008-user-interaction-interrupt-response.md
---

# 用户交互中断响应

## 1. 特性定位

FEAT-008 定义 `agent-runtime` 在运行时观察到“交互式中断”后的 Task 状态、客户端可见行为和续接语义。交互式中断可以由本地智能体产生，也可以由下游远端智能体产生并经本地 runtime 投影；无论来源如何，其含义都是：当前 Task 的执行需要交由客户端侧提供后续输入、决策或材料，runtime 必须把该等待点暴露给客户端，并在客户端使用同一 Task 的标准 A2A 消息续接后恢复执行链路。

本特性解决的问题是：本地或远端智能体执行过程中可能进入需要客户端参与的等待点。客户端不应理解具体 Agent 框架、远端 runtime 或 checkpoint 细节；它只应通过 FEAT-001 定义的标准智能体服务入口观察 Task 进入 `INPUT_REQUIRED`，并通过同一 Task 的标准 A2A `SendMessage` 续接。runtime 负责保持 Task 生命周期、等待状态和恢复通道一致，但不判断客户端后续消息在业务语义上是否“回答正确”。

交互式中断不是“上游调用智能体内部继续处理”的信号。如果某个等待应由上游智能体自行处理，而不是交给客户端侧处理，则不应建模为 FEAT-008 的交互式中断；应按任务失败、异常终止或下一轮新任务调用处理。

本特性不定义新的服务化入口、A2A method、A2A Part 类型、客户端响应格式、表单 schema、审批协议或框架 SPI。相关职责归属如下：

- FEAT-001 定义 A2A 服务入口、Task/Message/SSE/error 表面以及任何客户端可见消息结构扩展。
- FEAT-002 定义本地异构智能体框架如何把原生中断归一为 runtime 可观察的执行结果，以及如何恢复执行。
- 任务状态缓存特性负责长生命周期 Task 状态缓存、冷热转换、持久化、跨实例和重启后的恢复能力。
- FEAT-005 定义远端 Agent 调用、远端 Task 绑定、远端中断投影、续接、重试和取消传播。
- DFX-001 定义轨迹、审计和敏感信息处理。

FEAT-008 自身只定义这些能力组合起来后的交互式中断处理语义。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 本地交互式中断处理 | MUST | 当本地智能体执行结果通过 FEAT-002 归一为需要客户端参与的交互式中断时，runtime 必须将当前 Task 推进到 `INPUT_REQUIRED`，并通过 FEAT-001 标准响应表面暴露等待输入状态和提示信息。 |
| 远端交互式中断投影 | MUST | 当 FEAT-005 远端调用链路观察到远端 Task 进入需要客户端参与的 `INPUT_REQUIRED` 时，本地 runtime 必须把该等待点投影到本地 Task，使客户端仍通过本地 Task 观察和续接。 |
| 标准服务入口复用 | MUST | 客户端续接交互式中断必须使用 FEAT-001 定义的标准 A2A `SendMessage` 与 Task/context 关联语义。FEAT-008 不新增、不收紧客户端请求格式。 |
| 同 Task 续接 | MUST | Task 处于 `INPUT_REQUIRED` 时，客户端使用同一 Task 发送的合法 A2A 消息必须被视为续接该等待点的输入，并交回当前执行链路。runtime 不得因消息业务内容看起来“不符合 prompt”而拒绝续接。 |
| 非同 Task 隔离 | MUST | 客户端使用不同 Task 或新建请求发送的消息不得抢占或隐式续接旧的 `INPUT_REQUIRED` Task；应按 FEAT-001 创建或推进对应 Task。 |
| 业务语义归属智能体 | MUST | 客户端续接消息是否满足此前等待点的业务期待，由产生中断的本地或远端智能体判断。智能体可以继续执行、再次产生交互式中断、失败或完成。runtime 只处理协议、访问、Task 状态和恢复通道层面的事实。 |
| 单等待点推进 | MUST | 在 FEAT-001 入口、任务状态缓存和 FEAT-005 远端编排的既有幂等机制排除重复提交后，同一 Task 的一次 `INPUT_REQUIRED` 等待点只应被一条合法续接消息推进一次。后续消息按其到达时的 Task 状态交给对应特性的并发或追加语义处理。 |
| 多轮交互 | MUST | 同一 Task 必须支持顺序发生多轮 `WORKING -> INPUT_REQUIRED -> WORKING`。每一轮是否再次等待由智能体执行结果决定。 |
| 长时挂起 | MUST | `INPUT_REQUIRED` 是非终态，Task 可以长时间等待客户端续接、查询、订阅或取消。FEAT-008 不设置 TTL，不定义自动过期。 |
| 等待期间查询与订阅 | MUST | `GetTask` 和 `SubscribeToTask` 必须能按 FEAT-001 语义观察处于 `INPUT_REQUIRED` 的 Task 及其后续状态变化。 |
| 等待期间取消 | MUST | 处于 `INPUT_REQUIRED` 的 Task 必须能通过 FEAT-001 的 `CancelTask` 取消；取消后 Task 进入 `CANCELED`，该等待点失效。 |
| 当前实例内恢复 | MUST | 在当前 runtime 实例拥有 Task、等待点绑定和恢复上下文的期间，合法续接消息必须能够恢复到正确执行链路。跨实例、重启和长生命周期恢复由任务状态缓存特性承接。 |
| 明确运行时失败 | MUST | Task 不存在或不可访问、Task 状态不允许续接、恢复上下文不可用、本地恢复失败、远端续接失败等运行时事实必须映射为可区分的标准 Task/error 表面。 |
| 可观测与审计 | SHOULD | runtime 应记录中断建立、Task 状态变化、客户端续接、恢复、再次中断、取消和失败等观察事实，并关联 tenant、caller、user、Task/context、request、trace 和耗时；具体脱敏规则遵守 DFX-001。 |

## 3. 引用接口与入口要求

FEAT-008 不拥有独立外部 API 或 SPI 定义权。下游设计与实现必须引用以下既有特性接口。

### 3.1 FEAT-001 标准智能体服务入口

| API | FEAT-008 使用语义 |
|---|---|
| `SendMessage` | 客户端创建 Task 或续接已有 Task 的统一入口。Task 处于 `INPUT_REQUIRED` 且请求关联同一 Task/context 时，该消息作为交互式中断的续接输入。消息体格式完全遵守 FEAT-001。 |
| `SendStreamingMessage` | 执行进入 `INPUT_REQUIRED` 时，按 FEAT-001 的 interrupted stream 语义推送 Task 状态并结束本次发送流。 |
| `GetTask` | 返回 Task 当前状态；当状态为 `INPUT_REQUIRED` 时，调用方可观察到等待客户端输入的状态和提示信息。 |
| `SubscribeToTask` | 订阅已有 Task 的后续事件，观察等待、续接后的 `WORKING`、再次中断或终态。 |
| `CancelTask` | 取消等待中的 Task；取消后当前交互式等待点失效。 |

如果未来需要统一结构化表单、候选项、审批控件或专用响应 Part，这些客户端可见 wire 契约必须先由 FEAT-001 或新的服务入口特性声明，FEAT-008 只能引用。

### 3.2 FEAT-002 异构智能体框架兼容

本地智能体如何表达“需要客户端交互”、adapter 如何归一原生中断、runtime 如何恢复 handler 执行，属于 FEAT-002 的框架兼容和 SPI 范围。FEAT-008 只消费 FEAT-002 已归一出的交互式中断事实，不定义 `AgentExecutionResult`、handler input type、resume context 或 adapter API。

### 3.3 任务状态缓存特性

FEAT-008 承认 `INPUT_REQUIRED` 可以长时挂起，但不定义 Task 状态缓存、冷热转换、持久化存储、跨实例恢复或重启恢复接口。这些能力由任务状态缓存特性负责；FEAT-008 只要求在可恢复状态来源存在时，续接语义仍保持同一 Task 生命周期。

### 3.4 FEAT-005 远程 Agent 编排

远端 Agent 的发现、调用、远端 Task 绑定、远端中断投影、续接、重试、取消传播和结果回灌由 FEAT-005 定义。FEAT-008 只要求当远端等待点需要客户端参与时，本地 runtime 对客户端呈现与本地交互式中断一致的 `INPUT_REQUIRED` 语义。

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 本地智能体请求客户端交互 | 本地智能体在已有 Task 执行中需要客户端提供后续输入、决策或材料 | 智能体通过 FEAT-002 定义的归一路径产生交互式中断 | runtime 将当前 Task 推进到 `INPUT_REQUIRED`，通过 FEAT-001 标准响应、SSE 或 Task 查询暴露等待状态和提示；客户端使用同一 Task 的标准 `SendMessage` 续接后，runtime 恢复执行链路。 |
| 远端智能体请求客户端交互 | 本地 Task 正在通过 FEAT-005 调用远端 Agent，远端 Task 进入需要客户端参与的等待状态 | 本地 runtime 观察到远端 `INPUT_REQUIRED` | 本地 Task 进入 `INPUT_REQUIRED`，客户端仍面向本地 Task 响应；本地 runtime 按 FEAT-005 绑定把续接消息传递给远端等待 Task。 |
| 同 Task 发送业务语义不匹配的续接消息 | Task 处于 `INPUT_REQUIRED`，客户端使用同一 Task 发送格式合法的 A2A 消息，但内容没有满足此前提示的业务期待 | 客户端提交该消息 | runtime 不做业务语义拒绝，仍恢复智能体；智能体判断该输入无效时，可以再次中断、失败或按自身逻辑继续。 |
| 非当前 Task 发起新任务 | 旧 Task 处于 `INPUT_REQUIRED`，客户端选择放弃或暂不处理它 | 客户端发起不关联旧 Task 的新 `SendMessage` | 新请求按 FEAT-001 创建或推进新 Task；旧 Task 保持自身 `INPUT_REQUIRED` 状态，直到被续接、取消或由状态缓存/生命周期治理处理。 |
| 长时挂起后续接 | Task 处于 `INPUT_REQUIRED`，客户端或业务应用需要等待人工审批、外部流程或较长时间后再响应 | 客户端稍后查询、订阅或使用同一 Task 续接 | Task 等待期间不因 FEAT-008 自身 TTL 自动过期；在任务状态缓存和恢复上下文可用时，续接仍恢复同一 Task 执行链路。 |
| 等待期间取消 | Task 处于 `INPUT_REQUIRED`，用户或业务应用决定停止当前执行 | 客户端调用 `CancelTask` | runtime 将 Task 推进到 `CANCELED`，当前等待点失效；后续对该 Task 的续接按终态 Task 处理。 |
| 多轮客户端交互 | 智能体在一次续接后仍需要更多客户端信息 | 智能体再次产生交互式中断 | runtime 再次将同一 Task 推进到 `INPUT_REQUIRED`；客户端继续使用同一 Task 续接，直到智能体完成、失败或取消。 |

## 5. 行为语义与边界

### 5.1 `INPUT_REQUIRED` 语义

- `INPUT_REQUIRED` 表示当前 Task 等待客户端侧参与，不是完成态，也不是失败态。
- 等待对象是客户端侧，可能是人类用户，也可能是集成了客户端的业务应用。
- 本地智能体和远端智能体产生的客户端交互等待，在本地 Task 表面对客户端都表现为 `INPUT_REQUIRED`。
- runtime 不解释客户端续接消息的业务含义，只负责把同一 Task 的合法 A2A 消息交回当前执行链路。
- 如果某个等待应由上游智能体自行处理，而不是交给客户端侧处理，则不属于 FEAT-008 的交互式中断。

### 5.2 续接语义

- 续接入口是 FEAT-001 的标准 A2A `SendMessage`。
- 续接关联由 FEAT-001 的 Task/context 语义和可信调用上下文确定；FEAT-008 不定义新的 response id、interaction id 或专用 response payload。
- 同一 Task 处于 `INPUT_REQUIRED` 时，合法 A2A 消息推进该等待点并恢复执行。
- 非同 Task 消息不得隐式续接旧 Task。
- 在统一入口、任务状态缓存和远端编排各自的幂等机制排除重复提交后，同一等待点只推进一次。第一条合法续接消息使 Task 离开 `INPUT_REQUIRED`；第二条消息按到达时的 Task 状态交由 FEAT-001 或相关特性处理。

### 5.3 业务语义归属

- prompt、说明文本、可展示消息或未来 FEAT-001 定义的结构化交互材料，只帮助客户端理解需要提供什么。
- 客户端提供的后续消息是否满足业务要求，由智能体或其业务组件判断。
- runtime 不应校验“文本是否回答了问题”“审批是否通过业务规则”“授权是否有效”等业务语义。
- 如果智能体判断续接输入不满足要求，可以再次产生交互式中断、返回失败或继续执行。
- 授权确认、审批结果回填、表单填写等可以作为智能体产生交互式中断的业务场景，但其业务语义不归 runtime 拥有。

### 5.4 长时等待与恢复边界

- FEAT-008 不设置交互等待 TTL，也不定义自动过期失败。
- 长时等待可以持续十天、半个月或更久；其可恢复性取决于任务状态缓存、持久化、冷热转换、框架 checkpoint 和部署治理能力。
- 当前 runtime 实例内拥有 Task、等待点绑定和恢复上下文时，应能恢复执行。
- 跨实例、重启和长生命周期恢复能力由任务状态缓存特性定义，FEAT-008 不重复定义存储接口。

### 5.5 远端交互边界

- 远端 Runtime 拥有远端 Task 生命周期；本地 Runtime 拥有本地 Task 生命周期。
- 本地 Runtime 只把需要客户端参与的远端等待点投影为本地 `INPUT_REQUIRED`。
- 客户端只需要面向本地 Task 续接；本地到远端的续接、重试、幂等、取消传播和结果回灌由 FEAT-005 负责。
- 如果远端续接经过受控重试仍失败，本地 Task 应按上游调用失败语义进入失败表面；远端 Task 生命周期继续由远端 Runtime 管理。

### 5.6 失败语义

runtime 可以基于以下运行时事实拒绝续接或推进失败：

| 失败场景 | 语义 |
|---|---|
| Task/context 不存在或不可访问 | 按 FEAT-001 访问和错误表面返回，不恢复智能体。 |
| Task 不处于可续接状态 | 返回 Task 状态冲突或按 FEAT-001 对该状态的消息处理语义执行。 |
| 请求不符合 A2A 标准入口格式 | 按 FEAT-001 协议层错误处理。 |
| 恢复上下文不可用 | Task 进入标准失败表面，错误含义为恢复上下文不可用。 |
| 本地执行恢复失败 | Task 进入标准失败表面，错误含义为恢复执行失败。 |
| 远端续接失败 | 按 FEAT-005 的远端调用失败语义回灌本地 Task。 |

runtime 不应基于业务内容不匹配返回上述运行时失败；该类判断由智能体负责。

### 5.7 可观测语义

- 应记录交互式中断建立、Task 进入 `INPUT_REQUIRED`、客户端查询/订阅、客户端续接、恢复开始、恢复结果、再次中断、取消和失败。
- 观察事实应关联 tenant、caller、user、Task/context、远端 Task 绑定信息、request、trace、结果和耗时。
- prompt、客户端输入、业务审批材料、授权信息等敏感内容应按 DFX-001 策略脱敏、摘要或不记录。

## 6. 对下游设计与实现的约束

- 下游设计必须把 FEAT-008 作为交互式中断处理行为的事实来源，而不是服务入口、客户端协议、框架 SPI 或状态存储接口的事实来源。
- 不得在 FEAT-008 的名义下新增 A2A endpoint、method、专用响应 DataPart、统一表单 schema、审批协议或客户端专用 wire 格式；这类能力必须先进入 FEAT-001 或新的服务入口特性。
- 不得在 FEAT-008 的名义下新增或修改 `AgentRuntimeHandler`、`AgentExecutionResult`、adapter resume input 等 SPI；这类能力必须由 FEAT-002 承接。
- 不得在 FEAT-008 的名义下承诺 Task 状态缓存、冷热转换、持久化存储、跨实例或重启恢复；这类能力由任务状态缓存特性承接。
- 不得在 FEAT-008 的名义下重定义远端 Task 绑定、远端续接、重试、幂等或取消传播；这类能力由 FEAT-005 承接。
- 实现必须保证同 Task 的合法续接消息能够恢复当前等待点，并保证非同 Task 消息不会隐式抢占旧等待点。
- 实现必须避免把业务语义判断放入 runtime。业务输入无效、审批未通过、授权材料不足等应由智能体返回再次中断、失败或继续执行。
- 测试应覆盖本地中断、远端中断投影、同 Task 合法续接、同 Task 业务语义不匹配仍恢复智能体、非同 Task 新任务隔离、长时等待查询/订阅、取消、多轮交互、恢复上下文缺失、本地恢复失败和远端续接失败。
- 文档和开发者指南必须统一使用 `INPUT_REQUIRED`、交互式中断、同 Task 续接、标准 A2A 入口、本地/远端 Task 生命周期所有权等术语。

## 7. 关联文档

- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L0-Top-Level-Design/constraints.md`
- `architecture/L0-Top-Level-Design/glossary.md`
- `architecture/L1-High-Level-Design/agent-runtime/README.md`
- `architecture/L1-High-Level-Design/agent-runtime/overview.md`
- `architecture/L1-High-Level-Design/agent-runtime/logical.md`
- `architecture/L1-High-Level-Design/agent-runtime/process.md`
- `architecture/L1-High-Level-Design/agent-runtime/scenarios.md`
- `architecture/L1-High-Level-Design/agent-runtime/api-appendix.md`
- `architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-002-heterogeneous-agent-framework-compatibility.md`
- `version-scope/FEAT-005-remote-agent-orchestration.md`
- `version-scope/DFX-001-trajectory-observability.md`
