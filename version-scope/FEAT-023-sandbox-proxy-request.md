---
version: 0730
module: agent-runtime
feature_type: functional
feature_id: FEAT-023
status: active
related_docs:
  - ./README.md
  - ./FEAT-005-agent-middleware-request-proxy.md
  - ./FEAT-002-heterogeneous-agent-framework-compatibility.md
  - ../architecture/L0-Top-Level-Design/boundaries.md
  - ../architecture/L0-Top-Level-Design/glossary.md
  - ../architecture/L1-High-Level-Design/agent-runtime/README.md
  - ../architecture/L1-High-Level-Design/agent-runtime/development.md
  - ../architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md
---

# 【智能体中间件请求代理-运行调用态】新增支持代理请求 jiuwen box

> **术语：扩展点（Extension Point）**
>
> 扩展点是 runtime 定义的一个接口，允许客户在不修改 runtime 或 Agent 业务代码的前提下，提供自定义实现来替换默认行为。runtime 在启动时通过依赖注入或插件发现机制加载客户的实现；若客户未提供，则使用默认实现。
>
> **举例**：
>
> - **沙箱扩展点**：runtime 定义 `AgentCoreSandboxClientFactory` 接口，默认实现创建连接 jiuwenbox 沙箱的 `DecoratingSandboxClient`。客户实现该接口可接入 K8s Pod 或 Docker 沙箱后端，无需修改 Agent 代码。
> - **凭据解密扩展点**：runtime 定义 `CredentialDecryptor` 接口，默认实现为 passthrough（透明传递）。客户实现该接口可接入企业 KMS（如 HashiCorp Vault）解密沙箱服务地址。
>
> 不同语言的实现方式：Java 通过 Spring Bean 注入；Python 通过 `entry_points` 或 `pluggy` 插件框架；Go 通过接口+构造器注入。特性文档只约束接口语义，不固定发现机制。

## 1. 特性定位

FEAT-023 从 FEAT-005 拆分，聚焦 **沙箱代理运行调用态**：runtime 通过 `AgentCoreSandboxClientFactory` 扩展点创建 `DecoratingSandboxClient`，包装 `agent-core` 的原生 `SandboxClient`，为 Agent 的沙箱调用注入熔断、重试、超时和结构化错误码治理能力。Agent 通过 `ObjectProvider` 可选注入治理装饰客户端，不注入时降级为 `agent-core` 直接调用模式。

本特性解决的问题是：Agent 需要在隔离容器中执行脚本和技能，但不应直接耦合沙箱服务的连接管理、凭据解密、熔断重试等治理逻辑；runtime 代理这些能力，Agent 通过扩展点可选注入即可获得治理装饰。

本特性不接管 `agent-core` 的 `SandboxClient` 接口定义（`shell()`/`code()`/`fs()`），不定义沙箱容器创建和生命周期管理（归属 `agent-core`），只负责在 `agent-core` 原生沙箱客户端外层装饰治理能力。不定义 Agent 的沙箱使用场景（脚本执行、归一化、技能部署等）、Rail 拦截逻辑和降级策略；这些归属 Agent 业务层。runtime 仅提供治理装饰客户端，Agent 决定如何使用。

本特性面向以下角色：

- 现场交付人员：配置沙箱服务地址、治理策略和凭据解密。
- Agent 开发者：通过 `ObjectProvider` 可选注入沙箱治理装饰客户端。
- Runtime 开发者：实现沙箱治理装饰层（熔断/重试/超时/结构化错误码）。
- 沙箱适配开发者：通过实现 `AgentCoreSandboxClientFactory` 扩展点接入自定义沙箱后端。
- 测试与验收团队：验证沙箱熔断/重试/降级和结构化错误码。

## 2. 当前版本能力要求

