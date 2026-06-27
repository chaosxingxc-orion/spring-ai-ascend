package com.huawei.ascend.examples.workmate.session.dto;

import java.time.Instant;
import java.util.UUID;

public record AutoArchivedSession(UUID id, String title, Instant archivedAt) {}
