# 提案：AgentService / AgentRuntime 职责拆分（逐模块逐类）

> 2026-06-06 | 提案 / 供架构 + 工程团队 review | 级别：[提议] | **本文不拍板，登记待决策事项**
> 配套证据：`docs/logs/reviews/2026-06-06-runtime-capability-gap-survey.cn.md`（12 框架接入缺口调研）
> 触发链：`2026-06-05-openjiuwen-runtime-deep-analysis.md`（DeepAgent 演进质疑）→ Host 模式确认 → owner 定 1:1 +
> 管理面归 AgentService → owner 定"拆：执行态留 Runtime、权威记录上移 AgentService"

---

## 1. 目的与边界

把当前 `agent-runtime` 单体按**控制面 / 数据面**拆成 **AgentService**（控制面，管舰队）与 **AgentRuntime**
（数据面 worker，与框架 1:1）。本提案做三件事：①逐模块逐类给出归属；②说明 carve-out 迁移影响；③登记
**待决策事项**供团队裁决。**agent-bus 本轮不在范围内**（ADR-0160 退役遗留，待单独梳理）。

### 1.1 已锁前提（owner 决策）

- **Host 模式不变**（守 D6：执行/工具/记忆/中间件 framework-internal）。
- **AgentRuntime ↔ 框架严格 1:1**；异构是舰队级属性。
- **管理面整体归 AgentService**；Runtime 只暴露被管最小 hooks（H1-H8，见配套调研）。
- **拆：执行态留 Runtime、编排态/权威记录上移 AgentService**（部分修订 ADR-0159 的 run-owning-runtime）。
- 同会话单写 = AgentService 粘性路由保证；Runtime 保留 JVM 本地锁，**无需分布式锁**。

---

## 2. 当前单体的真实流向（证据）

```
A2A 入口(A2aJsonRpcController/Handler)
  → AccessSubmissionService.run(AgentRequest)         [resolveSession + 提交编排]
    → SessionManager.loadOrCreate                      [会话]
    → TaskControlClient.run(RunCommand)                [编排 API]
      → TaskControlService: 建 Task + 幂等去重 + sessionLock + FSM
        → dispatch() → EngineDispatchApi.enqueueExecution   ← ★下行接口
          → EngineCommandGateway.publish → InternalEventQueue
            → EngineCommandProcessor(消费,线程池) → EngineDispatcher.dispatch
              → registry.find(AgentDriver) → RunCoordinator.stream
                → driver.invoke → OutputConverter → RunEvent 流(collect,120s)
              → EngineDispatcher.route(每个 RunEvent):
                  ├─ TaskControlClient.mark*()         ← ★上行接口(经 EngineTaskControlAdapter→FSM)
                  └─ AccessLayerClient.*Output()        ← ★上行接口(经 AccessNotificationClient→A2A egress)
```

**关键发现**：S2 边界处**已存在干净的进程内接口**：
- **★下行**（编排→执行）：`dispatch.api.EngineDispatchApi`（enqueueExecution/Resume/Cancel）。
- **★上行**（执行→编排/边缘）：`dispatch.port.TaskControlClient`（mark*）+ `dispatch.port.AccessLayerClient`（*Output）+ `dispatch.event.Engine*Event`。
- `bootstrap.AgentServiceBootstrapConfiguration` 当前**只装配这两条跨平面缝**（inbound seam + outbound seam）。

→ **拆分 ≈ 把这两个进程内接口提升为跨模块契约**。单体里这条缝是"潜伏"的，不是从零划。

---

## 3. 目标架构：两平面 + 两条 S2 缝

