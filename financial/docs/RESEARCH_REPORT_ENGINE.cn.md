# 研报生成引擎(多智能体)— 设计与说明

> 金融行业智能体 · 归属 `financial/`(银行客户工作区)· 基于 spring-ai-ascend 平台与 `a2a-shared-memory` 共享记忆套件构建。
> 包路径:`com.bank.financial.research`。
>
> **产品范围:** 本客户为商业银行,无单一股票二级研究(不含「个股」研报)。报告按两个轴组织——**按视角**(宏观与政策、行业主题/板块策略)与**按标的**(基金/FOF、债券/固收)。四类复用同一套架构:共享黑板单一事实源、确定性计算、单声道撰写、独立评审、合规治理、跨运行经验。

把"研究报告生产"建模为一支**研究小组的多智能体协作**:若干专精子智能体围绕一块**共享黑板(单一事实源)**分工,经"规划→数据→分析→首席收敛→撰写→评审→合规"的流水线,产出**评级/结论、关键指标与多章节长文一致**的研究报告。核心设计目标:**生成效果可控、数据接入可插拔、数字可计算可核验、风格与观点一致。**

---

## 1. 方法论依据(借鉴头部投行做法)

- **报告骨架(以结论为脊柱)**:摘要/评级 → 核心论点 → 量化分析 → 风险/情景 → 配置建议 → 披露;评级、关键指标、结论逐层勾稽([Wall Street Prep](https://www.wallstreetprep.com/knowledge/sample-equity-research-report/))。
- **分工与治理**:首席分析师拥有评级与论点(唯一决策者),副手做量化/数据/风险,**持牌监督分析师(SA)**按 FINRA Rule 2241 复核签发([FINRA 2241](https://www.finra.org/rules-guidance/rulebooks/finra-rules/2241))。
- **多智能体长文架构**:STORM(先大纲 + grounding,再分节撰写)、FinCon(经理-分析师层级 + 单一决策者)、AutoGen earnings-call(writer↔critic 限轮反馈循环)([STORM](https://storm-project.stanford.edu/research/storm/);[FinCon](https://openreview.net/forum?id=dG1HwKMYbC);[arXiv 2410.01039](https://arxiv.org/html/2410.01039v1))。
- **诚实定位**:已发表最佳研报生成系统仍有 **83% 情形被人工报告偏好**([arXiv 2410.01039](https://arxiv.org/html/2410.01039v1))。本引擎定位为**分析师增强草拟器**,产出须经 SA 复核签发,**不是自主发布者**。

---

## 2. 四类报告与子智能体

四条流水线共享 `RunContext`(黑板/模型/预算/降级)、`ReportModel`、`PipelineProgress`、经验与可观测;各自有专精 agent 与确定性计算骨架。

| 报告类型 | 引擎 | 智能体(role) | 数字骨架(确定性计算) |
|---|---|---|---|
| **宏观与政策** | `MacroReportEngine` | planner / data(指标录入)/ analysis / lead-manager(策略) / writer / critic / compliance | `MacroCalc`:增长/通胀/景气/流动性打分 → 综合分 + 资产配置倾向(权益/债券/中性) |
| **基金 / FOF** | `FundReportEngine` | planner / data(NAV 录入)/ performance / risk / lead-manager / writer / critic / compliance | `FundCalc`:累计/年化收益、年化波动、夏普、最大回撤、Calmar、α-β |
| **债券 / 固收** | `BondReportEngine` | planner / data / rates / credit / lead-manager / writer / critic / compliance | `BondCalc`:YTM(二分)、Macaulay/修正久期、凸性、信用利差、久期 stance |
| **行业主题 / 板块策略** | `ThematicReportEngine` | planner / data(宏观录入)/ sector-impact / lead-manager / writer / critic / compliance | `SectorImpactModel`:宏观因子(方向×强度)× 子板块敞口 → 复合影响分 → 超配/标配/低配 |

> 为何**单一撰写者 + 单一决策者**:长文一致性靠"单声道写作 + 单一定调",专精体只作分析/评审而非各自成文(STORM/FinCon 的核心经验)。**评级/指标是算出来的,不是模型断言的**;LLM 只把规范数字写成散文。

---

## 3. 共享架构(四类通用)

1. **黑板 = 单一事实源**:每个数字只由一个 owner 写一次;撰写环节只引用,不二次推算(`a2a-shared-memory` 的 ownership + append-log + provenance 保障)。摘要/正文各节引用同一黑板键,从根上杜绝跨章节漂移。
2. **结论为脊柱 + 固定大纲**:规划阶段先定结论与章节,撰写/评审都校验各节回链结论。
3. **有界 writer↔critic 循环**:`NumericConsistencyChecker` 确定性核对正文数字 = 黑板规范值,发现漂移触发改稿,**轮数受预算上限约束**(AutoGen 限轮)。
4. **合规门**:披露 + 认证 + 平衡风险 + SA 签发约束 + 数据缺口透明披露。
5. **跨运行知识** = `ExperienceMemoryKit`:经验在 run 末由 `CollaborationMemoryHook` 蒸馏入库、下次同类报告 recall;可插 `MemOptExperienceStore` 做持久语义记忆。
6. **智能体协作流**:引擎记录 handover/READ/outcome 交互边并在 run 末导出,Web 端可视。

---

## 4. 确定性金融计算(`research.calc`,纯 Java、确定性、全单测)

**所有报告中的数字都来自这里计算,绝不由模型臆造。**

| 模型 | 作用 |
|---|---|
| `FundCalc` | 由净值序列算累计/年化收益、年化波动、夏普、最大回撤、Calmar、α-β → 评级(优选/中性/回避) |
| `BondCalc` | YTM(二分求解)、Macaulay/修正久期、凸性、信用利差、久期 stance(拉久期/中性/缩久期) |
| `SectorImpactModel` | 宏观因子 × 子板块敞口矩阵 → 复合影响分 → 子板块评级(超配/标配/低配) |
| `Calc` / `ValueRange` | 共享数值/区间工具;`Bb.fmt/pct` 规范化(保证"散文里的数字"="校验器看到的数字") |

---

## 5. 数据接入(`research.data`)

- **SPI**:`MacroDataSource` / `FundDataSource` / `BondDataSource` / `ThematicDataSource`,一家实现 = 一个数据提供方;失败抛 `DataUnavailableException`(独立异常类),调用方优雅降级而非泄漏栈。
- **离线**:`StubFundDataSource` / `StubBondDataSource` / `StubThematicDataSource`(数字固定 → 测试/演示确定性可复现)。
- **免费真实(基金)**:`EastMoneyFundDataSource`——天天基金 `lsjz` 累计净值历史(已处理每页 20 条的分页上限)+ `fundgz` 名称;对真实响应校准过。
- **免费真实(宏观)**:`EastMoneyMacroDataSource`——东财 datacenter 经济数据:GDP(`RPT_ECONOMY_GDP`)、CPI(`RPT_ECONOMY_CPI`)、制造业 PMI(`RPT_ECONOMY_PMI`)、M2(`RPT_ECONOMY_CURRENCY_SUPPLY`);每项独立抓取、失败逐项跳过并记告警;海外(FOMC/美国数据)与监管文字为扩展项,接入点已就绪。
- **新鲜度 / 溯源**:`FreshnessPolicy` 做新鲜度窗口,过期**标注而非静默丢弃**;每个数据点带 `Provenance`(来源/类型/as-of/引用/置信度),供合规披露与评审核验。
- 债券实时数据难取 → 债券走合成样例 + 真实 `BondCalc`;主题走情景库(可接实时事件流)。

---

## 6. 韧性 / 预算 / 可观测(工程质量)

- **预算上限** `ReportBudget`:最大改稿轮数、最大模型调用次数、墙钟超时——超限即用"当前最优"收尾,绝不无界循环。
- **故障隔离**:每个 agent contribution 与每次模型调用异常隔离——失败则该节降级为"事实摘要",**整篇仍完整产出**,降级记入 `metadata.degradations` + WARN;经验/合规 fail-soft。失败路径有测试。
- **live LLM 硬超时 + 退避重试**:`TimeoutReportModel`(每调用硬超时)+ `RetryReportModel`(指数退避 + 抖动)。
- **可观测**:`MemoryObserver`(离线 NOOP,生产 Slf4j 例行 DEBUG / 降级 WARN + Micrometer);报告元数据记录模型调用数/改稿轮数/数据缺口/一致性发现/降级。
- **可插拔/规模**:数据源、生成模型、经验后端均为 SPI;引擎无状态、黑板按 run 隔离。

---

## 7. 如何运行

```bash
# Web 演示台(组合式):① 标的(基金/债券/无特定标的,单选+代码)② 分析维度(宏观政策/行业主题/
# 板块策略/全球影响,多选,叠加到标的组合成一篇)③ 生成模型(GLM-4.5-air / DeepSeek-V4-Flash / 桩 /
# 三档对比并排)。CompositeReportEngine 复用各引擎为"章节模块";三档对比同一篇跑三个模型对照。
./financial/play-web.sh          # http://localhost:8088
# (自动从 ~/.claude/settings.json 注入 GLM_* 与 DEEPSEEK_*;未配置的模型回退桩)

# 主题/板块策略 CLI 演示(基金/债券通过 Web 演示):
./financial/play-research.sh "中国 TMT"          # 离线:情景库 + 脚本模型
./financial/play-research.sh "中国 TMT" --real    # 接真实模型(BANK_LLM_*, RESEARCH_REPORT_LIVE_MODEL)

# 测试(单元 + 集成 + 一致性)
./mvnw -f financial/pom.xml test
```

样例:[`sample-thematic-china-tmt.md`](sample-thematic-china-tmt.md)、[`sample-report-THEMATIC-glm52.md`](sample-report-THEMATIC-glm52.md)。

---

## 8. 诚实自检 — 已达成 vs 边界

**已达成(离线可复现、有测试,54/54):** 四条流水线(宏观/基金/债券/主题)共享黑板架构;纯 Java 金融计算全单测;黑板单一事实源 + 一致性门 + 有界改稿 + 合规披露;数据 SPI + 容错降级 + 新鲜度 + 溯源;跨运行经验蒸馏/召回;预算/超时/退避/可观测;Web 演示台(四类 + 模型选择 + 可视化)。

**✅ 真实模型验证**:以 GLM Coding Plan(glm-5.2/glm-4.6)端到端跑通宏观/基金/债券/主题(宏观为东财真实 GDP/CPI/PMI/M2),**0 降级、0 一致性问题**(数字全来自计算)。

**边界(明确披露):**
- 债券走合成样例(免费实时债券数据难取),BondCalc 为真实计算;主题走情景库(可接实时事件流)。
- 宏观国内核心指标(GDP/CPI/PMI/M2)为东财 datacenter **真实数据**;海外(FOMC/美国数据)与监管政策文字为扩展项,接入点已就绪但暂未实接。
- 真实模型测试需 JVM 出网(代理或直连)。
- 适用档:**生产级方向的可运行垂直切片**,符合"分析师增强、人工/SA 签发"的定位,非完整可发布产品。
