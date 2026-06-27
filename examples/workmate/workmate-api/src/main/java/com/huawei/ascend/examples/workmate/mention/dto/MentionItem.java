package com.huawei.ascend.examples.workmate.mention.dto;

import com.huawei.ascend.examples.workmate.mention.MentionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MentionItem(
        @NotBlank @Size(max = 32) String type,
        @NotBlank @Size(max = 256) String id,
        @Size(max = 1024) String path,
        @Size(max = 256) String label) {

    public MentionType mentionType() {
        return MentionType.parse(type);
    }
}
