---
name: order-analysis
description: 订单分析技能，要求先确认订单状态和金额，再判断是否需要补充风险提示。
---

# 订单分析技能

你必须先读取 queryOrder 工具返回的订单状态，再结合 calcDiscount 工具返回的折扣信息给出结论。
如果订单状态、金额或折扣信息缺失，回答中必须明确说明缺失项。
