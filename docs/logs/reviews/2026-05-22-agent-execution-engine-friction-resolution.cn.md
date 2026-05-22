---
affects_level: L1
affects_view: logical
proposal_status: proposed
authors: ["Flash (Agent)", "LucioIT (Core Architect)"]
related_adrs: [ADR-0072, ADR-0079, ADR-0088, ADR-0100]
related_rules: [Rule-R-M, Rule-R-C.e]
affects_artefact: []
---

# 架构评审提案：agent-execution-engine 与 agent-service 核心摩擦消解 (Wave 1.2)

> **日期:** 2026-05-22
> **状态:** Proposed (已提交评审)
> **影响范围:** L1 层（涵盖 Logical, Process, Development, Physical 视图）

## 1. 背景 (Background)

在补齐 `agent-execution-engine` 模块的 L1 高阶设计过程中，开发组识别出了智能体服务端（`agent-service`）与执行引擎（`agent-execution-engine`）在控制流表达、无状态性边界定义以及多物理部署模式（Mode-A 与 Mode-B）下的三大核心架构摩擦。为了保障 `spring-ai-ascend` 平台的架构纯洁性，遵循 L0 顶层设计军规，特提此提案对上述摩擦进行根源性消解，统一两模块在 L1 层的交互契约与物理定界。

## 2. 变更定界 (Scope Statement)

本变更主要触及 **L1 层的逻辑视图（Logical View）与开发视图（Development View）**，次要影响 **过程视图（Process View）与物理视图（Physical View）**。
- **L1 Logical & Process**: 重新界定 Service 级 Task 与 Engine 级 Run 的状态归属；彻底废除基于 Java 异常的中断挂起机制，确立符合 A2A 协议的 Value-based（基于值返回）中断流转契约。
- **L1 Development & Physical**: 明确平台中心模式（Platform-Centric）与业务中心模式（Business-Centric）下，`client -> service -> engine` 调用链在任何物理部署拓扑下均保持“神圣不可侵犯”，Engine 始终保持纯粹的无状态计算芯片定位。

## 3. 根因分析与最强解读 (Root Cause / Strongest Interpretation)

1. **Observed failure / motivation**: 既有设计中试图通过受检异常（Checked Exception `SuspendSignal`）来表达智能体执行引擎的异步挂起与中断，且对业务中心部署模式（Mode-B）下 Engine 的独立性和状态管理边界存在模糊认知。
2. **Execution path**: `com.huawei.ascend.engine.spi.ExecutorAdapter.execute(...)` 抛出 `SuspendSignal` 异常，并在响应式流（Project Reactor）中流转。
3. **Root cause**: 
   - 挂起/中断属于正常的业务控制流程（正常分支），将其作为 Exception 抛出违反了“异常不用于控制流”的架构常识，且在反应式管道中会导致异常语义滥用（强制包装进 `Mono.error()`），破坏了 Telemetry 与监控。
   - 对“业务中心模式”的误解导致试图在 Engine 模块内部引入越过 Service 的本地存储/持久化设计，破坏了 Engine 的纯计算芯片定位。
4. **Evidence**: 
   - `com.huawei.ascend.engine.orchestration.spi.SuspendSignal` 继承自 `Exception`；
   - `agent-execution-engine/ARCHITECTURE.md` §5 接口定义与 ADR-0100 中的设计偏差。

## 4. 提案变更内容 (Proposed Change)

### 4.1 摩擦消解一：明晰 Service 与 Engine 的“服务化封装”关系
- **无状态定界**：
  - **Engine（执行引擎）** 的无状态性是**被动剥离**的。Engine 只负责 Task 内部更微观的 **Run 级状态**计算推进。它不感知长程状态，计算所需的全部输入必须由 Service 显式拼装为 `InjectedContext` 喂入，并在遇到阻断时立即输出 `StateDelta` 退栈。
  - **Service（服务端）** 的无状态性类似于传统微服务，是**主动持久化**的。Service 负责 **Task 级状态控制**以及之上的 Session、Memory 状态，通过对接外部分布式缓存（Redis/Temporal/Postgres）来实现自身的无状态和弹性收缩。
- **服务化封装**：`service` 与 `engine` 之间不是单纯的“Java 接口与实现”的关系，而是**服务化封装与管控**的关系。

### 4.2 摩擦消解二：废除“异常中断”，建立 A2A Value-based 挂起契约
- **彻底消灭 Exception 中断**：
  - 受检异常 `com.huawei.ascend.engine.orchestration.spi.SuspendSignal`（继承自 `Exception`）不再作为 Engine 正常挂起/中断的抛出通道。
  - 挂起是一种**正常且预期内**的控制流，必须作为执行结果的一部分以 **值（Value）** 的形式返回。
