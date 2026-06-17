# agent-runtime-a2a-versatile-parent-e2e 手工验证指南

## 概述

本示例演示**主 Agent（OpenJiuwen LLM）调用子 Agent（Versatile）**的 A2A 父-子代理场景，
包含结构化 metadata 透传、中断检测、输出缓存、target 路由等新能力。

## 架构

```
用户 → 主 Agent (OpenJiuwen ReAct LLM, profile=main, port=18080)
        │
        ├─ LLM 通过 a2a_remote_versatile_child 工具调用子 Agent
        │
        └─→ 子 Agent (Versatile REST SSE proxy, profile=versatile, port=18082)
              │
              ├─ 从 metadata.versatile 还原真实 Versatile REST 请求
              ├─ 中间 OUTPUT(target=USER) → 透传 artifact 给用户
              ├─ HTTP 断开, 无 End 节点 → INTERRUPTED → INPUT_REQUIRED
              └─ 收到 End 节点 → COMPLETED(target=LLM) → 注入 LLM 继续 ReAct
```

## 前置条件

1. JDK 21+
2. Maven 3.9+
3. 在项目根目录 (`spring-ai-ascend/`) 下执行所有命令
4. （场景 B 必需）LLM API Key —— 见下方说明

---

## 验证步骤

### 步骤 1：编译并安装 agent-runtime

```bash
mvn install -pl agent-runtime -am -DskipTests -q
```

预期输出：`BUILD SUCCESS`

---

### 步骤 2：运行 agent-runtime 全部单元测试（193 个）

```bash
mvn test -pl agent-runtime
```

预期输出：
```
Tests run: 193, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

### 步骤 3：运行新 example 模块测试

```bash
mvn test -f examples/agent-runtime-a2a-versatile-parent-e2e/pom.xml
```

#### 场景 A：无 LLM API Key（3 个跳过 + 1 个通过）

```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 3
BUILD SUCCESS
```

- `agentCardIsDiscoverable` ✅ — 验证 Versatile 子 Agent 的 A2A AgentCard 可发现
- 其余 3 个 LLM 测试 ⏭️ 跳过

#### 场景 B：有 LLM API Key（4 个全部通过）

需要先配置 LLM API Key，测试才能走真实的 OpenJiuwen ReAct LLM 调用远程子 Agent 的完整链路。

**配置环境变量：**

```bash
# 必须设置 —— LLM API Key（不设则 LLM 测试跳过）
export SAA_SAMPLE_LLM_API_KEY=sk-your-api-key

# 可选 —— LLM 提供商、代理地址、模型名（以下为默认值）
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
export SAA_SAMPLE_OPENJIUWEN_API_BASE=http://localhost:4000/v1
export SAA_SAMPLE_LLM_MODEL=gpt-5.4-mini

# 可选 —— SSL 验证开关（默认为 true，自建代理通常需要关掉）
export SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY=false
```

> **环境变量与配置文件的对应关系：**
>
> 这些变量在 `application-main.yaml` 中被引用：
> ```yaml
> sample.versatile-parent.llm.model-provider: ${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}
> sample.versatile-parent.llm.api-key:      ${SAA_SAMPLE_LLM_API_KEY:}
> sample.versatile-parent.llm.api-base:     ${SAA_SAMPLE_OPENJIUWEN_API_BASE:}
> sample.versatile-parent.llm.model-name:   ${SAA_SAMPLE_LLM_MODEL:deepseek-chat}
> sample.versatile-parent.llm.ssl-verify:   ${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:true}
> ```
> `SAA_SAMPLE_LLM_API_KEY` 为空时，`assumeTrue` 会跳过所有 LLM 测试。

---

### 步骤 4：手工启动双进程验证（核心验证）

默认配置（`versatile.url=http://localhost:18083`）已指向本地 mock 服务。子 Agent 启动时会通过 `VersatileMockConfiguration` 自动在端口 18083 启动内嵌 WireMock，无需额外终端。

如需使用外部 Versatile 服务，添加 `--versatile.mock.enabled=false --versatile.url=<外部URL>`。

#### 终端 1 — 启动 Versatile 子 Agent（端口 18082）

```bash
cd examples/agent-runtime-a2a-versatile-parent-e2e
mvn spring-boot:run -Dspring-boot.run.profiles=versatile
```

日志出现 `Versatile mock server started on port 18083` 表示 mock 已就绪。

#### 终端 2 — 启动主 Agent（OpenJiuwen LLM，端口 18080）

