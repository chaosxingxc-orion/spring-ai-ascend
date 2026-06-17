# OpenJiuwen Workflow Adapter

在 agent-runtime 中托管 OpenJiuwen Workflow Agent（DAG 编排 + 人工确认中断/恢复），三步即可通过 A2A 端点访问。

## 1. 概述

```java
// 最小示例
public class MyWorkflowHandler extends OpenJiuwenWorkflowAgentRuntimeHandler {
    public MyWorkflowHandler() { super("my-workflow"); }
    @Override protected Workflow createOpenJiuwenWorkflow(AgentExecutionContext ctx) {
        // 构建并返回 Workflow DAG
    }
}
@Bean OpenJiuwenWorkflowAgentRuntimeHandler myHandler() { return new MyWorkflowHandler(); }
```

与 ReActAgent Adapter 的区别：Workflow 使用**显式 DAG 编排**替代 LLM 自主循环，原生支持 `QuestionerComponent` 人工确认节点——执行到该节点自动挂起，等待用户输入后恢复。

## 2. 快速开始

### 第一步 — 继承 Handler

```java
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenWorkflowAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.workflow.Workflow;

public class MyWorkflowHandler extends OpenJiuwenWorkflowAgentRuntimeHandler {
    public MyWorkflowHandler() { super("my-workflow"); }

    @Override
    protected Workflow createOpenJiuwenWorkflow(AgentExecutionContext context) {
        // 第二步在此实现
    }
}
```

### 第二步 — 实现 createOpenJiuwenWorkflow()

```java
@Override
protected Workflow createOpenJiuwenWorkflow(AgentExecutionContext context) {
    ModelClientConfig clientCfg = ModelClientConfig.builder()
        .clientProvider("openai").apiKey(apiKey).apiBase(apiBase).verifySsl(true).build();
    ModelRequestConfig reqCfg = ModelRequestConfig.builder()
        .modelName("gpt-4").temperature(0.0).maxTokens(256).build();

    // 人机交互节点：固定提问，不使用 LLM
    QuestionerConfig qCfg = new QuestionerConfig();
    qCfg.setModelClientConfig(clientCfg);
    qCfg.setModelConfig(reqCfg);
    qCfg.setResponseType("reply_directly");
    qCfg.setExtractFieldsFromResponse(false);
    qCfg.setQuestionContent("请问1+1等于几？");

    Workflow wf = new Workflow(WorkflowCard.builder().id("my-workflow").build());
    wf.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
    wf.addWorkflowComp("ask", new QuestionerComponent(qCfg),
        Map.of("summary", "${start.query}"), null);
    wf.setEndComp("end", new End(),
        Map.of("answer", "${ask.user_response}"), null);
    wf.addConnection("start", "ask");
    wf.addConnection("ask", "end");
    return wf;
}
```

### 第三步 — 注册为 Spring Bean

```java
@Configuration(proxyBeanMethods = false)
public class MyConfiguration {
    @Bean OpenJiuwenWorkflowAgentRuntimeHandler myHandler() { return new MyWorkflowHandler(); }
}
```

Runtime 自动从 `agentId` 生成 A2A AgentCard。如需被邻接 Agent 作为 Tool 发现，必须声明 skills（见 §5 邻接 Agent 互调）。

## 3. 工作原理

```
A2A 请求 → A2aAgentExecutor
  │
  ├─ createOpenJiuwenWorkflow(): 子类构建 Workflow DAG
  ├─ workflow.invoke(inputs, session, null) — 同步执行 DAG
  │     │
  │     ├─ COMPLETED → OpenJiuwenWorkflowStreamAdapter → AgentExecutionResult.completed
  │     ├─ INPUT_REQUIRED
  │     │     ├─ 提取 InteractionOutput → UserInputInterrupt
  │     │     ├─ 保存 WorkflowResumeContext（按 agentStateKey）
  │     │     └─ AgentExecutionResult.interrupted → A2A INPUT_REQUIRED
  │     └─ ERROR → AgentExecutionResult.failed
  │
  └─ 恢复:
       A2A 层收到同 taskId 的消息 → handler.execute() 再次调用
         → 检查 pendingResumes[agentStateKey] → 找到断点
         → InteractiveInput.update(nodeId, answer)
         → workflow.invoke(resumeInput, sameSession, null)
         → Checkpointer 恢复图状态 → 从 Questioner 继续执行 → COMPLETED
```

**关键设计**：
- Handler 自身维护 resume 上下文（`ConcurrentHashMap` keyed by `agentStateKey`），不依赖 A2A 层判断中断类型
- Checkpointer 按 `sessionId` 保存/恢复 Workflow 图状态——中断恢复无需 Handler 层额外管理
- Workflow 和 ReActAgent 共享同一套 Checkpointer 基础设施（`OpenJiuwenCheckpointerConfigurer` 全局配置）

## 4. 核心接口

