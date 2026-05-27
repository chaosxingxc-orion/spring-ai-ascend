---
affects_level: L1
affects_view: [logical, process, development, scenarios]
proposal_status: draft
language: zh-CN
module: agent-service
authors: ["x00209170"]
related_adrs: []
related_rules: []
affects_artefact: []
---

# AgentService 设计自审与修复说明

> 版本：v1（配套 M1~M6 功能清单与天气澄清场景）
> 范围：在 7 篇设计稿全部完成之后做一次横向自审，找出"职责漂移 / 职责真空 / 数据契约不一致 / 边界用例覆盖不全 / 违反核心约束"等问题，并把修复落到原稿。
> 目的：把"先写文档再做交叉校验"的工程纪律留痕，便于后续 v2 增量推进时不重复踩坑。

---

## 1. 自审方法

1. **以场景为骨架反向验证**：拿 `AgentService-Scenario-Weather-Clarification.md` 的 6 个阶段对 M1~M6 的功能项逐步对账，看每个 message 是否在文档里有显式归属。
2. **以约束为切面横向验证**：枚举 8 条核心约束（C1~C8），逐条把"被谁验证、由哪个文档兜底"列出来，找悬空者。
3. **以数据契约为线索纵向验证**：把每个模块声明的对外契约（AccessIntent / ControlEvent / WorkItem / RunEvent / ExecutionRequest / AgentEvent / BuiltPrompt / ContentBlock / ConfigSnapshotRef / CorrelationRecord / InterruptRegistration）从生产到消费走通，看字段是否在生产端缺位或在消费端被臆造。
4. **以错误类别枚举为兜底**：把 M5 EDE-09 列出的错误类别和 M4 TCC-07 / M6 TTI-08 等需要消费枚举的地方做匹配。
5. **以"M4 唯一驱动者"做反例搜索**：grep "STM-03" 的调用方，确认没有非 M4 模块在文档里写过 STM-03。

## 2. 发现与修复总览

| 严重度 | 类别 | 数量 | 已修 | 待处理 |
|---|---|---|---|---|
| HIGH | 职责漂移 / 唯一驱动者违例 / 数据契约对不齐 | 6 | 6 | 0 |
| MEDIUM | 字段对齐 / 边界用例覆盖 / 路由完整性 | 9 | 9 | 0 |
| LOW | 文案 / 表格条目过期 / 措辞 | 5 | 5 | 0 |

## 3. HIGH 级（已修复）

### H1. TTI-09 直接写 STM-03 违反"M4 唯一驱动者"
- **现象**：原 M6 TTI-09 描述里写"INTERRUPT_REQUEST → 直接调 STM-03 RUNNING→SUSPENDED"。
- **违反**：M4 TCC C1（STM-03 唯一调用者）+ M2 STM-03 输入契约（只接受 M4 调用）。
- **修复**：TTI-09 输出改为 `ControlEvent { kind=INTERRUPT_REGISTERED, payloadRef=InterruptRegistration }` 投到 IEQ-02；由 M4 接管转移。

### H2. INTERRUPT_REGISTERED 路由表缺失
- **现象**：TCC-01 控制事件路由列表里没有 INTERRUPT_REGISTERED；M4 拿到事件后无处可去。
- **修复**：TCC-01 路由列表新增 `INTERRUPT_REGISTERED / RESUME_ACCEPTED / SPAWN_CHILD`；TCC-06 拆为 **TCC-06A**（消费 INTERRUPT_REGISTERED → RUNNING→SUSPENDED）与 **TCC-06B**（消费 RESUME / CALLBACK → SUSPENDED→RESUMING）。

### H3. RESUMING→RUNNING 触发信号真空
- **现象**：M4 把 Run 转入 RESUMING 后派 RESUME_TICK；M5 拿到 WorkItem 后没有任何机制告诉 M4 "adapter 已成功接管，可以转 RUNNING 了"。
- **违反**：状态机 RESUMING 永远走不到 RUNNING。
- **修复**：定义新的控制信号 `ControlEvent.RESUME_ACCEPTED { runId }`；M5 EDE-07 在 RESUME_TICK 完成 adapter 注入后向 IEQ-02 emit；M4 TCC-06B 接收后做 RESUMING→RUNNING CAS。

