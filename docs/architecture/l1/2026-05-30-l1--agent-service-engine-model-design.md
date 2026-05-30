# Agent Service Engine 模块设计终稿

> 版本：v1.1 终稿  
> 日期：2026-05-30  
> 服务：`agent-service`  
> 模块名：`engine`  
> Java 包根路径：`agent-service/src/main/java/com/huawei/ascend/service/engine/`  
> Java 包名：`com.huawei.ascend.service.engine`

## 1. 模块定位

`agent-service` 内部服务分层：

```text
agent-service
  1. access-layer
  2. session-task-manager
  3. internal-event-queue
  4. task-centric-control
  5. engine
```

`engine` 模块负责 Agent 执行调度和具体 Agent 框架适配：

```text
task-centric-control
  -> engine.spi.EngineDispatchSpi
  -> internal-event-queue
  -> engine.queue.EngineCommandSubscriber
  -> engine.dispatch.EngineDispatcher
  -> engine.handler.AgentHandler
  -> engine.adapter.openjiuwen.OpenJiuwenAgentHandler
  -> openJiuwen/agent-core-java Agent
  -> task-centric-control / access-layer
```

模块职责：

1. 对外提供异步执行入队接口；
2. 将 task-centric-control 提供的最小执行请求写入 `internal-event-queue`；
3. 订阅本模块的执行 command event；
4. 按 `agentId` 找到已注册的 Agent handler；
5. 调用 handler 执行目标 Agent；
6. 将用户可见输出发送给 `access-layer`；
7. 将开始、完成、失败、中断、取消等状态回写给 `task-centric-control`；
8. 支持 Agent 调 Agent 的 inline 调用和 child task 调用。

## 2. 命名与目录约束

原设计名统一收敛为：

```text
engine
```

目录约束：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/
```

包名约束：

```text
com.huawei.ascend.service.engine
```

原 `agent-service/src/main/java/com/huawei/ascend/service/engine/` 下既有文件按本终稿废弃并删除。

外部接口目录遵从原项目风格使用：

```text
spi/
```

不使用 `api/` 目录。

## 3. 总体目录结构

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/
  spi/
    EngineDispatchSpi.java
    EnqueueEngineExecutionRequest.java
    EnqueueEngineResumeRequest.java
    EnqueueEngineCancelRequest.java
  model/
    EngineExecutionScope.java
    EngineInput.java
    EngineMessage.java
    EngineOutput.java
    AgentCallMode.java
    InterruptType.java
  event/
    EngineCommandEvent.java
    EngineExecutionEvent.java
    EngineStartedEvent.java
    EngineOutputEvent.java
    EngineAgentCallEvent.java
    EngineInterruptedEvent.java
    EngineCompletedEvent.java
    EngineFailedEvent.java
    EngineCancelledEvent.java
  queue/
    EngineQueueGateway.java
    EngineCommandEventFactory.java
    EngineCommandSubscriber.java
  dispatch/
    EngineDispatcher.java
    AgentHandlerRegistry.java
  handler/
    AgentHandler.java
    AgentExecutionContext.java
  adapter/openjiuwen/
    OpenJiuwenAgentHandler.java
    OpenJiuwenAgentFactory.java
    OpenJiuwenMessageConverter.java
  service/
    TaskControlClient.java
    AccessLayerClient.java
  config/
    EngineAutoConfiguration.java
    EngineProperties.java
```

## 4. SPI 定义

### 4.1 EngineDispatchSpi

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/spi/EngineDispatchSpi.java
```

```java
package com.huawei.ascend.service.engine.spi;

public interface EngineDispatchSpi {

    EnqueueEngineStatus enqueueExecution(EnqueueEngineExecutionRequest request);

    EnqueueEngineStatus enqueueResume(EnqueueEngineResumeRequest request);

    EnqueueEngineStatus enqueueCancel(EnqueueEngineCancelRequest request);
}
```

功能注释：

```text
task-centric-control 调用 engine 的唯一外部入口。
只负责异步入队。
不直接执行 Agent。
不直接返回真实执行状态。
真实执行状态通过 TaskControlClient 回写。
```

### 4.2 EnqueueEngineExecutionRequest

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/spi/EnqueueEngineExecutionRequest.java
```

