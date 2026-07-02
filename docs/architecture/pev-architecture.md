---
level: L1
view: architecture
status: proposal
document_role: PEV 执行-验证-自愈闭环架构说明
source_of_truth: false
---

# PEV Agent 架构（极小内核落地版）

## 0. 这个架构怎么来的

PEV 是一轮**架构进化搜索**的收敛结果，不是拍脑袋的：

1. 纸面分析 5 条候选路线 →
2. 为 6 个种群写代码设计 →
3. 8 个种群 × 代码试验 × 2 进化轮，16 次交叉代码审查 →
4. **8 条独立进化线全部收敛到同一核心结构**：sealed types + 纯函数 dispatch + 同步闭环

> **关于内部代号**（看 commit history / 设计 memory 时会用到）：你会在早期提交和
> memory 里看到「结论 AAA / AAB / AAC」这种标签——是这轮迭代的三版设计结论，
> 后一版迭代掉前一版（AAA → AAB → AAC），AAC 是最终幸存版。本文档不讲迭代故事，
> 直接讲这版结论本身；迭代过程详见设计 memory（`pev-graft-conclusion-aac`）。

**P8 极小内核**（`design-reference` 模块的 `MinimalAgentEngine`，<800 行纯 Java，零框架依赖）
是这版结论的参考实现。`agent-runtime` 的 `PEVAlphaStrategy` 等是它的**生产落地版**
（拆回多类 + Spring/Reactor + agent-core-java SPI）。

---

## 1. 一句话

**PEV = Plan → Execute → Verify → Diagnose → Dispatch**——一个让 agent 学会
"先诊断为什么失败、再决定做什么"的执行-验证-自愈闭环。核心 dispatch 逻辑 ~20 行，
用 sealed types 让编译器当防火墙：**删一个 case 分支 → 编译红**，不是运行时静默漏分支。

---

## 2. 核心循环

```
        ┌────────────────────────────────────────────────────────┐
        │                                                         │
        ▼                                                         │
   ┌─────────┐  TaskGraph   ┌───────────┐  nodeResults  ┌─────────┐
   │  Plan   │ ───────────> │  Execute  │ ────────────> │ Verify  │
   │ Planner │  (NL→DAG)    │ BSP/Pregel │   (逐节点)    │rule+LLM │
   │ NL→图   │              │ 拓扑执行   │               │         │
   └─────────┘              └───────────┘               └────┬────┘
        ▲                                                       │ passed?
        │ GlobalReplan                                failed    ▼
        │ (重新生成图)                       ┌─────────────────────────┐
        │        ┌────────────┐               │  Diagnose (纯函数)       │ zero-LLM
        │        │  Dispatch  │ <─────────────│  RootCauseDiagnoser     │
        │        │ sealed switch│              └─────────────────────────┘
        │        │ over 3 Action│                        │ RootCause (3 态)
        │        └──────┬──────┘                         ▼
        │   ┌───────────┼───────────┐             ┌──────────────────┐
        │   │           │           │             │ DeviceFailure    │ → 工具/infra 坏
        │   ▼           ▼           ▼             │ PerceptionUnrel. │ → verifier 不可信
        │ LocalReplan  GlobalReplan AcceptPartial │ PlanOrAnswerErr. │ → 内容错，replan 救得了
        │ (重执行失败  (丢弃图,    (诚实降级,       └──────────────────┘
        │  节点+       重新 Plan)  接受部分结果)
        │  correction
        │  hint)
        └──────────────────── LocalReplan 重执行后回到 Verify
```

**5 个阶段，每个都是可独立替换的 SPI**：

| 阶段 | 接口/类 | 职责 | 用 LLM? |
|------|---------|------|---------|
| **Plan** | `Planner` / `DefaultPlanner` | NL 目标 → TaskGraph（DAG；节点 = LLM_CALL / TOOL_CALL / SUB_AGENT）| 是（生成图）|
| **Execute** | `PregelExecutor` / `DefaultPregelExecutor` | BSP superstep：拓扑分层，层内并行（虚拟线程），层间屏障 | LLM 节点是 / 工具节点否 |
| **Verify** | `Verifier` / `DefaultVerifier` | ruleVerify（确定性）+ llmVerify（质量）+ successCriteria 逐条 | 否（规则）/ 是（LLM 质量）|
| **Diagnose** | `RootCauseDiagnoser.diagnose()` + `toReplanAction()` | 纯函数：信号 → RootCause（3 态）→ ReplanAction（3 态）| **否**（zero-LLM）|
| **Dispatch** | `PEVAlphaStrategy.executeVerifyLoop` | sealed switch over ReplanAction | 否 |

---

## 3. 类型架构——三层 sealed（本架构的核心洞察）

同一概念空间有**三个类型层**，各司其职，不混淆：

