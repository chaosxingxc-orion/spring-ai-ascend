# 07. agent-service L2 Session Manager

## 1. 职责

- 定义并维护 Session 聚合对象
- 按 `tenantId + sessionId` 创建、加载和更新 Session
- 维护 Session 内的消息历史、会话状态和 Task 占位列表
- 向其它层提供 Session 相关公共方法
- 通过 `SessionStore` 抽象 Session 存储，避免上层依赖内存、数据库、Redis 等具体实现，并预留版本号/乐观锁语义
- Task 在当前阶段只保留占位对象，不实现 Task 状态机、checkpoint、调度、执行和恢复逻辑

---

## 2. 包结构

```text
service/
  sessionmanage/
    api/
      SessionManager.java
    core/
      SessionManagerImpl.java
    config/
      SessionManageConfiguration.java
      SessionManageProperties.java
      DefaultSessionStoreFactory.java
    model/
      Session.java
      SessionKey.java
      SessionMessage.java
      SessionMessageRole.java
      SessionContentPart.java
      Task.java
    store/
      SessionStore.java
      SessionStoreFactory.java
      memory/
        InMemorySessionStore.java
        SessionNotFoundException.java
      redis/
        RedisSessionStore.java
        RedisSessionStoreProperties.java
        RedisSessionCommands.java
        SessionCodec.java
        JacksonSessionCodec.java
```

---

## 3. 公共接口

`SessionManager` 是 L2 暴露给其它内部调用方的主入口。其它层不直接访问 `SessionStore`，也不依赖具体存储实现。

```java
public interface SessionManager {
    Session loadOrCreate(String tenantId, String userId, String agentId, String sessionId);
    Optional<Session> get(String tenantId, String sessionId);
    boolean exists(String tenantId, String sessionId);
    Session appendMessage(String tenantId, String sessionId, SessionMessage message);
    Session putState(String tenantId, String sessionId, String key, Object value);
    Session removeState(String tenantId, String sessionId, String key);
    Session putMetadata(String tenantId, String sessionId, String key, Object value);
    Session removeMetadata(String tenantId, String sessionId, String key);
    Session appendTask(String tenantId, String sessionId, Task task);
    void delete(String tenantId, String sessionId);
}
```

| 方法 | 入参 | 返回值 | 描述 |
|---|---|---|---|
| `loadOrCreate` | `tenantId, userId, agentId, sessionId` | `Session` | 按租户和会话 ID 加载 Session；不存在则创建。 |
| `get` | `tenantId, sessionId` | `Optional<Session>` | 只读查询 Session；不存在时返回空。 |
| `exists` | `tenantId, sessionId` | `boolean` | 判断 Session 是否存在；用于轻量存在性检查。 |
| `appendMessage` | `tenantId, sessionId, message` | `Session` | 向 Session 追加一条消息。 |
| `putState` | `tenantId, sessionId, key, value` | `Session` | 写入或覆盖一个会话级结构化状态。 |
| `removeState` | `tenantId, sessionId, key` | `Session` | 删除一个会话级结构化状态。 |
| `putMetadata` | `tenantId, sessionId, key, value` | `Session` | 写入或覆盖一个 Session 元信息。 |
| `removeMetadata` | `tenantId, sessionId, key` | `Session` | 删除一个 Session 元信息。 |
| `appendTask` | `tenantId, sessionId, task` | `Session` | 向 Session 的 Task 列表追加一个 Task 占位对象。 |
| `delete` | `tenantId, sessionId` | `void` | 删除 Session；用于用户清理、过期清理、测试清理或管理端删除。 |

公共方法只表达 Session 状态操作，不表达 Task 执行流程。`appendTask` 只维护 Session 持有 Task 列表这层关系，不代表 L2 理解 Task 状态、顺序、checkpoint、恢复或调度语义。

`state` 和 `metadata` 的边界必须保持清晰：`state` 用于业务可恢复状态，例如槽位、用户临时选择、流程参数、当前上下文标记；`metadata` 用于非业务元信息，例如入口协议、外部 context、trace/debug 标记、来源标签和观测标签。业务流程状态不应写入 `metadata`。

