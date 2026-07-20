---
scope: version
module: agent-core
feature_type: functional
feature_id: FEAT-019
status: draft
updated: 2026-07-20
dependency:
  - README.md
  - FEAT-002-heterogeneous-agent-framework-compatibility.md
  - FEAT-004-remote-agent-orchestration.md
  - DFX-001-trajectory-observability.md
---

# 智能体生成并行的下游智能体调用委托 - 当前版本事实要求

## 1. 特性定位

FEAT-019 定义 `agent-core` 侧对 openJiuwen DeepAgent / ReActAgent 同一轮 agent-loop 生成多个下游智能体委托调用的事实要求。下游智能体通过 runtime 代理工具暴露给模型；模型在同一轮可以多次调用该单调用工具，每个调用代表一个独立的下游 Agent 委托意图。

本特性不把下游 Agent 委托设计成一个显式的“批量调用工具”。模型看到的是多个普通形态的 runtime-proxy downstream-agent ToolCall；`agent-core` 必须在同轮 ToolCall 执行边界完整保留这些调用，为每个调用生成独立中断项，并把同轮多个下游 Agent 中断聚合成一个批量中断交给 `agent-runtime`。真正的远程 A2A child Task 创建、并发 fan-out / fan-in、状态投射、INPUT_REQUIRED 定向续接、取消、超时和部分失败处理由 [FEAT-004](./FEAT-004-remote-agent-orchestration.md) 约束。

本特性解决的问题是：DeepAgent 一轮规划可能同时需要天气、酒店、航班、研究、财务等多个下游 Agent。如果 `agent-core` 只保留最后一个工具中断，或逐个局部 resume，就会导致下游 Agent 调用丢失、结果串线、父 Agent 过早继续推理和 runtime 无法形成稳定并行批次。FEAT-019 要求 `agent-core` 把这些委托意图作为同一轮批量中断保存和回灌，使 runtime 可以安全代理 A2A 并行调用。

本特性面向以下角色：

- Agent 开发者：希望 DeepAgent 能在一轮推理中规划多个下游 Agent 委托。
- agent-core 设计与实现人员：需要定义同轮多 ToolCall、中断聚合、`toolCallId` 关联和批量 resume 契约。
- agent-runtime 设计与实现人员：需要从 core 获得稳定批量中断输入，并把结果按 `toolCallId` 回灌。
- 测试与验收团队：需要验证多下游 Agent 委托不丢失、不串线、只触发一次父 Agent 后续推理。

本特性明确不覆盖本地普通工具并行。文件、Shell、本地函数、普通 REST/MCP 工具、浏览器、设备、代码执行等非 Agent 工具的并行执行不属于 FEAT-019 当前版本承诺；若这些能力需要版本事实，应另立特性或由对应中间件/工具治理文档承载。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 本次版本变化 | 事实要求 |
|---|---|---|---|
| 多下游 Agent ToolCall 生成 | MUST | 新增 | DeepAgent / ReActAgent 必须允许同一轮 agent-loop 生成两个或以上指向 runtime-proxy downstream-agent tool 的 ToolCall。 |
| 单调用工具形态 | MUST | 新增 | 每个下游 Agent 委托仍表现为一个独立 ToolCall；当前版本不要求也不鼓励向模型暴露一个 `delegate_many` 类批量工具。 |
| 下游 Agent 代理工具识别 | MUST | 新增 | `agent-core` 必须能识别哪些 ToolCall 是 runtime-proxy downstream-agent tool，并把它们作为中断型工具处理，而不是在 core 内直接完成远程调用。 |
| 同轮批量中断聚合 | MUST | 新增 | 同一轮产生的多个下游 Agent 代理 ToolCall 必须聚合为一个批量中断 envelope 上抛给 runtime，不得只保留最后一个中断。 |
| `toolCallId` 稳定关联 | MUST | 新增 | 每个下游 Agent 代理 ToolCall 必须携带稳定且批次内唯一的 `toolCallId`，用于中断、结果、错误、续接和 ToolMessage 归位。 |
| 独立调用上下文 | MUST | 新增 | 每个代理 ToolCall 的工具名、参数、目标 Agent、extra、callback / rail context 和中断项不得被同批次兄弟调用覆盖。 |
| 批量 checkpoint / resume | MUST | 新增 | core 必须保存同轮全部未完成代理 ToolCall 的恢复点，并支持 runtime 用 `toolCallId -> result` 映射一次性恢复。 |
| all-settled 后单次继续推理 | MUST | 新增 | runtime 回灌完整批次结果后，core 只触发一次 DeepAgent 后续推理，不得每个下游 Agent 完成后各自触发一次。 |
| 结果稳定归位 | MUST | 新增 | 批量回灌结果必须按 `toolCallId` 写回对应 ToolMessage；完成顺序不得改变结果身份。 |
| 单成员兼容 | MUST | 现有兼容 | 单个下游 Agent 代理 ToolCall 必须继续兼容既有单中断、单结果回灌路径。 |
| 普通本地工具并行 | OUT | 不在范围 | 本特性不承诺本地普通工具并行、工具安全分级、文件冲突键或普通 provider 并发治理。 |
| A2A child Task 编排 | OUT | 归属 FEAT-004 | 远程 A2A Task 创建、状态、并发预算、INPUT_REQUIRED 路由、取消、超时和结果投射由 `agent-runtime` / FEAT-004 约束。 |
| 任意依赖图 / DAG | OUT | 不在范围 | 当前只处理同轮彼此独立的下游 Agent 委托，不解析依赖图、条件分支、循环或 workflow DAG。 |

