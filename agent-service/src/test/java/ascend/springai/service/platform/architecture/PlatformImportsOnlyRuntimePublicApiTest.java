package ascend.springai.service.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforcer for plan §11 row E34 / Phase K audit fix F9 (META-PATTERN
 * side-effect of Phase B's Gate Rule D-6 amendment).
 *
 * <p>ADR-0055 permits {@code agent-platform → agent-runtime} for the W1 HTTP
 * run handoff, but only through the runtime's PUBLIC API surface. Internal
 * packages (in-memory reference impls, resilience routing, memory SPI, etc.)
 * MUST remain hidden from the HTTP edge — otherwise a future refactor could
 * silently couple the platform's request thread to runtime internals that
 * are not request-safe.
 *
 * <p>Permitted import roots from {@code ascend.springai.service.platform..}:
 * <ul>
 *   <li>{@code ascend.springai.service.runtime.runs..} — Run entity + state machine</li>
 *   <li>{@code ascend.springai.engine.orchestration.spi..} — pure-Java SPIs</li>
 *   <li>{@code ascend.springai.service.runtime.orchestration.inmemory.InMemoryRunRegistry}
 *       — the only dev-posture impl the platform edge (post-Phase-C / ADR-0078:
 *       agent-service; pre-Phase-C: agent-platform) legitimately wires
 *       (RunControllerAutoConfiguration). Other inmemory.* classes stay
 *       internal.</li>
 *   <li>{@code ascend.springai.service.runtime.posture..} — AppPostureGate / AppPosture</li>
 *   <li>{@code ascend.springai.service.runtime.memory.spi..} — explicitly forbidden;
 *       enforced by the sibling test {@code HttpEdgeMustNotImportMemorySpiTest}</li>
 * </ul>
 *
 * <p>Anything else under {@code ascend.springai.service.runtime..} is OFF-LIMITS to
 * the HTTP edge.
 */
class PlatformImportsOnlyRuntimePublicApiTest {

    private static final JavaClasses PLATFORM_MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("ascend.springai.service.platform");

    @Test
    void platform_does_not_depend_on_internal_runtime_packages() {
        // W1.x Phase 9 / ADR-0070 promoted ascend.springai.service.runtime.resilience.spi..
        // to a PUBLIC SPI surface — ADR-0080 moved the surface
        // types (ResilienceContract, ResiliencePolicy, SkillResolution, SuspendReason,
        // SkillCapacityRegistry) under the .spi sub-package; implementations stay in
        // runtime.resilience.* (Default*, Yaml*). The two-arg resolve is consumed by
        // the platform-side ResilienceAutoConfiguration (post-Phase-C lives under
        // ascend.springai.service.platform.resilience; pre-Phase-C was
        // agent-platform.resilience.ResilienceAutoConfiguration per ADR-0078).
        // Internal-only packages stay: idempotency.. and probe..
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.service.platform..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "ascend.springai.service.runtime.idempotency..",
                        "ascend.springai.service.runtime.probe..")
                .because("ADR-0055 / plan §18 F9 (extended by ADR-0070; refined by ADR-0080): "
                        + "platform may import runtime's PUBLIC api (runs.*, orchestration.spi.*, "
                        + "posture.*, resilience.spi.* + resilience.{Default,Yaml}* impls, and the "
                        + "InMemoryRunRegistry adapter). Internal packages (idempotency.., probe..) "
                        + "remain hidden from the HTTP edge. Enforcer row E34.");
        rule.check(PLATFORM_MAIN_CLASSES);
    }

    @Test
    void platform_does_not_depend_on_runtime_inmemory_executors_or_checkpointer() {
        // The single legitimate inmemory import is InMemoryRunRegistry (wired
        // by RunControllerAutoConfiguration in dev posture). Other inmemory
        // adapters — SyncOrchestrator, SequentialGraphExecutor,
        // IterativeAgentLoopExecutor, InMemoryCheckpointer — stay hidden from
        // the HTTP edge. They're driven by the orchestration SPI from within
        // the runtime, not from the platform.
        //
        // W2.x Phase 5 (ADR-0076) exception: ascend.springai.service.platform.engine..
        // IS the centralized engine-discovery wiring point per Rule R-M.a (Engine
        // Envelope Single Authority). EngineAutoConfiguration legitimately
        // constructs the two W0 reference executors as @Bean methods so Spring
        // can wire them into EngineRegistry. This is NOT HTTP-edge coupling —
        // it is the explicit, single, authorized wiring location. The original
        // rule intent (prevent ad-hoc HTTP-edge access) is preserved by excluding
        // ONLY the engine package; everything else under the platform sub-tree
        // (post-Phase-C: ascend.springai.service.platform..; pre-Phase-C:
        // agent-platform.. per ADR-0078) still cannot reach these classes.
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.service.platform..")
                .and().resideOutsideOfPackage("ascend.springai.service.platform.engine..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(
                        "ascend.springai.service.runtime.orchestration.inmemory.SyncOrchestrator")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "ascend.springai.service.runtime.orchestration.inmemory.SequentialGraphExecutor")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "ascend.springai.service.runtime.orchestration.inmemory.IterativeAgentLoopExecutor")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "ascend.springai.service.runtime.orchestration.inmemory.InMemoryCheckpointer")
                .because("ADR-0055 / plan §18 F9 + ADR-0076 W2.x Phase 5 exception: only "
                        + "InMemoryRunRegistry is the legitimate in-memory adapter the HTTP "
                        + "edge wires. Sync/Sequential/Iterative executors + InMemoryCheckpointer "
                        + "stay internal to the runtime EXCEPT inside ascend.springai.service.platform.engine.. "
                        + "(EngineAutoConfiguration — the single authorized engine-discovery wiring "
                        + "point per Rule R-M.a).");
        rule.check(PLATFORM_MAIN_CLASSES);
    }
}
