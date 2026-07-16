---
version: 0715
module: agent-runtime
feature_type: functional
feature_id: FEAT-005
status: active
related_docs:
  - ./README.md
  - ./FEAT-002-heterogeneous-agent-framework-compatibility.md
  - ../architecture/L0-Top-Level-Design/boundaries.md
  - ../architecture/L0-Top-Level-Design/glossary.md
  - ../architecture/L1-High-Level-Design/agent-runtime/README.md
  - ../architecture/L1-High-Level-Design/agent-runtime/development.md
  - ../architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md
---

# 智能体中间件请求代理

> **术语：扩展点（Extension Point）**
>
> 扩展点是 runtime 定义的一个接口，允许客户在不修改 runtime 或 Agent 业务代码的前提下，提供自定义实现来替换默认行为。runtime 在启动时通过依赖注入或插件发现机制加载客户的实现；若客户未提供，则使用默认实现。
>
> **举例**：
> - **沙箱扩展点**：runtime 定义 `AgentCoreSandboxClientFactory` 接口，默认实现创建连接 jiuwenbox 沙箱的 `DecoratingSandboxClient`。客户实现该接口可接入 K8s Pod 或 Docker 沙箱后端，无需修改 Agent 代码。
> - **凭据解密扩展点**：runtime 定义 `CredentialDecryptor` 接口，默认实现为 passthrough（透明传递）。客户实现该接口可接入企业 KMS（如 HashiCorp Vault）解密沙箱服务地址。
> - **Skill Hub 扩展点**：runtime 定义 Skill Hub 访问接口，默认实现对接 openJiuwen Skill Hub。客户实现该接口可对接自定义 Skill Hub 服务。
>
> 不同语言的实现方式：Java 通过 Spring Bean 注入；Python 通过 `entry_points` 或 `pluggy` 插件框架；Go 通过接口+构造器注入。特性文档只约束接口语义，不固定发现机制。

## 1. 特性定位

FEAT-005 定义 `agent-runtime` 在部署或启动阶段代表 Agent 访问智能体中间件服务的当前版本事实。当前版本纳入范围的子特性包括：

- **沙箱代理**（现有）：runtime 通过 `AgentCoreSandboxClientFactory` 扩展点创建 `DecoratingSandboxClient`，包装 `agent-core` 的原生 `SandboxClient`，为 Agent 的沙箱调用注入熔断、重试、超时和结构化错误码治理能力。Agent 通过 `ObjectProvider` 可选注入治理装饰客户端，不注入时降级为 `agent-core` 直接调用模式。
- **Skill Hub 代理**（新增）：runtime 读取部署态 runtime 配置和 Agent skill 选择配置，通过 Skill Hub 扩展点访问 Skill Hub，下载 Agent 声明需要的 skill 包，并把可注册的 skill 材料交给 `agent-core` 或框架适配入口完成注册和使用。

本特性解决的问题是：

- **沙箱代理**：Agent 需要在隔离容器中执行脚本和技能，但不应直接耦合沙箱服务的连接管理、凭据解密、熔断重试等治理逻辑；runtime 代理这些能力，Agent 通过扩展点可选注入即可获得治理装饰。
- **Skill Hub 代理**：Agent 的 skill 资产由外部 Skill Hub 管理，Agent 部署时只声明需要哪些 skill；runtime 需要在 Agent 启动前完成连接认证、访问控制、下载、诊断和移交，避免 Agent 开发者在业务代码中直接耦合 Skill Hub 服务 API 或凭据处理。

本特性不是运行中动态 skill 调度能力，也不是 `agent-core` 的 skill 解析、注册、执行或 prompt 组装规范。runtime 不解释 skill 包内容，不把 skill instructions 直接注入运行时 prompt，不接管框架内部 tool/skill 选择和执行，也不建立独立于 Skill Hub 的 skill 授权模型。沙箱代理不接管 `agent-core` 的 `SandboxClient` 接口定义（`shell()`/`code()`/`fs()`），不定义沙箱容器创建和生命周期管理（归属 `agent-core`），只负责在 `agent-core` 原生沙箱客户端外层装饰治理能力。

