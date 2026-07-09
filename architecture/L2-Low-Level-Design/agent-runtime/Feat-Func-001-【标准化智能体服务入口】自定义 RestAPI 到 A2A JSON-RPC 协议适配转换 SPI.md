---
level: L2-LLD
module: agent-runtime-ext-java
feature_type: functional
feature_id: FEAT-015
status: proposed
dependency:
  - openJiuwen/agent-runtime-java
---

# Custom REST API 到 Agent Runtime 执行入口适配 SPI 设计说明

> 目标仓库：`openJiuwen/agent-solution`
> 目标模块：`common/agent-runtime-ext-java/agent-service-app/agent-service-adapters-custom-rest`
> 最后更新：2026-07-09

说明：本文档归档在 `agent-runtime-java` 的开发指南目录，用于描述 runtime 扩展方案；实际代码实现仍落在 `agent-solution` 仓库。

---

## 1. 概述

### 1.1 特性定位

本设计面向 FEAT-015：在 `agent-solution/common/agent-runtime-ext-java` 中新增一个轻量 custom-rest 扩展 starter，使平台集成方可以用自有 REST URL、请求字段和响应信封调用 Agent Runtime。

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

`ServeOrchestrator` 的默认实现可能是 `A2AEnabledServeOrchestrator`。因此 custom-rest 只要和 `/v1/query` 一样构造规范的 `ServeRequest`，就能进入同一条 A2A-aware 执行链路，复用 shadow task、INPUT_REQUIRED、远端 A2A delegate、stream cancel 等语义。

### 1.3 设计原则

1. **YAML 只描述 HTTP 暴露面**：只配置开关、query URL 和 query method，不把字段映射做成 YAML DSL。
2. **转换全部由 Java SPI 实现**：入站请求转换、出站响应信封、SSE chunk 包装、错误包装都由业务实现类掌控。
3. **单 adapter**：当前版本只允许启用一个自定义 REST adapter，不考虑多 adapter 共存、优先级和多路径路由表。
4. **复用 `/v1/query` 执行主链**：转换后直接调用 `ServeOrchestrator`，不调用 `A2aJsonRpcController`，不构造 JSON-RPC 字符串。
5. **agent-solution 扩展仓承载**：custom-rest 作为 `agent-runtime-ext-java` 的独立 Spring Boot auto-configuration module，被宿主应用引入 classpath 后生效。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
| --- | --- | --- | --- |
| 自定义 URL 暴露 | 按 YAML 注册一个 query path 和 query method | `CustomRestProperties`, `CustomRestAutoConfiguration` | proposed |
| 入站转换 SPI | 将 HTTP 上下文转换为 `ServeRequest` | `CustomRestProtocolAdapter.Context` | proposed |
| 出站转换 SPI | 将 `QueryResponse`、`QueryChunk`、错误转换为客户响应 | `CustomRestProtocolAdapter` | proposed |
| 执行桥接 | 复用 runtime 内部 `ServeOrchestrator` | `ServeOrchestrator` | existing |
| A2A task 语义复用 | 通过 `A2AEnabledServeOrchestrator` 复用 A2A-aware 状态维护 | `A2AEnabledServeOrchestrator` | existing |

---

## 2. 特性规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 单个自定义 query URL | proposed | 通过 YAML 配置，例如 `/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}` |
| 自定义 query method | proposed | 通过 YAML 配置，默认 `POST`；用于 Spring MVC endpoint 注册 |
| Java 入站转换 | proposed | 业务代码读取 header/path/query/body，构造 `ServeRequest` |
| Java 出站转换 | proposed | 业务代码包装同步响应、SSE chunk和错误响应 |
| 同步执行 | proposed | 当 adapter 产出的 `ServeRequest.stream=false` 时调用 `ServeOrchestrator.query()` |
| 流式执行 | proposed | 当 adapter 产出的 `ServeRequest.stream=true` 时调用 `ServeOrchestrator.streamQuery()`，返回 SSE |
| A2A-aware 状态语义复用 | proposed | 由于衔接到同一个 `ServeOrchestrator`，继承 `/v1/query` 当前执行路径能力 |
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

