# EDPAgent 简化重构方案

> **版本**：v2.0
> **最后修改**：2026-06-21
> **目标**：基于 Jiuwen DeepAgent（Java）对 Python 版 EDPAgent 进行重构，采用 OpenJiuwen 标准开发方式，通过 `OpenJiuwenAgentRuntimeHandler` SPI 接入 agent-runtime
> **核心原则**：挂载式开发 = OpenJiuwen式开发 · 配置标准化（YAML） · 消除中间层 · SPI 契约接入 agent-runtime

---

## 〇、项目背景

### 0.1 技术栈统一诉求

客户当前开发工具环境以 **Java 为主**，现有团队人员技能模型为 **Java 技术栈**。而 EDPAgent 当前实现基于 Python（openjiuwen 框架），与客户的技术生态不匹配，导致：

- **维护成本高**：团队需要同时维护 Java 和 Python 两套技术栈
- **人员复用难**：Python 开发人员储备不足，项目交付依赖少数关键人员
- **CI/CD 割裂**：Python 服务需单独部署流水线，与 Java 微服务体系不统一
- **调试排障门槛高**：一线运维人员对 Python 运行时不如 Java 熟悉

因此需要将 EDPAgent 从 Python 迁移到 Java，融入客户的 Java 微服务生态。

### 0.2 AgentRule 复杂度反馈

EDPAgent 已在以下项目中落地应用：

| 项目 | 场景 | 关键反馈 |
|---|---|---|
| **超级智能体项目** | 多 Agent 编排，金融尽调自动化 | AgentRule.md 配置项过多，Rule 1~6 + todolist_steps + scripts 等字段分散，业务人员理解成本高 |
| **手机银行项目** | 基金购买，话术引导 | 业务人员期望"只写我需要改的部分"，但当前设计要求完整填写所有配置节 |

补充说明：**超级智能体项目** 客户最后选择 DeepAgent 进行业务 Agent 开发。

### 0.3 项目反馈要点

**超级智能体项目：**

- **EDPAgent 复杂度过高**：项目场景不需要话术系统和业务规则约束，但 EDPAgent 设计上这些能力高度耦合，无法按需剥离
- **开放性不足**：EDPAgent 添加一个自定义工具需要修改工具注册代码、AgentRule.md 配置声明、Rail 拦截逻辑等多处，而 DeepAgent 仅需通过 `config.tools` 一行注入
- **配置负担重**：AgentRule.md 的 Rule 1~6 全部必填，即使场景不需要也必须完整填写

**手机银行项目：**

- **配置门槛高**：业务人员期望"只写需要改的部分"，但当前设计要求完整填写所有配置节
- **话术配置分散**：话术模板散落在 AgentRule.md 和 ScriptsConfig.md 两个文件中

**两个项目的差异化需求对比：**

| 需求点 | 超级智能体项目 | 手机银行项目 |
|---|---|---|
| **话术系统** | ❌ 不需要 | ✅ 需要合规话术 |
| **业务规则** | ❌ 不敏感 | ✅ 需要明确约束 |
| **工具自由度** | ✅ 需自由增删工具 | 工具集相对固定 |
| **定制深度** | ✅ 需挂载自定义逻辑 | 主要由配置驱动 |

### 0.4 简化重构的核心洞察

**挂载式开发 = OpenJiuwen式开发**。DeepAgentPlus 继承 DeepAgent，意味着对 DeepAgentPlus 的操作本质上就是对 DeepAgent 的操作。因此：

- **消除 DeepAgentPlus 中间类**：直接使用 DeepAgent，EDPAgent 扩展通过 `EdpaAgentEnhancer` 工具类注册
- **消除 AgentRule.md 专有格式**：标准配置用 `ascend-agent/v1` YAML，EDPAgent 专有扩展用 `edp-config.yaml`
- **消除 StreamEventAdapter**：由 agent-runtime 的 `OpenJiuwenStreamAdapter` 覆盖
- **统一开发方式**：不再区分"装配式/挂载式"，统一为"OpenJiuwen式开发"

---

## 一、重构目标

### 目标1：继承 EDPAgent 全部能力，零丢失

| 维度 | 现状（Python EDPAgent） | 目标（Java 版重构） |
|---|---|---|
| 基础框架 | openjiuwen ReActAgent | openjiuwen DeepAgent |
| 配置驱动 | AgentRule.md + ScriptsConfig.md | edp-agent.yaml（ascend-agent/v1）+ edp-config.yaml + ScriptsConfig.md |
| 工具体系 | 5 个自建工具 + SysOp | DeepAgent 46+ 工具 + 5 个业务工具（通过 EdpaAgentEnhancer 注册） |
| Rails | 9 个自建 Rails | DeepAgent 26+ Rails + 9 个 EDPAgent 自建 Rail（通过 EdpaAgentEnhancer 注册，其中 MemoryRail 可选） |
| Skills | 5 个尽调 Skill | Skill 目录化加载（DeepAgent 原生 skillDirectories） |
| 话术系统 | AskUserRail 话术模板 | 完整继承，通过 EdpConfig 配置 |
| 流事件 | 17 种 AgentEvent | DeepAgent OutputSchema → agent-runtime OpenJiuwenStreamAdapter 映射 |
| 记忆 | MemoryRail（Redis + GaussDB + ES + Embedding） | MemoryRail（EDPAgent 自建，含 GaussDB + Redis + ES + Embedding 初始化） |
| 中断/级联 | Versatile 级联中断 | VersatileInterruptRail → INTERRUPTED → agent-runtime 编排接管 |
| 固定脚本 | FixedScriptFeeder | 完整继承 |

### 目标2：统一为 OpenJiuwen式开发（含配置驱动 + 深度定制两个层级）

> **说明**：v1.1 的"装配式开发"（零代码配置驱动）在 v2.0 中仍然支持，只是实现方式从 `DeepAgentPlus.assemble(AgentRule.md)` 改为 `AgentFactory.toDeepAgent(edp-agent.yaml)` + `EdpaAgentEnhancer.enhance(agent, edpConfig)`。不再区分"装配式/挂载式"两种模式，而是统一为"OpenJiuwen式开发"的两个层级：配置驱动（零代码）和深度定制（Java 代码）。

```
              EDPAgent 重构后的开发方式
  
  ┌──────────────────────────────────────────────────────────────┐
  │                                                              │
  │  OpenJiuwen式开发（与任何 OpenJiuwen Agent 完全一致）          │
  │  ─────────────────────────────                               │
  │                                                              │
  │  ★ 层级一：配置驱动（零代码，等同于 v1.1 装配式开发）          │
  │  1. 编写 edp-agent.yaml（ascend-agent/v1 格式）              │
  │     → model / prompt / maxIterations / skills / tools        │
  │  2. 编写 edp-config.yaml（EDPAgent 专有配置，可选）           │
  │     → todolistSteps / utterances / scope / limits             │
  │  3. EdpaRuntimeHandler @PostConstruct 自动装配               │
  │     → AgentFactory.toDeepAgent + EdpaAgentEnhancer.enhance   │
  │     → 零 Java 业务代码，纯配置驱动                             │
  │                                                              │
  │  ★ 层级二：深度定制（Java 代码，等同于 v1.1 挂载式开发）       │
  │  4. agent.registerTool(new MyCustomTool())                   │
  │  5. agent.registerRail(new MyCustomRail())                   │
  │  6. 覆写 openJiuwenRails() 注入自定义 Rail                   │
  │                                                              │
  │  ★ 两个层级的关系：                                           │
  │     层级一 是基础（配置驱动创建 Agent）                         │
  │     层级二 是叠加（在层级一基础上追加 Java 定制）               │
  │     不是"两种模式"，而是同一种开发方式的两个层级                 │
  │                                                              │
  └──────────────────────────────────────────────────────────────┘
```

### 目标3：简化开发者复杂度

| 简化维度 | 旧（Python EDPAgent） | 新（简化重构） | 降低的复杂度 |
|---|---|---|---|
| **配置文件** | AgentRule.md（专有 Markdown 格式，必填） | edp-agent.yaml（标准 YAML）+ edp-config.yaml（可选） | 标准 YAML 格式，与 OpenJiuwen 生态一致 |
| **配置章节** | 全部必填 | 全部可选，按需叠加 | 只需写关注的部分 |
| **中间类** | DeepAgentPlus（1,800 行） | 无，直接使用 DeepAgent | 消除 ~3,100 行中间层代码 |
| **工具管理** | 需了解每个工具、手动注册 | DeepAgent 46+ 内置 + EdpaAgentEnhancer 注册 5 业务工具 | 零配置获得内置工具能力 |
| **Rails 管理** | 全部自建 | DeepAgent 26+ 内置 + EdpaAgentEnhancer 注册 9 个 EDPAgent 自建 Rail（其中 MemoryRail 可选） | 减少 60% 自建代码量 |
| **流事件映射** | 自建 StreamEventAdapter | agent-runtime OpenJiuwenStreamAdapter | 消除自建适配层 |
| **开发入门** | 需理解完整代码结构 | 与 OpenJiuwen 标准开发方式一致 | 新人半天可上手 |

---

## 二、类架构设计

### 2.1 架构关系

```
DeepAgent (com.openjiuwen.harness.deep_agent)
    │
    │  ★ 不再继承，直接使用
    │  EDPAgent 扩展通过 EdpaAgentEnhancer 工具类注册
    │
    ├── EdpaAgentEnhancer（工具类，非继承）
    │   ├── 注册 5 个业务工具（LiteTodoWrite / CallVersatile / CallMcp / AskUser / Cancel）
│   ├── 注册 9 个 EDPAgent 自建 Rail（Cancel / LiteTodo / IterationLimit / ExecutionLimit / VersatileInterrupt / MCPInterrupt / AskUserRail/AskUserTemplateRail / Memory / Log；Memory 可选）
│   ├── 注入 channel/ToolDataChannel（MCP 数据 → Versatile 委托注入）
│   ├── 注入话术系统（ScriptsConfigManager）
    │   ├── 注入 Todo 动态 Schema（LiteTodoWriteTool）
    │   └── 注入固定脚本帧推送（FixedScriptFeeder）
    │
    ├── EdpaRuntimeHandler（extends OpenJiuwenAgentRuntimeHandler）
    │   ├── createOpenJiuwenAgent(ctx) → 返回 DeepAgent 实例
    │   └── openJiuwenRails(ctx) → 返回 EDPAgent 专有 Rail 列表
    │
    └── EdpConfig（EDPAgent 专有配置 POJO）
        ├── todolistSteps / utterances / scope / limits
        └── EdpConfigLoader 解析 edp-config.yaml
```

### 2.2 类职责边界

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DeepAgent（直接使用，不继承）                        │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │  DeepAgent 原生能力（不改动，直接复用）                           │ │
│  │  • TaskLoop 多轮循环       • CompletionPromise 完成检测         │ │
│  │  • 子Agent委派             • Steering / FollowUp / Abort       │ │
│  │  • 46+ 内置工具            • 26+ 内置 Rails                    │ │
│  │  • ProgressiveToolRail     • AgentMode（Plan/Normal）          │ │
│  │  • 权限系统                • 上下文引擎                         │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │  EdpaAgentEnhancer 注册的扩展能力（业务层增强）                    │ │
│  │                                                                 │ │
│  │  工具层：                                                       │ │
│  │  • LiteTodoWriteTool     — 轻量 Todo（覆盖式）                  │ │
│  │  • CallVersatileTool     — 工作流调用（VA 级联中断）             │ │
│  │  • CallMcpTool           — MCP 脚本调用（沙箱执行）              │ │
│  │  • EnhancedAskUserTool   — 增强 ask_user（话术参数）            │ │
│  │  • CancelTaskTool        — 任务取消（话术响应）                  │ │
│  │                                                                 │ │
│  │  Rail 层：                                                      │ │
│  │  • CancelRail              — 取消任务 + 注入话术 + 三层清理      │ │
│  │  • LiteTodoRail            — Todo 持久化 + tool_end 事件         │ │
│  │  • IterationLimitRail      — 迭代次数限制                        │ │
│  │  • ExecutionLimitRail      — 单工具限制 + tool/todo 事件补齐     │ │
│  │  • McpInterruptRail        — MCP 沙箱执行拦截                   │ │
│  │  • VersatileInterruptRail  — 工作流级联中断 + todo 事件特殊处理  │ │
│  │  • AskUserRail/AskUserTemplateRail — 话术模板匹配 + JSON 容错   │ │
│  │  • MemoryRail              — 记忆持久化（可选）                 │ │
│  │  • LogRail                 — 模型名和工具列表记录               │ │
│  │                                                                 │ │
│  │  运行时辅助组件：                                               │ │
│  │  • ToolDataChannel        — 工具/Rail 间共享数据通道            │ │
│  │  • FixedScriptFeeder      — 固定脚本帧推送                      │ │
│  │  • ScriptsConfigManager   — 话术配置管理                        │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

> **关键变化**：消除 DeepAgentPlus 中间类。DeepAgent 直接使用，EDPAgent 扩展通过 `EdpaAgentEnhancer.enhance(agent, edpConfig)` 注册。这与 OpenJiuwen 标准开发方式完全一致——`agent.registerTool()` / `agent.registerRail()` 是 DeepAgent 的标准 API。

---

## 三、EDPAgent 与 DeepAgent 的关系

> **核心论断**：**EDPAgent 是 DeepAgent + EDPAgent 扩展的实例化**。DeepAgent 提供了 Agent 的基础框架和能力（ReAct 推理、工具系统、Rail 链、TaskLoop 等），EDPAgent 通过 `EdpaAgentEnhancer` 注册业务扩展（5 个业务工具 + 9 个 EDPAgent 自建 Rail〔其中 MemoryRail 可选〕+ 话术 + Todo + Channel），形成完整的可用 Agent。
>
> **EDPAgent 不包含 Runtime 基础设施**。Runtime 基础设施由 `agent-runtime` 项目提供，EDPAgent 通过继承 `OpenJiuwenAgentRuntimeHandler` 接入 Runtime。

### 3.1 关系公式

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                      │
│   EDPAgent  =   DeepAgent（OpenJiuwen 标准创建）                       │
│               + EdpaAgentEnhancer.enhance(agent, edpConfig)           │
│               + edp-agent.yaml（ascend-agent/v1 标准配置）             │
│               + edp-config.yaml（EDPAgent 专有配置）                   │
│               + Skills（领域知识：尽调技能包）                         │
│               + ScriptsConfig.md（话术模板）                           │
│                                                                      │
│   ★ Runtime 基础设施由 agent-runtime 项目提供：                        │
│     A2aJsonRpcController + A2aAgentExecutor + TaskStore               │
│     + VersatileAgentRuntimeHandler + OpenJiuwenStreamAdapter          │
│     EDPAgent 通过 extends OpenJiuwenAgentRuntimeHandler 接入 Runtime  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

| 组成要素 | 来源 | 角色 | 类比 |
|---------|------|------|------|
| **DeepAgent** | OpenJiuwen SDK（`AgentFactory.toDeepAgent(yamlPath)`） | 引擎：ReAct 推理循环、46+ 工具、26+ Rail、TaskLoop、Memory | 汽车底盘 + 发动机 |
| **EdpaAgentEnhancer** | Java 注册器 | 扩展注册器：将 5 个业务工具 + 9 个 EDPAgent 自建 Rail（Memory 可选）+ 话术 + Todo + Channel 注册到 DeepAgent | 加装导航 + 车载语音 |
| **edp-agent.yaml** | `ascend-agent/v1` 标准格式 | 标准配置：model / prompt / maxIterations / skills / tools | 行车路线 + 限速 |
| **edp-config.yaml** | EDPAgent 专有格式 | 专有配置：todolistSteps / utterances / scope / limits | 业务规则 + 话术 |
| **Skills** | 领域专家编写的 SKILL.md 目录 | 知识：领域专属执行流程、约束、脚本 | 导航地图 |
| **ScriptsConfig.md** | 话术管理员编写的 YAML 文件 | 话术：中断提示、工具执行提示 | 车载语音系统 |
| **Runtime 基础设施** | `agent-runtime` 项目 | 运行环境：HTTP 入口、A2A 协议栈、Task 管理、编排器 | 道路 + 加油站 |

### 3.2 层次关系图

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         EDPAgent（业务 Agent 实例）                               │
│                                                                                   │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                    ③ EDPAgent 专有扩展（EdpaAgentEnhancer 注册）        │    │
│  │                                                                      │    │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │    │
│  │  │ edp-config.yaml    │  │ Skills 目录        │  │ ScriptsConfig.md  │  │    │
│  │  │ • todolistSteps    │  │ • base_info/       │  │ • utterances     │  │    │
│  │  │ • utterances       │  │ • analysis/        │  │ • tools 话术      │  │    │
│  │  │ • scope / limits   │  │ • summary/         │  │ • interrupt 话术   │  │    │
│  │  └──────────────────┘  └──────────────────┘  └──────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    ▲ EdpaAgentEnhancer.enhance() 注册到...   │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    ② OpenJiuwen 标准配置（edp-agent.yaml）              │    │
│  │                                                                      │    │
│  │  ┌──────────────────────────────────────────────────────────────┐    │    │
│  │  │ ascend-agent/v1 YAML                                          │    │    │
│  │  │ • model / prompt / maxIterations / skillDirectories / tools    │    │    │
│  │  │ → AgentFactory.toDeepAgent(yamlPath) 标准路径创建 DeepAgent     │    │    │
│  │  └──────────────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    ▲ 创建...                                │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    ① DeepAgent（引擎底座，直接使用）                       │    │
│  │                                                                      │    │
│  │  • ReActAgent 推理引擎    • TaskLoop 多轮循环                     │    │
│  │  • 46+ 内置工具           • 26+ 内置 Rails                       │    │
│  │  • ContextEngine 上下文    • Memory 长期记忆                      │    │
│  │  • CompletionPromise      • 子Agent委派 / Steering / Abort       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────────┘
                                          │
                                          │ 运行在...之上
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│         ★ agent-runtime 项目（EDPAgent 通过 OpenJiuwenAgentRuntimeHandler 接入） │
│                                                                                   │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │ A2aJsonRpcController (HTTP 入口，JSON-RPC 2.0)                        │    │
│  │ A2aAgentExecutor (编排器，Task 生命周期管理)                           │    │
│  │ InMemoryTaskStore / RedisTaskStore (Task 持久化)                      │    │
│  │ VersatileAgentRuntimeHandler (Versatile 适配 Handler)                 │    │
│  │ OpenJiuwenStreamAdapter (流事件 → A2A 事件映射)                       │    │
│  │ A2aRemoteInvocationOrchestrator (远程 Agent 调用编排)                 │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                                   │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │ DeepAgent 框架 SDK (com.openjiuwen.harness.deep_agent)                │    │
│  │ Runner / ReActAgent / Session / ContextEngine                        │    │
│  │ Tool/Rail 基类 / Memory / Checkpointer                               │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 初始化流程

