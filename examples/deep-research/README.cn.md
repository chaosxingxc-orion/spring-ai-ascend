# Deep Research Demo — 开发说明

根 DeepAgent（`deep-research-agent`）的 A2A 包装与 YAML 装配骨架。子 agent（search/read/verify）由其他同学维护。

## 模块

| 模块 | 说明 |
|------|------|
| `agent-deep-research` | 库层：YAML + system prompt + constants（无 Spring） |
| `agent-deep-research-a2a` | A2A 包装：DeepAgent handler + mem0 + 端口 13003 |
| `agent-search` / `agent-search-a2a` | search 子 agent 库 + A2A 包装，端口 **13004** |
| `agent-verify` / `agent-verify-a2a` | verify 子 agent 库 + A2A 包装，端口 **13006** |

## 构建

在仓库根目录安装平台产物后：

```bash
./mvnw clean install -DskipTests
./mvnw -f examples/deep-research/pom.xml clean test
```

## 环境变量

| 变量 | 说明 |
|------|------|
| `DEEP_RESEARCH_LLM_PROVIDER` | YAML `model.provider` |
| `DEEP_RESEARCH_LLM_API_BASE` | YAML `model.baseUrl` |
| `DEEP_RESEARCH_LLM_API_KEY` | YAML `model.apiKey`（必填） |
| `DEEP_RESEARCH_LLM_MODEL` | YAML `model.name` |
| `DEEP_RESEARCH_MEM0_BASE_URL` | 默认 `http://7.209.189.82:8000` |
| `DEEP_RESEARCH_MEM0_API_KEY` | mem0 `X-API-Key`（可选） |
| `DEEP_RESEARCH_SEARCH_A2A_URL` | 子 agent search，默认 `http://localhost:13004` |
| `DEEP_RESEARCH_READ_A2A_URL` | 子 agent read，默认 `http://localhost:13005` |
| `DEEP_RESEARCH_VERIFY_A2A_URL` | 子 agent verify，默认 `http://localhost:13006` |
| `DEEP_RESEARCH_TENANT_ID` | mem0 租户隔离 |
| `DEEP_RESEARCH_E2E_ENABLED` | 设为 `true` 启用 `DeepResearchA2aE2eIT` |
| `DEEP_RESEARCH_MEM0_E2E_ENABLED` | 设为 `true` 启用 `DeepResearchMem0LiveIT`（直连 mem0） |
| `VERIFY_LLM_API_KEY` | verify YAML `model.apiKey`（**stub/prod 均必填**，本地可填假 key） |
| `VERIFY_LLM_API_BASE` | verify YAML `model.baseUrl` |
| `VERIFY_LLM_MODEL` | verify YAML `model.name` |
| `TAVILY_API_KEY` | verify **prod** profile 下 Tavily 搜索（stub 不需要） |

## Phase 2（mock 驱动）

库层提供 mock fixture 与参考编排：

- `src/main/resources/fixtures/` — search/read/verify 子 agent 响应样例（含 `spa_blocked`、`cloudflare_403`、`contradict`、`insufficient`）
- `mock.MockSubAgentFixtures` — 加载 fixture
- `mock.DeepResearchOrchestrationScenario` — 无 LLM 的参考编排，验证 §5 输出阈值
- `report.DeepResearchReportComposer` / `DeepResearchOutputContract` — 报告组装与 JSON 尾块解析

单测：

```bash
./mvnw -f examples/deep-research/pom.xml test
```

## Phase 3（mem0 + A2A 集成）

- `DeepResearchInMemoryMemoryProvider` — 测试 profile（`application-test.yaml`）进程内记忆
- `DeepResearchMem0MemoryProviderTest` — mock HTTP 验证 mem0 OSS 契约
- `DeepResearchMemoryRailTest` — `ExternalMemoryRail` 在 DeepAgent 流式前安装
- `DeepResearchA2aBootTest` — 嵌入式 Spring Boot + AgentCard 发现（日常 `mvn test`）
- `DeepResearchMem0LiveIT` — 可选 live mem0 往返（`DEEP_RESEARCH_MEM0_E2E_ENABLED=true`）
- `DeepResearchA2aE2eIT` — 全链路 E2E（`DEEP_RESEARCH_E2E_ENABLED=true` + stub + 真实 LLM key）

```bash
# 日常单测 + Boot 测试
./mvnw -f examples/deep-research/pom.xml test

# 可选 live mem0
export DEEP_RESEARCH_MEM0_E2E_ENABLED=true
./mvnw -f examples/deep-research/agent-deep-research-a2a/pom.xml verify

# 全链路 E2E（stub + LLM）
export DEEP_RESEARCH_E2E_ENABLED=true
export DEEP_RESEARCH_LLM_API_KEY=sk-your-key
bash examples/deep-research/start-stubs.sh
./mvnw -f examples/deep-research/agent-deep-research-a2a/pom.xml verify
```

## 启动与手测

```bash
export DEEP_RESEARCH_LLM_PROVIDER=OpenAI
export DEEP_RESEARCH_LLM_API_BASE=http://localhost:4000/v1
export DEEP_RESEARCH_LLM_API_KEY=sk-your-key
export DEEP_RESEARCH_LLM_MODEL=gpt-4o-mini

# 先起 B/C/D stub（jar 就绪后）
bash examples/deep-research/start-stubs.sh

./mvnw -f examples/deep-research/agent-deep-research-a2a/pom.xml spring-boot:run
```

