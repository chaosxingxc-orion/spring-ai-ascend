---
version: 0730
module: agent-runtime
feature_type: functional
feature_id: FEAT-003
status: active
related_docs:
  - ./FEAT-001-standardized-agent-service-entrypoint.md
  - ./FEAT-004-remote-agent-orchestration.md
  - ../architecture/L0-Top-Level-Design/boundaries.md
  - ../architecture/L0-Top-Level-Design/glossary.md
  - ../architecture/L1-High-Level-Design/agent-runtime/README.md
  - ../architecture/L1-High-Level-Design/agent-runtime/development.md
  - ../architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-001-standardized-agent-service-entrypoint.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-003-agent-task-state-cache.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-004-remote-agent-orchestration.md
---

# 智能体任务状态缓存特性文档

## 1. 特性定位

FEAT-003 定义 `agent-runtime` 使用 Redis 缓存智能体任务状态时的统一接入要求。该特性新增标准化 Redis 缓存 SPI，使运行时与开发框架能够复用同一 Redis 连接池和操作接口，当前版本支持缓存 A2A Task、Agent 执行 checkpoints 与 DeepAgent Todolist 相关任务上下文快照，并允许 DeepAgent 执行期复用同一 Redis 接入能力自治持久化 Todolist 状态。不同 Redis 使用方能够在原生 Redis 单机版、原生 Redis 集群版以及客户自封装 Redis 组件之间通过统一配置和 SPI 适配切换。

本特性解决的问题是：Agent 在工行现场当前使用原生 Redis 单机版，但工行有自封装 Redis 组件。为满足客户技术规范，Redis 资源的使用需要被客户既有平台统一管理和监控；同时产品不能把业务代码、A2A Task 存储、Agent 状态持久化或未来中间件能力直接绑定到某一个 Redis 客户端实现。此外，DeepAgent 的 Todolist 能力在分布式无状态的企业级部署中需要跨实例共享任务内规划状态，并在 Task 下发、挂起等 runtime 生命周期边界进入任务级上下文缓存，避免同一 Task 被恢复或转移处理时丢失 Todolist 上下文。

本特性对客户的价值是：满足客户对 Redis 资源接入方式的规范要求，使 Redis 资源纳入客户统一管理和监控体系。对产品的价值是：通过简单配置和稳定扩展点适配不同 Redis 数据源，降低多客户交付时的定制成本。Todolist Redis 化使 runtime-hosted DeepAgent 在分布式部署中获得任务级状态隔离和共享能力，不依赖本地文件系统作为企业级并发隔离方案。

对下游设计和实现而言，本特性是 `agent-runtime` 智能体任务状态缓存的事实来源。L2 设计、Redis 缓存 SPI、默认原生 Redis 适配、客户封装组件适配、日志、测试和指南必须以本文定义的外部行为、边界和验收要求为准；实现中已经存在但本文未声明的 Redis 命令、连接池参数、缓存策略或客户专用能力，不能自动成为当前版本对外事实承诺。

本特性面向以下角色：

- 现场交付人员：通过配置选择当前部署环境的 Redis endpoint 拓扑，并通过装配选择默认或客户 Redis 适配实现。
- 客户平台团队：提供客户自封装 Redis 组件，并确认资源被统一治理系统接管。
- Runtime 开发者：基于统一 Redis 操作接口消费 Redis 能力，不直接依赖具体 Redis 客户端。
- 客户适配开发者：把客户自封装 Redis 组件适配为 runtime 可识别的 Redis 数据源实现。
- DeepAgent 适配开发者：在 Task 下发和挂起边界把 Todolist 上下文纳入 runtime task-scoped snapshot，并在执行期为 Core 提供可复用的 Redis 状态存储接缝。
- 运维人员：通过启动日志确认当前生效的数据源策略和关键连接配置。
- 集成测试团队：按验收标准验证原生 Redis 单机版、集群版、配置切换和 DeepAgent Todolist 任务级隔离行为。

