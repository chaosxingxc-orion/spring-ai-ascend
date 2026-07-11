---
level: L2-LLD
module: agent-runtime-ext-java
feature_type: functional
feature_id: Feat-Func-001
status: active
dependency:
  - ../../L1-High-Level-Design/agent-runtime/api-appendix.md
  - Feat-Func-001-standardized-agent-service-entrypoint.md
  - openJiuwen/agent-runtime-java
---

# Custom REST API 到 Agent Runtime 执行入口适配 SPI 设计说明

> 目标仓库：`openJiuwen/agent-solution`
> 目标模块：`common/agent-runtime-ext-java/agent-service-app/agent-service-app-custom-rest`
> 最后更新：2026-07-11

说明：本文档是 Feat-Func-001 的 Custom REST 子设计，描述 `agent-runtime` 逻辑边界在 OpenJiuwen 社区实现中的扩展方案；实际代码实现落在 `agent-solution` 仓库，不修改 `spring-ai-ascend/agent-runtime` 主模块代码。

---

## 1. 概述

### 1.1 特性定位

本设计是 Feat-Func-001 的 Custom REST 子设计：在 `agent-solution/common/agent-runtime-ext-java` 中新增一个轻量 custom-rest 扩展 starter，使平台集成方可以用自有 REST URL、请求字段和响应信封调用 Agent Runtime。

该扩展只负责 HTTP 外壳适配：

```text
自定义 REST HTTP 请求
  -> CustomRestProtocolAdapter Java SPI
  -> ServeRequest
  -> ServeOrchestrator.query()/streamQuery()
  -> CustomRestProtocolAdapter Java SPI
  -> 自定义 REST HTTP 响应 / SSE event
```

核心原则是：自定义 REST 不新增执行状态机，不直接调用 A2A JSON-RPC HTTP controller，也不再走一次 HTTP。它和现有 `/v1/query` 固定 REST facade 一样，最终衔接到 runtime 内部统一的 `ServeOrchestrator`。

### 1.2 当前事实依据

当前 `agent-runtime-java` 中已有两类与本特性直接相关的入口：

| 入口 | 当前路径 | 内部衔接点 | 说明 |
| --- | --- | --- | --- |
| 固定 REST facade | `POST /v1/query` | `ServeOrchestrator` | `QueryMvcController` 将 `QueryRequest` 转为 `ServeRequest` |
| A2A JSON-RPC | `POST /a2a` | A2A SDK `RequestHandler` -> `A2AAgentExecutor` -> `ServeOrchestrator` | JSON-RPC 是外部协议壳，执行仍归一到 orchestrator |

`ServeOrchestrator` 的默认实现可能是 `A2AEnabledServeOrchestrator`。因此 custom-rest 和 `/v1/query` 一样可以复用 handler 调用、远端 A2A delegate 编排和 stream cancel，但它们都是 **非 Task Query facade**：普通本地 query/stream 不进入 A2A SDK `RequestHandler`，不创建正式 Task。远端 delegate 使用的 shadow Task 只是 orchestrator 内部恢复状态。

### 1.3 设计原则

