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
---

# Custom REST API 到 A2A Task 执行入口适配 SPI 设计说明

> runtime 设计基线：`openJiuwen/agent-runtime-java@e0e9cd2127f8b837e586b77c34fdc7890e0b0e31`，A2A SDK `1.0.0.Final`
>
> 实现仓库：`openJiuwen/agent-solution`
>
> 目标模块：`common/agent-runtime-ext-java/agent-service-app/agent-service-app-custom-rest`
>
> 最后更新：2026-07-21

本文描述 Feat-Func-022 的待落地设计。Custom REST实现放在 `agent-solution` 的 runtime扩展模块中；`agent-solution` 中已经存在的 Custom REST代码是历史方案残留，不是本文的设计依据。当前 `agent-runtime-java` 尚缺少非null `MessageSendParams.tenant -> ServeRequest.tenantId`传播，本文把该问题列为 runtime前置遗留，不在 solution中建立绕行链路。

---

## 1. 结论与范围

### 1.1 设计结论

Custom REST 是当前 hosted Agent 的一个 HTTP 边缘入口。它不调用 JSON-RPC controller，也不建立第二套执行链，而是把自定义请求转换为 A2A Java DTO，直接调用 runtime 已装配的 `RequestHandler`：

```text
Custom REST POST
  -> CustomRestProtocolAdapter
  -> conversationId 隔离编码与正式 Task 解析
  -> RequestHandler.onMessageSend / onMessageSendStream
  -> TaskStore / EventBus / QueueManager
  -> A2AAgentExecutor
  -> AgentRuntimeHandler
  -> Task / StreamingEventKind
  -> CustomRestProtocolAdapter
  -> Custom JSON / SSE data
```

Custom REST 客户端只需要提供业务 `conversationId`，不要求感知 A2A `taskId`。adapter未设置 `message.taskId`时，扩展按tenant和conversationId定位同一会话下唯一可续轮的正式父 Task：

- 没有可续轮正式 Task时，不填写 `message.taskId`，由 `RequestHandler` 创建新的正式 Task。
- 唯一正式 Task为 `INPUT_REQUIRED` 时，扩展把该 Task id写入内部 `Message.taskId`，由 `RequestHandler` 按 A2A 原生规则续轮。
- 正式 Task正在 `SUBMITTED/WORKING`、处于不可自动续轮状态或存在歧义时，拒绝本次请求，不猜测恢复目标。
- 已终态正式 Task不再续轮；同一 conversationId 的下一次请求创建新正式 Task。
- adapter显式设置 `message.taskId`时，该值优先并跳过conversation resolver，由 `RequestHandler`按标准A2A规则校验和恢复。

扩展只负责“从自定义 HTTP 请求进入正式 A2A Task”这一段。远端 Agent 调用、agent-bus、Task 查询/取消 REST surface 和已有入口均不属于本特性的设计范围。

### 1.2 首版能力

| 能力 | 首版结论 |
| --- | --- |
| adapter 数量 | 一个 |
| Custom REST path | 一个可配置 POST path |
| 请求体 | JSON object |
| 响应 | 同步 JSON 或 SSE |
| Task | 必须由 runtime `RequestHandler` 创建和推进正式 A2A Task |
| 续轮输入 | `conversationId`必填；adapter可选设置 `message.taskId`，客户无需感知该字段 |
| 续轮状态 | taskId为空时只自动续 `INPUT_REQUIRED` 正式 Task；显式taskId沿用runtime语义 |
| TaskStore 使用 | resolver 只读 list/get；扩展不 save/delete Task |
| runtime 源码 | Custom REST实现不修改；adapter提供tenant时的传播遗留需由runtime补齐 |

### 1.3 非目标

- 不修改或重新定义 runtime 既有 HTTP 入口。
- 不把 `/a2a` JSON-RPC 封装成进程内字符串调用，也不向本机发起二次 HTTP。
- 不直接调用 `A2AAgentExecutor`、`ServeOrchestrator`、`AgentRuntimeHandler`。
- 不新增独立于 A2A Task 的 run/job/conversation 状态机。
- 不设计远端 Agent 调用链；但 resolver 必须避免把该链产生的辅助 Task误认为正式父 Task。
- 不支持多个 adapter、多个兼容 path、GET、cancel、subscribe、webhook、multipart 或多 Agent 路由。
- 不以 `agent-solution` 现存历史代码中的私有字段、私有 Task 前缀或状态机作为契约。

---

## 2. runtime 代码事实与设计约束

设计以 `openJiuwen/agent-runtime-java` 当前代码为事实源。

主要核验文件位于 `service/agent-service-app`：`A2aJsonRpcController`、`A2AAutoConfiguration`、`A2AAgentExecutor`、`A2AMessageContext`、`A2AProtocolAdapter`、`A2AEnabledServeOrchestrator`、`RedisTaskStore` 和 `WriteThrottlingTaskStore`；同时核验 A2A SDK `DefaultRequestHandler`、`TaskManager`、`MainEventBusProcessor` 与 `InMemoryTaskStore` 的 `1.0.0.Final` 源码。

### 2.1 可复用入口

