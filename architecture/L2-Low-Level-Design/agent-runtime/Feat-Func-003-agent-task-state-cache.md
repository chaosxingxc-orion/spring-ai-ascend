---
level: L2-LLD
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-003
status: active
dependency:
  - ../../../version-scope/FEAT-001-standardized-agent-service-entrypoint.md
  - ../../../version-scope/FEAT-004-remote-agent-orchestration.md
  - ./Feat-Func-001-standardized-agent-service-entrypoint.md
  - ./Feat-Func-004-remote-agent-orchestration.md
  - ../../L1-High-Level-Design/agent-runtime/README.md
  - ../../L1-High-Level-Design/agent-runtime/development.md
  - ../../L1-High-Level-Design/agent-runtime/spi-appendix.md
  - ../../../version-scope/FEAT-003-agent-task-state-cache.md
---

# 智能体任务状态缓存 — 设计文档

> 目标模块：`agent-runtime` 的 Redis 中间件接入层、A2A Task 存储和 Agent 状态持久化 Redis 使用方
> 最后更新：2026-07-10

---

## 1. 概述

### 1.1 特性定位

智能体任务状态缓存把 `agent-runtime` 内部对 Redis 的使用从具体客户端实现中解耦出来。当前版本承诺 A2A Task 存储和 Agent 执行 checkpoints 依赖 runtime Redis 缓存 SPI；原生 Redis 单机版、原生 Redis 集群版和客户封装 Redis 组件分别通过数据源策略或适配实现接入该抽象，并复用同一连接池和操作接口。后续能力如需使用 Redis，应经独立特性评审后复用该 SPI，不自动进入本设计范围。

- **解决的问题**：现场需要接入客户自封装 Redis 组件，但当前运行时 Redis 使用方不应直接依赖客户 JAR，也不应分散绑定到 Jedis、Lettuce 或其他具体客户端。
- **适用场景**：需要将 Redis 资源纳入客户统一管理和监控体系；需要在不同客户环境中通过配置切换 Redis 数据源；需要让 A2A Task 与 Agent 状态持久化复用同一 Redis 接入策略。

### 1.2 当前事实边界

本文描述 Feat-Func-003 在 `agent-runtime` 中的已接受架构方案。面向调用方的黑盒行为、验收标准和外部边界以 `version-scope/FEAT-003-agent-task-state-cache.md` 为准；模块级 SPI 原则、依赖方向和自动装配边界以 L1 设计及 SPI 附录为准。

本文是后续实现、测试和评审的目标态设计依据。代码实现如与本文不一致，应以本文定义的 SPI、配置模型、装配优先级、日志脱敏和验证策略为准进行修正。

### 1.3 设计原则

1. **统一抽象** — 已纳入本特性的 runtime Redis 使用方依赖同一操作接口，不直接依赖具体 Redis 客户端。
2. **统一配置** — 默认 Redis client 和客户自定义 Redis client 读取同一组 `openjiuwen.service.middleware.redis.<ref>` 配置，单机/集群切换只改配置。
3. **Bean 优先扩展** — 客户封装 Redis JAR 不进入产品仓库，开发者通过注册 `RuntimeRedisClient` Bean 覆盖默认 Bean；配置中不新增 `customer` 类型。
4. **日志可确认且脱敏** — 启动日志能确认当前策略和关键配置，但不输出密码、密文或凭据。
5. **最小命令面** — 只抽象当前 runtime 消费方需要的 Redis 操作，避免把 Redis 全命令集变成长期兼容负担。
6. **最小状态范围** — 当前版本只把 A2A Task 快照和 Agent checkpoints 作为 Redis-backed 状态缓存对象，不把进程内运行态注册表、取消句柄或其他非核心持久化概念纳入 Redis。

### 1.4 子特性全景

| 子特性 | 职责 | 关键抽象 | 设计定位 |
|---|---|---|---|
| Redis 配置模型 | 通过 checkpointer 类型和 redis-ref 引用命名 Redis endpoint，并在 endpoint 内声明单机或集群 | `MiddlewareProperties` | 配置契约 |
| Runtime Redis 操作接口 | 向 Redis 使用方暴露统一读写、TTL、删除、扫描等命令面 | Runtime Redis cache SPI | SPI 契约 |
| 默认原生 Redis Bean | 未提供自定义 Bean 且启用 Redis checkpointer 时，根据 endpoint type 创建单机或集群客户端 | Runtime Redis auto-configuration | 默认实现 |
| 客户 Redis 适配 Bean | 将客户封装 Redis 组件桥接到统一操作接口 | 自定义 `RuntimeRedisClient` Bean | 扩展实现 |
| Redis 使用方收敛 | A2A TaskStore、Agent checkpointer 等复用统一 Redis 门面 | Redis-backed runtime consumers | 使用方约束 |
| 状态缓存 TTL | 为 Redis-backed A2A Task 快照和 Agent checkpoints 设置统一过期时间 | `ttl-seconds` | 数据生命周期 |
| 启动日志与脱敏 | 输出当前 Redis client Bean、连接引用、endpoint type 和非敏感配置摘要 | Redis diagnostics | 运维契约 |

