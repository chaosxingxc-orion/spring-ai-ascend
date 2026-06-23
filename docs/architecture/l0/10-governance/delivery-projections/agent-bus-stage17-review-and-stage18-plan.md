---
artifact_type: delivery_projection
version: agent-bus-stage17-review-and-stage18-plan
status: stage-17-completed
source_commit: 4994e7d3
stage18_planned: 2026-06-23
source_stage17_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage16-review-and-stage17-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_l2: architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md
target_module: agent-bus (+ cross-module agent-runtime, test-only, 沿用 Stage 17)
---

# agent-bus Stage 17 评审与 Stage 18 计划（失败路径端到端验证 + `REMOTE_TASK_FAILED` non-retryable 码收口）

## 0. 结论

提交 `4994e7d3` 可以作为 Stage 17 的阶段性成果接受：agent-bus 项目自 Stage 1 以来的**首个跨模块集成里程碑** —— `C3ForwardingEndToEndIntegrationTest` 用**真实的 `LocalA2aRuntimeHost`**（Spring Boot A2A server）替换 Stage 15 的 MockWebServer，端到端驱动 `outbox enqueue → dispatch tick → A2aForwardingDeliveryPort → 真实 /a2a JSON-RPC+SSE → COMPLETED → worker ACKED` 全链；agent-bus 引入首个 `agent-runtime` **test-scope** 依赖（生产仍零依赖，`AgentBusDependencyBoundaryTest.bus_does_not_depend_on_agent_runtime` 守卫）。**182 tests green**，ArchUnit green，§6.2 不变。**接受**。

Stage 17 的核心盲区也很清晰：**只有 happy path**。`StubHandler` 恒 `COMPLETED`，Stage 7-16 落地的全部失败处理（ACK/RETRY/DLQ/EXPIRED + lease-guarded mutation + retry policy + circuit breaker）**都是 fake delivery 的 unit/contract test 覆盖，从未在真实端到端链路验证过**；而 `REMOTE_TASK_FAILED` non-retryable 码从 Stage 14→15→17 **三次 deferred**，`ForwardingFailureCode` enum 至今无此码，`FAILED/CANCELED/REJECTED` 终态保守映射 `retry(RECEIVER_UNAVAILABLE)`。

Stage 18 = 用户在 Stage 17 收尾后选定的**失败路径端到端验证 + `REMOTE_TASK_FAILED` non-retryable 码收口**（候选 A）。两件事天然耦合：要端到端验证失败路径，就要先有精确的失败码（否则 FAILED 终态仍保守重试，端到端 IT 只能验证 retry 而非 DLQ）；而 `REMOTE_TASK_FAILED` 码一旦引入，趁 Stage 17 真实 runtime 基础设施在手，端到端 IT 立刻能证明「真实 handler FAILED → 真实 SSE FAILED 帧 → `dlq(REMOTE_TASK_FAILED)` → outbox DLQ」闭环。**收口三次 deferred 的码 + 补 Stage 17 唯一盲区，一次 Stage 同时完成**。

**核心论点**：A2A 的 `FAILED/CANCELED/REJECTED` 是远程 agent **主动报告的终态失败**（业务/逻辑层），与 infra 层的 `RECEIVER_UNAVAILABLE`/`DELIVERY_TIMEOUT`（网络/超时，瞬时，retryable）性质不同。转发层对确定失败的远程 task 反复重试 = bomber；精确分类为 non-retryable → DLQ 更合理，且与 Stage 14 retry policy 正交（non-retryable 直接 DLQ 不消耗 retry budget，retryable 走退避耗尽 DLQ）。`AUTH_REQUIRED` 例外（「需认证」非「task 失败」，认证可恢复，保留 retry）。

Stage 18 **不裁决** Stage 13 的 push/pull/MQ 哲学张力（仍 H2/H3）—— 失败路径 IT 沿用 Stage 15 已选的 T1（同步等完成）+ Stage 10 测试 `TickSource`（无真实 scheduler）。§6.2 不变（`remote_task_failed` 是失败码，不引入 concrete broker/MQ、不写 payload 正文/token 流/Task execution state）。