### H4. responseSnapshot 回填责任归属错位
- **现象**：原稿暗示由 M1 在投递终态 reply 后回填 STM-08。
- **违反**：M1 不应做"幂等响应快照写回"这种状态决策类工作；且 M1 在多个 reply channel 下"回填时机"难以唯一。
- **修复**：明确移交 M4 TCC-03，作为"终态转移"的事后钩子：transition 成功且 newState ∈ {COMPLETED, FAILED, CANCELLED} → 在 actor 内同步调 STM-08 回填。

### H5. REMOTE_AGENT_INVOKE_REQUEST 违反"远端调用不经 M6"
- **现象**：M6 TTI 把"远端 Agent 调用"列为第 6 类拦截；M5 EDE-08 SPI Kind 里也含 `REMOTE_AGENT_INVOKE_REQUEST`。
- **违反**：远端 Agent 在远端进程内自治，本进程 M6 不应也无法拦截远端进程的资源调用；M6 TTI-01 C2 已声明"远端不拦"。
- **修复**：从 EDE-08 桥接列表、ExecutorAdapter SPI Kind、M6 拦截 kind 列表全部删除；远端 Agent 通过 EDE-04 Remote Adapter 自行调远端 A2A，且仅暴露 A2A 翻译后的平台 AgentEvent。

### H6. CONTEXT_OVERFLOW 未在错误类别枚举中
- **现象**：M6 TTI-02 描述 prompt 超预算时"返回错误类别 CONTEXT_OVERFLOW"；但 M5 EDE-09 枚举里只有 11 项，没有这个类别。
- **修复**：把 EDE-09 错误类别枚举扩展到 14 项：新增 `CONTEXT_OVERFLOW / ADAPTER_UNAVAILABLE / DEADLINE_EXCEEDED`，并在文档中明确"EDE-09 是全平台错误类别枚举的唯一权威来源"。M6 / TCC-07 / EDE-11 / TCC-08 均直接消费本枚举，不得自造。

## 4. MEDIUM 级（已修复）

### M1. CorrelationRecord.handle 字段形态不清
- **修复**：STM-06 把 handle 定义为 discriminated union `LocalChildHandle { childRunId } | RemoteAgentHandle { remoteAgentId, remoteTaskId, remoteThreadId, callbackId }`；并说明 M4 创建空骨架（status=PENDING），M5 拿到远端 handle 后 CAS 填实（PENDING→ACTIVE）。

### M2. ConfigSnapshotRef.hash 缺位
- **修复**：STM-07 ConfigSnapshotRef 增加 `hash` 字段，content-hash 由 STM 在写入时计算；用作 M5 EDE-06 `(agentId, snapshotHash)` 双键缓存判等的物质基础，避免不同 snapshot 命中同一缓存条目。

### M3. CANCEL_REQUESTED 与 WORKITEM_DONE 的竞态行为没有定义
- **修复**：TCC-03 显式给出仲裁规则——
  - 仍有未 settle 的子方 → 保持 CANCEL_REQUESTED 等子方完成 → CANCELLED；
  - 无子方且本次 WORKITEM_DONE 含完整 final artifact → 走 COMPLETED（用户实际拿到了结果）；
  - 无子方且 done 不含 final artifact → 走 CANCELLED。
  - 决策落 RunEvent，reason code `CANCEL_RACE_RESOLVED_AS_COMPLETED / _AS_CANCELLED`。

### M4. AgentCard 发布方向反了
- **原稿**：M5 EDE-05 主动 push registry summary 给 M1。
- **修复**：改为 M5 只提供只读 pull API；M1 AL-10 启动期/变更通知时主动 pull。避免 M5 对 M1 形成 push 反向依赖。

### M5. ControlEvent 字段 enqueuedAt 在 IEQ-02 与 TCC-01 不一致
- **修复**：IEQ-02 envelope schema 与 TCC-01 入参 schema 统一以"IEQ-02 envelope"为唯一权威定义；TCC-01 文档将 `enqueuedAt` 等字段措辞改为"envelope 字段透传"，不再重复定义。