| runtime 组件 | 当前行为 | 本特性用法 |
| --- | --- | --- |
| `A2aJsonRpcController` | JSON-RPC `SendMessage`/`SendStreamingMessage` 最终调用 `RequestHandler` | 仅作行为参照；Custom REST 不经过该 controller |
| `RequestHandler` | `onMessageSend(...)` 和 `onMessageSendStream(...)` 创建或恢复 RequestContext，驱动事件消费 | Custom REST 唯一执行入口 |
| `DefaultRequestHandler` | `message.taskId == null` 时生成新 taskId；非空时按 id读取 Task并校验终态/context | 保留其原生创建、恢复和错误语义 |
| `TaskManager` | 首个 Task事件创建正式 Task，初始 history保存入站message；续轮时追加message | 正式父 Task的可验证特征 |
| `MainEventBusProcessor` | 先持久化 Task/Status/Artifact 事件，再向消费者分发 | 流式 reservation 可在确认事件后释放的顺序依据 |
| `A2AAgentExecutor` | 新 Task执行 `submit/startWork`；`INPUT_REQUIRED` Task按恢复语义执行 | 由 `RequestHandler` 间接复用 |
| `TaskStore` | 支持 `get` 和按 context/status分页 `list` | resolver 只读查询，不接管 Task 生命周期 |

`RequestHandler` 不会根据 `contextId` 自动选择 Task。adapter未设置taskId时，只传conversationId会在同一context下不断创建新Task，无法完成 `INPUT_REQUIRED`续轮。因此conversation resolver是无显式taskId请求的必要入口逻辑；adapter已设置taskId时不调用resolver。

### 2.2 TaskStore 中不只有正式父 Task

`A2AEnabledServeOrchestrator` 当前会把远端调用的待续状态保存为：

```text
task.id       = "shadow:" + agentId + ":" + conversationId
task.context  = conversationId
task.state    = INPUT_REQUIRED
task.history  = empty
```

正式 A2A Task与该辅助 Task使用同一个 `TaskStore`。因此 resolver 不能按 contextId取第一条 `INPUT_REQUIRED` Task，也不能依赖存储返回顺序。远端调用流程本身不属于 FEAT-022；这里仅把“排除辅助 Task”作为 Task 解析正确性的必要条件。

### 2.3 当前 tenant传播缺口

标准 `A2aJsonRpcController.parseParams()`当前不设置 `MessageSendParams.tenant`，A2A SDK中的该字段允许为null，runtime也没有补默认tenant。因此从标准入口进入时tenant缺失并保持null是合法基线，Custom REST不得额外要求tenant必填。

当前 SDK `RequestContext#getTenant()`能够读取 `MessageSendParams.tenant`，但 runtime `A2AMessageContext.from(RequestContext)`只复制 message、contextId、taskId和metadata，没有复制 tenant。`A2AProtocolAdapter.toServeRequest(...)`虽然尝试从 `A2AMessageContext.headers`读取 tenant，但该 headers字段在当前链路中未被赋值。

因此 Custom REST adapter即使产出非null tenant，solution bridge也只能把它写入 `MessageSendParams.tenant`；按当前代码，该值不会到达 `ServeRequest.tenantId`。tenant未提供时，从入口到 `ServeRequest`均为null是合法行为；要支持adapter提供tenant，runtime需要补齐如下最小契约：

```text
MessageSendParams.tenant
  -> RequestContext.getTenant()
  -> A2AMessageContext.tenantId
  -> A2AProtocolAdapter.toServeRequest
  -> ServeRequest.tenantId
```

具体最小改动是 `A2AMessageContext.from(...)`复制 `ctx.getTenant()`，`A2AProtocolAdapter.toServeRequest(...)`直接设置该tenant。runtime对tenant没有默认值；framework和runtime都不得偷偷补值。adapter未映射tenant时保持null；单租户部署若希望使用固定值，也由业务adapter明确返回，例如 `default`。

### 2.4 TaskStore 实现差异

- SDK `InMemoryTaskStore.list(...)` 支持分页，默认 page size为 50，默认 `historyLength=0` 会返回空 history，但同样不执行tenant过滤。
- runtime `RedisTaskStore.list(...)` 当前扫描并按 context/status过滤，不执行 tenant过滤，也不真正分页。
- `WriteThrottlingTaskStore.list(...)` 会先刷新缓存状态再查询 delegate。

resolver 必须按 `nextPageToken` 遍历所有页，并对 list结果逐项 `get(task.id())` 获取完整 Task后再识别正式父 Task。查询参数仍应传 tenant，兼容以后真正执行 tenant过滤的 TaskStore；隔离不能依赖当前实现“恰好忽略 tenant”。

---

## 3. 设计原则与约束

1. **Task 单一事实源**：正式 Task只由 `RequestHandler`/A2A SDK创建、恢复和推进。
2. **客户不必感知 taskId**：framework不要求客户提供taskId；adapter可以显式设置，未设置时才由resolver内部补入。
3. **conversationId 稳定**：业务 conversationId由 adapter解析，框架负责租户隔离后写入 A2A `contextId`。
4. **只自动续明确状态**：conversation resolver仅自动续 `INPUT_REQUIRED`；显式taskId交由runtime校验，忙碌、鉴权中断、未知状态和歧义规则只约束自动解析分支。
5. **辅助 Task 不参与父 Task选择**：正式 Task使用入站message history正向识别；当前已知 shadow Task显式排除。
6. **并发先保正确性**：同一进程内，同一内部 context的解析和首次可观察事件之间只允许一个请求进入。
7. **tenant由业务 adapter可选映射**：未映射时允许为null；framework不猜测来源、不隐式补默认 tenant，只负责隔离和原样传递。
8. **沿用runtime tenant契约**：solution只设置标准 `MessageSendParams.tenant`；runtime负责把标准字段传到 `ServeRequest`，不使用私有metadata绕行。
9. **不改变存量入口**：conversationId自动续轮只对新增 Custom REST mapping生效。
10. **状态语义由框架掌握**：adapter可自定义响应body和SSE event名称，但不能改变原始A2A状态、HTTP status和流终止规则。
11. **首版不假装解决跨实例原子性**：没有分布式CAS能力时，必须声明实例亲和/单writer部署前提。

