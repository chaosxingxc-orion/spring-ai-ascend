---
version: 0715
module: agent-bus
feature_type: functional
feature_id: FEAT-011
status: active
---

# agent-gateway 组件客户端调用路由转发特性文档

## 1. 特性定位

FEAT-011 定义 `agent-gateway` 当前版本作为客户端统一 A2A 调用路由转发入口的事实：Gateway 必须接收 `agent-client` 封装后的 A2A 兼容 C/S 请求，完成认证鉴权、租户识别、请求标准化、路由决策和 HTTP/SSE 桥接，再把请求投递到目标 `agent-runtime` 标准入口。

本特性解决的问题是：业务应用不应感知内部 runtime 物理 endpoint，也不应自行选择未治理 URL。Gateway 作为 agent-bus 中的入口治理平面，必须根据 agentId、tenant、版本、健康状态和路由策略生成 routeHandle，并将 A2A 调用、流式观察、查询、取消和订阅请求按路由转发回目标 Task owner。

在总体架构中，本特性位于 agent-client 与 agent-runtime 之间。Gateway 不执行 Agent、不调用模型、不读写 runtime TaskStore；runtime 仍是 Task owner。Gateway 只保存路由、幂等、审计、恢复关联和流桥接投影。若部署形态中存在受控服务 facade，该 facade 只能作为部署侧适配或反向代理，不得形成绕过 `FEAT-001` 标准 Agent 服务入口的第二套执行协议。

本特性面向以下角色：

- Gateway 开发者：实现入口治理、路由决策、A2A 转发和 SSE 桥接。
- agent-client 接入方：通过统一 Gateway facade 调用 Agent 服务。
- R&D Center / 注册发现集成方：提供 Agent、版本、健康和 routeHandle。
- agent-runtime 开发者：承接 Gateway 转发并对齐标准 Agent 服务入口响应。
- 运行运维方：配置灰度、回退、流控、审计和故障处理策略。
- 测试与验收团队：按路由、转发、流桥接、查询、取消和错误语义验证。

本特性只定义客户端调用的直接路由转发路径。事件化调用转发由 FEAT-013 承接；runtime 标准 A2A 服务入口由 FEAT-001 承接；Agent 执行、工具、记忆和 Task 生命周期不属于 Gateway 职责。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 客户端入口治理 | MUST | Gateway 必须处理认证鉴权、租户解析、基础参数校验、幂等和审计。 |
| agentId 路由 | MUST | Gateway 必须支持按明确 agentId 查找 routeHandle 并转发。 |
| routeHandle 抽象 | MUST | routeHandle 必须是受治理路由引用，不得向客户端暴露物理 endpoint。 |
| 统一 A2A 封装转发 | MUST | Gateway 必须以统一 A2A 兼容请求表面承接 `SendMessage`、`SendStreamingMessage`、`GetTask`、`CancelTask` 和 `SubscribeToTask` 等语义，并按 `FEAT-001` 的标准 Agent 服务入口语义转发；不得把 invoke 与 stream 作为互相独立的客户端入口协议。 |
| SSE 桥接 | MUST | Gateway 必须支持对目标服务 A2A SSE 的桥接，但不得生成 Agent token 内容，不得缓存 token 流，也不得把流式观察变成独立于 A2A Task 的第二套接口。 |
| 查询转发 | MUST | 查询长任务由 Client 与 runtime 配合完成：Client 必须携带服务端 `taskId` 发起查询，Gateway 只定位 Task owner 并转发到 runtime 标准查询语义；`clientInvocationId` 不得替代 `taskId`。 |
| 取消转发 | MUST | 取消任务由 Client 与 runtime 配合完成：Client 必须携带服务端 `taskId` 发起取消，Gateway 只把取消请求映射到 runtime 标准取消语义，Task 状态由 runtime 决定。 |
| UNKNOWN 恢复 | MUST | 当 Gateway 无法确认 runtime 是否已创建 Task 且 client 尚未获得 `taskId` 时，必须允许 client 使用同一 `clientInvocationId` 和同一 `idempotencyKey` 重试原始创建类调用以恢复关联；当前版本不新增私有 `ResolveInvocation` 查询接口。 |
| 灰度与回退 | SHOULD | Gateway 应支持按租户、版本、比例、健康状态执行灰度和故障回退。 |
| 状态边界 | MUST | Gateway 不拥有服务端 Task，不写 runtime TaskStore。 |
| 拓扑隐藏 | MUST | 客户端不得感知内部服务 endpoint、实例、网络拓扑或 runtime 私有接口。 |
| Agent 执行 | OUT | Gateway 不执行推理、工具调用、记忆、知识库或业务 Agent 逻辑。 |
| 私有执行口 | OUT | Gateway 不为 agent-bus 或 client 创建绕过标准 runtime 入口的私有 Agent 执行协议。 |

