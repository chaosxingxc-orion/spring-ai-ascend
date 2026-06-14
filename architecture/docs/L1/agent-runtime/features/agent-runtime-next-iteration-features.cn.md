---
level: L1
view: features
module: agent-runtime
status: planning
updated: 2026-06-14
authority: "v0.1.0 release checklist ⬜ items + agent-sdk/agent-service module analysis + third_party/fin-code reference"
covers: [agent-runtime能力补齐, agent-sdk声明式Agent, agent-service平台服务]
---

# spring-ai-ascend — 下一迭代特性清单（v0.2.0 候选）

> 本文档规划 v0.2.0 迭代的三大模块特性目标：agent-runtime（能力补齐）、agent-sdk（YAML 驱动 Agent 生成）、agent-service（开箱即用平台服务）。

---

## 特性 1：agent-runtime 能力补齐

### 1.1 能力描述

在 v0.1.0 已实现的 Agent 托管、A2A 协议、轨迹可观测、远程编排等核心能力基础上，
补齐生产基线必备的持久化、隔离、跨框架适配能力，并将轨迹系统从"可用"提升到"生产级"。

### 1.2 功能要求

**部署与运维**

- 非 Spring Boot 部署：提供不依赖 Spring Boot 的 `RuntimeHost` 实现，`RuntimeApp.create(handler).run(host)` 完整可用
- 会话持久化：Task 和 Session 支持 MySQL 持久化存储，支持超时回收、上下文窗口滑动。参考 fin-code SessionManagerService

**AgentScope 适配补齐**

- AgentScope Checkpoint 适配：支持 AgentScope Agent 的状态持久化（checkpoint/save/restore）
- AgentScope Memory 适配：AgentScope Handler 接入 `MemoryProvider` SPI，支持跨会话记忆检索与保存

**轨迹生产化**

- OpenTelemetry 导出（OTLP）：代码已有 `OtelSpanSink`，补齐 example 验证和文档
- 轨迹数据对调用方可见：`trajectory.northbound=true` 能力代码已有，补齐 example 验证
- 首 Token 延迟（TTFT）观测：`MODEL_CALL_FIRST_TOKEN` 枚举已存在，Adapter 实际发射
- 采样率控制：实现采样门控逻辑
- LLM 推理过程（REASONING）独立事件记录
- 大载荷外置存储（`payload_ref://`）
- 自定义脱敏逻辑注入（Redactor SPI）

**记忆增强**

- 双记忆架构（STM + LTM）：短期轮次内记忆 + 长期跨会话持久化记忆。参考 fin-code MemoryService + MemoryContext
- 记忆压缩：LLM 驱动的 STM→LTM 摘要压缩，自动控制上下文窗口。参考 fin-code MemoryCompressionService
- 记忆中途检索：Agent 推理过程中按需检索记忆，替代当前"轮次开始前一次性注入"
- 内置记忆 Tool：Agent 可在对话中主动调用记忆读写

**编排与意图**

- PDCA 多 Agent 编排：Plan-Do-Check-Act 协作周期，Plan Agent 生成步骤计划 → Do Agent 执行 → Check Agent 审查 → Act Agent 修正。参考 fin-code AgentProcessEngine（1302 行完整实现）
- 意图识别管道：NER 实体识别 → Query 改写（语义补全、术语标准化）→ 意图分类（LLM + 规则回退）→ Skill 映射。参考 fin-code IntentEngine

**其他补齐**

- OpenJiuwen Workflow 适配：当前仅支持 Core Agent，Workflow Agent 需支持
- Redis 分布式 Checkpoint 预置适配：开箱即用的 Redis Checkpointer
- Push Notification / Webhook：激活 A2A SDK 推送通道，Task 完成后主动回调
- Reactive 响应式接口（Flux / Mono）：OpenJiuwen Core 下个版本支持后适配
- MCP (Model Context Protocol) 协议接入：新增 MCP Adapter，连接工具生态
- gRPC 传输协议：与 HTTP/SSE 并存
- 知识检索 / RAG 集成：多知识库接入，每个知识库作为独立 Tool。参考 fin-code KnowledgeRetrievalTool
- 视觉 / 多模态 Agent：支持图片分析，base64 + 多模态模型。参考 fin-code ImageAgent
- AGUI / WebSocket 流式协议：双向流式通信，细粒度事件（toolCallStart/Args/End/textMessageStart）
- SDK / Client 库：封装 A2A Java SDK 调用

---

## 特性 2：agent-sdk — YAML 配置驱动 Agent 生成

### 2.1 能力描述

