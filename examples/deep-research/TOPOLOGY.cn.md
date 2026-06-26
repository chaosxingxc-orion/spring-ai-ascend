# Deep Research Demo —— 拓扑约定与开工分工

本文件是 deep-research 这个示例的**开工前约定**，4 个开发者按自己负责的章节读即可开干。集成期再回头对照"集成测试"章节验收。

> 暂不包含任何代码骨架，等 4 位负责人对契约无异议后再落 module。

## 0. 一句话目标

基于 `agent-runtime` 的 **DeepAgent handler** 做一个深度调研 agent，演示「root DeepAgent + 多个 ReAct 子 agent + 工具/技能调用」的协作模式。研究主题固定为：

> **国内主流大模型 API 对比（截至 2026 Q2）**，覆盖维度：定价 / 上下文窗口 / 限速 / Function Calling / 特色能力，输出对比矩阵 + 引用 + 置信度。

## 1. 拓扑

```
                       user query
                          │
                          ▼
           ┌────────────────────────────┐
           │  deep-research-agent       │ ← DeepAgent
           │  • planner-worker task loop│   maxIterations=10
           │  • 最后一步组装 final report │   enableTaskLoop=true
           │  • A2A 入口暴露给用户/上游   │   Checkpointer 可选
           └─────────────┬──────────────┘
                         │ OpenJiuwenRemoteToolInstaller
                         │ 自动注入为 3 个 tool：search-agent / plan_read / verify-agent
         ┌───────────────┼────────────────┐
         ▼               ▼                ▼
 ┌────────────────┐ ┌────────────┐ ┌──────────────┐
 │ search-agent   │ │ read-agent │ │ verify-agent │
 │ (ReAct)        │ │ (ReAct)    │ │ (ReAct)      │
 │ Tavily Search  │ │ jsoup +    │ │ LLM 评判 +    │
 │ 候选 url + rerank│ │ readability4j│ │ 跨源对照      │
 └────────────────┘ └────────────┘ └──────────────┘
   每个子 agent 独立 a2a runtime 进程，独立端口；通过 A2A SDK 互相调用
```

**为什么子 agent 走远端 A2A 而不是同进程多 DeepAgent**：
- 子 agent 各有独立 runtime，cancel / checkpointer / trajectory / 租户隔离独立
- 各自独立开发 / 部署 / 扩缩容，符合"一人负责一个 agent"的分工
- 主要 tradeoff：每跳多 50–200ms，相对模型推理（5–30s/跳）可忽略

**关键依赖说明**：DeepAgent handler 现已具备以下能力注入点：
- 远端 A2A 子 agent 注入（`setRuntimeToolInstaller`）—— root 调子 agent 的核心路径
- MCP installer（`setMcpToolInstaller`）
- SkillHub installer（`setSkillHubInstaller`）
- ReAct callback rails 注入（`openJiuwenRails(ctx)`，作用于 inner ReActAgent 回调）
- DeepAgent task-loop rails 注入（`openJiuwenDeepAgentRails(ctx)`，作用于 DeepAgent 任务循环生命周期）
- 两个 memory rail 工厂：`memoryRuntimeRail(ctx, mp)`（ReAct 兼容）+ `openJiuwenExternalMemoryRail(ctx, mp)`（harness native）

本 demo 本期重点利用 **长期记忆 rail**（见 §3.1，跨会话研究召回是 deep research 最自然的差异化场景）。MCP / SkillHub installer 和 自定义 audit rail 列为可选加分项（见 §8）。

## 2. 模块布局与端口

