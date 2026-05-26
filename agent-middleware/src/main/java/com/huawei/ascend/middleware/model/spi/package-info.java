/**
 * Model Gateway SPI — tenant-scoped boundary for LLM invocation.
 *
 * <p>Authority: ADR-0121. Status: design_only at the time of landing.
 *
 * <p>The {@link com.huawei.ascend.middleware.model.spi.ModelGateway}
 * interface is the platform's abstraction for chat-style LLM
 * invocation. The reference adapter ({@code SpringAiChatModelGateway},
 * in the Spring AI integration layer) decorates Spring AI's
 * {@code org.springframework.ai.chat.model.ChatModel} (ADR-0125)
 * (Spring AI canonical integration boundary).
 *
 * <p>Threading model: blocking signatures backed by Java 21 virtual
 * threads ({@code spring.threads.virtual.enabled=true}). Reactive
 * implementations MAY wrap blocking calls; the SPI surface stays
 * synchronous to honor Rule R-D (SPI purity: java.* only).
 *
 * <p>Hook binding: {@link com.huawei.ascend.middleware.spi.HookPoint}
 * {@code BEFORE_LLM} fires with {@link ModelInvocation};
 * {@code AFTER_LLM} fires with {@link ModelResponse}. Hooks may
 * short-circuit (ADR-0073).
 *
 * <p>Tenant scope per Rule R-C.c: every invocation carries
 * {@code tenantId} via {@link ModelInvocation#tenantId()}.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package siblings.
 */
package com.huawei.ascend.middleware.model.spi;
