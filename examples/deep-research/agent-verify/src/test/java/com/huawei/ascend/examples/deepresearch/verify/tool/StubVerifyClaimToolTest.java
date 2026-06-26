/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StubVerifyClaimToolTest {

    @Test
    void knownClaim_shouldReturnFixturedVerdict() {
        Map<String, Object> result = StubVerifyClaimTool.verify(Map.of(
                "claim", "火山方舟豆包 Pro 4K 模型输入价格为 0.0008 元/千 token",
                "sources", java.util.List.of(),
                "claim_type", "numeric"
        ));

        assertThat(result.get("verdict")).isEqualTo("support");
        assertThat((Double) result.get("confidence")).isGreaterThan(0.9);
        assertThat(result.get("suggested_followup_query")).isNull();
    }

    @Test
    void unknownClaim_shouldReturnInsufficient() {
        Map<String, Object> result = StubVerifyClaimTool.verify(Map.of(
                "claim", "一个完全不存在的声明 xyz123",
                "sources", java.util.List.of(),
                "claim_type", "qualitative"
        ));

        assertThat(result.get("verdict")).isEqualTo("insufficient");
        assertThat((Double) result.get("confidence")).isEqualTo(0.3);
        assertThat(result.get("suggested_followup_query")).isNotNull();
    }

    @Test
    void contradictFixture_shouldBePresent() {
        Map<String, Object> result = StubVerifyClaimTool.verify(Map.of(
                "claim", "阿里百炼 Qwen-Max 上下文窗口为 32K tokens",
                "sources", java.util.List.of(),
                "claim_type", "numeric"
        ));

        assertThat(result.get("verdict")).isEqualTo("contradict");
    }

    @Test
    void hash8_isDeterministic() {
        String h1 = StubVerifyClaimTool.hash8("test claim");
        String h2 = StubVerifyClaimTool.hash8("test claim");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(8);
    }
}
