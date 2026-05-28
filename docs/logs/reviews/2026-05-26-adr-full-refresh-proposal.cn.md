---
affects_level: L0
affects_view: development
proposal_status: draft
authors: ["LucioIT", "Flash"]
related_adrs: []
related_rules: []
affects_artefact: []
---

# ADR全量刷新提案

> **Date:** 2026-05-26
> **Status:** Draft
> **Affects:** L0 / development (CLAUDE.md Rule 33)

## 一、 三线协作分工原则 (Three-Line Collaboration Division of Labor Principles)

在 AI 协同研发时代，软件工程的设计、评审与测试链路被彻底解耦，重构为三条平行的、具有明确契约的专业化工作线。

### 1.1 设计与开发线 (Line 1: Design & Development)
- **核心职责与定位**：
  * **分级设计职责**：确立分层治理的责任主体。L0（系统顶层设计）与 L1（模块高阶设计）属于架构师（Architect）的专属职责；L2（特性详细设计）与具体代码编写（Code）属于开发者（Developer）的职责边界。
  * **设计反馈与单轮响应**：对于门禁流水线反馈的架构提案（Arch Proposal）评审意见，开发线只做一轮的修改与应答，避免无休止的口水战。
  * **代码修复硬约束**：对于具体的代码修复与重构，开发线必须无条件 100% 运行通过全部自动化测试用例、架构一致性校验以及编码规范门禁，属于零妥协的合入硬红线。

### 1.2 评审与审核线 (Line 2: Review & Approval)
- **核心职责与定位**：
  * **设计提案首要评审**：评审与审核线的第一要务是评审 Line 1 提交的设计提案（Design Proposal），确保其设计理念与全局底座契合。
  * **代码合入审查校验**：负责对 Line 1 提交的代码合并请求（PR）执行最终代码合入审核。重点开展代码与设计方案的一致性审计、代码规范性审计，确认全部基础门禁流水线通过后准予合并。
  * **主动冲突仲裁**：在设计提案经历二次评审（Second Pass）仍未通过时，评审线的人类与 AI 角色必须主动介入，进行权威仲裁与冲突裁决。
  * **设计资产沉淀与门禁衍生**：设计提案获得评审线批准合入后，评审线负责将其转化为系统架构的实际新增或变更，并提炼出具备完整可溯源性的架构决策记录（ADR），进一步向下延伸衍生出具体的代码约束规则（Rules），进而构建、生成或配置相应的一致性与规范性门禁流水线。

### 1.3 测试与验证线 (Line 3: Test & Verification)
- **核心职责与定位**：
  * **集成边界与路径覆盖**：明确多模块联合集成后的系统集成测试边界，重点覆盖跨模块、跨边界的复杂集成路径、状态转移与核心控制流。
  * **集成测试用例输出**：作为核心质量资产，测试线必须输出高质量、可复现、具备深度穿透性的集成测试用例（Integration Test Cases）。
  * **测试反馈与运行态兜底**：在输出完备 of 集成测试用例的基础之上，实时向开发线提供多维度的精准测试反馈，从而有效兜底、防范回归缺陷，保障整个系统运行态（Runtime）的高可用与高健壮性。

---

## 二、 项目四类文档机制 (Four Categories of Project Documentation Mechanism)

为了实现从高层架构构想到代码实现的精准映射与全自动拦截闭环，项目建立了包含“设计、决策、条款、脚本”的四类文档机制。

### 2.1 架构设计 (Arch L0/L1/L2)
- **是什么 (What it is)**：
  * **系统与功能的多维结构化蓝图**。根据系统演进的不同抽象阶段，架构设计按 L0、L1、L2 进行层级展开：
    * **L0（系统顶层设计）**：指明系统的核心世界观（如双模运行时）、全局六大系统模块拓扑与宏观设计理念与整体架构原则。
    * **L1（模块高阶设计）**：规范特定垂直模块的外在行为边界、内在逻辑流转、内部组件划分与核心接口契约。
    * **L2（特性详细设计）**：落实到具体业务特性开发时的时序图、控制流与数据流设计。
  * 均遵循 4+1 视图（逻辑、开发、物理、进程 + 场景）设计规范，但在 L2 级别实施**视图轻量化裁减**，要求至少保留“场景视图”与“逻辑/数据流视图”。
- **用来干什么 (What it is used for)**：
  * **上层设计指导下层设计，详细设计指导代码开发**。L0 是 L1 的上下文输入，L1 是 L2 的上下文输入，L2 则是具体编码实现的终极指导。