| 能力            | 要求级别   | 本次版本变化 | 事实要求                                                                                                                          |
| :------------ | :----- | :----- | :---------------------------------------------------------------------------------------------------------------------------- |
| 沙箱治理装饰        | MUST   | 现有     | runtime 必须通过 `AgentCoreSandboxClientFactory` 扩展点创建 `DecoratingSandboxClient`，包装 `agent-core` 的原生 `SandboxClient`，为沙箱调用注入治理能力。 |
| 沙箱熔断          | MUST   | 现有     | runtime 必须为沙箱调用提供熔断器，连续失败达到阈值后打开熔断，经过恢复时间后进入半开状态；熔断打开时返回结构化错误码。                                                               |
| 沙箱重试          | SHOULD | 现有     | runtime 可为沙箱调用提供自动重试，重试次数和退避间隔由配置控制；重试中断时返回结构化错误码。                                                                            |
| 沙箱结构化错误码      | MUST   | 现有     | runtime 必须为沙箱治理层异常提供结构化错误码（至少包括：调用失败、熔断打开、超时、重试中断），使 Agent 能按错误码分类降级。                                                         |
| 沙箱降级到直接模式     | MUST   | 现有     | 当 `AgentCoreSandboxClientFactory` Bean 不存在或创建失败时，runtime 必须自动降级为 `agent-core` 直接调用模式（无治理装饰），不阻断 Agent 启动。                     |
| 沙箱凭据解密        | MUST   | 现有     | runtime 必须通过 `CredentialDecryptor` 扩展点对沙箱服务地址等凭据进行解密；默认实现为 passthrough（透明传递），客户可接入企业 KMS。                                     |
| 沙箱扩展点可替换      | MUST   | 现有     | runtime 必须提供 `AgentCoreSandboxClientFactory` 工厂扩展点，客户可通过实现该接口接入自定义沙箱后端，无需修改 Agent 业务代码。                                       |
| 沙箱配置归 runtime | MUST   | 现有     | 沙箱治理层配置（熔断阈值、重试次数、全局超时）由 runtime 部署配置持有；Agent 配置不持有沙箱治理层参数。                                                                   |
| 沙箱实例空闲回收    | MUST   | 新增     | runtime 必须支持通过 `idle-ttl-seconds` 配置沙箱实例的空闲存活时间，超过此时间的空闲沙箱实例将被自动回收。默认 300 秒。                                                              |
| 沙箱实例回收检查间隔 | SHOULD | 新增     | runtime 可通过 `check-interval` 配置沙箱实例空闲回收的检查间隔，控制回收扫描频率。默认 60 秒。                                                                              |
| 凭据与敏感信息保护     | MUST   | 现有     | 密码、token、认证头、密钥、凭据密文和解密后的敏感值不得写入日志、错误响应、遥测数据或持久化明文配置。                                  |
| 错误诊断          | MUST   | 现有     | 沙箱治理层异常通过结构化错误码提供诊断，诊断必须明确且不泄露敏感信息。 |

## 3. 外部接口与入口要求

| 入口        | 类型                    | 事实要求                                                                                                                                                                                                                                                                                                                                |
| :-------- | :-------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 沙箱治理层配置   | runtime configuration | 必须表达全局超时、熔断阈值/恢复时间、重试次数/退避间隔。配置前缀为 `openjiuwen.service.external.sandbox`。治理层不配置服务地址、沙箱类型、启动器类型等连接信息--这些归 Agent 业务层，通过工厂方法参数传入。                                                                                                                                                                                                      |
| 沙箱扩展点工厂   | extension point       | 必须提供 `AgentCoreSandboxClientFactory` 工厂接口，`factory.create(sandboxGatewayConfig)` 从调用方接收连接信息（service-url、sandbox-type、launcher-type、on-stop 等），返回 `DecoratingSandboxClient`（装饰 `agent-core` 的 `SandboxClient`）。默认实现为 `DefaultAgentCoreSandboxClientFactory`，由 Spring 条件装配在 `openjiuwen.service.external.sandbox.enabled=true` 时自动创建。 |
| 沙箱凭据解密扩展点 | extension point       | 必须提供 `CredentialDecryptor` 接口，对沙箱服务地址等凭据进行解密。默认实现为 passthrough（透明传递），客户可接入企业 KMS。                                                                                                                                                                                                                                                   |
| 沙箱结构化错误码  | observability         | 必须为沙箱治理层异常提供结构化错误码枚举（`SANDBOX_OUTBOUND_CALL_FAILED`/`SANDBOX_CIRCUIT_OPEN`/`SANDBOX_TIMEOUT`/`SANDBOX_RETRY_INTERRUPTED`），通过 `ExternalSvcAdapterException.getErrorCode()` 获取。                                                                                                                                                       |
| 沙箱启动日志    | observability         | 必须输出沙箱治理层状态（adapter 创建结果、熔断器配置、连接目标摘要），不输出明文凭据。                                                                                                                                                                                                                                                                                     |

配置示意只表达归属，不固定字段名：

```yaml
# 沙箱治理层配置（runtime 持有，治理参数 + server 级配置）
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
        servers:                              # server 级配置（治理层持有）
          - server-id: default                # 服务器标识，factory.create() 无参版本默认查找 "default"
            service-url: ${EDPA_SANDBOX_SERVICE_URL:http://127.0.0.1:8321}
            sandbox-type: jiuwenbox
            launcher-type: pre_deploy
            on-stop: delete
            root-path: .
            idle-ttl-seconds: 300             # 沙箱实例空闲存活秒数，超时后自动回收
            check-interval: 60                # 空闲回收检查间隔（秒），控制扫描频率
            extra-params:                     # filesystem_policy 等扩展参数
              policy:
                filesystem_policy:
                  read_write:
                    - /app/skills
              policy_mode: append
        # sandbox-type、launcher-type、on-stop 等连接信息也可由 Agent 业务层
        # 通过 factory.create(sandboxGatewayConfig) 传入

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
```

