package com.huawei.ascend.middleware.prompt.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of rendering a {@link PromptTemplate}.
 *
 * <p>Authority: ADR-0131, schema at
 * {@code docs/contracts/prompt-template.v1.yaml}.
 *
 * <p>The {@code variables} field captures an immutable copy of the
 * resolver inputs so the rendered prompt is auditable / replayable
 * end-to-end (it feeds ModelInvocation.hookContext at the W2
 * binding).
 *
 * @param templateId    stable id of the source template (matches
 *                      {@link PromptTemplate#templateId()}); MUST
 *                      be non-blank.
 * @param renderedText  rendered text after variable substitution;
 *                      MUST be non-null, MAY be empty when the
 *                      template resolves to an empty string.
 * @param variables     immutable copy of the variables used during
 *                      rendering; never null.
 */
public record RenderedPrompt(
        String templateId,
        String renderedText,
        Map<String, Object> variables) {

    public RenderedPrompt {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(renderedText, "renderedText");
        Objects.requireNonNull(variables, "variables");
        if (templateId.isBlank()) {
            throw new IllegalArgumentException("templateId must be non-blank");
        }
        variables = Map.copyOf(variables);
    }
}
