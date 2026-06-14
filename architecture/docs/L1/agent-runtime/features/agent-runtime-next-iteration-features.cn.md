---
level: L1
view: features
module: agent-runtime
status: planning
updated: 2026-06-14
authority: "v0.1.0 release checklist ⬜ items + agent-sdk/agent-service module analysis + third_party/fin-code reference"
covers: [agent-runtime增强, agent-sdk, agent-service, 多Agent编排, 记忆增强]
---

# spring-ai-ascend — 下一迭代特性清单（v0.2.0 候选）

> 本文档规划 v0.2.0 迭代的三大模块特性目标：agent-runtime（能力补齐）、agent-sdk（YAML 驱动 Agent 生成）、agent-service（开箱即用平台服务）。
> 优先级：P0 = 生产基线，P1 = 重要增强，P2 = 可延后。

---

## 一、agent-runtime — 能力补齐

v0.1.0 遗留的 ⬜ 项 + fin-code 参考增强。

### 1.1 P0 — 生产基线

- **非 Spring Boot 部署**：提供非 Spring Boot 的 `RuntimeHost` 实现，使 `RuntimeApp.create(handler).run(host)` 完整可用
- **AgentScope 补齐**：Checkpoint 适配 + Memory 适配，使 AgentScope 具备与 OpenJiuwen 同等的持久化和记忆能力
- **会话持久化（MySQL）**：替换 InMemoryTaskStore，支持超时回收、上下文窗口、父子消息线程。参考 fin-code SessionManagerService

### 1.2 P1 — 重要增强

- **轨迹生产化**：OTel 导出 / 北向投递 / TTFT 观测 / 采样率控制 / REASONING 独立事件 / 载荷外置 / 自定义脱敏
- **记忆增强**：中途检索 + 内置记忆 Tool + 双记忆架构（STM+LTM）+ 记忆压缩。参考 fin-code MemoryService / MemoryCompressionService
- **OpenJiuwen Workflow 适配**：支持 Workflow Agent
- **Redis 分布式 Checkpoint**：预置适配器
- **Push Notification / Webhook**：激活 SDK 推送通道
- **PDCA 多 Agent 编排**：Plan-Do-Check-Act 协作周期。参考 fin-code AgentProcessEngine
- **意图识别管道**：NER → Query 改写 → 意图分类 → Skill 映射。参考 fin-code IntentEngine

### 1.3 P2 — 可延后

- MCP 协议接入
- gRPC 传输
- Reactive 响应式接口（Flux/Mono）
- 知识检索 / RAG 集成
- 视觉 / 多模态 Agent
- AGUI / WebSocket 流式协议
- SDK / Client 库
- Skills 声明式定义的示例演示

---

## 二、agent-sdk — YAML 配置驱动 Agent 生成（新增大特性）

agent-sdk 已实现完整的 YAML→Agent 管道（`AgentSpec` → `AgentYamlLoader` → `OpenJiuwenReactAgentBuilder` → `ReActAgent`），当前作为独立模块存在，需集成到主构建并增强。

### 2.1 已有能力（待归档入发布清单）

| 能力 | 说明 |
|------|------|
| YAML 模式 `ascend-agent/v1` | 声明式定义 Agent 的模型、提示词、工具、技能 |
| 模型配置 | provider / name / baseUrl / apiKey / sslVerify / 自定义 headers |
| 系统提示词配置 | 文本或文件路径 |
| 工具（file 方案） | Java 静态方法，通过反射调用 |
| 工具（http 方案） | REST 端点，支持 POST/GET/HEAD/DELETE，JSON body、query params、超时 |
| 技能（filesystem） | 从目录加载 `SKILL.md` |
| 框架选项 | maxIterations、sysOperationId |
| 环境变量解析 | `${VAR_NAME}` 替换 |
| ReActAgent 构建 | `AgentFactory.toReactAgent(path)` 一行生成 |
| DeepAgent 构建 | `AgentFactory.toDeepAgent(path)` 一行生成 |

### 2.2 P0 — 生产基线

