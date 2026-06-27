package com.huawei.ascend.examples.workmate.skill;

import com.huawei.ascend.examples.workmate.office.SkillDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SkillSecurityScanner {

    private static final List<PatternRule> RULES = List.of(
            new PatternRule("shell-exec", Pattern.compile("(?i)(subprocess|os\\.system|Runtime\\.getRuntime|ProcessBuilder)")),
            new PatternRule("pipe-shell", Pattern.compile("(?i)(curl|wget)[^\\n]*\\|\\s*(ba)?sh")),
            new PatternRule("eval", Pattern.compile("(?i)\\beval\\s*\\(")),
            new PatternRule("credential-path", Pattern.compile("(?i)(\\.env|credentials\\.json|api[_-]?key)")),
            new PatternRule("destructive", Pattern.compile("(?i)\\b(rm\\s+-rf|chmod\\s+777|mkfs|dd\\s+if=)\\b")));

    public SkillScanResult scan(SkillDefinition skill) {
        String body = skill.skillBody() == null ? "" : skill.skillBody();
        List<String> warnings = new ArrayList<>();
        for (PatternRule rule : RULES) {
            if (rule.pattern().matcher(body).find()) {
                warnings.add(rule.label());
            }
        }
        return new SkillScanResult(skill.id(), warnings.isEmpty(), warnings);
    }

    private record PatternRule(String label, Pattern pattern) {}
}
