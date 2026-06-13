---
affects_level: L1
affects_view: development
proposal_status: response
authors: ["Claude"]
responds_to: docs/logs/reviews/2026-06-12-agent-runtime-outside-in-architecture-proposal.cn.md
related_adrs: [ADR-0088, ADR-0098]
related_rules: [D-1, D-2, D-4, D-5, G-9, G-15]
affects_artefact:
  - agent-runtime
  - agent-runtime/README.md
  - docs/quickstart.md
  - docs/contracts/contract-catalog.md
  - architecture/docs/L1/agent-runtime
---

# Response: AgentRuntime outside-in 架构提案 — 对抗式核查与交付回复

> **Date:** 2026-06-12
> **Reviewer doc:** `2026-06-12-agent-runtime-outside-in-architecture-proposal.cn.md`（同目录）
> **Methodology:** 多代理对抗核查（每条 finding ≥2 次驳斥尝试，驳不倒才确认；4 persona 独立盘点先于评审主张裁决；OSS 复用裁决以本地上游源码镜像实读为准）→ 四波并行实施（隔离 worktree，每波 verify 绿后顺序集成）→ 五类缺陷深扫 + 修复波 → 两套 e2e 收口。
> **Verified at:** 分支 `review/2026-06-12-agent-runtime-outside-in-waves`（基线 ac07e347 #189）。
> **结论先行:** 提案方向**接受**，七条 findings **六条确认（其中三条加重）、一条降级部分确认、零条整体驳回**；但提案的修复方案有四处被对抗核查**收窄**（六类型中立模型、remote 完整配置面、traceId/spanId MDC、SSE 直接换核）。对抗核查另挖出评审者未点名的**隐藏缺陷 34 项**（其中 P1 一项：库 jar 携带前代 agent-service 的整份 application.yml + Flyway 迁移）。全部确认项已在本分支实施并验证闭环。

---

## 0. 摘要

评审者的核心论断——"代码已收敛为 A2A SDK bridge + adapter library，但文档、配置、SPI 与运行面仍在表达更宽的 runtime 承诺"——**经逐条实证核查成立**。本回复不止于裁决：按裁决后的修正规格，四个交付波已全部实施、集成、自检、e2e 验证：

| 波次 | 内容 | commit | 模块 verify |
|---|---|---|---|
| W1 契约 truth-up | F1 配置实现（方案 A）+ F3 措辞对齐 + HD-1 库工件清理 + HD-7 北向装配 + 9 项文档对账 | 2a619c94 | 181/0 |
| W2 协议中立核心 | F2 收窄版：`common.RuntimeMessage` 单一中立类型 + 卡类归位 engine.a2a + ArchUnit 白名单反转为禁止 | af007208 | 172/0 |
| W3 可观测性闭环 | F5 收窄版（MDC +tenantId/agentId）+ F6 全盘接受 + HD-17 远端腿轨迹 + 脱敏对齐 + 北向截断可见化 | 809e3511 | 175/0 |
| W4 remote 生产化 + SSE 收敛 | F4 收窄版（stream-timeout 可配 + 超时 best-effort cancel + catalog 进 actuator + 粘性 id + 并发安全）+ F7（共享 SseEventDecoder） | 47af5087 | 190/0 |
| 集成对齐 | 跨波接缝 + E13 治理收口 + extractor worktree provenance 修复 | 7d8d00a9 / acf739ba | 223/0 |
| 修复波（深扫第 1 轮） | 12 项必修（含 stop 阶段 drain 行为修复） | 33bbd8d1 | 230/0 |

收口证据：canonical gate `check_architecture_sync.sh` **PASS**；openjiuwen e2e **7/0**；llm e2e 全套 **51/0**（详见 §7）。

---

## 1. F1–F7 逐项裁决表