- **必须**：custom-rest query path 和 query method 被调用时，扩展解析 HTTP 上下文并调用 `CustomRestProtocolAdapter.toServeRequest(...)`。
- **必须**：adapter 返回的 `ServeRequest` 进入 `ServeOrchestrator.query()` 或 `streamQuery()`。
- **必须**：ready gate 与 `/v1/query` 对齐，`AgentReadiness` 未 ready 时返回 adapter 包装后的 503 错误。
- **必须**：`ServeOrchestrator` 不存在时返回 adapter 包装后的 503 错误。
- **必须**：JSON body 解析失败时返回 adapter 包装后的 400 错误。
- **必须**：流式执行中发生异常时，输出一帧 adapter 包装的 error SSE event 后结束流。
- **允许**：adapter 自行决定外部字段优先级、必填校验、metadata 结构和响应字段名。
- **禁止**：custom-rest 扩展维护独立 task/run/job 状态机。

---

## 3. 核心实现

### 3.1 模块放置

在 `openJiuwen/agent-solution` 新增 ext 子模块：

```text
common/agent-runtime-ext-java/agent-service-app/agent-service-adapters-custom-rest
```

该模块不放在 ext 根目录的 `agent-service-adapters/` 下。`agent-service-adapters/` 当前承载的是 AgentHandler/框架适配类模块（例如 versatile、agentcore-ext）；custom-rest 是北向 HTTP ingress/app 扩展，应该新增 `agent-service-app` 分组。模块名保留 `agent-service-adapters-custom-rest`，用于表达它是对 agent-service-app 北向 REST 入口的可插拔适配扩展，同时前缀与 runtime 既有 `agent-service-*` 命名保持一致。

在 `agent-solution/common/agent-runtime-ext-java/pom.xml` 新增 module：

```xml
<module>agent-service-app/agent-service-adapters-custom-rest</module>
```

模块定位是 Spring Boot auto-configuration starter。宿主应用引入该 jar，并提供一个 `CustomRestProtocolAdapter` bean 后，配置 `openjiuwen.service.custom-rest.enabled=true` 即可注册自定义 REST 入口。`query-method` 默认 `POST`，用于注册 Spring MVC method condition；实际 method 仍会进入 request context，便于 adapter 记录审计或做协议校验。

### 3.2 入站主流程

```text
HTTP {query-method} {query-path}
  -> CustomRestAutoConfiguration 注册的 HandlerMethod
  -> 读取 rawBody
  -> ObjectMapper 将 JSON body 解析为 Map<String,Object>
  -> 从 NativeWebRequest/HttpServletRequest 提取：
       headers
       pathVariables
       queryParams
       rawBody
       bodyMap
       method
       requestPath
  -> CustomRestProtocolAdapter.Context
  -> CustomRestProtocolAdapter.toServeRequest(context)
       ok(ServeRequest) 或 error(status, body/message)
  -> AgentReadiness gate
  -> ServeOrchestrator provider gate
  -> if serveRequest.isStream(): streamQuery(...)
     else: query(...)
  -> CustomRestProtocolAdapter.fromQueryResponse/fromQueryChunk/fromError
```

### 3.3 HTTP method 配置

当前版本框架层只注册一个 `queryPath`，并用 `queryMethod` 作为 Spring MVC method condition。`queryMethod` 默认 `POST`，可按客户协议改为 `PUT`、`DELETE` 等单个 HTTP method。

```text
HTTP {query-method} {query-path}
  -> CustomRestProtocolAdapter.Context.method
  -> CustomRestProtocolAdapter.toServeRequest(context)
  -> ServeRequest
  -> ServeOrchestrator.query/streamQuery
```

设计约束：

