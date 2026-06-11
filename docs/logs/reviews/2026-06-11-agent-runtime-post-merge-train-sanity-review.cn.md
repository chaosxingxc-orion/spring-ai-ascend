# agent-runtime 合并队列后四维架构自审（对抗验证版）

> 日期：2026-06-11（UTC）
> 基线：main `fbf2493a`（PR #183 lifecycle + PR #182 remote A2A tool + PR #181 trajectory 三波合并完成后）
> 方法：30-agent 多智能体 Workflow——4 维度 finder → 每条 finding 双镜头对抗验证（必要性反驳 + 简化可行性）→ 综合 + 完整性批评者；全部结论来自现场源码（含基准库 AgentScope-Runtime-Java / LangGraph4j 源码深读），实测不猜
> 战绩：24 项指控 → **18 项被对抗驳回、6 项确认**；修复 wave 见 PR #184；e2e 4/4 模块绿收尾

---

## 一、四个问题逐一作答

### Q1 类嵌套 / 功能模块不清晰或不内聚？——包结构成立；问题 100% 集中在一个类

D1 维度的 6 项指控（spi 轨迹类型混居、boot 大包、engine/service 错位、common 单类包、StampingTrajectoryEmitter、god class 定性）经对抗验证**全部被驳回**：boot 扁平由 ArchUnit `bootIsFlat()` 硬约束，engine/service 在包边界白名单内未泄漏进 spi，span 栈是跨过滤事件保持父链平衡的状态机（测试钉死）。真正确认的内聚缺陷由 D2/D4 双维度独立命中同一点：**`A2aAgentExecutor` 626 LOC 把就绪门控、轨迹开启/排空、cancel 双路传播、远程调用编排、终态延迟路由五个正交职责焊在一个类里**。它不是传统 god class（单写者 emitter + 时序因果链是刻意耦合），但五职责可分解且应分解——可行性已验证（27 个边界测试零改动）。

### Q2 过设计 / 融合掺杂业务语义？——轨迹管道不是过设计，是三条硬约束的代价；业务语义零入侵

对照 LangGraph4j 用 ~4 类 / 555 LOC 的 WrapCall hook 做完整 OTel 观测，我们的 emitter→channel→sink 三层管道初看嫌疑最大，但驳回理由成立：(1) A2A 单写者 emitter 强制"drain 线程缓冲、execute 线程 flush"的异步窗口，同步 hook 无法复制；(2) OTel 依赖 `<optional>`，SinkFactory 是 classpath 隔离边界；(3) span 栈跨被过滤事件保持父链平衡，单趟无法重建。确认的过设计仅两处：执行器单类五职责（P0）与 NOOP 通道 5 形态（P1）。业务语义入侵：**零**——remote invocation/continuation 是 A2A 协议语义而非业务状态投影，与"不做业务投影"边界一致。"配置面爆炸"指控被实测证伪：3 个 properties 类 / 8 个叶子 knob。

### Q3 提前定义/使用了周边模块？——确认 3 项，全部是"声明了但没人用"的死契约

1. **agent-bus 依赖纯死（P1，已修）**：pom 声明消费 EnginePort / s2c SPI，但 75 个主源文件 **0 个 `com.huawei.ascend.bus.*` import**。
2. **`runtime.orchestration` 包纯虚构（P2，已修）**：catalog 宣称该包承载 EnginePort 适配器（含 `InProcessEnginePort`），全仓不存在，ArchUnit 白名单明确禁止。
3. **MemoryProvider 单消费者公共 SPI（P2，暂缓）**：44 LOC 接口仅 openJiuwen 的 MemoryRuntimeRail 一个树内消费者——但 open 状态的 PR #180（OpenJiuwen native memory bridge）正基于它构建，搬迁会正面相撞，暂缓有实证理由。

**会后追加扫描（owner 质询触发）**：agent-bus 在全仓（含 agent-service / examples / agent-sdk）的 import 消费 = **0**；agent-service 的 pom 甚至未声明该依赖；其唯一引用是根 reactor 清单和一个已归档 spike。**agent-bus 是彻底孤儿模块**（21 个主代码文件自我循环）。另发现 catalog 的 agent-bus 行超额宣称 8 个 SPI，其中 `IngressGateway` / `ReflectionEnvelopeRouter` / `FederationGateway` 三个**文件不存在**（Rule 95 只查"代码→catalog"方向，反向超额宣称无 gate 兜底）。处置（退役 / 降级 design_only / 按需折叠）为架构决定，待 owner 拍板。

### Q4 代码冗余？能否进一步抽象简化？——确认 2 项；三处"重复"经验证不可合并

确认：TrajectoryChannel 5 形态收敛为 2（已修，-1 公共类型）；A2aAgentExecutor 拆分（P0，暂缓，见下）。**警惕被驳回的合并冲动**：AgentScope/LangGraph/A2A 三个远程客户端的状态机差异（eventName 配对 / 无 event 行 / 回调流）是协议本质差异，共享 util 在 ArchUnit 包白名单下没有合法落点，提取会把框架常量灌进中立 spi。

---

## 二、基准对照表（我方实测，基准来自源码深读）

| 维度 | agent-runtime（实测） | AgentScope-Runtime-Java | LangGraph4j |
|---|---|---|---|
| 模块总量 | 75 文件 / 6117 LOC | engine-core 55 类 / 8.3K LOC | ~88 类 / ~8.2K LOC |
| 核心 request→handler→events→response 路径 | 12 文件 / 1318 LOC | 19 类 / ~2.0K LOC | 公共 API ~19 根类型 / 2.6K LOC |
| 可观测专用面 | 22 文件 / 1241 LOC | **0 专用类型**（~50 LOC 散布 SLF4J） | 独立模块 ~4 类 / 555 LOC（hook） |
| 单一最大执行类 | A2aAgentExecutor 626 LOC | Runner ~232 LOC | ~248 LOC |
| 配置面 | 3 properties / 8 叶子 knob | DeployManager 2 方法 | 2 config 对象 / 19 builder 方法 |