---

## 4. 存储抽象

Session 存储需要抽象一层。原因是当前可以先做内存实现，但正式环境更可能使用 Redis 或其它外部状态存储；如果调用方直接依赖具体存储，后续替换成本会很高。

```java
public interface SessionStore {
    Optional<Session> find(SessionKey key);
    Session save(Session session);
    Session update(SessionKey key, UnaryOperator<Session> mutator);
    boolean saveIfVersion(Session session, long expectedVersion);
    void remove(SessionKey key);
}
```

| 方法 | 入参 | 返回值 | 描述 |
|---|---|---|---|
| `find` | `SessionKey key` | `Optional<Session>` | 从存储中读取 Session；不存在时返回空。 |
| `save` | `Session session` | `Session` | 保存完整 Session；由实现决定插入或覆盖。 |
| `update` | `SessionKey key, UnaryOperator<Session> mutator` | `Session` | 原子更新 Session；用于追加消息、写会话状态和追加 Task。 |
| `saveIfVersion` | `Session session, expectedVersion` | `boolean` | 乐观锁保存；只有当前存储版本等于 `expectedVersion` 时才写入。 |
| `remove` | `SessionKey key` | `void` | 删除 Session；由 `SessionManager.delete` 调用。 |

`SessionManagerImpl` 依赖 `SessionStore`，不关心它背后是内存、Redis 还是数据库。当前阶段至少提供两个实现：

| 实现 | 适用场景 | 说明 |
|---|---|---|
| `InMemorySessionStore` | 本地开发、单机验证、单元测试 | 进程内保存，重启丢失，不适合多实例部署。 |
| `RedisSessionStore` | 联调环境、测试环境、生产环境、多实例部署 | Session 跨进程共享，可设置 TTL，可用 Redis 原子能力实现版本保护。 |

实现选择通过配置和 `SessionStoreFactory` 完成，调用方不感知具体实现。建议配置形态如下：

```yaml
agent-service:
  session:
    store:
      type: redis # memory | redis
      redis:
        key-prefix: spring-ai-ascend:session:
        ttl-seconds: 86400
        max-cas-retries: 16
```

`SessionManageConfiguration` 注册 `SessionStoreFactory`，再由工厂根据 `type` 创建具体 `SessionStore`。当 `type=memory` 时创建 `InMemorySessionStore`；当 `type=redis` 时创建 `RedisSessionStore`。如果未显式配置，开发环境可以默认 `memory`，共享环境和生产环境应显式使用 `redis`。当选择 `redis` 但没有可用的 `RedisSessionCommands` 时，工厂应失败并给出明确错误，避免静默退回内存实现。

`version` 由存储实现维护，创建时从 `1` 开始，每次成功保存或更新后递增。内存实现可以用同步块或并发容器保证原子性；数据库/Redis 实现应使用 CAS、where version 条件更新或同等机制。Redis CAS 更新需要设置最大重试次数，例如 `max-cas-retries=16`，避免并发冲突时无限循环。当前阶段 `SessionManager` 不需要把版本号暴露成业务参数，但 `Session` 对象需要携带它，便于后续替换分布式存储。

Redis 实现建议使用单个 JSON value 保存完整 `Session`，key 形如：

```text
{keyPrefix}{tenantId}:{sessionId}
```

后续如果消息列表变大，再参考 AgentScope 的列表增量追加思路，把 `messages` 拆成 Redis List；当前阶段保持整对象存储更简单。`saveIfVersion` 可以用 Lua 脚本、事务或 RedisJSON 条件更新实现，确保版本不匹配时不覆盖已有 Session。

---

## 5. POJO

```java
public record SessionKey(
    String tenantId,
    String sessionId
) {}

public record Session(
    String tenantId,
    String userId,
    String agentId,
    String sessionId,
    long version,
    List<SessionMessage> messages,
    Map<String, Object> state,
    List<Task> tasks,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt,
    Instant lastAccessedAt,
    Instant expiresAt
) {}

public record SessionMessage(
    String messageId,
    String name,
    SessionMessageRole role,
    String content,
    List<SessionContentPart> parts,
    Map<String, Object> metadata,
    Instant createdAt
) {}

public enum SessionMessageRole {
    USER, ASSISTANT, SYSTEM, TOOL
}

public record SessionContentPart(
    String type,
    Object value,
    Map<String, Object> metadata
) {}

public record Task() {}

```

