---
level: L2-LLD
module: agent-runtime-ext-java
feature_type: functional
feature_id: Feat-Func-022
status: design_accepted
dependency:
  - ../../../version-scope/FEAT-022-custom-rest-api-agent-service-entrypoint.md
  - ../../L1-High-Level-Design/agent-runtime/api-appendix.md
  - Feat-Func-001-standardized-agent-service-entrypoint.md
  - openJiuwen/agent-runtime-java
---

# Custom REST API 到 A2A 执行入口适配 SPI 设计说明

> 目标仓库：`openJiuwen/agent-solution`
> 目标模块：`common/agent-runtime-ext-java/agent-service-app/agent-service-app-custom-rest`
> 最后更新：2026-07-21

说明：本文档描述独立功能特性 Feat-Func-022。它与 Feat-Func-001“标准化智能体服务入口”关联：Feat-Func-001 提供标准 `/a2a` JSON-RPC 协议表面，Feat-Func-022 提供客户自定义 REST 协议表面；二者在 HTTP 协议层相互独立，但共享 runtime 已有的 A2A `RequestHandler`、`TaskStore`、事件总线、`A2AAgentExecutor` 和 Agent 执行链。Feat-Func-022 的实现放在 `agent-solution` 扩展仓，首版不修改 `agent-runtime-java` 源码。本文设计已接受、代码待落地；solution 仓中的历史残留代码不作为当前事实。

---

## 1. 概述

### 1.1 特性定位

Feat-Func-022 提供一个轻量 custom-rest 扩展 starter，使平台集成方可以使用自有 REST URL、请求字段和响应信封调用 runtime 的正式 A2A 执行入口。

该扩展完成两类适配：

1. 将客户自定义 HTTP 请求转换为 A2A SDK `MessageSendParams`。
2. 将 A2A 同步结果或流式事件交给业务 adapter 自定义包装。

```text
自定义 REST HTTP 请求
  -> CustomRestProtocolAdapter Java SPI
  -> A2ASendCommand(MessageSendParams, stream)
  -> conversationId 续轮解析
  -> A2A SDK RequestHandler
  -> TaskStore / EventBus / QueueManager
  -> A2AAgentExecutor
  -> AgentRuntimeHandler / runtime 下游执行链
  -> EventKind / StreamingEventKind
  -> CustomRestProtocolAdapter Java SPI
  -> 自定义 REST HTTP 响应 / SSE data
```

Custom REST 客户端不需要感知或回传 A2A `taskId`。业务 adapter 将客户会话标识映射为 A2A `Message.contextId` 并提供可信 tenant；框架在调用 `RequestHandler` 前按 `(tenant, contextId)` 构造隔离后的 internalContextId，查找唯一可续轮的正式 Task，并在需要时自动补入 `Message.taskId`。

配置的一个自定义 path 只新增一个 mapping，不替换、不代理也不禁用 runtime 既有入口。

### 1.2 当前事实依据

当前 runtime 代码已提供以下可复用边界：

| 组件 | 当前职责 | 本特性复用方式 |
| --- | --- | --- |
| `POST /a2a` | `A2aJsonRpcController` 解析 JSON-RPC 后调用 A2A SDK `RequestHandler` | 不经过该 controller，直接复用同一个 `RequestHandler` bean |
| `RequestHandler` | `onMessageSend(...)` / `onMessageSendStream(...)` 创建或恢复正式 Task，并连接 TaskStore、QueueManager 和事件总线 | Custom REST 的唯一 A2A 执行入口 |
| `TaskStore` | 按 taskId 保存正式 Task；`list(ListTasksParams)` 接收 context、tenant 和分页参数。当前 SDK `InMemoryTaskStore` 只按 context过滤，持久化实现可以执行 tenant过滤 | 用于 Custom REST 的 conversationId 续轮解析；扩展必须同时传 context与tenant，不能依赖默认实现忽略 tenant |
| `A2AAgentExecutor` | 把 SDK `RequestContext` 交给 runtime 的 Agent 执行 SPI，并通过 `AgentEmitter` 产生 Task 状态和 artifact 事件 | 由 `RequestHandler` 间接复用，扩展不直接依赖其具体下游类 |
| `MainEventBusProcessor` | 在事件分发给响应消费者前更新 TaskStore | 作为流式 reservation 可在首个 Task 事件后释放的持久化顺序依据 |

正式 A2A `RequestHandler` 默认只按 `taskId` 恢复 Task，不会按 contextId 自动选择 Task。因此 Feat-Func-022 在扩展层增加只对 Custom REST mapping 生效的 conversationId 续轮策略；该策略在调用 `RequestHandler` 前解析 taskId，不修改 SDK 或标准 JSON-RPC wire 语义。

### 1.3 设计原则