读数：总量小于两个基准，核心路径最瘦。**离群值是可观测面（1241 LOC = LangGraph4j 的 2.2 倍）**——但三层管道已被对抗验证为单写者/optional-classpath/span 栈三约束下的承重结构（AgentScope 的 50 LOC 裸日志是下限不是理想态）。结论：**可观测面的合理上限就是现状，不许再长**。

---

## 三、简化清单与处置

| 级别 | 动作 | LOC | 处置 |
|---|---|---|---|
| P0 | 拆 A2aAgentExecutor（TrajectoryPipelineManager ~80 / RemoteInvocationOrchestrator ~100-120 / ResultRouter ~60，主类降至 ~400）。强制修正条款：提取类 package-private、只被 execute() 同步调用、不持有 emitter 字段、Javadoc 标注单写者约束 | -200 | **暂缓**：该文件是三 PR 刚撞完的热区（本次合并三轮冲突全在它身上），并发贡献者在线；带条款方案已备好，建议独立 wave |
| P1 | 删 agent-runtime 的 agent-bus 死依赖；2 个 bus 契约测试迁回 agent-bus | — | ✅ PR #184 |
| P1 | NOOP 通道收敛（删 NoopTrajectoryChannel，内联进接口常量） | -25 | ✅ PR #184 |
| P2 | catalog EnginePort 行 truth-up（删虚构的 InProcessEnginePort / runtime.orchestration）+ agent-runtime 模块行 truth-up | 文档 | ✅ PR #184 |
| P2 | MemoryProvider 迁 openjiuwen 包私有 | 公共面 -44 | **暂缓**：与 open PR #180 正面冲突 |

## 四、保留项清单（对抗验证证明承重——别再提刀）

1. 轨迹三层管道（单写者 emitter 异步窗口 / OTel optional classpath / span 栈平衡，6 个测试钉死）
2. boot/ 扁平 884 LOC（ArchUnit `bootIsFlat()` 硬约束）
3. engine/service 326 LOC（OutboundPort 是 9 处测试 mock 的接缝；catalog 双协议消费）
4. TrajectoryRuntime 2 字段 record（原子协调 channel+emitter，拆开必引竞态）
5. 三个 SSE 解码器不合并（协议状态机本质差异）
6. StreamAdapter 工具不提取（框架常量进 spi 即污染中立层）
7. Sink 不提基类（错误隔离是 Composite 的唯一职责所在）
8. AgentCardProvider（卡片定制的廉价保险缝）
9. 配置面现状（8 叶子 knob；OTel 双条件守卫是 boot 安全边界）

## 五、修复 wave 执行记录（含 e2e 暴露的主干回归）

1. **PR #184 — 审查修复 wave**：死依赖删除 + 测试归位 + NOOP 收敛 + catalog truth-up + facts/graph 再生 + baseline 274/441 truth-up。verify 177/0，gate 32/32 PASS。
2. **actuator 可选性回归（e2e 暴露，PR #183 引入）**：`AgentRuntimeHealthIndicator` 实现 actuator 的 `HealthIndicator`，而 actuator 是 `<optional>`；自动配置类外层 bean 方法签名引用该类型 → 无 actuator 宿主在 bean 类型推导时 `NoClassDefFoundError`，上下文启动失败。CI 不构建 examples，故从未暴露。修复：health bean 隔离进嵌套 `@ConditionalOnClass`（按类元数据评估，不加载类）配置类 + `FilteredClassLoader` 回归测试。
3. **logback 全局状态 flaky（合并期 CI 暴露）**：`A2aAgentExecutorTest` 往 JVM 全局 logger 挂 ListAppender 抓轨迹日志，与并发启动的 SpringApplication 日志重载（`LoggerContext.reset()` 拆光 appender）竞态。修复：测试类 `@Isolated`。

## 六、e2e 验证终局（Goal 收尾条件）

| 模块 | 结果 |
|---|---|
| agent-runtime-a2a-llm-e2e（OpenJiuwen ReAct + AgentScope，真模型 Ollama/gemma4） | ✅ 全绿 |
| agent-runtime-a2a-openjiuwen-e2e（真模型） | ✅ 6/0 |
| agent-runtime-a2a-return-modes-e2e | ✅ 2/0 |
| agent-runtime-a2a-remote-openjiuwen-e2e（双 runtime 远程 A2A tool） | ✅ 4 跑 0 败 1 跳（凭据分支按设计 assume 跳过） |

## 七、完整性批评者补盲（下一轮候选，未审不代表无问题）

- engine/openjiuwen 包（7 类 ~729 LOC，第 3 大类 OpenJiuwenAgentRuntimeHandler 289 LOC）四个维度均未覆盖
- app/ 包的单实现接缝（RuntimeHost interface + 1 impl）与 boot/AgentRuntimeLifecycle 的启动职责重叠
- engine/agentscope 6 类型小叶子层级
- A2aParentTaskProjector（179 LOC）与执行器终态/cancel 逻辑的重叠未查
- **spi 的协议类型泄漏**：`AgentCards`/`AgentCardProvider` import `org.a2aproject.sdk.spec.AgentCard`——中立 SPI 面里嵌着 A2A 协议类型（语义泄漏审查的真正未答半题）
- agentscope+langgraph 客户端栈（~900 LOC）在 1:1 runtime↔framework 约束下是否属投机性多框架预留（D3 未问）
- agent-bus 孤儿模块处置 + catalog 超额宣称的 3 个不存在 SPI（见 Q3 追加扫描）