| 类型 | 字段/枚举值 | 描述 |
|---|---|---|
| `SessionKey` | `tenantId` | 租户标识。 |
| `SessionKey` | `sessionId` | 会话标识。 |
| `Session` | `tenantId` | 租户标识。 |
| `Session` | `userId` | 用户标识，用于用户隔离和会话归属。 |
| `Session` | `agentId` | Agent 标识，用于区分同一用户下的不同 Agent 会话。 |
| `Session` | `sessionId` | 会话标识。 |
| `Session` | `version` | 存储版本号，用于乐观锁和并发更新保护。 |
| `Session` | `messages` | 会话消息历史/轻量事件流，保存用户消息、助手消息、系统上下文、工具结果摘要等可恢复上下文。 |
| `Session` | `state` | 会话级结构化状态，用于保存槽位、用户临时选择、流程参数、当前上下文标记等可恢复状态。 |
| `Session` | `tasks` | Session 持有的 Task 列表；当前 Task 只是占位对象。 |
| `Session` | `metadata` | Session 元信息，例如入口协议、外部 context、trace/debug 标记等；不承载业务状态。 |
| `Session` | `createdAt` | Session 创建时间。 |
| `Session` | `updatedAt` | Session 最近更新时间。 |
| `Session` | `lastAccessedAt` | Session 最近访问时间，用于过期、清理或观测。 |
| `Session` | `expiresAt` | Session 过期时间；内存和 Redis 实现都可以据此做清理或 TTL 映射。 |
| `SessionMessage` | `messageId` | 消息标识；可由 L2 生成，也可保留外部协议消息 ID。 |
| `SessionMessage` | `name` | 消息发送方名称或来源名称，可为空。 |
| `SessionMessage` | `role` | 消息角色。 |
| `SessionMessage` | `content` | 消息文本内容，便于对话类场景直接读取。 |
| `SessionMessage` | `parts` | 多段内容列表，用于承载文本、图片、工具结果、附件引用等扩展内容。 |
| `SessionMessage` | `metadata` | 消息扩展信息，例如来源、附件引用、协议侧原始字段等。 |
| `SessionMessage` | `createdAt` | 消息创建时间。 |
| `SessionMessageRole` | `USER` | 用户消息。 |
| `SessionMessageRole` | `ASSISTANT` | Agent 或模型返回给用户的消息。 |
| `SessionMessageRole` | `SYSTEM` | 系统级上下文或控制消息。 |
| `SessionMessageRole` | `TOOL` | 工具结果或外部能力返回消息。 |
| `SessionContentPart` | `type` | 内容片段类型，例如 `text`、`image`、`tool_result`、`artifact_ref`。 |
| `SessionContentPart` | `value` | 内容片段值；可以是文本、引用 ID、结构化对象等。 |
| `SessionContentPart` | `metadata` | 内容片段扩展信息。 |
| `Task` | 无字段 | 当前阶段的 Task 占位对象，只表达 Session 持有 Task 列表这层关系；Task 细节由 L4 定义。 |
---

## 6. 核心流程

### 6.1 加载或创建 Session

```java
调用方 -> SessionManager.loadOrCreate(tenantId, userId, agentId, sessionId)
   -> SessionStore.find(SessionKey)
   -> found ? return Session : create new Session
   -> SessionStore.save(Session)
```

如果入口请求没有携带 `sessionId`，当前阶段可以由 `SessionManagerImpl` 生成一个新的会话 ID。是否把 ID 生成能力拆成独立接口，等后续出现多种 ID 策略时再处理。每次加载到已有 Session 时，应更新 `lastAccessedAt`；如果配置了过期策略，可以同步计算或刷新 `expiresAt`。

### 6.2 追加消息

```java
调用方 -> SessionManager.appendMessage(...)
   -> SessionStore.update(SessionKey, mutator)
   -> messages.add(message)
   -> updatedAt = now
   -> version = version + 1
```

