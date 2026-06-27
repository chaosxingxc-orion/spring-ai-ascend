package com.huawei.ascend.examples.workmate.office.dto;

import java.util.List;

public record StudioExpertCapabilitiesResponse(
        List<StudioExpertCapabilityItemResponse> skills,
        List<StudioExpertCapabilityItemResponse> connectors,
        List<StudioExpertCapabilityItemResponse> unresolved) {}
