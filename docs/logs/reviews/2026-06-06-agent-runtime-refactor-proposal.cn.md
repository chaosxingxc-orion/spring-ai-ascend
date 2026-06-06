---
affects_level: L1
affects_view: [logical, development]
proposal_status: accepted-for-impl
authors: ["Chao Xing", "Claude"]
supersedes: docs/logs/reviews/2026-06-05-agent-runtime-sdk-five-module-refactor-proposal.cn.md
responds_to: docs/logs/reviews/2026-06-05-agent-runtime-refactor-decision-response.cn.md
proposes_retiring_adrs: [ADR-0071, ADR-0075, ADR-0158]
related_rules: [R-D, R-F, R-G, R-K]
affects_artefact: []
---

# Proposal — agent-runtime 中立执行核重建（自有方案，supersede Doc 2）

> **Date:** 2026-06-06
> **Status:** Accepted-for-impl（核心已实现并 e2e 验证；传输/治理收尾进行中）
> **Supersedes:** Doc 2《agent-runtime 五层重构实施方法》(x00209170)
> **背景:** 本提案是 owner 自有方案，吸收 Doc 2 的打包卫生方向，按决策回执（`2026-06-05-...decision-response`）的 D1–D10 落地，并已用真实框架 + 真实 LLM 端到端验证。

## 0. 摘要

把 `agent-runtime` 的执行核重建为一个**框架中立、响应式、单产物可多形态部署**的运行时，对外只暴露一条 **I/O 边界中立 seam**，所有框架私有复杂度（tool/memory/skill/MCP/中间件）下沉到各框架 adapter。**v1 已实现 openjiuwen adapter，并在三种部署形态下 e2e 跑通 `ping→pong`**（真实 openjiuwen ReActAgent + 真实 A2A + 真实 LLM）。

## 1. 中立 seam（已实现）

唯一的 runtime 中立 SPI 在 I/O 边界——`com.huawei.ascend.runtime`：

```
common/   RunEvent, RunEventType{ACCEPTED,CHUNK,COMPLETED,FAILED}, RunPhase{PENDING,RUNNING,WAITING_INPUT,SUCCEEDED,FAILED,CANCELLED}, InvocationRequest
engine/   RunCoordinator                       # 包一个 AgentDriver，产出 Flow.Publisher<RunEvent>
  spi/    AgentDriver                           # name/description/frameworkId/start/stop/isRunning/invoke(InvocationRequest):Object/outputConverter()
          AbstractAgentDriver                   # start/stop/isRunning 默认实现
          OutputConverter                       # convert(Object 原生流):Flow.Publisher<RunEvent>
  adapters/openjiuwen/  OpenJiuwenAgentDriver, OpenJiuwenOutputConverter   # openjiuwen 私有全封内
```

- **流模型**：JDK `java.util.concurrent.Flow.Publisher`（响应式、零新依赖、与已退役 EnginePort 的 `Flow.Publisher` 一致、满足 Rule R-F/R-G）。
- **`AgentDriver.invoke` 返回不透明 `Object`**（原生流：openjiuwen `Iterator`、langchain4j 回调 `TokenStream`、langgraph4j `AsyncGenerator`、dify SSE 句柄……都能塞）；中立边界 = `OutputConverter.convert` 的**输出**。
- **tool/memory/skill/MCP/中间件不上 runtime SPI**——全在 adapter 内（openjiuwen 的 `Tool`/`AgentRail`/`McpClient`/`LongTermMemory` 都封在 `OpenJiuwenAgentDriver`）。**runtime 不为 openjiuwen 定制。**

## 2. 命名约定（三约束，已落地）

① 不镜像 AgentScope；② 行业通识、不怪名（从五框架 grep 提取）；③ 能与 openjiuwen 一致就一致。

| 概念 | 本方案 | 依据 |
|---|---|---|
| 运行编排 | `RunCoordinator` | 避开 AgentScope/openjiuwen 同名 `Runner`（防镜像 + 防依赖撞名）；coordinator 行业通用 |
| 框架适配 SPI | `AgentDriver` | JDBC 式 "driver"；异于 AgentScope `AgentHandler` |
| 输出转换 | `OutputConverter`/`convert` | Spring `Converter` 约定；异于 AgentScope `StreamAdapter` |
| 流元素 | `RunEvent`/`RunEventType`/`RunPhase` | "event/phase" 通用；异于 AgentScope 裸 `Event`/`RunStatus` |
| 输入 | `InvocationRequest` | "invocation/request" 通用 |
| 动词 | `invoke`/`stream`/`start`/`stop`/`isRunning` | **对齐 openjiuwen**（其 agent `invoke(inputs,session)`/`stream`、Runner `start/stop`）；`isRunning` 取 Spring `Lifecycle` |

