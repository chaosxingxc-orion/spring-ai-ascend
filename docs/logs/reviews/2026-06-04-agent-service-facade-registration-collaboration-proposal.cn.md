# 提案：AgentService 服务化门面 —— 动态注册发现、多 Agent 协同与凭证传播（轻量 · 亲和集成企业既有 K8S）

| 字段 | 值 |
|---|---|
| 类型 | 设计评审提案（Design Review Proposal）—— 待架构师团队评审，**非 ADR、非已批准设计** |
| 日期 | 2026-06-04 |
| 提案人 | chao（与 Claude 协同推演） |
| 状态 | proposal / for-review |
| 主题模块 | `agent-service`（门面）· `agent-runtime`（运行时）· `agent-bus`（总线/状态面）· `agent-client`（SDK） |
| 填补 | **ADR-0159 §Decision.7 预留但未设计的"注册与发现"职责** + 其在多 Agent 协同、凭证传播两个维度上的延展 |
| 直接依据 | ADR-0159（重新分区：runtime DRIVES / service EXPOSES）、ADR-0101（双部署态）、ADR-0100（Run≤Task≤Session≤Memory + Task/Session 解耦）、ADR-0126（Planner SPI）、ADR-0158（中立 EnginePort，§Decision.5 已被 ADR-0159 部分取代） |
| 互锁规则 | R-A/P-A（业务-平台解耦）、R-E/P-E（三轨道隔离）、R-F/P-F（Cursor Flow）、R-I.1/ADR-0089（Edge↔Compute Ingress 路由）、R-K/P-K（Skill 容量矩阵 + 本地准入）、R-J（租户隔离 + 取消再授权）、R-L（沙箱权限收窄） |
| 主部署态 | Mode A 中心化 / Platform-Centric（平台侧有 K8S）；Mode B / On-Site 经 adapter 替换支持 |

---

## 0. 摘要

ADR-0159 把真实的缝划清了：**`agent-runtime` 是 run-owning 运行时（DRIVES），`agent-service` 是企业级服务化门面（EXPOSES）**，并明确把"对 runtime 构建的 Agent 实例做注册与发现"作为 `agent-service` 的未来职责**预留（§Decision.7），不在该 ADR 内设计**。本提案设计这块预留能力，并把它延展到两个必然相邻的维度：**多 Agent 协同**与**凭证/权限传播**。

贯穿全文的一条载重原则：

> **凡企业既有基建（K8S / etcd / Vault / Service Mesh / IdP）已经做好的事，`agent-service` 一律只对接、不重造。它持有的是引用与投影，不是真相源本身。**

由此得到的形态：`agent-service` 在注册上是 **K8S 的轻量观察者**（不自建注册中心/HA 存储），在协同上是 **网格的服务发现 + 路由 + 治理入口**（不进重数据路径），在凭证上是 **导体 + 策略引用解析器**（不持密钥、不铸 token、不跑 IAM）。这让门面真正保持"薄"，同时最大化复用企业 K8S，且不破坏 ADR-0158 的中立 EnginePort 与"位置无关"原则。

本提案请求架构师团队评审 §7（提议的中立 SPI 面）、§9（备选方案权衡）、§11（开放问题），并裁决是否据此立项一个新 ADR。

---

## 1. 背景与问题

### 1.1 ADR-0159 留下的洞

ADR-0159（2026-06-03，accepted）重新分区后：

- `agent-runtime` = 自包含、可独立启动、被开发者集成驱动的**运行时内核**，**拥有** Run 生命周期、session、task-control、tenant 传播、内部事件队列、engine dispatch、执行引擎、planner。
- `agent-service` = **重新奠基的企业级服务化门面**：北向 HTTP edge（`service.platform.*`），以及——**在未来某个独立 ADR 下**——对 runtime 构建的 Agent 实例的注册与发现。
- 依赖方向：`agent-service → agent-runtime → {agent-bus, agent-middleware}`，无环；`agent-service → agent-runtime` 是唯一合法跨模块边。
- 中立 EnginePort 留在 `agent-bus.bus.spi.engine`，且 ADR-0159 §Decision.4 把它从"中立"改为**显式携带 tenant / correlation**（部分取代 ADR-0158 §Decision.5）。