```
              S3 边缘(A2A/MQ ingress+egress, 多租户, UI 网关)
                         │  (fleet: 在 AgentService; standalone: 在 Runtime —— 见 D-1)
        ┌────────────────▼─────────────────┐
        │  AgentService（控制面 / 管舰队）   │
        │  权威 Run 记录 + Task FSM + 幂等   │
        │  会话注册/TTL + 注册发现 + 路由    │
        │  liveness检测 + 跨实例恢复编排     │
        │  扩缩 + 配置/凭证分发 + 监控聚合    │
        └──────┬───────────────────▲───────┘
   S2-down ★   │ EngineDispatchApi  │ Engine*Event / mark* / *Output   ★ S2-up
   (编排→执行) ▼ (enqueue exec/res/cancel) │ (执行→编排+边缘)
        ┌──────▼───────────────────┴───────┐
        │  AgentRuntime（数据面 worker, 1:1框架）│
        │  EngineDispatcher + 执行命令队列    │
        │  RunCoordinator + AgentDriver + 适配器│
        │  执行态薄记录(本实例自恢复) + 被管 hooks│
        └──────────────────┬───────────────┘
                    S1 │ AgentDriver / RunEvent
                  ┌────▼────┐
                  │  框架    │ (openjiuwen / dify / …)
                  └─────────┘
```

---

## 4. 逐模块逐类归属表

归属图例：**[R]** 留 AgentRuntime · **[S]** 搬/建 AgentService · **[S2]** 升为跨模块契约 · **[共]** 共享库 · **[?]** 待决策

### 4.1 `taskcontrol/` → 整块 [S]

| 类 | 归属 | 说明 |
|---|---|---|
| `Task`(JavaBean) | [S] | 权威 run 记录实体；其 state 是**粗粒度编排态**，不含框架 in-flight 进度 |
| `TaskState`/`WaitingReason`/`TaskFailureCode` | [S] | 编排 FSM 词汇。与 `common.RunPhase`（执行态）形成**双枚举**分界 |
| `TaskQueueRegistry` | [S] | run 注册表 + 会话内调度（用 [共] queue） |
| `TaskControlService` | [S] | 建 task/幂等/sessionLock/FSM(revision+allowed)/select；其 `dispatch()`→ [S2]下行 |
| `api/TaskControlClient` | [S] | 控制面命令 API（run/resume/cancel/mark*） |
| `EngineTaskControlAdapter` | [S2]上行消费端 | 拆后：AgentService 消费 Runtime 执行事件 → 写权威 FSM |
| `config/TaskControlAutoConfiguration` | [S] | AgentService boot |

### 4.2 `session/` → 整块 [S]

| 类 | 归属 | 说明 |
|---|---|---|
| `Session`/`SessionKey` | [S] | 轻量会话记录 + TTL；**非对话历史**（历史框架内部 D6） |
| `SessionManager`(+`Impl`) | [S] | loadOrCreate/TTL touch；Runtime 只需 sessionId(key) |
| `SessionStore`/`InMemorySessionStore`/`Factory` | [S] | 与 RunStore 同构成 AgentService 持久底座 |
| `session/config/*` | [S] | AgentService boot |

### 4.3 `access/` → 边缘，拆/dual（**见 D-1**）

| 类 | 归属 | 说明 |
|---|---|---|
| `core/AccessSubmissionService` | [S] | resolveSession + 提交编排（纯编排 intake） |
| `protocol/a2a/ingress/*`、`jsonrpc/*` | [?] D-1 | A2A 入口：fleet=AgentService 网关 / standalone=Runtime 自 serving |
| `protocol/a2a/egress/*`(A2aOutputMapper/Registry/…) | [?] D-1 | 出口映射，跟随入口所在 |
| `protocol/a2a/A2aWellKnownAgentCardController`、`A2aAccessProperties` | [R]自描述 / [S]注册表 | AgentCard 自描述在 Runtime(H2)，舰队注册表在 AgentService |
| `protocol/async/*`(MQ ingress) | [?] D-1 | 另一接入协议，跟随边缘 |
| `api/NotificationPort`、`model/*`、`AgentNotification`、`NotificationType` | [?] D-1 | egress 抽象，跟随边缘 |
| `config/AccessLayerConfiguration` | [?] D-1 | 边缘 boot |