### 2.2 架构决策记录 (ADR)
- **是什么 (What it is)**：
  * **技术方案选择和演进历程的关键“检查点”**。它是架构师在面临具体工程挑战、进行技术选型和权衡妥协时做出的技术决策记录。
  * **目录隔离与状态定义**：所有 ADR 文件直接在 `docs/adr/` 下进行物理目录隔离：
    * 🟢 `docs/adr/active/`：当前处于激活状态、强行在流水线中执行一致性校验的 ADR 集合。
    * 🟡 `docs/adr/future/`：超前于当前迭代、面向未来设计、防止打破现有迭代边界而处于压栈暂存状态的决策封存区。
    * 🔴 `docs/adr/discard/`：因架构重构、方案演进等原因，已被后续决策明文废弃或否决的历史决策。
  * **多维元数据管理**：ADR 必须进行标签化管理（层级、模块、视图），需要显式声明影响范围，并且必须具有锚定架构蓝图与评审提案的溯源记录。
- **用来干什么 (What it is used for)**：
  * **主要用途是作为设计提案与代码提交的“检查点”**。它在不同的迭代节点上锁定系统演进的规范边界，充当流水线和 AI 审查一致性时的绝对评判尺度。

### 2.3 约束规则 (Rule)
- **是什么 (What it is)**：
  * **明确具体、可自动化消费的行为与设计红线条款**（如 Rule-025, Rule-043 等）。它是具体技术标准在项目工程层面的硬性制度化表达。
- **用来干什么 (What it is used for)**：
  * **主要是为了给三条协作线上的 AI 助手提供行为约束**。它规定了 AI 助手在设计/开发（Line 1）、评审/审核（Line 2）、以及测试/验证（Line 3）阶段“必须做什么、严禁做什么、应该如何做”，以此将复杂的架构治理融入 AI 自身的思维围栏中。

### 2.4 流水门禁 (Gate Spec)
- **是什么 (What it is)**：
  * **自动运行在 CI/CD 流水线中、用以拦截违规提交的确定性断言检验脚本与静态扫描工具集**（如 `check_architecture_sync.sh`）。
  * 它是约束规则（Rule）的最终物理化落锁工具，包含 AST 语法树解析、静态文件扫描以及自动化契约比对等组件。
- **用来干什么 (What it is used for)**：
  * **负责实现 100% 自动化的架构防腐与物理阻断**。在每次代码提交（PR）或设计提案（Proposal）合入时，自动执行并验证其是否违背了约束规则和 active ADR 契约，防止系统发生架构退化。

---

## 三、 ADR审视意见 (ADR Review Comments)

根据上述分工原则与四类文档演进机制，对当前项目架构决策库（ADR）进行全量刷新的审计意见与执行方案如下：

### 3.1 整体意见 (Overall Opinion)
- **物理目录重构与存量平移**：
  * **物理目录硬隔离建立**：在 `docs/adr/` 根目录下，彻底建立 `active/`、`future/` 与 `discard/` 三个物理子目录。
  * **旧 `archive` 平移 `discard`**：将原有 `docs/adr/archive/` 目录下的所有陈旧历史决策、阶段性归档叙事等资产物理平移至 `docs/adr/discard/` 中，原 `archive/` 物理目录予以废除。
  * **释放 `locked` 决策**：将原有 `docs/adr/locked/` 子目录下封存的 11 份奠基性决策文件全部“取出来”，重新放置回外层的 `docs/adr/` 目录下，与现有其余 ADR 合并，统一进行逐条高密度评审。
- **激活决策索引文档规范 (`README.md` 重构)**：
  * 在 `docs/adr/` 目录下重构创建一个全新的 `README.md` 文件（或维护现有文件的升级版），作为激活状态（active）ADR 的全局核心索引表。
  * **信息列精简约束**：该索引表必须摒弃原有的简单表格，每一项均需要体现以下多维结构化特征（**无需包含文档来源一列**），以保持界面绝对高密度与极致清爽：
    1. **标签 (Tags)**：显式标明该 ADR 的层级（L0/L1/L2）、关联模块（具体微服务/组件）、以及所属视图（逻辑、开发、物理、进程、场景），支持多标签检索。
    2. **一句话描述 (One-sentence Description)**：用最精练的自然语言，对该 ADR 规定的核心行为约束或技术标准进行最直观、无歧义的陈述。

