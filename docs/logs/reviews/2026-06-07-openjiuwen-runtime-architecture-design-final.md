# Openjiuwen-Java Runtime 架构设计文档

> **项目**：Openjiuwen-Java — 企业级 DeepAgent 框架
> **版本**：v1.0
> **总架构师**：姚骏（突突突）
> **方法论**：GEPA 遗传-帕累托优化 + 奇点分叉博弈论（50 轮推演）
> **日期**：2026-06-07
> **文档性质**：最终架构设计文档（唯一权威来源）

---

## 目录

1. [项目定位与愿景](#1-项目定位与愿景)
2. [第一性原理地基](#2-第一性原理地基)
3. [架构全景图](#3-架构全景图)
4. [核心设计决策与来源](#4-核心设计决策与来源)
5. [GEPA 迭代产出的优良基因](#5-gepa-迭代产出的优良基因)
6. [奇点分叉博弈产出的设计](#6-奇点分叉博弈产出的设计)
7. [架构师决策记录](#7-架构师决策记录)
8. [模块结构与代码组织](#8-模块结构与代码组织)
9. [核心接口定义](#9-核心接口定义)
10. [安全架构](#10-安全架构)
11. [企业接入架构（SDK）](#11-企业接入架构sdk)
12. [Spring 生态集成](#12-spring-生态集成)
13. [元Agent 晋升机制](#13-元agent-晋升机制)
14. [successCriteria 渐进式知识积累](#14-successcriteria-渐进式知识积累)
15. [P1/P2 范围与演进路线](#15-p1p2-范围与演进路线)
16. [架构风险与缓解](#16-架构风险与缓解)
17. [附录：50 轮 GEPA 演进时间线](#17-附录50-轮-gepa-演进时间线)

---

## 1. 项目定位与愿景

### 1.1 一句话定位

**Openjiuwen-Java 是 Java 生态首个面向企业级 Deep Agent 的原生框架，基于 Spring Boot + Spring AI，让行业 Java 开发者定义一个 Deep Agent 就像写一个 @Service 一样自然。**

### 1.2 核心愿景

```
行业 Java 开发者，用 Spring Boot 写一个 Deep Agent，
应该像写一个 @Service 一样自然。
```

三个核心约束：
- **不铲掉现有系统** — 扔进去就能跑，不改 Java 版本，不换框架
- **可迭代** — 今天跑叶子任务，明天加规划，后天加验证，不需要重构
- **简洁** — 开发者只关心三件事：Agent 是谁、能用什么工具、怎么跑

### 1.3 竞争格局

| 竞品 | 定位 | Openjiuwen 的差异化 |
|------|------|-------------------|
| AgentScope-Java v2 | 阿里巴巴 Agent 平台 | Openjiuwen：Deep Agent（PEV）、Agent 进化、私有化优先、SDK Java 8 |
| Spring AI Alibaba | Spring AI Agent 编排层 | Openjiuwen：Framework-agnostic、独立竞争、不绑定通义千问 |
| LangChain4j | Java LLM 集成层 | Openjiuwen：Deep Agent + 元Agent晋升 + 知识积累闭环 |

**差异化窗口 6-12 个月**。最大杀手锏：Agent Evolution + Pregel 图引擎 + 私有化部署优先。

---

## 2. 第一性原理地基

### 2.1 业务视角的 DeepAgent 本质

**DeepAgent 不是"更聪明的 Agent"，而是在不完全信息条件下，用 LLM 的语义理解能力替代人工判断节点，实现端到端的业务流程自动化。**

与传统 BPM（Camunda/Activiti）的区别：BPM 要求设计时确定每一步；DeepAgent 处理**设计时无法完全预定义**的场景。

### 2.2 Runtime 的本质：操作系统内核

Runtime 既不是编排器（流程可预定义），也不是容器（组件无主动行为），而是**操作系统内核**：

- 管理资源：Token 预算（= CPU 时间）、上下文窗口（= 内存）、工具调用（= IO）
- 提供抽象：Task（= 进程）、VFS（= 文件系统）、Checkpoint（= 信号）
- 隔离故障：Agent 级别的故障不扩散

> **设计来源**：GEPA Cycle 1-2，第一性原理拆解。推翻了 v3 将 Runtime 视为"编排器"的定位，重新定义为"OS 内核"。

### 2.3 会被模型吃掉的能力 vs 永久基础设施

| 分类 | 能力 | 预计存续期 |
|------|------|-----------|
| **临时补丁** | Planner 的 prompt engineering | 1-2 年 |
| **临时补丁** | ContextEngineer 上下文压缩 | 1-3 年 |
| **临时补丁** | Verifier 独立验证轮次 | 1-2 年 |
| **永久基础设施** | 检查点与恢复 | 永久 |
| **永久基础设施** | Token 预算管控 | 永久 |
| **永久基础设施** | 工具注册与 MCP 调用路由 | 永久 |
| **永久基础设施** | DAG 拓扑排序与并行执行 | 永久 |
| **永久基础设施** | 审计日志与可观测性 | 永久 |
| **永久基础设施** | Agent 无状态 + 虚拟线程并发 | 永久 |

> **设计原则**：依赖 Spring 的稳定基础设施（IoC/Reactor/Spring Data），自建"智能层"（执行策略/安全边界/元Agent）。架构按"可替换"设计，但默认实现假设模型不会自动变好。

### 2.4 Runtime 的 7 个系统调用

| # | 名称 | 签名 | 等价 OS 概念 | 永久性理由 |
|---|------|------|-------------|-----------|
| 1 | **spawn** | `taskId = spawn(agentName, input, budget)` | fork + exec | 创建执行单元，所有 Runtime 的入口 |
| 2 | **invoke** | `result = invoke(toolName, args)` | read/write (IO) | 与外部世界的唯一交互通道（MCP 协议层） |
| 3 | **yield** | `yield(checkpoint, reason)` | sigsuspend | 暂停并保存状态（等审批、等外部事件、预算耗尽） |
| 4 | **resume** | `resume(taskId, checkpointId, input)` | sigreturn | 从检查点恢复（崩溃恢复、审批回调） |
| 5 | **alloc** | `budget = alloc(parentBudget, limits)` | setrlimit | 资源预算分配（Token 上限、时间上限、工具权限） |
| 6 | **observe** | `flux = observe(taskId, eventTypes)` | epoll | 事件流订阅（可观测性、审计、前端推送） |
| 7 | **query** | `status = query(taskId)` | procfs | 状态查询（运维 dashboard、健康检查） |

**关键洞察**：Plan / Verify / ReAct / Workflow 不在系统调用中。它们是运行在"用户态"的策略，模型能力提升时可替换，内核不变。

> **设计来源**：GEPA Cycle 1-2，第一性原理推导。Plan/Verify/ReAct 从系统调用中移除是抗熵增的关键决策。

---

## 3. 架构全景图

```
┌─────────────────────────────────────────────────────────────────┐
│  开发者层                                                        │
│  @Agent(name="order-refund") + @Tool + AgentClient.invoke()     │
├─────────────────────────────────────────────────────────────────┤
│  Spring Boot Starter                                             │
│  OpenjiuwenAutoConfiguration → AgentBeanPostProcessor           │
│  @Agent/@Tool 注解扫描 → AgentRegistry 注册                     │
├─────────────────────────────────────────────────────────────────┤
│  L3 策略层（"用户态程序"，可替换）                                 │
│  ┌──────────────────┐  ┌──────────────────┐                     │
│  │ AlphaStrategy    │  │ BetaStrategy     │                     │
│  │ (PEV 显式控制)   │  │ (LLM 自主编排)   │  ← P2             │
│  │ Planner          │  │ GoalSpec         │                     │
│  │ PregelExecutor   │  │ GuardrailEngine  │                     │
│  │ Verifier         │  │ ContextManager   │                     │
│  └────────┬─────────┘  └────────┬─────────┘                     │
│           └──────────┬──────────┘                               │
│                      ▼                                          │
│  L2 调度层                                                        │
│  AdaptiveStrategy（AutonomyLevel 四档路由）                       │
├─────────────────────────────────────────────────────────────────┤
│  L1 内核层（"系统调用"，稳定不变）                                 │
│  ┌──────────────────────────────────────┐                       │
│  │ AgentKernel（7 个系统调用）           │                       │
│  │ spawn / invoke / yield / resume      │                       │
│  │ alloc / observe / query              │                       │
│  ├──────────────────────────────────────┤                       │
│  │ SafetyBoundary（统一安全边界）        │                       │
│  │ Token预算 / 迭代限制 / 递归深度       │                       │
│  │ MCP安全 / criteria覆盖检查           │                       │
│  └──────────────────────────────────────┘                       │
├─────────────────────────────────────────────────────────────────┤
│  基础设施层（委托给 Spring / 企业基础设施）                         │
│  Spring AI ChatModel │ MCP 协议 │ Spring Data（CheckpointStore）│
│  Reactor（Flux/Mono）│ Actuator │ VectorStore（Agent 记忆）     │
├─────────────────────────────────────────────────────────────────┤
│  SDK 桥接层（Java 8 企业系统接入）                                 │
│  sdk-api（零依赖）→ sdk-remote（HTTP/MCP）→ sdk-embedded（Java21）│
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 核心设计决策与来源

以下每个核心设计决策都标注了**来源**：

| 标记 | 含义 |
|------|------|
| 🔬 **第一性原理** | 从本质推导，不经由 GEPA 迭代或博弈 |
| 🧬 **GEPA 基因** | 通过 GEPA 白盒反思+突变迭代存活下来的优良基因 |
| ⚔️ **博弈产出** | 通过奇点分叉博弈（Alpha↔Beta 互相拆解）催生的设计 |
| 👤 **架构师决策** | 架构师（姚骏）的明确裁决 |

| # | 设计决策 | 来源 | 理由 |
|---|---------|------|------|
| 1 | Runtime = OS 内核，7 个系统调用 | 🔬 第一性原理 | 编排器和容器的假设都是错的；Agent 有主动推理能力，需要 OS 级别的资源管理和故障隔离 |
| 2 | Plan/Verify/ReAct 不在系统调用中 | 🧬 GEPA C1-2 | v3 的四种 Strategy 过度设计；Workflow 移出 Runtime，PlannerExecutor 合并，最终只有 Alpha(PEV) + Beta(自主) |
| 3 | Fusion 融合架构（Alpha + Beta 共存） | ⚔️ 博弈 R3 | 博弈第三轮交叉吸收的结论："两条路径不是二选一，而是同一个内核上的两种策略" |
| 4 | AutonomyLevel 四档路由 | ⚔️ 博弈 R3 | 博弈发现两种路径需要统一的调度机制 → AdaptiveStrategy |
| 5 | SafetyBoundary 底层统一 | ⚔️ 博弈 R3 | 博弈发现无论自主度多高，安全检查必须统一 |
| 6 | GuardrailEngine（代码级护栏） | ⚔️ 博弈 R1 | Alpha 拆解 Beta 的工具安全问题 → "护栏优于禁止"原则 |
| 7 | PlanningMode 三档（AUTO/SEMI_AUTO/MANUAL） | ⚔️ 博弈 R2 | Beta 拆解 Alpha 的静态规划天花板 → Alpha 获得自适应规划基因 |
| 8 | Agent 无状态设计（record + with* 方法链） | 🧬 GEPA C5-6 | 极端场景压力测试证明有状态 Agent 在并发和崩溃恢复下不可靠 |
| 9 | 按步检查点（非按层检查点） | 🧬 GEPA C7 | 按拓扑层检查点导致层内并行节点失败丢失全部进度 |
| 10 | 双轨审计（操作日志 + ReasoningTrail） | ⚔️ 博弈 R1 | Alpha 拆解 Beta 的合规问题 → 确定性操作日志（审计依据）+ ReasoningTrail（调试辅助） |
| 11 | 元Agent 策略无关性 | ⚔️ 博弈 R4 | 博弈揭示元Agent封装的是安全边界+工具集+历史数据，不绑定任何执行策略 |
| 12 | Spring 管基础设施，我们管智能 | 👤 架构师决策 + ⚔️ 博弈 R5 | 博弈+架构师裁决：依赖 Spring 稳定基础设施，自建执行策略和安全边界 |
| 13 | AutonomyLevel 显式声明，无默认值 | 👤 架构师决策 | 框架默认值是偷懒；自主度由业务领域熟悉度决定，不由 Java 版本决定 |
| 14 | 元Agent 单继承 + 半自动晋升 | 👤 架构师决策 | 多重继承模糊边界；自动晋升风险不可控 |
| 15 | successCriteria 渐进式知识积累 | 👤 架构师决策 | 选择题>填空题；本体驱动提案；验证沉淀回本体 |
| 16 | P1 先修安全+覆盖率，性能延后 | 👤 架构师决策 | 安全是架构属性（不改就得推翻重来），性能是工程属性（上线前解决） |
| 17 | P1=Alpha+安全+SDK，P2=Beta+successCriteria+Meta-Agent | 👤 架构师决策 | P1 目标是让企业先用起来，不需要全部能力 |
| 18 | 工具动态发现（从静态白名单进化） | ⚔️ 博弈 R2 | Beta 拆解 Alpha 的工具组合爆炸 → ToolGroup 运行时可扩展 |
| 19 | MCP 6 层安全体系 | 🧬 GEPA C21-25 | W4 修复（架构师优先级决策）驱动，从简单 mTLS 扩展到完整安全栈 |
| 20 | 安全层与 SafetyBoundary 职责分离 | 🧬 GEPA C24 | 避免 SafetyBoundary 膨胀；安全全走 SecurityPipeline，SafetyBoundary 只管非安全层的底层保护 |

---

## 5. GEPA 迭代产出的优良基因

50 轮 GEPA 白盒反思+突变迭代中，以下 6 个基因通过了全部 10 个极端场景压力测试，存活到最后：

### 5.1 存活的优良基因

| # | 基因 | 存活场景数 | 为什么抗打击 | 存活轮次 |
|---|------|----------|------------|---------|
| 1 | **Plan-Execute-Verify 三阶段分离** | 10/10 | 每个阶段独立可替换、可重试、可恢复；Verifier 可选择独立模型 | C5 → C40 |
| 2 | **Agent 无状态设计** | 6/10 | record + with* 方法链，每次状态变更产生新对象，天然并发安全 | C5 → C40 |
| 3 | **检查点机制** | 6/10 | 从"按层"突变到"按步"，解决并行节点部分失败 | C7 → C40 |
| 4 | **策略可插拔** | 5/10 | ExecutionStrategy 接口 + sealed interface 类型系统 | C5 → C40 |
| 5 | **MCP 协议解耦** | 4/10 | 工具调用与 Agent 逻辑完全分离，通过协议桥接 | C5 → C40 |
| 6 | **拓扑排序 + BSP 并行** | 4/10 | Pregel 超步模型保证上层全部完成后才执行下层 | C5 → C40 |

### 5.2 被淘汰的"看似美好"基因

| # | 淘汰基因 | 淘汰原因 | 淘汰轮次 |
|---|---------|---------|---------|
| 1 | WorkflowStrategy | 确定性工作流是 Camunda 的领域，不属于 Agent Runtime | C1 |
| 2 | PlannerExecutorStrategy | 与 Deep 策略 70% 重叠 | C1 |
| 3 | LLM 自验证（Verifier = Executor 同模型） | 幻觉一致性：模型不会发现自己的错误 | C5 |
| 4 | CompletableFuture.supplyAsync 盲用 | 虚拟线程环境下反而破坏调度 | C6 |
| 5 | 按拓扑层检查点 | 层内并行节点失败时丢失全部进度 | C7 |
| 6 | 假设所有操作无副作用 | MCP 工具调用可能已产生不可逆效果 | C8 |
| 7 | LLM 盲信（不加参数校验） | 幻觉工具名或参数导致生产事故 | C5 |
| 8 | 单模型绑定 | 模型宕机或限流时全系统不可用 | C5 |
| 9 | ChatModelAdapter 做所有事 | Token统计和护栏应交给 Advisor/SafetyBoundary | C13 |
| 10 | 双层护栏概念混淆 | 审计和行为约束应分离，Advisor做审计，GuardrailEngine做行为 | C14 |
| 11 | runtime 单模块 | 全塞一起必然膨胀，拆为 4 子模块 | C15 |
| 12 | AutonomyLevel 继承自元Agent | 安全边界必须显式声明，框架默认值是偷懒 | C11-12 |

---

## 6. 奇点分叉博弈产出的设计

50 轮推演中进行了 **5 轮奇点分叉博弈**（Alpha 开发者显式控制路径 ↔ Beta LLM 自主编排路径）：

### 6.1 第一轮：Alpha 拆解 Beta

**博弈焦点**：状态一致性、合规审计、工具安全

| Alpha 的攻击 | Beta 的回应 | 博弈产出的设计 |
|-------------|-----------|--------------|
| LLM 可能生成有环 TaskGraph | GuardrailEngine 运行时环检测 | **GuardrailEngine** — 代码级护栏，LLM 无法绕过 |
| 决策过程不可追溯 | ReasoningTrail 记录决策链 | **双轨审计** — 确定性操作日志 + 推理过程记录 |
| LLM 可能越权调用工具 | 代码级拦截 | **"护栏优于禁止"原则** — Alpha 从 Beta 学到 |

### 6.2 第二轮：Beta 拆解 Alpha

**博弈焦点**：适应性、工具组合、学习闭环

| Beta 的攻击 | Alpha 的回应 | 博弈产出的设计 |
|------------|-----------|--------------|
| 静态 TaskGraph 无法应对开放世界 | 引入 PlanningMode 三档 | **PlanningMode（AUTO/SEMI_AUTO/MANUAL）** — Alpha 获得自适应规划基因 |
| 预注册工具无法覆盖 LLM 可想象的组合 | ToolGroup 概念 | **工具动态发现** — 从静态白名单进化到运行时可扩展 |
| Agent 无法学习 | 离线进化闭环 | 确认**离线进化（LLM-as-Judge）在合规场景更安全**，在线学习留给 Beta |

### 6.3 第三轮：交叉吸收（最重要的一轮）

**博弈焦点**：是否可以融合

**这是 50 轮推演中回报率最高的博弈环节。**

博弈前假设：Alpha 和 Beta 二选一。
博弈后结论：**"两条路径不是二选一，而是同一个内核上的两种策略"**

直接催生的设计：

```
⚔️ 博弈产出：Fusion 融合架构
├── AutonomyLevel 四档路由（SUPERVISED / GUIDED / AUTONOMOUS / FULL_AUTO）
├── AdaptiveStrategy 调度层（根据自主度路由到 Alpha 或 Beta）
├── SafetyBoundary 底层统一（无论自主度多高都生效）
├── A 独有基因：ApprovalGate / sealed interface 事件 / Constraint / 合规审计链
└── B 独有基因：LLMDecision / SelfReflectionTrigger / ContextWindowManager / GoalSpec
```

**如果没有这一轮博弈**：项目会走 Alpha vs Beta 二选一的路线，场景覆盖从 ~80% 降到 ~40%。

### 6.4 第四轮：元Agent 的归属

**博弈焦点**：元Agent 属于 Alpha 世界还是 Beta 世界？

**博弈结论**：元Agent 是**策略无关的** — 它封装的是 SafetyBoundary 配置 + 工具集 + 历史成功率数据，不绑定任何执行策略。新 Agent 选 Alpha 还是 Beta 与元Agent 无关。

**如果没有这一轮博弈**：会陷入"Alpha 的元Agent"和"Beta 的元Agent"两套体系，继承机制变得复杂且脆弱。

### 6.5 第五轮：Spring 依赖纯度

**博弈焦点**：内核层是否零 Spring 依赖？

| 博弈方 | 主张 | 结果 |
|--------|------|------|
| 纯洁路线 | AgentKernel 零依赖，需要适配层 | ❌ |
| 实用路线 | 直接用 Spring Bean，减少代码 | 架构师采纳 |
| **架构师裁断** | — | **"Spring 管基础设施（怎么跑），我们管智能（怎么想）"** |

**依赖边界**：不是"零依赖 vs 全依赖"，而是按"稳定基础设施 vs 创新前沿"划分。

---

## 7. 架构师决策记录

以下决策由架构师（姚骏）在 GEPA 推演过程中做出明确裁决：

| # | 决策 | 架构师原话 | 背景 |
|---|------|----------|------|
| D1 | AutonomyLevel 显式声明，无默认值 | "每个Agent定义时候可以有一个显式声明" | 推翻了子智能体提出的"按Java版本自动选择"方案 |
| D2 | 元Agent 单继承，半自动晋升 | "晋升是半自动，系统提名+人工审批" | 推翻了多重继承和全自动晋升方案 |
| D3 | Spring 管基础设施，我们管智能 | "依赖放在核心的稳定的并且广为接受的部分" | 博弈+架构师裁决：依赖 Spring 稳定基础设施，自建智能层 |
| D4 | successCriteria 渐进式知识积累 | "让客户多做选择题，少做填空题" | 推翻了"开发者手写清单"和"等模型变强"两个选项 |
| D5 | P1 先修覆盖率+安全，性能延后 | "安全因素设计期间如果不考虑，后面比较难补充" | "安全是架构属性，性能是工程属性" |
| D6 | P1/P2 功能拆分 | "先修W3+W4，W1延后" | P1=Alpha+安全+SDK(~6K行)，P2=Beta+successCriteria+Meta-Agent |
| D7 | 验证沉淀为延续性知识 | "经过用户开发者检验过的输入要尽可能形成对这个任务延续性的知识" | successCriteria 不是静态清单，是活的认知积累 |
| D8 | 驱动因素是业务领域而非技术版本 | "跟Java的版本没有关系，主要还是考虑业务的新领域还有复杂度" | 推翻了"Java 8→SUPERVISED，Java 21→AUTONOMOUS"的技术绑定 |

---

## 8. 模块结构与代码组织

### 8.1 Maven 模块树

```
openjiuwen-java/
├── openjiuwen-bom/                           ← 版本管理 BOM
│
├── openjiuwen-core/                          ← 纯接口+模型，Java 21，零 Spring 依赖
│   └── com.openjiuwen.core/
│       ├── agent/   (AgentDefinition, AgentMaturity, AutonomyLevel)
│       ├── kernel/  (AgentKernel, SafetyBoundary, TaskStatus, Budget)
│       ├── alpha/   (TaskGraph, Constraint, ApprovalGate, AlphaEvent)
│       ├── beta/    (GoalSpec, LLMDecision, Guardrail, BetaEvent)
│       ├── meta/    (MetaAgentManifest, PromotionCriteria)
│       └── spi/     (CheckpointStore, MemoryStore, ToolProvider)
│
├── openjiuwen-runtime/                       ← Spring Boot 执行引擎
│   ├── runtime-core/                         ← 共享组件（AgentKernel 实现 + SafetyBoundary + 调度层）
│   ├── runtime-alpha/                        ← Alpha PEV 策略（Planner + PregelExecutor + Verifier）
│   ├── runtime-beta/                         ← Beta 自主策略（P2）← P2
│   └── openjiuwen-spring-boot-starter/       ← Spring Boot Starter
│
├── openjiuwen-sdk/                           ← Java 8 桥接层
│   ├── sdk-api/                              ← 纯接口，Java 8，零依赖
│   ├── sdk-remote/                           ← HTTP/MCP 远程调用，Java 8
│   └── sdk-embedded/                         ← 进程内调用，Java 21
│
├── openjiuwen-evolution/                     ← Agent 进化系统 ← P2
│   ├── evaluate/  (LLM-as-Judge)
│   ├── optimize/  (Prompt/Memory/Tool 优化器)
│   ├── ab/        (A/B 测试)
│   └── meta/      (晋升/继承/降级)
│
├── openjiuwen-examples/                      ← 示例项目
└── openjiuwen-distribution/                  ← 发布包
```

### 8.2 代码量估算

| 模块 | P1 | P2 | 总计 |
|------|-----|-----|------|
| L1 内核（AgentKernel + SafetyBoundary + 模型） | ~1,500 行 | 不变 | ~1,500 行 |
| L2 调度（AdaptiveStrategy + AgentRegistry） | ~500 行 | ~200 行 | ~700 行 |
| Alpha 策略（PEV） | ~2,700 行 | — | ~2,700 行 |
| Beta 策略（自主编排） | — | ~2,200 行 | ~2,200 行 |
| successCriteria（知识积累） | — | ~1,500 行 | ~1,500 行 |
| Meta-Agent（晋升） | — | ~500 行 | ~500 行 |
| MCP 安全 | ~800 行 | ~400 行 | ~1,200 行 |
| Spring Starter | ~500 行 | ~200 行 | ~700 行 |
| SDK | ~2,200 行 | — | ~2,200 行 |
| 测试 | ~1,900 行 | ~2,000 行 | ~3,900 行 |
| **P1 总计** | **~6,000 行** | | |
| **P2 增量** | | **~7,000 行** | |
| **全量** | | | **~17,000 行** |

---

## 9. 核心接口定义

### 9.1 AgentKernel — 7 个系统调用

```java
public interface AgentKernel {
    TaskId spawn(AgentName agent, TaskInput input, Budget budget);
    ToolResult invoke(TaskId task, ToolName tool, Map<String, Object> args);
    Checkpoint yield(TaskId task, YieldReason reason);
    void resume(TaskId task, CheckpointId checkpoint, TaskInput newInput);
    Budget alloc(TaskId parent, BudgetLimits limits);
    Flux<AgentEvent> observe(TaskId task, Set<EventType> eventTypes);
    TaskStatus query(TaskId task);
}
```

### 9.2 SafetyBoundary — 统一安全边界

```java
public interface SafetyBoundary {
    Optional<Violation> checkInvoke(TaskId task, ToolName tool, Map<String, Object> args);
    Optional<Violation> checkSpawn(TaskId parent, AgentName child, Budget requested);
    void audit(TaskId task, AgentEvent event);
    boolean isIterationExceeded(TaskId task, int maxIterations);
    boolean isBudgetExhausted(TaskId task);
    // ⚔️ 博弈产出：criteria 覆盖检查
    Optional<Violation> checkCriteriaCoverage(TaskId task, List<String> criteria, List<String> verified);
}
```

### 9.3 ExecutionStrategy — 策略接口

```java
public interface ExecutionStrategy {
    Flux<AgentEvent> execute(TaskContext context);
}
```

### 9.4 AutonomyLevel — 四档自主度（⚔️ 博弈产出）

```java
public enum AutonomyLevel {
    SUPERVISED,    // 每步审批（Alpha 完全控制）
    GUIDED,        // 关键节点审批（Alpha 为主）
    AUTONOMOUS,    // 仅护栏约束（Beta 为主，必须 basedOn 元Agent）
    FULL_AUTO      // 仅 SafetyBoundary（Beta 完全自主）
}
```

### 9.5 sealed interface 类型系统

| 类型 | sealed 子类数 | 用途 |
|------|-------------|------|
| TaskStatus | 8 | 任务状态机（Created→Planning→Executing→Verifying→Paused→Completed→Failed→Cancelled） |
| Violation | 7 | 违规类型（ToolNotAllowed / BudgetExhausted / DangerousOperation / RecursionDepth / IterationLimit / CriteriaNotCovered / McpSecurity） |
| YieldReason | 5 | yield 原因（HumanApproval / ExternalEvent / BudgetExhausted / Checkpoint / Error） |
| Budget | 2 | Fixed / Unlimited |
| Constraint | 4 | MaxSteps / RequiredTool / OutputFormat / Approval |
| AlphaEvent | 13 | Alpha 策略生命周期事件 |
| LLMDecision | 7 | Beta 策略 LLM 决策类型 |
| AgentMaturity | 4 | Novel / Stable / Proven / Veteran |

---

## 10. 安全架构

### 10.1 MCP 6 层安全体系

> **设计来源**：🧬 GEPA C21-25，架构师决策 P1 优先修 W4

| 层级 | 职责 | 实现 |
|------|------|------|
| L1 传输安全 | 数据加密 | mTLS（Spring Boot server.ssl） |
| L2 身份认证 | 谁在调用 | sealed AuthenticatedIdentity（mTLS/JWT/APIKey） |
| L3 权限控制 | 能调什么 | ToolAccessChecker + RBAC |
| L4 参数校验 | 参数合法 | TypeSpec（String/Number/Enum）+ 敏感字段检测 |
| L5 操作审计 | 调了什么 | SecurityAuditRecord + 异步三路输出 |
| L6 幂等保障 | 重试安全 | @Idempotent + CAS + 补偿升级人工 |

### 10.2 SafetyBoundary vs SecurityPipeline 职责分离

> **设计来源**：🧬 GEPA C24

```
SafetyBoundary（L1 内核层）
├── Token 预算检查
├── 迭代次数限制
├── 递归深度限制
├── criteria 覆盖检查（⚔️ 博弈产出）
└── MCP 安全检查（委托给 SecurityPipeline）

SecurityPipeline（安全层）
├── L2 身份认证
├── L3 权限控制
├── L4 参数校验
└── L6 幂等检查
```

### 10.3 敏感数据脱敏策略

> **设计来源**：🧬 GEPA C23

**仅审计日志和最终输出层脱敏，LLM 推理过程用原始值**（否则影响推理质量）。原始值 AES-256 加密存储在合规数据库。

### 10.4 三档环境 Profile

| 环境 | 安全级别 | 说明 |
|------|---------|------|
| development | HTTP 明文 | 开发环境降级 |
| staging | 单向 TLS | 测试环境 |
| production | mTLS 强制 | 生产环境 |

---

## 11. 企业接入架构（SDK）

### 11.1 三种接入模式

| 模式 | Java 版本 | 网络开销 | 适用场景 |
|------|----------|---------|---------|
| **SDK Remote** | Java 8 | HTTP/MCP | 存量企业系统 |
| **SDK Embedded** | Java 21 | 零网络 | 新系统 |
| **Spring Starter** | Java 21 | 零网络（@Agent 注解） | Spring Boot 应用 |

### 11.2 SDK API（零依赖，Java 8）

```java
// 企业开发者的入口
AgentClient client = AgentClientBuilder.remote("http://runtime:8080")
    .withTimeout(30, TimeUnit.SECONDS)
    .withRetry(3)
    .build();

AgentResult result = client.invoke("order-refund-agent", "处理订单12345的退款");
```

### 11.3 MCP 跨 JVM 工具调用

SDK 端内嵌轻量 MCP Server（~600 行，Servlet 3.0 async），Runtime 作为 MCP Client 调用业务系统的 @Tool 方法。

---

## 12. Spring 生态集成

### 12.1 依赖策略

> **设计来源**：👤 架构师决策 + ⚔️ 博弈 R5

```
Spring 管基础设施（怎么跑）        我们管智能（怎么想）
├── IoC 容器（@Component）         ├── AgentKernel 7个系统调用
├── 自动配置（@AutoConfiguration）  ├── SafetyBoundary 安全边界
├── Actuator（健康检查/指标）       ├── Plan-Execute-Verify 引擎
├── ChatModel（统一 LLM 调用）     ├── AdaptiveStrategy 自主度路由
├── ToolCallback / MCP（工具协议）  ├── GuardrailEngine 护栏引擎
├── VectorStore（向量存储）         └── Meta-Agent 晋升机制
├── Reactor（Flux/Mono）
└── Spring Data（存储后端）
```

### 12.2 Spring Boot Starter

```java
@AutoConfiguration
@ConditionalOnClass(ChatModel.class)
@EnableConfigurationProperties(OpenjiuwenProperties.class)
public class OpenjiuwenAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    public AgentKernel agentKernel(...) { ... }
    @Bean @ConditionalOnMissingBean
    public SafetyBoundary safetyBoundary(...) { ... }
    @Bean @ConditionalOnMissingBean
    public AlphaStrategy alphaStrategy(...) { ... }
    @Bean @ConditionalOnMissingBean
    public AdaptiveStrategy adaptiveStrategy(...) { ... }
    // ... 共 11 个 @Bean
}
```

### 12.3 开发者注解

```java
@Agent(name = "order-refund", autonomyLevel = AutonomyLevel.GUIDED)
public class OrderRefundAgent {
    @Tool(description = "查询订单状态")
    public OrderStatus checkOrder(@Param("orderId") String orderId) { ... }

    @Tool(description = "处理退款")
    public RefundResult processRefund(
        @Param("orderId") String orderId,
        @Param("amount") BigDecimal amount) { ... }
}
```

---

## 13. 元Agent 晋升机制

> **设计来源**：👤 架构师决策 D1/D2 + ⚔️ 博弈 R4

### 13.1 四级成熟度

```
Novel → Stable → Proven → Veteran
(手搓)   (验证)    (候选)    (正式元Agent)
```

| 转换 | 条件 | 触发 |
|------|------|------|
| Novel → Stable | 执行 ≥50次，成功率 ≥95%，异常率 ≤2%，持续 ≥7天 | 系统+人工 |
| Stable → Proven | 执行 ≥200次，跨 ≥3场景，漂移度 <15% | 系统+人工 |
| Proven → Veteran | 人工审批 + 至少1个团队已复用 | 人工 |

### 13.2 继承规则

- **单继承**：一个 Agent 只能基于一个元Agent
- **安全边界取交集**：继承的 + 自己的，全部生效，不能放松
- **AutonomyLevel 不继承**：即使父元Agent 是 AUTONOMOUS，新 Agent 也可能从 GUIDED 开始
- **元Agent 策略无关**（⚔️ 博弈 R4）：封装的是安全边界+工具集+历史数据，不绑定 Alpha 或 Beta

### 13.3 降级保护

- 自动触发：连续 5 次执行失败 或 成功率低于 80%（滚动 30 天）
- 降级后恢复条件比首次晋升更严格

---

## 14. successCriteria 渐进式知识积累

> **设计来源**：👤 架构师决策 D4/D7（← P2 范围）

### 14.1 闭环流程

```
本体/模板/LLM → CriteriaProposer 提案（选择题）
    → 用户确认（选择>填空，极少填空）
    → GoalSpec 注入 BetaStrategy
    → Agent 自主执行
    → CriteriaVerifier 逐条验证
    → 验证通过 → KnowledgeAccumulator 沉淀
    → 更新本体（下次提案更准，用户干预更少）
```

### 14.2 三层提案来源

| 来源 | 优先级 | 说明 |
|------|--------|------|
| 领域本体 | 最高 (1.0) | 经过验证的历史知识 |
| 行业模板 | 中 (0.8) | 金融10条/电力8条/制造8条/通用6条 |
| LLM 推理 | 低 (0.6) | 动态推断维度 |

### 14.3 知识膨胀控制（四层）

1. 同维度合并（按 dimension + industry 唯一键）
2. 时效衰减（30天满分→365天归零）
3. 低质淘汰（成功率 <30%）
4. 容量上限（默认 500 条）

---

## 15. P1/P2 范围与演进路线

### 15.1 四阶段演进

| 阶段 | 范围 | 工时 |
|------|------|------|
| **P1 Starter** | Alpha(PEV) + 安全 + SDK + Spring Starter | 2-3 人月 |
| **P2 原生重构** | Beta(自主) + successCriteria + Meta-Agent | 3-4 人月 |
| **P3 Agent 网关** | 跨框架互操作 + A2A | 2-3 人月 |
| **P4 完整生态** | Agent Store + 可视化 | 持续 |

### 15.2 P1 开发任务清单

| 任务 | 工时 | 验收标准 |
|------|------|---------|
| T1 编译通过 + 依赖整理 | 2天 | mvn compile 成功 |
| T2 Spring AI ChatModel 集成 | 3天 | 真实 LLM 调用返回结果 |
| T3 @Agent/@Tool 注解跑通 | 3天 | AgentRegistry 注册成功 |
| T4 10分钟 Quick Start | 2天 | 新人独立跑通第一个 Agent |
| T5 AlphaStrategy PEV 联调 | 5天 | 10节点 DAG 端到端执行 |
| T6 集成测试 | 3天 | 52+ 测试全部通过 |
| T7 SDK 远程联调 | 5天 | Java 8 系统成功调用 Runtime |
| T8 生产化打磨 | 3天 | actuator/metrics/mTLS |

---

## 16. 架构风险与缓解

### 16.1 当前风险 Top 5

| # | 风险 | 优先级 | 缓解措施 |
|---|------|--------|---------|
| R1 | SecurityPipeline 与 Web 层耦合 | P1 | 抽象 SecurityContext 接口，ServerWebExchange 只是实现之一 |
| R2 | Beta 的 LLM 成本失控 | P2 | Budget 消耗追踪应在 SafetyBoundary（内核级） |
| R3 | Planner 输出 TaskGraph 质量不可控 | P1 | Tarjan 环检测 + 引用完整性校验 + 自纠错循环 |
| R4 | core 模块 AgentDefinition 职责膨胀 | P1 | AgentDefinition 只保留身份信息，策略配置独立到 runtime |
| R5 | 过度设计惯性（P1 里有 5K 行 P2 骨架） | P1 | P1 删除非 P1 代码，用分支管理 P2 |

### 16.2 被忽略的企业客户问题（需在后续阶段解决）

1. Agent 版本管理（热更新 vs 检查点恢复冲突）
2. 多 Agent 对等通信（当前只有父子关系）
3. Agent 效果度量（评估框架未排进 P1/P2）
4. LLM Provider 降级策略
5. 运维团队的基础设施需求

---

## 17. 附录：50 轮 GEPA 演进时间线

```
C1-2      C5-6        C7-10         C11-14        C15-16       C21-30        C31-40       C41-50
 │         │           │              │             │            │              │            │
 ▼         ▼           ▼              ▼             ▼            ▼              ▼            ▼
[v3]     极端场景     奇点分叉      架构师决策     模块结构     深度打磨       P1代码落地   终局审计
4策略    白盒反思     博弈           落实          确立         MCP安全6层     146文件      健康检查
 │      10场景      3轮博弈        2大决策       10子模块     Alpha PEV      17K行代码    6.4/10熵
 │      6基因淘汰   Alpha/Beta     元Agent       Maven树      Beta自主       52测试       P2决策
 │      6基因提取   交叉吸收       Spring集成    代码骨架     successCriteria P1/P2切分   团队交接
 │        │        Fusion诞生     49骨架        确立         知识积累       SDK桥接      GEPA总结
 │        │           │              │             │            │              │            │
 ▼        ▼           ▼              ▼             ▼            ▼              ▼            ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  阶段1: 基因筛选        阶段2: 融合成型        阶段3: 深化打磨       阶段4: P1 落地     阶段5: 终局审计
  "什么是对的"          "怎么组合"             "怎么做对"            "做出来"            "好不好"
  C1 ─────── C10      C11 ─────── C20       C21 ────── C30     C31 ────── C40    C41 ── C50
```

**代码量演进**：
```
C1 → C10 → C20 → C30 → C40 → C50
0   4,500  3,852 11,786 17,117 17,117（设计推演完成，代码量不变）
    (估算)  (骨架) (深化)  (落地)  (审计)
```

**GEPA 方法论评分**：8/10

最有价值的环节：白盒反思（6基因一次淘汰）> 奇点分叉博弈（Fusion 诞生）> 帕累托前沿（数据驱动融合决策）

---

> **文档结束。架构设计阶段完成，进入 P1 开发阶段。**
>
> 总架构师：姚骏（突突突）
> 方法论：GEPA 遗传-帕累托优化 + 奇点分叉博弈论
> 推演周期：50 轮 / 29 路并行子智能体
> 最终产出：146 文件 / 17,117 行代码 / 52 测试 / 13 份报告 / 8 个架构师决策
