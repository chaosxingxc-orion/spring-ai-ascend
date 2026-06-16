# a2a-shared-memory —— A2A 多智能体共享记忆中间件(kit)

A2A 协作里 agent 之间**共享记忆**的中间件,`kit` 范式(门面 + 可组合 rail + 安全默认 + **可插拔后端 SPI**)。**独立模块**(与 agent-runtime 同级,不在根 reactor),包 `com.huawei.ascend.a2a.memory`。

- **不依赖 MemOpt**:MemOpt 是另一个 kit 中间件,以后作为后端之一插在 `SharedMemoryStore` SPI 之后。
- **不依赖协作引擎(279)**:通过单向 hook SPI 集成;依赖方向 协作 → 本模块。
- 权威设计见 [the design decision](../docs/logs/reviews/2026-06-16-a2a-shared-memory-design-decision.yaml)。

## 两层结构

1. **run 内黑板**(working memory):一次协作里 agent 边跑边共享的上下文。key = `tenantId + A2A contextId`。
   - **所有权写入**:key 归首次写入的 `writerAgentId`;**仅 owner 可改**,他人只读;非 owner 写 → `OwnershipViolationException`。交接 A→B 后,B 写自己的新 key,A 的 key 仍只读。
   - **append-log + 出处**:每次写追加 (value, writerAgentId, version, ts),读默认取最新、可拉历史;不静默覆盖,可审计。
2. **跨 run 经验**(experience):把"有效的协作模式/结论"蒸馏成持久经验,供后续协作召回。key = `tenantId + signature`(signature = 能力组合 + 任务类型),**不按 user**;**record 前强制剥离用户 PII**。

## 接入 A2A(按 contextId)

```java
// 在 A2A agent 的 execute(ctx) 里:
var board = A2aSharedMemory.forContext(ctx, store);   // 绑到本次协作的 A2A contextId,写入归属当前 agent
board.put("riskAssessment", json);                    // risk-agent 写自己的结论
board.get("loanDecision");                            // 读别的 agent 的结论
```

`A2aSharedMemory.forContext` 把黑板 key 到 `RuntimeIdentity.sessionId()`(= A2A contextId)+ tenant,写入归属 `RuntimeIdentity.agentId()`。这是**唯一**触碰 agent-runtime 的地方(provided),kit 核心保持 agent 中立。

经验沉淀由协作引擎在 **run 结束钩子**触发(`CollaborationMemoryHook`),依赖方向 协作 → 本模块。

## 鲁棒 / 韧性 / 幂等

- **幂等写**:`put(key, value, idempotencyKey)`——重试同一 idempotencyKey **不重复追加**(`IdempotencyTest`)。
- **反压**:`BoundedSharedMemoryStore` 装饰器——有界在途许可 + 获取超时,过载即 `BackpressureRejectedException` 甩载(负反馈),并计数 + 上报观测(`BoundedSharedMemoryStoreTest`)。保护下游引擎不被流量打爆。
- **所有权/后端错均上抛**(权限错≠基础设施错),交 kit/协作 reclaim。

## 经济性 & 性能

- **经济性 eval**(`EconomyEvalTest`):K=5、20 轮的确定性模型,共享黑板让下游 agent 读上游结论而非重推 →**省 ~67% 推导(token 代理量)**。
- **性能基准**(`PerfBenchmarkTest`):进程内黑板 put+get **~130 万 ops/s**,p50≈0µs、p99≈1µs(运行态 kit 层非瓶颈;真持久/召回性能属后端)。

## 可插拔后端 & 规模

`SharedMemoryStore` / `ExperienceStore` SPI。本模块自带 **in-process 默认实现**(离线可评测;`ScaleTest` 验证**单 JVM 内 2000 并发协作**零跨域泄漏)。任何其它后端(如 redis,或 MemOpt 引擎)可实现同一 SPI 接入——**本模块不规定后端如何部署**(进程内/远程/容器化由各后端自定);真分布式/分片扩展、MemOpt 引擎本体**另开任务**,不在本模块。

## 横切 rail(开箱即用的企业级能力)

- 所有权 / append-log(`shared/`)
- 经验 PII 脱敏(`privacy/` `PiiRedactor`)
- 双模可观测(`obs/`:`MemoryObserver` + Slf4j 双模 routine→DEBUG/verbose→INFO/问题→WARN + Micrometer `a2amem.*` + 组合故障隔离;接入 `SharedMemoryKit`)

## 构建 & 测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home \
  ./mvnw -f a2a-shared-memory/pom.xml test
```

**38/38 通过**:A2A 绑定(agent 间按 contextId 共享 + 所有权 + 交接 + 隔离,用真实 agent-runtime context)、**真实 A2A over-the-wire e2e**(`A2aSharedMemoryWireTest`:启真实 runtime,经 A2A JSON-RPC 线 PUT/GET、所有权拒写、跨 contextId 隔离)、黑板(所有权/append-log/并发/**幂等**)、**反压**(有界+甩载)、经验(召回/脱敏/租户隔离/**跨 run 生命周期**)、PII redactor、run-end hook、可观测(级别路由/扇出/MDC)、规模(2000 协作并发)、**经济性 eval**(省 ~67%)、**性能基准**(~130 万 ops/s)。