```java
package com.huawei.ascend.service.engine.spi;

import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;

public final class EnqueueEngineExecutionRequest {
    private EngineExecutionScope scope;
    private EngineInput input;
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `scope` | task-centric-control 能提供的 task/session/agent 上下文 |
| `input` | 用户输入或系统任务输入 |

执行闭环：

```text
task-centric-control 在 scope 中传入 agentId
  -> EngineDispatchSpi.enqueueExecution
  -> EngineCommandEvent(scope.agentId)
  -> EngineCommandSubscriber
  -> EngineDispatcher
  -> AgentHandlerRegistry.findByAgentId(scope.agentId)
  -> AgentHandler.execute(context)
```

约束：

```text
外部调用者只在 scope 中携带 agentId
外部调用者不传 handler 名称
外部调用者不传底层执行框架类型
外部调用者不传 openJiuwen 内部执行模式
scope.agentId 必须与 AgentHandlerRegistry 注册值匹配
找不到 agentId 时，EngineDispatcher 生成 EngineFailedEvent 并回写 task-centric-control
```

### 4.3 EnqueueEngineResumeRequest

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/spi/EnqueueEngineResumeRequest.java
```

```java
package com.huawei.ascend.service.engine.spi;

import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;

public final class EnqueueEngineResumeRequest {
    private EngineExecutionScope scope;
    private EngineInput input;
}
```

功能注释：

```text
用于恢复已中断的 Agent 执行。
scope 定位原 task/session/agent。
input 表示人工输入、审批结果或 child agent 返回结果。
```

### 4.4 EnqueueEngineCancelRequest

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/spi/EnqueueEngineCancelRequest.java
```

```java
package com.huawei.ascend.service.engine.spi;

import com.huawei.ascend.service.engine.model.EngineExecutionScope;

public final class EnqueueEngineCancelRequest {
    private EngineExecutionScope scope;
}
```

功能注释：

```text
用于取消指定 scope 的执行。
只负责发送取消 command。
取消结果由 TaskControlClient 回写。
```

### 4.5 EnqueueEngineStatus

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/spi/EnqueueEngineStatus.java
```

```java
package com.huawei.ascend.service.engine.spi;

public enum EnqueueEngineStatus {
    SUCCESS,
    FAILED
}
```

字段说明：

| 枚举值 | 说明 |
|---|---|
| `SUCCESS` | engine 已接收入队请求 |
| `FAILED` | engine 未接收入队请求 |

## 5. Model 定义

### 5.1 EngineExecutionScope

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/model/EngineExecutionScope.java
```

```java
package com.huawei.ascend.service.engine.model;

public final class EngineExecutionScope {
    private String tenantId;
    private String userId;
    private String sessionId;
    private String taskId;
    private String agentId;
}
```

功能注释：

```text
表示一次 Agent 执行所需的最小业务上下文。
agentId 归入 scope，由 task-centric-control 按 task 维度携带。
第一版不设计 run id，异步执行定位直接复用 taskId。
后续如果 session-task-manager 提供统一上下文类型，本类替换为 session-task-manager 的真实类型。
```

### 5.2 EngineInput

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/model/EngineInput.java
```

```java
package com.huawei.ascend.service.engine.model;

import java.util.List;
import java.util.Map;

public final class EngineInput {
    private String inputType;
    private List<EngineMessage> messages;
    private Map<String, Object> variables;
}
```

字段取值：

```text
inputType: USER_MESSAGE / RESUME_SIGNAL / AGENT_CALL_RESULT
```

功能注释：

```text
承载 Agent 执行输入。
优先使用 messages 表示对话输入。
variables 用于工作流变量或结构化任务参数。
```

### 5.3 EngineMessage

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/model/EngineMessage.java
```

```java
package com.huawei.ascend.service.engine.model;

