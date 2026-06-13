# agent-runtime middleware state InMemory example

这个样例验证「中间件解耦」下的 Agent State 本地持久化路径：
业务侧通过 `OpenJiuwenCheckpointerConfigurer.setInMemoryDefault()` 选择
OpenJiuwen 原生 `InMemoryCheckpointer`，`agent-runtime` 公共层不感知具体后端。

## 配置方式

适用场景：

- 本地开发、单元验证、临时端到端验证。
- 不需要跨进程恢复 Agent State。
- 不希望引入 Redis、数据库等外部依赖。

核心配置点：

```java
Checkpointer checkpointer = OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
```

这行代码会把 OpenJiuwen 默认 checkpointer 设置为 `InMemoryCheckpointer`。
业务代码只需要保证后续 OpenJiuwen 执行使用稳定的 `conversation_id`。在
`agent-runtime` 的 OpenJiuwen 适配层中，这个值来自
`AgentExecutionContext#getAgentStateKey()`。

测试团队检查点：

- `preAgentExecute(...)` 能按 session id 创建或恢复 OpenJiuwen session。
- `postAgentExecute(...)` 能把本轮状态写回 checkpointer。
- `sessionExists(sessionId)` 在保存后返回 `true`。
- `release(sessionId)` 后状态被清理。

## Run

```bash
./mvnw -f examples/agent-runtime-middleware-state-inmemory/pom.xml test
```

## 端到端链路

```text
OpenJiuwenCheckpointerConfigurer.setInMemoryDefault()
  -> AgentSessionApi(sessionId)
  -> checkpointer.preAgentExecute(...)
  -> session.updateState(...)
  -> checkpointer.postAgentExecute(...)
  -> checkpointer.sessionExists(sessionId)
  -> checkpointer.release(sessionId)
```

## Scope

- 无外部依赖。
- 验证 `preAgentExecute -> postAgentExecute -> release` 的一轮
  OpenJiuwen `AgentSessionApi` 状态持久化链路。
- 不引入脚本；所有运行方式写在 README 中。
