package ascend.springai.service.runtime.memory.spi;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Contract enforcement for the surviving middleware SPI package.
 * ascend.springai.service.runtime.memory.spi must remain pure-Java (no Spring, no platform deps)
 * so the interface stays stable across Boot major upgrades.
 */
class MemorySpiArchTest {

    private static final JavaClasses RUNTIME_CLASSES = new ClassFileImporter()
            .importPackages("ascend.springai.service.runtime");

    @Test
    void memory_spi_imports_only_java_sdk_types() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.service.runtime.memory.spi..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..");
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void memory_spi_has_no_platform_dependency() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.service.runtime.memory.spi..")
                .should().dependOnClassesThat()
                .resideInAPackage("ascend.springai.service.platform..");
        rule.check(RUNTIME_CLASSES);
    }
}
