---
artifact_type: delivery_projection
version: agent-bus-stage22-review-and-stage23-plan
status: stage-23-planned
source_commit: 852765c9
stage23_planned: 2026-07-01
source_stage22_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage21-review-and-stage22-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_icd_forwarding: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md
source_l2: architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md
target_module: agent-bus（纯测试 / 验证阶段，无生产代码；不 boot runtime）
---

# agent-bus Stage 22 评审与 Stage 23 计划

**payloadRef 端到端传递验证：outbox 持久化 + A2A metadata 边界**

本文是 agent-bus 转发运行态验证序列的交付投影（delivery projection）：先评审上一阶段（Stage 22）的实际落点，再规划下一阶段（Stage 23）的范围与设计。沿用 Stage 19–22 的「纯测试 / 验证阶段」范式：无生产代码改动，不 boot runtime，不触及 §6.2。

---

## §0 结论

- **Stage 22 接受**（commit `852765c9`，192 tests green，ArchUnit green，未 push 待用户授权）。时间驱动的终态（EXPIRED `markExpired` 落盘）+ lease 续约首次端到端真实触发两场景闭合。
- **Stage 22 留的第三个高价值盲区 = payloadRef 大载荷引用路径**。Stages 17–22 的全部端到端验证（happy-path / 失败路径 / 重投往返 / 租约回收 / 断路器 / 多 worker 并发 / EXPIRED / 续约）**一律用 CONTROL_ONLY envelope**，`payloadRef=null`。DATA_BEARING + payloadRef 的端到端传递**从未跑通**。本文将其收口为 Stage 23。
- **范围修正（关键）**：Stage 22 计划（本文 `source_stage22_plan` 第 49/192 行）预期 Stage 23「需 boot runtime + 一个能断言『收到 payloadRef metadata』的 handler」。本轮前置调研发现这个预期可以收窄——agent-bus 是**无状态引用路由层**，它的职责是**把 payloadRef 从 enqueue 传到 A2A metadata**，不是处理 payloadRef 指向的载荷内容。payloadRef 的两个传递关键点**都不需要 boot runtime**：
  - **outbox 持久化层**：复用 Stage 19–22 的 `embedded-postgres + Flyway + JdbcForwardingOutbox` + observing fake delivery port；
  - **transport metadata 层**：复用 Stage 15 的 `A2aForwardingDeliveryPort + MockWebServer`。
  - 因此 Stage 23 仍是**轻量纯测试阶段**（比 Stage 22 预期轻），不需要 boot runtime，不需要新裁决。接收端（agent-runtime `A2aJsonRpcController`）是否提取并处理 payloadRef 是 **agent-runtime 的职责**，超出 agent-bus 边界，记录为 deferred。
- **payloadRef 持久化是完整的**（与 Stage 22 EXPIRED 的「deadline 触发源缺失」根本不同）：`ForwardingEnvelope` 有 payloadRef 字段、`ForwardingOutboxRecord` 有 payloadRef 字段、DDL 有 `payload_ref VARCHAR(1024)` 列、`ForwardingSqlCodec` 对其编码/解码。所以 Stage 23 **不需要任何注入**——payloadRef 在真实链上自然流动，场景只需断言它在各层不丢失。这直接对比 EXPIRED（record 不持久化 deadline、deliver 不检查 deadline → 真实链路从不返回 `expired()`）。
- **§6.2 守恒**：payloadRef 是**数据引用路径**（String 引用），不是 payload body / token stream / Task execution state / concrete broker client。DATA_BEARING envelope 携带 payloadRef 字符串不触发任何 §6.2 禁项。agent-bus 永远不持有/存储/解析 payload 正文（HD4 不变）。
- **预计**：192 → 195 tests green（场景 A +1 IT，场景 B +2 transport 单元测试），无生产代码，ArchUnit green。

---

## §1 Stage 22 评审

### 1.1 已落地

`C3ForwardingExpiryAndLeaseRenewalIntegrationTest` 双场景，纯测试无生产代码，仅 `embedded-postgres + Flyway`，不 boot runtime，`@Isolated`：

