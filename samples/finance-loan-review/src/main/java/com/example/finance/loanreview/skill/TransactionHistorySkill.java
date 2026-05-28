package com.example.finance.loanreview.skill;

import com.huawei.ascend.middleware.skill.spi.Skill;
import com.huawei.ascend.middleware.skill.spi.SkillInvocation;
import com.huawei.ascend.middleware.skill.spi.SkillKind;
import com.huawei.ascend.middleware.skill.spi.SkillResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * STUB transaction-history skill.
 *
 * <p>v1.0 reference for the {@code loan-review-assistant} sample. In a
 * real deployment this skill calls the bank's core-banking transaction
 * API and returns the last 90 days of debits / credits + the daily
 * end-of-day balance series. The stub below returns synthetic data only.
 *
 * <p>The {@code Skill} SPI surface (ADR-0127):
 * <pre>
 *   String skillKey();                            // "transactionHistorySkill"
 *   SkillKind kind();                             // BUILTIN
 *   SkillResult execute(SkillInvocation inv);     // synthetic 90-day series
 * </pre>
 *
 * <p>Required inputs in {@link SkillInvocation#inputs()}:
 * <ul>
 *   <li>{@code applicantRef} — opaque applicant token (non-blank).</li>
 * </ul>
 *
 * <p>Output map in {@link SkillResult.SkillSuccess#outputs()}:
 * <ul>
 *   <li>{@code applicant_ref}      — echo of input token</li>
 *   <li>{@code window_days}        — 90 (constant in stub)</li>
 *   <li>{@code total_credits}      — sum, BigDecimal as string</li>
 *   <li>{@code total_debits}       — sum, BigDecimal as string</li>
 *   <li>{@code overdraft_incidents} — count over the window</li>
 *   <li>{@code balance_series_eom} — list of three end-of-month balances</li>
 * </ul>
 *
 * <p>PII discipline: account numbers and counterparty names are NEVER
 * returned. The model gateway receives only the aggregate features.
 */
@Component("transactionHistorySkill")
public class TransactionHistorySkill implements Skill {

    @Override
    public String skillKey() {
        return "transactionHistorySkill";
    }

    @Override
    public SkillKind kind() {
        return SkillKind.BUILTIN;
    }

    @Override
    public SkillResult execute(SkillInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        Object refRaw = invocation.inputs().get("applicantRef");
        if (!(refRaw instanceof String ref) || ref.isBlank()) {
            return new SkillResult.SkillError(
                    "invalid_argument",
                    "transactionHistorySkill requires non-blank inputs.applicantRef");
        }

        // SYNTHETIC DATA — no real core-banking call, no outbound network.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicant_ref", ref);
        out.put("window_days", 90);
        out.put("total_credits", new BigDecimal("142350.00").toPlainString());
        out.put("total_debits", new BigDecimal("128720.00").toPlainString());
        out.put("overdraft_incidents", 0);
        out.put("balance_series_eom", List.of(
                new BigDecimal("28100.00").toPlainString(),
                new BigDecimal("31250.00").toPlainString(),
                new BigDecimal("34680.00").toPlainString()));
        out.put("source", "stub://txn.local");
        return new SkillResult.SkillSuccess(out);
    }
}
