---
name: fund_planning_skill
description: 模型驱动的理财卡资金汇聚与指定产品购买（通用工具版）
---

# fund_planning_skill

本 Skill 由模型逐步调用 `call_versatile` 完成：
理财卡余额查询 → 默认卡余额查询 → 资金判断 → 必要时转账 → 购买。

## 工具白名单（严格）

只允许调用以下工具：
- `call_versatile`

禁止调用 `ask_user` 或其他非白名单工具。

**例外：取消意图不受白名单限制。** 当用户表达终止意图（如"取消"、"取消购买"、"退出"、"stop"、"cancel"等），必须立即停止本 Skill，转而调用 `ask_user` 走全局取消流程（见 AgentRule 第五条第 4 款），不得由 Skill 内部自行处理。

## 固定参数

本 Skill 所有 `call_versatile` 调用的 `query_response_analysis_scripts` 参数固定为：
```
python fund_planning_skill/scripts/run_fund_planning.py
```

## 输入槽位（结构化）

从用户请求和上文对话中提取并保持一致：
- `wealth_card_tail`：理财卡尾号（可空）
- `product_id`：产品ID
- `product_name`：产品名称
- `buy_amount`：购买金额（数字）

## 执行顺序

### 第一步：提取槽位（仅思考，不调工具）

从用户输入和上文对话中识别 `wealth_card_tail`、`product_id`、`product_name`、`buy_amount`。

### 第二步：查询理财卡余额

```
call_versatile(
  query_description="查询尾号为{wealth_card_tail}的卡的余额",
  query_intent="查询账户余额",
  query_response_analysis_scripts="python fund_planning_skill/scripts/run_fund_planning.py",
  notice_context='{"phase":"wealth","buy_amount":{buy_amount}}'
)
```

必须传 `notice_context`，脚本会在理财卡余额不足时自动输出话术（不中断，不需 LLM 手动发）。

工具返回结构：
```json
{
  "account_id": "6605",
  "bank_card_number": "6222021234566605",
  "balance": "80,000.00",
  "currency": "001",
  "balance_numeric": 80000.0
}
```

### 第三步：判断余额是否充足

- 若 `balance_numeric >= buy_amount`，跳到第六步直接购买。
- 若 `balance_numeric < buy_amount`，继续查询默认卡余额。

### 第四步：查询默认储蓄卡余额（仅余额不足时）

```
call_versatile(
  query_description="查余额",
  query_intent="查询账户余额",
  query_response_analysis_scripts="python fund_planning_skill/scripts/run_fund_planning.py",
  notice_context='{"phase":"default","buy_amount":{buy_amount},"wealth_balance_numeric":{wealth_balance_numeric}}'
)
```

必须传 `notice_context`，脚本会在两卡余额总和仍不足时自动输出话术（不中断、不需 LLM 手动发）。其中 `wealth_balance_numeric` 从第二步返回中携带。

返回结构同第二步。根据返回的 `bank_card_number` 提取默认卡尾号。

判断逻辑：
- 若默认卡与理财卡是同一张卡 → 回复"只有一张卡，无法完成跨卡资金汇聚"，结束。
- 若两卡总额不足 → 回复"两张卡钱不够，无法完成本次购买"，结束。
- 若总额足够 → 计算缺口金额 = `buy_amount - wealth_balance_numeric`，进入转账。

### 第五步：转账（仅余额不足时）

```
call_versatile(
  query_description="从尾号{default_card_tail}的卡转账{gap_amount}元到尾号为{wealth_card_tail}的卡",
  query_intent="快速转账",
  query_response_analysis_scripts="python fund_planning_skill/scripts/run_fund_planning.py"
)
```

返回结构：
```json
{
  "status": "success",
  "from_account": "3344",
  "to_account": "6605",
  "amount": 18000.0,
  "requested_transfer_amount": 30000.0,
  "actual_transfer_amount": 18000.0,
  "remaining_transfer_amount": 12000.0,
  "transfer_satisfied": false,
  "currency": "CNY",
  "transaction_id": "tx-001",
  "requested_transfer_amount_str": "30000.00",
  "transfer_amount_str": "18000.00"
}
```

