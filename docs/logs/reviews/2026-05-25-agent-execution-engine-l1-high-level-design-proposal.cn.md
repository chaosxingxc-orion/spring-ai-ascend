---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-execution-engine
affects_level: L0, L1
affects_view: [scenarios, logical, process, development, physical]
status: designing
---

# 架构评审提案：agent-execution-engine L1 高级设计提案 (Wave 1.2)

> **日期:** 2026-05-25
> **作者:** LucioIT (核心架构师) & 急急 (智能体)
> **目标 Wave:** W0/W1 (立即执行)
> **关联军规:** Rule G-1.c (L1 深度与落地)

## 1. 背景与原则 (Background & Principles)

### 1.1 顶层设计背景 (L0 架构)

#### 1.1.1 六大核心模块
1. **智能体客户端 (agent-client)**：在 SaaS 应用与桌面应用中被集成，负责感知业务知识与状态，操作业务环境与工具，下发管理智能体配置，调用执行智能体服务。
2. **智能体服务端 (agent-service)**：负责把图模式执行 of workflow 智能体与循环模式执行 of ReAct 智能体封装成微服务。
3. **智能体执行引擎 (agent-execution-engine)**：**（本模块核心定界）** 负责提供两大类智能体的执行器，提供可供开发者使用的各种组件，如 workflow 会用到的 node、ReAct 会用到的 tool 和 hook。
4. **智能体总线 (agent-bus)**：负责连接南北向 of C/S 通信流量，连接东西向 of A2A 通信流量。
5. **智能体中间件 (agent-middleware)**：负责提供智能体需要的基础服务，如记忆服务、技能服务、知识服务、沙箱服务等。
6. **智能体演进平台 (agent-evolve)**：负责在线与离线的智能体自主演进。

#### 1.1.2 两种核心部署/集成模式
- **平台中心模式 (Platform-Centric Mode)**：业务侧仅集成 `agent-client`，其他所有模块均部署在平台端（集中托管与运行，降低业务集成心智负担）。
- **业务中心模式 (Business-Centric Mode)**：业务侧不仅集成 `agent-client`，还会在本地化（业务物理边界内）部署 `agent-service` 和 `agent-execution-engine`，实现就近计算；平台侧仅提供统一治理、互联互通及基础公共服务。

### 1.2 项目阶段背景与演进规划

#### 1.2.1 定位：基于开源基座增补的工具集引擎 (Open-Source Foundation & Core Engine)
根据 **L0 顶层架构定位**，`agent-execution-engine` 本身不参与具体垂直业务智能体的开发，而是作为智能体执行的“通用物理芯片”。
- **基于开源底座快速迭代**：本模块的发展并非闭门造车，而是**基于成熟开源项目 `openJiuwen/agent-core-java` 仓库作为起点与物理底座进行深度改造、增补与增量迭代**。我们将复用其已具备的底层执行器核心功能，并针对平台化需求进行分布式、无状态、响应式及 A2A 协议层面的改造。
- **工具非智能体**：本引擎仅提供基础执行器、通用组件及开发工具，具体的业务逻辑由使用工具的开发者（平台用户）自行构建。

#### 1.2.2 开发者体验 (DX) 演进路线（三阶段跃迁）
为了降低开发门槛、释放生产力，我们为引擎规划了清晰的开发者体验演进 roadmap：
1. **阶段一（当前 Wave 1 聚焦）：配置化开发 (Configuration-Driven Development)**
   - 彻底将智能体逻辑与代码解耦。无论是 Workflow 还是 ReAct 智能体，其核心运行拓扑、工具绑定关系、以及 Hook 配置全部支持**声明式标准 schema（JSON/YAML）定义**。支持配置一键解析、动态加载并在生产环境直接部署。
2. **阶段二：图形化开发 (Graphical Flow Canvas)**
   - 抽象并提供可视化的低代码拖拽画布（Flow Chart），支持开发者直观地对 Node、Tool、Hook 进行拓扑连接，并一键导出标准配置文件。
3. **阶段三（终极杀手锏）：自然语言式开发 (Natural Language-Driven Development)**
   - **对话式构建**：开发者用户在跟“元智能体（Meta Agent）”聊天的过程中，直接讨论和细化业务 SOP。
   - **交互式装配**：类似 openClaw 的交互体验，用户可通过自然语言指令“安装这个工具/注入那个钩子”，元智能体实时感知并翻译为引擎底层配置指令。
   - **自动生成与调试**：系统自动在后台合成、编排并进行运行时调试，最终一键导出符合生产部署要求的标准配置包。