## 3. 两/三种部署形态（单产物，已 e2e 验证）

同一 `RunCoordinator(AgentDriver)` 内核 + A2A 传输，三形态共用：

1. **源码SDK托管 / 独立微服务 A2A**：Spring Boot app 嵌入 agent-runtime、暴露 `/a2a`，被远程 A2A 客户端 over-the-wire 调用。✅ `OpenJiuwenReactAgentA2aE2eTest` `ping→pong`。
2. **网关前置（中心化）**：AgentService/网关按 `tenant/agentId` 路由到多个 runtime 实例。✅ `RuntimeGatewayFullStackE2eTest`/`RuntimeRegistryPingPongE2eTest`。
3. （收尾）专用 `spring-boot-starter-runtime-a2a`：从 handler/driver bean 自动装配 A2A 服务——纯打包便利，行为同形态 1。

**AgentService = 纯控制面编排器**（启动/注册/路由/治理 runtime 实例），web/app 留在 agent-runtime。

## 4. e2e 验证证据（真实框架 + 真实 A2A + 真实 LLM）

- 环境：openjiuwen `agent-core-java:0.1.12`（本地源码构建装入 .m2）；LLM = Ollama `gemma4:latest`（OpenAI 兼容 `/v1`，无 thinking 污染）。
- `examples/agent-runtime-a2a-llm-e2e` 全套 **15/15 绿**，运行期日志实证经新核：`... (new engine core) ... terminalKind=COMPLETED` + LLM `content=pong`。
- 引擎核独立 e2e：`OpenJiuwenAgentDriverEngineE2eTest`（真实 ReActAgent→`RunCoordinator`→Ollama）`ping→pong` 绿。

## 5. 治理与收尾（remaining）

- **退役**（W0，需 superseding ADR）：ADR-0158 的 **EnginePort/ExecutorAdapter/双模(GRAPH/AGENT_LOOP)/SuspendSignal 引擎契约机制**——经 grep 证从未接进活跃 A2A 路径。**注意：不全退 ADR-0158**，其"单产物多部署形态 + `Flow.Publisher` 响应式 + engine 不依赖 service + PC-002/004 绑定"是本方案**继承**的；ADR-0071 伞内子决策逐项裁；ADR-0075 评估；ADR-0020(RunStatus) 保留。落地后同步 architecture-status.yaml baseline lockstep + facts。
- **旧路径退役清理**（W8）：当前新核经 **W4 桥接**运行在旧 `dispatch.spi` access 之后；后续把 A2A access 直接接 `RunCoordinator`，退役旧 `dispatch`/`engine` 残留 + 其测试。
- **Doc 3**（yougq）随之改为面向新 `AgentDriver`（非旧 `AgentHandler`），YAML 装配的 tool/skill 交框架原生注册，不引入 runtime 级中立 ToolResolver。
- **Doc 1**（registry/gateway）并入 AgentService 控制面，清理 `agent-examples`/`agent-service` 命名残留。

## 6. 异构兼容（接缝已为五框架预留）

`AgentDriver`(frameworkId) + `adapters/<framework>/`；v1 仅 openjiuwen 完整实现，agentscope-java / spring-ai-alibaba / langchain4j / langgraph4j / **dify(远程 REST+SSE)** 预留。adapter 两类：进程内 Java 库 + 远程协议（Dify/MCP）。SAA 为竞品，其 adapter 须独立可选模块、适配不依赖核心（[[feedback_saa_competitor]]）。

## Authority / 关联
- 退役目标：ADR-0158(部分)/ADR-0071/ADR-0075（待 superseding ADR）；ADR-0159 已归档。
- supersedes Doc 2；关联决策回执（2026-06-05）、Doc 1、Doc 3。
- 实现分支：`feature/agent-runtime-agentscope-rebuild`（W-env+W2+W3+W4，5 commit）。
