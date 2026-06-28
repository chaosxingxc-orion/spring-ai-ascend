---
level: L1-HLD
TAG:
  - overview
  - architecture-fact
  - module-boundary
  - edge-plane
status: draft
dependency:
  - ../../L0-Top-Level-Design/overview.md
  - ../../L0-Top-Level-Design/views.md
  - ../../L0-Top-Level-Design/boundaries.md
  - ../../L0-Top-Level-Design/constraints.md
  - ../../L0-Top-Level-Design/glossary.md
  - ../agent-runtime/overview.md
  - ../agent-bus/ARCHITECTURE.md
  - ../../../docs/contracts/ingress-envelope.v1.yaml
---

# agent-client L1 架构概览

## 目的

本文档给出 `agent-client` 模块的 L1 高阶心智模型，概述模块目标、受众边界、问题领域和模块边界形态。

本文档是 `agent-client` L1 高阶设计的 overview 初稿。它承接 L0 中对 `agent-client` 的业务接入、客户端集成、本地能力端点、游标/回调和服务流消费边界定义，并参照 `agent-runtime` L1 文档体系的章节粒度展开。本文不展开类级清单、接口签名、配置项、测试矩阵、错误处理表或具体 SDK 使用指南；这些内容进入后续 `logical.md`、`development.md`、`process.md`、`physical.md`、`api-appendix.md` 或 L2 详细设计。

当前 `agent-client` 在代码事实中仍是 edge plane 的 SDK skeleton。本文把已经成立的 L0/L1 契约和后续波次候选能力分开描述，避免把未落地的 SDK、客户端 webhook 异步回调或跨重启游标恢复能力写成 active 实现事实。

## 模块目标

`agent-client` 是面向业务应用、终端应用和集成开发者的 **edge access SDK / client integration boundary**。它位于 C-Side 与平台运行时之间，负责把业务侧意图、本地能力、客户端进度和服务端实时输出消费组织成可治理的接入形态。

当前模块目标包括：

- 作为业务接入侧 SDK 边界，封装 client invocation、本地调用句柄、请求关联信息、租户与 trace 传播、幂等键和客户端侧进度。
- 作为 edge plane 的入口消费者，未来所有 client -> server 的跨平面请求必须通过 `agent-bus` 的 `IngressGateway` / `IngressEnvelope` 契约进入，而不是直接依赖 `agent-runtime`、`agent-core` 或 `agent-middleware` 内部实现。
- 消费 `agent-runtime` 暴露的 Task 查询、取消、恢复和服务流表面，但不拥有服务端 Task lifecycle state，也不写服务端权威生命周期状态。
- 承载客户端侧 SSE/stream 消费、断线续接所需的本地游标、调用引用和客户端侧持久化进度；跨重启、跨设备或全局可靠恢复能力需要由后续设计明确持久化与治理边界。
- 在当前版本不暴露 A2A webhook 或其他公网/内网回调入口；智能体对本地能力的需求应表达为服务端 Task 状态、事件或服务流中的待输入/待执行意图，再由 `agent-client` 主动发起下一轮 client -> server 请求提交观测结果、动作结果、授权引用或错误。
- 显式治理 C-Side local capability。按强化学习语义，本地能力至少分为业务环境观测和业务动作执行两类：观测能力向智能体提供受控环境状态、上下文或检索结果；动作能力对业务环境产生副作用，必须接受更强的授权、审批、幂等和审计约束。
- 作为开发态客户端连接开发态智能体服务运行时，支持下发配置变更、按步调试和产物导出。workflow 类智能体的产物可以是类 DSL 的描述文件；agent-loop 类智能体的产物可以是类 `openclaw.json` 的配置文件。
- 将业务侧凭据、细粒度权限模型和本地敏感数据保持在 C-Side 边界内；平台侧只获得经过授权和契约化的引用或执行结果。

## 受众边界

| 受众 | 主要需求 |
|---|---|
| 模块维护者 | 理解 `agent-client` 的 edge plane 身份、L0 边界、当前 skeleton 状态、依赖红线和后续 L1 文档展开方向。 |
| 业务集成开发者 | 理解如何通过客户端 SDK 提交意图、持有调用引用、消费服务流、恢复调用进度和承载本地能力。 |
| Runtime / Bus 设计者 | 理解 client 侧只能通过 ingress 契约主动请求服务端，不能绕过治理边界写 Task 生命周期，也不在当前版本暴露 webhook 回调入口。 |
| 本地能力提供者 | 理解本地观测能力和动作能力如何作为 client capability 被智能体感知，并如何按不同权限、审批和副作用要求返回受治理结果。 |
| 智能体开发者 | 理解开发态 client 如何连接开发态运行时，下发配置、按步调试 workflow / agent-loop，并导出类 DSL 或类 `openclaw.json` 的智能体产物。 |
| 架构评审者 | 判断 `agent-client` 是否保持不可信 edge caller 定位、是否避免平台编排下沉到客户端、是否符合 L0 机制三线分离。 |
| AI agent / 文档维护者 | 以本文建立模块心智模型，再进入后续 4+1 视图、契约附录和 L2 设计定位事实来源。 |