## 1. Stage 17 评审（commit `4994e7d3`）

Stage 17 把 Stage 15 的「对接可行性 PoC」（MockWebServer 证明线上字节对称）提升为「真实 server 全栈验证」，并建立了 agent-bus 项目首个跨模块依赖。

**4 个优点：**

1. **端到端闭环真实验证（质变）**：全链路除 handler 外无 fake —— 真实 Postgres 16.2 + Flyway V1 + RLS（Stage 12）+ 真实 Spring Boot MVC + 真实 A2A SDK server runtime + 真实 Task 状态机流转（SUBMITTED→WORKING→COMPLETED）+ 真实 SSE 编码。Stage 15 只能证明「客户端发的字节 == server 应答的字节」（MockWebServer 用 SDK 自身序列化器产帧逐字节比对），Stage 17 证明「真实客户端 + 真实 server 交换这些字节后真的把 Task 推到 COMPLETED 并 round-trip 回 outbox ACK」。
2. **首次跨模块依赖 + 边界守卫**：agent-bus 与 agent-runtime 此前是完全隔离构建的兄弟模块（m2 互不引用）。Stage 17 引入 test-scope `agent-runtime` 依赖，同时 `AgentBusDependencyBoundaryTest.bus_does_not_depend_on_agent_runtime` 守卫 `com.huawei.ascend.bus..`（生产）反 `com.huawei.ascend.runtime..`，supersede `034da8f7` agent-service→agent-runtime 重命名遗留的旧 `service..` 守卫 —— **既建立依赖又守住边界**。
3. **意外强证据（tenant continuity）**：真实 `A2aAgentExecutor` 运行日志收到 `metadata.tenantId=tenant-loop`/`tenant-iso-a` —— 证明 `X-Tenant-Id` header 不只被发送（Stage 15 wire 断言），更被真实 controller 接收/解析/传给 handler（Stage 15 的 MockWebServer 根本无法验证此语义层）。
4. **两个有价值的发现**：(a) `LocalA2aRuntimeHost` 的 A2A server Spring 上下文纯内存但对 JDBC-bearing 共享 classpath 敏感（agent-bus 的 jdbc starter + postgres driver + flyway 泄漏后触发 `DataSourceAutoConfiguration`/`FlywayAutoConfiguration` 对真实 host 上下文 fire）；(b) **Spring Boot 4 autoconfigure 重打包**（jdbc `…autoconfigure.jdbc`→`org.springframework.boot.jdbc.autoconfigure`、flyway→`org.springframework.boot.flyway.autoconfigure`，旧包名排除静默无效）—— 后者是排查陷阱，值得文档化。

**5 个边界项 / 盲区（观察，驱动 Stage 18）：**

1. **只有 happy path（最大盲区）**：`StubHandler.execute` 恒返回 `completed("ok")`。Stage 7-16 落地的全部失败处理 —— `ForwardingStateMachine` 的 ACK/RETRY/DLQ/EXPIRED、MI9-001 lease-owner guarded mutation、Stage 14 retry policy、Stage 16 circuit breaker —— **都是 `InMemoryForwardingDelivery`/`FakeDeliveryPort` 等 fake 的 unit/contract test 覆盖，从未在真实端到端链路（真实 Postgres outbox + 真实 A2A server + 真实 SSE）跑过一次**。真实转发系统必须处理失败，这是 Stage 17 留下的最大覆盖盲区。
2. **`REMOTE_TASK_FAILED` 码三次 deferred**（14→15→17）：`ForwardingFailureCode` enum 共 7 码，**完全无 `remote_task_failed`**；`A2aForwardingDeliveryPort` 把 `FAILED/CANCELED/REJECTED`/`AUTH_REQUIRED` 一律保守映射 `retry(RECEIVER_UNAVAILABLE)`（Stage 15 PoC 取舍，注释明说「避免 Stage 9 classification / DDL / ICD 连锁」）。这意味着**远程 agent 明确报告失败时，转发层仍当瞬时网络故障反复重试**。
3. `MapEndpointResolver` 硬编码真实 port：生产 resolver 由 Stage 3 registry 集成实现，但 registry runtime 物理实现仍 deferred（H2/H3）。
4. 测试 `TickSource` 单 tick：真实 scheduler/polling/多 worker 未落地（§6.1「总线无调度器」+ H2/H3）。
5. **m2 旧 jar 脆弱性**：rebase 整合同事 `20dc622f` 后，workspace `agent-runtime/pom.xml` 新引入 `com.openjiuwen:agent-core-java` 依赖但 `${openjiuwen.version}` property **从未在任何 xml 定义**（同事 pre-existing）→ 阻塞重新 install agent-runtime；Stage 17 靠 m2 里 rebase 前 install 的旧 jar（`20dc622f` 未改 Java 源码，类签名不变）+ `mvn -f agent-bus/pom.xml test`（用 m2 旧 pom 绕过 broken workspace pom）跑通。**下次 agent-runtime Java 源码改动或 m2 清理即断裂**。这是构建债，不在 Stage 18 范围（见 §5），但需明示。

