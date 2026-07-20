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

## 1. 特性定位

FEAT-005 定义 `agent-runtime` 在部署或启动阶段代表 Agent 访问智能体中间件服务的当前版本事实。当前版本纳入范围的子特性是 **Skill Hub 代理**：runtime 读取部署态 runtime 配置和 Agent skill 选择配置，通过 Skill Hub SPI 下载 Agent 声明需要的 skill 包，并把可注册材料交给 `agent-core` 或框架适配入口。下载失败时，Agent 可以降级进入 ready，runtime 在请求链路外继续完成该 skill 的首次生效。

本特性解决的问题是：Agent 的 skill 资产由外部 Skill Hub 管理，Agent 部署时只声明需要哪些 skill；runtime 在启动阶段负责连接认证、访问控制、下载、校验、诊断和移交，避免 Agent 开发者在业务代码中直接耦合 Skill Hub 服务 API 或凭据处理。

本特性不是运行中动态 skill 调度或热刷新能力，也不是 `agent-core` 的 skill 解析、注册、执行或 prompt 组装规范。runtime 不在用户请求链路中下载 skill，不解释 skill 包内容，不把 skill instructions 直接注入运行时 prompt，不接管框架内部 tool/skill 选择和执行，也不建立独立于 Skill Hub 的 skill 授权模型。下载故障后完成尚未注册 skill 的首次生效不属于热刷新。

本特性面向以下角色：

- 现场交付人员：配置 Skill Hub 服务连接、认证凭据和 Agent 所需 skill。
- Agent 开发者：在 Agent 配置中声明需要的 skill 标识和 required/optional 语义。
- Runtime 开发者：实现部署/启动阶段的 Skill Hub 访问、下载、完整性校验、降级重试、失败诊断和注册材料移交。
- Skill Hub 适配开发者：通过 Skill Hub SPI 对接 openJiuwen Skill Hub 或客户自定义 Skill Hub。
- 测试与验收团队：验证启动阶段下载、SPI 替换、降级首次生效、完整性校验、失败策略和敏感信息保护。

## 2. 当前版本能力要求

| 能力 | 要求级别 | 事实要求 |
|---|---|---|
| 部署/启动阶段代理访问 | MUST | runtime 必须在 Agent 部署或启动阶段发起 Skill Hub 访问；下载失败后的重试在用户请求链路外执行。 |
| Skill Hub 服务配置归 runtime | MUST | Skill Hub endpoint、认证方式、连接凭据和连接策略由 runtime 部署配置持有；Agent 配置不得持有 Skill Hub 明文访问凭据。 |
| Agent skill 选择配置归 Agent | MUST | Agent 配置只声明需要的 skill 标识、版本或等价选择条件，以及 required/optional 语义。 |
| Skill Hub SPI | MUST | runtime 必须提供可替换的 Skill Hub 访问 SPI 或等价扩展点，使默认实现和客户自定义实现可以在不改变 Agent 业务代码的前提下替换。 |
| openJiuwen Skill Hub 默认实现 | MUST | 当前版本必须提供默认实现，对接 `openJiuwen/skillhub` 服务 API。 |
| skill 包下载 | MUST | runtime 必须根据 Agent skill 选择配置从 Skill Hub 获取并下载对应 skill 包或等价可注册材料。 |
| skill 包完整性校验 | MUST | runtime 必须在移交前校验下载材料。Skill Hub 支持摘要时，使用其通过可信通道提供的 SHA-256 或更安全摘要；不支持时，退化为文件非空、大小一致（如可获得）、文件或压缩包可完整读取、必要文件存在等常规校验。校验失败的材料不得移交、注册或使用。 |
| 注册材料移交 | MUST | runtime 必须把下载成功且通过完整性及基本格式校验的 skill 材料移交给 `agent-core` 或框架适配入口；注册、解析、执行和模型上下文处理归属 `agent-core` 或具体框架。 |
| required skill 失败策略 | MUST | required skill 的配置、认证、授权、查找或启动阶段移交失败时，Agent 不得进入 ready；下载或完整性校验失败按降级首次生效规则处理。 |
| 下载失败降级与首次生效 | MUST | 下载或完整性校验失败不得阻塞 Agent ready，但该 skill 不可用；runtime 在请求链路外重试，成功校验并注册后，该 skill 从下一轮新请求开始可用。 |
| optional skill 降级 | SHOULD | optional skill 获取失败时可以跳过并继续启动，但必须输出脱敏诊断；下载或完整性校验失败按降级首次生效规则处理。 |
| 凭据与敏感信息保护 | MUST | 密码、token、认证头、密钥、凭据密文和解密后的敏感值不得写入日志、错误响应、遥测数据或持久化明文配置。 |
| 错误诊断 | MUST | 连接认证失败、Skill Hub 拒绝访问、skill 不存在、下载失败、校验失败或移交失败时，必须提供明确且不泄露敏感信息的诊断。 |