```bash
cd examples/agent-runtime-a2a-versatile-parent-e2e
mvn spring-boot:run -Dspring-boot.run.profiles=main
```

---

### 步骤 5：两轮酒店预订验证

**场景**：用户订北京酒店（3/30 → 4/3，入住人李四）。LLM 通过 skill 指导自动将自然语言参数封装为 Versatile 要求的 JSON 结构，分两轮完成查询和预订。

#### 5.1 第一轮 — 查询酒店列表

发送自然语言请求，LLM 根据 hotel-booking skill 提取参数并格式化为 JSON：

```bash
SESSION_ID="ctx-hotel-$(date +%s)"

curl -s -X POST http://localhost:18080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "id": "1",
    "params": {
      "metadata": {
        "userId": "test-user",
        "agentId": "main-parent",
        "versatile": {
          "headers": {},
          "query": {
            "type": "controller",
            "workspace_id": "10"
          }
        }
      },
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "contextId": "'"$SESSION_ID"'",
        "parts": [{"text": "请帮我预订一家北京的酒店，3月30日入住，4月3日退房，我叫李四。"}]
      }
    }
  }' --no-buffer
```

**预期行为**：
1. SSE 流中看到 `TaskArtifactUpdateEvent` — 透传 Versatile 返回的酒店列表（hotels_info）
2. 最终状态 `TASK_STATE_INPUT_REQUIRED`，父任务记录远端 task/context 路由信息，等待用户选择酒店

保存第一轮 SSE 输出，从中提取 `taskId` 和酒店名，用于第二轮：

```bash
# 将第一轮的所有 SSE 输出保存到文件（也可以用 tee 同时输出到终端）
curl ... --no-buffer > /tmp/round1.sse

# 提取 TASK_ID（第一个 taskId 即为父任务 ID）
TASK_ID=$(grep -o '"taskId":"[^"]*"' /tmp/round1.sse | head -1 | cut -d'"' -f4)
echo "TASK_ID=$TASK_ID"

# 从酒店列表中提取一个真实酒店名（SSE 输出中转义了引号，用 -P 匹配）
HOTEL_NAME=$(grep -oP "hotel_name\\\\?\":\\\\?\"\K[^\"\\\\]+" /tmp/round1.sse | head -1)
echo "HOTEL_NAME=$HOTEL_NAME"
```

**关键验证点** — 主 Agent 日志中检查 LLM 封装出了正确的 JSON `query`：

```
Executing tool: a2a_remote_versatile_child with args: {"query":"{\"person_name\":\"李四\",...}","intent":"订酒店"}
```

子 Agent 日志中检查 query 正确到达 Versatile REST API：

```
versatile body query extracted chars=xxx
```

#### 5.2 第二轮 — 确认预订

第二轮携带相同 `taskId` 发送，系统识别为 remote continuation，**直接路由到远端子 Agent**（不经主 Agent LLM）。因此 `parts[0].text` 需按 Versatile 子 Agent 的输入格式填写。

酒店名使用第一轮输出中提取到的真实名称（`$HOTEL_NAME`），不要用不存在的酒店名：

```bash
curl -s -X POST http://localhost:18080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "id": "2",
    "params": {
      "metadata": {
        "userId": "test-user",
        "agentId": "main-parent",
        "versatile": {
          "headers": {},
          "query": {
            "type": "controller",
            "workspace_id": "10"
          }
        }
      },
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-002",
        "contextId": "'"$SESSION_ID"'",
        "taskId": "'"$TASK_ID"'",
        "parts": [{"text": "{\"inputs\":{\"query\":\"{\\\"hotel_name\\\":\\\"'"$HOTEL_NAME"'\\\"}\",\"intent\":\"LATEST\",\"wap_userName\":\"张三\"}}"}]
      }
    }
  }' --no-buffer
```

**预期行为**：
1. SSE 流中 `TASK_STATE_COMPLETED`
2. 回复包含预订确认信息：酒店名称、订单号、日期、价格

**关键验证点**：
1. 主 Agent 日志中走 `remote continuation` 路径（`[A2A] remote tool invocation resume`），**不经过** LLM 的 tool call
2. 远端 Versatile 收到的 body 为 `{"inputs":{"query":"{\"hotel_name\":\"美居宾馆\"}","intent":"LATEST",...}}`

#### 5.3 验证结果提取

子 Agent 的 `application-versatile.yaml` 中配置了提取规则：

