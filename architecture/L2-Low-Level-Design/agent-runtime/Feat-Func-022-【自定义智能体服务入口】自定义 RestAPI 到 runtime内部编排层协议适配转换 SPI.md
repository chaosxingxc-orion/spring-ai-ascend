---
level: L2-LLD
module: agent-runtime-ext-java
feature_type: functional
feature_id: Feat-Func-022
status: active
dependency:
  - ../../../version-scope/FEAT-022-custom-rest-api-to-a2a-jsonrpc-adaptation-spi.md
  - ../../L1-High-Level-Design/agent-runtime/api-appendix.md
  - Feat-Func-001-standardized-agent-service-entrypoint.md
  - openJiuwen/agent-runtime-java
---

# Custom REST API 到 A2A 执行入口适配 SPI 设计说明

> 目标仓库：`openJiuwen/agent-solution`
> 目标模块：`common/agent-runtime-ext-java/agent-service-app/agent-service-app-custom-rest`
> 最后更新：2026-07-21

说明：本文档描述独立功能特性 Feat-Func-022。它与 Feat-Func-001“标准化智能体服务入口”关联：Feat-Func-001 提供标准 `/a2a` JSON-RPC 协议表面，Feat-Func-022 提供客户自定义 REST 协议表面；二者在 HTTP 协议层相互独立，但共享 runtime 已有的 A2A `RequestHandler`、`TaskStore`、事件总线、`A2AAgentExecutor` 和内部编排链路。Feat-Func-022 的实现放在 `agent-solution` 扩展仓，首版不修改 `agent-runtime-java` 源码。

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
  -> A2AProtocolAdapter
  -> ServeRequest
  -> ServeOrchestrator
  -> EventKind / StreamingEventKind
  -> CustomRestProtocolAdapter Java SPI
  -> 自定义 REST HTTP 响应 / SSE data