本特性面向以下角色：

- 现场交付人员：配置 Skill Hub 服务连接、认证凭据和 Agent 所需 skill；配置沙箱服务地址、治理策略和凭据解密。
- Agent 开发者：在 Agent 配置中声明需要的 skill 标识和 required/optional 语义；通过 `ObjectProvider` 可选注入沙箱治理装饰客户端。
- Runtime 开发者：实现部署/启动阶段的 Skill Hub 访问、下载、失败诊断和注册材料移交；实现沙箱治理装饰层（熔断/重试/超时/结构化错误码）。
- Skill Hub 适配开发者：通过 Skill Hub 扩展点对接 openJiuwen Skill Hub 或客户自定义 Skill Hub。
- 沙箱适配开发者：通过实现 `AgentCoreSandboxClientFactory` 扩展点接入自定义沙箱后端。
- 测试与验收团队：验证启动阶段下载、扩展点替换、失败策略和敏感信息保护；验证沙箱熔断/重试/降级和结构化错误码。

## 2. 当前版本能力要求

### 2.1 沙箱代理

| 能力 | 要求级别 | 本次版本变化 | 事实要求 |
|---|---|---|---|
| 沙箱治理装饰 | MUST | 现有 | runtime 必须通过 `AgentCoreSandboxClientFactory` 扩展点创建 `DecoratingSandboxClient`，包装 `agent-core` 的原生 `SandboxClient`，为沙箱调用注入治理能力。 |
| 沙箱熔断 | MUST | 现有 | runtime 必须为沙箱调用提供熔断器，连续失败达到阈值后打开熔断，经过恢复时间后进入半开状态；熔断打开时返回结构化错误码。 |
| 沙箱重试 | SHOULD | 现有 | runtime 可为沙箱调用提供自动重试，重试次数和退避间隔由配置控制；重试中断时返回结构化错误码。 |
| 沙箱结构化错误码 | MUST | 现有 | runtime 必须为沙箱治理层异常提供结构化错误码（至少包括：调用失败、熔断打开、超时、重试中断），使 Agent 能按错误码分类降级。 |
| 沙箱降级到直接模式 | MUST | 现有 | 当 `AgentCoreSandboxClientFactory` Bean 不存在或创建失败时，runtime 必须自动降级为 `agent-core` 直接调用模式（无治理装饰），不阻断 Agent 启动。 |
| 沙箱凭据解密 | MUST | 现有 | runtime 必须通过 `CredentialDecryptor` 扩展点对沙箱服务地址等凭据进行解密；默认实现为 passthrough（透明传递），客户可接入企业 KMS。 |
| 沙箱扩展点可替换 | MUST | 现有 | runtime 必须提供 `AgentCoreSandboxClientFactory` 工厂扩展点，客户可通过实现该接口接入自定义沙箱后端，无需修改 Agent 业务代码。 |
| 沙箱配置归 runtime | MUST | 现有 | 沙箱治理层配置（熔断阈值、重试次数、全局超时）由 runtime 部署配置持有；Agent 配置不持有沙箱治理层参数。 |

### 2.2 Skill Hub 代理

