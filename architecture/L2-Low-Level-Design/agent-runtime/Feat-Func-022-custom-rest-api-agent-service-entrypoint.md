---
level: L2-LLD
module: agent-runtime-ext-java
feature_type: functional
feature_id: Feat-Func-022
status: active
dependency:
  - ../../../version-scope/FEAT-022-custom-rest-api-agent-service-entrypoint.md
  - ../../L1-High-Level-Design/agent-runtime/api-appendix.md
  - Feat-Func-001-standardized-agent-service-entrypoint.md
---

# Custom REST API 到 A2A Task 执行入口适配 SPI 设计说明

> runtime 设计基线：`openJiuwen/agent-runtime-java`，A2A SDK `1.0.0.Final`
>
> 实现仓库：`openJiuwen/agent-solution`
>
> 目标模块：`common/agent-runtime-ext-java/agent-service-app/agent-service-app-custom-rest`
>
> example：`common/example/agentcore-ext-remote-a2a-tool-demo/agent-a-deepagent-runtime`
>
> 最后更新：2026-07-22

本文按当前实现反向维护。Custom REST 已落地在 `agent-solution` 的 runtime 扩展模块中；本文描述当前代码事实、对外契约、运行限制和仍需由上游 runtime 补齐的 tenant 传播门禁，不再描述待实现的候选结构。

---

## 1. 概述

### 1.1 特性定位

Custom REST 是 hosted Agent 的可配置 HTTP 边缘入口。宿主通过一个 Java SPI 把客户自定义 JSON 请求转换为 A2A Java DTO，扩展再直接调用 runtime 已装配的 `RequestHandler`，从而复用正式 A2A Task、TaskStore、EventBus 和执行器链路。

- **解决的问题**：在不要求客户使用 A2A JSON-RPC envelope、`taskId` 或固定 URL 的前提下，把客户协议请求接入正式 A2A Task 执行链，并支持基于业务 `conversationId` 的多轮恢复。
- **适用场景**：宿主需要一个固定的自定义 POST path、客户 JSON/JSON-SSE 信封和单一业务 adapter，同时仍希望 Task 由 runtime 统一创建、推进和持久化。
- **不适用场景**：需要多 path 动态路由、GET/cancel/subscribe/webhook、跨实例强一致并发创建或绕过 A2A Task 的独立执行入口。

当前执行链如下：

```text
Custom REST POST
  -> CustomRestAutoConfiguration.CustomRestHandler
  -> CustomRestProtocolAdapter.toA2ARequest
  -> CustomRestA2ABridge
       -> internalContextId / reservation / CustomRestA2ATaskResolver
  -> RequestHandler.onMessageSend / onMessageSendStream
  -> TaskStore / EventBus / QueueManager
  -> A2AAgentExecutor -> AgentRuntimeHandler
  -> Task / StreamingEventKind
  -> CustomRestProtocolAdapter
  -> Custom JSON / SSE data
```

### 1.2 核心设计原则

1. **执行链复用** — Custom REST 只增加 HTTP 收集、协议适配和 Task 寻址，正式执行始终进入 runtime 的 `RequestHandler`。
2. **Task 单一事实源** — 扩展只读 `TaskStore.list/get`，不预创建、不保存、不删除 Task，也不维护第二套 run/job 状态机。
3. **客户无需感知 taskId** — adapter 只需返回稳定 `conversationId`；未显式设置 `message.taskId` 时由 resolver 选择唯一可续轮正式 Task。
4. **并发失败关闭** — 单 JVM reservation 覆盖“查询无 Task 到首个正式 Task 可观察”的竞态；无法确认安全时不提前释放。
5. **业务映射归属 adapter** — tenant 来源、客户字段校验、同步响应和 SSE event/data 信封由宿主 adapter 决定；framework 只掌握 HTTP 状态、A2A 状态和连接终止语义。

