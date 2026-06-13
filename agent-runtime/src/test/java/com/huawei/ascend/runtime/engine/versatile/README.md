# Versatile Adapter 测试说明

## 运行前提

远端 Versatile 服务必须可达，地址通过环境变量配置：

```bash
# 真实 Versatile API 示例
export VERSATILE_URL="http://7.213.200.213:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}"
export VERSATILE_URL_VARIABLES_PROJECT_ID="mock_project_id"
export VERSATILE_URL_VARIABLES_AGENT_ID="fb723468-c8ca-424b-a95f-a3e74b37e090"

# 或者使用默认配置（application.yaml 中的 packet.txt 地址）
```

---

## VersatileClientTest （6 个测试）

验证 HTTP 客户端：请求体发送、Content-Type 设置、SSE 流解析、错误处理。

| 测试方法 | 验证内容 |
|---|---|
| `sendsPostWithJsonBodyAndContentType` | POST 请求包含正确的 JSON body 和 `Content-Type: application/json` |
| `addsContentTypeWhenNotConfigured` | 配置未设置 Content-Type 时，自动补充 `application/json` |
| `streamsSseLinesToStream` | 多行 SSE 响应逐行返回，顺序正确 |
| `filtersEmptyLines` | SSE 流中的空白行被过滤 |
| `throwsOnHttpErrorStatus` | HTTP 500 返回 `VersatileClientException`，包含状态码 |
| `throwsOnConnectionRefused` | 连接拒绝时返回 `VersatileClientException` |

---

## VersatileStreamAdapterTest （11 个测试）

验证 SSE 行 → `AgentExecutionResult` 映射：标准事件、自定义事件、过滤逻辑、异常处理。

| 测试方法 | 验证内容 |
|---|---|
| `mapsMessageEventToOutput` | `message` 事件的 `text` 字段 → `OUTPUT` |
| `mapsMessageSummaryWhenFinished` | `message` 事件 `is_finished=true` → `OUTPUT(summary)` |
| `mapsWorkflowFinishedToCompleted` | `workflow_finished` → `COMPLETED(responseContent)` |
| `mapsWorkflowFinishedNoOutputsToCompletedEmpty` | `workflow_finished` 无 outputs → `COMPLETED("")` |
| `mapsEndEventToCompleted` | `end` 事件 → `COMPLETED` |
| `mapsExceptionToFailed` | `exception` 事件 → `FAILED(code, message)` |
| `filtersControlEvents` | `workflow_started`/`node_started`/`node_finished` → 过滤 |
| `mapsCustomEventToOutput` | 未知事件（如 `hotels_info`）→ `OUTPUT`，data 序列化为 JSON |
| `filtersCustomEventWithEmptyData` | data 为空的未知事件 → 过滤 |
| `stripsDataPrefixAndHandlesRawSse` | 自动去除 `data:` 前缀 |
| `skipsUnparseableLines` | 不可解析的行 → 跳过，继续处理后续行 |

---

## VersatileMessageAdapterTest （7 个测试）

验证 `AgentExecutionContext` → `VersatileHttpRequest` 转换：
URL 模板解析、路径安全校验、metadata 合入 body、header 合并。

| 测试方法 | 验证内容 |
|---|---|
| `resolvesUrlTemplate` | URL 模板中 `{conversation_id}` + `url-variables` → 完整 URL + query params |
| `rejectsPathTraversalInConversationId` | 包含 `..` 的 conversationId → `IllegalArgumentException` |
| `buildsBodyWithQueryAndMetadataFields` | A2A metadata（`intent`, `wap_userName`）按 `input-metadata-keys` 合入 `body.inputs` |
| `omitsMissingMetadataKeysFromBody` | metadata 缺失的 key 不出现在 body 中 |
| `mergesHeadersWithPassthroughOverride` | `passthrough-headers` 白名单中的 A2A metadata → REST header，覆盖 YAML 同名字段 |
| `extractsLastUserMessageAsQuery` | 最后一条 `ROLE_USER` 消息的文本 → `body.inputs.query` |
| `alwaysUsesPostMethod` | 请求方法恒为 POST |

---

## 手工端到端验证

需要可用的 Runtime + 可访问的 Versatile 远端服务。

**终端 1** — 启动 Runtime：
```bash
mvn spring-boot:run -f examples/agent-runtime-a2a-versatile-e2e/pom.xml
```

**终端 2** — 运行客户端：
```bash
mvn exec:java -f examples/agent-runtime-a2a-versatile-e2e/pom.xml \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.versatile.ManualVersatileClient
```

交互式粘贴 URL 行 + JSON body：
```
Paste URL line> /v1/mock_project_id/agents/fb723468.../conversations/test-001?type=controller&workspace_id=10
Paste JSON body> {"inputs":{"query":"预订酒店","intent":"订酒店","wap_userName":"张三"}}
```

**运行 @SpringBootTest（手工 Tag）**：
```bash
mvn test -f examples/agent-runtime-a2a-versatile-e2e/pom.xml \
  -Dtest=VersatileA2aE2eTest
```
