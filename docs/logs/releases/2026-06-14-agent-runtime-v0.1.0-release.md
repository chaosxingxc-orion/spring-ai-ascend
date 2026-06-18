---
formal_release: true
release_candidate_branch: release/v0.1.0
status: formal-release-candidate
---

# agent-runtime v0.1.0 Release Notes

> Release date: 2026-06-14
> Version: v0.1.0
> Artifact: `agent-runtime-0.1.0.jar`

---

## 一、v0.1.0 发布特性清单

本次发布为 agent-runtime 首个功能版本。agent-runtime 是框架中立的 Agent 托管运行时 SDK，提供统一的 `AgentRuntimeHandler` SPI 接入异构 Agent 框架，通过 Google A2A 协议对外暴露 Agent 端点，记录执行轨迹，并支持远程 A2A Agent 编排调用。

> ✅ = 已实现，⬜ = 计划中/未实现

### 1. 异构 Agent 框架兼容

通过统一的 Adapter 抽象接入不同类型的 Agent 实现，上层调用方无需感知底层 Agent 框架差异。

**1.1 ✅ OpenJiuwen 适配器**

- ✅ 进程内直接调用 `openjiuwen-agent-core-java`，低延迟同进程执行
- ✅ Rails 注入机制，支持三种扩展点：
  - 轨迹追踪 Rail — 自动捕获模型调用/工具调用的全链路事件
  - 远程工具中断 Rail — 将远程 A2A Agent 调用拦截为可恢复的中断点
  - 记忆注入 Rail — 在每次调用前后自动检索和保存长期记忆
- ✅ Agent 执行状态持久化（Checkpoint），支持 InMemory / SQLite / 自定义后端
- ⬜ OpenJiuwen Workflow 适配（当前仅支持 Core Agent，不支持 Workflow）

**1.2 ✅ AgentScope 适配器**

- ✅ 进程内直接调用 AgentScope Agent
- ✅ 三种运行模式：
  - 本地 Agent — 进程内直接调用
  - Harness Agent — 测试/评估场景下的受控运行
  - 远程 SSE 客户端 — 连接远程 AgentScope Runtime 实例
- ✅ AgentScope 错误码到标准 ErrorCategory 的自动映射

**1.3 ✅ Versatile REST 代理适配器**

- ✅ 通过 RESTful API 代理任意远程非 A2A Agent（workflow 引擎、自定义 Agent 服务等）
- ✅ URL 模板机制（占位符替换 + 静态查询参数），适配不同远程 API 规范
- ✅ 两级 Header 透传模型：YAML 预配置 + A2A 客户端动态传递
- ✅ 结果提取规则引擎（关键字匹配 → 深层 key 查找），从非标准响应中提取结构化结果

**1.4 ✅ Adapter 抽象层**

- ✅ 统一的 `AgentRuntimeHandler` SPI — 所有 Adapter 对外暴露一致的调用语义
- ✅ 新增 Agent 框架只需实现一个 Adapter，不修改 runtime 核心
- ⬜ MCP (Model Context Protocol) 协议接入（当前仅支持 Java 进程内 + HTTP/SSE）

### 2. 中间件解耦 — Memory / State

Agent 执行的通用基础设施（记忆、状态持久化）以可注入、可替换的中间件形式由 runtime 统一提供。

**2.1 ✅ 记忆服务**

- ✅ 框架无关的 `MemoryProvider` SPI（init / search / save）— 切换存储后端不影响 Agent 代码
- ✅ 预置 OpenJiuwen 记忆集成（每次调用自动检索 + 对话结束后自动写回），可替换为自定义实现
- ⬜ 记忆与提示词协同（当前仅在 ReAct 轮次开始前注入，不支持中途检索）
- ⬜ 记忆工具（Agent 在对话过程中主动读写记忆）
- ⬜ 异构框架记忆适配（仅 OpenJiuwen 已接入，AgentScope 尚未支持）

**2.2 ✅ 状态持久化**

- ✅ OpenJiuwen Agent 执行状态 Checkpoint（支持 InMemory / SQLite）
- ⬜ Redis 分布式 Checkpoint 预置适配
- ⬜ AgentScope Agent Checkpoint 适配

### 3. 三种 S2C（Server-to-Client）通讯模型

A2A 协议层统一对外暴露三种通讯模式，各 Adapter 按自身执行模型适配。

**3.1 ✅ 同步（Blocking）**

