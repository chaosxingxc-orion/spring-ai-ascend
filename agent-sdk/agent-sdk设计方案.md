# Agent SDK 设计方案

## 1. 模块定位

`agent-sdk` 是一个面向客户侧配置的轻量装配 SDK。当前只支持 OpenJiuwen，职责收敛为：

1. 读取 `ascend-agent/v1` YAML。
2. 解析为内部 `AgentSpec`。
3. 按调用方选择，显式装配为 OpenJiuwen `ReActAgent` 或 `DeepAgent`。
4. 将 YAML 声明的 Java / HTTP tool 转为 OpenJiuwen `Tool`。
5. 将本地 filesystem skill 目录注册到 OpenJiuwen agent。

依赖边界：

| 依赖 | 版本 / 作用 |
|---|---|
| `com.openjiuwen:agent-core-java` | `0.1.12`，提供 `ReActAgent`、`DeepAgent`、`DeepAgentConfig`、`HarnessFactory`、`Tool`、`ToolCard`、`LocalFunction`、`SkillUseRail` 等 OpenJiuwen API。 |
| `snakeyaml` | 读取 YAML。 |
| `jackson-databind` | 处理参数结构。 |

## 2. 对外 API

SDK 只暴露两个 YAML 转 agent 的显式入口：

```java
public final class AgentFactory {
    public static ReActAgent toReactAgent(Path yamlPath);

    public static DeepAgent toDeepAgent(Path yamlPath);

    public static AgentFactoryBuilder builder();
}
```

Builder 保留扩展点：

```java
ReActAgent reactAgent = AgentFactory.builder()
    .toolResolver(customResolver)
    .toReactAgent(Path.of("openjiuwen/agent.yaml"));

DeepAgent deepAgent = AgentFactory.builder()
    .toolResolver(customResolver)
    .toDeepAgent(Path.of("openjiuwen/deepagent.yaml"));
```

调用方需要哪一种 agent，就显式调用哪一个方法；SDK 不提供返回 `Object` 的泛化入口。

## 3. 转换流程

ReActAgent：

```text
agent.yaml
  -> AgentYamlLoader
  -> AgentSpec
  -> AgentFactory.toReactAgent(...)
  -> OpenJiuwenReactAgentBuilder
  -> ReActAgent
```

DeepAgent：

```text
deepagent.yaml
  -> AgentYamlLoader
  -> AgentSpec
  -> AgentFactory.toDeepAgent(...)
  -> OpenJiuwenDeepAgentBuilder
  -> DeepAgent
```

## 4. YAML 设计

YAML 使用 `ascend-agent/v1`。它是客户侧 agent 装配格式，不是完整工作流 DSL。

| YAML 字段 | AgentSpec 字段 | 说明 |
|---|---|---|
| `schema` | `schema` | 当前为 `ascend-agent/v1`。 |
| `name` | `name` | 稳定标识符；映射为 OpenJiuwen `AgentCard.id`。 |
| `displayName` | `displayName` | 映射为 OpenJiuwen `AgentCard.name`；为空时默认取 `name`。 |
| `description` | `description` | 映射为 OpenJiuwen `AgentCard.description`。 |
| `framework.type` | `frameworkType` | 当前只支持 `openjiuwen`。 |
| `framework.agent` | `agentType` | 当前支持 `react` 和 `deepagent`。 |
| `framework.options` | `frameworkOptions` | OpenJiuwen 特有选项，如 `maxIterations`、`sysOperationId`。 |
| `model` | `modelSpec` | provider、name、baseUrl、apiKey、sslVerify、headers。 |
| `prompt` | `promptSpec` | system prompt。 |
| `skills.sources` | `skillSources` | 本地 filesystem skill 目录。 |
| `tools` | `toolSpecs` | 工具声明列表。 |

环境变量解析规则：

1. YAML 中的 `${ENV_NAME}` 由 `AgentYamlEnvironmentResolver` 解析。
2. 环境变量不存在时抛出 `ValidationException`。
3. 示例 YAML 可以显式写 `apiKey` 便于真实调用；生产环境按客户自身安全规范管理密钥。

