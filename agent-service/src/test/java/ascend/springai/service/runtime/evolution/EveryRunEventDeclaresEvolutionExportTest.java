package ascend.springai.service.runtime.evolution;

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
 * Enforces Rule 47 (Evolution Scope Default Boundary, ADR-0075): every
 * concrete record that implements {@code RunEvent} MUST expose an
 * {@code evolutionExport()} accessor returning an {@link EvolutionExport}
 * value. The platform refuses to persist any RunEvent whose export is
 * {@link EvolutionExport#OUT_OF_SCOPE}; {@link EvolutionExport#OPT_IN}
 * requires a matching telemetry-export contract row.
 *
 * <p>Phase 4 reality check: the {@code RunEvent} sealed interface is NOT
 * present under {@code agent-runtime/src/main} today (ADR-0022 declared
 * the type but deferred implementation to W2 alongside the typed payload
 * codec + streaming Flux contract). The test is therefore armed with
 * {@code allowEmptyShould(true)} -- it passes today (no variants exist)
 * and starts asserting the contract the moment the first RunEvent variant
 * lands in W2. This is the same arming pattern used by
 * {@code EveryEngineDeclaresHookSurfaceTest} (E79) for the same Phase 2
 * deferred-fire-points reason.
 *
 * <p>Authority: ADR-0075; CLAUDE.md Rule 47.
 * Enforcer row: {@code docs/governance/enforcers.yaml#E87} (post-review fix
 * plan F / P1-2: prior Javadoc cited #E86 which is the evolution-scope gate-
 * script row, not this ArchUnit test).
 */
class EveryRunEventDeclaresEvolutionExportTest {

    private static final JavaClasses RUNTIME_MAIN = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("ascend.springai.service.runtime");

    private static final ArchCondition<JavaClass> DECLARE_EVOLUTION_EXPORT =
            new ArchCondition<>("declare an evolutionExport() accessor returning EvolutionExport") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    boolean hasMethod = clazz.getAllMethods().stream()
                            .anyMatch(m -> m.getName().equals("evolutionExport")
                                    && m.getRawParameterTypes().isEmpty()
                                    && m.getRawReturnType().getName()
                                            .equals("ascend.springai.service.runtime.evolution.EvolutionExport"));
                    if (!hasMethod) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " implements RunEvent but does not declare"
                                        + " EvolutionExport evolutionExport() per Rule 47 / ADR-0075"));
                    }
                }
            };

    @Test
    void every_run_event_record_declares_evolution_export() {
        // Phase 4 boundary: RunEvent sealed interface lands in W2 alongside the typed
        // payload codec + Flux<RunEvent> streaming contract (ADR-0022). The test is
        // armed with allowEmptyShould(true) so it stays GREEN today (no variants
        // exist) and starts asserting the contract the moment the first
        // record X implements RunEvent lands. The condition is fully written so no
        // edit is required when RunEvent ships -- only the variants need to add
        // the accessor.
        classes()
                .that().areRecords()
                .and().implement("ascend.springai.service.runtime.runs.RunEvent")
                .should(DECLARE_EVOLUTION_EXPORT)
                .allowEmptyShould(true)
                .because("ADR-0075: every RunEvent variant declares its evolution-plane export at the type level.")
                .check(RUNTIME_MAIN);
    }
}
