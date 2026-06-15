package com.huawei.ascend.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces that Audience B extension code under
 * {@code com.huawei.ascend.examples..} composes against the platform's
 * {@code ChatAdvisor} SPI (ADR-0132) rather than reaching for Spring AI's
 * {@code ChatClientAdvisor} or {@code PromptTemplate} directly.
 *
 * <p>This guard preserves the decoration boundary (ADR-0125, Path a):
 * Spring AI is canonical inside the platform, and Audience B writes
 * against platform SPIs (ChatAdvisor / PromptTemplate) that the
 * platform internally maps to Spring AI types. Direct customer imports
 * of {@code org.springframework.ai.chat.client.advisor..} or
 * {@code org.springframework.ai.chat.prompt..} would couple customer
 * code to provider internals and bypass the platform's tenant carrier
 * and hook bindings (ADR-0073, ADR-0121).
 *
 * <p>The rule uses ArchUnit's {@code allowEmptyShould(true)} semantic —
 * it is vacuous at L0 because no {@code examples/**} directory exists
 * yet, and arms automatically the moment examples land in W3.
 */
class AudienceBExtensionSeamsArchTest {

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter()
            .importPackages("com.huawei.ascend");

    @Test
    void examples_must_not_import_spring_ai_advisor_or_prompt_template_directly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.examples..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.ai.chat.client.advisor..",
                        "org.springframework.ai.chat.prompt..")
                .allowEmptyShould(true);
        rule.check(ALL_CLASSES);
    }
}
