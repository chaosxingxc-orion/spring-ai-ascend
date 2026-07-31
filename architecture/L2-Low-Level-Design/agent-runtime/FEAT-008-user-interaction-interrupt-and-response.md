---
level: L2-LLD
module: agent-runtime
feature_type: functional
feature_id: FEAT-008
status: active
updated: 2026-07-31
dependency:
  - ../../L1-High-Level-Design/agent-runtime/README.md
  - ../../L1-High-Level-Design/agent-runtime/development.md
  - ../../L1-High-Level-Design/agent-runtime/process.md
  - ../../../version-scope/FEAT-008-user-interaction-interrupt-and-response.md
  - ../../../version-scope/FEAT-020-agent-intent-matching-and-action-routing.md
  - ../agent-core/Feat-Func-020-agent-intent-recognition-and-downstream-task-matching.md
  - Feat-Func-004-remote-agent-orchestration.md
---

# 意图驱动工具调用的中断与响应设计文档

## 1. 设计目标与边界

### 1.1 特性范围

FEAT-008 连接 FEAT-020 意图套件和 runtime 已有的 A2A 远端调用、中断响应及续接链。设计目标是：初始化时把当前可调用远端 Agent 的完整 Agent Card、全部 Skill、稳定远端目标和实际 Tool 信息交给意图套件；意图匹配完成后，Agent 调用远端或本地 Tool 发生用户交互中断时，runtime 通过当前服务调用返回 A2A `input-required`，并把客户端后续输入送回同一个 Tool 调用；Workflow 命中远端 Agent 时使用独立适配路径发起远端调用，远端完成后不重新执行 Workflow。

本特性包含：

- 远端 Agent Card 与实际 AgentCore Tool 的统一描述、稳定快照和 FEAT-020 初始化输入；
- Agent 场景已有远端 Tool、本地 Tool 中断及续接链的使用约束；
- Workflow `DELEGATE_AGENT` 结果到 runtime `a2a_delegate` 调用的适配；
- 下游 Tool 续接后返回意图跳变信号时的透明回传；
- runtime-ext 下覆盖完整意图场景的 DeepAgent 与 Workflow 独立 example。

FEAT-020 负责初始化、匹配、结果函数、Agent 提示词和重新匹配；FEAT-004 负责 A2A 远端接入、代理调用和正常结果回填。本特性不判断意图，不执行意图套件内的同步结果函数，也不新增客户端服务入口。

### 1.2 能力对齐矩阵

| 需求能力 | 当前实现状态 | L2 设计响应 |
|---|---|---|
| Agent Card 与稳定远端目标 | 部分具备 | 复用 `A2ARemoteAgentCardRegistry`，新增统一 `RemoteA2aToolDescriptor` 和排序快照。 |
| 单 Card 多 Skill | Card 已完整保存 | 每个 descriptor 保留完整 Card；provider 将全部 Skill 交给 FEAT-020，由 Core 为每条 Skill 建立独立意图项。 |
| 实际远端 Tool 识别 | Tool 已按 registry name 注入 | descriptor 同时提供 `remoteTargetId`、`delegateToolName`、输入字段和 Tool schema，Tool 安装与意图来源共用该对象。 |
| 初始化周期固定 | 当前 Tool 安装按请求增量执行 | 意图启用时使用固定 descriptor snapshot；既有 Agent/Workflow 不读取后续 registry 变化。 |
| Agent 远端 Tool 中断 | 已具备主链 | 复用 `RemoteA2aInterruptRail`、`A2AEnabledServeOrchestrator`、shadow Task 和 `A2AAgentExecutor`。 |
| Agent 本地 Tool 中断 | 已具备主链 | 复用 Core interaction、`JiuwenCoreAgentHandler` 标准化和 `A2AAgentExecutor` 的 `input-required`/resume。 |
| 意图跳变回传 | 已能回传 Tool result | 规定 JSON 信号契约；runtime 不解析、不判断，只把结果交回 Agent ToolMessage 上下文。 |
| Workflow 远端调用 | 远端协调器支持 `resume=false`，缺少 Core Workflow 结果入口 | 新增 `IntentWorkflowResultAdapter` 与专用 Handler，在 Workflow 正常结束后生成 `resume=false` 委派中断。 |
| Workflow 中断续接 | 远端 shadow Task 已支持 | 续接时先恢复远端 Task；完成结果直接成为当前服务结果，不重新运行 Workflow。 |
| 完整 example | 尚无 | 在 runtime-ext 新增多模块 example，DeepAgent 与 Workflow 分别部署并覆盖需求全部路径。 |

### 1.3 当前实现差距

当前代码已经完成用户交互中断的主要运行链，但还不能直接满足 FEAT-008 与 FEAT-020 的组合场景：

