# Proposal — AgentSDK：YAML / Agent 实例到 OpenJiuwen runtime Handler 的客户侧转换层

> 形态：架构评审提案。本文把 `agent-sdk/agent-sdk设计方案.md` 从“实现设计说明”转换为“可供架构师 + 工程团队拍板的 proposal”。
> 关联：`agent-sdk/agent-sdk设计方案.md`、`docs/logs/reviews/2026-06-05-agentsdk-scenario-agent-and-deploy-layering-proposal.cn.md`、`agent-runtime` 的 `AgentHandler` / `OpenJiuwenAgentHandler` 运行时契约。
> 事实基线：已按 Rule G-15 先读 `architecture/facts/generated/`。本提案对 runtime 既有代码形态的事实引用以生成事实为准；对 `agent-sdk` 方案本身的描述来自当前设计稿和本次落地草案，生成事实尚未覆盖新增模块。
>
> **收窄（2026-06-06，ADR-0160 / `docs/logs/reviews/2026-06-06-agent-runtime-refactor-proposal.cn.md`）**：本提案假设的 `AgentHandler` / `OpenJiuwenAgentHandler` 运行时契约**已退役**，被中立 **`AgentDriver`**（`engine.spi.AgentDriver` + `OutputConverter` + `common.RunEvent`）取代。YAML→handler 转换层须**改为面向 `AgentDriver`**；YAML 装配出的 tool / skill / memory 交目标框架原生机制注册，**不**上 runtime 中立 SPI。其余分层主张仍适用。

---

## 0. 摘要（TL;DR）

现有 `agent-sdk设计方案.md` 已经把 Agent SDK 的实现细节写得很完整：YAML 形态、`AgentSpec`、`ToolResolver`、skill 目录规则、OpenJiuwen React / DeepAgent 映射、目录结构和文件职责都有定义。但它是一份“怎么实现”的说明，不是评审时最需要的“为什么要这样分层、边界在哪里、哪些决策需要拍板”的 proposal。

**本提案的核心主张**：

- 新增 `agent-sdk` 作为**客户侧转换 SDK**：把客户 YAML 或已有框架 Agent 实例转换成 `agent-runtime` 可调度的 `AgentHandler`。
- SDK **不重新定义 runtime Handler 契约**，而是依赖 runtime 既有 `AgentHandler` 和框架 Handler 基类；事实层显示 `AgentHandler` 当前公开 `agentId()`、`execute(AgentExecutionContext)`、`isHealthy()`、`resultAdapter()`，事实 ID：`code-symbol/com-huawei-ascend-runtime-dispatch-spi-agenthandler`。
- 第一版只把 OpenJiuwen 作为明确落地目标，支持 `react` 和 `deepagent`；AgentScope、LangChain4j 只作为未来 adapter 方向，不在第一版承诺。
- 对客户暴露一条最短路径：`AgentHandlerFactory.fromYaml(Path)`；高级场景再通过 builder 注入自定义 `ToolResolver`、缓存根目录等。
- YAML 只声明 agent / model / prompt / tools / skills，不承载 Java 代码；工具执行能力通过 `file`、`http`、`mcp`、`mock` 等 resolver 转换成框架可注册的 tool。
- `agent-sdk` 只负责“构造 handler”，不负责启动服务、不替代 `agent-runtime` / `agent-service` 的部署职责。

推荐决策：**采纳 `agent-sdk` 独立模块，但保持第一版职责窄化**：只做 YAML / 实例到 runtime `AgentHandler` 的转换层，避免把部署、注册发现、规则引擎、UI/no-code、服务治理混进 SDK。

---

## 1. 背景与触发（为什么需要这份 proposal）

`2026-06-05-agentsdk-scenario-agent-and-deploy-layering-proposal.cn.md` 已经提出更大的分层问题：AgentSDK、配置化场景 Agent、部署/服务承载层分别应处在不同边界。那份提案回答的是“AgentSDK 与部署层为什么需要分出来”。