| 约束 | 说明 |
| --- | --- |
| 单 method | 当前只注册一个 query method，不支持同一路径多 method 分发 |
| 默认值 | 未配置时使用 `POST` |
| 非匹配 method | 由 Spring MVC 按未匹配 method 处理，不进入 adapter |
| context 保留 method | adapter 仍可读取 `context.method()`，用于审计、日志或协议校验 |

### 3.4 与 A2A task 状态的关系

custom-rest 不生成 A2A JSON-RPC `Task` 响应对象，但它复用执行路径上的 A2A-aware 状态语义。

```text
/custom/url
  -> CustomRestProtocolAdapter
  -> ServeRequest
  -> ServeOrchestrator
     -> A2AEnabledServeOrchestrator
        -> shadow task
        -> INPUT_REQUIRED
        -> remote A2A delegate
        -> stream cancel
```

因此：

| 项 | custom-rest 行为 |
| --- | --- |
| A2A shadow task 保存 | 由 `A2AEnabledServeOrchestrator` 处理 |
| INPUT_REQUIRED / interrupt | 以 `QueryChunk.TYPE_INTERRUPT` 或 `QueryResponse.result._interrupt` 形式返回给 adapter 包装 |
| 远端 A2A delegate | 由 `A2AEnabledServeOrchestrator` 消费 `a2a_delegate` interrupt 并调用远端 |
| JSON-RPC Task 对象 | 不直接返回；如客户响应需要 task-like 字段，由 adapter 从 QueryResponse/QueryChunk 投影 |

### 3.5 SPI 形态

最小 SPI 使用一个接口承载入站和出站转换，并把请求上下文、适配结果定义为接口内嵌类型，避免为纯数据结构单独增加 Java 文件。

```java
public interface CustomRestProtocolAdapter {
    AdaptResult toServeRequest(Context context);

    Object fromQueryResponse(QueryResponse response, Context context, long executionTimeMs);

    Object fromQueryChunk(QueryChunk chunk, Context context);

    Object fromError(int httpStatus, String errorMessage, Context context, long executionTimeMs);

    record AdaptResult(boolean success, ServeRequest request, int httpStatus, String errorMessage) {
        public static AdaptResult ok(ServeRequest request) { ... }
        public static AdaptResult error(int status, String message) { ... }
    }

    record Context(
        String method,
        String requestPath,
        Map<String, String> headers,
        Map<String, String> pathVariables,
        Map<String, List<String>> queryParams,
        Map<String, Object> body,
        String rawBody
    ) {
    }
}
```

`toServeRequest(...)` 是入站转换函数：把客户自定义 REST 请求转换为 runtime 规范 `ServeRequest`。

`fromQueryResponse(...)`、`fromQueryChunk(...)`、`fromError(...)` 是出站转换函数：把 runtime 执行结果转换为客户自定义 REST 响应、SSE chunk 或错误信封。

出站函数返回 `Object`，允许业务返回 `Map<String,Object>`、DTO 或 Jackson 可序列化对象。

### 3.6 SPI Context

`CustomRestProtocolAdapter.Context` 不是转换器，而是一次 HTTP 请求的只读上下文。它是 adapter 入站转换函数的参数，用来承载 method、path、header、path variable、query param、body 和 raw body。它是 SPI 的内嵌 record，不单独拆 Java 文件。

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
| `rawBody` | 保留原始 body，便于客户自定义解析或审计 |

### 3.7 endpoint 注册

由于只支持一个 adapter，注册逻辑放在 `CustomRestAutoConfiguration` 内部即可，不单拆 registrar，也不单拆 MVC endpoint 文件。auto-configuration 可以声明一个内部 handler 对象，并通过 `RequestMappingHandlerMapping.registerMapping(...)` 绑定到 `queryPath/queryMethod`。

启动期行为：

```text
1. properties.enabled != true -> 不注册 endpoint
2. 缺少 CustomRestProtocolAdapter bean -> 启动失败
3. queryPath 为空 -> 启动失败
4. queryMethod 为空 -> 使用默认值 POST
5. queryMethod 非法 -> 启动失败
6. queryPath 与内置路径冲突 -> 启动失败
7. 使用 RequestMappingHandlerMapping.registerMapping(...) 注册 handler method
```