## 4. 场景与用户旅程

| 场景       | 前置条件                                                                                 | 用户/系统动作                           | 期望行为                                                                                                                                                   |
| :------- | :----------------------------------------------------------------------------------- | :-------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------- |
| 治理模式启动   | runtime 配置 `openjiuwen.service.external.sandbox.enabled=true`，沙箱服务可用                 | runtime 启动 Agent                  | Spring 条件装配创建 `DefaultAgentCoreSandboxClientFactory` Bean；`factory.create()` 返回 `DecoratingSandboxClient`（含熔断/重试）；Agent 通过 `ObjectProvider` 注入治理装饰客户端。 |
| 直接模式降级   | runtime 配置 `openjiuwen.service.external.sandbox.enabled=false`                       | runtime 启动 Agent                  | `AgentCoreSandboxClientFactory` Bean 不存在；`ObjectProvider` 返回 null；Agent 降级为 `agent-core` 直接调用 `SandboxClient`（无治理装饰）；不阻断启动。                            |
| 沙箱熔断降级   | 沙箱连续失败达到熔断阈值（默认 5 次）                                                                 | Agent 调用沙箱执行脚本                    | `DecoratingSandboxClient` 抛出 `ExternalSvcAdapterException(SANDBOX_CIRCUIT_OPEN)`；Agent 捕获后降级到本地 `ProcessBuilder` 执行（若 `fallback-on-failure=true`）。     |
| 沙箱超时     | 沙箱执行超过全局超时（默认 60s）                                                                   | Agent 调用沙箱执行脚本                    | `DecoratingSandboxClient` 抛出 `ExternalSvcAdapterException(SANDBOX_TIMEOUT)`；Agent 按错误码降级或报错。                                                           |
| 凭据解密     | 沙箱 service-url 为加密值，客户实现 `CredentialDecryptor`                                       | runtime 启动 Agent                  | `SandboxInitHook.decryptIfNeeded()` 调用 `CredentialDecryptor` 解密 service-url；解密后的值仅存于内存，不写入日志。                                                          |
| 自定义沙箱后端  | 客户实现 `AgentCoreSandboxClientFactory` 扩展点                                             | 部署方替换 factory Bean 并保持 Agent 配置不变 | runtime 通过自定义 factory 接收 Agent 业务层传入的 `sandboxGatewayConfig`，创建装饰客户端对接客户自定义沙箱后端（如 K8s Pod、Docker）；Agent 业务代码不需要修改。                                     |
| 沙箱工厂创建失败 | `AgentCoreSandboxClientFactory` Bean 存在但 `factory.create(sandboxGatewayConfig)` 抛出异常 | runtime 启动 Agent                  | runtime 捕获异常，`decoratedSandboxClient` 为 null；降级为 `agent-core` 直接调用模式；输出 WARN 日志但不阻断启动。                                                                 |
| 沙箱实例空闲回收   | 沙箱实例在 `idle-ttl-seconds`（默认 300s）内无任何调用活动                                       | runtime 后台回收任务运行              | runtime 自动回收空闲沙箱实例，释放容器资源；回收时输出 INFO 日志，不阻断正在进行的沙箱调用。 |
| 沙箱实例回收检查   | runtime 按 `check-interval`（默认 60s）间隔定期扫描空闲沙箱实例                                  | runtime 后台回收任务运行              | runtime 按 check-interval 间隔执行回收扫描，识别超过 idle-ttl-seconds 的空闲实例并回收。 |
| 沙箱实例活跃续期   | 沙箱实例在 idle-ttl-seconds 内有调用活动                                                        | Agent 调用沙箱执行脚本                | 沙箱实例的空闲计时器被重置，不被回收；活跃实例不会被误回收。 |
| 自定义空闲回收策略 | 部署方配置非默认的 `idle-ttl-seconds` 和 `check-interval` 值                                  | 部署方修改 runtime 配置并重启        | runtime 按自定义参数执行回收策略；Agent 业务代码不需要修改。 |

## 5. 行为语义与边界

### 5.1 核心行为语义

#### 5.1.1 配置归属语义

- 沙箱治理层配置（`openjiuwen.service.external.sandbox.*`）只持有治理参数（全局超时、熔断、重试）；服务地址、沙箱类型、启动器类型、on-stop 等连接和行为信息由 Agent 业务层配置（`edpa.agent.sandbox.*`）持有，通过 `factory.create(sandboxGatewayConfig)` 参数传入治理层。治理层与业务层配置不重复。

#### 5.1.2 沙箱实例空闲回收语义