---

## 4. 模块与组件

### 4.1 模块位置

```text
openJiuwen/agent-solution
  common/agent-runtime-ext-java/agent-service-app/agent-service-app-custom-rest
```

宿主应用先引入 runtime A2A server，再引入该扩展模块、声明一个 adapter bean并配置一个 POST path。

### 4.2 组件划分

```text
agent-service-app-custom-rest
|-- CustomRestProtocolAdapter.java
|-- CustomRestProperties.java
|-- CustomRestController.java
|-- CustomRestContextIdCodec.java
|-- CustomRestA2ATaskResolver.java
|-- CustomRestConversationCoordinator.java
|-- CustomRestA2ABridge.java
|-- CustomRestStreamBridge.java
`-- CustomRestAutoConfiguration.java
```

| 组件 | 职责 |
| --- | --- |
| `CustomRestProtocolAdapter` | 客户 HTTP 与 A2A Java DTO/客户响应之间的业务映射 SPI |
| `CustomRestProperties` | 绑定并校验唯一 `query-path` |
| `CustomRestController` | 收集HTTP Context，检查可选AgentReadiness，调用adapter/bridge，返回JSON或SSE |
| `CustomRestContextIdCodec` | 从tenantId和externalConversationId生成内部 A2A contextId |
| `CustomRestA2ATaskResolver` | taskId为空时只读 TaskStore，识别正式父 Task并决定新建、续轮或拒绝 |
| `CustomRestConversationCoordinator` | 保护单 JVM内同一 conversation的解析到首次持久化窗口 |
| `CustomRestA2ABridge` | 校验command、补context/task、调用 `RequestHandler` |
| `CustomRestStreamBridge` | 单次订阅 publisher，执行 reservation、SSE背压和断连处理 |
| `CustomRestAutoConfiguration` | 条件装配上述组件，不承载业务映射 |

不设计 `ReservationReconciler`、`UNCERTAIN`后台状态、私有 Task索引或第二套持久化状态机。

### 4.3 依赖关系

```text
CustomRestController
  -> CustomRestProtocolAdapter
  -> AgentReadiness (optional)
  -> CustomRestA2ABridge
       -> CustomRestContextIdCodec
       -> CustomRestConversationCoordinator
       -> CustomRestA2ATaskResolver -> TaskStore (read only)
       -> RequestHandler
       -> CustomRestStreamBridge

runtime A2AAgentExecutor
  -> runtime A2AProtocolAdapter
       -> ServeRequest
```

扩展只依赖runtime公开bean类型 `RequestHandler`和 `TaskStore`，不得覆盖 `A2AProtocolAdapter`，也不得依赖 `DefaultRequestHandler`、`RedisTaskStore`等具体实现。

---

## 5. Java SPI 与 HTTP Context

### 5.1 SPI

```java
public interface CustomRestProtocolAdapter {
    A2ASendCommand toA2ARequest(Context context);

    Object fromA2ATask(Task task, Context context);

    SseEvent fromA2AStreamEvent(StreamingEventKind event, Context context);

    Object fromError(CustomRestError error, Context context);

    SseEvent fromStreamError(CustomRestError error, Context context);

    record A2ASendCommand(
        MessageSendParams params,
        String conversationId,
        String tenantId, // nullable
        boolean stream
    ) {
    }

    record SseEvent(
        String event,
        Object data
    ) {
    }

    record Context(
        Map<String, List<String>> headers,
        Map<String, String> pathVariables,
        Map<String, List<String>> queryParams,
        Map<String, Object> body
    ) {
    }

