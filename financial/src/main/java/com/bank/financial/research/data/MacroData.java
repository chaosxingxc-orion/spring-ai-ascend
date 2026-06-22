package com.bank.financial.research.data;

import java.util.List;

/**
 * Normalised inputs for a macro & policy report: a set of macro indicators, each
 * tagged with the domain it belongs to (growth / inflation / monetary / activity /
 * overseas) and carrying its latest reading plus year-on-year / month-on-month
 * changes where meaningful. {@code MacroCalc} turns these into a deterministic
 * macro stance + asset tilt; the writer only narrates. Each indicator carries
 * {@link Provenance} so the compliance agent can disclose sources.
 */
public final class MacroData {

    private MacroData() {
    }

    /** The lens an indicator belongs to (drives report sections + which domains a run pulls). */
    public enum Domain { GROWTH, INFLATION, MONETARY, ACTIVITY, OVERSEAS }

    /**
     * One macro indicator reading.
     *
     * @param value   the headline reading (a level for PMI; a YoY % for GDP/CPI/M2)
     * @param unit    display unit ("%" or "" for an index level)
     * @param yoy     year-on-year change in pct points (NaN if n/a)
     * @param mom     month-on-month / sequential change (NaN if n/a)
     */
    public record Indicator(
            String key, String label, Domain domain, String period,
            double value, String unit, double yoy, double mom, String note, Provenance provenance) {
    }

    /**
     * The assembled macro dataset for one region (e.g. "中国").
     *
     * @param freshnessWarnings non-fatal staleness/coverage notes
     */
    public record Dataset(
            String region, long asOfEpochMs,
            List<Indicator> indicators, List<String> freshnessWarnings) {

        public Dataset {
            indicators = List.copyOf(indicators);
            freshnessWarnings = freshnessWarnings == null ? List.of() : List.copyOf(freshnessWarnings);
        }

        /** Indicators in one domain (for a section / a selected lens). */
        public List<Indicator> inDomain(Domain d) {
            return indicators.stream().filter(i -> i.domain() == d).toList();
        }

        public boolean has(Domain d) {
            return indicators.stream().anyMatch(i -> i.domain() == d);
        }
    }
}
