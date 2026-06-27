package com.huawei.ascend.examples.workmate.team.backend;

/**
 * Pluggable execution backend for a single team member turn.
 *
 * <p>This is the heterogeneous-framework seam of WorkMate's application-layer team
 * orchestration (Plan C): the mailbox / coordinator / routing layer stays framework-agnostic,
 * while concrete backends adapt a member run to a specific runtime — the in-process DeepAgent
 * ({@code local}), an A2A remote member runtime ({@code a2a}), an external CLI, or a third-party
 * vendor framework. New backends can be added without touching the orchestration layer.</p>
 *
 * <p>Concurrency note: a backend's {@link #run(MemberRunContext)} may be synchronous (a single
 * blocking turn). Parallelism across members is provided by the member worker layer (one worker
 * per member), so even a synchronous backend participates in concurrent team execution.</p>
 */
public interface MemberBackend {

    /** Stable backend identifier, e.g. {@code local}, {@code a2a}, {@code external_cli}. */
    String kind();

    /** Lower runs first when several backends match; the local fallback should have the highest value. */
    default int priority() {
        return 500;
    }

    /** Whether this backend can host the given member. */
    boolean supports(MemberDescriptor member);

    /** Run one member turn and return its result. Must not throw for ordinary run failures. */
    MemberRunResult run(MemberRunContext context);
}
