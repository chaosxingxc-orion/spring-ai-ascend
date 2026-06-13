# agent-runtime middleware memory Mem0 example

这个样例验证 `MemoryProvider` 对接 Mem0-compatible REST 服务的方式。
Provider 放在 example 目录中，避免 `agent-runtime` 公共 SPI 直接绑定 Mem0 包名。

## 配置方式

适用场景：

- 客户已有 Mem0-compatible REST 服务。
- 需要把 runtime 的 `MemoryProvider` 窄 SPI 接到外部长期记忆服务。
- 希望验证 `search` / `save` 的 request/response 映射，而不是把 Mem0 SDK
  绑定进 runtime 公共层。

核心配置点：

```java
Mem0RestMemoryProvider provider = new Mem0RestMemoryProvider(
        "http://localhost:8000",
        apiKey,
        false,
        "oss");

class MemoryEnabledHandler extends OpenJiuwenAgentRuntimeHandler {
    @Override
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        return List.of(memoryRuntimeRail(context, provider));
    }
}
```

参数说明：

| 参数 | 含义 |
|---|---|
| `baseUrl` | Mem0-compatible REST 服务地址 |
| `apiKey` | 鉴权密钥；OSS 本地测试可为空 |
| `inferOnSave` | 写入时是否让 Mem0 自己推断/抽取长期记忆 |
| `apiMode` | `oss` 或 `platform`，用于选择不同 REST path 和鉴权 header |

测试团队检查点：

- handler 执行前，provider 向 Mem0 search endpoint 发送检索请求。
- handler 执行后，provider 向 Mem0 add/memories endpoint 写入本轮记录。
- 请求里包含 user/session/task/agentStateKey 这类隔离 metadata。
- `agent-runtime` 公共层只依赖 `MemoryProvider`，不依赖 Mem0 SDK 或包名。

## Environment

该样例必须连接真实 Mem0-compatible REST 服务。自动化测试不会启动内嵌假服务；
没有设置 `SAA_SAMPLE_MEM0_BASE_URL` 时，测试会通过 JUnit assumption 跳过。

最小配置：

```bash
export SAA_SAMPLE_MEM0_BASE_URL=http://localhost:8000
export SAA_SAMPLE_MEM0_API_MODE=oss
```

如果服务开启鉴权，再设置：

```bash
export SAA_SAMPLE_MEM0_API_KEY=<your-api-key>
```

环境变量说明：

| 变量 | 必填 | 默认值 | 说明 |
|---|---|---|---|
| `SAA_SAMPLE_MEM0_BASE_URL` | 是 | 无 | Mem0-compatible REST 服务地址；未设置时测试跳过 |
| `SAA_SAMPLE_MEM0_API_MODE` | 否 | `oss` | REST endpoint 与鉴权 header 形态，支持 `oss` / `platform` |
| `SAA_SAMPLE_MEM0_API_KEY` | 否 | 空 | 鉴权密钥；本地 OSS 服务未开启鉴权时可为空 |

API mode 对应关系：

| `SAA_SAMPLE_MEM0_API_MODE` | 写入 endpoint | 检索 endpoint | 鉴权 header |
|---|---|---|---|
| `oss` | `POST /memories` | `POST /search` | `X-API-Key: <key>` |
| `platform` | `POST /v1/memories/` | `POST /v2/memories/search/` | `Authorization: Token <key>` |

注意：该样例验证的是 Mem0 OSS/Platform memory REST API，不是 OpenMemory UI
的 `/api/v1/memories` 管理 API。

## Mem0 service contract

测试团队可以使用官方 Mem0 OSS REST server，也可以使用基于 `mem0ai`
Python 包封装的本地 REST 服务。无论哪种方式，服务必须真实调用 Mem0 memory
引擎，不能用固定 JSON response 伪装。

`oss` 模式需要支持：

```text
POST /memories
  request:  { "messages": [...], "user_id": "...", "agent_id": "...", "run_id": "...", "metadata": {...}, "infer": false }
  response: Mem0 Memory.add(...) compatible JSON

POST /search
  request:  { "query": "...", "top_k": 5, "user_id": "...", "agent_id": "...", "run_id": "...", "metadata": {...} }
  response: Mem0 Memory.search(...) compatible JSON, usually { "results": [...] }
```

本地验证过的真实后端形态：

- Qdrant container 提供向量存储。
- `mem0ai` Python package 提供 `Memory.add(...)` / `Memory.search(...)`。
- `fastembed` 提供本地 embedding。
- 一个很薄的 FastAPI/uvicorn wrapper 只负责把 `/memories` 和 `/search`
  转给 Mem0 `Memory` 对象；wrapper 不生成固定响应。

## Run

无真实 Mem0 服务时，命令应当构建成功并显示测试被跳过：

```bash
./mvnw -f examples/agent-runtime-middleware-memory-mem0/pom.xml test
```

连接真实 Mem0 OSS-compatible REST 服务时：

```bash
export SAA_SAMPLE_MEM0_BASE_URL=http://localhost:8000
export SAA_SAMPLE_MEM0_API_MODE=oss
export SAA_SAMPLE_MEM0_API_KEY=
./mvnw -f examples/agent-runtime-middleware-memory-mem0/pom.xml test
```

连接 Mem0 Platform-compatible REST 服务时：

```bash
export SAA_SAMPLE_MEM0_BASE_URL=https://api.mem0.ai
export SAA_SAMPLE_MEM0_API_MODE=platform
export SAA_SAMPLE_MEM0_API_KEY=<your-api-key>
./mvnw -f examples/agent-runtime-middleware-memory-mem0/pom.xml test
```

## 端到端链路

```text
AgentExecutionContext(user input + agentStateKey)
  -> OpenJiuwenAgentRuntimeHandler.execute(...)
  -> openJiuwenRails(context)
  -> memoryRuntimeRail(context, Mem0RestMemoryProvider)
  -> POST /search
  -> OpenJiuwen ModelContext receives memory note
  -> simulated OpenJiuwen agent response
  -> POST /memories
  -> POST /search verifies the saved user-facing memory is queryable
```

这个测试从用户输入进入 handler 开始，经过 OpenJiuwen memory rail，再打到真实
Mem0-compatible REST 服务。它不是 provider 的孤立单元测试，也不是内嵌 HTTP
server 的伪装测试。
