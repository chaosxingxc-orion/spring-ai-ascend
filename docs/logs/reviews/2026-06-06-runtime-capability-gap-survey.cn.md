# 全局职责划分（AgentService / AgentRuntime）+ 异构框架接入能力缺口

> 2026-06-06 | 设计输入 / 调研归纳 | 级别：[解读] | 不在本文档拍板 ADR
> 触发：`docs/logs/reviews/2026-06-05-openjiuwen-runtime-deep-analysis.md`（专家对 DeepAgent 演进的质疑）
> 方法：源码/文档级证据归纳；外部框架读自 `D:\ai-research\agent-platforms-survey`
>
> **本稿修订说明**：初稿是 Runtime 单边视角（把 AgentService 划到框外当"装载方"）。本稿改为**先做
> AgentService↔AgentRuntime 全局职责划分，再分别给两个模块的缺口**。owner 已定 gating 决策：
> **执行态留 Runtime、权威 Run 记录上移 AgentService**（部分修订 ADR-0159 的 run-owning-runtime）。

---

## 0. 结论先行

1. **架构是两个模块的事，不是 Runtime 一家的事。** 责任沿"控制面 / 数据面"切：AgentService = 控制面
   （管舰队：注册/路由/恢复编排/权威记录/治理），AgentRuntime = 数据面 worker（与框架 1:1，执行单个
   run + 暴露被管 hooks）。
2. **当前中立缝是有损细腰**：`InvocationRequest`(text-first) → `Flow.Publisher<RunEvent>{ACCEPTED,
   CHUNK,COMPLETED,FAILED}+String`，两端（框架原生流、A2A 协议）都富，中间拍平。调研 12 框架，结论收敛。
3. **gating 决策（owner 定）**：区分**执行态生命周期**（in-flight，留 Runtime，可自恢复本实例未完 run）
   与**编排态/权威记录**（system-of-record + 跨实例恢复，上移 AgentService）。→ 需起草 **ADR-0159 修订**。
4. **白送收益**：拆分后 **G12 双写在 Runtime 侧结构性消失**——Runtime 只发事件、AgentService 单写权威
   记录，不再"mark 状态 + append 输出"双写权威态。

---

## 1. 范围与方法

调研 12 框架（点名批 + Java 同源；hermes 跳过；混合深度）。证据来源：

| 框架 | 接入 | 深度 | 关键证据 |
|---|---|---|---|
| dify | 远程 SSE | 源码 | `task_entities.py` StreamEvent(28)、`queue_entities.py` QueueEvent |
| langgraph(py) | 远程 | 源码 | `types.py`(StreamMode/StateSnapshot/Interrupt/Command)、`checkpoint/base`(Checkpoint/BaseCheckpointSaver) |
| agentscope-java | in-proc | 源码 | `event/AgentEventType.java`、`middleware/` |
| agentscope-runtime-java | 部署层 | 源码 | `adapters/AgentHandler.java`、`engine/DeployManager.java`、`lifecycle/RunnerShutdownListener.java` |
| langgraph4j | in-proc | 源码 | `checkpoint/{Checkpoint,BaseCheckpointSaver}.java`、`state/StateSnapshot.java` |
| langchain(py) | 远程 | 能力 | astream_events |
| langchain4j | in-proc | 能力 | `StreamingChatResponseHandler`、`TokenStream`(onPartialThinking/onToolExecuted) |
| agent-core-java | in-proc | 能力 | hierarchical_group / interact / permissions / context_evolver |
| opencode | 远程/子进程 | 能力 | `packages/sdk`：message.part.delta / file.* / session.children / session.compact |
| openclaw | 远程 | 能力 | 个人助理 + Gateway=控制面 + 多渠道 |
| spring-ai-alibaba | in-proc | 能力 | Graph(StateGraph/CompiledGraph/NodeOutput)+checkpoint — 🚩**竞品：仅参考，不进 Maven 依赖** |
| openjiuwen | in-proc | 基线 | `adapters/openjiuwen/`，对照基准 |

