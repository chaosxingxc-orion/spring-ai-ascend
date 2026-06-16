# A2A 共享记忆(#283)五维复检(鲁棒 / 弹性 / 韧性 / Ops·性能 / 经济)

日期:2026-06-16 · 范围:**`a2a-shared-memory/`** 模块(run 内黑板 + 跨 run 经验 + A2A contextId 绑定)· 关联 [the a2a-shared-memory design decision](2026-06-16-a2a-shared-memory-design-decision.yaml) · **46/46 测试通过**

> **权威已修正(同事评审)**:记忆访问权威 = 认证 `MemoryPrincipal`(tenant+user+agent),在边界签发、向内转发;**agent-runtime 只是携带者**。模块主代码**对 runtime 零依赖**(runtime→principal 映射在调用方/test)。
>
> MemOpt(闭源记忆引擎 + per-user)**不在 #283**,另开任务做;它将作为 `SharedMemoryStore` 的可插拔后端接入。本复检只覆盖 #283 的 A2A 共享记忆中间件 kit。
>
> 诚实前提:本模块是 **in-process Java 形态**(可离线评测、可对真实 A2A 线 e2e)。真分布式/分片,以及任何远程/持久后端(如 redis 或 MemOpt 引擎)的选型与部署,是后端阶段、**另开任务**;**本模块不规定后端部署形态**(容器化等是各后端自己的事)。明确标注未做、非缺陷。

## 记分卡

| 维度 | 状态 | 证据(测试)/ 边界 |
|---|---|---|
| **鲁棒性** | ✅ | 所有权违例 surface 不吞;后端错 surface 交协作 reclaim;append-log 不静默覆盖、并发原子;**幂等写**(重试不重复追加);**失联取代**(owner 不可用→supersede:追加新版+转移 owner+留历史+记审计,不篡改)。测试:`SharedMemoryKitTest` / `SharedMemoryConcurrencyTest` / `IdempotencyTest` / `SupersedeTest` / `A2aSharedMemoryTest`。 |
| **权威/隔离** | ✅(同事点) | 权威 = 认证 `MemoryPrincipal`,**不在 runtime**;主代码零 runtime 依赖;租户隔离 + 所有权由记忆层强制;团队交互记录含**读归因**(reader→owner 边)。 |
| **弹性(上千 A2A)** | ✅ 进程内已验证 | 单一**按协作分区**的共享存储(非每协作一结构),`ConcurrentHashMap` 可水平扩。`ScaleTest`:**2000 并发协作**零跨协作泄漏、零竞争。边界:**真跨进程/分片**的分布式扩展属后端(redis / 闭源引擎),本模块未含。 |
| **韧性(流量过大反压)** | ✅ | `BoundedSharedMemoryStore`:有界在途许可 + 获取超时,**过载即甩载**(`BackpressureRejectedException`,负反馈),计数 + 观测上报。`BoundedSharedMemoryStoreTest`:满载拒绝、欠载放行。 |
| **Ops 可观测 + 性能** | ✅ | 双模观测(`Slf4jMemoryObserver` routine→DEBUG/verbose→INFO/问题→WARN,`isEnabled` 守卫 + MDC finally 清;`MicrometerMemoryObserver` `a2amem.*` 低基数;组合故障隔离),接入 `SharedMemoryKit`(`MemoryObserverTest`)。性能:`PerfBenchmarkTest` 进程内 put+get **~130 万 ops/s**,p50≈0µs/p99≈1µs。 |
| **经济性(token 节省)** | ✅ 已量化 | `EconomyEvalTest`:确定性模型,共享黑板让下游 agent 读上游结论而非重推 → **省 ~67% 推导**(token 代理量);跨 run 经验召回避免重推有效模式;经验蒸馏 + PII 脱敏只留要点。 |

## 真实 A2A 端到端(核心)

`A2aSharedMemoryWireTest`:启**真实 agent-runtime**(随机端口,no-LLM agent),经**真实 A2A JSON-RPC 线**:同一 `contextId` 上 `PUT`→`GET` 跨调用共享、非 owner 写被拒(`DENIED`)、跨 `contextId` 隔离(`MISS`)。证明"A2A 智能体之间共享记忆"在真实协议上成立,无需 API key。
跨 run 经验:`ExperienceLifecycleTest` 模拟 coordinator run-end 调 hook → 后续协作召回前次经验(且租户隔离)。

## 本期明确未做(后端/部署阶段,非缺陷)
- 任何远程/持久后端(redis 或 MemOpt 引擎)的本体与部署——属各后端任务,**A2A 共享记忆设计不规定其形态**(容器/gRPC/mTLS 等由后端任务自决)。
- 真**跨进程/分片**的分布式扩展(本期是单 JVM 内并发验证)。
- 服务端限流(本期反压在客户端/装饰器侧;服务端半边属引擎)。
- 经验"任务签名"调优;两个真实 runtime 互打的 over-wire e2e(本期单 runtime 多调用 + 多 agent 角色已验证语义)。

## 结论
对照之前提的需求 + 同事评审,#283 的 A2A 共享记忆中间件在五维上**逐条有已测落地**:反压、幂等、真实 over-the-wire e2e、经验跨 run 生命周期 + **保鲜**、经济性量化、性能基准,以及同事点出的**权威=principal(解耦 runtime)**、**团队交互记录/读归因**、**失联取代**。**46/46 通过**。仍未做的是**闭源引擎部署形态(MemOpt,另任务)** 与**真跨进程/分片分布式扩展**,边界清晰、已在 the a2a-shared-memory design decision 标注——不混进 #283。
