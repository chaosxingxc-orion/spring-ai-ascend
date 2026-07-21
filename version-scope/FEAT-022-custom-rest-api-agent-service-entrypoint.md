---
scope: v0730
module: agent-runtime
feature_type: functional
feature_id: FEAT-022
status: active
updated: 2026-07-21
---

# 自定义 REST API 服务入口 - 当前版本事实要求

## 1. 特性定位

FEAT-022 定义 `agent-runtime` 在标准化 Agent 服务语义之上扩展自定义 REST 服务化入口的版本事实。该特性的主体是 runtime 自身：runtime 允许用户通过自定义扩展方式，把自身既有或目标 REST API 形态映射为标准 Agent 服务调用，使调用方可以使用普通 HTTP、JSON body 和可选 SSE 流访问当前 runtime hosted Agent。

本特性不是外部网关或 BFF 的实现方案，也不是替代 A2A JSON-RPC 的系统间标准协议。它与 `FEAT-001` 标准 Agent 服务入口关联，复用相同的正式 A2A Task生命周期和 Agent执行基础；自定义 REST 入口可以改变 URL、请求字段和响应信封，但每次消息提交都必须创建或恢复正式 A2A Task，不得建立第二套 Task owner。

当前版本一个 runtime 实例只 host 一个 Agent 实例。自定义 REST 路径中即使包含 `agent_id`、`project_id`、`workspace_id` 等业务字段，也只能作为请求上下文、metadata、审计或用户自定义校验输入，不作为同一 runtime 实例内区分或路由多个 Agent handler 的依据。

本特性面向以下角色：

- Agent 应用开发者：希望在 runtime 内暴露符合业务或平台习惯的 REST 服务入口。
- 平台集成方：希望保留既有 HTTP API 形态，同时复用 runtime 的标准 Agent 执行语义。
- 测试与验收团队：需要从黑盒视角验证自定义 REST 入口与标准 Agent 服务入口的语义一致性。
- 下游设计与实现人员：需要在 L2 中细化扩展点、配置、路由装配、字段映射和错误封装实现。

本特性明确不面向以下路径：

- 其他 agent-runtime 调用本 runtime 的系统间路径；该路径仍以 `FEAT-001` 的 A2A Agent 服务入口为标准。
- agent-bus forwarding 投递到 runtime 的系统间路径；该路径仍必须落到 `FEAT-001` 的标准 Agent 服务入口。
- 外部网关、BFF 或客户平台自行实现的协议转换逻辑；这些系统可以调用 runtime，但其内部适配不属于 `agent-runtime` 版本事实。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 本次版本变化 | 事实要求 |
|---|---|---|---|
| 自定义 REST 服务入口 | MUST | 新增 | runtime 必须支持启用一个自定义 REST 服务化入口能力，使调用方可以通过用户定义的 REST URL 与 JSON 请求体发起 Agent 调用。 |
| 单 path | MUST | 新增 | 当前版本只允许配置一个自定义 REST path pattern，不支持同一入口挂载多个兼容 path。 |
| 既有入口共存 | MUST | 新增 | 自定义 REST 入口只新增一个可配置 mapping，不替换或禁用 runtime 既有入口；既有固定入口不计入“一个自定义入口”约束。 |
| 单 Agent 执行边界 | MUST | 现有约束 | 自定义 REST path 必须映射到当前 runtime hosted 的同一个 Agent；path 或 body 中的 `agent_id` 不得触发同实例多 Agent 路由。 |
| 用户自定义请求映射 | MUST | 新增 | runtime 必须提供用户可扩展的请求映射能力，由用户自行定义 path、query、header、body 到标准 Agent 调用上下文的映射规则。 |
| 用户自定义响应投影 | MUST | 新增 | runtime 必须提供用户可扩展的响应投影能力，由用户自行定义同步 JSON 响应、流式 SSE event data 和错误响应信封的展示形态。 |
| 同步消息调用 | MUST | 新增 | 自定义 REST 入口必须支持一次普通 HTTP 请求对应一次 A2A 消息提交，并把原始正式 Task结果或框架脱敏错误信息交给用户 adapter 投影为 JSON 响应。 |
| 流式消息调用 | MUST | 新增 | 自定义 REST 入口必须支持以 SSE 返回执行过程；正式 Task产生的每个原始状态、输出或消息事件都交给用户 adapter 转换为自定义 SSE data。 |
| A2A Task 语义归一 | MUST | 现有约束 | 自定义 REST 入口必须创建或恢复正式 A2A Task，并复用既有 Task状态、事件、执行、错误、中断和可观测语义。 |
| conversation续轮 | MUST | 新增 | 调用方不必回传 taskId；runtime按可信 tenant和客户 conversation标识恢复唯一 `INPUT_REQUIRED` Task，无可恢复 Task时创建新 Task，运行中、不可自动恢复或歧义状态返回冲突。 |
| 并发唯一性 | MUST | 新增 | 同一 `(tenant, conversation)` 的并发消息不得创建多个正式活跃 Task；当前请求无法取得该会话执行权时返回 409。 |
| Task 查询能力 | OUT | 不在范围 | 当前版本不为自定义 REST 入口提供 Task 查询；需要时使用标准 A2A 入口。 |
| Task 取消能力 | OUT | 不在范围 | 当前版本不为自定义 REST 入口提供 Task 取消；需要时使用标准 A2A 入口。 |
| 异步提交与轮询 | OUT | 不在范围 | 当前版本不通过自定义 REST 入口提供 Task 异步提交、轮询或订阅。 |
| 自定义字段冲突处理 | MAY | 新增 | path、query、header、body 中同一业务语义出现冲突时，由用户自定义映射规则自行决定处理方式；当前版本不规定平台级优先级、冲突检测或自动拒绝策略。 |
| runtime-to-runtime 自定义 REST 调用 | OUT | 不在范围 | 当前版本不要求其他 runtime 通过本特性的自定义 REST 入口调用本 runtime。 |
| agent-bus 自定义 REST 投递 | OUT | 不在范围 | 当前版本不要求 agent-bus forwarding 通过本特性的自定义 REST 入口投递请求。 |
| 独立状态机 | OUT | 不在范围 | 当前版本不允许自定义 REST 入口定义私有的 run、job、message 或 conversation 状态机。 |
| webhook callback | OUT | 不在范围 | 当前版本不通过本特性新增提交后主动 callback 能力；非一次性响应应使用 SSE、标准 A2A 入口或后续独立特性。 |