| ID | 评审定级 | 裁决 | 终级 | 关键证据 | 处置 |
|---|---|---|---|---|---|
| F1 | P1 | **确认并加重** | P1 | README 声明 `default-agent-id`/`public-base-url` 两键全仓零绑定（`RuntimeAccessProperties` 仅 defaultTenantId）；**加重**：仓库自己的两个示例 application.yaml 已在设置死键被 relaxed-binding 静默吞掉，且默认 card `provider.url` 硬编码 `localhost:8080`、模块从未注册 ForwardedHeaderFilter | W1 实施方案 A：两键实现 + public-base-url 第一优先级 + mismatch WARN + forward-headers 默认/文档化（commit 2a619c94） |
| F2 | P1 | **确认并加重** | P1 | `AgentExecutionContext` 携带 `spec.Message`，三适配器全部消费 Role 枚举；`RuntimePackageBoundaryTest` 白名单显式放行 spec；**加重三连**：(a) 桥层构造的 Message 永远是合成单文本——耦合纯仪式性；(b) 下游已书面投诉（hotel 示例 README）；(c) `logical.md` 断言"保证框架适配器不依赖 A2A 协议"与代码相反，"有意决策"驳斥失败（ADR 全目录零提及） | W2 收窄实施：单一 `RuntimeMessage` + `lastUserText()`，ArchUnit 反转为禁止（commit af007208）。评审的六类型模型被拒，见 §5 |
| F3 | P1 | **部分确认（降 P2）** | P2 | 漂移核心成立：无 Run 实体（按 ADR-0088 归 agent-service）、"engine"实为 bridge、session 持久化委托框架。**两处驳回成立**：internal queue 真实存在（SDK `InMemoryQueueManager` 接线）、task-control 真实存在（cancel-through + Get/List/CancelTask 路由），评审把"SDK 提供"误判为"不存在" | W1 文档措辞对齐（README/pom description/catalog "Run-owning" 改写 + "What runtime does NOT own" 边界段），**不**按 P1 立项 run repository |
| F4 | P2 | **确认** | P2 | 四子主张逐项驳斥全部失败：配置仅 url 一字段；`refreshPending()` 首行跳过 available；transport 按 id 永久缓存无失效；超时仅返回 FAILED 不 cancel（60s 硬编码且生产构造不可达） | W4 收窄实施：见 §5 对 §4.4 的裁决（commit 47af5087） |
| F5 | P2 | **确认** | P2 | `A2aAgentExecutor` 是全模块唯一 MDC 写入点且仅 contextId/taskId 两键；`TrajectoryEvent` 自述 tenant 为强制属性，普通日志与自身契约矛盾；勘误：应为"仅写入"而非评审说的"主要写入" | W3 收窄实施：+tenantId/agentId 两键 + pattern 约定落 README；traceId/spanId 拒绝，理由见 §5 |
| F6 | P2 | **确认** | P2 | `TrajectoryOtelConfiguration` 条件仅查 api 类 `OpenTelemetry` 而 @Bean 方法签名用 SDK/exporter 类——条件通过后 Spring 反射解析方法签名即抛 NoClassDefFoundError（失败早于方法体）；同文件 javadoc 自称门控 SDK 与实现矛盾；同模块 `RuntimeAutoConfiguration` 已有正确的 name= 模式，此处是唯一偏离点 | W3 全盘接受：`@ConditionalOnClass(name={...OpenTelemetrySdk, ...OtlpGrpcSpanExporter})` + 类缺失 WARN + FilteredClassLoader 测试 |
| F7 | P3 | **确认并加重** | P3 | 两份手写 SSE parser 逐字近似且已行为漂移；**加重**：被复制能力就在本 pom 已声明直接依赖 `a2a-java-sdk-http-client` 内（javap 实证 `ServerSentEventParser` 为 public API）。**子主张驳回**："手拼 JSON"不成立——两 client 的 JSON 全走 Jackson；同类缺陷实际位于 `A2aParentTaskProjector.errorJson`（转义不全），按位置勘误为 HD-26 | W4 实施：模块内共享 `SseEventDecoder`（方言参数化），既有 wire 测试一行不改作回归护栏；**不**直接换 SDK 核，理由见 §5 |

