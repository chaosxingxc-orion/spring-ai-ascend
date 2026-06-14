# agent-runtime v0.1.0 Release Notes

> Release date: 2026-06-14
> Version: v0.1.0
> Artifact: `agent-runtime-0.1.0.jar`

---

## 一、v0.1.0 发布特性

本次发布为 agent-runtime 首个功能版本，提供框架中立的 Agent 托管运行时能力。

### 1. 异构 Agent 框架兼容

通过统一的 `AgentRuntimeHandler` SPI 接入三种 Agent 框架，上层 A2A 协议层无需感知底层差异。

- **OpenJiuwen 适配器**：进程内直接调用，Rails 注入机制（轨迹追踪、远程工具中断、记忆注入），支持 InMemory / SQLite Checkpoint
- **AgentScope 适配器**：三种运行模式（本地 Agent、Harness Agent、远程 SSE 客户端），错误码自动映射
- **Versatile REST 代理适配器**：协议转换代理，URL 模板、Header 透传、结果提取规则引擎

### 2. 中间件解耦 — Memory / State

通用基础设施能力以可注入、可替换的中间件形式统一提供。

- **Memory 服务**：框架无关的 `MemoryProvider` SPI，预置 OpenJiuwen 记忆集成
- **State 持久化**：OpenJiuwen Checkpoint（InMemory / SQLite），通过 `CheckpointerFactory` 全局配置

### 3. S2C 通讯模型 + A2A 协议

- **阻塞请求-响应**：`SendMessage`，A2A 层收集 Stream 后一次性返回 JSON
- **流式 SSE**：`SendStreamingMessage`，支持 `SubscribeToTask` 断线重连
- **异步**：`GetTask` / `CancelTask` / `ListTasks`，完整 Task 生命周期
- **A2A Methods**：6 种方法全覆盖 + Agent Card 端点 + YAML 驱动 AgentCard 配置

### 4. 轨迹可观测性

框架中立的执行轨迹系统，记录模型调用、工具调用、错误等事件。

- 事件模型：8 种 Kind 类型（RUN、MODEL_CALL、TOOL_CALL、ERROR、PROGRESS）
- 敏感信息掩码：可配置正则 + 截断阈值
- Adapter 覆盖矩阵：OpenJiuwen（5 种）+ AgentScope（4 种）

### 5. 远程 Agent 编排

作为 A2A 客户端接入和调用其他 A2A Agent。

- YAML 配置远程端点，自动拉取 Agent Card 并缓存
- 自适应刷新：10s 快速重试 → 600s 保活 → 指数退避
- Card Skills → RemoteAgentToolSpec → 自动注入本地 Agent Tool
- 中断-续接流水线：远程 INPUT_REQUIRED → 父 Task 挂起 → 用户输入 → 恢复

### 6. 运维就绪

- 生命周期管理（start → serve → stop → drain）、优雅停机、就绪门控
- Actuator 健康检查、MDC 日志关联、RuntimeErrorCode 错误分类
- RuntimeApp 嵌入式部署 API

---

## 二、下一迭代计划（v0.2.0 候选）

### agent-runtime 能力补齐

- OpenJiuwen Workflow 适配
- MCP (Model Context Protocol) 协议接入
- 完善日志轨迹记录，提供生产环境最佳实践
- 支持自研记忆服务

### agent-sdk — YAML 配置驱动 Agent 生成

- 模型配置：YAML 声明 LLM 连接信息
- 提示词配置：支持文件引用和环境变量注入
- 工具配置：支持 HTTP 接口工具和本地 Java 方法工具
- 技能配置：自动加载技能描述文件
- 与 runtime 集成：YAML 定义的 Agent 自动注册为 Handler
- 启动校验：schema 正确性和工具可达性

### agent-service — 开箱即用的 Agent 平台服务

- 一键部署：一个 Spring Boot 应用启动即用
- YAML 驱动：配置文件声明 Agent，无需编写 Java 代码
- Agent 管理 API：Agent 列表、状态查询、启停控制

---

## 三、贡献者

### agent-runtime 模块

| 贡献者 | Commits |
|--------|---------|
| Chao Xing | 52 |
| chaosxingxc-orion | 19 |
| yougq | 14 |
| x00209170 | 10 |
| Kevin-708090 | 10 |
| Euphoria Yan | 7 |
| yansuqing | 2 |
| Suqing Yan | 1 |
| Kevin Hu | 1 |

### Examples 模块

| 贡献者 | Commits |
|--------|---------|
| yougq | 26 |
| x00209170 | 10 |
| chaosxingxc-orion | 10 |
| yansuqing | 8 |
| Euphoria Yan | 8 |
| xuefanfan-cmd | 6 |
| Kevin-708090 | 4 |
| Chao Xing | 1 |
| Kevin Hu | 1 |
| nickylba | 1 |
| caikongerbanhzz-ui | 1 |

### 文档

| 贡献者 | Commits |
|--------|---------|
| Chao Xing | 52 |
| chaosxingxc-orion | 20 |
| yougq | 17 |
| x00209170 | 8 |
| yansuqing | 7 |
| Euphoria Yan | 7 |
| LucioIT | 4 |
| Kevin-708090 | 4 |
