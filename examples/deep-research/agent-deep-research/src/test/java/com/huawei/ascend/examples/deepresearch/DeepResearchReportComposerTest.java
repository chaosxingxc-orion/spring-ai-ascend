/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.examples.deepresearch.report.DeepResearchCitation;
import com.huawei.ascend.examples.deepresearch.report.DeepResearchOutputContract;
import com.huawei.ascend.examples.deepresearch.report.DeepResearchReportComposer;
import com.huawei.ascend.examples.deepresearch.report.DeepResearchReportOutput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeepResearchReportComposerTest {

    @Test
    void composesMarkdownAndParsableJsonTail() {
        DeepResearchReportOutput output = DeepResearchReportComposer.compose(sampleRequest());
        String markdown = output.toMarkdownWithJsonTail();

        DeepResearchReportOutput parsed = DeepResearchOutputContract.parseReport(markdown);
        assertThat(parsed.comparisonTable()).containsKey("火山方舟");
        assertThat(parsed.citations()).hasSize(1);
        assertThat(parsed.confidencePerField()).containsKey("火山方舟.pricing.input");
    }

    @Test
    void rejectsMissingJsonTail() {
        assertThatThrownBy(() -> DeepResearchOutputContract.parseReport("# no json here"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing ```json```");
    }

    private static DeepResearchReportComposer.ComposeRequest sampleRequest() {
        return new DeepResearchReportComposer.ComposeRequest(
                "国内主流大模型 API 对比 (2026 Q2)",
                "2026-06-22",
                List.of(new DeepResearchReportComposer.VendorRow(
                        "火山方舟",
                        Map.of(
                                "pricing", "输入 0.0008 / 输出 0.002 元/千 token",
                                "context_length", "128K",
                                "rate_limit", "60 QPS",
                                "function_calling", "支持"),
                        List.of(new DeepResearchCitation(
                                "https://www.volcengine.com/pricing", "火山方舟定价", "2026-06-22T00:00:00Z")),
                        Map.of("火山方舟.pricing.input", 0.92))),
                List.of("单厂商样例行"));
    }
}