### 1.3 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| 可配置 Custom REST POST | 在 Servlet MVC 应用中注册一个属性驱动的 POST mapping | `CustomRestAutoConfiguration.CustomRestHandler` | ✅ |
| 客户协议 SPI | 完成客户 HTTP 与 A2A DTO/客户响应之间的双向映射 | `CustomRestProtocolAdapter` | ✅ |
| conversation 自动续轮 | 以内部 context 查询并恢复唯一 `INPUT_REQUIRED` 正式父 Task | `CustomRestA2ATaskResolver` | ✅ |
| A2A 执行桥接 | 重建 framework-owned 字段并调用阻塞或流式 `RequestHandler` | `CustomRestA2ABridge` | ✅ |
| Servlet SSE 传输 | 执行单次订阅、逐帧背压、终止、断连和安全错误兜底 | `CustomRestSseTransport` | ✅ |
| tenant 隔离与传播 | solution 侧寻址和 A2A 标准字段已实现；下游 `ServeRequest` 传播仍依赖 runtime 修复 | `MessageSendParams.tenant` | ⚠️ |
| Agent A example | 注册业务 adapter，验证自定义 URL 与既有 `/a2a/`、`/v1/query` 共存 | `CustomRestDemoAdapter` | ✅ |

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| 单一可配置 POST path | ✅ | 配置 `openjiuwen.service.custom-rest.query-path` 后注册一个 Spring MVC mapping |
| JSON object 请求 | ✅ | 非空 body 要求 JSON media type且根节点为 object；空 body 作为空 object |
| HTTP Context 快照 | ✅ | 收集小写多值 headers、path variables、多值 query parameters 和 JSON body |
| 阻塞 JSON 响应 | ✅ | 调用 `RequestHandler.onMessageSend`，只接受 `Task` 结果并交给 adapter 投影 |
| SSE 流式响应 | ✅ | 调用 `RequestHandler.onMessageSendStream`，逐帧投影并输出 `text/event-stream` |
| conversationId 隔离 | ✅ | `(tenant, conversationId)` 经带长度帧的 SHA-256 编码生成不透明 internal contextId |
| 自动创建/续轮 | ✅ | 无活跃正式 Task 时新建；唯一 `INPUT_REQUIRED` 正式 Task 时恢复 |
| 显式 taskId | ✅ | adapter 设置的 `message.taskId`优先，跳过resolver并交由 runtime 校验 |
| shadow Task 排除 | ✅ | id 为 `shadow:`前缀且 history 为空的已知辅助 Task 不参与正式父 Task 选择 |
| 单 JVM conversation 互斥 | ✅ | 同一 internal context 在解析到首次可观察事件窗口内只允许一个请求 |
| framework错误投影 | ✅ | framework内部失败经`CustomRestError`交给adapter映射；adapter自身异常不由bridge包装 |
| 错误兜底 | ✅ | adapter错误投影返回null或结果不可序列化时使用framework固定信封；投影抛错不在bridge内吞掉 |
| 非空 tenant 下游传播 | ⚠️ | bridge 原样设置 `MessageSendParams.tenant`，但当前 runtime 尚未传播到 `ServeRequest.tenantId` |

### 2.2 显式排除

| 排除项 | 原因 | 替代（如有） |
|--------|------|------------|
| 修改既有 `/a2a/` 或 `/v1/query` | 本特性是新增旁路 HTTP ingress，不改变存量入口语义 | 继续使用 runtime 既有入口 |
| 进程内调用 JSON-RPC controller或本机二次HTTP | 会增加不必要的序列化和网络层，并绕远执行链 | 直接调用 `RequestHandler` |
| 直接调用 `A2AAgentExecutor` / `ServeOrchestrator` | 会跳过 SDK RequestContext、TaskManager 和标准 A2A 错误语义 | 通过 `RequestHandler` 间接执行 |
| 自建 Task 或 conversation 状态机 | 会形成第二事实源 | 正式状态只保存在 runtime TaskStore |
| 多 adapter、多 path、method DSL | 首版只服务一个宿主协议入口 | 多入口使用独立应用或后续特性 |
| Task query/cancel/subscribe/webhook | 不属于自定义 send ingress | 使用 A2A 标准 Task surface |
| 跨 JVM 原子首轮创建 | 通用 `TaskStore` 没有 context CAS 能力 | 实例亲和、单 writer或后续分布式协调器 |
| tenant 认证授权 | adapter 只负责业务映射，不能证明输入已认证 | 由宿主安全层完成认证授权 |

### 2.3 接口契约（Logical View）

#### 2.3.1 SPI / API 声明

当前唯一业务 SPI 为：

```java
/** 客户 HTTP 协议与 runtime A2A DTO之间的双向适配器。 */
public interface CustomRestProtocolAdapter {
    /** 把单次客户HTTP请求快照转换为A2A发送命令。 */
    A2ASendCommand toA2ARequest(Context context);

    /** 把阻塞A2A Task投影为客户JSON响应；不得泄露framework内部contextId。 */
    Object fromA2ATask(Task task, Context context);

    /** 把原始A2A流事件投影为一个客户SSE事件。 */
    SseEvent fromA2AStreamEvent(StreamingEventKind event, Context context);

    /** 把framework错误投影为客户同步错误body。 */
    Object fromError(CustomRestError error, Context context);

    /** 把framework错误投影为客户SSE错误事件。 */
    SseEvent fromStreamError(CustomRestError error, Context context);

    record A2ASendCommand(
        MessageSendParams params,
        String conversationId,
        boolean stream
    ) {}

    record SseEvent(String event, Object data) {}

    record Context(
        Map<String, List<String>> headers,
        Map<String, String> pathVariables,
        Map<String, List<String>> queryParams,
        Map<String, Object> body
    ) {}

    record CustomRestError(int httpStatus, String code, String message) {}
}
```

SPI不定义客户请求异常。客户字段解释与业务校验归宿主adapter；adapter方法抛出的运行时异常不由bridge捕获或改写。framework只统一校验command/params和conversationId，其中conversationId为null或blank时返回400 `invalid_custom_request`。

#### 2.3.2 数据类型

