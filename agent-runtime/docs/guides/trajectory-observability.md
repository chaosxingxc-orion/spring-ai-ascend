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
| MODEL_CALL_START / MODEL_CALL_END | 模型调用（tokens、延迟、模型名） | OpenJiuwen |
| TOOL_CALL_START / TOOL_CALL_END | 工具调用 | OpenJiuwen、AgentScope |
| ERROR | 执行错误（分类、详情、可重试） | 所有 |
| PROGRESS | 进度更新 | AgentScope |

MODEL_CALL_END 携带 `Usage` 记录：prompt/completion/total tokens、延迟、模型名。

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
| `app.trajectory.enabled` | boolean | `true` | 启用轨迹记录。关闭后不做事件收集和掩码 |
| `app.trajectory.mask.key-pattern` | String | 见上 | 敏感 key 正则，匹配到的值替换为 `***` |
| `app.trajectory.mask.truncate-chars` | int | `0` | 长字符串截断阈值（0=不截断） |
| `app.trajectory.otel.enabled` | boolean | `false` | 启用 OTel 导出 |
| `app.trajectory.otel.endpoint` | String | — | OTLP 端点地址 |

## 8. 限制

| 限制 | 影响 | 替代 |
|------|------|------|
| MODEL_CALL 仅 OpenJiuwen | AgentScope 无模型调用级观测 | AgentScope 自行添加埋点 |
| TTFT 未观测 | 无首 Token 延迟数据 | — |
| REASONING 无独立事件 | 推理内容嵌入 MODEL_CALL_END | — |
| OTel 无 example 验证 | OTel 导出可靠性未充分测试 | — |

## 9. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/trajectory-observability-design.md`
- [配置属性参考](configuration-properties.md)