```
  启动入口: agent-runtime Spring Boot Application
  │
  ├── Step 1: agent-runtime 基础设施初始化（★ 由 agent-runtime 项目负责）
  │   ├── Spring Boot 自动配置（DataSource / Redis / WebMvc）
  │   ├── A2aJsonRpcController 注册（HTTP 入口 + JSON-RPC 2.0 路由）
  │   ├── A2aAgentExecutor 初始化（Task 生命周期管理）
  │   ├── TaskStore 初始化（InMemoryTaskStore 或 RedisTaskStore）
  │   └── VersatileAgentRuntimeHandler 初始化（Versatile 适配 Handler）
  │
  ├── Step 2: EDPAgent Handler Bean 注册（★ EDPAgent 交付范围）
  │   │
  │   │  EDPAgent 通过 @Component 继承 OpenJiuwenAgentRuntimeHandler：
  │   │
  │   ├── @Component EdpaRuntimeHandler extends OpenJiuwenAgentRuntimeHandler("edp-agent")
  │   │   ├── @PostConstruct init()
  │   │   │   ├── AgentFactory.toDeepAgent(Path.of("./edp-agent.yaml"))
  │   │   │   │   → 创建 DeepAgent 实例（标准路径）
  │   │   │   ├── EdpConfigLoader.load(Path.of("./edp-config.yaml"))
  │   │   │   │   → 解析 EDPAgent 专有配置
  │   │   │   ├── EdpaAgentEnhancer.enhance(agent, edpConfig)
  │   │   │   │   → 注册 5 个业务工具 + 9 个 EDPAgent 自建 Rail（Memory 可选）+ 话术 + Todo + channel/ToolDataChannel + FixedScriptFeeder
  │   │   │   └── agent.initialize()
  │   │   │
  │   │   ├── createOpenJiuwenAgent(ctx) → 返回 agent 实例
  │   │   │   （基类处理执行流程、消息转换、流映射）
  │   │   │
  │   │   └── openJiuwenRails(ctx) → 返回 EDPAgent 专有 Rail 列表
  │   │
  │   └── agent-runtime 自动发现并注册 Handler
  │       └── A2aAgentExecutor.handlerRegistry.register(edpaRuntimeHandler)
  │
  ├── Step 3: 业务工具和 Rail 已注册，缺省生效
  │   │  ★ EdpaAgentEnhancer 注册的扩展，缺省全部生效。
  │   │    edp-config.yaml 提供关闭/降级接口：
  │   │
  │   ├── 业务工具（EdpaAgentEnhancer 注册，缺省生效）
  │   │   ├── LiteTodoWriteTool       ← todolistSteps 未配时降级为 DeepAgent todo_*
  │   │   ├── CallVersatileTool       ← exclude_tools 可关闭；Tool 只保留 schema + delegate intent
  │   │   ├── CallMcpTool             ← exclude_tools 可关闭；Tool 只保留 schema + MCP intent
  │   │   ├── EnhancedAskUserTool     ← utterances 未配时降级为 DeepAgent ask_user
  │   │   └── CancelTaskTool          ← exclude_tools 可关闭；Tool 只保留 schema + cancel intent
  │   │
  │   └── 业务 Rail（EdpaAgentEnhancer 注册，缺省生效；MemoryRail 可选）
  │       ├── CancelRail              ← 始终生效，负责取消终止和清理
  │       ├── LiteTodoRail            ← lite_todo_write 持久化 + tool_end 事件
  │       ├── IterationLimitRail      ← limits.max_iterations 未配时使用默认值
  │       ├── ExecutionLimitRail      ← per-tool 限制 + tool/todo 事件补齐
  │       ├── McpInterruptRail        ← 未配置时 approve 放行
  │       ├── VersatileInterruptRail  ← 未配置时 approve 放行
  │       ├── AskUserRail/AskUserTemplateRail ← 对应 Python AskUserRail；utterances 未配时使用通用中断
  │       ├── LogRail                 ← 记录模型名和工具列表
  │       └── MemoryRail              ← EDPAgent 自建，可按 memory_enabled 关闭
  │
  ├── Step 4: agent-runtime 服务就绪（★ 由 agent-runtime 项目负责）
  │   ├── A2aJsonRpcController 挂载 HTTP 路由
  │   └── VersatileAgentRuntimeHandler 就绪
  │
  └── ★ 此时 EDPAgent 已通过 OpenJiuwenAgentRuntimeHandler 完全接入 agent-runtime
```

### 3.4 运行时请求流转

```
  用户请求 (HTTP POST /a2a/ JSON-RPC 2.0)
  │
  ▼
  agent-runtime ─ A2aJsonRpcController
  ├── 解析 JSON-RPC 请求 (SendMessage / SendStreamingMessage)
  ├── A2aAgentExecutor.execute()
  │   ├── 创建 Task(WORKING)
  │   ├── 调用 EdpaRuntimeHandler（基类处理消息转换）
  │   │
  │   ▼
  │   EDPAgent Handler ─ EdpaRuntimeHandler
  │   ├── DeepAgent.stream(query) → LLM 推理循环
  │   │   ├── edp-agent.yaml 约束 LLM 行为边界
  │   │   ├── edp-config.yaml 约束业务规则
  │   │   ├── Skills 注入领域知识
  │   │   ├── Rails 拦截链逐层检查
  │   │   └── 产出 OutputSchema 流事件
  │   │
  │   │   ┌── Rail 拦截: VersatileInterruptRail
  │   │   │   → 产出 INTERRUPTED 信号
  │   │   │   → A2aAgentExecutor 检测 INTERRUPTED → 接管编排
  │   │   │   → VersatileAgentRuntimeHandler 处理 Versatile 调用
  │   │   │   → 返回结果 → A2aAgentExecutor 注入回 Agent
  │   │   │
  │   │   └── 其他 OutputSchema
  │   │       → OpenJiuwenStreamAdapter 映射为 A2A 事件
  │   │       → SSE 流式推送客户端
  │   │
  │   └── Task 状态更新 → TaskStore.save()
  │
  ▼
  SSE 流式推送最终结果给客户端
```

### 3.5 关键结论

| 维度 | 说明 |
|------|------|
| **DeepAgent 是什么** | OpenJiuwen SDK 标准类，提供推理引擎、工具系统、Rail 链等通用能力。EDPAgent 直接使用，不继承 |
| **EdpaAgentEnhancer 是什么** | 注册器，将 EDPAgent 专有扩展（5 个业务工具 + 9 个 EDPAgent 自建 Rail〔Memory 可选〕+ 话术 + Todo + Channel）注册到 DeepAgent 实例上 |
| **EDPAgent 是什么** | DeepAgent + EdpaAgentEnhancer 扩展 + 配置的完整实例。通过 `OpenJiuwenAgentRuntimeHandler` 接入 agent-runtime |
| **配置的作用** | edp-agent.yaml 是标准配置（model/prompt/maxIterations/skills/tools），edp-config.yaml 是 EDPAgent 专有配置（todolistSteps/utterances/scope/limits） |
| **Runtime 的作用** | agent-runtime 项目提供 HTTP 入口、A2A 协议栈、Task 管理、编排器、Versatile 适配、流事件映射。EDPAgent 通过 `extends OpenJiuwenAgentRuntimeHandler` 接入 |
| **开发方式** | 与 OpenJiuwen 标准开发方式完全一致。无中间层，无专有格式 |

> **一句话总结**：**DeepAgent 是引擎，EdpaAgentEnhancer 是加装包，EDPAgent 是整车**。引擎决定了能跑多快；加装包决定了往哪跑、到站说什么话；道路和加油站（Runtime）由 agent-runtime 提供。

---

## 四、重构后的整体架构

### 4.1 系统拓扑

```
                        外部客户端 (HTTP/SSE, JSON-RPC 2.0)
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│              agent-runtime 项目（★ 独立项目，不在 EDPAgent 交付范围）    │
│                                                                      │
│  ┌ HTTP 入口层 ────────────────────────────────────────────────┐  │
│  │ A2aJsonRpcController                                         │  │
│  │ POST /a2a/                        (JSON-RPC 2.0)             │  │
│  │ GET  /a2a/.well-known/agent-card  (AgentCard)                │  │
│  │ GET  /health                      (健康检查)                  │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                          │                                          │
│  ┌ 编排层 ────────────────────────────────────────────────────┐  │
│  │ A2aAgentExecutor                                             │  │
│  │ • execute() → 调用 Handler.createOpenJiuwenAgent()           │  │
│  │ • 检测 INTERRUPTED → 接管编排 → 调用子 Handler               │  │
│  │ • Task 生命周期管理                                           │  │
│  │                                                              │  │
│  │ OpenJiuwenStreamAdapter                                      │  │
│  │ • OutputSchema → AgentExecutionResult 映射                   │  │
│  │ • SSE 流式推送                                                │  │
│  │                                                              │  │
│  │ VersatileAgentRuntimeHandler                                 │  │
│  │ • Versatile 适配 Handler                                     │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                          │                                          │
│  ┌ 基础设施层 ────────────────────────────────────────────────┐  │
│  │ InMemoryTaskStore / RedisTaskStore                           │  │
│  │ Spring Boot 自动配置                                         │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                          │                                          │
│  ┌ Handler 注册层 ────────────────────────────────────────────┐  │
│  │ OpenJiuwenAgentRuntimeHandler SPI 接口                       │  │
│  │ └── EdpaRuntimeHandler (@Component, EDPAgent 交付)          │  │
│  │     ├── createOpenJiuwenAgent(ctx) → DeepAgent 实例          │  │
│  │     └── openJiuwenRails(ctx) → EDPAgent 专有 Rail 列表       │  │
│  └─────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ Versatile 调用 (A2A JSON-RPC 或 HTTP)
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│              Versatile 低代码平台 (外部系统)                           │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  Redis (外部存储)                                                     │
│  • Task 状态持久化 (RedisTaskStore)                                   │
│  • Session Checkpointer                                               │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 分层架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Layer 2: Agent 层 (EDPAgent 交付范围)                                        │
│  ─────────────────────────────                                                │
│  EdpaRuntimeHandler (extends OpenJiuwenAgentRuntimeHandler)                   │
│  ├── DeepAgent 引擎（AgentFactory.toDeepAgent 标准创建）                       │
│  │   • LLM 推理、工具调用、Rail 拦截、Skill 执行、OutputSchema 产出            │
│  ├── EdpaAgentEnhancer 注册的扩展                                              │
│  │   • 5 个业务工具 + 9 个 EDPAgent 自建 Rail（Memory 可选）+ channel/ToolDataChannel + 话术 + Todo + FixedScriptFeeder │
│  ├── EdpConfig（EDPAgent 专有配置）                                             │
│  │   • todolistSteps / utterances / scope / limits                             │
│  └── ScriptsConfigManager（话术配置管理）                                       │
│  职责：Agent 业务逻辑，通过 OpenJiuwenAgentRuntimeHandler SPI 接入 Runtime     │
├─────────────────────────────────────────────────────────────────────────────┤
│  Layer 1: Runtime 层 (agent-runtime 项目提供，不在 EDPAgent 交付范围)           │
│  ─────────────────────────────                                                │
│  HTTP 入口 (A2aJsonRpcController)                                             │
│  编排器 (A2aAgentExecutor)                                                     │
│  Task 管理 (InMemoryTaskStore / RedisTaskStore)                               │
│  Versatile 适配 (VersatileAgentRuntimeHandler)                                │
│  流事件映射 (OpenJiuwenStreamAdapter)                                          │
│  远程调用编排 (A2aRemoteInvocationOrchestrator)                                │
│  Spring Boot 基础设施                                                          │
│  职责：A2A 协议服务、Task 生命周期、编排、流推送、基础存储                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.3 关键组件职责矩阵

| 组件 | 所属层 | 交付方 | 核心职责 | 关键输出 |
|------|--------|---------|---------|---------|
| **A2aJsonRpcController** | Runtime 层 | agent-runtime | HTTP 入口、JSON-RPC 2.0 路由 | HTTP SSE 流 |
| **A2aAgentExecutor** | Runtime 层 | agent-runtime | Task 生命周期管理、INTERRUPTED 检测、编排接管 | Task 状态变更 |
| **OpenJiuwenStreamAdapter** | Runtime 层 | agent-runtime | OutputSchema → AgentExecutionResult 映射 | A2A 事件 |
| **VersatileAgentRuntimeHandler** | Runtime 层 | agent-runtime | Versatile 适配 Handler | Versatile 调用结果 |
| **InMemoryTaskStore / RedisTaskStore** | Runtime 层 | agent-runtime | Task 持久化 | Task 数据 |
| **EdpaRuntimeHandler** | Agent 层 | EDPAgent | OpenJiuwenAgentRuntimeHandler SPI 实现 | DeepAgent 实例 |
| **EdpaAgentEnhancer** | Agent 层 | EDPAgent | EDPAgent 扩展注册器 | 5 个业务工具 + 9 个 EDPAgent 自建 Rail（Memory 可选）+ 话术 + Todo + Channel |
| **DeepAgent** | Agent 层 | OpenJiuwen SDK | Agent 引擎底座 | OutputSchema 流事件 |
| **Versatile 低代码平台** | 外部 | 外部 | 工作流执行引擎 | NDJSON/SSE 结果流 |
| **Redis** | 外部存储 | 外部 | Session/Task 持久化 | KV 数据 |

### 4.4 配置驱动关系

```
                    ┌─────────────────────────────┐
                    │      edp-agent.yaml          │
                    │  (ascend-agent/v1 标准配置)    │
                    │  ─────────────────────       │
                    │  • model: LLM 模型配置        │
                    │  • prompt: System Prompt      │
                    │  • maxIterations: 最大迭代     │
                    │  • skillDirectories: Skill 目录│
                    │  • tools: 工具白名单           │
                    └──────────┬──────────────────┘
                               │ AgentFactory.toDeepAgent()
          ┌────────────────────┼────────────────────┐
          │                    ▼                     │
          │  ┌─────────────────────────────────┐    │
          │  │        DeepAgent 实例             │    │
          │  └─────────────────────────────────┘    │
          │                    ▲                     │
          └────────────────────┼────────────────────┘
                               │ EdpaAgentEnhancer.enhance()
                    ┌──────────┴──────────────────┐
                    │                             │
          ┌─────────┴──────────┐    ┌─────────────┴──────────┐
          │  edp-config.yaml    │    │   ScriptsConfig.md      │
          │  (EDPAgent 专有配置) │    │   (话术模板)             │
          │  ─────────────────  │    │   ─────────────────      │
          │  • todolistSteps    │    │  • tools: 工具话术       │
          │  • utterances       │    │  • interrupt: 中断话术   │
          │  • scope / limits   │    │  • task: 任务话术        │
          └────────────────────┘    └─────────────────────────┘
```

### 4.5 部署视图

```
┌──────────────────────────────────────────────────────────────────┐
│                        Docker Container                           │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              agent-runtime (Spring Boot, 端口由配置决定)       │  │
│  │                                                              │  │
│  │  • A2aJsonRpcController (HTTP 入口)                          │  │
│  │  • A2aAgentExecutor (编排器)                                  │  │
│  │  • VersatileAgentRuntimeHandler (Versatile 适配)             │  │
│  │  • OpenJiuwenStreamAdapter (流事件映射)                       │  │
│  │  • TaskStore (Task 持久化)                                    │  │
│  │                                                              │  │
│  │  ┌──────────────────────────────────────────────────────┐  │  │
│  │  │ EdpaRuntimeHandler (@Component Bean)                  │  │  │
│  │  │ • DeepAgent + EdpaAgentEnhancer 注册的扩展              │  │  │
│  │  │ • edp-agent.yaml + edp-config.yaml + Skills + 话术     │  │  │
│  │  └──────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                           │                       │
│                                           │ TCP                   │
└───────────────────────────────────────────┼───────────────────────┘
                                            │
                                   ┌────────┴────────┐
                                   │     Redis        │
                                   └─────────────────┘
```

---

## 五、配置体系设计

> **关键变化**：从 AgentRule.md（EDPAgent 专有 Markdown 格式）改为 edp-agent.yaml（ascend-agent/v1 标准）+ edp-config.yaml（EDPAgent 专有扩展）。标准配置与专有配置分离，互不干扰。

### 5.1 配置分层

| 层 | 配置文件 | 格式 | 承载内容 | 解析方式 |
|---|---|---|---|---|
| **Layer 1: 标准配置** | `edp-agent.yaml` | `ascend-agent/v1` YAML | model / prompt / maxIterations / skillDirectories / tools | `AgentFactory.toDeepAgent(yamlPath)` |
| **Layer 2: 专有配置** | `edp-config.yaml` | 标准 YAML | todolistSteps / utterances / scope / limits / thinkChunk | `EdpConfigLoader.load(yamlPath)` |
| **Layer 3: 话术配置** | `ScriptsConfig.md` | YAML Frontmatter + Markdown | 工具话术 / 中断话术 / 任务话术 | `ScriptsConfigManager.load(path)` |

### 5.2 edp-agent.yaml（ascend-agent/v1 标准格式）

```yaml
schema: ascend-agent/v1
name: edp-agent
description: 企业尽职调查 Agent

framework:
  type: openjiuwen
  agent: deepagent
  options:
    maxIterations: 15
    enableTaskLoop: true

model:
  provider: OpenAI
  name: deepseek-chat
  baseUrl: https://api.deepseek.com
  apiKey: sk-xxx

prompt:
  system: |
    你是一个企业尽调分析助手。
    
    ## 执行规范
    - 使用 todolist_steps 中定义的步骤进行规划
    - 调用工具前先在 think 中说明意图
    - 超范围问题时调用 ask_user 向用户确认
    
    ## MCP 先行架构
    - 需要获取外部数据时，先 call_mcp 获取数据
    - MCP 数据通过 ToolDataChannel 自动注入后续 call_versatile 委托请求
    - 不要在 call_versatile 中重复获取 MCP 已获取的数据
    
    ## 工具使用规则
    - bash: 执行 Shell 命令（仅限沙箱环境）
    - read_file: 读取 Skill 的 SKILL.md 获取执行指引
    - call_versatile: 委托 Versatile 执行业务操作
    - call_mcp: 获取外部数据（MCP 先行）
    - ask_user: 向用户提问或确认
    - lite_todo_write: 管理待办清单（覆盖式写入），用于 3+ 步任务规划与进度展示
    - cancel_task: 取消当前任务
    
    ## 状态更新流程（MANDATORY）
    - call_mcp 或 call_versatile 成功执行后，必须立即调用 lite_todo_write 标记当前步骤为完成
    - 状态更新必须在向用户输出最终结果之前完成
    - 所有步骤变更必须通过 lite_todo_write 记录，确保状态可追溯
    
    ## 输出规范
    - 最终回答使用 Markdown 格式
    - 涉及金额时保留两位小数

skills:
  directories:
    - ./skills
  mode: all

tools:
  - bash
  - read_file
  - call_versatile
  - call_mcp
  - ask_user
  - lite_todo_write
  - cancel_task
```

### 5.3 edp-config.yaml（EDPAgent 专有配置）

```yaml
# EDPAgent 专有配置（全部可选，未配置 = DeepAgent 默认行为）

# 业务范围约束（可选）
scope:
  allowed: "尽调报告生成相关业务"
  out_of_scope_message: "抱歉，我仅支持尽调相关业务，请重新描述您的需求。"

# 规划步骤模板（可选，注入到系统提示词）
planning_steps:
  - 需求解析：识别用户意图与关键参数
  - 目标拆解：列出待执行的子任务
  - 方案生成：确定每个子任务的工具与入参
  - 规则校验：检查是否超出业务范围
  - 结果输出：总结并返回用户

