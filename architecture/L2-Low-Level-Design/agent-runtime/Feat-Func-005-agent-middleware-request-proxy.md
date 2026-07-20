| level | L2-LLD |
|---|---|
| module | agent-service-adapters-agentcore-ext |
| feature_type | functional |
| feature_id | Feat-Func-005 |
| status | draft |
| dependency | https://github.com/chaosxingxc-orion/spring-ai-ascend/pull/415 https://gitcode.com/openJiuwen/agent-core-java (BaseAgent / DeepAgent / SkillManager) https://gitcode.com/openJiuwen/agent-runtime-java (MiddlewareProperties / JiuwenCoreAgentHandler) |

## SkillHub 中间件适配设计文档

> 目标模块：`agent-runtime-ext-java/agent-service-adapters-agentcore-ext/src/main/java/com/openjiuwen/service/adapters/agentcore/ext/`
> 需求来源：FEAT-005 智能体中间件请求代理特性文档（spring-ai-ascend PR #415，version 0715）
> 最后更新：2026-07-15

---

## 1. 概述

### 1.1 特性定位

FEAT-005 定义 `agent-runtime` 在部署或启动阶段代表 Agent 访问智能体中间件服务的当前版本事实，当前版本纳入范围的子特性是 **Skill Hub 代理**：runtime 读取部署态 middleware 配置，通过 Skill Hub SPI 访问 Skill Hub，下载 Agent 声明需要的 skill 包，校验完整性后把可注册的 skill 材料交给 `agent-core` 的 `BaseAgent.registerSkill()` 完成注册和使用。

本特性是 **runtime middleware 的一种**，配置模型、凭据处理、自动装配、诊断风格均对齐 runtime 已有 middleware（如 redis checkpointer）。实现归属 `agent-runtime-ext-java/agent-service-adapters-agentcore-ext`（ext 模块），通过扩展 `JiuwenCoreAgentExtHandler`（继承 runtime 的 `JiuwenCoreAgentHandler`）hook 到 agent 启动生命周期，不修改 runtime 仓库代码。

SkillHub 提供渐进式 skill 发现与加载能力：先列出轻量摘要，再按需加载完整定义和打包载荷，最终通过 `BaseAgent.registerSkill()` 安装到 agent 实例。

- **解决的问题**：Agent 的 skill 资产由外部 Skill Hub 管理，Agent 部署时只声明需要哪些 skill；runtime 需要在 Agent 启动前完成连接认证、访问控制、下载、完整性校验、诊断和移交，避免 Agent 开发者在业务代码中直接耦合 Skill Hub 服务 API 或凭据处理。
- **适用场景**：业务自建 `@Bean AgentHandler` 传入 `BaseAgent` 实例（如 `ReActAgent`）的场景。Agent 在 `start()` 阶段从 SkillHub 加载 skill 并注册到自身。
- **客户价值**：Agent 能力可以随部署环境、租户和业务场景进行配置化选择，技能目录可以由客户或平台统一治理。
- **非目标**：不是运行中动态 skill 调度能力；不是 `agent-core` 的 skill 解析、注册、执行或 prompt 组装规范；runtime 不解释 skill 包内容，不把 skill instructions 直接注入运行时 prompt，不接管框架内部 tool/skill 选择和执行，也不建立独立于 Skill Hub 的 skill 授权模型。

### 1.2 核心设计原则

1. **启动期下载 + 请求期注册** — `Handler.start()` 阶段调用 `SkillHubManager.download()` 触发首次下载+校验；`Handler.query()`/`streamQuery()` 阶段调用 `SkillHubManager.register(agent)` 注册已下载校验通过的 skill
2. **稳定部署态** — SkillHub SPI 入参是部署态稳定的配置（endpoint、加密凭据、localDir），不依赖每次请求的 user/session/task，也不要求调用方传入 skillId（调用方在下载前拿不到 skillId）
3. **渐进式加载** — 摘要阶段不加载 instructions 或 skill 包；完整定义只在安装前按需加载
4. **Agent 自治** — `registerSkill` 已将 skill description 注入 prompt，适配层不重复注入 instructions，遵循 skill 懒加载设计原则
5. **三层分离** — `SkillHubProvider`（SPI：start/download/verify/stop）负责数据源无关的 skill 访问；`SkillHubManager`（管理器）编排下载/校验/注册三阶段 + 后台重试 + 维护已安装/未安装列表；`SkillHubInstaller`（接口）负责注册到 agent 实例（框架相关）
6. **配置归属分离** — Skill Hub 服务连接（endpoint、认证方式、加密凭据、localDir）由 runtime middleware 配置持有；Agent 配置不持有 Skill Hub 访问凭据
7. **分层失败语义**（PR #415）— required skill 的配置/认证/查找/移交失败 → fail fast 阻断 Agent ready；required skill 的下载或完整性校验失败 → 降级 ready，skill 不可用；optional skill 任何失败 → 降级跳过。被跳过或未校验通过的 skill 不得注册为可用
8. **完整性校验 MUST**（PR #415）— runtime 必须在移交前校验下载材料；校验方法由 Provider 实现自决（SHA-256/常规/自定义均可），文档不强制约束校验算法；校验失败的材料不得注册
9. **下载失败后台重试**（PR #415）— 下载/校验失败时 Agent 降级为 ready，skill 不可用；`SkillHubManager` 启动后台线程定时重试 download，成功后校验并加入"未安装列表"；后台线程只负责 download + verify + 维护"未安装列表"，不触碰 SkillManager（注册严格在请求线程执行）
10. **凭据与敏感信息保护** — token、认证头、密钥不得写入日志、错误响应、遥测数据；加密凭据经 `CredentialDecryptor` 解密后使用，明文不落盘、不进日志
11. **可选装配** — 仅当容器中存在 `SkillHubProvider` Bean 且配置启用时才激活 SkillHub 链路，不影响未使用 skill 的服务
12. **Skill 与 MCP 解耦** — SkillHub 只负责 skill 摘要、说明、依赖和包加载，不负责执行 MCP tool 或其他工具调用
13. **middleware 风格一致** — 配置 POJO + 静态嵌套类 + getter/setter，凭据 `encryptedToken` + `CredentialDecryptor`（对齐 runtime `MiddlewareProperties.RedisEndpoint.encryptedPassword` + `RedisMiddlewareAutoConfiguration` 解密模式），自动装配 `@ConditionalOnProperty` + `@ConditionalOnMissingBean` + `ObjectProvider`

### 1.3 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| Skill 数据模型 | 本地 skill 列表项（skillId + localPath） | `LocalSkillEntry` | ✅ |
| SkillHub middleware 配置 | runtime 持有 endpoint、认证方式、加密凭据 | `SkillHubMiddlewareProperties` | ✅ |
| SkillHub SPI | start/download/verify/stop 四方法（`SkillHubProvider`） | `SkillHubProvider` | ✅ |
| SkillHub 管理器 | 编排下载/校验/注册三阶段；后台重试；维护已安装/未安装列表 | `SkillHubManager` | ✅ |
| Agent 适配安装器 | 将 skill 路径注册到 BaseAgent 实例；执行分层失败语义 | `SkillHubInstaller` | ✅ |
| 完整性校验 | 校验由 Provider 实现自决（不强制算法），校验失败不返回成功结果 | `SkillHubProvider` 实现内部 | ✅ |
| 后台重试 | 下载失败时由 SkillHubManager 启动后台线程定时重试，成功后校验并加入"未安装列表" | `SkillHubManager` 内部后台线程 | ✅ |
| 错误诊断与分类 | 连接/认证/不存在/下载/校验/移交失败分类诊断 | `SkillHubErrorCategory` | ✅ |
| 自动装配 | 条件注册 SkillHub 链路 Bean | `SkillHubMiddlewareAutoConfiguration` | ✅ |
| openJiuwen 默认实现 | 对接 `openJiuwen/skillhub` 服务 API 的默认 Provider | `OpenJiuwenSkillHubProvider` | ✅ |
| Agent skill 选择配置驱动 | Agent 声明 `skills:[{id,version,required}]` 驱动获取 | — | ⬜ 第一期不做，未安装列表包含全部下载校验通过的 skill |

---

## 2. 特性规格

### 2.1 能力清单

> FEAT-005 §2 能力要求映射（PR #415）：MUST = ✅；SHOULD = ✅（默认实现，可降级）；不承诺 = ⬜。

