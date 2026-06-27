package com.huawei.ascend.examples.workmate.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlanExtractServiceTest {

    private final PlanExtractService service = new PlanExtractService();

    @Test
    void extractsNumberedList() {
        String text = """
                我将按以下步骤执行：
                1. 下载腾讯2025中期报告PDF
                2. 从PDF提取财务数据
                3. 创建Excel财务模型
                """;
        var plan = service.extract(text).orElseThrow();
        assertThat(plan.steps()).hasSize(3);
        assertThat(plan.steps().get(0).title()).contains("下载");
    }

    @Test
    void extractsBulletList() {
        String text = """
                - 下载报告
                - 提取数据
                - 生成 Word
                """;
        var plan = service.extract(text).orElseThrow();
        assertThat(plan.steps()).hasSize(3);
    }

    @Test
    void skipsSingleLine() {
        assertThat(service.extract("只有一步：下载报告")).isEmpty();
    }
}