- `A2ARemoteAgentCardRegistry#getAll()` 返回并发 Map 的值快照，顺序不稳定；`RemoteA2aToolInstaller` 私有生成 Tool 规格，FEAT-020 无法取得与实际 Tool 完全一致的稳定初始化输入。
- `RemoteA2aToolInstaller` 在每次 `query/streamQuery` 前读取 registry 并增量安装新 Tool，尚无与一次意图套件初始化周期绑定的固定 Tool 集合。
- `A2AAgentCardDiscovery` 在 `ApplicationReadyEvent` 执行，缺少可由意图初始化显式调用且不会重复请求成功目标或重复启动 retry 的同步初次发现入口。
- Agent Tool 路径已经产生带 `toolCallId` 的 `a2a_delegate` 中断并按 `resume=true` 恢复 Agent；不需要新增远端调用协议。
- 远端协调器已经支持 `context.resume=false`，但 Core Workflow 的正常 `IntentResult` 尚不会转换成协调器可识别的中断，因此无法从 FEAT-020 Workflow 组件进入该路径。
- A2A executor 已把本地或远端等待输入状态返回为 A2A `input-required` 并保存中断信息；本特性只需补齐意图场景的关联与验收，不另建中断模型。

## 2. Runtime 交互契约设计

### 2.1 远端能力描述与快照契约

每个可被 runtime 调用且至少声明一条 Skill 的远端 Agent 生成一个 `RemoteA2aToolDescriptor`。descriptor 是远端 AgentCore Tool 安装和 FEAT-020 Agent Card 初始化的共同事实源，必须包含：

| 字段 | 来源 | 语义 |
|---|---|---|
| `remoteTargetId` | `RemoteAgentEntry.name` | runtime registry、远端 caller 和 shadow Task 使用的稳定目标标识。 |
| `delegateToolName` | 当前固定为 `RemoteAgentEntry.name` | LLM 实际可调用的 AgentCore Tool 名；不得由 Core 再次推导。 |
| `delegateInputName` | 当前固定为 `remoteInput` | 远端 Tool 接收请求文本的字段名。 |
| `toolDescription` | Agent Card Skill 描述，缺失时回退 Card 描述 | Agent 可见 Tool 描述。 |
| `inputSchema` | runtime 远端 Tool JSON schema | 与 `RemoteA2aInterruptRail` ToolCard 完全一致。 |
| `agentCard` | registry entry | 完整 A2A Agent Card，保留全部 Skill。 |
| `timeoutSeconds/isStreaming` | registry entry | 远端调用配置；不进入意图匹配文本。 |

快照按 `remoteTargetId`、`delegateToolName` 稳定排序，返回不可变 List。空 Card、空 Skill、空目标名或无法形成合法 Tool 的 entry 不进入快照；如果它属于初始化请求声明的必需目标，则初始化失败，不能静默生成不可执行意图项。

同一 Card 只生成一个 descriptor 和一个远端 Tool，但 provider 把完整 Card 交给 FEAT-020，Core 默认 initializer 为其中每条 Skill 生成独立 `IntentItem`。各 Skill 命中后共享该 Card 的 `remoteTargetId`、`delegateToolName` 和 `delegateInputName`。

### 2.2 意图初始化衔接契约

`IntentRuntimeBootstrap` 只在 Agent 或 Workflow 初始化阶段执行：

1. 触发一次已配置远端 Card 初次发现，同一目标只执行一次；
2. 从 registry 建立稳定 descriptor snapshot；
3. 校验调用方声明的必需远端目标均存在；
4. 将 descriptor 转换为 FEAT-020 `AgentCardIntentSource`；
5. 使用调用方提供的三个 SPI 和初始化 `kwargs` 创建 `IntentSuite`；
6. 返回同一次快照对应的 suite 与 descriptors。

Agent 调用方随后使用 descriptors 固定安装远端 Tool，再用 FEAT-020 `IntentAgentBinder` 绑定意图 Tool 和提示词。Workflow 调用方用 suite 创建独立 `IntentMatchingComponent`，并用专用 Workflow Handler 暴露服务。

bootstrap 不在 Agent loop 或 Workflow 节点执行时重新初始化。registry 后续因 retry 或配置变化出现新 Card 时，已创建的 suite、Agent Tool 集合和 Workflow 组件保持不变；应用必须创建新的 Agent/Workflow、suite 和 handler 才能使用新描述。

初始化 `kwargs` 只传给 FEAT-020 `IntentInitializationRequest`，不读取或合并 `ServeRequest.metadata`、Agent Tool invocation `kwargs` 或用户续接输入。

### 2.3 Agent Tool 中断契约

Agent Card Skill 命中后，FEAT-020 返回：

```json
{
  "type": "intent_result",
  "status": "MATCHED",
  "action": {
    "type": "DELEGATE_AGENT",
    "target": "travel-agent",
    "data": {
      "remoteTargetId": "travel-agent",
      "delegateToolName": "travel-agent",
      "delegateArguments": {
        "remoteInput": "预订明天去深圳的机票"
      }
    }
  }
}
```

LLM 按 FEAT-020 提示词调用 `action.target`，并原样使用 `delegateArguments`。`RemoteA2aInterruptRail` 将该 Tool call 转换为 Core Tool interaction：

```json
{
  "type": "__interaction__",
  "toolCallId": "<LLM tool call id>",
  "toolName": "travel-agent",
  "message": "预订明天去深圳的机票",
  "context": {
    "_interrupt_kind": "a2a_delegate",
    "agentName": "travel-agent"
  }
}
```

