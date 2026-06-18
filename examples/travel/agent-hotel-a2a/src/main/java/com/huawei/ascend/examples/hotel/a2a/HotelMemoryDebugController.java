/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import com.huawei.ascend.runtime.engine.spi.MemoryProvider.MemoryRecord;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only HTTP inspection of the process-local hotel memory.
 *
 * <p>Hosted only when the in-memory provider is active — when Mem0 is selected
 * via {@code hotel-agent.memory.provider=mem0}, inspection should happen against
 * the Mem0 API or its own UI, so this endpoint is intentionally absent.
 *
 * <p>{@code GET /debug/memory?tenantId=...&userId=...} returns the saved records
 * for that {@code (tenantId, userId)} partition in insertion order. Demo /
 * dogfood use only — no auth, no paging, no redaction.
 */
@Configuration(proxyBeanMethods = false)
@RestController
@RequestMapping("/debug/memory")
@ConditionalOnBean(HotelInMemoryMemoryProvider.class)
class HotelMemoryDebugController {

    private final HotelInMemoryMemoryProvider provider;

    HotelMemoryDebugController(HotelInMemoryMemoryProvider provider) {
        this.provider = provider;
    }

    @GetMapping
    Map<String, Object> list(
            @RequestParam(name = "tenantId", required = false, defaultValue = "default") String tenantId,
            @RequestParam(name = "userId") String userId) {
        List<MemoryRecord> records = provider.records(tenantId, userId);
        return Map.of(
                "tenantId", tenantId,
                "userId", userId,
                "count", records.size(),
                "records", records);
    }
}