| 能力 | 状态 | 说明 |
|------|------|------|
| 部署/启动阶段代理访问 | ✅ MUST | runtime 在 Agent 部署或启动阶段读取配置并访问 Skill Hub，不在用户 query 请求过程中动态拉取 skill |
| Skill Hub 服务配置归 runtime | ✅ MUST | endpoint、认证方式、加密凭据和连接策略由 `SkillHubMiddlewareProperties` 持有；Agent 配置不持有 Skill Hub 明文访问凭据 |
| Skill Hub SPI | ✅ MUST | `SkillHubProvider` 可替换访问边界，默认实现和自定义实现可在不改变 Agent 业务代码的前提下替换 |
| openJiuwen Skill Hub 默认实现 | ✅ MUST | `OpenJiuwenSkillHubProvider` 对接 `openJiuwen/skillhub` 服务 API（`/plugins`、`/plugins/{id}/versions/{ver}`、`/artifacts/{id}`） |
| skill 包下载 | ✅ MUST | `SkillHubProvider.download(properties, decryptor)` 下载应下载的全部 skill 到 properties.getLocalDir() 本地目录，校验由 `SkillHubProvider.verify(skillPath)` 逐条实现，返回 boolean |
| 完整性校验 MUST | ✅ MUST | runtime 必须在移交前校验下载材料；校验方法由 Provider 实现自决（SHA-256/常规/自定义均可）；校验失败的材料不得注册 |
| 注册材料移交 | ✅ MUST | 下载且通过完整性校验的 skill 材料移交给 `agent-core` 的 `BaseAgent.registerSkill(path)`；注册、解析、执行归属 `agent-core` |
| required 非下载失败 fail fast | ✅ MUST | required skill 的配置/认证/查找/移交失败时抛异常，阻断 `Runner.start()`，Agent 不 ready |
| required 下载/校验失败降级 | ✅ MUST | required skill 的下载或完整性校验失败时 Agent 降级为 ready，skill 不可用；SkillHubManager 启动后台线程定时重试下载，成功后校验并加入"未安装列表" |
| optional skill 降级 | ✅ SHOULD | optional skill 失败时跳过该 skill 并继续启动，输出脱敏降级诊断；被跳过的 skill 不得注册为可用 |
| 凭据与敏感信息保护 | ✅ MUST | token 不写入日志、错误响应、遥测数据；加密凭据经 `CredentialDecryptor` 解密后使用（详见 §4.11） |
| 错误诊断 | ✅ MUST | 连接/认证/拒绝访问/不存在/下载/校验/移交失败时通过 `SkillHubErrorCategory` 输出明确且不泄露敏感信息的诊断 |
| Skill 已安装/未安装列表 | ✅ | `SkillHubManager` 维护"未安装列表"（download + verify 后校验通过的路径）和"已安装列表"（已注册到 agent 的路径）；`Handler.query()`/`streamQuery()` 阶段调 `register(agent)`，判断"未安装列表"是否为空决定是否委托 `SkillHubInstaller.install(agent, paths)` |
| 安装日志与诊断 | ✅ | 日志输出 tenantId、agentId、installed 数量、skipped 数量、是否降级（具体 skillId、failureCategory 由 Provider 内部日志输出） |
| 无 Provider 降级 | ✅ | 未配置 SkillHubProvider 时 Agent 正常启动和执行 |
| DeepAgent skill 安装 | ✅ | 保留 `install(DeepAgent)` 适配代码，取 inner ReActAgent 安装；当前项目无 DeepAgent 路径但兼容后演进 |
| 渐进式加载 | ✅ | 摘要阶段不加载 instructions；完整定义只在安装前按需加载 |
| 完整 instructions 不注入 prompt | ✅ | `registerSkill` 已注入 description，不重复注入 instructions |
| 首次有效注册后不热刷新 | ✅ | 下载成功后启动期注册一次，同一 skill 不再重复注册或热替换 |
| 请求级动态 skill 过滤 | ⬜ | 当前版本不要求基于每次请求的 user/session/task 动态变更 skill 集合 |
| agent-id 场景 skill 安装 | ⬜ | adapter 层无法获取 agent 实例（详见 §9.2） |
| Agent skill 选择配置驱动 | ⬜ | 第一期不做 Agent 声明 `skills:[{id,version,required}]` 驱动；`SkillHubManager` 的"未安装列表"包含全部下载校验通过的 skill，按其 `required` 字段决定 fail fast / 降级行为 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| 请求级 skill 安装 | `registerSkill` 是累积注册，每次请求重复安装会导致重复；FEAT-005 明确不在用户 query 请求过程中动态拉取 skill | 启动期 `start()` 首次安装 |
| `injectRuntimeSkillSection` prompt 注入 | FEAT-005 §5.1.4 明确：runtime 不把 skill instructions 注入运行时 prompt；`registerSkill` 已注入 description | 依赖 Agent 自身的 skill prompt 机制 |
| 请求级上下文（user/session/task） | FEAT-005 §5.2 明确不承诺请求级动态获取 | 使用稳定部署上下文（agentId、tenantId） |
| agent-id 字符串场景 | adapter 层无法获取 agent 实例，且注册的 agent 可能不是 `BaseAgent` | 仅支持 `BaseAgent` 实例场景（详见 §9.2） |
| Agent skill 选择配置驱动（第一期） | 第一期保持 `SkillHubManager` 的"未安装列表"包含全部下载校验通过的 skill 的模型；Agent 声明 `skills:[{id,version,required}]` 驱动推迟到后续版本 | `LocalSkillEntry` 只含 skillId + localPath，required 由 Provider 实现按部署态决定 |
| 首次有效注册后热刷新 | FEAT-005 §5.2 明确不承诺运行中自动刷新、热替换、卸载或按策略切换 skill | 启动期注册一次，之后不再重复注册 |
| Agent 自主决策获取 | FEAT-005 §5.2 不承诺 Agent 在推理过程中自主决定从 Skill Hub 获取新 skill | 启动期一次性安装 |
| 独立 skill 授权模型 | FEAT-005 §5.1.1 明确：授权由 Skill Hub 根据 runtime 凭据判定，runtime 不维护独立 Agent-skill 授权规则 | 通过 `SkillHubMiddlewareProperties` 的 endpoint/encryptedToken 让 Skill Hub 侧判定 |
| Skill Hub 服务端能力 | FEAT-005 §5.2 不定义 Skill Hub 服务端的管理、审批、运营、存储、审计或发布流程 | 通过 Provider SPI 对接外部 Skill Hub |
| `agent-core` skill 语义 | FEAT-005 §5.2 不定义 `agent-core` 的 skill 格式、解析、注册、执行、prompt 注入、渐进加载或模型上下文处理策略 | 由 `agent-core` 框架自身处理 |
| MCP tool 执行 | SkillHub 不负责调用 MCP tool | 工具调用由 MCP Provider 或框架 tool 机制处理 |
| 缓存与重试策略固定 | FEAT-005 §5.2 不在 version-scope 固定下载缓存、分页、断点续传或本地落盘策略 | 由 Provider 实现自行治理 |

### 2.3 接口契约

#### SkillHub SPI

> 模块/包归属：`agent-service-spec-ext` 项目，包 `com.openjiuwen.service.spec.ext.skillhub.spi`（对齐 runtime `agent-service-spec.spec.spi.AgentHandler`，纯接口无实现依赖，no Spring）。

SkillHub SPI 只声明一个聚合接口 `SkillHubProvider`，业务方整体替换 Skill Hub 访问：

```java
/**
 * SkillHub Provider：Skill Hub 访问边界。
 * 声明 start / download / verify / stop 四个方法。
 * start/stop 由 SkillHubManager 构造/关闭时调用；download/verify 由 SkillHubManager 编排调用。
 */
public interface SkillHubProvider {

    /**
     * 启动 Provider（建立连接池、预热认证等）。
     * 由 SkillHubManager 构造方法中调用。
     *
     * @param properties  SkillHub 连接配置（endpoint、authType、encryptedToken、localDir 等）
     * @param decryptor   凭据解密器（实现内部调用 decryptor.decrypt(properties.getEncryptedToken()) 解密 token）
     */
    void start(SkillHubMiddlewareProperties properties, CredentialDecryptor decryptor);

    /**
     * 下载应下载的全部 skill 到 properties.getLocalDir() 本地目录。
     * Provider 内部自行决定要下载哪些 skill（如从 Skill Hub 拉取该租户/配置下应下载的 skill 清单），
     * 不由调用方传入 skillId——调用方在下载前根本拿不到 skillId。
     * localDir 也不由调用方传入——从 properties.getLocalDir() 获取。
     * 本方法只负责下载，不负责校验（校验由 verify 方法处理）。
     *
     * @param properties  SkillHub 连接配置（含 localDir）
     * @param decryptor   凭据解密器
     * @return true 表示下载全部成功；false 表示部分或全部失败（具体哪些失败由日志记录）
     */
    boolean download(SkillHubMiddlewareProperties properties, CredentialDecryptor decryptor);

    /**
     * 校验指定 skill 本地路径的完整性。
     * 入参为要校验的单个 skill 本地路径。
     * 校验方法由实现自决（文档不约束具体算法，SHA-256 / 常规文件检查 / 自定义均可）。
     * 校验失败的路径不得返回成功结果（抛异常或返回 false，由 Manager 据此排除）。
     *
     * @param skillPath   待校验的 skill 本地路径
     * @return true 表示校验通过；false 表示校验失败（失败项由 Manager 从注册列表中排除）
     */
    boolean verify(Path skillPath);

    /**
     * 停止 Provider（关闭连接池、释放资源）。
     * 由 SkillHubManager 关闭/Handler stop 时调用。
     */
    void stop();
}
```

**本地 skill 条目 `LocalSkillEntry`**（record，归属 `spec.ext.skillhub.dto`）：
```java
/** 本地 skill 列表项，只含 skillId 和本地路径。 */
public record LocalSkillEntry(
    String skillId,
    Path localPath
) {}
```

**校验职责说明**：skill 下载后如何校验是具体 `SkillHubProvider` 实现的事情，本设计文档不强制约束校验算法（SHA-256 / 常规文件检查 / 自定义均可）。唯一要求：校验失败的材料不得注册为可用。

**SPI 异常约定**：Provider 实现应将连接失败、认证失败（401/403）、skill 不存在（404）、下载失败、校验失败等分类为 `IllegalStateException` 抛出（复用 JDK 异常，不新建异常类），分类信息通过 message 前缀 `SkillHub[CATEGORY]` 携带，`CATEGORY` 取自 `SkillHubErrorCategory`（详见 §4.10）。未分类的其他异常由 SkillHubManager 兜底为 `UNKNOWN`。

#### SkillHubManager 管理器

> 模块/包归属：`agent-service-adapters-agentcore-ext`，包 `com.openjiuwen.service.adapters.agentcore.ext.middleware.skillhub`（实现类，非 SPI）。

`SkillHubManager` 是 skill 下载/校验/注册的编排者，构造入参为 `SkillHubProvider` 实现 bean，对内管理生命周期与后台重试，对外提供下载与注册入口。

