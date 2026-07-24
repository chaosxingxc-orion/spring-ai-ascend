---
level: L2-LLD
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-002-versatile-intent-workflow
status: active
target_module: agent-solution-zyw/common/agent-runtime-ext-java/agent-service-adapters/agent-service-adapters-versatile
runtime: agent-runtime-java/service/agent-service-spec
dependency:
  - ../../L1-High-Level-Design/agent-runtime/README.md
  - ../../L1-High-Level-Design/agent-runtime/development.md
  - ../../L1-High-Level-Design/agent-runtime/process.md
  - ./Feat-Func-002-heterogeneous-agent-framework-compatibility.md
  - ../../../version-scope/FEAT-002-versatile-intent-workflow-adapter-compatibility.md
  - ../../../version-scope/FEAT-002-heterogeneous-agent-framework-compatibility.md
  - ../../../version-scope/Feat-008-user-interaction-interrupt-response.md
  - ../../../version-scope/Feat-015-agent-card-registration-and-discovery.md
  - ../../../version-scope/FEAT-016-runtime-instance-route-query.md
  - ../../../version-scope/FEAT-012-client-invocation-bus-forwarding.md
  - ../../../version-scope/FEAT-013-client-invocation-event-forwarding.md
  - ../../../version-scope/FEAT-014-a2a-call-event-forwarding.md
  - ../../../prd/PRD-intent-recognition-and-multi-agent-collaboration-short-term.md
---

# Versatile 意图识别工作流适配兼容 — 设计文档

> 目标模块：`agent-solution-zyw/common/agent-runtime-ext-java/agent-service-adapters/agent-service-adapters-versatile`
> Runtime SPI：`agent-runtime-java/service/agent-service-spec`
> 最后更新：2026-07-21

---

## 1. 概述

### 1.1 特性定位

本特性在通用 Versatile HTTP/SSE 代理能力上叠加 Versatile 意图识别工作流的标准接入约束，使独立部署的一层、二层意图识别工作流以及最终下游业务工作流能通过同一个 `VersatileAgentHandler` 接入 runtime，并对外保持统一的 query/result/interrupt/error 语义。

- **解决的问题**：客户低码平台 Versatile 意图识别工作流采用两层独立工作流结构——一层确定大业务领域并返回唯一二层 `agent_id`，二层确定具体业务并返回唯一下游 `agent_id`；最终业务工作流在判断分类错误时还能返回指向固定一层意图 Agent 的结构化下一跳结果以触发重新分类。通用 Versatile adapter 当前只组装 `query`+`intent` 两字段、只提取 `text` 一字段、且把"无 terminal event 即关闭"统一映射为 `TYPE_INTERRUPT`，既不满足三字段输入契约（`query`/`intents`/`messages`），也不满足三字段结果契约，更会把异常断流误报为用户交互中断。本特性把这些差异约束在既有 `VersatileAgentHandler` 内部，不新增公开类。
- **L2 范围**：本文只覆盖 **Adapter 层** 的输入映射、三字段结果提取、显式用户交互中断转换、异常断流分离、原工作流恢复与失败/取消/可观测语义。PRD（`PRD-intent-recognition-and-multi-agent-collaboration-short-term.md`）覆盖的下游调用链（FEAT-015 Agent Card 查询、FEAT-016 实例路由、FEAT-014 A2A 调用）、跨 Runtime Task 所有权、下游直接用户消息投影、跳转与重新分类的循环保护、取消级联等由 runtime 下游调用能力与 Agent Bus 的 L2 文档约束；本 Adapter 只承诺把工作流显式产生的三字段结果、用户消息、交互中断以标准 chunk/result 形式交给 runtime，不参与下游编排。
- **适用场景**：客户集成与部署流程已完成一层、二层及最终下游 Versatile 工作流的独立部署，并通过 `openjiuwen.service.versatile.*` 配置由不同 runtime 实例接入；每个 runtime 实例只调用当前实例配置的一个工作流。

### 1.2 当前事实边界

本文承接 `version-scope/FEAT-002-versatile-intent-workflow-adapter-compatibility.md` 行为契约与 `PRD-intent-recognition-and-multi-agent-collaboration-short-term.md` 短期方案。version-scope 与 PRD 使用 `AgentRuntimeHandler`/`AgentExecutionContext`/`AgentExecutionResult` 等术语描述契约，实际 runtime（`agent-runtime-java`）当前提供的 SPI 是 `AgentHandler`/`ServeRequest`/`QueryResponse`/`QueryChunk`。本文以下列映射把 spec 术语落到实际代码；中断与续接语义只使用 runtime 已有的 `QueryChunk(TYPE_INTERRUPT)` + `QueryResponse.result._interrupt` 承载，不新增或假设 `UserInputInterrupt`/`USER_INTERACTION_RESUME` 等未经确认的接口名称，同 Task 续接入口由 FEAT-008 定义。

| Spec 术语（version-scope / PRD） | 实际代码（agent-runtime-java） |
|---|---|
| `AgentRuntimeHandler.execute(AgentExecutionContext) → Stream<AgentExecutionResult>` | `AgentHandler.query(ServeRequest) → QueryResponse` 与 `AgentHandler.streamQuery(ServeRequest, QueryStreamObserver)` |
| `AgentExecutionContext.mainInput` | `ServeRequest.lastUserQuery()`（或 `messages` 中最后一条 user 消息 content） |
| `AgentExecutionContext.metadata` | `ServeRequest.metadata`（Map，含 `body`/`headers`/`query` 子结构） |
| `AgentExecutionContext.conversationId` | `ServeRequest.conversationId` |
| `AgentExecutionResult.COMPLETED` 携带三字段 | `QueryResponse.result` Map 携带 `response_content`/`intent_id`/`agent_id` |
| `AgentExecutionResult.INTERRUPTED`（携带用户提示与续接关联） | `QueryChunk(TYPE_INTERRUPT, payload)` + `QueryResponse.result._interrupt` Map |
| `AgentExecutionResult.FAILED` | `QueryChunk(TYPE_ERROR, data)` 或 `IllegalStateException` 上抛 |
| `versatile.*` 配置 | `openjiuwen.service.versatile.*`（`VersatileProperties`） |

### 1.3 设计原则

1. **零新增公共类型** — 复用 `AgentHandler`、`ServeRequest`、`QueryResponse`、`QueryChunk` 与现有 `VersatileAgentHandler`；理由是避免职责重复的公开类污染 SPI 表面。
2. **单实现多实例** — 同一个 `VersatileAgentHandler` 类通过不同 runtime 实例的 `openjiuwen.service.versatile.*` 配置适配一层、二层及最终下游工作流；理由是层级差异不构成两套 Adapter。
3. **工作流输入/输出契约外置** — 三字段输入（`query`/`intents`/`messages`）与三字段结果的字段名、JSON 路径、必填性、缺省值均由 `VersatileProperties` 扩展配置声明，Adapter 不硬编码字段路径；理由是 Versatile 原生协议路径随部署变化（PRD TBD-03/04）。
4. **三字段结果是统一载体** — 匹配成功、未匹配、需要澄清、跳转、重新分类交接均通过相同三字段结果表达，Adapter 对其执行相同技术转换；理由是 PRD 已明确 Runtime 不解释业务语义。
5. **机器可读优先** — 三字段结果以结构化字段写入 `QueryResponse.result`，禁止仅写入 `content` 自然语言；理由是下游调用能力不能从自然语言推导目标。
6. **业务语义中立** — Adapter 不解释匹配成功/未匹配/需要澄清/跳转/重新分类的业务含义，不选择候选目标，不做兜底；理由是业务决策归属客户工作流或其调用方。
7. **显式中断与异常断流分离** — 当前代码把"无 terminal event 即关闭"映射为 `TYPE_INTERRUPT`；本特性必须将其拆分为：显式原生中断（按配置识别）才构造 `TYPE_INTERRUPT`，无 terminal event 的连接关闭改为 `TYPE_ERROR`。
8. **Adapter 不参与跨 Runtime 编排** — 下游 Agent Card 查询、实例路由、A2A 调用、Task 所有权、直接用户消息投影、跳转循环保护由 runtime 下游调用能力与 Agent Bus 承担；Adapter 只对当前 Runtime 的 Task 产出 chunk/result。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| 单工作流实例适配 | 通过 `openjiuwen.service.versatile.*` 配置使同一 Handler 实现适配一层/二层/下游独立部署的意图识别工作流 | `VersatileAgentHandler`, `VersatileProperties` | ✅ |
| 三字段输入映射 | 把 `ServeRequest` 主输入、配置候选意图列表与会话消息历史映射为 `query`/`intents`(JSON 数组)/`messages`(JSON 数组)；一层 `response_content` 在二层调用前作为 assistant 消息追加到 `messages` | `VersatileRequestExtractor` 扩展 | ⬜ |
| 三字段结果提取（统一载体） | 提取 `response_content`/`intent_id`/`agent_id` 并以机器可读形式保留于 `QueryResponse.result`；匹配/未匹配/澄清/跳转/重新分类共用 | `VersatileResponseExtractor` 扩展 | ⬜ |
| `agent_id` 兼容映射（1:N + 策略） | Versatile 工作流未返回 `agent_id` 时通过 `intent-agent-mapping`（`Map<String, List<String>>`）与选择策略把 `intent_id` 翻译为 A2A Gateway `agentCard`；工作流直接返回 `agent_id` 时优先使用返回值 | `VersatileProperties.IntentAgentMapping` + `IntentAgentMappingStrategy` + `VersatileResponseExtractor` 映射分支 | ⬜ |
| 显式用户交互中断转换 | 把工作流显式原生中断转换为 `QueryChunk(TYPE_INTERRUPT)` + `_interrupt` Map，且信息完整才构造 | `VersatileResponseExtractor` 中断分支改造 | ⬜ |
| 异常断流分离 | 无 terminal event 的连接关闭改为 `TYPE_ERROR`，不再映射为 `TYPE_INTERRUPT` | `VersatileResponseExtractor.finish()` 改造 | ⬜ |
| 原工作流恢复 | Adapter 不区分新调用与 resume；runtime 处理 resume 入口与 `ServeRequest` 构造；Adapter 按配置可选填充 `resume-request-template` | `VersatileRequestExtractor` + `interrupt.resume-request-template` | ✅（Adapter 侧不感知 resume） |
| 下游直接用户消息承载 | 工作流面向用户的业务消息通过 `QueryChunk(TYPE_CHUNK)` 承载，交由 runtime/Bus 投影 | `VersatileResponseExtractor` 已有 `TYPE_CHUNK` 输出 | ✅（Adapter 侧）；投影链路依赖 FEAT-012/013 |
| 状态/失败/取消映射 | 工作流完成、显式中断、远端失败、配置缺失、结果契约违反、恢复失败映射为标准 chunk/result | `VersatileResponseExtractor` + `VersatileAgentHandler` | ⬜ |
| Runtime 转发能力 a2a_delegate 路径扩展 + SPI 化（依赖项，不在 Adapter 内） | runtime 核心 module 改动严格限定为 a2a_delegate 路径扩展 + SPI 改造（`InterruptData` 新增 `responseContent` 字段 + `RemoteAgentCall` 值对象含 `responseContent` + 既有 `A2ARemoteAgentClient`/`A2AAgentCardDiscovery` 签名升级为 `RemoteAgentCaller`/`RemoteAgentCardResolver` SPI 实现忽略 `responseContent` 逻辑等价 + `A2AEnabledServeOrchestrator` 的 `handleA2ADelegate` 路径消费 `resume=false` 与 `responseContent`、构造 `RemoteAgentCall` 并通过 `RemoteAgentCaller` SPI 执行转发）；Adapter 产出 `a2a_delegate` interrupt；messages 追加、A2A Gateway 路由、进程内联调等能力放部署模块 `versatile-intent-boot` 的 SPI 实现内部，runtime 核心 module 不硬依赖 | `RemoteAgentCaller`, `RemoteAgentCardResolver`, `RemoteAgentCall`, `InterruptData.responseContent`, `A2ARemoteAgentClient`, `A2AAgentCardDiscovery`（runtime 核心 module）；`A2AGatewayRemoteAgentCaller`, `A2AGatewayCardResolver`, `InProcessRemoteAgentCaller`, `LocalHttpRemoteAgentCaller`（versatile-intent-boot） | ⬜（runtime 侧 a2a_delegate 路径扩展 + SPI 改造 + 部署模块能力实现） |
| 可观测与敏感信息保护 | 调用、中断、恢复、失败的可关联观察记录与敏感字段掩码 | 现有日志 + 掩码策略 | ⬜（依赖 DFX-001） |

> 状态标记说明：✅ 已在现有代码中具备；⬜ 需要按本特性扩展。

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| 一层/二层/下游独立接入 | ✅ | 同一 `VersatileAgentHandler` 类，不同 runtime 实例通过 `openjiuwen.service.versatile.*` 配置分别接入 |
| 单实例单工作流 | ✅ | 一个 runtime 实例只调用当前配置的一个意图识别工作流（由 `urlTemplate` 或 `endpoints` 唯一确定） |
| 主输入映射为 `query` | ✅ | 当前代码已通过 `ServeRequest.lastUserQuery()` 提取主输入并放入 `inputs.query` |
| 上一层 `response_content` 作为二层 `messages` 补充上下文 | ✅ | 调用方/runtime 编排组件把上一层 `response_content` 作为 `assistant` 消息加入二层 `ServeRequest.messages`；Adapter 从 `ServeRequest.messages` 按顺序提取并映射为 `messages` 数组，不区分消息来源 |
| 一层/二层 `query` 均来自用户本轮输入 | ✅ | 二层 `query` 与一层同源（用户本轮输入）；一层 `response_content` 作为补充上下文进入二层 `messages`，不替代二层 `query`（重新分类场景见 4.7） |
| 意图候选列表 `intents` 数组透传 | ⬜ | 当前代码无此字段；需扩展 `VersatileProperties.Intents`（List<{id,name}>）与 `VersatileRequestExtractor`，把配置列表序列化为 JSON 数组传给工作流 |
| 会话消息 `messages` 数组透传 | ⬜ | 当前代码无此字段；需扩展 `VersatileProperties.Messages` 与 `VersatileRequestExtractor`，从 `ServeRequest.messages` 会话历史提取 `role`/`content` 序列化为 JSON 数组；二层调用前由 runtime 编排组件在一层 `ServeRequest.messages` 末尾追加一层 `response_content` 作为 assistant 消息（见 4.9） |
| 三字段正常结果提取 | ⬜ | 当前代码只提取 `text` 一字段；需扩展 `VersatileResponseExtractor` 提取三字段并写入 `QueryResponse.result` |
| `agent_id` 兼容映射（`intent_id` → `agentCard`，1:N + 策略） | ⬜ | Versatile 工作流当前未支持返回 `agent_id`；通过 `intent-agent-mapping`（`Map<String, List<String>>`）+ 选择策略把 `intent_id` 翻译为 A2A Gateway `agentCard`；工作流直接返回 `agent_id` 时优先使用工作流返回值 |
| 三字段结果作为跳转/重新分类统一载体 | ⬜ | Adapter 不区分业务结果类型，统一提取三字段；跳转/重新分类由 runtime 下游调用能力消费 |
| 唯一 `agent_id` 校验 | ⬜ | 当前无校验；需在结果提取阶段加校验 |
| 结构化结果保留 | ⬜ | 当前 `result` Map 只有 `role`/`content`/`_interrupt`；需扩展承载三字段 |
| 显式用户交互中断转换 | ⬜ | 当前代码把"无 terminal event"统一映射为 `TYPE_INTERRUPT`；需改为只在工作流显式原生中断且信息完整时构造 |
| 异常断流分离 | ⬜ | 当前 `finish()` 在 `!isCompleted` 时产出 `TYPE_INTERRUPT`；需改为 `TYPE_ERROR` |
| 原工作流恢复 | ✅ | Adapter 不区分新调用与 resume；runtime 处理 resume 入口；Adapter 按配置可选填充 `interrupt.resume-request-template` |
| 再次中断适配 | ✅ | 恢复后的工作流再次请求用户交互时按 4.4 节重新映射为 `TYPE_INTERRUPT` |
| 协作式取消 | ✅ | 当前 `streamQuery` 已通过 `observer.isCancelled()` 检查并在取消时抛 `CancellationException` |
| Runtime 转发能力支持 `xxxx/{agentCard}` 路由 + messages 追加（依赖项，不在 Adapter 内） | ⬜ | runtime 编排组件当前转发硬编码 `/.well-known/agent-card.json` + `jsonRpcPath`，无 `{agentCard}` 占位符；需抽象为 `RemoteAgentCaller` / `RemoteAgentCardResolver` SPI 并新增 A2A Gateway 实现；转发由 runtime 编排组件在 Adapter 返回三字段后发起，转发时把当前层 `response_content` 作为 assistant 消息追加到 `messages` 数组传给下一层（见 4.9） |
| 下游直接用户消息承载 | ✅ | Adapter 侧已通过 `QueryChunk(TYPE_CHUNK, data)` 输出工作流消息；投影到原客户端由 FEAT-012/013 负责 |
| 结构化技术失败 | ⚠️ | 当前失败路径上抛 `IllegalStateException`；需在 `VersatileResponseExtractor` 中区分失败阶段并产出 `TYPE_ERROR` |
| 可观测关联 | ⚠️ | 当前 `VersatileAgentHandler` 已记录 conversation_id/user_id/tenant_id；trace/correlation 关联依赖 DFX-001 |
| 敏感字段掩码 | ⬜ | 当前日志直接打印 messages/metadata；需按 DFX-001 加掩码 |

### 2.2 显式排除

