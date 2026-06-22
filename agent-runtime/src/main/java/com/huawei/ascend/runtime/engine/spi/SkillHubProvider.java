package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.List;

/**
 * Runtime-neutral SkillHub SPI.
 *
 * <p>A SkillHub provides progressive skill loading: adapters can list compact
 * summaries first, then load the full definition only when the concrete agent
 * framework needs to install or expose that skill.
 */
public interface SkillHubProvider {

    /** Return compact skill summaries visible to the current execution. */
    List<SkillSummary> listSkills(AgentExecutionContext context);

    /** Load one full skill definition by id. */
    SkillDefinition loadSkill(AgentExecutionContext context, String skillId);

    /**
     * Load one packaged skill bundle by id.
     *
     * <p>This is optional because not every registry exposes archives. Providers
     * that support install/download flows should return a portable package such
     * as a zip containing {@code SKILL.md} plus reference files.
     */
    default SkillPackage loadSkillPackage(AgentExecutionContext context, String skillId) {
        throw new UnsupportedOperationException("skill package loading is not supported");
    }
}