Stage 17 DoD：182 tests green，ArchUnit green，§6.2 不变。**接受**。

## 2. Stage 18 范围与设计

### 2.1 为什么（补失败路径盲区 + 收口三次 deferred 码）

两件事在 Stage 17 之后必须一起做，互相解锁：

- **要端到端验证失败处理**（盲区 1），就得让真实 server 产出失败终态。Stage 15 已证明 `A2aForwardingDeliveryPort` 能捕获 SSE `FAILED` 帧并映射 —— 但当前映射成保守 `retry`，端到端 IT 只能验证「真实 FAILED → retry」，无法验证「真实 FAILED → DLQ」这条更有价值的路径。
- **要收口 `REMOTE_TASK_FAILED` 码**（盲区 2），就要完成 Stage 15 当年回避的「Stage 9 classification / DDL / ICD 连锁」。码一引入，终态映射立即精确化（FAILED→dlq），Stage 17 的真实 runtime 基础设施立刻能证明这条新映射端到端成立。

合起来 = 一个 Stage 同时：① 引入 `remote_task_failed` non-retryable 码（完成 Stage 9 连锁）；② `A2aForwardingDeliveryPort` 终态映射精确化；③ 真实失败路径端到端 IT（`FAILED→DLQ` + `route 不可达→RETRY`）。复用 Stage 17 的 `LocalA2aRuntimeHost` + embedded-postgres 基础设施，**零新增前置工程**（不像 Stage 17 切片 0a-0d 的跨模块治理）。

### 2.2 `REMOTE_TASK_FAILED` 码设计

新码加进 `ForwardingFailureCode` 的 NON_RETRYABLE 组（与 `ROUTE_NOT_FOUND`/`TENANT_MISMATCH`/`PAYLOAD_REF_INVALID` 同类）：

```java
REMOTE_TASK_FAILED("remote_task_failed", Classification.NON_RETRYABLE),
```

**Classification = NON_RETRYABLE 的论证**：

| 失败层 | 码 | classification | 性质 |
|---|---|---|---|
| infra | `RECEIVER_UNAVAILABLE` / `DELIVERY_TIMEOUT` | RETRYABLE | 网络/超时，瞬时，重试合理 |
| infra | `BACKPRESSURE_REJECTED` | RETRYABLE | 接收方反压，瞬时 |
| 业务 | **`REMOTE_TASK_FAILED`（新）** | **NON_RETRYABLE** | 远程 agent 主动报告 task 失败 |
| 配置 | `ROUTE_NOT_FOUND` / `TENANT_MISMATCH` / `PAYLOAD_REF_INVALID` | NON_RETRYABLE | 配置/契约错误 |

- A2A `FAILED/CANCELED/REJECTED` 是 agent **主动报告的终态失败**（业务/逻辑层），重试同一输入大概率重现（agent 拒绝该能力 / 输入有误 / 确定性崩溃）。
- 转发层对确定失败的远程 task 反复重试 = bomber（轰炸下游），DLQ 更合理（人工介入或死信分析）。
- 与 Stage 14 retry policy 正交：non-retryable 直接 DLQ（不消耗 retry budget、不经退避）；retryable 走指数退避耗尽 → DLQ。
- `AUTH_REQUIRED` 例外：是「投递到达但需认证」非「task 失败」，认证可恢复（补凭证后重投可成），保留 `retry(RECEIVER_UNAVAILABLE)`。

