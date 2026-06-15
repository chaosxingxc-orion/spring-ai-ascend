package com.bank.financial.kit.compliance;

import com.openjiuwen.core.security.guardrail.GuardrailBackend;
import com.openjiuwen.core.security.guardrail.RiskAssessment;
import com.openjiuwen.core.security.guardrail.RiskLevel;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * STARTER screening backend — a placeholder so an agent can be wired and demoed
 * end to end. Replace with a real sanctions / AML / suitability screening
 * service (or model) for production. It implements the platform's
 * {@link GuardrailBackend} contract: read the {@code "text"} key, return a
 * {@link RiskAssessment}.
 *
 * <p>Keep production risk logic OUT of the agent and IN a backend like this so
 * it is testable and swappable independently of the agent.
 */
public class KeywordScreeningBackend implements GuardrailBackend {

    private static final Pattern CRITICAL_TERMS = Pattern.compile(
            "(?i)(sanction|ofac|launder|shell company|洗钱|制裁|恐怖融资)");

    private final Set<String> blockedTerms;

    public KeywordScreeningBackend() {
        this(Set.of());
    }

    public KeywordScreeningBackend(Set<String> extraBlockedTerms) {
        this.blockedTerms = extraBlockedTerms;
    }

    @Override
    public RiskAssessment analyze(Map<String, Object> data) {
        String text = String.valueOf(data.getOrDefault("text", ""));
        if (text.isBlank()) {
            return safe();
        }
        if (CRITICAL_TERMS.matcher(text).find()) {
            return RiskAssessment.builder()
                    .hasRisk(true)
                    .riskLevel(RiskLevel.CRITICAL)
                    .riskType("aml.keyword_hit")
                    .confidence(0.8)
                    .details(Map.of("matched", "screening_term"))
                    .build();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String t : blockedTerms) {
            if (!t.isBlank() && lower.contains(t.toLowerCase(Locale.ROOT))) {
                return RiskAssessment.builder()
                        .hasRisk(true)
                        .riskLevel(RiskLevel.HIGH)
                        .riskType("policy.blocked_term")
                        .confidence(0.6)
                        .details(Map.of("matched", t))
                        .build();
            }
        }
        return safe();
    }

    private RiskAssessment safe() {
        return RiskAssessment.builder().hasRisk(false).riskLevel(RiskLevel.SAFE).build();
    }
}
