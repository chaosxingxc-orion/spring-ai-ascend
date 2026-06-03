package com.huawei.ascend.runtime.engine.runtime;

import com.huawei.ascend.bus.spi.engine.Orchestrator;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Enforces Rule R-M.a (Engine Envelope Single Authority) at the structural level:
 * every concrete {@link Orchestrator} implementation in the runtime production
 * sources MUST depend on {@link EngineRegistry}. Pattern-matching on
 * {@code ExecutorDefinition} subtypes outside the registry is the prohibited
 * shape; depending on the registry is the affirmative requirement that proves
 * dispatch goes through the single-source authority.
 *
 * <p>The narrower "no instanceof ExecutorDefinition.* outside engine/" regex
 * scan ships as gate Rule 55 (source-level) — ArchUnit operates on bytecode
 * dependencies and cannot directly observe the {@code instanceof} keyword.
 *
 * <p>Authority: ADR-0072; CLAUDE.md Rule R-M.a. agent-service no longer hosts
 * concrete Orchestrator implementations (orchestration ships in
 * agent-runtime); the rule remains as a structural guard that fails if
 * an Orchestrator impl is ever reintroduced here without depending on the
 * registry, so it allows an empty match set.
 */
class EnginePayloadDispatchOnlyViaRegistryTest {

    private static final JavaClasses RUNTIME_MAIN = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.huawei.ascend.runtime");

    private static final ArchCondition<JavaClass> DEPEND_ON_ENGINE_REGISTRY =
            new ArchCondition<>("depend on " + EngineRegistry.class.getName()) {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    boolean dependsOnRegistry = clazz.getAllRawSuperclasses().stream()
                            .map(JavaClass::getName)
                            .anyMatch(EngineRegistry.class.getName()::equals)
                            || clazz.getDirectDependenciesFromSelf().stream()
                            .anyMatch(d -> d.getTargetClass().getName().equals(EngineRegistry.class.getName()));
                    if (!dependsOnRegistry) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " implements Orchestrator but does not depend on EngineRegistry — "
                                        + "Rule R-M.a requires dispatch to go through EngineRegistry.resolveByPayload "
                                        + "or EngineRegistry.resolve(envelope)."));
                    }
                }
            };

    @Test
    void every_orchestrator_implementation_depends_on_engine_registry() {
        classes()
                .that().implement(Orchestrator.class)
                .and().areNotInterfaces()
                .should(DEPEND_ON_ENGINE_REGISTRY)
                .because("ADR-0072 + Rule R-M.a: dispatch authority is centralised in EngineRegistry.")
                .allowEmptyShould(true)
                .check(RUNTIME_MAIN);
    }
}