| 能力 | 要求级别 | 本次版本变化 | 事实要求 |
|---|---|---|---|
| 部署/启动阶段代理访问 | MUST | 新增 | runtime 必须在 Agent 部署或启动阶段读取配置并访问 Skill Hub，当前版本不在用户 query 请求过程中动态拉取 skill。 |
| Skill Hub 服务配置归 runtime | MUST | 新增 | Skill Hub endpoint、认证方式、连接凭据和连接策略由 runtime 部署配置持有；Agent 配置不得持有 Skill Hub 明文访问凭据。 |
| Agent skill 选择配置归 Agent | MUST | 新增 | Agent 配置只声明需要的 skill 标识、版本或等价选择条件，以及 required/optional 语义。 |
| Skill Hub 扩展点 | MUST | 新增 | runtime 必须提供可替换的 Skill Hub 访问扩展点，使默认实现和客户自定义实现可以在不改变 Agent 业务代码的前提下替换。 |
| openJiuwen Skill Hub 默认实现 | MUST | 新增 | 当前版本必须提供默认实现，对接 `openJiuwen/skillhub` 服务 API。 |
| skill 包下载 | MUST | 新增 | runtime 必须根据 Agent skill 选择配置从 Skill Hub 获取并下载对应 skill 包或等价可注册材料。 |
| 注册材料移交 | MUST | 新增 | runtime 必须把下载成功且通过基本校验的 skill 材料移交给 `agent-core` 或框架适配入口；注册、解析、执行和模型上下文处理归属 `agent-core` 或具体框架。 |
| required skill fail fast | MUST | 新增 | required skill 的认证、授权、查找、下载、校验或移交失败时，Agent 不得进入 ready；部署/启动必须失败或 readiness 不通过。 |
| optional skill 降级 | SHOULD | 新增 | optional skill 获取失败时可以跳过该 skill 并继续启动，但必须输出脱敏、可诊断的降级信息；被跳过的 skill 不得注册为可用。 |

### 2.3 通用

| 能力 | 要求级别 | 本次版本变化 | 事实要求 |
|---|---|---|---|
| 凭据与敏感信息保护 | MUST | 现有 | 密码、token、认证头、密钥、凭据密文和解密后的敏感值不得写入日志、错误响应、遥测数据或持久化明文配置。 |
| 错误诊断 | MUST | 现有 | 连接认证失败、Skill Hub 拒绝访问、skill 不存在、下载失败、校验失败或移交失败时，必须提供明确且不泄露敏感信息的诊断。沙箱治理层异常通过结构化错误码提供诊断。 |

## 3. 外部接口与入口要求

### 3.1 沙箱代理

| 入口 | 类型 | 事实要求 |
|---|---|---|
| 沙箱治理层配置 | runtime configuration | 必须表达全局超时、熔断阈值/恢复时间、重试次数/退避间隔。配置前缀为 `openjiuwen.service.external.sandbox`。治理层不配置服务地址、沙箱类型、启动器类型等连接信息——这些归 Agent 业务层，通过工厂方法参数传入。 |
| 沙箱扩展点工厂 | extension point | 必须提供 `AgentCoreSandboxClientFactory` 工厂接口，`factory.create(sandboxGatewayConfig)` 从调用方接收连接信息（service-url、sandbox-type、launcher-type、on-stop 等），返回 `DecoratingSandboxClient`（装饰 `agent-core` 的 `SandboxClient`）。默认实现为 `DefaultAgentCoreSandboxClientFactory`，由 Spring 条件装配在 `openjiuwen.service.external.sandbox.enabled=true` 时自动创建。 |
| 沙箱凭据解密扩展点 | extension point | 必须提供 `CredentialDecryptor` 接口，对沙箱服务地址等凭据进行解密。默认实现为 passthrough（透明传递），客户可接入企业 KMS。 |
| 沙箱结构化错误码 | observability | 必须为沙箱治理层异常提供结构化错误码枚举（`SANDBOX_OUTBOUND_CALL_FAILED`/`SANDBOX_CIRCUIT_OPEN`/`SANDBOX_TIMEOUT`/`SANDBOX_RETRY_INTERRUPTED`），通过 `ExternalSvcAdapterException.getErrorCode()` 获取。 |
| 沙箱启动日志 | observability | 必须输出沙箱治理层状态（adapter 创建结果、熔断器配置、连接目标摘要），不输出明文凭据。 |

### 3.2 Skill Hub 代理

