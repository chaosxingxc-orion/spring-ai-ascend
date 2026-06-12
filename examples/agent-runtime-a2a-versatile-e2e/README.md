# agent-runtime A2A Versatile E2E Example

## 概述

启动一个 agent-runtime 实例，注册 Versatile Agent，通过 A2A JSON-RPC 端点调用远端 Versatile 工作流服务，验证端到端链路。

## 目录

```
agent-runtime-a2a-versatile-e2e/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/.../versatile/
    │   │   ├── VersatileA2aRuntimeApplication.java   # Spring Boot 入口
    │   │   ├── VersatileAgentConfiguration.java      # 注册 Versatile Handler
    │   │   └── ManualVersatileClient.java            # 控制台手工测试客户端
    │   └── resources/
    │       └── application.yaml                      # Versatile 连接配置
    └── test/
        └── java/.../versatile/
            └── VersatileA2aE2eTest.java              # @SpringBootTest 手工测试
```

## 配置

`application.yaml` 中的 versatile 配置支持环境变量覆盖：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `VERSATILE_URL` | `http://7.213.200.213:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}` | URL 模板 |
| `VERSATILE_URL_VARIABLES_*` | `project_id=mock_project_id`, `agent_id=fb723468...` | 模板变量 |
| `VERSATILE_QUERY_PARAMS_*` | `type=controller`, `workspace_id=10` | 查询参数 |
| `VERSATILE_INPUT_METADATA_KEYS` | `intent,wap_userName` | 从 A2A metadata 合入 body 的字段 |

## 运行方式

### 方式 A：两个终端手工测试

**终端 1** — 启动 Runtime：
```bash
mvn spring-boot:run -f examples/agent-runtime-a2a-versatile-e2e/pom.xml
```

**终端 2** — 启动客户端：
```bash
mvn exec:java -f examples/agent-runtime-a2a-versatile-e2e/pom.xml \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.versatile.ManualVersatileClient
```

粘贴 URL 行（从 curl 命令中复制 host:port 之后的部分）和 JSON body：

```
Runtime URL [http://localhost:8080]: ↵
Paste URL line> /v1/mock_project_id/agents/fb723468.../conversations/test-001?type=controller&workspace_id=10
Paste JSON body> {"inputs":{"query":"预订酒店","intent":"订酒店","wap_userName":"张三"}}

── Sending via A2A ──
  conversation_id: test-001
  query: 预订酒店
  metadata: {intent=订酒店, wap_userName=张三}

  [TASK_STATE_SUBMITTED]
  [TASK_STATE_WORKING]
  ... hotel data ...
  [TASK_STATE_COMPLETED]
```

### 方式 B：Spring Boot 测试

```bash
mvn test -f examples/agent-runtime-a2a-versatile-e2e/pom.xml \
  -Dtest=VersatileA2aE2eTest
```

### 方式 C：JAR 包

```bash
# 构建
mvn package -f examples/agent-runtime-a2a-versatile-e2e/pom.xml -DskipTests

# 启动（可选：用外部配置文件覆盖默认值）
java -jar target/agent-runtime-a2a-versatile-e2e-example-*.jar \
  --spring.config.additional-location=file:./my-versatile.yaml
```

## 测试

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `VersatileClientTest` | 6 | HTTP body、Content-Type、SSE 流、错误 |
| `VersatileStreamAdapterTest` | 11 | 事件映射、过滤、自定义事件 |
| `VersatileMessageAdapterTest` | 7 | URL 模板、metadata 合入、路径安全 |
| `VersatileA2aE2eTest` | 2 | 端到端：Agent Card + 流式消息 |
