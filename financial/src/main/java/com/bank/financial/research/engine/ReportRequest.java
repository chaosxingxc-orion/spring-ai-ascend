package com.bank.financial.research.engine;

/**
 * Input to a report run.
 *
 * @param ticker      subject identifier (e.g. "DEMO")
 * @param reportType  EQUITY | INDUSTRY | MACRO (the pipeline is shared; the lens differs)
 * @param tenantId    isolation boundary for memory + observability
 * @param language    output language tag (e.g. "zh-CN")
 * @param asOfEpochMs the as-of instant for data freshness and the report date
 * @param budget      the run's resilience budget
 */
public record ReportRequest(
        String ticker, String reportType, String tenantId, String language,
        long asOfEpochMs, ReportBudget budget) {

    public ReportRequest {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker is required");
        }
        reportType = reportType == null || reportType.isBlank() ? "EQUITY" : reportType;
        tenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        language = language == null || language.isBlank() ? "zh-CN" : language;
        if (budget == null) {
            budget = ReportBudget.standard();
        }
    }

    /** A standard equity-report request for {@code ticker} as of {@code asOf}. */
    public static ReportRequest equity(String ticker, String tenantId, long asOfEpochMs) {
        return new ReportRequest(ticker, "EQUITY", tenantId, "zh-CN", asOfEpochMs, ReportBudget.standard());
    }

    /** The collaboration / run id used to scope this report's blackboard. */
    public String collaborationId() {
        return "report-" + ticker.toUpperCase() + "-" + asOfEpochMs;
    }
}
