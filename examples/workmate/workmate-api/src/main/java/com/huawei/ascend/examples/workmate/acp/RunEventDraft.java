package com.huawei.ascend.examples.workmate.acp;

import java.util.Map;

/** Inbound conversion result before persisting to {@code run_events} (W38). */
public record RunEventDraft(String eventName, Map<String, Object> payload) {}
