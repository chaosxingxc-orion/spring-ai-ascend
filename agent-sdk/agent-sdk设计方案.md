# Agent SDK 设计说明

> 本文按当前代码实现刷新。早期方案中的 AgentScope 适配、AdapterProvider / AdapterRegistry、`adapter/openjiuwen/` 过渡目录、`fromAgent(...)` 入口和 tool `path` 公开写法已经不再作为当前设计。

## 1. 模块定位

`agent-sdk` 是客户侧轻量转换 SDK，负责把 OpenJiuwen YAML 或已有 OpenJiuwen Agent 实例转换成 `agent-runtime` 可调度的 `AgentRuntimeHandler`。

当前边界很窄：

1. SDK 负责 YAML 解析、`AgentSpec` 归一、OpenJiuwen ReAct / DeepAgent 装配、tool / skill 注册和 handler 包装。
2. SDK 不启动 Spring Boot，不做服务注册发现，不替代 `agent-runtime` / `agent-service` 的部署职责。
3. SDK 不重新定义 runtime handler 契约，最终返回 `com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler`。
4. 当前只适配 OpenJiuwen，且只支持 `framework.agent=react` 与 `framework.agent=deepagent`。

运行时契约由 `agent-runtime` 提供：

| 类型 | 作用 |
|---|---|
| `AgentRuntimeHandler` | runtime 调度统一入口，公开 `agentId()`、`isHealthy()`、`execute(AgentExecutionContext)`、`resultAdapter()`。 |
| `OpenJiuwenAgentRuntimeHandler` | runtime 中的 OpenJiuwen handler 基类，SDK 的 ReAct / DeepAgent adapter 继承它。 |
| `AgentRuntimeHandlerRegistry` | runtime 内部按 `agentId` 注册和查找 handler；SDK 只构造 handler，不管理 registry 生命周期。 |

## 2. 转换流程

YAML 一步到 handler：

```text
agent.yaml
  -> AgentYamlLoader
  -> AgentSpec
  -> AgentHandlerFactoryBuilder.fromYaml(...)
  -> toReactAgent(spec) / toDeepAgent(spec)
  -> toHandler(spec.name(), agent)
  -> AgentRuntimeHandler
```

分步调用：

```text
agent.yaml
  -> AgentHandlerFactory.toReactAgent(...) / toDeepAgent(...)
  -> ReActAgent / DeepAgent
  -> AgentHandlerFactory.toHandler(name, agent)
  -> AgentRuntimeHandler
```

已有实例路径：

```text
(name, ReActAgent | DeepAgent)
  -> AgentHandlerFactory.toHandler(name, agent)
  -> OpenJiuwenReactAgentHandlerAdapter / OpenJiuwenDeepAgentHandlerAdapter
  -> AgentRuntimeHandler
```

`toHandler(String, Object)` 内部按实例类型判断：

1. `ReActAgent` 调用 `OpenJiuwenReactAgentBuilder.toHandler(...)`。
2. `DeepAgent` 调用 `OpenJiuwenDeepAgentBuilder.toHandler(...)`。
3. 其他对象抛出 `UnsupportedFrameworkException`。

因此不再需要两个公开的“React 转 handler / DeepAgent 转 handler”函数，客户只调用统一的 `toHandler(...)`。

## 3. 对外 API

当前公开入口在 `com.huawei.ascend.agentsdk.factory.AgentHandlerFactory`：

```java
public final class AgentHandlerFactory {
    public static AgentRuntimeHandler fromYaml(Path yamlPath);

    public static ReActAgent toReactAgent(Path yamlPath);

    public static DeepAgent toDeepAgent(Path yamlPath);

    public static AgentRuntimeHandler toHandler(String name, Object agent);

    public static AgentHandlerFactoryBuilder builder();
}
```

客户可以一次性从 YAML 得到 runtime handler：