Agent 路径不设置 `resume=false`，`RemoteInvocationBatchMapper` 按默认值将其解释为 `resume=true`。远端正常完成后，协调器以 `runtime.remoteToolResults[toolCallId]` 构造 `ServeRequest`，`JiuwenCoreAgentHandler` 转为 `InteractiveInput`，同一个 Tool call 取得远端结果并回到 LLM。

远端返回 `INPUT_REQUIRED` 时，协调器保存 shadow Task 和远端 taskId，向上返回 `QueryChunk.TYPE_INTERRUPT`。`A2AAgentExecutor` 将其转换为 A2A `input-required`，当前调用结束但 Task 保持可续接状态。

### 2.4 本地 Tool 中断契约

意图结果为 `CALL_TOOL`，或 LLM 在后续 loop 选择其他本地 Tool 时，Tool 可以通过 AgentCore interaction 请求用户输入。`JiuwenCoreAgentHandler` 将 `InteractionOutput` 标准化为 `__interaction__`，非 `a2a_delegate` 中断由 `A2AEnabledServeOrchestrator` 原样返回客户端；`A2AAgentExecutor` 负责生成 A2A `input-required` 并把完整 `_interrupt` 保存到 Task status message metadata。

客户端使用同一个 A2A taskId 提交输入后，executor 只在 Task 当前状态为 `INPUT_REQUIRED` 时恢复保存的 `_interrupt`。本需求一次意图只选择一个下游目标，AgentCore 依据该目标的 `toolCallId` 把本次请求最新用户输入交回等待中的 Tool call；错误 taskId 不得恢复其他 Tool。

FEAT-020 意图结果函数是同步不可中断回调，不进入本节链路。只有意图 Tool 返回后由 LLM 调用的下游本地 Tool 可以使用 runtime 中断与续接能力。

### 2.5 Workflow 远端委派契约

Workflow 意图组件只输出结构化 `IntentResult` 并交给后续节点，不注入提示词。Workflow 图正常执行完毕后，如果服务终态是 `MATCHED` 或 `FALLBACK` 且 `action.type=DELEGATE_AGENT`，`IntentWorkflowResultAdapter` 校验：

- `action.target` 与 snapshot 中实际 `delegateToolName` 一致；
- `action.data.remoteTargetId` 存在于同一 snapshot；
- `delegateArguments` 包含 descriptor 指定的 `delegateInputName`，且值为字符串；
- 结果能由 FEAT-020 `IntentResultCodec` 完整解析。

校验通过后生成协调器已有格式：

```json
{
  "type": "__interaction__",
  "toolCallId": "<runtime generated unique id>",
  "toolName": "travel-agent",
  "message": "预订明天去深圳的机票",
  "context": {
    "_interrupt_kind": "a2a_delegate",
    "agentName": "travel-agent",
    "resume": false
  }
}
```

该转换发生在 Workflow 已正常完成之后，不创建等待恢复的 Core Workflow checkpoint。`A2AEnabledServeOrchestrator` 调用远端 Agent：

- 远端正常完成：删除 shadow，远端结果直接成为本层服务结果，不再次执行 Workflow 或意图组件；
- 远端需要输入：保存 shadow 并返回 A2A `input-required`；客户端续接时直接续接远端 taskId；
- 续接后完成：结果直接返回客户端，仍不重新执行 Workflow；
- 远端失败：按现有远端调用失败结果返回，不回到意图组件选择其他目标或 fallback。

非 `DELEGATE_AGENT`、`UNMATCHED`、`FAILED` 或格式非法的结果不生成远端中断。格式非法的 `DELEGATE_AGENT` 返回明确适配失败，不能把不可信目标交给远端协调器。

### 2.6 意图跳变回传契约

意图跳变只发生在意图匹配已经结束后的下游 Tool 中断续接阶段。远端 Agent Tool 或本地 Tool 可以在取得用户补充输入后结束原调用并返回：

```json
{
  "signal": "intent_shift",
  "latestIntent": "改为查询明天去上海的高铁",
  "message": "用户已切换任务"
}
```

runtime 将该值作为普通 Tool 结果交回 Agent，不解析 `latestIntent`，不自行调用意图套件，也不把普通失败或普通完成结果改写为跳变。AgentCore 将结果写入 ToolMessage 上下文，LLM 按 FEAT-020 生效提示词再次调用 `intent_match(latestIntent)`。

下游 Tool 正常完成时不存在该信号，LLM完成本轮答复，不重新匹配。Workflow 不使用 Agent 提示词；无论远端完成、失败还是中断续接，runtime 都不自动执行 Workflow 意图组件。

## 3. 核心运行模型

### 3.1 初始化远端意图来源