| 排除项 | 原因 | 归属 |
|--------|------|------|
| 新增专用 Handler / SPI 方法 / 结果类 / 中断类 | 破坏零新增公共类型原则 | 复用 `AgentHandler`、`QueryChunk`、`QueryResponse.result` Map |
| 在单个 Adapter 实例内编排一层、二层和下游工作流 | 越过单 Agent runtime 边界 | 由调用方/runtime 下游调用能力串行调用 |
| FEAT-015 Agent Card 查询 / FEAT-016 实例路由 / FEAT-014 A2A 调用 | 属于 runtime 下游调用能力 | runtime 下游调用能力 L2 |
| 跨 Runtime Task 所有权 / 远端 Task 引用 / 终态收敛 | 各 Runtime 独立拥有 Task | runtime 下游调用能力 L2 + FEAT-014 L2 |
| 跳转/重新分类的循环保护（deadline、最大次数、重复路径检测） | 属于 runtime 下游调用能力 | runtime 下游调用能力 L2 |
| 取消级联（父 Task 取消沿活动调用链逐级取消下游） | 属于 runtime 下游调用能力与 FEAT-014 | runtime 下游调用能力 L2 |
| A2A Gateway 转发逻辑 SPI 化 | runtime 当前转发硬编码 `/.well-known/agent-card.json` + `jsonRpcPath`，无 `{agentCard}` 占位符；本特性需把转发抽象为 SPI 以支持 `xxxx/{agentCard}` 路由（见 4.9） | 本特性 4.9 节承担 |
| 下游直接用户消息投影到原客户端 / 用户响应直达 Task owner | 属于 Agent Bus 与 Gateway | FEAT-012/013 L2 + Agent Bus L2（PRD TBD-07） |
| `agent_id` 注册中心查询 / 别名转换 / 候选目标推荐 | 属于 FEAT-015/016 与客户工作流 | FEAT-015/016 L2 |
| 业务匹配 / 兜底 / 候选 `agent_id` 选择 / 分类错误判断 | 业务决策归属客户工作流或调用方 | 客户工作流 |
| 重新分类循环保护的业务规则（同一错误业务目标识别等） | 业务规则归属客户工作流或 runtime 下游调用能力配置 | runtime 下游调用能力 L2 + 客户配置 |
| Versatile 原生协议字段路径硬编码 | 路径随部署变化 | 由 `openjiuwen.service.versatile.*` 配置声明 |
| 调用模型生成字段 / 推断消息角色 / 拼接总结会话内容 | 破坏业务语义中立原则 | Adapter 仅做字符串映射与校验 |

### 2.3 接口契约（Logical View）

#### 2.3.1 SPI / API 声明

本特性不新增任何公开类、方法或类型。所有外部接口以 `AgentHandler`、`ServeRequest`、`QueryResponse`、`QueryChunk` 为准。本节列出复用入口及其在本特性下的语义约束。

```java
// 复用入口，签名不变；本特性约束 request 与 response 的语义
public interface AgentHandler {
    /**
     * 非流式执行当前 runtime 实例配置的意图识别工作流。
     *
     * @implSpec
     * - 输入：从 request.lastUserQuery() 取主输入映射为 query；
     *   从 openjiuwen.service.versatile.intents 取候选意图列表（List<{id,name}>），序列化为 JSON 数组；
     *   从 ServeRequest.messages 会话历史取 role/content 字段序列化为 messages JSON 数组；
     *   二层调用前由 runtime 编排组件（非 Adapter）把一层 response_content 作为 assistant 消息追加到 messages 数组。
     * - 恢复：Adapter 不区分新调用与 resume；runtime 在调用前已完成 resume 上下文解析与 ServeRequest 构造。
     *   若配置了 interrupt.resume-request-template，Adapter 从 metadata 取值填充恢复请求体。
     * - 结果：QueryResponse.result 携带 role/content/response_content/intent_id/agent_id；
     *   匹配成功、未匹配、需要澄清、跳转、重新分类交接均通过相同三字段结果表达；
     *   显式用户交互中断时 result._interrupt 携带交互提示、输入要求与续接关联；
     *   失败时上抛 IllegalStateException 或 result 中携带 error 标识。
     */
    QueryResponse query(ServeRequest request);

    /**
     * 流式执行；通过 QueryStreamObserver 推送 QueryChunk。
     * TYPE_CHUNK 流式中间结果（含工作流面向用户的直接业务消息）；
     * TYPE_INTERRUPT 显式用户交互中断；
     * TYPE_ERROR 失败；observer.isCancelled() 触发协作式取消。
     */
    void streamQuery(ServeRequest request, QueryStreamObserver observer);
}
```

```java
// 复用 QueryChunk 类型，签名不变；本特性约束 TYPE_INTERRUPT 的构造条件
public class QueryChunk {
    public static final String TYPE_INTERRUPT = "interrupt";  // 仅显式原生中断且信息完整时构造
    public static final String TYPE_CHUNK = "chunk";          // 含工作流直接用户业务消息
    public static final String TYPE_ERROR = "error";          // 异常断流、远端失败、契约违反
}
```

```java
// 本特性新增 SPI（agent-runtime-java/service/agent-service-app）
public interface RemoteAgentCaller {
    /**
     * 按 agentId 把 ServeRequest 透传到远端 runtime 并把 chunk 流回 observer。
     * 默认实现走 A2A SDK；A2A Gateway 实现按 xxxx/{agentCard} 路由。
     */
    void call(RemoteAgentCall call, QueryStreamObserver observer);

    /** 是否支持该 agentId 的路由（用于多实现共存时选择） */
    boolean supported(String agentId);
}

public interface RemoteAgentCardResolver {
    /** 按 agentId 解析 Agent Card URL */
    String resolveCardUrl(String agentId);
    /** 按 agentId 解析 JSON-RPC URL */
    String resolveJsonRpcUrl(String agentId);
}
```

#### 2.3.2 数据类型

| 类型 | 关键字段 | 含义 | 约束 |
|------|---------|------|------|
| `ServeRequest` | `conversationId`, `messages`, `userId`, `spaceId`, `tenantId`, `stream`, `metadata` | 贯穿链路的执行上下文 | `lastUserQuery()` 非空（除非 resume）；`messages` 会话历史供 `messages` 数组映射 |
| 三字段工作流输入 | `query`, `intents`, `messages` | 传递给 Versatile 意图工作流的输入 | `query` 为 string 必填；`intents` 为 JSON 数组字符串 `[{"id":"...","name":"..."},...]` 必填且非空；`messages` 为 JSON 数组字符串 `[{"role":"...","content":"..."},...]` 必填 |
| `intents` 数组元素 | `id`, `name` | 候选意图标识与名称 | 均 string，非空；`id` 与 `intent-agent-mapping` 的 key 对应 |
| `messages` 数组元素 | `role`, `content` | 会话消息角色与内容 | 均 string，非空；二层调用时一层 `response_content` 作为 `assistant` 消息追加到末尾 |
| 三字段工作流结果 | `response_content`, `intent_id`, `agent_id` | 工作流正常完成的结构化结果（含跳转/重新分类交接） | 全部 `string`；`intent_id`/`agent_id` 非空；`agent_id` 唯一；`agent_id` 为 FEAT-015 可查询的逻辑 agentId |
| `QueryResponse` | `result` (Map), `conversationId` | 非流式聚合结果 | `result` 含 `role`/`content`/`response_content`/`intent_id`/`agent_id`；中断时含 `_interrupt` |
| `QueryChunk` | `type`, `data` | 流式 chunk | `TYPE_INTERRUPT` 的 `data` 须含 prompt/input-requirement/resume-token |

#### 2.3.3 行为承诺

- **必须**：每个 runtime 实例只调用当前实例 `openjiuwen.service.versatile.*` 配置的一个意图识别工作流。
- **必须**：`query` 来自 `ServeRequest.lastUserQuery()`；一层与二层 `query` 均来自用户本轮输入；重新分类时新一层 `query` 来自业务工作流 `response_content`。
- **必须**：`intents` 数组来自 `VersatileProperties.Intents`（List<{id,name}>）配置，Adapter 序列化为 JSON 数组字符串透传给工作流；不从 Agent Card 或用户输入推导。
- **必须**：`intents` 数组必须非空；每个元素的 `id` 与 `name` 均为非空字符串；`id` 与 `intent-agent-mapping` 的 key 对应（用于工作流未返回 `agent_id` 时的映射查找）。
- **必须**：`messages` 数组来自 `ServeRequest.messages` 会话历史，Adapter 提取每条消息的 `role`/`content` 序列化为 JSON 数组字符串透传；不生成新消息、不推断角色、不拼接总结或改写消息内容。
- **必须**：二层调用前由 runtime 编排组件（非 Adapter）把一层 `response_content` 作为一条 `assistant` 消息追加到 `messages` 数组末尾，与原会话历史一同传递给二层工作流。
- **必须**：正常完成时提取并保留三字段；`response_content`/`intent_id` 必须由工作流返回且非空；`agent_id` 可由工作流返回或由 `intent-agent-mapping`（1:N）+ 选择策略映射查找，最终必须非空且唯一。
- **必须**：三字段以机器可读形式写入 `QueryResponse.result`，禁止仅写入 `content` 自然语言字段。
- **必须**：一层/二层 Adapter 只提取并返回三字段（含 `agent_id`），不调用下一层 runtime，不依赖 `versatile-intent-boot`；Adapter 在三字段结果就绪时产出 `a2a_delegate` interrupt（`resume=false` + `agentName=agent_id` + `responseContent=response_content`），`agent_id` 的消费与跨 Runtime 转发由 `A2AEnabledServeOrchestrator.handleA2ADelegate` 通过 `RemoteAgentCaller` SPI 执行。A2A Gateway URL 模式为 `xxxx/{agentCard}`，`response_content` 通过 `InterruptData.responseContent` → `RemoteAgentCall.responseContent` 从 orchestrator 传到 Caller；messages 追加在 `A2AGatewayRemoteAgentCaller` / `InProcessRemoteAgentCaller` / `LocalHttpRemoteAgentCaller` 实现的 `call()` 内部完成（基线 `A2ARemoteAgentClient` 忽略 `responseContent`，保持原逻辑）；runtime 核心 module 改动严格限定为 a2a_delegate 路径扩展 + SPI 改造（`InterruptData.responseContent` 字段 + `A2ARemoteAgentClient` + `A2AAgentCardDiscovery` 签名升级为 `implements` `RemoteAgentCaller` / `RemoteAgentCardResolver` SPI + `RemoteAgentCall` 值对象 + orchestrator `handleA2ADelegate` 消费 `resume=false` + `responseContent` 构造 `RemoteAgentCall` 并通过 `RemoteAgentCaller` SPI 转发），messages 追加等新增能力由部署模块 SPI 实现内部承担（见 4.9）。
- **禁止**：Adapter 在提取三字段后自行调用 `RemoteAgentCaller` 或任何跨 Runtime 转发组件；单工作流 Adapter 边界要求 Adapter 只对当前 Runtime 的 Task 产出结果。
- **必须**：匹配成功、未匹配、需要澄清、跳转、重新分类交接执行相同的技术完成语义；Adapter 不区分业务结果类型。
- **必须**：`agent_id` 是当前 tenant 下 FEAT-015 可直接查询的逻辑 `agentId`，Adapter 不做别名转换。
- **必须**：`intent_id` 作为低码业务关联信息透传；当工作流未返回 `agent_id` 时，`intent_id` 作为 `intent-agent-mapping`（1:N）的 key 查找候选 `agentCard` 列表，Adapter 按部署配置的选择策略（`first`/`priority`/`round-robin`，默认 `first`）选定唯一值；策略属于部署期固化的静态规则，不做业务判断。
- **禁止**：查询注册中心验证 `agent_id` 是否存在或可路由。
- **禁止**：把 `intent-agent-mapping` 的多候选选择用于业务判断（如根据会话内容、用户画像选择不同 agent）；多候选选择只按部署期策略执行。
- **必须**：工作流显式请求用户交互且信息足以构造标准中断时，产出 `QueryChunk(TYPE_INTERRUPT, payload)` 并在非流式 `result._interrupt` 中携带交互提示、输入要求与续接关联。
- **必须**：无 terminal event 的连接关闭映射为 `TYPE_ERROR`，不构造 `TYPE_INTERRUPT`。
- **必须**：客户端通过同一 Task 续接时（续接入口与 `ServeRequest` 续接载荷载体由 FEAT-008 定义，本特性不新增 resume input type），Adapter 从 `ServeRequest` 取已校验用户响应并结合 `conversationId` 与续接关联恢复原工作流，不创建新工作流执行。
- **必须**：恢复后的工作流再次请求用户交互时再次产出 `TYPE_INTERRUPT`。
- **必须**：远端失败、超时、配置缺失、契约违反、恢复失败映射为 `TYPE_ERROR` 或 `IllegalStateException`，并保留可区分的失败阶段。
- **必须**：runtime 发起取消时（`observer.isCancelled()`）停止继续消费本次结果并尽力通知远端工作流。
- **必须**：工作流面向用户的直接业务消息通过 `QueryChunk(TYPE_CHUNK)` 承载，由 runtime/Bus 负责投影到原客户端；Adapter 不负责跨 Runtime 消息路由。
- **必须**：调用、中断、恢复、失败使用 Runtime 提供的 tenant/task/conversation/request/trace 语义建立观测关联。
- **禁止**：在缺少有效交互描述时构造 `TYPE_INTERRUPT`。
- **禁止**：把远端技术失败转换为正常 `intent_id`，或生成匹配/澄清/未匹配/跳转/重新分类业务兜底结果。
- **禁止**：把异常断流映射为 `TYPE_INTERRUPT` 或 `COMPLETED`。
- **禁止**：调用模型生成字段、推断消息角色、拼接总结或改写会话内容。
- **禁止**：查询注册中心验证 `agent_id` 是否存在或可路由。
- **禁止**：跨 Runtime 共享 Task execution state；Adapter 只对当前 Runtime 的 Task 产出结果。
- **允许**：工作流返回空 `response_content`（业务是否允许空字符串由客户工作流契约决定）。
- **允许**：恢复失败时保留与远端调用失败不同的失败阶段标识，供调用方区分。

---

## 3. 模块结构（Development View）

### 3.1 包结构

本特性不新增包或类，仅对既有 `com.openjiuwen.service.adapters.versatile` 包内组件施加意图工作流场景的配置扩展和行为约束。差异点标注如下。

```
agent-service-adapters-versatile/src/main/java/com/openjiuwen/service/adapters/versatile/
├── agentfw/
│   ├── VersatileAgentHandler.java         # 不变签名；扩展 resolveQueryResult 承载三字段与 _interrupt payload；
│   │                                      #   提取三字段后返回，不调用下一层 runtime（转发由 runtime 编排组件承担）
│   ├── VersatileRequestExtractor.java     # 扩展：三字段 inputs 组装（query/intents/messages）+ resume 请求模板填充
│   ├── VersatileResponseExtractor.java    # 扩展：三字段结果提取 + 显式中断与异常断流分离
│   └── VersatileHttpClient.java           # 不变；HTTP/SSE 客户端，支持 observer 取消
└── autoconfigure/
    ├── VersatileProperties.java           # 扩展：Intents 列表（List<{id,name}>，候选意图）
    │                                      #       + Messages 映射规则（从 ServeRequest.messages 取 role/content）
    │                                      #       + IntentAgentMapping（intent_id → List<agentCard> 1:N 映射）
    │                                      #       + IntentAgentMappingStrategy（first/priority/round-robin，默认 first）
    │                                      #       + Interrupt 子配置（signal-match/prompt-get/input-requirement-get/resume-token-get/resume-request-template）
    │                                      #       + ResultExtraction 三字段提取规则
    └── VersatileAutoConfiguration.java    # 不变

agent-runtime-java/service/agent-service-app/src/main/java/com/openjiuwen/service/app/controller/a2a/
├── client/
│   ├── RemoteAgentCaller.java             # 新增 SPI 接口（runtime 核心 module 仅保留 SPI 改造）
│   ├── RemoteAgentCardResolver.java       # 新增 SPI 接口
│   ├── RemoteAgentCall.java               # 新增值对象（agentId + ServeRequest + responseContent
│   │                                      #   + contextId/taskId/message/streaming legacy 兼容字段）
│   ├── RemoteAgentException.java          # 新增：远端调用结构化异常（REMOTE_TIMEOUT/
│   │                                      #   REMOTE_STREAM_CLOSED/REMOTE_ERROR）
│   ├── RemoteInputRequiredException.java  # 新增：远端 INPUT_REQUIRED 信号，携带 remoteTaskId
│   ├── RemoteAgentAnswerExtractor.java    # 新增：answer envelope 通用解析工具（type/output/
│   │                                      #   response_content/agent_id/intent_id 提取）
│   ├── A2ARemoteAgentClient.java          # 保留类名与文件名，签名改为 implements RemoteAgentCaller
│   │                                      #   （基线 Caller；逻辑等价：URL/card 查询/SDK 调用/chunk 流回/
│   │                                      #   错误处理完全不变；忽略 responseContent, 不追加 messages）
│   ├── A2AAgentCardDiscovery.java         # 保留类名与文件名，签名改为 implements RemoteAgentCardResolver
│   │                                      #   （基线 Resolver；逻辑等价：baseUrl + /.well-known/agent-card.json
│   │                                      #   + jsonRpcPath 不变）
│   └── A2ARemoteAgentCardRegistry.java    # 既有 card 缓存组件（保留，配合 SPI 化的 Discovery）
└── orchestrator/
    └── A2AEnabledServeOrchestrator.java   # 改动含 SPI 依赖迁移 + handleA2ADelegate 路径扩展：
                                           #   消费 InterruptData.resume=false 与 responseContent，
                                           #   构造 RemoteAgentCall 并通过 RemoteAgentCaller SPI 执行转发；
                                           #   远端再返回 a2a_delegate envelope 时继续转发（递归单跳），
                                           #   远端返回纯答案时直接返回（不 resume 父 handler）。
                                           #   不追加 messages（messages 追加在 Caller 实现内部完成）

# runtime 核心 module 改动严格限定为 a2a_delegate 路径扩展 + SPI 改造：
#   - InterruptData 新增 responseContent 字段
#   - 新增 RemoteAgentCaller / RemoteAgentCardResolver SPI 接口 + RemoteAgentCall 值对象
#   - A2ARemoteAgentClient / A2AAgentCardDiscovery 保留类名，签名改为 implements 对应 SPI
#     （逻辑等价，忽略 responseContent）
#   - A2AEnabledServeOrchestrator 的 handleA2ADelegate 路径消费 resume=false + responseContent，
#     构造 RemoteAgentCall 并通过 RemoteAgentCaller SPI 转发
# 其他能力（messages 追加、A2AGateway / InProcess / LocalHttp 实现）由部署模块 versatile-intent-boot 提供，
# 通过 Spring Bean 注入为对应 SPI 实现
```

