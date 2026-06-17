# OpenJiuwen Workflow Runtime Adapter — 设计文档

> 适用目录：`architecture/docs/L2/agent-runtime/`
> 目标模块：`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/`
> 最后更新：2026-06-17

---

## 1. 概述

### 1.1 特性定位

agent-runtime 新增 OpenJiuwen Workflow 适配层，将 OpenJiuwen 原生的 DAG 工作流引擎接入 runtime 的统一执行模型，支持多步骤 Workflow Agent 的托管执行与人工确认中断/恢复。

- **解决的问题**：当前 runtime 仅支持 OpenJiuwen ReActAgent（单 Agent 自主循环），无法运行显式 DAG 编排的多步骤工作流。Workflow 适配后，runtime 可以托管需要确定性步骤编排、人工审批节点的流水线型 Agent。
- **适用场景**：需要多步骤 DAG 编排的业务流程（如内容审核流水线、多阶段数据处理）、需要人工确认节点的审批类 Agent、需要被其他 Agent 作为 Tool 调用的 Workflow Agent。如果只需要 LLM 自主推理循环，使用已有的 ReActAgent Adapter。

### 1.2 核心设计原则

1. **复用 runtime 契约** — Workflow Adapter 实现与 ReActAgent Adapter 相同的 `AgentRuntimeHandler` SPI，A2A 层无需修改
2. **中断-续接复用已有通道** — Workflow 的 `INPUT_REQUIRED` 映射到 `AgentExecutionResult.INTERRUPTED`，复用现有的 `UserInputInterrupt` 类型和 A2A `Task → INPUT_REQUIRED` 机制
3. **Session 驱动状态持久化** — 利用 OpenJiuwen 原生 Checkpointer，按 `sessionId` 保存/恢复 Workflow 图状态，中断恢复无需 Adapter 层额外管理状态
4. **Workflow 构建权归用户** — Adapter 只负责执行和中断映射，Workflow DAG 的构建由子类在 `createOpenJiuwenWorkflow()` 中完成，保持灵活性

### 1.3 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| Workflow Handler | 扩展 runtime SPI，托管 Workflow 执行生命周期 | `OpenJiuwenWorkflowAgentRuntimeHandler` | ⬜ |
| Workflow StreamAdapter | WorkflowOutput → AgentExecutionResult 类型映射 | `OpenJiuwenWorkflowStreamAdapter` | ⬜ |
| Workflow 中断/恢复 | INPUT_REQUIRED → INTERRUPTED → InteractiveInput 恢复 | 复用 `UserInputInterrupt` + `AgentExecutionContext.INPUT_TYPE_REMOTE_RESUME` | ⬜ |
| Workflow 轨迹观测 | Workflow 组件执行事件 → TrajectoryEvent | 扩展现有 `OpenJiuwenTrajectoryRail` 或新增 Workflow 版本 | ⬜ |
| 邻接 Agent 互调 | Workflow Agent 被其他 Agent 作为远程 Tool 调用 | 复用 `RemoteAgentToolSpec` + `OpenJiuwenRemoteToolInstaller` | ⬜ |

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| Workflow 进程内执行 | ⬜ | `Workflow.invoke()` 同步调用，结果包装为 Stream |
| Workflow 多步骤 DAG | ⬜ | 支持 Start/End/LLM/Tool/Branch/Loop/Questioner 等全部原生组件 |
| 中断-续接（人工确认） | ⬜ | QuestionerComponent 触发 → INPUT_REQUIRED → 用户输入 → InteractiveInput 恢复 |
| 中断-续接（远程 Agent） | ⬜ | Workflow 内调用远程 A2A Tool → 复用现有远程中断通道 |
| Workflow Session Checkpoint | ⬜ | 复用 `OpenJiuwenCheckpointerConfigurer`，按 sessionId 持久化图状态 |
| Workflow 流式输出 | ⬜ | 每步 component 完成时输出 chunk |
| Workflow 轨迹事件 | ⬜ | COMPONENT_START/END 事件，含 nodeId、耗时、输入输出摘要 |
| 模型配置注入 | ⬜ | 复用现有 `sample.openjiuwen.*` 配置模式 |
| Agent Card 能力声明 | ⬜ | 声明 skills 列表，供邻接 Agent 发现和调用 |
| 取消 | ⬜ | 同 ReActAgent：同步执行模型下 cancel 仅关闭 Stream，不中断 LLM 调用 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| Workflow YAML DSL 解析 | YAML → Workflow DAG 的反序列化属于 SDK 层职责，不属于 runtime adapter | 在 `agent-sdk` 模块实现 |
| 多 Workflow 跳转（WorkflowAgent） | `WorkflowAgent` 是多 workflow 之间的路由器，与单 workflow DAG 执行是不同的执行模型 | 后续版本单独适配 |
| Branch 条件表达式引擎 | Branch 路由逻辑由 Workflow 构建者在 `createOpenJiuwenWorkflow()` 中以 Java 代码定义 | — |
| SubWorkflow 嵌套 | `SubWorkflowComponent` 在当前版本保持可用但不作为适配重点 | 直接使用 |

