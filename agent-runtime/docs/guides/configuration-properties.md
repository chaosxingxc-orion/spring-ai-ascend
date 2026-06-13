# 配置属性

agent-runtime 支持的全部 application.yaml 属性。

## agent-runtime.access.a2a

访问层设置。

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `default-tenant-id` | String | `default` | 无 X-Tenant-Id 头时的租户标识 |
| `default-agent-id` | String | — | 默认 Agent ID |
| `public-base-url` | String | — | Agent Card 的外部基础 URL。为空时从请求自动检测 |

## agent-runtime.access.a2a.agent-card

A2A Agent Card 元数据。所有字段可选，未设置使用合理默认值。

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `name` | String | handler.agentId() | A2A 卡片中的 Agent 名称 |
| `description` | String | `agent-runtime` | 可读的描述文本 |
| `version` | String | `0.1.0` | Agent 版本号 |
| `organization` | String | `spring-ai-ascend` | 提供商组织名 |
| `organization-url` | String | `http://localhost:8080` | 提供商组织 URL |
| `endpoint` | String | `/a2a` | A2A JSON-RPC 端点路径 |

## sample.openjiuwen

openJiuwen LLM 连接设置。

| 属性 | 类型 | 默认值 | 环境变量 |
|---|---|---|---|
| `model-provider` | String | `openai` | `SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER` |
| `api-key` | String | `sk-local-placeholder` | `SAA_SAMPLE_LLM_API_KEY` |
| `api-base` | String | `http://localhost:4000/v1` | `SAA_SAMPLE_OPENJIUWEN_API_BASE` |
| `model-name` | String | `gpt-5.4-mini` | `SAA_SAMPLE_LLM_MODEL` |
| `ssl-verify` | boolean | `true` | `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY` |
| `checkpointer` | String | `in-memory` | `SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER` |
| `redis-url` | String | `redis://localhost:6379` | `SAA_SAMPLE_OPENJIUWEN_REDIS_URL` |

## server

Spring Boot 标准服务端属性。

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `server.port` | int | `8080` | HTTP 监听端口 |
| `server.shutdown` | String | `immediate` | 设为 `graceful` 启用优雅关闭 |

## logging

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `logging.level.root` | String | `INFO` | 根日志级别 |
| `logging.level.com.huawei.ascend` | String | `INFO` | Runtime 日志级别 |

## management

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `management.endpoints.web.exposure.include` | String | — | 暴露的端点，建议 `health,info` |

## 完整配置示例

```yaml
server:
  port: 8080
  shutdown: graceful

agent-runtime:
  access:
    a2a:
      default-tenant-id: my-tenant
      default-agent-id: my-agent
      public-base-url: https://agents.example.com/runtime
      agent-card:
        name: my-agent
        description: 我的 Agent，由 agent-runtime 托管
        version: "1.0.0"

sample:
  openjiuwen:
    model-provider: openai
    api-key: ${LLM_API_KEY}
    api-base: https://api.openai.com/v1
    model-name: gpt-5.4-mini
    ssl-verify: true

logging:
  level:
    com.huawei.ascend: DEBUG
```