本特性只定义 Redis 数据源的选择、统一操作接口、状态缓存 TTL 基本语义、配置切换、日志脱敏、DeepAgent Todolist 与 runtime Task 上下文缓存的协作边界，以及验收边界。Redis 本身的部署、容灾、数据持久化、监控平台建设、客户封装 JAR 的内部实现、客户私有安全策略和 agent-core 独立样例程序的本地存储策略不属于 `agent-runtime` 当前版本承诺。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 本次版本变化 | 事实要求 |
|---|---|---|---|
| Redis endpoint 拓扑配置 | MUST | 现有 | 系统必须支持通过配置文件指定 Redis endpoint 拓扑，取值至少包括原生单机和原生集群；未配置拓扑类型时按单机兼容处理。 |
| 原生 Redis 单机版接入 | MUST | 现有 | 系统必须支持接入原生 Redis 单机版，并能完成运行时 Redis 读写操作。 |
| 原生 Redis 集群版接入 | MUST | 现有 | 系统必须支持以同一操作接口接入原生 Redis 集群版；集群路由、连接和故障处理由对应数据源实现承接。 |
| 客户封装 Redis 适配扩展点 | MUST | 现有 | 系统必须提供客户封装 Redis 组件的适配扩展点，使客户组件可在不改动业务代码的前提下承接 Redis 操作。 |
| 统一 Redis 操作接口 | MUST | 现有 | 不同 Redis endpoint 拓扑和适配实现对上层暴露的操作接口必须保持一致，切换 Redis 接入方式不得要求业务代码变化。 |
| 启动策略日志 | MUST | 现有 | 启动日志必须直观体现当前生效的 Redis endpoint type、适配实现和关键非敏感配置，便于运维确认。 |
| 密码日志脱敏 | MUST | 现有 | 密码、密钥、token、凭证密文或解密后的敏感值不得在日志中输出。 |
| 配置切换 | MUST | 现有 | 在单机和集群 Redis endpoint 之间切换时，只允许修改配置；在默认实现和客户实现之间切换时，只允许替换 SPI 适配 Bean，不得要求修改使用 Redis 的业务代码。 |
| 状态缓存 TTL | SHOULD | 现有 | Redis-backed A2A Task 快照和 Agent checkpoints 应支持统一的可配置 TTL，并提供简单默认值，避免状态缓存长期无界增长。 |
| A2A Task 与运行时状态复用 | SHOULD | 现有 | 当前版本承诺 A2A Task 存储和 Agent 状态持久化复用同一 Redis 数据源抽象；其他运行时能力如需使用 Redis，应经独立特性评审后复用本 SPI，不自动进入本特性范围。 |
| 客户模式内部不可复现说明 | MUST | 现有 | 文档和验收说明必须明确：客户自封装 Redis JAR 因安全合规要求不能外传，内部环境不直接复现客户模式，只验证可适配能力和默认模式。 |
| 配置错误可诊断 | SHOULD | 现有 | Redis endpoint type、连接配置或适配实现缺失时，系统应在启动或首次使用时给出明确错误信息。 |
| 资源生命周期管理 | SHOULD | 现有 | Redis 数据源实现应支持按应用生命周期初始化和释放连接资源，避免泄漏。 |
| DeepAgent Todolist 任务上下文快照 | MUST | 新增 | 在 Task 下发和挂起等 runtime 生命周期边界，runtime-hosted DeepAgent 的 Todolist 上下文必须能够作为 task-scoped opaque payload 写入 Redis-backed Task 上下文缓存，恢复时回灌给对应 adapter/Core。 |
| DeepAgent 执行期 Todolist 自治持久化 | MUST | 新增 | 在 Task 连续执行过程中，Todolist 的 save/load 由 Core/DeepAgent 按自身领域语义自治触发；runtime 只提供 Redis 连接复用和 task-scoped 存储接缝，不逐步解释或接管 TodoItem 领域模型。 |
| Todolist 任务级隔离 | MUST | 新增 | Todolist Redis 存储必须下探到 Task 级隔离，key 必须继承部署方配置的 keyPrefix，并包含 taskId 或等价 task-scoped 标识；不得仅依赖 tenantId、agentId、conversationId 或 sessionId 作为最小隔离粒度。 |
| Todolist 文件存储排除 | MUST | 新增 | runtime-hosted 分布式多并发场景不承诺、不推荐、不回退到本地文件系统保存 Todolist；agent-core 独立样例或非生产用法的本地存储策略不属于本特性范围。 |

