---
level: L1-HLD
TAG:
  - scenarios
  - technical-scenario
  - architecture-fact
  - edge-plane
status: draft
dependency:
  - README.md
  - overview.md
  - logical.md
  - development.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
  - ../../L0-Top-Level-Design/views.md
  - ../../../docs/contracts/ingress-envelope.v1.yaml
---

# agent-client L1 架构场景视图

## 目的

本文档从 `agent-client` 的 L1 模块定位反推技术场景，用于把 L1 架构概览、逻辑视图、开发视图、进程视图和物理视图连接到可验证的客户端运行路径。

本文档不承载业务场景。业务场景应从需求导入，并按版本实现范围维护在根目录 `version-scope/` 下的版本范围文档中。本文只描述 `agent-client` 作为 edge plane 接入边界、服务流消费者、本地能力治理者和开发态客户端时必须成立的技术路径。

## 场景边界

`agent-client` 的技术场景围绕业务侧接入、client invocation、服务流消费、本地能力多轮交互和开发态调试展开。它不拥有服务端 Task 生命周期，不承载通用智能体编排，不直接依赖 `agent-runtime`、`agent-core` 或 `agent-middleware` 生产代码。

当前版本场景遵循以下边界：

- client -> server 的跨平面请求必须通过 `agent-bus` 的 ingress 契约进入，不能直连 compute_control 内部实现。
- 当前版本不实现客户端暴露式 A2A webhook 或 S2C 异步回调入口；智能体对本地能力的需求通过 Task 状态、事件或服务流显式表达，再由 client 主动发起下一轮请求返回结果。
- 本地能力按环境交互语义分为 Observation 和 Action。Observation 提供受控环境事实；Action 产生业务副作用，需要更强的授权、审批、幂等和审计。
- 开发态 client 可以连接开发态 runtime，下发配置、按步调试，并导出 workflow 类 DSL 或 agent-loop 类 `openclaw.json` 配置产物；该通道不等同于生产控制面。
- 当前 `agent-client` 仍是 SDK skeleton。本文中的场景是 L1 设计目标和验证候选，不表示对应 SDK API 已经全部实现。

## TS-01 客户端创建并绑定智能体调用

### 场景目标

业务应用通过 `agent-client` 提交智能体执行意图。client 构造入口 envelope，经 `agent-bus` ingress 进入服务端运行时，并在本地创建 client invocation 与服务端 Task 的映射引用。

### 参与组件

| 组件 | 角色 |
|---|---|
| Business application | 发起业务意图，提供用户输入、业务上下文和本地授权引用。 |
| Agent Client SDK facade | 封装创建调用、租户、trace、幂等键和 deadline。 |
| Client Invocation Store | 保存本地调用句柄、Task 映射引用和客户端侧进度。 |
| IngressEnvelope | 表达 client -> server 请求的跨平面入口 envelope。 |
| IngressGateway | `agent-bus` 暴露的受治理入口。 |
| agent-runtime Task surface | 服务端创建 Task 并拥有生命周期状态。 |

### 基本路径

1. 业务应用调用 `agent-client` 创建智能体调用。
2. `agent-client` 生成或接收 `requestId`、`tenantId`、`traceId`、`idempotencyKey` 和 deadline。
3. `agent-client` 构造 `IngressEnvelope`，将业务意图和必要引用放入 payload。
4. `IngressGateway` 校验 envelope 并路由到服务端运行时入口。
5. `agent-runtime` 创建或绑定服务端 Task，并返回受治理响应。
6. `agent-client` 在本地保存 client invocation、Task 引用、trace 关联和初始进度。

### 验证关注点

- client invocation 不是第二套服务端 Task 生命周期状态。
- `agent-client` 不直接依赖或调用 `agent-runtime` 生产代码。
- 入口 envelope 显式携带 tenant、trace、requestId 和幂等键。
- 失败、拒绝或 deferred 响应不会在客户端制造未绑定的孤儿调用。

