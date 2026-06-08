# Proposal — AgentSDK：YAML / OpenJiuwen Agent 实例到 runtime Handler 的客户侧转换层

> 形态：架构评审提案，已按当前 `agent-sdk` 实现刷新。
> 关联：`agent-sdk/agent-sdk设计方案.md`、`examples/agent-sdk-example/README.md`、`agent-runtime` 的 `AgentRuntimeHandler` / `OpenJiuwenAgentRuntimeHandler` 运行时契约。
> 事实基线：已按 Rule G-15 先读 `architecture/facts/generated/`。runtime 事实以生成事实为准；`agent-sdk` 当前实现以源码为准。

---

## 0. 摘要（TL;DR）

当前 `agent-sdk` 已经落成一个窄边界的客户侧转换 SDK：把 OpenJiuwen YAML 或已有 OpenJiuwen Agent 实例转换成 `agent-runtime` 可调度的 `AgentRuntimeHandler`。

最新实现后的核心结论：

- SDK 不重新定义 runtime handler 契约，最终返回 `com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler`。
- runtime 侧已经存在 `AgentRuntimeHandler`、`OpenJiuwenAgentRuntimeHandler`、`AgentRuntimeHandlerRegistry`；SDK 只构造 handler，不管理 runtime registry 或服务生命周期。
- 当前只支持 OpenJiuwen，且支持两种 agent 类型：`react` 和 `deepagent`。
- 对外入口是 `AgentHandlerFactory.fromYaml(Path)`、`toReactAgent(Path)`、`toDeepAgent(Path)`、`toHandler(String,Object)`。
- `fromYaml(Path)` 是顺序组合：先 YAML -> ReActAgent / DeepAgent，再由统一 `toHandler(String,Object)` 转成 runtime handler。
- 已删除早期 provider / registry / SPI adapter 抽象，不保留 `adapter/openjiuwen/` 过渡层。
- YAML 只声明 agent / model / prompt / tools / skills；Java tool 公开写法只使用 `ref.type=file`、`ref.class`、`ref.method`，不再要求 tool `path`。
- 示例模块包含 ReAct 和 DeepAgent 两个 main，默认真实调用 OpenJiuwen / DeepSeek；`-Dopenjiuwen.example.proof=true` 才进入本地 proof mode。

推荐决策：**保留当前窄 SDK 形态，不再扩大第一版范围**。AgentScope、LangChain4j、部署层、注册发现、UI/no-code、服务治理都不进入当前 `agent-sdk`。

---

## 1. 背景与根因

早期设计稿的方向是“YAML / Agent 实例到 OpenJiuwen runtime Handler”，但在代码落地过程中做了几次收敛：

1. 只保留 OpenJiuwen 适配，不做 AgentScope adapter 和 example。
2. 不再引入 `AdapterProvider`、`AdapterRegistry`、Java SPI provider 注册等多框架预留抽象。
3. `adapter/` 目录下直接放 OpenJiuwen 通用 mapper，并按 `react/`、`deepagent/` 分子目录。
4. 原先分开的 React handler 转换函数和 DeepAgent handler 转换函数合并为统一的 `toHandler(String,Object)`。
5. Java tool ref 不再把源码 `path` 暴露给客户，只要求 classpath 中的 `class` 和 `method`。

根因一句话：**早期 proposal 承载了过多“未来多框架扩展”的预留结构，而当前实现已经选择 OpenJiuwen-only 的最小可用 SDK；文档需要回到代码真实边界。**

---

## 2. 既有事实与约束

### 2.1 runtime Handler 契约

生成事实显示，runtime 当前公开的 handler 契约是：

| 事实 | 说明 |
|---|---|
| `code-symbol/com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler` | `AgentRuntimeHandler` 公开 `agentId()`、`execute(AgentExecutionContext)`、`isHealthy()`、`resultAdapter()`。 |
| `code-symbol/com.huawei.ascend.runtime.engine.handler.AgentExecutionContext` | `AgentExecutionContext` 是 handler 执行输入上下文。 |
| `code-symbol/com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler` | OpenJiuwen runtime handler 基类，实现 `AgentRuntimeHandler`。 |
| `code-symbol/com.huawei.ascend.runtime.engine.AgentRuntimeHandlerRegistry` | runtime 内部按 `agentId` 注册和查找 handler。 |

因此，SDK 的边界是复用 runtime 契约，而不是发明 SDK-only handler 或 SDK-owned registry。

### 2.2 依赖方向

SDK 的依赖方向保持：

```text
agent-sdk -> agent-runtime
agent-sdk -/-> agent-service
agent-runtime -/-> agent-sdk
```

SDK 可以依赖 runtime 暴露的 `AgentRuntimeHandler` 和 `OpenJiuwenAgentRuntimeHandler`，但不能要求 runtime 反向感知 SDK，也不能把 service 的启动、路由、租户鉴权、注册发现职责下沉到 SDK。

