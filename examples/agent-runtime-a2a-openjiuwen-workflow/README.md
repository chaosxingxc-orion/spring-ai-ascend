# OpenJiuwen Workflow Agent — A2A E2E 示例

演示：**主 ReActAgent (LLM) 调用 Workflow Agent (提问器) → 中断 → 用户输入 → 恢复 → 完成**。

## 架构

```
用户 ──A2A curl──▶ Main ReActAgent (:8081) ──remote A2A──▶ Questioner Workflow (:8080)
                   (LLM 决定调用 ask_question)              (Questioner 中断/恢复)
```

## 场景：提问器

```
[Start] → [Questioner: "1+1等于几?"] → [End: "你的答案是XX，回答正确!"]
```

- **Questioner**：固定提问，不使用 LLM，挂起等待输入。
- **End**：模板渲染答案，输出确认。
- **Main ReActAgent**：LLM 在用户要求时调用 `ask_question` 远程工具。

## 项目结构

```
agent-runtime-a2a-openjiuwen-workflow/
├── pom.xml
├── README.md
└── src/
    ├── main/java/.../workflow/
    │   ├── QuestionerWorkflowApplication.java       # Spring Boot 入口
    │   └── QuestionerWorkflowConfiguration.java     # Workflow Handler + AgentCard (skills)
    ├── main/resources/
    │   ├── application.yaml                         # Workflow Agent (默认 profile, :8080)
    │   └── application-main.yaml                    # Main ReActAgent (main profile, :8081)
    └── test/java/.../workflow/
        ├── MainAgentConfiguration.java              # Main ReActAgent handler (@Profile("main"))
        └── QuestionerWorkflowE2eTest.java           # E2E 测试
```

## 手工测试：3 终端操作指南

### 前置条件

```bash
# 安装 agent-runtime（首次）
mvn install -DskipTests -f pom.xml

# 设置 LLM API Key
export SAA_LLM_API_KEY="your-api-key"
```

### 打开 3 个终端

---

**终端 1 — 启动 Workflow Agent（:8080）**

```bash
cd ~/github.com/spring-ai-ascend
export SAA_LLM_API_KEY="your-api-key"
mvn spring-boot:run -f examples/agent-runtime-a2a-openjiuwen-workflow/pom.xml
```

预期输出：
```
Started QuestionerWorkflowApplication in X.XXX seconds
```

> 此终端保持运行，不要关闭。

---

**终端 2 — 启动 Main ReActAgent（:8081）**

```bash
cd ~/github.com/spring-ai-ascend
export SAA_LLM_API_KEY="your-api-key"
mvn spring-boot:run -f examples/agent-runtime-a2a-openjiuwen-workflow/pom.xml \
  -Dspring-boot.run.profiles=main
```

预期输出：
```
Started QuestionerWorkflowApplication in X.XXX seconds
The following 1 profile is active: "main"
```

> Main Agent 启动时自动拉取 Workflow Agent 的 Card（`GET :8080/.well-known/agent-card.json`），
> 发现 `ask_question` skill 并安装为本地 LLM Tool。

---

**终端 3 — 用户操作（curl）**

### 步骤 1：对主 Agent 说话，触发 LLM 调用提问器

```bash
curl -s -X POST http://localhost:8081/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-001",
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "contextId": "ctx-1",
        "metadata": {
          "userId": "u1",
          "agentId": "main-react-agent",
          "sessionId": "s1"
        },
        "parts": [{"text": "帮我提个问题"}]
      }
    }
  }'
```

**预期现象**：
- 终端 1（Workflow）无日志输出（Questioner 无 LLM，无调用）
- 终端 2（Main）日志显示 LLM 决策调用了 `ask_question` 工具
- 终端 3 收到 SSE 事件流，**注意查找 `taskId`**：

```
event: task-status-update
data: {"taskId":"<记下这个ID>","status":{"state":"INPUT_REQUIRED",
       "message":{"parts":[{"text":"请问1+1等于几？"}]}}}
```

### 步骤 2：用户输入答案（恢复）

用步骤 1 记下的 `taskId` 替换 `<TASK_ID>`：

```bash
curl -s -X POST http://localhost:8081/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-001",
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-002",
        "taskId": "<TASK_ID>",
        "contextId": "ctx-1",
        "metadata": {
          "userId": "u1",
          "agentId": "main-react-agent",
          "sessionId": "s1"
        },
        "parts": [{"text": "2"}]
      }
    }
  }'
```

**预期现象**：
- 终端 2 日志：Workflow resume → Tool result → LLM summary
- 终端 3 收到最终结果，包含 `"你的答案是2，回答正确！"`

```
event: task-status-update
data: {"taskId":"...","status":{"state":"COMPLETED",
       "message":{"parts":[{"text":"你的答案是2，回答正确！"}]}}}
```

---

## 完整数据流

```
终端3 curl ──"帮我提个问题"──▶ :8081 MainAgent (ReActAgent + LLM)
                                  │
                                  ├─ LLM: "用户要我提问题，我看到了 ask_question 工具"
                                  ├─ LLM → tool_call: ask_question()
                                  ├─ remote A2A POST :8080/a2a ──▶ Questioner Workflow
                                  │                                    │
                                  │                              [Questioner 挂起]
                                  │                                    │
                                  │◀── INPUT_REQUIRED ────────────────┘
                                  │
                                  ◀── SSE: INPUT_REQUIRED + "1+1等于几?"
终端3 看到问题，用户输入 "2" ──▶ :8081 (同 taskId)
                                  │
                                  ├─ A2A resume → :8080
                                  │                 └─ Workflow 恢复 → COMPLETED
                                  │◀── "你的答案是2，回答正确！"
                                  │
                                  ├─ tool result → LLM 总结
终端3 ◀── SSE: COMPLETED + 最终结果
```

## 仅测试 Workflow Agent（单终端）

如果只想验证 Workflow 的中断/恢复机制，不需要主 Agent：

```bash
# 终端 1：启动 Workflow Agent
mvn spring-boot:run -f examples/agent-runtime-a2a-openjiuwen-workflow/pom.xml

# 终端 2：直接调用 Workflow Agent
# 第一轮
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"req-001","method":"SendStreamingMessage","params":{"message":{
    "role":"ROLE_USER","messageId":"m1","contextId":"c1",
    "metadata":{"userId":"u1","agentId":"questioner-workflow","sessionId":"s1"},
    "parts":[{"text":"启动"}]}}}'
# → INPUT_REQUIRED + taskId

# 第二轮（替换 taskId）
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"req-001","method":"SendStreamingMessage","params":{"message":{
    "role":"ROLE_USER","messageId":"m2","taskId":"<TASK_ID>","contextId":"c1",
    "metadata":{"userId":"u1","agentId":"questioner-workflow","sessionId":"s1"},
    "parts":[{"text":"2"}]}}}'
# → COMPLETED + "你的答案是2，回答正确！"
```

## E2E 测试

```bash
export SAA_LLM_API_KEY="your-api-key"
mvn test -f examples/agent-runtime-a2a-openjiuwen-workflow/pom.xml \
  -Dtest=QuestionerWorkflowE2eTest
```
