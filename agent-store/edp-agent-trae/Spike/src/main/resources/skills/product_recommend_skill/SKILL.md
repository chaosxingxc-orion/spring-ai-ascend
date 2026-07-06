---
name: product_recommend_skill
description: >
  根据用户理财意图进入理财推荐流程并展示推荐产品列表。
  触发词：推荐理财产品、帮我看看理财、有什么理财可以买。
  不要用于：产品选择确认、资金筹划、账户查询。
---

# 产品推荐 Skill

## 职责

接收用户的理财购买意向，通过 `call_versatile` 触发理财推荐工作流，
获取推荐产品列表，按清晰格式展示给用户，并告知用户可进入选品流程。

## 工具白名单（严格）

只允许调用以下工具：
- `call_versatile`

禁止调用 `ask_user` 或其他非白名单工具。

**例外：取消意图不受白名单限制。** 当用户表达终止意图（如"取消"、"取消购买"、"退出"、"stop"、"cancel"等），必须立即停止本 Skill，转而调用 `ask_user` 走全局取消流程（见 AgentRule 第五条第 4 款），不得由 Skill 内部自行处理。

## 固定参数

本 Skill 调用 `call_versatile` 时的参数分为两类：

**A. 强制写死（【强约束】LLM 不得根据用户原话改写、不得替换）**
- `query_description`：**固定为 `"推荐理财产品"`**
- `query_intent`：**固定为 `"理财选品购买"`**

> 说明：用户的风险偏好 / 产品类型等自然语言描述**不要**塞进 `query_description`；
> 下游工作流由 `query_intent` 路由，本 skill 不依赖 `query_description` 携带筛选条件。

**B. 模板默认值（按本 SKILL.md 给定值传入即可，部署环境/工作流升级时可能调整，不视为 LLM 错填）**
- `query_response_analysis_scripts`：默认 `"python product_recommend_skill/scripts/run_product_recommend_skill.py"`
  —— 本路径相对 `skills/` 目录，rail 会在沙箱内/外自动 `cd` 到对应根目录后执行；如部署侧需要绝对路径或不同入口文件，由该 skill 的维护者在本文档中同步更新即可。
- `response_template_keys`：默认 `'["product_recommend_success", "product_recommend_empty"]'`
  —— 数组下标 0 = 沙箱脚本 `status=success` 时的话术 key，下标 1 = `status=failure` 时的话术 key；
  如未来工作流新增第三种归一化结果或调整话术映射，由 skill 维护者在本文档中同步更新。

## 执行流程

### 第一步：直接调用 `call_versatile`（无需提取偏好、无需思考拼参）

请按下列示例逐字传入参数。其中 `query_description` / `query_intent` **必须与示例完全一致**；
`query_response_analysis_scripts` / `response_template_keys` 直接复制本文档当前给定值即可：

```
call_versatile(
  query_description="推荐理财产品",
  query_intent="理财推荐",
  query_response_analysis_scripts="python product_recommend_skill/scripts/run_product_recommend_skill.py",
  response_template_keys='["product_recommend_success", "product_recommend_empty"]'
)
```

**严禁**对 `query_description` / `query_intent` 的反例（无论用户原话是什么）：
- ❌ `query_description="买理财"` / `"我要买理财"` / `"购买理财产品"`
- ❌ `query_description="查询账户余额"` / `"快速转账"` / `"理财选品购买"`
- ❌ `query_description="推荐低风险理财，关键词：固收，风险等级：R2"`（不要把偏好塞进来）
- ❌ `query_intent="查询账户余额"` / `"理财选品购买"` / 任何非 `"理财推荐"` 的值

工具返回结构：
```json
{
  "products": [
    {
      "productCode": "XLT1801",
      "productName": "工银理财「添利宝」净值型理财产品(XLT1801)",
      "productType": "固定收益类",
      "profitValue": "3.2%",
      "riskLevel": "R2"
    }
  ],
  "bankCardNumber": "6605",
  "total": 3
}
```

### 第二步：处理返回结果

**若 products 为空（total == 0）：**

由 rail 自动从 `response_template_keys[1]` 取 `product_recommend_empty` 话术输出，
本 Skill 直接以 final answer 总结结束，**不要**再调用其他工具，**不要**自行复述话术。

**若 products 不为空但 bankCardNumber 为空：**

此时归一化脚本已自动注入 `ui_notice`，由 rail 直接北向输出固定话术
`product_recommend_no_card`（"当前账户没有绑定借记卡"）。
本 Skill 必须**立即终止流程**，以 final answer 总结结束，**禁止**再次调用 `call_versatile`、
**禁止**复述话术、**禁止**进入选品/资金筹划。

**若 products 不为空且 bankCardNumber 不为空：**
按下方格式展示产品列表（最多展示前 5 条）：

---
为您推荐以下理财产品：

| 序号 | 产品名称 | 产品类型 | 预期年化收益 | 风险等级 |
|------|----------|----------|-------------|----------|
| 1 | {productName} | {productType} | {profitValue} | {riskLevel} |
| 2 | ... | ... | ... | ... |

您的理财卡尾号：{bankCardNumber}

如需购买，请告诉我您想选择哪款产品及购买金额。
---

## 字段说明

| 字段 | 说明 |
|------|------|
| productCode | 产品代码（内部唯一标识，选品时需要） |
| productName | 产品全名 |
| productType | 产品类型：固定收益类 / 混合类 / 权益类 |
| profitValue | 预期年化收益率，如 3.2% |
| riskLevel | 风险等级：R1（最低）~ R5（最高） |
| bankCardNumber | 用户理财卡后四位，如 6605 |

## 约束

- 禁止自行编造产品信息，只展示 `call_versatile` 返回的真实数据。
- 推荐列表超过 5 条时截取前 5 条，末尾加提示"（共 {total} 个产品，已为您展示前 5 条）"。
- 不要替用户做出选择，那是 product_select_skill 的职责。
- 每次执行只调用一次 `call_versatile`，不重复调用。