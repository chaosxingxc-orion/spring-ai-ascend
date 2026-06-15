# Versatile 适配器

通过 RESTful API 将远端 Agent 服务代理为 A2A 可路由的 Agent。适用于独立部署、跨进程、异构语言实现的 Agent 服务。

## 1. 概述

```java
// 最小示例：注册 Versatile Handler
@Bean AgentRuntimeHandler versatileHandler(VersatileProperties props) {
    VersatileClient client = new VersatileClient(props);
    return new VersatileAgentRuntimeHandler("my-agent", "My Agent",
        "代理远端 REST 服务", client,
        new VersatileMessageAdapter(props), new VersatileStreamAdapter());
}
```

## 2. 快速开始

### Step 1 — 注册 Handler Bean

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VersatileProperties.class)
public class MyVersatileConfiguration {
    public static final String AGENT_ID = "my-versatile-agent";

    @Bean AgentRuntimeHandler versatileHandler(VersatileProperties props) {
        VersatileClient client = new VersatileClient(props);
        return new VersatileAgentRuntimeHandler(AGENT_ID, "My Versatile Agent",
            "代理远端的 REST Agent 服务", client,
            new VersatileMessageAdapter(props), new VersatileStreamAdapter());
    }
}
```

`VersatileAgentRuntimeHandler` 同时实现 `AgentRuntimeHandler` 和 `AgentCardProvider`，无需额外注册 AgentCard Bean。

### Step 2 — 配置远端连接

```yaml
versatile:
  url: ${VERSATILE_URL:http://host:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}}
  timeout: 30s
  url-variables:
    project_id: ${VERSATILE_PROJECT_ID:mock_project_id}
    agent_id: ${VERSATILE_AGENT_ID:fb723468-c8ca-424b-a95f-a3e74b37e090}
  query-params:
    type: controller
  headers:
    content-type: "application/json"
    stream: "true"
  passthrough-headers:
    - x-language
  input-metadata-keys:
    - intent
```

### Step 3 — 启动

```java
@SpringBootApplication(scanBasePackages = {"your.package", "com.huawei.ascend.runtime.boot"})
public class MyApplication {
    public static void main(String[] args) { SpringApplication.run(MyApplication.class, args); }
}
```

## 3. 工作原理

```
A2A 客户端
    │  SendStreamingMessage
    ▼
┌──────────────────────────┐
│ agent-runtime             │
│   ├─ 接收 A2A JSON-RPC    │
│   ├─ 转换为 REST 请求      │
│   ├─ 代理解析 SSE 响应     │
│   └─ 映射回 A2A 事件       │
└──────────┬───────────────┘
           │  POST /v1/.../conversations/{id}
           ▼
┌──────────────────────────┐
│ 远端 Agent 服务（REST）    │
│   └─ SSE 流返回结果        │
└──────────────────────────┘
```

类协作：

```
VersatileAgentRuntimeHandler.execute(context)
  ├─ VersatileMessageAdapter.toRequest(context) → VersatileHttpRequest
  │     ├─ URL: 模板替换 {conversation_id} + query params
  │     ├─ Headers: 三级优先级（YAML < flat metadata < structured versatile.headers）
  │     └─ Body: {"inputs":{...}} — text JSON 优先
  ├─ VersatileClient.stream(request) → Stream<String> (SSE lines)
  └─ VersatileStreamAdapter.adapt(rawStream) → Stream<AgentExecutionResult>
```

## 4. 核心接口

`VersatileAgentRuntimeHandler` 实现两个接口：

| 接口 | 方法 | 用途 |
|------|------|------|
| `AgentRuntimeHandler` | `execute(ctx)` | A2A 请求 → REST 调用 → 结果流 |
| `AgentCardProvider` | `agentCard()` | 内建 Skill 描述，供远程主 Agent 发现并注册为 Tool |

## 5. 能力详述

### URL 模板

```yaml
versatile:
  url: http://host:port/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
  url-variables:
    project_id: my-project
    agent_id: fb723468-c8ca-424b-a95f-a3e74b37e090
```

`{conversation_id}` 由 runtime 自动注入（= sessionId），其他占位符在 `url-variables` 中配置。

### Header 透传（三级优先级）

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 低 | `versatile.headers` (YAML) | 部署时预设 |
| 中 | A2A flat metadata（passthrough allowlist） | 白名单内透传 |
| 高 | `metadata.versatile.headers`（structured） | 用户显式指定，白名单控制 |

### 输入元数据映射

`input-metadata-keys` 从 A2A metadata 提取字段合并到 REST body：

A2A metadata：`{"intent": "订酒店", "wap_userName": "张三"}` → REST body：`{"inputs": {"query": "...", "intent": "订酒店", "wap_userName": "张三"}}`

### 请求/响应映射

| 环节 | 组件 | 职责 |
|------|------|------|
| A2A Message → REST 请求 | `VersatileMessageAdapter` | 提取 query、URL 模板、合并 metadata 到 body |
| REST SSE → AgentExecutionResult | `VersatileStreamAdapter` | 解析 SSE 事件行，映射为框架中立结果 |
| HTTP 调用 | `VersatileClient` | JDK HttpClient，流式响应和错误处理 |

## 6. 完整示例

```yaml
server:
  port: 8080

agent-runtime:
  access:
    a2a:
      agent-card:
        name: versatile-agent
        description: 代理远端 Workflow 引擎的 Agent
        skills:
          - id: workflow-proxy
            name: Workflow Proxy
            description: Call this tool to invoke a remote workflow

versatile:
  url: https://agent-host:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
  timeout: 30s
  url-variables:
    project_id: my-project
    agent_id: fb723468-c8ca-424b-a95f-a3e74b37e090
  query-params:
    type: controller
  headers:
    content-type: "application/json"
    stream: "true"
  passthrough-headers:
    - x-language
  input-metadata-keys:
    - intent
  result-extractions:
    - match: booking_success
      get: ticket
```

验证：

```bash
curl -s http://localhost:8080/.well-known/agent-card.json | jq .
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"SendStreamingMessage","id":"1",
    "params":{"message":{"role":"ROLE_USER","messageId":"m1",
    "metadata":{"userId":"u1","agentId":"versatile-agent","intent":"订酒店"},
    "parts":[{"text":"预订酒店"}]}}}' --no-buffer
