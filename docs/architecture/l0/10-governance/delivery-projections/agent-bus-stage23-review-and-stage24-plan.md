---
artifact_type: delivery_projection
version: agent-bus-stage23-review-and-stage24-plan
status: stage-24-planned
source_commit: e827291c
stage24_planned: 2026-07-01
source_stage23_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage22-review-and-stage23-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_icd_forwarding: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md
source_l2: architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md
target_module: agent-bus（**含生产代码改动**：adapter 首次引入 `TransactionTemplate` 事务管理 + RLS 接线；不 boot runtime；§6.2 不变）
---

# agent-bus Stage 23 评审与 Stage 24 计划

**RLS 接线：闭合 §7.3 跨租户纵深防御的「armed but not wired」债务**

本文是 agent-bus 转发运行态验证序列的交付投影（delivery projection）：先评审上一阶段（Stage 23）的实际落点，再规划下一阶段（Stage 24）的范围与设计。**Stage 24 打破 Stages 19–23 的「纯测试 / 验证阶段」范式**——它是 persistence 层的**生产代码改动**：adapter（`JdbcForwardingOutbox` / `JdbcForwardingInbox`）从 auto-commit 改为事务化，首次引入事务管理，使 §7.3 RLS 纵深防御在受限角色连接下真正生效。仍不 boot runtime，仍不触及 §6.2。

---

## §0 结论

- **Stage 23 接受**（commit `e827291c`，195 tests green，ArchUnit green，未 push 待用户授权）。payloadRef 端到端传递验证两场景闭合：DATA_BEARING payloadRef 在真实 PG 上 round-trip 不变（持久化盲区）+ A2A metadata 传输边界两分支对称（transport 盲区）。
- **Stage 23 收尾调查挖出 Stage 12 遗留的真实债务 = RLS「armed but not wired」**。Stage 12 落地真实持久化时，V1 migration 配好了 §7.3 RLS（`ENABLE ROW LEVEL SECURITY` + `CREATE POLICY ... USING (tenant_id = current_setting('app.tenant_id', true))`，fail-closed：未设 tenant 时 `current_setting(...,true)` 返回 NULL → 行不可见），L2 `forwarding-persistence §273` 也明写「`app.tenant_id` 由应用连接设置」。但 production `main` 下 `grep` 确认**无任何** `app.tenant_id` / `SET ROLE` / `SET LOCAL` / `TenantContext`——**JDBC adapter 从不设置租户上下文，RLS policy 从不激活**。当前唯一租户隔离是应用层 `WHERE tenant_id = :tenantId` 单层防御；所有「跨租户隔离」测试（`cross_tenant_claim_returns_nothings`、`dispatch_loop_isolates_tenants_end_to_end`）测的都是这一层。§6.2「禁止跨租户回退」的**纵深防御实际不存在**——任何应用层 SQL bug（漏 WHERE / join 漏 tenant）都会直接跨租户泄露。
- **根因（关键技术现实）**：adapter 当前是 `NamedParameterJdbcTemplate` + **纯 auto-commit 单语句、无任何事务管理**（`@Transactional`/`TransactionTemplate`/`PlatformTransactionManager` 全无）。`SET LOCAL`（事务级）在 auto-commit 下每语句独立提交、立即失效，**无法生效**。要让 RLS 接线，必须先引入事务边界。**这是 agent-bus 首次引入事务管理**，把 adapter 从 auto-commit 改为事务化——必要的架构前提，不是可选优化。
- **核心设计（5 点）**：(1) 引入编程式事务 `TransactionTemplate`（不用 `@Transactional`——adapter 是 POJO，测试直接 `new JdbcForwardingOutbox(dataSource)` 不经容器）；(2) 抽 `withTenant(tenantId, Supplier)` helper，每端口方法事务内 `set_config('app.tenant_id', :tenantId, true)`（≡ SET LOCAL，支持命名参数绑定防注入）；(3) **不改 migration**（不加 FORCE 避免破坏 7 个 superuser IT、不加 WITH CHECK 最小 footprint、V1 零改）；(4) 双构造器向后兼容——旧 `(DataSource)` 委托 `(DataSource, new DataSourceTransactionManager(dataSource))`，现有所有 IT 零改动；(5) RLS 生效 IT 用「受限 `app_role` 下 adapter 业务路径 green」反证接线（不接线则 RLS fail-closed 断 claimDue 返回空）。
- **§6.2 守恒**：`set_config('app.tenant_id',…)` 是 session/transaction 设置，**非 payload body / token stream / Task execution state / concrete broker client**。接线**强化**跨租户隔离（反方向于「跨租户回退」），不触发任何 §6.2 禁项。
- **预计**：195 → 200 tests green（+2 RLS 接线 IT + 3 接线契约测试），ArchUnit green（spring 事务管理圈在 `persistence.jdbc` 子包，现有规则不破），**有生产代码改动**（首次事务管理）。