    record CustomRestError(
        int httpStatus,
        String code,
        String message
    ) {
    }
}
```

adapter若判定客户字段缺失、格式错误或业务校验不通过，应抛出 `CustomRestRequestException`。该异常只允许携带稳定错误 code、脱敏 message和 400-499 status；其他未声明异常一律视为 adapter内部错误并返回 500，避免把实现缺陷伪装为客户错误。

`tenantId`是可选的业务租户字符串。其来源和映射规则完全由adapter决定，可以来自path、query、header、body或宿主提供的其他输入；未提供时可以返回null。framework不规定字段优先级，不要求非空，不提供默认值，也不执行trim或其他规范化；null、空字符串和非空字符串均原样传递。adapter宜用null表达“未提供”，单租户应用也可以显式返回固定值。

### 5.2 Context 契约

- `Context` 是单次请求的只读快照，不暴露 `HttpServletRequest`、Spring MVC类型或原始 body字符串。
- header名称统一转小写；header和query parameter保留多值。
- 顶层集合做不可变复制；JSON嵌套对象不承诺递归冻结。
- adapter是宿主内可信代码，负责依据客户协议和宿主安全策略产生tenant；本特性不负责证明该字段已经认证。

### 5.3 command 约束

adapter返回的是已经构造完成的A2A Java DTO，因此校验基线是A2A SDK DTO和runtime `RequestHandler`的Java契约，不重复实现JSON-RPC Controller的字段解析逻辑。bridge只增加conversationId非空这一项业务校验，并把它放在独立函数中，便于后续确有必要时集中扩展：

```java
private static void validateCommand(A2ASendCommand command) {
    if (command.conversationId() == null || command.conversationId().isBlank()) {
        throw new InvalidAdapterCommandException("conversationId is required");
    }
}
```

adapter返回null command或null params属于宿主实现错误；`MessageSendParams`和 `Message`内部必填字段由A2A SDK构造器保证，不在 `validateCommand`中重复校验。

| 字段 | 约束 |
| --- | --- |
| `params` / `params.message` | 非null；与A2A SDK一致，`MessageSendParams`只把message定义为必填字段 |
| `message.role` / `message.parts` / `message.messageId` | 沿用A2A SDK约束：三者非null且parts列表非空；messageId未设置时由SDK builder生成 |
| `message`其他字段 | `referenceTaskIds`、`metadata`、`extensions`按SDK nullable契约原样保留；当前runtime只提取 `TextPart`形成下游文本，adapter需要产生文本输入时应使用 `TextPart` |
| `message.taskId` | 可为空；非空时原样保留、跳过conversation resolver并交由 `RequestHandler`校验；值的正确性由adapter负责 |
| `message.contextId` | adapter无需设置；若设置也必须被框架的 internalContextId覆盖 |
| `conversationId` | 非空，是客户稳定会话标识；具体规范化规则由adapter决定，framework不改写 |
| `tenantId` | 可为null；具体来源和值由adapter决定，framework不改写、不补默认值 |
| `params.configuration` / `params.metadata` | 按A2A SDK nullable契约原样保留；configuration中的各字段不增加Custom REST私有限制，由runtime按现有语义处理 |
| `stream` | true调用stream方法，false调用blocking方法 |

bridge把adapter返回的conversationId用于寻址，把adapter返回的tenantId（包括null或空字符串）同时用于internalContextId、TaskStore查询和 `MessageSendParams.tenant`。adapter若要把客户输入错误投影为4xx，应在返回command前抛出 `CustomRestRequestException`；adapter返回空conversationId属于宿主实现错误，返回500。adapter可以自定义parts、A2A可选字段、同步JSON、SSE event名称和data信封。bridge覆盖标准tenant/context字段；taskId由adapter显式值或resolver结果二选一，其他A2A字段不改写、不丢弃。

adapter显式taskId不存在、与internalContextId不匹配或指向runtime不允许恢复的Task时，直接沿用 `RequestHandler`产生的标准A2A错误；framework不增加taskId预校验或私有错误码。

`SseEvent.event`允许为空（输出未命名 `data:`帧）；非空时不得包含CR、LF或NUL。`data`必须非空且可由宿主 `ObjectMapper`序列化，不能返回 `SseEmitter`、`ResponseEntity`或其他Servlet/Spring响应对象。

### 5.4 出站契约

- 所有同步Task状态都交给 `fromA2ATask(...)`；所有流式Task/Status/Artifact事件都交给 `fromA2AStreamEvent(...)`。
- 同步handler成功返回Task时，HTTP transport status固定为200，包括Task状态为failed/canceled/rejected/unrecognized；adapter必须根据原始Task状态忠实投影，不能改变HTTP status。
- adapter必须忠实投影 completed、failed、canceled、rejected、unrecognized、input-required等状态。
- adapter可以自定义SSE event名称和data；framework仍根据原始A2A事件决定是否终止连接，不能由自定义名称反向改变语义。
- 所有投影结果必须非null且可序列化；否则按 `adapter_execution_failed`处理。
- `fromError(...)`/`fromStreamError(...)`返回null、抛错或结果无法序列化时，framework使用固定脱敏兜底信封/event。

---

## 6. conversationId 与正式 Task 解析

### 6.1 内部 contextId

不能把外部 conversationId直接作为 A2A contextId，因为当前 TaskStore实现不保证tenant过滤。框架生成：

```text
tenantComponent =
  0x00                                      // tenantId == null
  0x01 || len(tenantId) || tenantId         // tenantId != null

internalContextId =
  "custom-rest:v1:" +
  base64urlNoPadding(
    SHA-256(
      tenantComponent ||
      len(externalConversationId) || externalConversationId
    )
  )
```

- 所有字符串按 UTF-8编码；长度使用固定宽度大端整数，避免拼接歧义；单字节tenant标记确保null与空字符串属于不同命名空间。
- adapter产出的 `message.contextId`不作为输入，框架使用internalContextId重建Message；`message.taskId`非空时原样保留。

internalContextId既进入正式 A2A Task，也成为下游 `ServeRequest.conversationId`。外部响应仍使用业务 conversationId，不向客户暴露内部值。

### 6.2 查询算法

仅当adapter未设置 `message.taskId`时，resolver在持有conversation reservation期间执行以下既有算法：

```text
pageToken = null
all = []
do:
  result = taskStore.list(ListTasksParams(
      contextId = internalContextId,
      pageSize = 100,
      pageToken = pageToken,
      historyLength = 0,
      includeArtifacts = false,
      tenant = tenantId))
  for each summary in result.tasks:
      current = taskStore.get(summary.id)
      if current != null and current.contextId == internalContextId:
          all.add(current)
  pageToken = result.nextPageToken
