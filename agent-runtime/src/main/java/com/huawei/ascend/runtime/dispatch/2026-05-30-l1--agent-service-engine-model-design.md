# Agent Service Engine 模块设计终稿

> 版本：v1.4 终稿  
> 日期：2026-05-30（v1.2 修订：2026-05-31；v1.3 修订：2026-05-31）  
> 服务：`agent-service`  
> 模块名：`engine`  
> Java 包根路径：`agent-service/src/main/java/com/huawei/ascend/service/engine/`  
> Java 包名：`com.huawei.ascend.service.engine`

> v1.2 修订说明：纠正 v1.1 将入站入口接口（`EngineDispatchSpi`）误归为 SPI 的分类错误。
> 按"方向性定义"重新区分 API 与 SPI：入站、由 engine 实现、供外部调用的接口归为 **API**；
> 由 engine 定义、供外部或插件实现的接口（扩展点与出站端口）归为 **SPI**。
> 入站接口 `EngineDispatchSpi` 重命名为 `EngineDispatchApi`，迁入新增的 `api/` 目录。
> 详见 §2.1 分类准则与 §4.0 分类总表。

> v1.3 修订说明：依据 `openJiuwen/agent-core-java` 分支 `0.1.12` 真实源码逐行核对，
> 纠正与框架实际不符的描述并补全空白：
> （1）§14.1 依赖 groupId 由 `io.gitcode.openjiuwen` 改为 `com.openjiuwen`，版本锁定 `0.1.12`；
> （2）§14.3 删除"reactor 用于 openJiuwen stream 适配"的理由——框架流式返回 `Iterator<Object>` 而非 `Flux`；
> （3）§15.2 补全 `EngineProperties` 的 LLM 配置字段；
> （4）新增 §10.4 适配器执行契约：`Runner.runAgent` 同步返回 `Map{output, result_type}`，
> `result_type ∈ {answer, error, interrupt}` 天然映射 Completed/Failed/Interrupted 三事件。
> 详细论证与源码出处见同目录补丁文档 `2026-05-31-l1--agent-service-engine-openjiuwen-adapter-verification-design.md`。

> v1.4 修订说明：依据 clone 到 `third_party/agentscope/` 的 `agentscope-runtime-java` /
> `agentscope-java` 真实源码,对齐分层定位:
> （1）§1 明确 engine 对标 agentscope-runtime-java（runtime 层,非框架层）,补三层所有权模型;
> （2）§10 代码组织修正:"造具体 agent（prompt/工具/模型）"下沉到开发者 agent 应用层,
> engine 只留通用适配基类;
> （3）新增 §10.5 Agent 应用模块形态决策（B-deferred:逻辑边界按库划清,物理拆分推迟）;
> （4）§14/§15 标注 agent 应用作为 `samples/` 下独立模块,不挂根 reactor。
> 详细论证见同目录指导文档 `2026-05-31-l2--agent-service-engine-agentscope-alignment-guidance.md`。

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
  -> engine.api.EngineDispatchApi
  -> internal-event-queue
  -> engine.command.EngineCommandProcessor
  -> engine.dispatch.EngineDispatcher
  -> engine.spi.AgentHandler
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

### 1.1 对标定位与三层所有权模型

engine 模块对标 **agentscope-runtime-java（runtime 层）**，不是 agent 开发框架本身。
对应关系：openjiuwen `agent-core-java`（框架）↔ agentscope `agentscope-java`；
agent-service 的 `engine` 模块 ↔ agentscope `engine-core`。agent-service 整体对外起
接口服务，engine 只是其中一个模块，承担 **agent 执行器** 角色。

借鉴 agentscope-runtime 真实代码呈现的三层所有权边界（engine 保留本设计既有的
"命令队列 + 事件回写"模型，**不采用** agentscope 的 `AgentApp.run(port)` HTTP 部署）：