- `idle-ttl-seconds` 定义沙箱实例的空闲存活时间。从最后一次调用活动开始计时，超过此时间无任何调用活动的实例将被回收。
- `check-interval` 定义回收扫描的执行间隔。runtime 后台按此间隔定期扫描所有沙箱实例，识别并回收超过 `idle-ttl-seconds` 的空闲实例。
- 活跃实例（在 idle-ttl-seconds 内有调用活动的实例）不会被回收，每次调用活动重置空闲计时器。
- 回收操作不阻断正在进行的沙箱调用；仅回收已空闲超时的实例。
- 回收时输出 INFO 日志，包含 server-id 和沙箱实例标识，不输出敏感凭据。
- `idle-ttl-seconds` 和 `check-interval` 配置在 runtime 治理层的 `servers[]` 中，由 runtime 持有；Agent 业务层不持有回收参数。

#### 5.1.3 错误、安全与可观测语义

| 场景                        | 事实要求                                                                                                                    |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| 沙箱熔断器打开                   | 返回 `SANDBOX_CIRCUIT_OPEN` 错误码；Agent 按错误码降级到本地执行或报错。                                                                     |
| 沙箱执行超时                    | 返回 `SANDBOX_TIMEOUT` 错误码；Agent 按错误码降级或报错。                                                                               |
| 沙箱调用失败                    | 返回 `SANDBOX_OUTBOUND_CALL_FAILED` 错误码；Agent 按错误码降级或报错。                                                                  |
| 沙箱重试中断                    | 返回 `SANDBOX_RETRY_INTERRUPTED` 错误码；Agent 按错误码降级或报错。                                                                     |
| 沙箱工厂创建失败                  | 降级为 `agent-core` 直接调用模式；输出 WARN 日志；不阻断 Agent 启动。                                                                        |
| 日志与遥测                     | 可输出 adapter 名称、endpoint 摘要、沙箱错误码和 correlation 信息；不得输出明文凭据、密文、认证头、密钥、内部敏感地址。 |

### 5.2 显式边界与不承诺项

| 边界                    | 当前版本不承诺                                                                                                          |
| --------------------- | ---------------------------------------------------------------------------------------------------------------- |
| 沙箱容器管理                | 不定义 `agent-core` 的沙箱容器创建、生命周期管理、资源调度和隔离策略；这些归属 `agent-core`。runtime 仅在 `agent-core` 原生 `SandboxClient` 外层装饰治理能力。 |
| 沙箱业务语义                | 不定义 Agent 的沙箱使用场景（脚本执行、归一化、技能部署等）、Rail 拦截逻辑和降级策略；这些归属 Agent 业务层。runtime 仅提供治理装饰客户端，Agent 决定如何使用。                 |

## 6. 对下游设计与实现的约束

- 沙箱治理层必须通过 `AgentCoreSandboxClientFactory` 扩展点创建 `DecoratingSandboxClient`，不得在 Agent 业务代码中硬编码沙箱治理逻辑（熔断/重试/超时）。
- 沙箱治理层配置（`openjiuwen.service.external.sandbox.*`）与 Agent 业务层配置（`edpa.agent.sandbox.*`）必须分层独立：治理层只配置治理参数（全局超时、熔断阈值、重试次数），不配置服务地址、沙箱类型等连接信息；Agent 配置持有连接和行为参数，不持有治理层参数。两层配置不重复。
- 沙箱 `AgentCoreSandboxClientFactory` 必须通过 `factory.create(sandboxGatewayConfig)` 从调用方接收连接信息，不得从治理层 YAML 读取服务地址等业务参数。
- 沙箱 `AgentCoreSandboxClientFactory` Bean 不存在或创建失败时必须自动降级为 `agent-core` 直接调用模式，不得阻断 Agent 启动。
- 沙箱治理层异常必须通过结构化错误码（`ExternalSvcAdapterException`）暴露，不得以原始异常透传到 Agent 业务层。
- 沙箱凭据解密必须通过 `CredentialDecryptor` 扩展点，不得在代码中硬编码解密逻辑或明文凭据。
- 测试必须覆盖沙箱治理模式启动、直接模式降级、熔断降级、超时降级、凭据解密、自定义扩展点替换和工厂创建失败降级。
- 测试必须覆盖沙箱实例空闲回收：配置 `idle-ttl-seconds` 后空闲实例被回收、活跃实例不被回收、`check-interval` 控制扫描频率、自定义回收参数生效。

## 7. 关联文档

- `version-scope/README.md`
- `version-scope/FEAT-005-agent-middleware-request-proxy.md`
- `version-scope/FEAT-002-heterogeneous-agent-framework-compatibility.md`
- `architecture/L0-Top-Level-Design/boundaries.md`
- `architecture/L0-Top-Level-Design/glossary.md`
- `architecture/L1-High-Level-Design/agent-runtime/README.md`
- `architecture/L1-High-Level-Design/agent-runtime/development.md`
- `architecture/L1-High-Level-Design/agent-runtime/spi-appendix.md`
