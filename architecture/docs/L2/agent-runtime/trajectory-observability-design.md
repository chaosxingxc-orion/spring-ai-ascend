# 轨迹可观测性 — 设计文档

> 目标模块：`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/`（事件模型）、`engine/otel/`（OTel 导出）
> 最后更新：2026-06-14

---

## 1. 概述

### 1.1 特性定位

轨迹可观测性是 agent-runtime 框架中立的 Agent 执行记录系统——记录每次调用的完整执行过程（模型调用、工具调用、错误等），支持敏感信息掩码。

- **解决的问题**：Agent 执行是黑盒——多个 LLM 调用、工具调用、子 Agent 调用交织在一起，没有统一的可观测性视图。轨迹系统为每次 invocation 产出一个带时间戳、序列号、Span 嵌套树的完整事件流。
- **适用场景**：调试 Agent 行为、性能分析、合规审计、多 Agent 调用链路追踪。如果只需要最终结果不需要执行过程，可以关闭轨迹（`app.trajectory.enabled=false`）。

### 1.2 核心设计原则

1. **框架中立** — 事件模型不绑定任何 Agent 框架；各 Adapter 通过 `TrajectoryDraft` 工厂方法提交事件
2. **最小侵入** — Adapter 只需将原生回调映射为 `TrajectoryDraft`，runtime 负责 stamping、掩码、输出
3. **后端无关** — 通过 `TrajectorySink` 接口支持多后端消费，当前已实现 A2A 北向投递
4. **故障隔离** — Sink 失败不影响其他 Sink 和 Agent 执行

### 1.3 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| 事件模型 | 定义所有事件类型和 Span 模型 | `TrajectoryEvent`, `TrajectoryDraft` | ✅ |
| Stamping 引擎 | 半成品事件 → 完整事件（seq/span 树/时间戳/掩码） | `StampingTrajectoryEmitter` | ✅ |
| 敏感信息掩码 | 自动掩码敏感 key 的值 | `TrajectoryMasking` | ✅ |
| 北向投递 | 轨迹通过 A2A artifact 返回调用方 | `A2aNorthboundSink` | ⚠️ 代码已有，无 example |
| OpenTelemetry 导出 | 轨迹转为 OTel Span → OTLP | `OtelSpanSink` | ⚠️ 代码已有，无 example |
| Adapter 接入 | 各 Adapter 的轨迹事件产出 | Rail + Handler | ⚠️ 部分覆盖 |

---

## 2. 功能规格

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

### 2.3 接口契约

#### TrajectoryEmitter

```java
/** 推送侧接口。Adapter 调用 emit 提交半成品事件。 */
@FunctionalInterface
public interface TrajectoryEmitter {
    /** 提交一个轨迹草稿。线程安全。 */
    void emit(TrajectoryDraft draft);

    TrajectoryEmitter NOOP = draft -> {};  // 轨迹关闭时的空实现
}
```

#### TrajectorySink

```java
/** 终端消费接口。每 invocation 创建一个实例。 */
public interface TrajectorySink {
    void onOpen();                         // invocation 开始时调用
    void accept(TrajectoryEvent event);    // 每个事件调用一次（按 seq 顺序）
    void onClose();                        // invocation 结束时调用
}
```

#### 行为承诺

- **必须**：所有事件的 seq 严格单调递增
- **必须**：RUN_START 必须是第一个事件，RUN_END 必须是最后一个事件
- **禁止**：Adapter 不可在 doExecute 之外直接构造 TrajectoryEvent（只能通过 TrajectoryDraft）
- **允许**：Sink 实现可以是异步的（accept 返回后事件可能尚未持久化）

---

## 3. 模块结构

### 3.1 包结构