### 2.3 接口契约

#### OpenJiuwenWorkflowAgentRuntimeHandler

```java
/**
 * OpenJiuwen Workflow Agent 的 runtime 适配基类。
 *
 * <p>子类实现 {@link #createOpenJiuwenWorkflow(AgentExecutionContext)} 构建 Workflow DAG，
 * 基类负责执行循环、中断映射和结果适配。
 *
 * <p>执行模型：
 * <pre>
 *   Workflow.invoke(inputs, session, null)
 *     → INPUT_REQUIRED → AgentExecutionResult.interrupted(userInputInterrupt)
 *     → COMPLETED      → AgentExecutionResult.completed(finalOutput)
 *     → ERROR          → AgentExecutionResult.failed(errorDetail)
 * </pre>
 */
public abstract class OpenJiuwenWorkflowAgentRuntimeHandler
        extends AbstractAgentRuntimeHandler {

    public OpenJiuwenWorkflowAgentRuntimeHandler(String agentId) { ... }

    /**
     * 子类在此方法中构建 Workflow DAG。
     * 基类在每次 execute() 调用时调用此方法，允许按请求动态构建 Workflow。
     *
     * @param context 执行上下文，包含租户/用户/会话标识和模型配置
     * @return 配置完成的 Workflow 实例（已添加所有节点和边）
     */
    protected abstract Workflow createOpenJiuwenWorkflow(AgentExecutionContext context);

    // 基类管理的内部状态:
    // - sessionId → 按 context.agentStateKey 关联，初次执行创建，恢复时复用
    // - 当前 Workflow 实例引用（每次 execute() 通过 createOpenJiuwenWorkflow() 重建）
    //
    // Skills 声明: 不在 Handler 层——沿用已有模式，通过 YAML 配置:
    //   agent-runtime.access.a2a.agent-card.skills
    // 与 ReActAgent 一致，skills 是 Agent Card 元数据，不是 Workflow 结构的一部分。
}
```

#### OpenJiuwenWorkflowStreamAdapter

```java
/**
 * 将 Workflow.invoke() 的 WorkflowOutput 映射为 AgentExecutionResult 流。
 *
 * <p>映射规则：
 * <ul>
 *   <li>COMPLETED → OUTPUT 流 + 最终 COMPLETED</li>
 *   <li>INPUT_REQUIRED → INTERRUPTED(UserInputInterrupt)</li>
 *   <li>ERROR → FAILED(errorCode, message)</li>
 * </ul>
 */
public class OpenJiuwenWorkflowStreamAdapter implements StreamAdapter {

    @Override
    public Stream<AgentExecutionResult> adapt(Stream<?> raw) { ... }

    /**
     * 从 WorkflowOutput 中提取 InteractionOutput，
     * 构造 UserInputInterrupt。
     */
    static UserInputInterrupt toUserInputInterrupt(WorkflowOutput output) { ... }
}
```

#### 数据类型

| 类型 | 关键字段 | 含义 | 约束 |
|------|---------|------|------|
| `WorkflowOutput` | `result`, `state` | Workflow 执行结果和状态 | `state` ∈ {COMPLETED, INPUT_REQUIRED, ERROR} |
| `InteractiveInput` | 按 nodeId 索引的用户输入 | 恢复 Workflow 的输入载体 | 与中断时的 sessionId 匹配 |
| `WorkflowExecutionState` | `COMPLETED`, `INPUT_REQUIRED`, `ERROR` | Workflow 终止/挂起/失败 | — |
| `InteractionOutput` | `id`(nodeId), `value`(prompt) | Questioner 节点产出的交互请求 | — |

#### 行为承诺

- **必须**：`execute()` 返回的 Stream 包含至少一个终端事件（COMPLETED / FAILED / INTERRUPTED）
- **必须**：中断恢复时使用与初次执行相同的 `sessionId`，确保 Checkpointer 正确恢复 Workflow 状态
- **必须**：Workflow Agent 的 Agent Card 必须声明至少一个 skill，才能被邻接 Agent 作为 Tool 发现
- **禁止**：Adapter 不缓存 Workflow 实例——每次 `execute()` 调用 `createOpenJiuwenWorkflow()` 构建新实例（状态由 Checkpointer 按 sessionId 恢复，非 Workflow 实例自身）
- **允许**：子类可以在 `createOpenJiuwenWorkflow()` 中根据 `AgentExecutionContext` 动态选择不同的 DAG 拓扑

