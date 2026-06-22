---
artifact_type: delivery_projection
version: agent-bus-stage14-review-and-stage15-plan
status: stage-15-completed
source_commit: 7b5fe961
source_stage14_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage13-review-and-stage14-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_transport_candidates: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_l2: architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md
target_module: agent-bus
---

# agent-bus Stage 14 评审与 Stage 15 计划（真实投递绑定 PoC：A2A transport adapter）

## 0. 结论

提交 `7b5fe961` 可以作为 Stage 14 的阶段性成果接受：deliver 异常重投策略从 `ForwardingDeliveryPort.deliver`（交付动作）中分离，落到独立的 `ForwardingRetryPolicy` 端口（重试治理）—— overflow-safe 指数退避 + 注入 jitter + `exhausted`→DLQ + worker RETRY 分支接入，`ForwardingDeliveryResult` 移除 `nextAttemptAtMillisEpoch` / `retry(code)` 简化为单参。**158 tests green**，ArchUnit 纯度 green，独立于 Stage 13 未裁决的 transport 模型，`§6.2` 不变。

但 Stage 14 把「重投时机治理」做完整后，C3 forwarding 仍有一个从 Stage 8 起一直 deferred 的硬缺口：**`ForwardingDeliveryPort.deliver` 的真实 transport 实现从未落地** —— 唯一实现是 test-scope 的 `InMemoryForwardingDelivery` fake。C3 能 claim / lease / dispatch-loop / retry-policy，但 deliver 不知道发去哪。持久化层是闭环的，投递半边一直是断的（[`stage12-review-and-stage13-plan §1 MI13-T`](agent-bus-stage12-review-and-stage13-plan.md)）。

Stage 15 解除这个 deferred：用户提出「确认 experimental 分支的 C3 转发能否与同事在 main 分支开发的 runtime 代码对接，避免自己的道路越走越远」。Stage 15 = **真实投递绑定 PoC**：用 A2A HTTP transport adapter 让 `deliver` 真正通过 A2A JSON-RPC 协议调用一个模拟的 agent-runtime `/a2a` 端点，证明「agent-bus 能消费 agent-runtime」的技术可行性。

**已落地**：`A2aForwardingDeliveryPort implements ForwardingDeliveryPort`（A2A SDK `JSONRPCTransport`，圈进 `transport.a2a` 子包）+ `ForwardingEndpointResolver` 注入端口（解开 routeHandle opaque，HD4 不破）+ MockWebServer 契约验证（5 场景，线上格式对称性）。**164 tests green**（Stage 14 的 158 + 5），ArchUnit 纯度 green，`§6.1` 第 4 项「真实投递绑定 deferred」解除、`§6.2` 始终不得项不变。

**Stage 15 不裁决** Stage 13 的 push / pull / MQ 哲学张力（仍 H2/H3）—— PoC 选了 T1（同步等完成，镜像 main 的 `A2aRemoteAgentOutboundAdapter`）作为「对接可行性」的最短路径，但这不是对投递模型的最终裁决。

## 1. Stage 14 评审回顾（commit `7b5fe961`）

Stage 14 是 Stage 13 拆出的「可独立于投递模型先行」子项（[`stage13-review-and-stage14-plan`](agent-bus-stage13-review-and-stage14-plan.md)）。落地点：

- **`ForwardingRetryPolicy` 端口**（`forwarding/runtime`，与 `DispatchLeasePolicy` / `EpochClock` 同层注入范式）：`nextAttemptAt(code, attemptCount, now)` + `exhausted(attemptCount)` + `DEFAULT = ExponentialBackoff(100ms base, 60s cap, maxAttempts=5)`。
- **overflow-safe 指数退避**：`delay = min(cap, base << shift)`，`shift = min(attemptCount, 62 − highestSetBit(base))`。关键修复——固定 shift 上限 62 对多 bit base 是错的（`100 << 62` 把 100 的所有位移出得 `0`，非负逃过符号检查，被 `min(0, cap)=0`）；动态上限 `62 − (63 − Long.numberOfLeadingZeros(baseMillis))` 保证 `base << shift` 恒正。
- **worker RETRY 接入**：`exhausted(attemptCount)` → DLQ，否则 `nextAttemptAt` → `scheduleRetry`；`ForwardingDeliveryResult.retry(code)` 简化为单参（移除 `nextAttemptAtMillisEpoch`，重投时机归 policy，交付与重试治理分离关注点；outbox `nextAttemptAt` 仍是 persisted 字段）。
- **`ForwardingCircuitBreaker` 端口 + `ALWAYS_CLOSED` no-op deferred**（形态依赖 transport 模型：push 需主动短路、consumer-pull 天然自调速）。

