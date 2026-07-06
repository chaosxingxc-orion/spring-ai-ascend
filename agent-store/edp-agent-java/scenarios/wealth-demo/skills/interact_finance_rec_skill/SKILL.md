---
name: interact_finance_rec_skill
description: >
  处理首次推荐完成后的后续交互式多轮理财产品推荐。
  触发词：换一批、再来一批、再推荐、还有别的吗、再看看别的、换几个看看、换个稳健型、换个低风险、换个短期、换个长期、收益高的、不要R5、不要高风险、追加条件、修改筛选条件、按收益排序。
  不要用于：首次推荐、产品选择确认、资金筹划、账户查询。
---

# 交互式理财筛选 Skill
后续交互推荐，执行架构：`call_mcp` → `call_versatile`（按顺序调用，禁止调用 `ask_user` 等非白名单工具）。取消意图（"取消"、"退出"、"stop"等）例外：立即停止本 Skill，转调 `ask_user` 走全局取消流程。

## 执行流程

### 第一步：解析用户当前输入获取用户偏好信息

从用户输入中识别偏好变化，映射为 `mcp_params`（字典，key 限以下 13 个字段，未提及的不输出）：

| 字段 | 含义 | 取值范围 |
|------|------|---------|
| filterRiskLevel | 风险等级 | 0=不筛选此项，1=风险等级R1低风险，2=风险等级R2稳健，3=风险等级R3平衡，4=风险等级R4进取，5=风险等级R5高风险。支持多选，使用\|分割，如：`"1\|2"` ；支持排除取补集 |
| filterDayProd | 日开/灵活产品 | 0=不筛选此项，1=筛选日开产品/灵活申赎 |
| filter06FixProd | 0-6个月期限 | 0=不筛选此项，1=筛选0-6个月期限产品 |
| filter612FixProd | 6-12个月期限 | 0=不筛选此项，1=筛选6-12个月期限产品 |
| filter12FixProd | 12个月以上期限 | 0=不筛选此项，1=筛选12个月以上期限产品 |
| filterConsignment | 产品销售渠道 | 0=不筛选此项，1=自营，2=代销。支持多选，使用\|分割，如：`"1\|2"` |
| filterLowestBuyAmt | 最低购买金额 | 0=不筛选此项，1=0-1万，2=1-5万，3=5-50万，4=50-500万，5=500万以上。支持多选，使用\|分割，如：`"1\|2"` |
| filterProdType | 产品类型 | 0=不筛选此项，1=固定收益类，2=商品及金融衍生品类，3=混合类，4=传统产品，5=结构性存款。支持多选和排除，多选用\|分隔如`"1\|2"` |
| filterCurrtype | 产品币种 | 0=不筛选此项，1=人民币，2=美元，3=其他币种。支持多选，使用\|分割，如：`"1\|2"` |
| filterStatus | 产品状态 | 0=不筛选此项，1=在售，2=暂无额度。支持多选，使用\|分割，如：`"1\|2"` |
| filterOrganization | 代销机构 | 0=不筛选此项，其他值-填写机构编码。GW=离磁工银理财，GY=工银理财，PAW=平安理财，EW=光大理财，ZY1=招银理财，YZX=信银理财，MS=民生理财，PWM=中邮理财，Y3=中银理财，Y04=苏银理财，PYB=湘银理财，Y05=兴银理财。支持多选用\|分割和排除，如：`"GW\|GY"` |
| QueryFundStatus | 在售状态 | 0=不筛选此项，1=只展示在售产品，2=只展示非在售产品 |
| sortType | 排序类型 | 0=默认排序，1=近1月年化收益排序，2=近3月年化收益排序，3=近6月年化收益排序，4=近1年年化收益排序，5=成立以来年化收益排序，6=七日年化收益排序 |

#### 条件继承规则（核心）

多轮对话中，用户输入分为以下三种意图类型，每种类型的条件继承行为不同：

**类型一：换一批类（仅换产品，条件不变）**

触发词：换一批、再来一批、再推荐、还有别的吗、再看看别的、换几个看看。

行为规则：
- 原样输出上一轮 `history_params` 作为本轮 `mcp_params`，不修改任何筛选参数（已推荐产品会自动去重，结果数量可能逐轮减少）

**类型二：追加/修改/排除条件类（增量更新）**

触发词：追加条件、加个条件、修改筛选条件、换个条件、排除XX、剔除XX、只要XX、换成XX。

