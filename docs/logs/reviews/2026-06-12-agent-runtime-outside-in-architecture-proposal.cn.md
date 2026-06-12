---
affects_level: L1
affects_view: development
proposal_status: review
authors: ["Codex"]
related_adrs: []
related_rules: [D-1, G-15]
affects_artefact:
  - agent-runtime
  - agent-runtime/README.md
  - docs/quickstart.md
  - docs/contracts/contract-catalog.md
  - architecture/docs/L1/agent-runtime
---

# Proposal: AgentRuntime outside-in 架构收敛与运行完整性加固

> **Date:** 2026-06-12
> **Status:** Pending Review
> **Affects:** `agent-runtime` L1 module contract；primary view = `development`；secondary views = `logical`, `process`, `physical`, `scenarios`
> **Fact baseline:** 本 proposal 先读 `architecture/facts/generated/` 后读 prose。关键事实 ID：`build-module/agent-runtime`、`code-symbol/com-huawei-ascend-runtime-engine-spi-agentruntimehandler`、`code-symbol/com-huawei-ascend-runtime-engine-a2a-a2aagentexecutor`、`code-symbol/com-huawei-ascend-runtime-boot-runtimeautoconfiguration`、`test/com-huawei-ascend-runtime-architecture-runtimepackageboundarytest`。

---

## 0. 摘要

当前 `agent-runtime` 的真实代码边界已经从早期“完整 runtime 平台”收敛为：

```text
A2A SDK ingress/egress bridge
  -> AgentRuntimeHandler SPI
  -> framework adapters: OpenJiuwen / AgentScope / LangGraph
  -> trajectory / remote invocation / health / lifecycle support
```

这个收敛本身是正确方向：它减少本地跨库依赖，也尽量复用了 A2A SDK、Spring lifecycle、OpenTelemetry 等主流组件。但 outside-in 看，文档、配置、SPI 与运行面仍在表达一个更宽的 runtime 承诺，导致四类使用者的期待不一致：

- **开发者**以为自己面对的是协议中立 runtime SPI，但实际需要理解 A2A `Message`。
- **部署者**以为可以通过配置声明公开 URL / 默认 Agent ID，但实现只支持默认 tenant，并从请求现场推导 Agent Card URL。
- **运维者**能拿到 trajectory，但普通日志、远端通信健康、远端任务取消和 catalog refresh 还不够可运维。
- **最终用户**能通过 A2A 流式调用得到结果，但 trajectory 默认不可见，远端 input-required / timeout / cancel 的体验仍依赖较多内部约定。

本 proposal 建议把 `agent-runtime` 明确收敛为“可嵌入、A2A 优先、协议边界清晰的 Agent runtime library”，并按三条原则修正：

1. **高内聚、低耦合**：A2A 只在 access/adapter 层出现；核心执行上下文使用 runtime-native message model。
2. **运行特性完整**：生命周期、trajectory、communication、logs、health、remote invocation、cancel、timeout 形成可追踪闭环。
3. **优先复用主流开源能力**：A2A、OTel、Spring Boot lifecycle/actuator、SSE/HTTP client、framework SDK 能复用则复用；只有上游缺位时才自研薄适配层。

---

## 1. Root cause / strongest interpretation (Rule D-1)

1. **Observed failure / motivation**: `AgentRuntime` 的代码实现边界与外部使用者读到的 runtime 承诺存在漂移，尤其在配置契约、协议中立性、运行状态完整性和运维可观测性上。
2. **Execution path**: A2A request 进入 `A2aJsonRpcController`，经 A2A SDK `DefaultRequestHandler` 到 `A2aAgentExecutor.execute()`，再构造 `AgentExecutionContext` 调用 `AgentRuntimeHandler.execute()`，最后由 result router / trajectory / remote orchestrator 发送 A2A 事件。
3. **Root cause**: 代码已经按 A2A SDK bridge + framework adapter 收敛，但 README/L1 prose/SPI 仍保留更宽的 run/session/task-control runtime 叙事；例如 README 声明配置和 runtime 范围，而 `RuntimeAccessProperties` 只实现 `defaultTenantId`，`AgentExecutionContext` 又把 A2A `Message` 带进核心执行上下文。
4. **Evidence**: `agent-runtime/README.md:1`、`agent-runtime/README.md:67`、`agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/RuntimeAccessProperties.java:9`、`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/AgentExecutionContext.java:8`、`agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/RuntimeAutoConfiguration.java:63`。

Strongest interpretation: 这不是要求把 `agent-runtime` 做成另一个完整 agent platform；更强也更可行的解释是，把它从“宽叙事、窄实现”修正为“窄边界、强运行闭环、强复用”的 runtime library。

---

## 2. Scope statement

### 2.1 范围内

