package com.bank.financial.research.thematic.agent;

import com.bank.financial.research.data.ThematicData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.model.ReportModel;
import com.bank.financial.research.thematic.ThematicBb;
import com.bank.financial.research.thematic.ThematicContext;
import com.bank.financial.research.thematic.ThematicSubAgent;
import java.util.ArrayList;
import java.util.List;

/**
 * Lead strategist — the sole decision-maker. Groups the computed sub-sector
 * ratings into an allocation (overweight / neutral / underweight buckets) and
 * forms the house view (thesis) anchored to the overall stance. The buckets are
 * derived deterministically from the sector-impact ratings; only the thesis prose
 * uses the model (with a deterministic fallback on failure).
 */
public final class StrategyManagerAgent implements ThematicSubAgent {

    @Override
    public String role() {
        return "lead-manager";
    }

    @Override
    public String capability() {
        return "house-view";
    }

    @Override
    public void contribute(ThematicContext ctx) {
        List<String> ow = new ArrayList<>();
        List<String> neutral = new ArrayList<>();
        List<String> uw = new ArrayList<>();
        for (ThematicData.SubSector s : ctx.dataset().subSectors()) {
            String rating = ctx.latest(ThematicBb.sectorRatingKey(s.name())).orElse("NEUTRAL");
            switch (rating) {
                case "OVERWEIGHT" -> ow.add(s.name());
                case "UNDERWEIGHT" -> uw.add(s.name());
                default -> neutral.add(s.name());
            }
        }
        ctx.put(role(), ThematicBb.ALLOC_OVERWEIGHT, String.join("、", ow));
        ctx.put(role(), ThematicBb.ALLOC_NEUTRAL, String.join("、", neutral));
        ctx.put(role(), ThematicBb.ALLOC_UNDERWEIGHT, String.join("、", uw));

        String overall = ThematicBb.ratingLabel(ctx.latest(ThematicBb.OVERALL_RATING).orElse("NEUTRAL"));
        double score = ctx.latestNum(ThematicBb.OVERALL_SCORE).orElse(0.0);
        ctx.put(role(), ThematicBb.THESIS, thesis(ctx, overall, score, ow, uw));
    }

    private String thesis(ThematicContext ctx, String overall, double score, List<String> ow, List<String> uw) {
        String deterministic = "基于宏观因子向子板块的传导打分,给予该板块「" + overall + "」总体评级(综合影响分 "
                + Bb.fmt(score) + ")。建议超配:" + (ow.isEmpty() ? "无" : String.join("、", ow))
                + ";低配:" + (uw.isEmpty() ? "无" : String.join("、", uw)) + "。";
        if (!ctx.tryModelCall()) {
            return deterministic;
        }
        String brief = "板块总体评级=" + overall + "; 综合影响分=" + Bb.fmt(score)
                + "; 超配=" + String.join("、", ow) + "; 低配=" + String.join("、", uw)
                + "; 因子摘要:\n" + ctx.latest(ThematicBb.FACTORS_SUMMARY).orElse("");
        try {
            String prose = ctx.model().generate(new ReportModel.ModelTask(
                    "lead-manager", "用2-3句话给出统一的板块策略观点(house view),以总体评级为锚,说明主要驱动与风险。",
                    brief, 140));
            return prose + " 【锚定】总体评级 " + overall + ",综合影响分 " + Bb.fmt(score) + "。";
        } catch (RuntimeException e) {
            ctx.degraded("lead-manager:thesis", e.getMessage());
            return deterministic;
        }
    }
}