```java
/**
 * SkillHub 管理器：编排 skill 的下载、校验、注册三个阶段。
 * 构造入参为 SkillHubProvider 实现 bean + SkillHubInstaller 实现 bean。
 *
 * 生命周期：
 *   构造方法 → provider.start() + 触发首次下载
 *     首次下载成功 → verify 校验下载的 skill → 校验通过项加入"未安装列表"
 *     首次下载失败 → 启动后台线程定时重试 download，成功后 verify + 加入"未安装列表"
 *   Handler.query/streamQuery → 调 register(agent) → 判断"未安装列表"是否为空
 *     非空 → 从"未安装列表"取路径，委托 SkillHubInstaller.install(agent, paths) → 安装后移入"已安装列表"
 *     为空 → 直接返回（请求照常处理）
 *   重新注册 → reregister(agent) → 清空"已安装列表"，把所有 skill 重新注册一遍
 *   Manager.stop() / Handler.stop → provider.stop() + 停后台线程
 *
 * 线程安全约束：
 *   - 后台线程只负责 download + verify + 维护"未安装列表"，不触碰 agent-core 的 SkillManager
 *   - 注册严格在 query/streamQuery 请求线程执行，规避 SkillManager 非线程安全
 *
 * 两个列表语义：
 *   - "未安装列表"：download + verify 通过但尚未注册到 agent 的 skill 路径
 *   - "已安装列表"：已注册到 agent 的 skill 路径
 *   - register(agent) 时判断"未安装列表"是否为空来决定是否安装
 */
public class SkillHubManager {

    /** 构造方法：入参为 SkillHubProvider 实现 bean + SkillHubInstaller 实现 bean + properties + decryptor。内部调用 provider.start() 并触发首次下载。 */
    public SkillHubManager(SkillHubProvider provider,
                           SkillHubInstaller installer,
                           SkillHubMiddlewareProperties properties,
                           CredentialDecryptor decryptor) { ... }

    /**
     * 触发下载（同步，用于 Handler.start() 阶段）。
     * 下载成功 → verify 校验 → 校验通过项加入"未安装列表"
     * 下载失败 → 启动后台线程定时重试，成功后校验并加入"未安装列表"
     */
    public void download() { ... }

    /**
     * 注册 skill 到 agent 实例（用于 Handler.query/streamQuery 阶段）。
     * 判断"未安装列表"是否为空：
     *   非空 → 从"未安装列表"取路径，委托 SkillHubInstaller.install(agent, paths) → 安装后移入"已安装列表"
     *   为空 → 直接返回（请求照常处理，skill 已全部安装或尚无 skill）
     */
    public void register(Object agent) { ... }

    /**
     * 重新注册：把所有 skill 重新注册一遍。
     * 清空"已安装列表"，把"未安装列表" + "已安装列表"中的全部 skill 路径重新委托 SkillHubInstaller.install(agent, paths) 注册。
     * 用于 skill 更新或需要重新加载的场景。
     */
    public void reregister(Object agent) { ... }

    /** 停止：停后台线程 + provider.stop()。用于 Handler.stop() 或应用关闭。 */
    public void stop() { ... }
}
```

#### SkillHubInstaller 安装器

> 模块/包归属：`agent-service-adapters-agentcore-ext`，包 `com.openjiuwen.service.adapters.agentcore.ext.middleware.skillhub`（接口，非 SPI）。

`SkillHubInstaller` 是将 skill 路径注册到 agent 实例的接口，由 `SkillHubManager.register()`/`reregister()` 委托调用：

```java
/**
 * 将 skill 路径注册到 agent 实例的接口。
 *
 * 内部逻辑：
 *   agent instanceof DeepAgent → innerAgent = deepAgent.getAgent()
 *   agent instanceof BaseAgent → target = baseAgent
 *   其他 → warn + skip
 *   对每个路径调用 target.registerSkill(path)
 *
 * 分层失败语义（PR #415）：
 *   - required skill 移交失败（registerSkill 后 skillCount 未增长）→ 抛异常
 *   - optional skill 移交失败 → 内部捕获并记录降级诊断
 */
public interface SkillHubInstaller {

    /**
     * 将指定 skill 路径列表注册到 agent 实例。
     *
     * @param agent       目标 agent（DeepAgent / BaseAgent）
     * @param skillPaths  待注册的 skill 本地路径列表（由 SkillHubManager 的"未安装列表"提供）
     */
    void install(Object agent, List<Path> skillPaths);

    /** 无操作 installer。 */
    static SkillHubInstaller noop() { return (agent, skillPaths) -> {}; }
}
```

#### 行为承诺

- **必须**：`SkillHubManager` 构造方法中调用 `provider.start()` 并触发首次 `download()`
- **必须**：`download()` 成功后逐条调用 `provider.verify(skillPath)`，校验通过的路径加入"未安装列表"；校验失败的路径不加入
- **必须**：`download()` 失败时启动后台线程定时重试 `provider.download()`，成功后执行 verify + 加入"未安装列表"
- **必须**：后台线程只负责 download + verify + 维护"未安装列表"，不触碰 agent-core 的 SkillManager
- **必须**：`register(agent)` 判断"未安装列表"是否为空：非空时取路径委托 `SkillHubInstaller.install(agent, paths)`，安装后移入"已安装列表"；为空时直接返回
- **必须**：`Handler.start()` 阶段调用 `SkillHubManager.download()`；`Handler.query()`/`streamQuery()` 阶段调用 `SkillHubManager.register(agent)`
- **必须**：`reregister(agent)` 把所有 skill（已安装 + 未安装）重新注册一遍，清空"已安装列表"后重新注册
- **必须**：`SkillHubInstaller.install(agent, paths)` 仅在 `agent instanceof DeepAgent` 或 `agent instanceof BaseAgent` 时执行注册
- **必须**：下载材料完整性校验由 `SkillHubProvider` 实现自决（不强制算法），校验失败由 `verify` 返回 false 或抛 `IllegalStateException`（message 前缀 `SkillHub[CHECKSUM_MISMATCH]`），Manager 据此将失败项从"未安装列表"排除
- **必须**：日志输出 tenantId、agentId、installed 数量、skipped 数量、是否降级（具体 skillId、failureCategory 由 Provider 内部日志输出）
- **必须**：required skill 的移交失败（`registerSkill` 后 skillCount 未增长）时 `install` 抛异常
- **必须**：optional skill 失败时跳过并输出脱敏降级诊断，该 skill 不得被注册为可用
- **必须**：下载/校验失败的 skill 不得注册为可用
- **必须**：日志、错误响应、遥测数据不得输出 API key、token、认证头、内部敏感地址或 skill 包中的敏感内容
- **必须**：未装配 SkillHubProvider 时不影响 Agent 启动和请求执行
- **必须**：`OpenJiuwenSkillHubProvider` 作为默认实现，对接 `openJiuwen/skillhub` 服务 API；业务方可通过自定义 `SkillHubProvider` Bean 覆盖
- **禁止**：后台线程直接调用 `registerSkill` 或触碰 SkillManager（注册严格在请求线程执行）
- **禁止**：向 prompt builder 注入 skill instructions（由 Agent 自身 skill 机制处理）
- **禁止**：在摘要阶段加载大量 instructions 或 skill 包
- **禁止**：将凭据明文写入持久化配置、日志或错误响应
- **禁止**：将未通过完整性校验的材料注册为可用
- **允许**：agent 非 `BaseAgent`/`DeepAgent` 时跳过注册并输出 warn 日志
- **允许**：Provider 自行缓存摘要、定义或远端响应

---

## 3. 模块结构

### 3.1 模块分布

FEAT-005 横跨两个 ext 项目，对齐 runtime 的 `agent-service-spec`（纯契约）↔ `agent-service-adapters`（实现）分层：

```
agent-solution/common/
├── agent-service-spec-ext/                         # 新增项目：纯契约（SPI + DTO），扩展 runtime agent-service-spec
│   └── src/main/java/com/openjiuwen/service/spec/ext/
│       └── skillhub/
│           ├── spi/
│           │   └── SkillHubProvider.java            # SPI 接口：start / download / verify / stop（便于整体替换）
│           ├── dto/
│           │   └── LocalSkillEntry.java           # record（本地 skill 列表项：skillId + localPath）
│           ├── SkillHubErrorCategory.java         # 错误分类枚举
│           └── package-info.java
│
└── agent-runtime-ext-java/                        # 已有项目：实现层
    └── agent-service-adapters/agent-service-adapters-agentcore-ext/
        └── src/main/java/com/openjiuwen/service/adapters/agentcore/ext/
            ├── agentfw/                           # 已有：JiuwenCoreAgentExtHandler
            ├── external/                          # 已有：RemoteA2aToolInstaller / RemoteA2aInterruptRail
            ├── autoconfigure/                     # 已有：AgentCoreExtAutoConfiguration
            └── middleware/                        # 新增：实现层（对齐 runtime agent-service-adapters 分层）
                └── skillhub/                      # skillhub 特性子包（对齐 runtime middleware/redis）
                    ├── SkillHubMiddlewareProperties.java  # POJO + 静态嵌套类（对齐 MiddlewareProperties 风格）
                    ├── SkillHubMiddlewareAutoConfiguration.java  # 自动装配（对齐 RedisMiddlewareAutoConfiguration）
                    ├── SkillHubInstaller.java     # 安装器接口（对齐 RemoteA2aToolInstaller 模式）
                    ├── SkillHubManager.java       # 管理器：编排 download/verify/register + 后台重试 + 维护已安装/未安装列表
                    ├── openjiuwen/               # 默认实现子包（对齐 redis/ 内聚）
                    │   └── OpenJiuwenSkillHubProvider.java  # 默认实现：对接 openJiuwen/skillhub API
                    └── package-info.java
```

**模块坐标与依赖**：

| 模块 | groupId:artifactId | version | 依赖 | 对齐 runtime 模块 |
|---|---|---|---|---|
| `agent-service-spec-ext` | `com.openjiuwen:agent-service-spec-ext` | `0.1.0` | `com.openjiuwen:agent-service-spec:0.1.0`（纯契约，no Spring） | `agent-service-spec` |
| `agent-service-adapters-agentcore-ext` | `com.openjiuwen:agent-service-adapters-agentcore-ext` | `0.1.0` | `agent-service-spec-ext:0.1.0` + `agent-service-adapters-agentcore:0.1.0` + `agent-core-java:0.1.13` | `agent-service-adapters-agentcore` |

依赖方向（单向，无环）：
```
agent-service-spec-ext  →  agent-service-spec (runtime)
        ▲
        │ 依赖契约
        │
agent-service-adapters-agentcore-ext  →  agent-service-adapters-agentcore (runtime)
                                      →  agent-core-java
```