### 3.2 核心类静态关系

```
«interface»                  «concrete»
AgentHandler                   VersatileAgentHandler
  ↑                              │  (implements)
  └──── implements ──────────────┘
                                 │
                                 ├── uses → VersatileRequestExtractor    (三字段 inputs + resume 请求模板填充)
                                 ├── uses → VersatileResponseExtractor   (三字段结果 + 显式中断 + 异常断流分离)
                                 ├── uses → VersatileHttpClient          (HTTP/SSE，支持取消)
                                 └── uses → VersatileProperties          (intents / messages / intent-agent-mapping / interrupt.* / result-extractions)

      ┌──────────────────────────────────────────────────────────────────────────────┐
      │ 以下属于 runtime 编排层（agent-runtime-java/service/agent-service-app），       │
      │ 不属于 VersatileAgentHandler；Adapter 不依赖 RemoteAgentCaller SPI            │
      │ runtime 核心 module 改动: a2a_delegate 路径扩展 + SPI 改造:                     │
      │   InterruptData.responseContent + RemoteAgentCall 值对象 +                    │
      │   既有类签名升级为 SPI 实现 + orchestrator handleA2ADelegate 消费 resume=false │
      └──────────────────────────────────────────────────────────────────────────────┘

«interface» RemoteAgentCaller                «interface» RemoteAgentCardResolver
        ↑                                              ↑
        │                                              │
  «concrete» A2ARemoteAgentClient              «concrete» A2AAgentCardDiscovery
  (保留类名, implements RemoteAgentCaller;     (保留类名, implements RemoteAgentCardResolver;
   逻辑等价: URL/card 查询/SDK 调用/             逻辑等价: baseUrl +
   chunk 流回/错误处理完全不变;                  /.well-known/agent-card.json,
   忽略 responseContent, 不追加 messages)        jsonRpcPath 不变)

  «concrete» A2AEnabledServeOrchestrator
  (改动: SPI 依赖迁移 + handleA2ADelegate 路径扩展:
   消费 InterruptData.resume=false + responseContent,
   构造 RemoteAgentCall, 通过 RemoteAgentCaller SPI 转发;
   远端再返回 a2a_delegate → 继续转发;
   远端返回纯答案 → 直接返回, 不 resume 父 handler.
   不追加 messages, 不构造新 ServeRequest)

      ┌──────────────────────────────────────────────────────────────────────────────┐
      │ 以下属于部署模块 versatile-intent-boot，不属于 runtime 核心 module              │
      │ 通过 Spring Boot 自动装配按 Profile 注入为 RemoteAgentCaller /                 │
      │ RemoteAgentCardResolver SPI 实现                                               │
      │ 承担 SPI 之外的 messages 追加、Gateway 路由、进程内联调等能力                    │
      └──────────────────────────────────────────────────────────────────────────────┘

  «concrete» A2AGatewayRemoteAgentCaller      «concrete» A2AGatewayCardResolver
  (xxxx/{agentCard} 路由，生产形态;            (xxxx/{agentCard}/.well-known/agent-card.json;
   消费 responseContent 追加 messages,          其余逻辑与基线 A2AAgentCardDiscovery 对齐)
   其余逻辑与基线 A2ARemoteAgentClient 对齐)

  «concrete» InProcessRemoteAgentCaller
  (开发联调形态; 消费 responseContent 追加 messages 后,
   进程内直调目标 AgentHandler Bean, 不走 HTTP)

  «concrete» LocalHttpRemoteAgentCaller
  (本地多端口联调形态; 消费 responseContent 追加 messages,
   按 localhost:port 走 A2A SDK, 用于完整 HTTP/SSE 链路验证)

QueryChunk  ──── typed by ────►  TYPE_CHUNK / TYPE_INTERRUPT / TYPE_ERROR
QueryResponse  ──── carries ───►  result Map { role, content, response_content, intent_id, agent_id, _interrupt? }
```

---

## 4. 核心设计（Logical + Process View）

### 4.1 单工作流实例适配与两层调用链

#### 4.1.1 关键处理流程

```
runtime 实例 A (一层)               runtime 实例 B (二层)               runtime 实例 C (下游业务)
        │                                   │                                   │
        │ openjiuwen.service.versatile:     │ openjiuwen.service.versatile:     │ openjiuwen.service.versatile:
        │   url-template: .../workflow_L1   │   url-template: .../workflow_L2   │   url-template: .../workflow biz
        │   intents:                        │   intents:                        │   (使用通用 Versatile Adapter，
        │     - id: intent_L1_hotel         │     - id: intent_L2_hotel_dom     │    或本特性扩展，二者皆可)
        │       name: 酒店                  │       name: 国内酒店              │
        │     - id: intent_L1_flight        │     - id: intent_L2_hotel_int     │
        │       name: 机票                  │       name: 国际酒店              │
        ▼                                   ▼                                   ▼
  VersatileAgentHandler              VersatileAgentHandler              VersatileAgentHandler
  (同一类，不同实例配置)             (同一类，不同实例配置)             (同一类，不同实例配置)

  转发：一层/二层 Adapter 只提取三字段并返回（含 agent_id）；runtime 编排组件消费 agent_id
        后通过 A2A Gateway 转发 SPI（见 4.9）按 agent_id 路由到下一层 runtime；
        转发时把当前层 response_content 作为一条 assistant 消息追加到 ServeRequest.messages 末尾，
        原 ServeRequest（含用户本轮输入）转发；
        二层 Adapter 从 ServeRequest.lastUserQuery() 取得用户本轮输入作为 query，
        从 ServeRequest.messages 取得会话历史 + 一层 response_content 组装为 messages JSON 数组。
```

- 同一个 `VersatileAgentHandler` 类被三个 runtime 实例各自实例化；每个实例只读取自己的 `VersatileProperties`。
- 一层与二层都基于用户本轮输入识别，但聚焦不同粒度（一层识别大业务领域，二层识别具体业务）；两层 `query` 均来自 `ServeRequest.lastUserQuery()`；A2A Gateway 转发时把一层 `response_content` 作为 assistant 消息追加到 `messages` 数组传给二层，二层 `messages` 数组 = 会话历史 + 一层输出。
- 一层返回三字段（含指向二层的 `agent_id`） → A2A Gateway 按 `agent_id` 转发到二层 runtime（`messages` 追加一层 `response_content`） → 二层 Adapter 收到用户本轮输入 + 一层输出，执行二层识别 → 返回三字段（含指向下游的 `agent_id`） → A2A Gateway 转发到下游 runtime（`messages` 追加二层 `response_content`）。
- `response_content` 作为业务上下文传递给下一层（通过 `messages` 数组追加 assistant 消息），不作为下一层 `query` 来源（除重新分类场景，见 4.7）。
- 当前代码 `VersatileRequestExtractor.resolveUrlTemplate(intent)` 已支持按 `intent` 选择 `endpoints[i].urlTemplate`，本特性复用此机制；`intent` 字段仍用于 URL 路由，与配置侧的 `intents`（工作流输入候选意图列表）语义不同，二者不冲突。

### 4.2 三字段输入映射

#### 4.2.1 关键处理流程

```
ServeRequest                       VersatileRequestExtractor              RemoteRequest
    │                                    │                                       │
    │── lastUserQuery() ────────────────>│                                       │
    │── messages (会话历史) ────────────>│                                       │
    │                                    │── 读 properties.intents (List)     ──┐
    │                                    │── 校验非空 + 每元素 id/name 非空    │
    │                                    │── 序列化为 JSON 数组字符串           │
    │                                    │   [{"id":"...","name":"..."},...]    │
    │                                    │── 从 ServeRequest.messages 提取      │
    │                                    │   role/content 序列化为 JSON 数组    │
    │                                    │   [{"role":"...","content":"..."}]   │
    │                                    │   (二层调用时末尾含一层 response_content │
    │                                    │    作为 assistant 消息，由 runtime 编排组件追加) │
    │                                    │── query = lastUserQuery()            │
    │                                    │<─────────────────────────────────────┘
    │                                    │── inputs = { query, intents,         │
    │                                    │              messages }              │
    │                                    │── remoteBody = { inputs, custom_data }─>│
```

#### 4.2.2 输入字段映射规则

| 字段 | 来源 | 必填 | 缺省 | 校验 |
|------|------|------|------|------|
| `query` | `ServeRequest.lastUserQuery()` | MUST | — | 非空字符串 |
| `intents` | `VersatileProperties.Intents`（List<{id,name}>） | MUST | — | 非空数组；每元素 `id`/`name` 非空字符串；序列化为 JSON 数组字符串透传 |
| `messages` | `ServeRequest.messages` 会话历史 | MUST | — | 非空数组；每元素 `role`/`content` 非空字符串；序列化为 JSON 数组字符串透传 |

- `intents` 数组元素结构：

```yaml
openjiuwen:
  service:
    versatile:
      intents:
        - id: intent_L1_hotel           # 与 intent-agent-mapping 的 key 对应
          name: 酒店
        - id: intent_L1_flight
          name: 机票
```

- `intents` 来源选项（由 `VersatileProperties.Intents.source` 控制，默认 `config`）：
  - `config`（默认）：从 `application.yml` 的 `openjiuwen.service.versatile.intents` 读取；部署时静态声明候选意图列表。
  - `metadata`：从 `ServeRequest.metadata` 指定字段读取（用于调用方动态传入候选列表；目前不常用，保留扩展点）。
  - 未来可扩展从 FEAT-015 Agent Card 注册中心查询候选意图列表（PRD TBD-15，本特性不实现）。
- `intents[].id` 必须与 `intent-agent-mapping` 的 key 对应；Adapter 不强制校验二者一致，但配置不一致会导致工作流返回 `intent_id` 后映射查找失败（产出 `VERSATILE_INTENT_AGENT_ID_UNMAPPED`）。
- `messages` 数组来源：`ServeRequest.messages` 会话历史；二层调用时，runtime 编排组件（非 Adapter）在 `ServeRequest.messages` 末尾追加一层 `response_content` 作为 `assistant` 消息（见 4.9），二层 Adapter 取时会话历史 + 一层输出共同组成 `messages` JSON 数组。
- Adapter 不推断角色、不生成或总结消息内容；只从 `ServeRequest.messages` 提取 `role`/`content` 字段。
- 部署配置声明为必填且无法取得时（如 `intents` 列表缺失或 `ServeRequest.messages` 为空），`VersatileRequestExtractor.extract()` 抛 `IllegalArgumentException`（携带阶段标识），`VersatileAgentHandler` 捕获后映射为 `TYPE_ERROR` 或上抛。

### 4.3 三字段结果提取（统一载体）

#### 4.3.1 关键处理流程

```
VersatileResponseExtractor                    QueryResponse.result Map
        │                                            │
        │── SSE 帧序列 → consumeLine ──┐             │
        │                              │             │
        │── shouldExtractResult (node_name 命中)     │
        │   ├── extractResult → text                 │
        │── containsNodeTypeEnd → isCompleted=true  │
        │── hasTextField(event,exception) → hasFailed │
        │                                            │
        │── finish()                                 │
        │   ├── hasFailed → TYPE_ERROR               │
        │   ├── isCompleted && result!=null          │
        │   │   ├── 应用 result-extractions          │
        │   │   │   取 response_content              │
        │   │   │   取 intent_id                     │
        │   │   │   取 agent_id (可选)               │
        │   │   ├── 校验 response_content / intent_id │
        │   │   │   存在 + string + 非空             │
        │   │   ├── 解析 agent_id：                  │
        │   │   │   if 工作流返回 agent_id 非空:     │
        │   │   │     使用工作流返回值               │
        │   │   │   else:                            │
        │   │   │     查 intent-agent-mapping[intent_id]
        │   │   │     (Map<String, List<String>>)   │
        │   │   │     命中 → 按策略选 agentCard:     │
        │   │   │       first / priority / round-robin
        │   │   │     未命中或候选空 → TYPE_ERROR    │
        │   │   │       (VERSATILE_INTENT_AGENT_ID_UNMAPPED)
        │   │   ├── 校验 agent_id 非空 + 唯一        │
        │   │   └── TYPE_CHUNK(answer envelope +    ──> result.put("response_content", ...)
        │   │       三字段)                          │ result.put("intent_id", ...)
        │   │                                        │ result.put("agent_id", ...)
        │   ├── isCompleted && result==null → (空)   │
        │   └── !isCompleted → TYPE_ERROR            │
        │       (异常断流，不再 TYPE_INTERRUPT)       │
        │                                            │
        │── 字段缺失/类型错误/agent_id 多值/         ──> TYPE_ERROR(契约违反)
        │   intent_id 无映射且工作流未返回 agent_id  │
```

#### 4.3.2 三字段结果作为统一载体（关键设计）

- 三字段 `response_content`/`intent_id`/`agent_id` 是所有正常完成结果的统一载体，Adapter 不区分业务结果类型：
  - **匹配成功**：`agent_id` 指向最终业务工作流，`response_content` 为业务输入。
  - **未匹配**：`agent_id` 指向客户配置的未匹配处理工作流。
  - **需要澄清**：`agent_id` 指向客户配置的澄清工作流。
  - **跳转**：当前工作流终态返回下一跳三字段，`agent_id` 指向跳转目标。
  - **重新分类交接**：最终业务工作流判断分类错误时返回三字段，`intent_id` 指向一层低码意图工作流 ID，`agent_id` 指向当前租户固定的一层意图 Agent，`response_content` 携带重分类上下文（当前有效用户请求、无法处理的业务目标、分类不适用原因、已执行路径、必要会话信息）。
- Adapter 对上述五类结果执行相同的三字段提取与校验逻辑，不解释 `intent_id`/`agent_id` 的业务含义。
- 重新分类场景的 `response_content` 内部格式由客户确认（PRD TBD-14）；Adapter 只按字符串透传。
- 三字段写入 `QueryResponse.result` Map，键为 `response_content`/`intent_id`/`agent_id`，值为字符串。
- `result.content` 仍保留为自然语言（供向后兼容），但调用方必须通过三字段键读取结构化结果，不得从 `content` 解析。
- 一个 `intent_id` 可对应多个候选 `agent_id`，但客户工作流必须完成本次选择并只返回一个；Adapter 不执行第二次选择。
- `agent_id` 返回数组、多个目标或无法确定唯一值时，Adapter 产出 `TYPE_ERROR`（契约违反），不输出部分正常结果。

#### 4.3.3 `agent_id` 兼容映射（1:N + 策略）

**背景**：当前 Versatile 意图工作流通常只返回 `intent_id`（业务意图标识），不直接返回 `agent_id`（A2A Gateway 路由路径段）。为兼容现有工作流，本特性提供 `intent_id` → `List<agent_id>` 映射配置（1:N）+ 选择策略，映射 value 即 A2A Gateway 中的 `agentCard`。

**解析规则**：

```
agent_id 解析顺序：
  1. 若 result-extractions 提取到工作流返回的 agent_id 非空字符串：
       使用工作流返回值（工作流已支持 agent_id 的场景）
  2. 否则：
       查 intent-agent-mapping[intent_id]
       (Map<String, List<String>>)
       命中且候选列表非空 → 按策略选 agentCard:
         first        : 取列表第一个
         priority     : 按 priority 字段排序后取第一个
         round-robin  : 按 intent_id 轮询（状态按 intent_id 维护）
       命中但候选列表为空 / 未命中 →
         TYPE_ERROR("VERSATILE_INTENT_AGENT_ID_UNMAPPED")
```

**映射语义**：

| 字段 | 来源 | 含义 |
|---|---|---|
| `intent_id` | 工作流返回（必填） | 业务意图标识，用于映射查找 |
| `agent_id` | 工作流返回 或 `intent-agent-mapping` + 策略查找 | A2A Gateway 路由路径段，即 `xxxx/{agentCard}` 中的 `agentCard` |
| `intent-agent-mapping` | `VersatileProperties.IntentAgentMapping` | `Map<String, List<String>>`，key=intent_id，value=候选 agentCard 列表（1:N） |
| `intent-agent-mapping-strategy` | `VersatileProperties.IntentAgentMappingStrategy` | 选择策略：`first`（默认）/ `priority` / `round-robin` |

**选择策略语义**：

- `first`（默认）：取候选列表第一个；列表顺序由配置声明。
- `priority`：候选元素带 `priority` 字段，按 `priority` 升序选第一个；相同 `priority` 按 `first` 兜底。
- `round-robin`：按 `intent_id` 维护轮询游标，每次调用选择下一个；用于多实例负载分担；游标状态由 Adapter 内部维护，进程重启后回退到 `first`。
- 所有策略都是部署期固化的静态规则，**不读取会话内容、用户画像、业务上下文**做选择；不视为第二次业务选择。