public final class EngineMessage {
    private String role;
    private String content;
}
```

字段取值：

```text
role: system / user / assistant / tool
```

功能注释：

```text
第一版只保留文本消息。
content 直接适配 openJiuwen/agent-core-java 常见文本输入。
不设计复杂消息块。
不设计多模态消息。
```

### 5.4 EngineOutput

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/model/EngineOutput.java
```

```java
package com.huawei.ascend.service.engine.model;

public final class EngineOutput {
    private String content;
    private boolean finalOutput;
}
```

功能注释：

```text
表示 handler 输出给 access-layer 的最小用户可见内容。
content 为文本内容。
finalOutput 表示是否为最终输出。
```

### 5.5 AgentCallMode

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/model/AgentCallMode.java
```

```java
package com.huawei.ascend.service.engine.model;

public enum AgentCallMode {
    INLINE,
    CHILD_TASK
}
```

### 5.6 InterruptType

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/model/InterruptType.java
```

```java
package com.huawei.ascend.service.engine.model;

public enum InterruptType {
    HUMAN_INPUT,
    APPROVAL,
    WAITING_CHILD_AGENT
}
```

## 6. Event 定义

### 6.1 EngineCommandEvent

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/event/EngineCommandEvent.java
```

```java
package com.huawei.ascend.service.engine.event;

import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;
import java.time.Instant;

public final class EngineCommandEvent {
        private String commandType;
    private EngineExecutionScope scope;
    private EngineInput input;
    private Instant createdAt;
}
```

`commandType` 取值：

```text
EXECUTE
RESUME
CANCEL
```

功能注释：

```text
EngineCommandEvent 是 task-centric-control 能提供信息的直接封装。
不得包含 handler 名称、底层执行框架类型、openJiuwen 内部执行模式、service 引用等 task-centric-control 不感知的信息。
EXECUTE command 必须携带 scope、input。
RESUME command 必须携带 scope、input。
CANCEL command 必须携带 scope。
```

### 6.2 EngineExecutionEvent

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/event/EngineExecutionEvent.java
```

```java
package com.huawei.ascend.service.engine.event;

import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import java.time.Instant;

public abstract class EngineExecutionEvent {
    private String eventId;
    private EngineExecutionScope scope;
    private Instant occurredAt;
}
```

功能注释：

```text
Engine 执行事件基类。
只保留事件 id、scope、发生时间。
具体含义由子类表达。
```

### 6.3 EngineStartedEvent

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/event/EngineStartedEvent.java
```

```java
package com.huawei.ascend.service.engine.event;

public final class EngineStartedEvent extends EngineExecutionEvent {
}
```

### 6.4 EngineOutputEvent

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/event/EngineOutputEvent.java
```

```java
package com.huawei.ascend.service.engine.event;

import com.huawei.ascend.service.engine.model.EngineOutput;

public final class EngineOutputEvent extends EngineExecutionEvent {
    private EngineOutput output;
}
```

### 6.5 EngineAgentCallEvent

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/event/EngineAgentCallEvent.java
```

```java
package com.huawei.ascend.service.engine.event;

import com.huawei.ascend.service.engine.model.AgentCallMode;
import com.huawei.ascend.service.engine.model.EngineInput;

public final class EngineAgentCallEvent extends EngineExecutionEvent {
    private String parentAgentId;
    private String targetAgentId;
    private AgentCallMode mode;
    private EngineInput input;
}
```

功能注释：

```text
表示 Agent 调用另一个 Agent。
INLINE 模式在当前 handler 内处理。
CHILD_TASK 模式由 TaskControlClient 创建子 task。
```

### 6.6 EngineInterruptedEvent

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/event/EngineInterruptedEvent.java
```

```java
package com.huawei.ascend.service.engine.event;

import com.huawei.ascend.service.engine.model.InterruptType;

public final class EngineInterruptedEvent extends EngineExecutionEvent {
    private InterruptType interruptType;
    private String prompt;
}
```

### 6.7 EngineCompletedEvent

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/event/EngineCompletedEvent.java
```

```java
package com.huawei.ascend.service.engine.event;

