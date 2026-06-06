package com.huawei.ascend.runtime.engine.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentDriver;
import com.huawei.ascend.runtime.engine.spi.AgentDriver;
import com.huawei.ascend.runtime.engine.spi.OutputConverter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R2.1/R4.1: resolve drivers by agent id and group by framework id. */
class AgentDriverRegistryTest {

    @Test
    void resolvesByAgentIdAndGroupsByFramework() {
        AgentDriverRegistry registry = new DefaultAgentDriverRegistry();
        AgentDriver weather = stub("weather-agent", "openjiuwen");
        AgentDriver ticket = stub("ticket-agent", "openjiuwen");
        AgentDriver remote = stub("dify-flow", "dify");
        registry.register(weather);
        registry.register(ticket);
        registry.register(remote);

        assertSame(weather, registry.find("weather-agent"));
        assertSame(remote, registry.find("dify-flow"));
        assertNull(registry.find("missing-agent"));

        List<AgentDriver> openjiuwen = registry.byFramework("openjiuwen");
        assertEquals(2, openjiuwen.size());
        assertTrue(openjiuwen.contains(weather) && openjiuwen.contains(ticket));
        assertEquals(1, registry.byFramework("dify").size());
        assertTrue(registry.byFramework("agentscope").isEmpty());
    }

    @Test
    void rejectsNullDriver() {
        AgentDriverRegistry registry = new DefaultAgentDriverRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
    }

    @Test
    void rejectsBlankOrNullAgentId() {
        AgentDriverRegistry registry = new DefaultAgentDriverRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(stub("", "openjiuwen")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(stub("   ", "openjiuwen")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(stub(null, "openjiuwen")));
    }

    @Test
    void rejectsBlankFrameworkId() {
        AgentDriverRegistry registry = new DefaultAgentDriverRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(stub("agent", " ")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(stub("agent", null)));
    }

    @Test
    void rejectsDuplicateAgentIdFromADifferentDriver() {
        AgentDriverRegistry registry = new DefaultAgentDriverRegistry();
        registry.register(stub("dup", "openjiuwen"));
        assertThrows(IllegalStateException.class, () -> registry.register(stub("dup", "dify")));
    }

    @Test
    void allowsIdempotentReRegistrationOfSameInstance() {
        AgentDriverRegistry registry = new DefaultAgentDriverRegistry();
        AgentDriver driver = stub("idem", "openjiuwen");
        registry.register(driver);
        registry.register(driver);
        assertSame(driver, registry.find("idem"));
    }

    private static AgentDriver stub(String agentId, String frameworkId) {
        return new AbstractAgentDriver() {
            @Override
            public String name() {
                return agentId;
            }

            @Override
            public String frameworkId() {
                return frameworkId;
            }

            @Override
            public Object invoke(InvocationRequest request) {
                return List.of();
            }

            @Override
            public OutputConverter outputConverter() {
                return frameworkStream -> subscriber -> subscriber.onSubscribe(emptySubscription(subscriber));
            }
        };
    }

    private static java.util.concurrent.Flow.Subscription emptySubscription(
            java.util.concurrent.Flow.Subscriber<?> subscriber) {
        return new java.util.concurrent.Flow.Subscription() {
            @Override
            public void request(long n) {
                subscriber.onComplete();
            }

            @Override
            public void cancel() {
                // no-op
            }
        };
    }
}
