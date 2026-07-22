---
level: L1-HLD
TAG:
  - process-view
  - runtime-behavior
  - concurrency
  - architecture-fact
  - edge-plane
status: draft
dependency:
  - README.md
  - overview.md
  - logical.md
  - scenarios.md
  - development.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
  - ../../L0-Top-Level-Design/views.md
  - ../../../docs/contracts/ingress-envelope.v1.yaml
  - ../../L2-Low-Level-Design/agent-client/Feat-Func-006-standard-agent-client-invocation.md
  - ../../L2-Low-Level-Design/agent-client/Feat-Func-007-local-tool-registration-and-execution.md
---

# agent-client L1 架构进程视图

## 1. 进程视图定位

进程视图描述 `agent-client` 在**运行时**的行为：请求/响应时序、服务流消费循环、本地
能力多轮循环、并发与线程模型、背压、超时与重试、幂等去重、取消与恢复、以及资源
生命周期（close/drain）。它回答"这个 SDK 跑起来时，线程和消息是怎么流动的、在哪里
可能阻塞、在哪里保证不重复副作用"。

本视图与 `logical.md`（状态归属）和 `scenarios.md`（技术场景）配合：逻辑视图定义"是什么"，
场景视图定义"要走通哪些路径"，进程视图定义"运行时如何编排与保证不变量"。具体 wire
时序见 L2 `Feat-Func-006` §3.5 与 `Feat-Func-007` §3.5/§5（对齐 runtime `Feat-Func-009`）。

## 2. 并发与线程模型

### 2.1 异步优先原则

- 所有网络与本地能力调用默认异步；公共 API 不隐式 `Thread.sleep` 或无限阻塞。
- 并发原语只用 `CompletionStage`（结果）与 `Flow.Publisher`（流），不在公共签名暴露
  具体线程池或 Reactor 类型。
- 若提供阻塞式便捷 API，须置于独立可选适配层并要求显式 `Duration`。

### 2.2 线程角色

| 线程角色 | 职责 | 约束 |
|---|---|---|
| 调用方线程 | 调用 SDK 公共 API，拿到 `CompletionStage` | 不被 SDK 长时间占用 |
| transport I/O 线程 | HTTP/SSE 连接读写 | 由 adapter 管理，不泄漏到公共层 |
| 事件分发线程 | 把 transport 事件归一为领域事件并推给订阅者 | 尊重订阅者 demand |
| 本地能力执行线程池 | 执行 Observation/Action | 有界；建议虚拟线程 per-task；受 deadline 约束 |
| 结果回传线程 | 组织下一轮请求并等 ACK | 与执行分离，支持重投 |

本地能力执行必须使用**有界**执行器；不得无界建线程，不得用 `Thread.sleep` 等待协议
状态（对齐仓库 no-thread-sleep 纪律）。

## 3. C2S 请求/响应进程

### 3.1 创建调用

```text
调用方 → sdk-facade.invoke(request)
  1. 生成并持久化幂等键/messageId（先落 state store，再发网络）
  2. 生成 clientInvocationId，创建本地 ClientInvocation（状态 NEW → SUBMITTING）
  3. L3 transport 构造线协议请求，发往 gateway 公开入口
  4a. 收到首帧 Task 快照 → 记录 serverTaskRef，状态 → ACCEPTED，返回 handle
  4b. 拒绝 → 状态 → REJECTED（终态，无 taskId）
  4c. 超时/断连且未拿到 taskId → 状态 → UNKNOWN
```

### 3.2 UNKNOWN 恢复

```text
UNKNOWN 阶段（尚无 serverTaskRef）：
  - 用同一 messageId / 幂等键重发原始创建请求
  - gateway 在去重窗口内返回同一 Task（replayed）或同一拒绝结论
  - 拿到 serverTaskRef 后 → ACCEPTED；此后任何超时都不得再回退为 UNKNOWN
  - 调用方放弃 → ABANDONED
```

不变量：拿到 `taskId` 后一切操作以 `taskId` 为准，`clientInvocationId`/`messageId`
只用于日志关联，不得再用于查询服务端 Task。

## 4. 服务流消费循环

### 4.1 订阅与背压

```text
stream-and-turn-loop 打开服务流（SSE 或等价）
  → 事件到达 → 按 cursor 顺序归一为领域事件
  → 推给订阅者（Flow.Publisher，尊重 demand，有界缓冲）
  → 订阅者处理成功 → 推进本地 cursor（先处理成功，再进 cursor）
```

不变量：
- 有界缓冲达到上限时不得无界增长：按策略 fail subscriber / drop 可丢事件 / 暂停读取，
  并暴露 overflow 诊断。
- cursor 只表示"client 消费到哪"，不表示"Task 执行到哪"。
- 慢消费或断线不得反向阻塞服务端控制面（`scenarios.md` TS-02）。

### 4.2 断线与重连

```text
连接断开
  → 状态：CONNECTING → RETRY_WAIT（指数退避+抖动）→ CONNECTING
  → 重连携带 cursor（Last-Event-ID）
  → gateway 支持 replay：从 cursor 后补发
  → 不支持 replay：以当前 Task 快照开流，用 cursor/tool_call_id 去重续读
  → 预算耗尽 → FAILED_FATAL
```

关键不变量（对齐协议提案 §6.2）：**流 EOF 或 `final:true` ≠ Task 终态**。
`final:true` + 非终态（input-required/auth-required）= 挂起等推进；EOF 未见终态 =
异常断连，必须查询补偿，绝不能判为完成。stream 状态与 Task 状态正交；订阅
cancel 只释放本地流，不取消 Task。

