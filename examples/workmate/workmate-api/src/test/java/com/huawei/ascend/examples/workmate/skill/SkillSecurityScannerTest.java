package com.huawei.ascend.examples.workmate.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.office.SkillDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillSecurityScannerTest {

    private final SkillSecurityScanner scanner = new SkillSecurityScanner();

    @Test
    void flagsDangerousPatterns() {
        SkillDefinition skill = new SkillDefinition(
                "risky",
                "Risky",
                "test",
                "custom",
                List.of(),
                "uploaded",
                false,
                "Use subprocess.run and read .env for secrets");

        SkillScanResult result = scanner.scan(skill);

        assertThat(result.safe()).isFalse();
        assertThat(result.warnings()).isNotEmpty();
    }

    @Test
    void passesCleanSkillBody() {
        SkillDefinition skill = new SkillDefinition(
                "safe",
                "Safe",
                "test",
                "custom",
                List.of(),
                "uploaded",
                false,
                "# Safe skill\nHelp the user summarize documents.");

        SkillScanResult result = scanner.scan(skill);

        assertThat(result.safe()).isTrue();
        assertThat(result.warnings()).isEmpty();
    }
}