# 执行限制（可选）
limits:
  max_iterations: 15
  max_input_attempts: 3
  interrupt_timeout_seconds: 300
  tasks:
    call_versatile: 100
    call_mcp: 100
    ask_user: 100
    execute_cmd: 100

# 执行总结格式（可选）
summary:
  format: "需求概述→规划过程→任务执行情况→结果汇总→异常说明"
  max_length: 500
  required_fields: [用户查询, 执行步骤, 结果状态]

# 业务步骤目录（可选，不配 = 不注入 Todo 工具）
todolist_steps:
  - step_id: 1
    content: "收集企业基础信息"
    skill: "company_base_info"
  - step_id: 2
    content: "尽调要点分析"
    skill: "company_due_diligence_key_points"
  - step_id: 3
    content: "生成尽调报告"
    skill: "company_summary"
    depends_on: [1, 2]

# 任务依赖关系（可选，全局声明；v2.0 增强设计，Python 版使用 task_dependencies: {}）
# task_dependencies: {}

# 话术配置路径（可选，不配 = 通用中断，无语术模板）
utterances:
  config_path: "./ScriptsConfig.md"

# 记忆（可选，默认不启用）
memory:
  enabled: false

# Think Chunk 模式（可选）
think_chunk:
  mode: fixed_script
  chars_per_frame: 4
  tokens_between_frames: 2
  min_interval_ms: 50
  default_scripts:
    - "正在分析您的需求..."
  query_patterns:
    - keywords: ["推荐", "理财", "产品", "筛选"]
      scripts: ["正在搜索理财产品..."]
    - keywords: ["购买", "买", "下单", "确认"]
      scripts: ["正在确认购买信息..."]
    - keywords: ["余额", "查询", "账户", "转账"]
      scripts: ["正在查询账户信息..."]
  execution_scripts:
    - "正在分析执行结果..."
  resume_scripts:
    - "当前业务步骤已为您处理完毕"
  scripts:
    - "正在分析您的需求..."

# LLM Sampling 覆盖（可选，修复 reasoning 模型空响应）
llm_sampling:
  temperature: 0.1
  top_p: 0.95
  max_retries: 0
```

### 5.4 配置优先级规则

| 规则 | 说明 |
|------|------|
| **edp-agent.yaml 优先** | model / prompt / maxIterations 等标准字段，edp-agent.yaml 中的值优先 |
| **edp-config.yaml 补充** | todolistSteps / utterances / scope / limits 等专有字段，仅 edp-config.yaml 承载 |
| **不冲突** | 两份配置文件承载的字段完全不同，不存在同一字段在两处配置的情况 |
| **缺失兜底** | edp-config.yaml 中未配置的字段使用 DeepAgent 默认值 |

### 5.4.1 环境变量映射

Python 版 `config.py` 有 50+ 个环境变量，Java 版映射如下：

| Python 环境变量分组 | 数量 | Java 版映射方式 | 说明 |
|---------------------|------|----------------|------|
| LLM（model_name, api_key, base_url 等） | ~8 | `edp-agent.yaml` model 字段 | 标准 YAML 覆盖；API Key 支持 decrypt_config_value 解密；Custom Headers（TOKEN/USER_ID/EXTRA_HEADERS）通过环境变量构建 |
| Redis（redis_host, redis_port 等） | ~4 | `application.yml` spring.data.redis | Spring Boot 标准配置 |
| ContextEngine（context_engine_url 等） | ~3 | `application.yml` 自定义属性 | Spring Boot 配置 |
| DialogueCompressor（compressor_enabled 等） | ~2 | `application.yml` 自定义属性 | Spring Boot 配置 |
| Memory（memory_enabled, gaussdb_* 等） | ~15 | `application.yml` 自定义属性 | GaussDB + Redis + ES + Embedding 初始化；Memory LLM 可独立配置（provider/api_base/api_key/model_name/timeout/verify_ssl/custom_headers），未配置时降级到主 Agent LLM |
| Sandbox（sandbox_url, skill_target_path 等） | ~2 | `application.yml` 自定义属性 | 沙箱环境配置 |
| Versatile（versatile_url, versatile_token 等） | ~4 | `application.yml` 自定义属性 | Versatile 委托配置 |
| MCP（mcp_server_url 等） | ~3 | `application.yml` 自定义属性 | MCP 服务配置 |
| EDPAgent 专有（scope, limits, todolist_steps 等） | ~10 | `edp-config.yaml` | 专有配置文件 |

> **原则**：标准 Agent 配置 → `edp-agent.yaml`，EDPAgent 专有配置 → `edp-config.yaml`，基础设施配置 → `application.yml`。

> **过渡说明**：Python 版 AgentRule.md frontmatter 中仍有 `scripts` 字段（注释标注"话术配置已迁移到 ScriptsConfig.md"），实际加载时 `AgentRuleConfig.scripts` 和 `ScriptsConfigData.scripts` 合并使用。Java 版彻底分离：话术全部由 ScriptsConfig.md 承载，edp-config.yaml 仅通过 `utterances.config_path` 引用。

### 5.5 配置渐进示例

#### 5.5.1 复杂度递进

```
复杂度递进：

  Level 0：仅标准配置（edp-agent.yaml）
  ┌─────────────────────────┐
  │ edp-agent.yaml:          │
  │   model / prompt / tools │
  │ 无 edp-config.yaml       │
  │ = 纯 DeepAgent 行为      │
  └─────────────────────────┘

  Level 1：标准配置 + 执行限制
  ┌─────────────────────────┐
  │ edp-agent.yaml: 不变     │
  │ edp-config.yaml:         │
  │   limits: ...            │
  └─────────────────────────┘

  Level 2：加 Todo + 话术
  ┌─────────────────────────┐
  │ edp-agent.yaml: 不变     │
  │ edp-config.yaml:         │
  │   limits: ...            │
  │   todolist_steps: [...]  │
  │   utterances: ...        │
  └─────────────────────────┘

  Level 3：完整配置 + 深度定制
  ┌──────────────────────────────────────────────┐
  │ edp-agent.yaml + edp-config.yaml 全配置       │
  │ + agent.registerTool() / agent.registerRail() │
  └──────────────────────────────────────────────┘
```

#### 5.5.2 场景对比：基金购买 vs 尽调报告

> **核心洞察**：切换业务场景时，框架层配置（~40%）是模板化的、可复用的，只需修改业务层配置（~60%）。Java 代码零修改。

**场景一：基金购买**（4 个 Skill，线性依赖）

```yaml
# edp-agent.yaml（基金购买场景）
schema: ascend-agent/v1
name: fund-purchase-agent
description: 基金购买智能助手

framework:
  type: openjiuwen
  agent: deepagent
  options:
    maxIterations: 20
    enableTaskLoop: true

model:
  provider: OpenAI
  name: deepseek-chat
  baseUrl: https://api.deepseek.com
  apiKey: sk-xxx

prompt:
  system: |
    你是一名基金购买智能助手。
    ## 业务范围
    当前支持：基金推荐、基金选择、资金汇聚、基金购买
    ## 规划规约
    涉及 ≥ 2 个 skill 时，先调用 lite_todo_write 发出完整 todo 列表
    ## 工具调用规则
    用户选择产品时必须使用 product_select_skill

skills:
  directories: [./skills]
  mode: all

tools: [bash, read_file, call_versatile, call_mcp, ask_user, lite_todo_write, cancel_task]
```

```yaml
# edp-config.yaml（基金购买场景）
scope:
  allowed: "基金购买相关业务（基金推荐、基金选择、资金汇聚、基金购买下单）"
  out_of_scope_message: "正在学习中，暂不支持该业务。"

limits:
  max_iterations: 20
  max_input_attempts: 3
  interrupt_timeout_seconds: 300
  tasks:
    call_versatile: 100
    call_mcp: 100

todolist_steps:
  - step_id: 1
    content: "推荐理财产品"
    skill: "product_recommend_skill"
  - step_id: 2
    content: "交互式理财筛选"
    skill: "interact_finance_rec_skill"
  - step_id: 3
    content: "确定购买产品和金额"
    skill: "product_select_skill"
    depends_on: [1, 2]
  - step_id: 4
    content: "查询余额、资金筹划并购买理财产品"
    skill: "fund_planning_skill"
    depends_on: [3]

utterances:
  config_path: "./ScriptsConfig.md"

think_chunk:
  mode: fixed_script
```

**场景二：尽调报告**（5 个 Skill，树形依赖）

```yaml
# edp-agent.yaml（尽调报告场景）
schema: ascend-agent/v1
name: due-diligence-agent
description: 企业尽职调查报告生成 Agent

framework:
  type: openjiuwen
  agent: deepagent
  options:
    maxIterations: 25
    enableTaskLoop: true

model:
  provider: OpenAI
  name: deepseek-chat
  baseUrl: https://api.deepseek.com
  apiKey: sk-xxx

prompt:
  system: |
    你是一名企业尽调分析助手。
    ## 业务范围
    当前支持：公司基本信息获取、尽调要点分析、综合分析、融资方案分析、报告汇总
    ## 规划规约
    涉及 ≥ 2 个 skill 时，先调用 lite_todo_write 发出完整 todo 列表
    ## 输出规范
    最终回答使用 Markdown 格式，涉及金额时保留两位小数

skills:
  directories: [./skills]
  mode: all

tools: [bash, read_file, call_versatile, call_mcp, ask_user, lite_todo_write, cancel_task]
```

```yaml
# edp-config.yaml（尽调报告场景）
scope:
  allowed: "尽调报告生成相关业务（公司基本信息、尽调要点分析、综合分析、融资方案、报告汇总）"
  out_of_scope_message: "抱歉，我仅支持尽调相关业务，请重新描述您的需求。"

limits:
  max_iterations: 25
  max_input_attempts: 3
  interrupt_timeout_seconds: 600
  tasks:
    call_versatile: 100
    call_mcp: 100

todolist_steps:
  - step_id: 1
    content: "收集企业基础信息"
    skill: "company_base_info"
  - step_id: 2
    content: "尽调要点分析"
    skill: "company_due_diligence_key_points"
    depends_on: [1]
  - step_id: 3
    content: "综合分析"
    skill: "company_comprehensive_analysis"
    depends_on: [2]
  - step_id: 4
    content: "融资方案分析"
    skill: "company_financing_analysis"
    depends_on: [3]
  - step_id: 5
    content: "生成尽调报告"
    skill: "company_summary"
    depends_on: [3]

utterances:
  config_path: "./ScriptsConfig.md"

think_chunk:
  mode: fixed_script
```

#### 5.5.3 场景配置异同分析

**edp-agent.yaml 异同**：

| 字段 | 场景一（基金购买） | 场景二（尽调报告） | 相同？ |
|------|------------------|------------------|--------|
| `schema` | `ascend-agent/v1` | `ascend-agent/v1` | ✅ 相同 |
| `name` | `fund-purchase-agent` | `due-diligence-agent` | ❌ 不同 |
| `description` | `基金购买智能助手` | `企业尽职调查报告生成 Agent` | ❌ 不同 |
| `framework.type` | `openjiuwen` | `openjiuwen` | ✅ 相同 |
| `framework.agent` | `deepagent` | `deepagent` | ✅ 相同 |
| `framework.options.maxIterations` | `20` | `25` | ❌ 不同 |
| `framework.options.enableTaskLoop` | `true` | `true` | ✅ 相同 |
| `model.*` | `OpenAI / deepseek-chat` | `OpenAI / deepseek-chat` | ✅ 相同 |
| `prompt.system` | 基金购买角色 + 基金业务范围 | 尽调分析角色 + 尽调业务范围 | ❌ 不同（★ 核心差异） |
| `skills.*` | `[./skills] / all` | `[./skills] / all` | ✅ 相同 |
| `tools` | 7 个标准工具 | 7 个标准工具 | ✅ 相同 |

**edp-config.yaml 异同**：

| 字段 | 场景一（基金购买） | 场景二（尽调报告） | 相同？ |
|------|------------------|------------------|--------|
| `scope.allowed` | 基金购买相关业务 | 尽调报告生成相关业务 | ❌ 不同 |
| `scope.out_of_scope_message` | `正在学习中...` | `抱歉，我仅支持...` | ❌ 不同 |
| `limits.max_iterations` | `20` | `25` | ❌ 不同 |
| `limits.max_input_attempts` | `3` | `3` | ✅ 相同 |
| `limits.interrupt_timeout_seconds` | `300` | `600` | ❌ 不同 |
| `limits.tasks.*` | `100` | `100` | ✅ 相同 |
| `todolist_steps 数量` | 4 个 | 5 个 | ❌ 不同 |
| `todolist_steps 依赖拓扑` | 线性链 | 树形依赖 | ❌ 不同（★ 关键差异） |
| `todolist_steps 内容` | 基金推荐/选择/汇聚/购买 | 企业信息/要点/综合/融资/报告 | ❌ 不同 |
| `todolist_steps step_id 类型` | 整数（1, 2, 3, 4） | 整数（1, 2, 3, 4, 5） | ✅ 相同（与 Python 版 AgentRule.md 一致） |
| `utterances.config_path` | `./ScriptsConfig.md` | `./ScriptsConfig.md` | ✅ 相同 |
| `think_chunk.mode` | `fixed_script` | `fixed_script` | ✅ 相同 |

**todolist_steps 依赖拓扑对比（★ 最关键差异）**：

```
  场景一：线性链（4 步骤）
  
  Step 1 ──→ Step 2 ──→ Step 3 ──→ Step 4
  推荐       筛选       确认       购买
  
  场景二：树形依赖（5 步骤）
  
                    Step 1
                      │
                    Step 2
                      │
                    Step 3
                   ┌──┴──┐
            Step 4      Step 5
            融资方案     报告汇总
```

**汇总**：

```
  ┌──────────────────────────────────────────────────────────────┐
  │                                                              │
  │  ★ 完全相同的部分（框架层，~40%，与业务无关）：                 │
  │  ┌────────────────────────────────────────────────────────┐  │
  │  │  edp-agent.yaml:                                       │  │
  │  │    schema / framework.type / framework.agent /          │  │
  │  │    framework.options.enableTaskLoop / model.* /         │  │
  │  │    skills.* / tools                                    │  │
  │  │                                                         │  │
  │  │  edp-config.yaml:                                       │  │
  │  │    limits.max_input_attempts / limits.tasks.* /         │  │
  │  │    utterances.config_path / think_chunk.mode            │  │
  │  └────────────────────────────────────────────────────────┘  │
  │                                                              │
  │  ★ 业务不同的部分（配置层，~60%，与业务强相关）：               │
  │  ┌────────────────────────────────────────────────────────┐  │
  │  │  edp-agent.yaml:                                       │  │
  │  │    name / description / maxIterations /                 │  │
  │  │    prompt.system（★ Agent 角色与行为规范）               │  │
  │  │                                                         │  │
  │  │  edp-config.yaml:                                       │  │
  │  │    scope.* / limits.max_iterations /                    │  │
  │  │    limits.interrupt_timeout_seconds /                   │  │
  │  │    todolist_steps（★ 步骤数量 + 依赖拓扑 + 内容）        │  │
  │  │                                                         │  │
  │  │  ScriptsConfig.md:                                      │  │
  │  │    业务专有话术 / think_chunk query_patterns             │  │
  │  └────────────────────────────────────────────────────────┘  │
  │                                                              │
  │  ★ Java 代码：完全相同（仅 super("agent-id") 不同）           │
  │                                                              │
  └──────────────────────────────────────────────────────────────┘
```

| 类别 | 占比 | 说明 |
|------|------|------|
| **框架层（完全相同）** | ~40% | schema / framework / model / skills / tools / 通用 limits / 通用话术 |
| **业务层（场景不同）** | ~60% | name / prompt / scope / todolist_steps / 业务话术 / query_patterns |
| **Java 代码** | 0% 差异 | EdpaRuntimeHandler 完全相同 |

> **核心结论**：切换业务场景时，框架层配置是模板化的、可复用的，只需修改业务层配置（prompt.system + todolist_steps + scope + 业务话术）。**业务差异全部由 YAML 承载，Java 代码零修改**。

---


## 六、开发方式（配置驱动 + 深度定制）

> **关键变化**：开发方式从"装配式/挂载式双模式"统一为"OpenJiuwen式开发"。与任何 OpenJiuwen Agent 的开发方式完全一致。OpenJiuwen式开发包含两个层级：**层级一：配置驱动**（零代码）和**层级二：深度定制**（Java 代码），两个层级是叠加关系而非互斥关系。

### 6.1 层级一：配置驱动（零代码，写 YAML 即可）

> **适用角色**：业务分析师、产品经理
> **等同于**：v1.1 的"装配式开发"
> **核心思路**：仅编写 YAML 配置文件，EdpaRuntimeHandler 的 `@PostConstruct` 自动完成 Agent 创建和扩展注册，零 Java 业务代码。

**步骤**：

```
层级一：配置驱动开发流程

  Step 1: 编写 edp-agent.yaml（ascend-agent/v1 标准格式）
  ┌──────────────────────────────────────────────────────────────┐
  │ schema: ascend-agent/v1                                      │
  │ name: edp-agent                                              │
  │ framework:                                                   │
  │   type: openjiuwen                                           │
  │   agent: deepagent                                           │
  │   options: { maxIterations: 15 }                             │
  │ model: { provider: OpenAI, name: deepseek-chat }             │
  │ prompt:                                                      │
  │   system: "你是一个企业尽调分析助手..."                         │
  │ skills: { directories: [./skills], mode: all }               │
  │ tools: [bash, read_file, call_versatile, call_mcp, ...]      │
  └──────────────────────────────────────────────────────────────┘

  Step 2: 编写 edp-config.yaml（EDPAgent 专有配置，可选）
  ┌──────────────────────────────────────────────────────────────┐
  │ scope: { allowed: "尽调报告生成相关业务" }                     │
  │ limits: { max_iterations: 15, tasks: {...} }                 │
  │ todolist_steps: [{ step_id, content, skill }]                │
  │ utterances: { config_path: "./ScriptsConfig.md" }            │
  │ think_chunk: { mode: fixed_script }                           │
  └──────────────────────────────────────────────────────────────┘

  Step 3: EdpaRuntimeHandler @PostConstruct 自动装配
  ┌──────────────────────────────────────────────────────────────┐
  │ AgentFactory.toDeepAgent(edp-agent.yaml) → DeepAgent 实例    │
  │ EdpConfigLoader.load(edp-config.yaml) → EdpConfig            │
  │ EdpaAgentEnhancer.enhance(agent, edpConfig) → 注册扩展       │
  │ → 零 Java 业务代码，纯配置驱动                                 │
  └──────────────────────────────────────────────────────────────┘

  结果：Agent 可运行，具备 EDPAgent 全部能力
```

**EdpaRuntimeHandler 代码（层级一，无任何 Java 业务代码修改）**：

```java
@Component
public class EdpaRuntimeHandler extends OpenJiuwenAgentRuntimeHandler {

    private DeepAgent agent;
    private EdpConfig edpConfig;

    public EdpaRuntimeHandler() {
        super("edp-agent");
    }

    @PostConstruct
    public void init() {
        agent = AgentFactory.toDeepAgent(Path.of("./edp-agent.yaml"));
        edpConfig = EdpConfigLoader.load(Path.of("./edp-config.yaml"));
        EdpaAgentEnhancer.enhance(agent, edpConfig);
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
        return agent;
    }

