---
name: product_select_skill
description: >
  接收用户的选品输入（产品 + 金额），通过 ask_user 输出固定话术与用户确认。
  触发词：我选第X个、买这个、选XXXXX、我要买XX元、购买第X个、购买XX产品。
  当用户表达购买/选择意图且上文存在产品推荐列表时，必须先使用本 skill 处理选品信息，禁止直接跳到资金筹划。
  不要用于：产品推荐查询、资金筹划、账户余额查询。
---

# 产品选择 Skill

## 职责

从上文产品列表中识别用户当前输入的「产品」与「购买金额」，根据缺失情况调用一次
`ask_user` 输出**固定话术**（话术内容由 AgentRule.md 中 `scripts` 配置项提供，由
AskUserRail 从 session 写到北向 `interrupt_start` 事件）。

本 skill 在一次执行中**只调用一次** `ask_user`，调用后立即结束当轮，等待用户下一轮输入。

## 工具白名单（严格）

只允许调用以下工具：
- `ask_user`

禁止调用 `call_versatile`、`recommend_product` 或其他非白名单工具。

**例外：需区分全局取消与选品重选。** 当用户明确表达全局取消/终止购买（如"取消"、"取消购买"、"不买了"、"退出"、"stop"、"cancel"），必须立即停止本 Skill，转而调用 `ask_user` 走全局取消流程（见 AgentRule 第五条第 4 款），不得由 Skill 内部自行处理。若用户在选品确认语境下回复"否"、"不确认"、"重新选择"，不视为全局取消，不调用 `cancel_task`；应调用一次 `ask_user`，输出 `product_recommend_success` 固定话术，让用户重新选择产品和金额。

## 执行流程

### 第一步：从上文找产品列表

从当前对话上文中查找已展示的产品列表（`productCode` / `productName` / `productType` / `profitValue` / `riskLevel`）。

> **只看最近一轮推荐**：一次会话中可能有多轮 `product_recommend_skill` / `interact_finance_rec_skill` 输出的产品列表，本 skill **仅**从“距当前用户输入最近的一轮推荐输出”中取列表（包括序号、productName、productCode 的查找范围）；**禁止**跨轮拼接、禁止回背更早轮次的推荐列表去匹配。若多轮中同一产品在不同轮次位于不同序号，一律以最新一轮为准。

- **若上文中没有产品列表（或最新一轮推荐为空）** → 直接按 Case D 的写法调用一次 `ask_user`（`response_template_status="invalid"`、`response_template_vars={}`），由 AskUserRail 复用 `product_select_invalid` 话术 "抱歉没有理解您的意思，请重新输入" 北向输出后结束当轮。**禁止**直接以 final answer 输出该话术、**禁止**跳过 `ask_user` 直接结束。

### 第二步：解析用户当前输入

从用户输入中提取两个槽位：

| 槽位 | 提取方式 |
|------|----------|
| 产品（productName） | 序号（"第7个" → 上文列表中第 7 项 productName）/ 产品代码（"24YO0028"）/ 产品名称关键词（"邮鸿宝"） |
| 金额（amount） | 见下面“金额解析规则” |

#### 金额解析规则（必须严格遵守）

1. **输入形式**：金额可以带单位也可以不带单位，可以是阿拉伯数字也可以是中文数字，可以含小数点。
2. **单位识别**（不区分大小写、不区分全半角）：
   - 有主单位“元”/“块”/“块钱” → 主单位是元
   - “万” → × 10000；“千” → × 1000；“百” → × 100；“毛”/“角” → × 0.1；“分” → × 0.01
   - 无任何单位的纯数字默认表示“元”
3. **中文数字表达**：“三”=3、“五”=5、“十”=10、“一百二十”=120、“三千五百”=3500；中文复合如 “三块五毛八” = 3 + 0.5 + 0.08 = 3.58；“三块一毛” = 3.1；“三万五千” = 35000。
4. **小数位数**：只保留到小数点后两位（超过两位则裁为多值表达走 invalid，如 “3.123元”）。
5. **不准推算**：不要凭空填默认金额；如果完全识别不出金额门限 → 走 missing_amount 分支。

