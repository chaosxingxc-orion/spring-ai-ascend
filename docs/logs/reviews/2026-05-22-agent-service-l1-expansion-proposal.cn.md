---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-service
affects_level: L0, L1
affects_view: [scenarios, logical, process, development, physical]
status: proposed
---

# 架构评审提案：agent-service L1 领域扩展 (Wave 1.2)

> **日期:** 2026-05-21
> **作者:** LucioIT (核心架构师) & 急急 (智能体)
> **目标 Wave:** W0/W1 (立即执行)
> **关联军规:** Rule G-1.c (L1 深度与落地), Rule R-G (响应式 I/O), Rule R-M (引擎剥离)

## 1. 背景与原则 (Background & Principles)

### 1.1 顶层设计背景 (L0 架构)
本模块（agent-service）作为整体智能体生态中的核心一环，深度嵌入在 **L0 顶层设计架构**之中。L0 架构整体由 **6 大核心模块** 与 **2 种核心部署/集成模式** 构成：

#### 1.1.1 六大核心模块
1. **智能体客户端 (agent-client)**：在 SaaS 应用与桌面应用中被集成，负责感知业务知识与状态，操作业务环境与工具，下发管理智能体配置，调用执行智能体服务。
2. **智能体服务端 (agent-service)**：**（本模块核心定界）** 负责把图模式执行 of workflow 智能体与循环模式执行 of ReAct 智能体封装成微服务。
3. **智能体执行引擎 (agent-execution-engine)**：负责提供两大类智能体的执行器，提供可供开发者使用的各种组件，如 workflow 会用到的 node、ReAct 会用到的 tool 和 hook。
4. **智能体总线 (agent-bus)**：负责连接南北向 of C/S 通信流量，连接东西向 of A2A 通信流量。
5. **智能体中间件 (agent-middleware)**：负责提供智能体需要的基础服务，如记忆服务、技能服务、知识服务、沙箱服务等。
6. **智能体演进平台 (agent-evolve)**：负责在线与离线的智能体自主演进。

#### 1.1.2 两种核心部署/集成模式
- **平台中心模式 (Platform-Centric Mode)**：业务侧仅集成 `agent-client`，其他所有模块均部署在平台端（集中托管与运行，降低业务集成心智负担）。
- **业务中心模式 (Business-Centric Mode)**：业务侧不仅集成 `agent-client`，还会在本地化（业务物理边界内）部署 `agent-service` 和 `agent-execution-engine`，实现就近计算；平台侧仅提供统一治理、互联互通及基础公共服务。

### 1.2 项目阶段背景与演进规划
为了在项目孵化期平衡“交付速度”与“长远大方向”，本项目确立了明确的阶段性演进和建设重点：

- **聚焦生态与体验，暂缓高并发压力**：当前处于项目早期阶段。在确保大方向和关键架构定界正确的前提下，研发重点应聚焦于**构建良性的智能体生态与极致的开发者/用户体验**，而非在现阶段过度投入解决高并发、海量吞吐等纯系统工程问题（架构设计留出扩展余地，实现上力求轻量高效）。
- **六大模块分步建设**：
  - **核心自研优先（Client/Service/Engine）**：本阶段优先并集中实现 `agent-client`、`agent-service` 和 `agent-execution-engine` 三大核心支柱模块。
  - **成熟开源引入（Bus/Middleware）**：对于 `agent-bus`（总线）与 `agent-middleware`（中间件），本阶段主要引入业界成熟的开源技术栈（如 NATS/RabbitMQ/Redis 存储/向量库等），做轻量适配集成，拒绝闭门造车，全力保障核心链路的交付速度。
- **部署模式演进 roadmap**：
  - **当前阶段**：优先打通并完整实现**平台中心模式 (Platform-Centric Mode)**，快速跑通端到端核心用例，实现业务闭环。
  - **下一阶段**：全面落地支持**业务中心模式 (Business-Centric Mode)**。虽然该模式在本阶段暂不交付，但**当前阶段的 L1 架构与 SPI 接口设计必须前置深度考虑该模式的隔离和多态调用语义**，确保未来切换时业务零改动。

### 1.3 设计原则与核心形态
`agent-service` 在 L1 层的设计中必须严格遵循以下原则，以支撑核心的智能体形态 and 业务演进诉求：

#### 1.3.1 两种智能体形态的封装
1. **工作流智能体 (Workflow Agent)**：封装图模式（Graph）执行的智能体，对应确定性强、有向无环或带有复杂拓扑的分支流程。
2. **ReAct 智能体 (ReAct Agent)**：封装循环模式（Loop）执行的智能体，通过“推理-动作”闭环循环，自主选择并调用工具与钩子，处理非确定性任务。

#### 1.3.2 两种部署形态与集成调用方式（双模态）
1. **共进程函数调用 (Embedded Co-process)**：`agent-service` 与 `agent-execution-engine` 共进程部署（如同一 JVM），采用直接的方法/函数级调用。追求极低的延迟和极致的计算性能。
2. **无状态服务级调用 (Stateless Service-level)**：将智能体作为完全无状态的服务化节点运行在独立的执行引擎中。`agent-service` 作为管控层，通过 RPC、gRPC 或 A2A 总线向执行引擎下发控制指令。