1. **YAML 只描述 HTTP 暴露面**：只配置一个 query URL，首版固定 POST，不把字段映射做成 YAML DSL。
2. **转换全部由 Java SPI 实现**：客户字段到 A2A 请求、A2A 结果到客户响应均由业务 adapter 控制。
3. **正式进入 A2A 执行链**：调用 `RequestHandler`，获得正式 Task、TaskState、Artifact 和事件语义。
4. **conversationId 对客户稳定**：客户无需感知 taskId；框架按 contextId 自动解析唯一可续轮 Task。
5. **不改变既有入口**：conversationId 自动续轮只作用于 Custom REST mapping，不修改 runtime 既有 mapping。
6. **不做进程内协议绕行**：不调用 `A2aJsonRpcController`，不拼 JSON-RPC 字符串，不发起二次 HTTP。
7. **首版复用现有存储**：分页使用 `TaskStore.list(ListTasksParams)` 查找正式 Task，不新增独立 task/run/job 状态机。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
| --- | --- | --- | --- |
| 自定义 URL 暴露 | 按 YAML 注册一个 POST path | `CustomRestProperties`、`CustomRestAutoConfiguration` | 需按本文刷新 |
| 入站转换 SPI | 将 HTTP Context 转为 A2A 发送命令 | `CustomRestProtocolAdapter`、`A2ASendCommand` | 需按本文刷新 |
| conversationId 续轮 | 查找唯一可恢复正式 Task并自动补 taskId | `CustomRestA2ATaskResolver`、`TaskStore` | 新增 |
| A2A 执行桥接 | 创建/恢复 Task并进入现有执行链 | `RequestHandler` | 既有依赖 |
| 出站转换 SPI | 包装 A2A 同步结果、流式事件和错误 | `CustomRestProtocolAdapter` | 需按本文刷新 |

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 首版目标 | 说明 |
| --- | --- | --- |
| 单个自定义 query URL | 支持 | URL 模板可包含业务自定义 path variable |
| 固定 POST | 支持 | 不提供 method 配置项 |
| Java 入站转换 | 支持 | HTTP Context 转为 `A2ASendCommand` |
| conversationId 自动续轮 | 支持 | 客户不传 taskId；框架解析唯一可续轮 Task |
| A2A 正式 Task | 支持 | 请求进入 `RequestHandler`、TaskStore 和事件总线 |
| 同步执行 | 支持 | 调用 `RequestHandler.onMessageSend(...)` |
| 流式执行 | 支持 | 调用 `RequestHandler.onMessageSendStream(...)` 并输出 SSE |
| Java 出站转换 | 支持 | 包装正式 `Task`、`StreamingEventKind` 和错误 |
| 多 adapter 共存 | 不支持 | 首版只允许一个 path 和一个 adapter |
| GetTask/CancelTask/SubscribeToTask 自定义端点 | 不支持 | 首版只提供发送/续轮入口 |
| YAML 字段映射 DSL | 不支持 | 转换由 Java SPI 实现 |
| multipart / 文件输入 | 不支持 | 首版只解析 JSON object body |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
| --- | --- | --- |
| 调用 `A2aJsonRpcController.handleJsonRpc` | controller 是 JSON-RPC HTTP 外壳，进程内调用会重复解析和序列化 | 直接调用 `RequestHandler` |
| 构造 JSON-RPC 字符串 | adapter 已经位于 Java 对象边界，无需协议文本往返 | 构造 `MessageSendParams` |
| 直接调用 runtime 下游 Agent 执行 SPI | 会绕过正式 TaskStore/EventBus 生命周期，不满足“转成 A2A”目标 | 只调用 `RequestHandler` |
| 修改 A2A SDK 按 contextId 恢复 | 会改变标准 `/a2a` 语义，并引入同 context 多 Task 歧义 | 只在 Custom REST 扩展层解析 taskId |
| 新建独立 TaskStore | 会复制正式 Task状态并产生一致性问题 | 复用 runtime 已有 `TaskStore` |
| 多路径、多操作路由 DSL | 当前需求只有一个自定义发送入口 | 单 path + 单 adapter |

### 2.3 行为承诺

- **必须**：`POST {query-path}` 被调用时，框架解析 HTTP Context 并调用 `CustomRestProtocolAdapter.toA2ARequest(...)`。
- **必须**：adapter 产出的 `Message.contextId` 非空，它是客户协议中的 externalContextId；框架负责转换为租户隔离的 internalContextId。
- **必须**：adapter 不从客户请求映射 `taskId`；taskId 由框架解析和注入。
- **必须**：框架调用 runtime 已有 `TaskStore`，按 internalContextId 和 tenant 查找续轮候选。
- **必须**：存在唯一 `INPUT_REQUIRED` 正式 Task时自动续轮；不存在可续轮 Task时创建新 Task。
- **必须**：存在 `SUBMITTED`/`WORKING` Task或多个非终态正式 Task时拒绝请求，避免错误续轮。
- **必须**：同步和流式调用分别进入 `RequestHandler.onMessageSend(...)` 和 `onMessageSendStream(...)`。
- **必须**：A2A 原始同步结果或每个原始流式事件交给 adapter 包装，框架不先转换为 `QueryResponse/QueryChunk`。
- **必须**：非空 body 只接受 `application/json` 或 `application/*+json`；非法 JSON 或非 object 根节点返回 400。
- **必须**：配置的 mapping 与 runtime 既有 mapping 共存，不改变既有入口行为。
- **必须**：入口日志不记录认证 header 和 raw body。
- **允许**：adapter 自行决定外部字段名、消息 parts、metadata、同步响应信封和 SSE data 信封。
- **禁止**：按 TaskStore 返回顺序随意选择多个候选 Task。
- **禁止**：依赖 solution 历史代码中的私有 taskId 前缀、私有状态机或编排实现细节判断正式 Task。

---

## 3. 核心设计（Logical + Process View）

### 3.1 模块放置

实现继续放在：

```text
openJiuwen/agent-solution
  common/agent-runtime-ext-java/agent-service-app/agent-service-app-custom-rest
```

该模块是北向 HTTP ingress/app 扩展，不放在 `agent-service-adapters/`。宿主应用已经引入 runtime 的 `agent-service-app` 后，再引入 custom-rest jar 并声明唯一 adapter bean；配置 `openjiuwen.service.custom-rest.query-path` 即注册入口。

首版只修改 `agent-solution` 扩展模块及其使用方，不修改 `agent-runtime-java`。扩展通过 Spring 注入 runtime 已有的 `RequestHandler` 和 `TaskStore` bean。

### 3.2 入站主流程

```text
HTTP POST {query-path}
  -> 校验 Content-Type
  -> ObjectMapper 解析 JSON object body
  -> 提取 requestPath / headers / pathVariables / queryParams / body
  -> CustomRestProtocolAdapter.Context
  -> adapter.toA2ARequest(context)
  -> A2ASendCommand
       MessageSendParams params
       boolean stream
  -> 校验 params.message.contextId
  -> 校验 params.tenant
  -> CustomRestConversationCoordinator.execute(params.tenant, command)
       对 (tenantId, externalContextId) 原子取得 reservation
       构造租户命名空间内的 internalContextId
       resolver 按 internalContextId 查询并解析正式 Task
       必要时自动注入 taskId
       构建 ServerCallContext
       if stream: RequestHandler.onMessageSendStream(...)
       else: RequestHandler.onMessageSend(...)
       SDK 持久化新 Task或确认续轮后释放 reservation
  -> adapter 出站包装
```

请求体规则保持为：

| 场景 | 框架行为 |
| --- | --- |
| JSON media type + JSON object | 继续进入 adapter |
| Content-Type 缺失且 body 为空 | 按空 object 处理 |
| Content-Type 缺失但 body 非空 | 返回 415 |
| 非 JSON media type | 返回 415 |
| JSON 语法非法或根节点非 object | 返回 400 |