### 1.3 设计原则与核心形态

#### 1.3.1 核心形态：双驱运行内核 (Dual-Engine Architecture)
1. **工作流引擎（Workflow / Graph-Mode Executor）**：
   - **拓扑执行**：支持有向无环（DAG）以及带有复杂环形/条件跳转的复杂图（Graph）拓扑调度。
   - **积木化节点（Standard Nodes）**：将常见计算步骤固化为原子级 Node 组件（如 LLM 推理节点、Prompt 渲染节点、条件决策路由节点、数据收集与映射节点等），开发者只需通过 DSL 或 Java API 编排节点关系。
2. **ReAct 引擎（ReAct / Loop-Mode Executor）**：
   - **循环推进（Reasoning-Action Loop）**：负责维护“思考(Thought) -> 动作(Action) -> 观察(Observation)”的自主推理闭环。
   - **组件级支撑（Tools & Hooks）**：提供标准的 **Tool（物理工具适配）** 与 **Hook（生命周期钩子）**。通过 Tool 屏蔽外部系统的物理调用；通过 Hook 支持在 Loop 运行的前置、中置、后置注入统一的安全审计、风控拦截和日志追踪。

#### 1.3.2 运行双模式：特权开发态与只读生产态原则 (Dual-Mode & Registry Lock)
引擎在运行时有着完全分裂的环境诉求，必须通过严格的单向管道进行隔绝，保证开发态的极致灵活性决不破坏生产环境的安全稳定：
1. **开发模式 (DEV Mode) —— 动态生成、自我创造与高能特权态**：
   - 核心任务是辅助开发与交互式构建。
   - 在此模式下，引擎完全释放“特权”，开启**内存编译器 (In-Memory Compiler)**、开启**组件元数据自省接口**、开启 **Mock 注册表**，支持全量 Trace 追踪以及运行时的断点、调试、拦截和随时热插拔 (Hot-swap)。
2. **标准的智能体交付包 (Standard Agent Package, 简称 .apg)**：
   - DEV 模式 the 唯一物理产出物。它包含声明式的 YAML 配置文件，以及**在开发阶段由于“自我创造”动态编译生成的 Java 字节码文件 (.class)**，整体打包为一个不可变、带签名的 `.apg` 物理交付归档包。
3. **生产模式 (PROD Mode) —— 只读不可变、高性能计算芯片态**：
   - 核心任务是追求极致的性能、安全与隔离。它**只认且仅能加载**由开发态导出的 `.apg` 包。
   - **严格物理安全锁 (Registry Lock)**：PROD 引擎在引导（Boot）加载交付包完成之后，底层必须执行**“注册表物理加锁”**。内存编译器和自省端口彻底关闭或物理切除，配置动态改动入口被物理阻断，拒绝任何运行态代码注入或无授权热更，保证零日漏洞（Zero-Day）级的生产绝对安全。

#### 1.3.3 组件的自进化代码合成原则 (Generative Component Self-Creation)
- **非单纯积木拼装**：若自然语言式开发仅仅局限于已有组件的连线拼装，它与低代码画布相比并没有实质性颠覆体验。
- **全栈代码自我创造**：引擎在 DEV 模式下，支持元智能体根据用户自然语言描述的未知物理逻辑，在线动态编写合法的 `ToolSPI` 类或 `NodeExecutor` 类的 Java 源码（或沙箱脚本文件）。
- **动态瞬时编译**：引擎内置**内存编译器 (In-Memory Compiler)**，在内存中瞬间将其编译为 class 字节码并热挂载（Hot-mount）至当前 JVM，直接在 DEV 环境进行就地沙箱断点调试，调试通过后随交付包一同固化输出为生产包。

#### 1.3.4 完全无状态与配置代码分离原则 (Stateless & Generative Tenets)
1. **完全无状态（Stateless Compute Kernel）**：
   - 引擎内核坚守“纯计算芯片”原则，不直连数据库、不发起直接 A2A 网络寻址。
   - 每次执行均为 pure 状态映射过程。任何 Node/Tool/Hook 组件**严格禁止同步阻塞物理线程或自行调用中间件/I/O 服务**。
   - 一切阻断或外部依赖（如工具调用、审批、A2A协作）通过向 Service 抛出强类型 **`InterruptSignal`（中断信号）** 解决，瞬时释放计算线程，实现极致并发。