1. **YAML 只描述 HTTP 暴露面**：只配置 query URL，不把字段映射做成 YAML DSL；首版固定使用 POST。
2. **转换全部由 Java SPI 实现**：入站请求转换、出站响应信封、SSE chunk 包装、错误包装都由业务实现类掌控。
3. **单 adapter**：当前版本只允许启用一个自定义 REST adapter，不考虑多 adapter 共存、优先级和多路径路由表。
4. **复用 `/v1/query` 执行主链**：转换后直接调用 `ServeOrchestrator`，不调用 `A2aJsonRpcController`，不构造 JSON-RPC 字符串。
5. **agent-solution 扩展仓承载**：custom-rest 作为 `agent-runtime-ext-java` 的独立 Spring Boot auto-configuration module，被宿主应用引入 classpath 后生效。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
| --- | --- | --- | --- |
| 自定义 URL 暴露 | 按 YAML 注册一个 query path，固定使用 POST | `CustomRestProperties`, `CustomRestAutoConfiguration` | 已实现 |
| 入站转换 SPI | 将 HTTP 上下文转换为 `ServeRequest` | `CustomRestProtocolAdapter.Context` | 已实现 |
| 出站转换 SPI | 将 `QueryResponse`、`QueryChunk`、错误转换为客户响应 | `CustomRestProtocolAdapter` | 已实现 |
| 执行桥接 | 复用 runtime 内部 `ServeOrchestrator` | `ServeOrchestrator` | 既有依赖 |
| 非 Task Query 边界 | 普通调用只返回当次 JSON/SSE；远端 delegate shadow Task 不升级为正式 Task | `A2AEnabledServeOrchestrator` | 已实现 |

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 单个自定义 query URL | 已实现 | 通过 YAML 配置路径模板，例如 `/custom/{conversation_id}` |
| 固定 POST method | 已实现 | Spring MVC endpoint 固定注册 `POST`，不提供 method 配置项 |
| Java 入站转换 | 已实现 | 业务代码读取 header/path/query/body，构造 `ServeRequest` |
| Java 出站转换 | 已实现 | 业务代码包装同步响应、SSE chunk 和错误响应 |
| 同步执行 | 已实现 | 当 adapter 产出的 `ServeRequest.stream=false` 时调用 `ServeOrchestrator.query()` |
| 流式执行 | 已实现 | 当 adapter 产出的 `ServeRequest.stream=true` 时调用 `ServeOrchestrator.streamQuery()`，返回 SSE |
| 非 Task Query 语义 | 已实现 | 与 `/v1/query` 相同，不创建正式 Task；需要 Task 能力时使用 `/a2a` |
| 多 adapter 共存 | out | 当前版本不支持 |
| YAML 字段映射 DSL | out | 请求/响应转换全部由 Java 代码实现 |
| runtime-to-runtime 标准入口 | out | 自定义 REST 不作为 runtime 间调用标准；runtime 间仍使用 A2A |
| webhook callback | out | 非一次性响应仍使用 SSE 或标准 task 查询能力 |
| multipart / 文件输入 | out | 当前版本只处理 JSON body |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
| --- | --- | --- |
| 调用 `A2aJsonRpcController.handleJsonRpc` | 该 controller 是 JSON-RPC HTTP 协议壳，会导致 custom-rest 先组 JSON-RPC 再被解析，链路绕且绑定协议 envelope | 直接调用 `ServeOrchestrator` |
| 走二次 HTTP | 同进程内方法调用即可，二次 HTTP 增加延迟、错误面和部署耦合 | Spring bean 方法调用 |
| 接入 A2A SDK `RequestHandler` | custom-rest 目标是自定义 REST facade，不是重新实现 A2A JSON-RPC 表面 | 复用 orchestrator 的协议无关入口 |
| 多 adapter 路由 | 当前需求只允许一个自定义 REST | 单 properties + 单 adapter bean |
| 在 YAML 配置字段映射规则 | 字段别名、metadata 透传、响应信封属于业务协议语义，配置化会演变成复杂 DSL | Java SPI 实现 |

### 2.3 行为承诺

- **必须**：custom-rest 的 `POST {query-path}` 被调用时，扩展解析 HTTP 上下文并调用 `CustomRestProtocolAdapter.toServeRequest(...)`。
- **必须**：adapter 返回的 `ServeRequest` 进入 `ServeOrchestrator.query()` 或 `streamQuery()`。
- **必须**：ready gate 与 `/v1/query` 对齐，`AgentReadiness` 未 ready 时返回 adapter 包装后的 503 错误。
- **必须**：`ServeOrchestrator` 不存在时返回 adapter 包装后的 503 错误。
- **必须**：非空请求体的 `Content-Type` 只接受 `application/json` 或 `application/*+json`；空 body 可以缺省 Content-Type。不支持的 media type 返回 adapter 包装后的 415 错误，JSON body 解析失败返回 adapter 包装后的 400 错误。
- **必须**：`executionTimeMs` 使用单调时钟，从 custom-rest handler 方法进入后开始计时，所有同步成功和失败分支采用同一口径；不要求包含 Spring MVC 参数解析耗时。
- **必须**：adapter 产出的 `ServeRequest.conversationId` 非空；`userId`、`spaceId`、`tenantId` 在通用 SPI 层允许为空，其是否必填以及缺失影响由具体 adapter 和下游 Agent 契约说明。
- **必须**：入口日志记录 method、requestPath、conversationId 和 tenantId，且不记录认证 header 和 raw body；correlation/trace 复用宿主已有 Web Filter、网关或观测组件，本扩展不新建 tracing 机制。
- **必须**：流式执行中发生异常时，输出一帧 adapter 包装的 error SSE event 后结束流。
- **允许**：adapter 自行决定外部字段取值优先级、metadata 结构和响应字段名；框架不要求 adapter 建立客户自定义字段校验体系。
- **禁止**：custom-rest 扩展维护独立 task/run/job 状态机。

---

## 3. 核心设计（Logical + Process View）

### 3.1 模块放置

在 `openJiuwen/agent-solution` 新增 ext 子模块：

```text
common/agent-runtime-ext-java/agent-service-app/agent-service-app-custom-rest
```

