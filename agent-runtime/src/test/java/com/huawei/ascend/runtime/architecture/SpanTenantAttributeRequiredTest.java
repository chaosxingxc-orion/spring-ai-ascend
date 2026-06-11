package com.huawei.ascend.runtime.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.huawei.ascend.runtime.llm.gateway.otel.OtelGenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.GenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.GenerationSpanSink.GenerationSpan;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Enforces ARCHITECTURE.md §4 #57 (Tenant attribute on every span) against the
 * platform's real span-emission seam: every platform span MUST carry
 * {@code tenant.id}.
 *
 * <p>Scope (honest): the LLM gateway's GENERATION records are currently the only
 * span-emission path in this module, and they flow exclusively through
 * {@link GenerationSpanSink} (the emission funnel is itself enforced by
 * {@code LlmGatewayEmissionBoundaryArchTest}). Three layers close the invariant:
 * the span payload cannot be constructed without a tenant, the concrete OTel
 * bridge writes the {@code tenant.id} attribute, and the implementation census
 * below fails — rather than silently passing — if the set of concrete sinks
 * changes, forcing this enforcer to be re-pointed instead of going vacuous.
 * A future span emitter that bypasses the {@code GenerationSpanSink} SPI is NOT
 * covered here and needs its own enforcement.
 *
 * <p>Enforcer E44. ADR-0061 §5.
 */
class SpanTenantAttributeRequiredTest {

    private static final JavaClasses RUNTIME_MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.runtime");

    /** The span payload is unconstructible without tenant attribution. */
    @Test
    void generationSpanCannotBeConstructedWithoutTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GenerationSpan(
                "openai", "finance-chat", 10, 5, null, 12, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new GenerationSpan(
                "openai", "finance-chat", 10, 5, null, 12, "  "));
    }

    /** The real OTel emitter writes the tenant onto the wire-out span. */
    @Test
    void otelBridgeCarriesTenantIdAttributeOnTheEmittedSpan() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build()) {
            new OtelGenerationSpanSink(sdk).emit(new GenerationSpan(
                    "openai", "finance-chat", 10, 5, null, 12, "tenant-57"));

            // Assert before close: the exporter's shutdown clears collected spans.
            assertThat(exporter.getFinishedSpanItems()).hasSize(1);
            assertThat(exporter.getFinishedSpanItems().get(0).getAttributes()
                    .get(AttributeKey.stringKey("tenant.id"))).isEqualTo("tenant-57");
        }
    }

    /**
     * Census of concrete sink implementations. This is the arming mechanism: a
     * new or renamed emitter fails this list, so the invariant can never again
     * rot into a name-pattern that matches nothing. When this fails, extend the
     * tenant.id coverage above to the new emitter, then update the census.
     */
    @Test
    void everyConcreteGenerationSpanSinkIsKnownToThisEnforcer() {
        List<String> concreteSinks = RUNTIME_MAIN_CLASSES.stream()
                .filter(clazz -> clazz.isAssignableTo(GenerationSpanSink.class))
                .filter(clazz -> !clazz.isInterface())
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        assertThat(concreteSinks)
                .as("concrete GenerationSpanSink implementations — never empty, or the"
                        + " §4 #57 enforcer is vacuous")
                .containsExactly("NoopGenerationSpanSink", "OtelGenerationSpanSink");
    }
}