| 入口 | 类型 | 事实要求 |
|---|---|---|
| Skill Hub 服务连接配置 | runtime configuration | 必须表达 Skill Hub endpoint、认证方式、连接凭据引用或加密凭据，以及默认实现或自定义扩展点实现选择。具体字段名由 L2 或配置指南定义。 |
| Agent skill 选择配置 | agent configuration | 必须表达 Agent 需要的 skill 标识、版本或等价选择条件，并能区分 required 与 optional。该配置不保存 Skill Hub 明文访问凭据。 |
| Skill Hub 扩展点 | extension point | 必须允许替换 Skill Hub 访问实现。扩展点需要产出 runtime 可校验并可移交给 `agent-core` 或框架适配入口的 skill 包或注册材料。具体接口方法由 L2 设计约束。 |
| openJiuwen Skill Hub adapter | default adapter | 默认 adapter 必须通过 openJiuwen Skill Hub 服务 API 完成认证访问、skill 查询和 skill 包下载。 |
| 启动日志与诊断 | observability | 必须能帮助运维确认 Skill Hub adapter、连接目标摘要、Agent skill 获取结果和 required/optional 处理结果；不得输出敏感凭据或敏感 skill 内容。 |

配置示意只表达归属，不固定字段名：

```yaml
# 沙箱治理层配置（runtime 持有，仅治理参数）
openjiuwen:
  service:
    external:
      sandbox:
        enabled: true                        # 触发 AgentCoreSandboxClientFactory Bean 创建
        timeout-ms: 60000                    # 全局超时（毫秒）
        retry:
          max: 2                              # 重试次数
          backoff-ms: 1000                    # 退避间隔
        circuit-breaker:
          enabled: true                       # 熔断器开关
          failure-threshold: 5                # 连续失败阈值
          reset-timeout-ms: 30000             # 熔断恢复时间
        # 不配置 servers[] —— 服务地址、沙箱类型、启动器类型、on-stop 等连接信息
        # 由 Agent 业务层通过 factory.create(sandboxGatewayConfig) 传入

# 沙箱业务层配置（Agent 持有，连接和行为参数）
agent:
  sandbox:
    enabled: true
    service-url: ${SANDBOX_SERVICE_URL}        # 唯一连接信息来源
    sandbox-type: jiuwenbox
    launcher-type: pre_deploy
    on-stop: delete
    skill-deploy-path: /app/skills
    fallback-on-failure: true                 # 沙箱不可用时降级到本地
    container-scope: SESSION                  # 容器隔离级别

# Skill Hub 配置（runtime 持有）
runtime:
  skill-hub:
    endpoint: https://skillhub.example.internal
    credential-ref: runtime-skillhub-credential

# Skill 选择配置（Agent 持有）
agent:
  skills:
    - id: document-search
      version: 1.2.0
      required: true
    - id: chart-render
      required: false
```

## 4. 场景与用户旅程

