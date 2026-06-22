package com.bank.financial.research.macro.agent;

import com.bank.financial.research.data.MacroData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.macro.MacroBb;
import com.bank.financial.research.macro.MacroContext;
import com.bank.financial.research.macro.MacroSubAgent;
import com.bank.financial.research.model.ReportModel;
import com.bank.financial.research.model.WriterPrompts;
import java.util.LinkedHashMap;
import java.util.Map;

/** Single writer: turns the computed scores + indicator readings into section prose. */
public final class MacroWriterAgent implements MacroSubAgent {

    @Override
    public String role() {
        return "writer";
    }

    @Override
    public String capability() {
        return "writing";
    }

    @Override
    public void contribute(MacroContext ctx) {
        String outline = ctx.latest(MacroBb.OUTLINE).orElse(MacroBb.OUTLINE_DEFAULT);
        for (String id : outline.split(",")) {
            writeSection(ctx, id.trim());
        }
    }

    public String writeSection(MacroContext ctx, String id) {
        String title = MacroBb.titleOf(id);
        String brief = briefFor(ctx, id);
        String body;
        if (ctx.tryModelCall()) {
            try {
                body = ctx.model().generate(new ReportModel.ModelTask(
                        "writer", WriterPrompts.section(title, 450, "宏观环境综合判断与资产配置倾向"), brief, 800));
            } catch (RuntimeException e) {
                ctx.degraded("writer:" + id, e.getMessage());
                body = "(本节模型生成失败,降级为事实摘要)\n" + brief;
            }
        } else {
            ctx.degraded("writer:" + id, "model budget exhausted");
            body = "(模型预算用尽,以下为事实摘要)\n" + brief;
        }
        ctx.put(role(), MacroBb.SECTION_PREFIX + id, body);
        return body;
    }

    private String briefFor(MacroContext ctx, String id) {
        Map<String, String> f = new LinkedHashMap<>();
        switch (id) {
            case "summary" -> {
                f.put("资产配置倾向", MacroBb.tiltLabel(ctx.latest(MacroBb.ASSET_TILT).orElse("NEUTRAL")));
                f.put("宏观综合分", ctx.latest(MacroBb.COMPOSITE).orElse("n/a"));
                f.put("核心判断", ctx.latest(MacroBb.THESIS).orElse(""));
            }
            case "growth" -> {
                indicator(ctx, f, MacroData.Domain.GROWTH);
                f.put("增长打分", ctx.latest(MacroBb.SCORE_GROWTH).orElse("n/a"));
            }
            case "inflation" -> {
                indicator(ctx, f, MacroData.Domain.INFLATION);
                f.put("通胀打分(越高越温和)", ctx.latest(MacroBb.SCORE_INFLATION).orElse("n/a"));
            }
            case "monetary" -> {
                indicator(ctx, f, MacroData.Domain.MONETARY);
                f.put("流动性打分", ctx.latest(MacroBb.SCORE_LIQUIDITY).orElse("n/a"));
            }
            case "activity" -> {
                indicator(ctx, f, MacroData.Domain.ACTIVITY);
                f.put("景气打分", ctx.latest(MacroBb.SCORE_ACTIVITY).orElse("n/a"));
            }
            case "allocation" -> {
                f.put("资产配置倾向", MacroBb.tiltLabel(ctx.latest(MacroBb.ASSET_TILT).orElse("NEUTRAL")));
                f.put("宏观综合分", ctx.latest(MacroBb.COMPOSITE).orElse("n/a"));
                f.put("增长/通胀/流动性打分",
                        ctx.latest(MacroBb.SCORE_GROWTH).orElse("n/a") + " / "
                        + ctx.latest(MacroBb.SCORE_INFLATION).orElse("n/a") + " / "
                        + ctx.latest(MacroBb.SCORE_LIQUIDITY).orElse("n/a"));
            }
            case "risks" -> {
                f.put("提示", "宏观预测存在数据修订、政策变化与外部冲击(如海外货币政策、地缘)的不确定性");
                f.put("反向情景", "若增长/通胀/流动性方向反转,上述资产配置倾向应相应调整");
            }
            default -> {
            }
        }
        StringBuilder sb = new StringBuilder();
        f.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    private void indicator(MacroContext ctx, Map<String, String> f, MacroData.Domain d) {
        for (MacroData.Indicator i : ctx.dataset().inDomain(d)) {
            f.put(i.label(), Bb.fmt(i.value()) + i.unit() + "(" + i.period() + ");" + i.note());
        }
        if (ctx.dataset().inDomain(d).isEmpty()) {
            f.put("数据", "本次未接入该维度指标");
        }
    }
}