---

## 2. 分类深查（含评审者未点名的同类缺陷）

### 2.1 文档/配置/工件契约漂移（F1、F3 所在类）

评审者看到了配置键与定位措辞，**漏掉了同类中最重的一项**：

- **HD-1（P1）库工件越界**：自称 plain library 的 jar 在 classpath 根携带**前代 agent-service 的整份 `application.yml`**（文件头自述 "W0 agent-service application config"；flyway.enabled=true 指向库内死迁移、localhost datasource、server.port 8080、`${APP_TRAJECTORY_LEVEL}` 等多个零绑定死键）+ `logback-spring.xml` + `db/migration` 死脚本。宿主同名配置按 classpath 顺序静默遮蔽；宿主带 JDBC+Flyway 时死迁移混入宿主迁移链。**已修**：全部移出库主资源，pom 同步删除 flyway/jdbc/postgres/snakeyaml 死依赖段，新增 `LibraryArtifactBoundaryTest` 固化"打包产物根无应用级工件"。
- HD-7（P2）：纯依赖宿主启动后 `/a2a` 与 agent card 静默 404（北向控制器不随 auto-config 注册，官方示例被迫手写 scanBasePackages）。已修：`@ConditionalOnWebApplication` 嵌套装配。
- HD-2/3/4/5/6/8/9：README 引用不存在的 access 类、quickstart 称 run+idempotency 实体 live in runtime、L1 引用已删除的 AgentStateStore、示例死 exclude、card provider.url 硬编码、snakeyaml 死依赖——全部已修（W1 + 修复波）。
- 深扫第 1 轮追加：A8（contract-catalog 包列错误，已修）；A2/A3/A9/A10（L1 旧类引用与 quickstart 首跑路径失实，**可缓**——属独立 L1 全量对账波，与既有"L1/dfx/catalog 滞后"议题合并处理）。

### 2.2 SPI 协议泄漏（F2 所在类）

主泄漏点**不在** `engine.spi` 而在 engine 根包的 `AgentExecutionContext`（评审引用的 ArchUnit 白名单只是固化层）。全 SPI 表面扫描另获：HD-10（`AgentCards`/`AgentCardProvider` 引 5 个 A2A spec 类型却住在 spi 包——裁决为**归位 engine.a2a** 而非中立化：协议元数据本就属于桥层）、HD-11（业务 handler 被迫手工解析 Message 已出现两份变体）、HD-14（适配器到桥包的依赖缝无守卫）、HD-15（OtelSpanSink 错置 a2a 包稀释包名承诺，迁出至新建 engine.otel）、HD-16（ArchUnit 黑名单引用四个已退役包恒真空转）。修后守卫形态：中立包（engine 根 + spi + common）**禁止** `org.a2aproject..`，适配器禁依赖 engine.a2a，engine.service 限定卡解析切片（修复波加 `engineServiceTouchesOnlyA2aCardResolutionSlice`，并以临时探针验证规则会红后回退）。

### 2.3 Remote A2A 生产面（F4 所在类）

四子主张之外的同类深挖（全部已修，W4 + 修复波）：HD-21（in-flight 注册在 consumeHandler finally 即移除而 invokeRemote 发生在其后——取消落入窗口找不到 cancelled 标志）、HD-22（同名 card 先后可用时去重后缀被静默改名——粘性 id 修复）、HD-23（超时/终态后迟到事件从回调线程触碰 single-writer emitter——closed 门闩 + gate 串行化）、HD-24（Entry 可变字段跨线程无可见性保证——改不可变快照 + volatile copy-on-write）、HD-25（超时丢弃已收结果且无稳定错误码——保留 results + `REMOTE_TIMEOUT`）、HD-26（errorJson 控制字符转义不全——改 Jackson）。**记录不修**（D-S2/D-S3 等 P3 残留与 SDK 限制：ClientTransport 不暴露 per-stream 关闭句柄，超时后只能门闩丢弃迟到事件，已在代码注释说明）。

