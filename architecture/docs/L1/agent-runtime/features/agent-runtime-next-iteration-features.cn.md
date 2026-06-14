---
level: L1
view: features
module: agent-runtime
status: planning
updated: 2026-06-14
authority: "v0.1.0 release checklist ⬜ items + agent-sdk/agent-service module planning"
covers: [agent-runtime, agent-sdk, agent-service]
---

# spring-ai-ascend — 下一迭代特性清单（v0.2.0 候选）

> 本文档规划 v0.2.0 迭代的特性目标，覆盖 agent-runtime、agent-sdk、agent-service 三个模块。

---

## 特性 1：agent-runtime 能力补齐

### 1.1 能力描述

在 v0.1.0 已实现的核心能力基础上，补齐工作流支持、外部协议接入、可观测性最佳实践和自研记忆服务。

### 1.2 功能要求

- OpenJiuwen Workflow 适配：支持多步骤 Workflow Agent 的创建和执行
- MCP (Model Context Protocol) 协议接入：新增 MCP Adapter，支持 MCP 工具的发现和调用，连接 Agent 到外部工具生态
- 完善日志轨迹记录：输出结构化轨迹日志，提供生产环境下的轨迹收集、存储和查询最佳实践
- 支持自研记忆服务：提供记忆服务的标准接入方式，支持短期会话记忆和长期记忆检索，按用户和会话隔离

---

## 特性 2：agent-sdk — YAML 配置驱动 Agent 生成

### 2.1 能力描述

开发者通过 YAML 配置文件声明 Agent 的模型连接、系统提示词、工具和技能，SDK 自动构建可运行的 Agent 实例。从编写 Java 代码到编写 YAML 配置，降低 Agent 开发门槛。

### 2.2 功能要求

- 模型配置：YAML 中声明 LLM 连接信息（provider、apiKey、baseUrl、modelName）
- 提示词配置：YAML 中声明系统提示词，支持文件引用和环境变量注入
- 工具配置：YAML 中声明 Agent 可用工具，支持 HTTP 接口工具和本地 Java 方法工具
- 技能配置：YAML 中声明 Agent 技能目录，自动加载技能描述文件
- 与 runtime 集成：YAML 定义的 Agent 自动注册为 runtime 的 Handler，通过 A2A 端点对外暴露
- 启动校验：启动时校验 YAML 文件的 schema 正确性和工具可达性

---

## 特性 3：agent-service — 开箱即用的 Agent 平台服务

### 3.1 能力描述

结合 agent-runtime 的 A2A 协议能力和 agent-sdk 的声明式 Agent 生成能力，提供一个可直接部署的 Agent 平台服务。用户编写 YAML 配置文件描述 Agent，启动服务即获得完整的 A2A 协议端点和 Agent 管理能力。

### 3.2 功能要求

- 一键部署：一个 Spring Boot 应用启动即用，自动集成 A2A 协议端点和 Agent 管理
- YAML 驱动：通过 YAML 配置文件声明 Agent，无需编写 Java 代码
- Agent 管理 API：提供 Agent 列表、状态查询、启停控制的管理接口