### M6. InterruptRegistration 字段挂载位置
- **修复**：在 M6 TTI-09 与 M4 TCC-06A 一致约定，`InterruptRegistration` 走 ControlEvent.payloadRef（不是 ControlEvent 顶层字段也不是扩展字段），由 STM 落具体载荷。

### M7. RESUME_TICK 携带 STM-04 cursor 的方式
- **修复**：在 IEQ-03 WorkItem schema 中显式定义可选字段 `parentCursor: StmContextCursor?`；M4 TCC-06B 派发 RESUME_TICK 时填入；M5 EDE-07 消费时透传给 adapter，让 adapter 知道"基于哪个 session version 之后继续推理"。

### M8. ExecutionRequest.sessionContextRef 来源不清
- **修复**：M5 EDE-01 明确 sessionContextRef = `(sessionId, version)`，由 EDE-07 在派工时基于 Run 的 latest STM-04 version 构造；adapter 不直接读，统一由 M6 TTI-02 在 MODEL_REQUEST 阶段按 ref 拉取。

### M9. SUSPENDED 状态下到达 CANCEL 的 DFA 处理
- **修复**：M4 TCC-05 显式补充：SUSPENDED→CANCEL_REQUESTED 合法；callbackId 计时器在 cancel 触发后立即取消；后续就算 callback 到达也按 STM-08 幂等 reject + 审计，不二次驱动。

## 5. LOW 级（已修复）

### L1. ControlEvent.runHint 可空性表述
- **修复**：在 IEQ-02 envelope 描述中明确 runHint 仅在 SUBMIT 时允许为空（runId 由 TCC-04 分配），其他 kind 必须非空。

### L2. M6 "不做的事"清单与功能项有轻微重复
- **修复**：删除冗余条目，保留"不写 STM-03 / 不做协议投影 / 不做 retry 决策"等核心边界声明。

### L3. M1 AL-05 "终态投影" 与 "milestone 投影" 措辞
- **修复**：AL-05 描述里区分 terminal / milestone / token；其中 terminal 是 RunEvent kind=terminal 的投影，milestone 是 StateChanged / tool_result / input_required 等阶段事件。

### L4. TCC-04 启动两步合一的措辞
- **修复**：TCC-04 处理流程显式区分"CREATED→QUEUED"与"QUEUED→RUNNING"两步，并指明配额检查发生在二者之间；避免把两次 STM-03 调用读作一次。

### L5. 场景 §4.4 INTERRUPT 引用错误
- **修复**：将"TCC-06"明确为 TCC-06A（INTERRUPT）/ TCC-06B（RESUME）；新增 RESUME_ACCEPTED 行；Control 通道事件清单补 RESUME_ACCEPTED；§4.5 EDE 增 "RESUME ack" 行。

## 6. 跨模块约束兜底矩阵（自审后）

| 约束 | 主声明位置 | 在场景中的验证 | 现状 |
|---|---|---|---|
| C1：状态机唯一在 Run | STM C1 / TCC C1 | 全场景仅一个 Run 状态机被驱动 | OK |
| C2：M4 是 STM-03 唯一调用者 | TCC C1 | 5 次状态转移全部由 M4 触发；TTI-09 改投控制信号后兼容 | OK |
| C3：协议类型不出 M1 | M1 AL C1 | A2A/MQ envelope 在 AL-02/03 之后即被丢弃 | OK |
| C4：per-Run 单写者 | TCC C2 / TCC-02 actor | 同 Run 决策串行；INTERRUPT/RESUME race 通过 actor mailbox 兜底 | OK |
| C5：资源调用统一经 M6 | TTI C1 | 三次 MODEL + 一次 TOOL + 一次 INTERRUPT 全部经 M6；远端调用不算 5 类 | OK |
| C6：出口事件 cursor 单调 | STM-09 / IEQ-04 | TOKEN/StateChanged/tool_result/terminal 全部经 STM-09 拿 cursor | OK |
| C7：幂等独立空间 | STM-08 / AL-04 | K1（submit）/ K2（callback）各自占位；INTERRUPT_REGISTERED 也受幂等保护 | OK |
| C8：HITL 不破坏状态机 | TTI-09 / TCC-06 | INTERRUPT/RESUME/RESUME_ACCEPTED 全部走控制信号；状态机 DFA 不嵌套 SUSPEND | OK |