内置冲突路径：

```text
/v1/query
/query
/v1/query/reactive
/v1/reset_conversation
/reset_conversation
/a2a
/a2a/
/.well-known/agent-card.json
/.well-known/agent.json
/a2a/.well-known/agent-card.json
/health
```

只注册一个 queryPath 和一个 queryMethod。Spring MVC mapping 设置 method condition，未命中的 HTTP method 不进入 custom-rest adapter。

### 3.8 SSE 包装

流式调用使用 MVC `SseEmitter`，行为与 `/v1/query` 对齐：

```text
1. response Content-Type = text/event-stream
2. Cache-Control = no-cache, no-transform
3. Connection = keep-alive
4. X-Accel-Buffering = no
5. 每个 QueryChunk 调用 adapter.fromQueryChunk(...)
6. Jackson 序列化后作为 SSE data 输出
7. onError 时输出 adapter.fromError(...) 一帧 error event，然后 complete
```

SSE data 不强制加 JSON-RPC envelope。客户需要什么外部格式，由 `fromQueryChunk` 决定。

---

## 4. 代码结构

### 4.1 新增代码结构

```text
common/agent-runtime-ext-java/agent-service-app/agent-service-adapters-custom-rest
|-- pom.xml
|-- src/main/java/com/openjiuwen/service/adapters/customrest
|   |-- CustomRestProtocolAdapter.java
|   |-- CustomRestProperties.java
|   `-- CustomRestAutoConfiguration.java
`-- src/main/resources/META-INF/spring
    `-- org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

最小主代码 3 个顶层 Java 文件。

Java package 统一使用 `com.openjiuwen.service.adapters.customrest`。虽然模块目录归在 `agent-service-app` 分组下，但代码仍属于 `agent-solution` 扩展仓，不使用 `com.openjiuwen.service.app.*`，避免和 runtime 主仓 app 模块混淆。

| 文件 | 职责 |
| --- | --- |
| `CustomRestProtocolAdapter` | 业务实现的 Java SPI；`toServeRequest` 负责入站转换，`fromQueryResponse/fromQueryChunk/fromError` 负责出站转换；内嵌 `Context` 与 `AdaptResult` |
| `CustomRestProperties` | 绑定 `openjiuwen.service.custom-rest` 配置 |
| `CustomRestAutoConfiguration` | 条件装配、动态注册 mapping、内部 handler、orchestrator/readiness gate、SSE 输出 |
| `AutoConfiguration.imports` | 增加 `CustomRestAutoConfiguration` 自动装配入口 |

### 4.2 Maven 依赖

`agent-service-adapters-custom-rest/pom.xml` 建议依赖：

| 依赖 | scope | 用途 |
| --- | --- | --- |
| `com.openjiuwen:agent-service-spec` | compile | `ServeRequest`、`QueryResponse`、`QueryChunk`、`ServeOrchestrator`、`AgentReadiness` |
| `org.springframework.boot:spring-boot-autoconfigure` | optional | auto-configuration 和 properties |
| `org.springframework.boot:spring-boot-configuration-processor` | optional | 配置元数据生成 |
| `org.springframework:spring-webmvc` | optional | `RequestMappingHandlerMapping`、`SseEmitter` |
| `com.fasterxml.jackson.core:jackson-databind` | compile | body parse 和响应序列化 |
| `org.slf4j:slf4j-api` | compile | 日志 |
| `org.springframework.boot:spring-boot-test` | test | auto-configuration 测试 |
| `org.junit.jupiter:junit-jupiter` | test | 单元测试 |
| `org.assertj:assertj-core` | test | 断言 |

WebMVC 依赖建议设为 optional，并在 auto-config 上增加 `@ConditionalOnClass(RequestMappingHandlerMapping.class)`。这样 ext jar 在非 Web 环境被引入时不会强行拉起 Servlet/MVC 依赖。