---

## §1 Stage 23 评审

### 1.1 已落地

`C3ForwardingPayloadRefIntegrationTest`（场景 A，新 IT）+ `A2aForwardingDeliveryPortMockWebServerTest` 扩展（场景 B，+2 transport 测试 + 1 helper），纯测试无生产代码，`@Isolated`，不 boot runtime：

- **场景 A — DATA_BEARING payloadRef outbox 持久化端到端**：DATA_BEARING envelope + embedded-postgres + Flyway + observing fake delivery port；断言 payloadRef 从 envelope → record → PG `payload_ref` 列（raw JDBC 投影读）→ record.payloadRef()（SqlCodec decode）→ delivery port **一路 round-trip 不变**，ACK 后列存活（markAcked 不清）。闭合 Stages 17–22 全 CONTROL_ONLY 的持久化盲区。
- **场景 B — payloadRef → A2A metadata 传输边界**：`A2aForwardingDeliveryPort + MockWebServer`；`deliver_carries_payload_ref` 断言 DATA_BEARING body 含 `payloadRef` / `deliver_omits_payload_ref` 断言 CONTROL_ONLY 省略（两分支对称即 §6.2 data-reference 契约）。闭合 Stage 15 transport 盲区（`toMessageSendParams` line 230-232 首次被断言）。

### 1.2 测试与提交

- 195 tests green（Stage 22 的 192 + 3：场景 A +1 IT、场景 B +2 transport 测试），ArchUnit green。
- commit `e827291c`（experimental，**未 push**，PAT 过期待用户 `! git push origin experimental`）。

### 1.3 关键发现（payloadRef 持久化完整 vs PAYLOAD_REF_INVALID 触发源缺失）

- **payloadRef 持久化是完整的**（与 Stage 22 EXPIRED「触发源缺失」根本不同）：envelope → record → DDL `payload_ref VARCHAR(1024)` → SqlCodec encode/decode 全实现，Stage 23 无需任何注入，payloadRef 在真实链自然流动。
- **PAYLOAD_REF_INVALID 失败码未接线**：`ForwardingFailureCode.PAYLOAD_REF_INVALID`（NON_RETRYABLE）只有枚举定义，无生产代码返回它——类似 Stage 22 EXPIRED「触发源缺失」，deferred。
- **A2A 接收端未提取 payloadRef**：agent-runtime `A2aJsonRpcController` 未显式提取 metadata 里的 payloadRef——agent-runtime 职责，超 agent-bus 边界，deferred。

### 1.4 deferred 结转（Stage 23 → 后续）

PAYLOAD_REF_INVALID 接线 / 接收端 handler 处理 payloadRef（agent-runtime 职责）/ payloadPolicy 持久化（可选 schema 变更）/ 真实 agent handler / registry 集成 resolver 生产实现 / 断路器状态持久化 / 连接池治理 / 真实 scheduler + polling cadence / push-pull-MQ 最终裁决（H2/H3）。

---

## §2 Stage 24 范围与设计

### 2.1 为什么是 RLS 接线（债务分析）

agent-bus 的 forwarding 设计在 §7.3 配置了跨租户纵深防御：V1 migration（`V1__create_agent_bus_forwarding_outbox_inbox.sql:86-94`）有 `ENABLE ROW LEVEL SECURITY` + `CREATE POLICY ... USING (tenant_id = current_setting('app.tenant_id', true))`。fail-closed——未设 tenant 时 `current_setting('app.tenant_id', true)` 返回 NULL，`USING(NULL=...)` 永假，行不可见。L2 `forwarding-persistence §273` 明写「`app.tenant_id` 由应用连接设置」。

但 Stage 23 收尾调查的 `grep` 暴露真相：

