package com.huawei.ascend.examples.workmate.spi.topic.local;

import com.huawei.ascend.examples.workmate.spi.topic.TopicBusProvider;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusScope;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusSpi;
import org.springframework.stereotype.Component;

@Component
public class LocalInMemoryTopicBusProvider implements TopicBusProvider {

    public static final String PROVIDER_ID = "local-in-memory";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public TopicBusSpi open(TopicBusScope scope) {
        return new LocalInMemoryTopicBus();
    }
}