- **模块纳入主构建**：将 agent-sdk 加入父 POM `<modules>`，参与 CI/Maven 构建
- **Spring Boot 自动配置**：提供 `@EnableAgentSdk` 注解，自动扫描 YAML 文件并注册为 Handler Bean
- **与 agent-runtime 集成**：SDK 生成的 `ReActAgent` 可直接通过 `OpenJiuwenAgentRuntimeHandler` 挂载到 runtime 的 A2A 端点

### 2.3 P1 — 重要增强

- **Tool 类型扩展**：支持 A2A 远程 Agent Tool（将其他 A2A Agent 声明为 Tool）、MCP Tool
- **Skill 源扩展**：支持 HTTP Skill 源、数据库 Skill 源
- **YAML 模式升级 `ascend-agent/v2`**：支持多 Agent 配置（一个 YAML 定义多个 Agent）、Agent 间路由规则
- **配置校验**：启动时 fail-fast 校验 YAML schema、Tool 可达性、Skill 完整性

### 2.4 P2 — 可延后

- **热重载**：YAML 文件变更时自动重建 Agent，无需重启
- **图形化配置界面**：Web UI 编辑 Agent YAML

---

## 三、agent-service — 开箱即用的 Agent 平台服务（新增大特性）

agent-service 当前为空骨架。目标：结合 runtime 的 A2A 协议能力和 SDK 的 YAML 驱动 Agent 生成，提供一个用户可直接部署使用的 Agent 平台服务。

### 3.1 核心能力

- **一站式部署**：一个 `@SpringBootApplication` 启动完整的 Agent 服务平台
- **多 Agent 管理**：通过 YAML 配置文件声明多个 Agent，自动注册各自的 A2A 端点
- **Agent 生命周期管理**：启停、健康检查、配置热更新
- **内置管理 API**：Agent 列表、状态查询、配置查看
- **内置管理界面**：简单的 Web UI，展示 Agent 状态和调用统计

### 3.2 P0 — 生产基线

- **服务骨架**：`AgentServiceApplication` 主类 + 自动配置，集成 agent-runtime 和 agent-sdk
- **多 Agent 路由**：根据 `agentId` 将请求路由到对应 Handler（替代当前只取第一个 Handler 的限制）
- **统一配置模型**：`application.yaml` 中同时配置 runtime（A2A 端点、AgentCard）、SDK（YAML Agent 定义）、service（管理端点）
- **内置安全**：Basic Auth / API Key 认证

### 3.3 P1 — 重要增强

- **Agent 市场**：预置常用 Agent 模板（客服、数据分析、文档问答等），一键部署
- **调用统计与监控**：请求量、延迟、成功率、Token 消耗的 Dashboard
- **多租户支持**：租户级 Agent 隔离、配额管理

### 3.4 P2 — 可延后

- **Agent 编排工作流**：可视化拖拽编排多 Agent 协作流程
- **企业集成**：LDAP/SSO 认证、审计日志、合规报告

---

## 四、迭代完成度预估

| 模块 | P0 | P1 | P2 | 合计 |
|------|----|----|----|------|
| agent-runtime | 3 | 8 | 8 | 19 |
| agent-sdk | 3 | 4 | 2 | 9 |
| agent-service | 4 | 3 | 2 | 9 |
| **合计** | **10** | **15** | **12** | **37** |

---

## 五、参考来源

| 来源 | 参考特性 |
|------|---------|
| v0.1.0 ⬜ 项 | runtime 轨迹/Workflow/Redis/Webhook/非SpringBoot部署 等 |
| fin-code `AgentProcessEngine.java` (1302 行) | PDCA 多 Agent 编排 |
| fin-code `MemoryService.java` + `MemoryCompressionService.java` (~450 行) | 双记忆架构 + 记忆压缩 |
| fin-code `IntentEngine.java` (383 行) | 意图识别管道 |
| fin-code `SessionManagerService.java` (323 行) | 会话持久化 |
| agent-sdk 现有代码（~25 个 Java 文件） | YAML 驱动 Agent 生成 |