- **场景 A — EXPIRED 终态端到端**：observing fake delivery port 注入 `expired()` → 单 tick `markExpired` 落盘（`status=EXPIRED` / `last_failure_code=delivery_timeout` / `attempt_count=0` / lease NULL）；2 ticks 聚合 `claimed=1` 证明终态不回收（EXPIRED 不在 `claimDue` 候选集 PENDING/RETRY_SCHEDULED/DISPATCHING）。这是继 ACKED（Stage 17）/ DLQ（Stage 18）后闭合的**第三个终态**。
- **场景 B — lease 续约真实触发**：`MutableEpochClock(t0)` + `DispatchLeasePolicy(renewBefore=50s, extend=60s)` + claim 短 lease 30s → `remaining=30s < 50s` 触发 `renewLease(msg, t0+90s)` 在 deliver 前 commit；observing delivery port 在 deliver 时读 PG `lease_until==t0+90s` 作为**续约确凿证据**（该值只可能由 renewLease 写入）→ ACKED。这是 Stages 19–21 全程 `DispatchLeasePolicy.DISABLED` 后续约首次端到端触发。

### 1.2 测试与提交

- 192 tests green（Stage 21 的 190 + 2 IT），ArchUnit green。
- commit `852765c9`（experimental，**未 push**，PAT 过期待用户 `! git push origin experimental`）。

### 1.3 关键发现（EXPIRED 触发源缺失）

EXPIRED 是「SQL 正确但触发源缺失」终态：`ForwardingOutboxRecord` 不持久化 `deadlineMillisEpoch`（envelope 有但 record 无投影）、`A2aForwardingDeliveryPort.deliver` 不检查 deadline → 真实 A2A 链路**从不返回 `expired()`**。Stage 22 通过注入 `expired()` 验证 `markExpired` SQL 契约（与 Stage 18 注入 `dlq()` 对称）。真实触发源（record deadline 字段 + DDL 列 + deliver deadline 检查 = schema 变更）deferred。`markExpired` 硬编码 `delivery_timeout`（retryable 码）满足 EXPIRED record 不变量但语义混淆——EXPIRED 终态该码从不参与 retry 决策，无害。

### 1.4 deferred 结转（Stage 22 → 后续）

EXPIRED 真实触发源（schema 变更）/ 真实 scheduler / polling cadence / 真实 agent handler（仍 fake/observing port）/ registry 集成 resolver 生产实现 / 断路器状态持久化 / 连接池治理 / push-pull-MQ 最终裁决（仍 H2/H3）。

---

## §2 Stage 23 范围与设计

### 2.1 为什么是 payloadRef（盲区分析）

agent-bus 的 forwarding 设计有一个明确的载荷不变量（HD4 + ICD forwarding + L2 forwarding-persistence）：**runtime-to-runtime 消息有载荷时只携带 `payloadRef`（条件必填，MI5-003 方案 B：DATA_BEARING 必填、CONTROL_ONLY 可选），不携带 payload body；大载荷走 data reference path，不进 event / control channel。** 这是 §6.2 的核心执行机制——payloadRef 是「大载荷不进控制通道」的载体。

但是从 Stage 17 到 Stage 22，**所有端到端验证一律用 CONTROL_ONLY envelope**（payloadRef=null）。这留下一个直接的盲区：

| 层 | payloadRef 状态 | 是否验证过 |
|---|---|---|
| `ForwardingEnvelope`（SPI 入口） | 有字段，DATA_BEARING 强制非空非空白（构造器校验） | ✅ 单元测试（Stage 7 MI7） |
| `ForwardingOutboxRecord`（领域模型） | 有字段 | ✅ record 不变量测试（Stage 9） |
| DDL `payload_ref VARCHAR(1024)` 列 | 存在 | ❌ **从未端到端写入/读回** |
| `ForwardingSqlCodec` encode/decode | 实现完整 | ❌ **从未端到端 round-trip** |
| `claimDue` → worker → deliver 传递链 | record.payloadRef() 一路透传 | ❌ **从未端到端断言不丢失** |
| `A2aForwardingDeliveryPort.toMessageSendParams` 把 payloadRef 放进 metadata | 实现完整（line 230-232） | ❌ **从未断言到达请求体** |
| A2A 接收端提取 payloadRef | — | ❌ agent-runtime 职责，超 agent-bus 边界 |

