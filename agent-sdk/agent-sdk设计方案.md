# Agent SDK 设计说明

> 当前目录用于沉淀 `agent-sdk` 的模块设计。本文先定义目标边界、对外 API、YAML 形态、目录层级和文件职责；Java 实现文件在方案确认后再按本文结构落地。

## 1. 模块定位、边界与转换流程

`agent-sdk` 是面向客户代码使用的轻量 SDK，负责把客户提供的 Agent 描述或 Agent 实例转换成 `agent-runtime` 能启动和调度的 `AgentHandler`。

它的职责边界是转换层：接收 YAML 或已有 Agent 实例，识别目标框架和 Agent 类型，然后交给对应框架的 runtime Handler 能力完成包装。

这里需要明确区分两类对象：

1. `AgentHandler` 是 `agent-runtime` 暴露给运行时调度的统一 Handler 类型。
2. `OpenJiuwenAgentHandler` 这类框架 Handler 基类由 `agent-runtime` 定义和提供，`agent-sdk` 不重新定义 runtime Handler 契约，只负责根据客户输入创建可交给 runtime 调度的具体 Handler 实例。

`agent-sdk` 的转换职责包括：

1. 读取 YAML，解析出统一的 `AgentSpec`。
2. 根据 `framework.type` 和 `framework.agent` 选择内置框架适配器。
3. 基于 `agent-runtime` 提供的 Handler 基类或适配契约，创建最终的 `AgentHandler`。

YAML 路径：

```text
agent.yaml
  -> AgentYamlLoader
  -> AgentSpec
  -> AdapterRegistry
  -> OpenJiuwen / AgentScope Adapter
  -> concrete runtime AgentHandler
  -> AgentHandler
```

已有实例路径：

```text
(name, frameworkType, agentType, agent)
  -> AgentInstance
  -> AdapterRegistry
  -> OpenJiuwen / AgentScope Adapter
  -> concrete runtime AgentHandler
  -> AgentHandler
```

这里的关键点是：客户最终拿到的永远是 `AgentHandler`，服务如何启动仍由 `agent-runtime` 或客户自己的应用决定。

## 2. 对外 API

第一版建议只暴露一个主入口类：

```java
AgentHandler handler = AgentHandlerFactory.fromYaml(Path.of("agent.yaml"));
```

已有 Agent 实例的路径：

```java
AgentHandler handler = AgentHandlerFactory.fromAgent(
    "order-agent",
    "openjiuwen",
    "react",
    reactAgent
);
```

`AgentHandlerFactory` 不要求客户实例化。客户只需要传 YAML 路径或已有 Agent 实例，就能拿到 `AgentHandler`。

`fromAgent(...)` 不引入额外的 `Framework<T>` 泛型抽象，第一版保持和 YAML 一致的 `frameworkType` + `agentType` 明确参数，避免为了类型安全提前引入新的框架描述模型。

`fromAgent(...)` 适用于客户已经自行完成框架配置的 Agent 实例。SDK 只负责根据 `name`、`frameworkType`、`agentType` 把该实例包装成 `AgentHandler`，不额外补充 `model`、`prompt`、`tools`、`skills` 配置。如果客户希望由 SDK 根据描述组装这些配置，应使用 `fromYaml(...)`。

对应目标签名：

```java
public final class AgentHandlerFactory {
    public static AgentHandler fromYaml(Path yamlPath);

    public static AgentHandler fromAgent(
        String name,
        String frameworkType,
        String agentType,
        Object agent
    );
}
```

后续如果需要高级场景，再补 builder：

```java
AgentHandler handler = AgentHandlerFactory.builder()
    .toolResolver(toolResolver)
    .modelResolver(modelResolver)
    .cacheRoot(Path.of("/data/agent-sdk-cache"))
    .fromYaml(Path.of("agent.yaml"));
```

高级场景的用途主要是：

1. 自定义 tool 解析方式，例如从客户自己的注册中心拿工具。
2. 自定义 model 解析方式，例如把 YAML 中的模型配置转换成客户已有的模型客户端。
3. 测试时替换模型、工具、远端调用客户端。

## 3. YAML 设计

推荐对客户暴露更简洁的 YAML，不直接照搬更重的内部定义模型。SDK 内部可以把它转换成 `AgentSpec`，再交给具体框架适配器。

### AgentSpec 字段映射

`AgentSpec` 是 SDK 内部的统一描述模型。YAML 字段进入适配器前先归一到 `AgentSpec`，避免每个框架适配器重复解析 YAML。

| YAML 字段 | AgentSpec 字段 | 说明 |
|---|---|---|
| `schema` | `schema` | 配置版本，第一版为 `ascend-agent/v1`。 |
| `name` | `name` | 稳定标识符，用于 runtime `agentId`、缓存目录分组和默认 `sysOperationId`。 |
| `displayName` | `displayName` | 展示名，可选；`AgentYamlParser` 在解析阶段把空值填充为 `name`。 |
| `description` | `description` | Agent 描述，会映射到框架 AgentCard，面向人、协议发现和模型理解。 |
| `metadata` | `metadata` | 可选扩展元信息，不参与核心调度语义。 |
| `cacheRoot` | `cacheRoot` | 可选，本 YAML 对应资源的本地缓存根目录；只影响 `localCache: true` 的资源。 |
| `framework.type` | `frameworkType` | 框架类型，例如 `openjiuwen`。 |
| `framework.agent` | `agentType` | 同一框架下的 Agent 类型，例如 `react`、`deepagent`。 |
| `framework.options` | `frameworkOptions` | 框架特有配置，例如 `maxIterations`、`sysOperationId`。 |
| `model` | `modelSpec` | 模型配置，第一版建议包含 `provider`、`name`、`baseUrl`、`apiKey`、`sslVerify`。 |
| `prompt` | `promptSpec` | system prompt、模板 prompt 或外部 prompt 引用。 |
| `skills.sources` | `skillSources` | 技能来源，字符串简写会归一成 `SkillSourceSpec(type=filesystem, path, localCache=false)`。 |
| `tools` | `toolSpecs` | 工具声明列表。 |

OpenJiuwen React 需要稳定 `sysOperationId` 初始化 skill 能力。规则是：默认 `sysOperationId = AgentSpec.name`；客户可以用 `framework.options.sysOperationId` 覆盖。

### OpenJiuwen React

