package com.huawei.ascend.service.integration.springai;

import com.huawei.ascend.middleware.model.spi.ModelResponse;
import com.huawei.ascend.middleware.model.spi.StructuredOutputConverter;

import java.util.Objects;

/**
 * Reference {@link StructuredOutputConverter} that decorates a
 * Spring AI {@code BeanOutputConverter}.
 *
 * <p>Authority: ADR-0130 + ADR-0125. Design-only shell at L0 — see
 * package-info / ADR-0125 for the L0 vs W2 boundary; Spring AI types
 * are confined to this integration package (the underlying converter
 * is held as {@link Object} to defer the FQN binding until the W2
 * LLM gateway wave wires it).
 *
 * <p>W2 implementation responsibilities:
 * <ul>
 *   <li>Bind the held {@link #springAiBeanOutputConverter} to the
 *       Spring AI {@code BeanOutputConverter<T>} FQN (cast or
 *       reflective adapter).</li>
 *   <li>Delegate {@link #convert(ModelResponse)} to
 *       {@code BeanOutputConverter.convert(response.content())}.</li>
 *   <li>Delegate {@link #getFormatInstructions()} to
 *       {@code BeanOutputConverter.getFormat()}.</li>
 *   <li>Wrap parse failures in {@link IllegalArgumentException}
 *       per the SPI contract.</li>
 * </ul>
 *
 * @param <T> target value type produced by {@link #convert(ModelResponse)}.
 */
public final class SpringAiBeanOutputConverterAdapter<T> implements StructuredOutputConverter<T> {

    private final Object springAiBeanOutputConverter;
    private final Class<T> targetType;

    public SpringAiBeanOutputConverterAdapter(Object springAiBeanOutputConverter, Class<T> targetType) {
        this.springAiBeanOutputConverter = Objects.requireNonNull(springAiBeanOutputConverter,
                "springAiBeanOutputConverter");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
    }

    @Override
    public T convert(ModelResponse response) {
        Objects.requireNonNull(response, "response");
        throw new UnsupportedOperationException(
                "SpringAiBeanOutputConverterAdapter: design-only shell at L0; "
                        + "W2 LLM gateway wave wires BeanOutputConverter.convert(...).");
    }

    @Override
    public String getFormatInstructions() {
        throw new UnsupportedOperationException(
                "SpringAiBeanOutputConverterAdapter: design-only shell at L0; "
                        + "W2 LLM gateway wave wires BeanOutputConverter.getFormat().");
    }

    /** Exposes the underlying Spring AI converter for diagnostic / wiring assertions. */
    public Object underlyingSpringAiConverter() {
        return springAiBeanOutputConverter;
    }

    /** Target type the adapter converts into; non-null. */
    public Class<T> targetType() {
        return targetType;
    }
}