## 3. 外部接口与入口要求

| 入口 | 类型 | 事实要求 |
|---|---|---|
| Skill Hub 服务连接配置 | runtime configuration | 必须表达 Skill Hub endpoint、认证方式、连接凭据引用或加密凭据，以及默认实现或自定义 SPI 实现选择。具体字段名由 L2 或配置指南定义。 |
| Agent skill 选择配置 | agent configuration | 必须表达 Agent 需要的 skill 标识、版本或等价选择条件，并能区分 required 与 optional。该配置不保存 Skill Hub 明文访问凭据。 |
| Skill Hub SPI | Java SPI / extension point | 必须允许替换 Skill Hub 访问实现。SPI 需要产出 runtime 可校验并可移交给 `agent-core` 或框架适配入口的 skill 包或注册材料。具体接口方法由 L2 设计约束。 |
| openJiuwen Skill Hub adapter | default adapter | 默认 adapter 必须通过 openJiuwen Skill Hub 服务 API 完成认证访问、skill 查询和 skill 包下载。 |
| 启动日志与诊断 | observability | 必须能帮助运维确认 Skill Hub adapter、连接目标摘要、Agent skill 获取结果和 required/optional 处理结果；不得输出敏感凭据或敏感 skill 内容。 |

配置示意只表达归属，不固定字段名：

```yaml
runtime:
  skill-hub:
    endpoint: https://skillhub.example.internal
    credential-ref: runtime-skillhub-credential

agent:
  skills:
    - id: document-search
      version: 1.2.0
      required: true
    - id: chart-render
      required: false
```

## 4. 场景与用户旅程

| 场景 | 前置条件 | 用户/系统动作 | 期望行为 |
|---|---|---|---|
| 启动时获取 required skill | runtime 已配置 Skill Hub 连接凭据，Agent 声明 required skill | runtime 启动 Agent | runtime 通过 Skill Hub SPI 认证访问 Skill Hub，下载 skill 包并移交注册；Agent ready。 |
| required skill 不存在 | Agent 声明的 required skill 在 Skill Hub 中不存在或无权访问 | runtime 启动 Agent | 启动失败或 readiness 不通过；诊断说明 skill 不可用但不泄露凭据、内部地址或敏感内容。 |
| required skill 下载失败与恢复 | Skill Hub 暂时不可达或下载中断 | runtime 启动 Agent，后续下载成功 | Agent 降级进入 ready；runtime 在请求链路外重试，成功校验并注册后，该 skill 从下一轮新请求开始可用。 |
| skill 包完整性校验 | runtime 下载 skill 包 | runtime 校验下载材料 | 有可信摘要时校验摘要；没有时执行非空、大小、可完整读取和必要文件等常规校验。校验失败时拒绝注册并继续重试。 |
| optional skill 下载失败 | Agent 声明 optional skill，Skill Hub 暂时不可用或下载失败 | runtime 启动 Agent | Agent 可继续 ready，该 skill 暂不可用；runtime 输出脱敏诊断并在请求链路外继续重试。 |
| 凭据缺失或无效 | runtime 未配置凭据、凭据无效、过期或 Skill Hub 返回 `401/403` | runtime 启动 Agent | required skill 场景 fail fast；optional skill 场景按降级规则处理；日志和错误不输出凭据。 |
| 替换 Skill Hub 实现 | 客户提供自定义 Skill Hub SPI 实现 | 部署方替换 adapter 并保持 Agent skill 选择配置不变 | runtime 通过自定义实现获取等价 skill 包或注册材料；Agent 业务代码不需要修改。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 配置归属语义

