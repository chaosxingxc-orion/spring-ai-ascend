# Expense Review Workflow Agent — SIT 测试指南

演示：**费用报销审核 Workflow Agent（8 节点 DAG）→ 中断/恢复 → 邻接 Agent 互调**。

## 架构

```
用户 ──A2A curl──▶ Main ReActAgent (:8081) ──remote A2A──▶ Expense Review Workflow (:8080)
                   (LLM 决定调用 review_expense)            (DAG: 分析→查政策→比对→审批)
```

## DAG 拓扑

```
[Start] → [LLM: analyze] → [Tool: check_policy] → [LLM: audit]
              ↑ 分析报销条目      ↑ 查询公司政策         ↑ 合规比对
    → [Branch: route]
        ├─ risk=high → [Questioner: approve] ──→ [End]   (需人工审批)
        └─ risk=none → [LLM: auto_approve] ────→ [End]   (自动通过)
```

## 项目结构

```
expense-review/
├── pom.xml
├── README.md
└── src/
    ├── main/java/.../expensereview/
    │   ├── ExpenseReviewWorkflowApplication.java       # Spring Boot 入口
    │   ├── ExpenseReviewWorkflowConfiguration.java     # Workflow DAG + AgentCard
    │   ├── MainAgentConfiguration.java                 # Main ReActAgent (@Profile("main"))
    │   └── tool/
    │       └── CompanyPolicyTool.java                  # 公司政策查询 (Stub)
    ├── main/resources/
    │   ├── application.yaml                           # Workflow Agent (:8080)
    │   └── application-main.yaml                      # Main ReActAgent (:8081)
    └── test/java/.../expensereview/
        └── ExpenseReviewWorkflowStructureTest.java     # DAG 结构烟雾测试
```

## 部署

### 前置条件

```bash
# 安装 agent-runtime（首次）
mvn install -DskipTests -f pom.xml

# 设置 LLM API Key
export LLM_API_KEY="your-api-key"
```

### 三终端 SIT 测试

---

**终端 1 — 启动 Workflow Agent（:8080）**

```bash
cd spring-ai-ascend
export LLM_API_KEY="your-api-key"
mvn spring-boot:run -f examples/workflow-calls/expense-review/pom.xml
```

---

**终端 2 — 启动 Main ReActAgent（:8081）**

```bash
cd spring-ai-ascend
export LLM_API_KEY="your-api-key"
mvn spring-boot:run -f examples/workflow-calls/expense-review/pom.xml \
  -Dspring-boot.run.profiles=main
```

---

**终端 3 — 用户操作（curl）**

### 场景 1：超标报销 → 人工审批（Path A）

```bash
# Step 1: 提交超标报销（酒店超标）
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
          "agentId": "expense-review-main",
          "sessionId": "s1"
        },
        "parts": [{"text": "帮我审核这笔报销：机票5000，酒店3晚每晚800共2400，客户晚餐800"}]
      }
    }
  }'
```

**预期**：流式输出 analyze→policy→audit 后，收到 `INPUT_REQUIRED` 状态和审批提示。

```bash
# Step 2: 经理审批（替换 <TASK_ID> 为上一步返回的 taskId）
curl -s -X POST http://localhost:8081/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-002",
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-002",
        "taskId": "<TASK_ID>",
        "contextId": "ctx-1",
        "metadata": {
          "userId": "u1",
          "agentId": "expense-review-main",
          "sessionId": "s1"
        },
        "parts": [{"text": "approved"}]
      }
    }
  }'
```

**预期**：Workflow 恢复 → COMPLETED，返回审核结果。

### 场景 2：合规报销 → 自动通过（Path B）

```bash
curl -s -X POST http://localhost:8081/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-003",
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-003",
        "contextId": "ctx-2",
        "metadata": {
          "userId": "u1",
          "agentId": "expense-review-main",
          "sessionId": "s2"
        },
        "parts": [{"text": "审核这笔报销：机票3000，酒店2晚每晚500共1000，餐费200"}]
      }
    }
  }'
```

**预期**：流式输出后直接 COMPLETED（无需人工审批）。

### 场景 3：直接测试 Workflow Agent（单终端）

```bash
# 直接调用 Workflow Agent（绕过 Main ReActAgent）
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"req-001","method":"SendStreamingMessage","params":{"message":{
    "role":"ROLE_USER","messageId":"m1","contextId":"c1",
    "metadata":{"userId":"u1","agentId":"expense-review-workflow","sessionId":"s1"},
    "parts":[{"text":"审核：机票5000，酒店3晚800共2400，晚餐800"}]}}}'
```

### AgentCard 发现

```bash
curl -s http://localhost:8080/.well-known/agent-card.json | jq .
# 验证: .name == "expense-review-workflow"
# 验证: .skills[0].id == "review_expense"
```

## SIT 功能点覆盖

| 功能点 | 场景 | 验证方式 |
|--------|------|---------|
| Workflow 多步骤 DAG | 场景 1/2 | 流式输出含 analyze/audit/approve 等多步 chunk |
| LLM 节点执行 | 场景 1 | analyze 提取条目→audit 比对合规 |
| Tool 节点执行 | 场景 1 | check_policy 返回公司政策 |
| 中断-续接 | 场景 1 | INPUT_REQUIRED → 用户输入 → COMPLETED |
| 条件路由 Path A | 场景 1 | risk=high → Questioner(approve) |
| 条件路由 Path B | 场景 2 | risk=none → LLM(auto_approve) |
| Session Checkpoint | 重启后 resume | 进程重启后用同一 taskId 恢复 |
| 邻接互调 | 场景 1/2 | MainAgent 发现 review_expense skill → 远程调用 |
| AgentCard 发现 | curl card 端点 | name/skills/version 正确 |