ADR-0159 §Decision.7 原文：注册与发现"DEFERRED to a separate design discussion + ADR；本 ADR 只 RESERVE 这个职责，不声明任何 SPI/契约"。**本提案即那次 design discussion 的产物。**

### 1.2 触发本提案的具体问题

门面被掏空后，一连串定位问题浮现：

1. **门面会不会重新长出一套类 K8S 的东西？** 一旦做"实例自注册 → 中心 → HA 存储 → 心跳 → 超时驱逐"，终点就是一个小号 `etcd + apiserver + kubelet + node-controller`。这与 ADR-0159 "门面要薄"直接冲突。
2. **两类业务智能体如何被统一发现与驱动？**
   - **Type A**：智能体框架**源码以 AgentRuntime 集成模式**接入（自身不带 Runtime，用 Handler 被驱动）。
   - **Type B**：智能体框架**自带 Runtime、以 Docker 方式部署**的智能体服务。
3. **多 Agent 协同**时，AgentService / AgentClient / AgentBus 各自怎么处理？
4. **凭证传播**（模型 API Key、Agent 身份、中间件服务权限）必然把门面拽向重型 secrets broker / IAM —— 怎么保持轻量？
5. 以上全部要满足：**企业一般在既有标准 K8S 上做增量开发与组件增强，我们的 Service 必须能被轻量、亲和地集成进去**，而不是带一套并行控制平面去和企业的打架。

---

## 2. 范围与非目标

### 2.1 范围

- 主战场：**Mode A 中心化部署**（平台侧有 K8S 可用）。
- `agent-service` 门面的：动态注册与状态管理机制、面向多 Agent 协同的职责切分、凭证/权限传播路径。
- 与 `agent-runtime` / `agent-bus` / `agent-client` 的职责边界。

### 2.2 非目标（明确排除）

- **集群外 / 第三方 / 联邦 Agent**：企业级平台的主权前提是"**企业内部所有智能体都在本平台注册和驱动才合法**"，外部 Agent **不考虑**（这砍掉了早期讨论里的 `ExternalAgent` 分支）。
- **Mode B / On-Site 的 Java 实现细节**：本提案只保证机制可经 adapter 替换落到 On-Site；具体实现是后续工作。
- **联邦总线 broker 选型**（Kafka/NATS/…）：沿 ADR-0101 继续 defer。
- **业务/会话状态的持久化设计**：按 ADR-0100 既有 Session/Memory，且按本提案归为 client 侧 Cursor + 服务端 Session，不在此重设计。
- **Run 内核实现**：仍是 design-phase 目标，不在此 stub。

---

## 3. 设计原则

| # | 原则 | 含义 |
|---|---|---|
| P1 | **委托而非重造（Delegate, don't rebuild）** | 注册存储→etcd（经 K8S API）；密钥存储→Vault/K8S Secret；身份→K8S SA/Mesh；铸 token→企业 IdP。门面只持引用与投影。 |
| P2 | **门面不持真相源（Conduit, not store）** | `agent-service` 在注册上持"可重建的派生缓存"，在凭证上持"引用 + 上下文"，绝不持久化真相源。 |
| P3 | **控制流集中、重数据流 P2P** | L0 §7 既定：控制走 Track1 中心总线；agent 间重载荷走 Track2 点对点，**永不过中心 broker**。 |
| P4 | **中立 SPI + 位置无关** | 所有 infra 耦合（K8S/Vault/Mesh/IdP）藏在中立 SPI 的 adapter 后面，`bus.spi.*` 内不出现 infra 类型，保 ADR-0158 中立性与 Mode B 可移植。 |
| P5 | **被集成优先（亲和集成）** | 复用企业既有 K8S 原语与扩展点（标准资源 + 标签注解 + readiness 探针 + admission webhook + service mesh），不引入需要 cluster-admin 的并行控制平面。 |
| P6 | **实例 fungible、状态外置** | 业务/会话状态外置（client cursor + 服务端 Session），实例对业务无粘性，注册表只跟踪 `{存活, 能力}`。 |

---

## 4. 核心设计