    @Override
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext ctx) {
        return List.of(
            new CancelRail(),
            new LiteTodoRail(),
            new IterationLimitRail(edpConfig.getLimits()),
            new ExecutionLimitRail(edpConfig.getLimits()),
            new VersatileInterruptRail(),
            new McpInterruptRail(),
            new AskUserTemplateRail(edpConfig.getUtterances()),
            new MemoryRail(),
            new LogRail()
        );
    }
}
```

**启动流程**：

```
agent-runtime Spring Boot Application 启动
    │
    ├── @ComponentScan 发现 EdpaRuntimeHandler
    │   → 注册为 OpenJiuwenAgentRuntimeHandler SPI 实现
    │
    ├── EdpaRuntimeHandler @PostConstruct
    │   → AgentFactory.toDeepAgent(edp-agent.yaml) → DeepAgent 实例
    │   → EdpConfigLoader.load(edp-config.yaml) → EdpConfig
    │   → EdpaAgentEnhancer.enhance(agent, edpConfig) → 注册扩展
    │
    ├── A2aJsonRpcController 自动注册 HTTP 路由
    │
    └── A2aAgentExecutor 自动注册编排器
```

**层级一能做什么**：

| 能力 | 配置项 | 说明 |
|------|--------|------|
| 模型选择 | `edp-agent.yaml → model` | 指定 LLM provider / model / baseUrl |
| 系统提示词 | `edp-agent.yaml → prompt.system` | 定义 Agent 角色和行为规范 |
| 工具声明 | `edp-agent.yaml → tools` | 声明 Agent 可用的工具列表 |
| Skill 加载 | `edp-agent.yaml → skills` | 指定 Skill 目录和加载模式 |
| 业务范围约束 | `edp-config.yaml → scope` | 限制 Agent 业务范围 |
| 执行限制 | `edp-config.yaml → limits` | 最大迭代次数、工具调用次数等 |
| Todo 步骤 | `edp-config.yaml → todolist_steps` | 定义业务步骤目录 |
| 话术模板 | `edp-config.yaml → utterances` | 指定话术配置文件路径 |
| Think Chunk | `edp-config.yaml → think_chunk` | 固定脚本或真实流式 |

> **层级一覆盖了 EDPAgent 90% 的业务需求**。大多数场景下，仅修改 YAML 配置即可完成业务调整，无需编写任何 Java 代码。

### 6.2 层级二：深度定制（Java 代码，registerTool/registerRail）

> **适用角色**：Java 开发工程师
> **等同于**：v1.1 的"挂载式开发"
> **核心思路**：在层级一的基础上，通过 `agent.registerTool()` / `agent.registerRail()` / 覆写 `openJiuwenRails()` 追加 Java 定制逻辑。与 OpenJiuwen 标准深度定制方式完全一致。

**层级二叠加在层级一之上**：

```
层级二：深度定制开发流程

  前置：层级一已完成（YAML 配置 + EdpaRuntimeHandler 自动装配）

  Step 4: 在 @PostConstruct 中追加自定义工具
  ┌──────────────────────────────────────────────────────────────┐
  │ agent.registerTool(new MyAuditTool());                       │
  │ agent.registerTool(new MyReportTool());                      │
  │ → 与 OpenJiuwen 标准 registerTool() 方式完全一致              │
  └──────────────────────────────────────────────────────────────┘

  Step 5: 在 @PostConstruct 中追加自定义 Rail
  ┌──────────────────────────────────────────────────────────────┐
  │ agent.registerRail(new MyComplianceRail());                  │
  │ → 与 OpenJiuwen 标准 registerRail() 方式完全一致              │
  └──────────────────────────────────────────────────────────────┘

  Step 6: 覆写 openJiuwenRails() 注入自定义 Rail
  ┌──────────────────────────────────────────────────────────────┐
  │ @Override                                                    │
  │ protected List<AgentRail> openJiuwenRails(ctx) {             │
  │     List<AgentRail> rails = new ArrayList<>(                 │
  │         super.openJiuwenRails(ctx));                         │
  │     rails.add(new MyComplianceRail());                       │
  │     return rails;                                            │
  │ }                                                            │
  └──────────────────────────────────────────────────────────────┘

  结果：Agent 在层级一基础上叠加了 Java 定制能力
```

**深度定制示例**：

```java
@Component
public class MyCustomEdpaHandler extends OpenJiuwenAgentRuntimeHandler {

    private DeepAgent agent;

    public MyCustomEdpaHandler() {
        super("my-edp-agent");
    }

    @PostConstruct
    public void init() {
        // ★ 层级一：配置驱动（与标准 EdpaRuntimeHandler 一致）
        agent = AgentFactory.toDeepAgent(Path.of("./edp-agent.yaml"));
        EdpConfig edpConfig = EdpConfigLoader.load(Path.of("./edp-config.yaml"));
        EdpaAgentEnhancer.enhance(agent, edpConfig);

        // ★ 层级二：深度定制（追加自定义工具和 Rail）
        agent.registerTool(new MyAuditTool());
        agent.registerTool(new MyReportTool());
        agent.registerRail(new MyComplianceRail());
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
        return agent;
    }

    @Override
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext ctx) {
        List<AgentRail> rails = new ArrayList<>(super.openJiuwenRails(ctx));
        rails.add(new MyDataValidationRail());
        return rails;
    }
}
```

**层级二能做什么**：

| 能力 | API | 说明 |
|------|-----|------|
| 自定义工具 | `agent.registerTool(new MyTool())` | 注册业务专有工具（审计、报告等） |
| 自定义 Rail | `agent.registerRail(new MyRail())` | 注册业务专有拦截器（合规、数据校验等） |
| 覆写 Rail 列表 | `openJiuwenRails(ctx)` | 替换或追加 EDPAgent 专有 Rail |
| 覆写 Agent 创建 | `createOpenJiuwenAgent(ctx)` | 替换 Agent 实例（如使用不同的 DeepAgent 配置） |

### 6.3 两个层级的关系

```
  ┌──────────────────────────────────────────────────────────────┐
  │                                                              │
  │  层级一：配置驱动（零代码）                                    │
  │  ┌────────────────────────────────────────────────────────┐  │
  │  │  edp-agent.yaml + edp-config.yaml                      │  │
  │  │  → AgentFactory.toDeepAgent()                          │  │
  │  │  → EdpaAgentEnhancer.enhance()                         │  │
  │  │  → 覆盖 EDPAgent 90% 业务需求                           │  │
  │  └────────────────────────────────────────────────────────┘  │
  │                         │ 叠加                                │
  │  层级二：深度定制（Java 代码）                                 │
  │  ┌────────────────────────────────────────────────────────┐  │
  │  │  agent.registerTool() / agent.registerRail()           │  │
  │  │  覆写 openJiuwenRails() / createOpenJiuwenAgent()      │  │
  │  │  → 满足剩余 10% 的深度定制需求                           │  │
  │  └────────────────────────────────────────────────────────┘  │
  │                                                              │
  │  ★ 不是"两种模式"，而是同一种开发方式的两个层级                 │
  │  ★ 层级二叠加在层级一之上，层级一可独立使用                     │
  │  ★ 与 OpenJiuwen 标准开发方式完全一致                          │
  │                                                              │
  └──────────────────────────────────────────────────────────────┘
```

| 关系 | 说明 |
|------|------|
| **叠加而非互斥** | 层级二在层级一基础上追加，不是替换层级一 |
| **层级一可独立使用** | 仅 YAML 配置即可运行 Agent |
| **层级二不可独立使用** | 必须先有层级一的 Agent 实例，才能 registerTool/registerRail |
| **与 v1.1 对应** | 层级一 = 装配式开发，层级二 = 挂载式开发 |
| **与 OpenJiuwen 一致** | 两个层级与 OpenJiuwen 标准开发方式完全一致 |

### 6.4 开发方式对比

| 维度 | v1.1 方案（装配式/挂载式） | v2.0 简化方案（OpenJiuwen式） |
|---|---|---|
| **Handler 实现** | `implements AgentRuntimeHandler`（自写 handleTask/getAgentCard/resultAdapter） | `extends OpenJiuwenAgentRuntimeHandler`（基类处理执行/消息/流映射） |
| **Agent 创建** | `DeepAgentPlus.assemble(AgentRule.md, skills, scripts)` | `AgentFactory.toDeepAgent(yamlPath)` + `EdpaAgentEnhancer.enhance()` |
| **配置格式** | AgentRule.md（EDPAgent 专有 Markdown） | edp-agent.yaml（标准 YAML）+ edp-config.yaml（标准 YAML） |
| **工具注册** | `customTools()` 钩子覆写 | `EdpaAgentEnhancer.registerBusinessTools()` + `agent.registerTool()` |
| **Rail 注册** | `customRails()` 钩子覆写 | `openJiuwenRails()` + `agent.registerRail()` |
| **流事件映射** | 自建 StreamEventAdapter | OpenJiuwenStreamAdapter（agent-runtime 提供） |
| **开发模式** | 装配式 / 挂载式（两种独立模式） | 层级一：配置驱动 + 层级二：深度定制（叠加关系） |
| **与 OpenJiuwen 标准一致性** | ❌ 不一致（专有格式 + 专有中间类） | ✅ 完全一致 |

---

## 七、DeepAgent 模块对标分析

### 7.1 对标总表

| EDPAgent 模块 | DeepAgent 对应度 | 简化重构实现策略 | 工作量 |
|-------------|----------------|---------|----------|
| **配置解析** | ✅ `AgentFactory.toDeepAgent(yamlPath)` | ✅ 标准路径创建 DeepAgent，edp-config.yaml 由 EdpConfigLoader 解析 | 极小 |
| **业务范围约束** | ❌ 无对应 | ★ `AskUserTemplateRail` 读取 `EdpConfig.scope` | 小 |
| **规划步骤模板** | ⚠️ 有 TaskPlanningRail | ★ Prompt 注入（edp-agent.yaml `prompt.system`） | 小 |
| **任务依赖关系** | ❌ 无对应 | ★ `LiteTodoWriteTool` 依赖校验 | 中 |
| **LiteTodo 覆盖式 Todo** | ⚠️ 有 Todo 工具组但设计不同 | ★ `LiteTodoWriteTool`，条件性替换 DeepAgent Todo | 中 |
| **执行限制** | ⚠️ 有 maxIterations | ★ 直接映射 + `ExecutionLimitRail` | 小 |
| **话术系统** | ❌ 无对应 | ★ `ScriptsConfigManager` + `ResponseTemplateManager` | 大 |
| **Skill 管理** | ✅ 有 Skill 基础设施 | ✅ 复用 `skillDirectories` + 白名单过滤 | 小 |
| **记忆系统** | ⚠️ 有 MemoryRail 但存储初始化不同 | ★ `MemoryRail`（EDPAgent 自建，含 GaussDB+Redis+ES+Embedding 初始化） | 中 |
| **流事件适配** | ✅ agent-runtime OpenJiuwenStreamAdapter | ✅ 基类处理，无需自建 | 无 |
| **FixedScriptFeeder** | ❌ 无对应 | ★ 新建 | 小 |
| **Versatile 级联中断** | ✅ agent-runtime VersatileAgentRuntimeHandler | ★ `VersatileInterruptRail` 产出 INTERRUPTED | 中 |
| **MCP 沙箱执行** | ⚠️ 有 McpRail 但仅资源管理 | ★ `McpInterruptRail` 扩展沙箱执行 | 中 |
| **Cancel 取消任务** | ⚠️ 有 requestAbort() | ★ `CancelRail` + `CancelTaskTool` | 小 |

### 7.2 消除的中间层组件

| 消除项 | 原估算行数 | 消除原因 |
|--------|----------|---------|
| **DeepAgentPlus.java** | 1,800 | 直接使用 DeepAgent，扩展通过 EdpaAgentEnhancer 注册 |
| **DeepAgentPlusConfig.java** | 350 | 直接用 DeepAgentConfig + EdpConfig |
| **AgentRuleParser.java** | 400 | YAML 解析由 agent-sdk AgentFactory 完成 |
| **AgentRuleSchema.java** | 200 | 配置 POJO 由 AgentSpec + EdpConfig 替代 |
| **PromptBuilder.java** | 150 | systemPrompt 直接在 edp-agent.yaml `prompt.system` 中表达 |
| **StreamEventAdapter.java** | 200 | 由 agent-runtime OpenJiuwenStreamAdapter 覆盖 |
| **合计消除** | **~3,100** | |

### 7.3 新增组件

| 新增项 | 估算行数 | 说明 |
|--------|----------|------|
| **EdpaAgentEnhancer.java** | ~300 | 注册器：注册 5 个业务工具 + 9 个 EDPAgent 自建 Rail（Memory 可选）+ channel/ToolDataChannel + 话术 + Todo + FixedScriptFeeder |
| **EdpConfig.java** | ~150 | EDPAgent 专有配置 POJO |
| **EdpConfigLoader.java** | ~100 | edp-config.yaml 解析 |
| **合计新增** | **~550** | |

---

## 八、Java 项目目录框架

```
edpa-agent/                                 # 单模块 Maven 项目
├── pom.xml                                 # POM：依赖 agent-runtime + agent-sdk + DeepAgent SDK
└── src/main/java/com/huawei/edpa/agent/
    ├── EdpaRuntimeHandler.java             # ★ extends OpenJiuwenAgentRuntimeHandler
    │   ├── @PostConstruct init()           #    AgentFactory.toDeepAgent + EdpaAgentEnhancer.enhance
    │   ├── createOpenJiuwenAgent(ctx)      #    → 返回 DeepAgent 实例
    │   └── openJiuwenRails(ctx)            #    → 返回 EDPAgent 专有 Rail 列表
    │
    ├── EdpaAgentEnhancer.java              # ★ 注册器：注册 EDPAgent 扩展
    │   ├── enhance(agent, edpConfig)       #    注册 5 个业务工具 + 9 个 EDPAgent 自建 Rail（Memory 可选）+ 运行时辅助组件
    │   ├── registerBusinessTools(agent)    #    注册 LiteTodoWrite / CallVersatile / CallMcp / AskUser / Cancel
    │   └── registerBusinessRails(agent)    #    注册 Cancel / LiteTodo / IterationLimit / ExecutionLimit / MCPInterrupt / VersatileInterrupt / AskUserRail / Log / Memory
    │
    ├── EdpConfig.java                      #    EDPAgent 专有配置 POJO
    ├── EdpConfigLoader.java                #    edp-config.yaml 解析
    │
    ├── tools/                              # 内置业务工具 (5 个，只放注册到 DeepAgent 的 Tool)
    │   ├── LiteTodoWriteTool.java
    │   ├── CallVersatileTool.java
    │   ├── CallMcpTool.java
    │   ├── EnhancedAskUserTool.java
    │   └── CancelTaskTool.java
    │
    ├── channel/                            # 工具/Rail 之间共享的会话数据通道
    │   └── ToolDataChannel.java            #    MCP 数据 / VA 结果跨工具传递，不注册为 Tool
    │
    ├── rail/                               # 内置业务 Rail (9 个；对应 Python rail 包)
    │   ├── VersatileInterruptRail.java
    │   ├── McpInterruptRail.java
    │   ├── AskUserTemplateRail.java
    │   ├── CancelRail.java
    │   ├── ExecutionLimitRail.java
    │   ├── IterationLimitRail.java         #    迭代次数限制（读取 edp-config.yaml limits.max_iterations）
    │   ├── LiteTodoRail.java               #    lite_todo_write 持久化 + tool_end 事件发射 + 动态规划校验
    │   ├── LogRail.java                    #    记录模型名和工具列表
    │   └── MemoryRail.java                #    记忆持久化（替代 memory_engine.py）
    │
    ├── stream/                             # 流事件处理
    │   └── FixedScriptFeeder.java          #    固定脚本帧推送
    │   # StreamEventAdapter.java → 消除，由 agent-runtime OpenJiuwenStreamAdapter 覆盖
    │
    ├── utterance/                          # 话术系统
    │   ├── ScriptsConfigManager.java       #    话术配置管理
    │   └── ResponseTemplateManager.java    #    话术模板匹配 + JSON 4 层容错
    │
    └── state/
        └── StateKeys.java                  #    状态 key 常量

├── config/
│   ├── edp-agent.yaml                      # ★ ascend-agent/v1 标准配置
│   ├── edp-config.yaml                     # ★ EDPAgent 专有配置
│   ├── ScriptsConfig.md                    #    话术模板（保留，零改动）
│   └── application.yml                     #    Spring Boot 配置

├── skills/                                 # Skill 目录（运行时通过 agent.register_skill() 动态注册）
│   └── __init__.py                         #    Skill 在运行时从 SkillStore 加载并注册

└── docker/
    ├── Dockerfile
    └── docker-compose.yml
