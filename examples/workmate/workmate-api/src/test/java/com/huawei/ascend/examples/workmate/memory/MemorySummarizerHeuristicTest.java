package com.huawei.ascend.examples.workmate.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MemorySummarizerHeuristicTest {

    @Test
    void extractsPreferenceLikeLinesWithoutLlm() {
        String transcript = """
                我是基金经理，偏好简洁中文回复
                今天天气不错
                """;

        List<String> entries = MemorySummarizer.heuristicExtract(transcript, "");

        assertThat(entries).anyMatch(line -> line.contains("基金经理"));
    }

    @Test
    void skipsDuplicateAgainstExistingMemory() {
        List<String> entries =
                MemorySummarizer.heuristicExtract("偏好简洁回复", "偏好简洁回复");

        assertThat(entries).isEmpty();
    }
}