```text
① 框架层（第三方，engine 只消费）
   com.openjiuwen:agent-core-java:0.1.12  → ReActAgent / Runner / Toolkit
        ↑ 被 ② 调用
② engine 层（本项目核心，通用、不含任何具体 agent）
   agent-service/.../engine/  → SPI 契约 + 队列/dispatch/事件回写
                               + OpenJiuwen 通用适配基类（连框架，不定义"是哪个 agent"）
        ↑ 被 ③ 继承 / 注册
③ agent 应用层（开发者编写的"具体 agent"，独立模块）
   samples/<agent-app>/  → 继承 ② 的适配基类，build 具体 ReActAgent
                          （prompt + 工具 + 模型在此定义）
   依赖：② engine SPI + ① openjiuwen 框架
```

engine 与 agentscope 的**执行内核同形**（agentscope `streamQuery→Flux<Event>`；
本设计 `execute→Stream<EngineExecutionEvent>`），差异仅在触发与结果出口：engine 由
`EngineCommandEvent` 经内部队列触发，结果回写 `TaskControlClient`/`AccessLayerClient`。
详见同目录指导文档 `2026-05-31-l2--agent-service-engine-agentscope-alignment-guidance.md`。

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

外部接口目录按"方向性定义"区分为两类：

```text
api/   入站接口：由 engine 实现，供外部（task-centric-control）调用
spi/   provider 扩展点：由 engine 定义，供 agent handler 插件实现
port/  出站端口：由 engine 定义，供 access / task-control 适配器实现
queue/ engine 内部队列协作端口与订阅器
```

### 2.1 API 与 SPI 分类准则（方向性定义）

判定一个接口归 API 还是 SPI，看**调用方向与实现方**：

| 维度 | API | SPI |
|---|---|---|
| 调用方 | 外部（task-centric-control） | engine 自身 |
| 实现方 | engine 自身 | 外部服务或插件 |
| 方向 | 入站（inbound / provided） | 扩展（extension） |
| 本模块示例 | `EngineDispatchApi` | `AgentHandler` |

要点：

```text
入站入口 EngineDispatchApi 由 engine 实现、被 task-centric-control 调用，是 API，不是 SPI。
插件扩展点 AgentHandler 由 engine 定义、被 openJiuwen 适配器实现，是 SPI。
EngineCommandGateway 属 engine 内部 queue 协作端口，放在 engine.command。
TaskControlClient / AccessLayerClient 属 engine 出站端口，放在 engine.port。
数据载体（model / event / 请求载体 / 状态枚举）既非 API 也非 SPI，按 Rule R-D.d 放在功能包而非 spi/ 包。
```

## 3. 总体目录结构

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/
  api/
    EngineDispatchApi.java
    EnqueueEngineExecutionRequest.java
    EnqueueEngineResumeRequest.java
    EnqueueEngineCancelRequest.java
    EnqueueEngineStatus.java
  spi/
    AgentHandler.java
  port/
    TaskControlClient.java
    AccessLayerClient.java
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
  command/
    EngineCommandGateway.java
    EngineCommandEventFactory.java
    EngineCommandProcessor.java
  dispatch/
    EngineDispatcher.java
    AgentHandlerRegistry.java
  handler/
    AgentExecutionContext.java
  adapter/openjiuwen/
    OpenJiuwenAgentHandler.java
    OpenJiuwenAgentFactory.java
    OpenJiuwenMessageConverter.java
  config/
    EngineAutoConfiguration.java
    EngineProperties.java