```
examples/deep-research/
├── pom.xml                                                # parent
├── TOPOLOGY.cn.md                                         # 本文件
├── start-stubs.sh                                         # 一键拉起 3 个 stub
├── agent-deep-research/         agent-deep-research-a2a/        13003   ← A
│   └── src/main/resources/agent/{deepagent.prod.yaml, deepagent.stub.yaml}
├── agent-search/                agent-search-a2a/                13004   ← B
│   └── src/main/resources/agent/{agent.prod.yaml, agent.stub.yaml}
├── agent-read/                  agent-read-a2a/                  13005   ← C
│   └── src/main/resources/agent/{agent.prod.yaml, agent.stub.yaml}
└── agent-verify/                agent-verify-a2a/                13006   ← D
    └── src/main/resources/agent/{agent.prod.yaml, agent.stub.yaml}
```

每个 agent **两层结构**（库 + a2a wrapper），跟 `examples/travel-agentscope/` 一致：

- **库**（`agent-xxx/`）：仅依赖 `agent-sdk` + 用到的工具库（如 jsoup / readability4j / Tavily HTTP client），**不依赖** `agent-runtime` / Spring。包含：
  - `agent/*.yaml`：装配清单（model / prompt / tools / skills / rails / framework options）
  - Java tool 静态方法（签名固定为 `public static Object method(Map<String,Object>)`）
  - Java rail hook 类（function rail 引用的方法）
  - 外部 client 接口（如 `WebSearchProvider`）+ prod/stub 两套实现
- **包装**（`agent-xxx-a2a/`）：Spring Boot 应用，runtime SAM 桥接 adapter 继承 `OpenJiuwenDeepAgentRuntimeHandler` / `OpenJiuwenAgentRuntimeHandler`，并 override `createOpenJiuwenDeepAgent(ctx)` / `createOpenJiuwenAgent(ctx)` 内部**直接调 `AgentFactory.toDeepAgent(yamlPath)` / `toReactAgent(yamlPath)`**；YAML 路径由 Spring profile 决定（`stub` profile 加载 `agent.stub.yaml`，`prod` profile 加载 `agent.prod.yaml`）

这条约束是**强制的**：库不依赖 runtime SAM，将来换其他 runtime 时 agent 主体不变。

### 2.1 装配方式约束（强制）

**所有 4 个 agent 必须走 agent-sdk YAML 装配（迭代二特性 2）**，不允许在 wrapper 里直接 `new ReActAgentBuilder()` / `new DeepAgentBuilder()` 手写 Java 装配。

YAML 覆盖的范围（详见 [`agent-sdk-openjiuwen-yaml.md`](../../agent-runtime/docs/guides/agent-sdk-openjiuwen-yaml.md)）：

| 配置项 | 入口 |
|---|---|
| framework type/agent + options | YAML `framework.*` |
| 模型 base URL / API key / 参数 | YAML `model.*`，凭证用 `${ENV}` 注入 |
| system prompt | YAML `prompt.system`（或 `prompt.agentMd`） |
| Java tool（@Tool 替代品） | YAML `tools[].ref.type=file` + `class`/`method` |
| HTTP tool | YAML `tools[].ref.type=http` |
| filesystem skills | YAML `skills.sources[]` |
| function rails（before/after Model/Tool） | YAML `rails[]` |
| DeepAgent MCP server | YAML `mcps[]`（只对 DeepAgent 生效） |

YAML **不能**覆盖（保留在 wrapper handler 里）：

| 配置项 | 入口 | 说明 |
|---|---|---|
| 远端 A2A 子 agent 注入 | `setRuntimeToolInstaller(OpenJiuwenRemoteToolInstaller)` | 由 runtime 注入；YAML 不知道 `remote-agents.*.endpoint` |
| 长期记忆 rail (`MemoryProvider`) | handler override `openJiuwenExternalMemoryRail(ctx, mp)` | `MemoryProvider` 是 Spring bean，YAML 无法引用 Spring context |
| 自定义 audit rail / 限流 rail | handler override `openJiuwenRails(ctx)` / `openJiuwenDeepAgentRails(ctx)` | 需要 Spring bean（如 metrics client）时只能走 handler |

Wrapper handler 骨架（4 个 agent 都按这个模板写）：

