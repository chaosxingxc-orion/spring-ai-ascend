---
level: L2-LLD
module: agent-core
feature_type: functional
feature_id: Feat-Func-003
status: active
updated: 2026-07-28
dependency:
  - ../../../version-scope/FEAT-003-agent-task-state-cache.md
  - ../../L1-High-Level-Design/agent-core/README.md
  - ../../L1-High-Level-Design/agent-core/overview.md
  - ../../L1-High-Level-Design/agent-core/development.md
  - ../../L1-High-Level-Design/agent-core/spi-appendix.md
  - ../agent-runtime/Feat-Func-003-agent-task-state-cache.md
---

# DeepAgent Todolist 分布式存储 — 设计文档

> 目标模块：`agent-core` 的 DeepAgent Todolist 状态存储 SPI 及 KV-backed 实现
> 最后更新：2026-07-28

---

## 1. 概述

### 1.1 特性定位

FEAT-003 在 agent-core 侧定义 DeepAgent Todolist 的状态存储抽象，使 Todolist 的 save/load 不再绑定到本地文件系统，而是通过可插拔的 `TodoStorage` SPI 支持文件存储和 KV 存储两种后端。agent-core 内置了完整的 Redis 后端实现（`RedisStore` + `RedisKVStoreProvider`），可通过配置直接创建 Redis 连接，无需依赖外部模块。同时提供 `DeepAgent.setKvStore()` / `DeepAgentConfig.kvStoreConfig` 作为注入接缝，允许外部传入已有的 `BaseKVStore` 实例以复用连接。

- **解决的问题**：DeepAgent 的 Todolist 规划状态需要与本地文件系统解耦，支持通过 KV 存储后端（如 Redis）实现持久化。
- **适用场景**：
  - 单机开发/测试场景使用默认文件存储，零配置即可运行。
  - 独立使用 agent-core 时，通过 `kvStoreConfig` 配置 Redis 连接即可启用 KV 后端。
  - 集成部署时，外部可将已有 `BaseKVStore` 实例注入 `DeepAgent` 以复用已有连接。

### 1.2 模块内职责边界

FEAT-003 在 agent-core 侧的 DeepAgent Todolist 存储能力为模块自闭环实现，不依赖 agent-runtime。agent-core 提供完整的 TodoStorage SPI、BaseKVStore SPI 以及 Redis 后端实现，外部调用方按需通过配置或编程接口注入存储实例。

| 关注点 | agent-core 自闭环实现 | 说明 |
|--------|----------------------|------|
| TodoItem 领域模型 | `TodoItem`、`TodoStatus` 定义 schema、状态机、CRUD 语义 | 序列化为 JSON，外部按 opaque payload 读写 |
| Todolist 存储 SPI | `TodoStorage` / `TodoStorageProvider` / `TodoStorageFactory` | 内置 `"file"` 和 `"kv"` 两种 provider，ServiceLoader 发现 |
| KV 存储 SPI | `BaseKVStore` / `KVStoreProvider` / `KVStoreFactory` | 内置 `InMemoryKVStore`、`RedisStore`，ServiceLoader 发现 |
| Redis 后端 | `RedisStore` 通过反射适配多种 Redis 客户端（Jedis/Lettuce/Redisson），`RedisKVStoreProvider` 支持配置驱动创建单机或集群 Redis 连接 | 无需外部注入即可独立工作 |
| KV store 注入接缝 | `DeepAgent.setKvStore()` / `DeepAgentConfig.kvStoreConfig` | 允许外部注入已有 `BaseKVStore` 实例以复用连接 |
| 多租户 key 隔离 | `TenantKVStoreKeyResolver` 基于 `TenantContext` 为 KV key 添加租户前缀 | 非租户场景下原样返回 key |

### 1.3 设计原则

