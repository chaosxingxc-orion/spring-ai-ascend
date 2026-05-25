package com.huawei.ascend.service.agent.spi;

import com.huawei.ascend.middleware.memory.spi.MemoryCategory;
import com.huawei.ascend.middleware.memory.spi.MemoryRef;
import com.huawei.ascend.engine.planner.spi.PlannerRef;
import com.huawei.ascend.middleware.skill.spi.SkillRef;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Declarative shape for an {@link Agent} — customers attach via
 * {@code @Bean} + {@code @ConfigurationProperties} per Rule R-A.
 *
 * <p>Authority: ADR-0128. Mirrored at the wire boundary by
 * {@code docs/contracts/agent-definition.v1.yaml}.
 *
 * @param agentId         stable per-tenant identity.
 * @param tenantId        owning tenant (Rule R-C.c).
 * @param displayName     human-readable label.
 * @param description     one-line summary.
 * @param modelBinding    reference to a {@code ModelGateway} by id.
 * @param toolBindings    bound {@code Skill}s (any {@code SkillKind}).
 * @param memoryBindings  per-category memory store bindings.
 * @param plannerBinding  optional {@code Planner} reference.
 * @param systemPrompt    agent-level system prompt; never null.
 * @param safetyPolicy    safety policy (ADR-0051).
 * @param metadata        free-form metadata.
 */
public record AgentDefinition(
        String agentId,
        String tenantId,
        String displayName,
        String description,
        ModelRef modelBinding,
        Set<SkillRef> toolBindings,
        Map<MemoryCategory, MemoryRef> memoryBindings,
        Optional<PlannerRef> plannerBinding,
        String systemPrompt,
        SafetyPolicy safetyPolicy,
        Map<String, Object> metadata) {

    public AgentDefinition {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(modelBinding, "modelBinding");
        Objects.requireNonNull(toolBindings, "toolBindings");
        Objects.requireNonNull(memoryBindings, "memoryBindings");
        Objects.requireNonNull(plannerBinding, "plannerBinding");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(safetyPolicy, "safetyPolicy");
        Objects.requireNonNull(metadata, "metadata");
        toolBindings = Set.copyOf(toolBindings);
        memoryBindings = Map.copyOf(memoryBindings);
        metadata = Map.copyOf(metadata);
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must be non-blank");
        }
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank (Rule R-C.c)");
        }
    }
}
