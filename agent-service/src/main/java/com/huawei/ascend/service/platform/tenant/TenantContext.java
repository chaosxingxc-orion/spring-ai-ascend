package com.huawei.ascend.service.platform.tenant;

import java.util.UUID;

// scope: process-internal -- request-bound; not persisted or transmitted across tenants. Rule 11 exempt.
public record TenantContext(UUID tenantId) {}