| 类型 | 层 | 角色 | 携带数据 |
|------|----|------|---------|
| `RootCause`（3 态 sealed）| **诊断输出** | **为什么** verify 失败？ | `DeviceFailure{nodes}` / `PerceptionUnreliable{verifierThrew}` / `PlanOrAnswerError{nodes}` |
| `ReplanAction`（3 态 sealed）| **调度输出** | **做什么**？PEV 主循环 switch 的输入 | `LocalReplan{failedNodes, feedback}` / `GlobalReplan{feedback}` / `AcceptPartial{reason}` |
| `ReplanStrategy`（3 态 sealed）| **verifier 推荐** | verifier **建议**什么策略？ | `LocalReplan{maxRounds}` / `GlobalReplan` / `AcceptPartial` |

**为什么分三层？** "为什么失败"（诊断）、"做什么"（调度）、"verifier 建议"（推荐）是**不同关注点**。
绑进一个类型（早期版本就这么做）会让 verifier 既诊断又调度，难测、难复用。分三层后：

- 诊断是**纯函数**（`RootCauseDiagnoser.diagnose`），零副作用零 LLM，可穷举单测
- 调度是 **sealed switch**（`toReplanAction`），编译器保证穷举
- verifier 只管"结果对不对 + 建议什么策略"，不操心调度

**sealed = 编译器当防火墙**：`switch` over `RootCause` 或 `ReplanAction`，删任一 case arm →
**编译红**（不是运行时静默漏分支）。这比运行时测试 RED 更早——类型层证明。

```java
sealed interface RootCause permits DeviceFailure, PerceptionUnreliable, PlanOrAnswerError {}
sealed interface ReplanAction permits LocalReplan, GlobalReplan, AcceptPartial {}
sealed interface NodeResult permits Success, DeviceFailure, VerifierFailure {}
```

---

## 4. 关键机制深潜

### 4.1 纯函数诊断（zero-LLM）

`RootCauseDiagnoser.diagnose(verifyThrew, failedToolNodes, verifyFailedNodes)`：

```java
if (verifyThrew)            return PerceptionUnreliable(true);   // verifier 自己崩了
hit = failedToolNodes ∩ verifyFailedNodes;
if (!hit.isEmpty())         return DeviceFailure(hit);           // 节点真执行失败
                            return PlanOrAnswerError(...);       // 排除法 → 内容错
```

**诊断优先级是确定性的，不问 LLM "你觉得为什么失败"**（LLM 会编）。直接用信号：
- verifier 抛异常 → 感知层不可靠
- 失败节点 ∩ 工具失败节点 非空 → 设备故障（同输入重跑还错）
- 否则 → 图/答案内容错

### 4.2 RootCause → ReplanAction 映射（~20 行核心 dispatch）

```java
switch (cause) {
    case DeviceFailure d         -> AcceptPartial("...replan cannot fix broken tools/infra");
    case PerceptionUnreliable p -> AcceptPartial("...cannot trust its FAILED verdict");
    case PlanOrAnswerError pe   -> {
        if (pe.nodes().isEmpty())      yield GlobalReplan(feedback);   // 空失败集 → 重规划
        if (pe.nodes().size() <= 2)    yield LocalReplan(nodes, hint); // 少量 → 局部重执行
                                       yield GlobalReplan(feedback);   // 大量 → 全局重规划
    }
}
```

**两条不可违反的 IFF（充分必要）契约**（编译期 + 测试双层保证）：

- `DeviceFailure / PerceptionUnreliable → AcceptPartial`（**永不应重试**——设备坏重试浪费
  轮次，verifier 坏盲信 FAILED 判定）
- `PlanOrAnswerError → LocalReplan / GlobalReplan`（**永不应 AcceptPartial**——内容错
  replan 可修复，降级过早放弃）

### 4.3 BSP 拓扑执行（`DefaultPregelExecutor`）

```
Layer 0: [A]            ← 无依赖，先执行
Layer 1: [B, C]         ← 依赖 A，A 完成后并行执行 B、C（虚拟线程）
Layer 2: [D]            ← 依赖 B、C，屏障后执行
```

- 层内并行（`Semaphore(maxParallelism)` + 虚拟线程）
- 层间屏障（`CompletableFuture.allOf().get()`）
- 多上游 fan-in：`resolveInputs` 聚合 `${A.output}` 占位符
- 失败记录为 `NodeResult.DeviceFailure(nodeId, error, isTimeout)`（类型化，替代脆弱的
  `"FAILED:"` 字符串前缀）

### 4.4 规则 + LLM 双层验证 + 短路

`DefaultVerifier.verify()`：