---

## 2. 特性规格

### 2.1 能力清单

| 能力 | 说明 |
|---|---|
| Redis checkpointer 开关 | `openjiuwen.service.middleware.checkpointer.type=redis` 启用 Redis 型运行时存储。 |
| 命名 Redis 连接引用 | `openjiuwen.service.middleware.checkpointer.redis-ref` 指向 `openjiuwen.service.middleware.redis.<ref>`；默认值为 `default`。 |
| 状态缓存 TTL | `openjiuwen.service.middleware.checkpointer.ttl-seconds` 控制 Redis-backed A2A Task 快照和 Agent checkpoints 的过期时间；默认 7 天（604800 秒）。 |
| Redis endpoint type | `redis.<ref>.type` 是新增可选配置，取值为 `standalone` 或 `cluster`；默认值为 `standalone`。 |
| 单机 Redis 配置 | `standalone` endpoint 使用 host、port、database、timeout-ms、encrypted-password。 |
| 集群 Redis 配置 | `cluster` endpoint 使用 nodes、timeout-ms、encrypted-password；nodes 支持多个 `host:port` seed node；database 不参与 cluster client 创建。 |
| Runtime Redis 缓存 SPI | 提供 get、set、setex、setnx、del、exists、expire、mget、scanIter 等当前 Task 与 checkpoint 缓存所需操作。 |
| 默认原生单机适配 | 未注册自定义 `RuntimeRedisClient` Bean 且 endpoint type 为 `standalone` 时，默认创建单机 Redis client。 |
| 默认原生集群适配 | 未注册自定义 `RuntimeRedisClient` Bean 且 endpoint type 为 `cluster` 时，默认创建原生 Redis Cluster client。 |
| 客户封装组件适配 | 客户 JAR 由现场或客户侧实现 `RuntimeRedisClient` Bean，并读取同一 Redis endpoint 配置。 |
| A2A Task 缓存复用 | Redis-backed TaskStore 依赖 `RuntimeRedisClient`，不直接创建具体客户端。 |
| Agent checkpoint 缓存复用 | Redis checkpointer 配置把 `RuntimeRedisClient` 注入 agent-core Redis connection map。 |
| TTL 写入约束 | Redis-backed TaskStore 和 checkpointer 默认使用带 TTL 的写入路径，或在写入后刷新同一 TTL。 |
| 策略日志 | 启动时输出当前 `RuntimeRedisClient` Bean 类型、连接引用、endpoint type、非敏感连接摘要和适配实现标识。 |
| 密码脱敏 | 日志和错误信息不得输出明文密码、加密密文或 token。 |
| 内部单机/集群验收 | 内部环境分别搭建原生 Redis 单机和集群并执行读写验证。 |
| 客户模式内部复现限制 | 客户 JAR 不能外传，内部只验证扩展点和默认模式；客户模式真实联调在客户或现场环境完成。 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|---|---|---|
| 内置工行 Redis JAR | 客户安全合规限制，JAR 不能外传 | 现场提供适配实现并注册到应用 |
| Redis 全命令 SPI | 命令面过大且长期兼容成本高 | 按 runtime 消费方需要扩展最小命令面 |
| Redis 数据迁移 | 数据迁移和 key schema 转换属于运维/交付活动 | 通过交付方案单独规划 |
| Redis 服务治理平台 | 客户已有统一治理和监控体系 | 通过客户 Redis 组件接入 |
| 运行时 Redis 指标体系 | 当前阶段 runtime 特性重心是统一接入和状态缓存，不定义命令级 metrics / DFX | 后续按独立 DFX 或客户平台接入需求扩展 |
| 运行中热切换 | 当前需求只要求修改配置后切换 | 配置变更后重启生效 |
| 运行时故障自动降级 | Redis 运行中断后的业务迁移属于系统工程和部署容灾问题 | 由整体部署、流量切换和 Redis 高可用方案承接 |
| 多租户动态数据源路由 | 当前需求不是按请求动态路由多 Redis | 单实例使用当前生效数据源策略 |
| 多租户 key 隔离 | 当前版本不做多租户特性，不定义 tenant 维度 key namespace | 多租户隔离需求由独立特性定义 |
| 跨逻辑 runtime keyspace 自动隔离 | SDK 不定义稳定的逻辑 runtime namespace，也不基于 `spring.application.name` 自动增加 key 前缀 | 开发者和部署方通过独立 endpoint、standalone 独立 database、由客户 Redis 适配器或接入代理统一增加 namespace，或其他等价方式隔离完整 keyspace |
| 复杂链路安全配置 | 当前版本支持密码认证和日志脱敏，不定义 TLS、证书或私有认证配置模型 | 由客户自定义 `RuntimeRedisClient` 或后续安全需求扩展 |
| 使用方级 Redis endpoint 拆分 | 当前版本 TaskStore 和 checkpointer 共用 `checkpointer.redis-ref` | 后续如需按能力拆分 Redis endpoint，再扩展配置模型 |
| Redis key schema 外部契约 | key pattern 和 value 序列化格式是 runtime 内部实现细节 | 运维不依赖具体格式做业务逻辑；迁移由 runtime 或交付方案处理 |