```yaml
versatile:
  result-extractions:
    - match: hotel_book_success
      get: ticket
```

**配置含义**：
- `match` — 匹配 SSE 行中任意位置包含的关键字（不限于 event 名）
- `get` — 在 JSON 树中深度搜索该 key，找到后返回其值

当 Versatile 返回的 SSE 行包含 `hotel_book_success` 且 JSON 中找到 `ticket` key 时，提取 ticket 值，缓存到 `node_type=End` 后作为 COMPLETED(LLM) 的结果返回给主 Agent。

主 Agent 日志中可验证：

```
versatile extracted result match='hotel_book_success' get='ticket'
```

---

### 步骤 6：确认远程工具注册成功（关键检查点）

在终端 2（主 Agent）的日志中依次检查：

**6.1 Skill 加载：**
```
registering skill path=skills absolute=/.../skills exists=true
skill registered hasSkillUtil=true hasSkill=true skillCount=1
```

**6.2 远程 Card 解析成功：**
```
remote agent tool description assembled name=versatile-child skillsCount=1
```

**6.3 远程工具已安装到 OpenJiuwen Handler：**
```
installed remote tool installer into openjiuwen handler agentId=main-parent
```

**6.4 LLM 实际调用工具：**
```
remote tool invocation start taskId=... toolName=a2a_remote_versatile_child
```

---

## 关键验证点汇总

| # | 验证点 | 如何验证 |
|---|--------|---------|
| 1 | **Skill 加载** | 日志 `hasSkillUtil=true hasSkill=true skillCount=1` |
| 2 | **LLM 按 skill 封装 JSON** | 日志 `Executing tool: a2a_remote_versatile_child with args: {"query":"{\"...\"}","intent":"..."}` |
| 3 | **两轮 intent 切换** | 第一轮 `intent=订酒店`，第二轮 `intent=LATEST` |
| 4 | **酒店列表透传** | SSE artifact 中出现 `hotels_info` 事件 |
| 5 | **预订结果提取** | `versatile extracted result match='hotel_book_success' get='ticket'` |
| 6 | **LLM 最终总结** | COMPLETED 消息包含酒店名、订单号、日期、价格 |
| 7 | **Remote Agent 配置** | `output.default-target=USER` + `completion-target=LLM` 生效 |

---

## Mock 测试（离线，无需外部 Versatile 服务）

本示例包含一个基于 WireMock 的 Versatile API 模拟服务 `VersatileMockService`，可以完全不依赖外部 Versatile REST API 运行 E2E 测试。

**Mock 行为（默认中断流程）：**

| 轮次 | Mock 响应 | child 结果 |
|------|----------|-----------|
| 第一轮 | `hotels_info` 事件（无 End） | INPUT_REQUIRED（等待用户选择酒店） |
| 第二轮 | `hotel_book_success` 事件 + End | COMPLETED（含 ticket 提取，返回 LLM） |

JUnit 测试可按需切换：`stubBookingFlow()`（第一轮也带 End → COMPLETED）或 `stubInterruptFlow()`（默认）。

**运行测试：**

```bash
cd examples/agent-runtime-a2a-versatile-parent-e2e

# 需要 LLM（API Key 已配置）
SAA_SAMPLE_LLM_API_KEY=sk-x00550472 SAA_SAMPLE_LLM_MODEL=gpt-5.4-mini mvn test

# 无 LLM — card discovery 测试仍可运行，LLM 测试自动跳过
mvn test
```

**Mock 架构：**
```
JUnit @BeforeEach → VersatileMockService.start()   (随机端口)
                 → --versatile.url=http://localhost:{mockPort}/...
                 → child 连接 mock 而非真实 API
JUnit @AfterEach  → VersatileMockService.stop()
```

测试不依赖外部 `http://7.213.200.213:3001`，可以在任何网络环境运行。

---

## 常见问题

### Q: 启动报 "port already in use"
```bash
# 杀掉占用端口的进程，或临时覆盖端口
mvn spring-boot:run -Dspring-boot.run.profiles=versatile \
    -Dspring-boot.run.arguments="--server.port=18092"
```

### Q: 主 Agent 找不到子 Agent
```bash
curl -s http://localhost:18082/.well-known/agent-card.json | python3 -m json.tool | grep -E '"name"|"skills"'
```

### Q: LLM 调用子 Agent 工具但没响应
确认 LLM API Key 和代理地址正确：
```bash
curl -s http://localhost:4000/v1/models
```