当前 `agent-sdk设计方案.md` 则进一步回答了一个更具体的问题：**如果要落 `agent-sdk`，这个 SDK 的最小可用形态应该是什么**。

两份材料之间还缺一个评审用中间层：

- 参考 proposal 关注宏观分层，仍留有“SDK 内部怎么划边界”的空白。
- `agent-sdk设计方案.md` 关注实现细节，缺少架构评审所需的决策摘要、利弊、非目标、验证口径和开放问题。
- 工程实现已经围绕 `AgentHandlerFactory`、YAML、OpenJiuwen adapter、tool / skill resolver 形成第一版形状，需要一份 proposal 把“这是一个窄 SDK，不是新 runtime / 新 service”的边界说清楚。

根因一句话：**当前设计稿直接跳到了实现结构，缺少可被架构师评审和拍板的 proposal 层；本提案把实现细节上升为边界、决策和验证标准。**

---

## 2. 既有事实与约束

### 2.1 runtime Handler 契约已经存在

生成事实显示，`agent-runtime` 已有统一运行时 Handler 契约：

| 事实 | 说明 |
|---|---|
| `code-symbol/com-huawei-ascend-runtime-dispatch-spi-agenthandler` | `com.huawei.ascend.runtime.dispatch.spi.AgentHandler` 是接口，公开 `agentId()`、`execute(AgentExecutionContext)`、`isHealthy()`、`resultAdapter()`。 |
| `code-symbol/com-huawei-ascend-runtime-dispatch-handler-agentexecutioncontext` | `AgentExecutionContext` 已在 runtime dispatch handler 包下存在，是 handler 执行输入上下文。 |
| `code-symbol/com-huawei-ascend-runtime-dispatch-adapter-openjiuwen-openjiuwenagenthandler` | `OpenJiuwenAgentHandler` 是 runtime 中的 OpenJiuwen 抽象类，并实现 `AgentHandler`。 |
| `code-symbol/com-huawei-ascend-runtime-dispatch-dispatch-agenthandlerregistry` | runtime 已有 `AgentHandlerRegistry`，可按 `agentId` 注册和查找 handler。 |

因此，`agent-sdk` 的第一原则应是：**复用 runtime 已有 handler 契约，不另起一套 SDK-only handler 抽象**。

### 2.2 依赖方向不能反转

生成事实显示 `agent-runtime` 禁止依赖 `agent-service`，事实 ID：`build-module/agent-runtime`。这意味着 SDK 的依赖边界必须保持：

```text
agent-sdk -> agent-runtime
agent-sdk -/-> agent-service
agent-runtime -/-> agent-sdk
```

`agent-sdk` 可以依赖 runtime 暴露的 `AgentHandler` / `OpenJiuwenAgentHandler`，但不能要求 runtime 反过来感知 SDK，也不能把 service 的启动/部署职责下沉到 SDK。

### 2.3 OpenJiuwen 是第一版真实适配目标

`agent-sdk设计方案.md` 明确把 OpenJiuwen React 和 DeepAgent 作为第一批目标；OpenJiuwen 已在 runtime 中有 `OpenJiuwenAgentHandler` 抽象基类。SDK 可以在自己模块内创建具体 adapter，继承 runtime 基类，并在 `execute(...)` 里调用 OpenJiuwen 原生 Runner / DeepAgent。

这条路径的关键不是“SDK 执行 agent”，而是：

```text
客户输入
  -> SDK 解析 / 装配
  -> runtime AgentHandler 实例
  -> runtime 调度或客户应用调用 execute
```

---

## 3. 提案：AgentSDK 的窄边界

### 3.1 模块定位

`agent-sdk` 是面向客户代码的轻量 SDK，具体职责是：

```text
YAML 描述 / 已有框架 Agent 实例
  -> 统一 SDK spec
  -> 框架 adapter
  -> runtime AgentHandler
```

它不是：

- 新的 agent runtime。
- 新的 agent service。
- 新的部署管理层。
- 新的规则引擎或工作流 DSL。
- 新的模型网关。

