package com.example.finance.loanreview.skill;

import com.huawei.ascend.middleware.skill.spi.Skill;
import com.huawei.ascend.middleware.skill.spi.SkillInvocation;
import com.huawei.ascend.middleware.skill.spi.SkillKind;
import com.huawei.ascend.middleware.skill.spi.SkillResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * STUB credit-bureau skill.
 *
 * <p>v1.0 reference for the {@code loan-review-assistant} sample. In a
 * real deployment this skill calls the external credit-bureau gateway
 * via an outbound-allowlist destination registered in the per-skill
 * sandbox row (NOT via the {@code financial_default} default-deny
 * outbound). The stub below returns synthetic data only.
 *
 * <p>The {@code Skill} SPI surface (ADR-0127):
 * <pre>
 *   String skillKey();                            // "creditBureauSkill"
 *   SkillKind kind();                             // BUILTIN
 *   SkillResult execute(SkillInvocation inv);     // synthetic bureau report
 * </pre>
 *
 * <p>Required inputs in {@link SkillInvocation#inputs()}:
 * <ul>
 *   <li>{@code applicantRef} — opaque applicant token (non-blank).</li>
 * </ul>
 *
 * <p>Output map in {@link SkillResult.SkillSuccess#outputs()}:
 * <ul>
 *   <li>{@code applicant_ref}        — echo of input token</li>
 *   <li>{@code bureau_score}         — integer in [300, 850] (stub: 712)</li>
 *   <li>{@code score_band}           — coarse banding for LLM consumption</li>
 *   <li>{@code adverse_event_flags}  — list of machine-readable codes</li>
 *   <li>{@code report_freshness_days} — days since the report was issued</li>
 * </ul>
 *
 * <p>PII discipline: bureau reports often contain full name + national
 * ID; the stub deliberately strips both. A production wiring MUST
 * redact before crossing the model gateway boundary
 * ({@code pii_egress_to_model: false} in {@code financial_default}).
 */
@Component("creditBureauSkill")
public class CreditBureauSkill implements Skill {

    @Override
    public String skillKey() {
        return "creditBureauSkill";
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
                    "creditBureauSkill requires non-blank inputs.applicantRef");
        }

        // SYNTHETIC DATA — no real bureau call, no outbound network.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicant_ref", ref);
        out.put("bureau_score", 712);
        out.put("score_band", "PRIME");
        out.put("adverse_event_flags", List.of());            // empty in stub
        out.put("report_freshness_days", 3);
        out.put("source", "stub://bureau.local");
        return new SkillResult.SkillSuccess(out);
    }
}