while pageToken is not blank
```

必须检测重复 page token，避免错误 TaskStore实现导致死循环。list/get失败统一按 `task_store_unavailable`处理，不降级为“没有 Task”，否则会重复创建父 Task。

### 6.3 正式父 Task识别

当前正式 Task的创建路径会把入站message保存到history；shadow Task没有history。候选分类如下：

1. `history`非空，且至少存在一条 `contextId == internalContextId` 的Message：正式父Task；role沿用SDK/runtime语义，不作为Task归属判据。
2. `id`以 runtime当前保留的 `shadow:`前缀开头且 history为空：已知辅助 Task，排除。
3. 其他同 context且不是已知终态的Task，包括状态为空、`UNRECOGNIZED`或未来未知状态：无法证明其归属，返回 `conversation_task_conflict`，不得忽略后创建新Task。
4. 其他同 context且状态明确为completed/failed/canceled/rejected的Task可以忽略，但记录不含业务标识的诊断信息。

这里使用 history是当前 runtime/A2A SDK的可验证事实，不依赖 solution历史私有 metadata。若 runtime以后改变正式 Task初始 history契约，必须同步调整 resolver和契约测试。

### 6.4 状态决策

先过滤正式父 Task中的终态 Task，再对非终态集合决策：

| 非终态正式父 Task | 决策 |
| --- | --- |
| 0个 | `CREATE_NEW`：保持 taskId为空 |
| 1个且 `INPUT_REQUIRED` | `RESUME`：内部补入该 Task.id |
| 1个且 `SUBMITTED`/`WORKING` | 409 `conversation_busy` |
| 1个且 `AUTH_REQUIRED` | 409 `conversation_not_resumable` |
| 1个且状态为空/`UNRECOGNIZED`/未知 | 409 `conversation_task_conflict` |
| 多于1个 | 409 `conversation_task_ambiguous` |

允许被resolver忽略并创建新Task的已知终态只包括completed、failed、canceled、rejected。SDK `1.0.0.Final`的 `TaskState.isFinal()`还会把 `UNRECOGNIZED`返回为true，但resolver不得据此忽略未知状态；它必须失败关闭。已知终态后的新请求创建新Task，但旧Task仍保留在TaskStore中。

### 6.5 参数重建

bridge使用builder复制adapter生成的参数并重建Message。显式taskId优先；只有taskId为空时才使用resolver结果：

```text
message.contextId = internalContextId
message.taskId    = originalMessage.taskId // adapter显式设置，跳过resolver
                  = null                   // 未设置且resolver返回CREATE_NEW
                  = resolvedParent.id      // 未设置且resolver返回RESUME
params.tenant     = command.tenantId
params.metadata   = original metadata
```

bridge为每次调用新建 `ServerCallContext`，并显式设置：

```text
callContext.state["_a2a_stream"] = command.stream
```

这是当前 `A2AAgentExecutor`选择 `ServeOrchestrator.query()`或 `streamQuery()`的必要runtime契约。仅根据command.stream选择两个 `RequestHandler`方法但不设置该state，会造成外层返回SSE而内部仍按非流式执行。Custom REST重建后的message.contextId与续轮task上下文严格一致，因此保留SDK默认strict context validation，不复制标准controller当前的宽松设置。

扩展不预创建 Task，不生成 shadow Task，不调用 `TaskStore.save/delete`。正式 Task唯一的创建、history追加和状态推进路径仍是 `RequestHandler`。

---

## 7. runtime tenant传播前置契约

### 7.1 solution侧职责

业务adapter可以把客户协议中的租户值转换为 `A2ASendCommand.tenantId`，也可以不映射并返回null。bridge不校验tenant非空、不补默认值，以该值原样覆盖 `MessageSendParams.tenant`。tenant通过可空编码参与internalContextId计算，并原样传给 `ListTasksParams.tenant`。solution不把tenant复制到私有metadata，也不覆盖runtime `A2AProtocolAdapter`。

### 7.2 runtime目标契约

runtime应统一按A2A标准字段完成传播：

```text
MessageSendParams.tenant
  -> RequestContext.getTenant()
  -> A2AMessageContext.tenantId
  -> A2AProtocolAdapter.toServeRequest(...)
  -> ServeRequest.tenantId
```

建议runtime最小改动：

1. `A2AMessageContext`增加 `tenantId`字段。
2. `A2AMessageContext.from(RequestContext)`设置 `tenantId = ctx.getTenant()`。
3. `A2AProtocolAdapter.toServeRequest(...)`设置 `req.setTenantId(ctx.getTenantId())`。
4. 不再依赖当前未赋值的 `A2AMessageContext.headers`来获得tenant。

这不是Custom REST私有契约。任何通过 `RequestHandler`提交的Java ingress，都应得到与 `MessageSendParams.tenant`相同的 `ServeRequest.tenantId`，包括null。

### 7.3 当前遗留与发布门禁

在本文基线commit中，上述传播尚未实现，且runtime没有tenant默认值。由此得到三个明确结论：

- adapter未提供tenant时，`MessageSendParams.tenant`和 `ServeRequest.tenantId`均为null是合法结果，不存在默认值缺失问题。
- adapter提供tenant时，solution侧Task寻址仍可按该值隔离，但当前runtime会使下游 `ServeRequest.tenantId`变为null，存在传播损失。
- 完整支持adapter提供tenant之前，必须先合入runtime传播修复并通过契约测试；不得用metadata、thread-local或solution覆盖bean临时绕过。

---

## 8. 并发与执行流程

### 8.1 单 JVM reservation

`CustomRestConversationCoordinator`维护：

```text
ConcurrentHashMap<internalContextId, ReservationToken>
```

请求在查询 TaskStore之前执行 `putIfAbsent`。同一 key已有 reservation时返回 409 `conversation_busy`；不同 conversation可并发。释放使用 `remove(key, token)`，迟到回调不能删除后继请求的 reservation。

reservation不是 Task状态，也不持久化。它只覆盖以下原子性缺口：

```text
查询“无正式 Task”
  -> RequestHandler生成 taskId
  -> 首个正式 Task/Status事件持久化并可查询
```

### 8.2 同步调用

```text
POST
  -> parse HTTP/JSON and build Context
  -> adapter.toA2ARequest
  -> validate command
  -> AgentReadiness.isAgentLoaded
  -> derive internalContextId
  -> acquire reservation
  -> taskId为空时执行resolver
  -> RequestHandler.onMessageSend
  -> verify result is Task
  -> release reservation
  -> adapter.fromA2ATask
  -> HTTP 200 JSON