| 防御层 | 配置状态 | 激活状态 |
|---|---|---|
| §7.3 RLS policy（`USING tenant_id = app.tenant_id`） | ✅ V1 migration 已配（Stage 12） | ❌ **从未激活**（无人设 `app.tenant_id`） |
| 应用层 `WHERE tenant_id = :tenantId` | ✅ 每端口方法 SQL 都有 | ✅ **唯一**在生效的隔离 |
| 受限角色（`app_role`，受 RLS 约束） | ❌ 生产无 | ❌ 生产用 owner/superuser（bypass RLS） |

**核心论点**：RLS 是「armed but not wired」。policy 写好了、migration 跑了、fail-closed 语义正确，但**生产 adapter 从不设置 `app.tenant_id`**，所以 `current_setting('app.tenant_id', true)` 恒为 NULL，RLS policy 恒过滤掉所有行——但因为是 owner 连接（bypass RLS），policy 根本不评估，**等同于 RLS 不存在**。当前所有跨租户隔离测试（`cross_tenant_claim_returns_nothings`、`dispatch_loop_isolates_tenants_end_to_end`、ForwardingJdbcIntegrationTest 的 tenant 隔离用例）测的都是应用层 WHERE 这一层。任何应用层 SQL bug（漏 WHERE / join 漏 tenant）都会直接跨租户泄露，纵深防御不存在。这是 Stage 12 留的债务的直接定义。

### 2.2 根因：为什么必须引入事务管理（不能只加一行 SET）

直觉方案是「adapter 每个方法前加一行 `SET LOCAL app.tenant_id`」。但这**不工作**，因为：

> adapter 当前是 `NamedParameterJdbcTemplate` + **纯 auto-commit 单语句**。auto-commit 下每个语句独立提交。`SET LOCAL`（事务级）在语句结束时立即提交失效——设完紧接着的业务 SQL 已经在**下一个事务**里，`app.tenant_id` 已重置。**SET LOCAL 在 auto-commit 下永远活不过一条语句。**

要让 `SET LOCAL` 覆盖业务 SQL，必须有一个**显式事务边界**把「SET + 业务 SQL」包在一起。即 adapter 必须**从 auto-commit 改为事务化**。这是 RLS 接线的**必要架构前提**。

可选事务管理方案：

| 方案 | 适配性 | Stage 24 选择 |
|---|---|---|
| `@Transactional`（声明式） | 需 Spring 代理 + bean 扫描；adapter 是 POJO，测试直接 `new JdbcForwardingOutbox(dataSource)` 不经容器 → **不工作** | ❌ |
| `TransactionTemplate`（编程式） | POJO 友好，构造器注入 `PlatformTransactionManager`，与现有「构造器注入 DataSource」风格一致，事务管理显式圈在 `persistence.jdbc` 子包 | ✅ |

选 `TransactionTemplate`（编程式）。ArchUnit 已允许 `org.springframework..` 进 `persistence.jdbc` 子包（Stage 12 精确化），`AgentBusForwardingSpiPurityTest` 规则不破。

### 2.3 核心设计（5 点）

**(1) 引入编程式事务 `TransactionTemplate`**

- `JdbcForwardingOutbox` / `JdbcForwardingInbox` 新增字段 `private final TransactionTemplate txTemplate;`。
- 新增全参构造器 `(DataSource dataSource, PlatformTransactionManager txManager)` → `this.txTemplate = new TransactionTemplate(txManager);`。
- 现有 `(DataSource)` 构造器改为委托：`this(dataSource, new DataSourceTransactionManager(dataSource));`（`DataSourceTransactionManager` 在 `org.springframework.jdbc.datasource`，spring-jdbc 工件）——**向后兼容，现有所有 IT（`new JdbcForwardingOutbox(dataSource)`）零改动**，默认走事务路径。

**(2) `withTenant(tenantId, Supplier)` helper 统一收口**

抽一个私有 helper 把「事务 + 设 tenant」收口，避免 13 个方法各自膨胀：

```java
private <T> T withTenant(String tenantId, Supplier<T> work) {
    return txTemplate.execute(status -> {
        // set_config(setting, value, is_local=true) ≡ SET LOCAL；
        // 返回文本（结果集）→ 必须 queryForObject 消费，update 会拒绝返回的结果集。
        // 支持命名参数绑定 → 防租户 ID 注入。事务结束自动重置 → 无连接池污染。
        jdbc.queryForObject("SELECT set_config('app.tenant_id', :tenantId, true)",
                new MapSqlParameterSource("tenantId", tenantId), String.class);
        return work.get();
    });
}
```