---

## 3. 模块结构

### 3.1 包结构

```
engine/openjiuwen/
├── OpenJiuwenAgentRuntimeHandler.java            # 已有：ReActAgent 基类
├── OpenJiuwenWorkflowAgentRuntimeHandler.java    # 新增：Workflow Agent 基类
├── OpenJiuwenStreamAdapter.java                  # 已有：ReActAgent 结果映射
├── OpenJiuwenWorkflowStreamAdapter.java          # 新增：Workflow 结果映射
├── OpenJiuwenTrajectoryRail.java                 # 已有：轨迹观测（需扩展支持 Workflow 事件）
├── OpenJiuwenCheckpointerConfigurer.java         # 已有：Checkpointer 配置（复用）
├── OpenJiuwenRemoteAgentInterruptRail.java       # 已有：远程 Tool 中断（复用）
├── OpenJiuwenRemoteToolInstaller.java            # 已有：远程 Tool 安装（复用）
├── OpenJiuwenMessageAdapter.java                 # 已有：输入转换（复用，需扩展 REMOTE_RESUME）
└── OpenJiuwenMemoryMessageAdapter.java           # 已有：记忆适配（Workflow 不使用）
```

### 3.2 核心类静态关系

```
«interface»                   «abstract»                       «concrete»
AgentRuntimeHandler            AbstractAgentRuntimeHandler
      ↑                              ↑
      │                              ├── extends ──> OpenJiuwenAgentRuntimeHandler  (ReActAgent)
      │                              │                    ↑
      │                              │                    └── user subclass
      │                              │
      │                              └── extends ──> OpenJiuwenWorkflowAgentRuntimeHandler  (Workflow)
      │                                                  ↑
      │                                                  └── user subclass
      │
      └── StreamAdapter
            ├── OpenJiuwenStreamAdapter          (ReActAgent result → AgentExecutionResult)
            └── OpenJiuwenWorkflowStreamAdapter  (WorkflowOutput → AgentExecutionResult)
```

---

## 4. 核心设计

### 4.1 中断捕获机制

Workflow 中断由 QuestionerComponent 在 Workflow 图引擎内部触发。完整链路：

```
QuestionerExecutable.invoke()
  │
  ├─ state.handleEvent(START_EVENT) → START → USER_INTERACT
  ├─ session.interact(questionText)           // NodeSessionApi
  │     └─ WorkflowInteraction.waitUserInputs()
  │           ├─ 写 OutputSchema(type="__interaction__", payload=InteractionOutput)
  │           │     到 graph output stream
  │           └─ throw GraphInterrupt          // 中断信号
  │
  ▼
Vertex.doAtomicInvoke() (line 205)
  ├─ catch RuntimeException → unwrap GraphInterrupt
  ├─ 保存 checkpoint: session.saveCheckpoint(graphState)
  └─ re-throw GraphInterrupt
  │
  ▼
PregelGraph 引擎
  ├─ catch GraphInterrupt → 停止 super-step 迭代
  ├─ graphState 已通过 Checkpointer 持久化
  └─ 组装 WorkflowOutput(state=INPUT_REQUIRED, result=[OutputSchema(interaction)])
  │
  ▼ 返回到 Handler
```

**Handler 层的捕获代码**（`doExecute()` 核心循环）：

```java
@Override
protected Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory) {
    String sessionId = context.agentStateKey();  // 稳定标识
    WorkflowSessionApi session = new WorkflowSessionApi(null, sessionId, Map.of());
    Workflow workflow = createOpenJiuwenWorkflow(context);

    Object nextInputs = Map.of("query", context.userMessage().text());

    while (true) {
        WorkflowOutput output = workflow.invoke(nextInputs, session, null);

        switch (output.getState()) {
            case COMPLETED:
                trajectory.emit(TrajectoryEvent.WORKFLOW_COMPLETED);
                return Stream.of(output);  // StreamAdapter 后续映射为 COMPLETED

            case INPUT_REQUIRED: {
                // 提取 Questioner 产出的 InteractionOutput
                InteractionOutput interaction = extractInteraction(output);
                String nodeId = interaction.getId();      // "confirm"
                String prompt = interaction.getValue().toString();  // "审核摘要: yes/no"

                // 保存恢复上下文到 handler 实例字段
                this.pendingResume = new WorkflowResumeContext(
                    sessionId, nodeId, workflow
                );

                // 构造 runtime 中断载荷
                UserInputInterrupt interrupt = new UserInputInterrupt(
                    prompt,
                    Map.of("nodeId", nodeId, "sessionId", sessionId)
                );

                trajectory.emit(TrajectoryEvent.WORKFLOW_INTERRUPTED, nodeId);
                return Stream.of(interrupt);  // StreamAdapter 映射为 INTERRUPTED
            }

            case ERROR:
                trajectory.emit(TrajectoryEvent.ERROR);
                return Stream.of(output);  // StreamAdapter 映射为 FAILED
        }
    }
}
```