```

`onMessageSend`抛错时释放reservation并投影错误。当前blocking handler在返回前完成事件消费和持久化，因此成功返回Task后不再执行一次冗余的TaskStore读取；返回非Task结果时按 `invalid_a2a_result`失败。

Custom REST不新增第二套blocking timeout。`DefaultRequestHandler`在 `configuration.returnImmediately=true`或既有agent等待窗口超时后可能返回仍处于working的当前Task，该Task按真实状态投影，不能声明completed；事件消费超时产生的A2A `InternalError`按SDK错误映射。网络断开或HTTP等待超时不自动取消正式Task。

### 8.3 流式调用

```text
POST
  -> parse HTTP/JSON and build Context
  -> adapter.toA2ARequest(stream=true)
  -> validate command
  -> AgentReadiness.isAgentLoaded
  -> derive internalContextId
  -> acquire reservation / taskId为空时执行resolver
  -> RequestHandler.onMessageSendStream
  -> subscribe exactly once
  -> persisted StreamingEventKind
  -> confirm formal parent Task is observable
  -> release reservation
  -> adapter.fromA2AStreamEvent
  -> SSE data
```

SDK `MainEventBusProcessor`保证事件先写 TaskStore再分发。stream bridge在收到首个携带 taskId的 Task/Status/Artifact事件时，用 `TaskStore.get(taskId)`确认其为当前 internalContextId下的正式父 Task，然后释放 reservation。对续轮请求，必须等到本次调用的首个已持久化事件，而不能因为旧 Task在调用前已经存在就立即释放。

`onMessageSendStream`调用抛错、publisher订阅抛错、首事件前 `onError`或正常 `onComplete`都必须释放reservation；不建立 `UNCERTAIN`状态或后台对账器。

若publisher既不产生首事件也不terminal，reservation保持fail-closed，同conversation后续请求持续返回409。首版不以超时直接释放，因为无法证明后台没有迟到创建Task；进程重启后由TaskStore事实重新解析，不持久化reservation。

### 8.4 首事件前断连

客户端在首个可确认事件前断连时：

1. 停止向 HTTP响应写数据并记录下游已关闭。
2. 暂不取消上游订阅，继续最小请求事件，直到正式父 Task首次可观察或 publisher terminal。
3. reservation释放后，如果下游已关闭，再取消上游订阅。
4. 不调用 `RequestHandler.onCancelTask(...)`；网络断连不等于业务取消。

这样可以避免“客户端断连 -> reservation立即释放 -> 第二个请求在 Task持久化前又创建新 Task”的窗口。

### 8.5 多实例边界

本地 `ConcurrentHashMap`不能解决两个 JVM同时执行“查询为空并创建”的竞态，而当前通用 `TaskStore`接口没有按 context的原子 compare-and-set能力。首版部署必须满足以下至少一项：

- 同一 `(tenantId, conversationId)`始终路由到同一实例；或
- 该 hosted Agent只有一个写实例。

若部署环境不能提供该约束，必须先增加基于共享基础设施的分布式 conversation coordinator/CAS索引，再声称支持多副本并发首轮。不能用普通 Redis `GET`/`SET`拼接或本地锁假装已经解决跨实例原子性。

---

## 9. HTTP、SSE 与错误语义

### 9.1 配置

```yaml
openjiuwen:
  service:
    custom-rest:
      query-path: /custom/{session_key}
```

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.service.custom-rest.query-path` | String | 空 | 唯一 Custom REST POST path；配置后启用 |

不提供 `enabled`、method列表、多 path列表或字段映射 DSL。

### 9.2 自动装配

auto-configuration仅在 Servlet WebApplication、Spring MVC、A2A SDK、`query-path`存在时生效，并要求：

1. 恰好一个 `CustomRestProtocolAdapter`。
2. 存在 `RequestHandler`、`TaskStore`、`ObjectMapper`。
3. `query-path`非空且为合法绝对 path pattern。

配置缺失时不注册Custom REST controller，也不要求上述业务bean。配置存在但依赖缺失、adapter多于一个或mapping冲突时启动失败，不能半可用启动。

`AgentReadiness`使用 `ObjectProvider`可选注入。controller完成HTTP/JSON解析、调用 `toA2ARequest`并校验command后再检查readiness，与runtime Query入口保持一致；存在且 `isAgentLoaded()==false`时，在派生internalContextId、获取reservation和创建Task前返回503，错误body调用adapter的 `fromError(...)`投影。缺少该bean时沿用runtime A2A入口行为并继续执行。

首版使用Spring属性占位符直接注册唯一mapping，例如：

```java
@PostMapping(path = "${openjiuwen.service.custom-rest.query-path}")
```

整个controller bean受 `query-path`存在条件保护；配置启用后直接注入单个adapter、RequestHandler和TaskStore，缺失或多候选让容器启动失败。非法pattern和与既有mapping完全冲突由Spring启动期校验，不再引入 `RequestMappingHandlerMapping.registerMapping(...)`第二套动态注册生命周期。

### 9.3 请求与响应

| 场景 | 行为 |
| --- | --- |
| `application/json` + JSON object | 构造 Context并调用 adapter |
| body为空 | 按空 object处理；不因Content-Type缺失而拒绝 |
| 非空 body但 Content-Type缺失或不是JSON media type | 415 |
| JSON语法错误或根节点非 object | 400 |
| 非 POST | 不进入 adapter，由 Spring返回 method mismatch |
| `stream=true`但 Accept不允许 SSE | 406 |