### 2.4 可观测性（F5、F6 所在类）

同类追加（W3 + 修复波）：HD-17（**远端 resume/continuation 腿轨迹完全丢失**——northbound artifact 在 invokeRemote 前就 flush 只含第一段本地腿；修后经 `openForResume` + beforeTerminal 推迟到收敛后交付）、HD-18（INPUT_REQUIRED prompt 全文裸打 INFO——promptChars + `A2aLogMasking`）、HD-19（sink WARN 不带关联键）、HD-20（北向 10k 满后丢最新事件且无截断标记——终态保留配额 + in-band `TRUNCATED` DataPart）、E3/E5（park/continuation 场景 artifact append/lastChunk 标志矩阵错误——`hasArtifact` helper 修正两处）。**拒绝项**：traceId/spanId 进 MDC（见 §5）。

### 2.5 OSS 复用与重复实现（F7 所在类）

见 §5 对 §4.6 的裁决与 §4 原则核查。同类追加：C1（outbound adapter 第三处文本抽取语义漂移——统一 `Messages.text` 重载）、C3/C-S2（pom 13 个零消费依赖逐一 grep 实证后删除）、C-S1（三处操作日志裸打 exception message——补掩码）。

---

## 3. Outside-in 四视角对抗核查

四个 persona agent **先仅凭代码独立盘点能力清单，后**对照评审 §3.x 主张逐条裁决（防锚定双段式）。

### 3.1 开发者（handler/SPI 作者）

- 独立盘点：SPI 生命周期契约（default no-op + 启动回滚有测试固化）、中立结果模型、轨迹基类**完整**；输入契约硬编码 A2A Message、`Stream<?>` 无泛型绑定、取消对 eager 流无效、测试夹具未导出、缺"写第一个 handler"教程为**缺口**。
- 评审主张 (a)–(e) **全部成立**（生命周期语义清楚；handler 作者必须理解 A2A wire 类型；ArchUnit 固化泄漏；新入口要么污染要么破坏性迁移；三适配器共同理解 A2A 消息结构）。
- 评审遗漏：**openJiuwen handler eager 执行使协作式 cancel 形同虚设**（HD-28，修复波以 javadoc 契约澄清收口第一步，异步化属大改造留待后续）；`Stream<?>` 泛型化是破坏性 SPI 改动（HD-31，记录，留待 SPI 升版批次）。

### 3.2 部署者

- 独立盘点：A2A 端点、租户 header、生命周期（启动回滚/readiness 闸/优雅停机）、optional 依赖降级**完整**；card URL 纯请求推导、双部署路径装配不一致为**缺口**；public-base-url/default-agent-id 为**文档承诺未实现**。
- 评审主张 (a)–(d) **全部成立**，其中 (c) 还被加重（路径前缀场景必丢前缀 + 默认 provider.url 硬编码 localhost）。
- 评审遗漏五项，最重者即 **HD-1 库 jar 污染宿主（P1）** 与 **HD-7 纯依赖宿主 404（P2）**——两项都比评审点名的任何部署问题更伤"部署者"，全部已修。HD-29（X-Tenant-Id 无鉴权即定租户而代码自称 transport-authenticated）以文档诚实化收口（README "Tenant header trust" 节 + 措辞修正），网关鉴权属 agent-service 边界。

### 3.3 运行运维者

- 独立盘点：生命周期编排、取消通路、任务状态机结构化日志、轨迹脱敏 fail-safe **完整**；MDC 两键、远端容错、健康面不含 catalog 为**缺口**。
- 评审主张 (a)–(e)/(g) 成立；**(f)"能从 trajectory 查问题但串不起来"判遗漏前提**——默认部署 trajectory 无消费者（sinks 为空整条跳过），"可查"前提缺失；而 traceId=taskId 可与日志 join，"完全串不起"过强。真实断裂收窄为 tenant/agent 维度（F5）与远端健康面（F4），均已修。
- 评审遗漏：**HD-30 停机次序**（drain 写在 destroy 阶段而 SmartLifecycle.stop 先走——修复波将 drain 编入 phase>0 的 stop，附时序固化测试）、HD-33（模块零 Micrometer 指标——记录，独立议题）。