### 2.3 行为承诺

- **必须**：Redis 使用方只依赖统一操作接口，不直接依赖具体 Redis 客户端。
- **必须**：当前生效的 `RuntimeRedisClient` Bean、Redis endpoint type、Redis endpoint 配置与启动日志一致。
- **必须**：配置或适配缺失时给出明确错误，不静默切换到非预期数据源。
- **必须**：Redis-backed A2A Task 快照和 Agent checkpoints 默认具备 TTL，不产生无过期时间的状态缓存写入。
- **必须**：日志中不输出密码、密文或凭据。
- **允许**：客户适配实现由交付工程或客户工程在外部模块提供。
- **允许**：原生单机和原生集群使用不同底层客户端，只要上层操作语义一致。

---

## 3. 核心设计

### 3.1 逻辑分层

```
Redis 使用方
  ├─ A2A Task 存储
  ├─ Agent 状态持久化 / checkpointer
  └─ 独立特性纳入后的其他 Redis 型能力
        │
        ▼
Runtime Redis 操作接口
        │
        ├─ 原生 Redis 单机适配
        ├─ 原生 Redis 集群适配
        └─ 客户封装 Redis 适配
                │
                ▼
        Redis 服务或客户 Redis 治理组件
```

运行时只把 Redis 读写能力暴露为稳定操作接口。单机、集群和客户组件的连接、路由、池化、鉴权、监控接入和资源释放由各自适配层承接。

### 3.2 配置模型

目标态配置通过中间件配置启用 Redis，并通过 `redis-ref` 引用命名 Redis endpoint。endpoint 内部用 `type` 区分原生单机和原生集群；客户封装组件不通过 `type=customer` 表达，而是通过自定义 `RuntimeRedisClient` Bean 接管。

| 配置语义 | 说明 |
|---|---|
| `openjiuwen.service.middleware.checkpointer.type` | 当前 Redis TaskStore 和 agent-core checkpointer 共用该开关；取值为 `redis` 时启用 Redis 型运行时存储，默认是 `in_memory`。 |
| `openjiuwen.service.middleware.checkpointer.redis-ref` | 指向 `openjiuwen.service.middleware.redis.<ref>`；未配置时默认使用 `default`。 |
| `openjiuwen.service.middleware.checkpointer.ttl-seconds` | Redis-backed A2A Task 快照和 Agent checkpoints 的统一 TTL，单位秒，默认 7 天（604800 秒）。该值必须大于 0；当前版本不提供按使用方拆分 TTL 或复杂 eviction 策略。 |
| `openjiuwen.service.middleware.redis.<ref>.type` | Redis endpoint 类型。该字段为新增可选配置，取值为 `standalone` 或 `cluster`；默认值为 `standalone`。 |
| `openjiuwen.service.middleware.redis.<ref>.host` | 单机 Redis 主机名。`standalone` 模式使用该字段；未配置 type 且存在 host/port 时按 `standalone` 兼容解析。 |
| `openjiuwen.service.middleware.redis.<ref>.port` | 单机 Redis 端口，默认 6379。仅 `standalone` 模式使用该字段。 |
| `openjiuwen.service.middleware.redis.<ref>.nodes` | 集群 seed node 列表，`cluster` 模式必填，至少包含一个 `host:port`，推荐配置多个节点以避免单点入口失效。`cluster` 模式只使用 nodes，不从 host/port 推导集群节点。 |
| `openjiuwen.service.middleware.redis.<ref>.database` | Redis database 编号，默认 0。该字段是 standalone-only 兼容配置：`standalone` 模式继续生效并传给单机 Redis client；`cluster` 模式不参与 cluster client 创建，也不作为失败条件。 |
| `openjiuwen.service.middleware.redis.<ref>.timeout-ms` | 连接和读写超时时间，默认 3000。 |
| `openjiuwen.service.middleware.redis.<ref>.encrypted-password` | 加密后的 Redis 密码；由 `CredentialDecryptor` 解密后传给默认 Bean 或自定义 Bean。 |

默认 Bean 应按 endpoint type 选择客户端策略：`standalone` 创建单机 Redis client，`cluster` 创建 Redis Cluster client。客户封装组件、客户统一接入代理或其他第三方客户端通过自定义 `RuntimeRedisClient` Bean 接入，并读取同一 `MiddlewareProperties` 和 `redis-ref` 配置。这样默认实现和客户实现共享配置格式，业务使用方不感知底层是单机、集群还是客户组件。

配置兼容规则如下：