## 3. 外部接口与入口要求

FEAT-022 只约束黑盒可观察的外部接口行为，不固定 Java SPI 名称、方法签名、包路径、controller 类、内部 DTO、编排类型或自动装配细节。具体扩展点和实现结构由 L2 详细设计定义，但不得改变本文的外部事实要求。

| 入口 / 对象 | 类型 | 事实要求 |
|---|---|---|
| 自定义 REST path pattern | HTTP endpoint | 必须由用户声明并由 runtime 暴露；当前版本只支持一个自定义 path pattern。 |
| HTTP method | HTTP contract | 当前版本固定使用 POST 提交消息。 |
| Request header | HTTP metadata | 可作为租户、用户、空间、鉴权透传、correlation 或业务 metadata 的输入来源；本特性不声明完成认证授权。 |
| Path / query 参数 | HTTP metadata | 可作为会话、业务上下文、Agent 标识展示字段、审计字段或 metadata 的输入来源；不得作为同实例多 Agent 路由依据。 |
| JSON request body | HTTP body | 必须允许用户定义业务字段名，并通过扩展映射为标准 Agent 输入、会话上下文、stream 标记或 metadata。 |
| JSON response body | HTTP body | 同步响应必须按用户定义信封返回；正常返回分支向 adapter 提供原始正式 Task结果，错误分支提供框架脱敏后的错误信息。 |
| SSE stream | HTTP response | 流式响应必须使用可解析的 SSE；adapter 接收原始正式 Task状态、输出或消息事件并返回自定义 data body，框架负责 JSON 序列化和 SSE framing。 |
| 错误响应 | HTTP status + body | 错误展示可自定义，但 HTTP status 由框架错误分支确定；adapter 只负责错误 body，不指定自定义状态码。 |

### 3.1 参考 REST 形态

以下仅作为能力覆盖示例，不是当前版本强制 wire schema：

```http
POST /v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
Content-Type: application/json
Accept: application/json
```

```json
{
  "input": "请总结这段材料",
  "stream": false,
  "workspace_id": "workspace-a",
  "custom_data": {
    "source": "legacy-gateway"
  }
}
```

示例字段语义：