## 问题领域

`agent-client` 解决的是业务侧如何安全、稳定、可恢复地接入智能体运行时的问题。它不是一个新的服务端 runtime，也不是通用智能体编排层。

1. **业务侧意图与服务端 Task 生命周期容易混淆**

   业务应用需要提交意图、查看进展、取消或继续执行，但服务端 Task lifecycle state 的权威 owner 是 `agent-runtime`。`agent-client` 只能持有 client invocation、cursor 和本地进度映射，不能把本地句柄升级为第二套服务端生命周期状态。

2. **长周期异步任务需要客户端侧可恢复体验**

   Agent 执行常常包含长模型推理、人工审批、外部工具等待或断线重连。客户端必须能保存调用引用、消费 SSE/stream、处理断线和重订阅，并在业务 UI 中呈现进度。但客户端慢消费、断线或重试不得反向阻塞服务端控制面，也不得制造重复副作用。

3. **C-Side 本地能力需要显式治理**

   业务系统的本地工具、本地上下文、本地记忆、检索和审批 UI 往往持有敏感数据或本地凭据。`agent-client` 需要把这些能力表达为可声明、可授权、可审计的 capability，而不是让服务端或 Agent 框架透明调用本地函数。

   本地能力可以按强化学习中的环境交互语义拆成两类。第一类是 **Observation**，用于向智能体提供业务环境状态、用户上下文、检索结果、文件摘要、界面状态或外部系统只读事实；它的治理重点是数据范围、脱敏、租户隔离、可见性和时效性。第二类是 **Action**，用于修改业务环境或触发外部副作用，例如提交审批、更新工单、发送消息、写入业务系统或执行本地命令；它的治理重点是授权、审批、幂等、补偿、审计和失败回滚。

4. **edge 调用方必须被视为不可信**

   即使 `agent-client` 是平台提供的 SDK，运行时也必须把来自 edge 的请求视为外部输入。租户、trace、幂等、deadline、请求类型和 payload 必须通过 ingress envelope 显式携带并由平台侧重新校验；客户端传入的 header 或本地声明不能直接成为平台信任事实。

5. **client -> server 路由需要避免直连 compute_control**

   L0/L1 已经把跨平面入口治理放在 `agent-bus` 侧。未来 `agent-client` SDK 不能为了实现便利直接依赖 `agent-runtime` 生产代码或绕过 `IngressGateway` 访问内部 Task 服务。当前 `ingress-envelope.v1.yaml` 仍是 `design_only`，但它已经定义了后续 SDK 落地时必须遵守的入口形态。

6. **当前版本不实现客户端暴露式 webhook 异步回调**

   即使 `agent-client` 未来集成 A2A SDK 中的 A2A client，当前整体架构也不要求 client 暴露 A2A webhook 或其他服务端可回调入口。客户端回调入口会带来额外安全面、网络可达性、NAT/防火墙穿透、租户边界和部署复杂度风险。当前版本应把智能体对本地能力的调用折叠为服务端 Task 的等待输入/待执行意图，由 client 消费状态或服务流后，主动发起下一轮 client -> server 请求提交 capability 结果。

7. **开发态 client 需要支持智能体调试与产物生成**

   除生产接入外，`agent-client` 还承担开发态客户端角色。当它连接开发态智能体服务运行时时，可以下发配置变更、调整智能体逻辑、单步或分段调试执行过程，并把调试结果固化为智能体定义产物。workflow 类智能体的产物偏向类 DSL 的图/流程描述；agent-loop 类智能体的产物偏向类 `openclaw.json` 的 loop、tool、prompt 和策略配置。

8. **服务流、控制信号和数据载荷需要分离**

   实时输出可以通过 SSE 或等价 stream 被客户端消费；控制请求通过 ingress envelope 和多轮请求表达；大对象或敏感数据通过引用、授权和外部数据路径处理。`agent-client` 不应把 token stream、控制事件和大载荷数据揉成同一个不透明通道。

## 模块边界形态

`agent-client` 是 L1 逻辑模块，也是当前代码仓中的 edge plane Maven module skeleton。它可以在未来表现为 Java SDK、Spring Boot starter、HTTP/SSE client、本地能力 endpoint adapter、浏览器/桌面/服务端应用集成层等多种交付形态，但这些形态不改变 L0 逻辑边界。

