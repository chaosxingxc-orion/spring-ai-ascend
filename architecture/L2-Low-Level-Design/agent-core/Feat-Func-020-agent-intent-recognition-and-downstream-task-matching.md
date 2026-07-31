---
level: L2-LLD
module: agent-core
feature_type: functional
feature_id: FEAT-020
status: active
updated: 2026-07-31
dependency:
  - ../../L1-High-Level-Design/agent-core/README.md
  - ../../L1-High-Level-Design/agent-core/logical.md
  - ../../L1-High-Level-Design/agent-core/physical.md
  - ../../../version-scope/FEAT-020-agent-intent-matching-and-action-routing.md
  - ../../../version-scope/FEAT-008-user-interaction-interrupt-and-response.md
---

# Agent 与 Workflow 意图匹配及后续处理设计文档

## 1. 设计目标与边界

### 1.1 特性范围

FEAT-020 在 `agent-solution/common/agent-core-ext-java` 新增独立的 `agent-intent` 模块，为 Agent 和 Workflow 提供意图套件。套件在初始化阶段把 A2A Agent Card Skill 和用户自定义配置转换为统一意图项；在执行阶段由意图匹配 SPI 选择一个意图项，再调用意图结果生成 SPI 执行该项的结果工具函数，返回可供 Agent 或 Workflow 继续处理的结构化结果。

本特性包含：

- 初始化 SPI、意图匹配 SPI、意图结果生成 SPI 及默认实现；
- Agent Card Skill、用户自定义意图项和 fallback 的统一执行模型；
- 基于 `Reranker` 和匹配阈值的默认匹配；
- ReAct Agent、DeepAgent 的意图 Tool 和提示词接入；
- 不依赖提示词的独立 Workflow 意图组件；
- 工具中断续接后发生意图跳变时，供 Agent 重新匹配使用的结构化信号和提示词约束。

本特性不发现、拉取或刷新 Agent Card，不注入远端 A2A 工具，不发起远端调用，也不处理服务层的 `input-required`。这些能力由 FEAT-008 和 FEAT-004 承担。`agent-core-java` 只作为公共 SDK 依赖，本特性不修改其源码。

### 1.2 能力对齐矩阵

| 需求能力 | L2 设计响应 |
|---|---|
| 初始化 SPI | `IntentInitializer#initialize` 接收 Agent Card 来源和初始化 `kwargs`，返回不可变 `IntentCatalog`。 |
| Agent Card Skill 独立匹配 | 默认初始化为每条 Skill 创建一个 `IntentItem`，同一 Card 的各项共享远端目标信息。 |
| 用户自定义意图项 | 默认初始化读取 `CustomIntentDefinition(description, resultFunction)`。 |
| fallback | 单独保存为 `FallbackIntent`，不加入候选列表，只在正常未匹配后执行。 |
| 意图匹配 SPI | `IntentMatcher#match` 选择意图项，并显式调用传入的 `IntentResultGenerator`。 |
| 默认 Reranker 匹配 | 对全部候选评分，只接受达到初始化阈值的首项；空候选不调用 Reranker。 |
| 意图结果生成 SPI | `IntentResultGenerator#generate` 统一执行 Agent Card、自定义项和 fallback 的结果工具函数。 |
| 同步不可中断回调 | `IntentResultFunction` 是同步函数，不接收 Agent loop 的 Session `kwargs`；中断被转换为失败。 |
| Agent 接入 | `IntentMatchingTool` 通过 `IntentAgentBinder` 接入 ReAct Agent 和 DeepAgent；DeepAgent 使用公开的 `registerHarnessTool(...)` 与 `getAgent()` 完成 Tool 和提示词绑定。 |
| Agent 提示词 | 默认提示词随 Tool 注入，可由用户完整覆盖；明确正常结束和意图跳变重匹配边界。 |
| Workflow 接入 | `IntentMatchingComponent` 独立调用同一 `IntentSuite`，不注册 Agent Tool、不注入提示词。 |
| 描述固定 | `IntentCatalog` 初始化后不可变，无动态更新 API；变更时创建并绑定新的套件。 |

### 1.3 当前实现差距

当前代码已提供 `Tool`、ReAct Agent、DeepAgent、Workflow Component 和 `Reranker` 等基础能力，但尚未形成 FEAT-020 的完整意图套件：

- `Reranker` 只提供候选评分与排序，不负责匹配阈值、fallback 或结果函数执行；缺少把三者串联起来的意图匹配 SPI。
- 现有 `IntentDetectionComponent` 是基于 LLM 的 Workflow 分类组件，不能承载 Agent Card Skill、自定义结果函数和统一 fallback 语义。
- ReAct Agent 与 DeepAgent 都支持新增 Tool 和提示词 section，但当前没有同时适配两类 Agent 的意图 Tool 绑定器。DeepAgent 已公开 `registerHarnessTool(...)`，并通过 Lombok `@Getter` 暴露内部 `ReActAgent` 的 `getAgent()`，无需创建前改写 `DeepAgentConfig` 或使用反射。
- `AbilityManager` 使用 `String.valueOf(result)` 生成 `ToolMessage`；直接返回普通 Java Map 不能保证模型看到合法 JSON，意图结果需要统一 JSON 编码。
- ReAct 中断恢复会把被续接 Tool 的返回值加入模型上下文，但不会自动新增一条包含续接输入的 `UserMessage`；下游 Tool 必须显式返回携带最新意图的结构化跳变信号。