```yaml
schema: ascend-agent/v1

name: order-assistant
displayName: 订单助手
description: 订单助手
cacheRoot: ./cache/order-assistant
metadata:
  version: "1.0"
  owner: order-team

framework:
  type: openjiuwen
  agent: react

model:
  provider: openai-compatible
  name: deepseek-chat
  baseUrl: ${DEEPSEEK_BASE_URL}
  apiKey: ${DEEPSEEK_API_KEY}
  sslVerify: true

prompt:
  system: |
    你是一个订单助手，负责查询订单状态并给出简洁回复。

skills:
  sources:
    - ./skills/common
    # 对象写法可声明来源类型并覆盖 localCache；字符串简写默认 type: filesystem、localCache: false。
    - type: filesystem
      path: ./skills/order
      localCache: true

tools:
  - name: queryOrder
    description: 查询订单状态。
    inputSchema:
      type: object
      properties:
        orderId:
          type: string
          description: 订单号。
      required:
        - orderId
    outputSchema:
      type: object
      properties:
        status:
          type: string
          description: 订单状态。
        updatedAt:
          type: string
          description: 最近更新时间。
    ref: file:./tools/query-order-tool.yaml
    localCache: true
```

### OpenJiuwen DeepAgent

```yaml
schema: ascend-agent/v1

name: research-agent
displayName: 深度研究助手
description: 深度研究助手
cacheRoot: ./cache/research-agent
metadata:
  version: "1.0"
  owner: research-team

framework:
  type: openjiuwen
  agent: deepagent

model:
  provider: openai-compatible
  name: qwen-plus
  baseUrl: ${MODEL_BASE_URL}
  apiKey: ${MODEL_API_KEY}
  sslVerify: true

prompt:
  system: |
    你是一个深度研究助手，先规划任务，再分步执行。

skills:
  sources:
    - ./skills/common
    - ./skills/research
    - ./skills/reporting

tools:
  - name: webSearch
    ref:
      type: http
      url: https://search.example.com/api/search
      method: POST
    localCache: false
```

说明：

1. `framework.type` 决定大框架，第一批明确适配 `openjiuwen`。
2. `framework.agent` 决定同一框架下的 Agent 类型，例如 `react`、`deepagent`。
3. `skills.sources` 暴露给客户传入多个技能来源；第一版来源仅支持本地 filesystem 目录，SDK 内部会归一成技能来源模型并把技能资源纳入 `AgentSpec`。
4. 通用字段覆盖 `name`、`displayName`、`description`、`metadata`、`cacheRoot`、`model`、`prompt`、`skills`、`tools`。
5. 框架特有配置后续可以放在 `framework.options` 里，不污染通用层。
6. `agentscope`、`langchain4j` 当前只保留适配目录占位，暂不提供 YAML 示例。

`description` 会映射到框架 AgentCard，既面向协议发现和人工理解，也可能参与模型侧 Agent 描述，建议客户认真填写。

环境变量解析规则：

1. YAML 中的 `${ENV_NAME}` 由 `AgentYamlEnvironmentResolver` 解析。
2. 如果环境变量不存在，第一版直接抛出 `ValidationException`，不保留原始占位符，也不替换成空字符串。
3. 这样可以把 API key、baseUrl 等配置缺失问题暴露在 Handler 创建阶段，避免运行时才出现难定位的鉴权或连接失败。

示例：

```yaml
framework:
  type: openjiuwen
  agent: deepagent
  options:
    maxPlanningSteps: 5
    enableReflection: true
```

### skills 传参规则

`skills.sources` 接收多个技能来源。第一版只支持 `type: filesystem`，也就是本地文件系统目录；后续可以扩展到 git、classpath、zip 等来源。

`skills.sources` 支持字符串简写和对象写法。字符串简写默认表示 `type: filesystem` 且 `localCache: false`：

```yaml
skills:
  sources:
    - ./skills/order
```

等价于：

```yaml
skills:
  sources:
    - type: filesystem
      path: ./skills/order
      localCache: false
```

单独 skill 目录：

```text
./skills/order/
  SKILL.md
```

skill repository 目录：

```text
./skills/order/
  order-query/
    SKILL.md
  refund-helper/
    SKILL.md
```

解析规则：

1. 如果目录自身存在 `SKILL.md`，把该目录解析为一个 skill。
2. 如果目录自身不存在 `SKILL.md`，扫描它的一级子目录，把每个包含 `SKILL.md` 的子目录解析为一个 skill。
3. 第一版不做无限递归扫描，避免目录结构不可控。
4. 如果目录自身有 `SKILL.md`，同时子目录也有 `SKILL.md`，视为结构冲突并报配置错误。

### tools 传参规则

`tools` 表示可被 Agent 调用的函数能力声明。YAML 负责描述工具是什么，以及真实实现在哪里；工具执行逻辑不写在 YAML 里。

推荐字段：

| 字段 | 作用 |
|---|---|
| `name` | 暴露给 Agent 的工具名。 |
| `description` | 工具能力描述，帮助模型判断什么时候调用。 |
| `inputSchema` | 工具入参 JSON Schema。 |
| `outputSchema` | 工具出参结构说明。 |
| `ref` | 工具真实实现的引用，由 `ToolResolver` 解析。 |
| `localCache` | 可选，默认 `false`；为 `true` 时把可缓存的文件 / 目录资源复制或下载到 SDK 本地缓存，HTTP / MCP 只缓存元信息，执行仍访问远端。 |

`ref` 第一版只支持三类来源：本地文件路径、HTTP 端点、MCP tool。暂不支持 Spring Bean 注入形式，避免 `agent-sdk` 第一版和 Spring 容器生命周期绑定。

`ref` 支持两种互斥格式：

| 格式 | 适用场景 | 示例 |
|---|---|---|
| 字符串 `scheme:value` | 简写，第一版主要用于 `file`。 | `ref: file:./tools/query-order-tool.yaml` |
| 对象 `{ type, ... }` | 所有 scheme 均可用，适合带额外参数的场景。 | `ref: { type: http, url: ..., method: POST }` |

解析规则：

1. `ref` 是字符串时，按第一个 `:` 拆成 `scheme` 和 `value`。
2. `ref` 是对象时，读取 `type` 作为 `scheme`，其余字段作为该 resolver 的参数。
3. 未知 `scheme` 不回退为空工具，而是抛出 `UnsupportedToolRefException`。