| 类型 | 关键字段 | 含义 | 约束 |
|------|---------|------|------|
| `Context` | `headers`, `pathVariables`, `queryParams`, `body` | 单次HTTP请求的只读顶层快照 | null集合转为空；顶层map/list防御性复制；JSON嵌套对象不递归冻结 |
| `A2ASendCommand` | `params`, `conversationId`, `stream` | adapter交给framework的A2A执行命令 | command和params非null；conversationId非空；tenant读取自`params.tenant()` |
| `MessageSendParams` | `message`, `configuration`, `metadata`, `tenant` | A2A SDK标准请求参数 | message必填；其他字段按SDK nullable契约保留 |
| `Message` | `messageId`, `contextId`, `taskId`, parts等 | A2A入站消息 | framework覆盖contextId；taskId取adapter显式值或resolver结果；其余字段复制保留 |
| `SseEvent` | `event`, `data` | 客户SSE event名称和data | event允许空但禁止CR/LF/NUL；data非null、可序列化且不能是Servlet/Spring响应对象 |
| `CustomRestError` | `httpStatus`, `code`, `message` | adapter可见的脱敏framework错误 | adapter不能改变实际HTTP status或流终止时机 |

#### 2.3.3 行为承诺

- **必须**：宿主在启用path时提供且只提供一个 `CustomRestProtocolAdapter` bean。
- **必须**：adapter 返回完整合法的 `MessageSendParams`，并为 framework 提供非空业务 `conversationId`。
- **必须**：framework 以 `params.tenant()`和`conversationId`生成internal contextId，覆盖adapter message中的contextId。
- **必须**：adapter显式taskId原样保留；只有taskId为null时才调用conversation resolver。
- **必须**：`configuration`、params metadata以及message中的messageId、parts、referenceTaskIds、metadata和extensions原样保留。
- **必须**：同步和流式客户响应不得暴露framework生成的internal contextId。
- **禁止**：扩展直接save/delete Task、覆盖runtime `A2AProtocolAdapter`或通过私有metadata绕行tenant传播。
- **允许**：tenant为null、空字符串或非空字符串；framework不trim、不补默认值。
- **允许**：adapter自定义客户JSON结构、SSE event名称和data，但不能改变原始A2A状态与终止规则。
- **允许**：adapter映射或投影方法抛出运行时异常；bridge不包装为自定义请求异常，也不保证将其转换为adapter错误信封。

---

## 3. 模块结构（Development View）

### 3.1 包结构

```text
common/agent-runtime-ext-java/
└── agent-service-app/agent-service-app-custom-rest/
    ├── pom.xml
    └── src/main/
        ├── java/com/openjiuwen/service/app/custom/rest/
        │   ├── CustomRestProtocolAdapter.java    // 公共业务SPI及命令/Context/响应类型
        │   ├── CustomRestFailure.java           // 模块内部统一错误载体
        │   ├── CustomRestA2ATaskResolver.java   // 正式父Task识别与自动续轮决策
        │   ├── CustomRestA2ABridge.java         // A2A参数重建、reservation和RequestHandler调用
        │   ├── CustomRestSseTransport.java      // Flow.Publisher到Servlet SSE的传输状态机
        │   └── CustomRestAutoConfiguration.java // 条件装配及内嵌CustomRestHandler
        └── resources/META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

当前为6个生产Java文件：1个公共业务SPI、4个核心实现组件和1个内部失败载体。只有`CustomRestProtocolAdapter`是使用方API；其余类型均为包内实现。contextId编码和reservation分别内聚在resolver和bridge中，不拆成独立bean或文件。

### 3.2 核心类静态关系

```text
宿主业务代码
  └── implements CustomRestProtocolAdapter
                 ▲
                 │ injected exactly once
CustomRestAutoConfiguration
  ├── CustomRestHandler
  │     ├── CustomRestA2ABridge
  │     ├── CustomRestSseTransport
  │     └── ObjectMapper
  └── CustomRestA2ABridge
        ├── CustomRestProtocolAdapter
        ├── CustomRestA2ATaskResolver ──> TaskStore (list/get only)
        ├── RequestHandler
        └── AgentReadiness (optional)

CustomRestSseTransport ──> CustomRestA2ABridge
```

### 3.3 依赖边界

模块直接声明当前源码使用的依赖：

- `agent-service-spec`：`AgentReadiness`；
- `a2a-java-sdk-spec`：A2A DTO；
- `a2a-java-sdk-server-common`：`RequestHandler`、`TaskStore`、`ServerCallContext`；
- `a2a-java-sdk-jsonrpc-common`：`ListTasksResult`；
- Spring Boot auto-configuration、Servlet MVC、`jackson-core`和`jackson-databind`。

模块不直接依赖 `agent-service-app`。宿主应用必须单独引入runtime A2A server，再引入本扩展；这样扩展只面向公开bean类型，不把runtime实现及其传递依赖打包进自身。

---

## 4. 核心设计（Logical + Process View）

### 4.1 HTTP入口与自动装配

#### 4.1.1 装配条件

`CustomRestAutoConfiguration`使用：

```java
@AutoConfiguration
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnClass({DispatcherServlet.class, RequestHandler.class})
@ConditionalOnProperty(prefix = "openjiuwen.service.custom-rest", name = "query-path")
public class CustomRestAutoConfiguration { ... }
```

`@ConditionalOnProperty`只标在外层auto-configuration。path未配置或值为字面量`false`时不启用；其他已配置值进入`validateQueryPath`，空白或非绝对path在启动期失败。启用后容器必须能唯一解析adapter，并存在`RequestHandler`、`TaskStore`和`ObjectMapper`；缺失、多候选或mapping冲突均使启动失败。

`AgentReadiness`通过`ObjectProvider`可选注入。没有readiness bean时沿用runtime A2A入口行为；存在且未加载时，在生成internalContextId和获取reservation前返回503。

#### 4.1.2 HTTP解析

```text
HttpServletRequest
  -> headers: 名称转小写，保留多值
  -> path variables: Spring解析后的字符串map
  -> query params: 保留多值
  -> body:
       empty                         -> empty map
       non-empty + non-JSON         -> 415
       invalid JSON/non-object root -> 400
       JSON object                  -> Map<String,Object>
  -> immutable-top-level Context