### 3.3 HTTP method 配置

框架固定注册：

```text
POST {query-path}
```

非 POST 请求不进入 adapter，由 Spring MVC 按 method mismatch 处理。首版不提供 `enabled` 或 `query-method` 属性；存在合法 `query-path` 即启用，不配置即不启用。

### 3.4 正式 A2A Task 与 conversationId 续轮

A2A SDK 的标准行为是按 `Message.taskId` 恢复 Task，`Message.contextId` 只表示上下文。Custom REST 客户端不提供 taskId，因此扩展定义以下专属策略。下文的 `externalContextId` 是 adapter 产出的客户会话标识；`internalContextId` 是框架按可信 tenant 做命名空间隔离后的 A2A contextId：

```text
外部 conversationId
  -> adapter 映射为 externalContextId
  -> framework namespace(tenantId, externalContextId) 得到 internalContextId
  -> resolver 查询该 internalContextId 下的正式 Task
  -> 自动决定创建新 Task或恢复 INPUT_REQUIRED Task
```

状态决策表：

| 查询结果 | 决策 |
| --- | --- |
| 无正式非终态 Task | 保持 taskId 为空，SDK 创建新 Task |
| 唯一正式 Task为 `INPUT_REQUIRED` | 将该 Task.id 注入 `Message.taskId` 后续轮 |
| 存在正式 `SUBMITTED` 或 `WORKING` Task | 返回 409 `conversation is already running` |
| 唯一正式 Task为 `AUTH_REQUIRED` 或其他不可自动续轮的非终态 | 返回 409 `conversation is not automatically resumable` |
| 只有 `COMPLETED/FAILED/CANCELED/REJECTED` | 不复用终态 Task，创建新 Task |
| 多个正式非终态 Task | 返回 409 `ambiguous active task` |

`AUTH_REQUIRED` 当前不作为自动续轮状态；它会结束当前消息流，但后续 Custom REST请求不会自动恢复该 Task。如后续明确支持认证恢复，再单独扩展状态规则。

同一 JVM 内，`CustomRestConversationCoordinator` 使用 `ConcurrentHashMap<ConversationKey, Reservation>` 保存活跃占位。请求通过 `putIfAbsent` 原子取得 `(tenantId, externalContextId)` 的执行权；已有 reservation 时立即返回 409 `conversation_busy`，不等待也不再次查询。reservation 是可由任意回调线程按 token 条件删除的占位对象，不使用 `ReentrantLock`，因为流式回调与 HTTP 请求线程不同，Java 锁不能跨线程解锁。

reservation 生命周期固定为：

1. 在查询 TaskStore 前取得，覆盖“查询候选、调用 `RequestHandler`、正式 Task首次可观察”的完整竞态窗口；状态至少区分 `ACQUIRED`、`HANDLER_STARTED` 和 `UNCERTAIN`。
2. 同步调用正常返回后以 `reservations.remove(key, token)` 释放；SDK 已在返回前完成本轮可见事件的持久化。若在调用 handler 前校验失败，可直接释放；handler 已启动后的异常只有在 TaskStore 已观察到非终态 Task或能够证明执行未注册时才释放，否则转为 `UNCERTAIN` 并进入后台对账。
3. 流式调用在收到首个携带 taskId 的 Task、状态、artifact 或 message 事件，并确认 `TaskStore.get(taskId)` 非空后释放；`MainEventBusProcessor` 的“先持久化、后分发”顺序是该判断成立的前提。publisher 若在任何正式 Task可观察前终止，不能形成成功响应。
4. 流式调用若在 handler 启动前失败，可直接释放。publisher 在首个事件前终止且能够证明不会再产生 Task时释放；否则转为 `UNCERTAIN`。若 HTTP 客户端在首个 Task 事件前断开，桥接层停止向客户端写数据，但继续最小化消费上游，直到 Task 首次可观察或 publisher 确定终止。
5. `UNCERTAIN` reservation 由后台对账按 internalContextId + tenant 分页查询 TaskStore：观察到非终态 Task后删除占位，让后续请求由 resolver 返回 busy；确认 handler/publisher 已终止且连续对账仍无 Task时才删除。无法证明安全时保持 fail-closed，后续同 conversation 返回409并输出结构化 ERROR，不以超时静默放行。
6. 所有释放都必须携带原 token；迟到回调不得删除同 key 的后继 reservation。

首版只支持一个逻辑 runtime 的单活 JVM。多个副本不得共享同一 TaskStore namespace 对外提供同一个 Custom REST path；active-active 部署必须先提供分布式 compare-and-set active-task 索引，不能用本地锁冒充跨实例互斥。

Custom REST resolver 只识别 `TaskStore.list(ListTasksParams)` 返回的 A2A SDK `Task` 及标准 `TaskState`。solution 历史代码中的私有 taskId 前缀或内部编排状态不属于本设计契约，既不能作为过滤规则，也不能成为续轮正确性的前提。

### 3.5 SPI 形态

```java
public interface CustomRestProtocolAdapter {
    A2ASendCommand toA2ARequest(Context context);

    Object fromA2AResponse(
        Task response,
        Context context,
        long executionTimeMs
    );

    Object fromA2ATaskFailure(
        Task task,
        Context context,
        long executionTimeMs
    );

    Object fromA2AStreamEvent(
        StreamingEventKind event,
        Context context
    );

    Object fromA2AStreamFailure(
        StreamingEventKind event,
        Context context
    );

    Object fromError(
        int httpStatus,
        String errorCode,
        String errorMessage,
        Context context,
        long executionTimeMs
    );

    record A2ASendCommand(MessageSendParams params, boolean stream) {
    }

    record Context(
        String requestPath,
        Map<String, String> headers,
        Map<String, String> pathVariables,
        Map<String, List<String>> queryParams,
        Map<String, Object> body
    ) {
    }
}
```

`A2ASendCommand` 不是校验结果包装。它承载一次真实 A2A 发送所需的两个独立信息：A2A 请求参数和调用同步/流式方法的选择。`MessageSendParams` 本身不包含 `onMessageSend` 与 `onMessageSendStream` 的选择，因此该 record 有独立必要性。

### 3.6 SPI Context

