# Agent Runtime Task-Centric Control 实现说明

本文说明当前 Internal Event Queue（IEQ）与 Task-Centric Control（TCC）的本地实现、接口边界、用户流程和最小验证方式。

## 1. 本版实现范围

1. IEQ 提供通用薄队列抽象：`InternalEventQueue<T>`。
2. IEQ 当前本地实现为 `InMemoryInternalEventQueue<T>`，提供多生产者、单消费者的 Reactor `Flux<T>` 流式读取。
3. `QueueManager` 是队列资源初始化与分配对象，按 `queueId + payloadType` 创建和关闭队列；它不管理业务数据。
4. 各业务模块持有自己的队列注册表；TCC 使用 `TaskQueueRegistry` 维护 task 索引并通过公共 IEQ 完成异步交接。
5. TCC 主实现为 `TaskControlService`，负责创建 Task、选择当前 Task、状态流转、幂等键和 revision fencing。
6. `TaskControlClient` 是内部 API，不是 SPI。Access 侧只调用 `runTask(RunTaskCommand)`；runtime/engine 回写侧只调用 `mark*` 状态入口。
7. `EngineTaskControlAdapter` 把 engine 侧状态回调转换为 `TaskControlService.mark*`，避免 runtime/engine 直接读写 IEQ。

## 2. 代码结构

```text
agent-runtime/src/main/java/com/huawei/ascend/runtime/queue/
  InternalEventQueue.java
  InMemoryInternalEventQueue.java
  QueueManager.java

agent-runtime/src/main/java/com/huawei/ascend/runtime/taskcontrol/
  Task.java
  TaskState.java
  TaskFailureCode.java
  WaitingReason.java
  TaskQueueRegistry.java
  TaskControlService.java
  EngineTaskControlAdapter.java
  api/
    TaskControlClient.java
  config/
    TaskControlAutoConfiguration.java

agent-runtime/src/test/java/com/huawei/ascend/runtime/taskcontrol/test/
  TaskControlServiceWhiteboxTest.java
  TaskflowEngineBridgeWhiteboxTest.java

agent-runtime/src/test/java/com/huawei/ascend/runtime/queue/
  InternalEventQueueTest.java
  QueueManagerTest.java
```

## 3. IEQ 接口边界

`InternalEventQueue<T>` 只定义队列能力：

- `queueId()`
- `offer(T value)`：线程安全，多生产者可并发写入，写入后立即返回。
- `stream()`：返回唯一消费者使用的 `Flux<T>`；无数据时等待，有数据时向消费者发出。
- `size()`：仅用于 DFX 观测，不作为业务消费入口。
- `close()`

队列不关心内容物类型，也不理解 Task 状态。当前 TCC 会把 `Task` 放进队列，但 task 查询、当前任务选择和状态流转均由 `TaskQueueRegistry` / `TaskControlService` 维护，不由 IEQ 扫描队列完成。

`QueueManager` 只做资源初始化、查找和关闭：

- `getOrCreate(String queueId, Class<T> payloadType)`
- `find(String queueId)`
- `queueIds()`
- `close(String queueId)`

## 4. TCC 接口边界

`TaskControlClient` 是 TCC 对其他内部模块暴露的 API：

- `runTask(RunTaskCommand command)`
- `markRunning(MarkTaskCommand command)`
- `markWaiting(MarkTaskCommand command)`
- `markSucceeded(MarkTaskCommand command)`
- `markFailed(MarkTaskCommand command)`
- `markCancelled(MarkTaskCommand command)`

Access 侧只使用 `runTask`。`RUN`、`RESUME_INPUT`、`CANCEL` 通过 `TaskAction` 表达，避免把 TaskHandler 拆成多个方法。

Runtime/engine 侧不直接操作 IEQ，也不直接改 Task 字段；它通过 `EngineTaskControlAdapter` 进入 `mark*`，由 TCC 裁决状态转换是否合法。

## 5. AgentId 与 dispatch 边界