2. **配置与代码彻底分离（Configuration & Code Decoupling）**：
   - 智能体逻辑的承载是“配置”而非“编译后代码”。任何 Workflow 的图节点定义和 ReAct 的决策树均可声明化，使得运行拓扑可在运行时被修改、翻译和序列化。
3. **自然语言式开发友好（NL-Dev Friendly Design）**：
   - 引擎底层的积木注册和拓扑生成，必须提供完善的自省自知能力。为“元智能体”在对话过程中动态解析 SOP、合成配置并在运行态进行在线调试提供了坚实的底层技术支撑。

## 2. 场景视图 (Scenarios View)

### 2.1 极速内聚运行场景 (共进程模式)
- **典型链路**：`agent-service` 解析到标准调用请求 -> 装配 `InjectedContext` -> 直接通过 JVM 内存调用本地共进程的 `agent-execution-engine` -> 执行核极速推进 Run 状态并返回 `StateDelta`，实现亚毫秒级的紧凑计算，不发生多进程网络调用。

### 2.2 声明式配置解析与动态热部署场景 (DEV 模式)
- **典型链路**：在开发态，开发者编写或更新智能体的 JSON/YAML 配置文件 -> Service 监听到变更或通过 API 触发 -> 引擎的 `ConfigCompiler` 进行编译与安全 Schema 校验 -> 从 `Component Registry` 中拉取对应的 Node/Tool/Hook 实现类 -> 生成全新的执行 DAG 图/状态机 -> 就地热替换（Hot-swap）原实例，实现运行态零停机更新。

### 2.3 调试态交互式单步调试与状态注入场景 (DEV 模式)
- **典型链路**：开发者在 DEV 模式下启动智能体 -> 引擎启动 Trace 追踪录制 -> 遇到指定 Node/Tool 触发断点挂起 -> 开发者调用调试接口查看当前状态，动态热调 Node 内部 Prompt 属性，或通过 Mock 注册表注入一个外部 API 的 Mock 返回值 -> 调用 `resume` 指令携带注入状态继续向下推进 Run 计算。

### 2.4 智能体组件“自我创造”代码合成场景 (DEV 模式)
- **典型链路**：用户在聊天过程中描述了系统中不存在的复杂业务接口逻辑 -> 元智能体（Meta Agent）理解后，自主合成实现了标准接口 `ToolSPI` 的 Java 源代码 -> 调用引擎的内存编译器 `InMemoryCompiler` 瞬间编译为 class -> 动态热加载并自动追加到智能体的可用工具注册表（Registry）中 -> 立即开始沙箱端到端运行调试，不需要重新打包或重启 JVM。

### 2.5 开发向生产单向构建交付场景 (Pipeline 闭环)
- **典型链路**：开发调试完毕 -> 引擎接收打包指令，启动交付打包器（Packager） -> 将配置 YAML 与 DEV 阶段自主创造生成的自定义编译 class 二进制流共同打包并签名，导出标准的不可变交付包 `.apg` -> 生产（PROD）引擎冷引导（Boot）加载 `.apg` 包 -> 加载结束后执行 `lockRegistry()`，物理切除编译器和自省组件，开启严格沙箱环境运行，保障极致的高性能与系统防线。

## 3. 逻辑视图 (Logical View)

### 3.1 统一 SPI 契约交互层 (Unified SPI Gateway)
- 引擎对外暴露的唯一物理边界。承载并实现底层的 `StatelessEngineExecutor` 契约，负责接收 Service 输入的 `TaskSpec` 与 `InjectedContext`，通过引擎分流机制调度具体执行核，并将执行成果包装为 `StateDelta` 输出。

### 3.2 声明式配置解析与装配编译器 (Config Compiler & Assembler)
- 负责将声明式的 JSON/YAML Schema 描述一键编译并装配为运行态实体的引擎。
- **配置编译器 (Compiler)**：进行标准 Schema 语意与安全校验，将非结构化配置编译为物理 DAG 关系或决策树状态机。
- **动态装配器 (Assembler)**：结合组件注册表，对配置文件中指定的各种 Node、Tool、Hook 实体类进行实例化、依赖注入、以及连线装配。支持 DEV 模式下的运行时热插拔（Hot-swap）替换，以及 PROD 模式下的不可变冻结装配。