### 4.1 统一实例模型 —— Type A 与 Type B 收敛为同一契约

**关键洞察**：从 `agent-service` / `agent-bus` 的视角，Type A 与 Type B **是同一个东西**：

> **一个可达的 EnginePort / A2A 端点 + 一组能力 + 一个存活/容量状态。**

"源码集成（Handler）" 与 "Docker 自带 Runtime" 只是 **ADR-0158 中立 EnginePort 后面的传输实现差异**（`InProcessEnginePort` 同进程 vs 网络化实现），**不是注册模型差异**。

| 维度 | Type A（源码集成 / AgentRuntime Handler） | Type B（Docker 自带 Runtime） |
|---|---|---|
| 接入方式 | 业务把 AgentCore 逻辑实现为 `runtime.dispatch.spi.AgentHandler` + `AgentResultAdapter`，由宿主 AgentRuntime 驱动 | 自带 Runtime 的容器，暴露 A2A / EnginePort 端点 |
| 中心化部署形态 | 一个承载若干 Handler 的 AgentRuntime Pod（K8S Deployment） | 一个智能体服务容器（K8S Deployment + Service） |
| 能力声明 | 启动时上报所承载 Handler 的能力 | `runtime.access.A2aWellKnownAgentCardController` 暴露 `/.well-known/agent.json`（A2A AgentCard） |
| 被驱动 | AgentService 经（网络化）EnginePort 派发 Run，宿主 AgentRuntime 跑 | 同上，经中立 EnginePort / A2A 派发 |

> **设计收益**：一套状态机、一套发现、一套驱动路径，覆盖两类。绝不为两类各开一条注册路径（那会把传输细节泄漏成控制面分叉）。

### 4.2 动态注册与状态管理 —— K8S 即注册中心，门面只持派生缓存

#### 4.2.1 翻转：不自建注册中心

中心化部署下平台已有 K8S，**注册中心、HA 存储已经存在**：

- **注册中心 = K8S API Server**。Type A/B "被调度起来"本身即注册；EndpointSlice 自动可达。无 bespoke 注册端点。
- **持久真相源 = etcd**（Raft 共识、HA、watch 变更流——全部免费）。
- **`agent-service` / `agent-bus` 侧零持久化**：只跑一个 **informer / watch**，把标准资源投影成内存里的**活路由表**；重启即 list+watch 重放，**没有"我的状态要做 HA"这个问题**。
- controller 的 leader 选举 = K8S Lease（免费）。

#### 4.2.2 注册载体：约定优于 CRD（评审已选 A）

采用**标准 K8S 原语 + 标签注解约定**，**零自定义 CRD、零 Operator**：

- 用一个约定标签/注解（例如 `agent.ascend.io/registered=true` + 能力/容量上限注解）从企业标准资源（Deployment/Pod/Service/EndpointSlice）里**挑出"已注册 agent"**。
- 能力靠 **A2A AgentCard** 自描述，由 informer 拉取。
- 这样**不需要 cluster-admin 装 CRD**，和企业既有方案最不冲突，纯观察者姿态。

> 被否的备选见 §9.2（最小 CRD / 完整 CRD+Operator）。

#### 4.2.3 三类信号按变更频率分流（不踩 etcd-当遥测库 的反模式）

| 信号 | 来源（复用） | 性质 |
|---|---|---|
| **存活**（进程活着） | **K8S readiness/liveness 探针**（kubelet 已在探，informer 从 Pod Ready condition 顺手取） | 白来 |
| **能力**（能干什么） | **A2A AgentCard** | 慢变，注册时读 + 变更刷新 |
| **活容量/满载** | **不预先跟踪**（见下） | —— |
| **draining** | informer 看到 Pod 进入 Terminating 即停路由 | 白来 |

#### 4.2.4 反应式路由 —— 活容量不入注册表（评审已选）

注册表只持 `{存活, 能力}`。**活负载不预先跟踪**，而是派发时反应式解决，直接复用 **Rule R-K（本地准入 + `BackpressureSignal`，L0 §7 "admission decisions are local"）**：

