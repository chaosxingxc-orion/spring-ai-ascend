# Agent Service Engine — 对齐 agentscope-runtime 的分层与 Agent 应用形态 标准指导

> 版本：v1.0
> 日期：2026-05-31
> 关联主设计：`2026-05-30-l1--agent-service-engine-model-design.md`（"主设计"）
> 关联补丁：`2026-05-31-l1--agent-service-engine-openjiuwen-adapter-verification-design.md`
> 对标参考：`agentscope-ai/agentscope-runtime-java`（engine 对标）、`agentscope-ai/agentscope-java`（框架对标）
> 来源：基于 clone 到 `third_party/agentscope/` 的真实源码逐文件核对，非记忆推断

## 1. 定位校正（本文档存在的理由）

engine 模块对标的是 **agentscope-runtime-java（runtime 层）**，不是框架层。
对应关系：

| 角色 | agentscope 阵营 | 本项目阵营 |
|---|---|---|
| Agent 开发框架 | `agentscope-java`（`agentscope-core`） | openjiuwen `agent-core-java:0.1.12` |
| Agent 运行时 | `agentscope-runtime-java`（`engine-core`） | `agent-service` 的 `engine` 模块 |

agent-service 是对外起接口服务的库，整体与 agentscope-runtime-java 对齐；
engine 只是其中一个模块，承担 **agent 执行器** 角色。

> 一处必须记录的认知校正：曾误判为"agent 只是 engine 进程内 new 出来的对象、
> 不存在独立 agent 应用"。核对 agentscope 真实代码后确认此判断错误——runtime 模型中
> **存在开发者独立编写、可独立部署的 agent 应用**。本文档以真实代码为准。

## 2. 三层所有权模型

agentscope-runtime 真实代码呈现三条清晰的所有权边界。本项目借鉴这一**切分**，
但 engine 保留主设计既有的"命令队列 + 事件回写"模型，**不采用** agentscope 的
`AgentApp.run(port)` HTTP 部署形态（见 §4 的取舍说明）。

```
┌─ ① 框架层（第三方，engine 只消费）──────────────────────┐
│  com.openjiuwen:agent-core-java:0.1.12  （pom 依赖）      │
│  提供 ReActAgent / Runner / Toolkit                       │
└────────────────────────────────────────────────────────────┘
            ↑ 被 ② 调用
┌─ ② engine 层（本项目核心，通用、不含任何具体 agent）────┐
│  agent-service/src/main/java/com/huawei/ascend/service/engine/ │
│   spi/      AgentHandler 契约（主设计 §9.1）             │
│   adapter/openjiuwen/  OpenJiuwen 适配基类（连框架，     │
│             但不定义"是哪个 agent"）                     │
│   + 队列 / dispatch / 事件回写（主设计既有，保留不动）   │
└────────────────────────────────────────────────────────────┘
            ↑ 被 ③ 继承 / 注册
┌─ ③ agent 应用层（开发者编写的"具体 agent"，独立模块）──┐
│  samples/<agent-app>/  独立 Maven 模块                    │
│   继承 ② 的适配基类，在方法体里 build 具体 ReActAgent：  │
│   prompt + tools + model 都在此定义                       │
│  依赖：engine（② 的 SPI）+ openjiuwen 框架（①）          │
└────────────────────────────────────────────────────────────┘
```

## 3. agentscope 真实类 → 本项目映射（逐一核实）

下表每行均来自 clone 的 agentscope-runtime-java 真实代码。

| agentscope 真实物 | 出处 | 本项目对应 |
|---|---|---|
| `agentscope-core`（框架 jar） | `pom.xml` 依赖 `io.agentscope:agentscope-core:1.0.0` | `com.openjiuwen:agent-core-java:0.1.12`（pom 依赖） |
| `AgentHandler` 接口 | `engine-core/.../adapters/AgentHandler.java` | engine `spi/AgentHandler`（主设计 §9.1） |
| `AgentScopeAgentHandler`（abstract 适配基类） | `engine-core/.../adapters/agentscope/AgentScopeAgentHandler.java` | engine `adapter/openjiuwen/OpenJiuwenAgentHandler`（适配基类） |
| `MyAgentScopeAgentHandler`（开发者具体 agent，**独立模块**） | `examples/.../MyAgentScopeAgentHandler.java` | **开发者 agent 子类（samples 下独立模块）** |
| `Runner`（代理 handler、流式执行） | `engine-core/.../engine/Runner.java` | `EngineDispatcher`（主设计），但由**队列**触发 |
| `AgentApp.run(port)` → HTTP/SSE 部署 | `web/.../app/AgentApp.java` | **不采用**；改由 `EngineCommandEvent`→队列→dispatch，结果回写 `TaskControlClient`/`AccessLayerClient` |
| `LocalDeployManager`（起 Spring Boot） | `web/.../LocalDeployManager.java` | `PlatformApplication`（已有单进程）+ `EngineCommandProcessor`（队列消费） |

## 4. 运行形态取舍（保留队列，仅借鉴分层）

agentscope 与本项目的**执行内核同形**——都是"AgentHandler 流式执行"
（agentscope：`streamQuery → Flux<Event>`；本项目：`execute → Stream<EngineExecutionEvent>`）。
差异只在"**如何触发、结果送往何处**"：

