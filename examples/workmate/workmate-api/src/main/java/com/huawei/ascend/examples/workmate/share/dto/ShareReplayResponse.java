package com.huawei.ascend.examples.workmate.share.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ShareReplayResponse(
        String token,
        UUID sessionId,
        String title,
        String expertId,
        Instant sharedAt,
        String scope,
        Instant expiresAt,
        List<Map<String, Object>> messages,
        List<Map<String, Object>> events,
        List<ShareArtifactSummary> artifacts) {}
