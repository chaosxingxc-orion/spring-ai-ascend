# Versatile Adapter 设计

> 文档归档：`architecture/docs/L2/agent-runtime/versatile-adapter-design.md`  
> 目标模块：`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/versatile/`  
> 设计日期：2026-06-10

---

## 1. 概述

Versatile Adapter 是一个 agent-runtime 引擎层的内置适配器，其职责是将 A2A（Agent-to-Agent）客户端通过 JSON-RPC 发来的请求转换为 RESTful 请求，转发到远端 Versatile 工作流服务；将远端返回的 SSE（Server-Sent Events）流式响应转换回 agent-runtime 的标准 `AgentExecutionResult`，再经由 A2A SDK 返回给调用方。

**核心理念**：Versatile Adapter 是一个**协议转换代理**——前端是 A2A JSON-RPC，后端是 Versatile REST/SSE，adapter 在这两者之间做双向转换。

### 1.1 设计原则

1. **模块闭环**：所有代码在 `engine/versatile/` 目录下，不穿越模块边界
2. **遵从 SPI 契约**：实现 `AgentRuntimeHandler` + `AgentCardProvider`，无缝接入 agent-runtime
3. **配置驱动**：远端连接参数通过 `application.yml` 注入，header 采用两级优先级（YAML 预配置 + A2A 客户端透传覆盖）
4. **可扩展**：消息适配器和流适配器均可被子类化覆盖

---

## 2. 模块结构

```
agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/versatile/
├── VersatileAgentRuntimeHandler.java   // 主 Handler，实现 AgentRuntimeHandler + AgentCardProvider
├── VersatileMessageAdapter.java        // 输入转换：AgentExecutionContext → REST 请求（URL/headers/body）
├── VersatileStreamAdapter.java         // 输出转换：Flux<String> SSE 行 → Stream<AgentExecutionResult>
├── VersatileClient.java                // HTTP 客户端：调用远端 Versatile REST API，返回 SSE Flux
└── VersatileProperties.java            // 配置属性类，含 header 两级优先级模型
```

### 2.1 类图（简化）

```
┌──────────────────────────────────────────┐
│          AgentRuntimeHandler (SPI)        │
│  + agentId(): String                     │
│  + execute(AgentExecutionContext): Stream│
│  + resultAdapter(): StreamAdapter        │
│  + isHealthy(): boolean                  │
│  + providers(): List<AgentRuntimeProvider>│
└──────────────────┬───────────────────────┘
                   │ implements
┌──────────────────▼───────────────────────┐
│     VersatileAgentRuntimeHandler          │
│──────────────────────────────────────────│
│  - agentId: String                       │
│  - name: String                          │
│  - description: String                   │
│  - client: VersatileClient               │
│  - messageAdapter: VersatileMessageAdapter│
│  - streamAdapter: VersatileStreamAdapter │
│──────────────────────────────────────────│
│  + agentId(): String                     │
│  + execute(context): Stream<?>           │
│  + resultAdapter(): StreamAdapter        │
│  + isHealthy(): boolean                  │
│  + agentCard(): AgentCard                │  ◄── AgentCardProvider
└──────────────────────────────────────────┘
         │                    │
         ▼                    ▼
┌─────────────────┐  ┌──────────────────────┐
│VersatileMessage │  │ VersatileStreamAdapter│
│    Adapter      │  │──────────────────────│
│─────────────────│  │+ adapt(Stream<?>):    │
│+ toRequest(ctx) │  │  Stream<AgentExec...> │
│ : VersatileReq  │  │- mapEvent(json):      │
└────────┬────────┘  │  AgentExecutionResult │
         │            │- isIntermediateNode()│
         ▼            └──────────────────────┘
┌─────────────────┐
│ VersatileClient │
│─────────────────│
│+ stream(req):   │
│  Flux<String>   │
└─────────────────┘
```

---

## 3. 输入路径：A2A Client → Versatile REST

### 3.1 数据流