| 维度 | agentscope-runtime | 本项目 engine（保留） |
|---|---|---|
| 触发方式 | HTTP 请求进 `AgentApp` | `EngineCommandEvent` 进内部命令队列 |
| 编排 | `Runner` 直接代理 | `EngineDispatcher` 经队列消费触发 |
| 结果出口 | HTTP/SSE 响应客户端 | 回写 `TaskControlClient` / `AccessLayerClient` |
| 部署 | `AgentApp.run(port)` 独立端口 | 随 `PlatformApplication` 单进程，`EngineCommandProcessor` auto-start |

**决策（已确认）**：engine 保留队列 + 事件回写模型，**不引入** HTTP 部署。
agentscope 仅用于借鉴"框架 / runtime / agent 应用"的三层切分与代码组织。

## 5. Agent 应用模块形态（决策：独立 Maven 模块）

**决策（已确认，选项 B）**：开发者的"具体 agent 子类"作为**独立 Maven 模块**存在，
放在仓库已有的 `examples/` 顶层目录下，照 `examples/finance-loan-review/` 现成样板。

依据真实结构核实：
- 根 `pom.xml` 为 `packaging=pom` 多模块聚合，现有 8 个模块；
- `examples/` 是顶层目录，已有先例 `examples/finance-loan-review/`（独立模块，已被 git 跟踪）；
- 该 sample 的 pom 注释明言："intentionally NOT listed in the root reactor by default…
  Add it to the root reactor explicitly when you want the full build to compile and test it."

即 sample 模块**不挂进根 `<modules>`**，按需显式加入构建。这与"engine 是核心工作区、
在外部加 example 模块合理、不破坏 agent-service 边界"的约束一致。

### 5.1 新模块 pom 骨架（仿 `examples/finance-loan-review` 样板）

```xml
<project ...>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.huawei.ascend</groupId>
    <artifactId>spring-ai-ascend-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>

  <artifactId>openjiuwen-echo-agent-sample</artifactId>
  <packaging>jar</packaging>
  <name>spring-ai-ascend openjiuwen echo agent sample</name>

  <properties>
    <enforcer.skip>true</enforcer.skip>   <!-- sample 不入主线分发 -->
  </properties>

  <dependencies>
    <!-- ② engine：拿到 AgentHandler SPI 与 OpenJiuwen 适配基类 -->
    <dependency>
      <groupId>com.huawei.ascend</groupId>
      <artifactId>agent-service</artifactId>
      <version>${project.version}</version>
    </dependency>
    <!-- ① 框架：openjiuwen agent-core-java -->
    <dependency>
      <groupId>com.openjiuwen</groupId>
      <artifactId>agent-core-java</artifactId>
      <version>0.1.12</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

> 注：`agent-service` 是否作为可被依赖的 jar 暴露 engine 适配基类，需在实现阶段确认其
> packaging 与 API 可见性（主设计将适配基类置于 `engine/adapter/openjiuwen/`，为 public）。
> 若 agent-service 仅为可执行服务、不宜被 sample 依赖，则需把 engine 抽为独立可依赖模块——
> 此点留待实现计划阶段裁定，不在本指导武断结论。

## 6. 对主设计 §10 的结构修正

主设计 §10 当前把"适配基类"与"造具体 agent 的 Factory/定义"**捏在 engine 内**。
对齐 agentscope 分层后，二者拆开：

| 主设计 §10 现状 | 对齐后归属 |
|---|---|
| `OpenJiuwenAgentHandler`（适配基类，连框架、走 `Runner.runAgent`） | 留在 engine ②层（通用） |
| `OpenJiuwenAgentFactory`（build 具体 ReActAgent：prompt/tools/model） | 下沉到 ③层（开发者模块），由 agent 子类实现 |
| `OpenJiuwenMessageConverter`（入参转换） | 留在 engine ②层（通用机制） |

即：**engine 提供"怎么把一个 openJiuwen agent 跑起来并映射成事件"的通用骨架；
开发者模块提供"这个 agent 具体是什么"（prompt/工具/模型）**。
`AgentHandlerRegistry`（主设计 §8.2）按 `agentId` 把开发者子类注册进 engine。

## 7. 四个"在哪"——对齐后的最终答案

| 问题 | 答案 |
|---|---|
| 真实 agent 代码放哪 | ③层：`samples/<agent-app>/` 独立模块里的 AgentHandler 子类（开发者写，定义 prompt/工具/模型） |
| engine 代码放哪 | ②层：`agent-service/.../engine/`（SPI + 队列/dispatch/事件 + OpenJiuwen 通用适配基类） |
| 怎么关联 | `AgentHandlerRegistry.register(agentId, handler)`；依赖单向：③ → ②(engine SPI) + ①(框架) |
| 启动代码放哪 | `PlatformApplication`（唯一 `@SpringBootApplication`）；`EngineCommandProcessor` 随进程 auto-start 消费队列触发执行 |

## 8. 不做项（YAGNI）

- 不引入 agentscope 的 `AgentApp.run(port)` HTTP/SSE 部署（保留队列模型）。
- 不把 sample 模块挂进根 `<modules>`（reactor 外，按需显式加入）。
- 不在本指导裁定 agent-service 是否需拆出独立可依赖 engine 模块（留待实现计划）。
- 不引入 agentscope 的 sandbox / Deployer / 多协议端点（超出 engine 执行器职责）。
