# Agent Runtime Memory Mem0 Example

本样例验证 `MemoryProvider` 对接 Mem0-compatible REST 服务的方式。它启动一个常驻 Spring Boot 进程，用户通过 curl 触发写入、检索和 OpenJiuwen ReActAgent 执行链路。

完整 step by step 教程见：[TUTORIAL.cn.md](TUTORIAL.cn.md)。

## 快速启动

先准备一个兼容 Mem0 REST API 的服务，然后启动样例。Mem0 服务启动方式、`oss` / `platform`
两种 API 模式差异，以及后续 curl 验证步骤见 [TUTORIAL.cn.md](TUTORIAL.cn.md)。

```bash
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
export SAA_SAMPLE_OPENJIUWEN_API_BASE=https://api.deepseek.com
export SAA_SAMPLE_LLM_MODEL=deepseek-chat
export SAA_SAMPLE_LLM_API_KEY=sk-your-key

./mvnw -f examples/agent-runtime-middleware-memory-mem0/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--sample.mem0.base-url=http://localhost:8000 --sample.mem0.api-mode=oss --sample.mem0.infer-on-save=false"
```

默认监听：

```text
http://localhost:18082
```

## 主要接口

| 接口 | 用途 |
|---|---|
| `POST /sample/memory/remember` | 通过 Mem0 REST 写入一条长期记忆 |
| `GET /sample/memory/search?stateKey=demo-user&query=green%20tea` | 通过 Mem0 REST 检索记忆 |
| `POST /sample/memory/ask` | 模拟一次用户请求，并触发 memory rail 检索和注入 |

## 设计要点

- `/sample/memory/ask` 请求体里的 `text` 是本轮用户输入；样例会把它包装成 `RuntimeMessage.user(text)`，
  最终由 `OpenJiuwenMessageAdapter` 转成 OpenJiuwen Runner 的 `query`。
- `createOpenJiuwenAgent(...)` 中的 promptTemplate 只是 system prompt，用来约束样例 Agent 的回答方式，
  不是用户输入。
- 样例 handler 直接持有 `MemoryProvider`，并在 `openJiuwenRails(context)` 中注册唯一的 memory rail。
- 执行时不 override `runOpenJiuwenAgent(...)`，仍走 OpenJiuwen 默认 Runner。
- `Mem0RestMemoryProvider` 是 example 级适配器，用于演示 `MemoryProvider` 如何对接外部长期记忆服务。
- `sample.mem0.api-mode=oss` 使用 `/memories` 和 `/search`；`sample.mem0.api-mode=platform` 使用 `/v1/memories/` 和 `/v2/memories/search/`。
- 本样例是面向用户视角的 daemon + curl 验证，不是单元测试替代品。
