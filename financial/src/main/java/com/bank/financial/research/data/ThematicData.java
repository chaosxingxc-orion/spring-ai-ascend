package com.bank.financial.research.data;

import java.util.List;
import java.util.Map;

/**
 * Normalised inputs for a thematic / sector-strategy report: the macro factors in
 * play and the sub-sectors they transmit to (with each sub-sector's exposure to
 * each factor). The sector-impact model turns these into computed sub-sector
 * ratings; the writer narrates them. Any field may carry {@link Provenance} so
 * the compliance agent can disclose sources.
 */
public final class ThematicData {

    private ThematicData() {
    }

    /** The lens a factor belongs to (for grouping in the report's macro section). */
    public enum FactorCategory {
        GEOPOLITICS, MONETARY, DOMESTIC_MACRO, MARKET
    }

    /**
     * A macro factor's bearing on (China) risk assets.
     *
     * @param signedMagnitude direction × strength, conventionally in [-1, 1]
     *                        (positive = supportive of risk assets)
     */
    public record MacroFactor(
            String key, String label, FactorCategory category,
            double signedMagnitude, String note, Provenance provenance) {
    }

    /** A sub-sector and its signed elasticity to each factor key (absent ⇒ 0). */
    public record SubSector(String name, Map<String, Double> exposures, String note) {
        public SubSector {
            exposures = Map.copyOf(exposures);
        }
    }

    /**
     * The assembled thematic dataset for one theme (e.g. "中国 TMT").
     *
     * @param freshnessWarnings non-fatal staleness/coverage notes
     */
    public record Dataset(
            String theme, long asOfEpochMs,
            List<MacroFactor> factors, List<SubSector> subSectors,
            List<String> freshnessWarnings) {

        public Dataset {
            factors = List.copyOf(factors);
            subSectors = List.copyOf(subSectors);
            freshnessWarnings = freshnessWarnings == null ? List.of() : List.copyOf(freshnessWarnings);
        }
    }
}