```

> 目录归属说明：
> - `api/` 入站接口（engine 实现，外部调用）：`EngineDispatchApi` 及其请求/状态载体。
> - `spi/` 只放 provider 扩展点：当前只有 `AgentHandler`。
> - `port/` 放 engine 出站端口：`TaskControlClient` / `AccessLayerClient`。
> - `command/` 放 engine 内部队列协作端口与订阅器：`EngineCommandGateway` / `EngineCommandEventFactory` / `EngineCommandProcessor`。
> - `dispatch/`、`adapter/`、`config/` 为 engine 内部实现，既非 API 也非 SPI。`AgentHandlerRegistry` 由 engine 自身实现并自用，属内部接口，保留在 `dispatch/`。
> - `model/`、`event/`、`handler/AgentExecutionContext` 为数据/上下文载体，按 Rule R-D.d 不放入 `spi/` 包。
> - `TaskControlClient` / `AccessLayerClient` 的具体实现由 task-control / access 模块提供适配器。

## 4. API 定义

### 4.0 接口分类总表

按 §2.1 方向性定义，engine 模块对外接口归类如下：

| 接口 | 分类 | 包 | 调用方 → 实现方 | 章节 |
|---|---|---|---|---|
| `EngineDispatchApi` | API | `engine.api` | task-centric-control → engine | §4.1 |
| `EnqueueEngineExecutionRequest` | API 载体 | `engine.api` | — | §4.2 |
| `EnqueueEngineResumeRequest` | API 载体 | `engine.api` | — | §4.3 |
| `EnqueueEngineCancelRequest` | API 载体 | `engine.api` | — | §4.4 |
| `EnqueueEngineStatus` | API 载体 | `engine.api` | — | §4.5 |
| `AgentHandler` | SPI（扩展点） | `engine.spi` | engine → openJiuwen 适配器 | §9.1 |
| `TaskControlClient` | 出站端口 | `engine.port` | engine → task-centric-control 适配器 | §11.1 |
| `AccessLayerClient` | 出站端口 | `engine.port` | engine → access-layer 适配器 | §11.2 |

> v1.1 错误：曾将入站入口 `EngineDispatchSpi` 及其请求/状态载体整体归入"SPI 定义"。
> 入站入口由 engine 实现、被 task-centric-control 调用，本质是 **API**。
> v1.2 已将其重命名为 `EngineDispatchApi` 并迁入 `api/`。
> v1.3 进一步收敛 SPI 语义：`engine.spi` 只保留 provider 扩展点 `AgentHandler`；
> engine 自己的队列协作放在 `engine.command`，出站调用放在 `engine.port`。

### 4.1 EngineDispatchApi

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/api/EngineDispatchApi.java
```

```java
package com.huawei.ascend.service.engine.api;

public interface EngineDispatchApi {

    EnqueueEngineStatus enqueueExecution(EnqueueEngineExecutionRequest request);

    EnqueueEngineStatus enqueueResume(EnqueueEngineResumeRequest request);

    EnqueueEngineStatus enqueueCancel(EnqueueEngineCancelRequest request);
}
```

功能注释：

```text
task-centric-control 调用 engine 的唯一外部入口（入站 API）。
由 engine 实现，由 task-centric-control 调用。
只负责异步入队。
不直接执行 Agent。
不直接返回真实执行状态。
真实执行状态通过 TaskControlClient（engine.port 出站端口）回写。
```

### 4.2 EnqueueEngineExecutionRequest

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/api/EnqueueEngineExecutionRequest.java
```

```java
package com.huawei.ascend.service.engine.api;

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
  -> EngineDispatchApi.enqueueExecution
  -> EngineCommandEvent(scope.agentId)
  -> EngineCommandProcessor
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
agent-service/src/main/java/com/huawei/ascend/service/engine/api/EnqueueEngineResumeRequest.java
```

```java
package com.huawei.ascend.service.engine.api;

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
agent-service/src/main/java/com/huawei/ascend/service/engine/api/EnqueueEngineCancelRequest.java
```

```java
package com.huawei.ascend.service.engine.api;

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
agent-service/src/main/java/com/huawei/ascend/service/engine/api/EnqueueEngineStatus.java
```

```java
package com.huawei.ascend.service.engine.api;

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
CHILD_TASK 模式暂不暴露 createChildTask 入口；当前实现将该模式视为 Phase 2 延后能力。
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

### 7.1 EngineCommandGateway

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/command/EngineCommandGateway.java
```

```java
package com.huawei.ascend.service.engine.command;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;

public interface EngineCommandGateway {

    boolean publish(EngineCommandEvent event);

