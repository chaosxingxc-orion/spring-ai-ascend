# Agent Service Taskflow 实现说明

本文说明当前 Internal Event Queue（IEQ）与 Task-Centric Control（TCC）
的代码实现、用户流程、边界和验证方式。完整设计背景见：

- `architecture/docs/L1/agent-service/2026-05-30-agent-service-l3-l4-task-queue-control-architecture-proposal.cn.md`
- `architecture/docs/L1/agent-service/2026-05-30-agent-service-l3-l4-task-queue-control-implementation-spec.cn.md`

## 1. 本版实现了什么

本版从“接口冻结”推进到“本地可运行闭环”：

1. IEQ 提供一个薄队列抽象：`TaskQueue<T>`。
2. IEQ 当前本地实现为 `InMemoryTaskQueue<T>`，底层使用 JDK
   `LinkedBlockingQueue`。
3. `QueueFactory` 仍是静态工厂类，不是 SPI，也不是可被外部替换的
   provider 接口。
4. `QueueManager` 是弱管理对象：记录队列注册、按 `queueId` 查询、按
   `tenantId + sessionId` 查询、注销队列；它不强制所有调用方必须通过
   manager 创建队列。
5. TCC 的主实现是 `TaskControlService`，负责创建 Task、选择当前 Task、
   执行状态流转、处理幂等键和 revision fencing。
6. `TaskControlClient` 是内部 API，不是 SPI。Access 侧只调用
   `runTask(RunTaskCommand)`；runtime adapter 只调用 `mark*` 状态入口。
7. `EngineTaskControlAdapter` 把 engine 侧的任务状态回调转成
   `TaskControlService.mark*`，避免 runtime/engine 直接读写 IEQ。
8. Spring 自动配置通过 `TaskflowAutoConfiguration` 创建
   `QueueManager`、`TaskControlService` 和 `EngineTaskControlAdapter`。

## 2. 代码结构

```text
agent-service/src/main/java/com/huawei/ascend/service/taskflow/
  config/
    TaskflowAutoConfiguration.java

  queue/
    TaskQueue.java
    InMemoryTaskQueue.java
    QueueFactory.java
    QueueManager.java
    QueueRegistration.java

  control/
    Task.java
    TaskState.java
    TaskFailureCode.java
    WaitingReason.java
    TaskControlService.java
    EngineTaskControlAdapter.java
    api/
      TaskControlClient.java

agent-service/src/test/java/com/huawei/ascend/service/taskflow/test/
  TaskBeanWhiteboxTest.java
  InMemoryTaskQueueWhiteboxTest.java
  TaskControlClientApiWhiteboxTest.java
  QueueManagerWhiteboxTest.java
  TaskControlServiceWhiteboxTest.java
  TaskflowEngineBridgeWhiteboxTest.java
```

## 3. 关键接口和类

### 3.1 IEQ

`TaskQueue<T>` 只定义队列能力：

- `queueId()`
- `offer(T value)`
- `poll()`
- `peek()`
- `find(Predicate<? super T> matcher)`
- `snapshot()`
- `size()`

队列不关心队列内容物类型，也不理解 Task 状态。当前实现虽然会存放
`Task` 对象，但这是 TCC 的使用方式，不是 IEQ 的语义。

`QueueManager` 管理队列注册关系：

- `register(TaskQueue<T>, QueueRegistration)`
- `findByQueueId(String)`
- `findBySession(String tenantId, String sessionId)`
- `registration(String queueId)`
- `registrations()`
- `unregister(String queueId)`

`QueueRegistration` 记录队列归属、创建方和创建时间。Session 队列的默认
`queueId` 由 `QueueRegistration.sessionQueueId(tenantId, sessionId)` 生成。

### 3.2 TCC

`TaskControlClient` 是 TCC 对外暴露的内部 API：

- `runTask(RunTaskCommand command)`
- `markRunning(MarkTaskCommand command)`
- `markWaiting(MarkTaskCommand command)`
- `markSucceeded(MarkTaskCommand command)`
- `markFailed(MarkTaskCommand command)`
- `markCancelled(MarkTaskCommand command)`

Access 侧只应该使用 `runTask`。`RUN`、`RESUME_INPUT`、`CANCEL` 通过
`TaskAction` 表达，避免把 TaskHandler 拆成多个入口。

Runtime/engine 侧不直接操作 IEQ，也不直接改 Task 字段；它通过
`EngineTaskControlAdapter` 进入 `mark*`，由 TCC 裁决状态转换是否合法。

## 4. 用户流程如何串起来

### 4.1 系统启动

1. Spring 创建 `QueueManager`。
2. Spring 创建 `TaskControlService`，并注入 `QueueManager` 与
   `EngineDispatchApi` 的延迟提供者。
3. Spring 创建 `EngineTaskControlAdapter`，作为 engine 侧状态回写到 TCC 的
   adapter。
4. Engine 自动配置创建 `EngineDispatchApi`。TCC 通过该 API 把执行请求入到
   engine/runtime 侧。

### 4.2 首次用户输入

1. Access/Session 层拿到或创建 `sessionId`。
2. Access 把 `tenantId`、`sessionId`、`agentId`、用户输入组装成
   `RunTaskCommand(action=RUN)`。
3. `TaskControlService.runTask` 根据 `tenantId + sessionId` 从
   `QueueManager` 找 session 队列。
