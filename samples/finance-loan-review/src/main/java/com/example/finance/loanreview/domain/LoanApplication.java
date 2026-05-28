package com.example.finance.loanreview.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Input record for the {@code loan-review-assistant} v1.0 reference agent.
 *
 * <p>Carries only what is needed to drive the three CIF / transaction /
 * bureau skill calls. PII fields use opaque reference tokens
 * ({@code applicantRef}) rather than direct identifiers; the back-end
 * stub resolves the token to a synthetic profile.
 *
 * @param applicantRef opaque applicant token (NOT a national ID, NOT a
 *                     full account number); resolved server-side
 *                     against the CIF gateway. Non-blank.
 * @param amount       requested principal, in account currency. Positive.
 * @param termMonths   loan term in months. 1..360.
 * @param purpose      free-text purpose tag (e.g. {@code "auto"},
 *                     {@code "home_improvement"}). Non-blank.
 * @param tenantId     owning tenant; flows through every skill
 *                     invocation per Rule R-C.c. Non-blank.
 */
public record LoanApplication(
        String applicantRef,
        BigDecimal amount,
        int termMonths,
        String purpose,
        String tenantId) {

    public LoanApplication {
        Objects.requireNonNull(applicantRef, "applicantRef");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(tenantId, "tenantId");
        if (applicantRef.isBlank()) {
            throw new IllegalArgumentException("applicantRef must be non-blank");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (termMonths < 1 || termMonths > 360) {
            throw new IllegalArgumentException("termMonths must be in [1, 360]");
        }
        if (purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must be non-blank");
        }
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank (Rule R-C.c)");
        }
    }
}