## 2. 公共接口设计

### 2.1 意图套件生命周期

`IntentSuite` 是 Agent Tool 和 Workflow 组件共享的执行入口。构造时只执行一次初始化，之后只读使用 `IntentCatalog`。

```java
public final class IntentSuite {
    public static IntentSuite create(
            IntentInitializationRequest request,
            IntentInitializer initializer,
            IntentMatcher matcher,
            IntentResultGenerator resultGenerator);

    public IntentResult match(String semantic);
    public IntentCatalog catalog();
}
```

`create` 的固定顺序是：

1. 调用初始化 SPI；
2. 校验初始化结果、候选唯一性、fallback 和默认匹配阈值；
3. 建立不可变目录；
4. 保存 matcher 和 result generator。

初始化失败时不创建套件。`match` 不重新初始化，也不接受新的描述或 Agent Card。

### 2.2 初始化 SPI

```java
public interface IntentInitializer {
    IntentCatalog initialize(IntentInitializationRequest request)
            throws IntentInitializationException;
}

public record IntentInitializationRequest(
        List<AgentCardIntentSource> agentCardSources,
        Map<String, Object> kwargs) {
}

public record AgentCardIntentSource(
        org.a2aproject.sdk.spec.AgentCard agentCard,
        String remoteTargetId,
        String delegateToolName,
        String delegateInputName,
        Map<String, Object> context) {
}
```

`remoteTargetId` 是 runtime 注册表中的稳定远端目标标识；`delegateToolName` 是 Agent 可见的实际远端 Tool 名称；`delegateInputName` 是该 Tool 接收远端请求文本的字段名。三者都由 FEAT-008 提供，Core 不推导 runtime 命名或 Tool 入参规则。`context` 用于携带来源、租户或其他初始化信息，不进入 Agent loop 的 Tool `kwargs`。

初始化输出：

```java
public record IntentCatalog(
        List<IntentItem> items,
        FallbackIntent fallback,
        Double matchThreshold,
        Map<String, Object> initializationKwargs) {
}

public record IntentItem(
        String id,
        String description,
        IntentSourceType sourceType,
        Map<String, Object> sourceData,
        IntentResultFunction resultFunction) implements IntentExecutionTarget {
}

public record FallbackIntent(
        String id,
        FallbackType type,
        Map<String, Object> sourceData,
        IntentResultFunction resultFunction) implements IntentExecutionTarget {
}

public enum IntentSourceType {
    AGENT_CARD_SKILL, CUSTOM
}

public enum FallbackType {
    INTENT, TOOL
}

public sealed interface IntentExecutionTarget
        permits IntentItem, FallbackIntent {
    String id();
    Map<String, Object> sourceData();
    IntentResultFunction resultFunction();
}
```

`FallbackIntent` 不实现可匹配描述，不出现在 `items`。fallback 意图项和 fallback 工具只在 `FallbackType` 上保留来源差异，执行时都使用 `IntentResultFunction`。

默认初始化实现 `DefaultIntentInitializer` 识别以下公开键和值类型：

| `kwargs` 键 | 值类型 | 语义 |
|---|---|---|
| `customIntents` | `List<CustomIntentDefinition>` | 用户自定义的 `id + description + resultFunction`。 |
| `fallback` | `FallbackDefinition` | 可选 fallback 意图项或 fallback 工具；只能配置一个。 |
| `matchThreshold` | `Double` | 默认 matcher 的匹配阈值。 |

```java
public record CustomIntentDefinition(
        String id,
        String description,
        IntentResultFunction resultFunction) {
}

public record FallbackDefinition(
        String id,
        FallbackType type,
        IntentResultFunction resultFunction) {
}
```

默认初始化规则：

1. 按 `remoteTargetId + skill.id` 生成稳定且全目录唯一的 Agent Card 意图项 ID。
2. 每条 Skill 单独生成一个意图项；匹配文本由 Card 名称、Skill 名称、Skill 描述、tags 和 examples 组成，不把接口 URL、安全配置或认证信息加入匹配文本。
3. Agent Card 意图项保存完整 Card、当前 Skill、`remoteTargetId`、`delegateToolName`、`delegateInputName` 和来源 context。
4. 每个 `CustomIntentDefinition` 生成一个意图项，匹配文本使用其 `description`。
5. fallback 单独保存，不加入 `items`。
6. `kwargs` 保存为只读顶层 Map；结果函数执行时取得的是本次初始化保存的内容。
7. 空白描述、重复 ID、缺少结果函数、Agent Card 缺少必要 Skill 信息，或远端目标、Tool 名和 Tool 入参名不完整时初始化失败，不静默覆盖或丢弃。