```
engine/spi/
├── TrajectoryEvent.java              # 事件模型（Kind 枚举, Span, Usage, ErrorInfo）
├── TrajectoryDraft.java              # 半成品事件工厂（modelCallStart/End, toolCallStart/End, error...）
├── TrajectoryEmitter.java            # @FunctionalInterface 推送侧
├── StampingTrajectoryEmitter.java    # 核心 stamping 引擎（seq/span 栈/掩码/时间戳）
├── TrajectorySink.java               # 消费接口（onOpen/accept/onClose）
├── CompositeTrajectorySink.java      # 多 Sink 扇出，故障隔离
├── TrajectorySinkFactory.java        # @FunctionalInterface 每 invocation 创建 Sink
├── TrajectorySource.java             # Handler 标记接口
├── TrajectoryMasking.java            # 敏感字段掩码
└── TrajectorySettings.java           # 每次调用设置（enabled/maskKeyPattern/truncateChars）

engine/a2a/
├── A2aTrajectorySupport.java         # 轨迹设置解析 + Sink 扇出构建
└── A2aNorthboundSink.java            # 北向轨迹投递

engine/otel/
├── OtelSpanSink.java                 # 轨迹事件 → OTel Span
└── OtelSpanSinkFactory.java          # 每 invocation 创建 OTel Sink
```

### 3.2 核心类静态关系

```
«interface»               «engine»                      «interface»
TrajectoryEmitter    ←── StampingTrajectoryEmitter  ──→ TrajectorySink
      ↑                         │                           ↑
      │                         ├─ 维护 span 栈              ├── CompositeTrajectorySink
      │                         ├─ 调用 TrajectoryMasking     ├── A2aNorthboundSink
      │                         └─ 分配 seq/timestamp         └── OtelSpanSink
```

---

## 4. 核心设计

### 4.1 事件管道

```
Adapter 发射半成品
  │
  ├─ TrajectoryDraft.modelCallStart(...)  ─┐
  ├─ TrajectoryDraft.toolCallStart(...)  ──┤  框架原生回调
  ├─ TrajectoryDraft.error(...)          ──┘
  │
  ▼
StampingTrajectoryEmitter.emit(draft)
  ├─ 分配单调 seq
  ├─ Span 栈维护：_START 事件 push stack，_END 事件 pop stack
  ├─ 生成 traceId / spanId / parentSpanId
  ├─ wall-clock 时间戳
  ├─ TrajectoryMasking: 遍历 Map key，匹配敏感正则 → 值替换为 ***
  └─ → TrajectoryEvent (完整事件)
  │
  ▼
CompositeTrajectorySink
  ├─ OtelSpanSink (如启用)
  ├─ A2aNorthboundSink (如启用)
  └─ [自定义 Sink]
```

### 4.2 Adapter 接入方式

#### OpenJiuwen

`OpenJiuwenTrajectoryRail` 注册为 OpenJiuwen `AgentRail`：

| 回调 | → TrajectoryDraft | Kind |
|------|-------------------|------|
| `beforeModelCall` | `modelCallStart()` | MODEL_CALL_START |
| `afterModelCall` | `modelCallEnd(usage, finishReason, reasoning)` | MODEL_CALL_END |
| `onModelException` | `error(null, code, message, retryAttempt, true)` | ERROR |
| `beforeToolCall` | `toolCallStart(toolName, args)` | TOOL_CALL_START |
| `afterToolCall` | `toolCallEnd(toolResult)` | TOOL_CALL_END |
| `onToolException` | `error(null, code, message, retryAttempt, true)` | ERROR |

所有回调包裹在 try-catch 中，Rail 失败不影响 Agent 执行。

#### AgentScope

`AbstractAgentScopeRuntimeHandler.doExecute()` 消费原生 `AgentScopeEvent` 流时，OUTPUT 事件映射为 PROGRESS，FAILED 事件映射为 ERROR。支持的 Kind：RUN_START/END、TOOL_CALL_START/END、ERROR、PROGRESS。

### 4.3 Adapter 覆盖矩阵