- 路由到任一 `Ready ∧ 能力匹配` 实例；
- 实例本地判定超载 → 回 `Rejected / Delayed / Yielded`（背压）；
- `agent-service` 据此**重路由 / 延迟**。

> **结果**：无存储、无调度器、无健康检查器、无容量库——这是**离"类 K8S"最远**的形态，正面回应 §1.2(1) 的担忧。

#### 4.2.5 实例生命周期状态机（供评审）

```
REGISTERING ──→ READY ⇄ AT_CAPACITY ──→ DRAINING ──→ DEREGISTERED
                  │  ▲                                    ▲
                  ▼  │（探针/心跳恢复）                    │（驱逐超时）
              UNREACHABLE ───────────────────────────────┘
```
- `READY` 判定 = `K8S Ready ∧ 能力匹配`（容量在派发时由背压裁决，不作为进 READY 的前置）。
- `AT_CAPACITY` / `DRAINING` 主要由派发时背压 + informer Terminating 体现，而非注册表主动维护的重状态。
- 真相源同住 etcd（infra 存活 = readiness；能力 = AgentCard），所以"两个真相源 split-brain"降级为**普通的 informer 滞后（最终一致）**。

### 4.3 多 Agent 协同 —— 既有原语的组合，而非新子系统

#### 4.3.1 协同所需零件已全部就位

- **嵌套/对等 Run over 中立 EnginePort**：`NestedDualModeIT` 已证明 3 层 `graph→agent-loop→graph` 嵌套可经 `SuspendSignal` 运行。一个 agent 调另一个 = 派发子 Run。
- **Planner SPI + GRAPH 引擎**（ADR-0126）：协同"谁调谁/编排"的逻辑之家。
- **Task ≤ Session 解耦**（ADR-0100）：明确为 **group-chat 多 agent 协同**设计（一个 Session 并发多 Task；一个 Task 跨多 Session）。
- **A2A access**（`A2aJsonRpcController` + AgentCard）：agent 间对等发现与调用。

#### 4.3.2 贯穿原则：控制流集中、重数据流 P2P（P3）

应用到协同：**发现/授权/控制经 AgentService + Track1；agent 间重载荷（LLM 上下文、工具结果、文档）经 Track2 在 Pod 间 P2P，永不过中心 broker**——这是扩展性与 K8S 复用的双重关键。

#### 4.3.3 协同身份 = Session（group-chat 模型）

协同的共享上下文（黑板）= **服务端 Session**（agents 读写处，ADR-0100 ContextProjector SPI 投影）；client 只持自己的 cursor 视图。实例因状态外置而保持 fungible。整组取消/观测以**根 Run / Session**为单位。

#### 4.3.4 编排权放置（评审已选 A + 内嵌 B）

- **(A) 服务端编排（采纳为主）**：client 给高层目标，`agent-runtime` 内一个协调者 agent / GRAPH 分解并经嵌套 Run + A2A P2P 驱动子 agent；client 一个 cursor。命中既有 Planner/GRAPH/Nested 原语，client 与 service 都保持薄。
- **(B) P2P 编舞（作为 A 内部可用能力）**：协调者可放权让子 agent 之间直接 A2A P2P。
- **(C) 客户端编排（不采纳）**：违背薄 client、治理最差。

### 4.4 凭证与权限传播 —— 纯委托（评审已选 A），门面是导体

#### 4.4.1 载重原则（P2）

`agent-service` 在凭证路径上**只传引用与上下文**（tenant、posture、secret-ref、delegation handle），**绝不经手原始密钥**；存储/身份/铸 token/注入全部委托企业基建，且**注入发生在使用点（runtime / middleware pod），不在控制面途中**。

#### 4.4.2 三类凭证的归宿

| 凭证 | 存储（复用） | 身份/铸造（复用） | 传播载体 | AgentService 角色 |
|---|---|---|---|---|
| **模型 API Key** | K8S Secret / Vault（按 tenant） | —— | RunContext 携带 secret-ref + tenant；**密钥由 K8S/Vault 注入到 runtime pod** | 只解析"哪个 ref 适用"，**永不见 key** |
| **Agent 身份（Agent API Key）** | K8S SA / Mesh SPIFFE | **K8S 短时 SA token / Mesh mTLS** | Mesh mTLS + SA token（Pod 间自动） | 在现有 JWT 校验链上**多认一种 workload 身份**，不铸不存 |
| **中间件服务权限** | 企业 IdP 策略 | **OAuth Token-Exchange (RFC 8693) 在使用点换取** | 受限委托 token 走 RunContext / EnginePort 信封；**ActionGuard 执行 per-tenant allowlist** | 传 delegation handle + tenant，**引用策略而非存储策略** |

