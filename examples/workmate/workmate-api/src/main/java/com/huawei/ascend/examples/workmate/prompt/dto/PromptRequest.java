package com.huawei.ascend.examples.workmate.prompt.dto;

import com.huawei.ascend.examples.workmate.mention.dto.MentionItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PromptRequest(
        @NotBlank @Size(max = 32_000) String message,
        @Valid List<MentionItem> mentions,
        @Valid List<UserAttachmentItem> attachments) {

    public PromptRequest {
        if (mentions == null) {
            mentions = List.of();
        }
        if (attachments == null) {
            attachments = List.of();
        }
    }

    public PromptRequest(String message, List<MentionItem> mentions) {
        this(message, mentions, List.of());
    }
}