```yaml
tools:
  - name: queryOrder
    description: 查询订单状态。
    inputSchema:
      type: object
      properties:
        orderId:
          type: string
          description: 订单号。
      required:
        - orderId
    outputSchema:
      type: object
      properties:
        status:
          type: string
          description: 订单状态。
    ref: file:./tools/query-order-tool.yaml

  - name: queryOrderVerbose
    description: 查询订单状态，对象写法。
    ref:
      type: file
      path: ./tools/query-order-tool.yaml

  - name: searchDocs
    description: 搜索知识库文档。
    ref:
      type: mcp
      server: doc-search
      tool: searchDocs

  - name: callRemoteApi
    description: 调用远端订单服务。
    ref:
      type: http
      url: https://order.example.com/api/query
      method: POST
```

这里的边界是：

1. YAML 中的 `tools` 描述函数名、函数说明、入参、出参和实现引用。
2. `ToolResolver` 根据 `ref` 找到真实工具实现，第一版内置 `FileToolResolver`、`HttpToolResolver`、`McpToolResolver` 三类 resolver。
3. 具体框架适配器负责把解析后的工具注册给 OpenJiuwen React / DeepAgent。
4. 如果外部工具对象已经能提供名称、描述和 schema，YAML 可以只写 `ref`；显式字段优先级高于外部反射结果。

`ToolResolver` 契约建议为：

```java
public interface ToolResolver {
    boolean supports(String scheme);

    ResolvedTool resolve(ToolSpec spec);
}

public sealed interface ResolvedTool permits NativeTool, WrappableTool {
}

public record NativeTool(Object tool) implements ResolvedTool {
}

public record WrappableTool(
    ToolDescriptor descriptor,
    ExecutionHandle executionHandle
) implements ResolvedTool {
}

public sealed interface ExecutionHandle permits HttpExecutionHandle, McpExecutionHandle {
}

public record HttpExecutionHandle(
    URI url,
    String method,
    Map<String, String> headers,
    Duration timeout
) implements ExecutionHandle {
}

public record McpExecutionHandle(
    String server,
    String tool
) implements ExecutionHandle {
}
```

这里的设计边界是：

1. `ResolvedTool` 是 SDK 工具解析的稳定产物，不直接等同于某个框架的 tool 类型。
2. `NativeTool` 是客户自定义 resolver 的逃生舱口，用于承载外部 resolver 已经解析好的框架原生工具对象；`tool` 的实际类型由 resolver 和具体 mapper 双方约定。
3. `WrappableTool` 用于承载框架无关的工具描述和执行句柄；`ToolDescriptor` 保存名称、描述、入参 schema、出参 schema 等元信息，`ExecutionHandle` 保存怎么执行。
4. `HttpToolResolver` 产出 `WrappableTool + HttpExecutionHandle`；`McpToolResolver` 产出 `WrappableTool + McpExecutionHandle`。
5. `FileToolResolver` 读取 `ascend-tool/v1` 描述文件，解析其中的 `execution`，再产出对应的 `WrappableTool`。
6. SDK 内置 resolver 第一版不产出 `NativeTool`，全部产出 `WrappableTool`；`NativeTool` 的类型检查只在客户注入自定义 resolver 的路径触发。
7. `spec/tool/` 不依赖 OpenJiuwen、AgentScope 或 Spring；框架相关包装只发生在 `adapter/openjiuwen/OpenJiuwenToolMapper` 这类 adapter 内部。

组合规则：

1. SDK 内部维护一个 `List<ToolResolver>`。
2. 内置 resolver 顺序为 `file`、`http`、`mcp`。
3. builder 注入的 resolver 默认插入到内置 resolver 前面，用于覆盖或扩展 scheme。
4. 多个 resolver 支持同一个 scheme 时，使用第一个匹配项。
5. 没有 resolver 支持该 scheme 时抛出 `UnsupportedToolRefException`。

`file:` 指向的是工具描述文件，不是 Spring Bean，也不是 Java 类名。第一版建议工具描述文件仍然产出一个可执行 tool wrapper，例如：

```yaml
schema: ascend-tool/v1

name: queryOrder
description: 查询订单状态。

inputSchema:
  type: object
  properties:
    orderId:
      type: string
      description: 订单号。
  required:
    - orderId

outputSchema:
  type: object
  properties:
    status:
      type: string

execution:
  type: http
  url: https://order.example.com/api/query
  method: POST
```

`FileToolResolver` 读取该文件后生成 `ToolDescriptor`，并根据 `execution` 创建框架无关的 `ExecutionHandle`。第一版不引入 `class:` scheme；如果后续需要反射 Java 类，再单独扩展。

`ToolDescriptor` 的填充规则固定为：

1. `FileToolResolver` 从 `ascend-tool/v1` 文件中读取 `name`、`description`、`inputSchema`、`outputSchema` 构建 `ToolDescriptor`；如果 YAML `tools[*]` 显式声明了同名字段，以 YAML 字段覆盖文件字段。
2. `HttpToolResolver` 和 `McpToolResolver` 从 YAML `tools[*]` 的显式字段构建 `ToolDescriptor`；第一版不主动访问 HTTP 端点或 MCP server 拉取远端 schema。
3. 如果 resolver 构建 `ToolDescriptor` 时缺少工具名、描述或入参 schema 等必需字段，应在 Handler 创建阶段抛出 `ValidationException`，不要等模型调用工具时才失败。

`ascend-tool/v1` 的 `execution.type` 校验规则固定为：

1. `FileToolResolver` 直接根据 `execution.type` 构造对应 `ExecutionHandle`，例如 `http` 构造 `HttpExecutionHandle`，`mcp` 构造 `McpExecutionHandle`。
2. `FileToolResolver` 不递归调用 `HttpToolResolver` 或 `McpToolResolver`，避免 resolver 链在文件描述解析时再次分派。
3. `execution.type` 是未知值时，抛出 `UnsupportedToolRefException`。

更具体地说，`tool` 在这一版里就是一个可被模型函数调用的能力单元：

```text
工具名 + 工具描述 + 入参 schema + 出参说明 + 执行入口
```

YAML 只声明前四类元信息和执行入口引用，不承载 Java 代码。SDK 内部的转换建议分两步：

1. `AgentYamlParser` 把 YAML 中的 `tools[*]` 解析成 `ToolSpec`。
2. `ToolResolver` 根据 `ToolSpec.ref` 找到真实实现，并返回 SDK 可识别的工具句柄。
3. `OpenJiuwenToolMapper` 把工具句柄适配成 OpenJiuwen 原生 `com.openjiuwen.core.foundation.tool.Tool`。
4. 如果工具实现来自本地文件、HTTP 端点或 MCP tool，SDK 用 OpenJiuwen 的 `ToolCard` + `LocalFunction` 包装；如果工具实现本身已经是 OpenJiuwen `Tool`，则直接使用。