### 2.3 意图匹配 SPI

```java
public interface IntentMatcher {
    IntentResult match(
            IntentMatchRequest request,
            IntentResultGenerator resultGenerator);
}

public record IntentMatchRequest(
        String semantic,
        IntentCatalog catalog) {
}
```

此签名把“匹配后必须调用意图结果生成 SPI”定义为 SPI 契约的一部分。实现必须遵守：

- 命中意图项时，调用一次 `resultGenerator.generate(...)`；
- 正常未命中且有 fallback 时，调用一次 result generator 执行 fallback；
- 正常未命中且无 fallback 时，直接返回 `UNMATCHED`；
- 匹配失败时返回 `FAILED`，不得调用 result generator、fallback 或其他意图项；
- 一次调用最多选择一个意图项。

默认实现 `RerankerIntentMatcher` 的处理顺序：

1. 校验 `semantic` 非空；无效输入返回明确失败。
2. `catalog.items` 为空时不调用 Reranker，直接进入未匹配处理。
3. 为全部意图项构造带稳定 item ID 的候选，一次调用覆盖全部候选；底层 Reranker 可以按自身实现分批。
4. 调用 `Reranker` 获取每个候选的分数，按分数降序、item ID 升序确定唯一首项。
5. 首项分数大于等于 `matchThreshold` 时命中，否则正常未匹配。
6. 命中或 fallback 时调用 result generator，并原样返回其 `IntentResult`。

使用默认 matcher 且目录非空时，`matchThreshold` 必须是有限有效值；缺失时在 `IntentSuite.create` 阶段失败。Reranker 抛错、超时、漏回候选或返回非法分数均属于匹配失败，不触发 fallback。

### 2.4 意图结果生成 SPI 与结果函数

```java
public interface IntentResultGenerator {
    IntentResult generate(IntentResultRequest request);
}

public record IntentResultRequest(
        String semantic,
        IntentExecutionKind executionKind,
        IntentExecutionTarget target,
        Map<String, Object> initializationKwargs) {
}

public enum IntentExecutionKind {
    MATCHED_ITEM, FALLBACK
}

@FunctionalInterface
public interface IntentResultFunction {
    IntentAction apply(IntentResultFunctionContext context) throws Exception;
}

public record IntentResultFunctionContext(
        String semantic,
        IntentExecutionTarget target,
        Map<String, Object> initializationKwargs) {
}
```

`IntentResultFunction` 是普通同步函数，不继承 AgentCore `Tool`，不接收 Session，也不接收 `Tool.invoke` 的运行时 `kwargs`。它必须在当前线程、当前调用内返回 `IntentAction` 或抛出失败。

默认结果生成实现 `DefaultIntentResultGenerator`：

1. 取得所选意图项或 fallback 的 result function；
2. 使用待匹配语义、执行目标和初始化 `kwargs` 构造函数上下文；
3. 同步调用一次函数；
4. 把函数结果包装为 `MATCHED` 或 `FALLBACK` 的 `IntentResult`；
5. 函数抛错、返回空值或产生 Agent/Workflow 中断时返回 `FAILED`，不执行其他目标或 fallback。

对 `ToolInterruptException`、Workflow 交互中断或等价可恢复中断的处理固定为 `RESULT_FUNCTION_INTERRUPT_NOT_ALLOWED`。用户需要可中断本地工具时，result function 应返回 `CALL_TOOL` 指示，由 LLM 在意图 Tool 返回后的 Agent loop 中调用该工具。

### 2.5 统一结果模型

```java
public enum IntentResultStatus {
    MATCHED, FALLBACK, UNMATCHED, FAILED
}

public enum IntentActionType {
    DELEGATE_AGENT, CALL_TOOL, DIRECT_RESPONSE, CUSTOM
}

public record IntentAction(
        IntentActionType type,
        String target,
        String instruction,
        Map<String, Object> data) {
}

public record IntentResult(
        String type,
        IntentResultStatus status,
        String intentId,
        IntentSourceType sourceType,
        IntentAction action,
        String message,
        IntentFailure failure) {
}

public enum IntentFailureStage {
    MATCH, RESULT_GENERATION, RESULT_FUNCTION
}

public record IntentFailure(
        IntentFailureStage stage,
        String code,
        String message) {
}
```

`type` 固定为 `intent_result`，供 Workflow 与 runtime 可靠识别。各路径输出如下：