```java
@Component
public final class DeepResearchHandler extends OpenJiuwenDeepAgentRuntimeHandler {
    private final Path yamlPath;
    private final MemoryProvider memoryProvider;

    public DeepResearchHandler(
            @Value("${deep-research.yaml-path}") Path yamlPath,
            MemoryProvider memoryProvider) {
        super("deep-research-agent");
        this.yamlPath = yamlPath;
        this.memoryProvider = memoryProvider;
    }

    @Override
    protected DeepAgent createOpenJiuwenDeepAgent(AgentExecutionContext context) {
        return AgentFactory.toDeepAgent(yamlPath);   // ← 特性 2 入口
    }

    @Override
    protected List<AgentRail> openJiuwenDeepAgentRails(AgentExecutionContext ctx) {
        return memoryProvider == null
                ? List.of()
                : List.of(openJiuwenExternalMemoryRail(ctx, memoryProvider));
    }
}
```

`application.yaml` profile 切换 YAML 路径：

```yaml
deep-research:
  yaml-path: classpath:/agent/deepagent.prod.yaml
---
spring:
  config:
    activate:
      on-profile: stub
deep-research:
  yaml-path: classpath:/agent/deepagent.stub.yaml
```

## 3. 每个 Agent 的契约

### 3.1 deep-research-agent（A 同学）

模式：**DeepAgent**，`maxIterations=10`，`enableTaskLoop=true`。

A2A 入口契约：

```json
// input
{
  "topic": "国内主流大模型 API 对比 (2026 Q2)",
  "vendors": ["火山方舟", "阿里百炼", "智谱AI", "Kimi", "DeepSeek", "百度文心", "腾讯混元"],
  "dimensions": ["pricing", "context_length", "rate_limit", "function_calling", "specialty"],
  "as_of_date": "2026-06-22"
}

// output
{
  "report_markdown": "...",
  "comparison_table": { "vendor -> dimension -> value": "..." },
  "citations": [{ "url": "...", "title": "...", "fetched_at": "..." }],
  "confidence_per_field": { "火山方舟.pricing.input": 0.92, "...": "..." }
}
```

A 同学的开发重点：
- **`deepagent.prod.yaml` 装配（特性 2）**：`framework.type=openjiuwen` / `framework.agent=deepagent` / `framework.options.{maxIterations:10, enableTaskLoop:true, language:cn}`；`model.*` 配项目默认 LLM；`prompt.system` 写 DeepAgent 决策提示（决定调 `search-agent` / `plan_read` / `verify-agent` 中的哪个，以及报告组装的格式）；本 agent **不在 YAML 里声明 tools**（3 个子 agent tool 由 runtime `OpenJiuwenRemoteToolInstaller` 注入，不走 YAML）
- 报告组装 prompt：comparison_table 转 markdown 表格 + citations + 置信度叙述（写在 `prompt.system` 内）
- 错误路径处理：`spa_blocked` / `cloudflare_403` / `verdict=insufficient` 这几种 sub-agent 返回时，重新调度策略（同样写在 prompt 里）
- A2A wrapper 的 `application.yaml` 配 3 个 `agent-runtime.remote-agents.*.endpoint`
- **长期记忆接入（本 demo 的差异化亮点）**：A 同学 adapter override `openJiuwenDeepAgentRails(ctx)`，把 `openJiuwenExternalMemoryRail(ctx, memoryProvider)` 挂到 DeepAgent task-loop 上，`MemoryProvider` 实现走 **mem0**（参考 `examples/travel/agent-hotel-a2a/` 已有的 mem0 接入）。这一步**不能放进 YAML**（`MemoryProvider` 是 Spring bean），但 wrapper override 跟 YAML 装配是叠加关系，不冲突
  - mem0 命名空间按 `tenantId + agentId` 隔离，避免跨用户串台
  - 写入策略：仅在 DeepAgent task-loop 收尾阶段（final report 组装完成后）批量写一次，不在 sub-agent 调用过程中频繁写

### 3.2 search-agent（B 同学）