#### 1.3.3 异构智能体兼容设计原则
- **向后兼容与生态解耦 (Heterogeneous Compatibility)**：支持对客户系统内现存、已在运行态的异构/存量智能体进行无缝收口。通过 `agent-service` 的服务级封装和适配器，将老系统中的智能体转化为标准服务形态，实现平滑演进与统一治理。

#### 1.3.4 服务级背压与无状态原则（Reactive & Stateless）
- **接口响应式设计（Reactive API）**：智能体服务端接口全面采用响应式设计。通过背压（Backpressure）机制，向上与总线/客户端形成系统级流量协调，向下保护执行引擎。
- **双模入参流量适配（Pull & Push）**：服务本身除了支持主动从事件总线拉取（Pull）任务外，还需支持外部直接推送（Push）请求（如 HTTP/gRPC 直连），两者在响应式流控中统一适配。
- **基于内部队列的非阻塞解耦（Asynchronous Decoupling）**：服务层内部引入高吞吐的"事件/任务队列"。请求到达后先快速发布（Publish）任务，再由后台线程异步消费（Consume）并派发给执行引擎。**（2026-05-26 rc53-wave-5 按 ADR-0138 §3 红线 c 更新）**：此"内部队列"是规范化 **三轨物理隔离总线**（`docs/governance/bus-channels.yaml`，Rule R-E）在 agent-service 进程内的本地化绑定层 — `control`（高优先级、out-of-band）/ `data`（in-band、重载，16 KiB inline 上限）/ `rhythm`（心跳 / liveness）。Producer / Consumer 按 event intent 路由，**不按存储等级**；持久化是 `bus-channels.yaml` 中每通道独立声明的属性。规范映射见 `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md` §15.1 / §17.3。
- **无状态与缓存/半持久化**（**2026-05-26 rc53-wave-5 按 ADR-0139 窄化 Fast-Path 语义更新**：任何"业务中心模式 / 内存级队列 / 紧凑部署"Fast-Path 理解必须保留 Rule R-G 反应式 I/O + Rule R-H 无 Thread.sleep + Rule R-J.a 含 tenant_id 表 RLS 的不变量；Fast-Path 只窄化**checkpoint/snapshot**轴，不窄化**元数据持久化**轴。每次 Fast-Path 执行 Run + Task 记录均经 RLS 持久化 — 见 `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md` §4.4 + §16.2）：
  - *在业务中心模式下*：内部事件队列可采用高效的**内存级队列**（如 JVM Reactor Sinks），实现高性能紧凑部署。
  - *在平台中心模式下*：为了保障服务层完全无状态（Stateless）、支持极致的水平弹性缩容，内部事件队列与任务状态需接入外部分布式缓存或进行**半持久化处理（Semi-persistence）**。

#### 1.3.5 A2A 多智能体协同与双向对等网络（Agent-to-Agent Network）
- **对等双向调用（A2A P2P Invocations）**：`agent-service` 服务层不再是单向的 API 提供者，它同时具备 **A2A 服务端（A2A Server）**与 **A2A 客户端（A2A Client）**的双重角色。各智能体服务不仅对外提供服务，还必须有能力作为客户端调用其他智能体服务。
- **全场景协同适配**：
  - *空间维度*：支持同节点内（Intra-node）进程级方法互调，亦支持跨节点（Cross-node）跨网络的 A2A 分布式调用。
  - *生命周期维度*：支持实时动态拆分与派生的子智能体（Dynamic Sub-agents）的临时协作与生命周期纳管，也支持与预先存在的、长时生存的独立智能体（Long-lived Agents）之间的平级协作。
- **谷歌 `a2a-java` 协议栈集成**：底层统一引入 A2A 协议标准，通过谷歌官方 Java 实现的 **`a2a-java`** SDK 封装端到端的握手、通道建链、多路由管理及会话关联，确保协议级标准化。

#### 1.3.6 Task-Centric 状态控制与 A2A 中断信号体系
- **任务中心型状态机定位（Task-Centric Model）**：摒弃传统以 Session（会话）为主线的同步阻塞模式，全面重构为以 **A2A 标准任务生命周期状态**为核心的调度体系，支持任务的提交、执行、异步挂起和最终完成。
- **显式无状态中断信号机制（Explicit Interrupt Primitives）**：废除基于传统 Java 异常（Exception）抛出的隐式中断。引擎在遇到各种异步、外部等待时，必须向外抛出显式的、强类型的**中断信号（Interrupt Signals）**，触发 Service 层的脱水存储与上下文持久化，从而在根本上保持执行线程的高并发和非阻塞。

#### 1.3.7 双轨快慢路径调度原则 (Dual-Track Fast/Slow-Path Routing)
- **极简无状态计算内核**：执行引擎（Engine）作为无状态计算芯片，不直接执行任何具象的 I/O 操作。当它需要外界资源或调用中间件时，一律通过向外投递带有强类型控制体的 `InterruptSignal`（对应 L1 层中断原语体系）来宣告挂起。
- **双轨快慢路径分流**：智能体服务端（Service）作为“交通警察”，根据拦截到的 `InterruptSignal` 类型动态进行物理路由：
  - *快路径（Fast-Path）*：针对耗时极短、高频低开销的操作（如本地高能缓存读取、简单数据转换或内存变量计算），直接在 JVM 内存中进行非阻塞级就地挂起与恢复，避免不必要的持久化与分布式网络损耗，榨干单机吞吐极限。
  - *慢路径（Slow-Path）*：针对耗时高、不稳定性强、或需要人机协同的操作（如长耗时推理、外部微服务 API、代码沙箱执行、人工审批），执行标准的分布式序列化并落入 3 级持久化介质，托管至 Temporal 分布式工作流引擎中，彻底释放当前物理计算线程，保证分布式环境下的极致弹性和高可用容灾。