- `agent-runtime` 的 L1 module contract 与 developer-facing SPI 边界。
- A2A access adapter、Agent Card、runtime config、remote A2A invocation、trajectory、logs、health/lifecycle。
- README、quickstart、contract catalog、L1 docs 的 runtime 承诺收敛。
- 对现有 hand-written SSE / JSON / remote client 逻辑做复用优先的重评估。

### 2.2 范围外

- 不把 `agent-runtime` 扩张成 `agent-service` 的替代品。
- 不新增跨本地模块依赖；`build-module/agent-runtime` 继续禁止依赖 `agent-service`。
- 不直接改写 `architecture/facts/generated/`；若事实需要改变，只改源码、契约 YAML 或 extractor。
- 不在第一波引入完整多租户授权平台；但需要为 route grant / tenant policy 留出清晰端口。

---

## 3. Outside-in 问题陈述

### 3.1 开发者视角：SPI 不够协议中立

现状：

- `AgentRuntimeHandler` 的生命周期语义清楚：`agentId()`、`isHealthy()`、`execute()`、`resultAdapter()`、`start()`、`stop()`、`cancel()`。
- 但 `AgentExecutionContext` 直接携带 `org.a2aproject.sdk.spec.Message`，导致 handler 作者必须理解 A2A wire type。
- `RuntimePackageBoundaryTest` 允许 `engine.spi` 依赖 `org.a2aproject.sdk.spec..`，这让测试固化了协议泄漏。

影响：

- 未来如果接入 HTTP/MQ/CLI/local function call，核心 context 要么继续污染，要么做破坏性迁移。
- OpenJiuwen / AgentScope / LangGraph 适配器会被迫共同理解 A2A 消息结构，而不是只理解 runtime-native input。

目标：

```text
A2A Message
  -> A2aRuntimeMessageMapper
  -> RuntimeMessage / RuntimeConversation / RuntimeInvocation
  -> AgentRuntimeHandler
```

A2A type 只允许出现在 `engine.a2a` / `boot` access adapter，不能进入中立 SPI。

### 3.2 部署者视角：配置契约与实际 bean 不一致

现状：

- README 声明 `agent-runtime.access.a2a.default-agent-id` 与 `public-base-url`。
- `RuntimeAccessProperties` 实际只支持 `defaultTenantId`。
- `AgentCardController` 从请求 scheme/host/port 推导 endpoint，只有在宿主显式安装 forwarded-header 支持时才可能正确处理代理场景。

影响：

- Ingress / Gateway / reverse proxy / internal-external hostname 不一致时，Agent Card 可能暴露错误 URL。
- 部署者无法用配置稳定声明 public endpoint，也无法显式指定默认 agent ID。

目标：

- 实现或删除文档中的配置项；不能保留“声明了但无法生效”的部署契约。
- 如果实现，`public-base-url` 应成为 Agent Card endpoint 的第一优先级，request-derived URL 只能是 fallback。

### 3.3 运维者视角：trajectory 强，但 logs / remote health / cancel 闭环不足

现状：

- `TrajectoryEvent` 已有 schema version、tenant/context/task、span、usage、error、reasoning 等字段。
- `StampingTrajectoryEmitter` 有脱敏、span 栈和 mandatory event 约束。
- OTel sink 与 northbound trajectory 都存在。
- 普通日志 MDC 目前主要写入 `contextId`、`taskId`，缺少 tenant、agent、trace/span/run 等维度。
- remote A2A catalog 配置只有 URL，refresh 只处理 pending entry；outbound transport 按 remote agent ID 缓存；超时返回失败但不主动 cancel remote task。

影响：

- 运维者能从 trajectory 查问题，但日志、远端健康状态和任务生命周期不能天然串起来。
- 远端 Agent 改地址、换 card、超时、半失败时，系统缺少清晰的刷新、熔断、取消和告警语义。

目标：

- 日志 MDC 与 trajectory 的 trace/task/tenant/agent 对齐。
- 远端通信有 timeout、cancel-on-timeout、refresh/invalidation、auth header、TLS、health status、retry/backoff 的最小生产面。

### 3.4 最终用户视角：事件协议基本可用，但解释性与恢复体验需补齐

现状：

- A2A streaming endpoint 能输出 result、input-required、error。
- trajectory 默认不 northbound，只有请求 metadata opt-in 或 OTel sink 才可见。
- remote input-required 通过 parent task metadata 进行 continuation。

影响：

- 对普通用户，远端 Agent 参与、等待、超时、取消、需要补充输入等状态可能表现为“系统停住了”或“只看到最终失败”。
- 如果隐藏 trajectory 是隐私默认值，这是合理的；但需要有用户可理解的 progress/event 投影。

目标：

- caller-visible event 应至少覆盖：started、remote-called、remote-progress、input-required、cancelled、timeout、failed、completed。
- trajectory 可继续作为 developer/ops 细粒度通道，progress event 作为最终用户可理解通道。

