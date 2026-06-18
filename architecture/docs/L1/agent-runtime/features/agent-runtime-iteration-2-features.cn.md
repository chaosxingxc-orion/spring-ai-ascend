---
level: L1
view: features
module: agent-runtime
status: planning
updated: 2026-06-15
authority: "v0.1.0 release baseline + agent-sdk module planning"
covers: [agent-runtime能力补齐, Skill Hub接入, DeepAgent, MCP协议接入, agent-sdk声明式Agent生成]
---

# spring-ai-ascend — 下一迭代特性清单（v0.2.0 候选）

> 本文档规划 v0.2.0 迭代的特性目标，覆盖 agent-runtime 和 agent-sdk 两个模块。
> ⬜ = 计划中/待实现。所有特性均为规划状态。

---

## 特性 1：agent-runtime 能力补齐与生态接入

在 v0.1.0 已实现能力基础上，接入 Skill Hub 和 MCP 生态，支持 DeepAgent 执行模式，补齐 OpenJiuwen Workflow 适配。

### 1.1 ⬜ Skill Hub 接入
- ⬜ Skill Hub 协议适配 — 通过标准协议接入远程 Skill 市场/仓库，自动发现可用技能
- ⬜ Skill 注册 — 从 Skill Hub 拉取技能列表，注册为 Agent 技能

### 1.2 ⬜ DeepAgent 支持
- ⬜ DeepAgent 适配器 — 新增 DeepAgent Adapter，支持进程内调用
- ⬜ DeepAgent 执行模型适配 — 将 DeepAgent 的多步骤推理、工具调用映射为 runtime 统一事件模型

### 1.3 ⬜ OpenJiuwen Workflow 适配
- ⬜ 支持多步骤 Workflow Agent 的托管和执行
- ⬜ Workflow 执行中断/恢复 — 需要人工确认的步骤自动挂起，输入后继续执行

### 1.4 ⬜ MCP 协议接入
- ⬜ MCP 工具接入 — 通过 MCP 协议连接外部工具服务器，Agent 自动发现并调用 MCP 工具

---

## 特性 2：agent-sdk — YAML 配置驱动 Agent 生成

开发者通过 YAML 配置文件声明 Agent 的模型连接、系统提示词、工具、技能和 MCP 服务器，SDK 自动构建可运行的 Agent 实例，并支持 DeepAgent 深度推理执行模式。从编写 Java 代码到编写 YAML 配置，降低 Agent 开发门槛。

### 2.1 ⬜ 核心配置能力

#### 2.1.1 ⬜ 模型配置
- ⬜ 接入模型服务 — 填写模型服务地址和凭证即可接入，凭证支持环境变量注入避免泄露

#### 2.1.2 ⬜ 提示词配置
- ⬜ 设置系统提示词 — 定义 Agent 的角色、行为规则和回答风格

#### 2.1.3 ⬜ 工具配置
- ⬜ 接入外部 API 工具 — 描述接口地址和调用方式，Agent 即可自动调用
- ⬜ 接入本地代码工具 — 注册已有的 Java 方法为 Agent 可用工具

#### 2.1.4 ⬜ 技能配置
- ⬜ 加载技能文件 — 指定技能文件目录，SDK 自动扫描并注册为 Agent 技能

### 2.2 ⬜ MCP (Model Context Protocol) 接入
- ⬜ 接入 MCP Server — 填写 MCP Server 地址和认证信息，Agent 自动发现并调用 MCP 工具

### 2.3 ⬜ DeepAgent 支持
- ⬜ 启用深度推理模式 — Agent 支持多步骤推理和工具调用，自主规划和执行复杂任务