#### 4.4.3 要点

- **模型 Key 不进控制面**。共享 runtime pod 服务多租户时，按请求里的 tenant 上下文**动态查 Vault 路径**取对应 key（per-request 解析），不塞进 Run 请求。posture 门控复用既有表（dev mock 允许、research/prod 强制 Vault）。
- **Agent 身份复用 K8S workload identity / Mesh mTLS**，agent 间网格内自动 mTLS 互认，无需门面铸/存静态 Agent API Key。
- **中间件权限核心是 confused-deputy**：下游调用须带**原始 tenant/user 的委托权限**，不是 agent 的环境权限。使用点做 token-exchange 换 audience 受限、短时、scope 收窄的 token（**Rule R-L**：权限逐层收窄不放大），**ActionGuard** 在调用点执行 allowlist。

#### 4.4.4 传播载体复用既有 carrier（不新造竖切）

- **编排内** → `RunContext`（tenantId 已这么流，扩展携带 secret-ref / delegation token）。
- **跨网格（多 agent P2P）** → **EnginePort 信封**（ADR-0159 §Decision.4 已使其显式携带 tenant/correlation，委托 token 搭此信封；**密钥永不上信封**）。
- **整组取消时** → 接 **Rule R-J cancel 再授权**（取消 fan-out 时委托 token 一并失效/重核）。

---

## 5. 四组件职责总表

| 组件 | 在本机制中的职责 | 明确不做 |
|---|---|---|
| **agent-service**（门面） | 北向 HTTP edge；**网格的服务发现 + 路由 + 治理/授权入口**；消费 bus 状态面活路由表选址；凭证**导体 + 策略引用解析** | 不持注册真相源、不进重数据路径、不编排协同、不持密钥/铸 token/跑 IAM |
| **agent-runtime**（运行时） | 真正**驱动** Agent；Planner/GRAPH **编排协同**；嵌套/对等 Run；session/task-control；在使用点解析 secret-ref / 换委托 token / 执行 ActionGuard | —— |
| **agent-bus**（总线/状态面） | 承载注册派生视图（中立 SPI 后）；Track1 控制 fan-out；**Track2 P2P inter-agent 载荷（架在企业 K8S 网络/Mesh 上）**；Track3 存活；中立 EnginePort/S2C 传输 | `bus.spi.*` 内不出现 K8S/Vault/Mesh 类型 |
| **agent-client**（SDK） | 提交一个协同目标 → 一个 cursor；消费**带协同结构标注**的事件流；按 Session/Task 关联；对根 cursor 发整组取消 | 不感知 agent 拓扑、不自行编排、不持 (a) 实例运营状态 |

---

## 6. 部署形态

| 形态 | 注册存活源 | inter-agent 传输 | 凭证基建 | 本机制落法 |
|---|---|---|---|---|
| **Mode A 中心化（主）** | K8S readiness informer | 企业 K8S 网络 / Service Mesh（Istio/Linkerd → mTLS/重试/熔断/追踪白来） | 企业 Vault / IdP / Mesh | 全量如 §4 |
| **Mode B / On-Site** | **替换 adapter** → Track-3 心跳当存活源 | bus 传输（可无 Mesh） | K8S Secret 地板 / 嵌入式 | 中立 SPI 不变，仅换 adapter 实现 |

inter-agent **寻址 = K8S Service DNS / headless Service**，不自造寻址。本机制**自研的只有 4 样**：Planner/GRAPH 编排语义、中立 EnginePort/A2A 协议、Session/Task 协同数据模型、Track1 取消 fan-out 语义——**物理网格、寻址、传输安全、密钥存储、身份、铸 token 全部复用企业基建**。

---