1. **ruleVerify**（确定性，不调 LLM）：null 检查 / `NodeResult.DeviceFailure`/`VerifierFailure`
   instanceof / `FAILED:` 前缀 / expectedOutput 关键字
2. **短路**：若 ruleVerify 已检出**全部**节点失败 → 跳过 LLM（零增量信息，省一轮 token）
3. 否则 **llmVerify**（质量评估，独立 prompt 避免 execute LLM 偏见）
4. **successCriteria 逐条**（关键词覆盖 + 停用词过滤）

### 4.5 LocalReplan + correction hint

`LocalReplan{failedNodes, feedback}` 不只是"重跑失败节点"——它把 `feedback` 作为
**correction hint** 注入失败节点的下一次 LLM 调用：

```xml
<task>原始任务</task>
<correction>verify 反馈：你上次算错的是 X，原因是 Y</correction>
```

让 LLM 自纠正，而不只是机械重试。`sanitizePlaceholders` 在三个入口
（description / inputs / correctionHint）排毒，防 prompt 注入。

### 4.6 GEPA-lite best-of-K 重规划（可选）

`PlanOrAnswerError → GlobalReplan` 时，若 `policy.bestOfKReplan()=true`：生成 K 个候选 plan
（不同 prompt 突变轴）+ **确定性 fitness 纯函数选优**（零 LLM judge）：

```
fitness = 0.45 * criteriaCoverage + 0.35 * toolCallRatio + 0.20 * failedHit
```

感知层不可靠故不用 LLM judge 选 plan——用确定性信号（criteria 覆盖、TOOL_CALL 占比、
失败节点命中）。

---

## 5. 收益（vs 原生 ReAct）

| 维度 | 原生 ReAct | PEV |
|------|-----------|-----|
| **失败处理** | 笼统"再试一次"或硬失败 | 类型驱动：DeviceFailure 不重试 / PlanOrAnswerError replan / Perception 不盲信 |
| **分支完整性** | 运行时才发现漏处理 | sealed switch 编译期保证穷举 |
| **诊断可信** | LLM 自评"为什么失败"（会编）| 纯函数信号诊断（zero-LLM，确定）|
| **降级** | 要么无限重试要么硬失败 | `AcceptPartial` 一等终态（诚实返回部分结果 + degraded 标记）|
| **验证成本** | 每次都 LLM 验证 | 规则短路：确定性失败不调 LLM |
| **自纠正** | 机械重试 | correction hint 注入，LLM 带反馈重做 |
| **执行模型** | 单线索 tool-call 循环 | BSP 拓扑并行（DAG 层内并行、层间屏障）|
| **可测性** | 端到端才能测 | diagnose/dispatch 纯函数，可穷举单测（mock 证控制流）|

**一句话**：ReAct 是"想一步做一步"的反射弧；PEV 是"先规划、执行、验证、诊断、再决策"的闭环——
它把"为什么失败"从 LLM 的口算里拿出来，变成类型驱动的确定性 dispatch。

---

## 6. 实例化一个 PEV agent

### 6.1 组件清单（要凑齐什么）

```
AgentKernel（聚合 4 个 SPI）
├── LLMProvider           ← 流式 LLM（agent-core-java Model 适配）
├── ToolExecutor registry ← Map<ToolName, ToolExecutor>（@Tool 自省或手填）
├── CheckpointStore       ← 内存 / Redis / JDBC
└── SafetyBoundary        ← 预算 + 工具白名单 + 敏感模式 + criteria 覆盖

Planner        ← DefaultPlanner(kernel)（NL → TaskGraph）
Verifier       ← DefaultVerifier(kernel)（rule + LLM）
PregelExecutor ← DefaultPregelExecutor(kernel, errorPolicy, subAgentBudget)
ExecutionPolicy ← PlanningMode / VerifyMode / maxRetries / bestOfK / pevSelfHealingEnabled
```

### 6.2 三种实例化路径

**路径 A：Spring autoconfig（生产）**

`PEVAlphaConfiguration` 一键装配：`@Import(PEVAlphaConfiguration.class)` 进 Spring Boot app，
配 `pev.alpha.api-key/base/model`（或 `OPENJIUWEN_*` env），得到 `pevAlphaHandler` bean
（`AlphaRuntimeHandler` PEV 模式）。

**路径 B：编程式（测试 / 自定义接线）**

```java
AgentKernel kernel = new DefaultAgentKernel(
    llmProvider, toolExecutors, toolDefinitions,
    new InMemoryCheckpointStore(),
    new DefaultSafetyBoundary(toolWhitelist, sensitivePatterns));

Planner planner     = new DefaultPlanner(kernel);
Verifier verifier   = new DefaultVerifier(kernel);
PregelExecutor exec = new DefaultPregelExecutor(kernel, errorPolicy, subAgentBudget);

ExecutionPolicy policy = new ExecutionPolicy(
    PlanningMode.AUTO, VerifyMode.STRICT,
    maxRetries = 3, bestOfK = 2, pevSelfHealingEnabled = true);

// TaskContext 装 kernel + planner + verifier + policy + budget
PEVAlphaStrategy strategy = new PEVAlphaStrategy();
Flux<AgentEvent> events = strategy.execute(taskContext);  // 订阅事件流
```

