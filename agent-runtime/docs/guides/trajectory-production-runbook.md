# 轨迹可观测性 — 生产环境最佳实践 Runbook

> **适用范围**：agent-runtime v0.2.0 的两条发射轨道（NDJSON 结构化日志 + OTLP Span），面向生产环境的收集、存储和查询最佳实践。
> **相关文档**：[轨迹可观测性概念指南](trajectory-observability.md) · [配置属性参考](configuration-properties.md) · [L2 设计文档](../../architecture/docs/L2/agent-runtime/trajectory-observability-design.md)

---

## 1. 架构概览

```
agent-runtime（发射侧，emit-only）
  │
  ├── OTLP Span → OTel Collector → Jaeger / Tempo / ClickHouse / Langfuse
  └── NDJSON 行日志 → Fluent Bit / Filebeat → Loki / Elasticsearch / ClickHouse
```

**架构红线**（不可越过）：

- agent-runtime **只发射**，不回读、不存储、不提供查询 API。
- 轨迹数据的持久化、保留策略、查询面由外部 OSS 负责。
- 发射失败（OTel Collector 不可达、磁盘满）**不影响**业务运行。

---

## 2. OTLP Span 轨道

### 2.1 开启配置

```yaml
app:
  trajectory:
    enabled: true
    otel:
      enabled: true
      endpoint: "http://otel-collector:4317"  # gRPC OTLP，支持覆盖
    sample-rate: 0.1  # 头部采样：10% 调用写入完整轨迹 span 树
```

### 2.2 OTel Collector 参考配置

```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  memory_limiter:
    check_interval: 1s
    limit_mib: 512
  batch:
    send_batch_size: 512
    timeout: 5s
  tail_sampling:        # 按采样策略过滤；头部采样已在 runtime 侧
    decision_wait: 10s  # 等待整条 trace 到齐再决策
    policies:
      - name: errors-policy
        type: status_code
        status_code: { status_codes: [ERROR] }
      - name: latency-policy
        type: latency
        latency: { threshold_ms: 3000 }

exporters:
  otlp/jaeger:
    endpoint: "http://jaeger:4317"
    tls:
      insecure: true
  otlp/tempo:
    endpoint: "http://tempo:4317"
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch, tail_sampling]
      exporters: [otlp/tempo]
```

### 2.3 Span 属性速查

| OTel 属性 | 来源字段 | 用途 |
|---|---|---|
| `trajectory.trace_id` | `traceId` = `taskId` | 按 taskId 检索完整轨迹 |
| `trajectory.tenant_id` | `tenantId` | 按租户聚合 |
| `gen_ai.system` | `usage.provider` | 模型厂商 |
| `gen_ai.response.finish_reasons` | `finishReason` | 完成原因 |
| `gen_ai.error.type` | `error.category` | 错误分类（对齐 OpenTelemetry GenAI 语义约定） |
| `gen_ai.server.time_to_first_token` | `MODEL_CALL_FIRST_TOKEN.durationMs` / 1000 | TTFT（秒） |
| `trajectory.parent_task_id` | `parentTaskId` | 跨 run 父链路 |

---

## 3. NDJSON 结构化日志轨道

### 3.1 开启配置

```yaml
app:
  trajectory:
    enabled: true
    log:
      enabled: true
    mask:
      key-pattern: "(?i)(key|token|secret|password|api_key|credential)"
      truncate-chars: 256
```

日志写入专用 Logger `com.huawei.ascend.runtime.trajectory`，以 `INFO` 级别输出单行 JSON。

### 3.2 Logback 分离配置（推荐）

```xml
<!-- logback-spring.xml 片段 -->
<appender name="TRAJECTORY" class="ch.qos.logback.core.rolling.RollingFileAppender">
  <file>${LOG_PATH}/trajectory.ndjson</file>
  <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
    <fileNamePattern>${LOG_PATH}/trajectory.%d{yyyy-MM-dd}.ndjson</fileNamePattern>
    <maxHistory>30</maxHistory>
    <totalSizeCap>10GB</totalSizeCap>
  </rollingPolicy>
  <encoder>
    <pattern>%msg%n</pattern>  <!-- 消息已是完整 JSON，不加额外格式 -->
  </encoder>
</appender>

<logger name="com.huawei.ascend.runtime.trajectory" level="INFO" additivity="false">
  <appender-ref ref="TRAJECTORY"/>
</logger>
```