### 4.4 `dispatch/` → 执行 [R] + 两条 [S2] 契约

| 类 | 归属 | 说明 |
|---|---|---|
| `dispatch/EngineDispatcher` | [R] | 执行驱动 + 发执行事件（不再双写权威态，见 §5 G12） |
| `command/*`(Processor/Gateway/Factory) | [R] | 执行命令消费 |
| `api/EngineDispatchApi`(+Default)、`Enqueue*Request`、`EnqueueEngineStatus` | [S2]下行 | AgentService 调、Runtime 实现（执行 intake） |
| `event/Engine*Event`(Started/Output/Completed/Failed/Interrupted/Cancelled/AgentCall) | [S2]上行 | Runtime 发 → AgentService/边缘 消费 |
| `model/EngineInput`、`EngineOutput`、`EngineExecutionScope`、`InterruptType`、`AgentCallMode` | [S2/共] | S2 DTO |
| `port/TaskControlClient`、`port/AccessLayerClient` | [S2]上行端口 | Runtime 发、对端消费 |
| `handler/AgentExecutionContext`、`config/*` | [R] | Runtime |

### 4.5 `engine/` → 整块 [R]（执行核）

| 类 | 归属 | 说明 |
|---|---|---|
| `RunCoordinator`、`spi/AgentDriver`(+Abstract)、`spi/OutputConverter` | [R] | 执行核 + S1 SPI |
| `adapters/openjiuwen/*`、`adapters/dify/*` | [R] | 框架适配器（含框架内 tools/memory/middleware，D6） |
| `registry/AgentDriverRegistry`(+Default) | [R]本实例 | 本实例承载的 agent；**舰队注册表在 [S]** |
| `config/EngineAutoConfiguration` | [R] | Runtime boot |

### 4.6 `queue/` → [共] 共享基础库

| 类 | 归属 | 说明 |
|---|---|---|
| `InternalEventQueue`、`InMemoryInternalEventQueue`、`QueueManager`、`config/*` | [共] | Runtime 用于执行命令队列；AgentService 用于 task 队列（各自实例化，见 D-4） |

### 4.7 `schema/` → [共] 共享 DTO 库

| 类 | 归属 | 说明 |
|---|---|---|
| `AgentRequest` | [S]intake / [共]类型 | 控制面 intake DTO（含 idempotencyKey/tenant/user） |
| `Message`、`Content`、`ContentType`、`Role`、`AgentResponse`、`RunStatus` | [共] | 两平面共享 payload 类型 |

### 4.8 `common/` → [R] 定义、[S2] 消费

| 类 | 归属 | 说明 |
|---|---|---|
| `InvocationRequest` | [R] | S1 入（→框架适配器） |
| `RunEvent`/`RunEventType`/`RunPhase` | [R]产出 / [S2]共享契约 | Runtime 定义+产出；AgentService/边缘消费（执行态 RunPhase ≠ 编排态 TaskState） |

### 4.9 `bootstrap/` → 拆

| 类 | 归属 | 说明 |
|---|---|---|
| `AgentRuntimeApplication` | [R] | Runtime boot（执行栈） |
| `AgentServiceBootstrapConfiguration` | [S2] wiring | 当前两条跨平面缝；拆后部分→AgentService boot + S2 传输装配 |
| `AccessNotificationClient` | [?] D-1 | egress 适配，跟随边缘 |

---

## 5. carve-out 迁移影响

1. **AgentService 现为空壳**（仅 `service/spi/package-info.java`）→ 拆 = 把 `taskcontrol` + `session` +
   `AccessSubmissionService` **整块迁出 agent-runtime、在 agent-service 新建**，并新建管理面（注册/路由/恢复/聚合）。
