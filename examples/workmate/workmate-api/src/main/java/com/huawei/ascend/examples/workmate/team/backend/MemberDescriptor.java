package com.huawei.ascend.examples.workmate.team.backend;

/**
 * Framework-agnostic description of a team member, used to route a member run to the right
 * {@link MemberBackend} (local DeepAgent / A2A remote / external CLI / heterogeneous vendor).
 *
 * @param memberId    stable member id within the team
 * @param memberName  human-readable member name
 * @param expertId    backing expert id (used to resolve a remote runtime, prompt, model, ...)
 * @param backendType declared backend hint from the expert spec
 *                    (e.g. {@code local}, {@code expert_ref}, {@code a2a}, {@code external_cli});
 *                    may be {@code null} when unspecified
 */
public record MemberDescriptor(
        String memberId,
        String memberName,
        String expertId,
        String backendType) {

    public MemberDescriptor {
        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException("memberId is required");
        }
    }

    public static MemberDescriptor of(String memberId, String memberName, String expertId, String backendType) {
        return new MemberDescriptor(memberId, memberName, expertId, backendType);
    }
}
