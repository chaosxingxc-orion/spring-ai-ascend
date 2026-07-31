---
level: L1-HLD
TAG:
  - physical-view
  - deployment
  - network-boundary
  - architecture-fact
  - edge-plane
status: draft
dependency:
  - README.md
  - overview.md
  - logical.md
  - scenarios.md
  - development.md
  - process.md
  - api-appendix.md
  - spi-appendix.md
  - ../../L0-Top-Level-Design/views.md
  - ../../../docs/contracts/ingress-envelope.v1.yaml
  - ../../L2-Low-Level-Design/agent-client/Feat-Func-006-standard-agent-client-invocation.md
  - ../../L2-Low-Level-Design/agent-client/Feat-Func-007-local-tool-registration-and-execution.md
---

# agent-client L1 架构物理视图

## 1. 物理视图定位

物理视图描述 `agent-client` 的**部署形态、宿主环境、网络边界、状态持久化落点、凭据来源、
可观测数据出口，以及国产化硬件适配的相关约束**。它回答"这个 SDK 实际跑在什么进程/设备里、
和平台之间的网络怎么连、本地状态和敏感数据存在哪、边界在哪"。

`agent-client` 是逻辑模块，也是可嵌入多种宿主的库；它没有独立的服务进程，而是**寄居在
业务宿主进程内**。因此本视图的"部署单元"是"宿主 + 嵌入的 SDK"，不是独立服务。

## 2. 部署形态

`agent-client` 作为 SDK 可嵌入多种 C-Side 宿主，逻辑边界不随形态改变（`overview.md` §模块
边界形态）。

| 宿主形态 | 典型进程 | 本地能力 host | 状态持久化能力 |
|---|---|---|---|
| 服务端业务应用 | JVM 后端服务 | 进程内业务系统访问 | 可用文件/DB，易做持久化恢复 |
| 桌面应用 | 桌面 JVM 进程 | 本地文件系统、本地命令、审批 UI | 本地文件，受用户会话生命周期约束 |
| IDE / 开发工具（后续愿景） | IDE 插件宿主 | 工作区、调试运行时 | 工作区本地状态 |
| 受限容器 / 边缘设备 | 受限 JVM | 受策略约束的本地资源 | 可能只有内存态 |

移动端/浏览器等非 JVM 形态属跨语言 SDK 的后续候选，不在当前 Java V1 范围（设计提案 §9.3）。

## 3. 网络拓扑与边界

### 3.1 唯一出站方向

```text
[C-Side 宿主进程]
   agent-client SDK
      │  仅出站（client → gateway）
      │  A2A JSON-RPC over HTTPS + SSE
      ▼
[平台边界] agent-bus / gateway 公开入口（受治理）
      │  内部路由（client 不感知）
      ▼
   agent-runtime（Task owner）
```

物理边界铁律：

- **client 只发起出站连接到 gateway 公开 base URI**，不感知也不缓存 runtime endpoint、
  routeHandle、topic 或 broker 地址。
- **client 不开放任何入站端口/webhook**。服务端无法直接回调 client（`overview.md` §问题
  领域 6、`scenarios.md` TS-06）。这规避了 NAT/防火墙穿透、公网可达性、额外攻击面等问题，
  也是选择"主动多轮"而非"S2C 被动回调"的物理动因。
- 跨平面流量在两个方向上都经 gateway；client 与 runtime 无直连（gate Rule 105 强制）。

### 3.2 连接特性与降级

| 通道 | 物理特性 | 降级路径 |
|---|---|---|
| 控制请求 | HTTPS 短请求（创建/查询/取消/续跑） | 重试 + 退避 |
| 服务流 | SSE 长连接 | 企业代理/网关剥离长连接时，退化为轮询查询（协议提案 §5.1） |
| 大载荷 | 带授权与 TTL 的引用（A2A FilePart uri） | 不内联字节；由外部对象存储路径承载 |

SSE 长连接必须应对企业代理的 idle timeout：以心跳监督 + `stream idle timeout` + 重连
（`process.md` §4.2）。

## 4. 状态持久化落点

