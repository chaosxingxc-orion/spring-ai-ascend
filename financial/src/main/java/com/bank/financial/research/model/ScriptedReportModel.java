package com.bank.financial.research.model;

/**
 * Deterministic, offline {@link ReportModel}. It produces role-appropriate
 * Chinese prose that <em>grounds itself in the brief verbatim</em> — every number
 * in the output is copied from the canonical brief the orchestrator supplies, so
 * the generated text can never drift from the computed figures. This is what lets
 * the whole multi-agent pipeline run end-to-end in tests and in {@code --mock}
 * with no API key and byte-identical results, while still exercising the real
 * consistency / convergence / compliance machinery.
 *
 * <p>The production {@link OpenJiuwenReportModel} swaps in a real LLM; the
 * consistency checker then earns its keep by catching any figure the model
 * invents that the blackboard does not contain.
 */
public final class ScriptedReportModel implements ReportModel {

    @Override
    public String name() {
        return "scripted";
    }

    @Override
    public String generate(ModelTask task) {
        String role = task.role() == null ? "" : task.role();
        String brief = task.brief() == null ? "" : task.brief();
        return switch (role) {
            case "planner" -> planner(brief);
            case "writer" -> writer(task.instruction(), brief);
            case "sector-macro" -> sectorMacro(brief);
            case "critic" -> critic(brief);
            case "lead-manager" -> manager(brief);
            default -> "[" + role + "] " + task.instruction() + "\n" + brief;
        };
    }

    private String planner(String brief) {
        return "本报告采用卖方研究的标准骨架,以投资论点为脊柱组织全文,确保评级、估计、估值与目标价"
                + "逐层勾稽。各章节字数与数据预算已在大纲阶段固定。\n" + brief;
    }

    private String writer(String instruction, String brief) {
        // Deterministic multi-paragraph section prose that weaves the canonical facts
        // (brief) in verbatim, so the offline report is substantial AND every number
        // traces to the blackboard. A live model produces the full long-form copy;
        // this keeps the offline demo coherent and the consistency check meaningful.
        return "结论先行:本节结论严格基于下列经计算或经核验的事实,不引入未经数据支撑的数字。\n\n"
                + brief + "\n"
                + "分析:上述指标构成本节的核心依据。我们以投资论点为脊柱,逐项核对其与盈利模型、"
                + "估值结论及行业景气的内在一致性;凡涉及金额、估值与增速的数字,均来自经计算的单一事实源"
                + "(黑板),不在撰写环节二次推算或润饰。\n\n"
                + "对照与交叉验证:我们将上述结论与行业可比公司、历史趋势以及一致预期进行三角对照,"
                + "识别其中相互印证与彼此背离之处;对背离项,我们在情景与风险章节给出反向情形与触发条件,"
                + "确保论述平衡、可证伪,而非单边叙事。\n\n"
                + "对论点的贡献:本节据此回链至总体投资论点与评级,既支持基准情形,也明确了若关键假设"
                + "(增速、利润率、贴现率、外部信息冲击)发生变化时结论可能的位移方向与幅度。";
    }

    private String sectorMacro(String brief) {
        return "行业与宏观:结合景气指标与外部资讯的方向性影响,我们对需求与利润率趋势给出如下判断。"
                + "外部冲击已通过驱动因子模型量化并写入黑板,供估值与撰写环节统一引用。\n" + brief;
    }

    private String critic(String brief) {
        return "评审意见(风格与一致性):全文采用统一的机构口径与术语;数字与黑板单一事实源一致;"
                + "论点均回链至投资论点。下列为需关注事项与已核验项。\n" + brief;
    }

    private String manager(String brief) {
        return "首席观点:在三角验证收敛的前提下,我们形成统一的房屋观点(house view),"
                + "并对分歧情形给出明确的取舍理由。\n" + brief;
    }
}
