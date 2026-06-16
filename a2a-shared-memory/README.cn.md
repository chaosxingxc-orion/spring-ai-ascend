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

## 权限权威 = `MemoryPrincipal`(不在 runtime)

记忆访问的**权威是认证后的 `MemoryPrincipal`(tenant + user + agent)**,在认证边界签发、向内转发;**agent-runtime 只是携带者,不是权威**。本模块主代码**对 agent-runtime 零依赖**——把"运行态上下文 → MemoryPrincipal + A2A contextId"的映射放在**调用方**(见测试)。

- `tenant` = 隔离边界;`user` = 私有记忆权威(agent 代用户行事);`agent` = 共享黑板写入归属。

```java
// 调用方转发已认证 principal + A2A contextId:
var board = A2aSharedMemory.forCollaboration(principal, contextId, store);
board.put("riskAssessment", json);   // 归属 principal.agentId()
board.get("loanDecision");           // 读别的 agent 的结论(自动记 READ 依赖边)
```

## 团队交互记录(谁对谁做了什么)

各 agent 只写自己的结论,**agent 之间的交互边由协作记录捕获**:`get` 自动记 **READ 依赖边**(reader → owner / key);Coordinator 记 **DISPATCH / HANDOVER / VALIDATE / OUTCOME**(`recordInteraction` / `interactions()`)。这就是"团队记忆"——单个 agent 的结论里没有的"协同图"。

经验沉淀由协作引擎在 **run 结束钩子**触发(`CollaborationMemoryHook`),依赖方向 协作 → 本模块。

## 鲁棒 / 韧性 / 幂等

- **幂等写**:`put(key, value, idempotencyKey)`——重试同一 idempotencyKey **不重复追加**(`IdempotencyTest`)。
- **反压**:`BoundedSharedMemoryStore` 装饰器——有界在途许可 + 获取超时,过载即 `BackpressureRejectedException` 甩载(负反馈),并计数 + 上报观测(`BoundedSharedMemoryStoreTest`)。保护下游引擎不被流量打爆。
- **所有权/后端错均上抛**(权限错≠基础设施错),交 kit/协作 reclaim。
- **失联取代(supersede)**:owner 不可用时,非 owner 可 `supersedeUnavailable(key, value, reason)` 接管——**追加新版本 + 转移 owner,保留前 owner 历史(不篡改)**,并记入交互记录(`SupersedeTest`)。"做过什么(记忆)"与"现在谁能做(健康)"分开。

## 经验保鲜

跨 run 经验会**保鲜**(`ExperienceFreshnessTest`):同一事实**再确认即刷新**(reinforcement + recency,不重复)、`reinforce` 标记有用性、召回按 **相似度 → 有用性 → 新近** 排序、按租户**有界淘汰**(低 reinforcement/旧 的先出局)。

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

**46/46 通过**:A2A 绑定(principal 鉴权 + 按 contextId 共享 + 所有权 + 交接 + 隔离 + **读归因**)、**真实 A2A over-the-wire e2e**(`A2aSharedMemoryWireTest`:启真实 runtime,经 A2A JSON-RPC 线 PUT/GET、所有权拒写、跨 contextId 隔离)、黑板(所有权/append-log/并发/**幂等**/**失联取代**)、**反压**(有界+甩载)、**经验保鲜**(再确认刷新/有用性排序/有界淘汰)+ 召回/脱敏/租户隔离/跨 run 生命周期、PII redactor、run-end hook、可观测(级别路由/扇出/MDC)、规模(2000 协作并发)、**经济性 eval**(省 ~67%)、**性能基准**(~130 万 ops/s)。