### 4.2 中断 → A2A 协议映射

这是从 Workflow 层中断到对端收到 A2A 中断事件的完整映射链：

```
Handler.doExecute() 返回 INTERRUPTED 信号
  │
  ▼
OpenJiuwenWorkflowStreamAdapter.adapt(rawStream)
  │
  ├─ 检测到 UserInputInterrupt 实例
  └─ return AgentExecutionResult.interrupted(userInputInterrupt)
       │
       ▼
A2aAgentExecutor (agent-runtime A2A 层，已存在)
  │
  ├─ case INTERRUPTED:
  │     if (payload instanceof UserInputInterrupt ui) {
  │         // 更新 Task 状态为 INPUT_REQUIRED
  │         task.transition(INPUT_REQUIRED);
  │         // 保存 resume 路由信息到 Task metadata
  │         task.metadata.put("runtime.waitingTarget", "USER");
  │         task.metadata.put("runtime.interruptNodeId", ui.nodeId());
  │         task.metadata.put("runtime.workflowSessionId", ui.metadata().get("sessionId"));
  │
  │         // 通过 A2A SDK 发送中断事件
  │         emitter.emit(TaskStatusUpdateEvent(
  │             taskId,
  │             TaskState.INPUT_REQUIRED,
  │             message = Message(role=AGENT, parts=[TextPart(ui.prompt())])
  │         ));
  │     }
  │
  ▼
A2A JSON-RPC Transport → SSE → 对端 A2A Client
  │
  └─ 对端收到:
       event: task-status-update
       data: {"taskId":"...", "state":"INPUT_REQUIRED",
              "message":{"parts":[{"text":"审核摘要: yes/no"}]}}
```

**映射不变量**：
- `WorkflowExecutionState.INPUT_REQUIRED` → `AgentExecutionResult.Type.INTERRUPTED` → A2A `TaskState.INPUT_REQUIRED`
- `InteractionOutput.id` → `runtime.interruptNodeId` metadata
- `WorkflowSessionApi.sessionId` → `runtime.workflowSessionId` metadata
- `InteractionOutput.value` → A2A `Message.parts[0].text`（用户看到的提示）

### 4.3 中断恢复 — Runtime 框架必须做的事

恢复时，runtime 的处理分两个阶段：**A2A 层路由**（已有，需扩展）和 **Handler 层执行**（新增）。

#### 阶段 1：A2A 层 — 路由到正确的恢复路径

```
对端 A2A Client 发送用户输入
  │
  ▼ POST /a2a
  {
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "taskId": "<与被中断 Task 相同>",
        "parts": [{"text": "yes"}]
      }
    }
  }
  │
  ▼
A2aAgentExecutor (扩展点)
  │
  ├─ 查找 Task: taskStore.get(message.taskId)
  ├─ 判断 Task 状态:
  │     if (task.state == INPUT_REQUIRED) {
  │         // 读取 resume 路由信息
  │         String waitingTarget = task.metadata.get("runtime.waitingTarget");
  │
  │         if ("USER".equals(waitingTarget)) {
  │             // Workflow 用户输入恢复路径
  │             AgentExecutionContext resumeCtx = AgentExecutionContext.builder()
  │                 .inputType(INPUT_TYPE_REMOTE_RESUME)   // 复用现有常量
  │                 .userMessage(userMessage)
  │                 .taskId(message.taskId)
  │                 .metadata(Map.of(
  │                     "resume.sessionId", task.metadata.get("runtime.workflowSessionId"),
  │                     "resume.nodeId", task.metadata.get("runtime.interruptNodeId"),
  │                     "resume.userInput", message.parts.get(0).text
  │                 ))
  │                 .build();
  │
  │             // 重新进入 handler.execute()
  │             handler.execute(resumeCtx);
  │         }
  │         // else if ("REMOTE_AGENT".equals(waitingTarget)) { ...已有逻辑... }
  │     }
```

#### 阶段 2：Handler 层 — 构造 InteractiveInput 恢复 Workflow