**分层说明**（对齐 runtime 模块分布）：

runtime 的模块分层为 `agent-service-spec`（SPI 接口 + DTO，纯契约无实现，no Spring）↔ `agent-service-adapters`（实现层）。FEAT-005 用两个 ext 项目对齐该分层，而非在单一 jar 内用包结构模拟：

| 项目 | 对齐 runtime 模块 | 职责 | Spring 依赖 |
|---|---|---|---|
| `agent-service-spec-ext`（`com.openjiuwen.service.spec.ext.skillhub`） | `agent-service-spec` | `SkillHubProvider` SPI 接口 + `LocalSkillEntry` DTO + `SkillHubErrorCategory`（纯契约） | 无（no Spring，仅 jackson-annotations + lombok） |
| `agent-service-adapters-agentcore-ext`（`...ext.middleware.skillhub`） | `agent-service-adapters-agentcore` | `Installer`/`RetryExecutor`/`Verifier`/`Properties`/`AutoConfiguration`/`OpenJiuwenProvider`（实现） | 有（Spring Boot autoconfigure） |

**为什么新建独立 spec-ext 项目而非放进 agentcore-ext 包内**：
1. **契约纯净性**：`agent-service-spec-ext` 像 runtime `agent-service-spec` 一样是 no Spring 纯契约模块，业务方自定义 `SkillHubProvider` 实现时只需依赖 `agent-service-spec-ext`（轻量），不引入 Spring autoconfigure 和 runtime adapters 的重依赖
2. **对等定位**：runtime 把 spec 独立成 artifact，ext 同样把 spec-ext 独立成 artifact，分层定位完全对等
3. **复用性**：未来其他 ext 特性（不止 skillhub）的 SPI/DTO 也可放入 `agent-service-spec-ext` 的不同子包（`spec.ext.<feature>.spi` / `spec.ext.<feature>.dto`），形成统一的 ext 契约层

**为什么不能把 SPI/DTO 直接放进 runtime 的 `agent-service-spec` 模块**：ext 以 jar 依赖引入 runtime，不能修改 runtime 的 spec 模块。故新建 `agent-service-spec-ext` 作为 runtime spec 的扩展契约层。

**配置类说明**：`agent-service-adapters-agentcore-ext` 以 jar 依赖引入 runtime（`agent-runtime-java` 0.1.0）和 agent-core（0.1.13），不能修改 runtime 的 `MiddlewareProperties`。`SkillHubMiddlewareProperties` 在 agentcore-ext 独立定义，但风格（POJO + 静态嵌套类 + getter/setter + `encryptedToken`）和前缀（`openjiuwen.service.middleware.skillhub`）对齐 runtime middleware 约定。runtime 已提供 `CredentialDecryptor` 模块（`agent-service-adapters-common.credential`，含 `PassthroughCredentialDecryptor` 默认直通实现），agentcore-ext 通过 `agent-service-adapters-agentcore` → `agent-service-adapters-common` 传递依赖可访问。`SkillHubMiddlewareAutoConfiguration` 注入 `CredentialDecryptor` Bean，调用 `decryptor.decrypt(encryptedToken)` 获取明文 token（对齐 `RedisMiddlewareAutoConfiguration` 的 `decryptor.decrypt(endpoint.getEncryptedPassword())` 模式）。

### 3.2 核心类静态关系

```
«autoconfigure»                      «manager»
SkillHubMiddleware              →   SkillHubManager
AutoConfiguration                    │
  │ @ConditionalOnProperty           │ 构造方法(provider, installer, properties, decryptor)
  │   openjiuwen.service.            │   → provider.start() + 触发首次 download()
  │   middleware.skillhub.enabled    ├─ download() 成功 → 逐条 verify(skillPath) → 未安装列表
  │ @ConditionalOnMissingBean        │   download() 失败 → 后台线程定时重试 + verify + 未安装列表
  │   SkillHubProvider               │
  │  默认 = OpenJiuwen               │ register(agent)（query/streamQuery 阶段调）
  │  SkillHubProvider                │   未安装列表非空 → 取路径 → 委托 SkillHubInstaller.install(agent, paths) → 移入已安装列表
  │                                  │   未安装列表为空 → 直接返回（请求照常处理）
  ▼                                  │
SkillHubProvider                ←────┤ reregister(agent) → 清空已安装列表，全部 skill 重新注册
(OpenJiuwen 默认                     │
 或 业务 @Bean 覆盖)                 │ stop() → 停后台线程 + provider.stop()
 ┌──────────────────────────┐        │
 │ start(properties, decryptor)│     │ 分层失败语义：
 │ download(properties, decryptor)│   │   required 移交失败 → throw
 │   → boolean              │        │   optional 任何失败 → warn + skip
 │ verify(skillPath)        │        ▼
 │   → boolean              │   SkillHubInstaller
 │ stop()                   │  .install(agent, paths)（请求线程串行注册）
 │                          │   注：校验方式由 Provider 实现自决，
 │                          │      文档不约束具体校验算法
 └──────────────────────────┘

«agentfw»
JiuwenCoreAgentExtHandler (extends JiuwenCoreAgentHandler)
  │
  ├─ start()   ← override
  │    ├─ skillHubManager.download()   ← 新增（触发首次下载+校验，失败则后台重试）
  │    └─ super.start()
  │         ├─ middlewareAdapterRegistrar.applyToRunnerConfig(...)
  │         ├─ externalSvcAdapterRegistrar.registerToRunner()
  │         └─ Runner.start()
  │
  └─ streamQuery / query  ← override：调 skillHubManager.register(agent)（未安装列表非空时注册，为空时照常处理）

«config»
SkillHubMiddlewareProperties  ← @ConfigurationProperties("openjiuwen.service.middleware.skillhub")
  ├─ enabled (boolean, default false)
  ├─ endpoint (String)
  ├─ authType (String, system-token | bearer)
  ├─ encryptedToken (String)   ← 加密凭据，经 CredentialDecryptor.decrypt() 解密（对齐 encryptedPassword）
  ├─ provider (String, openjiuwen | custom)
  └─ localDir (String, 下载 skill 的本地目录)
```

---

## 4. 核心设计

### 4.1 Skill 安装时序（含降级）

```
应用启动
  │
  ▼
JiuwenCoreAgentExtHandler.start()
  │
  ├─ skillHubManager.download()   ← 触发首次下载+校验
  │     │
  │     │  ┌─────────────────────────────────────────────────────────────┐
  │     │  │ SkillHubManager.download() 内部逻辑                        │
  │     │  │                                                             │
  │     │  ├─ boolean ok = provider.download(properties, decryptor)
  │     │  │     下载应下载的全部 skill 到 properties.getLocalDir()
  │     │  │
  │     │  ├─ if (ok) {
  │     │  │     paths = 扫描 properties.getLocalDir() 下已下载的 skill
  │     │  │     for each path in paths:
  │     │  │       boolean verified = provider.verify(path)
  │     │  │       if (verified) → 加入"未安装列表"
  │     │  │       else → 不加入（Provider 内部日志记录）
  │     │  │   }
  │     │  ├─ if (!ok 或 未安装列表为空) {
  │     │  │     → 启动后台线程定时重试 provider.download()
  │     │  │     → 成功后 verify + 加入"未安装列表"
  │     │  │     → 后台线程不触碰 SkillManager（注册严格在请求线程）
  │     │  │   }
  │     │  └─────────────────────────────────────────────────────────────┘
  │     │   注：start() 阶段不抛异常（下载失败降级，后台重试）
  │
  ├─ super.start()
  │     ├─ middlewareAdapterRegistrar.applyToRunnerConfig(...)
  │     ├─ externalSvcAdapterRegistrar.registerToRunner()
  │     └─ Runner.start()
  │
  ▼
请求处理（streamQuery / query）
  │
  ├─ skillHubManager.register(agent)   ← 请求线程执行
  │     │
  │     │  ┌─────────────────────────────────────────────────────────────┐
  │     │  │ SkillHubManager.register(agent) 内部逻辑                    │
  │     │  │                                                             │
  │     │  ├─ if (未安装列表为空) → 直接返回（请求照常处理，skill 尚未注册）
  │     │  ├─ if (未安装列表非空) {
  │     │  │     paths = 未安装列表
  │     │  │     skillHubInstaller.install(agent, paths)
  │     │  │       ├─ agent instanceof DeepAgent → innerAgent = deepAgent.getAgent()
  │     │  │       ├─ agent instanceof BaseAgent → target = baseAgent
  │     │  │       ├─ 其他 → warn + skip
  │     │  │       └─ for each path: target.registerSkill(path)
  │     │  │     安装后将路径从"未安装列表"移入"已安装列表"（避免重复注册）
  │     │  │   }
  │     │  └─────────────────────────────────────────────────────────────┘
  │
  └─ Runner.runAgentStreaming(agent, ...)   ← agent 使用已注册的 skill
```

**关键语义**：
- `SkillHubManager.download()` 对下载和校验分别处理，start() 阶段不抛异常（下载失败降级 + 后台重试）
- required skill 移交失败（registerSkill 后 skillCount 未增长）→ `install` 抛异常 → 请求线程感知
- required skill 下载/校验失败 → 降级（"未安装列表"为空），`start()` 继续 `super.start()`，Agent ready（skill 不可用）；后台线程重试成功后校验通过项加入"未安装列表"
- optional skill 任何失败 → warn + skip + 降级诊断，该 skill 不注册为可用
- 下载/校验失败的 skill 不得加入"未安装列表"
- "未安装列表"为空时请求照常处理，skill 尚未注册；`register(agent)` 成功后路径从"未安装列表"移入"已安装列表"避免重复注册

### 4.2 稳定部署态配置

SkillHub SPI 入参是部署态稳定的配置（endpoint、加密凭据 from `SkillHubMiddlewareProperties`），不依赖请求级 user/session/task。

**与请求上下文的区别**：需求文档明确，当前版本不要求 Provider 基于每次请求的 user/session/task 动态变更 skill 集合。SPI 入参是部署态稳定的，不会随请求变化。如业务确需请求级权限过滤，应由网关、业务 Provider 预筛选或独立 Agent/Handler 实例承接。

