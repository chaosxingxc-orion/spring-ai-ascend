package com.bank.financial.kit.tool;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import java.util.Map;
import java.util.function.Function;

/**
 * Wraps an in-process Java function as an agent tool — for domain logic that
 * runs in the JVM (e.g. a suitability engine over an in-memory product shelf)
 * rather than a remote HTTP call. The counterpart to {@link HttpTool}.
 *
 * <p>Use this when the "backend" is your own deterministic code that the LLM must
 * not bypass — exactly how investor-suitability filtering should be enforced.
 */
public final class LocalTool {

    private LocalTool() {
    }

    public static LocalFunction of(
            String name, String description, Map<String, Object> inputParams,
            Function<Map<String, Object>, Object> fn) {
        ToolCard card = ToolCard.builder()
                .id(name).name(name).description(description)
                .inputParams(inputParams == null ? Map.of() : inputParams)
                .build();
        return new LocalFunction(card, fn);
    }
}