```

Custom REST 客户端不需要感知或回传 A2A `taskId`。业务 adapter 将客户会话标识映射为 A2A `Message.contextId`；框架在调用 `RequestHandler` 前根据该 contextId 查找唯一可续轮的正式 Task，并在需要时自动补入 `Message.taskId`。

配置的一个自定义 path 只新增一个 mapping，不替换、不代理也不禁用既有 `/a2a`、`/v1/query` 和 `/query`。

### 1.2 当前事实依据

当前 `agent-runtime-java` 已有以下执行入口和组件：

| 组件 | 当前职责 | 本特性复用方式 |
| --- | --- | --- |
| `POST /v1/query` | `QueryMvcController` 将 `QueryRequest` 转为 `ServeRequest` 后直接调用 `ServeOrchestrator` | 不经过该 controller |
| `POST /a2a` | `A2aJsonRpcController` 解析 JSON-RPC 后调用 A2A SDK `RequestHandler` | 不经过该 controller，直接复用同一个 `RequestHandler` bean |
| `RequestHandler` | 创建/恢复正式 Task，连接 TaskStore、QueueManager 和事件总线 | Custom REST 的 A2A 执行入口 |
| `A2AAgentExecutor` | 将 A2A 请求投影到 `ServeOrchestrator`，并将输出投影为 A2A Task 事件 | 原样复用 |
| `TaskStore` | 按 taskId 保存正式 Task，也保存 orchestrator 内部 `shadow:` Task | 用于 Custom REST 的 conversationId 续轮解析 |
| `A2AEnabledServeOrchestrator` | 执行本地 Agent，并按 conversationId 管理远端 A2A delegate 的 shadow task | 作为 `A2AAgentExecutor` 下游原样复用 |

`/v1/query` 的多轮主要依赖 `ServeRequest.conversationId` 对应的 Agent session，以及 `A2AEnabledServeOrchestrator` 的内部 shadow task。正式 A2A `RequestHandler` 默认只按 `taskId` 恢复 Task，不会按 contextId 自动选择 Task。因此 Feat-Func-022 在扩展层增加只对 Custom REST 生效的 conversationId 续轮策略，不修改标准 `/a2a` 语义。

### 1.3 设计原则

1. **YAML 只描述 HTTP 暴露面**：只配置一个 query URL，首版固定 POST，不把字段映射做成 YAML DSL。
2. **转换全部由 Java SPI 实现**：客户字段到 A2A 请求、A2A 结果到客户响应均由业务 adapter 控制。
3. **正式进入 A2A 执行链**：调用 `RequestHandler`，获得正式 Task、TaskState、Artifact 和事件语义。
4. **conversationId 对客户稳定**：客户无需感知 taskId；框架按 contextId 自动解析唯一可续轮 Task。
5. **不改变标准入口**：conversationId 自动续轮只作用于 Custom REST mapping，不修改 `/a2a` 和 `/v1/query`。
6. **不做进程内协议绕行**：不调用 `A2aJsonRpcController`，不拼 JSON-RPC 字符串，不发起二次 HTTP。
7. **首版复用现有存储**：使用 `TaskStore.list(contextId)` 查找正式 Task，不新增独立 task/run/job 状态机。

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
| Java 出站转换 | 支持 | 包装 `EventKind`、`StreamingEventKind` 和错误 |
| 多 adapter 共存 | 不支持 | 首版只允许一个 path 和一个 adapter |
| GetTask/CancelTask/SubscribeToTask 自定义端点 | 不支持 | 首版只提供发送/续轮入口 |
| YAML 字段映射 DSL | 不支持 | 转换由 Java SPI 实现 |
| multipart / 文件输入 | 不支持 | 首版只解析 JSON object body |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
| --- | --- | --- |
| 调用 `A2aJsonRpcController.handleJsonRpc` | controller 是 JSON-RPC HTTP 外壳，进程内调用会重复解析和序列化 | 直接调用 `RequestHandler` |
| 构造 JSON-RPC 字符串 | adapter 已经位于 Java 对象边界，无需协议文本往返 | 构造 `MessageSendParams` |
| 直接调用 `ServeOrchestrator` | 会绕过正式 TaskStore/EventBus 生命周期，不满足“转成 A2A”目标 | 经 `RequestHandler` 和 `A2AAgentExecutor` 调用 |
| 修改 A2A SDK 按 contextId 恢复 | 会改变标准 `/a2a` 语义，并引入同 context 多 Task 歧义 | 只在 Custom REST 扩展层解析 taskId |
| 新建独立 TaskStore | 会复制正式 Task状态并产生一致性问题 | 复用 runtime 已有 `TaskStore` |
| 多路径、多操作路由 DSL | 当前需求只有一个自定义发送入口 | 单 path + 单 adapter |

### 2.3 行为承诺

- **必须**：`POST {query-path}` 被调用时，框架解析 HTTP Context 并调用 `CustomRestProtocolAdapter.toA2ARequest(...)`。
- **必须**：adapter 产出的 `Message.contextId` 非空，它是框架续轮解析使用的内部 conversationId。
- **必须**：adapter 不从客户请求映射 `taskId`；taskId 由框架解析和注入。
- **必须**：框架调用 runtime 已有 `TaskStore`，只在正式非 shadow Task 中查找续轮候选。
- **必须**：存在唯一 `INPUT_REQUIRED` 正式 Task时自动续轮；不存在可续轮 Task时创建新 Task。
- **必须**：存在 `SUBMITTED`/`WORKING` Task或多个非终态正式 Task时拒绝请求，避免错误续轮。
- **必须**：同步和流式调用分别进入 `RequestHandler.onMessageSend(...)` 和 `onMessageSendStream(...)`。
- **必须**：A2A 原始同步结果或每个原始流式事件交给 adapter 包装，框架不先转换为 `QueryResponse/QueryChunk`。
- **必须**：非空 body 只接受 `application/json` 或 `application/*+json`；非法 JSON 或非 object 根节点返回 400。
- **必须**：配置的 mapping 与 `/a2a`、`/v1/query`、`/query` 共存，不改变内置入口行为。
- **必须**：入口日志不记录认证 header 和 raw body。
- **允许**：adapter 自行决定外部字段名、消息 parts、metadata、同步响应信封和 SSE data 信封。
- **禁止**：按 TaskStore 返回顺序随意选择多个候选 Task。
- **禁止**：将 `shadow:` 开头的 orchestrator 内部 Task作为正式续轮 Task。

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
  -> CustomRestA2ATaskResolver.resolve(params)
       list TaskStore by contextId
       排除 shadow:* Task
       判断正式 Task 状态
       必要时自动注入 taskId
  -> 构建 ServerCallContext
  -> state["_a2a_stream"] = stream
  -> if stream: RequestHandler.onMessageSendStream(...)
     else: RequestHandler.onMessageSend(...)
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

A2A SDK 的标准行为是按 `Message.taskId` 恢复 Task，`Message.contextId` 只表示上下文。Custom REST 客户端不提供 taskId，因此扩展定义以下专属策略：

```text
外部 conversationId
  -> adapter 映射为 Message.contextId
  -> resolver 查询该 contextId 下的正式 Task
  -> 自动决定创建新 Task或恢复 INPUT_REQUIRED Task
```

状态决策表：

| 查询结果 | 决策 |
| --- | --- |
| 无正式非终态 Task | 保持 taskId 为空，SDK 创建新 Task |
| 唯一正式 Task为 `INPUT_REQUIRED` | 将该 Task.id 注入 `Message.taskId` 后续轮 |
| 存在正式 `SUBMITTED` 或 `WORKING` Task | 返回 409 `conversation is already running` |
| 只有 `COMPLETED/FAILED/CANCELED/REJECTED` | 不复用终态 Task，创建新 Task |
| 多个正式非终态 Task | 返回 409 `ambiguous active task` |

`AUTH_REQUIRED` 当前不作为自动续轮状态；如后续明确支持认证恢复，再单独扩展状态规则。

TaskStore 还保存 orchestrator 内部远端委托状态，其 taskId 以 `shadow:` 开头。resolver 必须先排除这些内部 Task。它们仍由 `A2AEnabledServeOrchestrator` 按 conversationId 管理，不能作为 `RequestHandler` 的外层正式 Task 恢复。

该设计形成两层互不替代的状态：

```text
外层正式 A2A Task
  -> Custom REST resolver 按 conversationId 找到并向 RequestHandler 注入 taskId

内层远端 delegate shadow task
  -> A2AEnabledServeOrchestrator 按 conversationId 保存/恢复远端 taskId
```

### 3.5 SPI 形态

```java
public interface CustomRestProtocolAdapter {
    A2ASendCommand toA2ARequest(Context context);

    Object fromA2AResponse(
        EventKind response,
        Context context,
        long executionTimeMs
    );

    Object fromA2AStreamEvent(
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

auto-configuration 继续通过 `RequestMappingHandlerMapping.registerMapping(...)` 动态注册固定 POST mapping，并使用宿主的 builder configuration。

启动期规则：

1. 未配置 `query-path`：不启用，不要求 adapter、RequestHandler 或 TaskStore。
2. 配置 path 但缺少或存在多个 adapter：启动失败。
3. path 为空、空白或不是绝对路径：启动失败。
4. 已启用但缺少 `RequestHandler` 或 `TaskStore`：启动失败，说明宿主没有完整 A2A server能力。
5. path + POST 与既有 mapping 完全冲突：由 Spring MVC 拒绝注册。

### 3.8 同步与 SSE 输出

同步模式调用 `RequestHandler.onMessageSend(...)`，返回 `EventKind`。当前 runtime 的 `A2AAgentExecutor` 会提交正式 Task，因此正常结果通常是 `Task`，但 SPI 使用接口类型以遵循 SDK 契约。

流式模式调用 `RequestHandler.onMessageSendStream(...)`，订阅 `Flow.Publisher<StreamingEventKind>`：

1. 每个事件调用 `adapter.fromA2AStreamEvent(...)`。
2. adapter 返回对象经 Jackson 序列化后作为无 event name 的 SSE `data:` 输出。
3. publisher 正常完成时结束 emitter。
4. publisher 异常时最多输出一帧 adapter 错误 data，然后结束。
5. emitter completion、timeout或写失败时取消当前 `Flow.Subscription`。
6. 客户端断连只取消当前订阅，不自动调用 `RequestHandler.onCancelTask(...)`，避免把网络断开等同于业务取消。

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
| `message.contextId` | runtime 内部 conversationId，也是续轮查询键 | 必填；可来自任意自定义字段 |
| `message.taskId` | 正式 Task 恢复标识 | adapter 必须留空，由框架注入 |
| `message.messageId` | 本轮消息标识 | adapter 按客户协议映射或生成 |
| `message.role` | A2A 消息角色 | 通常为 `ROLE_USER` |
| `message.parts` | 交给 A2A/runtime 的消息内容 | 支持 SDK 允许的 Part 类型；当前 runtime 主要消费 TextPart |
| `message.metadata` | 消息级扩展信息 | 不承载可信身份 |
| `params.metadata` | 请求级扩展信息 | 将进入现有 A2A adapter的 metadata处理 |
| `params.tenant` | A2A tenant | 按宿主租户策略设置 |
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
        .build();
    return new A2ASendCommand(params, Boolean.TRUE.equals(context.body().get("stream")));
}
```

### 4.4 conversationId 续轮解析

resolver 使用 `message.contextId` 调用：

```java
taskStore.list(ListTasksParams.builder().contextId(contextId).build())
```

然后执行：

1. 排除 taskId 为空和 taskId 以 `shadow:` 开头的内部 Task。
2. 忽略所有终态 Task。
3. 没有非终态 Task：保持 taskId 为空。
4. 唯一非终态 Task为 `INPUT_REQUIRED`：重建 `Message` 并注入该 Task.id。
5. 唯一非终态 Task为 `SUBMITTED`/`WORKING`：返回 409。
6. 多个非终态 Task：返回 409。

resolver 不修改 adapter 提供的 contextId、parts、metadata、configuration 和 tenant。

### 4.5 转换后的框架处理

| adapter/resolver结果 | 框架行为 |
| --- | --- |
| command或 params/message 为 null | 500 `adapter execution failed` |
| `message.contextId` 为空 | 400 `conversation_id is required` |
| adapter 写入非空 taskId | 500 `adapter task_id must be empty` |
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
| 同步成功 | `fromA2AResponse(...)` | `EventKind`、Context、耗时 | 自定义 JSON body Object |
| 流式事件 | `fromA2AStreamEvent(...)` | 单个 `StreamingEventKind`、Context | 自定义 SSE data Object |
| 框架或 A2A 错误 | `fromError(...)` | HTTP status、错误码、脱敏消息、Context、耗时 | 自定义错误 Object |

所有返回值必须能被 Jackson 序列化。框架不要求客户响应使用 A2A JSON-RPC envelope。

### 5.2 同步成功转换

```java
Object fromA2AResponse(
    EventKind response,
    Context context,
    long executionTimeMs
);
```

adapter 会收到 SDK 原始 `EventKind`。正常情况下通常为 `Task`，可读取：

- `Task.id`
- `Task.contextId`
- `Task.status`
- `Task.artifacts`
- `Task.history`
- `Task.metadata`

客户协议可以隐藏 taskId，只返回 conversationId 和业务结果；框架续轮不依赖客户回传 taskId。但 adapter 不应丢失客户实际需要的 Task 状态、interrupt 或 artifact 语义。

同步成功固定返回 HTTP 200 和 `application/json`。

### 5.3 流式事件转换

```java
Object fromA2AStreamEvent(
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

若 `fromError(...)` 返回 null或抛出异常，框架使用固定兜底：

```json
{
  "type": "error",
  "status": 500,
  "code": "internal_error",
  "error": "internal error"
}
```

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
|   |-- CustomRestA2ATaskResolver.java
|   |-- CustomRestProperties.java
|   `-- CustomRestAutoConfiguration.java
`-- src/main/resources/META-INF/spring
    `-- org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

| 文件 | 职责 |
| --- | --- |
| `CustomRestProtocolAdapter` | A2A 入站和出站业务 SPI，内嵌 `Context`、`A2ASendCommand` |
| `CustomRestA2ATaskResolver` | 按 contextId 查询正式 Task 并决定创建、续轮或冲突 |
| `CustomRestProperties` | 绑定和校验 `query-path` |
| `CustomRestAutoConfiguration` | 条件装配、mapping注册、HTTP解析、RequestHandler调用和 SSE桥接 |

resolver 独立成文件是为了隔离状态选择规则并进行针对性测试，不对业务侧公开扩展点。

### 6.2 Maven 依赖

| 依赖 | scope | 用途 |
| --- | --- | --- |
| `a2a-java-sdk-spec` | compile | `MessageSendParams`、`EventKind`、`StreamingEventKind`、Task DTO |
| `a2a-java-sdk-server-common` | compile | `RequestHandler`、`TaskStore`、`ServerCallContext` |
| `spring-boot-autoconfigure` | optional | auto-configuration |
| `spring-boot-starter-webmvc` | optional | Servlet MVC、`SseEmitter` |
| `jackson-databind` | compile | body解析和客户响应序列化 |
| `slf4j-api` | compile | 日志 |
| Spring Boot test 依赖 | test | 单元和集成测试 |

版本必须与宿主 runtime 使用的 A2A SDK版本一致。扩展不依赖 runtime 的 controller 或 orchestrator具体实现类。

### 6.3 静态关系

```text
CustomRestAutoConfiguration
  -> CustomRestProperties
  -> CustomRestProtocolAdapter
  -> CustomRestA2ATaskResolver
  -> RequestHandler
  -> TaskStore
  -> ObjectMapper
  -> RequestMappingHandlerMapping

CustomRestA2ATaskResolver
  -> TaskStore
  -> MessageSendParams / Task / TaskState
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
  -> ServerCallContext state[_a2a_stream]=false
  -> RequestHandler.onMessageSend
  -> EventKind（通常为 Task）
  -> adapter.fromA2AResponse
  -> HTTP 200 application/json
```

若本轮返回 `INPUT_REQUIRED`，正式 Task保存在 TaskStore。下一轮相同 conversationId进入 resolver 时，框架自动注入该 Task.id并恢复同一正式 Task。

### 7.2 流式发送

```text
Client
  -> POST {query-path}
  -> adapter.toA2ARequest
  -> resolver
  -> ServerCallContext state[_a2a_stream]=true
  -> RequestHandler.onMessageSendStream
  -> Flow.Publisher<StreamingEventKind>
  -> adapter.fromA2AStreamEvent(event)
  -> SSE data: <custom json>
  -> publisher terminal -> emitter complete
```

`INPUT_REQUIRED` 由 A2A `TaskStatusUpdateEvent` 表达，是正常任务状态，不作为 HTTP错误处理。下一轮由 resolver 恢复。

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

`RequestHandler` 和 `TaskStore` 是该方案的必要执行依赖，不再像旧的非 Task设计那样在请求期回退为 “no orchestrator” 503。

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
- 同一个 conversationId 同一时刻只允许一个正式非终态 Task。
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
| 多个非终态正式 Task | 409 | `ambiguous_active_task` | 不任意选择 |
| TaskStore查询失败 | 500 | `task_resolution_failed` | 不调用 RequestHandler |
| A2A SDK参数/Task错误 | 按统一映射 | `a2a_<code>` | 保留 SDK错误码语义，消息脱敏 |
| 同步输出包装失败 | 500 | `adapter_execution_failed` | 使用 `fromError` 或兜底 body |
| 流式 adapter包装失败 | SSE已建立 | `adapter_execution_failed` | 最多输出一帧错误 data后结束 |
| publisher terminal error | SSE已建立 | `a2a_stream_failed` | 最多输出一帧错误 data后结束 |
| 客户端断连 | 无新增响应 | 无 | 取消当前 subscription，不取消整个 Task |

`executionTimeMs` 从 custom-rest handler方法进入后使用单调时钟计时。同步成功和所有同步失败分支使用同一口径；流式事件不逐帧携带统一耗时，流式错误可以携带截至错误发生时的耗时。

---

## 11. 测试与验收

### 11.1 单元测试

| 测试类 | 覆盖点 |
| --- | --- |
| `CustomRestPropertiesTest` | query-path缺失、绝对路径校验 |
| `CustomRestProtocolAdapterTest` | Context顶层集合防御性复制、A2ASendCommand基本契约 |
| `CustomRestA2ATaskResolverTest` | 新建、唯一 INPUT_REQUIRED续轮、终态忽略、busy、多活跃冲突、shadow过滤 |

### 11.2 集成测试

| 场景 | 验收点 |
| --- | --- |
| 同步首轮 | adapter产出 A2A请求，RequestHandler创建正式 Task，结果经 adapter包装 |
| 流式首轮 | 原始 A2A Task/Status/Artifact事件逐帧进入 adapter |
| conversation续轮 | 第二轮不传 taskId，框架恢复同一 INPUT_REQUIRED Task |
| 终态后新轮 | 同 conversation在原 Task完成后创建新 Task |
| shadow共存 | 同 context存在 `shadow:` Task时不被选为正式续轮 Task |
| conversation busy | `WORKING`/`SUBMITTED` 时返回 409 |
| 多活跃冲突 | 不按列表顺序选择，返回 409 |
| adapter和 A2A错误 | 错误分类、脱敏、兜底 body正确 |
| SSE断连 | 取消当前 subscription，不调用 Task取消 |

### 11.3 宿主回归断言

- `/v1/query` 和 `/query` 保持现有 conversationId/Agent session行为。
- `/a2a` 保持标准 taskId恢复语义，不应用 Custom REST resolver。
- Custom REST mapping与内置 mapping共存。
- Custom REST 同步和流式请求均经过 `RequestHandler`。
- 不修改 `agent-runtime-java` 源码即可完成首版集成。

---

## 12. 限制与待补

| 限制 | 影响 | 首版处理 |
| --- | --- | --- |
| 单 adapter、单 path | 不能同时挂多个客户协议 | 多协议部署多个实例或后续扩展路由 |
| 只支持 Servlet MVC | WebFlux不自动注册 | 首版使用 MVC |
| conversation只允许一个非终态正式 Task | 无 taskId客户无法区分同 context多 Task | busy/歧义返回 409 |
| `TaskStore.list(contextId)` 可能扫描 Task | Redis Task量大时查询成本上升 | 首版复用现有能力；规模明确后增加 `(tenant, conversationId) -> activeTaskId` 索引 |
| shadow task与正式 Task共用 TaskStore | context查询可能同时返回内部状态 | resolver明确排除 `shadow:` taskId |
| 不提供 Get/Cancel/Subscribe自定义端点 | 客户无法通过 Custom REST主动查询、取消或重订阅 | 有明确需求后单独设计，不扩展当前发送 SPI |
| adapter只控制 body | 不能自定义 status/header/media type/SSE id/retry | 由框架和宿主 filter/gateway控制 |
| 不支持 multipart/file | 文件类协议不能直接接入 | 后续独立设计 |
| 不新建认证授权体系 | 依赖宿主网关/filter | 身份字段不得从不可信 body伪造 |
| A2A可信身份字段传递不完整 | 当前 `A2AMessageContext` 未完整承接 Custom REST header 到 `ServeRequest.userId/spaceId/tenantId` | 首版通过 A2A metadata 传递一般业务上下文；若下游必须使用这些固定字段，需要后续修改 runtime 的可信调用上下文传递 |

多租户环境下，conversation续轮的实际唯一键必须至少包含 tenant维度。若现有 `TaskStore.list` 实现不能按 tenant可靠隔离，则接入方必须保证 contextId全局唯一，或在后续索引实现中使用 `(tenantId, conversationId)` 复合键。

---

## 13. 实施结论

刷新后的目标边界是：

```text
YAML只配置一个 POST path；
Java SPI将 Custom REST转换为 A2A MessageSendParams；
扩展按 conversationId自动解析正式 Task；
RequestHandler负责 A2A Task生命周期；
adapter包装原始 A2A同步结果和流式事件；
runtime源码不修改。
```

该方案同时满足两项要求：对内真正进入 A2A `RequestHandler`/TaskStore/EventBus执行链，对外继续保持客户只传 conversationId、不感知 taskId的调用方式。conversationId自动续轮是 Custom REST专属策略，不改变标准 `/a2a` 或既有 `/v1/query` 行为。