- 用 PostgreSQL 内置 `set_config('app.tenant_id', :tenantId, true)`（`is_local=true` ≡ `SET LOCAL`），而非字符串拼接 `SET`——支持命名参数绑定，杜绝 tenantId 注入。
- `set_config` **返回文本**（结果集）→ 必须 `queryForObject(..., String.class)` 消费，不能用 `update`（update 拒绝返回的结果集 → `DataIntegrityViolationException`）。该值被丢弃。
- `Supplier<T>` 不抛 checked exception；Spring JDBC 的 `DataAccessException`（RuntimeException）自然传播出 `txTemplate.execute` 触发回滚。
- 每个端口方法体改为 `return withTenant(tenantId, () -> { …现有 SQL 逻辑… });`：
  - Outbox（9 方法）：`enqueue`（取 `envelope.tenantId()`）/ `markAcked` / `scheduleRetry` / `moveToDlq` / `markExpired` / `statusOf` / `claimDue` / `renewLease` / `releaseLease`（均显式 tenantId 参数）
  - Inbox（4 方法）：`receive`（取 `envelope.tenantId()`）/ `markConsumed` / `markRejected` / `statusOf`
- 事务结束后 `SET LOCAL` 自动恢复 → **无连接池污染**（解决 session-level `SET` 跨请求复用污染问题）。
- 副作用红利：`leaseGuardedUpdate` / `mutate` 失败诊断路径（UPDATE + 诊断 SELECT）从此在同事务同连接，修复当前跨连接的潜在隐患。

**(3) 不改 migration（不加 FORCE / 不加 WITH CHECK）—— 最小 footprint**

- **不加 `FORCE ROW LEVEL SECURITY`**：FORCE 让 owner/superuser 也受 RLS 约束，则所有 7 个 C3 IT（superuser 连接、不设 tenant）会因 fail-closed 100% 失败。保持 `ENABLE`（owner bypass）—— production 用受限角色才受约束，测试 superuser 不受影响。
- **不加 `WITH CHECK`**：当前 policy 只有 `USING`（管可见性）。加 `WITH CHECK` 会校验写入（INSERT/UPDATE 时 `tenant_id = app.tenant_id`），是更强的写越权防护，但应用层 `WHERE tenant_id` 已防写越权。保持只有 `USING`——读越权由 RLS 防御，写越权由应用层 WHERE 防御，两层分工。**V1 migration 零改动**。

**(4) 双构造器向后兼容（现有 IT 零改动）**

现有 IT 用 superuser DataSource + 旧构造器 `new JdbcForwardingOutbox(dataSource)` → 委托新构造器 → 内部 `DataSourceTransactionManager` + `TransactionTemplate`。每方法走事务 + SET LOCAL。但 superuser **bypass RLS** → SET LOCAL 设了也不影响 → 业务 SQL 用 `WHERE tenant_id` 正常 → **全 green**。多 worker 并发（Stage 21）：每个 `claimDue` 是独立 `TransactionTemplate.execute`（独立事务），`FOR UPDATE SKIP LOCKED` 跨事务正常 → green。事务隔离默认 READ_COMMITTED，与 auto-commit 每语句独立 READ_COMMITTED 语义一致 → green。

**(5) RLS 生效验证 IT：受限角色 + adapter 业务路径**

关键洞察：应用层 `WHERE tenant_id` 总是先于 RLS 过滤，**通过 adapter 业务路径无法隔离 RLS 的独立贡献**（被应用层 WHERE 掩盖）。所以接线证据用「受限角色下 adapter 业务路径 green」作间接但强力反证：

- 若 adapter **不接线** SET LOCAL：受限角色（`app_role`，受 RLS）调 `claimDue` 时 `current_setting('app.tenant_id')` 为 NULL → RLS `USING` 永假 → fail-closed → **claimDue 看不到刚 enqueue 的行 → 返回空 → 业务断裂**。
- 若 adapter **接线** SET LOCAL：claimDue 事务内设 `app.tenant_id=A` → RLS `USING(A)` 匹配 → 看到 A 行 → 业务 green。

「app_role + adapter 全流程 green」**等价于接线生效的反证**。

### 2.4 场景 A：受限角色下 adapter 业务路径 green（接线反证）