同步成功返回 `application/json`。流式成功返回 `text/event-stream`；每帧event名称和data内容均使用adapter返回的 `SseEvent`投影。

### 9.4 stream bridge约束

1. publisher只能订阅一次。
2. 按原始到达顺序处理事件，不自行合并 Task/Artifact/Status。
3. 每帧完成 adapter转换、JSON序列化和SSE写出后再请求下一帧，避免无界缓冲。
4. `TaskStatusUpdateEvent.isFinalOrInterrupted()`为true，或Task快照状态 `isFinal()/isInterrupted()`为true时，在对应事件成功写出后结束当前HTTP流；包括 `INPUT_REQUIRED`、`AUTH_REQUIRED`和各终态。adapter可自定义event名称，但不得改变状态语义。
5. publisher error或adapter/序列化失败时最多调用一次 `fromStreamError(...)`并最多写出一帧错误；已经写出终止事件后，忽略后到的 `onError`/`onComplete`。兜底event名称为 `error`。
6. 首事件后的断连取消当前订阅，但不取消正式 Task。

### 9.5 错误映射

| 场景 | HTTP/SSE | code |
| --- | --- | --- |
| Content-Type不支持 | HTTP 415 | `unsupported_media_type` |
| JSON非法或根节点非 object | HTTP 400 | `invalid_json` |
| stream与Accept不兼容 | HTTP 406 | `stream_not_acceptable` |
| adapter判定客户请求非法 | HTTP 400 | `invalid_custom_request` |
| conversationId为空 | HTTP 500 | `invalid_adapter_command` |
| adapter返回null command/params或执行异常 | HTTP 500 | `adapter_execution_failed` |
| Agent尚未加载 | HTTP 503 | `agent_not_ready` |
| 同 conversation已有reservation或Task正在执行 | HTTP 409 | `conversation_busy` |
| Task不可自动续轮 | HTTP 409 | `conversation_not_resumable` |
| 正式 Task多义或出现未知同 context Task | HTTP 409 | `conversation_task_ambiguous` / `conversation_task_conflict` |
| TaskStore查询失败 | HTTP 503 | `task_store_unavailable` |
| A2A协议错误 | `A2AErrorCodes.fromCode(code).httpCode()`；未知code为500 | `a2a_<numeric-code>` |
| 同步返回非 Task | HTTP 502 | `invalid_a2a_result` |
| 同步Task为failed/canceled/rejected/unrecognized | HTTP 200 + Task投影 | 由Task状态决定 |
| 同步出站 adapter/序列化失败 | HTTP 500 | `adapter_execution_failed` |
| 流式 adapter/序列化/publisher失败 | SSE `error`后结束 | 对应稳定 code |
| 客户端断连 | 无新增响应 | 无 |

framework始终掌握实际HTTP status、原始A2A状态和连接终止时机；adapter拥有响应body、SSE event名称和data。任意返回对象无法被framework通用地判断是否在客户协议中“伪装成功”，因此每个业务adapter必须用契约测试证明失败、中断和终态投影正确。

---

## 10. 测试与验收

### 10.1 单元测试

| 测试对象 | 必须覆盖 |
| --- | --- |
| properties/auto-configuration | path缺失不启用；非法path、缺少/多个adapter、缺少RequestHandler/TaskStore、mapping冲突时失败 |
| controller/context | path/header/query/body采集；header多值和大小写；`application/json`、空body、Content-Type/Accept校验；readiness在adapter校验后且不获取reservation、不创建Task |
| context codec | 长度前缀无拼接碰撞；null tenant与空字符串不碰撞；tenant/conversation任一变化均隔离；输出稳定且不泄露原文 |
| command validation | 独立 `validateCommand`只校验conversationId非空；显式taskId允许且原样保留；tenant为null、空字符串和非空值时均原样覆盖params.tenant；adapter contextId被覆盖；其余字段沿用SDK/runtime契约 |
| task resolver | 全分页；重复pageToken保护；list后get完整history；nullable tenant参数原样传递；已知终态忽略；UNRECOGNIZED失败关闭；状态决策表 |
| task classification | 正式Task与shadow Task混存不误选；message role不影响正式Task识别；状态为空、UNRECOGNIZED或未来未知Task失败关闭；存储返回顺序不影响结果 |
| conversation coordinator | 同key互斥、不同key并行、token条件删除、异常释放、续轮首事件前不早释 |
| runtime tenant契约 | tenant为null、空字符串和非空值时，`MessageSendParams.tenant -> RequestContext.getTenant -> A2AMessageContext.tenantId -> ServeRequest.tenantId`均完整原样传播且无隐式默认值 |
| blocking bridge | 无显式taskId时新建/自动续轮参数正确；显式taskId跳过resolver并由RequestHandler校验；`_a2a_stream=false`；调用onMessageSend；configuration原样传递；`returnImmediately=true`返回的working Task按原状态投影；非Task结果拒绝；A2AError映射 |
| stream bridge | 显式taskId跳过resolver并由RequestHandler校验；`_a2a_stream=true`；单次订阅、顺序、背压、自定义event/data、final/interrupted终止、首次持久化后释放、终止事件后迟到onError、重复terminal幂等、断连前后两种处理 |
| error fallback | 投影/fromError/fromStreamError返回null、抛错或不可序列化时使用固定脱敏信封/event；HTTP status和连接终止语义不被adapter覆盖 |

### 10.2 集成验收

