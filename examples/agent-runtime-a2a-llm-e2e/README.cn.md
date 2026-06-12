# Agent Runtime A2A LLM E2E 示例

## 目的

本示例演示如何启动一个 `agent-runtime` 应用：它暴露 A2A 端点，在端点后托管 AgentScope SDK agent，并只从 A2A 客户端视角验证整条链路。

示例位于 `examples/agent-runtime-a2a-llm-e2e`，包含：

- Spring Boot 服务端：`com.huawei.ascend.examples.a2a.A2aAgentRuntimeApplication`
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

## 选择 Agent Profile

每个 runtime 进程只托管**一个** agent。设置 `sample.a2a.agent` 切换：

- `agentscope`（默认）：AgentScope Java SDK ReAct agent。
- `retail-wealth-advisor`：AgentScope 零售财富助手样例，带 mock 行内 skills。

当前 E2E 测试覆盖两个 profile：

- `agentscope-react-agent`：AgentScope Java SDK ReAct agent。
- `agentscope-retail-wealth-advisor`：基于 AgentScope ReAct agent 和示例 skills 的银行零售财富助手。

## AgentScope 零售财富助手样例

零售财富助手样例模拟客户侧已经用 AgentScope 构建好的 agent：大型银行的业务研发中心用行内系统和金融能力组合 skills，给客户经理生成资产配置建议。该样例故意放在 example 模块内：`spring-ai-ascend` 负责运行时治理、A2A、任务状态和输出分发；财富助手业务逻辑属于客户侧 AgentScope 应用。

样例注册了本地 mock skills，用来代表客户环境里的能力：

- 客户画像与适当性查询
- 当前持仓查询
- 行情观点分析
- 银行产品池匹配
- 资产配置收益测算和压力场景测算

样例产品池保持银行财富管理语境：短期限理财产品、公募基金、合格投资者私募基金、黄金产品、ETF 联接基金。样例不推荐个股或场内 ETF，并要求模型输出适当性和合规提示。这些 skills 只用于演示，不构成金融建议。

## 快速开始：配置模板 + 脚本

进入 `examples/agent-runtime-a2a-llm-e2e` 目录后，复制一个模板，填好配置，然后通过 helper 脚本运行：

```bash
cp .env.ollama.example .env        # 或复制 .env.openai-compatible.example 后再编辑
bash scripts/test-e2e.sh .env      # 安装 agent-runtime 并运行 E2E suite
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

> 真实 LLM E2E 测试只有在 `SAA_SAMPLE_LLM_API_KEY` 非空时才运行。未设置时，JUnit `assumeTrue()` 会在 agent-card 断言后跳过真实 LLM 分支；suite 中其他部分仍会运行。

## 哪些环境变量会真正生效？

Maven 和 Spring Boot 只会看到启动时传入 Java 进程的环境变量。实际生效规则如下：

1. **helper 脚本加载的 env 文件值** — `scripts/run-server.sh` 和 `scripts/test-e2e.sh` 会加载传入的 env 文件；未传参数时默认加载本示例目录下的 `.env`。如果 env 文件定义了某个变量，它会覆盖运行脚本的 shell 中已 export 的同名变量。
2. **显式 shell 环境变量** — 当你直接运行 Maven，或 helper 脚本加载的 env 文件没有定义某个变量时，Maven 会看到启动 shell 中已 export 的变量，例如 `export SAA_SAMPLE_LLM_API_KEY=...`。
3. **Spring Boot 默认值** — 如果 Java 进程看不到环境变量，就使用 `src/main/resources/application.yaml` 中的默认值。

仓库内默认值是面向本地 OpenAI-compatible gateway 的占位配置：

```yaml
sample:
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
      default-agent-id: agentscope-react-agent
      # public-base-url: https://agents.example.com/runtime-one