- Skill Hub 服务连接、认证方式和凭据由 runtime 部署配置持有。
- Agent 配置只声明需要哪些 skill，以及这些 skill 是否为启动必需。
- 在当前 agent 与 runtime 实例 1:1 的部署模型下，runtime 使用 runtime 级凭据访问 Skill Hub；Agent 不持有 Skill Hub 访问凭据。
- Skill Hub 根据 runtime 凭据判定可访问的 skill 范围；runtime 不在本地维护独立的 Agent-skill 授权规则。

#### 5.1.2 部署/启动阶段语义

- runtime 在部署或启动阶段发起 skill 获取，下载失败后的重试不进入用户请求链路。
- required skill 的配置、认证、授权、查找或启动阶段移交失败时，Agent 不应进入 ready；下载或完整性校验失败时，Agent 可以降级进入 ready。
- 成功下载、校验并注册后，skill 从下一轮新请求开始首次生效；首次生效后不自动刷新、替换或卸载。
- optional skill 获取失败不应被伪装为成功注册；调用方、运维和测试应能通过脱敏诊断确认其被跳过。
- 配置变更生效方式可以是重新部署或重启；当前版本不要求运行中热刷新。

#### 5.1.3 Skill Hub SPI 语义

- Skill Hub SPI 是访问 Skill Hub 的替换边界，不是 `agent-core` skill 执行 SPI。
- 默认实现对接 openJiuwen Skill Hub；客户或部署方可以通过自定义 SPI 实现对接其他 Skill Hub。
- 自定义 SPI 必须遵守同样的凭据保护、错误诊断、required/optional、完整性校验、下载降级首次生效和注册材料移交语义。
- version-scope 不固定 SPI 方法、配置字段、协议报文、分页、缓存、重试或落盘格式；这些细节由 L2 设计或实现约束。

#### 5.1.4 skill 包与注册材料语义

- runtime 必须支持从 Skill Hub 下载 skill 包或等价可注册材料。
- Skill Hub 支持摘要时，runtime 必须校验其通过可信通道提供的 SHA-256 或更安全摘要；不支持时，必须执行文件非空、大小一致（如可获得）、可完整读取和必要文件存在等常规校验，并记录采用的校验方式。
- 摘要或常规校验失败时，runtime 必须拒绝移交和注册，并按降级首次生效规则继续获取有效材料。
- runtime 不把 skill instructions 注入运行时 prompt；skill 描述、渐进加载、详细内容读取、工具调用和模型上下文处理由 `agent-core` 或具体 Agent 框架负责。
- 下载成功的 skill 只有在成功移交并被下游注册后，才能被视为 Agent 可用能力。

#### 5.1.5 错误、安全与可观测语义

| 场景 | 事实要求 |
|---|---|
| Skill Hub endpoint 配置缺失 | required skill 场景 fail fast；optional skill 场景可降级；诊断必须说明配置缺失。 |
| Skill Hub 暂时不可达或下载中断 | 不得注册对应 skill；Agent 可降级进入 ready，runtime 必须在请求链路外继续重试并输出连接或下载失败诊断。 |
| 凭据缺失、无效、过期或 `401/403` | 不得下载或注册对应 skill；required skill 场景 fail fast。 |
| skill 不存在或无权访问 | 不得注册对应 skill；required skill 场景 fail fast，optional skill 场景跳过。 |
| 下载不完整、包损坏或完整性校验失败 | 不得移交或注册对应 skill；Agent 可降级 ready，runtime 输出脱敏诊断并继续获取有效材料。 |
| 注册材料移交失败 | 启动阶段 required skill 移交失败时 fail fast；Agent 已降级 ready 时保持该 skill 不可用并继续诊断和重试。 |
| 日志与遥测 | 可输出 adapter 名称、endpoint 摘要、skill id、required/optional、失败分类和 correlation 信息；不得输出明文凭据、密文、认证头、密钥、内部敏感地址或敏感 skill 内容。 |

