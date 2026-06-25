# agent-task-collector — 差旅事项收集子智能体

本模块是 `examples/travel` 多智能体示例中的纯 Java library。它是 `trip-planning-agent` 的子智能体，负责调用 mock MCP 工具收集日程、待办和差旅规则，并返回中文 Markdown 事项清单。

## 模块定位

- 提供 `TaskCollectorAgent#chat(String)` 同步入口。
- 提供 `TaskCollectorAgentBuilder`，方便上游或 A2A wrapper 构建 openJiuwen `ReActAgent`。
- 内置 mock MCP 工具：`travel_calendar_search`、`travel_todo_search`、`travel_policy_lookup`。
- 不启动 HTTP 服务，不暴露 A2A endpoint。
- 不调用 `trip-planning-agent`、`hotel-planning-agent` 或主规划智能体。

## 工具列表

| 工具 | 用途 |
|---|---|
| `travel_calendar_search` | 查询日期范围内的会议、拜访和固定日程 |
| `travel_todo_search` | 查询出差相关待办 |
| `travel_policy_lookup` | 查询酒店预算、星级、协议品牌和交通标准 |

## 本地运行

```bash
mvn -f examples/travel/agent-task-collector/pom.xml test
mvn -f examples/travel/agent-task-collector/pom.xml exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.travel.taskcollector.TaskCollectorSampleMain
```

LLM 配置通过环境变量读取，参考 `src/main/resources/llm.properties.example`。

## 输入示例

```text
我 2026-06-18 到 2026-06-20 去北京出差，帮我收集会议、待办和差标。
```

## 输出约束

输出为中文 Markdown，包含已识别差旅信息、日程事项、待办事项、差旅规则、缺失信息和给行程规划智能体的建议。
