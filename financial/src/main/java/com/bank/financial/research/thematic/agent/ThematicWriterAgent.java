package com.bank.financial.research.thematic.agent;

import com.bank.financial.research.data.ThematicData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.model.ReportModel;
import com.bank.financial.research.thematic.ThematicBb;
import com.bank.financial.research.thematic.ThematicContext;
import com.bank.financial.research.thematic.ThematicSubAgent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single writer for the sector-strategy note — one voice for the whole
 * report. Assembles a fact-bearing brief per outline section from the blackboard
 * (computed scores/ratings + factor digest) and asks the model to narrate it,
 * with a fact-only fallback so a model failure degrades a section rather than
 * aborting the report.
 */
public final class ThematicWriterAgent implements ThematicSubAgent {

    @Override
    public String role() {
        return "writer";
    }

    @Override
    public String capability() {
        return "writing";
    }

    @Override
    public void contribute(ThematicContext ctx) {
        String outline = ctx.latest(ThematicBb.OUTLINE).orElse(ThematicBb.OUTLINE_DEFAULT);
        for (String id : outline.split(",")) {
            writeSection(ctx, id.trim());
        }
    }

    public String writeSection(ThematicContext ctx, String id) {
        String title = ThematicBb.titleOf(id);
        String brief = briefFor(ctx, id);
        String body;
        if (ctx.tryModelCall()) {
            try {
                body = ctx.model().generate(new ReportModel.ModelTask(
                        "writer",
                        "撰写「" + title + "」章节,约 400-600 字:结论先行,展开完整逻辑链,"
                                + "给出正反两面与关键风险,回链板块总体评级;只可引用简报中的规范数字,不得新增数字。",
                        brief, 600));
            } catch (RuntimeException e) {
                ctx.degraded("writer:" + id, e.getMessage());
                body = "(本节模型生成失败,降级为事实摘要)\n" + brief;
            }
        } else {
            ctx.degraded("writer:" + id, "model budget exhausted");
            body = "(模型预算用尽,以下为事实摘要)\n" + brief;
        }
        ctx.put(role(), ThematicBb.SECTION_PREFIX + id, body);
        return body;
    }

    private String briefFor(ThematicContext ctx, String id) {
        ThematicData.Dataset ds = ctx.dataset();
        Map<String, String> facts = new LinkedHashMap<>();
        switch (id) {
            case "summary" -> {
                facts.put("主题", ctx.latest(ThematicBb.THEME).orElse(ds.theme()));
                facts.put("总体评级", ThematicBb.ratingLabel(ctx.latest(ThematicBb.OVERALL_RATING).orElse("NEUTRAL")));
                facts.put("综合影响分", ctx.latest(ThematicBb.OVERALL_SCORE).orElse("n/a"));
                facts.put("核心观点", ctx.latest(ThematicBb.THESIS).orElse(""));
                facts.put("超配", ctx.latest(ThematicBb.ALLOC_OVERWEIGHT).orElse("无"));
                facts.put("低配", ctx.latest(ThematicBb.ALLOC_UNDERWEIGHT).orElse("无"));
            }
            case "macro_global" -> factorBucket(ds, facts,
                    f -> f.category() == ThematicData.FactorCategory.GEOPOLITICS || f.key().startsWith("fomc"));
            case "macro_china" -> factorBucket(ds, facts, f -> f.key().startsWith("cn_"));
            case "markets" -> factorBucket(ds, facts, f -> f.category() == ThematicData.FactorCategory.MARKET);
            case "transmission" -> {
                for (ThematicData.SubSector s : ds.subSectors()) {
                    String rating = ThematicBb.ratingLabel(ctx.latest(ThematicBb.sectorRatingKey(s.name())).orElse("NEUTRAL"));
                    String score = ctx.latest(ThematicBb.sectorScoreKey(s.name())).orElse("n/a");
                    facts.put(s.name(), rating + "(影响分 " + score + "):" + s.note());
                }
            }
            case "allocation" -> {
                facts.put("超配", ctx.latest(ThematicBb.ALLOC_OVERWEIGHT).orElse("无"));
                facts.put("标配", ctx.latest(ThematicBb.ALLOC_NEUTRAL).orElse("无"));
                facts.put("低配", ctx.latest(ThematicBb.ALLOC_UNDERWEIGHT).orElse("无"));
                facts.put("核心观点", ctx.latest(ThematicBb.THESIS).orElse(""));
            }
            case "risks" -> {
                int i = 1;
                for (ThematicData.MacroFactor f : ds.factors()) {
                    if (f.signedMagnitude() < 0) {
                        facts.put("风险因子#" + i++, f.label() + ":" + f.note() + "(若强化或反转则构成下行风险)");
                    }
                }
                facts.put("情景反转", "若美伊停战在正式签署前生变,油价暴涨、风险偏好断崖,将系统性压制成长股。");
            }
            default -> {
            }
        }
        StringBuilder sb = new StringBuilder();
        facts.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    private interface FactorPredicate {
        boolean test(ThematicData.MacroFactor f);
    }

    private void factorBucket(ThematicData.Dataset ds, Map<String, String> facts, FactorPredicate p) {
        for (ThematicData.MacroFactor f : ds.factors()) {
            if (p.test(f)) {
                facts.put(f.label(), "影响强度 " + Bb.fmt(f.signedMagnitude()) + ";" + f.note());
            }
        }
    }
}