`Context` 仍是一次 HTTP 请求的只读视图。框架不把 `HttpServletRequest`、原始 body 字符串或 Spring MVC 类型暴露给 adapter。顶层集合做防御性不可变复制，不承诺递归冻结嵌套 JSON 对象。

### 3.7 endpoint 注册

auto-configuration 通过 `SmartInitializingSingleton` 在普通 controller mapping 完成装配后执行冲突检查，再调用 `RequestMappingHandlerMapping.registerMapping(...)` 动态注册固定 POST mapping，并使用宿主的 builder configuration。检查和注册在同一启动回调内完成；任何失败都中止应用启动，不留下半注册入口。

启动期规则：

1. 未配置 `query-path`：不启用，不要求 adapter、RequestHandler 或 TaskStore。
2. 配置 path 但缺少或存在多个 adapter：启动失败。
3. path 为空、空白或不是绝对路径：启动失败。
4. 已启用但缺少 `RequestHandler` 或 `TaskStore`：启动失败，说明宿主没有完整 A2A server能力。
5. 注册前使用宿主 `RequestMappingHandlerMapping` 的 pattern 配置解析 path；与既有 POST mapping 完全相同或仅变量名不同的等价 pattern 必须判定为冲突并启动失败。其他可由 Spring specificity 明确裁决的父子 pattern 可以共存；无法静态证明是否相交的任意 pattern 不承诺做通用集合求交，运行期一旦出现 Spring ambiguous mapping 必须按服务器错误记录并修正配置。

### 3.8 同步与 SSE 输出

同步模式调用 `RequestHandler.onMessageSend(...)`。虽然 SDK 方法签名返回 `EventKind`，本特性要求正式 Task，因此框架必须校验结果为 `Task` 后才调用 adapter；返回 `Message` 或其他非 Task结果时按 `INVALID_AGENT_RESPONSE` 映射为502，不得作为成功响应。`fromA2AResponse` 使用 `Task` 参数，把该约束固化在业务 SPI 上。

流式模式调用 `RequestHandler.onMessageSendStream(...)`，订阅 `Flow.Publisher<StreamingEventKind>`：

1. 普通事件调用 `adapter.fromA2AStreamEvent(...)`；`FAILED`、`REJECTED` 或 `CANCELED` 状态事件调用 `adapter.fromA2AStreamFailure(...)`。
2. adapter 返回对象经 Jackson 序列化后作为无 event name 的 SSE `data:` 输出。
3. `TaskStatusUpdateEvent` 进入任一 interrupted 状态（包括 `INPUT_REQUIRED`、`AUTH_REQUIRED`）或终态时，先成功写出该事件，再取消当前 subscription 并结束 emitter，不等待 publisher 自行完成。
4. publisher 正常完成时结束 emitter；重复 terminal callback 必须幂等。
5. publisher 异常时最多输出一帧 adapter 错误 data，然后结束。
6. emitter completion、timeout或写失败发生在 reservation 已释放后，立即取消当前 `Flow.Subscription`；若发生在首个 Task可观察前，停止下游写出但按 3.4 节继续最小化消费，直到可以安全释放 reservation 再取消。
7. 客户端断连不自动调用 `RequestHandler.onCancelTask(...)`，避免把网络断开等同于业务取消。
8. bridge 对 publisher 只订阅一次，每次只 `request(1)`；完成 TaskStore确认、adapter投影和当前 SSE写出后再请求下一帧，不使用无界内存缓冲。

---

## 4. 入站协议转换（Custom REST -> A2A）

### 4.1 转换边界

入站方法为：

```java
A2ASendCommand toA2ARequest(CustomRestProtocolAdapter.Context context);
```

adapter 负责理解客户字段，框架负责 A2A Task 续轮和执行。

```text
HTTP Context
  -> adapter
  -> MessageSendParams + stream
  -> framework conversation resolver
  -> RequestHandler
```

### 4.2 转换前参数：Context

假设配置 URL 为：

```text
POST /custom/{session_key}?version={version}
```

其中 `{session_key}` 和 `{version}` 都只是业务示例，不是框架固定字段。adapter 收到：

| Context 字段 | 内容 | 约束 |
| --- | --- | --- |
| `requestPath` | 实际 URI path | 不含 query string |
| `headers` | HTTP headers | header 名统一小写 |
| `pathVariables` | URL 模板变量 | 键名由 `query-path` 决定 |
| `queryParams` | 全部 query parameter值 | 每个键对应 `List<String>` |
| `body` | JSON object对应 Map | 空 body 为不可变空 Map |

### 4.3 转换后参数：A2ASendCommand

adapter 必须构造 A2A SDK `MessageSendParams`，重点字段如下：

| A2A 字段 | 用途 | 规则 |
| --- | --- | --- |
| `message.contextId` | adapter 产出的外部 conversationId | 必填；框架在进入 TaskStore 前替换为 tenant 命名空间内的 internalContextId |
| `message.taskId` | 正式 Task 恢复标识 | adapter 必须留空，由框架注入 |
| `message.messageId` | 本轮消息标识 | adapter 按客户协议映射或生成 |
| `message.role` | A2A 消息角色 | 通常为 `ROLE_USER` |
| `message.parts` | 交给 A2A/runtime 的消息内容 | 支持 SDK 允许的 Part 类型；当前 runtime 主要消费 TextPart |
| `message.metadata` | 消息级扩展信息 | 不承载可信身份 |
| `params.metadata` | 请求级扩展信息 | 将进入现有 A2A adapter的 metadata处理 |
| `params.tenant` | A2A tenant和续轮隔离键 | 必填；adapter必须按宿主安全策略从可信 header或调用上下文映射，不得从不可信 body取值 |
| `configuration.returnImmediately` | 是否立即返回当前 Task快照 | 必须为 null或 false；首版同步调用等待本轮结果，true视为非法 adapter command |
| `configuration.taskPushNotificationConfig` | Task push配置 | 必须为空；webhook不属于本特性 |
| `command.stream` | 选择同步或流式调用 | 由 adapter从自定义协议字段或固定策略得出 |

示意代码：

