package com.huawei.ascend.examples.workmate.member;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MemberExpertPromptLoader {

    private final String expertId;
    private final Path officeRoot;
    private final String fallbackPrompt;

    public MemberExpertPromptLoader(
            @Value("${workmate.member.expert-id:member-expert}") String expertId,
            @Value("${workmate.office.root:../office}") String officeRoot,
            @Value("${workmate.member.fallback-prompt:You are a WorkMate office expert. Answer concisely in markdown.}")
            String fallbackPrompt) {
        this.expertId = expertId;
        this.officeRoot = Path.of(officeRoot);
        this.fallbackPrompt = fallbackPrompt;
    }

    public String systemPrompt() {
        Path promptFile = officeRoot.resolve("experts").resolve(expertId).resolve("prompt.md");
        if (!Files.isRegularFile(promptFile)) {
            return fallbackPrompt;
        }
        try {
            return Files.readString(promptFile, StandardCharsets.UTF_8).trim();
        } catch (Exception ex) {
            return fallbackPrompt;
        }
    }

    public String expertId() {
        return expertId;
    }
}
