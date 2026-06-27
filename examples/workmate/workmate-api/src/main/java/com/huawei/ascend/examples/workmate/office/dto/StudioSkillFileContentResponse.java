package com.huawei.ascend.examples.workmate.office.dto;

public record StudioSkillFileContentResponse(
        String path, String content, boolean truncated, boolean binary, boolean editable) {}