```

> **关键变化**：
> - 消除 DeepAgentPlus / DeepAgentPlusConfig / AgentRuleParser / AgentRuleSchema / PromptBuilder / StreamEventAdapter 6 个中间层类
> - 新增 EdpaRuntimeHandler（extends OpenJiuwenAgentRuntimeHandler）、EdpaAgentEnhancer、EdpConfig、EdpConfigLoader 4 个类
> - 新增 IterationLimitRail / LiteTodoRail / LogRail / MemoryRail 4 个 Rail（原误标为 DeepAgent 内置，实际为 EDPAgent 自建）
> - 新增 channel/ToolDataChannel（运行时数据通道，MCP 数据 → Versatile 委托注入，不计入工具）
> - 配置文件从 AgentRule.md（专有 Markdown 格式）改为 edp-agent.yaml（标准 YAML）+ edp-config.yaml（标准 YAML）
> - EdpaRuntimeHandler 从 `implements AgentRuntimeHandler` 改为 `extends OpenJiuwenAgentRuntimeHandler`
> - Skills 目录从静态子目录改为运行时动态注册

---

## 九、Python → Java 模块映射总表

| Python 源位置 | Java 目标位置 | 迁移类型 | 改动量 |
|--------------|-------------|---------|--------|
| `a2a_service/agents/EDPAgent/agent.py` | — | ★ 消除，直接使用 DeepAgent；内部 `_StreamProcessor`（17 种事件状态机）由 agent-runtime OpenJiuwenStreamAdapter 覆盖；`_reset_session_after_cancel`（三层清理）由 CancelRail 覆盖；`_drain_ui_notices` 由 AskUserTemplateRail 覆盖 | 无 |
| `a2a_service/agents/EDPAgent/config.py` | — | ★ 消除，50+ 环境变量映射到 edp-agent.yaml + edp-config.yaml + application.yml | 无 |
| `a2a_service/agents/EDPAgent/agent_rule.py` | — | ★ 消除，用 AgentFactory + EdpConfigLoader | 无 |
| `a2a_service/agents/EDPAgent/prompt.py` | — | ★ 消除，systemPrompt 在 YAML 中表达（含 MCP 先行架构 + 工具使用规则 + 执行规则强调） | 无 |
| `a2a_service/agents/EDPAgent/adapter.py` | — | ★ 消除，`inject_query()` / `extract_user_query()` 由 ToolDataChannel 覆盖（Versatile 委托请求注入） | 无 |
| `a2a_service/agents/EDPAgent/state_keys.py` | `edpa-agent/state/StateKeys.java` | ✅ 常量迁移 | 极小 |
| `a2a_service/agents/EDPAgent/fixed_script_feeder.py` | `edpa-agent/stream/FixedScriptFeeder.java` | ★ 重写为 Java | 小 |
| `a2a_service/agents/EDPAgent/memory_engine.py` | `edpa-agent/rail/MemoryRail.java` | ★ 重写为 MemoryRail（GaussDB + Redis + ES + Embedding 初始化逻辑需迁移；可选注册） | 中 |
| `a2a_service/agents/EDPAgent/tool/` (5 个工具) | `edpa-agent/tools/` (5 个 Java 工具类) + `edpa-agent/channel/ToolDataChannel.java` | ★ 重写为 Java；ToolDataChannel 为运行时数据通道，不计入工具 | 中 |
| `a2a_service/agents/EDPAgent/rail/` (9 个 Rail) | `edpa-agent/rail/` (9 个 Java 类) | ★ 重写为 Java（LiteTodoRail、LogRail、IterationLimitRail 为 EDPAgent 自建，非 DeepAgent 内置；MemoryRail 可选） | 大 |
| — | `edpa-agent/EdpaRuntimeHandler.java` | ★ 新建（extends OpenJiuwenAgentRuntimeHandler） | 小 |
| — | `edpa-agent/EdpaAgentEnhancer.java` | ★ 新建（扩展注册器） | 中 |
| — | `edpa-agent/EdpConfig.java` + `EdpConfigLoader.java` | ★ 新建（专有配置） | 小 |
| `AgentRule.md` | `config/edp-agent.yaml` + `config/edp-config.yaml` | ★ 格式转换（Markdown → YAML） | 小 |
| `ScriptsConfig.md` | `config/ScriptsConfig.md` | ✅ 零改动直接复用 | 无 |
| `skills/` 目录 | `skills/` 目录 | ✅ 零改动直接复用 | 无 |

> **由 agent-runtime 覆盖，不再迁移的 Python 源码**：
> - `common/events.py` → OpenJiuwenStreamAdapter 覆盖
> - `common/logger.py` → Spring Boot SLF4J 覆盖
> - `common/crypto.py` → Jasypt 覆盖
> - `common/redis_client.py` + `redis_task_store.py` → RedisTaskStore 覆盖
> - `orchestrator/executor.py` → A2aAgentExecutor 覆盖
> - `orchestrator/agent_adapter.py` → OpenJiuwenStreamAdapter 覆盖
> - `orchestrator/user_router.py` + `sse_helpers.py` → A2aJsonRpcController 覆盖
> - `app.py` + `main.py` + `config.py` → Spring Boot Application 覆盖
> - `versatile_adapter/` 全部 → VersatileAgentRuntimeHandler 覆盖

---

## 十、代码量估算

### 10.1 Python 源码行数统计（仅 Agent 层）

| 模块 | Python 文件 | 行数 |
|------|------------|------|
| **EDPAgent 核心** | agent.py + config.py + agent_rule.py + prompt.py + adapter.py + state_keys.py + fixed_script_feeder.py + memory_engine.py | 2,084 |
| **工具层** | tool/ (5 个工具) | 667 |
| **Rail 层** | rail/ (9 个 Rail: CancelRail, IterationLimitRail, ExecutionLimitRail, LiteTodoRail, McpInterruptRail, VersatileInterruptRail, AskUserRail, LogRail, MemoryRail) | 1,947 |
| **Agent 层 Python 总计** | | **≈4,698** |

### 10.2 Java 代码量估算（单模块 edpa-agent）

| Java 类 | 估算行数 | 说明 |
|---------|----------|------|
| **SPI 入口** | | |
| `EdpaRuntimeHandler.java` | 100 | extends OpenJiuwenAgentRuntimeHandler，基类处理大部分逻辑 |
| **扩展注册器** | | |
| `EdpaAgentEnhancer.java` | 300 | 注册 5 个业务工具 + 9 个 EDPAgent 自建 Rail（Memory 可选）+ 话术 + Todo + FixedScriptFeeder + channel/ToolDataChannel |
| `EdpConfig.java` | 150 | EDPAgent 专有配置 POJO |
| `EdpConfigLoader.java` | 100 | edp-config.yaml 解析 |
| **工具（5 个业务 Tool）** | | |
| `LiteTodoWriteTool.java` | 500 | 覆盖式 Todo + 动态 JSON Schema |
| `CallVersatileTool.java` | 150 | VA 工作流调用 |
| `CallMcpTool.java` | 120 | MCP 沙箱执行 |
| `EnhancedAskUserTool.java` | 120 | 增强 ask_user |
| `CancelTaskTool.java` | 100 | 任务取消 |
| **运行时辅助组件** | | |
| `ToolDataChannel.java` | 80 | channel 包下的数据通道（MCP 数据 → Versatile 委托注入），不注册为 Tool |
| **Rail（9 个，MemoryRail 可选）** | | |
| `VersatileInterruptRail.java` | 600 | VA 级联中断 |
| `AskUserTemplateRail.java` | 450 | 话术模板 + scope 校验 + _drain_ui_notices |
| `McpInterruptRail.java` | 400 | MCP 沙箱拦截 |
| `ExecutionLimitRail.java` | 300 | Per-tool 调用限制 + tool/todo 事件补齐 + skill 追踪 |
| `CancelRail.java` | 100 | 取消任务拦截 + _reset_session_after_cancel 三层清理 |
| `IterationLimitRail.java` | 80 | 迭代次数限制（读取 edp-config.yaml limits.max_iterations） |
| `LiteTodoRail.java` | 150 | lite_todo_write 持久化 + tool_end 事件发射 + 动态规划校验 |
| `LogRail.java` | 50 | 记录模型名和工具列表 |
| `MemoryRail.java` | 200 | 记忆持久化（GaussDB + Redis + ES + Embedding） |
| **话术系统** | | |
| `ScriptsConfigManager.java` | 300 | 话术配置解析 + SafeDict |
| `ResponseTemplateManager.java` | 200 | JSON 4 层容错 + 模板匹配 |
| **流事件** | | |
| `FixedScriptFeeder.java` | 200 | ThinkChunk 固定脚本帧推送 |
| **其他** | | |
| `StateKeys.java` | 30 | 状态 key 常量 |
| | **~4,230** | |

### 10.3 配置文件

| 资源 | 操作 | 说明 |
|------|------|------|
| `config/edp-agent.yaml` | ★ 新建 | ascend-agent/v1 格式，约 50 行 |
| `config/edp-config.yaml` | ★ 新建 | EDPAgent 专有配置，约 50 行 |
| `config/ScriptsConfig.md` | ✅ 直接复用 | 零改动 |
| `skills/` 目录 | ✅ 直接复用 | 零改动 |
| `config/application.yml` | ★ 新建 | 约 50 行 |
| `docker/Dockerfile` | ★ 新建 | 约 30 行 |
| `docker/docker-compose.yml` | ★ 新建 | 约 40 行 |
| `pom.xml` | ★ 新建 | 约 150 行 |

### 10.4 汇总

| 项目 | 估算行数 | 备注 |
|-----------|---------|------|
| `edpa-agent`（Agent 层 Java） | ~4,230 | 消除 DeepAgentPlus 等中间层，新增 4 个 EDPAgent 自建 Rail + channel/ToolDataChannel |
| 配置文件 | ~370 | YAML + Spring Boot + Docker |
| `config/` + `skills/` 复用 | 0 | 零改动 |
| **EDPAgent 项目总代码量** | **≈4,600** | |

> **关键变化**：
> - v1.1 方案约 7,780 行 → v2.0 简化方案约 4,600 行，缩减 **41%**
> - 消除 DeepAgentPlus / DeepAgentPlusConfig / AgentRuleParser / AgentRuleSchema / PromptBuilder / StreamEventAdapter 6 个中间层类（~3,100 行）
> - 新增 EdpaRuntimeHandler / EdpaAgentEnhancer / EdpConfig / EdpConfigLoader 4 个类（~550 行）
> - 新增 IterationLimitRail / LiteTodoRail / LogRail / MemoryRail 4 个 Rail（~480 行）+ channel/ToolDataChannel（~80 行）
> - EdpaRuntimeHandler 从 200 行减少到 100 行（基类处理大部分逻辑）

---
## 十一、工具重构映射

| EDPAgent 工具 | 简化重构实现 | 关键入参 | 说明 |
|---|---|---|---|
| `call_mcp` | `CallMcpTool` + `McpInterruptRail` + `channel/ToolDataChannel` | `script_command`、`script_params` | **Tool 只保留 schema 和 MCP intent**；沙箱执行、`mcp_required_params` 注入、`mcp_products_data` / `mcp_to_versatile_information` / `history_info` / `history_params` / `response_template` 等状态维护全部放到 `McpInterruptRail`。 |
| `call_versatile` | `CallVersatileTool` + `VersatileInterruptRail` + `channel/ToolDataChannel` | `query_description`、`query_intent`、`query_response_analysis_scripts`、`response_template_keys`、`notice_context`、`input_key` | **Tool 只保留 schema 和 VA delegate intent**；首次拦截写入 `pending_delegate` / `pending_tool_context`、interrupt、Cascade 恢复、归一化脚本执行、ToolDataChannel 读取、`ui_notice` 和 todo 事件全部放到 `VersatileInterruptRail`。 |
| `ask_user` | `EnhancedAskUserTool` + `AskUserRail/AskUserTemplateRail`；未配置话术时降级 DeepAgent 原生 `ask_user` | `question`、`response_template_keys`、`response_template_status`、`response_template_vars` | 有 EDP 话术配置时使用增强工具和 Rail，支持话术模板匹配 + JSON 容错 + 中断恢复；`utterances` 未配置时不自建增强语义，降级复用 DeepAgent 原生 `ask_user`。 |
| `cancel_task` | `CancelTaskTool` + `CancelRail` | `reason` | **Tool 只保留 schema 和 cancel intent**；固定话术响应、runtime cancel / force finish、`_reset_session_after_cancel` 三层清理全部放到 `CancelRail`。 |
| `lite_todo_write` | `LiteTodoWriteTool` + `LiteTodoRail`；未配置 EDP Todo 步骤时降级 DeepAgent 原生 todo 能力 | `todos: [{step_id, status}]` | 有 `todolist_steps` 时使用 EDP 轻量 Todo；Schema 从 edp-config.yaml 动态生成，LiteTodoRail 负责持久化 + tool_end 事件发射 + 动态规划校验；未配置时不自建轻量 Todo，降级复用 DeepAgent 原生 `todo_*` 能力。 |
| `read_file` | DeepAgent `read_file` | — | **不再实现 EDPAgent 自定义工具**；直接复用 DeepAgent 系统工具，不计入 5 个 EDPAgent 业务工具。 |
| `execute_cmd` | DeepAgent `bash` / `execute_cmd` | — | **不再实现 EDPAgent 自定义工具**；直接复用 DeepAgent 系统工具，不计入 5 个 EDPAgent 业务工具。 |

> **MCP 先行架构**：Python 版 `prompt.py`、`call_mcp.py`、`mcp_interrupt_rail.py`、`call_versatile.py` 和 `versatile_interrupt_rail.py` 描述了 MCP 先行架构——先 `call_mcp` 获取数据 → session / ToolDataChannel 存储 → `call_versatile` 自动读取 MCP 数据并注入委托请求。Java 版通过 `channel/ToolDataChannel.java` 实现这一数据通道；它不是 Tool，不放在 `tools/`，也不注册到 DeepAgent。

### 11.1 工具简化原则（已确认）

1. `call_mcp`、`call_versatile`、`cancel_task` 的 Tool 层只保留 **Schema + intent**，不承载业务执行、状态清理、事件发射和跨工具数据流转；这些逻辑统一放入对应 Rail。
2. `ask_user`、`lite_todo_write` 支持 **可选降级**：配置存在时启用 EDPAgent 增强语义；配置缺失时复用 DeepAgent 原生能力，避免重复实现通用能力。
3. `read_file`、`execute_cmd` / `bash` **不再实现 EDPAgent 自定义工具**，直接复用 DeepAgent 系统工具，并明确不计入 5 个 EDPAgent 业务工具。

### 11.2 运行时辅助组件

| 组件 | 推荐 Java 位置 | 说明 |
|---|---|---|
| `ToolDataChannel` | `channel/ToolDataChannel.java` | 工具/Rail 之间共享的会话数据通道，负责 store/get/getAll/getKeys/remove/clear，不是 Tool。 |
| `ScriptsConfigManager` / `ResponseTemplateManager` | `utterance/` | 读取 `ScriptsConfig.md`，提供话术 key 查询、模板变量替换和 JSON 容错。 |
| `FixedScriptFeeder` | `stream/FixedScriptFeeder.java` | 固定话术帧推送，支持 query pattern、resume/executing/planning 阶段、字符切片、token 间隔和 drain_all。 |

---

## 十二、Rail 体系重构

### 12.1 Rail 注册顺序

```
注册顺序（优先级从高到低）：

  1. CancelRail                    — EDPAgent 自建    (priority=10)
  2. LiteTodoRail                  — EDPAgent 自建    (priority=35)
  3. IterationLimitRail            — EDPAgent 自建    (priority=40)
  4. ExecutionLimitRail            — EDPAgent 自建    (priority=40)
  5. McpInterruptRail              — EDPAgent 自建    (priority=50)
  6. VersatileInterruptRail        — EDPAgent 自建    (priority=50)
  7. AskUserRail/AskUserTemplateRail — EDPAgent 自建  (priority=50，对应 Python AskUserRail)
  8. SubagentRail                  — DeepAgent 内置   (自动)
  9. SecurityRail                  — DeepAgent 内置   (自动)
 10. AgentModeRail                 — DeepAgent 内置   (自动)
 11. SessionRail                   — DeepAgent 内置   (自动)
 12. ProgressiveToolRail           — DeepAgent 内置   (自动)
 13. MemoryRail                    — EDPAgent 自建    (priority=100)
 14. LogRail                       — EDPAgent 自建    (priority=1000)
```

> **修正**：IterationLimitRail、LogRail、MemoryRail 在 v1.1 方案中被误标为"DeepAgent 内置"，实际为 EDPAgent 自建的业务 Rail。LiteTodoRail 在 v1.1 方案中遗漏，实际负责 lite_todo_write 持久化 + tool_end 事件发射 + 动态规划校验。

### 12.2 业务 Rail 功能清单

| Rail | 拦截工具 | 首次拦截行为 | 恢复行为 |
|---|---|---|---|
| `CancelRail` | `cancel_task` | 注入取消话术 → `requestForceFinish()` + `_reset_session_after_cancel` 三层清理（Redis checkpoint 释放 + 内存 session state 清空 + context_engine 内存池清理） | — |
| `LiteTodoRail` | `lite_todo_write` | 持久化 Todo 列表 + tool_end 事件发射 + 动态规划校验 | — |
| `IterationLimitRail` | —（before_invoke） | 读取 edp-config.yaml `limits.max_iterations`，迭代次数超限 → 强制完成 | — |
| `McpInterruptRail` | `call_mcp` | 沙箱执行脚本，注入 `mcp_required_params`，写入 `mcp_products_data` / `mcp_to_versatile_information` / `history_info` / `history_params` / `response_template`，然后 `reject(result)` | — |
| `VersatileInterruptRail` | `call_versatile` | 写入 `pending_delegate` / `pending_tool_context` → interrupt()；特殊发射 `todo_start`，避免中断时序倒挂 | 读取 `cascade_result`，从 ToolDataChannel 读取 `input_key`，执行归一化脚本，处理 `result_key` / `result_message` / `ui_notice`，特殊发射 `todo_end` |
| `AskUserRail/AskUserTemplateRail` | `ask_user` | 话术模板匹配 → interrupt() + `_drain_ui_notices` 从 session.ui_notices drain 指定 event 类型的提示话术；支持 question / response_template_keys / response_template_status / response_template_vars | reject(tool_result=user_input) |
| `ExecutionLimitRail` | —（before/after tool） | per-tool 调用计数 + 超限强制完成 + `tool_start` / `tool_end` / `todo_start` / `todo_end` 事件补齐 + skill 追踪；抑制 `lite_todo_write` / `read_file` / `ask_user` 等内部工具事件 | — |
| `MemoryRail` | —（after_invoke） | GaussDB + Redis + ES + Embedding 记忆持久化；按 `memory_enabled` 可选注册 | — |
| `LogRail` | —（after_invoke） | 记录模型名和工具列表 | — |

### 12.3 Python 同构细节补充

- `AskUserRail/AskUserTemplateRail` 的 JSON 容错需要覆盖 Python 版的关键行为：去外层引号、中文引号归一、中文冒号归一、非法 JSON key/value 正则抽取、`response_template_keys` 容错、`response_template_vars` 容错、模板变量缺失时安全置空。
- `VersatileInterruptRail` 的 `todo_start` / `todo_end` 需要特殊处理：`call_versatile` 首轮会 interrupt，不能完全依赖普通 `after_tool_call` 时序，否则事件顺序会倒挂。
- `ExecutionLimitRail` 不只是调用次数限制，还承担 tool/todo 流事件补齐和 skill 追踪；内部工具事件需要抑制，避免 UI 展示无意义中间工具。
- `ToolDataChannel` 属于运行时辅助组件，不属于工具清单；Java 推荐放在 `channel/ToolDataChannel.java`，Python 虽位于 `rail/tool_data_channel.py`，但其职责是会话数据通道。

---

## 十三、Skill 管理

### 13.1 Skill 加载逻辑

```
EdpaRuntimeHandler.init():
  │
  ├── 1. 读取 edp-agent.yaml skills.directories / skills.mode
  │     → EdpAgentConfig.Skills
  │     → DeepAgentConfig.skillDirectories / DeepAgentConfig.skillMode（诊断与后续扩展字段）
  │
  ├── 2. 按 edp-agent.yaml 所在目录解析相对路径
  │     → 当前穿刺目录：agent-store/edp-agent-trae/spike/src/main/resources/skills
  │     → 配置写法：skills.directories: ["./skills"]
  │
  ├── 3. 调用 OpenJiuwen 原生注册接口
  │     → deepAgent.getAgent().registerSkill(resolvedSkillRoot)
  │     → 目录下每个子目录的 SKILL.md 进入 SkillManager
  │
  ├── 4. DeepAgent SkillUtil 生成 Skill prompt
  │     → prompt 中包含 Skill 名称、描述和 Skill directory file path
  │
  └── 5. 启动日志穿刺点
        → Skill load probe: configuredDirectory / resolvedDirectory / exists
        → Skill load completed: hasSkill / skillCount / skillNames / mode
