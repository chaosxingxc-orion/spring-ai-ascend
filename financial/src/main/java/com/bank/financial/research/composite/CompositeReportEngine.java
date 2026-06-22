package com.bank.financial.research.composite;

import com.bank.financial.research.bond.BondReport;
import com.bank.financial.research.bond.BondReportEngine;
import com.bank.financial.research.data.MacroData;
import com.bank.financial.research.data.eastmoney.EastMoneyFundDataSource;
import com.bank.financial.research.data.eastmoney.EastMoneyMacroDataSource;
import com.bank.financial.research.data.stub.StubBondDataSource;
import com.bank.financial.research.data.stub.StubFundDataSource;
import com.bank.financial.research.data.stub.StubMacroDataSource;
import com.bank.financial.research.data.stub.StubThematicDataSource;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.PipelineProgress;
import com.bank.financial.research.engine.ReportBudget;
import com.bank.financial.research.engine.ReportMetadata;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.ReportSection;
import com.bank.financial.research.fund.FundReport;
import com.bank.financial.research.fund.FundReportEngine;
import com.bank.financial.research.macro.MacroReport;
import com.bank.financial.research.macro.MacroReportEngine;
import com.bank.financial.research.model.ReportModel;
import com.bank.financial.research.thematic.ThematicReport;
import com.bank.financial.research.thematic.ThematicReportEngine;
import com.huawei.ascend.a2a.memory.experience.CollaborationSignature;
import com.huawei.ascend.a2a.memory.experience.ExperienceMemoryKit;
import com.huawei.ascend.a2a.memory.experience.ExperienceStore;
import com.huawei.ascend.a2a.memory.experience.InMemoryExperienceStore;
import com.huawei.ascend.a2a.memory.experience.Lesson;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Composes a report as a sequence of STAGES, presented top-down: one subject
 * (fund / bond) as the base — its own sub-agents, blackboard, cross-run memory and
 * a base research report — followed by each selected analysis lens (macro /
 * industry-sector / global) which adds a supplementary section + an explicit
 * "影响点评" on the base view (it does not rewrite the base report). A {@link StageSink}
 * receives the staged events so the UI can render each stage in order; the merged
 * {@link CompositeReport} is also returned.
 */
public final class CompositeReportEngine {

    private final ReportModel model;
    private final ExperienceStore experienceStore;
    private final MemoryObserver observer;
    private final LongSupplier clock;
    private final long asOf;
    private final boolean real;
    private final ReportBudget budget = new ReportBudget(1, 8000, 30, 4 * 60 * 1000L);

    public CompositeReportEngine(ReportModel model, ExperienceStore experienceStore,
            MemoryObserver observer, LongSupplier clock, long asOf, boolean real) {
        this.model = model;
        this.experienceStore = experienceStore == null ? new InMemoryExperienceStore() : experienceStore;
        this.observer = observer == null ? MemoryObserver.NOOP : observer;
        this.clock = clock == null ? System::currentTimeMillis : clock;
        this.asOf = asOf;
        this.real = real;
    }

    /** Receives the staged presentation events (UI renders each stage in order). */
    public interface StageSink {
        /** Begin a stage with its sub-agent roster (roles already prefixed by stage key). */
        void stage(String key, String title, List<Map<String, String>> agents);

        /** A sub-agent transition within the current stage (role is stage-prefixed). */
        void agent(String role, String state, int index, int total);

        /** Blackboard keys this sub-agent wrote (role is stage-prefixed). */
        void agentDone(String role, Map<String, String> wrote);

        /** Interaction edges for the current stage. */
        void interactions(List<Map<String, String>> edges);

        /** Cross-run experience lessons (sent once, after the base subject stage). */
        void experience(List<Map<String, Object>> lessons);

        /** A finished report block (markdown) for the current stage. */
        void report(String title, String markdown);

        StageSink NOOP = new StageSink() {
            public void stage(String k, String t, List<Map<String, String>> a) { }
            public void agent(String r, String s, int i, int t) { }
            public void agentDone(String r, Map<String, String> w) { }
            public void interactions(List<Map<String, String>> e) { }
            public void experience(List<Map<String, Object>> l) { }
            public void report(String t, String m) { }
        };
    }

    /** Sub-agent roster (role → 中文标签) for a module, in pipeline order. */
    private static List<String[]> roster(String key) {
        return switch (key) {
            case "fund" -> List.of(a("planner", "规划"), a("data", "数据"), a("performance", "业绩"),
                    a("risk", "风险"), a("lead-manager", "首席"), a("writer", "撰写"),
                    a("critic", "评审"), a("compliance", "合规"));
            case "bond" -> List.of(a("planner", "规划"), a("data", "数据"), a("rates", "利率"),
                    a("credit", "信用"), a("lead-manager", "首席"), a("writer", "撰写"),
                    a("critic", "评审"), a("compliance", "合规"));
            case "macro" -> List.of(a("planner", "规划"), a("data", "指标录入"), a("analysis", "量化打分"),
                    a("lead-manager", "策略首席"), a("writer", "撰写"), a("critic", "评审"), a("compliance", "合规"));
            case "sector" -> List.of(a("planner", "规划"), a("data", "宏观录入"), a("sector-impact", "因子打分"),
                    a("lead-manager", "策略首席"), a("writer", "撰写"), a("critic", "评审"), a("compliance", "合规"));
            case "global" -> List.<String[]>of(a("writer", "全球分析"));
            default -> List.<String[]>of();
        };
    }