    reactor.core.publisher.Flux<EngineCommandEvent> commands();
}
```

功能注释：

```text
内部队列端口：由 engine 定义，由 engine queue 实现接入。
对 internal-event-queue 的薄封装。
不定义 internal-event-queue 基础接口。
不暴露 queue 产品实现。
```

### 7.2 EngineCommandEventFactory

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/command/EngineCommandEventFactory.java
```

```java
package com.huawei.ascend.service.engine.command;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.engine.api.EnqueueEngineCancelRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineExecutionRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineResumeRequest;

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

### 7.3 EngineCommandProcessor

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/command/EngineCommandProcessor.java
```

```java
package com.huawei.ascend.service.engine.command;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.engine.dispatch.EngineDispatcher;

public final class EngineCommandProcessor {
    private final EngineCommandGateway queueGateway;
    private final EngineDispatcher dispatcher;

    public EngineCommandProcessor(EngineCommandGateway queueGateway, EngineDispatcher dispatcher) {
        this.queueGateway = queueGateway;
        this.dispatcher = dispatcher;
    }

    public void start() {
        queueGateway.commands().subscribe(this::onCommand);
    }

    private void onCommand(EngineCommandEvent command) {
        dispatcher.dispatch(command);
    }
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
import com.huawei.ascend.service.engine.spi.AgentHandler;
import com.huawei.ascend.service.engine.port.AccessLayerClient;
import com.huawei.ascend.service.engine.port.TaskControlClient;
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

import com.huawei.ascend.service.engine.spi.AgentHandler;

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
agent-service/src/main/java/com/huawei/ascend/service/engine/spi/AgentHandler.java
```

```java
package com.huawei.ascend.service.engine.spi;

import com.huawei.ascend.service.engine.event.EngineExecutionEvent;
import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import java.util.stream.Stream;

public interface AgentHandler {

    String agentId();

    boolean isHealthy();

    Stream<EngineExecutionEvent> execute(AgentExecutionContext context);
}
```

功能注释：

```text
SPI（扩展点）：由 engine 定义，由 openJiuwen 适配器等插件实现。
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

> 代码组织修正（v1.4，对齐 §1.1 三层模型）：本章三件套按所有权拆分。
> `OpenJiuwenAgentHandler`（适配基类，走 `Runner.runAgent` 并映射事件）与
> `OpenJiuwenMessageConverter`（入参转换）属 ②engine 层，通用、不定义具体 agent；
> 而 `OpenJiuwenAgentFactory` 中"build 具体 ReActAgent（prompt/工具/模型）"的逻辑属
> ③agent 应用层，由开发者在 `samples/` 下的 agent 模块实现（见 §10.5）。
> engine 侧 Factory 退化为接缝接口/抽象，具体实现下沉到开发者模块。

### 10.1 OpenJiuwenAgentHandler

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/adapter/openjiuwen/OpenJiuwenAgentHandler.java
```

```java
package com.huawei.ascend.service.engine.adapter.openjiuwen;

import com.huawei.ascend.service.engine.event.EngineExecutionEvent;
import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.spi.AgentHandler;
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
第一版形态（0.1.12）：Map.of("query", lastUserText, "conversation_id", scope.getTaskId())。
```

### 10.4 适配器执行契约（0.1.12 核实）

唯一接缝在 `OpenJiuwenAgentFactory.create()`：用 `AgentCard.builder()` 造卡、
`new ReActAgent(card)` 造 agent、`ReActAgentConfig.builder()....configureModelClient(
provider, apiKey, apiBase, modelName, sslVerify)` 注入 LLM（配置来自 `EngineProperties`，见 §15.2），
`agent.configure(config)` 生效。其余适配器代码只见 engine 自身类型。

`OpenJiuwenAgentHandler.execute()` 调 `Runner.runAgent(agent, input, null, null)`，
**同步**返回 `Map{"output", "result_type"}`，据 `result_type` 映射为第 6 章事件流：