### 3.3 双驱物理执行内核 (Dual-Engine Execution Cores)
- 继承自成熟开源项目 `openJiuwen/agent-core-java` 仓库并进行“无状态化”深度改写的核心驱动层。
- **工作流（图）执行内核 (Workflow Core)**：负责 Workflow 智能体在 DAG 图层面的拓扑节点排序、分支路由控制、条件循环与跳转。
- **自适应循环（ReAct）执行内核 (ReAct Core)**：负责 ReAct 自主智能体在 "Thought -> Action -> Observation" 循环中的自主推理状态变迁，包含 LLM 文本与结构化 Tool Call 命令的双向解析。

### 3.4 自进化组件注册表与瞬时编译器 (Generative Registry & In-Memory Compiler)
- 引擎内部的“乐高积木盒子”与元数据目录。
- **只读锁注册表 (Component Registry)**：存储和维护引擎支持的所有可用基础组件类。提供 `lockRegistry()` 物理加锁方法，一旦锁定（PROD 模式下强制），拒绝任何动态注册或修改行为。
- **内存编译器 (In-Memory Compiler)**：**（DEV 模式专属特权）** 集成 JavaCompiler API，支持将元智能体动态合成的 Java/Script 源码瞬间在内存编译为不可变的 Class 二进制流，并热加载载入注册表。
- **组件自省服务 (Introspection)**：**（DEV 模式专属端点）** 提供公共元数据查询接口，向元智能体或可视化拖拽画布暴露当前组件库中所有可用积木组件的描述（Description）、属性、以及输入参数的 JSON Schema。

### 3.5 调试监视器与 Mock 注册表 (Debugger Monitor & Mock Registry)
- **（DEV 模式专属特权模块）** 用于打通开发态的极致观测性与可控性。
- **调试监视器 (Debugger Monitor)**：录制并输出执行全生命周期内的 Trace 树，支持设置节点/工具级断点，拦截并拦截控制权。
- **Mock 注册表 (Mock Registry)**：允许开发者或元智能体针对某些耗时长、开销大、或难以在本地复现的物理工具（如外部扣款接口）动态设置 Mock 返回值，由引擎核心在 DEV 执行时自动进行就地注入，实现零依赖的本地安全调试。

### 3.6 异构配置迁移编译器 (Heterogeneous Config Transpiler)
- 用于降低迁移心智负担的非运行态编译工具。负责接收 LangChain、LlamaIndex 等第三方异构框架的 Agent 拓扑配置文件，静态翻译、映射并输出为本引擎标准的声明式 Schema 配置。

## 4. 进程视图 (Process View)

### 4.1 声明式配置解析与动态热加载时序
1. **配置检测**：Service 监听到智能体配置发生变更（仅限 DEV 环境），调用引擎编译器接口。
2. **Schema 校验**：`ConfigCompiler` 拦截配置，进行 Schema 合法性与安全性校验。
3. **图装配**：`ConfigAssembler` 解析节点与连线，去 `ComponentRegistry` 中查找对应的 Node, Tool 和 Hook 物理类并反射实例化。
4. **内存热换 (Hot-swap)**：引擎以原子操作将该 Agent 对应的内存执行图引用指向全新的装配实体，后续该 Agent 的所有计算请求直接由新图驱动。

### 4.2 调试态交互式单步调试与状态注入时序
1. **启动录制**：以 DEV 模式启动，`DebuggerMonitor` 激活，对每次 execute 请求生成专有 TraceID，并对指定工具设置 Breakpoint 断点。
2. **遇到阻断/断点**：执行核运行至含有断点的组件时。
3. **挂起上报**：组件不进行阻塞，由调试监视器拦截状态并封装为 `StateDelta`，标明 `breakpoint_halt` 及当前运行栈，向上抛回 Service 层并释放线程。
4. **Mock 与变量注入**：开发者/元智能体调用 `MockRegistry` 为该断点拦截点注入 Mock 工具返回值。
5. **恢复计算**：再次发起执行请求，控制参数携带 `resume: true`。调试监视器提取 Mock 值或注入的会话变量喂入引擎，物理执行核从断点位置恢复状态机推进。