---

## 4. Proposed change

### 4.1 收敛模块定位

把 `agent-runtime` 的公开定位改为：

```text
Embeddable A2A-first Agent Runtime Library
```

它提供：

- A2A ingress/egress。
- `AgentRuntimeHandler` 执行 SPI。
- framework adapters。
- runtime lifecycle / health。
- trajectory / OTel / northbound event projection。
- remote A2A invocation bridge。

它不直接承诺：

- 完整 run repository。
- 跨进程 durable session platform。
- agent-service 级别的 orchestration / governance / tenant authorization。

### 4.2 中立 runtime model

新增或收敛以下中立模型：

```text
RuntimeMessage
RuntimePart
RuntimeConversation
RuntimeInvocation
RuntimeUserInput
RuntimeAgentOutput
```

改造原则：

- `AgentExecutionContext` 不再暴露 A2A `Message`。
- `A2aAgentExecutor` 在边界上完成 A2A -> runtime model mapping。
- `StreamAdapter` 继续负责 framework result -> `AgentExecutionResult`，但不需要理解 A2A wire shape。
- ArchUnit 从“允许 engine.spi 依赖 A2A spec”改为“禁止 engine.spi 依赖 A2A spec”，仅 `engine.a2a` 例外。

### 4.3 配置契约 truth-up

二选一：

方案 A：实现配置。

```yaml
agent-runtime:
  access:
    a2a:
      default-tenant-id: public
      default-agent-id: default-agent
      public-base-url: https://agents.example.com/runtime
```

规则：

- `public-base-url` 存在时，Agent Card endpoint 使用该值。
- `default-agent-id` 存在时，默认 card 与 handler mismatch 要 fail fast 或明确 warning。
- request-derived URL 只作为 fallback。

方案 B：删除文档项。

- README、quickstart、contract catalog 不再提未实现配置。
- 只保留 `default-tenant-id`。

建议选方案 A，因为部署者对 public endpoint 的需求是生产环境刚需。

### 4.4 Remote A2A production surface

扩展 remote agent 配置：

```yaml
agent-runtime:
  remote-agents:
    - id: weather
      url: https://weather-agent.example.com
      card-path: /.well-known/agent-card.json
      enabled: true
      auth:
        type: bearer
        token-ref: WEATHER_AGENT_TOKEN
      timeout:
        connect: 2s
        request: 30s
        stream-idle: 15s
      retry:
        max-attempts: 2
        backoff: 250ms
      refresh:
        interval: 30s
        mode: always
      invocation:
        cancel-on-timeout: true
```

最低实现要求：

- endpoint/card refresh 后要 invalidate outbound transport cache。
- timeout 时 best-effort cancel remote task。
- remote health exposed through actuator details。
- tenant/agent allowlist 先以端口形式预留，不在第一波实现复杂策略。

### 4.5 Logs 与 trajectory 对齐

在 `A2aAgentExecutor` 执行窗口中，MDC 至少包含：

```text
tenantId
agentId
contextId
taskId
traceId
spanId
remoteAgentId when applicable
```

trajectory event 与普通 log 使用同一组 correlation keys。这样 ops 可以从日志跳到 OTel trace，再跳回 A2A task。

### 4.6 复用主流开源组件

硬原则：

- A2A：继续使用 A2A SDK 的 request handling、task/event、JSON-RPC transport；不自研并行 A2A 协议栈。
- OTel：继续使用 OpenTelemetry SDK/exporter；`@ConditionalOnClass` 应覆盖实际 SDK/exporter 类。
- Spring lifecycle/health/config：继续复用 Spring Boot `SmartLifecycle`、configuration properties、actuator health。
- SSE/HTTP：优先评估 Java 标准 `HttpClient` + 成熟 SSE parser、Spring WebClient SSE、或框架官方 SDK；只有确认上游缺口时，才保留 hand-written parser，并集中成一个测试充分的小组件。
- LangGraph / AgentScope：优先评估官方或主流 Java SDK；如果没有成熟 SDK，保留薄 HTTP adapter，但禁止把框架协议状态机泄漏进 runtime SPI。

---

## 5. Findings and disposition

| ID | Severity | Finding | Disposition |
|---|---:|---|---|
| F1 | P1 | README 配置契约与 `RuntimeAccessProperties` 不一致，`public-base-url` / `default-agent-id` 未实现。 | W1 修复或删文档；建议实现。 |
| F2 | P1 | `AgentExecutionContext` 携带 A2A `Message`，SPI 协议中立性不足。 | W2 引入 runtime-native message model。 |
| F3 | P1 | 文档承诺 run/session/task-control/internal queue，但实现更像 A2A SDK bridge + adapter library。 | W1 truth-up 定位；W3 再决定是否补 durable runtime。 |
| F4 | P2 | remote A2A 配置只有 URL，缺少生产通信控制面。 | W2/W3 扩展 properties 与 outbound lifecycle。 |
| F5 | P2 | logs correlation 弱于 trajectory，MDC 缺 tenant/agent/trace/span。 | W1 补 MDC 与测试。 |
| F6 | P2 | OTel optional condition 只看 API class，不看 SDK/exporter class。 | W1 修正条件装配。 |
| F7 | P3 | AgentScope/LangGraph SSE parser 与部分 JSON 拼接为手写实现。 | W2 做 OSS/SKD survey；能复用则替换。 |

