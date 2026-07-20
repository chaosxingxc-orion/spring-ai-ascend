---
scope: version
module: agent-runtime
feature_type: functional
feature_id: FEAT-004
status: active
updated: 2026-07-20
dependency:
  - README.md
  - FEAT-001-standardized-agent-service-entrypoint.md
  - FEAT-003-agent-task-state-cache.md
  - FEAT-008-user-interaction-interrupt-response.md
  - FEAT-019-agent-core-parallel-tool-tasks.md
  - DFX-001-trajectory-observability.md
  - ../architecture/L1-High-Level-Design/agent-runtime
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-004-remote-agent-orchestration.md
---

# 任务驱动的远程智能体调用 - 当前版本事实要求

## 1. 特性定位

FEAT-004 定义 `agent-runtime` 侧执行下游智能体调用委托的黑盒行为。运行时代理在父 Task 执行过程中接收来自智能体框架的远程智能体调用委托，将委托转换为标准 A2A 调用发送给下游 runtime，并在下游调用返回后把结果回填给父智能体，使父 Task 从此前的委托等待点恢复执行。

本特性覆盖两类调用形态：

1. **单个下游智能体调用**：父智能体产生一个下游智能体委托，runtime 代理一次远程 A2A 调用，收到结果后回填并恢复父任务。
2. **同轮多个下游智能体调用**：`agent-core` 按 FEAT-019 在同一轮 agent-loop 中保留多个下游智能体 ToolCall，并以批量委托形式交给 runtime；runtime 可并行代理多个下游 A2A 调用，等待同批次全部到达结果性终态后一次性回填并恢复父任务。

本特性解决的问题是：父智能体需要把部分工作委托给一个或多个专业下游智能体执行，但客户端不应直接理解下游 Agent 地址、A2A 调用细节、远端 Task 生命周期或结果汇聚过程。`agent-runtime` 必须以父 Task 为中心完成远程调用代理、状态关联、结果聚合和父任务恢复，使单调用和并行调用在客户端表面保持一致、可追踪、可取消、可诊断。

本特性只处理下游智能体调用委托、远程 A2A 执行、结果回填和父任务恢复。交互式 `INPUT_REQUIRED` 的客户端等待、续接和定向输入语义归属 FEAT-008；FEAT-004 仅在远端调用返回该类状态时把事实交给对应特性处理，不在本文档中定义人机交互协议。

本特性不定义新的批量创建下游任务 API，也不要求向智能体暴露一个显式的批量委托工具。多个下游调用来自 FEAT-019 定义的同轮多个单调用 ToolCall；runtime 只消费这些委托并代理执行。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 远程 Agent 静态接入 | MUST | runtime 必须能基于配置接入下游 A2A Agent，获取其可调用能力，并将其作为父智能体可使用的下游智能体代理能力暴露。 |
| 单个下游调用代理 | MUST | 父智能体产生单个下游智能体调用委托时，runtime 必须代理一次远程 A2A 调用，并在结果返回后恢复父任务。 |
| FEAT-019 批量委托消费 | MUST | runtime 必须能接收同一父任务、同一轮 agent-loop 产生的多个下游智能体调用委托，并保持每个委托的独立身份。 |
| 并行远程调用 | MUST | 对彼此独立的同批次委托，runtime 必须支持在受控并发预算内并行发起下游 A2A 调用。 |
| all-settled 汇聚 | MUST | 同批次多个下游调用必须全部到达结果性终态后，runtime 才能向父智能体回填该批次结果并恢复父任务。 |
| `toolCallId` 结果关联 | MUST | runtime 必须按 FEAT-019 提供的 `toolCallId` 关联每个委托、远程调用结果和父智能体回填项；不得按完成顺序、工具名或目标 Agent 猜测。 |
| 任务中心化关联 | MUST | 父 Task 必须能观察到下游调用的关联状态。下游实际 Task 的主权归下游 runtime；本地 runtime 只维护与父 Task 相关的本地可观察关联或投影。 |
| 远端 Task ID 关联 | MUST | 当下游 runtime 受理调用并返回远端 Task 标识时，本地 runtime 必须把该标识与父 Task、对应 `toolCallId` 关联，用于查询、取消、诊断和结果回填。 |
| 结果与错误并存 | MUST | 同批次中部分下游调用失败、拒绝、取消或超时时，runtime 必须保留成功项和失败项，并把它们作为对应 `toolCallId` 的结构化结果回填。 |
| 父任务单次恢复 | MUST | 同一批次 fan-in 完成后，runtime 只能触发一次父智能体恢复，避免每个下游调用完成后分别触发父 Agent 推理。 |
| 取消传播 | MUST | 父 Task 被取消时，runtime 必须 best-effort 取消尚未进入结果性终态的下游远程调用，并保留可诊断轨迹。 |
| 超时治理 | MUST | runtime 必须对远程调用执行超时治理；超时项作为对应下游调用的结果性失败参与 all-settled 汇聚。 |
| 可观测性 | SHOULD | runtime 应记录父 Task、下游目标、远端 Task 标识、`toolCallId`、状态变化、耗时、结果类别和错误原因，并遵守 DFX-001 的脱敏要求。 |
| 客户端交互续接 | OUT | `INPUT_REQUIRED` 的客户端呈现、续接、歧义处理和长时挂起语义由 FEAT-008 定义。 |
| 本地普通工具并行 | OUT | 文件、Shell、本地函数、普通 REST/MCP、浏览器、设备或代码执行等非 Agent 工具的并行不属于 FEAT-004。 |
| 显式批量委托 API | OUT | 当前版本不定义 `ParallelChildTaskSet` 或类似批量创建下游任务接口。 |
| 任意依赖图 / DAG | OUT | 当前版本只处理同轮彼此独立的下游智能体调用，不解析依赖图、条件分支、循环或工作流 DAG。 |

