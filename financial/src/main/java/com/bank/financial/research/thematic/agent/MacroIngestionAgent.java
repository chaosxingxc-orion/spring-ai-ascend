package com.bank.financial.research.thematic.agent;

import com.bank.financial.research.data.ThematicData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.thematic.ThematicBb;
import com.bank.financial.research.thematic.ThematicContext;
import com.bank.financial.research.thematic.ThematicSubAgent;

/**
 * Data / macro-ingestion associate. Publishes a canonical digest of the macro
 * factors (direction + magnitude) to the blackboard so the writer and compliance
 * agents reference one shared view rather than re-reading the raw dataset.
 */
public final class MacroIngestionAgent implements ThematicSubAgent {

    @Override
    public String role() {
        return "data";
    }

    @Override
    public String capability() {
        return "data-ingestion";
    }

    @Override
    public void contribute(ThematicContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (ThematicData.MacroFactor f : ctx.dataset().factors()) {
            sb.append("- [").append(f.category()).append("] ").append(f.label())
                    .append("(影响强度 ").append(Bb.fmt(f.signedMagnitude())).append("):")
                    .append(f.note()).append('\n');
        }
        ctx.put(role(), ThematicBb.FACTORS_SUMMARY, sb.toString());
    }
}