**边界**：

- Adapter 不查询 Agent Card 注册中心，不验证 `agent_id` 是否在 FEAT-015 注册；映射只负责把 `intent_id` 翻译为路由路径段。
- 一个 `intent_id` 在映射中可对应多个候选 `agent_id`（1:N）；Adapter 按部署期策略选定唯一值，不做业务判断。
- 工作流完成业务选择时直接返回 `agent_id`，不走映射；映射仅用于工作流未返回 `agent_id` 的兼容场景。
- 重新分类场景下，业务工作流必须直接返回 `agent_id`（指向固定一层 Agent），不依赖映射（因为 `intent_id` 指向一层工作流 ID，与 `agent_id` 的语义关系不固定）。
- `intent-agent-mapping` 在一层、二层配置中各自独立：一层映射 `intent_L1_* → [agentCard_L2_*]`，二层映射 `intent_L2_* → [agentCard_biz_*]`；下游业务工作流配置不使用映射（重新分类直接返回 `agent_id`）。

**与 A2A Gateway 的关系**：

- `intent-agent-mapping` 的 value 元素就是 A2A Gateway `xxxx/{agentCard}` 中的 `agentCard` 路径段。
- `RemoteAgentCaller.call(agentId, ...)` 接收的 `agentId` 即策略选定的 value；A2A Gateway 实现按 `gatewayBaseUrl + "/" + agentId + jsonRpcPath` 构造 URL。
- 因此 `intent-agent-mapping` 既是 Adapter 兼容映射，也是 A2A Gateway 路由表；二者共用同一配置源。

### 4.4 显式用户交互中断转换

#### 4.4.1 关键处理流程

```
Versatile 工作流                VersatileResponseExtractor           VersatileAgentHandler
    │                                    │                                   │
    │── 原生中断事件 ───────────────────>│                                   │
    │   (按 interrupt.signal-match 识别) │                                   │
    │                                    │── 解析 prompt-get 路径 ──┐         │
    │                                    │── 解析 input-requirement │         │
    │                                    │── 解析 resume-token      │         │
    │                                    │<─────────────────────────┘         │
    │                                    │                                   │
    │                                    ├── 信息完整 ──> TYPE_INTERRUPT(      │
    │                                    │     { prompt, input_requirement,   │
    │                                    │       resume_token })              │
    │                                    │                                   │
    │                                    └── 信息不完整 ──> TYPE_ERROR(       │
    │                                          "原生中断信息不足")            │
    │                                                                        │
    │                                                            resolveQueryResult()
    │                                                            ├── TYPE_INTERRUPT →
    │                                                            │   result._interrupt = {
    │                                                            │     message: prompt,
    │                                                            │     input_requirement,
    │                                                            │     resume_token
    │                                                            │   }
    │                                                            └── ...
```

#### 4.4.2 中断构造条件

- 仅当 Versatile 原生响应同时提供以下信息时才产出 `TYPE_INTERRUPT`：
  - 可映射为交互提示的内容（`interrupt.prompt-get` 路径提取）；
  - 可映射为用户输入要求的描述（`interrupt.input-requirement-get` 路径提取）；
  - 可用于恢复原工作流执行的续接关联（`interrupt.resume-token-get` 路径提取）。
- 原生中断事件名、字段路径、JSON/SSE 路径与恢复请求格式由 `VersatileProperties.Interrupt` 配置声明，不在 Adapter 中硬编码。
- 信息不完整时产出 `TYPE_ERROR`（携带 "原生中断信息不足" 类的可诊断错误），不生成缺少有效问题或输入要求的 `TYPE_INTERRUPT`。

#### 4.4.3 异常断流分离（与当前代码的差异）

当前 `VersatileResponseExtractor.finish()`：

```java
// 当前代码（与本特性冲突）
if (!isCompleted) {
    return List.of(new QueryChunk(QueryChunk.TYPE_INTERRUPT, null));  // ❌ 伪造中断
}
```

本特性要求改为：

```java
// 本特性
if (!isCompleted) {
    return List.of(new QueryChunk(QueryChunk.TYPE_ERROR,
        "{\"stage\":\"stream_closed_without_terminal\",\"reason\":\"no End/exception event\"}"));
}
```

- 当前 `VersatileAgentHandler.resolveQueryResult()` 中 `isInterrupted` 分支因此只在真正收到 `TYPE_INTERRUPT` chunk 时触发，不会被异常断流误触发。
- 这是一个**破坏性变更**：依赖"断流即中断"行为的现有部署需在升级时确认工作流是否显式产出中断事件。

### 4.5 原工作流恢复

#### 4.5.1 Adapter 不区分新调用与 resume

- Adapter 不识别"这是 resume 调用"或"这是新调用"；runtime 在调用 `AgentHandler.query()` / `streamQuery()` 之前已完成 resume 上下文的解析、校验与 `ServeRequest` 构造。
- runtime 已实现中断检测与 Task 状态推进：`TaskState.TASK_STATE_INPUT_REQUIRED` 状态、`A2AAgentExecutor` 对 `QueryChunk.TYPE_INTERRUPT` 的检测、`A2AEnabledServeOrchestrator` 把 interrupt chunk 路由到 `INPUT_REQUIRED` 均已就绪；resume 入口（`ServeRequest` 如何承载 `user_response` + `resume_token`）由 FEAT-008 runtime 侧定义，本特性不重复定义。
- Adapter 只按 `ServeRequest.lastUserQuery()` + `ServeRequest.messages` + `VersatileProperties.Intents` / `Interrupt` 配置组装三字段输入与（如配置存在）resume 请求模板；无论 runtime 传入的是新调用还是 resume 调用，Adapter 执行相同的映射逻辑。
- 恢复后的工作流可完成、失败或再次请求用户交互；Adapter 按第 4.3、4.4、4.6 节重新映射，不保留 resume 状态机。

#### 4.5.2 resume 请求模板（可选）

- 若 Versatile 工作流的恢复端点需要特殊请求体（如 `resume_token` + `user_response` 嵌入 `inputs`），由 `interrupt.resume-request-template` 配置声明；Adapter 按模板从 `ServeRequest.metadata` 取值并填充。
- 若工作流恢复端点与新调用端点一致（resume 信号由 `conversationId` 隐式承载），`interrupt.resume-request-template` 可省略，Adapter 按标准三字段输入调用。
- 模板填充失败、`resume_token` 缺失或远端恢复调用失败时产出 `TYPE_ERROR`，保留可区分的失败阶段（恢复阶段 vs 远端调用阶段）。

### 4.6 状态、失败、取消与可观测

#### 4.6.1 状态映射

| 场景 | 标准结果 | 事实要求 |
|------|---------|---------|
| 工作流返回完整三字段正常结果（含匹配/未匹配/澄清/跳转/重新分类） | `QueryResponse.result` + `TYPE_CHUNK(answer)` | 执行相同技术完成语义；业务类型由 runtime 下游调用能力按 `agent_id` 消费 |
| 工作流显式请求用户交互（信息完整） | `TYPE_INTERRUPT` + `result._interrupt` | 携带 prompt/input_requirement/resume_token |
| 远端 HTTP/SSE 调用失败或超时 | `TYPE_ERROR` 或 `IllegalStateException` | 保留远端错误、状态或超时分类 |
| 输入配置缺失或输入映射失败 | `TYPE_ERROR` | 指出失败发生在配置读取或输入组装阶段 |
| 正常结果字段缺失、类型错误或 `agent_id` 不唯一 | `TYPE_ERROR` | 不输出部分正常结果 |
| 用户交互恢复上下文不可用或远端恢复失败 | `TYPE_ERROR` | 保留可区分的失败阶段 |
| HTTP/SSE 无明确 terminal event 即关闭 | `TYPE_ERROR` | 按 FEAT-002 通用异常断流检测，不映射为 `TYPE_INTERRUPT`，不构造 `_interrupt` |
| Runtime 发起取消 | 取消语义 | `observer.isCancelled()` 抛 `CancellationException`；最终 Task 状态由 runtime 推进 |

#### 4.6.2 取消语义

- 当前 `VersatileAgentHandler.streamQuery()` 已通过 `observer.isCancelled()` 检查并在取消时抛 `CancellationException`；本特性保留此行为。
- Adapter 不负责最终 Task 状态推进或取消级联；Task 终态与下游取消由 runtime 下游调用能力与 FEAT-014 负责。
- 当前 `VersatileHttpClient.postStream()` 的 `LineConsumer` 回调中检查取消；远端 HTTP 连接的尽力通知能力依赖客户端关闭连接，本特性不新增独立 cancel 通道。

#### 4.6.3 可观测与敏感信息

- 当前 `VersatileAgentHandler` 已记录 `conversation_id`/`user_id`/`tenant_id`/`messages.size()`，并以 DEBUG 级别记录完整 `messages`/`metadata`；本特性要求：
  - 调用、输入映射、结果提取、中断、恢复、失败建立 trace/correlation 关联（依赖 DFX-001）。
  - 日志、轨迹和错误表面对 `query`/`messages` 数组内容/工作流响应/用户交互响应执行掩码、截断或禁止落盘；具体规则由 DFX-001 约束。
  - 当前 DEBUG 级别直接打印 `messages`/`metadata` 的行为需在启用意图工作流场景时降级或掩码。

### 4.7 跳转与重新分类的 Adapter 职责

#### 4.7.1 Adapter 在跳转/重新分类中的边界

- 跳转与重新分类是业务工作流显式产生的正常结构化终态结果，由当前工作流在终态三字段中表达：
  - 跳转：`agent_id` 指向跳转目标工作流的逻辑 Agent Card。
  - 重新分类：`agent_id` 指向当前租户固定的一层意图 Agent；`intent_id` 指向一层低码意图工作流 ID；`response_content` 携带重分类上下文。
- Adapter 对跳转/重新分类结果执行与匹配成功**完全相同**的三字段提取与校验逻辑，不识别"这是跳转"或"这是重新分类"，也不附加特殊标志。
- 跳转/重新分类的循环保护（deadline、最大次数、同一错误业务目标重复检测、无进展重复路径检测）由 runtime 下游调用能力负责，不在 Adapter 内实现。
- 重新分类时新一层 `query` 来自业务工作流 `response_content`（重分类上下文），由调用方显式构造新 `ServeRequest`：`messages` 最后一条 user 消息 content 为 `response_content`，二层 Adapter 通过 `ServeRequest.lastUserQuery()` 自动取为主输入，不区分首次调用与重新分类调用；同时业务工作流的 `response_content` 也会作为 assistant 消息追加到 `messages` 数组传给新一层工作流。

#### 4.7.2 跳转/重新分类与中断的区别

| 场景 | Task 终态 | 三字段 | `_interrupt` |
|------|----------|--------|--------------|
| 匹配成功/未匹配/澄清 | `COMPLETED` | 存在 | 无 |
| 跳转 | `COMPLETED` | 存在（下一跳） | 无 |
| 重新分类交接 | `COMPLETED` | 存在（指向一层） | 无 |
| 显式用户交互中断 | `INTERRUPTED` | 不产生 | 存在 |
| 远端失败/契约违反/异常断流 | `FAILED` | 不产生 | 无 |

### 4.8 下游直接用户消息的 Adapter 职责

- 最终下游工作流面向用户的直接业务消息通过 `QueryChunk(TYPE_CHUNK, data)` 承载，由 `VersatileResponseExtractor.consumeLine()` 在流式过程中产出。
- Adapter 不负责消息投影到原客户端、不负责用户响应路由；这些由 runtime 下游调用能力与 FEAT-012/013/014 负责（PRD TBD-07/11）。
- 父 Task 的去重（不重复输出下游业务消息）由 runtime 下游调用能力负责；Adapter 只对当前 Task 产出 chunk。

### 4.9 Runtime 转发能力 SPI 化（依赖项，不在 Adapter 内）

> **重构说明（2026-07-24，2026-07-24 修订）**：原设计的 `ServeForwardStrategy` SPI + `NoopServeForwardStrategy` + `ThreeFieldForwardStrategy` 已删除。Versatile Adapter 在意图工作流正常返回时直接产出 `a2a_delegate` interrupt，复用 runtime 核心 module 既有 `a2a_delegate` 转发路径（`handleA2ADelegate` → `delegateSse`/`delegateSync`/`handleQueryInterrupt`）。runtime 核心 module 的 `a2a_delegate` 路径扩展两点：(1) `InterruptData` 携带 `responseContent` 字段，构造 `RemoteAgentCall` 时透传给 Caller（Caller 内部决定是否追加 messages）；(2) Versatile Adapter 在 interrupt payload 中设置 `resume=false`——signal orchestrator 远端返回后**不 resume 父 handler**，远端的终态答案即本层的终态答案；远端若再次返回 `a2a_delegate` envelope 则 orchestrator 继续转发（递归单跳语义）。工具调用式 a2a_delegate（如 AgentCore rail）保持 `resume=true`（默认），维持原有 forward-once + resume 父 handler 语义。
>
> **实现机制说明**：设计阶段曾以 `recursiveForward=true` 命名此语义，落地实现复用 `InterruptData.resume` 既有字段（设为 `false`）而非新增 `recursiveForward` 字段——二者语义等价，均表示"远端返回后不 resume 父 handler"。本特性文档统一以 `resume=false` 表述落地实现；`recursiveForward` 一词仅在本重构说明的历史背景中出现，不再作为接口字段名。SPI 数量从 3 降到 2（`RemoteAgentCaller` / `RemoteAgentCardResolver`）。

> **职责归属**：本节描述的能力分属 runtime 编排层（`agent-runtime-java/service/agent-service-app`）与部署模块（`versatile-intent-boot`），不属于 Versatile Adapter。`VersatileAgentHandler` 只提取并返回三字段（含 `agent_id`），不依赖、不调用 `RemoteAgentCaller` / `RemoteAgentCardResolver` SPI，也不依赖 `versatile-intent-boot`。本节作为本特性的依赖项说明 runtime 编排层需要做的 a2a_delegate 路径扩展与 SPI 化改造，以满足 Versatile 意图识别两层调用链的 `xxxx/{agentCard}` 路由与 `messages` 追加需求。
>
> **模块归属原则**：runtime 核心 module（`agent-service-app`）的改动**严格限定为 a2a_delegate 路径扩展 + SPI 改造**——`InterruptData` 新增 `responseContent` 字段 + `RemoteAgentCall` 值对象（含 `responseContent`）+ 既有 `A2ARemoteAgentClient`/`A2AAgentCardDiscovery` 签名升级为 `RemoteAgentCaller` / `RemoteAgentCardResolver` SPI 实现（保留类名，忽略 `responseContent`，逻辑等价）+ `A2AEnabledServeOrchestrator` 的 `handleA2ADelegate` 路径消费 `resume=false` 与 `responseContent`、构造 `RemoteAgentCall` 并通过 `RemoteAgentCaller` SPI 执行转发；**其他能力**（`messages` 追加与新 `ServeRequest` 构造、A2A Gateway 路由实现、进程内联调实现）**放到部署模块 `versatile-intent-boot`** 的 SPI 实现内部，通过 Spring Boot 自动装配按 Profile 注入为对应 SPI 实现，runtime 核心 module 不硬依赖这些能力。调用链：adapter 产出 `a2a_delegate` interrupt（`resume=false` + `responseContent`）→ orchestrator `handleA2ADelegate` 构造 `RemoteAgentCall` → `RemoteAgentCaller` SPI 调用 Caller → Caller 实现内部决定是否追加 messages。

#### 4.9.1 背景与差距

Versatile 意图识别两层调用链要求：一层 Adapter 提取到 `agent_id`（指向二层）并返回后，runtime 编排组件消费 `agent_id`，通过 A2A Gateway 按 `agent_id` 路由转发到二层 runtime；二层同理转发到下游。Adapter 本身不发起跨 Runtime 转发，单工作流 Adapter 边界保持不变。A2A Gateway 的 URL 路由模式为 `xxxx/{agentCard}`——单一前缀路径下通过 `{agentCard}` 路径段区分目标 Agent。转发时还需把当前层 `response_content` 作为 assistant 消息追加到 `ServeRequest.messages` 末尾，使下一层工作流的 `messages` 数组包含会话历史 + 上一层输出。

runtime 当前转发逻辑硬编码在：
- `A2AAgentCardDiscovery.fetchCard(baseUrl)`：`baseUrl + "/.well-known/agent-card.json"`（`A2AAgentCardDiscovery.java:117-118`）
- `AgentCardController`：`baseUrl + jsonRpcPath`（`AgentCardController.java:88-92`）
- `A2ARemoteAgentClient`：基于 `AgentCard` 通过 A2A SDK `Client.builder(card).withTransport(JSONRPCTransport.class, config)` 发起调用
- `A2AEnabledServeOrchestrator`：在 orchestrator 层串接转发

上述路径构造无 `{agentCard}` 占位符，无法适配 A2A Gateway 的 `xxxx/{agentCard}` 路由模式；且当前转发逻辑不追加上一层 `response_content` 到 `messages`。本特性把 runtime 编排层的"远端 Agent 调用"抽象为 `RemoteAgentCaller` / `RemoteAgentCardResolver` SPI，runtime 核心 module 只做 SPI 改造（接口 + 值对象 + Default 基线实现 + orchestrator SPI 依赖迁移）；`messages` 追加与新 `ServeRequest` 构造作为 SPI 实现的内部行为，由部署模块 `versatile-intent-boot` 的 A2AGateway / InProcess 实现承担——orchestrator 通过 SPI 调用实现，`responseContent` 通过 `RemoteAgentCall` 值对象从 orchestrator 传到 Caller，Caller 内部决定是否追加 messages。Default 实现忽略 `responseContent`，保持与原 `A2ARemoteAgentClient` / `A2AAgentCardDiscovery` 逻辑等价。