| 路径 | `status` | `action` / `message` |
|---|---|---|
| Agent Card Skill 命中 | `MATCHED` | `DELEGATE_AGENT`；`target=delegateToolName`；data 包含完整 Agent Card、当前 Skill、`remoteTargetId`、`delegateToolName`、`delegateArguments={delegateInputName: semantic}` 和本次 semantic。 |
| 自定义意图项命中 | `MATCHED` | 用户 result function 返回的 action。 |
| fallback | `FALLBACK` | fallback result function 返回的 action。 |
| 无 fallback 未匹配 | `UNMATCHED` | `message=意图未匹配`，无 action。 |
| 匹配或结果生成失败 | `FAILED` | `failure` 包含阶段、错误码和安全错误信息。 |

Agent Card 默认 result function 只生成 `DELEGATE_AGENT` action，不调用远端 Agent。Agent 场景由 LLM 调用 `target` 指定的 runtime Tool；Workflow 场景由 FEAT-008 适配器消费同一 action。

字段约束固定为：`MATCHED` 必须包含 `intentId/sourceType/action`；`FALLBACK` 必须包含 fallback `intentId` 和 `action`，`sourceType` 为空；`UNMATCHED` 只包含默认 message；`FAILED` 只包含 failure。互斥路径的字段必须为空，避免上游把失败或未匹配误当成可执行 action。

`IntentResultCodec` 是 Agent Tool、Workflow Component 与 runtime-ext 共用的结构化边界：

```java
public final class IntentResultCodec {
    public com.fasterxml.jackson.databind.JsonNode toJson(IntentResult result);
    public Map<String, Object> toMap(IntentResult result);
    public IntentResult from(Object value) throws IntentResultFormatException;
}
```

`IntentMatchingTool` 使用 `toJson`，`IntentMatchingComponent` 使用 `toMap`，FEAT-008 的 Workflow 结果适配器使用 `from`。编解码必须保留枚举值、Agent Card、Skill、action data 和失败信息；未知 `type`、缺少必填字段或不合法枚举值必须拒绝，不能按远端委派处理。

### 2.6 Agent Tool 与提示词接入

`IntentMatchingTool` 继承 AgentCore `Tool`：

```java
public final class IntentMatchingTool extends Tool {
    public static final String DEFAULT_NAME = "intent_match";

    public Object invoke(
            Map<String, Object> inputs,
            Map<String, Object> invocationKwargs);

    public Iterator<Object> stream(
            Map<String, Object> inputs,
            Map<String, Object> invocationKwargs);
}
```

Tool 的 LLM 输入固定为：

```json
{
  "type": "object",
  "properties": {
    "semantic": {
      "type": "string",
      "description": "需要匹配的完整用户请求或子任务"
    }
  },
  "required": ["semantic"],
  "additionalProperties": false
}
```

Tool 只调用 `IntentSuite.match(semantic)`，不重复匹配、fallback 或结果函数执行。`stream` 不建立异步或中断流程，只返回包含一次 `invoke` 结果的迭代器。为确保 `AbilityManager` 写入模型上下文的是合法 JSON，Agent Tool 使用 Jackson 把 `IntentResult` 编码为 `JsonNode`；Workflow 使用同一字段模型转换为 Map。

`invocationKwargs` 只由 AgentCore 传递运行时 Session 等信息，Tool 不把它传给 initializer、matcher 或 result function，也不覆盖 catalog 中保存的初始化 `kwargs`。

```java
public final class IntentAgentBinder {
    public static IntentAgentBinding bind(
            ReActAgent agent,
            IntentSuite suite,
            IntentAgentOptions options);

    public static IntentAgentBinding bind(
            DeepAgent agent,
            IntentSuite suite,
            IntentAgentOptions options);
}

public record IntentAgentBinding(
        IntentMatchingTool tool,
        String promptSectionName) {
}

public record IntentAgentOptions(
        String toolId,
        String promptOverride) {
    public static IntentAgentOptions defaults();
}
```

`IntentAgentBinder` 负责：

- 对 ReAct Agent，在 `Runner.resourceMgr()` 注册 Tool 实例，在 `AbilityManager` 注册 ToolCard，并通过 `addPromptBuilderSection(...)` 注入 `intent-routing` section；
- 对 DeepAgent，调用 `deepAgent.registerHarnessTool(intentTool)` 注册同一个 Tool，再通过 `deepAgent.getAgent().addPromptBuilderSection(...)` 注入同名 section；
- 校验同一 Agent 不存在同名 Tool；
- 要求在 Agent 自身配置完成后、首次执行前绑定。ReAct Agent 后续再次调用 `configure(...)` 会重建 PromptBuilder，因此必须重新创建并绑定 Agent；当前版本不支持在运行中的 Agent 替换套件。

DeepAgent 先按原有 `DeepAgentConfig` 完成创建，再执行 `IntentAgentBinder.bind(deepAgent, ...)`。绑定器不修改 `DeepAgentConfig.systemPrompt` 或原 Tool 列表，也不通过反射访问内部 Agent。这样默认意图提示词或用户覆盖提示词只替换 `intent-routing` section，不覆盖 DeepAgent 原有 system prompt。