判据（让缺口列表"合理"）：一个能力**只有"必须跨缝 或 必须被某模块持有/协调"才算缺口**；纯框架内部
机制（D6 排除）不算。每条缺口同时标注**归属模块**。

---

## 2. 全局职责划分（本稿主产物）

### 2.1 三条缝

```
S1 框架 ↔ AgentRuntime        : AgentDriver / RunEvent typed 词汇（数据面内部）
S2 AgentRuntime ↔ AgentService : 被管 hooks(暴露/上报) + 控制命令(接受/执行) + 事件流(上报)
S3 AgentService ↔ 外部          : 舰队级网关/路由 / 多租户 / 治理 / UI（单实例 A2A serving 仍在 Runtime，D9）
```

### 2.2 能力 → 模块（拆分后）

| 能力 | AgentRuntime（数据面，1:1 框架） | AgentService（控制面，管舰队） |
|---|---|---|
| 执行单个 run（调框架、跨 S1、流式） | ✅ 拥有 | — |
| S1 typed 事件词汇（G1-4,G7） | ✅ 定义 + 发射 | 消费 |
| 框架适配器 + 框架内 tools/memory/middleware | ✅ 内部(D6) | — |
| health/readiness/metrics | 暴露端点 + 发射 | 探测 + 聚合 + 计费 + 告警 |
| cancel / drain | 接受 + 执行 | **决策（何时）** |
| resume | 接受 + 执行（带载荷） | **选择目标 run + 决策** |
| 本实例 in-flight 执行态 + 自恢复本实例未完 run | ✅ 持有 | — |
| **权威 Run 记录（system-of-record）+ 持久化** | 上报事件 | ✅ **拥有（单写）** |
| **Run 编排态 FSM / 跨会话任务表** | 执行态 FSM（in-flight） | ✅ **编排态权威 FSM** |
| 注册/发现（registry/informer） | 自描述 + 心跳上报 | ✅ 拥有注册表 |
| 路由（含会话粘性 = 单写保证） | — | ✅ 拥有 |
| liveness 检测 + 跨实例恢复编排（死实例→重排） | 上报 run→本实例归属 | ✅ 拥有 |
| 幂等 / 去重 / 重试编排 | 接受 + 透传 token | ✅ 拥有编排 |
| 扩缩决策 / 背压消费 | 上报负载 + 本地容量上限 | ✅ 拥有决策 |
| 配置 / 凭证 | 接收 + 应用 | ✅ 拥有分发 |
| 跨实例监控聚合 / 治理 / 审计 | 发射 | ✅ 拥有 |
| S3 舰队网关 / 多租户 / UI | 单实例 A2A serving | ✅ 拥有舰队门面 |

> **gating 决策落点**：粗体两行是从初稿 Runtime `[座]` 桶上移到 AgentService 的核心。区分原则——
> **执行态**（单 run 的 in-flight 进度，Runtime；丢了顶多重跑这一实例的活）vs **编排态/权威记录**
> （舰队里有哪些 run、在哪、什么状态、恢复依据，AgentService；这是 system-of-record）。

---

## 3. 调研证据矩阵（缝 S1，每框架的事件/状态富度）