OpenJiuwen 工具映射的推荐规则：

| YAML / SDK 输入 | OpenJiuwen 落点 |
|---|---|
| `name` | `ToolCard.id` 和 `ToolCard.name`，保证模型看到的工具名稳定。 |
| `description` | `ToolCard.description`。 |
| `inputSchema` | `ToolCard.inputParams`。 |
| `outputSchema` | 放入 `ToolCard.properties` 或 SDK 内部元数据，用于文档、校验和结果解释；OpenJiuwen 当前主要依赖入参 schema 参与函数调用。 |
| `ref` | 交给 `ToolResolver` 找真实实现，再由 `OpenJiuwenToolMapper` 包装成 `Tool`。 |

`OpenJiuwenToolMapper` 的分支规则固定为两类：

1. `NativeTool`：如果 `tool` 已经是 OpenJiuwen `Tool`，直接返回；否则报清晰的类型错误，避免在 mapper 中无限制 `instanceof` 猜测。
2. `WrappableTool`：把 `ToolDescriptor` 转成 OpenJiuwen `ToolCard`，再把 `HttpExecutionHandle` / `McpExecutionHandle` 包装成 `LocalFunction` 的执行函数。

React 和 DeepAgent 的注册方式不同，但输入来源相同：

```text
ToolSpec
  -> ToolResolver
  -> resolved tool implementation
  -> OpenJiuwenToolMapper
  -> com.openjiuwen.core.foundation.tool.Tool
  -> React / DeepAgent registration
```

对 OpenJiuwen React，SDK 创建 `ReActAgent` 后，把每个 `Tool` 的 `ToolCard` 放入 `agent.getAbilityManager().add(tool.getCard())`，同时把 `Tool` 放入 `Runner.resourceMgr().addTool(tool, agentId)`，这样模型看到工具声明，实际执行时也能从 resource manager 找到工具实例。

对 OpenJiuwen DeepAgent，SDK 优先把工具放进 `DeepAgentConfig.tools`；`DeepAgent.ensureInitialized()` 会调用 `registerHarnessTool(tool)`，内部完成 `Runner.resourceMgr().addTool(...)` 和 `agent.getAbilityManager().add(...)`。

### 路径和远端资源生命周期

第一版建议采用 `localCache` 控制本地缓存，默认 `false`：

```yaml
localCache: false
```

语义如下：

| 来源 | `localCache: false` 默认行为 | `localCache: true` 行为 |
|---|---|---|
| `tools[*].ref: file:...` | 把相对路径按 YAML 所在目录解析成绝对路径，校验文件存在并读取必要元信息；Handler 持有原始路径引用。 | 把工具文件复制到 SDK 本地缓存目录，Handler 使用缓存副本。 |
| `tools[*].ref.type: http` | 校验 URL、method、headers、timeout 等配置，创建 HTTP tool wrapper；每次工具调用访问远端 HTTP 服务。 | 只缓存工具 descriptor / schema / 连接配置，执行仍访问远端 HTTP 服务。 |
| `tools[*].ref.type: mcp` | 校验 MCP server/tool 引用，创建 MCP tool wrapper；每次工具调用通过 MCP client 访问远端 MCP server/tool。 | 只缓存工具 descriptor / schema / 连接配置，执行仍访问远端 MCP server/tool。 |
| `skills.sources` filesystem | 把相对目录按 YAML 所在目录解析成绝对路径，校验 `SKILL.md` 结构；React / DeepAgent 持有原始目录引用。 | 把 skill 目录复制到 SDK 本地缓存目录，React / DeepAgent 使用缓存目录。 |

这意味着第一版的默认行为仍然是引用客户提供的位置，不创建影子副本，也不监听文件变化。客户如果希望 Handler 创建后不受原始文件变更影响，可以显式设置 `localCache: true`。

缓存刷新第一版不做自动监听：重新调用 `AgentHandlerFactory.fromYaml(...)` 创建新的 Handler 时，SDK 重新解析资源，并按 `localCache` 重新写入缓存。未来如果支持 Git 仓库、HTTP zip 包、classpath 包这类远程 skill 来源，也复用同一个 `localCache` 语义：默认远端引用，显式打开后下载到 SDK 本地缓存目录。

SDK 本地缓存目录建议由 `ResourceLocalCache` 管理。由于 `agent-sdk` 最终会以 jar 形式交付给客户，缓存目录必须位于运行环境外部的可写目录，不能放在 jar 包内部，也不能依赖 `agent-sdk` 源码目录存在。

`cacheRoot` 可以在 YAML 顶层声明，也可以通过 SDK builder 或运行环境配置。YAML 顶层配置适合“一个 YAML 就是一份部署单元”的场景；builder 配置适合客户应用统一托管多个 agent 的场景。

`fromYaml(...)` 路径下，缓存根目录解析顺序建议为：

1. YAML 顶层 `cacheRoot`。
2. `AgentHandlerFactory.builder().cacheRoot(Path)` 显式传入。
3. Java system property：`ascend.agent.sdk.cacheDir`。
4. 环境变量：`ASCEND_AGENT_SDK_CACHE_DIR`。
5. 默认目录：`${user.home}/.ascend/agent-sdk/cache`。

`fromAgent(...)` 路径没有 YAML 输入，因此不读取 YAML 顶层 `cacheRoot`，缓存根目录解析顺序仍从 builder 开始：

1. `AgentHandlerFactory.builder().cacheRoot(Path)` 显式传入。
2. Java system property：`ascend.agent.sdk.cacheDir`。
3. 环境变量：`ASCEND_AGENT_SDK_CACHE_DIR`。
4. 默认目录：`${user.home}/.ascend/agent-sdk/cache`。

如果 YAML 顶层 `cacheRoot` 是相对路径，按 YAML 文件所在目录解析成绝对路径，避免客户从不同工作目录启动时解析结果漂移。

如果最终目录不存在，SDK 可以尝试创建；如果目录不可写，`fromYaml(...)` 应尽早失败并抛出清晰错误。

缓存目录内部布局建议包含 agent 名和资源 hash，避免不同 YAML、同名 agent、同名 tool / skill 互相覆盖：

```text
${cacheRoot}/
  agents/
    {safeAgentName}-{agentSpecHash}/
      tools/
        {toolName}-{toolSpecHash}/
          query-order-tool.yaml
      skills/
        {skillName}-{skillSourceHash}/
          SKILL.md
```

