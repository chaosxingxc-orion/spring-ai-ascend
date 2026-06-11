# agent-hotel-a2a — A2A 服务封装

> 状态：v1。把 [agent-hotel](../agent-hotel/) 库托管到 `agent-runtime` 里，对外暴露 A2A JSON-RPC 端点。

## 这个模块解决什么问题

[agent-hotel](../agent-hotel/) 是个纯 jar 库，对外接口只有 `HotelPlanningAgent.chat(String) -> String`，需要宿主进程内 Java 方法调用。
这一层 wrapper 把它装到一个 Spring Boot 应用里，借助 `agent-runtime` 自动暴露 A2A 协议，让外部进程（trip planner 或别的 agent）通过 HTTP JSON-RPC 调用酒店智能体。

业务代码 0 修改 —— 仅在外层加一层薄壳。

## 架构

```
HTTP (A2A JSON-RPC)
   │
   ▼
agent-runtime 自带的 A2aJsonRpcController  ← /a2a endpoint
   │
   ▼
A2aAgentExecutor (runtime 自动装配)
   │
   ▼
HotelAgentHandler (本模块) extends OpenJiuwenAgentRuntimeHandler
   │ execute(AgentExecutionContext)
   │   ├─ 从 context.getMessages() 抽出最近一条 user 消息文本
   │   └─ 委派给 HotelPlanningAgent.chat(query)
   ▼
HotelPlanningAgent (agent-hotel 库) → openJiuwen ReAct → 工具 → mock 数据 → markdown
   │
   ▼
返回 Stream.of(markdown) — 父类 StreamAdapter 自动包成 result_type=answer
```

## 文件清单

| 文件 | 作用 |
|---|---|
| [pom.xml](pom.xml) | spring-boot-maven-plugin repackage，主类 HotelAgentApplication；依赖 agent-hotel + agent-runtime |
| [HotelAgentApplication.java](src/main/java/com/huawei/ascend/examples/hotel/a2a/HotelAgentApplication.java) | Spring Boot main；`scanBasePackages` 必须包含 `com.huawei.ascend.runtime.boot` 让 runtime 的自动配置生效 |
| [HotelAgentConfiguration.java](src/main/java/com/huawei/ascend/examples/hotel/a2a/HotelAgentConfiguration.java) | 4 个 @Bean：LlmConfig / HotelPlanningAgent / OpenJiuwenAgentRuntimeHandler / AgentCard |
| [HotelAgentHandler.java](src/main/java/com/huawei/ascend/examples/hotel/a2a/HotelAgentHandler.java) | extends OpenJiuwenAgentRuntimeHandler，execute() 抽 query 委派 chat() |
| [application.yaml](src/main/resources/application.yaml) | server.port + hotel-agent.llm.* 配置 |

## 构建与运行

### 1. 先装好库依赖

```bash
# 装 agent-hotel 库到本地仓
mvn -f examples/travel/agent-hotel/pom.xml -DskipTests install
```

`agent-runtime` 已经在根 reactor 里，不用单独装。

### 2. 打可执行 jar

```bash
mvn -f examples/travel/agent-hotel-a2a/pom.xml clean package
```

产物：`examples/travel/agent-hotel-a2a/target/agent-hotel-a2a-0.1.0-SNAPSHOT.jar`（Spring Boot 可执行 jar）。

### 3. 启动服务

```bash
# 方式 A：环境变量传 LLM 配置
export LLM_PROVIDER=OpenAI
export LLM_API_BASE=http://api.bigmodel.dev.huawei.com/v1
export LLM_API_KEY=sk-xxx
export LLM_MODEL=deepseek-v4-pro
export LLM_SSL_VERIFY=false
java -jar examples/travel/agent-hotel-a2a/target/agent-hotel-a2a-0.1.0-SNAPSHOT.jar

# 方式 B：直接传 Spring 属性
java -jar examples/travel/agent-hotel-a2a/target/agent-hotel-a2a-0.1.0-SNAPSHOT.jar \
    --hotel-agent.llm.provider=OpenAI \
    --hotel-agent.llm.api-base=http://api.bigmodel.dev.huawei.com/v1 \
    --hotel-agent.llm.api-key=sk-xxx \
    --hotel-agent.llm.model-name=deepseek-v4-pro

# 方式 C：开发期直接 spring-boot:run
mvn -f examples/travel/agent-hotel-a2a/pom.xml spring-boot:run
```

服务默认监听 `8081`，A2A endpoint 在 `/a2a`。

### 4. 调用验证

```bash
# 健康检查（runtime 自带的 actuator 或 GET /a2a/.well-known/agent.json）
curl http://localhost:8081/a2a/.well-known/agent.json

# 发一个酒店规划请求（A2A JSON-RPC）
curl -X POST http://localhost:8081/a2a \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-1",
    "method": "message/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"text": "员工 zhang3 出差北京 2026-06-16 至 2026-06-18，4 星，≤800 元，全季/亚朵优先"}]
      }
    }
  }'
```

## LLM 配置项