```text
Agent/Workflow 应用初始化
  -> A2AAgentCardDiscovery.discoverConfiguredAgents()（同步初次尝试、每个目标一次）
  -> A2ARemoteAgentCardRegistry
  -> RemoteA2aToolDescriptorFactory.snapshot（稳定排序）
  -> RemoteAgentIntentSourceProvider.toSources
       每个 descriptor 保留完整 Agent Card
       FEAT-020 默认 initializer 再按每条 Skill 建立 IntentItem
  -> IntentRuntimeBootstrap 创建 IntentSuite 和固定 descriptor snapshot
  -> Agent: installSnapshot + IntentAgentBinder
     Workflow: IntentMatchingComponent + IntentWorkflowAgentHandler
```

发现失败且目标被声明为必需时，初始化失败，服务不得以“有意图项但无实际 Tool”的状态启动。后续 registry 变化不影响当前实例。

### 3.2 Agent 远端 Tool 正常完成

```text
用户输入 -> LLM -> intent_match -> DELEGATE_AGENT
  -> LLM 调用实际远端 Tool(delegateArguments)
  -> RemoteA2aInterruptRail 产生 a2a_delegate + toolCallId
  -> RemoteInvocationBatchCoordinator 调用远端 Agent
  -> 远端 COMPLETED
  -> runtime.remoteToolResults[toolCallId]
  -> AgentCore 恢复同一 Tool call
  -> ToolMessage -> LLM 答复 -> 本轮结束
```

正常完成路径只执行一次意图匹配，不因为远端 Tool 返回而再次匹配。

### 3.3 Agent 远端 Tool 需要用户输入

```text
远端返回 INPUT_REQUIRED
  -> 保存 shadow(parent taskId, remote taskId, toolCallId, agentName, resume=true)
  -> A2AAgentExecutor 返回 input-required
  -> 客户端携带父 taskId 提交用户输入
  -> coordinator 找到 shadow，使用 remote taskId 续接远端 Agent
     -> 再次 INPUT_REQUIRED: 更新 shadow，再次返回 input-required
     -> COMPLETED: 写 runtime.remoteToolResults，恢复 Agent Tool call
     -> FAILED: 写结构化失败 Tool result，恢复 Agent Tool call
```

### 3.4 Agent 本地 Tool 中断后正常完成或意图跳变

```text
LLM 调用本地 Tool
  -> AgentCore interaction -> runtime 返回 input-required
  -> 客户端续接 -> 同一 Tool call 取得输入
     -> 普通结果: ToolMessage -> LLM 答复 -> 本轮结束
     -> IntentShiftSignal: ToolMessage 携带 latestIntent
          -> LLM 再次调用 intent_match(latestIntent)
          -> 新的意图处理循环
```

远端 Tool 在续接后返回 `IntentShiftSignal` 时使用同一后半段流程。runtime 只保证信号作为 Tool result 回到 Agent。

### 3.5 Workflow 远端调用与续接

```text
Workflow 输入 -> IntentMatchingComponent -> 显式 Workflow 分支
  -> Workflow 正常终态 IntentResult(DELEGATE_AGENT)
  -> IntentWorkflowResultAdapter -> a2a_delegate(resume=false)
  -> coordinator 调用远端 Agent
     -> COMPLETED: 远端结果直接返回客户端
     -> INPUT_REQUIRED: shadow + A2A input-required
          -> 客户端续接远端 taskId
          -> 完成结果直接返回客户端
```

Workflow 不进入 LLM Agent loop，不注入 FEAT-020 提示词，也不在远端结果返回后自动重新匹配。

## 4. 代码结构与类级设计

### 4.1 仓库与模块归属

`agent-runtime-java` 只增强已有 Card 发现生命周期：

```text
service/agent-service-app/
└── .../controller/a2a/client/A2AAgentCardDiscovery.java
```

新增 `discoverConfiguredAgents()`：对每个配置目标执行一次同步初次发现，成功即写 registry，失败沿用现有 retry；同一实例重复调用不重复发现成功目标，也不为失败目标重复创建 retry。现有 `ApplicationReadyEvent` 入口改为调用该方法，保持无意图套件应用的行为不变。

其余新增实现位于 `agent-solution/common/agent-runtime-ext-java/agent-service-adapters/agent-service-adapters-agentcore-ext`：

```text
com.openjiuwen.service.adapters.agentcore.ext
├── autoconfigure
│   └── AgentCoreExtAutoConfiguration.java        # 装配 factory/provider/bootstrap
├── external
│   ├── RemoteA2aToolDescriptor.java
│   ├── RemoteA2aToolDescriptorFactory.java
│   ├── RemoteA2aToolInstaller.java              # 增加固定快照安装
│   └── RemoteA2aInterruptRail.java              # 改为消费 descriptor
├── intent
│   ├── RemoteAgentIntentSourceProvider.java
│   ├── IntentRuntimeBootstrap.java
│   ├── IntentRuntimeInitialization.java
│   ├── IntentRuntimeState.java
│   └── IntentWorkflowResultAdapter.java
└── agentfw
    └── IntentWorkflowAgentHandler.java
```