## 7. 数据契约一致性（自审后）

| 契约 | 生产端 | 消费端 | 字段权威定义位置 | 现状 |
|---|---|---|---|---|
| AccessIntent | M1 AL-03 | M4 TCC-04 / TCC-06B | AL-03 | OK |
| ControlEvent | M1 / M4 / M5 / M6 | M4 TCC-01 | IEQ-02（envelope）+ 各生产模块定义 kind | OK |
| WorkItem | M4 | M5 EDE-07 | IEQ-03 schema | OK（parentCursor 字段补齐）|
| RunEvent + cursor | STM-03/04/09 + M4/M5/M6 emit | M1 AL-06 投影 | STM-09 | OK |
| ExecutionRequest | M5 EDE-07 | adapter | EDE-01 | OK（sessionContextRef 含 version）|
| AgentEvent | adapter | M5 EDE-07 | EDE-01 SPI（去除 REMOTE_AGENT_INVOKE_REQUEST 后）| OK |
| BuiltPrompt | M6 TTI-02 | TTI-03 | TTI-02 | OK |
| ContentBlock / ToolResult | M6 TTI-11 | adapter / STM-09 | TTI-11 | OK |
| ConfigSnapshotRef | STM-07 | M5 EDE-06 / M6 | STM-07（含 hash） | OK |
| CorrelationRecord | M4 TCC-09（创建）/ M5（填 handle） | TCC-05/07/09 | STM-06（discriminated union） | OK |
| InterruptRegistration | M6 TTI-09 | M4 TCC-06A | TTI-09 → payloadRef | OK |
| ErrorClass 枚举 | M5 EDE-09 | M4 TCC-07 / TCC-08 / M6 | EDE-09（14 项，全平台唯一）| OK |

## 8. 自审遗留的开放问题（不影响 v1 闭环）

下列问题在当前 7 篇文档里都已显式声明"v1 不展开 / 兼容延后"，在自审中也没看到对场景闭环的阻塞，因此留到 v2：

- **跨 Run 父子 join + cancel 级联的更细粒度策略**（TCC-05 / TCC-09 只给出 best-effort，没列尾迹）。
- **GC 与正在订阅的 EventLog 竞争**（STM-10 给出"订阅未终止前不截断"的口径，没给具体水位）。
- **配额 fairness 在大量短任务挤兑下的退化行为**（TCC-10 给了 per-tenant 轮转，没给抢占细节）。
- **三方 Agent 框架（langgraph4j / agentscope-java）的工具回调注入器具体形态**（EDE-03 只列了硬性要求，没列具体 Java 接口）。
- **Vendor Adapter 版本漂移触发的 drift 事件如何下发到运营面**（TTI-10 留口子）。
- **远端 A2A 协议升级（version negotiation）失败的恢复策略**（EDE-04 标 REMOTE_PROTOCOL_ERROR + retryable=false，没给版本回退路径）。

## 9. 与 v1 文档清单的对应

修复完成后，下列 7 篇文档对外构成 v1 设计基线：

- `AgentService-M1-Access-Layer.md`
- `AgentService-M2-Session-Task-Manager.md`
- `AgentService-M3-Internal-Event-Queue.md`
- `AgentService-M4-Task-Centric-Control.md`
- `AgentService-M5-Engine-Dispatch-Execution.md`
- `AgentService-M6-Translation-Tool-Intercept.md`
- `AgentService-Scenario-Weather-Clarification.md`

本文件 `AgentService-Review-Notes.md` 作为审计附录，记录"v1 自审了什么、怎么修的、还有什么没修"。后续任何模块改动需要触发跨模块一致性检查时，先回到本文件第 6/7 节的两张矩阵，再决定是否更新原文与本文。

## 10. v1.1 增量修订（人类挑战驱动）

### 10.1 触发问题

