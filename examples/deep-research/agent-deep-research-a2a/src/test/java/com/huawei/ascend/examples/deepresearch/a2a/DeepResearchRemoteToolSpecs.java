/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import com.huawei.ascend.runtime.engine.spi.RemoteAgentToolSpec;
import java.util.List;
import java.util.Map;

final class DeepResearchRemoteToolSpecs {

    static final String SEARCH_AGENT = "search-agent";
    static final String READ_AGENT = "read-agent";
    static final String VERIFY_AGENT = "verify-agent";

    private static final Map<String, Object> INPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "remoteInput", Map.of(
                            "type", "string",
                            "description", "Text to send as the remote A2A user message.")),
            "required", List.of("remoteInput"),
            "additionalProperties", true);

    private DeepResearchRemoteToolSpecs() {
    }

    static List<RemoteAgentToolSpec> all() {
        return List.of(
                spec(
                        SEARCH_AGENT,
                        "search-agent / web_search: query, top_k, time_range, language → results[]"),
                spec(
                        READ_AGENT,
                        "read-agent / read_url: url, focus_question → content_markdown, metadata.doc_type"),
                spec(
                        VERIFY_AGENT,
                        "verify-agent / verify_claim: claim, sources, claim_type → verdict, confidence"));
    }

    private static RemoteAgentToolSpec spec(String name, String description) {
        return new RemoteAgentToolSpec(name, name, description, INPUT_SCHEMA);
    }
}