Access/L1 入口负责接收并校验外部传入的 `agentId`。至少要保证 `agentId` 非空；如果入口已经接入 agent registry、鉴权网关或 agent card 发现机制，`agentId` 是否存在、是否可被当前租户/用户调用，也应在入口侧完成判断。

TCC 的职责是信任入口契约，把 `tenantId`、`sessionId`、`taskId`、`agentId` 和输入封装为 `EngineExecutionScope` / `EngineInput`，然后调用 `EngineDispatchApi`。TCC 不依赖 agent registry，也不负责判断 `agentId` 是否真实存在。

Runtime/engine dispatch 仍保留防御性兜底：如果异常路径绕过入口校验，或者 dispatch 时发现目标 Agent 不可用，可以通过失败事件回到 `EngineTaskControlAdapter`，最终进入 `TaskControlService.markFailed`。但这只是兜底，不是正常路径。

## 6. Engine 接口分类

Engine 侧接口按方向分为三类：

1. `engine.api.EngineDispatchApi`：engine 提供的入站 API，TCC 调用它提交执行、恢复、取消请求。
2. `engine.spi.AgentHandler`：唯一 provider SPI，外部 agent provider 实现它。
3. `engine.port.*` / `engine.command.*`：engine 内部端口和内部队列，不属于 SPI。

因此，示例应提供 `AgentHandler` 的示例实现，而不是再新增示例接口。

## 7. 用户流程

1. Access 把 A2A / async 协议输入转换为统一 `AgentRequest`。此时 `sessionId` 可以为空，表示客户端没有传入会话标识。
2. `AccessSubmissionService` 先调用 `SessionManager.loadOrCreate(..., currentUserInput)`，由 Session 层查找或创建 Session，并记录本次用户输入；`currentUserInput` 只包含 `USER` 角色消息。
3. `AccessSubmissionService` 使用 resolved `sessionId` 重新构造 `AgentRequest`，再绑定 egress，并把 resolved request 交给 `TaskControlClient.runTask(RunTaskCommand)`。
4. TaskControl 只接受 resolved request；进入 `TaskControlService` 后，`sessionId` 必须非空。
5. `TaskControlService` 通过 `TaskQueueRegistry` 获取 session 对应的 task 索引和 IEQ；registry 内部通过 `QueueManager.getOrCreate("task:<tenantId>:<sessionId>", Task.class)` 初始化队列。
6. 如果 session 下没有可挂接 Task，TCC 创建 `Task`，初始状态为 `CREATED`，写入 task 索引并 `offer` 到 IEQ。
7. TCC 调用 `EngineDispatchApi.enqueueExecution`，把执行请求交给 engine/runtime。
8. Runtime/engine 通过 outbound port 回写 `markRunning`、`markWaiting`、`markSucceeded`、`markFailed` 或 `markCancelled`。
9. TCC 校验 revision 和状态转换规则后更新 Task。

## 7.1 AgentRequest 阶段语义

`AgentRequest` 是统一入参 DTO，不保证已经绑定 Session。它有两个阶段：

1. Access 阶段：`sessionId` 可以为空；`input` 表示本次用户输入。
2. TaskControl 阶段：`sessionId` 必须已经由 `AccessSubmissionService` 通过 `SessionManager` resolved。

因此，任何模块如果绕过 `AccessSubmissionService` 直接调用 `TaskControlClient`，必须自行保证传入的是 resolved `AgentRequest`。正常路径不允许未解析 Session 的请求直接进入 TaskControl。

## 8. 当前明确保留的风险

1. OOD / `NOT_CURRENT_TASK` 后是否立即由 TCC 创建新 Task，仍需后续策略确认。
2. 当前 in-memory IEQ 仅支持单消费者；需要多消费者时应新增明确的队列语义，而不是复用当前接口。
3. 现在只做本地内存队列，分布式后端的 at-least-once、幂等重放和 fencing 还需要独立设计。

## 9. 最小验证

推荐在 Linux/WSL 环境执行：