```

## 7. 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `versatile.url` | String | — | URL 模板（必配） |
| `versatile.timeout` | Duration | — | HTTP 超时 |
| `versatile.url-variables` | Map | — | URL 模板变量 |
| `versatile.query-params` | Map | — | 静态查询参数 |
| `versatile.headers` | Map | — | YAML 预配置头（低优先级） |
| `versatile.passthrough-headers` | List | — | A2A 透传头白名单 |
| `versatile.input-metadata-keys` | List | — | 从 A2A metadata 提取并合并到 body 的键 |
| `versatile.result-extractions` | List | — | match → get 结果提取规则 |

## 8. SKILL.md 最佳实践

当 Versatile 子 Agent 需要被主 Agent 作为 Tool 调用时，通过 SKILL.md 引导 LLM 正确填充参数。

### 编写 SKILL.md

位置：`skills/<name>/SKILL.md`：

```markdown
---
name: versatile-request
description: 按目标 REST API 的 inputs 结构直接输出完整 JSON
---

# Versatile 请求体组装

调用时传入完整的请求体 JSON：{"inputs":{"query":"...","intent":"..."}}

## 字段

| 字段 | 说明 | 示例 |
|------|------|------|
| `query` | JSON 字符串，业务参数 | `"{\"person_name\":\"李四\"}"` |
| `intent` | 操作意图 | `"订酒店"` / `"LATEST"` |

## 示例

{"inputs":{"query":"{\"person_name\":\"李四\"}","intent":"订酒店"}}
```

### 注册 Skill

```java
agent.registerSkill("skills");  // 注册 skill 目录，自动注入摘要到 system prompt
```

### LLM 调用流程

```
1. System Prompt → 告知 LLM 有 skill，用 readFile 读取
2. LLM → readFile("skills/hotel-booking/SKILL.md")
3. LLM → 按 SKILL.md 提取参数 → tool call
4. A2A 层 → 调用 Versatile 子 Agent → 结果返回
```

## 9. 限制

| 限制 | 影响 |
|------|------|
| URL 模板中的非 `{conversation_id}` 占位符需在 `url-variables` 中预先配置 | 动态 URL 参数需通过 query-params 或 structured metadata 传递 |
| SSE 事件映射依赖远端服务的 event 名称约定 | 非标准事件名需通过 `result-extractions` 规则匹配 |

## 10. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/heterogeneous-agent-framework-compatibility.md` §6
- [Adapter 开发](handler-spi.md)
- [A2A 协议调用](a2a-endpoints.md)
- [Agent Card 配置](agent-card-configuration.md)
- Example：`examples/agent-runtime-a2a-versatile-e2e/`、`examples/agent-runtime-a2a-versatile-parent-e2e/`
