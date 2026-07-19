---
scope: version
module: agent-runtime
feature_type: functional
feature_id: FEAT-004
status: active
dependency:
  - README.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-004-remote-agent-orchestration.md
---

# 远程 Agent 编排 — 黑盒行为说明

## 1. 特性定位

agent-runtime 作为 A2A 客户端接入和调用其他 A2A Agent，实现跨 Agent 协作。远程 Agent 通过 YAML 配置静态接入，runtime 自动拉取 Agent Card、缓存维护本地目录、生成工具描述，并将其安装为本地 Agent 可调用的 Tool。

本特性同时纳入两种远程调用方式：

1. **单任务工具调用**：LLM 调用一个远程 Tool，runtime 通过中断—续接流水线执行一个远程 A2A Task，并将结果回灌本地 Agent。
2. **任务驱动的并行子任务**：上游 Agent 或任务规划器一次提交多个彼此独立的远程 Agent 子任务，runtime 将每个子任务物化为可独立追踪的远程 A2A Task，在并发预算内 fan-out 并行执行，待结果 fan-in 后一次性回灌本地 Agent。

并行子任务在当前版本采用**同步父级等待**：父 Task 保持运行 / 等待状态，child Task 并行执行并实时投射轨迹，all-settled 后父 Agent 恢复。父 Task 创建子任务后立即返回、依赖异步回调聚合和断线重连的执行方式不属于本次 FEAT-004 更新范围。

- **解决的问题**：单个 Agent 能力有限；复杂任务需要拆成多个相互独立的专业子任务，并发委托给一个或多个下游 Agent，并行执行子智能体，提升任务执行效率，在保持 Task 可追踪、可取消、可恢复的同时缩短端到端时延。
- **适用场景**：旅行助手并行查询天气、酒店和航班；研究 Agent 并行收集不同信息源；企业主 Agent 并行调用多个部门级 Agent。存在严格前后依赖的子任务仍应串行执行或交由工作流 / DAG 编排能力处理。

## 2. 对外能力边界

### 2.1 能力清单

状态说明：`✅` 表示已有实现事实；`🟡` 表示已纳入当前版本范围、需要设计实现和验收对齐；`⬜` 表示当前版本不承诺。

| 能力 | 状态 | 说明 |
|------|------|------|
| YAML 配置远程端点 | ✅ | `agent-runtime.remote-agents[N].url` |
| Agent Card 自动拉取 | ✅ | 启动时拉取，自适应刷新 |
| 本地目录维护 | ✅ | sticky remoteAgentId，故障降级 |
| RemoteAgentToolSpec 生成 | ✅ | 从 Card skills 生成，开放 JSON schema；**无 skills 的 Agent Card 不会被注入为 Tool** |
| OpenJiuwen Tool 安装 | ✅ | Placeholder Tool + Interrupt Rail |
| 远程 A2A 调用 | ✅ | `SendStreamingMessage`，独立 streaming |
| 单任务中断—续接 | ✅ | 远程 INPUT_REQUIRED → 父 Task 挂起 → 用户输入 → 续写 |
| Metadata 转发 | ✅ | 入站 metadata → 出站远程调用 |
| 单任务结果回灌 | ✅ | 远程 COMPLETED → InteractiveInput → 本地 Agent resume |
| 父 Task 进度投射 | ✅ | 远程 progress → 父 Task artifact |
| 取消级联传播 | ✅ | 父 Task cancel → 远程 CancelTask |
| 超时检测 | ✅ | REMOTE_TIMEOUT + 孤儿 Task cancel |
| 任务驱动的并行子任务 | 🟡 | 一次接收多个独立子任务，物化为多个 child Task，在并发预算内 fan-out / fan-in |
| 子任务独立状态与关联 | 🟡 | 每个 child Task 有稳定标识、目标 Agent、序号、状态、deadline 和父子关联 |
| 并行结果汇聚与回灌 | 🟡 | 默认 all-settled；按子任务稳定序号形成结构化结果集，一次性恢复父 Agent |
| 并行取消、超时与部分失败 | 🟡 | 父取消向未终态子任务级联；失败和超时保留为可编程结果，不吞掉成功结果 |
| 并行 INPUT_REQUIRED | 🟡 | 等待输入的 child Task 可挂起，其他独立 child Task 继续；输入必须定向到具体 child Task；语义与单任务中断-续接一致 |
| 子任务信息分类与投射 | 🟡 | child Task 返回三类信息：①最终结果信息（结构化结果集回灌父 Agent）；②思维链信息（thinking_* 实时投射给调用端）；③中间内容与状态信息（流式内容、状态变化、progress 实时转发给调用端） |
| 任意依赖图 / DAG 编排 | ⬜ | 当前只承诺单层独立子任务 fan-out / fan-in，不解析子任务依赖图 |
| 嵌套远程调用 | ⬜ | 当前不承诺 child Task 内再次发起并行远程调用 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| 动态服务发现 | 远程端点必须通过 YAML 配置声明，不自动扫描网络 | 使用当前配置目录；后续由注册发现特性扩展 |
| 远程 Agent 负载均衡 | 不属于本特性的编排语义 | 在反向代理、路由或平台调度层实现 |
| 远程调用的认证 | A2A 认证属于协议和接入层，不属于编排层 | 通过 A2A SDK 认证扩展 |
| 有依赖子任务的自动排序 | 依赖图需要独立的验证、调度和恢复语义 | 由工作流 / DAG 编排能力处理 |
| 下游 Agent 内部工具 / 工作流并行 | A2A child Task 与下游内部 Tool Call / workflow instance 的状态所有者不同 | 由下游 Agent 自己实现，不属于远程 Agent 编排 |
| 无界并发 | 会导致下游过载、连接耗尽和成本失控 | runtime 按配置的并发预算排队和限流 |