**目标**：证明 adapter 接线 SET LOCAL 让受限 `app_role` 下 outbox 全业务路径 green（green ⟺ 接线生效）。

**文件**：`agent-bus/src/test/java/com/huawei/ascend/bus/forwarding/runtime/C3ForwardingRlsWiringIntegrationTest.java`（新文件，`@Isolated`，不 boot runtime）。

**boot recipe**（复用 Stage 17–23 embedded-postgres + Flyway，superuser 起；扩 app_role + GRANT DML + app_role 专用 DataSource）：

```java
@Isolated @BeforeAll bootPostgres() {
    pg = EmbeddedPostgres.builder().start();
    superuserDs = pg.getPostgresDatabase();
    Flyway.configure().dataSource(superuserDs).load().migrate();
    // superuser 起 app_role + GRANT SELECT/INSERT/UPDATE（扩现有 ForwardingJdbcIntegrationTest 只 SELECT 的 GRANT，因走 DML 业务路径）
    try (Connection c = superuserDs.getConnection(); Statement s = c.createStatement()) {
        s.execute("CREATE ROLE app_role");
        s.execute("GRANT SELECT, INSERT, UPDATE ON agent_bus_forwarding_outbox, agent_bus_forwarding_inbox TO app_role");
    }
    // app_role 专用 DataSource：每连接 SET ROLE app_role（test-only，使该连接 current_user=app_role → RLS 绑定）
    appRoleDs = new SetRoleDataSource(superuserDs, "app_role");
    outbox = new JdbcForwardingOutbox(appRoleDs);  // 旧构造器，委托新构造器 → 默认事务路径
}
```

**反证逻辑**：adapter(app_role) `enqueue(tenant-A)` → `claimDue(tenant-A)` → `markAcked` 全 green。green 即证明接线 SET LOCAL 生效（否则 RLS fail-closed 断 claimDue 返回空）。Inbox 同路径（`receive` → `markConsumed`）。

### 2.5 场景 B：裸 SQL RLS 过滤对照（纵深防御可见）

**目标**：用 app_role 连接 + 手动 `SET app.tenant_id` + **裸 SQL** `SELECT count(*)`（绕应用层 WHERE）证明 RLS policy 真的过滤，作场景 A 的对照组。

**复用 `ForwardingJdbcIntegrationTest.visibleOutboxCount` 裸 SQL 范式**（line 380-401）：

```java
// 场景 B 对照：设 tenant-A → count=1；设 tenant-B → count=0（A 行被 RLS 过滤）；不设 → count=0（fail-closed）
try (Connection c = appRoleDs.getConnection(); Statement s = c.createStatement()) {
    s.execute("SET app.tenant_id = 'tenant-A'");
    assertThat(visibleOutboxCountUnset(c)).isEqualTo(1);  // RLS USING(A) 匹配 → 可见
    s.execute("SET app.tenant_id = 'tenant-B'");
    assertThat(visibleOutboxCountUnset(c)).isEqualTo(0);  // RLS USING(B) 不匹配 → 过滤
}
```

**断言**：设 tenant→可见 / 他 tenant→0 / 不设→0（fail-closed）。证明 RLS policy 在受限角色下真正生效（与场景 A 的 adapter 业务路径 green 互补：A 证接线、B 证 RLS 真过滤）。

### 2.6 边界 + ArchUnit + §6.2

| 护栏 | Stage 24 状态 |
|---|---|
| 不 boot runtime | ✅ 两场景都 embedded-postgres + Flyway，无 runtime boot |
| 生产依赖边界 | ✅ 不新增模块依赖（仅 spring-jdbc 已有的 `DataSourceTransactionManager` + spring-tx 的 `TransactionTemplate`，均在 `persistence.jdbc` 子包内）；`AgentBusDependencyBoundaryTest.bus_does_not_depend_on_agent_runtime` 仍 green |
| §6.2 守恒 | ✅ `set_config('app.tenant_id',…)` 是 session/transaction 设置，非 payload body / token stream / Task state / concrete broker；接线**强化**跨租户隔离 |
| ArchUnit | ✅ spring 事务管理圈在 `persistence.jdbc` 子包（Stage 12 已允许 `org.springframework..`）；无新豁免 |
| migration 零改 | ✅ 不加 FORCE / WITH CHECK / 新列；V1 不动 |
| superuser IT 兼容 | ✅ 双构造器委托，superuser bypass RLS，7 个 C3 IT + ForwardingJdbcIntegrationTest 全 green 零改动 |
| 4+1 视图回灌 | ✅ 按 [[agent-bus-4plus1-view-rebound]] 清单，7 L1 视图 + 2 L2 + ICD + yaml + decision 全同步 |