#### 几个示例

| 输入 | 解析后 amount（两位小数字符串） |
|------|----------------------------|
| `3` | `3.00` |
| `3.1` | `3.10` |
| `3.58` | `3.58` |
| `3元` / `3块` / `3块钱` | `3.00` |
| `3.1元` | `3.10` |
| `三块一毛` | `3.10` |
| `三块五毛八` / `三元五角八分` | `3.58` |
| `5000元` / `5千` / `五千元` | `5000.00` |
| `5万` / `五万` | `50000.00` |

### 第二步.25：选品确认后的重选回复

若用户在选品确认语境下回复"否"、"不确认"、"重新选择"，表示不确认当前产品/金额组合，需要回到产品列表继续选择。此时不要走全局取消流程，不调用 `cancel_task`，直接调用一次 `ask_user`：

- `response_template_status="reselect"`
- `response_template_vars={}`

该分支通过 `product_recommend_success` 固定话术提示用户重新选择产品和金额。

### 第二步.5：合法性校验（优先于最终决策）

按顺序判断，任一命中则选 `invalid`：

1. **多值检测**：用户输入中同时包含≥2个产品提及（序号/代码/名称任意组合超过 1 个）。例："买第一支17元，第二支21元"。
2. **多值检测**：用户输入中同时包含≥2个金额表达。
3. **存在性检测**：解析出的序号 > 上文产品列表长度，或 productName / productCode 在上文列表中不存在。例：列表只有 3 支时输入"第五支"。
4. **一致性检测**：用户同时给了序号 + 名称/代码，但二者在上文列表中指向不同条目。例："第一支 DTEL"但 DTEL 是列表第二支。
5. **金额正负检测**：解析后 amount 不是**严格大于 0** 的数值（包括 0、0元、负数、表达不明确导致不是有限正数）。例："0元"、"-100"、"零元"。
6. **金额精度检测**：解析后 amount 小数位超过 2 位（如 "3.123"、"3.1415元"）。

f以上任一命中 → 跳过第三步表格，直接以 `response_template_status="invalid"` 、`response_template_vars={}` 调用 `ask_user`。

### 第三步：根据缺失情况调用一次 `ask_user`

按下表选取 `response_template_status` 与 `response_template_vars`：

| 抽取结果 | response_template_status | response_template_vars |
|---|---|---|
| 用户在选品确认语境下回复"否" / "不确认" / "重新选择" | `reselect` | `{}` |
| 多产品 / 多金额 / 产品不存在 / 序号与名称不一致 | `invalid` | `{}` |
| 产品 ✅ + 金额 ✅ | `confirm` | `{"amount": "<金额>", "productName": "<产品名>"}` |
| 产品 ❌（含两者都缺） | `missing_product` | `{}` |
| 产品 ✅ + 金额 ❌ | `missing_amount` | `{}` |

### 第四步：固定参数

本 skill 所有 `ask_user` 调用的固定参数：

- `response_template_keys`：固定直接传下面这段 **JSON 对象（dict）**，不要在外层额外包单引号或双引号，也不要先转成字符串：

  ```json
  {"confirm": "product_select_confirm", "missing_product": "product_select_missing_product", "missing_amount": "product_select_missing_amount", "invalid": "product_select_invalid", "reselect": "product_recommend_success"}
  ```

- `question`：免底文本（话术参数缺失时才使用）；正常情况下话术由 rail 从 AgentRule.md 取，
  仅作为模型遵从工具 schema 的占位。建议使用与最终话术语义一致的简短文本。
- `response_template_vars`：`confirm` 分支直接传 JSON 对象（dict），如 `{"amount": "50000.00", "productName": "..."}`；`reselect` / `missing_product` / `missing_amount` / `invalid` 分支直接传 `{}`。不要在外层额外包任何引号。

### 调用示例

**Case A：产品 ✅ + 金额 ✅**

