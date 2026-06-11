# Agent Runtime A2A LLM E2E 示例

## 目的

本示例演示如何启动一个 `agent-runtime` 应用：它暴露 A2A 端点，在端点后托管 openJiuwen 和 AgentScope agent，并只从 A2A 客户端视角验证整条链路。

示例位于 `examples/agent-runtime-a2a-llm-e2e`，包含：

- Spring Boot 服务端：`com.huawei.ascend.examples.a2a.OpenJiuwenA2aAgentRuntimeApplication`
- 控制台客户端：`com.huawei.ascend.examples.a2a.A2aConsoleClientApplication`
- 端到端测试：通过本地 OpenAI-compatible LLM gateway 验证 A2A 流程

## 验证内容

本示例验证以下边界：

1. `agent-runtime` 通过 A2A 托管并暴露 agent。
2. 客户端发现 `/.well-known/agent-card.json`。
3. 客户端向 `/a2a` 发送 streaming JSON-RPC 请求。
4. 客户端读取 A2A 流式事件，直到运行完成。
5. 输入 `ping` 时，最终可见回答是 `pong`。
6. 银行零售财富助手样例通过同一个 A2A surface 产出资产配置建议。

当前自动化 E2E 测试覆盖 openJiuwen 以及三种 AgentScope 接入路径：

- `agentscope-react-agent`：AgentScope Java SDK ReAct agent。
- `agentscope-harness-agent`：通过 runtime Harness adapter 包装 AgentScope SDK agent。
- `agentscope-runtime-agent`：AgentScope REST/SSE runtime client 路径，默认使用示例内置的 `/sample/agentscope/process` endpoint。
- `agentscope-retail-wealth-advisor`：基于 AgentScope ReAct agent 和示例 skills 的银行零售财富助手。
- `agentscope-retail-wealth-advisor-harness`：同一个助手通过 runtime Harness adapter 接入。
- `agentscope-retail-wealth-advisor-runtime`：同一个助手通过 AgentScope REST/SSE runtime client 接入，默认使用 `/sample/agentscope/retail-wealth/process`。

## AgentScope 零售财富助手样例

零售财富助手样例模拟客户侧已经用 AgentScope 构建好的 agent：大型银行的业务研发中心用类似点金 skills 的方式组合行内系统和金融能力，给客户经理生成资产配置建议。该样例故意放在 example 模块内：`spring-ai-ascend` 负责运行时治理、A2A、任务状态和输出分发；财富助手业务逻辑属于客户侧 AgentScope 应用。

样例注册了本地 mock skills，用来代表客户环境里的能力：

- 客户画像与适当性查询
- 当前持仓查询
- 行情观点分析
- 银行产品池匹配
- 资产配置收益测算和压力场景测算

样例产品池保持银行财富管理语境：短期限理财产品、公募基金、合格投资者私募基金、黄金产品、ETF 联接基金。样例不推荐个股或场内 ETF，并要求模型输出适当性和合规提示。这些 skills 只用于演示，不构成金融建议。

## Gateway Facade 示例

本模块还包含一个最小 Gateway facade 示例，位于
`com.huawei.ascend.examples.a2a.gateway`。它展示平台层如何维护多个单 agent runtime 实例注册表，并暴露少量 HTTP facade 能力：

- runtime 自注册、续租、注销
- 租户范围内的 agent 列表和 AgentCard 查询
- 按 `tenantId` 和 `agentId` 做路由解析
- 面向客户参考的最小 A2A JSON-RPC 转发端点
- runtime-to-runtime A2A 调用的租户级 `RouteGrant` 签发和校验
- 异步 A2A 交互遥测记录和查询 API

该 facade 示例故意保持本地、内存态。它是面向客户的可插拔 gateway 集成示例，不是生产 `agent-service` 实现。

### Runtime-to-Runtime 发现与遥测

对于东西向 runtime 调用，示例让 examples 层承担发现、路由策略和可观测性职责，但不强制所有 runtime-to-runtime payload 都经过 examples 数据面：

1. source runtime 向 facade 请求短期 `RouteGrant`
2. facade 解析健康的 target runtime 并签发 grant
3. source runtime 直接调用 target runtime A2A 端点
4. target runtime 在接受调用前校验 grant
5. source 和 target runtime 异步上报交互遥测

示例暴露的最小 HTTP 端点：

- `POST /v1/route-grants/resolve`
- `POST /v1/route-grants/validate`
- `POST /v1/a2a-interactions`
- `GET /v1/a2a-interactions?tenantId=...&correlationId=...`

这让 runtime 缓存保持较小：runtime 缓存的是带 TTL 和 policy version 的 scoped grant，而不是完整的 `tenantId x sourceAgentId x targetAgentId x replica` 授权表。

### Gateway DFX 参考形态