一句话边界：**SDK 只负责把客户侧 agent 描述转换成 runtime 能调度的 handler。**

### 3.2 两条客户入口

第一版只推荐一个主入口类：

```java
AgentHandler handler = AgentHandlerFactory.fromYaml(Path.of("agent.yaml"));
```

已有框架 Agent 实例路径：

```java
AgentHandler handler = AgentHandlerFactory.fromAgent(
    "order-agent",
    "openjiuwen",
    "react",
    reactAgent
);
```

高级入口延后到 builder：

```java
AgentHandler handler = AgentHandlerFactory.builder()
    .toolResolver(customResolver)
    .cacheRoot(Path.of("/data/agent-sdk-cache"))
    .fromYaml(Path.of("agent.yaml"));
```

设计取舍：

- 静态入口保证客户最短路径。
- `fromAgent(...)` 保持 `frameworkType + agentType + Object agent`，不提前发明泛型框架模型。
- builder 只服务扩展点，不影响主路径。

### 3.3 YAML 作为客户侧声明格式

SDK 暴露简洁 YAML，内部归一成 `AgentSpec`。

第一版 YAML 关键字段：

| 字段 | 决策 |
|---|---|
| `schema` | 第一版固定 `ascend-agent/v1`。 |
| `name` | 映射 runtime `agentId`，同时作为默认 `sysOperationId`。 |
| `framework.type` | 第一版明确支持 `openjiuwen`。 |
| `framework.agent` | 第一版明确支持 `react`、`deepagent`。 |
| `framework.options` | 框架特有配置，例如 `maxIterations`、`sysOperationId`。 |
| `model` | 模型 provider / name / baseUrl / apiKey / sslVerify。 |
| `prompt` | system prompt。 |
| `skills.sources` | 本地 filesystem skill 来源。 |
| `tools` | 工具声明与实现引用。 |

YAML 不是内部 `AgentDefinition` 的完整替代物，也不承载代码。它是 SDK 面向客户的**装配单元**，目标是让客户能声明“用哪个框架、哪个模型、哪些 prompt、哪些 skill、哪些 tool”，然后得到 `AgentHandler`。

### 3.4 Adapter 分层

SDK 内部采用 provider / registry 分派：

```text
AgentSpec
  -> AdapterRegistry
  -> AdapterProvider(frameworkType, agentType)
  -> OpenJiuwen React / DeepAgent builder
  -> concrete AgentHandler
```

第一版 OpenJiuwen 适配落点：

```text
openjiuwen/react:
  AgentSpec
    -> AgentCard
    -> ReActAgentConfig
    -> ReActAgent
    -> OpenJiuwenReactAgentHandlerAdapter extends OpenJiuwenAgentHandler

openjiuwen/deepagent:
  AgentSpec
    -> AgentCard
    -> DeepAgentConfig
    -> DeepAgent
    -> OpenJiuwenDeepAgentHandlerAdapter extends OpenJiuwenAgentHandler
```

关键边界：

- `spec/` 包不依赖 OpenJiuwen。
- `adapter/openjiuwen/` 才依赖 OpenJiuwen 原生类型。
- 具体 handler 继承 runtime 的 `OpenJiuwenAgentHandler`，不新定义 runtime 映射逻辑。

### 3.5 Tool 解析

YAML 中的 `tools` 描述工具元信息和实现引用：

```text
工具名 + 描述 + 入参 schema + 出参说明 + ref
```

SDK 内部用 `ToolResolver` 把 `ref` 解析成稳定中间产物：

```text
ToolSpec
  -> ToolResolver(file/http/mcp/mock/custom)
  -> ResolvedTool(NativeTool | WrappableTool)
  -> OpenJiuwenToolMapper
  -> OpenJiuwen Tool / LocalFunction
```

建议第一版内置：

| scheme | 语义 |
|---|---|
| `file` | 指向 `ascend-tool/v1` 工具描述文件。 |
| `http` | 包装远端 HTTP 工具调用配置。 |
| `mcp` | 包装 MCP server/tool 引用。 |
| `mock` | 示例和测试使用，证明工具链路，不作为生产默认推荐。 |