该 runtime-ext 模块直接依赖 `com.openjiuwen:agent-intent:0.1.0`，并在 `agent-runtime-ext-java/pom.xml` 的 dependency management 中统一版本。依赖方向保持为 `runtime-ext -> agent-intent -> agent-core-java`，`agent-intent` 不依赖 runtime。solution 构建流水线先安装 `common/agent-core-ext-java`，再构建 `common/agent-runtime-ext-java`；不能依赖开发机 Maven 缓存中未知版本的 `agent-intent`。

`AgentCoreExtAutoConfiguration` 在相关类型存在且用户未自定义 Bean 时装配 descriptor factory、source provider、bootstrap 和 Workflow result adapter；它不自动创建 `IntentSuite` 或替换用户的 `AgentHandler`，因为初始化 `kwargs`、三个 SPI、必需远端目标以及 Agent/Workflow 类型必须由应用明确选择。

### 4.2 Descriptor 与 factory

```java
public record RemoteA2aToolDescriptor(
        String remoteTargetId,
        String delegateToolName,
        String delegateInputName,
        String toolDescription,
        Map<String, Object> inputSchema,
        org.a2aproject.sdk.spec.AgentCard agentCard,
        int timeoutSeconds,
        boolean streaming) {
}

public final class RemoteA2aToolDescriptorFactory {
    public Optional<RemoteA2aToolDescriptor> create(
            A2ARemoteAgentCardRegistry.RemoteAgentEntry entry);

    public List<RemoteA2aToolDescriptor> snapshot(
            A2ARemoteAgentCardRegistry registry);
}
```

factory 是 Tool 名、目标 ID、`remoteInput` schema 和 Tool 描述的唯一生成点。`RemoteA2aToolInstaller`、`RemoteA2aInterruptRail` 与 `RemoteAgentIntentSourceProvider` 不再各自拼装这些字段。

### 4.3 Source provider 与 bootstrap

```java
public final class RemoteAgentIntentSourceProvider {
    public List<AgentCardIntentSource> toSources(
            List<RemoteA2aToolDescriptor> descriptors);
}

public record IntentRuntimeInitialization(
        Set<String> requiredRemoteTargetIds,
        Map<String, Object> initializationKwargs,
        IntentInitializer initializer,
        IntentMatcher matcher,
        IntentResultGenerator resultGenerator) {
}

public record IntentRuntimeState(
        IntentSuite suite,
        List<RemoteA2aToolDescriptor> descriptors) {
}

public final class IntentRuntimeBootstrap {
    public IntentRuntimeState initialize(IntentRuntimeInitialization request);
}
```

`initialize` 调用一次初次发现、建立一次 descriptor snapshot、校验 required target，再创建 suite。返回对象中的 List、Card source context 和初始化 `kwargs` 均按 FEAT-020 约束保存为只读结构。bootstrap 不暴露 refresh、replace 或 request-time initialize API。

### 4.4 远端 Agent Tool 固定安装

`RemoteA2aToolInstaller` 保留现有非意图应用的增量 `install(Object agent)`，并新增：

```java
public void installSnapshot(
        Object agent,
        List<RemoteA2aToolDescriptor> descriptors);
```

`installSnapshot` 为目标 Agent 记录固定 descriptor 集合并一次性安装对应 `RemoteA2aInterruptRail`。之后 `JiuwenCoreAgentExtHandler` 每次请求调用原 `install(agent)` 时，只确认固定集合已安装，不读取 registry 新增项。对同一 Agent 再绑定不同快照直接失败；更新意图描述时创建新 Agent 和 suite。

ReAct Agent 直接注册 rail。DeepAgent 使用公开 `deepAgent.getAgent()` 取得内部 ReAct Agent；不使用反射。ToolCard 的 `id/name/description/inputParams` 均来自 descriptor。

### 4.5 Workflow 结果适配与 Handler

```java
public final class IntentWorkflowResultAdapter {
    public Optional<Map<String, Object>> toDelegateInterrupt(
            Object workflowResult,
            List<RemoteA2aToolDescriptor> descriptors);
}

public final class IntentWorkflowAgentHandler extends JiuwenCoreAgentHandler {
    public IntentWorkflowAgentHandler(
            Object workflow,
            IntentWorkflowResultAdapter adapter,
            List<RemoteA2aToolDescriptor> descriptors);
}
```

Handler 与 Agent 的 `JiuwenCoreAgentExtHandler` 分离：

- `query` 路径在 `WorkflowOutput` 转为普通 `QueryResponse` 前识别终态 `IntentResult`；命中远端委派时返回包含 `_interrupt` 的结果。
- `streamQuery` 路径只缓存终态 `intent_result` chunk，待底层 Workflow 正常 `onComplete` 后输出一个 `QueryChunk.TYPE_INTERRUPT`；内部 IntentResult 不作为业务 chunk 发给客户端。
- 其他 Workflow 输出沿用 `JiuwenCoreAgentHandler` 的正常映射。
- adapter 生成的 `toolCallId` 每次委派唯一，`context.resume=false` 固定存在。
- adapter 只接受同一 descriptor snapshot 中的目标和 Tool，不按字符串直接调用 registry 中其他目标。

### 4.6 复用的现有 Runtime 类