## 3. 外部行为与用户场景

### 3.1 外部接口

| API / 行为表面 | 说明 |
|-----|------|
| `agent-runtime.remote-agents` YAML | 配置远程端点和单次远程调用参数 |
| RemoteAgentToolSpec | 被 LLM 看到的远程能力描述 |
| 单个 RemoteInvocation | 表达一个远程 Agent 工具调用 |
| ParallelChildTaskSet | 表达同一父 Task 下的一组独立下游子任务；稳定字段由 L2 详细设计固化 |
| 父 Task artifact / status | 外部客户端通过 A2A stream 看到各 child Task 的状态投影（接受、进度、等待输入、终态） |
| 子任务信息分类投射 | child Task 返回三类信息：①最终结果信息（结构化结果集回灌父 Agent）；②思维链信息（thinking_* 实时投射给调用端）；③中间内容与状态信息（流式内容、progress 实时转发给调用端） |
| 结构化并行结果集 | fan-in 后按稳定子任务序号返回每个 child Task 的结果或错误，并回灌父 Agent |

### 3.2 用户示例

#### 3.2.1 配置远程 Agent

```yaml
# 主 Agent (8080) 配置三个远程 Agent
agent-runtime:
  remote-agents:
    - url: http://weather-agent:18081
    - url: http://hotel-agent:18082
    - url: http://flight-agent:18083
```

前置条件：远程 Agent 已启动在对应端口，Agent Card 可访问且至少声明一个 skill。预期结果：主 Agent 的 LLM 工具列表中出现天气、酒店和航班三个远程能力。

#### 3.2.2 单个远程 Agent 调用

```text
用户：查北京天气
主 Agent：调用 query_weather(city="北京")
runtime：创建一个远程 A2A Task，投射进度，完成后回灌结果
主 Agent：继续推理并给出最终回答
```

#### 3.2.3 多个下游子任务并行执行

```text
用户：帮我规划周末去上海的行程，同时查天气、酒店和往返航班

主 Agent / 任务规划器一次生成三个独立远程 Agent 子任务：
  1. 查询上海周末天气       → weather-agent
  2. 查询预算内酒店         → hotel-agent
  3. 查询往返航班           → flight-agent

runtime：
  - 为三个请求分别创建 child Task
  - 在并发预算内并行派发
  - 持续把 child Task 进度投射到父 Task
  - 等待三个 child Task 全部进入结果性终态
  - 按 1、2、3 的稳定顺序汇聚结果并一次性回灌

主 Agent：基于完整结果集生成统一行程建议
```

