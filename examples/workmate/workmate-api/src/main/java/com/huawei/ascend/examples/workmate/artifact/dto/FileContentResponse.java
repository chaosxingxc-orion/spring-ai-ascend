package com.huawei.ascend.examples.workmate.artifact.dto;

public record FileContentResponse(String path, String mime, String content, long size, boolean truncated) {
}