### 4.3 Skill path 解析规则

skill path 从 `SkillHubManager` 的"未安装列表"获取（download + verify 后校验通过的路径）。path 是本地文件系统路径（解压后根目录或 zip 文件），直接传给 `BaseAgent.registerSkill(path)`。

### 4.4 完整性校验（由 Provider 实现自决）

依据 PR #415：runtime 必须在移交前校验下载材料。但**校验方法由 `SkillHubProvider` 实现自决**（SHA-256 / 常规文件检查 / 自定义均可），本设计文档不强制约束具体校验算法。唯一要求：校验失败的材料不得注册为可用，由 `verify(skillPath)` 逐条返回 false 或抛出 `IllegalStateException`（message 前缀 `SkillHub[CHECKSUM_MISMATCH]`，分类见 §4.10），`SkillHubManager` 据此将失败项从"未安装列表"排除。

**校验失败处理**：
- required skill 校验失败 → Installer 捕获后降级 ready，skill 不可用
- optional skill 校验失败 → Installer 捕获后 warn + skip

### 4.5 安装结果验证

`BaseAgent` 的 skill 计数通过 `agent.getSkillUtil().getSkillManager().count()` 获取。安装前后计数对比：

| before | after | 判定 | 日志级别 |
|---|---|---|---|
| -1 | -1 | `SkillUtil` 或 `SkillManager` 为 null | warn（skill 运行时未初始化） |
| N | N | 计数未增长 | warn（SKILL.md 可能缺少 YAML frontmatter） |
| N | N+1 | 计数增长 | info（安装成功） |

**注意**：安装结果验证（skillCount 对比）是辅助诊断手段，不替代完整性校验（§4.4）。完整性校验在 registerSkill 之前执行；skillCount 对比在 registerSkill 之后执行，用于确认注册是否生效。

### 4.7 自动装配条件

```
SkillHubMiddlewareAutoConfiguration
  │
  ├─ @EnableConfigurationProperties(SkillHubMiddlewareProperties.class)
  ├─ @ConditionalOnProperty(
  │     prefix = "openjiuwen.service.middleware.skillhub",
  │     name = "enabled", havingValue = "true")
  ├─ @ConditionalOnClass(RunnerConfig.class)
  │
  ├─ @Bean SkillHubProvider
  │   @ConditionalOnMissingBean(SkillHubProvider.class)
  │   → String token = decryptor.decrypt(properties.getEncryptedToken())
  │   → new OpenJiuwenSkillHubProvider(properties.getEndpoint(), token, properties.getAuthType())
  │   （注入 CredentialDecryptor，对齐 RedisMiddlewareAutoConfiguration 的 decryptor.decrypt(endpoint.getEncryptedPassword())）
  │   （OpenJiuwenSkillHubProvider 实现 SkillHubProvider，校验方式由 Provider 实现自决）
  │
  ├─ @Bean SkillHubManager
  │   @ConditionalOnMissingBean
  │   → new SkillHubManager(provider, installer, properties, decryptor)
  │     （构造方法内部调用 provider.start() + 触发首次 download()）
  │
  └─ 注入到 JiuwenCoreAgentExtHandler
      └─ @Autowired(required = false) SkillHubManager
          → 有则注入，无则 null（Handler.start() 直接 super.start()）
```

无 `SkillHubProvider` Bean 或 `enabled=false` 时，整条 SkillHub 链路不激活，`JiuwenCoreAgentExtHandler` 不持有 `SkillHubManager`，行为与无 SkillHub 时完全一致。

**对齐 runtime middleware 模式**：
- `@ConditionalOnProperty` 控制激活（同 `RedisMiddlewareAutoConfiguration`）
- `@ConditionalOnMissingBean` 允许业务覆盖（同 redis client bean）
- `OpenJiuwenSkillHubProvider` 构造时注入解密后的明文 token（`SkillHubMiddlewareAutoConfiguration` 调用 `decryptor.decrypt(properties.getEncryptedToken())`，对齐 `RedisMiddlewareAutoConfiguration` 的 `decryptor.decrypt(endpoint.getEncryptedPassword())`）
- `ObjectProvider` 用于可选依赖（同 `AgentCoreExtAutoConfiguration` 的 `A2ARemoteAgentCardRegistry`）

### 4.8 安装顺序与冲突语义

SkillHub installer 与 MCP installer、远程 Agent tool installer 在同一启动阶段执行时，顺序和冲突语义必须可诊断。当前项目中：
- middleware 注册（checkpointer，`applyToRunnerConfig`）→ SkillHub 下载（`SkillHubManager.download()`）→ external 注册（MCP/Remote/Sandbox，`registerToRunner`）→ `Runner.start()` → 请求期 SkillHub 注册（`SkillHubManager.register(agent)` 在 query/streamQuery 中调用）
- **required skill 移交失败阻断请求**：`install` 抛异常，请求线程感知（start() 阶段下载失败不阻断，降级 + 后台重试）
- **required skill 下载/校验失败不阻断 start()**：降级（"未安装列表"为空）后继续 `super.start()`，进入 external 注册和 `Runner.start()`；后台线程重试成功后校验通过项加入"未安装列表"，后续请求注册 skill
- **optional skill 失败不阻断**：warn + skip 后继续处理下一个 skill；optional 全部失败后仍正常进入 external 注册和 `Runner.start()`
- external 注册（MCP/Remote/Sandbox）与 SkillHub 相互独立，external 失败的语义由 external 自身决定，不被 SkillHub 失败影响

### 4.9 required / optional 行为矩阵

第一期 `LocalSkillEntry` 只含 skillId + localPath，无 required 字段。required/optional 语义由 Provider 实现内部决定（如从配置或 Skill Hub 元数据推断），SkillHubManager 通过 Provider 提供的 required 标记执行分层失败语义。后续版本可通过 `SkillHubMiddlewareProperties` 配置 required skill 列表。

**失败行为矩阵**（PR #415 更新）：

| skill 类型 | 失败阶段 | 错误分类 | 行为 | Agent ready |
|---|---|---|---|---|
| required | download（内部连接） | `CONNECT_FAILED` | **degrade** + 后台重试 | ✓ 降级 ready |
| required | download（内部连接） | `AUTH_FAILED` | **degrade** + 后台重试 | ✓ 降级 ready |
| required | download | `NOT_FOUND` | **degrade** + 后台重试 | ✓ 降级 ready |
| required | download | `ACCESS_DENIED` | **degrade** + 后台重试 | ✓ 降级 ready |
| required | download | `DOWNLOAD_FAILED` | **degrade** + 后台重试 | ✓ 降级 ready |
| required | verify | `CHECKSUM_MISMATCH` | **degrade**（失败项从未安装列表排除） | ✓ 降级 ready |
| required | registerSkill | `INSTALL_FAILED` | **throw**（请求线程感知） | 请求异常 |
| optional | 任一阶段 | 任一分类 | **warn + skip** | ✓ 继续 |
| 无 Provider | — | — | noop，正常启动 | ✓ |

注：start() 阶段所有 download/verify 失败均降级 + 后台重试，不阻断 Agent ready；仅 registerSkill 移交失败在请求线程抛异常。

**降级诊断要求**：具体 skillId、required/optional 标记、failureCategory、failureReason（脱敏，不含凭据或敏感内容）由 Provider 内部日志输出（Provider 内部知道哪个 skill 失败）。Installer 层日志输出汇总：tenantId、agentId、installed 数量、skipped 数量、是否降级。

### 4.10 错误诊断与分类

`SkillHubErrorCategory` 枚举覆盖 FEAT-005 §5.1.5 的失败分类：

| Category | 触发条件 | required 行为 | optional 行为 |
|---|---|---|---|
| `CONNECT_FAILED` | endpoint 缺失或不可达 | 降级 + 后台重试 | 降级 skip |
| `AUTH_FAILED` | 凭据缺失/无效/过期/401/403 | 降级 + 后台重试 | 降级 skip |
| `ACCESS_DENIED` | Skill Hub 拒绝访问 | 降级 + 后台重试 | 降级 skip |
| `NOT_FOUND` | skill 不存在或无权访问 | 降级 + 后台重试 | 降级 skip |
| `DOWNLOAD_FAILED` | 下载中断、包损坏 | **降级** + 后台重试 | 降级 skip |
| `CHECKSUM_MISMATCH` | 完整性校验失败 | **降级**（失败项从未安装列表排除） | 降级 skip |
| `INSTALL_FAILED` | registerSkill 后 skillCount 未增长 | throw（请求线程） | 降级 skip |
| `UNSUPPORTED` | download 不支持（如 Skill Hub 无 artifact 下载能力） | 降级 + 后台重试 | 降级 skip |
| `UNKNOWN` | 未分类异常兜底 | 降级 + 后台重试 | 降级 skip |

**异常约定**：复用 JDK 异常（`IllegalStateException`），不新建异常类。分类信息通过异常 message 前缀携带：

```java
// 不新建异常类，复用 IllegalStateException
throw new IllegalStateException("SkillHub[" + category + "] skillId=" + skillId + ": " + sanitizedMessage);
```

**诊断输出要求**：
- 日志可输出 adapter 名称、endpoint 摘要（不含路径后的 query/认证信息）、skill id、required/optional、failureCategory、correlation 信息
- 日志不得输出明文 token、认证头、密钥、内部敏感地址或敏感 skill 内容
- 错误响应对外暴露时只返回分类和脱敏摘要，不暴露内部地址或凭据

### 4.11 凭据与敏感信息保护

依据 FEAT-005 §2 和 §5.1.5，对齐 runtime middleware 凭据处理模式。runtime 已提供 `CredentialDecryptor` 模块（`agent-service-adapters-common.credential`），ext 通过传递依赖访问。

