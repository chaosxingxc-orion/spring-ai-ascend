# Agent SDK OpenJiuwen YAML

从 `ascend-agent/v1` YAML 构造原生 OpenJiuwen `ReActAgent` 或 `DeepAgent` 对象。

`agent-sdk` 是装配 SDK，不提供 A2A endpoint。要把创建出的 agent 暴露为 A2A 服务，需要把它接入 `agent-runtime` handler。

## 1. 概览

最小调用：

```java
ReActAgent react = AgentFactory.toReactAgent(Path.of("openjiuwen/agent.yaml"));
DeepAgent deepAgent = AgentFactory.toDeepAgent(Path.of("openjiuwen/deepagent.yaml"));
```

适用场景：

- 用 YAML 管理 OpenJiuwen ReActAgent 或 DeepAgent 的模型、prompt、tools、skills 和 rails。
- 在非 Spring runtime 场景中直接创建原生 OpenJiuwen 对象。
- 在应用代码中先装配 DeepAgent，再交给 `agent-runtime` handler 托管。

不适用场景：

- 需要 A2A HTTP endpoint：使用 `agent-runtime`。
- 需要 Workflow DAG：使用 `openjiuwen-workflow-adapter.md`。
- 需要 AgentScope 或 Versatile 适配：使用对应 runtime guide。

## 2. 快速开始

### 第一步：本地安装 SDK

`agent-sdk` 使用独立模块 POM：

```bash
mvn -f agent-sdk/pom.xml -DskipTests install
```

### 第二步：编写 YAML

最小 ReAct YAML：

```yaml
schema: ascend-agent/v1
name: sdk-openjiuwen-example-agent
description: Example ReAct agent.

framework:
  type: openjiuwen
  agent: react
  options:
    maxIterations: 6

model:
  provider: OpenAI
  name: deepseek-chat
  baseUrl: https://api.deepseek.com
  apiKey: ${DEEPSEEK_API_KEY}
  sslVerify: true

prompt:
  system: |
    You are a concise assistant.
```

最小 DeepAgent YAML：

```yaml
schema: ascend-agent/v1
name: sdk-openjiuwen-deepagent-example-agent
description: Example DeepAgent.

framework:
  type: openjiuwen
  agent: deepagent
  options:
    maxIterations: 8
    enableTaskLoop: true
    workspacePath: ./target/deepagent-workspace
    language: en

model:
  provider: OpenAI
  name: deepseek-chat
  baseUrl: https://api.deepseek.com
  apiKey: ${DEEPSEEK_API_KEY}
  sslVerify: true
```

`${DEEPSEEK_API_KEY}` 这类环境变量占位符在 YAML 加载阶段解析。缺少变量时快速失败。

### 第三步：构造原生 Agent

```java
import com.huawei.ascend.agentsdk.factory.AgentFactory;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.nio.file.Path;

ReActAgent react = AgentFactory.toReactAgent(Path.of("openjiuwen/agent.yaml"));
DeepAgent deepAgent = AgentFactory.toDeepAgent(Path.of("openjiuwen/deepagent.yaml"));
```

添加代码级 resolver 或 rail：

```java
ReActAgent react = AgentFactory.builder()
        .toolResolver(customResolver)
        .rail(customRail)
        .toReactAgent(Path.of("openjiuwen/agent.yaml"));
```

自定义 tool resolver 会先于内置 resolver 执行。自定义 resolver 只要对相同 scheme 的 `supports(...)` 返回 true，就可以覆盖内置 `file` 或 `http` 解析。

## 3. 工作原理

```text
agent.yaml
  -> AgentYamlLoader
  -> AgentYamlEnvironmentResolver
  -> AgentYamlParser
  -> AgentSpec
  -> AgentFactoryBuilder framework guard
  -> OpenJiuwenReactAgentBuilder / OpenJiuwenDeepAgentBuilder
  -> ReActAgent / DeepAgent
```

ReAct 装配链路：

```text
AgentFactory.toReactAgent(path)
  -> 校验 framework.type=openjiuwen, framework.agent=react
  -> 构造 AgentCard
  -> 构造模型客户端和请求配置
  -> 解析 tools
  -> 注册 skills
  -> 注册 rails
  -> 返回 ReActAgent
```

DeepAgent 装配链路：

```text
AgentFactory.toDeepAgent(path)
  -> 校验 framework.type=openjiuwen, framework.agent=deepagent
  -> 构造 AgentCard
  -> 构造 DeepAgentConfig
  -> 写入 model/backend/options/skills/rails/mcps
  -> 构造 Workspace
  -> HarnessFactory.createDeepAgent(...)
  -> 返回 DeepAgent
```

## 4. 核心接口

