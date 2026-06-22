package com.bank.financial.research.composite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.model.ScriptedReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Integration layer: the composition of subject + lenses into one report, offline. */
class CompositeReportEngineTest {

    private static final long AS_OF = 1_750_000_000_000L;

    private CompositeReportEngine engine() {
        return new CompositeReportEngine(new ScriptedReportModel(), null, MemoryObserver.NOOP, () -> AS_OF, AS_OF, false);
    }

    @Test
    void composesSubjectPlusLensesIntoOneReport() {
        CompositeReport r = engine().generate("fund", "DEMOFUND", Set.of("macro", "global"));
        List<String> keys = r.modules().stream().map(CompositeReport.Module::key).toList();
        assertTrue(keys.contains("fund"), keys.toString());
        assertTrue(keys.contains("macro"), keys.toString());
        assertTrue(keys.contains("global"), keys.toString());
        String md = r.toMarkdown();
        assertTrue(md.contains("基金分析"));
        assertTrue(md.contains("宏观与政策"));
        assertTrue(md.contains("全球影响"));
        assertFalse(r.complianceNotes().isEmpty());
    }

    @Test
    void industryAndSectorCollapseToOneSectorModule() {
        CompositeReport r = engine().generate("none", "", Set.of("industry", "sector"));
        long sectorModules = r.modules().stream().filter(m -> m.key().equals("sector")).count();
        assertEquals(1, sectorModules, "industry+sector → one 行业与板块策略 module");
    }

    @Test
    void modulesForReflectsSelection() {
        assertEquals(0, CompositeReportEngine.modulesFor("none", Set.of()).size());
        assertEquals(1, CompositeReportEngine.modulesFor("fund", Set.of()).size());
        assertEquals(3, CompositeReportEngine.modulesFor("bond", Set.of("macro", "global")).size());
    }

    @Test
    void noneSubjectWithMacroProducesMacroOnly() {
        CompositeReport r = engine().generate("none", "", Set.of("macro"));
        assertEquals(1, r.modules().size());
        assertEquals("macro", r.modules().get(0).key());
        assertFalse(r.toMarkdown().isBlank());
    }
}