#### 1.3.8 异构引擎影子工具拦截与防腐原则 (Shadow-Plugin Anti-Corruption)
- **异构生态平滑收口**：在面对企业存量的、基于异构框架（如 LangChain、LlamaIndex、Spring AI 等）构建的智能体时，严禁让其直接调用底层的真实微服务和工具，防止 Service 层的微服务治理体系被第三方框架击穿。
- **影子拦截机制（Shadow Tools Interceptor）**：
  - **上下文双向翻译**：通过专用的适配器，在编译/运行边界将平台标准的数据面 `RunContext` 实时翻译为异构引擎原生支持的上下文结构，反之亦然。
  - **影子工具虚拟注册**：在向异构框架注册工具集（Tools/Plugins）时，仅注册空壳的“影子工具”。当异构框架运行时决定调用工具，影子工具会拦截该调用意图、捕获强类型入参，并瞬间抛出带有 `TOOL_EXECUTION` 类型的 `InterruptSignal` 中断信号，迫使整个异构框架当前执行栈立刻挂起退出。
  - **控制权强收拢**：真实的工具与中间件调用、安全沙箱保障以及 A2A 分布式路由，全部收拢在 Service 层执行。完成后，再将结果重新唤醒并注入异构引擎。这保持了异构引擎与中间件之间的绝对架构防腐。

### 1.4 逻辑执行粒度与四层状态生命周期定界
为了精确界定数据沉淀与逻辑运行的边界，本项目确立了由微观到宏观的四层状态与生命周期定界模型：

1. **层级 1：Run（瞬时单步计算）**：最微观的执行粒度。对应有向无环工作流（Workflow）中的单个节点（Node）推进、ReAct 自主代理思考循环中的单次迭代（Loop Turn），或两个物理拦截拦截钩子（Hooks）之间的计算跃迁。属于瞬时态。
2. **层级 2：Task（任务控制生命周期）**：由外部调用端（SaaS Client 或其他协同 A2A 智能体）的消息请求所衍生出的、具有明确业务边界的待处理任务。具有Submitted -> Working -> Suspended -> Completed 的状态控制链路。
3. **层级 3：Session（会话交互数据域）**：管理多轮人机交互或机器对等协作的上下文数据会话容器。在部分简单企业场景下，“任务即会话（Task is Session）”；在长程、多任务协作场景下，一个 Session 周期内可能包含并并发执行多个 Task，负责维护多轮交互的上下文继承关系。
4. **层级 4：Memory（长程知识资产）**：跨越 Session 交互、生命周期等同于智能体物理寿命的长程知识与自我演进认知资产，使智能体具备自我优化提升的数字生命基础。

### 1.5 Service 与 Engine 的核心分工界面原则
基于上述四层模型，智能体服务端（Service）与执行引擎（Engine）在 L1 层确立了极其严格的分工原则：

- **核心定界原则**：**以 Task 级别为核心分工界面。Task 控制权归 Service，Task 内部的 Run 执行归 Engine。**
- **Engine（执行引擎）职责边界：专精于 Run，Task 内部的无状态纯计算芯片**：
  - 聚焦在 Run 级的计算推进 and Task 内部的纯无状态逻辑循环。
  - **核心军规**：Engine 内部禁止包含任何 I/O 动作（严禁直接读写数据库、发起网络 A2A 寻址、调用外部总线/中间件）。Engine 运行所需的全部输入必须由 Service 层在运行前通过 `InjectedContext` 显式单向喂入；在遇到阻断点时，Engine 立即输出 `StateDelta` 及 `YieldSignal` 中断原语，释放物理计算线程。
- **Service（服务端）职责边界：掌控 Task 状态、装配 Session 投影、沉淀 Memory 认知**：
  - **Task 级状态管控**：Service 负责 Task 层的状态机维护、A2A 队列背压流控、以及拦截中断信号（InterruptSignal）执行状态的脱水（Dehydrate）与吸水（Rehydrate）。
  - **Session 级动态装配**：Service 负责管理交互会话生命周期，通过 `ContextProjector` 算法从 Session 海量历史中“语义投影”出最相关的片段，装配成 `InjectedContext` 喂给 Engine，彻底解放引擎的状态管理负担。
  - **Memory 级认知沉淀**：Service 在 Task 完成或挂起后，负责将 `StateDelta` 同步至中间件和演进平台，进行长程记忆的提取、知识沉淀及智能体演进。

### 1.6 Message-Centric 数据域与 Task-Centric 控制域的分离原则
为了支撑智能体对自然语言的灵活理解，同时保障分布式微服务内核的确定性，必须在 Service 接口层与 Engine SPI 契约中确立“编译期与运行期”解耦原则：

- **Message 与 Task 的定义分离**：
  - **Message（消息载荷）**：属于 **数据域（Data Plane）**，本质是非结构化/半结构化的自然语言文本（Natural Language Content），代表用户意图或交互事实。
  - **Task（任务控制体）**：属于 **控制域（Control Plane）**，是拥有生命周期 and 确定性参数的强类型结构化执行实体。