- **响应式契约重构**：
  - 引擎最底层无状态核心契约（`StatelessEngineExecutor`）接口重构为：
    ```java
    package com.huawei.ascend.engine.spi;

    import reactor.core.publisher.Mono;

    public interface StatelessEngineExecutor {
        /**
         * 纯反应式执行无状态计算：输入任务定义与投影上下文，输出执行 Delta
         * 正常完结与中途挂起一律通过 StateDelta 值返回，不抛出中途异常控制流
         */
        Mono<StateDelta> execute(TaskSpec task, InjectedContext ctx);
    }
    ```
- **A2A 强类型中断对齐**：
  - 在 `StateDelta` 中内置 nullable 的 `InterruptSignal`。若引擎决定挂起，则带回非空的 `InterruptSignal`；计算完结则为 `null`。
  - 信号类型严格按照 **A2A 协议规范** 进行定义和收口，杜绝自定义私有信号：
    * `INPUT_REQUIRED`：用户交互/审批挂起。
    * `SUB_TASK_AWAIT`：子智能体派生/外部 A2A 协作挂起。
    * `TOOL_EXECUTION`：真实物理工具调用拦截挂起。
    * `DELAY_AWAIT`：时间窗/定时挂起。
    * `POLICY_APPROVAL`：风控、预算限额或安全沙箱审批挂起。

### 4.3 摩擦消解三：确立业务中心模式（Mode-B）下的模块功能边界不变量
- **模块调用铁律**：`client -> service -> engine` 的单向调用链是**物理拓扑不变量**。
  - 在任何部署模式（Mode-A 平台中心 / Mode-B 业务中心）下，各个模块的功能定界绝对不变。
  - 在 Mode-B 下，业务端仅物理集成了 client，而 service 与 engine 只是共同下沉部署在业务物理边界内（如同一 JVM 或本地 Pod）。
  - Client 依然不能越过 Service 直接调用 Engine；Engine 也永远不能在缺乏 Service 封装的情况下独立运行。
- **无状态芯片不退化**：由于 Service 永远存在于物理链路中，Engine 在 Mode-B 下也无需在内部集成任何关系型数据库或外置存储参考实现（如 `InMemoryCheckpointer` 等），它永远作为最纯粹的、无状态计算芯片存在。

## 5. 替代方案评估 (Alternatives Considered)

| 替代方案 | 拒绝理由 |
|---|---|
| **方案 A**：保留受检异常 `SuspendSignal`，在 Engine 执行深度直接抛出。 | 1. 彻底破坏反应式编程（Project Reactor）的声明式链式流。在 `Mono` 管道中处理 Checked Exception 极其痛苦，必须进行大量硬包装。<br>2. 混淆了“控制流信号”与“运行期异常（故障）”，导致 Telemetry 对系统健康的度量产生误报。 |
| **方案 B**：允许 Client 越过 Service，在 Mode-B 下直接调用本地 Engine 进行计算。 | 1. 导致 Engine 模块退化，被迫在内部实现复杂的状态脱水、吸水和持久化逻辑，破坏其“纯计算芯片”定位。<br>2. 违反 L0 模块高内聚、低耦合的设计原则，使微服务治理体系在本地化集成时被击穿。 |

## 6. 验证计划 (Verification plan)

- [x] 静态编译校验：重构后，`agent-execution-engine` 编译无异常，无 `com.huawei.ascend.engine.spi` 以外的纯洁度破坏引入。
- [ ] 架构守护测试：`SpiPurityGeneralizedArchTest` (Rule E48) 与 `RuntimeMustNotDependOnPlatformTest` 依旧 100% 绿色通过。
- [ ] 单元/集成测试：
  - [ ] 验证 `execute` 方法返回的 `StateDelta` 在中途拦截到 A2A 标准中断（如 `TOOL_EXECUTION`）时，`interruptSignal` 属性非空且字段对齐 A2A 标准。
  - [ ] 验证没有出现基于抛出异常（Exception）来处理挂起的测试用例。

## 7. 部署与发布 (Rollout)

- **当前 Wave**: W1.2 / W2 (优先在当前演进阶段立即执行重构)
- **不冻结影响 (Freeze Impact)**: 本提案无需解冻 L0 物理工件，但需要针对 `agent-execution-engine` 的 L1 骨架、SPI 声明以及 `agent-service` 的底层适配进行同步轻量级代码重构。

## 8. 自私审计 (Self-Audit)

- 是否有 ship-blocking 类别未决问题？**无**。本提案通过彻底消灭异常控制流和理清部署边界，消除了一个潜在的 P0 级响应式吞吐衰退风险。

---

## Authority

- CLAUDE.md Rule 33 (Layered 4+1 Discipline)
- CLAUDE.md Rule 34 (Architecture-Graph Truth)
- ADR-0068 (Layered 4+1 Twin Sources of Truth)
