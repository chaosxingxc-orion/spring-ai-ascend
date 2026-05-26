package com.huawei.ascend.service.integration.springai;

import com.huawei.ascend.middleware.prompt.spi.PromptTemplate;
import com.huawei.ascend.middleware.prompt.spi.PromptTemplateSource;
import com.huawei.ascend.middleware.prompt.spi.RenderedPrompt;

import java.util.Map;
import java.util.Objects;

/**
 * Reference {@link PromptTemplate} that decorates a Spring AI
 * {@code org.springframework.ai.chat.prompt.PromptTemplate}.
 *
 * <p>Authority: ADR-0131 + ADR-0125. Design-only shell — see
 * package-info for the L0 vs W2 boundary.
 *
 * <p>The Spring AI prompt-template type is held as {@link Object}
 * at the SPI layer to defer the FQN until the W2 prompt-rendering
 * wave wires actual rendering through
 * {@code org.springframework.ai.chat.prompt.PromptTemplate.create(...)}.
 *
 * <p>W2 implementation responsibilities:
 * <ul>
 *   <li>Validate {@code tenantId} non-blank (Rule R-C.c) and apply
 *       any per-tenant rendering policy (sanitization, secrets
 *       redaction).</li>
 *   <li>Translate {@link PromptTemplateSource} into Spring AI's
 *       template body; reject unknown
 *       {@link PromptTemplateSource.PlaceholderSyntax} values.</li>
 *   <li>Invoke the underlying Spring AI prompt template's render
 *       call with the supplied variables.</li>
 *   <li>Return a {@link RenderedPrompt} carrying the rendered text
 *       and an immutable copy of the variables snapshot.</li>
 * </ul>
 */
public final class SpringAiPromptTemplateAdapter implements PromptTemplate {

    private final Object springAiPromptTemplate; // held as Object to defer Spring AI FQN
    private final String templateId;
    private final PromptTemplateSource source;

    public SpringAiPromptTemplateAdapter(Object springAiPromptTemplate,
                                         String templateId,
                                         PromptTemplateSource source) {
        this.springAiPromptTemplate = Objects.requireNonNull(springAiPromptTemplate,
                "springAiPromptTemplate");
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.source = Objects.requireNonNull(source, "source");
        if (templateId.isBlank()) {
            throw new IllegalArgumentException("templateId must be non-blank");
        }
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public PromptTemplateSource source() {
        return source;
    }

    @Override
    public RenderedPrompt render(String tenantId, Map<String, Object> variables) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(variables, "variables");
        throw new UnsupportedOperationException(
                "SpringAiPromptTemplateAdapter: design-only shell at L0; "
                        + "W2 prompt-rendering wave wires Spring AI PromptTemplate.create(...).");
    }

    /** Exposes the underlying Spring AI prompt-template bean for diagnostic / wiring assertions. */
    public Object underlyingSpringAiPromptTemplate() {
        return springAiPromptTemplate;
    }
}