1. **存储后端可插拔** — Todolist 通过 `TodoStorage` SPI 支持 file 和 kv 两种后端，切换只需改配置。
2. **KV 存储链路复用** — `KvTodoStorage` 依赖 `BaseKVStore` SPI，同一 `RedisStore` 实例可被多个消费方（Todolist、checkpointer 等）共用。
3. **不透明 payload** — TodoItem 序列化/反序列化由 agent-core 完成，外部调用方按 opaque JSON 读写即可。
4. **Session 级隔离** — KV key 以 sessionId 为隔离粒度（sessionId 对应 Task 维度），继承租户前缀。同一 session 下的子任务共享同一 Todolist key，不提供子任务级物理隔离。
5. **文件存储为默认开发体验** — 单机开发/测试场景默认 `file` 存储，零配置即可运行。

---

## 2. 能力清单与排除

### 2.1 能力清单

| 能力 | 说明 |
|------|------|
| TodoStorage SPI | 定义 `load(sessionId)`、`save(sessionId, todos)`、`delete(sessionId)` 接口，支持带租户上下文重载。 |
| TodoStorageProvider SPI | 定义 `typeName()` + `create(conf)` 工厂接口，通过 ServiceLoader 发现。 |
| TodoStorageFactory | 基于 ServiceLoader 的注册表，支持程序化注册。 |
| FileTodoStorage | 文件系统实现：`{workspace}/{sessionId}/todo.json`，序列化为 JSON。 |
| KvTodoStorage | KV 存储实现：key=`{sessionId}:todo`，值=TodoItem[] JSON，委托给 `BaseKVStore`。 |
| KvTodoStorage 共享 KV store 注入 | 支持 `sharedKvStore` 配置项接收外部注入的 `BaseKVStore`，优先于自建。 |
| DeepAgentConfig.todoStorageType | 配置项，取值 `file`（默认）或 `kv`，控制 Todolist 存储后端选择。 |
| TaskPlanningRail Todolist 初始化 | 在 `init()` 中根据 `todoStorageType` 创建 `TodoStorage` 和 `TodoTool`，注入 DeepAgent。 |
| TodoTool | 封装 `TodoStorage`，提供 create/list/get/modify 工具操作。 |
| 多租户 key 隔离 | `TenantKVStoreKeyResolver.resolveKey(rawKey)` 根据当前 `TenantContext` 为 KV key 添加租户命名空间前缀。 |
| RedisStore | Redis-backed `BaseKVStore`，通过反射适配多种 Redis 客户端（Jedis/Lettuce/Redisson），支持单机和集群模式。 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| TodoItem 跨 Task 共享 | Todolist 是 task-scoped 概念，跨 Task 共享无业务语义 | 每个 Task 独立 sessionId 和 TodoStorage |
| agent-core 独立 Redis 连接管理 | agent-core 独立样例/非生产用法使用文件存储或测试用 InMemoryKVStore | 通过 `kvStoreConfig` 配置 Redis 连接或调用 `setKvStore()` 注入外部 KV store |
| 文件存储用于分布式场景 | 多实例共享文件系统不做为架构承诺 | 分布式场景使用 kv 后端 |
| Todolist 执行过程完整异常恢复 | 当前版本只承诺 Task 边界缓存和回灌 | 边界缓存 + 执行期自治 save/load |
| TodoStorage 跨后端迁移 | 切换 file/kv 时旧数据不自动迁移 | 新 Task 使用新后端，旧 Task 数据随 TTL 自然过期 |

### 2.3 行为承诺

- **必须**：`todoStorageType=kv` 时，Todolist 通过 `BaseKVStore` 完成读写，不依赖文件系统。
- **必须**：`KvTodoStorage` 的 key 格式为 `{tenantPrefix:}{sessionId}:todo`，提供 Session 级隔离（sessionId 对应 Task 维度，同一 session 下的子任务共享同一 key）。
- **必须**：`KvTodoStorageProvider` 支持接收外部 `sharedKvStore` 注入，优先于自建 KV store。
- **必须**：`FileTodoStorage` 的路径为 `{workspace}/{sessionId}/todo.json`，保持向后兼容。
- **允许**：未配置 `todoStorageType` 时，默认使用 `file` 后端。
- **允许**：agent-core 独立样例/测试使用 `InMemoryKVStore` 作为 kv 后端的测试替身。