## 3. 外部接口与入口要求

FEAT-019 约束 `agent-core` 与 runtime adapter 之间的可观察契约，不固定 Java 类名、包路径、内部 DTO 名称或具体序列化字段。L2 详细设计可以定义具体类型，但不得改变以下语义。

| 入口 / 对象 | 类型 | 事实要求 |
|---|---|---|
| runtime-proxy downstream-agent tool | Agent 可见 Tool | 表达一次下游 Agent 委托意图；模型可在同一轮生成多个该类 ToolCall。 |
| ToolCall | core 调用项 | 每项必须保留 `toolCallId`、tool name、arguments、目标远程 Agent 能力标识和模型生成顺序。 |
| batch interrupt envelope | core -> runtime adapter | 承载同一轮多个下游 Agent 代理 ToolCall 的中断项；至少能表达批次、成员列表、成员顺序和每项关联键。 |
| interrupt item | batch 成员 | 每项对应一个 ToolCall，必须可被 runtime 转换为一个远程 Agent invocation / child Task 候选。 |
| `InteractiveInput.userInputs` 或等价映射 | runtime -> core | runtime fan-in 后按 `toolCallId` 回灌每个成员的结果或结构化错误；core 按该映射恢复对应 ToolCall。 |
| ToolMessage 归位 | core context | core 必须把每个回灌结果写入与原始 `toolCallId` 对应的 ToolMessage，不能按工具名或完成顺序猜测。 |

### 3.1 参考批量中断形态

以下仅作为语义示例，不是强制 wire schema：

```json
{
  "batchId": "batch-001",
  "items": [
    {
      "toolCallId": "call-weather",
      "toolName": "delegate_to_weather_agent",
      "remoteAgentId": "weather-agent",
      "arguments": {"city": "上海"},
      "ordinal": 0
    },
    {
      "toolCallId": "call-hotel",
      "toolName": "delegate_to_hotel_agent",
      "remoteAgentId": "hotel-agent",
      "arguments": {"city": "上海", "budget": 800},
      "ordinal": 1
    }
  ]
}
```

约束：