该模块不放在 ext 根目录的 `agent-service-adapters/` 下。`agent-service-adapters/` 当前承载的是 AgentHandler/框架适配类模块（例如 versatile、agentcore-ext）；custom-rest 是北向 HTTP ingress/app 扩展，因此放在 `agent-service-app` 分组，模块名使用 `agent-service-app-custom-rest`。目录分组、artifact 前缀和职责边界保持一致，也与 `agent-service-adapters/agent-service-adapters-*` 的命名规则对称。

在 `agent-solution/common/agent-runtime-ext-java/pom.xml` 新增 module：

```xml
<module>agent-service-app/agent-service-app-custom-rest</module>
```

模块定位是 Spring Boot auto-configuration starter。宿主应用引入该 jar，并提供一个 `CustomRestProtocolAdapter` bean 后，配置 `openjiuwen.service.custom-rest.query-path` 即可注册自定义 REST 入口；不配置该路径时扩展不启用。首版固定使用 `POST`。

### 3.2 入站主流程

```text
HTTP POST {query-path}
  -> Spring MVC 以 @RequestBody(required=false) String 绑定可选 rawBody
  -> CustomRestAutoConfiguration 注册的 HandlerMethod
  -> 启动单调计时器
  -> 校验 Content-Type
  -> ObjectMapper 将 JSON body 解析为 Map<String,Object>
  -> 从 NativeWebRequest/HttpServletRequest 提取：
       headers
       pathVariables
       queryParams
       bodyMap
       requestPath
  -> CustomRestProtocolAdapter.Context
  -> CustomRestProtocolAdapter.toServeRequest(context)
       ServeRequest
  -> 按 runtime 标准入口规则校验 ServeRequest
  -> AgentReadiness gate
  -> ServeOrchestrator provider gate
  -> if serveRequest.isStream(): streamQuery(...)
     else: query(...)
  -> CustomRestProtocolAdapter.fromQueryResponse/fromQueryChunk/fromError
```

请求体规则：

| 场景 | 框架行为 |
| --- | --- |
| `Content-Type: application/json` 或 `application/*+json` | 继续解析 JSON object |
| Content-Type 缺失且 body 为空 | 按空 object 处理 |
| Content-Type 缺失但 body 非空 | 返回 415，不尝试猜测 media type |
| 其他 Content-Type | 返回 415，adapter 不介入 |
| JSON 语法非法或根节点不是 object | 返回 400 |

handler 使用 `@RequestBody(required=false) String rawBody` 接收请求体：`null` 或空字符串按空 object 处理，非空字符串再由 `ObjectMapper` 解析。`rawBody` 只是 handler 内部的解析输入，不进入 adapter context。Servlet 容器和 Spring MVC 负责请求体读取与字符集处理，本扩展不重复建设独立的 body reader。

### 3.3 HTTP method 配置

当前版本框架层只注册一个 `queryPath`，并固定使用 `POST` 作为 Spring MVC method condition，不提供 method 配置项。

```text
HTTP POST {query-path}
  -> CustomRestProtocolAdapter.toServeRequest(context)
  -> ServeRequest
  -> ServeOrchestrator.query/streamQuery
```

设计约束：

| 约束 | 说明 |
| --- | --- |
| 单 method | 当前只注册 POST，不支持同一路径多 method 分发 |
| 非匹配 method | 由 Spring MVC 按未匹配 method 处理，不进入 adapter |

### 3.4 与 A2A Task 状态的关系

custom-rest 是非 Task Query facade，不生成 A2A JSON-RPC `Task` 响应对象，也不把普通本地 invocation 写入正式 `TaskStore`。

```text
/custom/url
  -> CustomRestProtocolAdapter
  -> ServeRequest
  -> ServeOrchestrator
     -> A2AEnabledServeOrchestrator
        -> local query/stream: QueryResponse / QueryChunk only
        -> remote A2A delegate: optional shadow task
        -> current-stream cancel
```

因此：

| 项 | custom-rest 行为 |
| --- | --- |
| 普通本地完成 | 只返回 `QueryResponse` / `QueryChunk`，不创建正式 Task |
| ask-user interrupt | 以 `QueryChunk.TYPE_INTERRUPT` 或 `QueryResponse.result._interrupt` 在当前响应中返回，不形成可查询的 `INPUT_REQUIRED` Task |
| 远端 A2A delegate | 由 `A2AEnabledServeOrchestrator` 消费 `a2a_delegate` interrupt 并调用远端 |
| A2A shadow task | 只在远端 delegate pending/recovery 时保存，属于 orchestrator 内部状态，不是 Custom REST 返回的 Task |
| Task 查询/取消/订阅 | 不支持 GetTask、CancelTask、SubscribeToTask；需要这些能力时调用 `/a2a` |

业务 adapter 不应将自定义响应声明为权威 A2A Task，也不应使用自定义字段模拟可查询的 Task 生命周期。框架只负责序列化 adapter 返回的任意对象，不检查其中的业务字段；该约束属于 adapter 与接入方之间的集成契约。

