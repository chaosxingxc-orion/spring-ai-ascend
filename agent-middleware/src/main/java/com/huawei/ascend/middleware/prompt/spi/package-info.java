/**
 * Prompt Template SPI — tenant-scoped boundary for prompt rendering.
 *
 * <p>Authority: ADR-0131. Status: design_only at the time of landing.
 *
 * <p>The {@link com.huawei.ascend.middleware.prompt.spi.PromptTemplate}
 * interface is the platform's abstraction for prompt construction.
 * The reference adapter {@code SpringAiPromptTemplateAdapter} (lands
 * in the agent-service integration package) decorates Spring AI's
 * {@code org.springframework.ai.chat.prompt.PromptTemplate}
 * (ADR-0125 Spring AI canonical integration boundary).
 *
 * <p>Tenant scope per Rule R-C.c:
 * {@link com.huawei.ascend.middleware.prompt.spi.PromptTemplate#render}
 * takes an explicit {@code tenantId} argument.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package siblings.
 */
package com.huawei.ascend.middleware.prompt.spi;