### 3.2 逐条意见 (Point-by-Point Opinion)
- **ADR-0001 (Java 21 + Spring Boot 4.0.5 as the runtime baseline)**：
  * **修改决策**：不修改原始 ADR 正文，仅在其元数据中追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0001
    title: "Java 21 + Spring Boot 4.0.5 as the runtime baseline"
    status: active
    scope:
      level: L1
      target: "agent-client/agent-service"
    view: development
    traceability: null
    ```

- **ADR-0002 (Spring AI 2.0.0-M5 as the LLM gateway, not LangChain4j)**：
  * **决策：废弃 (Discard)**
  * **废弃理由**：企业级 LLM Gateway 为外部依赖，agent 项目只集成模型调用的 client，client 选型由具体的智能体异构框架定义，无法固定选型 Spring AI 的 ChatClient。在后续的目录物理重构中，该 ADR 将直接平移迁移至 `docs/adr/discard/` 物理目录下。

- **ADR-0003 (Temporal Java SDK 1.35.0 for durable workflows, not Airflow / Step Functions)**：
  * **修改决策**：不修改原始 ADR 正文，仅在其元数据中追加/修订以下强 Schema 元数据规范，并在工程决策层面追加两项治理规范：
    ```yaml
    id: ADR-0003
    title: "Temporal Java SDK 1.35.0 for durable workflows, not Airflow / Step Functions"
    status: active
    scope:
      level: L1
      target: "agent-service"
    view: development
    traceability: null
    ```
  * **治理规范 1（状态管理对齐）**：Temporal 状态机设计必须对齐 `agent-service` 下发至 `agent-execution-engine`（智能体执行引擎）的 Task 状态进行生命周期统合与精细化管理。
  * **治理规范 2（标识与信号对齐）**：
    1. 强制对齐 Temporal 的 `Workflow ID` 与下发任务的 `Task ID`。
    2. 中断与挂起信号机制必须全面与标准 `A2A` 协议（Agent-to-Agent）的挂起与中断事件原语对齐，以保证跨主体/跨引擎的长周期协同稳定性。

- **ADR-0004 (PostgreSQL 16 with RLS + pgvector, not separate vector DB)**：
  * **决策：移动至未来决策区 (Deferred to Future / `future/`)**
  * **理由**：该决策与底层 `agent-middleware`（智能体中间件）存在深度耦合绑定。为保持现有迭代边界纯粹，决策暂时挂起，在后续物理目录重构时，该 ADR 将直接平移至 `docs/adr/future/` 子目录下进行封存，留待未来迭代再做决策。

- **ADR-0005 (Row-level security with SET LOCAL transaction-scoped GUC, not per-connection reset)**：
  * **修改决策**：不修改原始 ADR 正文，仅在其元数据中追加/修订以下强 Schema 元数据规范，并在架构约束层面进行精炼和去具体化：
    ```yaml
    id: ADR-0005
    title: "Row-level security with SET LOCAL transaction-scoped GUC, not per-connection reset"
    status: active
    scope:
      level: L1
      target: "agent-service"
    view: development
    traceability: null
    ```
  * **修改要点**：
    1. **去实现细节化**：剥离任何具体的物理代码或数据库层面的细节实现约束（如特定 AOP 拦截器命名、类设计 or Trigger 具体定义）。
    2. **硬性架构级约束**：强制规定 `agent-service` 必须且仅能实现**事务生命周期（Transaction-scoped）级别**的多租户物理隔离，以此物理阻断连接池复用时可能存在的租户数据残留与残留越权漏洞。
    3. **安全风险底线**：设计方案必须在任何高并发、重试、或物理连接复用场景下，100% 绝对规避租户间数据越权泄露的风险，并建立Fail-closed（防错闭锁）兜底能力。

- **ADR-0006 (ActionGuard 5-stage chain (cycle-9 truth-cut), not 11-stage)**：
  * **修改决策**：不修改原始 ADR 正文，仅在其元数据中追加/修订以下强 Schema 元数据规范，并在工程决策层面固化拦截分层协作逻辑：
    ```yaml
    id: ADR-0006
    title: "ActionGuard 5-stage chain (cycle-9 truth-cut), not 11-stage"
    status: active
    scope:
      level: L1
      target: "agent-execution-engine/agent-service"
    view: development
    traceability: null
    ```
  * **治理规范 1（拦截机制与分层协作）**：
    1. 拦截动作必须首先由 `agent-execution-engine`（智能体执行引擎）物理触发 and 执行。
    2. 拦截过程中如产生中断信号且需要服务层交互，必须向上抛出（Bubble up）至 `agent-service` 层统一进行业务级协同和状态恢复处理。
  * **治理规范 2（拦截器本质对齐）**：明确安全拦截器（ActionGuard）是通用拦截器设计的一种特例，确认并固化其 Authenticate / Authorize / Bound / Execute / Witness 5阶段原子执行链设计。

- **ADR-0007 (At-least-once outbox in Postgres, not Kafka, for v1)**：
  * **决策：废弃 (Discard)**
  * **废弃理由**：该逻辑不应由数据库直接承接（废除 Postgres 本地 Outbox 轮询方案），应该统一基于 `agent-bus` 模块实现。在系统规模较小时，应直接复用 `agent-service` 中的事件队列、任务管理、A2A 收发逻辑（包括其中的状态持久化逻辑），以保持系统高内聚、轻量化与事件驱动架构的一致性。在后续的目录物理重构中，该 ADR 将直接物理平移迁移至 `docs/adr/discard/` 目录下进行封存。

- **ADR-0008 (OPA sidecar for authorization, not in-process Cedar / custom)**：
  * **决策：移动至未来决策区 (Deferred to Future / `future/`)**
  * **理由**：该 OPA 侧车安全授权方案的业务与技术逻辑完整正确。但由于安全防护（ActionGuard / OPA 授权）相关特性的高阶开发不在当前产品迭代阶段（W1 聚焦于底座及核心任务流打通），故将本决策挂起，在后续物理重构中直接平移至 `docs/adr/future/` 子目录下进行封存，留待后续安全特性演进阶段再行激活。

- **ADR-0009 (HashiCorp Vault (OSS) for secrets, not env vars / K8s Secrets only)**：
  * **决策：移动至未来决策区 (Deferred to Future / `future/`)**
  * **理由**：该 HashiCorp Vault 密钥及凭证管理设计具备完整的金融级合规性。但由于密钥中心运维、Secrets 自动轮转等高阶安全特性建设不在当前的底座打通阶段（W1 产品迭代范围），为保持当前版本的轻量化和极速物理交付，故将本决策挂起，在后续物理重构中直接平移至 `docs/adr/future/` 子目录下进行封存，留待后续安全合规迭代阶段再行决策。

- **ADR-0010 (Keycloak (OSS) as default IdP, but customer can BYO)**：
  * **决策：移动至未来决策区 (Deferred to Future / `future/`)**
  * **修改要点**：
    1. **网关层统一解耦**：将身份认证（Authentication）与租户解析解耦，由最外层边缘网关（Gateway）负责统一完成 Token 签名验证与 JWT 解析，并转化为统一、无状态的 HTTP Header（如 `X-Current-Tenant-Id`）转发至后端。`agent-service` 只消费 Header，不直接依赖或网络调用底层的 Keycloak/JWKS。
    2. **安全延期归位**：遵循 W1 阶段轻量化底座交付原则。当前阶段暂不对接 Keycloak 等物理 IdP。在后续物理目录重构时，本决策挂起，并直接平移至 `docs/adr/future/` 子目录下进行封存，留待后续多租户身份与权限演进阶段再行激活。
    ```yaml
    id: ADR-0010
    title: "Keycloak (OSS) as default IdP, but customer can BYO"
    status: active
    scope:
      level: L1
      target: "agent-service"
    view: development
    traceability: null
    ```

- **ADR-0011 (Spring Cloud Gateway as ingress, not Kong / Traefik)**：
  * **修改决策**：不修改原始 ADR 正文，仅在其元数据中追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0011
    title: "Spring Cloud Gateway as ingress, not Kong / Traefik"
    status: active
    scope:
      level: L1
      target: "agent-service"
    view: ["development", "deployment"]
    traceability: null
    ```

