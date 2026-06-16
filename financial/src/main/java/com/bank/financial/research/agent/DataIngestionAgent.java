package com.bank.financial.research.agent;

import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;

/**
 * Data / ingestion associate. Publishes the canonical market anchor (current
 * price) that the house view is measured against, from the already-assembled,
 * freshness-checked dataset. It deliberately does not re-publish every raw input:
 * the blackboard is the single source of truth for <em>report-bearing</em>
 * figures and decisions, while raw fundamentals stay in the dataset the analysis
 * agents read.
 */
public final class DataIngestionAgent implements ReportSubAgent {

    @Override
    public String role() {
        return "data";
    }

    @Override
    public String capability() {
        return "data-ingestion";
    }

    @Override
    public void contribute(ReportContext ctx) {
        CompanyData.Dataset ds = ctx.dataset();
        if (ds.hasMarket()) {
            ctx.putNum(role(), Bb.CURRENT_PRICE, ds.market().price());
        }
    }
}
