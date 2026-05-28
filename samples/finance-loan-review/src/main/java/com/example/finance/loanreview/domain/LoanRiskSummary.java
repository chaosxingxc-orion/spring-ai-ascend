package com.example.finance.loanreview.domain;

import java.util.List;
import java.util.Objects;

/**
 * Output record for the {@code loan-review-assistant} v1.0 reference
 * agent. The credit officer treats this as a recommendation input, not
 * a final decision.
 *
 * <p>Rationale strings are emitted by the LLM AFTER server-side PII
 * redaction: no full name, no national ID, no full account number. The
 * {@code applicantRef} token from {@link LoanApplication} is the only
 * permitted identifier in the rationale text.
 *
 * @param runId       audit-trail correlation key matching
 *                    {@code docs/contracts/audit-trail.v1.yaml}.
 * @param riskBand    one of LOW / MEDIUM / HIGH / DECLINE_RECOMMENDED.
 * @param topFactors  up to three signed contributing factors (highest
 *                    absolute weight first). Never null; may be empty
 *                    if upstream stubs returned partial data.
 * @param rationale   one-paragraph free-text rationale. Never null.
 * @param dataGaps    machine-readable codes for missing inputs (e.g.
 *                    {@code "bureau_unreachable"}). Empty when every
 *                    skill returned a usable response.
 */
public record LoanRiskSummary(
        String runId,
        RiskBand riskBand,
        List<RiskFactor> topFactors,
        String rationale,
        List<String> dataGaps) {

    public LoanRiskSummary {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(riskBand, "riskBand");
        Objects.requireNonNull(topFactors, "topFactors");
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(dataGaps, "dataGaps");
        if (topFactors.size() > 3) {
            throw new IllegalArgumentException("topFactors capped at 3");
        }
        topFactors = List.copyOf(topFactors);
        dataGaps = List.copyOf(dataGaps);
    }

    /** Risk band closed enum (open enums defeat type-safe routing). */
    public enum RiskBand { LOW, MEDIUM, HIGH, DECLINE_RECOMMENDED }

    /**
     * One contributing factor in {@link #topFactors}.
     *
     * @param code   stable machine code (e.g. {@code "bureau_score"},
     *               {@code "cashflow_volatility"}).
     * @param weight signed contribution; positive widens risk, negative
     *               narrows it. Roughly bounded to [-1.0, +1.0] but not
     *               clamped — calibration is the LLM's job.
     */
    public record RiskFactor(String code, double weight) {
        public RiskFactor {
            Objects.requireNonNull(code, "code");
            if (code.isBlank()) {
                throw new IllegalArgumentException("code must be non-blank");
            }
        }
    }
}