Gateway facade 示例不是五个九生产 gateway，但它展示了客户侧平台 facade 应具备的最小 DFX 形态：

- runtime 注册记录携带 TTL / lease 信息
- 过期 lease 标记为 `UNREACHABLE`，且不再可路由
- cold、draining、unreachable、at-capacity runtime fail closed，并返回明确错误码
- 多个 runtime 副本通过同一 route view 解析，只有 `READY` 副本能接收新流量
- A2A 转发端点返回路由解析、响应开始、总转发耗时和选中 runtime instance 的 trace header
- route grant 短期有效、租户级、方法级、带签名
- A2A interaction telemetry 携带 correlation、route latency、first-byte latency、total latency、status 和 selected runtime identity

生产部署仍需补齐持久或可重建的 registry 状态、runtime 身份认证、租户-agent 授权、限流、熔断、多 AZ 部署、同城容灾、跨区域恢复、SLA/SLO 看板和 error-budget 治理。

## 快速开始：配置模板 + 脚本

进入 `examples/agent-runtime-a2a-llm-e2e` 目录后，复制一个模板，填好配置，然后通过 helper 脚本运行：

```bash
cp .env.openai-compatible.example .env   # 或复制 .env.ollama.example 后再编辑
bash scripts/test-e2e.sh .env            # 安装 agent-runtime 并运行 E2E suite
# Windows: ./scripts/test-e2e.ps1 -EnvFile .env
```

手工验证服务端时，推荐使用 server helper 脚本，因为它会在启动 Spring Boot 前加载 env 文件：

```bash
bash scripts/run-server.sh .env
# Windows: ./scripts/run-server.ps1 -EnvFile .env
```

模板说明：你填写的 `.env` 已 gitignore；`*.example` 模板会被提交到仓库。

- `.env.example` — 列出所有变量，并带内联说明。
- `.env.ollama.example` — 本地 Ollama，通过 OpenAI-compatible `/v1` 接口访问，默认模型 `gemma4:latest`。
- `.env.openai-compatible.example` — 云端 OpenAI-compatible API 模板，不包含真实 key。

> `.env` 不会被 Maven 或 Spring Boot 自动加载。helper 脚本会先 source env 文件，再启动 Maven。如果直接运行 `./mvnw ... spring-boot:run`，Java 进程只能看到当前 shell 已经 export 的变量。

> 真实 LLM E2E 测试 `OpenJiuwenReactAgentA2aE2eTest` 只有在 `SAA_SAMPLE_LLM_API_KEY` 非空时才运行。未设置时，JUnit `assumeTrue()` 会在 agent-card 断言后跳过真实 LLM 分支；suite 中其他部分仍会运行。
> AgentScope 真实 LLM E2E 测试 `AgentScopeA2aE2eTest` 沿用同样规则：`SAA_SAMPLE_LLM_API_KEY` 为空时跳过三条真实模型分支。

## 哪些环境变量会真正生效？

Maven 和 Spring Boot 只会看到启动时传入 Java 进程的环境变量。实际生效规则如下：

1. **helper 脚本加载的 env 文件值** — `scripts/run-server.sh` 和 `scripts/test-e2e.sh` 会加载传入的 env 文件；未传参数时默认加载本示例目录下的 `.env`。如果 env 文件定义了某个变量，它会覆盖运行脚本的 shell 中已 export 的同名变量。
2. **显式 shell 环境变量** — 当你直接运行 Maven，或 helper 脚本加载的 env 文件没有定义某个变量时，Maven 会看到启动 shell 中已 export 的变量，例如 `export SAA_SAMPLE_LLM_API_KEY=...`。
3. **Spring Boot 默认值** — 如果 Java 进程看不到环境变量，就使用 `src/main/resources/application.yaml` 中的默认值。

仓库内默认值是面向本地 OpenAI-compatible gateway 的占位配置：

```yaml
sample:
  openjiuwen:
    model-provider: ${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}
    api-key: ${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}
    api-base: ${SAA_SAMPLE_OPENJIUWEN_API_BASE:http://localhost:4000/v1}
    model-name: ${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}
    ssl-verify: ${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}
    checkpointer: ${SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER:in-memory}
    redis-url: ${SAA_SAMPLE_OPENJIUWEN_REDIS_URL:redis://localhost:6379}
  agentscope:
    api-key: ${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}
    api-base: ${SAA_SAMPLE_AGENTSCOPE_API_BASE:http://localhost:4000/v1}
    endpoint-path: ${SAA_SAMPLE_AGENTSCOPE_ENDPOINT_PATH:/chat/completions}
    model-name: ${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}
    runtime:
      base-url: ${SAA_SAMPLE_AGENTSCOPE_RUNTIME_BASE_URL:self}
      endpoint-path: ${SAA_SAMPLE_AGENTSCOPE_RUNTIME_ENDPOINT_PATH:/sample/agentscope/process}
      embedded: ${SAA_SAMPLE_AGENTSCOPE_RUNTIME_EMBEDDED:true}
    retail-wealth:
      runtime:
        base-url: ${SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_BASE_URL:self}
        endpoint-path: ${SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_ENDPOINT_PATH:/sample/agentscope/retail-wealth/process}
        embedded: ${SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_EMBEDDED:true}
```

