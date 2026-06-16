package com.bank.financial.research.agent;

import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;
import com.bank.financial.research.model.ReportModel;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single writer — one voice for the whole report (the STORM / earnings-call
 * pattern of a sole drafter to preserve coherence). For each outline section it
 * assembles a fact-bearing brief from the blackboard's canonical figures and asks
 * the model to turn it into prose; the figures are passed verbatim so the prose
 * stays anchored to the computed numbers. The same {@link #writeSection} is used
 * for the initial draft and for critic-driven revisions (each revision appends a
 * new version, preserving the audit trail via the blackboard's append log).
 */
public final class WriterAgent implements ReportSubAgent {

    @Override
    public String role() {
        return "writer";
    }

    @Override
    public String capability() {
        return "writing";
    }

    @Override
    public void contribute(ReportContext ctx) {
        String outline = ctx.latest(Bb.OUTLINE).orElse(PlannerAgent.OUTLINE);
        for (String id : outline.split(",")) {
            writeSection(ctx, id.trim());
        }
    }

    /** Draft (or revise) one section; returns the body and writes it to the blackboard. */
    public String writeSection(ReportContext ctx, String id) {
        String title = titleOf(id);
        String brief = briefFor(ctx, id);
        String body;
        if (ctx.tryModelCall()) {
            body = ctx.model().generate(new ReportModel.ModelTask(
                    "writer", "撰写「" + title + "」章节,机构口径、结论先行、论点回链。", brief, 600));
        } else {
            // Budget exhausted — emit a faithful fact-only section so the report stays complete.
            body = "(模型预算用尽,以下为事实摘要)\n" + brief;
        }
        ctx.put(role(), Bb.SECTION_PREFIX + id, body);
        return body;
    }

    public static String titleOf(String id) {
        return switch (id) {
            case "summary" -> "摘要与评级";
            case "thesis" -> "投资论点";
            case "model" -> "盈利预测与模型";
            case "valuation" -> "估值";
            case "scenario" -> "情景与风险";
            case "sector" -> "行业、宏观与外部信息影响";
            default -> id;
        };
    }

    private String briefFor(ReportContext ctx, String id) {
        Map<String, String> facts = new LinkedHashMap<>();
        switch (id) {
            case "summary" -> {
                facts.put("公司", ctx.latest(Bb.COMPANY).orElse(ctx.request().ticker()));
                facts.put("评级", ctx.latest(Bb.RATING).orElse("n/a"));
                facts.put("目标价", ctx.latest(Bb.PRICE_TARGET).orElse("n/a"));
                facts.put("现价", ctx.latest(Bb.CURRENT_PRICE).orElse("n/a"));
                facts.put("潜在空间(%)", ctx.latest(Bb.UPSIDE_PCT).map(v -> Bb.pct(parse(v))).orElse("n/a"));
                facts.put("收敛判定", ctx.latest(Bb.CONVERGENCE_VERDICT).orElse("n/a"));
            }
            case "thesis" -> {
                facts.put("投资论点", ctx.latest(Bb.THESIS).orElse(""));
                facts.put("收入趋势收敛", ctx.latest(Bb.TREND_CONVERGENT).orElse("n/a"));
                facts.put("盈利惊喜分类", ctx.latest(Bb.SUE_CLASS).orElse("n/a"));
            }
            case "model" -> {
                facts.put("FY1收入", ctx.latest(Bb.REVENUE_FY1).orElse("n/a"));
                facts.put("FY1每股收益", ctx.latest(Bb.EPS_FY1).orElse("n/a"));
                facts.put("FY1自由现金流", ctx.latest(Bb.FCF_FY1).orElse("n/a"));
                facts.put("隐含增速(%)", ctx.latest(Bb.GROWTH).map(v -> Bb.pct(parse(v))).orElse("n/a"));
                facts.put("SUE", ctx.latest(Bb.SUE).orElse("n/a"));
            }
            case "valuation" -> {
                facts.put("DCF每股", ctx.latest(Bb.DCF_PER_SHARE).orElse("n/a"));
                facts.put("终值占比", ctx.latest(Bb.DCF_TERMINAL_WEIGHT).orElse("n/a"));
                facts.put("可比中位每股", ctx.latest(Bb.COMPS_MEDIAN).orElse("n/a"));
                facts.put("可比区间", ctx.latest(Bb.COMPS_LOW).orElse("n/a") + " ~ " + ctx.latest(Bb.COMPS_HIGH).orElse("n/a"));
                facts.put("收敛判定", ctx.latest(Bb.CONVERGENCE_VERDICT).orElse("n/a"));
                facts.put("收敛后每股", ctx.latest(Bb.CONVERGENCE_BLENDED).orElse("n/a"));
                facts.put("方法离散度", ctx.latest(Bb.CONVERGENCE_DISPERSION).orElse("n/a"));
                facts.put("WACC", ctx.latest("valuation.wacc").orElse("n/a"));
                facts.put("永续增长", ctx.latest("valuation.terminalGrowth").orElse("n/a"));
            }
            case "scenario" -> {
                facts.put("乐观每股", ctx.latest(Bb.SCENARIO_BULL).orElse("n/a"));
                facts.put("中性每股", ctx.latest(Bb.SCENARIO_BASE).orElse("n/a"));
                facts.put("悲观每股", ctx.latest(Bb.SCENARIO_BEAR).orElse("n/a"));
                facts.put("概率加权期望每股", ctx.latest(Bb.SCENARIO_EXPECTED).orElse("n/a"));
            }
            case "sector" -> {
                facts.put("外部信息收入影响(%)", ctx.latest(Bb.REVENUE_IMPACT_PCT).orElse("n/a"));
                facts.put("外部信息EPS影响", ctx.latest(Bb.EPS_IMPACT).orElse("n/a"));
                CompanyData.Dataset ds = ctx.dataset();
                int i = 1;
                for (CompanyData.MacroIndicator m : ds.macro()) {
                    facts.put("宏观#" + i++, m.name() + "=" + Bb.fmt(m.value()) + m.unit());
                }
                i = 1;
                for (CompanyData.TextItem n : ds.news()) {
                    facts.put("资讯#" + i++, n.title());
                }
            }
            default -> {
            }
        }
        StringBuilder sb = new StringBuilder();
        facts.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
