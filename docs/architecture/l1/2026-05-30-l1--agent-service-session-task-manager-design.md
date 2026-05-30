# 07. agent-service L2 Session Task Manager

## 1. 职责

- 定义并维护 Session 聚合对象
- 按 `tenantId + sessionId` 创建、加载和更新 Session
- 维护 Session 内的消息列表、变量表和 Task 占位列表
- 向其它层提供 Session 相关公共方法
- 通过 `SessionStatePort` 抽象 Session 存储，避免上层依赖内存、数据库、Redis 等具体实现，并预留版本号/乐观锁语义
- Task 在当前阶段只保留占位对象，不实现 Task 状态机、调度、执行和恢复逻辑

---

## 2. 包结构

```text
service/
  tasksessionmanager/
    SessionTaskManager.java
    SessionTaskManagerImpl.java
    model/
      Session.java
      SessionKey.java
      SessionMessage.java
      SessionMessageRole.java
      SessionContentPart.java
      Task.java
    spi/
      SessionStatePort.java
    memory/
      InMemorySessionStateAdapter.java
    redis/
      RedisSessionStateAdapter.java
      RedisSessionStateProperties.java
```

---

## 3. 公共接口

`SessionTaskManager` 是 L2 暴露给其它内部调用方的主入口。其它层不直接访问 `SessionStatePort`，也不依赖具体存储实现。

```java
public interface SessionTaskManager {
    Session loadOrCreate(String tenantId, String userId, String agentId, String sessionId);
    Optional<Session> get(String tenantId, String sessionId);
    Session appendMessage(String tenantId, String sessionId, SessionMessage message);
    Session putVariable(String tenantId, String sessionId, String key, Object value);
    Session appendTask(String tenantId, String sessionId, Task task);
}
```

| 方法 | 入参 | 返回值 | 描述 |
|---|---|---|---|
| `loadOrCreate` | `tenantId, userId, agentId, sessionId` | `Session` | 按租户和会话 ID 加载 Session；不存在则创建。 |
| `get` | `tenantId, sessionId` | `Optional<Session>` | 只读查询 Session；不存在时返回空。 |
| `appendMessage` | `tenantId, sessionId, message` | `Session` | 向 Session 追加一条消息。 |
| `putVariable` | `tenantId, sessionId, key, value` | `Session` | 写入或覆盖一个会话级变量。 |
| `appendTask` | `tenantId, sessionId, task` | `Session` | 向 Session 的 Task 列表追加一个 Task 占位对象。 |

公共方法只表达 Session 状态操作，不表达 Task 执行流程。`appendTask` 只维护 Session 持有 Task 列表这层关系，不代表 L2 理解 Task 状态、顺序、恢复或调度语义。

---

## 4. 存储抽象

Session 存储需要抽象一层。原因是当前可以先做内存实现，但正式环境更可能使用 Redis 或其它外部状态存储；如果调用方直接依赖具体存储，后续替换成本会很高。

```java
public interface SessionStatePort {
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
| `update` | `SessionKey key, UnaryOperator<Session> mutator` | `Session` | 原子更新 Session；用于追加消息、写变量和追加 Task。 |
| `saveIfVersion` | `Session session, expectedVersion` | `boolean` | 乐观锁保存；只有当前存储版本等于 `expectedVersion` 时才写入。 |
| `remove` | `SessionKey key` | `void` | 删除 Session；当前阶段可先不对外暴露，只作为存储能力预留。 |

`SessionTaskManagerImpl` 依赖 `SessionStatePort`，不关心它背后是内存、Redis 还是数据库。当前阶段至少提供两个实现：

| 实现 | 适用场景 | 说明 |
|---|---|---|
| `InMemorySessionStateAdapter` | 本地开发、单机验证、单元测试 | 进程内保存，重启丢失，不适合多实例部署。 |
| `RedisSessionStateAdapter` | 联调环境、测试环境、生产环境、多实例部署 | Session 跨进程共享，可设置 TTL，可用 Redis 原子能力实现版本保护。 |

实现选择通过配置完成，调用方不感知具体实现。建议配置形态如下：

```yaml
agent-service:
  session:
    store:
      type: redis # memory | redis
      redis:
        key-prefix: spring-ai-ascend:session:
        ttl-seconds: 86400