## 3. 外部接口与入口要求

| 入口 / 配置 | 类型 | 事实要求 |
|---|---|---|
| Redis endpoint 拓扑配置 | configuration | 必须能够表达当前启用的 Redis endpoint 拓扑，例如原生单机或原生集群。客户封装组件不通过新增配置类型表达，而是通过 SPI 适配 Bean 接管。 |
| Redis 连接引用 | configuration | 应支持按名称引用 Redis 连接配置，使 checkpointer、TaskStore、DeepAgent Todolist 存储接缝或其他中间件能力可以复用同一数据源定义。 |
| Redis 连接关键配置 | configuration | 应支持 host、port、database、timeout、集群节点或客户组件所需的等价连接参数。database 是单机兼容配置；集群模式忽略该字段且不应因此启动失败。密码类配置必须按安全要求加密或脱敏处理。 |
| Redis 状态缓存 TTL | configuration | 应支持为 Redis-backed A2A Task 快照、Agent checkpoints 和 task-scoped DeepAgent Todolist 上下文快照配置秒级 TTL；当前版本使用简单统一 TTL，不要求复杂 eviction 策略。 |
| Todolist Task 上下文存储接缝 | SPI / adapter contract | runtime-hosted DeepAgent adapter 必须能在 Task 下发和挂起边界提交/恢复 Todolist 上下文快照；payload 对 runtime 不透明，由 Core/adapter 负责序列化、反序列化和语义解释。 |
| Todolist 执行期 Redis 复用接缝 | SPI / adapter contract | Core/DeepAgent 执行过程中的 Todolist save/load 可以复用 runtime 提供的 Redis 操作接口或其封装接缝；runtime 不在每次 Todolist 变更时强制介入写 Task 快照。 |
| Runtime Redis 操作接口 | SPI | 必须提供当前运行时 Redis 消费方需要的最小读写、TTL、删除、存在性判断、批量读取和扫描语义；具体方法面由 L2 设计约束。 |
| 默认原生 Redis 实现 | adapter | 在未提供客户适配实现且配置为原生 Redis 时，系统应提供默认原生 Redis 连接策略。 |
| 客户 Redis 适配实现 | adapter | 客户封装 Redis 组件应通过适配实现接入统一 Redis 操作接口；客户 JAR 不需要进入产品开源仓库。 |
| 启动日志 | observability | 必须输出当前 endpoint type、连接引用、host/port 或集群节点摘要、database、timeout、适配实现标识等非敏感信息。集群模式下配置非 0 database 时，应输出非敏感 ignored 提示；不得输出明文密码或加密密码原文。 |
| 使用方能力 | runtime consumer | 当前版本的 Redis 使用方范围是 A2A Task 存储、Agent 状态持久化、DeepAgent Todolist 任务上下文快照和执行期 Todolist Redis 存储接缝；所有 Redis 使用方只依赖统一 Redis 操作接口，不直接依赖某个 Redis 客户端。 |
| 配置错误诊断 | observability | Redis endpoint type、连接配置或适配实现缺失时，系统应在启动或首次使用时给出明确错误信息，不静默退回错误数据源，不输出敏感信息。 |
| Redis 资源生命周期 | lifecycle | Redis 数据源实现应支持按应用生命周期初始化和释放连接资源，避免泄漏。初始化在应用启动阶段完成，释放在应用关闭阶段完成。 |

## 4. 场景与用户旅程

