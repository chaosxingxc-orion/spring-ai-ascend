package com.huawei.ascend.examples.workmate.config;

import java.util.List;

/**
 * Single entry in the LLM model catalog (W34 G6).
 *
 * <p>Each entry may override the provider/endpoint/key of the global {@code workmate.llm} block,
 * so the catalog can mix models from different providers (e.g. a local Ollama model plus a hosted
 * DeepSeek/OpenAI model) and let the user pick per session. Any field left blank falls back to the
 * global default at resolve time.
 */
public record LlmModelDefinition(
        String id,
        String displayName,
        String provider,
        String apiBase,
        String apiKey,
        String modelName,
        List<String> capabilities) {

    public LlmModelDefinition {
        if (capabilities == null) {
            capabilities = List.of();
        }
    }
}
