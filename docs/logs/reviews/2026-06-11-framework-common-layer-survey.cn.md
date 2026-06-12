# 参考框架「契约层 vs 执行层」切分取证 + agent-runtime common 化裁决（2026-06-11）

8 个参考框架源码树（D:\ai-research\agent-platforms-survey\）逐仓取证，每条结论带文件级引用（8 agents / 157 次源码读取）。回答的问题：**agent-runtime 的 `engine.spi` 契约面（handler SPI、执行上下文/结果、RuntimeIdentity、轨迹事件 schema）被 agent-sdk / agent-service / examples 消费，是否应该抽出专门 common/api 模块？**

## 一、各框架做法速览

| 框架 | 专门契约模块? | 契约宿主 | 关键证据 | 代价/教训 |
|---|---|---|---|---|
| agentscope-runtime-java（直接对标） | 否 | engine-core（同时是引擎） | AgentHandler SPI/schemas 全在 engine-core；engine-core 编译依赖 agentscope-core 框架 SDK + sandbox-core | 契约宿主不框架中立；SPI 泄漏 `getSandboxService()`；反面教材 |
| agentscope-java | 否 | agentscope-core（410 类单 core） | 扩展用 `provided+optional` 把重 core 挡在传递类路径外；legacy Event 用弃用策略管理 5 个下游的 wire 绑定 | scope 纪律 + 包稳定承诺是不切模块的替代手段 |
| langgraph4j | 否 | langgraph4j-core | 12+ 下游全指 core；core 编译类路径仅 slf4j+async-generator（JSON 全 provided） | 「依赖 runtime ≈ 依赖 api jar」——轻 jar 使切分无必要 |
| **langchain4j** | **是**（langchain4j-core） | 专用契约模块（仅 jackson+slf4j+jspecify） | ~60 集成实现 core 接口；集成 pom 里主模块仅 test scope | 承重原因 = 实现者扇出；core 非接口洁癖（default 方法/编解码随契约走） |
| spring-ai-alibaba | 否 | graph-core | RunnableConfig（core 契约）硬编码 agent 层 metadata 键 | **腐化样本：上层语义泄漏进底层契约** |
| openjiuwen agent-core-java | 否 | 单体 jar | schema 子包与机制同居；`spi` 包与 core 包级循环依赖 | 消费 schema 拖 milvus/pulsar/pdfbox；只因无真实模块边界才成立 |
| adk-python | 否 | google.adk 单发行包 | 契约只是命名子包；但执行 import 契约、永不反向 | 方向纪律与切不切模块无关，必须始终成立 |
| **langgraph（python）** | **是**（选择性） | langgraph-checkpoint（零内部依赖） | 只在多实现者的缝切（saver×N + conformance kit）；单实现执行类型留 core；北向 wire schema 独立 sdk-py（零引擎依赖）；同命名空间发布 | **选择性抽取 = 最精准处方** |

票面 6:2 偏 runtime-as-host；但切了的两家（langchain4j、langgraph）是生态最大的两家，且都是**扇出压力到了才切**，非预先设计。

## 二、横向规律（决定性变量）

1. **独立实现者扇出**才是分水岭，不是消费者数量——出现第二群外部实现者才切，且按缝切，不做大一统 common。
2. **宿主依赖重量**是第二变量——core 足够轻则不切也无痛（langgraph4j）；重 jar 拖累消费者则要么瘦身要么切（openjiuwen/asrj 的反面）。
3. **方向纪律永远成立**：执行依赖契约、永不反向；契约类型中不得出现上层语义（SAA 的 RunnableConfig 教训）。
4. **跨进程 wire schema**（轨迹事件、错误 envelope）是最早值得独立的部分（langgraph sdk-py 模式）。

## 三、裁决（对照 2026-06-11 common 化 A/B/C 档分析）

**维持：本轮不建 common 模块，agent-runtime 继续当契约宿主。** 三处落地：

1. **触发条件硬化为两个指标**（任一成立即按 langgraph 式选择性抽取执行）：
   - T1：agent-sdk 进 reactor，或框架适配器形成第二实现者群（注：agent-sdk 今天已实现 AgentRuntimeHandler 两个适配器——扇出压力半成立，持续观察）；
   - T2：agent-service / agent-sdk 明确拒绝拖 runtime 的 spring-web + a2a-server 传递依赖。
   - 抽取范围预案：仅 SPI 缝（AgentRuntimeHandler/StreamAdapter/AgentExecutionResult/AgentExecutionContext/RuntimeIdentity/TrajectoryEvent schema + 各 SPI 接口），执行类型不动，**沿用 `engine.spi` 包名**（langgraph 同命名空间技巧，消费者零改动）。
2. **腐化哨兵已落地**：`RuntimePackageBoundaryTest.engineSpiDependsOnlyOnContractSafePackages` —— `engine.spi` 依赖白名单锁死为 {自身, engine 根, runtime.common, java, slf4j, spring-util, a2a-spec}，上层语义（boot/a2a 执行件/框架适配包/a2a server/兄弟模块）一旦渗入即红。
3. **廉价对策**：runtime jar 依赖面不再加重（本轮已删 logstash-encoder）；如兄弟模块抱怨类路径，先评估 provided/optional scope（agentscope-java 模式），再触发 T2。

## 四、关联

- common 化 A/B/C 档分析（同日，对话内交付）：A 档=SPI 契约面+RuntimeIdentity+轨迹 schema；B 档=错误 envelope/Messages/AgentCards/OtelSpanSink（单消费者缓议）；C 档=执行机制/框架适配/装配（永不进 common）。
- ADR-0162（发现迁 agent-service）已为 T2 方向铺了第一块砖：service 通过 `RemoteAgentCatalogPort` 实现 runtime 端口。