```java
AgentRuntimeHandler handler = AgentHandlerFactory.fromYaml(Path.of("openjiuwen/agent.yaml"));
```

也可以显式分两步：

```java
ReActAgent reactAgent = AgentHandlerFactory.toReactAgent(Path.of("openjiuwen/agent.yaml"));
AgentRuntimeHandler reactHandler = AgentHandlerFactory.toHandler("order-agent", reactAgent);

DeepAgent deepAgent = AgentHandlerFactory.toDeepAgent(Path.of("openjiuwen/deepagent.yaml"));
AgentRuntimeHandler deepHandler = AgentHandlerFactory.toHandler("deep-agent", deepAgent);
```

高级入口当前只保留已实现扩展点：

```java
AgentRuntimeHandler handler = AgentHandlerFactory.builder()
    .toolResolver(customResolver)
    .cacheRoot(Path.of("/data/agent-sdk-cache"))
    .fromYaml(Path.of("openjiuwen/agent.yaml"));
```

说明：

1. `fromYaml(Path)` 会读取 YAML，并根据 `framework.agent` 分派到 ReAct 或 DeepAgent。
2. `toReactAgent(Path)` 只接受 `framework.type=openjiuwen` 且 `framework.agent=react` 的 YAML。
3. `toDeepAgent(Path)` 只接受 `framework.type=openjiuwen` 且 `framework.agent=deepagent` 的 YAML。
4. `toHandler(String, Object)` 不再要求客户传 `frameworkType` / `agentType`，因为对象实例本身已经能表达类型。
5. 当前 builder 支持自定义 `ToolResolver` 和 `cacheRoot`；没有公开 `modelResolver`。

## 4. YAML 设计

YAML 使用 `ascend-agent/v1`。它是客户侧装配格式，不承载 Java 代码，也不是完整 no-code / workflow DSL。

### 4.1 字段映射

| YAML 字段 | AgentSpec 字段 | 说明 |
|---|---|---|
| `schema` | `schema` | 当前为 `ascend-agent/v1`。 |
| `name` | `name` | 稳定标识符，用作 runtime `agentId`，也是 ReAct 默认 `sysOperationId`。 |
| `displayName` | `displayName` | 展示名；空值会默认取 `name`。 |
| `description` | `description` | 映射到 OpenJiuwen `AgentCard.description`。 |
| `metadata` | `metadata` | 可选扩展元信息。 |
| `cacheRoot` | `cacheRoot` | 可选；当前预留给 `localCache` 资源物化。 |
| `framework.type` | `frameworkType` | 当前只支持 `openjiuwen`。 |
| `framework.agent` | `agentType` | 当前支持 `react`、`deepagent`。 |
| `framework.options` | `frameworkOptions` | OpenJiuwen 特有配置，例如 `executeMode`、`maxIterations`、`sysOperationId`。 |
| `model` | `modelSpec` | 模型 provider、name、baseUrl、apiKey、sslVerify、headers。 |
| `prompt` | `promptSpec` | system prompt。 |
| `skills.sources` | `skillSources` | 本地 filesystem skill 目录来源。 |
| `tools` | `toolSpecs` | 工具声明列表。 |

环境变量解析规则：

1. YAML 中的 `${ENV_NAME}` 由 `AgentYamlEnvironmentResolver` 解析。
2. 环境变量不存在时抛出 `ValidationException`。
3. 示例 YAML 为了便于真实调用，当前显式写入 `apiKey` 字段；生产配置仍建议由客户按自身安全规范管理密钥。

### 4.2 OpenJiuwen ReAct 示例

```yaml
schema: ascend-agent/v1

name: sdk-openjiuwen-example-agent
displayName: SDK OpenJiuwen Example Agent
description: Demonstrates YAML to OpenJiuwen ReAct runtime handler.

framework:
  type: openjiuwen
  agent: react
  options:
    executeMode: openjiuwen
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
    outputSchema:
      type: object
      properties:
        status:
          type: string
        proof:
          type: string
    ref:
      type: file
      class: QueryOrderTool
      method: query
```

