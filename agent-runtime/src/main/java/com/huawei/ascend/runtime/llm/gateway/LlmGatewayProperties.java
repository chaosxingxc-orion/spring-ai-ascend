package com.huawei.ascend.runtime.llm.gateway;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM egress gateway settings: the model-alias routing table and the minted-token
 * directory. Raw provider credentials live only here (Vault-resolvable like any
 * other property) — agents are provisioned minted tokens, never provider keys.
 */
@ConfigurationProperties("agent-runtime.llm.gateway")
public class LlmGatewayProperties {

    private boolean enabled;

    /** Model alias → upstream route. The alias is the {@code model} value callers send. */
    private Map<String, Upstream> aliases = new LinkedHashMap<>();

    /** Minted token string → the (tenant, agent) identity it was provisioned for. */
    private Map<String, MintedToken> tokens = new LinkedHashMap<>();

    /** TCP connect timeout for reaching an upstream provider, on both forwarding paths. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Upstream response timeout. LLM completions routinely take tens of seconds,
     * hence the generous default. On the buffered path it bounds the whole
     * exchange; on the streaming path it bounds time-to-response-headers only —
     * an SSE body may legitimately relay for longer than any fixed bound, so the
     * relay itself is left unbounded by design.
     */
    private Duration requestTimeout = Duration.ofSeconds(120);

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, Upstream> getAliases() { return aliases; }

    public void setAliases(Map<String, Upstream> aliases) { this.aliases = aliases; }

    public Map<String, MintedToken> getTokens() { return tokens; }

    public void setTokens(Map<String, MintedToken> tokens) { this.tokens = tokens; }

    public Duration getConnectTimeout() { return connectTimeout; }

    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getRequestTimeout() { return requestTimeout; }

    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }

    /** One OpenAI-compatible upstream behind a model alias. */
    public static class Upstream {

        /** OpenAI-compatible API root including the version segment, e.g. {@code https://api.openai.com/v1}. */
        private String baseUrl;

        /**
         * Real provider credential, sent upstream as {@code Authorization: Bearer …}.
         * Required; an explicitly empty value declares a no-auth upstream (e.g. a
         * local model server) and omits the Authorization header entirely.
         */
        private String apiKey;

        /** Provider label carried into telemetry ({@code gen_ai.system}, meter tag). */
        private String provider = "openai-compatible";

        /**
         * Real model name at the upstream. Null forwards the alias unchanged —
         * for deployments whose alias IS the provider model name.
         */
        private String upstreamModel;

        /** Pricing of this alias. Null means unpriced: cost is omitted, not zeroed. */
        private Pricing pricing;

        public String getBaseUrl() { return baseUrl; }

        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getApiKey() { return apiKey; }

        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getProvider() { return provider; }

        public void setProvider(String provider) { this.provider = provider; }

        public String getUpstreamModel() { return upstreamModel; }

        public void setUpstreamModel(String upstreamModel) { this.upstreamModel = upstreamModel; }

        public Pricing getPricing() { return pricing; }

        public void setPricing(Pricing pricing) { this.pricing = pricing; }
    }

    /** The (tenant, agent) identity a minted gateway token resolves to. */
    public static class MintedToken {

        private String tenantId;
        private String agentId;

        public String getTenantId() { return tenantId; }

        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getAgentId() { return agentId; }

        public void setAgentId(String agentId) { this.agentId = agentId; }
    }

    /** USD price per million tokens, split by direction like provider price sheets. */
    public static class Pricing {

        private double inputPerMillionTokensUsd;
        private double outputPerMillionTokensUsd;

        public double getInputPerMillionTokensUsd() { return inputPerMillionTokensUsd; }

        public void setInputPerMillionTokensUsd(double inputPerMillionTokensUsd) {
            this.inputPerMillionTokensUsd = inputPerMillionTokensUsd;
        }

        public double getOutputPerMillionTokensUsd() { return outputPerMillionTokensUsd; }

        public void setOutputPerMillionTokensUsd(double outputPerMillionTokensUsd) {
            this.outputPerMillionTokensUsd = outputPerMillionTokensUsd;
        }

        public double costUsd(long inputTokens, long outputTokens) {
            return inputTokens * inputPerMillionTokensUsd / 1_000_000.0
                    + outputTokens * outputPerMillionTokensUsd / 1_000_000.0;
        }
    }
}