### 3.3 Fluent Bit 采集配置（→ Loki）

```ini
[INPUT]
    Name              tail
    Path              /var/log/app/trajectory.ndjson
    Tag               trajectory
    multiline.parser  json  # 每行已是完整 JSON
    Rotate_Wait       5

[FILTER]
    Name      parser
    Match     trajectory
    Key_Name  log
    Parser    json

[OUTPUT]
    Name            loki
    Match           trajectory
    Host            loki
    Port            3100
    Labels          job=trajectory,env=prod
    Label_Keys      $tenantId,$kind
    Batch_size      1048576
    Line_Format     json
```

### 3.4 Filebeat 采集配置（→ Elasticsearch）

```yaml
filebeat.inputs:
  - type: filestream
    id: trajectory
    paths:
      - /var/log/app/trajectory.ndjson
    parsers:
      - ndjson:
          target: ""
          add_error_key: true

output.elasticsearch:
  hosts: ["https://es:9200"]
  index: "trajectory-%{+yyyy.MM.dd}"
  pipeline: "trajectory-ingest-pipeline"

# Ingest Pipeline（示例）：按 tenantId 路由索引
PUT _ingest/pipeline/trajectory-ingest-pipeline
{
  "processors": [
    { "date": { "field": "tsEpochMillis", "formats": ["UNIX_MS"], "target_field": "@timestamp" } },
    { "remove": { "field": "tsEpochMillis" } }
  ]
}
```

---

## 4. 存储后端选型

| 需求场景 | 推荐后端 | 说明 |
|---|---|---|
| 追踪 / Span 树可视化 | **Jaeger** / **Grafana Tempo** | 接受 OTLP，按 traceId 检索 span 树 |
| LLM 原生可观测（成本 / 评估） | **Langfuse** / **Phoenix (Arize)** | 理解 gen_ai.* 语义约定，支持 prompt/cost 聚合 |
| 大规模时序分析 | **ClickHouse** | OTLP Exporter 直写，低成本列式存储，适合多租户 cost 聚合 |
| 日志全文检索 | **Elasticsearch / OpenSearch** | NDJSON 行逐条索引，支持正则查询 |
| 日志流聚合（低成本） | **Grafana Loki** | 按 label (tenantId/kind) 分流，配合 LogQL 查询 |

**明确非目标**：agent-runtime 轨迹存储 ≠ 框架 Checkpoint/持久化（框架内部运行状态由 AgentScope/AgentService 管理，与可观测轨迹完全分离）。

**数据保留建议**：

- 生产：保留 30–90 天完整轨迹；90 天后聚合保留分钟级统计。
- 存储估算：每次 Run 约 5–20 事件，每事件平均 < 1 KB（经 truncate-chars=256 后），10k 并发 TPS ≈ 10–20 MB/s raw。

---

## 5. 查询 Cookbook

### 5.1 按 taskId 取完整 Span 树（Jaeger / Tempo UI）

```
traceId = <taskId>
```

`taskId` 即 `traceId`，直接在 Jaeger / Tempo 的 "Search by Trace ID" 输入框粘贴。

### 5.2 NDJSON 按 taskId 查询（Loki LogQL）

```logql
{job="trajectory"} | json | taskId="<your-task-id>"
```

### 5.3 按租户聚合 token 用量（ClickHouse SQL）

```sql
SELECT
    tenantId,
    usage_model   AS model,
    usage_provider AS provider,
    SUM(usage_inputTokens)  AS total_input_tokens,
    SUM(usage_outputTokens) AS total_output_tokens,
    SUM(usage_costMicros) / 1e6 AS total_cost_currency_unit
FROM trajectory_events
WHERE kind = 'MODEL_CALL_END'
  AND toDate(fromUnixTimestamp64Milli(tsEpochMillis)) >= today() - 30
GROUP BY tenantId, model, provider
ORDER BY total_cost_currency_unit DESC;
```

### 5.4 错误率监控（Elasticsearch / ES|QL）

```esql
FROM trajectory-*
| WHERE kind == "ERROR"
| STATS error_count = COUNT(*) BY tenantId, error_category = error.category
| SORT error_count DESC
```