| 属性 / 环境变量 | 默认 | 说明 |
|---|---|---|
| `hotel-agent.llm.provider` / `LLM_PROVIDER` | `OpenAI` | openJiuwen 0.1.12 仅支持 `[OpenAI, OpenRouter, SiliconFlow, DashScope, InferenceAffinity]` |
| `hotel-agent.llm.api-base` / `LLM_API_BASE` | `http://localhost:4000/v1` | OpenAI 协议兼容网关地址 |
| `hotel-agent.llm.api-key` / `LLM_API_KEY` | `sk-local-placeholder` | 必须由部署方覆盖 |
| `hotel-agent.llm.model-name` / `LLM_MODEL` | `gpt-4o-mini` | 实际模型名（如 `deepseek-v4-pro`） |
| `hotel-agent.llm.ssl-verify` / `LLM_SSL_VERIFY` | `false` | 内网自签证书设 `false` |

## 设计取舍

- **委派 vs 重建 ReActAgent**：选委派（直接调 `HotelPlanningAgent.chat()`），匹配 [agent-hotel](../agent-hotel/) §4.1 "跨调用无状态" 的契约。trip planner 需要多轮上下文时，自己把对话历史拼到 NL 里重发一次，runtime 这一侧无需 conversation_id 透传。
- **HotelPlanningAgent 单例 vs 每请求新建**：选单例 + Spring 生命周期管理；`@Bean(destroyMethod = "close")` 在 context 关闭时清理 `Runner.resourceMgr()` 里的工具注册。当前对单 agent 实例的并发安全未做验证，A2A 高并发场景下如出现状态串扰，再切换到 "每请求新建 ReActAgent + 共享 MockHotelInventory"。
- **不接 openJiuwen Checkpointer**：HotelPlanningAgent 内部每次自生成 conversation_id（UUID），不依赖跨调用持久化；省一个 bean。

## 已知未验证

- [x] **Spring context 启动**：2026-06-10 09:34 冒烟通过。日志确认 `HotelPlanningAgent` 构造完成，`hotel_search` / `hotel_detail` 已注册到 `ResourceMgr`，A2A `MainEventBusProcessor` 启动正常，Spring Boot 启动耗时 3.3s。
- [x] **AgentCard 暴露**：`GET /.well-known/agent.json` 返回 200，agent card 正确包含 name/description/JSONRPC endpoint。
- [ ] **端口配置**：`application.yaml` 里设 `server.port: 8081` 被忽略，实际 Tomcat 仍跑在 8080。怀疑是 agent-runtime 那边有 application.yaml 或 EnvironmentPostProcessor 优先级更高，未深究 —— 见本仓库下 [runtime 反馈笔记](#runtime-反馈)。
- [ ] **A2A 全链路实跑**：`POST /a2a` 发 `message/send` 调真模型，验证 markdown 回流。
- [ ] **并发**：多线程同时 A2A 调用对单例 HotelPlanningAgent 的影响。
- [ ] **运行时日志**：跑起来看 OpenJiuwenStreamAdapter 是否正确把 String → answer 包好。

## runtime 反馈

记录本次接入过程中发现的可优化点（不阻塞使用，留给 runtime 同事参考）：

1. **`server.port` 被覆盖**：业务侧 `application.yaml` 里设的端口被 runtime 静默覆盖到 8080。Spring Boot 的标准约定是业务 yaml 最高优先级；如果 runtime 真有意把端口收口，建议要么文档明确、要么用 `@ConditionalOnMissingProperty` 让业务可覆盖。
2. **没有 quickstart 用法注释**：`OpenJiuwenAgentRuntimeHandler` 是面向业务 agent 的 SPI 基类，但没有 class-level javadoc 描述"实现者只需要 override 一个 `execute()` 方法"这种关键契约。新接入者只能靠看现有样例来学。
3. **`AgentExecutionContext.getMessages()` 接的是 A2A SDK 的 `org.a2aproject.sdk.spec.Message`**：把 SDK 类型直接漏到 SPI 上，业务 handler 不得不引 A2A SDK 依赖才能抽出 query 文本。考虑在 `AgentExecutionContext` 上加个便利方法 `lastUserText()` 之类的，把 A2A 类型藏起来。否则像本模块这样，业务还得手动调 `OpenJiuwenMessageAdapter.messageText(...)` 静态方法（这又是个"工具类"的奇怪暴露面）。
4. **`OpenJiuwenAgentRuntimeHandler.toOpenJiuwenInput()` 返回 `Object` 而不是 `Map<String,Object>`**：调用方拿到结果第一件事就是 cast，要 `@SuppressWarnings`。返回值类型可以直接收紧。
5. **`AgentCards.create()` 默认 endpoint 写死 `/a2a`、provider URL 写死 `http://localhost:8080`**：自定义场景需要拷一份并改 5 个字段，建议拆成 builder 风格更顺手。
6. **无 health endpoint 标准化**：参考样例靠业务自己起 actuator 或者监听 `/.well-known/agent.json`，但 runtime 应该自带一个 `/health` 给反向代理/K8s 探针用。
