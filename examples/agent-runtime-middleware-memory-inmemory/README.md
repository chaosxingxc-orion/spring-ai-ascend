# agent-runtime middleware memory InMemory example

这个样例验证 `MemoryProvider` 的 InMemory 接入形态。Provider 放在 example
目录中，说明 Memory 后端可以由业务样例或客户工程自行选择，`agent-runtime`
公共层只依赖窄 SPI。

## 配置方式

适用场景：

- 本地开发、客户二次开发验证、测试团队构造确定性记忆命中。
- 不需要真实向量库或外部 Memory 服务。
- 希望看清楚 `MemoryProvider` 如何接入 OpenJiuwen handler。

核心配置点：

```java
InMemoryMemoryProvider provider = new InMemoryMemoryProvider();

class MemoryEnabledHandler extends OpenJiuwenAgentRuntimeHandler {
    @Override
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        return List.of(memoryRuntimeRail(context, provider));
    }
}
```

`InMemoryMemoryProvider` 是 example-only 实现，按
`AgentExecutionContext#getAgentStateKey()` 做最小隔离。客户项目可以替换为
自己的数据库、向量库、RAG 记忆服务或企业内存储。

测试团队检查点：

- 测试先写入一条长期记忆，例如 `the user prefers green tea`。
- 用户输入进入 `OpenJiuwenAgentRuntimeHandler#execute(...)`。
- handler 注册 memory rail。
- rail 在执行前调用 `MemoryProvider.search(...)`。
- rail 在执行后调用 `MemoryProvider.save(...)` 写回本轮 user/assistant 记录。
- 不同 `agentStateKey` 的记忆不会互相命中。

## Run

```bash
./mvnw -f examples/agent-runtime-middleware-memory-inmemory/pom.xml test
```

## 端到端链路

```text
AgentExecutionContext(user input + agentStateKey)
  -> OpenJiuwenAgentRuntimeHandler.execute(...)
  -> openJiuwenRails(context)
  -> memoryRuntimeRail(context, InMemoryMemoryProvider)
  -> MemoryProvider.search(...)
  -> OpenJiuwen ModelContext receives memory note
  -> simulated OpenJiuwen agent response
  -> MemoryProvider.save(...)
```

## Scope

- 无外部依赖。
- 验证用户接入 `MemoryProvider` 后，执行 `OpenJiuwenAgentRuntimeHandler`
  会安装 memory rail，并触发 `search` / `save`。
- 不引入脚本；所有运行方式写在 README 中。