### 4.3 OpenJiuwen DeepAgent 示例

```yaml
schema: ascend-agent/v1

name: sdk-openjiuwen-deepagent-example-agent
displayName: SDK OpenJiuwen DeepAgent Example Agent
description: Demonstrates YAML to OpenJiuwen DeepAgent runtime handler.

framework:
  type: openjiuwen
  agent: deepagent
  options:
    executeMode: openjiuwen
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
    outputSchema:
      type: object
      properties:
        discount:
          type: number
        proof:
          type: string
    ref:
      type: file
      class: CalcDiscountTool
      method: calculate
```

ReAct 和 DeepAgent 可以使用同一套 YAML 字段；示例目录保留两个 YAML，是为了分别固定 `framework.agent`、`maxIterations`、prompt 和 main class 的运行入口。

## 5. Tool 规则

`tools` 表示可被 Agent 调用的函数能力声明。YAML 描述工具名、描述、schema 和实现引用；工具执行逻辑不写在 YAML 里。

推荐字段：

| 字段 | 作用 |
|---|---|
| `name` | 暴露给 Agent 的工具名。 |
| `description` | 工具能力描述。 |
| `inputSchema` | 工具入参 JSON Schema。 |
| `outputSchema` | 工具出参结构说明。 |
| `ref` | 工具真实实现引用，由 `ToolResolver` 解析。 |
| `localCache` | 可选，默认 `false`；当前缓存物化能力仍是预留路径。 |

当前内置 resolver 顺序：

1. 自定义 resolver，按 builder 注入顺序优先。
2. `JavaFileToolResolver`，处理 `ref.type=file`。
3. `HttpToolResolver`，处理 `ref.type=http`。
4. `McpToolResolver`，处理 `ref.type=mcp`。

公开 YAML 的 Java 工具引用不再写 `path`，只写 class 和 method：

```yaml
ref:
  type: file
  class: QueryOrderTool
  method: query
```

`JavaFileToolResolver` 的规则：

1. `class` 和 `method` 必填，缺失时在 handler 创建阶段抛出 `ValidationException`。
2. `class` 必须已经在运行 classpath 中。
3. resolver 不读取 Java 源文件，不要求 `path`。
4. 示例项目通过 `build-helper-maven-plugin` 把 `examples/agent-sdk-example/tools` 加入编译 source root。

工具注册链路：

```text
ToolSpec
  -> ToolResolver
  -> ResolvedTool
  -> OpenJiuwenToolMapper
  -> com.openjiuwen.core.foundation.tool.Tool
  -> ReAct / DeepAgent registration
```

ReAct 注册方式：

1. `agent.getAbilityManager().add(tool.getCard())`
2. `Runner.resourceMgr().addTool(tool, agentId, true)`

DeepAgent 注册方式：

1. 将工具转换成 OpenJiuwen `Tool`。
2. 放入 `DeepAgentConfig.tools(configTools)`。

`NativeTool` 仍作为自定义 resolver 的逃生入口；内置 `file/http/mcp` resolver 默认产出 `WrappableTool`。

## 6. Skill 规则

`skills.sources` 当前只支持本地 filesystem 目录。字符串简写默认表示 `type=filesystem`、`localCache=false`：

```yaml
skills:
  sources:
    - ../skills/order-analysis
```

等价对象写法：

```yaml
skills:
  sources:
    - type: filesystem
      path: ../skills/order-analysis
      localCache: false
```

目录解析规则：

1. 目录自身存在 `SKILL.md` 时，该目录就是一个 skill。
2. 目录自身不存在 `SKILL.md` 时，扫描一级子目录，每个包含 `SKILL.md` 的子目录是一个 skill。
3. 目录自身和子目录同时存在 `SKILL.md` 时视为结构冲突。
4. 不做无限递归。

OpenJiuwen 注册方式：

