/**
 * Spring AI reference adapter layer (ADR-0125) (Spring AI as the
 * canonical Model / Tool / Vector / Embedding / Retrieval abstraction).
 *
 * <p>This package hosts the first production usage of Spring AI in
 * the codebase. Each adapter implements one platform SPI by
 * delegating to the matching Spring AI bean, adding tenant scoping
 * (Rule R-C.c), hook binding (ADR-0073), {@code ResilienceContract}
 * pass-through (ADR-0080), and {@code TraceContext} propagation
 * (ADR-0061).
 *
 * <p><b>Design-only shells:</b> the adapters
 * declare the correct constructor + SPI surface so the boundary
 * is provable at compile-time. The {@code LlmGatewayHookChainOnlyTest}
 * asserts the L0 {@code ChatModel} shell cannot call the provider yet;
 * runtime method bodies throw {@link UnsupportedOperationException}
 * pending the W2 LLM gateway / W3 RAG vertical implementations that
 * bind Spring AI calls behind the platform's hook + capacity machinery.
 *
 * <p>No adapter is auto-wired as a {@code @Bean} at L0. Customers
 * who want to use a Spring AI provider declare their own
 * {@code @Configuration} class per Rule R-A and instantiate the
 * adapter with their {@code ChatModel} / {@code VectorStore} /
 * {@code EmbeddingModel} bean. The {@code AppPostureGate} permits
 * such wiring in {@code dev} posture; {@code research}/{@code prod}
 * require explicit {@code @ConfigurationProperties} binding.
 *
 * <p>Authority: ADR-0120, ADR-0121, ADR-0124, ADR-0125, ADR-0127.
 */
package com.huawei.ascend.service.integration.springai;