```
A2A Client (JSON-RPC)
  │
  │  POST /a2a
  │  {
  │    "jsonrpc": "2.0",
  │    "method": "tasks/send",
  │    "params": {
  │      "id": "task-001",
  │      "message": {
  │        "role": "user",
  │        "parts": [{ "text": "转账" }]
  │      },
  │      "contextId": "test-001",
  │      "metadata": {
  │        "x-invoke-mode": "DEBUG",
  │        "x-language": "zh-cn",
  │        "cftk": "my-auth-token"
  │      }
  │    }
  │  }
  │
  ▼
A2aAgentExecutor.toExecutionContext(RequestContext)
  │  构建 AgentExecutionContext:
  │    scope    = RuntimeIdentity("default", "system", "test-001", "task-001", "versatile-agent")
  │    messages = [Message(ROLE_USER, [TextPart("转账")])]
  │    variables = Map.of("x-invoke-mode", "DEBUG", "x-language", "zh-cn", "cftk", "my-auth-token")
  │    agentStateKey = "task-001"
  │
  ▼
VersatileAgentRuntimeHandler.execute(context)
  │
  ▼
VersatileMessageAdapter.toRequest(context)
  │  返回 VersatileHttpRequest:
  │    url: https://{host}:{port}/v1/0/agent-manager/workflows/{workflow_id}
  │          /conversations/{sessionId}?workspace_id={workspace_id}
  │    method: POST
  │    headers: { Accept: application/json, text/event-stream,
  │              Content-Type: application/json,
  │              Stream: true,
  │              X-Invoke-Mode: DEBUG,
  │              X-Language: zh-cn,
  │              Cftk: my-auth-token }
  │    body: { inputs: { query: "转账" },
  │            memory_inputs: {},
  │            globals: {},
  │            plugin_configs: [],
  │            version: 1772196378200,
  │            long_term_memory: { enable_retrieve: true, enable_extract: true } }
  │
  ▼
VersatileClient.stream(request)
  │  通过 WebClient 发起 HTTP POST，流式读取响应 SSE
  │  返回 Flux<String>（每行为一个 data: 行或原始文本行）
```

### 3.2 输入关键映射

#### 3.2.1 URL 构建

| URL 组成部分 | 来源 | 说明 |
|---|---|---|
| `host` / `port` / `ssl` | `VersatileProperties` | YAML 配置的远端地址 |
| `/v1/0/agent-manager/workflows/` | 固定路径前缀 | Versatile REST API 规范 |
| `{workflow_id}` | `VersatileProperties.workflowId` | YAML 配置 |
| `/conversations/` | 固定路径 | — |
| `{conversation_id}` | `AgentExecutionContext.getScope().sessionId()` | 对应 A2A 的 `contextId` |
| `workspace_id` | `VersatileProperties.workspaceId` | URL 查询参数 |

#### 3.2.2 请求体构建

```java
// VersatileMessageAdapter.toRequestBody(context)
Map<String, Object> body = new LinkedHashMap<>();
body.put("inputs", Map.of("query", lastUserText(context.getMessages())));
body.put("memory_inputs", Map.of());
body.put("globals", Map.of());
body.put("plugin_configs", List.of());
body.put("version", properties.getVersion());
body.put("long_term_memory", properties.getLongTermMemory());
```

- `inputs.query` 从 `context.getMessages()` 提取最后一条 `ROLE_USER` 消息的文本内容
- `version` / `long_term_memory` 来自 `VersatileProperties` 配置

#### 3.2.3 Header 构建（两级优先级）

Header 有两个来源，冲突时 A2A 客户端传入的值覆盖 YAML 预配置：

```
来源 1（低优先级）：YAML 预配置 headers  — 部署时预设的默认值（如固定鉴权 token、语言等）
来源 2（高优先级）：A2A Client 传入     — metadata 中的 key，通过 passthrough-headers
                                        白名单控制哪些 key 可透传（不在白名单的忽略）
                                        A2A 传入的值覆盖 YAML 同名字段
```

实现伪代码：