```

`public-base-url` 对本地运行是可选的。为空时，agent-card 端点会根据当前 HTTP 请求推导 base URL。生产环境建议显式设置为 runtime 的外部可访问 base URL，这样标准 A2A client 能拿到不依赖本地 host/port 推断的绝对 endpoint URL。

LLM 相关变量：

- `SAA_SAMPLE_LLM_API_KEY`
- `SAA_SAMPLE_LLM_MODEL`
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
- 第 2 个参数或 `SAA_SAMPLE_AGENT_ID`：agent id，默认 `agentscope-react-agent`
- 第 3 个参数或 `SAA_SAMPLE_USER_ID`：user id，默认 `manual-user`

示例覆盖：

```bash
export SAA_SAMPLE_LLM_API_KEY="<your-key>"
export SAA_SAMPLE_AGENTSCOPE_API_BASE="http://localhost:4000/v1"
export SAA_SAMPLE_LLM_MODEL="gpt-5.4-mini"
export SAA_SAMPLE_A2A_BASE_URL="http://localhost:18080"
```

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

测试会启动示例应用，并通过 A2A 客户端流程调用它。AgentScope 基础连通性测试期望 `ping` 的可见响应是 `pong`。零售财富助手测试发送客户经理场景提示，并期望可见响应包含客户画像、资产配置、收益测算、风险提示和合规提示。

如果你已经手工 export 所需变量，也可以直接运行 Maven（模块 pom 默认
`skipTests=true`，必须显式覆盖）：

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test -DskipTests=false
```

## LangGraph 远程 runtime 样例（非 shipped）

`src/main/java/com/huawei/ascend/examples/langgraph/` 提供一个面向远程
LangGraph runtime（LangGraph Platform / langgraph-api）的 `AgentRuntimeHandler`
样例实现，演示如何把第三个框架适配到中立 runtime SPI 之后。它不属于 shipped
的 agent-runtime 适配器面；如需转正，需要补授权 ADR 以及 L1/contract-catalog
联动。其单元测试随本模块测试套件一起运行。

## 手工验证

### 前置：安装 agent-runtime

本示例模块在 root Maven reactor 外部。先安装一次 runtime：

```bash
./mvnw -pl agent-runtime -am -DskipTests install
```

（helper 脚本会自动执行此步骤。）

---

### 方式 A — 脚本启动（推荐）

helper 脚本自动加载 `.env` 并安装依赖。**适合想要一条命令直接跑起来的场景。**

#### 启动 server

```bash
# 默认 agent（Agentscope ReAct，ping → pong）
bash scripts/run-server.sh .env

# 零售财富助手（资产配置建议）
bash scripts/run-server.sh .env retail-wealth-advisor
```

| 参数 | 必填 | 默认值 | 说明 |
|---|---|---|---|
| env-file | 是 | `.env` | 环境变量文件路径 |
| agent-profile | 否 | `agentscope` | `agentscope` 或 `retail-wealth-advisor` |

#### 切换 agent

停掉当前 server（Ctrl+C），用不同 profile 重启：

```bash
# 从 agentscope 切到 retail-wealth-advisor
bash scripts/run-server.sh .env retail-wealth-advisor

# 从 retail-wealth-advisor 切回 agentscope
bash scripts/run-server.sh .env agentscope
```

脚本每次都重新 source `.env`——API key 始终会继承。

---

### 方式 B — 原始命令行启动

适合需要完全控制 Maven 参数、JVM 选项或端口的场景。

**重要：** Maven 不会自动读取 `.env` 文件。必须先 export 环境变量再运行 `mvn`，否则应用会回退到 `application.yaml` 的默认值（使用占位 key，LLM 调用会失败）。

#### 1. 导出环境变量

```bash
set -a
source .env
set +a
```

或手动 export：

```bash
export SAA_SAMPLE_LLM_API_KEY="sk-your-key"
export SAA_SAMPLE_AGENTSCOPE_API_BASE="http://localhost:4000/v1"
export SAA_SAMPLE_LLM_MODEL="gpt-5.4-mini"
```