- **接口转换契约分离（Metaphor: Natural Language Compiler）**：
  - **入参（Inbound）解耦**：当 Service 接收到自然语言请求（Message）时，不得将 Message 直接投给引擎，而必须将其包装并升格为强类型的 **Task（附带 InjectedContext 装配好的历史消息载荷）** 发送给引擎。
  - **出参（Outbound）解耦**：
    - *自然语言响应*：当引擎执行完毕（Completed）输出响应消息时，通过 `StateDelta` 中的 `newMessages` 数据域包装自然语言，传递给 Service。
    - *控制逻辑交互（如 A2A 拆分）*：当引擎推理出需要协作或挂起（Yield）时，**禁止通过自然语言文本向服务层表达其调用意图**（极大降低了脆弱性）。引擎必须通过 `StateDelta` 显式返回强类型的 **Task 中断原语（如 SubTaskAwaitSignal 强类型控制体）**，把 A2A 寻址、子任务路由、建链等底层网络动作完全交给 Service（即智能体操作系统 OS Kernel）去物理执行。

## 2. 场景视图 (Scenarios View)
本设计方案覆盖的核心业务运作场景如下：

### 2.1 高性能内聚运行场景 (共进程模式)
- **典型链路**：业务侧触发指令 -> 本地 `agent-service` 快速加载 -> 通过内存/函数级调用直接驱动共进程的 `agent-execution-engine` 执行计算 -> 内存传递 Delta 结果并落盘。
- **适用场景**：对响应时间极其敏感（如高频交互、本地 SaaS 辅助）且资源开销高度紧凑的边缘计算或业务中心模式。

### 2.2 异构存量智能体兼容集成场景 (服务化模式)
- **典型链路**：业务侧下发复杂决策任务 -> `agent-service` 判断当前智能体为存量 or 异构运行态实例 -> 派发器（Dispatcher）切换到服务化模式 -> 通过 A2A 总线或 RPC 调用客户自建的、异构运行的外部引擎实例 -> 接收执行状态、返回控制流。
- **适用场景**：企业级混部场景。客户已存在运行中的私有智能体，需要平滑接入统一的平台总线治理框架。

### 2.3 跨节点多智能体 A2A 异步协同场景
- **典型链路**：主智能体 A 执行任务中途遇到瓶颈 -> A 的服务层拉起并调用 A2A 客户端组件 -> 基于 `a2a-java` 发起跨节点远程调用，向智能体 B 的 A2A 服务端投递协作子任务 -> 智能体 A 脱水挂起 -> 智能体 B 计算完毕回调 A 的 A2A 端口 -> 智能体 A 唤醒并复原上下文，继续执行至完结。

### 2.4 高频轻量访问快路径场景 (Fast-Path Loop)
- **典型链路**：业务触发轻量决策 -> Service 驱动 Engine 运行 -> Engine 遭遇高频轻量数据读取（如本地 Redis 获取高频参数），抛出 `InterruptSignal(TOOL_EXECUTION, RedisGet)` -> Service 快速拦截，就地执行内存/Redis 读取 -> 不进行 Temporal 序列化与落库，在当前物理线程中即刻 `resume` 注入数据并唤醒 -> Engine 继续下一步状态转移。
- **适用场景**：对响应延迟极高要求的短程、低延时智能体辅助计算。

### 2.5 异构引擎影子工具挂起与异步执行场景 (Shadow-Plugin Intercept Loop)
- **典型链路**：运行一个存量的 LangChain 智能体 -> Service 启动执行 -> 适配器将上下文翻译并启动 LangChain -> 引擎中途决策需要调用 SQL 执行工具 -> 触发适配器预先向 LangChain 动态注册的“影子 SQL 工具” -> 影子工具瞬间抛出强类型 `InterruptSignal`，强行阻断 LangChain 运行堆栈 -> Service 接管控制，将 SQL 发送至微服务安全沙箱执行 -> 执行完毕 -> Service 在可用节点反序列化 LangChain 状态并注入结果，恢复后续计算。
- **适用场景**：企业遗留异构引擎治理，以及具有高隔离、强风控审计要求的物理工具调用场景。

## 3. 逻辑视图 (Logical View)
实现双模态调用的核心逻辑组件设计：

### 3.1 多态派发器 (Polymorphic Dispatcher)
- 智能体调用的统一物理入口。它根据注册表配置，判断当前被调用的智能体类型 and 运行环境。
- 提供本地分支（`LocalDirectExecutor`）和服务化远程分支（`RemoteServiceExecutor`）的两路多态派发，向北向调用方屏蔽底层的部署差异。
- **集成 A2A 路由**：当识别到目标调用地址为异构/跨节点的其他智能体时，自动转由内置 of A2A 客户端管道进行协议封包与跨节点派发。

### 3.2 引擎适配器 (Engine Adapter)
- 屏蔽 Workflow（图）与 ReAct（循环）引擎的具体执行语义，抽象出统一的无状态计算接口。
- 本地共进程运行时，直接代理 `agent-execution-engine` SDK；在服务化部署时，则封装 A2A 协议客户端与 RPC 调用代理。
- **异构防腐隔离子系统 (Heterogeneous Anti-Corruption Subsystem)**：
  - *异构框架适配器 (Framework Adapter)*：负责桥接外部存量框架（如 LangChain、LlamaIndex）的生命周期与初始化。
  - *影子工具拦截器 (Shadow Tool Interceptor)*：虚拟注册层，动态向异构引擎注册空壳影子工具，拦截并捕获工具调用细节并直接抛出标准 `InterruptSignal` 中断信号。
  - *上下文双向翻译器 (Context Translator)*：提供高保真的上下文翻译，将标准平台 `InjectedContext` 实时映射为异构引擎的原生数据模型，反之亦然。