### 3.5 SPI 形态

最小 SPI 使用一个接口承载入站和出站转换，并把请求上下文定义为接口内嵌类型，避免为纯数据结构单独增加 Java 文件。

```java
public interface CustomRestProtocolAdapter {
    ServeRequest toServeRequest(Context context);

    Object fromQueryResponse(QueryResponse response, Context context, long executionTimeMs);

    Object fromQueryChunk(QueryChunk chunk, Context context);

    Object fromError(int httpStatus, String errorMessage, Context context, long executionTimeMs);

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

`toServeRequest(...)` 是入站转换函数：把客户自定义 REST 请求转换为 runtime 规范 `ServeRequest`。

adapter 只负责协议转换，不承担客户自定义字段的通用校验。转换后的 `ServeRequest` 由 handler 内部校验方法按照 runtime 标准入口当前规则校验；首版要求 `conversationId` 非空。adapter 抛出运行时异常或返回 `null` 视为实现错误，统一按脱敏后的 HTTP 500 处理，不进入 orchestrator。

`fromQueryResponse(...)`、`fromQueryChunk(...)`、`fromError(...)` 是出站转换函数：把 runtime 执行结果转换为客户自定义 REST 响应、SSE chunk 或错误信封。

出站函数返回 `Object`，允许业务返回 `Map<String,Object>`、DTO 或 Jackson 可序列化对象。该 SPI 的自定义边界是 **HTTP/SSE body envelope**，不扩展为通用 HTTP response SPI：HTTP status、Content-Type、缓存头、连接头和 SSE event framing 由框架层统一控制。

因此首版明确约束：

| 项 | owner | adapter 是否可自定义 |
| --- | --- | --- |
| JSON/SSE data body | `CustomRestProtocolAdapter` | 是 |
| HTTP status | `CustomRestAutoConfiguration` | 否；按统一错误分类设置 |
| Content-Type / SSE headers | `CustomRestAutoConfiguration` | 否 |
| SSE event name / id / retry | `CustomRestAutoConfiguration` | 否；首版只使用固定 data/error 事件 |
| 任意响应 header | 宿主 Web filter / gateway | 否 |

只有出现明确的客户 header、media type 或 SSE id/retry 需求时，才新增结构化 `CustomHttpResponse` / `CustomSseEvent`；当前返回 `Object` 的最小 SPI 足以覆盖“自定义响应信封”目标。

### 3.6 SPI Context

`CustomRestProtocolAdapter.Context` 不是转换器，而是一次 HTTP 请求的只读上下文。它是 adapter 入站转换函数的参数，用来承载 path、header、path variable、query param 和结构化 body。它是 SPI 的内嵌 record，不单独拆 Java 文件。构造器对顶层集合做防御性不可变复制，不承诺递归冻结嵌套 JSON 对象。

```java
CustomRestProtocolAdapter.Context context
```

设计约束：

| 字段 | 规则 |
| --- | --- |
| `headers` | header 名统一小写保存；adapter 可自行兼容大小写 |
| `pathVariables` | 由 Spring MVC path match 提取 |
| `queryParams` | 保留多值；常见场景 adapter 取第一个值 |
| `body` | JSON object body；空 body 为空 map；非 object body 返回 400 parse error |

身份字段约束：

- `conversationId` 是进入 orchestrator 前的必填字段，缺失时由框架调用 `fromError(400, ...)`，不调用 orchestrator。
- `userId`、`spaceId`、`tenantId` 在通用 SPI 中允许为空。具体 adapter 必须说明这些字段的来源、是否必填以及缺失后对权限、memory、state scope 的影响。
- adapter 可以从受信任网关 header 映射身份，但不得在本层执行 OAuth、签名或租户授权判断。
- header 名按 HTTP 语义统一小写；不提供或承诺保留线上原始大小写。

### 3.7 endpoint 注册

由于只支持一个 adapter，注册逻辑放在 `CustomRestAutoConfiguration` 内部即可，不单拆 registrar，也不单拆 MVC endpoint 文件。auto-configuration 仅在 Servlet WebApplication 中生效，可以声明一个内部 handler 对象，并通过 `RequestMappingHandlerMapping.registerMapping(...)` 绑定到 `queryPath` 和固定 POST method。构造 `RequestMappingInfo` 时使用 `RequestMappingHandlerMapping.getBuilderConfiguration()`，使动态 mapping 与宿主的 path pattern 和内容协商配置保持一致。

启动期行为：

```text
1. 未配置 queryPath -> auto-configuration 不生效，不要求 CustomRestProtocolAdapter bean
2. 已配置 queryPath 但缺少 CustomRestProtocolAdapter bean -> 启动失败
3. queryPath 为空、空白或不是绝对路径 -> 启动失败
4. 使用 RequestMappingHandlerMapping.registerMapping(...) 注册 POST handler method；完全相同的 path + method 由 Spring MVC 拒绝重复注册
```

只注册一个 queryPath 和 POST method condition。非 POST 请求不进入 custom-rest adapter。扩展不复制维护 runtime 内置路径清单：完全相同的 path + method 由 Spring MVC 在注册时拒绝；更具体的内置 mapping 继续按 Spring MVC 路由优先级处理。

### 3.8 SSE 包装

流式调用使用 MVC `SseEmitter`，行为与 `/v1/query` 对齐。首版直接复用现有 MVC 入口的 `SseEmitter(0L)` 和 `CompletableFuture.runAsync(...)` 模式，使 handler 先返回 emitter，再由异步任务调用可能阻塞的 `ServeOrchestrator.streamQuery(...)`；本扩展不额外引入线程池配置：

```text
1. response Content-Type = text/event-stream
2. Cache-Control = no-cache, no-transform
3. Connection = keep-alive
4. X-Accel-Buffering = no
5. 每个 QueryChunk 调用 adapter.fromQueryChunk(...)
6. Jackson 序列化后作为 SSE data 输出
7. 每次流式调用最多输出一帧错误：若已经收到并发送 `QueryChunk.TYPE_ERROR`，后续 `onError` 只 complete；否则由 `onError` 调用 `adapter.fromError(...)` 输出一帧 error event 后 complete
```

SSE data 不强制加 JSON-RPC envelope。客户需要什么外部格式，由 `fromQueryChunk` 决定。

SSE 生命周期由框架层拥有：

- `onTimeout`：标记当前 observer cancelled，并完成 emitter；不调用 `adapter.fromError(...)`。
- `onCompletion`：标记当前 observer cancelled，不再发送 chunk。
- `onError`：若错误来自连接写出或客户端断开，只标记当前 observer cancelled，不再尝试发送 error frame。
- 客户端断开只取消当前调用的 observer。不得仅因一个 emitter 断开而调用 conversation 级 `ServeOrchestrator.cancelActive(conversationId)`，避免误取消同 conversation 的其他并发流。
- 收到 `TYPE_INTERRUPT` 时，adapter 只负责包装该 chunk；是否结束流由 orchestrator/observer 的 terminal 回调决定，adapter 不直接操作 emitter。

---

## 4. 模块结构（Development View）

### 4.1 新增代码结构

```text
common/agent-runtime-ext-java/agent-service-app/agent-service-app-custom-rest
|-- pom.xml
|-- src/main/java/com/openjiuwen/service/app/customrest
|   |-- CustomRestProtocolAdapter.java
|   |-- CustomRestProperties.java
|   `-- CustomRestAutoConfiguration.java
`-- src/main/resources/META-INF/spring
    `-- org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

最小主代码 3 个顶层 Java 文件。

Java package 统一使用 `com.openjiuwen.service.app.customrest`。该扩展属于 agent-service-app 的北向入口能力，package 与模块分组保持一致；artifact 独立存在，不会与 runtime 主仓的 `agent-service-app` 类发生冲突。

| 文件 | 职责 |
| --- | --- |
| `CustomRestProtocolAdapter` | 业务实现的 Java SPI；`toServeRequest` 直接返回 `ServeRequest`，`fromQueryResponse/fromQueryChunk/fromError` 负责出站转换；内嵌 `Context` |
| `CustomRestProperties` | 绑定 `openjiuwen.service.custom-rest` 配置 |
| `CustomRestAutoConfiguration` | 条件装配、动态注册 mapping、内部 handler、`ServeRequest` 必填字段校验、orchestrator/readiness gate、SSE 输出 |
| `AutoConfiguration.imports` | 增加 `CustomRestAutoConfiguration` 自动装配入口 |

### 4.2 Maven 依赖

`agent-service-app-custom-rest/pom.xml` 实际依赖：

| 依赖 | scope | 用途 |
| --- | --- | --- |
| `com.openjiuwen:agent-service-spec` | compile | `ServeRequest`、`QueryResponse`、`QueryChunk`、`ServeOrchestrator`、`AgentReadiness` |
| `org.springframework.boot:spring-boot-autoconfigure` | optional | auto-configuration 和 properties |
| `org.springframework.boot:spring-boot-configuration-processor` | optional | 配置元数据生成 |
| `org.springframework.boot:spring-boot-starter-webmvc` | optional | Servlet MVC、`RequestMappingHandlerMapping`、`SseEmitter` |
| `com.fasterxml.jackson.core:jackson-databind` | compile | body parse 和响应序列化 |
| `org.slf4j:slf4j-api` | compile | 日志 |
| `org.springframework.boot:spring-boot-starter-test` | test | 单元测试和 auto-configuration 测试 |
| `org.springframework.boot:spring-boot-starter-webmvc-test` | test | MockMvc 和 MVC 集成测试 |
| `org.springframework.boot:spring-boot-starter-restclient` | test | 测试期 HTTP client 支持 |

WebMVC 依赖建议设为 optional，并在 auto-config 上同时增加 `@ConditionalOnClass(RequestMappingHandlerMapping.class)` 和 `@ConditionalOnWebApplication(type = SERVLET)`。这样 ext jar 在非 Web 或 Reactive WebApplication 中被引入时不会误注册 MVC endpoint。

### 4.3 静态关系

```text
CustomRestAutoConfiguration
  -> CustomRestProperties
  -> CustomRestProtocolAdapter
  -> ObjectProvider<ServeOrchestrator>
  -> ObjectProvider<AgentReadiness>
  -> ObjectMapper
  -> RequestMappingHandlerMapping.registerMapping