## 5. 本地能力多轮循环（核心闭环）

### 5.1 一轮工具调用的进程

```text
服务流/查询暴露 CapabilityIntent（含 tool_call_id）
  → DETECTED：按 tool_call_id 去重（重复意图不重复执行，返回已存结果或当前句柄）
  → POLICY_CHECKING：schema 校验 → PolicyGuard → （Action）ApprovalProvider
  → 幂等 claim（原子；重复请求复用旧结果）
  → EXECUTING：有界执行器内运行 LocalTool，受 deadline 约束
  → RESULT_READY：生成 CapabilityResult（outcome=OK/ERROR/REJECTED/TIMEOUT）
  → 先写结果 outbox（state store），再允许提交
  → SENDING：组织下一轮 C2S 请求回传结果（带 taskId + tool_call_id + attempt）
  → 收到成功响应（= 交付 ACK）→ ACKED（终态）
  → 回传失败 → RETRY_WAIT → SENDING；预算耗尽 → DELIVERY_FAILED（人工恢复）
```

### 5.2 两个必须分离的事实

```text
事实 A：CapabilityResult 已在本地生成（执行成功）
事实 B：CapabilityResult 已被服务端 Task owner 接收（ACK）
```

网络故障发生在 A 与 B 之间时，SDK **只能用同一 tool_call_id 重投同一逻辑结果，
绝不能重新执行工具**（尤其 Action，重执行 = 重复副作用）。这是整个多轮模型最重要的
不变量，直接决定去重键与 outbox 的设计。

### 5.3 多轮串联

一轮 ACKED 后 Task 可能再次进入等待并暴露下一个 CapabilityIntent，重复 §5.1。为避免
同一 invocation 上并发 resume，事件订阅者应每次只消费一条事件（request(1)），处理并
回传完成后再请求下一条。

## 6. 取消与恢复进程

### 6.1 取消

```text
调用方 cancel → 构造受治理取消请求 → 状态 CANCEL_REQUESTED
  → 服务端可能返回 canceled，也可能仍 working（协作取消进行中）
  → terminal 竞态时服务端终态优先
```

不变量：取消是请求不是命令；已终态 Task 的重复取消返回当前终态而非报错；关闭 SSE
永不隐式取消 Task；取消可停止未开始的本地工具，并协作取消运行中工具（不安全强杀
线程被禁止）。

### 6.2 进程/连接恢复

```text
恢复顺序（对齐 getting-started §5）：
  1. 从 state store 读 invocation、tenant、serverTaskRef、投影
  2. 检查结果 outbox，先重投尚未 ACK 的结果（同 tool_call_id）
  3. 读最后已处理 cursor
  4. 查询服务端 Task 快照
  5. Task 非终态则用 cursor 重订阅
  6. 服务端不支持 replay 时从快照续读，用 cursor/tool_call_id 去重
```

MVP 以 in-memory state store 为默认，不承诺跨进程重启恢复；需要跨重启/跨设备恢复时
换持久化 `ClientStateStore`（见 `physical.md`）。

## 7. 超时、重试与幂等

### 7.1 超时分层

| 超时 | 作用范围 |
|---|---|
| connect timeout | 建立连接 |
| accept timeout | 等待创建被接受（首帧 Task） |
| stream idle timeout | 服务流空闲（配合心跳监督） |
| tool deadline | 单次本地能力执行 |
| overall deadline | 一次调用总体截止 |

### 7.2 重试纪律

- 只对声明为可重试（`retryable=true`）且幂等安全的操作重试。
- 重试复用原幂等键/messageId，不新生成。
- 非幂等动作不盲目重试；有副作用工具的 idempotency policy 由业务声明。
- 退避使用指数+抖动；受 retry budget 与 `Retry-After` 约束。

### 7.3 去重键正交性

`messageId`（请求去重）、`clientInvocationId`（本地关联）、`serverTaskId`（Task 权威）、
`tool_call_id`（工具执行去重）、`attempt`（结果重投序号）各自独立，不得一个字段承担
多种语义（见协议提案 §2.3、设计提案附录 A.6）。

## 8. 资源生命周期与关闭

```text
close(AgentClient / InvocationCall)
  → 停止接收新事件
  → 有界 drain 进行中的本地能力执行与回传（尊重 deadline）
  → 释放订阅、连接、线程池
  → 记录仍 pending 的结果 outbox（不隐式取消服务端 Task）
```

不变量：close 不泄漏连接/线程/订阅；close 不等于 CancelTask；无声降级与无限重试被禁止。

## 9. 关键运行时不变量汇总

- 服务端 Task 是唯一权威；client 只保存投影，不写服务端状态。
- 拿到 `taskId` 后不再用 `clientInvocationId` 查询/恢复服务端 Task。
- `final:true` / EOF 不等于 Task 终态。
- 工具"执行成功"与"结果被接收"是两个事实；两者之间只重投不重执行。
- 有界缓冲、有界执行器、有界 drain；无 `Thread.sleep` 等待协议状态。
- 所有重试有稳定幂等/去重键。

## 10. 与其他视图的衔接

- 状态机与状态归属：`logical.md` §4。
- 技术场景路径：`scenarios.md` TS-01 ～ TS-06。
- 代码分层与依赖红线：`development.md`。
- 部署形态与网络/持久化边界：`physical.md`。
- wire 时序、幂等/冲突规则、事件语义：协议提案 §4–§8。
