---
scope: version
module: agent-core
feature_type: functional
feature_id: FEAT-019
status: draft
dependency:
  - README.md
  - FEAT-002-heterogeneous-agent-framework-compatibility.md
  - FEAT-026-agent-runtime-parallel-tool-governance.md
  - DFX-001-trajectory-observability.md
---

# 并行下游工具任务（agent-core 部分） — 批次执行与结果归位

> **特性边界声明**：
>
> 本特性定义的 agent-core 统一并行执行能力（`AbilityManager` 并行执行、`OpenJiuwenExecutors` 共享线程池、`buildToolCallbackContext` 上下文隔离、`copyToolExtra` extra 副本）是**共享基础能力**，既支撑本特性（普通 ToolCall 并行），也支撑 [FEAT-004 远程 Agent 编排](./FEAT-004-remote-agent-orchestration.md) 的 SubAgent 并行执行。从 Agent 视角看，SubAgent 与普通 ToolCall 都是 LLM 规划的 ToolCall，统一由 `AbilityManager.execute(toolCalls)` 并行调度；差异仅在 Rail 层（普通 ToolCall 直接执行，SubAgent 经 `RemoteA2aInterruptRail` 转 A2A 中断交由 Runtime 远程执行）。
>
> 本特性明确不覆盖以下组件，这些组件属于 [FEAT-004](./FEAT-004-remote-agent-orchestration.md) 的范畴：
>
> - `RemoteA2aInterruptRail`：SubAgent ToolCall 转 A2A 中断的桥梁，属 FEAT-004 的 Rail 扩展点
> - `RemoteAgentToolSpec`：远程 Agent 工具描述，属 FEAT-004
> - `RemoteInvocationBatchCoordinator`、`ChildTaskEventForwarder`、`BatchObserver` 等 runtime 组件：属 FEAT-004 的 runtime 落地
>
> 本特性仅定义普通 ToolCall（本地函数、REST、MCP、文件、Shell、代码、Web、多模态等）的并行执行行为。

## 1. 特性定位

FEAT-019（agent-core 部分）定义 DeepAgent 在一次模型决策中生成多个彼此独立的 Tool Call 时，`agent-core` 如何把这些 Tool Call 形成同一批次、隔离每个调用上下文、并行执行、all-settled 汇聚并按稳定顺序一次性回灌 DeepAgent 推理循环。

```text
DeepAgent 一次生成多个独立子任务
   │
   ├─ tool-call-1 → Tool A ─┐
   ├─ tool-call-2 → Tool B ─┼─ all-settled 汇聚
   └─ tool-call-3 → Tool C ─┘
                              │
                              ▼
                    DeepAgent 单次继续推理
```

本部分的核心对象是 **Tool Call**，不是下游 Agent：

- 不创建子 Agent、远程 Agent 或 A2A child Task。
- 不调用 Agent Card，不维护远程 `taskId` / `contextId`，不进入远程 Agent 编排链路。
- openJiuwen 的 `TaskTool`、`SessionsSpawnTool` 等子智能体 / 会话委托工具，以及 SAA 的 `RemoteAgentToolSpec`，即使在接口表面表现为 Tool，也不属于 FEAT-019。
- 工作流只有被封装为普通 Tool、REST Tool 或 MCP Tool 后，才能作为本特性的下游工具；工作流本身不被解释为 Agent。

### 1.1 解决的问题

DeepAgent 已能在一次模型输出中生成多个 Tool Call。如果这些调用被串行执行，总耗时接近各工具耗时之和；如果简单无界并发，又会造成共享状态冲突、外部副作用重复、后端过载、权限绕过和结果串线。

FEAT-019 的 `agent-core` 部分负责：

- 校验 Tool Call，建立并行工具批次；
- 为每个 Tool Call 创建独立调用上下文，避免兄弟调用间共享可变状态；
- 以 all-settled 方式并行等待全部调用完成、失败、取消或超时；
- 按 `tool_call_id` 将结果稳定映射回 ToolMessage 并一次性回灌 DeepAgent。

