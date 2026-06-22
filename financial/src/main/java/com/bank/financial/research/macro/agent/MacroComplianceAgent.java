package com.bank.financial.research.macro.agent;

import com.bank.financial.research.data.MacroData;
import com.bank.financial.research.macro.MacroContext;
import com.bank.financial.research.macro.MacroSubAgent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compliance / supervisory gate for the macro note: mandatory disclosures, the
 * asset-tilt definition, source attribution from indicator provenance, transparent
 * coverage gaps, and the requirement that an AI-drafted report be signed off by a
 * licensed analyst.
 */
public final class MacroComplianceAgent implements MacroSubAgent {

    @Override
    public String role() {
        return "compliance";
    }

    @Override
    public String capability() {
        return "compliance";
    }

    @Override
    public void contribute(MacroContext ctx) {
        ctx.put(role(), "compliance.noteCount", Integer.toString(notes(ctx).size()));
    }

    public List<String> notes(MacroContext ctx) {
        List<String> notes = new ArrayList<>();
        notes.add("分析师认证:本报告所述宏观观点系基于所披露的数据与方法独立形成。");
        notes.add("倾向定义:权益占优/债券占优/中性为相对资产配置倾向,非具体买卖或申赎建议。");

        Set<String> sources = new LinkedHashSet<>();
        for (MacroData.Indicator i : ctx.dataset().indicators()) {
            sources.add(i.provenance().cite());
        }
        if (!sources.isEmpty()) {
            notes.add("数据来源:" + String.join("、", sources) + "。指标读数以官方发布为准。");
        }
        if (!ctx.dataset().freshnessWarnings().isEmpty()) {
            notes.add("数据覆盖提示:" + String.join(";", ctx.dataset().freshnessWarnings()) + "。");
        }
        notes.add("风险提示:宏观数据存在修订,政策与外部环境(含海外货币政策、地缘)变化可能令实际走势与判断出现重大差异。");
        notes.add("发布约束:本报告由 AI 多智能体引擎起草,须经持牌监督分析师(SA)复核签发后方可对外发布;"
                + "宏观打分与资产配置倾向由确定性模型计算得出,文字部分为模型生成,定位为分析师增强工具。");
        return notes;
    }

    public List<String> dataGaps(MacroContext ctx) {
        return new ArrayList<>(ctx.dataset().freshnessWarnings());
    }
}
