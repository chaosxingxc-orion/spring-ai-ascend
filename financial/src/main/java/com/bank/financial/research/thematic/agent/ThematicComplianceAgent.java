package com.bank.financial.research.thematic.agent;

import com.bank.financial.research.data.ThematicData;
import com.bank.financial.research.thematic.ThematicContext;
import com.bank.financial.research.thematic.ThematicSubAgent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compliance / supervisory gate for the sector-strategy note — the deterministic
 * analog of a FINRA Rule 2241 review: mandatory disclosures, rating definitions,
 * source attribution from factor provenance, transparent coverage gaps, and the
 * hard requirement that an AI-drafted report be signed off by a licensed analyst.
 */
public final class ThematicComplianceAgent implements ThematicSubAgent {

    @Override
    public String role() {
        return "compliance";
    }

    @Override
    public String capability() {
        return "compliance";
    }

    @Override
    public void contribute(ThematicContext ctx) {
        ctx.put(role(), "compliance.noteCount", Integer.toString(notes(ctx).size()));
    }

    public List<String> notes(ThematicContext ctx) {
        List<String> notes = new ArrayList<>();
        notes.add("分析师认证:本报告所述观点系基于所披露的数据与方法独立形成,薪酬不与具体推荐意见直接挂钩。");
        notes.add("评级定义:超配=预期未来12个月相对基准超额收益显著为正;标配=与基准相当;低配=显著为负。评级为相对评级。");

        Set<String> sources = new LinkedHashSet<>();
        for (ThematicData.MacroFactor f : ctx.dataset().factors()) {
            sources.add(f.provenance().cite());
        }
        if (!sources.isEmpty()) {
            notes.add("数据来源:" + String.join("、", sources) + "。所列宏观因子为情景设定,非实时行情数据。");
        }
        if (!ctx.dataset().freshnessWarnings().isEmpty()) {
            notes.add("数据覆盖提示:" + String.join(";", ctx.dataset().freshnessWarnings()) + "。");
        }
        notes.add("风险提示:本报告包含前瞻性判断与情景假设,实际结果可能因市场、政策与外部环境变化而与预测存在重大差异。");
        notes.add("发布约束:本报告由 AI 多智能体引擎起草,须经持牌监督分析师(SA)复核并签发后方可对外发布;"
                + "板块评级由确定性传导打分模型计算得出,文字部分为模型生成,定位为分析师增强工具,非自主发布者。");
        return notes;
    }

    public List<String> dataGaps(ThematicContext ctx) {
        return new ArrayList<>(ctx.dataset().freshnessWarnings());
    }
}