- 未配置 `type` 时，按 `standalone` 处理，必须保持现有 host、port、database、timeout-ms、encrypted-password 单机配置继续可用。
- 未配置 `ttl-seconds` 时，按 7 天（604800 秒）处理；配置值必须是大于 0 的整数。
- `type=standalone` 时，必须配置 host；port 可缺省为 6379；nodes 不参与单机客户端创建。
- `type=cluster` 时，必须配置 nodes；host/port 不参与集群客户端创建，避免把单机入口误认为集群节点；database 不参与 cluster client 创建，也不作为失败条件。
- `type=cluster` 且配置了非 0 database 时，启动诊断日志应输出非敏感 ignored 提示，说明该字段已被忽略。
- 自定义 `RuntimeRedisClient` Bean 可以读取同一 endpoint 配置，但也必须遵守“拓扑 type 只表达 standalone/cluster，不表达 customer”的公共配置语义。

### 3.3 Runtime Redis 操作接口

`RuntimeRedisClient` 定义在 `com.huawei.ascend.runtime.engine.spi`，是 Redis 使用方唯一依赖的命令门面。SPI 方法面如下：

```java
public interface RuntimeRedisClient extends AutoCloseable {
    Object get(String key);
    byte[] get(byte[] key);
    String set(String key, String value);
    String set(String key, byte[] value);
    String set(byte[] key, byte[] value);
    String setex(String key, long seconds, String value);
    String setex(byte[] key, long seconds, byte[] value);
    long setnx(String key, String value);
    long setnx(byte[] key, byte[] value);
    long del(String... keys);
    long del(byte[]... keys);
    boolean exists(String key);
    boolean exists(byte[] key);
    long expire(String key, long seconds);
    long expire(byte[] key, long seconds);
    List<Object> mget(String... keys);
    List<String> scanIter(String pattern);
}
```

该接口承载当前运行时需要的最小 Redis 命令面：

| 操作类型 | SPI 方法 | 语义 |
|---|---|---|
| 单 key 读取 | `get` | 按文本或二进制 key 读取值。 |
| 单 key 写入 | `set` | 写入文本或二进制值。 |
| TTL 写入 | `setex` | 写入值并设置过期时间。 |
| 条件写入 | `setnx` | key 不存在时写入，用于幂等或锁类场景。 |
| 删除 | `del` | 删除一个或多个 key。 |
| 存在性判断 | `exists` | 判断 key 是否存在。 |
| 过期时间刷新 | `expire` | 为已有 key 设置或刷新 TTL。 |
| 批量读取 | `mget` | 按多个文本 key 读取有序结果。 |
| 模式扫描 | `scanIter` | 按 pattern 扫描 key，用于 Task 列表等低频管理场景。 |

接口实现必须满足线程安全或明确由容器为并发使用提供隔离。客户封装组件如没有某项命令的原生支持，适配层应使用客户组件提供的等价能力实现；无法保证语义一致时应显式失败。

### 3.4 Bean 装配与默认实现

`RedisMiddlewareAutoConfiguration` 是 Redis client Bean 的装配入口。自动装配必须遵循 Bean 优先和 endpoint type 分发：

| 条件 | 设计含义 |
|---|---|
| `@ConditionalOnMissingBean(RuntimeRedisClient.class)` | 开发者提供自定义 `RuntimeRedisClient` Bean 时，默认 Bean 自动让位。 |
| `@ConditionalOnProperty(prefix = "openjiuwen.service.middleware.checkpointer", name = "type", havingValue = "redis")` | 只有启用 Redis checkpointer 时才创建默认 Redis client Bean。 |
| Redis client classpath 条件 | 默认单机和默认集群实现可分别声明底层客户端依赖条件；缺少依赖时应给出明确错误或不创建对应默认 Bean。 |

默认 Bean 的创建流程：

1. 读取 `MiddlewareProperties.getCheckpointer().getRedisRef()`，空值按 `default` 处理。
2. 通过 `RedisConnectionAssembler.resolveEndpoint(properties, redisRef)` 解析 `openjiuwen.service.middleware.redis.<ref>`。
3. 读取 endpoint `type`，空值按 `standalone` 处理。
4. 通过 `CredentialDecryptor` 解密 `encrypted-password`。
5. 当 type 为 `standalone` 时，使用 host、port、database、timeout-ms 创建默认单机 Redis client。
6. 当 type 为 `cluster` 时，使用 nodes、timeout-ms、encrypted-password 创建默认 Redis Cluster client；database 不参与创建。
7. 将底层客户端包装为 `RuntimeRedisClient` 暴露给 Redis 使用方。

默认 `standalone` 实现负责把 `RuntimeRedisClient` 方法映射到底层单机 Redis 命令，并实现 `close()` 释放底层连接池资源。默认 `cluster` 实现负责把同一 SPI 方法面映射到 Redis Cluster 客户端，并保证调用方不需要感知 slot 路由、节点选择或重定向细节。

### 3.5 开发者自定义 Redis client

客户封装 Redis、客户统一接入代理或第三方 Redis 客户端通过自定义 `RuntimeRedisClient` Bean 接入。自定义 Bean 应复用同一中间件配置，不引入 `type=customer` 配置。