```

### 13.2 当前 Skill 目录约定

| 项 | 当前取值 | 说明 |
|---|---|---|
| 配置文件 | `spike/src/main/resources/edp-agent.yaml` | `skills.directories` 使用相对路径 |
| 配置值 | `./skills` | 相对 `edp-agent.yaml` 所在目录解析 |
| 实际目录 | `spike/src/main/resources/skills` | 当前手机银行 Skill 根目录 |
| Skill 文件 | `*/SKILL.md` | 每个 Skill 子目录必须包含 `SKILL.md` |
| 加载模式 | `all` | 当前加载全部 Skill；后续可扩展 whitelist |

当前穿刺已确认加载 4 个 Skill：

```text
fund_planning_skill
interact_finance_rec_skill
product_recommend_skill
product_select_skill
```

### 13.3 Skill 加载穿刺验收

| 穿刺点 | 验收标准 | 验证方式 |
|---|---|---|
| 配置解析 | `edp-agent.yaml` 可解析 `skills.directories=["./skills"]`、`skills.mode=all` | `P18SkillLoadingSpikeTest.P18-1` |
| 目录解析 | `./skills` 解析到 `src/main/resources/skills`，且目录存在 | 启动日志 `Skill load probe` |
| 原生注册 | `SkillUtil.hasSkill()==true`，`SkillManager.count()==4` | `P18SkillLoadingSpikeTest.P18-2` |
| Prompt 注入 | Skill prompt 包含 `product_recommend_skill` 和 `Skill directory file path` | `P18SkillLoadingSpikeTest.P18-3` |
| 可观测性 | 启动日志打印 `Skill load completed: hasSkill=true, skillCount=4` | `logs/run/run.log` |

---

## 十四、话术系统保留

### 14.1 ScriptsConfig.md 结构（兼容现有）

```yaml
---
scripts:
  tool_start: "正在调用：{tool_name}"
  tool_end: "{tool_name} 执行完成"
  todolist_start: "规划任务清单"
  todolist_end: "任务规划完成"
  interrupt_start: "需要您确认以下信息"
  request_start: "您的请求已收到。"

  # 基金推荐话术
  product_recommend_success: "我找到以上您可能感兴趣的基金产品，可以告诉我购买哪支产品及购买金额，如果不满意请告诉我重新推荐。"
  product_recommend_empty: "根据您的条件没有找到合适产品，您可以从以下产品中选择一个或者重新筛选。"
  product_recommend_no_card: "当前账户没有绑定银行卡"

  # 基金选择话术
  fund_select_confirm: "请确认是否购买{amount}元{productName}基金产品"
  fund_select_missing_product: "您可以告诉我想要购买第几支产品"
  fund_select_missing_amount: "请问您购买的金额是多少"
  fund_select_invalid: "抱歉没有理解您的意思，请重新输入"

  # 尽调话术
  base_info_success: "企业基本信息采集完成。"
  key_points_success: "已完成尽调要点分析，识别出{count}个关键风险点"
  report_success: "尽调报告已生成，包含{sections}个章节"

  # 通用话术
  out_of_scope: "正在学习中，暂不支持该业务。"
  task_cancelled: "好的，已为您取消当前操作。如需其他帮助，请随时告诉我。"
  cancel_confirm: "确认要取消当前操作吗？"

think_chunk_mode: fixed_script
think_chunk_fixed_scripts:
  enabled: true
  chars_per_frame: 4
  tokens_between_frames: 2
  min_interval_ms: 50
  default_scripts:
    - "正在分析您的需求..."
    - "正在规划最优方案..."
  query_patterns:
    - keywords: ["推荐", "理财", "产品", "筛选"]
      scripts:
        - "正在搜索理财产品..."
    - keywords: ["购买", "买", "下单", "确认"]
      scripts:
        - "正在确认购买信息..."
    - keywords: ["余额", "查询", "账户", "转账"]
      scripts:
        - "正在查询账户信息..."
  execution_scripts:
    - "正在分析执行结果..."
  resume_scripts:
    - "当前业务步骤已为您处理完毕"
  scripts:
    - "正在分析您的需求..."
---
```

> **修正**：v1.1 方案中 ScriptsConfig.md 使用了分组结构（`tools:` / `task:` / `interrupt:` / `due_diligence:`），实际 Python 版使用的是**扁平 scripts 字典**（如 `product_recommend_success`、`fund_select_confirm` 等），没有分组嵌套。

### 14.2 话术模板规范

| 能力 | 说明 |
|---|---|
| `{varName}` 占位符 | 标准变量替换 |
| `{sys_xxx}` 系统变量 | 从 session state 中读取 |
| `_SafeDict` 安全格式化 | 缺失变量替换为空串，不抛异常 |
| JSON 容错 4 层解析 | 中文引号→外引号剥离→全角冒号→正则兜底 |
| `confirm` 特殊处理 | 持久化 `selected_product` 到 session state |

---

## 十五、功能完整性校验清单

> **由 agent-runtime 覆盖的功能**（不在 EDPAgent 交付范围）：
> - HTTP 入口 + JSON-RPC 2.0 路由 → A2aJsonRpcController
> - Task 生命周期管理 + INTERRUPTED 检测 → A2aAgentExecutor
> - OutputSchema → AgentExecutionResult 映射 → OpenJiuwenStreamAdapter
> - Versatile 适配 → VersatileAgentRuntimeHandler
> - Task 持久化 → InMemoryTaskStore / RedisTaskStore
> - SSE 流式推送 → agent-runtime 内置
>
> **由 agent-sdk 覆盖的功能**（不在 EDPAgent 交付范围）：
> - YAML 配置解析 → AgentFactory.toDeepAgent()
> - DeepAgent 标准创建路径 → AgentFactory + DeepAgentConfig
>
> 以下清单仅覆盖 **EDPAgent Agent 层** 的功能完整性。

### 15.1 核心引擎

| # | 功能 | Python EDPAgent | Java 简化重构 | 状态 |
|---|---|---|---|---|
| 1 | ReAct 推理循环 | ✅ ReActAgent | ✅ DeepAgent（内嵌 ReActAgent） | 继承 |
| 2 | 流式输出 | ✅ agent.stream() | ✅ DeepAgent.stream() | 继承 |
| 3 | 会话持久化 | ✅ Redis Checkpointer | ✅ DeepAgent Session | 继承 |
| 4 | 上下文引擎 | ✅ 滑动窗口 + 压缩器 | ✅ ContextEngine + DialogueCompressor | 继承 |
| 5 | 取消恢复 | ✅ _reset_session_after_cancel（三层清理） | ✅ CancelRail + DeepAgent Session | 重构 |
| 6 | _StreamProcessor（17 种事件状态机） | ✅ agent.py 内部类 | ✅ OpenJiuwenStreamAdapter 覆盖 | 继承 |
| 7 | _drain_ui_notices（话术 drain） | ✅ agent.py 内部函数 | ✅ AskUserTemplateRail 覆盖 | 重构 |
| 8 | SysOperationCard 注册 | ✅ local/sandbox 模式切换 + 沙箱创建 + skills.zip 打包上传 | ✅ DeepAgent SkillUseRail + SysOperationCard | 重构 |
| 9 | DialogueCompressor 配置 | ✅ _build_dialogue_compressor_processors() | ✅ DeepAgent context_processors | 继承 |
| 10 | LLM Sampling 覆盖 | ✅ _LLM_TEMPERATURE_OVERRIDE=0.1 等 | ✅ edp-config.yaml llm_sampling | **简化** |

### 15.2 工具

| # | 工具 | Python EDPAgent | Java 简化重构 | 状态 |
|---|---|---|---|---|
| 11 | call_mcp | ✅ | ✅ CallMcpTool | 重构 |
| 12 | call_versatile | ✅ | ✅ CallVersatileTool | 重构 |
| 13 | ask_user（含话术） | ✅ | ✅ EnhancedAskUserTool | 重构 |
| 14 | cancel_task | ✅ | ✅ CancelTaskTool | 重构 |
| 15 | lite_todo_write | ✅ | ✅ LiteTodoWriteTool | 重构 |
| 16 | ToolDataChannel（MCP→Versatile 数据通道） | ✅ rail/tool_data_channel.py | ✅ channel/ToolDataChannel.java | 重构；运行时辅助组件，不计入工具 |
| 17 | read_file | ✅ SysOp | ✅ DeepAgent FilesystemTool | 复用 DeepAgent；不再自建 EDPAgent 工具 |
| 18 | execute_cmd | ✅ SysOp | ✅ DeepAgent BashTool | 复用 DeepAgent；不再自建 EDPAgent 工具 |
| 19 | 文件编辑 | ❌ | ✅ DeepAgent FilesystemTool | **新增** |
| 20 | Web 搜索 | ❌ | ✅ DeepAgent WebSearchTool | **新增** |
| 21 | 子Agent委派 | ❌ | ✅ DeepAgent TaskTool | **新增** |

### 15.3 Rails

| # | Rail | Python EDPAgent | Java 简化重构 | 状态 |
|---|---|---|---|---|
| 22 | CancelRail | ✅ | ✅ CancelRail | 重构 |
| 23 | LiteTodoRail | ✅ | ✅ LiteTodoRail | 重构 |
| 24 | IterationLimitRail | ✅ | ✅ IterationLimitRail（EDPAgent 自建） | 重构 |
| 25 | ExecutionLimitRail | ✅ | ✅ ExecutionLimitRail | 重构 |
| 26 | MCPInterruptRail | ✅ | ✅ McpInterruptRail | 重构 |
| 27 | VersatileInterruptRail | ✅ | ✅ VersatileInterruptRail | 重构 |
| 28 | AskUserRail（话术模板） | ✅ | ✅ AskUserTemplateRail | 重构 |
| 29 | LogRail | ✅ | ✅ LogRail（EDPAgent 自建） | 重构 |
| 30 | MemoryRail | ✅ | ✅ MemoryRail（EDPAgent 自建，含 GaussDB+Redis+ES+Embedding 初始化） | 重构 |
| 31 | SecurityRail | ❌ | ✅ DeepAgent 内置 | **新增** |
| 32 | ProgressiveToolRail | ❌ | ✅ DeepAgent 内置 | **新增** |

### 15.4 配置与话术

| # | 功能 | Python EDPAgent | Java 简化重构 | 状态 |
|---|---|---|---|---|
| 33 | 配置解析 | ✅ AgentRuleParser | ✅ AgentFactory + EdpConfigLoader | **简化** |
| 34 | 50+ 环境变量 | ✅ config.py | ✅ edp-agent.yaml + edp-config.yaml + application.yml | **简化** |
| 35 | ScriptsConfig 话术 | ✅ | ✅ ScriptsConfigManager | 重构 |
| 36 | 话术模板变量替换 | ✅ _SafeDict | ✅ ResponseTemplateManager | 重构 |
| 37 | JSON 4 层容错 | ✅ | ✅ | 重构 |
| 38 | confirm selected_product | ✅ | ✅ session.updateState() | 重构 |
| 39 | Think Chunk 固定脚本 | ✅ FixedScriptFeeder | ✅ FixedScriptFeeder | 重构 |
| 40 | Think Chunk 真实流式 | ✅ real_stream | ✅ DeepAgent 原生流式 | 继承 |

### 15.5 Skills

| # | 功能 | Python EDPAgent | Java 简化重构 | 状态 |
|---|---|---|---|---|
| 41 | SKILL.md 加载 | ✅ SkillLoader | ✅ DeepAgent SkillUseRail | **简化** |
| 34 | YAML 前置元数据解析 | ✅ | ✅ | 继承 |
| 35 | Skill prompt 注入 | ✅ | ✅ DeepAgent extraPromptSections | 继承 |
| 36 | 白名单/全量加载 | ✅ skills.mode | ✅ | 重构 |
| 37 | Skill 工具自动关联 | ✅ | ✅ EdpaAgentEnhancer | 重构 |

### 15.6 记忆

| # | 功能 | Python EDPAgent | Java 简化重构 | 状态 |
|---|---|---|---|---|
| 38 | Redis KV 存储 | ✅ | ✅ DeepAgent MemoryRail | 继承 |
| 39 | GaussDB 关系存储 | ✅ | ✅ | 继承 |
| 40 | ES 向量检索 | ✅ | ✅ | 继承 |
| 41 | 记忆变量注入 Prompt | ✅ | ✅ DeepAgent 内置 | 继承 |

### 15.7 流事件

| # | 功能 | Python EDPAgent | Java 简化重构 | 状态 |
|---|---|---|---|---|
| 42 | 17 种 AgentEvent | ✅ _StreamProcessor | ✅ OpenJiuwenStreamAdapter | **简化** |
| 43 | ThinkStart/Chunk/End | ✅ | ✅ FixedScriptFeeder | 重构 |
| 44 | TodoList/Todo 事件 | ✅ | ✅ LiteTodoWriteTool | 重构 |
| 45 | ToolStart/Status/End | ✅ | ✅ DeepAgent OutputSchema | 继承 |
| 46 | InterruptStart/End | ✅ | ✅ VersatileInterruptRail | 重构 |
| 47 | FinalAnswer 事件 | ✅ | ✅ DeepAgent OutputSchema | 继承 |
| 48 | ConversationStart/End | ✅ | ✅ | 继承 |
| 49 | DelegateRequest | ✅ | ✅ | 继承 |

### 15.8 新增功能（DeepAgent 自带）

| # | 功能 | 来源 |
|---|---|---|
| 50 | TaskLoop 多轮自动循环 | DeepAgent |
| 51 | CompletionPromise 完成检测 | DeepAgent |
| 52 | Steering 引导注入 | DeepAgent |
| 53 | FollowUp 追问队列 | DeepAgent |
| 54 | AgentMode Plan/Normal 切换 | DeepAgent |
| 55 | 权限系统 PermissionEngine | DeepAgent |
| 56 | LSP 代码智能 | DeepAgent |
| 57 | 浏览器自动化 | DeepAgent |
| 58 | MCP 资源管理 | DeepAgent |
| 59 | 渐进式工具发现 | DeepAgent |
| 60 | 多模态（视觉/音频） | DeepAgent |

---

## 十六、风险与应对

| 风险 | 影响 | 应对 |
|---|---|------|
| DeepAgent 仍在快速迭代，API 可能变动 | EDPAgent 扩展需频繁适配 | 锁定 DeepAgent 版本，EdpaAgentEnhancer 作为隔离层 |
| Python 到 Java 的生态差异（如沙箱执行） | MCP/Versatile 调用方式不同 | 通过 SysOperation 抽象层适配 |
| edp-config.yaml 与 edp-agent.yaml 配置冲突 | 同一字段在两处配置时的优先级规则 | 两份配置承载的字段完全不同，不存在冲突 |
| DeepAgent 不继承 BaseAgent（P11 穿刺阻塞） | DeepAgent 无法通过 OpenJiuwenAgentRuntimeHandler 返回 | 备选方案：等待 DeepAgent 修复，或用委托模式包装 |
| DeepAgent 中断机制不兼容话术模板（P6 穿刺阻塞） | AskUser 话术系统需彻底重设计 | 备选方案：在前端层做话术匹配，Rail 仅负责任务中断/恢复 |
| agent-runtime SPI 契约变动（P11 穿刺点） | EdpaRuntimeHandler 接口需频繁适配 | 锁定 agent-runtime 版本，EdpaRuntimeHandler 作为隔离层 |
| agent-runtime 与 EDPAgent Bean 冲突（P12 穿刺点） | 同进程内多个 Handler 注册冲突 | agent-runtime 支持 Handler 白名单配置 |
| OpenJiuwenAgentRuntimeHandler 与 DeepAgent 适配（P14 穿刺点） | createOpenJiuwenAgent 返回 DeepAgent 是否可被 Runner 执行 | P14 穿刺验证 |
| _StreamProcessor 17 种事件状态机迁移（P15 穿刺点） | OpenJiuwenStreamAdapter 能否完全覆盖 LiteTodo 事件、think_chunk 模式切换、ui_notices drain | P15 穿刺验证；备选：自定义 StreamAdapter |
| stream-debug-ui Query 请求接入（P16 穿刺点） | Planning Path 请求格式与 agent-runtime A2A 端点不兼容；SSE 响应格式与 stream-debug-ui 解析格式不兼容 | stream-debug-ui 新增 A2A JSON-RPC 预设 + server.js 代理层格式转换 |

---

## 十七、关键技术穿刺点

### 17.1 穿刺点总览

| 编号 | 穿刺点 | 风险 | 不确定性 | 优先级 | 穿刺进展 |
|------|--------|------|---------|--------|----------|
| **P1** | edp-config.yaml 解析 + 配置合并验证 | 中 | 中 | 🟡 高 | <span style="color:#b58900">部分完成（2026-06-19）：已验证 edp-config.yaml 基础解析，完整合并矩阵待补齐</span> |
| **P2** | DeepAgent 子类化验证（改为：DeepAgent API 验证） | 高 | 高 | 🔴 最高 | <span style="color:green">已完成（2026-06-19）：已验证 DeepAgent 创建、Rail 注册和业务 Rail 增强</span> |
| **P3** | LiteTodoWrite 动态 JSON Schema | 高 | 中 | 🔴 最高 | <span style="color:red">未完成（2026-06-19）：尚未验证动态 JSON Schema 生成与工具调用闭环</span> |
| **P4** | 话术模板 JSON 4 层容错解析 | 中 | 高 | 🟡 高 | <span style="color:red">未完成（2026-06-19）：尚未验证模板解析和多层 fallback 容错</span> |
| **P5** | 自定义 Rail 与 DeepAgent 26+ Rails 交互 | 高 | 中 | 🟡 高 | <span style="color:#b58900">部分完成（2026-06-21）：已验证 9 个 EDPAgent 自建 Rail 注册；已修复 persistent BaseAgent 下每请求重复安装 Rail 导致 callback 累积的问题；内置 Rail 交互顺序矩阵待补齐</span> |
| **P6** | AskUserTemplateRail 中断/恢复机制 | 高 | 中 | 🟡 高 | <span style="color:#b58900">部分完成（2026-06-21）：已参考 Python EDPA 改为模型自然生成 ask_user、Rail 在 beforeToolCall 拦截；已修复 OpenAI-compatible arguments 非 JSON 与重复 tool_calls 问题；resume 专项回归待补齐</span> |
| **P7** | Versatile 级联中断编排 | 低 | 低 | 🟢 低（复杂 A2A 级联暂缓） | <span style="color:#b58900">范围收敛（2026-06-20）：不做父子 A2A remote-agent 级联；call_versatile 采用 VersatileInterruptRail 直连已知 Versatile REST 服务</span> |
| **P8** | call_versatile 直连 Versatile 服务 | 高 | 中 | 🔴 最高 | <span style="color:green">已完成（2026-06-21）：CallVersatileTool 保持 thin tool；VersatileInterruptRail 读取 edp-agent.yaml versatile 配置，真实 POST localhost:8095；已修复浏览器/ReAct 链路 toolArgs 为空时未从 ToolCall.arguments JSON 取参的问题；P8 单测覆盖真实 ReAct 参数形态；stream-debug-ui 模拟“帮我推荐几款稳健型理财产品”已完成，toolResult.content 非空且为标准 JSON</span> |
| **P9** | MCP 沙箱执行（SysOperation 适配） | 中 | 中 | 🟢 中 | <span style="color:#b58900">部分完成（2026-06-19）：模型可触发 call_mcp 意图，真实 MCP 执行闭环待验证</span> |
| **P10** | 配置渐进全链路验证 | 低 | 中 | 🟢 中 | <span style="color:#b58900">部分完成（2026-06-19）：已打通基础全链路，渐进配置矩阵待补齐</span> |
| **P11** | OpenJiuwenAgentRuntimeHandler SPI 契约验证 | 中 | 中 | 🟡 高 | <span style="color:green">已完成（2026-06-19）：EdpaRuntimeHandler 已继承 SPI 并通过 A2A 运行验证</span> |
| **P12** | agent-runtime 与 EDPAgent Bean 冲突检测 | 中 | 中 | 🟡 高 | <span style="color:#b58900">部分完成（2026-06-19）：运行未发现冲突，专项 Bean 冲突测试待补齐</span> |
| **P13** | YAML + edp-config.yaml 双配置合并验证 | 中 | 中 | 🟡 高 | <span style="color:green">已完成基础验证（2026-06-19）：双配置加载与字段分工已验证，异常矩阵待补齐</span> |
| **P14** | OpenJiuwenAgentRuntimeHandler + DeepAgent 适配验证 | 高 | 高 | 🔴 最高 | <span style="color:green">已完成（2026-06-19）：通过 deepAgent.getAgent() 返回 BaseAgent，A2A 端到端已跑通</span> |
| **P15** | _StreamProcessor 17 种事件状态机迁移验证 | 高 | 高 | 🔴 最高 | <span style="color:#b58900">部分完成（2026-06-21）：已验证基础 A2A 状态流、summary/completed 和前端展示；todo_start/todo_end、interrupt_start/interrupt_end、tool_start/tool_end 完整边界、fixed_script/real_stream 切换和 ui_notices drain 待补齐</span> |
| **P16** | stream-debug-ui Planning Path Query 请求接入验证 | 中 | 中 | 🟡 高 | <span style="color:green">已完成主链路（2026-06-21）：A2A 预设、Header、SSE 转换和前端展示已打通；已通过模拟浏览器请求“帮我推荐几款稳健型理财产品”验证推荐产品端到端完成</span> |
| **P17** | EDPAgent 内置业务工具注册验证 | 高 | 中 | 🔴 最高 | <span style="color:green">已完成基础验证（2026-06-20）：已通过 DeepAgent.registerHarnessTool 注册 lite_todo_write、call_mcp、call_versatile、ask_user、cancel_task 5 个业务工具，并通过 P17 测试验证工具清单、LiteTodo 动态 step_id schema、能力列表可见和直接调用</span> |
| **P18** | Skill 目录加载与 Prompt 注入验证 | 高 | 中 | 🔴 最高 | <span style="color:green">机制完成，数据待校准（2026-06-21）：edp-agent.yaml skills.directories=./skills 按配置文件所在目录解析到 src/main/resources/skills；保留 EDP 手工 registerSkill，同时将解析后的目录传给 DeepAgentConfig.skillDirectories，确保 SkillUseRail 的 skill_tool/list_skill 可用；当前运行可见 product_recommend_skill、product_select_skill，原文档 4 个 Skill 验收口径待与资源目录校准</span> |

> **关键变化**：
> - P1 从"AgentRule.md 混合格式解析器"改为"edp-config.yaml 解析 + 配置合并验证"（标准 YAML 解析，复杂度降低）
> - P2 从"DeepAgent 子类化验证"改为"DeepAgent API 验证"（不再继承，验证 registerTool/registerRail API）
> - P7 优先级 🟢 低（复杂父子 A2A 级联暂缓），当前业务调用改为 P8 直连 Versatile REST 服务
> - P8 从 FixedScriptFeeder 帧推送调整为 call_versatile 直连 Versatile 服务穿刺：Tool 保持 thin，HTTP 调用、JSON 归一化和错误可见性由 VersatileInterruptRail 承担；2026-06-21 已补齐 ToolCall.arguments JSON 取参，P8 单测与 stream-debug-ui 端到端推荐链路均通过
> - 2026-06-21 新增阶段报告：`EDAgent穿刺问题与解决报告-20260621.md`，记录 P8/P16/P17/P18 组合主链路通过、AskUser/Rail/Skill 关键问题与后续遗留项
> - 新增 P13（双配置合并验证）和 P14（OpenJiuwenAgentRuntimeHandler + DeepAgent 适配验证）
> - 新增 P17（EDPAgent 内置业务工具注册验证）：验证 5 个业务工具通过 DeepAgent 实际 API `registerHarnessTool()` 注册并与 DeepAgent 内置工具共存
> - 新增 P18（Skill 目录加载与 Prompt 注入验证）：验证 `src/main/resources/skills` 下 SKILL.md 被 OpenJiuwen SkillUtil 加载并进入 Skill prompt

### 17.2 P2：DeepAgent API 验证（★ 最高优先级）

**穿刺目标**：验证 DeepAgent 的标准 API（`registerTool()` / `registerRail()` / `DeepAgentConfig.tools` / `DeepAgentConfig.rails`）能否承载 EDPAgent 的扩展注册需求。

**关键验证点**：

| 验证点 | 通过标准 |
|--------|---------|
| `AgentFactory.toDeepAgent(yamlPath)` 创建 DeepAgent | 不抛异常，返回可运行的 DeepAgent 实例 |
| `agent.registerTool(new CallVersatileTool())` | 工具出现在 Agent 能力列表中 |
| `agent.registerRail(new VersatileInterruptRail())` | Rail 的 beforeInvoke 被调用 |
| `DeepAgentConfig.tools` 配置注入 | YAML 中声明的工具对 LLM 可见 |
| `DeepAgentConfig.rails` 配置注入 | YAML 中声明的 Rail 拦截生效 |
| 自定义 Rail + 内置 26+ Rail 无冲突 | 同时注册不报错 |

### 17.3 P11：OpenJiuwenAgentRuntimeHandler SPI 契约验证

**穿刺目标**：验证 `EdpaRuntimeHandler extends OpenJiuwenAgentRuntimeHandler` 的完整链路。

**关键验证点**：

| 验证点 | 通过标准 |
|--------|---------|
| @Component 注册成功 | agent-runtime 启动后 EdpaRuntimeHandler Bean 可被发现 |
| createOpenJiuwenAgent(ctx) 返回 DeepAgent | DeepAgent 实例可被 Runner 执行 |
| 基类处理执行流程 | handleTask / resultAdapter / getAgentCard 由基类处理 |
| INTERRUPTED 信号传递 | VersatileInterruptRail 产出的 INTERRUPTED 被 A2aAgentExecutor 检测 |

### 17.4 P13：YAML + edp-config.yaml 双配置合并验证

**穿刺目标**：验证 edp-agent.yaml（标准配置）和 edp-config.yaml（专有配置）的合并逻辑。

**关键验证点**：

| 验证点 | 通过标准 |
|--------|---------|
| edp-agent.yaml 缺失 | 仅 edp-config.yaml 时，DeepAgent 使用默认配置 |
| edp-config.yaml 缺失 | 仅 edp-agent.yaml 时，EDPAgent 扩展不注册 |
| 两份配置同时存在 | 标准配置创建 DeepAgent，专有配置注册扩展 |
| 字段不冲突 | 两份配置承载的字段完全不同 |

### 17.5 P14：OpenJiuwenAgentRuntimeHandler + DeepAgent 适配验证（★ 最高优先级）

**穿刺目标**：验证 DeepAgent 能否通过 `OpenJiuwenAgentRuntimeHandler.createOpenJiuwenAgent()` 返回并被 Runner 执行。

**为什么需要穿刺**：当前 OpenJiuwen DeepAgent 不继承 `BaseAgent`（openjiuwen-adapter.md §8 限制说明），这意味着 `createOpenJiuwenAgent()` 返回 DeepAgent 实例时，Runner 可能无法执行。

**关键验证点**：

| 验证点 | 通过标准 |
|--------|---------|
| DeepAgent IS-A BaseAgent | DeepAgent 类继承或实现 BaseAgent 接口 |
| Runner.runAgentStreaming(agent) 可执行 | DeepAgent 实例可被 Runner 正常执行 |
| OpenJiuwenStreamAdapter 适配 | DeepAgent 的 OutputSchema 可被 OpenJiuwenStreamAdapter 映射 |

**备选方案**（如果 DeepAgent 不继承 BaseAgent）：
- 等待 OpenJiuwen 修复 DeepAgent（使其继承 BaseAgent）
- 用委托模式包装：`DeepAgentDelegate implements BaseAgent`，内部持有 DeepAgent 实例

### 17.6 P15：_StreamProcessor 17 种事件状态机迁移验证（★ 最高优先级）

**穿刺目标**：验证 Python 版 `agent.py` 内部的 `_StreamProcessor`（17 种事件的状态机转换）能否由 agent-runtime 的 `OpenJiuwenStreamAdapter` 完全覆盖。

**为什么需要穿刺**：`_StreamProcessor` 是 EDPAgent 最核心的流事件处理逻辑（~200 行），包含：
- LiteTodo 事件解析（todo_start / todo_update / todo_done）
- ui_notices drain（从 session.ui_notices 中 drain 指定 event 类型的提示话术）
- think_chunk 模式切换（fixed_script / real_stream）
- tool_start / tool_end 边界处理
- interrupt_start / interrupt_end 事件映射

**关键验证点**：

| 验证点 | 通过标准 |
|--------|---------|
| OpenJiuwenStreamAdapter 能处理 LiteTodo 事件 | todo_start / todo_update / todo_done 事件正确映射到 AgentExecutionResult |
| OpenJiuwenStreamAdapter 能处理 think_chunk 事件 | fixed_script 和 real_stream 模式正确切换 |
| OpenJiuwenStreamAdapter 能处理 interrupt 事件 | interrupt_start / interrupt_end 正确映射 |
| OpenJiuwenStreamAdapter 能处理 tool 边界事件 | tool_start / tool_end 正确映射 |
| _drain_ui_notices 逻辑可由 AskUserTemplateRail 覆盖 | 话术 drain 在 tool_end 边界后正确调用 |

**备选方案**（如果 OpenJiuwenStreamAdapter 不能完全覆盖）：
- 在 EdpaRuntimeHandler 中覆写 `resultAdapter()`，返回自定义 StreamAdapter
- 在 EdpaAgentEnhancer 中注册自定义 StreamProcessor Rail

### 17.7 P16：stream-debug-ui Planning Path Query 请求接入验证

**穿刺目标**：验证 EDPAgent（Java）能接受 stream-debug-ui 通过 A2A JSON-RPC 预设发送的 Query 请求，并返回符合 stream-debug-ui 解析要求的 SSE 流式响应。

**为什么需要穿刺**：stream-debug-ui 是 EDPAgent 的测试调试平台，穿刺验证时需要通过该平台发送 Query 请求到 agent-runtime 并观察 SSE 流式响应。存在两个格式不兼容问题：

1. **请求格式不兼容**：agent-runtime 仅暴露 `/a2a`（A2A JSON-RPC）端点，Planning Path 模式发送的是 Versatile REST 格式请求
2. **响应格式不兼容**：agent-runtime 返回 A2A JSON-RPC SSE 格式（`event:jsonrpc` + `data:{jsonrpc:"2.0",result:{statusUpdate:{...}}}`），而 stream-debug-ui 的 `parseSSEBlock()` 期望解析为 `{success, custom_rsp_data:{event, content}}` 格式的 JSON 对象

**请求路由方案**：在 stream-debug-ui 中新增 A2A JSON-RPC 请求预设选项，直接发送 A2A 格式请求到 agent-runtime 的 `/a2a` 端点，无需在 agent-runtime 中新增 REST 适配端点。

**响应格式适配方案**：在 stream-debug-ui 的 `server.js` 代理层增加 A2A SSE → stream-debug-ui SSE 格式转换逻辑，将 agent-runtime 返回的 `event:jsonrpc` + `data:{jsonrpc:"2.0",result:{...}}` 转换为 stream-debug-ui 期望的 `data:{success:true, custom_rsp_data:{event:"xxx", content:"xxx"}}` 格式。

**Planning Path 模式请求格式**（stream-debug-ui 当前默认格式）：

| 字段 | 值 | 说明 |
|------|-----|------|
| URL | `POST {baseUrl}/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}` | 默认 baseUrl=http://localhost:8190, project_id=test_project, agent_id=edp_agent |
| agent_id | `"main_planner"` | 固定值 |
| input.query | 用户输入文本 | 如 `"请推荐几款理财产品"` |
| conversation_id | 会话标识 | 如 `"tests-conv-0001"` |
| timeout | `"300"` | 超时秒数 |
| role_id | `"1"` | 角色标识 |
| role_name | `"mobile-bank"` | 角色名称 |
| stream | `true` | 流式响应标志 |
| custom_data.inputs.query | 用户输入文本（冗余传递） | 与 input.query 相同 |
| custom_data.long_term_memory.enable_retrieve | `true` | 长期记忆检索开关 |
| custom_data.long_term_memory.enable_extract | `true` | 长期记忆提取开关 |

**Planning Path 完整请求体示例**：

```json
{
  "agent_id": "main_planner",
  "input": { "query": "请推荐几款理财产品" },
  "conversation_id": "tests-conv-0001",
  "timeout": "300",
  "role_id": "1",
  "role_name": "mobile-bank",
  "stream": true,
  "custom_data": {
    "inputs": { "query": "请推荐几款理财产品" },
    "memory_inputs": {},
    "globals": {},
    "plugin_configs": [],
    "long_term_memory": {
      "enable_retrieve": true,
      "enable_extract": true
    }
  }
}
```

**A2A JSON-RPC 请求格式**（新增预设，穿刺验证实际使用格式）：

| 字段 | 值 | 说明 |
|------|-----|------|
| URL | `POST {baseUrl}/a2a` | 默认 baseUrl=http://localhost:8190 |
| Accept | `text/event-stream` | SSE 流式响应 |
| jsonrpc | `"2.0"` | JSON-RPC 版本 |
| method | `"SendStreamingMessage"` | 流式消息方法 |
| id | `"1"` | 请求标识 |
| params.message.role | `"ROLE_USER"` | 用户角色 |
| params.message.messageId | `"msg-001"` | 消息标识 |
| params.message.contextId | `"session-123"` | 会话标识（对应 conversation_id） |
| params.message.parts[0].text | 用户输入文本 | 如 `"请推荐几款理财产品"` |
| params.message.metadata | `{ userId, agentId, sessionId }` | 元数据（对应 role_id/role_name/custom_data） |

**A2A JSON-RPC 完整请求体示例**：

```json
{
  "jsonrpc": "2.0",
  "method": "SendStreamingMessage",
  "id": "1",
  "params": {
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-001",
      "contextId": "tests-conv-0001",
      "metadata": {
        "userId": "1",
        "agentId": "edp_agent",
        "sessionId": "tests-conv-0001"
      },
      "parts": [{ "text": "请推荐几款理财产品" }]
    }
  }
}
```

**Planning Path → A2A 字段映射**：

| Planning Path 字段 | A2A 字段 | 映射规则 |
|---------------------|----------|----------|
| `input.query` | `params.message.parts[0].text` | 直接映射 |
| `conversation_id` | `params.message.contextId` | 直接映射 |
| `role_id` | `params.message.metadata.userId` | 映射到 metadata |
| `role_name` | `params.message.metadata.roleName` | 映射到 metadata（新增） |
| `agent_id` | `params.message.metadata.agentId` | 映射到 metadata |
| `custom_data.long_term_memory` | `params.message.metadata.long_term_memory` | 映射到 metadata（由 MemoryRail 读取） |
| `stream: true` | `Accept: text/event-stream` | HTTP Header 映射 |
| `timeout` | 无直接映射 | 由 agent-runtime 配置管理 |

**SSE 流式响应格式**（agent-runtime 返回，A2A JSON-RPC 格式）：

```
event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{"state":"TASK_STATE_SUBMITTED"}}}