```java
// OpenJiuwenWorkflowAgentRuntimeHandler.execute() 的 resume 分支
@Override
protected Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory) {

    // 判断是否为恢复执行
    if (INPUT_TYPE_REMOTE_RESUME.equals(context.inputType())) {
        // ── 恢复路径 ──
        String sessionId = context.metadata().get("resume.sessionId");
        String nodeId = context.metadata().get("resume.nodeId");
        String userInput = context.metadata().get("resume.userInput");

        WorkflowSessionApi session = new WorkflowSessionApi(null, sessionId, Map.of());
        Workflow workflow = createOpenJiuwenWorkflow(context);

        // 构造 InteractiveInput — nodeId 精确指向被中断的 Questioner 节点
        InteractiveInput resumeInput = new InteractiveInput();
        resumeInput.update(nodeId, Map.of("answer", userInput));

        trajectory.emit(TrajectoryEvent.WORKFLOW_RESUMED, nodeId);

        // 重新进入 invoke 循环
        return resumeLoop(workflow, resumeInput, session, trajectory);

    } else {
        // ── 初次执行路径（见 4.1）──
        ...
    }
}
```

**恢复后 Workflow 引擎的行为**：
1. `workflow.invoke(resumeInput, session, null)` → 图引擎从 Checkpointer 恢复 graphState
2. 找到被中断的 confirm 节点 → `QuestionerState.loadFromSession()` 恢复为 `USER_INTERACT` 状态
3. `NodeSessionApi.interact()` 不再阻塞（已有 `InteractiveInput` 在 session 中）→ 返回用户输入 `"yes"`
4. `QuestionerState.handleEvent(USER_INTERACT_EVENT)` → `END`
5. 图引擎继续执行后续节点 finalize → End

### 4.4 正确性保证

中断恢复的正确性依赖三个不变量的严格维护：

#### 4.4.1 Session ID 稳定性

```
初次执行:  sessionId = context.agentStateKey()    // 由 runtime 根据 tenantId+userId+agentId 生成
           workflow.invoke(inputs, new WorkflowSessionApi(null, sessionId, ...), null)
           → Checkpointer 以 sessionId 为 key 保存 graphState

恢复执行:  sessionId = task.metadata.get("runtime.workflowSessionId")
             // ↑ 从 A2A Task metadata 取回，保证与初次执行一致
           workflow.invoke(resumeInput, new WorkflowSessionApi(null, sessionId, ...), null)
           → Checkpointer 以 sessionId 为 key 查找到 graphState → 恢复
```

**保证手段**：
- 初次执行时，`context.agentStateKey()` 是稳定标识（同一租户/用户/Agent 组合不变）
- A2A 层将 sessionId 写入 Task metadata，随 A2A 协议往返
- 恢复时从 metadata 取回，不经用户或客户端传递（防止篡改）
- 如果 metadata 中 sessionId 缺失或与 Task 不匹配 → 返回 FAILED

#### 4.4.2 Checkpointer 原子性

```
GraphInterrupt 抛出前:
  ├─ session.saveCheckpoint(graphState) 已完成
  └─ 所有节点状态（QuestionerState, LLMExecutableState, ...）已序列化

GraphInterrupt 抛出后:
  ├─ PregelGraph 停止 super-step 迭代
  ├─ 图状态已持久化，即使进程重启也不丢失
  └─ WorkflowOutput 从 graph output stream 收集已产生的 OutputSchema
```

**保证手段**：`Vertex.doAtomicInvoke()` 在 catch GraphInterrupt 后、re-throw 前调用 `session.saveCheckpoint()`。这是 OpenJiuwen 图引擎的原子保证——在中断信号传出之前，状态必定已落盘。

#### 4.4.3 Resume 幂等性

```
第一次 resume: workflow.invoke(interactiveInput, session, null)
  → Checkpointer 恢复 graphState → QuestionerState(USER_INTERACT)
  → 消费 InteractiveInput → QuestionerState(END) → 继续执行 → COMPLETED
  → Checkpointer 保存新的 graphState（已完成）

第二次 resume（错误重试）: workflow.invoke(interactiveInput, session, null)
  → Checkpointer 恢复 graphState → QuestionerState(END) / 已完成
  → 图引擎发现所有节点已完成 → 直接返回 COMPLETED（相同结果）
```

**保证手段**：OpenJiuwen Checkpointer 在每次 `invoke()` 完成后保存最新状态。重复 resume 从同一个已完成状态恢复，产生相同结果。不依赖 Handler 层去重。

#### 4.4.4 不变量验证清单

| 不变量 | 验证点 | 违规处理 |
|--------|--------|---------|
| sessionId 非空 | Handler 初次执行和恢复入口 | 抛出 `IllegalStateException` → FAILED |
| nodeId 匹配 Questioner 节点 | InteractiveInput.update(nodeId, ...) | OpenJiuwen 忽略未注册 nodeId 的 input → 流程卡住，最终超时 |
| taskId 非空 | A2A 层恢复路由 | 无法查找 Task → 返回 `TASK_NOT_FOUND` 错误 |
| context.inputType 正确 | Handler resume 分支入口 | 走错分支 → Workflow 从头执行 → 结果不正确，但不产生副作用 |

