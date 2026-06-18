# agent-runtime openJiuwen Simple Example

最简示例：将一个 openJiuwen ReActAgent 挂载到 agent-runtime，通过 A2A 协议暴露。

## 目录

```
agent-runtime-openjiuwen-simple/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/.../openjiuwensimple/
    │   │   ├── OpenJiuwenSimpleApplication.java          # Spring Boot 入口
    │   │   └── OpenJiuwenSimpleAgentConfiguration.java   # 注册 openJiuwen Handler
    │   └── resources/
    │       └── application.yaml                          # LLM 连接配置
    └── test/
        └── java/.../openjiuwensimple/
            ├── OpenJiuwenSimpleE2eTest.java              # 端到端验证
            └── SampleA2aClient.java                      # A2A 测试客户端
```

## 快速开始

### 1. 前置条件

- JDK 17+
- 可用的 LLM 端点（OpenAI 兼容接口）
- 先在本地安装 `agent-runtime`：

```bash
mvn install -DskipTests
```

### 2. 配置 LLM 连接

设置环境变量（或直接编辑 [application.yaml](src/main/resources/application.yaml)）：

```bash
export SAA_SAMPLE_LLM_API_KEY="sk-your-key"
export SAA_SAMPLE_OPENJIUWEN_API_BASE="http://localhost:4000/v1"  # LLM 代理地址
export SAA_SAMPLE_LLM_MODEL="gpt-5.4-mini"
```

### 3. 启动 Runtime

```bash
mvn spring-boot:run -f examples/agent-runtime-openjiuwen-simple/pom.xml
```

### 4. 验证 Agent Card

```bash
curl -s http://localhost:8080/.well-known/agent-card.json | jq .
```

### 5. 发送消息（A2A JSON-RPC）

```bash
SESSION_ID="test-$(date +%s)"

# 发送消息（A2A SendStreamingMessage — 流式返回）
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "id": "1",
    "params": {
      "metadata": {
        "userId": "test-user",
        "agentId": "openjiuwen-simple-agent"
      },
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "contextId": "'"$SESSION_ID"'",
        "parts": [{"text": "你好，请介绍一下自己"}]
      }
    }
  }' --no-buffer
```

## 开发步骤讲解

### Step 1 — 继承 OpenJiuwenAgentRuntimeHandler

```java
static final class MyHandler extends OpenJiuwenAgentRuntimeHandler {
    public MyHandler() {
        super("my-agent-id");  // agentId 需要与 AgentCard 一致
    }
}
```

- `OpenJiuwenAgentRuntimeHandler` 是 agent-runtime 提供的 SPI 基类
- 它已经帮你处理了：A2A 消息转换、streaming、会话管理、结果映射
- 你只需要关注 agent 的创建

### Step 2 — 实现 createOpenJiuwenAgent()

```java
@Override
protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
    // 2a. 创建 openJiuwen AgentCard
    AgentCard card = AgentCard.builder()
            .id("my-agent-id")
            .name("My Agent")
            .description("...")
            .build();

    // 2b. 创建 ReActAgent + 配置 system prompt + model client
    ReActAgent agent = new ReActAgent(card);
    ReActAgentConfig config = ReActAgentConfig.builder()
            .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
            .maxIterations(5)
            .build()
            .configureModelClient("openai", apiKey, apiBase, modelName, sslVerify);

    agent.configure(config);
    return agent;
}
```

### Step 3 — 注册为 Spring Bean

```java
@Configuration(proxyBeanMethods = false)
public class MyConfiguration {

    @Bean
    OpenJiuwenAgentRuntimeHandler myHandler(...) {
        return new MyHandler();
    }
}
```

Handler bean 是唯一需要注册的 bean。runtime 会根据 handler 的 `agentId` 自动生成 A2A AgentCard；
如需自定义卡片信息，在 `application.yaml` 中配置：

```yaml
agent-runtime:
  access:
    a2a:
      agent-card:
        name: my-agent-id
        description: 我的自定义 agent
```

| 方式 | 说明 |
|---|---|
| 不配置 | runtime 自动从 handler.agentId() 生成 AgentCard |
| YAML 配置 | 在 `application.yaml` 中自定义卡片字段 |
| 自定义 Bean | 注册 `@Bean AgentCard` 完全接管 |

### Spring Boot 入口

```java
@SpringBootApplication(scanBasePackages = {
    "your.package",                   // 你的配置类所在包
    "com.huawei.ascend.runtime.boot"  // agent-runtime 自动装配（A2A 端点等）
})
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

## 配置说明

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `sample.openjiuwen.model-provider` | `openai` | 模型提供商 |
| `sample.openjiuwen.api-key` | `sk-local-placeholder` | LLM API Key |
| `sample.openjiuwen.api-base` | `http://localhost:4000/v1` | LLM API 地址 |
| `sample.openjiuwen.model-name` | `gpt-5.4-mini` | 模型名称 |
| `sample.openjiuwen.ssl-verify` | `true` | SSL 证书校验 |
| `agent-runtime.access.a2a.agent-card.name` | `openjiuwen-simple-agent` | A2A AgentCard 名称 |
| `agent-runtime.access.a2a.agent-card.description` | 示例描述 | A2A AgentCard 描述 |
| `agent-runtime.access.a2a.agent-card.version` | `0.1.0` | 版本号 |
| `agent-runtime.access.a2a.agent-card.organization` | `spring-ai-ascend` | 组织名 |
| `agent-runtime.access.a2a.agent-card.organization-url` | `http://localhost:8080` | 组织 URL |
| `agent-runtime.access.a2a.agent-card.endpoint` | `/a2a` | A2A 端点路径 |

## 运行测试

```bash
SAA_SAMPLE_LLM_API_KEY="sk-your-key" \
SAA_SAMPLE_OPENJIUWEN_API_BASE="http://localhost:4000/v1" \
  mvn test -f examples/agent-runtime-openjiuwen-simple/pom.xml \
    -Dtest=OpenJiuwenSimpleE2eTest
```

## 声明

此示例仅为演示目的，展示最简集成路径。生产环境请补充错误处理、安全配置和可观测性。
