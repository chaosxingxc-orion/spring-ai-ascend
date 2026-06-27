package com.huawei.ascend.examples.workmate.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApprovalDecisionRequest(
        @NotBlank @Pattern(regexp = "approve|deny", flags = Pattern.Flag.CASE_INSENSITIVE) String decision,
        String scope,
        Boolean always) {
}