### 4.1 沙箱代理场景

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 治理模式启动 | runtime 配置 `openjiuwen.service.external.sandbox.enabled=true`，沙箱服务可用 | runtime 启动 Agent | Spring 条件装配创建 `DefaultAgentCoreSandboxClientFactory` Bean；`factory.create()` 返回 `DecoratingSandboxClient`（含熔断/重试）；Agent 通过 `ObjectProvider` 注入治理装饰客户端。 |
| 直接模式降级 | runtime 配置 `openjiuwen.service.external.sandbox.enabled=false` | runtime 启动 Agent | `AgentCoreSandboxClientFactory` Bean 不存在；`ObjectProvider` 返回 null；Agent 降级为 `agent-core` 直接调用 `SandboxClient`（无治理装饰）；不阻断启动。 |
| 沙箱熔断降级 | 沙箱连续失败达到熔断阈值（默认 5 次） | Agent 调用沙箱执行脚本 | `DecoratingSandboxClient` 抛出 `ExternalSvcAdapterException(SANDBOX_CIRCUIT_OPEN)`；Agent 捕获后降级到本地 `ProcessBuilder` 执行（若 `fallback-on-failure=true`）。 |
| 沙箱超时 | 沙箱执行超过全局超时（默认 60s） | Agent 调用沙箱执行脚本 | `DecoratingSandboxClient` 抛出 `ExternalSvcAdapterException(SANDBOX_TIMEOUT)`；Agent 按错误码降级或报错。 |
| 凭据解密 | 沙箱 service-url 为加密值，客户实现 `CredentialDecryptor` | runtime 启动 Agent | `SandboxInitHook.decryptIfNeeded()` 调用 `CredentialDecryptor` 解密 service-url；解密后的值仅存于内存，不写入日志。 |
| 自定义沙箱后端 | 客户实现 `AgentCoreSandboxClientFactory` 扩展点 | 部署方替换 factory Bean 并保持 Agent 配置不变 | runtime 通过自定义 factory 接收 Agent 业务层传入的 `sandboxGatewayConfig`，创建装饰客户端对接客户自定义沙箱后端（如 K8s Pod、Docker）；Agent 业务代码不需要修改。 |
| 沙箱工厂创建失败 | `AgentCoreSandboxClientFactory` Bean 存在但 `factory.create(sandboxGatewayConfig)` 抛出异常 | runtime 启动 Agent | runtime 捕获异常，`decoratedSandboxClient` 为 null；降级为 `agent-core` 直接调用模式；输出 WARN 日志但不阻断启动。 |

### 4.2 Skill Hub 代理场景

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 启动时获取 required skill | runtime 已配置 Skill Hub 连接凭据，Agent 声明 required skill | runtime 启动 Agent | runtime 通过 Skill Hub 扩展点认证访问 Skill Hub，下载 skill 包并移交注册；Agent ready。 |
| required skill 不存在 | Agent 声明的 required skill 在 Skill Hub 中不存在或无权访问 | runtime 启动 Agent | 启动失败或 readiness 不通过；诊断说明 skill 不可用但不泄露凭据、内部地址或敏感内容。 |
| optional skill 下载失败 | Agent 声明 optional skill，Skill Hub 暂时不可用或下载失败 | runtime 启动 Agent | runtime 跳过该 optional skill，输出脱敏降级诊断；Agent 可继续 ready，但该 skill 不注册为可用。 |
| 凭据缺失或无效 | runtime 未配置凭据、凭据无效、过期或 Skill Hub 返回 `401/403` | runtime 启动 Agent | required skill 场景 fail fast；optional skill 场景按降级规则处理；日志和错误不输出凭据。 |
| 替换 Skill Hub 实现 | 客户提供自定义 Skill Hub 扩展点实现 | 部署方替换 adapter 并保持 Agent skill 选择配置不变 | runtime 通过自定义实现获取等价 skill 包或注册材料；Agent 业务代码不需要修改。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 配置归属语义

- Skill Hub 服务连接、认证方式和凭据由 runtime 部署配置持有。
- Agent 配置只声明需要哪些 skill，以及这些 skill 是否为启动必需。
- 在当前 agent 与 runtime 实例 1:1 的部署模型下，runtime 使用 runtime 级凭据访问 Skill Hub；Agent 不持有 Skill Hub 访问凭据。
- Skill Hub 根据 runtime 凭据判定可访问的 skill 范围；runtime 不在本地维护独立的 Agent-skill 授权规则。
- 沙箱治理层配置（`openjiuwen.service.external.sandbox.*`）只持有治理参数（全局超时、熔断、重试）；服务地址、沙箱类型、启动器类型、on-stop 等连接和行为信息由 Agent 业务层配置（`edpa.agent.sandbox.*`）持有，通过 `factory.create(sandboxGatewayConfig)` 参数传入治理层。治理层与业务层配置不重复。

#### 5.1.2 部署/启动阶段语义

- 当前版本只承诺在 Agent 部署或启动阶段获取 skill。
- required skill 获取完成前，Agent 不应进入 ready。
- optional skill 获取失败不应被伪装为成功注册；调用方、运维和测试应能通过脱敏诊断确认其被跳过。
- 配置变更生效方式可以是重新部署或重启；当前版本不要求运行中热刷新。

