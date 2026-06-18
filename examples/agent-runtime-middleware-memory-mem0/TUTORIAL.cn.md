# Mem0 MemoryProvider 样例教程

跟着做完，你会启动一个 Mem0-compatible REST 服务，再启动本样例服务，通过 curl 写入、检索并注入长期记忆。

---

## 0. 你将验证什么

本样例验证外部长期记忆服务接入：

```text
curl 写入记忆
        ↓
Mem0RestMemoryProvider 调 Mem0 REST API
        ↓
curl 发起用户输入
        ↓
memory rail 调 Mem0 REST API 检索记忆
        ↓
ReActAgent 模型输入包含 Relevant memory
```

---

## 1. 环境准备

在仓库根目录执行命令。需要：

- JDK 21
- curl
- 可用的 OpenAI-compatible LLM API
- 一个可访问的 Mem0-compatible REST 服务
- 本地 18082 端口未被占用

Windows PowerShell 可以把下面的 `./mvnw` 换成 `./mvnw.cmd`。

---

## 2. Step 1 — 准备 Mem0 REST 服务

样例支持两种 REST 形态：

| 模式 | 配置值 | 写入路径 | 检索路径 |
|---|---|---|---|
| Mem0 OSS REST server | `oss` | `/memories` | `/search` |
| OpenMemory / Platform API | `platform` | `/v1/memories/` | `/v2/memories/search/` |

默认使用 `oss`：

```text
sample.mem0.base-url=http://localhost:8000
sample.mem0.api-key=
sample.mem0.api-mode=oss
sample.mem0.infer-on-save=false
```

`infer-on-save=false` 表示样例按测试输入直接写入，便于稳定验证。接入真实 Mem0 抽取能力时可以改成 `true`，但验证结果会依赖 Mem0 的抽取策略。

---

## 3. Step 2 — 启动样例守护进程

```bash
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
export SAA_SAMPLE_OPENJIUWEN_API_BASE=https://api.deepseek.com
export SAA_SAMPLE_LLM_MODEL=deepseek-chat
export SAA_SAMPLE_LLM_API_KEY=sk-your-key

./mvnw -f examples/agent-runtime-middleware-memory-mem0/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--sample.mem0.base-url=http://localhost:8000 --sample.mem0.api-mode=oss --sample.mem0.infer-on-save=false"
```

如果服务需要 API Key：

```bash
./mvnw -f examples/agent-runtime-middleware-memory-mem0/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--sample.mem0.base-url=http://localhost:8000 --sample.mem0.api-key=your-key --sample.mem0.api-mode=oss --sample.mem0.infer-on-save=false"
```

服务地址：

```text
http://localhost:18082
```

---

## 4. Step 3 — 写入一条长期记忆

```bash
curl -s -X POST http://localhost:18082/sample/memory/remember \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"the user prefers green tea"}'
```

期望响应：

```json
{
  "stateKey": "demo-user",
  "saved": true
}
```

---

## 5. Step 4 — 直接检索 Mem0 记忆

```bash
curl -s 'http://localhost:18082/sample/memory/search?stateKey=demo-user&query=green%20tea'
```

期望响应里 `hits` 至少包含一条和 `green tea` 相关的内容。

---

## 6. Step 5 — 发起一次用户请求

```bash
curl -s -X POST http://localhost:18082/sample/memory/ask \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-user","text":"What drink does the user prefer?"}'
```

期望响应：

- `hits` 包含 Mem0 返回的记忆。
- `agentOutputs` 中是模型真实返回结果，正常情况下应能回答用户偏好是 `green tea`。

---

## 7. Step 6 — 看代码入口

关键代码在：

- `MemoryMem0Application.java`
- `Mem0RestMemoryProvider`
- `SampleMem0OpenJiuwenHandler#openJiuwenRails(...)`

用户侧要复用这个模式时，核心动作是让 handler 持有 `MemoryProvider`，并在每次执行时基于当前 `AgentExecutionContext` 创建 memory rail：

```java
protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
    return List.of(memoryRuntimeRail(context, memoryProvider));
}
```

样例不 override `runOpenJiuwenAgentStreaming(...)`；handler 执行仍走 `OpenJiuwenAgentRuntimeHandler` 默认 streaming Runner。业务侧只负责把自己的 `MemoryProvider` 接到 handler 上。

---

## 8. 清理

在启动样例服务的终端按 `Ctrl+C` 停止进程。Mem0 服务由测试团队或客户开发团队按自己的环境清理。
