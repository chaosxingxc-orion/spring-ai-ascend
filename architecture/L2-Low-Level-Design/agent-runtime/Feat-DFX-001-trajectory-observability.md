---
level: L2-LLD
module: agent-runtime
feature_type: dfx
feature_id: Feat-DFX-001
status: active
dependency:
  - ../../L1-High-Level-Design/agent-runtime/README.md
  - ../../L1-High-Level-Design/agent-runtime/development.md
  - ../../L1-High-Level-Design/agent-runtime/process.md
  - ../../../version-scope/DFX-001-trajectory-observability.md
---

# 轨迹可观测性 — 设计文档

> 目标模块：`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/`（事件模型）、`engine/otel/`（OTel 导出）
> 最后更新：2026-06-14

---

## 1. 概述

### 1.1 特性定位

轨迹可观测性是 agent-runtime 框架中立的 Agent 执行记录系统——记录每次调用的完整执行过程（模型调用、工具调用、错误等），支持敏感信息掩码。

- **解决的问题**：Agent 执行是黑盒——多个 LLM 调用、工具调用、子 Agent 调用交织在一起，没有统一的可观测性视图。轨迹系统为每次 invocation 产出一个带时间戳、序列号、Span 嵌套树的完整事件流。
- **适用场景**：调试 Agent 行为、性能分析、合规审计、多 Agent 调用链路追踪。如果只需要最终结果不需要执行过程，可以关闭轨迹（`app.trajectory.enabled=false`）。

### 1.2 当前事实边界

本文只描述 Feat-DFX-001 在当前 `agent-runtime` 模块中的已接受实现事实。面向调用方的黑盒行为、用户场景和外部示例已迁移到 `version-scope/DFX-001-trajectory-observability.md`；模块级 API/SPI、逻辑对象归属和部署资源模型以 L1 设计及其附录为准。

### 1.3 设计原则

1. **框架中立** — 事件模型不绑定任何 Agent 框架；各 Adapter 通过 `TrajectoryDraft` 工厂方法提交事件
2. **最小侵入** — Adapter 只需将原生回调映射为 `TrajectoryDraft`，runtime 负责 stamping、掩码、输出
3. **后端无关** — 通过 `TrajectorySink` 接口支持多后端消费，当前已实现 A2A 北向投递
4. **故障隔离** — Sink 失败不影响其他 Sink 和 Agent 执行

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| 事件模型 | 定义所有事件类型和 Span 模型 | `TrajectoryEvent`, `TrajectoryDraft` | ✅ |
| Stamping 引擎 | 半成品事件 → 完整事件（seq/span 树/时间戳/掩码） | `StampingTrajectoryEmitter` | ✅ |
| 敏感信息掩码 | 自动掩码敏感 key 的值 | `TrajectoryMasking` | ✅ |
| 北向投递 | 轨迹通过 A2A artifact 返回调用方 | `A2aNorthboundSink` | ⚠️ 代码已有，无 example |
| OpenTelemetry 导出 | 轨迹转为 OTel Span → OTLP | `OtelSpanSink` | ⚠️ 代码已有，无 example |
| Adapter 接入 | 各 Adapter 的轨迹事件产出 | Rail + Handler | ⚠️ 部分覆盖 |

---

## 2. 特性规格

### 2.1 DFX 目标清单

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

### 2.3 行为承诺

- **必须**：所有事件的 seq 严格单调递增
- **必须**：RUN_START 必须是第一个事件，RUN_END 必须是最后一个事件
- **禁止**：Adapter 不可在 doExecute 之外直接构造 TrajectoryEvent（只能通过 TrajectoryDraft）
- **允许**：Sink 实现可以是异步的（accept 返回后事件可能尚未持久化）

---

## 3. 核心实现

### 3.1 事件管道

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

### 3.2 Adapter 接入方式

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

### 3.3 Adapter 覆盖矩阵

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

## 4. 代码结构

### 4.1 包结构

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

### 4.2 核心类静态关系

```
«interface»               «engine»                      «interface»
TrajectoryEmitter    ←── StampingTrajectoryEmitter  ──→ TrajectorySink
      ↑                         │                           ↑
      │                         ├─ 维护 span 栈              ├── CompositeTrajectorySink
      │                         ├─ 调用 TrajectoryMasking     ├── A2aNorthboundSink
      │                         └─ 分配 seq/timestamp         └── OtelSpanSink
```

---

## 5. 运行流程

### 5.1 主流程

主流程由第 3 章各子特性的内部实现流程描述；本章只补充跨流程的错误、取消和降级语义，避免重复外部用户场景。

### 5.2 分支流程

分支流程按第 3 章中的状态流转、数据流或 adapter 分支处理。涉及外部调用方式的黑盒场景不在 L2 展开。

### 5.3 错误、取消、降级处理

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---------|---------|------|---------|
| Sink accept 异常 | 某 Sink 抛 RuntimeException | CompositeTrajectorySink 隔离，其他 Sink 继续 | 不影响 Agent 执行 |
| Rail 回调异常 | OpenJiuwen 回调抛异常 | try-catch 包裹，WARN 日志 | 该事件丢弃，Agent 继续 |
| Stamping 异常 | 非法 span 嵌套 | ERROR 日志 | 该事件跳过，后续事件继续 |
| OTel export 失败 | Collector 不可达 | WARN 日志 | 本地事件 buffer 继续（可能丢弃） |

**降级策略**：轨迹系统是观测增强，失败时 Agent 核心功能不受影响。Sink 异常隔离，Rail 异常不影响 Agent 执行。

---

## 6. 配置使用

### 6.1 完整配置示例

```yaml
app:
  trajectory:
    enabled: true
    mask:
      key-pattern: "(?i)(key|token|secret|password|api_key|credential)"
      truncate-chars: 256
    otel:
      enabled: false
      endpoint: http://localhost:4317
```

### 6.2 配置属性表

| 属性路径 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `app.trajectory.enabled` | boolean | `true` | 启用轨迹记录 |
| `app.trajectory.mask.key-pattern` | String | `(?i)(key\|token\|secret\|...)` | 敏感 key 正则 |
| `app.trajectory.mask.truncate-chars` | int | `256` | 字符串截断阈值 |
| `app.trajectory.otel.enabled` | boolean | `false` | 启用 OTel 导出 |
| `app.trajectory.otel.endpoint` | String | `http://localhost:4317` | OTLP 端点地址 |

---

## 7. 当前限制

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| MODEL_CALL 仅 OpenJiuwen | AgentScope 无模型调用级别的观测 | AgentScope 自行在 Agent 层添加埋点 |
| MODEL_CALL_FIRST_TOKEN 无 Adapter 发射 | 无法观测 TTFT | — |
| REASONING 无独立事件 | 推理过程观测不完整 | reasoning 内容在 MODEL_CALL_END.Usage 中 |
| OTel 导出无独立样例 | OTel 导出路径缺少独立运行样例 | 先使用北向投递替代 |
| 采样率/载荷外置/自定义脱敏未实现 | 生产级轨迹管理不完整 | — |