| 外部字段 | 可选映射语义 |
|---|---|
| `project_id` | 业务 project metadata 或审计字段。 |
| `agent_id` | Agent 标识展示、metadata 或用户自定义校验字段；不参与同实例 handler 路由。 |
| `conversation_id` | 会话、上下文或 Task 关联字段。 |
| `input` / `query` | 用户消息文本。 |
| `stream` | 同步或流式响应偏好。 |
| `workspace_id` / `role_id` / `custom_data` | 业务 metadata。 |

示例同步响应形态：

```json
{
  "success": true,
  "agent_id": "agent-a",
  "conversation_id": "conversation-1",
  "output": "总结结果...",
  "task_id": "task-1",
  "custom_rsp_data": {}
}
```

示例错误响应形态：

```json
{
  "success": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "message is required"
  },
  "task_id": null
}
```

上述字段均由用户 adapter 自定义；底层正式 A2A Task 始终由 runtime 创建或恢复，示例是否向调用方展示 `task_id` 由用户 adapter 决定，不影响框架续轮规则。

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户 / 系统动作 | 期望行为 |
|---|---|---|---|
| 启用自定义 REST 入口 | runtime 已 host 一个 Agent，用户已提供自定义映射与响应投影扩展 | 部署方配置一个 path pattern | runtime 暴露一个自定义 REST 入口；路径冲突或入口配置不完整时，该入口不应以半可用状态对外提供服务。 |
| 同步调用 Agent | 自定义 REST 入口已启用，调用方发送非流式请求 | 调用方向配置的 REST path 提交 JSON body | runtime 将请求映射为 A2A 消息，创建或恢复正式 Task，并返回自定义 JSON 信封。 |
| 流式调用 Agent | 自定义 REST 入口已启用，调用方请求流式响应 | 调用方向配置的 REST path 提交请求并声明流式偏好 | runtime 创建或恢复正式 Task并返回 SSE stream；原始 A2A 事件由 adapter 包装为自定义 data body，框架逐帧输出。 |
| 传递业务上下文 | 调用方在 path、query、header 或 body 中携带业务字段 | 用户自定义映射把相关字段转为标准上下文或 metadata | Agent 执行上下文、日志、trace 和审计链路可观察到被映射后的上下文字段；字段不会改变单 runtime 单 Agent 边界。 |
| 外部字段冲突 | 同一业务语义在多个请求来源中出现不同值 | 用户自定义映射规则自行选择、合并、拒绝或透传 | runtime 不提供统一优先级承诺；调用结果以用户扩展定义为准。 |
| 既有入口共存 | 自定义 REST 入口已启用 | 调用方访问 runtime 既有固定入口 | 原有固定入口保持可用，不受单个自定义 path 约束。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 入口归一语义

- 自定义 REST 入口是 runtime 的边缘服务化入口，不是新的事实权威。
- 每次自定义 REST 消息提交都必须归一为一次标准 A2A 消息提交。
- 自定义 REST 入口可以改变请求和响应外观，但不得改变正式 Task 生命周期、底层 Agent 执行、错误分类、租户上下文和可观测事实。
- 自定义 REST 入口不得绕过标准 Task控制路径形成私有执行链；无可恢复 Task时创建正式 Task，存在唯一可恢复 Task时继续该 Task。
- Task解析和创建必须按可信 tenant隔离；相同 conversation值在不同 tenant之间不得互相发现或恢复。

#### 5.1.2 单入口单路径语义

- 当前版本只允许一个自定义 REST 入口能力和一个 path pattern。
- runtime 既有固定入口不计入该数量约束。
- path pattern 中的业务变量可以进入上下文或 metadata，但不得成为同实例多 Agent handler 选择条件。

#### 5.1.3 请求映射语义

- 用户自定义映射规则负责解释 path、query、header 和 body 中的业务字段。
- FEAT-022 不规定字段优先级、冲突检测、自动合并或自动拒绝策略。
- 未被用户映射的字段是否透传、丢弃或进入 metadata，由用户扩展和 L2 设计约束；本文不把自动注入 metadata 写成黑盒事实承诺。
- 框架只校验 JSON media type、JSON object 可解析性和转换后 runtime 标准请求的最小必填项；其他业务字段校验由 adapter 自行决定。

#### 5.1.4 同步响应语义

- 非流式请求应返回一次 JSON 响应。
- 标准 Task控制路径返回的原始同步结果交给 adapter，由用户自定义响应信封字段名和投影方式。
- adapter 可以隐藏 taskId，但不得改变底层正式 Task 的状态或把失败状态投影为成功。

#### 5.1.5 流式响应语义