## 7. 提议的中立 SPI / 契约面（请重点评审）

均为**中立接口**，infra 耦合落在各自 adapter（不进 `bus.spi.*` 中立包；K8S/Vault/Mesh 类型只在 adapter 模块出现）。**具体模块归属与依赖白名单影响见 §11 开放问题。**

| 提议 SPI | 职责 | Mode A adapter | Mode B / On-Site adapter |
|---|---|---|---|
| `InstanceRegistry`（读） | 按能力查询 `Ready` 实例视图（返回中立 `RegisteredInstance{endpoint, capabilities, state}`） | —— | —— |
| `DiscoveryProvider`（喂） | 向注册视图喂存活/能力事件 | `K8sInformerDiscoveryProvider`（watch 标准资源 + 拉 AgentCard） | `SelfRegistrationDiscoveryProvider`（自注册 + Track3 心跳） |
| `SecretResolver` | 解析 secret-ref；**在使用点**返回/注入，控制面永不见原值 | `VaultSecretResolver` / `K8sSecretResolver` | `K8sSecretResolver`（地板） |
| `IdentityVerifier` | 校验 workload/user 身份（扩展现有 JWT 校验链） | `MeshSpiffeVerifier` / `K8sSaTokenVerifier` | 嵌入式 |
| `PermissionBroker` | token-exchange / 委托收窄（Rule R-L），供 ActionGuard 调用点执行 | `OidcTokenExchangeBroker`（企业 IdP） | 嵌入式 |

> 提议把 `InstanceRegistry` / `DiscoveryProvider` home 在 `agent-bus`（`bus_state` 面），`agent-service` 作为消费者路由、`agent-runtime` 作为 inter-agent 发现的消费者；`SecretResolver` / `IdentityVerifier` / `PermissionBroker` 的 home 待定（§11）。

---

## 8. 治理影响（lockstep 预判）

- **需要一个新 ADR** 来批准本机制（建议标题：*Agent 注册发现、协同与凭证传播门面契约*；`extends` ADR-0159 / ADR-0101 / ADR-0100；`relates_to` ADR-0126 / ADR-0158 / ADR-0089）。本提案是其 design-discussion 前置。
- **互锁规则**（无需新增规则，复用既有）：R-K（反应式路由/背压）、R-J（取消再授权）、R-L（权限收窄）、R-F（Cursor Flow）、R-I.1（Ingress 路由）、R-E（三轨道）、R-A（业务-平台解耦）。
- **新增 SPI 落地时**需走 Rule R-D 全套（`module-metadata.yaml#spi_packages` + `docs/dfx/<module>.yaml` + `docs/contracts/contract-catalog.md`）+ 4-way parity（G-1.1.b）。
- **deployment-loci**：维持 `[platform_centric, business_centric]`，本机制不改变 SSOT，仅在 adapter 层体现差异。
- **主权落地（可选）**："注册即合法"可经一个**轻量 K8S admission webhook**（挂进企业既有准入链）拒绝/标记未按约定注册的 agent 工作负载——是否纳入由架构师裁决（§11-Q5）。
- **设计相位纪律**：本提案不落任何 production Java，不 stub Run 内核；SPI 仅在新 ADR 批准后按相位推进。

---

## 9. 备选方案与权衡

### 9.1 注册：自建注册中心 + 心跳 + HA 存储（**否决**）
终点是小号 `etcd + apiserver + kubelet + node-controller`，直接违反 ADR-0159 "门面要薄" 与 P1/P2。中心化下 etcd 已存在，无理由重造。

### 9.2 注册载体：最小 CRD / 完整 CRD+Operator（**未采纳**）
- 最小 CRD（无 controller）：类型契约更规整，但要装 cluster 级 CRD，部分企业抵触，违背 P5 亲和集成。
- 完整 CRD+Operator：复用最大但要运维一个并行 operator，最违背"轻量被集成"。
- → 采纳 §4.2.2 **约定优于 CRD**。

### 9.3 路由：主动负载感知（**未采纳**）
更聪明的最少负载路由，但要重新引入活负载通道（轮询 metrics 或 Track3 带容量），复杂度回来，且与 R-K "本地准入" 重叠。→ 采纳 §4.2.4 **反应式**。

