# agent-trip-a2a — 行程规划 A2A 服务

> 状态：v1。在 `agent-runtime` 上托管 openJiuwen ReAct 行程规划智能体，对外暴露 A2A JSON-RPC 端点；通过 YAML 声明远程调用 [agent-hotel-a2a](../agent-hotel-a2a/)。

## 这个模块解决什么问题

差旅多智能体链路中的**行程规划层**：

- 上游（如 [travel-ascend](../travel-ascend/) 主规划 agent）通过 A2A 调用本模块；
- 本模块理解出差诉求后，通过 `agent-runtime.remote-agents` 远程调用酒店 agent；
- 返回含酒店推荐的 markdown 行程方案。

## 架构

```
HTTP (A2A JSON-RPC)
   │
   ▼
agent-runtime  A2aJsonRpcController  ← POST /a2a
   │
   ▼
TripAgentHandler (OpenJiuwenAgentRuntimeHandler)
   │  ReAct 循环
   │  远程工具 hotel-planning-agent  →  agent-hotel-a2a :8081
   ▼
markdown 行程方案
```

## 文件清单

| 文件 | 作用 |
|---|---|
| [pom.xml](pom.xml) | Spring Boot 可执行 jar，主类 `TripAgentApplication` |
| [TripAgentApplication.java](src/main/java/com/huawei/ascend/examples/trip/a2a/TripAgentApplication.java) | Spring Boot 启动类 |
| [TripAgentConfiguration.java](src/main/java/com/huawei/ascend/examples/trip/a2a/TripAgentConfiguration.java) | Checkpointer / Handler / AgentCard（含 skills） |
| [TripAgentConstants.java](src/main/java/com/huawei/ascend/examples/trip/a2a/TripAgentConstants.java) | Agent id、prompt 变量、远程 hotel 工具名 |
| [SystemPromptBuilder.java](src/main/java/com/huawei/ascend/examples/trip/a2a/SystemPromptBuilder.java) | ReAct system prompt 构造 |
| [application.yaml](src/main/resources/application.yaml) | 端口、LLM、remote-agents 配置 |
| [a2a-fixtures/](a2a-fixtures/) | 手测 JSON 请求样例 |

## 构建与运行

### 1. 前置依赖

```bash
# 在 spring-ai-ascend 根目录安装 agent-runtime
./mvnw clean install -DskipTests

# 安装 agent-hotel 库（hotel-a2a 依赖）
./mvnw -f examples/travel/agent-hotel/pom.xml install
```

### 2. 打包

```bash
./mvnw -f examples/travel/agent-trip-a2a/pom.xml clean package
```

产物：`target/agent-trip-a2a-0.1.0-SNAPSHOT.jar`

### 3. 启动（需 hotel-a2a 已在 8081 运行）

```bash
export LLM_PROVIDER=OpenAI
export LLM_API_BASE=http://your-llm-gateway/v1
export LLM_API_KEY=sk-xxx
export LLM_MODEL=gpt-4o-mini
export LLM_SSL_VERIFY=false
export HOTEL_A2A_BASE_URL=http://localhost:8081

./mvnw -f examples/travel/agent-trip-a2a/pom.xml spring-boot:run
```

默认端口：**13001**（与 travel-ascend 的 `remote-agents` 配置一致）。

### 4. 验证

```bash
# Agent Card 发现（路径在根路径，不是 /a2a 下）
curl http://localhost:13001/.well-known/agent.json

# 同步调用（SendMessage）
curl -X POST http://localhost:13001/a2a \
  -H 'Content-Type: application/json' \
  --data-binary @examples/travel/agent-trip-a2a/a2a-fixtures/trip-round1.json

# 流式调用（SendStreamingMessage，推荐）
curl -X POST http://localhost:13001/a2a \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  --data-binary @examples/travel/agent-trip-a2a/a2a-fixtures/trip-round1-stream.json \
  --no-buffer
```

> A2A 方法名使用 PascalCase：`SendMessage` / `SendStreamingMessage`，不是 `message/send`。

## 配置项

| 属性 / 环境变量 | 默认 | 说明 |
|---|---|---|
| `server.port` | `13001` | HTTP 端口 |
| `trip-agent.llm.model-provider` / `LLM_PROVIDER` | `openai` | openJiuwen 仅支持 OpenAI 兼容 provider 名 |
| `trip-agent.llm.api-key` / `LLM_API_KEY` | 空 | 必须由部署方设置 |
| `trip-agent.llm.api-base` / `LLM_API_BASE` | `http://localhost:4000/v1` | LLM 网关 |
| `trip-agent.llm.model-name` / `LLM_MODEL` | `gpt-4o-mini` | 模型名 |
| `trip-agent.llm.ssl-verify` / `LLM_SSL_VERIFY` | `false` | 内网自签证书设 `false` |
| `agent-runtime.remote-agents[0].url` / `HOTEL_A2A_BASE_URL` | `http://localhost:8081` | hotel-a2a 基址，**不要**加 `/a2a` |

## Agent Card 与远程工具

- Card `name`：`trip-planning-agent`
- 必须声明 **skills**（`skills[].description`），上游 agent 才会通过 `RemoteAgentCardCache` 注册远程工具
- 下游酒店工具名：`hotel-planning-agent`（与 hotel-a2a Card `name` 一致）

## 全链路联调

| 窗口 | 服务 | 端口 |
|---|---|---|
| 1 | agent-hotel-a2a | 8081 |
| 2 | agent-trip-a2a | 13001 |
| 3 | travel-ascend | 8080 |

链路：**8080 → 13001 → 8081**

## Windows 提示

- curl 请用 `curl.exe`，JSON 请求推荐 `--data-binary @文件.json`
- 流式请求：`method` 必须为 `SendStreamingMessage`，且加 `Accept: text/event-stream`