## 3. 引用接口与入口要求

FEAT-004 不新增面向客户端的 A2A method，不定义 `ParallelChildTaskSet` 类外部接口，也不固定 runtime 内部协调器、表结构或 DTO 名称。下游技术详设可以选择具体实现，但不得改变以下黑盒契约。

| 入口 / 对象 | 归属 | FEAT-004 使用语义 |
|---|---|---|
| 标准 A2A Task 入口 | FEAT-001 | 客户端仍通过标准 Task 创建、查询、订阅和取消入口观察父 Task；FEAT-004 不新增客户端调用入口。 |
| 远程 Agent 配置与能力暴露 | agent-runtime | runtime 根据配置接入下游 Agent，并把下游能力作为父智能体可调用的代理能力暴露。 |
| 下游智能体调用委托 | FEAT-019 / agent-core | 每个委托表达一次下游 Agent 调用意图，必须携带稳定 `toolCallId`、目标能力和调用输入。 |
| 批量委托 | FEAT-019 / agent-core | 同一轮多个下游 Agent 委托作为一个恢复批次交给 runtime；runtime 不要求智能体调用显式批量工具。 |
| 远程 A2A 调用 | agent-runtime -> 下游 runtime | runtime 为每个委托发起远程 A2A 调用；下游 runtime 受理后拥有远端 Task 主权。 |
| 本地任务关联表面 | agent-runtime | 父 Task 可观察到下游调用正在执行、已完成、失败、取消或超时等关联状态；具体存储形态由技术详设决定。 |
| 结果回填 | agent-runtime -> agent-core | runtime 在可回填条件满足后，按 `toolCallId` 把成功结果或结构化失败回填给父智能体。 |

### 3.1 标识语义

FEAT-004 只约束标识的黑盒职责，不规定内部字段名或存储模型。