Windows PowerShell 等价写法（**必须在启动 13003 的同一终端**设置，curl 终端无需重复）：

```powershell
$env:DEEP_RESEARCH_LLM_PROVIDER = "OpenAI"
$env:DEEP_RESEARCH_LLM_API_BASE = "https://your-gateway/v1"
$env:DEEP_RESEARCH_LLM_API_KEY = "sk-your-key"
$env:DEEP_RESEARCH_LLM_MODEL = "gpt-4o-mini"

# 可选：跳过 mem0 延迟
.\mvnw.cmd -f examples\deep-research\pom.xml -pl agent-deep-research-a2a spring-boot:run `
  "-Dspring-boot.run.arguments=--deep-research.memory.provider=in-memory"
```

## 启动 verify 子 agent（13006）

verify **没有默认 profile**，启动时必须显式指定 `stub` 或 `prod`（与 search-a2a 不同）。

构建（需先 `install` 到本地 m2）：

```powershell
.\mvnw.cmd clean install -DskipTests
.\mvnw.cmd -f examples\deep-research\pom.xml clean install -DskipTests
```

### stub 模式（本地联调，fixture  verdict，不调 Tavily）

```powershell
$env:VERIFY_LLM_API_KEY  = "sk-test"
$env:VERIFY_LLM_API_BASE  = "http://localhost:4000/v1"
$env:VERIFY_LLM_MODEL     = "gpt-4o-mini"

.\mvnw.cmd -f examples\deep-research\pom.xml -pl agent-verify-a2a spring-boot:run `
  "-Dspring-boot.run.arguments=--spring.profiles.active=stub"
```

### prod 模式（真实 LLM + Tavily）

```powershell
$env:VERIFY_LLM_API_KEY  = "sk-your-key"
$env:VERIFY_LLM_API_BASE  = "https://your-gateway/v1"
$env:VERIFY_LLM_MODEL     = "deepseek-v4-pro"
$env:TAVILY_API_KEY       = "tvly-your-key"

.\mvnw.cmd -f examples\deep-research\pom.xml -pl agent-verify-a2a spring-boot:run `
  "-Dspring-boot.run.arguments=--spring.profiles.active=prod"
```

就绪检查：

```powershell
curl.exe -s http://localhost:13006/.well-known/agent-card.json
curl.exe -s http://localhost:13006/actuator/health
```

AgentCard `name` 为 **`verify-agent`**；root（13003）通过 `DEEP_RESEARCH_VERIFY_A2A_URL`（默认 `http://localhost:13006`）自动注册为远端 tool。启动或重启 root 后，日志应出现 `name=verify-agent`。

单独手测 verify（不经过 root）：

```powershell
curl.exe -X POST "http://localhost:13006/a2a" `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  -d "{\"jsonrpc\":\"2.0\",\"id\":\"verify-r1\",\"method\":\"SendStreamingMessage\",\"params\":{\"message\":{\"role\":\"ROLE_USER\",\"messageId\":\"msg-v1\",\"contextId\":\"session-verify-1\",\"metadata\":{\"userId\":\"manual-user\",\"agentId\":\"verify-agent\"},\"parts\":[{\"text\":\"Verify: 火山方舟豆包 Pro 4K 输入价格为 0.0008 元/千 token。claim_type=numeric\"}]}}}" `
  --no-buffer
```

### 本地联调注意事项

| 项 | 说明 |
|----|------|
| **远端 A2A 段数上限** | runtime 默认 `max-legs=5`，多轮 search/read/verify 易触发 `REMOTE_INVOCATION_LIMIT_EXCEEDED`。`agent-deep-research-a2a` 的 `application.yaml` 已设为 **30**（可覆盖：`--agent-runtime.remote-invocation.max-legs=N`，上限 100） |
| **远端 tool 名** | runtime 按子 agent AgentCard `name` 注入：`search-agent`、`verify-agent` 已与 system prompt 对齐；`plan_read` 待 read-agent 就绪后对齐 |
| **重复 session** | 同一 `sessionId` 会恢复 checkpoint；手测失败重试时建议换新 `contextId` / fixture 中的 session |
| **仅 search 就绪时** | 13005/13006 未起时 card refresh 告警可忽略；完整五家对比仍需多段远端调用，依赖 `max-legs` 足够大 |

手测流式请求：

```bash
curl -X POST http://localhost:13003/a2a \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  --data-binary @examples/deep-research/agent-deep-research-a2a/src/main/resources/a2a-fixtures/research-round1-stream.json \
  --no-buffer
```

## 设计约束

- Wrapper 仅通过 `AgentFactory.toDeepAgent(yamlPath)` 装配，禁止手写 `DeepAgentConfig.builder()`
- 远端子 agent tool 名必须为 `search-agent` / `plan_read` / `verify-agent`（与 AgentCard `name` 一致）
- 长期记忆走 `openJiuwenRails` + `openJiuwenExternalMemoryRail`（非 `openJiuwenDeepAgentRails`）

详细拓扑见 [TOPOLOGY.cn.md](TOPOLOGY.cn.md)。
