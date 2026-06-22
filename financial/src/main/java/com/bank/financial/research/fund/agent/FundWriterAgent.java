package com.bank.financial.research.fund.agent;

import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.fund.FundBb;
import com.bank.financial.research.fund.FundContext;
import com.bank.financial.research.fund.FundSubAgent;
import com.bank.financial.research.model.ReportModel;
import java.util.LinkedHashMap;
import java.util.Map;

/** Single writer: turns the computed metrics into section prose, with a fact-only fallback. */
public final class FundWriterAgent implements FundSubAgent {

    @Override
    public String role() {
        return "writer";
    }

    @Override
    public String capability() {
        return "writing";
    }

    @Override
    public void contribute(FundContext ctx) {
        String outline = ctx.latest(FundBb.OUTLINE).orElse(FundBb.OUTLINE_DEFAULT);
        for (String id : outline.split(",")) {
            writeSection(ctx, id.trim());
        }
    }

    public String writeSection(FundContext ctx, String id) {
        String title = FundBb.titleOf(id);
        String brief = briefFor(ctx, id);
        String body;
        if (ctx.tryModelCall()) {
            try {
                body = ctx.model().generate(new ReportModel.ModelTask(
                        "writer",
                        com.bank.financial.research.model.WriterPrompts.section(title, 450, "基金总体评级与适配人群"),
                        brief, 800));
            } catch (RuntimeException e) {
                ctx.degraded("writer:" + id, e.getMessage());
                body = "(本节模型生成失败,降级为事实摘要)\n" + brief;
            }
        } else {
            ctx.degraded("writer:" + id, "model budget exhausted");
            body = "(模型预算用尽,以下为事实摘要)\n" + brief;
        }
        ctx.put(role(), FundBb.SECTION_PREFIX + id, body);
        return body;
    }

    private String briefFor(FundContext ctx, String id) {
        Map<String, String> f = new LinkedHashMap<>();
        switch (id) {
            case "summary" -> {
                f.put("基金", ctx.latest(FundBb.NAME).orElse(ctx.dataset().code()));
                f.put("类型", ctx.latest(FundBb.TYPE).orElse("—"));
                f.put("评级", FundBb.ratingLabel(ctx.latest(FundBb.OVERALL_RATING).orElse("NEUTRAL")));
                f.put("核心观点", ctx.latest(FundBb.THESIS).orElse(""));
            }
            case "performance" -> {
                f.put("累计收益", pct(ctx, FundBb.CUM_RETURN));
                f.put("年化收益", pct(ctx, FundBb.ANN_RETURN));
                f.put("夏普比率", ctx.latest(FundBb.SHARPE).orElse("n/a"));
                f.put("Calmar", ctx.latest(FundBb.CALMAR).orElse("n/a"));
                f.put("Alpha", pct(ctx, FundBb.ALPHA));
                f.put("Beta", ctx.latest(FundBb.BETA).orElse("n/a"));
            }
            case "risk" -> {
                f.put("年化波动", pct(ctx, FundBb.ANN_VOL));
                f.put("最大回撤", pct(ctx, FundBb.MAX_DD));
                f.put("风险等级", ctx.latest(FundBb.RISK_LEVEL).orElse("—"));
            }
            case "suitability" -> {
                f.put("评级", FundBb.ratingLabel(ctx.latest(FundBb.OVERALL_RATING).orElse("NEUTRAL")));
                f.put("风险等级", ctx.latest(FundBb.RISK_LEVEL).orElse("—"));
                f.put("年化收益", pct(ctx, FundBb.ANN_RETURN));
                f.put("最大回撤", pct(ctx, FundBb.MAX_DD));
            }
            default -> {
            }
        }
        StringBuilder sb = new StringBuilder();
        f.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    private static String pct(FundContext ctx, String key) {
        return ctx.latestNum(key).isPresent() ? Bb.pct(ctx.latestNum(key).getAsDouble()) : "n/a";
    }
}
