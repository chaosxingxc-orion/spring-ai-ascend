package com.bank.financial.research.engine;

/**
 * One section of the report (summary, thesis, model, valuation, risks, …).
 *
 * @param order render order (ascending)
 */
public record ReportSection(String id, String title, String body, int order) {

    public ReportSection {
        body = body == null ? "" : body;
    }

    /** Character count (a reasonable length proxy for CJK text). */
    public int length() {
        return body.length();
    }
}