`toolId` 是 ResourceManager 中的资源 ID，默认生成当前 Agent 内唯一值；ToolCard name 固定为 `intent_match`。`promptOverride` 为空时使用默认意图提示词，非空时由用户内容完整替换默认意图提示词，但不覆盖 Agent 原有的其他 system prompt。默认意图提示词至少包含这些规则：

1. 需要选择处理目标时，调用 `intent_match` 并传入完整用户请求或 DeepAgent 子任务。
2. 返回 `DELEGATE_AGENT` 时，调用 `action.target` 指定的远端 Tool，并使用 `action.data.delegateArguments` 作为 Tool 实参，不自行猜测 Tool 名或字段名。
3. 返回 `CALL_TOOL` 时，调用 action 指定的本地 Tool；返回 `DIRECT_RESPONSE` 时直接答复。
4. fallback 和未匹配按结果内容处理，不自行虚构命中目标。
5. 下游工具正常完成后完成本轮答复，不再次匹配。
6. 只有下游工具在用户交互中断续接后返回完整 `IntentShiftSignal` 时，才使用其中的 `latestIntent` 再次调用 `intent_match`，而不是结束本轮会话。

意图跳变信号定义为：

```java
public record IntentShiftSignal(
        String signal,
        String latestIntent,
        String message) {
    // signal 固定为 "intent_shift"
}
```

该信号由意图匹配完成后调用的远端 Agent Tool 或其他本地 Tool 返回，不由意图套件生成。信号以 `{"signal":"intent_shift","latestIntent":"...","message":"..."}` 的 JSON 结构进入 `ToolMessage`；本地 Tool 使用本模块的 Jackson 编码能力返回合法 JSON，远端 Agent 将同一结构放入 A2A 结果文本。信号缺失、`latestIntent` 为空或工具正常完成时均不得触发重新匹配。

### 2.7 Workflow 组件接入

```java
public final class IntentMatchingComponent implements ComponentComposable {
    public IntentMatchingComponent(IntentSuite suite);

    public Executable<?, ?> toExecutable();
}

final class IntentMatchingExecutable extends ComponentExecutable {
    public Object invoke(
            Object inputs,
            NodeSessionApi session,
            ModelContext context);
}
```

Workflow 输入为 `{"semantic": ...}`，输出为 `IntentResult` 的 Map 表示。Executable 只调用 `IntentSuite.match` 并把结果交给下一个节点，不注册 Agent Tool、不调用 LLM、不读取 Workflow Session 作为初始化 `kwargs`。

Workflow 根据 `status` 和 `action.type` 显式连接后续节点：`DELEGATE_AGENT` 分支保留完整 `IntentResult` 作为 Workflow 正常终态，待 Workflow 完成后由 FEAT-008 Runtime Handler 适配远端调用；`CALL_TOOL` 进入本地工具节点；`DIRECT_RESPONSE/CUSTOM` 进入对应业务节点；`UNMATCHED` 和 `FAILED` 分别进入未匹配与失败节点。fallback 依据其结果函数返回的 action 使用同一分支规则。Workflow 不在图内创建远端调用中断，不注入 Agent 提示词，也不自动识别或重新匹配意图跳变。

## 3. 核心运行模型

### 3.1 初始化流程

```text
Agent/Workflow 创建阶段
  -> 调用方准备 AgentCardIntentSource 列表和初始化 kwargs
  -> IntentInitializer.initialize
       -> 每条 Agent Card Skill -> IntentItem
       -> 每个 description + resultFunction -> IntentItem
       -> fallback -> 独立 FallbackIntent
       -> 保存匹配阈值和初始化 kwargs
  -> IntentSuite 校验并冻结 IntentCatalog
  -> ReAct Agent: 配置完成后绑定 IntentMatchingTool + 提示词 section
     DeepAgent: 创建完成后通过公开 API 绑定 IntentMatchingTool + 提示词 section
     Workflow: 创建 IntentMatchingComponent
```

初始化完成后不读取外部 Card 或配置变化。需要更新 description、Skill 或 result function 时，应用创建新的 `IntentSuite`，并在新的 Agent/Workflow 实例对外服务前完成绑定；当前版本不提供运行中热更新。

### 3.2 默认匹配与结果生成流程

```text
semantic
  -> IntentMatchingTool / IntentMatchingComponent
  -> IntentSuite.match
  -> RerankerIntentMatcher
       -> 空候选: 正常未匹配
       -> 全候选评分
       -> top1 达阈值: 选择 IntentItem
       -> top1 未达阈值: 正常未匹配
  -> 命中: IntentResultGenerator.generate(IntentItem)
  -> 未命中且有 fallback: IntentResultGenerator.generate(FallbackIntent)
  -> 未命中且无 fallback: UNMATCHED
  -> 最终 IntentResult 返回上游
```

### 3.3 Agent 后续处理