---

## 3. 核心设计

### 3.1 逻辑分层

```
TodoTool (工具层)
  │
  ▼
TodoStorage (SPI)
  ├── FileTodoStorage ────── 本地文件系统
  │     └── {workspace}/{sessionId}/todo.json
  │
  └── KvTodoStorage ──────── BaseKVStore (SPI)
        ├── InMemoryKVStore (测试/开发)
        ├── RedisStore (单机/集群 Redis)
        └── 外部注入的共享 KV store
              │
              ▼
        Redis / 其他 KV 存储服务
```

### 3.2 TodoStorage SPI

`TodoStorage` 是 Todolist 状态存储的统一接口，定义在 `com.openjiuwen.harness.tools`：

```java
public interface TodoStorage {
    List<TodoItem> load(String sessionId) throws IOException;
    void save(String sessionId, List<TodoItem> todos) throws IOException;
    void delete(String sessionId) throws IOException;

    // 带租户上下文的重载 — 在方法内设置/清理 TenantContext
    default List<TodoItem> load(String sessionId, TenantContext tenantContext) throws IOException;
    default void save(String sessionId, List<TodoItem> todos, TenantContext tenantContext) throws IOException;
    default void delete(String sessionId, TenantContext tenantContext) throws IOException;
}
```

`sessionId` 作为存储隔离的主键。在多租户场景下，带 `TenantContext` 的重载在调用前后设置/清理线程级租户上下文，使底层 `BaseKVStore` 的 key 解析器能获取当前租户信息。

### 3.3 TodoStorageProvider SPI 与 Factory

```java
public interface TodoStorageProvider {
    String typeName();                        // "file" 或 "kv"
    TodoStorage create(Map<String, Object> conf);
}

public final class TodoStorageFactory {
    // 静态初始化：ServiceLoader.load(TodoStorageProvider.class)
    public static void register(String type, TodoStorageProvider provider);
    public static TodoStorage create(String type, Map<String, Object> conf);
    public static boolean hasProvider(String type);
}
```

内置两种 provider：

| typeName | Provider 类 | 创建行为 |
|----------|------------|---------|
| `"file"` | `FileTodoStorageProvider` | 从 `conf.basePath` 读取路径，创建 `FileTodoStorage` |
| `"kv"` | `KvTodoStorageProvider` | 优先使用 `conf.sharedKvStore` 注入的 BaseKVStore；否则从 `conf.kvStoreType` 和 `conf.kvStoreConf` 通过 `KVStoreFactory.create()` 自建 |

第三方可通过 ServiceLoader (`META-INF/services/com.openjiuwen.harness.tools.TodoStorageProvider`) 或程序化 `TodoStorageFactory.register()` 注册自定义 provider。

### 3.4 FileTodoStorage

文件系统实现，数据文件路径为 `{workspace}/{sessionId}/todo.json`：

```java
public class FileTodoStorage implements TodoStorage {
    private final Path workspace;
    private final TenantWorkspaceResolver workspaceResolver;

    public FileTodoStorage(Path workspace);
    public FileTodoStorage(Path workspace, TenantWorkspaceResolver workspaceResolver);

    // load:  读取 {workspace}/{sessionId}/todo.json → TodoItem[]
    // save:  写入 {workspace}/{sessionId}/todo.json
    // delete: 递归删除 {workspace}/{sessionId}/
}
```

多租户模式下，通过 `TenantWorkspaceResolver` 将 workspace 解析为租户隔离目录。

### 3.5 KvTodoStorage

KV 存储实现，委托给 `BaseKVStore`：