| Kind | OpenJiuwen | AgentScope | 说明 |
|------|-----------|-----------|------|
| RUN_START | ✅ | ✅ | AbstractAgentRuntimeHandler 自动发射 |
| RUN_END | ✅ | ✅ | 同上 |
| MODEL_CALL_START | ✅ | — | AgentScope 不暴露模型调用回调 |
| MODEL_CALL_END | ✅ | — | 含 Usage (tokens/latency/model/cost) |
| TOOL_CALL_START | ✅ | ✅ | 工具名称 + 参数 |
| TOOL_CALL_END | ✅ | ✅ | 工具返回结果 |
| ERROR | ✅ | ✅ | ErrorInfo (category/detail/retryable) |
| PROGRESS | — | ✅ | AgentScope 原生产出增量事件 |

---

## 5. 配置模型

### 5.1 完整配置示例

```yaml
app:
  trajectory:
    enabled: true
    mask:
      key-pattern: "(?i)(key|token|secret|password|api_key|credential)"
      truncate-chars: 0
    otel:
      enabled: false
      endpoint:
```

### 5.2 配置属性表

| 属性路径 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `app.trajectory.enabled` | boolean | `true` | 启用轨迹记录 |
| `app.trajectory.mask.key-pattern` | String | `(?i)(key\|token\|secret\|...)` | 敏感 key 正则 |
| `app.trajectory.mask.truncate-chars` | int | `0` | 截断阈值（0=不截断） |
| `app.trajectory.otel.enabled` | boolean | `false` | 启用 OTel 导出 |
| `app.trajectory.otel.endpoint` | String | — | OTLP 端点地址 |

---

## 6. 对外呈现 / 用户场景

### 6.1 外部接口

| API | 说明 |
|-----|------|
| `TrajectorySink` SPI | 实现自定义轨迹消费后端 |
| `TrajectoryDraft` 工厂方法 | Adapter 开发者通过工厂方法提交事件 |
| `app.trajectory.mask.*` | 运维者配置掩码规则 |

### 6.2 用户示例

#### 6.2.1 自定义掩码规则

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

#### 6.2.2 启用 OTel 导出

```yaml
app:
  trajectory:
    otel:
      enabled: true
      endpoint: http://otel-collector:4318/v1/traces
```

前置条件：OTel SDK 在 classpath。预期结果：轨迹事件自动转为 OTel Span，通过 OTLP 导出到 collector。

### 6.3 E2E 流程

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

## 7. 错误处理

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---------|---------|------|---------|
| Sink accept 异常 | 某 Sink 抛 RuntimeException | CompositeTrajectorySink 隔离，其他 Sink 继续 | 不影响 Agent 执行 |
| Rail 回调异常 | OpenJiuwen 回调抛异常 | try-catch 包裹，WARN 日志 | 该事件丢弃，Agent 继续 |
| Stamping 异常 | 非法 span 嵌套 | ERROR 日志 | 该事件跳过，后续事件继续 |
| OTel export 失败 | Collector 不可达 | WARN 日志 | 本地事件 buffer 继续（可能丢弃） |

**降级策略**：轨迹系统是观测增强，失败时 Agent 核心功能不受影响。Sink 异常隔离，Rail 异常不影响 Agent 执行。

---

## 8. 限制与待补

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| MODEL_CALL 仅 OpenJiuwen | AgentScope 无模型调用级别的观测 | AgentScope 自行在 Agent 层添加埋点 |
| MODEL_CALL_FIRST_TOKEN 无 Adapter 发射 | 无法观测 TTFT | — |
| REASONING 无独立事件 | 推理过程观测不完整 | reasoning 内容在 MODEL_CALL_END.Usage 中 |
| OTel 导出无 example 验证 | OTel 功能可靠性未充分测试 | 先使用北向投递替代 |
| 采样率/载荷外置/自定义脱敏未实现 | 生产级轨迹管理不完整 | — |
