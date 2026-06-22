# 研报生成方法论 — 卖方研究做法调研与映射

> 本文档是 [`RESEARCH_REPORT_ENGINE.cn.md`](RESEARCH_REPORT_ENGINE.cn.md) 的方法论依据,记录设计前对**摩根大通等头部投行卖方研究做法**及**已发表多智能体长文生成架构**的调研,并给出到引擎设计的映射,供团队复用与设计留痕。
>
> 范围说明:本调研基于**公开来源**(投行/学界/业界公开资料),聚焦公开的方法论,**非任一机构的内部流程文档**。每个非平凡论断均附引用。
>
> 产品范围提示:本客户(商业银行)产品为**基金/债券/板块策略**三类,无个股二级研究。下文以卖方个股研究为方法学母本,其中**报告骨架、单一决策者、独立复核、SA 签发、三角验证、grounding/限轮反馈等通用原则**已落到三类报告;个股 DCF/可比的**具体估值算法**为方法学来源,不再作为产品形态。

---

## 1. 研报的解剖 — 标准骨架

卖方研报有公认骨架:**封面摘要(评级 Overweight/Neutral/Underweight + 12 个月目标价 + 论点速览)→ 公司近况 → 投资论点 → 盈利模型与估计(收入/EBITDA/EPS/FCF)→ 估值(DCF + 可比公司,有时加先例交易)→ 风险因素 → 强制披露**。([Wall Street Prep](https://www.wallstreetprep.com/knowledge/sample-equity-research-report/);[CFI](https://corporatefinanceinstitute.com/resources/valuation/equity-research-report/);[AlphaSense](https://www.alpha-sense.com/resources/equity-research-guide/))

什么让它"投行级":
- **投资论点是脊柱**:为什么跑赢/跑输、什么催化剂推到目标价;强论点先摆牛熊两面再定调。([Wall Street Prep](https://www.wallstreetprep.com/knowledge/sample-equity-research-report/))
- **数字内部勾稽一致**:盈利预测是估值地基,每股目标价必须从企业价值正确桥接到股权价值(减净负债、少数股东、稀释股本)——最常见的错就是这个桥。([Ryan O'Connell CFA](https://ryanoconnellfinance.com/dcf-valuation-multiples/))
- **既是分析也是说服工具**:卖方报告意在驱动客户行动;评级是相对评级,真正信号在论点与风险叙事里。([Crispidea](https://www.crispidea.com/how-to-read-an-equity-research-report-guide/))

## 2. 分工 — 研究台角色

股票研究是扁平的倒金字塔:
- **资深/首席分析师**:拥有评级与论点、对接管理层/投资者、出想法;几乎不自己建模或扒数据。([Mergers&Inquisitions](https://mergersandinquisitions.com/equity-research-analyst/))
- **副手(Associate)**:盈利/行业模型、敏感性、新闻摘要、渠道调研、起草。([CFI 职位描述](https://corporatefinanceinstitute.com/resources/career/equity-research-job-description/))
- **研究总监**:定议程、配资源、把标准。([Wall Street Mojo](https://www.wallstreetmojo.com/equity-research-jobs/))
- **监督分析师(SA)**:持 FINRA Series 16 牌照的把关人,**每篇报告发布前审核合规与准确性**。([FINRA Rule 2241](https://www.finra.org/rules-guidance/rulebooks/finra-rules/2241))

## 3. 数据输入 — 四层栈

1. **一致预期/明细估计**:I/B/E/S 为行业标准(EPS、收入、EBITDA、目标价、买卖评级;consensus + 逐分析师明细;2.3 万+ 公司;每天更新最多 5 次)。([Baruch I/B/E/S 指南](https://guides.newman.baruch.cuny.edu/Earnings);[LSEG](https://www.lseg.com/en/data-analytics/financial-data/company-data/ibes-estimates))
2. **公司财报**:10-K/10-Q/8-K,模型与比率的基础。
3. **电话会纪要**:定性/文本信号,越来越多被 NLP 处理。
4. **实时行情**:股价 + 盈利惊喜跟踪,形式化为 **SUE(标准化盈利惊喜)**;只用约 90 天内、每位分析师最新估计以防陈旧/重复计数。([arXiv 2511.15214](https://arxiv.org/html/2511.15214v2))

> 可移植原则:**新鲜度窗口 + 最新覆盖去重**。

## 4. 分析方法 + 如何收敛到一个评级

分析师跑 **DCF**(内在、多期、假设敏感)与**可比公司倍数**(市场法;EV 类倍数配企业价值,股权类倍数配市值)。([Auxo Capital](https://auxocapitaladvisors.com/multiples-vs-dcf-vs-precedent-transactions/);[Street of Walls](https://www.streetofwalls.com/finance-training-courses/investment-banking-technical-training/comparable-company-analysis/))

**收敛靠三角验证**:无单一方法权威,一种方法的弱点被另一种补上,**DCF 与可比重叠区即最高置信区间**;大幅背离时要去查原因(增长/风险/情绪分歧),**而非直接平均**。([Ryan O'Connell CFA](https://ryanoconnellfinance.com/dcf-valuation-multiples/))

> 学界诚实发现:分析师常把 DCF 当"传达观点的可信载体",目标价/评级实际更受倍数与判断驱动。([Emerald JAAR](https://www.emerald.com/jaar/article/26/6/108/1267281/Financial-analysts-use-of-industry-specific-stock))

## 5. 长文一致性(多人协作下)

1. **论点为脊柱 + 标准模板/house style**:评级→估计→估值→目标价全回链一个论点;模板防漏项与各写各的。([Finzer 模板](https://finzer.io/en/blog/equity-research-report-template))
2. **发布前编辑/制作审查**:在合规之前抓图表错配、数字不一致、措辞含糊。([The Ink Corporated](https://www.theinkorporated.com/insights/equity-research-reports-pre-publication-review/))
3. **投资评审委员会(IRC)**:机构层面强制 house view。
4. **监督分析师 + 合规**:FINRA Rule 2241 要求研究与投行分离、平衡风险/收益讨论、**分析师认证**、封面披露、记录留存。([FINRA 2241](https://www.finra.org/rules-guidance/rulebooks/finra-rules/2241);[InnReg](https://www.innreg.com/resources/finra-rules/2241-research-analysts-and-research-reports))

## 6. 已发表的多智能体 LLM 架构

- **STORM**(Stanford,NAACL 2024):预写(多视角提问 + 检索 grounding)产出大纲,再分节撰写带引用 —— **先定结构再填充**;较 outline-RAG 基线组织性 +25%、覆盖 +10%。([STORM](https://storm-project.stanford.edu/research/storm/);[GitHub](https://github.com/stanford-oval/storm))
- **FinCon**(NeurIPS 2024):模仿投资公司的**经理-分析师层级**,**经理唯一决策者**;双层风控 + "信念选择性传播"(只把结论给相关 agent,省通信)。([FinCon](https://openreview.net/forum?id=dG1HwKMYbC))
- **FinTeam**(arXiv 2025):四 agent 顺序流水线(文档分析→分析师→会计→顾问),一致性来自顺序依赖;接受率 62%,超 GPT-4o。([arXiv 2507.10448](https://arxiv.org/pdf/2507.10448))
- **AutoGen earnings-call 框架**(arXiv 2410.01039):**Writer ↔ 多评审(分析师/心理/编辑/客户)限轮反馈循环**(上限 10 轮)。**最对口**,但仍有 **83% 情形被人工报告偏好**,前瞻性分析与风险深度偏弱。([arXiv 2410.01039](https://arxiv.org/html/2410.01039v1))

> 范围说明:BloombergGPT / FinGPT / 摩根大通 IndexGPT、DocLLM 属**数据/信号提取层**(文档理解、情绪、主题篮子),非研报生成流水线,故不入编排层。([BloombergGPT](https://arxiv.org/abs/2303.17564);[DocLLM](https://arxiv.org/pdf/2401.00908);[IndexGPT](https://hyscaler.com/insights/jpmorgan-indexgpt-ai-thematic-investing-tool/))

---

## 7. 到引擎的映射

| 投行做法 | 引擎对应 |
|---|---|
| 倒金字塔分工 + SA | **9 子智能体**:首席(唯一决策者)+ 建模/数据/估值/行业副手 + 单一撰写者 + 评审 + 合规门 |
| 论点为脊柱 + house view | `LeadManagerAgent` 定 thesis/rating/target,写作与评审回链 |
| 数据四层 + 新鲜度去重 | `ResearchDataSource` 四层 SPI + `FreshnessPolicy`(90 天窗口、最新覆盖) |
| DCF↔可比三角验证、背离回调和 | `ConvergenceCheck`:CONVERGENT 取重叠区,**DIVERGENT 回首席调和而非平均** |
| 数字内部一致(企业→股权桥) | 纯 Java `calc` 全算、`DcfModel` 显式做桥;`NumericConsistencyChecker` 确定性核对 |
| 模板 + 编辑审查 + 限轮 | 固定大纲 + writer↔critic **有界改稿循环** |
| FINRA 合规 + SA 签发 | `ComplianceAgent` 出认证/评级定义/披露 + **强制 SA 签发** |
| STORM 先大纲 / FinCon 单决策者 / AutoGen 限轮 | 流水线 PLAN→…→CRITIQUE 的整体编排 |
| 83% 落后人工的警钟 | 引擎**定位为分析师增强草拟器,强制人工/SA 签发**,非自主发布者 |

---

## 关键来源

- 报告解剖:[Wall Street Prep](https://www.wallstreetprep.com/knowledge/sample-equity-research-report/)、[CFI](https://corporatefinanceinstitute.com/resources/valuation/equity-research-report/)
- 角色分工:[Mergers&Inquisitions](https://mergersandinquisitions.com/equity-research-analyst/)、[Wall Street Mojo](https://www.wallstreetmojo.com/equity-research-jobs/)
- 数据:[Baruch I/B/E/S 指南](https://guides.newman.baruch.cuny.edu/Earnings)、[LSEG I/B/E/S](https://www.lseg.com/en/data-analytics/financial-data/company-data/ibes-estimates)
- 估值收敛:[Ryan O'Connell CFA](https://ryanoconnellfinance.com/dcf-valuation-multiples/)、[Emerald JAAR](https://www.emerald.com/jaar/article/26/6/108/1267281/Financial-analysts-use-of-industry-specific-stock)
- 一致性/合规:[FINRA Rule 2241](https://www.finra.org/rules-guidance/rulebooks/finra-rules/2241)、[The Ink Corporated](https://www.theinkorporated.com/insights/equity-research-reports-pre-publication-review/)
- 多智能体架构:[STORM](https://storm-project.stanford.edu/research/storm/)、[FinCon](https://openreview.net/forum?id=dG1HwKMYbC)、[FinTeam](https://arxiv.org/pdf/2507.10448)、[earnings-call 框架](https://arxiv.org/html/2410.01039v1)