其中：

1. `safeAgentName`、`toolName`、`skillName` 需要做文件名安全化。
2. `agentSpecHash` 来自 YAML 绝对路径和核心配置内容。
3. `toolSpecHash` 来自 `ToolSpec.ref`、schema 和 `localCache` 等字段。
4. `skillSourceHash` 来自 skill 源路径、目录结构摘要和 `localCache`。
5. 第一版 hash 建议使用规范化后的核心字段计算 SHA-256，并取前 12 个十六进制字符作为目录名片段。
6. hash 算法和目录布局属于 SDK 内部实现细节，不承诺跨版本稳定；SDK 升级导致旧缓存失效是允许的。
7. 缓存目录布局不作为公共 API；客户只通过 YAML 顶层 `cacheRoot`、`cacheRoot(Path)` 或环境变量控制根目录。

### OpenJiuwen skill 映射规则

`skills.sources` 的客户语义保持简单：客户传一个或多个技能来源。第一版来源只支持本地目录；SDK 要做两件事：

1. 先按前文的目录规则校验目录结构，识别每个目录是单独 skill 还是 skill repository。
2. 再根据 `framework.agent` 映射到 OpenJiuwen 的原生能力入口。

OpenJiuwen React 和 DeepAgent 的落点如下：

| Agent 类型 | OpenJiuwen 落点 | 说明 |
|---|---|---|
| `openjiuwen/react` | `BaseAgent.registerSkill(Object skillPath)` | SDK 创建 `ReActAgentConfig` 时应设置稳定的 `sysOperationId`，否则 `BaseAgent` 内部的 `SkillUtil` 可能无法初始化。 |
| `openjiuwen/deepagent` | `DeepAgentConfig.skillDirectories` + `SkillUseRail` | DeepAgent 已有 `SkillUseRail`，会注册 `list_skill` 和 `skill_tool`，并在模型调用前注入 skill prompt section。 |

因此，YAML 里的：

```yaml
skills:
  sources:
    - ./skills/common
    - ./skills/order
```

不会被 SDK 强行展开成一大段 system prompt。更推荐的行为是：

1. SDK 校验路径存在、结构合法。
2. React 路径下，把这些 filesystem 来源解析出的目录传给 `ReActAgent.registerSkill(...)`。
3. DeepAgent 路径下，把这些 filesystem 来源解析出的目录写入 `DeepAgentConfig.skillDirectories`，并把 `SkillUseRail` 放入 `DeepAgentConfig.rails`。
4. 运行时由 OpenJiuwen 原生 skill 机制负责列出 skill、读取 `SKILL.md` 和注入必要提示。

这也解释了 `./skills/order` 下面直接有 `SKILL.md` 是否能解析：可以。它会被视为一个单独 skill 目录。只有当 `./skills/order` 自身没有 `SKILL.md` 时，SDK 才把它当成 repository，扫描它的一层子目录。

## 4. 目标目录层级

当前只创建设计目录和本文档。后续实现时建议按以下结构落地：

```text
agent-sdk/
  README.md
  pom.xml
  module-metadata.yaml
  src/
    main/
      java/
        com/
          huawei/
            ascend/
              agentsdk/
                factory/
                  AgentHandlerFactory.java
                  AgentHandlerFactoryBuilder.java
                spec/
                  AgentSpec.java
                  AgentInstance.java
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
                    HttpExecutionHandle.java
                    McpExecutionHandle.java
                    FileToolResolver.java
                    HttpToolResolver.java
                    McpToolResolver.java
                    UnsupportedToolRefException.java
                adapter/
                  AdapterProvider.java
                  AdapterRegistry.java
                  AdapterSelectionException.java
                  openjiuwen/
                    OpenJiuwenAdapterProvider.java
                    OpenJiuwenAgentSpecMapper.java
                    OpenJiuwenToolMapper.java
                    OpenJiuwenSkillMapper.java
                    factory/
                      OpenJiuwenHandlerFactory.java
                    react/
                      OpenJiuwenReactProvider.java
                      OpenJiuwenReactAgentBuilder.java
                      OpenJiuwenReactAgentHandlerAdapter.java
                      OpenJiuwenReactOptions.java
                    deepagent/
                      OpenJiuwenDeepAgentProvider.java
                      OpenJiuwenDeepAgentBuilder.java
                      OpenJiuwenDeepAgentHandlerAdapter.java
                      OpenJiuwenDeepAgentOptions.java
                  agentscope/
                    # reserved
                  langchain4j/
                    # reserved
                support/
                  AgentSdkException.java
                  ValidationException.java
                  UnsupportedFrameworkException.java
                  cache/
                    ResourceLocalCache.java
                    LocalCacheException.java
      resources/
        META-INF/
          services/
            com.huawei.ascend.agentsdk.adapter.AdapterProvider
    test/
      java/
        com/
          huawei/
            ascend/
              agentsdk/
                factory/
                  AgentHandlerFactoryTest.java
                spec/
                  yaml/
                    AgentYamlParserTest.java
                adapter/
                  openjiuwen/
                    OpenJiuwenAdapterProviderTest.java
                  agentscope/
                    # reserved
                  langchain4j/
                    # reserved
      resources/
        examples/
          openjiuwen-react-agent.yaml
          openjiuwen-deepagent-agent.yaml
```

这个层级里，`agentsdk/` 根包下不直接放实现类，只承载四个分组包：

1. `factory/` 是客户入口。
2. `spec/` 是客户输入规格层，包含统一描述模型、YAML 解析、模型配置、Prompt、技能目录和工具解析。
3. `adapter/` 是所有框架适配器的总目录。
4. `support/` 是异常和通用支撑类。

## 5. 设计结论

1. 模块名使用 `agent-sdk`，比 `agent-execution-engine` 更贴近“客户侧转换 SDK”的职责。
2. 对外入口使用 `factory/AgentHandlerFactory.java`，静态方法满足最短代码路径。
3. YAML 使用 `ascend-agent/v1`，对客户保持简洁；SDK 内部再转换成 `spec/AgentSpec.java`。
4. OpenJiuwen React、OpenJiuwen DeepAgent 作为第一批明确适配方向；AgentScope、LangChain4j 先在 `adapter/` 下保留空目录占位。
5. Adapter provider 由 SDK 自己实现，客户默认不需要理解或实现 provider。

## 附录 A：OpenJiuwen 源码对齐