import com.huawei.ascend.service.engine.model.EngineOutput;

public final class EngineCompletedEvent extends EngineExecutionEvent {
    private EngineOutput finalOutput;
}
```

### 6.8 EngineFailedEvent

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/event/EngineFailedEvent.java
```

```java
package com.huawei.ascend.service.engine.event;

public final class EngineFailedEvent extends EngineExecutionEvent {
    private String errorCode;
    private String errorMessage;
}
```

### 6.9 EngineCancelledEvent

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/event/EngineCancelledEvent.java
```

```java
package com.huawei.ascend.service.engine.event;

public final class EngineCancelledEvent extends EngineExecutionEvent {
    private String reason;
}
```

## 7. Queue 集成

### 7.1 EngineQueueGateway

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/queue/EngineQueueGateway.java
```

```java
package com.huawei.ascend.service.engine.queue;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;

public interface EngineQueueGateway {

    boolean publish(EngineCommandEvent event);

    void subscribe(EngineCommandConsumer consumer);
}
```

功能注释：

```text
对 internal-event-queue 的薄封装。
不定义 internal-event-queue 基础接口。
不暴露 queue 产品实现。
```

### 7.2 EngineCommandEventFactory

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/queue/EngineCommandEventFactory.java
```

```java
package com.huawei.ascend.service.engine.queue;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.engine.spi.EnqueueEngineCancelRequest;
import com.huawei.ascend.service.engine.spi.EnqueueEngineExecutionRequest;
import com.huawei.ascend.service.engine.spi.EnqueueEngineResumeRequest;

public final class EngineCommandEventFactory {

    public EngineCommandEvent execute(EnqueueEngineExecutionRequest request) {
        return null;
    }

    public EngineCommandEvent resume(EnqueueEngineResumeRequest request) {
        return null;
    }

    public EngineCommandEvent cancel(EnqueueEngineCancelRequest request) {
        return null;
    }
}
```

功能注释：

```text
只负责把外部 request 转为 EngineCommandEvent。
不发布 queue。
不执行 Agent。
```

### 7.3 EngineCommandSubscriber

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/queue/EngineCommandSubscriber.java
```

```java
package com.huawei.ascend.service.engine.queue;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.engine.dispatch.EngineDispatcher;

public final class EngineCommandSubscriber {
    private final EngineQueueGateway queueGateway;
    private final EngineDispatcher dispatcher;

    public EngineCommandSubscriber(EngineQueueGateway queueGateway, EngineDispatcher dispatcher) {
        this.queueGateway = queueGateway;
        this.dispatcher = dispatcher;
    }

    public void start() {
        queueGateway.subscribe(this::onCommand);
    }

    private void onCommand(EngineCommandEvent command) {
        dispatcher.dispatch(command);
    }
}
```

### 7.4 EngineCommandConsumer

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/queue/EngineCommandConsumer.java
```

```java
package com.huawei.ascend.service.engine.queue;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;

@FunctionalInterface
public interface EngineCommandConsumer {
    void accept(EngineCommandEvent event);
}
```

## 8. Dispatch 设计

### 8.1 EngineDispatcher

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/dispatch/EngineDispatcher.java
```

```java
package com.huawei.ascend.service.engine.dispatch;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.engine.event.EngineExecutionEvent;
import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.handler.AgentHandler;
import com.huawei.ascend.service.engine.service.AccessLayerClient;
import com.huawei.ascend.service.engine.service.TaskControlClient;
import java.util.stream.Stream;

public final class EngineDispatcher {
    private final AgentHandlerRegistry registry;
    private final TaskControlClient taskControlClient;
    private final AccessLayerClient accessLayerClient;

    public void dispatch(EngineCommandEvent command) {
        AgentHandler handler = registry.findByAgentId(command.getScope().getAgentId());
        AgentExecutionContext context = buildContext(command);
        Stream<EngineExecutionEvent> events = handler.execute(context);
        events.forEach(this::route);
    }
}
```

功能注释：