```java
@Configuration
class CustomerRedisClientConfiguration {
    @Bean
    RuntimeRedisClient runtimeRedisClient(MiddlewareProperties properties, CredentialDecryptor decryptor) {
        String redisRef = properties.getCheckpointer().getRedisRef();
        MiddlewareProperties.RedisEndpoint endpoint =
            RedisConnectionAssembler.resolveEndpoint(properties, redisRef);
        String password = decryptor.decrypt(endpoint.getEncryptedPassword());
        return new CustomerRuntimeRedisClient(endpoint, password);
    }
}
```

自定义实现负责：

- 依赖客户提供的 Redis JAR、客户侧 SDK 或第三方 Redis client。
- 读取同一 `openjiuwen.service.middleware.redis.<ref>` 配置，解释 type、host、port、nodes、database、timeout-ms 和 encrypted-password。
- 将客户组件的读写、TTL、删除、扫描等能力映射到 `RuntimeRedisClient`。
- 接入客户的连接治理、监控、审计和安全策略。
- 避免把客户私有类型泄漏到 `engine.spi`、Redis 使用方或业务代码中。

客户 JAR 不进入产品仓库。产品提供的是稳定 SPI、统一配置、默认原生单机 Bean 和 Bean 覆盖机制；现场或客户侧提供具体客户适配 Bean。

### 3.6 Redis 使用方收敛

Redis-backed 运行时能力通过统一接口获取 Redis 能力：

| 使用方 | Redis 用途 | 设计要求 |
|---|---|---|
| A2A Task 存储 | 保存、读取、删除、列表查询 Task 快照 | 通过统一接口执行读写和扫描，不直接创建 Redis 客户端；写入快照时使用统一 TTL。 |
| Agent 状态持久化 | 保存和恢复 Agent 执行状态 checkpoint | 通过统一接口或由其注入的连接对象完成 Redis 操作；写入 checkpoint 时使用统一 TTL。 |
| 后续 Redis 型中间件 | 当前版本不承诺 | 新增能力如需使用 Redis，必须先经独立特性评审，再复用 `RuntimeRedisClient`，不得新增平行 Redis 客户端抽象。 |

当前版本不把进程内运行态注册表、流取消句柄、临时连接表或其他非核心持久化对象纳入 Redis-backed 使用方范围。

Task 存储和 checkpointer 可以有各自的数据结构和 key 前缀，但当前版本使用同一个 `ttl-seconds` 控制状态缓存生命周期，不提供按使用方拆分的 Redis endpoint 或复杂 eviction 策略。key 的责任边界如下：

- Redis-backed A2A TaskStore 的 key schema 和 value 序列化格式由 runtime 内部实现管理，不作为外部稳定契约。
- Agent checkpoint 及其他业务 key 的名称、前缀和结构由开发者定义；SDK 不写死开发者业务 key 的前缀。
- SDK 不为不同逻辑 runtime 自动生成 Redis key namespace，也不基于 `spring.application.name` 或其他 runtime 标识自动增加前缀。
- 不同逻辑 runtime 使用同一 Redis 服务时，开发者和部署方必须通过独立 endpoint、standalone 独立 database、由客户 Redis 适配器或接入代理统一增加 namespace，或其他等价方式保证完整 keyspace 隔离，避免 key 覆盖、`scanIter` 扫描串扰和运维归属不清。
- 同一逻辑 runtime 的多个部署副本可以按业务需要共享同一 keyspace；该场景不等同于不同逻辑 runtime 之间的隔离。

---

## 4. 代码结构

### 4.1 包结构建议

```
agent-runtime/src/main/java/com/huawei/ascend/runtime/
├── engine/spi/
│   └── RuntimeRedisClient                # runtime Redis 操作接口
├── engine/redis/
│   ├── MiddlewareProperties              # 中间件与 Redis 数据源配置
│   ├── StandaloneRuntimeRedisClient      # 默认原生 Redis 单机适配实现
│   ├── ClusterRuntimeRedisClient         # 默认原生 Redis 集群适配实现
│   ├── RedisClientFactory                # 默认单机/集群 Redis client 创建
│   ├── RedisConnectionAssembler          # 连接配置解析、校验与摘要构造
│   ├── RedisDatasourceDiagnostics        # 启动日志与脱敏诊断
│   ├── RedisTaskStore                    # A2A TaskStore 的 Redis 使用方
│   └── AgentCoreCheckpointerConfigAssembler # Agent 状态持久化的 Redis 使用方配置
└── boot/
    └── RedisMiddlewareAutoConfiguration  # RuntimeRedisClient 默认 Bean 自动装配
```

具体类名可随实现演进调整，但包职责和依赖方向应保持：公共 SPI 位于 `engine.spi`，默认适配和 Redis-backed 使用方位于 `engine.redis`，自动装配入口位于 `boot`，业务使用方只依赖 SPI。

### 4.2 依赖方向

```
boot / engine.a2a ─┐
                   ├─ depends on Runtime Redis SPI
engine.openjiuwen ─┘
        ▲
        │
engine.redis 提供默认 standalone / cluster RuntimeRedisClient 和 Redis-backed TaskStore
        ▲
        │
客户适配模块提供 Runtime Redis SPI 实现
```

