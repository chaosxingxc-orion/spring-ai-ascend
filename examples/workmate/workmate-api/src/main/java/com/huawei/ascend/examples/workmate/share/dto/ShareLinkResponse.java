package com.huawei.ascend.examples.workmate.share.dto;

import java.time.Instant;

public record ShareLinkResponse(String token, String sharePath, String scope, Instant expiresAt) {}