CustomRestProtocolAdapter
  -> ServeRequest
  -> QueryResponse / QueryChunk

CustomRestProtocolAdapter.Context
  -> requestPath/headers/pathVariables/queryParams/body
```

---

## 5. 运行流程（Process View）

### 5.1 同步 query

handler 方法进入后记录 `startNanos = System.nanoTime()`；传给 `fromQueryResponse(...)` 或 `fromError(...)` 的 `executionTimeMs` 统一按当前单调时钟与该起点之差计算。media type 校验、JSON parse、adapter 转换、ServeRequest 校验、readiness 和 orchestrator 异常都使用同一口径，不允许在不同分支重新起表或固定返回 0；Spring MVC 在 handler 方法调用前完成的 rawBody 绑定时间不计入该值。

```text
Client
  -> POST {query-path}
  -> CustomRestAutoConfiguration.handleQuery
  -> CustomRestProtocolAdapter.toServeRequest
  -> ServeRequest(stream=false)
  -> ServeOrchestrator.query
  -> QueryResponse
  -> CustomRestProtocolAdapter.fromQueryResponse
  -> HTTP 200 application/json
```

异常分支：

| 场景 | HTTP status | 响应来源 |
| --- | --- | --- |
| unsupported media type | 415 | `fromError(415, "unsupported media type", ...)` |
| JSON parse error | 400 | `fromError(400, "invalid JSON object", ...)` |
| conversationId missing | 400 | `fromError(400, "conversation_id is required", ...)` |
| adapter conversion error | 500 | 完整异常只记录到受控日志；固定调用 `fromError(500, "adapter execution failed", ...)` |
| agent not loaded | 503 | `fromError(503, "agent not loaded", ...)` |
| no orchestrator | 503 | `fromError(503, "no agent handler configured", ...)` |
| orchestrator exception | 500 | 完整异常只记录到受控日志；对 adapter 固定调用 `fromError(500, "agent execution failed", ...)` |

### 5.2 流式 query

```text
Client
  -> POST {query-path}, body stream=true
  -> CustomRestProtocolAdapter.toServeRequest
  -> ServeRequest(stream=true)
  -> 返回 SseEmitter(0L)
  -> CompletableFuture.runAsync 调用 ServeOrchestrator.streamQuery
  -> QueryChunk ...
  -> CustomRestProtocolAdapter.fromQueryChunk(chunk)
  -> SSE data: <json>
  -> onComplete close
