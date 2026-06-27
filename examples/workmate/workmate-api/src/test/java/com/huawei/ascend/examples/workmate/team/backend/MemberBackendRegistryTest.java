package com.huawei.ascend.examples.workmate.team.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MemberBackendRegistryTest {

    private static MemberDescriptor member(String id) {
        return MemberDescriptor.of(id, id, id, null);
    }

    private static final class FakeBackend implements MemberBackend {
        private final String kind;
        private final int priority;
        private final boolean supports;

        FakeBackend(String kind, int priority, boolean supports) {
            this.kind = kind;
            this.priority = priority;
            this.supports = supports;
        }

        @Override
        public String kind() {
            return kind;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean supports(MemberDescriptor descriptor) {
            return supports;
        }

        @Override
        public MemberRunResult run(MemberRunContext context) {
            return MemberRunResult.ok(kind);
        }
    }

    @Test
    void picksLowestPriorityMatchingBackend() {
        MemberBackend specific = new FakeBackend("a2a", 100, true);
        MemberBackend fallback = new FakeBackend("local", Integer.MAX_VALUE, true);
        MemberBackendRegistry registry = new MemberBackendRegistry(List.of(fallback, specific));

        assertThat(registry.resolve(member("m1")).kind()).isEqualTo("a2a");
    }

    @Test
    void fallsBackWhenSpecificDoesNotSupport() {
        MemberBackend specific = new FakeBackend("a2a", 100, false);
        MemberBackend fallback = new FakeBackend("local", Integer.MAX_VALUE, true);
        MemberBackendRegistry registry = new MemberBackendRegistry(List.of(specific, fallback));

        assertThat(registry.resolve(member("m1")).kind()).isEqualTo("local");
        assertThat(registry.backendKinds()).containsExactly("a2a", "local");
    }
}
