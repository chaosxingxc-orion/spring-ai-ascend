---
level: L1-HLD
TAG:
  - development-view
  - module-structure
  - dependency-boundary
  - architecture-fact
  - edge-plane
status: draft
dependency:
  - README.md
  - overview.md
  - logical.md
  - scenarios.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
  - ../../L0-Top-Level-Design/views.md
  - ../../../docs/contracts/ingress-envelope.v1.yaml
  - ../../../../agent-client/docs/proposals/agent-client-v1-design.md
  - ../../L2-Low-Level-Design/agent-client/Feat-Func-006-standard-agent-client-invocation.md
  - ../../L2-Low-Level-Design/agent-client/Feat-Func-007-local-tool-registration-and-execution.md
---

# agent-client L1 架构开发视图

## 1. 开发视图定位

开发视图描述 `agent-client` 的**代码组织、分层依赖方向、禁止依赖红线、构建基线、
扩展点（SPI）布局和测试守卫**。它回答"这个 SDK 的源码应该怎么切分、哪些依赖允许、
哪些被 CI 拦截、如何在不破坏 edge plane 边界的前提下演进"。

本视图不规定最终 artifact 数量，也不冻结类级签名（类级内容进入 `api-appendix.md` /
`spi-appendix.md` 与 L2）。它与 `logical.md` 的四个责任面对应，但从**可编译单元和依赖
方向**角度约束实现。

本视图的落地细节与拟议 Java 形状见提案文档
`agent-client/docs/proposals/agent-client-v1-design.md` §7（四层模块方案）；此处只固化
L1 层面的开发约束。

## 2. 分层与包结构

### 2.1 四层依赖方向

`agent-client` 采用单向分层：上层依赖下层，下层不得反向依赖上层。V1 可在单个 Maven
artifact 内按包隔离，稳定后再拆分 artifact；不得为追求模块数量提前制造循环依赖。

```text
┌─────────────────────────────────────────────┐
│ L4 集成与开发体验（可选，独立 artifact 候选） │
│   spring-boot-starter / examples / bridges   │
└───────────────┬─────────────────────────────┘
                │ 只依赖 L1 公共 API/SPI
                ▼
┌─────────────────────────────────────────────┐
│ L3 transport / codec adapter                 │
│   gateway HTTP / A2A JSON-RPC / SSE codec     │
└───────────────┬─────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────┐
│ L2 core 编排与状态                            │
│   三套状态机 / 去重 / 恢复 / 有界队列          │
└───────────────┬─────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────┐
│ L1 公共 API / SPI（业务与工具开发者可见）      │
│   AgentClient / LocalTool / 值对象 / 扩展点    │
└─────────────────────────────────────────────┘
```

对应 `logical.md` 的责任面：`sdk-facade` 表面属于 L1 公共 API；`invocation-state` 与
`stream-and-turn-loop` 属于 L2 core；`capability-and-debug` 的执行编排属于 L2 core，
其对外扩展点属于 L1 SPI；具体 A2A/SSE 绑定属于 L3。

### 2.2 建议包布局

| 层 | 建议包 | 内容 | 可见性 |
|---|---|---|---|
| L1 API | `com.huawei.ascend.client.api` | AgentClient、InvocationCall、值对象 | 公开 |
| L1 SPI | `com.huawei.ascend.client.tool.spi` | LocalTool、LocalToolRegistry、ToolCallChannel | 公开 |
| L1 SPI | `com.huawei.ascend.client.state.spi` | ClientStateStore | 公开 |
| L1 SPI | `com.huawei.ascend.client.auth.spi` | CredentialProvider | 公开 |
| L1 SPI | `com.huawei.ascend.client.observation.spi` | ClientObservationListener | 公开 |
| L2 core | `com.huawei.ascend.client.internal.*` | 状态机、去重、恢复、队列 | 内部 |
| L3 transport | `com.huawei.ascend.client.transport.*` | HTTP/A2A/SSE/codec | 内部 |
| L4 集成 | `com.huawei.ascend.client.spring` 等 | starter、bridge、examples | 独立 artifact |

`internal` / `transport` 包是实现细节，不承诺兼容性；只有 `api` 与 `*.spi` 是对下游的
稳定契约。

## 3. 依赖红线（CI 强制）

### 3.1 禁止的跨平面依赖

`agent-client` 是 edge plane 模块，**任何层都禁止 import 以下 compute plane 生产代码**：

- `com.huawei.ascend.runtime.*`（agent-runtime）
- `com.huawei.ascend.core.*`（agent-core）
- `com.huawei.ascend.middleware.*`（agent-middleware）

理由见 `logical.md` §5：client 可独立分发到客户现场，一旦与服务端 Java 类型耦合，
服务端演进将强制重新分发所有已部署 SDK。client 与服务端之间只能通过线协议通信。

该红线由仓库既有守卫强制：
`agent-client/src/test/java/com/huawei/ascend/client/architecture/EdgeToComputeDirectLinkArchTest.java`
以及 gate Rule 105（`edge_no_direct_compute_link`，扫描禁止 import 与针对非 bus 主机
的 WebClient/RestTemplate 构造）。

### 3.2 公共 API/SPI 的第三方类型红线

L1 公共 API 与 SPI **只允许出现 `java.*` 与 agent-client 自有值对象**。禁止在公开签名中
泄漏：

- Spring 注解与类型
- Reactor `Mono` / `Flux`
- Jackson `JsonNode` / `ObjectMapper`
- A2A SDK（`org.a2aproject.*`）类型
- HTTP client、broker、数据库厂商类型