```java
Map<String, String> finalHeaders = new LinkedHashMap<>();
// Step 1: 先写入 YAML 预配置（低优先级）
finalHeaders.putAll(properties.getHeaders());
// Step 2: A2A 透传覆盖（高优先级 — 白名单内 + A2A 实际传入的 key）
Map<String, Object> a2aMetadata = context.getVariables();
for (String passthroughKey : properties.getPassthroughHeaders()) {
    Object value = a2aMetadata.get(passthroughKey);
    if (value != null) {
        finalHeaders.put(toHeaderName(passthroughKey), String.valueOf(value));
    }
}
```

| 优先级 | 来源 | 使用场景 |
|---|---|---|
| 低 | `versatile.headers` (YAML) | 部署时预设的固定 header、鉴权 token、语言等默认值 |
| 高 | A2A Client metadata | 客户端动态传入的值（白名单内），覆盖 YAML 同名字段 |

---

## 4. 输出路径：Versatile SSE → AgentExecutionResult

### 4.1 数据流

```
Versatile 远端 SSE 流
  │
  │  data:{"event":"workflow_started","data":{...}}
  │  data:{"event":"node_started","data":{"node_name":"开始","node_type":"Start",...}}
  │  data:{"event":"node_finished","data":{"node_name":"开始",...}}
  │  data:{"event":"node_started","data":{"node_name":"问答","node_type":"QA",...}}
  │  data:{"event":"message","data":{"text":"这是第0个问题","node_type":"QA",...}}
  │  data:{"event":"message","data":{"text":"","summary":"这是第0个问题","is_finished":true,...}}
  │  data:{"event":"node_finished","data":{"node_type":"QA","outputs":{"response":"标准答案"},...}}
  │  data:{"event":"workflow_finished","data":{"outputs":{"responseContent":"恭喜你..."},...}}
  │  data:{"event":"end","createdTime":...}
  │
  ▼
VersatileClient.stream(request) → Flux<String>
  │  每行为一条 SSE 行（"data:{...}"），或原始文本
  │
  ▼
VersatileStreamAdapter.adapt(Flux<String>)
  │  逐行解析 JSON，按 event 字段分派：
  │
  │  "message"  → AgentExecutionResult.output(text)
  │  "workflow_finished" → AgentExecutionResult.completed(responseContent)
  │  "end" (无前置 workflow_finished) → AgentExecutionResult.completed("")
  │  "exception" → AgentExecutionResult.failed(code, message)
  │  其余事件（workflow_started/node_started/node_finished）→ 过滤，不产出
  │
  ▼
Stream<AgentExecutionResult>
  │
  ▼
A2aAgentExecutor.route(result, emitter)
  │  OUTPUT    → emitter.sendMessage(text), state=WORKING
  │  COMPLETED → emitter.complete(text),   state=COMPLETED
  │  FAILED    → emitter.fail(),           state=FAILED
  │
  ▼
A2A Client (JSON-RPC)
```

### 4.2 SSE 事件映射规则

| SSE `event` 字段 | 条件 | `AgentExecutionResult` | A2A emitter | 说明 |
|---|---|---|---|---|
| `message` | `text` 非空 | `output(text)` | `sendMessage(text)` | 流式文本块 => `WORKING` |
| `message` | `is_finished=true` | `output(summary ∥ text)` | `sendMessage(...)` | 节点结束的摘要消息，仍为 OUTPUT |
| `workflow_finished` | — | `completed(outputs.responseContent)` | `complete(text)` | 工作流正常结束 => `COMPLETED` |
| `end` | 无前置 `workflow_finished` | `completed("")` | `complete()` | 简洁结束（非 debug 模式常见） |
| `exception` | — | `failed(code, message)` | `fail()` | 工作流异常 => `FAILED` |
| `workflow_started` | — | 过滤（无产出） | — | 启动元信息 |
| `node_started` | — | 过滤（无产出） | — | 中间节点开始 |
| `node_finished` | — | 过滤（无产出） | — | 中间节点结束 |

### 4.3 过滤策略

