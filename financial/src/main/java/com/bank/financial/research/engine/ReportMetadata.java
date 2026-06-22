package com.bank.financial.research.engine;

import java.util.List;

/**
 * Run metadata for audit and ops, shared by every report type (fund, bond,
 * thematic). Carries the model/data provenance, the budget usage (model calls,
 * critic rounds), the analytics verdict, and the transparent coverage/quality
 * record — data gaps, compliance disclosures, numeric-consistency findings, and
 * any graceful-degradation notes.
 *
 * @param dataGaps            tiers that were unavailable/stale (transparent coverage)
 * @param complianceNotes     disclosures + the mandatory analyst certification
 * @param consistencyFindings what the numeric-consistency checker flagged
 */
public record ReportMetadata(
        String modelName, String dataSource, int modelCalls, int criticRounds,
        String convergenceVerdict, List<String> dataGaps, List<String> complianceNotes,
        List<String> consistencyFindings, List<String> degradations, long generatedAtEpochMs) {

    public ReportMetadata {
        dataGaps = List.copyOf(dataGaps);
        complianceNotes = List.copyOf(complianceNotes);
        consistencyFindings = List.copyOf(consistencyFindings);
        degradations = degradations == null ? List.of() : List.copyOf(degradations);
    }
}