### 4.1 原有场景（保留）

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 原生 Redis 单机版启动 | 内部环境已部署 Redis 单机版 | 运维在配置文件中选择原生单机模式并配置连接信息 | 应用启动并创建对应 Redis 数据源，日志显示单机策略和非敏感连接摘要，运行时 Redis 读写正常。 |
| 原生 Redis 集群版启动 | 内部环境已部署 Redis 集群版 | 运维在配置文件中选择原生集群模式并配置集群节点 | 应用启动并创建对应 Redis 数据源，业务代码不变化，运行时 Redis 读写正常。 |
| 单机版切换到集群版 | 同一应用已有 Redis 单机版配置 | 运维只修改 Redis endpoint type 和连接配置后重启 | 上层业务代码和 Redis 使用方无需修改，启动日志体现新策略，读写操作走集群数据源。 |
| 接入客户封装 Redis 组件 | 客户提供自封装 Redis JAR 和适配代码 | 现场交付把客户适配实现注册为 runtime Redis SPI Bean，并复用同一 Redis endpoint 配置 | 运行时 Redis 操作由客户组件承接，客户统一管理和监控系统可识别资源使用。 |
| 客户 JAR 不可内部复现 | 客户封装 Redis JAR 因安全合规不能外传 | 内部集成测试执行当前版本验收 | 内部只验证原生单机、原生集群和统一适配扩展点；客户模式由现场或客户环境完成联调确认。 |
| 不同逻辑 runtime 共用 Redis 服务 | 多个独立 Agent 宿主使用同一 Redis 服务 | 开发者定义 Agent checkpoint 等业务 key，部署方为不同逻辑 runtime 规划相互隔离的 keyspace | A2A TaskStore key 与开发者定义的业务 key 不发生跨 runtime 覆盖或扫描串扰；SDK 不自动生成跨 runtime namespace，也不规定开发者业务 key 的名称或前缀。 |
| 密码脱敏确认 | Redis 配置包含密码或加密密码 | 应用启动并打印 Redis 数据源策略日志 | 日志不得出现明文密码、加密密文或可逆凭据，只显示已配置/未配置等安全摘要。 |
| 配置缺失或类型错误 | 配置指定 Redis 但缺少连接信息或适配实现 | 应用启动或首次创建 Redis 数据源 | 系统给出可诊断错误，不静默退回错误数据源，不输出敏感信息。 |

### 4.2 DeepAgent Todolist 任务级状态场景（新增）

runtime-hosted DeepAgent 提供 Todolist 规划能力时，Todolist 的领域语义归 Core/DeepAgent，任务生命周期边界和任务级上下文缓存归 runtime。Core 不需要感知 tenantId、agentId、conversationId、sessionId 与 taskId 的组合隔离规则；runtime/adapter 为当前 Task 注入已经绑定隔离域的 Todolist 状态存储接缝。

| 场景 | 前置条件 | Runtime / Adapter 行为 | Core / DeepAgent 行为 |
|---|---|---|---|
| Task 下发初始化 Todolist 上下文 | runtime 创建或接收 Task，准备调用 DeepAgent | 基于 taskId 建立 task-scoped Todolist 存储接缝，并可读取 Redis 中该 Task 的既有上下文快照后回灌给 adapter/Core | 通过注入的接缝读取当前 Task 的 Todolist 初始状态，不感知 Redis key、endpoint 或客户适配实现。 |
| Task 连续执行期 Todolist 更新 | 同一 runtime 实例正在执行 DeepAgent，未发生挂起或恢复 | runtime 不在每次 Todolist 变更时强制写 Task 快照，只提供可复用 Redis 连接和 task-scoped 存储接缝 | Core/DeepAgent 按自身规划语义触发 Todolist save/load，复用接缝自治持久化执行期状态。 |
| Task 挂起保存 Todolist 上下文 | DeepAgent 触发远端 Agent 调用、中间件代理、本地工具、等待用户输入等外部等待，Task 需要脱水挂起 | runtime 在挂起边界把当前 Todolist 上下文作为 opaque payload 写入 Redis-backed Task 上下文缓存，TTL 与 Task 状态缓存策略一致 | Core/adapter 提供可序列化的 Todolist payload，不要求 runtime 理解 TodoItem schema。 |
| Task 恢复读取 Todolist 上下文 | 同一 Task 后续请求可能被路由到不同 runtime 实例 | runtime 按 taskId 从 Redis 读取 Task 上下文快照，并通过 adapter 回灌 Todolist payload | Core/DeepAgent 恢复 Todolist 后继续执行；跨实例读取以 Task 级隔离为准。 |
| Todolist 任务级隔离 | 多个 Task 可能属于同一 tenant、agent、conversation 或 session，也可能跨实例执行 | Redis key 继承 keyPrefix，并包含 taskId 或等价 task-scoped 标识；具体 key schema 属于 runtime 内部实现细节 | Core 只使用当前 Task 的状态接缝，不以 tenantId、agentId、conversationId 或 sessionId 自行拼接 Redis key。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 配置选择语义