### 4.5 邻接 Agent 互调

Workflow Agent 作为被调用方（被其他 Agent 作为远程 Tool 调用）时，完整的调用链：

```
主 Agent (OpenJiuwen ReActAgent)
  │
  ├─ LLM 看到 tool: a2a_remote_workflow_reviewer
  ├─ LLM 调用 tool(reviewer, "请审核这篇文章")
  │
  ▼ OpenJiuwenRemoteAgentInterruptRail 拦截
  ├─ 创建 RemoteAgentInvocation(remoteAgentId="workflow-reviewer")
  └─ → AgentExecutionResult.INTERRUPTED(remoteAgentInterrupt)
  │
  ▼ A2A 层 (主 Agent 侧)
  ├─ A2aRemoteInvocationOrchestrator
  ├─ POST /a2a SendStreamingMessage → workflow-reviewer:8080
  │     message.role = USER
  │     message.parts[0].text = "请审核这篇文章"
  │
  ▼ workflow-reviewer 侧
  ├─ A2A 请求 → AgentExecutionContext
  ├─ OpenJiuwenWorkflowAgentRuntimeHandler.execute(context)
  ├─ Workflow.invoke() → LLM → Tool → LLM → Questioner
  │
  │ 情况 A: 不需要人工输入
  ├─ WorkflowOutput(COMPLETED) → emit COMPLETED(result)
  └─ 远程返回 COMPLETED → 主 Agent 收到 toolResult → 继续推理
  │
  │ 情况 B: 需要人工输入
  ├─ WorkflowOutput(INPUT_REQUIRED)
  ├─ emit INTERRUPTED(UserInputInterrupt("审核摘要: yes/no"))
  │
  └─ 远程返回 INPUT_REQUIRED → 主 Agent 侧父 Task → INPUT_REQUIRED
       │
       用户输入 "yes"
       │
       └─ 主 Agent A2A 层识别 USER → 转发到 workflow-reviewer
            → workflow-reviewer A2A 层识别 REMOTE_RESUME
            → handler.execute(resumeContext) → Workflow 恢复 → COMPLETED
            → 远程返回 COMPLETED → 主 Agent 收到 toolResult → 继续推理
```

**Agent Card 要求**：Workflow Agent 必须声明 skills 才能被邻接 Agent 发现。示例：

```yaml
agent-runtime:
  access:
    a2a:
      agent-card:
        name: workflow-reviewer
        description: 多步骤内容审核 Workflow Agent
        version: "1.0"
        skills:
          - id: review_article
            name: review_article
            description: 审核文章：分析→搜索→摘要→人工确认→输出
```

### 4.6 轨迹观测

Workflow 执行过程产生以下轨迹事件：

| 事件 | 触发时机 | payload |
|------|---------|---------|
| `COMPONENT_START` | 每个 Workflow 节点开始执行 | `{nodeId, componentType}` |
| `COMPONENT_END` | 每个 Workflow 节点执行完成 | `{nodeId, durationMs, outputSize}` |
| `WORKFLOW_INTERRUPTED` | Workflow 挂起等待输入 | `{nodeId, prompt}` |
| `WORKFLOW_RESUMED` | Workflow 恢复执行 | `{nodeId, resumedFromState}` |

实现方案：新增 `OpenJiuwenWorkflowTrajectoryRail`（或扩展现有 `OpenJiuwenTrajectoryRail`），在 Workflow 执行循环中，每步 `invoke()` 返回后发射对应事件。

### 4.7 Checkpointer 共享 — 与 ReActAgent Handler 复用

Workflow Handler 和 ReActAgent Handler 共享同一套 Checkpointer 基础设施。

**为什么可以共享**：
- `OpenJiuwenCheckpointerConfigurer` 是全局静态配置（`static setDefault(Checkpointer)`），设置一次即对所有 OpenJiuwen 执行路径生效
- `Runner.runAgent()` 和 `Workflow.invoke()` 底层都通过 `WorkflowSession` / `NodeSessionApi` 访问 Checkpointer
- Checkpointer 按 `sessionId` 分区——不同 session 的状态互不干扰，Workflow 和 ReActAgent 的 session 天然不同
- Checkpointer 接口是通用的 key-value 状态存储（`save(sessionId, state)` / `load(sessionId)`），不关心存储的是 ReActAgent 的 conversation history 还是 Workflow 的 graphState

**配置方式**（与 ReActAgent 完全一致）：

```java
// 全局配置 — 对所有 OpenJiuwen handler 生效（ReActAgent + Workflow）
OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
// 或 Redis:
// OpenJiuwenCheckpointerConfigurer.setDefault(new RedisCheckpointer(config));
```