Stage 14 DoD：158 tests green（153 + 5），ArchUnit 纯度 green，`§6.2` 不变，独立于 transport 裁决。**接受**。

> Stage 14 之后剩余的 deferred 中，**deliver 真实 transport 绑定**是最关键的一项（它直接决定「C3 转发是否真能投递出去」）—— 这正是 Stage 15 的对象。

## 2. Stage 15 范围与设计

### 2.1 为什么（对接可行性 PoC）

调研结论（确认 experimental 的 C3 转发与 main 的 runtime 可对接）：

- **agent-runtime 模块两条路径已对齐**：同名 `com.huawei.ascend.runtime` A2A 运行时，非冲突。
- **对接点确认**：main 的 agent-runtime 暴露 `A2aJsonRpcController` = `@PostMapping("/a2a")`（JSON-RPC over HTTP，阻塞 `application/json` + 流式 `text/event-stream`）+ `AgentCardController`（`/.well-known/agent-card.json`）。
- **可照搬模板**：main 的 `A2aRemoteAgentOutboundAdapter` 已是完整 A2A 出站客户端（`JSONRPCTransport` + per-endpoint 缓存 + streaming 阻塞等终态 + 超时映射）。
- **哲学张力**：同事用的是 T1（同步 push RPC）；experimental 的 C3 是持久化异步转发。Stage 15 PoC 不裁决这个张力，只证明**对接在技术上可行**。

### 2.2 两个范围决策（用户已拍板）

1. **MockWebServer 契约验证**（不拉起真实 agent-runtime，避免被其构建坑阻塞；与 Stage 12 用 embedded-postgres 验证真实 SQL 的范式一致）。
2. **同步等完成语义**（照搬 adapter 的 streaming 模式，= Stage 13 的 T1；deliver 等 Task COMPLETED 才 ACKED）。

### 2.3 生产代码落点：`transport.a2a` 子包

照 Stage 12 把 Spring/JDBC 圈进 `persistence.jdbc` 的范式，把 A2A SDK 圈进 `transport.a2a` 子包；forwarding core（ports / 状态机 / worker / loop）保持 transport-agnostic。ArchUnit `forwarding_core_does_not_import_a2a_outside_transport_adapter` 强制；`§6.2` 文本扫描（`readForwardingProductionSources`）同步排除 `runtime/transport/a2a` 子树。

### 2.4 `ForwardingEndpointResolver`（解开 routeHandle opaque，HD4 不破）

`ForwardingRouteHandle(value, tenantScope)` 完全 opaque，**HD4 硬约束：deliver 不得自行 unwrap 到物理 endpoint**。引入注入端口（与 `ForwardingRetryPolicy` / `DispatchLeasePolicy` / `EpochClock` 同层范式）：

```java
public interface ForwardingEndpointResolver {
    Optional<String> resolve(ForwardingRouteHandle handle);  // 空 = 路由解析失败 → dlq(ROUTE_NOT_FOUND)
}
```

deliver 调 resolver 拿 URL，不自己 unwrap，符合 HD4。默认 `MapEndpointResolver`（测试里指向 MockWebServer URL）；**生产实现由 Stage 3 registry 提供，deferred**。

### 2.5 `A2aForwardingDeliveryPort` + 终态映射表

照搬 main 的 `A2aRemoteAgentOutboundAdapter`：per-endpoint `JSONRPCTransport` 缓存（`ConcurrentHashMap` / `computeIfAbsent`）+ `sendMessageStreaming` + `CountDownLatch.await(streamTimeoutMillis)` 阻塞等终态。终态映射（`terminalStateOf` 提取 `Task` / `TaskStatusUpdateEvent` → `TaskState`，`isFinal() || isInterrupted()` 镜像 `A2aJsonRpcController#isStreamTerminating`）：

| A2A 远程 Task 状态 | 映射 | 理由 |
|---|---|---|
| COMPLETED / INPUT_REQUIRED | `acked()` | 投递成功 + 远程到达（INPUT_REQUIRED 的精确半完成态语义 deferred） |
| FAILED / CANCELED / REJECTED / AUTH_REQUIRED | `retry(RECEIVER_UNAVAILABLE)` ⚠️ PoC 取舍 | 保守重试；理想 `REMOTE_TASK_FAILED` non-retryable 码 deferred（避免 Stage 9 classification / DDL / ICD 连锁） |
| 流内 `errorConsumer` 触发（无前置终态） | `retry(RECEIVER_UNAVAILABLE)` | 连接级失败 |
| `await` 超时（未到终态） | `retry(DELIVERY_TIMEOUT)` | 可重试 |
| `sendMessageStreaming` 同步抛 `RuntimeException` | `retry(RECEIVER_UNAVAILABLE)` | MI11-002 契约：`deliver` 不抛非 lease 异常 |
| resolver 返回空 endpoint | `dlq(ROUTE_NOT_FOUND)` | 路由无效（HD4） |