### 4.3 引擎中断挂起与状态脱水时序
1. **运行触界**：双驱执行内核在推进单步 Run 过程中（如 ReAct 推理出需要调用 Google 搜索物理工具，或 Workflow 走到人工审批 Node）。
2. **抛出信号**：目标物理组件（如 Tool）遵守开发原则，不进行任何同步阻塞与中间件交互，瞬间向执行内核抛出强类型 `InterruptSignal(TOOL_EXECUTION, GoogleSearch)` 中断。
3. **内核挂起**：执行内核捕获中断，立即冻结运行状态、中断单步计算，输出最新的状态快照及信号包并封装为 `StateDelta`。
4. **线程解耦**：SPI 契约接口将 `StateDelta` 返回给 `agent-service`。Service 进行状态序列化并异步脱水，执行引擎物理计算线程立刻彻底释放并归还至 JVM 线程池。

### 4.4 智能体组件“自我创造”动态编译挂载时序
1. **感知缺失**：用户发出超出预设工具库范围的物理逻辑意图 -> 元智能体生成对应的 Java 类源代码（实现 `ToolSPI`）。
2. **源码编译**：元智能体将源码投递给 `InMemoryCompiler` 接口。
3. **瞬间出 class**：内存编译器调用 JavaCompiler，在内存中瞬间将其转换为 `byte[]` 字节码。
4. **热注册挂载**：通过 `Dynamic ClassLoader` 载入，直接注入 `ComponentRegistry`。
5. **沙箱演练**：通过 ConfigCompiler 的 `hotSwapAgent`，将该工具绑定至测试智能体，在本地开发沙箱中就地驱动运行。

### 4.5 智能体从“自我创造、打包交付到固化部署”全生命周期时序
```text
[开发模式 (DEV Mode)]                       [交付物 (.apg)]              [生产模式 (PROD Mode)]
 元智能体       InMemoryCompiler                 Packager                   ProdEngine      ComponentRegistry
   │               │                                │                           │                  │
   ├── 1. 自主代码合成 ─────────────────────────────>│                           │                  │
   │   (ToolSPI.java 源码)                          │                           │                  │
   │               │                                │                           │                  │
   ├── 2. 内存编译 ────────────────────────────────>│                           │                  │
   │   (JavaCompiler API 编译生成 .class)           │                           │                  │
   │               │                                │                           │                  │
   ├── 3. 热插拔调试(通过) ──────────────────────────>│                           │                  │
   │               │                                │                           │                  │
   ├── 4. 触发打包 ────────────────────────────────>│                           │                  │
   │                                                ├── 5. 组装并加密签名 ───────>│                  │
   │                                                │   (YAML + .class)         │                  │
   │                                                │                           ├── 6. 引导加载 ──>│
   │                                                │                           │   (.apg 包解密)  │
   │                                                │                           │                  ├── 7. 物理上锁
   │                                                │                           │                  │  (lockRegistry)
   │                                                │                           │                  │  注册表只读，
   │                                                │                           │                  │  切除编译器
```

## 5. 开发视图 (Development View)

### 5.1 依赖开源与自研边界定界
本模块采用**“继承开源，专注增量，改造内核”**的原则，与 `openJiuwen/agent-core-java` 仓库保持明确的分工边界：
- **复用开源基座**：直接继承其已被验证过的底层 DAG 拓扑图排序算法、ReAct 基础推理循环逻辑。
- **自研与深度改造部分（100% 自研/重写）**：
  - **无状态化改造**：彻底重构其执行核堆栈，剔除所有同步等待与物理 I/O，全面重构为“中断信号（InterruptSignal）- 状态脱水”的纯计算运行态。
  - **双运行模式分流（DEV/PROD）**：重写装配编译器，在 PROD 模式下实施严格的只读锁限制与特权切除。
  - **开发调试套件 (Debugger & Mock Suite)**：研发调试监视器与 Mock 注册表，打通状态注入和工具 Mock 能力。
  - **自进化内存编译器（InMemoryCompiler）**：研发基于 `javax.tools` 的瞬时代码生成、在线编译与热挂载核心包。
  - **交付打包器 (Packager)**：研发标准的不可变 `.apg` 物理压缩与签名打包工具。