并发预算、权限、deadline、冲突控制、审计和可观测性等治理策略由 [FEAT-026（agent-runtime 部分）](./FEAT-026-agent-runtime-parallel-tool-governance.md) 承担。

### 1.2 openJiuwen 对齐事实

当前 openJiuwen 开源实现已经具备本部分的基础：

- `DeepAgentConfig.parallel_tool_calls` 和 `create_deep_agent(..., parallel_tool_calls=True)` 支持开启并行 Tool Call，默认值为 `True`。
- ReActAgent 将同一轮生成的多个 Tool Call 一次交给 AbilityManager。
- AbilityManager 为每个调用创建隔离的 callback context，并以 all-settled 方式并行等待，单项异常不会直接取消其他项。
- openJiuwen 同时提供统一 Tool / ToolCard 抽象、内置工具、MCP 接入以及本地函数和 REST API 工具封装。

FEAT-019（agent-core 部分）不重复定义 openJiuwen 的类级实现，而是规定 `agent-core` 应承诺的批次执行、上下文隔离、结果归位和失败语义。

## 2. 术语与职责

| 术语 / 角色 | 定义或职责 |
|---|---|
| 父执行 / parent invocation | 当前 DeepAgent 的一次 ReAct / task-loop 执行；并行工具调用仍属于同一 Agent 执行。 |
| 并行工具批次 | DeepAgent 在同一轮输出中生成、且声明为无前置依赖的一组 Tool Call。 |
| 工具子任务 | 批次中的一个逻辑子任务，与一个确定的 Tool Call 一一对应；不是 Agent Task。 |
| `tool_call_id` | 工具子任务的稳定关联键，用于输入、结果、错误、轨迹和模型上下文归位。 |
| DeepAgent | 生成子任务、选择工具、提供参数并消费汇聚结果；不直接管理线程、连接池或全局并发。 |
| `agent-core` | 校验 Tool Call，建立批次，调用 AbilityManager，隔离每个调用上下文并提交结果。 |
| `agent-runtime` | 提供并发预算、权限、deadline、冲突键、取消和可观测约束；不替代模型拆解业务任务（参见 [FEAT-026](./FEAT-026-agent-runtime-parallel-tool-governance.md)）。 |
| Tool provider | 执行本地函数、REST、MCP、文件、Shell、代码、Web、多模态等具体能力。 |

## 3. 支持的工具范围