**核心论点**：payloadRef 的持久化和传递代码（DDL + SqlCodec + toMessageSendParams）**全部写好了但从未端到端跑通**。这是 Stage 22 留的「第三个高价值盲区」的直接定义（`source_stage22_plan` 第 49/192 行）。

### 2.2 范围修正：为什么不需要 boot runtime（收窄 Stage 22 预期）

Stage 22 计划原文预期 Stage 23「需 boot runtime + 一个能断言『收到 payloadRef metadata』的 handler」。本轮前置调研**修正**了这个预期——它把验证目标设成了「接收端 handler 收到并处理 payloadRef」，那是 **agent-runtime 的职责**，不是 agent-bus 的。

agent-bus 在 payloadRef 链路上的职责边界（HD4 守恒）：

> agent-bus 是**无状态引用路由层**。它传递 payloadRef（一个 String 引用），**从不持有/存储/解析 payloadRef 指向的载荷正文**。payloadRef 对 agent-bus 是 **opaque 引用**——它被原样持久化、原样透传到 A2A metadata，agent-bus 不读取/校验其内容（只校验非空非空白格式）。

因此 agent-bus 内 payloadRef 的完整传递链有两个关键检查点，**两个都不需要 boot runtime**：

1. **outbox 持久化检查点**（enqueue → claim → deliver，payloadRef 在真实 PG 上 round-trip 不变）：复用 Stage 22 的 `embedded-postgres + Flyway + JdbcForwardingOutbox` + observing fake delivery port。**不 boot runtime**（fake port 替代真实 A2A 投递）。
2. **transport metadata 检查点**（`A2aForwardingDeliveryPort.toMessageSendParams` 把 payloadRef 放进 metadata，到达 A2A 请求体）：复用 Stage 15 的 `A2aForwardingDeliveryPort + MockWebServer`。**不 boot runtime**（MockWebServer 拦截请求，断言请求体）。

这两个检查点覆盖 agent-bus 对 payloadRef 的全部职责。接收端（真实 agent-runtime handler）是否提取 payloadRef 是 agent-runtime 的事——而且前置调研发现 `A2aJsonRpcController` **未显式提取 payloadRef metadata**（那是 agent-runtime 的 gap，记录为 deferred，但不在 agent-bus Stage 23 范围）。

**结论**：Stage 23 是轻量纯测试阶段（与 Stage 19–22 同范式），不 boot runtime，不需要新裁决，§6.2 不变。这比 Stage 22 预期的「boot runtime + handler」轻得多——预期被调研收窄。

### 2.3 场景 A：DATA_BEARING payloadRef outbox 持久化端到端

**目标**：证明 payloadRef 从 enqueue 到 deliver 在真实 PG 上 round-trip 不丢失（闭合 DDL/SqlCodec/传递链的端到端盲区）。

**文件**：`agent-bus/src/test/java/com/huawei/ascend/bus/forwarding/runtime/C3ForwardingPayloadRefIntegrationTest.java`（新文件）。

**boot recipe**（复用 Stage 22 `C3ForwardingExpiryAndLeaseRenewalIntegrationTest` 的引导方案，唯一差异是 envelope 用 DATA_BEARING）：

```java
@Isolated
@BeforeAll bootPostgres() {
    pg = EmbeddedPostgres.builder().start();
    dataSource = pg.getPostgresDatabase();
    Flyway.configure().dataSource(dataSource).load().migrate();
    outbox = new JdbcForwardingOutbox(dataSource);
}
```

**envelope helper 差异**（对照 Stage 22 helper 以 `ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY, null` 结尾）：

```java
// Stage 23：DATA_BEARING，payloadRef 必填非空非空白
ForwardingEnvelope.PayloadPolicy.DATA_BEARING, "ref://tenant-a/payload/" + messageId
```

**outboxFullRow 投影读扩展**（Stage 22 版读 6 列：status/last_failure_code/attempt_count/next_attempt_at/lease_owner/lease_until；Stage 23 扩展读第 7 列 `payload_ref`）：

```java
// raw JDBC 投影读（outbox 无 per-record reader，照 Stage 18/22 raw JDBC 投影范式）
String sql = "SELECT status, last_failure_code, attempt_count, next_attempt_at, " +
             "lease_owner, lease_until, payload_ref FROM agent_bus_forwarding_outbox " +
             "WHERE tenant_id=? AND message_id=?";
```