`sk-local-placeholder` 是**不可用占位 key**，不是实际 key。Ollama 这类本地 gateway 通常忽略 `Authorization` header，所以任意字符串都可用；如果你使用云端 API 或会校验 key 的本地 gateway，请设置 `SAA_SAMPLE_LLM_API_KEY`，并通过 `scripts/run-server.sh .env` 启动，或在运行 Maven 前手工 export。

从仓库根目录手工加载 `.env` 的方式：

```bash
set -a
. ./examples/agent-runtime-a2a-llm-e2e/.env
set +a
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run
```

如果你已经启动了 server，修改 `.env` 不会影响正在运行的 Java 进程。需要停止旧 server 后重新启动。

## 本地 LLM 默认值和 curl 检查

示例默认指向本地 OpenAI-compatible gateway。启动 sample 前，可以先直接检查 gateway：

```bash
curl http://localhost:4000/v1/models \
  -H 'Authorization: Bearer sk-local-placeholder'
```

如果你的 gateway 会校验 key，请使用 `.env` 中同一个 key：

```bash
curl http://localhost:4000/v1/models \
  -H "Authorization: Bearer ${SAA_SAMPLE_LLM_API_KEY}"
```

如果 gateway 使用其他 key、host 或 model，请覆盖下方环境变量。

## 可覆盖的环境变量

本示例使用的 runtime 配置前缀是 `agent-runtime.access.a2a`：

```yaml
agent-runtime:
  access:
    a2a:
      default-tenant-id: sample-tenant
      default-agent-id: openjiuwen-react-agent
      # public-base-url: https://agents.example.com/runtime-one
```

`public-base-url` 对本地运行是可选的。为空时，agent-card 端点会根据当前 HTTP 请求推导 base URL。生产环境建议显式设置为 runtime 的外部可访问 base URL，这样标准 A2A client 能拿到不依赖本地 host/port 推断的绝对 endpoint URL。

LLM 相关变量：

- `SAA_SAMPLE_LLM_API_KEY`
- `SAA_SAMPLE_OPENJIUWEN_API_BASE`
- `SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER`
- `SAA_SAMPLE_LLM_MODEL`
- `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY`
- `SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER`
- `SAA_SAMPLE_OPENJIUWEN_REDIS_URL`
- `SAA_SAMPLE_AGENTSCOPE_API_BASE`
- `SAA_SAMPLE_AGENTSCOPE_ENDPOINT_PATH`
- `SAA_SAMPLE_AGENTSCOPE_RUNTIME_BASE_URL`
- `SAA_SAMPLE_AGENTSCOPE_RUNTIME_ENDPOINT_PATH`
- `SAA_SAMPLE_AGENTSCOPE_RUNTIME_EMBEDDED`
- `SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_BASE_URL`
- `SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_ENDPOINT_PATH`
- `SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_EMBEDDED`

控制台客户端支持位置参数或环境变量：

- 第 1 个参数或 `SAA_SAMPLE_A2A_BASE_URL`：A2A server base URL，默认 `http://localhost:8080`
- 第 2 个参数或 `SAA_SAMPLE_AGENT_ID`：agent id，默认 `openjiuwen-react-agent`
- 第 3 个参数或 `SAA_SAMPLE_USER_ID`：user id，默认 `manual-user`

示例覆盖：

```bash
export SAA_SAMPLE_LLM_API_KEY="<your-key>"
export SAA_SAMPLE_OPENJIUWEN_API_BASE="http://localhost:4000/v1"
export SAA_SAMPLE_AGENTSCOPE_API_BASE="http://localhost:4000/v1"
export SAA_SAMPLE_LLM_MODEL="gpt-5.4-mini"
export SAA_SAMPLE_A2A_BASE_URL="http://localhost:18080"
```

OpenJiuwen 示例在配置阶段会同时创建两个原生 checkpointer 候选。默认路径使用
`InMemoryCheckpointer`，便于本地 E2E；如果需要切到 Redis 路径，可以设置
`SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER=redis`，并通过
`SAA_SAMPLE_OPENJIUWEN_REDIS_URL` 指定 Redis URL。

## 安装 runtime 依赖

该 example 位于 root Maven reactor 外部，因此需要先把 runtime 依赖安装到本地 Maven 仓库：

```bash
./mvnw -pl agent-runtime -am -DskipTests install
```