```text
EngineDispatcher 是 command 消费后的调度核心。
从 EngineCommandEvent.scope 获取 agentId。
通过 AgentHandlerRegistry.findByAgentId(agentId) 找到 handler。
找不到 handler 时生成 EngineFailedEvent 并调用 TaskControlClient.markFailed。
找到 handler 后构造 AgentExecutionContext 并执行。
将 handler 输出的事件路由到 task-centric-control 和 access-layer。
```

### 8.2 AgentHandlerRegistry

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/dispatch/AgentHandlerRegistry.java
```

```java
package com.huawei.ascend.service.engine.dispatch;

import com.huawei.ascend.service.engine.handler.AgentHandler;

public interface AgentHandlerRegistry {

    void register(String agentId, AgentHandler handler);

    AgentHandler findByAgentId(String agentId);
}
```

功能注释：

```text
维护 agentId 到 AgentHandler 的映射。
agentId 是外部 request 与内部 handler 的闭环匹配字段。
注册时必须保证 agentId 唯一。
调度时必须使用外部传入的 agentId 查找 handler。
```

注册闭环：

```text
OpenJiuwenAgentHandler registers agentId="xxx"
  -> AgentHandlerRegistry.register("xxx", handler)
  -> task-centric-control enqueueExecution(agentId="xxx")
  -> EngineDispatcher findByAgentId("xxx")
  -> handler.execute(context)
```

## 9. Handler 设计

### 9.1 AgentHandler

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/handler/AgentHandler.java
```

```java
package com.huawei.ascend.service.engine.handler;

import com.huawei.ascend.service.engine.event.EngineExecutionEvent;
import java.util.stream.Stream;

public interface AgentHandler {

    String agentId();

    boolean isHealthy();

    Stream<EngineExecutionEvent> execute(AgentExecutionContext context);
}
```

功能注释：

```text
AgentHandler 是具体 Agent 实现的统一执行接口。
第一版只保留 agentId、health、execute。
不定义独立状态服务接口。
不定义独立会话历史服务接口。
不定义独立记忆服务接口。
不定义独立沙箱服务接口。
openJiuwen 需要的 session、memory、checkpoint、sandbox 能力由 OpenJiuwenAgentHandler 内部按 openJiuwen 原生能力接入。
```

### 9.2 AgentExecutionContext

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/handler/AgentExecutionContext.java
```

```java
package com.huawei.ascend.service.engine.handler;

import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;

public final class AgentExecutionContext {
    private EngineExecutionScope scope;
    private EngineInput input;
}
```

功能注释：

```text
AgentExecutionContext 是 EngineCommandEvent 到 AgentHandler 的执行上下文。
只保留 handler 执行必须字段。
```

## 10. OpenJiuwen Adapter 设计

### 10.1 OpenJiuwenAgentHandler

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/adapter/openjiuwen/OpenJiuwenAgentHandler.java
```

```java
package com.huawei.ascend.service.engine.adapter.openjiuwen;

import com.huawei.ascend.service.engine.event.EngineExecutionEvent;
import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.handler.AgentHandler;
import java.util.stream.Stream;

public final class OpenJiuwenAgentHandler implements AgentHandler {
    private final String agentId;
    private final OpenJiuwenAgentFactory agentFactory;
    private final OpenJiuwenMessageConverter messageConverter;

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public boolean isHealthy() {
        return agentFactory != null;
    }

    @Override
    public Stream<EngineExecutionEvent> execute(AgentExecutionContext context) {
        return executeOpenJiuwenAgent(context);
    }
}
```

功能注释：

```text
OpenJiuwenAgentHandler 是 openJiuwen/agent-core-java 的 AgentHandler 实现。
按 agentId 绑定一个可执行 Agent 定义。
通过 OpenJiuwenAgentFactory 创建或恢复 openJiuwen Agent。
通过 OpenJiuwenMessageConverter 转换 EngineInput。
执行 openJiuwen Agent。
将 openJiuwen 输出转换为 EngineExecutionEvent。
```

openJiuwen 能力接入：