**observing fake delivery port**（复用 Stage 22 模式）：

```java
ForwardingDeliveryPort observingDelivery = (record, now) -> {
    // 关键断言：deliver 收到的 record.payloadRef() 与 enqueue 时一致（真实 PG round-trip 不丢失）
    assertThat(record.payloadRef()).isEqualTo("ref://tenant-a/payload/" + messageId);
    // 同时断言 PG 里的 payload_ref 列也一致（证明 SqlCodec decode 正确）
    Map<String,Object> row = outboxFullRow(tenant, messageId);
    assertThat(row.get("payload_ref")).isEqualTo("ref://tenant-a/payload/" + messageId);
    return ForwardingDeliveryResult.acked();
};
```

**worker + loop**（复用 Stage 22，`DispatchLeasePolicy.DISABLED`）：

```java
ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(outbox, outbox, observingDelivery,
        DispatchLeasePolicy.DISABLED, clock, ForwardingRetryPolicy.DEFAULT);
ForwardingDispatchLoop loop = new ForwardingDispatchLoop(worker, singleTickSource, ForwardingDispatchLoop.NO_BACKOFF);
loop.run(tenant, 5, leaseOwner, leaseDurationMillis);
```

**断言**：
1. payloadRef 从 envelope → record → PG `payload_ref` 列 → record.payloadRef() → delivery port **一路不变**（DATA_BEARING 引用完整性）。
2. 单 tick 后 status=ACKED（DATA_BEARING 不影响投递状态机，与 CONTROL_ONLY 同路径）。

### 2.4 场景 B：payloadRef → A2A metadata 传输边界

**目标**：证明 `A2aForwardingDeliveryPort.toMessageSendParams` 把 DATA_BEARING record 的 payloadRef 放进 A2A metadata 到达请求体，CONTROL_ONLY record 省略 payloadRef（闭合 Stage 15 transport 盲区）。

**文件**：`agent-bus/src/test/java/com/huawei/ascend/bus/forwarding/runtime/transport/a2a/A2aForwardingDeliveryPortMockWebServerTest.java`（**扩展现有文件**）。

**transport 盲区直接证据**：现有 `record()` helper（line 87-98）已经设置了 `payloadRef="payload-ref-1"`，但**全部 5 个现有场景都没有断言它到达请求体**——`toMessageSendParams` 第 230-232 行的 `if (record.payloadRef() != null) { metadata.put("payloadRef", record.payloadRef()); }` **从未被验证**。

**新增测试 1 — DATA_BEARING 携带 payloadRef 到 metadata**：

```java
@Test void deliver_carries_payload_ref_in_a2a_metadata_for_data_bearing_record() {
    server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody(sseFrame(task(COMPLETED))));
    port(2000).deliver(record(), clock.epochMillis());  // record() 已含 payloadRef="payload-ref-1"
    RecordedRequest request = server.takeRequest();
    String body = request.getBody().readUtf8();
    assertThat(body).contains("payloadRef");
    assertThat(body).contains("payload-ref-1");
}
```

**新增 controlOnlyRecord() helper + 测试 2 — CONTROL_ONLY 省略 payloadRef**：

```java
// 新 helper：payloadPolicy=CONTROL_ONLY, payloadRef=null（对照 record() 的 DATA_BEARING）
private ForwardingOutboxRecord controlOnlyRecord() { ... ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY, null ... }

@Test void deliver_omits_payload_ref_for_control_only_record() {
    server.enqueue(...);
    port(2000).deliver(controlOnlyRecord(), clock.epochMillis());
    RecordedRequest request = server.takeRequest();
    String body = request.getBody().readUtf8();
    assertThat(body).doesNotContain("payloadRef");
}
```

**断言**：
1. DATA_BEARING：请求体含 `"payloadRef"` 字段 + 引用值（metadata 注入生效）。
2. CONTROL_ONLY：请求体不含 `"payloadRef"`（`if (payloadRef != null)` 省略生效）。
3. 两场景都**不含 payload 正文**——TextPart 只携带一个简短路由描述符（`"agent-bus forwarded message " + messageId`），§6.2 守恒。

### 2.5 边界 + ArchUnit + §6.2

