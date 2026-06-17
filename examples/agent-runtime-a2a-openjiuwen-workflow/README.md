# OpenJiuwen Workflow Agent — A2A E2E 示例

基于 agent-runtime 托管 OpenJiuwen Workflow Agent，通过 A2A 协议暴露，
演示：**主 ReActAgent (LLM) 调用 Workflow Agent (提问器) → 中断 → 用户输入 → 恢复 → 完成**。

## 架构

```
用户 ──A2A──▶ Main ReActAgent (:8081) ──remote A2A──▶ Questioner Workflow Agent (:8080)
              (LLM 决策调用工具)                        (Questioner 中断/恢复)
```

## 场景：提问器

```
[Start] → [Questioner: "1+1等于几?"] → [End: "你的答案是XX，回答正确!"]
```

- **Questioner 节点**：固定提问，不使用 LLM。挂起等待用户输入。
- **End 节点**：模板渲染用户答案，输出确认信息。
- **Main ReActAgent**：LLM 决策调用 `ask_question` 远程工具。

## 项目结构

```
agent-runtime-a2a-openjiuwen-workflow/
├── pom.xml
├── README.md
└── src/
    ├── main/java/.../workflow/
    │   ├── QuestionerWorkflowApplication.java       # Spring Boot 入口
    │   ├── QuestionerWorkflowConfiguration.java     # Workflow Handler + AgentCard (含 skills)
    │   └── main/
    │       └── MainAgentConfiguration.java          # Main ReActAgent Handler (@Profile("main"))
    ├── main/resources/
    │   ├── application.yaml                         # Workflow Agent 配置（默认 profile）
    │   └── application-main.yaml                    # Main ReActAgent 配置（main profile）
    └── test/java/.../workflow/
        └── QuestionerWorkflowE2eTest.java           # E2E 测试：A2A 中断/恢复
```

## 快速开始

### 前置条件

- JDK 21+
- 已安装 agent-runtime: `mvn install -DskipTests -f pom.xml`

### 方式一：仅启动 Workflow Agent（验证中断/恢复）

```bash
# 终端 1：启动提问器 Workflow Agent (:8080)
mvn spring-boot:run -f examples/agent-runtime-a2a-openjiuwen-workflow/pom.xml

# 终端 2：直接调用（两轮）
# 第一轮 — 触发中断
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"SendStreamingMessage","params":{"message":{
    "role":"ROLE_USER","messageId":"m1","contextId":"c1",
    "metadata":{"userId":"u1","agentId":"questioner-workflow","sessionId":"s1"},
    "parts":[{"text":"启动"}]}}}'
# → INPUT_REQUIRED + "请问1+1等于几？"
# 记下返回的 taskId

# 第二轮 — 输入答案恢复
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"SendStreamingMessage","params":{"message":{
    "role":"ROLE_USER","messageId":"m2","taskId":"<上一轮的taskId>","contextId":"c1",
    "metadata":{"userId":"u1","agentId":"questioner-workflow","sessionId":"s1"},
    "parts":[{"text":"2"}]}}}'
# → COMPLETED + "你的答案是2，回答正确！"
```

### 方式二：主 Agent 调用 Workflow Agent（完整 LLM 决策场景）

```bash
# 终端 1：启动提问器 Workflow Agent (:8080)
mvn spring-boot:run -f examples/agent-runtime-a2a-openjiuwen-workflow/pom.xml

# 终端 2：启动主 ReActAgent (:8081)，指向 Workflow Agent
mvn spring-boot:run -f examples/agent-runtime-a2a-openjiuwen-workflow/pom.xml \
  -Dspring-boot.run.profiles=main

# 终端 3：对主 Agent 说话
curl -s -X POST http://localhost:8081/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"SendStreamingMessage","params":{"message":{
    "role":"ROLE_USER","messageId":"m1","contextId":"c1",
    "metadata":{"userId":"u1","agentId":"main-react-agent","sessionId":"s1"},
    "parts":[{"text":"帮我提个问题"}]}}}'

# 主 Agent 的 LLM 自动调用 ask_question 远程工具
# → Workflow Agent 被调用 → Questioner 中断 → 传播回终端 3
# → 用户输入答案 → 继续执行 → 最终输出含 "回答正确"
```

## 配置说明

| 文件 | 用途 | Agent | Port |
|------|------|-------|------|
| `application.yaml` | 默认 profile | Workflow Agent (提问器) | 8080 |
| `application-main.yaml` | main profile | Main ReActAgent | 8081 |

### Workflow Agent 的 Agent Card 声明了 skills

```yaml
agent-runtime:
  access:
    a2a:
      agent-card:
        skills:
          - id: ask_question
            name: ask_question
            description: 向用户提一个问题，等待用户回答后回显答案并确认正确
```

邻接 Agent 配置 `remote-agents` 指向 `http://localhost:8080`，启动时自动拉取 Card，
发现 `ask_question` skill 并安装为本地 Tool。

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

| 类 | 职责 |
|----|------|
| `OpenJiuwenWorkflowAgentRuntimeHandler` | Workflow Agent 抽象基类，继承 `AbstractAgentRuntimeHandler` |
| `OpenJiuwenWorkflowStreamAdapter` | `WorkflowOutput` → `AgentExecutionResult` 映射 |