```java
public class KvTodoStorage implements TodoStorage {
    private final BaseKVStore kvStore;

    public KvTodoStorage(BaseKVStore kvStore);

    private String buildKey(String sessionId) {
        String rawKey = sessionId + ":todo";
        return TenantKVStoreKeyResolver.resolveKey(rawKey);
    }

    // load:   kvStore.get(key) → 反序列化 JSON → List<TodoItem>
    // save:   kvStore.set(key, JsonUtils.safeJsonDumps(todos))
    // delete: kvStore.delete(key)
}
```

关键设计决策：

- **key 格式**：`{tenantPrefix:}{sessionId}:todo`，其中 `tenantPrefix:` 由 `TenantKVStoreKeyResolver` 根据当前线程的 `TenantContext` 动态添加。隔离粒度为 session 级（sessionId 对应 Task 维度），同一 session 下的子任务共享同一 key；不提供子任务级物理隔离。
- **值格式**：TodoItem[] 的 JSON 序列化，使用 `JsonUtils.safeJsonDumps/safeJsonLoads`。
- **空值语义**：key 不存在或值为空时，`load()` 返回空列表。
- **不设置 TTL**：KvTodoStorage 本身不设置 key 过期时间；TTL 由 Redis 侧或外部调用方统一管理。

### 3.6 BaseKVStore SPI 与 RedisStore

`BaseKVStore` 是 agent-core 定义的 KV 存储抽象基类：

```java
public abstract class BaseKVStore implements AutoCloseable {
    public abstract void set(String key, Object value);
    public abstract Object get(String key);
    public abstract void delete(String key);
    public abstract boolean isExists(String key);
    public abstract boolean exclusiveSet(String key, Object value, Integer expiry);
    public abstract Map<String, Object> getByPrefix(String prefix);
    public abstract void deleteByPrefix(String prefix, Integer batchSize);
    public abstract List<Object> mget(List<String> keys);
    public abstract int batchDelete(List<String> keys, Integer batchSize);
    public abstract KVStorePipeline pipeline();
    public void close();  // 默认 no-op
}
```

`RedisStore` 是 Redis-backed 实现，通过反射适配多种 Redis 客户端：

```java
public class RedisStore extends BaseKVStore {
    private final Object redisClient;   // Jedis / Lettuce / Redisson 实例
    private final boolean isCluster;     // 自动检测

    public RedisStore(Object redisClient);
    // 通过反射调用 redisClient 的 set/get/del/exists/expire/scan 等方法
}
```

`RedisKVStoreProvider` 是 ServiceLoader provider，typeName 为 `"redis"`，支持通过配置或直接注入 `redis_client` 创建 `RedisStore`。

### 3.7 多租户 key 隔离

```java
public class TenantKVStoreKeyResolver {
    private static TenantNamespaceFactory globalNamespaceFactory;

    public static String resolveKey(String originalKey) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        return globalNamespaceFactory.namespace(ctx, originalKey);
    }
}
```

`TenantNamespaceFactory` 的策略：当 `TenantContext` 存在且租户感知时，将 `originalKey` 包装为 `{tenantId}:{originalKey}`；非租户场景下原样返回。`KvTodoStorage.buildKey()` 在每次读写时调用 `TenantKVStoreKeyResolver.resolveKey()`，确保多租户部署中不同租户的 Todolist 数据物理隔离。

---

## 4. 代码结构

### 4.1 包结构

