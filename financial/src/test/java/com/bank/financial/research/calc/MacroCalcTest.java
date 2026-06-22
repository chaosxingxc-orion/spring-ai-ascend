package com.bank.financial.research.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.calc.MacroCalc.AssetTilt;
import com.bank.financial.research.calc.MacroCalc.MacroStance;
import com.bank.financial.research.data.MacroData;
import com.bank.financial.research.data.MacroData.Domain;
import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.SourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit layer: the deterministic macro stance against hand-verified rules. */
class MacroCalcTest {

    private static final long AS_OF = 1_750_000_000_000L;

    private MacroData.Indicator ind(String key, Domain d, double value) {
        return new MacroData.Indicator(key, key, d, "p", value, "%", value, Double.NaN, "",
                new Provenance("t", SourceType.MACRO, AS_OF, "", 0.9));
    }

    private MacroData.Dataset ds(double gdp, double cpi, double pmi, double m2) {
        return new MacroData.Dataset("中国", AS_OF, List.of(
                ind("gdp", Domain.GROWTH, gdp), ind("cpi", Domain.INFLATION, cpi),
                ind("pmi", Domain.ACTIVITY, pmi), ind("m2", Domain.MONETARY, m2)), List.of());
    }

    @Test
    void weakGrowthEasyPolicyFavoursBonds() {
        MacroStance s = MacroCalc.assess(ds(3.0, 0.5, 47.0, 10.0));
        assertTrue(s.growth() < 0, "weak growth");
        assertTrue(s.activity() < 0, "contracting activity");
        assertEquals(AssetTilt.BONDS_FAVOURED, s.tilt());
    }

    @Test
    void firmGrowthExpansionFavoursEquity() {
        MacroStance s = MacroCalc.assess(ds(7.0, 2.0, 53.0, 9.0));
        assertTrue(s.growth() > 0, "firm growth");
        assertTrue(s.activity() > 0, "expanding activity");
        assertEquals(AssetTilt.EQUITY_FAVOURED, s.tilt());
    }

    @Test
    void aroundTrendIsNeutral() {
        MacroStance s = MacroCalc.assess(ds(5.0, 0.8, 49.8, 7.5));
        assertEquals(AssetTilt.NEUTRAL, s.tilt());
        assertTrue(Math.abs(s.composite()) <= 1.0);
    }

    @Test
    void scoresAreBoundedAndInflationSignBenignWhenLow() {
        MacroStance s = MacroCalc.assess(ds(5.0, 0.5, 50.0, 8.0));
        assertTrue(s.inflation() > 0, "low CPI ⇒ benign (positive) inflation score");
        for (double v : new double[] {s.growth(), s.inflation(), s.activity(), s.liquidity()}) {
            assertTrue(v >= -1.0 && v <= 1.0, "score in [-1,1]: " + v);
        }
    }
}