```text
LLM -> intent_match -> IntentResult
  -> DELEGATE_AGENT: LLM 调用 action.target 对应的远端 Tool
  -> CALL_TOOL: LLM 调用 action.target 对应的本地 Tool
  -> DIRECT_RESPONSE / UNMATCHED: LLM 直接答复
  -> 下游 Tool 正常结束: 本轮结束
  -> 下游 Tool 中断续接后返回 IntentShiftSignal:
       ToolMessage 携带 latestIntent
       -> LLM 再次调用 intent_match(latestIntent)
       -> 进入新的处理循环
```

重新匹配发生在下游 Tool loop，不发生在前一次 `IntentMatcher#match` 中。意图套件不检查用户续接输入，也不把任意工具失败解释为意图跳变。

### 3.4 Workflow 后续处理

```text
Workflow 输入
  -> IntentMatchingComponent
  -> IntentResult
  -> 显式 Workflow 分支
       -> DELEGATE_AGENT: 保留 IntentResult 作为正常终态
            -> Workflow 完成后由 FEAT-008 Runtime Handler 适配远端调用
       -> CALL_TOOL / DIRECT_RESPONSE / CUSTOM: 对应本地或业务节点
       -> UNMATCHED / FAILED: 未匹配或失败节点
```

Workflow 只在流程经过该组件时匹配一次。远端调用完成或中断续接后，FEAT-008 不回到该组件自动匹配。

### 3.5 失败处理

| 失败阶段 | 对外行为 | fallback |
|---|---|---|
| 初始化参数、候选或阈值非法 | 抛 `IntentInitializationException`，套件不创建 | 不执行 |
| matcher SPI 抛错或返回非法结果 | `FAILED / MATCH_FAILED` | 不执行 |
| Reranker 失败或分数非法 | `FAILED / RERANK_FAILED` | 不执行 |
| result generator SPI 失败 | `FAILED / RESULT_GENERATION_FAILED` | 不执行 |
| result function 抛错 | `FAILED / RESULT_FUNCTION_FAILED` | 不执行 |
| result function 产生中断 | `FAILED / RESULT_FUNCTION_INTERRUPT_NOT_ALLOWED` | 不执行 |
| 正常未匹配且有 fallback | 执行 fallback result function | 执行一次 |
| 正常未匹配且无 fallback | `UNMATCHED / 意图未匹配` | 不执行 |

## 4. 代码结构与类级设计

### 4.1 Maven 模块

```text
common/agent-core-ext-java/
├── pom.xml                         # 新增 <module>agent-intent</module>
└── agent-intent/
    ├── pom.xml                     # artifactId: agent-intent
    └── src/
        ├── main/java/com/openjiuwen/agents/intent/
        └── test/java/com/openjiuwen/agents/intent/
```

`agent-intent` 依赖：

- `com.openjiuwen:agent-core-java`，复用 Tool、ReAct/DeepAgent、Workflow 和 Reranker；
- `org.a2aproject.sdk:a2a-java-sdk-spec`，直接使用标准 AgentCard/AgentSkill；
- 与 AgentCore 版本对齐的 Jackson，用于 Agent Tool 的合法 JSON 输出。

`agent-core-ext-java/pom.xml` 在 dependency management 中统一声明 A2A SDK spec 与 Jackson 版本，并把 `agent-intent` 加入 modules；`agent-intent/pom.xml` 对上述依赖做直接声明，不能依赖 `agent-core-java` 的传递依赖偶然提供 A2A 或 Jackson API。

模块不依赖 `agent-runtime-java`、Spring Boot、A2A client/server 或 `agent-runtime-ext-java`，避免 Core 扩展反向依赖 Runtime。

### 4.2 包与关键类

```text
com.openjiuwen.agents.intent
├── api
│   ├── IntentInitializer.java
│   ├── IntentMatcher.java
│   ├── IntentResultGenerator.java
│   ├── IntentResultFunction.java
│   ├── IntentSuite.java
│   └── IntentModels.java           # 实际实现按公开类型拆文件
├── initializer
│   └── DefaultIntentInitializer.java
├── matcher
│   └── RerankerIntentMatcher.java
├── result
│   ├── DefaultIntentResultGenerator.java
│   ├── AgentCardIntentResultFunction.java
│   └── IntentResultCodec.java
├── agent
│   ├── IntentMatchingTool.java
│   ├── IntentAgentBinder.java
│   └── DefaultIntentPrompt.java
└── workflow
    ├── IntentMatchingComponent.java
    └── IntentMatchingExecutable.java
```

职责约束：

- `IntentSuite` 只编排 SPI 生命周期，不包含默认匹配算法。
- `DefaultIntentInitializer` 不调用 Reranker 或结果函数。
- `RerankerIntentMatcher` 不直接调用用户 result function，只调用 `IntentResultGenerator`。
- `DefaultIntentResultGenerator` 不再次选择意图项。
- Agent Tool 与 Workflow Component 不各自实现匹配逻辑。

### 4.3 依赖方向

