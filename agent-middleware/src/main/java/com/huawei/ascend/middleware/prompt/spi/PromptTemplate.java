package com.huawei.ascend.middleware.prompt.spi;

import java.util.Map;

/**
 * Tenant-scoped prompt-template rendering boundary.
 *
 * <p>Authority: ADR-0131. The reference adapter
 * {@code SpringAiPromptTemplateAdapter} (agent-service integration
 * package) wraps Spring AI's
 * {@code org.springframework.ai.chat.prompt.PromptTemplate}
 * (ADR-0125).
 *
 * <p>Implementations:
 * <ul>
 *   <li>MUST validate the {@code tenantId} argument non-blank
 *       (Rule R-C.c).</li>
 *   <li>MUST raise {@link IllegalArgumentException} when a
 *       placeholder named in {@link #source()} has no entry in
 *       {@code variables}.</li>
 *   <li>MUST be thread-safe; rendering is invoked from virtual
 *       threads in the W2 prompt-rendering wave.</li>
 *   <li>MUST NOT mutate the supplied {@code variables} map; the
 *       returned {@link RenderedPrompt} captures an immutable copy.</li>
 * </ul>
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package siblings.
 */
public interface PromptTemplate {

    /** Stable identifier (e.g. "support-agent.system-prompt.v1"); non-blank. */
    String templateId();

    /** Source of the template body. */
    PromptTemplateSource source();

    /**
     * Render the template with the given variables.
     *
     * @param tenantId  owning tenant (Rule R-C.c); non-blank.
     * @param variables variable values; never null, may be empty when
     *                  the template has no placeholders.
     * @return rendered prompt; never null.
     * @throws IllegalArgumentException on missing required variable
     *                                  whose placeholder appears in
     *                                  the source.
     */
    RenderedPrompt render(String tenantId, Map<String, Object> variables);
}
