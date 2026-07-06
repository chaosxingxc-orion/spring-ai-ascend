# EDPAgent Spike 生产化方案

## 1. 背景与目标

当前 `agent-store/edp-agent-trae/Spike` 是 EDPAgent Java 重构的 Spike 验证工程，已经验证了基于 OpenJiuwen DeepAgent、agent-runtime A2A 接入、EDPAgent 业务工具和 Rails 的主链路可行性。

生产化目标不是简单改名，而是将当前验证性工程转为可交付、可部署、可运维、可测试、可审计的正式 EDPAgent Java 服务。

生产版本应具备：

- 标准工程结构。
- 明确模块边界。
- 外部化配置与密钥管理。
- 稳定 A2A 接入能力。
- 可观测性。
- 自动化测试与质量门禁。
- Docker / Kubernetes 部署能力。
- 安全合规和审计能力。
- 面向多业务 Skill 的扩展能力。

---

## 2. 当前 Spike 已验证能力

当前 Spike 已验证以下核心能力：

| 能力 | 当前状态 |
|---|---|
| Spring Boot 启动 | 已验证 |
| agent-runtime A2A 接入 | 已验证 |
| OpenJiuwen DeepAgent 创建 | 已验证 |
| Skill 目录加载 | 已验证 |
| 5 个 EDPAgent 业务工具注册 | 已验证 |
| 9 个 EDPAgent Rails 注册 | 已验证 |
| `ask_user` 中断与恢复 | 已验证 |
| `call_mcp` 脚本/MCP 调用 | 已验证 |
| `call_versatile` 工作流委托 | 已验证 |
| 理财推荐、选品、资金筹划、购买链路 | 已验证 |

当前已验证的业务路径包括：

1. 理财卡余额充足，直接购买。
2. 理财卡余额不足，储蓄卡补足后资金汇聚购买。
3. 两卡合计不足，安全终止购买。

---

## 3. 生产化总体路线

建议分 8 个阶段推进：

```text
阶段 1：工程身份生产化--Agent
阶段 2：架构包结构生产化--Aget
阶段 3：配置与密钥生产化--Runtimg
阶段 4：运行时稳定性生产化---time
阶段 5：工具 / Rail / Skill 生产化--anget
阶段 6：安全与合规生产化--？
阶段 7：测试与质量门禁生产化---？解决方案
阶段 8：部署、运维、发布生产化---？？
横向补齐：近期 FEAT_EDPA 多子Agent并行与 GH 接口需求---Agent
```

---

## 4. 近期 FEAT_EDPA 需求补齐

生产化范围需要记录并覆盖以下两个近期需求文档中的能力：

- `FEAT_EDPA 杭研智贷通EDPA支持多个子Agent并行执行以及子Agent内多工作流并行执行_特性用例说明_20260529 (1).md`
- `FEAT_EDPA GH 杭研智贷通接口特性用例.md`

本部分仅作为生产化范围记录，不在本文展开具体实现方案。相关能力已有 Python 版本实现基础，Java 生产版本按“对齐 Python 版本能力并迁移到 Java + OpenJiuwen DeepAgent 架构”的原则推进。

需要纳入迁移范围的事项：

| 事项 | 处理原则 |
|---|---|
| 多个子Agent并行执行 | 从 Python 版本迁移并适配 Java 运行时 |
| 子Agent内多工作流并行执行 | 从 Python 版本迁移并适配 Java 运行时 |
| 杭研智贷通 GH 接口能力 | 从 Python 版本迁移并适配 Java 运行时 |
| 配套配置、事件、状态和测试 | 按 Python 版本能力补齐，本文不展开实现细节 |

---

## 5. 阶段 1：工程身份生产化

### 5.1 去 Spike 化

当前工程名、artifactId、启动类都带有 Spike 语义。生产化需要去掉验证工程身份。

建议调整：

| 当前 | 建议 |
|---|---|
| `Spike` 目录 | `runtime` 或正式主模块 |
| `edp-agent-spike` | `edp-agent-runtime` |
| `EdpaSpikeApplication` | `EdpaApplication` |
| `EdpaSpikeConfiguration` | `EdpaRuntimeConfiguration` |

Maven 建议：

```xml
<artifactId>edp-agent-runtime</artifactId>
<name>edp-agent-runtime</name>
<description>Production EDPAgent runtime powered by OpenJiuwen DeepAgent and agent-runtime.</description>
```

### 5.2 纳入正式构建

生产版本应纳入父工程 modules 和 CI/CD。

需要完成：

- 父级 `pom.xml` 增加正式模块。
- 明确版本号策略。
- 明确构建产物名称。
- 明确 Docker 镜像名称。
- 明确服务注册名称。
- 移除临时测试命名。

---

## 6. 阶段 2：架构包结构生产化

当前已有：

```text
config/
enhancer/
handler/
rail/
tools/
```

生产化建议补齐：

```text
com.huawei.ascend.edp
  handler/
  enhancer/
  config/
  tools/
  rail/
  channel/
  utterance/
  stream/
  security/
  observability/
  runtime/
```

### 6.1 新增 `channel/`

用于承载跨工具、跨 Rail、跨执行阶段的上下文数据。

建议新增：

```text
channel/
  ToolDataChannel.java
  ToolDataKey.java
  ToolDataScope.java
```

职责：

- 保存 MCP 推荐结果。
- 保存 MCP 到 Versatile 的中间数据。
- 保存选品上下文。
- 保存资金筹划上下文。
- 按 `tenantId + agentId + contextId + taskId` 隔离数据。
- 支持清理、过期、审计。

禁止：

- 在工具类中保存全局状态。
- 使用静态裸 Map 承载会话数据。
- 将 `ToolDataChannel` 注册为 Tool。

### 6.2 新增 `utterance/`

用于管理话术模板。

建议新增：

```text
utterance/
  ScriptsConfigManager.java
  ResponseTemplateManager.java
  TemplateRenderer.java
  TemplateVariables.java
```

