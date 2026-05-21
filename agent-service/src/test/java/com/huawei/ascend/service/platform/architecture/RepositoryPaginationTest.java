package com.huawei.ascend.service.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Collection;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Enforcer for plan §11 row E16: every Spring Data {@code Repository} method
 * returning a {@link Collection} or a {@link Page} MUST declare a
 * {@link Pageable} parameter — unbounded reads are forbidden.
 *
 * <p>Scope is {@code com.huawei.ascend.service.platform.persistence..}; non-repository
 * helpers and runtime SPIs are out of scope. Repositories that legitimately
 * return single-element results (Optional, entity, count) are unaffected.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E16.
 */
class RepositoryPaginationTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.huawei.ascend.service.platform.persistence");

    @Test
    void repository_methods_returning_collection_must_declare_pageable() {
        // allowEmptyShould(true): at L1 there are no Spring Data Repository
        // beans in the platform-side persistence package (post-Phase-C:
        // com.huawei.ascend.service.platform.persistence; pre-Phase-C:
        // agent-platform.persistence per ADR-0078) yet; the rule activates
        // when one is added.
        methods()
                .that().areDeclaredInClassesThat().areAssignableTo(Repository.class)
                .and().haveRawReturnType(Collection.class)
                .should(declarePageable())
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void repository_methods_returning_page_must_declare_pageable() {
        methods()
                .that().areDeclaredInClassesThat().areAssignableTo(Repository.class)
                .and().haveRawReturnType(Page.class)
                .should(declarePageable())
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    private static ArchCondition<JavaMethod> declarePageable() {
        return new ArchCondition<>("declare a Pageable parameter") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean hasPageable = method.getRawParameterTypes().stream()
                        .anyMatch(c -> c.isAssignableTo(Pageable.class));
                if (!hasPageable) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " returns Collection/Page but does not declare Pageable"
                                    + " (enforcer row E16 forbids unbounded repository reads)"));
                }
            }
        };
    }
}