- **ADR-0012 (Maven multi-module, not Gradle)**：
  * **修改决策**：不修改原始 ADR 正文，仅在其元数据中追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0012
    title: "Maven multi-module, not Gradle"
    status: active
    scope:
      level: L0
      target: "/"
    view: development
    traceability: null
    ```

- **ADR-0013 (UUIDv7 for surrogate IDs, not snowflake / sequence)**：
  * **修改决策**：不修改原始 ADR 正文，仅在其元数据中追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0013
    title: "UUIDv7 for surrogate IDs, not snowflake / sequence"
    status: active
    scope:
      level: L1
      target: "agent-service"
    view: development
    traceability: null
    ```

- **ADR-0014 (3-posture model (dev/research/prod), not 5 or 2)**：
  * **修改决策**：对原始 ADR 进行重大方向性修改，由 3 姿态裁剪为 2 姿态（dev/prod）模型，并根据平台工具属性重新对齐其部署与业务世界观。追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0014
    title: "3-posture model (dev/research/prod), not 5 or 2"
    status: active
    scope:
      level: L0
      target: "/"
    view: ["development", "deployment"]
    traceability: null
    ```
  * **修改要点与世界观澄清（平台工具属性对齐）**：
    1. **姿态裁剪（2 姿态设计）**：彻底弃用原来的 3 姿态设计，裁剪并收敛为 **`dev`（开发姿态）** 与 **`prod`（生产姿态）** 2 姿态模型。
    2. **部署对齐硬约束**：明确本项目属于**智能体平台工具属性**的项目。无论是 `dev`（开发姿态）还是 `prod`（生产姿态），在物理部署上**均需要完整、独立的物理部署**，不得有缺失。
    3. **开发姿态（dev）定位澄清**：澄清 `dev`（开发姿态）的最终用户定位——它是提供给**最终智能体开发者（使用本平台进行智能体规则与任务流配置的用户）**使用的低代码、可视化配置与联调调试界面，而非为本智能体平台（spring-ai-ascend）项目自身贡献核心代码的研发人员界面。

- **ADR-0015 (Defer multi-framework dispatch (Python sidecar, LangChain4j) to W4+)**：
  * **决策：废弃 (Discard)**
  * **废弃理由**：由于已废除 Spring AI 作为全局 LLM 唯一网关（ADR-0002 废弃），且确立了“Client 选型由具体异构智能体框架定义和集成”的松耦合设计底座。平台绝不引入复杂的 Java 本地 gRPC 侧车（Python sidecar）与 SPIFFE 强安全管控。异构引擎与多异构框架集成完全通过轻量级的 SPI 接口进行 Java 内插，或通过 A2A 标准总线（`agent-bus`）进行跨进程异步网络对齐。本决策相关的冗余复杂分发方案予以废除，后续重构时直接物理平移至 `docs/adr/discard/` 目录封存。

- **ADR-0016 (A2A Federation — Strategic Deferral to Post-W4)**：
  * **修改决策**：对原始 ADR 执行分层落地重塑。通信协议与核心收发逻辑当前激活（Active），而动态注册发现机制保持战略延期。追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0016
    title: "A2A Federation — Strategic Deferral to Post-W4"
    status: active
    scope:
      level: L0
      target: "agent-service"
    view: development
    traceability: null
    ```
  * **修改要点（P2P 协议分层激活）**：
    1. **通信协议与收发激活（Active）**：明确 A2A 是一个 P2P 类型的协议，主要覆盖范围为 `agent-service`。A2A 核心消息格式、信箱（Mailbox）及与中断挂起（`SuspendSignal` / `ResumeSignal`）相关的 A2A 协议收发契约在 W1 阶段直接**激活（Active）**，作为底座核心功能进行物理开发落地。
    2. **注册与发现延期（Deferred）**：仅将“基于 Nacos 的分布式智能体自动注册、心跳维护与动态能力发现机制（`AgentRegistry` 寻址）”保持战略延期，留待未来多节点协同阶段演进。在当前阶段，`RemoteAgentClient` 采用静态配置或基于配置字典的直连（Direct Endpoint）方式跑通 P2P A2A 物理通信。