### 3.3 内部事件队列（Internal Event Queue）
- 位于微服务边界内的缓冲区，解耦了网络 I/O 线程与 CPU 密集型的 LLM 推理/执行引擎计算线程。
- **多态存储底层实现（Polymorphic Queue Storage）**：
  - *内存级事件队列（Memory-based Queue）*：服务内基于 Project Reactor Sinks / Disruptor 构建，直接打通内存级订阅消费。
  - *分布式缓存/半持久化队列（Semi-persistent Queue）*：对接 Redis List 或外部轻量级 Task Store，存储当前挂起和执行中的 Task 状态，确保在平台中心模式下的多实例水平伸缩和节点漂移时，任务状态不丢失、计算不中断。

### 3.4 A2A 协议收发引擎组件（A2A Connector）
- 引入谷歌 **`a2a-java`** SDK，在服务层内部集成对等的 Client/Server 组件：
  - **A2A Server 端接口**：监听并在 `api/` 北向层统一收口，负责接收来自其他智能体（跨节点或进程内）发起的对等 A2A 协作请求。
  - **A2A Client 客户端接口**：提供统一的 outbound 路由套接字，供执行引擎或编排器向远端智能体投递协作包（Envelopes）。

### 3.5 Task-Centric 状态控制体系与信号派发组件
- **A2A 状态控制组件**：严格依照 A2A 协议规范，追踪与维护任务的五个核心状态变迁，对外暴露统一的监控和主动中止/重试接口。
- **中断信号拦截器**：拦截来自执行层抛出的 `InterruptSignal`，智能识别中断子类型（如等待输入、等待工具、等待协同、安全风控等），并多态化派发至特定的生命周期管理器。
- **双轨调度路由器 (Dual-Track Router)**：依据拦截器捕获到的 `InterruptSignal`，判断当前挂起动作是否满足轻量低延时特征，从而动态路由至极速的 Fast-Path（内存挂起与就地唤醒）或高可用的 Slow-Path（基于 3 级持久化 SPI 的分布式状态脱水编排与线程自杀）。

### 3.6 逻辑分工边界映射组件（Logical Boundary Mapping）
四层状态生命周期的逻辑分工与组件物理归属关系定义如下：
* **智能体服务端（agent-service）管控层**：
  - **Memory 层**：对接外部 `agent-middleware`（知识、记忆服务）与 `agent-evolve`。
  - **Session 层**：完全由 Service 的 `SessionManager` 及 `ContextProjector` 组件托管，负责对 Session 进行语义投影。
  - **Task 层控制面**：由 `TaskCenter`、`PolymorphicDispatcher` 以及 A2A 状态控制组件负责控制流编排、任务排队与脱水存储。
* **智能体执行引擎（agent-execution-engine）执行层**：
  - **Task 层执行面（引擎核心 SPI 契约）**：Engine 接受结构化的 `TaskSpec` 与 `InjectedContext` 载荷，成为 Task 的具体算法无状态执行芯片（对 Engine 屏蔽自然语言 Message 级的 Session 数据）。
  - **Run 层**：完全属于 Engine 内部实现，驱动 Workflow 节点转换（Node Run）与 ReAct 思考循环（Loop Run）。

## 4. 进程视图 (Process View)
聚焦于任务的状态流转与非阻塞响应式背压流控：

### 4.1 异步任务发布/消费环路 (Asynchronous Task Loop)
1. **任务发布（Task Intake）**：
   - 接收到 Push 接口调用（如 REST / gRPC）或从总线主动 Pull 到事件请求。
   - `ReactiveOrchestrator`（响应式协调器）将请求快速解析为标准 `Task`，向内部队列成功发布该事件，并立即向调用方返回包含 `TaskID` 的受理状态回执，保持物理连接非阻塞。
2. **任务派发与背压（Backpressured Dispatch）**：
   - 后台响应式消费线程组（基于 Reactor Sub）根据背压反馈 `request(N)`，按需拉取待处理任务，并调用 `Engine Adapter` 开始执行。
3. **计算与脱水存储（Execution & State Dehydration）**：
   - 引擎返回 `StateDelta` 与 `Yield`（挂起）信号。
   - *平台中心模式*：服务层自动将 `StateDelta` 及执行进度脱水，同步存储至共享缓存/轻量数据库，随后当前服务节点即可释放物理计算线程，维持完全无状态特征。
   - *业务中心模式*：直接在 JVM 进程内存或本地轻量存储中完成状态更新。

### 4.2 跨节点多智能体协作与中断唤醒链路 (A2A Collaboration Loop)
1. **协作请求外发（Spawn/Call）**：
   - 智能体 A 在执行逻辑中发起子智能体拆分，或发起与其他智能体协作的请求。
   - 适配器拦截指令，触发 A2A 客户端使用 `a2a-java` 向智能体 B 的 A2A 服务端口投递异步请求包。