1. 首次同步请求只携带 conversationId，经 `RequestHandler`创建可从 runtime TaskStore查询的正式 Task。
2. 首次流式请求产生正式 Task、状态和 artifact事件，逐帧进入 adapter。
3. adapter未设置taskId时，正式Task变为 `INPUT_REQUIRED`后，相同tenant（包括同为null）和conversationId的下一次请求自动恢复同一Task id。
4. 正式 Task进入completed/failed/canceled/rejected已知终态后，相同 conversationId的下一次请求创建新的正式 Task；UNRECOGNIZED不按正常终态忽略。
5. `SUBMITTED/WORKING`时并发输入返回 `conversation_busy`；`AUTH_REQUIRED`不被当作普通输入续轮。
6. 无显式taskId时，多个非终态正式Task返回ambiguous，不按时间或列表顺序猜测；显式taskId不进入该选择逻辑。
7. 正式父 Task和 runtime shadow Task共存时，只恢复正式父 Task；远端调用链本身保持 runtime原有行为。
8. 相同 conversationId在不同 tenant下互不串 Task，null tenant与空字符串也不共用context。
9. 单实例内两个并发首轮至多创建一个正式父 Task。
10. SSE客户端在首事件前断连时，reservation保持到正式 Task可观察或publisher terminal，不产生第二个父 Task。
11. adapter未提供tenant时，`MessageSendParams.tenant`与 `ServeRequest.tenantId`保持null；adapter提供tenant时，原值经runtime标准契约进入 `ServeRequest.tenantId`；runtime不自动补默认值。
12. 普通、失败、中断和终态SSE事件均允许adapter自定义event名称和data，且保持原始A2A语义与终止规则。
13. Custom REST mapping与runtime既有mapping共存，后者的请求、响应和配置行为不变化。
14. solution不覆盖runtime `A2AProtocolAdapter`；完整支持adapter提供tenant前，runtime tenant传播修复必须完成。
15. AgentReadiness未就绪时，合法请求在adapter校验后返回503且不创建Task；非法请求仍返回对应4xx；缺少readiness bean时沿用runtime既有行为。
16. 同步failed/canceled/rejected/unrecognized Task以HTTP 200进入 `fromA2ATask`，adapter保持原始失败状态；blocking窗口返回working Task或runtime超时错误时不投影为completed。

---

## 11. 合理性、完整性与落地边界

### 11.1 为什么该方案合理

- **执行链最短**：Custom REST只增加协议转换和Task寻址，执行仍进入现有 `RequestHandler`。
- **没有双状态源**：扩展不创建、不保存、不删除 Task，不维护 conversation到task的持久化私有索引。
- **满足真实续轮输入**：客户只有conversationId时，扩展在边界层自动补齐SDK要求的taskId；adapter已有明确taskId时直接沿用，不增加私有限制。
- **识别当前存储现实**：同一 TaskStore存在正式 Task和shadow Task，方案明确正向识别和失败关闭。
- **tenant职责清晰**：adapter决定是否提供tenant及其值，solution不设默认值并写入标准A2A字段，runtime负责原样传播到 `ServeRequest`。

### 11.2 必要复杂度与已删除的过设计

必要组件只有一个只读resolver和一个短生命周期本地reservation，分别解决SDK不按context恢复以及查询到创建竞态。tenant不再引入solution私有registry、thread-local或adapter覆盖。

以下内容不进入首版：

- 后台 reservation reconciler；
- `UNCERTAIN`持久状态；
- 独立 active-task数据库索引；
- 自定义 Task创建器或 Task状态机；
- 远端调用编排改造；
- 多 path路由 DSL。

### 11.3 明确限制

| 限制 | 首版处理 |
| --- | --- |
| 多 JVM同会话并发创建 | 依赖实例亲和或单 writer；无该条件不得宣称支持 |
| runtime Task history契约变化 | 通过版本锁定和契约测试发现，随后更新classifier |
| TaskStore list一致性 | list后get并失败关闭；不把存储错误当作空结果 |
| RedisTaskStore list全量scan | 首版只适用于Task数量可控的部署；上线前按预期Task基数压测，规模化前由runtime增加context索引能力 |
| TaskStore生命周期 | InMemory重启或Redis TTL到期后旧Task不可续，下一次请求按无活跃Task创建新Task |
| stream首事件永久缺失 | reservation保持fail-closed；不以超时冒险释放，不增加后台对账状态机 |
| runtime tenant传播缺失 | tenant为null时合法；完整支持adapter提供tenant前先修复runtime，solution不做私有绕行 |
| 请求去重/idempotency | 不在首版范围；conversation互斥不等于通用请求幂等 |
| Task查询、取消、订阅 | 不新增Custom REST surface |
| tenant认证与授权 | 不在本特性范围；adapter按客户协议决定是否产出tenant及其值 |

---

## 12. 实施结论

首版落地边界为：

```text
一个可配置 POST path
  + 一个 Java adapter
  + conversationId -> internalContextId
  + 只读正式 Task resolver
  + 单实例短生命周期 reservation
  + 标准 MessageSendParams.tenant
  + runtime现有 RequestHandler
  + 正式 A2A Task
  + 自定义 JSON/SSE投影
```

在同一会话请求具备实例亲和或单writer的前提下，该方案满足客户只提供conversationId、首次创建正式A2A Task、`INPUT_REQUIRED`时恢复同一Task、终态后新建Task的要求。tenant可以不提供并保持null；若要支持adapter提供tenant并传入下游 `ServeRequest`，runtime还必须补齐标准tenant传播。方案不改变runtime既有入口语义，不把远端调用纳入本需求，不依赖solution历史残留实现，也不引入第二套持久化状态机。
