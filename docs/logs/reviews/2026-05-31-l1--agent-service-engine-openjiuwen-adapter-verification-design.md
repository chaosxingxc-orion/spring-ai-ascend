# Agent Service Engine — OpenJiuwen 适配器接入与验证 设计补丁

> 版本：v1.0
> 日期：2026-05-31
> 关联主设计：`2026-05-30-l1--agent-service-engine-model-design.md`（以下简称"主设计"）
> 框架基准：`openJiuwen/agent-core-java` 分支 `0.1.12`
> 来源：基于 0.1.12 真实源码逐行核对，非记忆推断

## 1. 背景与动机

主设计第 10 章定义了 `OpenJiuwenAgentHandler` / `OpenJiuwenAgentFactory` /
`OpenJiuwenMessageConverter` 三件套，但其方法体为占位（`return null`），
且第 14 章对框架坐标、流式适配、LLM 配置注入的描述未经真实 API 核对。

本补丁做两件事：

1. 用 0.1.12 真实源码纠正主设计中与框架实际不符的描述（§3）；
2. 落实 openJiuwen 适配器的执行契约（§4）、`EngineProperties` 字段（§5）
   与验证策略（§6），使主设计第 10 章可被忠实实现。

本补丁**不修改**主设计的 API/SPI/Event/Dispatch 建模（第 4–9、11–13 章），
那部分与框架无耦合，无需改动。

## 2. 真实 API 事实（0.1.12 源码核实）

下列事实均有源码出处，是后续所有结论的依据。

| 事实 | 出处 | 内容 |
|---|---|---|
| Maven 坐标 | 根 `pom.xml` | `com.openjiuwen:agent-core-java:0.1.12` |
| 执行入口 | `runner/Runner.java:172` | `Object runAgent(Object agent, Object inputs, Object session, ModelContext context)`，**同步** |
| 单次执行返回 | `singleagent/agents/ReActAgent.java:573` 及类头注释 | `Map{"output", "result_type"}`，`result_type ∈ {answer, error, interrupt}` |
| 流式返回 | `ReActAgent.java:788` | `Iterator<Object>`（**非** Reactor `Flux`） |
| LLM 注入点 | 示例 `ReActWeatherAgentExample.java:84-90` | `ReActAgentConfig.configureModelClient(provider, apiKey, apiBase, modelName, sslVerify)` |
| 配置来源约定 | 示例 `ExampleApiConfigLoader.java:40-58` | `apiconfig.json` 5 键：`MODEL_PROVIDER / API_KEY / API_BASE / MODEL_NAME / LLM_SSL_VERIFY` |
| 模型可替身 | `ReActAgent.java:274` + `foundation/llm/Model.java:42` | `setLlm(Model)` 存在；`Model` 为 `public class`（非 final，可继承） |

## 3. 对主设计的纠正项

| # | 主设计现状 | 纠正为（0.1.12 实证） | 影响章节 |
|---|---|---|---|
| 1 | §14.1 groupId `io.gitcode.openjiuwen` | `com.openjiuwen`（root pom 确认） | §14.1 依赖坐标 |
| 2 | §14.1 版本"按上游实际为准" | 锁定 `0.1.12` | §14.1 |
| 3 | §14.3 reactor-core 用于"openJiuwen stream 适配" | 框架流式为 `Iterator<Object>`，**不产出 Flux**；此理由不成立。第一版适配器走同步 `Runner.runAgent`，reactor 仅在 access-layer 流式桥接侧按需保留，不因 openJiuwen 而引入 | §14.3 |
| 4 | §3/§14.2 点名 `EngineProperties` 却从未定义字段 | 见 §5 字段定义；api-key 走 Vault（pom 已含 `spring-cloud-starter-vault-config`） | §3、§14.2、§15.2 |
| 5 | §10 适配器执行契约含糊（方法体 `return null`） | 见 §4 执行契约；`result_type` 三值天然映射 Completed/Failed/Interrupted | §10 全章 |

## 4. OpenJiuwen 适配器执行契约

### 4.1 关联点：唯一接缝在 OpenJiuwenAgentFactory

"碰真实框架"的代码收口到 `OpenJiuwenAgentFactory.create()` 一处。其余适配器代码
只见 engine 自身类型，可独立编译与测试。`create()` 内部的真实构建（逐字对应官方示例
`ReActWeatherAgentExample.java:72-99`）：

```java
AgentCard card = AgentCard.builder().id(agentId).name(agentId).description(desc).build();
ReActAgent agent = new ReActAgent(card);                       // com.openjiuwen.core.singleagent.ReActAgent
ReActAgentConfig config = ReActAgentConfig.builder()
        .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
        .maxIterations(props.getMaxIterations())
        .build()
        .configureModelClient(                                 // ← LLM 注入（§5）
                props.getModelProvider(), props.getApiKey(),
                props.getApiBase(), props.getModelName(), props.isSslVerify());
agent.configure(config);
return agent;
```

### 4.2 执行与事件映射：OpenJiuwenAgentHandler.execute

`Runner.runAgent` 同步返回 `Map{output, result_type}`；适配器据 `result_type` 映射为
主设计第 6 章事件流。映射规则：

