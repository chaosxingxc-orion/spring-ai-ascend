package com.huawei.ascend.middleware.memory.spi;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped windowed conversation memory: a {@link MemoryStore}
 * variant whose key is a conversation id and whose value is a
 * {@link ConversationWindow}, with explicit token-budget pruning and
 * summarisation hooks.
 *
 * <p>Authority: ADR-0133 (extends ADR-0123 unified Memory SPI).
 *
 * <p>Category defaults to {@link MemoryCategory#M2_EPISODIC}.
 *
 * <p>Implementations MUST:
 * <ul>
 *   <li>preserve insertion order across {@link #addTurns};</li>
 *   <li>return at most {@code maxTokenBudget} worth of turns from
 *       {@link #getTurnsUpToBudget}, dropping OLDEST first (FIFO);</li>
 *   <li>treat {@link #summariseAndCompact} as a no-op when the
 *       conversation has fewer than {@code keepLastN} turns;</li>
 *   <li>fail closed on blank {@code tenantId} or {@code conversationId}.</li>
 * </ul>
 *
 * <p>Token counting: implementations choose the tokenizer (provider
 * native, GPT-2, Llama, ...); the platform does not standardise the
 * counter at L0. Documented in the implementation, not the SPI.
 *
 * <p>SPI purity per Rule R-D.
 */
public interface ConversationMemory extends MemoryStore<String, ConversationWindow> {

    /** Default category for ConversationMemory implementations. */
    @Override
    default MemoryCategory category() {
        return MemoryCategory.M2_EPISODIC;
    }

    /**
     * Append turns to the conversation.
     *
     * @param tenantId        owning tenant (Rule R-C.c); non-blank.
     * @param conversationId  conversation identifier; non-blank.
     * @param turns           ordered list of turns to append; never
     *                        null, may be empty (no-op).
     */
    void addTurns(String tenantId, String conversationId, List<ConversationTurn> turns);

    /**
     * Return the suffix of the conversation that fits within
     * {@code maxTokenBudget} tokens, computed by the implementation's
     * tokenizer. Oldest turns are dropped first.
     *
     * @param tenantId        owning tenant; non-blank.
     * @param conversationId  conversation identifier; non-blank.
     * @param maxTokenBudget  upper bound on the returned message
     *                        token sum; MUST be positive.
     * @return ordered list of turns whose total token count is
     *         &le; {@code maxTokenBudget}; never null, may be empty.
     */
    List<ConversationTurn> getTurnsUpToBudget(String tenantId, String conversationId, int maxTokenBudget);

    /**
     * Replace the OLDEST {@code totalTurns - keepLastN} turns of the
     * conversation with a single synthetic assistant summary turn
     * produced by the implementation. Summary generation details are
     * opaque to the SPI surface.
     *
     * @param tenantId        owning tenant; non-blank.
     * @param conversationId  conversation identifier; non-blank.
     * @param keepLastN       number of trailing turns to preserve
     *                        verbatim; MUST be non-negative.
     * @return the summary turn inserted at the head of the kept
     *         window, or {@link Optional#empty()} when no compaction
     *         happened (conversation had &le; keepLastN turns).
     */
    Optional<ConversationTurn> summariseAndCompact(String tenantId, String conversationId, int keepLastN);
}