| 边界项 | agent-client 负责 | agent-client 不负责 | 事实下沉位置 |
|---|---|---|---|
| Client invocation | 创建并持有客户端调用引用、本地句柄、request/correlation 信息、Task 映射引用和客户端侧进度。 | 不创建第二套服务端生命周期状态，不绕过 runtime 查询或修改 Task owner。 | 后续 `logical.md`, `process.md` |
| C2S ingress | 构造 client -> server 请求的入口 envelope，传播 `requestId`、`tenantId`、`idempotencyKey`、`requestType`、`payload`、`traceId` 等字段。 | 不直接依赖 compute_control 内部模块，不直接调用 `agent-runtime` / `agent-core` / `agent-middleware` 生产代码。 | `ingress-envelope.v1.yaml`, `agent-bus` L1 |
| 服务流消费 | 消费 runtime 服务流表面，维护本地 SSE/stream cursor、断线重连位置和 UI/业务侧进度投影。 | 不把慢消费变成服务端控制面背压来源，不把 token/content stream 当作控制通道。 | 后续 `process.md`, `physical.md` |
| Task 查询/取消/恢复 | 通过受治理入口发起查询、取消、恢复或继续消息，保持 tenant、trace、幂等和 deadline 上下文。 | 不直接写 Task lifecycle state，不直接读取 Agent 框架内部 checkpoint。 | 后续 `api-appendix.md`, `process.md` |
| 本地观测能力 | 声明并执行读取业务环境的 capability，例如本地上下文、检索、文件摘要、界面状态和只读业务事实，并把观测结果作为下一轮请求输入返回服务端。 | 不把本地敏感正文无治理地传出 C-Side，不把观测结果伪装成服务端权威状态。 | 后续 `logical.md`, `process.md` |
| 本地动作能力 | 声明并执行会产生业务副作用的 capability，例如审批、写入业务系统、发送消息、提交工单或执行本地命令，并携带授权、审批、幂等和审计引用返回结果。 | 不让服务端或 Agent 框架透明调用本地函数，不绕过业务权限、人工确认和副作用保护。 | 后续 `logical.md`, `process.md`, L2 设计 |
| 本地能力多轮交互 | 从 Task 状态、事件或服务流中识别待观测/待执行意图，client 在本地完成治理后主动发起下一轮 client -> server 请求。 | 当前版本不暴露 A2A webhook 或 S2C 异步回调入口，不要求 server 直接访问 client 网络端点。 | 后续 `process.md`, `api-appendix.md` |
| 开发态客户端 | 连接开发态 runtime，下发配置变更、按步调试、观察中间状态，并导出 workflow 类 DSL 或 agent-loop 类 `openclaw.json` 配置产物。 | 不把开发态调试通道作为生产控制面，不绕过发布、版本和权限治理直接修改生产智能体。 | 后续 `scenarios.md`, `api-appendix.md`, L2 设计 |
| 客户端状态持久化 | 保存 client invocation、cursor、retry budget、capability correlation、本地授权引用和业务 UI 进度。 | 不拥有平台审计最终写入，不持久化服务端 Task lifecycle，不替代 runtime checkpoint。 | 后续 `physical.md`, L2 设计 |
| SDK 依赖边界 | 保持 edge SDK 的轻量、可嵌入和独立演进；只消费允许的跨平面契约。 | 不承载通用智能体编排、模型/记忆/工具全局治理、跨实例 A2A 控制或平台级审计写入。 | 后续 `development.md`, `module-metadata.yaml` |

跨模块依赖方向保持为：`agent-client` 可以消费 `agent-bus` 暴露的 ingress 契约；`agent-runtime` 拥有服务端 Task 生命周期并暴露查询、恢复、取消和服务流表面；`agent-bus` 治理跨平面入口；`agent-core` 与 `agent-middleware` 的内部执行和能力治理不向 `agent-client` 泄漏实现依赖。当前版本中，智能体对本地能力的需求通过 Task 状态、事件或服务流显式表达，并由 `agent-client` 主动发起多轮请求返回结果，不通过客户端暴露 webhook 入口完成。

## 当前状态与后续 L1 展开

当前 `agent-client` 的 active 事实主要来自 L0 边界、模块元数据、DFX 声明和 bus 侧契约；完整 SDK 行为仍处于后续波次设计范围。本文建议后续按与 `agent-runtime` 相同的 L1 文档地图补齐：

| 文件 | 建议作用 |
|---|---|
| `README.md` | L1 入口、文档地图、阅读路径和当前成熟度说明。 |
| `scenarios.md` | 场景视图：创建调用、查询/取消、SSE 消费、断线重连、本地观测/动作多轮请求、审批恢复、开发态调试。 |
| `logical.md` | 逻辑视图：client invocation、cursor、capability endpoint、observation/action、debug session、状态归属。 |
| `development.md` | 开发视图：SDK 包结构、依赖边界、禁止依赖、自动装配和测试守卫。 |
| `process.md` | 进程视图：C2S ingress、stream 消费、retry/backoff、本地能力多轮请求、取消/恢复、按步调试流程。 |
| `physical.md` | 物理视图：浏览器、桌面、服务端业务应用、本地能力 host、持久化和网络边界。 |
| `api-appendix.md` | API 附录：面向业务侧的 SDK facade、多轮请求语义、开发态调试语义、错误和重试语义。 |
| `spi-appendix.md` | SPI 附录：本地观测 provider、本地动作 executor、cursor store、credential resolver、debug artifact exporter 等扩展点。 |
| `glossary.md` | 模块术语表：client invocation、cursor、local capability、observation、action、debug session、authorized reference 等。 |

在上述文档补齐前，`overview.md` 只作为模块定位与边界初稿，不作为完整 SDK API 或运行时行为承诺。
