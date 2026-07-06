# Mock Workflows

可配置的 Versatile 子 agent Mock 上游，用于测试大 Agent 在不同下游表现下的编排能力。

## 启动

```bash
cd mock_workflows
pip install fastapi uvicorn python-dotenv loguru
python versatile_main.py
```

默认监听 `http://127.0.0.1:30001`。

## 架构

```
mock_workflows/
├── versatile_main.py       # FastAPI 入口
├── config/server.json      # 端口、SSE 格式、feature flags
├── workflows/*.json        # 原子意图（匹配规则 + 输出帧）
└── engine/                 # 匹配、流式输出、hooks
```

## 工作流端点

```
POST /v1/0/agent-manager/workflows/{workflow_id}/conversations/{conversation_id}
POST /v1/chat/{conversation_id}          # 旧版兼容
GET  /health
POST /admin/reload                       # 热加载 workflows/*.json
POST /reset_transfer_counter             # 重置转账计数器与余额状态
```

## 内置意图（workflows/）

| 文件 | 意图 | 说明 |
|------|------|------|
| `product_buy.json` | 购买签署 | 结构化 query 触发 |
| `fund_recommend.json` | 基金推荐 | |
| `wealth_recommend.json` | 理财推荐 | finance_recommend 测试用例使用 |
| `balance_query.json` | 查余额 | 含可选 BALANCE_MENU 卡片 |
| `transfer_round1.json` | 转账首轮 | TRANSFER_MENU，不发 End |
| `transfer_confirmed.json` | 转账确认续轮 | context 路由 |
| `transfer_no_confirm.json` | 转账拒绝 | error 帧 |
| `default.json` | 兜底 | |

## 增删改意图

1. 在 `workflows/` 新增或编辑 JSON 文件
2. 设置 `priority`（越大越优先）、`match` 规则、`output.frames`
3. 动态字段引用 hooks：`"text_hook": "wealth_product_filter_json"`
4. 执行 `POST /admin/reload` 或重启服务

## 配置

`config/server.json`：

- `sse_format`: `raw`（默认，对齐 v6）或 `wrapped`（对齐 interrupt 版 unwrap 格式）
- `features.enable_interrupt_menus`: 查余额时是否插入 BALANCE_MENU Custom 帧
- `features.stateful_balance` / `stateful_transfer`: 会话级余额/转账状态

环境变量（与旧版 mock 兼容）：

- `MOCK_SERVER_PORT` / `MOCK_SERVER_HOST`
- `MOCK_LICAI_BALANCE` / `MOCK_CHUXU_BALANCE`
- `MOCK_TRANSFER_AMOUNTS` / `MOCK_TRANSFER_MODE`
- `MOCK_BALANCE_DELAY_SECONDS`
- `MOCK_PRODUCT_BUY_SUCCESS`

## 测试

```bash
python -m unittest discover -s tests -v
```
