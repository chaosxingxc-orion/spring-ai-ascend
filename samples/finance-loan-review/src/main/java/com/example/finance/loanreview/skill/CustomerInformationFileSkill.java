package com.example.finance.loanreview.skill;

import com.huawei.ascend.middleware.skill.spi.Skill;
import com.huawei.ascend.middleware.skill.spi.SkillInvocation;
import com.huawei.ascend.middleware.skill.spi.SkillKind;
import com.huawei.ascend.middleware.skill.spi.SkillResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * STUB Customer Information File (CIF) skill.
 *
 * <p>v1.0 reference for the {@code loan-review-assistant} sample. In a
 * real deployment this skill calls the bank's CIF gateway over the
 * outbound-allowlist channel of {@code financial_default} and returns
 * the applicant's KYC + demographic profile. The stub below returns
 * synthetic data only — no real customer records, no real network.
 *
 * <p>The {@code Skill} SPI surface (ADR-0127):
 * <pre>
 *   String skillKey();                            // "cifSkill"
 *   SkillKind kind();                             // BUILTIN
 *   SkillResult execute(SkillInvocation inv);     // synthetic profile
 * </pre>
 *
 * <p>Required inputs in {@link SkillInvocation#inputs()}:
 * <ul>
 *   <li>{@code applicantRef} — opaque applicant token (non-blank).</li>
 * </ul>
 *
 * <p>Output map in {@link SkillResult.SkillSuccess#outputs()}:
 * <ul>
 *   <li>{@code applicant_ref}    — echo of input token</li>
 *   <li>{@code kyc_status}       — {@code "verified"} (stub)</li>
 *   <li>{@code account_tenure_months} — int</li>
 *   <li>{@code residency_region} — coarse region bucket (no street address)</li>
 * </ul>
 *
 * <p>PII discipline: the stub deliberately does NOT return full name,
 * national ID, full account number, or street address. The
 * {@code financial_default} policy's {@code pii_egress_to_model: false}
 * means any such field would have to be redacted before reaching the
 * LLM anyway.
 */
@Component("cifSkill")
public class CustomerInformationFileSkill implements Skill {

    @Override
    public String skillKey() {
        return "cifSkill";
    }

    @Override
    public SkillKind kind() {
        return SkillKind.BUILTIN;
    }

    @Override
    public SkillResult execute(SkillInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        // Rule R-C.c — tenantId validated by SkillInvocation constructor.
        Object refRaw = invocation.inputs().get("applicantRef");
        if (!(refRaw instanceof String ref) || ref.isBlank()) {
            return new SkillResult.SkillError(
                    "invalid_argument",
                    "cifSkill requires non-blank inputs.applicantRef");
        }

        // SYNTHETIC DATA — no real CIF lookup, no outbound network.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicant_ref", ref);
        out.put("kyc_status", "verified");
        out.put("account_tenure_months", 84);
        out.put("residency_region", "CN-East");
        out.put("source", "stub://cif.local");
        return new SkillResult.SkillSuccess(out);
    }
}
