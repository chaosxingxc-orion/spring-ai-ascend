package com.huawei.ascend.examples.workmate.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionPromptNormalizerTest {

    @Test
    void normalizeCollapsesWhitespace() {
        assertThat(QuestionPromptNormalizer.normalize("  hello \n  world  ")).isEqualTo("hello world");
    }

    @Test
    void messageIdIsStableForSamePrompt() {
        String first = QuestionPromptNormalizer.messageId("确认执行模式");
        String second = QuestionPromptNormalizer.messageId("  确认执行模式 \n");
        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("question-");
    }

    @Test
    void semanticKeyGroupsResearchParamPromptVariants() {
        List<String> modeOptions = List.of(
                "完整模式（含审稿修订，默认）",
                "快速模式（跳过审稿，3章精简版）",
                "单章模式（只研究某一子课题）");
        String modeA = QuestionPromptNormalizer.semanticKey("在开始深度研究之前，请确认以下研究参数：", modeOptions);
        String modeB = QuestionPromptNormalizer.semanticKey("请确认执行模式", modeOptions);
        assertThat(modeA).isEqualTo("hitl:research-mode");
        assertThat(modeB).isEqualTo("hitl:research-mode");

        List<String> detailOptions = List.of(
                "报告语言：中文",
                "时效窗口：近1年（AI领域变化快，推荐）",
                "引用格式：APA（默认）",
                "输出格式：Markdown（默认）");
        String detailA = QuestionPromptNormalizer.semanticKey("请确认以下研究参数：", detailOptions);
        List<String> detailOptionsExtended = List.of(
                "报告语言：中文",
                "时效窗口：近1年（AI领域变化快，推荐）",
                "时效窗口：近2年",
                "时效窗口：近5年（含更早期基础研究）",
                "引用格式：APA（默认）",
                "输出格式：Markdown（默认）");
        String detailB = QuestionPromptNormalizer.semanticKey("请确认以下研究参数：", detailOptionsExtended);
        assertThat(detailA).isEqualTo("hitl:research-params");
        assertThat(detailB).isEqualTo("hitl:research-params");
    }
}