### 5.5 时延分位数（Loki / Grafana Panel）

```logql
# RUN_END.durationMs 分布
quantile_over_time(0.95, {job="trajectory"} | json | kind="RUN_END" | unwrap durationMs [5m]) by (tenantId)
```

### 5.6 TTFT 趋势（Grafana Tempo / Metrics）

`MODEL_CALL_FIRST_TOKEN` 事件的 `gen_ai.server.time_to_first_token` attribute（单位秒）已由 OtelSpanSink 导出为 span attribute。在 Grafana Tempo Metrics 中：

```promql
histogram_quantile(0.95, rate(gen_ai_server_time_to_first_token_bucket[5m]))
```

### 5.7 跨 run 父链路追踪

对于 multi-agent 级联场景，每个子 run 的轨迹事件携带 `parentTaskId`，在 Jaeger 中：

```
parentTaskId = <caller-task-id>
```

或通过 Elasticsearch 查：

```esql
FROM trajectory-*
| WHERE parentTaskId == "<caller-task-id>"
| KEEP taskId, kind, tsEpochMillis, tenantId
```

---

## 6. 大载荷外置（payload_ref://）

当推理输出或 args 超过 `truncate-chars` 阈值时，配置 `payload-ref` 外置：

```yaml
app:
  trajectory:
    mask:
      truncate-chars: 512       # 低于此长度正常记录
    payload-ref:
      enabled: true
      directory: /data/trajectory-payloads  # 挂载共享存储（NFS/S3-FUSE）
```

超阈值的字符串 payload 写入 `<directory>/<sha256>.json`，事件中记录 `{"payload_ref": "payload_ref://sha256..."}`。SHA-256 内容寻址，相同内容只写一份。

**生产多节点**：`directory` 须挂载为所有实例共享的持久化存储（NFS、S3-FUSE、CSI PVC）。单节点开发场景使用本地目录即可。

---

## 7. 值级内容脱敏

在 `truncate-chars` 关键词掩码的基础上，开启值级检测：

```yaml
app:
  trajectory:
    redact:
      enabled: true  # 检测信用卡（Luhn）/ SSN / GPS 坐标
```

`PatternRedactor` 扫描字符串叶子节点，递归处理 Map/List 结构，命中即替换为 `***`。

---

## 8. FinOps 成本记录

```yaml
app:
  trajectory:
    pricing:
      enabled: true
      models:
        gpt-4o:
          provider: openai
          input-micros-per-token: 5      # $5/M tokens → 5 micros/token
          output-micros-per-token: 15
        claude-3-sonnet:
          provider: anthropic
          input-micros-per-token: 3
          output-micros-per-token: 15
```

`MODEL_CALL_END` 事件的 `usage.costMicros` 字段（整数微货币单位，避免浮点舍入）即可在 ClickHouse / Kibana 直接聚合为 FinOps 报表，无需在消费侧重新计算。

---

## 9. 完整最小化生产配置模板

```yaml
app:
  trajectory:
    enabled: true
    sample-rate: 0.1            # 10% 头部采样，高流量建议调低
    mask:
      key-pattern: "(?i)(key|token|secret|password|api_key|credential|card|ssn)"
      truncate-chars: 256
    otel:
      enabled: true
      endpoint: "http://otel-collector:4317"
    log:
      enabled: true             # NDJSON 写 com.huawei.ascend.runtime.trajectory logger
    redact:
      enabled: true             # 值级 Luhn/SSN/GPS 脱敏
    pricing:
      enabled: false            # 按需开启
    payload-ref:
      enabled: false            # 大载荷外置，共享存储时开启
```

---

## 10. 告警建议

| 指标 | 建议阈值 | 说明 |
|---|---|---|
| ERROR 事件率 | > 5% 告警 | `kind=ERROR` 占总事件比例 |
| RUN_END.durationMs p99 | > 30s 告警 | 根据 SLA 调整 |
| MODEL_CALL_FIRST_TOKEN p95 | > 5s 告警 | TTFT 退化 |
| TRUNCATED 截断事件 | 出现即告警 | A2A buffer 10k 溢出，考虑降采样 |
| payload-ref 写入失败 | 日志 `WARN trajectory.payload-ref` | 存储已满或权限不足 |
