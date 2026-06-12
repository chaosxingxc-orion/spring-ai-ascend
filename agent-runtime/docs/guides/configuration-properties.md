# Configuration Properties

All application.yaml properties recognized by agent-runtime.

## agent-runtime.access.a2a

| Property | Type | Default | Description |
|---|---|---|---|
| `default-tenant-id` | String | `default` | Tenant for requests without X-Tenant-Id |
| `default-agent-id` | String | — | Fallback agent id |
| `public-base-url` | String | — | External base URL for agent card. Auto-detected from request when blank. |

## agent-runtime.access.a2a.agent-card

All fields optional. Unset fields draw sensible defaults.

| Property | Type | Default | Description |
|---|---|---|---|
| `name` | String | handler.agentId() | Agent name in the A2A card |
| `description` | String | `agent-runtime` | Human-readable description |
| `version` | String | `0.1.0` | Agent version |
| `organization` | String | `spring-ai-ascend` | Provider organization name |
| `organization-url` | String | `http://localhost:8080` | Provider organization URL |
| `endpoint` | String | `/a2a` | A2A JSON-RPC endpoint path |

## sample.openjiuwen

openJiuwen LLM connection settings.

| Property | Type | Default | Env override |
|---|---|---|---|
| `model-provider` | String | `openai` | `SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER` |
| `api-key` | String | `sk-local-placeholder` | `SAA_SAMPLE_LLM_API_KEY` |
| `api-base` | String | `http://localhost:4000/v1` | `SAA_SAMPLE_OPENJIUWEN_API_BASE` |
| `model-name` | String | `gpt-5.4-mini` | `SAA_SAMPLE_LLM_MODEL` |
| `ssl-verify` | boolean | `true` | `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY` |
| `checkpointer` | String | `in-memory` | `SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER` |
| `redis-url` | String | `redis://localhost:6379` | `SAA_SAMPLE_OPENJIUWEN_REDIS_URL` |

## server

Standard Spring Boot server properties.

| Property | Type | Default | Description |
|---|---|---|---|
| `server.port` | int | `8080` | HTTP listen port |
| `server.shutdown` | String | `immediate` | `graceful` for graceful shutdown |

## logging

| Property | Type | Default | Description |
|---|---|---|---|
| `logging.level.root` | String | `INFO` | Root log level |
| `logging.level.com.huawei.ascend` | String | `INFO` | Runtime log level |

## app.trajectory

Observability settings.

| Property | Type | Default | Description |
|---|---|---|---|
| `app.trajectory.enabled` | boolean | `true` | Enable trajectory events |
| `app.trajectory.default-level` | String | `SUMMARY` | `OFF` / `SUMMARY` / `FULL` |
| `app.trajectory.mask.truncate-chars` | int | `256` | Truncate message content in events |
| `app.trajectory.otel.enabled` | boolean | `false` | Export trajectory spans via OTLP |
| `app.trajectory.otel.endpoint` | String | `http://localhost:4317` | OTLP collector endpoint |

## app.runs.dispatch

Run dispatch executor settings.

| Property | Type | Default | Description |
|---|---|---|---|
| `app.runs.dispatch.core-threads` | int | `4` | Core thread pool size |
| `app.runs.dispatch.max-threads` | int | `16` | Max thread pool size |
| `app.runs.dispatch.queue-capacity` | int | `256` | Work queue capacity |
| `app.runs.dispatch.rejection-policy` | String | `CALLER_RUNS` | `CALLER_RUNS` / `ABORT` |

## Complete example

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
        description: My agent served by agent-runtime
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