### 2.3 OpenJiuwen 是当前唯一适配目标

当前实现只支持：

```text
framework.type = openjiuwen
framework.agent = react | deepagent
```

不支持的框架或 agent 类型在 handler 创建阶段抛出 `UnsupportedFrameworkException`。

---

## 3. 当前提案：窄 SDK 边界

### 3.1 模块定位

`agent-sdk` 的职责是：

```text
YAML 描述 / 已有 OpenJiuwen Agent 实例
  -> SDK spec / adapter
  -> runtime AgentRuntimeHandler
```

它不是：

- 新的 agent runtime。
- 新的 agent service。
- 新的部署管理层。
- 新的模型网关。
- 新的 workflow DSL / rule DSL。
- 多框架 adapter 框架。

### 3.2 对外入口

一次性 YAML 到 handler：

```java
AgentRuntimeHandler handler = AgentHandlerFactory.fromYaml(Path.of("openjiuwen/agent.yaml"));
```

显式分步：

```java
ReActAgent reactAgent = AgentHandlerFactory.toReactAgent(Path.of("openjiuwen/agent.yaml"));
AgentRuntimeHandler reactHandler = AgentHandlerFactory.toHandler("order-agent", reactAgent);

DeepAgent deepAgent = AgentHandlerFactory.toDeepAgent(Path.of("openjiuwen/deepagent.yaml"));
AgentRuntimeHandler deepHandler = AgentHandlerFactory.toHandler("deep-agent", deepAgent);
```

高级入口：

```java
AgentRuntimeHandler handler = AgentHandlerFactory.builder()
    .toolResolver(customResolver)
    .cacheRoot(Path.of("/data/agent-sdk-cache"))
    .fromYaml(Path.of("openjiuwen/agent.yaml"));
```

当前不提供 `fromAgent(...)`。已有实例统一使用 `toHandler(String,Object)`，由方法内部根据 `ReActAgent` / `DeepAgent` 实例类型分派。

### 3.3 YAML 作为客户侧声明格式

YAML 是装配单元，声明“用哪个 OpenJiuwen agent、哪个模型、哪些 prompt、哪些 skill、哪些 tool”，然后得到 runtime handler。

关键字段：

| 字段 | 决策 |
|---|---|
| `schema` | 当前固定 `ascend-agent/v1`。 |
| `name` | runtime `agentId`，也是 ReAct 默认 `sysOperationId`。 |
| `framework.type` | 当前只支持 `openjiuwen`。 |
| `framework.agent` | 当前支持 `react`、`deepagent`。 |
| `framework.options` | OpenJiuwen 配置，例如 `executeMode`、`maxIterations`、`sysOperationId`。 |
| `model` | provider / name / baseUrl / apiKey / sslVerify / headers。 |
| `prompt` | system prompt。 |
| `skills.sources` | 本地 filesystem skill 目录。 |
| `tools` | 工具声明与实现引用。 |

示例 YAML 当前显式包含 `apiKey`，用于真实大模型调用。生产配置是否使用环境变量、配置中心或其他密钥管理方式，应由客户交付规范决定。

### 3.4 Adapter 分层

当前实现不再使用 provider / registry 分派，而是在 factory builder 内按 YAML 的 `framework.agent` 直接分派：

```text
AgentHandlerFactoryBuilder.fromYaml(...)
  -> AgentYamlLoader.load(...)
  -> if react: toReactAgent(spec) -> toHandler(spec.name(), reactAgent)
  -> if deepagent: toDeepAgent(spec) -> toHandler(spec.name(), deepAgent)
```

目录结构：

```text
agent-sdk/src/main/java/com/huawei/ascend/agentsdk/
  factory/
  spec/
  adapter/
    OpenJiuwenAgentSpecMapper.java
    OpenJiuwenToolMapper.java
    OpenJiuwenSkillMapper.java
    react/
    deepagent/
  support/
```

关键边界：

- `spec/` 包保持框架无关。
- `adapter/` 直接承载 OpenJiuwen 适配实现；当前没有 `adapter/openjiuwen/` 这一层。
- `adapter/react/` 负责 `ReActAgent` 构造和 `OpenJiuwenReactAgentHandlerAdapter`。
- `adapter/deepagent/` 负责 `DeepAgent` 构造和 `OpenJiuwenDeepAgentHandlerAdapter`。
- 两个具体 handler 都继承 runtime 的 `OpenJiuwenAgentRuntimeHandler`。

### 3.5 Tool 解析

工具链路：

```text
ToolSpec
  -> ToolResolver(file/http/mcp/custom)
  -> ResolvedTool(NativeTool | WrappableTool)
  -> OpenJiuwenToolMapper
  -> OpenJiuwen Tool / LocalFunction
  -> ReAct / DeepAgent registration
```