#### 4.9.2 a2a_delegate 路径与 SPI 抽象

```
┌─ runtime 编排层（agent-runtime-java/service/agent-service-app）────────────────────────┐
│  改动: a2a_delegate 路径扩展 + SPI 改造                                                     │
│  InterruptData 新增 responseContent 字段; RemoteAgentCall 值对象;                            │
│  既有 A2ARemoteAgentClient / A2AAgentCardDiscovery 签名升级为 SPI 实现;                      │
│  A2AEnabledServeOrchestrator 的 handleA2ADelegate 路径消费 resume=false + responseContent  │
│                                                                                          │
│  VersatileAgentHandler (adapter)                                                         │
│        │── 三字段结果提取完成                                                              │
│        │── 产出 QueryChunk(TYPE_INTERRUPT, {agentName, responseContent,                    │
│        │     resume=false, context:{_interrupt_kind:"a2a_delegate"}})                      │
│        ▼                                                                                 │
│  A2AEnabledServeOrchestrator.handleA2ADelegate()                                         │
│        │── resolveInterruptData 读取 _interrupt_kind=a2a_delegate + agentName              │
│        │   + responseContent + resume=false                                                │
│        │── 构造 RemoteAgentCall{agentId=agentName, ServeRequest=原始, responseContent}     │
│        │── RemoteAgentCaller.call(remoteAgentCall, observer) ──────────┐                   │
│        │                                                                │                   │
│        │   远端返回:                                                     ▼                   │
│        │   ├── 纯答案 (TYPE_CHUNK/COMPLETED) → 直接透传给 observer, 不 resume 父 handler │
│        │   ├── a2a_delegate envelope (resume=false) → 递归 handleA2ADelegate 继续转发    │
│        │   ├── TYPE_INTERRUPT(INPUT_REQUIRED) → 存 shadow task, 透传 interrupt chunk     │
│        │   └── TYPE_ERROR → 透传错误                                                     │
│        │── resume=false: 远端终态即本层终态, 不再调用 VersatileAgentHandler               │
│        │── resume=true (工具调用式 a2a_delegate, 如 AgentCore rail): 远端终态后           │
│        │   resume 父 handler 继续执行 (原有 forward-once 语义, 不变)                      │
│        ▼                                                                                 │
│  «interface» RemoteAgentCaller                «interface» RemoteAgentCardResolver         │
│          ↑                                              ↑                                │
│    «concrete» A2ARemoteAgentClient   «concrete» A2AAgentCardDiscovery                      │
│    (基线 Caller, 逻辑等价: A2A SDK 调用;   (基线 Resolver, 逻辑等价:                        │
│     忽略 responseContent)                  baseUrl + /.well-known/agent-card.json)         │
└──────────────────────────────────────────────────────────────────────────────────────────┘

┌─ 部署模块（versatile-intent-boot）────────────────────────────────────────────────────────┐
│  承担 SPI 之外的跨层转发能力（messages 追加 + Gateway 路由 + 进程内联调 + 本地 HTTP 联调）；  │
│  runtime 核心 module 不硬依赖，通过 Spring Bean 注入                                        │
│                                                                                          │
│    «concrete» A2AGatewayRemoteAgentCaller        «concrete» A2AGatewayCardResolver        │
│    (消费 responseContent, 构造新 ServeRequest     (card URL = gatewayBaseUrl + "/" +      │
│     追加 messages, 按 gatewayBaseUrl + "/" +      agentId + "/.well-known/agent-card.json")│
│     agentId + jsonRpcPath 转发;                                                   │
│     重新分类场景 messages.last 替换 gap 由 follow-up 跟踪, 见 §4.9.3)                     │
│                                                                                          │
│    «concrete» InProcessRemoteAgentCaller                                                 │
│    (开发联调：消费 responseContent 追加 messages 后,                                    │
│     从 Spring ApplicationContext 查找目标 AgentHandler Bean 直调, 不走 HTTP)               │
│                                                                                          │
│    «concrete» LocalHttpRemoteAgentCaller                                                 │
│    (本地多端口联调：消费 responseContent 追加 messages,                                │
│     按 localhost:port 走 A2A SDK, 验证完整 HTTP/SSE 链路)                                 │
└──────────────────────────────────────────────────────────────────────────────────────────┘

VersatileAgentHandler 不依赖上述 SPI —— 单工作流 Adapter 边界
runtime 核心 module 改动: a2a_delegate 路径扩展 + SPI 改造
Default 实现与原 A2ARemoteAgentClient / A2AAgentCardDiscovery 逻辑等价（向后兼容）
A2AEnabledServeOrchestrator 通过 handleA2ADelegate 消费 a2a_delegate interrupt, 通过 RemoteAgentCaller 执行转发
messages 追加在 Caller 实现内部完成
```

- `RemoteAgentCaller` SPI：接收 `RemoteAgentCall`（`agentId` + `ServeRequest` + `responseContent`）与 `QueryStreamObserver`，发起远端调用并把 chunk 流回。**调用方是 `A2AEnabledServeOrchestrator.handleA2ADelegate`（runtime 核心），不是 Versatile Adapter，也不是部署模块的独立组件。**
- `RemoteAgentCardResolver` SPI：把 `agentId` 解析为 Agent Card URL 与 JSON-RPC URL。
- `RemoteAgentCall` 值对象（runtime 核心 module）：`agentId` + `ServeRequest`（原始）+ `responseContent`（可选，上一层 `response_content`，源自 `InterruptData.responseContent`）+ `contextId`/`taskId`/`message`/`streaming`（legacy 兼容字段）；Caller 实现决定是否消费 `responseContent`。
- **`a2a_delegate` interrupt 契约**（Versatile Adapter 产出）：`QueryChunk(TYPE_INTERRUPT, payload)`，payload 含 `agentName`（= 三字段 `agent_id`，作为远端 agentId）、`responseContent`（= 三字段 `response_content`）、`resume=false`、`context._interrupt_kind="a2a_delegate"`。`resume=false` 信号 orchestrator：远端返回纯答案后**不 resume 父 handler**，远端终态即本层终态。工具调用式 a2a_delegate（如 AgentCore rail）不设 `resume=false`（保持默认 `resume=true`），维持原有 forward-once + resume 父 handler 语义。
- **runtime 核心 module（`agent-service-app`）改动严格限定为 a2a_delegate 路径扩展 + SPI 改造**：`InterruptData` 新增 `responseContent` 字段 + `RemoteAgentCaller` / `RemoteAgentCardResolver` SPI 接口 + `RemoteAgentCall` 值对象 + `A2ARemoteAgentClient`/`A2AAgentCardDiscovery` SPI 化（逻辑等价）+ `A2AEnabledServeOrchestrator.handleA2ADelegate` 消费 `resume=false` 与 `responseContent`、构造 `RemoteAgentCall` 并通过 `RemoteAgentCaller` SPI 转发（复用 `callRemoteAndCapture` + `handleRemoteInputRequired`，含 shadow task 保存）；**三字段检测、messages 追加、新 `ServeRequest` 构造、A2A Gateway 路由、进程内联调不在 runtime 核心 module**。
- **`A2AEnabledServeOrchestrator.handleA2ADelegate` 改动范围**：SPI 依赖迁移（`A2ARemoteAgentClient` → `RemoteAgentCaller`、`A2AAgentCardDiscovery` → `RemoteAgentCardResolver`）+ 从 interrupt payload 读取 `agentName`/`responseContent`/`resume=false`/`_interrupt_kind=a2a_delegate` + 构造 `RemoteAgentCall` + 调用 `RemoteAgentCaller.call()` + 远端返回处理（纯答案→透传不 resume；a2a_delegate envelope→递归继续转发；INPUT_REQUIRED→存 shadow task 透传 interrupt；ERROR→透传错误）；其他能力（Task 状态机交互、observer 透传、错误处理等）保持不变。**orchestrator 不检测三字段 envelope、不追加 messages、不构造跨层新 `ServeRequest`——三字段检测在 Versatile Adapter（产出 a2a_delegate interrupt），messages 追加在 Caller 实现内部。**
- **`messages` 追加与新 `ServeRequest` 构造职责归属 Caller SPI 实现**（部署模块 `versatile-intent-boot`）：`A2AGatewayRemoteAgentCaller`、`InProcessRemoteAgentCaller`、`LocalHttpRemoteAgentCaller` 消费 `RemoteAgentCall.responseContent`，在 `call()` 内部构造新 `ServeRequest`（在 `messages` 末尾追加 `{role:"assistant", content: response_content}`）后转发。`A2ARemoteAgentClient`（基线 Caller）忽略 `responseContent`，不追加 messages。
- 基线实现 `A2ARemoteAgentClient` / `A2AAgentCardDiscovery` **承诺与原 `A2ARemoteAgentClient` / `A2AAgentCardDiscovery` 逻辑等价**：URL 构造、Agent Card 查询、A2A SDK `Client.builder(card).withTransport(JSONRPCTransport.class, config)` 调用、chunk 流回、错误处理与异常分类等行为完全不变；迁移不引入新行为（`responseContent` 被忽略、不追加 messages、不重写 URL 模板、不改变错误语义）。**这两个实现放在 runtime 核心 module（`agent-service-app`），作为基线实现，用于非 Versatile 意图识别的原有转发场景。**
- A2A Gateway 实现 `A2AGatewayRemoteAgentCaller` / `A2AGatewayCardResolver`：**消费 `responseContent` 做 messages 追加**（跨层转发场景必需），URL 模板按 `gatewayBaseUrl + "/" + agentId + jsonRpcPath`，其余逻辑（A2A SDK 调用、chunk 流回、错误处理）与基线对齐。**这两个实现放在部署模块 `versatile-intent-boot`，属于生产部署形态，runtime 核心 module 不硬依赖。**
- InProcess 实现 `InProcessRemoteAgentCaller` 放在部署模块 `versatile-intent-boot`（开发联调 scope）：**消费 `responseContent` 做 messages 追加**后，从 Spring `ApplicationContext` 查找目标 `AgentHandler` Bean 直调，不走 HTTP；属于开发联调形态。
- LocalHttp 实现 `LocalHttpRemoteAgentCaller` 放在部署模块 `versatile-intent-boot`（本地多端口联调 scope）：**消费 `responseContent` 做 messages 追加**后，按 `localhost:port` 走 A2A SDK 转发，用于验证完整 HTTP/SSE 链路（header 透传、SSE 编码、Agent Card 解析）。

#### 4.9.3 关键处理流程

```
一层 VersatileAgentHandler   A2AEnabledServeOrchestrator   RemoteAgentCaller SPI        二层 runtime
        │                     (runtime 核心 module)         (versatile-intent-boot 注入
        │                         │                          A2AGatewayRemoteAgentCaller)    │
        │── 提取三字段             │                          │                              │
        │   (agent_id 指向二层,    │                          │                              │
        │    response_content      │                          │                              │
        │    为一层输出)           │                          │                              │
        │── 产出 a2a_delegate      │                          │                              │
        │   interrupt:             │                          │                              │
        │   {agentName=agent_id,   │                          │                              │
        │    responseContent,      │                          │                              │
        │    resume=false,         │                          │                              │
        │    context._interrupt_kind                              │                          │
        │     ="a2a_delegate"}     │                          │                              │
        │── TYPE_INTERRUPT chunk ─>│                          │                              │
        │   (Adapter 侧结束)       │                          │                              │
        │                          │── handleA2ADelegate:     │                              │
        │                          │   resolveInterruptData   │                              │
        │                          │   读取 agentName +       │                              │
        │                          │   responseContent +      │                              │
        │                          │   resume=false           │                              │
        │                          │── 构造 RemoteAgentCall:  │                              │
        │                          │   {agentId=agentName,    │                              │
        │                          │    原 ServeRequest,      │                              │
        │                          │    responseContent}      │                              │
        │                          │── call(remoteAgentCall,  │                              │
        │                          │   observer) ─────────────>│                              │
        │                          │                          │── 消费 responseContent       │
        │                          │                          │── 构造新 ServeRequest:       │
        │                          │                          │   保留原 conversationId/     │
        │                          │                          │   userId/tenantId/metadata   │
        │                          │                          │   + 原 messages 会话历史     │
        │                          │                          │   末尾追加 {role:            │
        │                          │                          │    "assistant",             │
        │                          │                          │    content: responseContent} │
        │                          │                          │   lastUserQuery() 不变       │
        │                          │                          │── resolveUrl(agentId)        │
        │                          │                          │   = gatewayBaseUrl + "/"     │
        │                          │                          │     + agentId + jsonRpcPath  │
        │                          │                          │── 按 A2A SDK 发起调用         │
        │                          │                          │── 转发新 ServeRequest ──────>│
        │                          │                          │   (query=用户本轮输入不变,    │
        │                          │                          │    messages 已含一层输出)     │
        │                          │                          │<── chunk 流 ─────────────────│
        │                          │<── chunk 流 ─────────────│                              │
        │                          │   (透传给原 observer)    │                              │
        │                          │── resume=false:          │                              │
        │                          │   远端终态即本层终态,     │                              │
        │                          │   不 resume 父 handler   │                              │
```

- 一层/二层 Adapter 在 `streamQuery` 完成三字段提取后**产出 `a2a_delegate` interrupt**（`resume=false` + `agentName=agent_id` + `responseContent=response_content`），Adapter 侧执行结束；Adapter 不检测 `agent_id` 是否指向下一层，也不调用 `RemoteAgentCaller`，不依赖 `versatile-intent-boot`。
- runtime 核心 module 改动为 `handleA2ADelegate` 路径扩展：从 interrupt payload 读取 `agentName`/`responseContent`/`resume=false` 构造 `RemoteAgentCall(agentId=agentName, originalRequest, responseContent)` + 调用 `Caller.call()`；**orchestrator 不追加 messages、不构造跨层新 `ServeRequest`**——messages 追加在 Caller 实现内部完成。
- `A2AGatewayRemoteAgentCaller`（部署模块 `versatile-intent-boot`，通过 Spring Bean 注入为 `RemoteAgentCaller` SPI 实现）在 `call()` 内部消费 `responseContent`，构造新 `ServeRequest`：保留原 `conversationId`/`userId`/`tenantId`/`metadata` 与 `messages` 会话历史；在 `messages` 末尾追加 `{role:"assistant", content: responseContent}`；`lastUserQuery()` 不变（仍是用户本轮输入）；然后按 `gatewayBaseUrl + "/" + agentId + jsonRpcPath` 通过 A2A SDK 转发。
- `A2ARemoteAgentClient`（runtime 核心 module，保留类名，签名升级为 `implements RemoteAgentCaller`，用于非 Versatile 意图识别的原有转发场景）忽略 `responseContent`，按原 `A2ARemoteAgentClient` 逻辑转发（不追加 messages）。
- 二层 Adapter 收到转发后的 `ServeRequest`：`query = lastUserQuery()`（用户本轮输入）；`messages` 数组 = 会话历史 + 一层 `response_content`（assistant 消息）。
- 远端返回处理：纯答案 → orchestrator 透传给原 observer，因 `resume=false` 不再调用一层 Adapter；a2a_delegate envelope → orchestrator 递归 `handleA2ADelegate` 继续转发到下一层；INPUT_REQUIRED → 存 shadow task 透传 interrupt chunk；ERROR → 透传错误。
- 若 `agent_id` 指向最终业务工作流且当前 Adapter 即下游，则一层 Adapter 不产出 a2a_delegate interrupt（`agent_id` 指向自身 handler），按 4.3 节正常产出结果。
- 重新分类场景：业务工作流 Adapter 提取到指向一层 `agent_id` 的三字段后产出 `a2a_delegate` interrupt（`responseContent` = 重分类上下文）；orchestrator `handleA2ADelegate` 构造 `RemoteAgentCall`；`A2AGatewayRemoteAgentCaller` 在 `call()` 内部构造新 `ServeRequest`：`messages` 最后一条 user 消息 content **应**为业务工作流 `response_content`（重分类上下文），同时业务工作流 `response_content` 也作为 assistant 消息追加到 `messages` 末尾；一层 Adapter 通过 `lastUserQuery()` 取重分类上下文作为 `query`。**⚠️ 已知 gap：当前 `ForwardedServeRequests.build()` 仅追加 assistant 消息，未做 `messages.last`（user 消息）替换——无法区分"正常跨层转发"与"重新分类"。Caller 收到的 `RemoteAgentCall` 字段在两种场景下完全相同。完整修复需要 orchestrator 在重新分类时传递场景信号（如设置 `call.message()=responseContent`）或由 orchestrator 直接构造替换后的 ServeRequest。此 gap 由 follow-up issue 跟踪（跨仓库：agent-runtime-java + spring-ai-ascend），见 §8。**

#### 4.9.4 SPI 范围边界