```java
public abstract class OpenJiuwenWorkflowAgentRuntimeHandler
        extends AbstractAgentRuntimeHandler {

    /** 子类必须实现：构建并返回 Workflow DAG。 */
    protected abstract Workflow createOpenJiuwenWorkflow(AgentExecutionContext context);
}
```

| 方法 | 用途 | 关键约束 |
|------|------|---------|
| `createOpenJiuwenWorkflow` | 构建 Workflow DAG（必实现） | 每次 execute() 调用时执行，允许按请求动态构建 |
| `resultAdapter()` | 返回 StreamAdapter（基类已实现） | WorkflowOutput → AgentExecutionResult |
| `agentId()` | 返回 Agent 标识（继承自基类） | 与 A2A 路由键一致 |

**与 ReActAgent Handler 的区别**：

| | OpenJiuwenAgentRuntimeHandler | OpenJiuwenWorkflowAgentRuntimeHandler |
|---|---|---|
| Agent 类型 | ReActAgent（LLM 自主循环） | Workflow（DAG 编排） |
| 中断模型 | 通过 RemoteAgentInterruptRail 拦截 | 通过 QuestionerComponent 原生中断 |
| Rails | 支持 AgentRail 注入 | 不支持 Rails（Workflow 使用组件模型） |
| 会话持久化 | 共享 | 共享（同一 Checkpointer） |

## 5. 能力详述

### 中断/恢复

当 Workflow 执行到 `QuestionerComponent` 节点时：

1. Workflow 挂起，图状态通过 Checkpointer 持久化
2. Handler 返回 `AgentExecutionResult.interrupted(UserInputInterrupt(prompt))`
3. A2A 层向调用方发送 `INPUT_REQUIRED` 状态 + 问题文本
4. 调用方发送同 `taskId` 的续接消息
5. Handler 查找保存的 resume 上下文 → 构造 `InteractiveInput` → 恢复执行

```java
// Questioner 配置（无需 LLM 提取字段）
QuestionerConfig qCfg = new QuestionerConfig();
qCfg.setModelClientConfig(clientCfg);
qCfg.setModelConfig(reqCfg);
qCfg.setResponseType("reply_directly");
qCfg.setExtractFieldsFromResponse(false);   // 不提取结构化字段
qCfg.setQuestionContent("请问1+1等于几？");   // 固定提问文本
```

> `reply_directly` + `extractFieldsFromResponse(false)` + 固定 `questionContent` 时，Questioner 不使用 LLM——仅展示问题并等待回答。

### 会话持久化

Workflow 和 ReActAgent 共享 `OpenJiuwenCheckpointerConfigurer` 全局配置：

```java
@Bean Checkpointer checkpointer() {
    return OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
}
```

Checkpointer 按 `sessionId` 分区——不同 session 互不干扰，Workflow 中断自动保存/恢复。

### 邻接 Agent 互调

Workflow Agent 可被其他 ReActAgent 通过 A2A 远程工具调用。前提：Agent Card 声明 skills。

**被调用方（Workflow Agent）配置**：

```yaml
agent-runtime:
  access:
    a2a:
      agent-card:
        skills:
          - id: ask_question
            name: ask_question
            description: 向用户提一个问题并等待回答
```

或通过 Java 注册带 skills 的 AgentCard Bean（当 YAML 不支持 skills 时）：

```java
@Bean AgentCard myCard() {
    return AgentCard.builder()
        .name("my-workflow")
        .description("...")
        .skills(List.of(AgentSkill.builder()
            .id("ask_question").name("ask_question")
            .description("向用户提一个问题并等待回答")
            .tags(List.of()).build()))
        // ...其他字段
        .build();
}
```

**调用方（Main ReActAgent）配置**：

```yaml
agent-runtime:
  remote-agents:
    - url: http://localhost:8080    # Workflow Agent 地址
```

Main Agent 的 LLM 将看到 `ask_question` 工具，可在推理中调用。

**中断传播**：Main Agent 调用 Workflow Agent → Workflow 中断 → `INPUT_REQUIRED` 通过 A2A 远程编排层传播到调用方 → 用户输入 → 恢复 → COMPLETED。

### Workflow 组件

Workflow DAG 支持以下组件（在 `createOpenJiuwenWorkflow()` 中按需使用）：

| 组件 | 用途 | 是否需要 LLM |
|------|------|:---:|
| `Start` / `End` | 入口/出口节点 | 否 |
| `LLMComponent` | LLM 推理 | 是 |
| `ToolComponent` | 工具调用 | 否 |
| `QuestionerComponent` | 人工确认交互 | 按需 |
| `BranchComponent` | 条件路由 | 否 |

**数据路由**：节点间通过 `${nodeId.fieldName}` 语法传递数据：

```java
wf.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
wf.addWorkflowComp("analyze", new LLMComponent(cfg),
    Map.of("article", "${start.query}"), null);   // analyze.article ← start.query
wf.addWorkflowComp("confirm", new QuestionerComponent(qCfg),
    Map.of("summary", "${analyze.text}"), null);   // confirm.summary ← analyze.text
```

