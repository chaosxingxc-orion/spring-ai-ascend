package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.common.RunEvent;
import java.util.concurrent.Flow;

/**
 * Converts a framework-specific result/event stream into the neutral reactive
 * {@code Flow.Publisher<RunEvent>}. Follows the Spring {@code Converter<S,T>} naming convention
 * ({@code convert}); deliberately not named after any single agent framework's stream type.
 *
 * <p>This is the ONLY place a framework's native stream shape (openjiuwen {@code Iterator},
 * Spring-AI/langchain4j reactive {@code ChatResponse} streams, langchain4j callback
 * {@code TokenStream}, langgraph4j {@code AsyncGenerator}, Dify SSE, ...) is bridged to the
 * runtime's neutral event stream. Nothing above the converter sees the native type.
 */
@FunctionalInterface
public interface OutputConverter {

    Flow.Publisher<RunEvent> convert(Object frameworkStream);
}