```java
public A2ASendCommand toA2ARequest(Context context) {
    Map<String, Object> input = (Map<String, Object>) context.body().get("input");
    Message message = Message.builder()
        .role(Message.Role.ROLE_USER)
        .contextId(context.pathVariables().get("session_key"))
        .messageId(UUID.randomUUID().toString())
        .parts(List.of(new TextPart(String.valueOf(input.get("query")))))
        .build();
    MessageSendParams params = MessageSendParams.builder()
        .message(message)
        .tenant(trustedTenant(context))
        .build();
    return new A2ASendCommand(params, Boolean.TRUE.equals(context.body().get("stream")));
}
```

### 4.4 conversationId 续轮解析

框架对二元组 `(tenantId, externalContextId)` 的确定性字节表示计算 SHA-256：每段均编码为“4 字节 big-endian UTF-8 字节长度 + UTF-8 字节”，并按 tenant、externalContextId 顺序拼接。随后生成 `internalContextId = "custom-rest:v1:" + base64urlNoPadding(digest)`，再重建 `Message` 写入该值。该格式跨进程重启稳定、长度固定，不把原始租户和客户会话标识直接写入 TaskStore；原始 externalContextId 仍只通过当前 `Context` 提供给响应 adapter。resolver 在 coordinator reservation 内分页调用：

```java
pageToken = null
do {
    result = taskStore.list(ListTasksParams.builder()
        .contextId(internalContextId)
        .tenant(tenantId)
        .pageSize(100)
        .historyLength(0)
        .includeArtifacts(false)
        .pageToken(pageToken)
        .build())
    collect(result.tasks())
    pageToken = result.nextPageToken()
} while (pageToken != null)
```

resolver 必须遍历全部分页结果后再执行以下决策，不能只检查第一页：

1. 任一结果缺少 taskId、contextId、status/state，或返回的 contextId 与查询条件不一致：按 `task_resolution_failed` 返回500，不调用 `RequestHandler`；不得忽略可疑结果后创建新 Task。
2. 忽略所有终态 Task。
3. 没有非终态 Task：保持 taskId 为空。
4. 唯一非终态 Task为 `INPUT_REQUIRED`：重建 `Message` 并注入该 Task.id。
5. 唯一非终态 Task为 `SUBMITTED`、`WORKING`、`AUTH_REQUIRED` 或其他状态：返回对应 409，不自动续轮。
6. 多个非终态 Task：返回 409。

resolver 不修改 adapter 提供的 parts、message metadata、configuration 和 tenant；框架只覆盖 message contextId。若客户响应需要原始 conversationId，从当前 `Context` 读取，不从 internalContextId 反解。

调用 `RequestHandler` 前，框架使用同一个 tenantId 同时重建 `MessageSendParams.tenant`，并写入 runtime 约定的 `ServerCallContext.state` tenant key。两处值不一致属于框架缺陷；adapter 不得通过 message metadata 覆盖可信 tenant。这样 Task查询隔离与 `A2aAgentExecutor` 构造执行身份使用同一租户来源。

### 4.5 转换后的框架处理

| adapter/resolver结果 | 框架行为 |
| --- | --- |
| command或 params/message 为 null | 500 `adapter execution failed` |
| `params.tenant` 为空 | 400 `tenant is required` |
| `message.contextId` 为空 | 400 `conversation_id is required` |
| adapter 写入非空 taskId | 500 `adapter task_id must be empty` |
| adapter 设置 `returnImmediately=true` 或 push配置 | 500 `adapter command is not supported` |
| 唯一 `INPUT_REQUIRED` Task | 自动注入 taskId并调用 RequestHandler |
| conversation 正在执行 | 409，不调用 RequestHandler |
| 无活跃 Task | 由 SDK 创建新 Task |
| `stream=false` | `onMessageSend(...)` |
| `stream=true` | `onMessageSendStream(...)` |

---

## 5. 出站协议转换（A2A -> Custom REST）

### 5.1 转换边界

| 场景 | adapter 方法 | 转换前参数 | 转换后参数 |
| --- | --- | --- | --- |
| 同步正常结果 | `fromA2AResponse(...)` | 非失败正式 `Task`、Context、耗时 | 自定义 JSON body Object |
| 同步 Task失败 | `fromA2ATaskFailure(...)` | `FAILED`、`REJECTED` 或 `CANCELED` Task、Context、耗时 | 自定义失败 JSON body Object |
| 流式普通事件 | `fromA2AStreamEvent(...)` | 单个非失败 `StreamingEventKind`、Context | 自定义 SSE data Object |
| 流式 Task失败 | `fromA2AStreamFailure(...)` | 表示 `FAILED`、`REJECTED` 或 `CANCELED` 的事件、Context | 自定义失败 SSE data Object |
| 框架或 A2A 错误 | `fromError(...)` | HTTP status、错误码、脱敏消息、Context、耗时 | 自定义错误 Object |

所有返回值必须能被 Jackson 序列化。框架不要求客户响应使用 A2A JSON-RPC envelope。

### 5.2 同步结果转换

```java
Object fromA2AResponse(
    Task response,
    Context context,
    long executionTimeMs
);
```

adapter 会收到框架校验后的 SDK 原始 `Task`，可读取：

- `Task.id`
- `Task.contextId`
- `Task.status`
- `Task.artifacts`
- `Task.history`
- `Task.metadata`

客户协议可以隐藏 taskId，只返回 conversationId 和业务结果；框架续轮不依赖客户回传 taskId。但 adapter 不应丢失客户实际需要的 Task 状态、interrupt 或 artifact 语义。

框架在调用 adapter 前检查同步返回的 Task状态。`FAILED`、`REJECTED` 或 `CANCELED` 不进入 `fromA2AResponse(...)`，而进入 `fromA2ATaskFailure(...)`；`INPUT_REQUIRED` 是可恢复的非终态结果，仍进入 `fromA2AResponse(...)`。框架只能保证分支选择正确，无法检查 adapter 返回的任意 `Object` 是否在客户协议中语义上表示失败；`fromA2ATaskFailure(...)` 不得生成成功信封属于 adapter 合规责任，必须由业务 adapter 契约测试验证。

同步取得 A2A Task结果时固定返回 HTTP 200 和 `application/json`，由成功、等待输入或失败投影方法表达 Task语义；HTTP解析、适配器执行和 SDK调用异常仍按第 10 节返回对应非 2xx status。

### 5.3 流式事件转换