## 3. 外部接口与入口要求

本节只定义 Gateway 面向 client 的接口事实和输入输出约束。Gateway 转发所依赖的 runtime 标准 Agent 服务入口由 `FEAT-001` 定义，本文不重复定义下游 runtime 接口。

| Gateway 接口面 | 调用方向 | 输入要求 | 输出要求 | 约束 |
|---|---|---|---|---|
| Gateway A2A facade | client -> Gateway | A2A 兼容请求；创建类调用必须携带 `agentId`，应携带 `idempotencyKey`，可携带 `clientInvocationId`。 | 返回 A2A 兼容响应、错误或 SSE 流。 | 不向 client 暴露 runtime endpoint、routeHandle 解析信息、内部实例、stream endpoint 或私有执行口。 |
| 阻塞调用入口 | client -> Gateway | `SendMessage` 语义；Gateway 基于认证主体和策略解析 tenant，并归一 trace、correlation、幂等键和请求上下文。 | 返回已完成响应、已接受 Task 引用/快照、明确失败，或未确认创建时的 `UNKNOWN`。 | Gateway 已获得 runtime `taskId` 后不得再把该调用报告为 `UNKNOWN`。 |
| 流式调用入口 | client -> Gateway | `SendStreamingMessage` 语义；client 通过同一 Gateway A2A facade 建立 SSE 连接。 | Gateway 桥接标准服务流，直到 Task 终态、中断、下游流错误或 client 连接关闭。 | Gateway 不生成 token、不缓存 token 流、不定义第二套 stream 协议。 |
| Task 查询入口 | client -> Gateway | `GetTask` 语义；必须基于 runtime 生成的 `taskId`。 | 返回 A2A 兼容 Task 快照或确定错误。 | `clientInvocationId` 不得替代 `taskId`。 |
| Task 取消入口 | client -> Gateway | `CancelTask` 语义；必须基于 runtime 生成的 `taskId`。 | 返回取消请求结果、Task 快照或确定错误。 | Gateway 不承诺强制中断底层模型调用。 |
| Task 重订阅入口 | client -> Gateway | `SubscribeToTask` 语义；必须基于 runtime 生成的 `taskId`。 | Gateway 重新桥接该 Task 的标准 SSE 流。 | 找不到 Task 时不得隐式创建新 Task。 |
| UNKNOWN 恢复入口 | client -> Gateway | client 使用同一 `clientInvocationId`、同一 `idempotencyKey` 重试原始创建类调用。 | 若原 Task 已创建，返回同一 `taskId` 或当前 Task 快照；若未创建，则按新投递或明确拒绝处理。 | 当前版本不新增 `ResolveInvocation` 之类 Gateway 私有查询接口。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 按 agentId 调用 Agent | 目标 Agent 已注册并可访问 | client 提交 agentId 调用 | Gateway 校验权限，选择 routeHandle，并按 `FEAT-001` 标准 Agent 服务入口语义转发到目标 runtime。 |
| 流式调用桥接 | 目标支持 A2A SSE / streaming，client 连接仍保持 | client 通过同一 A2A facade 发起 `SendStreamingMessage` | Gateway 转发调用并在目标返回可订阅流后桥接 A2A SSE；实时 token 内容由目标服务产生，Gateway 不生成、不缓存 token 流。 |
| 查询长任务 | client 已从同步退化、流式接受或重试恢复中获得服务端 `taskId` | client 通过 Gateway facade 发起 `GetTask(taskId)`；runtime 作为 Task owner 提供任务快照 | Gateway 校验租户与调用关联，定位 Task owner，并把查询转发到 runtime；runtime 返回 A2A 兼容 Task 快照。 |
| 取消任务 | client 已获得服务端 `taskId`，且业务允许请求取消 | client 通过 Gateway facade 发起 `CancelTask(taskId)`；runtime 作为 Task owner 判断是否可取消 | Gateway 校验租户与调用关联，定位 Task owner，并把取消请求转发到 runtime；runtime 按自身 Task 生命周期决定取消结果并返回。 |
| UNKNOWN 后恢复 | Gateway 无法确认 runtime 是否已创建 Task，且 client 尚未获得服务端 `taskId` | client 使用同一 `clientInvocationId` 和同一 `idempotencyKey` 重试原始创建类调用 | Gateway 尽力恢复或复用原投递结果；若 runtime 已创建 Task，则代理返回同一 `taskId` 或当前 Task 快照；若未创建，则按新投递或明确拒绝处理。 |
| 路由失败 | 无权限、无 routeHandle、目标不可用或下游返回确定错误 | client 发起调用、查询、取消或流式请求 | Gateway 按失败类型给出相应返回或透传下游错误即可；Gateway 只是路由转发模块，不伪造 Task，也不作为失败结果的最终控制模块。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.0 入口治理语义