- ✅ A2A `SendMessage` — 发送消息，等待完整结果后返回 JSON
- 由 A2A 协议层将 Handler 的 Stream 输出收集后一次性返回，Handler 本身始终以 Stream 方式产出

**3.2 ✅ 流式（Streaming）**

- ✅ A2A `SendStreamingMessage` — 通过 SSE 推送结果
- ✅ SSE 流在终端状态下正确关闭
- ✅ 断线重连订阅（`SubscribeToTask`）
- ⬜ Reactive 响应式接口（Flux / Mono），OpenJiuwen Core 下个版本支持

**3.3 ✅ 异步（Async）**

- ✅ `GetTask` — 提交任务后立即返回 taskId，后续按 taskId 轮询结果
- ✅ `CancelTask` — 取消任务（OpenJiuwen 仅阻止结果消费，不中断 Agent 执行；远程 Agent 支持级联取消）
- ✅ `ListTasks` — 查询任务列表
- ✅ 完整 Task 生命周期：SUBMITTED → WORKING → COMPLETED / FAILED / CANCELED / INPUT_REQUIRED
- ⬜ Push Notification / Webhook（任务完成后主动回调）

**3.4 ⬜ gRPC 传输**

- ⬜ gRPC 传输协议（A2A v0.3 已加入，当前仅 HTTP + SSE）

### 4. A2A 协议标准支持（北向）

Runtime 对外暴露符合 Google A2A 协议标准的服务端点，任意 A2A 客户端均可通过标准化 JSON-RPC 接口发现和调用 Agent。

**4.1 ✅ A2A Methods 全覆盖**

- ✅ `SendMessage` — 同步消息
- ✅ `SendStreamingMessage` — 流式消息（SSE）
- ✅ `GetTask` — 异步任务查询
- ✅ `CancelTask` — 任务取消
- ✅ `ListTasks` — 任务列表
- ✅ `SubscribeToTask` — 断线重连恢复订阅
- ✅ Agent Card 端点（`/.well-known/agent-card.json` + `/.well-known/agent.json`）

**4.2 ✅ Agent Card YAML 配置**

- ✅ YAML 配置驱动的 AgentCard 自动生成
- ✅ 声明式能力描述：skills、capabilities、version — 支持 Handler 自动提供 + YAML 全量覆盖

### 5. 轨迹可观测性（Trajectory）

框架中立的 Agent 执行轨迹系统 — 记录每次调用的执行过程，支持输出到 A2A 调用方。

**5.1 ✅ 执行轨迹记录**

- ✅ 框架中立的事件模型：各 Adapter 自动记录调用过程，runtime 统一完成时间戳、序列号、Span 嵌套树
- ✅ 已覆盖的事件类型：
  - RUN_START / RUN_END — 调用边界（所有 Adapter）
  - MODEL_CALL_START / MODEL_CALL_END — 模型调用，含 tokens、延迟、模型名（OpenJiuwen）
  - TOOL_CALL_START / TOOL_CALL_END — 工具调用（OpenJiuwen、AgentScope）
  - ERROR — 执行错误（所有 Adapter）
  - PROGRESS — 进度更新（AgentScope）
- ✅ 父-子 Agent 调用链路追踪（parentTaskId / parentTraceId 传递）

**5.2 ✅ 敏感信息掩码**

- ✅ 轨迹和日志中匹配 key / token / secret / password 等模式的字段自动掩码
- ✅ 掩码规则可配置（`app.trajectory.mask.*`）

**5.3 ⬜ 轨迹能力待补**

- ⬜ 轨迹数据对调用方可见（设置 `trajectory.northbound=true` 返回轨迹）
- ⬜ OpenTelemetry 导出（OTLP）
- ⬜ 首 Token 延迟（TTFT）观测
- ⬜ LLM 推理过程（REASONING）记录
- ⬜ 采样率控制
- ⬜ 大载荷外置存储
- ⬜ 自定义脱敏逻辑注入

### 6. 远程 Agent 编排（A2A 南向/出站）

Runtime 作为 A2A 客户端接入和调用其他 A2A Agent，实现跨 Agent 协作。

**6.1 ✅ 远程 Agent 配置接入**

- ✅ 通过 YAML 配置远程 A2A 端点，自动拉取 Agent Card 并维护本地目录
- ✅ 自适应刷新策略 — 快速恢复（10s 间隔）+ 长保持（600s），指数退避 + 随机抖动
- ✅ 自动从 Agent Card 的 Skills 生成 RemoteAgentToolSpec
- ✅ 每个远程端点可独立配置 stream timeout
- ✅ 故障远程 Agent 优雅降级 — 从目录标记不可用，后台自动重试