> **语义标注**：classification = NON_RETRYABLE 是本 plan 的提议。若 H2/H3 裁决认为远程 `FAILED` 应可重试（如该部署下 agent 瞬时故障占多数），只需把 classification 改 `RETRYABLE` —— **码本身的引入与终态映射结构不受影响**，只是 `FAILED` 类终态从 `dlq` 回到 `retry`。码引入是 additive（`last_failure_code` 多一个可选值），ICD `compatibility.additive_fields_allowed: true` 已支持。

### 2.3 终态映射精确化（`A2aForwardingDeliveryPort`）

当前（Stage 15，`switch (state)`）：

```java
case TASK_STATE_COMPLETED, TASK_STATE_INPUT_REQUIRED -> acked();
default -> retry(RECEIVER_UNAVAILABLE);   // isFinal && !COMPLETED (FAILED/CANCELED/REJECTED) 或 AUTH_REQUIRED
```

Stage 18 精确化：

```java
case TASK_STATE_COMPLETED, TASK_STATE_INPUT_REQUIRED                 -> acked();
case TASK_STATE_FAILED, TASK_STATE_CANCELED, TASK_STATE_REJECTED     -> dlq(REMOTE_TASK_FAILED);   // 新：业务终态失败
default                                                              -> retry(RECEIVER_UNAVAILABLE);  // AUTH_REQUIRED 等 interrupted 非 INPUT_REQUIRED
```

**现有 MockWebServer 测试连带变更**：`A2aForwardingDeliveryPortMockWebServerTest.deliver_retries_on_remote_task_failed` 当前断言 `FAILED → retry(RECEIVER_UNAVAILABLE)`，语义变更后改名为 `deliver_dlqs_on_remote_task_failed`，断言 `FAILED → dlq(REMOTE_TASK_FAILED)`。这是**有意的语义变更**（保守重试 → 精确 DLQ），不是回归。

### 2.4 失败路径端到端 IT（新类 `C3ForwardingFailurePathIntegrationTest`）

不复用 `C3ForwardingEndToEndIntegrationTest`（它的 `@BeforeAll` 用 `StubHandler` boot 单个 runtime，无法产出 FAILED）。新类聚焦失败路径，照 Stage 17 范式（`@BeforeAll` boot embedded-postgres + Flyway + `JdbcForwardingOutbox` + `spring.autoconfigure.exclude` Spring Boot 4 包名 + 真实 `LocalA2aRuntimeHost`）：

**场景 1 — 真实 handler FAILED → DLQ（验证 `REMOTE_TASK_FAILED` 端到端）**：

- 声明 `FailingHandler`（照 `StubHandler`，`resultAdapter` 改映射 `AgentExecutionResult.failed("stub-failed", "forced failure")`）；`@BeforeAll` 用它 boot runtime。
- `AgentExecutionResult.failed(errorCode, errorMessage)` 工厂已确认存在（`agent-runtime/.../spi/AgentExecutionResult.java`）；`A2aResultRouter` 有 `case FAILED ->` 映射（result.failed → task state=FAILED → SSE FAILED 帧）。
- 链路：`outbox.enqueue(CONTROL_ONLY)` → `ForwardingDispatchLoop.run`（测试 `TickSource`）→ worker claim → `A2aForwardingDeliveryPort.deliver` → 真实 `/a2a` → `FailingHandler` → task FAILED → SSE FAILED 帧 → `dlq(REMOTE_TASK_FAILED)` → worker `moveToDlq` → outbox DLQ。
- 断言：`outbox.statusOf == DLQ`、`tick.dlqd() == 1`、`tick.retried() == 0`、`lastFailureCode == remote_task_failed`、tick 自洽不变量 `claimed == acked+retried+dlqd+expired+skipped`。

**场景 2 — route 不可达 → RETRY（验证 infra 失败端到端重试）**：