关键取舍：

- 未知 `scheme` 必须在 handler 构造阶段失败。
- YAML 显式字段优先于工具文件字段。
- `spec/tool/` 不依赖 OpenJiuwen；OpenJiuwen 包装只在 adapter 中发生。
- HTTP / MCP 第一版只解析配置，不主动拉远端 schema。

### 3.6 Skill 解析

`skills.sources` 第一版只支持本地 filesystem。

目录规则：

```text
./skills/order/
  SKILL.md                  -> 单 skill

./skills/order/
  order-query/SKILL.md      -> skill repository，一层子目录扫描
  refund-helper/SKILL.md
```

冲突规则：

- 目录自身有 `SKILL.md` 时，视为单 skill。
- 目录自身无 `SKILL.md` 时，扫描一级子目录。
- 自身和子目录同时有 `SKILL.md` 时，报结构冲突。
- 不做无限递归。

OpenJiuwen 映射：

| agent 类型 | skill 落点 |
|---|---|
| `react` | 调用 `BaseAgent.registerSkill(skillDirectory)`。 |
| `deepagent` | 写入 `DeepAgentConfig.skillDirectories`，并使用 DeepAgent skill rail。 |

SDK 不把 skill 强行展开成大段 system prompt；skill 如何被读取、展示、使用，交给 OpenJiuwen 原生 skill 机制。

### 3.7 本地缓存与资源生命周期

第一版默认不复制资源，只解析绝对路径并校验结构。`localCache: true` 才复制文件 / 目录到 SDK 外部可写缓存目录。

缓存根目录解析建议：

1. YAML 顶层 `cacheRoot`。
2. builder `cacheRoot(Path)`。
3. system property：`ascend.agent.sdk.cacheDir`。
4. env：`ASCEND_AGENT_SDK_CACHE_DIR`。
5. 默认：`${user.home}/.ascend/agent-sdk/cache`。

缓存目录不是公共 API，不承诺跨版本稳定。它只保证 handler 构造后能使用缓存副本，避免依赖源码目录或 jar 内部可写性。

---

## 4. 利弊分析

### 决策 A：独立 `agent-sdk` 模块

**利**：

- 客户可以只依赖 SDK 来构造 runtime handler，不需要理解 service 启动细节。
- 依赖方向清晰：`agent-sdk -> agent-runtime`。
- SDK 成为多框架 adapter 的自然承载点，后续可扩展 AgentScope / LangChain4j。

**弊 / 风险**：

- 模块新增会触发治理对“空骨架”的敏感。
- 如果 SDK 偷偷承担部署、注册、服务启动，会与 `agent-service` / `agent-runtime` 边界混乱。

**建议**：

采纳独立模块，但第一版必须被运行示例和测试触达；职责限定为“输入转换 + handler 构造”。

### 决策 B：YAML 作为客户主入口

**利**：

- 支持配置化 agent 开发，客户不用为简单场景写 Java。
- tool / skill / model / prompt 可以用统一文件描述，便于样例、文档和未来治理。

**弊 / 风险**：

- YAML 容易膨胀成规则 DSL。
- API key 等敏感配置如果直接写死，会带来交付风险。

**建议**：

第一版 YAML 只做装配，不表达复杂业务规则；环境变量占位缺失时 fail closed。示例中可按用户要求放入 DeepSeek 配置，但产品文档应优先推荐环境变量。

### 决策 C：ToolResolver 中间层

**利**：

- tool 来源和框架 tool 类型解耦。
- `file/http/mcp/mock/custom` 可以统一进入 `ResolvedTool`。
- 测试和示例能证明工具被调用，而不必依赖真实业务系统。

**弊 / 风险**：

- `NativeTool(Object)` 是必要逃生舱口，但类型安全弱。
- HTTP / MCP wrapper 若过早做真实客户端，会扩散 I/O 和生命周期复杂度。

**建议**：