追加消息只更新 Session 历史，不触发任务执行，不向用户回消息。`SessionMessage.content` 用于常规文本读取，`SessionMessage.parts` 用于多模态、工具结果或附件引用。两者可以同时存在；如果只有纯文本，`parts` 可以为空。

### 6.3 更新状态和元信息

```java
调用方 -> SessionManager.putState(...) / removeState(...)
   -> SessionStore.update(SessionKey, mutator)
   -> state.put(key, value) 或 state.remove(key)
   -> updatedAt = now
   -> version = version + 1

调用方 -> SessionManager.putMetadata(...) / removeMetadata(...)
   -> SessionStore.update(SessionKey, mutator)
   -> metadata.put(key, value) 或 metadata.remove(key)
   -> updatedAt = now
   -> version = version + 1
```

`state` 面向业务恢复；`metadata` 面向协议、追踪和观测。两者都通过 `SessionStore.update` 原子更新，避免调用方直接修改 `Session` 内部 Map。

### 6.4 追加 Task 占位

```java
调用方 -> SessionManager.appendTask(...)
   -> tasks.add(task)
```

L2 只负责把 Task 占位对象追加进 `tasks` 列表。哪个 Task 最新、哪个 Task 正在执行、Task 如何 checkpoint、是否恢复历史 Task，都由调用方或 L4 Task Control 处理。

### 6.5 判断和删除 Session

```java
调用方 -> SessionManager.exists(tenantId, sessionId)
   -> SessionStore.find(SessionKey)
   -> return found

调用方 -> SessionManager.delete(tenantId, sessionId)
   -> SessionStore.remove(SessionKey)
```

`exists` 只做轻量存在性判断，不加载或返回完整业务对象给调用方。`delete` 只删除 L2 持有的 Session 聚合；如果其它层围绕该 Session 还有队列、Task、外部连接等资源，需要由对应层按自己的生命周期机制清理，L2 不跨层级删除其它组件内部对象。

---

## 7. 落地完整性与边界

当前 L2 方案已经可以作为 Session 部分的代码落地依据。需要注意的是，Task 细节、checkpoint 和执行恢复相关实现归 L4，不在本层展开。

### 7.1 必须保持的实现约束

| 约束 | 说明 |
|---|---|
| 其它层只调用 `SessionManager` | 避免上层依赖具体存储或直接修改 Session 内部结构。 |
| `SessionStore` 只作为存储抽象 | 它负责读写状态，不承载业务判断。 |
| 存储实现可配置选择 | 通过配置选择 `memory` 或 `redis`，调用方不感知具体实现。 |
| 存储更新必须保护版本 | 实现层需要保证 `update` 或 `saveIfVersion` 不发生静默覆盖。 |
| 消息结构保持轻量可扩展 | 当前只定义通用 `SessionContentPart`，不提前建立完整多模态类型继承体系。 |
| `state` 和 `metadata` 必须分工清晰 | 业务可恢复状态放 `state`；协议、追踪和观测元信息放 `metadata`。 |
| Task 不实现状态机 | 当前 `Task` 是空对象，只用于建立 Session 与 Task 列表关系。 |
| 不维护 Task 游标 | L2 不维护 latest/active 游标，避免提前设计 Task 管理语义。 |
| 不新增业务并发控制文件 | L2 不判断是否允许并发任务，只保证 Session 状态更新本身不丢失。 |
| 不新增上下文投影接口 | 投影、摘要、token 裁剪不是当前 Session Task Manager 的核心职责。 |
| `delete` 不做跨层资源级联删除 | L2 只删除 Session 聚合，不直接删除 L3 队列、L4 Task 或外部连接。 |

### 7.2 当前不需要继续增加的抽象