**路径 C：极小内核参考（零框架）**

`design-reference` 模块的 `MinimalAgentEngine`（<800 行，java.base only）——用于理解内核、
做设计参考、回归守门。不依赖 Spring / Reactor。

### 6.3 为一个具体场景设计 PEV agent（清单）

以"投资分析"为例（已用真 LLM e2e 验证）：

1. **定义 PlanGoal**：`description="完成投资分析"` +
   `successCriteria=["给出具体配置建议", "引用风险评估"]` +
   `constraints=[MaxSteps=5, RequiredTool=analyzePortfolio]`
2. **注册工具**：`analyzePortfolio(portfolioId)` / `researchMarket(sector)` /
   `assessRisk(investorProfile)`（`@Tool` 或 `ToolExecutor`）
3. **设 ExecutionPolicy**：`VerifyMode.STRICT`（逐节点 + criteria）/ `maxRetries=3`
   （LocalReplan 上限）/ `bestOfK=2`（GlobalReplan 时双候选）
4. **配 Budget**：`Budget.Fixed(maxLLMCalls=20, maxToolCalls=15, maxTokens=50k, timeoutMillis=300k)`
5. **设 SafetyBoundary**：工具白名单 `{analyzePortfolio, researchMarket, assessRisk}` +
   敏感模式（卡号 / 身份证）+ criteria 覆盖检查
6. **订阅事件流**：`PLAN_GENERATED` → `LAYER_COMPLETED` → `VERIFY_PASSED/FAILED` →
   （若失败）`ROOT_CAUSE_DIAGNOSED` → `PLAN_REVISED` → … → `TASK_COMPLETED` 或
   `TASK_COMPLETED(degraded)`

**Planner 会生成形如这样的图**（真 LLM e2e 实测）：

```
A: analyzePortfolio(P1001)  ─┐
B: researchMarket(tech)     ─┼─> D: 综合分析(给建议)  [criteria: 配置建议 + 风险引用]
C: assessRisk(growth_seeker)─┘
```

Execute 并行跑 A/B/C（层 0），屏障后跑 D（层 1）；Verify 逐节点 + 检 criteria；通过 →
`TASK_COMPLETED`。

---

## 7. 诚实边界（不装）

PEV **不做**这些（明确 defer）：

- **per-node ReActAgent**（节点内 ReAct 子循环）——当前 LLM_CALL 节点直接 `kernel.think()`，
  不带工具循环。`DefaultPregelExecutor` 标记 deferred。
- **GroundTruthVerifier**（带 ground truth 的强验证）——当前 DefaultVerifier 用 LLM 软判断。
- **Constraint 注入到 Planner**（MaxSteps / RequiredTool 强约束）——当前只 warn。
- **criteria double-LLM**（PEV verify 已是 LLM，再过 criteria rail 是 double-LLM）——
  `PEVToRailBridge` 标记需调用方显式 opt-in。
- **真 LLM 自愈闭环 e2e**（DeviceFailure → AcceptPartial 在真 LLM 下端到端）——mock 证控制流，
  真 LLM 软观察。

---

## 8. 参考实现索引

| 角色 | 位置 |
|------|------|
| **极小内核参考**（P8，<800 行纯 Java）| `design-reference/src/test/java/com/openjiuwen/reference/gepa3/MinimalAgentEngine` |
| **生产主循环** | `agent-runtime/src/main/java/com/openjiuwen/runtime/alpha/PEVAlphaStrategy.java` |
| **BSP 执行器** | `agent-runtime/src/main/java/com/openjiuwen/runtime/alpha/executor/DefaultPregelExecutor.java` |
| **规则 + LLM 验证** | `agent-runtime/src/main/java/com/openjiuwen/runtime/alpha/verifier/DefaultVerifier.java` |
| **纯函数诊断 + dispatch** | `agent-runtime/src/main/java/com/openjiuwen/runtime/beta/selfheal/RootCauseDiagnoser.java` |
| **sealed 类型** | `agent-runtime/src/main/java/com/openjiuwen/core/alpha/verifier/{NodeResult,ReplanAction,RootCause}.java` |
| **Spring 装配** | `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/alpha/PEVAlphaConfiguration.java` |
| **PEV ↔ Rails 桥** | `agent-runtime/src/main/java/com/openjiuwen/runtime/beta/bridge/PEVToRailBridge.java` |