> 三种 Agent 形态（原生 / 三方 AgentScope-java / 远端服务）是否需要进行模型、工具调用拦截？如果需要如何实现，如果不需要又如何兼容？

### 10.2 反思发现

复盘后发现 v1 文档把"代码归属"和"部署拓扑"两个独立维度混在了一起，从而对"如何拦截"语焉不详：

- **R1（HIGH）**：EDE-02/03 隐含假设"原生 / 三方 = in-process"，EDE-04 假设"远端 = out-of-process"，但代码归属与部署形态可正交（自研也可独立部署，三方框架也可作为 sidecar）。文档未显式声明 v1 部署假设，造成读者无法判断"原生 Agent 部署在独立进程时走哪条路径"。
- **R2（HIGH）**：拦截注入机制在三种 adapter 上其实并不一致——
  - Native：DI 强制依赖 platform bean，**同步直调** M6（无需事件桥）；
  - Third-party：替换框架的 `Model / Toolkit / Memory` 抽象层为 platform bridge + 启动期合规校验；
  - Remote：本进程 **不拦**，仅在 A2A 协议出站边界做策略审计。
  
  原稿 EDE-08 用同一句"adapter 产出 *_REQUEST → M5 桥接给 M6"含糊带过，对 native 是过度抽象、对 remote 根本不适用。
- **R3（MEDIUM）**：M6 TTI C2"远端 Agent 调用不拦"过于简略，与 C1"5 类全过 M6"读起来矛盾；需要把"拦截范围 = in-process adapter 进程内的 5 类资源调用，远端进程内自治"作为整体约束讲清。

### 10.3 修复落地

| 问题 | 修复位置 | 修复内容 |
|---|---|---|
| R1 | EDE-02 / EDE-03 / EDE-04 | 每条 adapter 描述新增"部署假设"行，显式声明 v1 形态绑定（native = in-process，third-party = in-process，remote = out-of-process）；脱离假设的组合走 Remote 路径 |
| R1 | EDE §7 三种 Agent 形态对照表 | 新增"部署拓扑"列；追加底注"部署拓扑 vs 代码归属是两个独立维度" |
| R2 | EDE-02 处理流程 | 明确 native adapter 资源调用通过 platform bean 同步直调 M6，不必走 AgentEvent 桥接 |
| R2 | EDE-03 处理流程 + 约束 | 明确 third-party adapter 启动期替换框架 Model/Toolkit/Memory 抽象层为 platform bridge；启动期合规校验扫描注册表 |
| R2 | EDE-04 处理流程 | 明确 remote adapter 在 A2A 出站消息送 TTI-08 策略链做边界审计 |
| R2 | EDE-08 | 新增 Injection 模式表，给出三种 adapter 各自的注入路径；adapter 注册时声明 InjectionMode ∈ {NATIVE_DI / THIRD_PARTY_BRIDGE / EVENT_RELAY / NONE}；M5 据此选路径 |
| R3 | TTI C1 / C2 | C1 限定"适用于 in-process adapter"；C2 重写为"远端进程内自治 + 本进程仅在 A2A 出站边界审计"；新增 C8 显式声明 M6 同时暴露同步入口与事件桥接入口 |
| R3 | TTI-01 | 处理流程区分同步入口（Native/Third-party 直调）与事件桥接入口（少数三方异步路径）；强调两种入口落同一处理链 |
| R3 | TTI §7 协作表 | 拆分"接收 *_REQUEST"为"同步入口"与"事件桥接"两行；新增"远端 A2A 出站消息边界审计"行 |

### 10.4 三种 Agent 形态最终拦截策略