| 类 | 本特性使用方式 | 是否修改 |
|---|---|---|
| `A2ARemoteAgentCardRegistry` | 保存发现后的 Card、稳定注册名和调用配置。 | 否 |
| `A2ARemoteAgentClient` | 按 `agentName`、remote taskId 发起或续接 A2A 调用。 | 否 |
| `JiuwenCoreAgentHandler` | 标准化 Core interaction；把 `runtime.remoteToolResults` 转为 `InteractiveInput`。 | 否 |
| `JiuwenCoreAgentExtHandler` | Agent 场景绑定固定远端 Tool 与 request-scoped client Tool rail。 | 适配 installer 调用语义 |
| `RemoteInvocationBatchMapper` | 读取 `toolCallId/toolName/context.agentName/context.resume`，形成 batch。 | 否 |
| `RemoteInvocationBatchCoordinator` | 保存 shadow、远端 taskId、等待状态和结果；区分 `resume=true/false`。 | 否 |
| `A2AEnabledServeOrchestrator` | 截获 `a2a_delegate`；Agent 恢复 Core，Workflow 直接返回远端结果。 | 否 |
| `A2AAgentExecutor` | A2A `input-required` 输出、Task 中断信息保存与 resume 恢复。 | 否 |

## 5. 关键数据对象与映射设计

### 5.1 Registry 到 FEAT-020 初始化输入

| Runtime 数据 | FEAT-020 字段 | 规则 |
|---|---|---|
| `RemoteAgentEntry.name` | `remoteTargetId` | 非空、稳定；远端 caller 查找键。 |
| descriptor Tool name | `delegateToolName` | 与实际 ToolCard name 完全相同。 |
| descriptor input name | `delegateInputName` | 当前为 `remoteInput`。 |
| `RemoteAgentEntry.card` | `agentCard` | 完整保留全部 Skill。 |
| timeout/streaming/descriptor | `context` | 只作为初始化来源信息，不加入 Agent loop kwargs。 |

FEAT-020 为每条 Skill 建立 item 后，返回结果中的 `remoteTargetId`、`delegateToolName` 和 `delegateArguments` 必须能反查到同一个 descriptor。

### 5.2 Agent Tool 调用到远端 batch

| 来源 | 中断字段 | 后续用途 |
|---|---|---|
| LLM Tool call id | `toolCallId` | shadow member、远端结果和 Core Tool 恢复的关联键。 |
| `action.target` / ToolCard name | `toolName` | AgentCore Tool 身份。 |
| `delegateArguments.remoteInput` | `message` | 发给远端 Agent 的 A2A user TextPart。 |
| descriptor target | `context.agentName` | `A2ARemoteAgentClient` registry 查找键。 |
| Agent Tool 路径 | `context.resume` 缺省 | mapper 解释为 `true`，远端完成后恢复 Agent。 |

### 5.3 Workflow IntentResult 到远端 batch

| IntentResult | 中断字段 |
|---|---|
| runtime 新建 UUID | `toolCallId` |
| `action.target` | `toolName` |
| `action.data.delegateArguments[delegateInputName]` | `message` |
| `action.data.remoteTargetId` | `context.agentName` |
| 固定值 | `context._interrupt_kind=a2a_delegate`、`context.resume=false` |

Workflow adapter 不使用 Agent Card 的 URL 直接调用远端；所有调用仍经 registry 和 `RemoteAgentCaller`，避免绕开 timeout、streaming、callback 和 shadow Task 语义。

### 5.4 Shadow Task 与客户端续接

shadow snapshot 至少保留 `parentTaskId`、`batchId`、`resume`、`toolCallId`、`toolName`、`agentName`、`remoteTaskId`、成员状态、等待提示和结果。状态行为：

| 远端状态 | shadow | 客户端/上游行为 |
|---|---|---|
| `INPUT_REQUIRED` / `AUTH_REQUIRED` | 保存 `WAITING_INPUT` | 返回 A2A `input-required`。 |
| 再次等待输入 | 更新同一成员和 remote taskId | 再次返回 `input-required`。 |
| `COMPLETED`, `resume=true` | 保存 `READY_TO_RESUME` 直到 Core 恢复完成 | 结果写入 `runtime.remoteToolResults[toolCallId]`。 |
| `COMPLETED`, `resume=false` | 删除 shadow | 远端结果直接成为服务结果。 |
| 失败 | 形成现有远端调用失败结果 | Agent 路径恢复 LLM；Workflow 路径沿用 FEAT-004 失败表面，且不重新匹配。 |

客户端续接输入的关联映射为：A2A message `taskId` -> `runtime.parentTaskId`，普通 `TextPart` -> `ServeRequest.lastUserQuery()`；远端 Tool 由 shadow 中保存的 `toolCallId/remoteTaskId` 关联，本地 Tool 由 A2A Task 中恢复的 `_interrupt.toolCallId` 关联。

### 5.5 意图跳变数据

`IntentShiftSignal` 作为 Tool 业务结果，不存入 `_interrupt`，也不改变 shadow 结构。远端 Tool 路径中它位于远端最终 TextPart；本地 Tool 路径中它是被续接 Tool 的结果。两条路径最终都进入 Agent ToolMessage。runtime 不读取 `signal` 或 `latestIntent`。

