/**
 * Chat Advisor SPI — interceptor chains around model invocation and
 * streaming model output.
 *
 * <p>Authority: ADR-0132. Status: design_only at the time of landing.
 *
 * <p>The {@link com.huawei.ascend.middleware.advisor.spi.ChatAdvisor}
 * and {@link com.huawei.ascend.middleware.advisor.spi.StreamingChatAdvisor}
 * patterns are the public Audience B extension surfaces for
 * cross-cutting model-call decoration — PII redaction, memory
 * retrieval augmentation, cost attribution, caching. Customers
 * compose advisors at agent definition time; the platform
 * internally binds the chain to the {@code HookDispatcher}
 * (ADR-0073) — Audience B code never imports
 * {@code HookDispatcher} directly.
 *
 * <p>The runtime binding lives in the W2 LLM gateway wave per
 * ADR-0061 §7 (Telemetry Vertical co-arrives with the Hook SPI
 * activation).
 *
 * <p>Tenant scope per Rule R-C.c: every advised envelope carries
 * {@code tenantId}.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package advisor SPI siblings. Cross-SPI carrier translation
 * belongs in adapter code outside this package.
 */
package com.huawei.ascend.middleware.advisor.spi;