| 形态 | 拦截范围 | 实现机制 | 兜底措施 |
|---|---|---|---|
| Native（in-process） | 5 类资源调用全拦 | DI 注入 `PlatformChatClient / PlatformToolCallback / PlatformMemoryProvider / PlatformRetriever` Spring bean；adapter 代码内同步直调 | adapter 代码 review + 静态扫描禁止 `new` vendor SDK |
| Third-party（in-process，如 AgentScope-java） | 5 类资源调用全拦 | 启动期替换三方框架 `Model / Toolkit / Memory` 抽象实例为 `PlatformModelBridge / PlatformToolkitBridge / PlatformMemoryBridge`；bridge 内部转入 M6 同步入口 | 启动期合规校验（注册表全扫描，不全 = 拒绝注册）+ ClassLoader 隔离 + 自定义 Hook/Tool 静态扫描禁用清单 |
| Remote（out-of-process） | **本进程不拦** | EDE-04 通过 A2A 协议直连远端；远端 Agent 内部 model/tool 调用由远端服务自治 | A2A 出站消息内容经 M6 TTI-08 策略链做出域审计 / PII 脱敏 / 敏感词扫描；远端服务的内部审计/合规由远端自行保证；可观测性靠 A2A 协议暴露的 progress / token 事件 + 远端审计日志 |

### 10.5 遗留到 v2 的关联问题

- **跨进程统一治理**（如 v1 native Agent 想拆 sidecar 的过渡方案）：可考虑"Managed Remote / Resource Gateway"模式——远端通过 OpenAI-compat / MCP 接口反向调本进程 M6 platform bean，实现"远端逻辑 + 本地资源治理"组合。v1 不做。
- **三方框架自定义 Hook / 自定义 Tool 实现绕过桥接的 100% 防御**：v1 静态扫描兜底，v2 考虑 Java agent 字节码改写 + SecurityManager 替代品（JEP 411 后需另选方案）。
- **A2A 协议升级或 MCP 进入 v1.x 时 native/third-party 拦截范围的扩展**：保留 5 类拦截 kind 枚举的扩展点。

## 11. v1.2 增量修订（人类挑战驱动）

### 11.1 触发问题

> 提示词在哪里拼装、上下文如何持久化？三种 Agent 形态下提示词是否应该跟着 Agent 管理，AgentService 管好上下文就够，它根本无法控制 Agent 实现会在哪里拼装？

### 11.2 反思发现

人类判断完全成立。复盘后发现 v1 文档把 **"prompt 构造"** 作为 M6 的专属能力是职责入侵：

- **Native Agent**：ReAct / Plan-Execute / Reflexion 等不同 Agent 形态各有 system prompt 风格、scratchpad 格式、observation 注入位置——这是 Agent 业务逻辑核心，不是平台基础设施。M6 接管要么内置所有 Agent 模板（违反单一职责），要么 Agent 给 M6 的"草稿"已等同于自己拼了一遍。
- **Third-party AgentScope-java**：它有自己的 `Formatter` + ReActAgent 内部装配链。**根本无法**让 AgentScope 调 M6 来拼——它只调自己的 Formatter。能拦的只是 `Model.invoke(messages)` 出口，此时 messages 已是成品。
- **Remote Agent**：完全在远端进程拼，A2A 协议只传业务消息，本进程既看不到也管不了。

三种形态只有 native 一种能强行落地"M6 拼"——这种设计就是错的。

### 11.3 正确边界

**AgentService 的责任**：管好上下文事实源（STM-04 append-only）+ 提供上下文读取 SPI（PlatformMemoryProvider）+ 在 Agent 拼好的 messages 上做 messages-in-flight 切面治理（M6 TTI-02：策略 / 脱敏 / token 预算审计 / 必要时兜底裁剪）。

**Agent 的责任**：自决 prompt 形态（system / history / retrieval / tool spec 怎么拼），通过 PlatformMemoryProvider 读 STM-04，按自己的窗口与 prompt 策略组装 messages，再调 PlatformChatClient 出去。

类比：M6 是 messages-in-flight 切面 ≈ HTTP API Gateway。Gateway 不替业务后端拼 body，它只做鉴权 / 限流 / 大小检查 / 内容策略。

### 11.4 修复落地