## 6. Runtime-ext Example 设计

### 6.1 目录与模块

example 放在：

```text
common/agent-runtime-ext-java/example/intent-routing-a2a/
├── pom.xml
├── demo-support/                    # 固定 Reranker、Scripted Model、共享请求与断言
├── remote-agent-runtime/            # 按 travel/finance profile 启动两个远端 runtime
├── deepagent-intent-runtime/        # DeepAgent 调用方，独立 Spring Boot 应用
├── workflow-intent-runtime/         # Workflow 调用方，独立 Spring Boot 应用
├── smoke-intent-a2a.sh
├── smoke-intent-a2a.ps1
└── README.md
```

`agent-runtime-ext-java/pom.xml` 在 `examples` profile 中加入该 example，默认库构建不启动或打包示例应用。两个调用方应用不能放入同一个 Spring context，避免多个 `AgentHandler` bean 和不同初始化生命周期互相影响。

`remote-agent-runtime` 使用两个 profile 启动两个端口：

| profile | Agent Card | Skills | 中断能力 |
|---|---|---|---|
| `travel` | `travel-agent` | `book-flight`、`book-hotel` | 信息不足时 `input-required`；续接可正常完成或返回 `IntentShiftSignal`。 |
| `finance` | `finance-agent` | `query-expense` | 正常完成，用于多 Card 匹配和不重新匹配验证。 |

### 6.2 DeepAgent 应用

`deepagent-intent-runtime` 初始化顺序固定为：发现两个远端 Card，创建 runtime snapshot 和 suite，创建 DeepAgent，按 snapshot 安装远端 Tool，调用 `IntentAgentBinder.bind(deepAgent, ...)`，最后创建 `JiuwenCoreAgentExtHandler`。

意图目录包含：

- 两个远端 Agent Card，其中 travel Card 有两条 Skill；
- 自定义项 `local-weather`，同步结果函数返回 `CALL_TOOL(weather_tool)`；
- 自定义项 `service-hours`，同步结果函数返回 `DIRECT_RESPONSE`；
- 可切换的 fallback 配置；
- 本地 `weather_tool` 和一个会产生用户交互中断的 `changeable-order-tool`。

必须覆盖以下独立场景：

| 场景 | 预期 Tool/结果序列 |
|---|---|
| 多 Card 匹配 | `intent_match -> finance-agent`，返回后 LLM 答复。 |
| 单 Card 多 Skill | flight/hotel 分别命中独立 Skill，但都调用 `travel-agent`。 |
| 自定义本地 Tool | `intent_match -> CALL_TOOL -> weather_tool`；结果函数本身不产生中断。 |
| 自定义直接答复 | `intent_match -> DIRECT_RESPONSE`，不调用下游 Tool。 |
| fallback | 低于阈值后执行 fallback 结果函数一次。 |
| 无 fallback | 返回 `UNMATCHED`，不执行结果函数或其他 Tool。 |
| 远端补充原任务信息 | `travel-agent -> input-required -> resume -> completed`；不再次调用 `intent_match`。 |
| 本地补充原任务信息 | 本地 Tool 中断续接后普通完成；不再次匹配。 |
| 远端意图跳变 | 远端中断续接后返回信号；LLM 使用 `latestIntent` 第二次调用 `intent_match`。 |
| 本地意图跳变 | 本地 Tool 中断续接后返回信号；使用相同重新匹配流程。 |
| 非法跳变信号 | 缺少 `latestIntent` 或普通失败；不得重新匹配。 |

默认运行使用可编程 Model 和固定分数 Reranker，确保 Tool call 序列可断言；README 另提供真实 LLM/Reranker 环境变量作为可选 smoke，不把外部模型稳定性作为自动验收前提。

### 6.3 Workflow 应用

`workflow-intent-runtime` 使用独立 Workflow 图：

```text
Start -> IntentMatchingComponent -> RouteByIntentResult
  -> MATCHED/FALLBACK + DELEGATE_AGENT -> terminal IntentResult -> runtime adapter
  -> MATCHED/FALLBACK + CALL_TOOL -> LocalBusinessNode -> End
  -> MATCHED/FALLBACK + DIRECT_RESPONSE/CUSTOM -> BusinessResponseNode -> End
  -> UNMATCHED -> UnmatchedResponseNode -> End
  -> FAILED -> ErrorNode -> End
```

该应用不创建 `IntentMatchingTool`，不调用 `IntentAgentBinder`，不配置意图提示词。必须覆盖：

| 场景 | 预期行为 |
|---|---|
| Agent Card Skill | 终态 `DELEGATE_AGENT` 被适配为 `resume=false`，远端结果直接返回。 |
| 自定义结果函数 | 在意图组件内同步执行，结果由下一个 Workflow 节点处理。 |
| fallback / 无 fallback | 分别执行 fallback 或把 `UNMATCHED` 交给响应节点。 |
| 远端需要输入 | 返回 A2A `input-required`；客户端续接 remote task；完成后不重跑 Workflow。 |
| 普通远端完成 | Workflow 和意图组件调用计数均为 1。 |
| 续接内容表达新意图 | Workflow 仍只续接既有远端调用，不注入提示词、不自动重新匹配。 |

