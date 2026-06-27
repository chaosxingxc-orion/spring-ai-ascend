package com.huawei.ascend.examples.workmate.share.dto;

import java.time.Instant;

public record TempDownloadLinkResponse(String token, String downloadPath, Instant expiresAt) {}
