---
scope: version
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-005
status: active
dependency:
  - agent-runtime-release-features.cn.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-005-remote-agent-orchestration.md
---

# 远程 Agent 编排 — 黑盒行为说明

## 1. 特性定位

agent-runtime 作为 A2A 客户端接入和调用其他 A2A Agent，实现跨 Agent 协作。远程 Agent 通过 YAML 配置静态接入，runtime 自动拉取 Agent Card、缓存维护本地目录、生成工具描述、安装为本地 Agent 可调用的 Tool。当 LLM 调用远程 Tool 时，走中断-续接流水线：本地 Agent 挂起 → 远程调用 → 等待结果 → 回灌本地 Agent 继续推理。

- **解决的问题**：单个 Agent 能力有限，需要将专业任务委托给其他 Agent。A2A 协议提供了标准的跨 Agent 通信方式，无需 Agent 之间共享代码或状态。
- **适用场景**：多 Agent 协作（旅行助手调用天气/酒店/航班 Agent）、企业 Agent 生态（主 Agent 调用部门级子 Agent）。如果只需要单一 Agent 完成所有任务，不需要此特性。

## 2. 对外能力边界

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| YAML 配置远程端点 | ✅ | `agent-runtime.remote-agents[N].url` |
| Agent Card 自动拉取 | ✅ | 启动时拉取，自适应刷新 |
| 本地目录维护 | ✅ | sticky remoteAgentId，故障降级 |
| RemoteAgentToolSpec 生成 | ✅ | 从 Card skills 生成，开放 JSON schema；**无 skills 的 Agent Card 不会被注入为 Tool** |
| OpenJiuwen Tool 安装 | ✅ | Placeholder Tool + Interrupt Rail |
| 远程 A2A 调用 | ✅ | `SendStreamingMessage`，独立 streaming |
| 中断-续接 | ✅ | 远程 INPUT_REQUIRED → 父 Task 挂起 → 用户输入 → 续写 |
| Metadata 转发 | ✅ | 入站 metadata → 出站远程调用 |
| 结果回灌 | ✅ | 远程 COMPLETED → InteractiveInput → 本地 Agent resume |
| 父 Task 进度投射 | ✅ | 远程 progress → 父 Task artifact |
| 取消级联传播 | ✅ | 父 Task cancel → 远程 CancelTask |
| 超时检测 | ✅ | REMOTE_TIMEOUT + 孤儿 Task cancel |
| 嵌套远程调用 | ⬜ | resume 后再次请求远程 → 返回错误 NESTED_REMOTE_INVOCATION_UNSUPPORTED |
| Graph/Parallel 编排 | ⬜ | 仅支持单层远程调用 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| 动态服务发现 | 远程端点必须通过 YAML 配置声明，不自动扫描网络 | — |
| 远程 Agent 负载均衡 | 不属于 agent-runtime 职责 | 在反向代理层实现 |
| 远程调用的认证 | A2A 认证属于协议层，不属于编排层 | 通过 A2A SDK 认证扩展 |

## 3. 外部行为与用户场景

### 3.1 外部接口

| API | 说明 |
|-----|------|
| `agent-runtime.remote-agents` YAML | 配置远程端点 |
| RemoteAgentToolSpec | 被 LLM 看到的工具描述 |
| 父 Task artifact / status | 外部客户端通过 A2A stream 看到的进度和结果 |

### 3.2 用户示例

#### 3.2.1 配置远程 Agent

```yaml
# 主 Agent (8080) 配置两个远程 Agent
agent-runtime:
  remote-agents:
    - url: http://weather-agent:18081
    - url: http://hotel-agent:18082
```

前置条件：远程 Agent 已启动在对应端口，Agent Card 可访问。预期结果：主 Agent 的 LLM 工具列表中出现 `query_weather` 和 `search_hotels` 两个远程工具。

#### 3.2.2 多 Agent 协作

```bash
# 终端 1: 天气 Agent
java -jar weather-agent.jar --server.port=18081

# 终端 2: 酒店 Agent
java -jar hotel-agent.jar --server.port=18082

# 终端 3: 主 Agent（配置了上述两个远程）
java -jar main-agent.jar --server.port=8080

# 调用：用户只需对主 Agent 说话
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
        "parts": [{"text": "帮我查北京天气并订个酒店"}]
      }
    }
  }'

# 预期结果：主 Agent 的 LLM 自动依次调用 query_weather 和 search_hotels，汇总返回
```

### 3.3 E2E 流程

```
用户: "查北京天气"
  │
  ▼ 主 Agent
  ├─ LLM 看到 tool: query_weather (来自 weather-agent)
  ├─ LLM 调用: query_weather(city="北京")
  │
  ▼ Interrupt Rail 拦截 → RemoteInvocation
  ├─ POST /a2a SendStreamingMessage → weather-agent:18081
  ├─ weather-agent 返回: ArtifactUpdate("晴 22°C") → 父 Task 进度
  └─ weather-agent 返回: COMPLETED → toolResult = "晴 22°C"
  │
  ▼ 回灌主 Agent
  ├─ InteractiveInput.update("tool-call-1", "晴 22°C")
  ├─ LLM resume: "北京今天天气晴朗，气温22°C"
  └─ parent task COMPLETED
```

---