本节用于约束第一版 OpenJiuwen 适配的真实落点，避免后续实现时在 SDK 内部重新发明 OpenJiuwen 已经提供的对象模型。

### 关键源码入口

| 源码 | 对 SDK 设计的影响 |
|---|---|
| `openjiuwen-agent-core-java/src/main/java/com/openjiuwen/core/singleagent/ReActAgent.java` | 对外别名，构造参数是 `AgentCard`，实际继承 `core.singleagent.agents.ReActAgent`。 |
| `openjiuwen-agent-core-java/src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgentConfig.java` | ReAct 的主要配置对象，包含 `promptTemplate`、`maxIterations`、`configureModelClient(...)`、`sysOperationId`。 |
| `openjiuwen-agent-core-java/src/main/java/com/openjiuwen/core/singleagent/schema/AgentCard.java` | Agent 元信息入口，承载 `id`、`name`、`description`、`inputParams`、`outputParams`。 |
| `openjiuwen-agent-core-java/src/main/java/com/openjiuwen/core/foundation/tool/Tool.java` | OpenJiuwen 原生工具抽象，真实工具最终应适配成这个类型。 |
| `openjiuwen-agent-core-java/src/main/java/com/openjiuwen/core/foundation/tool/ToolCard.java` | 工具元信息入口，承载工具名、描述和入参 schema。 |
| `openjiuwen-agent-core-java/src/main/java/com/openjiuwen/core/foundation/tool/function/LocalFunction.java` | 普通 Java 函数包装成 OpenJiuwen `Tool` 的直接落点。 |
| `openjiuwen-agent-core-java/src/main/java/com/openjiuwen/core/foundation/tool/function/AnnotatedToolFactory.java` | 已有注解方法转 `LocalFunction` 的能力，可作为后续 Java 方法工具解析参考；不进入第一版 tool 来源。 |
| `openjiuwen-agent-core-java/src/main/java/com/openjiuwen/harness/deep_agent/DeepAgent.java` | DeepAgent 内部持有 ReActAgent，并通过 `DeepAgentConfig` 初始化模型、工具、rail 和运行逻辑。 |
| `openjiuwen-agent-core-java/src/main/java/com/openjiuwen/harness/schema/config/DeepAgentConfig.java` | DeepAgent 的配置对象，已有 `systemPrompt`、`tools`、`rails`、`skillDirectories`、`skills`、`model`、`backend` 等字段。 |
| `openjiuwen-agent-core-java/src/main/java/com/openjiuwen/harness/rails/SkillUseRail.java` | DeepAgent 的 skill 使用 rail，会注册 `list_skill`、`skill_tool`，并注入 skill prompt section。 |
| `spring-ai-ascend/agent-runtime/src/main/java/com/huawei/ascend/runtime/dispatch/adapter/openjiuwen/OpenJiuwenAgentHandler.java` | runtime 提供的 OpenJiuwen Handler 基类，SDK 具体 Handler 应继承它。 |

### React 转换链

React YAML 的落地链路建议固定为：

```text
YAML
  -> AgentSpec
  -> AgentCard
  -> ReActAgentConfig
  -> ReActAgent
  -> OpenJiuwenReactAgentHandlerAdapter extends OpenJiuwenAgentHandler
  -> execute(AgentExecutionContext)
  -> toOpenJiuwenInput(context)
  -> Runner.runAgent(agent, input, null, null)
```

字段映射：

| YAML 字段 | OpenJiuwen 对象 |
|---|---|
| `name` | `AgentCard.id`、`AgentCard.name`、runtime `agentId`。 |
| `description` | `AgentCard.description`。 |
| `prompt.system` | `ReActAgentConfig.promptTemplate = List.of(Map.of("role", "system", "content", ...))`。 |
| `model.provider`、`model.apiKey`、`model.baseUrl`、`model.name`、`model.sslVerify` | `ReActAgentConfig.configureModelClient(provider, apiKey, baseUrl, modelName, sslVerify)`；`sslVerify` 不填时默认 `true`。 |
| `framework.options.maxIterations` | `ReActAgentConfig.maxIterations`。 |
| `framework.options.sysOperationId` | `ReActAgentConfig.sysOperationId`；不填时默认使用 `AgentSpec.name`。 |
| `skills.sources` | `ReActAgent.registerSkill(...)`，并要求配置中有稳定 `sysOperationId`。 |
| `tools` | `ToolSpec -> OpenJiuwen Tool`，再注册到 `AbilityManager` 和 `Runner.resourceMgr()`。 |

React Handler 的 `execute(...)` 由 `agent-runtime` 调度调用，不由客户代码直接调用。它的职责是把 runtime 的 `AgentExecutionContext` 转成 OpenJiuwen 输入，执行 OpenJiuwen agent，并把原始结果交给 `OpenJiuwenAgentHandler.resultAdapter()` 映射为 runtime 结果。

### DeepAgent 转换链

DeepAgent YAML 的落地链路建议固定为：

```text
YAML
  -> AgentSpec
  -> AgentCard
  -> DeepAgentConfig
  -> DeepAgent
  -> OpenJiuwenDeepAgentHandlerAdapter extends OpenJiuwenAgentHandler
  -> execute(AgentExecutionContext)
  -> toOpenJiuwenInput(context)
  -> deepAgent.run(input)
```

`DeepAgent.run(input)` 内部会调用 `ensureInitialized()`，再执行 `Runner.runAgent(agent, inputs, null, null)`。因此 SDK 应调用 `deepAgent.run(input)`，不直接操作其内部 ReActAgent，也不直接调用 `Runner.runAgent(...)`。

字段映射：

| YAML 字段 | OpenJiuwen 对象 |
|---|---|
| `name` | `AgentCard.id`、`AgentCard.name`、runtime `agentId`。 |
| `description` | `AgentCard.description`。 |
| `prompt.system` | `DeepAgentConfig.systemPrompt`。 |
| `model.name` | 写入 `DeepAgentConfig.model` 对应的模型请求配置，例如 `modelName`。 |
| `model.provider`、`model.apiKey`、`model.baseUrl`、`model.sslVerify` | 写入 `DeepAgentConfig.backend` 对应的模型客户端配置；`sslVerify` 不填时默认 `true`。 |
| `framework.options.maxIterations` | `DeepAgentConfig.maxIterations`。 |
| `skills.sources` | `DeepAgentConfig.skillDirectories`，并追加 `SkillUseRail`。 |
| `tools` | `DeepAgentConfig.tools`，真实可执行工具应是 OpenJiuwen `Tool`；`ToolCard` 只适合作为能力声明或过渡元信息。 |