```
agent-core-java/src/main/java/com/openjiuwen/
├── harness/tools/
│   ├── TodoStorage.java              # Todolist 存储 SPI
│   ├── TodoStorageProvider.java      # SPI 工厂接口
│   ├── TodoStorageFactory.java       # ServiceLoader 注册表
│   ├── TodoItem.java                 # TodoItem 领域模型
│   ├── TodoStatus.java               # 状态枚举
│   ├── TodoTool.java                 # 工具封装（create/list/get/modify）
│   ├── FileTodoStorage.java          # 文件系统实现
│   ├── FileTodoStorageProvider.java  # type="file" provider
│   ├── KvTodoStorage.java            # KV 存储实现
│   └── KvTodoStorageProvider.java    # type="kv" provider
│
├── harness/rails/
│   └── TaskPlanningRail.java         # DeepAgent rail，初始化 TodoTool
│
├── harness/schema/config/
│   └── DeepAgentConfig.java          # todoStorageType / kvStoreConfig 配置
│
├── harness/deep_agent/
│   └── DeepAgent.java                # kvStore getter/setter
│
├── spi/store/
│   ├── BaseKVStore.java              # KV 存储抽象基类
│   ├── KVStoreProvider.java          # KV store SPI 工厂
│   ├── KVStoreFactory.java           # ServiceLoader 注册表
│   └── KVStorePipeline.java          # 批量操作 pipeline
│
├── extensions/store/kv/
│   ├── RedisStore.java               # Redis-backed BaseKVStore
│   └── RedisKVStoreProvider.java     # type="redis" provider
│
├── core/multitenant/
│   ├── TenantKVStoreKeyResolver.java # KV key 租户前缀解析
│   ├── TenantContext.java            # 租户上下文
│   ├── TenantContextHolder.java      # 线程级 TenantContext holder
│   └── TenantNamespaceFactory.java   # 命名空间工厂
│
└── core/foundation/store/kv/
    └── InMemoryKVStore.java          # 内存 KV store（测试/开发）
```

### 4.2 依赖方向

```
TaskPlanningRail (harness/rails)
  └── TodoStorageFactory (harness/tools)
        ├── FileTodoStorageProvider → FileTodoStorage
        └── KvTodoStorageProvider → KvTodoStorage
              └── BaseKVStore (spi/store)
                    ├── InMemoryKVStore (foundation/store/kv)
                    └── RedisStore (extensions/store/kv)

TodoTool (harness/tools)
  └── TodoStorage (harness/tools)
```

- `harness/tools` 定义 SPI 和内置实现，不依赖任何具体存储后端。
- `spi/store` 定义 `BaseKVStore` SPI，不依赖 Redis 或其他客户端。
- `extensions/store/kv` 提供 Redis 实现，可选依赖 Jedis/Lettuce/Redisson（通过反射解耦）。
- `harness/rails` 负责组装，根据配置选择具体实现。

---

## 5. 运行流程

### 5.1 DeepAgent 启动时 Todolist 存储初始化

```
DeepAgent 构造 / HarnessFactory.createDeepAgent()
  │
  ├─ DeepAgentConfig.todoStorageType 决定存储后端
  │     ├─ "file" (默认) → FileTodoStorage
  │     └─ "kv"         → KvTodoStorage
  │
  ├─ DeepAgent.ensureInitialized()
  │     └─ 初始化 config.rails
  │
  └─ TaskPlanningRail.init(deepAgent)
        ├─ 读取 deepAgent.getConfig().getTodoStorageType()
        ├─ resolveTodoStorage(deepAgent, todoStorageType):
        │     ├─ 如果 TodoStorageFactory.hasProvider(type):
        │     │     └─ buildTodoStorageConfig() → TodoStorageFactory.create()
        │     │           ├─ type="file": conf.basePath = workspace/.todo
        │     │           └─ type="kv":
        │     │                 ├─ 优先: deepAgent.getKvStore() 不为空
        │     │                 │     → conf.sharedKvStore = kvStore
        │     │                 └─ 否则: deepAgent.getConfig().getKvStoreConfig()
        │     │                       → conf.kvStoreConf = kvConfig
        │     └─ 否则: 降级为 FileTodoStorage(workspace/.todo)
        │
        ├─ new TodoTool(todoStorage)
        └─ 注册 todo_create / todo_list / todo_get / todo_modify 工具到 DeepAgent
```

### 5.2 Todolist 执行期读写流程

