---
level: L1
view: process
module: agent-runtime
status: proposal
authority: "design proposal — to graduate to an ADR when the trajectory seam is implemented"
---

# A2A 异常流补齐 + 轨迹可观测设计提案

> 状态：proposal（设计提案，gate 排除目录）。本提案的 **Phase 0 急症修复已实现并 TDD 验证**；
> **双平面轨迹 seam（Plane A/B）为前瞻设计，本次不实现**，待实现时升格为正式 ADR。

## 1. 动机：从"黑盒答案"到"可观测、可进化"

智能体最大的价值是**可进化**，而进化的前提是**可观测**——把尽量丰富的执行轨迹
（推理、工具调用、LLM 调用、检索、重试、token/时延/成本）暴露给 Client，
而不仅是最终答案或一句失败原因。

当前 `A2aAgentExecutor` 把引擎产出的事件流**坍缩**为四态
（`OUTPUT / COMPLETED / FAILED / INTERRUPTED`，见
`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/AgentExecutionResult.java`），
把进化最需要的步内信号丢弃了。异常流只是这条轨迹里的一条**终态车道**。

## 2. 源码证据（结论靠源码，不靠猜）

- **A2A SDK 能力**（`org.a2aproject.sdk:a2a-java-sdk 1.0.0.CR1`，javap 实证）：
  - `AgentEmitter` 提供 `fail(Message)` / `reject(Message)`（→REJECTED）/ `requiresInput` /
    `requiresAuth`（→AUTH_REQUIRED）/ `sendMessage(Message)`（非终态）/ `emitEvent` /
    `newAgentMessage(parts, metadata)`。
  - **决定性字节码事实**：`AgentEmitter.fail(A2AError)` 只 `enqueueEvent(error)` 并锁
    `terminalStateReached`，**不构造 `TaskStatusUpdateEvent`、不转 `TASK_STATE_FAILED`**；
    只有 `fail(Message)` → `updateStatus(TASK_STATE_FAILED, msg)` 才真正流转状态。
    故**任务层终态失败必须 `fail(Message)`**，`A2AError` 归协议层。
  - `Message` 带 `metadata` map；`DataPart` 可载结构化 JSON——A2A 原生的机器可读载体。
  - `TaskState` 自带 `isFinal()` / `isInterrupted()`；协议层有 `A2AErrorResponse(id, A2AError)`
    + `A2AErrorCodes`（每码带 JSON-RPC code / httpCode / grpcStatus）+
    `JSONRPCUtils.toJsonRPCErrorResponse(id, error)`。
- **AgentScope-Java 对齐实情**：
  - 任务级错误是**纯文本**（`"Handle Agent execute error: " + t.getMessage()`，
    `AgentScopeAgentExecutor.java`），**无结构化错误码**；流式 `doOnError`→`taskUpdater.fail(msg)`→FAILED。
  - 其 A2A server `AgentExecuteProperties{completeWithMessage, requireInnerMessage}`——
    `requireInnerMessage=true` 时把 `EventType.TOOL_RESULT / HINT` 等**内部轨迹**也流给 client。
  - 轨迹建模为 `Event{sequence_number, object, status, error}`
    （`agentscope-runtime-java/.../engine/schemas/Event.java`），状态
    `CREATED→IN_PROGRESS→COMPLETED/FAILED/REJECTED/CANCELED`。
  - 可观测面用 **OpenTelemetry GenAI 语义约定**（`gen_ai.*`，`AgentScopeIncubatingAttributes`）。
- **LangGraph4j**：**无 A2A 面**；价值在图执行层——节点异常→`GraphRunnerException`→`Data.error()`；
  **中断是状态 `Data.done(InterruptionMetadata)` 不是异常**（我们 `INTERRUPTED→requiresInput()` 已对齐）。

> 诚实结论：结构化机器可读错误/轨迹 **超越** AgentScope（其任务级为纯文本）——是业界更优实践
> （对齐 OTel GenAI + AgentScope 的事件流形状），不是"对齐底线"。

## 3. 设计：轨迹 = 带类型的事件流，双平面暴露

### Plane A — 带内轨迹（in-band，给"调用方 Agent / Client"实时看）

引擎产出的每个轨迹事件 → 映射到 A2A 的**非终态**载体 `sendMessage(Message)`（保持 WORKING）：

- `Message` = `DataPart{ kind, object, seq, name, args/result, gen_ai.usage.*, latency, attempt, error? }`
  （机器可读）+ `TextPart{人读摘要}` + `Message.metadata{ trace.kind, gen_ai.*, traceId, spanId }`。
- 终态答案 → artifact + `complete(Message)`。
- **终态失败** → `fail(Message{DataPart 结构化错误})`（字节码证据：唯一能转 FAILED 的路径）。
- **步内可恢复错误/重试** → 非终态轨迹事件（`status=FAILED` 但非 terminal，带 `attempt`、`retryable`）——
  失败但被救回的工具调用全程可见，是评测/进化最值钱的数据。
