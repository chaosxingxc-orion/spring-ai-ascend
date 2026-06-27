package com.huawei.ascend.examples.workmate.session.dto;

import java.util.List;

public record AutoArchiveResponse(
        List<AutoArchivedSession> archived, int activeCount, int maxActive) {}