### 9.4 协同：客户端编排 / 纯 P2P 编舞（**未采纳为默认**）
客户端编排违背薄 client、治理最差；纯 P2P 编舞整组取消/观测难、"一个 cursor"难给。→ 采纳 §4.3.4 **服务端编排为主 + P2P 作内部能力**。

### 9.5 凭证：薄内置兜底（B）（**未采纳**）
为没有 Vault/IdP 的企业内置极薄密钥存储 + token 签发，turnkey 一点，但门面又持上一条密钥/token 路径，违背 P2。→ 采纳 (A) **纯委托**，以 **K8S Secret 为永远可用地板**，Vault/Mesh/IdP 为更富 adapter。

---

## 10. 风险与硬骨头

1. **整组取消的 fan-out 竞态**（最硬）：多 agent 下根 Run 取消向子 Run fan-out，叠加 Rule R-J cancel 再授权，竞态面比单 Run 大。需专门时序设计 + IT（沿用历史 cancel-race 的严格度）。
2. **多租户共享 runtime pod 的 per-request key 解析**：动态查 Vault 的延迟与缓存策略、错配租户的 fail-closed 必须明确（接 posture 门控）。
3. **informer 滞后窗口**：派发到刚 Terminating 的实例 → 由背压 `Rejected: draining` 兜底，需保证重路由幂等。
4. **Service Mesh 依赖的可选性**：有 Mesh 则 mTLS/重试白来；无 Mesh 时 Track2 的传输安全需由 EnginePort 层补齐——adapter 要覆盖两种。
5. **`bus.spi.*` 中立性回归风险**：K8S/Vault 类型若泄进中立包即破 P4；需 ArchUnit 兜底（类比现有 SPI 纯净测试）。

---

## 11. 待架构师裁决的开放问题

- **Q1（SPI 归属）**：`InstanceRegistry`/`DiscoveryProvider` home 在 `agent-bus`（`bus_state`）是否合适？`agent-service` 直接消费 `agent-bus` SPI 是否需要补依赖白名单条目（当前唯一合法跨边是 `agent-service → agent-runtime`）？还是路由视图应经 `agent-runtime` 暴露？
- **Q2（凭证 SPI 归属）**：`SecretResolver`/`IdentityVerifier`/`PermissionBroker` 落在门面侧（platform）还是使用点侧（runtime）？建议"解析在 runtime 使用点、校验在门面入口"，请确认切分。
- **Q3（admission webhook）**：主权"注册即合法"是否以轻量 admission webhook 落地？是否接受其作为门面的可选部署件？
- **Q4（约定 schema）**：注册标签/注解约定（`agent.ascend.io/*`）+ AgentCard 必填字段，是否需要一个 `docs/contracts/*.v1.yaml` 契约固化？
- **Q5（新 ADR 立项）**：是否据本提案立项新 ADR？编号与 `extends/relates_to` 链请指派。

---

## 附录 A：端到端时序（中心化）

**单 Agent 调用**
```
client → IngressGateway(bus) → agent-service(HTTP edge)
  → 查 InstanceRegistry 活路由表（Ready ∧ 能力匹配）+ 主权/授权校验
  → 经网络化 EnginePort 派发 Run 给选中 Pod，其 agent-runtime 驱动
  → 超载则本地背压 Rejected/Delayed/Yielded → agent-service 重路由
  → 结果经 Cursor Flow(SSE/Webhook) 回 client；密钥在 runtime pod 由 Vault/K8S 注入
```

**多 Agent 协同**
```
client 提交协同目标 → 一个 cursor
  → agent-runtime 内 Planner/GRAPH 协调者分解
  → 子 agent 调用 = 嵌套 Run；定位经 InstanceRegistry，A 直连 B 走 Track2 P2P（Mesh mTLS）
  → 共享上下文写服务端 Session（黑板）；委托 token 搭 EnginePort 信封逐层收窄(R-L)
  → 整组取消 = 根 Run 取消 → Track1 fan-out 到子 Run（R-J 再授权）
  → 协同进度以子 Run 标注汇入同一 cursor 事件流回 client
```