### 5.2 显式边界与不承诺项

| 边界 | 当前版本不承诺 |
|---|---|
| 请求级动态获取 | 不在用户 query 请求中访问 Skill Hub 获取 skill；下载重试由 runtime 在请求链路外执行。 |
| 运行中热刷新 | 下载故障后完成首次生效不属于热刷新；首次成功注册后不自动刷新、替换或卸载 skill。 |
| Agent 自主决策获取 | 不承诺 Agent 在推理过程中自主决定从 Skill Hub 获取新 skill。 |
| 独立 skill 授权模型 | 不在 runtime 中维护 Agent 与 skill 的独立授权规则；授权由 Skill Hub 根据 runtime 凭据判定。 |
| Skill Hub 服务端能力 | 不定义 Skill Hub 服务端的管理、审批、运营、存储、审计或发布流程。 |
| `agent-core` skill 语义 | 不定义 `agent-core` 的 skill 格式、解析、注册、执行、prompt 注入、渐进加载或模型上下文处理策略。 |
| 框架扩展机制治理 | 不接管具体框架的 hook、tool、skill、middleware、callback、memory 或 checkpoint 机制。 |
| 缓存与重试策略 | 不在 version-scope 固定下载缓存、重试、分页、断点续传或本地落盘策略。 |
| 其他中间件服务代理 | 记忆服务、知识服务等中间件代理不自动进入当前版本范围；需要后续子特性或独立特性声明。 |

## 6. 对下游设计与实现的约束

- L2 设计必须把 FEAT-005 设计成部署/启动阶段的中间件请求代理能力，不得把当前版本扩展为请求级动态 skill 获取。
- 实现必须清晰区分 runtime Skill Hub 服务配置和 Agent skill 选择配置；Agent 配置不得保存 Skill Hub 明文访问凭据。
- Skill Hub SPI 必须是可替换访问边界，默认 openJiuwen 实现和客户自定义实现都必须遵守同一 required/optional、完整性校验、降级首次生效、错误诊断和安全语义。
- required skill 的配置、认证、授权、查找或启动阶段移交失败必须阻断 Agent ready；下载或完整性校验失败不得阻断 ready，但该 skill 必须保持不可用并在请求链路外重试。
- 下载成功的 skill 必须通过摘要校验；Skill Hub 不支持摘要时，必须通过常规完整性校验。成功注册后从下一轮新请求开始首次生效，后续不自动刷新、替换或卸载。
- 实现不得把 skill instructions 直接注入运行时 prompt，不得绕过 `agent-core` 或框架适配入口解释 skill 内容。
- 测试必须覆盖 required 非下载类失败阻断 ready、下载失败降级与重试、下一轮请求首次生效、摘要及常规完整性校验、校验失败拒绝注册、首次生效后不热刷新和日志脱敏。
- 若未来要支持运行中动态刷新、请求级按租户/用户过滤、Agent 自主获取、记忆服务代理或知识服务代理，必须先更新 version-scope 事实范围，再进入 L2 和实现。

## 7. 关联文档

- `version-scope/README.md`
- `version-scope/FEAT-002-heterogeneous-agent-framework-compatibility.md`
- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L0-Top-Level-Design/glossary.md`
- `architecture/L1-High-Level-Design/agent-runtime/README.md`
- `architecture/L1-High-Level-Design/agent-runtime/development.md`
- `architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md`