| 框架 | 原生事件分类（③） | 状态/回退（⑥） |
|---|---|---|
| dify | 28 种 SSE：agent_thought/agent_log(推理)、message_file/tts(多模态)、workflow/node/iteration/loop_*、human_input_*(HITL)、retriever_resources(RAG)、pause/retry/stop | workflow_paused + 表单 HITL + 节点重试 |
| langgraph(py) | 7 StreamMode：values/updates/**messages**(token)/**checkpoints**/**tasks**/debug/custom | `Checkpoint{id单调}`、`StateSnapshot{values,next,parent_config}`、`CheckpointMetadata{source∈input/loop/update/**fork**,parents}`、`BaseCheckpointSaver`(put/get/list=time-travel)、`Command.resume` |
| agentscope-java | AGENT/MODEL_CALL_START/END、TEXT_BLOCK_*、**THINKING_BLOCK_***、**DATA_BLOCK_***、**TOOL_RESULT_DATA_DELTA** | flat AgentState；实例级 running CAS + activeEventSink 单例 |
| langgraph4j | NodeOutput/StreamingOutput | `Checkpoint{id,state,nodeId,nextNodeId}`、`BaseCheckpointSaver{list/get/put/release}` |
| langchain4j | onPartialResponse/**onPartialThinking**/onToolExecuted/onComplete/onError | ChatMemory；无内建跨会话 checkpoint |
| agent-core-java | ReAct + **hierarchical group**(子代理) + interact(HITL) + permissions | context_evolver |
| opencode | message.part.delta、file.edited/read、**session.children**(子代理)、**session.compact**、session.abort | session 持久 + compact |
| spring-ai-alibaba | Graph NodeOutput（仿 langgraph） | StateGraph + checkpoint/OverAllState |
| openjiuwen | result_type∈{answer,interrupt,error} | openJiuwen Checkpointer（框架内） |

**收敛结论**：③⑥两维上无一例外比我方 `RunEvent` 富一个数量级；每个有状态框架都带 thread/session 维度
持久 checkpoint + 历史 + fork 血缘。

---

## 4. AgentRuntime 缺口（数据面）

桶：**[词]** S1 缝词汇 · **[座]** 数据面底座

| ID | 缺口 | 触发证据 | 桶 | v1(6/30)? |
|---|---|---|---|---|
| G1 | **typed 事件词汇贫乏**（需 message-delta/reasoning/tool-call/tool-result/structured/step/end） | dify(28)、agentscope-java、langchain4j、opencode | [词] | **是**（兑现 D8 观测） |
| G2 | 多模态/文件/结构化 payload 缺位（content 仅 String） | dify MESSAGE_FILE/tts、agentscope-java DATA_BLOCK、opencode file.* | [词] | 部分 |
| G3 | per-event 元数据**发射**（usage/model/error-code 字段；error 现为裸 String） | 各框架细粒度事件均带元数据 | [词] | **是**（聚合在 AgentService，但源在此） |
| G4 | branch/血缘 id（单 sequence，并发分支串成线性丢归属） | langgraph parents/fork、opencode session.children | [词] | 缓 |
| G5 | 入口塌缩：历史/variables/inputType 被压成 lastUserText | `EngineDispatcher.java:101-102/190-202` vs `EngineInput.java:16-23` | [词] | **是**（多轮上下文） |
| G6 | resume 语义跨缝 + resume 载荷（执行侧；选哪个 run 归 AgentService） | langgraph `Command.resume`、dify human_input_form_filled | [词] | **是**（HITL） |
| G7 | 子代理事件表达不出（`EngineAgentCallEvent` 抛异常） | `EngineDispatcher.java:279-281`；agent-core-java、opencode | [词] | 缓 |
| G8 | checkpoint token 入缝 + 本实例局部续跑（**权威持久归 AgentService**） | langgraph/langgraph4j `BaseCheckpointSaver`、SAA checkpoint | [词][座] | 缓（长程 DeepAgent 必咬） |
| G9 | 非真流式 + 120s 硬墙（collect-then-route；Dify 适配器阻塞读全量） | `EngineDispatcher.collect():209-248`、`STREAM_TIMEOUT_SECONDS=54` | [座] | **是** |
| G14(H1) | 优雅排水**执行**（SmartLifecycle/ContextClosed→drain） | 蓝本 `RunnerShutdownListener`/`DeployManager` | [座] | **是** |
| G16(H3) | health/readiness **端点** | — | [座] | **是** |
| G17(H4) | metrics/trace **发射**（仅关联日志） | `agent-bus TraceContext` 未接 | [座] | **是** |
| G15(H2) | 自描述 + 心跳**上报**（注册表本身归 AgentService） | `AgentHandler.getFrameworkType` | [座] | **是** |
| G20(H7) | 背压**上报** + 本地容量上限（无界 executor + 120s 钉线程） | — | [座] | 部分 |
| G21(H8) | config/凭证**接收 + 应用** | 蓝本 `AgentApp`/`LocalDeployManager` | [座] | 缓 |
| G10 | 执行树回溯/MCTS = **框架内部**（专家 StateNode 在 Deep 适配器内，Runtime 不碰） | — | 排除 | — |