```java
Object fromA2AStreamEvent(
    StreamingEventKind event,
    Context context
);

Object fromA2AStreamFailure(
    StreamingEventKind event,
    Context context
);
```

可能收到的主要事件包括：

| 类型 | 语义 |
| --- | --- |
| `Task` | Task 快照 |
| `TaskStatusUpdateEvent` | `SUBMITTED`、`WORKING`、`INPUT_REQUIRED`、终态等状态变化 |
| `TaskArtifactUpdateEvent` | 增量或最终 artifact |
| `Message` | SDK允许的消息事件 |

adapter 返回单帧 SSE data body。框架不把 A2A 事件预先转换为 `QueryChunk`，也不固定客户 event 名称；首版统一输出未命名 `data:` 帧。

框架在调用 adapter 前识别失败状态事件。`FAILED`、`REJECTED` 或 `CANCELED` 不进入 `fromA2AStreamEvent(...)`，而进入 `fromA2AStreamFailure(...)`；失败事件写出后立即按第 3.8 节结束当前流。`INPUT_REQUIRED` 仍是可恢复的正常状态事件。与同步分支相同，框架不反解析 adapter 的任意返回对象；失败 data 的业务语义由 adapter 合规测试保证。

### 5.4 错误转换

```java
Object fromError(
    int httpStatus,
    String errorCode,
    String errorMessage,
    Context context,
    long executionTimeMs
);
```

`errorCode` 用于区分解析错误、conversation冲突、adapter错误和 A2A SDK错误。`errorMessage` 必须脱敏，不直接回显异常堆栈或认证信息。

若 `fromError(...)` 返回 null或抛出异常，框架使用固定结构兜底。以下以 400 `invalid_json` 为例：

```json
{
  "type": "error",
  "status": 400,
  "code": "invalid_json",
  "error": "invalid JSON"
}
```

兜底只替换 adapter 输出，不改变原错误分支已经确定的 HTTP status和错误分类；若原错误信息本身不可安全输出，`error` 固定为 `internal error`。

### 5.5 输出控制边界

| 输出项 | owner | adapter 是否可自定义 |
| --- | --- | --- |
| JSON/SSE data body | `CustomRestProtocolAdapter` | 是 |
| HTTP status | custom-rest framework | 否；按统一错误分类设置 |
| Content-Type / SSE headers | custom-rest framework | 否 |
| SSE event name/id/retry | custom-rest framework | 否；首版只输出 data |
| 任意响应 header | 宿主 filter/gateway | 否 |

---

## 6. 模块结构（Development View）

### 6.1 代码结构

```text
agent-service-app-custom-rest
|-- pom.xml
|-- src/main/java/com/openjiuwen/service/app/customrest
|   |-- CustomRestProtocolAdapter.java
|   |-- CustomRestConversationCoordinator.java
|   |-- CustomRestA2ATaskResolver.java
|   |-- CustomRestReservationReconciler.java
|   |-- CustomRestHttpHandler.java
|   |-- CustomRestStreamBridge.java
|   |-- CustomRestProperties.java
|   `-- CustomRestAutoConfiguration.java
`-- src/main/resources/META-INF/spring
    `-- org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

| 文件 | 职责 |
| --- | --- |
| `CustomRestProtocolAdapter` | A2A 入站和出站业务 SPI，内嵌 `Context`、`A2ASendCommand` |
| `CustomRestConversationCoordinator` | 按 `(tenantId, externalContextId)` 原子保留 Task解析与 RequestHandler启动权，管理 reservation 生命周期 |
| `CustomRestA2ATaskResolver` | 按 contextId 查询正式 Task 并决定创建、续轮或冲突 |
| `CustomRestReservationReconciler` | 对账 `UNCERTAIN` reservation；只在 Task已可见或执行已确定终止时安全清理，并输出结构化 ERROR诊断 |
| `CustomRestHttpHandler` | 构造只读 HTTP Context、调用 adapter/coordinator，并返回同步 JSON 或启动 SSE bridge |
| `CustomRestStreamBridge` | 单次订阅 `Flow.Publisher`，管理事件投影、SSE写出、终止、断连和 reservation安全释放 |
| `CustomRestProperties` | 绑定和校验 `query-path` |
| `CustomRestAutoConfiguration` | 条件装配、依赖校验和动态 mapping注册，不承载请求处理逻辑 |

coordinator 与 resolver 都是扩展内部组件：coordinator 负责 reservation 和调用顺序，resolver 只负责无副作用的候选分类与参数重建，二者都不对业务侧公开扩展点。

### 6.2 Maven 依赖

| 依赖 | scope | 用途 |
| --- | --- | --- |
| `a2a-java-sdk-spec` | compile | `MessageSendParams`、`EventKind`、`StreamingEventKind`、Task DTO |
| `a2a-java-sdk-server-common` | compile | `RequestHandler`、`TaskStore`、`ServerCallContext` |
| `spring-boot-autoconfigure` | optional | auto-configuration |
| `spring-webmvc` | compile | Servlet MVC、`RequestMappingHandlerMapping`、`SseEmitter` |
| `jackson-databind` | compile | body解析和客户响应序列化 |
| `slf4j-api` | compile | 日志 |
| Spring Boot test 依赖 | test | 单元和集成测试 |

版本必须与宿主 runtime 使用的 A2A SDK版本一致。扩展不依赖 runtime 的 controller 或 orchestrator具体实现类。

### 6.3 静态关系

```text
CustomRestAutoConfiguration
  -> CustomRestProperties
  -> CustomRestProtocolAdapter
  -> CustomRestHttpHandler
  -> CustomRestConversationCoordinator
  -> CustomRestA2ATaskResolver
  -> RequestHandler
  -> TaskStore
  -> ObjectMapper
  -> RequestMappingHandlerMapping

CustomRestA2ATaskResolver
  -> TaskStore
  -> MessageSendParams / Task / TaskState

CustomRestConversationCoordinator
  -> CustomRestA2ATaskResolver
  -> RequestHandler
  -> CustomRestStreamBridge
  -> CustomRestReservationReconciler
  -> ConcurrentHashMap<ConversationKey, Reservation>
```

---

## 7. 运行流程（Process View）

### 7.1 同步发送