```

流式错误：

```text
streamQuery QueryChunk(TYPE_ERROR)
  -> CustomRestProtocolAdapter.fromQueryChunk(errorChunk, context)
  -> emitter.send(event.name("error").data(json))
  -> 标记 errorFrameSent=true

streamQuery onError / runtime exception
  -> if errorFrameSent=false:
       CustomRestProtocolAdapter.fromError(500, "agent execution failed", context, elapsed)
       emitter.send(event.name("error").data(json))
  -> if errorFrameSent=true: 不再发送第二帧错误
  -> emitter.complete()
```

`TYPE_INTERRUPT` 是本次调用的正常协议结果，不是 HTTP 错误：同步 query 返回 HTTP 200 并由 `fromQueryResponse(...)` 包装 `_interrupt`；流式 query 发送 adapter 包装的 interrupt chunk，随后等待 orchestrator 的 `onComplete/onError` 收束，不由 adapter 主动结束 emitter。

### 5.3 HTTP method 处理

custom-rest 框架固定注册 `POST {query-path}`，不提供 HTTP method 配置项。adapter 不负责 method 路由，非 POST 请求不会进入 handler，由 Spring MVC 返回 405。


### 5.4 自定义 adapter 使用方式

接入方实现 `CustomRestProtocolAdapter`，自行定义外部协议与 runtime DTO 之间的转换规则：

| SPI 方法 | 接入方职责 |
| --- | --- |
| `toServeRequest` | 从 `Context` 的 path、header、path variable、query parameter 和 JSON object body 中读取所需字段，构造 `ServeRequest` |
| `fromQueryResponse` | 将同步 `QueryResponse` 包装为接入方定义的 JSON body |
| `fromQueryChunk` | 将每个 `QueryChunk` 包装为接入方定义的 SSE data body |
| `fromError` | 将框架或 runtime 给出的 HTTP status、脱敏错误消息和执行耗时包装为接入方定义的错误 body |

框架不规定外部请求字段名、字段优先级、metadata 结构或响应信封，也不要求将外部 body 完整透传到 `ServeRequest`。adapter 只需生成满足 runtime 入口要求的 `ServeRequest`，并确保出站返回值能够被 Jackson 序列化。转换后框架统一校验 `conversationId`，其余业务字段约束由接入方根据实际协议处理。

---

## 6. 配置模型（Physical View）

### 6.1 配置示例

```yaml
openjiuwen:
  service:
    custom-rest:
      query-path: /custom/{conversation_id}