### 3.4 最终用户（A2A 调用方）

- 独立盘点：端点全集 + 结构化错误（code/retryable/schema_version 测试固化）+ 取消 + 远端 continuation **完整**；远端进度无来源标记、northbound 终态一次性交付、超时无任务级状态为**缺口**。
- 评审主张 (a)(b)(c)(e) 成立；**(d)"表现为停住/只见失败"判夸大**——取消与补输入有明确可见状态且测试固化；留存的真实内核（等待期最长 60s 无心跳 + 超时被降格为工具错误）已落 W4 修复（保留已收结果 + `REMOTE_TIMEOUT` 稳定码）。
- 评审遗漏：**HD-17 远端 resume 腿轨迹整段丢失（P2）**、HD-25 超时语义、HD-20 北向静默截断——全部已修。

---

## 4. 架构原则核查（评审 §0 三原则 + 用户要求的结构维度）

| 维度 | 裁决 | 要点 |
|---|---|---|
| 高内聚低耦合 | 部分符合 → 修后符合 | A2A 重型机械已限域 engine.a2a+boot；import 矩阵主导方向 `*→engine.spi` 无逆向边。唯一实质缺口即 F2（已修）。 |
| 功能结构单一 | 部分符合，**驳回"必须拆包"** | engine.a2a 11 类分 4 职责簇，但执行器拆分后最大类仅 349 行、协作者全部 package-private——拆包将被迫升 public 扩大可见面（违 D-2）。唯一名实不符住户 OtelSpanSink 定点迁出 engine.otel。 |
| 无本地跨库依赖 | **符合（正面确认）** | pom 全量实读唯一华为坐标是聚合 parent；全目录 grep 兄弟模块（bus/service/sdk）零命中（含注释/字符串）；facts `build-module/agent-runtime` allowed=[]、forbid agent-service。编译期硬隔离即最强执行。 |
| 事件和协议清晰 | 部分符合 | TrajectoryEvent schema v2 + 单一映射点成立；缺口=北向线协议（`trajectory.level`/`trajectory.northbound` metadata 键、`agent-trajectory` artifact 名）只存在于代码常量——W3 已在 contract-catalog 落锚点（"Northbound trajectory wire contract"）。 |
| 运行特性完整（trajectory/communication/logs/lifecycle） | 修后闭环 | trajectory：远端腿接通 + 截断可见化；communication：超时/取消/粘性路由/并发安全；logs：四键 MDC + 掩码对齐；lifecycle：启动回滚 + readiness 闸 + stop 阶段 drain。 |

---

## 5. 对评审 §4.1–§4.6 与交付波的裁决

