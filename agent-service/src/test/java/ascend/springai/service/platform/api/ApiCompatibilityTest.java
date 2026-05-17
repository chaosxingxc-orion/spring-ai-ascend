package ascend.springai.service.platform.api;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Freezes the public API and SPI surface of ascend.springai.* and enforces
 * the competitor-exclusion rule (no com.alibaba.cloud.ai imports).
 *
 * Per docs/cross-cutting/oss-bill-of-materials.md sec-8 "Excluded dependencies":
 * spring-ai-alibaba (com.alibaba.cloud.ai:*) is a direct competitor.
 * Its code must never be imported into the SDK. This test is the
 * compile-time enforcement gate for that rule.
 *
 * SPI freeze: extended after Step 11 landed ascend.springai.service.runtime.spi.*.
 * Any signature change in spi.** requires editing this test, making
 * the break explicit during code review.
 */
class ApiCompatibilityTest {

    private static final JavaClasses FIN_SPRINGAI_CLASSES = new ClassFileImporter()
            .importPackages("ascend.springai");

    @Test
    void no_springai_ascend_class_imports_competitor_alibaba_cloud_ai() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.alibaba.cloud.ai..");
        rule.check(FIN_SPRINGAI_CLASSES);
    }

    // platform → runtime dependency-direction check was previously declared here
    // as `noClasses().resideInAPackage("ascend.springai.service.platform..").should()
    // .dependOnClassesThat().resideInAPackage("ascend.springai.service.runtime..")` — that
    // rule contradicted ADR-0055 (which permits platform → runtime via runtime's
    // PUBLIC SPI surface) and was a latent failure on main. The authoritative
    // check now lives in PlatformImportsOnlyRuntimePublicApiTest (enforcer E34),
    // which forbids only INTERNAL runtime packages (idempotency.., probe..) per
    // ADR-0070 / W1.x Phase 9.

    // spi_packages_* rules relocated to agent-runtime/MemorySpiArchTest after
    // all legacy ascend.springai.service.runtime.spi.** starters were deleted in C2-C8.
    // The surviving SPI (GraphMemoryRepository at ascend.springai.service.runtime.memory.spi)
    // lives in agent-runtime; its contract rules live there too.
}
