package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeneratorAcceptanceCheckerTest {

    @Test
    void rejectsShortDraftMissingRequiredPhrases() {
        String criteria = "正文 10–200 个汉字；须含「生成-校验」与「无团长」";
        var result = GeneratorAcceptanceChecker.check(
                "写 150 字简介",
                "太短了，已生成文件 intro.md",
                criteria);
        assertThat(result.passed()).isFalse();
        assertThat(result.feedback()).contains("汉字不足");
    }

    @Test
    void dedupesRepeatedParagraphBeforeCheck() {
        String dup =
                "生成-校验闭环让撰写与质控轮流修订，无团长模式下成员对等协作。"
                        + "团队在迭代上限内自动驳回并返工，直至字数与要点全部达标，从而提升交付一致性与可预期性。"
                        + "用户描述需求后即可获得经质控背书的一段简介。"
                        + "生成-校验闭环让撰写与质控轮流修订，无团长模式下成员对等协作。"
                        + "团队在迭代上限内自动驳回并返工，直至字数与要点全部达标，从而提升交付一致性与可预期性。"
                        + "用户描述需求后即可获得经质控背书的一段简介。";
        String criteria = "正文 10–200 个汉字；须含「生成-校验」与「无团长」";
        var raw = GeneratorAcceptanceChecker.check("写 150 字简介", dup, criteria);
        assertThat(raw.passed()).isFalse();
        String deduped = GeneratorAcceptanceChecker.dedupeRepeatedBody(dup);
        var after = GeneratorAcceptanceChecker.check("写 150 字简介", deduped, criteria);
        assertThat(after.passed()).isTrue();
    }
}