## TS-02 服务流消费与客户端游标维护

### 场景目标

`agent-client` 消费服务端实时输出表面，把 token/content stream、Task 事件和最终状态投影为业务 UI 或业务回调所需的客户端进度，同时维护本地 cursor 以支持断线重连。

### 参与组件

| 组件 | 角色 |
|---|---|
| Agent Client Stream Consumer | 建立并消费 SSE 或等价服务流。 |
| Client Cursor Store | 保存最近消费位置、Task 引用和本地恢复点。 |
| Business UI / caller | 呈现流式输出、状态变化和等待输入提示。 |
| agent-runtime stream surface | 暴露服务端实时输出和 Task 事件。 |
| IngressGateway | 承接必要的重订阅或查询请求。 |

### 基本路径

1. `agent-client` 根据 client invocation 中的 Task 引用打开服务流订阅。
2. 服务端持续输出内容片段、状态事件或等待输入提示。
3. `agent-client` 按事件顺序更新本地 cursor 和业务 UI 进度。
4. 连接断开后，`agent-client` 使用本地 cursor 或 Task 引用发起重订阅/查询。
5. 服务端从可用位置继续推送或返回当前 Task 快照。

### 验证关注点

- 慢消费或断线不反向阻塞服务端控制面。
- cursor 是客户端消费进度，不是服务端 Task 生命周期状态。
- token/content stream 不被用作控制命令通道。
- 当前 active 形态不承诺跨进程重启后的服务端事件全量重放。

## TS-03 查询、取消与继续执行

### 场景目标

业务应用围绕已创建的 client invocation 发起查询、取消或继续执行请求。`agent-client` 通过受治理入口把这些请求绑定回服务端 Task 语义，但不直接改写 Task 生命周期状态。

### 参与组件

| 组件 | 角色 |
|---|---|
| Agent Client SDK facade | 提供查询、取消、继续执行的客户端方法。 |
| Client Invocation Store | 读取 Task 引用、trace、tenant 和幂等上下文。 |
| IngressEnvelope | 表达 `RUN_GET`、`RUN_CANCEL`、`RUN_RESUME` 等请求类型。 |
| IngressGateway | 校验并路由 client 主动请求。 |
| agent-runtime Task surface | 读取、取消或推进服务端 Task。 |

### 基本路径

1. 业务应用使用 client invocation 发起查询、取消或继续执行。
2. `agent-client` 从本地调用记录读取 Task 引用和上下文。
3. `agent-client` 构造对应 request type 的入口 envelope。
4. `IngressGateway` 校验 tenant、trace、幂等和准入规则。
5. `agent-runtime` 读取或推进 Task 状态，并返回响应或后续流事件。
6. `agent-client` 更新本地进度和业务可见状态。

### 验证关注点

- 查询、取消和继续执行都以服务端 Task owner 为权威。
- Cancel 或 resume 不绕过 ingress 和 runtime Task surface。
- 重复请求通过幂等键和本地 invocation 状态约束副作用。
- 客户端本地状态丢失时，必须通过受治理查询恢复可见状态。

## TS-04 本地 Observation 能力多轮请求

### 场景目标

当智能体需要业务环境观测时，服务端通过 Task 状态、事件或服务流表达待观测意图。`agent-client` 在本地执行只读观测能力，并主动发起下一轮请求提交观测结果或授权引用。

### 参与组件

| 组件 | 角色 |
|---|---|
| agent-runtime Task / stream surface | 暴露待观测意图、参数和提示。 |
| Agent Client Capability Router | 将待观测意图路由到本地 Observation provider。 |
| Observation Provider | 执行本地上下文读取、检索、文件摘要或界面状态采集。 |
| Client Policy Guard | 检查数据范围、脱敏、租户隔离和可见性规则。 |
| IngressGateway | 接收 client 主动提交的观测结果。 |