**现有 7 个 `ForwardingFailureCode` 覆盖 PoC 全部场景，不新增码**（远程 FAILED 的精确分类 deferred）。

### 2.6 tenant + payload 语义（§6.2 守恒）

- **tenant（R-C.c 跨租户连续性）**：`ClientCallContext` 携带 `X-Tenant-Id` header（`Map.of(properties.tenantHeaderName(), record.tenantId())`），对齐 `A2aJsonRpcController @RequestHeader(name="X-Tenant-Id")`。
- **payload（§6.2 不破）**：`payloadRef` 走 `MessageSendParams.metadata`（A2A 扩展位）；`TextPart` 仅载 routing descriptor（`"agent-bus forwarded message " + messageId`），**绝不放 payload body / token stream**。

### 2.7 MockWebServer 契约验证（test，5 场景）

`A2aForwardingDeliveryPortMockWebServerTest`：

1. **COMPLETED → ACKED**（happy path）+ 断言 `X-Tenant-Id` header + body 含 `SendStreamingMessage`。
2. **流超时 → DELIVERY_TIMEOUT**（`setBodyDelay(2s)` + `streamTimeoutMillis=200`）。
3. **远程 FAILED → RECEIVER_UNAVAILABLE**。
4. **连接错误 → RECEIVER_UNAVAILABLE**（`SocketPolicy.DISCONNECT_AFTER_REQUEST`）。
5. **resolver 空 → DLQ/ROUTE_NOT_FOUND**（无网络调用）。

### 2.8 ArchUnit（子包豁免）

`forwarding_core_does_not_import_a2a_outside_transport_adapter`：`org.a2aproject` 仅允许在 `transport.a2a` 子包内依赖。全局规则（netty / jackson / servlet / kafka / nats / hikari / reactor）实测不退化（A2A SDK 公共 API 不暴露这些）。

## 3. 关键发现（PoC 过程中）

1. **线上格式对称性**（核心 PoC 价值）：harness 用 A2A SDK 自身序列化器 `JsonUtil.toJson(new SendStreamingMessageResponse(id, event))` 产出 SSE `event:jsonrpc\ndata:<json>` 帧 —— 与 agent-runtime `A2aJsonRpcController` 的 `ServerSentEvent.event("jsonrpc").data(streamingResponseJson(id, evt))` **逐字节一致**。即真实 `JSONRPCTransport` 客户端解析的字节 == 真实 agent-runtime 响应。这把 PoC 从「测一个 fake」提升为「测真实协议代码」（发送方序列化 == 接收方反序列化的对称性）；同 Stage 12 embedded-postgres「用测试载体验证真实协议代码」哲学，非 agent-runtime 自测用的 `RecordingTransport` fake（`RecordingTransport` 伪造 `ClientTransport` seam，不测线上格式）。
2. **SDK 行为发现**：HTTP 4xx / 5xx（如 503）**不被当错误** —— A2A SDK 把非 2xx 视为静默空 SSE 流（无事件、连接正常关闭、`errorConsumer` 从不触发）→ deliver 阻塞到 `DELIVERY_TIMEOUT`。真正的 socket 断开才触发 `onFailure → errorConsumer → RECEIVER_UNAVAILABLE`。故「连接错误」场景用 `SocketPolicy.DISCONNECT_AFTER_REQUEST`（真 socket 断开）而非 HTTP 503。此行为已记录在 harness 注释，供生产化时参考（反压场景下 SDK 不区分 429/503，并入 `RECEIVER_UNAVAILABLE`）。
3. **`§6.2` 文本扫描豁免**：`AgentBusForwardingRuntimeContractTest` 的 `forwarding_production_sources_carry_no_payload_body_nor_task_state_nor_broker` 扫描转发 .java 时发现 A2aForwardingDeliveryPort 含 `TaskStatus`（解析远程 A2A 响应所需）。解法：`readForwardingProductionSources()` walk 排除 `transport/a2a` 子树（镜像 ArchUnit + Stage 12 JDBC 子包豁免哲学）。合理：`transport.a2a` 是线上格式解析器（读远程 Task 事件映射为 `ForwardingDeliveryResult`，**从不把 Task execution state 写进 record** —— record 无 `TaskStatus` 字段），转发核心的 §6.2 精神不变。

