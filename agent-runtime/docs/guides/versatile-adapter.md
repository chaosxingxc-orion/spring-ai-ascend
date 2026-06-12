# Versatile 适配器

Versatile 适配器通过 RESTful API 将远端的 Agent 服务代理为 A2A 可路由的 Agent。
适用于独立部署、跨进程、异构语言实现的 Agent 服务。

## 工作原理

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
           │  Accept: text/event-stream
           ▼
┌──────────────────────────┐
│ 远端 Agent 服务（REST）    │
│   └─ SSE 流返回结果        │
└──────────────────────────┘
```

## 快速开始

### Step 1 — 注册 Versatile Handler Bean

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VersatileProperties.class)
public class MyVersatileConfiguration {

    public static final String AGENT_ID = "my-versatile-agent";

    @Bean
    AgentRuntimeHandler versatileHandler(VersatileProperties props) {
        VersatileClient client = new VersatileClient(props);
        return new VersatileAgentRuntimeHandler(
                AGENT_ID,
                "My Versatile Agent",        // A2A 显示名称
                "代理远端的 REST Agent 服务",  // A2A 描述
                client,
                new VersatileMessageAdapter(props),
                new VersatileStreamAdapter());
    }
}
```

`VersatileAgentRuntimeHandler` 同时实现了 `AgentRuntimeHandler`（执行）和
`AgentCardProvider`（A2A 卡片元数据），无需额外注册 AgentCard Bean。

### Step 2 — 配置远端服务连接

```yaml
versatile:
  # 远端 REST API 的 URL 模板
  # {conversation_id} 由 runtime 自动注入
  # 其他 {placeholder} 在 url-variables 中配置
  url: ${VERSATILE_URL:http://7.213.200.213:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}}
  timeout: 30s

  # URL 模板变量
  url-variables:
    project_id: ${VERSATILE_PROJECT_ID:mock_project_id}
    agent_id: ${VERSATILE_AGENT_ID:fb723468-c8ca-424b-a95f-a3e74b37e090}

  # 追加到 URL 的查询参数
  query-params:
    type: ${VERSATILE_QUERY_TYPE:controller}
    workspace_id: ${VERSATILE_WORKSPACE_ID:10}

  # 预配置的 HTTP 请求头（低优先级）
  headers:
    content-type: "application/json"
    stream: "true"

  # 允许 A2A 客户端通过 metadata 透传的请求头
  passthrough-headers:
    - x-invoke-mode
    - x-language

  # 从 A2A metadata 提取并合并到请求 body.inputs 的字段
  input-metadata-keys:
    - intent
    - wap_userName
```

### Step 3 — Spring Boot 入口

```java
@SpringBootApplication(scanBasePackages = {
    "your.package",
    "com.huawei.ascend.runtime.boot"})
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

## 配置详解

### URL 模板

URL 使用 `{placeholder}` 语法，runtime 启动时解析：

```
http://host:port/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
                     ↑                              ↑
               url-variables 中配置           runtime 自动注入
```

`{conversation_id}` 永远是 A2A 的 session id，由 runtime 自动解析。
其他占位符在 `url-variables` map 中配置。

### 请求头模型（两级优先级）

1. **YAML 预配置** `headers`（低优先级）— 静态请求头
2. **A2A 客户端透传**（高优先级）— 从 A2A metadata 提取 `passthrough-headers` 列表中的键

键冲突时 A2A 客户端透传值覆盖 YAML 预配置值。

### 输入元数据映射

`input-metadata-keys` 指定的字段会从 A2A 请求的 `metadata` 中提取，
合并到 REST 请求体的 `inputs` map 中。例如：

A2A 请求 metadata：
```json
{"intent": "订酒店", "wap_userName": "张三"}
```

配置 `input-metadata-keys: [intent, wap_userName]` 后，REST 请求体变为：
```json
{"inputs": {"query": "预订酒店", "intent": "订酒店", "wap_userName": "张三"}}
```

## 请求/响应映射

| 环节 | 组件 | 职责 |
|---|---|---|
| A2A Message → REST 请求 | `VersatileMessageAdapter` | 提取 query、解析 URL 模板、合并 metadata 到 body |
| REST SSE 事件 → AgentExecutionResult | `VersatileStreamAdapter` | 解析 SSE 事件行，映射为框架中立结果 |
| HTTP 调用 | `VersatileClient` | OkHttp 客户端，处理流式响应和错误 |

## 完整配置示例

```yaml
server:
  port: 8080

agent-runtime:
  access:
    a2a:
      default-tenant-id: sample-tenant
      default-agent-id: versatile-agent

versatile:
  url: https://agent-host:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
  timeout: 30s
  url-variables:
    project_id: my-project
    agent_id: fb723468-c8ca-424b-a95f-a3e74b37e090
  query-params:
    type: controller
    workspace_id: "10"
  headers:
    content-type: "application/json"
    stream: "true"
  passthrough-headers:
    - x-invoke-mode
    - x-language
  input-metadata-keys:
    - intent
    - wap_userName
```

## 验证

```bash
# 检查 Agent Card
curl -s http://localhost:8080/.well-known/agent-card.json | jq .

# 流式调用
SESSION_ID="test-$(date +%s)"
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d "{
    \"jsonrpc\": \"2.0\",
    \"method\": \"SendStreamingMessage\",
    \"id\": \"1\",
    \"params\": {
      \"message\": {
        \"role\": \"ROLE_USER\",
        \"messageId\": \"msg-001\",
        \"contextId\": \"$SESSION_ID\",
        \"metadata\": {
          \"userId\": \"test-user\",
          \"agentId\": \"versatile-agent\",
          \"sessionId\": \"$SESSION_ID\",
          \"intent\": \"订酒店\",
          \"wap_userName\": \"张三\"
        },
        \"parts\": [{\"text\": \"预订酒店\"}]
      }
    }
  }" --no-buffer
```

## 相关

- 示例：`examples/agent-runtime-a2a-versatile-e2e/`
- 源码：`agent-runtime/src/main/java/.../engine/versatile/`
- [适配器总览](adapter-overview.md)
- [Handler SPI](handler-spi.md)
- [A2A 端点](a2a-endpoints.md)