| 项 | 要求 |
|---|---|
| 凭据存储 | `SkillHubMiddlewareProperties.encryptedToken` 持有密文 token（对齐 `MiddlewareProperties.RedisEndpoint.encryptedPassword`） |
| 凭据解密 | `SkillHubMiddlewareAutoConfiguration` 注入 `CredentialDecryptor` Bean，调用 `decryptor.decrypt(encryptedToken)` 获取明文 token（对齐 `R
edisMiddlewareAutoConfiguration`）；默认实现 `PassthroughCredentialDecryptor` 直通返回（密文=明文），业务可通过自定义 Bean 覆盖为真实解密 |
| 凭据使用 | `OpenJiuwenSkillHubProvider` 构造时接收解密后的明文 token，作为 HTTP 认证头值；明文 token 不持久化、不进日志 |
| 日志 | 不得输出 token、API key、认证头、内部敏感地址、skill 包敏感内容；只记录 `credential=provided` 或 `credential=absent` |
| 错误响应 | 对外只返回 `SkillHubErrorCategory` 和脱敏摘要，不暴露 endpoint 完整路径、凭据片段或内部堆栈 |
| 遥测数据 | 同日志要求；如需 metric，只输出计数和分类，不输出值内容 |
| 持久化配置 | `encryptedToken` 密文可写入配置文件；明文 token 不得写入提交到代码仓库的配置文件 |
| 异常堆栈 | 异常 message 已脱敏；原始异常的 stacktrace 不输出到响应 |

**`OpenJiuwenSkillHubProvider` 凭据处理**：
- 构造时接收解密后的明文 token（由 `SkillHubMiddlewareAutoConfiguration` 通过 `CredentialDecryptor.decrypt()` 解密后传入）
- 注入 HTTP 头 `Authorization: Bearer <token>` 或 `X-System-Token: <token>`
- token 不进入日志、异常消息或 metric 标签
- 日志只记录 `credential=provided` 或 `credential=absent`

---

## 5. 数据模型

> **模块/包归属**：本节数据模型均为纯契约（record/enum），归属 `agent-service-spec-ext` 项目 `com.openjiuwen.service.spec.ext.skillhub` 及 `com.openjiuwen.service.spec.ext.skillhub.dto` 包（对齐 runtime `agent-service-spec.spec.dto`），no Spring，不含实现依赖。实现类（`Installer`/`Verifier` 等）在 `agent-service-adapters-agentcore-ext` 项目的 `...ext.middleware.skillhub` 包（§4）。

### 5.1 LocalSkillEntry

```java
/** 本地 skill 列表项，只含 skillId 和本地路径。 */
public record LocalSkillEntry(
        String skillId,           // 必填
        Path localPath             // 必填，本地路径（解压后根目录或 zip 文件）
) { }
```

由 `SkillHubManager` 的"未安装列表"（download + verify 后校验通过的路径）产出。

### 5.2 SkillHubMiddlewareProperties（middleware 配置模型）

对齐 runtime `MiddlewareProperties` 风格：POJO + 静态嵌套类 + getter/setter（非 record）。凭据字段 `encryptedToken` + `CredentialDecryptor` 解密（对齐 `MiddlewareProperties.RedisEndpoint.encryptedPassword`）。

```java
@ConfigurationProperties(prefix = "openjiuwen.service.middleware.skillhub")
public class SkillHubMiddlewareProperties {
    private boolean enabled = false;
    private String endpoint = "";
    private String authType = "system-token";   // system-token | bearer
    private String encryptedToken = "";          // 加密凭据，经 CredentialDecryptor.decrypt() 解密（对齐 encryptedPassword）
    private String provider = "openjiuwen";      // openjiuwen | custom
    private String localDir = "";                // 下载 skill 的本地目录

    // getter/setter 省略
}
```

**配置归属说明**（依据 FEAT-005 §5.1.1）：
- Skill Hub 服务连接配置（endpoint、authType、encryptedToken、provider、localDir）归 runtime middleware 配置持有
- Agent 配置不持有 Skill Hub 访问凭据
- `encryptedToken` 为密文，经 `CredentialDecryptor.decrypt()` 解密后使用（对齐 `encryptedPassword`）；默认 `PassthroughCredentialDecryptor` 直通返回，业务可通过自定义 Bean 覆盖为真实解密

**配置示例**：

```yaml
openjiuwen:
  service:
    middleware:
      skillhub:
        enabled: true
        endpoint: https://swarmskills.openjiuwen.com
        auth-type: system-token
        encrypted-token: ${SKILLHUB_ENCRYPTED_TOKEN:}   # 加密凭据，经 CredentialDecryptor 解密；默认直通实现时密文=明文
        provider: openjiuwen
        retry:
          max-attempts: 3
          initial-delay-ms: 5000
          max-delay-ms: 60000
```

---

## 6. JiuwenCoreAgentExtHandler 扩展

### 6.1 新增字段和 setter

扩展已有的 `JiuwenCoreAgentExtHandler`（继承 runtime `JiuwenCoreAgentHandler`），新增 SkillHubManager 注入：

```java
public class JiuwenCoreAgentExtHandler extends JiuwenCoreAgentHandler {
    private RemoteA2aToolInstaller remoteToolInstaller = RemoteA2aToolInstaller.noop();
    private SkillHubManager skillHubManager;  // 新增（可为 null，表示未启用 SkillHub）

    // 现有构造器保留

    @Autowired(required = false)
    void setRemoteA2aToolInstaller(RemoteA2aToolInstaller remoteToolInstaller) {
        this.remoteToolInstaller = Objects.requireNonNull(remoteToolInstaller);
    }

    @Autowired(required = false)               // 新增
    void setSkillHubManager(SkillHubManager skillHubManager) {
        this.skillHubManager = skillHubManager;
    }

    // ... 现有 streamQuery / query 需 override 调 register
}
```

### 6.2 start() / query() / streamQuery() 改动

```java
@Override
public void start() {
    if (skillHubManager != null) {
        try {
            skillHubManager.download();  // 触发首次下载+校验，失败降级+后台重试
        } catch (Exception ex) {
            // start() 阶段下载失败不阻断（内部降级 + 后台重试）
            log.warn("SkillHub download failed, will retry in background reason={}", sanitize(ex.getMessage()));
        }
    }
    super.start();
}

@Override
public String streamQuery(/* 现有参数 */) {
    if (skillHubManager != null) {
        skillHubManager.register(getAgent());  // 未安装列表非空时注册，为空时直接返回
    }
    return super.streamQuery(/* 现有参数 */);
}

@Override
public String query(/* 现有参数 */) {
    if (skillHubManager != null) {
        skillHubManager.register(getAgent());  // 未安装列表非空时注册，为空时直接返回
    }
    return super.query(/* 现有参数 */);
}
```

**分层失败语义说明**：
- `start()` 阶段 `download()` 失败不阻断，降级 + 后台重试，Agent ready（skill 不可用）
- `query()`/`streamQuery()` 阶段 `register(agent)` 在"未安装列表"非空时执行注册；为空时直接返回，请求照常处理
- `register(agent)` 内部 `install` 移交失败时抛异常，请求线程感知
- 日志输出 category、degraded（汇总），不输出凭据或敏感内容

---

## 7. 对外呈现 / 用户场景

### 7.1 业务接入方式

业务方自建 `@Bean AgentHandler` 传入 `BaseAgent` 实例，配置 SkillHub middleware 即可自动安装 skill：

```java
@Bean
public AgentHandler agentHandler(BaseAgent agent,
        MiddlewareAdapterRegistrar middlewareRegistrar,
        ExternalSvcAdapterRegistrar externalRegistrar) {
    // 使用 ExtHandler 而非 runtime 的 JiuwenCoreAgentHandler
    return new JiuwenCoreAgentExtHandler(agent, middlewareRegistrar, externalRegistrar);
}
```

```yaml
openjiuwen:
  service:
    middleware:
      skillhub:
        enabled: true
        endpoint: https://swarmskills.openjiuwen.com
        auth-type: system-token
        encrypted-token: ${SKILLHUB_ENCRYPTED_TOKEN:}   # 加密凭据，经 CredentialDecryptor 解密
```

Spring Boot 自动装配会：
1. 绑定 `SkillHubMiddlewareProperties`
2. 当 `enabled=true` 时创建 `OpenJiuwenSkillHubProvider`（实现 `SkillHubProvider`）、`SkillHubManager` bean
3. 通过 `@Autowired(required = false)` 注入到 `JiuwenCoreAgentExtHandler`
4. `start()` 时自动执行 skill 下载+校验；`query()`/`streamQuery()` 时执行注册

### 7.2 本地 skill 目录约定

`SkillHubProvider.download(properties, decryptor)` 下载应下载的全部 skill 到 `properties.getLocalDir()` 本地目录。`SkillHubManager` 扫描该目录获取已下载的 skill 路径，逐条调 `SkillHubProvider.verify(skillPath)` 校验，校验通过的路径加入"未安装列表"。目录结构由 Provider 实现自决（如按 skillId 子目录组织），但最终注册的路径必须是 `BaseAgent.registerSkill(path)` 可直接使用的本地路径。

### 7.3 日志输出示例

**正常流程**：
```
INFO  SkillHub download started tenantId=default agentId=hotel-agent   ← Manager 层
INFO  SkillHub skill download succeeded skillId=hotel-booking   ← Provider 内部日志
INFO  SkillHub skill verified skillId=hotel-booking verified=true   ← Provider 内部日志
INFO  SkillHub skill registered skillId=hotel-booking skillCountBefore=0 skillCountAfter=1   ← Installer 层（请求线程）
```

**required skill 下载失败降级 + 后台重试**：
```
WARN  SkillHub skill download failed skillId=hotel-booking required=true category=DOWNLOAD_FAILED   ← Provider 内部日志
INFO  SkillHub download completed (degraded) tenantId=default agentId=hotel-agent   ← Manager 层（start() 阶段）
INFO  SkillHub background retry started   ← Manager 层
INFO  SkillHub skill download succeeded skillId=hotel-booking   ← Provider 内部日志（后台重试成功）
INFO  SkillHub skill verified skillId=hotel-booking verified=true   ← Provider 内部日志
INFO  SkillHub skill added to uninstalled list skillId=hotel-booking   ← Manager 层（加入"未安装列表"）
```