## 4. 切片 + MI 表（执行结果）

| MI | 切片 | 产出 | 状态 |
|---|---|---|---|
| MI15-001 | 0 范围确认 + 治理 | `§6.1` 第 4 项解除到「A2A HTTP transport adapter + MockWebServer 契约验证（scaffold 层级）」；`§6.2` 不变；映射表定稿；deferred 标注（registry 集成 / 连接池 / 熔断接入 / 真实 agent-runtime 端到端 / H2/H3 push-pull-MQ 裁决 / REMOTE_TASK_FAILED 码） | ✓ decision §4/§6.1/§8 + ICD + yaml 同步 |
| MI15-002 | 1 resolver 端口 | `ForwardingEndpointResolver` 端口 + `MapEndpointResolver` 默认实现。routeHandle 保持 opaque，HD4 不破。纯 Java | ✓ |
| MI15-003 | 2 A2A deliver 实现 | `A2aForwardingDeliveryPort`（`JSONRPCTransport` 缓存 + `sendMessageStreaming` 阻塞 + 终态映射表）；`A2aForwardingProperties`（`streamTimeoutMillis` + `tenantHeaderName`）。圈 `transport.a2a` 子包 | ✓ |
| MI15-004 | 2 tenant + payload | `ClientCallContext` 携带 `X-Tenant-Id` header；`payloadRef` → `MessageSendParams.metadata`，`TextPart` 不放 payload body | ✓ |
| MI15-005 | 3 MockWebServer harness | 5 场景测试（COMPLETED / 超时 / FAILED / 连接错误 / resolver 空）。pom 加 `mockwebserver`（test） | ✓ |
| MI15-006 | 4 ArchUnit + 依赖 | pom 加 a2a client 依赖（`client-transport-jsonrpc` + `http-client`，version `${a2a-sdk.version}`=CR1）；新增 `_a2a_outside_transport_adapter` 规则；全局规则实测不退化 | ✓ |
| — | 5 文档同步 | decision §4/§6.1/§8 + ICD + yaml + L1 README/physical + L2 `forwarding-persistence §16` + 本双语文档 | ✓ |
| — | 6 构建验证 + 提交 | `mvn -f .../agent-bus/pom.xml test`；164 tests green；ArchUnit green；commit + push（experimental） | 见 slice 6 |

## 5. deferred（明示边界）

- **不裁决 T1 vs C3 异步的哲学张力**：PoC 选 T1（同步等完成）证明对接可行，不裁决 Stage 13 的 push / pull / MQ 最终模型（仍 H2/H3）。
- **不拉起真实 agent-runtime 端到端**：受 agent-runtime 构建坑（Spring Boot 启动配置未做）阻塞，deferred。MockWebServer 已覆盖协议契约。
- **`REMOTE_TASK_FAILED` non-retryable 码未加**：远程 task 失败的精确分类 deferred，PoC 保守重试。
- **生产化项 deferred**：连接池治理、registry 集成（resolver 生产实现）、`ForwardingCircuitBreaker` 接入 worker、真实 scheduler / polling —— 均 H2/H3 后。
- **§6.2 守恒验证**：grep 确认无 payload body / token stream / concrete broker client（Kafka / RabbitMQ / NATS）进入 envelope 或 `transport.a2a`；A2A 是 HTTP JSON-RPC，非 broker / MQ。

## 相关文档

- Stage 13 transport 投递模型候选评审：[`agent-bus-forwarding-runtime-transport-candidates`](../review-packets/agent-bus-forwarding-runtime-transport-candidates.md)（T1-T4 × 8 维度，T3 consumer-pull over DB 非裁决推荐）。
- C3 裁决：[`agent-bus-forwarding-runtime-decision`](../review-packets/agent-bus-forwarding-runtime-decision.md)（`adopted-c3`；§4 Stage 15 许可段、§6.1 第 4 项解除、§8 Stage 15 条）。
- runtime 契约：[`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)（Stage 15 边界条 + Open Issues）。
- 持久化 L2：[`forwarding-persistence §16`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)（Stage 15 决策：A2A transport adapter）。
- Stage 14 计划：[`stage13-review-and-stage14-plan`](agent-bus-stage13-review-and-stage14-plan.md)（retry policy 先行）。