- 流式请求应返回 SSE。
- 每个原始 Task状态、输出或消息事件都交给 adapter，SSE data 字段结构和信封字段由用户定义。
- 收到 interrupted 状态（包括 `INPUT_REQUIRED`、`AUTH_REQUIRED`）或终态事件时，框架先输出当前事件，再结束本次 SendStreamingMessage 流。

#### 5.1.6 错误、状态与可观测语义

| 场景 | 事实要求 |
|---|---|
| 请求无法解析 | 返回 4xx HTTP status 和自定义错误信封；错误语义应可诊断为非法请求。 |
| 转换后缺少 runtime 最小必填项 | 返回 400 和自定义错误信封；当前具体校验项由 L2 设计记录。 |
| adapter 执行失败 | 返回 500 和自定义错误信封；adapter 不指定自定义 4xx 状态。 |
| runtime 不可执行 | 返回可诊断错误；不得伪造成功响应。 |
| Agent 执行失败 | `FAILED`、`REJECTED` 或 `CANCELED` Task必须投影为失败语义，不得包装为成功结果。 |
| 流式过程中失败 | 应通过 adapter 包装的错误 data body 表达失败，并结束当前流；同一调用最多输出一帧错误。 |
| 中断 | 正式 Task进入 interrupted状态（包括 `INPUT_REQUIRED`、`AUTH_REQUIRED`），对应原始 A2A 事件交给 adapter 包装，随后结束本次消息流。 |
| timeout / cancel / observability | 本特性不新增私有机制，沿用宿主 Web 生命周期、runtime/orchestrator 配置和既有观测设施。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 系统间标准协议替代 | 自定义 REST 入口不替代 A2A；runtime-to-runtime 和 agent-bus forwarding 不以本入口作为事实标准。 |
| 多个独立自定义入口 | 当前版本不承诺同一 runtime 内存在多个彼此独立的自定义 REST 入口或多个兼容 path。 |
| 同实例多 Agent 路由 | 当前版本不承诺按 path、query、header 或 body 中的 `agent_id` 路由多个 Agent handler。 |
| 平台级字段冲突治理 | 当前版本不承诺统一字段优先级、冲突检测、冲突拒绝或自动 metadata 注入策略。 |
| Custom REST Task 管理端点 | 当前版本不新增 Task查询、取消、轮询、订阅或断线重订阅端点；该限制不影响消息提交创建或恢复正式 Task。 |
| 独立状态机 | 不定义独立 run、job、message、conversation 状态机。 |
| webhook callback | 不通过本特性新增主动 callback 能力。 |
| 认证授权协议 | OAuth、签名校验、token 校验、mTLS、租户认证等不在本特性事实要求中。 |
| 非文本主路径 | 当前主路径仍是文本输入；file、data、multipart、二进制或多模态输入需单独进入版本事实。 |
| 强制中断底层执行 | 不承诺立即打断已经进入底层模型、HTTP client 或 Agent 框架的阻塞调用。 |
| Java SPI 具体形态 | 本文不固定接口名、方法签名、包路径、配置属性名、controller 名称或内部 DTO 类型。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把 FEAT-022 设计成与 `FEAT-001` 关联、创建或恢复正式 A2A Task的 REST 协议投影。
- L2 可以定义 Java SPI、配置属性、path 注册、请求解析、响应封装、路由装配和默认实现，但不得把这些实现细节反向提升为本文没有声明的版本事实。
- 自定义 REST 入口必须复用标准 A2A消息、Task结果、流式事件、错误、中断和既有可观测链路，不得建立私有执行链路或私有状态 owner。
- 当前版本只允许一个自定义 path 和一个 adapter；runtime 既有入口不受影响。
- 用户自定义映射规则可以自行处理字段冲突、字段透传和响应信封，但实现文档必须说明其选择，避免测试和验收依赖隐式行为。
- 若未来要支持多个独立自定义 REST 入口、完整 Task 管理 REST surface、异步提交规范、webhook、multipart、二进制文件、批量消息、独立 run resource、多 Agent 路由或认证授权协议，必须先更新本特性或新增 version-scope 特性文档，再进入 L2 和实现。

## 7. 关联文档

- `version-scope/README.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `architecture/L1-High-Level-Design/agent-runtime/README.md`
- `architecture/L1-High-Level-Design/agent-runtime/api-appendix.md`
- `architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md`
- `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-022-custom-rest-api-to-a2a-jsonrpc-adaptation-spi.md`