- `MapEndpointResolver` 指向一个未监听端口（如 `http://localhost:<runtime.port()+offset>/a2a`，connection refused）。不需要真实 runtime（deliver 在连真实 server 前就失败）。
- 链路：`outbox.enqueue` → tick → deliver → `JSONRPCTransport` 连接被拒 → SDK `errorConsumer` → `retry(RECEIVER_UNAVAILABLE)` → worker `scheduleRetry`（Stage 14 retry policy 算 `nextAttemptAt`）→ outbox RETRY_SCHEDULED。
- 断言：`outbox.statusOf == RETRY_SCHEDULED`、`tick.retried() == 1`、`lastFailureCode == receiver_unavailable`、`attemptCount == 1`、`nextAttemptAt` 已置。
- 复用 Stage 15 发现：SDK 把非 2xx 当静默空流（→ `DELIVERY_TIMEOUT`），真正 socket 断开/拒绝才 `errorConsumer`（→ `RECEIVER_UNAVAILABLE`）；用未监听端口确保走 socket 级 errorConsumer。

### 2.5 连带一致性（完成 Stage 15 当年回避的 Stage 9 连锁）

`remote_task_failed` 引入触及 Stage 9/12 的 failure-code 体系，必须一致更新（不能只动 enum）：

| 位置 | 改动 |
|---|---|
| `ForwardingFailureCode` enum | 加 `REMOTE_TASK_FAILED("remote_task_failed", NON_RETRYABLE)` + javadoc（NON_RETRYABLE 组补一条） |
| DDL CHECK（`forwarding-persistence §3.1`/`§11`） | `last_failure_code` CHECK 约束补 `'remote_task_failed'` |
| ICD yaml `failure_codes.non_retryable` | 加 `remote_task_failed`；`forbidden_fields` 不变 |
| classification harness | `failure_code_classification_drives_retry_and_dlq_routing` 等加 `remote_task_failed` 断言（non-retryable → DLQ 路径） |
| `SqlCodec`（Stage 12） | `last_failure_code` 序列化/反序列化覆盖新码（enum 值自动覆盖，确认 round-trip） |
| `ForwardingOutboxRecord` compact constructor | 不变量基于 classification，新码 non-retryable 自动符合 DLQ 路径（确认 `dlq(REMOTE_TASK_FAILED)` 经 MI9-001 lease-guarded `moveToDlq` 接受） |

### 2.6 边界 + ArchUnit + governance

- **§6.2 不变**：`remote_task_failed` 是失败码分类，不引入 concrete broker/MQ、不写 payload 正文/token 流/Task execution state、不跨租户回退。
- **ArchUnit**：`REMOTE_TASK_FAILED` 是纯 JDK enum 值 + `A2aForwardingDeliveryPort` 终态映射只动 `transport.a2a` 子包（Stage 15 已豁免 `org.a2aproject`），无需新 ArchUnit 豁免。
- **decision §8**：加 Stage 18 bullet（正向：失败码收口 + 终态精确化 + 失败路径端到端；反向：§6.2 不变、不裁决 push/pull/MQ）。
- **physical §2 / 转发边界**：加 Stage 18；L1 `README` C3 标题 + `§5.2` 加 Stage 18；L2 `forwarding-persistence` 加 §19。

## 3. 关键发现（前置分析）