| 修复点 | 文件 | 修复内容 |
|---|---|---|
| 模块定位重写 | TTI §1 | M6 不再叫"Prompt/上下文翻译层"；改为"资源访问统一拦截层 + 边界治理切面" |
| 核心约束 C3 重写 | TTI §2 | 从"Prompt 与上下文裁剪是 M6 专属能力"反转为"Prompt 不由 M6 构造" |
| TTI-02 重写 | TTI §4 | 从"Prompt 构造与上下文裁剪"改名为"Messages 切面治理（边界审计 + 兜底裁剪）"；输入是现成 messages，输出是 GovernedMessages（含 budgetSnapshot / 脱敏标注） |
| TTI-03 改写 | TTI §4 | 输入字段从 `draftPrompt` 改为 `messages: List<Msg>`；流程明确 M6 不读 STM-04 拼 history、不调 RAG 拼 chunk、不拼 system |
| TTI-06 RAG 改写 | TTI §4 | RAG 只做 retrieve；chunk 是否注入、注入到哪、怎么拼，由 Agent 自决；"M6 在 prompt 构造阶段把 chunk 内容按 token 预算注入"删除 |
| 数据契约 | TTI §5 | `BuiltPrompt` 删除；新增 `GovernedMessages`（M6 切面后产物） |
| "不做的事"补强 | TTI §9 | 显式列入"不构造 prompt"与"不拥有上下文事实源" |
| STM-04 描述补强 | STM §4 | 明确 STM-04 是上下文事实源，不参与 prompt 拼装；上下文读取由 PlatformMemoryProvider 转发，Agent 自取自拼 |
| EDE-02 增 Prompt 拼装责任行 | EDE §4 | 明确 native Agent 自己拼 messages；通过 PlatformMemoryProvider 拉 STM-04，PlatformChatClient.invoke 出去 |
| EDE-03 增 Prompt 拼装责任行 | EDE §4 | 明确三方框架自带 Formatter 拼；M6 无法接管，只能在 `Model.invoke(messages)` 出口做切面 |
| EDE-04 增 Prompt 拼装责任行 | EDE §4 | 明确远端服务自治 prompt 与上下文；A2A 只传业务消息；threadId 表达会话归属，远端自行决定历史引入策略 |
| 场景 Phase 2/4/5 重写 | Scenario | M5 step 改为：PlatformMemoryProvider 读 STM-04 → Native Agent 自拼 messages → PlatformChatClient.invoke；M6 step 改为切面治理；契约表 BuiltPrompt → GovernedMessages |
| §4.6 M6 表更新 | Scenario | "三轮 LLM" 一行：从 "prompt 构造 + 上下文裁剪 + token 预算" 改为 "Agent 已拼好的 messages 上做切面治理"；新增 "上下文读 SPI" 行 |

### 11.5 三种 Agent 形态 prompt / 上下文最终责任分配

| 形态 | Prompt 拼装位置 | 上下文获取方式 | M6 在 MODEL_REQUEST 时做什么 | 上下文持久化 |
|---|---|---|---|---|
| **Native** | Agent 代码自己拼 | DI 注入 `PlatformMemoryProvider` Spring bean → 读 STM-04 | 在 messages 上做切面：策略链 / token 预算审计 / 必要时兜底裁剪 / 审计落事件 | STM-04 append-only |
| **Third-party**（AgentScope-java 等） | 三方框架自带 Formatter | `PlatformMemoryBridge` 替换框架 `Memory` 抽象，框架透明拉 STM-04 | 同上（拦的是框架 `Model.invoke(messages)` 出口） | STM-04 append-only |
| **Remote** | 远端进程内拼，本进程不可见 | 远端自治；A2A `threadId` 表达会话归属 | 仅在 A2A 出站消息边界做策略链审计；不拦内部 model 调用 | 远端自治（本进程 STM-04 仍记录与远端 Agent 的 message 往来作为审计追溯） |

### 11.6 v1.2 之后的核心约束更新

- **AgentService 管事实，Agent 管表达**：上下文事实由 STM-04 append-only 管理（事实源唯一性）；prompt 表达由 Agent 自决（业务逻辑灵活性）。
- **M6 是 messages-in-flight 切面，不是 messages 构造器**：类比 HTTP API Gateway 与业务后端的关系。
- **"无法控制" 不等于 "不治理"**：远端 prompt 本进程虽不可见，但 A2A 出站消息仍要过 TTI-08 策略链；远端 Agent 内部资源调用虽不可拦，但 A2A 协议事件（progress / token / tool_progress）仍翻译为平台 AgentEvent 落 STM-09 审计。