```text
Client
  -> POST {query-path}
  -> Context
  -> adapter.toA2ARequest
  -> resolver按 conversationId解析 Task
  -> ServerCallContext state[tenantId]
  -> RequestHandler.onMessageSend
  -> EventKind校验为 Task
  -> adapter.fromA2AResponse
  -> HTTP 200 application/json
```

若本轮返回 `INPUT_REQUIRED`，正式 Task保存在 TaskStore。下一轮相同 tenant和 conversationId进入 resolver 时，框架自动注入该 Task.id并恢复同一正式 Task。

### 7.2 流式发送

```text
Client
  -> POST {query-path}
  -> adapter.toA2ARequest
  -> resolver
  -> ServerCallContext state[tenantId]
  -> RequestHandler.onMessageSendStream
  -> Flow.Publisher<StreamingEventKind>
  -> adapter.fromA2AStreamEvent(event) / fromA2AStreamFailure(event)
  -> SSE data: <custom json>
  -> interrupted/终态事件或 publisher terminal -> emitter complete
```

`INPUT_REQUIRED` 由 A2A `TaskStatusUpdateEvent` 表达，是正常任务状态，不作为 HTTP错误处理。该事件写出后框架结束本次流，下一轮由 resolver 恢复。

### 7.3 新建与续轮示例

```text
第一轮 conversationId=C1
  -> 无正式活跃 Task
  -> SDK创建 Task=T1
  -> T1状态 INPUT_REQUIRED

第二轮 conversationId=C1，客户仍不传 taskId
  -> resolver找到唯一 T1(INPUT_REQUIRED)
  -> 自动注入 taskId=T1
  -> RequestHandler恢复 T1

T1完成后再次请求 conversationId=C1
  -> resolver忽略终态 T1
  -> SDK创建新 Task=T2
  -> Agent session仍可继续使用 conversationId=C1
```

---

## 8. 配置模型（Physical View）

### 8.1 配置示例

```yaml
openjiuwen:
  service:
    custom-rest:
      query-path: /custom/{session_key}
```

业务侧声明：

```java
@Bean
CustomRestProtocolAdapter customRestProtocolAdapter() {
    return new MyCustomRestProtocolAdapter();
}
```