- `batchId` 可以是 core 或 runtime adapter 内部诊断标识，不要求外部客户端传入。
- `toolCallId` 是跨中断、回灌和 ToolMessage 归位的稳定关联键。
- `remoteAgentId`、tool name 或目标能力字段的具体来源由 FEAT-004 的远程 Agent 工具安装与 Agent Card 映射设计定义。
- arguments 属于模型生成的工具输入；core 只负责保留和传递，不负责执行远程 A2A 调用。

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户 / 系统动作 | 期望行为 |
|---|---|---|---|
| 一轮生成三个下游 Agent 委托 | runtime 已安装天气、酒店、航班三个代理工具 | 用户要求规划行程，DeepAgent 同轮生成三个代理 ToolCall | core 形成包含三项的批量中断；runtime 后续可并行代理 A2A 调用。 |
| 下游 Agent ToolCall 不丢失 | 同轮多个代理 ToolCall 都抛出中断 | core 收集同轮工具执行结果 | 所有中断项都进入同一个 batch，不得被 AtomicReference、last-write-wins 或单槽状态覆盖。 |
| 单个下游 Agent 委托 | 模型只生成一个代理 ToolCall | core 产生单项中断 | 单项仍可按批次大小为 1 的语义处理，并兼容既有单中断路径。 |
| runtime 完整回灌 | runtime 已完成多个远程 child Task 的 fan-in | runtime 以 `toolCallId -> result` 映射恢复 core | core 为每个 ToolCall 写入对应 ToolMessage，并只继续推理一次。 |
| 部分失败回灌 | runtime 返回成功项和结构化失败项 | core 消费批量映射 | 成功和失败均按 `toolCallId` 归位；失败项不覆盖成功项，也不让 core 猜测目标。 |
| 后续轮次再次委托 | 第一批结果已回灌并完成下一轮模型推理 | DeepAgent 新一轮再次生成代理 ToolCall | 新一轮形成新的批量中断；不得与上一轮未相关的 `toolCallId` 或 batch 状态串线。 |

### 4.1 典型流程

```text
用户：规划周末去上海的行程，同时查天气、酒店和航班
  |
  v
DeepAgent 同一轮输出三个 runtime-proxy downstream-agent ToolCall
  |-- call-weather -> weather-agent
  |-- call-hotel   -> hotel-agent
  |-- call-flight  -> flight-agent
  |
  v
agent-core 为三个 ToolCall 生成批量中断 envelope
  |
  v
agent-runtime 接管批次并按 FEAT-004 创建/推进远程 A2A child Task
  |
  v
runtime all-settled 后回灌 {call-weather, call-hotel, call-flight}
  |
  v
agent-core 按 toolCallId 写入 ToolMessage，并触发一次 DeepAgent 后续推理
```

## 5. 行为语义与边界

### 5.1 工具形态语义

- 下游 Agent 代理工具是 **interrupt-producing tool**：它在 core 中产生委托中断，不在 core 内直接完成远程 A2A 调用。
- 多个下游 Agent 委托通过同轮多个单调用 ToolCall 表达，不通过一个显式批量工具表达。
- LLM 生成 N 个代理 ToolCall 只表示 N 个委托意图，不表示 core 自行创建 N 个远程 Task，也不表示无界并发。
- 下游 Agent 代理工具可以由 FEAT-004 的 RemoteAgentToolSpec / Agent Card 映射安装，但安装、发现和远程调用链路不属于 FEAT-019 的主体责任。

### 5.2 批量中断语义

- 一个批量中断只包含同一 DeepAgent / ReActAgent 同一轮模型决策产生的下游 Agent 代理 ToolCall。
- core 必须等待同轮代理 ToolCall 中断收集完成后再向 runtime 暴露批量中断，不得对成员逐个提前恢复父 Agent。
- 同一批次内 `toolCallId` 必须唯一；缺失或重复且内容冲突时，core 或 adapter 必须产生可诊断错误，不得按 tool name 猜测关联。
- 批量中断成员顺序应保留模型生成顺序；该顺序用于诊断和稳定结果展示，但结果身份以 `toolCallId` 为准。

### 5.3 回灌与继续推理语义

- runtime 只有在 FEAT-004 定义的批次可回灌条件满足后，才向 core 提交批量结果映射。
- core 必须支持一次 `InteractiveInput` 或等价输入携带多个 `toolCallId` 的结果。
- 每个 ToolCall 只消费自己的结果；禁止把同一个普通字符串结果复制给多个 ToolCall。
- 未收到结果的 ToolCall 不得被伪造为成功；应继续保持中断、等待输入或按 runtime 返回的结构化错误处理。
- 批量结果全部写入上下文后，DeepAgent 才能进入下一轮模型推理。

