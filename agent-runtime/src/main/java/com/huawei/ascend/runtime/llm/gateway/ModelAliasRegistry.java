package com.huawei.ascend.runtime.llm.gateway;

import com.huawei.ascend.runtime.llm.gateway.spi.LlmTokenUsage;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a caller-facing model alias to its configured upstream route. The alias
 * indirection is the gateway's whole adoption mechanism: agents name a platform
 * alias, and only this registry knows the real base URL, credential and pricing.
 *
 * <p>Alias entries are validated at construction so a misconfigured routing table
 * fails the deployment at startup, naming the exact property — never at request
 * time, where a missing key would surface as an unexplained 500 to an agent
 * developer who cannot see the gateway's configuration.
 */
public final class ModelAliasRegistry {

    private static final String ALIAS_PROPERTY_PREFIX = "agent-runtime.llm.gateway.aliases.";

    private final Map<String, LlmGatewayProperties.Upstream> aliases;

    public ModelAliasRegistry(LlmGatewayProperties properties) {
        properties.getAliases().forEach(ModelAliasRegistry::validateAlias);
        this.aliases = Map.copyOf(properties.getAliases());
    }

    private static void validateAlias(String alias, LlmGatewayProperties.Upstream upstream) {
        if (upstream.getBaseUrl() == null || upstream.getBaseUrl().isBlank()) {
            throw new IllegalStateException(
                    ALIAS_PROPERTY_PREFIX + alias + ".base-url is required and must not be blank");
        }
        if (upstream.getApiKey() == null) {
            throw new IllegalStateException(ALIAS_PROPERTY_PREFIX + alias
                    + ".api-key is required — set it to an empty string for an upstream"
                    + " that takes no authentication");
        }
    }

    public Optional<Route> resolve(String alias) {
        return Optional.ofNullable(aliases.get(alias)).map(upstream -> new Route(alias, upstream));
    }

    /**
     * Priced cost of a call against an alias, or null when the alias is unknown,
     * unpriced, or the usage is estimated — an invented cost would silently
     * corrupt the spend ledger, so absence is reported as absence.
     */
    public Double costUsd(String alias, LlmTokenUsage usage) {
        LlmGatewayProperties.Upstream upstream = aliases.get(alias);
        if (upstream == null || upstream.getPricing() == null || usage.estimated()) {
            return null;
        }
        return upstream.getPricing().costUsd(usage.inputTokens(), usage.outputTokens());
    }

    /** A resolved alias: the upstream route plus the URL/header values derived from it. */
    public record Route(String alias, LlmGatewayProperties.Upstream upstream) {

        public String chatCompletionsUrl() {
            String base = upstream.getBaseUrl();
            return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                    + "/chat/completions";
        }

        public String apiKey() {
            return upstream.getApiKey();
        }

        public String provider() {
            return upstream.getProvider();
        }

        /** The model name to forward, or null when the alias passes through unchanged. */
        public String upstreamModel() {
            return upstream.getUpstreamModel();
        }
    }
}