- **ADR-0017 (Dev-time Trace Replay Surface — MCP Server, No Admin UI)**：
  * **决策：移动至未来决策区 (Deferred to Future / `future/`)**
  * **理由与世界观对齐**：该决策属于开发体验类的外围调试工具建设。在当前 W1 核心迭代阶段，系统建设的绝对核心是跑通平台底座与关键任务流。为防止非必要的外围组件对核心工期和资源产生分散，现将本决策暂时挂起封存，待后续迭代阶段有明确需求时再行激活。
  * **元数据规范追加**：
    ```yaml
    id: ADR-0017
    title: "Dev-time Trace Replay Surface — MCP Server, No Admin UI"
    status: future
    scope:
      level: L0
      target: "/"
    view: "development"
    traceability: "ARCHITECTURE.md §1"
    ```

- **ADR-0018 (Sandbox Executor SPI for ActionGuard Bound Stage)**：
  * **修改决策**：对原始 ADR 执行核心架构对齐，将其状态设定为 **Active（激活）**，重构其元数据并对齐底层调用链路设计。追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0018
    title: "Sandbox Executor SPI for ActionGuard Bound Stage"
    status: active
    scope:
      level: L1
      target: "agent-execution-engine/agent-service/agent-middleware"
    view: ["development", "process"]
    traceability: "ADR-0006 (ActionGuard 5-stage chain)"
    ```
  * **修改要点 1（调用链与分层协作）**：
    1. **拦截器中断触发**：在拦截器执行过程中，`agent-execution-engine`（智能体执行引擎）拦截器检测到高危或代码执行请求时，触发沙箱执行中断信号，不再在引擎内直接本地调用，而是将中断物理上抛（Bubble up）给 `agent-service`（智能体服务层）进行中心化业务处理。
    2. **服务层挂起与转发**：`agent-service` 解析该中断信号后，执行任务挂起（Suspend Task）逻辑并记录状态，随后将代码/脚本执行请求，异构转发给 L0 核心基础设施层中的 `sandbox`（智能体沙箱中间件）进行安全的多语言隔离执行。
  * **修改要点 2（异步反应式契约）**：
    1. **异步化契约重塑**：将 `SandboxExecutor` 接口契约重塑为**异步反应式设计**（使用 `CompletableFuture<Object>` 或 `Mono<Object>` 作为返回载荷），物理杜绝了长周期沙箱执行（如容器拉起、重度代码解析）可能导致的主工作线程同步阻塞与线程池耗尽隐患，保障系统的高吞吐高容错率。

- **ADR-0019 (SuspendSignal: Checked-Exception Primitive and Sealed SuspendReason Taxonomy)**：
  * **修改决策**：对原始 ADR 执行核心技术方案的颠覆性重构，**坚决反对保留受检异常设计**。状态设定为 **Active（激活）**，必须使用响应式编程，用状态机/密封类来处理中断挂起信号。追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0019
    title: "SuspendSignal: Checked-Exception Primitive and Sealed SuspendReason Taxonomy"
    status: active
    scope:
      level: L0
      target: "agent-execution-engine/agent-service"
    view: ["logical", "development"]
    traceability: "ARCHITECTURE.md §4 #19"
    ```
  * **修改要点 1（中转信号响应式重塑）**：
    1. **彻底摒弃受检异常**：认定将 `SuspendSignal` 作为 Java 受检异常（Checked Exception）抛出是严重阻碍流式/反应式编程的架构反模式。全面重构挂起机制，严禁在执行链中 throw 挂起异常。
    2. **响应式与密封类结合**：必须采用响应式编程模型（Project Reactor / Java Stream），在运行上下文中通过高内聚的**状态机与密封类（Sealed Class/Interface）**，在流式链条的下游或出口端直接捕捉并消费“中断挂起信号”，实现流式契约的优雅解耦。
  * **修改要点 2（生命周期与死锁防范）**：
    1. **参考 A2A 设计**：密封类变体体系的设计需深度参考 A2A（Agent-to-Agent）标准协议设计，确保通信原语与交互事件生命周期的完美互通。
    2. **死锁回收机制**：针对 `AwaitTimer`、`AwaitApproval` 等引起的无休止挂起（Suspension Deadlock）问题，必须引入强制性的资源及任务回收与超时（Reclamation / Timeout）物理机制，定时扫描并清理长期处于挂起僵死状态的任务，释放核心引擎资源。

