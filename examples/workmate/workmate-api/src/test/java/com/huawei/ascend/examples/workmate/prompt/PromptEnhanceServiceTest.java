package com.huawei.ascend.examples.workmate.prompt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptEnhanceServiceTest {

    @Test
    void rejectsBlankText() {
        PromptEnhanceService service = new PromptEnhanceService(
                new WorkmateLlmProperties(
                        "openai", "sk-test-key", "https://api.example.com/v1", "gpt-4o-mini", true, 10, "gpt-4o-mini", List.of()),
                new ObjectMapper());

        assertThatThrownBy(() -> service.enhance("  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsWhenLlmNotConfigured() {
        PromptEnhanceService service = new PromptEnhanceService(
                new WorkmateLlmProperties(
                        "openai",
                        "sk-local-placeholder",
                        "https://api.example.com/v1",
                        "gpt-4o-mini",
                        true,
                        10,
                        "gpt-4o-mini",
                        List.of()),
                new ObjectMapper());

        assertThatThrownBy(() -> service.enhance("写一份周报", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM not configured");
    }
}
