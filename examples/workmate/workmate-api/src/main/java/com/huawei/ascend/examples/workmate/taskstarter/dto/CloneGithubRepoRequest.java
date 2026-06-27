package com.huawei.ascend.examples.workmate.taskstarter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloneGithubRepoRequest(
        @NotBlank @Size(max = 128) String owner,
        @NotBlank @Size(max = 128) String repo,
        @Size(max = 256) String branch) {}
