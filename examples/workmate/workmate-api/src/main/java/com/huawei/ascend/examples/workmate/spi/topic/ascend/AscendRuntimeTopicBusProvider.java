package com.huawei.ascend.examples.workmate.spi.topic.ascend;

import com.huawei.ascend.examples.workmate.spi.topic.TopicBusProvider;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusScope;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusSpi;
import com.huawei.ascend.examples.workmate.spi.topic.local.LocalInMemoryTopicBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * WorkMate-side incubation of ascend {@code agent-runtime} topic routing (W26 ascend SPI).
 *
 * <p>Does not modify {@code agent-runtime}. Today uses the same in-memory bus as
 * {@code local-in-memory}; when upstream ships native {@code publish/subscribe(topic)},
 * replace {@link #open} with a bridge to that SPI without changing orchestrators.
 */
@Component
public class AscendRuntimeTopicBusProvider implements TopicBusProvider {

    public static final String PROVIDER_ID = "ascend-runtime";

    private static final Logger LOG = LoggerFactory.getLogger(AscendRuntimeTopicBusProvider.class);

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public TopicBusSpi open(TopicBusScope scope) {
        LOG.debug(
                "ascend-runtime topic bus incubated in workmate (in-memory); scopeId={} teamId={}",
                scope.scopeId(),
                scope.teamId());
        return new LocalInMemoryTopicBus();
    }
}