- Redis endpoint type 由配置文件声明，应用启动时选择对应单机或集群连接策略。
- 配置应能区分原生 Redis 单机版和原生 Redis 集群版；客户封装 Redis 组件通过 SPI Bean 装配接入，不新增客户专用 endpoint type。
- 切换 Redis endpoint 拓扑只改变配置；切换默认实现和客户实现只改变适配 Bean 装配；两者都不改变 A2A Task 存储、Agent 状态持久化、DeepAgent Todolist 存储接缝或其他 Redis 使用方代码。
- 未配置 Redis endpoint type 时应按单机兼容处理，继续支持既有 host、port、database、timeout-ms、encrypted-password 单机配置。
- 集群模式必须配置至少一个 host:port 形式的 nodes；host/port 不用于创建集群 client。
- database 仅作为单机模式兼容配置：单机模式继续生效，集群模式忽略且不作为启动失败条件；集群模式下非 0 database 应产生非敏感 ignored 诊断提示。
- 当配置指定的 endpoint type 或适配实现无法装配时，系统应明确失败或给出可诊断错误，不应静默使用与配置不一致的数据源。

#### 5.1.2 统一操作接口语义

- Redis 使用方只能面向 runtime 统一 Redis 操作接口编程。
- 原生 Redis 单机版、集群版和客户封装 Redis 组件都必须适配到同一操作语义。
- 统一接口只承诺当前运行时需要的最小 Redis 命令面，不承诺暴露 Redis 全量命令。
- 客户封装 Redis 组件如存在与原生命令差异，应由客户适配实现完成语义对齐或显式报错。

#### 5.1.3 TTL 与数据边界语义

- Redis-backed A2A Task 快照、Agent checkpoints 和 task-scoped DeepAgent Todolist 上下文快照应使用带 TTL 的写入或在写入后刷新 TTL，避免缓存数据无限期增长。
- 当前版本只要求提供一个简单的秒级 TTL 配置和默认值，不要求 LRU、max-entries、按租户配额或其他复杂 eviction 策略。
- Redis-backed A2A TaskStore 的 key schema 和 value 序列化格式属于 runtime 内部实现细节，当前版本不作为外部稳定契约；运维和业务代码不应依赖其具体 key pattern 或序列化内容。
- Agent checkpoint 及其他业务 key 的名称、前缀和结构由开发者定义，SDK 不写死开发者业务 key 的前缀。
- DeepAgent Todolist 任务上下文缓存必须以 Task 为最小隔离粒度。key 必须继承部署方配置的 keyPrefix，并包含 taskId 或等价 task-scoped 标识；不得仅以 tenantId、agentId、conversationId 或 sessionId 作为隔离依据。具体 key schema 不作为外部稳定契约。
- 当不同逻辑 runtime 使用同一 Redis 服务时，开发者和部署方必须通过独立 endpoint、单机独立 database、由客户 Redis 适配器或接入代理统一增加 namespace，或其他等价方式保证完整 keyspace 隔离。SDK 不基于 `spring.application.name` 或其他 runtime 标识自动增加前缀。

#### 5.1.4 DeepAgent Todolist Redis 协作语义