模式：**ReAct**。

A2A skill 契约：

```json
// skill: web_search
// input
{ "query": "string", "top_k": 10, "time_range": "year|month|week|all", "language": "zh|en|any" }
// output
{
  "results": [
    {
      "url": "...",
      "title": "...",
      "snippet": "...",
      "source_kind": "official|blog|news|forum",
      "score": 0.87
    }
  ]
}
```

B 同学的开发重点：
- **`agent.prod.yaml` 装配（特性 2）**：`framework.agent=react`；`model.*` 用 LLM 来对结果做轻量改写/摘要；`prompt.system` 写"按 web_search 契约返回结构化结果"指令；`tools[]` 声明一个 Java tool `web_search`，`ref.type=file` 指向 `WebSearchTool.search(Map<String,Object>)`；Tavily API key 通过 `model.*` 之外的自定义 tool resolver 读不到 env，所以建议在 wrapper @Bean 阶段把 `TAVILY_API_KEY` 注入到 `WebSearchTool` 的 static field（或单例 holder），tool 方法读 holder。**不要**把 key 写进 YAML
- 包 Tavily Search API（`https://api.tavily.com/search`），调用参数建议 `search_depth=basic`、`max_results=top_k`、`include_answer=false`（answer 综述由 root agent 自己做，不依赖 Tavily 的 LLM）
- Tavily 自带的 `results[].content` 可以填到我们的 `snippet` 字段；Tavily 自带的 `score` 直接透传，再叠加下面的 reranker 权重
- 自己加一层 reranker：**官网域名加权 ×2**（volcengine.com / bailian.aliyun.com / bigmodel.cn / moonshot.cn / deepseek.com / 百度智能云 / 腾讯混元 等）；CSDN/掘金/知乎降权 ×0.7。Tavily 支持 `include_domains/exclude_domains` 参数，可以一部分约束直接下推到搜索端
- `source_kind` 字段是给 verify-agent 用的（官方 > 评测 > 博客 > 论坛）；根据 domain 分类映射
- 抽象 `WebSearchProvider` interface，将来切别家（Serper / Exa / 自建 SearXNG）只改实现类
- 兜底：Tavily key 申请受阻时，降级到 Serper.dev 一次性赠额（2500 次，足够 4 人开发期），实现类换 `SerperSearchProvider`

### 3.3 read-agent（C 同学）

模式：**ReAct**。第一阶段**只抓公网 HTML**。

A2A skill 契约：

```json
// skill: read_url
// input
{ "url": "string", "focus_question": "string|null" }
// output
{
  "title": "...",
  "content_markdown": "...",
  "sections": [{ "heading": "...", "body": "..." }],
  "summary": "200 字内摘要，focus_question 非空时围绕它生成",
  "metadata": {
    "author": "...|null",
    "publish_date": "...|null",
    "doc_type": "pricing_page|blog|news|doc|spa_blocked|cloudflare_403|other"
  }
}
```

C 同学的开发重点：
- **`agent.prod.yaml` 装配（特性 2）**：`framework.agent=react`；`prompt.system` 写"按 read_url 契约返回 markdown + metadata"指令；`tools[]` 声明 Java tool `read_url`，`ref.type=file` 指向 `ReadUrlTool.read(Map<String,Object>)`。注意**不要**用 `ref.type=http` 直接做抓取——SPA 检测和 readability4j 抽取逻辑必须在 Java tool 内部完成，YAML HTTP tool 只能拿到原始响应
- JDK `java.net.http.HttpClient` 抓 HTML；不引第三方 HTTP 库
- `org.jsoup:jsoup` 做 DOM 清理
- `net.dankito.readability4j:readability4j` 提取主体内容（Mozilla Readability 的 Java 移植）
- 抽象 `DocumentReader` interface，PDF / 内部知识库后续阶段加新实现类
- **重点约束**：大模型厂商定价页面很多是 SPA（React/Vue 渲染），jsoup 抓不到。检测到正文极短 / 只剩空 div 时，返回 `doc_type=spa_blocked`，由 root 决定换源；不要默默吐空结果
- Cloudflare 403 / 429 / 5xx 等返回 `doc_type=cloudflare_403` 或 `other`，附错误描述