| 不增加的抽象 | 原因 |
|---|---|
| 独立 Session ID 生成抽象 | 当前一个默认生成策略足够；多策略出现后再拆。 |
| 独立业务并发保护抽象 | 是否允许同一 Session 发起新 Task 属于调用方业务决策，L2 不提供该抽象。 |
| Task 选择器或 checkpoint 存储接口 | Task、checkpoint 和恢复游标归 L4 管理，L2 不解释执行语义。 |
| 完整 ContentBlock 类型体系 | 当前只需要轻量 `SessionContentPart`；图片、音频、工具调用等强类型对象后续按真实协议再细化。 |
| 独立上下文投影模块 | 当前重点是 Session 定义和状态方法，投影能力可以后续单独设计。 |

### 7.3 代码落地时的最小实现顺序

1. 先实现 `model` 包下的 `SessionKey / Session / SessionMessage / SessionMessageRole / SessionContentPart / Task`。
2. 再实现 `SessionStore`。
3. 再实现 `InMemorySessionStore`，用于本地开发和单元测试。
4. 再实现 `RedisSessionStore` 和 `RedisSessionStoreProperties`，用于共享环境和生产环境。
5. 再实现 `SessionManager` 和 `SessionManagerImpl`。
6. 最后补充 Session 方法的单元测试，重点覆盖创建、存在性判断、追加消息、写/删状态、写/删元信息、追加 Task、删除、版本递增、过期时间和存储替换边界。

### 7.4 AgentScope 参考取舍

| AgentScope 参考点 | 本方案取舍 |
|---|---|
| `Session` 支持多后端实现，例如内存、JSON、Redis、MySQL | 保留 `SessionStore`，并在实现层适配不同存储。 |
| Redis Session 支持 key prefix 和多 Redis 客户端/部署模式 | 本方案保留 `keyPrefix` 和 Redis 配置对象，但不绑定具体客户端选择。 |
| Redis Session 对列表状态支持增量追加 | 当前先整对象保存 `Session`；消息量变大后再考虑把 `messages` 拆成 list。 |
| Store 层使用 version / CAS 防止并发写覆盖 | 在 `Session.version` 和 `saveIfVersion` 中预留乐观锁语义。 |
| `Msg` 包含 id、name、role、content blocks、metadata、timestamp | `SessionMessage` 补充 `messageId/name/parts/metadata/createdAt`，但不复制完整 `Msg` 类。 |
| `SessionInfo` 包含 lastModified 等观测信息 | `Session` 保留 `updatedAt/lastAccessedAt/expiresAt/metadata`，满足当前观测和清理需要。 |
| `ContentBlock` 是完整多模态类型体系 | 当前只保留轻量 `SessionContentPart`，避免过早设计多模态继承结构。 |
| Session 可按 key 保存多个 State 组件 | 当前不拆成通用 State Store；L2 只维护一个明确的 Session 聚合对象。 |

基于 AgentScope 的对照，当前 `Session` 参数整体够用，但建议补充 `metadata / lastAccessedAt / expiresAt`。`metadata` 用于承接协议来源、外部 context、调试标记等非稳定字段；`lastAccessedAt` 和 `expiresAt` 用于 Redis TTL、会话清理和观测。暂不建议补 `size/componentCount`，这些属于存储观测信息，应由存储实现或管理接口计算，而不是写进 Session 聚合对象。

---

## 8. 备注：文件职责说明

