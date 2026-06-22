package com.bank.financial.research.macro.agent;

import com.bank.financial.research.data.MacroData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.macro.MacroBb;
import com.bank.financial.research.macro.MacroContext;
import com.bank.financial.research.macro.MacroSubAgent;

/** Data ingestion: publish each macro indicator reading to the blackboard. */
public final class MacroDataAgent implements MacroSubAgent {

    @Override
    public String role() {
        return "data";
    }

    @Override
    public String capability() {
        return "data-ingestion";
    }

    @Override
    public void contribute(MacroContext ctx) {
        for (MacroData.Indicator i : ctx.dataset().indicators()) {
            String v = i.label() + ": " + Bb.fmt(i.value()) + i.unit() + "(" + i.period() + ")";
            ctx.put(role(), MacroBb.indicatorKey(i.key()), v);
        }
    }
}