### 5.4 与 FEAT-004 的责任分界

| 责任 | FEAT-019 / agent-core | FEAT-004 / agent-runtime |
|---|---|---|
| 下游 Agent 工具可见性 | 消费已安装的代理工具定义 | 基于 Agent Card / RemoteAgentToolSpec 安装和暴露代理工具 |
| 多 ToolCall 生成 | 保留同轮多个代理 ToolCall | 不负责模型生成 |
| 批量中断 envelope | 生成或向 adapter 暴露完整成员列表 | 接收并解释为远程调用批次 |
| 远程 A2A 调用 | 不执行 | 创建 child Task，调用远程 Agent |
| 并发预算与调度 | 不拥有 | 拥有 fan-out / fan-in、排队、限流和超时 |
| INPUT_REQUIRED 外部续接 | 只要求按 `toolCallId` 接收回灌结果 | 拥有外部 A2A 输入路由和歧义处理 |
| Task 生命周期状态 | 不写 runtime Task 状态 | 拥有 parent / child Task 状态和投射 |
| 最终 resume | 按映射恢复 ToolMessage 并继续推理一次 | 在 all-settled 或可回灌状态下构造映射并触发恢复 |

### 5.5 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 本地普通工具并行 | 不承诺文件、Shell、本地函数、普通 REST/MCP、浏览器、设备或代码工具的并行执行。 |
| 显式批量委托工具 | 不要求向模型暴露一个一次性包含多个下游 Agent 调用的批量工具。 |
| core 拥有远程 Task | core 不创建、不查询、不取消远程 A2A Task，也不保存远端 `taskId` / `contextId` 的生命周期状态。 |
| runtime 状态写入 | core 不写 parent Task、child Task、TaskStore 或 A2A 状态机。 |
| 自动依赖分析 | 不从自然语言或工具参数中推断 ToolCall 间依赖关系。 |
| 下游 Agent 内部并行 | 不约束下游 Agent 自己如何并行执行工具或工作流。 |
| 跨进程恢复 | core 不承诺跨进程恢复尚未回灌的批量中断；服务侧持久化与恢复边界由 FEAT-004 / FEAT-003 约束。 |
| Chain-of-Thought 暴露 | 批量中断和轨迹不得要求暴露原始 Chain-of-Thought。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把下游 Agent 代理工具实现为中断型工具：core 侧生成委托中断，runtime 侧代理 A2A 调用。
- L2 不得把 FEAT-019 设计成普通本地工具并行执行特性，也不得把普通工具安全分级、文件冲突键或 provider 并发预算写成本特性的验收主线。
- L2 不得要求模型调用一个显式批量工具才能触发并行下游 Agent；同轮多个单调用代理 ToolCall 必须是主路径。
- L2 必须验证非流式和流式路径都能完整保留多个中断项，不能出现只保留最后一个中断的实现。
- L2 必须验证 `toolCallId` 在中断、runtime 回灌和 ToolMessage 写入之间保持稳定关联。
- L2 必须验证 runtime 批量回灌后 core 只触发一次 DeepAgent 后续推理。
- L2 必须与 FEAT-004 对齐：凡涉及 child Task、A2A wire、远端状态、用户定向续接、取消、超时、部分失败和实时投射的内容，应在 FEAT-004 或其详细设计中落地。
- FEAT-026 已合并进 FEAT-019，不再作为独立 version-scope 事实源；历史并行工具治理内容不得作为当前 FEAT-019 的范围依据。

## 7. 关联文档

- `version-scope/README.md`
- `version-scope/FEAT-002-heterogeneous-agent-framework-compatibility.md`
- `version-scope/FEAT-004-remote-agent-orchestration.md`
- `version-scope/DFX-001-trajectory-observability.md`
- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L1-High-Level-Design/agent-core/logical.md`
- `architecture/L1-High-Level-Design/agent-runtime/logical.md`
- `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-004-remote-agent-orchestration.md`
- `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-026-parallel-tool-execution.md`