**optional skill 跳过**：
```
WARN  SkillHub skill download failed skillId=weather-lookup required=false category=DOWNLOAD_FAILED reason=download-interrupted   ← Provider 内部日志
```

**无 Provider 降级**：
```
INFO  SkillHub not active (no provider or disabled), agent starts normally
```

### 7.4 E2E 流程

1. 应用启动 → Spring 装配 `SkillHubMiddlewareProperties` + `OpenJiuwenSkillHubProvider` + `SkillHubManager`
2. `SkillHubManager` 构造方法调用 `provider.start()` + 触发首次 `download()`
3. `JiuwenCoreAgentExtHandler.start()` 调用 `skillHubManager.download()`（若已由构造触发则跳过重复下载）
4. Manager 调用 `provider.download(properties, decryptor)` → boolean（Provider 内部自决下载哪些 skill，下载到 properties.getLocalDir()）
5. 下载成功 → Manager 扫描本地路径，逐条调 `provider.verify(skillPath)` → 校验通过路径加入"未安装列表"
6. 下载失败 → Manager 启动后台线程定时重试 `provider.download()`，成功后 verify + 加入"未安装列表"
7. required 下载/校验失败 → 降级（"未安装列表"为空）+ 后台重试；optional 失败 → skip
8. `super.start()` → `Runner.start()`
9. 请求处理 → `Handler.query()`/`streamQuery()` 调 `skillHubManager.register(agent)` → "未安装列表"非空时 `install(agent, paths)`，安装后移入"已安装列表" → `Runner.runAgentStreaming` → agent 使用已注册的 skill
10. `register(agent)` 成功后路径从"未安装列表"移入"已安装列表"，避免重复注册

### 7.5 无 Provider 降级

未配置 `SkillHubProvider` Bean 或 `enabled=false` 时：
- `SkillHubManager` 为 null，`JiuwenCoreAgentExtHandler` 不持有 Manager
- `start()` 直接 `super.start()`，`query()`/`streamQuery()` 直接调父类
- Agent 正常启动和执行，无 skill 安装

### 7.6 `OpenJiuwenSkillHubProvider` API 映射

默认实现对接 `openJiuwen/skillhub` 服务 API（依据 TeamSkillsHub 接口参考）：

| SPI 方法 | HTTP API | 说明 |
|---|---|---|
| `start(properties, decryptor)` | 无（建立连接池、预热认证） | 初始化 HTTP 客户端，解密 token |
| `download(properties, decryptor)` | `GET /api/v1/plugins?plugin_type=skill` + `GET /api/v1/artifacts/{id}?version={ver}` | 先用解密后 token 作 HTTP 认证头拉取应下载的 skill 清单，再逐个预签名 URL 下载 zip 到 properties.getLocalDir() → 返回 boolean（成功/失败） |
| `verify(skillPath)` | 无（本地校验） | 逐条校验本地已下载 skill 路径的完整性（方式由实现自决）→ 返回 boolean |
| `stop()` | 无（关闭连接池） | 释放 HTTP 客户端资源 |

**认证**：
- `authType=system-token` → HTTP 头 `X-System-Token: <token>`（服务端集成，运维发放的静态 token）
- `authType=bearer` → HTTP 头 `Authorization: Bearer <token>`（通过外部 OAuth 流程获取后注入的 access_token）
- 两种方式的 token 均由 `SkillHubMiddlewareAutoConfiguration` 调用 `CredentialDecryptor.decrypt(properties.getEncryptedToken())` 解密后传入 `OpenJiuwenSkillHubProvider`，不进入日志
- runtime 只负责"使用解密后的 token 作为认证头"，不执行 OAuth 浏览器交互流程

**版本选择**：默认取 `latest_version`；若需要固定版本，由 Provider 实现内部按配置或元数据决定。

**required 字段来源**：第一期 `LocalSkillEntry` 只含 skillId + localPath，无 required 字段。required/optional 语义由 `OpenJiuwenSkillHubProvider` 实现内部决定（如从 Skill Hub 返回的元数据或配置推断），`SkillHubManager` 通过 Provider 提供的 required 标记执行分层失败语义。

**完整性校验数据来源**：`/artifacts/{id}` 响应中的 `checksum_sha256` 和 `file_size` 由 `verify` 实现内部自行决定如何使用（可选 SHA-256 校验或常规校验），校验方式由 Provider 实现自决，文档不强制约束。

### 7.7 场景覆盖（FEAT-005 §4）

| 场景 | 期望行为 |
|---|---|
| 启动时获取 required skill | runtime 通过 SkillHub SPI 认证访问，下载 skill 包，完整性校验通过后加入"未安装列表"；query/streamQuery 时注册；Agent ready |
| required skill 下载/校验失败 | Agent 降级为 ready，skill 不可用；SkillHubManager 启动后台线程定时重试下载，成功后校验通过项加入"未安装列表" |
| required skill 配置/认证/查找失败 | 降级 ready + 后台重试；诊断说明 skill 不可用但不泄露凭据、内部地址或敏感内容 |
| required skill 不存在 | 降级 ready + 后台重试；诊断说明 skill 不可用 |
| optional skill 下载失败 | runtime 跳过该 optional skill，输出脱敏降级诊断；Agent 可继续 ready，但该 skill 不注册 |
| 凭据缺失或无效 | 降级 ready + 后台重试；日志和错误不输出凭据 |
| 替换 Skill Hub 实现 | 业务方提供自定义 `@Bean SkillHubProvider` 覆盖默认实现；Agent 业务代码不需修改 |
| Skill Hub 不支持 digest | 校验方式由 Provider 实现自决（常规文件检查/自定义均可），文档不强制 |
| 下载/校验失败后台重试 | 日志告警 skill 不可用；Agent 继续运行（已降级 ready）；后台重试成功后校验通过项加入"未安装列表"，后续请求注册 skill |
| "未安装列表"为空时请求 | 请求照常处理，skill 尚未注册（Agent 可响应但无 skill 可用） |
| 首次成功注册后重复请求 | `register(agent)` 成功后路径从"未安装列表"移入"已安装列表"，后续请求不重复注册 |

---

## 8. 测试矩阵（PR #415）

依据 PR #415 Change 3，测试必须覆盖以下场景：

| # | 测试场景 | 验证点 | 预期结果 |
|---|---|---|---|
| T1 | required skill 下载失败（config/auth/lookup） | 降级 + 后台重试 | `start()` 正常返回，`Runner.start()` 已调用；"未安装列表"为空；后台线程启动重试 |
| T2 | required skill 下载失败（download） | 降级 + 后台重试 | 同 T1；skill 未注册；后台重试成功后校验通过项加入"未安装列表" |
| T3 | required skill 校验失败（checksum mismatch） | 降级，失败项从未安装列表排除 | 同 T2，且 `verify` 返回 false 或抛 `CHECKSUM_MISMATCH`，失败路径不加入"未安装列表" |
| T4 | 下载成功后注册 | 请求期注册 | `download` 返回 true；`verify` 返回 true；"未安装列表"非空；`query()`/`streamQuery()` 调 `register(agent)` 后 `registerSkill` skillCount 增长；请求可用到 skill |
| T5 | 校验通过（Provider 用 SHA-256） | digest 校验 | `verify` 返回 true；路径加入"未安装列表"；skill 注册成功（校验方式由 Provider 自决，文档不强制 SHA-256） |
| T6 | 校验通过（Provider 用常规检查） | 常规校验兜底 | 同 T5（校验方式由 Provider 自决） |
| T7 | 校验失败拒绝注册 | 不注册未校验材料 | `verify` 返回 false 或抛 `CHECKSUM_MISMATCH`；失败路径不加入"未安装列表"；required 降级 / optional skip |
| T8 | 首次有效注册后不重复注册 | 幂等保护 | 首次 `register(agent)` 注册后路径移入"已安装列表"，后续请求"未安装列表"为空不重复注册 |
| T9 | optional skill 任何失败 | 降级 skip | warn 日志 + 跳过，该 skill 不注册，Agent ready |
| T10 | 日志脱敏 | 不泄露敏感信息 | 日志无 token/认证头/敏感凭据，只有 `credential=provided/absent` |
| T11 | 无 Provider 降级 | noop 不影响启动 | `enabled=false` 或无 Provider 时 Agent 正常启动，`SkillHubManager` 为 null |
| T13 | required 移交失败（INSTALL_FAILED） | 请求线程抛异常 | registerSkill 后 skillCount 未增长，`register(agent)` 抛异常，请求线程感知 |
| T14 | DeepAgent 适配 | 取 inner ReActAgent | `install(DeepAgent, paths)` 取 `deepAgent.getAgent()` 注册 |
| T15 | "未安装列表"为空时请求 | 请求照常处理 | "未安装列表"为空时 `query()`/`streamQuery()` 直接调父类，skill 尚未注册但 Agent 可响应 |
| T16 | 后台重试成功后注册 | 加入未安装列表 | 下载失败后后台重试成功 → verify 通过 → 加入"未安装列表" → 后续请求 `register(agent)` 注册 skill |

---

## 9. 限制与待补

### 9.1 为什么 SkillHub 不通过 Runner 配置 skill

FEAT-005 选择通过 `BaseAgent.registerSkill(path)`（agent 实例方法）安装 skill，而非在 `RunnerConfig` 中配置 skill 字段。原因如下（已核实 agent-core 0.1.13 代码）：

**1. RunnerConfig 无 skill 字段，且 ext 无法修改**

`RunnerConfig`（`com.openjiuwen.core.runner.RunnerConfig`）当前仅包含运行时基础设施字段：`checkpointerConfig`、`mcpServers`、`kvStoreConfig`、`vectorStoreConfig`、`objectStorageConfig`。无任何 skill 相关字段。ext 模块以 jar 依赖引入 agent-core，不能修改 `RunnerConfig` 添加 skill 字段。

**2. harness 层的 `skillDirectories` 是静态本地路径，非运行时远程下载**