    private static String[] a(String role, String label) {
        return new String[] {role, label};
    }

    /** The ordered modules a selection produces (subject first, then lenses). */
    public static List<Map<String, String>> modulesFor(String subject, Set<String> lenses) {
        List<Map<String, String>> mods = new ArrayList<>();
        if ("fund".equals(subject)) {
            mods.add(Map.of("role", "fund", "label", "基金分析"));
        } else if ("bond".equals(subject)) {
            mods.add(Map.of("role", "bond", "label", "债券分析"));
        }
        if (lenses.contains("macro")) {
            mods.add(Map.of("role", "macro", "label", "宏观与政策"));
        }
        if (lenses.contains("industry") || lenses.contains("sector")) {
            mods.add(Map.of("role", "sector", "label", "行业与板块策略"));
        }
        if (lenses.contains("global")) {
            mods.add(Map.of("role", "global", "label", "全球影响(定性)"));
        }
        return mods;
    }

    public CompositeReport generate(String subject, String code, Set<String> lenses) {
        return generate(subject, code, lenses, StageSink.NOOP);
    }

    public CompositeReport generate(String subject, String code, Set<String> lenses, StageSink sink) {
        if (sink == null) {
            sink = StageSink.NOOP;
        }
        List<Map<String, String>> roster = modulesFor(subject, lenses);
        List<CompositeReport.Module> modules = new ArrayList<>();
        Set<String> notes = new LinkedHashSet<>();
        List<String> degradations = new ArrayList<>();
        int modelCalls = 0;
        boolean subjectDone = false;

        for (Map<String, String> mod : roster) {
            String key = mod.get("role");
            String label = mod.get("label");
            boolean isSubject = key.equals("fund") || key.equals("bond");
            sink.stage(key, label, prefixedRoster(key));
            PipelineProgress sp = stageProgress(sink, key);
            try {
                List<ReportSection> sections;
                ReportMetadata md;
                String impact = "";
                switch (key) {
                    case "fund" -> {
                        FundReport r = fund(code, sp);
                        sections = r.sections();
                        md = r.metadata();
                        modules.add(new CompositeReport.Module("fund", label, sections));
                    }
                    case "bond" -> {
                        BondReport r = new BondReportEngine(new StubBondDataSource(asOf), model,
                                experienceStore, observer, clock)
                                .generate(req(blank(code, "DEMOBOND"), "BOND"), sp);
                        sections = r.sections();
                        md = r.metadata();
                        modules.add(new CompositeReport.Module("bond", label, sections));
                    }
                    case "macro" -> {
                        MacroReport r = new MacroReportEngine(
                                real ? new EastMoneyMacroDataSource(asOf) : new StubMacroDataSource(asOf),
                                model, experienceStore, observer, clock, Set.<MacroData.Domain>of())
                                .generate(req("中国", "MACRO"), sp);
                        sections = r.sections();
                        md = r.metadata();
                        impact = "【影响点评】宏观环境综合分 " + Bb.fmt(r.composite()) + "、资产配置倾向 " + r.assetTilt()
                                + ";据此对上述基础研报中的标的,建议结合该宏观取向调整仓位与久期。\n\n";
                        modules.add(new CompositeReport.Module("macro", label, sections));
                    }
                    case "sector" -> {
                        String theme = isSubjectName(subject) ? "中国 TMT" : blank(code, "中国 TMT");
                        ThematicReport r = new ThematicReportEngine(new StubThematicDataSource(asOf), model,
                                experienceStore, observer, clock)
                                .generate(req(theme, "INDUSTRY"), sp);
                        sections = r.sections();
                        md = r.metadata();
                        impact = "【影响点评】板块总体评级 " + r.overallRating() + "(综合影响分 " + Bb.fmt(r.overallScore())
                                + ");用于校准基础研报中标的的行业景气与配置权重。\n\n";
                        modules.add(new CompositeReport.Module("sector", label, sections));
                    }
                    case "global" -> {
                        sp.onAgent("writer", "running", 1, 1);
                        GlobalResult g = global();
                        sp.onAgent("writer", "done", 1, 1);
                        sections = List.of(new ReportSection("global", "全球影响(定性)", g.body(), 0));
                        md = null;
                        modelCalls += g.calls();
                        if (g.degraded() != null) {
                            degradations.add(g.degraded());
                        }
                        impact = "【影响点评】全球/海外因素为定性判断(未接实时源),用于提示基础研报的外部风险与情景。\n\n";
                        modules.add(new CompositeReport.Module("global", label, sections));
                    }
                    default -> {
                        sections = List.of();
                        md = null;
                    }
                }
                if (md != null) {
                    notes.addAll(md.complianceNotes());
                    modelCalls += md.modelCalls();
                    degradations.addAll(md.degradations());
                }
                // Emit this stage's report block: base report for the subject, a labelled
                // supplement (+ 影响点评) for each lens.
                if (isSubject) {
                    sink.report("基础投研报告 · " + subjectTitle(subject, code), sectionsMd(sections));
                    sink.experience(recall(key));
                    subjectDone = true;
                } else {
                    String t = subjectDone ? label + "(对基础研报的补充与影响点评)" : label;
                    sink.report(t, impact + sectionsMd(sections));
                }
            } catch (RuntimeException e) {
                degradations.add(key + ": " + e.getMessage());
                sink.report(label, "(本模块生成失败,已跳过:" + e.getMessage() + ")");
            }
        }

        notes.add("组合说明:基金/债券为基础研报;勾选的分析视角在其下作补充与影响点评(不改写基础研报正文)。"
                + "各模块数字均由确定性模型计算;全球影响为定性,暂未接入海外实时源。须经持牌监督分析师(SA)复核签发。");

        String title = subjectTitle(subject, code) + " — 组合研究报告";
        String subtitle = "视角:" + roster.stream().map(m -> m.get("label")).reduce((x, y) -> x + " · " + y).orElse("(无)");
        ReportMetadata metadata = new ReportMetadata(
                model.name(), real ? "东方财富(免费真实)+ 情景库" : "离线桩 / 快照", modelCalls, 0, "COMPOSITE",
                List.of(), new ArrayList<>(notes), List.of(), degradations, clock.getAsLong());
        return new CompositeReport(title, subtitle, modules, new ArrayList<>(notes), metadata);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private List<Map<String, String>> prefixedRoster(String key) {
        List<Map<String, String>> out = new ArrayList<>();
        for (String[] ag : roster(key)) {
            out.add(Map.of("role", key + "/" + ag[0], "label", ag[1]));
        }
        return out;
    }

    private PipelineProgress stageProgress(StageSink sink, String key) {
        int total = roster(key).size();
        return new PipelineProgress() {
            @Override
            public void onAgent(String role, String state, int index, int total2) {
                sink.agent(key + "/" + role, state, index, total);
            }

            @Override
            public void onAgentDone(String role, Map<String, String> wrote) {
                sink.agentDone(key + "/" + role, wrote);
            }

            @Override
            public void onInteractions(List<Map<String, String>> edges) {
                sink.interactions(edges);
            }
        };
    }

    private ReportRequest req(String ticker, String type) {
        return new ReportRequest(ticker, type, "web", "zh-CN", asOf, budget);
    }

    private FundReport fund(String code, PipelineProgress sp) {
        String c = blank(code, "110020");
        if (real) {
            try {
                return new FundReportEngine(new EastMoneyFundDataSource(asOf), model, experienceStore, observer, clock)
                        .generate(req(c, "FUND"), sp);
            } catch (RuntimeException e) {
                // fall through to stub
            }
        }
        return new FundReportEngine(new StubFundDataSource(asOf), model, experienceStore, observer, clock)
                .generate(req(real ? "DEMOFUND" : c, "FUND"), sp);
    }

    private List<Map<String, Object>> recall(String key) {
        try {
            String taskType = "research-report:" + key.toUpperCase(java.util.Locale.ROOT);
            ExperienceMemoryKit kit = ExperienceMemoryKit.forTenant(experienceStore, "web");
            List<Lesson> lessons = kit.recall(new CollaborationSignature(Set.of(), taskType), 12);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Lesson l : lessons) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("text", l.text());
                m.put("reinforcement", l.reinforcement());
                out.add(m);
            }
            return out;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static String sectionsMd(List<ReportSection> sections) {
        StringBuilder sb = new StringBuilder();
        for (ReportSection s : sections.stream().sorted((a, b) -> Integer.compare(a.order(), b.order())).toList()) {
            sb.append("### ").append(s.title()).append("\n\n").append(s.body()).append("\n\n");
        }
        return sb.toString();
    }

    private record GlobalResult(String body, int calls, String degraded) {
    }

    private GlobalResult global() {
        String brief = "(本节为定性分析,暂未接入海外实时数据源)";
        String instruction = "就全球宏观与外部事件(美联储 FOMC、美国通胀/就业、地缘形势等)对中国资产及所选标的的"
                + "传导影响,做定性分析(约 400 字):分点说明方向与不确定性,明确标注为非实时数据的定性判断。";
        try {
            String body = model.generate(new ReportModel.ModelTask("writer", instruction, brief, 700));
            return new GlobalResult(body, 1, null);
        } catch (RuntimeException e) {
            return new GlobalResult("(全球影响章节生成失败,降级)\n" + brief, 0, "global: " + e.getMessage());
        }
    }

    private static boolean isSubjectName(String subject) {
        return "fund".equals(subject) || "bond".equals(subject);
    }

    private static String subjectTitle(String subject, String code) {
        return switch (subject) {
            case "fund" -> "基金 " + blank(code, "110020");
            case "bond" -> "债券 " + blank(code, "DEMOBOND");
            default -> "宏观与策略";
        };
    }

    private static String blank(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