并发用 `CompletionStage` 与 `Flow.Publisher`；时间用 `Instant` / `Duration`；endpoint 用
`URI`。L3 adapter 内部可以使用 Reactor、Jackson、A2A SDK、JDK HttpClient，但必须在层
边界转换，不得泄漏到 L1/L2 公开签名。

建议以 ArchUnit 规则固化：`api` 与 `*.spi` 包的公开类型不得引用上述禁止包。

### 3.3 允许的依赖

| 需求 | 允许方式 |
|---|---|
| 跨平面请求 | 通过线协议（A2A JSON-RPC over HTTP/SSE）访问 gateway 公开入口；见协议提案 |
| ingress 契约语义 | 可理解 `ingress-envelope.v1.yaml` 语义，但不 import agent-bus 内部实现类型 |
| agent-bus SPI（可选 adapter） | 仅 L3 可选 adapter 允许，且不得让 L1/L2 反向依赖 bus internal（决策项 L-03） |

## 4. 扩展点（SPI）布局

SPI 是"平台机制 vs 业务策略"的分界（见设计提案附录 A.5）：SDK 编排骨架不可绕过，
策略实现由业务方注入。V1 建议保留的扩展点：

| SPI | 责任 | 默认实现 |
|---|---|---|
| `LocalTool` | 业务本地能力（Observation/Action）执行体 | 无（业务提供） |
| `LocalToolRegistry` | 工具注册/替换/注销/查找 | SDK 内置内存实现 |
| `ToolCallChannel` | 工具调用意图接收与结果回传的 transport | 由 bus 评审后的一种 transport |
| `ClientStateStore` | invocation/cursor/结果 outbox/幂等键持久化 | in-memory；可换文件/DB |
| `CredentialProvider` | 提供短期凭据，不落日志 | 无（业务提供） |
| `ClientObservationListener` | metrics/trace/日志桥接 | no-op |
| `PolicyGuard` / `ApprovalProvider` | Action 授权与人工审批（治理策略） | 业务提供；MVP 可给保守默认 |

新增扩展点必须遵守 §3.2 的类型红线。

## 5. 构建基线

| 项 | 基线 | 说明 |
|---|---|---|
| JDK | 17（决策项 L-08 已敲定） | 只用 17 稳定特性（record/sealed/instanceof 模式匹配/文本块），不依赖虚拟线程；可用 `--release 17` 在更高 JDK 上验证；升级 21+ 时执行器平滑替换 |
| 构建 | Maven（仓库 `mvnw` / `mvnw.cmd`） | — |
| 版本 | 实施前必须先修复：根 POM 为 `0.2.0-SNAPSHOT`，而 `agent-client` 父版本仍为 `0.1.0-SNAPSHOT`（pom.xml:20-21 与 agent-client/pom.xml:20-22） | 见设计提案 §2.1，属 Phase 0 前置 |
| 版本策略 | Java API 语义化版本（SemVer）；wire envelope 带 `schemaVersion` | 见设计提案 §3.7 |
| 依赖现状 | 当前 agent-client 无 HTTP/A2A/JSON codec 生产依赖，仍是 skeleton | 引入依赖属 Phase 2 |

## 6. 测试守卫布局

开发视图要求测试与源码同步演进。测试分层与目录建议：

| 测试类型 | 位置 | 守卫目标 |
|---|---|---|
| 架构守卫（ArchUnit） | `src/test/java/.../architecture` | §3 依赖红线、公开签名类型纯度 |
| 单元：状态机 | `src/test/java/.../internal` | 三套状态机合法/非法转换、UNKNOWN、乱序/重复/终态后事件 |
| 单元：注册与去重 | 同上 | 重复 tool_call_id 只执行一次、注册冲突确定性失败 |
| 契约测试（golden fixture） | `src/test/resources/wire-fixtures/v1/` + 对应测试 | 与 bus/runtime 共享 fixture，编解码一致、未知字段前向兼容 |
| 集成（fake gateway/WireMock） | `src/test/java/.../transport` | accepted/rejected/UNKNOWN/超时/失败/取消路径 |
| 故障注入 | 专用 profile | 连接中断、gateway 重启、Task missing、重复/乱序、慢订阅者、close/drain |

共享 fixture 清单见协议提案 §9；测试方法与命令见 `agent-client/docs/getting-started.md`
§6–§7。契约测试与 fixture 的关系是硬规则：**任一方修改 fixture 即协议变更，必须双方
契约测试同时通过**。

## 7. 演进与 artifact 拆分策略

- V1：单 artifact 内按包隔离（§2.2），以 ArchUnit 维持层边界。
- 稳定后：可将 L4 集成层（Spring Boot starter、examples）拆为独立 artifact，避免 L1
  公共 API 反向依赖 Spring。
- 后续 transport（WebSocket / long-poll / S2C 被动通道）作为新增 L3 adapter，不改动
  L1/L2 公开签名。
- 开发态调试能力（`DebugSession` 等，见 `logical.md` §2.5）属后续 wave，落地时新增独立
  责任面与包，不污染调用/工具主干。

## 8. 与其他视图的衔接

- 责任面语义：`logical.md` §3。
- 运行时线程/并发/多轮流程：`process.md`。
- 部署形态与网络/持久化边界：`physical.md`。
- 拟议 Java API/SPI 形状与分阶段实施：`agent-client/docs/proposals/agent-client-v1-design.md`。
- 线协议（对齐 runtime `Feat-Func-009`）：L2 `Feat-Func-006` §3.5 / `Feat-Func-007` §3.5。