**6.2 ✅ 远程调用**

- ✅ 每次调用独立 streaming，支持超时检测与自动取消
- ✅ 远程 Agent 中断续接 — 远程 Agent 返回输入请求时，父 Task 自动挂起等待并恢复
- ✅ 远程执行进度实时投射到父 Task
- ✅ 入站 A2A metadata 自动转发到出站远程调用

### 7. 运维就绪

**7.1 ✅ 生命周期管理**

- ✅ 完整的 start → serve → stop → drain 生命周期
- ✅ 优雅停机 — drain 等待进行中请求完成
- ✅ 就绪门控 — runtime 完全就绪后才接受请求

**7.2 ✅ 健康检查**

- ✅ Actuator Health Indicator — 每个 Handler 独立报告健康状态（UP / OFF_OF_SERVICE / DOWN）
- ✅ 远程 Agent 目录健康状态展示（available / pending / unreachable）

**7.3 ✅ 日志与诊断**

- ✅ MDC 日志关联 — 每次请求自动注入 contextId / taskId / tenantId / agentId，便于日志追踪
- ✅ 错误码分类 — 自动将异常归类为 INVALID_INPUT / TIMEOUT / UPSTREAM_UNAVAILABLE / CANCELLED / INTERNAL，标注可重试性

**7.4 ✅ 嵌入式部署**

- ✅ `RuntimeApp.create(handler).run(host)` 简洁启动 API（仅封装 Handler 调用，丢失 A2A 协议端点、Task 生命周期管理、远程 Agent 编排等能力）
- ⬜ 非 Spring Boot 部署（当前仅 `LocalA2aRuntimeHost` 基于 Spring Boot 实现）

### 8. 开发者体验

**8.1 ✅ Spring Boot 内嵌**

- ✅ 一个 `@SpringBootApplication` + `application.yaml` 即可启动，自动装配 A2A 端点、AgentCard、Health

**8.2 ✅ 声明式 Agent 配置**

- ✅ YAML 驱动 AgentCard：名称、描述、版本、组织、endpoint 等元信息可配置
- ✅ 不配置时自动从 Handler 的 agentId 生成 AgentCard

**8.3 ✅ 示例与文档**

- ✅ 开箱即用的 E2E 示例项目（openjiuwen-simple、a2a-llm-e2e、a2a-versatile-e2e、remote-agent-tool-e2e 等 11 个）
- ✅ 中文开发者指南

**8.4 ⬜ 开发者体验待补**

- ⬜ SDK / Client 库（调用方快速接入 A2A）
- ⬜ Skills / Capabilities 声明式定义的示例演示

### 附录：已支持特性关联资产