```text
session: 使用 openJiuwen AgentSessionApi / WorkflowSessionApi
checkpoint: 使用 openJiuwen Checkpointer
memory: 使用 openJiuwen LongTermMemory / retrieval / context engine
agent call: 使用 openJiuwen AbilityManager 中的 agent ability
sandbox: 如 openJiuwen 当前无直接基类，则第一版不在 engine 文档中定义独立 sandbox service
```

### 10.2 OpenJiuwenAgentFactory

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/adapter/openjiuwen/OpenJiuwenAgentFactory.java
```

```java
package com.huawei.ascend.service.engine.adapter.openjiuwen;

import com.huawei.ascend.service.engine.handler.AgentExecutionContext;

public final class OpenJiuwenAgentFactory {

    public Object create(AgentExecutionContext context) {
        return null;
    }
}
```

功能注释：

```text
根据 agentId 和 OpenJiuwenAgentHandler 内部配置创建 openJiuwen Agent。
可创建 LlmAgent、WorkflowAgent 或 ReActAgent。
agent 类型由注册配置决定，不由 task-centric-control 传入。
```

### 10.3 OpenJiuwenMessageConverter

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/adapter/openjiuwen/OpenJiuwenMessageConverter.java
```

```java
package com.huawei.ascend.service.engine.adapter.openjiuwen;

import com.huawei.ascend.service.engine.handler.AgentExecutionContext;

public final class OpenJiuwenMessageConverter {

    public Object toOpenJiuwenInput(AgentExecutionContext context) {
        return null;
    }
}
```

功能注释：

```text
将 EngineInput 转换为 openJiuwen Agent 可接受的输入。
第一版只处理文本消息和 variables。
```

## 11. Service Client 设计

### 11.1 TaskControlClient

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/service/TaskControlClient.java
```

```java
package com.huawei.ascend.service.engine.service;

import com.huawei.ascend.service.engine.event.EngineCancelledEvent;
import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineFailedEvent;
import com.huawei.ascend.service.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;

public interface TaskControlClient {

    void markRunning(EngineExecutionScope scope);

    void markWaiting(EngineExecutionScope scope, EngineInterruptedEvent event);

    void markSucceeded(EngineExecutionScope scope, EngineCompletedEvent event);

    void markFailed(EngineExecutionScope scope, EngineFailedEvent event);

    void markCancelled(EngineExecutionScope scope, EngineCancelledEvent event);

    EngineExecutionScope createChildTask(EngineExecutionScope parentScope, String targetAgentId, String input);
}
```

功能注释：

```text
TaskControlClient 是 engine 回写 task 状态的客户端接口。
真实 task 状态机由 task-centric-control 定义。
engine 只调用该接口上报状态。
```

### 11.2 AccessLayerClient

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/service/AccessLayerClient.java
```

```java
package com.huawei.ascend.service.engine.service;

import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineFailedEvent;
import com.huawei.ascend.service.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.service.engine.event.EngineOutputEvent;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;

public interface AccessLayerClient {

    void appendOutput(EngineExecutionScope scope, EngineOutputEvent event);

    void completeOutput(EngineExecutionScope scope, EngineCompletedEvent event);

    void failOutput(EngineExecutionScope scope, EngineFailedEvent event);

    void requestUserInput(EngineExecutionScope scope, EngineInterruptedEvent event);
}
```

功能注释：

```text
AccessLayerClient 是 engine 向 access-layer 发送用户可见内容的客户端接口。
engine 不管理 WebSocket/SSE/A2A 连接。
```

## 12. Agent 调 Agent

### 12.1 INLINE 模式

```text
Parent OpenJiuwen Agent
  -> openJiuwen AbilityManager resolves target agent ability
  -> target agent executes inline
  -> result returns to parent agent
  -> parent agent continues
```

规则：

```text
不创建 child task
不独立暴露子 Agent 生命周期
子 Agent 输出默认不直接推送 access-layer
最终输出归属 parent task
```

### 12.2 CHILD_TASK 模式