- Gateway 是客户端进入平台的治理入口，必须先完成认证鉴权、租户解析、幂等和基础校验。
- Gateway 必须基于认证主体和网关策略解析 tenant，并清洗或覆盖 client 请求中与身份冲突的 tenant / user 声明。
- 租户身份真实性由前置认证和 Gateway 策略保障，runtime 只消费 Gateway 转发后的可信上下文。
- Gateway 可以记录审计和 route trace，但不改变 Agent 业务语义。

#### 5.1.1 路由决策语义

- routeHandle 是受治理路由引用，不是物理 URL。
- Gateway 按 agentId、tenant、版本、健康状态和路由策略选择目标，并记录 routeHandle、选择原因、策略版本和审计信息。
- 当前版本不把亲和调度作为特性要求；实现可使用 route trace、短期定位索引或注册发现恢复策略定位 Task owner，但不得把这些定位信息升级为 Gateway TaskStore。

#### 5.1.2 转发与桥接语义

- Gateway 转发必须遵守 `FEAT-001` 定义的 runtime 标准 Agent 服务入口语义；受控部署 facade 只能作为适配或代理，不得形成私有执行协议。
- 阻塞调用、流式调用、查询、取消和重订阅都必须是同一 A2A 兼容 facade 下的语义分支，不得拆成互相独立、状态可能漂移的客户端入口协议。
- SSE bridge 只能在 client 连接存活期间桥接服务端流，不生成 Agent token、不缓存 token 流、不修改 Task 终态；client 断开后 Gateway 应释放桥接连接，后续恢复通过 `SubscribeToTask(taskId)` 或 UNKNOWN 恢复语义完成。
- 查询、取消和重订阅必须由 client 基于服务端 `taskId` 发起，并由 runtime 按 Task 生命周期处理；Gateway facade 只承接入口校验、定位和转发。
- `clientInvocationId` 是 client 产生并发送给 Gateway 的弱关联句柄，只在 `UNKNOWN` 且 client 尚未获得 `taskId` 时用于恢复关联；一旦 Gateway 已向 client 返回 runtime `taskId`，后续 Task 操作必须直接使用 `taskId`。

#### 5.1.3 错误、状态与可观测结果

