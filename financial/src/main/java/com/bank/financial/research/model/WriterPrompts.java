package com.bank.financial.research.model;

/**
 * Shared section-writing instructions for the single-writer agents (equity, fund,
 * bond). One institutional voice and one discipline across every desk: lead with
 * the conclusion, develop a real argument chain rather than a bullet dump, weigh
 * both sides, link back to the house rating, and never introduce a number that is
 * not already in the brief. Centralising the prompt keeps the prose quality and
 * the no-fabrication contract identical across report types; the consistency
 * checker still verifies the output against the blackboard's canonical figures.
 */
public final class WriterPrompts {

    private WriterPrompts() {
    }

    /**
     * Build the writer instruction for one section.
     *
     * @param title  the section title (e.g. "估值")
     * @param words  soft length target in Chinese characters
     * @param anchor what this section must link back to (e.g. "总体投资评级与目标价")
     */
    public static String section(String title, int words, String anchor) {
        return "撰写「" + title + "」章节,约 " + words + " 字,采用机构卖方研究的中文专业口径,分 2-4 个自然段。要求:"
                + "(1) 结论先行——首句给出本节的核心判断;"
                + "(2) 展开完整逻辑链(论据→推理→结论),不要罗列要点,把简报中的事实编织成连贯论证;"
                + "(3) 给出正反两面与关键风险/不确定性,保持论述平衡、可证伪;"
                + "(4) 显式回链至" + anchor + ",说明本节如何支持或修正总体判断;"
                + "(5) 数字纪律——只能引用简报中已给出的规范数字,绝不新增、推算或润饰任何数值;"
                + "定性判断可以充分展开,但不得为其编造量化依据;"
                + "(6) 直接输出正文,不要重复章节标题、不要加 Markdown 标题符号。";
    }
}