中间事件（`workflow_started`、`node_started`、`node_finished`）默认不产出 `AgentExecutionResult`，避免 A2A 客户端收到无意义的内部节点状态。

**可配置项**（在 `VersatileStreamAdapter` 中）：  
- `verboseEvents: true` — 开启后，所有事件均产出为 `output(...)`，用于调试
- `filterIntermediateNodes: true`（默认） — 过滤中间事件

### 4.4 流结束判定

- 当收到 `workflow_finished` → 产出 `completed`，流结束
- 当收到 `end` 且未收到 `workflow_finished` → 产出 `completed("")`，流结束
- 当收到 `exception` → 产出 `failed`，流结束
- HTTP 超时 → 产出 `failed("TIMEOUT", "...")`，流结束

---

## 5. 配置模型（VersatileProperties）

### 5.1 完整配置结构

```yaml
versatile:
  # ============= REST 远端连接 =============
  host: 100.93.15.185
  port: 30001
  ssl: true                          # true→https, false→http

  # ============= Versatile 工作流标识 =============
  workflow-id: "17bf8748-d35d-41da-ad61-9cf53f6bb4b3"
  workspace-id: "38e96fe921ff4daf8af01bd3529346c6"

  # ============= 超时 =============
  timeout: 30s                       # HTTP 连接/读取超时

  # ============= 请求体固定字段 =============
  version: 1772196378200
  long-term-memory:
    enable-retrieve: true
    enable-extract: true

  # ============= Header：YAML 预配置（低优先级）=============
  # 部署时预设的默认值，可被 A2A 客户端传入的同名 header 覆盖
  headers:
    accept: "application/json, text/event-stream"
    content-type: "application/json"
    stream: "true"
    x-language: "zh-cn"
    # 示例：部署时注入固定业务标识
    # x-business-line: "finance"

  # ============= Header：A2A 透传白名单（高优先级）=============
  # A2A Client metadata 中的 key 在此名单内的才会透传到 REST 请求头
  # 不在白名单的 key 被忽略（安全）
  # 透传的值会覆盖 YAML headers 中的同名字段
  passthrough-headers:
    - x-invoke-mode
    - x-language
    - cftk
    - cf2-cftk
    - cookie
    - x-user-id
    - x-project-id
```

### 5.2 Java 配置类

```java
@ConfigurationProperties(prefix = "versatile")
public class VersatileProperties {
    // REST connection
    private String host;
    private int port = 30001;
    private boolean ssl = true;

    // Workflow identity
    private String workflowId;
    private String workspaceId;

    // Timeout
    private Duration timeout = Duration.ofSeconds(30);

    // Body fixed fields
    private long version;
    private Map<String, Boolean> longTermMemory = Map.of(
            "enable_retrieve", true,
            "enable_extract", true);

    // Header model: YAML pre-config (low priority) + A2A passthrough (high priority, overrides)
    private Map<String, String> headers = new LinkedHashMap<>();
    private List<String> passthroughHeaders = List.of();
}
```

---

## 6. 对外呈现（Agent Card）

`VersatileAgentRuntimeHandler` 同时实现 `AgentCardProvider`，向 A2A 协议层注册 Agent Card 描述：

```java
@Override
public AgentCard agentCard() {
    return AgentCards.create(name, description, "0.1.0", "/a2a");
}
```

生成的 Agent Card 结构：

```json
{
  "name": "versatile-agent",
  "description": "Versatile workflow proxy agent",
  "url": "/a2a",
  "version": "0.1.0",
  "provider": {
    "name": "spring-ai-ascend",
    "url": "http://localhost:8080"
  },
  "capabilities": {
    "streaming": true,
    "pushNotifications": true,
    "extendedAgentCard": false
  },
  "defaultInputModes": ["text"],
  "defaultOutputModes": ["text", "artifact"],
  "skills": [],
  "supportedInterfaces": [
    {
      "transport": "jsonrpc",
      "url": "/a2a"
    }
  ],
  "preferredTransport": "jsonrpc"
}
```