### 4.3 静态关系

```text
CustomRestAutoConfiguration
  -> CustomRestProperties
  -> ObjectProvider<CustomRestProtocolAdapter>
  -> ObjectProvider<ServeOrchestrator>
  -> ObjectProvider<AgentReadiness>
  -> ObjectMapper
  -> RequestMappingHandlerMapping.registerMapping

CustomRestProtocolAdapter
  -> ServeRequest
  -> QueryResponse / QueryChunk

CustomRestProtocolAdapter.Context
  -> method/path/headers/pathVariables/queryParams/body/rawBody
```

---

## 5. 运行流程

### 5.1 同步 query

```text
Client
  -> {query-method} {query-path}
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
| JSON parse error | 400 | `fromError(400, "request parse error", ...)` |
| adapter validation error | adapter 指定 | `fromError(status, message, ...)` |
| agent not loaded | 503 | `fromError(503, "agent not loaded", ...)` |
| no orchestrator | 503 | `fromError(503, "no agent handler configured", ...)` |
| orchestrator exception | 500 | `fromError(500, exception message, ...)` |

### 5.2 流式 query

```text
Client
  -> {query-method} {query-path}, body stream=true
  -> CustomRestProtocolAdapter.toServeRequest
  -> ServeRequest(stream=true)
  -> ServeOrchestrator.streamQuery
  -> QueryChunk ...
  -> CustomRestProtocolAdapter.fromQueryChunk(chunk)
  -> SSE data: <json>
  -> onComplete close
```

流式错误：

```text
streamQuery onError / runtime exception
  -> CustomRestProtocolAdapter.fromError(500, message, context, elapsed)
  -> emitter.send(event.name("error").data(json))
  -> emitter.complete()
```

### 5.3 HTTP method 处理

custom-rest 框架通过 `query-method` 预定义唯一入口 method。默认情况下，只有 `POST {query-path}` 会进入 handler；如果配置为 `PUT`，则只有 `PUT {query-path}` 会进入 handler。handler 会将实际 HTTP method 写入 `CustomRestProtocolAdapter.Context.method`。

```text
query-method=POST -> POST {query-path} -> context.method = POST
query-method=PUT  -> PUT  {query-path} -> context.method = PUT
```

adapter 不负责 method 路由，但可以做二次校验：

| 场景 | 行为 |
| --- | --- |
| method 与客户协议不一致 | 返回 `AdaptResult.error(405, "method not allowed")` |
| method 仅用于审计 | 写入 metadata 或日志，不影响转换 |


### 5.4 GHZHY 示例 adapter 行为

GHZHY adapter 可在 Java 中实现以下规则：

| 来源 | 外部字段 | 内部目标 |
| --- | --- | --- |
| path | `project_id` | `metadata.project_id` |
| path | `agent_id` | `metadata.agent_id` |
| path | `conversation_id` | `ServeRequest.conversationId` |
| query | `workspace_id` | `metadata.workspace_id` |
| query | `version` | `metadata.version` |
| body | `input` / `query` | `ServeRequest.messages[0].content` |
| body | `stream` | `ServeRequest.stream` |
| header | `X-User-ID` | `ServeRequest.userId` |
| header | `X-Space-ID` | `ServeRequest.spaceId` |
| header | `X-Tenant-ID` | `ServeRequest.tenantId` |
| body extra | 任意额外字段 | `metadata.<field>` |

响应包装：

```json
{
  "success": true,
  "agent_id": "agent-a",
  "conversion_id": "conv-1",
  "Output": "...",
  "Error": null,
  "execution_time": 123,
  "custom_rsp_data": {}
}
```

---

## 6. 配置使用

### 6.1 配置示例

```yaml
openjiuwen:
  service:
    custom-rest:
      enabled: true
      query-path: /v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
      query-method: POST