| 提案节 | 裁决 | 理由（含拒绝项的技术理由） |
|---|---|---|
| §4.1 收敛定位 "Embeddable A2A-first Agent Runtime Library" | **接受** | 构建层已成立（无 boot jar、RuntimeApp 零 Spring Boot 依赖）；落地动作=清除矛盾残留（HD-1）+ 补北向装配（HD-7）使 embeddable 名副其实，而非新增代码。 |
| §4.2 中立 runtime model（六类型） | **收窄接受** | 接受核心方向（上下文不暴露 A2A Message、映射收口桥层、ArchUnit 反转）。**拒绝六类型建模**：桥层构造的消息永远是合成单文本，RuntimeConversation/RuntimeInvocation/RuntimeUserInput/RuntimeAgentOutput 没有当下消费者（与既有中立 `AgentExecutionResult` 还有职责重叠）——单一 `RuntimeMessage(role,text,metadata)` + `lastUserText()` 即达成全部验收，信息无损（D-2）。出口侧已是中立 String，证明入口中立化可行。 |
| §4.3 配置契约（方案 A vs B） | **接受方案 A** | 与评审建议一致。附加裁定：方案 B 并不更便宜（还需同步 2 个示例 yaml + 4 处 README）；public-base-url 支持路径前缀场景是 request-derived 永远做不到的。 |
| §4.4 Remote 完整配置面 | **收窄接受** | 接受：`stream-timeout` 可配置、超时**无条件** best-effort cancel（不做 cancel-on-timeout 旋钮——能力已存在且无不取消的正当场景）、catalog 状态进 actuator、card re-refresh + transport invalidation（以粘性 id 修复为前置）。**拒绝**：retry/backoff（`message/send` 非幂等，自动重试可致远端副作用重复执行）、connect/request/stream-idle 三段超时与 refresh 模式旋钮（无消费场景的配置面，违 D-2）、per-entry card-path/enabled；bearer auth 留端口后置。 |
| §4.5 logs 与 trajectory 对齐 | **收窄接受** | 接受 tenantId/agentId 两键 + pattern 锁步（评审未提的必要配套：库 yml 已删，pattern 约定落 README/logback include 片段）。**拒绝 traceId/spanId**：模块内无真实分布式 trace 上下文（无 micrometer-tracing/traceparent 入站），trajectory 的 traceId 恒等于 taskId（放 MDC 纯冗余）、spanId 逐事件随机无窗口级取值——强行加键是假可观测性；待 tracing 基础设施引入后再议。 |
| §4.6 OSS 复用硬原则 | **接受（原则）+ 按实测修正路径** | A2A SDK/Spring 复用经实读确认已完整落地（task/store/queue 全 SDK 类且 @ConditionalOnMissingBean 可替换；A2aJsonRpcController 仅是 SDK 缺 Spring MVC 绑定时的传输胶水，非自研协议栈）。OTel 缺口即 F6 一行注解。**SSE 换核实测后不走"直接替换"分支**：SDK `ServerSentEventParser` 在已声明依赖内且 public，但按 WHATWG 丢弃无 data 命名帧，而 LangGraph 方言依赖 `event: end` 空帧表达完成（有测试固化）——直接换核会吞完成信号；故走评审条件分支的另一支：模块内单一共享 `SseEventDecoder`（方言参数化），SDK 换核留待上游稳定版+选项（CR1 候选版本身也是暂缓理由）。**LangGraph/AgentScope Java SDK survey 已关闭**：langgraph4j 是 in-process 图编排库非 Platform 客户端，LangGraph 官方 SDK 仅 py/js（上游 libs 目录实读）；AgentScope 上游 11 个 Client 全是 sandbox/容器客户端、无 /process SSE Java 消费端——薄 HTTP adapter 即正解；上游 agentscope-runtime-java 原生 A2A 面记为评估项（HD-34，须过"适配层只消费原生事件"边界裁决）。 |
| W1–W4 波次划分 | **接受（重排）** | 按裁决后规格实施完毕；评审 W2 的 "OSS survey" 项已由核查代答关闭。 |

---

## 6. 隐藏缺陷登记（评审者未点名，对抗核查与深扫产出）

34 项编号 HD-1..34，按波次归置：W1 共 9 项（HD-1..9）、W2 共 7 项（HD-10..16）、W3 共 4 项（HD-17..20）、W4 共 7 项（HD-21..27）、修复波收口 5 项（HD-28..32 中的必修部分）、记录不修 2 项（HD-33 零 Micrometer 指标、HD-34 上游 A2A 面评估）。深扫第 1 轮另增同类兄弟 16 项（A8/A9/A10、B-S1/S2、C-S1..S5、D-S1..S3、E5/E6），其中 5 项必修已随修复波落地，余为可缓记录。完整 D-1 四行（现象/根因/影响/修复方向）与逐项证据留存于审计产物（PR 描述附索引），此处列严重度 Top 3：

