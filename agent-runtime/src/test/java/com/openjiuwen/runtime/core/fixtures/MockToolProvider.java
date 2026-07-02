package com.openjiuwen.runtime.core.fixtures;

import com.openjiuwen.runtime.core.engine.DefaultAgentKernel;
import com.openjiuwen.core.kernel.model.ToolName;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Mock Tool Provider -- registers test tools with controllable behavior.
 *
 * Supports:
 * 1. Simple tools that return fixed values
 * 2. Tools with custom execute functions
 * 3. Tools that throw exceptions (for failure testing)
 */
public class MockToolProvider {

    private final Map<ToolName, DefaultAgentKernel.ToolExecutor> tools = new HashMap<>();

    /** Register a tool that returns a fixed value */
    public MockToolProvider register(String name, Object returnValue) {
        tools.put(new ToolName(name), args -> returnValue);
        return this;
    }

    /** Register a tool with a custom execute function */
    public MockToolProvider register(String name, Function<Map<String, Object>, Object> fn) {
        tools.put(new ToolName(name), args -> fn.apply(args));
        return this;
    }

    /** Register a tool that always throws */
    public MockToolProvider registerFailing(String name, String errorMessage) {
        tools.put(new ToolName(name), args -> {
            throw new RuntimeException(errorMessage);
        });
        return this;
    }

    /** Register a tool that succeeds on the Nth call (first N-1 calls fail) */
    public MockToolProvider registerFlaky(String name, int failCount, Object successValue) {
        final int[] counter = {0};
        tools.put(new ToolName(name), args -> {
            counter[0]++;
            if (counter[0] < failCount) {
                throw new RuntimeException("模拟失败 (第" + counter[0] + "次)");
            }
            return successValue;
        });
        return this;
    }

    /** Build the tool map for DefaultAgentKernel constructor */
    public Map<ToolName, DefaultAgentKernel.ToolExecutor> build() {
        return Map.copyOf(tools);
    }
}