- Todolist 的领域模型、TodoItem schema、规划语义、增删改查和执行期 save/load 触发时机归 Core/DeepAgent 自治；runtime 不解释、不合并、不校验 TodoItem 业务内容。
- runtime-hosted DeepAgent 场景下，runtime/adapter 必须为当前 Task 注入 task-scoped Todolist 状态存储接缝，使 Core 能够复用当前 Redis 数据源完成执行期 Todolist 自治持久化。
- runtime 只在 Task 下发、挂起、恢复等生命周期边界介入 Todolist 上下文缓存：下发或恢复时读取 task-scoped snapshot 并回灌给 adapter/Core，挂起时把当前 Todolist 上下文作为 opaque payload 写入 Redis-backed Task 上下文缓存。
- runtime 不要求在 DeepAgent 每次 Todolist 变更后同步刷新 Task 上下文快照；连续执行期的高频写入由 Core 通过注入接缝自治完成。
- Todolist Redis 存储必须下探到 Task 级隔离，不承诺跨 Task 共享 Todolist，不把跨租户、跨会话或跨智能体实例隔离描述为足够粒度。
- runtime-hosted 分布式多并发场景不承诺、不推荐、不回退到文件系统保存 Todolist。agent-core 独立样例或非生产用法如需使用本地文件能力，由 agent-core 自身文档和实现承接，不进入本特性范围。

#### 5.1.5 日志与安全语义

- 启动日志必须帮助运维确认当前生效的数据源策略。
- 日志可输出 endpoint type、连接引用、host、port、database、timeout、集群节点数量或适配实现标识等非敏感信息。
- 日志不得输出明文密码、encrypted-password 原文、token、密钥、证书内容或客户私有鉴权材料。
- 配置错误、连接失败和适配失败日志也必须遵守同样的脱敏要求。

#### 5.1.6 验收语义