```

当 `type=memory` 时注入 `InMemorySessionStateAdapter`；当 `type=redis` 时注入 `RedisSessionStateAdapter`。如果未显式配置，开发环境可以默认 `memory`，共享环境和生产环境应显式使用 `redis`。

`version` 由存储实现维护，创建时从 `1` 开始，每次成功保存或更新后递增。内存实现可以用同步块或并发容器保证原子性；数据库/Redis 实现应使用 CAS、where version 条件更新或同等机制。当前阶段 `SessionTaskManager` 不需要把版本号暴露成业务参数，但 `Session` 对象需要携带它，便于后续替换分布式存储。

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
    Map<String, Object> variables,
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
| `Session` | `messages` | 会话消息列表，保存对话历史或关键上下文事件。 |
| `Session` | `variables` | 会话级变量表，用于保存偏好、槽位、运行参数等轻量状态。 |
| `Session` | `tasks` | Session 持有的 Task 列表；当前 Task 只是占位对象。 |
| `Session` | `metadata` | Session 扩展元数据，例如入口协议、外部上下文、调试标记等。 |
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
| `Task` | 无字段 | 当前阶段的 Task 占位对象，后续再扩展 taskId、状态、阶段等字段。 |

---

## 6. 核心流程

### 6.1 加载或创建 Session

```java
调用方 -> SessionTaskManager.loadOrCreate(tenantId, userId, agentId, sessionId)
   -> SessionStatePort.find(SessionKey)
   -> found ? return Session : create new Session
   -> SessionStatePort.save(Session)
```

如果入口请求没有携带 `sessionId`，当前阶段可以由 `SessionTaskManagerImpl` 生成一个新的会话 ID。是否把 ID 生成能力拆成独立接口，等后续出现多种 ID 策略时再处理。每次加载到已有 Session 时，应更新 `lastAccessedAt`；如果配置了过期策略，可以同步计算或刷新 `expiresAt`。

### 6.2 追加消息

```java
调用方 -> SessionTaskManager.appendMessage(...)
   -> SessionStatePort.update(SessionKey, mutator)
   -> messages.add(message)
   -> updatedAt = now
   -> version = version + 1
```

追加消息只更新 Session 历史，不触发任务执行，不向用户回消息。`SessionMessage.content` 用于常规文本读取，`SessionMessage.parts` 用于多模态、工具结果或附件引用。两者可以同时存在；如果只有纯文本，`parts` 可以为空。

### 6.3 追加 Task 占位

```java
调用方 -> SessionTaskManager.appendTask(...)
   -> tasks.add(task)