| 在 SPI 范围内（Caller 实现职责） | 不在 SPI 范围内（orchestrator / 其他组件职责） |
|---|---|
| URL 模板构造（Default：`baseUrl + jsonRpcPath`；Gateway：`gatewayBaseUrl + "/" + agentId + jsonRpcPath`） | 业务匹配/兜底/候选选择 |
| **messages 追加（A2AGateway / InProcess / LocalHttp 实现消费 `responseContent`，在 `call()` 内部构造新 `ServeRequest`）** | 跳转/重新分类循环保护 |
| **重新分类场景 `messages.last`（user 消息）替换为重分类上下文** — ⚠️ **当前未实现（follow-up gap，见 §4.9.3 与 §8）** | Task 终态收敛、跨 Runtime Task 所有权 |
| Agent Card 查询（按 `agentId` 解析 card URL） | 父 Task 去重、用户消息投影 |
| chunk 流透传（含 `TYPE_INTERRUPT` / `TYPE_ERROR`） | 多候选 agent_id 的策略选择（由 Adapter 按 4.3.3 完成，SPI 只接收最终 agentId） |
| 取消级联（`observer.isCancelled()` 传播到下游） | 原有 orchestrator 编排逻辑（Task 状态机交互、observer 透传等，保持不变） |
| A2A SDK 调用与错误分类（Default 与原 `A2ARemoteAgentClient` 逻辑等价；A2AGateway / InProcess / LocalHttp 在此基础上追加 messages） | 从 `a2a_delegate` interrupt payload 提取 `agentName`/`responseContent`/`resume=false` 构造 `RemoteAgentCall`（**由 orchestrator `handleA2ADelegate` 完成**） |

- Caller 实现承担"按 agentId 解析 URL、（A2AGateway / InProcess / LocalHttp）消费 `responseContent` 追加 messages 构造新 `ServeRequest`、通过 A2A SDK 发起调用、把 chunk 流回 observer、传播取消"的机制职责；**Default 实现不追加 messages（逻辑等价）**。
- `messages` 追加在 **A2AGateway / InProcess / LocalHttp Caller 实现的 `call()` 内部**完成；Default 实现不做此处理。重新分类场景的 `messages.last`（user 消息）替换**当前未实现**（follow-up gap）。
- runtime 核心 module 改动为 `handleA2ADelegate` 路径扩展 + SPI 改造；`A2AEnabledServeOrchestrator` 的改动含 SPI 依赖迁移 + 从 `a2a_delegate` interrupt payload 提取 `agentName`/`responseContent`/`resume=false` 构造 `RemoteAgentCall` 入参 + 调用 `Caller.call()` + 远端返回处理（纯答案/a2a_delegate/INPUT_REQUIRED/ERROR 分支）；原有编排逻辑（Task 状态机交互、observer 透传、错误处理等）保持不变。
- 业务决策（匹配/未匹配/澄清/跳转/重新分类）由客户工作流产生，循环保护由 runtime 下游调用能力负责。
- `agentId` 来源：工作流直接返回的 `agent_id`，或 `intent-agent-mapping`（1:N）+ 策略查得的 `agentCard`（见 4.3.3）；二者都是 A2A Gateway 的 `agentCard` 路径段，SPI 不区分来源。
- **SPI 的调用方是 `A2AEnabledServeOrchestrator.handleA2ADelegate`（runtime 核心），不是 Versatile Adapter，也不是部署模块的独立组件**；Adapter 完成 a2a_delegate interrupt 产出后即结束，不感知 SPI 的存在；部署模块的 Caller 实现通过 Spring Bean 注入到 orchestrator，不主动调用 orchestrator。

#### 4.9.5 配置

```yaml
openjiuwen:
  service:
    a2a-gateway:
      enabled: true
      base-url: https://gateway.example.com       # A2A Gateway 基础 URL
      json-rpc-path: /{agentCard}/a2a
```

- `a2a-gateway.enabled=true` 时注入 `A2AGatewayRemoteAgentCaller` + `A2AGatewayCardResolver`（来自部署模块 `versatile-intent-boot`）；`false` 时注入 runtime 核心 module 的默认实现。
- 部署模块通过 `application.yml` 切换；一层、二层、下游部署模块均可启用。
- 该配置属于部署模块 + runtime 编排层，不属于 `openjiuwen.service.versatile.*`；Versatile Adapter 不读取此配置。

#### 4.9.6 落地范围

- **runtime 核心 module（`agent-runtime-java/service/agent-service-app`）改动严格限定为 a2a_delegate 路径扩展 + SPI 改造**：`InterruptData` 新增 `responseContent` 字段 + `RemoteAgentCaller` / `RemoteAgentCardResolver` SPI 接口 + `RemoteAgentCall` 值对象（含 `agentId` + `ServeRequest` + `responseContent` + legacy 兼容字段）+ `A2ARemoteAgentClient`/`A2AAgentCardDiscovery` SPI 化（迁移自现有代码，忽略 `responseContent`，逻辑等价）+ `A2AEnabledServeOrchestrator.handleA2ADelegate` 消费 `resume=false` + `responseContent` + `agentName` 构造 `RemoteAgentCall` 并通过 `RemoteAgentCaller` SPI 转发（复用 `callRemoteAndCapture` + `handleRemoteInputRequired`，含 shadow task 保存）；**三字段检测、messages 追加、新 `ServeRequest` 构造、A2A Gateway 路由、进程内联调不在此 module**。
- **`A2ARemoteAgentClient` / `A2AAgentCardDiscovery` 与原实现逻辑等价**：URL 构造（`baseUrl + /.well-known/agent-card.json` + `jsonRpcPath`）、Agent Card 查询、A2A SDK `Client.builder(card).withTransport(JSONRPCTransport.class, config)` 调用、chunk 流回、错误处理与异常分类等行为完全不变；`responseContent` 被忽略，不追加 messages；用于非 Versatile 意图识别的原有转发场景。
- **`A2AEnabledServeOrchestrator.handleA2ADelegate` 改动范围**：SPI 依赖迁移（`A2ARemoteAgentClient` → `RemoteAgentCaller` SPI、`A2AAgentCardDiscovery` → `RemoteAgentCardResolver` SPI）+ 从 `a2a_delegate` interrupt payload 读取 `agentName`/`responseContent`/`resume=false` + 构造 `RemoteAgentCall` + 调用 `RemoteAgentCaller.call()` + 远端返回处理（纯答案→透传不 resume；a2a_delegate envelope→递归继续转发；INPUT_REQUIRED→存 shadow task 透传 interrupt；ERROR→透传错误）；其他能力（Task 状态机交互、observer 透传、错误处理等）保持不变。**orchestrator 不检测三字段 envelope、不追加 messages、不构造跨层新 `ServeRequest`。**
- **`handleA2ADelegate` 修复 a2a_delegate 转发 INPUT_REQUIRED resume 断链**：远端返回 INPUT_REQUIRED 时存 shadow task（而非静默吞掉），续接 `findPending` 均能命中 shadow task，恢复远端任务。
- **部署模块 `versatile-intent-boot` 承担 SPI 之外的其他能力**（通过 Spring Bean 注入）：
  - `A2AGatewayRemoteAgentCaller` / `A2AGatewayCardResolver`：A2A Gateway 路由实现，**消费 `responseContent` 做 messages 追加**（跨层转发场景必需），URL 模板按 `gatewayBaseUrl + "/" + agentId + jsonRpcPath`，其余逻辑与基线对齐；属于生产部署形态。`@AutoConfiguration(before = A2AAutoConfiguration.class)` + `@ConditionalOnProperty(a2a-gateway.enabled=true)` 装配。
  - `InProcessRemoteAgentCaller`：开发联调实现，**消费 `responseContent` 做 messages 追加**后，从 Spring `ApplicationContext` 查找目标 `AgentHandler` Bean 直调，不走 HTTP；属于开发联调形态（test/dev scope）。
  - `LocalHttpRemoteAgentCaller`：本地多端口联调实现，**消费 `responseContent` 做 messages 追加**后，按 `localhost:port` 走 A2A SDK 转发，验证完整 HTTP/SSE 链路；属于本地联调形态。
- 部署模块（`versatile-intent-boot`）通过配置 + Spring Profile 选择注入哪个 Caller 实现，不修改 SPI；runtime 核心 module 不硬依赖部署模块中的任何实现。
- **`VersatileAgentHandler`（`agent-service-adapters-versatile` 模块）不引入对 `RemoteAgentCaller` SPI 的依赖，也不依赖 `versatile-intent-boot`**；Adapter 与 runtime 编排层之间的契约是 `a2a_delegate` interrupt payload（`agentName` + `responseContent` + `resume=false` + `_interrupt_kind=a2a_delegate`）。

---

## 5. 配置模型（Physical View）

### 5.1 完整配置示例

```yaml
# 一层意图识别工作流 runtime 实例配置
openjiuwen:
  service:
    versatile:
      url-template: http://host:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}
      timeout: 30s
      headers-template:
        content-type: application/json
        stream: "true"
      forward-header-whitelist:
        - x-language
      result-node-name: custom_rsp_node   # 命中此 node_name 时触发 extractResult

      # 候选意图列表（本特性扩展；工作流入参 intents JSON 数组来源）
      intents:
        - id: intent_L1_hotel                          # MUST，与 intent-agent-mapping 的 key 对应
          name: 酒店                                   # MUST，展示用名称
        - id: intent_L1_flight
          name: 机票
        - id: intent_L1_other
          name: 其他

      # 会话消息映射（本特性扩展；工作流入参 messages JSON 数组来源）
      # 从 ServeRequest.messages 会话历史提取 role/content 字段序列化为 JSON 数组
      # 二层调用时由 runtime 编排组件（非 Adapter）在末尾追加一层 response_content 作为 assistant 消息
      messages:
        source: serve_request_messages                 # 默认且唯一来源
        required: true                                 # 会话消息历史必填

      # intent_id → List<agent_id> 兼容映射（本特性扩展，1:N）
      # 当工作流未返回 agent_id 时，按 intent_id 查此映射得到候选列表
      # Adapter 按策略选定唯一值；value 元素即 A2A Gateway 的 agentCard 路径段
      # key 必须与上面 intents[].id 对应
      intent-agent-mapping:
        intent_L1_hotel:                               # 1:N 候选列表
          - agent_card_layer2_hotel                    # 默认 first 策略选第一个
        intent_L1_flight:
          - agent_card_layer2_flight_a                 # priority 策略按 priority 选
          - agent_card_layer2_flight_b
        intent_L1_other:
          - agent_card_layer2_fallback
      intent-agent-mapping-strategy: first             # first(默认) | priority | round-robin

      # 三字段结果提取（本特性扩展；当前代码只提取 text，需扩展为三字段）
      # agent_id 为可选提取；未提取到时走 intent-agent-mapping + 策略查找
      result-extractions:
        - match: response_content
          get: /custom_rsp_data/data/response_content
        - match: intent_id
          get: /custom_rsp_data/data/intent_id
        - match: agent_id                              # 可选；工作流未返回时不配置或留空
          get: /custom_rsp_data/data/agent_id

      # 显式用户交互中断识别与恢复请求映射（本特性扩展）
      interrupt:
        signal-match: need_user_input                  # 识别原生中断信号的关键字/事件
        prompt-get: /data/question
        input-requirement-get: /data/input_schema
        resume-token-get: /data/resume_token
        resume-request-template:
          body:
            inputs:
              resume_token: "{resume_token}"
              user_response: "{user_response}"

      # endpoints 仍用于按 intent 选择 URL（现有能力，保留）
      endpoints:
        - intent: route_L1
          url-template: http://host:3001/v1/{project_id}/agents/{agent_id_L1}/conversations/{conversation_id}
```

```yaml
# 二层意图识别工作流 runtime 实例配置（同一 Handler 类，不同 url/intents）
openjiuwen:
  service:
    versatile:
      url-template: http://host:3001/v1/{project_id}/agents/{agent_id_L2}/conversations/{conversation_id}
      intents:
        - id: intent_L2_hotel_domestic
          name: 国内酒店
        - id: intent_L2_hotel_international
          name: 国际酒店
        - id: intent_L2_flight_domestic
          name: 国内机票
      # 二层 intent_id → 下游业务 agentCard 映射（1:N）
      intent-agent-mapping:
        intent_L2_hotel_domestic:
          - agent_card_biz_hotel_domestic
        intent_L2_hotel_international:
          - agent_card_biz_hotel_international
        intent_L2_flight_domestic:
          - agent_card_biz_flight_domestic
      intent-agent-mapping-strategy: first
      # ...其余字段同上
```

```yaml
# 最终下游业务工作流 runtime 实例配置
# 选项 A：使用 FEAT-002 通用 Versatile Adapter（不启用 intent.* 扩展）
# 选项 B：使用本特性扩展的同一 Handler（启用 intent.* 扩展以支持重新分类三字段承载）
openjiuwen:
  service:
    versatile:
      url-template: http://host:3001/v1/{project_id}/agents/{agent_id_biz}/conversations/{conversation_id}
      # 下游业务工作流不配置 intent-agent-mapping；
      # 重新分类时必须由工作流直接返回 agent_id（指向固定一层 Agent）
      result-extractions:
        - match: response_content
          get: /custom_rsp_data/data/response_content
        - match: intent_id                          # 重新分类时指向一层低码意图工作流 ID
          get: /custom_rsp_data/data/intent_id
        - match: agent_id                            # 重新分类时必须由工作流返回
          get: /custom_rsp_data/data/agent_id
      # ...其余字段同上
```

### 5.2 配置属性表

| 属性路径 | 类型 | 默认值 | 必填 | 说明 |
|---------|------|--------|------|------|
| `openjiuwen.service.versatile.url-template` | String | — | 是 | 工作流 REST/SSE 入口 URL 模板（现有） |
| `openjiuwen.service.versatile.timeout` | Duration | `600s` | 否 | HTTP/SSE 调用超时（现有） |
| `openjiuwen.service.versatile.headers-template` | Map | `{}` | 否 | 部署预设 header（现有） |
| `openjiuwen.service.versatile.forward-header-whitelist` | Set | `{}` | 否 | flat metadata 透传 allowlist（现有） |
| `openjiuwen.service.versatile.result-node-name` | String | — | 否 | 命中 `node_name` 时触发 `extractResult`（现有） |
| `openjiuwen.service.versatile.endpoints` | List | `[]` | 否 | 按 `intent` 选择 `url-template`（现有） |
| `openjiuwen.service.versatile.intents` | List<{id,name}> | — | 是 | 候选意图列表，序列化为 JSON 数组透传给工作流；`id` 与 `intent-agent-mapping` 的 key 对应（新增） |
| `openjiuwen.service.versatile.intents.source` | String | `config` | 否 | `config`（默认，从配置读取）/ `metadata`（从 `ServeRequest.metadata` 读取）；未来可扩展 Agent Card 注册中心查询（新增） |
| `openjiuwen.service.versatile.messages.source` | String | `serve_request_messages` | 否 | `serve_request_messages`（默认且唯一来源，从 `ServeRequest.messages` 会话历史取 `role`/`content`）（新增） |
| `openjiuwen.service.versatile.messages.required` | boolean | `true` | 否 | 会话消息历史是否必填（新增） |
| `openjiuwen.service.versatile.intent-agent-mapping` | Map<String, List<String>> | `{}` | 否 | `intent_id` → 候选 `agentCard` 列表（1:N）；工作流未返回 `agent_id` 时按 `intent_id` 查此映射；value 元素即 A2A Gateway 路由路径段（新增） |
| `openjiuwen.service.versatile.intent-agent-mapping-strategy` | String | `first` | 否 | 多候选选择策略：`first`（默认）/ `priority` / `round-robin`；属于部署期固化的静态规则，不做业务判断（新增） |
| `openjiuwen.service.versatile.result-extractions` | List | — | 是 | 三字段提取规则；`agent_id` 条目可选，未配置或未提取到时走 `intent-agent-mapping` + 策略查找（新增） |
| `openjiuwen.service.versatile.interrupt.signal-match` | String | — | 否 | 原生中断信号识别关键字或事件名（新增） |
| `openjiuwen.service.versatile.interrupt.prompt-get` | String | — | 否 | 交互提示字段 JSON path（新增） |
| `openjiuwen.service.versatile.interrupt.input-requirement-get` | String | — | 否 | 用户输入要求字段 JSON path（新增） |
| `openjiuwen.service.versatile.interrupt.resume-token-get` | String | — | 否 | 续接关联字段 JSON path（新增） |
| `openjiuwen.service.versatile.interrupt.resume-request-template` | Object | — | 否 | 恢复请求构造模板（新增） |

### 5.3 配置类

`VersatileProperties`（`@ConfigurationProperties(prefix = "openjiuwen.service.versatile")`）扩展 `Intents`、`Messages`、`IntentAgentMapping`（1:N）、`IntentAgentMappingStrategy`、`Interrupt`、`ResultExtraction` 内嵌类承载上述配置。本特性不新增配置类，只扩展既有 `VersatileProperties` 字段。

### 5.4 部署模块（SpringBoot 启动单元 + 多 Profile 配置）

`agent-service-adapters-versatile` 与 `agent-runtime-java` 都是 SDK，本特性在 `agent-solution-zyw/common/example/` 下提供一个可部署的 SpringBoot 启动模块 `versatile-intent-boot`，通过 Spring Profile 加载不同层级的配置文件，作为一层、二层、下游控制器的统一部署单元。该目录下已有 `versatile-a2a-adapter-demo` 模板（依赖 `agent-service-app` + `agent-service-adapters-versatile`，SpringBoot 打包），本特性按此模式新增单一模块。

#### 5.4.1 模块布局