外部 A2A 客户端通过 `GET /a2a/.well-known/agent-card/versatile-agent` 获取此卡信息，然后通过标准 JSON-RPC `POST /a2a` 调用。

---

## 7. 用户可见示例

### 7.1 输入示例

#### A2A Client 发送的 JSON-RPC 请求

```json
{
  "jsonrpc": "2.0",
  "method": "tasks/send",
  "params": {
    "id": "task-001",
    "message": {
      "role": "user",
      "parts": [
        { "text": "转账" }
      ]
    },
    "contextId": "test-001",
    "metadata": {
      "x-invoke-mode": "DEBUG",
      "x-language": "zh-cn",
      "cftk": "my-auth-token"
    }
  }
}
```

> **关键字段说明**：
> - `params.message.parts[*].text` → Versatile 的 `inputs.query`（提取最后一条 user 消息的文本）
> - `params.contextId` → Versatile URL 的 `conversations/{conversation_id}`
> - `params.metadata` → 按 `passthrough-headers` 白名单透传到 REST 请求头

#### 转换后的 REST 请求（等价 curl）

```bash
curl 'https://100.93.15.185:30001/v1/0/agent-manager/workflows/17bf8748-d35d-41da-ad61-9cf53f6bb4b3/conversations/test-001?workspace_id=38e96fe921ff4daf8af01bd3529346c6' \
  -H 'accept: application/json, text/event-stream' \
  -H 'content-type: application/json' \
  -H 'stream: true' \
  -H 'x-invoke-mode: DEBUG' \
  -H 'x-language: zh-cn' \
  -H 'cftk: my-auth-token' \
  --data-raw '{"inputs":{"query":"转账"},"memory_inputs":{},"globals":{},"plugin_configs":[],"version":1772196378200,"long_term_memory":{"enable_retrieve":true,"enable_extract":true}}'
```

### 7.2 输出示例

#### 场景 A：正常流式对话（带问答节点）

| 步骤 | 远端 SSE (data:) | AgentExecutionResult | A2A 客户端收到 |
|---|---|---|---|
| 1 | `{"event":"workflow_started","data":{...}}` | (过滤，无产出) | — |
| 2 | `{"event":"node_started","data":{"node_name":"开始",...}}` | (过滤，无产出) | — |
| 3 | `{"event":"message","data":{"text":"这是第0个问题","node_type":"QA",...}}` | `output("这是第0个问题")` | `sendMessage("这是第0个问题")` |
| 4 | `{"event":"message","data":{"text":"","summary":"这是第0个问题","is_finished":true,...}}` | `output("这是第0个问题")` | `sendMessage("这是第0个问题")` |
| 5 | `{"event":"end","createdTime":...}` | `completed("")` | `complete()` — 任务结束 |

#### 场景 B：正常完成（含 Branch + End）

| 步骤 | 远端 SSE (data:) | AgentExecutionResult | A2A 客户端收到 |
|---|---|---|---|
| 1–N | ... QA node messages ... | `output(...)` × N | 流式文本逐条推送 |
| N+1 | `{"event":"message","data":{"text":"恭喜你， ","node_type":"End",...}}` | `output("恭喜你， ")` | 流式推送 |
| N+2 | `{"event":"message","data":{"text":"标准答案","node_type":"End",...}}` | `output("标准答案")` | 流式推送 |
| N+3 | `{"event":"message","data":{"text":" 是正确答案","node_type":"End",...}}` | `output(" 是正确答案")` | 流式推送 |
| N+4 | `{"event":"message","data":{"text":"","summary":"恭喜你， 标准答案 是正确答案","is_finished":true,...}}` | `output("恭喜你， 标准答案 是正确答案")` | 最终摘要 |
| N+5 | `{"event":"workflow_finished","data":{"outputs":{"responseContent":"恭喜你， 标准答案 是正确答案"},...}}` | `completed("恭喜你， 标准答案 是正确答案")` | `complete("恭喜你， 标准答案 是正确答案")` |