职责：

- 加载 `ScriptsConfig.md`。
- 管理 `ask_user` 模板。
- 管理取消确认模板。
- 管理购买成功、资金不足、异常说明模板。
- 做安全变量替换。
- 做 JSON 容错处理。

### 6.3 新增 `stream/`

用于统一流式事件输出。

建议新增：

```text
stream/
  FixedScriptFeeder.java
  StreamFrameFactory.java
  StreamEventNormalizer.java
```

职责：

- 固定思考脚本输出。
- A2A `artifactUpdate` 输出。
- `todo_start` / `todo_end` 输出。
- 最终摘要输出。
- 断连清理。

---

## 7. 阶段 3：配置与密钥生产化

### 7.1 移除明文密钥

当前 `edp-agent.yaml` 中存在模型 API Key。生产版本必须外部化。

建议：

```yaml
model:
  provider: OpenAI
  name: ${EDP_AGENT_MODEL_NAME:deepseek-v4-pro}
  baseUrl: ${EDP_AGENT_MODEL_BASE_URL}
  apiKey: ${EDP_AGENT_MODEL_API_KEY}
```

密钥来源可选：

- 环境变量。
- Kubernetes Secret。
- Vault。
- Spring Cloud Config。
- 企业内部密钥管理服务。

禁止：

- 明文提交 API Key。
- 在日志中打印 API Key。
- 在测试报告中暴露 API Key。

### 7.2 配置分层

建议拆分：

```text
application.yml
application-dev.yml
application-test.yml
application-prod.yml
edp-agent.yaml
edp-agent-prod.yaml
edp-config.yaml
edp-config-prod.yaml
```

基础原则：

- 通用配置放 `application.yml`。
- 环境差异放 profile。
- 业务 Prompt 与 Skill 放 Agent 配置。
- 密钥全部外部注入。

### 7.3 启动时配置校验

启动时必须校验：

- 模型 provider/name/baseUrl/apiKey 是否完整。
- `versatile.url` 是否合法。
- Skill 目录是否存在。
- `todolist_steps` 与 Prompt 中 step_id 是否一致。
- 工具声明与实际注册是否一致。
- timeout、limit 是否合理。
- 生产环境是否禁用了不安全工具能力。
- 近期 FEAT_EDPA 相关配置项按 Python 版本能力迁移，并纳入启动校验范围。

---

## 8. 阶段 4：运行时稳定性生产化

### 8.1 健康检查

生产版本需要接入 Spring Boot Actuator。

建议暴露：

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
/actuator/metrics
```

健康检查应覆盖：

| 检查项 | 说明 |
|---|---|
| Spring Boot | 服务是否存活 |
| DeepAgent | 是否初始化完成 |
| A2A handler | `edp-agent` 是否注册 |
| Skill | Skill 是否加载成功 |
| 模型服务 | LLM endpoint 是否可用 |
| Versatile | 工作流服务是否可用 |
| MCP / 脚本 | 脚本目录和执行权限是否正常 |
| FEAT_EDPA 迁移能力 | Python 版本能力对应配置是否加载 |

### 8.2 超时、重试、熔断

外部依赖必须增加保护：

| 依赖 | 保护机制 |
|---|---|
| LLM API | 超时、重试、限流、熔断 |
| Versatile 工作流 | 超时、重试、熔断、降级 |
| MCP 脚本 | 超时、退出码处理、stdout/stderr 限长 |
| A2A 流式响应 | 客户端断连检测、资源释放 |
| ask_user 中断 | 中断超时、恢复校验 |
| FEAT_EDPA 迁移链路 | 按 Python 版本能力补齐超时、限流和故障保护 |

### 8.3 会话状态管理

生产版本需要明确状态归属：

| 状态 | 建议 |
|---|---|
| A2A task 状态 | 由 agent-runtime 管理 |
| DeepAgent checkpoint | 接入可靠存储 |
| ask_user 中断上下文 | 按 contextId/taskId 持久化 |
| ToolDataChannel 数据 | 按 tenant/session/context/task 隔离 |
| todo 状态 | 可恢复、可审计 |
| 购买确认状态 | 必须可追溯 |
| FEAT_EDPA 迁移状态 | 按 Python 版本能力补齐相关状态管理 |

---

## 9. 阶段 5：工具 / Rail / Skill 生产化

### 9.1 保持 5 个业务工具边界

生产版本仍保持 5 个 EDPAgent 业务工具：

1. `lite_todo_write`
2. `call_mcp`
3. `call_versatile`
4. `ask_user`
5. `cancel_task`

不要把系统工具做成 EDPAgent 自定义业务工具。

禁止新增：

- `ReadFileTool`
- `BashTool`
- `ExecuteCmdTool`

`read_file`、`readFile`、`bash`、`execute_cmd` 应复用 DeepAgent / OpenJiuwen 系统工具能力或按生产安全策略禁用。

### 9.2 工具保持薄实现

| 工具 | 生产化职责 |
|---|---|
| `CallMcpTool` | 只表达 MCP 调用意图 |
| `CallVersatileTool` | 只表达 VA 委托意图 |
| `CancelTaskTool` | 只表达取消意图 |
| `EnhancedAskUserTool` | 触发真实中断 |
| `LiteTodoWriteTool` | 提供 Schema 与 todo 意图 |

真正执行逻辑应放在 Rails 中。

### 9.3 Rails 承担业务执行

| Rail | 生产化重点 |
|---|---|
| `McpInterruptRail` | 脚本执行、沙箱、超时、结果解析 |
| `VersatileInterruptRail` | 工作流调用、级联恢复、结果归一 |
| `AskUserTemplateRail` | 中断恢复、模板渲染、确认语义判断 |
| `LiteTodoRail` | todo 状态一致性、事件输出 |
| `ExecutionLimitRail` | 工具调用次数限制、事件抑制 |
| `IterationLimitRail` | ReAct 迭代保护 |
| `CancelRail` | 取消确认、任务终止、上下文清理 |
| `MemoryRail` | 可选记忆存储 |
| `LogRail` | 模型输入输出与工具调用观测 |

近期 FEAT_EDPA 涉及的编排、接口、状态、事件能力从 Python 版本迁移补齐，本文不展开组件级实现方案。

### 9.4 Skill 生产化

当前 Skill 应补齐版本、契约、测试和安全约束。

建议结构：

```text
skills/
  product_recommend_skill/
    v1/
      SKILL.md
      scripts/
      schema/
      tests/
  product_select_skill/
    v1/
  fund_planning_skill/
    v1/
