# financial — 银行客户智能体工作区

本目录是**银行客户开发者的工作区**,用于在 `spring-ai-ascend` 平台之上开发金融行业智能体。

## 基本原则(不可破坏)

1. **绝不修改平台**。`agent-runtime` / `agent-bus` / `agent-service`、根 `pom.xml` 的 reactor、`gate/` 治理层、架构记录——一律不动。本目录的存在不需要改平台的任何文件。
2. **以"外部客户"的姿态消费平台**。本工程是一个**独立 Maven 工程**:parent 用 `spring-boot-starter-parent`,**不**继承平台 reactor parent,也**不**加入根 `<modules>`。它通过依赖 `com.huawei.ascend:agent-runtime` 来使用平台。
3. **所有金融领域代码都在本目录下**。与 `agent-runtime` 平行,互不侵入。

## 目录结构

```
financial/
├── pom.xml                                  # 独立工程,依赖平台 agent-runtime
├── src/main/java/com/bank/financial/
│   ├── FinancialAgentApplication.java       # Spring Boot 入口(扫描自己的包 + runtime.boot)
│   └── agent/
│       └── FinancialAdvisorAgentConfiguration.java   # 第一个 agent:只读理财顾问
└── src/main/resources/
    └── application.yaml                      # A2A 接入 + LLM 连接配置
```

## 客户扩展平台的姿势(三步)

1. 继承 `OpenJiuwenAgentRuntimeHandler(agentId)`;
2. 重写 `createOpenJiuwenAgent(ctx)`,构建一个 `ReActAgent`(系统提示 + 模型;更复杂的 agent 还可挂护栏 / 人审 rail / 检查点);
3. 把 handler 发布为 `@Bean`。运行时自动发现它,并经 A2A JSON-RPC 暴露。

## 在平台原语之上做金融能力(后续 agent)

平台底层的 openJiuwen core(`com.openjiuwen.core`)已提供:

- **合规 / AML 护栏** — 实现 `GuardrailBackend`,在输入边界触发,或作为 `AgentRail` 用 `agent.registerRail(...)` 挂上;有风险即短路抛 `GuardrailError`。
- **敏感动作前人工审批** — 继承 `harness.rails.interrupt.BaseInterruptRail`,在 `beforeToolCall` 判断是否需人审;需要则暂停,运行返回 `result_type:"interrupt"`,凭同一 `conversation_id` + `InteractiveInput` 续跑。
- **可持久的暂停/续跑** — 用 `CheckpointerFactory.setDefaultCheckpointer(...)` 配检查点(Redis 可跨进程/跨节点恢复),适配银行后台审批队列。

## 构建与运行

平台产物需先装进本地 `.m2`(在仓库根目录执行,这一步只写 `.m2` 与 `target/`,不改平台源码):

```bash
# 1) 安装平台依赖(agent-runtime 及其依赖)到本地 .m2
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home \
  ./mvnw -pl agent-runtime -am -DskipTests install

# 2) 独立构建本工作区
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home \
  ./mvnw -f financial/pom.xml -DskipTests package

# 3) 运行(需要一个可达的 LLM 端点;用环境变量覆盖默认占位符)
BANK_LLM_API_KEY=... BANK_LLM_API_BASE=... \
  ./mvnw -f financial/pom.xml spring-boot:run
```

启动后,A2A agent card 在 `http://localhost:8080/.well-known/agent-card.json`,JSON-RPC 端点在 `/a2a`。

> 安全提醒:运行时**不**认证 `X-Tenant-Id`。生产部署必须置于会鉴权并重注入租户头的前置网关之后,不要把 `/a2a` 直接暴露给不可信客户端。