2. **AgentRuntime 显著变薄**：只剩 `engine` + `dispatch`(执行侧) + 执行命令 `queue` + `common`(S1) + 被管 hooks +
   执行态薄记录。
3. **两条潜伏接口升为跨模块契约**：`EngineDispatchApi`（下行）、`dispatch.port.*` + `Engine*Event`（上行）。
   传输形态见 **D-2**。
4. **G12 双写结构性消失**：现 `EngineDispatcher.route()` 同时 `mark*()`+`*Output()` 双写权威态；拆后 Runtime
   **只发一条执行事件流**，AgentService **单写**权威 Task 记录，边缘从同一事件流取输出。
5. **双枚举对齐分界**：`common.RunPhase`（执行态@R，骑事件流）vs `taskcontrol.TaskState`（编排态@S，权威 FSM）——
   代码里本就存在，拆分让它显式化。

---

## 6. 待决策事项登记（Decision Register）

| # | 决策 | 选项 / 权衡 | 暂定建议 |
|---|---|---|---|
| **D-1** | **边缘(access)归属** | (a) fleet：A2A/MQ ingress+egress 在 AgentService 网关，按 sessionId 路由到属主 Runtime；(b) standalone：每个 Runtime 自 serving（D9"web/app stays in runtime"）。张力：D2/D9 vs 舰队网关 | **dual，按 RuntimeHost 档位**：standalone=Runtime serving；fleet=AgentService 网关。`AccessSubmissionService` 逻辑随编排走 [S]，A2A 协议适配做成两边可复用的库 |
| **D-2** | **S2 传输形态** | 进程内(同 JVM 共置) / 内部 RPC / A2A。呼应已退役 ADR-0158 三传输 | 抽象成接口、**传输按部署档可换**（standalone 进程内；fleet RPC/A2A）。`EngineDispatchApi` + `Engine*Event` 即契约面 |
| **D-3** | **Runtime 执行态薄记录** | 为"自恢复本实例 in-flight run"，Runtime 要不要本地薄持久？薄到什么程度 | 仅"本实例正在跑的 run + 进度指针"，最小化；权威记录在 AgentService。需论证是否连这点都可省（AgentService 重投即可） |
| **D-4** | **queue 共享库** | 抽成共享 infra module / 各模块各自持有 | 抽成共享库；Runtime(执行命令)、AgentService(task 队列)各自实例化 |
| **D-5** | **schema 共享契约 module** | `AgentRequest`/`Message`/… 抽成独立 contract 模块 | 是；两平面 + S2 共享，避免双向依赖 |
| **D-6** | **会话→实例归属上报** | Runtime 如何把 session→本实例 归属上报给 AgentService（支撑粘性路由 + 跨实例恢复） | 走 H2 自描述 + H6 归属上报；契约待设计 |
| **D-7** | **ADR-0159 修订范围** | 把"run-owning agent-runtime"改为"Runtime owns 执行态、AgentService owns 编排态+权威记录"；`TaskControlService` 上移 | 起草 ADR-0159 修订（或新 ADR）；本提案 review 通过后进行 |
| **D-8** | **idempotency token 入缝(G13)** | 幂等编排在 AgentService，但工具级去重要 token 透传到框架 | token 需经 S2-down + S1 透传到适配器；与 typed 事件并集一起设计 |
| **D-9** | **RunPhase / TaskState 双枚举映射** | 两枚举的权威映射表（谁映射谁、在哪映射） | 在 S2-up 消费端（AgentService）维护 RunPhase→TaskState 映射 |

---

## 7. 建议后续步骤（不在本提案执行）

1. 架构 + 工程团队 review 本提案 + 配套缺口调研，裁决 D-1..D-9。
2. 据裁决起草 **ADR-0159 修订**（执行态/编排态切分）。
3. 然后开 **AgentRuntime 端详细调整计划**（含缺口调研里 v1 已被咬项的落地排期）。
4. agent-bus 退役遗留单独梳理（本轮搁置）。