| Agent 类型 | 落点 |
|---|---|
| ReAct | `OpenJiuwenSkillMapper.toSkillDirectories(...)` 后逐个调用 `agent.registerSkill(skillDirectory)`。 |
| DeepAgent | 写入 `DeepAgentConfig.skillDirectories(skillDirs)`，并追加 `new SkillUseRail(skillDirs, "all")`。 |

SDK 不把 skill 强行展开成 system prompt；skill 的读取、列表和注入由 OpenJiuwen 原生机制完成。

## 7. 目录层级

当前实现目录：

```text
agent-sdk/
  pom.xml
  agent-sdk设计方案.md
  src/
    main/
      java/
        com/huawei/ascend/agentsdk/
          factory/
            AgentHandlerFactory.java
            AgentHandlerFactoryBuilder.java
          spec/
            AgentSpec.java
            yaml/
              AgentYamlLoader.java
              AgentYamlParser.java
              AgentYamlEnvironmentResolver.java
            model/
              ModelSpec.java
              ModelResolver.java
              ModelResolutionException.java
            prompt/
              PromptSpec.java
              PromptLoader.java
            skill/
              SkillSpec.java
              SkillSourceSpec.java
              SkillSourceLoader.java
            tool/
              ToolSpec.java
              ToolResolver.java
              ResolvedTool.java
              NativeTool.java
              WrappableTool.java
              ToolDescriptor.java
              ExecutionHandle.java
              JavaExecutionHandle.java
              HttpExecutionHandle.java
              McpExecutionHandle.java
              JavaFileToolResolver.java
              HttpToolResolver.java
              McpToolResolver.java
              UnsupportedToolRefException.java
          adapter/
            OpenJiuwenAgentSpecMapper.java
            OpenJiuwenToolMapper.java
            OpenJiuwenSkillMapper.java
            react/
              OpenJiuwenReactAgentBuilder.java
              OpenJiuwenReactAgentHandlerAdapter.java
              OpenJiuwenReactOptions.java
              OpenJiuwenRuntimeProof.java
            deepagent/
              OpenJiuwenDeepAgentBuilder.java
              OpenJiuwenDeepAgentHandlerAdapter.java
              OpenJiuwenDeepAgentOptions.java
          support/
            AgentSdkException.java
            ValidationException.java
            UnsupportedFrameworkException.java
            cache/
              ResourceLocalCache.java
              LocalCacheException.java
```

目录边界：

1. `factory/` 是客户入口。
2. `spec/` 是框架无关输入规格层。
3. `adapter/` 是 OpenJiuwen 适配实现目录；当前不再保留 `adapter/openjiuwen/` 过渡层。
4. `adapter/react/` 和 `adapter/deepagent/` 分别承载两种 OpenJiuwen agent 类型。
5. `support/` 承载异常和通用支撑。
6. `src/main/resources` 当前没有内容，已不作为必要目录。

## 8. OpenJiuwen 映射

### 8.1 ReAct

```text
YAML
  -> AgentSpec
  -> AgentCard
  -> ReActAgentConfig
  -> ReActAgent
  -> OpenJiuwenReactAgentHandlerAdapter extends OpenJiuwenAgentRuntimeHandler
```

字段映射：

| YAML 字段 | OpenJiuwen 落点 |
|---|---|
| `name` | `AgentCard.id`、`AgentCard.name`、runtime `agentId`。 |
| `description` | `AgentCard.description`。 |
| `prompt.system` | `ReActAgentConfig.promptTemplate`。 |
| `model.provider`、`model.apiKey`、`model.baseUrl`、`model.name`、`model.sslVerify`、`model.headers` | `ReActAgentConfig.configureModelClient(...)`。 |
| `framework.options.maxIterations` | `ReActAgentConfig.maxIterations`。 |
| `framework.options.sysOperationId` | `ReActAgentConfig.sysOperationId`；默认使用 `AgentSpec.name`。 |
| `framework.options.executeMode` | `sdk-proof` 时走本地 proof 辅助逻辑，否则真实调用 OpenJiuwen。 |
| `skills.sources` | `ReActAgent.registerSkill(...)`。 |
| `tools` | 注册到 `AbilityManager` 和 `Runner.resourceMgr()`。 |