```

业务侧提供 adapter bean：

```java
@Bean
CustomRestProtocolAdapter ghzhyCustomRestProtocolAdapter() {
    return new GhzhyCustomRestProtocolAdapter();
}
```

### 6.2 配置属性表

| 属性路径 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.service.custom-rest.enabled` | boolean | `false` | 是否启用 custom-rest endpoint 注册 |
| `openjiuwen.service.custom-rest.query-path` | String | 空 | query endpoint 路径模板；启用时必填 |
| `openjiuwen.service.custom-rest.query-method` | String | `POST` | query endpoint HTTP method；用于注册 Spring MVC method condition |

### 6.3 启用条件

custom-rest auto-configuration 激活条件：

```text
1. classpath 中存在 Spring MVC RequestMappingHandlerMapping
2. openjiuwen.service.custom-rest.enabled=true
3. 容器中存在且仅存在一个 CustomRestProtocolAdapter bean
4. 容器中存在 ObjectMapper
```

`ServeOrchestrator` 和 `AgentReadiness` 不作为启动必需条件；它们按请求时 `ObjectProvider` 获取，与现有 query controller 行为保持一致。缺少 orchestrator 时请求返回 503。

---

## 7. 测试设计

### 7.1 单元测试

| 测试类 | 覆盖点 |
| --- | --- |
| `CustomRestPropertiesTest` | 默认值、enabled/query-path/query-method 绑定、缺 query-path 校验、非法 query-method 校验 |
| `CustomRestProtocolAdapterContextTest` | context 构造规则：header 小写化、query 多值保留、空 body 行为 |
| `CustomRestAutoConfigurationTest` | enabled=false 不注册、缺 adapter 启动失败、路径冲突启动失败、method condition 生效 |

### 7.2 集成测试

| 测试类 | 场景 |
| --- | --- |
| `CustomRestMvcIntegrationTest` | GHZHY 示例路径同步调用成功 |
| `CustomRestSseIntegrationTest` | `stream=true` 返回 SSE，chunk 经 adapter 包装 |
| `CustomRestUnavailableIntegrationTest` | agent not ready / no orchestrator 返回 adapter 包装后的 503 |

### 7.3 回归断言

- `/v1/query` 仍可用。
- `/a2a` 仍可用。
- custom-rest path 与内置 path 冲突时启动失败。
- custom-rest 通过 `ServeOrchestrator` 进入与 `/v1/query` 相同的 handler/orchestrator 链路。

---

## 8. 当前限制

| 限制 | 影响范围 | 临时方案 |
| --- | --- | --- |
| 只支持一个 custom-rest adapter | 不能同时挂多个客户协议 | 多协议场景部署多个 runtime 实例 |
| 只支持 Spring MVC | WebFlux custom endpoint 不自动注册 | 先使用 MVC runtime；如需 WebFlux 后续新增独立 registrar |
| 不返回 A2A JSON-RPC Task 对象 | 客户响应不是标准 A2A Task envelope | adapter 可从 QueryResponse/QueryChunk 投影客户需要的状态字段 |
| 不支持 YAML 字段映射 | 不能靠配置声明复杂字段别名 | 在 Java adapter 中实现 |
| 不支持 multipart/file | 文件上传类协议无法直接接入 | 另起版本事实和 L2 设计 |
| 不做认证授权 | custom-rest 只信任前置网关传入身份 | 在网关层完成 OAuth/签名校验 |

## 9. 实现结论

本设计推荐在 `agent-solution/common/agent-runtime-ext-java/agent-service-app` 中新增 `agent-service-adapters-custom-rest` 模块，以 Spring Boot auto-configuration 形式提供一个单 adapter custom-rest 扩展入口。

最终边界是：

```text
YAML 只配 queryPath 和 queryMethod；Java SPI 做转换；ServeOrchestrator 做执行；adapter 包响应。
```

这样可以保持 runtime 主仓执行核心不变，复用 `/v1/query` 现有 A2A-aware 执行语义，同时给平台集成方保留足够自由的请求和响应协议转换能力。