| 文件 | 职责 |
|---|---|
| `SessionManager.java` | L2 对其它内部层暴露的 Session 操作入口，包含创建、查询、存在性判断、消息追加、状态更新、元信息更新、Task 追加和删除。 |
| `SessionManagerImpl.java` | Session 操作编排实现，依赖 `SessionStore` 完成读写和删除。 |
| `SessionManageConfiguration.java` | L2 Spring 装配类，注册 `SessionManager`、默认内存存储、可选 Redis 存储、时钟和序列化组件。 |
| `SessionManageProperties.java` | L2 Session 通用配置，例如 TTL。 |
| `DefaultSessionStoreFactory.java` | 默认 Session 存储工厂，根据配置创建内存或 Redis 版 `SessionStore`。 |
| `Session.java` | Session 聚合对象，保存消息历史、会话状态和 Task 占位列表。 |
| `SessionKey.java` | Session 存储主键，当前由 `tenantId + sessionId` 组成。 |
| `SessionMessage.java` | 会话消息对象，用于保存用户、助手、系统或工具消息。 |
| `SessionMessageRole.java` | 会话消息角色枚举。 |
| `SessionContentPart.java` | 会话消息内容片段，用于预留多模态、工具结果和附件引用。 |
| `Task.java` | Task 占位对象，当前不实现任何 Task 细节。 |
| `SessionStore.java` | Session 存储抽象，屏蔽内存、数据库、Redis 等存储差异。 |
| `SessionStoreFactory.java` | Session 存储工厂接口，负责创建具体 `SessionStore` 实现。 |
| `InMemorySessionStore.java` | 默认内存存储实现，用于本地开发和早期验证。 |
| `SessionNotFoundException.java` | Session 不存在异常，用于 `update` 等必须命中已有 Session 的操作。 |
| `RedisSessionStore.java` | Redis 存储实现，用于共享环境和生产环境。 |
| `RedisSessionStoreProperties.java` | Redis Session 存储配置，例如 key prefix、TTL、CAS 最大重试次数、连接配置引用等。 |
| `RedisSessionCommands.java` | Redis 基础命令端口，封装 get、set、compare-and-set 和 delete；具体 Redis 客户端由外部适配。 |
| `SessionCodec.java` | Session 序列化端口，用于屏蔽 JSON 或其它编码格式。 |
| `JacksonSessionCodec.java` | 基于 Jackson 的 Session JSON 序列化实现。 |

---

## 9. 备注：代码结构与设计对象对齐

| 设计对象 | 代码位置 | 对齐说明 |
|---|---|---|
| L2 公共入口 | `service/sessionmanage/api/SessionManager.java` | 其它内部层只通过该接口访问 Session。 |
| L2 门面实现 | `service/sessionmanage/core/SessionManagerImpl.java` | 组织 Session 创建、查询、更新、追加消息、写/删状态、写/删元信息、追加 Task 和删除等操作。 |
| L2 Spring 装配 | `service/sessionmanage/config/SessionManageConfiguration.java` | 注册 `SessionStoreFactory`、`SessionStore` 和 `SessionManager`。 |
| 存储工厂 | `service/sessionmanage/store/SessionStoreFactory.java` + `service/sessionmanage/config/DefaultSessionStoreFactory.java` | 根据配置选择并创建内存或 Redis 版 Session 存储。 |
| Session 聚合 | `service/sessionmanage/model/Session.java` | 持有消息历史、会话状态和 Task 列表。 |
| Session 存储主键 | `service/sessionmanage/model/SessionKey.java` | 作为 `SessionStore` 的查找键。 |
| 会话消息 | `service/sessionmanage/model/SessionMessage.java` | 表达要写入 Session 历史的消息。 |
| 消息角色 | `service/sessionmanage/model/SessionMessageRole.java` | 约束消息来源类型。 |
| 消息内容片段 | `service/sessionmanage/model/SessionContentPart.java` | 给 Session 消息预留多段内容和结构化扩展能力。 |
| Task 占位 | `service/sessionmanage/model/Task.java` | 只表达 Session 持有 Task 的关系，不表达 Task 执行细节。 |
| 存储抽象 | `service/sessionmanage/store/SessionStore.java` | 未来替换数据库、Redis 等实现时，上层接口不变。 |
| 内存存储实现 | `service/sessionmanage/store/memory/InMemorySessionStore.java` | 本地开发、单机验证和单元测试使用。 |
| Redis 存储实现 | `service/sessionmanage/store/redis/RedisSessionStore.java` | 共享环境和生产环境使用，通过配置切换。 |
| Redis 存储配置 | `service/sessionmanage/store/redis/RedisSessionStoreProperties.java` | 定义 key prefix、TTL、CAS 最大重试次数和 Redis 连接配置引用。 |
| Redis 客户端端口 | `service/sessionmanage/store/redis/RedisSessionCommands.java` | 由具体 Redis client adapter 实现，L2 Redis 存储不直接绑定某个客户端库。 |
| Session 序列化 | `service/sessionmanage/store/redis/SessionCodec.java` + `JacksonSessionCodec.java` | Redis value 的编码/解码能力，默认使用 Jackson JSON。 |

