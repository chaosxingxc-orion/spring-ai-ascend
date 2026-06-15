# Agent Runtime Memory InMemory Example

本样例验证“长期记忆 `MemoryProvider` 解耦”的最小接入方式。它启动一个常驻 Spring Boot 进程，用户通过 curl 写入记忆、发起一次用户输入，并从响应中观察记忆是否进入 OpenJiuwen ReActAgent 的真实模型输入。

完整 step by step 教程见：[TUTORIAL.cn.md](TUTORIAL.cn.md)。

## 快速启动

```bash
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
export SAA_SAMPLE_OPENJIUWEN_API_BASE=https://api.deepseek.com
export SAA_SAMPLE_LLM_MODEL=deepseek-chat
export SAA_SAMPLE_LLM_API_KEY=sk-your-key

./mvnw -f examples/agent-runtime-middleware-memory-inmemory/pom.xml spring-boot:run
```

默认监听：

```text
http://localhost:18081
```

## 主要接口

| 接口 | 用途 |
|---|---|
| `POST /sample/memory/remember` | 写入一条长期记忆 |
| `POST /sample/memory/ask` | 模拟一次用户请求，并触发 memory rail 检索和注入 |
| `GET /sample/memory/records?stateKey=demo-user` | 查看当前 `stateKey` 下保存的记忆 |

## 设计要点

- `/sample/memory/ask` 请求体里的 `text` 是本轮用户输入；样例会把它包装成 `RuntimeMessage.user(text)`，
  最终由 `OpenJiuwenMessageAdapter` 转成 OpenJiuwen Runner 的 `query`。
- `createOpenJiuwenAgent(...)` 中的 promptTemplate 只是 system prompt，用来约束样例 Agent 的回答方式，
  不是用户输入。
- 样例 handler 直接持有 `MemoryProvider`，并在 `openJiuwenRails(context)` 中注册唯一的 memory rail。
- 执行时不 override `runOpenJiuwenAgent(...)`，仍走 OpenJiuwen 默认 Runner。
- `InMemoryMemoryProvider` 只放在 example 中，用于端到端验证；生产环境应替换成企业自己的长期记忆服务。