| 护栏 | Stage 23 状态 |
|---|---|
| 无生产代码 | ✅ 两场景都是测试（场景 A 新 IT + 扩展 1 helper；场景 B 扩展现有 test 文件 + 2 测试 + 1 helper） |
| 不 boot runtime | ✅ 场景 A fake delivery port + embedded-postgres；场景 B MockWebServer + `A2aForwardingDeliveryPort` |
| §6.2 守恒 | ✅ payloadRef 是 String 引用，非 payload body / token stream / Task state / concrete broker。两场景断言请求体不含 payload 正文 |
| ArchUnit | ✅ 无新生产类型，现有纯度规则全 green，无需新豁免 |
| `DispatchTickResult` 自洽 | ✅ 场景 A 复用现有 worker/loop，单 tick ACKED，不变量自动保持 |
| 4+1 视图回灌 | ✅ 按 [[agent-bus-4plus1-view-rebound]] 清单，6 L1 视图 + 2 L2 + ICD + yaml + decision 全同步 |

---

## §3 关键发现（前置分析）

| # | 发现 | 影响 |
|---|---|---|
| F1 | **payloadRef 持久化完整**（envelope→record→DDL `payload_ref VARCHAR(1024)`→SqlCodec encode/decode 全实现） | Stage 23 无需注入，payloadRef 在真实链自然流动；与 Stage 22 EXPIRED「触发源缺失」根本不同 |
| F2 | **DATA_BEARING 端到端完全空白**：Stages 17–22 全 CONTROL_ONLY，DATA_BEARING + payloadRef 从未端到端跑通 | 这是 Stage 23 的核心盲区定义 |
| F3 | **transport 盲区直接证据**：`A2aForwardingDeliveryPortMockWebServerTest.record()` 已设 `payloadRef="payload-ref-1"` 但全 5 场景从未断言到达请求体 | `toMessageSendParams` line 230-232 从未验证 → 场景 B 直接闭合 |
| F4 | **agent-bus 是无状态引用路由层**：传递 payloadRef（opaque String），从不持有/存储/解析载荷正文（HD4 守恒） | 收窄 Stage 22 预期——两检查点都不需 boot runtime；§6.2 不触发 |
| F5 | **PAYLOAD_REF_INVALID 失败码未接线**：`ForwardingFailureCode.PAYLOAD_REF_INVALID`（NON_RETRYABLE）只有枚举定义，无生产代码返回它 | 类似 Stage 22 EXPIRED「触发源缺失」；接线需校验语义决策（DATA_BEARING + payloadRef 空白何时抛），deferred |
| F6 | **payloadPolicy 未持久化到 record**：record 只有 nullable payloadRef，无 DATA_BEARING/CONTROL_ONLY 区分；用 payloadRef 的 null/非null 隐含表达 | by design；不影响传递正确性（场景 A 验证 payloadRef round-trip 不变即覆盖）。但弱化了 DATA_BEARING 不变量（误清 payloadRef 后 record 无法知道原是 DATA_BEARING）——记录观察，非 Stage 23 验证目标 |
| F7 | **A2A 接收端未提取 payloadRef**：agent-runtime `A2aJsonRpcController` 未显式提取 metadata 里的 payloadRef | agent-runtime gap，非 agent-bus 职责；接收端处理 deferred |
| F8 | **两场景都不 boot runtime**：场景 A fake port + embedded-postgres；场景 B MockWebServer + 投递端口 | Stage 23 是轻量纯测试阶段（与 Stage 19–22 同范式），比 Stage 22 预期轻 |
| F9 | **测试基础设施全复用**：Stage 22 boot recipe + observing port + raw JDBC 投影读 + Stage 15 MockWebServer + sseFrame/task helper | 无新基础设施，无新依赖，无新 ArchUnit 豁免 |

---

## §4 切片 + MI 表