"支持某类工具"表示该工具能够进入 `agent-core` 统一的批次校验、并发执行、结果归位、错误和轨迹链路；不表示该类工具在任何情况下都可以无条件并发。实际是否允许并行由 `agent-runtime` 治理策略决定（参见 [FEAT-026 第 4 章](./FEAT-026-agent-runtime-parallel-tool-governance.md#4-并行安全与调度策略)）。

### 3.1 openJiuwen 核心扩展工具

| 工具类别 | openJiuwen 形态 | FEAT-019（agent-core 部分）范围 |
|---|---|---|
| 本地函数工具 | `@tool` / `LocalFunction` / `Tool` | 支持；是否并发取决于函数纯度、线程安全和共享资源声明。 |
| REST API 工具 | `RestfulApi` / `RestfulApiCard` | 支持；受连接、目标服务限流、鉴权、幂等和副作用策略约束。 |
| MCP 工具 | `MCPTool` / `McpToolCard` / `McpServerConfig` | 支持；覆盖 stdio、SSE、Streamable HTTP、Playwright / OpenAPI 等客户端形态，实际并发受 MCP server 能力约束。 |
| 工作流工具 | 通过 Tool / REST / MCP adapter 暴露的 workflow | 支持工具化后的调用；工作流实例仍由工作流引擎拥有，不提升为 Agent Task。 |

### 3.2 openJiuwen DeepAgent 内置工具

| 工具类别 | 代表工具 / 能力 | 默认并行判断 |
|---|---|---|
| 文件系统 | Read、Write、Edit、Glob、Grep、ListDir | 只读调用可并行；写入同一路径或目录的调用必须冲突控制。 |
| Shell | Bash、PowerShell | 仅在工作目录、进程和外部资源互不冲突时并行；否则串行或隔离执行。 |
| 代码执行 | CodeTool | 使用独立 sandbox / namespace 时可并行；共享解释器、文件或端口时必须限流。 |
| 图像 | Image OCR、Visual Question Answering | 可按模型服务并发预算并行。 |
| 音频 | 转录、音频问答、元数据 | 可按模型 / 媒体服务并发预算并行。 |
| 视频 | Video Understanding | 支持，但受文件大小、GPU / 模型服务配额和超时约束。 |
| Web | WebFetch、免费搜索、付费搜索 | 只读请求可并行；必须遵守站点、供应商配额和成本预算。 |
| 浏览器自动化 | Playwright / browser runtime MCP 工具 | 不同隔离浏览器会话可并行；同一页面 / 会话中的有序交互默认串行。 |
| 移动 GUI | Android emulator / device GUI 工具 | 不同设备会话可并行；同一设备上的动作默认串行。 |
| Todo | Todo create / list / get / modify | 读取可并行；对同一 Todo 集合的修改必须串行或使用版本 / 冲突控制。 |
| Skill | ListSkill、SkillTool | 技能检索 / 读取可并行；Skill 是上下文和规则资产，不因并行调用而形成 Agent。 |
| 工具发现与加载 | SearchTools、LoadTools | 搜索可并行；同一 AbilityManager 的工具注册 / 加载必须保证幂等和一致性。 |
| 人机交互 | AskUserTool | 独占交互，不与其他 AskUser 或有副作用调用盲目并发。 |
| 定时任务 | Cron 工具集 | 查询可并行；创建、修改、删除同一计划任务需串行和幂等。 |
| LSP / 代码智能 | LspTool | 只读查询可并行；依赖同一 mutable workspace 的操作需与文件写入协调。 |
| Agent 模式 / 计划模式 | SwitchMode、EnterPlanMode、ExitPlanMode | 控制面工具，按父执行串行，不进入普通并行数据面。 |
| Worktree / Workspace | EnterWorktree、ExitWorktree | 不同隔离 worktree 可并行；同一执行的进入 / 退出和生命周期操作必须串行。 |

### 3.3 明确排除

| 排除项 | 原因 | 归属 |
|---|---|---|
| 下游 Agent、远程 Agent | 执行主体和生命周期是 Agent / A2A Task，不是普通 Tool Call | 独立多 Agent 编排特性 |
| `TaskTool`、子智能体 task tool | 实质是把任务委托给另一个 Agent | openJiuwen 子 Agent 能力，不属于 FEAT-019 |
| `SessionsSpawnTool`、`SessionsCancelTool` 等 | 实质是创建和管理子 Agent 会话 | openJiuwen async subagent 能力，不属于 FEAT-019 |
| `RemoteAgentToolSpec` | 虽以 Tool 暴露，但执行语义是远程 A2A Agent 调用 | 独立远程 Agent 编排能力 |
| 任意依赖图 / DAG | 同一批次只接受互相独立的 Tool Call | 工作流 / DAG 编排能力 |
| 无界并发 | 会造成资源、成本、安全和后端稳定性风险 | `agent-runtime` 并发与治理策略（FEAT-026） |

## 4. 外部接口与入口要求

| 入口 | 类型 | 事实要求 |
|------|------|------|
| `DeepAgentConfig.parallel_tool_calls` | agent configuration | 必须表达是否允许在同一轮模型决策中生成多个 Tool Call 并进入并行批次；默认值为 `True`。 |
| `create_deep_agent(..., parallel_tool_calls=True)` | agent factory | 必须在构造 DeepAgent 时根据配置开启并行 Tool Call 能力。 |
| ReActAgent 同轮多 ToolCall 提交 | agent-core invocation | ReActAgent 必须将同一轮生成的多个 Tool Call 一次性提交给 AbilityManager，不得逐个串行提交。 |
| `AbilityManager.execute(toolCalls)` | agent-core SPI | 必须接收多个 Tool Call，为每个调用创建独立 callback context，并以 all-settled 方式并行等待；单项异常不得直接取消其他项。 |
| `tool_call_id` | agent-core identifier | 每个 Tool Call 必须有稳定 `tool_call_id`，用于输入、结果、错误、轨迹和模型上下文归位；工具名相同不代表调用相同。 |
| `ToolMessage` 回灌 | agent-core context | all-settled 结果必须通过 `tool_call_id` 与原始调用一一对应，并按模型生成顺序写入 ToolMessage，一次性回灌 DeepAgent。 |
| before / after / error rail | agent-core rail | 每个调用必须独立触发 before / after / error rail，不共享可变 callback context。 |
| Tool / ToolCard 抽象 | agent-core tool spec | 必须支持本地函数、REST、MCP、工作流等工具通过统一 Tool / ToolCard 抽象进入并行批次。 |
| `agent-runtime` 治理策略查询 | runtime SPI | 批次预检时必须向 `agent-runtime` 治理策略查询权限、deadline、并行安全等级和 conflict key，作为分组调度依据（参见 [FEAT-026 第 4 章](./FEAT-026-agent-runtime-parallel-tool-governance.md#4-并行安全与调度策略)）。 |
| `agent-runtime` 并发预算配置 | runtime configuration | 并行执行必须受 `agent-runtime` 并发预算约束；单批次最大调用数默认值由 runtime 配置确定（参见 [FEAT-026 第 5 章](./FEAT-026-agent-runtime-parallel-tool-governance.md#5-有界并发与预算)）。 |

## 5. `agent-core` 能力要求

| 能力 | 要求级别 | `agent-core` 行为要求 |
|---|---|---|
| 单轮生成多个 Tool Call | MUST | 接收 DeepAgent 在一次模型决策中生成两个或以上的 Tool Call，并保持各自工具名、参数和 `tool_call_id`。 |
| 独立性前提 | MUST | 只有不依赖同批次其他结果的调用可以并行；有依赖调用必须拆成后续 ReAct 轮次。 |
| 工具范围覆盖 | MUST | 第 3 章所列 openJiuwen 工具均能进入统一调度链路；排除的 Agent 类工具必须被识别并转交其所属特性。 |
| 批次预检 | MUST | 执行前必须完成工具存在性、输入 schema 校验，并向 `agent-runtime` 治理策略查询权限、deadline、并行安全和资源冲突结果。 |
| 独立调用上下文 | MUST | 每个 Tool Call 使用独立 callback / rail context，不能共享可被兄弟调用覆盖的 tool name、args、result 或 error 槽。 |
| 有界并发 | MUST | 并行执行受 `agent-runtime` 并发预算约束；`parallel_tool_calls=true` 只表示允许并行，不表示无界（参见 [FEAT-026 第 5 章](./FEAT-026-agent-runtime-parallel-tool-governance.md#5-有界并发与预算)）。 |
| 同一工具多次调用 | MUST | 同一工具可以使用不同参数产生多个 Tool Call；是否并行取决于工具可重入性和 `agent-runtime` 资源冲突判定。 |
| all-settled 汇聚 | MUST | 默认等待批次内所有已启动调用完成、失败、取消或超时；单项失败不得自动取消其他独立调用。 |
| 稳定结果归位 | MUST | 结果通过 `tool_call_id` 与原始调用一一对应，并按模型生成的稳定顺序提交上下文，不依赖完成时间。 |
| 单次继续推理 | MUST | 一个工具批次汇聚后只触发一次 DeepAgent 后续推理，避免每个结果单独触发模型竞态。 |
| 部分失败可见 | MUST | 每个失败项返回工具名、`tool_call_id`、错误类型、可重试性和必要诊断；成功结果同时保留。 |
| 超时 | MUST | 每个 Tool Call 和整个批次都应有 deadline / timeout；一个超时项不能无限阻塞整个批次。 |
| 取消 | MUST | 接到取消通知时，尚未启动的调用不再执行，运行中的调用 best-effort 取消；迟到结果不得重新激活已取消执行。 |
| 串行兼容 | MUST | `parallel_tool_calls=false` 时保持原始 Tool Call 顺序串行执行，不改变输入输出契约。 |

## 6. 批次形成与执行语义

### 6.1 批次形成

- 一个批次只包含同一 DeepAgent、同一模型轮次生成的 Tool Call。
- 每个调用必须有唯一 `tool_call_id`；工具名相同不代表调用相同。
- DeepAgent 不得把"先读取数据，再根据数据写入结果"放进同一并行批次。
- 需要逐实体调用同一工具时，每个实体形成独立 Tool Call；是否并行取决于该工具的可重入性和资源冲突键（由 `agent-runtime` 判定）。
- 超过单批次最大调用数时，`agent-core` 不得静默丢弃；应拒绝整个批次、只接受策略允许的前 N 项并明确返回剩余项，或要求 DeepAgent 分批生成。具体策略由 `agent-runtime` L2 配置固化。

### 6.2 执行与汇聚

- 预检通过后，`agent-core` 按 `agent-runtime` 返回的安全等级和 conflict key 对调用分组。
- 可并行组在并发预算内启动；同一冲突组内部串行。
- 每个调用独立触发 before / after / error rail，不共享可变 callback context。
- all-settled 结果保持模型生成顺序，并通过 `tool_call_id` 写回对应 ToolMessage。
- 工具返回先后只影响实时进度，不影响结果身份和最终上下文顺序。
- 全部结果写入上下文后，DeepAgent 才进入下一次模型推理。

### 6.3 失败与中断

- 工具不存在、参数非法或权限拒绝（由 `agent-runtime` 治理策略返回）在预检阶段产生该调用的 `rejected` 结果。
- 运行异常、provider 限流、网络错误和工具业务错误形成独立 `failed` 结果。
- `timeout`、`canceled`、`rejected` 和 `failed` 必须可区分。
- 某一调用要求用户确认或输入时，不得借此授权其他调用；交互型调用按 P4 独占策略处理。
- 如果某工具无法安全取消，父执行取消后其结果只能进入审计，不得继续驱动 DeepAgent。

## 7. 外部状态与结果

工具子任务只形成**调用级状态**，不形成 A2A Task 状态机：

| 状态 | 含义 |
|---|---|
| `pending` | Tool Call 已生成，等待预检或执行槽 |
| `running` | 工具正在执行 |
| `completed` | 工具成功返回结果 |
| `failed` | 工具执行失败 |
| `rejected` | 工具、参数、权限或并行策略校验未通过，未执行 |
| `canceled` | 随父执行取消或在启动前被取消 |
| `timeout` | 单调用 deadline 到期 |

父 Agent 的 A2A Task 仍由现有 `agent-runtime` 状态机拥有。并行工具批次只是父 Agent 执行过程中的内部阶段：成功与部分失败结果由 DeepAgent 消费，最终父 Task 状态由 DeepAgent 执行结果决定。

每个结果项至少包含：

| 字段语义 | 要求 |
|---|---|
| batch reference | 关联本轮工具批次 |
| `tool_call_id` | 与模型生成调用一一对应 |
| tool name / category | 标识实际工具和工具类别 |
| status | completed / failed / rejected / canceled / timeout |
| result / resultRef | 成功输出、受控引用或摘要 |
| error | 错误码、消息、阶段和可重试性 |
| timing | 排队、开始、结束和耗时 |

## 8. 用户场景

### 8.1 多源信息并行读取

```text
用户：结合项目文件、官网信息和内部 REST 数据生成分析

DeepAgent 同一轮生成：
  1. ReadFileTool(project.md)
  2. WebFetchWebpageTool(official_url)
  3. RestfulApi(query_internal_data)

三个调用均为只读，在预算内并行；all-settled 后按 tool_call_id 回灌，DeepAgent 统一分析。
```

### 8.2 同一工具处理多个实体

DeepAgent 为 A、B、C 三个实体分别生成一个 REST Tool Call。接口声明幂等且允许并发，三个调用可并行；如果 provider 并发上限为二，则两个运行、一个排队，但结果仍按 A、B、C 的原始顺序归位。

### 8.3 文件读写冲突

DeepAgent 同一轮生成两个文件读取和两个文件写入：读取不同文件可并行；写入同一路径的两个调用必须按路径 conflict key 串行或拒绝，不能因 `parallel_tool_calls=true` 同时覆盖文件。

### 8.4 多模态并行处理

DeepAgent 同时调用图像 OCR、音频转录和视频理解。三个工具使用不同输入资源，可在各自模型服务预算内并行。任一模型服务超时只形成该 Tool Call 的超时结果，其余多模态结果仍被保留。

### 8.5 浏览器与移动设备

两个隔离浏览器会话或两台不同移动设备可以并行；同一浏览器页面中的"点击—输入—提交"以及同一移动设备上的连续动作必须保持顺序，不能拆成盲目并行调用。

### 8.6 部分失败

三个 Web / MCP 调用中一个返回限流错误。批次等待其余调用完成，将两个成功结果和一个可重试失败一并回灌 DeepAgent；DeepAgent 可以基于成功结果降级回答，或在下一轮单独重试失败项。

## 9. 行为不变量

1. **子任务等于 Tool Call，不等于 Agent Task。**
2. **一个 Tool Call 一个稳定 `tool_call_id`。**
3. **一次工具批次只触发一次 DeepAgent 后续推理。**
4. **完成顺序不改变结果身份和上下文提交顺序。**
5. **单项失败不取消其他独立调用。**
6. **允许并行不等于无界并发。**
7. **相同工具名不代表可以安全并行。**
8. **共享写入、外部副作用、交互和控制工具必须保守调度。**
9. **每个调用独立通过权限、Rail、超时和审计。**
10. **Agent proxy 即使包装成 Tool，也不进入 FEAT-019。**

## 10. 最小验收用例

| 用例 | 前置条件 | 预置数据 | 预期结果 |
|---|---|---|---|
| 一轮生成三个只读 Tool Call | 注册三个只读 openJiuwen 工具；并发预算 ≥ 3（引用 FEAT-026 默认值） | 三个 ToolCall 分别指向独立只读工具 | 三个调用时间窗口重叠；结果按原始顺序和 `tool_call_id` 归位；DeepAgent 只继续推理一次。 |
| `parallel_tool_calls=false` | DeepAgent 配置 `parallel_tool_calls=false` | 三个 ToolCall 指向任意工具 | 三个调用严格按生成顺序串行，输入输出契约不变。 |
| 同一 REST Tool 三组参数 | REST 工具声明可重入；provider 允许并发（FEAT-026 判定） | 三个 ToolCall 使用同一 REST 工具不同参数 | 工具和 provider 允许并发时有界并行；不允许时排队或串行，不串结果。 |
| 一个调用抛出异常 | 批次含三个独立 ToolCall；其中 1 个工具会抛异常 | 批次含 3 个 ToolCall | 其他独立调用继续；结果集中同时存在成功项和失败项。 |
| 一个调用超时 | 批次含 3 个 ToolCall；其中 1 个工具响应时间 > deadline | 批次含 3 个 ToolCall | 超时项被标记并停止阻塞；其他成功结果保留。 |
| 同一路径两个 WriteFile | FEAT-026 治理策略识别同路径为 conflict key | 两个 WriteFile 指向同一路径 | 不得并行覆盖；按 conflict key 串行或在预检阶段拒绝。 |
| 不同 worktree 两个代码任务 | workspace 隔离成立（FEAT-026 判定） | 两个 CodeTool 在不同 worktree | workspace 隔离成立时允许并行，文件和进程互不污染。 |
| 同一浏览器页面多个动作 | FEAT-026 治理策略识别同页面为独占 | 同一浏览器页面连续 3 个动作 ToolCall | 按页面 / 会话串行，保持交互顺序。 |
| 两个 AskUserTool | AskUser 工具声明 P4 独占（FEAT-026） | 两个 AskUserTool ToolCall | 不同时向用户提出竞争问题；按父执行独占串行。 |
| REST / MCP 外部副作用 | FEAT-026 要求独立鉴权与幂等 | 3 个外部副作用 ToolCall | 每项独立鉴权、确认和幂等；不得复用另一项的批准。 |
| 大型多模态结果 | 多模态工具配置受控引用或摘要返回 | 3 个多模态 ToolCall | 返回受控结果或引用，不因多个结果同时内联导致上下文失控。 |
| TaskTool / RemoteAgentTool | Agent 类工具识别规则加载 | 1 个 TaskTool + 1 个 RemoteAgentTool | 被识别为 Agent 委托并排除，不进入普通工具并行批次。 |
| 取消父执行 | 批次已发起且至少 1 个调用 RUNNING | 批次含 3 个 ToolCall | 未启动调用停止，运行调用 best-effort 取消，迟到结果不触发新一轮推理。 |
| 轨迹检查 | DFX-001 轨迹链路接入 | 批次已完成 | 可按 batch 和 `tool_call_id` 查看工具类别、状态、耗时和错误，不包含原始 Chain-of-Thought。 |

在三个无排队、无资源冲突且耗时相近的工具调用场景中，并行性能目标为：

```text
T_batch <= 1.3 × max(T_tool_1, T_tool_2, T_tool_3)
```

该目标不适用于 provider 限流、工具串行策略、用户确认、资源冲突、重试或 sandbox 冷启动场景。

## 11. 与其他特性的关系

- **FEAT-002 异构 Agent 框架兼容**：openJiuwen adapter 将 DeepAgent 的多 Tool Call、ToolMessage、Rail 和结果归一到 runtime 可观察语义；框架私有 callback context 不提升为公共 runtime Task。
- **FEAT-026 并行下游工具治理（agent-runtime 部分）**：承担并发预算、权限、deadline、冲突键、取消和可观测约束；本特性的批次预检、分组调度和取消动作均依赖 FEAT-026 的治理策略返回结果。
- **DFX-001 轨迹可观测性**：补充 batch、`tool_call_id`、工具类别、并行度、排队、冲突、耗时、错误和取消轨迹。
- **Skill / 工作流能力**：Skill 是可加载上下文和规则资产；workflow 只有通过 Tool / REST / MCP adapter 暴露后才进入本特性，其实例状态仍由工作流引擎拥有。

## 12. 当前版本不承诺

- 不支持下游 Agent、子 Agent、远程 Agent、Agent Team 或 A2A Task 的并行编排。
- 不支持把 TaskTool、SessionsSpawnTool 或 RemoteAgentTool 包装成普通工具以绕过 Agent 治理。
- 不支持 Tool Call 之间的依赖图、条件分支、循环或自动拓扑调度。
- 不承诺所有 openJiuwen 工具都无条件并行；交互、控制、共享写和外部副作用工具必须按 `agent-runtime` 安全策略串行或隔离。
- 不承诺 exactly-once 外部副作用；需要工具 provider 提供幂等键、查询或补偿能力。
- 不承诺跨进程恢复尚未完成的普通 Tool Call；如需长任务持久化，应提升为受治理 Task 或由对应工具 / 工作流系统提供恢复语义。