并行执行不保证完成顺序；对父 Agent 可见的汇聚结果必须保持生成时的稳定序号，不能因网络返回先后而改变语义。

### 3.3 单任务 E2E 流程

```text
用户："查北京天气"
  │
  ▼ 主 Agent
  ├─ LLM 看到 tool: query_weather
  └─ LLM 调用: query_weather(city="北京")
  │
  ▼ Interrupt Rail → RemoteInvocation
  ├─ SendStreamingMessage → weather-agent
  ├─ 远程 progress → 父 Task artifact
  └─ 远程 COMPLETED → toolResult
  │
  ▼ 回灌主 Agent
  ├─ InteractiveInput.update(toolCallId, toolResult)
  ├─ LLM resume
  └─ parent Task COMPLETED
```

### 3.4 并行子任务 E2E 流程

```text
父 Agent 生成 ParallelChildTaskSet
  │
  ├─ child-1 → weather-agent → remote-task-w ─┐
  │              │思维链/中间内容实时投射          │
  │              ▼                              │
  │        父 Task artifact（主调用端可见）        │
  ├─ child-2 → hotel-agent   → remote-task-h ─┼─ all-settled join
  │              │思维链/中间内容实时投射          │
  │              ▼                              │
  │        父 Task artifact（主调用端可见）        │
  └─ child-3 → flight-agent  → remote-task-f ─┘
                 │思维链/中间内容实时投射
                 ▼
           父 Task artifact（主调用端可见）
                                               │
                    StructuredChildTaskResults│（仅最终结果回灌父 Agent）
                                               ▼
                                      父 Agent 单次 resume
                                               │
                                               ▼
                                      parent Task COMPLETED
```

## 4. 任务驱动并行行为语义

### 4.1 子任务生成与接受

- 并行批次必须属于一个确定的父 Task，每个子任务必须具有稳定的批次内序号或幂等键。
- 子任务必须明确目标远程能力和输入；缺少目标、输入不合法或目标不可用的子任务必须形成该子任务自己的拒绝 / 失败结果，不得让整个批次无记录消失。
- runtime 只并行调度被声明为彼此独立的子任务；当前版本不从自然语言中推断依赖关系，也不自动把有依赖任务改写为 DAG。
- 接受并行批次不等于所有子任务已经启动。超过并发预算的子任务保持可观察的 pending / queued 状态，并在容量释放后派发。

### 4.2 父子 Task 与关联

- 每个下游调用都必须拥有独立的 child Task 记录和独立的远程 `taskId` / `contextId`（在远端接受后）。
- 父 Task 必须能够关联批次、child Task、目标 Agent、远程 Task、tool call、trace 和租户上下文。
- child Task 的状态变化应投射为父 Task 的进度或 artifact，但 child Task 不能直接覆盖父 Task 的最终状态。
- 重试同一批次时，runtime 必须基于父 Task 与子任务稳定键避免重复创建可见下游任务。

### 4.3 并发与背压

- runtime 必须实施有界并发；并发上限由部署或平台策略确定，不能由 LLM 任意放大。
- 并行批次中的子任务可以指向不同下游 Agent，也可以在目标容量允许时指向同一下游 Agent。
- 当并发预算不足时，runtime 应排队剩余子任务，不得退化为不可观察的同步阻塞，也不得直接丢弃。
- 并发限制、排队时长、实际并行度和被限流结果必须可观测。
- runtime 的瞬时并发预算只约束已接受 child Task 的启动节奏；上游入口的一次提交批次上限约束本次接受多少项。两者不得混为一个“并发上限”。

### 4.4 结果汇聚与父 Agent 恢复

- 当前版本默认采用 **all-settled**：所有 child Task 都进入 completed、failed、canceled、rejected 或 timed-out 等结果性终态后，才完成 fan-in。
- 结果集必须逐项包含 child Task 标识、稳定序号、目标 Agent、结果状态、结果 / artifact 引用或结构化错误。
- 结果集按子任务生成时的稳定序号排列，而不是按完成时间排列。
- 某个 child Task 失败不得吞掉其他 child Task 的成功结果；父 Agent 必须同时看到成功结果和失败原因。
- fan-in 完成后只对父 Agent执行一次批次级 resume，避免每完成一个子任务就触发一次父 LLM 推理而产生竞态、重复推理和上下文污染。