## 6. 完整示例

完整可运行的提问器 Workflow + Main ReActAgent 配对示例见 `examples/agent-runtime-a2a-openjiuwen-workflow/`。

**提问器 Workflow Agent**（`QuestionerWorkflowConfiguration.java`）：

```java
@Configuration(proxyBeanMethods = false)
@Profile("!main")
public class QuestionerWorkflowConfiguration {
    static final String AGENT_ID = "questioner-workflow";

    @Bean AgentCard questionerWorkflowAgentCard() {
        return AgentCard.builder()
            .name(AGENT_ID)
            .description("提问器 Workflow Agent")
            .skills(List.of(AgentSkill.builder()
                .id("ask_question").name("ask_question")
                .description("向用户提一个问题并等待回答")
                .tags(List.of()).build()))
            // ...
            .build();
    }

    @Bean OpenJiuwenWorkflowAgentRuntimeHandler handler(...) {
        return new OpenJiuwenWorkflowAgentRuntimeHandler(AGENT_ID) {
            @Override protected Workflow createOpenJiuwenWorkflow(AgentExecutionContext ctx) {
                // Start → Questioner("1+1等于几?") → End
                // ...
            }
        };
    }
}
```

**Main ReActAgent**（`MainAgentConfiguration.java`）：

```java
@Configuration(proxyBeanModels = false)
@Profile("main")
public class MainAgentConfiguration {
    @Bean OpenJiuwenAgentRuntimeHandler mainHandler(...) {
        return new OpenJiuwenAgentRuntimeHandler("main-react-agent") {
            @Override protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
                // 标准 ReActAgent，system prompt 引导 LLM 使用 ask_question 工具
                // 远程 Agent 通过 application-main.yaml 的 remote-agents 配置接入
            }
        };
    }
}
```

**运行**（三个终端）：

```bash
# 终端 1: Workflow Agent
mvn spring-boot:run -f examples/.../pom.xml

# 终端 2: Main ReActAgent（依赖终端 1）
mvn spring-boot:run -f examples/.../pom.xml -Dspring-boot.run.profiles=main

# 终端 3: 用户
curl -s -X POST http://localhost:8081/a2a \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"r1","method":"SendStreamingMessage","params":{"message":{
    "role":"ROLE_USER","messageId":"m1","contextId":"c1",
    "metadata":{"userId":"u1","agentId":"main-react-agent","sessionId":"s1"},
    "parts":[{"text":"帮我提个问题"}]}}}'
# → INPUT_REQUIRED + "请问1+1等于几？" + taskId

curl -s -X POST http://localhost:8081/a2a \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"r2","method":"SendStreamingMessage","params":{"message":{
    "role":"ROLE_USER","messageId":"m2","taskId":"<TASK_ID>","contextId":"c1",
    "metadata":{"userId":"u1","agentId":"main-react-agent","sessionId":"s1"},
    "parts":[{"text":"2"}]}}}'
# → COMPLETED + 答案确认
```

## 7. 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `sample.openjiuwen.model-provider` | String | `openai` | 模型提供商 |
| `sample.openjiuwen.api-key` | String | — | LLM API Key（必配） |
| `sample.openjiuwen.api-base` | String | — | LLM API 地址（必配） |
| `sample.openjiuwen.model-name` | String | `gpt-5.4-mini` | 模型名称 |
| `sample.openjiuwen.ssl-verify` | boolean | `true` | TLS 证书校验 |
| `sample.openjiuwen.checkpointer` | String | `in-memory` | checkpoint 后端 |
| `agent-runtime.access.a2a.agent-card.skills` | List | `[]` | 声明 skills（邻接发现所必需） |

> Workflow 复用与 ReActAgent 相同的 `sample.openjiuwen.*` 配置前缀。

## 8. 限制

| 限制 | 影响 | 替代 |
|------|------|------|
| 同步执行，cancel 仅阻止结果消费 | 与 ReActAgent 相同限制 | 使用 AgentScope 或 Versatile Adapter |
| 不支持 WorkflowAgent（多 Workflow 跳转） | 无法在多个 Workflow 间动态路由 | 在单 Workflow 内用 BranchComponent |
| 每次 execute() 重建 Workflow 实例 | 复杂 DAG 构建有开销 | 构建耗时远小于 LLM 调用，影响可忽略 |
| Questioner 需提供 modelClientConfig | 即使不使用 LLM 也需配置（库验证要求） | 提供任意有效配置即可 |
| End 模板变量渲染可能受 Graph 数据路由影响 | 复杂输出格式需调试 | 使用非模板模式透传 |

## 9. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/openjiuwen-workflow-runtime-adapter-design.md`
- [OpenJiuwen Adapter](openjiuwen-adapter.md)（ReActAgent 版本）
- [远程调用](remote-invocation.md)
- [State 持久化](state-persistence.md)
- [Handler SPI](handler-spi.md)
- Example：`examples/agent-runtime-a2a-openjiuwen-workflow/`