```

生产要求：

- 每个 Skill 有版本号。
- 每个 Skill 有输入输出 Schema。
- 脚本返回统一 JSON。
- 脚本有超时和退出码处理。
- 禁止任意读写文件。
- 禁止调用未授权网络地址。
- 支持灰度和回滚。
- 近期 FEAT_EDPA 涉及的 Skill 能力按 Python 版本迁移补齐。

---

## 10. 阶段 6：安全与合规生产化

### 10.1 密钥安全

必须保护：

- 模型 API Key。
- Versatile token。
- MCP token。
- 数据库密码。
- Redis 密码。
- 内部服务凭证。

CI 中必须加入 Secret 扫描。

### 10.2 工具安全

生产环境对脚本和 shell 能力必须收敛。

建议：

- 默认禁用通用 shell。
- 只允许白名单脚本。
- 禁止模型自由拼接命令。
- 参数必须 JSON Schema 校验。
- 执行目录固定。
- 执行用户降权。
- 超时强制终止。
- stdout/stderr 限长。
- 禁止访问敏感目录。

### 10.3 数据安全

理财购买场景涉及金融数据，必须具备：

- 用户身份隔离。
- 账户数据脱敏。
- 卡号只显示尾号。
- 日志脱敏。
- 购买确认审计。
- 转账/购买操作审计。
- 模型输入输出留痕策略。
- 敏感字段最小化进入 Prompt。
- 近期 FEAT_EDPA 迁移能力涉及的数据、事件和日志同样纳入脱敏与审计范围。

---

## 11. 阶段 7：测试与质量门禁生产化

### 11.1 单元测试

覆盖：

- 配置加载。
- 配置校验。
- Tool Schema。
- Rail 状态机。
- 模板渲染。
- JSON 容错。
- ToolDataChannel 隔离。
- 限流、超时逻辑。
- 近期 FEAT_EDPA 迁移能力的配置、状态、事件和异常分支。

### 11.2 集成测试

覆盖：

- Spring Boot 启动。
- A2A handler 注册。
- DeepAgent 初始化。
- Skill 加载。
- `ask_user` interrupt/resume。
- `call_mcp`。
- `call_versatile`。
- 资金筹划链路。
- 近期 FEAT_EDPA 迁移能力端到端回归。

### 11.3 端到端测试

固定三条主路径：

| 用例 | 期望 |
|---|---|
| 100 元购买 | 理财卡余额充足，直接购买 |
| 10000 元购买 | 储蓄卡补足后购买 |
| 200000 元购买 | 两卡合计不足，终止购买 |
| 近期 FEAT_EDPA 迁移能力 | 按 Python 版本既有能力补齐回归用例 |

### 11.4 质量门禁

生产合入前至少执行：

```powershell
.\mvnw.cmd clean verify
```

若只验证当前模块：

```powershell
.\mvnw.cmd -f agent-store\edp-agent-trae\Spike\pom.xml clean verify
```

门禁项：

- 单元测试通过。
- 集成测试通过。
- A2A E2E 通过。
- 静态检查通过。
- 依赖漏洞扫描通过。
- Secret 扫描通过。
- 镜像扫描通过。
- 架构同步检查通过。

---

## 12. 阶段 8：部署、运维、发布生产化

### 12.1 打包交付物

生产版本应输出：

- 可执行 jar。
- Docker 镜像。
- Helm Chart / K8s YAML。
- 配置模板。
- 启停脚本。
- 运维手册。

### 12.2 服务端口

当前端口是 `8190`。生产建议改为：

```yaml
server:
  port: ${SERVER_PORT:8190}
```

### 12.3 日志

生产日志要求：

- JSON 结构化日志。
- traceId / contextId / taskId / agentId。
- 近期 FEAT_EDPA 迁移能力所需的业务维度标识。
- 工具调用耗时。
- 外部依赖耗时。
- 错误码。
- 脱敏。
- 日志滚动。
- 按环境配置日志级别。

### 12.4 监控指标

建议暴露：

| 指标 | 含义 |
|---|---|
| `edp_agent_requests_total` | 请求总数 |
| `edp_agent_request_duration` | 请求耗时 |
| `edp_agent_tool_calls_total` | 工具调用次数 |
| `edp_agent_llm_calls_total` | 模型调用次数 |
| `edp_agent_interrupt_total` | ask_user 中断次数 |
| `edp_agent_resume_total` | 中断恢复次数 |
| `edp_agent_errors_total` | 错误总数 |
| `edp_agent_versatile_latency` | Versatile 调用耗时 |
| `edp_agent_mcp_latency` | MCP 调用耗时 |
| FEAT_EDPA 迁移能力指标 | 按 Python 版本既有观测维度补齐 |

### 12.5 发布策略

建议：

1. dev 环境验证。
2. test 环境自动化回归。
3. staging 环境灰度。
4. prod 小流量发布。
5. 观察指标。
6. 全量发布。
7. 保留回滚版本。

---

## 13. 推荐目标目录结构

生产版本建议拆成 **通用动态规划 Agent 内核** 与 **场景化资产包** 两层：

- `runtime/`：只保存通用 Java Agent 运行时能力，不写入具体业务场景。
- `scenarios/`：保存场景化 Prompt、Skill、话术、工作流、接口契约和测试资产。

```text
agent-store/edp-agent-java
├── README.md
├── pom.xml
├── .env.example
├── docs/
├── runtime/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/huawei/ascend/edp/
│       │   ├── EdpaApplication.java
│       │   ├── EdpaRuntimeConfiguration.java
│       │   ├── config/
│       │   ├── handler/
│       │   ├── enhancer/
│       │   ├── planner/
│       │   ├── tools/
│       │   ├── rail/
│       │   ├── channel/
│       │   ├── utterance/
│       │   ├── stream/
│       │   ├── security/
│       │   └── observability/
│       └── main/resources/
│           ├── application.yml
│           └── application-prod.yml
├── scenarios/
│   ├── README.md
│   ├── wealth-demo/
│   │   ├── edp-agent.yaml
│   │   ├── edp-config.yaml
│   │   ├── ScriptsConfig.md
│   │   ├── skills/
│   │   └── tests/
│   └── hz-zhidaitong/
│       ├── edp-agent.yaml
│       ├── edp-config.yaml
│       ├── ScriptsConfig.md
│       ├── skills/
│       └── tests/
├── deploy/
│   ├── docker/
│   └── helm/
└── tests/
    ├── contract/
    ├── e2e/
    └── performance/