```

L2 只负责把 Task 占位对象追加进 `tasks` 列表。哪个 Task 最新、哪个 Task 正在执行、是否允许新增 Task、是否恢复历史 Task，都由调用方或后续 Task 设计处理。

---

## 7. 落地完整性与边界

当前 L2 方案已经可以作为 Session 部分的代码落地依据。需要注意的是，Task 相关实现仍然刻意留空，不在本阶段展开。

### 7.1 必须保持的实现约束

| 约束 | 说明 |
|---|---|
| 其它层只调用 `SessionTaskManager` | 避免上层依赖具体存储或直接修改 Session 内部结构。 |
| `SessionStatePort` 只作为存储 SPI | 它负责读写状态，不承载业务判断。 |
| 存储实现可配置选择 | 通过配置选择 `memory` 或 `redis`，调用方不感知具体实现。 |
| 存储更新必须保护版本 | 实现层需要保证 `update` 或 `saveIfVersion` 不发生静默覆盖。 |
| 消息结构保持轻量可扩展 | 当前只定义通用 `SessionContentPart`，不提前建立完整多模态类型继承体系。 |
| Task 不实现状态机 | 当前 `Task` 是空对象，只用于建立 Session 与 Task 列表关系。 |
| 不维护 Task 游标 | L2 不维护 latest/active 游标，避免提前设计 Task 管理语义。 |
| 不新增业务并发控制文件 | L2 不判断是否允许并发任务，只保证 Session 状态更新本身不丢失。 |
| 不新增上下文投影接口 | 投影、摘要、token 裁剪不是当前 Session Task Manager 的核心职责。 |

### 7.2 当前不需要继续增加的抽象

| 不增加的抽象 | 原因 |
|---|---|
| 独立 Session ID 生成 SPI | 当前一个默认生成策略足够；多策略出现后再拆。 |
| 独立业务并发保护 SPI | 是否允许同一 Session 发起新 Task 属于调用方业务决策，L2 不提供该抽象。 |
| 独立 Task 选择器 | L2 不解释 Task 列表语义，因此不提供 latest/active 选择器。 |
| 完整 ContentBlock 类型体系 | 当前只需要轻量 `SessionContentPart`；图片、音频、工具调用等强类型对象后续按真实协议再细化。 |
| 独立上下文投影模块 | 当前重点是 Session 定义和状态方法，投影能力可以后续单独设计。 |

### 7.3 代码落地时的最小实现顺序

1. 先实现 `model` 包下的 `SessionKey / Session / SessionMessage / SessionMessageRole / SessionContentPart / Task`。
2. 再实现 `SessionStatePort`。
3. 再实现 `InMemorySessionStateAdapter`，用于本地开发和单元测试。
4. 再实现 `RedisSessionStateAdapter` 和 `RedisSessionStateProperties`，用于共享环境和生产环境。
5. 再实现 `SessionTaskManager` 和 `SessionTaskManagerImpl`。
6. 最后补充 Session 方法的单元测试，重点覆盖创建、追加消息、追加 Task、版本递增、过期时间和存储替换边界。

### 7.4 AgentScope 参考取舍

| AgentScope 参考点 | 本方案取舍 |
|---|---|
| `Session` 支持多后端实现，例如内存、JSON、Redis、MySQL | 保留 `SessionStatePort`，并在实现层适配不同存储。 |
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
| `SessionTaskManager.java` | L2 对其它内部层暴露的 Session 操作入口。 |
| `SessionTaskManagerImpl.java` | Session 操作编排实现，依赖 `SessionStatePort` 完成读写。 |
| `Session.java` | Session 聚合对象，保存消息、变量和 Task 占位列表。 |
| `SessionKey.java` | Session 存储主键，当前由 `tenantId + sessionId` 组成。 |
| `SessionMessage.java` | 会话消息对象，用于保存用户、助手、系统或工具消息。 |
| `SessionMessageRole.java` | 会话消息角色枚举。 |
| `SessionContentPart.java` | 会话消息内容片段，用于预留多模态、工具结果和附件引用。 |
| `Task.java` | Task 占位对象，当前不实现任何 Task 细节。 |
| `SessionStatePort.java` | Session 存储 SPI，屏蔽内存、数据库、Redis 等存储差异。 |
| `InMemorySessionStateAdapter.java` | 默认内存存储实现，用于本地开发和早期验证。 |
| `RedisSessionStateAdapter.java` | Redis 存储实现，用于共享环境和生产环境。 |
| `RedisSessionStateProperties.java` | Redis Session 存储配置，例如 key prefix、TTL、连接配置引用等。 |

---

## 9. 备注：代码结构与设计对象对齐

| 设计对象 | 代码位置 | 对齐说明 |
|---|---|---|
| L2 公共入口 | `service/tasksessionmanager/SessionTaskManager.java` | 其它内部层只通过该接口访问 Session。 |
| L2 门面实现 | `service/tasksessionmanager/SessionTaskManagerImpl.java` | 组织 Session 创建、更新、追加消息和追加 Task 等操作。 |
| Session 聚合 | `service/tasksessionmanager/model/Session.java` | 持有消息、变量和 Task 列表。 |
| Session 存储主键 | `service/tasksessionmanager/model/SessionKey.java` | 作为 `SessionStatePort` 的查找键。 |
| 会话消息 | `service/tasksessionmanager/model/SessionMessage.java` | 表达要写入 Session 历史的消息。 |
| 消息角色 | `service/tasksessionmanager/model/SessionMessageRole.java` | 约束消息来源类型。 |
| 消息内容片段 | `service/tasksessionmanager/model/SessionContentPart.java` | 给 Session 消息预留多段内容和结构化扩展能力。 |
| Task 占位 | `service/tasksessionmanager/model/Task.java` | 只表达 Session 持有 Task 的关系，不表达 Task 执行细节。 |
| 存储抽象 | `service/tasksessionmanager/spi/SessionStatePort.java` | 未来替换数据库、Redis 等实现时，上层接口不变。 |
| 内存存储实现 | `service/tasksessionmanager/memory/InMemorySessionStateAdapter.java` | 本地开发、单机验证和单元测试使用。 |
| Redis 存储实现 | `service/tasksessionmanager/redis/RedisSessionStateAdapter.java` | 共享环境和生产环境使用，通过配置切换。 |
| Redis 存储配置 | `service/tasksessionmanager/redis/RedisSessionStateProperties.java` | 定义 key prefix、TTL 和 Redis 连接配置引用。 |
