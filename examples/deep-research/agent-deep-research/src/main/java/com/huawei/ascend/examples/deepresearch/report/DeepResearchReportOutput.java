/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured final output contract for deep-research-agent (TOPOLOGY §3.1).
 */
public record DeepResearchReportOutput(
        String reportMarkdown,
        Map<String, Map<String, String>> comparisonTable,
        List<DeepResearchCitation> citations,
        Map<String, Double> confidencePerField) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, Object> toMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(DeepResearchOutputContract.REPORT_MARKDOWN, reportMarkdown);
        root.put(DeepResearchOutputContract.COMPARISON_TABLE, comparisonTable);
        root.put(DeepResearchOutputContract.CITATIONS, citations.stream().map(this::citationMap).toList());
        root.put(DeepResearchOutputContract.CONFIDENCE_PER_FIELD, confidencePerField);
        return root;
    }

    public String toJsonBlock() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toMap());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to serialize deep-research report output", error);
        }
    }

    public String toMarkdownWithJsonTail() {
        return reportMarkdown + "\n\n```json\n" + toJsonBlock() + "\n```\n";
    }

    private Map<String, Object> citationMap(DeepResearchCitation citation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("url", citation.url());
        map.put("title", citation.title());
        map.put("fetched_at", citation.fetchedAt());
        return map;
    }
}
