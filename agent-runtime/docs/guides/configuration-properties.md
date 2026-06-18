# 配置属性参考

agent-runtime 支持的全部 `application.yaml` 配置项。按功能分组，每组说明该配置影响什么行为。

## 1. A2A 访问层

### agent-runtime.access.a2a

控制 A2A 协议入口行为。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `agent-runtime.access.a2a.default-tenant-id` | String | `default` | 租户标识。无 `X-Tenant-Id` 头时使用此值，贯穿 MDC 和 AgentExecutionContext |
| `agent-runtime.access.a2a.default-agent-id` | String | — | 默认 Agent ID |
| `agent-runtime.access.a2a.public-base-url` | String | — | Agent Card 中返回给客户端的绝对 URL。为空时从请求自动检测。反向代理后必配此项 |

### agent-runtime.access.a2a.agent-card

控制 `GET /.well-known/agent-card.json` 返回的卡片内容。所有字段可选，未设置使用合理默认值。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `agent-runtime.access.a2a.agent-card.name` | String | handler.agentId() | Agent 名称 |
| `agent-runtime.access.a2a.agent-card.description` | String | `agent-runtime` | Agent 描述 |
| `agent-runtime.access.a2a.agent-card.version` | String | `0.1.0` | Agent 版本 |
| `agent-runtime.access.a2a.agent-card.organization` | String | `spring-ai-ascend` | 组织名 |
| `agent-runtime.access.a2a.agent-card.organization-url` | String | `http://localhost:8080` | 组织 URL |
| `agent-runtime.access.a2a.agent-card.endpoint` | String | `/a2a` | A2A JSON-RPC 端点路径 |
| `agent-runtime.access.a2a.agent-card.skills` | List | — | Skill 声明。远程 Agent 发现后根据 skills 注册为 Tool。无 skills 的 Agent 不会被远端注入为 Tool |
| `agent-runtime.access.a2a.agent-card.capabilities` | Map | — | 能力宣告（streaming / pushNotifications） |

## 2. 远程 Agent 连接

### agent-runtime.remote-agents

配置远程 A2A Agent 端点。至少配置一个 `url` 才会激活远程 Agent 客户端。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `agent-runtime.remote-agents[N].url` | String | — | 远程 Agent base URL（必填以激活）。支持 `http://host` / `http://host/` / `http://host/.well-known/agent-card.json` 三种形式 |
| `agent-runtime.remote-agents[N].stream-timeout` | Duration | — | 流式调用超时。超时后返回 `REMOTE_TIMEOUT` 错误，best-effort 取消远程 Task |
| `agent-runtime.remote-agents[N].output.default-target` | String | — | 默认输出目标：`USER` / `LLM` / `BOTH` |
| `agent-runtime.remote-agents[N].output.completion-target` | String | — | 完成时输出目标 |

**行为影响**：配置后 runtime 启动时自动拉取远程 Agent Card，按自适应策略刷新（10s 快速 → 600s 保活）。Card 中的 `skills` 自动生成 `RemoteAgentToolSpec` 并安装为本地 Agent 的 Tool。无 skills 的远程 Agent 不会被注入为 Tool。

### agent-runtime.remote-invocation

控制远程 A2A Tool 调用的编排保护。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `agent-runtime.remote-invocation.max-legs` | int | `5` | 单次父任务内允许串行执行的远程 A2A 调用段数。小于 1 时按 1 处理，大于 100 时按 100 处理 |

**行为影响**：当本地 Agent 在 `REMOTE_RESUME` 后继续发起远程 A2A 调用时，runtime 会串行执行多个远程调用段，直到本地 Agent 完成、远程端要求输入、任务取消或超过 `max-legs`。超过限制时父任务返回 `REMOTE_INVOCATION_LIMIT_EXCEEDED` 错误，避免模型循环导致父任务长期占用。

## 3. 轨迹可观测性

### app.trajectory

控制 Agent 执行轨迹的记录和掩码行为。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `app.trajectory.enabled` | boolean | `true` | 启用轨迹记录。关闭后不做事件收集和掩码 |
| `app.trajectory.mask.key-pattern` | String | `(?i)(key\|token\|secret\|password\|api_key\|credential)` | 敏感 key 正则。匹配到的 Map key 对应的值替换为 `***` |
| `app.trajectory.mask.truncate-chars` | int | `0` | 长字符串截断阈值。0=不截断 |
| `app.trajectory.otel.enabled` | boolean | `false` | 启用 OpenTelemetry 导出（需 OTel SDK 在 classpath） |
| `app.trajectory.otel.endpoint` | String | — | OTLP 端点地址 |