| # | 发现 | 验证 | 结论 |
|---|---|---|---|
| 1 | `ForwardingFailureCode` 现状 | 读 enum 全文 | 7 码，**无 `remote_task_failed`**；NON_RETRYABLE 3 码（route_not_found/tenant_mismatch/payload_ref_invalid）、RETRYABLE 3 码（delivery_timeout/receiver_unavailable/backpressure_rejected）、DEDUP 1 码（duplicate_suppressed） |
| 2 | 终态映射现状 | 读 `A2aForwardingDeliveryPort` switch | `COMPLETED/INPUT_REQUIRED→acked`、`FAILED/CANCELED/REJECTED/AUTH_REQUIRED→retry(RECEIVER_UNAVAILABLE)`（保守）、超时→`retry(DELIVERY_TIMEOUT)`、连接错误→`retry(RECEIVER_UNAVAILABLE)`、resolver 空→`dlq(ROUTE_NOT_FOUND)` |
| 3 | 真实 handler 产 FAILED 的 API | 读 `AgentExecutionResult`/`A2aResultRouter`/`RuntimeAppTest` | ✅ `AgentExecutionResult.failed(code,msg)` 工厂 + `A2aResultRouter` `case FAILED ->` + `StubHandler.resultAdapter` 范式现成 → `FailingHandler` 可直接声明 |
| 4 | route 不可达走 errorConsumer | Stage 15 发现（SDK 行为） | ✅ 未监听端口 = socket 级 connection refused → `errorConsumer` → `retry(RECEIVER_UNAVAILABLE)`（非 2xx 才走静默空流→`DELIVERY_TIMEOUT`） |
| 5 | `REMOTE_TASK_FAILED` 引入的连锁范围 | 对照 Stage 9 MI9-004 / Stage 12 SqlCodec | enum + DDL CHECK + ICD yaml + classification harness + SqlCodec round-trip；record 不变量基于 classification 自动符合，`moveToDlq(REMOTE_TASK_FAILED)` 经 MI9-001 lease-guard 接受 |
| 6 | MockWebServer 现有测试影响 | grep `deliver_retries_on_remote_task_failed` | 1 个测试需改名 + 改断言（retry→dlq），是有意语义变更非回归 |

## 4. 切片 + MI 表

| MI | 切片 | 产出 |
|---|---|---|
| MI18-001 | 0 `REMOTE_TASK_FAILED` 码 + 治理 | `ForwardingFailureCode` 加 `REMOTE_TASK_FAILED("remote_task_failed", NON_RETRYABLE)` + javadoc；DDL CHECK 补码；ICD yaml `failure_codes.non_retryable` 加码；classification harness 加断言；`SqlCodec` round-trip 覆盖；`ForwardingOutboxRecord` 确认 `dlq(REMOTE_TASK_FAILED)` 经 lease-guard 接受 |
| MI18-002 | 1 终态映射精确化 | `A2aForwardingDeliveryPort` `switch` 加 `FAILED/CANCELED/REJECTED → dlq(REMOTE_TASK_FAILED)`，`AUTH_REQUIRED` 保留 `retry(RECEIVER_UNAVAILABLE)`；`A2aForwardingDeliveryPortMockWebServerTest.deliver_retries_on_remote_task_failed` → `deliver_dlqs_on_remote_task_failed`（retry→dlq 断言，有意语义变更） |
| MI18-003 | 2 失败路径端到端 IT | 新 `C3ForwardingFailurePathIntegrationTest`：`FailingHandler`（`resultAdapter` 映射 `AgentExecutionResult.failed`）→ 真实 `/a2a` → SSE FAILED → `dlq(REMOTE_TASK_FAILED)` → outbox DLQ（场景 1）；`MapEndpointResolver` 指未监听端口 → `errorConsumer` → `retry(RECEIVER_UNAVAILABLE)` → outbox RETRY_SCHEDULED（场景 2）；各断言 tick 自洽 + lastFailureCode + attemptCount/nextAttemptAt |
| MI18-004 | 3 文档同步 | decision §8 Stage 18 bullet + 更新 Stage 15/17 bullet 的 REMOTE_TASK_FAILED deferred 标注；ICD（边界条 + Open Issues REMOTE_TASK_FAILED 收口）；yaml `stage18_scope` + 顶部 description + stage15/17_scope 标注；L2 `forwarding-persistence §19` + 更新 §3.1/§11 failure code；L1 `README` C3 标题 + `§5.2` + `physical` 转发边界 + §2；本双语文档 |
| MI18-005 | 4 构建验证 + 提交 | `mvn -f agent-bus/pom.xml test` green（182 + 2 失败路径 IT ≈ 184+）；ArchUnit green；§6.2 文本扫描不触发；commit + push（experimental） |

## 5. deferred + 风险（明示边界）

**风险（需关注）：**

