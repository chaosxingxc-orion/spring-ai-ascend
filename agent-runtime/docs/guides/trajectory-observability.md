# 轨迹可观测性

agent-runtime 执行 Agent 时自动记录执行轨迹——模型调用、工具调用、错误、进度等事件，支持敏感信息掩码。

## 1. 概述

```yaml
# 最小示例：默认开启，无需配置
# 日志中自动出现模型调用、工具调用等轨迹事件，敏感字段自动掩码
```

轨迹系统是框架中立的：各 Adapter 自动记录事件，runtime 统一完成时间戳、Span 嵌套树和掩码。

## 2. 快速开始

### 自定义掩码规则

```yaml
app:
  trajectory:
    enabled: true
    mask:
      key-pattern: "(?i)(key|token|secret|password|api_key|credential|phone|email)"
      truncate-chars: 200
```

配置后，轨迹事件中匹配 `phone` 或 `email` 的字段值被替换为 `***`，超过 200 字符的字符串被截断。

### 验证

```bash
# 发送请求后检查日志，确认敏感字段已掩码
# 日志中应出现 RUN_START、MODEL_CALL_START 等事件
```

## 3. 工作原理

```
Adapter 捕获原生回调
  │
  ├─ OpenJiuwenTrajectoryRail: beforeModelCall/afterModelCall/...
  ├─ AgentScope: OUTPUT → PROGRESS, FAILED → ERROR
  │
  ▼
StampingTrajectoryEmitter
  ├─ 分配单调序列号
  ├─ 构建 Span 嵌套树 (traceId/spanId/parentSpanId)
  ├─ wall-clock 时间戳
  └─ 敏感字段掩码
  │
  ▼
CompositeTrajectorySink → 多个后端（OTel / 北向投递 / 自定义）
```

## 4. 核心接口

Adapter 开发者通过 `TrajectoryDraft` 工厂方法提交事件：

```java
trajectory.emit(TrajectoryDraft.modelCallStart());
trajectory.emit(TrajectoryDraft.modelCallEnd(usage, finishReason, reasoning));
trajectory.emit(TrajectoryDraft.toolCallStart(toolName, args));
trajectory.emit(TrajectoryDraft.toolCallEnd(toolResult));
trajectory.emit(TrajectoryDraft.error(kind, code, message, retryAttempt, retryable));
```

## 5. 事件类型

| Kind | 含义 | 覆盖 Adapter |
|------|------|-------------|
| RUN_START / RUN_END | 调用边界 | 所有 |
| MODEL_CALL_START / MODEL_CALL_END | 模型调用（tokens、延迟、模型名、finishReason） | OpenJiuwen |
| MODEL_CALL_FIRST_TOKEN | 首 Token 时刻（durationMs 即 TTFT） | AgentScope（流式） |
| TOOL_CALL_START / TOOL_CALL_END | 工具调用 | OpenJiuwen、AgentScope |
| REASONING | 推理过程（Chain-of-Thought） | OpenJiuwen |
| ERROR | 执行错误（分类、详情、可重试） | 所有 |
| PROGRESS | 进度更新 | AgentScope |

MODEL_CALL_END 携带 `Usage` 记录：`inputTokens` / `outputTokens` / `latencyMs` / `model` / `provider` / `costMicros`（整数微货币）。`finishReason` 对齐 OpenTelemetry `gen_ai.response.finish_reasons`。`error.category` 对齐 `gen_ai.error.type`。

## 6. 完整示例

```yaml
app:
  trajectory:
    enabled: true
    mask:
      key-pattern: "(?i)(key|token|secret|password|api_key|credential)"
      truncate-chars: 0

agent-runtime:
  access:
    a2a:
      agent-card:
        name: my-agent
```

预期结果：每次 Agent 调用自动生成完整事件序列，日志中敏感字段被掩码：

```
[INFO] trajectory seq=1 kind=RUN_START taskId=t1
[INFO] trajectory seq=2 kind=MODEL_CALL_START taskId=t1
[INFO] trajectory seq=3 kind=MODEL_CALL_END taskId=t1 tokens=150 latency=1.2s
[INFO] trajectory seq=4 kind=RUN_END taskId=t1
```

## 7. 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `app.trajectory.enabled` | boolean | `true` | 启用轨迹记录 |
| `app.trajectory.sample-rate` | double | `1.0` | 头部采样率（0.0–1.0），整条轨迹 in/out，不撕裂 Span 树 |
| `app.trajectory.mask.key-pattern` | String | 见文档 | 敏感 key 正则，匹配值替换为 `***` |
| `app.trajectory.mask.truncate-chars` | int | `256` | 长字符串截断阈值 |
| `app.trajectory.otel.enabled` | boolean | `false` | 启用 OTLP Span 导出 |
| `app.trajectory.otel.endpoint` | String | `http://localhost:4317` | OTLP gRPC 端点 |
| `app.trajectory.log.enabled` | boolean | `false` | 启用 NDJSON 结构化日志导出 |
| `app.trajectory.redact.enabled` | boolean | `false` | 值级脱敏（信用卡 Luhn / SSN / GPS） |
| `app.trajectory.pricing.enabled` | boolean | `false` | 模型 token 成本计算 |
| `app.trajectory.pricing.models.<id>.provider` | String | — | 模型厂商（= gen_ai.system） |
| `app.trajectory.pricing.models.<id>.input-micros-per-token` | long | `0` | 输入 token 单价（整数微货币） |
| `app.trajectory.pricing.models.<id>.output-micros-per-token` | long | `0` | 输出 token 单价 |
| `app.trajectory.payload-ref.enabled` | boolean | `false` | 超阈值载荷外置写入 |
| `app.trajectory.payload-ref.directory` | String | 系统 tmpdir | 外置载荷存储目录（生产须挂共享存储） |

## 8. 限制

| 限制 | 状态 | 替代 |
|------|------|------|
| MODEL_CALL 仅 OpenJiuwen | 已知限制 | AgentScope 可自行添加埋点 |
| A2A buffer 上限 10,000 事件 | 已知限制 | 超限追加 TRUNCATED 事件；考虑降低 sample-rate |
| payload-ref 多节点需共享存储 | 已知限制 | 挂载 NFS / S3-FUSE / CSI PVC |
| OTel Collector 不可达时 Span 丢失 | 已知限制 | OTel SDK 内置重试；同时开启 NDJSON 双轨兜底 |

## 9. 相关资源

- **生产最佳实践 Runbook**（收集 / 存储 / 查询配方）：[trajectory-production-runbook.md](trajectory-production-runbook.md)
- 设计文档：`architecture/docs/L2/agent-runtime/trajectory-observability-design.md`
- [配置属性参考](configuration-properties.md)