### 4.5 INPUT_REQUIRED

- 任一 child Task 进入 `INPUT_REQUIRED` 时，父 Task 必须投射等待输入状态，并明确需要输入的 child Task 标识、目标 Agent 和问题描述。
- 其他与该 child Task 独立的在途子任务可以继续执行并保留结果，不因一个分支等待输入而被取消。
- 用户补充输入必须定向到具体 child Task；当多个 child Task 同时等待输入而请求未指定目标时，runtime 必须返回明确的歧义错误，不得猜测路由。
- 等待输入的 child Task 完成后重新进入原批次 join；已完成兄弟任务不得重复执行。

### 4.6 取消、超时与失败

- 取消父 Task 必须 best-effort 取消所有未进入结果性终态的本地 child Task 和远程 Task，并保留取消轨迹。
- 单个 child Task 被取消、失败或超时，默认不取消兄弟任务；其结果作为结构化错误参与 all-settled 汇聚。
- 每个 child Task 必须受单次远程调用 deadline / timeout 约束；并行批次还应受父 Task deadline 和批次 join deadline 约束。
- 父 deadline 到期后，runtime 必须停止继续派发未启动子任务，取消仍在运行的远程 Task，并以部分结果 + 明确超时项完成汇聚或终止父 Task。
- 远程不可达、协议错误、限流、超时和业务失败必须可区分，并携带是否可重试语义。

### 4.7 父子状态投射

- child Task 使用 `pending`、`running`、`input_required`、`completed`、`failed`、`canceled`、`timeout`、`rejected` 等状态表达独立下游执行。
- 父 Task 在 fan-out / fan-in 期间保持 `running`；需要定向补充输入时可投射 `input_required`。
- 所有 child Task 成功且父 Agent 综合成功时，父 Task 进入 `completed`。
- 至少一个 child Task 成功、至少一个失败 / 取消 / 拒绝 / 超时，且父 Agent仍形成有效综合结果时，父 Task 进入 `completed_with_partial_failures`。
- 批次结构、父子关联、权限一致性或父 Agent 综合发生不可恢复失败时，父 Task 进入 `failed`；不得把任一 child Task 普通失败自动扩大为父级失败。
- `canceled` 与 `timeout` 作为父级结果时，必须保留已完成 child Task 的结果和未完成项明细。

### 4.8 子任务信息分类与投射

子智能体返回给主智能体的信息分为三类，分别服务于不同的消费方和目的：

#### 4.8.1 最终结果信息（给父 Agent resume 使用）

这是子任务完成后回灌给父 Agent 的核心数据，用于父 Agent 的后续推理。

- **结构化结果集**：每个 child Task 的最终结果，包含 child Task 标识、稳定序号、目标 Agent、结果状态、结果内容 / artifact 引用或结构化错误
- **tool_result**：远程 Tool 调用的返回值
- **适用范围**：仅在 all-settled 汇聚完成后，一次性回灌父 Agent；父 Agent 基于完整结果集进行综合推理

#### 4.8.2 思维链信息（给外部调用端实时呈现）

子智能体的推理过程，用于外部调用端的监控和调试。

- **思维链事件**：`thinking_start`、`thinking_chunk`、`thinking_end`
- **reasoning**：推理内容
- **投射方式**：实时投射到父 Task 的 artifact 中，携带 child Task 稳定序号和目标 Agent 标识
- **消费方**：主调用端（用户或客户端），用于观察子智能体的思考过程
- **注意**：思维链信息不直接注入父 Agent 的推理上下文

#### 4.8.3 中间内容与状态信息（给外部调用端实时呈现）

子智能体执行过程中的中间输出和状态变化。

- **流式内容片段**：`final_answer_chunk`、`interrupt_start`、`interrupt_end`、`tool_start`、`tool_end`、`todo_start`、`todo_end`、`todolist_start/item/end`
- **状态变化**：child Task 的状态变更（`pending` → `running` → `completed`/`failed`/`canceled`/`timeout`/`rejected`/`input_required`）
- **进度信息**：远程 progress 更新
- **artifact 更新**：子任务产生的 artifact
- **投射方式**：实时转发到父 Task 的输出流，携带 child Task 稳定序号和目标 Agent 标识
- **消费方**：主调用端，用于实时监控各子智能体的执行进度

