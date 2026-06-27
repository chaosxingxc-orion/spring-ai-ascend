package com.huawei.ascend.examples.workmate.audit.chain.dto;

import java.util.List;

public record AuditEntryPageResponse(List<AuditEntryResponse> entries, Long nextCursor) {
}