## 5. ReActAgent 装配

示例：

```yaml
schema: ascend-agent/v1
name: sdk-openjiuwen-example-agent
displayName: SDK OpenJiuwen Example Agent
description: Demonstrates YAML to OpenJiuwen ReActAgent.

framework:
  type: openjiuwen
  agent: react
  options:
    maxIterations: 6
    sysOperationId: sdk-openjiuwen-example-agent

model:
  provider: OpenAI
  name: deepseek-chat
  baseUrl: https://api.deepseek.com
  apiKey: sk-your-deepseek-api-key
  sslVerify: true

prompt:
  system: |
    你是一个订单助手。

skills:
  sources:
    - ../skills/order-analysis
    - ../skills/report-writing

tools:
  - name: queryOrder
    description: 查询本地示例订单状态。
    inputSchema:
      type: object
      properties:
        orderId:
          type: string
      required:
        - orderId
    ref:
      type: file
      class: QueryOrderTool
      method: query
```

落点：

| YAML 字段 | OpenJiuwen 落点 |
|---|---|
| `name`、`displayName`、`description` | `AgentCard`。 |
| `prompt.system` | `ReActAgentConfig.configurePromptTemplate(...)`。 |
| `model.*` | `ReActAgentConfig.configureModelClient(...)`。 |
| `framework.options.maxIterations` | `ReActAgentConfig.configureMaxIterations(...)`。 |
| `framework.options.sysOperationId` | `ReActAgentConfig.sysOperationId`；默认使用 `name`。 |
| `tools` | 转成 `Tool`，写入 `AbilityManager`，并通过 `Runner.resourceMgr().addTool(tool, agentId)` 注册。 |
| `skills.sources` | 调用 `ReActAgent.registerSkill(skillDirectory)`。 |

## 6. DeepAgent 装配

示例：

```yaml
schema: ascend-agent/v1
name: sdk-openjiuwen-deepagent-example-agent
displayName: SDK OpenJiuwen DeepAgent Example Agent
description: Demonstrates YAML to OpenJiuwen DeepAgent.

framework:
  type: openjiuwen
  agent: deepagent
  options:
    maxIterations: 8

model:
  provider: OpenAI
  name: deepseek-chat
  baseUrl: https://api.deepseek.com
  apiKey: sk-your-deepseek-api-key
  sslVerify: true

prompt:
  system: |
    你是一个订单分析 DeepAgent。

skills:
  sources:
    - ../skills/order-analysis
    - ../skills/report-writing

tools:
  - name: calcDiscount
    description: 计算示例折扣。
    inputSchema:
      type: object
      properties:
        amount:
          type: number
      required:
        - amount
    ref:
      type: file
      class: CalcDiscountTool
      method: calculate
```

落点：

| YAML 字段 | OpenJiuwen 0.1.12 落点 |
|---|---|
| `name`、`displayName`、`description` | `AgentCard`。 |
| `prompt.system` | `DeepAgentConfig.systemPrompt`。 |
| `framework.options.maxIterations` | `DeepAgentConfig.maxIterations`。 |
| `model.name` | `DeepAgentConfig.model` map 中的 `model`。 |
| `model.provider`、`apiKey`、`baseUrl`、`sslVerify`、`headers` | `DeepAgentConfig.backend` map。 |
| `tools` | 转成 OpenJiuwen `Tool`，放入 `DeepAgentConfig.tools`。 |
| `skills.sources` | 归并为 skill 根目录，写入 `DeepAgentConfig.skillDirectories`；OpenJiuwen `SkillUseRail` 通过 `skillsRoot/skillName/SKILL.md` 读取。 |

构造路径：

```text
AgentSpec
  -> DeepAgentConfig
  -> Workspace
  -> HarnessFactory.createDeepAgent(card, config, workspace)
  -> DeepAgent
```

## 7. Tool 和 Skill

### 7.1 Tool

公开 YAML 的 Java tool 引用不写 source `path`，只写 class 和 method：

```yaml
ref:
  type: file
  class: QueryOrderTool
  method: query
```

当前 resolver：