### 3.4 verify-agent（D 同学）

模式：**ReAct**。

A2A skill 契约：

```json
// skill: verify_claim
// input
{
  "claim": "火山方舟豆包 Pro 4K 模型输入价格为 0.0008 元/千 token",
  "sources": [{ "url": "...", "excerpt": "..." }],
  "claim_type": "numeric|categorical|temporal|qualitative"
}
// output
{
  "verdict": "support|contradict|insufficient|partial",
  "confidence": 0.85,
  "supporting_excerpts": [{ "url": "...", "quote": "...", "is_authoritative": true }],
  "contradicting_excerpts": [...],
  "suggested_followup_query": "...|null"
}
```

D 同学的开发重点：
- **`agent.prod.yaml` 装配（特性 2）**：`framework.agent=react`；`prompt.system` 写"找反证"指令 + 各 `claim_type` 分支决策规则；`tools[]` 声明两个 Java tool —— `verify_claim`（核心逻辑，调用 LLM 评判）+ `web_search`（独立 Tavily 入口，**不走 A2A 调 B 的 search-agent**，避免循环依赖）
- 数字型 claim（定价、上下文长度、QPS）要在 **≥2 个来源** 对照才能给出 `support`
- `is_authoritative` 标记官方源（厂商官网 / 官方文档）
- prompt 设计是这个 agent 质量差异化的关键：明确"找反证"动机，对 `qualitative` claim 输出 `partial` 时要给具体的"哪部分对、哪部分存疑"

## 4. Mock 约定

**核心约定：每个 sub-agent a2a wrapper 必须支持 `--spring.profiles.active=stub`**。stub 模式下 A2A 接口、契约 schema 跟 prod 完全一致，内部实现替换成读 `fixtures/` 下的数据。

stub 切换走的是 **YAML 替换**（不是 bean 替换）：wrapper 的 Spring profile 决定 `AgentFactory.toReactAgent(...)` / `toDeepAgent(...)` 加载哪份 YAML，`agent.stub.yaml` 跟 `agent.prod.yaml` 的 model / prompt / framework 完全一致，唯一不同的是 `tools[].ref.class` 指向 stub 实现类（如 `StubWebSearchTool` 而非 `WebSearchTool`）。这样 stub vs prod 走完全相同的装配链路，避免「stub bean 跟 prod bean 行为漂移」。

### 4.1 Stub profile 行为

| Agent | stub 行为 |
|---|---|
| search-agent | `tools[].ref.class` 切到 `StubWebSearchTool`；按 query 关键词路由到 `fixtures/search-results.json`；未命中返回空结果 + 一条警告日志；不读 Tavily API key |
| read-agent | `tools[].ref.class` 切到 `StubReadUrlTool`；按 url 的 host+path 路由到 `fixtures/*.html`；未命中返回 `doc_type=other`；不发起真 HTTP |
| verify-agent | `tools[].ref.class` 切到 `StubVerifyClaimTool`；按 claim hash 查 `fixtures/verdicts.json`；未命中返回 `verdict=insufficient, confidence=0.3, suggested_followup_query="<原 claim>"`，让 root 多搜一轮；不调真 LLM |
| deep-research-agent | **不需要 stub**。开发期 A 自己用 prod profile 连真 LLM，把另外 3 个用 stub 启动即可 |

### 4.2 Fixture 位置与命名

放在**库**的 `src/main/resources/fixtures/`（不是 test 资源，因为 stub profile 在 main classpath 加载）：