### 5.2 自研代码包目录映射与依赖集成
```text
agent-execution-engine/src/main/java/com/huawei/ascend/agent/engine/
├── spi/                        # 统一 SPI 契约边界
│   └── StatelessEngineExecutor.java # 承载 Service 与 Engine 唯一的无状态交互接口
├── compiler/                   # 声明式配置编译器与交付包
│   ├── ConfigCompiler.java     # 配置文件解析与安全/格式校验
│   ├── ConfigAssembler.java    # 节点、工具、拦截钩子物理组装与内存热替换
│   ├── AgentPackager.java      # 智能体交付物理包 (.apg) 签名、打包与校验器
│   └── schema/                 # 声明式 JSON/YAML 标准 Schema 验证文件
├── core/                       # 双驱无状态物理执行内核 (基于 openJiuwen 深度重构)
│   ├── workflow/               # 改造后的无状态 Workflow/DAG 计算推动器
│   └── react/                  # 改造后的无状态 ReAct 思考循环计算推动器
├── debug/                      # DEV 调试套件 (调试监控与 Mock)
│   ├── DebuggerMonitor.java    # Trace 树录制、单步断点与状态上报
│   └── MockRegistry.java       # 组件/工具 Mock 数据就地注入模块
├── registry/                   # 组件自进化注册表与自省服务 (乐高积木管理)
│   ├── ComponentRegistry.java  # Node、Tool、Hook 物理类只读锁注册表 (带 lockRegistry 物理安全锁)
│   ├── InMemoryCompiler.java   # DEV 态 Java 源码内存瞬时编译器
│   ├── IntrospectionService.java # 组件描述与输入 Schema 元数据查询暴露服务
│   └── loader/                 # 外部 Jar 动态 ClassLoader 热插拔加载器
└── transpiler/                 # 异构配置迁移编译器 (纯静态翻译)
    ├── LangChainTranspiler.java # LangChain 配置翻译器
    └── LlamaIndexTranspiler.java # LlamaIndex 配置翻译器
```

## 6. 物理视图 (Physical View)

### 6.1 共进程内聚部署拓扑 (Embedded Deployment)
- `agent-execution-engine.jar` 作为一个轻量级计算芯片，直接以 Maven 依赖形式打包在 `agent-service.jar` 进程中（如 JVM 同一堆进程）。
- **进程内直连**：配置编译器、组件注册表、以及物理执行核都在同一 JVM 堆中运行。所有的装配、热加载（Hot-swap）、自省服务调用均为毫秒级的直接内存函数调用，极大榨干计算性能。

### 6.2 动态扩展 APG 交付包物理上锁拓扑 (APG Deployment & Lock Down)
- **开发态物理组装**：DEV 下，元智能体编译的代码写入专门的 JVM 内存区。打包导出时，打包器将这些编译好的 class 和拓扑配置序列化为标准的加密物理包 `.apg`。
- **生产态物理只读锁**：PROD 容器启动时，引擎的 APG 解包器在隔离的 ClassLoader 下加载 `.apg` 内的配置与 class。加载完毕后，物理切除 `InMemoryCompiler` 的引用，并执行 `ComponentRegistry.lockRegistry()`，该 JVM 内的所有执行图和支持组件彻底进入只读闭环，杜绝任何外部网络攻击侵入。

## 7. 附录：核心 SPI 接口 (Appendix: Core SPI Interfaces)

### 7.1 StatelessEngineExecutor 引擎唯一核心契约接口定义
```java
package com.huawei.ascend.agent.engine.spi;

import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

/**
 * 智能体执行引擎核心无状态计算接口 (Engine SPI Contract)
 */
public interface StatelessEngineExecutor {
    /**
     * 无状态执行入口：输入任务定义与投影上下文，输出执行 Delta 增量与可能的中断信号
     */
    Mono<StateDelta> execute(TaskSpec task, InjectedContext ctx);
}

public class TaskSpec {
    private String taskId;
    private String agentId;                   // 指向要执行的智能体配置 ID
    private String taskType;                  // WORKFLOW 或是 REACT
    private RunMode runMode;                  // PROD 或者 DEV 模式，控制调试器的唤醒与注册表的上锁
    private Map<String, Object> parameters;   // 启动控制性参数
    // Getters and Setters...
}

public enum RunMode {
    PROD,   // 生产模式：高性能、只读注册表、关闭调试器和编译器
    DEV     // 开发模式：完全特权态、开启调试、开启 Mock、开启源码瞬时编译
}

public class InjectedContext {
    private String sessionId;
    private List<Message> messageHistory;      // 投影装配好的历史交互消息列表
    private List<Map<String, Object>> tools;   // 本轮可用物理工具集定义 (元数据 Schema)
    private Map<String, Object> sessionVars;   // 运行时会话变量快照
    // Getters and Setters...
}

public class StateDelta {
    private List<Message> newMessages;         // 本轮计算产生的新消息
    private Map<String, Object> updatedVars;   // 产生变化的会话变量增量
    private InterruptSignal interruptSignal;   // 触发挂起时携带的强类型控制中断包，完成则为 null
    private boolean isBreakpointHalt;          // 仅在 DEV 模式下，标明当前是否因为调试断点挂起
    // Getters and Setters...
}

public class Message {
    private String messageId;
    private String role;                      // USER, ASSISTANT, SYSTEM
    private String content;                   // 自然语言载荷
    private long timestamp;
    // Getters and Setters...
}
```