DeepAgent 对 `skills` 的支持比 React 更直接：`SkillUseRail` 会把可用 skill 列表注入 prompt，并注册 `list_skill`、`skill_tool` 两个工具。用户的 YAML 只需要传目录，不需要暴露这两个内部工具。

DeepAgent 的模型映射保持和 React 同源，但落点不同：React 直接调用 `ReActAgentConfig.configureModelClient(...)`；DeepAgent 先把请求侧字段写入 `DeepAgentConfig.model`，把客户端侧字段写入 `DeepAgentConfig.backend`，再由 DeepAgent 初始化流程映射到内部 ReActAgent 的模型请求和模型客户端配置。

### 工具和技能的第一版策略

第一版建议把 OpenJiuwen 的实现策略收敛成以下规则：

1. `ToolResolver` 是 SDK 内部扩展点，第一版内置 `file`、`http`、`mcp` 三类 resolver；客户默认不实现 provider。
2. 如果解析结果已经是 OpenJiuwen `Tool`，直接注册。
3. 如果解析结果是本地文件工具配置、HTTP 调用器或 MCP 调用器，用 `ToolCard` + `LocalFunction` 包装成 OpenJiuwen `Tool`。
4. 如果解析结果只有 `ToolCard`，只能作为工具声明参与模型提示；第一版对客户 YAML 中声明的业务工具，应尽量要求 resolver 返回可执行 `Tool`。
5. `outputSchema` 先作为元信息保留，不阻塞 OpenJiuwen 函数调用；真正参与模型工具调用的是 `ToolCard.inputParams`。
6. `tools` 和 `skills` 的本地路径默认不复制，只解析成绝对路径并由 Handler 持有引用；显式 `localCache: true` 时复制到 SDK 本地缓存目录。
7. HTTP / MCP 始终远端调用；显式 `localCache: true` 时只缓存 descriptor / schema / 连接配置，不改变执行位置。
8. `skills.sources` 先只支持本地目录；远程 skill、classpath skill 等后续再扩展到 `SkillSourceSpec`，并复用 `localCache` 语义。
9. React 和 DeepAgent 使用同一套 YAML 字段，但落到不同 OpenJiuwen 原生入口；不要把两者强行压成完全一样的内部实现。

## 附录 B：文件职责

### 根目录

| 文件 | 作用 |
|---|---|
| `README.md` | 模块设计说明、客户使用方式、YAML 示例、目标目录层级。 |
| `pom.xml` | 后续把 `agent-sdk` 纳入 Maven 构建时使用，依赖 `agent-runtime` 暴露的 Handler 契约；第一版不提供 Spring 启动封装，传递依赖以 `agent-runtime` 当前发布形态为准。 |
| `module-metadata.yaml` | 可选的模块元信息，用于记录模块名、职责、依赖边界和架构归属。 |

### 客户入口

| 文件 | 作用 |
|---|---|
| `factory/AgentHandlerFactory.java` | SDK 唯一推荐入口，提供 `fromYaml(Path)` 和 `fromAgent(...)`。 |
| `factory/AgentHandlerFactoryBuilder.java` | 高级入口，允许注入 `spec/tool/ToolResolver`、`spec/model/ModelResolver`，以及在 YAML 未声明 `cacheRoot` 或 `fromAgent(...)` 场景下覆盖 SDK 本地缓存根目录。 |

### 统一描述模型

| 文件 | 作用 |
|---|---|
| `spec/AgentSpec.java` | SDK 内部统一 Agent 描述模型，由 YAML 解析得到，包含 `name`、`displayName`、`description`、`metadata`、`cacheRoot`、framework、model、prompt、skills、tools 等字段。 |
| `spec/AgentInstance.java` | 包装客户直接传入的 Agent 实例，补齐名称、框架类型、Agent 类型等元信息。 |

### YAML 解析

| 文件 | 作用 |
|---|---|
| `spec/yaml/AgentYamlLoader.java` | 从 `Path` 读取 YAML，并组织解析、环境变量替换、校验流程。 |
| `spec/yaml/AgentYamlParser.java` | 把 YAML 内容解析成 `AgentSpec`，并在解析阶段填充 `displayName = name` 等默认值；顶层 `cacheRoot` 若为相对路径，交给 loader 按 YAML 所在目录归一。 |
| `spec/yaml/AgentYamlEnvironmentResolver.java` | 解析 `${ENV_NAME}` 这类环境变量占位符；变量不存在时抛出 `ValidationException`。 |

### 适配器选择

| 文件 | 作用 |
|---|---|
| `adapter/AdapterProvider.java` | SDK 内部 SPI，表示一个框架适配器，例如 OpenJiuwen 或 AgentScope。 |
| `adapter/AdapterRegistry.java` | 根据 `framework.type` 和 `framework.agent` 找到对应 adapter provider。 |
| `adapter/AdapterSelectionException.java` | 框架或 Agent 类型无法匹配时抛出的异常。 |

### OpenJiuwen 适配

| 文件 | 作用 |
|---|---|
| `adapter/openjiuwen/OpenJiuwenAdapterProvider.java` | OpenJiuwen 总入口，负责分发到 React 或 DeepAgent。 |
| `adapter/openjiuwen/OpenJiuwenAgentSpecMapper.java` | 把通用 `AgentSpec` 映射成 OpenJiuwen 可理解的配置对象。 |
| `adapter/openjiuwen/OpenJiuwenToolMapper.java` | 把 `ToolResolver` 的解析结果转换成 OpenJiuwen 原生 `Tool` / `LocalFunction`；只产出工具实例，不负责注册。 |
| `adapter/openjiuwen/OpenJiuwenSkillMapper.java` | 把 `SkillSourceSpec` 转换成 OpenJiuwen React / DeepAgent 可注册的 skill 路径、rail 或 prompt section。 |
| `adapter/openjiuwen/factory/OpenJiuwenHandlerFactory.java` | 基于 runtime 的 `OpenJiuwenAgentHandler` 抽象基类，创建 SDK 内部具体 Handler 实现。 |
| `adapter/openjiuwen/react/OpenJiuwenReactProvider.java` | OpenJiuwen React Agent 类型的 provider。 |
| `adapter/openjiuwen/react/OpenJiuwenReactAgentBuilder.java` | 根据 YAML / `AgentSpec` 创建 OpenJiuwen React Agent 实例。 |
| `adapter/openjiuwen/react/OpenJiuwenReactAgentHandlerAdapter.java` | 继承 runtime 的 `OpenJiuwenAgentHandler`，在 `execute(...)` 中构建 / 调用 OpenJiuwen React Agent。 |
| `adapter/openjiuwen/react/OpenJiuwenReactOptions.java` | React Agent 独有配置，例如迭代次数、停止条件等。 |
| `adapter/openjiuwen/deepagent/OpenJiuwenDeepAgentProvider.java` | OpenJiuwen DeepAgent 类型的 provider。 |
| `adapter/openjiuwen/deepagent/OpenJiuwenDeepAgentBuilder.java` | 根据 YAML / `AgentSpec` 创建 OpenJiuwen DeepAgent 实例。 |
| `adapter/openjiuwen/deepagent/OpenJiuwenDeepAgentHandlerAdapter.java` | 继承 runtime 的 `OpenJiuwenAgentHandler`，在 `execute(...)` 中构建 / 调用 OpenJiuwen DeepAgent。 |
| `adapter/openjiuwen/deepagent/OpenJiuwenDeepAgentOptions.java` | DeepAgent 独有配置，例如规划深度、反思开关等。 |