#### 4.8.4 信息分类汇总表

| 信息类别 | 内容 | 消费方 | 投射时机 | 是否注入父 Agent 上下文 |
|----------|------|--------|----------|------------------------|
| 最终结果信息 | 结构化结果集、tool_result | 父 Agent | all-settled 后一次性回灌 | 是（用于 resume 推理） |
| 思维链信息 | thinking_*、reasoning | 主调用端 | 实时投射 | 否（仅用于监控） |
| 中间内容信息 | final_answer_chunk、interrupt_*、tool_*、todo_*、todolist_* | 主调用端 | 实时转发 | 否（仅用于监控） |
| 状态进度信息 | 状态变化、progress、artifact | 主调用端 | 实时更新 | 否（仅用于监控） |

#### 4.8.5 关键约束

- 中间内容的投射不影响 all-settled 汇聚逻辑，仍需等待所有 child Task 进入结果性终态后才触发父 Agent resume。
- 所有实时投射的信息必须携带 child Task 的稳定序号和目标 Agent 标识，避免主调用端混淆不同子任务的输出。
- 父 Agent 在 resume 时只接收最终结构化结果集，中间内容和思维链不直接注入父 Agent 的推理上下文。

## 5. 行为不变量与验收要点

### 5.1 行为不变量

- **一个下游调用，一个 child Task**：不得用共享状态槽覆盖多个并行调用。
- **父 Task 是编排所有者**：child Task 进度可投射，父 Task 终态只能由父级 join / 恢复流程决定。
- **有界并发**：LLM 生成 N 个子任务不等于创建 N 个线程或无条件同时发送 N 个请求。
- **稳定汇聚**：结果顺序与子任务生成顺序一致，与完成顺序无关。
- **单次恢复**：一个并行批次默认只触发一次父 Agent resume。
- **部分失败可见**：成功、失败、取消、拒绝和超时逐项保留。
- **取消可传播**：父取消后不得继续派发尚未启动的子任务。
- **租户与权限不扩张**：child Task 继承父 Task 的租户和调用授权上界，不能因委托获得更高权限。

### 5.2 最小验收场景

| 场景 | 验收结果 |
|---|---|
| 三个独立子任务均成功 | 三个下游 Task 在并发预算内重叠执行；父 Agent 收到稳定有序的三个成功结果并只恢复一次 |
| 一个快、一个慢、一个失败 | 快任务结果被保留；父级等待慢任务终态；失败项携带结构化错误；最终结果顺序不受完成先后影响 |
| 已接受子任务数超过瞬时并发预算 | 只启动预算允许的数量，其余处于可观察排队状态；容量释放后继续派发 |
| 上游提交超过远程 Agent 批次规模策略 | runtime 明确拒绝整个批次或只接受策略允许的项目并返回剩余项；不得静默丢弃或无界创建远程 Task |
| 一个 child Task 请求输入 | 父 Task 标识具体等待输入分支；其他分支继续；定向输入后只恢复该分支并最终 join |
| 两个 child Task 同时请求输入 | 未指定 child Task 的输入被拒绝为歧义；指定后分别续接，不串线 |
| 父 Task 取消 | 所有 queued 子任务停止派发，running 远程 Task 收到 best-effort cancel，父子状态和轨迹一致 |
| 父 deadline 到期 | 未启动项标记未执行 / 超时，运行项被取消，已完成项保留，父级得到可解释的部分结果 |
| 同一批次重复提交 | 不重复创建下游可见 Task，返回或恢复原有 child Task 集合 |
| 子任务思维链实时投射 | child Task 的 thinking_* 事件实时出现在父 Task artifact，携带子任务序号和目标 Agent 标识 |
| 子任务中间内容实时投射 | child Task 的 final_answer_chunk、tool_*、todo_* 等事件实时转发到父 Task 输出流 |
| 父 Agent resume 只接收最终结果 | 父 Agent resume 时只收到结构化结果集，思维链和中间内容不注入推理上下文 |

---
