package com.huawei.ascend.middleware.model.spi;

/**
 * Typed converter from a {@link ModelResponse} to a value of type
 * {@code T}.
 *
 * <p>Authority: ADR-0130, schema at
 * {@code docs/contracts/structured-output.v1.yaml}.
 *
 * <p>Canonical usage:
 * <pre>{@code
 * StructuredOutputConverter<Invoice> converter = ...;
 * String fmt = converter.getFormatInstructions();
 * // append fmt as a system or user message inside ModelInvocation.messages();
 * ModelResponse rsp = modelGateway.invoke(invocation);
 * Invoice typed = converter.convert(rsp);
 * }</pre>
 *
 * <p>The reference adapter
 * {@code SpringAiBeanOutputConverterAdapter} (W2 LLM gateway wave)
 * wraps Spring AI's {@code BeanOutputConverter} (ADR-0125).
 *
 * <p>Implementations MUST be thread-safe; the runtime may invoke
 * {@link #convert(ModelResponse)} from virtual threads.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package siblings.
 *
 * @param <T> target value type produced by {@link #convert(ModelResponse)}.
 */
public interface StructuredOutputConverter<T> {

    /**
     * Convert an assistant response into a typed value.
     *
     * @param response the model response whose {@link ModelResponse#content()}
     *                 carries the convertible payload; never null.
     * @return the converted value; never null.
     * @throws IllegalArgumentException when {@code response.content()} cannot
     *                                  be parsed into {@code T}.
     */
    T convert(ModelResponse response);

    /**
     * Human-readable format-instructions string to be appended to the
     * model prompt so the model emits parseable output. Typical
     * implementations return a JSON schema or a short natural-language
     * directive ("Respond with a single JSON object matching this
     * schema: ...").
     *
     * @return the prompt fragment; never null.
     */
    String getFormatInstructions();
}