### 预留适配目录

| 文件 | 作用 |
|---|---|
| `adapter/agentscope/` | AgentScope 适配占位目录；当前不定义文件，待 runtime 提供对应 Handler 基类或适配契约后再展开。 |
| `adapter/langchain4j/` | LangChain4j 适配占位目录；当前不定义文件，待确认 runtime 侧契约和支持范围后再展开。 |

### 模型、Prompt、技能和工具

| 文件 | 作用 |
|---|---|
| `spec/model/ModelSpec.java` | 表示 YAML 中的模型配置。 |
| `spec/model/ModelResolver.java` | 把 `ModelSpec` 解析成具体框架需要的模型客户端或模型配置。 |
| `spec/model/ModelResolutionException.java` | 模型解析失败时抛出的异常。 |
| `spec/prompt/PromptSpec.java` | 表示 system prompt、模板 prompt 或外部 prompt 引用。 |
| `spec/prompt/PromptLoader.java` | 加载外部 prompt 文件或模板。 |
| `spec/skill/SkillSpec.java` | 表示一个从技能目录读取出来的技能资源。 |
| `spec/skill/SkillSourceSpec.java` | 表示一个技能来源；第一版由 `skills.sources` 归一得到，并承载 `localCache`；后续可扩展到 filesystem、classpath、git 等来源。 |
| `spec/skill/SkillSourceLoader.java` | 读取多个技能来源，并把来源中的技能资源合并进 `AgentSpec`。 |
| `spec/tool/ToolSpec.java` | 表示 YAML 中声明的工具，包含名称、描述、schema、`ref` 和 `localCache`。 |
| `spec/tool/ToolResolver.java` | 工具解析 SPI，按 `ref` scheme 判断是否支持并把 `ToolSpec` 转成 `ResolvedTool`。 |
| `spec/tool/ResolvedTool.java` | `ToolResolver` 的 sealed 解析结果基类，只区分 `NativeTool` 和 `WrappableTool`。 |
| `spec/tool/NativeTool.java` | 客户自定义 resolver 的逃生舱口，承载外部 resolver 已经解析出的框架原生工具对象；内置 resolver 不产出该类型。 |
| `spec/tool/WrappableTool.java` | 承载 `ToolDescriptor` 和框架无关 `ExecutionHandle`，等待具体 adapter 包装；内置 resolver 默认产出该类型。 |
| `spec/tool/ToolDescriptor.java` | 承载工具名、描述、入参 schema、出参 schema 等工具元信息；YAML 显式字段优先于文件描述字段。 |
| `spec/tool/ExecutionHandle.java` | 框架无关的工具执行入口基类。 |
| `spec/tool/HttpExecutionHandle.java` | 描述 HTTP 工具的 URL、method、headers、timeout 等执行配置。 |
| `spec/tool/McpExecutionHandle.java` | 描述 MCP 工具的 server 和 tool 引用。 |
| `spec/tool/FileToolResolver.java` | 解析 `file:` 工具描述文件，按 `execution.type` 直接创建 `HttpExecutionHandle` 或 `McpExecutionHandle`，再返回 `WrappableTool`。 |
| `spec/tool/HttpToolResolver.java` | 解析 YAML 中的 HTTP tool 配置，创建 `HttpExecutionHandle`；第一版不主动从远端拉取 schema。 |
| `spec/tool/McpToolResolver.java` | 解析 YAML 中的 MCP server/tool 引用，创建 `McpExecutionHandle`；第一版不主动从远端拉取 schema。 |
| `spec/tool/UnsupportedToolRefException.java` | 未知 tool `ref` scheme 或 resolver 不存在时抛出。 |

### 支撑类

| 文件 | 作用 |
|---|---|
| `support/AgentSdkException.java` | SDK 异常基类。 |
| `support/ValidationException.java` | YAML 字段缺失、格式错误或配置冲突时抛出。 |
| `support/UnsupportedFrameworkException.java` | 请求了 SDK 不支持的框架或 Agent 类型时抛出。 |
| `support/cache/ResourceLocalCache.java` | 管理 `localCache: true` 时的外部可写缓存目录解析、目录创建、资源复制 / 下载和缓存路径返回。 |
| `support/cache/LocalCacheException.java` | 本地缓存目录创建、复制或下载失败时抛出。 |

### 资源和测试

| 文件 | 作用 |
|---|---|
| `resources/META-INF/services/com.huawei.ascend.agentsdk.adapter.AdapterProvider` | 可选的 Java SPI 注册文件，用于自动发现内置 adapter provider。 |
| `test/.../factory/AgentHandlerFactoryTest.java` | 验证公开入口能从 YAML / 实例返回 `AgentHandler`。 |
| `test/.../spec/yaml/AgentYamlParserTest.java` | 验证 YAML 解析、默认值、环境变量替换和错误提示。 |
| `test/.../adapter/openjiuwen/OpenJiuwenAdapterProviderTest.java` | 验证 OpenJiuwen React / DeepAgent 的 adapter provider 选择和转换流程。 |
| `test/resources/examples/openjiuwen-react-agent.yaml` | OpenJiuwen React 示例配置。 |
| `test/resources/examples/openjiuwen-deepagent-agent.yaml` | OpenJiuwen DeepAgent 示例配置。 |