#### 场景 C：异常退出

| 步骤 | 远端 SSE (data:) | AgentExecutionResult | A2A 客户端收到 |
|---|---|---|---|
| 1 | `{"event":"node_finished","data":{"node_type":"QA","outputs":{"response":"错误答案"},...}}` | (过滤) | — |
| 2 | `{"event":"node_started","data":{"node_name":"异常","node_type":"StructuredMessagesException",...}}` | (过滤) | — |
| 3 | `{"event":"exception","data":{"code":"ERR-1001","message":"数据库连接失败"}}` | `failed("ERR-1001", "数据库连接失败")` | `fail()` — 任务失败 |
| 4 | `{"event":"end","createdTime":...}` | 不被产出（流已在第 3 步结束） | — |

---

## 8. 开发者使用指南

### 8.1 引入依赖

Versatile Adapter 是 agent-runtime SDK 的内置模块（`com.huawei.ascend:agent-runtime`），不需要额外 Maven 坐标。

```xml
<!-- 已存在：主依赖 -->
<dependency>
    <groupId>com.huawei.ascend</groupId>
    <artifactId>agent-runtime</artifactId>
</dependency>
```

### 8.2 最小配置启动

#### Step 1 — application.yml

```yaml
versatile:
  host: 100.93.15.185
  port: 30001
  ssl: true
  workflow-id: "17bf8748-d35d-41da-ad61-9cf53f6bb4b3"
  workspace-id: "38e96fe921ff4daf8af01bd3529346c6"
  timeout: 30s
  version: 1772196378200
```

#### Step 2 — 注册 Spring Bean

```java
package com.example.agent.config;

import com.huawei.ascend.runtime.engine.versatile.*;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VersatileProperties.class)
public class VersatileAgentConfiguration {

    @Bean
    public AgentRuntimeHandler versatileAgent(VersatileProperties props) {
        VersatileClient client = new VersatileClient(props);
        return new VersatileAgentRuntimeHandler(
                "versatile-agent",               // agentId — A2A 路由依据
                "Versatile Agent",               // Agent Card 显示名
                "Versatile workflow proxy agent",// Agent Card 描述
                client,
                new VersatileMessageAdapter(props),
                new VersatileStreamAdapter()
        );
    }
}
```

#### Step 3 — 启动即生效

agent-runtime 在启动时自动扫描所有 `AgentRuntimeHandler` Bean，将 `VersatileAgentRuntimeHandler` 注册到 `AgentRuntimeProviderChain`。Agent Card 通过 A2A SDK 自动暴露。

无需额外启动步骤。`isHealthy()` 默认返回 `true`（可通过子类覆盖实现健康检查）。

### 8.3 自定义 Header（高级）

Header 有两级来源：YAML 预配置（低优先级）和 A2A 客户端输入（高优先级，覆盖 YAML 同名字段）。

```yaml
versatile:
  host: 100.93.15.185
  port: 30001
  ssl: true
  workflow-id: "17bf8748-d35d-41da-ad61-9cf53f6bb4b3"
  workspace-id: "38e96fe921ff4daf8af01bd3529346c6"

  # YAML 预配置（低优先级）— 部署时预设的固定 header
  headers:
    accept: "application/json, text/event-stream"
    content-type: "application/json"
    stream: "true"
    x-language: "zh-cn"          # 默认语言，可被 A2A 客户端覆盖
    x-business-line: "finance"   # 注入默认业务标识

  # A2A 透传白名单（高优先级）— 客户端传入的这些 key 覆盖 YAML 同名字段
  passthrough-headers:
    - x-invoke-mode
    - x-language               # 客户端可覆盖 YAML 的语言设置
    - cftk
    - cf2-cftk
    - cookie
```

### 8.4 自定义流适配器（高级）

如果远端 Versatile 工作流的事件格式有差异（例如使用了自定义 node_type），开发者可以继承 `VersatileStreamAdapter` 覆盖映射逻辑：