第一版保留 `NativeTool`，但内置 resolver 统一产出 `WrappableTool`；HTTP / MCP 先表达执行句柄，复杂客户端留给后续版本。

### 决策 D：OpenJiuwen 先行

**利**：

- runtime 已有 `OpenJiuwenAgentHandler` 基类，适配成本最低。
- OpenJiuwen React / DeepAgent 已有工具、skill、模型配置落点。

**弊 / 风险**：

- 如果 SDK 把 OpenJiuwen 的实现细节泄漏到通用 spec，将阻碍后续 AgentScope / LangChain4j。

**建议**：

通用 `spec/` 保持框架无关；OpenJiuwen 的 `AgentCard`、`Tool`、`ReActAgentConfig`、`DeepAgentConfig` 只出现在 `adapter/openjiuwen/`。

---

## 5. 非目标（明确不做）

- 不在 SDK 中启动 Spring Boot。
- 不在 SDK 中实现注册发现、路由、租户鉴权或服务治理。
- 不修改 runtime `AgentHandler` 签名。
- 不引入新的 workflow DSL / rule DSL。
- 不把 YAML 设计成 UI/no-code 后台的完整领域模型。
- 不第一版承诺 AgentScope / LangChain4j 可运行适配。
- 不把 `agent-sdk` 变成 `agent-service` 的替代品。

---

## 6. 工程落地建议

建议目录保持 `agent-sdk设计方案.md` 的四组边界：

```text
agent-sdk/
  factory/   客户入口：AgentHandlerFactory / builder
  spec/      框架无关描述模型、YAML、tool、skill、model、prompt
  adapter/   框架适配器：openjiuwen/react、openjiuwen/deepagent
  support/   异常、缓存、校验支撑
```

第一版验收必须包含：

1. YAML loader 能把 `ascend-agent/v1` 转成 `AgentSpec`。
2. `AgentHandlerFactory.fromYaml(...)` 返回 runtime `AgentHandler`。
3. OpenJiuwen React adapter 能注册两个 tool 和两个 skill。
4. 示例目录 `agent-sdk/example` 能独立运行 main，并证明：
   - 通过 `agent.yaml` 构造 OpenJiuwen handler。
   - 输入经过模型。
   - 模型调用两个工具。
   - 最终输出包含两个 skill 的使用证明。
5. `agent-sdk` 构建和测试不依赖 `agent-service`。

---

## 7. 示例形态（客户可理解的最小闭环）

示例应放在 `agent-sdk/example`，而不是只放在 `src/test/java`：

```text
agent-sdk/example/
  pom.xml
  agent.yaml
  tools/
    query-order.yaml
    calc-discount.yaml
  skills/
    order-analysis/SKILL.md
    report-writing/SKILL.md
  src/main/java/.../AgentSdkExample.java
```

示例运行链路：

```text
AgentSdkExample.main
  -> AgentHandlerFactory.fromYaml(agent.yaml)
  -> OpenJiuwenReactAgentHandlerAdapter
  -> handler.execute(context)
  -> DeepSeek / OpenJiuwen model call
  -> queryOrder + calcDiscount
  -> final answer mentions order-analysis + report-writing
```

示例存在的意义不是做单元测试，而是给客户一个“拿 YAML 到 handler 再执行”的最短可运行样板。

---

## 8. 逐条回应可能的评审 concerns

**C1. 这是又加了一个模块，会不会变成空壳？**

回应：SDK 必须通过 example 和测试触达。没有 `fromYaml -> handler -> execute` 的可运行闭环，就不应宣称模块完成。

**C2. 为什么不直接让 agent-runtime 解析 YAML？**

回应：runtime 负责调度、执行和结果适配；YAML / tool / skill 装配是客户侧构造问题。把 YAML 解析塞进 runtime 会让 runtime 感知客户交付格式，也会让未来多框架 adapter 更难隔离。

**C3. 为什么不放到 agent-service？**