| 标识 | 主体责任 | 黑盒语义 |
|---|---|---|
| 父 `taskId` | 当前 runtime | 标识客户端正在观察和控制的父 Task。 |
| `toolCallId` | agent-core 生成，runtime 消费 | 标识父智能体中的一次下游调用委托，是结果回填和父 Agent 恢复的稳定关联键。 |
| 本地下游调用关联标识 | 当前 runtime | 标识父 Task 下某个下游调用的本地可观察关联或投影；不得伪装为远端 Task 主权。 |
| 远端 `taskId` / `contextId` | 下游 runtime | 标识下游 runtime 受理并执行的实际远端 Task；当前 runtime 只能关联和代理。 |
| trace / correlation id | 调用链各方 | 用于诊断、审计和跨 runtime 关联，不得替代 `toolCallId` 做结果归位。 |

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户 / 系统动作 | 期望行为 |
|---|---|---|---|
| 配置并暴露下游 Agent | 下游 A2A Agent 已可访问并声明能力 | runtime 启动并加载远程 Agent 配置 | 父智能体可看到对应下游智能体代理能力；客户端无需直接感知下游地址。 |
| 单个远程智能体调用 | 父智能体选择调用一个下游 Agent | runtime 代理远程 A2A 调用 | 父 Task 等待远端结果；远端完成后 runtime 把结果回填给父智能体并恢复父任务。 |
| 三个下游调用并行执行 | FEAT-019 交给 runtime 三个同轮下游委托 | runtime 在并发预算内调用 weather、hotel、flight 等下游 Agent | 三个远程调用可并行推进；父智能体在三者全部结果性终态后只恢复一次。 |
| 部分下游调用失败 | 同批次中一个下游 Agent 超时或失败 | 其他下游 Agent 正常完成 | runtime 保留成功结果和失败原因，all-settled 后按 `toolCallId` 一次性回填；父 Task 不因单个下游普通失败自动失败。 |
| 父任务取消 | 父 Task 正在等待一个或多个远程调用 | 客户端取消父 Task | runtime 停止继续派发未启动调用，并 best-effort 取消已派发远端调用；父 Task 进入取消表面。 |
| 远端调用需要客户端交互 | 下游 runtime 返回需要客户端参与的等待事实 | runtime 观察到远端等待 | FEAT-004 只保留远端调用关联事实；客户端等待、续接和路由语义交由 FEAT-008 处理。 |

### 4.1 典型并行流程

```text
用户：规划周末行程，同时查天气、酒店和航班
  |
  v
父智能体同轮生成三个下游智能体调用委托
  |-- toolCallId=call-weather -> weather-agent
  |-- toolCallId=call-hotel   -> hotel-agent
  |-- toolCallId=call-flight  -> flight-agent
  |
  v
agent-core 按 FEAT-019 批量中断并交给 runtime
  |
  v
runtime 代理三个远程 A2A 调用，并关联父 Task、toolCallId、远端 Task
  |
  v
runtime 等待三个调用全部到达结果性终态
  |
  v
runtime 按 toolCallId 回填成功结果和结构化失败
  |
  v
父智能体恢复一次，并基于完整结果继续推理
```

## 5. 行为语义与边界

### 5.1 父任务等待与恢复语义

- 父智能体产生下游智能体调用委托后，父 Task 进入等待远程调用结果的执行阶段。
- 单个下游调用完成后，runtime 可以回填该调用结果并恢复父任务。
- 同批次多个下游调用必须全部到达结果性终态后，runtime 才能回填批次结果并恢复父任务。
- 一个并行批次只触发一次父智能体恢复；不得按子调用完成顺序多次局部恢复父 Agent。
- 父智能体恢复后如何继续推理、是否再次生成下游调用，归属 agent-core 和具体 Agent 行为。

### 5.2 并行批次语义

- 同一轮来自 FEAT-019 的多个下游智能体调用委托属于同一恢复批次。
- runtime 可以在受控并发预算内并行发起同批次的远程 A2A 调用。
- 并行执行不保证完成顺序；结果身份必须由 `toolCallId` 决定。
- 并发预算不足时，runtime 可以排队部分调用，但不得丢弃委托或静默改写结果。
- 当前版本不解析依赖关系；存在严格前后依赖的任务应由父智能体分轮生成，或交给工作流 / DAG 编排能力处理。

### 5.3 任务主权语义

- 当前 runtime 拥有父 Task 的生命周期和客户端可见状态。
- 下游 runtime 拥有实际远端 Task 的生命周期、执行状态和结果主权。
- 当前 runtime 对下游调用只建立本地可观察关联或投影，用于父 Task 关联、状态展示、取消传播、诊断和结果回填。
- 本地关联不得被描述为当前 runtime 拥有远端 Task；远端 `taskId` 只能作为关联事实保存。
- 本地关联的存储方式、是否形成单独记录、是否使用专门协调器等属于技术详设，不在 FEAT-004 黑盒文档中规定。