### 8.2 配置属性表

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.service.custom-rest.query-path` | String | 空 | Custom REST POST路径模板；存在即启用 |

### 8.3 启用条件

1. 当前应用是 Servlet WebApplication。
2. classpath 中存在 Spring MVC 和 A2A SDK server类型。
3. 已配置合法 `query-path`。
4. 容器中存在且仅存在一个 `CustomRestProtocolAdapter`。
5. 容器中存在 `RequestHandler`、`TaskStore` 和 `ObjectMapper`。

`RequestHandler` 和 `TaskStore` 是该方案的必要执行依赖；缺少任一依赖都在启动期失败，不在请求期回退为降级执行路径。

---

## 9. 对外呈现 / 用户场景（Scenario View）

### 9.1 外部接口

| 端点/API | 方法 | 说明 |
| --- | --- | --- |
| `{query-path}` | POST | Custom REST 同步或 SSE A2A发送/续轮入口 |
| `CustomRestProtocolAdapter` | Java SPI | 自定义 HTTP与 A2A Java DTO双向转换 |

### 9.2 典型接入场景

1. 宿主应用引入 runtime `agent-service-app` 和 custom-rest扩展。
2. 业务声明唯一 adapter bean。
3. 配置 query path。
4. adapter 将客户 conversation字段映射为 A2A `Message.contextId`。
5. 框架自动解析正式 Task 并调用 `RequestHandler`。
6. adapter 将 A2A Task或流式事件包装成客户协议。

### 9.3 用户可见边界

- 客户可以自定义 URL、请求字段、A2A message parts、响应 body 和 SSE data 信封。
- 客户只需要稳定传递 conversationId，不需要保存或回传 taskId。
- 同一个 `(tenant, conversationId)` 同一时刻只允许一个正式非终态 Task。
- 客户通过该入口可以获得正式 A2A Task语义，但首版不额外提供 GetTask、CancelTask和 SubscribeToTask自定义 URL。
- `/a2a` 仍是 runtime-to-runtime 的标准 JSON-RPC入口；Custom REST不是新的 runtime 间标准协议。

---

## 10. 错误处理（Process View）

| 错误场景 | HTTP status | errorCode | 行为 |
| --- | --- | --- | --- |
| media type不支持 | 415 | `unsupported_media_type` | 不调用 adapter入站方法和 RequestHandler |
| JSON解析失败 | 400 | `invalid_json` | 不调用 RequestHandler |
| contextId缺失 | 400 | `conversation_id_required` | 不查询 TaskStore |
| adapter command非法 | 500 | `adapter_execution_failed` | 记录完整异常，对外脱敏 |
| adapter提供 taskId | 500 | `adapter_task_id_not_allowed` | 防止绕过 conversation续轮规则 |
| conversation存在运行中 Task | 409 | `conversation_busy` | 不并发创建 Task |
| conversation存在不可自动续轮的非终态 Task | 409 | `conversation_not_resumable` | 不创建新 Task，也不猜测恢复策略 |
| 多个非终态正式 Task | 409 | `ambiguous_active_task` | 不任意选择 |
| TaskStore查询失败 | 500 | `task_resolution_failed` | 不调用 RequestHandler |
| TaskStore返回结构不完整或 context不匹配 | 500 | `task_resolution_failed` | fail-closed，不忽略可疑 Task后创建新 Task |
| A2A SDK参数/Task错误 | `A2AErrorCodes.fromCode(code).httpCode()`；未知 code 为500 | `a2a_<code>` | 保留 SDK错误码和官方 HTTP映射，消息脱敏 |
| 同步 handler返回非 Task结果 | 502 | `a2a_-32006` | 按 `INVALID_AGENT_RESPONSE` 处理，不调用成功投影 |
| 同步 Task进入 `FAILED`/`REJECTED`/`CANCELED` | 200 | 由 Task状态决定 | 调用 `fromA2ATaskFailure`，不得进入成功投影 |
| 同步输出包装失败 | 500 | `adapter_execution_failed` | 使用 `fromError` 或兜底 body |
| 流式 adapter包装失败 | SSE已建立 | `adapter_execution_failed` | 最多输出一帧错误 data后结束 |
| publisher terminal error | SSE已建立 | `a2a_stream_failed` | 最多输出一帧错误 data后结束 |
| 客户端断连 | 无新增响应 | 无 | 正式 Task已可观察时取消 subscription；首个 Task事件前按 3.4 节最小化消费至可安全释放，不取消整个 Task |

`executionTimeMs` 从 custom-rest handler方法进入后使用单调时钟计时。同步成功和所有同步失败分支使用同一口径；流式事件不逐帧携带统一耗时，流式错误可以携带截至错误发生时的耗时。

---

## 11. 测试与验收

### 11.1 单元测试

| 测试类 | 覆盖点 |
| --- | --- |
| `CustomRestPropertiesTest` | query-path缺失、绝对路径校验 |
| `CustomRestProtocolAdapterTest` | Context顶层集合防御性复制、A2ASendCommand tenant契约、同步/流式 Task失败投影及失败信封合规性 |
| `CustomRestA2ATaskResolverTest` | 新建、唯一 INPUT_REQUIRED续轮、终态忽略、非法 Task结果、多活跃冲突、internalContextId租户隔离、tenant查询参数、跨页候选收集 |
| `CustomRestConversationCoordinatorTest` | 同键 `putIfAbsent` 只有一个请求进入解析/启动窗口、其他请求返回409、token条件删除、同步异常安全释放或转 UNCERTAIN、流式首事件持久化后释放、不同键可并行 |
| `CustomRestReservationReconcilerTest` | 可见活跃 Task后清理、已终止且无 Task后清理、无法证明时保持 fail-closed、迟到 token不删除后继 reservation |
| `CustomRestStreamBridgeTest` | 单次订阅、首事件持久化确认、跨线程 token释放、终态/中断结束、首事件前断连最小化消费、UNCERTAIN转移、重复 terminal幂等、写失败取消 |

### 11.2 集成测试

| 场景 | 验收点 |
| --- | --- |
| 同步首轮 | adapter产出 A2A请求，RequestHandler创建正式 Task，Task结果经 adapter包装；非 Task EventKind返回502 |
| 流式首轮 | 原始 A2A Task/Status/Artifact事件逐帧进入 adapter |
| conversation续轮 | 第二轮不传 taskId，框架恢复同一 INPUT_REQUIRED Task |
| 终态后新轮 | 同 conversation在原 Task完成后创建新 Task |
| conversation busy | `WORKING`/`SUBMITTED` 时返回 409 |
| 多活跃冲突 | 不按列表顺序选择，返回 409 |
| 并发首轮 | 同一 `(tenantId, externalContextId)` 的两个并发首轮最多创建一个正式 Task |
| 跨租户同 conversation | 两个 tenant使用相同外部 conversationId时生成不同 internalContextId，互不可见 |
| adapter和 A2A错误 | 错误分类、脱敏、兜底 body正确 |
| 同步 Task失败 | `FAILED`/`REJECTED`/`CANCELED` 只进入失败投影，不进入成功投影 |
| 流式 Task失败 | 失败状态事件只进入流式失败投影，写出一帧后结束流 |
| SSE终止 | interrupted状态或终态事件写出后取消 subscription并结束 emitter |
| SSE断连 | 首个 Task可观察后取消 subscription；首事件前继续最小化消费至安全释放，不调用 Task取消 |

### 11.3 宿主回归断言

- runtime 既有 mapping 保持原有行为，不应用 Custom REST resolver。
- Custom REST mapping与内置 mapping共存。
- Custom REST 同步和流式请求均经过 `RequestHandler`。
- 不修改 `agent-runtime-java` 源码即可完成首版集成。

---

## 12. 限制与待补

| 限制 | 影响 | 首版处理 |
| --- | --- | --- |
| 单 adapter、单 path | 不能同时挂多个客户协议 | 多协议部署多个实例或后续扩展路由 |
| 只支持 Servlet MVC | WebFlux不自动注册 | 首版使用 MVC |
| conversation只允许一个非终态正式 Task | 无 taskId客户无法区分同 context多 Task | coordinator按 `(tenantId, externalContextId)` 使用原子 reservation；busy/歧义返回409 |
| `TaskStore.list(ListTasksParams)` 可能扫描 Task | Redis Task量大时查询成本上升 | 首版按 internalContextId + tenant 分页复用现有能力；规模明确后增加 activeTaskId索引 |
| 单活 JVM互斥 | 本地 reservation不能保护共享 TaskStore的多副本并发 | 首版禁止同一逻辑 runtime active-active；多副本前先实现分布式 CAS索引 |
| 不确定启动 fail-closed | 极端 SDK/存储故障可能使单个 conversation持续返回409 | 后台对账只在安全条件下清理；持续未决输出结构化 ERROR，重启宿主后由 TaskStore事实重新解析 |
| 不提供 Get/Cancel/Subscribe自定义端点 | 客户无法通过 Custom REST主动查询、取消或重订阅 | 有明确需求后单独设计，不扩展当前发送 SPI |
| adapter只控制 body | 不能自定义 status/header/media type/SSE id/retry | 由框架和宿主 filter/gateway控制 |
| 不支持 multipart/file | 文件类协议不能直接接入 | 后续独立设计 |
| 不新建认证授权体系 | 依赖宿主网关/filter | 身份字段不得从不可信 body伪造 |
| 可信身份不由本扩展认证 | adapter 可读取 header/body，但框架无法判断字段是否经过网关认证 | adapter 按宿主安全策略解析 tenant；框架同时写入 `params.tenant` 与 `ServerCallContext.state[tenantId]`，其他一般业务上下文通过 A2A request metadata 传递 |

---

## 13. 实施结论

刷新后的目标边界是：

```text
YAML只配置一个 POST path；
Java SPI将 Custom REST转换为 A2A MessageSendParams；
扩展按 conversationId自动解析正式 Task；
RequestHandler负责 A2A Task生命周期；
adapter包装原始 A2A同步结果和流式事件；
agent-runtime-java源码不修改。
```

该方案同时满足两项要求：对内真正进入 A2A `RequestHandler`/TaskStore/EventBus执行链，对外继续保持客户只传 conversationId、不感知 taskId的调用方式。conversationId自动续轮是 Custom REST专属策略，不改变 runtime 既有入口行为。