### 7.2 强类型中断信号 (InterruptSignal) 基础规范
```java
package com.huawei.ascend.agent.engine.spi;

import java.util.Map;

/**
 * 引擎中断信号类型定义
 */
public enum InterruptType {
    INPUT_REQUIRED,   // 索要用户输入或人工审批
    TOOL_EXECUTION,   // 引擎遭遇物理工具执行（如物理 API 或沙箱代码）
    SUB_TASK_AWAIT    // 引擎在 A2A 协议中拆分出子任务，需要外接协作
}

/**
 * 强类型中断原语信号：任何组件遇阻时必须立即实例化并 Throw
 */
public interface InterruptSignal {
    String getTaskId();
    InterruptType getType();
    Map<String, Object> getPayload(); // 中断所需的强类型参数，例如 ToolName 和入参数据
}
```

### 7.3 调试态 Debugger & Mock SPI 接口定义 (仅 DEV 下可激活)
```java
package com.huawei.ascend.agent.engine.debug;

import java.util.Map;

/**
 * 调试监视器：用于 DEV 下进行 Trace 树录制、断点拦截
 */
public interface DebuggerMonitor {
    /**
     * 设置组件级断点
     */
    void setBreakpoint(String agentId, String componentId);
    
    /**
     * 清除断点
     */
    void clearBreakpoint(String agentId, String componentId);

    /**
     * 获取指定任务在 DEV 运行中的 Trace 详情
     */
    Object getExecutionTrace(String taskId);
}

/**
 * DEV 模式专属 Mock 注册表，用以注入模拟输出
 */
public interface MockRegistry {
    /**
     * 针对某智能体的某工具，注入强类型 Mock 返回值
     */
    void registerMockValue(String agentId, String toolName, Map<String, Object> mockResponse);
    
    /**
     * 提取 Mock 值
     */
    Map<String, Object> getMockValue(String agentId, String toolName);
    
    /**
     * 清除 Mock 规则
     */
    void clearMockRules(String agentId);
}
```

### 7.4 自进化注册表与内存编译器 (Generative Registry & Compiler) 接口定义
```java
package com.huawei.ascend.agent.engine.registry;

import java.util.List;

/**
 * 支持自进化及物理上锁的引擎基础组件注册表
 */
public interface ComponentRegistry {
    /**
     * 动态/静态注册积木类
     * @throws IllegalStateException 若注册表已被锁定(PROD下)，强抛异常
     */
    void registerNode(String nodeType, Class<?> nodeClass) throws IllegalStateException;
    void registerTool(String toolName, Class<?> toolClass) throws IllegalStateException;
    void registerHook(String hookName, Class<?> hookClass) throws IllegalStateException;

    /**
     * 生产模式下的物理安全上锁：一经上锁，注册表终身进入只读只加载状态，拒绝任何动态注册
     */
    void lockRegistry();

    /**
     * 验证是否已上锁
     */
    boolean isLocked();

    /**
     * 自省接口：暴露当前积木盒中注册的所有积木元数据
     */
    List<ComponentMetadata> listAvailableComponents();
}

/**
 * 内存编译器接口：将元智能体生成的源码瞬时转译并热加载 (仅 DEV 下激活引用)
 */
public interface InMemoryCompiler {
    /**
     * 内存瞬间编译 Java 源代码并将其物理载入当前 JVM
     * @param className 目标类全限定名 (如 com.huawei.ascend.agent.tool.QueryTool)
     * @param javaSourceCode Java 源码文本内容
     * @return 编译加载成功的 Class 实体引用
     * @throws CompileException 编译语法或类型检查错误时抛出
     */
    Class<?> compileAndLoad(String className, String javaSourceCode) throws CompileException;
}

public class ComponentMetadata {
    private String name;                       // 组件唯一标识
    private String category;                   // NODE, TOOL, HOOK
    private String description;                // 自然语言功能描述 (供元智能体理解)
    private String inputSchema;                // JSON Schema 格式输入参数约束 (供元智能体进行参数生成校验)
    // Getters and Setters...
}
```

