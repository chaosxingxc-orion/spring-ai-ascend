package com.huawei.ascend.examples.workmate.office.dto;

import java.util.Map;

public record StudioLeadWriteRequest(String name, Map<String, String> title, String avatar) {}