```
agent-search/src/main/resources/fixtures/search-results.json
agent-read/src/main/resources/fixtures/
    pricing-volcengine.html
    pricing-bailian.html
    blog-comparison.html
    spa-blocked.html              # 模拟 SPA 抓不到
    cloudflare-403.html           # 模拟 Cloudflare 拦截
agent-verify/src/main/resources/fixtures/verdicts.json
```

**强制要求**：fixture 必须覆盖至少 1 条 `contradict` 和 1 条 `spa_blocked`，确保 A 同学的 root 集成测试能验证错误路径处理。

### 4.3 单测层 mock（B/C/D 各自的事）

| 同学 | 单测 mock 内容 | 工具 |
|---|---|---|
| B | `TavilySearchClient` 用 Mockito mock，避免烧免费额度；HTTP response 用 JSON fixture | Mockito + JSON fixture |
| C | `HttpClient` 用 WireMock 起本地 HTTP server，返回各种 HTML fixture；断言 readability4j 抽取结果 | WireMock + HTML fixture |
| D | LLM 客户端 mock 掉，针对各 `claim_type` 用 stub 响应 | Mockito |

A 同学的 root 单测：直接 mock `OpenJiuwenRemoteToolInstaller` 注入的 tool，不用起真 a2a 服务。

### 4.4 一键拉起 stub

`examples/deep-research/start-stubs.sh`：

```bash
#!/bin/bash
cd "$(dirname "$0")"
java -jar agent-search/agent-search-a2a/target/*.jar  --spring.profiles.active=stub > /tmp/search.log  2>&1 &
java -jar agent-read/agent-read-a2a/target/*.jar      --spring.profiles.active=stub > /tmp/read.log    2>&1 &
java -jar agent-verify/agent-verify-a2a/target/*.jar  --spring.profiles.active=stub > /tmp/verify.log  2>&1 &
echo "stubs started: search=13004 read=13005 verify=13006"
```

### 4.5 profile 切换规约

每个 a2a wrapper 的 `application.yaml` 必须分 3 个 profile：

- `default`：什么都不配，启动失败并提示"必须指定 profile=stub|prod"（防止误启动到真后端）
- `stub`：bean 替换成 Stub 实现，fixture 路径硬编码
- `prod`：连真 Tavily / 真 HTTP / 真 LLM，外部依赖配置从 env 读

## 5. 集成测试主题

A 同学维护 e2e 测试用例，固定输入：

> 「截至 2026 Q2，对比火山方舟、阿里百炼、智谱、Kimi、DeepSeek 五家的旗舰模型 API：输入/输出定价、最大上下文窗口、QPS 默认限制、是否支持 Function Calling，给出推荐选型矩阵。」

**通过标准**：
- 报告里 5 家厂商 × 4 个维度的表格至少 16/20 格非空
- 每个数字都有 citation
- verify-agent 至少识别出 1 个 `contradict` 或 `insufficient`（说明它在干活，不是空过）
- 整体耗时不超过 5 分钟（端到端）
- **跨会话记忆召回**：同一 `tenantId + agentId` 下，第一轮研究完成后清掉 session（模拟"次日再问"），第二轮提问"上次对比里上下文窗口最大的是哪家"时，root 直接从 mem0 召回结论给出答案，耗时 < 30s（不应再次触发完整 search/read/verify 链路）

## 6. 分工与里程碑

| 负责人 | 模块 | 开发重点 | 验收输入 |
|---|---|---|---|
| A | agent-deep-research + a2a wrapper + e2e | deepagent YAML、system prompt、报告组装、memory rail、错误路径、端到端集成 | stub 模式下 e2e 测试通过；wrapper 装配走 `AgentFactory.toDeepAgent` |
| B | agent-search + a2a wrapper | agent YAML、Tavily 包装、reranker、source_kind 分类 | 单测覆盖 + stub fixture 覆盖；wrapper 装配走 `AgentFactory.toReactAgent` |
| C | agent-read + a2a wrapper | agent YAML、jsoup + readability4j、SPA 检测、错误分类 | 单测（含 spa_blocked / cloudflare_403 用例）+ stub fixture；wrapper 装配走 `AgentFactory.toReactAgent` |
| D | agent-verify + a2a wrapper | agent YAML、跨源对照 prompt、claim_type 分支、`partial` 判定 | 单测 + stub fixture（含 1 条 contradict）；wrapper 装配走 `AgentFactory.toReactAgent` |