### 8.2 DeepAgent

```text
YAML
  -> AgentSpec
  -> AgentCard
  -> DeepAgentConfig
  -> DeepAgent
  -> OpenJiuwenDeepAgentHandlerAdapter extends OpenJiuwenAgentRuntimeHandler
```

字段映射：

| YAML 字段 | OpenJiuwen 落点 |
|---|---|
| `name` | `AgentCard.id`、`AgentCard.name`、runtime `agentId`。 |
| `description` | `AgentCard.description`。 |
| `prompt.system` | `DeepAgentConfig.systemPrompt`。 |
| `model.name` | `DeepAgentConfig.model`。 |
| `model.provider`、`model.apiKey`、`model.baseUrl`、`model.sslVerify` | `DeepAgentConfig.backend`。 |
| `framework.options.maxIterations` | `DeepAgentConfig.maxIterations`。 |
| `framework.options.executeMode` | `sdk-proof` 时走本地 proof 辅助逻辑，否则真实调用 OpenJiuwen。 |
| `skills.sources` | `DeepAgentConfig.skillDirectories` + `SkillUseRail`。 |
| `tools` | `DeepAgentConfig.tools`；当前 OpenJiuwen 公开 0.1.12 未发布 DeepAgent API，代码中真实装配临时注释，先用占位 handler 保证编译和 proof 链路。 |

DeepAgent handler 调用 `deepAgent.run(input)`，不直接操作 DeepAgent 内部的 ReActAgent。

## 9. Example

示例模块位于 `examples/agent-sdk-example`：

```text
examples/agent-sdk-example/
  pom.xml
  README.md
  openjiuwen/
    agent.yaml
    deepagent.yaml
  skills/
    order-analysis/SKILL.md
    report-writing/SKILL.md
  tools/
    QueryOrderTool.java
    CalcDiscountTool.java
  src/main/java/com/huawei/ascend/agentsdk/example/
    OpenJiuwenReactAgentSdkExample.java
    OpenJiuwenDeepAgentSdkExample.java
    OpenJiuwenExampleSupport.java
```

两个 main 都采用分步调用方式：

```text
YAML
  -> toReactAgent(...) / toDeepAgent(...)
  -> toHandler(...)
  -> handler.execute(context)
  -> resultAdapter().adapt(...)
```

默认 `executeMode=openjiuwen`，执行真实大模型调用。需要避免远程调用时，运行时加：

```bash
-Dopenjiuwen.example.proof=true
```

proof mode 会临时把 YAML 改为 `executeMode=sdk-proof`，用于验证 handler、tool 和 skill 装配链路。

## 10. 设计结论

1. `agent-sdk` 的职责是 OpenJiuwen YAML / Agent 实例到 runtime `AgentRuntimeHandler` 的客户侧转换。
2. 对外 API 固定为 `fromYaml(Path)`、`toReactAgent(Path)`、`toDeepAgent(Path)`、`toHandler(String,Object)` 和 builder。
3. `fromYaml(Path)` 是分步能力的顺序组合，不是独立第三套逻辑。
4. ReAct / DeepAgent 到 handler 的公开转换统一为 `toHandler(String,Object)`，内部按对象实例类型分派。
5. 当前只支持 OpenJiuwen，不保留 AgentScope example 或 adapter 占位。
6. `adapter/` 下直接放 OpenJiuwen 通用 mapper，并按 `react/`、`deepagent/` 分子目录，不再套 `adapter/openjiuwen/`。
7. 公开 YAML 的 Java tool ref 使用 `type + class + method`，不再使用 tool `path`。