- **ADR-0020 (RunLifecycle SPI Separation and RunStatus Formal DFA)**：
  * **修改决策**：对原始 ADR 执行统一术语与核心契约重构。状态设定为 **Active（激活）**，对应 **L1 层级**，且管理对象全面对齐 A2A 协议命名。追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0020
    title: "RunLifecycle SPI Separation and RunStatus Formal DFA"
    status: active
    scope:
      level: L1
      target: "agent-service"
    view: ["logical", "development"]
    traceability: "ARCHITECTURE.md §4 #20"
    ```
  * **修改要点 1（术语统一对齐 A2A Task）**：
    1. **Task 全局替代 Run**：为保障业务世界观及数据模型的一致性，在 `agent-service` 中统一术语规范：**全面使用 A2A 协议中的 “Task” 替代 “Run” 作为状态机管理的核心对象**。两者的物理与逻辑概念完全对齐，从根本上解决名词分裂现象。
  * **修改要点 2（状态审计事件驱动化解耦）**：
    1. **流转审计解耦**：状态机本身仅执行对 `tasks` 表的同步更新，状态流转产生的审计足迹（`run_state_change` 或 `task_state_change`）不再采用同步事务强行写 Postgres 表的极重方式。改为采用领域事件异步广播或由事件总线（Event-Driven）异步解耦落盘，极大降低并发数据库锁竞争压力。

- **ADR-0021 (Layered SPI Taxonomy: Cross-Tier Core vs Tier-Specific Adapters)**：
  * **决策：废弃 (Discard)**
  * **废弃理由与架构轻量化重塑**：
    1. **拒绝过度设计**：认定引入复杂的分布式编排框架（如 Temporal）以及繁琐的三层 SPI 分类体系（Layer 1/2/3）属于严重过度设计，增加不必要的交付延迟与架构维护成本。
    2. **服务层边界内聚**：`agent-service`（服务层）的职责边界应全面内聚在宏观的任务控制上。其状态机仅维护和管理 **`TASK`** 的核心状态（包括 `PENDING, RUNNING, SUSPENDED, SUCCEEDED, FAILED, CANCELLED, EXPIRED`），而绝不深入到更细粒度的“图节点状态、子任务控制循环、或者是算子状态”，这些局部状态完全由下发的目标执行体（如专门的 Workflow 智能体、ReAct 智能体）在执行内部自行闭环。
    3. **无状态高可用特性**：服务层抛弃复杂的分布式中继持久化方案，统一通过轻量化的**缓存 (Cache) 与关系型数据库 (Postgres)** 来实现标准的无状态、多实例高可用部署，保持系统极简和高频物理交付能力。

- **ADR-0022 (PayloadCodec SPI and Typed Payload Contract)**：
  * **决策：废弃 (Discard)**
  * **废弃理由与架构轻量化重塑**：
    1. **微观 PayloadCodec 降维废弃**：服务层（agent-service）抛弃微观 `NodeFunction` 算子级别的 Payload 传递，也不再管理 `RawPayload`/`TypedPayload` 运行期包裹类。Task（任务）的入参与出参直接在数据库表字段中定义为标准的 `jsonb`（Postgres 物理特性）或普通 String，统一由成熟且稳定的 Jackson `ObjectMapper` 做序列化和反序列化处理，废除支持多协议（如 Protobuf/Avro）的插件式 `PayloadCodec` SPI 注册中心。
    2. **降级微观 RunEvent 为宏观 TaskEvent**：废除原设计中针对微观节点（Node）生命周期的 `NodeStarted`、`NodeCompleted` 事件定义。服务层的流式推送或领域事件发布仅限宏观的 `Task` 级别事件（包括 `TaskCreated`、`TaskSuspended`、`TaskCompleted`、`TaskFailed`）。对应的事件载荷不再承载复杂的 `EncodedPayload`，直接承载标准的 Task 核心字段，极大简化消费模型。

- **ADR-0023 (Cross-Boundary Context Propagation: Tenant, Trace, MDC, Metric Tags)**：
  * **决策：移动至未来决策区 (Deferred to Future / `future/`)**
  * **理由与生命周期延期**：多租户隔离、跨边界租户传递以及分布式 Tracing 传导属于 W2+ 深度企业级特性的核心。当前 W1 迭代阶段重心在于跑通极简、单租户、无状态高可用的核心底座与核心 Task 任务流。为保持当前版本轻量化和物理交付速度，现将本决策暂时挂起，在后续物理重构中直接平移至 `docs/adr/future/` 子目录下进行封存，留待后续多租户身份与权限演进阶段再行激活。
  * **元数据规范追加**：
    ```yaml
    id: ADR-0023
    title: "Cross-Boundary Context Propagation: Tenant, Trace, MDC, Metric Tags"
    status: future
    scope:
      level: L1
      target: "agent-service"
    view: ["logical", "development"]
    traceability: "ARCHITECTURE.md §4 #3 / #14"
    ```

- **ADR-0024 (Suspension Write Atomicity: Checkpointer and RunRepository Transactional Contract)**：
  * **修改决策**：对原始 ADR 执行统一术语与核心架构契约重构。本决策状态设定为 **Active（激活）**，全面废除 Temporal 级支持，保留数据库级与 Redis 缓存级，并将所有 “Run” 术语全局更名为 “Task”。追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0024
    title: "Suspension Write Atomicity: Checkpointer and RunRepository Transactional Contract"
    status: active
    scope:
      level: L1
      target: "agent-service"
    view: ["logical", "development"]
    traceability: "ARCHITECTURE.md"
    ```
  * **修改要点 1（术语重构与数据库级原子性收敛）**：
    1. **Task 单行更新事务**：在数据库级存储中，Task（任务）的挂起状态（`status = 'SUSPENDED'`）与其最新的入参、出参 and 上下文载荷数据物理存储在同一张 `tasks` 表（通过 `jsonb` 等字段）的**同一行记录**中。每一次状态跃迁与数据持久化仅为 Postgres 的单行 `UPDATE` SQL 操作，天然通过 RDBMS 单行事务机制保证 100% 写入原子性。
  * **修改要点 2（高并发多实例防脏写乐观锁）**：
    1. **引入 version 乐观锁**：在 `tasks` 数据库表中引入版本号字段 `version`。当服务层（多实例部署）对 Task 进行并发更新时，强制通过乐观锁机制执行更新：`WHERE id = :taskId AND version = :fromVersion`。更新失败则抛出 `OptimisticLockException`，实现并发安全的防脏写与防回退保护。
  * **修改要点 3（Redis 级与废除 Temporal 级）**：
    1. **Redis 级与数据库同步**：在引入 Redis 缓存级作为无状态多实例状态快速协调时，必须保证缓存与数据库的任务状态及载荷数据同步原子性。
    2. **废除 Temporal 级**：彻底删除所有有关 Temporal 分布式编排引擎的原子写入与旁路逻辑，降阶系统复杂度，保持底座高内聚。