#### 5.1.3 Skill Hub 扩展点语义

- Skill Hub 扩展点是访问 Skill Hub 的替换边界，不是 `agent-core` skill 执行扩展点。
- 默认实现对接 openJiuwen Skill Hub；客户或部署方可以通过自定义扩展点实现对接其他 Skill Hub。
- 自定义扩展点实现必须遵守同样的凭据保护、错误诊断、required/optional 和注册材料移交语义。
- version-scope 不固定扩展点方法、配置字段、协议报文、分页、缓存、重试或落盘格式；这些细节由 L2 设计或实现约束。

#### 5.1.4 skill 包与注册材料语义

- runtime 必须支持从 Skill Hub 下载 skill 包或等价可注册材料。
- runtime 可以执行基本来源、完整性、格式或可移交性校验，但不解释 skill 的业务语义。
- runtime 不把 skill instructions 注入运行时 prompt；skill 描述、渐进加载、详细内容读取、工具调用和模型上下文处理由 `agent-core` 或具体 Agent 框架负责。
- 下载成功的 skill 只有在成功移交并被下游注册后，才能被视为 Agent 可用能力。

#### 5.1.5 错误、安全与可观测语义

| 场景 | 事实要求 |
|---|---|
| Skill Hub endpoint 缺失或不可达 | required skill 场景 fail fast；optional skill 场景可降级；诊断必须说明连接不可用。 |
| 凭据缺失、无效、过期或 `401/403` | 不得下载或注册对应 skill；required skill 场景 fail fast。 |
| skill 不存在或无权访问 | 不得注册对应 skill；required skill 场景 fail fast，optional skill 场景跳过。 |
| 下载中断、包损坏或校验失败 | 不得移交或注册对应 skill；按 required/optional 规则处理。 |
| 注册材料移交失败 | 不得把 skill 暴露为可用；required skill 场景 fail fast。 |
| 沙箱熔断器打开 | 返回 `SANDBOX_CIRCUIT_OPEN` 错误码；Agent 按错误码降级到本地执行或报错。 |
| 沙箱执行超时 | 返回 `SANDBOX_TIMEOUT` 错误码；Agent 按错误码降级或报错。 |
| 沙箱调用失败 | 返回 `SANDBOX_OUTBOUND_CALL_FAILED` 错误码；Agent 按错误码降级或报错。 |
| 沙箱重试中断 | 返回 `SANDBOX_RETRY_INTERRUPTED` 错误码；Agent 按错误码降级或报错。 |
| 沙箱工厂创建失败 | 降级为 `agent-core` 直接调用模式；输出 WARN 日志；不阻断 Agent 启动。 |
| 日志与遥测 | 可输出 adapter 名称、endpoint 摘要、skill id、required/optional、失败分类、沙箱错误码和 correlation 信息；不得输出明文凭据、密文、认证头、密钥、内部敏感地址或敏感 skill 内容。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 请求级动态获取 | 不在每次用户 query 请求时按用户、租户或上下文动态访问 Skill Hub 获取 skill。 |
| 运行中热刷新 | 不承诺运行中自动刷新、热替换、卸载或按策略切换 skill。 |
| Agent 自主决策获取 | 不承诺 Agent 在推理过程中自主决定从 Skill Hub 获取新 skill。 |
| 独立 skill 授权模型 | 不在 runtime 中维护 Agent 与 skill 的独立授权规则；授权由 Skill Hub 根据 runtime 凭据判定。 |
| Skill Hub 服务端能力 | 不定义 Skill Hub 服务端的管理、审批、运营、存储、审计或发布流程。 |
| `agent-core` skill 语义 | 不定义 `agent-core` 的 skill 格式、解析、注册、执行、prompt 注入、渐进加载或模型上下文处理策略。 |
| 框架扩展机制治理 | 不接管具体框架的 hook、tool、skill、middleware、callback、memory 或 checkpoint 机制。 |
| 缓存与重试策略 | 不在 version-scope 固定下载缓存、重试、分页、断点续传或本地落盘策略。 |
| 其他中间件服务代理 | 记忆服务、知识服务等中间件代理不自动进入当前版本范围；需要后续子特性或独立特性声明。沙箱代理已纳入当前版本。 |
| 沙箱容器管理 | 不定义 `agent-core` 的沙箱容器创建、生命周期管理、资源调度和隔离策略；这些归属 `agent-core`。runtime 仅在 `agent-core` 原生 `SandboxClient` 外层装饰治理能力。 |
| 沙箱业务语义 | 不定义 Agent 的沙箱使用场景（脚本执行、归一化、技能部署等）、Rail 拦截逻辑和降级策略；这些归属 Agent 业务层。runtime 仅提供治理装饰客户端，Agent 决定如何使用。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把 FEAT-005 设计成部署/启动阶段的中间件请求代理能力，不得把当前版本扩展为请求级动态 skill 获取。
- 实现必须清晰区分 runtime Skill Hub 服务配置和 Agent skill 选择配置；Agent 配置不得保存 Skill Hub 明文访问凭据。
- Skill Hub 扩展点必须是可替换访问边界，默认 openJiuwen 实现和客户自定义实现都必须遵守同一 required/optional、错误诊断和安全语义。
- required skill 失败必须阻断 Agent ready；optional skill 失败必须可诊断且不得注册为可用。
- 实现不得把 skill instructions 直接注入运行时 prompt，不得绕过 `agent-core` 或框架适配入口解释 skill 内容。
- 测试必须覆盖默认 openJiuwen Skill Hub 对接、自定义扩展点替换、凭据缺失/无效、`401/403`、skill 不存在、下载失败、校验失败、required fail fast、optional 降级和日志脱敏。
- 沙箱治理层必须通过 `AgentCoreSandboxClientFactory` 扩展点创建 `DecoratingSandboxClient`，不得在 Agent 业务代码中硬编码沙箱治理逻辑（熔断/重试/超时）。
- 沙箱治理层配置（`openjiuwen.service.external.sandbox.*`）与 Agent 业务层配置（`edpa.agent.sandbox.*`）必须分层独立：治理层只配置治理参数（全局超时、熔断阈值、重试次数），不配置服务地址、沙箱类型等连接信息；Agent 配置持有连接和行为参数，不持有治理层参数。两层配置不重复。
- 沙箱 `AgentCoreSandboxClientFactory` 必须通过 `factory.create(sandboxGatewayConfig)` 从调用方接收连接信息，不得从治理层 YAML 读取服务地址等业务参数。
- 沙箱 `AgentCoreSandboxClientFactory` Bean 不存在或创建失败时必须自动降级为 `agent-core` 直接调用模式，不得阻断 Agent 启动。
- 沙箱治理层异常必须通过结构化错误码（`ExternalSvcAdapterException`）暴露，不得以原始异常透传到 Agent 业务层。
- 沙箱凭据解密必须通过 `CredentialDecryptor` 扩展点，不得在代码中硬编码解密逻辑或明文凭据。
- 测试必须覆盖沙箱治理模式启动、直接模式降级、熔断降级、超时降级、凭据解密、自定义扩展点替换和工厂创建失败降级。
- 若未来要支持运行中动态刷新、请求级按租户/用户过滤、Agent 自主获取、记忆服务代理或知识服务代理，必须先更新 version-scope 事实范围，再进入 L2 和实现。

## 7. 关联文档

- `version-scope/README.md`
- `version-scope/FEAT-002-heterogeneous-agent-framework-compatibility.md`
- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L0-Top-Level-Design/glossary.md`
- `architecture/L1-High-Level-Design/agent-runtime/README.md`
- `architecture/L1-High-Level-Design/agent-runtime/development.md`
- `architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md`
