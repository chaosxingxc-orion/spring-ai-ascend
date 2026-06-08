package com.huawei.ascend.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class RuntimePackageBoundaryTest {

    private static final JavaClasses RUNTIME_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.runtime");

    @Test
    void openJiuwenAdapterLivesUnderEngineAdapters() {
        ArchRule rule = classes()
                .that().resideInAPackage("..openjiuwen..")
                .should().resideInAPackage("com.huawei.ascend.runtime.engine.adapters.openjiuwen..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void accessDoesNotDependOnOpenJiuwenAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..runtime.access..")
                .should().dependOnClassesThat()
                .resideInAPackage("..runtime.engine.adapters.openjiuwen..");
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void controlDoesNotDependOnOpenJiuwenAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..runtime.control..")
                .should().dependOnClassesThat()
                .resideInAPackage("..runtime.engine.adapters.openjiuwen..");
        rule.check(RUNTIME_CLASSES);
    }
}