event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{"state":"TASK_STATE_WORKING"}}}

event:jsonrpc
data:{"jsonrpc":"2.0","id":1,"result":{"statusUpdate":{"state":"TASK_STATE_COMPLETED",
  "message":{"role":"ROLE_AGENT","parts":[{"text":"你好！"}]}}}}
```

**SSE 响应格式转换**（server.js 代理层：A2A JSON-RPC → stream-debug-ui 格式）：

stream-debug-ui 的 `parseSSEBlock()` 期望每个 SSE 块解析为 JSON 对象，包含 `success` 和 `custom_rsp_data` 字段。agent-runtime 返回的 A2A JSON-RPC SSE 格式需要转换为 stream-debug-ui 可解析的格式。

| A2A JSON-RPC SSE 事件 | stream-debug-ui SSE 格式 | 转换规则 |
|------------------------|--------------------------|----------|
| `TASK_STATE_SUBMITTED` | `{success:true, custom_rsp_data:{event:"conversation_start", content:""}}` | 会话开始 |
| `TASK_STATE_WORKING` + tool_start | `{success:true, custom_rsp_data:{event:"tool_start", content:"工具名"}}` | 工具调用开始 |
| `TASK_STATE_WORKING` + tool_end | `{success:true, custom_rsp_data:{event:"tool_end", content:"工具结果"}}` | 工具调用结束 |
| `TASK_STATE_WORKING` + interrupt | `{success:true, custom_rsp_data:{event:"interrupt_start", content:"中断提示"}}` | 中断确认 |
| `TASK_STATE_WORKING` + interrupt_end | `{success:true, custom_rsp_data:{event:"interrupt_end", content:"恢复内容"}}` | 中断恢复 |
| `TASK_STATE_WORKING` + todo | `{success:true, custom_rsp_data:{event:"todo_start/todo_end", content:"任务描述"}}` | LiteTodo 事件 |
| `TASK_STATE_WORKING` + summary | `{success:true, custom_rsp_data:{event:"summary", content:"回答文本"}}` | 最终回答流 |
| `TASK_STATE_COMPLETED` | `{success:true, custom_rsp_data:{event:"conversation_end", content:""}}` | 会话结束 |
| `TASK_STATE_FAILED` | `{success:false, custom_rsp_data:{event:"error", content:"错误信息"}}` | 执行失败 |

**关键验证点**：

| 验证点 | 通过标准 |
|--------|---------|
| stream-debug-ui 新增 A2A 预设选项 | UI 中可选择 "A2A JSON-RPC (/a2a)" 请求预设 |
| A2A 请求正确路由到 EdpaRuntimeHandler | agent-runtime 根据 agentId 路由到 edp_agent Handler |
| Query 文本正确传递到 DeepAgent | `params.message.parts[0].text` → DeepAgent 输入 |
| conversation_id 正确传递 | `params.message.contextId` → session 上下文 |
| SSE 流式响应正确返回 | stream-debug-ui 接收到 TASK_STATE_SUBMITTED → WORKING → COMPLETED 事件流 |
| SSE 响应格式转换正确 | server.js 代理层将 A2A JSON-RPC SSE 转换为 stream-debug-ui 可解析的 `{success, custom_rsp_data}` 格式 |
| metadata 正确传递到 Rail | role_id / role_name / long_term_memory 通过 metadata 传递到 MemoryRail |
| 穿刺验证可观察 | stream-debug-ui 事件面板正确显示 conversation_start / tool_start / tool_end / interrupt_start / summary 等事件 |

**stream-debug-ui 修改清单**（新增 A2A 预设 + 响应格式转换）：

| 修改点 | 文件 | 说明 |
|--------|------|------|
| 新增 A2A 预设选项 | `public/index.html` requestPreset select | 新增 `<option value="a2a_jsonrpc">A2A JSON-RPC (/a2a)</option>` |
| 新增 A2A URL 构建逻辑 | `server.js` buildBackendUrl() | `preset === "a2a_jsonrpc"` → `${cleanBase}/a2a` |
| 新增 A2A 请求体构建逻辑 | `server.js` buildRequestBody() | `preset === "a2a_jsonrpc"` → 构建 JSON-RPC 格式请求体 |
| 新增 A2A SSE 响应格式转换 | `server.js` `/api/stream` 代理逻辑 | `preset === "a2a_jsonrpc"` 时，逐块解析 A2A JSON-RPC SSE，转换为 `{success, custom_rsp_data}` 格式后转发到前端 |

**备选方案**（如果 stream-debug-ui 不便于修改）：
- 在 agent-runtime 中新增 REST 适配端点，接受 Planning Path 格式请求，内部转换为 A2A JSON-RPC，并直接返回 stream-debug-ui 格式的 SSE 响应
- 使用 VersatileAgentRuntimeHandler 代理模式，将 Planning Path 请求转发到 Versatile 远端

### 17.8 P8：call_versatile 直连 Versatile 服务验证

**穿刺目标**：验证 `call_versatile` 不再走复杂父子 A2A / remote-agent 级联，而是由 `VersatileInterruptRail` 直接调用已知 Versatile REST 服务，并把返回值归一化为可被模型继续消费的标准 JSON。

**实现边界**：

| 组件 | 职责 |
|---|---|
| `CallVersatileTool` | thin tool，只声明工具名、schema 和 `delegate_intent` 返回；不发 HTTP、不做业务执行 |
| `VersatileInterruptRail` | 拦截 `call_versatile`，读取 `edp-agent.yaml.versatile`，执行 HTTP POST，解析 JSON/SSE，设置 `_skip_tool=true` 和 `toolResult`；取参需兼容 `inputs.getToolArgs()` 与 OpenAI-compatible `ToolCall.arguments` JSON 字符串 |
| `edp-agent.yaml.versatile` | 配置 URL、超时、URL 变量、Query Params、Headers |
| `P8CallVersatileDirectSpikeTest` | 验证 schema、thin tool、配置解析、Rail 拦截、真实 8095 调用、错误可见性，以及 `toolArgs` 为空但 `ToolCall.arguments` 有 JSON 参数的真实 ReAct 场景 |

**关键验收标准**：

| 验证点 | 通过标准 |
|---|---|
| Tool 保持 thin | 直接调用 Tool 返回 `{tool:"call_versatile", status:"delegate_intent", input:{...}}` |
| 最小 Skill 调用参数 | `query_description="推荐理财产品"`、`query_intent="理财推荐"` 即可触发 |
| ReAct 参数形态 | 当 `toolArgs` 为空时，Rail 能从 `ToolCall.arguments` JSON 字符串解析出 `query_description` / `query_intent` |
| Rail 拦截 | `ctx.extra._skip_tool == true`，原 Tool 执行被跳过 |
| 真实服务调用 | `localhost:8095` 返回 HTTP 2xx |
| 返回内容标准化 | `toolResult.source == "versatile"`、`toolResult.status == "completed"` |
| JSON 验收 | `toolResult.content` 非空，且 `ObjectMapper.readTree(content)` 成功，解析结果是 Object 或 Array |
| 可观测性 | 测试日志打印 `toolResult`、`request body`、`response body` 和 `toolResult.content` |

**2026-06-21 端到端穿刺结论**：stream-debug-ui 模拟浏览器输入 `帮我推荐几款稳健型理财产品` 已通过。实际链路为 `skill_tool(product_recommend_skill)` → `call_versatile(query_description="推荐理财产品", query_intent="理财推荐")` → `mock_workflow_server_v6.py` 命中 `mock_wealth_recommend_stream` → 返回理财产品 JSON → A2A `TASK_STATE_COMPLETED`。本次定位并修复的根因是浏览器/ReAct 链路中参数在 `ToolCall.arguments` JSON 字符串内，Rail 原先只读取 `toolArgs` 导致 `query` 为空。

### 17.9 P18：Skill 目录加载与 Prompt 注入验证

**穿刺目标**：验证当前 Skill 根目录 `agent-store/edp-agent-trae/spike/src/main/resources/skills` 能从 `edp-agent.yaml` 中被正确解析，同时进入 EDP 手工注册 SkillUtil 与 OpenJiuwen `SkillUseRail` 使用的 SkillManager，使 `skill_tool` / `list_skill` 和 Skill prompt 保持一致。

**关键验收标准**：

| 验证点 | 通过标准 |
|---|---|
| 配置解析 | `EdpAgentConfig` 能解析 `skills.directories=["./skills"]`、`skills.mode=all` |
| 目录解析 | `./skills` 按 `edp-agent.yaml` 所在目录解析到 `src/main/resources/skills` |
| EDP 手工 Skill 注册 | `deepAgent.getAgent().registerSkill(resolvedSkillRoot)` 保留，`getSkillUtil().hasSkill()==true` |
| SkillUseRail 注册 | `DeepAgentConfig.skillDirectories` 使用按 `edp-agent.yaml` 所在目录解析后的绝对路径，`skill_tool(product_recommend_skill)` 可用 |
| Skill 数量 | 当前运行可见 `product_recommend_skill`、`product_select_skill`；原 4 个 Skill 验收口径需与资源目录校准 |
| Skill 名称 | 至少包含 `product_recommend_skill`、`product_select_skill`；如资源目录恢复 4 个 Skill，再补齐 `fund_planning_skill`、`interact_finance_rec_skill` 验证 |
| Prompt 注入 | `getSkillPrompt()` 包含 `product_recommend_skill` 和 `Skill directory file path` |
| 启动日志 | 能看到 Skill 根目录解析、EDP 手工注册完成，以及 `skill_tool` / `list_skill` 可读取同一 Skill 集 |

**穿刺测试**：`P18SkillLoadingSpikeTest` 覆盖配置解析、运行时注册和 Skill prompt 注入三层验证；如测试仍断言 4 个 Skill，需要按当前资源目录或恢复资源后同步调整。

---

## 十八、EDPAgent 对 agent-runtime SPI 契约依赖分析

### 18.1 SPI 契约依赖全景图

```
┌──────────────────────────────────────────────────────────────────┐
│                    agent-runtime (Spring Boot)                    │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  OpenJiuwenAgentRuntimeHandler SPI 接口                     │  │
│  │  ├── createOpenJiuwenAgent(ctx) → BaseAgent 实例            │  │
│  │  ├── openJiuwenRails(ctx) → List<AgentRail>                │  │
│  │  ├── resultAdapter() → OpenJiuwenStreamAdapter（基类提供）   │  │
│  │  └── getAgentCard() → AgentCard（基类从 agentId 自动生成）   │  │
│  └────────────────────────────┬───────────────────────────────┘  │
│                               │                                    │
│  ┌────────────────────────────┼───────────────────────────────┐  │
│  │  EdpaRuntimeHandler (@Component Bean)                       │  │
│  │  │                                                          │  │
│  │  │  DeepAgent 实例（AgentFactory.toDeepAgent 标准创建）      │  │
│  │  │  ├── EdpaAgentEnhancer 注册的扩展                        │  │
│  │  │  ├── VersatileInterruptRail → INTERRUPTED 信号           │  │
│  │  │  └── OutputSchema → OpenJiuwenStreamAdapter 映射         │  │
│  │  └──────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                               │                                    │
│  ┌────────────────────────────┼───────────────────────────────┐  │
│  │  agent-runtime 内部组件（EDPAgent 不直接依赖）               │  │
│  │  ├── A2aJsonRpcController (HTTP 入口)                       │  │
│  │  ├── A2aAgentExecutor (编排器 + INTERRUPTED 检测)           │  │
│  │  ├── OpenJiuwenStreamAdapter (事件映射)                     │  │
│  │  ├── VersatileAgentRuntimeHandler (Versatile 适配)          │  │
│  │  └── TaskStore (Task 持久化)                                │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### 18.2 SPI 契约依赖详细分析

