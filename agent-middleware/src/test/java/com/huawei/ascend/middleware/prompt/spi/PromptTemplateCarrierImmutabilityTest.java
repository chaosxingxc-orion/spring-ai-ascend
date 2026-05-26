package com.huawei.ascend.middleware.prompt.spi;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateCarrierImmutabilityTest {

    @Test
    void inlineStringRejectsBlankBody() {
        assertThatThrownBy(() -> new PromptTemplateSource.InlineString(
                "   ", PromptTemplateSource.PlaceholderSyntax.MUSTACHE_SINGLE_BRACE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body");

        assertThatThrownBy(() -> new PromptTemplateSource.InlineString(
                null, PromptTemplateSource.PlaceholderSyntax.MUSTACHE_SINGLE_BRACE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void classpathResourceRejectsBlankPath() {
        assertThatThrownBy(() -> new PromptTemplateSource.ClasspathResource(
                "", PromptTemplateSource.PlaceholderSyntax.DOLLAR_BRACE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");

        assertThatThrownBy(() -> new PromptTemplateSource.ClasspathResource(
                "prompts/system.txt", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void renderedPromptCopiesVariablesMap() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Alice");
        variables.put("tone", "formal");

        RenderedPrompt rendered = new RenderedPrompt(
                "support-agent.system-prompt.v1",
                "Hello Alice (formal).",
                variables);

        variables.put("name", "mutated");
        variables.put("injected", "value");

        assertThat(rendered.variables()).containsEntry("name", "Alice");
        assertThat(rendered.variables()).containsEntry("tone", "formal");
        assertThat(rendered.variables()).doesNotContainKey("injected");
        assertThatThrownBy(() -> rendered.variables().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void renderedPromptRejectsBlankTemplateId() {
        assertThatThrownBy(() -> new RenderedPrompt("   ", "text", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateId");
    }

    @Test
    void renderedPromptAllowsEmptyRenderedText() {
        RenderedPrompt rendered = new RenderedPrompt(
                "empty-template.v1",
                "",
                Map.of());

        assertThat(rendered.renderedText()).isEmpty();
        assertThat(rendered.variables()).isEmpty();
    }
}
