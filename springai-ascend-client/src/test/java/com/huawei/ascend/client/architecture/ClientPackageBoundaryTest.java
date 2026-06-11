package com.huawei.ascend.client.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards the client SDK's embeddability contract: main scope must drop into
 * any customer application, so it stays Spring-free and never depends on a
 * platform server module (the OSS a2a client + JDK + slf4j are the whole
 * required dependency surface). OpenTelemetry is the one optional addition,
 * and it stays confined to the telemetry package so the always-loaded client
 * classes never force OTel onto a consumer classpath.
 */
class ClientPackageBoundaryTest {

    private static final JavaClasses CLIENT_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.client");

    @Test
    void clientModuleIsSpringFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.client..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .allowEmptyShould(false);
        rule.check(CLIENT_CLASSES);
    }

    @Test
    void openTelemetryStaysBehindTheTelemetryPackage() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.client..")
                .and().resideOutsideOfPackage("com.huawei.ascend.client.telemetry")
                .should().dependOnClassesThat()
                .resideInAPackage("io.opentelemetry..")
                .allowEmptyShould(false);
        rule.check(CLIENT_CLASSES);
    }

    @Test
    void clientModuleDoesNotDependOnPlatformServerModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.client..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.huawei.ascend.runtime..",
                        "com.huawei.ascend.bus..",
                        "com.huawei.ascend.service..",
                        "com.huawei.ascend.agentsdk..")
                .allowEmptyShould(false);
        rule.check(CLIENT_CLASSES);
    }
}