- **`FAILED → DLQ` 语义变更（最重要）**：把远程 `FAILED/CANCELED/REJECTED` 从保守 retry 改 non-retryable DLQ 是**行为变更**，影响所有用 `A2aForwardingDeliveryPort` 的转发。论证见 §2.2（业务失败 vs infra 失败分层）。若该部署下 agent 瞬时故障占多数、希望「先重试 N 次再 DLQ」，则 classification 应改 RETRYABLE（码 + 映射结构不变）。**建议 plan 评审时确认此语义**，或默认采纳 NON_RETRYABLE、H2/H3 有异议再调。
- **MockWebServer 测试语义变更**：`deliver_retries_on_remote_task_failed` → `deliver_dlqs_on_remote_task_failed` 是有意变更，需在 commit message 明示（避免误读为测试 bug）。
- **场景 2 的端口选择**：未监听端口须确保走 socket `errorConsumer` 而非 SDK 静默空流（`DELIVERY_TIMEOUT`）。用 `runtime.port()` 之外的明确未监听端口（如 +offset），不要用可能被占用的端口。

**deferred（明示边界，不在 Stage 18 范围）：**

- **不裁决 T1 vs C3 异步哲学张力**：失败路径 IT 沿用 Stage 15 选的 T1（同步等完成）+ 测试 `TickSource`（无真实 scheduler），**不裁决** Stage 13 push/pull/MQ（仍 H2/H3）。
- **lease 过期回收端到端 IT**：claim → DISPATCHING + lease → deliver 超过 lease TTL → lease 过期 → 另一 worker reclaim 的端到端验证需控制时钟（真实 `JdbcForwardingOutbox` claim 基于 DB `lease_until`），复杂度高，Stage 18 不含（可作可选扩展或后续 Stage）。
- **`MapEndpointResolver` → registry resolver**：生产 resolver 由 Stage 3 registry 集成实现（registry runtime 物理实现仍 H2/H3）；Stage 18 IT 仍用 `MapEndpointResolver` 硬编码真实/假 port。
- **真实 scheduler / polling / 多 worker**：§6.1 + H2/H3。
- **production 依赖 agent-runtime / 真实 agent handler**：Stage 17 建立的是 test-scope；production 提升 + 非 StubHandler/FailingHandler 的真实 handler 是大边界决策（H2/H3）。
- **`openjiuwen.version` 构建债**（Stage 17 盲区 5）：同事 `20dc622f` 引入 `com.openjiuwen:agent-core-java` 依赖但 property 未定义，阻塞 agent-runtime workspace install；Stage 17 靠 m2 旧 jar 绕过。**Stage 18 不碰**（同事模块 + 范围外），但需作为独立 tech debt 项跟踪 —— 下次 agent-runtime Java 源码改动或 m2 清理会让 Stage 17/18 的 IT 断裂。
- **push/pull/MQ 最终裁决**：仍 H2/H3。

## 相关文档

- Stage 17 计划：[`agent-bus-stage16-review-and-stage17-plan`](agent-bus-stage16-review-and-stage17-plan.md)（首次跨模块端到端集成）。
- C3 裁决：[`agent-bus-forwarding-runtime-decision`](../review-packets/agent-bus-forwarding-runtime-decision.md)（`adopted-c3`；§8 Stage 18 许可段待加）。
- runtime 契约：[`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)（Stage 18 边界条 + Open Issues `REMOTE_TASK_FAILED` 收口）。
- 持久化 L2：[`forwarding-persistence`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)（Stage 18 决策：失败码收口 + 失败路径端到端，§19 待加）。
- 失败码源头：`agent-bus/src/main/java/com/huawei/ascend/bus/forwarding/spi/ForwardingFailureCode.java`（Stage 9 MI9-004 classification）；终态映射 `…/runtime/transport/a2a/A2aForwardingDeliveryPort.java`（Stage 15）。
- 真实 FAILED 产法：`agent-runtime/.../spi/AgentExecutionResult.failed(...)` + `A2aResultRouter` `case FAILED` + `RuntimeAppTest.StubHandler` 范式（`FailingHandler` 照此改 `resultAdapter`）。
