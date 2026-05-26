package com.huawei.ascend.service.integration.springai;

import com.huawei.ascend.middleware.skill.spi.Skill;
import com.huawei.ascend.middleware.skill.spi.SkillInvocation;
import com.huawei.ascend.middleware.skill.spi.SkillKind;
import com.huawei.ascend.middleware.skill.spi.SkillResult;
import com.huawei.ascend.middleware.skill.spi.SkillSuspensionState;

import java.util.Objects;

/**
 * Reference {@link Skill} that adapts a Spring AI tool callback
 * (the customer-supplied tool function the LLM may call).
 *
 * <p>Authority: ADR-0122 (Tool-Skill semantic resolution: Path b)
 * + ADR-0127 (Skill SPI unified) + ADR-0125 (Spring AI canonical
 * boundary). Design-only shell.
 *
 * <p>The wrapped Spring AI tool type is held as {@link Object}
 * with the FQN documented in Javadoc — Spring AI's
 * {@code ToolCallback} package location varies across milestone
 * releases (2.0.0-M5 candidate paths include
 * {@code org.springframework.ai.tool.ToolCallback} and
 * {@code org.springframework.ai.chat.model.ToolCallback}). The
 * implementation that binds tool dispatch will lock the import to the
 * shipped 2.0 GA location.
 *
 * <p>{@link SkillKind#TOOL} discriminator (ADR-0122). Lifecycle
 * methods {@code init} / {@code teardown} are no-ops; {@code suspend}
 * throws {@link UnsupportedOperationException} per the SPI default.
 */
public final class SpringAiToolCallbackSkillAdapter implements Skill {

    private final Object springAiToolCallback;
    private final String skillKey;

    public SpringAiToolCallbackSkillAdapter(Object springAiToolCallback, String skillKey) {
        this.springAiToolCallback = Objects.requireNonNull(
                springAiToolCallback, "springAiToolCallback");
        this.skillKey = Objects.requireNonNull(skillKey, "skillKey");
        if (skillKey.isBlank()) {
            throw new IllegalArgumentException("skillKey must be non-blank");
        }
    }

    @Override
    public String skillKey() {
        return skillKey;
    }

    @Override
    public SkillKind kind() {
        return SkillKind.TOOL;
    }

    @Override
    public SkillResult execute(SkillInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        throw new UnsupportedOperationException(
                "SpringAiToolCallbackSkillAdapter: design-only shell at L0; "
                        + "tool registry implementation wires hook dispatch + ToolCallback.call(...)");
    }

    @Override
    public void suspend(SkillSuspensionState state) {
        throw new UnsupportedOperationException(
                "SkillKind.TOOL skills do not support suspension (ADR-0127)");
    }

    /** Exposes the underlying Spring AI tool callback. */
    public Object underlyingSpringAiToolCallback() {
        return springAiToolCallback;
    }
}