| 框架 `result_type` | 适配器产出事件序列 | 对应主设计 §13 动作 |
|---|---|---|
| `answer` | `EngineStartedEvent` → `EngineCompletedEvent(output)` | markRunning → markSucceeded + completeOutput |
| `error` | `EngineStartedEvent` → `EngineFailedEvent(output)` | markRunning → markFailed + failOutput |
| `interrupt` | `EngineStartedEvent` → `EngineInterruptedEvent` | markRunning → markWaiting + requestUserInput |
| 抛异常 | `EngineFailedEvent(异常信息)` | markFailed + failOutput |

> `interrupt` 分支的细化（`result_type=interrupt` 携带的 `state`/`interrupt_ids`
> 如何映射到 `InterruptType` 与 `prompt`）属主设计 Phase 2，本补丁不展开，仅确认通道成立。

### 4.3 输入转换：OpenJiuwenMessageConverter

将 `AgentExecutionContext`（`EngineInput` + `EngineExecutionScope`）转为框架入参 Map。
第一版只处理文本：

```java
Map.of("query", lastUserMessageText(input), "conversation_id", scope.getTaskId())
```

`conversation_id` 复用 `taskId`，与主设计 §5.1"异步执行定位直接复用 taskId"一致。

## 5. EngineProperties 字段定义

补主设计 §3/§14.2 点名却未定义的 `EngineProperties`。LLM 配置 5 字段沿用框架
`apiconfig.json` 约定键名，前缀 `agent-service.engine.openjiuwen`：

| 字段 | 类型 | 来源键 | 说明 |
|---|---|---|---|
| `modelProvider` | String | `MODEL_PROVIDER` | 模型供应商标识 |
| `apiKey` | String | `API_KEY` | **密钥：走 Vault，禁止明文 yaml** |
| `apiBase` | String | `API_BASE` | 模型服务 base url |
| `modelName` | String | `MODEL_NAME` | 模型名 |
| `sslVerify` | boolean | `LLM_SSL_VERIFY` | 默认 true |
| `maxIterations` | int | — | ReAct 最大轮次，默认 3 |

`apiKey` 经 `spring-cloud-starter-vault-config`（pom 已含）注入；其余可走普通配置。

## 6. 验证策略

### 6.1 主验证手段：联网冒烟（真 LLM）

直接 `new OpenJiuwenAgentHandler(...).execute(...)`，打真实 LLM，验证"openJiuwen 能被
驱动 + 适配器事件映射成立"。

```java
@Tag("smoke")   // 排除出默认门禁，走 failsafe（pom 已含 maven-failsafe-plugin）
class OpenJiuwenAgentHandlerSmokeIT {
    @Test
    void realAgent_realLlm_emitsStartedThenCompleted() {
        EngineProperties props = EngineProperties.fromApiConfig();   // 读 5 个键
        var handler = new OpenJiuwenAgentHandler(
                "smoke-echo-agent",
                new OpenJiuwenAgentFactory(props),
                new OpenJiuwenMessageConverter());
        List<EngineExecutionEvent> events =
                handler.execute(new AgentExecutionContext(scope("task-1"), userInput("用一句话介绍你自己"))).toList();
        assertThat(events).first().isInstanceOf(EngineStartedEvent.class);
        assertThat(events).last().isInstanceOf(EngineCompletedEvent.class);
    }
}
```

配置归档：
- 真密钥 → `agent-service/src/test/resources/openjiuwen/apiconfig.json`，**进 .gitignore**；
- 提交 `apiconfig.json.template`（占位值）供他人照填；
- 键名沿用框架：`MODEL_PROVIDER / API_KEY / API_BASE / MODEL_NAME / LLM_SSL_VERIFY`。

### 6.2 边界与已知风险（诚实标注）

- **冒烟测试绕过 queue→dispatch→事件路由。** 它只覆盖"适配器 + 框架"这一层，
  **不覆盖** engine 调度骨架。调度骨架需独立测试（见 §6.3）。
- **真 LLM 不适合当 CI 回归门禁**：不确定性输出、需密钥、限流、超时。冒烟定位为
  "手动/夜间打 tag 的可行性证明"，不进默认门禁。
- **回归门禁缺口**：本补丁主验证手段不含确定性回归测试。框架的 `Model`（可继承）+
  `ReActAgent.setLlm(Model)` 提供了离线替身能力，可在后续补一层确定性测试守回归。
  本补丁记录此缺口，不在第一版强制落地。

### 6.3 engine 调度骨架验证（范围说明）

主设计第 7–8 章的 queue→dispatch→事件路由链，用 in-memory 队列 + `FakeAgentHandler`
（吐编排好的事件序列）+ mock 的 `TaskControlClient`/`AccessLayerClient` 验证。此层
与 openJiuwen 无关，独立于本补丁，仅在此标注它是与适配器验证并列的另一件事。

## 7. 不做项（YAGNI）

- 不为 openJiuwen 引入 Reactor 流式适配（框架产出 `Iterator`，非 Flux）。
- 不在第一版落地离线确定性回归测试（记录为 §6.2 缺口）。
- 不展开 `interrupt` 分支的 `InterruptType`/`prompt` 细化（属主设计 Phase 2）。
- 不改动主设计第 4–9、11–13 章建模。
