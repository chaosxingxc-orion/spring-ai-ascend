---
scope: version
module: agent-runtime
feature_type: dfx
feature_id: Feat-DFX-001
status: active
dependency:
  - agent-runtime-release-features.cn.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-DFX-001-trajectory-observability.md
---

# 轨迹可观测性 — 黑盒行为说明

## 1. 特性定位

轨迹可观测性是 agent-runtime 框架中立的 Agent 执行记录系统——记录每次调用的完整执行过程（模型调用、工具调用、错误等），支持敏感信息掩码。

- **解决的问题**：Agent 执行是黑盒——多个 LLM 调用、工具调用、子 Agent 调用交织在一起，没有统一的可观测性视图。轨迹系统为每次 invocation 产出一个带时间戳、序列号、Span 嵌套树的完整事件流。
- **适用场景**：调试 Agent 行为、性能分析、合规审计、多 Agent 调用链路追踪。如果只需要最终结果不需要执行过程，可以关闭轨迹（`app.trajectory.enabled=false`）。

## 2. 对外能力边界

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| 事件模型 | ✅ | `TrajectoryEvent` Schema v3，Kind 枚举 8 种 |
| Span 模型 | ✅ | traceId / spanId / parentSpanId |
| Stamping 引擎 | ✅ | 单调 seq、span 栈嵌套、wall-clock 时间戳 |
| OpenJiuwen 轨迹 | ✅ | RUN/MODEL_CALL/TOOL_CALL/ERROR — 5 种 Kind |
| AgentScope 轨迹 | ✅ | RUN/TOOL_CALL/ERROR/PROGRESS — 4 种 Kind |
| 敏感信息掩码 | ✅ | key/token/secret/password 模式匹配替换 |
| 掩码规则可配置 | ✅ | `app.trajectory.mask.key-pattern` + `truncate-chars` |
| 多 Sink 扇出 | ✅ | `CompositeTrajectorySink`，故障隔离 |
| 父-子链路追踪 | ✅ | parentTaskId / parentTraceId 传递 |
| TTFT 观测 | ⬜ | `MODEL_CALL_FIRST_TOKEN` 枚举存在，无 Adapter 发射 |
| REASONING 记录 | ⬜ | reasoning 内容嵌入 MODEL_CALL_END，无独立事件 |
| 采样率控制 | ⬜ | 无代码 |
| 大载荷外置存储 | ⬜ | 无代码 |
| 自定义脱敏逻辑注入 | ⬜ | Redactor SPI 未定义 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| 业务级 Metrics | Trajectory 是事件级记录，不是聚合指标 | OTel Metrics / Prometheus |
| 轨迹持久化存储 | 属于存储层职责 | 通过 Sink 接口对接外部存储 |

## 3. 外部行为与用户场景

### 3.1 外部接口

| API | 说明 |
|-----|------|
| `TrajectorySink` SPI | 实现自定义轨迹消费后端 |
| `TrajectoryDraft` 工厂方法 | Adapter 开发者通过工厂方法提交事件 |
| `app.trajectory.mask.*` | 运维者配置掩码规则 |

### 3.2 用户示例

#### 3.2.1 自定义掩码规则

```yaml
# 前置条件：runtime 已启动
app:
  trajectory:
    enabled: true
    mask:
      key-pattern: "(?i)(key|token|secret|password|api_key|credential|phone|email)"
      truncate-chars: 200
```

预期结果：轨迹事件中 key 匹配 `phone` 或 `email` 的字段被掩码，超过 200 字符的字符串被截断。

#### 3.2.2 启用 OTel 导出

```yaml
app:
  trajectory:
    otel:
      enabled: true
      endpoint: http://otel-collector:4318/v1/traces
```

前置条件：OTel SDK 在 classpath。预期结果：轨迹事件自动转为 OTel Span，通过 OTLP 导出到 collector。

### 3.3 E2E 流程

```
用户请求 → Agent 执行
  │
  ├─ OpenJiuwenTrajectoryRail 捕获回调
  │     ├─ MODEL_CALL_START/END (token 用量、延迟、模型名)
  │     ├─ TOOL_CALL_START/END (工具名、参数、结果)
  │     └─ ERROR (错误码、重试次数)
  │
  ├─ StampingTrajectoryEmitter: stamping + 掩码
  │
  └─ Sink 输出:
       ├─ A2aNorthboundSink → 调用方 SSE artifact stream (如启用)
       └─ OtelSpanSink → OTLP exporter (如启用)
```

---