1. **HD-1（P1）**：库 jar 携带前代 agent-service 整份 application.yml + Flyway 迁移 + logback——现象：classpath 根随包发布应用级工件；根因：纯重建只推倒代码体未审计 resources，库/应用资源边界从未切分且无守卫；影响：污染/遮蔽每一个宿主的配置与迁移链，运维设 `APP_TRAJECTORY_LEVEL` 静默无效；修复：移出全部应用级工件 + 删死键死依赖 + `LibraryArtifactBoundaryTest` 防回潮（commit 2a619c94）。
2. **HD-17（P2）**：远端 resume/continuation 腿轨迹整段丢失——现象：northbound artifact 在 invokeRemote 前 flush；根因：trajectory flow 生命周期未覆盖多腿 run；影响：含远端调用的 run 后半段对一切 sink 不可见；修复：`openForResume` + 北向交付推迟至收敛后（commit 809e3511）。
3. **HD-30（P2）**：drain 在 destroy 阶段而 handler.stop 先走——现象：SmartLifecycle.stop 阶段在飞执行未排干；根因：drain 挂在 AutoCloseable.close；影响：优雅停机窗口内 handler 已 stop 而任务仍在其上执行；修复：A2aServerExecutor 实现 SmartLifecycle（phase=1024），stop=drain，close 幂等委托（commit 33bbd8d1）。

---

## 7. 验证记录

- 基线（改动前 ac07e347）：`./mvnw -pl agent-runtime -am clean verify` BUILD SUCCESS（34.8s），确立全绿起点。
- 每波隔离 verify：181/0、172/0、175/0、190/0；集成后 223/0；修复波后 **230/0**（含新增 lifecycle/decoder/append 语义测试）。
- canonical gate：`bash gate/check_architecture_sync.sh` → **GATE: PASS**（E13 退役引发的 graph/workspace 基线已锁步：nodes 270→266、edges 436→432、elements 365→364、enforcer_rows 38→37）。
- e2e：`examples/agent-runtime-a2a-openjiuwen-e2e` **7/0/0/0**；`examples/agent-runtime-a2a-llm-e2e` 全套 **51/0/0/0**（真 LLM 走本地 Ollama gemma4）。
- e2e 排障记录（证据先行）：llm 套件首跑 51 跑 1 失败——`RetailWealthAdvisorAgentScopeA2aE2eTest` 断言模型回答含字面量"合规提示"，实际回答结构完整唯独该节标题缺失。回归假设（W2 映射丢指令）被驳倒：同测试定向重跑 **3/3 通过**、全套重跑全绿——若映射丢失会稳定失败而非随机。定性：**预先存在的真 LLM 内容断言脆弱性**，按"不动测试断言"纪律记录不改；后续建议把字面量节标题断言改为结构完成性断言（独立小 PR）。
- 修复波反向验证：ArchUnit 新规则以临时探针证明"会红"后回退探针；pom 死依赖逐个 grep 零消费实证（唯一 org.springframework.ai 命中是 ArchUnit 规则里的包名字符串）。
- G-9 台账：无新 family（41 不变）；登记 `F-hand-authored-factual-drift`、`F-cross-authority-agreement`、`F-numeric-drift` 三处复发（yaml+md 平价，UTC 日期）。

### 7.1 合并收口（2026-06-13，main 前进至 c09732e6 后的语义合并）

波次完成后 main 又前进了一个平行重构窗口（#194 a2a remote client 重构、#192 versatile REST 适配器、#173 travel mainplan、#208 llm-e2e 清理、agent-core 0.1.12-jdk17 切换），与本分支在 35 个文件上重叠。按"main 结构为骨架、硬化语义重移植"完成合并：

