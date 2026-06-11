package com.huawei.ascend.runtime.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.huawei.ascend.runtime.llm.gateway.spi.LlmTokenUsage;
import org.junit.jupiter.api.Test;

class ModelAliasRegistryTest {

    private ModelAliasRegistry registry() {
        LlmGatewayProperties properties = new LlmGatewayProperties();

        LlmGatewayProperties.Upstream priced = new LlmGatewayProperties.Upstream();
        priced.setBaseUrl("https://api.example.com/v1/");
        priced.setApiKey("sk-real");
        priced.setProvider("openai");
        priced.setUpstreamModel("gpt-4o-mini");
        LlmGatewayProperties.Pricing pricing = new LlmGatewayProperties.Pricing();
        pricing.setInputPerMillionTokensUsd(0.15);
        pricing.setOutputPerMillionTokensUsd(0.60);
        priced.setPricing(pricing);
        properties.getAliases().put("finance-chat", priced);

        LlmGatewayProperties.Upstream unpriced = new LlmGatewayProperties.Upstream();
        unpriced.setBaseUrl("https://other.example.com/v1");
        unpriced.setApiKey("sk-other");
        properties.getAliases().put("scratch-model", unpriced);

        return new ModelAliasRegistry(properties);
    }

    @Test
    void resolvesConfiguredAliasWithDerivedUrlAndProvider() {
        ModelAliasRegistry.Route route = registry().resolve("finance-chat").orElseThrow();

        assertThat(route.alias()).isEqualTo("finance-chat");
        assertThat(route.chatCompletionsUrl()).isEqualTo("https://api.example.com/v1/chat/completions");
        assertThat(route.apiKey()).isEqualTo("sk-real");
        assertThat(route.provider()).isEqualTo("openai");
        assertThat(route.upstreamModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void unknownAliasResolvesEmpty() {
        assertThat(registry().resolve("no-such-alias")).isEmpty();
    }

    @Test
    void providerDefaultsToOpenAiCompatibleAndModelPassesThrough() {
        ModelAliasRegistry.Route route = registry().resolve("scratch-model").orElseThrow();

        assertThat(route.provider()).isEqualTo("openai-compatible");
        assertThat(route.upstreamModel()).isNull();
        assertThat(route.chatCompletionsUrl())
                .isEqualTo("https://other.example.com/v1/chat/completions");
    }

    @Test
    void costUsesPerMillionTokenPricesByDirection() {
        Double cost = registry().costUsd("finance-chat", new LlmTokenUsage(1_000_000, 2_000_000, false));

        assertThat(cost).isEqualTo(0.15 + 2 * 0.60);
    }

    /** Misconfiguration must fail deployment startup naming the exact property, not 500 at request time. */
    @Test
    void aliasWithoutBaseUrlFailsConstructionNamingTheProperty() {
        LlmGatewayProperties properties = new LlmGatewayProperties();
        LlmGatewayProperties.Upstream upstream = new LlmGatewayProperties.Upstream();
        upstream.setApiKey("sk-real");
        properties.getAliases().put("team-default", upstream);

        assertThatIllegalStateException()
                .isThrownBy(() -> new ModelAliasRegistry(properties))
                .withMessageContaining("agent-runtime.llm.gateway.aliases.team-default.base-url");
    }

    @Test
    void aliasWithBlankBaseUrlFailsConstruction() {
        LlmGatewayProperties properties = new LlmGatewayProperties();
        LlmGatewayProperties.Upstream upstream = new LlmGatewayProperties.Upstream();
        upstream.setBaseUrl("   ");
        upstream.setApiKey("sk-real");
        properties.getAliases().put("team-default", upstream);

        assertThatIllegalStateException()
                .isThrownBy(() -> new ModelAliasRegistry(properties))
                .withMessageContaining("agent-runtime.llm.gateway.aliases.team-default.base-url");
    }

    /** A null api-key would otherwise go on the wire as the literal "Bearer null". */
    @Test
    void aliasWithoutApiKeyFailsConstructionNamingTheProperty() {
        LlmGatewayProperties properties = new LlmGatewayProperties();
        LlmGatewayProperties.Upstream upstream = new LlmGatewayProperties.Upstream();
        upstream.setBaseUrl("http://localhost:11434/v1");
        properties.getAliases().put("team-default", upstream);

        assertThatIllegalStateException()
                .isThrownBy(() -> new ModelAliasRegistry(properties))
                .withMessageContaining("agent-runtime.llm.gateway.aliases.team-default.api-key");
    }

    /** Explicit empty api-key is the documented opt-in for no-auth local upstreams. */
    @Test
    void explicitlyEmptyApiKeyIsAcceptedAsNoAuthUpstream() {
        LlmGatewayProperties properties = new LlmGatewayProperties();
        LlmGatewayProperties.Upstream upstream = new LlmGatewayProperties.Upstream();
        upstream.setBaseUrl("http://localhost:11434/v1");
        upstream.setApiKey("");
        properties.getAliases().put("local-model", upstream);

        assertThatCode(() -> new ModelAliasRegistry(properties)).doesNotThrowAnyException();
    }

    @Test
    void costIsNullForUnpricedAliasUnknownAliasAndEstimatedUsage() {
        ModelAliasRegistry registry = registry();
        LlmTokenUsage measured = new LlmTokenUsage(10, 10, false);

        assertThat(registry.costUsd("scratch-model", measured)).isNull();
        assertThat(registry.costUsd("no-such-alias", measured)).isNull();
        assertThat(registry.costUsd("finance-chat", LlmTokenUsage.estimatedAbsent())).isNull();
    }
}
