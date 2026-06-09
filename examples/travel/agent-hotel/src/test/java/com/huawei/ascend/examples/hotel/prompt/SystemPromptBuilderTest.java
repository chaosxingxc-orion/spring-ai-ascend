/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.prompt;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptBuilderTest {

    @Test
    void todayPlaceholderIsReplaced() {
        String prompt = SystemPromptBuilder.build(LocalDate.of(2026, 6, 20));
        assertThat(prompt).contains("【今天】2026-06-20").doesNotContain("{today}");
    }

    @Test
    void promptIncludesKeyContractClauses() {
        String prompt = SystemPromptBuilder.build();
        // Contract clauses the agent depends on — fail fast if someone trims the prompt.
        assertThat(prompt)
                .contains("hotel_search")
                .contains("hotel_detail")
                .contains("maxPricePerNight")
                .contains("minStar")
                .contains("brandWhitelist")
                .contains("markdown")
                .contains("[不符合差标]");
    }

    @Test
    void buildWithoutArgUsesShanghaiTimezone() {
        // Sanity: building today should not throw and should yield a non-empty prompt.
        assertThat(SystemPromptBuilder.build()).isNotBlank();
        assertThat(SystemPromptBuilder.TIMEZONE.getId()).isEqualTo("Asia/Shanghai");
    }
}
