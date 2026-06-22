package com.bank.financial.research.thematic.agent;

import com.bank.financial.research.thematic.ThematicBb;
import com.bank.financial.research.thematic.ThematicContext;
import com.bank.financial.research.thematic.ThematicSubAgent;

/** Planner: fixes the theme identity and the sector-strategy outline. */
public final class ThematicPlannerAgent implements ThematicSubAgent {

    @Override
    public String role() {
        return "planner";
    }

    @Override
    public String capability() {
        return "planning";
    }

    @Override
    public void contribute(ThematicContext ctx) {
        ctx.put(role(), ThematicBb.THEME, ctx.dataset().theme());
        ctx.put(role(), ThematicBb.OUTLINE, ThematicBb.OUTLINE_DEFAULT);
    }
}
