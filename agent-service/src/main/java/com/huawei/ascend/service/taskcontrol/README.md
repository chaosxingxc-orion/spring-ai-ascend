# Agent Service Task-Centric Control 实现说明

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
agent-service/src/main/java/com/huawei/ascend/service/queue/
  InternalEventQueue.java
  InMemoryInternalEventQueue.java
  QueueManager.java

agent-service/src/main/java/com/huawei/ascend/service/taskcontrol/
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

agent-service/src/test/java/com/huawei/ascend/service/taskcontrol/test/
  TaskControlServiceWhiteboxTest.java
  TaskflowEngineBridgeWhiteboxTest.java

agent-service/src/test/java/com/huawei/ascend/service/queue/
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
3. `engine.port.*` / `engine.queue.*`：engine 内部端口和内部队列，不属于 SPI。

因此，示例应提供 `AgentHandler` 的示例实现，而不是再新增示例接口。

## 7. 用户流程

1. Access/Session 层拿到或创建 `sessionId`。
2. Access 把 `tenantId`、`sessionId`、`agentId` 和用户输入组装为 `RunTaskCommand(action=RUN)`。
3. `TaskControlService` 通过 `TaskQueueRegistry` 获取 session 对应的 task 索引和 IEQ；registry 内部通过 `QueueManager.getOrCreate("task:<tenantId>:<sessionId>", Task.class)` 初始化队列。
4. 如果 session 下没有可挂接 Task，TCC 创建 `Task`，初始状态为 `CREATED`，写入 task 索引并 `offer` 到 IEQ。
5. TCC 调用 `EngineDispatchApi.enqueueExecution`，把执行请求交给 engine/runtime。
6. Runtime/engine 通过 outbound port 回写 `markRunning`、`markWaiting`、`markSucceeded`、`markFailed` 或 `markCancelled`。
7. TCC 校验 revision 和状态转换规则后更新 Task。

## 8. 当前明确保留的风险

1. OOD / `NOT_CURRENT_TASK` 后是否立即由 TCC 创建新 Task，仍需后续策略确认。
2. 当前 in-memory IEQ 仅支持单消费者；需要多消费者时应新增明确的队列语义，而不是复用当前接口。
3. 现在只做本地内存队列，分布式后端的 at-least-once、幂等重放和 fencing 还需要独立设计。

## 9. 最小验证

推荐在 Linux/WSL 环境执行：

```bash
./mvnw -pl agent-service -am \
  -Dtest=InternalEventQueueTest,QueueManagerTest,TaskControlServiceWhiteboxTest,TaskflowEngineBridgeWhiteboxTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Pquality -B -ntp test
```

提交前再执行：

```bash
./mvnw -pl agent-service -am -Pquality -B -ntp verify
```

最近一次白盒验证应以本地命令输出为准；若重命名 IEQ 或迁移 engine port 包，需要先跑上述 targeted tests。
