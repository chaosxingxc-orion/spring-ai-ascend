package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenJiuwen-local adapter from the runtime-neutral {@link MemoryProvider} to
 * OpenJiuwen 0.1.12 external memory provider semantics.
 *
 * <p>This class deliberately stays in the OpenJiuwen package. If OpenJiuwen
 * later moves memory to a split module, only this adapter and the OpenJiuwen
 * wiring should change; the runtime SPI must remain OpenJiuwen-free.
 */
final class OpenJiuwenExternalMemoryProviderAdapter implements com.openjiuwen.core.memory.external.MemoryProvider {

    private static final int DEFAULT_PREFETCH_LIMIT = 5;
    private static final String PROVIDER_NAME = "runtime_memory";
    private static final String SOURCE = "openjiuwen-external-memory";

    private final AgentExecutionContext context;
    private final MemoryProvider delegate;
    private final int prefetchLimit;
    private boolean initialized;

    OpenJiuwenExternalMemoryProviderAdapter(AgentExecutionContext context, MemoryProvider delegate) {
        this(context, delegate, DEFAULT_PREFETCH_LIMIT);
    }

    OpenJiuwenExternalMemoryProviderAdapter(AgentExecutionContext context, MemoryProvider delegate, int prefetchLimit) {
        this.context = Objects.requireNonNull(context, "context");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.prefetchLimit = Math.max(1, prefetchLimit);
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void initialize(Map<String, Object> scope) {
        delegate.init(context);
        initialized = true;
    }

    @Override
    public List<Map<String, Object>> getToolSchemas() {
        return List.of();
    }

    @Override
    public String handleToolCall(String toolName, Map<String, Object> arguments) {
        return "{\"error\":\"runtime memory provider exposes no OpenJiuwen memory tools\"}";
    }

    @Override
    public String prefetch(String query, Map<String, Object> scope) {
        if (query == null || query.isBlank()) {
            return "";
        }
        List<MemoryProvider.MemoryHit> hits = delegate.search(context, query, prefetchLimit);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (MemoryProvider.MemoryHit hit : hits) {
            if (hit != null && !hit.content().isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append("- ").append(hit.content());
            }
        }
        return builder.toString();
    }

    @Override
    public void syncTurn(String userMessage, String assistantMessage, Map<String, Object> scope) {
        List<MemoryProvider.MemoryRecord> records = new ArrayList<>();
        if (hasText(userMessage)) {
            records.add(new MemoryProvider.MemoryRecord(null, "user", userMessage, Map.of("source", SOURCE)));
        }
        if (hasText(assistantMessage)) {
            records.add(new MemoryProvider.MemoryRecord(null, "assistant", assistantMessage, Map.of("source", SOURCE)));
        }
        if (!records.isEmpty()) {
            delegate.save(context, records);
        }
    }

    @Override
    public String systemPromptBlock() {
        return "";
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