| 特性 | 设计文档 | 开发指导 | Example |
|------|---------|---------|--------|
| **1.1 OpenJiuwen 适配器** | | | |
| 进程内直接调用 | architecture/docs/L2/agent-runtime/heterogeneous-agent-framework-compatibility.md §4.2 | agent-runtime/docs/guides/openjiuwen-adapter.md | examples/agent-runtime-openjiuwen-simple/ |
| Rails 注入：轨迹追踪 | 同上 §4.2.2；trajectory-observability-design.md §4.2 | agent-runtime/docs/guides/openjiuwen-adapter.md | examples/agent-runtime-a2a-openjiuwen-e2e/ |
| Rails 注入：远程工具中断 | 同上 §4.2.2；remote-agent-orchestration-design.md §4.2 | openjiuwen-adapter.md, remote-invocation.md | examples/agent-runtime-a2a-remote-agent-tool-e2e/ |
| Rails 注入：记忆注入 | 同上 §4.2.2；middleware-services-design.md §4.1.1 | openjiuwen-adapter.md, memory-services.md | examples/agent-runtime-middleware-memory-inmemory/ |
| Checkpoint 持久化 | 同上 §4.2.3；middleware-services-design.md §4.2 | state-persistence.md | examples/agent-runtime-middleware-state-inmemory/ |
| **1.2 AgentScope 适配器** | | | |
| 进程内调用 + 三种模式 | heterogeneous-agent-framework-compatibility.md §4.3 | agentscope-adapter.md | — |
| 错误码自动映射 | 同上 §4.3.2 | agentscope-adapter.md | — |
| **1.3 Versatile 适配器** | | | |
| REST API 代理 | heterogeneous-agent-framework-compatibility.md §4.4 | versatile-adapter.md | examples/agent-runtime-a2a-versatile-e2e/ |
| URL 模板 + Header 透传 + 结果提取 | 同上 §4.4.1-§4.4.3 | versatile-adapter.md | examples/agent-runtime-a2a-versatile-parent-e2e/ |
| **1.4 Adapter 抽象层** | | | |
| AgentRuntimeHandler SPI | heterogeneous-agent-framework-compatibility.md §2.3 | handler-spi.md | examples/agent-runtime-openjiuwen-simple/ |
| **2.1 记忆服务** | | | |
| MemoryProvider SPI | middleware-services-design.md §2.3 | memory-services.md | examples/agent-runtime-middleware-memory-inmemory/ |
| OpenJiuwen 记忆集成 | 同上 §4.1.1 | memory-services.md, openjiuwen-adapter.md | examples/agent-runtime-middleware-memory-inmemory/ |
| **2.2 状态持久化** | | | |
| OpenJiuwen Checkpoint | middleware-services-design.md §4.2 | state-persistence.md | examples/agent-runtime-middleware-state-inmemory/ |
| **3.1-3.3 S2C 通讯模型** | | | |
| SendMessage / SendStreamingMessage | a2a-protocol-and-communication-design.md §4.2 | a2a-endpoints.md | examples/agent-runtime-a2a-llm-e2e/ |
| GetTask / CancelTask / ListTasks | 同上 §4.1-§4.2 | a2a-endpoints.md | examples/agent-runtime-a2a-external-access-e2e/ |
| SubscribeToTask 断线重连 | 同上 §4.1 | a2a-endpoints.md | examples/agent-runtime-a2a-external-access-e2e/ |
| Task 生命周期 | 同上 §4.2 | a2a-endpoints.md | examples/agent-runtime-a2a-llm-e2e/ |
| **4.1 A2A Methods** | | | |
| 6 种 A2A 方法全覆盖 | a2a-protocol-and-communication-design.md §4.1 | a2a-endpoints.md | examples/agent-runtime-a2a-external-access-e2e/ |
| Agent Card 端点 | 同上 §4.4 | agent-card-configuration.md | examples/agent-runtime-openjiuwen-simple/ |
| **4.2 Agent Card 配置** | | | |
| YAML 驱动 + skills/capabilities 声明 | a2a-protocol-and-communication-design.md §2.3, §4.4, §5 | agent-card-configuration.md | examples/agent-runtime-openjiuwen-simple/ |
| **5.1 执行轨迹记录** | | | |
| 框架中立事件模型 + stamping | trajectory-observability-design.md §4.1 | trajectory-observability.md | — |
| 事件类型（RUN/MODEL_CALL/TOOL_CALL/ERROR/PROGRESS） | 同上 §4.3 | trajectory-observability.md | — |
| 父-子链路追踪 | 同上 §2.1 | trajectory-observability.md | examples/agent-runtime-a2a-remote-agent-tool-e2e/ |
| **5.2 敏感信息掩码** | | | |
| 敏感字段掩码 + 规则可配置 | trajectory-observability-design.md §4.1, §5 | trajectory-observability.md, configuration-properties.md | — |
| **6.1 远程 Agent 配置接入** | | | |
| YAML 配置 + Card 缓存 + 自适应刷新 | remote-agent-orchestration-design.md §4.1 | remote-invocation.md | examples/agent-runtime-a2a-remote-openjiuwen-e2e/ |
| Card Skills → RemoteAgentToolSpec | 同上 §4.1；a2a-protocol-and-communication-design.md §4.4 | remote-invocation.md | examples/agent-runtime-a2a-remote-agent-tool-e2e/ |
| 独立 timeout + 故障降级 | 同上 §5, §7 | remote-invocation.md | examples/agent-runtime-a2a-remote-openjiuwen-e2e/ |
| **6.2 远程调用** | | | |
| 独立 streaming + 超时取消 | remote-agent-orchestration-design.md §4.2 | remote-invocation.md | examples/agent-runtime-a2a-remote-agent-tool-e2e/ |
| 中断-续接 | 同上 §4.3 | remote-invocation.md | examples/agent-runtime-a2a-versatile-parent-e2e/ |
| 进度投射 + metadata 转发 | 同上 §4.2 | remote-invocation.md | examples/agent-runtime-a2a-remote-agent-tool-e2e/ |
| **7.1 生命周期管理** | | | |
| start → drain + 优雅停机 + 就绪门控 | a2a-protocol-and-communication-design.md §4.3 | operations-guide.md | — |
| **7.2 健康检查** | | | |
| Handler + 远程 Agent 目录健康状态 | — | operations-guide.md | — |
| **7.3 日志与诊断** | | | |
| MDC 日志关联 + 错误码分类 | a2a-protocol-and-communication-design.md §4.3, §7 | operations-guide.md | — |
| **7.4 嵌入式部署** | | | |
| RuntimeApp 简洁启动 API | — | operations-guide.md | — |
| **8.1 Spring Boot 内嵌** | | | |
| @SpringBootApplication 一键启动 | heterogeneous-agent-framework-compatibility.md §6 | openjiuwen-adapter.md | examples/agent-runtime-openjiuwen-simple/ |
| **8.2 声明式 Agent 配置** | | | |
| YAML AgentCard + 自动生成 | a2a-protocol-and-communication-design.md §5 | agent-card-configuration.md | examples/agent-runtime-openjiuwen-simple/ |
| **8.3 示例与文档** | | | |
| 11 个 E2E 示例 + 中文指南 | — | agent-runtime/docs/guides/ | examples/ |