```
agent-solution-zyw/common/example/
├── versatile-a2a-adapter-demo/           # 现有通用 Versatile A2A demo（保留）
└── versatile-intent-boot/                # 新增：意图识别工作流统一部署单元
    ├── src/main/java/
    │   └── .../versatile/intent/
    │       ├── VersatileIntentApplication.java          # 单一启动类
    │       ├── VersatileIntentAutoConfiguration.java    # 自动装配入口
    │       ├── a2a/
    │       │   ├── A2AGatewayRemoteAgentCaller.java     # A2A Gateway 路由实现（消费 responseContent
    │       │   │                                        #   追加 messages + xxxx/{agentCard} 模式，生产形态）
    │       │   ├── A2AGatewayCardResolver.java          # A2A Gateway card URL 解析
    │       │   ├── A2AGatewayProperties.java            # a2a-gateway.* 配置属性
    │       │   ├── A2AGatewayAutoConfiguration.java     # @ConditionalOnProperty(a2a-gateway.enabled=true) 注入
    │       │   ├── ForwardedServeRequests.java          # 共用工具：构造转发 ServeRequest（追加 assistant 消息）
    │       │   ├── LocalHttpRemoteAgentCaller.java      # 本地多端口联调 Caller（消费 responseContent
    │       │   │                                        #   追加 messages, 走 localhost A2A SDK）
    │       │   ├── LocalMappingCardRegistrar.java       # 本地 agentCard → localhost:port 映射注册
    │       │   └── LocalMappingProperties.java          # 本地映射配置属性
    │       └── mock/
    │           ├── MockA2AGatewayController.java        # mock A2A Gateway（/{agentId} 路由 + SSE 回放）
    │           └── MockVersatileController.java         # mock Versatile 工作流（按 intent_id 回放 SSE）
    ├── src/test/java/
    │   └── .../versatile/intent/
    │       ├── ProfileLayer1LoadTest.java               # layer1 profile 上下文加载测试
    │       ├── ProfileLayer2LoadTest.java               # layer2 profile 上下文加载测试
    │       ├── ProfileDownstreamLoadTest.java           # downstream profile 上下文加载测试
    │       └── a2a/
    │           ├── A2AGatewayCardResolverTest.java      # card URL 解析单测
    │           └── A2AGatewayRemoteAgentCallerTest.java # caller messages 追加 + 转发单测
    ├── scripts/
    │   ├── local-e2e.sh                                 # 方案 B 三进程三场景 e2e 联调脚本
    │   └── local-e2e-a2a-gateway.sh                     # A2A Gateway 模式 e2e 联调脚本（含 header 透传断言）
    ├── README.md                                        # 部署模块使用指南
    └── src/main/resources/
        ├── application.yml                              # 公共配置（server.port、logging 等）
        ├── application-layer1.yml                        # 一层 Profile 配置
        ├── application-layer2.yml                        # 二层 Profile 配置
        ├── application-downstream.yml                    # 下游业务工作流 Profile 配置
        ├── application-dev.yml                           # 开发联调公共覆盖（mock-versatile include）
        ├── application-mock-versatile.yml                # mock Versatile 工作流 profile
        ├── application-mock-a2a-gateway.yml              # mock A2A Gateway profile
        └── application-a2a-gateway-test.yml              # A2A Gateway 测试 profile
```

> 单一 jar 三个 Profile，通过 `--spring.profiles.active=layer1|layer2|downstream` 切换部署层级；也可以在同一 host 上以不同端口并行启动三个实例。
>
> **实现偏差说明**：设计阶段曾规划 `InProcessRemoteAgentCaller`（进程内直调，跳过 HTTP），落地实现改为 `LocalHttpRemoteAgentCaller`（走 localhost A2A SDK）以覆盖完整 HTTP/SSE 链路验证（header 透传、SSE 编码、Agent Card 解析）。`InProcessRemoteAgentCaller` 作为未来扩展点保留在设计文档 §5.5.3 方案 A 中，当前未实现。
>
> **runtime 核心 module（`agent-service-app`）改动严格限定为 a2a_delegate 路径扩展 + SPI 改造**：`InterruptData` 新增 `responseContent` 字段 + `RemoteAgentCaller` / `RemoteAgentCardResolver` SPI 接口 + `RemoteAgentCall` 值对象（含 `responseContent`）+ 既有 `A2ARemoteAgentClient`/`A2AAgentCardDiscovery` 签名升级为 SPI 实现（保留类名，忽略 `responseContent`，逻辑等价）+ `A2AEnabledServeOrchestrator.handleA2ADelegate` 消费 `resume=false` + `responseContent` + `agentName` 构造 `RemoteAgentCall` 并通过 `RemoteAgentCaller` SPI 转发。**其他能力由部署模块 `versatile-intent-boot` 承担**：`A2AGatewayRemoteAgentCaller` / `A2AGatewayCardResolver`（A2A Gateway 路由实现，消费 `responseContent` 追加 messages，生产形态）、`InProcessRemoteAgentCaller`（进程内直调，消费 `responseContent` 追加 messages，开发联调形态）、`LocalHttpRemoteAgentCaller`（本地多端口联调，消费 `responseContent` 追加 messages，走 localhost A2A SDK）。runtime 核心 module 不硬依赖这些实现，通过 Spring Bean 注入为对应 SPI 实现按需装配。

#### 5.4.2 模块依赖（pom 要点）

```xml
<dependencies>
    <dependency>
        <groupId>com.openjiuwen</groupId>
        <artifactId>agent-service-app</artifactId>          <!-- 提供 A2A 入口、Task 状态机、orchestrator、RemoteAgentCaller SPI -->
    </dependency>
    <dependency>
        <groupId>com.openjiuwen</groupId>
        <artifactId>agent-service-adapters-versatile</artifactId>  <!-- 提供 VersatileAgentHandler -->
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
</dependencies>
```

#### 5.4.3 启动类

```java
@SpringBootApplication
public class VersatileIntentApplication {
    public static void main(String[] args) {
        SpringApplication.run(VersatileIntentApplication.class, args);
    }
}
```

启动命令：

```bash
# 一层
java -jar versatile-intent-boot.jar --spring.profiles.active=layer1

# 二层
java -jar versatile-intent-boot.jar --spring.profiles.active=layer2

# 下游业务
java -jar versatile-intent-boot.jar --spring.profiles.active=downstream
```

#### 5.4.4 Profile 配置差异

各 Profile 通过 `application-{profile}.yml` 加载对应层级的 `openjiuwen.service.versatile.*` 配置（见 5.1 示例）。差异点：

| 配置项 | application-layer1.yml | application-layer2.yml | application-downstream.yml |
|--------|------------------------|------------------------|----------------------------|
| `server.port` | 8081 | 8082 | 8083 |
| `url-template` | 一层工作流 URL | 二层工作流 URL | 下游业务工作流 URL |
| `intents` | `[{intent_L1_hotel,酒店},...]` | `[{intent_L2_hotel_dom,国内酒店},...]` | 不配置（下游不需要候选列表） |
| `messages` | `source: serve_request_messages` | 同上 | 同上 |
| `intent-agent-mapping` | `intent_L1_* → [agentCard_L2_*]` (1:N) | `intent_L2_* → [agentCard_biz_*]` (1:N) | 不配置（重新分类直接返回 `agent_id`） |
| `intent-agent-mapping-strategy` | `first` / `priority` / `round-robin` | 同上 | 不配置 |
| `result-extractions` | 三字段路径（`agent_id` 可选） | 三字段路径（`agent_id` 可选） | 三字段路径（`agent_id` 必填，重新分类返回） |
| `a2a-gateway.enabled` | `true` | `true` | `true` |

`application.yml` 公共配置：

```yaml
server:
  port: 8080   # 被 profile 覆盖
spring:
  application:
    name: versatile-intent
  profiles:
    active: ${PROFILE:layer1}   # 默认一层，可由环境变量切换
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

#### 5.4.5 部署模块职责边界

- 部署模块只负责 SpringBoot 启动、配置加载、Bean 装配；不包含业务逻辑。
- 部署模块不实现新的 Handler 或 SPI；所有适配逻辑由 `agent-service-adapters-versatile` 提供，转发 SPI 由 `agent-service-app` 提供。
- 一层、二层、下游是同一 jar 不同 Profile，部署形态由 `--spring.profiles.active` 决定；运维可按需独立扩缩容每层实例。
- 若未来需要层级差异化依赖（如一层需要额外组件），可拆分为独立模块；当前阶段共用单一模块以降低维护成本。

### 5.5 开发环境支持（mock 与本地联调）

Versatile 意图工作流与 A2A Gateway 仅生产环境存在，开发环境无法访问。本特性提供本地替身方案，使开发者无需访问生产即可完成 Adapter 解析逻辑、转发 SPI、两层调用链的功能验证。

#### 5.5.1 总体策略

| 依赖 | 生产环境 | 开发环境替身 | 切换方式 |
|------|---------|-------------|---------|
| Versatile 意图工作流 | 客户低码平台部署的 HTTP/SSE 服务 | 本地 mock HTTP/SSE 服务（WireMock stub 或 SpringBoot mock endpoint） | `openjiuwen.service.versatile.url-template` 指向 `localhost` |
| A2A Gateway | 生产 Gateway 域名（`xxxx/{agentCard}` 路由） | (a) 进程内 `InProcessRemoteAgentCaller` 直调下一层 Handler；(b) 多端口本地 runtime 实例 + `A2ARemoteAgentClient` 走 localhost | `openjiuwen.service.a2a-gateway.enabled=false` + 选择 caller 实现 |

开发配置通过 `application-dev.yml` 覆盖 `application-{layer}.yml` 中的 URL 与 Gateway 配置，同一层级部署单元可在 dev/prod 切换。

#### 5.5.2 Versatile 工作流 mock

```yaml
# application-dev.yml
openjiuwen:
  service:
    versatile:
      url-template: http://localhost:9090/mock/versatile/{conversation_id}
      # 其余 intent.* / interrupt.* / result-extractions / intent-agent-mapping 与生产一致
```

mock 服务实现选项（按测试粒度选择）：

| 选项 | 适用场景 | 实现 |
|------|---------|------|
| WireMock stub | 单元测试 / 契约测试 | 在 `src/test/resources/wiremock/mappings/` 下按请求体匹配返回预设 SSE 序列 |
| SpringBoot mock endpoint | 本地手动联调 | 在 `versatile-intent-boot` 模块新增 `@Profile("mock-versatile")` 的 controller，按 `intent_id` 配置返回不同 SSE 响应 |
| `MockVersatileClient` | 纯单元测试（不启动 HTTP） | 实现 `VersatileHttpClient` 测试版，直接返回预设 `Stream<String>` |

mock 场景覆盖（必须）：
- 正常完成（含三字段 `response_content`/`intent_id`/`agent_id`）
- 工作流未返回 `agent_id`（触发 `intent-agent-mapping` 查找）
- 显式用户交互中断（信息完整 → `TYPE_INTERRUPT`）
- 显式中断信息不完整（→ `TYPE_ERROR`）
- 异常断流（连接关闭无 terminal event → `TYPE_ERROR`）
- 远端 HTTP 错误（4xx/5xx）
- 超时

#### 5.5.3 A2A Gateway 本地替身

**方案 A：进程内直连（设计备选，当前未实现）**

> **落地状态**：方案 A 在当前实现中**未落地**。落地实现选择了方案 B（`LocalHttpRemoteAgentCaller` + 多端口本地 runtime 实例），以覆盖完整 HTTP/SSE 链路（header 透传、SSE 编码、Agent Card 解析）。方案 A 作为未来扩展点保留，适用于纯 SPI 验证、跳过 HTTP 序列化的快速单测场景。

新增 `InProcessRemoteAgentCaller` 实现 `RemoteAgentCaller` SPI，不发起 HTTP 调用，从 Spring `ApplicationContext` 中直接查找目标 `AgentHandler` Bean 并调用。该实现放在部署模块 `versatile-intent-boot` 的 test/dev scope（不属于 runtime 核心 module）：

```java
@Component
@Profile("dev-inprocess")
public class InProcessRemoteAgentCaller implements RemoteAgentCaller {
    private final Map<String, AgentHandler> handlersByAgentId;  // 按 agentId 注入

    @Override
    public void call(RemoteAgentCall call, QueryStreamObserver observer) {
        AgentHandler target = handlersByAgentId.get(call.getAgentId());
        if (target == null) {
            observer.onError(new IllegalStateException(
                "VERSATILE_INPROCESS_AGENT_NOT_FOUND: " + call.getAgentId()));
            return;
        }
        target.streamQuery(call.getServeRequest(), observer);
    }

    @Override
    public boolean supported(String agentId) {
        return handlersByAgentId.containsKey(agentId);
    }
}
```

- 在同一进程内加载一层、二层、下游三套 `VersatileProperties` 与 `AgentHandler` Bean（通过 `@Profile` 或 `@Qualifier` 区分），验证转发 SPI 与三字段流转。
- 跳过 HTTP 序列化，不能发现 HTTP 层问题（header 透传、SSE 编码、Agent Card 解析）；联调阶段必须切到方案 B。

**方案 B：多端口本地 runtime 实例（推荐本地联调）**

启动多个 `versatile-intent-boot` 进程，分别激活 `layer1`/`layer2`/`downstream` Profile，监听不同端口（8081/8082/8083）；不启用 A2A Gateway，使用 `A2ARemoteAgentClient` 走 `localhost:808x`：

```yaml
# application-dev.yml (公共开发配置)
openjiuwen:
  service:
    a2a-gateway:
      enabled: false
    # 默认 RemoteAgentCardResolver 通过本地映射把 agentCard 解析为 localhost URL
    card-resolver:
      local-mapping:
        agent_card_layer2_hotel: http://localhost:8082
        agent_card_layer2_flight: http://localhost:8082
        agent_card_biz_hotel_domestic: http://localhost:8083
        agent_card_biz_hotel_international: http://localhost:8083
```

- 适合验证完整两层调用链（含 HTTP 序列化、SSE 流透传、Agent Card 查询）。
- 启动命令：`java -jar versatile-intent-boot.jar --spring.profiles.active=layer1,dev`（同样可启动 layer2、downstream 实例）。

#### 5.5.4 Profile 组合

| Profile 组合 | 用途 | Versatile 来源 | 转发方式 |
|---|---|---|---|
| `layer1,dev` + WireMock | 一层 Adapter 单元测试 | WireMock stub | 不涉及（一层测试不转发） |
| `layer1,dev,mock-versatile` + `layer2,dev,mock-versatile` + `downstream,dev,mock-versatile`（多端口） | 本地 HTTP 联调（**已落地**） | SpringBoot mock endpoint | `LocalHttpRemoteAgentCaller` 走 localhost A2A SDK |
| `layer1,dev,mock-versatile` + `mock-a2a-gateway` | A2A Gateway 模式单进程联调（**已落地**） | SpringBoot mock endpoint | `A2AGatewayRemoteAgentCaller` + `MockA2AGatewayController` |
| `layer1,prod` | 一层生产 | 客户低码平台 Versatile | `A2AGatewayRemoteAgentCaller` |
| `layer2,prod` / `downstream,prod` | 二层/下游生产 | 同上 | 同上 |

> 方案 A（`InProcessRemoteAgentCaller` 进程内直连）未落地；如需纯 SPI 验证可后续补齐。

`application-dev.yml` 模板：

```yaml
spring:
  profiles:
    include: mock-versatile   # 激活本地 mock endpoint

openjiuwen:
  service:
    versatile:
      url-template: http://localhost:9090/mock/versatile/{conversation_id}
    a2a-gateway:
      enabled: false
    card-resolver:
      local-mapping:
        # 按 agentCard → localhost:port 映射
```

#### 5.5.5 测试用例

| 层次 | 测试类 | 覆盖 |
|------|--------|------|
| 单元测试 | `VersatileResponseExtractorTest` | 三字段提取、`intent-agent-mapping` 查找、显式中断分离、异常断流、契约违反错误 |
| 单元测试 | `VersatileRequestExtractorTest` | 三字段组装、`intents` 数组序列化、`messages` 数组序列化、必填校验 |
| 单元测试 | `IntentAgentResolverTest` | `first`/`priority`/`round-robin` 策略、cursor 隔离、空白 agentCard |
| 单元测试 | `A2AGatewayCardResolverTest` | `xxxx/{agentCard}` card URL 与 json-rpc URL 构造 |
| 单元测试 | `A2AGatewayRemoteAgentCallerTest` | messages 追加、`responseContent` 透传、取消传播、错误分类 |
| 集成测试 | `ProfileLayer1LoadTest` / `ProfileLayer2LoadTest` / `ProfileDownstreamLoadTest` | 各 profile Spring 上下文加载、bean 装配校验 |
| 契约测试 | `VersatileSseContractTest` | 录制生产 Versatile SSE 响应样本（脱敏）作为 golden file；生产 Versatile 升级时检测 Adapter 兼容性 |
| 联调脚本 | `scripts/local-e2e.sh` | 方案 B：启动三个本地进程，发送 curl 请求验证全链路（三场景） |
| 联调脚本 | `scripts/local-e2e-a2a-gateway.sh` | A2A Gateway 模式 e2e：业务输出 + 两跳 + 7 项 header 透传 + caller 激活断言 |

#### 5.5.6 边界

- 开发环境 mock 不替代生产联调；上线前必须在 staging 环境对接真实 Versatile 与 A2A Gateway 验证。
- `InProcessRemoteAgentCaller` 跳过 HTTP 序列化，不能发现 HTTP 层问题；联调阶段必须切到方案 B 或 staging。
- mock 服务的 SSE 序列由开发者维护，可能与生产不一致；建议通过契约测试与录制样本减少偏差。
- mock endpoint 不模拟 A2A Gateway 的 `xxxx/{agentCard}` 路由；A2A Gateway 路由逻辑的端到端验证只能在 staging/生产进行。
- 录制生产样本时必须脱敏（移除 `messages` 数组中的 `content`、用户 ID、`response_content` 中的业务数据），遵循 DFX-001 掩码策略。

---

## 6. 对外呈现 / 用户场景（Scenario View）

### 6.1 外部接口

本特性不新增对外端点。所有外部入口以标准 Agent 服务入口、`AgentHandler` SPI 和 FEAT-008 用户交互中断与恢复入口为准。

| 端点 / API | 方法 | 说明 |
|-----------|------|------|
| `POST /a2a` 或等价服务入口 | HTTP | 标准入站入口，调用一层、二层或下游 runtime 实例 |
| `AgentHandler.query(ServeRequest)` | SPI | 非流式执行当前实例配置的意图识别工作流 |
| `AgentHandler.streamQuery(ServeRequest, QueryStreamObserver)` | SPI | 流式执行；支持取消 |
| `QueryChunk(TYPE_INTERRUPT, payload)` | 结果类型 | 工作流显式用户交互中断的标准承载 |
| `QueryResponse.result._interrupt` | 结果字段 | 非流式聚合结果中的中断信息 |
| `QueryResponse.result.{response_content,intent_id,agent_id}` | 结果字段 | 三字段结构化结果，匹配/未匹配/澄清/跳转/重新分类共用 |

### 6.2 用户示例

#### 6.2.1 场景一：两层识别 + 下游业务

```bash
# 1. 一层
curl -s -X POST http://runtime-a:8080/query \
  -H "Content-Type: application/json" \
  -d '{"conversation_id":"c1","user_id":"u1","tenant_id":"t1",
       "messages":[{"role":"user","content":"我要订酒店"}]}'