`agent-client` 通过 `ClientStateStore` SPI 抽象持久化，物理落点随宿主与需求变化：

| 数据 | 内容 | MVP 落点 | 持久化落点（需要恢复时） |
|---|---|---|---|
| ClientInvocation | 本地句柄、serverTaskRef、幂等键 | 内存 | 文件 / 本地 DB |
| Cursor | 流消费位置 | 内存 | 文件 / 本地 DB |
| Capability correlation + 结果 outbox | 待提交/已提交工具结果 | 内存 | 文件 / 本地 DB（跨重启恢复的关键） |
| 幂等键 / messageId | 请求去重指纹 | 内存 | 与对应记录同落点 |

物理约束：
- MVP 默认内存态，进程退出即丢，不承诺跨重启恢复（`process.md` §6.2）。
- 需要"关闭应用后重启继续未完成工具"时，结果 outbox 与幂等键**必须**落持久化存储，
  且遵守"先落盘再发网络"顺序（设计提案附录 §A.1、getting-started §5）。
- 持久化落点必须在 C-Side 边界内；不得把本地敏感正文或凭据无治理地写入会同步到平台的
  位置。

## 5. 凭据与敏感数据边界

- 凭据来自可信来源（企业凭据代理、环境变量、短期 token provider），经 `CredentialProvider`
  SPI 注入；SDK 不长期缓存、不落日志、不写入状态文件。
- tenant 由 gateway 从凭据解析；client **不得**通过 header 自报 tenant（协议提案 §2.2，
  `X-Tenant-Id` 由 gateway 接管）。
- 本地能力（尤其 Observation 读取的业务事实）的敏感正文默认不离开 C-Side；发往平台的
  只能是经授权、契约化的结果或引用（`overview.md` §问题领域 3、`scenarios.md` TS-04）。
- 大对象/多模态大正文用带授权与过期时间的引用，不经 bus 控制事件或日志内联。

## 6. 可观测数据出口

- SDK 通过 `ClientObservationListener` SPI 桥接宿主既有 metrics/trace/日志系统，不绑定
  特定 telemetry SDK。
- trace 通过 `traceparent`（W3C）端到端传播，串联 client → gateway → runtime。
- 结构化日志字段、脱敏规则、推荐指标见 `agent-client/docs/getting-started.md` §8：默认
  不记录凭据、prompt、工具参数/结果、可重放 cursor；高基数标识符（taskId/tool_call_id）
  不作为 metric label。

## 7. 国产化硬件适配（鲲鹏 / 昇腾）

- `agent-client` 是纯 JVM 客户端库，本身不做模型推理，不直接依赖昇腾算子或加速库；
  模型推理与硬件加速发生在 compute plane（runtime 及其后端），client 不承载。
- 物理适配要求集中在**运行环境**：SDK 必须能在鲲鹏（aarch64）架构的 JVM 上正常构建与
  运行，依赖项需具备 aarch64 兼容的实现，不得引入仅 x86 的原生依赖。
- 因此 SDK 应尽量只依赖纯 Java 实现（JDK HttpClient 等），避免绑定平台相关 native 库，
  以保持在国产化硬件与通用硬件上的一致可移植性。

## 8. 部署边界不变量汇总

- client 只出站连 gateway 公开入口；无入站端口、无 webhook、无 runtime 直连。
- 本地状态、凭据、敏感正文留在 C-Side 边界内。
- 大载荷走带授权 TTL 的引用，不内联、不经 bus 长期缓存。
- 跨重启恢复能力取决于所选 `ClientStateStore` 物理落点；MVP 不承诺。
- SDK 在鲲鹏 aarch64 JVM 上可移植；硬件加速不下沉到 client。

## 9. 与其他视图的衔接

- 状态归属与领域对象：`logical.md`。
- 运行时线程/多轮/恢复流程：`process.md`。
- 代码分层、依赖红线、构建基线：`development.md`。
- 技术场景：`scenarios.md`。
- 网络协议与降级细节：L2 `Feat-Func-006` §3.5/§5.2；对 gateway 的要求见 `Feat-Func-006` §8 与 `Feat-Func-007` §8。