```java
package com.example.agent.config;

import com.huawei.ascend.runtime.engine.versatile.VersatileStreamAdapter;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.Map;

public class MyVersatileStreamAdapter extends VersatileStreamAdapter {

    @Override
    protected AgentExecutionResult mapEvent(Map<String, Object> json) {
        String event = (String) json.getOrDefault("event", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) json.get("data");

        return switch (event) {
            case "message" -> handleMessage(data);
            case "workflow_finished" -> handleWorkflowFinished(data);
            case "exception" -> handleException(data);
            case "my_custom_event" -> handleMyCustomEvent(data);  // 自定义
            default -> null;  // 过滤
        };
    }

    private AgentExecutionResult handleMyCustomEvent(Map<String, Object> data) {
        String text = (String) data.getOrDefault("text", "");
        return AgentExecutionResult.output("[Custom] " + text);
    }
}
```

然后在 Bean 注册时传入：

```java
@Bean
public AgentRuntimeHandler versatileAgent(VersatileProperties props) {
    VersatileClient client = new VersatileClient(props);
    return new VersatileAgentRuntimeHandler(
            "versatile-agent", "Versatile Agent",
            "Custom versatile proxy", client,
            new VersatileMessageAdapter(props),
            new MyVersatileStreamAdapter()   // 使用自定义 adapter
    );
}
```

### 8.5 调用方式

Agent 注册后，外部 A2A Client 通过标准端点调用：

```bash
# 查询 Agent Card
curl 'http://localhost:8080/a2a/.well-known/agent-card/versatile-agent'

# 发送任务
curl -X POST 'http://localhost:8080/a2a' \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "method": "tasks/send",
    "params": {
      "id": "my-task-001",
      "message": {
        "role": "user",
        "parts": [{"text": "转账"}]
      },
      "contextId": "conversation-123",
      "metadata": {
        "x-invoke-mode": "DEBUG",
        "cftk": "auth-token-xxx"
      }
    }
  }'
```

> **说明**：`contextId` 值会映射为 Versatile 工作流的 `conversation_id`。同一个 `contextId` 的多轮对话会被 Versatile 识别为同一会话，从而保持上下文。

---

## 9. 错误处理

| 错误场景 | 行为 | A2A 结果 |
|---|---|---|
| `VersatileProperties` 未配置 `host` | `execute()` 前抛出 `IllegalStateException`，emitter 调用 `fail()` | FAILED |
| HTTP 连接超时 | `VersatileClient` 捕获 `TimeoutException` → 产出 `AgentExecutionResult.failed("TIMEOUT", "...")` | FAILED |
| HTTP 4xx/5xx 响应 | `VersatileClient` 读取 error body → `AgentExecutionResult.failed("HTTP_{code}", body)` | FAILED |
| SSE 行 JSON 解析失败 | 跳过该行，记录 WARN 日志，流继续 | 该行被丢弃，继续处理后续行 |
| 流中收到 `exception` 事件 | `AgentExecutionResult.failed(code, message)` → 流结束 | FAILED |
| 流自然结束（无 `end` 或 `workflow_finished`） | 超时时间内无新行 → `AgentExecutionResult.completed("")` | COMPLETED |

---

## 10. 待实现文件清单

| 文件 | 所在包 | 说明 |
|---|---|---|
| `VersatileProperties.java` | `...engine.versatile` | 配置属性类，Spring Boot `@ConfigurationProperties` |
| `VersatileClient.java` | `...engine.versatile` | HTTP 客户端，基于 `WebClient` 连接远端 Versatile REST API，返回 `Flux<String>` |
| `VersatileMessageAdapter.java` | `...engine.versatile` | 输入转换：`AgentExecutionContext` → URL / headers / body |
| `VersatileStreamAdapter.java` | `...engine.versatile` | 输出转换：实现 `StreamAdapter`，将 `Flux<String>` SSE 行映射为 `AgentExecutionResult` |
| `VersatileAgentRuntimeHandler.java` | `...engine.versatile` | 实现 `AgentRuntimeHandler` + `AgentCardProvider`，组合上述组件 |