---

## 二、下一迭代计划（v0.2.0 候选）

### 特性 1：agent-runtime 能力补齐

**1.1 能力描述**

在 v0.1.0 已实现的核心能力基础上，补齐工作流支持、外部协议接入、可观测性最佳实践和自研记忆服务。

**1.2 功能要求**

- OpenJiuwen Workflow 适配：支持多步骤 Workflow Agent 的创建和执行
- MCP (Model Context Protocol) 协议接入：新增 MCP Adapter，支持 MCP 工具的发现和调用，连接 Agent 到外部工具生态
- 完善日志轨迹记录：输出结构化轨迹日志，提供生产环境下的轨迹收集、存储和查询最佳实践
- 支持自研记忆服务：提供记忆服务的标准接入方式，支持短期会话记忆和长期记忆检索，按用户和会话隔离

### 特性 2：agent-sdk — YAML 配置驱动 Agent 生成

**2.1 能力描述**

开发者通过 YAML 配置文件声明 Agent 的模型连接、系统提示词、工具和技能，SDK 自动构建可运行的 Agent 实例。从编写 Java 代码到编写 YAML 配置，降低 Agent 开发门槛。

**2.2 功能要求**

- 模型配置：YAML 中声明 LLM 连接信息（provider、apiKey、baseUrl、modelName）
- 提示词配置：YAML 中声明系统提示词，支持文件引用和环境变量注入
- 工具配置：YAML 中声明 Agent 可用工具，支持 HTTP 接口工具和本地 Java 方法工具
- 技能配置：YAML 中声明 Agent 技能目录，自动加载技能描述文件
- 与 runtime 集成：YAML 定义的 Agent 自动注册为 runtime 的 Handler，通过 A2A 端点对外暴露
- 启动校验：启动时校验 YAML 文件的 schema 正确性和工具可达性

### 特性 3：agent-service — 开箱即用的 Agent 平台服务

**3.1 能力描述**

结合 agent-runtime 的 A2A 协议能力和 agent-sdk 的声明式 Agent 生成能力，提供一个可直接部署的 Agent 平台服务。用户编写 YAML 配置文件描述 Agent，启动服务即获得完整的 A2A 协议端点和 Agent 管理能力。

**3.2 功能要求**

- 一键部署：一个 Spring Boot 应用启动即用，自动集成 A2A 协议端点和 Agent 管理
- YAML 驱动：通过 YAML 配置文件声明 Agent，无需编写 Java 代码
- Agent 管理 API：提供 Agent 列表、状态查询、启停控制的管理接口

---

## 三、致谢

感谢以下贡献者在本版本中的代码、示例和文档贡献。

**agent-runtime 模块**：Kevin-708090、Kevin Hu、Chao Xing、chaosxingxc-orion、yougq、x00209170、Euphoria Yan、yansuqing、Suqing Yan

**Examples 模块**：Kevin-708090、Kevin Hu、yougq、x00209170、chaosxingxc-orion、yansuqing、Euphoria Yan、xuefanfan-cmd、Chao Xing、nickylba、caikongerbanhzz-ui、Suqing Yan

**文档**：Kevin-708090、Chao Xing、chaosxingxc-orion、yougq、x00209170、yansuqing、Euphoria Yan、LucioIT