2. **脱水等待（Dehydrated Suspend）**：
   - 智能体 A 在 `agent-service` 中产生标准的 `Yield` 信号。
   - 协调器对 A 当前的运行态、上下文 and Session 数据进行物理脱水，落盘至 Task Store。
   - A 所在的计算进程与线程立即释放归还线程池。
3. **异步唤醒（Rehydrated Resume）**：
   - 智能体 B 计算完毕后，调用 A2A 客户端向智能体 A 回传响应包。
   - 智能体 A 的 A2A 接收服务捕获回调，协调器依据包内的 `TaskID` 在 Task Store 中对智能体 A 重新吸水（Rehydrate）复原上下文。
   - 将任务重新丢入内部事件队列排队，拉起执行线程继续执行。

### 4.3 4级状态全生命周期流转环流（Four-Layer Life Cycle Flow）
1. **任务承接（Memory -> Session -> Task）**：
   - 任务通过 A2A 或 Client 传入。Service 在 Task Store 中注册 Task，并调取 `SessionManager` 创建/关联 Session。
2. **装配投影（Session -> Task Context）**：
   - 在将任务抛给引擎前，Service 的 `ContextProjector` 检索 Session 的多轮历史及 Memory 长程记忆中投影出语义片段，装配成 `InjectedContext` 载荷喂入引擎。
3. **引擎单步循环（Task Context -> Runs Loop）**：
   - 执行引擎（Engine）加载 Context，成为无状态的计算载体。在内部连续驱动多个 **Run**（单步推进）直至任务计算结束或触发 A2A `Yield` 中断。
4. **状态刷新与记忆沉淀（Run Delta -> Session -> Memory）**：
   - 计算完成输出 `StateDelta`，Service 拦截数据写回并刷新 Session，同时后台触发异步任务，通过 `agent-evolve` and `agent-middleware` 将该轮决策提炼沉淀为长程 Memory，使智能体认知跨会话持续自我提升。

### 4.4 双轨快慢路径调度时序流程 (Fast-Path & Slow-Path Dispatch Loop)
1. **中断捕获**：Engine 执行遭遇外部资源依赖，抛出中断信号 `InterruptSignal`，中断当前单步 Run 的推进。
2. **信号分流路由**：`Dual-Track Router` 拦截到该中断信号并评估：
   - **走 Fast-Path（快路径 - 内存直通）**：
     - 当前 JVM 物理工作线程不释放。
     - 路由器就地调用对应的本地轻量执行器执行操作（如本地缓存）。
     - 调用完毕后，在当前物理线程中即刻重新唤醒引擎，实现亚毫秒级的紧凑计算推进。
   - **走 Slow-Path（慢路径 - 分布式容灾）**：
     - 路由器调用脱水服务，触发 3 级持久化 SPI，将 Agent 内存状态及上下文进行全量序列化并落入 Postgres。
     - 任务生命周期托管给 Temporal 编排引擎。
     - 本地 JVM 执行线程立刻销毁并释放归还线程池。
     - 中间件异步计算或人工审批就绪，Temporal 接收回调事件。
     - Temporal 反序列化恢复 Run 的上下文并重新路由到存活节点，执行 `resume` 恢复计算。

### 4.5 异构框架影子工具拦截与恢复流程 (Heterogeneous Framework Shadow Interceptor Flow)
1. **装配与环境拉起**：Service 启动任务调度，触发 `Engine Adapter`。
2. **上下文高保真翻译**：`Context Translator` 拦截 `InjectedContext`，转换翻译为异构框架（如 LangChain）原生的数据结构。
3. **影子代理注册**：Adapter 动态生成影子工具集（空壳 Proxy Tools）并注入该异构框架，随后拉起其执行堆栈。
4. **影子工具拦截挂起**：异构框架决策调用工具 -> 命中影子工具 -> 影子工具获取入参，捕获调用行为 -> 影子工具瞬间抛出强类型 `InterruptSignal(TOOL_EXECUTION)`，强行强退异构引擎堆栈。
5. **外置业务调度**：Service 捕获该中断信号，将真实的工具逻辑派发至外部的安全沙箱中运行（根据需要选择走 Fast-Path 或 Slow-Path 存储脱水）。
6. **反序列化恢复**：物理工具执行完毕，数据返回 -> Service 重启 Adapter -> Adapter 重新加载并反序列化异构引擎状态 -> 将工具结果填回槽位并恢复执行流。

## 5. 开发视图 (Development View)
本章节严格遵循军规 **Rule G-1.c（代码包映射约束）**，清晰界定自研代码结构与对外部开源 **`a2a-java`** 协议栈的依赖，秉持“绝不重复造轮子”的敏捷落地原则。

### 5.1 依赖开源与自研边界定界
我们全面整合谷歌官方 **`a2a-java`** SDK。以下核心机制已由开源库完备实现，我们直接复用其内核：
1. **响应式背压流控通道**：使用 `a2a-java` 内置的响应式连接、Reactor Sinks 缓冲区。
2. **双向 C/S 总线底座**：使用其对等的 `A2AClient` 与 `A2AServer` 通信套接字，处理网络级建链、通道加密与多路由。
3. **Task-Centric 状态控制机**：使用 `a2a-java` 的标准任务状态生命周期状态及控制原语。