#### 18.2.1 OpenJiuwenAgentRuntimeHandler SPI 接口契约

| 契约点 | 方法 | 输入 | 输出 | EDPAgent 实现 | 依赖类型 |
|--------|------|------|------|--------------|---------|
| Agent 创建 | `createOpenJiuwenAgent(ctx)` | `AgentExecutionContext` | `BaseAgent` | 返回 DeepAgent 实例 | **SPI 契约** |
| Rail 注入 | `openJiuwenRails(ctx)` | `AgentExecutionContext` | `List<AgentRail>` | 返回 EDPAgent 专有 Rail 列表 | **SPI 契约** |
| 流映射 | `resultAdapter()` | 无 | `OpenJiuwenStreamAdapter` | 基类提供 | **SPI 契约**（基类处理） |
| AgentCard | `getAgentCard()` | 无 | `AgentCard` | 基类从 agentId 自动生成 | **SPI 契约**（基类处理） |
| 初始化 | `@PostConstruct` | 无 | 无 | AgentFactory + EdpaAgentEnhancer | **Bean 生命周期** |

#### 18.2.2 配置属性依赖

| 配置属性 | 来源 | 作用 | 依赖类型 |
|---------|------|------|---------|
| `edpa.agent.yaml-path` | application.yml | edp-agent.yaml 文件路径 | **配置属性** |
| `edpa.agent.config-path` | application.yml | edp-config.yaml 文件路径 | **配置属性** |
| `edpa.agent.scripts-config-path` | application.yml | ScriptsConfig.md 文件路径 | **配置属性** |
| `agent-runtime.handler.whitelist` | application.yml | Handler 白名单 | **可选配置** |
| `spring.data.redis.*` | application.yml | Redis 连接配置 | **配置属性** |

#### 18.2.3 可选依赖

| 依赖 | 来源 | 作用 | 依赖类型 |
|------|------|------|---------|
| `VersatileAgentRuntimeHandler` | agent-runtime 内置 | Versatile 适配 | **可选** |
| `InMemoryTaskStore / RedisTaskStore` | agent-runtime 内置 | Task 持久化 | **可选** |
| `OpenJiuwenStreamAdapter` | agent-runtime 内置 | 流事件映射 | **可选** |

### 18.3 信号交互依赖

| 信号 | 产出方 | 消费方 | 交互方式 |
|------|--------|--------|---------|
| **INTERRUPTED** | VersatileInterruptRail（EDPAgent） | A2aAgentExecutor（agent-runtime） | AgentExecutionResult.interrupted() |
| **OutputSchema 流** | DeepAgent.stream()（EDPAgent） | OpenJiuwenStreamAdapter（agent-runtime） | Flux<AgentExecutionResult> 返回值 |

> **关键结论**：EDPAgent 对 agent-runtime 的依赖是**纯接口层依赖**。EdpaRuntimeHandler 继承 `OpenJiuwenAgentRuntimeHandler`，基类处理执行流程、消息转换、流映射、AgentCard 生成。EDPAgent 仅需实现 `createOpenJiuwenAgent()` 和 `openJiuwenRails()` 两个方法。

---

## 十九、修改记录

### 19.1 v2.0.1（2026-06-21）—— 穿刺问题与解决进展同步

| 序号 | 更新项 | 说明 |
|------|--------|------|
| 1 | **新增阶段报告引用** | 新增 `EDAgent穿刺问题与解决报告-20260621.md`，单独记录本轮穿刺问题、定位、修复和后续遗留项 |
| 2 | **更新 P8 直连 Versatile 状态** | 补充浏览器/ReAct 链路下 `toolArgs` 为空、参数位于 `ToolCall.arguments` JSON 字符串的根因和修复要求；P8 单测需覆盖真实 ReAct 参数形态 |
| 3 | **确认推荐产品端到端主链路通过** | stream-debug-ui 模拟输入 `帮我推荐几款稳健型理财产品`，链路为 `skill_tool(product_recommend_skill)` → `call_versatile` → `mock_wealth_recommend_stream` → `TASK_STATE_COMPLETED` |
| 4 | **更新 P5 Rail 交互状态** | 记录 persistent BaseAgent 下每请求重复安装 EDPAgent Rails 导致 callback 累积的问题已修复；`openJiuwenRails(context)` 返回空列表，业务 Rails 只在初始化阶段安装 |
| 5 | **更新 P6 AskUser 状态** | 记录已参考 Python EDPA 改为模型自然生成 `ask_user`、Rail 在 `beforeToolCall` 拦截；已修复重复 `assistant.tool_calls` 与 OpenAI-compatible arguments 非 JSON 问题；resume 专项回归仍待补齐 |
| 6 | **更新 P18 Skill 加载状态** | 保留 EDP 手工 `registerSkill`，同时将解析后的 Skill 目录传给 `DeepAgentConfig.skillDirectories`，确保 `skill_tool` / `list_skill` 与 Prompt Skill 注册使用同一目录；当前运行可见 2 个 Skill，原 4 个 Skill 口径待校准 |
| 7 | **更新 P15 流事件状态** | 明确基础 A2A 状态流和前端展示已通，但 17 种事件完整状态机仍待补齐 |

### 19.2 v2.0（2026-06-20）—— 简化重构方案

基于"挂载式开发 = OpenJiuwen式开发"的核心洞察，对 v1.1 方案进行全面简化。

| 序号 | 简化项 | 说明 |
|------|--------|------|
| 1 | **消除 DeepAgentPlus 中间类** | 直接使用 DeepAgent，扩展通过 EdpaAgentEnhancer 注册 |
| 2 | **消除 AgentRule.md 专有格式** | 改为 edp-agent.yaml（标准 YAML）+ edp-config.yaml（标准 YAML） |
| 3 | **消除 DeepAgentPlusConfig** | 直接用 DeepAgentConfig + EdpConfig |
| 4 | **消除 AgentRuleParser / AgentRuleSchema** | YAML 解析由 agent-sdk AgentFactory 完成 |
| 5 | **消除 PromptBuilder** | systemPrompt 直接在 edp-agent.yaml 中表达 |
| 6 | **消除 StreamEventAdapter** | 由 agent-runtime OpenJiuwenStreamAdapter 覆盖 |
| 7 | **EdpaRuntimeHandler 改为 extends OpenJiuwenAgentRuntimeHandler** | 基类处理执行流程、消息转换、流映射 |
| 8 | **统一开发方式** | 从"装配式/挂载式"改为"OpenJiuwen式开发" |
| 9 | **代码量缩减 41%** | 从 ~7,780 行缩减到 ~4,600 行（新增 4 个 EDPAgent 自建 Rail + channel/ToolDataChannel） |
| 10 | **新增 P13/P14 穿刺点** | 双配置合并验证 + OpenJiuwenAgentRuntimeHandler 适配验证 |
| 11 | **新增 P15/P16 穿刺点** | _StreamProcessor 事件状态机迁移验证 + MemoryRail 存储初始化验证 |
| 12 | **修正 Rail 分类** | IterationLimitRail / LogRail / MemoryRail 从"DeepAgent 内置"修正为"EDPAgent 自建" |
| 13 | **新增 LiteTodoRail** | 遗漏的 LiteTodoRail（priority=35）补充到 Rail 注册顺序和功能清单 |
| 14 | **新增 ToolDataChannel** | channel/ToolDataChannel 运行时数据通道（MCP 数据 → Versatile 委托注入），不计入工具 |
| 15 | **修正 adapter.py 消除原因** | 从"OpenJiuwenStreamAdapter 覆盖"修正为"ToolDataChannel 覆盖（Versatile 委托请求注入）" |
| 16 | **修正 ScriptsConfig.md 结构** | 从分组结构修正为扁平 scripts 字典 |
| 17 | **修正 step_id 类型** | 从字符串修正为整数（与 Python 版 AgentRule.md 一致） |
| 18 | **补充 prompt.py 内容迁移** | MCP 先行架构 + 工具使用规则 + 执行规则强调 |
| 19 | **补充 think_chunk 详细配置** | chars_per_frame / query_patterns / execution_scripts / resume_scripts |
| 20 | **补充环境变量映射** | 50+ 环境变量映射到 edp-agent.yaml + edp-config.yaml + application.yml |
| 21 | **补充 LLM Sampling 覆盖** | temperature=0.1 / top_p=0.95 / max_retries=0（修复 reasoning 模型空响应） |
| 22 | **补充核心引擎校验项** | _StreamProcessor / _drain_ui_notices / SysOperationCard / DialogueCompressor / LLM Sampling |
| 23 | **修正 think_chunk 参数值** | chars_per_frame=4 / tokens_between_frames=2 / min_interval_ms=50（对齐 ScriptsConfig.md 实际值） |
| 24 | **补充 summary 字段** | edp-config.yaml 增加 summary（format / max_length / required_fields） |
| 25 | **补充 limits.tasks 缺失项** | 增加 ask_user: 100 / execute_cmd: 100（对齐 AgentRule.md） |
| 26 | **补充 Memory LLM 独立配置** | Memory LLM 可独立配置（7 个环境变量），未配置时降级到主 Agent LLM |
| 27 | **补充 Custom Headers / decrypt** | API Key 支持 decrypt_config_value 解密；Custom Headers 通过环境变量构建 |
| 28 | **补充 prompt.system 状态更新流程** | 增加 MANDATORY 状态更新规则（call_mcp/call_versatile 成功后必须 lite_todo_write） |
| 29 | **补充 AgentRule.md scripts 过渡说明** | Python 版 AgentRuleConfig.scripts 和 ScriptsConfigData.scripts 合并使用；Java 版彻底分离 |
| 30 | **补充 planning_steps 字段** | edp-config.yaml 增加 planning_steps（规划步骤模板，注入到系统提示词） |
| 31 | **补充 task_dependencies 说明** | v2.0 使用 todolist_steps.depends_on（增强设计），Python 版使用 task_dependencies: {}（全局声明） |
| 32 | **新增 P17 穿刺点** | stream-debug-ui Planning Path Query 请求接入验证；新增 A2A JSON-RPC 预设方案 + Planning Path → A2A 字段映射 + stream-debug-ui 修改清单 |
| 33 | **移除 P16 穿刺点** | MemoryRail 存储初始化验证不需要穿刺，由 Spring Boot @Configuration 管理，移除 P16 总览行 + 详细描述章节 + 风险行 |
| 34 | **P17→P16 重编号** | 原 P17（stream-debug-ui Query 请求接入验证）重编号为 P16，穿刺点编号连续 |
| 35 | **补充 P16 响应格式兼容性** | 明确 SSE 响应格式也要符合 stream-debug-ui 解析要求；新增 A2A SSE → stream-debug-ui SSE 格式转换映射表（9种事件）；server.js 代理层格式转换方案；验证点新增"SSE 响应格式转换正确" |
| 36 | **补充 P8 call_versatile 直连 Versatile 穿刺点** | 明确不做复杂父子 A2A / remote-agent 级联；CallVersatileTool 保持 thin tool；VersatileInterruptRail 直接调用 localhost:8095，`toolResult.content` 必须非空且为标准 JSON；测试打印返回值 |
| 37 | **补充 P18 Skill 加载穿刺点** | 明确当前 Skill 根目录为 `spike/src/main/resources/skills`；按 `edp-agent.yaml` 所在目录解析 `./skills`；当时按 4 个 Skill 目标补充启动日志和 P18 验收标准（2026-06-21 已记录当前运行可见 2 个 Skill，验收口径待校准） |

### 19.3 v1.1（2026-06-19）—— 接入 agent-runtime 全面优化

基于接入 agent-runtime 的方案，对文档进行14项优化修改。5模块独立部署架构改为单模块 SPI 契约接入架构。

### 19.4 v1.0（2026-06-12）—— 初版

初版文档，基于 Python 版 EDPAgent 源码分析，规划 Java 重构方案。5模块架构，独立部署模式。