**共享范围**：
```
OpenJiuwenCheckpointerConfigurer (全局)
  │
  ├─ ReActAgent Handler
  │     └─ Runner.runAgent() → AgentSession(sessionId) → Checkpointer
  │
  └─ Workflow Handler
        └─ Workflow.invoke() → WorkflowSessionApi(sessionId) → Checkpointer
```

**不需要做的事**：
- 不需要为 Workflow 单独配置 Checkpointer
- 不需要在处理 Workflow 中断时手动调用 `save()` / `load()`——图引擎自动完成
- 不需要担心 Workflow session 和 ReActAgent session 的 key 冲突——`sessionId` 前缀不同（ReActAgent 用 `conversation_id`，Workflow 用 `context.agentStateKey()`）

---

## 5. 配置模型

### 5.1 完整配置示例

```yaml
# Workflow Agent 配置（复用现有配置模式）
sample:
  openjiuwen:
    model-provider: ${LLM_PROVIDER:openai}
    api-key: ${LLM_API_KEY}
    api-base: ${LLM_API_BASE:https://api.openai.com/v1}
    model-name: ${LLM_MODEL:gpt-4}
    ssl-verify: true
    checkpointer: in-memory       # in-memory | redis

# A2A Agent Card（含 skills 声明，供邻接 Agent 发现）
agent-runtime:
  access:
    a2a:
      default-tenant-id: default
      default-agent-id: workflow-reviewer
      agent-card:
        name: workflow-reviewer
        description: 多步骤内容审核 Workflow Agent
        version: "1.0"
        skills:
          - id: review_article
            name: review_article
            description: 审核文章内容（分析→验证→摘要→人工确认→输出）
```

### 5.2 配置属性表

| 属性路径 | 类型 | 默认值 | 必填 | 说明 |
|---------|------|--------|------|------|
| `sample.openjiuwen.model-provider` | String | `openai` | 否 | LLM 提供商 |
| `sample.openjiuwen.api-key` | String | — | 是 | LLM API Key |
| `sample.openjiuwen.api-base` | String | `http://localhost:4000/v1` | 否 | LLM API 地址 |
| `sample.openjiuwen.model-name` | String | `gpt-4` | 否 | 模型名称 |
| `sample.openjiuwen.checkpointer` | String | `in-memory` | 否 | Checkpoint 存储类型 |
| `agent-runtime.access.a2a.agent-card.skills` | List | `[]` | 否 | 声明 skills 列表（邻接 Agent 发现所必需） |

---

## 6. 对外呈现 / 用户场景

### 6.1 外部接口

| API | 说明 |
|-----|------|
| `OpenJiuwenWorkflowAgentRuntimeHandler` SPI | 开发者继承并实现 `createOpenJiuwenWorkflow()` |
| `GET /.well-known/agent-card.json` | A2A 客户端/邻接 Agent 发现 Workflow Agent 能力 |
| `POST /a2a` | A2A JSON-RPC 入口，中断恢复走相同端点 |

### 6.2 用户示例

#### 6.2.1 挂载 Workflow Agent（三步）

```java
// Step 1: 继承 OpenJiuwenWorkflowAgentRuntimeHandler
public class ReviewerHandler extends OpenJiuwenWorkflowAgentRuntimeHandler {
    public ReviewerHandler() { super("workflow-reviewer"); }

    @Override
    protected Workflow createOpenJiuwenWorkflow(AgentExecutionContext ctx) {
        ModelClientConfig clientCfg = ModelClientConfig.builder()
            .clientProvider("openai").apiKey(apiKey).apiBase(apiBase).build();
        ModelRequestConfig reqCfg = ModelRequestConfig.builder()
            .modelName("gpt-4").temperature(0.7).build();

        Workflow wf = new Workflow(WorkflowCard.builder().id("reviewer").build());

        wf.setStartComp("start", new Start(),
            Map.of("query", "${query}"), null);

        wf.addWorkflowComp("analyze", new LLMComponent(llmConfig("分析文章主题")),
            Map.of("article", "${start.query}"), null);

        wf.addWorkflowComp("confirm", new QuestionerComponent(questionerConfig()),
            Map.of("summary", "${analyze.text}"), null);

        wf.setEndComp("end", new End(),
            Map.of("result", "${confirm.user_response}"), null);

        wf.addConnection("start", "analyze");
        wf.addConnection("analyze", "confirm");
        wf.addConnection("confirm", "end");
        return wf;
    }

    // Skills 通过 YAML 配置声明（见第 5 章），不在 Java 代码中定义。
}

// Step 2: 注册为 Spring Bean
@Bean
OpenJiuwenWorkflowAgentRuntimeHandler reviewerHandler() {
    return new ReviewerHandler();
}

// Step 3: 启动
// 预期：runtime 生成 AgentCard（含 skills），暴露 A2A 端点
```