```text
Agent Tool -------> IntentSuite <------- Workflow Component
                         |
                         +--> IntentInitializer SPI
                         +--> IntentMatcher SPI --> IntentResultGenerator SPI
                                                      |
                                                      +--> IntentResultFunction

agent-intent --> agent-core-java
agent-intent --> A2A Java SDK spec
runtime-ext  --> agent-intent            # FEAT-008 适配方向
agent-intent -X-> runtime-ext/runtime     # 禁止反向依赖
```

## 5. 关键数据与映射设计

### 5.1 Agent Card Skill 映射

| 来源 | `IntentItem` / action 字段 |
|---|---|
| runtime 注册名 | `remoteTargetId`，同时参与 item ID |
| runtime 远端 Tool 名 | `delegateToolName`、`action.target` |
| runtime 远端 Tool 输入字段 | `delegateInputName`；结果中生成 `delegateArguments={delegateInputName: semantic}` |
| 完整 `AgentCard` | `sourceData.agentCard`、`action.data.agentCard` |
| 当前 `AgentSkill` | 独立 item 的 description 来源、`action.data.skill` |
| 初始化 context | `sourceData.context`，按需返回给上游 |
| 本次待匹配语义 | `action.data.semantic`，供远端调用或下个节点使用 |

同一 Agent Card 的多条 Skill 分别生成 item，但 `remoteTargetId` 和 `delegateToolName` 相同。结果中返回的是命中的单条 Skill，不把 Card 级命中误表述为所有 Skill 同时命中。

### 5.2 用户自定义意图与 fallback 映射

| 输入 | 初始化结果 | 命中后行为 |
|---|---|---|
| `CustomIntentDefinition` | 可匹配 `IntentItem` | result generator 同步调用其 result function。 |
| fallback 意图项 | 独立 `FallbackIntent(type=INTENT)` | 正常未匹配后调用 result function。 |
| fallback 工具 | 独立 `FallbackIntent(type=TOOL)` | 正常未匹配后调用同一接口。 |
| 无 fallback | 无 `FallbackIntent` | 返回 `UNMATCHED`，不执行函数。 |

### 5.3 初始化与调用 `kwargs`

```text
Agent/Workflow 创建时提供的 kwargs
  -> IntentInitializationRequest.kwargs
  -> IntentCatalog.initializationKwargs
  -> IntentResultFunctionContext.initializationKwargs

Agent loop 的 Tool.invoke invocationKwargs
  -> 仅由 AgentCore 提供给 IntentMatchingTool
  -> 不进入上述链路
```

二者没有覆盖或合并关系。初始化 `kwargs` 的引用生命周期与 `IntentSuite` 一致。

## 6. 配置与接入设计

### 6.1 默认套件创建

```java
Map<String, Object> initializationKwargs = Map.of(
        "customIntents", List.of(
                new CustomIntentDefinition(
                        "local-order-query",
                        "查询本地订单缓存",
                        context -> IntentAction.callTool(
                                "query_local_order",
                                "调用本地订单工具",
                                Map.of("semantic", context.semantic())))),
        "fallback", FallbackDefinition.tool(
                "default-fallback",
                context -> IntentAction.directResponse("请重新描述需要处理的任务")),
        "matchThreshold", 0.65D);

IntentSuite suite = IntentSuite.create(
        new IntentInitializationRequest(agentCardSources, initializationKwargs),
        new DefaultIntentInitializer(),
        new RerankerIntentMatcher(reranker),
        new DefaultIntentResultGenerator());
```

阈值必须由当前意图目录和实际 Reranker 的验收数据确定，L2 示例值不作为生产默认值。默认 matcher 不提供“总是接受 top1”的隐式阈值。

### 6.2 Agent 接入

ReAct Agent 在完成 `configure(...)` 后、首次执行前绑定：

```java
IntentAgentBinder.bind(reactAgent, suite,
        new IntentAgentOptions("intent-match-router-01", null));
```

DeepAgent 按原配置创建后、首次执行前绑定：

```java
DeepAgent deepAgent = HarnessFactory.createDeepAgent(agentCard, baseConfig, workspace);
IntentAgentBinder.bind(deepAgent, suite,
        new IntentAgentOptions("intent-match-router-01", null));
```

`promptOverride` 为空时使用默认意图提示词；非空时完整覆盖默认意图提示词 section。Agent 原有 system prompt 和其他 section 保持不变。Tool `id` 在 AgentCore ResourceManager 中必须唯一，Tool `name` 默认保持 `intent_match`，便于提示词稳定引用。

### 6.3 Workflow 接入

```java
workflow.addWorkflowComp(
        "intent_match",
        new IntentMatchingComponent(suite),
        Map.of("semantic", "${start.query}"),
        null);
```

下一节点读取 `${intent_match.status}`、`${intent_match.action.type}`、`${intent_match.action.target}` 和 `${intent_match.action.data}`。Workflow 不接受 `IntentAgentOptions`，也没有提示词配置。

## 7. 集成测试与验收设计