- 内部验收必须搭建原生 Redis 单机版和原生 Redis 集群版，分别验证运行时 Redis 读写操作正常。
- 内部验收必须验证单机版和集群版之间切换只需修改配置，不需要修改业务代码。
- 客户封装 Redis JAR 因安全合规不能外传，内部环境不要求直接复现客户模式。
- 客户模式的最终联调由具备客户组件访问权限的现场或客户环境完成；产品侧必须保证 SPI 扩展点、统一 endpoint 配置和统一操作接口具备接入能力。
- Todolist 持久化验收必须验证：Task 下发/挂起/恢复边界的 task-scoped Todolist 上下文写入和回灌、DeepAgent 执行期通过注入接缝复用 Redis 自治 save/load、跨实例恢复读取、多个 Task 的 key 隔离，以及 runtime-hosted 场景不回退文件系统。

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 客户 Redis JAR 内置 | 不承诺在产品仓库中包含工行自封装 Redis JAR 或其内部源码。 |
| 客户模式内部复现 | 不承诺在内部环境直接复现工行客户模式。 |
| Redis 服务部署 | 不负责部署、扩容、备份、容灾或监控 Redis 服务本身。 |
| Redis 全命令支持 | 不承诺把 Redis 全量命令暴露为 runtime SPI。 |
| 业务语义迁移 | 不承诺在切换 Redis 数据源时迁移既有 Redis 数据或转换 key schema。 |
| key schema 稳定性 | 不承诺 Redis key schema 或 value 序列化格式作为外部稳定接口。 |
| 客户监控平台建设 | 不承诺实现客户统一监控平台或运行时 Redis 指标体系，只保证可通过客户组件接入其治理体系。 |
| 零重启切换 | 当前版本不要求运行中动态切换 Redis 数据源；配置切换可通过重启生效。 |
| 运行时故障自动降级 | 当前版本不承诺 Redis 运行中断后的自动流量迁移、fail-open、fail-close 或内存降级；Redis 可用性由部署和系统工程方案承接。 |
| 多租户 key 隔离 | 当前版本不承诺按租户生成 key namespace 或做请求维度租户隔离。 |
| 跨逻辑 runtime keyspace 自动隔离 | 当前版本不承诺为不同逻辑 runtime 自动生成 Redis key namespace，也不承诺未隔离共用同一 keyspace 时不存在 key 覆盖、扫描串扰或运维归属不清；隔离由开发者和部署方承接。 |
| 多数据源同时路由 | 当前版本不要求同一运行时实例按业务请求或使用方维度动态路由到多个 Redis 数据源。 |
| 私有安全协议 | 客户封装组件的私有鉴权、TLS、审计和安全协议由客户适配实现承接，当前版本不定义更复杂的认证配置模型。 |
| Todolist 领域模型治理 | 不承诺 runtime 理解、校验、合并或迁移 TodoItem JSON；Todolist payload 属于 Core/DeepAgent 内部契约，runtime 只按 opaque payload 缓存和回灌。 |
| Todolist 跨 Task 共享 | 不承诺 Todolist Redis 数据跨 Task 共享；跨租户、跨会话或跨智能体实例隔离不能替代 Task 级隔离。 |
| Todolist 文件系统存储 | runtime-hosted 分布式多并发场景不承诺、不推荐、不回退到文件系统保存 Todolist；agent-core 独立样例或非生产用法的本地存储策略不属于本特性范围。 |
| 异常全过程恢复 | 当前版本先承诺 Task 级异常恢复所需的 Todolist 上下文边界缓存，不承诺 DeepAgent 每一步执行过程的完整异常恢复或精确重放。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把 Redis endpoint 拓扑选择、统一操作接口和客户适配扩展点作为核心边界，不得把上层业务能力绑定到某一个 Redis 客户端实现。
- L2 设计必须明确默认原生 Redis 策略与客户封装 Redis 策略的装配优先级和失败语义。
- 实现必须保证密码类配置不进入启动日志、错误日志或诊断输出。
- 实现必须为 Redis-backed A2A Task 快照、Agent checkpoints 和 task-scoped DeepAgent Todolist 上下文快照提供简单统一的可配置 TTL，并在默认配置下避免无 TTL 写入。
- 实现必须让 Redis 使用方通过统一接口完成读写、TTL、删除、存在性判断、批量读取和扫描等当前必要语义；具体方法、命令映射和异常语义由 L2 设计约束。
- L2 设计必须分别明确 A2A TaskStore 内部 key、开发者定义的 Agent checkpoint 或业务 key、Todolist task-scoped key/上下文快照，以及不同逻辑 runtime 共用 Redis 时的 keyspace 隔离责任；不得把多租户隔离与跨逻辑 runtime 隔离合并描述。
- 测试必须覆盖原生 Redis 单机版和集群版读写、既有单机配置未配置 type 时仍可启动、集群模式 database 非 0 不失败且有 ignored 诊断提示、配置切换、适配实现替换、日志脱敏和配置错误诊断。
- 文档必须明确客户封装 JAR 的合规限制：内部不能直接复现客户模式，产品交付的是适配能力和扩展点。
- 开发者指南应说明如何提供客户适配实现、如何选择 Redis endpoint type、如何确认启动日志以及如何避免在业务代码中直接依赖具体 Redis 客户端。
- L2 设计必须明确 DeepAgent Todolist 的 runtime/Core 协作边界：runtime 在 Task 下发和挂起边界写入或回灌 task-scoped opaque context snapshot；Core 在连续执行期通过注入接缝自治 save/load；runtime 不接管 TodoItem 领域模型。
- 测试必须覆盖 Todolist 持久化数据源：Task 下发/挂起/恢复边界写入和回灌、执行期 Core 复用 Redis 接缝自治 save/load、跨实例恢复读取、多个 Task 的 key 隔离，以及 runtime-hosted 场景不回退文件系统。
- 实现必须保证 Todolist Redis key 继承 keyPrefix，并包含 taskId 或等价 task-scoped 标识；不得仅使用 tenantId、agentId、conversationId 或 sessionId 作为最小隔离粒度。

## 7. 关联文档

- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L0-Top-Level-Design/glossary.md`
- `architecture/L1-High-Level-Design/agent-runtime/README.md`
- `architecture/L1-High-Level-Design/agent-runtime/development.md`
- `architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md`
- `version-scope/FEAT-001-standardized-agent-service-entrypoint.md`
- `version-scope/FEAT-004-remote-agent-orchestration.md`
- `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-001-standardized-agent-service-entrypoint.md`
- `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-003-agent-task-state-cache.md`
- `architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-004-remote-agent-orchestration.md`