### 基本路径

1. 智能体执行过程中需要读取 C-Side 业务环境事实。
2. 服务端 Task 进入等待输入或待观测状态，并通过查询/服务流暴露 capability intent。
3. `agent-client` 识别 intent，并路由到本地 Observation provider。
4. Client Policy Guard 校验数据范围、脱敏策略、租户和可见性。
5. Observation provider 返回受控观测结果或数据引用。
6. `agent-client` 通过下一轮 client -> server 请求提交观测结果。
7. 服务端 runtime 将结果绑定回原 Task 语义并继续执行。

### 验证关注点

- Observation 是只读环境事实，不产生业务副作用。
- 本地敏感正文不无治理地离开 C-Side。
- 服务端不直接访问 client 网络端点或本地资源。
- 观测结果不能伪装成服务端权威生命周期状态。

## TS-05 本地 Action 能力多轮请求

### 场景目标

当智能体需要对业务环境执行动作时，`agent-client` 将待执行意图交给本地 Action executor，并在授权、审批、幂等和审计约束满足后，主动提交动作结果或失败原因。

### 参与组件

| 组件 | 角色 |
|---|---|
| agent-runtime Task / stream surface | 暴露待执行动作、参数和风险提示。 |
| Agent Client Capability Router | 将待执行意图路由到本地 Action executor。 |
| Action Executor | 执行本地工具、业务系统写入、消息发送或命令操作。 |
| Approval UI | 在需要人工确认时收集审批决定。 |
| Client Policy Guard | 校验权限、审批、幂等、副作用保护和审计要求。 |
| Client Audit / Evidence Sink | 记录本地动作证据、授权引用和结果摘要。 |
| IngressGateway | 接收 client 主动提交的动作结果。 |

### 基本路径

1. 智能体执行过程中产生需要 C-Side 执行的动作意图。
2. 服务端 Task 暴露待执行状态、动作参数和必要的用户提示。
3. `agent-client` 路由到对应 Action executor。
4. Client Policy Guard 检查本地权限、风险等级、幂等键和审批要求。
5. 如需人工确认，Approval UI 收集同意、拒绝或修改后的参数。
6. Action executor 执行业务动作，并产出动作结果、授权引用、审计引用或错误。
7. `agent-client` 主动发起下一轮请求，把结果提交给服务端 Task。

### 验证关注点

- Action 与 Observation 分开治理，副作用动作必须有授权和幂等保护。
- 人工审批结果属于 C-Side 事实，返回服务端时只传递必要结果或引用。
- 动作失败、超时或拒绝必须成为 Task 可理解的输入，而不是客户端静默吞掉。
- 当前版本不要求服务端通过 webhook 直接调用本地 Action endpoint。

## TS-06 本地能力等待与恢复的无 webhook 交互

### 场景目标

在需要本地能力参与的长等待场景中，`agent-client` 不暴露 A2A webhook 或 S2C 异步回调入口，而是通过轮询、查询、服务流或重订阅发现待处理意图，并通过主动多轮请求恢复服务端 Task。

### 参与组件

| 组件 | 角色 |
|---|---|
| agent-runtime Task state | 表达等待输入、待观测或待动作状态。 |
| Agent Client Poll / Stream Loop | 通过查询、服务流或重订阅发现待处理意图。 |
| Client Capability Router | 执行本地 Observation 或 Action。 |
| Client Invocation Store | 保存等待意图、capability correlation 和恢复上下文。 |
| IngressGateway | 接收 client 主动恢复请求。 |

### 基本路径

1. 服务端 Task 因本地能力需求进入等待状态。
2. `agent-client` 通过服务流、查询或重订阅看到待处理意图。
3. `agent-client` 将意图保存为 capability correlation，并执行本地能力。
4. 本地能力完成、拒绝或超时后，`agent-client` 构造下一轮恢复请求。
5. `IngressGateway` 校验并转发恢复请求。
6. `agent-runtime` 将结果绑定回原 Task，并继续执行或进入终态。

