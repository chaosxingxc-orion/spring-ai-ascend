/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyClaimToolTest {

    @Test
    void numericClaim_exactMatch_shouldReturnExactMatch() {
        Map<String, Object> inputs = Map.of(
                "claim", "豆包 Pro 4K 模型输入价格为 0.0008 元/千 token",
                "sources", List.of(
                        Map.of("url", "https://www.volcengine.com/pricing",
                               "excerpt", "豆包 Pro 4K 输入价格 0.0008 元/千 token，输出 0.002 元/千 token")
                ),
                "claim_type", "numeric"
        );

        Map<String, Object> result = VerifyClaimTool.verify(inputs);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) result.get("evidence");
        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).get("match_status")).isEqualTo("exact_match");
        assertThat(evidence.get(0).get("is_authoritative")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> agg = (Map<String, Object>) result.get("aggregate");
        assertThat(agg.get("supporting_count")).isEqualTo(1);
        assertThat(agg.get("contradicting_count")).isEqualTo(0);
    }

    @Test
    void numericClaim_mismatch_shouldReturnMismatch() {
        Map<String, Object> inputs = Map.of(
                "claim", "上下文窗口为 128K tokens",
                "sources", List.of(
                        Map.of("url", "https://example.com/review",
                               "excerpt", "该模型的上下文窗口为 32K tokens")
                ),
                "claim_type", "numeric"
        );

        Map<String, Object> result = VerifyClaimTool.verify(inputs);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) result.get("evidence");
        assertThat(evidence.get(0).get("match_status")).isEqualTo("mismatch");
    }

    @Test
    void categoricalClaim_allTermsFound_shouldReturnExactMatch() {
        Map<String, Object> inputs = Map.of(
                "claim", "支持 Function Calling、流式输出",
                "sources", List.of(
                        Map.of("url", "https://www.volcengine.com/docs",
                               "excerpt", "豆包模型支持 Function Calling 功能，同时提供流式输出能力")
                ),
                "claim_type", "categorical"
        );

        Map<String, Object> result = VerifyClaimTool.verify(inputs);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) result.get("evidence");
        assertThat(evidence.get(0).get("match_status")).isEqualTo("exact_match");
    }

    @Test
    void categoricalClaim_partialMatch_shouldReturnPartialMatch() {
        Map<String, Object> inputs = Map.of(
                "claim", "支持 Function Calling、图像理解",
                "sources", List.of(
                        Map.of("url", "https://api.example.com/docs",
                               "excerpt", "该API支持 Function Calling 功能")
                ),
                "claim_type", "categorical"
        );

        Map<String, Object> result = VerifyClaimTool.verify(inputs);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) result.get("evidence");
        assertThat(evidence.get(0).get("match_status")).isEqualTo("partial_match");
        @SuppressWarnings("unchecked")
        Map<String, Object> extracted = (Map<String, Object>) evidence.get(0).get("extracted_values");
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) extracted.get("missing_from_excerpt");
        assertThat(missing).contains("图像理解");
    }

    @Test
    void qualitativeClaim_keyTermsFound_shouldReturnPartialMatch() {
        Map<String, Object> inputs = Map.of(
                "claim", "DeepSeek Python 代码生成 基准测试",
                "sources", List.of(
                        Map.of("url", "https://deepseek.com/blog",
                               "excerpt", "DeepSeek V3 在代码生成基准测试中取得优异成绩，HumanEval Python 得分 92.5%")
                ),
                "claim_type", "qualitative"
        );

        Map<String, Object> result = VerifyClaimTool.verify(inputs);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) result.get("evidence");
        assertThat(evidence.get(0).get("match_status")).isEqualTo("partial_match");
    }

    @Test
    void noSources_shouldReturnEmptyEvidence() {
        Map<String, Object> result = VerifyClaimTool.verify(Map.of(
                "claim", "任意声明",
                "sources", List.of(),
                "claim_type", "numeric"
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> agg = (Map<String, Object>) result.get("aggregate");
        assertThat(agg.get("total_sources")).isEqualTo(0);
    }

    @Test
    void extractNumbers_shouldHandleUnits() {
        assertThat(VerifyClaimTool.extractNumbers("价格为 0.0008 元/千 token"))
                .contains(0.0008);
        assertThat(VerifyClaimTool.extractNumbers("上下文 128K"))
                .contains(128000.0);
        assertThat(VerifyClaimTool.extractNumbers("3千台设备"))
                .contains(3000.0);
    }

    @Test
    void extractDates_shouldHandleVariants() {
        assertThat(VerifyClaimTool.extractDates("截至 2026 年 Q2"))
                .contains("2026-Q2");
        assertThat(VerifyClaimTool.extractDates("2026年6月发布"))
                .contains("2026-6月");
    }
}