- **ADR-0025 (Checkpoint Ownership Boundary: Executor Resume Cursors vs Orchestrator Run Row)**：
  * **决策：废弃 (Discard)**
  * **废弃理由**：执行器已经完全从服务层解耦并下放到具体的 Agent 内部（异构智能体端自闭环管理），服务层（agent-service）职责边界收缩，仅负责管理 Task 宏观状态与元数据，不再感知任何微观的执行游标。因此，本决策中关于编排器与微观执行器之间进度 checkpoint 所有权边界和前缀限制的设计逻辑直接废弃。

- **ADR-0027 (Idempotency Scope at W0: Header Validation Only, Dedup Deferred to W1)**：
  * **修改决策**：对原始 ADR 执行核心架构对齐。状态设定为 **Active（激活）**，部分采纳意见 1（智能体服务的幂等性需要在请求粒度和任务粒度双层保持），完全采纳意见 2（废除 Postgres 物理去重表，统一升级为 Redis 超轻量分布式原子去重锁），并重塑多实例无状态去重模型。追加/修订以下强 Schema 元数据规范：
    ```yaml
    id: ADR-0027
    title: "Idempotency Scope at W0: Header Validation Only, Dedup Deferred to W1"
    status: active
    scope:
      level: L1
      target: "agent-service"
    view: ["logical", "development"]
    traceability: "ARCHITECTURE.md §4 #4"
    ```
  * **修改要点 1（请求粒度与任务粒度双层幂等）**：
    1. **请求粒度幂等（HTTP 级别）**：在网络/控制器层面，必须通过 `Idempotency-Key` 拦截器拦截由于前端双击或网络重试产生的重复请求，在极短窗口（如 5s）内执行原子锁拦截，防止重复重入。
    2. **任务粒度幂等（业务级别）**：在底层 `Task` 编排控制层面，必须结合 Task 的核心状态机（DFA）设计业务级幂等保护。例如：如果针对同一个 `Task` 接收到多次并发的 “Resume” 信号，业务逻辑必须能识别其业务幂等性，保证仅首次生效，后续重复状态变迁直接幂等返回，保障任务状态不被并发乱序写死。
  * **修改要点 2（Redis 超轻量分布式去重）**：
    1. **废弃 Postgres 物理去重表**：彻底废止原有规划中的 `idempotency_dedup` 数据库表设计，避免高频对表写入、清除（24h TTL）造成的数据库物理膨胀与锁竞争。
    2. **Redis 锁自愈**：统一升级为基于 **Redis 的原子锁**（使用 `SET key value NX PX 86400`），实现极速、零开销的分布式排重与去重缓存。