### 7.1 SPI 与默认实现测试

| 测试组 | 必测内容 |
|---|---|
| `DefaultIntentInitializerTest` | 多 Card、单 Card 多 Skill、完整远端关联、自定义项、fallback、重复/缺失字段、不可变目录。 |
| `RerankerIntentMatcherTest` | 全候选评分、top1 达阈值、低于阈值、确定性并列、空候选不调用 Reranker。 |
| `DefaultIntentResultGeneratorTest` | Agent Card、自定义项、fallback 使用同一 SPI；同步返回、异常、空结果和中断拒绝。 |
| `IntentResultCodecTest` | IntentResult 在 JsonNode/Map/Java 对象间无损转换；非法 type、必填字段和枚举值被拒绝。 |
| `IntentFailureBoundaryTest` | 初始化、匹配、结果生成失败均不触发 fallback 或其他意图项。 |
| `IntentSpiReplacementTest` | 三个 SPI 分别可替换；自定义 matcher 通过 spy 验证实际调用 result generator。 |
| `IntentKwargsLifecycleTest` | 初始化 `kwargs` 固定，Agent Tool 调用 `kwargs` 不能覆盖；重新创建套件后新配置才生效。 |

### 7.2 Agent 集成测试

| 场景 | Given / When | Then |
|---|---|---|
| ReAct Tool 调用 | Scripted Model 生成 `intent_match` Tool call。 | Tool 只调用 matcher；合法 JSON 结果写入 ToolMessage。 |
| DeepAgent 远端意图 | 多 Card 且某 Card 有多 Skill。 | 命中单条 Skill；返回正确 Card、Skill、remoteTargetId、delegateToolName 和 delegateArguments。 |
| 自定义本地工具指示 | 自定义 result function 返回 `CALL_TOOL`。 | 回调在意图 Tool 内同步完成；下一轮 LLM 调用指定本地 Tool。 |
| 直接答复 | 自定义项或 fallback 返回 `DIRECT_RESPONSE`。 | LLM 使用结果答复，不调用其他 Tool。 |
| fallback / 无 fallback | Reranker 首项低于阈值。 | 分别执行 fallback 一次或返回 `UNMATCHED`。 |
| 正常 Tool 完成 | 远端或本地 Tool 正常返回。 | LLM 完成本轮，不再次调用 `intent_match`。 |
| 意图跳变 | 下游 Tool 中断续接后返回 `IntentShiftSignal(latestIntent)`。 | ToolMessage 包含最新意图；Scripted Model 再次调用 `intent_match`，参数为 `latestIntent`。 |
| 非法跳变 | 普通失败、普通结果或信号缺少最新意图。 | 不重新匹配。 |
| 提示词覆盖 | ReAct 和 DeepAgent 均在自身配置完成后、首次执行前绑定；分别使用默认意图提示词和用户覆盖提示词。 | Agent 原提示词保留，模型输入只包含一个生效的意图提示词 section；Workflow 不受影响。 |

ReAct 与 DeepAgent 的真实框架测试使用可编程 Model，断言 Tool call 序列和入参，避免仅依赖不稳定的自然语言输出。另保留一个带真实 LLM 的可选 smoke test，不能替代确定性集成测试。

### 7.3 Workflow 集成测试

| 场景 | 验收标准 |
|---|---|
| Agent Card Skill | Component 输出 `DELEGATE_AGENT`，字段与 Agent Tool 对同一 semantic 的结果一致；Workflow 正常结束后才由 FEAT-008 Runtime Handler 消费。 |
| 自定义意图项 | result function 在组件调用内同步执行，结果可被下一节点读取。 |
| fallback | 未命中后执行一次 fallback，且 fallback 从未进入 Reranker 候选。 |
| 无 fallback | 输出 `UNMATCHED / 意图未匹配` 并进入业务响应节点。 |
| 提示词边界 | 创建和执行 Workflow 不修改任何 Agent PromptBuilder。 |
| 重匹配边界 | Workflow 只在图经过该组件时调用 matcher；组件自身不循环。 |

### 7.4 跨特性集成验收

与 FEAT-008 的集成测试至少证明：

1. runtime 注册名同时成为 `remoteTargetId`，每条 Skill 结果的 `delegateToolName` 与实际注入 Tool 完全一致，`delegateArguments` 使用该 Tool 的真实输入字段；
2. Agent 根据 `DELEGATE_AGENT` 结果进入 FEAT-004 远端调用链，`input-required` 和续接由 FEAT-008 处理；
3. Workflow 的同类结果由 runtime-ext 转换为 `resume=false` 的远端委派，不依赖 Agent 提示词；
4. 远端或本地 Tool 续接后返回 `IntentShiftSignal` 时，runtime 只透传，重新匹配由 Agent 提示词和 LLM 完成；
5. Agent Card 或 Skill 在 registry 中变化后，既有 suite 不变；创建并绑定新 suite 后新描述才参与匹配。