这样 `examples/agent-runtime-a2a-llm-e2e` 就能解析当前的 `agent-runtime` snapshot。

`bash scripts/run-server.sh .env` 会在启动服务前自动执行安装步骤。

## 自动化测试

推荐通过 helper 脚本运行示例测试模块：

```bash
bash scripts/test-e2e.sh .env
```

测试会启动示例应用，并通过 A2A 客户端流程调用它。基础 openJiuwen 和 AgentScope 连通性测试期望 `ping` 的可见响应是 `pong`。零售财富助手测试发送客户经理场景提示，并期望可见响应包含客户画像、资产配置、收益测算、风险提示和合规提示。AgentScope SDK、Harness、REST/SSE runtime 测试使用同一套真实模型配置；REST/SSE 路径默认回环到示例内置 AgentScope runtime endpoint，也可以通过对应的 `*_RUNTIME_BASE_URL` 指向客户环境中的外部 runtime。

如果你已经手工 export 所需变量，也可以直接运行 Maven（模块 pom 默认
`skipTests=true`，必须显式覆盖）：

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test -DskipTests=false
```

## LangGraph 远程 runtime 样例（非 shipped）

`src/main/java/com/huawei/ascend/examples/langgraph/` 提供一个面向远程
LangGraph runtime（LangGraph Platform / langgraph-api）的 `AgentRuntimeHandler`
样例实现，演示如何把第三个框架适配到中立 runtime SPI 之后。它不属于 shipped
的 agent-runtime 适配器面（openJiuwen + AgentScope）；如需转正，需要补授权 ADR
以及 L1/contract-catalog 联动。其单元测试随本模块测试套件一起运行。

## 手工验证

1. 确认本地 OpenAI-compatible endpoint 可访问。
2. 使用 env-loading helper 脚本启动服务端：

```bash
bash scripts/run-server.sh .env
```

该脚本会加载 `.env`、安装 `agent-runtime`，然后启动 Spring Boot server。若旧 server 已在运行，请先停止旧进程；修改 `.env` 不会更新已运行 Java 进程的配置。

3. 在另一个终端启动控制台客户端：

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
```

4. 在提示符中输入：

```text
ping
```

5. 确认打印响应是 `pong`。

如果要指定其他 server，请把 base URL 作为第一个参数传入：

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication \
  -Dexec.args="http://localhost:18080"
```

server 启动后，也可以直接用 curl 验证 A2A surface。

检查 agent card：

```bash
curl http://localhost:8080/.well-known/agent-card.json
```

向 `/a2a` 发送 streaming JSON-RPC 请求：

```bash
curl http://localhost:8080/a2a \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "jsonrpc": "2.0",
    "id": "manual-1",
    "method": "message/stream",
    "params": {
      "message": {
        "role": "user",
        "messageId": "manual-message-1",
        "contextId": "manual-session-1",
        "metadata": {
          "userId": "manual-user",
          "agentId": "openjiuwen-react-agent",
          "sessionId": "manual-session-1"
        },
        "parts": [
          {
            "kind": "text",
            "text": "ping"
          }
        ]
      }
    }
  }'
```

SSE stream 应包含 accepted event，然后出现 completed response，用户可见文本为 `pong`。

## 期望的 ping/pong 路径

正常 happy path：

- 输入：`ping`
- 从 `/.well-known/agent-card.json` 发现 agent card
- 向 `/a2a` 发送 JSON-RPC streaming 请求
- 最终可见回答：`pong`

## 排错

- `Could not resolve com.huawei.ascend:agent-runtime:<version>`
  - 先运行 `./mvnw -pl agent-runtime -am -DskipTests install`。

- server 启动成功，但模型调用失败。
  - 检查 `SAA_SAMPLE_LLM_API_KEY`、`SAA_SAMPLE_OPENJIUWEN_API_BASE` 和 `SAA_SAMPLE_LLM_MODEL`。
  - 确认本地 gateway 能响应：`curl http://localhost:4000/v1/models -H 'Authorization: Bearer ...'`。
  - 如果 gateway 用真实 key 能成功，但 sample 表现像仍在用 placeholder key，请停止 server，并用 `bash scripts/run-server.sh .env` 重启。
  - 如果 `/v1/models` 成功但 sample 仍失败，请用同一个 key 和 model 直接测试 gateway 的 `/v1/chat/completions` 端点。

- 控制台客户端连不上。
  - 确认 server 运行在 `http://localhost:8080`，或通过 `SAA_SAMPLE_A2A_BASE_URL` / 第一个 CLI 参数传入正确 base URL。

- A2A 调用没有最终回答。
  - 检查 stream 是否到达 completed event。
  - 重新运行自动化测试，验证期望的 `ping -> pong` 路径。

- 本地 gateway TLS 或证书问题。
  - 检查 `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY`；本示例本地默认值为 `false`。