### 6.4 Smoke 脚本与断言

脚本启动 travel、finance、DeepAgent caller 和 Workflow caller 四个进程，轮询各 Agent Card endpoint 就绪后发送 A2A `SendMessage`/`SendStreamingMessage`。每个中断场景从首轮响应读取父 taskId，后续请求显式携带该 taskId。

脚本至少断言：A2A Task 状态序列、`toolCallId` 稳定关联、选中的 remote target、远端 taskId 续接、Agent intent Tool 调用次数、Workflow 节点调用次数、fallback 函数调用次数，以及正常完成/意图跳变的不同循环次数。脚本退出时清理四个进程，不要求 Redis。

## 7. 集成测试与验收设计

### 7.1 单元与模块集成测试

| 测试组 | 覆盖重点 |
|---|---|
| `A2AAgentCardDiscoveryTest` | 显式初次发现与 ApplicationReady 共用入口；成功不重复请求，失败不重复创建 retry。 |
| `RemoteA2aToolDescriptorFactoryTest` | 空 Card/Skill/名称过滤、描述回退、固定 `remoteInput` schema、稳定排序。 |
| `RemoteAgentIntentSourceProviderTest` | 完整 Card、多 Skill、target/tool/input name 和 context 无损映射。 |
| `IntentRuntimeBootstrapTest` | 只初始化一次、必需 target 缺失失败、kwargs 原样进入 Core、registry 后续变化不影响 state。 |
| `RemoteA2aToolInstallerSnapshotTest` | Tool 与 source 使用同一 descriptor；DeepAgent 公开 API；绑定后不增量加入新 registry entry。 |
| `IntentWorkflowResultAdapterTest` | MATCHED/FALLBACK 委派、resume=false、唯一 toolCallId、未知目标/Tool/参数/格式拒绝，其他 action 不适配。 |
| `IntentWorkflowAgentHandlerTest` | query 与 stream 都只在 Workflow 正常完成后生成中断；内部 IntentResult 不外泄；其他结果保持原行为。 |

### 7.2 Agent 中断链集成测试

| 场景 | 验收标准 |
|---|---|
| 远端正常完成 | descriptor Tool 命中正确 target；远端结果按 toolCallId 恢复 Agent；LLM 不重新匹配。 |
| 远端单次/多次 `input-required` | 每次返回 A2A `input-required`；shadow 保存 remote taskId；续接使用同一远端 Task。 |
| 本地 Tool `input-required` | 完整 `_interrupt` 存入 A2A Task；客户端输入恢复同一 Tool call。 |
| 远端/本地正常续接 | 普通 Tool result 回到 LLM，本轮结束，意图 Tool 调用总数仍为 1。 |
| 远端/本地意图跳变 | JSON 信号与最新意图回到 ToolMessage；LLM 第二次调用意图 Tool，runtime 不直接调用 matcher。 |

### 7.3 Workflow 中断链集成测试

| 场景 | 验收标准 |
|---|---|
| `resume=false` 正常完成 | Workflow 只执行一次；远端结果直接成为服务结果；不生成 `runtime.remoteToolResults` Core 恢复请求。 |
| `resume=false` 等待输入 | 返回 A2A `input-required`，shadow 的 `resume=false` 和 remote taskId 被保存。 |
| Workflow 续接完成 | coordinator 在进入 Handler 前续接远端；删除 shadow；Workflow/意图组件调用计数不增加。 |
| Workflow 新意图输入 | 仍按当前 remote taskId 续接，不执行 Agent 提示词或自动重匹配。 |
| 委派格式非法 | 明确失败且不调用 `RemoteAgentCaller`。 |

### 7.4 跨特性验收

完整验收必须同时证明：

1. FEAT-008 descriptor snapshot 中的每个 Card/Skill 与 FEAT-020 `IntentItem`、实际远端 Tool 和 runtime registry target 一一对应；
2. Agent Card、自定义项和 fallback 都先经过 FEAT-020 matcher -> result generator，runtime 不越过该主链重新选择目标；
3. Agent 远端调用使用 `resume=true` 恢复 Tool loop，Workflow 使用 `resume=false` 直接返回远端结果；
4. 远端和本地 Tool 的 `input-required` 都通过当前 A2A 服务调用返回并按 `toolCallId` 正确续接；
5. 只有下游 Tool 在中断续接后返回完整 `IntentShiftSignal` 时，Agent 才由 LLM 使用最新意图重新调用意图 Tool；普通完成不重新匹配；
6. Workflow 不注入提示词，也不在远端完成或续接后自动执行意图组件；
7. registry 或 Agent Card 在初始化后变化不会修改已创建 suite 和已绑定 Tool 集合；重新创建 Agent/Workflow 和 suite 后新内容才生效；
8. DeepAgent 与 Workflow 两套独立 example 均通过确定性集成测试和 A2A smoke 脚本。
