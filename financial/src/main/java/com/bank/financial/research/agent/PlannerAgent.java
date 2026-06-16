package com.bank.financial.research.agent;

import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;

/**
 * Planner / outliner. Fixes the report skeleton and seeds the identity fields
 * before any analysis runs, so every downstream agent shares one outline and one
 * company identity. Mirrors STORM's pre-writing stage: decide the structure
 * first, then fill it — which is what keeps a long report organised.
 */
public final class PlannerAgent implements ReportSubAgent {

    /** Canonical equity-report section order (the thesis-as-spine skeleton). */
    public static final String OUTLINE = "summary,thesis,model,valuation,scenario,sector";

    @Override
    public String role() {
        return "planner";
    }

    @Override
    public String capability() {
        return "planning";
    }

    @Override
    public void contribute(ReportContext ctx) {
        CompanyData.Dataset ds = ctx.dataset();
        String company = ctx.request().ticker();
        String currency = "CNY";
        if (ds.hasFundamentals()) {
            company = ds.fundamentals().company();
            currency = ds.fundamentals().currency();
        }
        ctx.put(role(), Bb.COMPANY, company);
        ctx.put(role(), Bb.CURRENCY, currency);
        ctx.put(role(), Bb.OUTLINE, OUTLINE);
    }
}
