package com.bank.financial.research.agent;

import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compliance / supervisory gate — the deterministic analog of a FINRA Rule 2241
 * Supervisory Analyst pass. It assembles the mandatory disclosures (analyst
 * certification, rating definitions, balanced-risk reminder, source attribution),
 * surfaces every data gap and staleness warning transparently, and stamps the
 * hard requirement that an AI-drafted report be reviewed and signed off by a
 * licensed analyst before publication. No figure is invented here; it only
 * attests to and discloses what the other agents produced.
 */
public final class ComplianceAgent implements ReportSubAgent {

    @Override
    public String role() {
        return "compliance";
    }

    @Override
    public String capability() {
        return "compliance";
    }

    @Override
    public void contribute(ReportContext ctx) {
        List<String> notes = notes(ctx);
        ctx.put(role(), "compliance.noteCount", Integer.toString(notes.size()));
    }

    /** Build the disclosure / certification notes for the report's back matter. */
    public List<String> notes(ReportContext ctx) {
        List<String> notes = new ArrayList<>();
        CompanyData.Dataset ds = ctx.dataset();

        notes.add("分析师认证:本报告所述观点系基于所披露的数据与方法独立形成,薪酬不与具体推荐意见直接挂钩。");
        notes.add("评级定义:增持=预期12个月相对收益≥15%;中性=-10%~15%;减持=≤-10%。评级为相对评级。");

        // Source attribution (FINRA-style transparency).
        Set<String> sources = new LinkedHashSet<>();
        if (ds.hasFundamentals()) {
            sources.add(ds.fundamentals().provenance().cite());
        }
        if (ds.hasConsensus()) {
            sources.add(ds.consensus().provenance().cite());
        }
        if (ds.hasMarket()) {
            sources.add(ds.market().provenance().cite());
        }
        if (ds.hasPeers()) {
            sources.add(ds.peers().provenance().cite());
        }
        if (!sources.isEmpty()) {
            notes.add("数据来源:" + String.join("、", sources) + "。");
        }

        // Transparent coverage gaps + staleness.
        List<String> gaps = new ArrayList<>(dataGaps(ctx));
        if (!gaps.isEmpty()) {
            notes.add("数据覆盖提示:" + String.join(";", gaps) + "。");
        }

        notes.add("风险提示:本报告包含前瞻性判断,实际结果可能因市场、经营与外部环境变化而与预测存在重大差异;"
                + "正反两面情形详见情景与风险章节。");
        notes.add("发布约束:本报告由 AI 多智能体引擎起草,须经持牌监督分析师(SA)复核并签发后方可对外发布;"
                + "实证研究显示自动研报在前瞻性分析与风险深度上仍逊于资深分析师,本引擎定位为分析师增强工具,而非自主发布者。");
        return notes;
    }

    /** Unavailable tiers + freshness warnings, for the metadata and disclosures. */
    public List<String> dataGaps(ReportContext ctx) {
        List<String> gaps = new ArrayList<>();
        CompanyData.Dataset ds = ctx.dataset();
        if (!ds.hasFundamentals()) {
            gaps.add("缺少基本面(财报)数据");
        }
        if (!ds.hasConsensus()) {
            gaps.add("缺少一致预期数据");
        }
        if (!ds.hasMarket()) {
            gaps.add("缺少实时行情");
        }
        if (!ds.hasPeers()) {
            gaps.add("缺少可比公司集");
        }
        gaps.addAll(ds.freshnessWarnings());
        return gaps;
    }
}
