/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.deepresearch.mock.DeepResearchOrchestrationScenario;
import com.huawei.ascend.examples.deepresearch.report.DeepResearchOutputContract;
import com.huawei.ascend.examples.deepresearch.report.DeepResearchReportOutput;
import org.junit.jupiter.api.Test;

class DeepResearchOrchestrationScenarioTest {

    @Test
    void mockPipelineProducesReportMeetingSection5Thresholds() {
        DeepResearchReportOutput output = DeepResearchOrchestrationScenario.runIntegrationScenario();
        String markdown = output.toMarkdownWithJsonTail();

        DeepResearchReportOutput parsed = DeepResearchOutputContract.parseReport(markdown);

        assertThat(parsed.comparisonTable()).hasSize(5);
        int filled = DeepResearchOutputContract.countFilledComparisonCells(
                parsed.comparisonTable(), DeepResearchOutputContract.CORE_DIMENSIONS);
        assertThat(filled).isGreaterThanOrEqualTo(16);
        assertThat(parsed.citations()).isNotEmpty();
        assertThat(parsed.confidencePerField()).isNotEmpty();
        assertThat(markdown).contains("contradict");
        assertThat(markdown).contains("spa_blocked");
    }
}
