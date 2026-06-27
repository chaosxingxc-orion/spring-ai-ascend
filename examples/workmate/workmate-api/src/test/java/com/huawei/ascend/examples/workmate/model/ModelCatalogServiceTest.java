package com.huawei.ascend.examples.workmate.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.examples.workmate.config.LlmModelDefinition;
import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService.ResolvedModel;
import com.huawei.ascend.examples.workmate.model.dto.ModelCatalogResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelCatalogServiceTest {

    @Test
    void synthesizesCatalogFromLegacyFlatConfig() {
        WorkmateLlmProperties llm = new WorkmateLlmProperties(
                "openai", "sk-test", "https://api.example.com/v1", "gpt-4o-mini", true, 10, null, List.of());
        ModelCatalogService service = new ModelCatalogService(llm);

        ModelCatalogResponse catalog = service.catalog();
        assertThat(catalog.models()).hasSize(1);
        assertThat(catalog.models().getFirst().id()).isEqualTo("gpt-4o-mini");
        assertThat(catalog.defaultModelId()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void resolvesConfiguredCatalogEntry() {
        WorkmateLlmProperties llm = new WorkmateLlmProperties(
                "openai",
                "sk-test",
                "https://api.example.com/v1",
                "ignored",
                true,
                10,
                "deepseek-chat",
                List.of(new LlmModelDefinition(
                        "deepseek-chat",
                        "DeepSeek Chat",
                        "openai",
                        "https://api.deepseek.com/v1",
                        null,
                        "deepseek-chat",
                        List.of("chat"))));
        ModelCatalogService service = new ModelCatalogService(llm);

        ResolvedModel resolved = service.resolve("deepseek-chat");
        assertThat(resolved.modelName()).isEqualTo("deepseek-chat");
        assertThat(resolved.apiBase()).isEqualTo("https://api.deepseek.com/v1");
    }

    @Test
    void resolvesPerModelProviderEndpointAndKeyAcrossMultipleModels() {
        WorkmateLlmProperties llm = new WorkmateLlmProperties(
                "openai",
                "sk-global-fallback",
                "https://api.example.com/v1",
                "ignored",
                true,
                10,
                "local-model",
                List.of(
                        // Shares the global key (blank entry key falls back).
                        new LlmModelDefinition(
                                "local-model", "Local", "openai", "http://localhost:11434/v1", null, "local-model", List.of("chat")),
                        // Brings its own provider/endpoint/key.
                        new LlmModelDefinition(
                                "deepseek-chat",
                                "DeepSeek Chat",
                                "openai",
                                "https://api.deepseek.com/v1",
                                "sk-deepseek-key",
                                "deepseek-chat",
                                List.of("chat", "reasoning"))));
        ModelCatalogService service = new ModelCatalogService(llm);

        assertThat(service.catalog().models()).hasSize(2);

        ResolvedModel local = service.resolve("local-model");
        assertThat(local.apiBase()).isEqualTo("http://localhost:11434/v1");
        assertThat(local.apiKey()).isEqualTo("sk-global-fallback");

        ResolvedModel deepseek = service.resolve("deepseek-chat");
        assertThat(deepseek.apiBase()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(deepseek.apiKey()).isEqualTo("sk-deepseek-key");
    }

    @Test
    void rejectsUnknownModel() {
        WorkmateLlmProperties llm = new WorkmateLlmProperties(
                "openai", "sk-test", "https://api.example.com/v1", "gpt-4o-mini", true, 10, "gpt-4o-mini", List.of());
        ModelCatalogService service = new ModelCatalogService(llm);

        assertThatThrownBy(() -> service.resolve("missing"))
                .isInstanceOf(ModelCatalogService.UnknownModelException.class);
    }

    @Test
    void effortMaxUsesHigherTokenBudget() {
        ModelRequestConfig minimal = new ModelRequestConfig();
        ModelRequestConfig max = new ModelRequestConfig();
        ModelEffort.MINIMAL.applyTo(minimal);
        ModelEffort.MAX.applyTo(max);
        assertThat(max.getMaxTokens()).isGreaterThan(minimal.getMaxTokens());
    }
}
