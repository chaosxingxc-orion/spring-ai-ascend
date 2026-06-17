# OpenJiuwen Workflow Agent — A2A E2E 示例

基于 agent-runtime 托管 OpenJiuwen Workflow Agent，通过 A2A 协议暴露，
演示多步骤 DAG + 人工确认中断/恢复。

## 场景：提问器 (Questioner Workflow)

```
[Start] → [Questioner: "1+1等于几?"] → [End: "你的答案是XX，回答正确!"]
```

- **Questioner 节点**：固定提问，不使用 LLM。挂起等待用户输入。
- **End 节点**：模板渲染用户答案，输出确认信息。

## 快速开始

### 前置条件

- JDK 21+
- 已安装 agent-runtime: `mvn install -DskipTests -f pom.xml`

### 启动

```bash
mvn spring-boot:run -f examples/agent-runtime-a2a-openjiuwen-workflow/pom.xml
```

### 通过 A2A 调用

```bash
# 第一轮：发送消息，触发 Questioner 中断
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "contextId": "ctx-1",
        "metadata": {"userId":"u1","agentId":"questioner-workflow","sessionId":"s1"},
        "parts": [{"text": "启动"}]
      }
    }
  }'

# 预期：收到 INPUT_REQUIRED 状态 + 问题文本 "请问1+1等于几？"
# 记下响应中的 taskId

# 第二轮：发送答案，恢复执行
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-002",
        "taskId": "<上一轮的 taskId>",
        "contextId": "ctx-1",
        "metadata": {"userId":"u1","agentId":"questioner-workflow","sessionId":"s1"},
        "parts": [{"text": "2"}]
      }
    }
  }'

# 预期：收到 COMPLETED 状态 + "你的答案是2，回答正确！"
```

## 如何配对主 Agent（ReActAgent）

此 Workflow Agent 可被其他 ReActAgent 通过 A2A 远程工具调用。

### 主 Agent 配置

在主 Agent 的 `application.yaml` 中添加：

```yaml
agent-runtime:
  remote-agents:
    - url: http://localhost:8080    # 指向此 Workflow Agent
```

主 Agent 的 LLM 将看到名为 `questioner-workflow` 的远程工具，
可以在推理过程中调用它来向用户提问。

### 配对流程

```
用户 → 主Agent(ReActAgent + LLM)
         │
         ├─ LLM 决定调用 questioner 工具
         ├─ A2A → 提问器 Workflow Agent
         │         ├─ Questioner 中断
         │         └─ INPUT_REQUIRED → 传播回用户
         │
用户输入 → 主Agent A2A 层转发 → Workflow 恢复
         │
         └─ Workflow COMPLETED → toolResult → LLM 总结 → 最终回复
```

## 项目结构

```
agent-runtime-a2a-openjiuwen-workflow/
├── pom.xml
├── README.md
└── src/
    ├── main/java/.../workflow/
    │   ├── QuestionerWorkflowApplication.java       # Spring Boot 入口
    │   └── QuestionerWorkflowConfiguration.java     # Workflow Handler + Bean 注册
    ├── main/resources/
    │   └── application.yaml                         # 配置
    └── test/java/.../workflow/
        └── QuestionerWorkflowE2eTest.java           # E2E 测试：A2A 中断/恢复
```

## E2E 测试

```bash
export SAA_LLM_API_KEY="your-api-key"
mvn test -f examples/agent-runtime-a2a-openjiuwen-workflow/pom.xml \
  -Dtest=QuestionerWorkflowE2eTest
```

测试验证：
1. A2A 调用 Workflow Agent
2. Questioner 触发中断 → A2A INPUT_REQUIRED
3. 用户提供答案 → A2A resume
4. Workflow 完成 → COMPLETED，结果含 "回答正确"

## Key API（runtime adapter）

此示例依赖 `agent-runtime` 中新增的两个类：

| 类 | 职责 |
|----|------|
| `OpenJiuwenWorkflowAgentRuntimeHandler` | Workflow Agent 抽象基类，继承 `AbstractAgentRuntimeHandler` |
| `OpenJiuwenWorkflowStreamAdapter` | `WorkflowOutput` → `AgentExecutionResult` 映射 |

用户只需继承 handler、实现 `createOpenJiuwenWorkflow()`、注册为 Spring Bean 即可。