---

## 5. AgentService 缺口 / 职责（控制面）— 初稿缺失的一半

这一半初稿全划在框外。拆分后它们是 AgentService 的一等职责（多数当前生产**几近绿地**，仅 example 网关有内存原型）：

| ID | 职责/缺口 | 来源（从初稿 Runtime 上移 or 新增） | v1? |
|---|---|---|---|
| S-A | **权威 Run system-of-record + 持久化（单写）** | 承接 G11 权威部分；消解 **G12 双写**（Runtime 只发事件） | **是** |
| S-B | **Run 注册表 + 实例注册表 + 发现（informer）** | 承接 G15 registry 部分 | **是** |
| S-C | **路由 + 会话粘性（= 单写保证，替代分布式锁）** | 新增（修正初稿"分布式锁"判断） | **是** |
| S-D | **liveness 检测 + 跨实例恢复编排（死实例→重排其 run）** | 承接 G19 编排部分 | **是** |
| S-E | **幂等 / 去重 / 重试编排** | 承接 G13 编排部分（Runtime 仅透传 token） | 缓 |
| S-F | **扩缩决策（消费 Runtime 背压）** | 承接 G20 决策部分 | 缓 |
| S-G | **配置 / 凭证分发** | 承接 G21 分发部分 | 缓 |
| S-H | **跨实例监控聚合 + 计费 + 治理 + 审计** | 承接 G3/G17 聚合部分 | 部分 |
| S-I | **S3 舰队网关 / 多租户门面 / UI** | 新增（单实例 A2A serving 仍在 Runtime，D9） | 部分 |

> 诚实代价（Host 模式 + 拆分）：副作用 exactly-once / 补偿 saga 仍归框架内部；AgentService 的天花板 =
> **at-least-once 投递 + 幂等编排 + 权威记录 + 跨实例恢复 + 审计**。

---

## 6. 分档建议（两模块，供排期，不在此拍板）

- **v1 已被咬**：
  - *AgentRuntime*：G9(真流式+去墙)、G5(入口保全)、G1+G3(typed 事件+元数据发射)、G6(resume 载荷)、
    G14-G17(缝 2 最小核心：drain/端点/发射/心跳)。
  - *AgentService*：S-A(权威记录+消双写)、S-B(注册表)、S-C(粘性路由)、S-D(liveness+跨实例恢复)。
- **可缓（框架/规模触发再做）**：G2、G4、G7、G8(checkpoint+time-travel)、G20/G21；S-E、S-F、S-G。
- **明确排除**：G10(MCTS=框架内部)；分布式锁（S-C 粘性路由替代）。

---

## 7. 开放问题 / ADR 锚点

1. **ADR-0159 修订（已定方向，待起草）**：Dec.1/Dec.4 的"run-owning agent-runtime"修订为——Runtime
   owns **执行态**；AgentService owns **权威记录 + 编排态 + 跨实例恢复**。`TaskControlService` 的编排态
   部分（注册/幂等/任务表/恢复）随之上移；执行态部分留 Runtime。
2. **typed 事件并集的边界**：富到何处停？锚定 A2A 子集 + 必要扩展，避免在中立层重造某框架事件模型。
3. **Checkpointable 是否设为可选 SPI 能力**：框架吐不透明 checkpoint token、AgentService 持久 + 回放。
4. **远程框架的 typed 事件映射表**：dify/opencode/langgraph-platform 各自 SSE 枚举 → 中立 typed 事件；
   每接一个框架补一行——**RuntimeExample 模块作量具**（owner 最初提议在此落地）。
5. spring-ai-alibaba 富词汇可借鉴，**不得进依赖**。