| API | 用途 | 返回 |
|---|---|---|
| `AgentFactory.toReactAgent(Path)` | 从 YAML 构造 ReActAgent | `ReActAgent` |
| `AgentFactory.toDeepAgent(Path)` | 从 YAML 构造 DeepAgent | `DeepAgent` |
| `AgentFactory.builder()` | 创建可扩展 builder | `AgentFactoryBuilder` |
| `AgentFactoryBuilder.toolResolver(...)` | 注册自定义 tool resolver | builder |
| `AgentFactoryBuilder.rail(...)` | 注册代码级 rail | builder |

框架校验：

| 方法 | 需要的 YAML |
|---|---|
| `toReactAgent` | `framework.type: openjiuwen`，`framework.agent: react` |
| `toDeepAgent` | `framework.type: openjiuwen`，`framework.agent: deepagent` |

## 5. 能力详述

### 工具

Java static method tool：

```yaml
tools:
  - name: queryOrder
    description: Query an order.
    inputSchema:
      type: object
      properties:
        orderId:
          type: string
      required:
        - orderId
    ref:
      type: file
      class: com.example.tools.QueryOrderTool
      method: query
```

必需 Java 方法：

```java
public static Object query(Map<String, Object> inputs)
```

HTTP tool：

```yaml
tools:
  - name: queryRemoteOrder
    description: Query order service.
    inputSchema:
      type: object
    ref:
      type: http
      url: https://api.example.com/orders
      method: POST
      timeout: 30s
      followRedirects: false
      maxResponseBytes: 1048576
      exposeErrorBody: false
```

HTTP tool URL 只做语法校验，并限制响应大小。已发布 SDK 不做 host allowlist 或 SSRF 策略，因此只应使用受信任 YAML 中声明的 HTTP tools。

resolver 顺序：

1. builder 注册的自定义 resolver。
2. `JavaFileToolResolver`。
3. `HttpToolResolver`。

### 技能

文件系统 skills：

```yaml
skills:
  sources:
    - ../skills/order-analysis
    - type: filesystem
      path: ../skills/report-writing
```

source 目录直接包含 `SKILL.md` 时，该目录是一个 skill。source 目录没有直接 `SKILL.md` 时，该目录被视为 skill root，包含 `SKILL.md` 的子目录会被加载为独立 skill。

ReAct 对每个 skill 目录调用 `agent.registerSkill(...)`。DeepAgent 将 skill root 目录写入 `DeepAgentConfig.skillDirectories`。

### Rail 机制

Function rail：

```yaml
rails:
  - name: example-after-tool-call
    type: function
    event: afterToolCall
    class: com.example.rails.ExampleRailHooks
    method: afterToolCall
```

支持的 function rail event：

- `beforeModelCall`
- `afterModelCall`
- `beforeToolCall`
- `afterToolCall`

支持的方法：

```java
public static void afterToolCall(AgentCallbackContext context)
public static Map<String, Object> afterToolCall(Map<String, Object> extra)
```

Class rail 必须是 classpath 中与 OpenJiuwen rail API 兼容的类。builder 级 `.rail(...)` 会追加到 YAML rails 之后。

### MCP

`mcps[]` 只映射到 DeepAgent：

```yaml
mcps:
  - serverId: orders
    serverName: order-mcp
    serverPath: http://localhost:9000/sse
    clientType: sse
```

ReAct YAML 当前不消费 `mcps[]`。

## 6. 完整示例

示例工程：

```text
examples/agent-sdk-example/
```

示例结构：

```text
examples/agent-sdk-example/
├── openjiuwen/
│   ├── agent.yaml
│   └── deepagent.yaml
├── skills/
│   ├── order-analysis/SKILL.md
│   └── report-writing/SKILL.md
├── scripts/
│   ├── run-openjiuwen.sh
│   └── run-openjiuwen.ps1
└── src/main/java/com/huawei/ascend/agentsdk/example/
    ├── OpenJiuwenReactAgentSdkExample.java
    ├── OpenJiuwenDeepAgentSdkExample.java
    ├── OpenJiuwenExampleSupport.java
    ├── tools/
    └── rails/
```

编译示例：

```bash
mvn -f examples/agent-sdk-example/pom.xml compile
```

使用真实模型 key 运行：

```bash
export DEEPSEEK_API_KEY="..."
bash examples/agent-sdk-example/scripts/run-openjiuwen.sh react
bash examples/agent-sdk-example/scripts/run-openjiuwen.sh deepagent
```

PowerShell：

```powershell
$env:DEEPSEEK_API_KEY = "..."
.\examples\agent-sdk-example\scripts\run-openjiuwen.ps1 -Agent react
.\examples\agent-sdk-example\scripts\run-openjiuwen.ps1 -Agent deepagent
```

示例只有在模型、tool、skill、rail 证明检查都通过后，才打印 `verification: PASS`。

## 7. 配置参考