---

## 6. Alternatives considered

| Alternative | Why rejected |
|---|---|
| 把 `agent-runtime` 扩张为完整平台 runtime | 会与 `agent-service` / governance / orchestration 边界重叠，增加本地跨模块耦合风险。 |
| 接受 A2A type 进入 SPI | 当前快，但会把 handler 开发体验绑定到 A2A wire model，后续每接一个入口都会更痛。 |
| 删除 trajectory northbound，只保留 OTel | 对最终用户和开发调试不友好；northbound opt-in 是合理隐私默认值。 |
| 自研 remote protocol/client 栈 | 违反“主流开源已具备能力则复用”的基本原则，也会扩大协议兼容风险。 |
| 立即抽象所有 framework adapters | 过早抽象。应先用 runtime-native model 统一边界，再只抽公共的 HTTP/SSE/telemetry primitives。 |

---

## 7. Delivery waves

### W1: Contract truth-up and low-risk hardening

- 实现或删除 `public-base-url` / `default-agent-id` 文档契约。
- Agent Card endpoint 使用 configured public base URL。
- MDC 增加 tenant/agent/trace/span correlation keys。
- OTel `@ConditionalOnClass` 覆盖 SDK/exporter。
- README / quickstart / L1 docs truth-up：把定位写成 embeddable A2A-first runtime library。
- Verification:
  - `./mvnw -pl agent-runtime -am clean verify`
  - `bash gate/check_architecture_sync.sh`

### W2: Protocol-neutral core

- 引入 runtime-native message/conversation/invocation model。
- `A2aAgentExecutor` 负责 A2A mapping；handler SPI 不再暴露 A2A spec。
- 更新 ArchUnit：`engine.spi` 禁止依赖 `org.a2aproject.sdk.spec..`。
- 补 handler developer tests 和 A2A mapping tests。

### W3: Remote A2A production surface

- 扩展 remote agent properties：auth、timeout、retry、refresh、cancel-on-timeout。
- catalog refresh 支持已可用 entry 的更新与 transport cache invalidation。
- timeout 后 best-effort remote cancel。
- actuator health detail 暴露 remote agent availability。

### W4: OSS reuse survey and adapter simplification

- 对 LangGraph / AgentScope Java SDK、SSE parser、Spring WebClient SSE 做 source-backed survey。
- 有成熟实现则替换手写 parser；没有则将 parser 收敛成共享小组件并补协议样例测试。
- 保持 framework-specific 状态机在各 adapter 内，不上提到 SPI。

---

## 8. Verification plan

- [ ] Generated facts are read before implementation decisions.
- [ ] `./mvnw -pl agent-runtime -am clean verify` passes.
- [ ] `bash gate/check_architecture_sync.sh` passes.
- [ ] ArchUnit verifies no local sibling module dependency and no A2A spec leakage into neutral SPI after W2.
- [ ] A2A JSON-RPC and SSE smoke tests cover send, stream, cancel, input-required, failure frame.
- [ ] Agent Card behind configured public base URL is verified.
- [ ] OTel disabled / API-only / full SDK classpath scenarios are covered.
- [ ] Remote timeout triggers observable failure and best-effort cancel.

---

## 9. Self-audit

Open ship-blocking categories:

- **Contract drift:** yes, README/config mismatch is real and should be W1.
- **Protocol leakage:** yes, A2A type in execution context is real and should be W2.
- **Local cross-module dependency:** no current source import from sibling modules observed; keep gate.
- **Runtime completeness:** partial. Lifecycle and trajectory exist; durable run/session/query semantics should either be explicitly out of scope or implemented by a future proposal.
- **OSS reuse:** partial. A2A/OTel/Spring reuse is good; AgentScope/LangGraph/SSE parsing needs a fresh reuse survey before further hand-rolled expansion.

---

## 10. Decision request

Approve the following direction:

```text
Make agent-runtime a narrow, strong, A2A-first runtime library:
  clear protocol boundary,
  truthful deployment config,
  production-grade remote communication surface,
  correlated logs + trajectory,
  and explicit reuse of mainstream open-source runtime/protocol components.
```

If accepted, W1 should be treated as corrective hardening rather than feature work, because it resolves externally visible contract drift.