当前内置 resolver：

| scheme | 语义 |
|---|---|
| `file` | classpath 中的 Java class + method 工具。 |
| `http` | HTTP 工具执行句柄。 |
| `mcp` | MCP server/tool 执行句柄。 |

公开 Java tool YAML：

```yaml
ref:
  type: file
  class: QueryOrderTool
  method: query
```

公开 YAML 不再推荐或要求源码 `path` 字段；Java 工具必须已经编译并位于运行 classpath 中。

ReAct 注册：

```text
agent.getAbilityManager().add(tool.getCard())
Runner.resourceMgr().addTool(tool, agentId, true)
```

DeepAgent 注册：

```text
DeepAgentConfig.tools(configTools)
```

### 3.6 Skill 解析

`skills.sources` 当前只支持本地 filesystem。目录自身有 `SKILL.md` 时视为一个 skill；目录自身没有 `SKILL.md` 时扫描一级子目录；不做无限递归。

OpenJiuwen 映射：

| agent 类型 | skill 落点 |
|---|---|
| `react` | `ReActAgent.registerSkill(skillDirectory)`。 |
| `deepagent` | `DeepAgentConfig.skillDirectories(skillDirs)`，并追加 `new SkillUseRail(skillDirs, "all")`。 |

SDK 不把 skill 展开成 system prompt；skill 列表、读取和注入由 OpenJiuwen 原生机制负责。

---

## 4. 利弊分析

### 决策 A：独立 `agent-sdk` 模块

**利**：

- 客户可以只依赖 SDK 来构造 runtime handler，不需要理解 service 启动细节。
- 依赖方向清晰：`agent-sdk -> agent-runtime`。
- 示例能独立证明 YAML -> OpenJiuwen agent -> runtime handler -> execute 的闭环。

**风险**：

- 如果 SDK 继续吸收部署、注册、服务治理，会和 runtime / service 边界混乱。

**结论**：

保留独立模块，但职责限定为“输入转换 + handler 构造”。

### 决策 B：OpenJiuwen-only

**利**：

- 当前 runtime 已有 OpenJiuwen handler 基类。
- ReAct 和 DeepAgent 的模型、工具、skill 落点已经明确。
- 少一层 provider / registry 抽象，客户 API 更直接。

**风险**：

- 后续如果真的扩展其他框架，需要重新引入分派机制。

**结论**：

当前不为未来框架保留空目录或 provider SPI；等第二个真实框架出现时再提炼扩展点。

### 决策 C：统一 `toHandler(String,Object)`

**利**：

- 客户不需要知道 React / DeepAgent 分别该调用哪个 handler 转换函数。
- 对象实例已经包含类型信息，API 更短。
- `fromYaml(...)` 与客户手写分步调用走同一条 handler 包装路径。

**风险**：

- 入参是 `Object`，类型错误只能运行时发现。

**结论**：

当前保留 `Object` 入口，错误时抛出清晰的 `UnsupportedFrameworkException`；不提前引入复杂泛型层。

### 决策 D：Java tool 不暴露 `path`

**利**：

- YAML 不绑定源码布局。
- 示例与真实使用都依赖 classpath，更符合 Java 运行时形态。
- `agent-sdk` 不需要读取 Java 源文件。

**风险**：

- 客户需要保证工具类已经编译并在 classpath 中。

**结论**：

公开文档只保留 `class + method`，示例项目用 Maven 把 `tools/` 加入编译 source root。

---

## 5. 非目标

- 不在 SDK 中启动 Spring Boot。
- 不在 SDK 中实现注册发现、路由、租户鉴权或服务治理。
- 不修改 runtime `AgentRuntimeHandler` 签名。
- 不引入新的 workflow DSL / rule DSL。
- 不把 YAML 设计成 UI/no-code 后台的完整领域模型。
- 不第一版承诺 AgentScope / LangChain4j 可运行适配。
- 不引入 AdapterProvider / AdapterRegistry / Java SPI provider 层。
- 不把 `agent-sdk` 变成 `agent-service` 的替代品。

---

## 6. 工程落地状态

当前实现目录：

```text
agent-sdk/
  src/main/java/com/huawei/ascend/agentsdk/
    factory/   AgentHandlerFactory / AgentHandlerFactoryBuilder
    spec/      AgentSpec、YAML、tool、skill、model、prompt
    adapter/   OpenJiuwen mapper、react、deepagent
    support/   异常、缓存、校验支撑
```

当前 example：

