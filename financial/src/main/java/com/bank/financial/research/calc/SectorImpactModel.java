package com.bank.financial.research.calc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic macro→sector transmission scoring — the thematic report's
 * numeric backbone, the analog of DCF/comparables for a single stock. Each macro
 * factor carries a signed magnitude (its direction and strength as it bears on
 * risk assets); each sub-sector carries an exposure (elasticity) to each factor.
 * A sub-sector's impact score is the dot product of factor magnitudes and its
 * exposures, mapped to an OVERWEIGHT / NEUTRAL / UNDERWEIGHT rating by threshold.
 *
 * <p>This keeps sub-sector ratings computed and auditable rather than asserted by
 * the language model — the writer only narrates the scores this model produces.
 */
public final class SectorImpactModel {

    private SectorImpactModel() {
    }

    public enum Rating {
        OVERWEIGHT, NEUTRAL, UNDERWEIGHT
    }

    /**
     * A macro factor's bearing on risk assets.
     *
     * @param signedMagnitude direction × strength, conventionally in [-1, 1]
     *                        (e.g. oil falling = +; Fed hawkish = −)
     */
    public record FactorSignal(String key, String label, double signedMagnitude) {
    }

    /** A sub-sector's elasticity to each factor key (signed; absent ⇒ 0). */
    public record SectorExposure(String sector, Map<String, Double> exposures) {
        public SectorExposure {
            exposures = Map.copyOf(exposures);
        }
    }

    /**
     * @param contributions per-factor contribution to the score (factorKey → value)
     */
    public record SectorScore(String sector, double score, Rating rating, Map<String, Double> contributions) {
        public SectorScore {
            contributions = Map.copyOf(contributions);
        }
    }

    /** Default thresholds: score ≥ +0.15 ⇒ overweight, ≤ −0.15 ⇒ underweight. */
    public static List<SectorScore> score(List<FactorSignal> factors, List<SectorExposure> sectors) {
        return score(factors, sectors, 0.15, -0.15);
    }

    public static List<SectorScore> score(List<FactorSignal> factors, List<SectorExposure> sectors,
            double overweightAt, double underweightAt) {
        if (factors == null || factors.isEmpty()) {
            throw new IllegalArgumentException("need at least one macro factor");
        }
        if (sectors == null || sectors.isEmpty()) {
            throw new IllegalArgumentException("need at least one sub-sector");
        }
        List<SectorScore> out = new ArrayList<>();
        for (SectorExposure s : sectors) {
            Map<String, Double> contributions = new LinkedHashMap<>();
            double score = 0.0;
            for (FactorSignal f : factors) {
                double exposure = s.exposures().getOrDefault(f.key(), 0.0);
                double c = f.signedMagnitude() * exposure;
                if (exposure != 0.0) {
                    contributions.put(f.key(), Calc.rate(c));
                }
                score += c;
            }
            score = Calc.rate(score);
            Rating rating = score >= overweightAt ? Rating.OVERWEIGHT
                    : score <= underweightAt ? Rating.UNDERWEIGHT : Rating.NEUTRAL;
            out.add(new SectorScore(s.sector(), score, rating, contributions));
        }
        return out;
    }

    /** Overall stance: mean of sub-sector scores mapped to a rating. */
    public static SectorScore overall(String name, List<SectorScore> scores,
            double overweightAt, double underweightAt) {
        if (scores == null || scores.isEmpty()) {
            throw new IllegalArgumentException("need at least one sub-sector score");
        }
        double mean = Calc.rate(scores.stream().mapToDouble(SectorScore::score).average().orElse(0.0));
        Rating rating = mean >= overweightAt ? Rating.OVERWEIGHT
                : mean <= underweightAt ? Rating.UNDERWEIGHT : Rating.NEUTRAL;
        return new SectorScore(name, mean, rating, Map.of());
    }

    /** The factors most responsible for a sub-sector's score (largest |contribution| first). */
    public static List<Map.Entry<String, Double>> topDrivers(SectorScore score, int n) {
        List<Map.Entry<String, Double>> e = new ArrayList<>(score.contributions().entrySet());
        e.sort((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())));
        return e.size() <= n ? e : e.subList(0, n);
    }
}
