package com.huawei.ascend.examples.workmate.acp;

import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import java.util.LinkedHashMap;
import java.util.Map;

/** W38/W36-A5 — attach team-surface metadata to member sub-run run_events (mirrors AcpInboundConverter). */
public final class TeamSurfaceEnricher {

    private TeamSurfaceEnricher() {}

    public static Map<String, Object> enrich(RunPersistenceContext context, Map<String, Object> payload) {
        if (context == null || context.memberId() == null || context.parentRunId() == null) {
            return payload;
        }
        Map<String, Object> enriched = new LinkedHashMap<>(payload);
        enriched.put("surface", "team");
        enriched.put("memberId", context.memberId());
        enriched.put("parentRunId", context.parentRunId());
        if (context.runId() != null) {
            enriched.put("subRunId", context.runId());
        }
        String memberName = context.memberName();
        if (memberName != null && !memberName.isBlank()) {
            enriched.put("memberName", memberName);
        } else {
            enriched.putIfAbsent("memberName", context.memberId());
        }
        return enriched;
    }
}