---

## §3 关键发现（前置分析）

| # | 发现 | 影响 |
|---|---|---|
| F1 | **RLS armed but not wired**：V1 配了 RLS policy + fail-closed，但生产无任何 `app.tenant_id` 设置 → policy 从不激活（owner 连接 bypass） | Stage 24 核心债务；纵深防御实际不存在 |
| F2 | **SET LOCAL 在 auto-commit 下不生效**：adapter 纯 auto-commit 单语句，SET LOCAL 活不过一条语句 | 接线必须先引入事务边界 → 首次事务管理，不可避 |
| F3 | **应用层 WHERE 掩盖 RLS**：通过 adapter 业务路径无法隔离 RLS 独立贡献 | 验证用「受限角色业务路径 green」反证接线 + 裸 SQL 对照证 RLS 过滤 |
| F4 | **adapter 是 POJO**：测试直接 `new`，不经 Spring 容器 → `@Transactional` 不工作 | 选 `TransactionTemplate`（编程式）而非声明式 |
| F5 | **`set_config` 返回文本（结果集）**：不能 `update`（拒绝结果集），必须 `queryForObject(..., String.class)` 消费 | 实现陷阱；值被丢弃 |
| F6 | **FORCE 会破坏 superuser IT**：FORCE 让 owner 也受 RLS → 7 个 superuser IT fail-closed 100% 失败 | 保持 ENABLE（owner bypass），不加 FORCE |
| F7 | **双构造器向后兼容**：旧 `(DataSource)` 委托 `(DataSource, new DataSourceTransactionManager(dataSource))` | 现有所有 IT 零改动；默认走事务路径 |
| F8 | **事务结束 SET LOCAL 自动重置**：无连接池污染（解决 session-level SET 跨请求复用污染） | 编程式事务 + SET LOCAL 的天然红利 |
| F9 | **副作用红利**：`leaseGuardedUpdate`/`mutate` 诊断路径（UPDATE+诊断 SELECT）从此同事务同连接 | 修复当前跨连接潜在隐患 |
| F10 | **§6.2 守恒自证**：`app.tenant_id` 是 session 设置非 payload；接线强化跨租户隔离 | 反方向于「跨租户回退」，非 §6.2 违反 |

---

## §4 切片 + MI 表

| MI | 切片 | 产出 |
|---|---|---|
| MI24-001 | 0 治理落位 | decision §8 加 Stage 24 许可段（正向：闭合 §6.2 跨租户纵深防御、接线 RLS、首次引入事务管理；反向：§6.2 强化非违反、不加 FORCE 避免破坏 IT、不加 WITH CHECK 最小 footprint）；yaml 加 `stage24_scope`（delivers: rls-wired-tenant-context, transactional-adapter；not_delivers: force-rls, with-check-policy, connection-pool-governance）；ICD 边界标题 + 边界条 + Open Issue（RLS armed-but-not-wired → wired）。 |
| MI24-002 | 1 Outbox 接线 | `JdbcForwardingOutbox`：加 `TransactionTemplate` 字段 + `(DataSource, PlatformTransactionManager)` 全参构造器 + `(DataSource)` 委托构造器 + `withTenant` helper + 9 个端口方法改 `withTenant(tenantId, () -> …)`。 |
| MI24-003 | 1 Inbox 接线 | `JdbcForwardingInbox`：同 Outbox 模式 + 4 个端口方法接线。 |
| MI24-004 | 2 接线契约测试 | `AgentBusForwardingRuntimeContractTest` 加 Stage 24 节：断言 adapter 方法在事务内执行（observing DataSource 包装捕获 `set_config('app.tenant_id', ...)` SQL 执行，证明接线）；断言旧 `(DataSource)` 构造器向后兼容（仍可 new、仍 green）。 |
| MI24-005 | 2 RLS 生效 IT | 新文件 `C3ForwardingRlsWiringIntegrationTest`：app_role + GRANT DML + app_role DataSource + 场景 A（adapter 业务路径 green 反证接线）+ 场景 B（裸 SQL RLS 过滤 fail-closed）。`@Isolated`，embedded-postgres + Flyway，不 boot runtime。 |
| MI24-006 | 3 ArchUnit + 回归 | `AgentBusForwardingSpiPurityTest` 现有规则 green（事务管理圈在 persistence.jdbc，`org.springframework.transaction`/`jdbc.datasource` 允许进该子包，无需新规则）；现有 7 个 C3 IT + ForwardingJdbcIntegrationTest 全 green（superuser bypass，零改动）。 |
| — | 4 文档同步 | decision §8 + yaml `stage24_scope` + ICD（边界标题/边界条/Open Issue）+ L2 `forwarding-persistence` §7.3（从「armed but not wired」更新为「wired by Stage 24 adapter set_config in-tx」）+ L1 7 视图（README/physical/logical/process/development/scenarios/ARCHITECTURE）按 [[agent-bus-4plus1-view-rebound]] 回灌清单 + 双语 plan（本文）。 |
| — | 5 构建验证 + 提交 | `mvn -f .../agent-bus/pom.xml test -s ~/.m2/settings.xml -B`；断言 195→200 tests green，ArchUnit green；commit experimental + 提示用户 push。 |

