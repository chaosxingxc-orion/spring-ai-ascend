package com.huawei.ascend.examples.workmate.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "workmate.llm")
public record WorkmateLlmProperties(
        String modelProvider,
        String apiKey,
        String apiBase,
        String modelName,
        boolean sslVerify,
        int maxIterations,
        int requestTimeoutSeconds,
        String defaultModelId,
        List<LlmModelDefinition> catalog) {

    @ConstructorBinding
    public WorkmateLlmProperties {
        if (modelProvider == null || modelProvider.isBlank()) {
            modelProvider = "openai";
        }
        if (apiKey == null) {
            apiKey = "sk-local-placeholder";
        }
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = "https://api.openai.com/v1";
        }
        if (modelName == null || modelName.isBlank()) {
            modelName = "gpt-4o-mini";
        }
        if (maxIterations <= 0) {
            maxIterations = 10;
        }
        // openjiuwen's ModelClientConfig defaults to a 60s request timeout, which is too short for
        // long single-shot generations (deep-research members, large report writes) and surfaces as
        // a Reactor "Did not observe any item ... within 60000ms" TimeoutException. Default higher.
        if (requestTimeoutSeconds <= 0) {
            requestTimeoutSeconds = 300;
        }
        if (catalog == null) {
            catalog = List.of();
        }
        if (defaultModelId == null || defaultModelId.isBlank()) {
            defaultModelId = catalog.isEmpty() ? modelName : catalog.getFirst().id();
        }
    }

    /** Back-compat constructor (pre request-timeout); delegates with the default timeout. */
    public WorkmateLlmProperties(
            String modelProvider,
            String apiKey,
            String apiBase,
            String modelName,
            boolean sslVerify,
            int maxIterations,
            String defaultModelId,
            List<LlmModelDefinition> catalog) {
        this(modelProvider, apiKey, apiBase, modelName, sslVerify, maxIterations, 0, defaultModelId, catalog);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("sk-local");
    }
}