- `engine.spi` 不依赖任何 Redis 客户端或客户 JAR。
- `engine.redis` 可以依赖默认原生 Redis 客户端，并负责 Redis-backed TaskStore 与 checkpointer 装配协作。
- 客户适配模块依赖客户 Redis JAR，但不把客户类型暴露给 `engine.spi` 或业务使用方。
- `boot`、`engine.a2a` 和具体框架适配器只依赖 Runtime Redis SPI，不直接创建 Redis 客户端。

---

## 5. 运行流程

### 5.1 启动装配流程

```
应用启动
  │
  ├─ 绑定 MiddlewareProperties
  ├─ 判断 checkpointer.type 是否为 redis
  │     └─ 否: 不创建默认 RuntimeRedisClient
  ├─ 查找显式注册的 RuntimeRedisClient Bean
  │     ├─ 存在: 使用客户、集群或第三方适配 Bean
  │     └─ 不存在: 进入默认 Redis client 创建
  ├─ 解析 checkpointer.redis-ref 指向的 redis.<ref> endpoint
  ├─ 根据 endpoint.type 创建默认客户端
  │     ├─ standalone: 使用 host/port/database
  │     └─ cluster: 使用 nodes，忽略 database
  ├─ 输出数据源策略日志（脱敏）
  ├─ 将 RuntimeRedisClient 注入 Redis 使用方
  │     ├─ A2A Task 存储
  │     └─ Agent 状态持久化 / checkpointer
  ▼
Runtime 就绪
```

### 5.2 读写流程

```
Redis 使用方
  │
  ├─ 调用 Runtime Redis 操作接口
  │
  ├─ 当前生效适配实现执行命令
  │     ├─ 原生 Redis 单机
  │     ├─ 原生 Redis 集群
  │     └─ 客户封装 Redis 组件
  │
  ▼
返回统一结果或明确异常
```

上层使用方不判断当前是单机、集群还是客户组件；差异在适配层内闭合。

如果多个不同逻辑 runtime 指向同一 Redis 服务，SDK 不会自动修改 TaskStore 或开发者业务 key。开发者和部署方必须在接入前完成 keyspace 隔离；未隔离时，当前版本不保证 Task 快照或 checkpoint 不被跨 runtime 覆盖，也不保证 pattern 扫描只返回当前 runtime 的 key。

### 5.3 错误、降级和诊断

| 场景 | 触发条件 | 行为 | 对外结果 |
|---|---|---|---|
| checkpointer 类型不支持 | 配置指定未知 checkpointer 类型 | 启动失败或 Redis 使用方创建失败 | 明确错误提示支持 `in_memory` 和 `redis`。 |
| Redis 连接引用缺失 | Redis 型能力启用但找不到连接定义 | 启动失败或 Redis 使用方创建失败 | 明确指出缺失的连接引用。 |
| TTL 配置非法 | `ttl-seconds` 小于等于 0 或不是整数 | 启动失败或 Redis 使用方创建失败 | 明确提示 `checkpointer.ttl-seconds` 必须为大于 0 的秒数。 |
| endpoint type 不支持 | `redis.<ref>.type` 不是 `standalone` 或 `cluster` | 启动失败或 Redis 使用方创建失败 | 明确提示支持的 endpoint type。 |
| cluster nodes 缺失 | `type=cluster` 但未配置 `nodes` | 启动失败或 Redis 使用方创建失败 | 明确提示 `redis.<ref>.nodes` 必填。 |
| cluster database 非 0 | `type=cluster` 且配置了非 0 database | 启动继续，database 不参与 cluster client 创建 | 输出非敏感 ignored 诊断提示。 |
| 客户适配未注册 | 现场期望客户组件接管，但未注册自定义 `RuntimeRedisClient` Bean | 默认 Bean 会按 endpoint type 创建原生客户端 | 运维日志必须能看出当前实际 Bean，避免误以为客户组件生效。 |
| 默认客户端缺依赖 | 启用 Redis 但默认客户端不可用，且没有自定义 Bean | Redis 使用方创建失败 | 不静默改用内存模式。 |
| 连接失败 | Redis 服务不可达或认证失败 | Redis 使用方初始化或首次操作失败 | 错误日志脱敏，提示连接摘要。 |
| 命令语义不支持 | 客户组件无法提供某项必要命令 | 适配层显式失败 | 调用方获得可诊断异常。 |
| 密码配置存在 | 配置包含密码或加密密码 | 日志仅输出密码已配置状态 | 不输出明文或密文。 |

当前版本不定义跨数据源自动降级。配置指定 Redis 时，系统不得因为 Redis 初始化失败而静默退回内存存储，除非调用方显式配置为内存模式。

---

## 6. 配置使用

### 6.1 配置语义示例

以下示例为目标态配置格式。默认原生 Redis client 和开发者自定义 Redis client 都读取这组配置。`type` 是新增可选配置，默认值为 `standalone`；未配置时必须保持现有 host/port/database/timeout-ms/encrypted-password 单机配置继续可用。新配置建议显式填写 `type`。