### 7.5 声明式配置编译器与交付包 (ConfigCompiler & APG Packager) 接口定义
```java
package com.huawei.ascend.agent.engine.compiler;

import java.io.File;

/**
 * 声明式配置安全编译与智能体物理打包器
 */
public interface ConfigCompiler {
    /**
     * 编译格式校验
     */
    boolean validateAndCompile(String configContent) throws IllegalArgumentException;

    /**
     * DEV 下的热部署原子替换
     */
    void hotSwapAgent(String agentId, String newConfigContent);
}

/**
 * 智能体交付物打包器：在开发环境（DEV）结束时将配置与生成的 class 压缩为不可变交付物 (.apg)
 */
public interface AgentPackager {
    /**
     * 将 Agent 声明式 YAML 拓扑配置与 DEV 期间生成的 class 字节码打包为 APG 不可变包并进行数字签名
     * @param agentId 智能体 ID
     * @param destApgFile 目标保存的 APG 压缩包路径
     */
    void packageAgent(String agentId, File destApgFile) throws Exception;

    /**
     * 生产环境（PROD）冷启动引导时，对 APG 交付物包进行解密校验、完整性审计、并释放进行初始化引导
     */
    void bootstrapApg(File sourceApgFile) throws Exception;
}
```

## 8. 架构备忘与待完善点批注 (Appendix: Architectural Annotations & Pending Refinements)
在评审本 L1 高阶提案的过程中，架构师团队与智能体共同确立了以下三个关键的架构待完善与攻坚方向。这三个课题将在下一步的 L2 级详细设计与开发阶段进行重点突破与闭环：

### 8.1 待完善点 1：基于已有开源代码仓的 Gap 分析与物理重构设计 (Development View Gap)
* **批注背景**：
  鉴于 `agent-execution-engine` 将基于开源基座 `openJiuwen/agent-core-java` 迭代开发，代码/开发视图不能脱离其既有物理布局。
* **攻坚规划**：
  在 L2 级详细设计中，必须首先对 `openJiuwen/agent-core-java` 的包目录及主执行器（`WorkflowEngine` / `ReActEngine`）进行 Gap 审计。给出具体的“微创手术重构施工图”——即说明如何在保持兼容性的前提下，将其原生的状态化同步阻塞执行机制，通过字节码拦截或重构适配，彻底改写为我们引擎标准的“中断信号（InterruptSignal）- 无状态脱水”模式。

### 8.2 待完善点 2：积木组件分类体系与标准内置组件定义 (Component Taxonomy & Built-in Standards)
* **批注背景**：
  作为配置化开发的基础，若不定义明确的可用组件边界（即开发者的工具箱内有哪些积木），则声明式的配置语法（DSL Schema）无法进行语义定界，后续详细设计也将无法按图索骥。
* **攻坚规划**：
  在接下来的详细设计中，必须正式定义并枚举三类标准的可用组件及其参数约束：
  1. *Workflow 图节点 (NodeExecutor)*：如 `LLMNode`、`PromptTemplateNode`、`ConditionalRouterNode`、`APIConnectorNode` 等。
  2. *ReAct 物理工具 (ToolSPI)*：如 `HttpTool`、`ShellTool`、`WebSearchTool` 等。
  3. *生命周期拦截钩子 (LifecycleHook)*：如 `SecurityGuardHook`（安全审计）、`TokenRateLimiterHook`（预算限流）等。

### 8.3 待完善点 3：声明式智能体配置语法 (DSL) 与 .apg 物理包文件布局规范 (DSL Spec & APG Layout)
* **批注背景**：
  为了实现开发态到生产态的单向不可变流转，我们需要对交付物规格进行严格规范。
* **攻坚规划**：
  L2 阶段需要正式出台两项规格说明书：
  1. *Declarative Agent YAML Spec*：定义智能体 DSL 配置文件的 JSON Schema。
  2. *APG Archive File Layout*：定义 `.apg` 物理压缩包内部的目录规范（如 `/manifest.json`、`/agent.yaml`、`/classes/`、`/assets/`），为 Packager 的打包和 ProdEngine 的只读解密引导提供唯一的物理契约。