4. 如果 session 队列不存在，TCC 通过 `QueueFactory.inMemorySessionQueue`
   创建队列，并注册到 `QueueManager`。
5. 如果当前 session 没有可挂接 Task，TCC 创建一个 `Task`，初始状态为
   `CREATED`，并写入 session 队列。
6. TCC 构造 `EngineExecutionScope` 与 `EngineInput`，调用
   `EngineDispatchApi.enqueueExecution`。

### 4.3 Runtime 等待用户补充

1. Runtime/engine 侧产生等待用户输入的信号。
2. `EngineTaskControlAdapter` 把该信号转换成
   `TaskControlService.markWaiting`。
3. TCC 校验 `expectedRevision`，通过后把 Task 更新为 `WAITING`，并记录
   `WaitingReason.USER_INPUT`、detail 和 revision。
4. 面向用户的输出仍由 runtime/access 的同步返回路径处理；IEQ 不承担输出流。

### 4.4 用户补充输入

1. Access 再次调用 `runTask`，这次使用 `TaskAction.RESUME_INPUT`。
2. TCC 用 `tenantId + sessionId` 找到 session 队列。
3. 如果没有显式 `taskId`，TCC 选择最新的 `WAITING` Task。
4. TCC 调用 `EngineDispatchApi.enqueueResume`，把输入继续交给 runtime。
5. Runtime 继续通过 `EngineTaskControlAdapter` 回写 `markRunning`、
   `markSucceeded`、`markFailed` 或 `markCancelled`。

### 4.5 取消任务

1. Access 调用 `runTask`，使用 `TaskAction.CANCEL`，并携带 `taskId`。
2. TCC 把 Task 状态改为 `CANCELLING`。
3. TCC 调用 `EngineDispatchApi.enqueueCancel`。
4. Runtime 确认取消后，经 `EngineTaskControlAdapter` 调用
   `markCancelled`，TCC 将 Task 置为 `CANCELLED`。

## 5. 当前特性

| 特性 | 对应代码 | 说明 |
|---|---|---|
| 本地 IEQ | `TaskQueue`、`InMemoryTaskQueue` | 薄队列，只处理对象顺序和读取。 |
| 队列弱管理 | `QueueManager`、`QueueRegistration` | 记录队列归属和索引，不提供对外 admin port。 |
| Session 队列创建 | `QueueFactory.inMemorySessionQueue` | 创建时同步注册到 `QueueManager`。 |
| Task Bean | `Task` | Java Bean，包含状态、revision、detail、时间戳。 |
| 单入口任务 API | `TaskControlClient.runTask` | Access 侧统一从这里进入。 |
| 状态回写 API | `TaskControlClient.mark*` | Runtime adapter 回写状态意图。 |
| 当前 Task 选择 | `TaskControlService.findCurrentTask` | 按 `updatedAt` 再按 `taskId` 选择最新可挂接 Task。 |
| 幂等 | `RunTaskCommand.idempotencyKey` | 相同 key 返回已记录结果。 |
| fencing | `MarkTaskCommand.expectedRevision` | 防止旧 runtime 信号覆盖新状态。 |
| Engine 桥接 | `EngineTaskControlAdapter` | 对齐 engine 回调接口，转换等待原因和失败码。 |
| 白盒测试 | `taskflow/test/*WhiteboxTest` | 覆盖 Bean、队列、manager、TCC、engine bridge。 |

## 6. 设计边界

这些边界是当前实现需要保持的职责分离：

1. IEQ 不理解 Task 状态；队列只管理对象顺序、读取和快照。
2. Runtime/engine 不直接读写 IEQ，状态变化必须经 adapter 进入 TCC。
3. Access 不需要知道 `queueId`，只透传 `tenantId`、`sessionId`、
   `agentId` 和用户输入。
4. `QueueManager` 是弱管理对象，不作为对外 admin port 暴露。

## 7. 后续 TODO

1. 接入 Access / Session 的端到端流程：Session 创建时绑定 session 队列，
   Access 侧统一调用 `runTask`。
2. 明确 OOD / `NOT_CURRENT_TASK` 后是否立即由 TCC 创建新 Task 的策略，并补
   对应白盒测试。
3. 评估是否需要独立 Task 索引或持久化 TaskStore，避免未来持久化后端依赖
   全量队列扫描。
4. 增加 Redis/JDBC/Kafka 等持久化 IEQ 后端，同时保持 `TaskQueue` API
   不变。
5. 补充分布式后端的 at-least-once / exactly-once、幂等和 fencing 说明。

## 8. 最小验证

推荐先跑 taskflow 白盒测试：

```bash
./mvnw -pl agent-service -am \
  -Dtest=TaskBeanWhiteboxTest,InMemoryTaskQueueWhiteboxTest,TaskControlClientApiWhiteboxTest,QueueManagerWhiteboxTest,TaskControlServiceWhiteboxTest,TaskflowEngineBridgeWhiteboxTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Pquality -B -ntp test
```

提交前再跑 agent-service 质量验证：

```bash
./mvnw -pl agent-service -am -Pquality -B -ntp verify
```

当前白盒验证结果：

```text
Taskflow targeted white-box tests: passed
Tests run: 19, Failures: 0, Errors: 0
agent-service -Pquality verify: passed
```
