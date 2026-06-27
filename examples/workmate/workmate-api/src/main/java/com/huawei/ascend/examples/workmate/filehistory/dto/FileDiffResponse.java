package com.huawei.ascend.examples.workmate.filehistory.dto;

public record FileDiffResponse(
        String path,
        String mime,
        String original,
        String modified,
        boolean truncated) {}