- **ADR-0028 (Causal Payload Envelope and Semantic Ontology Tags)**：
  * **决策：移动至未来决策区 (Deferred to Future / `future/`)**
  * **理由与生命周期延期**：虽然服务层与微观执行链已解耦，但执行层在后续演进中（W3+）依然需要解决上下文防投毒、脱敏占位符处理以及大载荷/多模态数据溢出的物理和逻辑存储问题。考虑到当前 W1 核心底座交付阶段不涉及大载荷多模态与高阶上下文投毒防御场景，现将本决策挂起延期，在后续物理重构中平移至 `docs/adr/future/` 子目录下进行封存，待后续安全与多模态特性迭代时再行决策。
  * **元数据规范追加**：
    ```yaml
    id: ADR-0028
    title: "Causal Payload Envelope and Semantic Ontology Tags"
    status: future
    scope:
      level: L1
      target: "agent-service"
    view: ["logical", "development"]
    traceability: "ARCHITECTURE.md"
    ```

- **ADR-0029 (Cognition-Action Separation Principle)**：
  * **决策：废弃 (Discard)**
  * **废弃理由**：本决策中关于“认知”与“行动”的分立描述以及对 Python/Polyglot 脑的反驳，与 L0 整体的模块设计有很大重叠，边界职责定义不清晰。为避免底层设计原则与现有 L0 六大系统组件映射发生语义混淆，现将本决策予以废弃。