```
Agent 调用 todo_create / todo_modify / todo_list / todo_get
  │
  ▼
TodoTool.create(sessionId, tasks) / modify(sessionId, updates) / list(sessionId) / get(sessionId)
  │
  ├─ load(sessionId)  → TodoStorage.load(sessionId)
  ├─ 业务逻辑（状态校验、in_progress 约束、依赖检查）
  └─ save(sessionId, todos) → TodoStorage.save(sessionId, todos)
        │
        ▼
  ┌─ FileTodoStorage ──────────────────────────────┐
  │  读取/写入 {workspace}/{sessionId}/todo.json    │
  └────────────────────────────────────────────────┘
  ┌─ KvTodoStorage ────────────────────────────────┐
  │  buildKey(sessionId) → TenantKVStoreKeyResolver│
  │    .resolveKey("{sessionId}:todo")             │
  │  kvStore.get(key) / kvStore.set(key, json)     │
  │    │                                            │
  │    ▼                                            │
  │  BaseKVStore (InMemoryKVStore / RedisStore /    │
  │              外部注入的共享 KV store)          │
  └────────────────────────────────────────────────┘
```

---

## 6. 关键数据对象与映射

### 6.1 TodoItem 领域模型

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String (UUID) | 唯一标识，创建时自动生成 |
| `content` | String | 任务内容描述 |
| `activeForm` | String | 执行时的进行时描述 |
| `description` | String | 任务详细描述 |
| `status` | TodoStatus | PENDING / TODO / IN_PROGRESS / COMPLETED / DONE / CANCELLED |
| `dependsOn` | List\<String\> | 依赖的前置任务 ID 列表 |
| `resultSummary` | String | 完成后的结果摘要 |
| `metaData` | Map\<String, Object\> | 扩展元数据 |
| `selectedModelId` | String | 指定执行该任务的模型 ID |
| `priority` | String | 优先级：low / medium / high |

**状态机**：

```
PENDING ──→ IN_PROGRESS ──→ COMPLETED / DONE
  │              │
  └──→ CANCELLED ←──┘
```

约束：同一时刻只有一个任务处于 IN_PROGRESS。

### 6.2 TodoItem JSON 序列化格式

```json
[
  {
    "id": "uuid-1",
    "content": "实现用户登录接口",
    "activeForm": "正在实现用户登录接口",
    "description": "包括 JWT token 生成和验证",
    "status": "IN_PROGRESS",
    "depends_on": [],
    "result_summary": null,
    "meta_data": {},
    "selected_model_id": null,
    "priority": "high"
  }
]
```

序列化时 `dependsOn` → `depends_on`，`resultSummary` → `result_summary`，`metaData` → `meta_data`，`selectedModelId` → `selected_model_id`。反序列化时同时支持驼峰和下划线命名。

### 6.3 KV 存储 key 映射

| 场景 | rawKey | TenantContext | 最终 key |
|------|--------|---------------|----------|
| 无租户 | `session-123:todo` | null / 非租户感知 | `session-123:todo` |
| 有租户 | `session-123:todo` | tenantId=tenant-A | `tenant-A:session-123:todo` |

### 6.4 配置到 TodoStorage 的映射

| 配置路径 | 取值 | 创建的 TodoStorage |
|----------|------|--------------------|
| `DeepAgentConfig.todoStorageType` | `"file"` (默认) | `FileTodoStorage(workspace/.todo)` |
| `DeepAgentConfig.todoStorageType` | `"kv"` + `DeepAgent.kvStore` 不为空 | `KvTodoStorage(sharedKvStore)` |
| `DeepAgentConfig.todoStorageType` | `"kv"` + `DeepAgent.kvStore` 为空 + `kvStoreConfig` | `KvTodoStorage(KVStoreFactory.create(kvStoreType, kvStoreConf))` |

---

## 7. 配置设计

### 7.1 DeepAgent 配置项

```java
@Data
@Builder
public class DeepAgentConfig {
    @Builder.Default
    private String todoStorageType = "file";    // "file" 或 "kv"
    private Map<String, Object> kvStoreConfig;   // kv 后端时的 KVStoreFactory 配置
    // ... 其他配置
}
```