因此，我们的 **自研攻坚重心** 聚焦在：**本地多态路由派发、会话数据上下文的高性能动态投影算法、微服务北向接入、以及 a2a-java SPI 数据持久化适配**。

### 5.2 自研代码包目录映射与依赖集成
```text
agent-service/src/main/java/com/huawei/ascend/agent/service/
├── api/                        # [自研 + a2a-java 适配] 北向通信网关
│   ├── rest/                   # 业务侧 Push 模式接入端点
│   └── a2a/                    # A2A-Server 服务端，拦截外部 a2a-java 协作包
├── dispatcher/                 # [100% 自研] 本地多态派发逻辑
│   ├── PolymorphicDispatcher.java # 多态路由核心，决策本地 JVM 直连或是 A2A 网络调用
│   └── strategy/               # 静态与动态派发规则决策树
├── orchestrator/               # [a2a-java 深度集成] 核心任务生命周期控制
│   ├── ReactiveOrchestrator.java  # 绑定 a2a-java 调度引擎，驱动 Task 事件流
│   ├── DualTrackRouter.java    # [100% 自研] 双轨快慢路径多态路由器
│   └── handler/                # A2A 中断信号(InterruptSignal)强类型处理拦截器
├── task/                       # [部分自研] 状态与控制数据存储
│   ├── TaskCenter.java         # 任务中心管理入口
│   └── repository/             # [自研实现] 实现 a2a-java 的 TaskStore SPI 绑定 Redis/JPA
├── session/                    # [100% 纯自研] 会话数据域与动态投影 (核心算法创新)
│   ├── SessionManager.java     # 管理多轮对话上下文交互数据生命周期
│   └── projection/             # 核心语义投影算法：提取最相关上下文装配为 InjectedContext
├── engine/                     # [自研适配] 智能体无状态计算适配层
│   ├── workflow/               # 图模式（Workflow）执行引擎 SDK 适配
│   ├── react/                  # 循环模式（ReAct）执行引擎 SDK 适配
│   ├── heterogeneous/          # [100% 自研] 异构框架防腐隔离适配包
│   │   ├── adapter/            # 框架适配器 (LangChainAdapter, LlamaIndexAdapter 等)
│   │   ├── shadow/             # 影子工具拦截核心 (ShadowToolInterceptor)
│   │   └── translation/        # 上下文双向翻译器 (ContextTranslator)
│   └── spi/                    # proposed, not current: StatelessEngineExecutor
└── infrastructure/             # [自研粘合] 中件层适配与自动配置
    ├── config/                 # Spring Boot 与 a2a-java 协议栈 AutoConfiguration 配置包
    └── persistence/            # Redis/NATS 适配器、序列化与网络层连接池管理
```

## 6. 物理视图 (Physical View)
双模态集成在部署上的拓扑映射：

### 6.1 共进程内聚部署拓扑 (Embedded Deployment)
- `agent-service.jar` 与 `agent-execution-engine.jar` 作为一个进程（如一个 Pod 或边缘容器）整体打包，共享同一物理运行空间。内部事件队列和任务控制状态全部托管在 JVM 堆内存中，零网络开销。 A2A 调用在此拓扑下自动降级为高效的内存进程间方法调用。

### 6.2 存量解耦/异构微服务部署拓扑 (Decoupled Service Deployment)
- `agent-service` 作为主管控实例集中部署，通过网络（总线/网关）连接独立的、在边缘或客户内网运行 of `agent-execution-engine` 集群或存量第三方智能体执行实例。
- **多实例无状态模式**：多台 `agent-service` 管控节点共享外部的 Redis 缓存集群和关系/文档数据库（Task Store）。内部事件队列被拉偏至外部中间件实现（或通过 NATS 衔接），节点任意水平伸缩。
- **A2A 对等网络组网**：各 `agent-service` 服务实例对外暴露 A2A Listener 端口，利用 `a2a-java` 在分布式环境中构建对等图谱网络，通过 A2A 总线交换异步协作数据。

### 6.3 双轨路径的物理存储与计算边界 (Dual-Track Physical & Compute Boundaries)
- **Fast-Path 物理边界**：整个计算、信号拦截、轻量级操作以及就地唤醒都在 Runtime 节点的 JVM Heap 本地内存中完成，数据生存周期与当前物理线程绑定，具备极致性能与吞吐。
- **Slow-Path 持久化存储边界**：Agent 的当前执行快照、变量与上下文序列化为标准 JSON 或二进制字节，持久化保存在分布式的 Postgres 存储集群中。工作流的唤醒、编排和容灾逻辑全部交由外置部署的 Temporal 编排引擎保障，实现极强的鲁棒性、抗物理抖动与支持跨生命周期的长耗时等待。

## 7. 附录：核心 SPI 接口 (Appendix: Core SPI Interfaces)

