package ascend.springai.service.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces ARCHITECTURE.md §4 #56 (GENERATION span schema — HookChain-only path).
 *
 * <p>Direct LLM calls bypassing the Hook SPI are forbidden in posture=research/prod
 * because they bypass the {@code LlmSpanEmitterHook}, {@code TokenCounterHook},
 * {@code CostAttributionHook}, and {@code PiiRedactionHook} reference hooks
 * (Telemetry Vertical, ADR-0061 §7).
 *
 * <p>Specifically: no class under {@code ascend.springai.service.runtime.llm..} may depend
 * on Spring AI's {@code ChatModel} (or any provider client SDK) except via the
 * {@code HookChain} package (W2 — both arrive together per ADR-0061 §7).
 *
 * <p>The rule uses ArchUnit's {@code allowEmptyShould(true)} semantic — it is
 * vacuous at L1.x (no classes under {@code llm/} ship) and arms automatically the
 * moment W2 adds them. Enforcer E43.
 */
class LlmGatewayHookChainOnlyTest {

    private static final JavaClasses RUNTIME_MAIN_CLASSES = new ClassFileImporter()
            .importPackages("ascend.springai.service.runtime");

    @Test
    void no_runtime_llm_class_imports_chat_model_outside_hook_chain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.service.runtime.llm..")
                .and().resideOutsideOfPackage("ascend.springai.service.runtime.llm.hook..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.ai.chat..",
                        "com.openai..",
                        "com.anthropic..")
                .allowEmptyShould(true);
        rule.check(RUNTIME_MAIN_CLASSES);
    }
}