### 7.2 配置语义

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `todoStorageType` | String | `"file"` | Todolist 存储后端类型。`"file"` 使用文件系统；`"kv"` 使用 KV 存储。 |
| `kvStoreConfig.kvStoreType` | String | `"in_memory"` | 当 `todoStorageType=kv` 且未注入共享 KV store 时，用于 `KVStoreFactory.create()` 的 type 参数。 |
| `kvStoreConfig.*` | Map | - | 传递给 `KVStoreProvider.create(conf)` 的配置。对于 `"redis"` 类型，可包含 host/port/password/cluster/redis_client。 |

---


## 8. 验证策略

### 8.1 单元测试

| 测试项 | 验证点 |
|--------|--------|
| `FileTodoStorage` 读写删除 | 写入 TodoItem 列表到文件后再读取，数据一致；删除后文件不存在。 |
| `FileTodoStorage` 空值处理 | load 不存在的 sessionId 时返回空列表。 |
| `KvTodoStorage` 读写删除 | 使用 `InMemoryKVStore` 作为后端，验证 set/get/delete 正确。 |
| `KvTodoStorage` 空值处理 | KV store 中无对应 key 时返回空列表。 |
| `KvTodoStorage` JSON 序列化 | TodoItem 列表 JSON 序列化/反序列化后字段一致。 |
| `TodoStorageFactory` 内置 provider | type="file" 和 type="kv" 可成功创建 TodoStorage。 |
| `TodoStorageFactory` 自定义 provider | 程序化注册后 hasProvider/create 正确。 |
| `KvTodoStorageProvider` 共享 store 优先 | conf 同时包含 sharedKvStore 和 kvStoreConf 时，使用 sharedKvStore。 |
| `TenantKVStoreKeyResolver` 前缀 | 有租户上下文时 key 添加 `{tenantId}:` 前缀；无租户时原样返回。 |
| `TaskPlanningRail` TodoTool 初始化 | todoStorageType=file 时创建 FileTodoStorage；todoStorageType=kv 时创建 KvTodoStorage。 |
| `TodoTool.fromConfig` 工厂方法 | 根据 storageType 创建正确的 TodoTool 实例。 |

### 8.2 集成测试

| 环境 | 验证点 |
|------|--------|
| 文件模式 + DeepAgent | DeepAgent 使用文件后端，Todolist 在 Task loop 中正常读写。 |
| KV 模式 + InMemoryKVStore + DeepAgent | DeepAgent 使用 KV 后端，Todolist 在 Task loop 中正常读写。 |
| KV 模式 + Redis + DeepAgent | 通过 `kvStoreConfig` 配置或 `setKvStore()` 注入 RedisStore 作为后端，Todolist 读写正常。 |
| 多租户隔离 | 不同 tenantId 的 Task 使用不同的 KV key 前缀，数据物理隔离。 |
| 文件到 KV 切换 | todoStorageType 从 file 改为 kv 后，新 Task 使用新后端，旧文件不受影响。 |

### 8.3 测试替身

- `InMemoryKVStore`：agent-core 内置的内存 KV store，用于单元测试和独立样例。
- `FileTodoStorage`：测试文件后端的基本读写行为，验证 JSON 序列化兼容性。
- 自定义 `TodoStorageProvider` + `KVStoreProvider`：验证 SPI 注册表正确性。

---

## 9. 当前限制

| 限制 | 影响范围 | 临时方案 |
|------|----------|----------|
| KvTodoStorage 不设置 TTL | 历史 Todolist 数据可能长期占用 Redis 空间 | 由 Redis 侧统一管理 key TTL |
| 无跨后端数据迁移 | 切换 file/kv 时旧 Task 的 Todolist 不会自动迁移 | 新 Task 使用新后端；旧 Task 数据随 TTL 自然过期 |
| JSON 序列化不支持二进制 TodoItem | TodoItem 中 meta_data 等字段仅支持 JSON 可表达类型 | 复杂对象通过 JSON 字符串或 Base64 编码存入 meta_data |