```

业务侧提供 adapter bean：

```java
@Bean
CustomRestProtocolAdapter customRestProtocolAdapter() {
    return new MyCustomRestProtocolAdapter();
}
```

### 6.2 配置属性表

| 属性路径 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.service.custom-rest.query-path` | String | 空 | query endpoint 路径模板；启用时必填 |

### 6.3 启用条件

custom-rest auto-configuration 激活条件：

```text
1. 当前应用是 Servlet WebApplication
2. classpath 中存在 Spring MVC RequestMappingHandlerMapping
3. 已配置 openjiuwen.service.custom-rest.query-path
4. 容器中存在且仅存在一个 CustomRestProtocolAdapter bean
5. 容器中存在 ObjectMapper
```

`ServeOrchestrator` 和 `AgentReadiness` 不作为启动必需条件；它们按请求时 `ObjectProvider` 获取，与现有 query controller 行为保持一致。缺少 orchestrator 时请求返回 503；未提供 `AgentReadiness` bean 时视为 ready，提供后仅在 `isAgentLoaded=false` 时返回 503。

---

## 7. 对外呈现 / 用户场景（Scenario View）

### 7.1 外部接口

| 端点 / API | 方法 | 说明 |
| --- | --- | --- |
| `{query-path}` | `POST` | Custom REST 同步或 SSE query；实际模式由 adapter 生成的 `ServeRequest.stream` 决定 |
| `CustomRestProtocolAdapter` | Java SPI | 负责请求字段与响应 body envelope 转换 |

### 7.2 典型接入场景

1. 宿主应用引入 custom-rest adapter artifact。
2. 业务声明唯一 `CustomRestProtocolAdapter` bean。
3. YAML 配置 `query-path`，存在该配置即启用入口。
4. 外部请求经 adapter 转成 `ServeRequest`，进入既有 `ServeOrchestrator`。
5. 同步结果或 SSE data 经 adapter 包装为客户响应信封。

### 7.3 用户可见边界

- 客户可以自定义 URL、字段映射和 body envelope；首版 method 固定为 POST。
- 客户不能通过首版 SPI 自定义 HTTP status、Content-Type、SSE event id/retry 或任意响应 header。
- 客户不能通过该 facade 获得正式 Task、Task 查询、Task 取消或断线后的 Task 级重订阅。
- runtime-to-runtime 调用仍使用 A2A；Custom REST 不成为新的跨 runtime 标准协议。

---

## 8. 错误处理（Process View）

| 错误场景 | 触发条件 | 框架行为 | 对外结果 |
| --- | --- | --- | --- |
| media type 不支持 | 非空 body 未声明 JSON media type，或 Content-Type 不是 JSON | 不解析 body，不调用 `toServeRequest`/orchestrator；只调用 `fromError` 包装错误 | HTTP 415 + `fromError` body |
| 请求解析失败 | body 不是 JSON object | 不调用 orchestrator | HTTP 400 + `fromError` body |
| conversationId 缺失 | adapter 未产出有效 conversationId | 不调用 orchestrator | HTTP 400 + `fromError` body |
| adapter 转换失败 | `toServeRequest` 抛出异常或返回 `null` | 记录完整异常，不调用 orchestrator | HTTP 500 + `fromError` body，固定消息为 `adapter execution failed` |
| 同步响应包装失败 | `fromQueryResponse` 抛出异常 | 记录完整异常，不混同为 runtime 执行异常 | HTTP 500 + `fromError` body，固定消息为 `adapter execution failed` |
| runtime 未就绪 | readiness=false 或无 orchestrator | 拒绝执行 | HTTP 503 + `fromError` body |
| 同步执行异常 | `query()` 抛出异常 | 完整异常只记录到受控日志，传给 adapter 的固定消息为 `agent execution failed` | HTTP 500 + `fromError` body |
| 流内 error chunk | 收到 `TYPE_ERROR` | 经 `fromQueryChunk` 输出并标记已发送 | 一帧 error SSE |
| 流式响应包装失败 | `fromQueryChunk` 抛出异常 | 记录完整异常；若尚未发送错误帧则经 `fromError` 输出 | 一帧 `adapter execution failed` error SSE 后关闭 |
| 流式 terminal error | `onError` 且此前无 error chunk | 经 `fromError` 输出 | 一帧 error SSE 后关闭 |
| 客户端断连 | emitter completion/error | 当前 observer 进入 cancelled；不调用 `fromError`，不触发 conversation 级 `cancelActive` | 停止当前流继续发送；底层执行取消能力以 orchestrator/handler 为准 |