---

## §5 deferred + 风险

### 5.1 deferred（Stage 24 不触及，记录为后续）

- **FORCE RLS（owner 也受约束）**：FORCE 的价值是防 owner 越权，但会破坏所有 superuser IT。production 部署角色治理（强制用受限角色连接、禁 owner 直连）deferred。
- **WITH CHECK（防写越权）**：应用层 `WHERE tenant_id` 已防写越权；加 WITH CHECK 是更强但更复杂的双层防护，deferred。
- **app_role 生产部署模型**：Stage 24 让 adapter 接线（设 `app.tenant_id`），但 production 实际用受限角色连接（使 RLS 生效）属部署配置，本阶段只验证（测试用 app_role），不强制 production 部署模型。
- **连接池治理**：HikariCP 等连接池配置（ArchUnit 禁 `com.zaxxer.hikari`）仍 deferred。事务化使每端口方法事务期间持有连接（事务极短），production 部署需注意连接池容量。
- **不裁决 push/pull/MQ**：Stage 24 是 persistence 层 RLS 接线，与 transport 投递模型正交，不触及 Stage 13 的 H2/H3 裁决。
- **沿用 Stages 15–23 deferred**：PAYLOAD_REF_INVALID 接线 / EXPIRED 真实触发源 / payloadPolicy 持久化 / 真实 agent handler / registry resolver / 真实 scheduler / push-pull-MQ —— 均不变。

### 5.2 风险

- **首次引入事务管理的运行模型变化**：adapter 从 auto-commit 改为事务化是架构里程碑。事务边界、连接持有时长变化——虽对现有 IT 兼容（superuser bypass + READ_COMMITTED 语义一致 + 独立事务 SKIP LOCKED 正常），但 production 部署需复核连接池容量（事务期间持有连接，但事务极短：SET + 单业务 SQL）。
- **§6.2 守恒自证**：`app.tenant_id` 是 session/transaction 设置非 payload body；接线强化跨租户隔离（反方向于「跨租户回退」），自证非 §6.2 违反。
- **接线契约 + RLS IT 暴露的潜在缺陷**：若接线后受限角色业务路径非 green，说明接线或 RLS policy 有真实缺陷（这正是验证的价值）。本阶段性质允许修复生产代码（接线就是生产改动），不再受「纯测试」约束。

---

## 相关文档

- Stage 23 计划（本文评审对象）：[`agent-bus-stage22-review-and-stage23-plan`](agent-bus-stage22-review-and-stage23-plan.md)
- 运行态裁决：[`agent-bus-forwarding-runtime-decision`](../review-packets/agent-bus-forwarding-runtime-decision.md) §8
- 运行态 ICD：[`ICD-agent-bus-forwarding-runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)
- forwarding ICD（HD4/payloadRef）：[`ICD-agent-bus-forwarding`](../../05-contracts/human-readable/ICD-agent-bus-forwarding.md)
- L2 持久化（§7.3 RLS）：[`forwarding-persistence`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)
- yaml（machine-readable）：[`agent-bus-forwarding-runtime.v1`](../../05-contracts/machine-readable/agent-bus-forwarding-runtime.v1.yaml)