#### 2. 启动 server

```bash
# 默认 agent（agentscope-react-agent）
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run

# 零售财富助手
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--sample.a2a.agent=retail-wealth-advisor"

# 自定义端口
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=18080 --sample.a2a.agent=retail-wealth-advisor"
```

#### 切换 agent

停掉（Ctrl+C），用目标 profile 的 `mvn` 命令重启。如果开了新终端，记得重新 export 环境变量。

---

### 用 curl 验证（方式 A 和方式 B 通用）

Server 跑在 `http://localhost:8080` 后：

**Agent card：**

```bash
curl -s http://localhost:8080/.well-known/agent-card.json | python3 -m json.tool | grep name
# agentscope-react-agent             （默认）
# agentscope-retail-wealth-advisor   （retail-wealth-advisor profile）
```

**Ping → pong（Agentscope ReAct agent）：**

```bash
curl -s -N -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "id": "1",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "contextId": "session-1",
        "metadata": {
          "userId": "test-user",
          "agentId": "agentscope-react-agent",
          "sessionId": "session-1"
        },
        "parts": [{"text": "ping"}]
      }
    }
  }' --no-buffer
```

预期：最后一条 SSE event 中包含 `"text":"pong"`。

**资产配置建议（零售财富助手）：**

```bash
curl -s -N -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "id": "1",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "contextId": "session-2",
        "metadata": {
          "userId": "test-user",
          "agentId": "agentscope-retail-wealth-advisor",
          "sessionId": "session-2"
        },
        "parts": [{"text": "请为客户 BANK-CUST-001 生成一份稳健型资产配置建议。"}]
      }
    }
  }' --no-buffer
```

预期：completed response 包含 客户画像摘要、建议资产配置、收益测算、风险提示、合规提示 等章节。

---

### 交互式控制台客户端

在另一个终端（server 保持运行）：

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
```

输入 `ping` → 预期 `pong`。输入 `exit` 退出。

指定其他 server 或 agent：

```bash
# 通过 CLI 参数：<base-url> <agent-id> <user-id>
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication \
  -Dexec.args="http://localhost:18080 agentscope-retail-wealth-advisor manual-user"

# 通过环境变量
export SAA_SAMPLE_A2A_BASE_URL="http://localhost:18080"
export SAA_SAMPLE_AGENT_ID="agentscope-retail-wealth-advisor"
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
```

### Agent profile 速查

| Profile | Agent ID | 行为 |
|---|---|---|
| `agentscope`（默认） | `agentscope-react-agent` | ReAct agent，对 `ping` 回复 `pong` |
| `retail-wealth-advisor` | `agentscope-retail-wealth-advisor` | 银行零售财富助手，带 mock 行内 skills |

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
  - 检查 `SAA_SAMPLE_LLM_API_KEY`、`SAA_SAMPLE_AGENTSCOPE_API_BASE` 和 `SAA_SAMPLE_LLM_MODEL`。
  - 确认本地 gateway 能响应：`curl http://localhost:4000/v1/models -H 'Authorization: Bearer ...'`。
  - 如果 gateway 用真实 key 能成功，但 sample 表现像仍在用 placeholder key，请停止 server，并用 `bash scripts/run-server.sh .env` 重启。
  - 如果 `/v1/models` 成功但 sample 仍失败，请用同一个 key 和 model 直接测试 gateway 的 `/v1/chat/completions` 端点。

- 控制台客户端连不上。
  - 确认 server 运行在 `http://localhost:8080`，或通过 `SAA_SAMPLE_A2A_BASE_URL` / 第一个 CLI 参数传入正确 base URL。

- A2A 调用没有最终回答。
  - 检查 stream 是否到达 completed event。
  - 重新运行自动化测试，验证期望的 `ping -> pong` 路径。

- 端口冲突（8080 已被占用）。
  - 覆盖端口：`./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run -Dspring-boot.run.arguments="--server.port=18080"`
