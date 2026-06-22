package com.bank.financial.research.thematic.agent;

import com.bank.financial.research.calc.SectorImpactModel;
import com.bank.financial.research.calc.SectorImpactModel.FactorSignal;
import com.bank.financial.research.calc.SectorImpactModel.SectorExposure;
import com.bank.financial.research.calc.SectorImpactModel.SectorScore;
import com.bank.financial.research.data.ThematicData;
import com.bank.financial.research.thematic.ThematicBb;
import com.bank.financial.research.thematic.ThematicContext;
import com.bank.financial.research.thematic.ThematicSubAgent;
import java.util.ArrayList;
import java.util.List;

/**
 * The quant of the thematic desk. Runs the deterministic {@link SectorImpactModel}
 * over the macro factors and sub-sector exposures, writing each sub-sector's
 * computed impact score + rating and the overall stance to the blackboard. These
 * ratings are <em>computed</em>, not asserted by the model — the writer narrates
 * them.
 */
public final class SectorImpactAgent implements ThematicSubAgent {

    @Override
    public String role() {
        return "sector-impact";
    }

    @Override
    public String capability() {
        return "sector-scoring";
    }

    @Override
    public void contribute(ThematicContext ctx) {
        ThematicData.Dataset ds = ctx.dataset();

        List<FactorSignal> signals = new ArrayList<>();
        for (ThematicData.MacroFactor f : ds.factors()) {
            signals.add(new FactorSignal(f.key(), f.label(), f.signedMagnitude()));
        }
        List<SectorExposure> exposures = new ArrayList<>();
        for (ThematicData.SubSector s : ds.subSectors()) {
            exposures.add(new SectorExposure(s.name(), s.exposures()));
        }

        List<SectorScore> scores = SectorImpactModel.score(signals, exposures);
        for (SectorScore sc : scores) {
            ctx.putNum(role(), ThematicBb.sectorScoreKey(sc.sector()), sc.score());
            ctx.put(role(), ThematicBb.sectorRatingKey(sc.sector()), sc.rating().name());
        }

        SectorScore overall = SectorImpactModel.overall(ds.theme(), scores, 0.15, -0.15);
        ctx.putNum(role(), ThematicBb.OVERALL_SCORE, overall.score());
        ctx.put(role(), ThematicBb.OVERALL_RATING, overall.rating().name());
    }
}
