package com.huawei.ascend.runtime.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agent-runtime")
public class AgentRuntimeProperties {
    public static final int DEFAULT_REMOTE_INVOCATION_MAX_LEGS = 5;
    public static final int MIN_REMOTE_INVOCATION_MAX_LEGS = 1;
    public static final int MAX_REMOTE_INVOCATION_MAX_LEGS = 100;

    private final RemoteInvocation remoteInvocation = new RemoteInvocation();

    public RemoteInvocation getRemoteInvocation() {
        return remoteInvocation;
    }

    public static int clampRemoteInvocationMaxLegs(int maxLegs) {
        return Math.min(MAX_REMOTE_INVOCATION_MAX_LEGS,
                Math.max(MIN_REMOTE_INVOCATION_MAX_LEGS, maxLegs));
    }

    public static class RemoteInvocation {
        private int maxLegs = DEFAULT_REMOTE_INVOCATION_MAX_LEGS;

        public int getMaxLegs() {
            return maxLegs;
        }

        public void setMaxLegs(int maxLegs) {
            this.maxLegs = clampRemoteInvocationMaxLegs(maxLegs);
        }
    }
}