#### 6.2.2 通过 A2A 调用 Workflow Agent

```bash
# 前置条件：workflow-reviewer 已启动在 8080

# 发送审核请求
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "parts": [{"text": "AI医疗影像系统准确率达96.8%，已在三家医院试验"}]
      }
    }
  }'

# 预期结果（中断前）：
# event: artifact-update → chunk: "文章主题：AI医疗影像诊断系统"
# event: task-status-update → state: INPUT_REQUIRED

# 用户确认后继续：
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-002",
        "taskId": "<上一个 taskId>",
        "parts": [{"text": "yes"}]
      }
    }
  }'

# 预期结果：
# event: artifact-update → chunk: "审核通过..."
# event: task-status-update → state: COMPLETED
```

### 6.3 E2E 流程

```
用户                     Runtime (8080)                Workflow 引擎
  │                          │                              │
  │── POST /a2a ────────────>│                              │
  │   "审核这篇文章"          │── AgentExecutionContext ────>│
  │                          │── createOpenJiuwenWorkflow() │
  │                          │── workflow.invoke(inputs) ──>│
  │                          │                              │── [analyze LLM]
  │<── SSE: chunk ──────────│<── OUTPUT ──────────────────│
  │                          │                              │── [search Tool]
  │<── SSE: chunk ──────────│<── OUTPUT ──────────────────│
  │                          │                              │── [summarize LLM]
  │<── SSE: chunk ──────────│<── OUTPUT ──────────────────│
  │                          │                              │── [confirm Questioner]
  │                          │                              │── GraphInterrupt
  │                          │<── INPUT_REQUIRED ──────────│
  │<── SSE: INPUT_REQUIRED ─│                              │
  │   "审核摘要: yes/no"     │  (保存 sessionId)            │
  │                          │                              │
  │── POST /a2a (同 task) ──>│                              │
  │   "yes"                  │── InteractiveInput ─────────>│
  │                          │── workflow.invoke(resume) ──>│
  │                          │                              │── 恢复 confirm 状态
  │                          │                              │── [finalize LLM]
  │                          │                              │── [End]
  │                          │<── COMPLETED ───────────────│
  │<── SSE: COMPLETED ──────│                              │
  │   "审核通过，最终摘要"    │                              │
```

---

## 7. 错误处理

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---------|---------|------|---------|
| Workflow 构建失败 | `createOpenJiuwenWorkflow()` 返回 null 或抛出异常 | 基类发射 ERROR 轨迹事件 | `FAILED("WORKFLOW_CREATION_ERROR")` |
| LLM 调用失败 | 模型 API 不可达 | `LLMExecutable.invoke()` 抛出 `COMPONENT_LLM_INVOKE_CALL_FAILED` | `FAILED("OPENJIUWEN_LLM_ERROR")` |
| Workflow 执行异常 | 非中断的 RuntimeException | Workflow.invoke() 返回 ERROR state 或抛出异常 | `FAILED(errorCode, message)` |
| 中断恢复 sessionId 不匹配 | 续接请求携带错误的 taskId/sessionId | Checkpointer 找不到对应状态，Workflow 从头执行 | 可能丢失之前的执行上下文 |
| Checkpointer 不可用 | Redis 连接断开 | Checkpointer 回退到 in-memory | 进程重启后丢失所有中断状态 |
| 超时 | Workflow 执行超过配置时限 | 同 ReActAgent：cancel 关闭 Stream | `FAILED("TIMEOUT")` |
| Workflow 嵌套中断 | resume 后再次触发 Questioner | 正常的多次中断：第二轮继续 emit INTERRUPTED | A2A 层再次挂起 Task |

---

## 8. 限制与待补

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| 同步执行，cancel 不中断 LLM 调用 | 与 ReActAgent Adapter 相同的限制 | 长时间 LLM 调用场景使用 Versatile Adapter 代理 |
| 不支持 WorkflowAgent（多 Workflow 跳转） | 无法在多个 Workflow 之间动态路由 | 在单个 Workflow 内用 BranchComponent 实现条件分支 |
| 不支持 SubWorkflow 嵌套的深层中断 | 子 Workflow 内的 Questioner 中断可能无法正确传播到父 Workflow | 避免嵌套 Workflow，使用单层 DAG |
| 每次 execute() 重新构建 Workflow | 复杂 DAG 的构建开销 | Workflow DAG 构建通常耗时远小于 LLM 调用，影响可忽略 |
| 仅 OpenJiuwen 支持 Workflow | AgentScope / Versatile 不具备原生 Workflow 能力 | 使用 OpenJiuwen 作为 Workflow Agent 框架 |