```text
examples/agent-sdk-example/
  openjiuwen/
    agent.yaml
    deepagent.yaml
  src/main/java/com/huawei/ascend/agentsdk/example/
    OpenJiuwenReactAgentSdkExample.java
    OpenJiuwenDeepAgentSdkExample.java
    OpenJiuwenExampleSupport.java
  tools/
    QueryOrderTool.java
    CalcDiscountTool.java
  skills/
    order-analysis/SKILL.md
    report-writing/SKILL.md
```

示例运行链路：

```text
OpenJiuwenReactAgentSdkExample.main
  -> AgentHandlerFactory.toReactAgent(openjiuwen/agent.yaml)
  -> AgentHandlerFactory.toHandler(...)
  -> handler.execute(context)
  -> DeepSeek / OpenJiuwen model call 或 sdk-proof

OpenJiuwenDeepAgentSdkExample.main
  -> AgentHandlerFactory.toDeepAgent(openjiuwen/deepagent.yaml)
  -> AgentHandlerFactory.toHandler(...)
  -> handler.execute(context)
  -> DeepSeek / OpenJiuwen model call 或 sdk-proof
```

---

## 7. 逐条回应评审 concerns

**C1. 这是又加了一个模块，会不会变成空壳？**

回应：当前有独立 example 和测试触达 `fromYaml / toReactAgent / toDeepAgent / toHandler`，不是只有目录骨架。

**C2. 为什么不直接让 agent-runtime 解析 YAML？**

回应：runtime 负责调度、执行和结果适配；YAML / tool / skill 装配是客户侧构造问题。把 YAML 解析塞进 runtime 会让 runtime 感知客户交付格式。

**C3. 为什么不放到 agent-service？**

回应：service 是启动、暴露协议、管理运行时的承载层；SDK 是客户代码可依赖的构造层。把 SDK 放进 service 会迫使客户为构造 handler 依赖服务模块。

**C4. 为什么没有 AdapterProvider / AdapterRegistry？**

回应：当前只有 OpenJiuwen 一个真实框架，且只有 ReAct / DeepAgent 两条路径。factory builder 直接分派已经足够，提前引入 provider registry 会制造无用抽象。

**C5. skill 是否真的“执行”了？**

回应：OpenJiuwen skill 的语义不是普通函数调用，而是注册 skill 目录，让模型在运行时看到 skill 描述并按其指导组织行为。tool 的可观测证据是工具调用；skill 的可观测证据是目录注册进入 OpenJiuwen skill 机制，并在输出中按 skill 要求体现。

---

## 8. 推荐决策

| 决策 | 推荐 |
|---|---|
| 是否保留独立 `agent-sdk` | 保留。职责限定为客户侧 YAML / 实例到 runtime `AgentRuntimeHandler` 的转换层。 |
| 当前框架范围 | 只承诺 OpenJiuwen ReAct / DeepAgent。 |
| 对外入口 | `fromYaml(Path)`、`toReactAgent(Path)`、`toDeepAgent(Path)`、`toHandler(String,Object)`、builder。 |
| YAML 范围 | 只做装配，不做规则 DSL。 |
| tool 来源 | 内置 `file/http/mcp` + custom resolver。 |
| Java tool 写法 | `ref.type=file` + `class` + `method`，不暴露源码 `path`。 |
| skill 来源 | 当前仅 filesystem。 |
| adapter 层级 | `adapter/` 下直接放 OpenJiuwen mapper，并分 `react/`、`deepagent/`。 |
| 部署职责 | 不进 SDK，留给 runtime / service / 客户应用。 |
| 示例要求 | ReAct 和 DeepAgent 均有独立 main，可真实调用模型，也可 proof mode 验证装配。 |

---

## 9. 后续问题

1. `agent-sdk` YAML 与更完整的 `agent-definition.v1.yaml` 是否要在下一阶段定义映射关系。
2. HTTP / MCP tool 当前执行句柄是否需要补真实端到端示例。
3. `localCache` 当前主要是预留路径，是否要在第一版后续补完整资源物化测试。
4. 如果未来出现第二个真实框架，再决定是否重新引入 provider / registry 或其他分派抽象。

---

## 10. 验证口径

本 proposal 的一致性靠代码和示例闭环验证：

- 事实层：runtime `AgentRuntimeHandler` / `OpenJiuwenAgentRuntimeHandler` / `AgentRuntimeHandlerRegistry` 事实 ID 如 §2 所列。
- SDK 层：`AgentHandlerFactory` 公开四个静态主入口和 builder。
- 编译层：`mvn -pl agent-sdk -am verify` 或针对本次变更的 Maven 编译 / 测试通过。
- 示例层：`mvn -f examples/agent-sdk-example/pom.xml compile exec:java` 能运行 ReAct；指定 `example.mainClass` 后能运行 DeepAgent。
- 行为层：真实模式需要有效 API key；proof mode 使用 `-Dopenjiuwen.example.proof=true` 验证 handler、tool、skill 装配。