agent-sdk 提供声明式的 Agent 生成方式——开发者编写 YAML 配置文件描述 Agent 的模型连接、
系统提示词、可用工具和技能，SDK 自动构建可运行的 Agent 实例。开发者的角色从"编写 Java 代码
创建 Agent"转变为"编写 YAML 描述 Agent"，大幅降低 Agent 开发门槛。

当前 agent-sdk 已实现完整的 YAML→Agent 管道（`ascend-agent/v1` 模式 → `AgentSpec` →
`AgentYamlLoader` → `OpenJiuwenReactAgentBuilder` → `ReActAgent`），作为独立模块存在。
下一迭代将其集成到主构建体系，并与 agent-runtime 的 Handler SPI 打通，使 YAML 定义的
Agent 可直接通过 A2A 端点对外暴露。

### 2.2 功能要求

**模块集成**

- 将 agent-sdk 纳入父 POM `<modules>`，参与 CI 和 Maven 构建
- 提供 Spring Boot 自动配置（`@EnableAgentSdk`）：自动扫描 classpath 下的 Agent YAML 文件，构建 Agent 实例并注册为 Handler Bean
- 与 agent-runtime 集成：SDK 生成的 `ReActAgent` 通过 `OpenJiuwenAgentRuntimeHandler` 挂载到 runtime 的 A2A 端点，对外暴露

**YAML 配置能力（已有，需归档）**

- 模型配置：provider、name、baseUrl、apiKey、sslVerify、自定义 headers
- 系统提示词：文本或文件路径
- 工具声明：`file:` 方案（Java 静态方法，反射调用）和 `http:` 方案（REST 端点，POST/GET/HEAD/DELETE，支持 JSON body、query params、超时）
- 技能声明：`filesystem` 源，从目录加载 `SKILL.md`
- 框架选项：maxIterations、sysOperationId
- 环境变量解析：`${VAR_NAME}` 语法
- ReActAgent 和 DeepAgent 双框架支持

**YAML 配置增强**

- Tool 类型扩展：支持 A2A 远程 Agent Tool（将其他 A2A Agent 声明为 Tool）、MCP Tool
- Skill 源扩展：HTTP Skill 源、数据库 Skill 源
- 多 Agent 配置：一个 YAML 文件定义多个 Agent，声明各自的 agentId 和路由规则
- 启动时配置校验：fail-fast 校验 YAML schema、Tool 可达性、Skill 完整性
- 热重载：YAML 文件变更时自动重建 Agent，无需重启

---

## 特性 3：agent-service — 开箱即用的 Agent 平台服务

### 3.1 能力描述

agent-service 结合 agent-runtime 的 A2A 协议能力和 agent-sdk 的声明式 Agent 生成能力，
提供一个用户可直接部署使用的 Agent 平台服务。用户只需编写 YAML 配置文件描述 Agent，
启动一个 Spring Boot 应用，即可获得完整的多 Agent 服务平台——包括 A2A 协议端点、
Agent 管理界面、调用监控和安全认证。

### 3.2 功能要求

**服务骨架**

- 一键启动：`@SpringBootApplication` 主类，自动集成 runtime 和 SDK 的全部能力
- 多 Agent 路由：根据 `agentId` 将 A2A 请求路由到对应的 Handler，替代当前"只取第一个 Handler"的限制
- 统一配置模型：`application.yaml` 中集中配置 runtime（A2A 端点、AgentCard）、SDK（Agent YAML 路径）、service（管理端点、安全）

**Agent 管理**

- Agent 生命周期管理：启停、健康检查、配置查看
- 内置管理 API：Agent 列表、状态查询
- 内置管理界面：Web UI 展示 Agent 状态
- 多租户支持：租户级 Agent 隔离、配额管理

**内置安全**

- API Key 认证
- Basic Auth 认证

**运营能力**

- 调用统计与监控：请求量、延迟、成功率、Token 消耗 Dashboard
- Agent 模板市场：预置常用 Agent 模板（客服、数据分析、文档问答等）

---

## 特性 4：Agent 编排工作流

### 4.1 能力描述

在单 Agent 调用和远程 Agent Tool 调用的基础上，支持结构化的多 Agent 协作流程。
通过可视化的编排方式定义 Agent 之间的协作关系——串行、并行、条件分支、循环——
形成完整的 Agent 工作流。

### 4.2 功能要求

- PDCA 多 Agent 协作：Plan-Do-Check-Act 周期，多个 Agent 分工协作完成复杂任务
- 意图识别管道：用户消息先经过意图识别（NER→改写→分类→Skill 映射），再路由到对应 Agent
- 工作流编排：可视化拖拽编排多 Agent 协作流程，定义串行/并行/条件/循环节点
- 跨 Agent 审计链：Merkle-chained Decision BOM，追踪每一步决策的来源和影响