| 字段 | 必填 | 说明 |
|---|---:|---|
| `schema` | 是 | 固定为 `ascend-agent/v1` |
| `name` | 是 | agent id |
| `displayName` | 否 | agent 展示名，默认等于 `name` |
| `description` | 是 | AgentCard description |
| `framework.type` | 是 | 当前只支持 `openjiuwen` |
| `framework.agent` | 是 | `react` 或 `deepagent` |
| `framework.options` | 否 | ReAct/DeepAgent builder options |
| `model.provider` | 是 | 模型 provider |
| `model.name` | 是 | 模型名称 |
| `model.baseUrl` | 是 | 模型 API base URL |
| `model.apiKey` | 是 | 模型 API key，可用 `${ENV}` |
| `model.sslVerify` | 否 | 模型 backend TLS 校验 |
| `prompt.agentMd` | 否 | 相对 YAML 文件目录读取 |
| `prompt.system` | 否 | inline system prompt |
| `tools[]` | 否 | Java static method 或 HTTP tools |
| `skills.sources[]` | 否 | 文件系统 skill source |
| `rails[]` | 否 | class/function rails |
| `mcps[]` | 否 | 只映射到 DeepAgent |

DeepAgent framework options：

| Option | 默认值 | 说明 |
|---|---|---|
| `maxIterations` | `15` | DeepAgent 最大迭代数 |
| `skillMode` | `all` | skill 加载模式 |
| `workspacePath` | `./` | 工作区路径 |
| `language` | `cn` | 语言 |
| `enableTaskLoop` | `false` | 是否启用 task loop |
| `enableTaskPlanning` | `false` | 是否启用 task planning |
| `completionTimeout` | 空 | 完成超时 |

ReAct framework options：

| Option | 默认值 | 说明 |
|---|---|---|
| `maxIterations` | `5` | ReAct 最大迭代数 |
| `sysOperationId` | `name` | OpenJiuwen sys operation id |

## 8. 与 agent-runtime 组合

`agent-sdk` 只负责装配，不暴露 A2A endpoint。组合方式如下：

```java
public final class YamlDeepAgentHandler extends OpenJiuwenDeepAgentRuntimeHandler {
    private final Path yamlPath;

    public YamlDeepAgentHandler(Path yamlPath) {
        super("yaml-deep-agent");
        this.yamlPath = yamlPath;
    }

    @Override
    protected DeepAgent createOpenJiuwenDeepAgent(AgentExecutionContext context) {
        return AgentFactory.toDeepAgent(yamlPath);
    }
}
```

这样划分职责：

| 职责 | 所属模块 |
|---|---|
| YAML schema、tools、skills、rails、MCP 装配 | `agent-sdk` |
| A2A endpoint、远端 A2A tools、trajectory、cancel | `agent-runtime` |

## 9. 错误处理与排障

| 现象 | 原因 | 处理 |
|---|---|---|
| schema 校验失败 | `schema` 不是 `ascend-agent/v1` | 修正 YAML 顶层字段 |
| framework 不支持 | `framework.type` 不是 `openjiuwen` | 当前只支持 OpenJiuwen |
| `toReactAgent` 失败 | `framework.agent` 不是 `react` | 改为 `react` 或调用 `toDeepAgent` |
| `toDeepAgent` 失败 | `framework.agent` 不是 `deepagent` | 改为 `deepagent` 或调用 `toReactAgent` |
| 环境变量缺失 | `${ENV}` 未定义 | 在进程环境中设置变量 |
| Java tool 找不到类 | 类不在 classpath | 将工具类编译进应用或示例模块 |
| Java tool 方法非法 | 方法缺失、非 static、签名不匹配 | 使用 `public static Object method(Map<String,Object>)` |
| HTTP tool 失败 | URL 非法、超时、非 2xx 或响应超限 | 检查 `url`、`timeout`、`maxResponseBytes`、`exposeErrorBody` |
| skill 重名 | 多个目录名称相同 | 调整 skill 目录名 |
| DeepAgent MCP 不生效 | 使用了 ReAct agent | `mcps[]` 只映射到 DeepAgent |

## 10. 限制

| 限制 | 影响 | 替代 |
|---|---|---|
| 只支持 OpenJiuwen | 不能装配 AgentScope/Versatile | 使用对应 runtime adapter |
| 不托管 A2A | 没有 HTTP endpoint | 接入 `agent-runtime` |
| HTTP tool 无 SSRF 防护 | 不可信 YAML 有安全风险 | 只加载可信 YAML 或自定义 resolver 加策略 |
| ReAct 不消费 `mcps[]` | ReAct YAML MCP 无效果 | 使用 DeepAgent 或 runtime MCP installer |
| 不解析 Workflow YAML | 不能从 YAML 构造 Workflow DAG | 使用 Workflow runtime handler 的 Java 构建方式 |

## 11. 相关文档

- 设计文档：`architecture/docs/L2/agent-runtime/agent-sdk-openjiuwen-yaml-assembly-design.md`
- DeepAgent runtime 适配器：`openjiuwen-deepagent-adapter.md`
- OpenJiuwen ReAct 适配器：`openjiuwen-adapter.md`
- Workflow 适配器：`openjiuwen-workflow-adapter.md`
