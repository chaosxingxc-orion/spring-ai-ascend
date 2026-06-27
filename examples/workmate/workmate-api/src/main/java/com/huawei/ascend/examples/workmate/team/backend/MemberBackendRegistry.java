package com.huawei.ascend.examples.workmate.team.backend;

import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link MemberBackend} for a member and dispatches the run.
 *
 * <p>Backends are consulted in ascending {@link MemberBackend#priority()} order; the first whose
 * {@link MemberBackend#supports(MemberDescriptor)} returns {@code true} wins. The
 * {@link LocalMemberBackend} (priority {@link Integer#MAX_VALUE}) is the guaranteed fallback.</p>
 *
 * <p>This is the single dispatch point for heterogeneous member execution: adding a new backend
 * bean is enough to make WorkMate able to host members on a new framework.</p>
 */
@Component
public class MemberBackendRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(MemberBackendRegistry.class);

    private final List<MemberBackend> backends;

    public MemberBackendRegistry(List<MemberBackend> backends) {
        this.backends = backends.stream()
                .sorted(Comparator.comparingInt(MemberBackend::priority))
                .toList();
        LOG.info(
                "MemberBackendRegistry initialized with backends: {}",
                this.backends.stream().map(MemberBackend::kind).toList());
    }

    /** Resolve the backend that will host the given member. */
    public MemberBackend resolve(MemberDescriptor member) {
        for (MemberBackend backend : backends) {
            if (backend.supports(member)) {
                return backend;
            }
        }
        throw new IllegalStateException("No MemberBackend available for member: " + member.memberId());
    }

    /** Resolve the backend and run one member turn. */
    public MemberRunResult run(MemberRunContext context) {
        MemberBackend backend = resolve(context.member());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Dispatching member {} to backend {}", context.member().memberId(), backend.kind());
        }
        return backend.run(context);
    }

    public List<String> backendKinds() {
        return backends.stream().map(MemberBackend::kind).toList();
    }
}