**行为影响**：`enabled=true` 时，每次 Agent 执行自动生成事件序列（RUN_START/END、MODEL_CALL、TOOL_CALL、ERROR 等）。`mask.key-pattern` 对轨迹事件和日志同时生效。

## 4. Versatile 适配器

### versatile

控制 Versatile 适配器与远端 REST 服务的连接。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `versatile.url` | String | — | 远端 REST API 的 URL 模板。`{conversation_id}` 自动注入，其他占位符用 `url-variables` 替换 |
| `versatile.timeout` | Duration | — | HTTP 请求超时 |
| `versatile.url-variables` | Map | — | URL 模板变量（如 `project_id`、`agent_id`） |
| `versatile.query-params` | Map | — | 静态查询参数，可被 A2A structured metadata 覆盖 |
| `versatile.headers` | Map | — | YAML 预配置的 HTTP 头（低优先级） |
| `versatile.passthrough-headers` | List | — | 允许 A2A 客户端通过 metadata 透传的 HTTP 头白名单 |
| `versatile.input-metadata-keys` | List | — | 从 A2A metadata 提取并合并到 REST body `inputs` 的字段 |
| `versatile.result-extractions` | List | — | 结果提取规则列表（`match` 关键字 + `get` key 深度查找） |

**行为影响**：`url` 必配。`headers` + `passthrough-headers` 构成两级透传模型；A2A structured `metadata.versatile.headers` 为第三级（最高优先级）。`result-extractions` 定义从非标准 SSE 事件中提取结构化结果的规则。

## 5. OpenJiuwen LLM 连接

### sample.openjiuwen

OpenJiuwen Agent 的 LLM 连接配置（示例项目约定前缀，生产自定义）。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `sample.openjiuwen.model-provider` | String | `openai` | 模型提供商：`openai` / `ollama` / `openai-compatible` |
| `sample.openjiuwen.api-key` | String | — | LLM API Key（必配） |
| `sample.openjiuwen.api-base` | String | — | LLM API 地址（必配） |
| `sample.openjiuwen.model-name` | String | `gpt-5.4-mini` | 模型名称 |
| `sample.openjiuwen.ssl-verify` | boolean | `true` | TLS 证书校验 |
| `sample.openjiuwen.checkpointer` | String | `in-memory` | Checkpoint 后端：`in-memory` / `redis` |
| `sample.openjiuwen.redis-url` | String | `redis://localhost:6379` | checkpointer=redis 时必填 |

## 6. 服务端

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `server.port` | int | `8080` | HTTP 端口 |
| `server.shutdown` | String | `immediate` | 设为 `graceful` 启用优雅关闭 |

## 7. 日志

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `logging.level.root` | String | `INFO` | 根日志级别 |
| `logging.level.com.huawei.ascend` | String | `INFO` | Runtime 日志级别 |

## 8. 健康检查

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `management.endpoints.web.exposure.include` | String | — | 暴露的端点，建议 `health,info` |

## 9. 完整配置示例

```yaml
server:
  port: 8080
  shutdown: graceful

agent-runtime:
  access:
    a2a:
      default-tenant-id: my-tenant
      public-base-url: https://agents.example.com/runtime
      agent-card:
        name: my-agent
        description: 我的 Agent，由 agent-runtime 托管
        version: "1.0.0"
        skills:
          - id: my-skill
            name: My Skill
            description: 这个 skill 会被远程 Agent 发现并注册为 Tool
        capabilities:
          streaming: true
  remote-agents:
    - url: http://weather-agent:18081
      stream-timeout: 30s
  remote-invocation:
    max-legs: 5

app:
  trajectory:
    enabled: true
    mask:
      key-pattern: "(?i)(key|token|secret|password|api_key|credential|phone|email)"
      truncate-chars: 0

sample:
  openjiuwen:
    model-provider: openai
    api-key: ${LLM_API_KEY}
    api-base: https://api.openai.com/v1
    model-name: gpt-5.4-mini
    ssl-verify: true
    checkpointer: in-memory

logging:
  level:
    com.huawei.ascend: DEBUG
```