| MI | 切片 | 产出 |
|---|---|---|
| MI23-001 | 0 交付投影（本文） | Stage 22 评审 + Stage 23 范围/设计/场景 A/B/发现/deferred。frontmatter `status: stage-23-planned`。 |
| MI23-002 | 1 场景 A IT | 新文件 `C3ForwardingPayloadRefIntegrationTest.java`（forwarding/runtime/）：DATA_BEARING envelope + embedded-postgres + observing fake port + 扩展 outboxFullRow 读 payload_ref 列 + 断言 payloadRef 一路 round-trip 不变 → ACKED。`@Isolated`，不 boot runtime。 |
| MI23-003 | 1 场景 B 传输测试 | 扩展 `A2aForwardingDeliveryPortMockWebServerTest`：+`controlOnlyRecord()` helper（CONTROL_ONLY/payloadRef=null）；+`deliver_carries_payload_ref_in_a2a_metadata_for_data_bearing_record`（断言 body 含 "payloadRef"+"payload-ref-1"）；+`deliver_omits_payload_ref_for_control_only_record`（断言 body 不含 "payloadRef"）。 |
| MI23-004 | 2 文档同步 | L1 4+1 视图 7 文件（README/physical/development/logical/process/scenarios/ARCHITECTURE）+ L2 forwarding-persistence 新增 §24 + ICD forwarding-runtime（边界标题/边界条/Open Issue）+ yaml（description/stage23_scope）+ decision §8 Stage 23 bullet。按 [[agent-bus-4plus1-view-rebound]] 回灌清单。 |
| MI23-005 | 3 构建验证 + 提交 | `mvn -f .../agent-bus/pom.xml test -s ~/.m2/settings.xml -B`；断言 192→195 tests green，ArchUnit green；commit experimental + 提示用户 push。 |

---

## §5 deferred + 风险

### 5.1 deferred（Stage 23 不触及，记录为后续）

- **PAYLOAD_REF_INVALID 接线**（F5）：当前 `ForwardingFailureCode.PAYLOAD_REF_INVALID` 只有枚举，无触发点。接线 = 生产代码（worker 或 enqueue 路径在 DATA_BEARING + payloadRef 空白时返回该码）+ 校验语义决策（何时抛、retry 还是 DLQ）→ 独立生产化议题。
- **接收端 handler 处理 payloadRef**（F7）：agent-runtime `A2aJsonRpcController` 提取 payloadRef metadata 并解析载荷——agent-runtime 职责，超 agent-bus 边界。
- **payload store 不存在**：agent-bus 永远不持有/存储 payload 正文（HD4/§6.2 永久禁项）；payloadRef 指向的外部数据存储是调用方/接收方的职责。
- **payloadPolicy 持久化**（F6，可选）：若需强 DATA_BEARING 不变量（误清 payloadRef 可检测），可在 record 加 payloadPolicy 列 + DDL migration——schema 变更，独立议题，非 Stage 23 范围。
- **沿用 Stages 15–22 deferred**：真实 agent handler / registry 集成 resolver 生产实现 / 断路器状态持久化 / 连接池治理 / 真实 scheduler/polling cadence / push-pull-MQ 最终裁决（H2/H3）。

### 5.2 风险

- **DATA_BEARING 路径未被任何前序 Stage 验证过的潜在 bug**：场景 A/B 可能暴露 DDL/SqlCodec/toMessageSendParams 在 DATA_BEARING 下的真实缺陷（这正是验证的价值）。若发现，Stage 23 性质允许**只记录缺陷 + 失败测试 mark**，不在此阶段改生产代码（保持纯测试性质），或视缺陷严重度决定是否升级为生产修复 stage。
- **§6.2 守恒自证**：payloadRef 是引用非正文，两场景都断言请求体不含 payload 正文（TextPart 只含路由描述符），自证不触发 §6.2。

---

## 相关文档

- Stage 22 计划（本文评审对象）：[`agent-bus-stage21-review-and-stage22-plan`](agent-bus-stage21-review-and-stage22-plan.md)
- 运行态裁决：[`agent-bus-forwarding-runtime-decision`](../review-packets/agent-bus-forwarding-runtime-decision.md) §8
- transport 候选评审：[`agent-bus-forwarding-runtime-transport-candidates`](../review-packets/agent-bus-forwarding-runtime-transport-candidates.md)
- 运行态 ICD：[`ICD-agent-bus-forwarding-runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)
- forwarding ICD（payloadRef/HD4）：[`ICD-agent-bus-forwarding`](../../05-contracts/human-readable/ICD-agent-bus-forwarding.md)
- L2 持久化：[`forwarding-persistence`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)
- yaml（machine-readable）：[`agent-bus-forwarding-runtime.v1`](../../05-contracts/machine-readable/agent-bus-forwarding-runtime.v1.yaml)