行为规则：
- 基于上一轮 `history_params` 增量更新，**未提及的历史条件必须原样保留**（核心规则）
- 新增→添加字段；修改→替换字段值；取消→字段设为 `"0"`；排除→见下方排除逻辑
- 输出必须包含所有历史条件 + 新增/修改条件，未提及的字段不输出

**类型三：重置类（全部重置为默认）**

触发词：全部重置、重置为默认、重新开始推荐、恢复默认条件。

行为规则：清空所有筛选条件，不输出任何字段。

#### 排除逻辑统一规范

排除操作必须从字段**全集**中移除被排除的值，输出剩余项用 `|` 分隔。禁止使用负号前缀（如 `-4|-5`）。每次排除都从全集重新计算，而非从上一轮值计算。

| 字段 | 全集 |
|------|------|
| filterRiskLevel | `1\|2\|3\|4\|5`（R1~R5）|
| filterOrganization | `GW\|GY\|PAW\|EW\|ZY1\|YZX\|MS\|PWM\|Y3\|Y04\|PYB\|Y05`（12家）|
| filterLowestBuyAmt | `1\|2\|3\|4\|5`（0-1万~500万以上）|
| filterProdType | `1\|2\|3\|4\|5`（固定收益类~结构性存款）|

示例："不要R5" → 全集 `1|2|3|4|5` 排除5 → `"1|2|3|4"`

示例："推荐高风险的，持有周期12个月以上" → `{"filterRiskLevel": "5", "filter12FixProd": "1"}`

### 第二步：执行 MCP 脚本获取产品列表

```
call_mcp(
  script_command="python interact_finance_rec_skill/scripts/run_mcp_recommend.py",
  script_params='{"mcp_params": {...}, "empty_result_template_key": "mcp_result_empty"}'
)
```

`script_params` 中必须包含 `mcp_params`（按第一步映射表构造）和 `empty_result_template_key`（固定值 `"mcp_result_empty"`，用于 MCP 出错或结果为空时推送提示话术）。

**返回结构**：`{"products": [...], "total": int, "versatile_query": "...", "mcp_error": "...或null"}`

- 错误字段为 `mcp_error`（非 `error`）。
- **无论 MCP 结果如何，都必须继续执行第三步**。当 `mcp_error` 非空或 `total` = 0 时，将 `versatile_query` 作为第三步 `call_versatile` 的 `query_description` 降级获取产品。

### 第三步：调用低码平台获取补充信息

```
call_versatile(
  query_description="",
  query_intent="理财推荐",
  query_response_analysis_scripts="python interact_finance_rec_skill/scripts/run_versatile_normalize.py",
  response_template_keys='["product_recommend_success", "product_recommend_empty"]'
)
```

`query_description` 传空字符串（`versatile_query` 由 Rail 自动从缓存注入），其余两个参数为固定值。

**返回结构**：`{"products": [...], "bankCardNumber": "后四位", "total": int}`

- 返回空 / `products` 为空 → 回复"暂时无法获取理财产品信息，请稍后再试。"或"没有找到符合您要求的理财产品，您可以尝试调整筛选条件。"后结束。
- `products` 非空 → 输出 Markdown 表格，列：序号｜产品名称｜产品类型｜预期年化收益｜风险等级｜产品期限，字段依次取 `prodName`｜`prodType`（原值直出）｜`profitValue`｜`riskLevel`（1~5 转为 R1~R5）｜`limit_data`（如 182天、6个月）。超过5条仅展示前5条并附注"（共 {total} 个产品，已为您展示前5条）"。表格前加"为您推荐以下理财产品："，表格后加"您的理财卡尾号：{bankCardNumber}"及"如需购买或了解更多，请告诉我。"。禁止输出表中未列字段。


## 约束

- 禁止编造产品信息，只展示工具返回的真实数据
- 不要替用户做出选择，那是 `product_select_skill` 的职责
- 最多 10 轮推荐，超过后主动终止并告知用户"已为您推荐多轮产品，如需继续请重新开始推荐"
- 鉴权参数（MCP_SERVER_URL、MCP_ACCESS_TOKEN、MCP_APP_NAME）由 McpInterruptRail 通过 ProcessBuilder 环境变量自动注入 Python 子进程，禁止在 `script_params` 中传递；`empty_result_template_key` 必须由 LLM 传入