### 验证关注点

- client 不开放服务端可直接访问的 webhook 回调入口。
- capability correlation 只是客户端侧恢复上下文，不是服务端生命周期状态。
- 等待状态、恢复请求和结果提交都带有 tenant、trace 和幂等上下文。
- 网络不可达、客户端离线或本地能力超时不会破坏服务端 Task owner 边界。

## TS-07 开发态配置下发与按步调试

### 场景目标

智能体开发者使用 `agent-client` 连接开发态 runtime，下发配置变更、调整智能体逻辑、按步执行或分段调试，以验证 workflow 或 agent-loop 的执行行为。

### 参与组件

| 组件 | 角色 |
|---|---|
| Developer UI / IDE integration | 提供开发者操作入口、配置编辑和调试控制。 |
| Agent Client Debug Session | 保存开发态连接、配置版本、断点和执行步进上下文。 |
| Debug Configuration Draft | 表达待下发的 workflow / agent-loop 配置变更。 |
| IngressGateway | 承接开发态调试控制请求。 |
| Development Runtime | 开发态智能体服务运行时，执行配置草稿和调试命令。 |
| Stream Consumer | 接收每一步执行输出、中间状态和错误。 |

### 基本路径

1. 开发者通过 UI 或 IDE 创建 debug session。
2. `agent-client` 连接开发态 runtime，并绑定租户、trace 和开发态身份。
3. 开发者下发配置草稿或局部逻辑变更。
4. `agent-client` 通过 ingress 提交配置变更和调试命令。
5. 开发态 runtime 按步执行目标 workflow 或 agent-loop。
6. `agent-client` 消费每一步输出、中间状态、等待输入和错误信息。
7. 开发者根据反馈继续修改配置、单步推进、回退或结束调试。

### 验证关注点

- 开发态调试通道不等同于生产控制面。
- 配置变更只作用于开发态 session 或草稿，不直接修改生产智能体。
- 单步调试仍通过受治理入口进入 runtime，不绕过 Task owner。
- 中间状态用于调试观察，不成为服务端持久生命周期状态的第二来源。

## TS-08 智能体定义产物导出

### 场景目标

开发态调试完成后，`agent-client` 将验证过的智能体逻辑导出为可审阅、可版本化、可发布的定义产物。workflow 类智能体导出类 DSL 描述文件；agent-loop 类智能体导出类 `openclaw.json` 配置文件。

### 参与组件

| 组件 | 角色 |
|---|---|
| Agent Client Debug Session | 保存调试过程中确认的配置版本和执行证据。 |
| Debug Artifact Exporter | 将调试态配置转换为目标产物格式。 |
| Workflow DSL Artifact | 描述 workflow 图、节点、边、条件和参数。 |
| Agent-loop Config Artifact | 描述 loop、tool、prompt、策略和终止条件，例如类 `openclaw.json`。 |
| Version / Review Surface | 承接产物审阅、版本管理和后续发布流程。 |

### 基本路径

1. 开发者结束 debug session，并选择导出智能体定义。
2. `agent-client` 汇总当前配置草稿、调试参数和必要执行证据。
3. 对 workflow 类智能体，Exporter 生成类 DSL 的图/流程描述。
4. 对 agent-loop 类智能体，Exporter 生成类 `openclaw.json` 的 loop 配置。
5. `agent-client` 将产物交给版本、审阅或发布表面。
6. 后续发布流程决定该产物是否进入生产智能体定义。

### 验证关注点

- 导出产物是开发态结果，不自动等同于生产发布。
- workflow 和 agent-loop 产物格式可以不同，但都必须保留可审阅的配置语义。
- 产物中不得包含未经治理的本地敏感数据或临时凭据。
- 调试证据与导出产物可以关联，但不替代平台发布审批和版本治理。
