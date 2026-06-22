package com.bank.financial.research.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.calc.FundCalc.FundMetrics;
import com.bank.financial.research.calc.FundCalc.Rating;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit layer: fund performance + risk analytics against hand-verified values. */
class FundCalcTest {

    private static final double EPS = 0.005;

    @Test
    void cumulativeReturn() {
        assertEquals(0.5, FundCalc.cumulativeReturn(List.of(1.0, 1.5)), EPS);
    }

    @Test
    void annualizedReturnIsCagr() {
        // 2 periods/year, 1 year, total ×1.21 → 21%
        assertEquals(0.21, FundCalc.annualizedReturn(List.of(1.0, 1.1, 1.21), 2), EPS);
    }

    @Test
    void maxDrawdownPeakToTrough() {
        assertEquals(-0.25, FundCalc.maxDrawdown(List.of(1.0, 1.2, 0.9, 1.1)), EPS);
        assertEquals(0.0, FundCalc.maxDrawdown(List.of(1.0, 1.1, 1.2)), EPS); // monotonic up
    }

    @Test
    void volZeroWhenConstantReturns_positiveWhenVarying() {
        assertEquals(0.0, FundCalc.annualizedVol(List.of(1.0, 1.1, 1.21), 12), EPS); // equal +10% steps
        assertTrue(FundCalc.annualizedVol(List.of(1.0, 1.1, 1.0, 1.15), 12) > 0);
    }

    @Test
    void betaTwoWhenFundMovesDoubleBenchmark() {
        List<Double> bench = List.of(1.0, 1.05, 1.0815);           // rb = [.05, .03]
        List<Double> fund = List.of(1.0, 1.10, 1.166);             // rf ≈ [.10, .06] = 2×rb
        assertEquals(2.0, FundCalc.beta(fund, bench), 0.05);
    }

    @Test
    void betaZeroWithoutBenchmark() {
        assertEquals(0.0, FundCalc.beta(List.of(1.0, 1.1), null), EPS);
        assertEquals(0.0, FundCalc.beta(List.of(1.0, 1.1, 1.2), List.of(1.0, 1.1)), EPS); // length mismatch
    }

    @Test
    void ratingThresholds() {
        assertEquals(Rating.PREFERRED, FundCalc.rate(new FundMetrics(0, 0.2, 0.1, 1.5, -0.2, 0, 0, 0)));
        assertEquals(Rating.AVOID, FundCalc.rate(new FundMetrics(0, -0.1, 0.2, -0.5, -0.3, 0, 0, 0)));
        assertEquals(Rating.AVOID, FundCalc.rate(new FundMetrics(0, 0.3, 0.5, 2.0, -0.7, 0, 0, 0))); // deep DD
        assertEquals(Rating.NEUTRAL, FundCalc.rate(new FundMetrics(0, 0.1, 0.2, 0.5, -0.4, 0, 0, 0)));
    }

    @Test
    void rejectsTooShortSeries() {
        assertThrows(IllegalArgumentException.class, () -> FundCalc.cumulativeReturn(List.of(1.0)));
    }
}