```
ask_user(
  question="请确认是否购买50000.00元邮鸿宝理财产品",
  response_template_keys={"confirm": "product_select_confirm", "missing_product": "product_select_missing_product", "missing_amount": "product_select_missing_amount", "invalid": "product_select_invalid", "reselect": "product_recommend_success"},
  response_template_status="confirm",
  response_template_vars={"amount": "50000.00", "productName": "中邮理财邮鸿宝28号人民币理财产品"}
)
```

含中文单位与小数的等价示例：用户输入 "第二支，三块五毛八" → amount 填 `"3.58"`；question 可设为 `"请确认是否购买3.58元xxx理财产品"`。

**Case B：产品 ❌**

```
ask_user(
  question="您可以告诉我想要购买第几支产品",
  response_template_keys={"confirm": "product_select_confirm", "missing_product": "product_select_missing_product", "missing_amount": "product_select_missing_amount", "invalid": "product_select_invalid", "reselect": "product_recommend_success"},
  response_template_status="missing_product",
  response_template_vars={}
)
```

**Case C：产品 ✅ + 金额 ❌**

```
ask_user(
  question="请问您购买的金额是多少",
  response_template_keys={"confirm": "product_select_confirm", "missing_product": "product_select_missing_product", "missing_amount": "product_select_missing_amount", "invalid": "product_select_invalid", "reselect": "product_recommend_success"},
  response_template_status="missing_amount",
  response_template_vars={}
)
```

**Case D：多产品 / 多金额 / 产品不存在 / 序号名称不一致**

```
ask_user(
  question="抱歉没有理解您的意思，请重新输入",
  response_template_keys={"confirm": "product_select_confirm", "missing_product": "product_select_missing_product", "missing_amount": "product_select_missing_amount", "invalid": "product_select_invalid", "reselect": "product_recommend_success"},
  response_template_status="invalid",
  response_template_vars={}
)
```

**Case E：选品确认后回复“否” / “不确认” / “重新选择”**

```
ask_user(
  question="请重新选择想购买的产品和金额",
  response_template_keys={"confirm": "product_select_confirm", "missing_product": "product_select_missing_product", "missing_amount": "product_select_missing_amount", "invalid": "product_select_invalid", "reselect": "product_recommend_success"},
  response_template_status="reselect",
  response_template_vars={}
)
```

> 上述示例中 `response_template_keys` / `response_template_vars` 都直接传 JSON 对象（dict），
> **不要**在外层多包一层单引号或双引号。Rail 会兼容字符串形式，但本 skill 统一按对象传。

## 约束

- 一次执行**只调用一次** `ask_user`，调用后当轮立即结束。
- **仅使用最近一轮推荐结果**：多轮推荐场景下，序号、productName、productCode 的匹配范围均仅限于距当前用户输入最近的那一轮推荐输出；禁止跨轮拼接、禁止回背更早轮次。
- `response_template_vars` 中的 `productName` 必须取自**最新一轮**产品列表中的 `productName` 字段，不要自行编造。
- `amount` 必须是严格大于 0 的数值字符串，**固定保留两位小数**（例如 `"3.00"`、`"3.10"`、`"3.58"`、`"50000.00"`）；不要加千分位逗号、不要带单位、不要使用中文数字。`question` 里也要使用同一个两位小数的金额。
- 用户回复"确认"等二次回复属于**新的一轮**，由 LLM 在下一轮根据上下文继续处理（确认 → 进入资金筹划）。本 skill 不负责二次确认。
- **用户回复"取消"/"不买了"等明确终止购买表达后**，终止选品，转而调用 `ask_user` 走全局取消流程（见 AgentRule 第五条第 4 款），不得自行回复"已取消"等话术。
- **用户在选品确认语境下回复"否"/"不确认"/"重新选择"后**，不走全局取消流程，不调用 `cancel_task`；改为调用 `ask_user`，使用 `response_template_status="reselect"` 输出 `product_recommend_success` 固定话术，让用户重新选择产品和金额。
