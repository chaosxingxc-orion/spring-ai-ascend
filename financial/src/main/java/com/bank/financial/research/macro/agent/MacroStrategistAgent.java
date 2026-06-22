package com.bank.financial.research.macro.agent;

import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.macro.MacroBb;
import com.bank.financial.research.macro.MacroContext;
import com.bank.financial.research.macro.MacroSubAgent;

/**
 * Chief strategist — the sole decision-maker. Reads the computed scores and
 * publishes the house view: the asset tilt and a thesis sentence anchored to the
 * computed composite (so the prose and the consistency checker agree).
 */
public final class MacroStrategistAgent implements MacroSubAgent {

    @Override
    public String role() {
        return "lead-manager";
    }

    @Override
    public String capability() {
        return "house-view";
    }

    @Override
    public void contribute(MacroContext ctx) {
        String tilt = ctx.latest("macro.tilt.computed").orElse("NEUTRAL");
        ctx.put(role(), MacroBb.ASSET_TILT, tilt);

        double composite = ctx.latestNum(MacroBb.COMPOSITE).orElse(0.0);
        double growth = ctx.latestNum(MacroBb.SCORE_GROWTH).orElse(0.0);
        double inflation = ctx.latestNum(MacroBb.SCORE_INFLATION).orElse(0.0);
        double liquidity = ctx.latestNum(MacroBb.SCORE_LIQUIDITY).orElse(0.0);

        String tiltCn = switch (tilt) {
            case "EQUITY_FAVOURED" -> "增配权益、降低久期";
            case "BONDS_FAVOURED" -> "增配债券、拉长久期";
            default -> "股债均衡、保持灵活";
        };
        String growthCn = growth > 0.15 ? "增长动能偏强" : growth < -0.15 ? "增长动能偏弱" : "增长大致平稳";
        String priceCn = inflation > 0.15 ? "物价温和、政策有空间" : inflation < -0.15 ? "通胀偏热、政策受限" : "物价中性";
        String liqCn = liquidity > 0.15 ? "流动性偏宽" : liquidity < -0.15 ? "流动性偏紧" : "流动性中性";

        String thesis = "综合宏观环境分为 " + Bb.fmt(composite) + ":" + growthCn + "、" + priceCn + "、" + liqCn
                + ";据此建议" + tiltCn + "。本判断由确定性宏观打分得出,后文据此展开。";
        ctx.put(role(), MacroBb.THESIS, thesis);
    }
}