`fromError(...)` 返回 `null` 或抛出运行时异常时，框架使用固定兜底 body：`{"type":"error","status":<HTTP status>,"error":"<脱敏错误消息>"}`。该兜底只保证错误响应可输出，不改变原错误分支的 HTTP status。

错误消息不得直接回显异常原始 message、敏感异常堆栈、认证 header 或 raw body；完整异常只进入受控日志，correlation/trace 由宿主已有设施负责。

---

## 9. 测试与验收

### 9.1 单元测试

| 测试类 | 覆盖点 |
| --- | --- |
| `CustomRestPropertiesTest` | query-path 合法绝对路径、缺失和相对路径校验 |
| `CustomRestProtocolAdapterTest` | context 顶层集合的防御性不可变复制 |

### 9.2 集成测试

| 测试类 | 场景 |
| --- | --- |
| `CustomRestAutoConfigurationTest` | POST method condition、同步/流式执行、HTTP context 提取、media type/JSON 错误、conversationId 缺失和空白校验、adapter 和 runtime 异常分类 |
| `CustomRestDisabledIntegrationTest` | 未配置 query-path 时不要求 adapter bean，且不注册自定义入口 |
| `CustomRestUnavailableIntegrationTest` | agent not ready / no orchestrator 返回 adapter 包装后的 503 |

### 9.3 宿主回归断言

以下内容属于引入该扩展后的宿主应用回归责任，不表示当前模块已有同名专项测试：

- `/v1/query` 仍可用。
- `/a2a` 仍可用。
- custom-rest path 与内置 path 冲突时启动失败。
- custom-rest 通过 `ServeOrchestrator` 进入与 `/v1/query` 相同的 handler/orchestrator 链路。
- custom-rest 与 `/v1/query` 均保持非 Task Query facade 边界，不暴露 GetTask/CancelTask/SubscribeToTask 能力。

---

## 10. 限制与待补

| 限制 | 影响范围 | 临时方案 |
| --- | --- | --- |
| 只支持一个 custom-rest adapter | 不能同时挂多个客户协议 | 多协议场景部署多个 runtime 实例 |
| 只支持 Spring MVC | WebFlux custom endpoint 不自动注册 | 先使用 MVC runtime；如需 WebFlux 后续新增独立 registrar |
| 不返回 A2A JSON-RPC Task 对象 | 客户响应不是标准 A2A Task envelope | adapter 可投影当次 invocation 的 success/error/interrupt，但不得生成 taskId 或宣称权威 Task 生命周期 |
| 不支持 YAML 字段映射 | 不能靠配置声明复杂字段别名 | 在 Java adapter 中实现 |
| 不支持 multipart/file | 文件上传类协议无法直接接入 | 另起版本事实和 L2 设计 |
| 不做认证授权 | custom-rest 只信任前置网关传入身份 | 在网关层完成 OAuth/签名校验 |
| adapter 只控制 body envelope | 不能自定义 status/header/media type/SSE id/retry | 由 Web filter/gateway 扩展；出现稳定需求后再演进结构化响应 SPI |

## 11. 实施结论

当前已在 `agent-solution/common/agent-runtime-ext-java/agent-service-app` 中实现 `agent-service-app-custom-rest` 模块，以 Spring Boot auto-configuration 形式提供一个单 adapter custom-rest 扩展入口。

最终边界是：

```text
YAML 只配 queryPath；入口固定 POST；Java SPI 做转换；ServeOrchestrator 做执行；adapter 包响应。
```

这样可以保持 runtime 主仓执行核心不变，复用 `/v1/query` 现有 handler 调用、远端 A2A delegate 编排和当前流取消能力，同时给平台集成方保留足够自由的请求和响应协议转换能力。该入口始终保持非 Task Query facade 边界。