| 场景 | 事实要求 |
|---|---|
| 认证失败 | 返回认证/授权错误，不进入路由。 |
| 无可用目标 | 返回 route_not_found 或 service_unavailable。 |
| 未确认创建 | 若无法确认 runtime 是否已创建 Task，返回 timeout、UNKNOWN 或等价未知状态，允许 client 用同一 `clientInvocationId` 和同一 `idempotencyKey` 重试原始调用恢复。 |
| 已创建但未完成 | 若 Gateway 已获得 runtime `taskId` 但最终结果未在阻塞窗口内返回，必须返回已接受 Task 引用、当前 Task 快照或等价 A2A 表面，不得报告为 `UNKNOWN`。 |
| 确定失败 | 若目标明确拒绝、执行失败或返回确定错误，Gateway 应透传或映射为可编程确定错误，不伪造 Task 成功或未知状态。 |
| 目标流中断 | Gateway 暴露流错误；若 client 已获得 `taskId`，允许 client 查询或重订阅 Task；若尚未获得 `taskId`，按 UNKNOWN 恢复语义处理。 |
| runtime 不支持取消或 Task 不可取消 | 透传 capability_not_supported、task_not_cancelable 或等价错误，不伪造 canceled。 |
| routeHandle 失效 | 重新查询注册发现或返回明确错误。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| Agent 执行 | Gateway 不执行模型、工具、记忆、知识库或业务 Agent。 |
| Task 权威状态 | Gateway 不写 TaskStore，不决定 Task 终态。 |
| 物理拓扑暴露 | 不向 client 暴露内部 endpoint、实例或 runtime 私有地址。 |
| Event Bus 事件转发 | 直接路由转发不等同于事件化调用转发；gateway 与 event-bus 协作语义由 FEAT-013 定义。 |
| 私有执行协议 | 不新增绕过 runtime A2A / Task 语义的私有 Agent 执行口。 |
| 强制取消 | Gateway cancel 不承诺强制中断目标模型调用。 |
| 私有恢复查询 | 当前版本不新增 `ResolveInvocation` 之类以 `clientInvocationId` 查询 runtime Task 的私有接口；UNKNOWN 后通过同一幂等创建调用恢复。 |
| 后台流缓存 | Gateway 不在 client 断开后长期消费、缓存或重放 token 流。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把本特性作为客户端调用路由转发事实来源，Gateway 只能承担入口治理、路由、转发、桥接和审计职责。
- Gateway facade 与 runtime 标准 Agent 服务入口之间必须对齐 tenant、taskId、streamRef、幂等键和错误码语义；routeHandle 只能作为 Gateway 内部受治理路由引用，不得暴露给 client；clientInvocationId 只能作为 UNKNOWN 恢复关联句柄，不得替代 taskId。
- 测试必须覆盖 agentId 路由、认证鉴权、租户隔离与清洗、SSE bridge、client 断开后释放桥接、基于 taskId 的查询转发、基于 taskId 的取消转发、UNKNOWN 后同一 clientInvocationId + idempotencyKey 恢复、已创建 Task 后阻塞超时不得返回 UNKNOWN、灰度回退、routeHandle 隐藏和转发超时。
- 开发指南不得要求业务应用直接配置内部 runtime endpoint。
- 任何对 Gateway 执行 Agent、持有 TaskStore、暴露内部拓扑或私有执行协议的新增承诺，都必须先更新本特性或新增 version-scope 特性。
- 本特性术语必须保持稳定：Gateway、A2A facade、routeHandle、SSE bridge、Task owner、clientInvocationId。

## 7. 关联文档

- `agent-sdk/Docs/agent-gateway组件客户端调用路由转发特性设计.md`
- `Docs/FEAT_Design/FEAT-006-agent-client-standard-agent-service-invocation.md`
- `JAVA local working/version-scope/FEAT-013-client-invocation-event-forwarding.md`
- `Docs/FEAT_Design/FEAT-001-standardized-agent-service-entrypoint.md`
