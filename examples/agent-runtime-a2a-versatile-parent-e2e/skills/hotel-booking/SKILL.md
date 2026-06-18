---
name: versatile-request
description: 调用 Versatile 工作流工具时，按目标 REST API 的 inputs 结构直接输出完整 JSON。
---

# Versatile 请求体组装技能

调用 Versatile 工作流工具时，直接传入完整的请求体 JSON：

```json
{"inputs":{"query":"...","intent":"..."}}
```

## inputs 层字段

从用户消息中能提取出哪些字段，就填哪些。提取不出的用空字符串填充，不要编造。

| 字段 | 说明 | 格式 / 示例 |
|------|------|-------------|
| `query` | **JSON 字符串**，业务参数必须嵌套在这个 JSON 字符串内部，不要传普通自然语言文本。 | `"{\"person_name\":\"李四\",\"checkin_date\":\"2026-03-30\",\"arrival_city\":\"北京\"}"` |
| `intent` | 操作意图。 | `"订酒店"` / `"LATEST"` |

### `query` 内可包含的字段

| 字段 | 说明 | 示例值 |
|------|------|--------|
| `person_name` | 入住人姓名 | `"李四"` |
| `checkin_date` | 入住日期 | `"2026-03-30"` |
| `checkout_date` | 退房日期 | `"2026-04-03"` |
| `arrival_city` | 目的地城市 | `"北京"` |
| `hotel_name` | 用户选择的酒店名称 | `"美居宾馆"` |

## 调用示例

用户消息："请帮我订一家北京的酒店，3月30日入住，4月3日退房，我叫李四"

```json
{"inputs":{"query":"{\"person_name\":\"李四\",\"checkin_date\":\"2026-03-30\",\"checkout_date\":\"2026-04-03\",\"arrival_city\":\"北京\"}","intent":"订酒店"}}
```

用户消息中只包含酒店选择（如 "选美居宾馆"）：

```json
{"inputs":{"query":"{\"hotel_name\":\"美居宾馆\"}","intent":"LATEST"}}
```
