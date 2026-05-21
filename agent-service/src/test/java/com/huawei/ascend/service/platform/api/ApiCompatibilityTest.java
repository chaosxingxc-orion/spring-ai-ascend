package com.huawei.ascend.service.platform.api;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Freezes the public API and SPI surface of com.huawei.ascend.* and enforces
 * the competitor-exclusion rule (no com.alibaba.cloud.ai imports).
 *
 * Per docs/cross-cutting/oss-bill-of-materials.md sec-8 "Excluded dependencies":
 * spring-ai-alibaba (com.alibaba.cloud.ai:*) is a direct competitor.
 * Its code must never be imported into the SDK. This test is the
 * compile-time enforcement gate for that rule.
 *
 * SPI freeze: extended after Step 11 landed com.huawei.ascend.service.runtime.spi.*.
 * Any signature change in spi.** requires editing this test, making
 * the break explicit during code review.
 */
class ApiCompatibilityTest {

    private static final JavaClasses FIN_SPRINGAI_CLASSES = new ClassFileImporter()
            .importPackages("com.huawei.ascend");

    @Test
    void no_springai_ascend_class_imports_competitor_alibaba_cloud_ai() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.alibaba.cloud.ai..");
        rule.check(FIN_SPRINGAI_CLASSES);
    }

    // platform → runtime dependency-direction check was previously declared here
    // as `noClasses().resideInAPackage("com.huawei.ascend.service.platform..").should()
    // .dependOnClassesThat().resideInAPackage("com.huawei.ascend.service.runtime..")` — that
    // rule contradicted ADR-0055 (which permits platform → runtime via runtime's
    // PUBLIC SPI surface) and was a latent failure on main. The authoritative
    // check now lives in PlatformImportsOnlyRuntimePublicApiTest (enforcer E34),
    // which forbids only INTERNAL runtime packages (idempotency.., probe..) per
    // ADR-0070 / W1.x Phase 9.

    // spi_packages_* rules relocated to the runtime-side MemorySpiArchTest after
    // all legacy com.huawei.ascend.service.runtime.spi.** starters were deleted in C2-C8.
    // The surviving SPI (GraphMemoryRepository at com.huawei.ascend.service.runtime.memory.spi)
    // lives in agent-service post-Phase-C (consolidated from pre-Phase-C agent-runtime
    // per ADR-0078); its contract rules live there too.
}