```text
Parent Agent
  -> EngineAgentCallEvent(mode=CHILD_TASK)
  -> EngineDispatcher calls TaskControlClient.createChildTask(...)
  -> task-centric-control creates child scope
  -> EngineDispatchSpi.enqueueExecution(child agentId, child input)
  -> parent task enters WAITING_CHILD_AGENT
  -> child task completes
  -> task-centric-control calls EngineDispatchSpi.enqueueResume(parent scope, AGENT_CALL_RESULT)
  -> parent resumes
```

规则：

```text
子 Agent 需要独立 task 生命周期时使用 CHILD_TASK
父 task 等待子 task 结果
child task 的状态由 task-centric-control 管理
```

## 13. 状态与输出映射

| Engine event | task-centric-control 动作 | access-layer 动作 |
|---|---|---|
| `EngineStartedEvent` | `markRunning` | 无 |
| `EngineOutputEvent` | 无 | `appendOutput` |
| `EngineInterruptedEvent(HUMAN_INPUT)` | `markWaiting` | `requestUserInput` |
| `EngineInterruptedEvent(APPROVAL)` | `markWaiting` | `requestUserInput` |
| `EngineInterruptedEvent(WAITING_CHILD_AGENT)` | `markWaiting` | 无 |
| `EngineCompletedEvent` | `markSucceeded` | `completeOutput` |
| `EngineFailedEvent` | `markFailed` | `failOutput` |
| `EngineCancelledEvent` | `markCancelled` | 无或 complete |

## 14. 外部开源依赖

### 14.1 openJiuwen/agent-core-java

用途：

```text
OpenJiuwenAgentHandler 目标 Agent 框架
提供 LlmAgent / WorkflowAgent / ReActAgent / AbilityManager / Session / Checkpointer / Memory 能力
```

Maven 坐标按上游实际发布为准。若未发布到 Maven Central 或内部制品库，使用源码依赖方式。

```xml
<dependency>
  <groupId>io.gitcode.openjiuwen</groupId>
  <artifactId>agent-core-java</artifactId>
  <version>${openjiuwen.agent-core.version}</version>
</dependency>
```

源码依赖路径：

```text
third_party/openjiuwen/agent-core-java
```

### 14.2 Spring Boot

用途：

```text
EngineAutoConfiguration
EngineProperties
Bean 装配
生命周期管理
```

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
```

### 14.3 Reactor Core

用途：

```text
openJiuwen stream output 适配
access-layer 流式输出桥接
```

```xml
<dependency>
  <groupId>io.projectreactor</groupId>
  <artifactId>reactor-core</artifactId>
</dependency>
```

### 14.4 Jackson

用途：

```text
EngineCommandEvent 序列化
EngineExecutionEvent 序列化
internal-event-queue payload 编解码
```

```xml
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>
```

## 15. 部署说明

### 15.1 部署形态

`engine` 模块随 `agent-service` 主服务部署。

```text
agent-service.jar
  ├─ access-layer
  ├─ session-task-manager
  ├─ internal-event-queue client
  ├─ task-centric-control
  └─ engine
```

### 15.2 配置样例

```yaml
agent-service:
  engine:
    enabled: true
    command-topic: agent-service.engine.command
    subscriber:
      auto-start: true
    openjiuwen:
      enabled: true
```

### 15.3 本地开发部署

```text
internal-event-queue: in-memory
OpenJiuwenAgentHandler: enabled
EngineCommandSubscriber: auto-start
```

启动命令：

```bash
cd agent-service
mvn spring-boot:run -Dspring-boot.run.profiles=local-engine
```

### 15.4 生产部署

```text
internal-event-queue: Kafka / RocketMQ / Redis Streams
OpenJiuwenAgentHandler: enabled
EngineCommandSubscriber: auto-start
queue topic: agent-service.engine.command
```

生产要求：

```text
EngineCommandEvent 至少一次投递
EngineDispatcher 找不到 scope.agentId 时必须回写 task failed
AgentHandler 执行异常必须转换为 EngineFailedEvent
用户输出必须通过 AccessLayerClient
状态更新必须通过 TaskControlClient
```

### 15.5 健康检查

健康检查项：

```text
EngineQueueGateway 可用
EngineCommandSubscriber running
AgentHandlerRegistry 至少存在一个注册 agentId
OpenJiuwenAgentHandler.isHealthy=true
TaskControlClient 可用
AccessLayerClient 可用
```

### 15.6 回滚策略

```yaml
agent-service:
  engine:
    enabled: false
