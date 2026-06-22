package com.bank.financial.research.macro.agent;

import com.bank.financial.research.macro.MacroBb;
import com.bank.financial.research.macro.MacroContext;
import com.bank.financial.research.macro.MacroSubAgent;

/** Planner: fixes the region identity and the macro-note outline. */
public final class MacroPlannerAgent implements MacroSubAgent {

    @Override
    public String role() {
        return "planner";
    }

    @Override
    public String capability() {
        return "planning";
    }

    @Override
    public void contribute(MacroContext ctx) {
        ctx.put(role(), MacroBb.REGION, ctx.dataset().region());
        ctx.put(role(), MacroBb.OUTLINE, MacroBb.OUTLINE_DEFAULT);
    }
}