- **详细度开关** `trajectory level` ∈ {OFF, SUMMARY, FULL}（对齐 AgentScope `requireInnerMessage`/
  `completeWithMessage`），经请求 metadata 传入，默认 SUMMARY + 敏感参数脱敏。
- **关联与可演化**：每事件带 `sequence_number` + `taskId/contextId` + `traceId/spanId` +
  `schema_version`（轨迹契约自身的进化钩子，老消费者不被破坏）。

### Plane B — 带外 OTel GenAI span（out-of-band，给运维/评测/进化流水线）

- 用 OpenTelemetry GenAI 约定（`gen_ai.system / request.model / usage.input_tokens / operation.name`、
  工具 span）发全保真 trace——进化数据湖的规范来源，AgentScope 已这么做。
- **两平面用同一 `traceId/spanId` + `gen_ai.*` 键**：Client 能把带内事件缝合到带外 trace，
  评测/RLAIF/微调流水线零翻译直接吃。

### 轨迹事件 schema（草案，对齐 AgentScope `Event`）

```
{ "kind": "error|tool_call|tool_result|reasoning|llm_call|progress",
  "object": "<细分类型>",
  "seq": <int>, "schema_version": "1",
  "code": "<stable code | adapter code>", "retryable": <bool>,   // 仅 error
  "name": "...", "result": {...},                                 // 工具/llm
  "gen_ai": { "usage": {...}, "request.model": "..." },           // OTel 对齐
  "trace": { "traceId": "...", "spanId": "..." } }
```

## 4. 异常流三车道（归位）

| 车道 | 载体 | 状态 | Phase 0 状态 |
|---|---|---|---|
| 协议级错误（解析/未知方法/校验） | `A2AErrorResponse(id, A2AError)` 替代 `{}` | 无 task，JSON-RPC error | **已实现** |
| 终态失败 | `fail(Message{ TextPart + DataPart })` | `TASK_STATE_FAILED` | **已实现** |
| 无 handler | `reject(Message)` | `TASK_STATE_REJECTED` | **已实现** |
| 步内可恢复错误/重试 | `sendMessage(Message{DataPart, retryable})` | 非终态，仍 WORKING | 前瞻（Plane A） |

## 5. 约束对账（诚实）

- **不做业务投影**：轨迹是 A2A 原生 `metadata/DataPart` 上的**附加遥测，只发不回读**；
  运行时仍是唯一真相源，A2A 只承载 best-effort 可观测 trace。这是它不沦为投影层的红线。
- **最大化 OSS 复用**：复用 AgentScope `Event{object,seq,status,error}` 形状 + OTel GenAI 约定 +
  A2A 原生 `DataPart/metadata/sendMessage`，零自造 schema、零 SPI 镜像。
- **最小爆破面（Rule D-2）**：Plane A 落地时仅需拓宽 `AgentExecutionResult`（加 `TRAJECTORY` 变体承载
  原始类型化事件）+ `A2aAgentExecutor.route()` 加一个分支 + 适配器停止坍缩上游事件；executor 改动很小。

## 6. Phase 0 已实现（本次落地，TDD 验证）

- 协议层 `A2aJsonRpcController`：`handle()`/`handleBlocking()` 把异常映射成 `A2AError` 子码
  （JSON_PARSE / INVALID_REQUEST / METHOD_NOT_FOUND / INTERNAL）→ `A2AErrorResponse`，**不再返回 `{}`**。
- 任务层 `A2aAgentExecutor`：失败 `Message` 带 `TextPart`（`code: detail`）+ `DataPart`
  `{kind,code,message,retryable,schema_version}` + `Message.metadata`；新增 `RuntimeErrorCode`
  薄分类器（INVALID_INPUT/TIMEOUT/UPSTREAM_UNAVAILABLE/CANCELLED/INTERNAL + retryable，
  走 cause 链，仅分类未捕获异常，adapter 码透传）；`handler==null → reject(Message)`；`cancel()` 加守护。
- 测试：`A2aJsonRpcControllerTest`（+2）、`A2aAgentExecutorTest`（+3，更新 1）、`RuntimeErrorCodeTest`（6）。

## 7. 后续 wave（前瞻，未实现）

1. 拓宽 `AgentExecutionResult` 加 `TRAJECTORY` 变体 + `A2aAgentExecutor.route()` 分支（Plane A，SUMMARY 级）。
2. 适配器（`AgentScopeStreamAdapter` / `OpenJiuwenStreamAdapter`）停止坍缩，透传 tool_call/tool_result/error-recovered。
3. Plane B OTel GenAI span + 一致 key + 详细度级别 + 脱敏。
4. 升格为正式 ADR（架构记录 + baseline/契约 lockstep）。