```

回滚行为：

```text
EngineDispatchSpi 拒绝新 command 入队
EngineCommandSubscriber 停止消费新 command event
未消费 command event 保留在 internal-event-queue
已运行 task 按 task-centric-control 策略处理
```

## 16. 实现顺序

### Phase 1：最小闭环

```text
spi/EngineDispatchSpi.java
spi/EnqueueEngineExecutionRequest.java
spi/EnqueueEngineStatus.java
model/EngineExecutionScope.java
model/EngineInput.java
model/EngineMessage.java
event/EngineCommandEvent.java
event/EngineStartedEvent.java
event/EngineOutputEvent.java
event/EngineCompletedEvent.java
event/EngineFailedEvent.java
queue/EngineQueueGateway.java
queue/EngineCommandEventFactory.java
queue/EngineCommandSubscriber.java
dispatch/EngineDispatcher.java
dispatch/AgentHandlerRegistry.java
handler/AgentHandler.java
handler/AgentExecutionContext.java
adapter/openjiuwen/OpenJiuwenAgentHandler.java
adapter/openjiuwen/OpenJiuwenAgentFactory.java
adapter/openjiuwen/OpenJiuwenMessageConverter.java
service/TaskControlClient.java
service/AccessLayerClient.java
```

### Phase 2：中断与恢复

```text
spi/EnqueueEngineResumeRequest.java
EngineDispatchSpi.enqueueResume(...)
event/EngineInterruptedEvent.java
model/InterruptType.java
```

### Phase 3：Agent 调 Agent

```text
event/EngineAgentCallEvent.java
model/AgentCallMode.java
INLINE 模式
CHILD_TASK 模式
TaskControlClient.createChildTask(...)
WAITING_CHILD_AGENT
```

### Phase 4：生产接入

```text
internal-event-queue 生产实现
openJiuwen/agent-core-java 正式依赖
健康检查
部署开关
失败回写
```

## 17. 过度设计删除项

本终稿删除以下设计：

```text
旧版 runtime dispatch 对外接口命名
旧版 runtime dispatch 包路径
独立状态服务接口
独立会话历史服务接口
独立记忆服务接口
独立沙箱服务接口
复杂消息块与工具调用块
独立 selector 与底层框架类型外部传参
复杂 EnqueueRuntimeExecutionResult
复杂 RuntimeCommandEvent 字段
独立幂等与并发章节
非第一版必需的治理、指标、代码生成依赖
```

## 18. 最终结论

最终模块名：

```text
engine
```

最终包路径：

```text
com.huawei.ascend.service.engine
```

最终代码目录：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/
```

最终外部入口：

```text
com.huawei.ascend.service.engine.spi.EngineDispatchSpi
```

最终外部执行请求：

```text
EnqueueEngineExecutionRequest(scope, input)
```

最终入队返回：

```text
EnqueueEngineStatus.SUCCESS / EnqueueEngineStatus.FAILED
```

最终 agent 查找闭环：

```text
task-centric-control passes scope.agentId
  -> EngineCommandEvent carries scope.agentId
  -> EngineDispatcher reads scope.agentId
  -> AgentHandlerRegistry.findByAgentId(agentId)
  -> AgentHandler.execute(context)
```

最终 handler 抽象：

```text
AgentHandler
  - agentId()
  - isHealthy()
  - execute(AgentExecutionContext)
```

最终 openJiuwen 实现：

```text
OpenJiuwenAgentHandler
  -> OpenJiuwenAgentFactory
  -> OpenJiuwenMessageConverter
  -> openJiuwen/agent-core-java Agent
```