- `requested_transfer_amount`：本轮转账 query 中要求转入的差额金额。
- `actual_transfer_amount`：工作流本轮实际返回的 `transferAmount`。
- `remaining_transfer_amount`：`requested_transfer_amount - actual_transfer_amount` 的剩余差额，最小为 0。
- `transfer_satisfied`：仅当本轮 `status="success"` 且 `actual_transfer_amount >= requested_transfer_amount` 时为 `true`。

判断逻辑：
- 若 `status` 为 `"failed"` → 回复"转账失败，无法完成本次购买"，结束。
- 若 `status` 为 `"success"` 且 `transfer_satisfied=false` → **继续执行第五步转账**，并将下一轮 `query_description` 中的转账金额改为 `remaining_transfer_amount`。
- 若 `status` 为 `"success"` 且 `transfer_satisfied=true` → 进入第六步购买。

强约束：
- 第一次进入第五步时，`gap_amount = buy_amount - wealth_balance_numeric`。
- 若本轮返回 `transfer_satisfied=false`，则下一轮第五步中的转账金额必须严格使用本轮返回的 `remaining_transfer_amount`。
- 每一轮都只比较“本轮输入 query 的差额金额”和“本轮返回的 `actual_transfer_amount`”；不要拿新的 `transferAmount` 回头覆盖上一轮 query。
- 第五步允许在 Skill 内部连续执行多轮，直到 `transfer_satisfied=true` 才能进入第六步购买。

### 第六步：购买理财

```
call_versatile(
  query_description="购买理财产品：产品名称：{product_name}，产品代码：{product_id}，金额：{buy_amount}元",
  query_intent="理财选品购买",
  query_response_analysis_scripts="python fund_planning_skill/scripts/run_fund_planning.py",
  response_template_keys='["fund_planning_success", "fund_planning_buy_failed"]'
)
```

注意：**仅第六步购买**调用传 `response_template_keys`，前 5 步（查余额、转账）均不传该参数。

返回结构：
```json
{
  "status": "success",
  "product_id": "XLT1801",
  "product_name": "添利宝",
  "amount": 50000,
  "buy_status": "购买成功",
  "fail_cause": "",
  "transaction_id": "wealth-tx-001"
}
```

- 若 `status` 为 `"success"` → 回复"购买成功"。
- 若 `status` 为 `"failed"` → 回复"购买失败"并附带 `fail_cause`。

## query_description 格式规范

| query_intent | query_description 格式 | 示例 |
|---|---|---|
| 查询账户余额 | `查询尾号为{卡尾号}的卡的余额` 或 `查余额`（默认卡） | `查询尾号为6605的卡的余额` |
| 快速转账 | `从尾号{付款卡尾号}的卡转账{金额}元到尾号为{收款卡尾号}的卡` | `从尾号3344的卡转账30000元到尾号为6605的卡` |
| 理财选品购买 | `购买理财产品：产品名称：{名称}，产品代码：{代码}，金额：{金额}元` | `购买理财产品：产品名称：添利宝，产品代码：XLT1801，金额：50000元` |

## 强约束

- 购买类请求禁止首步直接调用购买，必须先查余额。
- 每轮只调用一个工具，工具返回后再做下一步决策。
- query_description 必须严格按照上表格式填写，归一化脚本依赖该格式解析参数。
- 余额比较必须使用工具返回的 `balance_numeric`。
- 默认按自动资金汇聚执行，禁止向用户发起追问。

## 终态回复模板

- 缺槽位：`缺少必要信息（理财卡尾号/产品ID/金额），无法执行资金汇聚。`
- 只有一张卡：`只有一张卡，无法完成跨卡资金汇聚。`
- 两张卡钱不够：`两张卡钱不够，无法完成本次购买。`
- 转账失败：`转账失败，无法完成本次购买。`
- 购买失败：`购买失败`