通用验收门槛（4 个 agent 都要满足）：

- wrapper 中**不出现** `new ReActAgentBuilder()` / `new DeepAgentBuilder()` / 任何手写 Java 装配代码（grep 关键字 review）
- `agent.prod.yaml` + `agent.stub.yaml` 两份配置都能加载通过（启动时 `AgentFactory.toXxx` 不抛异常）
- YAML 中没有硬编码的 API key（grep `sk-` / `tvly-` 关键字）

里程碑（建议）：

1. **W1**：4 人各自把库 + a2a wrapper 骨架搭起来，契约 schema 落地，YAML 装配跑通（启动期 `AgentFactory.toXxx` 成功），stub profile 跑通；A 同学的 root 用全 stub 集成测试可见端到端 markdown 报告（内容是 fixture 拼出来的）
2. **W2**：B/C/D 各自接通真后端（Tavily / HTTP / LLM）；A 同学的报告组装 prompt 调优 + 接通 mem0 memory rail
3. **W3**：集成联调，e2e 验收 + bug 修复

## 7. 还没决定的事项

- **Tavily API key 申请人**：xx 同学负责申请（注意核对当前免费额度，SaaS 报价变化频繁），结果同步到团队
- **CI / 部署**：本 demo 第一阶段只在本地 + 测试服跑（参考 hotel-agentscope 部署到 7.209.189.82 的方式），不进 CI；后续如果要进 CI，需要约定 `stub` profile 的 CI 测试矩阵
- **是否要 trajectory 可观测面板**：DeepAgent 自带 TrajectoryRail，runtime 会吐 RUN_START/RUN_END/MODEL_CALL/TOOL_CALL 事件；第一阶段先看日志，不接外部 dashboard

## 8. 可选加分项（W3 验收完之后再做）

DeepAgent handler 在 release/v0.2.0-rc1 上已经把 ReAct handler 已有的能力注入点补齐（详见 §1 关键依赖说明），W1/W2 没用到的几个 hook 列在这里作为后续拓展方向，不进入第一阶段验收：

- **MCP installer（`setMcpToolInstaller`）**：把外部 MCP server 提供的工具（如官方文档站、内部知识库 MCP）作为 root DeepAgent 的额外工具源，与 A2A 子 agent 互补。开发动作：A 同学 adapter `@Bean` 注入一个 `OpenJiuwenMcpToolInstaller`
- **SkillHub installer（`setSkillHubInstaller`）**：如果项目接入了 SkillHub，把"价格抓取"、"模型规格对比"等通用技能集中托管，跨 demo 复用；开发动作同上
- **自定义 DeepAgent task-loop 审计 rail（`openJiuwenDeepAgentRails(ctx)`）**：在 task-loop 生命周期上挂一个业务 rail，输出"本轮规划了几个子任务 / 哪些 sub-agent verdict 不一致 / 单轮 token 用量"等指标，方便后续做 deep research 质量评测；这一层 rail 是 DeepAgent harness native，比 ReAct 回调 rail 看到的事件更上层
- **ReAct 回调 rail（`openJiuwenRails(ctx)`）**：DeepAgent 内部的 ReAct planner 也会走 ReAct callback，需要在 planner 步骤埋点（例如限制连续 model_call 次数、tool 黑名单）时挂这一层

落地优先级：MCP > 自定义 task-loop rail > SkillHub > ReAct rail；除非现场观察到具体问题再补。

---

如有不同意见，请直接在群里反馈或在本文件提 PR 修改。契约 schema（第 3 节）的任何调整都要 4 人达成一致后再改，避免 fixture 漂移。