回应：service 是启动、暴露协议、管理运行时的承载层；SDK 是客户代码可依赖的构造层。把 SDK 放进 service 会迫使客户为构造 handler 依赖服务模块。

**C4. YAML 和已有 agent-definition 契约是什么关系？**

回应：本提案中的 YAML 是 SDK 第一版客户装配格式，目标更窄；已有 `agent-definition.v1.yaml` 是更完整的声明式 agent 契约。后续可以收敛或映射，但第一版不应强行把 SDK YAML 扩成完整内部契约。

**C5. ToolResolver 会不会变成另一个插件系统？**

回应：第一版只保留最小 resolver 机制。内置 resolver 覆盖 file/http/mcp/mock，custom resolver 只服务客户扩展；不引入 marketplace、生命周期管理、动态热加载。

**C6. skill 是否真的“执行”了？**

回应：OpenJiuwen skill 的语义不是普通函数调用，而是注册 skill 目录，让模型在运行时看到 skill 描述并按其指导组织行为。tool 的可观测证据是工具调用日志和结果；skill 的可观测证据是 skill 被注册、进入 OpenJiuwen skill 机制，并在最终输出中按 skill 名称和要求体现。

---

## 9. 推荐决策

| 决策 | 推荐 |
|---|---|
| 是否保留独立 `agent-sdk` | 保留。职责限定为客户侧 YAML / 实例到 runtime `AgentHandler` 的转换层。 |
| 第一版框架范围 | 只承诺 OpenJiuwen React / DeepAgent。 |
| 对外入口 | `AgentHandlerFactory.fromYaml(Path)` + `fromAgent(...)`；builder 作为高级入口。 |
| YAML 范围 | 只做装配，不做规则 DSL。 |
| tool 来源 | `file/http/mcp/mock` + custom resolver。 |
| skill 来源 | 第一版仅 filesystem。 |
| 部署职责 | 不进 SDK，留给 runtime / service / 客户应用。 |
| 示例要求 | `agent-sdk/example` 必须有独立 main，可运行证明模型、tool、skill 链路。 |

---

## 10. 待架构师 / 工程团队拍板的问题

1. `agent-sdk` 的 YAML 是否长期独立于 `agent-definition.v1.yaml`，还是下一阶段需要明确映射 / 合并策略？
2. `mock` tool resolver 是否只允许 test/example scope，还是作为 SDK 正式内置 resolver 暴露给客户？
3. HTTP / MCP tool 的第一版是否只返回 proof-style wrapper，还是必须接真实客户端？
4. `localCache` 是否进入第一版实现，还是先保留设计字段、等远程 skill / tool 来源出现后再启用？
5. AgentScope / LangChain4j adapter 目录是否保留空占位，还是为避免空骨架先不建目录？

---

## 11. 验证口径

本 proposal 的正确性不靠文档自证，而靠以下证据闭环：

- 事实层：runtime `AgentHandler` / `OpenJiuwenAgentHandler` / `AgentHandlerRegistry` 事实 ID 如 §2 所列。
- 编译层：`mvn -pl agent-sdk -am verify` 通过。
- 示例层：`mvn -pl agent-sdk -am -DskipTests install` 后，`mvn -f agent-sdk/example/pom.xml compile exec:java` 能跑通。
- 行为层：运行输出包含：
  - handler 类型为 OpenJiuwen adapter。
  - OpenJiuwen 发起模型请求。
  - `queryOrder`、`calcDiscount` 两个工具被调用。
  - 最终输出包含 `order-analysis`、`report-writing` 两个 skill 名称。

---

## 12. 下一步

1. 保留 `agent-sdk/agent-sdk设计方案.md` 作为实现细节说明。
2. 以本 proposal 作为架构评审入口，确认 SDK 边界和非目标。
3. 如评审通过，把 `agent-sdk` 的模块事实纳入下一次 deterministic extractor 刷新，而不是手写 `architecture/facts/generated/*`。
4. 对 `mock` resolver、`localCache`、HTTP / MCP 真实执行程度做一次小范围裁决，避免第一版实现范围漂移。