```yaml
openjiuwen:
  service:
    middleware:
      checkpointer:
        type: redis
        redis-ref: default
        ttl-seconds: 604800
      redis:
        default:
          type: standalone
          host: 127.0.0.1
          port: 6379
          database: 0
          timeout-ms: 3000
          encrypted-password: "${REDIS_PASSWORD_ENCRYPTED:}"
```

如果要切换到原生 Redis 集群，只修改 `redis-ref` 和对应 `redis.<ref>` 配置。集群配置应使用多个 seed node：

```yaml
openjiuwen:
  service:
    middleware:
      checkpointer:
        type: redis
        redis-ref: cluster
        ttl-seconds: 604800
      redis:
        cluster:
          type: cluster
          nodes:
            - 10.10.1.11:6379
            - 10.10.1.12:6379
            - 10.10.1.13:6379
          database: 0
          timeout-ms: 3000
          encrypted-password: "${REDIS_PASSWORD_ENCRYPTED:}"
```

上面的 `cluster` 是命名 endpoint，`type: cluster` 才是 endpoint 类型。原生 Redis Cluster 通常需要多个 seed node；至少配置一个可用节点，推荐配置多个节点以降低单节点不可达导致的启动风险。`cluster` 模式必须使用 `nodes`，不得只配置 `host` / `port` 来表示集群。`database` 在 cluster 模式下会被忽略；如果配置了非 0 database，启动诊断日志应提示该字段已被忽略。

如果现场接入客户封装 Redis 组件，配置仍使用相同 endpoint 结构；客户适配模块注册自定义 `RuntimeRedisClient` Bean 后，默认 Bean 自动让位：

```yaml
openjiuwen:
  service:
    middleware:
      checkpointer:
        type: redis
        redis-ref: icbc
        ttl-seconds: 604800
      redis:
        icbc:
          type: cluster
          nodes:
            - redis-gateway.icbc.example.com:6379
          database: 0
          timeout-ms: 3000
          encrypted-password: "${ICBC_REDIS_PASSWORD_ENCRYPTED:}"
```

客户配置中的 `type` 仍只表达 Redis 拓扑语义，不能写成 `customer`。客户组件的选择由 Spring Bean 装配决定。

以上配置只选择 Redis endpoint，不提供跨逻辑 runtime 的自动 key namespace。不同逻辑 runtime 共用同一 Redis 服务时，应使用独立 endpoint、standalone 独立 database、由客户 Redis 适配器或接入代理统一增加 namespace，或其他等价方式隔离完整 keyspace。Agent checkpoint 等业务 key 继续由开发者定义，SDK 不要求固定前缀。

### 6.2 启动日志要求

启动日志应包含：

- Redis connection ref。
- 状态缓存 TTL 秒数。
- Redis endpoint type：`standalone` 或 `cluster`。
- 当前生效的 `RuntimeRedisClient` Bean 类型，例如默认单机实现、默认集群实现或客户自定义实现。
- `standalone` 模式的 host、port、database、timeout。
- `cluster` 模式的 nodes 数量、节点摘要、timeout。
- `cluster` 模式下如配置了非 0 database，输出非敏感 ignored 提示。
- 密码是否配置的布尔摘要。

启动日志不得包含：

- 明文密码。
- `encrypted-password` 原文。
- token、key、证书内容。
- 客户私有鉴权材料。

---

## 7. 验证策略

### 7.1 单元测试

| 测试项 | 验证点 |
|---|---|
| checkpointer 类型解析 | 支持 `in_memory` 和 `redis`；未知类型报错。 |
| Redis endpoint 解析 | `redis-ref` 默认到 `default`，缺失命名 endpoint 时报错。 |
| TTL 配置解析 | 未配置 `ttl-seconds` 时默认 7 天（604800 秒）；配置小于等于 0 或非整数时报错。 |
| endpoint type 解析 | 未配置 type 时兼容为 `standalone`；支持 `standalone` 和 `cluster`；未知 type 报错。 |
| cluster nodes 校验 | `type=cluster` 时 nodes 必填，至少包含一个 `host:port`。 |
| cluster database 兼容 | `type=cluster` 且 database 非 0 时启动不失败，database 被忽略，并输出诊断提示。 |
| 默认原生单机适配装配 | `checkpointer.type=redis`、`type=standalone` 且未注册自定义 Bean 时创建单机 `RuntimeRedisClient`。 |
| 默认原生集群适配装配 | `checkpointer.type=redis`、`type=cluster` 且未注册自定义 Bean 时创建集群 `RuntimeRedisClient`。 |
| 客户适配优先级 | 已注册 `RuntimeRedisClient` Bean 时默认 Bean backs off。 |
| Redis 使用方依赖 | TaskStore 和 checkpointer 配置只依赖统一接口。 |
| TTL 写入 | TaskStore 和 checkpointer 写入 Redis 时使用 `setex` 或写入后 `expire`，TTL 取自 `ttl-seconds`。 |
| 命令映射 | get、set、setex、setnx、del、exists、expire、mget、scan 语义正确。 |
| 日志脱敏 | 启动日志和错误日志不包含明文密码或加密密文。 |

