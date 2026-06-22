package com.bank.financial.research.data.stub;

import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.SourceType;
import com.bank.financial.research.data.ThematicData;
import com.bank.financial.research.data.ThematicData.FactorCategory;
import com.bank.financial.research.data.ThematicData.MacroFactor;
import com.bank.financial.research.data.ThematicData.SubSector;
import com.bank.financial.research.data.ThematicDataSource;
import java.util.List;
import java.util.Map;

/**
 * Deterministic offline thematic scenario: China TMT as of mid-2026, encoding the
 * four macro lines (US–Iran de-escalation, a hawkish-hold FOMC, China's easing
 * stance, and the US/China market backdrop) and their exposures across the TMT
 * sub-sectors. Signed magnitudes are positive when supportive of China risk
 * assets. Numbers are an illustrative scenario, not live data — a production
 * source would assemble factors from real feeds (and could ingest real-time
 * events). As-of is injectable for freshness behaviour.
 */
public final class StubThematicDataSource implements ThematicDataSource {

    private final long asOfEpochMs;

    public StubThematicDataSource(long asOfEpochMs) {
        this.asOfEpochMs = asOfEpochMs;
    }

    @Override
    public String name() {
        return "stub-thematic";
    }

    private Provenance p(SourceType t, String src) {
        return new Provenance(src, t, asOfEpochMs, "scenario-2026-06", 0.7);
    }

    @Override
    public ThematicData.Dataset load(String theme, long asOf) {
        String t = (theme == null || theme.isBlank()) ? "中国 TMT" : theme;

        List<MacroFactor> factors = List.of(
                new MacroFactor("oil_geo", "美伊缓和 / 油价风险溢价回落", FactorCategory.GEOPOLITICS,
                        0.7, "6/19 拟签停战备忘录,油价中枢下行,缓解输入性通胀、提升风险偏好",
                        p(SourceType.NEWS, "地缘资讯")),
                new MacroFactor("fomc_hawkish", "FOMC 鹰派按兵 / 降息预期收窄", FactorCategory.MONETARY,
                        -0.5, "联邦基金 3.50%-3.75% 维持,沃什偏鹰,压制港股/远期成长股估值",
                        p(SourceType.MACRO, "美联储")),
                new MacroFactor("cn_easing", "国内适度宽松 / 低利率", FactorCategory.MONETARY,
                        0.6, "7天逆回购 1.40%、10Y 国债 ~1.75%,流动性宽裕,利好长久期成长股",
                        p(SourceType.MACRO, "央行")),
                new MacroFactor("cn_fiscal_debt", "财政重心转向化债", FactorCategory.DOMESTIC_MACRO,
                        -0.2, "下半年部分资金转向化债,To-G 信息化支出周期可能延长",
                        p(SourceType.MACRO, "财政")),
                new MacroFactor("us_tech_map", "美股 AI / 芯片走强映射", FactorCategory.MARKET,
                        0.6, "美光/英伟达/西部数据等大涨,验证 AI 产业高景气,映射国内算力/半导体",
                        p(SourceType.MARKET, "美股行情")),
                new MacroFactor("ashare_pullback", "A股科技回调 / 拥挤度释放", FactorCategory.MARKET,
                        0.1, "5月底科创50/创业板回调挤泡沫,估值分化未极端,位置转健康",
                        p(SourceType.MARKET, "A股行情")),
                new MacroFactor("semi_ipo", "重磅半导体 IPO(长鑫等)", FactorCategory.MARKET,
                        0.3, "国产存储/芯片 IPO 活跃,带来比价效应与情绪溢价,但对存量资金有虹吸",
                        p(SourceType.MARKET, "一级市场")),
                new MacroFactor("space_ipo", "SpaceX 史上最大 IPO 估值锚", FactorCategory.MARKET,
                        0.5, "SpaceX 上市首日 +19%,为全球商业航天提供估值锚定",
                        p(SourceType.MARKET, "美股行情")));

        List<SubSector> subSectors = List.of(
                new SubSector("半导体/AI算力", Map.of(
                        "us_tech_map", 0.8, "cn_easing", 0.6, "semi_ipo", 0.6,
                        "fomc_hawkish", 0.3, "oil_geo", 0.2, "ashare_pullback", 0.2),
                        "国产替代 + 周期复苏 + 海外映射共振"),
                new SubSector("通信(CPO/光模块)", Map.of(
                        "us_tech_map", 0.8, "oil_geo", 0.3, "cn_easing", 0.3, "fomc_hawkish", 0.2),
                        "AI 算力网络核心连接件,业绩兑现度高,外需强"),
                new SubSector("商业航天", Map.of(
                        "space_ipo", 0.9, "cn_easing", 0.6, "fomc_hawkish", 0.2, "cn_fiscal_debt", 0.1),
                        "SpaceX 映射 + 十五五低轨卫星投入高峰 + 低利率适配长周期"),
                new SubSector("消费电子", Map.of(
                        "oil_geo", 0.1, "cn_easing", 0.15, "us_tech_map", 0.1, "fomc_hawkish", 0.15),
                        "K型消费偏弱,缺乏杀手级 AI 终端,估值修复为主"),
                new SubSector("互联网平台", Map.of(
                        "fomc_hawkish", 0.7, "cn_easing", 0.3, "us_tech_map", 0.2, "oil_geo", 0.2),
                        "港股恒科对外部流动性敏感,鹰派压制估值,防御属性为主"),
                new SubSector("软件/SaaS", Map.of(
                        "cn_fiscal_debt", 1.0, "cn_easing", 0.3, "fomc_hawkish", 0.2, "semi_ipo", -0.2),
                        "To-G 受化债拖累,需严选 B 端强现金流标的"));

        return new ThematicData.Dataset(t, asOfEpochMs, factors, subSectors, List.of());
    }
}
