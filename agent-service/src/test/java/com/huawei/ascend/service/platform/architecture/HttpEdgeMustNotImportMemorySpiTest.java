package com.huawei.ascend.service.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces L1 layering (ADR-0055, plan §5.2): the HTTP edge layer
 * (post-Phase-C lives under {@code agent-service/src/main/.../platform/...};
 * pre-Phase-C was the {@code agent-platform} module per ADR-0078)
 * MUST NOT import the memory SPI under
 * {@code com.huawei.ascend.service.runtime.memory.spi..}.
 *
 * <p>Rationale: the HTTP edge owns request validation, auth, tenant binding,
 * idempotency, and observability. Memory persistence is a runtime concern that
 * MUST NOT be reachable from the request thread — otherwise the HTTP layer
 * silently grows responsibilities (cache lookups, embedding calls, sidecar
 * round-trips) that belong to the cognitive kernel.
 *
 * <p>Enforcer row: {@code docs/governance/enforcers.yaml#E4}.
 */
class HttpEdgeMustNotImportMemorySpiTest {

    private static final JavaClasses PLATFORM_MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.huawei.ascend.service.platform");

    @Test
    void platform_main_sources_must_not_depend_on_runtime_memory_spi() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.service.platform..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.service.runtime.memory.spi..")
                .because("ADR-0055 / plan §5.2: HTTP edge must not embed memory SPI. "
                        + "Enforcer row E4 in docs/governance/enforcers.yaml.");
        rule.check(PLATFORM_MAIN_CLASSES);
    }
}
