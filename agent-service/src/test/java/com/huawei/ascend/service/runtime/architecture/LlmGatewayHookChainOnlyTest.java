package com.huawei.ascend.service.runtime.architecture;

import com.huawei.ascend.middleware.model.spi.Message;
import com.huawei.ascend.middleware.model.spi.ModelInvocation;
import com.huawei.ascend.service.integration.springai.SpringAiChatModelGateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces ARCHITECTURE.md §4 #56 (GENERATION span schema — HookChain-only path).
 *
 * <p>Direct LLM calls bypassing the Hook SPI are forbidden in posture=research/prod
 * because they bypass the {@code LlmSpanEmitterHook}, {@code TokenCounterHook},
 * {@code CostAttributionHook}, and {@code PiiRedactionHook} reference hooks
 * (Telemetry Vertical, ADR-0061 §7).
 *
 * <p>Specifically: no class under {@code com.huawei.ascend.service.runtime.llm..} may depend
 * on Spring AI's {@code ChatModel} (or any provider client SDK) except via the
 * {@code HookChain} package (W2 — both arrive together per ADR-0061 §7).
 *
 * <p>The rule uses ArchUnit's {@code allowEmptyShould(true)} semantic — it is
 * vacuous at L1.x (no classes under {@code llm/} ship) and arms automatically the
 * moment W2 adds them. The Wave C1 Spring AI adapter package is asserted
 * separately below as design-only shell code, so its current {@code ChatModel}
 * reference cannot bypass hooks by making a provider call. Enforcer E43.
 */
class LlmGatewayHookChainOnlyTest {

    private static final JavaClasses RUNTIME_MAIN_CLASSES = new ClassFileImporter()
            .importPackages("com.huawei.ascend.service.runtime");

    @Test
    void no_runtime_llm_class_imports_chat_model_outside_hook_chain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.service.runtime.llm..")
                .and().resideOutsideOfPackage("com.huawei.ascend.service.runtime.llm.hook..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.ai.chat..",
                        "com.openai..",
                        "com.anthropic..")
                .allowEmptyShould(true);
        rule.check(RUNTIME_MAIN_CLASSES);
    }

    @Test
    void spring_ai_chat_model_gateway_is_design_only_until_hook_binding_ships() {
        SpringAiChatModelGateway gateway =
                new SpringAiChatModelGateway(unusedChatModel(), "gateway");
        ModelInvocation invocation = new ModelInvocation(
                "tenant",
                "model",
                List.of(new Message.UserMessage("hello")),
                List.of(),
                Map.of(),
                Map.of("traceId", "trace"));

        assertThatThrownBy(() -> gateway.invoke(invocation))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("design-only shell");
    }

    private static ChatModel unusedChatModel() {
        return prompt -> {
            throw new AssertionError("SpringAiChatModelGateway must not call ChatModel before W2 hook binding ships");
        };
    }
}