| 框架 `result_type` | 适配器产出事件 | 对应 §13 动作 |
|---|---|---|
| `answer` | Started → Completed(output) | markRunning → markSucceeded + completeOutput |
| `error` | Started → Failed(output) | markRunning → markFailed + failOutput |
| `interrupt` | Started → Interrupted | markRunning → markWaiting + requestUserInput |
| 抛异常 | Failed(异常信息) | markFailed + failOutput |

`interrupt` 分支细化属 Phase 2。详细论证与源码出处见同目录补丁文档
`2026-05-31-l1--agent-service-engine-openjiuwen-adapter-verification-design.md`。

### 10.5 Agent 应用模块形态（决策：B-deferred）

依据 agentscope 真实代码：其 example 模块依赖 `agentscope-runtime-engine` /
`-agentscope` / `-web` 三个**独立发布的库 jar**，而非依赖某个可运行服务——即
engine 是**库**不是服务，开发者的 agent 应用才是可运行单元。

**决策（已确认）**：方向取 B（engine 按"可被依赖的库"划清逻辑边界），但**物理拆分推迟**。

近期形态（不破坏整体架构）：
- engine 代码留在 agent-service 内，作为清晰的包边界（`engine/spi`、`engine/adapter/openjiuwen`）；
- 开发者的具体 agent 子类作为 `samples/<agent-app>/` 下**独立 Maven 模块**存在，
  parent 指向 `spring-ai-ascend-parent`，**不挂进根 `<modules>`**（reactor 外，按需显式加入），
  样板见 `samples/finance-loan-review/`；
- sample 模块暂依赖 `agent-service` 获取 engine SPI 与适配基类。

后置裁定（待实际体验后再定，不在本设计强行结论）：
- 是否将 engine 抽为独立可发布 artifact，或复用根 pom 已有的 `agent-execution-engine` 模块；
- `agent-service` 作为 Boot 应用被 sample 依赖其类的可行性（Boot repackage 后需 classifier）。

详细论证与样板 pom 见同目录指导文档
`2026-05-31-l2--agent-service-engine-agentscope-alignment-guidance.md`。

## 11. Service Client 设计

### 11.1 TaskControlClient

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/port/TaskControlClient.java
```

```java
package com.huawei.ascend.service.engine.port;

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
}
```

功能注释：

```text
出站端口：由 engine 定义，由 task-centric-control 适配器实现。
TaskControlClient 是 engine 回写 task 状态的客户端接口。
真实 task 状态机由 task-centric-control 定义。
engine 只调用该接口上报状态。
```

### 11.2 AccessLayerClient

路径：

```text
agent-service/src/main/java/com/huawei/ascend/service/engine/port/AccessLayerClient.java
```

```java
package com.huawei.ascend.service.engine.port;

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
出站端口：由 engine 定义，由 access-layer 适配器实现。
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
  -> deferred to a later task-control policy wave
