package com.huawei.ascend.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards the simplified runtime package structure after A2A SDK consolidation.
 * Engine sub-packages are restricted; framework adapters live under their
 * own sub-packages; common must stay framework-free.
 */
class RuntimePackageBoundaryTest {

    private static final JavaClasses RUNTIME_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.runtime");

    @Test
    void engineHasAllowedSubpackagesOnly() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.engine..")
                .should().resideInAnyPackage(
                        "com.huawei.ascend.runtime.engine",
                        "com.huawei.ascend.runtime.engine.a2a..",
                        "com.huawei.ascend.runtime.engine.agentscope..",
                        "com.huawei.ascend.runtime.engine.mcp..",
                        "com.huawei.ascend.runtime.engine.openjiuwen..",
                        "com.huawei.ascend.runtime.engine.otel..",
                        "com.huawei.ascend.runtime.engine.versatile..",
                        "com.huawei.ascend.runtime.engine.spi..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void bootIsFlat() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.boot..")
                .should().resideInAPackage("com.huawei.ascend.runtime.boot")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void appHasNoSubpackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.app..")
                .should().resideInAPackage("com.huawei.ascend.runtime.app")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void openJiuwenAdapterLivesUnderEngineOpenjiuwen() {
        ArchRule rule = classes()
                .that().resideInAPackage("..openjiuwen..")
                .should().resideInAPackage("com.huawei.ascend.runtime.engine.openjiuwen..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void agentScopeAdapterLivesUnderEngineAgentscope() {
        ArchRule rule = classes()
                .that().resideInAPackage("..agentscope..")
                .should().resideInAPackage("com.huawei.ascend.runtime.engine.agentscope..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void neutralEngineSpiStaysFrameworkAgnostic() {
        // The northbound trajectory abstraction lives in engine.spi and must stay framework-neutral:
        // native framework events are consumed only in the per-framework adapter (engine.openjiuwen),
        // never leaked into the neutral SPI. Guards the owner-mandated abstraction boundary.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..runtime.engine.spi..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.openjiuwen..");
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void engineSpiDependsOnlyOnContractSafePackages() {
        // engine.spi is the contract surface sibling modules (agent-sdk, agent-service, examples)
        // compile against. Pinning its dependency whitelist keeps sibling-layer semantics from
        // leaking into the contracts (the corruption mode where a core contract type accretes
        // upper-layer keys/types), which would otherwise force consumers to inherit executor,
        // boot, or protocol-server machinery just to see the SPI.
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.engine.spi..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.huawei.ascend.runtime.engine.spi..",
                        "com.huawei.ascend.runtime.engine",
                        "com.huawei.ascend.runtime.common",
                        "java..",
                        "org.slf4j..",
                        "org.springframework.util")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void commonDependsOnlyOnTheJdk() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.common..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.huawei.ascend.runtime.common..",
                        "java..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void protocolNeutralPackagesAreA2aSdkFree() {
        // Executable form of the L1 neutrality assertion (logical.md): the SPI,
        // the neutral context in the engine root, engine.otel, common, and the
        // framework adapters never see A2A wire types. The packages allowed to
        // touch org.a2aproject are the protocol bridge (engine.a2a), boot
        // wiring, and the versatile REST adapter (which provides its own A2A
        // card metadata).
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "com.huawei.ascend.runtime.engine",
                        "com.huawei.ascend.runtime.engine.mcp..",
                        "com.huawei.ascend.runtime.engine.spi..",
                        "com.huawei.ascend.runtime.engine.otel..",
                        "com.huawei.ascend.runtime.common..",
                        "com.huawei.ascend.runtime.engine.agentscope..",
                        "com.huawei.ascend.runtime.engine.openjiuwen..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.a2aproject..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void frameworkAdaptersDoNotDependOnTheA2aBridge() {
        // Adapters consume the neutral SPI only; a convenience import of bridge
        // machinery (executor, Messages, projector) would silently re-couple
        // them to the protocol layer.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "com.huawei.ascend.runtime.engine.agentscope..",
                        "com.huawei.ascend.runtime.engine.openjiuwen..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.huawei.ascend.runtime.engine.a2a..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void a2aServerMachineryStaysInBridgeAndBoot() {
        // The heavy A2A server machinery (RequestContext, AgentEmitter, task
        // stores, request handlers) is confined to the protocol bridge and the
        // boot wiring; everything else consumes the neutral SPI.
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(
                        "com.huawei.ascend.runtime.engine.a2a..",
                        "com.huawei.ascend.runtime.boot..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.a2aproject.sdk.server..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }
}
