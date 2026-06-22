package com.bank.financial.research.bond.agent;

import com.bank.financial.research.bond.BondBb;
import com.bank.financial.research.bond.BondContext;
import com.bank.financial.research.bond.BondSubAgent;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.model.ReportModel;
import java.util.LinkedHashMap;
import java.util.Map;

/** Single writer: turns the computed metrics into section prose, with a fact-only fallback. */
public final class BondWriterAgent implements BondSubAgent {

    @Override
    public String role() {
        return "writer";
    }

    @Override
    public String capability() {
        return "writing";
    }

    @Override
    public void contribute(BondContext ctx) {
        String outline = ctx.latest(BondBb.OUTLINE).orElse(BondBb.OUTLINE_DEFAULT);
        for (String id : outline.split(",")) {
            writeSection(ctx, id.trim());
        }
    }

    public String writeSection(BondContext ctx, String id) {
        String title = BondBb.titleOf(id);
        String brief = briefFor(ctx, id);
        String body;
        if (ctx.tryModelCall()) {
            try {
                body = ctx.model().generate(new ReportModel.ModelTask(
                        "writer",
                        com.bank.financial.research.model.WriterPrompts.section(title, 450, "债券配置评级(久期/信用 stance)"),
                        brief, 800));
            } catch (RuntimeException e) {
                ctx.degraded("writer:" + id, e.getMessage());
                body = "(本节模型生成失败,降级为事实摘要)\n" + brief;
            }
        } else {
            ctx.degraded("writer:" + id, "model budget exhausted");
            body = "(模型预算用尽,以下为事实摘要)\n" + brief;
        }
        ctx.put(role(), BondBb.SECTION_PREFIX + id, body);
        return body;
    }

    private String briefFor(BondContext ctx, String id) {
        Map<String, String> f = new LinkedHashMap<>();
        switch (id) {
            case "summary" -> {
                f.put("债券", ctx.latest(BondBb.NAME).orElse(ctx.dataset().code()));
                f.put("发行人", ctx.latest(BondBb.ISSUER).orElse("—"));
                f.put("评级", ctx.latest(BondBb.RATING).orElse("NR"));
                f.put("配置评级", BondBb.stanceLabel(ctx.latest(BondBb.STANCE).orElse("NEUTRAL")));
                f.put("核心观点", ctx.latest(BondBb.THESIS).orElse(""));
            }
            case "yield" -> {
                f.put("到期收益率", pct(ctx, BondBb.YTM));
                f.put("当期收益率", pct(ctx, BondBb.CURRENT_YIELD));
                f.put("Macaulay久期", ctx.latest(BondBb.MACAULAY).orElse("n/a"));
                f.put("修正久期", ctx.latest(BondBb.MODIFIED).orElse("n/a"));
                f.put("凸性", ctx.latest(BondBb.CONVEXITY).orElse("n/a"));
            }
            case "credit" -> {
                f.put("信用利差", pct(ctx, BondBb.CREDIT_SPREAD));
                f.put("基准收益率", pct(ctx, BondBb.BENCHMARK_YIELD));
                f.put("信用评级", ctx.latest(BondBb.RATING).orElse("NR"));
                f.put("信用风险等级", ctx.latest(BondBb.CREDIT_LEVEL).orElse("—"));
            }
            case "allocation" -> {
                f.put("配置评级", BondBb.stanceLabel(ctx.latest(BondBb.STANCE).orElse("NEUTRAL")));
                f.put("修正久期", ctx.latest(BondBb.MODIFIED).orElse("n/a"));
                f.put("信用利差", pct(ctx, BondBb.CREDIT_SPREAD));
                f.put("信用风险等级", ctx.latest(BondBb.CREDIT_LEVEL).orElse("—"));
            }
            default -> {
            }
        }
        StringBuilder sb = new StringBuilder();
        f.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    private static String pct(BondContext ctx, String key) {
        return ctx.latestNum(key).isPresent() ? Bb.pct(ctx.latestNum(key).getAsDouble()) : "n/a";
    }
}