```

SSE Accept判定遵守匹配项优先级：精确`text/event-stream`高于`text/*`，`text/*`高于`*/*`；同一精确度取较高q值。例如`text/event-stream;q=0, */*;q=1`由更具体的q=0决定，流式命令返回406。缺少Accept或所有值为空时允许SSE；非法Accept按不允许处理。

流式成功响应设置：

```text
Content-Type: text/event-stream
Cache-Control: no-cache, no-transform
Connection: keep-alive
X-Accel-Buffering: no
```

### 4.2 command校验与A2A参数重建

#### 4.2.1 准备顺序

```text
Context
  -> adapter.toA2ARequest
  -> command/params非null校验
  -> conversationId非空校验
  -> stream=true时校验Accept允许SSE
  -> AgentReadiness.isAgentLoaded
  -> tenant = command.params().tenant()
  -> derive internalContextId(tenant, conversationId)
  -> acquire reservation
  -> taskId为空时调用resolver
  -> rebuild Message / MessageSendParams
  -> build ServerCallContext
  -> Prepared
```

`adapter.toA2ARequest`抛出的运行时异常原样传播。command为null或params为null属于adapter实现错误，转换为500 `adapter_execution_failed`；conversationId为null或blank统一返回400 `invalid_custom_request`。校验在生成internal contextId和获取reservation之前完成。

#### 4.2.2 参数重建

```text
tenantId                 = command.params().tenant()
internalContextId        = hash(tenantId, command.conversationId())
rebuiltMessage           = Message.builder(original)
                             .contextId(internalContextId)
                             .taskId(original.taskId != null
                                 ? original.taskId
                                 : resolver.resolveTaskId(...))
                             .build()
rebuiltParams.message    = rebuiltMessage
rebuiltParams.configuration = originalParams.configuration
rebuiltParams.metadata   = originalParams.metadata
rebuiltParams.tenant     = originalParams.tenant
callContext.state["_a2a_stream"] = command.stream
```

`Message.builder(original)`保留role、parts、messageId、referenceTaskIds、message metadata和extensions。`MessageSendParams`重建时保留configuration、params metadata和tenant。framework只覆盖contextId，并在adapter未显式给出taskId时写入resolver结果。

### 4.3 conversationId与正式Task解析

#### 4.3.1 internal contextId

外部conversationId不直接写入A2A contextId，因为当前TaskStore实现不保证tenant过滤。编码为：

```text
tenantComponent =
  0x00                                      // tenant == null
  0x01 || int32be(len(tenantUtf8)) || tenantUtf8

internalContextId =
  "custom-rest:v1:" + base64urlNoPadding(
    SHA-256(
      tenantComponent ||
      int32be(len(conversationUtf8)) || conversationUtf8
    )
  )
```

null tenant和空字符串使用不同命名空间；tenant或conversation任一变化都会得到不同值；输出不包含业务原文。出站adapter负责把contextId外部化，不能向客户暴露该内部值。

#### 4.3.2 TaskStore查询

仅当原始`message.taskId == null`时执行resolver：

```text
pageToken = null
seenTokens = {}
do:
  page = taskStore.list(
      contextId=internalContextId,
      pageSize=100,
      pageToken=pageToken,
      historyLength=0,
      includeArtifacts=false,
      tenant=tenantId)
  page或重复pageToken -> task_store_unavailable
  for summary in page.tasks:
      current = taskStore.get(summary.id)
      current != null且context一致 -> candidates.add(current)
  pageToken = blankToNull(page.nextPageToken)
while pageToken != null
```

list/get抛`TaskStoreException`，或者分页返回null page、null tasks、重复page token等内部非法状态时，统一失败关闭为503 `task_store_unavailable`，不能降级成“没有Task”，否则可能重复创建正式父Task。其他未按`TaskStore`契约包装的运行时异常保持原异常传播。

#### 4.3.3 正式父Task分类

| 分类 | 判据 | 处理 |
|------|------|------|
| 正式父Task | id非null且不以`shadow:`开头 | 进入状态决策；history允许为空 |
| 已知shadow Task | id以`shadow:`开头且history为空 | 排除 |
| 已知终态非正式Task | completed/failed/canceled/rejected | 忽略 |
| 其他同context Task | `shadow:`前缀但不满足已知shadow判据，且非已知终态 | 409 `conversation_task_conflict` |

正式识别依赖Custom REST私有internal context和runtime的`shadow:`命名约定，不依赖history、message role、TaskStore返回顺序或私有metadata。当前A2A SDK由`MainEventBusProcessor`使用`initialMessage=null`持久化首轮流式事件，因此首轮正式Task可能合法地保持空history；若以history作为身份标记，SSE无法确认Task可观察并会永久保留reservation，第二轮只能得到409 `conversation_busy`。

#### 4.3.4 状态决策

| 非终态正式父Task集合 | 决策 | taskId结果/错误 |
|---------------------|------|----------------|
| 0个 | CREATE_NEW | 保持null，由RequestHandler创建新Task |
| 1个且INPUT_REQUIRED | RESUME | 写入该Task.id |
| 1个且SUBMITTED/WORKING | BUSY | 409 `conversation_busy` |
| 1个且AUTH_REQUIRED | NOT_RESUMABLE | 409 `conversation_not_resumable` |
| 1个且状态null/UNRECOGNIZED/未来未知 | CONFLICT | 409 `conversation_task_conflict` |
| 多于1个 | AMBIGUOUS | 409 `conversation_task_ambiguous` |

已知终态正式Task被过滤；同一conversation的下一次请求创建新正式Task。SDK的`UNRECOGNIZED.isFinal()`不作为忽略依据，必须失败关闭。

### 4.4 RequestHandler执行

#### 4.4.1 阻塞流程

```text
Prepared
  -> requestHandler.onMessageSend(params, callContext)
  -> result必须是Task，否则502 invalid_a2a_result
  -> finally按(key, token)释放reservation
  -> adapter.fromA2ATask(task, httpContext)
  -> 结果非null且可序列化
  -> HTTP 200 application/json
```

`Task`无论处于completed、working、input-required、failed、canceled、rejected或unrecognized，成功返回时HTTP transport status均为200并交由adapter忠实投影。`configuration.returnImmediately=true`或runtime既有等待窗口超时时可能返回working Task，本扩展不增加第二套blocking timeout。

#### 4.4.2 流式流程

```text
Prepared
  -> requestHandler.onMessageSendStream(params, callContext)
  -> publisher非null
  -> CustomRestSseTransport.connect(publisher, prepared)
  -> subscribe exactly once
       -> 未正常返回: finally abort，释放reservation并取消已建立的subscription，原异常继续传播
  -> request(1)
  -> onNext(event)
       -> 尝试确认正式父Task可观察并释放reservation
       -> adapter.fromA2AStreamEvent
       -> 校验event/data
       -> emitter.send
       -> terminal ? cancel+complete : request(1)
```

`_a2a_stream=true`既决定调用stream方法，也是当前runtime内部选择`ServeOrchestrator.streamQuery()`的必要state。只调用stream方法而不设置该state会导致外层SSE、内层非流式执行的不一致。

### 4.5 reservation与SSE生命周期

#### 4.5.1 单JVM reservation

`CustomRestA2ABridge`维护：

```text
ConcurrentHashMap<internalContextId, Object token>
```

请求在resolver之前执行`putIfAbsent`。已有reservation时返回409 `conversation_busy`；释放使用`remove(internalContextId, token)`，迟到回调不能误删后继请求的token。同步调用在`finally`释放；流式调用在正式父Task首次可观察、publisher error/complete、stream错误或subscribe未正常返回时释放。

reservation只保护：

```text
查询“无正式Task”
  -> RequestHandler生成taskId
  -> 首个正式Task/Status/Artifact事件持久化并可查询
```

它不是业务状态，不持久化，也不解决跨JVM竞态。

#### 4.5.2 流式状态与断连

| 当前状态/事件 | 行为 | reservation |
|---------------|------|-------------|
| subscribe未正常返回 | `finally`执行abort，标记终止并取消已建立的subscription；原异常传播 | 释放一次 |
| 首个携带taskId事件且正式父Task可观察 | 先释放，再投影和写帧 | 释放一次 |
| TaskStore确认抛错 | 转成stream error、取消订阅并结束 | 释放一次 |
| 普通非终止事件 | 写帧后request(1) | 保持当前状态 |
| final/interrupted事件 | 成功写帧后cancel并complete | 已确认时已释放；未确认时fail-closed保留 |
| publisher onError/onComplete | 结束流 | 释放一次 |
| 首次确认前客户端断连 | 停止写HTTP，继续最小request直到可确认或publisher terminal | 暂不释放 |
| 首次确认后客户端断连 | 取消订阅，不调用cancelTask | 已释放 |

若publisher既不产生可确认事件也不terminal，reservation持续fail-closed；当前实现不使用超时、后台reconciler或`UNCERTAIN`状态冒险释放。

#### 4.5.3 SSE投影约束

- publisher只订阅一次，并按到达顺序处理Task、Status和Artifact事件；
- 每帧完成adapter转换、可序列化检查和`emitter.send`后才请求下一帧；
- event名称允许null/空字符串，非空时禁止CR、LF和NUL；
- data必须非null且可由宿主`ObjectMapper`序列化；
- data不能直接返回`ResponseEntity`、`ResponseBodyEmitter`、`ServletResponse`或`ModelAndView`；
- `TaskStatusUpdateEvent.isFinalOrInterrupted()`或Task状态`isFinal()/isInterrupted()`决定连接终止，adapter名称不能覆盖该语义；
- 终止后迟到的`onError/onComplete`不再输出第二个错误帧。

### 4.6 runtime tenant前置契约

solution侧当前实现：

```text
command.params().tenant()
  -> internalContextId输入
  -> ListTasksParams.tenant
  -> rebuilt MessageSendParams.tenant
  -> RequestContext.getTenant()
```

当前runtime基线中的`A2AMessageContext.from(RequestContext)`未复制`ctx.getTenant()`，`A2AProtocolAdapter.toServeRequest`只尝试从未赋值的headers读取`x-tenant-id`。因此非null tenant不会进入`ServeRequest.tenantId`。

runtime目标契约为：

```text
MessageSendParams.tenant
  -> RequestContext.getTenant()
  -> A2AMessageContext.tenantId
  -> A2AProtocolAdapter.toServeRequest
  -> ServeRequest.tenantId
```

完整支持adapter提供tenant之前，runtime必须补齐该标准链路及null/空/非空契约测试。solution禁止使用metadata、thread-local或覆盖runtime bean进行私有绕行。adapter不提供tenant时保持null是合法结果。

---

## 5. 配置模型（Physical View）

### 5.1 完整配置示例

```yaml
openjiuwen:
  service:
    custom-rest:
      # 唯一Custom REST POST path，支持Spring MVC path variables
      query-path: /v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
```

### 5.2 配置属性表

| 属性路径（完整） | 类型 | 默认值 | 必填 | 说明 |
|-----------------|------|--------|------|------|
| `openjiuwen.service.custom-rest.query-path` | String | 未配置 | 启用时是 | 配置后启用唯一POST mapping；必须是非空绝对path pattern |

不提供`enabled`、method列表、多path列表或字段映射DSL。按当前`@ConditionalOnProperty`语义，属性未配置或值为字面量`false`时不启用；其他非法值在启动期由`validateQueryPath`或Spring mapping解析失败。

### 5.3 配置类

当前没有`@ConfigurationProperties`类。auto-configuration通过`@ConditionalOnProperty`判断是否启用，通过`@Value("${openjiuwen.service.custom-rest.query-path}")`读取并校验path，避免只为一个属性增加配置类。

### 5.4 部署约束

- 宿主必须先引入并装配runtime A2A server，使`RequestHandler`和`TaskStore`可用；
- 宿主再引入`agent-service-app-custom-rest`、声明一个adapter bean并配置path；
- 多副本部署必须保证同一`(tenant, conversationId)`实例亲和，或保证只有一个写实例；
- 若不能满足上述条件，必须先提供共享CAS/coordinator，不能宣称支持跨实例并发首轮。

---

## 6. 对外呈现 / 用户场景（Scenario View）

### 6.1 外部接口

| 端点 / API | 方法 | 说明 |
|-----------|------|------|
| `${openjiuwen.service.custom-rest.query-path}` | HTTP POST | 客户自定义JSON或SSE入口；实际path由宿主配置 |
| `CustomRestProtocolAdapter` | Java SPI | 宿主定义请求、成功响应和错误响应映射 |

本特性不修改runtime已有`/a2a/`、`/v1/query`、Agent Card或Task surface。

### 6.2 Agent A example

example在`DeepAgentRuntimeApplication`中显式注册bean：

```java
@Bean
CustomRestProtocolAdapter customRestProtocolAdapter(ObjectMapper objectMapper) {
    return new CustomRestDemoAdapter(objectMapper);
}
```

配置path：

```yaml
openjiuwen:
  service:
    custom-rest:
      query-path: /v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
```

example adapter执行以下映射：

- path中的`conversation_id`映射到`A2ASendCommand.conversationId`；
- body中的`input`转成一个`ROLE_USER` `TextPart`；缺失时使用空文本，非字符串值序列化为JSON文本；
- body中的`stream`决定阻塞或流式；缺失时默认`true`；
- body、headers、query和path variables写入`MessageSendParams.metadata`；
- tenant保持null；
- 同步Task和流事件中的internal contextId在出站前替换为外部conversationId；
- framework错误映射为example客户信封，SSE错误event名称为`error`。

### 6.3 用户示例

前置条件：Agent A监听18090，adapter已注册，path已配置。

#### 6.3.1 阻塞请求

```powershell
$body = @{
  agent_id = "main_planner"
  input = @{ query = "查询账户余额"; intent = "LATEST" }
  stream = $false
} | ConvertTo-Json -Depth 10

Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:18090/v1/0/agents/main_planner/conversations/sync-session-001" `
  -ContentType "application/json" `
  -Body $body
```

预期：HTTP 200、`application/json`，客户信封中的conversationId为`sync-session-001`，不出现`custom-rest:v1:`内部值。

#### 6.3.2 流式请求

```powershell
$body = @{
  agent_id = "main_planner"
  input = @{ query = "开始办理业务"; intent = "LATEST" }
  stream = $true
} | ConvertTo-Json -Depth 10

curl.exe -N `
  -X POST `
  "http://127.0.0.1:18090/v1/0/agents/main_planner/conversations/stream-session-001" `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  --data-binary $body
```

预期：HTTP 200、`text/event-stream`，逐帧输出adapter定义的event/data；final或interrupted原始A2A事件写出后连接结束。

完整三轮自定义入口、同步入口和既有入口回归步骤位于example README末尾。API Key只通过环境变量传入，不写入仓库。

### 6.4 E2E流程

```text
客户                     Custom REST扩展                runtime / TaskStore
  │                             │                              │
  │── 第一轮(conversationId) ──>│                              │
  │                             │── resolver: 无正式Task ─────>│
  │                             │── RequestHandler(taskId=null)>│
  │                             │<── 正式Task / events ────────│
  │<── JSON或SSE投影 ──────────│                              │
  │                             │                              │
  │── 第二轮(同conversationId) >│                              │
  │                             │── list/get正式Task ─────────>│
  │                             │<── 唯一INPUT_REQUIRED Task ─│
  │                             │── RequestHandler(taskId=id) >│
  │                             │<── 恢复后的Task / events ───│
  │<── JSON或SSE投影 ──────────│                              │
```

已终态正式Task不会恢复；后续同conversation请求创建新正式Task。SUBMITTED/WORKING、AUTH_REQUIRED、未知状态或歧义不会猜测恢复目标，而是返回稳定409错误。

---

## 7. 错误处理（Process View）

| 错误场景 | 触发条件 | 系统行为 | 对外结果 |
|---------|---------|---------|---------|
| Content-Type不支持 | 非空body但没有JSON media type | 不调用adapter | HTTP 415 `unsupported_media_type` |
| JSON非法 | 语法错误或根节点非object | 不调用adapter | HTTP 400 `invalid_json` |
| SSE不可接受 | command.stream=true且Accept不允许SSE | 不获取reservation | HTTP 406 `stream_not_acceptable` |
| adapter请求映射异常 | `toA2ARequest`抛运行时异常 | bridge不捕获、不包装 | 原异常进入Servlet/Spring异常处理 |
| command无效 | command或params为null | 转为framework内部失败 | HTTP 500 `adapter_execution_failed` |
| conversationId缺失 | null或blank | framework统一拒绝 | HTTP 400 `invalid_custom_request` |
| Agent未就绪 | readiness bean存在且返回false | 不生成context、不获取reservation、不创建Task | HTTP 503 `agent_not_ready` |
| conversation忙 | 本地reservation已存在或正式Task为SUBMITTED/WORKING | 失败关闭 | HTTP 409 `conversation_busy` |
| 不可自动续轮 | 正式Task为AUTH_REQUIRED | 不按普通输入恢复 | HTTP 409 `conversation_not_resumable` |
| Task歧义 | 多个非终态正式Task | 不按顺序或时间猜测 | HTTP 409 `conversation_task_ambiguous` |
| Task冲突 | 未知同context Task、未知状态或空状态 | 不创建新父Task | HTTP 409 `conversation_task_conflict` |
| TaskStore不可用 | list/get抛`TaskStoreException`、null page/tasks或重复page token | 不降级为空结果 | HTTP 503或SSE错误 `task_store_unavailable` |
| A2A协议错误 | RequestHandler抛`A2AError` | 按`A2AErrorCodes.httpCode`映射，未知code为500 | `a2a_<numeric-code>` |
| 阻塞结果非Task | onMessageSend返回其他EventKind | 拒绝投影 | HTTP 502 `invalid_a2a_result` |
| 同步成功投影无效 | adapter返回null或结果不可序列化 | 调用错误投影，必要时固定fallback | HTTP 500 `adapter_execution_failed` |
| 同步adapter投影抛错 | `fromA2ATask`或`fromError`抛运行时异常 | bridge不吞掉异常 | 原异常进入Servlet/Spring异常处理 |
| 流式事件投影无效 | adapter返回null、非法event/data或不可序列化 | 最多投影并发送一个安全error event | SSE error后结束 |
| 流式adapter错误投影抛错 | `fromStreamError`抛运行时异常 | 不再尝试第二次投影，`completeWithError` | SSE连接异常结束，可能没有error帧 |
| stream启动失败 | `onMessageSendStream`抛错或返回null publisher | 释放reservation；A2A错误按协议映射，其他运行时异常脱敏 | HTTP错误 |
| subscribe失败 | `publisher.subscribe`未正常返回 | `finally`释放reservation、终止subscriber并取消subscription | 原异常进入Servlet/Spring异常处理 |
| publisher异步失败 | publisher调用`onError` | 释放reservation并投影stream error | SSE error后结束 |
| 客户端断连 | Servlet emitter completion/timeout/error | 停止写下游；按首次可观察状态决定继续或取消上游 | 不新增响应，不自动取消正式Task |

同步`fromError`或流式`fromStreamError`返回null，以及错误投影结果不可序列化或不满足SSE event约束时，framework使用固定信封：

```json
{
  "error": {
    "code": "<stable-code>",
    "message": "<sanitized-message>"
  }
}
```

SSE fallback event名称固定为`error`。adapter错误投影自身抛错时不使用固定信封：同步异常原样离开handler，流式异常交给`SseEmitter.completeWithError`。adapter只能控制body/event内容，不能覆盖实际HTTP status、原始Task状态或连接终止时机。

---

## 8. 限制与待补

### 8.1 已知限制

| 限制 | 影响范围 | 临时方案（如有） |
|------|---------|----------------|
| runtime未传播非null tenant到ServeRequest | adapter提供tenant时，下游`ServeRequest.tenantId`仍为null | 合入runtime标准传播修复；solution不绕行 |
| reservation仅限单JVM | 多实例可能同时查询无Task并各自创建父Task | 同会话实例亲和或单writer |
| 正式Task识别依赖`shadow:`命名约定 | 同一私有context若新增非`shadow:`前缀辅助Task，会被视为正式父Task | runtime新增辅助Task时必须沿用shadow命名空间或同步更新classifier |
| RedisTaskStore list为全量scan | Task规模较大时查询成本上升 | 上线前压测；规模化前由runtime增加context索引 |
| TaskStore生命周期有限 | InMemory重启或Redis TTL后旧Task不可续 | 接受创建新Task或使用满足生命周期要求的TaskStore |
| stream无可确认事件且不terminal | reservation持续fail-closed，同conversation后续请求409 | 进程重启后按TaskStore事实重建；不使用不安全超时 |
| 多副本无分布式CAS | 无法保证跨实例首轮至多创建一个Task | 增加共享coordinator/CAS后再开放 |
| 非正式已终态Task忽略时无诊断日志 | 不影响resolver结果，但内部可诊断性有限 | 后续在不记录业务标识的前提下补充日志 |
| 通用请求幂等未实现 | conversation互斥不能防止客户重试重复业务输入 | 由客户协议或上层业务提供幂等键 |
| 仅一个adapter和一个POST path | 不能在单应用中声明多套客户协议 | 拆分应用或后续扩展能力 |

### 8.2 当前测试事实

当前Custom REST模块有34个单元测试，覆盖：

- Context顶层防御性复制和adapter运行时异常透传；
- internal contextId稳定、隔离和不透明；
- TaskStore分页、list后get、空history正式Task、shadow排除、已知终态、busy/ambiguous/conflict和重复page token；
- A2A参数重建、internal context覆盖、tenant/metadata保留、阻塞释放和A2A错误脱敏；
- path条件装配、基础path校验、HTTP Context采集、Content-Type、Accept specificity和SSE响应头；
- subscribe异常透传与abort清理、TaskStore确认失败、逐帧背压、terminal、fail-closed reservation和非法SSE投影。

Agent A example有9个单元测试：其中6个adapter契约测试覆盖请求映射、stream缺失默认true、conversationId交给framework校验、input缺失转空文本以及Task/嵌套status message/stream event中的internal contextId外部化；其余3个覆盖DeepAgent装配和Agent A/B运行时隔离。

尚未自动化覆盖但仍属于发布验收关注项的高风险场景包括：完整状态决策表、同key/不同key并发、显式taskId跳过resolver、readiness不获取reservation、首事件前后断连、迟到onError/重复terminal、错误投影null/抛错/不可序列化以及多adapter/缺bean/mapping冲突启动失败。

### 8.3 验收准则

1. 首次同步或流式请求只携带conversationId时，由`RequestHandler`创建可从TaskStore查询的正式Task。
2. 正式Task变为INPUT_REQUIRED后，同tenant和conversationId的下一轮自动恢复同一Task id。
3. completed/failed/canceled/rejected后，同conversationId下一轮创建新正式Task；UNRECOGNIZED失败关闭。
4. 正式父Task和shadow Task共存时只选择正式父Task。
5. SUBMITTED/WORKING、AUTH_REQUIRED、多个正式Task和未知同context Task分别返回既定409错误。
6. 相同conversationId在不同tenant下使用不同internal context；null tenant与空字符串也不共用context。
7. 单实例同conversation并发首轮至多一个进入Task创建窗口。
8. SSE事件按序逐帧投影，final/interrupted保持原始状态并终止连接；错误最多输出一帧。
9. Custom REST mapping与`/a2a/`、`/v1/query`在同一端口共存，存量入口行为不变化。
10. 客户同步和流式响应不泄露`custom-rest:v1:`内部contextId。
11. adapter不提供tenant时保持null；非null tenant完整下游传播必须等待runtime门禁修复。
12. solution不覆盖runtime `A2AProtocolAdapter`，不建立私有tenant或Task状态旁路。

---

本实现的必要复杂度限定为一个业务SPI、一个只读resolver、一个A2A bridge和一个Servlet SSE传输状态机。contextId编码和短生命周期reservation均内聚在bridge/resolver中；未引入独立codec、coordinator bean、后台reconciler、`UNCERTAIN`持久状态、active-task私有索引或多path路由DSL。