agent-core 在 harness 配置层（`DeepAgentConfig.skillDirectories`、`SubAgentConfig.skillDirectories`）提供了 skill 目录配置，但这是**静态本地路径列表**：在 harness 构建期由 `HarnessConfigBuilder.skillDirectories(resolveSkillDirs(...))` 从配置文件解析本地目录，供 `SkillUseRail` 加载本地 skill 文件。该机制：
- 仅支持本地文件系统路径，不支持远程 Skill Hub 下载
- 在 harness 构建期解析，不在运行时请求链路中执行
- 不涉及完整性校验、降级重试等 FEAT-005 要求的运行时能力

因此 `skillDirectories` 无法满足 FEAT-005"运行时从远程 Skill Hub 下载 + 完整性校验 + 降级重试"的需求。

**3. skill 注册是 agent 实例方法，注册路径由 agent 实例决定**

`BaseAgent.registerSkill(Object)` 是 agent **实例方法**（非静态方法），注册目标是特定 agent 实例的 `SkillManager`。Runner 层不持有 agent 实例（agent 实例由 `Supplier<Object>` 工厂按需创建，见 §9.2），因此 Runner 层无法直接调用 `registerSkill`。

**4. middleware hook 的时机窗口在 handler `start()`，而非 Runner 配置链路**

runtime middleware 的安装 hook 在 `JiuwenCoreAgentExtHandler.start()` 阶段（`MiddlewareAdapterRegistrar.applyToRunnerConfig` 之后、`Runner.start()` 之前），此时 handler 持有 agent 实例（或 agent-id），是唯一能调用 `agent.registerSkill(path)` 的时机窗口。在 `RunnerConfig` 构建阶段（middleware adapter 注册期）agent 实例尚未创建，无法注册 skill。

**结论**：FEAT-005 通过 `JiuwenCoreAgentExtHandler.start()` hook 拿到 agent 实例后调用 `BaseAgent.registerSkill(path)` 安装 skill，是当前 agent-core API 下唯一可行的运行时远程 skill 安装路径。

**agent-core 为什么设计通过 agent 注册 skill（设计意图分析）**：

这是有意的领域模型设计，skill 是 agent 的私有能力而非全局基础设施。代码证据（agent-core 0.1.13）：

| 证据 | 位置 | 说明 |
|---|---|---|
| `SkillManager` 持有 `sysOperationId` | `SkillManager.java:35` | agent 实例级标识，用于文件操作隔离和操作追踪 |
| `SkillUtil` 构造时传入 `sysOperationId` 组合 `SkillManager` + `RemoteSkillUtil` | `SkillUtil.java:30-32` | skill 工具集与 agent 实例绑定 |
| `BaseAgent.lazyInitSkill()` 从 agent config 取 `sysOperationId` 创建 `SkillUtil` | `BaseAgent.java:55-67` | skill 在 agent 构造期初始化，是 agent 身份的一部分 |
| `ReActAgent.buildPrompt()` 用 `getSkillUtil().hasSkill()` 注入 skill section | `ReActAgent.java:513` | skill 直接影响该 agent 的 prompt |
| `SkillUtil.SKILL_PROMPT_CONTENT` 模板把 skill 内容注入系统提示词 | `SkillUtil.java:19-22` | skill 是 agent prompt 的组成部分 |

对比 `RunnerConfig` 的定位（`RunnerConfig.java` javadoc）："Runner global configuration... SPI injection fields for checkpointer, KV store, vector store, and object storage"。RunnerConfig 持有的是**无状态、跨 agent 共享**的基础设施 provider（checkpointer/KV/vector/objectStorage/mcpServers），而 skill 是**有状态、agent 私有**的能力（影响 prompt、绑定 sysOperationId）。Runner 不持有 agent 实例（`Runner.java:35-36`：`Runner.runAgent(agent, ...)` 的 agent 由调用方传入），因此 Runner 层无法直接注册 skill。

**后续演进路径**（若 agent-core 采纳某路径，FEAT-005 可平滑迁移）：

| 路径 | 改动量 | 方案 | 优点 | 缺点 |
|---|---|---|---|---|
| **A. SkillSourceProvider SPI** | 小 | agent-core 新增 `SkillSourceProvider` SPI 接口；`BaseAgent.lazyInitSkill()` 时主动调用 SPI 按 agent 身份拉取 skill 路径 | 改动最小；保持 skill 是 agent 私有状态的领域模型 | SPI 需能根据 AgentCard/sysOperationId 决定拉哪些 skill，agent-core 需暴露 agent 身份信息 |
| **B. RunnerConfig 增加 skill source SPI 字段** | 中 | `RunnerConfig` 新增 `Map<String, Object> skillSourceConfig`（对齐 checkpointerConfig 模式）；Runner 启动时创建 `SkillSourceProvider`；agent 构造时通过 ResourceMgr 拿到 provider 按 agent-id 拉取 skill | 对齐 RunnerConfig 现有 SPI 注入模式；runtime middleware 可像配置 checkpointer 一样配置 skill source | Runner 层需增加"把 provider 分发给 agent"的机制；skill 仍是 agent 私有但来源由 Runner 配置 |
| **C. Runner 直接管理 skill 注册** | 大 | `RunnerConfig` 新增 skill 字段；Runner 启动时统一下载/注册到所有 agent | 统一管理 | **不推荐**：违背"skill 是 agent 私有状态"的领域模型；不同 agent 需不同 skill 集合，Runner 层难以决策 |

**FEAT-005 的迁移策略**：当前通过 `handler.query()`/`streamQuery()` hook 主动调用 `registerSkill` 是符合 agent-core 领域模型的方案。若未来 agent-core 采纳路径 A 或 B，FEAT-005 可把 `SkillHubInstaller` 的"主动调用 registerSkill"改为"通过 SPI 让 agent 主动拉取"，`SkillHubProvider` SPI 契约（§4.4）和 middleware 配置（§5.6）无需变更。

### 9.2 agent-id 字符串场景

当配置 `openjiuwen.service.agent-id` 为字符串时，`JiuwenCoreAgentHandler` 持有的是 agent-id 字符串，实际 agent 实例由 Core Runner 内部通过 `Runner.resourceMgr()` 注册的工厂函数（`Supplier<Object>`）创建和管理。

**创建机制**：

```
1. 外部注册阶段：
   Runner.resourceMgr().addAgent(AgentCard, Supplier<Object> agentFactory, tags)
   // agent 以 Supplier 形式注册，见 ResourceMgr.addAgent / AgentMgr.addAgent

2. 执行阶段（Core 内部）：
   Runner.runAgentStreaming("agent-id", inputs, session, null, streamModes)
   → Runner 按 agent-id 查找已注册的 AgentCard + Supplier
   → 调用 supplier.get() 创建 agent 实例（工厂模式，每次请求新建实例）
   → 执行 agent.stream(inputs, session, streamModes)
   → 返回 Iterator<Object>（输出流）

3. agent 实例获取（agent-core 已提供）：
   Runner.resourceMgr().getAgent("agent-id")
   → ResourceMgr.innerGetResourcesByProvider → AgentMgr.getAgent → AbstractManager.getResource
   → supplier.get()（同样每次调用返回新实例或缓存实例，取决于 Supplier 实现）
```

**adapter 层无法在启动期安装 skill 的根因**：

1. **agent-id 场景的 Supplier 通常是工厂模式**（如 demo 的 `SessionEchoAgent::new`），`supplier.get()` 每次返回新实例；即便 `Runner.resourceMgr().getAgent(agentId)` 已存在（agent-core 0.1.13），返回的也是 supplier 新建实例，对临时实例 `registerSkill` 不会作用于后续请求的实例
2. **`JiuwenCoreAgentHandler` 不缓存 agent 实例**：handler 的 `getAgent()` 直接返回构造时传入的 agent-id 字符串（非实例），请求时由 `Runner.runAgentStreaming(agent, ...)` 内部按 id 解析 supplier
3. **agent-id 场景注册的 agent 可能不是 `BaseAgent` 子类**（如 `SessionEchoAgent` 是普通 POJO，没有 `registerSkill` 方法），即使能拿到实例也无法注册 skill

**结论**：SkillHub 的 `registerSkill` 是 `BaseAgent` 的方法，仅适用于 agent 实例场景（Demo / 业务自建 `@Bean AgentHandler`）。agent-id 场景跳过 skill 安装，输出 warn 日志。若 agent-id 场景需要 skill 支持，需 Core 侧提供"单例 agent + 启动 hook"或"工厂注入 skill 路径"机制（超出本仓范围）。

**注**：旧版本文档曾写"`Runner.resourceMgr()` 不提供按 ID 获取 agent 实例的 API"——该描述已过时。agent-core 0.1.13 已提供 `getAgent(String agentId)`，但因其依赖 Supplier 工厂模式且 handler 不缓存实例，仍无法用于启动期 skill 安装。

### 9.3 其他限制

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| DeepAgent 适配代码保留 | `install(DeepAgent)` 已实现，取 inner ReActAgent 安装；当前项目无 DeepAgent 路径但兼容后演进 | 保留适配代码，按 `instanceof DeepAgent` 分支处理 |
| `SkillManager` 非线程安全（已确认） | 已核实 agent-core `SkillManager` 的 `registry`（`LinkedHashMap`）/`updateAtCache`（`LinkedHashMap`）/`skillOrder`（`ArrayList`）均非线程安全集合，`register`/`refreshIncrementally`/`clearAll` 等方法无同步保护 | `SkillHubManager` 后台线程只负责 download + verify + 维护"未安装列表"，不触碰 SkillManager；注册严格在 `query()`/`streamQuery()` 请求线程执行，规避并发写 |
| 无 tenantId/agentId 上下文 | SPI 入参只有 `SkillHubMiddlewareProperties`（endpoint + 加密凭据 + localDir），无 agentId/tenantId | 后续如需多租户/多 agent 过滤，扩展 `SkillHubMiddlewareProperties` 或 Provider 实现内部从配置读取；当前不影响功能 |
| Provider 缓存与重试策略未固定 | FEAT-005 §5.2 不在 version-scope 固定下载缓存、分页、断点续传或本地落盘策略 | 由 Provider 实现自行治理 |