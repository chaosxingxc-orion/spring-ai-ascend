# A2A 远程调用

agent-runtime 支持 Agent 将其他 A2A Agent 作为 Tool 调用。配置远端 URL 后，runtime 自动发现对方能力并注册为本地 Tool，LLM 按需调用。

## 1. 概述

```yaml
# 最小示例：配置一个远程 Agent
agent-runtime:
  remote-agents:
    - url: http://weather-agent:18081
```

配置后，远程 Agent 的 skills 自动注册为本地 Agent 的 Tool，LLM 在工具列表中看到并可调用。

## 2. 快速开始

### 配置远程 Agent

```yaml
agent-runtime:
  remote-agents:
    - url: http://weather-agent:18081
    - url: http://hotel-agent:18082
```

### 启动远程 Agent

```bash
java -jar weather-agent.jar --server.port=18081
java -jar hotel-agent.jar --server.port=18082
```

### 调用

```bash
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"SendStreamingMessage","params":{
    "message":{"role":"ROLE_USER","messageId":"m1",
    "parts":[{"text":"帮我查北京天气并订个酒店"}]}}}' --no-buffer
```

LLM 自动依次调用 `query_weather` 和 `search_hotels`，汇总返回。

## 3. 工作原理

```
远程 Agent Card 声明 skills
  │
  ▼ 主 Agent 启动
RemoteAgentCardCache: 拉取远程 Card → 解析 skills
  │
  ▼
RemoteAgentToolSpec: skill.description → LLM 看到的 Tool 描述
  │
  ▼
OpenJiuwenRemoteToolInstaller: 注册为本地 Agent 的 Tool
  │
  ▼ LLM 调用远程 Tool
OpenJiuwenRemoteAgentInterruptRail: 拦截 → InterruptRequest
  │
  ▼
A2aRemoteInvocationOrchestrator:
  ├─ outbound: POST /a2a SendStreamingMessage → 远程 Agent
  ├─ inbound: 远程结果 → toolResult
  └─ resume: 回灌本地 Agent → LLM 继续推理
```

> **关键约束：只有 skills 非空的 Agent Card 才会被注入为 Tool。** 如果远程 Agent 的 skills 为空，LLM 看不到它。

## 4. 核心接口

```java
// 远程 Tool 安装器（自动执行，无需手动调用）
// OpenJiuwenRemoteToolInstaller 发现 handler Bean
// → 从 RemoteAgentCardCache 获取所有远端 ToolSpec
// → 注册为 openJiuwen Tool

// 远端 Tool 中断 Rail（自动注册）
// OpenJiuwenRemoteAgentInterruptRail
// → 拦截 tool call → 创建 InterruptRequest → 远端 A2A 调用
```

## 5. 能力详述

### Card 刷新策略

| 阶段 | 间隔 | 行为 |
|------|------|------|
| 快速重试 | 10s | Card 不可达时快速重试 |
| 保活 | 600s | 所有 Card 可达后长间隔 |
| 指数退避 | 上限 600s | 持续失败时退避 |
| 故障降级 | — | 不可达的标记 pending，后台重试 |

**不支持动态发现**：远程端点必须通过 YAML 配置声明。刷新仅更新已配置 URL 的状态。

### LLM 看到的 Tool

```
可用工具：
- a2a_remote_weather_agent: Weather Agent
    Provides weather information for cities
- a2a_remote_hotel_agent: Hotel Agent
    Searches and books hotels
```

### 中断-续接

当远程 Agent 返回 `INPUT_REQUIRED` 时：

1. 父 Task 进入 `INPUT_REQUIRED`，metadata 保存路由信息
2. 用户第二轮输入到达后，直接续写远程（不经本地 LLM）
3. 远程返回 `COMPLETED` 后，结果回灌本地 Agent，LLM 继续推理

### Agent Card 消费字段

`RemoteAgentCardCache` 仅消费以下字段：

| Card 字段 | 用途 |
|----------|------|
| `name` | 生成 remoteAgentId |
| `description` | 拼入 Tool description |
| `skills[].description` | LLM 看到的 Tool 描述 |
| `supportedInterfaces[].url` | 出站调用 endpoint |

## 6. 完整示例

```yaml
# 主 Agent（旅行助手，8080）配置 3 个远程
agent-runtime:
  remote-agents:
    - url: http://localhost:18081    # 天气 Agent
      stream-timeout: 30s
    - url: http://localhost:18082    # 酒店 Agent
      stream-timeout: 30s
    - url: http://localhost:18083    # 航班 Agent
      stream-timeout: 60s
```

预期结果：LLM 看到 3 个远程 Tool，根据用户意图自动选择调用。

## 7. 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `agent-runtime.remote-agents[N].url` | String | — | 远程 Agent URL（必填以激活） |
| `agent-runtime.remote-agents[N].stream-timeout` | Duration | — | 流式调用超时 |
| `agent-runtime.remote-agents[N].output.default-target` | String | — | 默认输出目标（USER/LLM/BOTH） |
| `agent-runtime.remote-agents[N].output.completion-target` | String | — | 完成时输出目标 |

## 8. 限制

| 限制 | 影响 | 替代 |
|------|------|------|
| 仅单层远程调用 | A→B→C 链式调用不支持 | 每层独立配置 |
| 不支持嵌套调用 | resume 后不能再次请求远程 | LLM 一次性输出所有 tool call |
| 远程端点需静态配置 | 新增远程需修改 YAML 重启 | — |
| 仅 OpenJiuwen 支持远程 Tool | AgentScope 不能发起远程调用 | 使用 OpenJiuwen 作为主 Agent |
| 无 skills 的远程 Agent 不会被注入 | 需在远程 Agent 的 Card 中声明 skills | — |

## 9. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/remote-agent-orchestration-design.md`
- [A2A 协议调用](a2a-endpoints.md)
- [Agent Card 配置](agent-card-configuration.md)
- [OpenJiuwen Adapter](openjiuwen-adapter.md)
- Example：`examples/agent-runtime-a2a-remote-agent-tool-e2e/`
