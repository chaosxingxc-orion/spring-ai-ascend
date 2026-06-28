---
level: L1-HLD
TAG:
  - development-view
  - code-organization
  - dependency-boundary
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - logical.md
  - process.md
  - physical.md
  - api-appendix.md
  - spi-appendix.md
---

# agent-core L1 架构开发视图

## 1. 开发视图定位

本文档描述 `agent-core` 在代码、构建、依赖、资源、文档和测试成熟度上的 active 架构事实，并按 L0 边界映射 `openJiuwen/agent-core-java@v0.1.12-jdk21` 仓内包域。

## 2. Maven 模块形态

`agent-core-java` 是单 Maven 项目，产物为普通 jar：

```xml
<groupId>com.openjiuwen</groupId>
<artifactId>agent-core-java</artifactId>
<version>0.1.12</version>
<packaging>jar</packaging>
```

编译目标为 Java 21，构建工具要求 Maven 3.9+。该模块不是 Spring Boot fat jar，也不以服务进程为主产物。

## 3. 依赖管理

主要生产依赖包括：

| 依赖类别 | 代表依赖 | 用途 |
|---|---|---|
| JSON/YAML | Jackson、SnakeYAML | 配置、schema、消息和文档解析。 |
| 日志 | SLF4J、Logback | 框架日志和事件日志。 |
| 代码生成 | Lombok | Builder、getter 等样板代码。 |
| 安全 | Bouncy Castle | 加密与安全辅助。 |
| 通信 | JeroMQ | Agent Teams / messager 的 ZeroMQ 传输。 |
| 对象存储 | AWS S3 SDK | 对象存储客户端。 |
| 向量/数据库 | Milvus SDK、pgvector、PostgreSQL | 向量存储与数据库后端。 |
| 文档解析 | PDFBox、Apache POI、Jsoup | PDF、Word、HTML 等知识导入。 |
| 响应式 | Reactor Core | 流式处理和异步基础。 |
| MCP | mcp-core、mcp-json-jackson2 | MCP 工具/客户端集成。 |

测试依赖包括 JUnit 5、Mockito、AssertJ、H2、Testcontainers、mcp-test 和 JaCoCo。

## 4. 命名空间与代码组织

主代码命名空间以 `com.openjiuwen` 为根。按 Java 文件数量看，主要包域包括：

| 包域 | 仓内事实 | L0 归属与采用策略 |
|---|---|
| `com.openjiuwen.core` | Agent、Workflow、Foundation、Graph、Memory、Retrieval、Runner、Session 等。 | `workflow`、`graph`、`singleagent`、planner/node/hook/tool intent 是 `agent-core` 主体；`foundation`、`memory`、`retrieval`、`sysop`、`security` 中 provider 执行逻辑映射到 `agent-middleware`。 |
| `com.openjiuwen.harness` | DeepAgent harness、workspace、rails、tools、配置和提示词。 | 候选复用资产；按具体能力映射到 `agent-core` 执行组件、`agent-runtime` 编排、`agent-middleware` 工具/上下文或工具链。 |
| `com.openjiuwen.agent_teams` | 团队协作、成员、任务、消息、worktree、workspace 和团队工具。 | 不作为 `agent-core` 多智能体协同 owner；可评估复用同边界算法、context split 或工具类。智能体实体协同归 `agent-runtime`，跨边界治理归 `agent-bus`。 |
| `com.openjiuwen.agent_evolving` | Agent 自优化、评测、轨迹、训练、online/offline RL。 | 归属 `agent-evolve` 候选资产；目标形态是数据采集/存储、数据处理流水线、采样运行时、业务仿真、在线/离线优化微服务或作业。 |
| `com.openjiuwen.extensions` | 上下文演化等扩展能力。 | 按能力映射到 `agent-middleware` 或 `agent-evolve`，不默认进入 core 主体。 |
| `com.openjiuwen.dev_tools` | 开发工具、skill creator、agent builder 等。 | 工具链候选资产，不作为 `agent-core` 运行时职责。 |
| `com.openjiuwen.auto_harness` | 自动化 harness 资源和 skills。 | 候选复用资产；默认不启用为 core 主路径。 |
| `com.openjiuwen.spi` | store/query/vector/object 等较窄 SPI。 | 多数映射到 `agent-middleware` provider SPI；core 仅消费调用契约。 |
| `com.openjiuwen.deepagents` | DeepAgent 门面和 middleware。 | 候选复用资产；需要按执行组件、上下文、工具和 runtime 编排拆分归属。 |

`com.openjiuwen.core` 的一级子包包括 `foundation`、`workflow`、`common`、`retrieval`、`memory`、`sysop`、`runner`、`session`、`singleagent`、`graph`、`multiagent`、`controller`、`context`、`security`、`application` 和 `operator`。迁入或复用时不以一级包名直接决定模块归属，应以 L0 owner 和调用方向决定是否纳入 `agent-core` 主体、转为 `agent-middleware` 服务实现、进入 `agent-evolve` 或仅作为参考资产。

## 5. 资源与文档

主资源包括：

- `src/main/resources/common/appconfig.json`
- `src/main/resources/APIKEY/apiconfig.json`
- `src/main/resources/logback.xml`
- `src/main/resources/META-INF/services/com.openjiuwen.core.foundation.llm.Model$ModelClientFactory`
- `src/main/resources/memory/prompt/*`
- `src/main/resources/auto_harness/*`
- `src/main/resources/com/openjiuwen/...` 检索提示词与 guardrail 规则

文档组织包括：

- `README.zh.md` / `README.md`：项目入口。
- `documents/zh/SUMMARY.md`：开发指南和 API 文档总导航。
- `documents/zh/2.开发指南/`：基础功能、多智能体、工作流、高阶用法。
- `documents/zh/2.开发指南/API文档/`：按 `com.openjiuwen.core` 包结构生成的 API 页面。
- `examples/`：workflow、ReAct、groups、retrieval、store、skill、context evolver 等示例。

## 6. 测试成熟度

远端 `TEST_COMPILE_STATUS.md` 记录：

- 主代码编译成功。
- 测试编译仍有 32 个测试文件错误。

因此迁入本工程时，L1 只把主代码结构和公开能力作为当前实现事实；测试修复、依赖裁剪、包名适配和 CI 收敛应作为独立迁移任务或 L2 详细设计处理。

## 7. 开发边界约束

- 核心 SDK 不应依赖本工程旧 `agent-core` 模块类型。
- `agent-runtime` 可以适配 agent-core，但 agent-core 不应反向依赖 agent-runtime 的 A2A 或 TaskStore 语义。
- 模型服务、记忆服务、知识检索服务、工具代理服务、沙箱服务和安全护栏服务的具体执行逻辑应归属 `agent-middleware`；`agent-core` 保留驱动调用这些服务的组件逻辑。
- `agent_teams` 不作为 `agent-core` 多智能体协同 owner；天然存在的智能体实体协同由 `agent-runtime` 居中，跨边界进入 `agent-bus`，无状态子执行按 Task tree / 子 Task 建模。
- `agent_evolving` 不作为 `agent-core` 逻辑归属；复用决策应围绕 `agent-evolve` 的数据采集与存储、数据处理流水线、采样运行时、业务仿真、在线优化和离线优化能力展开。
- `harness`、`deepagents`、`auto_harness` 等高阶能力可以作为候选资产评估，但不能把训练、团队、工作区或工具治理策略下沉为核心 Agent 必选依赖。