# 预期：QueryResponse.result 含 response_content / intent_id / agent_id(指向二层)

# 2. A2A Gateway 按 agent_id 转发到二层：query 不变（用户本轮输入），
#    messages 末尾追加一层 response_content 作为 assistant 消息
# 二层 Adapter 收到的 ServeRequest.messages:
#   [{"role":"user","content":"我要订酒店"},
#    {"role":"assistant","content":"<一层 response_content>"}]
# 二层工作流收到的 messages 数组 = 上述会话历史 + 一层输出
curl -s -X POST http://runtime-b:8080/query \
  -d '{"conversation_id":"c2","user_id":"u1","tenant_id":"t1",
       "messages":[{"role":"user","content":"我要订酒店"},
                   {"role":"assistant","content":"<一层 response_content>"}]}'
# 预期：二层 result 含 agent_id(指向下游业务)

# 3. A2A Gateway 按 agent_id 转发到下游：messages 追加二层 response_content
curl -s -X POST http://runtime-c:8080/query \
  -d '{"conversation_id":"c3","user_id":"u1","tenant_id":"t1",
       "messages":[{"role":"user","content":"我要订酒店"},
                   {"role":"assistant","content":"<一层 response_content>"},
                   {"role":"assistant","content":"<二层 response_content>"}]}'
# 预期：下游 result 含业务输出
```

#### 6.2.2 场景二：分类错误重新分类

```bash
# 下游业务工作流判断分类错误，返回三字段：
#   intent_id = 一层低码意图工作流 ID
#   agent_id  = 固定一层意图 Agent 的逻辑 agentId
#   response_content = 重分类上下文（有效请求 + 错误目标 + 原因 + 已执行路径 + 会话信息）

# runtime 下游调用能力按 agent_id 调用固定一层 Agent（创建新 Task，不恢复原一层 Task）
curl -s -X POST http://runtime-a:8080/query \
  -d '{"conversation_id":"c4","messages":[{"role":"user","content":"<biz.response_content>"}]}'
# 预期：一层 Adapter 把 response_content 映射为 query，重新执行一层工作流
```

#### 6.2.3 场景三：工作流显式用户交互

```bash
# 第一轮：工作流请求补充信息
curl -s -X POST http://runtime-a:8080/query -d '{...}'
# 预期：QueryResponse.result._interrupt = {message, input_requirement, resume_token}

# 第二轮：客户端通过同一 Task 续接恢复原工作流（续接入口与 payload 载体由 FEAT-008 定义，本特性不新增 resume input type）
curl -s -X POST http://runtime-a:8080/query \
  -d '{"conversation_id":"c1",
       "messages":[{"role":"user","content":"上海 今晚 五星"}],
       "metadata":{"body":{"resume_token":"<token>"}}}'
# 预期：COMPLETED（或再次 INTERRUPT，或 ERROR）
```

### 6.3 E2E 流程

```
调用方       runtime A (一层)   Versatile 一层   A2A Gateway   runtime B (二层)   Versatile 二层   runtime C (下游)
  │                │                  │                │              │                  │                │
  │── 业务输入 ───>│                  │                │              │                  │                │
  │                │── inputs{3字段} ─>                │              │                  │                │
  │                │  query=用户输入   │                │              │                  │                │
  │                │  intents=[...]    │                │              │                  │                │
  │                │  messages=会话历史 │                │              │                  │                │
  │                │<── 三字段 ───────│                │              │                  │                │
  │<─ result ──────│                  │                │              │                  │                │
  │                │── agent_id + ─────────────────────>│              │                  │                │
  │                │   response_content                │              │                  │                │
  │                │                  │                │── 转发 ─────>│                  │                │
  │                │                  │                │   ServeRequest: │                  │                │
  │                │                  │                │   query=用户输入(不变)              │                │
  │                │                  │                │   messages += assistant(response_content)        │
  │                │                  │                │              │── inputs{3字段} ─>                │
  │                │                  │                │              │  query=用户输入    │                │
  │                │                  │                │              │  messages=会话历史+一层输出        │
  │                │                  │                │              │<── 三字段 ───────│                │
  │                │                  │                │── agent_id + ────────────────────>│                │
  │                │                  │                │   response_content                │                │
  │                │                  │                │── 转发 ──────────────────────>│── 调用工作流 ──>│
  │                │                  │                │              │                  │  messages 追加  │
  │                │                  │                │              │                  │  二层输出        │
  │<─ 直接用户消息（由 FEAT-012/013 投影） ─────────────────────────────────────────────────────────────────│
```

重新分类 E2E：

```
调用方       runtime (下游业务)   Versatile 下游     A2A Gateway   runtime (固定一层)   Versatile 一层
  │                │                   │                    │              │                     │
  │── 业务输入 ───>│                   │                    │              │                     │
  │                │── 调用工作流 ────>│                    │              │                     │
  │                │<── 三字段(指向一层) │                    │              │                     │
  │                │── agent_id + ───────────────────────────>│              │                     │
  │                │   response_content(重分类上下文)          │              │                     │
  │                │                   │                    │── 调用方显式构造新 ServeRequest ─>│     │
  │                │                   │                    │   messages.last(user) = response_content │
  │                │                   │                    │   messages += assistant(response_content) │
  │                │                   │                    │              │── query=lastUserQuery()=response_content
  │                │                   │                    │              │── inputs{3字段} ───>│
  │                │                   │                    │              │<── 新分类结果 ───────│
  │<─ 最终业务结果（通过新调用链） ──────────────────────────────────────────│                     │
```

中断恢复 E2E：

```
调用方              runtime               Versatile 工作流
  │                    │                         │
  │── 第一轮请求 ─────>│                         │
  │                    │── inputs{3 字段} ──────>│
  │                    │   query/intents/messages │
  │                    │<──── 原生中断事件 ───────│
  │                    │── 解析 prompt/input-req/resume-token
  │                    │── TYPE_INTERRUPT + _interrupt
  │<── result._interrupt ─│                       │
  │                    │                         │
  │── 同 Task 续接（FEAT-008） ─>│                │
  │   (user_response + resume_token)              │
  │                    │── resume 请求 (续接关联) ─>│
  │                    │<──── 完成/再次中断 ───────│
  │<── QueryResponse / TYPE_INTERRUPT / TYPE_ERROR ─│
```

---

## 7. 错误处理（Process View）

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---------|---------|------|---------|
| 意图配置缺失 | `intents` 列表缺失/空数组/元素 `id` 或 `name` 为空 | `VersatileRequestExtractor.extract()` 抛 `IllegalArgumentException` | `VersatileAgentHandler` 捕获 → `TYPE_ERROR("VERSATILE_INTENT_CONFIG_MISSING")`，指出配置读取阶段 |
| 必填消息字段缺失 | `messages.required=true` 且 `ServeRequest.messages` 为空或缺少 `role`/`content` 字段 | 同上 | `TYPE_ERROR("VERSATILE_INTENT_INPUT_MISSING")`，指出输入组装阶段 |
| 远端 HTTP 超时 | 超过 `timeout` | `VersatileHttpClient` 抛超时异常 | `TYPE_ERROR("VERSATILE_TIMEOUT")` 或 `IllegalStateException` |
| 远端 HTTP 4xx/5xx | 远端返回错误状态 | 当前代码上抛 `IllegalStateException` | `TYPE_ERROR("VERSATILE_HTTP_{code}")` |
| SSE 解析失败 | 某行 JSON 不合法 | 当前代码跳过该行 | 该行丢弃，不映射为失败 |
| 异常断流（无 terminal event） | 连接关闭且无 End/exception | **本特性改为**：`finish()` 产出 `TYPE_ERROR("stream_closed_without_terminal")` | `TYPE_ERROR`，不构造 `TYPE_INTERRUPT` |
| 结果字段缺失 | `response_content`/`intent_id`/`agent_id` 任一缺失 | `finish()` 产出 `TYPE_ERROR` | `TYPE_ERROR("VERSATILE_INTENT_RESULT_CONTRACT")`，不输出部分结果 |
| 结果类型错误 | 任一字段非 string | 同上 | `TYPE_ERROR("VERSATILE_INTENT_RESULT_TYPE")` |
| `agent_id` 非唯一 | `agent_id` 为数组、多值或无法确定唯一值 | 同上 | `TYPE_ERROR("VERSATILE_INTENT_AGENT_ID_NOT_UNIQUE")` |
| `intent_id` 无映射且工作流未返回 `agent_id` | `intent-agent-mapping` 中无此 `intent_id` 条目或候选列表为空，且工作流未返回 `agent_id` | 产出 `TYPE_ERROR` | `TYPE_ERROR("VERSATILE_INTENT_AGENT_ID_UNMAPPED")`，指出缺失的 `intent_id` |
| `intent-agent-mapping-strategy` 未识别 | 配置的策略值不在 `first`/`priority`/`round-robin` 范围内 | 启动期或首次调用时产出 `TYPE_ERROR` | `TYPE_ERROR("VERSATILE_INTENT_MAPPING_STRATEGY_INVALID")` |
| 原生中断信息不完整 | 工作流请求用户交互但 prompt/input-requirement/resume-token 缺失 | 产出 `TYPE_ERROR` | `TYPE_ERROR("VERSATILE_INTENT_INTERRUPT_INCOMPLETE")`，不构造 `TYPE_INTERRUPT` |
| 恢复上下文不可用 | 客户端通过同一 Task 续接时 `resume_token` 失效或缺失 | 产出 `TYPE_ERROR` | `TYPE_ERROR("VERSATILE_INTENT_RESUME_CONTEXT_INVALID")`，保留恢复阶段标识 |
| 远端恢复调用失败 | 恢复请求触发远端错误或超时 | 同上 | `TYPE_ERROR("VERSATILE_INTENT_RESUME_UPSTREAM_{code}")`，保留恢复阶段标识 |
| 取消 | `observer.isCancelled()` 返回 true | 抛 `CancellationException` | `streamQuery` 静默返回；Task 终态由 runtime 推进 |
| Handler 执行异常 | `execute()` 抛 RuntimeException | `streamQuery` 调用 `observer.onError(exception)` | `TYPE_ERROR` |

> 重新分类循环保护失败（超过次数、同一错误业务目标重复、相同错误路径循环、deadline 到期）由 runtime 下游调用能力检测并返回结构化失败，不进入 Adapter 错误处理表。

---

## 8. 限制与待补

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| 单 runtime 实例只适配一个意图工作流 | 无法在单个实例内编排一层、二层和下游 | 由 runtime 下游调用能力串行调用；本 Adapter 不参与编排 |
| FEAT-008 resume 入口由 runtime 处理，Adapter 不感知 | Adapter 不区分新调用与 resume；runtime 负责把 resume 上下文转换为标准 `ServeRequest` | Adapter 侧已就绪；若工作流恢复端点需要特殊请求体，通过 `interrupt.resume-request-template` 配置外置 |
| Runtime 转发能力 a2a_delegate 路径扩展 + SPI 化（依赖项，不在 Adapter 内） | runtime 编排组件当前转发硬编码 `/.well-known/agent-card.json` + `jsonRpcPath`，无 `{agentCard}` 占位符，且不追加上一层 `response_content` 到 `messages` | 本特性 4.9 节描述 runtime 编排层的 a2a_delegate 路径扩展 + SPI 化改造，支持 `xxxx/{agentCard}` 路由并在转发前把上一层 `response_content` 作为 assistant 消息追加到 `messages` 末尾；Versatile Adapter 产出 `a2a_delegate` interrupt（`resume=false`），不参与转发，不依赖 `RemoteAgentCaller` SPI |
| 重新分类场景 `messages.last` 替换未实现 | `ForwardedServeRequests.build()` 当前仅追加 assistant 消息，未把 `messages` 最后一条 user 消息 content 替换为 `responseContent`（重分类上下文）；Caller 收到的 `RemoteAgentCall` 在"正常跨层转发"与"重新分类"两种场景下字段完全相同，无法区分 | 由 follow-up issue 跟踪（跨仓库：agent-runtime-java + spring-ai-ascend）；完整修复需要 orchestrator 在重新分类时传递场景信号（如设置 `call.message()=responseContent`）或由 orchestrator 直接构造替换后的 `ServeRequest`，并同步更新本设计文档 §4.9.3 契约；当前重新分类场景依赖 PRD TBD-13/14 客户确认，非阻塞 |
| 下游直接用户消息投影链路（FEAT-012/013/014）契约待确认 | Adapter 产出的 `TYPE_CHUNK` 如何投影到原客户端、用户响应如何直达 Task owner 未定 | 等 PRD TBD-07/08/11 落地；Adapter 侧已具备 `TYPE_CHUNK` 输出 |
| 跳转/重新分类事件契约待确认 | 三字段结果通过现有 A2A/Bus 终态事件 payload 还是新增专用事件类型未定（PRD TBD-09） | Adapter 侧三字段已写入 `QueryResponse.result`；事件载体由 runtime 下游调用能力与 Agent Bus 决定 |
| 当前代码"无 terminal event 即关闭"映射为 `TYPE_INTERRUPT` | 与本特性"异常断流不得伪造中断"冲突，属破坏性变更 | 升级时确认现有部署是否依赖此行为；按 4.4.3 改造 |
| 不查询 `agent_id` 注册中心 | Adapter 不验证目标存在性、可访问性、可调用性 | 由 FEAT-015/016 处理 |
| 不执行业务匹配/兜底/候选选择/分类错误判断 | 未匹配、澄清、跳转、重新分类须由客户工作流或调用方处理 | 客户工作流返回唯一 `agent_id` |
| 原生协议路径不在 Adapter 内硬编码 | 部署必须显式声明 `result-extractions` 与 `interrupt.*` 路径 | 提供 YAML 模板与开发者指南 |
| 敏感字段掩码具体规则依赖 DFX-001 | 本特性不单独定义掩码算法 | 遵循平台 DFX-001 策略；当前 DEBUG 打印 messages/metadata 的行为需降级 |
| 三字段结果承载在 `QueryResponse.result` Map | 调用方读取路径需对齐本设计 | 调用方通过 `result.get("response_content")` 等键读取，不从 `content` 解析 |

---

## 9. 待确认事项（继承自 PRD）

本 L2 落地依赖以下 PRD TBD 项的客户确认或下游 L2 落地。Adapter 侧在 TBD 未确认前按"配置外置 + 字符串透传 + 默认 source=session_history"策略先行实现，待确认后调整默认值与校验规则。

| TBD | 影响 Adapter 的部分 | 当前暂定处理 |
|-----|---------------------|-------------|
| TBD-01 | `messages` 数组真实来源（会话历史范围、授权策略、是否包含跨 Runtime 历史） | 默认 `source=serve_request_messages`，从 `ServeRequest.messages` 会话历史取 `role`/`content`；待客户确认会话历史范围与授权规则 |
| TBD-02 | `messages` 数组元素 `role`/`content` 内部格式 | Adapter 按 `{role, content}` 透传，序列化为 JSON 数组；只校验非空 |
| TBD-03 | `intents` 数组元素 `id`/`name` 内部格式 | Adapter 按 List<{id,name}> 透传，序列化为 JSON 数组；只校验非空 |
| TBD-04 | Versatile 一层/二层正常结果 JSON/SSE 结构与字段提取路径 | 通过 `result-extractions` 配置外置；待客户提供样例后提供默认配置模板 |
| TBD-05 | Versatile 用户交互中断的 prompt/input-requirement/conversation_id/continuation 信息 | 通过 `interrupt.*` 配置外置；连接关闭无 terminal 改为 `TYPE_ERROR` |
| TBD-06 | Versatile 工作流执行失败后是否调用客户失败处理工作流 | 当前返回结构化 `TYPE_ERROR`，不自动调用失败处理工作流 |
| TBD-09 | 跳转/重新分类使用现有终态事件 payload 还是新增事件类型 | Adapter 侧三字段写入 `QueryResponse.result`；事件载体由 runtime 下游调用能力决定 |
| TBD-13 | 最终业务工作流能否稳定返回一层 `intent_id`/`agent_id`/`response_content` | 作为重新分类场景的客户接入前置条件；Adapter 侧三字段提取已就绪 |
| TBD-14 | 重新分类 `response_content` 的准确格式与一层工作流解析规则 | Adapter 按字符串透传；格式由客户确认 |
| TBD-15 | `intent-agent-mapping` 1:N 选择策略的业务合理性边界 | 默认 `first` 策略；`priority`/`round-robin` 由部署按需启用；策略不读取会话内容做业务判断 |