```bash
./mvnw -pl agent-runtime -am \
  -Dtest=InternalEventQueueTest,QueueManagerTest,TaskControlServiceWhiteboxTest,TaskflowEngineBridgeWhiteboxTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Pquality -B -ntp test
```

提交前再执行：

```bash
./mvnw -pl agent-runtime -am -Pquality -B -ntp verify
```

最近一次白盒验证应以本地命令输出为准；若重命名 IEQ 或迁移 engine port 包，需要先跑上述 targeted tests。

## 10. 日志与端到端观测

本版在 Access、TaskControl、Internal Event Queue、Engine command、Engine dispatch 和 A2A egress 之间补齐了阶段性 trace 日志。日志字段以 `trace stage=... durationMs=...` 为主，Engine 命令入口额外输出 `queueWaitMs`，用于判断事件入队后是否发生等待。

主要 trace 阶段如下：

| 模块 | 阶段 | 说明 |
|---|---|---|
| Access | `access-run` / `access-resume` / `access-cancel` | 外部输入进入 TaskControl 前后的总耗时 |
| TaskControl | `task-prepare` | 选择或创建 Task、处理幂等和本地状态准备 |
| TaskControl | `task-dispatch` | 调用 `EngineDispatchApi` 的耗时 |
| TaskControl | `task-submit` | 一次 TaskControl 提交的端到端耗时 |
| Internal Event Queue | `queue-offer` | 单条事件写入队列的 DEBUG 级耗时 |
| Engine command | `engine-command-received` | Engine 命令从入队到被接收的等待时间 |
| Engine command | `engine-command-execute-start` / `engine-command-execute-finish` | Engine 命令开始执行和执行完成耗时 |
| Engine dispatch | `engine-dispatch-publish` | dispatch 请求写入 engine 内部队列的耗时 |
| Engine handler | `engine-handler-finish` | AgentHandler 实际执行耗时 |
| A2A egress | `a2a-egress-deliver` | 结果推送到 A2A 出站队列的耗时 |

最近一次 targeted 验证覆盖：

1. `InternalEventQueueTest`：并发写入、关闭语义、压力 smoke 和队列时延 smoke。
2. `TaskControlServiceWhiteboxTest`：正常完成、WAITING 后恢复、FAILED、CANCELLED、revision fencing 和幂等。
3. `AgentServiceEndToEndIT`：A2A 正常完成、WAITING 后恢复、FAILED 结果、异常结果和 CANCEL。
4. `examples/agent-runtime-a2a-llm-e2e`：通过本地 Ollama `gemma4:e4b` 跑通真实 LLM agent-runtime A2A 端到端样例。

当前观测到的代表性耗时如下：

| 场景 | 代表阶段 | 代表耗时 | 结论 |
|---|---|---:|---|
| Queue 压力 smoke | 4000 条事件、8 个生产者 | 总耗时 66-80 ms | 队列本身不是瓶颈 |
| Queue 压力 smoke | 单事件平均等待 | 66-72 us | 处于微秒级 |
| Queue 压力 smoke | 单事件最大等待 | 1.4-1.7 ms | 低毫秒级峰值 |
| fake agent E2E | `queueWaitMs` | 0 ms | 本地队列无可见等待 |
| fake agent E2E | `engine-handler-finish` | 0-7 ms | fake handler 主要用于流程验证 |
| 本地 Ollama E2E | `queueWaitMs` | 0 ms | 慢点不在队列 |
| 本地 Ollama E2E | `engine-dispatch-publish` | 2 ms | dispatch 写入很快 |
| 本地 Ollama E2E | `engine-handler-finish` | 18204 ms | 主要耗时在 AgentHandler / LLM 推理 |
| 本地 Ollama E2E | `a2a-egress-deliver` | 10 ms | 出站投递不是瓶颈 |

因此，当前端到端慢主要来自本地 LLM 推理阶段，而不是 Internal Event Queue 或 TaskControl。后续若引入远程队列、远程 agent runtime 或注册中心，需要继续沿用这些 trace 阶段，避免把模型推理、网络等待和队列等待混在一起判断。