- **同向收敛**：双方独立把 AgentCardProvider/AgentCards 从 spi 归位 engine.a2a（同一 #182 评审意见的两次响应）——HD-10 裁决被上游独立佐证。
- **重移植**：RemoteAgentCatalog 的硬化实现体（volatile 不可变快照、全量刷新保留 last-good card、sticky id、per-remote stream-timeout）落入 main 命名的 `RemoteAgentCardCache`；remote 装配落位 main 的 `A2aClientAutoConfiguration`（refresher 改全量刷新）；W4 的超时 best-effort cancel / `REMOTE_TIMEOUT` 码 / 单写 emitter / 端点失效重建 transport 全部保留在 main 的 `obtainTransport` 命名下。
- **随 main 收敛**：`RemoteSupport` 包装移除（executor 直收 `RemoteAgentInvocationService`）；`REMOTE_INVOCATION` 结果类型并入 `INTERRUPTED` + `RemoteAgentInterrupt` payload（嵌套远程调用守卫保留）；`default-agent-id` 在 main 的"runtime 恰好托管一个 agent"硬化下收窄为校验+WARN+命名语义（多 handler 选择场景已不可达）。
- **新增收口**：`RemoteAgentToolSpec` 从卡缓存嵌套类抽至 `engine.spi`（openjiuwen 适配器不再触及 a2a 桥，ArchUnit 禁止规则原样保持）；main 的 versatile 适配器与 Mem0 测试适配 `RuntimeMessage` SPI；`AgentCardProperties.createAgentCard` 委托 `AgentCards` 工厂（消除第二份卡片拷贝及其 localhost provider URL 回归）；guides 三处 RemoteSupport/旧 SPI 措辞对齐。
- **合并后验证**：agent-runtime **258/0**（含 main 侧 versatile/checkpointer 新测试）；根 reactor BUILD SUCCESS；全部 example 模块编译通过——唯 `examples/agent-sdk-example` 在 main 上即有 `com.openjiuwen.harness.deep_agent` 缺包（本分支零接触，非本合并引入）；openjiuwen e2e 套件 BUILD SUCCESS（7 tests，真 LLM 用例因本地无 LLM 端点 1 skip；上文 7/0/0/0 为波次期全跑记录）；llm e2e 套件随 #208 清理缩减（openjiuwen 样例与 registry 测试移除），本轮为编译级验证。
- **基线再锁**：graph **267/433**（合并树上再生成实测；按两侧 delta 手算的 264/430 被实测驳回——再次印证 NEVER hand-computed）；workspace 364/200 不变；maven_tests_green 按公式重测 **323**（agent-runtime 258 + agent-bus 32 + tools 33，含 main 侧新测试）；事实层（architecture/facts/generated/）在合并树重提取，FactLayerByteIdentityIT 复绿。

---

## 8. 对 §10 决议请求的回应

**方向批准并已执行**："narrow, strong, A2A-first runtime library" 成立——但落地形态以对抗核查的收窄裁决为准（单一中立类型而非六类型；remote 第一波刚需而非完整配置面；两键 MDC 而非四键；共享解码器而非换核）。评审者把 W1 定性为 "corrective hardening rather than feature work" 的判断**正确**且被加重证实（HD-1 的存在使 W1 实质上是发布缺陷修复）。

留待 owner 后续决策的事项（均已留端口/记录，不阻塞本 PR）：
1. L1 agent-runtime 全量对账波（A2/A3/A9/A10 + 既有"L1/dfx/catalog 滞后"合并处理）；L0 "run-owning" 措辞属 architecture-of-record，须走 /design-mode。
2. handler 取消语义异步化（HD-28 的根治）与 `Stream<?>` 泛型化（HD-31）——破坏性 SPI 改动，建议并入下次 SPI 升版批次。
3. 运行指标面（HD-33，Micrometer）与上游 agentscope-runtime-java 原生 A2A 面评估（HD-34，须过"不碰被兼容框架、适配层只消费原生事件"边界）。
4. remote bearer auth / tenant allowlist（§4.4 留下的端口）。