### 5.4 失败、取消与超时语义

- 下游调用成功、失败、拒绝、取消或超时，都可以成为该调用的结果性终态。
- 同批次采用 all-settled 语义：单个下游调用失败不得导致父智能体提前恢复，也不得吞掉其他成功结果。
- 部分失败应作为对应 `toolCallId` 的结构化失败结果回填给父智能体，由父智能体决定后续推理。
- 只有 runtime 无法完成委托执行、无法保持必要关联、无法恢复父任务，或父智能体在恢复后判定整体失败时，父 Task 才进入失败表面。
- 父 Task 被取消时，runtime 必须停止继续派发未启动调用，并 best-effort 取消已派发但未终态的远端调用。
- 远端不可达、协议错误、限流、超时和业务失败应尽量形成可区分的错误类别，便于父智能体和运维诊断。

### 5.5 状态可见性语义

- 父 Task 在等待远程调用期间应保持可查询、可订阅、可取消。
- 客户端应能观察到父 Task 正在等待下游智能体调用，以及每个下游调用的目标、关联标识、当前阶段和结果类别。
- FEAT-004 不新增 A2A Task 状态。父 Task 只使用 FEAT-001 / L1 架构定义的标准状态，如 `WORKING`、`COMPLETED`、`FAILED`、`CANCELED`、`INPUT_REQUIRED`、`REJECTED`。
- 部分失败不得引入 `completed_with_partial_failures` 这类新父 Task 状态；应通过结果内容、artifact 或 metadata 表达。
- 若远端调用进入需要客户端交互的等待状态，客户端可见的 `INPUT_REQUIRED`、续接入口和歧义处理由 FEAT-008 定义。

### 5.6 信息投影语义

- 下游调用的最终结果或结构化失败用于回填父智能体。
- 下游调用的状态变化、进度、远端 Task 标识、耗时和错误类别可投影到父 Task 的可观测表面。
- 中间事件或调试信息不得自动注入父智能体推理上下文，除非它们被明确作为最终结果的一部分回填。
- 轨迹与可观测信息不得要求暴露原始 Chain-of-Thought；相关脱敏、摘要和审计要求遵守 DFX-001。

## 6. 对下游设计与实现的约束

- L2 详细设计必须把 FEAT-004 作为远程智能体调用代理、并行 fan-out / fan-in、结果回填和父任务恢复的事实来源。
- L2 不得在 FEAT-004 名义下新增 `ParallelChildTaskSet` 或类似客户端 / 上游公开接口。
- L2 不得要求智能体调用显式批量工具才能触发多个下游调用；同轮多个单调用委托由 FEAT-019 承接。
- L2 必须保留 `toolCallId` 在委托、远程调用关联、结果回填和父 Agent 恢复之间的稳定身份。
- L2 必须区分父 Task 主权、下游远端 Task 主权和本地可观察关联，不得把本地投影写成远端 Task 所有权。
- L2 必须验证同批次多个远程调用不会只保留最后一个、不会结果串线、不会按完成顺序错配。
- L2 必须验证 all-settled 后单次恢复父智能体，部分失败作为结构化结果回填。
- L2 必须把客户端交互等待、续接、多个等待点歧义和长时挂起语义交由 FEAT-008 定义。
- L2 必须把本地普通工具并行、工具安全分级、文件冲突键和普通 provider 并发治理排除在 FEAT-004 主线之外。
- 测试应覆盖单个下游调用、多个下游调用并行、完成顺序乱序、部分失败、远端拒绝、超时、父任务取消、远端 Task ID 关联、结果按 `toolCallId` 回填和父任务单次恢复。

## 7. 关联文档

- `version-scope/README.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-003-agent-task-state-cache.md`
- `version-scope/FEAT-008-user-interaction-interrupt-response.md`
- `version-scope/FEAT-019-agent-core-parallel-tool-tasks.md`
- `version-scope/DFX-001-trajectory-observability.md`
- `architecture/L1-High-Level-Design/agent-runtime/README.md`
- `architecture/L1-High-Level-Design/agent-runtime/logical.md`
- `architecture/L1-High-Level-Design/agent-runtime/process.md`
- `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-004-remote-agent-orchestration.md`
- `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-026-parallel-tool-execution.md`