### 7.2 集成测试

| 环境 | 验证点 |
|---|---|
| 原生 Redis 单机版 | 使用默认单机 `RuntimeRedisClient` 时，A2A Task 存储和 Agent 状态持久化读写正常。 |
| 原生 Redis 集群版 | 使用默认集群 `RuntimeRedisClient` 和多个 cluster nodes 时，同一业务代码读写正常。 |
| 配置切换 | 单机 endpoint 与集群 endpoint 之间只改 `redis-ref` / `redis.<ref>` 配置即可切换。 |
| TTL 生效 | Redis-backed Task 快照和 checkpoint 写入后具备过期时间。 |
| 客户适配替身 | 使用内部 fake / stub `RuntimeRedisClient` Bean 验证客户模式装配链路和默认 Bean 让位行为。 |
| standalone 兼容配置 | 旧单机配置未配置 type 时仍可启动并使用 host/port/database/timeout-ms/encrypted-password。 |
| cluster database 忽略 | cluster 配置 database 非 0 时不失败，且有 ignored 诊断提示。 |

### 7.3 验收限制

客户封装 Redis JAR 因安全合规要求不能外传，内部环境不直接复现工行客户模式。内部验收的证据应来自：

- 原生 Redis 单机版读写验证。
- 原生 Redis 集群版读写验证。
- 配置切换无需改业务代码的验证。
- 客户适配扩展点可被替身实现注册并被使用的验证。
- 日志脱敏检查。

客户模式的真实联调在客户或现场环境完成。

---

## 8. 当前限制

| 限制 | 影响范围 | 临时方案 |
|---|---|---|
| 客户 JAR 不能进入内部仓库 | 内部无法真实复现工行组件行为 | 使用适配替身验证装配链路，客户环境完成真实联调。 |
| 统一接口只包含最小命令面 | 新 Redis 使用方可能需要新增命令 | 先评估是否为 runtime 通用需求，再扩展 SPI。 |
| 不支持运行中热切换 | 修改数据源后需重启应用 | 运维按配置变更流程重启。 |
| 不包含数据迁移 | 切换 Redis 数据源不会自动迁移旧数据 | 交付方案单独规划迁移或清理。 |
| 不提供复杂 eviction 策略 | 当前版本只提供统一 TTL | 如需 LRU、max-entries 或按租户配额，另立特性评审。 |

## 9. 后续实现要求

后续代码 PR 应按本文目标态补齐以下实现项：

| 实现项 | 要求 |
|---|---|
| 配置扩展 | 在 `MiddlewareProperties.RedisEndpoint` 中提供 `type` 和 `nodes`，并保持现有 host、port、database、timeout-ms、encrypted-password 向后兼容。 |
| TTL 配置扩展 | 在 `MiddlewareProperties.Checkpointer` 中提供 `ttl-seconds`，默认 7 天（604800 秒），并保持未配置时兼容。 |
| 配置校验 | `type` 仅允许 `standalone` 和 `cluster`；未配置时默认为 `standalone`；`ttl-seconds` 必须大于 0；`cluster` 模式下 nodes 必填且至少一个 `host:port`；database 不作为失败条件。 |
| 默认单机 client | 将 host、port、database、timeout-ms、encrypted-password 映射为线程安全的 `RuntimeRedisClient`。 |
| 默认集群 client | 新增默认 Redis Cluster client，将 nodes、timeout-ms、encrypted-password 映射为同一 `RuntimeRedisClient` 方法面，忽略 database。 |
| Bean 覆盖 | 继续使用 `@ConditionalOnMissingBean(RuntimeRedisClient.class)`，客户自定义 Bean 存在时默认单机/集群 Bean 不创建。 |
| Redis 使用方 | A2A TaskStore 和 agent-core checkpointer 只依赖 `RuntimeRedisClient`，不得直接创建单机或集群客户端；后续 Redis 使用方必须经独立特性评审后复用该 SPI。 |
| TTL 写入 | A2A TaskStore 和 agent-core checkpointer 写入 Redis 状态缓存时必须使用 `setex` 或写入后 `expire`，TTL 取自 `checkpointer.ttl-seconds`。 |
| 启动日志 | 输出 redis-ref、ttl-seconds、endpoint type、实际 `RuntimeRedisClient` Bean、非敏感连接摘要、密码是否配置；cluster 模式下如 database 非 0，输出 ignored 提示；不得输出明文密码或 encrypted-password 原文。 |
| 测试 | 覆盖单机默认 Bean、旧 standalone 配置无 type 兼容、ttl-seconds 默认值和非法值、Redis 写入 TTL 生效、集群默认 Bean、多节点配置校验、cluster database 非 0 不失败但被忽略且有诊断提示、客户自定义 Bean 覆盖、日志脱敏、TaskStore 和 checkpointer 复用 SPI。 |