### 7.1 A2A 标准任务生命周期与中断类型定义
```java
package com.huawei.ascend.agent.service.api;

import java.util.Map;

/**
 * A2A 标准任务生命周期状态
 */
public enum TaskState {
    SUBMITTED,   // 任务已提交，进入队列排队
    WORKING,     // 执行引擎加载上下文并开始计算
    SUSPENDED,   // 发生中断挂起，上下文已物理脱水
    COMPLETED,   // 计算成功，输出增量 Delta
    FAILED       // 发生异常，计算中止
}

/**
 * A2A 标准中断原语类型
 */
public enum InterruptType {
    INPUT_REQUIRED,   // 用户交互中断（Human-in-the-Loop 索要输入或审批）
    SUB_TASK_AWAIT,   // 子智能体拆分/外部 A2A 节点协作等待
    TOOL_EXECUTION,   // 引擎需要服务层代理调用某一物理工具
    DELAY_AWAIT,      // 时间窗/定时延时挂起
    POLICY_APPROVAL   // 预算限额、安全或审计等风控审批挂起
}

/**
 * A2A 强类型中断信号定义
 */
public interface InterruptSignal {
    String getTaskId();
    InterruptType getType();
    Map<String, Object> getPayload();
}
```

### 7.2 StatelessEngineExecutor proposed, not current
此接口作为 `agent-service` 驱动 `agent-execution-engine` 的最底层无状态核心契约，任何引擎适配器均需实现并遵循此契约：

```java
package com.huawei.ascend.agent.service.engine.spi;

import com.huawei.ascend.agent.service.api.InterruptSignal;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 智能体执行引擎核心无状态计算接口 (Engine SPI Contract)
 * 被后续 agent-execution-engine 设计所直接引用并实现
 */
public interface StatelessEngineExecutor { // proposed, not current
    /**
     * 无状态执行入口：输入任务定义与投影上下文，输出执行 Delta 增量
     */
    Mono<StateDelta> execute(TaskSpec task, InjectedContext ctx);
}

/**
 * 强类型任务定义定义
 */
public class TaskSpec {
    private String taskId;
    private String taskType;                  // WORKFLOW 或是 REACT
    private Map<String, Object> parameters;   // 控制性参数
    
    // Getters and Setters...
}

/**
 * 投影注入上下文 (100% 显式注入，杜绝引擎自主 I/O)
 */
public class InjectedContext {
    private String sessionId;
    private List<Message> messageHistory;      // 语义投影算法装配好的历史交互消息列表
    private List<Map<String, Object>> tools;   // 本轮可用物理工具集定义 (Tool Definitions)
    private Map<String, Object> sessionVars;   // 本轮会话临时上下文变量 (Session Variables)
    
    // Getters and Setters...
}

/**
 * Message 数据域模型 (标准消息载荷，供 InjectedContext 承载)
 */
public class Message {
    private String messageId;
    private String role;                      // USER, ASSISTANT, SYSTEM
    private String content;                   // 自然语言内容 (Natural Language Content)
    private long timestamp;
    
    // Getters and Setters...
}

/**
 * 引擎执行输出状态增量包
 */
public class StateDelta {
    private List<Message> newMessages;         // 计算产生的新消息载荷 (自然语言返回)
    private Map<String, Object> updatedVars;   // 产生变化的会话变量
    private InterruptSignal interruptSignal;   // 若引擎中断(Yield)，携带强类型控制中断包；若计算完结(Completed)则为 null
    
    // Getters and Setters...
}
```

### 7.3 Dual-Track Router 与快慢路径处理器接口定义
```java
package com.huawei.ascend.agent.service.orchestrator;

import com.huawei.ascend.agent.service.api.InterruptSignal;
import com.huawei.ascend.agent.service.engine.spi.InjectedContext;
import reactor.core.publisher.Mono;

/**
 * 双轨调度路由器，决定挂起后的物理链路 (Fast-Path or Slow-Path)
 */
public interface DualTrackRouter {
    /**
     * 根据中断信号类型进行分流调度
     */
    Mono<Void> route(InterruptSignal signal, InjectedContext currentCtx);
    
    /**
     * 判断当前中断信号是否为轻量同步信号（是否走 Fast-Path）
     */
    boolean isFastPath(InterruptSignal signal);
}

/**
 * 慢路径执行器（分布式、状态脱水持久化）
 */
public interface SlowPathExecutor {
    /**
     * 将运行时上下文进行脱水落库，释放计算线程
     */
    Mono<Void> dehydrateAndStore(String taskId, InjectedContext ctx, InterruptSignal signal);
    
    /**
     * 收到外部/审批结果后重新吸水恢复工作流
     */
    Mono<Void> resumeWorkflow(String taskId, Object result);
}
```

### 7.4 ShadowToolInterceptor 与异构适配器接口定义
```java
package com.huawei.ascend.agent.service.engine.heterogeneous;

import com.huawei.ascend.agent.service.api.InterruptSignal;
import com.huawei.ascend.agent.service.engine.spi.InjectedContext;
import java.util.Map;

/**
 * 异构框架防腐适配器标准接口
 */
public interface HeterogeneousEngineAdapter {
    /**
     * 将平台标准上下文与异构引擎原生状态进行高保真双向转换
     */
    Object translateContext(InjectedContext standardCtx);
    
    /**
     * 恢复异构引擎的执行
     */
    Object resumeWithResult(String taskId, Object originalState, Object toolResult);
}

/**
 * 影子工具拦截器：向异构引擎注册的虚拟代理
 */
public interface ShadowToolInterceptor {
    /**
     * 捕获第三方引擎的物理调用，立刻抛出标准中断信号，强制中止当前执行线程
     * @param toolName 第三方引擎决定调用的工具名
     * @param arguments 第三方引擎推导出的工具入参
     * @throws RuntimeException 中断信号封装体
     */
    void interceptAndYield(String toolName, Map<String, Object> arguments) throws RuntimeException;
}
```
