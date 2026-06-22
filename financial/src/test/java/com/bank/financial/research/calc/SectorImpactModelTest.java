package com.bank.financial.research.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bank.financial.research.calc.SectorImpactModel.FactorSignal;
import com.bank.financial.research.calc.SectorImpactModel.Rating;
import com.bank.financial.research.calc.SectorImpactModel.SectorExposure;
import com.bank.financial.research.calc.SectorImpactModel.SectorScore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit layer: the deterministic macro→sector transmission scoring. */
class SectorImpactModelTest {

    private static final double EPS = 0.001;

    private static final List<FactorSignal> FACTORS = List.of(
            new FactorSignal("A", "利多因子", 1.0),
            new FactorSignal("B", "利空因子", -1.0));

    @Test
    void scoresAndRatingsByThreshold() {
        List<SectorExposure> sectors = List.of(
                new SectorExposure("S1", Map.of("A", 0.5, "B", 0.1)),   // 0.5 - 0.1 = 0.4 → OW
                new SectorExposure("S2", Map.of("A", 0.1, "B", 0.5)),   // 0.1 - 0.5 = -0.4 → UW
                new SectorExposure("S3", Map.of("A", 0.1, "B", 0.1)));  // 0.0 → NEUTRAL

        List<SectorScore> scores = SectorImpactModel.score(FACTORS, sectors);
        assertEquals(0.4, scores.get(0).score(), EPS);
        assertEquals(Rating.OVERWEIGHT, scores.get(0).rating());
        assertEquals(-0.4, scores.get(1).score(), EPS);
        assertEquals(Rating.UNDERWEIGHT, scores.get(1).rating());
        assertEquals(0.0, scores.get(2).score(), EPS);
        assertEquals(Rating.NEUTRAL, scores.get(2).rating());
    }

    @Test
    void overallIsMeanOfSubSectors() {
        List<SectorScore> scores = SectorImpactModel.score(FACTORS, List.of(
                new SectorExposure("S1", Map.of("A", 0.5, "B", 0.1)),
                new SectorExposure("S2", Map.of("A", 0.1, "B", 0.5)),
                new SectorExposure("S3", Map.of("A", 0.1, "B", 0.1))));
        SectorScore overall = SectorImpactModel.overall("T", scores, 0.15, -0.15);
        assertEquals(0.0, overall.score(), EPS); // (0.4 - 0.4 + 0)/3
        assertEquals(Rating.NEUTRAL, overall.rating());
    }

    @Test
    void contributionsExcludeZeroExposure() {
        SectorScore s = SectorImpactModel.score(FACTORS,
                List.of(new SectorExposure("S", Map.of("A", 0.5)))).get(0);
        assertEquals(0.5, s.score(), EPS);
        assertEquals(1, s.contributions().size()); // only A contributes
    }

    @Test
    void rejectsEmptyInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> SectorImpactModel.score(List.of(), List.of(new SectorExposure("S", Map.of("A", 1.0)))));
        assertThrows(IllegalArgumentException.class,
                () -> SectorImpactModel.score(FACTORS, List.of()));
    }
}