```

系统级话术目录的作用如下：

| 路径 | 作用 |
|---|---|
| `runtime/src/main/resources/utterances/system-thinking.yaml` | 系统级思维链进度话术，用于通用规划、执行、等待、汇总、取消、异常等状态，客户一般不修改。 |
| `runtime/src/main/resources/utterances/system-events.yaml` | 系统级事件话术，用于通用事件输出、系统级错误、默认兜底提示等。 |

`scenarios/` 下每个场景目录的作用如下：

| 路径 | 作用 |
|---|---|
| `scenarios/README.md` | 说明场景资产包规范、命名规则、必备文件和加载方式。 |
| `scenarios/{scenario}/edp-agent.yaml` | 场景级 Agent 配置，包括场景 Prompt、模型策略、Skill 目录、可用工具声明等。 |
| `scenarios/{scenario}/edp-config.yaml` | 场景级动态规划配置，包括规划步骤、步骤依赖、执行限制、记忆开关、摘要规则等。 |
| `scenarios/{scenario}/ScriptsConfig.md` | 场景级话术模板，包括确认、执行中、取消、异常、结果汇总等用户可见表达。 |
| `scenarios/{scenario}/skills/` | 场景级 Skill 资产，保存 `SKILL.md`、脚本、Schema、Skill 内部说明等。 |
| `scenarios/{scenario}/tests/` | 场景级测试资产，包括 E2E 用例、契约测试、样例输入输出、Mock 数据和回归基线。 |

场景选择建议通过环境变量或启动参数指定：

```text
EDP_AGENT_SCENARIO=hz-zhidaitong
EDP_AGENT_SCENARIO_HOME=scenarios/hz-zhidaitong
```

生产版本目标不是生成一个面向单一客户的 Agent，而是形成通用动态规划 Agent 内核。所有场景化资产独立放入 `scenarios/{scenario}/` 目录，通过启动参数选择场景资产包加载。

---

## 14. 生产化任务优先级

### P0：必须先做

| 任务 | 说明 |
|---|---|
| 去掉明文 API Key | 改为环境变量或密钥服务 |
| 确认正式模块位置 | `Spike` 是否迁移/重命名为 `agent-store/edp-agent-java` |
| 改 Maven artifactId | 从 `edp-agent-spike` 改为生产名 |
| 建立生产配置 profile | dev/test/prod |
| 建立场景资产目录 | 使用 `scenarios/{scenario}/` 管理场景化资产 |
| 建立场景选择机制 | 支持 `EDP_AGENT_SCENARIO` 和 `EDP_AGENT_SCENARIO_HOME` |
| 建立健康检查 | actuator health/readiness/liveness |
| 固定三条 E2E 回归用例 | 直购/资金汇聚/余额不足 |
| 梳理工具清单 | 保持 5 个业务工具边界 |
| 梳理系统工具策略 | `bash`、`readFile`、`skill_tool` 不做 EDP 自定义工具 |
| 记录近期 FEAT_EDPA 迁移范围 | 多子Agent并行、子Agent内多工作流并行、GH 接口能力从 Python 版本迁移 |

### P1：架构整改

| 任务 | 说明 |
|---|---|
| 新增 `channel/` | ToolDataChannel 生产化 |
| 新增 `utterance/` | 话术模板管理 |
| 新增 `stream/` | 固定脚本和事件流输出 |
| 新增 `planner/` | 通用动态规划能力，不写具体场景逻辑 |
| 整理 Rail 职责 | 保持工具薄、Rail 厚 |
| 配置校验器 | 启动时失败快报 |
| 近期 FEAT_EDPA 能力迁移 | 按 Python 版本实现边界补齐必要模块 |

### P2：运行稳定性

| 任务 | 说明 |
|---|---|
| 外部调用超时 | LLM / Versatile / MCP |
| 重试和熔断 | 防止外部服务拖垮 |
| 上下文隔离 | tenant/session/context/task |
| 中断恢复持久化 | ask_user 可恢复 |
| 资源清理 | 取消、异常、超时都要清理 |
| 近期 FEAT_EDPA 迁移链路稳定性 | 按 Python 版本能力补齐运行保护 |

### P3：安全合规

| 任务 | 说明 |
|---|---|
| Secret 扫描 | CI 阻断明文密钥 |
| 日志脱敏 | 卡号、手机号、token、余额敏感处理 |
| 脚本白名单 | 禁止模型自由执行 shell |
| 审计日志 | 购买、转账、取消、确认 |
| 权限控制 | 生产只开放必要接口 |

### P4：测试门禁

| 任务 | 说明 |
|---|---|
| 单元测试 | Tool / Rail / Config |
| 集成测试 | DeepAgent + A2A |
| E2E 测试 | 三类理财购买路径 |
| 压测 | 并发、长流、断连 |
| 回归报告 | 自动生成测试报告 |
| 近期 FEAT_EDPA 回归 | 按 Python 版本能力补齐对应回归用例 |

### P5：发布运维

| 任务 | 说明 |
|---|---|
| Dockerfile | 标准镜像 |
| Helm Chart | K8s 部署 |
| 运行手册 | 启停、排障、回滚 |
| 监控面板 | 请求量、错误率、耗时 |
| 告警规则 | 服务不可用、错误率升高、外部依赖异常 |

---

## 15. 生产化验收标准

生产版本至少满足：

1. 启动无 Spike 命名。
2. 无明文密钥。
3. 支持 dev/test/prod 配置。
4. `edp-agent` 可被 A2A runtime 正确注册。
5. DeepAgent 初始化健康检查可观测。
6. 5 个业务工具注册稳定。
7. 9 个 Rails 注册稳定。
8. Skill 加载稳定。
9. `ask_user` 中断恢复真实可用。
10. 三类理财购买路径自动化通过。
11. 日志脱敏。
12. 外部依赖有超时、重试、熔断。
13. Docker/K8s 可部署。
14. `mvnw.cmd clean verify` 通过。
15. 有运行手册和故障排查手册。
16. 近期 FEAT_EDPA 涉及的多子Agent并行、子Agent内多工作流并行、GH 接口能力已按 Python 版本能力迁移补齐。
17. 近期 FEAT_EDPA 迁移能力具备对应配置、测试和运维观测。

---

## 16. 结论

当前 Spike 已经验证了 EDPAgent Java 化的主链路，生产化不需要推倒重来。

后续重点是：

```text
工程正式化
配置安全化
运行稳定化
状态持久化
近期 FEAT_EDPA 能力从 Python 版本迁移补齐
测试自动化
部署运维标准化
安全合规体系化
```

推荐先完成 P0 和 P1，再进入运行稳定性、安全合规、自动化测试和部署运维建设。

---

## 17. 三方能力评估与功能分工矩阵（Core / Runtime / EDPA）

本节对生产化方案涉及的三方——JiuwenAgent Core（`agent-core-java-0.1.12`）、agent-runtime（Spring Boot + A2A 框架）、EDPAgent（业务模块）——进行能力评估，明确各方已具备的能力边界，并给出 8 个阶段的功能分工矩阵，指导生产化任务的归属判定。

分工标记含义：

| 标记 | 含义 |
|:---:|---|
| 复用 | 该方已提供完整能力，使用方直接调用 |
| 配置 | 该方已提供能力框架，使用方只需配置参数/策略 |
| 薄封装 | 该方已有基础能力，使用方在其上加业务定制层 |
| 自建 | 该方无对应能力，使用方需从零构建 |
| — | 不涉及该方 |

### 17.1 三方角色定位

| 角色 | 模块 | 定位 | 边界 |
|---|---|---|---|
| JiuwenAgent Core | `agent-core-java-0.1.12` | Agent 执行内核，提供 DeepAgent 创建、ReAct 推理引擎、Rail 机制、工具框架、中断恢复、Task Loop 控制、权限引擎、沙箱执行、Checkpointer、SubAgent 框架等通用 Agent 能力 | 不涉及 HTTP 接入、A2A 协议、Spring Boot 生命周期、业务逻辑、业务配置安全 |
| agent-runtime | `agent-runtime` | Spring Boot 服务框架，提供 A2A HTTP 接入、流式传输、task 状态管理、Actuator 基础健康/监控、基础日志框架、架构同步门禁 | 不涉及 Agent 执行内核、业务工具/Rail/Skill、业务配置、业务安全 |
| EDPAgent | `edp-agent` | 业务 Agent 模块，基于 Core 的 DeepAgent 内核和 Runtime 的 A2A 框架，承载理财场景的全部业务逻辑（工具/Rail/Skill/Prompt/话术/通道）、业务配置安全、业务测试与发布 | 不涉及 Agent 执行内核、A2A 协议框架 |

### 17.2 JiuwenAgent Core 能力盘点

| 能力域 | Core 已提供 | 关键类 |
|---|---|---|
| DeepAgent 创建与生命周期 | `HarnessFactory.createDeepAgent` / `DeepAgent` | `harness.factory.HarnessFactory`、`harness.deep_agent.DeepAgent` |
| ReAct Agent 执行引擎 | `ReActAgent` / `ReActAgentConfig` | `core.singleagent.agents.ReActAgent` |
| Skill 加载与管理 | `SkillUseRail` / `SkillTool` / `SkillManager` | `harness.rails.SkillUseRail`、`harness.tools.SkillTool`、`core.singleagent.skills.SkillManager` |
| ask_user 中断机制 | `AskUserRail` / `AskUserTool` / `BaseInterruptRail` / `InterruptRequest` / `ToolInterruptException` | `harness.rails.interrupt.*` |
| MCP 资源读取 | `McpRail` / `ReadMcpResourceTool` / `ListMcpResourcesTool` | `harness.rails.McpRail`、`harness.tools.*` |
| Todo/Task 管理 | `TodoTool` / `TodoItem` / `TodoStatus` / `TaskPlanningRail` / `TaskCompletionRail` | `harness.tools.TodoTool`、`harness.rails.TaskPlanningRail` |
| Task Loop 控制 | `TaskLoopController` / `LoopCoordinator` / `StopConditionEvaluator` / `MaxRoundsEvaluator` / `TimeoutEvaluator` | `harness.task_loop.*` |
| 权限引擎 | `PermissionEngine` / `PermissionLevel` / `SecurityRail` / `PermissionInterruptRail` | `harness.security.*`、`harness.rails.SecurityRail` |
| Bash/Shell 执行 | `BashTool` / `PowerShellTool` | `harness.tools.BashTool` |
| 文件系统操作 | `FilesystemTool` | `harness.tools.FilesystemTool` |
| 流式输出 | `StreamOutput` / `StreamMode` / `OutputSchema` | `core.session.stream.*` |
| Session 管理 | `Session` / `AgentSessionApi` / `SessionRail` | `core.session.*`、`harness.rails.SessionRail` |
| Checkpointer（内存/Redis） | `InMemoryCheckpointer` / `RedisCheckpointer` | `extensions.checkpointer.*` |
| SubAgent（多子Agent） | `SubAgentRegistry` / `SubAgentConfig` / `SubagentRail` + 多种 Factory | `harness.subagents.*` |
| 浏览器工具 | `BrowserRuntimeTools` / `BrowserService` | `harness.tools.browser.*` |
| 记忆 | `MemoryRail` / `MemoryTools` / `ExternalMemoryRail` / `CodingMemoryRail` | `harness.rails.MemoryRail`、`harness.tools.MemoryTools` |
| 上下文演化 | `ContextEvolver` / `ContextProcessorRail` / `ContextAssembleRail` | `extensions.context_evolver.*`、`harness.rails.ContextProcessorRail` |
| 模型观测 | `ModelUsageRecord` / `HeartbeatRail` | `harness.rails.ModelUsageRecord`、`harness.rails.HeartbeatRail` |
| 沙箱执行 | `SysOperation` / `SandboxOperation` / `SandboxRegistry` | `core.sysop.*` |

### 17.3 agent-runtime 能力盘点

| 能力域 | Runtime 是否具备 | 说明 |
|:---|:---:|:---|
| A2A HTTP 接入 | 具备 | 提供 A2A 协议的 HTTP 端点，接收 task 请求 |
| 流式响应传输 | 具备 | SSE/WebSocket 流式输出，客户端断连检测与资源释放 |
| A2A task 状态管理 | 具备 | task 生命周期状态机（submitted/working/completed/failed），文档 8.3 明确归 Runtime |
| AgentCard 注册 | 具备 | EDPAgent 通过 agentId 向 Runtime 注册 |
| Spring Boot Actuator | 具备 | 基础健康检查（health/readiness/liveness）、JVM/HTTP metrics |
| 基础日志框架 | 具备 | JSON 结构化日志基础框架 |
| 架构同步门禁 | 具备 | `gate/check_architecture_sync.sh` 治理脚本 |
| 配置占位符解析 | 不具备 | Runtime 不解析 EDPAgent 业务配置中的 `${EDP_AGENT_*}` 占位符 |
| 配置分层（profile） | 不具备 | EDPAgent 的 application-{dev,test,prod}.yml 由 EDPAgent 自行管理 |
| 启动时配置校验 | 不具备 | Runtime 不校验 EDPAgent 业务配置完整性 |
| 业务健康检查 | 不具备 | DeepAgent/Skill/LLM/Versatile 可用性检查由 EDPAgent 实现 |
| 业务超时熔断 | 不具备 | LLM/Versatile/MCP 超时重试熔断由 EDPAgent 配置 |
| 业务监控指标 | 不具备 | 业务维度指标由 EDPAgent 自定义 |

### 17.4 EDPAgent 现状评估（基于 Spike 代码）

| 能力域 | Spike 现状 | 生产化差距 |
|:---|:---|:---|
| DeepAgent 集成 | 已集成，`EdpaRuntimeHandler` 继承 `OpenJiuwenAgentRuntimeHandler` | 仅使用 Core 5 个 API，大量能力未利用 |
| 5 个业务工具 | 已实现薄工具（`LiteTodoWriteTool`/`CallMcpTool`/`CallVersatileTool`/`EnhancedAskUserTool`/`CancelTaskTool`） | 工具仅声明意图，执行依赖 Rail，需补充 Rail 厚逻辑 |
| 7 个 Rail | 已实现骨架（`AskUserTemplateRail`/`CancelRail`/`McpInterruptRail`/`VersatileInterruptRail`/`LiteTodoRail`/`ExecutionLimitRail`/`LogRail`） | 多数为观测/标记级别，需补充真实执行逻辑 |
| Skill 加载 | 已加载 3 个 Skill（fund_planning/product_recommend/product_select） | 缺少版本/Schema/测试约束 |
| ask_user 中断恢复 | 已实现中断+恢复主链路 | 缺少持久化、超时、上下文清理 |
| 配置安全 | 明文 API Key，无占位符，无 profile，无校验 | 完全不具备，属 P0 必须先做 |
| 状态持久化 | 无持久化，解析失败降级为默认配置 | 完全不具备，需接入 Checkpointer |
| 业务数据通道 | 无 ToolDataChannel | 完全不具备，需自建 |
| 话术模板 | 仅 `ScriptsConfig.md` 路径记录，返回固定话术 | 仅有骨架，需自建模板引擎 |
| 数据脱敏 | 无脱敏 | 完全不具备 |
| 审计日志 | 无审计 | 完全不具备 |
| 测试 | 11 个 Spike 测试（P2-P18） | 缺少单元/集成/E2E/压测 |
| 部署 | 无 Dockerfile/Helm | 完全不具备 |

### 17.5 三方功能分工矩阵

#### 阶段 1：工程身份生产化

| 任务 | Core | Runtime | EDPAgent | 说明 |
|---|:---:|:---:|:---:|---|
| 去 Spike 化（目录/artifactId/启动类） | — | — | 自建 | EDPAgent 工程身份 |
| 纳入父工程 modules | — | — | 自建 | EDPAgent 模块注册 |
| agentId 声明 | — | 复用 | 配置 | Runtime 提供注册机制；EDPAgent 声明 `edp-agent` |

#### 阶段 2：架构包结构生产化

| 任务 | Core | Runtime | EDPAgent | 说明 |
|---|:---:|:---:|:---:|---|
| `channel/`（ToolDataChannel） | — | — | 自建 | Core 无此概念，属 EDPAgent 业务数据通道 |
| `utterance/`（话术模板） | — | — | 自建 | Core 无业务话术概念 |
| `stream/`（固定脚本事件流） | 复用 | — | 薄封装 | Core 已有 `StreamOutput`/`OutputSchema`；EDPAgent 只需实现业务帧（`todo_start`/`todo_end`/摘要） |
| `security/` | 复用 | — | 配置 | Core 已有 `PermissionEngine`/`SecurityRail`；EDPAgent 只需配置白名单策略 |
| `observability/` | 复用 | — | 薄封装 | Core 已有 `ModelUsageRecord`/`HeartbeatRail`；EDPAgent 补充业务指标 |
| `planner/` | 复用 | — | 薄封装 | Core 已有 `TaskPlanningRail`/`TaskLoopController`；EDPAgent 只需配置 step_id 映射 |

#### 阶段 3：配置与密钥生产化

| 任务 | Core | Runtime | EDPAgent | 说明 |
|---|:---:|:---:|:---:|---|
| 占位符 `${EDP_AGENT_MODEL_API_KEY}` 解析 | — | — | 自建 | Core 配置由 `DeepAgentConfig` 直接消费，无占位符解析；EDPAgent 需在 Loader 中实现 |
| application-{dev,test,prod}.yml profile | — | — | 自建 | Spring Boot profile 机制，EDPAgent 自行配置 |
| 密钥来源（K8s Secret / Vault / 环境变量） | — | — | 自建 | EDPAgent 消费外部化密钥 |
| 启动时配置校验 | — | — | 自建 | Core/Runtime 均无校验；EDPAgent 需在 `EdpaRuntimeHandler.init` 中实现 |

#### 阶段 4：运行时稳定性生产化

| 任务 | Core | Runtime | EDPAgent | 说明 |
|---|:---:|:---:|:---:|---|
| Spring Boot 服务存活检查 | — | 复用 | — | Runtime Actuator 基础能力 |
| DeepAgent 初始化健康检查 | 复用 | — | 薄封装 | Core `DeepAgent.isInitialized()` 已有；EDPAgent 只需暴露 HealthIndicator |
| Skill 加载健康检查 | 复用 | — | 薄封装 | Core `SkillManager.count()`/`hasSkill()` 已有 |
| LLM 超时/重试/熔断 | 复用 | — | 配置 | Core `ModelClientConfig`/`ModelRequestConfig` 已有超时；熔断需 EDPAgent 配置 |
| MCP 脚本超时/退出码/stdout 限长 | 复用 | — | 配置 | Core `SysOperation`/`SandboxOperation` 已有沙箱；EDPAgent 配置白名单 |
| A2A 流式断连/资源释放 | — | 复用 | — | Runtime A2A 框架职责 |
| A2A task 状态管理 | — | 复用 | — | 文档 8.3 明确归 Runtime |
| ask_user 中断/恢复 | 复用 | — | 薄封装 | Core `AskUserRail`/`BaseInterruptRail`/`ToolInterruptException` 已有完整机制；EDPAgent `AskUserTemplateRail` 只是加了话术模板渲染 |
| Checkpoint 持久化 | 复用 | — | 配置 | Core 已有 `InMemoryCheckpointer`/`RedisCheckpointer`；EDPAgent 只需配置 Redis |
| Task Loop 迭代保护 | 复用 | — | 配置 | Core `MaxRoundsEvaluator`/`TimeoutEvaluator`/`StopConditionEvaluator` 已有；EDPAgent 配置阈值 |
| ToolDataChannel 数据隔离 | — | — | 自建 | Core 无此概念，属 EDPAgent 业务数据通道 |

#### 阶段 5：工具 / Rail / Skill 生产化

| 组件 | Core | Runtime | EDPAgent | 说明 |
|---|:---:|:---:|:---:|---|
| `ask_user` 工具 | 复用 | — | 薄封装 | Core `AskUserTool` 已有；EDPAgent `EnhancedAskUserTool` 加了话术参数（`response_template_*`），属业务扩展 |
| `ask_user` Rail | 复用 | — | 薄封装 | Core `AskUserRail` 已有中断机制；EDPAgent `AskUserTemplateRail` 加了话术渲染和恢复逻辑 |
| `cancel_task` 工具 | — | — | 自建 | Core 无此业务工具 |
| `cancel_task` Rail | — | — | 自建 | Core 无此业务 Rail |
| `call_mcp` 工具 | — | — | 自建 | Core 的 `McpRail` 是读取 MCP **资源**（`ReadMcpResourceTool`），不是执行脚本；EDPAgent `call_mcp` 是声明沙箱脚本执行意图，语义不同 |
| `McpInterruptRail` | — | — | 自建 | Core 的 `McpRail` 管理的是 MCP Server 资源发现；EDPAgent `McpInterruptRail` 管理的是沙箱脚本执行、超时、结果解析 |
| `call_versatile` 工具 | — | — | 自建 | Core 无 VA 委托概念 |
| `VersatileInterruptRail` | — | — | 自建 | Core 无工作流委托能力 |
| `lite_todo_write` 工具 | 复用 | — | 薄封装 | Core 已有 `TodoTool`/`TodoItem`/`TodoStatus`；EDPAgent `LiteTodoWriteTool` 改为覆盖式写入 + `step_id` 枚举绑定，属业务定制 |
| `LiteTodoRail` | 复用 | — | 薄封装 | Core 已有 `TaskPlanningRail`/`TaskCompletionRail`；EDPAgent `LiteTodoRail` 加了 `step_id` 一致性校验和事件输出 |
| `ExecutionLimitRail` | 复用 | — | 配置 | Core `TaskLoopController` 已有迭代/调用次数限制；EDPAgent 配置阈值即可 |
| `LogRail` | 复用 | — | 薄封装 | Core 已有 `ModelUsageRecord`；EDPAgent 补充业务日志维度 |
| `MemoryRail` | 复用 | — | 配置 | Core 已有 `MemoryRail`/`ExternalMemoryRail`；EDPAgent 配置开关 |
| 系统工具（bash/read_file/execute_cmd） | 复用 | — | 配置 | Core 已有 `BashTool`/`FilesystemTool`；EDPAgent 按安全策略禁用或开放 |
| Skill 资产管理 | 复用 | — | 薄封装 | Core 已有 `SkillManager`/`SkillUseRail`；EDPAgent 补充版本/Schema/测试约束 |

#### 阶段 6：安全与合规生产化

| 任务 | Core | Runtime | EDPAgent | 说明 |
|---|:---:|:---:|:---:|---|
| 密钥保护 | — | — | 自建 | EDPAgent 配置消费 |
| 工具权限控制 | 复用 | — | 配置 | Core `PermissionEngine`/`SecurityRail`/`PermissionInterruptRail` 已有完整权限引擎（allow/ask/deny）；EDPAgent 配置策略 |
| 沙箱执行 | 复用 | — | 配置 | Core `SandboxOperation`/`SandboxRegistry` 已有；EDPAgent 配置白名单 |
| 数据脱敏 | — | — | 自建 | Core 无业务脱敏概念 |
| 审计日志 | — | — | 自建 | Core 无业务审计概念 |

#### 阶段 7：测试与质量门禁生产化

| 任务 | Core | Runtime | EDPAgent | 说明 |
|---|:---:|:---:|:---:|---|
| 单元测试（Tool/Rail/Config） | — | — | 自建 | EDPAgent 业务测试 |
| 集成测试（启动/注册/DeepAgent） | — | 复用 | 自建 | Runtime 提供注册验证框架；EDPAgent 验证业务注册与 DeepAgent 初始化 |
| E2E 测试（三条购买路径） | — | — | 自建 | EDPAgent 业务 E2E |
| 架构同步检查 | — | 复用 | — | Runtime 治理脚本 |

#### 阶段 8：部署、运维、发布生产化

| 任务 | Core | Runtime | EDPAgent | 说明 |
|---|:---:|:---:|:---:|---|
| 打包（jar/Docker/Helm） | — | — | 自建 | EDPAgent 制品 |
| 基础监控指标（HTTP/JVM） | — | 复用 | — | Runtime Actuator |
| 业务监控指标 | — | — | 自建 | EDPAgent 自定义指标 |
| 发布策略 | — | — | 自建 | EDPAgent 发布流程 |

### 17.6 三方能力分布汇总

| 能力归属 | 数量占比 | 典型代表 |
|---|---|---|
| Core 已具备，EDPAgent 复用/配置即可 | ~40% | DeepAgent 创建、ReAct 引擎、ask_user 中断机制、Skill 管理、Task Loop 控制、权限引擎、沙箱执行、Checkpointer、系统工具（Bash/FS）、流式输出、SubAgent 框架 |
| Runtime 已具备，EDPAgent 直接依赖 | ~10% | A2A HTTP 接入、流式传输、task 状态管理、Actuator 基础健康/监控、基础日志框架、架构同步门禁 |
| EDPAgent 薄封装（Core/Runtime 有基础 + EDPAgent 加业务定制） | ~20% | ask_user（加话术模板）、lite_todo（加 step_id 枚举）、LogRail（加业务维度）、健康检查（加业务依赖）、stream（加业务帧）、Skill（加版本约束） |
| EDPAgent 必须自建（Core/Runtime 均无对应能力） | ~30% | call_mcp（沙箱脚本执行）、call_versatile（VA 委托）、cancel_task、ToolDataChannel、话术模板管理、配置占位符解析、配置校验、数据脱敏、审计日志、业务监控指标、制品打包发布 |

### 17.7 关键判断

1. **Core 能力远超 EDPAgent 当前使用范围**：EDPAgent Spike 只用了 Core 的 `DeepAgent`/`ReActAgent`/`AgentRail`/`ToolInterruptException`/`SkillManager` 这 5 个核心 API，而 Core 还提供了 `PermissionEngine`、`TaskLoopController`、`Checkpointer`、`SandboxOperation`、`SubAgentRegistry` 等大量未被利用的能力。生产化时应优先盘点 Core 已有能力，避免重复造轮子。

2. **Runtime 定位明确但能力边界窄**：Runtime 仅提供 A2A 协议接入、task 状态管理、Spring Boot 基础设施三类能力，不涉及 Agent 执行内核和业务逻辑。EDPAgent 的配置安全、业务健康检查、业务超时熔断均无法依赖 Runtime，需自行实现。

3. **EDPAgent 最大自建工作量集中在三类业务特有能力**：
   - Versatile 委托链路（`call_versatile` + `VersatileInterruptRail`）——Core 完全没有工作流委托概念
   - MCP 脚本执行（`call_mcp` + `McpInterruptRail`）——Core 的 `McpRail` 是资源发现，不是脚本执行
   - 业务配置安全（占位符解析、配置校验、脱敏、审计）——Core/Runtime 均无业务层配置安全机制

4. **FEAT_EDPA 多子Agent并行可复用 Core 能力**：Core 已有 `SubAgentRegistry`/`SubagentRail`/`SubAgentConfig` + 多种 Agent Factory（`ResearchAgentFactory`/`CodeAgentFactory`/`BrowserAgentFactory`），多子Agent并行执行的基础设施已具备，EDPAgent 需做的是适配 EDPAgent 的编排策略和状态管理。

5. **生产化优先级建议调整**：原方案中 P1 的"新增 planner/"和 P3 的"脚本白名单"可降级——Core 的 `TaskLoopController` 和 `SandboxOperation` 已提供基础，EDPAgent 只需配置而非重新构建。省下的精力应投入到 Versatile 委托链路和配置安全这两块 Core/Runtime 完全空白的领域。