```

规则：

```text
当前实现不暴露 createChildTask 接口
子 Agent 独立 task 生命周期由后续 task-control policy wave 决定
engine Phase 1 不直接创建或管理 child task
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
提供 ReActAgent（0.1.12 已核实）/ AbilityManager / Runner / Session / Memory 能力
执行入口 Runner.runAgent，单 agent 实现 com.openjiuwen.core.singleagent.ReActAgent
```

Maven 坐标依 `0.1.12` 真实发布为准（groupId `com.openjiuwen`）。若未发布到 Maven Central 或内部制品库，使用源码依赖方式。

```xml
<dependency>
  <groupId>com.openjiuwen</groupId>
  <artifactId>agent-core-java</artifactId>
  <version>0.1.12</version>
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
access-layer 流式输出桥接
```

> 注：openJiuwen 0.1.12 不产出 Reactor 流。其单次执行 `Runner.runAgent` 为同步，
> 流式 `ReActAgent.stream` 返回 `Iterator<Object>`。第一版适配器走同步路径，
> 无需 Reactor；Iterator→Stream 转换不依赖本库。reactor-core 仅为 access-layer
> 流式桥接保留。

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

> 开发者的具体 agent 应用（③层，见 §1.1/§10.5）作为 `samples/<agent-app>/` 下独立模块，
> **不在** `agent-service.jar` 内、不挂根 reactor；它在实现/体验阶段单独构建，依赖
> agent-service 获取 engine SPI 与适配基类。

### 15.2 配置样例

`EngineProperties` 字段（前缀 `agent-service.engine`）。openJiuwen LLM 配置 5 字段沿用框架
`apiconfig.json` 约定键名；`api-key` 为密钥，经 `spring-cloud-starter-vault-config` 注入，
**禁止明文写入 yaml**（下方占位仅示意层级）。

| 字段 | 类型 | 框架来源键 | 说明 |
|---|---|---|---|
| `openjiuwen.model-provider` | String | `MODEL_PROVIDER` | 模型供应商标识 |
| `openjiuwen.api-key` | String | `API_KEY` | 密钥：走 Vault |
| `openjiuwen.api-base` | String | `API_BASE` | 模型服务 base url |
| `openjiuwen.model-name` | String | `MODEL_NAME` | 模型名 |
| `openjiuwen.ssl-verify` | boolean | `LLM_SSL_VERIFY` | 默认 true |
| `openjiuwen.max-iterations` | int | —（engine 侧默认） | ReAct 最大轮次，默认 3 |

```yaml
agent-service:
  engine:
    enabled: true
    command-topic: engine:commands
    subscriber:
      auto-start: true
    openjiuwen:
      enabled: true
      model-provider: ${MODEL_PROVIDER}
      api-base: ${API_BASE}
      model-name: ${MODEL_NAME}
      ssl-verify: true
      max-iterations: 3
      # api-key 经 Vault 注入，不在此明文配置
```

### 15.3 本地开发部署

```text
internal-event-queue: in-memory
OpenJiuwenAgentHandler: enabled
EngineCommandProcessor: auto-start
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
EngineCommandProcessor: auto-start
queue topic: engine:commands
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
EngineCommandGateway 可用
EngineCommandProcessor running
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
EngineDispatchApi 拒绝新 command 入队
EngineCommandProcessor 停止消费新 command event
未消费 command event 保留在 internal-event-queue
已运行 task 按 task-centric-control 策略处理
```

## 16. 实现顺序

### Phase 1：最小闭环

```text
api/EngineDispatchApi.java
api/EnqueueEngineExecutionRequest.java
api/EnqueueEngineStatus.java
model/EngineExecutionScope.java
model/EngineInput.java
model/EngineMessage.java
event/EngineCommandEvent.java
event/EngineStartedEvent.java
event/EngineOutputEvent.java
event/EngineCompletedEvent.java
event/EngineFailedEvent.java
command/EngineCommandGateway.java
command/EngineCommandEventFactory.java
command/EngineCommandProcessor.java
dispatch/EngineDispatcher.java
dispatch/AgentHandlerRegistry.java
spi/AgentHandler.java
handler/AgentExecutionContext.java
adapter/openjiuwen/OpenJiuwenAgentHandler.java
adapter/openjiuwen/OpenJiuwenAgentFactory.java
adapter/openjiuwen/OpenJiuwenMessageConverter.java
port/TaskControlClient.java
port/AccessLayerClient.java
```

### Phase 2：中断与恢复

```text
api/EnqueueEngineResumeRequest.java
EngineDispatchApi.enqueueResume(...)
event/EngineInterruptedEvent.java
model/InterruptType.java
```

### Phase 3：Agent 调 Agent

```text
event/EngineAgentCallEvent.java
model/AgentCallMode.java
INLINE 模式
CHILD_TASK 模式（deferred）
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

对齐 agentscope 时同样删除（不引入）：

```text
agentscope AgentApp.run(port) 的 HTTP/SSE 部署形态（保留队列+事件回写）
agentscope sandbox / Deployer / 多协议端点（超出 engine 执行器职责）
将 agent 应用 sample 模块挂进根 <modules>（reactor 外，按需显式加入）
近期把 engine 抽为独立可发布 artifact（B-deferred：逻辑边界先行，物理拆分后置）
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
com.huawei.ascend.service.engine.api.EngineDispatchApi
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