| resolver | 说明 |
|---|---|
| `JavaFileToolResolver` | 将 classpath 上的静态 Java 方法包装成 OpenJiuwen `LocalFunction`。 |
| `HttpToolResolver` | 将 HTTP tool 声明包装为 SDK 的 HTTP execution handle。 |
| custom `ToolResolver` | 通过 `AgentFactory.builder().toolResolver(...)` 注入。 |

### 7.2 Skill

当前只支持本地 filesystem skill：

```yaml
skills:
  sources:
    - ../skills/order-analysis
    - ../skills/report-writing
```

约束：

1. 每个 skill 目录必须包含 `SKILL.md`。
2. 路径相对 YAML 所在目录解析。
3. ReActAgent 调用 `registerSkill(...)`。
4. DeepAgent 写入 skill 根目录到 `DeepAgentConfig.skillDirectories`，由 OpenJiuwen `SkillUseRail` 注册 `list_skill` / `skill_tool` 并读取 `skillsRoot/skillName/SKILL.md`。

## 8. 包结构

```text
agent-sdk/
  pom.xml
  agent-sdk设计方案.md
  src/main/java/com/huawei/ascend/agentsdk/
    factory/
      AgentFactory.java
      AgentFactoryBuilder.java
    adapter/
      OpenJiuwenAgentSpecMapper.java
      OpenJiuwenSkillMapper.java
      OpenJiuwenToolMapper.java
      react/
        OpenJiuwenReactAgentBuilder.java
        OpenJiuwenReactOptions.java
      deepagent/
        OpenJiuwenDeepAgentBuilder.java
        OpenJiuwenDeepAgentOptions.java
    spec/
      AgentSpec.java
      yaml/
      model/
      prompt/
      skill/
      tool/
    support/
```

## 9. Example

示例项目：

```text
examples/agent-sdk-example/
  openjiuwen/
    agent.yaml
    deepagent.yaml
  skills/
    order-analysis/SKILL.md
    report-writing/SKILL.md
  src/main/java/com/huawei/ascend/agentsdk/example/
    OpenJiuwenReactAgentSdkExample.java
    OpenJiuwenDeepAgentSdkExample.java
    OpenJiuwenExampleSupport.java
    tools/
      ReadFileTool.java
      QueryOrderTool.java
      CalcDiscountTool.java
```

示例只展示：

```text
YAML -> ReActAgent
YAML -> DeepAgent
```

示例验证目标：

1. 通过 YAML 构造 `ReActAgent` 和 `DeepAgent`。
2. 使用真实大模型调用默认 DeepSeek OpenAI-compatible endpoint；ReAct 示例调用 `ReActAgent.invoke(...)`，DeepAgent 示例调用 `DeepAgent.run(...)` 驱动真实执行。
3. 自定义 Java tool `queryOrder`、`calcDiscount` 被真实调用，并返回 proof 字段。
4. ReAct 示例通过自定义 Java tool `readFile` 读取本地 `SKILL.md`，证明 skill 文件被真实使用；DeepAgent 示例通过 OpenJiuwen 原生 `skill_tool` 读取 `skills/<skillName>/SKILL.md`。
5. 本地 skill `order-analysis`、`report-writing` 被注入，并要求最终回答输出独有 skill proof 标记。
6. 示例 main 在运行结束时自动校验 tool invocation count、tool proof、skill proof；ReAct 额外校验 `readFile` 调用次数，校验失败则抛出异常。

## 10. 当前结论

1. `agent-sdk` 当前只负责 OpenJiuwen YAML 到 OpenJiuwen agent 实例的客户侧装配。
2. `agent-core-java` 依赖版本为 `0.1.12`。
3. 对外 API 固定为 `AgentFactory.toReactAgent(Path)`、`AgentFactory.toDeepAgent(Path)` 和 builder。
4. ReActAgent 与 DeepAgent 使用同一套 YAML 主字段，差异由 `framework.agent` 和对应 OpenJiuwen 落点决定。
5. 公开 YAML 的 Java tool ref 使用 `type + class + method`，不使用 tool `path`。
